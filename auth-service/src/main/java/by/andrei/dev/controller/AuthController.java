package by.andrei.dev.controller;

import by.andrei.dev.dto.SendOtpRequest;
import by.andrei.dev.dto.VerifyOtpRequest;
import by.andrei.dev.service.AuthLogicService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<Map<String, String>> requestOtp(@RequestBody SendOtpRequest request, HttpServletRequest httpRequest) {
        log.info("REST: Request OTP for: {}", request.getIdentifier());
        String ip = httpRequest.getRemoteAddr();
        log.info("REST: Request OTP for: {} from IP: {}", request.getIdentifier(), ip);
        authLogicService.sendOtp(request.getIdentifier(), ip);
        return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        log.info("REST: Verify OTP for: {} from IP: {}", request.getIdentifier(), ip);
        // Убираем try-catch, так как мы добавим GlobalExceptionHandler
        String token = authLogicService.verifyOtpAndGenerateToken(request.getIdentifier(), request.getCode(), ip);
        return ResponseEntity.ok(Map.of("token", token, "type", "Bearer"));
    }
}