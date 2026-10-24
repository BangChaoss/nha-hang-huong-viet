package com.huongViet.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByTableIdOrderByCreatedAtDesc(String tableId);

    List<Order> findByStatusOrderByCreatedAtAsc(OrderStatus status);

    Optional<Order> findFirstByTableIdAndStatusOrderByCreatedAtDesc(String tableId, OrderStatus status);
}
