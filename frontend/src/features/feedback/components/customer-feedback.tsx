import { useState, useEffect, type FormEvent } from "react";
import { Link } from "@tanstack/react-router";
import {
  AlertCircle,
  CalendarCheck,
  CheckCircle2,
  Clock,
  EyeOff,
  Loader2,
  LogIn,
  MessageSquareQuote,
  MessageSquareReply,
  PhoneCall,
  Receipt,
  RefreshCw,
  Send,
  Sparkles,
  Star,
  Utensils,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { PaginationControls } from "@/components/hv/PaginationControls";
import { formatVND } from "@/lib/restaurant-store";
import { useSession } from "@/lib/auth-session";
import { cn } from "@/lib/utils";
import {
  COMMENT_MAX_LENGTH,
  formatFeedbackDate,
  hasReply,
  reviewerName,
  type Feedback,
} from "../api/feedback-api";
import {
  useEligibleInvoices,
  useMyReviews,
  usePublicReviews,
  useSubmitFeedback,
} from "../hooks/use-feedback";
import { StarRating, StarRatingInput } from "./star-rating";

/** UC19: Khách hàng gửi đánh giá & số sao, xem lịch sử và đánh giá công khai. */
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
  const [rating, setRating] = useState<number>(0);
  const [comment, setComment] = useState<string>("");
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string>(initialInvoiceId || "");
  const [error, setError] = useState<string | null>(null);

  const eligibleList = eligibleQuery.data || [];

  // Tự động nhận diện đơn hàng cần đánh giá:
  // 1. Ưu tiên initialInvoiceId (khi khách bấm "Để lại đánh giá" từ Lịch sử đặt bàn)
  // 2. Nếu danh sách chỉ có duy nhất 1 đơn -> tự động chọn cho khách
  useEffect(() => {
    if (initialInvoiceId) {
      setSelectedInvoiceId(initialInvoiceId);
    } else if (!selectedInvoiceId && eligibleList.length === 1 && eligibleList[0]?.id) {
      setSelectedInvoiceId(eligibleList[0].id);
    }
  }, [initialInvoiceId, eligibleList, selectedInvoiceId]);

  const activeInvoice = eligibleList.find(
    (inv) => inv.id === selectedInvoiceId || inv.invoiceCode === selectedInvoiceId
  );

  // Form chỉ được kích hoạt (active) khi người dùng ĐÃ CHỌN một đơn hàng cụ thể
  const isFormActive = Boolean(selectedInvoiceId);

  const remaining = COMMENT_MAX_LENGTH - comment.length;

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();

    if (!selectedInvoiceId) {
      setError("Vui lòng click chọn một đơn hàng hoặc đặt bàn phía trên để đánh giá.");
      return;
    }
    if (rating < 1 || rating > 5) {
      setError("Vui lòng chọn số sao đánh giá (1–5 sao).");
      return;
    }
    if (comment.trim().length > COMMENT_MAX_LENGTH) {
      setError(`Nhận xét không được vượt quá ${COMMENT_MAX_LENGTH} ký tự.`);
      return;
    }

    setError(null);
    try {
      await submit.mutateAsync({ rating, comment, invoiceId: selectedInvoiceId });
      toast.success("Cảm ơn bạn đã gửi đánh giá trải nghiệm cho Nhà hàng Hương Việt!");
      setRating(0);
      setComment("");
      setSelectedInvoiceId("");
      void eligibleQuery.refetch();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Gửi đánh giá thất bại.");
    }
  };

  const hasNoEligible = !initialInvoiceId && !eligibleQuery.isLoading && eligibleList.length === 0;

  return (
    <section className="surface-card space-y-5 p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b pb-4">
        <div>
          <h2 className="flex items-center gap-2 text-xl font-semibold sm:text-2xl">
            <Star className="h-5 w-5 fill-amber-400 text-amber-400" />
            Đánh giá bữa ăn
          </h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Bao gồm các hóa đơn gắn với tài khoản của bạn, hóa đơn thanh toán bằng số điện thoại của bạn và các đơn đặt bàn hoàn tất thanh toán.
          </p>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => void eligibleQuery.refetch()}
          disabled={eligibleQuery.isFetching}
          className="h-8 text-xs text-muted-foreground"
          title="Làm mới danh sách hóa đơn / đặt bàn chưa đánh giá"
        >
          <RefreshCw className={cn("h-3.5 w-3.5 mr-1.5", eligibleQuery.isFetching && "animate-spin")} />
          Làm mới
        </Button>
      </div>

      {/* 1. KHU VỰC HIỂN THỊ DANH SÁCH ĐƠN HÀNG / ĐẶT BÀN CHƯA ĐÁNH GIÁ (UNREVIEWED ORDERS LIST) */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
            <Receipt className="h-4 w-4 text-primary" />
            Đơn hàng & Đặt bàn chờ đánh giá ({eligibleList.length})
          </label>
          {isFormActive && (
            <span className="text-[11px] text-emerald-600 dark:text-emerald-400 font-medium">
              Đã chọn 1 đơn
            </span>
          )}
        </div>

        {eligibleQuery.isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-16 w-full rounded-xl" />
            <Skeleton className="h-16 w-full rounded-xl" />
          </div>
        ) : hasNoEligible ? (
          <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-xs text-amber-900 dark:text-amber-200 space-y-1">
            <p className="font-semibold flex items-center gap-1.5 text-amber-800 dark:text-amber-300">
              <AlertCircle className="h-4 w-4 shrink-0 text-amber-600" />
              Chưa có đơn hàng nào chờ đánh giá
            </p>
            <p className="text-muted-foreground leading-relaxed pl-5.5">
              Hệ thống chỉ cho phép gửi đánh giá cho những đơn hàng / đặt bàn đã thanh toán xong (PAID). Vui lòng dùng bữa và hoàn tất thanh toán trước khi gửi phản hồi.
            </p>
          </div>
        ) : (
          <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
            {eligibleList.map((inv) => {
              const isSelected = selectedInvoiceId === inv.id || selectedInvoiceId === inv.invoiceCode;
              const isBookingOrder = inv.invoiceCode?.startsWith("HV-") || inv.tableNumber?.includes("Đặt");

              return (
                <div
                  key={inv.id}
                  role="button"
                  tabIndex={0}
                  onClick={() => {
                    setSelectedInvoiceId(inv.id);
                    setError(null);
                  }}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      setSelectedInvoiceId(inv.id);
                      setError(null);
                    }
                  }}
                  className={cn(
                    "w-full text-left p-3 rounded-xl border transition-all cursor-pointer flex items-center justify-between gap-3 select-none",
                    isSelected
                      ? "border-primary bg-primary/10 ring-2 ring-primary/20 shadow-sm"
                      : "border-border bg-card hover:border-primary/40 hover:bg-muted/30"
                  )}
                >
                  <div className="flex items-start gap-3 min-w-0">
                    {/* Radio / Selection Indicator */}
                    <div
                      className={cn(
                        "mt-1 flex h-4 w-4 shrink-0 items-center justify-center rounded-full border transition-all",
                        isSelected
                          ? "border-primary bg-primary text-primary-foreground"
                          : "border-muted-foreground/40 bg-background"
                      )}
                    >
                      {isSelected && <CheckCircle2 className="h-3.5 w-3.5 text-white" />}
                    </div>

                    <div className="min-w-0 space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono font-bold text-sm text-primary">
                          {inv.invoiceCode}
                        </span>
                        <Badge variant="outline" className="text-[11px] font-medium py-0 h-5">
                          {inv.tableNumber}
                        </Badge>
                        {isBookingOrder ? (
                          <Badge
                            variant="secondary"
                            className="text-[10px] bg-sky-500/10 text-sky-700 dark:text-sky-300 border border-sky-500/20 py-0 h-4.5 gap-1"
                          >
                            <CalendarCheck className="h-3 w-3" />
                            Đặt bàn
                          </Badge>
                        ) : (
                          <Badge
                            variant="secondary"
                            className="text-[10px] bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20 py-0 h-4.5 gap-1"
                          >
                            <PhoneCall className="h-3 w-3" />
                            Theo SĐT / POS
                          </Badge>
                        )}
                        <Badge
                          variant="secondary"
                          className="text-[10px] bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/20 py-0 h-4.5"
                        >
                          Đã thanh toán
                        </Badge>
                      </div>

                      <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          {formatFeedbackDate(inv.paidAt)}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="text-right shrink-0">
                    <p className="text-[11px] text-muted-foreground">Tổng tiền</p>
                    <p className="font-semibold tabular-nums text-foreground text-sm">
                      {formatVND(inv.finalAmount)}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Hướng dẫn khi chưa chọn đơn */}
        {!hasNoEligible && !isFormActive && eligibleList.length > 0 && (
          <p className="text-xs text-amber-600 dark:text-amber-400 flex items-center gap-1.5 animate-pulse font-medium">
            <Sparkles className="h-3.5 w-3.5 shrink-0" />
            Nhấp chọn một đơn hàng hoặc đặt bàn ở trên để kích hoạt form đánh giá bên dưới.
          </p>
        )}
      </div>

      {/* 2. FORM ĐÁNH GIÁ (KÍCH HOẠT KHI ĐÃ CHỌN ĐƠN HÀNG) */}
      <form onSubmit={handleSubmit} className="space-y-5 border-t pt-5" noValidate>
        {/* Banner xác nhận đơn đang chọn */}
        {activeInvoice && (
          <div className="rounded-lg border border-primary/20 bg-primary/5 p-3 text-xs flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <Utensils className="h-4 w-4 text-primary shrink-0" />
              <span>
                Đang viết đánh giá cho:{" "}
                <strong className="font-mono text-primary font-bold">{activeInvoice.invoiceCode}</strong> (
                {activeInvoice.tableNumber})
              </span>
            </div>
            <button
              type="button"
              onClick={() => setSelectedInvoiceId("")}
              className="text-[11px] text-muted-foreground hover:text-foreground underline cursor-pointer"
            >
              Chọn lại
            </button>
          </div>
        )}

        {/* Chọn số sao */}
        <div className={cn("space-y-2 transition-opacity", !isFormActive && "opacity-40 pointer-events-none")}>
          <label htmlFor="feedback-rating" className="text-sm font-medium">
            Mức độ hài lòng của quý khách <span className="text-destructive">*</span>
          </label>
          <StarRatingInput
            id="feedback-rating"
            value={rating}
            onChange={(value) => {
              setRating(value);
              setError(null);
            }}
            disabled={!isFormActive || submit.isPending}
          />
        </div>

        {/* Nhập nhận xét */}
        <div className={cn("space-y-1.5 transition-opacity", !isFormActive && "opacity-40 pointer-events-none")}>
          <label htmlFor="feedback-comment" className="text-sm font-medium">
            Nội dung nhận xét & góp ý
          </label>
          <Textarea
            id="feedback-comment"
            value={comment}
            onChange={(e) => setComment(e.target.value.slice(0, COMMENT_MAX_LENGTH))}
            placeholder={
              isFormActive
                ? "Chia sẻ cảm nhận về hương vị món ăn, không gian nhà hàng, thái độ phục vụ..."
                : "Vui lòng chọn đơn hàng hoặc đặt bàn phía trên để viết nhận xét..."
            }
            rows={4}
            maxLength={COMMENT_MAX_LENGTH}
            disabled={!isFormActive || submit.isPending}
          />
          <p className="text-right text-xs tabular-nums text-muted-foreground">
            Còn {remaining} ký tự
          </p>
        </div>

        {/* Thông báo lỗi nếu có */}
        {error && (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"
          >
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Nút gửi đánh giá */}
        <Button
          type="submit"
          className="w-full sm:w-auto font-medium"
          disabled={!isFormActive || submit.isPending}
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
      <div className="flex items-center justify-between gap-2 border-b pb-3">
        <h2 className="text-lg font-semibold">Lịch sử đánh giá của tôi</h2>
        {query.data && (
          <span className="text-xs text-muted-foreground font-medium">
            {query.data.length} đánh giá
          </span>
        )}
      </div>

      {query.isPending && <ReviewSkeletons count={2} />}
      {query.isError && (
        <ErrorRow message={query.error.message} onRetry={() => void query.refetch()} />
      )}
      {query.data && query.data.length === 0 && (
        <p className="text-sm text-muted-foreground py-4 text-center">Bạn chưa gửi đánh giá nào.</p>
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
  const [page, setPage] = useState<number>(0);
  const query = usePublicReviews(page, PUBLIC_PAGE_SIZE);

  return (
    <section className="surface-card space-y-4 p-5 sm:p-6">
      <div className="border-b pb-3">
        <h2 className="flex items-center gap-2 text-xl font-semibold sm:text-2xl">
          <MessageSquareQuote className="h-5 w-5 text-primary" />
          Khách hàng nói gì về Hương Việt
        </h2>
        {query.data && (
          <p className="mt-1 text-xs text-muted-foreground">
            {query.data.totalElements} đánh giá công khai từ thực khách
          </p>
        )}
      </div>

      {query.isPending && <ReviewSkeletons count={3} />}
      {query.isError && (
        <ErrorRow message={query.error.message} onRetry={() => void query.refetch()} />
      )}
      {query.data && query.data.content.length === 0 && (
        <p className="py-8 text-center text-sm text-muted-foreground">
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