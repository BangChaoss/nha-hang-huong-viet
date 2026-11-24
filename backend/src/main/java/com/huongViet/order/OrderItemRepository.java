package com.huongViet.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Repository truy vấn các món trong đơn hàng. */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {

    @Query("SELECT i FROM OrderItem i WHERE i.orderId = :orderId ORDER BY i.createdAt ASC, i.id ASC")
    List<OrderItem> findByOrderIdOrderByCreatedAtAsc(@Param("orderId") String orderId);

    List<OrderItem> findByOrderIdInOrderByCreatedAtAsc(List<String> orderIds);
}
