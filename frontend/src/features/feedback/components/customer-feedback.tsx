import { useState, useEffect, type FormEvent } from "react";
import { Link } from "@tanstack/react-router";
import {
  AlertCircle,
  CheckCircle2,
  EyeOff,
  Loader2,
  LogIn,
  MessageSquareQuote,
  MessageSquareReply,
  Receipt,
  RefreshCw,
  Send,
  Star,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { PaginationControls } from "@/components/hv/PaginationControls";
import { formatVND } from "@/lib/restaurant-store";
import { useSession } from "@/lib/auth-session";
import {
  COMMENT_MAX_LENGTH,
  formatFeedbackDate,
  hasReply,
  reviewerName,
  type EligibleInvoice,
  type Feedback,
} from "../api/feedback-api";
import {
  useEligibleInvoices,
  useMyReviews,
  usePublicReviews,
  useSubmitFeedback,
} from "../hooks/use-feedback";
import { StarRating, StarRatingInput } from "./star-rating";

/** UC19: khách gửi đánh giá & số sao, xem lịch sử và đánh giá công khai. */
export function CustomerFeedback({ initialInvoiceId }: { initialInvoiceId?: string | undefined }) {
  const { session, ready } = useSession();
  const isAuthenticated = Boolean(session?.token);

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <div className="space-y-6">
        {!ready ? (
          <div className="surface-card flex justify-center p-10 text-muted-foreground">
            <Loader2 className="h-6 w-6 animate-spin" />
          </div>
        ) : isAuthenticated ? (
          <>
            <SubmitFeedbackForm initialInvoiceId={initialInvoiceId} />
            <MyReviews />
          </>
        ) : (
          <LoginPrompt />
        )}
      </div>
      <PublicReviews />
    </div>
  );
}

function LoginPrompt() {
  return (
    <section className="surface-card space-y-4 p-6 text-center">
      <Star className="mx-auto h-8 w-8 fill-amber-400 text-amber-400" />
      <div>
        <h2 className="text-xl font-semibold">Chia sẻ trải nghiệm của bạn</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Vui lòng đăng nhập để gửi đánh giá và theo dõi phản hồi từ nhà hàng.
        </p>
      </div>
      <Button asChild>
        <Link to="/login">
          <LogIn className="mr-2 h-4 w-4" />
          Đăng nhập để đánh giá
        </Link>
      </Button>
    </section>
  );
}

