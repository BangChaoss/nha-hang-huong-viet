package com.huongViet.table;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableRepository extends JpaRepository<RestaurantTable, String> {

    @Query("SELECT t FROM RestaurantTable t LEFT JOIN FETCH t.zone WHERE t.deletedAt IS NULL ORDER BY t.tableNumber ASC")
    List<RestaurantTable> findAllActiveWithZone();

    @Query("SELECT t FROM RestaurantTable t LEFT JOIN FETCH t.zone WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<RestaurantTable> findByIdActiveWithZone(@Param("id") String id);

    List<RestaurantTable> findAllByZoneIdAndDeletedAtIsNull(String zoneId);

    Optional<RestaurantTable> findByIdAndDeletedAtIsNull(String id);

    boolean existsByTableNumberIgnoreCaseAndDeletedAtIsNull(String tableNumber);

    boolean existsByTableNumberIgnoreCaseAndDeletedAtIsNullAndIdNot(String tableNumber, String id);

    boolean existsByZoneIdAndDeletedAtIsNull(String zoneId);
}
