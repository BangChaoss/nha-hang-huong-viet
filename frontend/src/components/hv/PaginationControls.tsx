import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import { cn } from "@/lib/utils";

export type PaginationControlsProps = {
  /** Trang hiện tại, đánh số từ 0. */
  page: number;
  /** Tổng số trang. */
  totalPages: number;
  /** Gọi khi người dùng chuyển trang. */
  onPageChange: (page: number) => void;
  className?: string;
};

/**
 * Tính danh sách số trang cần hiển thị.
 * Luôn hiện trang đầu, trang cuối và các trang lân cận trang hiện tại,
 * các khoảng cách bị bỏ qua thay bằng dấu "…".
 */
function buildPageItems(page: number, totalPages: number): (number | "ellipsis")[] {
  const total = Math.max(totalPages, 1);
  const current = Math.min(Math.max(page, 0), total - 1);

  const pages = new Set<number>([0, total - 1, current]);
  for (const offset of [-2, -1, 1, 2]) {
    const candidate = current + offset;
    if (candidate >= 0 && candidate < total) {
      pages.add(candidate);
    }
  }

  const sorted = [...pages].sort((a, b) => a - b);
  const items: (number | "ellipsis")[] = [];
  let previous = -1;

  for (const value of sorted) {
    if (previous >= 0 && value - previous > 1) {
      items.push("ellipsis");
    }
    items.push(value);
    previous = value;
  }

  return items;
}

/**
 * Bộ điều khiển phân trang dùng chung, dựng trên các primitive
 * `Pagination` của shadcn/ui.
 */
export function PaginationControls({
  page,
  totalPages,
  onPageChange,
  className,
}: PaginationControlsProps) {
  const total = Math.max(totalPages, 1);
  const current = Math.min(Math.max(page, 0), total - 1);
  const items = buildPageItems(current, total);

  return (
    <Pagination className={cn("w-auto", className)}>
      <PaginationContent>
        <PaginationItem>
          <PaginationPrevious
            href="#"
            aria-disabled={current === 0}
            className={current === 0 ? "pointer-events-none opacity-50" : undefined}
            onClick={(event) => {
              event.preventDefault();
              if (current > 0) onPageChange(current - 1);
            }}
          />
        </PaginationItem>

        {items.map((item, index) =>
          item === "ellipsis" ? (
            <PaginationItem key={`ellipsis-${index}`}>
              <PaginationEllipsis />
            </PaginationItem>
          ) : (
            <PaginationItem key={item}>
              <PaginationLink
                href="#"
                isActive={item === current}
                aria-current={item === current ? "page" : undefined}
                onClick={(event) => {
                  event.preventDefault();
                  onPageChange(item);
                }}
              >
                {item + 1}
              </PaginationLink>
            </PaginationItem>
          ),
        )}

        <PaginationItem>
          <PaginationNext
            href="#"
            aria-disabled={current >= total - 1}
            className={current >= total - 1 ? "pointer-events-none opacity-50" : undefined}
            onClick={(event) => {
              event.preventDefault();
              if (current < total - 1) onPageChange(current + 1);
            }}
          />
        </PaginationItem>
      </PaginationContent>
    </Pagination>
  );
}
