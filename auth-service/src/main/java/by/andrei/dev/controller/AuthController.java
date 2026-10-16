package by.andrei.dev.controller;

import by.andrei.dev.dto.SendOtpRequest;
import by.andrei.dev.dto.VerifyOtpRequest;
import by.andrei.dev.service.AuthLogicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthLogicService authLogicService;

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> requestOtp(@RequestBody SendOtpRequest request) {
        log.info("REST: Request OTP for: {}", request.getIdentifier());
        authLogicService.sendOtp(request.getIdentifier());
        return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request) {
        log.info("REST: Verify OTP for: {}", request.getIdentifier());
        try {
            String token = authLogicService.verifyOtpAndGenerateToken(request.getIdentifier(), request.getCode());
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "type", "Bearer"
            ));
        } catch (RuntimeException e) {
            log.error("Authentication failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}