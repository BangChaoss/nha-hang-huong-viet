package com.huongViet.menu;

import java.math.BigDecimal;

public record MenuItemResponse(
    String id,
    String categoryId,
    String categoryName,
    String itemName,
    String description,
    BigDecimal currentPrice,
    String imageUrl,
    Boolean isAvailable
) {
    public static MenuItemResponse from(MenuItem menuItem) {
        Category category = menuItem.getCategory();
        return new MenuItemResponse(
            menuItem.getId(),
            menuItem.getCategoryId(),
            category != null ? category.getCategoryName() : null,
            menuItem.getItemName(),
            menuItem.getDescription(),
            menuItem.getCurrentPrice(),
            menuItem.getImageUrl(),
            menuItem.getIsAvailable()
        );
    }
}
