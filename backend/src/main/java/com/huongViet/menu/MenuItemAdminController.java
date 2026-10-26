package com.huongViet.menu;

import com.huongViet.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản trị món ăn (UC-20 Manage Food).
 * Phân quyền MANAGER / ADMIN được xử lý trong SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/menu-items")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class MenuItemAdminController {

    /** Số món trên một trang tối đa. */
    private static final int MAX_PAGE_SIZE = 100;

    private final MenuItemService menuItemService;

    public MenuItemAdminController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    /**
     * GET /api/admin/menu-items?page=&size=&sort=&direction=
     *
     * <p>Phân trang và sắp xếp danh sách món ăn. Mặc định sắp xếp theo thời gian tạo,
     * món mới nhất lên đầu.</p>
     */
    @GetMapping
    public ResponseEntity<PageResponse<MenuItemResponse>> listAll(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean available,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(required = false) String direction) {
        Pageable pageable = buildPageable(page, size, sort, direction);
        return ResponseEntity.ok(PageResponse.from(menuItemService.listAll(categoryId, q, available, pageable)));
    }

    /**
     * Dựng thông tin phân trang, chặn các giá trị vượt quá giới hạn cho phép.
     */
    private Pageable buildPageable(int page, int size, String sort, String direction) {
        MenuItemSort menuItemSort = MenuItemSort.from(sort);
        Sort.Direction sortDirection = MenuItemSort.directionFrom(direction, menuItemSort.defaultDirection());

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        // Thêm tiêu chí phụ theo id để thứ tự ổn định giữa các trang.
        Sort resolved = Sort.by(sortDirection, menuItemSort.property()).and(Sort.by("id"));
        return PageRequest.of(safePage, safeSize, resolved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(menuItemService.getById(id));
    }

    @PostMapping
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody MenuItemRequest request) {
        MenuItemResponse created = menuItemService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuItemResponse> update(@PathVariable String id, @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.ok(menuItemService.update(id, request));
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<MenuItemResponse> updateAvailability(
            @PathVariable String id,
            @Valid @RequestBody UpdateAvailabilityRequest request) {
        return ResponseEntity.ok(menuItemService.updateAvailability(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        menuItemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
