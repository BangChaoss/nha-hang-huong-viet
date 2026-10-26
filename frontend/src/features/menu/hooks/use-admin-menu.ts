import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { adminMenuApi } from "../api/menu-api";
import type {
  CategoryRequest,
  CategoryResponse,
  MenuItemFilterParams,
  MenuItemRequest,
  MenuItemResponse,
  PageResponse,
} from "../api/menu-api";

const CATEGORY_KEY = ["admin", "categories"] as const;
const ITEM_KEY = ["admin", "menu-items"] as const;
const PUBLIC_MENU_KEY = ["menu"] as const;

/** Danh sách danh mục cho màn hình quản trị (STT 74). */
export function useAdminCategories() {
  return useQuery<CategoryResponse[], Error>({
    queryKey: [...CATEGORY_KEY],
    queryFn: () => adminMenuApi.listCategories(),
  });
}

/** Danh sách món ăn cho màn hình quản trị — có phân trang và sắp xếp (STT 75). */
export function useAdminMenuItems(params?: MenuItemFilterParams) {
  return useQuery<PageResponse<MenuItemResponse>, Error>({
    queryKey: [...ITEM_KEY, params ?? {}],
    queryFn: () => adminMenuApi.listItems(params),
    placeholderData: keepPreviousData,
  });
}

export function useCreateCategory() {
  const queryClient = useQueryClient();
  return useMutation<CategoryResponse, Error, CategoryRequest>({
    mutationFn: (payload: CategoryRequest) => adminMenuApi.createCategory(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...CATEGORY_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...ITEM_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...PUBLIC_MENU_KEY] });
      toast.success("Đã thêm danh mục mới.");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể thêm danh mục. Vui lòng thử lại.");
    },
  });
}

export function useUpdateCategory() {
  const queryClient = useQueryClient();
  return useMutation<CategoryResponse, Error, { id: string; payload: CategoryRequest }>({
    mutationFn: ({ id, payload }: { id: string; payload: CategoryRequest }) =>
      adminMenuApi.updateCategory(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...CATEGORY_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...ITEM_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...PUBLIC_MENU_KEY] });
      toast.success("Đã cập nhật danh mục.");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể cập nhật danh mục. Vui lòng thử lại.");
    },
  });
}

export function useDeleteCategory() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, string>({
    mutationFn: (id: string) => adminMenuApi.removeCategory(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...CATEGORY_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...ITEM_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...PUBLIC_MENU_KEY] });
      toast.success("Đã xóa danh mục.");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể xóa danh mục. Vui lòng thử lại.");
    },
  });
}

export function useCreateItem() {
  const queryClient = useQueryClient();
  return useMutation<MenuItemResponse, Error, MenuItemRequest>({
    mutationFn: (payload: MenuItemRequest) => adminMenuApi.createItem(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...ITEM_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...CATEGORY_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...PUBLIC_MENU_KEY] });
      toast.success("Đã thêm món ăn mới.");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể thêm món ăn. Vui lòng thử lại.");
    },
  });
}

export function useUpdateItem() {
  const queryClient = useQueryClient();
  return useMutation<MenuItemResponse, Error, { id: string; payload: MenuItemRequest }>({
    mutationFn: ({ id, payload }: { id: string; payload: MenuItemRequest }) =>
      adminMenuApi.updateItem(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...ITEM_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...CATEGORY_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...PUBLIC_MENU_KEY] });
      toast.success("Đã cập nhật món ăn.");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể cập nhật món ăn. Vui lòng thử lại.");
    },
  });
}

export function useDeleteItem() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, string>({
    mutationFn: (id: string) => adminMenuApi.removeItem(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...ITEM_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...CATEGORY_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...PUBLIC_MENU_KEY] });
      toast.success("Đã xóa món ăn.");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể xóa món ăn. Vui lòng thử lại.");
    },
  });
}

/** Bật/tắt tình trạng còn món (STT 76 / BR-50). */
export function useSetAvailability() {
  const queryClient = useQueryClient();
  return useMutation<MenuItemResponse, Error, { id: string; isAvailable: boolean }>({
    mutationFn: ({ id, isAvailable }: { id: string; isAvailable: boolean }) =>
      adminMenuApi.setAvailability(id, isAvailable),
    onSuccess: (data: MenuItemResponse) => {
      void queryClient.invalidateQueries({ queryKey: [...ITEM_KEY] });
      void queryClient.invalidateQueries({ queryKey: [...PUBLIC_MENU_KEY] });
      toast.success(`${data.itemName}: ${data.isAvailable ? "Đã còn món" : "Đã hết món"}`);
    },
    onError: (error: Error) => {
      toast.error(error.message || "Không thể cập nhật tình trạng món ăn. Vui lòng thử lại.");
    },
  });
}
