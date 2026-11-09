package com.huongViet.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huongViet.config.GlobalExceptionHandler;
import com.huongViet.feedback.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FeedbackAdminControllerTest {

    private MockMvc adminMockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FeedbackService feedbackService;

    @InjectMocks
    private FeedbackAdminController adminController;

    @BeforeEach
    void setUp() {
        adminMockMvc = MockMvcBuilders.standaloneSetup(adminController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /api/admin/feedbacks/stats: Quản lý lấy thống kê review 200 OK")
    void testGetAdminFeedbackStats() throws Exception {
        FeedbackStatsResponse mockStats = new FeedbackStatsResponse(10L, 4.8, Map.of(5, 8L, 4, 2L), 2L);
        when(feedbackService.getFeedbackStats()).thenReturn(mockStats);

        adminMockMvc.perform(get("/api/admin/feedbacks/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalReviews").value(10))
            .andExpect(jsonPath("$.averageRating").value(4.8))
            .andExpect(jsonPath("$.unrepliedCount").value(2));
    }

    @Test
    @DisplayName("PATCH /api/admin/feedbacks/{id}/reply: Quản lý gửi phản hồi 200 OK")
    void testAdminReplyEndpoint() throws Exception {
        ReplyFeedbackRequest replyReq = new ReplyFeedbackRequest("Cảm ơn quý khách!");
        FeedbackResponse mockResponse = new FeedbackResponse(
            "fb-1", "user-1", "Nguyen Van A", "0912345678", "inv-100",
            5, "Món ngon!", "Cảm ơn quý khách!", true, LocalDateTime.now()
        );

        when(feedbackService.replyFeedback(eq("fb-1"), any())).thenReturn(mockResponse);

        adminMockMvc.perform(patch("/api/admin/feedbacks/fb-1/reply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(replyReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.staffResponse").value("Cảm ơn quý khách!"));
    }

    @Test
    @DisplayName("PATCH /api/admin/feedbacks/{id}/visibility: Quản lý đổi trạng thái hiển thị 200 OK")
    void testAdminVisibilityEndpoint() throws Exception {
        UpdateFeedbackVisibilityRequest visReq = new UpdateFeedbackVisibilityRequest(false);
        FeedbackResponse mockResponse = new FeedbackResponse(
            "fb-1", "user-1", "Nguyen Van A", "0912345678", "inv-100",
            1, "Spam", null, false, LocalDateTime.now()
        );

        when(feedbackService.updateVisibility(eq("fb-1"), any())).thenReturn(mockResponse);

        adminMockMvc.perform(patch("/api/admin/feedbacks/fb-1/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(visReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isPublic").value(false));
    }
}

