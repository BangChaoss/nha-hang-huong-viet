import { useCallback, useEffect, useState } from "react";
import { CreditCard, FileText, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { VnpayPaymentButton } from "@/components/hv/VnpayPaymentButton";
import { paymentApi, type UnpaidInvoice } from "@/lib/payment-api";
import { useSession } from "@/lib/auth-session";

const formatVnd = (amount: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(amount);

export function UnpaidInvoices() {
  const { session, ready } = useSession();
  const [invoices, setInvoices] = useState<UnpaidInvoice[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!session) {
      setInvoices([]);
      return;
    }
    setLoading(true);
    try {
      setInvoices(await paymentApi.getUnpaidInvoices());
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Không tải được hóa đơn chưa thanh toán.",
      );
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    if (ready && session) void load();
    else if (ready && !session) setInvoices([]);
  }, [ready, session, load]);

  if (!ready || !session || (invoices.length === 0 && !loading)) return null;

  return (
    <section className="mt-8 space-y-4" aria-labelledby="unpaid-invoices-heading">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Thanh toán</p>
          <h2 id="unpaid-invoices-heading" className="text-3xl font-semibold">
            Hóa đơn chưa thanh toán
          </h2>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => void load()}
          disabled={loading}
        >
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          Làm mới
        </Button>
      </div>

      {loading && invoices.length === 0 ? (
        <p className="text-sm text-muted-foreground">Đang tải hóa đơn...</p>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {invoices.map((invoice) => (
            <article key={invoice.invoiceId} className="surface-card flex flex-col gap-4 p-5">
              <div className="flex items-start gap-3">
                <div className="rounded-lg bg-primary/10 p-2 text-primary">
                  <FileText className="h-5 w-5" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">{invoice.invoiceCode}</p>
                  <p className="break-all text-xs text-muted-foreground">
                    Mã hóa đơn: {invoice.invoiceId}
                  </p>
                  {invoice.createdAt && (
                    <p className="mt-1 text-xs text-muted-foreground">
                      Ngày tạo: {new Date(invoice.createdAt).toLocaleString("vi-VN")}
                    </p>
                  )}
                </div>
              </div>
              <div className="flex items-end justify-between gap-3 border-t pt-3">
                <div>
                  <p className="text-xs text-muted-foreground">Tổng thanh toán</p>
                  <p className="text-xl font-semibold tabular-nums">
                    {formatVnd(invoice.finalAmount)}
                  </p>
                </div>
                <VnpayPaymentButton invoiceId={invoice.invoiceId} />
              </div>
            </article>
          ))}
        </div>
      )}
      <p className="text-xs text-muted-foreground">
        <CreditCard className="mr-1 inline h-3.5 w-3.5" />
        Chỉ thanh toán hóa đơn do hệ thống Backend tạo và gắn với tài khoản đang đăng nhập.
      </p>
    </section>
  );
}
