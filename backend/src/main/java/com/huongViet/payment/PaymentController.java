package com.huongViet.payment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/vnpay")
public class PaymentController {
    private final PaymentService service;
    @Value("${app.frontend-url:http://localhost:5173}") private String frontendUrl;
    public PaymentController(PaymentService service){this.service=service;}

    @PostMapping("/create")
    public Map<String,String> create(@Valid @RequestBody VnpayCreateRequest request, HttpServletRequest servletRequest) {
        String ip=servletRequest.getHeader("X-Forwarded-For");
        if(ip!=null && ip.contains(",")) ip=ip.substring(0,ip.indexOf(',')).trim();
        if(ip==null || ip.isBlank()) ip=servletRequest.getRemoteAddr();
        return service.createPayment(request,ip);
    }

    @GetMapping("/ipn")
    public Map<String,String> ipn(@RequestParam Map<String,String> params) {
        String result=service.processCallback(params);
        return switch(result) {
            case "00" -> Map.of("RspCode","00","Message","Confirm Success");
            case "INVALID_SIGNATURE" -> Map.of("RspCode","97","Message","Invalid Signature");
            case "INVALID_AMOUNT" -> Map.of("RspCode","04","Message","Invalid Amount");
            case "ORDER_NOT_FOUND" -> Map.of("RspCode","01","Message","Order Not Found");
            default -> Map.of("RspCode","99","Message","Unknown Error");
        };
    }

    @GetMapping("/return")
    public RedirectView returned(@RequestParam Map<String,String> params) {
        String orderId=params.getOrDefault("vnp_TxnRef","");
        String result=service.processCallback(params);
        String status="00".equals(result) && "00".equals(params.get("vnp_ResponseCode")) ? "success" : "failed";
        return new RedirectView(frontendUrl.replaceAll("/$","")+"/?paymentOrderId="+
            java.net.URLEncoder.encode(orderId,java.nio.charset.StandardCharsets.UTF_8)+"&paymentStatus="+status);
    }

    @GetMapping(value="/orders/{id}", produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> order(@PathVariable String id){return service.getOrder(id);}
}
