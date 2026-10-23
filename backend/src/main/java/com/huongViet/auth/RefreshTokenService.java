package com.huongViet.auth;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final int expirationDays;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               JwtService jwtService,
                               @Value("${app.refresh-token.expiration-days:30}") int expirationDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.expirationDays = expirationDays;
    }

    /**
     * Create a new refresh token for the given user and persist its hash.
     */
    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(expirationDays);
        RefreshToken refreshToken = new RefreshToken(UUID.randomUUID().toString(), tokenHash, user.getId(), expiresAt, now);
        refreshTokenRepository.save(refreshToken);
        logger.info("Refresh token created for user {} (expires at {})", user.getUsername(), expiresAt);
        return rawToken;
    }

    /**
     * Validate the supplied raw refresh token, rotate it, and issue a new access JWT.
     */
    @Transactional
    public RefreshResponse refresh(String rawToken) {
        String tokenHash = sha256(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (stored.getRevokedAt() != null) {
            throw new IllegalArgumentException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token expired");
        }
        // Load user
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found for refresh token"));
        // Generate new JWT
        String newJwt = jwtService.generateToken(user);
        // Rotate refresh token: revoke old and issue new
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);
        String newRefresh = createRefreshToken(user);
        return new RefreshResponse(newJwt, newRefresh);
    }

    /**
     * Revoke all active refresh tokens for a given user (used on logout-all).
     */
    @Transactional
    public void revokeAllForUser(String userId) {
        var tokens = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        LocalDateTime now = LocalDateTime.now();
        tokens.forEach(t -> t.setRevokedAt(now));
        refreshTokenRepository.saveAll(tokens);
        logger.info("Revoked {} refresh tokens for user {}", tokens.size(), userId);
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
