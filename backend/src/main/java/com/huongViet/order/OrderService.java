package com.huongViet.order;

import com.huongViet.menu.MenuItem;
import com.huongViet.menu.MenuItemRepository;
import com.huongViet.auth.SystemUserInitializer;
import org.springframework.jdbc.core.JdbcTemplate;
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

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private static final String KITCHEN_TICKETS_TOPIC = "/topic/kitchen/tickets";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final KitchenTicketRepository kitchenTicketRepository;
    private final OrderItemAuditRepository orderItemAuditRepository;
    private final MenuItemRepository menuItemRepository;
    private final TableRepository tableRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final LoyaltyAccountRepository loyaltyAccounts;
    private final LoyaltySettingsRepository loyaltySettings;
    private final VoucherService voucherService;
    private final JdbcTemplate jdbcTemplate;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        KitchenTicketRepository kitchenTicketRepository,
                        OrderItemAuditRepository orderItemAuditRepository,
                        MenuItemRepository menuItemRepository,
                        TableRepository tableRepository,
                        SimpMessagingTemplate messagingTemplate,
                        LoyaltyAccountRepository loyaltyAccounts,
                        LoyaltySettingsRepository loyaltySettings,
                        VoucherService voucherService,
                        JdbcTemplate jdbcTemplate) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.kitchenTicketRepository = kitchenTicketRepository;
        this.orderItemAuditRepository = orderItemAuditRepository;
        this.menuItemRepository = menuItemRepository;
        this.tableRepository = tableRepository;
        this.messagingTemplate = messagingTemplate;
        this.loyaltyAccounts = loyaltyAccounts;
        this.loyaltySettings = loyaltySettings;
        this.voucherService = voucherService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String staffId) {
        RestaurantTable table = tableRepository.findByIdAndDeletedAtIsNull(request.tableId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn."));

        if (table.getStatus() != TableStatus.OCCUPIED) {
            logger.warn("BR-27/BR-31 violation blocked: cannot order for table {} in status {}",
                table.getTableNumber(), table.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Chỉ có thể gọi món cho bàn đang sử dụng. Vui lòng mở bàn trước khi gọi món.");
        }

        List<MenuItem> menuItems = new ArrayList<>();
        for (OrderItemRequest itemRequest : request.items()) {
            MenuItem menuItem = menuItemRepository.findByIdAndDeletedAtIsNull(itemRequest.menuItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món ăn."));

            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                logger.warn("BR-33 violation blocked: dish {} is not available", menuItem.getItemName());
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Món " + menuItem.getItemName() + " hiện không còn sẵn. Vui lòng chọn món khác.");
            }

            menuItems.add(menuItem);
        }

        OrderType orderType = request.orderType() != null ? request.orderType() : OrderType.DINE_IN;
        Order order = new Order(UUID.randomUUID().toString(), table.getId(), staffId, orderType);
        order.setTable(table);
        Order savedOrder = orderRepository.save(order);
        if (savedOrder == null) savedOrder = order;

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
                if (savedTicket == null) savedTicket = ticket;

                publishTicketEvent(new KitchenTicketEvent("CREATED", KitchenTicketResponse.from(savedTicket)));
            }
        }

        return OrderResponse.from(savedOrder, savedItems);
    }

    @Transactional
    public CashCheckoutResponse checkoutCash(String tableId, CashCheckoutRequest request) {
        return checkoutWithPaymentMethod(tableId, request, "TIEN_MAT");
    }

    @Transactional
    public CashCheckoutResponse checkoutWithPaymentMethod(String tableId, CashCheckoutRequest request, String paymentMethod) {
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

        LoyaltySettings currentSettings = loyaltySettings.findById("default")
            .orElseGet(() -> loyaltySettings.save(new LoyaltySettings("default", new BigDecimal("5.000"), BigDecimal.ONE)));
        BigDecimal pointValueVnd = currentSettings.getPointValueVnd();

        BigDecimal pointsRedeemed = BigDecimal.ZERO;
        BigDecimal pointsDiscountAmount = BigDecimal.ZERO;
        LoyaltyAccount account = null;

        if (!phone.isBlank()) {
            if (!phone.matches("^[0-9+]{8,20}$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ.");
            // SỬA TẠI ĐÂY: Dùng Pessimistic Lock lockByCustomerPhone để tránh Race Condition
            account = loyaltyAccounts.lockByCustomerPhone(phone).orElse(null);
            if (Boolean.TRUE.equals(request.usePoints()) && account != null) {
                BigDecimal balance = account.getPointsBalance();
                if (balance != null && balance.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal maxPointsPossible = afterDiscount.divide(pointValueVnd, 0, RoundingMode.DOWN);
                    BigDecimal availablePoints = balance.min(maxPointsPossible).setScale(0, RoundingMode.DOWN);
                    // SỬA TẠI ĐÂY: Nếu có pointsToUse thì dùng đúng số điểm đó, nếu không mới dùng toàn bộ điểm khả dụng
                    if (request.pointsToUse() != null && request.pointsToUse().compareTo(BigDecimal.ZERO) > 0) {
                        pointsRedeemed = request.pointsToUse().setScale(0, RoundingMode.DOWN).min(availablePoints);
                    } else {
                        pointsRedeemed = availablePoints;
                    }
                    if (pointsRedeemed.compareTo(BigDecimal.ZERO) > 0) {
                        account.redeemPoints(pointsRedeemed);
                        pointsDiscountAmount = pointsRedeemed.multiply(pointValueVnd).setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }
        } else if (Boolean.TRUE.equals(request.usePoints())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhập số điện thoại để sử dụng điểm.");
        }

        BigDecimal total = afterDiscount.subtract(pointsDiscountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal pointsAwarded = BigDecimal.ZERO;
        if (!phone.isBlank()) {
            BigDecimal earnRate = currentSettings.getEarnRatePercent();
            BigDecimal rawEarnAmount = total.multiply(earnRate).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            pointsAwarded = rawEarnAmount.divide(pointValueVnd, 0, RoundingMode.HALF_UP);
        }

        String voucherId = null;
        if (!voucherCode.isBlank()) {
            List<String> ids = jdbcTemplate.query(
                "SELECT id FROM vouchers WHERE UPPER(voucher_code) = UPPER(?) AND deleted_at IS NULL LIMIT 1",
                (rs, rowNum) -> rs.getString(1), voucherCode);
            if (!ids.isEmpty()) voucherId = ids.get(0);
        }

        String voucherInvoiceId = createCheckoutInvoices(
            openOrders, phone, voucherId, voucherCode, discount, pointsDiscountAmount, pointsAwarded
        );

        if (!voucherCode.isBlank()) {
            VoucherValidationResponse used = voucherService.redeemForInvoice(
                new VoucherValidationRequest(voucherCode, phone, beforeDiscount), voucherInvoiceId);
            if (!used.valid()) throw new ResponseStatusException(HttpStatus.CONFLICT, used.message());
        }

        if (!phone.isBlank()) {
            if (account == null) account = new LoyaltyAccount(java.util.UUID.randomUUID().toString(), phone);
            account.addPoints(pointsAwarded);
            loyaltyAccounts.save(account);
        }

        for (Order order : openOrders) {
            order.setStatus(OrderStatus.COMPLETED);
            order.setClosedAt(java.time.LocalDateTime.now());
            orderRepository.save(order);
        }

        TableStatus oldStatus = table.getStatus();
        table.setStatus(TableStatus.AVAILABLE);
        tableRepository.save(table);

        // BỔ SUNG: Phát WebSocket thông báo bàn đã chuyển sang AVAILABLE cho POS & Sơ đồ mặt bằng
        try {
            String zoneName = table.getZone() != null ? table.getZone().getZoneName() : null;
            messagingTemplate.convertAndSend("/topic/table-status",
                new com.huongViet.table.TableStatusMessage(table.getId(), table.getTableNumber(), oldStatus, TableStatus.AVAILABLE, zoneName));
        } catch (Exception ex) {
            logger.warn("Không thể broadcast trạng thái bàn {}: {}", table.getTableNumber(), ex.getMessage());
        }

        return new CashCheckoutResponse(
            tableId, subtotal, vat, discount, pointsRedeemed, total, pointsAwarded, table.getTableNumber()
        );
    }

    private String findCustomerIdByPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        List<String> ids = jdbcTemplate.queryForList(
            "SELECT id FROM users WHERE phone_number = ? AND role = 'CUSTOMER' AND deleted_at IS NULL LIMIT 1",
            String.class, phone);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String createCheckoutInvoices(List<Order> orders, String phone, String voucherId,
                                          String voucherCode, BigDecimal voucherDiscount,
                                          BigDecimal pointsDiscountAmount, BigDecimal pointsAwarded) {
        String voucherInvoiceId = null;
        boolean first = true;
        String customerId = findCustomerIdByPhone(phone);
        for (Order order : orders) {
            List<OrderItem> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .filter(i -> i.getCookingStatus() != CookingStatus.CANCELLED).toList();
            BigDecimal itemSubtotal = items.stream()
                .map(i -> i.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = itemSubtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal invoiceVoucherDiscount = first ? voucherDiscount : BigDecimal.ZERO;
            BigDecimal invoicePointsDiscount = first ? pointsDiscountAmount : BigDecimal.ZERO;
            BigDecimal finalAmount = itemSubtotal.add(tax).subtract(invoiceVoucherDiscount).subtract(invoicePointsDiscount)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            String invoiceId = UUID.randomUUID().toString();
            String invoiceCode = "HV-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(java.time.LocalDateTime.now()) + "-" + invoiceId.substring(0, 8).toUpperCase(java.util.Locale.ROOT);
            String currentVoucherId = first ? voucherId : null;
            String currentVoucherCode = first && voucherId != null ? voucherCode : null;
            int earned = first ? pointsAwarded.setScale(0, RoundingMode.DOWN).intValue() : 0;
            jdbcTemplate.update("INSERT INTO invoices (id, invoice_code, order_id, customer_id, staff_id, voucher_id, " +
                    "voucher_code_snapshot, voucher_discount_amount, subtotal_amount, discount_amount, tax_amount, " +
                    "final_amount, deposit_deducted, loyalty_points_earned, status, paid_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'PAID', CURRENT_TIMESTAMP(6))",
                invoiceId, invoiceCode, order.getId(), customerId, SystemUserInitializer.SYSTEM_USER_ID, currentVoucherId,
                currentVoucherCode, invoiceVoucherDiscount, itemSubtotal, invoiceVoucherDiscount.add(invoicePointsDiscount),
                tax, finalAmount, earned);
            for (OrderItem item : items) {
                String dishName = item.getMenuItem() != null ? item.getMenuItem().getItemName() :
                    menuItemRepository.findById(item.getMenuItemId()).map(MenuItem::getItemName).orElse("Món đã xóa");
                BigDecimal lineTotal = item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
                jdbcTemplate.update("INSERT INTO invoice_items (id, invoice_id, dish_id, dish_name_snapshot, " +
                        "unit_price_snapshot, quantity, total_price) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), invoiceId, item.getMenuItemId(), dishName,
                    item.getUnitPriceSnapshot(), item.getQuantity(), lineTotal);
            }
            if (first) voucherInvoiceId = invoiceId;
            first = false;
        }
        return voucherInvoiceId;
    }

    public List<OrderResponse> listAll(String tableId, OrderStatus status) {
        List<Order> orders;
        if (tableId != null && !tableId.isBlank()) {
            orders = orderRepository.findByTableIdOrderByCreatedAtDesc(tableId);
            if (status != null) {
                orders = orders.stream().filter(order -> order.getStatus() == status).toList();
            }
        } else if (status != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtAsc(status);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }
        return orders.stream().map(this::toResponse).toList();
    }

    public OrderResponse getById(String id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
        return toResponse(order);
    }

    public OrderResponse getActiveByTable(String tableId) {
        Order order = orderRepository
            .findFirstByTableIdAndStatusOrderByCreatedAtDesc(tableId, OrderStatus.IN_PROGRESS)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bàn này chưa có đơn hàng đang mở."));
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        return OrderResponse.from(order, items);
    }

    @Transactional
    public OrderResponse addItem(String orderId, AddOrderItemRequest request, String staffId) {
        Order order = findOpenOrderOrThrow(orderId);

        MenuItem menuItem = menuItemRepository.findByIdAndDeletedAtIsNull(request.menuItemId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món ăn."));

        if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Món " + menuItem.getItemName() + " hiện không còn sẵn. Vui lòng chọn món khác.");
        }

        OrderItem item = new OrderItem(
            UUID.randomUUID().toString(),
            order.getId(),
            menuItem.getId(),
            request.quantity(),
            menuItem.getCurrentPrice(),
            request.note()
        );
        item.setMenuItem(menuItem);
        OrderItem savedItem = orderItemRepository.save(item);

        writeAudit(savedItem.getId(), staffId, "ADD_ITEM", 0, savedItem.getQuantity(),
            CookingStatus.QUEUED, savedItem.getCookingStatus(), "Thêm món vào đơn");

        createKitchenTicket(order, savedItem, menuItem);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateItem(String orderId, String itemId, UpdateOrderItemRequest request, String staffId) {
        Order order = findOpenOrderOrThrow(orderId);
        OrderItem item = findItemOfOrder(order, itemId);
        assertEditable(item);

        int oldQuantity = item.getQuantity();
        CookingStatus oldStatus = item.getCookingStatus();
        String oldNote = item.getNote();

        if (request.quantity() != null) {
            item.setQuantity(request.quantity());
        }
        if (request.note() != null) {
            item.setNote(request.note().isBlank() ? null : request.note().trim());
        }
        OrderItem savedItem = orderItemRepository.save(item);

        writeAudit(savedItem.getId(), staffId, "UPDATE_ITEM", oldQuantity, savedItem.getQuantity(),
            oldStatus, savedItem.getCookingStatus(), describeChange(oldQuantity, savedItem.getQuantity(), oldNote, savedItem.getNote()));

        syncKitchenTicket(savedItem);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelItem(String orderId, String itemId, CancelOrderItemRequest request, String staffId) {
        Order order = findOpenOrderOrThrow(orderId);
        OrderItem item = findItemOfOrder(order, itemId);
        KitchenTicket ticket = kitchenTicketRepository.findByOrderItemId(itemId).orElse(null);
        assertCancellable(item, ticket);

        int oldQuantity = item.getQuantity();
        CookingStatus oldStatus = item.getCookingStatus();
        String reason = (request.reason() == null || request.reason().isBlank())
            ? "Khách đổi món"
            : request.reason().trim();

        item.setCookingStatus(CookingStatus.CANCELLED);
        item.setCancelReason(reason);
        OrderItem savedItem = orderItemRepository.save(item);

        writeAudit(savedItem.getId(), staffId, "CANCEL_ITEM", oldQuantity, oldQuantity,
            oldStatus, CookingStatus.CANCELLED, reason);

        if (ticket != null) {
            ticket.setStatus(CookingStatus.CANCELLED);
            KitchenTicket savedTicket = kitchenTicketRepository.save(ticket);
            publishTicketEvent(new KitchenTicketEvent("UPDATED", KitchenTicketResponse.from(savedTicket)));
        }

        return toResponse(order);
    }

    private void assertCancellable(OrderItem item, KitchenTicket ticket) {
        CookingStatus itemStatus = item.getCookingStatus();
        if (itemStatus == CookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Món này đã được huỷ trước đó.");
        }

        CookingStatus ticketStatus = (ticket != null) ? ticket.getStatus() : null;
        boolean ticketStarted = ticketStatus != null
            && ticketStatus != CookingStatus.QUEUED
            && ticketStatus != CookingStatus.CANCELLED;

        if (itemStatus != CookingStatus.QUEUED || ticketStarted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ huỷ được món còn ở mục chờ chế biến.");
        }
    }

    private void assertEditable(OrderItem item) {
        CookingStatus status = item.getCookingStatus();
        if (status == CookingStatus.READY || status == CookingStatus.SERVED || status == CookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Món đã hoàn tất hoặc đã hủy, không thể thay đổi.");
        }
    }

    private Order findOpenOrderOrThrow(String orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));

        if (order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã đóng, không thể điều chỉnh.");
        }
        return order;
    }

    private OrderItem findItemOfOrder(Order order, String itemId) {
        OrderItem item = orderItemRepository.findById(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món trong đơn hàng."));

        if (!item.getOrderId().equals(order.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Món này không thuộc đơn hàng.");
        }
        return item;
    }

    private void createKitchenTicket(Order order, OrderItem item, MenuItem menuItem) {
        RestaurantTable table = order.getTable();
        String tableNumber = (table != null) ? table.getTableNumber() : "";

        KitchenTicket ticket = new KitchenTicket(
            UUID.randomUUID().toString(),
            item.getId(),
            order.getId(),
            tableNumber,
            menuItem.getItemName(),
            item.getQuantity(),
            item.getNote()
        );
        KitchenTicket savedTicket = kitchenTicketRepository.save(ticket);
        publishTicketEvent(new KitchenTicketEvent("CREATED", KitchenTicketResponse.from(savedTicket)));
    }

    private void syncKitchenTicket(OrderItem item) {
        kitchenTicketRepository.findByOrderItemId(item.getId()).ifPresent(ticket -> {
            ticket.setQuantity(item.getQuantity());
            ticket.setNote(item.getNote());
            KitchenTicket savedTicket = kitchenTicketRepository.save(ticket);
            publishTicketEvent(new KitchenTicketEvent("UPDATED", KitchenTicketResponse.from(savedTicket)));
        });
    }

    private void writeAudit(String orderItemId,
                             String staffId,
                             String action,
                             int oldQuantity,
                             int newQuantity,
                             CookingStatus oldStatus,
                             CookingStatus newStatus,
                             String reason) {
        orderItemAuditRepository.save(new OrderItemAudit(
            UUID.randomUUID().toString(),
            orderItemId,
            staffId,
            action,
            oldQuantity,
            newQuantity,
            oldStatus,
            newStatus,
            (reason == null || reason.isBlank()) ? "Không nêu lý do" : reason
        ));
    }

    private String describeChange(int oldQuantity, int newQuantity, String oldNote, String newNote) {
        StringBuilder description = new StringBuilder();
        if (oldQuantity != newQuantity) {
            description.append("Số lượng ").append(oldQuantity).append(" → ").append(newQuantity);
        }
        if (!java.util.Objects.equals(oldNote, newNote)) {
            if (description.length() > 0) description.append("; ");
            description.append("Cập nhật ghi chú");
        }
        return description.length() == 0 ? "Cập nhật món" : description.toString();
    }

    private void publishTicketEvent(KitchenTicketEvent event) {
        try {
            messagingTemplate.convertAndSend(KITCHEN_TICKETS_TOPIC, event);
        } catch (Exception ex) {
            logger.error("Failed to broadcast kitchen ticket event: ", ex);
        }
    }
}
