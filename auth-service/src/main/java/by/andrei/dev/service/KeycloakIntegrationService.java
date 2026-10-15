package by.andrei.dev.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakIntegrationService {

    @Value("${keycloak.token-uri}")
    private String keycloakTokenUri;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private final RestClient restClient = RestClient.create();

    /**
     * Запрашиваем токен у Keycloak, используя Client Credentials или Password Grant.
     * В нашем случае мы можем использовать специальный технический пароль пользователя,
     * либо настроить Token Exchange в Keycloak. 
     * Для простоты пет-проекта используем grant_type = password (Direct Access Grants).
     */
    public String getTokenForUser(String identifier) {
        log.info("Requesting Keycloak token for user: {}", identifier);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("grant_type", "password");
        formData.add("username", identifier);
        // Секретный пароль, который мы задаем всем юзерам при регистрации через наш auth-service
        formData.add("password", "default-secure-password-123");

        try {
            Map<String, Object> response = restClient.post()
                    .uri(keycloakTokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            return (String) response.get("access_token");
        } catch (Exception e) {
            log.error("Failed to get token from Keycloak for user {}", identifier, e);
            throw new RuntimeException("Authorization server error");
        }
    }
}