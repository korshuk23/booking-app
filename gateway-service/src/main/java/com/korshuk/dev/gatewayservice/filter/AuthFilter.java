package com.korshuk.dev.gatewayservice.filter;

import com.korshuk.dev.core.grpc.AuthServiceGrpc;
import com.korshuk.dev.core.grpc.TokenRequest;
import com.korshuk.dev.core.grpc.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@Order(-1) // Самый высокий приоритет
public class AuthFilter implements GlobalFilter {

    @GrpcClient("auth-service")
    private AuthServiceGrpc.AuthServiceBlockingStub authStub;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.info("GATEWAY ПРИНЯЛ ЗАПРОС: {}", path);

        // 1. Белый список (пропускаем авторизацию)
        if (path.contains("/api/v1/auth")) {
            return chain.filter(exchange);
        }

        // 2. Проверка заголовка Authorization
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        try {
            // 3. gRPC запрос (Блокирующий вызов оборачиваем в логику)
            TokenRequest request = TokenRequest.newBuilder()
                    .setAccessToken(token)
                    .build();

            // В идеале здесь использовать FutureStub, но для начала починим текущий
            TokenResponse response = authStub.validateToken(request);

            if (response != null && response.getIsValid()) {
                log.info("Token valid for user: {}", response.getUserId());

                // 4. Мутируем запрос: прокидываем ID пользователя дальше по цепочке
                ServerWebExchange modifiedExchange = exchange.mutate()
                        .request(builder -> builder.header("X-User-Id", response.getUserId()))
                        .build();

                return chain.filter(modifiedExchange);
            } else {
                log.warn("Invalid token for path: {}", path);
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            log.error("gRPC Connection Error: {}", e.getMessage());
            return onError(exchange, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Вспомогательный метод для чистого завершения запроса с ошибкой
    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete();
    }
}
