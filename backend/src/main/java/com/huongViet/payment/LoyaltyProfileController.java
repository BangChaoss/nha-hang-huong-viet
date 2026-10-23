package com.huongViet.payment;

import com.huongViet.auth.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/loyalty")
public class LoyaltyProfileController {
    private final LoyaltyProfileService loyaltyProfileService;

    public LoyaltyProfileController(LoyaltyProfileService loyaltyProfileService) {
        this.loyaltyProfileService = loyaltyProfileService;
    }

    @GetMapping
    public LoyaltyProfileResponse get(@AuthenticationPrincipal User user) {
        return loyaltyProfileService.getProfile(user);
    }
}
