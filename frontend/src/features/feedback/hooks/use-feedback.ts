import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  feedbackApi,
  type AdminFeedbackFilters,
  type EligibleInvoice,
  type Feedback,
  type FeedbackPage,
  type FeedbackStats,
  type SubmitFeedbackPayload,
} from "../api/feedback-api";

export const feedbackKeys = {
  all: ["feedback"] as const,
  mine: () => [...feedbackKeys.all, "mine"] as const,
  public: (page: number, size: number) => [...feedbackKeys.all, "public", page, size] as const,
  admin: () => [...feedbackKeys.all, "admin"] as const,
  adminList: (filters: AdminFeedbackFilters) => [...feedbackKeys.admin(), "list", filters] as const,
  adminStats: () => [...feedbackKeys.admin(), "stats"] as const,
};

/** Lịch sử đánh giá của khách đang đăng nhập. */
export function useMyReviews(enabled = true) {
  return useQuery<Feedback[], Error>({
    queryKey: feedbackKeys.mine(),
    queryFn: feedbackApi.getMyReviews,
    enabled,
  });
}

/** Danh sách các hoá đơn đã thanh toán chưa được đánh giá của khách. */
export function useEligibleInvoices(enabled = true) {
  return useQuery<EligibleInvoice[], Error>({
    queryKey: [...feedbackKeys.all, "eligible-invoices"] as const,
    queryFn: feedbackApi.getEligibleInvoices,
    enabled,
  });
}

export function usePublicReviews(page: number, size = 6) {
  return useQuery<FeedbackPage, Error>({
    queryKey: feedbackKeys.public(page, size),
    queryFn: () => feedbackApi.getPublic(page, size),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

export function useSubmitFeedback() {
  const queryClient = useQueryClient();
  return useMutation<Feedback, Error, SubmitFeedbackPayload>({
    mutationFn: feedbackApi.submit,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: feedbackKeys.all });
    },
  });
}

export function useAdminFeedbacks(filters: AdminFeedbackFilters) {
  return useQuery<FeedbackPage, Error>({
    queryKey: feedbackKeys.adminList(filters),
    queryFn: () => feedbackApi.listAdmin(filters),
    placeholderData: keepPreviousData,
  });
}

export function useFeedbackStats() {
  return useQuery<FeedbackStats, Error>({
    queryKey: feedbackKeys.adminStats(),
    queryFn: feedbackApi.getStats,
  });
}

/** Phản hồi khách và ẩn/hiện đánh giá; làm mới danh sách + thống kê sau khi thành công. */
export function useFeedbackModeration() {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: feedbackKeys.all });

  const reply = useMutation<Feedback, Error, { id: string; staffResponse: string }>({
    mutationFn: ({ id, staffResponse }) => feedbackApi.reply(id, staffResponse),
    onSuccess: () => void refresh(),
  });

  const visibility = useMutation<Feedback, Error, { id: string; isPublic: boolean }>({
    mutationFn: ({ id, isPublic }) => feedbackApi.setVisibility(id, isPublic),
    onSuccess: () => void refresh(),
  });

  return { reply, visibility };
}
