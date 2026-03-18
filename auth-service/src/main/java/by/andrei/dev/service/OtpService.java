package by.andrei.dev.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class OtpService {
    public String generateOtp(){
        int code = ThreadLocalRandom.current().nextInt(1000,10000);
        return String.valueOf(code);
    }
}
