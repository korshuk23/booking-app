package com.korshuk.dev.gatewayservice.core;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class Status {
    private String status;
    private LocalDateTime time;
}
