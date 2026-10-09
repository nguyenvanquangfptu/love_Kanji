package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.service.JwtService.TokenType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import com.kanjimastery.backend.config.JwtProperties;

class JwtServiceTest {

    private static final String SECRET = "test-only-secret-key-must-be-at-least-32-bytes-long!!";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
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

        assertThat(jwtService.isTokenValid(token.token(), TokenType.ACCESS)).isTrue();
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
    void isTokenValid_shouldOnlyAcceptEachTokenAsItsOwnType() {
        String access = jwtService.generateAccessToken(user).token();
        String refresh = jwtService.generateRefreshToken(user).token();

        assertThat(jwtService.isTokenValid(access, TokenType.ACCESS)).isTrue();
        assertThat(jwtService.isTokenValid(access, TokenType.REFRESH)).isFalse();
        assertThat(jwtService.isTokenValid(refresh, TokenType.REFRESH)).isTrue();
        assertThat(jwtService.isTokenValid(refresh, TokenType.ACCESS))
                .as("refresh token không được dùng thay access token").isFalse();
    }

    @Test
    void isTokenValid_shouldRefuseTokensIssuedWithoutAType() {
        String untyped = Jwts.builder()
                .subject("taro")
                .id("legacy-jti")
                .claim("userId", 42L)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.isTokenValid(untyped, TokenType.ACCESS)).isFalse();
        assertThat(jwtService.isTokenValid(untyped, TokenType.REFRESH)).isFalse();
    }

    @Test
    void isTokenValid_shouldReturnFalse_forGarbageToken() {
        assertThat(jwtService.isTokenValid("not-a-real-jwt", TokenType.ACCESS)).isFalse();
    }

    @Test
    void isTokenValid_shouldReturnFalse_whenSignedWithDifferentSecret() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-completely-different-secret-key-32-bytes-min!!");
        otherProperties.setAccessTokenExpirationMs(900_000L);
        otherProperties.setRefreshTokenExpirationMs(604_800_000L);
        JwtService otherService = new JwtService(otherProperties);

        String tokenFromOtherService = otherService.generateAccessToken(user).token();

        assertThat(jwtService.isTokenValid(tokenFromOtherService, TokenType.ACCESS)).isFalse();
    }
}
