package com.huongViet.integration;

import com.huongViet.auth.*;
import com.huongViet.feedback.Feedback;
import com.huongViet.feedback.FeedbackRepository;
import com.huongViet.feedback.FeedbackService;
import com.huongViet.feedback.dto.CreateFeedbackRequest;
import com.huongViet.feedback.dto.EligibleInvoiceResponse;
import com.huongViet.feedback.dto.FeedbackResponse;
import com.huongViet.feedback.dto.ReplyFeedbackRequest;
import com.huongViet.menu.MenuItem;
import com.huongViet.menu.MenuItemRepository;
import com.huongViet.order.*;
import com.huongViet.payment.LoyaltyAccount;
import com.huongViet.payment.LoyaltyAccountRepository;
import com.huongViet.payment.LoyaltySettings;
import com.huongViet.payment.LoyaltySettingsRepository;
import com.huongViet.payment.PaymentOrderRepository;
import com.huongViet.reservation.*;
import com.huongViet.reservation.dto.CreateReservationRequest;
import com.huongViet.reservation.dto.ReservationResponse;
import com.huongViet.reservation.dto.UpdateReservationStatusRequest;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableStatus;
import com.huongViet.table.TableZone;
import com.huongViet.table.ZoneRepository;
import com.huongViet.voucher.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử tích hợp toàn diện luồng chính (End-to-End Main Flow Integration Test):
 * 1. Đăng ký tài khoản khách hàng (Register)
 * 2. Đăng nhập (Login)
 * 3. Đặt bàn trực tuyến (Reservation)
 * 4. Nhân viên duyệt & phân bàn (Confirm table)
 * 5. Khách đến & Check-in (Check-in, table -> OCCUPIED)
 * 6. Gọi món tại bàn (Order, ticket -> QUEUED)
 * 7. Bếp KDS chuyển trạng thái (QUEUED -> PREPARING -> READY)
 * 8. Phục vụ trả món (READY -> SERVED)
 * 9. Thu ngân thanh toán tiền mặt tại POS (Checkout Cash, invoice -> PAID, table -> AVAILABLE, tích điểm)
 * 10. Khách hàng tra cứu hóa đơn đủ điều kiện đánh giá (Eligible Invoices)
 * 11. Khách hàng gửi đánh giá & rating (Submit Feedback)
 * 12. Hiển thị đánh giá công khai & lịch sử
 * 13. Quản lý phản hồi đánh giá khách hàng (Admin Reply)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FullMainFlowIntegrationTest {

    // Auth Repos & Mocks
    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;

    // Reservation Repos & Mocks
    @Mock private ReservationRepository reservationRepository;
    @Mock private ZoneRepository zoneRepository;
    @Mock private TableRepository tableRepository;
    @Mock private ReservationAuditRepository reservationAuditRepository;
    @Mock private ReservationEmailService emailService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    // Order & KDS Repos & Mocks
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private KitchenTicketRepository kitchenTicketRepository;
    @Mock private OrderItemAuditRepository orderItemAuditRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private LoyaltyAccountRepository loyaltyAccounts;
    @Mock private LoyaltySettingsRepository loyaltySettings;
    @Mock private VoucherService voucherService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private PaymentOrderRepository paymentOrderRepository;

    // Feedback Repos & Mocks
    @Mock private FeedbackRepository feedbackRepository;

    // Services under test
    private AuthService authService;
    private ReservationService reservationService;
    private OrderService orderService;
    private KitchenTicketService kitchenTicketService;
    private FeedbackService feedbackService;

    // Test Domain Entities
    private TableZone zone;
    private RestaurantTable table;
    private MenuItem phoBo;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository, tokenRepository, jwtService, passwordEncoder, 30, null, refreshTokenService
        );

        reservationService = new ReservationService(
            reservationRepository, zoneRepository, tableRepository, reservationAuditRepository, emailService, messagingTemplate
        );

        orderService = new OrderService(
            orderRepository, orderItemRepository, kitchenTicketRepository, orderItemAuditRepository,
            menuItemRepository, tableRepository, messagingTemplate, loyaltyAccounts, loyaltySettings,
            voucherService, jdbcTemplate, reservationRepository, paymentOrderRepository
        );

        kitchenTicketService = new KitchenTicketService(
            kitchenTicketRepository, orderItemRepository, orderRepository, messagingTemplate
        );

        feedbackService = new FeedbackService(
            feedbackRepository, userRepository, reservationRepository, jdbcTemplate
        );

        // Chuẩn bị dữ liệu mẫu ban đầu
        zone = new TableZone("zone-1", "Khu Trong Nhà", false);
        table = new RestaurantTable("table-1", "zone-1", "Bàn 01", 4);
        table.setStatus(TableStatus.AVAILABLE);
        table.setZone(zone);

        when(tableRepository.findByIdAndDeletedAtIsNull("table-1")).thenReturn(Optional.of(table));
        when(tableRepository.findById("table-1")).thenReturn(Optional.of(table));

        phoBo = new MenuItem("menu-1", "cat-1", "Phở Bò Đặc Biệt", "Phở tái chín", new BigDecimal("65000"), null, true);
    }

    @Test
    @DisplayName("Test toàn diện 13 bước từ Đăng ký -> Đặt bàn -> Check-in -> Order -> KDS -> Thanh toán -> Đánh giá")
    void testFullMainFlow_RegisterToFeedback() {
        // ==========================================
        // BƯỚC 1: ĐĂNG KÝ TÀI KHOẢN KHÁCH HÀNG (REGISTER)
        // ==========================================
        RegisterRequest regReq = new RegisterRequest("cust_hoangnam", "hoangnam@gmail.com", "0981112233", "Password@123");
        when(userRepository.existsByUsernameAndDeletedAtIsNull("cust_hoangnam")).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull("hoangnam@gmail.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndDeletedAtIsNull("0981112233")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed_pass_123");
        when(jwtService.generateToken(any(User.class))).thenReturn("mocked_jwt_token_for_customer");

        User savedUser = new User("user-cust-1", "cust_hoangnam", "hoangnam@gmail.com", "0981112233", "hashed_pass_123", UserRole.CUSTOMER);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AuthResponse regResp = authService.register(regReq);
        assertNotNull(regResp);
        assertEquals("cust_hoangnam", regResp.username());
        assertEquals("0981112233", regResp.phoneNumber());
        assertEquals("CUSTOMER", regResp.role());

        // ==========================================
        // BƯỚC 2: ĐĂNG NHẬP (LOGIN)
        // ==========================================
        LoginRequest loginReq = new LoginRequest("cust_hoangnam", "Password@123");
        when(userRepository.findByUsernameAndDeletedAtIsNull("cust_hoangnam")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("Password@123", "hashed_pass_123")).thenReturn(true);

        AuthResponse loginResp = authService.login(loginReq);
        assertNotNull(loginResp);
        assertEquals("mocked_jwt_token_for_customer", loginResp.token());

        // ==========================================
        // BƯỚC 3: ĐẶT BÀN TRỰC TUYẾN (RESERVATION)
        // ==========================================
        LocalDate bookingDate = LocalDate.now().plusDays(1);
        LocalTime bookingTime = LocalTime.of(12, 0);

        CreateReservationRequest bookReq = new CreateReservationRequest(
            "Hoàng Nam", "0981112233", "hoangnam@gmail.com",
            bookingDate, bookingTime, 4, "zone-1", "Bàn gần cửa sổ"
        );

        when(zoneRepository.findById("zone-1")).thenReturn(Optional.of(zone));
        when(tableRepository.findAllByZoneIdAndDeletedAtIsNull("zone-1")).thenReturn(List.of(table));
        when(reservationRepository.sumConfirmedPartySizeByZoneAndSlot(eq("zone-1"), eq(bookingDate), any(LocalTime.class), any(LocalTime.class)))
            .thenReturn(0);

        Reservation createdReservation = new Reservation(
            "res-101", savedUser.getId(), "Hoàng Nam", "0981112233", "hoangnam@gmail.com",
            4, bookingDate, bookingTime, "Bàn gần cửa sổ", "zone-1"
        );
        createdReservation.setZone(zone);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse bookResp = reservationService.createBooking(bookReq, savedUser.getId());
        assertNotNull(bookResp);
        assertEquals(ReservationStatus.PENDING_HOLD, bookResp.status());
        assertEquals("res-101", createdReservation.getId());

        // ==========================================
        // BƯỚC 4: NHÂN VIÊN XÁC NHẬN & PHÂN BÀN (CONFIRM)
        // ==========================================
        when(reservationRepository.findActiveByIdWithTableAndZone("res-101"))
            .thenReturn(Optional.of(createdReservation));
        when(tableRepository.findById("table-1")).thenReturn(Optional.of(table));
        when(tableRepository.findAllById(List.of("table-1"))).thenReturn(List.of(table));

        UpdateReservationStatusRequest confirmReq = new UpdateReservationStatusRequest(
            ReservationStatus.CONFIRMED, "table-1", "Xác nhận phân Bàn 01"
        );
        ReservationResponse confirmResp = reservationService.updateStatus("res-101", confirmReq, "staff-01");
        assertNotNull(confirmResp);
        assertEquals(ReservationStatus.CONFIRMED, confirmResp.status());
        assertEquals("table-1", createdReservation.getTableId());

        // ==========================================
        // BƯỚC 5: KHÁCH ĐẾN & CHECK-IN (ARRIVED -> TABLE OCCUPIED)
        // ==========================================
        // Thiết lập thời gian đặt bàn khớp với thời gian check-in trong khung [-30m, +30m]
        LocalTime nowSlot = LocalTime.now().withSecond(0).withNano(0);
        createdReservation.setReservationDate(LocalDate.now());
        createdReservation.setReservationTime(nowSlot);

        UpdateReservationStatusRequest checkInReq = new UpdateReservationStatusRequest(
            ReservationStatus.ARRIVED, "table-1", "Khách đã đến nhận bàn"
        );
        ReservationResponse checkInResp = reservationService.updateStatus("res-101", checkInReq, "staff-01");
        assertNotNull(checkInResp);
        assertEquals(ReservationStatus.ARRIVED, checkInResp.status());
        // Trạng thái bàn tự động đổi sang OCCUPIED khi check-in thành công
        assertEquals(TableStatus.OCCUPIED, table.getStatus());

        // ==========================================
        // BƯỚC 6: TẠO ĐƠN & GỌI MÓN (ORDER -> KITCHEN TICKETS QUEUED)
        // ==========================================
        when(menuItemRepository.findByIdAndDeletedAtIsNull("menu-1")).thenReturn(Optional.of(phoBo));
        Order savedOrder = new Order("order-501", "table-1", "staff-01", OrderType.DINE_IN);
        savedOrder.setTable(table);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderItem savedItem = new OrderItem("item-901", "order-501", "menu-1", 2, new BigDecimal("65000"), "Ít hành");
        savedItem.setMenuItem(phoBo);
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(savedItem);

        KitchenTicket savedTicket = new KitchenTicket("ticket-701", "item-901", "order-501", "Bàn 01", "Phở Bò Đặc Biệt", 2, "Ít hành");
        when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenReturn(savedTicket);

        CreateOrderRequest orderReq = new CreateOrderRequest(
            "table-1", OrderType.DINE_IN,
            List.of(new OrderItemRequest("menu-1", 2, "Ít hành")),
            true
        );
        OrderResponse orderResp = orderService.create(orderReq, "staff-01");
        assertNotNull(orderResp);
        assertEquals(OrderStatus.IN_PROGRESS, orderResp.status());

        // ==========================================
        // BƯỚC 7: BẾP KDS CHẾ BIẾN (QUEUED -> PREPARING -> READY)
        // ==========================================
        when(kitchenTicketRepository.findById("ticket-701")).thenReturn(Optional.of(savedTicket));
        when(orderItemRepository.findById("item-901")).thenReturn(Optional.of(savedItem));

        // Nấu món
        kitchenTicketService.updateStatus("ticket-701", new UpdateTicketStatusRequest(CookingStatus.PREPARING));
        assertEquals(CookingStatus.PREPARING, savedTicket.getStatus());
        assertEquals(CookingStatus.PREPARING, savedItem.getCookingStatus());

        // Món xong
        kitchenTicketService.updateStatus("ticket-701", new UpdateTicketStatusRequest(CookingStatus.READY));
        assertEquals(CookingStatus.READY, savedTicket.getStatus());
        assertEquals(CookingStatus.READY, savedItem.getCookingStatus());

        // ==========================================
        // BƯỚC 8: PHỤC VỤ TRẢ MÓN (READY -> SERVED)
        // ==========================================
        kitchenTicketService.updateStatus("ticket-701", new UpdateTicketStatusRequest(CookingStatus.SERVED));
        assertEquals(CookingStatus.SERVED, savedTicket.getStatus());
        assertEquals(CookingStatus.SERVED, savedItem.getCookingStatus());

        // ==========================================
        // BƯỚC 9: THU NGÂN THANH TOÁN TIỀN MẶT TẠI POS (CHECKOUT CASH)
        // ==========================================
        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1"))
            .thenReturn(List.of(savedOrder));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("order-501"))
            .thenReturn(List.of(savedItem));
        when(loyaltySettings.findById("default"))
            .thenReturn(Optional.of(new LoyaltySettings("default", new BigDecimal("10.000"), BigDecimal.ONE)));

        // Mock tìm khách hàng bằng SĐT
        when(jdbcTemplate.queryForList(
            eq("SELECT id FROM users WHERE phone_number = ? AND role = 'CUSTOMER' AND deleted_at IS NULL LIMIT 1"),
            eq(String.class), eq("0981112233")
        )).thenReturn(List.of("user-cust-1"));

        CashCheckoutRequest checkoutReq = new CashCheckoutRequest("0981112233", null, false);
        CashCheckoutResponse checkoutResp = orderService.checkoutCash("table-1", checkoutReq);

        assertNotNull(checkoutResp);
        // Subtotal = 2 * 65.000 = 130.000; VAT 10% = 13.000; Total = 143.000
        assertEquals(new BigDecimal("130000.00"), checkoutResp.subtotal());
        assertEquals(new BigDecimal("13000.00"), checkoutResp.vat());
        assertEquals(new BigDecimal("143000.00"), checkoutResp.total());
        // Điểm thưởng tích 10%: 143.000 * 10% = 14.300 điểm
        assertEquals(new BigDecimal("14300"), checkoutResp.pointsAwarded());

        // Bàn và đơn chuyển trạng thái hoàn tất
        assertEquals(OrderStatus.COMPLETED, savedOrder.getStatus());
        assertEquals(TableStatus.AVAILABLE, table.getStatus());

        // ==========================================
        // BƯỚC 10: KHÁCH TRA CỨU HÓA ĐƠN ĐỦ ĐIỀU KIỆN ĐÁNH GIÁ (ELIGIBLE INVOICES)
        // ==========================================
        String expectedInvoiceId = "inv-auto-001";
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-cust-1")))
            .thenReturn(List.of(
                new EligibleInvoiceResponse(expectedInvoiceId, "HV-20261111-INV1", new BigDecimal("143000.00"), "Bàn 01", LocalDateTime.now())
            ));

        List<EligibleInvoiceResponse> eligibleInvoices = feedbackService.getEligibleInvoices(savedUser);
        assertFalse(eligibleInvoices.isEmpty());
        assertEquals(expectedInvoiceId, eligibleInvoices.get(0).id());
        assertEquals("Bàn 01", eligibleInvoices.get(0).tableNumber());

        // ==========================================
        // BƯỚC 11: KHÁCH HÀNG GỬI ĐÁNH GIÁ (SUBMIT FEEDBACK)
        // ==========================================
        CreateFeedbackRequest feedbackReq = new CreateFeedbackRequest(expectedInvoiceId, 5, "Món Phở bò tuyệt vời, nước dùng đậm đà!");

        when(feedbackRepository.findTopByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc("user-cust-1"))
            .thenReturn(Optional.empty()); // Không vi phạm 60s cooldown
        when(feedbackRepository.existsByInvoiceIdAndDeletedAtIsNull(expectedInvoiceId))
            .thenReturn(false); // Chưa gửi đánh giá trước đó

        // Mock hóa đơn tồn tại và thuộc quyền sở hữu của khách hàng
        when(jdbcTemplate.queryForList(anyString(), eq(expectedInvoiceId), eq(expectedInvoiceId)))
            .thenReturn(List.of(Map.of(
                "id", expectedInvoiceId,
                "customer_id", "user-cust-1",
                "status", "PAID"
            )));

        Feedback savedFeedback = new Feedback(
            "user-cust-1", "cust_hoangnam", "0981112233", expectedInvoiceId, 5, "Món Phở bò tuyệt vời, nước dùng đậm đà!"
        );
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(savedFeedback);

        FeedbackResponse feedbackResp = feedbackService.submitFeedback(savedUser, feedbackReq);
        assertNotNull(feedbackResp);
        assertEquals(5, feedbackResp.rating());
        assertEquals("Món Phở bò tuyệt vời, nước dùng đậm đà!", feedbackResp.comment());
        assertTrue(feedbackResp.isPublic());

        // ==========================================
        // BƯỚC 12: HIỂN THỊ ĐÁNH GIÁ CÔNG KHAI & LỊCH SỬ CỦA KHÁCH
        // ==========================================
        when(feedbackRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc("user-cust-1"))
            .thenReturn(List.of(savedFeedback));

        List<FeedbackResponse> myReviews = feedbackService.getMyFeedbacks(savedUser);
        assertEquals(1, myReviews.size());
        assertEquals(5, myReviews.get(0).rating());

        Page<Feedback> publicPage = new PageImpl<>(List.of(savedFeedback));
        when(feedbackRepository.findByIsPublicTrueAndDeletedAtIsNullOrderByCreatedAtDesc(any(Pageable.class)))
            .thenReturn(publicPage);

        Page<FeedbackResponse> publicFeedbacks = feedbackService.getPublicFeedbacks(PageRequest.of(0, 10));
        assertEquals(1, publicFeedbacks.getTotalElements());

        // ==========================================
        // BƯỚC 13: QUẢN LÝ PHẢN HỒI ĐÁNH GIÁ (ADMIN REPLY)
        // ==========================================
        when(feedbackRepository.findById(savedFeedback.getId()))
            .thenReturn(Optional.of(savedFeedback));

        ReplyFeedbackRequest replyReq = new ReplyFeedbackRequest("Hương Việt chân thành cảm ơn quý khách! Rất mong được đón tiếp lần sau.");
        FeedbackResponse repliedResp = feedbackService.replyFeedback(savedFeedback.getId(), replyReq);

        assertNotNull(repliedResp);
        assertEquals("Hương Việt chân thành cảm ơn quý khách! Rất mong được đón tiếp lần sau.", repliedResp.staffResponse());
    }

    @Test
    @DisplayName("Edge Case: Chuẩn hóa SĐT (+84981112233) tại POS khớp đúng khách hàng đăng ký (0981112233)")
    void testEdgeCase_PhoneNormalization_PosCheckoutMatchesCustomer() {
        // Khách hàng đăng ký bằng số 0981112233
        User customer = new User("cust-e1", "nam_e1", "nam_e1@test.com", "0981112233", "pwd", UserRole.CUSTOMER);

        Order order = new Order("ord-e1", "table-1", "staff-1", OrderType.DINE_IN);
        order.setTable(table);
        OrderItem item = new OrderItem("item-e1", "ord-e1", "menu-1", 1, new BigDecimal("65000"), null);
        item.setMenuItem(phoBo);

        when(orderRepository.findByTableIdOrderByCreatedAtDesc("table-1"))
            .thenReturn(List.of(order));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc("ord-e1"))
            .thenReturn(List.of(item));
        when(loyaltySettings.findById("default"))
            .thenReturn(Optional.of(new LoyaltySettings("default", new BigDecimal("5.000"), BigDecimal.ONE)));

        // Thu ngân gõ số điện thoại có tiền tố +84 và dấu khoảng trắng tại POS: "+84 981 112 233"
        when(jdbcTemplate.queryForList(
            eq("SELECT id FROM users WHERE phone_number = ? AND role = 'CUSTOMER' AND deleted_at IS NULL LIMIT 1"),
            eq(String.class), eq("0981112233")
        )).thenReturn(List.of("cust-e1"));

        CashCheckoutRequest req = new CashCheckoutRequest("+84 981 112 233", null, false);
        CashCheckoutResponse resp = orderService.checkoutCash("table-1", req);

        assertNotNull(resp);
        // Xác minh query được gọi với cả +84 và 0 để nối đúng khách hàng cust-e1 vào hóa đơn
        verify(jdbcTemplate).update(
            contains("INSERT INTO invoices"),
            anyString(), anyString(), eq("ord-e1"), eq("cust-e1"), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()
        );
    }

    @Test
    @DisplayName("Edge Case: Check-in ngoài khung [-30m, +30m] bị chặn với HTTP 400")
    void testEdgeCase_CheckInTimeGuard_BlocksOutOfWindow() {
        // Đặt bàn vào ngày mai lúc 12:00 (cách hiện tại gần 24h)
        Reservation res = new Reservation(
            "res-future", "cust-1", "Khách Test", "0981112233", null, 4,
            LocalDate.now().plusDays(1), LocalTime.of(12, 0), null, "zone-1"
        );
        res.setStatus(ReservationStatus.CONFIRMED);
        res.setTableId("table-1");

        when(reservationRepository.findActiveByIdWithTableAndZone("res-future"))
            .thenReturn(Optional.of(res));

        UpdateReservationStatusRequest checkInReq = new UpdateReservationStatusRequest(
            ReservationStatus.ARRIVED, "table-1", "Check in quá sớm"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            reservationService.updateStatus("res-future", checkInReq, "staff-01")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Chưa đến thời gian nhận bàn"));
    }

    @Test
    @DisplayName("Edge Case: Chuyển tắt trạng thái KDS (QUEUED sang SERVED) bị chặn 409 Conflict")
    void testEdgeCase_InvalidKitchenTicketTransition_BlocksSkippingStates() {
        KitchenTicket ticket = new KitchenTicket("ticket-bad", "item-1", "ord-1", "Bàn 1", "Phở Bò", 1, null);
        ticket.setStatus(CookingStatus.QUEUED);

        when(kitchenTicketRepository.findById("ticket-bad")).thenReturn(Optional.of(ticket));

        // Bếp nhảy cóc từ QUEUED thẳng sang SERVED mà không qua PREPARING/READY
        UpdateTicketStatusRequest req = new UpdateTicketStatusRequest(CookingStatus.SERVED);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            kitchenTicketService.updateStatus("ticket-bad", req)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Không thể chuyển trạng thái phiếu bếp"));
    }

    @Test
    @DisplayName("Edge Case: Đánh giá hóa đơn của khách khác bị chặn HTTP 403 Forbidden")
    void testEdgeCase_Feedback_BlocksUnownedInvoice() {
        User userA = new User("user-A", "userA", "a@test.com", "0981112233", "pwd", UserRole.CUSTOMER);
        CreateFeedbackRequest req = new CreateFeedbackRequest("inv-of-user-B", 5, "Món ngon!");

        when(feedbackRepository.findTopByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc("user-A"))
            .thenReturn(Optional.empty());
        when(feedbackRepository.existsByInvoiceIdAndDeletedAtIsNull("inv-of-user-B"))
            .thenReturn(false);

        // Hóa đơn trong DB thuộc về user-B
        when(jdbcTemplate.queryForList(anyString(), eq("inv-of-user-B"), eq("inv-of-user-B")))
            .thenReturn(List.of(Map.of(
                "id", "inv-of-user-B",
                "customer_id", "user-B",
                "status", "PAID"
            )));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            feedbackService.submitFeedback(userA, req)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("không có quyền đánh giá hóa đơn của khách hàng khác"));
    }
}
