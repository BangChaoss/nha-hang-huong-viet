package com.huongViet.table;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/zones")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class ZoneReadController {

    private final ZoneService zoneService;

    public ZoneReadController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @GetMapping
    public ResponseEntity<List<ZoneResponse>> listAll() {
        return ResponseEntity.ok(zoneService.listAll());
    }
}
