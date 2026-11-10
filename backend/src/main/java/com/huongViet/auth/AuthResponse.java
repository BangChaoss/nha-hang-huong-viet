package com.huongViet.auth;

public record AuthResponse(
    String token,
    String refreshToken,
    String id,
    String username,
    String email,
    String role,
    String phoneNumber
) {
    public static AuthResponse from(User user, String token, String refreshToken) {
        return new AuthResponse(
            token,
            refreshToken,
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole().name(),
            user.getPhoneNumber()
        );
    }

    public static AuthResponse from(User user, String token) {
        return from(user, token, null);
    }
}
