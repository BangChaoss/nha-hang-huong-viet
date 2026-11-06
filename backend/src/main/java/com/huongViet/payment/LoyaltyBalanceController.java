package com.huongViet.payment;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/loyalty")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class LoyaltyBalanceController {
    private final PaymentService service;
    public LoyaltyBalanceController(PaymentService service) { this.service = service; }
    @GetMapping("/{phone}")
    public Map<String, Object> balance(@PathVariable String phone) { return service.getLoyaltyBalance(phone); }
}
