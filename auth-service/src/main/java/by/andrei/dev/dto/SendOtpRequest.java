package by.andrei.dev.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendOtpRequest {

    @NonNull
    private String identifier;

    @NonNull
    private String role;
}