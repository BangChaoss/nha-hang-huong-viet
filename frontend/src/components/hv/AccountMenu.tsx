import { Link, useNavigate } from "@tanstack/react-router";
import { LayoutGrid, LogIn, LogOut, UserRound } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { authApi, ROLE_LABEL } from "@/lib/account-api";
import { useSession } from "@/lib/auth-session";
import { getHomeForRole } from "@/lib/role-routing";

export function initials(name: string) {
  return name.trim().slice(0, 2).toUpperCase() || "HV";
}

/** Avatar dropdown for headers: profile, staff management (admin/manager) and logout. */
export function AccountMenu() {
  const { session, ready } = useSession();
  const navigate = useNavigate();

  if (!ready) return <div className="h-9 w-9" />;
  if (!session) {
    return (
      <Link
        to="/login"
        className="inline-flex items-center gap-2 rounded-full border bg-card px-4 py-2 text-sm text-muted-foreground transition-colors hover:text-foreground"
      >
        <LogIn className="h-4 w-4" />
        Đăng nhập
      </Link>
    );
  }

  const workspaceLabel =
    session.role === "ADMIN"
      ? "Trung tâm quản trị IT"
      : session.role === "MANAGER"
      ? "Bảng điều khiển Quản lý"
      : session.role === "STAFF"
      ? "Khu vực Bán hàng & Thu ngân"
      : session.role === "CHEF"
      ? "Màn hình bếp"
      : null;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className="flex items-center gap-2 rounded-full border bg-card p-1 text-sm sm:pr-3 transition-colors hover:border-primary/40">
        <span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary text-xs font-semibold text-primary-foreground">
          {initials(session.username)}
        </span>
        <span className="hidden max-w-28 truncate sm:inline lg:max-w-40">{session.username}</span>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>
          <p className="truncate font-medium">{session.username}</p>
          <p className="truncate text-xs font-normal text-muted-foreground">
            {ROLE_LABEL[session.role]} · {session.email}
          </p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {workspaceLabel && (
          <DropdownMenuItem asChild>
            <Link to={getHomeForRole(session.role)}>
              <LayoutGrid className="mr-2 h-4 w-4" />
              {workspaceLabel}
            </Link>
          </DropdownMenuItem>
        )}
        <DropdownMenuItem asChild>
          <Link to="/account">
            <UserRound className="mr-2 h-4 w-4" />
            Hồ sơ của tôi
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          className="text-destructive focus:text-destructive"
          onSelect={async () => {
            await authApi.logout();
            void navigate({ to: "/login" });
          }}
        >
          <LogOut className="mr-2 h-4 w-4" />
          Đăng xuất
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
