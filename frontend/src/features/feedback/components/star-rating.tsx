import { useState, type KeyboardEvent } from "react";
import { Star } from "lucide-react";
import { cn } from "@/lib/utils";
import { RATING_LABELS } from "../api/feedback-api";

const STARS = [1, 2, 3, 4, 5];

/** Hiển thị số sao (chỉ đọc). */
export function StarRating({
  value,
  size = "sm",
  className,
}: {
  value: number;
  size?: "sm" | "md";
  className?: string;
}) {
  const iconSize = size === "md" ? "h-5 w-5" : "h-4 w-4";
  return (
    <span
      className={cn("inline-flex items-center gap-0.5", className)}
      role="img"
      aria-label={`${value} trên 5 sao`}
    >
      {STARS.map((star) => (
        <Star
          key={star}
          aria-hidden="true"
          className={cn(
            iconSize,
            star <= Math.round(value)
              ? "fill-amber-400 text-amber-400"
              : "text-muted-foreground/40",
          )}
        />
      ))}
    </span>
  );
}

/** Chọn số sao 1–5, hỗ trợ chuột (hover xem trước) và bàn phím (mũi tên). */
export function StarRatingInput({
  value,
  onChange,
  disabled,
  id,
}: {
  value: number;
  onChange: (value: number) => void;
  disabled?: boolean;
  id?: string;
}) {
  const [hover, setHover] = useState(0);
  const shown = hover || value;

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (disabled) return;
    if (event.key === "ArrowRight" || event.key === "ArrowUp") {
      event.preventDefault();
      onChange(Math.min(5, (value || 0) + 1));
    } else if (event.key === "ArrowLeft" || event.key === "ArrowDown") {
      event.preventDefault();
      onChange(Math.max(1, (value || 1) - 1));
    }
  };

  return (
    <div className="flex flex-wrap items-center gap-3">
      <div
        id={id}
        role="radiogroup"
        aria-label="Chọn số sao đánh giá"
        tabIndex={disabled ? -1 : 0}
        onKeyDown={handleKeyDown}
        onMouseLeave={() => setHover(0)}
        className="inline-flex gap-1 rounded-md outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        {STARS.map((star) => (
          <button
            key={star}
            type="button"
            role="radio"
            aria-checked={value === star}
            aria-label={`${star} sao — ${RATING_LABELS[star]}`}
            tabIndex={-1}
            disabled={disabled}
            onClick={() => onChange(star)}
            onMouseEnter={() => setHover(star)}
            className="rounded p-0.5 transition-transform hover:scale-110 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <Star
              aria-hidden="true"
              className={cn(
                "h-8 w-8",
                star <= shown ? "fill-amber-400 text-amber-400" : "text-muted-foreground/40",
              )}
            />
          </button>
        ))}
      </div>
      <span className="text-sm font-medium text-muted-foreground" aria-live="polite">
        {shown ? RATING_LABELS[shown] : "Chưa chọn"}
      </span>
    </div>
  );
}
