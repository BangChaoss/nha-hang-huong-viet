package com.huongViet.menu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Quản lý danh mục món ăn (UC-21 Manage Menu).
 *
 * <p>Danh mục chỉ được xóa mềm (BR-48) và không được xóa khi vẫn còn món ăn
 * đang hoạt động (BR-53). Mọi thay đổi cấu trúc thực đơn đều được thông báo
 * tới các client qua WebSocket {@code /topic/menu/refresh} (BR-55).</p>
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final Logger logger = LoggerFactory.getLogger(CategoryService.class);

    private static final String MENU_REFRESH_TOPIC = "/topic/menu/refresh";

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public CategoryService(CategoryRepository categoryRepository,
                           MenuItemRepository menuItemRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public List<CategoryResponse> listAll() {
        return categoryRepository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc()
            .stream()
            .map(category -> CategoryResponse.from(category, countActiveMenuItems(category.getId())))
            .toList();
    }

    public CategoryResponse getById(String id) {
        Category category = findOrThrow(id);
        return CategoryResponse.from(category, countActiveMenuItems(category.getId()));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String trimmedName = request.categoryName().trim();
        if (categoryRepository.existsByCategoryNameIgnoreCaseAndDeletedAtIsNull(trimmedName)) {
            logger.warn("Category name already exists: {}", trimmedName);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên danh mục đã tồn tại.");
        }

        Category category = new Category(
            UUID.randomUUID().toString(),
            trimmedName,
            request.displayOrder()
        );

        Category saved = categoryRepository.save(category);
        logger.info("Created menu category: id={}, name={}", saved.getId(), saved.getCategoryName());
        broadcastRefresh("CATEGORY_CREATED");
        return CategoryResponse.from(saved, 0);
    }

    @Transactional
    public CategoryResponse update(String id, CategoryRequest request) {
        Category category = findOrThrow(id);
        String trimmedName = request.categoryName().trim();

        if (categoryRepository.existsByCategoryNameIgnoreCaseAndDeletedAtIsNullAndIdNot(trimmedName, id)) {
            logger.warn("Duplicate category name on update: {}", trimmedName);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên danh mục đã tồn tại.");
        }

        category.setCategoryName(trimmedName);
        category.setDisplayOrder(request.displayOrder());
        logger.info("Updated menu category: id={}, name={}", category.getId(), category.getCategoryName());
        broadcastRefresh("CATEGORY_UPDATED");
        return CategoryResponse.from(category, countActiveMenuItems(category.getId()));
    }

    /**
     * BR-53: Không xóa danh mục đang chứa món ăn.
     */
    @Transactional
    public void delete(String id) {
        Category category = findOrThrow(id);

        if (menuItemRepository.existsByCategoryIdAndDeletedAtIsNull(id)) {
            logger.warn("Attempt to delete category with active menu items: id={}", id);
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Không thể xóa danh mục đang có món ăn. Vui lòng xóa hoặc chuyển các món trước."
            );
        }

        category.setDeletedAt(LocalDateTime.now());
        logger.info("Soft-deleted menu category: id={}", id);
        broadcastRefresh("CATEGORY_DELETED");
    }

    /**
     * Thực đơn công khai: chỉ trả về các danh mục còn món ăn.
     */
    public MenuOverviewResponse getMenuOverview() {
        List<MenuCategoryResponse> categories = categoryRepository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc()
            .stream()
            .map(this::toMenuCategory)
            .filter(menuCategory -> !menuCategory.items().isEmpty())
            .toList();

        return new MenuOverviewResponse(categories);
    }

    private MenuCategoryResponse toMenuCategory(Category category) {
        List<MenuItemResponse> items = menuItemRepository
            .findAllByCategoryIdAndDeletedAtIsNullOrderByItemNameAsc(category.getId())
            .stream()
            .map(item -> {
                item.setCategory(category);
                return MenuItemResponse.from(item);
            })
            .toList();

        return new MenuCategoryResponse(
            category.getId(),
            category.getCategoryName(),
            category.getDisplayOrder(),
            items
        );
    }

    private Category findOrThrow(String id) {
        return categoryRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục."));
    }

    private long countActiveMenuItems(String categoryId) {
        return menuItemRepository.findAllByCategoryIdAndDeletedAtIsNullOrderByItemNameAsc(categoryId).size();
    }

    private void broadcastRefresh(String reason) {
        try {
            MenuRefreshMessage message = new MenuRefreshMessage(reason);
            messagingTemplate.convertAndSend(MENU_REFRESH_TOPIC, message);
            logger.debug("Broadcasted menu refresh: {}", message);
        } catch (Exception ex) {
            logger.error("Failed to broadcast menu refresh via WebSocket: ", ex);
        }
    }
}
