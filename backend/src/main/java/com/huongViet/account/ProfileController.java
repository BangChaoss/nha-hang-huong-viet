package com.huongViet.account;

import com.huongViet.account.dto.ChangePasswordRequest;
import com.huongViet.account.dto.UpdateProfileRequest;
import com.huongViet.account.dto.UserProfileResponse;
import com.huongViet.auth.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public UserProfileResponse getProfile(@AuthenticationPrincipal User currentUser) {
        return profileService.getProfile(currentUser);
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(@AuthenticationPrincipal User currentUser,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(currentUser, request);
    }

    @PutMapping("/change-password")
    public Map<String, String> changePassword(@AuthenticationPrincipal User currentUser,
                                              @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(currentUser, request);
        return Map.of("message", "Đổi mật khẩu thành công. Vui lòng đăng nhập lại trên các thiết bị khác.");
    }
}
