package by.andrei.dev.gatewayservice.filter;

import com.korshuk.dev.core.grpc.AuthServiceGrpc;
import com.korshuk.dev.core.grpc.TokenRequest;
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
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@Order(-1) // Самый высокий приоритет
public class AuthFilter implements GlobalFilter {

    @GrpcClient("auth-service")
    private AuthServiceGrpc.AuthServiceBlockingStub authStub;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. Пропускаем авторизацию для эндпоинтов аутентификации
        if (path.startsWith("/api/v1/auth")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        // 2. Оборачиваем БЛОКИРУЮЩИЙ gRPC вызов в РЕАКТИВНЫЙ поток
        return Mono.fromCallable(() -> {
                    TokenRequest request = TokenRequest.newBuilder()
                            .setAccessToken(token)
                            .build();
                    return authStub
                            .withDeadlineAfter(500, TimeUnit.MILLISECONDS)
                            .validateToken(request);
                })
                .subscribeOn(Schedulers.boundedElastic()) // Выполняем в отдельном пуле, чтобы не вешать Gateway
                .flatMap(response -> {
                    if (response != null && response.getIsValid()) {
                        log.info("Token valid for user: {}", response.getUserId());

                        // 3. Мутируем запрос и идем дальше
                        ServerWebExchange modifiedExchange = exchange.mutate()
                                .request(builder -> builder
                                        .header("X-User-Id", response.getUserId())
                                        .header("X-User-Role", response.getRole())) // Пробрасываем роль в хедере
                                .build();
                        return chain.filter(modifiedExchange);
                    } else {
                        return onError(exchange, HttpStatus.UNAUTHORIZED);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Auth Service unavailable: {}", e.getMessage());
                    return onError(exchange, HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    // Вспомогательный метод для чистого завершения запроса с ошибкой
    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete();
    }
}
