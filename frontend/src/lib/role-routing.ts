/** Route destination to use after a successful authentication response. */
export type AppRole = "CUSTOMER" | "STAFF" | "CHEF" | "MANAGER";

export function getHomeForRole(role: AppRole): "/" | "/staff" | "/chef" | "/admin" {
  switch (role) {
    case "CUSTOMER": return "/";
    case "STAFF": return "/staff";
    case "CHEF": return "/chef";
    case "MANAGER": return "/admin";
  }
}
