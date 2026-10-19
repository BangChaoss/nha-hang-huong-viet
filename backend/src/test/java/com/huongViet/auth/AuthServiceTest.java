package com.huongViet.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, tokenRepository, jwtService, passwordEncoder, 30);
    }

    @Test
    @DisplayName("register should succeed when credentials are unique")
    void testRegisterSuccess() {
        RegisterRequest req = new RegisterRequest("newuser", "new@example.com", "0912345678", "password123");
        when(userRepository.existsByUsernameAndDeletedAtIsNull("newuser")).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndDeletedAtIsNull("0912345678")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token-123");

        AuthResponse resp = authService.register(req);

        assertNotNull(resp);
        assertEquals("jwt-token-123", resp.token());
        assertEquals("newuser", resp.username());
        assertEquals("new@example.com", resp.email());
        assertEquals("CUSTOMER", resp.role());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register should throw CONFLICT when username exists")
    void testRegisterDuplicateUsername() {
        RegisterRequest req = new RegisterRequest("existing", "email@example.com", "0912345678", "password123");
        when(userRepository.existsByUsernameAndDeletedAtIsNull("existing")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.register(req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Username"));
    }

    @Test
    @DisplayName("login should succeed with matching password")
    void testLoginSuccess() {
        LoginRequest req = new LoginRequest("testuser", "password123");
        User user = new User("uid-1", "testuser", "test@example.com", "0912345678", "hashedPw", UserRole.CUSTOMER);

        when(userRepository.findByUsernameAndDeletedAtIsNull("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashedPw")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-login-token");

        AuthResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("jwt-login-token", resp.token());
        assertEquals("testuser", resp.username());
    }

    @Test
    @DisplayName("login should throw UNAUTHORIZED when password is wrong")
    void testLoginWrongPassword() {
        LoginRequest req = new LoginRequest("testuser", "wrongpw");
        User user = new User("uid-1", "testuser", "test@example.com", "0912345678", "hashedPw", UserRole.CUSTOMER);

        when(userRepository.findByUsernameAndDeletedAtIsNull("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpw", "hashedPw")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(req));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("resetPassword should update password and mark token used")
    void testResetPasswordSuccess() {
        ResetPasswordRequest req = new ResetPasswordRequest("my-token", "newPassword123");
        PasswordResetToken prt = new PasswordResetToken("token-id", "user-id", "hash", LocalDateTime.now().plusMinutes(15));
        User user = new User("user-id", "testuser", "test@example.com", "0912345678", "oldHash", UserRole.CUSTOMER);

        when(tokenRepository.findByTokenHashAndUsedAtIsNull(anyString())).thenReturn(Optional.of(prt));
        when(userRepository.findById("user-id")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("newHashedPassword");

        authService.resetPassword(req);

        assertEquals("newHashedPassword", user.getPasswordHash());
        assertNotNull(prt.getUsedAt());
    }
}
