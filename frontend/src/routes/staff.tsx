import { createFileRoute, Link } from "@tanstack/react-router";
import { RestaurantProvider } from "@/lib/restaurant-store";
import { StaffPOS } from "@/components/hv/StaffPOS";
import { StaffBookingDashboard } from "@/features/reservation/components/staff-booking-dashboard";
import { UtensilsCrossed, CalendarCheck, Store } from "lucide-react";
import { AccountMenu } from "@/components/hv/AccountMenu";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

export const Route = createFileRoute("/staff")({ component: Page });

function Page() {
  return (
    <RestaurantProvider>
      <div className="min-h-screen bg-background">
        <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
          <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 sm:px-6">
            <Link to="/" className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground">
                <UtensilsCrossed className="h-5 w-5" />
              </div>
              <div>
                <p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p>
                <p className="text-xs uppercase tracking-widest text-muted-foreground">
                  Nhân Viên & Thu Ngân
                </p>
              </div>
            </Link>
            <div className="flex items-center gap-4">
              <Link
                to="/"
                className="hidden text-sm text-muted-foreground hover:text-foreground sm:inline"
              >
                Trang khách hàng
              </Link>
              <AccountMenu />
            </div>
          </div>
        </header>
        <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 sm:py-10">
          <Tabs defaultValue="pos" className="space-y-6">
            <TabsList className="grid w-full max-w-md grid-cols-2">
              <TabsTrigger value="pos" className="flex items-center gap-2">
                <Store className="h-4 w-4" />
                <span>Thu Ngân / POS</span>
              </TabsTrigger>
              <TabsTrigger value="bookings" className="flex items-center gap-2">
                <CalendarCheck className="h-4 w-4" />
                <span>Đặt Bàn</span>
              </TabsTrigger>
            </TabsList>

            <TabsContent value="pos" className="space-y-4 focus-visible:outline-none">
              <StaffPOS />
            </TabsContent>

            <TabsContent value="bookings" className="space-y-4 focus-visible:outline-none">
              <StaffBookingDashboard />
            </TabsContent>
          </Tabs>
        </main>
      </div>
    </RestaurantProvider>
  );
}
