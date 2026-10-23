package com.huongViet.payment;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/loyalty")
public class LoyaltySettingsController {
    private final PaymentService service;
    public LoyaltySettingsController(PaymentService service){this.service=service;}
    @GetMapping("/settings") public LoyaltyRateResponse get(){return service.getRate();}
    @PutMapping("/settings") public LoyaltyRateResponse update(@Valid @RequestBody LoyaltyRateRequest request){return service.updateRate(request);}
}
