package com.huongViet.order;

import com.huongViet.menu.MenuItem;
import com.huongViet.menu.MenuItemRepository;
import com.huongViet.auth.SystemUserInitializer;
import com.huongViet.reservation.Reservation;
import com.huongViet.reservation.ReservationAudit;
import com.huongViet.reservation.ReservationAuditRepository;
import com.huongViet.reservation.ReservationRepository;
import com.huongViet.reservation.ReservationService;
import com.huongViet.reservation.ReservationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableStatus;
import com.huongViet.payment.LoyaltyAccount;
import com.huongViet.payment.LoyaltyAccountRepository;
import com.huongViet.payment.LoyaltySettings;
import com.huongViet.payment.LoyaltySettingsRepository;
import com.huongViet.payment.PaymentOrder;
import com.huongViet.payment.PaymentOrderRepository;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final String KITCHEN_TICKETS_TOPIC = "/topic/kitchen/tickets";
    public static final BigDecimal VIP_ROOM_FEE = ReservationService.VIP_DEPOSIT_AMOUNT;

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
    private final ReservationRepository reservationRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final ReservationAuditRepository reservationAuditRepository;

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
                        JdbcTemplate jdbcTemplate,
                        ReservationRepository reservationRepository,
                        PaymentOrderRepository paymentOrderRepository) {
        this(orderRepository, orderItemRepository, kitchenTicketRepository, orderItemAuditRepository,
            menuItemRepository, tableRepository, messagingTemplate, loyaltyAccounts, loyaltySettings,
            voucherService, jdbcTemplate, reservationRepository, paymentOrderRepository, null);
    }

    @Autowired
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
                        JdbcTemplate jdbcTemplate,
                        ReservationRepository reservationRepository,
                        PaymentOrderRepository paymentOrderRepository,
                        ReservationAuditRepository reservationAuditRepository) {
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
        this.reservationRepository = reservationRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.reservationAuditRepository = reservationAuditRepository;
    }

    /**
     * Tìm tất cả ID các bàn liên quan (nếu bàn này thuộc một cụm bàn gộp đang phục vụ).
     */
    public List<String> findRelatedTableIds(String tableId) {
        if (tableId == null || tableId.isBlank()) {
            return new ArrayList<>();
        }

        return Reservation.relatedArrivedTableIds(tableId,
            reservationRepository.findActiveReservationsForDate(LocalDate.now()));
    }

    /**
     * Xác định tên hiển thị bàn (nếu bàn gộp thì nối Bàn 01 + Bàn 02).
     */
    private String resolveDisplayTableNumber(String tableId) {
        if (tableId == null) {
            return null;
        }
        List<String> relatedTableIds = findRelatedTableIds(tableId);
        if (relatedTableIds.size() > 1) {
            List<RestaurantTable> tables = tableRepository.findAllById(relatedTableIds);
            String display = tables.stream()
                    .map(RestaurantTable::getTableNumber)
                    .collect(Collectors.joining(" + "));
            if (display.length() > 100) {
                return display.substring(0, 97) + "...";
            }
            return display;
        }
        return tableRepository.findById(tableId).map(RestaurantTable::getTableNumber).orElse("Bàn ăn");
    }

    @Transactional
    public OrderResponse openSeatedOrder(String tableId, int partySize, String staffId) {
        RestaurantTable table = lockTable(tableId);
        if (table.getStatus() != TableStatus.OCCUPIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vui lòng mở bàn trước khi xếp khách.");
        }
        boolean exclusive = reservationRepository.findActiveReservationsForDate(LocalDate.now()).stream()
            .anyMatch(reservation -> reservation.blocksSharedSeating(tableId));
        if (exclusive) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bàn dành cho khách đặt trước không nhận khách ngồi ghép.");
        }
        int available = Order.availableSeats(table.getCapacity(), orderRepository.findSeatedOrdersByTableIds(List.of(tableId)));
        if (partySize < 1 || partySize > available) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bàn còn " + available + " ghế trống.");
        }
        Order order = new Order(UUID.randomUUID().toString(), tableId, staffId, OrderType.DINE_IN);
        order.setTable(table);
        order.setPartySize(partySize);
        return OrderResponse.from(orderRepository.save(order), List.of(), table.getTableNumber());
    }

    public List<OrderResponse> listSeatedOrders(String tableId) {
        return orderRepository.findSeatedOrdersByTableIds(findRelatedTableIds(tableId)).stream()
            .map(this::toResponse).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void releaseSeats(String orderId) {
        releaseSeats(orderId, null, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void releaseSeats(String orderId, String reservationAction, String staffId) {
        RestaurantTable table = lockTableForOrder(orderId);
        Order order = lockOrder(orderId);
        boolean assignedSeats = order.getPartySize() != null;
        if (assignedSeats ? !order.isSeated() : order.getStatus() != OrderStatus.IN_PROGRESS) return;

        boolean cancelledEmptyOrder = false;
        if (order.getStatus() == OrderStatus.IN_PROGRESS) {
            assertNoPendingVnpay(orderId);
            if (!payableItems(orderId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Hãy thanh toán đơn trước khi trả chỗ.");
            }
            order.setStatus(OrderStatus.CANCELLED);
            order.setClosedAt(LocalDateTime.now());
            cancelledEmptyOrder = true;
        }
        if (assignedSeats) order.releaseSeats();
        orderRepository.save(order);

        boolean revertedToConfirmed = false;
        if (cancelledEmptyOrder && order.getReservationId() != null && !order.getReservationId().isBlank()) {
            boolean hasOtherOrders = orderRepository.findByReservationId(order.getReservationId()).stream()
                .anyMatch(o -> !o.getId().equals(orderId) && o.getStatus() != OrderStatus.CANCELLED);
            if (!hasOtherOrders) {
                Reservation res = reservationRepository.findActiveByIdWithTableAndZone(order.getReservationId()).orElse(null);
                if (res != null && res.getStatus() == ReservationStatus.ARRIVED) {
                    String actor = (staffId != null && !staffId.isBlank()) ? staffId : SystemUserInitializer.SYSTEM_USER_ID;
                    if ("REVERT_CONFIRMED".equalsIgnoreCase(reservationAction)) {
                        res.setStatus(ReservationStatus.CONFIRMED);
                        reservationRepository.save(res);
                        recordReservationAudit(res.getId(), actor, ReservationStatus.ARRIVED, ReservationStatus.CONFIRMED,
                            res.getTableId(), res.getTableId(), "Hoàn tác Check-in từ POS");
                        broadcastReservationChange(res);
                        revertedToConfirmed = true;
                    } else {
                        res.setStatus(ReservationStatus.CANCELLED);
                        res.setCancellationReason("Hủy đơn và trả chỗ trên POS");
                        reservationRepository.save(res);
                        recordReservationAudit(res.getId(), actor, ReservationStatus.ARRIVED, ReservationStatus.CANCELLED,
                            res.getTableId(), null, "Hủy đơn và trả chỗ trên POS");
                        broadcastReservationChange(res);
                    }
                }
            }
        }

        if (!assignedSeats) return; // No individual seats to release on a reserved or legacy order.

        List<Reservation> todayReservations = reservationRepository.findActiveOrPaidReservationsForDate(LocalDate.now());
        List<String> relatedTableIds = todayReservations.stream()
            .filter(r -> (order.getReservationId() != null && order.getReservationId().equals(r.getId()))
                || ((r.getStatus() == ReservationStatus.ARRIVED || r.getStatus() == ReservationStatus.PAID)
                    && r.includesTable(table.getId())))
            .findFirst()
            .map(r -> java.util.stream.Stream.concat(
                    java.util.stream.Stream.ofNullable(r.getTableId()),
                    java.util.Arrays.stream(r.getMergedTableIds() == null ? new String[0] : r.getMergedTableIds().split(",")))
                .map(String::trim).filter(id -> !id.isEmpty()).distinct().sorted().toList())
            .orElseGet(() -> List.of(table.getId()));
        boolean othersSeated = orderRepository.findSeatedOrdersByTableIds(relatedTableIds).stream()
            .anyMatch(other -> !other.getId().equals(orderId));
        if (!othersSeated) {
            for (String relId : relatedTableIds) {
                RestaurantTable target = relId.equals(table.getId())
                    ? table
                    : tableRepository.findByIdAndDeletedAtIsNull(relId).orElse(null);
                if (target != null && target.getStatus() == TableStatus.OCCUPIED) {
                    releaseOrReserveTable(target, todayReservations, revertedToConfirmed ? null : order.getReservationId());
                }
            }
        }
    }

    private RestaurantTable lockTable(String tableId) {
        return tableRepository.lockActiveById(tableId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn."));
    }

    private RestaurantTable lockTableForOrder(String orderId) {
        return lockTable(orderRepository.findTableIdByOrderId(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.")));
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String staffId) {
        RestaurantTable table = lockTable(request.tableId());

        if (table.getStatus() != TableStatus.OCCUPIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể gọi món cho bàn đang sử dụng. Vui lòng mở bàn trước khi gọi món.");
        }

        List<String> relatedTableIds = findRelatedTableIds(table.getId());
        if (orderRepository.findSeatedOrdersByTableIds(relatedTableIds).stream().anyMatch(Order::isSeated)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vui lòng chọn đơn cần thêm món hoặc mở đơn mới.");
        }
        Order existingOrder = null;
        for (String relId : relatedTableIds) {
            var found = orderRepository.findFirstByTableIdAndStatusOrderByCreatedAtDesc(relId, OrderStatus.IN_PROGRESS);
            if (found.isPresent()) {
                existingOrder = found.get();
                break;
            }
        }

        List<MenuItem> menuItems = new ArrayList<>();
        for (OrderItemRequest itemRequest : request.items()) {
            MenuItem menuItem = menuItemRepository.findByIdAndDeletedAtIsNull(itemRequest.menuItemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món ăn."));

            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Món " + menuItem.getItemName() + " hiện không còn sẵn. Vui lòng chọn món khác.");
            }
            menuItems.add(menuItem);
        }

        Order order;
        if (existingOrder != null) {
            order = findEditableOrder(existingOrder.getId());
        } else {
            OrderType orderType = request.orderType() != null ? request.orderType() : OrderType.DINE_IN;
            order = new Order(UUID.randomUUID().toString(), table.getId(), staffId, orderType);
            order.setTable(table);
            order = orderRepository.save(order);
        }

        String displayTableNumber = resolveDisplayTableNumber(table.getId());

        List<OrderItem> savedItems = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            OrderItemRequest itemRequest = request.items().get(i);
            MenuItem menuItem = menuItems.get(i);

            OrderItem orderItem = new OrderItem(
                    UUID.randomUUID().toString(),
                    order.getId(),
                    menuItem.getId(),
                    itemRequest.quantity(),
                    menuItem.getCurrentPrice(),
                    itemRequest.note()
            );
            orderItem.setMenuItem(menuItem);
            OrderItem savedItem = orderItemRepository.save(orderItem);
            savedItems.add(savedItem);
        }

        boolean submit = request.submit() == null || request.submit();
        if (submit) {
            for (int i = 0; i < savedItems.size(); i++) {
                OrderItem orderItem = savedItems.get(i);
                MenuItem menuItem = menuItems.get(i);

                saveKitchenTicket(order, orderItem, menuItem, displayTableNumber, existingOrder != null);
            }
        }

        return toResponse(order);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CashCheckoutResponse checkoutOrderCash(String orderId, CashCheckoutRequest request, String staffId) {
        return checkoutOrder(orderId, request, staffId, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CashCheckoutResponse checkoutVnpayOrder(PaymentOrder payment, CashCheckoutRequest request) {
        return checkoutOrder(payment.getDiningOrderId(), request, null, payment);
    }

    @Transactional
    public BigDecimal prepareVnpayCheckout(String orderId, String tableId) {
        RestaurantTable table = lockTable(tableId);
        Order order = findEditableOrder(orderId);
        if (!order.getTableId().equals(tableId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn không thuộc bàn đã chọn.");
        }
        List<String> relatedTableIds = order.getPartySize() == null
            ? findRelatedTableIds(table.getId()) : List.of(table.getId());
        VipBillingInfo vipInfo = resolveVipBillingInfo(table, List.of(order), relatedTableIds);
        BigDecimal subtotal = subtotal(payableItems(orderId));
        BigDecimal taxableSubtotal = subtotal.add(vipInfo.vipFeeAmount());
        return taxableSubtotal.add(vat(taxableSubtotal));
    }

    private CashCheckoutResponse checkoutOrder(String orderId, CashCheckoutRequest request, String staffId,
                                               PaymentOrder payment) {
        RestaurantTable table = lockTableForOrder(orderId);
        Order order = payment == null ? findEditableOrder(orderId) : findOpenOrderOrThrow(orderId);
        List<String> relatedTableIds = findRelatedTableIds(table.getId());
        boolean releaseLegacyTable = order.getPartySize() == null &&
            orderRepository.findSeatedOrdersByTableIds(relatedTableIds).stream().noneMatch(o -> !o.getId().equals(orderId));
        return settle(table, relatedTableIds, List.of(order), request, staffId, releaseLegacyTable, false, payment);
    }

    @Transactional
    public CashCheckoutResponse checkoutCash(String tableId, CashCheckoutRequest request) {
        return checkoutCash(tableId, request, null);
    }

    @Transactional
    public CashCheckoutResponse checkoutCash(String tableId, CashCheckoutRequest request, String staffId) {
        return checkoutWithPaymentMethod(tableId, request, "TIEN_MAT", staffId);
    }

    @Transactional
    public CashCheckoutResponse checkoutWithPaymentMethod(String tableId, CashCheckoutRequest request, String paymentMethod) {
        return checkoutWithPaymentMethod(tableId, request, paymentMethod, null);
    }

    @Transactional
    public CashCheckoutResponse checkoutWithPaymentMethod(String tableId, CashCheckoutRequest request, String paymentMethod, String staffId) {
        RestaurantTable table = lockTable(tableId);
        List<String> relatedTableIds = findRelatedTableIds(tableId);
        if (orderRepository.findSeatedOrdersByTableIds(relatedTableIds).stream().anyMatch(Order::isSeated)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bàn có chỗ ngồi ghép, vui lòng thanh toán theo từng đơn.");
        }

        List<Order> openOrders = new ArrayList<>();
        for (String relId : relatedTableIds) {
            List<Order> found = orderRepository.findByTableIdOrderByCreatedAtDesc(relId);
            if (found != null) {
                for (Order o : found) {
                    if (o != null && o.getStatus() == OrderStatus.IN_PROGRESS) {
                        openOrders.add(o);
                    }
                }
            }
        }

        if (openOrders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bàn chưa có món đã gửi bếp để thanh toán.");
        }

        openOrders.forEach(order -> assertNoPendingVnpay(order.getId()));
        return settle(table, relatedTableIds, openOrders, request, staffId, true, true, null);
    }

    private List<OrderItem> payableItems(String orderId) {
        return orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
            .filter(item -> item.getCookingStatus() != CookingStatus.CANCELLED).toList();
    }

    private BigDecimal subtotal(List<OrderItem> items) {
        return items.stream().map(i -> i.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal vat(BigDecimal subtotal) {
        return subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
    }

    private CashCheckoutResponse settle(RestaurantTable table, List<String> relatedTableIds, List<Order> openOrders,
                                         CashCheckoutRequest request, String staffId, boolean releaseLegacyTable,
                                         boolean legacyTableCheckout, PaymentOrder payment) {
        if (request == null) request = new CashCheckoutRequest(null, null, false);
        String tableId = table.getId();
        String displayTableNumber = relatedTableIds.size() > 1 ? resolveDisplayTableNumber(tableId) : table.getTableNumber();
        List<OrderItem> billItems = openOrders.stream().flatMap(o -> payableItems(o.getId()).stream()).toList();
        if (!legacyTableCheckout && billItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn chưa có món để thanh toán.");
        }
        VipBillingInfo vipInfo = resolveVipBillingInfo(table, openOrders, relatedTableIds);
        BigDecimal subtotal = subtotal(billItems);
        BigDecimal taxableSubtotal = subtotal.add(vipInfo.vipFeeAmount());
        BigDecimal vat = vat(taxableSubtotal);
        BigDecimal beforeDiscount = taxableSubtotal.add(vat);

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
            account = loyaltyAccounts.lockByCustomerPhone(phone).orElse(null);
            if (Boolean.TRUE.equals(request.usePoints()) && account != null) {
                BigDecimal balance = account.getPointsBalance();
                if (balance != null && balance.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal maxPointsPossible = afterDiscount.divide(pointValueVnd, 0, RoundingMode.DOWN);
                    BigDecimal availablePoints = balance.min(maxPointsPossible).setScale(0, RoundingMode.DOWN);
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
        if (request.expectedTotal() != null && request.expectedTotal().compareTo(total) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền đã thay đổi. Vui lòng kiểm tra lại hóa đơn.");
        }
        if (payment != null && (beforeDiscount.compareTo(payment.getSubtotal()) != 0 ||
            discount.compareTo(payment.getDiscountAmount()) != 0 ||
            pointsRedeemed.compareTo(payment.getPointsUsed()) != 0 || total.compareTo(payment.getAmount()) != 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hóa đơn không còn khớp với giao dịch VNPay; cần đối soát.");
        }

        BigDecimal pointsAwarded = BigDecimal.ZERO;
        if (!phone.isBlank()) {
            BigDecimal earnRate = currentSettings.getEarnRatePercent();
            pointsAwarded = total.multiply(earnRate).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        }

        String voucherId = null;
        if (!voucherCode.isBlank()) {
            List<String> ids = jdbcTemplate.query(
                    "SELECT id FROM vouchers WHERE UPPER(voucher_code) = UPPER(?) AND deleted_at IS NULL LIMIT 1",
                    (rs, rowNum) -> rs.getString(1), voucherCode);
            if (!ids.isEmpty()) voucherId = ids.get(0);
        }

        String effectiveStaffId = (staffId != null && !staffId.isBlank()) ? staffId : SystemUserInitializer.SYSTEM_USER_ID;

        Reservation matchingReservation = null;
        String linkedReservationId = openOrders.stream()
            .map(Order::getReservationId)
            .filter(id -> id != null && !id.isBlank())
            .findFirst()
            .orElse(null);
        if (linkedReservationId != null) {
            matchingReservation = reservationRepository.findActiveByIdWithTableAndZone(linkedReservationId).orElse(null);
            if (matchingReservation != null && (matchingReservation.getStatus() == ReservationStatus.ARRIVED
                || matchingReservation.getStatus() == ReservationStatus.CONFIRMED)) {
                ReservationStatus prevStatus = matchingReservation.getStatus();
                matchingReservation.setStatus(ReservationStatus.PAID);
                reservationRepository.save(matchingReservation);
                recordReservationAudit(matchingReservation.getId(), effectiveStaffId, prevStatus, ReservationStatus.PAID,
                    matchingReservation.getTableId(), matchingReservation.getTableId(), "Thanh toán hóa đơn tại POS");
                broadcastReservationChange(matchingReservation);
            }
        }

        if (matchingReservation == null) {
            List<Reservation> activeReservations = reservationRepository.findActiveReservationsForDate(LocalDate.now());
            for (Reservation r : activeReservations) {
                if (relatedTableIds.stream().noneMatch(r::includesTable)) continue;
                if (!legacyTableCheckout && r.getStatus() != ReservationStatus.ARRIVED) continue;
                if (matchingReservation == null) matchingReservation = r;
                if (r.getStatus() == ReservationStatus.ARRIVED || r.getStatus() == ReservationStatus.CONFIRMED) {
                    ReservationStatus prevStatus = r.getStatus();
                    r.setStatus(ReservationStatus.PAID);
                    reservationRepository.save(r);
                    recordReservationAudit(r.getId(), effectiveStaffId, prevStatus, ReservationStatus.PAID,
                        r.getTableId(), r.getTableId(), "Thanh toán hóa đơn tại POS");
                    broadcastReservationChange(r);
                }
                if (!legacyTableCheckout) break;
            }
        }

        String actualCustomerId = null;
        String actualCustomerPhone = null;
        if (matchingReservation != null) {
            // 1. Có đặt bàn trước: Ưu tiên danh tính người đặt bàn (Tiêu chí 1 & Tiêu chí 3)
            actualCustomerId = matchingReservation.getCustomerId();
            actualCustomerPhone = matchingReservation.getGuestPhone();
            if ((actualCustomerId == null || actualCustomerId.isBlank()) && actualCustomerPhone != null && !actualCustomerPhone.isBlank()) {
                actualCustomerId = findCustomerIdByPhone(actualCustomerPhone);
            }
        } else if (!phone.isBlank()) {
            // 2. Không đặt bàn trước (Khách vãng lai): Dùng SĐT thanh toán để định danh & cấp quyền đánh giá (Tiêu chí 2)
            actualCustomerPhone = phone;
            actualCustomerId = findCustomerIdByPhone(phone);
        }

        String voucherInvoiceId = createCheckoutInvoices(
                openOrders, actualCustomerId, actualCustomerPhone, phone, voucherId, voucherCode, discount,
                pointsDiscountAmount, pointsAwarded, vipInfo.vipFeeAmount(), vipInfo.vipDepositPaid(), effectiveStaffId
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
            order.setClosedAt(LocalDateTime.now());
            orderRepository.save(order);
        }

        if (releaseLegacyTable) {
            makeTableAvailable(table);
            relatedTableIds.stream().filter(id -> !id.equals(tableId)).forEach(id ->
                tableRepository.findByIdAndDeletedAtIsNull(id).filter(t -> t.getStatus() == TableStatus.OCCUPIED)
                    .ifPresent(this::makeTableAvailable));
        }

        return new CashCheckoutResponse(
                tableId, subtotal, vat, discount, pointsRedeemed, total, pointsAwarded, displayTableNumber,
                vipInfo.vipFeeAmount(), vipInfo.vipDepositPaid()
        );
    }

    private record VipBillingInfo(BigDecimal vipFeeAmount, BigDecimal vipDepositPaid) {}

    private boolean isVipTable(RestaurantTable table, List<String> relatedTableIds) {
        if (table != null && table.getZone() != null && Boolean.TRUE.equals(table.getZone().getIsVip())) {
            return true;
        }
        for (String id : relatedTableIds) {
            var withZone = tableRepository.findByIdActiveWithZone(id);
            if (withZone.isPresent() && withZone.get().getZone() != null
                && Boolean.TRUE.equals(withZone.get().getZone().getIsVip())) {
                return true;
            }
        }
        return false;
    }

    private VipBillingInfo resolveVipBillingInfo(RestaurantTable table, List<Order> targetOrders,
                                                 List<String> relatedTableIds) {
        if (!isVipTable(table, relatedTableIds)) {
            return new VipBillingInfo(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        for (Order targetOrder : targetOrders) {
            if (targetOrder.getReservationId() != null && !targetOrder.getReservationId().isBlank()) {
                Reservation linked = reservationRepository.findActiveByIdWithTableAndZone(targetOrder.getReservationId()).orElse(null);
                if (linked != null && linked.getDepositAmount() != null
                    && linked.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
                    return new VipBillingInfo(BigDecimal.ZERO, linked.getDepositAmount().setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

        List<Reservation> activeReservations = reservationRepository.findActiveReservationsForDate(LocalDate.now());
        for (Reservation r : activeReservations) {
            if (r.getStatus() == ReservationStatus.ARRIVED
                && relatedTableIds.stream().anyMatch(r::includesTable)
                && r.getDepositAmount() != null
                && r.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
                return new VipBillingInfo(BigDecimal.ZERO, r.getDepositAmount().setScale(2, RoundingMode.HALF_UP));
            }
        }

        Set<String> targetOrderIds = targetOrders.stream().map(Order::getId).collect(Collectors.toSet());
        List<String> otherCompletedSeatedIds = orderRepository.findSeatedOrdersByTableIds(relatedTableIds).stream()
            .filter(o -> o.getStatus() == OrderStatus.COMPLETED && !targetOrderIds.contains(o.getId()))
            .map(Order::getId)
            .toList();

        if (hasPaidVipFeeInvoice(otherCompletedSeatedIds)) {
            return new VipBillingInfo(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        return new VipBillingInfo(VIP_ROOM_FEE.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO);
    }

    private boolean hasPaidVipFeeInvoice(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return false;
        }
        try {
            String placeholders = String.join(",", Collections.nCopies(orderIds.size(), "?"));
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE order_id IN (" + placeholders + ") AND vip_fee_amount > 0 AND status = 'PAID'",
                Integer.class,
                orderIds.toArray()
            );
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private void releaseOrReserveTable(RestaurantTable table, List<Reservation> todayReservations, String excludeReservationId) {
        boolean hasConfirmedInWindow = todayReservations != null && todayReservations.stream().anyMatch(r ->
            (excludeReservationId == null || !excludeReservationId.equals(r.getId()))
            && r.getStatus() == ReservationStatus.CONFIRMED
            && r.includesTable(table.getId())
            && ReservationService.isWithinReservationWindow(r.getReservationDate(), r.getReservationTime())
        );
        if (hasConfirmedInWindow) {
            TableStatus oldStatus = table.getStatus();
            table.setStatus(TableStatus.RESERVED);
            tableRepository.save(table);
            try {
                String zoneName = table.getZone() != null ? table.getZone().getZoneName() : null;
                messagingTemplate.convertAndSend("/topic/table-status",
                    new com.huongViet.table.TableStatusMessage(table.getId(), table.getTableNumber(), oldStatus,
                        TableStatus.RESERVED, zoneName));
            } catch (Exception ex) {
                logger.warn("Không thể broadcast giữ bàn {}: {}", table.getTableNumber(), ex.getMessage());
            }
            return;
        }
        makeTableAvailable(table);
    }

    private void recordReservationAudit(String reservationId, String changedBy,
                                        ReservationStatus oldStatus, ReservationStatus newStatus,
                                        String oldTableId, String newTableId, String reason) {
        if (reservationAuditRepository == null || reservationId == null) return;
        try {
            reservationAuditRepository.save(new ReservationAudit(
                UUID.randomUUID().toString(), reservationId, changedBy, oldStatus, newStatus,
                oldTableId, newTableId, reason
            ));
        } catch (Exception ex) {
            logger.warn("Không thể ghi audit đặt bàn {}: {}", reservationId, ex.getMessage());
        }
    }

    private void broadcastReservationChange(Reservation reservation) {
        if (messagingTemplate == null || reservation == null) return;
        try {
            messagingTemplate.convertAndSend("/topic/reservations", java.util.Map.of(
                "reservationId", reservation.getId(),
                "status", reservation.getStatus().name()
            ));
        } catch (Exception ex) {
            logger.warn("Không thể broadcast cập nhật đặt bàn {}: {}", reservation.getId(), ex.getMessage());
        }
    }

    private void makeTableAvailable(RestaurantTable table) {
        TableStatus oldStatus = table.getStatus();
        table.setStatus(TableStatus.AVAILABLE);
        tableRepository.save(table);
        try {
            String zoneName = table.getZone() != null ? table.getZone().getZoneName() : null;
            messagingTemplate.convertAndSend("/topic/table-status",
                new com.huongViet.table.TableStatusMessage(table.getId(), table.getTableNumber(), oldStatus,
                    TableStatus.AVAILABLE, zoneName));
        } catch (Exception ex) {
            logger.warn("Không thể broadcast giải phóng bàn {}: {}", table.getTableNumber(), ex.getMessage());
        }
    }

    private String createCheckoutInvoices(List<Order> orders, String customerId, String customerPhone,
                                          String loyaltyPhone, String voucherId,
                                          String voucherCode, BigDecimal voucherDiscount,
                                          BigDecimal pointsDiscountAmount, BigDecimal pointsAwarded,
                                          BigDecimal vipFeeAmount, BigDecimal vipDepositPaid,
                                          String staffId) {
        String voucherInvoiceId = null;
        boolean first = true;
        String effectiveLoyaltyPhone = (loyaltyPhone != null && !loyaltyPhone.isBlank()) ? loyaltyPhone : null;
        for (Order order : orders) {
            List<OrderItem> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                    .filter(i -> i.getCookingStatus() != CookingStatus.CANCELLED)
                    .collect(Collectors.toList());
            BigDecimal itemSubtotal = items.stream()
                    .map(i -> i.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal invoiceVipFee = first && vipFeeAmount != null ? vipFeeAmount : BigDecimal.ZERO;
            BigDecimal invoiceVipDepositPaid = first && vipDepositPaid != null ? vipDepositPaid : BigDecimal.ZERO;
            BigDecimal taxable = itemSubtotal.add(invoiceVipFee);
            BigDecimal tax = taxable.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal invoiceVoucherDiscount = first ? voucherDiscount : BigDecimal.ZERO;
            BigDecimal invoicePointsDiscount = first ? pointsDiscountAmount : BigDecimal.ZERO;
            BigDecimal finalAmount = taxable.add(tax).subtract(invoiceVoucherDiscount).subtract(invoicePointsDiscount)
                    .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            String invoiceId = UUID.randomUUID().toString();
            String invoiceCode = "HV-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .format(java.time.LocalDateTime.now()) + "-" + invoiceId.substring(0, 8).toUpperCase(java.util.Locale.ROOT);
            String currentVoucherId = first ? voucherId : null;
            String currentVoucherCode = first && voucherId != null ? voucherCode : null;
            int earned = first ? pointsAwarded.setScale(0, RoundingMode.DOWN).intValue() : 0;

            try {
                jdbcTemplate.update("INSERT INTO invoices (id, invoice_code, order_id, customer_id, staff_id, customer_phone, loyalty_phone, voucher_id, " +
                        "voucher_code_snapshot, voucher_discount_amount, subtotal_amount, discount_amount, tax_amount, " +
                        "final_amount, vip_fee_amount, vip_deposit_paid, deposit_deducted, loyalty_points_earned, status, paid_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'PAID', CURRENT_TIMESTAMP(6))",
                invoiceId, invoiceCode, order.getId(), customerId, staffId, customerPhone, effectiveLoyaltyPhone, currentVoucherId,
                currentVoucherCode, invoiceVoucherDiscount, itemSubtotal, invoiceVoucherDiscount.add(invoicePointsDiscount),
                tax, finalAmount, invoiceVipFee, invoiceVipDepositPaid, earned);
            } catch (Exception ex) {
                try {
                    jdbcTemplate.update("INSERT INTO invoices (id, invoice_code, order_id, customer_id, staff_id, customer_phone, loyalty_phone, voucher_id, " +
                            "voucher_code_snapshot, voucher_discount_amount, subtotal_amount, discount_amount, tax_amount, " +
                            "final_amount, deposit_deducted, loyalty_points_earned, status, paid_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'PAID', CURRENT_TIMESTAMP(6))",
                    invoiceId, invoiceCode, order.getId(), customerId, staffId, customerPhone, effectiveLoyaltyPhone, currentVoucherId,
                    currentVoucherCode, invoiceVoucherDiscount, itemSubtotal, invoiceVoucherDiscount.add(invoicePointsDiscount),
                    tax, finalAmount, earned);
                } catch (Exception legacyEx) {
                    jdbcTemplate.update("INSERT INTO invoices (id, invoice_code, order_id, customer_id, staff_id, customer_phone, voucher_id, " +
                            "voucher_code_snapshot, voucher_discount_amount, subtotal_amount, discount_amount, tax_amount, " +
                            "final_amount, deposit_deducted, loyalty_points_earned, status, paid_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'PAID', CURRENT_TIMESTAMP(6))",
                    invoiceId, invoiceCode, order.getId(), customerId, staffId, customerPhone, currentVoucherId,
                    currentVoucherCode, invoiceVoucherDiscount, itemSubtotal, invoiceVoucherDiscount.add(invoicePointsDiscount),
                    tax, finalAmount, earned);
                }
            }

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

    private String findCustomerIdByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        try {
            List<String> ids = jdbcTemplate.query(
                    "SELECT id FROM users WHERE phone_number = ? LIMIT 1",
                    (rs, rowNum) -> rs.getString(1), phone);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        } catch (Exception e) {
            logger.warn("Không tìm thấy customer id theo số điện thoại {}: {}", phone, e.getMessage());
        }
        return null;
    }

    public List<OrderResponse> listAll(String tableId, OrderStatus status) {
        List<Order> orders;
        if (tableId != null && !tableId.isBlank()) {
            List<String> relatedTableIds = findRelatedTableIds(tableId);
            orders = new ArrayList<>();
            for (String relId : relatedTableIds) {
                orders.addAll(orderRepository.findByTableIdOrderByCreatedAtDesc(relId));
            }
            if (status != null) {
                orders = orders.stream().filter(order -> order.getStatus() == status).collect(Collectors.toList());
            }
        } else if (status != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtAsc(status);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }
        return orders.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public OrderResponse getById(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
        return toResponse(order);
    }

    public OrderResponse getActiveByTable(String tableId) {
        List<String> relatedTableIds = findRelatedTableIds(tableId);
        for (String relId : relatedTableIds) {
            var found = orderRepository.findFirstByTableIdAndStatusOrderByCreatedAtDesc(relId, OrderStatus.IN_PROGRESS);
            if (found.isPresent()) {
                return toResponse(found.get());
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bàn này chưa có đơn hàng đang mở.");
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        VipBillingInfo vipInfo = new VipBillingInfo(BigDecimal.ZERO, BigDecimal.ZERO);
        if (order.getStatus() == OrderStatus.IN_PROGRESS && order.getTableId() != null) {
            List<String> relatedTableIds = order.getPartySize() == null
                ? findRelatedTableIds(order.getTableId())
                : List.of(order.getTableId());
            vipInfo = resolveVipBillingInfo(order.getTable(), List.of(order), relatedTableIds);
        }

        String resId = order.getReservationId();
        String resCode = null;
        String resGuestName = null;
        String resGuestPhone = null;
        String resTime = null;
        String resNotes = null;
        String resTableNumber = null;
        BigDecimal resDeposit = null;

        if (resId != null && !resId.isBlank()) {
            Reservation res = order.getReservation();
            if (res == null) {
                res = reservationRepository.findActiveByIdWithTableAndZone(resId).orElse(null);
            }
            if (res != null) {
                resCode = res.getBookingCode();
                resGuestName = res.getGuestName();
                resGuestPhone = res.getGuestPhone();
                resTime = res.getReservationTime() != null ? res.getReservationTime().toString().substring(0, 5) : null;
                resNotes = res.getSpecialRequests();
                resDeposit = res.getDepositAmount();
                resTableNumber = resolveReservationTableNumber(res);
            }
        }

        return OrderResponse.from(
            order, items, displayTableNumber(order), vipInfo.vipFeeAmount(), vipInfo.vipDepositPaid(),
            resId, resCode, resGuestName, resGuestPhone, resTime, resNotes, resTableNumber, resDeposit
        );
    }

    private String resolveReservationTableNumber(Reservation res) {
        if (res.getMergedTableIds() != null && !res.getMergedTableIds().isBlank()) {
            List<String> ids = java.util.Arrays.stream(res.getMergedTableIds().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (ids.size() > 1) {
                return tableRepository.findAllById(ids).stream()
                    .map(RestaurantTable::getTableNumber)
                    .collect(Collectors.joining(" + "));
            }
        }
        if (res.getTable() != null) {
            return res.getTable().getTableNumber();
        }
        if (res.getTableId() != null) {
            return tableRepository.findById(res.getTableId()).map(RestaurantTable::getTableNumber).orElse(null);
        }
        return null;
    }

    private String displayTableNumber(Order order) {
        return order.getPartySize() != null && order.getTable() != null
            ? order.getTable().getTableNumber() : resolveDisplayTableNumber(order.getTableId());
    }

    @Transactional
    public OrderResponse addItem(String orderId, AddOrderItemRequest request, String staffId) {
        Order order = findEditableOrder(orderId);

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

        boolean additional = kitchenTicketRepository.existsByOrderId(order.getId());
        saveKitchenTicket(order, savedItem, menuItem, displayTableNumber(order), additional);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse addItems(String orderId, AddOrderItemsBatchRequest request, String staffId) {
        Order order = findEditableOrder(orderId);

        List<MenuItem> menuItems = new ArrayList<>();
        for (AddOrderItemRequest itemRequest : request.items()) {
            MenuItem menuItem = menuItemRepository.findByIdAndDeletedAtIsNull(itemRequest.menuItemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món ăn."));

            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Món " + menuItem.getItemName() + " hiện không còn sẵn. Vui lòng chọn món khác.");
            }
            menuItems.add(menuItem);
        }

        boolean additional = kitchenTicketRepository.existsByOrderId(order.getId());
        String displayTableNumber = displayTableNumber(order);

        for (int i = 0; i < request.items().size(); i++) {
            AddOrderItemRequest itemRequest = request.items().get(i);
            MenuItem menuItem = menuItems.get(i);

            OrderItem item = new OrderItem(
                    UUID.randomUUID().toString(),
                    order.getId(),
                    menuItem.getId(),
                    itemRequest.quantity(),
                    menuItem.getCurrentPrice(),
                    normalizeNote(itemRequest.note())
            );
            item.setMenuItem(menuItem);
            OrderItem savedItem = orderItemRepository.save(item);

            writeAudit(savedItem.getId(), staffId, "ADD_ITEM", 0, savedItem.getQuantity(),
                    CookingStatus.QUEUED, savedItem.getCookingStatus(), "Thêm món vào đơn");

            saveKitchenTicket(order, savedItem, menuItem, displayTableNumber, additional);
        }

        return toResponse(order);
    }

    @Transactional
    public OrderResponse adjustItems(String orderId, AdjustOrderItemsRequest request, String staffId) {
        Order order = findEditableOrder(orderId);
        var ids = new HashSet<>(request.itemIds());
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(item -> ids.contains(item.getId())).toList();
        if (items.isEmpty() || items.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món trong đơn hàng.");
        }

        OrderItem first = items.getFirst();
        int quantity = items.stream().mapToInt(OrderItem::getQuantity).sum();
        if (quantity != request.expectedQuantity() || items.stream().anyMatch(item ->
                item.getCookingStatus() != request.expectedStatus()
                || !item.getMenuItemId().equals(first.getMenuItemId())
                || item.getUnitPriceSnapshot().compareTo(first.getUnitPriceSnapshot()) != 0
                || !Objects.equals(normalizeNote(item.getNote()), normalizeNote(first.getNote())))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Món đã thay đổi. Vui lòng kiểm tra lại đơn.");
        }

        switch (request.action()) {
            case DECREMENT -> {
                if (quantity <= 1) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Vui lòng xác nhận hủy phần cuối cùng.");
                }
                OrderItem latest = items.getLast();
                KitchenTicket ticket = kitchenTicketRepository.findByOrderItemId(latest.getId()).orElse(null);
                assertCancellable(latest, ticket);
                if (latest.getQuantity() == 1) {
                    cancelItem(latest, ticket, "Giảm số lượng", staffId);
                } else {
                    updateItem(latest, latest.getQuantity() - 1, null, staffId);
                }
            }
            case UPDATE_NOTE -> {
                items.forEach(this::assertEditable);
                items.forEach(item -> updateItem(item, null, request.note() == null ? "" : request.note(), staffId));
            }
            case CANCEL -> {
                List<KitchenTicket> tickets = items.stream()
                        .map(item -> kitchenTicketRepository.findByOrderItemId(item.getId()).orElse(null)).toList();
                for (int index = 0; index < items.size(); index++) {
                    assertCancellable(items.get(index), tickets.get(index));
                }
                for (int index = 0; index < items.size(); index++) {
                    cancelItem(items.get(index), tickets.get(index), request.reason(), staffId);
                }
            }
        }
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateItem(String orderId, String itemId, UpdateOrderItemRequest request, String staffId) {
        Order order = findEditableOrder(orderId);
        OrderItem item = findItemOfOrder(order, itemId);
        assertEditable(item);
        updateItem(item, request.quantity(), request.note(), staffId);
        return toResponse(order);
    }

    private void updateItem(OrderItem item, Integer quantity, String note, String staffId) {
        int oldQuantity = item.getQuantity();
        CookingStatus oldStatus = item.getCookingStatus();
        String oldNote = item.getNote();

        if (quantity != null) item.setQuantity(quantity);
        if (note != null) item.setNote(normalizeNote(note));
        if (oldQuantity == item.getQuantity() && Objects.equals(oldNote, item.getNote())) return;
        OrderItem savedItem = orderItemRepository.save(item);

        writeAudit(savedItem.getId(), staffId, "UPDATE_ITEM", oldQuantity, savedItem.getQuantity(),
                oldStatus, savedItem.getCookingStatus(), describeChange(oldQuantity, savedItem.getQuantity(), oldNote, savedItem.getNote()));

        syncKitchenTicket(savedItem);
    }

    private String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    @Transactional
    public OrderResponse cancelItem(String orderId, String itemId, CancelOrderItemRequest request, String staffId) {
        Order order = findEditableOrder(orderId);
        OrderItem item = findItemOfOrder(order, itemId);
        KitchenTicket ticket = kitchenTicketRepository.findByOrderItemId(itemId).orElse(null);
        assertCancellable(item, ticket);
        cancelItem(item, ticket, request.reason(), staffId);
        return toResponse(order);
    }

    private void cancelItem(OrderItem item, KitchenTicket ticket, String requestedReason, String staffId) {
        int oldQuantity = item.getQuantity();
        CookingStatus oldStatus = item.getCookingStatus();
        String reason = (requestedReason == null || requestedReason.isBlank())
                ? "Khách đổi món"
                : requestedReason.trim();

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
    }

    private void assertCancellable(OrderItem item, KitchenTicket ticket) {
        CookingStatus itemStatus = item.getCookingStatus();
        if (itemStatus == CookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Món này đã được huỷ trước đó.");
        }

        boolean ticketNotQueued = ticket != null && ticket.getStatus() != CookingStatus.QUEUED;

        if (itemStatus != CookingStatus.QUEUED || ticketNotQueued) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ huỷ được món còn ở mục chờ chế biến.");
        }
    }

    private void assertEditable(OrderItem item) {
        CookingStatus status = item.getCookingStatus();
        if (status == CookingStatus.READY || status == CookingStatus.SERVED || status == CookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Món đã hoàn tất hoặc đã hủy, không thể thay đổi.");
        }
    }

    private Order findEditableOrder(String orderId) {
        Order order = findOpenOrderOrThrow(orderId);
        assertNoPendingVnpay(orderId);
        return order;
    }

    private void assertNoPendingVnpay(String orderId) {
        if (paymentOrderRepository.existsByDiningOrderIdAndStatusAndCreatedAtAfter(
            orderId, "PENDING", LocalDateTime.now().minusMinutes(PaymentOrder.VNPAY_VALIDITY_MINUTES))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn đang chờ kết quả thanh toán VNPay.");
        }
    }

    private Order lockOrder(String orderId) {
        return orderRepository.lockById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
    }

    private Order findOpenOrderOrThrow(String orderId) {
        Order order = lockOrder(orderId);

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

    private void saveKitchenTicket(Order order, OrderItem item, MenuItem menuItem, String displayTableNumber,
                                   boolean additional) {
        KitchenTicket ticket = new KitchenTicket(
                UUID.randomUUID().toString(),
                item.getId(),
                order.getId(),
                displayTableNumber,
                menuItem.getItemName(),
                item.getQuantity(),
                item.getNote()
        );
        ticket.setAdditional(additional);
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
        KitchenNotifications.afterCommit(() -> {
            try {
                messagingTemplate.convertAndSend(KITCHEN_TICKETS_TOPIC, event);
            } catch (Exception ex) {
                logger.error("Failed to broadcast kitchen ticket event: ", ex);
            }
        });
    }
}