package com.huongViet.table;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<TableZone, String> {

    List<TableZone> findAllByDeletedAtIsNullOrderByZoneNameAsc();

    Optional<TableZone> findByIdAndDeletedAtIsNull(String id);

    boolean existsByZoneNameIgnoreCaseAndDeletedAtIsNull(String zoneName);

    boolean existsByZoneNameIgnoreCaseAndDeletedAtIsNullAndIdNot(String zoneName, String id);

    long countByDeletedAtIsNull();
}
