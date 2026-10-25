package com.huongViet.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemAuditRepository extends JpaRepository<OrderItemAudit, String> {

    List<OrderItemAudit> findByOrderItemIdOrderByChangedAtDesc(String orderItemId);
}
