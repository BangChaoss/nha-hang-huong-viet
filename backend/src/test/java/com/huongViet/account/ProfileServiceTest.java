package com.huongViet.account;

import com.huongViet.account.dto.ChangePasswordRequest;
import com.huongViet.account.dto.UpdateProfileRequest;
import com.huongViet.account.dto.UserProfileResponse;
import com.huongViet.auth.RefreshTokenService;
import com.huongViet.auth.User;
import com.huongViet.auth.UserRepository;
import com.huongViet.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    private ProfileService profileService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userRepository, passwordEncoder, refreshTokenService);
        sampleUser = new User(
                "user-1",
                "testuser",
                "test@example.com",
                "0912345678",
                "hashedOldPassword",
                UserRole.CUSTOMER
        );
    }

    @Test
    @DisplayName("getProfile should return profile when user is authenticated")
    void testGetProfileSuccess() {
        when(userRepository.findByIdAndDeletedAtIsNull("user-1")).thenReturn(Optional.of(sampleUser));

        UserProfileResponse response = profileService.getProfile(sampleUser);

        assertNotNull(response);
        assertEquals("testuser", response.username());
        assertEquals("test@example.com", response.email());
        assertEquals("0912345678", response.phoneNumber());
    }

    @Test
    @DisplayName("updateProfile should succeed when unique checks pass")
    void testUpdateProfileSuccess() {
        UpdateProfileRequest request = new UpdateProfileRequest("newname", "new@example.com", "0987654321");
        when(userRepository.existsByUsernameAndIdNotAndDeletedAtIsNull("newname", "user-1")).thenReturn(false);
        when(userRepository.existsByEmailAndIdNotAndDeletedAtIsNull("new@example.com", "user-1")).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndIdNotAndDeletedAtIsNull("0987654321", "user-1")).thenReturn(false);
        when(userRepository.findByIdAndDeletedAtIsNull("user-1")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = profileService.updateProfile(sampleUser, request);

        assertNotNull(response);
        assertEquals("newname", response.username());
        assertEquals("new@example.com", response.email());
        assertEquals("0987654321", response.phoneNumber());
    }

    @Test
    @DisplayName("updateProfile should throw CONFLICT when username is taken by another user")
    void testUpdateProfileConflictUsername() {
        UpdateProfileRequest request = new UpdateProfileRequest("existinguser", "new@example.com", "0987654321");
        when(userRepository.existsByUsernameAndIdNotAndDeletedAtIsNull("existinguser", "user-1")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                profileService.updateProfile(sampleUser, request)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    @DisplayName("changePassword should succeed when inputs are valid")
    void testChangePasswordSuccess() {
        ChangePasswordRequest request = new ChangePasswordRequest("oldSecret", "newSecret123", "newSecret123");
        when(userRepository.findByIdAndDeletedAtIsNull("user-1")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("oldSecret", "hashedOldPassword")).thenReturn(true);
        when(passwordEncoder.matches("newSecret123", "hashedOldPassword")).thenReturn(false);
        when(passwordEncoder.encode("newSecret123")).thenReturn("hashedNewPassword");

        assertDoesNotThrow(() -> profileService.changePassword(sampleUser, request));

        verify(userRepository).save(sampleUser);
        verify(refreshTokenService).revokeAllForUser("user-1");
        assertEquals("hashedNewPassword", sampleUser.getPasswordHash());
    }

    @Test
    @DisplayName("changePassword should throw BAD_REQUEST when current password is wrong")
    void testChangePasswordWrongCurrent() {
        ChangePasswordRequest request = new ChangePasswordRequest("wrongSecret", "newSecret123", "newSecret123");
        when(userRepository.findByIdAndDeletedAtIsNull("user-1")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrongSecret", "hashedOldPassword")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                profileService.changePassword(sampleUser, request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("changePassword should throw BAD_REQUEST when confirm password does not match")
    void testChangePasswordMismatchConfirm() {
        ChangePasswordRequest request = new ChangePasswordRequest("oldSecret", "newSecret123", "different123");
        when(userRepository.findByIdAndDeletedAtIsNull("user-1")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("oldSecret", "hashedOldPassword")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                profileService.changePassword(sampleUser, request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
