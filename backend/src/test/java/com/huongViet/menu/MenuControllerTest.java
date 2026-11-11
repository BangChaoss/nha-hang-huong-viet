package com.huongViet.menu;

import com.huongViet.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử API công khai của thực đơn (UC-14).
 */
@ExtendWith(MockitoExtension.class)
class MenuControllerTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private MenuItemService menuItemService;

    private MockMvc mockMvc;

    private MenuItemResponse pho;

    @BeforeEach
    void setUp() {
        MenuController controller = new MenuController(categoryService, menuItemService);
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
    @DisplayName("GET /api/menu - trả về thực đơn nhóm theo danh mục")
    void getMenuOverview_ReturnsCategoriesWithItems() throws Exception {
        MenuCategoryResponse category = new MenuCategoryResponse(
            "cat-1", "Món chính", 1, List.of(pho));
        when(categoryService.getMenuOverview()).thenReturn(new MenuOverviewResponse(List.of(category)));

        mockMvc.perform(get("/api/menu"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories.length()").value(1))
            .andExpect(jsonPath("$.categories[0].categoryName").value("Món chính"))
            .andExpect(jsonPath("$.categories[0].items[0].itemName").value("Phở Bò Tái"))
            .andExpect(jsonPath("$.categories[0].items[0].currentPrice").value(85000));
    }

    @Test
    @DisplayName("GET /api/menu - thực đơn rỗng vẫn trả về mảng categories")
    void getMenuOverview_Empty_ReturnsEmptyCategories() throws Exception {
        when(categoryService.getMenuOverview()).thenReturn(new MenuOverviewResponse(List.of()));

        mockMvc.perform(get("/api/menu"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/menu/categories - trả về danh mục kèm số món")
    void listCategories_ReturnsCategories() throws Exception {
        when(categoryService.listAll()).thenReturn(List.of(
            new CategoryResponse("cat-1", "Món chính", 1, 3L)));

        mockMvc.perform(get("/api/menu/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("cat-1"))
            .andExpect(jsonPath("$[0].categoryName").value("Món chính"))
            .andExpect(jsonPath("$[0].menuItemCount").value(3));
    }

    @Test
    @DisplayName("GET /api/menu/{id} - trả về chi tiết một món")
    void getMenuItem_ReturnsItem() throws Exception {
        when(menuItemService.getById("item-1")).thenReturn(pho);

        mockMvc.perform(get("/api/menu/item-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("item-1"))
            .andExpect(jsonPath("$.itemName").value("Phở Bò Tái"))
            .andExpect(jsonPath("$.categoryName").value("Món chính"))
            .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    @DisplayName("GET /api/menu/{id} - món không tồn tại trả về 404 kèm thông điệp")
    void getMenuItem_NotFound_Returns404() throws Exception {
        when(menuItemService.getById("item-x"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món ăn."));

        mockMvc.perform(get("/api/menu/item-x"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Không tìm thấy món ăn."));
    }
}
