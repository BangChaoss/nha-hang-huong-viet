package com.huongViet.account.dto;

import com.huongViet.auth.User;
import com.huongViet.auth.UserRole;

import java.time.LocalDateTime;

public record StaffResponse(
    String id,
    String username,
    String email,
    String phoneNumber,
    UserRole role,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static StaffResponse from(User user) {
        return new StaffResponse(
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
