package by.andrei.dev.service;

import by.andrei.dev.entity.User;
import by.andrei.dev.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthLogicService {
    private final UserRepository userRepository;
    private final OtpService otpService;

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
}
