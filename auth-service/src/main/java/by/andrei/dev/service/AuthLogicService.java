package by.andrei.dev.service;

import by.andrei.dev.entity.User;
import by.andrei.dev.repository.OtpRepository;
import by.andrei.dev.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthLogicService {
    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final KeycloakIntegrationService keycloakIntegrationService;
    private final StringRedisTemplate redisTemplate;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${auth.otp.length:6}")
    private int otpLength;

    @Value("${auth.otp.log-code:false}")
    private boolean logOtp;

    @Transactional
    public void sendOtp(String rawIdentifier) {
        String identifier = normalize(rawIdentifier);
        int bound = (int) Math.pow(10, otpLength);
        String format = "%0" + otpLength + "d";
        String code = String.format(format, SECURE_RANDOM.nextInt(bound));

        OtpCode otp = OtpCode.builder()
                .identifier(identifier)
                .code(code)
                .build();
        otpRepository.save(otp);
        //TODO логика отправки отп емайл/смс/телега

        if (logOtp) {
            log.warn("[DEV MODE ONLY] OTP for {}: {}", identifier, code);
        } else {
            log.info("OTP successfully generated and dispatched for: {}", identifier);
        }
    }

    public String verifyOtpAndGenerateToken(String rawIdentifier, String code) {
        String identifier = normalize(rawIdentifier);

        String attemptKey = "otp:attempts:" + identifier;
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptKey, Duration.ofMinutes(5));
        }
        if (attempts != null && attempts > 5) {
            log.warn("Brute-force attempt detected for: {}", identifier);
            throw new RuntimeException("To many attempts. Try later.");
        }

        OtpCode savedOtp = otpRepository.findById(identifier)
                .orElseThrow(() -> new RuntimeException("OTP code not found or expired"));

        boolean matches = code != null && MessageDigest.isEqual(
                savedOtp.getCode().getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new RuntimeException("Invalid OTP code");
        }

        otpRepository.delete(savedOtp);
        redisTemplate.delete(attemptKey);

        User user = userRepository.findByIdentifier(identifier)
                .orElseGet(() -> userRepository.save(User.builder()
                        .identifier(identifier)
                        .role("USER")
                        .build()));

        if (user.isBlocked()) {
            log.warn("Blocked user {} attempted to login", identifier);
            throw new RuntimeException("Account blocked");
        }

        String hmacPassword = keycloakIntegrationService.generateHmacPassword(identifier);
        keycloakIntegrationService.ensureUserExists(identifier, hmacPassword);

        return keycloakIntegrationService.getTokenForUser(identifier, hmacPassword);
    }

    private String normalize(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }
}
