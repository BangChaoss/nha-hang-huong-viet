import { useQuery } from "@tanstack/react-query";
import { Award, TicketPercent, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { loyaltyApi } from "@/lib/loyalty-api";
import { cn } from "@/lib/utils";

const money = (n: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(n);

export function LoyaltyWallet() {
  const query = useQuery({ queryKey: ["account", "loyalty"], queryFn: loyaltyApi.getProfile });

  return (
    <section className="surface-card space-y-5 p-5 sm:p-6">
      <div>
        <h2 className="flex items-center gap-2 text-xl font-medium sm:text-2xl">
          <Award className="h-6 w-6 text-primary" /> Ví Điểm & Voucher Của Tôi
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Điểm thưởng và voucher được định danh tự động theo số điện thoại trên hồ sơ tài khoản.
        </p>
      </div>

      {query.isPending && <p className="text-sm text-muted-foreground">Đang tải ví điểm...</p>}

      {query.isError && (
        <div className="text-sm text-destructive flex items-center gap-2">
          <span>{query.error.message}</span>
          <Button variant="ghost" size="sm" onClick={() => void query.refetch()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            Thử lại
          </Button>
        </div>
      )}

      {query.data && (
        <>
          <div className="rounded-xl border bg-card/60 p-5 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <p className="text-sm text-muted-foreground">Số dư điểm hiện tại</p>
              <p className="mt-1 text-4xl font-bold tabular-nums text-primary">
                {Math.floor(query.data.pointsBalance).toLocaleString("vi-VN")}{" "}
                <span className="text-lg font-medium text-muted-foreground">điểm</span>
              </p>
              <p className="mt-1.5 text-xs text-muted-foreground">
                Điểm tích luỹ có thể sử dụng trực tiếp để khấu trừ vào hoá đơn khi thanh toán tại nhà hàng.
              </p>
            </div>
            <Button variant="outline" size="sm" onClick={() => void query.refetch()} disabled={query.isFetching}>
              <RefreshCw className={cn("mr-2 h-4 w-4", query.isFetching && "animate-spin")} />
              Cập nhật ví
            </Button>
          </div>

          <div className="space-y-3 pt-2">
            <h3 className="flex items-center gap-2 text-base font-medium">
              <TicketPercent className="h-5 w-5 text-moss" /> Voucher có thể sử dụng ({query.data.vouchers.length})
            </h3>
            {query.data.vouchers.length === 0 ? (
              <div className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
                Hiện chưa có voucher khả dụng dành riêng cho số điện thoại của bạn.
              </div>
            ) : (
              <div className="grid gap-3 md:grid-cols-2">
                {query.data.vouchers.map((v) => (
                  <article key={v.id} className="rounded-xl border p-4 hover:border-primary/40 transition-colors">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-mono font-bold text-primary">{v.voucherCode}</span>
                      <span className="rounded-full bg-secondary px-2.5 py-0.5 text-xs font-semibold">
                        {v.discountType === "PERCENTAGE"
                          ? `Giảm ${v.discountValue}%`
                          : `Giảm ${money(v.discountValue)}`}
                      </span>
                    </div>
                    <p className="mt-2 font-medium text-foreground">{v.title}</p>
                    {v.description && <p className="mt-1 text-xs text-muted-foreground line-clamp-2">{v.description}</p>}
                    <div className="mt-3 border-t pt-2 space-y-0.5 text-[11px] text-muted-foreground">
                      <p>Đơn tối thiểu: {money(v.minOrderAmount)}</p>
                      {v.maxDiscountAmount != null && <p>Giảm tối đa: {money(v.maxDiscountAmount)}</p>}
                      <p>Hạn sử dụng: {new Date(v.endDate).toLocaleDateString("vi-VN")}</p>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </section>
  );
}
