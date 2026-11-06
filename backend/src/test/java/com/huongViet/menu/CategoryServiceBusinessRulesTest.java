package com.huongViet.menu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Các quy tắc nghiệp vụ bổ sung của danh mục thực đơn (UC-21).
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceBusinessRulesTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private CategoryService categoryService;

    private Category starter;
    private MenuItem springRoll;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, menuItemRepository, messagingTemplate);
        starter = new Category("cat-1", "Khai vị", 1);
        springRoll = new MenuItem("item-1", "cat-1", "Chả Giò", "Chả giò rế",
            new BigDecimal("65000"), null, true);
    }

    @Test
    @DisplayName("listAll - mỗi danh mục kèm số món đang hoạt động")
    void listAll_IncludesActiveItemCount() {
        Category main = new Category("cat-2", "Món chính", 2);
        when(categoryRepository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc())
            .thenReturn(List.of(starter, main));
        when(menuItemRepository.findAllByCategoryIdAndDeletedAtIsNullOrderByItemNameAsc("cat-1"))
            .thenReturn(List.of(springRoll));
        when(menuItemRepository.findAllByCategoryIdAndDeletedAtIsNullOrderByItemNameAsc("cat-2"))
            .thenReturn(List.of());

        List<CategoryResponse> result = categoryService.listAll();

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).menuItemCount());
        assertEquals(0L, result.get(1).menuItemCount());
    }

    @Test
    @DisplayName("getById - trả về danh mục kèm số món")
    void getById_ReturnsCategoryWithCount() {
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(starter));
        when(menuItemRepository.findAllByCategoryIdAndDeletedAtIsNullOrderByItemNameAsc("cat-1"))
            .thenReturn(List.of(springRoll));

        CategoryResponse response = categoryService.getById("cat-1");

        assertEquals("Khai vị", response.categoryName());
        assertEquals(1L, response.menuItemCount());
    }

    @Test
    @DisplayName("getById - ném lỗi 404 nếu danh mục không tồn tại")
    void getById_NotFound_ThrowsNotFound() {
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-x")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.getById("cat-x"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Không tìm thấy danh mục.", ex.getReason());
    }

    @Test
    @DisplayName("create - phát thông báo CATEGORY_CREATED")
    void create_BroadcastsRefresh() {
        when(categoryRepository.existsByCategoryNameIgnoreCaseAndDeletedAtIsNull("Khai vị")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.create(new CategoryRequest(" Khai vị ", 1));

        assertEquals("Khai vị", response.categoryName());
        assertNotNull(response.id());

        ArgumentCaptor<MenuRefreshMessage> captor = ArgumentCaptor.forClass(MenuRefreshMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/menu/refresh"), captor.capture());
        assertEquals("CATEGORY_CREATED", captor.getValue().reason());
    }

    @Test
    @DisplayName("create - displayOrder null được chuẩn hóa thành 0")
    void create_NullDisplayOrder_DefaultsToZero() {
        when(categoryRepository.existsByCategoryNameIgnoreCaseAndDeletedAtIsNull("Khai vị")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.create(new CategoryRequest("Khai vị", null));

        assertEquals(0, response.displayOrder());
    }

    @Test
    @DisplayName("update - chuẩn hóa tên, cập nhật thứ tự và phát CATEGORY_UPDATED")
    void update_Success() {
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(starter));
        when(categoryRepository.existsByCategoryNameIgnoreCaseAndDeletedAtIsNullAndIdNot("Món khai vị", "cat-1"))
            .thenReturn(false);
        when(menuItemRepository.findAllByCategoryIdAndDeletedAtIsNullOrderByItemNameAsc("cat-1"))
            .thenReturn(List.of(springRoll));

        CategoryResponse response = categoryService.update("cat-1", new CategoryRequest("  Món khai vị  ", 3));

        assertEquals("Món khai vị", response.categoryName());
        assertEquals(3, response.displayOrder());
        assertEquals(1L, response.menuItemCount());

        ArgumentCaptor<MenuRefreshMessage> captor = ArgumentCaptor.forClass(MenuRefreshMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/menu/refresh"), captor.capture());
        assertEquals("CATEGORY_UPDATED", captor.getValue().reason());
    }

    @Test
    @DisplayName("delete - ném lỗi 404 nếu danh mục không tồn tại")
    void delete_NotFound_ThrowsNotFound() {
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-x")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.delete("cat-x"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(menuItemRepository, never()).existsByCategoryIdAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("delete - xóa mềm thành công và phát CATEGORY_DELETED")
    void delete_EmptyCategory_BroadcastsRefresh() {
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(starter));
        when(menuItemRepository.existsByCategoryIdAndDeletedAtIsNull("cat-1")).thenReturn(false);

        categoryService.delete("cat-1");

        assertNotNull(starter.getDeletedAt());
        ArgumentCaptor<MenuRefreshMessage> captor = ArgumentCaptor.forClass(MenuRefreshMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/menu/refresh"), captor.capture());
        assertEquals("CATEGORY_DELETED", captor.getValue().reason());
    }

    @Test
    @DisplayName("getMenuOverview - danh mục rỗng không xuất hiện trong thực đơn công khai")
    void getMenuOverview_EmptyMenu_ReturnsNoCategories() {
        when(categoryRepository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc()).thenReturn(List.of());

        MenuOverviewResponse overview = categoryService.getMenuOverview();

        assertTrue(overview.categories().isEmpty());
    }

    @Test
    @DisplayName("getMenuOverview - món trong thực đơn công khai được gắn đúng danh mục")
    void getMenuOverview_AttachesCategoryToItems() {
        when(categoryRepository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc()).thenReturn(List.of(starter));
        when(menuItemRepository.findAllByCategoryIdAndDeletedAtIsNullOrderByItemNameAsc("cat-1"))
            .thenReturn(List.of(springRoll));

        MenuOverviewResponse overview = categoryService.getMenuOverview();

        assertEquals(1, overview.categories().size());
        assertEquals("Khai vị", overview.categories().get(0).items().get(0).categoryName());
    }
}
