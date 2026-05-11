package by.andrei.dev.gatewayservice.dto;

public record OtpRequestDto(
        String identifier // email или номер телефона
) {}
