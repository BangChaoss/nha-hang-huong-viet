import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { formatVND } from "@/lib/restaurant-store";
import { resolveImageUrl } from "@/lib/image-url";
import type { MenuItemResponse } from "../api/menu-api";

interface MenuDetailDialogProps {
  item: MenuItemResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * UC-14: Xem chi tiết món ăn (luồng thay thế A2).
 * Hiển thị ảnh lớn, tên, danh mục, giá, mô tả đầy đủ và badge "Hết món" khi món không còn.
 */
export function MenuDetailDialog({ item, open, onOpenChange }: MenuDetailDialogProps) {
  if (!item) {
    return null;
  }

  const description = item.description?.trim() ?? "";
  const imageUrl = resolveImageUrl(item.imageUrl);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="font-display text-2xl leading-tight">{item.itemName}</DialogTitle>
          <DialogDescription>
            {item.categoryName}
            {description ? ` · ${description}` : ""}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="relative aspect-[16/9] w-full overflow-hidden rounded-lg bg-muted">
            {imageUrl ? (
              <img
                src={imageUrl}
                alt={item.itemName}
                loading="lazy"
                className={cn("h-full w-full object-cover", !item.isAvailable && "grayscale")}
              />
            ) : (
              <div className="flex h-full w-full items-center justify-center text-sm text-muted-foreground">
                Chưa có hình ảnh
              </div>
            )}
            {!item.isAvailable && (
              <span className="status-cancelled absolute left-3 top-3 rounded-full px-2.5 py-0.5 text-xs font-medium">
                Hết món
              </span>
            )}
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="font-medium tabular-nums text-primary">
              {formatVND(item.currentPrice)}
            </span>
            <Badge variant={item.isAvailable ? "secondary" : "outline"}>
              {item.isAvailable ? "Còn món" : "Hết món"}
            </Badge>
          </div>

          {description ? (
            <p className="whitespace-pre-line text-sm leading-relaxed text-muted-foreground">
              {description}
            </p>
          ) : (
            <p className="text-sm italic text-muted-foreground">
              Món ăn này chưa có mô tả chi tiết.
            </p>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
