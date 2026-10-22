import { getSession } from "@/lib/auth-session";

export type DiscountType = "PERCENTAGE" | "FIXED_AMOUNT";

export type Voucher = {
  id: string;
  voucherCode: string;
  title: string;
  description: string | null;
  discountType: DiscountType;
  discountValue: number;
  maxDiscountAmount: number | null;
  minOrderAmount: number;
  startDate: string;
  endDate: string;
  totalQuantity: number;
  usedQuantity: number;
  maxUsesPerCustomer: number;
  active: boolean;
  createdAt: string | null;
  updatedAt: string | null;
};

export type VoucherPayload = Omit<
  Voucher,
  "id" | "usedQuantity" | "active" | "createdAt" | "updatedAt"
>;

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getSession()?.token;
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    let message = `Yêu cầu thất bại (${response.status})`;
    try {
      const body = await response.json();
      message = body.message ?? body.detail ?? message;
    } catch {
      // Một số phản hồi lỗi không có nội dung JSON.
    }
    throw new Error(message);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const voucherApi = {
  list: () => request<Voucher[]>("/api/admin/vouchers"),
  create: (payload: VoucherPayload) =>
    request<Voucher>("/api/admin/vouchers", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  setActive: (id: string, active: boolean) =>
    request<Voucher>(`/api/admin/vouchers/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ active }),
    }),
  remove: (id: string) => request<void>(`/api/admin/vouchers/${id}`, { method: "DELETE" }),
};

export type VoucherValidation = {
  valid: boolean;
  message: string;
  discountAmount: number;
  voucherCode: string;
};

export const validateVoucher = (voucherCode: string, phoneNumber: string, orderAmount: number) =>
  request<VoucherValidation>("/api/vouchers/validate", {
    method: "POST",
    body: JSON.stringify({ voucherCode, phoneNumber, orderAmount }),
  });

export const redeemVoucher = (voucherCode: string, phoneNumber: string, orderAmount: number) =>
  request<VoucherValidation>("/api/vouchers/redeem", {
    method: "POST",
    body: JSON.stringify({ voucherCode, phoneNumber, orderAmount }),
  });
