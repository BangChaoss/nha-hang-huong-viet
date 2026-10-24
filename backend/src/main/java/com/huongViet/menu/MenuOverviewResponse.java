package com.huongViet.menu;

import java.util.List;

public record MenuOverviewResponse(
    List<MenuCategoryResponse> categories
) {}
