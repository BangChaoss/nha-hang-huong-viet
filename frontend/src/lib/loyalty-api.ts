import { getSession } from "@/lib/auth-session";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");

async function request<T>(path: string): Promise<T> {
  const session = getSession();
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...(session ? { Authorization: `Bearer ${session.token}` } : {}) },
  });
  if (!response.ok) {
    let message = `Yêu cầu thất bại (${response.status})`;
    try { const body = await response.json(); message = body.message ?? body.detail ?? message; } catch { /* no JSON */ }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

export type LoyaltyProfile = {
  phoneNumber: string;
  pointsBalance: number;
  vouchers: {
    id: string;
    voucherCode: string;
    title: string;
    description: string | null;
    discountType: "PERCENTAGE" | "FIXED_AMOUNT";
    discountValue: number;
    maxDiscountAmount: number | null;
    minOrderAmount: number;
    startDate: string;
    endDate: string;
  }[];
};

export const loyaltyApi = {
  getProfile: () => request<LoyaltyProfile>("/api/account/loyalty"),
};
