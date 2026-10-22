package by.andrei.dev.service;

import by.andrei.dev.entity.User;
import by.andrei.dev.repository.OtpRepository;
import by.andrei.dev.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthLogicService {
    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final KeycloakIntegrationService keycloakIntegrationService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${auth.otp.length:6}")
    private int otpLength;

    @Transactional
    public void sendOtp(String identifier) {
        int bound = (int) Math.pow(10, otpLength);
        String format = "%0" + otpLength + "d";
        String code = String.format(format, SECURE_RANDOM.nextInt(bound));

        OtpCode otp = OtpCode.builder()
                .identifier(identifier)
                .code(code)
                .build();
        otpRepository.save(otp);
        //TODO логика отправки отп емайл/смс/телега

        log.info("OTP for {}: {}", identifier, code);
    }

    public String verifyOtpAndGenerateToken(String identifier, String code) {
        OtpCode savedOtp = otpRepository.findById(identifier)
                .orElseThrow(() -> new RuntimeException("OTP code not found or expired"));

        if (!savedOtp.getCode().equals(code)) {
            throw new RuntimeException("Invalid OTP code");
        }

        otpRepository.delete(savedOtp);

        userRepository.findByIdentifier(identifier)
                .orElseGet(() -> userRepository.save(User.builder()
                        .identifier(identifier)
                        .role("USER")
                        .build()));

        // TODO: Если юзера нет в самом Keycloak, его нужно там создать через Keycloak Admin REST Client.
        // Пока предполагаем, что юзер там есть (или создается асинхронно).

        return keycloakIntegrationService.getTokenForUser(identifier);
    }
}
