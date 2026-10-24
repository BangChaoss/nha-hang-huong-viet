package com.huongViet.order;

import com.huongViet.auth.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UC-15: Gọi món và tra cứu đơn hàng.
 */
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * POST /api/orders
     * Tạo đơn hàng (và đẩy phiếu bếp nếu submit = true).
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                @AuthenticationPrincipal User currentUser) {
        String staffId = (currentUser != null) ? currentUser.getId() : null;
        OrderResponse created = orderService.create(request, staffId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/orders?tableId=...&status=...
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> listAll(@RequestParam(required = false) String tableId,
                                                       @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(orderService.listAll(tableId, status));
    }

    /**
     * GET /api/orders/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getById(id));
    }

    /**
     * GET /api/orders/table/{tableId}/active
     * Đơn hàng đang mở gần nhất của bàn.
     */
    @GetMapping("/table/{tableId}/active")
    public ResponseEntity<OrderResponse> getActiveByTable(@PathVariable String tableId) {
        return ResponseEntity.ok(orderService.getActiveByTable(tableId));
    }
}
