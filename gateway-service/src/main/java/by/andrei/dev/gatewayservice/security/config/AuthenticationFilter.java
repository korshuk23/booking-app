package by.andrei.dev.gatewayservice.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.GrantedAuthority;
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
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    // 1. Извлекаем User ID (поле sub)
                    String userId = jwtAuth.getToken().getSubject();

                    // 2. Извлекаем роли пользователя
                    String roles = jwtAuth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.joining(","));
                    //Email пользователя(если есть)
                    String email = jwtAuth.getToken().getClaimAsString("email");

                    log.debug("Gateway propagating user info: userId={}, roles={}, email={}", userId, roles, email);

                    // 3. Модифицируем текущий HTTP-запрос, добавляя наши кастомные заголовки
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(builder -> {
                                builder.header("X-User-Id", userId);
                                builder.header("X-User-Roles", roles);
                                if (email != null) {
                                    builder.header("X-User-Email", email);
                                }
                            })
                            .build();

                    return chain.filter(mutatedExchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
