import { useState, useMemo, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Calendar,
  Search,
  Printer,
  Receipt,
  Eye,
  RefreshCw,
  FileText,
  UtensilsCrossed,
  CheckCircle2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Card } from "@/components/ui/card";
import { formatVND } from "@/lib/restaurant-store";
import { invoiceApi, type InvoiceRecord } from "@/lib/invoice-api";

export function StaffInvoices() {
  const [selectedDate, setSelectedDate] = useState<string>(() =>
    new Date().toISOString().slice(0, 10)
  );
  const [search, setSearch] = useState("");
  const [previewInvoice, setPreviewInvoice] = useState<InvoiceRecord | null>(null);
  const printRef = useRef<HTMLDivElement>(null);

  const query = useQuery({
    queryKey: ["invoices", selectedDate],
    queryFn: () => invoiceApi.listByDate(selectedDate),
    staleTime: 10_000,
  });

  const filteredInvoices = useMemo(() => {
    if (!query.data) return [];
    if (!search.trim()) return query.data;
    const q = search.trim().toLowerCase();
    return query.data.filter(
      (inv) =>
        inv.invoiceCode.toLowerCase().includes(q) ||
        inv.tableNumber.toLowerCase().includes(q) ||
        (inv.voucherCode && inv.voucherCode.toLowerCase().includes(q))
    );
  }, [query.data, search]);

  const summary = useMemo(() => {
    const list = query.data ?? [];
    const count = list.length;
    const totalAmount = list.reduce((sum, inv) => sum + Number(inv.finalAmount || 0), 0);
    const totalDiscount = list.reduce((sum, inv) => sum + Number(inv.discountAmount || 0), 0);
    return { count, totalAmount, totalDiscount };
  }, [query.data]);

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="space-y-6">
      {/* Header & Bộ lọc */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <Receipt className="h-6 w-6 text-primary" />
            Lịch Sử Hóa Đơn Đã Thanh Toán
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Xem danh sách giao dịch đã thanh toán thành công theo ngày và in lại hóa đơn.
          </p>
        </div>

        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => void query.refetch()}
          disabled={query.isFetching}
          className="text-xs h-8"
        >
          <RefreshCw className={`h-3.5 w-3.5 mr-1 ${query.isFetching ? "animate-spin" : ""}`} />
          Làm mới
        </Button>
      </div>

      {/* KPI tóm tắt ngày */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <Card className="p-4 border bg-card">
          <p className="text-xs text-muted-foreground">Tổng số hóa đơn</p>
          <p className="text-2xl font-bold font-mono text-foreground mt-1">{summary.count}</p>
        </Card>
        <Card className="p-4 border bg-emerald-500/10 border-emerald-500/20">
          <p className="text-xs text-emerald-800 dark:text-emerald-300 font-medium">Doanh thu thu về</p>
          <p className="text-2xl font-bold font-mono text-emerald-700 dark:text-emerald-400 mt-1">
            {formatVND(summary.totalAmount)}
          </p>
        </Card>
        <Card className="p-4 border bg-amber-500/10 border-amber-500/20">
          <p className="text-xs text-amber-800 dark:text-amber-300 font-medium">Tổng giảm giá / Voucher</p>
          <p className="text-2xl font-bold font-mono text-amber-700 dark:text-amber-400 mt-1">
            {formatVND(summary.totalDiscount)}
          </p>
        </Card>
      </div>

      {/* Thanh công cụ lọc */}
      <Card className="p-4 border">
        <div className="flex flex-col sm:flex-row items-center gap-3">
          <div className="flex items-center gap-2 w-full sm:w-auto">
            <Calendar className="h-4 w-4 text-muted-foreground shrink-0" />
            <Input
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="text-xs h-9 w-full sm:w-44"
            />
          </div>

          <div className="relative w-full sm:flex-1">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              type="text"
              placeholder="Tìm theo mã hóa đơn, số bàn..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-8 text-xs h-9"
            />
          </div>
        </div>
      </Card>

      {/* Bảng danh sách hóa đơn */}
      <div className="surface-card overflow-x-auto p-4 border rounded-xl">
        {query.isLoading ? (
          <p className="py-12 text-center text-sm text-muted-foreground">Đang tải danh sách hóa đơn...</p>
        ) : filteredInvoices.length === 0 ? (
          <div className="py-12 text-center text-muted-foreground space-y-2">
            <FileText className="h-8 w-8 mx-auto opacity-50" />
            <p className="text-sm">Không có hóa đơn nào trong ngày {selectedDate}.</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground text-xs uppercase tracking-wider">
                <th className="py-2.5 px-3">Mã Hóa Đơn</th>
                <th className="py-2.5 px-3">Bàn</th>
                <th className="py-2.5 px-3">Giờ thanh toán</th>
                <th className="py-2.5 px-3">Số món</th>
                <th className="py-2.5 px-3 text-right">Giảm giá</th>
                <th className="py-2.5 px-3 text-right">Tổng thực thu</th>
                <th className="py-2.5 px-3 text-center">Trạng thái</th>
                <th className="py-2.5 px-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredInvoices.map((inv) => {
                const itemCount = inv.items?.reduce((s, i) => s + i.quantity, 0) ?? 0;
                const paidTime = inv.paidAt
                  ? new Date(inv.paidAt).toLocaleTimeString("vi-VN", {
                      hour: "2-digit",
                      minute: "2-digit",
                      second: "2-digit",
                    })
                  : "—";

                return (
                  <tr key={inv.id} className="border-b last:border-0 hover:bg-muted/40 transition-colors">
                    <td className="py-3 px-3 font-mono font-bold text-primary">{inv.invoiceCode}</td>
                    <td className="py-3 px-3 font-medium">{inv.tableNumber}</td>
                    <td className="py-3 px-3 text-xs text-muted-foreground">{paidTime}</td>
                    <td className="py-3 px-3 tabular-nums">{itemCount} phần</td>
                    <td className="py-3 px-3 text-right tabular-nums text-muted-foreground">
                      {inv.discountAmount > 0 ? `-${formatVND(inv.discountAmount)}` : "0đ"}
                    </td>
                    <td className="py-3 px-3 text-right font-semibold tabular-nums text-foreground">
                      {formatVND(inv.finalAmount)}
                    </td>
                    <td className="py-3 px-3 text-center">
                      <Badge variant="outline" className="bg-emerald-500/10 text-emerald-700 border-emerald-500/30 text-[11px]">
                        <CheckCircle2 className="h-3 w-3 mr-1" />
                        Đã thanh toán
                      </Badge>
                    </td>
                    <td className="py-3 px-3 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="h-7 text-xs"
                          onClick={() => setPreviewInvoice(inv)}
                        >
                          <Eye className="h-3.5 w-3.5 mr-1" />
                          Xem & In
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* MODAL IN HÓA ĐƠN CHI TIẾT */}
      <Dialog open={Boolean(previewInvoice)} onOpenChange={(open) => !open && setPreviewInvoice(null)}>
        <DialogContent className="max-w-md max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center justify-between text-base">
              <span>Hóa đơn: {previewInvoice?.invoiceCode}</span>
              <Button size="sm" onClick={handlePrint} className="gap-1.5 bg-primary hover:bg-primary/90">
                <Printer className="h-4 w-4" />
                In hóa đơn
              </Button>
            </DialogTitle>
          </DialogHeader>

          {previewInvoice && (
            <div className="py-2" id="printable-invoice" ref={printRef}>
              <div className="rounded-lg border bg-white p-5 text-zinc-900 shadow-xs font-sans text-xs space-y-3 print:border-none print:shadow-none print:p-0">
                {/* Header Bill */}
                <div className="text-center space-y-1 border-b pb-3">
                  <div className="flex items-center justify-center gap-1.5 font-display text-xl font-bold tracking-wider text-zinc-950">
                    <UtensilsCrossed className="h-5 w-5 text-amber-700" />
                    HƯƠNG VIỆT
                  </div>
                  <p className="text-[11px] uppercase tracking-widest text-zinc-500 font-medium">
                    Ẩm Thực Cố Đô Huế
                  </p>
                  <p className="text-[10px] text-zinc-500">Số 12 Lê Lợi, Phường Vĩnh Ninh, TP. Huế</p>
                  <p className="text-[10px] text-zinc-500">Hotline: 1900 8888 · Website: huongviet.vn</p>
                </div>

                <div className="text-center font-bold text-sm tracking-wide text-zinc-900 pt-1">
                  PHIẾU THANH TOÁN
                </div>

                {/* Metadata */}
                <div className="grid grid-cols-2 gap-1 text-[11px] text-zinc-600 border-b pb-2">
                  <p>Mã HĐ: <strong className="text-zinc-900 font-mono">{previewInvoice.invoiceCode}</strong></p>
                  <p className="text-right">Bàn: <strong className="text-zinc-900">{previewInvoice.tableNumber}</strong></p>
                  <p>
                    Giờ: {previewInvoice.paidAt ? new Date(previewInvoice.paidAt).toLocaleString("vi-VN") : "—"}
                  </p>
                  <p className="text-right">Thu ngân: Staff</p>
                </div>

                {/* Bảng món */}
                <table className="w-full text-[11px] border-b pb-2">
                  <thead>
                    <tr className="border-b text-zinc-500 font-semibold text-left">
                      <th className="py-1">Món ăn</th>
                      <th className="py-1 text-center">SL</th>
                      <th className="py-1 text-right">Đơn giá</th>
                      <th className="py-1 text-right">T.Tiền</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-100">
                    {previewInvoice.items?.map((it) => (
                      <tr key={it.id}>
                        <td className="py-1.5 font-medium">{it.dishName}</td>
                        <td className="py-1.5 text-center tabular-nums">{it.quantity}</td>
                        <td className="py-1.5 text-right tabular-nums">{formatVND(it.unitPrice)}</td>
                        <td className="py-1.5 text-right tabular-nums font-semibold">{formatVND(it.totalPrice)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {/* Tính tiền chi tiết */}
                <div className="space-y-1 text-[11px] border-b pb-3">
                  <div className="flex justify-between">
                    <span className="text-zinc-500">Tạm tính (chưa VAT):</span>
                    <span className="tabular-nums font-medium">{formatVND(previewInvoice.subtotalAmount)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-zinc-500">Thuế VAT (10%):</span>
                    <span className="tabular-nums font-medium">{formatVND(previewInvoice.taxAmount)}</span>
                  </div>
                  {previewInvoice.voucherDiscountAmount > 0 && (
                    <div className="flex justify-between text-amber-700">
                      <span>Voucher giảm giá ({previewInvoice.voucherCode}):</span>
                      <span className="tabular-nums">-{formatVND(previewInvoice.voucherDiscountAmount)}</span>
                    </div>
                  )}
                  {previewInvoice.discountAmount > previewInvoice.voucherDiscountAmount && (
                    <div className="flex justify-between text-amber-700">
                      <span>Điểm tích lũy đã trừ:</span>
                      <span className="tabular-nums">
                        -{formatVND(previewInvoice.discountAmount - previewInvoice.voucherDiscountAmount)}
                      </span>
                    </div>
                  )}
                  <div className="flex justify-between pt-1 border-t text-sm font-bold text-zinc-950">
                    <span>TỔNG THANH TOÁN:</span>
                    <span className="text-base font-mono">{formatVND(previewInvoice.finalAmount)}</span>
                  </div>
                </div>

                {/* Điểm thưởng */}
                {previewInvoice.loyaltyPointsEarned > 0 && (
                  <div className="rounded bg-amber-50 p-2 text-center text-[10px] text-amber-900">
                    ✨ Quý khách được tích luỹ <strong>+{previewInvoice.loyaltyPointsEarned.toLocaleString("vi-VN")} điểm</strong> thưởng.
                  </div>
                )}

                {/* Lời cảm ơn */}
                <div className="text-center pt-2 space-y-0.5 text-[10px] text-zinc-400">
                  <p className="font-semibold text-zinc-600">Cảm ơn Quý khách & Hẹn gặp lại!</p>
                  <p>Wifi: HuongViet_Restaurant · Pass: 88888888</p>
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}