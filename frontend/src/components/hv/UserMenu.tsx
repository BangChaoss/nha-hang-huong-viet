import { useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { ChevronDown, LayoutGrid, LogIn, LogOut } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAuth } from "@/lib/auth-store";
import { getHomeForRole, type AppRole } from "@/lib/role-routing";

const roleLabel: Record<AppRole, string> = {
  CUSTOMER: "Khách hàng",
  STAFF: "Nhân viên & Thu ngân",
  CHEF: "Bếp",
  MANAGER: "Quản lý",
  ADMIN: "Quản trị IT",
};

/** Khu vực tài khoản trên header: nút Đăng nhập/Đăng ký hoặc menu người dùng có Đăng xuất. */
export function UserMenu() {
  const { user, ready, logout } = useAuth();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);

  // Giữ chỗ để header không bị nhảy layout trong lúc đọc phiên.
  if (!ready) return <div className="h-9 w-24" aria-hidden />;

  if (!user) {
    return (
      <div className="flex items-center gap-2">
        <Button asChild variant="ghost" size="sm" className="hidden sm:inline-flex">
          <Link to="/register">Đăng ký</Link>
        </Button>
        <Button asChild size="sm" className="bg-primary hover:bg-primary-hover">
          <Link to="/login"><LogIn className="h-4 w-4" />Đăng nhập</Link>
        </Button>
      </div>
    );
  }

  async function handleLogout() {
    setBusy(true);
    await logout();
    setBusy(false);
    toast.success("Bạn đã đăng xuất");
    navigate({ to: "/login" });
  }

  const initials = user.username.slice(0, 2).toUpperCase();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button className="flex items-center gap-2 rounded-full border bg-card py-1 pl-1 pr-2 text-sm transition hover:border-moss/40 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring sm:pr-3">
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-moss text-xs font-semibold text-moss-foreground">{initials}</span>
          <span className="hidden max-w-32 truncate font-medium sm:inline">{user.username}</span>
          <ChevronDown className="h-4 w-4 text-muted-foreground" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel className="font-normal">
          <p className="truncate font-medium">{user.username}</p>
          <p className="truncate text-xs text-muted-foreground">{user.email}</p>
          <span className="status-confirmed mt-2 inline-block rounded-full px-2 py-0.5 text-[11px] font-medium">{roleLabel[user.role]}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link to={getHomeForRole(user.role)}><LayoutGrid className="h-4 w-4" />Trang làm việc</Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled={busy} onSelect={(e) => { e.preventDefault(); void handleLogout(); }}
          className="text-destructive focus:bg-destructive/10 focus:text-destructive">
          <LogOut className="h-4 w-4" />Đăng xuất
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
