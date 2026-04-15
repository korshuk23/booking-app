package by.andrei.dev.service;

import org.springframework.data.annotation.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@RedisHash(value = "otp_codes", timeToLive = 300)
public class OtpCode implements Serializable {
    @Id
    private String identifier;
    private String code;
}
