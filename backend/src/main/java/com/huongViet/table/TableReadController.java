package com.huongViet.table;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tables")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class TableReadController {

    private final TableService tableService;

    public TableReadController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    public ResponseEntity<List<TableResponse>> listAll() {
        return ResponseEntity.ok(tableService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TableResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(tableService.getById(id));
    }
}
