import { useQuery } from "@tanstack/react-query";
import { menuApi } from "../api/menu-api";
import type { MenuCategoryResponse, MenuItemResponse } from "../api/menu-api";

/**
 * Danh sách món ăn cho khách hàng (UC-14).
 * Trả về kèm `categories` và `items` đã làm phẳng để tiện lọc theo danh mục.
 */
export function useMenu() {
  const query = useQuery({
    queryKey: ["menu"],
    queryFn: () => menuApi.getOverview(),
    staleTime: 5 * 60 * 1000,
  });

  const categories: MenuCategoryResponse[] = query.data?.categories ?? [];
  const items: MenuItemResponse[] = categories.flatMap((category) => category.items ?? []);

  return { ...query, categories, items };
}

/** Chi tiết một món ăn (UC-14). */
export function useMenuItem(id: string | null | undefined) {
  return useQuery({
    queryKey: ["menu", "item", id],
    queryFn: () => menuApi.getItem(id as string),
    enabled: Boolean(id),
    staleTime: 5 * 60 * 1000,
  });
}
