package by.andrei.dev.gatewayservice.dto;

public record VerifyRequestDto(
        String identifier,
        String code
) {}