import { createFileRoute, Link } from "@tanstack/react-router";
import { UtensilsCrossed } from "lucide-react";
import { RestaurantProvider } from "@/lib/restaurant-store";
import { CustomerPortal } from "@/components/hv/CustomerPortal";
import { AccountMenu } from "@/components/hv/AccountMenu";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Hương Việt — Nhà hàng Ẩm Thực Cố Đô" },
      { name: "description", content: "Khám phá thực đơn và đặt bàn tại nhà hàng Hương Việt." },
    ],
  }),
  component: HomePage,
});

function HomePage() {
  return (
    <RestaurantProvider>
      <div className="min-h-screen">
        <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
          <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 sm:px-6">
            <Link to="/" className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground">
                <UtensilsCrossed className="h-5 w-5" />
              </div>
              <div className="leading-tight">
                <p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p>
                <p className="text-xs uppercase tracking-[0.25em] text-muted-foreground">
                  Ẩm Thực Cố Đô
                </p>
              </div>
            </Link>
            <AccountMenu />
          </div>
        </header>
        <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 sm:py-10">
          <CustomerPortal />
        </main>
      </div>
    </RestaurantProvider>
  );
}
