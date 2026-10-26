package com.huongViet.config;

import com.huongViet.auth.User;
import com.huongViet.auth.UserRepository;
import com.huongViet.auth.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Seeds a set of demo accounts — one per role — so a fresh database can be
 * explored right away.
 *
 * <p>Each account is created only when its username is still free, so the
 * seeder is idempotent and never overwrites real data. Demo passwords are
 * intentionally weak: set {@code app.demo-accounts.enabled=false} to keep
 * them out of a real deployment.</p>
 */
@Component
public class DemoAccountSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo-accounts.enabled:true}")
    private boolean enabled;

    public DemoAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Tài khoản demo: username, email, số điện thoại, vai trò, mật khẩu.
     */
    private record DemoAccount(String username, String email, String phone, UserRole role, String password) {}

    private static final List<DemoAccount> DEMO_ACCOUNTS = List.of(
        new DemoAccount("quanly", "quanly@huongviet.vn", "0901000001", UserRole.MANAGER, "QuanLy@12345"),
        new DemoAccount("nhanvien", "nhanvien@huongviet.vn", "0901000002", UserRole.STAFF, "NhanVien@12345"),
        new DemoAccount("nhanvien2", "nhanvien2@huongviet.vn", "0901000003", UserRole.STAFF, "NhanVien@12345"),
        new DemoAccount("dau_bep", "daubep@huongviet.vn", "0901000004", UserRole.CHEF, "DauBep@12345"),
        new DemoAccount("khachhang", "khach@huongviet.vn", "0902000001", UserRole.CUSTOMER, "KhachHang@12345"),
        new DemoAccount("khachvip", "vip@huongviet.vn", "0902000002", UserRole.CUSTOMER, "KhachHang@12345")
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            logger.info("Demo accounts seeding is disabled (app.demo-accounts.enabled=false).");
            return;
        }

        int created = 0;
        for (DemoAccount account : DEMO_ACCOUNTS) {
            if (userRepository.existsByUsernameAndDeletedAtIsNull(account.username())) {
                continue;
            }
            if (userRepository.existsByEmailAndDeletedAtIsNull(account.email())) {
                logger.warn("Demo account {} skipped: email {} already in use.",
                    account.username(), account.email());
                continue;
            }

            userRepository.save(new User(
                UUID.randomUUID().toString(),
                account.username(),
                account.email(),
                account.phone(),
                passwordEncoder.encode(account.password()),
                account.role()
            ));
            created++;
        }

        if (created > 0) {
            logger.info("=========================================================");
            logger.info(" Seeded {} demo account(s):", created);
            DEMO_ACCOUNTS.forEach(account ->
                logger.info("  - {} ({}) / {}", account.username(), account.role(), account.password()));
            logger.info("=========================================================");
        } else {
            logger.info("Demo accounts already present. Skipping demo seed.");
        }
    }
}
