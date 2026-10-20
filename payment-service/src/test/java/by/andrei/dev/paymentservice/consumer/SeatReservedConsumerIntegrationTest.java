package by.andrei.dev.paymentservice.consumer;

import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.paymentservice.entity.OutboxEvent;
import by.andrei.dev.paymentservice.entity.Payment;
import by.andrei.dev.paymentservice.repository.OutboxRepository;
import by.andrei.dev.paymentservice.repository.PaymentRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SeatReservedConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("payment_db")
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
    private SeatReservedConsumer seatReservedConsumer;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("Сценарий 1: Успешная оплата при получении SEATS_RESERVED и сохранение в Outbox")
    void shouldProcessPaymentAndSaveToOutbox() {
        // Given
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BookingStatusEvent event = BookingStatusEvent.builder()
                .bookingId(bookingId)
                .userId(userId)
                .sessionId(UUID.randomUUID())
                .seatId(UUID.randomUUID())
                .price(BigDecimal.valueOf(25.00))
                .status("SEATS_RESERVED")
                .message("Seat locked for payment")
                .build();

        // When
        seatReservedConsumer.handleSeatReserved(event);

        // Then
        // 1. Платеж сохранен в базе
        Payment payment = paymentRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(25.00));
        assertThat(payment.getStatus()).isEqualTo("SUCCESS");

        // 2. Outbox событие создано для отправки в Kafka
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("PAYMENT_SUCCESS");
        assertThat(outboxEvents.get(0).getAggregateId()).isEqualTo(bookingId);
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Сценарий 2: Идемпотентность платежа — повторный ивент не дублирует оплату")
    void shouldNotProcessDuplicatePaymentEvent() {
        // Given
        UUID bookingId = UUID.randomUUID();
        BookingStatusEvent event = BookingStatusEvent.builder()
                .bookingId(bookingId)
                .userId(UUID.randomUUID())
                .sessionId(UUID.randomUUID())
                .seatId(UUID.randomUUID())
                .price(BigDecimal.valueOf(25.00))
                .status("SEATS_RESERVED")
                .build();

        // When: первый вызов
        seatReservedConsumer.handleSeatReserved(event);
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(outboxRepository.findAll()).hasSize(1);

        // Повторный дублирующий вызов из Kafka
        seatReservedConsumer.handleSeatReserved(event);

        // Then: никаких новых строк в базе и аутбоксе
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(outboxRepository.findAll()).hasSize(1);
    }
}
