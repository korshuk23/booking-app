package by.andrei.dev.controller;

import by.andrei.dev.dto.SendOtpRequest;
import by.andrei.dev.dto.VerifyOtpRequest;
import by.andrei.dev.service.AuthLogicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthLogicService authLogicService;

    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(@RequestBody SendOtpRequest request) {
        String code = authLogicService.processOtpRequest(request.getIdentifier(), request.getRole());
        log.info("Generate OTP code [{}] for phone: [{}]", code, request.getIdentifier());
        return ResponseEntity.ok("OTP sent to" + request.getIdentifier());
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody VerifyOtpRequest request) {
        String token = authLogicService.verifyOtpAndGenerateToken(request.getIdentifier(), request.getCode());
        return ResponseEntity.ok(token);
    }
}
