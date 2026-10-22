package com.huongViet.table;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FloorPlanLayoutRepository extends JpaRepository<FloorPlanLayout, String> {

    List<FloorPlanLayout> findAll();

    Optional<FloorPlanLayout> findByTableId(String tableId);

    void deleteByTableId(String tableId);
}
