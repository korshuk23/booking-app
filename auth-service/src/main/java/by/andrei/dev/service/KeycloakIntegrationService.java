package by.andrei.dev.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
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

    private final RestClient restClient = RestClient.create();


    public String generateHmacPassword(String identifier) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(identifier.toLowerCase().trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            log.error("Failed to generate HMAC password for identifier {}", identifier, e);
            throw new RuntimeException("Security configuration failure", e);
        }
    }

    @SuppressWarnings("unchecked")
    public void ensureUserExists(String identifier, String hmacPassword) {
        String adminToken = getAdminAccessToken();
        String usersUrl = serverUrl + "/admin/realms/" + realm + "/users";

        try {
            List<Map<String, Object>> users = restClient.get()
                    .uri(usersUrl + "?username=" + identifier + "&exact=true")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .retrieve()
                    .body(List.class);

            String userId;
            String email = identifier.contains("@") ? identifier : identifier + "@cinema.dev";
            String firstName = identifier.contains("@") ? identifier.split("@")[0] : identifier;
            String lastName = "User";

            if (users == null || users.isEmpty()) {
                log.info("User '{}' not found in Keycloak. Creating...", identifier);

                var response = restClient.post()
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
                                "requiredActions", List.of()
                        ))
                        .retrieve()
                        .toBodilessEntity();

                var location = response.getHeaders().getLocation();
                if (location != null) {
                    String path = location.getPath();
                    userId = path.substring(path.lastIndexOf('/') + 1);
                } else {
                    List<Map<String, Object>> created = restClient.get()
                            .uri(usersUrl + "?username=" + identifier + "&exact=true")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .retrieve()
                            .body(List.class);
                    userId = (String) created.get(0).get("id");
                }
                log.info("User '{}' created with ID: {}", identifier, userId);
            } else {
                userId = (String) users.get(0).get("id");
            }

            restClient.put()
                    .uri(usersUrl + "/" + userId + "/reset-password")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "type", "password",
                            "value", hmacPassword,
                            "temporary", false
                    ))
                    .retrieve()
                    .toBodilessEntity();

            restClient.put()
                    .uri(usersUrl + "/" + userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "firstName", firstName,
                            "lastName", lastName,
                            "emailVerified", true,
                            "requiredActions", List.of()
                    ))
                    .retrieve()
                    .toBodilessEntity();

            log.info("User '{}' profile completed (firstName/lastName set, required actions cleared)", identifier);

        } catch (Exception e) {
            log.error("Error setting up user in Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to synchronize user with Identity Provider", e);
        }
    }

    /**
     * Запрашиваем токен у Keycloak, используя Client Credentials или Password Grant.
     * В нашем случае мы можем использовать специальный технический пароль пользователя,
     * либо настроить Token Exchange в Keycloak.
     * Для простоты пет-проекта используем grant_type = password (Direct Access Grants).
     */
    @SuppressWarnings("unchecked")
    public String getTokenForUser(String identifier, String hmacPassword) {
        log.info("Requesting Keycloak JWT token for user: {}", identifier);

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

            return (String) response.get("access_token");
        } catch (Exception e) {
            log.error("Failed to get token from Keycloak for user {}", identifier, e);
            throw new RuntimeException("Authorization server error");
        }
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