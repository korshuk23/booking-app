package by.andrei.dev.service;

import by.andrei.dev.entity.User;
import by.andrei.dev.repository.UserRepository;
import by.andrei.dev.utils.JwtUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthLogicService {
    private final UserRepository userRepository;
    private final OtpService otpService;
    private final JwtUtils jwtUtils;

    @Transactional
    public String processOtpRequest(String identifier, String role) {
        String code = otpService.generateOtp();

        User user = userRepository.findByIdentifier(identifier)
                .orElse(User.builder()
                        .identifier(identifier)
                        .role(role)
                        .build());

        user.setOtpCode(code);
        userRepository.save(user);

        return code;
    }

    public String verifyOtpAndGenerateToken(String identifier, String code) {
        User user = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!code.equals(user.getOtpCode())) {
            throw new RuntimeException("Invalid OTP code!");
        }

        // Если всё ок — генерируем JWT
        String token = jwtUtils.generateToken(user.getIdentifier(), user.getRole());

        // Очищаем код в базе после успешного входа (безопасность!)
        user.setOtpCode(null);
        userRepository.save(user);

        return token;
    }
}
