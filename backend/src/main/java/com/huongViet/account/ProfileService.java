package com.huongViet.account;

import com.huongViet.account.dto.ChangePasswordRequest;
import com.huongViet.account.dto.UpdateProfileRequest;
import com.huongViet.account.dto.UserProfileResponse;
import com.huongViet.auth.RefreshTokenService;
import com.huongViet.auth.User;
import com.huongViet.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public ProfileService(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Người dùng chưa đăng nhập.");
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại."));

        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(User currentUser, UpdateProfileRequest request) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Người dùng chưa đăng nhập.");
        }

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        String phone = request.phoneNumber().trim();

        if (userRepository.existsByUsernameAndIdNotAndDeletedAtIsNull(username, currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username đã tồn tại.");
        }
        if (userRepository.existsByEmailAndIdNotAndDeletedAtIsNull(email, currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }
        if (userRepository.existsByPhoneNumberAndIdNotAndDeletedAtIsNull(phone, currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng.");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại."));

        user.setUsername(username);
        user.setEmail(email);
        user.setPhoneNumber(phone);

        User saved = userRepository.save(user);
        logger.info("User {} (ID: {}) updated profile successfully", saved.getUsername(), saved.getId());
        return UserProfileResponse.from(saved);
    }

    /**
     * Xử lý đổi mật khẩu cho người dùng hiện tại:
     * 1. Kiểm tra xác thực và các ràng buộc mật khẩu (mật khẩu cũ chính xác, mật khẩu mới khớp xác nhận, không trùng mật khẩu cũ).
     * 2. Mã hóa mật khẩu mới (BCrypt) và cập nhật vào cơ sở dữ liệu.
     * 3. Thu hồi (revoke) toàn bộ refresh token hiện hành để buộc đăng xuất trên các thiết bị khác nhằm đảm bảo an toàn bảo mật.
     */
    @Transactional
    public void changePassword(User currentUser, ChangePasswordRequest request) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Người dùng chưa đăng nhập.");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại."));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không chính xác.");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Xác nhận mật khẩu mới không khớp.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới không được trùng với mật khẩu cũ.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Thu hồi toàn bộ refresh token đã cấp trước đó để hủy các phiên đăng nhập cũ trên mọi thiết bị
        if (refreshTokenService != null) {
            refreshTokenService.revokeAllForUser(user.getId());
        }

        logger.info("Password changed successfully for user {} (ID: {})", user.getUsername(), user.getId());
    }
}
