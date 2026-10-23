package com.huongViet.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByIdAndDeletedAtIsNull(String id);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    boolean existsByEmailAndDeletedAtIsNull(String email);
    boolean existsByUsernameAndDeletedAtIsNull(String username);
    boolean existsByPhoneNumberAndDeletedAtIsNull(String phoneNumber);

    boolean existsByUsernameAndIdNotAndDeletedAtIsNull(String username, String id);
    boolean existsByEmailAndIdNotAndDeletedAtIsNull(String email, String id);
    boolean existsByPhoneNumberAndIdNotAndDeletedAtIsNull(String phoneNumber, String id);

    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
          AND u.role IN :roles
          AND (:role IS NULL OR u.role = :role)
          AND (:status IS NULL OR u.status = :status)
          AND (:keyword IS NULL OR :keyword = ''
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR u.phoneNumber LIKE CONCAT('%', :keyword, '%'))
    """)
    Page<User> findStaffWithFilter(
        @Param("roles") List<UserRole> roles,
        @Param("role") UserRole role,
        @Param("status") String status,
        @Param("keyword") String keyword,
        Pageable pageable
    );
}
