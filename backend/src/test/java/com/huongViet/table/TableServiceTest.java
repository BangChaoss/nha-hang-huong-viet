package com.huongViet.table;

import com.huongViet.order.Order;
import com.huongViet.order.OrderRepository;
import com.huongViet.order.OrderStatus;
import com.huongViet.order.OrderType;
import com.huongViet.reservation.Reservation;
import com.huongViet.reservation.ReservationRepository;
import com.huongViet.reservation.ReservationStatus;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private OrderRepository orderRepository;

    private TableService tableService;

    private TableZone sampleZone;
    private RestaurantTable sampleTable;

    @BeforeEach
    void setUp() {
        tableService = new TableService(
            tableRepository,
            zoneRepository,
            floorPlanLayoutRepository,
            reservationRepository,
            messagingTemplate,
            orderRepository
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
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(sampleTable));

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

    @Test
    void updateStatus_LegacyOrderOpenAcrossMergedTables_BlocksClosingOtherTable() {
        sampleTable.setStatus(TableStatus.OCCUPIED);
        Order order = new Order("order-1", "table-2", "staff-1", OrderType.DINE_IN);
        LocalDate today = LocalDate.now();
        Reservation reservation = new Reservation("reservation-1", null, "Khách", "0900000000", null, 5,
            today, LocalTime.NOON, null, "zone-1");
        reservation.setStatus(ReservationStatus.ARRIVED);
        reservation.setTableId("table-1");
        reservation.setMergedTableIds("table-2");
        when(reservationRepository.findActiveReservationsForDate(any(LocalDate.class))).thenReturn(List.of(reservation));
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(sampleTable));
        when(orderRepository.findSeatedOrdersByTableIds(List.of("table-1", "table-2"))).thenReturn(List.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> tableService.updateStatus("table-1", new UpdateTableStatusRequest(TableStatus.AVAILABLE)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(TableStatus.OCCUPIED, sampleTable.getStatus());
        verify(tableRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void updateStatus_LegacyOrderOpenOnSingleTable_BlocksMovingToMaintenance() {
        sampleTable.setStatus(TableStatus.OCCUPIED);
        Order order = new Order("order-1", "table-1", "staff-1", OrderType.DINE_IN);
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(sampleTable));
        when(orderRepository.findSeatedOrdersByTableIds(List.of("table-1"))).thenReturn(List.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> tableService.updateStatus("table-1", new UpdateTableStatusRequest(TableStatus.MAINTENANCE)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(TableStatus.OCCUPIED, sampleTable.getStatus());
        verify(tableRepository, never()).save(any());
    }

    @Test
    void updateStatus_PaidPartyStillSeated_RejectsMakingTableAvailable() {
        sampleTable.setStatus(TableStatus.OCCUPIED);
        Order order = new Order("order-1", "table-1", "staff-1", OrderType.DINE_IN);
        order.setPartySize(2);
        order.setStatus(OrderStatus.COMPLETED);
        when(tableRepository.lockActiveById("table-1")).thenReturn(Optional.of(sampleTable));
        when(orderRepository.findSeatedOrdersByTableIds(List.of("table-1"))).thenReturn(List.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> tableService.updateStatus("table-1", new UpdateTableStatusRequest(TableStatus.AVAILABLE)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(TableStatus.OCCUPIED, sampleTable.getStatus());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("calculateOptimalLayoutPosition - xếp bàn mới cạnh bàn hiện có trong cùng khu vực")
    void calculateOptimalLayoutPosition_ExistingZone_PlacesAdjacent() {
        // Table 1 already exists in zone-1 at (50, 50, 90, 90)
        FloorPlanLayout layout1 = new FloorPlanLayout("layout-1", "table-1", 50.0, 50.0, 90.0, 90.0, 0.0, FloorPlanShape.RECTANGLE);
        when(tableRepository.findAllActiveWithZone()).thenReturn(java.util.List.of(sampleTable));
        when(floorPlanLayoutRepository.findAll()).thenReturn(java.util.List.of(layout1));

        RestaurantTable newTable = new RestaurantTable("table-2", "zone-1", "Bàn 02", 4);
        FloorPlanLayout result = tableService.calculateOptimalLayoutPosition(newTable);

        assertNotNull(result);
        assertEquals("table-2", result.getTableId());
        // Candidate adjacent on the right: 50 + 90 + 40 = 180.0
        assertEquals(180.0, result.getPositionX());
        assertEquals(50.0, result.getPositionY());
    }

    @Test
    @DisplayName("calculateOptimalLayoutPosition - khu vực mới hoàn toàn thì xếp vào vị trí trống không trùng khu vực cũ")
    void calculateOptimalLayoutPosition_NewZone_PlacesInFreeSpace() {
        // Table 1 in zone-1 occupies (50, 70, 90, 90)
        FloorPlanLayout layout1 = new FloorPlanLayout("layout-1", "table-1", 50.0, 70.0, 90.0, 90.0, 0.0, FloorPlanShape.RECTANGLE);
        when(tableRepository.findAllActiveWithZone()).thenReturn(java.util.List.of(sampleTable));
        when(floorPlanLayoutRepository.findAll()).thenReturn(java.util.List.of(layout1));

        // New table in a different zone: zone-2
        RestaurantTable newTableInNewZone = new RestaurantTable("table-new", "zone-2", "Bàn VIP 01", 4);
        FloorPlanLayout result = tableService.calculateOptimalLayoutPosition(newTableInNewZone);

        assertNotNull(result);
        assertEquals("table-new", result.getTableId());
        // Must not overlap zone-1 bounding box (which around 50, 70 with padding 24 extends [26..164, 16..188])
        assertFalse(result.getPositionX() == 50.0 && result.getPositionY() == 70.0);
    }

    @Test
    @DisplayName("createTable - Non-VIP bàn có sức chứa > 4 tự động chia thành các bàn modular 4/2 chỗ")
    void createTable_nonVip_capacityOver4_autoSplitsTables() {
        TableRequest request = new TableRequest("Bàn 05", 8, "zone-1");

        when(zoneRepository.findByIdAndDeletedAtIsNull("zone-1")).thenReturn(Optional.of(sampleZone));
        when(tableRepository.existsByTableNumberIgnoreCaseAndDeletedAtIsNull("Bàn 05")).thenReturn(false);
        when(tableRepository.existsByTableNumberIgnoreCaseAndDeletedAtIsNull("Bàn 05A")).thenReturn(false);
        when(tableRepository.existsByTableNumberIgnoreCaseAndDeletedAtIsNull("Bàn 05B")).thenReturn(false);
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TableResponse response = tableService.create(request);

        assertNotNull(response);
        // Bàn đầu tiên được tạo là Bàn 05A với capacity 4
        assertEquals("Bàn 05A", response.tableNumber());
        assertEquals(4, response.capacity());

        ArgumentCaptor<RestaurantTable> captor = ArgumentCaptor.forClass(RestaurantTable.class);
        verify(tableRepository, times(2)).save(captor.capture());
        List<RestaurantTable> savedTables = captor.getAllValues();
        assertEquals(2, savedTables.size());
        assertEquals("Bàn 05A", savedTables.get(0).getTableNumber());
        assertEquals(4, savedTables.get(0).getCapacity());
        assertEquals("Bàn 05B", savedTables.get(1).getTableNumber());
        assertEquals(4, savedTables.get(1).getCapacity());

        // Kiểm tra 2 bàn con được xếp cặp liền kề nhau ("xếp vào nhau")
        ArgumentCaptor<FloorPlanLayout> layoutCaptor = ArgumentCaptor.forClass(FloorPlanLayout.class);
        verify(floorPlanLayoutRepository, times(2)).save(layoutCaptor.capture());
        List<FloorPlanLayout> layouts = layoutCaptor.getAllValues();
        assertEquals(2, layouts.size());
        FloorPlanLayout layoutA = layouts.get(0);
        FloorPlanLayout layoutB = layouts.get(1);
        assertEquals(layoutA.getPositionY(), layoutB.getPositionY(), "Hai bàn con phải cùng hàng Y");
        assertEquals(layoutA.getPositionX() + layoutA.getWidth() + 10.0, layoutB.getPositionX(), "Bàn B phải đặt ngay cạnh phải Bàn A với khoảng cách 10px");
    }

    @Test
    @DisplayName("calculateAndApplyOptimalLayouts - Cơ chế Nam châm trái chiều: tự động đẩy khu vực liền kề ra xa khi mở rộng")
    void calculateAndApplyOptimalLayouts_MagneticRepulsion_PushesAdjacentZone() {
        // Khu vực 1 (Trong Nhà) có bàn tại (50, 70, 90, 90)
        FloorPlanLayout layout1 = new FloorPlanLayout("layout-1", "table-1", 50.0, 70.0, 90.0, 90.0, 0.0, FloorPlanShape.RECTANGLE);
        
        // Khu vực 2 (Phòng VIP) nằm ở phía dưới tại (50, 200, 140, 90)
        RestaurantTable vipTable = new RestaurantTable("vip-1", "zone-vip", "VIP 01", 10);
        TableZone vipZone = new TableZone("zone-vip", "Phòng VIP", true);
        vipTable.setZone(vipZone);
        FloorPlanLayout vipLayout = new FloorPlanLayout("layout-vip", "vip-1", 50.0, 200.0, 140.0, 90.0, 0.0, FloorPlanShape.RECTANGLE);

        when(tableRepository.findAllActiveWithZone()).thenReturn(List.of(sampleTable, vipTable));
        when(floorPlanLayoutRepository.findAll()).thenReturn(List.of(layout1, vipLayout));

        // Tạo bàn mới trong khu vực 1 làm khu vực 1 mở rộng xuống dưới
        RestaurantTable newTable = new RestaurantTable("table-new", "zone-1", "Bàn 02", 4);
        List<FloorPlanLayout> results = tableService.calculateAndApplyOptimalLayouts(List.of(newTable), sampleZone);

        assertNotNull(results);
        assertEquals(1, results.size());

        // Lực đẩy nam châm trái chiều phải đẩy bàn VIP xuống dưới (Y > 200)
        verify(floorPlanLayoutRepository, atLeastOnce()).save(argThat(l -> 
            l.getTableId().equals("vip-1") && l.getPositionY() > 200.0
        ));
    }

    @Test
    @DisplayName("createTable - Khu vực VIP bàn sức chứa > 4 KHÔNG bị chia tách")
    void createTable_vipZone_capacityOver4_doesNotSplit() {
        TableZone vipZone = new TableZone("zone-vip", "Phòng VIP", false);
        vipZone.setIsVip(true);
        TableRequest request = new TableRequest("VIP 01", 10, "zone-vip");

        when(zoneRepository.findByIdAndDeletedAtIsNull("zone-vip")).thenReturn(Optional.of(vipZone));
        when(tableRepository.existsByTableNumberIgnoreCaseAndDeletedAtIsNull("VIP 01")).thenReturn(false);
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TableResponse response = tableService.create(request);

        assertNotNull(response);
        assertEquals("VIP 01", response.tableNumber());
        assertEquals(10, response.capacity());
        verify(tableRepository, times(1)).save(any(RestaurantTable.class));
    }
}
