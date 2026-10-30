package by.andrei.dev.authservice;

import by.andrei.dev.service.AuthLogicService;
import by.andrei.dev.service.KeycloakIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class AuthLogicServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auth_db")
            .withUsername("postgres")
            .withPassword("password");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired
    private AuthLogicService authLogicService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private KeycloakIntegrationService keycloakIntegrationService; // Заглушка, чтобы не поднимать Keycloak

    private final String TEST_PHONE = "+1234567890";
    private final String TEST_IP = "192.168.1.100";

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        // Мокаем успешную генерацию токена
        when(keycloakIntegrationService.generateHmacPassword(anyString())).thenReturn("mock-hmac");
    }

    @Test
    @DisplayName("Rate Limiter: Защита от спама OTP (Cooldown)")
    void shouldPreventSpammingOtpRequests() {
        // Первый запрос успешен
        authLogicService.sendOtp(TEST_PHONE, TEST_IP);

        // Второй запрос в ту же минуту должен быть заблокирован (429)
        assertThatThrownBy(() -> authLogicService.sendOtp(TEST_PHONE, TEST_IP))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429 TOO_MANY_REQUESTS");
    }

    @Test
    @DisplayName("Brute-Force Защита: 5 неверных попыток сжигают код")
    void shouldBurnOtpAfter5FailedAttempts() {
        authLogicService.sendOtp(TEST_PHONE, TEST_IP);

        // Достаем сгенерированный код напрямую из Redis для проверки
        String realCode = redisTemplate.opsForValue().get("otp:code:" + TEST_PHONE);
        assertThat(realCode).isNotNull();

        // Делаем 4 неверные попытки (код всё еще жив)
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authLogicService.verifyOtpAndGenerateToken(TEST_PHONE, "000000", TEST_IP))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("401 UNAUTHORIZED");
        }
        assertThat(redisTemplate.hasKey("otp:code:" + TEST_PHONE)).isTrue();

        // 5-я неверная попытка СЖИГАЕТ КОД
        assertThatThrownBy(() -> authLogicService.verifyOtpAndGenerateToken(TEST_PHONE, "000000", TEST_IP))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");

        // Убеждаемся, что настоящий код удален
        assertThat(redisTemplate.hasKey("otp:code:" + TEST_PHONE)).isFalse();
    }
}