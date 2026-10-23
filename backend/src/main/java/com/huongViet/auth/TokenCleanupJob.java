package com.huongViet.auth;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TokenCleanupJob {
    private static final Logger logger = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final RevokedTokenRepository revokedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final int revokedRetentionDays;
    private final int refreshRetentionDays;

    public TokenCleanupJob(RevokedTokenRepository revokedTokenRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           @Value("${app.revoked-token.retention-days:7}") int revokedRetentionDays,
                           @Value("${app.refresh-token.retention-days:30}") int refreshRetentionDays) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.revokedRetentionDays = revokedRetentionDays;
        this.refreshRetentionDays = refreshRetentionDays;
    }

    @Scheduled(cron = "0 0 * * * *") // every hour
    @Transactional
    public void cleanTokens() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime revokedCutoff = now.minusDays(revokedRetentionDays);
        LocalDateTime refreshCutoff = now.minusDays(refreshRetentionDays);
        try {
            revokedTokenRepository.deleteAllByRevokedAtBefore(revokedCutoff);
            refreshTokenRepository.deleteAllByExpiresAtBeforeOrRevokedAtNotNull(refreshCutoff);
            logger.info("Token cleanup executed: revoked before {}, refresh before {}", revokedCutoff, refreshCutoff);
        } catch (Exception e) {
            logger.error("Error during token cleanup", e);
        }
    }
}
