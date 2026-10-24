package com.huongViet.order;

import com.huongViet.menu.MenuItem;
import com.huongViet.menu.MenuItemRepository;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableStatus;
import com.huongViet.payment.LoyaltyAccount;
import com.huongViet.payment.LoyaltyAccountRepository;
import com.huongViet.payment.LoyaltySettings;
import com.huongViet.payment.LoyaltySettingsRepository;
import com.huongViet.voucher.VoucherService;
import com.huongViet.voucher.VoucherValidationRequest;
import com.huongViet.voucher.VoucherValidationResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * UC-15: Gọi món và quản lý đơn hàng.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private static final String KITCHEN_TICKETS_TOPIC = "/topic/kitchen/tickets";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final KitchenTicketRepository kitchenTicketRepository;
    private final MenuItemRepository menuItemRepository;
    private final TableRepository tableRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final LoyaltyAccountRepository loyaltyAccounts;
    private final LoyaltySettingsRepository loyaltySettings;
    private final VoucherService voucherService;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        KitchenTicketRepository kitchenTicketRepository,
                        MenuItemRepository menuItemRepository,
                        TableRepository tableRepository,
                        SimpMessagingTemplate messagingTemplate,
                        LoyaltyAccountRepository loyaltyAccounts,
                        LoyaltySettingsRepository loyaltySettings,
                        VoucherService voucherService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.kitchenTicketRepository = kitchenTicketRepository;
        this.menuItemRepository = menuItemRepository;
        this.tableRepository = tableRepository;
        this.messagingTemplate = messagingTemplate;
        this.loyaltyAccounts = loyaltyAccounts;
        this.loyaltySettings = loyaltySettings;
        this.voucherService = voucherService;
    }

    /**
     * UC-15: Tạo đơn hàng cho bàn đang sử dụng.
     * - BR-27/BR-31: chỉ gọi món được cho bàn có trạng thái OCCUPIED.
     * - BR-33: món không còn sẵn sàng không được thêm vào đơn.
     * - BR-32: chỉ tạo phiếu bếp khi đơn được gửi đi (submit != false).
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request, String staffId) {
        RestaurantTable table = tableRepository.findByIdAndDeletedAtIsNull(request.tableId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn."));

        // BR-27 / BR-31
        if (table.getStatus() != TableStatus.OCCUPIED) {
            logger.warn("BR-27/BR-31 violation blocked: cannot order for table {} in status {}",
                table.getTableNumber(), table.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Chỉ có thể gọi món cho bàn đang sử dụng. Vui lòng check-in khách trước khi gọi món.");
        }

        List<MenuItem> menuItems = new ArrayList<>();
        for (OrderItemRequest itemRequest : request.items()) {
            MenuItem menuItem = menuItemRepository.findByIdAndDeletedAtIsNull(itemRequest.menuItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món ăn."));

            // BR-33 — báo món lỗi đầu tiên
            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                logger.warn("BR-33 violation blocked: dish {} is not available", menuItem.getItemName());
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Món " + menuItem.getItemName() + " hiện không còn sẵn. Vui lòng chọn món khác.");
            }

            menuItems.add(menuItem);
        }

        OrderType orderType = request.orderType() != null ? request.orderType() : OrderType.DINE_IN;
        Order order = new Order(
            UUID.randomUUID().toString(),
            table.getId(),
            staffId,
            orderType
        );
        order.setTable(table);
        Order savedOrder = orderRepository.save(order);
        if (savedOrder == null) {
            savedOrder = order;
        }

        List<OrderItem> savedItems = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            OrderItemRequest itemRequest = request.items().get(i);
            MenuItem menuItem = menuItems.get(i);

            OrderItem orderItem = new OrderItem(
                UUID.randomUUID().toString(),
                savedOrder.getId(),
                menuItem.getId(),
                itemRequest.quantity(),
                menuItem.getCurrentPrice(),
                itemRequest.note()
            );
            orderItem.setMenuItem(menuItem);
            OrderItem savedItem = orderItemRepository.save(orderItem);
            savedItems.add(savedItem != null ? savedItem : orderItem);
        }

        // BR-32: chỉ tạo phiếu bếp khi đơn được gửi đi
        boolean submit = request.submit() == null || request.submit();
        if (submit) {
            for (int i = 0; i < savedItems.size(); i++) {
                OrderItem orderItem = savedItems.get(i);
                MenuItem menuItem = menuItems.get(i);

                KitchenTicket ticket = new KitchenTicket(
                    UUID.randomUUID().toString(),
                    orderItem.getId(),
                    savedOrder.getId(),
                    table.getTableNumber(),
                    menuItem.getItemName(),
                    orderItem.getQuantity(),
                    orderItem.getNote()
                );
                ticket.setPriority(1);
                ticket.setStatus(CookingStatus.QUEUED);
                KitchenTicket savedTicket = kitchenTicketRepository.save(ticket);
                if (savedTicket == null) {
                    savedTicket = ticket;
                }

                publishTicketEvent(new KitchenTicketEvent("CREATED", KitchenTicketResponse.from(savedTicket)));
            }
        }

        logger.info("Created order {} for table {} with {} item(s), submit={}",
            savedOrder.getId(), table.getTableNumber(), savedItems.size(), submit);

        return OrderResponse.from(savedOrder, savedItems);
    }


    /**
     * Thanh toán tiền mặt cho tất cả đơn đã gửi bếp còn mở của một bàn.
     * Tổng tiền được tính hoàn toàn từ unit_price_snapshot trong DB, không tin tổng frontend.
     */
    @Transactional
    public CashCheckoutResponse checkoutCash(String tableId, CashCheckoutRequest request) {
        RestaurantTable table = tableRepository.findByIdAndDeletedAtIsNull(tableId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn."));
        List<Order> openOrders = orderRepository.findByTableIdOrderByCreatedAtDesc(tableId).stream()
            .filter(o -> o.getStatus() == OrderStatus.IN_PROGRESS).toList();
        if (openOrders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bàn chưa có món đã gửi bếp để thanh toán.");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Order order : openOrders) {
            for (OrderItem item : orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId())) {
                if (item.getCookingStatus() != CookingStatus.CANCELLED) {
                    subtotal = subtotal.add(item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal beforeDiscount = subtotal.add(vat);
        String phone = request.phoneNumber() == null ? "" : request.phoneNumber().replaceAll("[\\s()-]", "").trim();
        String voucherCode = request.voucherCode() == null ? "" : request.voucherCode().trim().toUpperCase(java.util.Locale.ROOT);
        BigDecimal discount = BigDecimal.ZERO;
        if (!voucherCode.isBlank()) {
            if (phone.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhập số điện thoại để sử dụng voucher.");
            VoucherValidationResponse check = voucherService.validate(new VoucherValidationRequest(voucherCode, phone, beforeDiscount));
            if (!check.valid()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, check.message());
            discount = check.discountAmount().min(beforeDiscount);
        }
        BigDecimal afterDiscount = beforeDiscount.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal redeemed = BigDecimal.ZERO;
        LoyaltyAccount account = null;
        if (!phone.isBlank()) {
            if (!phone.matches("^[0-9+]{8,20}$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ.");
            account = loyaltyAccounts.findByCustomerPhone(phone).orElse(null);
            if (Boolean.TRUE.equals(request.usePoints()) && account != null) {
                redeemed = account.getPointsBalance().min(afterDiscount);
                account.redeemPoints(redeemed);
            }
        } else if (Boolean.TRUE.equals(request.usePoints())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhập số điện thoại để sử dụng điểm.");
        }
        BigDecimal total = afterDiscount.subtract(redeemed).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (total.signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tổng thanh toán không hợp lệ.");

        if (!voucherCode.isBlank()) {
            VoucherValidationResponse used = voucherService.redeem(new VoucherValidationRequest(voucherCode, phone, beforeDiscount));
            if (!used.valid()) throw new ResponseStatusException(HttpStatus.CONFLICT, used.message());
        }
        BigDecimal awarded = BigDecimal.ZERO;
        if (!phone.isBlank()) {
            if (account == null) account = new LoyaltyAccount(java.util.UUID.randomUUID().toString(), phone);
            BigDecimal rate = loyaltySettings.findById("default")
                .orElseGet(() -> loyaltySettings.save(new LoyaltySettings("default", new BigDecimal("5.000"))))
                .getEarnRatePercent();
            awarded = total.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            account.addPoints(awarded);
            loyaltyAccounts.save(account);
        }
        for (Order order : openOrders) {
            order.setStatus(OrderStatus.COMPLETED);
            order.setClosedAt(java.time.LocalDateTime.now());
            orderRepository.save(order);
        }
        table.setStatus(TableStatus.AVAILABLE);
        tableRepository.save(table);
        return new CashCheckoutResponse(tableId, subtotal, vat, discount, redeemed, total, awarded, table.getTableNumber());
    }

    /**
     * Danh sách đơn hàng, lọc theo bàn và/hoặc trạng thái.
     */
    public List<OrderResponse> listAll(String tableId, OrderStatus status) {
        List<Order> orders;

        if (tableId != null && !tableId.isBlank()) {
            orders = orderRepository.findByTableIdOrderByCreatedAtDesc(tableId);
            if (status != null) {
                orders = orders.stream()
                    .filter(order -> order.getStatus() == status)
                    .toList();
            }
        } else if (status != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtAsc(status);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }

        return orders.stream().map(this::toResponse).toList();
    }

    /**
     * Chi tiết đơn hàng.
     */
    public OrderResponse getById(String id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
        return toResponse(order);
    }

    /**
     * Đơn hàng đang mở (IN_PROGRESS) gần nhất của một bàn.
     */
    public OrderResponse getActiveByTable(String tableId) {
        Order order = orderRepository
            .findFirstByTableIdAndStatusOrderByCreatedAtDesc(tableId, OrderStatus.IN_PROGRESS)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Bàn này chưa có đơn hàng đang mở."));
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        return OrderResponse.from(order, items);
    }

    private void publishTicketEvent(KitchenTicketEvent event) {
        try {
            messagingTemplate.convertAndSend(KITCHEN_TICKETS_TOPIC, event);
            logger.debug("Broadcasted kitchen ticket event: {}", event);
        } catch (Exception ex) {
            logger.error("Failed to broadcast kitchen ticket event via WebSocket: ", ex);
        }
    }
}
