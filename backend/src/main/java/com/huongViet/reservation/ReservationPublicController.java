package com.huongViet.reservation;

import com.huongViet.auth.User;
import com.huongViet.reservation.dto.CreateReservationRequest;
import com.huongViet.reservation.dto.ReservationResponse;
import com.huongViet.reservation.dto.ZoneAvailabilityResponse;
import com.huongViet.table.ZoneResponse;
import com.huongViet.table.ZoneService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@Validated
public class ReservationPublicController {

    private final ReservationService reservationService;
    private final ZoneService zoneService;

    public ReservationPublicController(ReservationService reservationService, ZoneService zoneService) {
        this.reservationService = reservationService;
        this.zoneService = zoneService;
    }

    /**
     * POST /api/reservations
     * Public endpoint allowing both guests and authenticated users to submit a reservation.
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> createBooking(
        @Valid @RequestBody CreateReservationRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        String customerId = (currentUser != null) ? currentUser.getId() : null;
        ReservationResponse response = reservationService.createBooking(request, customerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/reservations/availability?date=YYYY-MM-DD&partySize=N
     * Public endpoint to check remaining capacity and available time slots per zone.
     */
    @GetMapping("/availability")
    public ResponseEntity<List<ZoneAvailabilityResponse>> checkAvailability(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Integer partySize
    ) {
        return ResponseEntity.ok(reservationService.checkAvailability(date, partySize));
    }

    /**
     * GET /api/reservations/time-slots
     * Public endpoint returning predefined restaurant dining time slots.
     */
    @GetMapping("/time-slots")
    public ResponseEntity<List<String>> getTimeSlots() {
        return ResponseEntity.ok(reservationService.getTimeSlots());
    }

    /**
     * GET /api/reservations/zones
     * Public endpoint returning active dining zones.
     */
    @GetMapping("/zones")
    public ResponseEntity<List<ZoneResponse>> getZones() {
        return ResponseEntity.ok(zoneService.listAll());
    }

    /**
     * GET /api/reservations/my-bookings
     * Authenticated endpoint returning logged-in customer's booking history.
     */
    @GetMapping("/my-bookings")
    public ResponseEntity<List<ReservationResponse>> getMyBookings(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xem lịch sử đặt bàn");
        }
        return ResponseEntity.ok(reservationService.getCustomerBookings(currentUser.getId()));
    }

    /**
     * GET /api/reservations/guest-lookup?phone=09xxxxxxxx
     * Public lookup endpoint for guests to check their reservations by phone number.
     */
    @GetMapping("/guest-lookup")
    public ResponseEntity<List<ReservationResponse>> guestLookup(@RequestParam String phone) {
        return ResponseEntity.ok(reservationService.getGuestBookings(phone));
    }
}
