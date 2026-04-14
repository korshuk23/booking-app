package com.korshuk.dev.gatewayservice.dto;

public record VerifyRequestDto(
        String identifier,
        String code
) {}