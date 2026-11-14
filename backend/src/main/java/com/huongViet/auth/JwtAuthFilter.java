package com.huongViet.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import com.huongViet.auth.RevokedTokenRepository;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    /** Header báo trạng thái tài khoản cho frontend (được expose qua CORS). */
    public static final String ACCOUNT_STATUS_HEADER = "X-Account-Status";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository, RevokedTokenRepository revokedTokenRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }
    private final RevokedTokenRepository revokedTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        // Check if token has been revoked
        String tokenHash = sha256(token);
        if (revokedTokenRepository.findByTokenHash(tokenHash).isPresent()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtService.extractUserId(token);
        User user = userRepository.findById(userId)
            .filter(u -> u.getDeletedAt() == null)
            .orElse(null);

        // Tài khoản bị khóa trong lúc đang đăng nhập: chặn ngay, kèm header để frontend
        // nhận biết, báo cho người dùng và xoá phiên.
        if (user != null && User.STATUS_LOCKED.equals(user.getStatus())) {
            writeAccountLocked(response);
            return;
        }

        if (user != null && "ACTIVE".equals(user.getStatus())) {
            var authorities = List.of(
                new SimpleGrantedAuthority(user.getRole().name()),
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );
            var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private void writeAccountLocked(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(ACCOUNT_STATUS_HEADER, User.STATUS_LOCKED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"status\":401,\"error\":\"Unauthorized\",\"code\":\"ACCOUNT_LOCKED\",\"message\":\""
                + AuthService.ACCOUNT_LOCKED_MESSAGE + "\"}"
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
}

