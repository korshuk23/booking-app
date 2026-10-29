package by.andrei.dev.paymentservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

@Component
@Slf4j
public class PaymentGateway {

    public boolean charge(BigDecimal amount, UUID idempotencyKey) {
       log.info("PSP CHARGE requested: amount={}, idempotencyKey={}", amount, idempotencyKey);
        return true;
    }

    public boolean refund(BigDecimal amount, UUID idempotencyKey) {
        log.info("PSP REFUND requested: amount={}, idempotencyKey={}", amount, idempotencyKey);
        return true;
    }

    public String checkStatus(UUID idempotencyKey) {
        return "SUCCESS";
    }
}
