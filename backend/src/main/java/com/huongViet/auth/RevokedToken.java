package com.huongViet.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;

    protected RevokedToken() {
        // JPA default constructor
    }

    public RevokedToken(String id, String tokenHash, LocalDateTime revokedAt) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.revokedAt = revokedAt;
    }

    public String getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
