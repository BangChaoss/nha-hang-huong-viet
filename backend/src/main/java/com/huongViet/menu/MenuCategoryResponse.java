package com.huongViet.menu;

import java.util.List;

public record MenuCategoryResponse(
    String id,
    String categoryName,
    Integer displayOrder,
    List<MenuItemResponse> items
) {}
