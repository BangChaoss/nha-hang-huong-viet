import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import {
  AlertCircle,
  Eye,
  EyeOff,
  Loader2,
  MessageSquareMore,
  MessageSquareReply,
  MessagesSquare,
  Phone,
  Receipt,
  RefreshCw,
  Search,
  Star,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PaginationControls } from "@/components/hv/PaginationControls";
import { cn } from "@/lib/utils";
import {
  RATING_LABELS,
  REPLY_MAX_LENGTH,
  formatFeedbackDate,
  hasReply,
  reviewerName,
  type AdminFeedbackFilters,
  type Feedback,
  type FeedbackStats,
} from "../api/feedback-api";
import { useAdminFeedbacks, useFeedbackModeration, useFeedbackStats } from "../hooks/use-feedback";
import { StarRating } from "./star-rating";

const PAGE_SIZE = 10;
const SELECT_CLASS =
  "h-10 min-w-0 flex-1 rounded-md border border-input bg-background px-3 text-sm md:flex-none";

type TriState = "" | "true" | "false";

type Filters = {
  rating: string;
  isPublic: TriState;
  hasResponse: TriState;
  keyword: string;
};

const EMPTY_FILTERS: Filters = { rating: "", isPublic: "", hasResponse: "", keyword: "" };

function toBool(value: TriState): boolean | undefined {
  return value === "" ? undefined : value === "true";
}

function toApiFilters(filters: Filters, page: number): AdminFeedbackFilters {
  const result: AdminFeedbackFilters = { page, size: PAGE_SIZE };
  if (filters.rating) result.rating = Number(filters.rating);
  const isPublic = toBool(filters.isPublic);
  if (isPublic !== undefined) result.isPublic = isPublic;
  const hasResponse = toBool(filters.hasResponse);
  if (hasResponse !== undefined) result.hasResponse = hasResponse;
  if (filters.keyword.trim()) result.keyword = filters.keyword.trim();
  return result;
}

