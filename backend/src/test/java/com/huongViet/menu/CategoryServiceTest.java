package com.huongViet.menu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private CategoryService categoryService;

    private Category sampleCategory;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, menuItemRepository, messagingTemplate);
        sampleCategory = new Category("cat-1", "Khai vị", 1);
    }

    @Test
    @DisplayName("createCategory - thành công khi tên danh mục hợp lệ và chưa tồn tại")
    void createCategory_Success() {
        CategoryRequest request = new CategoryRequest("  Khai vị  ", 1);

        when(categoryRepository.existsByCategoryNameIgnoreCaseAndDeletedAtIsNull("Khai vị")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.create(request);

        assertNotNull(response);
        assertEquals("Khai vị", response.categoryName());
        assertEquals(1, response.displayOrder());
        assertEquals(0L, response.menuItemCount());
        assertNotNull(response.id());
    }

    @Test
    @DisplayName("createCategory - ném lỗi 409 CONFLICT nếu tên danh mục đã tồn tại")
    void createCategory_DuplicateName_ThrowsConflict() {
        CategoryRequest request = new CategoryRequest("Khai vị", 1);

        when(categoryRepository.existsByCategoryNameIgnoreCaseAndDeletedAtIsNull("Khai vị")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> categoryService.create(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Tên danh mục đã tồn tại"));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("deleteCategory - ném lỗi 409 CONFLICT nếu danh mục đang có món ăn")
    void deleteCategory_HasMenuItems_ThrowsConflict() {
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(sampleCategory));
        when(menuItemRepository.existsByCategoryIdAndDeletedAtIsNull("cat-1")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> categoryService.delete("cat-1"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Không thể xóa danh mục đang có món ăn"));
        assertNull(sampleCategory.getDeletedAt());
    }

    @Test
    @DisplayName("deleteCategory - xóa mềm thành công khi danh mục không còn món ăn")
    void deleteCategory_Empty_SoftDelete() {
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(sampleCategory));
        when(menuItemRepository.existsByCategoryIdAndDeletedAtIsNull("cat-1")).thenReturn(false);

        categoryService.delete("cat-1");

        assertNotNull(sampleCategory.getDeletedAt());
        verify(menuItemRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("updateCategory - ném lỗi 409 CONFLICT nếu tên danh mục trùng với danh mục khác")
    void updateCategory_DuplicateName_ThrowsConflict() {
        CategoryRequest request = new CategoryRequest("Món chính", 2);

        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(sampleCategory));
        when(categoryRepository.existsByCategoryNameIgnoreCaseAndDeletedAtIsNullAndIdNot("Món chính", "cat-1"))
            .thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> categoryService.update("cat-1", request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Tên danh mục đã tồn tại"));
        assertEquals("Khai vị", sampleCategory.getCategoryName());
    }

    @Test
    @DisplayName("getMenuOverview - chỉ trả về các danh mục đang có món ăn")
    void getMenuOverview_OnlyCategoriesWithItems() {
        Category emptyCategory = new Category("cat-2", "Món chính", 2);

        MenuItem pho = new MenuItem(
            "item-1", "cat-1", "Phở Bò Tái", "Nước dùng xương ninh",
            new BigDecimal("85000"), null, true);

        when(categoryRepository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc())
            .thenReturn(List.of(sampleCategory, emptyCategory));
        when(menuItemRepository.findAllByCategoryIdAndDeletedAtIsNullOrderByItemNameAsc("cat-1"))
            .thenReturn(List.of(pho));
        when(menuItemRepository.findAllByCategoryIdAndDeletedAtIsNullOrderByItemNameAsc("cat-2"))
            .thenReturn(List.of());

        MenuOverviewResponse overview = categoryService.getMenuOverview();

        assertEquals(1, overview.categories().size());
        MenuCategoryResponse first = overview.categories().get(0);
        assertEquals("cat-1", first.id());
        assertEquals("Khai vị", first.categoryName());
        assertEquals(1, first.items().size());
        assertEquals("Phở Bò Tái", first.items().get(0).itemName());
        assertEquals("Khai vị", first.items().get(0).categoryName());
    }
}
