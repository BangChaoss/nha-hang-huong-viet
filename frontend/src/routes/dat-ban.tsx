import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, UtensilsCrossed } from "lucide-react";
import { BookForm } from "@/features/reservation/components/book-form";
import { AccountMenu } from "@/components/hv/AccountMenu";
import { RestaurantProvider } from "@/lib/restaurant-store";

export const Route = createFileRoute("/dat-ban")({
  head: () => ({
    meta: [
      { title: "Đặt Bàn Trực Tuyến — Hương Việt Ẩm Thực Cố Đô" },
      {
        name: "description",
        content:
          "Đặt bàn trước tại Nhà hàng Hương Việt. Chọn khu vực sân vườn, phòng VIP, theo dõi sức chứa trực tiếp.",
      },
    ],
  }),
  component: DatBanPage,
});

function DatBanPage() {
  return (
    <RestaurantProvider>
      <div className="min-h-screen bg-background">
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
          <div className="mb-6">
            <Link
              to="/"
              className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Quay lại trang chủ</span>
            </Link>
          </div>

          <BookForm />
        </main>
      </div>
    </RestaurantProvider>
  );
}