/** UC25: Quản lý xem, lọc, phản hồi và ẩn/hiện đánh giá của khách hàng. */
export function FeedbackManagement() {
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [keywordInput, setKeywordInput] = useState("");
  const [page, setPage] = useState(0);
  const [replyTarget, setReplyTarget] = useState<Feedback | null>(null);

  // Debounce ô tìm kiếm để không gọi API mỗi lần gõ phím.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setFilters((f) => (f.keyword === keywordInput ? f : { ...f, keyword: keywordInput }));
      setPage(0);
    }, 400);
    return () => window.clearTimeout(timer);
  }, [keywordInput]);

  const apiFilters = useMemo(() => toApiFilters(filters, page), [filters, page]);
  const list = useAdminFeedbacks(apiFilters);
  const stats = useFeedbackStats();
  const { visibility } = useFeedbackModeration();

  const updateFilter = <K extends keyof Filters>(key: K, value: Filters[K]) => {
    setFilters((f) => ({ ...f, [key]: value }));
    setPage(0);
  };

  const hasActiveFilters =
    filters.rating !== "" ||
    filters.isPublic !== "" ||
    filters.hasResponse !== "" ||
    keywordInput !== "";

  const resetFilters = () => {
    setFilters(EMPTY_FILTERS);
    setKeywordInput("");
    setPage(0);
  };

  const toggleVisibility = async (feedback: Feedback) => {
    const next = !feedback.isPublic;
    try {
      await visibility.mutateAsync({ id: feedback.id, isPublic: next });
      toast.success(
        next
          ? "Đã hiển thị lại đánh giá trên trang công khai."
          : "Đã ẩn đánh giá khỏi trang công khai.",
      );
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Không cập nhật được trạng thái hiển thị.");
    }
  };

  const pendingVisibilityId = visibility.isPending ? visibility.variables?.id : undefined;

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="flex items-center gap-2 text-2xl font-semibold">
            <MessagesSquare className="h-6 w-6 text-primary" />
            Quản lý đánh giá khách hàng
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Theo dõi mức độ hài lòng, phản hồi khách và ẩn các đánh giá vi phạm tiêu chuẩn.
          </p>
        </div>
        <Button
          variant="outline"
          onClick={() => {
            void list.refetch();
            void stats.refetch();
          }}
          disabled={list.isFetching || stats.isFetching}
        >
          <RefreshCw
            className={cn("mr-2 h-4 w-4", (list.isFetching || stats.isFetching) && "animate-spin")}
          />
          Làm mới
        </Button>
      </div>

      <StatsPanel
        stats={stats.data}
        isPending={stats.isPending}
        error={stats.isError ? stats.error.message : null}
        activeRating={filters.rating}
        onSelectRating={(star) => updateFilter("rating", filters.rating === star ? "" : star)}
        onShowUnreplied={() => updateFilter("hasResponse", "false")}
      />

      <div className="surface-card space-y-4 p-4 sm:p-5">
        <div className="flex flex-col gap-3 md:flex-row md:flex-wrap md:items-center">
          <div className="relative min-w-0 flex-1 md:min-w-64">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
              placeholder="Tìm tên khách, SĐT hoặc nội dung nhận xét"
              className="pl-9"
              aria-label="Tìm kiếm đánh giá"
            />
          </div>
          <div className="flex flex-wrap gap-3">
            <select
              value={filters.rating}
              onChange={(e) => updateFilter("rating", e.target.value)}
              aria-label="Lọc theo số sao"
              className={SELECT_CLASS}
            >
              <option value="">Mọi số sao</option>
              {[5, 4, 3, 2, 1].map((star) => (
                <option key={star} value={String(star)}>
                  {star} sao — {RATING_LABELS[star]}
                </option>
              ))}
            </select>
            <select
              value={filters.hasResponse}
              onChange={(e) => updateFilter("hasResponse", e.target.value as TriState)}
              aria-label="Lọc theo trạng thái phản hồi"
              className={SELECT_CLASS}
            >
              <option value="">Mọi trạng thái phản hồi</option>
              <option value="false">Chưa phản hồi</option>
              <option value="true">Đã phản hồi</option>
            </select>
            <select
              value={filters.isPublic}
              onChange={(e) => updateFilter("isPublic", e.target.value as TriState)}
              aria-label="Lọc theo trạng thái hiển thị"
              className={SELECT_CLASS}
            >
              <option value="">Mọi trạng thái hiển thị</option>
              <option value="true">Đang hiển thị</option>
              <option value="false">Đã ẩn</option>
            </select>
            {hasActiveFilters && (
              <Button variant="ghost" onClick={resetFilters}>
                <X className="mr-2 h-4 w-4" />
                Xoá bộ lọc
              </Button>
            )}
          </div>
        </div>

        {list.isPending && (
          <div className="space-y-3">
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} className="h-32 w-full" />
            ))}
          </div>
        )}

        {list.isError && (
          <div className="flex flex-col items-center gap-3 py-10 text-center">
            <AlertCircle className="h-8 w-8 text-destructive" />
            <p className="text-sm text-muted-foreground">{list.error.message}</p>
            <Button variant="outline" onClick={() => void list.refetch()}>
              <RefreshCw className="mr-2 h-4 w-4" />
              Thử lại
            </Button>
          </div>
        )}

        {list.data && list.data.content.length === 0 && (
          <div className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
            <MessageSquareMore className="h-8 w-8" />
            <p className="text-sm">
              {hasActiveFilters
                ? "Không có đánh giá nào khớp bộ lọc."
                : "Chưa có đánh giá nào từ khách hàng."}
            </p>
          </div>
        )}

        {list.data && list.data.content.length > 0 && (
          <>
            <p className="text-sm text-muted-foreground">
              Tìm thấy{" "}
              <span className="font-medium text-foreground">{list.data.totalElements}</span> đánh
              giá
            </p>
            <ul className={cn("space-y-3", list.isPlaceholderData && "opacity-60")}>
              {list.data.content.map((feedback) => (
                <li key={feedback.id}>
                  <AdminFeedbackCard
                    feedback={feedback}
                    onReply={() => setReplyTarget(feedback)}
                    onToggleVisibility={() => void toggleVisibility(feedback)}
                    visibilityPending={pendingVisibilityId === feedback.id}
                  />
                </li>
              ))}
            </ul>
            {list.data.totalPages > 1 && (
              <PaginationControls
                page={list.data.page}
                totalPages={list.data.totalPages}
                onPageChange={setPage}
              />
            )}
          </>
        )}
      </div>

      <ReplyDialog feedback={replyTarget} onClose={() => setReplyTarget(null)} />
    </section>
  );
}

