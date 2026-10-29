package by.andrei.dev.service;

import by.andrei.dev.entity.User;
import by.andrei.dev.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    @Value("${auth.otp.expiration-sec:300}")
    private long otpExpirationSec;

    @Value("${auth.otp.log-code:false}")
    private boolean logOtp;


    @Transactional
    public void sendOtp(String rawIdentifier, String ipAddress) {
        String identifier = normalize(rawIdentifier);

        String reqAttemptKey = "otp:req_attempts:ip:" + ipAddress;
        Long reqAttempts = redisTemplate.opsForValue().increment(reqAttemptKey);
        if (reqAttempts != null && reqAttempts == 1) redisTemplate.expire(reqAttemptKey, Duration.ofHours(1));
        if (reqAttempts != null && reqAttempts > 10) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "To many requests from your IP.");
        }

        String cooldownKey = "otp:cooldown:" + identifier;
        if (redisTemplate.hasKey(cooldownKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try to get next code one minute later.");
        }
        redisTemplate.opsForValue().set(cooldownKey, "lock", Duration.ofMinutes(1));

        int bound = (int) Math.pow(10, otpLength);
        String code = String.format("%0" + otpLength + "d", SECURE_RANDOM.nextInt(bound));

        redisTemplate.opsForValue().set("otp:code:" + identifier, code, Duration.ofSeconds(otpExpirationSec));

        if (logOtp) {
            log.warn("[DEV MODE ONLY] OTP for {}: {}", identifier, code);
        } else {
            log.info("OTP successfully generated for: {}", identifier);
        }
    }

    public String verifyOtpAndGenerateToken(String rawIdentifier, String code, String ipAddress) {
        String identifier = normalize(rawIdentifier);
        String attemptKey = "otp:attempts:ip:" + ipAddress;
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1) redisTemplate.expire(attemptKey, Duration.ofMinutes(15));

        if (attempts != null && attempts > 5) {
            log.warn("Brute-force attempt detected from IP: {}", ipAddress);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "oo many attempts from your IP. Please wait 15 minutes.");
        }

        String savedCode = redisTemplate.opsForValue().getAndDelete("otp:code:" + identifier);

        if (savedCode == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP not found or expired");
        }

        boolean matches = code != null && MessageDigest.isEqual(
                savedCode.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8));

        if (!matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect OTP code");
        }

        redisTemplate.delete(attemptKey);

        User user = userRepository.findByIdentifier(identifier)
                .orElseGet(() -> userRepository.save(User.builder().identifier(identifier).role("USER").build()));

        if (user.isBlocked()) {
            log.warn("Blocked user {} attempted to login", identifier);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account blocked");
        }

        String hmacPassword = keycloakIntegrationService.generateHmacPassword(identifier);
        keycloakIntegrationService.ensureUserExists(identifier, hmacPassword);

        return keycloakIntegrationService.getTokenForUser(identifier, hmacPassword);
    }

    private String normalize(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }
}
