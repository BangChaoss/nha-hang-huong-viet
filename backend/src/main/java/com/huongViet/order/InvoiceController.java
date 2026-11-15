package com.huongViet.order;

import com.huongViet.order.dto.InvoiceResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:8081"})
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * GET /api/invoices?date=YYYY-MM-DD
     */
    @GetMapping({"", "/"})
    public ResponseEntity<List<InvoiceResponse>> listByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(invoiceService.listByDate(date));
    }

    /**
     * GET /api/invoices/{id}
     */
    @GetMapping({"/{id}", "/{id}/"})
    public ResponseEntity<InvoiceResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(invoiceService.getById(id));
    }
}