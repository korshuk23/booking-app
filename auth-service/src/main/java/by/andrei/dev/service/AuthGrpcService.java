package by.andrei.dev.service;

import com.korshuk.dev.core.grpc.AuthServiceGrpc;
import com.korshuk.dev.core.grpc.TokenRequest;
import com.korshuk.dev.core.grpc.TokenResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {
    @Override
    public void validateToken(TokenRequest request, StreamObserver<TokenResponse> responseObserver) {
        String token = request.getAccessToken();
        log.info("gRPC call: Validating token [{}]", token);

        // ТУТ БУДЕТ ЛОГИКА ПРОВЕРКИ JWT (сделаем чуть позже)
        // А пока — хардкод для теста: если токен "admin", то он валиден
        boolean isValid = "admin-token".equals(token);

        TokenResponse response = TokenResponse.newBuilder()
                .setUserId("user-123")
                .setIsValid(isValid)
                .build();

        // Отправляем ответ клиенту
        responseObserver.onNext(response);
        // Закрываем соединение
        responseObserver.onCompleted();
    }
}
