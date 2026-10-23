import { getSession, setSession, updateSession, type AuthSession } from "@/lib/auth-session";
import type { AppRole } from "@/lib/role-routing";

export type UserStatus = "ACTIVE" | "INACTIVE" | "LOCKED";
export type StaffRole = Exclude<AppRole, "CUSTOMER">;

/** Backend `UserProfileResponse` / `StaffResponse` share the same shape. */
export type UserAccount = {
  id: string;
  username: string;
  email: string;
  phoneNumber: string;
  role: AppRole;
  status: UserStatus;
  createdAt: string | null;
  updatedAt: string | null;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};

export type UpdateProfilePayload = { username: string; email: string; phoneNumber: string };
export type ChangePasswordPayload = {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
};
export type CreateStaffPayload = UpdateProfilePayload & { role: StaffRole; password?: string };
export type UpdateStaffPayload = UpdateProfilePayload & { role: StaffRole };
export type StaffFilter = {
  keyword?: string;
  role?: AppRole | "";
  status?: UserStatus | "";
  page?: number;
  size?: number;
};

/** Error carrying per-field messages from backend `ErrorResponse.errors`. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

const API_BASE = (import.meta.env["VITE_API_BASE_URL"] ?? "http://localhost:8080").replace(/\/$/, "");

async function toApiError(response: Response): Promise<ApiError> {
  let message =
    response.status === 403
      ? "Bạn không có quyền thực hiện thao tác này."
      : `Yêu cầu thất bại (${response.status})`;
  let fieldErrors: Record<string, string> = {};
  try {
    const body = await response.json();
    message = body.message ?? body.detail ?? message;
    fieldErrors = body.errors ?? {};
  } catch {
    /* response may have no JSON body */
  }
  if (response.status === 401) message = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  return new ApiError(message, response.status, fieldErrors);
}

/**
 * Xin cặp token mới khi access token hết hạn (refresh token rotation):
 * 1. Gửi refresh token hiện tại lên `/api/auth/refresh`.
 * 2. Backend thu hồi refresh token cũ và trả về cặp token MỚI — mỗi refresh token chỉ dùng được một lần.
 * 3. Lưu cả hai token mới vào phiên `hv.session` để các request sau dùng.
 * Trả về false nếu không có refresh token, token đã bị thu hồi (vd. sau khi đổi mật khẩu) hoặc lỗi mạng.
 */
async function tryRefresh(session: AuthSession): Promise<boolean> {
  if (!session.refreshToken) return false;
  try {
    const res = await fetch(`${API_BASE}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    });
    if (!res.ok) return false;
    const { token, refreshToken } = (await res.json()) as { token: string; refreshToken: string };
    updateSession({ token, refreshToken });
    return true;
  } catch {
    return false;
  }
}

/**
 * Gọi API cần đăng nhập, tự gắn header `Authorization: Bearer <access token>`.
 * Khi nhận 401 (access token hết hạn/không hợp lệ): thử refresh một lần rồi gọi lại request;
 * `retried` chặn vòng lặp vô hạn. Nếu refresh thất bại thì xoá phiên → người dùng phải đăng nhập lại.
 */
async function authRequest<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  const session = getSession();
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(session ? { Authorization: `Bearer ${session.token}` } : {}),
      ...(init.headers ?? {}),
    },
  });
  if (response.status === 401 && session && !retried) {
    if (await tryRefresh(session)) return authRequest<T>(path, init, true);
    setSession(null);
  }
  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const authApi = {
  login: async (usernameOrEmail: string, password: string) => {
    const res = await fetch(`${API_BASE}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ usernameOrEmail, password }),
    });
    if (!res.ok) throw await toApiError(res);
    const session = (await res.json()) as AuthSession;
    setSession(session);
    return session;
  },
  logout: async () => {
    try {
      await authRequest<void>("/api/auth/logout", { method: "POST" }, true);
    } catch {
      /* token may already be invalid; clear locally regardless */
    }
    setSession(null);
  },
};

export const profileApi = {
  get: () => authRequest<UserAccount>("/api/account/profile"),
  update: async (payload: UpdateProfilePayload) => {
    const updated = await authRequest<UserAccount>("/api/account/profile", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
    updateSession({ username: updated.username, email: updated.email });
    return updated;
  },
  // Sau khi đổi mật khẩu, backend thu hồi toàn bộ refresh token của user (kể cả thiết bị này).
  // Access token hiện tại vẫn dùng được đến khi hết hạn; lần refresh kế tiếp sẽ thất bại
  // và authRequest xoá phiên → các thiết bị đều phải đăng nhập lại bằng mật khẩu mới.
  changePassword: (payload: ChangePasswordPayload) =>
    authRequest<{ message: string }>("/api/account/change-password", {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
};

export const staffApi = {
  list: ({ keyword, role, status, page = 0, size = 10 }: StaffFilter = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (keyword?.trim()) params.set("keyword", keyword.trim());
    if (role) params.set("role", role);
    if (status) params.set("status", status);
    return authRequest<PageResponse<UserAccount>>(`/api/admin/staff?${params}`);
  },
  create: (payload: CreateStaffPayload) =>
    authRequest<UserAccount>("/api/admin/staff", { method: "POST", body: JSON.stringify(payload) }),
  update: (id: string, payload: UpdateStaffPayload) =>
    authRequest<UserAccount>(`/api/admin/staff/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  setStatus: (id: string, status: UserStatus) =>
    authRequest<UserAccount>(`/api/admin/staff/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }),
  remove: (id: string) => authRequest<void>(`/api/admin/staff/${id}`, { method: "DELETE" }),
};

export const ROLE_LABEL: Record<AppRole, string> = {
  CUSTOMER: "Khách hàng",
  STAFF: "Nhân viên",
  CHEF: "Đầu bếp",
  MANAGER: "Quản lý",
  ADMIN: "Quản trị viên",
};

export const STATUS_LABEL: Record<UserStatus, string> = {
  ACTIVE: "Hoạt động",
  INACTIVE: "Ngưng",
  LOCKED: "Đã khoá",
};

/** Roles an actor may assign — mirrors StaffManagementService RBAC. */
export function assignableRoles(actor: AppRole | undefined): StaffRole[] {
  if (actor === "ADMIN") return ["STAFF", "CHEF", "MANAGER", "ADMIN"];
  if (actor === "MANAGER") return ["STAFF", "CHEF"];
  return [];
}

/** Whether actor may edit/lock/delete target — mirrors validateModificationPermissions. */
export function canModify(
  actor: { id: string; role: AppRole } | null,
  target: UserAccount,
): boolean {
  if (!actor || target.role === "ADMIN" || target.id === actor.id) return false;
  if (target.role === "CUSTOMER") return actor.role === "ADMIN";
  if (actor.role === "MANAGER" && target.role === "MANAGER") return false;
  return actor.role === "ADMIN" || actor.role === "MANAGER";
}

export const formatDate = (value: string | null) =>
  value
    ? new Date(value).toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
      })
    : "—";
