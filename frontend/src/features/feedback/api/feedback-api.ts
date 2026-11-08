import { API_BASE } from "@/lib/table-api";
import { getSession } from "@/lib/auth-session";

/** Payload của `FeedbackResponse` từ backend (UC19/UC25). */
export type Feedback = {
  id: string;
  customerId: string | null;
  guestName: string | null;
  guestPhone: string | null;
  invoiceId: string | null;
  rating: number;
  comment: string | null;
  staffResponse: string | null;
  isPublic: boolean;
  /** ISO `yyyy-MM-ddTHH:mm:ss` (LocalDateTime, không có múi giờ). */
  createdAt: string;
};

export type FeedbackPage = {
  content: Feedback[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};

/** Thống kê đánh giá cho Quản lý. `ratingCounts` có khoá "1".."5". */
export type FeedbackStats = {
  totalReviews: number;
  averageRating: number;
  ratingCounts: Record<string, number>;
  unrepliedCount: number;
};

export type SubmitFeedbackPayload = {
  rating: number;
  comment?: string;
  invoiceId?: string;
};

export type AdminFeedbackFilters = {
  rating?: number;
  isPublic?: boolean;
  hasResponse?: boolean;
  keyword?: string;
  page?: number;
  size?: number;
};

export const COMMENT_MAX_LENGTH = 1000;
export const REPLY_MAX_LENGTH = 2000;

export const RATING_LABELS: Record<number, string> = {
  1: "Rất tệ",
  2: "Chưa hài lòng",
  3: "Bình thường",
  4: "Hài lòng",
  5: "Tuyệt vời",
};

/** Lỗi API kèm mã HTTP để giao diện xử lý riêng (409 trùng hoá đơn, 429 cooldown...). */
export class FeedbackApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "FeedbackApiError";
    this.status = status;
  }
}

function defaultMessage(status: number): string {
  if (status === 401) return "Vui lòng đăng nhập để tiếp tục.";
  if (status === 403) return "Bạn không có quyền thực hiện thao tác này.";
  if (status === 404) return "Không tìm thấy dữ liệu yêu cầu.";
  return `Yêu cầu thất bại (${status})`;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T | null> {
  const token = getSession()?.token;
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });

  if (!response.ok) {
    let message = defaultMessage(response.status);
    try {
      const body = (await response.json()) as { message?: string; detail?: string } | null;
      if (body) message = body.message ?? body.detail ?? message;
    } catch {
      // Body không phải JSON
    }
    throw new FeedbackApiError(response.status, message);
  }

  if (response.status === 204) return null;
  return (await response.json()) as T;
}

async function requestBody<T>(path: string, init: RequestInit = {}): Promise<T> {
  const data = await request<T>(path, init);
  if (data === null) throw new FeedbackApiError(204, "Máy chủ không trả về dữ liệu.");
  return data;
}

function buildAdminQuery(filters: AdminFeedbackFilters): string {
  const search = new URLSearchParams();
  if (filters.rating !== undefined) search.append("rating", String(filters.rating));
  if (filters.isPublic !== undefined) search.append("isPublic", String(filters.isPublic));
  if (filters.hasResponse !== undefined) search.append("hasResponse", String(filters.hasResponse));
  const keyword = filters.keyword?.trim();
  if (keyword) search.append("keyword", keyword);
  search.append("page", String(filters.page ?? 0));
  search.append("size", String(filters.size ?? 10));
  return search.toString();
}

export const feedbackApi = {
  /** UC19: khách hàng gửi đánh giá & số sao. */
  submit: (payload: SubmitFeedbackPayload) => {
    const comment = payload.comment?.trim();
    const invoiceId = payload.invoiceId?.trim();
    return requestBody<Feedback>("/api/feedbacks", {
      method: "POST",
      body: JSON.stringify({
        rating: payload.rating,
        comment: comment ?? "",
        invoiceId: invoiceId || null,
      }),
    });
  },

  /** Lịch sử đánh giá của khách đang đăng nhập (gồm cả đánh giá bị ẩn — BR-47). */
  getMyReviews: () => requestBody<Feedback[]>("/api/feedbacks/my-reviews"),

  /** Đánh giá của một hoá đơn; `null` nếu hoá đơn chưa được đánh giá (204). */
  getByInvoice: (invoiceId: string) =>
    request<Feedback>(`/api/feedbacks/invoice/${encodeURIComponent(invoiceId.trim())}`),

  /** Đánh giá công khai cho trang khách hàng. */
  getPublic: (page = 0, size = 6) =>
    requestBody<FeedbackPage>(`/api/feedbacks/public?page=${page}&size=${size}`),

  /** UC25: danh sách đánh giá có bộ lọc cho Quản lý. */
  listAdmin: (filters: AdminFeedbackFilters) =>
    requestBody<FeedbackPage>(`/api/admin/feedbacks?${buildAdminQuery(filters)}`),

  getStats: () => requestBody<FeedbackStats>("/api/admin/feedbacks/stats"),

  reply: (id: string, staffResponse: string) =>
    requestBody<Feedback>(`/api/admin/feedbacks/${encodeURIComponent(id)}/reply`, {
      method: "PATCH",
      body: JSON.stringify({ staffResponse: staffResponse.trim() }),
    }),

  setVisibility: (id: string, isPublic: boolean) =>
    requestBody<Feedback>(`/api/admin/feedbacks/${encodeURIComponent(id)}/visibility`, {
      method: "PATCH",
      body: JSON.stringify({ isPublic }),
    }),
};

/** Định dạng ngày giờ backend (LocalDateTime) theo kiểu Việt Nam. */
export function formatFeedbackDate(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

/** Tên hiển thị của người đánh giá (ẩn bớt nếu không có). */
export function reviewerName(feedback: Pick<Feedback, "guestName">): string {
  return feedback.guestName?.trim() || "Khách hàng";
}

export function hasReply(feedback: Pick<Feedback, "staffResponse">): boolean {
  return Boolean(feedback.staffResponse?.trim());
}
