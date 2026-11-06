package com.huongViet.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huongViet.auth.User;
import com.huongViet.auth.UserRole;
import com.huongViet.config.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử REST API gọi món tại bàn (UC-15) và điều chỉnh món trong đơn (UC-17).
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    private OrderResponse orderResponse;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(orderService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        User staff = new User("staff-1", "nhanvien", "nv@huongviet.vn",
            "0901234567", "hash", UserRole.STAFF);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(staff, null, List.of()));

        orderResponse = new OrderResponse(
            "order-1", "table-1", "Bàn 1", "staff-1", OrderType.DINE_IN,
            OrderStatus.IN_PROGRESS, "2026-11-11T10:00:00", null,
            new BigDecimal("110000"), BigDecimal.ZERO,
            List.of(new OrderItemResponse(
                "item-1", "menu-1", "Phở Bò", 2, new BigDecimal("55000"),
                new BigDecimal("110000"), "Ít cay", CookingStatus.QUEUED, null, null))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/orders - tạo đơn và gửi bếp trả về 201 kèm mã nhân viên")
    void create_Returns201() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
            "table-1", OrderType.DINE_IN,
            List.of(new OrderItemRequest("menu-1", 2, "Ít cay")), true);
        when(orderService.create(any(CreateOrderRequest.class), eq("staff-1"))).thenReturn(orderResponse);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("order-1"))
            .andExpect(jsonPath("$.tableNumber").value("Bàn 1"))
            .andExpect(jsonPath("$.items[0].itemName").value("Phở Bò"))
            .andExpect(jsonPath("$.items[0].lineTotal").value(110000));

        verify(orderService).create(any(CreateOrderRequest.class), eq("staff-1"));
    }

    @Test
    @DisplayName("POST /api/orders - đơn không có món trả về 400 và không gọi service")
    void create_EmptyItems_Returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":\"table-1\",\"items\":[],\"submit\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verify(orderService, never()).create(any(CreateOrderRequest.class), any());
    }

    @Test
    @DisplayName("POST /api/orders - bàn chưa mở trả về 409 kèm thông điệp nghiệp vụ")
    void create_TableNotOccupied_Returns409() throws Exception {
        when(orderService.create(any(CreateOrderRequest.class), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "Chỉ có thể gọi món cho bàn đang sử dụng."));

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":\"table-1\",\"items\":[{\"menuItemId\":\"menu-1\",\"quantity\":1}]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Chỉ có thể gọi món cho bàn đang sử dụng."));
    }

    @Test
    @DisplayName("GET /api/orders - chuyển tiếp bộ lọc bàn và trạng thái")
    void listAll_PassesFilters() throws Exception {
        when(orderService.listAll("table-1", OrderStatus.IN_PROGRESS)).thenReturn(List.of(orderResponse));

        mockMvc.perform(get("/api/orders")
                .param("tableId", "table-1")
                .param("status", "IN_PROGRESS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("order-1"));

        verify(orderService).listAll("table-1", OrderStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("GET /api/orders/{id} - trả về chi tiết đơn")
    void getById_ReturnsOrder() throws Exception {
        when(orderService.getById("order-1")).thenReturn(orderResponse);

        mockMvc.perform(get("/api/orders/order-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("order-1"))
            .andExpect(jsonPath("$.subtotal").value(110000));
    }

    @Test
    @DisplayName("GET /api/orders/table/{tableId}/active - trả về đơn đang mở của bàn")
    void getActiveByTable_ReturnsOrder() throws Exception {
        when(orderService.getActiveByTable("table-1")).thenReturn(orderResponse);

        mockMvc.perform(get("/api/orders/table/table-1/active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("POST /api/orders/{orderId}/items - thêm món vào đơn đang mở")
    void addItem_ReturnsUpdatedOrder() throws Exception {
        AddOrderItemRequest request = new AddOrderItemRequest("menu-1", 2, "Ít cay");
        when(orderService.addItem(eq("order-1"), any(AddOrderItemRequest.class), eq("staff-1")))
            .thenReturn(orderResponse);

        mockMvc.perform(post("/api/orders/order-1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        verify(orderService).addItem(eq("order-1"), any(AddOrderItemRequest.class), eq("staff-1"));
    }

    @Test
    @DisplayName("PATCH /api/orders/{orderId}/items/{itemId} - sửa số lượng món")
    void updateItem_ReturnsUpdatedOrder() throws Exception {
        UpdateOrderItemRequest request = new UpdateOrderItemRequest(3, "Không cay");
        when(orderService.updateItem(eq("order-1"), eq("item-1"), any(UpdateOrderItemRequest.class), eq("staff-1")))
            .thenReturn(orderResponse);

        mockMvc.perform(patch("/api/orders/order-1/items/item-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("order-1"));
    }

    @Test
    @DisplayName("DELETE /api/orders/{orderId}/items/{itemId} - hủy món kèm lý do")
    void cancelItem_ReturnsUpdatedOrder() throws Exception {
        CancelOrderItemRequest request = new CancelOrderItemRequest("Khách đổi món");
        when(orderService.cancelItem(eq("order-1"), eq("item-1"), any(CancelOrderItemRequest.class), eq("staff-1")))
            .thenReturn(orderResponse);

        mockMvc.perform(delete("/api/orders/order-1/items/item-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("order-1"));

        verify(orderService).cancelItem(eq("order-1"), eq("item-1"),
            any(CancelOrderItemRequest.class), eq("staff-1"));
    }

    @Test
    @DisplayName("POST /api/orders/table/{tableId}/checkout-cash - thanh toán tiền mặt")
    void checkoutCash_ReturnsBill() throws Exception {
        CashCheckoutResponse bill = new CashCheckoutResponse(
            "table-1", new BigDecimal("110000"), new BigDecimal("11000"),
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("121000"),
            new BigDecimal("6050"), "Bàn 1");
        when(orderService.checkoutCash(eq("table-1"), any(CashCheckoutRequest.class))).thenReturn(bill);

        mockMvc.perform(post("/api/orders/table/table-1/checkout-cash")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"0901234567\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tableId").value("table-1"))
            .andExpect(jsonPath("$.subtotal").value(110000))
            .andExpect(jsonPath("$.vat").value(11000))
            .andExpect(jsonPath("$.total").value(121000));
    }
}
