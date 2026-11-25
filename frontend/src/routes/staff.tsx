import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { CalendarCheck, ChefHat, ShieldCheck, Store, UtensilsCrossed, Receipt } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { paymentApi, type PaymentOrderStatus } from "@/lib/payment-api";
import { RestaurantProvider } from "@/lib/restaurant-store";
import { StaffPOS } from "@/components/hv/StaffPOS";
import { StaffBookingDashboard } from "@/features/reservation/components/staff-booking-dashboard";
import { KitchenKDS } from "@/components/hv/KitchenKDS";
import { AccountMenu } from "@/components/hv/AccountMenu";
import { RoleGuard } from "@/components/hv/RoleGuard";
import { useSession } from "@/lib/auth-session";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { StaffInvoices } from "@/components/hv/StaffInvoices";
import { orderCode } from "@/features/order/utils/order-code";

export const Route = createFileRoute("/staff")({
  head: () => ({ meta: [{ title: "Nhân viên & Thu ngân — Hương Việt" }] }),
  component: Page,
});

function Page() {
  const { session } = useSession();
  const isAdmin = session?.role === "ADMIN";

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

    paymentApi
      .order(orderId)
      .then((order) => {
        const success = order.status === "PAID";
        setPaymentResult({
          order,
          status: order.status,
          message: success
            ? `Thanh toán thành công. Đã cộng ${Number(order.pointsAwarded || 0).toLocaleString("vi-VN")} điểm cho khách hàng (${order.phoneNumber || "không cung cấp số điện thoại"}).`
            : order.status === "PENDING"
              ? "Chưa chốt hóa đơn. Kiểm tra giao dịch trên VNPay trước khi thu tiền lại."
              : "Giao dịch thất bại hoặc bị huỷ. Voucher chưa được ghi nhận và không cộng điểm.",
        });
      })
      .catch(() =>
        setPaymentResult({
          order: null,
          status,
          message: "Không tra cứu được kết quả VNPay. Kiểm tra giao dịch trước khi thu tiền lại.",
        })
      );
  }, []);

  return (
    <RoleGuard allowedRoles={["ADMIN", "STAFF"]} pageTitle="Nhân viên & Thu ngân">
      <RestaurantProvider>
        <div className="min-h-screen bg-background">
          <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
            <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 sm:px-6">
              <Link to={isAdmin ? "/admin" : "/staff"} className="flex items-center gap-3">
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
                {isAdmin && (
                  <Link
                    to="/admin"
                    className="inline-flex items-center gap-1.5 rounded-full border border-primary/30 bg-primary/10 px-3 py-1.5 text-xs font-medium text-primary hover:bg-primary/20 transition-colors"
                  >
                    <ShieldCheck className="h-3.5 w-3.5" />
                    Về Trung tâm IT
                  </Link>
                )}
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
            <Dialog open onOpenChange={(open) => !open && setPaymentResult(null)}>
              <DialogContent className="max-h-[90dvh] max-w-lg overflow-y-auto">
                <DialogHeader>
                  <DialogTitle>
                    {paymentResult.status === "PAID" ? "Thanh toán thành công" : "Kết quả thanh toán VNPay"}
                  </DialogTitle>
                  <DialogDescription>{paymentResult.message}</DialogDescription>
                </DialogHeader>
                {paymentResult.order && (
                  <dl className="space-y-2 text-sm">
                    {paymentResult.order.diningOrderId && (
                      <div className="flex justify-between gap-4">
                        <dt className="text-muted-foreground">Đơn tại bàn</dt>
                        <dd>{orderCode(paymentResult.order.diningOrderId)}</dd>
                      </div>
                    )}
                    <div className="flex justify-between gap-4">
                      <dt className="text-muted-foreground">Mã giao dịch</dt>
                      <dd className="break-all text-right">
                        {paymentResult.order.transactionNo || paymentResult.order.orderId}
                      </dd>
                    </div>
                    <div className="flex justify-between gap-4">
                      <dt className="text-muted-foreground">Số tiền thanh toán</dt>
                      <dd>
                        {Number(paymentResult.order.amount || 0).toLocaleString("vi-VN")}đ
                      </dd>
                    </div>
                    <div className="flex justify-between gap-4">
                      <dt className="text-muted-foreground">Điểm thưởng</dt>
                      <dd>
                        {Number(paymentResult.order.pointsAwarded || 0).toLocaleString("vi-VN")}
                      </dd>
                    </div>
                  </dl>
                )}
                <div className="flex justify-end">
                  <button
                    type="button"
                    onClick={() => setPaymentResult(null)}
                    className="min-h-11 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    Đóng
                  </button>
                </div>
              </DialogContent>
            </Dialog>
          )}

          <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 sm:py-10">
            <Tabs defaultValue="pos" className="space-y-6">
              <TabsList className="grid w-full max-w-2xl grid-cols-4">
                <TabsTrigger value="pos" className="flex items-center gap-2">
                  <Store className="h-4 w-4" />
                  <span>Phục vụ & Thu ngân</span>
                </TabsTrigger>
                <TabsTrigger value="bookings" className="flex items-center gap-2">
                  <CalendarCheck className="h-4 w-4" />
                  <span>Đặt Bàn</span>
                </TabsTrigger>
                <TabsTrigger value="kitchen" className="flex items-center gap-2">
                  <ChefHat className="h-4 w-4" />
                  <span>Điều phối bếp</span>
                </TabsTrigger>
                <TabsTrigger value="invoices" className="flex items-center gap-2">
                  <Receipt className="h-4 w-4" />
                  <span>Hóa đơn</span>
                </TabsTrigger>
              </TabsList>

              <TabsContent value="pos" className="space-y-4 focus-visible:outline-none">
                <StaffPOS />
              </TabsContent>

              <TabsContent value="bookings" className="space-y-4 focus-visible:outline-none">
                <StaffBookingDashboard />
              </TabsContent>

              <TabsContent value="kitchen" className="space-y-4 focus-visible:outline-none">
                <KitchenKDS />
              </TabsContent>

              <TabsContent value="invoices" className="space-y-4 focus-visible:outline-none">
                <StaffInvoices />
              </TabsContent>

            </Tabs>
          </main>
        </div>
      </RestaurantProvider>
    </RoleGuard>
  );
}