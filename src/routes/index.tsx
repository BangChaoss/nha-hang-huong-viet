import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { UtensilsCrossed, ConciergeBell, ChefHat, LayoutDashboard, BookOpen } from "lucide-react";
import { RestaurantProvider } from "@/lib/restaurant-store";
import { StaffPOS } from "@/components/hv/StaffPOS";
import { KitchenKDS } from "@/components/hv/KitchenKDS";
import { CustomerPortal } from "@/components/hv/CustomerPortal";
import { ManagerDashboard } from "@/components/hv/ManagerDashboard";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Hương Việt — Hệ Thống Quản Lý Nhà Hàng" },
      { name: "description", content: "Quản lý bàn, gọi món, bếp, đặt chỗ và doanh thu cho nhà hàng Hương Việt - Ẩm Thực Cố Đô." },
      { property: "og:title", content: "Hương Việt — Hệ Thống Quản Lý Nhà Hàng" },
      { property: "og:description", content: "Sơ đồ bàn, POS, màn hình bếp, cổng khách hàng và bảng điều khiển quản lý." },
    ],
  }),
  component: Index,
});

type Role = "customer" | "staff" | "kds" | "manager";
const roles: { id: Role; label: string; icon: typeof BookOpen }[] = [
  { id: "customer", label: "Khách Hàng", icon: BookOpen },
  { id: "staff", label: "Nhân Viên & Thu Ngân", icon: ConciergeBell },
  { id: "kds", label: "Màn Hình Bếp", icon: ChefHat },
  { id: "manager", label: "Quản Lý", icon: LayoutDashboard },
];

function Index() {
  const [role, setRole] = useState<Role>("staff");
  return (
    <RestaurantProvider>
      <div className="min-h-screen">
        <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
          <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-6 py-4">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground"><UtensilsCrossed className="h-5 w-5" /></div>
              <div className="leading-tight">
                <p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p>
                <p className="text-xs uppercase tracking-[0.25em] text-muted-foreground">Ẩm Thực Cố Đô</p>
              </div>
            </div>
            <nav aria-label="Chuyển vai trò" className="flex flex-wrap gap-1 rounded-full border bg-card p-1">
              {roles.map((r) => (
                <button key={r.id} onClick={() => setRole(r.id)}
                  className={cn("flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm transition", role === r.id ? "bg-moss text-moss-foreground" : "text-muted-foreground hover:text-foreground")}>
                  <r.icon className="h-4 w-4" /><span className="hidden sm:inline">{r.label}</span>
                </button>
              ))}
            </nav>
          </div>
        </header>
        <main className="mx-auto max-w-7xl px-6 py-10">
          {role === "customer" && <CustomerPortal />}
          {role === "staff" && <StaffPOS />}
          {role === "kds" && <KitchenKDS />}
          {role === "manager" && <ManagerDashboard />}
        </main>
      </div>
    </RestaurantProvider>
  );
}
