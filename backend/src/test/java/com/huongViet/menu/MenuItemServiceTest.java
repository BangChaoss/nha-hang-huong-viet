package com.huongViet.menu;

import com.huongViet.upload.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ImageStorageService imageStorageService;

    private MenuItemService menuItemService;

    private Category sampleCategory;

    private MenuItem sampleItem;

    @BeforeEach
    void setUp() {
        menuItemService = new MenuItemService(
            menuItemRepository, categoryRepository, messagingTemplate, imageStorageService);
        sampleCategory = new Category("cat-1", "Món chính", 2);
        sampleItem = new MenuItem(
            "item-1",
            "cat-1",
            "Phở Bò Tái",
            "Nước dùng xương ninh 12 giờ",
            new BigDecimal("85000"),
            "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43",
            true
        );
        sampleItem.setCategory(sampleCategory);
    }

    @Test
    @DisplayName("createMenuItem - thành công khi danh mục hợp lệ và tên món chưa tồn tại")
    void createMenuItem_Success() {
        MenuItemRequest request = new MenuItemRequest(
            "cat-1",
            "  Phở Bò Tái  ",
            "Nước dùng xương ninh 12 giờ",
            new BigDecimal("85000"),
            "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43",
            true
        );

        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(sampleCategory));
        when(menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNull("Phở Bò Tái")).thenReturn(false);
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuItemResponse response = menuItemService.create(request);

        assertNotNull(response);
        assertEquals("Phở Bò Tái", response.itemName());
        assertEquals("cat-1", response.categoryId());
        assertEquals("Món chính", response.categoryName());
        assertEquals(new BigDecimal("85000"), response.currentPrice());
        assertTrue(response.isAvailable());
    }

    @Test
    @DisplayName("createMenuItem - ném lỗi 409 CONFLICT nếu tên món ăn đã tồn tại")
    void createMenuItem_DuplicateName_ThrowsConflict() {
        MenuItemRequest request = new MenuItemRequest(
            "cat-1", "Phở Bò Tái", null, new BigDecimal("85000"), null, true);

        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(sampleCategory));
        when(menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNull("Phở Bò Tái")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> menuItemService.create(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Tên món ăn đã tồn tại"));
        verify(menuItemRepository, never()).save(any(MenuItem.class));
    }

    @Test
    @DisplayName("createMenuItem - ném lỗi 400 BAD_REQUEST nếu danh mục không tồn tại")
    void createMenuItem_UnknownCategory_ThrowsBadRequest() {
        MenuItemRequest request = new MenuItemRequest(
            "cat-khong-ton-tai", "Phở Bò Tái", null, new BigDecimal("85000"), null, true);

        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-khong-ton-tai")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> menuItemService.create(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Danh mục không tồn tại hoặc đã bị xóa"));
        verify(menuItemRepository, never()).save(any(MenuItem.class));
    }

    @Test
    @DisplayName("updateAvailability - đổi trạng thái khả dụng của món ăn")
    void updateAvailability_FlipsFlag() {
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(sampleItem));
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(sampleCategory));

        MenuItemResponse response = menuItemService.updateAvailability("item-1", new UpdateAvailabilityRequest(false));

        assertFalse(response.isAvailable());
        assertFalse(sampleItem.getIsAvailable());
        assertEquals("Phở Bò Tái", response.itemName());
    }

    @Test
    @DisplayName("deleteMenuItem - chỉ xóa mềm, không bao giờ xóa cứng khỏi database")
    void deleteMenuItem_SoftDeleteOnly() {
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(sampleItem));

        menuItemService.delete("item-1");

        assertNotNull(sampleItem.getDeletedAt());
        verify(menuItemRepository, never()).deleteById(any());
        verify(menuItemRepository, never()).delete(any(MenuItem.class));
    }

    @Test
    @DisplayName("getById - ném lỗi 404 NOT_FOUND nếu không tìm thấy món ăn")
    void getById_NotFound_ThrowsNotFound() {
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-khong-ton-tai")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> menuItemService.getById("item-khong-ton-tai"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Không tìm thấy món ăn"));
    }

    @Test
    @DisplayName("updateMenuItem - xoá ảnh cũ trong thư mục upload khi thay ảnh mới")
    void updateMenuItem_ReplacesImage_DeletesOldFile() {
        sampleItem.setImageUrl("/uploads/menu/anh-cu.png");
        MenuItemRequest request = new MenuItemRequest(
            "cat-1", "Phở Bò Tái", "Mô tả", new BigDecimal("90000"), "/uploads/menu/anh-moi.png", true);

        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(sampleItem));
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(sampleCategory));
        when(menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNullAndIdNot("Phở Bò Tái", "item-1"))
            .thenReturn(false);

        MenuItemResponse response = menuItemService.update("item-1", request);

        assertEquals("/uploads/menu/anh-moi.png", response.imageUrl());
        verify(imageStorageService).delete("/uploads/menu/anh-cu.png");
    }

    @Test
    @DisplayName("updateMenuItem - không xoá ảnh cũ khi giữ nguyên ảnh đang dùng")
    void updateMenuItem_SameImage_KeepsOldFile() {
        sampleItem.setImageUrl("/uploads/menu/anh-cu.png");
        MenuItemRequest request = new MenuItemRequest(
            "cat-1", "Phở Bò Tái", "Mô tả", new BigDecimal("90000"), "/uploads/menu/anh-cu.png", true);

        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(sampleItem));
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(sampleCategory));
        when(menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNullAndIdNot("Phở Bò Tái", "item-1"))
            .thenReturn(false);

        menuItemService.update("item-1", request);

        verify(imageStorageService, never()).delete(any());
    }

    @Test
    @DisplayName("listAll - trả về một trang kèm tổng số món")
    void listAll_ReturnsPageWithTotal() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MenuItem> page = new PageImpl<>(List.of(sampleItem), pageable, 25);

        when(menuItemRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(page);
        when(categoryRepository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc())
            .thenReturn(List.of(sampleCategory));

        Page<MenuItemResponse> result = menuItemService.listAll(null, null, null, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(0, result.getNumber());
        assertEquals(25, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals("Món chính", result.getContent().get(0).categoryName());
    }

    @Test
    @DisplayName("listAll - truyền thông tin phân trang xuống repository")
    void listAll_PassesPageableThrough() {
        PageRequest pageable = PageRequest.of(2, 20, Sort.by(Sort.Direction.ASC, "itemName"));
        when(menuItemRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(categoryRepository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc())
            .thenReturn(List.of(sampleCategory));

        menuItemService.listAll("cat-1", "pho", true, pageable);

        verify(menuItemRepository).findAll(any(Specification.class), eq(pageable));
    }
}
