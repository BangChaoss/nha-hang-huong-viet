package com.huongViet.payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, String> {
    Optional<LoyaltyAccount> findByCustomerPhone(String customerPhone);
}
