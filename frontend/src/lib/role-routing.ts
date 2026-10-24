/** Route destination after successful authentication. */
export type AppRole = "CUSTOMER" | "STAFF" | "CHEF" | "MANAGER" | "ADMIN";

export type RoleHome = "/" | "/staff" | "/chef" | "/manager" | "/admin";

export function getHomeForRole(role: AppRole): RoleHome {
  switch (role) {
    case "CUSTOMER": return "/";
    case "STAFF": return "/staff";
    case "CHEF": return "/chef";
    case "MANAGER": return "/manager";
    case "ADMIN": return "/admin";
  }
}

export const ROLE_NAMES: Record<AppRole, string> = {
  ADMIN: "Quản trị IT",
  MANAGER: "Quản lý",
  STAFF: "Nhân viên & Thu ngân",
  CHEF: "Màn hình bếp",
  CUSTOMER: "Khách hàng",
};

/** Các vai trò được phép truy cập từng màn hình */
export const ROUTE_PERMISSIONS: Record<string, AppRole[]> = {
  "/admin": ["ADMIN"],
  "/manager": ["ADMIN", "MANAGER"],
  "/staff": ["ADMIN", "STAFF"],
  "/chef": ["ADMIN", "CHEF"],
  "/users": ["ADMIN", "MANAGER"],
};

/**
 * Kiểm tra xem một vai trò có quyền truy cập vào đường dẫn hay không.
 * ADMIN là IT có mọi quyền nên luôn trả về true.
 * MANAGER chỉ vào được /manager (và /users), KHÔNG vào được /staff, /chef, /admin.
 */
export function canAccessRoute(role: AppRole | undefined, path: string): boolean {
  if (!role) return false;
  if (role === "ADMIN") return true; // ADMIN là IT có mọi quyền
  const allowed = ROUTE_PERMISSIONS[path];
  if (!allowed) return true;
  return allowed.includes(role);
}
