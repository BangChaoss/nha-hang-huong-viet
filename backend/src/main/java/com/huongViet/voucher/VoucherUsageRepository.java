package com.huongViet.voucher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, String> {
    @Query("select count(u) from VoucherUsage u where u.voucherId = :voucherId and u.customerPhone = :phone")
    long countByVoucherAndPhone(@Param("voucherId") String voucherId, @Param("phone") String phone);
}
