import { useEffect, type ReactNode } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowLeft, Loader2, ShieldAlert, UtensilsCrossed } from "lucide-react";
import { useSession } from "@/lib/auth-session";
import { getHomeForRole, ROLE_NAMES, type AppRole } from "@/lib/role-routing";
import { AccountMenu } from "@/components/hv/AccountMenu";
import { Button } from "@/components/ui/button";

type Props = {
  allowedRoles: AppRole[];
  pageTitle?: string;
  children: ReactNode;
};

/**
 * Route authorization guard.
 * - Chưa đăng nhập -> chuyển hướng về /login.
 * - ADMIN là IT có mọi quyền -> luôn được phép truy cập tất cả màn hình (manager, staff, chef, admin).
 * - MANAGER là quản lý -> chỉ được phép vào màn hình cho phép MANAGER (ví dụ /manager, /users).
 *   Nếu cố vào /staff hoặc /chef hoặc /admin -> hiển thị màn hình Không có quyền truy cập.
 * - Các vai trò khác (STAFF, CHEF, CUSTOMER) -> chỉ được vào màn hình tương ứng của họ.
 */
export function RoleGuard({ allowedRoles, pageTitle, children }: Props) {
  const { session, ready } = useSession();
  const navigate = useNavigate();

  useEffect(() => {
    if (ready && !session) {
      void navigate({ to: "/login", replace: true });
    }
  }, [ready, session, navigate]);

  if (!ready || !session) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  // ADMIN là IT có mọi quyền, các role khác phải có trong allowedRoles
  const isAllowed = session.role === "ADMIN" || allowedRoles.includes(session.role);

  if (!isAllowed) {
    const userRoleName = ROLE_NAMES[session.role] ?? session.role;
    const allowedNames = allowedRoles.map((r) => ROLE_NAMES[r] ?? r).join(", ");
    const home = getHomeForRole(session.role);

    return (
      <div className="min-h-screen bg-background">
        <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
          <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 sm:px-6">
            <Link to={home} className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground">
                <UtensilsCrossed className="h-5 w-5" />
              </div>
              <div className="leading-tight">
                <p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p>
                <p className="text-xs uppercase tracking-[0.25em] text-muted-foreground">Ẩm Thực Cố Đô</p>
              </div>
            </Link>
            <AccountMenu />
          </div>
        </header>

        <main className="mx-auto flex max-w-xl flex-col items-center px-4 py-16 text-center">
          <div className="rounded-2xl border bg-card p-8 shadow-sm sm:p-10">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-destructive/10 text-destructive">
              <ShieldAlert className="h-8 w-8" />
            </div>
            <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">Không có quyền truy cập</h1>
            <p className="mt-3 text-sm text-muted-foreground">
              Tài khoản <span className="font-medium text-foreground">{session.username}</span> của bạn có vai trò{" "}
              <span className="font-semibold text-primary">{userRoleName}</span>.
            </p>
            {pageTitle && (
              <p className="mt-1 text-sm text-muted-foreground">
                Trang <span className="font-medium text-foreground">{pageTitle}</span> chỉ dành cho vai trò:{" "}
                <span className="font-semibold text-foreground">{allowedNames}</span>.
              </p>
            )}
            <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
              <Button asChild className="bg-primary hover:bg-primary-hover">
                <Link to={home}>
                  <ArrowLeft className="mr-2 h-4 w-4" />
                  Về trang làm việc của bạn
                </Link>
              </Button>
              <Button asChild variant="outline">
                <Link to="/">Trang khách hàng</Link>
              </Button>
            </div>
          </div>
        </main>
      </div>
    );
  }

  return <>{children}</>;
}
