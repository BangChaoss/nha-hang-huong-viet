package com.huongViet.account.dto;

import com.huongViet.auth.User;
import com.huongViet.auth.UserRole;

import java.time.LocalDateTime;

public record UserProfileResponse(
    String id,
    String username,
    String email,
    String phoneNumber,
    UserRole role,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPhoneNumber(),
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
