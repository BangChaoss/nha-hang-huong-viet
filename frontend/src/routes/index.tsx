import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { paymentApi, type PaymentOrderStatus } from "@/lib/payment-api";
import { UtensilsCrossed } from "lucide-react";
import { UserMenu } from "@/components/hv/UserMenu";
import { RestaurantProvider } from "@/lib/restaurant-store";
import { CustomerPortal } from "@/components/hv/CustomerPortal";
import { AccountMenu } from "@/components/hv/AccountMenu";

export const Route = createFileRoute("/")({
  head: () => ({ meta: [
    { title: "Hương Việt — Nhà hàng Ẩm Thực Cố Đô" },
    { name: "description", content: "Khám phá thực đơn và đặt bàn tại nhà hàng Hương Việt." },
  ] }),
  component: HomePage,
});

function HomePage() {
  const [paymentResult, setPaymentResult] = useState<{ status: string; order: PaymentOrderStatus | null; message: string } | null>(null);
  useEffect(() => {
    const url = new URL(window.location.href);
    const orderId = url.searchParams.get("paymentOrderId");
    const status = url.searchParams.get("paymentStatus");
    if (!orderId || !status) return;
    url.searchParams.delete("paymentOrderId");
    url.searchParams.delete("paymentStatus");
    window.history.replaceState({}, "", url.toString());
    paymentApi.order(orderId).then((order) => setPaymentResult({
      status: order.status,
      order,
      message: order.status === "PAID" ? `Thanh toán thành công. Đã cộng ${order.pointsAwarded.toLocaleString("vi-VN")} điểm.` :
        order.status === "FAILED" ? "Giao dịch thất bại hoặc bị huỷ. Voucher chưa được ghi nhận và không cộng điểm." :
        "Giao dịch đang chờ xác nhận từ VNPAY.",
    })).catch(() => setPaymentResult({
      status,
      order: null,
      message: status === "success" ? "VNPAY đã trả về thành công; hệ thống đang đối soát giao dịch." : "Thanh toán chưa thành công. Vui lòng kiểm tra trạng thái giao dịch.",
    }));
  }, []);
  return <RestaurantProvider><div className="min-h-screen">
    <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 sm:px-6">
        <Link to="/" className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground"><UtensilsCrossed className="h-5 w-5" /></div>
          <div className="leading-tight"><p className="font-display text-2xl font-semibold tracking-wide">HƯƠNG VIỆT</p><p className="text-xs uppercase tracking-[0.25em] text-muted-foreground">Ẩm Thực Cố Đô</p></div>
        </Link>
        <AccountMenu />
      </div>
    </header>
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 sm:py-10">
      {paymentResult && <section role="status" className="surface-card mb-6 space-y-2 border p-4">
        <h2 className="text-lg font-semibold">{paymentResult.status === "PAID" ? "Thanh toán thành công" : paymentResult.status === "FAILED" ? "Thanh toán thất bại" : "Trạng thái thanh toán"}</h2>
        <p className="text-sm">{paymentResult.message}</p>
        {paymentResult.order && <p className="text-xs text-muted-foreground">Mã giao dịch: {paymentResult.order.orderId} · Số tiền: {paymentResult.order.amount.toLocaleString("vi-VN")}đ</p>}
      </section>}
      <CustomerPortal />
    </main>
  </div></RestaurantProvider>;
}
