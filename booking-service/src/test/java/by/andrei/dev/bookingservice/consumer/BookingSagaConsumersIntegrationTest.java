package by.andrei.dev.bookingservice.consumer;

import by.andrei.dev.bookingservice.entity.Booking;
import by.andrei.dev.bookingservice.repository.BookingRepository;
import by.andrei.dev.bookingservice.service.BookingNotificationService;
import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Testcontainers
class BookingSagaConsumersIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("booking_db")
            .withUsername("postgres")
            .withPassword("password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentStatusConsumer paymentStatusConsumer;

    @Autowired
    private MovieStatusConsumer movieStatusConsumer;

    @Autowired
    private BookingNotificationService notificationService;

    private UUID testBookingId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();

        Booking booking = bookingRepository.save(Booking.builder()
                .userId(UUID.randomUUID())
                .movieId(UUID.randomUUID())
                .sessionId(UUID.randomUUID())
                .seatId(UUID.randomUUID())
                .price(BigDecimal.valueOf(20.00))
                .status("PENDING")
                .build());

        testBookingId = booking.getId();
    }

    @Test
    @DisplayName("Сценарий 1: Успешная оплата финализирует заказ в статус CONFIRMED")
    void shouldFinalizeBookingToConfirmedOnPaymentSuccess() {
        PaymentEvent successEvent = PaymentEvent.builder()
                .bookingId(testBookingId)
                .amount(BigDecimal.valueOf(20.00))
                .status("SUCCESS")
                .build();

        paymentStatusConsumer.handlePaymentStatus(successEvent);

        Booking updatedBooking = bookingRepository.findById(testBookingId).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("Сценарий 2: Отказ банка отменяет заказ и переводит его в CANCELLED")
    void shouldCancelBookingOnPaymentFailure() {
        PaymentEvent failedEvent = PaymentEvent.builder()
                .bookingId(testBookingId)
                .amount(BigDecimal.valueOf(20.00))
                .status("FAILED")
                .failureReason("Insufficient funds")
                .build();

        paymentStatusConsumer.handlePaymentStatus(failedEvent);

        Booking updatedBooking = bookingRepository.findById(testBookingId).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Сценарий 3: Если место занято, заказ переходит в статус REJECTED")
    void shouldRejectBookingWhenMovieServiceReportsRejection() {
        BookingStatusEvent rejectedEvent = BookingStatusEvent.builder()
                .bookingId(testBookingId)
                .status("REJECTED")
                .message("Seat already taken")
                .build();

        movieStatusConsumer.handleStatusUpdate(rejectedEvent);

        Booking updatedBooking = bookingRepository.findById(testBookingId).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("Сценарий 4: SSE Подписка — клиент подписывается и получает уведомление без ошибок")
    void shouldNotifySseSubscriberOnPaymentSuccess() {
        SseEmitter emitter = notificationService.subscribe(testBookingId);
        assertThat(emitter).isNotNull();

        PaymentEvent successEvent = PaymentEvent.builder()
                .bookingId(testBookingId)
                .status("SUCCESS")
                .build();

        assertThatCode(() -> paymentStatusConsumer.handlePaymentStatus(successEvent))
                .doesNotThrowAnyException();

        Booking updatedBooking = bookingRepository.findById(testBookingId).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo("CONFIRMED");
    }
}