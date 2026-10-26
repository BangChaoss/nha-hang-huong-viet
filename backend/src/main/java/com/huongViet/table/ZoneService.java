package com.huongViet.table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ZoneService {

    private static final Logger logger = LoggerFactory.getLogger(ZoneService.class);

    private final ZoneRepository zoneRepository;
    private final TableRepository tableRepository;

    public ZoneService(ZoneRepository zoneRepository, TableRepository tableRepository) {
        this.zoneRepository = zoneRepository;
        this.tableRepository = tableRepository;
    }

    public List<ZoneResponse> listAll() {
        return zoneRepository.findAllByDeletedAtIsNullOrderByZoneNameAsc()
            .stream()
            .map(zone -> ZoneResponse.from(zone, countTablesInZone(zone.getId())))
            .toList();
    }

    public ZoneResponse getById(String id) {
        TableZone zone = findOrThrow(id);
        return ZoneResponse.from(zone, countTablesInZone(zone.getId()));
    }

    @Transactional
    public ZoneResponse create(ZoneRequest request) {
        String trimmedName = request.zoneName().trim();
        if (zoneRepository.existsByZoneNameIgnoreCaseAndDeletedAtIsNull(trimmedName)) {
            logger.warn("Zone name already exists: {}", trimmedName);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên khu vực đã tồn tại.");
        }

        TableZone zone = new TableZone(
            UUID.randomUUID().toString(),
            trimmedName,
            request.isVip()
        );

        TableZone saved = zoneRepository.save(zone);
        logger.info("Created table zone: id={}, name={}", saved.getId(), saved.getZoneName());
        return ZoneResponse.from(saved, 0);
    }

    @Transactional
    public ZoneResponse update(String id, ZoneRequest request) {
        TableZone zone = findOrThrow(id);
        String trimmedName = request.zoneName().trim();

        if (zoneRepository.existsByZoneNameIgnoreCaseAndDeletedAtIsNullAndIdNot(trimmedName, id)) {
            logger.warn("Duplicate zone name on update: {}", trimmedName);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên khu vực đã tồn tại.");
        }

        zone.setZoneName(trimmedName);
        zone.setIsVip(request.isVip());
        logger.info("Updated table zone: id={}, name={}", zone.getId(), zone.getZoneName());
        return ZoneResponse.from(zone, countTablesInZone(zone.getId()));
    }

    @Transactional
    public void delete(String id) {
        TableZone zone = findOrThrow(id);

        if (tableRepository.existsByZoneIdAndDeletedAtIsNull(id)) {
            logger.warn("Attempt to delete zone with active tables: id={}", id);
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Không thể xóa khu vực đang có bàn. Vui lòng chuyển hoặc xóa các bàn trước."
            );
        }

        zone.setDeletedAt(LocalDateTime.now());
        logger.info("Soft-deleted table zone: id={}", id);
    }

    private TableZone findOrThrow(String id) {
        return zoneRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khu vực."));
    }

    private long countTablesInZone(String zoneId) {
        return tableRepository.findAllByZoneIdAndDeletedAtIsNull(zoneId).size();
    }
}
