import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { paymentApi, type PaymentOrderStatus } from "@/lib/payment-api";
import { RestaurantProvider } from "@/lib/restaurant-store";
import { StaffPOS } from "@/components/hv/StaffPOS";
import { StaffBookingDashboard } from "@/features/reservation/components/staff-booking-dashboard";
import { UtensilsCrossed, CalendarCheck, Store } from "lucide-react";
import { AccountMenu } from "@/components/hv/AccountMenu";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

export const Route = createFileRoute("/staff")({ component: Page });

function Page() {
  const [paymentResult, setPaymentResult] = useState<{
    order: PaymentOrderStatus | null;
    status: string;
    message: string;
  } | null>(null);

  useEffect(() => {
    const url = new URL(window.location.href);
    const orderId = url.searchParams.get("paymentOrderId");
    const status = url.searchParams.get("paymentStatus");
    if (!orderId || !status) return;
    url.searchParams.delete("paymentOrderId");
    url.searchParams.delete("paymentStatus");
    window.history.replaceState({}, "", url.toString());

    paymentApi.order(orderId).then((order) => {
      const success = order.status === "PAID";
      setPaymentResult({
        order,
        status: order.status,
        message: success
          ? `Thanh toán thành công. Đã cộng ${Number(order.pointsAwarded || 0).toLocaleString("vi-VN")} điểm cho khách hàng (${order.phoneNumber || "không cung cấp số điện thoại"}).`
          : "Giao dịch thất bại hoặc bị huỷ. Voucher chưa được ghi nhận và không cộng điểm.",
      });
    }).catch(() => setPaymentResult({
      order: null,
      status,
      message: "Giao dịch thất bại hoặc bị huỷ. Voucher chưa được ghi nhận và không cộng điểm.",
    }));
  }, []);

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
        {paymentResult && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4" role="presentation">
            <section role="dialog" aria-modal="true" aria-labelledby="payment-result-title"
              className="w-full max-w-lg space-y-4 rounded-xl border bg-background p-6 shadow-xl">
              <div className="flex items-start justify-between gap-4">
                <h2 id="payment-result-title" className="text-xl font-semibold">
                  {paymentResult.status === "PAID" ? "Thanh toán thành công" : "Kết quả thanh toán VNPAY"}
                </h2>
                <button type="button" aria-label="Đóng thông báo" onClick={() => setPaymentResult(null)}
                  className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground">
                  <X className="h-5 w-5" />
                </button>
              </div>
              <p className="text-sm">{paymentResult.message}</p>
              {paymentResult.order && (
                <dl className="space-y-2 text-sm">
                  <div className="flex justify-between gap-4"><dt className="text-muted-foreground">Mã giao dịch</dt><dd className="break-all text-right">{paymentResult.order.transactionNo || paymentResult.order.orderId}</dd></div>
                  <div className="flex justify-between gap-4"><dt className="text-muted-foreground">Số tiền thanh toán</dt><dd>{Number(paymentResult.order.amount || 0).toLocaleString("vi-VN")}đ</dd></div>
                  <div className="flex justify-between gap-4"><dt className="text-muted-foreground">Điểm thưởng</dt><dd>{Number(paymentResult.order.pointsAwarded || 0).toLocaleString("vi-VN")}</dd></div>
                </dl>
              )}
              <div className="flex justify-end">
                <button type="button" onClick={() => setPaymentResult(null)}
                  className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground">Đóng</button>
              </div>
            </section>
          </div>
        )}
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
