package com.huongViet.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenTicketRepository extends JpaRepository<KitchenTicket, String> {

    /** BR-35: the KDS queue is served first-in-first-out. */
    List<KitchenTicket> findAllByOrderByCreatedAtAsc();

    List<KitchenTicket> findByStatusOrderByCreatedAtAsc(CookingStatus status);

    List<KitchenTicket> findByStatusInOrderByCreatedAtAsc(List<CookingStatus> statuses);

    Optional<KitchenTicket> findByOrderItemId(String orderItemId);
}
