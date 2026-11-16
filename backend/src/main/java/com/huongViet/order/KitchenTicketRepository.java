package com.huongViet.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Repository truy vấn phiếu bếp. */
@Repository
public interface KitchenTicketRepository extends JpaRepository<KitchenTicket, String> {

    /** BR-35: the KDS queue is served first-in-first-out. */
    List<KitchenTicket> findAllByOrderByCreatedAtAsc();

    List<KitchenTicket> findByStatusOrderByCreatedAtAsc(CookingStatus status);

    List<KitchenTicket> findByStatusInOrderByCreatedAtAsc(List<CookingStatus> statuses);

    Optional<KitchenTicket> findByOrderItemId(String orderItemId);

    @Query("""
        SELECT k FROM KitchenTicket k
        WHERE k.createdAt >= :start AND k.createdAt <= :end
          AND (:status IS NULL OR k.status = :status)
        ORDER BY k.createdAt ASC
    """)
    List<KitchenTicket> findByDateAndStatusOrderByCreatedAtAsc(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        @Param("status") CookingStatus status
    );
}