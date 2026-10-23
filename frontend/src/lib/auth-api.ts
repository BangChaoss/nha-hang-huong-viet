import type { AppRole } from "@/lib/role-routing";

export type AuthUser = { id: string; username: string; email: string; role: AppRole };
export type AuthResponse = AuthUser & { token: string; refreshToken: string | null };
export type RefreshResponse = { token: string; refreshToken: string };

export type RegisterPayload = {
  username: string;
  email: string;
  phoneNumber: string;
  password: string;
};
export type LoginPayload = { usernameOrEmail: string; password: string };
export type ResetPasswordPayload = { token: string; newPassword: string };

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");

/** Lỗi API kèm HTTP status để màn hình chọn thông báo phù hợp. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

// Backend không bật server.error.include-message nên body lỗi thường không có message;
// dùng thông báo theo status làm mặc định cho từng endpoint.
async function request<T>(
  path: string,
  init: RequestInit & { fallback?: Record<number, string> } = {},
): Promise<T> {
  const { fallback, ...rest } = init;
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...rest,
      headers: { "Content-Type": "application/json", ...(rest.headers ?? {}) },
    });
  } catch {
    throw new ApiError("Không thể kết nối máy chủ. Vui lòng thử lại sau.", 0);
  }
  if (!response.ok) {
    let message = fallback?.[response.status] ?? `Yêu cầu thất bại (${response.status})`;
    try {
      const body = await response.json();
      if (typeof body.message === "string" && body.message) message = body.message;
      else if (typeof body.detail === "string" && body.detail) message = body.detail;
    } catch {
      /* response may have no JSON body */
    }
    throw new ApiError(message, response.status);
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

const invalidInput = "Thông tin chưa hợp lệ, vui lòng kiểm tra lại.";

export const authApi = {
  register: (payload: RegisterPayload) =>
    request<AuthResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload),
      fallback: {
        400: invalidInput,
        409: "Tên đăng nhập, email hoặc số điện thoại đã được sử dụng.",
      },
    }),
  login: (payload: LoginPayload) =>
    request<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload),
      fallback: { 400: invalidInput, 401: "Thông tin đăng nhập không hợp lệ." },
    }),
  forgotPassword: (email: string) =>
    request<void>("/api/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ email }),
      fallback: { 400: "Email không hợp lệ." },
    }),
  resetPassword: (payload: ResetPasswordPayload) =>
    request<void>("/api/auth/reset-password", {
      method: "POST",
      body: JSON.stringify(payload),
      fallback: { 400: "Mã OTP không hợp lệ, đã được sử dụng hoặc đã hết hạn." },
    }),
  refresh: (refreshToken: string) =>
    request<RefreshResponse>("/api/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
      fallback: { 401: "Phiên đăng nhập đã hết hạn." },
    }),
  logout: (token: string) =>
    request<void>("/api/auth/logout", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    }),
};
