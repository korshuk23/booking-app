package by.andrei.dev.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor  // Обязательно для Jackson (десериализация JSON)
@AllArgsConstructor // Нужно для работы @Builder
public class SendOtpRequest {

    @NonNull
    private String identifier; // Убрали static! Обычно это телефон или email

    @NonNull
    private String role; // Либо String, либо твой Enum Role
}