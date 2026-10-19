package com.huongViet.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    Optional<RevokedToken> findByTokenHash(String tokenHash);
    void deleteAllByRevokedAtBefore(LocalDateTime cutoff);
}
