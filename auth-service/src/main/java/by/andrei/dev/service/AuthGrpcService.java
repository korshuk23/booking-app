package by.andrei.dev.service;

import by.andrei.dev.utils.JwtUtils;
import com.korshuk.dev.core.grpc.AuthServiceGrpc;
import com.korshuk.dev.core.grpc.TokenRequest;
import com.korshuk.dev.core.grpc.TokenResponse;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@RequiredArgsConstructor
@GrpcService
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {
    private final JwtUtils jwtUtils;

    @Override
    public void validateToken(TokenRequest request, StreamObserver<TokenResponse> responseObserver) {
        String token = request.getAccessToken();
        log.info("gRPC call: Проверка токена через gRPC");

        try {
            Claims claims = jwtUtils.getClaimsFromToken(token);
            String userId = claims.getSubject();
            String role = claims.get("role", String.class); // Достаем роль из JWT

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
