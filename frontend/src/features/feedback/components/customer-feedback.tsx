import { useState, type FormEvent } from "react";
import { Link } from "@tanstack/react-router";
import {
  AlertCircle,
  EyeOff,
  Loader2,
  LogIn,
  MessageSquareQuote,
  MessageSquareReply,
  RefreshCw,
  Send,
  Star,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { PaginationControls } from "@/components/hv/PaginationControls";
import { useSession } from "@/lib/auth-session";
import {
  COMMENT_MAX_LENGTH,
  formatFeedbackDate,
  hasReply,
  reviewerName,
  type Feedback,
} from "../api/feedback-api";
import { useMyReviews, usePublicReviews, useSubmitFeedback } from "../hooks/use-feedback";
import { StarRating, StarRatingInput } from "./star-rating";

/** UC19: khách gửi đánh giá & số sao, xem lịch sử và đánh giá công khai. */
export function CustomerFeedback() {
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
            <SubmitFeedbackForm />
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

function SubmitFeedbackForm() {
  const submit = useSubmitFeedback();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [invoiceId, setInvoiceId] = useState("");
  const [error, setError] = useState<string | null>(null);

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
    setError(null);
    try {
      await submit.mutateAsync({ rating, comment, invoiceId });
      toast.success("Cảm ơn bạn đã gửi đánh giá cho Hương Việt!");
      setRating(0);
      setComment("");
      setInvoiceId("");
    } catch (err) {
      // 409: hoá đơn đã đánh giá, 429: gửi quá nhanh, 400/404: hoá đơn không hợp lệ...
      setError(err instanceof Error ? err.message : "Gửi đánh giá thất bại.");
    }
  };

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
            disabled={submit.isPending}
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
            disabled={submit.isPending}
          />
          <p className="text-right text-xs tabular-nums text-muted-foreground">
            Còn {remaining} ký tự
          </p>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="feedback-invoice" className="text-sm font-medium">
            Mã hoá đơn <span className="font-normal text-muted-foreground">(không bắt buộc)</span>
          </label>
          <Input
            id="feedback-invoice"
            value={invoiceId}
            onChange={(e) => setInvoiceId(e.target.value)}
            placeholder="VD: HV-20261108193000-1A2B3C4D"
            maxLength={50}
            disabled={submit.isPending}
          />
          <p className="text-xs text-muted-foreground">
            Mã in trên hoá đơn thanh toán. Mỗi hoá đơn chỉ được đánh giá một lần.
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

        <Button type="submit" className="w-full sm:w-auto" disabled={submit.isPending}>
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
