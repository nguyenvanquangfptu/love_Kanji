package com.kanjimastery.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Mỗi request một dòng log: phương thức, đường dẫn, mã trả về, thời gian xử lý. Request được gắn một mã ngắn
 * ({@code rid}) trong MDC - mọi dòng log trong lúc xử lý request đó đều mang mã này - và trả mã về ở header
 * {@code X-Request-Id}, nên thấy lỗi trên trình duyệt (DevTools) là tìm được đúng các dòng log của request đó.
 * <p>
 * Chạy trước mọi filter khác (cả Spring Security) để đo cả request bị chặn 401/403/429. Không log body, query string
 * hay header: đó là chỗ có mật khẩu, token. Mã request luôn do máy chủ sinh, không lấy từ header client gửi lên.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "rid";
    /** Tên đăng nhập của người gọi, do {@link JwtAuthenticationFilter} đặt khi xác thực được token. */
    public static final String MDC_USER = "user";
    static final long SLOW_REQUEST_MS = 1000;
    private static final List<String> SKIPPED_PREFIXES = List.of("/actuator", "/swagger-ui", "/v3/api-docs");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equals(request.getMethod()) || SKIPPED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long start = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            // Chỉ đánh dấu để log mã 500 rồi ném tiếp cho Tomcat xử lý như cũ.
            failed = true;
            throw ex;
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            int status = failed ? 500 : response.getStatus();
            String method = request.getMethod();
            String path = LogSanitizer.clean(request.getRequestURI());
            if (status >= 500 || elapsedMs >= SLOW_REQUEST_MS) {
                log.warn("HTTP {} {} -> {} ({} ms)", method, path, status, elapsedMs);
            } else {
                log.info("HTTP {} {} -> {} ({} ms)", method, path, status, elapsedMs);
            }
            // Luồng của Tomcat được dùng lại cho request sau: không để mã request, người dùng cũ dính sang.
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_USER);
        }
    }
}
