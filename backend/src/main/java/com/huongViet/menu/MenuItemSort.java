package com.huongViet.menu;

import org.springframework.data.domain.Sort;

import java.util.Locale;

/**
 * Cách sắp xếp danh sách món ăn trong màn Quản lý thực đơn.
 *
 * <p>Mặc định sắp xếp theo thời gian tạo, món mới nhất lên đầu — nhờ vậy người quản lý
 * luôn thấy món vừa thêm nằm ở trên cùng.</p>
 */
public enum MenuItemSort {

    /** Thời gian tạo (mặc định). */
    CREATED_AT("createdAt", Sort.Direction.DESC),
    /** Thời gian cập nhật gần nhất. */
    UPDATED_AT("updatedAt", Sort.Direction.DESC),
    /** Tên món. */
    ITEM_NAME("itemName", Sort.Direction.ASC),
    /** Giá món. */
    CURRENT_PRICE("currentPrice", Sort.Direction.ASC);

    private final String property;
    private final Sort.Direction defaultDirection;

    MenuItemSort(String property, Sort.Direction defaultDirection) {
        this.property = property;
        this.defaultDirection = defaultDirection;
    }

    public String property() {
        return property;
    }

    public Sort.Direction defaultDirection() {
        return defaultDirection;
    }

    /**
     * Đổi chuỗi do client gửi lên thành cách sắp xếp.
     * Giá trị lạ thì trả về {@link #CREATED_AT}.
     */
    public static MenuItemSort from(String value) {
        if (value == null || value.isBlank()) {
            return CREATED_AT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "createdat", "created_at", "newest", "created" -> CREATED_AT;
            case "updatedat", "updated_at", "updated" -> UPDATED_AT;
            case "name", "itemname", "item_name" -> ITEM_NAME;
            case "price", "currentprice", "current_price" -> CURRENT_PRICE;
            default -> CREATED_AT;
        };
    }

    /**
     * Đổi chuỗi hướng sắp xếp do client gửi lên.
     * Giá trị lạ thì trả về hướng mặc định của cách sắp xếp.
     */
    public static Sort.Direction directionFrom(String value, Sort.Direction fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "asc", "ascending" -> Sort.Direction.ASC;
            case "desc", "descending" -> Sort.Direction.DESC;
            default -> fallback;
        };
    }
}
