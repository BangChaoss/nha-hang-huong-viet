import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowRight, ChefHat, ClipboardList, ShieldCheck, UtensilsCrossed, Users } from "lucide-react";
import { RestaurantProvider } from "@/lib/restaurant-store";

export const Route = createFileRoute("/admin")({ component: AdminPage });

const destinations = [
  { to: "/manager" as const, title: "Quản lý nhà hàng", description: "Thực đơn, đặt bàn và hoạt động quản lý.", icon: ClipboardList },
  { to: "/staff" as const, title: "Nhân viên & Thu ngân", description: "Bán hàng và xử lý đơn tại quầy.", icon: Users },
  { to: "/chef" as const, title: "Màn hình bếp", description: "Theo dõi và xử lý các món cần chế biến.", icon: ChefHat },
];

function AdminPage() {
  return <RestaurantProvider><div className="min-h-screen">
    <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-6 py-4">
        <Link to="/" className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground"><UtensilsCrossed className="h-5 w-5" /></div>
          <div><p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p><p className="text-xs uppercase tracking-widest text-muted-foreground">Quản trị IT</p></div>
        </Link>
        <Link to="/" className="text-sm text-muted-foreground hover:text-foreground">Trang khách hàng</Link>
      </div>
    </header>
    <main className="mx-auto max-w-5xl px-6 py-10">
      <div className="mb-8 flex items-center gap-4"><div className="rounded-xl bg-primary/10 p-3 text-primary"><ShieldCheck className="h-7 w-7" /></div><div><h1 className="text-3xl font-semibold">Trung tâm quản trị IT</h1><p className="mt-1 text-muted-foreground">Truy cập các giao diện vận hành của hệ thống.</p></div></div>
      <div className="grid gap-4 md:grid-cols-3">{destinations.map(({to,title,description,icon:Icon}) =>
        <Link key={to} to={to} className="group rounded-xl border bg-card p-6 transition-colors hover:border-primary/50 hover:bg-accent/40">
          <Icon className="mb-4 h-8 w-8 text-primary" /><h2 className="text-lg font-semibold">{title}</h2><p className="mt-2 min-h-12 text-sm text-muted-foreground">{description}</p><span className="mt-5 inline-flex items-center gap-2 text-sm font-medium text-primary">Mở giao diện <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" /></span>
        </Link>)}</div>
    </main>
  </div></RestaurantProvider>;
}
