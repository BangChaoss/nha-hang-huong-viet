package com.huongViet.voucher;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/vouchers")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class VoucherController {
 private final VoucherService service;
 public VoucherController(VoucherService service){this.service=service;}

 @GetMapping public List<VoucherResponse> list(){return service.list();}
 @PostMapping("/validate")
 public VoucherValidationResponse validate(@Valid @RequestBody VoucherValidationRequest request){return service.validate(request);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public VoucherResponse create(@Valid @RequestBody VoucherRequest request){return service.create(request);}
 @PatchMapping("/{id}/status")
 public VoucherResponse status(@PathVariable String id,@RequestBody StatusRequest request){return service.setActive(id,request.active());}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable String id){service.delete(id);}
 public record StatusRequest(boolean active){}
}
