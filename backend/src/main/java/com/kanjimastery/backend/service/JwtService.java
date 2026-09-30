package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import com.kanjimastery.backend.config.JwtProperties;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    public GeneratedToken generateAccessToken(User user) {
        return generate(user, jwtProperties.getAccessTokenExpirationMs());
    }

    public GeneratedToken generateRefreshToken(User user) {
        return generate(user, jwtProperties.getRefreshTokenExpirationMs());
    }

    private GeneratedToken generate(User user, long expirationMs) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        String token = Jwts.builder()
                .subject(user.getUsername())
                .id(jti)
                .claim("userId", user.getId())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();

        return new GeneratedToken(token, jti, expiry);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    public Long extractUserId(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    public Date extractExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public record GeneratedToken(String token, String jti, Date expiresAt) {
    }
}
