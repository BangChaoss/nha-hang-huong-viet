import { getSession } from "@/lib/auth-session";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const session = getSession();
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(session ? { Authorization: `Bearer ${session.token}` } : {}),
      ...(init.headers ?? {}),
    },
  });
  if (!response.ok) {
    let message = `Yêu cầu thất bại (${response.status})`;
    try {
      const body = await response.json();
      message = body.message ?? body.detail ?? message;
    } catch {
      /* no JSON */
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

export type LoyaltyRate = { earnRatePercent: number; updatedAt: string | null };
export type PaymentOrderStatus = {
  orderId: string;
  status: "PENDING" | "PAID" | "FAILED";
  amount: number;
  phoneNumber: string;
  pointsAwarded: number;
  responseCode: string;
};

export const paymentApi = {
  getLoyaltyBalance: (phoneNumber: string) =>
    request<{ phoneNumber: string; pointsBalance: number }>(`/api/payments/loyalty/${encodeURIComponent(phoneNumber)}`),
  createVnpay: (payload: { subtotal: number; phoneNumber: string; voucherCode?: string; pointsToUse?: number }) =>
    request<{ orderId: string; paymentUrl: string }>("/api/payments/vnpay/create", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  order: (id: string) =>
    request<PaymentOrderStatus>(`/api/payments/vnpay/orders/${encodeURIComponent(id)}`),
  getLoyaltyRate: () => request<LoyaltyRate>("/api/admin/loyalty/settings"),
  updateLoyaltyRate: (earnRatePercent: number) =>
    request<LoyaltyRate>("/api/admin/loyalty/settings", {
      method: "PUT",
      body: JSON.stringify({ earnRatePercent }),
    }),
};
