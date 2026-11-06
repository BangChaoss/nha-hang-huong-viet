package com.huongViet.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huongViet.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử API quản trị danh mục thực đơn (UC-21).
 */
@ExtendWith(MockitoExtension.class)
class CategoryAdminControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CategoryAdminController controller = new CategoryAdminController(categoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /api/admin/categories - trả về danh sách danh mục")
    void listAll_ReturnsCategories() throws Exception {
        when(categoryService.listAll()).thenReturn(List.of(
            new CategoryResponse("cat-1", "Khai vị", 1, 2L)));

        mockMvc.perform(get("/api/admin/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("cat-1"))
            .andExpect(jsonPath("$[0].categoryName").value("Khai vị"))
            .andExpect(jsonPath("$[0].menuItemCount").value(2));
    }

    @Test
    @DisplayName("GET /api/admin/categories/{id} - trả về chi tiết danh mục")
    void getById_ReturnsCategory() throws Exception {
        when(categoryService.getById("cat-1"))
            .thenReturn(new CategoryResponse("cat-1", "Khai vị", 1, 2L));

        mockMvc.perform(get("/api/admin/categories/cat-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryName").value("Khai vị"));
    }

    @Test
    @DisplayName("GET /api/admin/categories/{id} - không tồn tại trả về 404")
    void getById_NotFound_Returns404() throws Exception {
        when(categoryService.getById("cat-x"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục."));

        mockMvc.perform(get("/api/admin/categories/cat-x"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Không tìm thấy danh mục."));
    }

    @Test
    @DisplayName("POST /api/admin/categories - tạo danh mục trả về 201")
    void create_Returns201() throws Exception {
        CategoryRequest request = new CategoryRequest("  Khai vị  ", 1);
        when(categoryService.create(any(CategoryRequest.class)))
            .thenReturn(new CategoryResponse("cat-1", "Khai vị", 1, 0L));

        mockMvc.perform(post("/api/admin/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("cat-1"))
            .andExpect(jsonPath("$.menuItemCount").value(0));
    }

    @Test
    @DisplayName("POST /api/admin/categories - tên trùng trả về 409")
    void create_DuplicateName_Returns409() throws Exception {
        when(categoryService.create(any(CategoryRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Tên danh mục đã tồn tại."));

        mockMvc.perform(post("/api/admin/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryName\":\"Khai vị\",\"displayOrder\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Tên danh mục đã tồn tại."));
    }

    @Test
    @DisplayName("PUT /api/admin/categories/{id} - cập nhật danh mục trả về 200")
    void update_Returns200() throws Exception {
        CategoryRequest request = new CategoryRequest("Món chính", 2);
        when(categoryService.update(eq("cat-1"), any(CategoryRequest.class)))
            .thenReturn(new CategoryResponse("cat-1", "Món chính", 2, 5L));

        mockMvc.perform(put("/api/admin/categories/cat-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryName").value("Món chính"))
            .andExpect(jsonPath("$.menuItemCount").value(5));
    }

    @Test
    @DisplayName("DELETE /api/admin/categories/{id} - xóa mềm trả về 204")
    void delete_Returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/categories/cat-1"))
            .andExpect(status().isNoContent());

        verify(categoryService).delete("cat-1");
    }

    @Test
    @DisplayName("DELETE /api/admin/categories/{id} - còn món ăn trả về 409")
    void delete_HasMenuItems_Returns409() throws Exception {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "Không thể xóa danh mục đang có món ăn."))
            .when(categoryService).delete("cat-1");

        mockMvc.perform(delete("/api/admin/categories/cat-1"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Không thể xóa danh mục đang có món ăn."));
    }

    @Test
    @DisplayName("POST /api/admin/categories - tên rỗng trả về 400 và không gọi service")
    void create_BlankName_Returns400() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryName\":\"   \",\"displayOrder\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verify(categoryService, never()).create(any(CategoryRequest.class));
    }
}
