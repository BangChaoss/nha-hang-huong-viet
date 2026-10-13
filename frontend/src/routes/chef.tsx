import { createFileRoute, Link } from "@tanstack/react-router";
import { RestaurantProvider } from "@/lib/restaurant-store";
import { KitchenKDS } from "@/components/hv/KitchenKDS";
import { UtensilsCrossed } from "lucide-react";

export const Route = createFileRoute("/chef")({ component: Page });

function Page() {
  return <RestaurantProvider><div className="min-h-screen">
    <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-6 py-4">
        <Link to="/" className="flex items-center gap-3"><div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground"><UtensilsCrossed className="h-5 w-5" /></div><div><p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p><p className="text-xs uppercase tracking-widest text-muted-foreground">Màn Hình Bếp</p></div></Link>
        <Link to="/" className="text-sm text-muted-foreground hover:text-foreground">Trang khách hàng</Link>
      </div>
    </header>
    <main className="mx-auto max-w-7xl px-6 py-10"><KitchenKDS /></main>
  </div></RestaurantProvider>;
}
