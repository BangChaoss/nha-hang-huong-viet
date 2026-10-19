package com.huongViet.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 50) String username,
    @NotBlank @Email @Size(max = 100) String email,
    @NotBlank @Size(max = 20) String phoneNumber,
    @NotBlank @Size(min = 8, max = 100) String password
) {}
