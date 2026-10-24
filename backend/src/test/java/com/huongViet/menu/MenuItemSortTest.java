package com.huongViet.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuItemSortTest {

    @Test
    @DisplayName("from - mặc định sắp xếp theo thời gian tạo, mới nhất lên đầu")
    void from_DefaultsToCreatedAtDesc() {
        assertEquals(MenuItemSort.CREATED_AT, MenuItemSort.from(null));
        assertEquals(MenuItemSort.CREATED_AT, MenuItemSort.from(""));
        assertEquals(MenuItemSort.CREATED_AT, MenuItemSort.from("createdAt"));
        assertEquals(MenuItemSort.CREATED_AT, MenuItemSort.from("created_at"));
        assertEquals(Sort.Direction.DESC, MenuItemSort.CREATED_AT.defaultDirection());
        assertEquals("createdAt", MenuItemSort.CREATED_AT.property());
    }

    @Test
    @DisplayName("from - nhận các tiêu chí sắp xếp khác")
    void from_ParsesOtherCriteria() {
        assertEquals(MenuItemSort.ITEM_NAME, MenuItemSort.from("name"));
        assertEquals(MenuItemSort.ITEM_NAME, MenuItemSort.from("itemName"));
        assertEquals(MenuItemSort.CURRENT_PRICE, MenuItemSort.from("price"));
        assertEquals(MenuItemSort.UPDATED_AT, MenuItemSort.from("updatedAt"));
    }

    @Test
    @DisplayName("from - giá trị lạ thì rơi về thời gian tạo")
    void from_UnknownValueFallsBack() {
        assertEquals(MenuItemSort.CREATED_AT, MenuItemSort.from("khong-ton-tai"));
    }

    @Test
    @DisplayName("directionFrom - đọc được hướng asc/desc, giá trị lạ dùng hướng dự phòng")
    void directionFrom_ParsesDirection() {
        assertEquals(Sort.Direction.ASC, MenuItemSort.directionFrom("asc", Sort.Direction.DESC));
        assertEquals(Sort.Direction.ASC, MenuItemSort.directionFrom("ascending", Sort.Direction.DESC));
        assertEquals(Sort.Direction.DESC, MenuItemSort.directionFrom("desc", Sort.Direction.ASC));
        assertEquals(Sort.Direction.DESC, MenuItemSort.directionFrom("descending", Sort.Direction.ASC));
        assertEquals(Sort.Direction.DESC, MenuItemSort.directionFrom(null, Sort.Direction.DESC));
        assertEquals(Sort.Direction.DESC, MenuItemSort.directionFrom("khong-hop-le", Sort.Direction.DESC));
    }
}
