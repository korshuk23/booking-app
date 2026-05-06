package by.andrei.dev.bookingservice.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    // 1. Создание топика программно
    @Bean
    public NewTopic bookingEventsTopic() {
        return TopicBuilder.name("booking-events")
                .partitions(3)    // Для параллельной обработки в будущем
                .replicas(1)      // Т.к. у нас одна нода кафки в докере
                .build();
    }

    // 2. Настройка Producer Factory
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Важно: добавляем тип в заголовок, чтобы Consumer знал, в какой класс десериализовать
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    // 3. KafkaTemplate для отправки сообщений
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        return new KafkaAdmin(configs);
    }
}
