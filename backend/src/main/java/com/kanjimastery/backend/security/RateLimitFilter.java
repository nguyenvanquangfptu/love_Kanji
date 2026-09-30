package com.kanjimastery.backend.security;

import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * Rate limiting kiểu fixed-window bằng Redis, áp dụng cho các endpoint nhạy cảm (đăng nhập, đăng ký)
 * để chống brute-force/spam, tính theo IP người gọi.
 * <p>
 * IP lấy từ {@code request.getRemoteAddr()}, KHÔNG tự đọc header {@code X-Forwarded-For}: client tự đặt
 * được header đó, mỗi lần đổi giá trị giả là thành "IP mới" và vượt qua giới hạn. Với request đi qua nginx,
 * Tomcat ({@code server.forward-headers-strategy=native}) đã thay remote address bằng IP thật lấy từ header
 * do chính nginx ghi đè.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    private final Map<String, Function<RateLimitProperties, RateLimitProperties.Bucket>> rateLimitedPaths = Map.of(
            "/api/v1/auth/login", RateLimitProperties::getLogin,
            "/api/v1/auth/register", RateLimitProperties::getRegister
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Function<RateLimitProperties, RateLimitProperties.Bucket> bucketFn = rateLimitedPaths.get(request.getRequestURI());
        if (bucketFn == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitProperties.Bucket bucket = bucketFn.apply(rateLimitProperties);
        String key = "ratelimit:%s:%s".formatted(request.getRequestURI(), request.getRemoteAddr());

        if (!rateLimiter.tryAcquire(key, bucket.getLimit(), Duration.ofSeconds(bucket.getWindowSeconds()))) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Quá nhiều yêu cầu, vui lòng thử lại sau ít phút.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
