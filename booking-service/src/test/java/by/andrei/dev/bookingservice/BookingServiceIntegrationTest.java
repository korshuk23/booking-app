package by.andrei.dev.bookingservice;

import by.andrei.dev.bookingservice.entity.Booking;
import by.andrei.dev.bookingservice.entity.OutboxEvent;
import by.andrei.dev.bookingservice.repository.BookingRepository;
import by.andrei.dev.bookingservice.repository.OutboxRepository;
import by.andrei.dev.bookingservice.service.BookingService;
import by.andrei.dev.bookingservice.service.MovieClient;
import com.korshuk.dev.core.grpc.MovieResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@Testcontainers
class BookingServiceIntegrationTest {

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
        registry.add("outbox.publisher.delay", () -> "3600000");
    }

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @MockitoBean
    private MovieClient movieClient;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create booking and save event to outbox in single ACID transaction")
    void shouldCreateBookingAndSaveToOutbox() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID movieId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        MovieResponse mockMovie = MovieResponse.newBuilder()
                .setTitle("Интерстеллар")
                .setPrice(15.0)
                .setIsAvailable(true)
                .build();

        Mockito.when(movieClient.getMovieInfo(any())).thenReturn(mockMovie);

        // When
        Booking createdBooking = bookingService.createBooking(userId, movieId, sessionId,seatId);

        // Then
        assertThat(createdBooking.getId()).isNotNull();
        assertThat(createdBooking.getStatus()).isEqualTo(Booking.BookingStatus.PENDING);

        // Проверяем, что запись появилась в реальной PostgreSQL
        List<Booking> allBookings = bookingRepository.findAll();
        assertThat(allBookings).hasSize(1);
        assertThat(allBookings.get(0).getMovieId()).isEqualTo(movieId);

        // Проверяем Transactional Outbox: событие создано и готово к отправке в Kafka
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(Booking.BookingStatus.PENDING);
        assertThat(outboxEvents.get(0).getAggregateId()).isEqualTo(createdBooking.getId());
    }
}