package com.huongViet.reservation;

import com.huongViet.auth.User;
import com.huongViet.auth.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationRateLimiterFilterTest {

    private ReservationRateLimiterFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new ReservationRateLimiterFilter();
        filter.clearBuckets();
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Cấp 1: Phân giải khóa theo User ID khi người dùng đã đăng nhập")
    void resolveRateLimitKey_AuthenticatedUser_ReturnsUserPrefix() {
        User user = new User("usr-123", "khachhang", "pass", "khach@test.com", "0901234567", UserRole.CUSTOMER);
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reservations");
        request.addHeader("X-Device-Id", "device-abc-999");
        request.setRemoteAddr("127.0.0.1");

        String key = filter.resolveRateLimitKey(request);
        assertEquals("user:usr-123", key);
    }

    @Test
    @DisplayName("Cấp 2: Phân giải khóa theo Device ID khi khách vãng lai gửi Header X-Device-Id")
    void resolveRateLimitKey_GuestWithDeviceId_ReturnsDevicePrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reservations");
        request.addHeader("X-Device-Id", "d83e2a41-b851-45f8-b3d5-1033b006c991");
        request.setRemoteAddr("192.168.1.100");

        String key = filter.resolveRateLimitKey(request);
        assertEquals("device:d83e2a41-b851-45f8-b3d5-1033b006c991", key);
    }

    @Test
    @DisplayName("Cấp 3: Phân giải khóa theo Client IP khi không có đăng nhập và không có Device ID")
    void resolveRateLimitKey_GuestWithoutDeviceId_ReturnsIpPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reservations");
        request.setRemoteAddr("203.0.113.195");

        String key = filter.resolveRateLimitKey(request);
        assertEquals("ip:203.0.113.195", key);
    }

    @Test
    @DisplayName("Chặn request thứ 6 của cùng 1 User và trả về HTTP 429")
    void doFilterInternal_RateLimitExceeded_Returns429() throws ServletException, IOException {
        User user = new User("usr-a", "user_a", "pass", "a@test.com", "0900000001", UserRole.CUSTOMER);
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Gửi 5 request đầu tiên -> thành công
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/reservations");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }
        verify(filterChain, times(5)).doFilter(any(), any());

        // Request thứ 6 -> Bị chặn 429
        MockHttpServletRequest req6 = new MockHttpServletRequest("POST", "/api/reservations");
        MockHttpServletResponse res6 = new MockHttpServletResponse();
        filter.doFilter(req6, res6, filterChain);

        assertEquals(429, res6.getStatus());
        assertTrue(res6.getContentAsString().contains("Bạn đã vượt quá số lần gửi yêu cầu đặt bàn"));
        // filterChain vẫn chỉ dừng lại ở 5 lần gọi
        verify(filterChain, times(5)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Giải quyết triệt để lỗi: 2 tài khoản khác nhau trên cùng IP có 2 bucket độc lập")
    void doFilterInternal_DifferentUsersOnSameIp_HaveIndependentBuckets() throws ServletException, IOException {
        User userA = new User("usr-a", "user_a", "pass", "a@test.com", "0900000001", UserRole.CUSTOMER);
        User userB = new User("usr-b", "user_b", "pass", "b@test.com", "0900000002", UserRole.CUSTOMER);

        // User A dùng hết 5 lượt trên IP 127.0.0.1
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userA, null, List.of()));
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/reservations");
            req.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }

        // User A gửi lần 6 -> bị 429
        MockHttpServletRequest reqA6 = new MockHttpServletRequest("POST", "/api/reservations");
        reqA6.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse resA6 = new MockHttpServletResponse();
        filter.doFilter(reqA6, resA6, filterChain);
        assertEquals(429, resA6.getStatus());

        // Đổi sang User B trên CÙNG IP 127.0.0.1 -> Lần 1 vẫn thành công 100%!
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userB, null, List.of()));
        MockHttpServletRequest reqB1 = new MockHttpServletRequest("POST", "/api/reservations");
        reqB1.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse resB1 = new MockHttpServletResponse();
        filter.doFilter(reqB1, resB1, filterChain);
        assertEquals(200, resB1.getStatus());
    }

    @Test
    @DisplayName("Giải quyết triệt để lỗi cùng Wi-Fi: 2 thiết bị khác nhau có X-Device-Id riêng không bị chặn oan")
    void doFilterInternal_DifferentDevicesOnSameWifi_HaveIndependentBuckets() throws ServletException, IOException {
        String sharedWifiIp = "118.69.182.20"; // Cùng 1 IP Public Wi-Fi

        // Thiết bị 1 dùng hết 5 lượt
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/reservations");
            req.setRemoteAddr(sharedWifiIp);
            req.addHeader("X-Device-Id", "device-laptop-1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }

        // Thiết bị 1 gửi lần 6 -> bị 429
        MockHttpServletRequest reqD1Limit = new MockHttpServletRequest("POST", "/api/reservations");
        reqD1Limit.setRemoteAddr(sharedWifiIp);
        reqD1Limit.addHeader("X-Device-Id", "device-laptop-1");
        MockHttpServletResponse resD1Limit = new MockHttpServletResponse();
        filter.doFilter(reqD1Limit, resD1Limit, filterChain);
        assertEquals(429, resD1Limit.getStatus());

        // Thiết bị 2 (điện thoại của người khác) trong cùng Wi-Fi -> gửi lần 1 thành công!
        MockHttpServletRequest reqD2 = new MockHttpServletRequest("POST", "/api/reservations");
        reqD2.setRemoteAddr(sharedWifiIp);
        reqD2.addHeader("X-Device-Id", "device-phone-2");
        MockHttpServletResponse resD2 = new MockHttpServletResponse();
        filter.doFilter(reqD2, resD2, filterChain);
        assertEquals(200, resD2.getStatus());
    }

    @Test
    @DisplayName("Các endpoint khác hoặc HTTP method khác không bị áp dụng Rate Limit")
    void doFilterInternal_OtherEndpoints_NotRateLimited() throws ServletException, IOException {
        MockHttpServletRequest reqGet = new MockHttpServletRequest("GET", "/api/reservations");
        MockHttpServletResponse resGet = new MockHttpServletResponse();
        filter.doFilter(reqGet, resGet, filterChain);
        assertEquals(200, resGet.getStatus());

        MockHttpServletRequest reqMenu = new MockHttpServletRequest("POST", "/api/menu");
        MockHttpServletResponse resMenu = new MockHttpServletResponse();
        filter.doFilter(reqMenu, resMenu, filterChain);
        assertEquals(200, resMenu.getStatus());

        verify(filterChain, times(2)).doFilter(any(), any());
    }
}
