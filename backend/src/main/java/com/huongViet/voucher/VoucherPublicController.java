package com.huongViet.voucher;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/vouchers")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class VoucherPublicController {
 private final VoucherService service;
 public VoucherPublicController(VoucherService service){this.service=service;}
 @PostMapping("/validate") public VoucherValidationResponse validate(@Valid @RequestBody VoucherValidationRequest request){return service.validate(request);}
 @PostMapping("/redeem") public VoucherValidationResponse redeem(@Valid @RequestBody VoucherValidationRequest request){return service.redeem(request);}
}
