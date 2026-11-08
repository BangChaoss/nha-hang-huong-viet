package com.huongViet.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huongViet.config.GlobalExceptionHandler;
import com.huongViet.feedback.dto.CreateFeedbackRequest;
import com.huongViet.feedback.dto.FeedbackResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FeedbackCustomerControllerTest {

    private MockMvc customerMockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FeedbackService feedbackService;

    @InjectMocks
    private FeedbackCustomerController customerController;

    @BeforeEach
    void setUp() {
        customerMockMvc = MockMvcBuilders.standaloneSetup(customerController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("POST /api/feedbacks: Gửi review thành công trả về 201 Created")
    void testSubmitFeedbackEndpoint() throws Exception {
        CreateFeedbackRequest request = new CreateFeedbackRequest("inv-100", 5, "Món ngon!");
        FeedbackResponse mockResponse = new FeedbackResponse(
            "fb-1", "user-1", "Nguyen Van A", "0912345678", "inv-100",
            5, "Món ngon!", null, true, LocalDateTime.now()
        );

        when(feedbackService.submitFeedback(any(), any())).thenReturn(mockResponse);

        customerMockMvc.perform(post("/api/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("fb-1"))
            .andExpect(jsonPath("$.rating").value(5))
            .andExpect(jsonPath("$.comment").value("Món ngon!"));
    }

    @Test
    @DisplayName("POST /api/feedbacks: Validation lỗi khi số sao < 1 hoặc > 5 trả về 400")
    void testSubmitFeedbackValidationStarError() throws Exception {
        CreateFeedbackRequest invalidRequest = new CreateFeedbackRequest("inv-100", 6, "Món ngon!");

        customerMockMvc.perform(post("/api/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/feedbacks/public: Trả về danh sách review công khai 200 OK")
    void testGetPublicFeedbacksEndpoint() throws Exception {
        FeedbackResponse mockReview = new FeedbackResponse(
            "fb-1", "user-1", "Nguyen Van A", "0912345678", "inv-100",
            5, "Món ngon!", null, true, LocalDateTime.now()
        );
        when(feedbackService.getPublicFeedbacks(any()))
            .thenReturn(new PageImpl<>(List.of(mockReview)));

        customerMockMvc.perform(get("/api/feedbacks/public")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value("fb-1"));
    }
}
