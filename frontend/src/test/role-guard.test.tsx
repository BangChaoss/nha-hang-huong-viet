import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { canAccessRoute, getHomeForRole } from "@/lib/role-routing";
import { RoleGuard } from "@/components/hv/RoleGuard";

// Mock auth session module
const mockSession = vi.fn();
vi.mock("@/lib/auth-session", () => ({
  useSession: () => mockSession(),
  isUserManager: (role: string | undefined) => role === "ADMIN" || role === "MANAGER",
}));

// Mock @tanstack/react-router
vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
  useNavigate: () => vi.fn(),
}));

describe("Role Routing & Access Control", () => {
  describe("getHomeForRole", () => {
    it("redirects ADMIN to /admin (Trung tâm quản trị IT)", () => {
      expect(getHomeForRole("ADMIN")).toBe("/admin");
    });

    it("redirects MANAGER to /manager (Bảng điều khiển Quản lý)", () => {
      expect(getHomeForRole("MANAGER")).toBe("/manager");
    });

    it("redirects STAFF to /staff", () => {
      expect(getHomeForRole("STAFF")).toBe("/staff");
    });

    it("redirects CHEF to /chef", () => {
      expect(getHomeForRole("CHEF")).toBe("/chef");
    });

    it("redirects CUSTOMER to /", () => {
      expect(getHomeForRole("CUSTOMER")).toBe("/");
    });
  });

  describe("canAccessRoute", () => {
    it("ADMIN có mọi quyền: vào được /admin, /manager, /staff, /chef, /users", () => {
      expect(canAccessRoute("ADMIN", "/admin")).toBe(true);
      expect(canAccessRoute("ADMIN", "/manager")).toBe(true);
      expect(canAccessRoute("ADMIN", "/staff")).toBe(true);
      expect(canAccessRoute("ADMIN", "/chef")).toBe(true);
      expect(canAccessRoute("ADMIN", "/users")).toBe(true);
    });

    it("MANAGER chỉ vào được /manager và /users, KHÔNG vào được /staff, /chef, /admin", () => {
      expect(canAccessRoute("MANAGER", "/manager")).toBe(true);
      expect(canAccessRoute("MANAGER", "/users")).toBe(true);
      expect(canAccessRoute("MANAGER", "/staff")).toBe(false);
      expect(canAccessRoute("MANAGER", "/chef")).toBe(false);
      expect(canAccessRoute("MANAGER", "/admin")).toBe(false);
    });

    it("STAFF chỉ vào được /staff, không vào được /manager, /admin, /chef, /users", () => {
      expect(canAccessRoute("STAFF", "/staff")).toBe(true);
      expect(canAccessRoute("STAFF", "/manager")).toBe(false);
      expect(canAccessRoute("STAFF", "/admin")).toBe(false);
      expect(canAccessRoute("STAFF", "/chef")).toBe(false);
      expect(canAccessRoute("STAFF", "/users")).toBe(false);
    });

    it("CHEF chỉ vào được /chef, không vào được /manager, /admin, /staff, /users", () => {
      expect(canAccessRoute("CHEF", "/chef")).toBe(true);
      expect(canAccessRoute("CHEF", "/manager")).toBe(false);
      expect(canAccessRoute("CHEF", "/admin")).toBe(false);
      expect(canAccessRoute("CHEF", "/staff")).toBe(false);
      expect(canAccessRoute("CHEF", "/users")).toBe(false);
    });

    it("CUSTOMER không vào được bất kỳ màn quản trị / nội bộ nào", () => {
      expect(canAccessRoute("CUSTOMER", "/admin")).toBe(false);
      expect(canAccessRoute("CUSTOMER", "/manager")).toBe(false);
      expect(canAccessRoute("CUSTOMER", "/staff")).toBe(false);
      expect(canAccessRoute("CUSTOMER", "/chef")).toBe(false);
      expect(canAccessRoute("CUSTOMER", "/users")).toBe(false);
    });
  });

  describe("RoleGuard component", () => {
    it("renders children when user role is in allowedRoles", () => {
      mockSession.mockReturnValue({
        ready: true,
        session: { id: "1", username: "manager_user", email: "mgr@test.com", role: "MANAGER" },
      });

      render(
        <RoleGuard allowedRoles={["ADMIN", "MANAGER"]} pageTitle="Bảng điều khiển Quản lý">
          <div data-testid="manager-content">Manager Content</div>
        </RoleGuard>,
      );

      expect(screen.getByTestId("manager-content")).toBeInTheDocument();
    });

    it("allows ADMIN even if not explicitly listed in allowedRoles", () => {
      mockSession.mockReturnValue({
        ready: true,
        session: { id: "2", username: "admin_user", email: "admin@test.com", role: "ADMIN" },
      });

      render(
        <RoleGuard allowedRoles={["STAFF"]} pageTitle="Nhân viên & Thu ngân">
          <div data-testid="staff-content">Staff POS Content</div>
        </RoleGuard>,
      );

      expect(screen.getByTestId("staff-content")).toBeInTheDocument();
    });

    it("blocks MANAGER from accessing /staff with Access Denied message", () => {
      mockSession.mockReturnValue({
        ready: true,
        session: { id: "3", username: "manager_user", email: "mgr@test.com", role: "MANAGER" },
      });

      render(
        <RoleGuard allowedRoles={["ADMIN", "STAFF"]} pageTitle="Nhân viên & Thu ngân">
          <div data-testid="staff-content">Staff Content</div>
        </RoleGuard>,
      );

      expect(screen.queryByTestId("staff-content")).not.toBeInTheDocument();
      expect(screen.getByText("Không có quyền truy cập")).toBeInTheDocument();
      expect(screen.getAllByText(/Nhân viên & Thu ngân/).length).toBeGreaterThan(0);
    });

    it("blocks MANAGER from accessing /admin with Access Denied message", () => {
      mockSession.mockReturnValue({
        ready: true,
        session: { id: "4", username: "manager_user", email: "mgr@test.com", role: "MANAGER" },
      });

      render(
        <RoleGuard allowedRoles={["ADMIN"]} pageTitle="Trung tâm quản trị IT">
          <div data-testid="admin-content">Admin Content</div>
        </RoleGuard>,
      );

      expect(screen.queryByTestId("admin-content")).not.toBeInTheDocument();
      expect(screen.getByText("Không có quyền truy cập")).toBeInTheDocument();
      expect(screen.getByText(/Trung tâm quản trị IT/)).toBeInTheDocument();
    });
  });
});
