package by.andrei.dev.movieservice.consumer;

import by.andrei.dev.core.dto.BookingEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import by.andrei.dev.movieservice.entity.Movie;
import by.andrei.dev.movieservice.entity.MovieSession;
import by.andrei.dev.movieservice.entity.OutboxEvent;
import by.andrei.dev.movieservice.entity.Seat;
import by.andrei.dev.movieservice.repository.*;
import by.andrei.dev.movieservice.service.SeatReleaseJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class BookingConsumerIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("movie_db")
            .withUsername("postgres")
            .withPassword("password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("grpc.server.port", () -> "0");
        registry.add("spring.cache.type", () -> "none");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
    }

    @Autowired
    private ProcessedBookingRepository processedBookingRepository;

    @Autowired
    private BookingConsumer bookingConsumer;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MovieSessionRepository sessionRepository;

    @Autowired
    private SeatReleaseJob seatReleaseJob;

    private UUID testSessionId;
    private UUID testSeatId;

    @BeforeEach
    void setUp() {
        processedBookingRepository.deleteAll();
        outboxRepository.deleteAll();
        seatRepository.deleteAll();
        sessionRepository.deleteAll();
        movieRepository.deleteAll();

        // 1. Создаем фильм
        Movie movie = movieRepository.save(Movie.builder()
                .title("Интерстеллар")
                .price(BigDecimal.valueOf(15.00))
                .isAvailable(true)
                .build());

        // 2. Создаем сеанс
        MovieSession session = sessionRepository.save(MovieSession.builder()
                .movieId(movie.getId())
                .startTime(LocalDateTime.now().plusHours(2))
                .totalSeats(100)
                .availableSeats(100)
                .build());
        testSessionId = session.getId();

        // 3. Создаем свободное кресло (БЕЗ version(0L) — Hibernate сам сделает persist!)
        Seat seat = seatRepository.save(Seat.builder()
                .sessionId(testSessionId)
                .rowNumber(1)
                .seatNumber(1)
                .status(Seat.SeatStatus.FREE)
                .build());
        testSeatId = seat.getId();
    }

    @Test
    @DisplayName("Сценарий 1: Успешное бронирование места и создание Outbox события")
    void shouldReserveSeatSuccessfully() {
        UUID bookingId = UUID.randomUUID();
        BookingEvent event = BookingEvent.builder()
                .bookingId(bookingId)
                .seatId(testSeatId)
                .sessionId(testSessionId)
                .userId(UUID.randomUUID())
                .price(BigDecimal.valueOf(15.00))
                .build();

        bookingConsumer.consume(event);

        Seat updatedSeat = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(Seat.SeatStatus.RESERVED);

        List<OutboxEvent> outbox = outboxRepository.findAll();
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getEventType()).isEqualTo("SEATS_RESERVED");
        assertThat(outbox.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Сценарий 2: Компенсация Саги - откат кресла в FREE при сбое оплаты")
    void shouldCompensateAndReleaseSeatOnPaymentFailure() {
        Seat seat = seatRepository.findById(testSeatId).orElseThrow();
        seat.setStatus(Seat.SeatStatus.RESERVED);
        seatRepository.saveAndFlush(seat);

        PaymentEvent failureEvent = PaymentEvent.builder()
                .bookingId(UUID.randomUUID())
                .seatId(testSeatId)
                .status("FAILED")
                .failureReason("Declined by bank")
                .build();

        // When: прилетает событие сбоя оплаты
        bookingConsumer.handlePaymentCompensation(failureEvent);

        // Then: кресло автоматически вернулось в статус FREE!
        Seat compensatedSeat = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(compensatedSeat.getStatus()).isEqualTo(Seat.SeatStatus.FREE);
    }

    @Test
    @DisplayName("Сценарий 3: Битва за кресло - только один успевает занять место, второй получает REJECTED")
    void shouldAllowOnlyOneBookingWhenConcurrentRequestsArrive() throws InterruptedException {
        // Given: 2 параллельных потока пытаются купить одно кресло
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            UUID bookingId = UUID.randomUUID();
            BookingEvent event = BookingEvent.builder()
                    .bookingId(bookingId)
                    .seatId(testSeatId)
                    .sessionId(testSessionId)
                    .userId(UUID.randomUUID())
                    .price(BigDecimal.valueOf(15.00))
                    .build();

            executor.submit(() -> {
                try {
                    startLatch.await(); // Ждем одновременного старта
                    bookingConsumer.consume(event);
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }
        // When: одновременный выстрел
        startLatch.countDown();
        endLatch.await(5, TimeUnit.SECONDS);

        // Then
        // 1. Кресло занято ровно один раз
        Seat seat = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.RESERVED);

        // 2. В Outbox ровно 1 успешное событие (SEATS_RESERVED) и 1 отказ (REJECTED)
        List<OutboxEvent> outbox = outboxRepository.findAll();
        assertThat(outbox).hasSize(2);

        long reservedCount = outbox.stream().filter(e -> "SEATS_RESERVED".equals(e.getEventType())).count();
        long rejectedCount = outbox.stream().filter(e -> "REJECTED".equals(e.getEventType())).count();

        assertThat(reservedCount).isEqualTo(1);
        assertThat(rejectedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Сценарий 4: Идемпотентность — повторное сообщение из Kafka игнорируется без ошибок и дублей")
    void shouldIgnoreDuplicateBookingEventIdempotently() {
        // Given: заказ отправляется первый раз
        UUID bookingId = UUID.randomUUID();
        BookingEvent event = BookingEvent.builder()
                .bookingId(bookingId)
                .seatId(testSeatId)
                .sessionId(testSessionId)
                .userId(UUID.randomUUID())
                .price(BigDecimal.valueOf(15.00))
                .build();

        bookingConsumer.consume(event);

        // Убедились, что первый раз обработался
        assertThat(outboxRepository.findAll()).hasSize(1);
        assertThat(seatRepository.findById(testSeatId).orElseThrow().getStatus()).isEqualTo(Seat.SeatStatus.RESERVED);

        // When: Kafka присылает ТОЧНО ТАКОЕ ЖЕ сообщение повторно (сетевой дубль)
        bookingConsumer.consume(event);

        // Then: никаких новых записей в Outbox не появилось, статус кресла не сломался
        assertThat(outboxRepository.findAll()).hasSize(1);
        assertThat(processedBookingRepository.existsById(bookingId)).isTrue();
    }

    @Test
    @DisplayName("Сценарий 5: Успешная оплата — кресло переходит из RESERVED в финальный статус BOOKED")
    void shouldFinalizeSeatToBookedOnPaymentSuccess() {
        // Given: кресло зарезервировано
        Seat seat = seatRepository.findById(testSeatId).orElseThrow();
        seat.setStatus(Seat.SeatStatus.RESERVED);
        seatRepository.saveAndFlush(seat);

        PaymentEvent successPayment = PaymentEvent.builder()
                .bookingId(UUID.randomUUID())
                .seatId(testSeatId)
                .status("SUCCESS")
                .amount(BigDecimal.valueOf(15.00))
                .build();

        // When: прилетает подтверждение успешной оплаты
        bookingConsumer.handlePaymentCompensation(successPayment);

        // Then: статус кресла стал окончательно BOOKED
        Seat finalizedSeat = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(finalizedSeat.getStatus()).isEqualTo(Seat.SeatStatus.BOOKED);
    }

    @Test
    @DisplayName("Сценарий 6: Таймаут брони — зависшие брони старше 15 минут автоматически сбрасываются в FREE")
    void shouldReleaseExpiredReservationsAutomatically() {
        // Given: кресло было забронировано 20 минут назад (просрочено)
        Seat expiredSeat = seatRepository.findById(testSeatId).orElseThrow();
        expiredSeat.setStatus(Seat.SeatStatus.RESERVED);
        expiredSeat.setReservedAt(LocalDateTime.now().minusMinutes(20));
        seatRepository.saveAndFlush(expiredSeat);

        // When: срабатывает фоновый шедулер очистки
        seatReleaseJob.releaseExpiredReservations();

        // Then: кресло снова свободно для покупки!
        Seat releasedSeat = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(releasedSeat.getStatus()).isEqualTo(Seat.SeatStatus.FREE);
        assertThat(releasedSeat.getReservedAt()).isNull();
    }

    @Test
    @DisplayName("Сценарий 7: Несуществующее кресло — корректно формирует REJECTED, сервис не падает")
    void shouldGenerateRejectedEventWhenSeatDoesNotExist() {
        // Given: событие с несуществующим seatId
        UUID nonExistentSeatId = UUID.randomUUID();
        BookingEvent event = BookingEvent.builder()
                .bookingId(UUID.randomUUID())
                .seatId(nonExistentSeatId)
                .sessionId(testSessionId)
                .userId(UUID.randomUUID())
                .price(BigDecimal.valueOf(15.00))
                .build();

        // When
        bookingConsumer.consume(event);

        // Then: в Outbox лежит событие об отказе с понятной причиной
        List<OutboxEvent> outbox = outboxRepository.findAll();
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getEventType()).isEqualTo("REJECTED");
        assertThat(outbox.get(0).getPayload()).contains("Seat not found with ID");
    }
}
