package by.andrei.dev.service;

import by.andrei.dev.dto.AuthTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakIntegrationService {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.admin-username}")
    private String adminUsername;

    @Value("${keycloak.admin-password}")
    private String adminPassword;

    @Value("${keycloak.hmac-secret}")
    private String hmacSecret;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(createClientHttpRequestFactory())
            .build();

    private static SimpleClientHttpRequestFactory createClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    public String generateHmacPassword(String identifier) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(identifier.toLowerCase().trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            log.error("Failed to generate HMAC password for identifier {}", identifier, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Security configuration failure");
        }
    }

    @SuppressWarnings("unchecked")
    public void ensureUserExists(String identifier, String hmacPassword) {
        String adminToken = getAdminAccessToken();
        String usersUrl = serverUrl + "/admin/realms/" + realm + "/users";

        try {
            List<Map<String, Object>> users = restClient.get()
                    .uri(usersUrl + "?username={username}&exact=true", identifier)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .retrieve()
                    .body(List.class);

            if (users != null && !users.isEmpty()) {
                return;
            }

            log.info("User '{}' not found in Keycloak. Creating with credentials...", identifier);

            String email = identifier.contains("@") ? identifier : identifier + "@cinema.dev";
            String firstName = identifier.contains("@") ? identifier.split("@")[0] : identifier;
            String lastName = "User";

            restClient.post()
                    .uri(usersUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "username", identifier,
                            "email", email,
                            "firstName", firstName,
                            "lastName", lastName,
                            "enabled", true,
                            "emailVerified", true,
                            "requiredActions", List.of(),
                            "credentials", List.of(Map.of(
                                    "type", "password",
                                    "value", hmacPassword,
                                    "temporary", false
                            ))
                    ))
                    .retrieve()
                    .toBodilessEntity();

            log.info("User '{}' successfully registered atomically in Keycloak", identifier);

        } catch (org.springframework.web.client.HttpClientErrorException.Conflict e) {
            log.info("User '{}' was created concurrently by another process. Skipping.", identifier);
        } catch (Exception e) {
            log.error("Error setting up user in Keycloak: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to synchronize user with Identity Provider");
        }
    }


    @SuppressWarnings("unchecked")
    public AuthTokenResponse getTokenForUser(String identifier, String hmacPassword) {
        log.info("Requesting Keycloak JWT tokens for user: {}", identifier);
        String tokenUri = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("grant_type", "password");
        formData.add("username", identifier);
        formData.add("password", hmacPassword);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            return mapTokenResponse(response);
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest |
                 org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            log.warn("Invalid credentials for user {}: {}", identifier, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        } catch (Exception e) {
            log.error("Keycloak is unavailable or failed: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity Provider error");
        }
    }

    @SuppressWarnings("unchecked")
    public AuthTokenResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token cannot be blank");
        }

        String tokenUri = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            return mapTokenResponse(response);
        } catch (Exception e) {
            log.warn("Invalid or expired refresh token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
    }

    private AuthTokenResponse mapTokenResponse(Map<String, Object> response) {
        return AuthTokenResponse.builder()
                .accessToken((String) response.get("access_token"))
                .refreshToken((String) response.get("refresh_token"))
                .expiresIn(response.get("expires_in") instanceof Number n ? n.longValue() : 300L)
                .build();
    }


    @SuppressWarnings("unchecked")
    private String getAdminAccessToken() {
        String adminTokenUri = serverUrl + "/realms/master/protocol/openid-connect/token";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", "admin-cli");
        formData.add("grant_type", "password");
        formData.add("username", adminUsername);
        formData.add("password", adminPassword);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(adminTokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            return (String) response.get("access_token");
        } catch (Exception e) {
            log.error("Failed to authenticate as Keycloak admin via admin-cli", e);
            throw new RuntimeException("Identity Provider Admin API unavailable", e);
        }
    }
}