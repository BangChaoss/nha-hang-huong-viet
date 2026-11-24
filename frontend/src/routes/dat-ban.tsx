import * as React from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, UtensilsCrossed, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { BookForm } from "@/features/reservation/components/book-form";
import { BookingConfirmation } from "@/features/reservation/components/booking-confirmation";
import { reservationApi } from "@/features/reservation/api/reservation-api";
import type { ReservationResponse } from "@/features/reservation/types/reservation.types";
import { AccountMenu } from "@/components/hv/AccountMenu";
import { RestaurantProvider } from "@/lib/restaurant-store";

interface DatBanSearch {
  reservationId?: string;
  depositStatus?: string;
}

export const Route = createFileRoute("/dat-ban")({
  validateSearch: (search: Record<string, unknown>): DatBanSearch => ({
    reservationId: typeof search.reservationId === "string" ? search.reservationId : undefined,
    depositStatus: typeof search.depositStatus === "string" ? search.depositStatus : undefined,
  }),
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
  const search = Route.useSearch();
  const [reservation, setReservation] = React.useState<ReservationResponse | null>(null);
  const [isLoading, setIsLoading] = React.useState<boolean>(Boolean(search.reservationId));

  React.useEffect(() => {
    if (!search.reservationId) return;

    let isMounted = true;
    setIsLoading(true);

    reservationApi
      .getPublicBooking(search.reservationId)
      .then((data) => {
        if (!isMounted) return;
        setReservation(data);
        if (search.depositStatus === "success") {
          toast.success("Đặt cọc thành công! Yêu cầu của bạn đang được xử lý.");
        } else if (search.depositStatus === "failed") {
          toast.error("Thanh toán đặt cọc thất bại. Quý khách vui lòng thử lại.");
        }
      })
      .catch((err: unknown) => {
        const msg = err instanceof Error ? err.message : "Không tìm thấy đơn đặt bàn";
        toast.error(msg);
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [search.reservationId, search.depositStatus]);

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

          {isLoading ? (
            <div className="flex flex-col items-center justify-center p-12 space-y-4">
              <Loader2 className="h-8 w-8 animate-spin text-primary" />
              <p className="text-sm text-muted-foreground">Đang tải thông tin đặt bàn...</p>
            </div>
          ) : reservation ? (
            <BookingConfirmation
              reservation={reservation}
              onReset={() => setReservation(null)}
            />
          ) : (
            <BookForm />
          )}
        </main>
      </div>
    </RestaurantProvider>
  );
}
