package com.huongViet.reservation;

import com.huongViet.auth.SystemUserInitializer;
import com.huongViet.auth.User;
import com.huongViet.reservation.dto.ReservationResponse;
import com.huongViet.reservation.dto.UpdateReservationStatusRequest;
import com.huongViet.table.TableResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reservations")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class ReservationAdminController {

    private final ReservationService reservationService;

    public ReservationAdminController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * GET /api/admin/reservations?date=YYYY-MM-DD&status=...
     * UC09: List reservations for a given date with optional status filter (defaults to today).
     */
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listByDate(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) ReservationStatus status
    ) {
        LocalDate queryDate = (date != null) ? date : LocalDate.now();
        return ResponseEntity.ok(reservationService.listByDate(queryDate, status));
    }

    /**
     * GET /api/admin/reservations/{id}
     * Get reservation detail by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(reservationService.getById(id));
    }

    /**
     * GET /api/admin/reservations/{id}/available-tables
     * UC10: Get available tables suitable for this reservation.
     */
    @GetMapping("/{id}/available-tables")
    public ResponseEntity<List<TableResponse>> getAvailableTables(@PathVariable String id) {
        return ResponseEntity.ok(reservationService.getAvailableTablesForReservation(id));
    }

    /**
     * PATCH /api/admin/reservations/{id}/status
     * UC10 & UC13: Staff/Admin status update & table assignment.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ReservationResponse> updateStatus(
        @PathVariable String id,
        @Valid @RequestBody UpdateReservationStatusRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        String actor = (currentUser != null) ? currentUser.getId() : SystemUserInitializer.SYSTEM_USER_ID;
        return ResponseEntity.ok(reservationService.updateStatus(id, request, actor));
    }
}
