package com.korshuk.dev.gatewayservice.modules.auth.dto.requests;

import com.korshuk.dev.gatewayservice.modules.auth.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
@Builder
public class SendOtpRequest {

    @NonNull
    private static String identifier;

    @NonNull
    private static Role role;
}
