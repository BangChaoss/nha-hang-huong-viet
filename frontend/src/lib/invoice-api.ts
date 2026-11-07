import { getSession } from "@/lib/auth-session";
import { API_BASE } from "@/lib/table-api";

export interface InvoiceItem {
  id: string;
  dishId: string;
  dishName: string;
  unitPrice: number;
  quantity: number;
  totalPrice: number;
}

export interface InvoiceRecord {
  id: string;
  invoiceCode: string;
  orderId: string;
  tableNumber: string;
  staffId: string | null;
  voucherCode: string | null;
  voucherDiscountAmount: number;
  subtotalAmount: number;
  discountAmount: number;
  taxAmount: number;
  finalAmount: number;
  loyaltyPointsEarned: number;
  status: string;
  paidAt: string | null;
  items: InvoiceItem[];
}

async function request<T>(path: string): Promise<T> {
  const token = getSession()?.token;
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });

  if (!res.ok) {
    let message = `Lỗi yêu cầu (${res.status})`;
    try {
      const data = await res.json();
      message = data.message || message;
    } catch {
      // bỏ qua
    }
    throw new Error(message);
  }
  return res.json() as Promise<T>;
}

export const invoiceApi = {
  listByDate: (date?: string) => {
    const q = date ? `?date=${encodeURIComponent(date)}` : "";
    return request<InvoiceRecord[]>(`/api/invoices${q}`);
  },
  getById: (id: string) => request<InvoiceRecord>(`/api/invoices/${encodeURIComponent(id)}`),
};