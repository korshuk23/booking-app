package com.korshuk.dev.gatewayservice.service;

import com.korshuk.dev.core.grpc.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AuthGrpcClient {

    @GrpcClient("auth-service")
    private AuthServiceGrpc.AuthServiceBlockingStub authStub;

    public Mono<OtpResponse> requestOtp(String identifier) {
        OtpRequest request = OtpRequest.newBuilder()
                .setIdentifier(identifier)
                .build();

        return Mono.fromCallable(() -> authStub.requestOtp(request));
    }

    public Mono<AuthResponse> verifyOtp(String identifier, String code) {
        VerifyRequest request = VerifyRequest.newBuilder()
                .setIdentifier(identifier)
                .setCode(code)
                .build();

        // Оборачиваем блокирующий вызов gRPC в реактивный поток Mono
        return Mono.fromCallable(() -> authStub.verifyOtp(request))
                .subscribeOn(Schedulers.boundedElastic()); // Важно для Gateway (Netty)
    }
}
