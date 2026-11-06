package com.huongViet.menu;

import com.huongViet.upload.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Các quy tắc nghiệp vụ bổ sung của thực đơn: thông báo realtime, cập nhật một phần
 * và các trường hợp lỗi chưa được phủ ở {@link MenuItemServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class MenuItemServiceBusinessRulesTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ImageStorageService imageStorageService;

    private MenuItemService menuItemService;

    private Category mainCategory;
    private Category starterCategory;
    private MenuItem pho;

    @BeforeEach
    void setUp() {
        menuItemService = new MenuItemService(
            menuItemRepository, categoryRepository, messagingTemplate, imageStorageService);

        mainCategory = new Category("cat-1", "Món chính", 1);
        starterCategory = new Category("cat-2", "Khai vị", 2);
        pho = new MenuItem("item-1", "cat-1", "Phở Bò Tái", "Nước dùng xương ninh",
            new BigDecimal("85000"), null, true);
        pho.setCategory(mainCategory);
    }

    @Test
    @DisplayName("create - phát thông báo MENU_ITEM_CREATED để client tải lại thực đơn")
    void create_BroadcastsRefresh() {
        MenuItemRequest request = new MenuItemRequest(
            "cat-1", "  Phở Bò Tái  ", null, new BigDecimal("85000"), null, true);
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(mainCategory));
        when(menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNull("Phở Bò Tái")).thenReturn(false);
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        menuItemService.create(request);

        ArgumentCaptor<MenuRefreshMessage> captor = ArgumentCaptor.forClass(MenuRefreshMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/menu/refresh"), captor.capture());
        assertEquals("MENU_ITEM_CREATED", captor.getValue().reason());
    }

    @Test
    @DisplayName("create - isAvailable null mặc định món vẫn đang bán")
    void create_NullAvailability_DefaultsToAvailable() {
        MenuItemRequest request = new MenuItemRequest(
            "cat-1", "Phở Bò Tái", null, new BigDecimal("85000"), null, null);
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(mainCategory));
        when(menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNull("Phở Bò Tái")).thenReturn(false);
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuItemResponse response = menuItemService.create(request);

        assertTrue(response.isAvailable());
    }

    @Test
    @DisplayName("update - ném lỗi 404 nếu món không tồn tại")
    void update_NotFound_ThrowsNotFound() {
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-x")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> menuItemService.update("item-x",
                new MenuItemRequest("cat-1", "Phở", null, BigDecimal.ONE, null, true)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Không tìm thấy món ăn"));
    }

    @Test
    @DisplayName("update - trùng tên món thì không lưu và không xóa ảnh cũ")
    void update_DuplicateName_DoesNotTouchImage() {
        MenuItemRequest request = new MenuItemRequest(
            "cat-1", "Bún Chả", null, new BigDecimal("70000"), null, true);
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(pho));
        when(menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNullAndIdNot("Bún Chả", "item-1"))
            .thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> menuItemService.update("item-1", request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Phở Bò Tái", pho.getItemName());
        verify(imageStorageService, never()).delete(any());
    }

    @Test
    @DisplayName("update - bỏ trống isAvailable thì giữ nguyên trạng thái đang bán")
    void update_NullAvailability_KeepsCurrentValue() {
        MenuItemRequest request = new MenuItemRequest(
            "cat-1", "Phở Bò Tái", "Mô tả mới", new BigDecimal("90000"), null, null);
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(pho));
        when(menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNullAndIdNot("Phở Bò Tái", "item-1"))
            .thenReturn(false);
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(mainCategory));

        MenuItemResponse response = menuItemService.update("item-1", request);

        assertTrue(response.isAvailable());
        assertEquals(new BigDecimal("90000"), response.currentPrice());
    }

    @Test
    @DisplayName("update - đổi danh mục phát thông báo MENU_ITEM_CATEGORY_CHANGED")
    void update_CategoryChanged_BroadcastsCategoryChanged() {
        MenuItemRequest request = new MenuItemRequest(
            "cat-2", "Phở Bò Tái", null, new BigDecimal("85000"), null, true);
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(pho));
        when(menuItemRepository.existsByItemNameIgnoreCaseAndDeletedAtIsNullAndIdNot("Phở Bò Tái", "item-1"))
            .thenReturn(false);
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-2")).thenReturn(Optional.of(starterCategory));

        MenuItemResponse response = menuItemService.update("item-1", request);

        assertEquals("cat-2", response.categoryId());
        assertEquals("Khai vị", response.categoryName());

        ArgumentCaptor<MenuRefreshMessage> captor = ArgumentCaptor.forClass(MenuRefreshMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/menu/refresh"), captor.capture());
        assertEquals("MENU_ITEM_CATEGORY_CHANGED", captor.getValue().reason());
    }

    @Test
    @DisplayName("updateAvailability - phát thông báo trạng thái món tới /topic/menu/availability")
    void updateAvailability_BroadcastsAvailability() {
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(pho));
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(mainCategory));

        menuItemService.updateAvailability("item-1", new UpdateAvailabilityRequest(false));

        ArgumentCaptor<MenuAvailabilityMessage> captor = ArgumentCaptor.forClass(MenuAvailabilityMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/menu/availability"), captor.capture());
        assertEquals("item-1", captor.getValue().menuItemId());
        assertEquals("Phở Bò Tái", captor.getValue().itemName());
        assertFalse(captor.getValue().isAvailable());
    }

    @Test
    @DisplayName("updateAvailability - lỗi WebSocket không làm hỏng nghiệp vụ")
    void updateAvailability_WebSocketFailure_DoesNotBreakBusinessLogic() {
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(pho));
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(mainCategory));
        doThrow(new RuntimeException("broker down"))
            .when(messagingTemplate).convertAndSend(eq("/topic/menu/availability"), any(MenuAvailabilityMessage.class));

        MenuItemResponse response = menuItemService.updateAvailability(
            "item-1", new UpdateAvailabilityRequest(false));

        assertFalse(response.isAvailable());
    }

    @Test
    @DisplayName("delete - xóa mềm và phát thông báo MENU_ITEM_DELETED")
    void delete_BroadcastsRefresh() {
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(pho));

        menuItemService.delete("item-1");

        ArgumentCaptor<MenuRefreshMessage> captor = ArgumentCaptor.forClass(MenuRefreshMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/menu/refresh"), captor.capture());
        assertEquals("MENU_ITEM_DELETED", captor.getValue().reason());
    }

    @Test
    @DisplayName("getById - gắn tên danh mục vào response")
    void getById_AttachesCategoryName() {
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(pho));
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.of(mainCategory));

        MenuItemResponse response = menuItemService.getById("item-1");

        assertEquals("Món chính", response.categoryName());
    }

    @Test
    @DisplayName("getById - danh mục đã bị xóa thì trả về categoryName null thay vì lỗi")
    void getById_DeletedCategory_ReturnsNullCategoryName() {
        when(menuItemRepository.findByIdAndDeletedAtIsNull("item-1")).thenReturn(Optional.of(pho));
        when(categoryRepository.findByIdAndDeletedAtIsNull("cat-1")).thenReturn(Optional.empty());

        MenuItemResponse response = menuItemService.getById("item-1");

        assertNull(response.categoryName());
    }

    @Test
    @DisplayName("listAll - món thuộc danh mục đã xóa vẫn hiển thị với categoryName null")
    void listAll_ItemWithMissingCategory_ReturnsNullCategoryName() {
        Pageable pageable = PageRequest.of(0, 10);
        when(menuItemRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(pho), pageable, 1));
        when(categoryRepository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc()).thenReturn(List.of());

        var page = menuItemService.listAll(null, null, null, pageable);

        assertEquals(1, page.getContent().size());
        assertNull(page.getContent().get(0).categoryName());
    }
}
