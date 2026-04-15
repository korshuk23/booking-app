package by.andrei.dev.service;

import by.andrei.dev.utils.JwtUtils;
import com.korshuk.dev.core.grpc.*;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@RequiredArgsConstructor
@GrpcService
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {
    private final AuthLogicService authService;
    private final JwtUtils jwtUtils;

    @Override
    public void requestOtp(OtpRequest request, StreamObserver<OtpResponse> responseObserver) {
        try {
            authService.sendOtp(request.getIdentifier());
            responseObserver.onNext(OtpResponse.newBuilder().setSuccess(true).build());
        } catch (Exception e) {
            log.error("Ошибка при запросе OTP: {}", e.getMessage());
            responseObserver.onNext(OtpResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void verifyOtp(VerifyRequest request, StreamObserver<AuthResponse> responseObserver) {
        try {
            String token = authService.verifyOtpAndGenerateToken(request.getIdentifier(), request.getCode());
            // Тут можно достать ID юзера из базы, если нужно передать в AuthResponse
            responseObserver.onNext(AuthResponse.newBuilder().setAccessToken(token).build());
        } catch (Exception e) {
            log.error("Ошибка верификации: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.UNAUTHENTICATED.withDescription(e.getMessage()).asException());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void validateToken(TokenRequest request, StreamObserver<TokenResponse> responseObserver) {
        String token = request.getAccessToken();
        log.info("gRPC call: Проверка токена через gRPC");

        try {
            Claims claims = jwtUtils.getClaimsFromToken(token);
            String userId = (claims.getSubject() != null) ? claims.getSubject() : "";
            String role = (claims.get("role", String.class) != null) ? claims.get("role", String.class) : "USER";

            TokenResponse response = TokenResponse.newBuilder()
                    .setIsValid(true)
                    .setUserId(userId)
                    .setRole(role != null ? role : "USER") // Устанавливаем роль
                    .build();
            responseObserver.onNext(response);
        } catch (Exception e) {
            // 3. Если токен плохой — отвечаем "Не валиден"
            log.error("Токен не прошел проверку: {}", e.getMessage());
            responseObserver.onNext(TokenResponse.newBuilder().setIsValid(false).build());
        }

        responseObserver.onCompleted();
    }
}
