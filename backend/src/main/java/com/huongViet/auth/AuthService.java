package com.huongViet.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.huongViet.auth.EmailService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final int resetTokenExpiryMinutes;

    @Autowired
    public AuthService(UserRepository userRepository,
                       PasswordResetTokenRepository tokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.password-reset.expiration-minutes}") int resetTokenExpiryMinutes,
                       EmailService emailService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.resetTokenExpiryMinutes = resetTokenExpiryMinutes;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthService(UserRepository userRepository,
                       PasswordResetTokenRepository tokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       int resetTokenExpiryMinutes) {
        this(userRepository, tokenRepository, jwtService, passwordEncoder, resetTokenExpiryMinutes, null, null);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        String phone = request.phoneNumber().trim();

        if (userRepository.existsByUsernameAndDeletedAtIsNull(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username đã tồn tại.");
        }
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }
        if (userRepository.existsByPhoneNumberAndDeletedAtIsNull(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng.");
        }

        User user = new User(
            UUID.randomUUID().toString(),
            username,
            email,
            phone,
            passwordEncoder.encode(request.password()),
            UserRole.CUSTOMER
        );
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService != null ? refreshTokenService.createRefreshToken(user) : null;
        return AuthResponse.from(user, token, refreshToken);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String input = request.usernameOrEmail().trim();
        User user = input.contains("@")
            ? userRepository.findByEmailAndDeletedAtIsNull(input.toLowerCase()).orElse(null)
            : userRepository.findByUsernameAndDeletedAtIsNull(input).orElse(null);

        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không hợp lệ.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không hợp lệ.");
        }

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService != null ? refreshTokenService.createRefreshToken(user) : null;
        return AuthResponse.from(user, token, refreshToken);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailAndDeletedAtIsNull(request.email().trim().toLowerCase())
            .ifPresent(user -> {
                // Generate secure 6-digit OTP (100000 - 999999)
                String otp = String.valueOf(100_000 + new java.security.SecureRandom().nextInt(900_000));
                String tokenHash = sha256(otp);

                PasswordResetToken prt = new PasswordResetToken(
                    UUID.randomUUID().toString(),
                    user.getId(),
                    tokenHash,
                    LocalDateTime.now().plusMinutes(resetTokenExpiryMinutes)
                );
                tokenRepository.save(prt);

                // Log OTP for development/testing
                logger.info("[DEV] Password reset OTP for {}: {}", user.getEmail(), otp);
                // Send email with OTP
                emailService.sendPasswordResetOtp(user.getEmail(), otp);
            });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = sha256(request.token().trim());
        PasswordResetToken prt = tokenRepository.findByTokenHashAndUsedAtIsNull(tokenHash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã OTP không hợp lệ hoặc đã được sử dụng."));

        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã OTP đã hết hạn.");
        }

        User user = userRepository.findById(prt.getUserId())
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại."));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        prt.setUsedAt(LocalDateTime.now());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
}
