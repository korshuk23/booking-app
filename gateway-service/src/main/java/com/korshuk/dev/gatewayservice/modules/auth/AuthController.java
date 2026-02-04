package com.korshuk.dev.gatewayservice.modules.auth;

import com.korshuk.dev.gatewayservice.modules.auth.dto.requests.SendOtpRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping
@Slf4j
@Tag(name = "Auth")
public class AuthController {
    @PostMapping("otp/send")
    public HttpStatus sendOtp(SendOtpRequest dto) {
        log.info("DATA: " + dto);

        return HttpStatus.OK;
    }
}
