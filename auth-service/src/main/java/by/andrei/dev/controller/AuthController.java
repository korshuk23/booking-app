package by.andrei.dev.controller;

import by.andrei.dev.dto.SendOtpRequest;
import by.andrei.dev.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final OtpService otpService;

    @PostMapping("/send-otp")
    public String sendOtp(@RequestBody SendOtpRequest request){
        String code = otpService.generateOtp();
        log.info("Generate OTP code for phone: [{}]",request.getIdentifier());
        return "Otp sent successfully for " + request.getIdentifier();
    }
}
