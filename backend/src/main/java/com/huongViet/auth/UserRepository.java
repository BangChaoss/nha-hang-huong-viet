package com.huongViet.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);
    boolean existsByEmailAndDeletedAtIsNull(String email);
    boolean existsByUsernameAndDeletedAtIsNull(String username);
    boolean existsByPhoneNumberAndDeletedAtIsNull(String phoneNumber);
}
