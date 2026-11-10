package com.huongViet.order;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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

/**
 * Màn hình bếp (KDS): xem hàng đợi phiếu bếp và cập nhật trạng thái nấu.
 */
@RestController
@RequestMapping("/api/kitchen/tickets")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class KitchenController {

    private final KitchenTicketService kitchenTicketService;

    public KitchenController(KitchenTicketService kitchenTicketService) {
        this.kitchenTicketService = kitchenTicketService;
    }

    /**
     * GET /api/kitchen/tickets?status=QUEUED&date=2026-11-11
     * Hàng đợi KDS sắp xếp FIFO.
     */
    @GetMapping
    public ResponseEntity<List<KitchenTicketResponse>> listAll(
            @RequestParam(required = false) CookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(kitchenTicketService.listAll(status, date));
    }

    /**
     * PATCH /api/kitchen/tickets/{id}/status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<KitchenTicketResponse> updateStatus(@PathVariable String id,
                                                               @Valid @RequestBody UpdateTicketStatusRequest request) {
        return ResponseEntity.ok(kitchenTicketService.updateStatus(id, request));
    }
}