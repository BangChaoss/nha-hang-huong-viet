package com.huongViet.menu;

import com.huongViet.upload.ImageStorageService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Quản lý món ăn trong thực đơn (UC-20 Manage Food).
 *
 * <p>Món ăn không bao giờ bị xóa cứng (BR-48) vì có thể đã được tham chiếu
 * trong các đơn hàng / hoá đơn cũ. Việc tạm ngưng bán được thông báo tới
 * client qua WebSocket {@code /topic/menu/availability} (STT 76 / BR-50).</p>
 */
@Service
@Transactional(readOnly = true)
public class MenuItemService {

    private static final Logger logger = LoggerFactory.getLogger(MenuItemService.class);

    private static final String MENU_REFRESH_TOPIC = "/topic/menu/refresh";
    private static final String MENU_AVAILABILITY_TOPIC = "/topic/menu/availability";

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ImageStorageService imageStorageService;

    public MenuItemService(MenuItemRepository menuItemRepository,
                           CategoryRepository categoryRepository,
                           SimpMessagingTemplate messagingTemplate,
                           ImageStorageService imageStorageService) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
        this.messagingTemplate = messagingTemplate;
        this.imageStorageService = imageStorageService;
    }

    /**
     * Lọc và phân trang danh sách món ăn theo danh mục, tên (không phân biệt hoa thường)
     * và trạng thái khả dụng.
     *
     * @param pageable thông tin phân trang và cách sắp xếp (mặc định theo thời gian tạo)
     */
    public Page<MenuItemResponse> listAll(String categoryId, String q, Boolean available, Pageable pageable) {
        Specification<MenuItem> specification = buildSpecification(categoryId, q, available);

        Page<MenuItem> page = menuItemRepository.findAll(specification, pageable);

        // Gắn danh mục để response có tên danh mục mà không cần truy vấn lặp lại.
        Map<String, Category> categories = new HashMap<>();
        for (Category category : categoryRepository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc()) {
            categories.put(category.getId(), category);
        }

        return page.map(item -> {
            item.setCategory(categories.get(item.getCategoryId()));
            return MenuItemResponse.from(item);
        });
    }

    private Specification<MenuItem> buildSpecification(String categoryId, String q, Boolean available) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));

            if (categoryId != null && !categoryId.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("categoryId"), categoryId));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("itemName")), pattern));
            }
            if (available != null) {
                predicates.add(criteriaBuilder.equal(root.get("isAvailable"), available));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public MenuItemResponse getById(String id) {
        MenuItem item = findOrThrow(id);
        item.setCategory(resolveCategory(item.getCategoryId()));
        return MenuItemResponse.from(item);
    }

    @Transactional
    public MenuItemResponse create(MenuItemRequest request) {
        Category category = validateCategory(request.categoryId());
        String trimmedName = request.itemName().trim();

        if (menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNull(trimmedName)) {
            logger.warn("Menu item name already exists: {}", trimmedName);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên món ăn đã tồn tại.");
        }

        MenuItem item = new MenuItem(
            UUID.randomUUID().toString(),
            category.getId(),
            trimmedName,
            request.description(),
            request.currentPrice(),
            request.imageUrl(),
            request.isAvailable()
        );
        item.setCategory(category);

        MenuItem saved = menuItemRepository.save(item);
        logger.info("Created menu item: id={}, name={}", saved.getId(), saved.getItemName());
        broadcastRefresh("MENU_ITEM_CREATED");
        return MenuItemResponse.from(saved);
    }

    @Transactional
    public MenuItemResponse update(String id, MenuItemRequest request) {
        MenuItem item = findOrThrow(id);
        String trimmedName = request.itemName().trim();

        if (menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNullAndIdNot(trimmedName, id)) {
            logger.warn("Duplicate menu item name on update: {}", trimmedName);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên món ăn đã tồn tại.");
        }

        Category category = validateCategory(request.categoryId());
        boolean categoryChanged = !category.getId().equals(item.getCategoryId());
        String previousImageUrl = item.getImageUrl();

        item.setCategoryId(category.getId());
        item.setCategory(category);
        item.setItemName(trimmedName);
        item.setDescription(request.description());
        item.setCurrentPrice(request.currentPrice());
        item.setImageUrl(request.imageUrl());
        if (request.isAvailable() != null) {
            item.setIsAvailable(request.isAvailable());
        }

        // Ảnh cũ đã bị thay thế thì dọn khỏi thư mục lưu ảnh cục bộ.
        if (previousImageUrl != null && !previousImageUrl.equals(request.imageUrl())) {
            imageStorageService.delete(previousImageUrl);
        }

        logger.info("Updated menu item: id={}, name={}", item.getId(), item.getItemName());
        broadcastRefresh(categoryChanged ? "MENU_ITEM_CATEGORY_CHANGED" : "MENU_ITEM_UPDATED");
        return MenuItemResponse.from(item);
    }

    /**
     * STT 76 / BR-50: Bật / tắt trạng thái khả dụng của món ăn và thông báo
     * tới các client đang mở thực đơn.
     */
    @Transactional
    public MenuItemResponse updateAvailability(String id, UpdateAvailabilityRequest request) {
        MenuItem item = findOrThrow(id);
        item.setIsAvailable(request.isAvailable());
        item.setCategory(resolveCategory(item.getCategoryId()));

        logger.info("Menu item availability updated: id={}, name={}, available={}",
            item.getId(), item.getItemName(), item.getIsAvailable());

        MenuItemResponse response = MenuItemResponse.from(item);
        broadcastAvailability(item);
        return response;
    }

    /**
     * BR-48: Chỉ xóa mền món ăn, không bao giờ xóa cứng.
     */
    @Transactional
    public void delete(String id) {
        MenuItem item = findOrThrow(id);
        item.setDeletedAt(LocalDateTime.now());
        logger.info("Soft-deleted menu item: id={}, name={}", id, item.getItemName());
        broadcastRefresh("MENU_ITEM_DELETED");
    }

    private MenuItem findOrThrow(String id) {
        return menuItemRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món ăn."));
    }

    private Category validateCategory(String categoryId) {
        return categoryRepository.findByIdAndDeletedAtIsNull(categoryId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Danh mục không tồn tại hoặc đã bị xóa."));
    }

    private Category resolveCategory(String categoryId) {
        return categoryRepository.findByIdAndDeletedAtIsNull(categoryId).orElse(null);
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

    private void broadcastAvailability(MenuItem item) {
        try {
            MenuAvailabilityMessage message = new MenuAvailabilityMessage(
                item.getId(),
                item.getItemName(),
                item.getIsAvailable()
            );
            messagingTemplate.convertAndSend(MENU_AVAILABILITY_TOPIC, message);
            logger.debug("Broadcasted menu availability change: {}", message);
        } catch (Exception ex) {
            logger.error("Failed to broadcast menu availability change via WebSocket: ", ex);
        }
    }
}