function SubmitFeedbackForm({ initialInvoiceId }: { initialInvoiceId?: string | undefined }) {
  const submit = useSubmitFeedback();
  const eligibleQuery = useEligibleInvoices();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string>(initialInvoiceId || "");
  const [error, setError] = useState<string | null>(null);

  const eligibleList = eligibleQuery.data || [];

  // Tự động gán mã hoá đơn hợp lệ đầu tiên nếu chưa có
  useEffect(() => {
    if (initialInvoiceId) {
      setSelectedInvoiceId(initialInvoiceId);
    } else if (!selectedInvoiceId && eligibleList.length > 0 && eligibleList[0]?.id) {
      setSelectedInvoiceId(eligibleList[0].id);
    }
  }, [initialInvoiceId, eligibleList, selectedInvoiceId]);

  const activeInvoice = eligibleList.find(
    (inv) => inv.id === selectedInvoiceId || inv.invoiceCode === selectedInvoiceId
  );

  const remaining = COMMENT_MAX_LENGTH - comment.length;

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (rating < 1 || rating > 5) {
      setError("Vui lòng chọn số sao đánh giá (1–5).");
      return;
    }
    if (comment.trim().length > COMMENT_MAX_LENGTH) {
      setError(`Nhận xét không được vượt quá ${COMMENT_MAX_LENGTH} ký tự.`);
      return;
    }
    if (!selectedInvoiceId) {
      setError("Không tìm thấy hoá đơn đã thanh toán để gửi đánh giá.");
      return;
    }
    setError(null);
    try {
      await submit.mutateAsync({ rating, comment, invoiceId: selectedInvoiceId });
      toast.success("Cảm ơn bạn đã gửi đánh giá cho Hương Việt!");
      setRating(0);
      setComment("");
      void eligibleQuery.refetch();
    } catch (err) {
      // 409: hoá đơn đã đánh giá, 429: gửi quá nhanh, 400/404: hoá đơn không hợp lệ...
      setError(err instanceof Error ? err.message : "Gửi đánh giá thất bại.");
    }
  };

  const hasNoEligible = !initialInvoiceId && !eligibleQuery.isLoading && eligibleList.length === 0;

  return (
    <section className="surface-card space-y-5 p-5 sm:p-6">
      <div>
        <h2 className="flex items-center gap-2 text-xl font-semibold sm:text-2xl">
          <Star className="h-5 w-5 fill-amber-400 text-amber-400" />
          Đánh giá bữa ăn
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Ý kiến của bạn giúp Hương Việt phục vụ tốt hơn mỗi ngày.
        </p>
      </div>

      <form className="space-y-5" onSubmit={handleSubmit} noValidate>
        {/* Thông tin hoá đơn đã thanh toán (Tự động lấy, ẩn ô nhập tay) */}
        {activeInvoice ? (
          <div className="rounded-lg border bg-muted/30 p-3.5 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Receipt className="h-4 w-4 text-primary shrink-0" />
                <span className="text-muted-foreground text-xs">Đang đánh giá hoá đơn:</span>
                <span className="font-mono font-bold text-primary">{activeInvoice.invoiceCode}</span>
              </div>
              <span className="inline-flex items-center rounded-full bg-emerald-500/10 px-2 py-0.5 text-xs font-medium text-emerald-600 border border-emerald-500/20">
                Đã thanh toán (PAID)
              </span>
            </div>
            <div className="mt-1.5 flex items-center gap-3 text-xs text-muted-foreground pl-6">
              <span>{activeInvoice.tableNumber}</span>
              <span>•</span>
              <span>{formatVND(activeInvoice.finalAmount)}</span>
              <span>•</span>
              <span>{formatFeedbackDate(activeInvoice.paidAt)}</span>
            </div>

            {eligibleList.length > 1 && (
              <div className="mt-3 pt-2.5 border-t text-xs">
                <span className="text-muted-foreground mr-2">Chọn hoá đơn khác:</span>
                <select
                  value={selectedInvoiceId}
                  onChange={(e) => setSelectedInvoiceId(e.target.value)}
                  className="rounded border bg-background px-2 py-1 text-xs"
                >
                  {eligibleList.map((inv) => (
                    <option key={inv.id} value={inv.id}>
                      {inv.invoiceCode} ({inv.tableNumber} - {formatVND(inv.finalAmount)})
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>
        ) : initialInvoiceId ? (
          <div className="rounded-lg border bg-muted/30 p-3 text-xs text-muted-foreground flex items-center gap-2">
            <Receipt className="h-4 w-4 text-primary shrink-0" />
            <span>Mã hoá đơn liên kết: <strong className="font-mono text-primary">{initialInvoiceId}</strong></span>
          </div>
        ) : hasNoEligible ? (
          <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3.5 text-xs text-amber-800 dark:text-amber-300">
            <p className="font-semibold">Chưa có hoá đơn hoàn tất thanh toán</p>
            <p className="mt-1 text-muted-foreground">
              Hệ thống chỉ cho phép gửi đánh giá cho những đơn hàng / đặt bàn đã thanh toán xong (PAID). Vui lòng dùng bữa và thanh toán trước khi gửi phản hồi.
            </p>
          </div>
        ) : eligibleQuery.isLoading ? (
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            <span>Đang kiểm tra hoá đơn thanh toán...</span>
          </div>
        ) : null}

        <div className="space-y-2">
          <label htmlFor="feedback-rating" className="text-sm font-medium">
            Mức độ hài lòng <span className="text-destructive">*</span>
          </label>
          <StarRatingInput
            id="feedback-rating"
            value={rating}
            onChange={(value) => {
              setRating(value);
              setError(null);
            }}
            disabled={submit.isPending || hasNoEligible}
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="feedback-comment" className="text-sm font-medium">
            Nhận xét
          </label>
          <Textarea
            id="feedback-comment"
            value={comment}
            onChange={(e) => setComment(e.target.value.slice(0, COMMENT_MAX_LENGTH))}
            placeholder="Món ăn, không gian, thái độ phục vụ..."
            rows={5}
            maxLength={COMMENT_MAX_LENGTH}
            disabled={submit.isPending || hasNoEligible}
          />
          <p className="text-right text-xs tabular-nums text-muted-foreground">
            Còn {remaining} ký tự
          </p>
        </div>

        {error && (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"
          >
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <Button
          type="submit"
          className="w-full sm:w-auto"
          disabled={submit.isPending || hasNoEligible}
        >
          {submit.isPending ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <Send className="mr-2 h-4 w-4" />
          )}
          Gửi đánh giá
        </Button>
      </form>
    </section>
  );
}

function MyReviews() {
  const query = useMyReviews();

  return (
    <section className="surface-card space-y-4 p-5 sm:p-6">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">Đánh giá của tôi</h2>
        {query.data && (
          <span className="text-sm text-muted-foreground">{query.data.length} đánh giá</span>
        )}
      </div>

      {query.isPending && <ReviewSkeletons count={2} />}
      {query.isError && (
        <ErrorRow message={query.error.message} onRetry={() => void query.refetch()} />
      )}
      {query.data && query.data.length === 0 && (
        <p className="text-sm text-muted-foreground">Bạn chưa gửi đánh giá nào.</p>
      )}
      {query.data && query.data.length > 0 && (
        <ul className="space-y-3">
          {query.data.map((feedback) => (
            <li key={feedback.id}>
              <ReviewCard feedback={feedback} showVisibility />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

const PUBLIC_PAGE_SIZE = 6;

function PublicReviews() {
  const [page, setPage] = useState(0);
  const query = usePublicReviews(page, PUBLIC_PAGE_SIZE);

  return (
    <section className="surface-card space-y-4 p-5 sm:p-6">
      <div>
        <h2 className="flex items-center gap-2 text-xl font-semibold sm:text-2xl">
          <MessageSquareQuote className="h-5 w-5 text-primary" />
          Khách hàng nói gì về Hương Việt
        </h2>
        {query.data && (
          <p className="mt-1 text-sm text-muted-foreground">
            {query.data.totalElements} đánh giá công khai
          </p>
        )}
      </div>

      {query.isPending && <ReviewSkeletons count={3} />}
      {query.isError && (
        <ErrorRow message={query.error.message} onRetry={() => void query.refetch()} />
      )}
      {query.data && query.data.content.length === 0 && (
        <p className="py-6 text-center text-sm text-muted-foreground">
          Chưa có đánh giá nào. Hãy là người đầu tiên chia sẻ cảm nhận!
        </p>
      )}
      {query.data && query.data.content.length > 0 && (
        <>
          <ul className="space-y-3">
            {query.data.content.map((feedback) => (
              <li key={feedback.id}>
                <ReviewCard feedback={feedback} showAuthor />
              </li>
            ))}
          </ul>
          {query.data.totalPages > 1 && (
            <PaginationControls
              page={query.data.page}
              totalPages={query.data.totalPages}
              onPageChange={setPage}
            />
          )}
        </>
      )}
    </section>
  );
}

function ReviewCard({
  feedback,
  showAuthor = false,
  showVisibility = false,
}: {
  feedback: Feedback;
  showAuthor?: boolean;
  showVisibility?: boolean;
}) {
  return (
    <article className="rounded-lg border bg-background/60 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2">
          {showAuthor && <span className="truncate font-medium">{reviewerName(feedback)}</span>}
          <StarRating value={feedback.rating} />
        </div>
        <span className="text-xs text-muted-foreground">
          {formatFeedbackDate(feedback.createdAt)}
        </span>
      </div>

      {feedback.comment?.trim() ? (
        <p className="mt-2 whitespace-pre-line break-words text-sm">{feedback.comment}</p>
      ) : (
        <p className="mt-2 text-sm italic text-muted-foreground">Không có nhận xét.</p>
      )}

      {showVisibility && !feedback.isPublic && (
        <p className="mt-2 inline-flex items-center gap-1.5 rounded-full bg-muted px-2.5 py-1 text-xs text-muted-foreground">
          <EyeOff className="h-3.5 w-3.5" />
          Đánh giá đang được ẩn khỏi trang công khai
        </p>
      )}

      {hasReply(feedback) && (
        <div className="mt-3 rounded-md border-l-2 border-primary bg-primary/5 p-3">
          <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-primary">
            <MessageSquareReply className="h-3.5 w-3.5" />
            Phản hồi từ nhà hàng
          </p>
          <p className="mt-1 whitespace-pre-line break-words text-sm">{feedback.staffResponse}</p>
        </div>
      )}
    </article>
  );
}

function ReviewSkeletons({ count }: { count: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} className="h-24 w-full" />
      ))}
    </div>
  );
}

function ErrorRow({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-sm text-destructive">
      <AlertCircle className="h-4 w-4" />
      <span>{message}</span>
      <Button variant="ghost" size="sm" onClick={onRetry}>
        <RefreshCw className="mr-2 h-4 w-4" />
        Thử lại
      </Button>
    </div>
  );
}
