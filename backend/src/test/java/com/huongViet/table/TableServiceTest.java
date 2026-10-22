package com.huongViet.table;

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TableServiceTest {

    @Mock
    private TableRepository tableRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private FloorPlanLayoutRepository floorPlanLayoutRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private TableService tableService;

    private TableZone sampleZone;
    private RestaurantTable sampleTable;

    @BeforeEach
    void setUp() {
        tableService = new TableService(
            tableRepository,
            zoneRepository,
            floorPlanLayoutRepository,
            messagingTemplate
        );

        sampleZone = new TableZone("zone-1", "Sảnh Trong Nhà", false);
        sampleTable = new RestaurantTable("table-1", "zone-1", "Bàn 01", 4);
        sampleTable.setZone(sampleZone);
    }

    @Test
    @DisplayName("createTable - thành công khi số bàn chưa tồn tại và zone hợp lệ")
    void createTable_Success() {
        TableRequest request = new TableRequest("Bàn 01", 4, "zone-1");

        when(zoneRepository.findByIdAndDeletedAtIsNull("zone-1")).thenReturn(Optional.of(sampleZone));
        when(tableRepository.existsByTableNumberIgnoreCaseAndDeletedAtIsNull("Bàn 01")).thenReturn(false);
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TableResponse response = tableService.create(request);

        assertNotNull(response);
        assertEquals("Bàn 01", response.tableNumber());
        assertEquals(4, response.capacity());
        assertEquals(TableStatus.AVAILABLE, response.status());

        verify(floorPlanLayoutRepository).save(any(FloorPlanLayout.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/table-status"), any(TableStatusMessage.class));
    }

    @Test
    @DisplayName("BR-57: createTable - ném lỗi 409 CONFLICT nếu số bàn đã tồn tại")
    void createTable_DuplicateNumber_ThrowsConflict() {
        TableRequest request = new TableRequest("Bàn 01", 4, "zone-1");

        when(zoneRepository.findByIdAndDeletedAtIsNull("zone-1")).thenReturn(Optional.of(sampleZone));
        when(tableRepository.existsByTableNumberIgnoreCaseAndDeletedAtIsNull("Bàn 01")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> tableService.create(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Số bàn đã tồn tại"));

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("BR-56: updateTable - ném lỗi 409 CONFLICT nếu bàn đang RESERVED")
    void updateTable_Reserved_ThrowsConflict() {
        sampleTable.setStatus(TableStatus.RESERVED);
        when(tableRepository.findByIdActiveWithZone("table-1")).thenReturn(Optional.of(sampleTable));

        TableRequest updateReq = new TableRequest("Bàn 01 Mới", 6, "zone-1");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> tableService.update("table-1", updateReq));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Không thể chỉnh sửa bàn đang được sử dụng hoặc đã đặt trước"));
    }

    @Test
    @DisplayName("BR-56: deleteTable - ném lỗi 409 CONFLICT nếu bàn đang OCCUPIED")
    void deleteTable_Occupied_ThrowsConflict() {
        sampleTable.setStatus(TableStatus.OCCUPIED);
        when(tableRepository.findByIdActiveWithZone("table-1")).thenReturn(Optional.of(sampleTable));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> tableService.delete("table-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Không thể xóa bàn đang được sử dụng hoặc đã đặt trước"));
        assertNull(sampleTable.getDeletedAt());
    }

    @Test
    @DisplayName("deleteTable - thành công khi bàn ở trạng thái AVAILABLE")
    void deleteTable_Available_Success() {
        sampleTable.setStatus(TableStatus.AVAILABLE);
        when(tableRepository.findByIdActiveWithZone("table-1")).thenReturn(Optional.of(sampleTable));

        tableService.delete("table-1");

        assertNotNull(sampleTable.getDeletedAt());
        verify(floorPlanLayoutRepository).deleteByTableId("table-1");
    }

    @Test
    @DisplayName("updateStatus - cập nhật trạng thái bàn và broadcast WebSocket topic")
    void updateStatus_Success() {
        sampleTable.setStatus(TableStatus.AVAILABLE);
        when(tableRepository.findByIdActiveWithZone("table-1")).thenReturn(Optional.of(sampleTable));

        UpdateTableStatusRequest request = new UpdateTableStatusRequest(TableStatus.MAINTENANCE);
        TableResponse response = tableService.updateStatus("table-1", request);

        assertEquals(TableStatus.MAINTENANCE, response.status());

        ArgumentCaptor<TableStatusMessage> messageCaptor = ArgumentCaptor.forClass(TableStatusMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/table-status"), messageCaptor.capture());

        TableStatusMessage broadcasted = messageCaptor.getValue();
        assertEquals("table-1", broadcasted.tableId());
        assertEquals("Bàn 01", broadcasted.tableNumber());
        assertEquals(TableStatus.AVAILABLE, broadcasted.oldStatus());
        assertEquals(TableStatus.MAINTENANCE, broadcasted.newStatus());
        assertEquals("Sảnh Trong Nhà", broadcasted.zoneName());
    }
}
