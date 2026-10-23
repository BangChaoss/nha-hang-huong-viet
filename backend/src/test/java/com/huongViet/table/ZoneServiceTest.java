package com.huongViet.table;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private TableRepository tableRepository;

    private ZoneService zoneService;

    private TableZone sampleZone;

    @BeforeEach
    void setUp() {
        zoneService = new ZoneService(zoneRepository, tableRepository);
        sampleZone = new TableZone("zone-vip", "Phòng VIP", true);
    }

    @Test
    @DisplayName("createZone - thành công khi tên khu vực hợp lệ và chưa tồn tại")
    void createZone_Success() {
        ZoneRequest request = new ZoneRequest("Phòng VIP", true);

        when(zoneRepository.existsByZoneNameIgnoreCaseAndDeletedAtIsNull("Phòng VIP")).thenReturn(false);
        when(zoneRepository.save(any(TableZone.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ZoneResponse response = zoneService.create(request);

        assertNotNull(response);
        assertEquals("Phòng VIP", response.zoneName());
        assertTrue(response.isVip());
    }

    @Test
    @DisplayName("createZone - ném lỗi 409 CONFLICT nếu tên khu vực đã tồn tại")
    void createZone_DuplicateName_ThrowsConflict() {
        ZoneRequest request = new ZoneRequest("Phòng VIP", true);

        when(zoneRepository.existsByZoneNameIgnoreCaseAndDeletedAtIsNull("Phòng VIP")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> zoneService.create(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Tên khu vực đã tồn tại"));
    }

    @Test
    @DisplayName("deleteZone - ném lỗi 409 CONFLICT nếu khu vực đang chứa bàn")
    void deleteZone_HasTables_ThrowsConflict() {
        when(zoneRepository.findByIdAndDeletedAtIsNull("zone-vip")).thenReturn(Optional.of(sampleZone));
        when(tableRepository.existsByZoneIdAndDeletedAtIsNull("zone-vip")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> zoneService.delete("zone-vip"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Không thể xóa khu vực đang có bàn"));
        assertNull(sampleZone.getDeletedAt());
    }

    @Test
    @DisplayName("deleteZone - thành công khi khu vực không có bàn")
    void deleteZone_Empty_Success() {
        when(zoneRepository.findByIdAndDeletedAtIsNull("zone-vip")).thenReturn(Optional.of(sampleZone));
        when(tableRepository.existsByZoneIdAndDeletedAtIsNull("zone-vip")).thenReturn(false);

        zoneService.delete("zone-vip");

        assertNotNull(sampleZone.getDeletedAt());
    }
}
