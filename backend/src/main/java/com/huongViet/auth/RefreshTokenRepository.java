package com.huongViet.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(String userId);
    void deleteAllByExpiresAtBeforeOrRevokedAtNotNull(java.time.LocalDateTime cutoff);
}
