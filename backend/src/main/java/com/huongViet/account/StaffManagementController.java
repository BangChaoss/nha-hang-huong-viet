package com.huongViet.account;

import com.huongViet.account.dto.*;
import com.huongViet.auth.User;
import com.huongViet.auth.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/staff")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class StaffManagementController {

    private final StaffManagementService staffService;

    public StaffManagementController(StaffManagementService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public PageResponse<StaffResponse> listStaff(
            @AuthenticationPrincipal User currentActor,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return staffService.listStaff(currentActor, keyword, role, status, page, size);
    }

    @GetMapping("/{id}")
    public StaffResponse getStaff(
            @AuthenticationPrincipal User currentActor,
            @PathVariable String id
    ) {
        return staffService.getStaff(currentActor, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse createStaff(
            @AuthenticationPrincipal User currentActor,
            @Valid @RequestBody CreateStaffRequest request
    ) {
        return staffService.createStaff(currentActor, request);
    }

    @PutMapping("/{id}")
    public StaffResponse updateStaff(
            @AuthenticationPrincipal User currentActor,
            @PathVariable String id,
            @Valid @RequestBody UpdateStaffRequest request
    ) {
        return staffService.updateStaff(currentActor, id, request);
    }

    @PatchMapping("/{id}/status")
    public StaffResponse updateStatus(
            @AuthenticationPrincipal User currentActor,
            @PathVariable String id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return staffService.updateStatus(currentActor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStaff(
            @AuthenticationPrincipal User currentActor,
            @PathVariable String id
    ) {
        staffService.deleteStaff(currentActor, id);
    }
}
