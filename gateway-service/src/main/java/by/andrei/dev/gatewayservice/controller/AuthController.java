package by.andrei.dev.gatewayservice.controller;

import by.andrei.dev.gatewayservice.dto.OtpRequestDto;
import by.andrei.dev.gatewayservice.dto.VerifyRequestDto;
import by.andrei.dev.gatewayservice.service.AuthGrpcClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;


@RestController
@RequestMapping("api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthGrpcClient authGrpcClient;

    @PostMapping("/request")
    public Mono<ResponseEntity<?>> requestOtp(@RequestBody OtpRequestDto request) {
        return authGrpcClient.requestOtp(request.identifier())
                .map(response -> response.getSuccess() ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build());
    }

    @PostMapping("/verify")
    public Mono<ResponseEntity<Map<String, String>>> verifyOtp(@RequestBody VerifyRequestDto dto) {
        return authGrpcClient.verifyOtp(dto.identifier(), dto.code())
                .map(response -> ResponseEntity.ok(Map.of(
                        "token", response.getAccessToken(),
                        "type", "Bearer"
                )))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("error", "Неверный код или срок действия истек"))
                ));
    }
}
