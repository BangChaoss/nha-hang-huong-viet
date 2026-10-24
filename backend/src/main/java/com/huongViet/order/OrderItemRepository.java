package com.huongViet.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {

    List<OrderItem> findByOrderIdOrderByCreatedAtAsc(String orderId);

    List<OrderItem> findByOrderIdInOrderByCreatedAtAsc(List<String> orderIds);
}
