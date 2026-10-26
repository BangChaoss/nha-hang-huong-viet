import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { UtensilsCrossed } from "lucide-react";
import { RestaurantProvider } from "@/lib/restaurant-store";
import { ManagerDashboard, type ManagerTab } from "@/components/hv/ManagerDashboard";
import { AccountMenu } from "@/components/hv/AccountMenu";
import { RoleGuard } from "@/components/hv/RoleGuard";
import { useSession } from "@/lib/auth-session";

interface ManagerSearchParams {
  tab?: ManagerTab;
}

export const Route = createFileRoute("/manager")({
  validateSearch: (search: Record<string, unknown>): ManagerSearchParams => {
    const tab = search.tab as string | undefined;
    if (tab === "permissions" || tab === "vouchers" || tab === "overview") {
      return { tab };
    }
    return { tab: "overview" };
  },
  head: () => ({ meta: [{ title: "Bảng điều khiển Quản lý — Hương Việt" }] }),
  component: Page,
});

function Page() {
  const { session } = useSession();
  const isAdmin = session?.role === "ADMIN";
  const search = Route.useSearch();
  const navigate = useNavigate();

  const activeTab: ManagerTab = search.tab ?? "overview";
  const handleTabChange = (tab: ManagerTab) => {
    void navigate({
      to: "/manager",
      search: { tab },
    });
  };

  return (
    <RoleGuard allowedRoles={["ADMIN", "MANAGER"]} pageTitle="Bảng điều khiển Quản lý">
      <RestaurantProvider>
        <div className="min-h-screen">
          <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
            <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 sm:px-6">
              <Link to={isAdmin ? "/admin" : "/manager"} className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground">
                  <UtensilsCrossed className="h-5 w-5" />
                </div>
                <div>
                  <p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p>
                  <p className="text-xs uppercase tracking-widest text-muted-foreground">Quản Lý</p>
                </div>
              </Link>
              <div className="flex items-center gap-4">
                <Link to="/" className="hidden text-sm text-muted-foreground hover:text-foreground sm:inline">
                  Trang khách hàng
                </Link>
                <AccountMenu />
              </div>
            </div>
          </header>
          <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 sm:py-10">
            <ManagerDashboard activeTab={activeTab} onTabChange={handleTabChange} />
          </main>
        </div>
      </RestaurantProvider>
    </RoleGuard>
  );
}

