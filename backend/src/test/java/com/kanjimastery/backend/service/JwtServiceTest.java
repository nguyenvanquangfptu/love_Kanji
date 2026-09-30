package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import com.kanjimastery.backend.config.JwtProperties;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-secret-key-must-be-at-least-32-bytes-long!!");
        properties.setAccessTokenExpirationMs(900_000L);
        properties.setRefreshTokenExpirationMs(604_800_000L);

        jwtService = new JwtService(properties);
        user = User.builder()
                .id(42L)
                .username("taro")
                .email("taro@example.com")
                .passwordHash("hash")
                .role("ROLE_USER")
                .build();
    }

    @Test
    void generateAccessToken_shouldEncodeSubjectAndClaims() {
        JwtService.GeneratedToken token = jwtService.generateAccessToken(user);

        assertThat(jwtService.isTokenValid(token.token())).isTrue();
        assertThat(jwtService.extractUsername(token.token())).isEqualTo("taro");
        assertThat(jwtService.extractUserId(token.token())).isEqualTo(42L);
        assertThat(jwtService.extractJti(token.token())).isEqualTo(token.jti());
    }

    @Test
    void generateAccessAndRefreshTokens_shouldHaveDifferentJti() {
        JwtService.GeneratedToken access = jwtService.generateAccessToken(user);
        JwtService.GeneratedToken refresh = jwtService.generateRefreshToken(user);

        assertThat(access.jti()).isNotEqualTo(refresh.jti());
    }

    @Test
    void isTokenValid_shouldReturnFalse_forGarbageToken() {
        assertThat(jwtService.isTokenValid("not-a-real-jwt")).isFalse();
    }

    @Test
    void isTokenValid_shouldReturnFalse_whenSignedWithDifferentSecret() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-completely-different-secret-key-32-bytes-min!!");
        otherProperties.setAccessTokenExpirationMs(900_000L);
        otherProperties.setRefreshTokenExpirationMs(604_800_000L);
        JwtService otherService = new JwtService(otherProperties);

        String tokenFromOtherService = otherService.generateAccessToken(user).token();

        assertThat(jwtService.isTokenValid(tokenFromOtherService)).isFalse();
    }
}
