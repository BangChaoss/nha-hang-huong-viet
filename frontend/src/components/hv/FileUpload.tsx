import { useRef, useState, type DragEvent } from "react";
import { ImagePlus, Loader2, Trash2, Upload } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { resolveImageUrl } from "@/lib/image-url";
import { uploadApi } from "@/lib/upload-api";

export type FileUploadProps = {
  /** Đường dẫn ảnh hiện tại (đường dẫn công khai do backend trả về). */
  value: string | null | undefined;
  /** Được gọi với đường dẫn mới, hoặc `null` khi người dùng xoá ảnh. */
  onChange: (url: string | null) => void;
  label?: string;
  hint?: string;
  disabled?: boolean;
  className?: string;
};

/**
 * Component chọn và tải ảnh lên từ máy người dùng.
 *
 * Dùng chung cho mọi nơi cần ảnh (hiện tại là ảnh món ăn trong màn quản lý
 * thực đơn). Ảnh được tải lên ngay khi chọn, giá trị trả về là đường dẫn
 * công khai để lưu vào bản ghi.
 */
export function FileUpload({
  value,
  onChange,
  label = "Hình ảnh",
  hint = "JPG, PNG, GIF, WEBP hoặc SVG — tối đa 5 MB",
  disabled = false,
  className,
}: FileUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const preview = resolveImageUrl(value);

  const handleFile = async (file: File | undefined) => {
    if (!file) return;
    setIsUploading(true);
    try {
      const result = await uploadApi.uploadImage(file);
      onChange(result.url);
      toast.success(
        result.backend === "cloudinary" ? "Đã tải ảnh lên Cloudinary." : "Đã tải ảnh lên máy chủ.",
      );
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Không tải được ảnh lên.");
    } finally {
      setIsUploading(false);
    }
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragging(false);
    if (disabled) return;
    void handleFile(event.dataTransfer.files?.[0]);
  };

  return (
    <div className={cn("space-y-1.5", className)}>
      <Label>{label}</Label>

      <div
        className={cn(
          "flex flex-wrap items-center gap-4 rounded-lg border border-dashed p-3 transition",
          isDragging ? "border-primary bg-primary/5" : "border-border",
          disabled && "opacity-60",
        )}
        onDragOver={(event) => {
          event.preventDefault();
          if (!disabled) setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
      >
        <div className="h-24 w-32 shrink-0 overflow-hidden rounded-md bg-muted">
          {preview ? (
            <img src={preview} alt="" className="h-full w-full object-cover" />
          ) : (
            <div className="flex h-full w-full items-center justify-center">
              <ImagePlus className="h-6 w-6 text-muted-foreground" />
            </div>
          )}
        </div>

        <div className="min-w-[12rem] flex-1 space-y-2">
          <input
            ref={inputRef}
            type="file"
            accept="image/png,image/jpeg,image/gif,image/webp,image/svg+xml"
            className="hidden"
            onChange={(event) => {
              void handleFile(event.target.files?.[0]);
              // Cho phép chọn lại đúng tệp vừa chọn nếu người dùng muốn đổi ý.
              event.target.value = "";
            }}
          />

          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={disabled || isUploading}
              onClick={() => inputRef.current?.click()}
            >
              {isUploading ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Upload className="mr-2 h-4 w-4" />
              )}
              {isUploading ? "Đang tải lên…" : "Chọn ảnh từ máy"}
            </Button>

            {value && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={disabled}
                onClick={() => onChange(null)}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                Xoá ảnh
              </Button>
            )}
          </div>

          <p className="text-xs text-muted-foreground">{hint}</p>
          {value && <p className="truncate text-xs text-muted-foreground">{value}</p>}
        </div>
      </div>
    </div>
  );
}
