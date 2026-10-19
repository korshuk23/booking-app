package by.andrei.dev.paymentservice.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;

@Component
public class PaymentGateway {
    private final Random random = new Random();

    /**
     * Симуляция списания средств через внешний шлюз ().
     * В реале здесь HTTP-запрос к API платежной системы.
     */
    public boolean processPayment(BigDecimal amount) {
        // Симулируем 90% успешных оплат и 10% сбоев (нехватка средств/отклонено банком)
        //return random.nextInt(100) < 90;
        return true;
    }
}
