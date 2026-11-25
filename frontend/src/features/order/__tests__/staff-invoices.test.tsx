import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { StaffInvoices } from "@/components/hv/StaffInvoices";
import { invoiceApi, type InvoiceRecord } from "@/lib/invoice-api";

vi.mock("@/lib/invoice-api");

it("gom món cùng giá trong nội dung dùng để xem và in hóa đơn, không đổi tổng tiền", async () => {
  const pho = { dishId: "pho", dishName: "Phở bò", unitPrice: 85000, quantity: 1, totalPrice: 85000 };
  const invoice: InvoiceRecord = {
    id: "invoice-1", invoiceCode: "HV-123", orderId: "order-1", tableNumber: "Bàn 01",
    staffId: null, customerPhone: null, voucherCode: null, voucherDiscountAmount: 0,
    subtotalAmount: 240000, discountAmount: 0, taxAmount: 24000, finalAmount: 264000,
    loyaltyPointsEarned: 0, status: "PAID", paidAt: null,
    items: [
      { id: "first", ...pho },
      { id: "second", ...pho },
      { id: "third", ...pho, unitPrice: 70000, totalPrice: 70000 },
    ],
  };
  vi.mocked(invoiceApi.listByDate).mockResolvedValue([invoice]);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><StaffInvoices /></QueryClientProvider>);

  fireEvent.click(await screen.findByRole("button", { name: "Xem & In" }));
  const printable = within(document.getElementById("printable-invoice")!);
  const rows = printable.getAllByRole("row", { name: /Phở bò/ });
  expect(rows).toHaveLength(2);
  expect(rows[0]).toHaveTextContent(/Phở bò\s*2\s*85\.000đ\s*170\.000đ/);
  expect(rows[1]).toHaveTextContent(/Phở bò\s*1\s*70\.000đ\s*70\.000đ/);
  expect(printable.getByText("264.000đ")).toBeInTheDocument();
  expect(invoice.items[0]?.quantity).toBe(1);
});

it("hiển thị rõ phí thu phòng VIP walk-in và phí thu & cọc đã thanh toán trước trên phiếu hóa đơn", async () => {
  const walkInVip: InvoiceRecord = {
    id: "inv-vip-walkin", invoiceCode: "HV-VIP-WALKIN", orderId: "order-w", tableNumber: "VIP 01",
    staffId: null, customerPhone: null, voucherCode: null, voucherDiscountAmount: 0,
    subtotalAmount: 100000, discountAmount: 0, taxAmount: 35000, finalAmount: 385000,
    vipFeeAmount: 250000, vipDepositPaid: 0, loyaltyPointsEarned: 0, status: "PAID", paidAt: null,
    items: [{ id: "i-1", dishId: "pho", dishName: "Phở bò", unitPrice: 100000, quantity: 1, totalPrice: 100000 }],
  };
  const prebookedVip: InvoiceRecord = {
    id: "inv-vip-prebook", invoiceCode: "HV-VIP-PREBOOK", orderId: "order-p", tableNumber: "VIP 02",
    staffId: null, customerPhone: "0909999999", voucherCode: null, voucherDiscountAmount: 0,
    subtotalAmount: 100000, discountAmount: 0, taxAmount: 10000, finalAmount: 110000,
    vipFeeAmount: 0, vipDepositPaid: 250000, loyaltyPointsEarned: 0, status: "PAID", paidAt: null,
    items: [{ id: "i-2", dishId: "pho", dishName: "Phở bò", unitPrice: 100000, quantity: 1, totalPrice: 100000 }],
  };
  vi.mocked(invoiceApi.listByDate).mockResolvedValue([walkInVip, prebookedVip]);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><StaffInvoices /></QueryClientProvider>);

  const viewButtons = await screen.findAllByRole("button", { name: "Xem & In" });
  fireEvent.click(viewButtons[0]!);
  let printable = within(document.getElementById("printable-invoice")!);
  expect(printable.getByText("Phí thu phòng VIP:")).toBeInTheDocument();
  expect(printable.getByText("+250.000đ")).toBeInTheDocument();

  fireEvent.click(screen.getByRole("button", { name: "Close" }));
  fireEvent.click(viewButtons[1]!);
  printable = within(document.getElementById("printable-invoice")!);
  expect(printable.getByText("Phí thu & cọc phòng VIP:")).toBeInTheDocument();
  expect(printable.getByText("Đã thanh toán 250.000đ (không trừ hóa đơn)")).toBeInTheDocument();
});