function StatsPanel({
  stats,
  isPending,
  error,
  activeRating,
  onSelectRating,
  onShowUnreplied,
}: {
  stats: FeedbackStats | undefined;
  isPending: boolean;
  error: string | null;
  activeRating: string;
  onSelectRating: (star: string) => void;
  onShowUnreplied: () => void;
}) {
  if (isPending) {
    return (
      <div className="grid gap-4 md:grid-cols-3">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-36 w-full" />
        ))}
      </div>
    );
  }
  if (error || !stats) {
    return (
      <div className="surface-card flex items-center gap-2 p-4 text-sm text-destructive">
        <AlertCircle className="h-4 w-4" />
        {error ?? "Không tải được thống kê đánh giá."}
      </div>
    );
  }

  const maxCount = Math.max(1, ...[1, 2, 3, 4, 5].map((s) => stats.ratingCounts[String(s)] ?? 0));

  return (
    <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.6fr)]">
      <StatCard label="Điểm trung bình" icon={<Star className="h-5 w-5" />}>
        <p className="text-4xl font-semibold tabular-nums">
          {stats.averageRating.toFixed(1)}
          <span className="text-base font-normal text-muted-foreground"> / 5</span>
        </p>
        <StarRating value={stats.averageRating} size="md" className="mt-2" />
        <p className="mt-2 text-sm text-muted-foreground">Từ {stats.totalReviews} đánh giá</p>
      </StatCard>

      <StatCard label="Chưa phản hồi" icon={<MessageSquareReply className="h-5 w-5" />}>
        <p className="text-4xl font-semibold tabular-nums">{stats.unrepliedCount}</p>
        <p className="mt-2 text-sm text-muted-foreground">
          {stats.unrepliedCount > 0
            ? "Đánh giá đang chờ nhà hàng trả lời"
            : "Đã phản hồi tất cả đánh giá"}
        </p>
        {stats.unrepliedCount > 0 && (
          <Button variant="link" className="mt-1 h-auto p-0" onClick={onShowUnreplied}>
            Xem danh sách chưa phản hồi
          </Button>
        )}
      </StatCard>

      <StatCard label="Phân bổ số sao" icon={<Star className="h-5 w-5" />}>
        <ul className="space-y-1.5">
          {[5, 4, 3, 2, 1].map((star) => {
            const count = stats.ratingCounts[String(star)] ?? 0;
            const active = activeRating === String(star);
            return (
              <li key={star}>
                <button
                  type="button"
                  onClick={() => onSelectRating(String(star))}
                  aria-pressed={active}
                  title={active ? "Bỏ lọc theo số sao" : `Lọc đánh giá ${star} sao`}
                  className={cn(
                    "flex w-full items-center gap-3 rounded-md px-1.5 py-1 text-sm transition-colors hover:bg-muted",
                    active && "bg-primary/10",
                  )}
                >
                  <span className="flex w-10 shrink-0 items-center gap-1 tabular-nums">
                    {star}
                    <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
                  </span>
                  <span className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                    <span
                      className="block h-full rounded-full bg-amber-400"
                      style={{ width: `${(count / maxCount) * 100}%` }}
                    />
                  </span>
                  <span className="w-10 shrink-0 text-right tabular-nums text-muted-foreground">
                    {count}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      </StatCard>
    </div>
  );
}

function StatCard({
  label,
  icon,
  children,
}: {
  label: string;
  icon: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="surface-card p-5">
      <div className="mb-3 flex items-center justify-between text-muted-foreground">
        <p className="text-sm font-medium">{label}</p>
        <span className="text-primary">{icon}</span>
      </div>
      {children}
    </div>
  );
}

function AdminFeedbackCard({
  feedback,
  onReply,
  onToggleVisibility,
  visibilityPending,
}: {
  feedback: Feedback;
  onReply: () => void;
  onToggleVisibility: () => void;
  visibilityPending: boolean;
}) {
  const replied = hasReply(feedback);

  return (
    <article
      className={cn(
        "rounded-lg border bg-background/60 p-4",
        !feedback.isPublic && "border-dashed bg-muted/40",
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{reviewerName(feedback)}</span>
            <StarRating value={feedback.rating} />
            <span className="text-xs text-muted-foreground">{RATING_LABELS[feedback.rating]}</span>
          </div>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
            <span>{formatFeedbackDate(feedback.createdAt)}</span>
            {feedback.guestPhone && (
              <span className="inline-flex items-center gap-1">
                <Phone className="h-3 w-3" />
                {feedback.guestPhone}
              </span>
            )}
            {feedback.invoiceId && (
              <span className="inline-flex items-center gap-1 font-mono" title={feedback.invoiceId}>
                <Receipt className="h-3 w-3" />
                HĐ {feedback.invoiceId.slice(0, 8).toUpperCase()}
              </span>
            )}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusPill tone={replied ? "success" : "warning"}>
            {replied ? "Đã phản hồi" : "Chưa phản hồi"}
          </StatusPill>
          <StatusPill tone={feedback.isPublic ? "neutral" : "muted"}>
            {feedback.isPublic ? "Đang hiển thị" : "Đã ẩn"}
          </StatusPill>
        </div>
      </div>

      {feedback.comment?.trim() ? (
        <p className="mt-3 whitespace-pre-line break-words text-sm">{feedback.comment}</p>
      ) : (
        <p className="mt-3 text-sm italic text-muted-foreground">Khách không để lại nhận xét.</p>
      )}

      {replied && (
        <div className="mt-3 rounded-md border-l-2 border-primary bg-primary/5 p-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-primary">
            Phản hồi của nhà hàng
          </p>
          <p className="mt-1 whitespace-pre-line break-words text-sm">{feedback.staffResponse}</p>
        </div>
      )}

      <div className="mt-4 flex flex-wrap gap-2">
        <Button size="sm" variant={replied ? "outline" : "default"} onClick={onReply}>
          <MessageSquareReply className="mr-2 h-4 w-4" />
          {replied ? "Sửa phản hồi" : "Phản hồi"}
        </Button>
        <Button
          size="sm"
          variant="outline"
          onClick={onToggleVisibility}
          disabled={visibilityPending}
        >
          {visibilityPending ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : feedback.isPublic ? (
            <EyeOff className="mr-2 h-4 w-4" />
          ) : (
            <Eye className="mr-2 h-4 w-4" />
          )}
          {feedback.isPublic ? "Ẩn đánh giá" : "Hiển thị lại"}
        </Button>
      </div>
    </article>
  );
}

function StatusPill({
  tone,
  children,
}: {
  tone: "success" | "warning" | "neutral" | "muted";
  children: ReactNode;
}) {
  const toneClass = {
    success: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-400",
    warning: "bg-amber-500/15 text-amber-700 dark:text-amber-400",
    neutral: "bg-primary/10 text-primary",
    muted: "bg-muted text-muted-foreground",
  }[tone];
  return (
    <span className={cn("rounded-full px-2.5 py-1 text-xs font-medium", toneClass)}>
      {children}
    </span>
  );
}

function ReplyDialog({ feedback, onClose }: { feedback: Feedback | null; onClose: () => void }) {
  const { reply } = useFeedbackModeration();
  const [text, setText] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setText(feedback?.staffResponse ?? "");
    setError(null);
  }, [feedback]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!feedback) return;
    const trimmed = text.trim();
    if (!trimmed) {
      setError("Nội dung phản hồi không được để trống.");
      return;
    }
    if (trimmed.length > REPLY_MAX_LENGTH) {
      setError(`Phản hồi không được vượt quá ${REPLY_MAX_LENGTH} ký tự.`);
      return;
    }
    try {
      await reply.mutateAsync({ id: feedback.id, staffResponse: trimmed });
      toast.success("Đã gửi phản hồi tới khách hàng.");
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Gửi phản hồi thất bại.");
    }
  };

  return (
    <Dialog
      open={feedback !== null}
      onOpenChange={(open) => !open && !reply.isPending && onClose()}
    >
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {hasReply(feedback ?? { staffResponse: null }) ? "Sửa phản hồi" : "Phản hồi đánh giá"}
          </DialogTitle>
          <DialogDescription>
            Phản hồi sẽ hiển thị cho khách hàng bên dưới đánh giá của họ.
          </DialogDescription>
        </DialogHeader>
        {feedback && (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="rounded-md border bg-muted/40 p-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm font-medium">{reviewerName(feedback)}</span>
                <StarRating value={feedback.rating} />
              </div>
              <p className="mt-1 max-h-32 overflow-y-auto whitespace-pre-line break-words text-sm text-muted-foreground">
                {feedback.comment?.trim() || "Khách không để lại nhận xét."}
              </p>
            </div>
            <div className="space-y-1.5">
              <label htmlFor="feedback-reply" className="text-sm font-medium">
                Nội dung phản hồi
              </label>
              <Textarea
                id="feedback-reply"
                value={text}
                onChange={(e) => {
                  setText(e.target.value.slice(0, REPLY_MAX_LENGTH));
                  setError(null);
                }}
                rows={5}
                maxLength={REPLY_MAX_LENGTH}
                placeholder="Cảm ơn quý khách đã dành thời gian chia sẻ..."
                disabled={reply.isPending}
                autoFocus
              />
              <div className="flex justify-between gap-2 text-xs">
                <span className="text-destructive">{error}</span>
                <span className="tabular-nums text-muted-foreground">
                  {text.length}/{REPLY_MAX_LENGTH}
                </span>
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose} disabled={reply.isPending}>
                Huỷ
              </Button>
              <Button type="submit" disabled={reply.isPending}>
                {reply.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Gửi phản hồi
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
