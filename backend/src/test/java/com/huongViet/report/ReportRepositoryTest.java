package com.huongViet.report;

import com.huongViet.report.dto.ReservationStatsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReportRepository reportRepository;

    @Test
    @DisplayName("getReservationStats: calculates counts and rates accurately including PAID and REVIEWED")
    void testGetReservationStatsIncludesPaidAndReviewed() throws SQLException {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 14);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);

            ResultSet rs1 = mock(ResultSet.class);
            when(rs1.getString("status")).thenReturn("ARRIVED");
            when(rs1.getLong("count_val")).thenReturn(2L);
            handler.processRow(rs1);

            ResultSet rs2 = mock(ResultSet.class);
            when(rs2.getString("status")).thenReturn("PAID");
            when(rs2.getLong("count_val")).thenReturn(5L);
            handler.processRow(rs2);

            ResultSet rs3 = mock(ResultSet.class);
            when(rs3.getString("status")).thenReturn("REVIEWED");
            when(rs3.getLong("count_val")).thenReturn(1L);
            handler.processRow(rs3);

            ResultSet rs4 = mock(ResultSet.class);
            when(rs4.getString("status")).thenReturn("CANCELLED");
            when(rs4.getLong("count_val")).thenReturn(2L);
            handler.processRow(rs4);

            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(LocalDate.class), any(LocalDate.class));

        ReservationStatsResponse stats = reportRepository.getReservationStats(start, end);

        // total = 2 (ARRIVED) + 5 (PAID) + 1 (REVIEWED) + 2 (CANCELLED) = 10
        assertEquals(10L, stats.totalBookings());
        assertEquals(2L, stats.arrivedCount());
        assertEquals(6L, stats.paidCount()); // 5 PAID + 1 REVIEWED
        assertEquals(2L, stats.cancelledCount());
        // completionRate = (arrived + paid) / total = (2 + 6) / 10 = 80.0%
        assertEquals(80.0, stats.completionRate());
        // paidRate = paid / total = 6 / 10 = 60.0%
        assertEquals(60.0, stats.paidRate());
        // cancellationRate = cancelled / total = 2 / 10 = 20.0%
        assertEquals(20.0, stats.cancellationRate());
    }

    @Test
    @DisplayName("getReservationStats: returns empty when total is 0")
    void testGetReservationStatsEmpty() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 14);

        ReservationStatsResponse stats = reportRepository.getReservationStats(start, end);

        assertEquals(0L, stats.totalBookings());
        assertEquals(0L, stats.paidCount());
        assertEquals(0.0, stats.completionRate());
        assertEquals(0.0, stats.paidRate());
    }
}
