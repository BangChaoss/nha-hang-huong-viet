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
