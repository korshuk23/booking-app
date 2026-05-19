package by.andrei.dev.gatewayservice.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Component
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Так как мы в реактивном WebFlux, контекст безопасности берется через Реактивный холдер
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                // Проверяем, что пользователь действительно аутентифицирован через JWT
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    // 1. Извлекаем User ID (поле sub)
                    String userId = jwtAuth.getToken().getSubject();

                    // 2. Извлекаем роли пользователя
                    String roles = jwtAuth.getAuthorities().stream()
                            .map(grantedAuthority -> grantedAuthority.getAuthority()) // Получим "ROLE_USER"
                            .collect(Collectors.joining(","));

                    log.info("Gateway intercepted request. User ID: {}, Roles: {}", userId, roles);

                    // 3. Модифицируем текущий HTTP-запрос, добавляя наши кастомные заголовки
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(builder -> builder
                                    .header("X-User-Id", userId)
                                    .header("X-User-Role", roles)
                                    .build())
                            .build();

                    // Передаем измененный запрос дальше по цепочке микросервисов
                    return chain.filter(mutatedExchange);
                })
                // Если запрос пришел на публичный эндпоинт (без токена), контекст будет пуст.
                // В таком случае просто пропускаем запрос «как есть» без добавления заголовков.
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        // Устанавливаем приоритет фильтра.
        // Он должен выполниться ПОСЛЕ того, как Spring Security проверит токен.
        // Маленькие/отрицательные числа выполняются первыми. Значение 0 или чуть выше подходит идеально.
        return 0;
    }
}
