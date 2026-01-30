package com.korshuk.dev.gatewayservice;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
public class Status {
    private String status;
    private LocalDateTime time;
}
