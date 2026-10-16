package com.huongViet.voucher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, String> {
    boolean existsByVoucherCodeIgnoreCaseAndDeletedAtIsNull(String code);
    List<Voucher> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    Optional<Voucher> findByIdAndDeletedAtIsNull(String id);
    Optional<Voucher> findByVoucherCodeIgnoreCaseAndDeletedAtIsNull(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where upper(v.voucherCode)=upper(:code) and v.deletedAt is null")
    Optional<Voucher> lockByCode(@Param("code") String code);
}
