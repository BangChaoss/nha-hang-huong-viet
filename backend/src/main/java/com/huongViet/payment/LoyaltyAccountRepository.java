package com.huongViet.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, String> {
    Optional<LoyaltyAccount> findByCustomerPhone(String customerPhone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoyaltyAccount a where a.customerPhone = :phone")
    Optional<LoyaltyAccount> lockByCustomerPhone(@Param("phone") String phone);
}
