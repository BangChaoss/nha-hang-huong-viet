package com.huongViet.table;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class FloorPlanController {

    private final FloorPlanService floorPlanService;

    public FloorPlanController(FloorPlanService floorPlanService) {
        this.floorPlanService = floorPlanService;
    }

    @GetMapping("/api/floor-plan")
    public ResponseEntity<List<FloorPlanLayoutResponse>> getAll() {
        return ResponseEntity.ok(floorPlanService.getAll());
    }

    @PutMapping("/api/admin/floor-plan")
    public ResponseEntity<List<FloorPlanLayoutResponse>> saveAll(
            @Valid @RequestBody List<FloorPlanLayoutRequest> requests) {
        return ResponseEntity.ok(floorPlanService.saveAll(requests));
    }
}
