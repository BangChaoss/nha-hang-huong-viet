package com.huongViet.account;

import com.huongViet.account.dto.CreateStaffRequest;
import com.huongViet.account.dto.PageResponse;
import com.huongViet.account.dto.StaffResponse;
import com.huongViet.account.dto.UpdateStaffRequest;
import com.huongViet.account.dto.UpdateUserStatusRequest;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    private StaffManagementService staffService;

    private User adminActor;
    private User managerActor;
    private User sampleStaff;

    @BeforeEach
    void setUp() {
        staffService = new StaffManagementService(userRepository, passwordEncoder, refreshTokenService);

        adminActor = new User("admin-id", "admin", "admin@huongviet.com", "0999999999", "hash", UserRole.ADMIN);
        managerActor = new User("mgr-id", "manager", "mgr@huongviet.com", "0988888888", "hash", UserRole.MANAGER);
        sampleStaff = new User("staff-id", "waiter1", "waiter1@huongviet.com", "0911111111", "hash", UserRole.STAFF);
    }

    @Test
    @DisplayName("createStaff should succeed when admin creates valid staff")
    void testCreateStaffSuccessByAdmin() {
        CreateStaffRequest request = new CreateStaffRequest("newstaff", "newstaff@huongviet.com", "0922222222", UserRole.STAFF, "StaffPass123");
        when(userRepository.existsByUsernameAndDeletedAtIsNull("newstaff")).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull("newstaff@huongviet.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndDeletedAtIsNull("0922222222")).thenReturn(false);
        when(passwordEncoder.encode("StaffPass123")).thenReturn("hashedPass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffResponse response = staffService.createStaff(adminActor, request);

        assertNotNull(response);
        assertEquals("newstaff", response.username());
        assertEquals("newstaff@huongviet.com", response.email());
        assertEquals(UserRole.STAFF, response.role());
    }

    @Test
    @DisplayName("createStaff should throw FORBIDDEN when manager attempts to create another manager")
    void testCreateStaffManagerCreatingManagerForbidden() {
        CreateStaffRequest request = new CreateStaffRequest("mgr2", "mgr2@huongviet.com", "0933333333", UserRole.MANAGER, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.createStaff(managerActor, request)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("updateStaff should succeed with valid parameters")
    void testUpdateStaffSuccess() {
        UpdateStaffRequest request = new UpdateStaffRequest("updatedStaff", "updated@huongviet.com", "0944444444", UserRole.CHEF);
        when(userRepository.findByIdAndDeletedAtIsNull("staff-id")).thenReturn(Optional.of(sampleStaff));
        when(userRepository.existsByUsernameAndIdNotAndDeletedAtIsNull("updatedStaff", "staff-id")).thenReturn(false);
        when(userRepository.existsByEmailAndIdNotAndDeletedAtIsNull("updated@huongviet.com", "staff-id")).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndIdNotAndDeletedAtIsNull("0944444444", "staff-id")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffResponse response = staffService.updateStaff(adminActor, "staff-id", request);

        assertNotNull(response);
        assertEquals("updatedStaff", response.username());
        assertEquals(UserRole.CHEF, response.role());
    }

    @Test
    @DisplayName("updateStatus should throw BAD_REQUEST when user attempts to change own status")
    void testUpdateStatusSelfLockForbidden() {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("INACTIVE");
        when(userRepository.findByIdAndDeletedAtIsNull("admin-id")).thenReturn(Optional.of(adminActor));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.updateStatus(adminActor, "admin-id", request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("deleteStaff should soft delete staff and revoke active tokens")
    void testDeleteStaffSuccess() {
        when(userRepository.findByIdAndDeletedAtIsNull("staff-id")).thenReturn(Optional.of(sampleStaff));

        assertDoesNotThrow(() -> staffService.deleteStaff(adminActor, "staff-id"));

        assertNotNull(sampleStaff.getDeletedAt());
        assertEquals("INACTIVE", sampleStaff.getStatus());
        verify(userRepository).save(sampleStaff);
        verify(refreshTokenService).revokeAllForUser("staff-id");
    }

    @Test
    @DisplayName("deleteStaff should throw FORBIDDEN when attempting to delete an ADMIN")
    void testDeleteAdminForbidden() {
        User anotherAdmin = new User("admin-2", "admin2", "admin2@huongviet.com", "0977777777", "hash", UserRole.ADMIN);
        when(userRepository.findByIdAndDeletedAtIsNull("admin-2")).thenReturn(Optional.of(anotherAdmin));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.deleteStaff(adminActor, "admin-2")
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("deleteStaff should throw BAD_REQUEST when user attempts to delete self")
    void testDeleteSelfBadRequest() {
        when(userRepository.findByIdAndDeletedAtIsNull("admin-id")).thenReturn(Optional.of(adminActor));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.deleteStaff(adminActor, "admin-id")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("listStaff with CUSTOMER filter should query only customers when actor is admin")
    void testListCustomersByAdmin() {
        User customer = new User("cus-id", "bin", "bin@gmail.com", "0395584625", "hash", UserRole.CUSTOMER);
        when(userRepository.findStaffWithFilter(eq(List.of(UserRole.CUSTOMER)), eq(UserRole.CUSTOMER), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));

        PageResponse<StaffResponse> response = staffService.listStaff(adminActor, null, UserRole.CUSTOMER, null, 0, 10);

        assertEquals(1, response.totalElements());
        assertEquals("bin", response.content().get(0).username());
    }

    @Test
    @DisplayName("listStaff with CUSTOMER filter should throw FORBIDDEN when actor is manager")
    void testListCustomersByManagerForbidden() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.listStaff(managerActor, null, UserRole.CUSTOMER, null, 0, 10)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("updateStatus should let admin lock a customer and revoke tokens")
    void testLockCustomerByAdmin() {
        User customer = new User("cus-id", "bin", "bin@gmail.com", "0395584625", "hash", UserRole.CUSTOMER);
        when(userRepository.findByIdAndDeletedAtIsNull("cus-id")).thenReturn(Optional.of(customer));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffResponse response = staffService.updateStatus(adminActor, "cus-id", new UpdateUserStatusRequest("LOCKED"));

        assertEquals("LOCKED", response.status());
        verify(refreshTokenService).revokeAllForUser("cus-id");
    }

    @Test
    @DisplayName("updateStatus on a customer should throw NOT_FOUND when actor is manager")
    void testLockCustomerByManagerNotFound() {
        User customer = new User("cus-id", "bin", "bin@gmail.com", "0395584625", "hash", UserRole.CUSTOMER);
        when(userRepository.findByIdAndDeletedAtIsNull("cus-id")).thenReturn(Optional.of(customer));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.updateStatus(managerActor, "cus-id", new UpdateUserStatusRequest("LOCKED"))
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
