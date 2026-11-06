package com.huongViet.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huongViet.config.GlobalExceptionHandler;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử API quản trị món ăn (UC-20): phân trang, lọc, CRUD và trạng thái khả dụng.
 */
@ExtendWith(MockitoExtension.class)
class MenuItemAdminControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MenuItemService menuItemService;

    private MockMvc mockMvc;

    private MenuItemResponse pho;

    @BeforeEach
    void setUp() {
        MenuItemAdminController controller = new MenuItemAdminController(menuItemService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        pho = new MenuItemResponse(
            "item-1", "cat-1", "Món chính", "Phở Bò Tái",
            "Nước dùng xương ninh 12 giờ", new BigDecimal("85000"),
            "https://example.com/pho.jpg", true
        );
    }

    @Test
    @DisplayName("GET /api/admin/menu-items - mặc định phân trang 20 món, mới nhất trước")
    void listAll_DefaultPagingAndSort() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(menuItemService.listAll(isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(pho), pageable, 1));

        mockMvc.perform(get("/api/admin/menu-items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].itemName").value("Phở Bò Tái"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(menuItemService).listAll(isNull(), isNull(), isNull(), captor.capture());

        Pageable used = captor.getValue();
        assertEquals(0, used.getPageNumber());
        assertEquals(20, used.getPageSize());
        assertNotNull(used.getSort().getOrderFor("createdAt"));
        assertEquals(Sort.Direction.DESC, used.getSort().getOrderFor("createdAt").getDirection());
        assertEquals("id", used.getSort().getOrderFor("id").getProperty());
    }

    @Test
    @DisplayName("GET /api/admin/menu-items - chặn page âm, size vượt giới hạn và đọc đúng sort")
    void listAll_ClampsPagingAndParsesSort() throws Exception {
        when(menuItemService.listAll(isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(get("/api/admin/menu-items")
                .param("page", "-5")
                .param("size", "999")
                .param("sort", "price")
                .param("direction", "asc"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(menuItemService).listAll(isNull(), isNull(), isNull(), captor.capture());

        Pageable used = captor.getValue();
        assertEquals(0, used.getPageNumber());
        assertEquals(100, used.getPageSize());
        assertEquals(Sort.Direction.ASC, used.getSort().getOrderFor("currentPrice").getDirection());
    }

    @Test
    @DisplayName("GET /api/admin/menu-items - sort/hướng không hợp lệ rơi về mặc định createdAt DESC")
    void listAll_InvalidSortFallsBackToDefault() throws Exception {
        when(menuItemService.listAll(isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/admin/menu-items")
                .param("page", "0")
                .param("size", "0")
                .param("sort", "khong-hop-le")
                .param("direction", "khong-hop-le"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(menuItemService).listAll(isNull(), isNull(), isNull(), captor.capture());

        Pageable used = captor.getValue();
        assertEquals(1, used.getPageSize());
        assertEquals(Sort.Direction.DESC, used.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    @DisplayName("GET /api/admin/menu-items - chuyển tiếp bộ lọc danh mục, từ khóa và khả dụng")
    void listAll_PassesFiltersToService() throws Exception {
        when(menuItemService.listAll(eq("cat-1"), eq("pho"), eq(true), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(pho), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/menu-items")
                .param("categoryId", "cat-1")
                .param("q", "pho")
                .param("available", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value("item-1"));

        verify(menuItemService).listAll(eq("cat-1"), eq("pho"), eq(true), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/admin/menu-items/{id} - trả về chi tiết món")
    void getById_ReturnsItem() throws Exception {
        when(menuItemService.getById("item-1")).thenReturn(pho);

        mockMvc.perform(get("/api/admin/menu-items/item-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("item-1"))
            .andExpect(jsonPath("$.categoryName").value("Món chính"));
    }

    @Test
    @DisplayName("GET /api/admin/menu-items/{id} - món không tồn tại trả về 404")
    void getById_NotFound_Returns404() throws Exception {
        when(menuItemService.getById("item-x"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món ăn."));

        mockMvc.perform(get("/api/admin/menu-items/item-x"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Không tìm thấy món ăn."));
    }

    @Test
    @DisplayName("POST /api/admin/menu-items - tạo món trả về 201")
    void create_Returns201() throws Exception {
        MenuItemRequest request = new MenuItemRequest(
            "cat-1", "  Phở Bò Tái  ", "Mô tả", new BigDecimal("85000"), null, true);
        when(menuItemService.create(any(MenuItemRequest.class))).thenReturn(pho);

        mockMvc.perform(post("/api/admin/menu-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("item-1"))
            .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    @DisplayName("PUT /api/admin/menu-items/{id} - cập nhật món trả về 200")
    void update_Returns200() throws Exception {
        MenuItemRequest request = new MenuItemRequest(
            "cat-1", "Phở Bò Tái", "Mô tả mới", new BigDecimal("90000"), null, false);
        MenuItemResponse updated = new MenuItemResponse(
            "item-1", "cat-1", "Món chính", "Phở Bò Tái", "Mô tả mới",
            new BigDecimal("90000"), null, false);
        when(menuItemService.update(eq("item-1"), any(MenuItemRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/admin/menu-items/item-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentPrice").value(90000))
            .andExpect(jsonPath("$.isAvailable").value(false));
    }

    @Test
    @DisplayName("PATCH /api/admin/menu-items/{id}/availability - đổi trạng thái khả dụng")
    void updateAvailability_ReturnsUpdatedItem() throws Exception {
        MenuItemResponse unavailable = new MenuItemResponse(
            "item-1", "cat-1", "Món chính", "Phở Bò Tái", null,
            new BigDecimal("85000"), null, false);
        when(menuItemService.updateAvailability(eq("item-1"), any(UpdateAvailabilityRequest.class)))
            .thenReturn(unavailable);

        mockMvc.perform(patch("/api/admin/menu-items/item-1/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isAvailable\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAvailable").value(false));
    }

    @Test
    @DisplayName("DELETE /api/admin/menu-items/{id} - xóa mềm trả về 204")
    void delete_Returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/menu-items/item-1"))
            .andExpect(status().isNoContent());

        verify(menuItemService).delete("item-1");
    }

    @Test
    @DisplayName("POST /api/admin/menu-items - dữ liệu không hợp lệ trả về 400 và không gọi service")
    void create_InvalidPayload_Returns400() throws Exception {
        MenuItemRequest invalid = new MenuItemRequest(
            "cat-1", "   ", null, null, null, true);

        mockMvc.perform(post("/api/admin/menu-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verify(menuItemService, never()).create(any(MenuItemRequest.class));
    }
}
