package com.huongViet.menu;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API công khai cho thực đơn (UC-14). Khách hàng chưa đăng nhập vẫn xem được.
 */
@RestController
@RequestMapping("/api/menu")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class MenuController {

    private final CategoryService categoryService;
    private final MenuItemService menuItemService;

    public MenuController(CategoryService categoryService, MenuItemService menuItemService) {
        this.categoryService = categoryService;
        this.menuItemService = menuItemService;
    }

    @GetMapping
    public ResponseEntity<MenuOverviewResponse> getMenuOverview() {
        return ResponseEntity.ok(categoryService.getMenuOverview());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponse> getMenuItem(@PathVariable String id) {
        return ResponseEntity.ok(menuItemService.getById(id));
    }
}
