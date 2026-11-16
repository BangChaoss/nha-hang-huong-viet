package com.huongViet.order;

import com.huongViet.auth.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                @AuthenticationPrincipal User currentUser) {
        String staffId = (currentUser != null) ? currentUser.getId() : null;
        OrderResponse created = orderService.create(request, staffId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/table/{tableId}/checkout-cash")
    public ResponseEntity<CashCheckoutResponse> checkoutCash(@PathVariable String tableId,
            @RequestBody(required = false) CashCheckoutRequest request,
            @AuthenticationPrincipal User currentUser) {
        String staffId = (currentUser != null) ? currentUser.getId() : null;
        CashCheckoutRequest safeRequest = (request != null) ? request : new CashCheckoutRequest(null, null, false);
        return ResponseEntity.ok(orderService.checkoutCash(tableId, safeRequest, staffId));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> listAll(@RequestParam(required = false) String tableId,
                                                       @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(orderService.listAll(tableId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getById(id));
    }

    @GetMapping("/table/{tableId}/active")
    public ResponseEntity<OrderResponse> getActiveByTable(@PathVariable String tableId) {
        return ResponseEntity.ok(orderService.getActiveByTable(tableId));
    }

    @PostMapping("/{orderId}/items")
    public ResponseEntity<OrderResponse> addItem(@PathVariable String orderId,
                                                 @Valid @RequestBody AddOrderItemRequest request,
                                                 @AuthenticationPrincipal User currentUser) {
        String staffId = (currentUser != null) ? currentUser.getId() : null;
        return ResponseEntity.ok(orderService.addItem(orderId, request, staffId));
    }

    @PatchMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<OrderResponse> updateItem(@PathVariable String orderId,
                                                    @PathVariable String itemId,
                                                    @Valid @RequestBody UpdateOrderItemRequest request,
                                                    @AuthenticationPrincipal User currentUser) {
        String staffId = (currentUser != null) ? currentUser.getId() : null;
        return ResponseEntity.ok(orderService.updateItem(orderId, itemId, request, staffId));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<OrderResponse> cancelItem(@PathVariable String orderId,
                                                    @PathVariable String itemId,
                                                    @Valid @RequestBody CancelOrderItemRequest request,
                                                    @AuthenticationPrincipal User currentUser) {
        String staffId = (currentUser != null) ? currentUser.getId() : null;
        return ResponseEntity.ok(orderService.cancelItem(orderId, itemId, request, staffId));
    }
}