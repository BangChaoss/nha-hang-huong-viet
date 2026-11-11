package com.huongViet.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huongViet.auth.User;
import com.huongViet.auth.UserRole;
import com.huongViet.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyControllerTest {

    private MockMvc mockBalanceMvc;
    private MockMvc mockSettingsMvc;
    private MockMvc mockProfileMvc;

    @Mock
    private PaymentService paymentService;

    @Mock
    private LoyaltyProfileService loyaltyProfileService;

    @InjectMocks
    private LoyaltyBalanceController balanceController;

    @InjectMocks
    private LoyaltySettingsController settingsController;

    @InjectMocks
    private LoyaltyProfileController profileController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("u-1", "customer1", "customer@huongviet.com", "0901234567", "pass", UserRole.CUSTOMER);

        mockBalanceMvc = MockMvcBuilders.standaloneSetup(balanceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockSettingsMvc = MockMvcBuilders.standaloneSetup(settingsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        HandlerMethodArgumentResolver authResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return testUser;
            }
        };

        mockProfileMvc = MockMvcBuilders.standaloneSetup(profileController)
                .setCustomArgumentResolvers(authResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/payments/loyalty/{phone} trả về số dư điểm")
    void testGetLoyaltyBalanceEndpoint() throws Exception {
        when(paymentService.getLoyaltyBalance("0901234567"))
                .thenReturn(Map.of("phoneNumber", "0901234567", "pointsBalance", new BigDecimal("500")));

        mockBalanceMvc.perform(get("/api/payments/loyalty/0901234567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.pointsBalance").value(500));
    }

    @Test
    @DisplayName("GET /api/admin/loyalty/settings trả về tỷ lệ tích điểm")
    void testGetLoyaltySettingsEndpoint() throws Exception {
        when(paymentService.getRate()).thenReturn(new LoyaltyRateResponse(new BigDecimal("5.000"), new BigDecimal("1000"), java.time.LocalDateTime.now()));

        mockSettingsMvc.perform(get("/api/admin/loyalty/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earnRatePercent").value(5.000));
    }

    @Test
    @DisplayName("PUT /api/admin/loyalty/settings cập nhật tỷ lệ tích điểm")
    void testUpdateLoyaltySettingsEndpoint() throws Exception {
        LoyaltyRateRequest req = new LoyaltyRateRequest(new BigDecimal("8.000"));
        when(paymentService.updateRate(any(LoyaltyRateRequest.class)))
                .thenReturn(new LoyaltyRateResponse(new BigDecimal("8.000"), new BigDecimal("1000"), java.time.LocalDateTime.now()));

        mockSettingsMvc.perform(put("/api/admin/loyalty/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earnRatePercent").value(8.000));
    }

    @Test
    @DisplayName("GET /api/account/loyalty trả về profile điểm và voucher của người dùng")
    void testGetLoyaltyProfileEndpoint() throws Exception {
        LoyaltyProfileResponse response = new LoyaltyProfileResponse(
                "0901234567",
                new BigDecimal("1500"),
                List.of(new LoyaltyProfileResponse.AvailableVoucherResponse(
                        "v-1", "WELCOME", "Chào mừng", "Giảm 10%",
                        "PERCENTAGE", new BigDecimal("10"), new BigDecimal("50000"),
                        BigDecimal.ZERO, "2026-01-01T00:00:00", "2026-12-31T23:59:59"
                ))
        );

        when(loyaltyProfileService.getProfile(testUser)).thenReturn(response);

        mockProfileMvc.perform(get("/api/account/loyalty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.pointsBalance").value(1500))
                .andExpect(jsonPath("$.vouchers.length()").value(1))
                .andExpect(jsonPath("$.vouchers[0].voucherCode").value("WELCOME"));
    }
}
