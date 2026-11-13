package com.huongViet.reservation;

import com.huongViet.auth.User;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-tier rate limiter filter protecting public booking endpoint POST /api/reservations.
 * Restricts clients to a maximum of 5 booking attempts per hour based on:
 * 1. Authenticated User ID (if logged in)
 * 2. Device ID header X-Device-Id (if guest)
 * 3. Client IP address (fallback for scripts/bots)
 */
@Component
public class ReservationRateLimiterFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.builder()
            .capacity(5)
            .refillIntervally(5, Duration.ofHours(1))
            .build();
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method) && "/api/reservations".equals(path)) {
            String rateLimitKey = resolveRateLimitKey(request);
            Bucket bucket = buckets.computeIfAbsent(rateLimitKey, k -> createNewBucket());

            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("""
                    {
                        "status": 429,
                        "error": "Too Many Requests",
                        "message": "Bạn đã vượt quá số lần gửi yêu cầu đặt bàn (tối đa 5 lượt/giờ). Vui lòng thử lại sau."
                    }
                    """);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    public String resolveRateLimitKey(HttpServletRequest request) {
        // Cấp 1: Người dùng đã đăng nhập (User ID)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            if (user.getId() != null && !user.getId().isBlank()) {
                return "user:" + user.getId().trim();
            }
        }

        // Cấp 2: Khách vãng lai với Device ID Header (từ localStorage client)
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId != null && !deviceId.isBlank()) {
            String sanitized = deviceId.trim();
            if (sanitized.length() <= 100 && sanitized.matches("^[a-zA-Z0-9_-]+$")) {
                return "device:" + sanitized;
            }
        }

        // Cấp 3: Dự phòng theo Client IP (chống spam bot/script)
        return "ip:" + getClientIp(request);
    }

    public void clearBuckets() {
        buckets.clear();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
