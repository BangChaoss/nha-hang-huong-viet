package com.huongViet.reservation;

import com.huongViet.auth.User;
import com.huongViet.reservation.dto.ReservationResponse;
import com.huongViet.reservation.dto.UpdateReservationStatusRequest;
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
     * GET /api/admin/reservations?date=YYYY-MM-DD
     * List reservations for a given date (defaults to today).
     */
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listByDate(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate queryDate = (date != null) ? date : LocalDate.now();
        return ResponseEntity.ok(reservationService.listByDate(queryDate));
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
     * PATCH /api/admin/reservations/{id}/status
     * Staff/Admin status update & table assignment.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ReservationResponse> updateStatus(
        @PathVariable String id,
        @Valid @RequestBody UpdateReservationStatusRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        String actor = (currentUser != null) ? currentUser.getId() : "system";
        return ResponseEntity.ok(reservationService.updateStatus(id, request, actor));
    }
}
