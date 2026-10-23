package com.huongViet.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private JwtService jwtService;
    private static final String SECRET = "huong-viet-restaurant-super-secret-key-must-be-at-least-256-bits-long-for-hs256";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("generateToken and extractUserId should round-trip correctly")
    void testGenerateAndExtract() {
        User user = new User("user-123", "testuser", "test@example.com", "0901234567", "hash", UserRole.CUSTOMER);
        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals("user-123", jwtService.extractUserId(token));
    }

    @Test
    @DisplayName("isTokenValid should return false for invalid or tampered token")
    void testInvalidToken() {
        assertFalse(jwtService.isTokenValid("invalid.jwt.token"));
        assertFalse(jwtService.isTokenValid(""));
    }
}
