package by.andrei.dev.service;

import by.andrei.dev.entity.User;
import by.andrei.dev.repository.OtpRepository;
import by.andrei.dev.repository.UserRepository;
import by.andrei.dev.utils.JwtUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthLogicService {
    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final JwtUtils jwtUtils;

    @Transactional
    public void sendOtp(String identifier) {
        String code = String.format("%06d", new Random().nextInt(999999));

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
                .orElseThrow(() -> new RuntimeException("Код не найден или истек"));

        if (!savedOtp.getCode().equals(code)) {
            throw new RuntimeException("Неверный код");
        }

        otpRepository.delete(savedOtp);

        User user = userRepository.findByIdentifier(identifier)
                .orElseGet(()-> userRepository.save(User.builder()
                        .identifier(identifier)
                        .role("USER")
                        .build()));

        return jwtUtils.generateToken(user.getId(), user.getRole());
    }
}
