package com.korshuk.dev.gatewayservice.core;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "App", description = "API gateway for TeaCinema microservices")
public class AppController {
    private final AppService service;

    @GetMapping
    @Operation(summary = "Welcome endpoint", description = "Return simple API welcome message")
    public String getHello() {
        return service.getHello();
    }

    @GetMapping("/health")
    @Operation(summary = "Get system status", description = "Check is alive")
    public String health() {
        return service.health().toString();
    }
}
