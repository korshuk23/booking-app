package com.korshuk.dev.gatewayservice.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppService {

    public String getHello() {
        return "Welcome to TeaCinema API";
    }

    public Status health() {
        return new Status("ok", LocalDateTime.now());
    }
}
