package by.andrei.dev.gatewayservice.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // 1. Публичные эндпоинты
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**").permitAll()
                        .pathMatchers("/api/v1/auth/**").permitAll()

                        // 2. Каталог фильмов
                        .pathMatchers(HttpMethod.GET, "/api/v1/movies", "/api/v1/movies/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/movies", "/api/v1/movies/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/movies", "/api/v1/movies/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/movies", "/api/v1/movies/**").hasRole("ADMIN")

                        // 3. Бронирование доступно любому авторизованному пользователю
                        .pathMatchers("/api/v1/bookings", "/api/v1/bookings/**").authenticated()

                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder())
                                .jwtAuthenticationConverter(grantedAuthorityExtractor())
                        )
                );

        return http.build();
    }

    /**
     * Создаем декодер, который проверяет криптографическую подпись по JWKS,
     * проверяет время жизни токена (timestamp), но НЕ падает из-за разницы localhost vs keycloak-cinema
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(this.jwkSetUri).build();

        // Валидируем только срок действия токена (exp/nbf), пропуская строгую проверку хоста iss
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator()
        );
        jwtDecoder.setJwtValidator(validator);

        return jwtDecoder;
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthorityExtractor() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }
}
