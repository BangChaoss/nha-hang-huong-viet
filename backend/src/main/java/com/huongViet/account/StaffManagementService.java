package com.huongViet.account;

import com.huongViet.account.dto.*;
import com.huongViet.auth.RefreshTokenService;
import com.huongViet.auth.User;
import com.huongViet.auth.UserRepository;
import com.huongViet.auth.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class StaffManagementService {

    private static final Logger logger = LoggerFactory.getLogger(StaffManagementService.class);

    private static final List<UserRole> ALL_STAFF_ROLES = List.of(
            UserRole.STAFF, UserRole.CHEF, UserRole.MANAGER, UserRole.ADMIN
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public StaffManagementService(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffResponse> listStaff(User currentActor,
                                                String keyword,
                                                UserRole role,
                                                String status,
                                                int page,
                                                int size) {
        if (role == UserRole.CUSTOMER && currentActor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Quản trị viên được xem danh sách khách hàng.");
        }
        // "All" (role == null) covers staff only; customers are listed solely via the CUSTOMER filter.
        List<UserRole> scope = role == UserRole.CUSTOMER ? List.of(UserRole.CUSTOMER) : ALL_STAFF_ROLES;

        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        String trimmedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String trimmedStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        Page<User> result = userRepository.findStaffWithFilter(scope, role, trimmedStatus, trimmedKeyword, pageable);
        return PageResponse.from(result.map(StaffResponse::from));
    }

    @Transactional(readOnly = true)
    public StaffResponse getStaff(User currentActor, String id) {
        User staff = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nhân viên không tồn tại."));

        if (staff.getRole() == UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nhân viên không tồn tại.");
        }

        return StaffResponse.from(staff);
    }

    @Transactional
    public StaffResponse createStaff(User currentActor, CreateStaffRequest request) {
        if (request.role() == UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể tạo tài khoản CUSTOMER tại mục quản lý nhân viên.");
        }

        if (currentActor.getRole() == UserRole.MANAGER) {
            if (request.role() == UserRole.MANAGER || request.role() == UserRole.ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quản lý (Manager) chỉ có quyền tạo tài khoản STAFF hoặc CHEF.");
            }
        } else if (currentActor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền tạo tài khoản nhân viên.");
        }

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

        String rawPassword = (request.password() != null && !request.password().isBlank())
                ? request.password().trim()
                : "Staff@123456";

        User staff = new User(
                UUID.randomUUID().toString(),
                username,
                email,
                phone,
                passwordEncoder.encode(rawPassword),
                request.role()
        );

        User saved = userRepository.save(staff);
        logger.info("Staff created successfully: id={}, username={}, role={} by actor={}",
                saved.getId(), saved.getUsername(), saved.getRole(), currentActor.getUsername());
        return StaffResponse.from(saved);
    }

    @Transactional
    public StaffResponse updateStaff(User currentActor, String id, UpdateStaffRequest request) {
        User staff = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nhân viên không tồn tại."));

        if (staff.getRole() == UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nhân viên không tồn tại.");
        }

        validateModificationPermissions(currentActor, staff);

        if (currentActor.getRole() == UserRole.MANAGER) {
            if (request.role() == UserRole.MANAGER || request.role() == UserRole.ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quản lý không thể nâng quyền lên MANAGER hoặc ADMIN.");
            }
        }

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        String phone = request.phoneNumber().trim();

        if (userRepository.existsByUsernameAndIdNotAndDeletedAtIsNull(username, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username đã tồn tại.");
        }
        if (userRepository.existsByEmailAndIdNotAndDeletedAtIsNull(email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }
        if (userRepository.existsByPhoneNumberAndIdNotAndDeletedAtIsNull(phone, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng.");
        }

        staff.setUsername(username);
        staff.setEmail(email);
        staff.setPhoneNumber(phone);
        staff.setRole(request.role());

        User saved = userRepository.save(staff);
        logger.info("Staff updated: id={}, username={}, role={} by actor={}",
                saved.getId(), saved.getUsername(), saved.getRole(), currentActor.getUsername());
        return StaffResponse.from(saved);
    }

    @Transactional
    public StaffResponse updateStatus(User currentActor, String id, UpdateUserStatusRequest request) {
        User staff = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nhân viên không tồn tại."));

        // Only ADMIN may lock/unlock customer accounts; to anyone else they do not exist here.
        if (staff.getRole() == UserRole.CUSTOMER && currentActor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nhân viên không tồn tại.");
        }

        if (staff.getId().equals(currentActor.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể tự thay đổi trạng thái của chính mình.");
        }

        validateModificationPermissions(currentActor, staff);

        String newStatus = request.status().trim().toUpperCase();
        staff.setStatus(newStatus);
        User saved = userRepository.save(staff);

        if (!"ACTIVE".equals(newStatus) && refreshTokenService != null) {
            refreshTokenService.revokeAllForUser(id);
        }

        logger.info("Staff status changed to {}: id={}, username={} by actor={}",
                newStatus, saved.getId(), saved.getUsername(), currentActor.getUsername());
        return StaffResponse.from(saved);
    }

    @Transactional
    public void deleteStaff(User currentActor, String id) {
        User staff = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nhân viên không tồn tại."));

        if (staff.getRole() == UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nhân viên không tồn tại.");
        }

        if (staff.getId().equals(currentActor.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể tự xóa tài khoản của chính mình.");
        }

        validateModificationPermissions(currentActor, staff);

        staff.setDeletedAt(LocalDateTime.now());
        staff.setStatus("INACTIVE");
        userRepository.save(staff);

        if (refreshTokenService != null) {
            refreshTokenService.revokeAllForUser(id);
        }

        logger.info("Staff soft-deleted: id={}, username={} by actor={}",
                staff.getId(), staff.getUsername(), currentActor.getUsername());
    }

    private void validateModificationPermissions(User currentActor, User targetStaff) {
        if (targetStaff.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không thể chỉnh sửa hoặc thao tác trên tài khoản Quản trị viên.");
        }

        if (currentActor.getRole() == UserRole.MANAGER) {
            if (targetStaff.getRole() == UserRole.MANAGER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quản lý (Manager) không có quyền thao tác trên tài khoản Quản lý khác.");
            }
        }
    }
}
