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

    long countByDeletedAtIsNull();

    /**
     * UC10: Finds available tables for reservation assignment in a given zone with sufficient capacity.
     * Orders by capacity ascending (best fit) and tableNumber ascending.
     */
    @Query("""
        SELECT t FROM RestaurantTable t
        LEFT JOIN FETCH t.zone z
        WHERE t.zoneId = :zoneId
          AND t.status = com.huongViet.table.TableStatus.AVAILABLE
          AND t.capacity >= :partySize
          AND t.deletedAt IS NULL
        ORDER BY t.capacity ASC, t.tableNumber ASC
        """)
    List<RestaurantTable> findAvailableByZoneAndCapacity(
        @Param("zoneId") String zoneId,
        @Param("partySize") int partySize
    );

    /**
     * UC10: Finds all available tables with sufficient capacity across all active zones.
     * Orders by capacity ascending (best fit) and tableNumber ascending.
     */
    @Query("""
        SELECT t FROM RestaurantTable t
        LEFT JOIN FETCH t.zone z
        WHERE t.status = com.huongViet.table.TableStatus.AVAILABLE
          AND t.capacity >= :partySize
          AND t.deletedAt IS NULL
        ORDER BY t.capacity ASC, t.tableNumber ASC
        """)
    List<RestaurantTable> findAvailableByCapacity(
        @Param("partySize") int partySize
    );
}
