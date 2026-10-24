import { createFileRoute, Link } from "@tanstack/react-router";
import { ShieldCheck, UtensilsCrossed } from "lucide-react";
import { RestaurantProvider } from "@/lib/restaurant-store";
import { KitchenKDS } from "@/components/hv/KitchenKDS";
import { AccountMenu } from "@/components/hv/AccountMenu";
import { RoleGuard } from "@/components/hv/RoleGuard";
import { useSession } from "@/lib/auth-session";

export const Route = createFileRoute("/chef")({
  head: () => ({ meta: [{ title: "Màn hình Bếp — Hương Việt" }] }),
  component: Page,
});

function Page() {
  const { session } = useSession();
  const isAdmin = session?.role === "ADMIN";

  return (
    <RoleGuard allowedRoles={["ADMIN", "CHEF"]} pageTitle="Màn hình Bếp">
      <RestaurantProvider>
        <div className="min-h-screen">
          <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
            <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 sm:px-6">
              <Link to={isAdmin ? "/admin" : "/chef"} className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground">
                  <UtensilsCrossed className="h-5 w-5" />
                </div>
                <div>
                  <p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p>
                  <p className="text-xs uppercase tracking-widest text-muted-foreground">Màn Hình Bếp</p>
                </div>
              </Link>
              <div className="flex items-center gap-4">
                {isAdmin && (
                  <Link
                    to="/admin"
                    className="inline-flex items-center gap-1.5 rounded-full border border-primary/30 bg-primary/10 px-3 py-1.5 text-xs font-medium text-primary hover:bg-primary/20 transition-colors"
                  >
                    <ShieldCheck className="h-3.5 w-3.5" />
                    Về Trung tâm IT
                  </Link>
                )}
                <Link to="/" className="hidden text-sm text-muted-foreground hover:text-foreground sm:inline">
                  Trang khách hàng
                </Link>
                <AccountMenu />
              </div>
            </div>
          </header>
          <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 sm:py-10">
            <KitchenKDS />
          </main>
        </div>
      </RestaurantProvider>
    </RoleGuard>
  );
}

