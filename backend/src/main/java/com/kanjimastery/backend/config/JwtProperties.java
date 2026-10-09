package com.kanjimastery.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.jwt")
@Validated
@Getter
@Setter
public class JwtProperties {
    /**
     * HS256 needs a key of at least 256 bits. Checked at startup so a missing JWT_SECRET (left as the literal
     * "${JWT_SECRET}") or a weak one stops the app instead of failing at the first sign-in.
     */
    @NotBlank(message = "chưa được đặt - đặt biến môi trường JWT_SECRET (xem .env.example)")
    @Size(min = 32, message = "phải dài ít nhất 32 ký tự - đặt biến môi trường JWT_SECRET (xem .env.example)")
    private String secret;
    private long accessTokenExpirationMs;
    private long refreshTokenExpirationMs;
}
