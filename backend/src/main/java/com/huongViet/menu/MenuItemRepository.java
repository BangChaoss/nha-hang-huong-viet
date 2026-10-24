package com.huongViet.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, String>, JpaSpecificationExecutor<MenuItem> {

    List<MenuItem> findAllByDeletedAtIsNullOrderByItemNameAsc();

    List<MenuItem> findAllByCategoryIdAndDeletedAtIsNullOrderByItemNameAsc(String categoryId);

    Optional<MenuItem> findByIdAndDeletedAtIsNull(String id);

    boolean existsByItemNameIgnoreCaseAndDeletedAtIsNull(String itemName);

    boolean existsByItemNameIgnoreCaseAndDeletedAtIsNullAndIdNot(String itemName, String id);

    /** BR-53: a category holding active dishes cannot be deleted. */
    boolean existsByCategoryIdAndDeletedAtIsNull(String categoryId);

    long countByDeletedAtIsNull();
}
