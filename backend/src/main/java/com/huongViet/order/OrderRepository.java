package com.huongViet.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Repository truy vấn đơn hàng. */
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByTableIdOrderByCreatedAtDesc(String tableId);

    List<Order> findByStatusOrderByCreatedAtAsc(OrderStatus status);

    Optional<Order> findFirstByTableIdAndStatusOrderByCreatedAtDesc(String tableId, OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") String id);

    @Query("select o.tableId from Order o where o.id = :id")
    Optional<String> findTableIdByOrderId(@Param("id") String id);

    @Query("""
        select o from Order o where o.status = com.huongViet.order.OrderStatus.IN_PROGRESS
            or (o.partySize is not null and o.seatsReleasedAt is null
                and o.status = com.huongViet.order.OrderStatus.COMPLETED)
        order by o.createdAt asc, o.id asc
        """)
    List<Order> findSeatedOrders();

    @Query("""
        select o from Order o where o.tableId in :tableIds and
            (o.status = com.huongViet.order.OrderStatus.IN_PROGRESS
            or (o.partySize is not null and o.seatsReleasedAt is null
                and o.status = com.huongViet.order.OrderStatus.COMPLETED))
        order by o.createdAt asc, o.id asc
        """)
    List<Order> findSeatedOrdersByTableIds(@Param("tableIds") List<String> tableIds);
}
