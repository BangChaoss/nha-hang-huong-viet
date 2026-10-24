import { API_BASE } from "@/lib/table-api";
import { getSession } from "@/lib/auth-session";

/** Một món ăn trả về từ backend. */
export type MenuItemResponse = {
  id: string;
  categoryId: string;
  categoryName: string;
  itemName: string;
  description: string | null;
  currentPrice: number;
  imageUrl: string | null;
  isAvailable: boolean;
};

/** Danh mục món ăn (dùng cho màn hình quản trị). */
export type CategoryResponse = {
  id: string;
  categoryName: string;
  displayOrder: number;
  menuItemCount: number;
};

/** Danh mục kèm danh sách món (dùng cho trang khách). */
export type MenuCategoryResponse = {
  id: string;
  categoryName: string;
  displayOrder: number;
  items: MenuItemResponse[];
};

/** Payload của `GET /api/menu`. */
export type MenuOverviewResponse = {
  categories: MenuCategoryResponse[];
};

/** Body khi tạo/cập nhật danh mục. */
export type CategoryRequest = {
  categoryName: string;
  displayOrder: number;
};

/** Body khi tạo/cập nhật món ăn. */
export type MenuItemRequest = {
  categoryId: string;
  itemName: string;
  description: string | null;
  currentPrice: number;
  imageUrl: string | null;
  isAvailable: boolean;
};

/** Tham số lọc, phân trang và sắp xếp danh sách món cho API quản trị. */
export type MenuItemFilterParams = {
  categoryId?: string;
  q?: string;
  available?: boolean;
  /** Trang hiện tại, đánh số từ 0. */
  page?: number;
  /** Số món trên một trang. */
  size?: number;
  /** Tiêu chí sắp xếp: `createdAt` (mặc định), `updatedAt`, `name`, `price`. */
  sort?: MenuItemSortKey;
  /** Hướng sắp xếp: `asc` hoặc `desc`. */
  direction?: SortDirection;
};

/** Tiêu chí sắp xếp danh sách món. */
export type MenuItemSortKey = "createdAt" | "updatedAt" | "name" | "price";

export type SortDirection = "asc" | "desc";

/** Kết quả phân trang trả về từ API. */
export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};

/** Message WebSocket `/topic/menu/availability`. */
export type MenuAvailabilityMessage = {
  menuItemId: string;
  itemName: string;
  isAvailable: boolean;
};

/** Message WebSocket `/topic/menu/refresh`. */
export type MenuRefreshMessage = {
  reason: string;
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getSession()?.token;
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    let message = `Yêu cầu thất bại (${response.status})`;
    try {
      const body = (await response.json()) as { message?: string; detail?: string } | null;
      if (body) {
        message = body.message ?? body.detail ?? message;
      }
    } catch {
      // Fallback khi body không phải JSON
    }
    throw new Error(message);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

/** API công khai cho khách hàng. */
export const menuApi = {
  getOverview: (): Promise<MenuOverviewResponse> => request<MenuOverviewResponse>("/api/menu"),
  getItem: (id: string): Promise<MenuItemResponse> => request<MenuItemResponse>(`/api/menu/${id}`),
  getCategories: (): Promise<CategoryResponse[]> =>
    request<CategoryResponse[]>("/api/menu/categories"),
};

/** API quản trị (Manager/Admin). */
export const adminMenuApi = {
  listCategories: (): Promise<CategoryResponse[]> =>
    request<CategoryResponse[]>("/api/admin/categories"),

  createCategory: (payload: CategoryRequest): Promise<CategoryResponse> =>
    request<CategoryResponse>("/api/admin/categories", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  updateCategory: (id: string, payload: CategoryRequest): Promise<CategoryResponse> =>
    request<CategoryResponse>(`/api/admin/categories/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  removeCategory: (id: string): Promise<void> =>
    request<void>(`/api/admin/categories/${id}`, { method: "DELETE" }),

  listItems: (params?: MenuItemFilterParams): Promise<PageResponse<MenuItemResponse>> => {
    const search = new URLSearchParams();
    if (params?.categoryId) search.append("categoryId", params.categoryId);
    if (params?.q) search.append("q", params.q);
    if (params?.available !== undefined) search.append("available", String(params.available));
    if (params?.page !== undefined) search.append("page", String(params.page));
    if (params?.size !== undefined) search.append("size", String(params.size));
    if (params?.sort) search.append("sort", params.sort);
    if (params?.direction) search.append("direction", params.direction);
    const queryString = search.toString();
    return request<PageResponse<MenuItemResponse>>(
      `/api/admin/menu-items${queryString ? `?${queryString}` : ""}`,
    );
  },

  createItem: (payload: MenuItemRequest): Promise<MenuItemResponse> =>
    request<MenuItemResponse>("/api/admin/menu-items", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  updateItem: (id: string, payload: MenuItemRequest): Promise<MenuItemResponse> =>
    request<MenuItemResponse>(`/api/admin/menu-items/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  setAvailability: (id: string, isAvailable: boolean): Promise<MenuItemResponse> =>
    request<MenuItemResponse>(`/api/admin/menu-items/${id}/availability`, {
      method: "PATCH",
      body: JSON.stringify({ isAvailable }),
    }),

  removeItem: (id: string): Promise<void> =>
    request<void>(`/api/admin/menu-items/${id}`, { method: "DELETE" }),
};
