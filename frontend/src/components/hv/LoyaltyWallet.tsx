import { useQuery } from "@tanstack/react-query";
import { Award, TicketPercent, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { loyaltyApi } from "@/lib/loyalty-api";

const money = (n: number) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(n);

export function LoyaltyWallet() {
  const query = useQuery({ queryKey: ["account", "loyalty"], queryFn: loyaltyApi.getProfile });
  return (
    <section className="surface-card space-y-5 p-5 sm:p-6">
      <div>
        <h2 className="flex items-center gap-2 text-xl font-medium sm:text-2xl"><Award className="h-5 w-5" /> Ví điểm & Voucher</h2>
        <p className="mt-1 text-sm text-muted-foreground">Ví điểm được xác định theo số điện thoại trên hồ sơ tài khoản.</p>
      </div>
      {query.isPending && <p className="text-sm text-muted-foreground">Đang tải ví điểm...</p>}
      {query.isError && <div className="text-sm text-destructive">{query.error.message}<Button variant="ghost" size="sm" onClick={() => void query.refetch()}><RefreshCw className="mr-2 h-4 w-4"/>Thử lại</Button></div>}
      {query.data && <>
        <div className="rounded-xl border p-4">
          <p className="text-sm text-muted-foreground">Số dư điểm hiện tại</p>
          <p className="mt-1 text-3xl font-semibold tabular-nums">{money(query.data.pointsBalance)}</p>
          <p className="mt-1 text-xs text-muted-foreground">Quy ước hiện tại: 1 điểm = 1 VND giảm giá.</p>
        </div>
        <div>
          <h3 className="mb-3 flex items-center gap-2 font-medium"><TicketPercent className="h-4 w-4"/> Voucher đang có thể sử dụng ({query.data.vouchers.length})</h3>
          {query.data.vouchers.length === 0 ? <p className="text-sm text-muted-foreground">Chưa có voucher phù hợp với số điện thoại này.</p> :
            <div className="grid gap-3 md:grid-cols-2">{query.data.vouchers.map(v => <article key={v.id} className="rounded-lg border p-4">
              <div className="flex flex-wrap items-center justify-between gap-2"><span className="font-semibold">{v.voucherCode}</span><span className="rounded-full bg-secondary px-2 py-1 text-xs">{v.discountType === "PERCENTAGE" ? `Giảm ${v.discountValue}%` : `Giảm ${money(v.discountValue)}`}</span></div>
              <p className="mt-2 font-medium">{v.title}</p>
              {v.description && <p className="mt-1 text-sm text-muted-foreground">{v.description}</p>}
              <p className="mt-2 text-xs text-muted-foreground">Đơn tối thiểu {money(v.minOrderAmount)} · Hạn đến {new Date(v.endDate).toLocaleDateString("vi-VN")}</p>
              {v.maxDiscountAmount != null && <p className="text-xs text-muted-foreground">Giảm tối đa {money(v.maxDiscountAmount)}</p>}
            </article>)}</div>}
        </div>
      </>}
    </section>
  );
}
