package by.andrei.dev.bookingservice.config;

import by.andrei.dev.bookingservice.service.BookingNotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
public class RedisPubSubConfig {

    public static final String BOOKING_STATUS_TOPIC = "booking-status-topic";

    @Bean
    public ChannelTopic bookingStatusTopic() {
        return new ChannelTopic(BOOKING_STATUS_TOPIC);
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            MessageListener bookingStatusMessageListener,
            ChannelTopic bookingStatusTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(bookingStatusMessageListener, bookingStatusTopic);
        return container;
    }

    @Bean
    public MessageListener bookingStatusMessageListener(BookingNotificationService notificationService) {
        return (message, pattern) -> {
            String jsonPayload = new String(message.getBody(), StandardCharsets.UTF_8);
            notificationService.handleClusterNotification(jsonPayload);
        };
    }
}