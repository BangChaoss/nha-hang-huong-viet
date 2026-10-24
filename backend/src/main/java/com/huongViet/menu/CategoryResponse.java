package com.huongViet.menu;

public record CategoryResponse(
    String id,
    String categoryName,
    Integer displayOrder,
    Long menuItemCount
) {
    public static CategoryResponse from(Category category, long menuItemCount) {
        return new CategoryResponse(
            category.getId(),
            category.getCategoryName(),
            category.getDisplayOrder(),
            menuItemCount
        );
    }
}
