import { API_BASE } from "@/lib/table-api";

/**
 * Ảnh do backend phục vụ được lưu với đường dẫn tương đối (`/uploads/menu/...`),
 * nên cần ghép với địa chỉ API mới hiển thị được trên trình duyệt.
 *
 * Đường dẫn tuyệt đối (`https://...`) hoặc ảnh nhúng (`data:...`) giữ nguyên.
 */
export function resolveImageUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  const trimmed = url.trim();
  if (!trimmed) return null;
  if (/^(https?:)?\/\//i.test(trimmed) || trimmed.startsWith("data:")) return trimmed;
  return `${API_BASE}${trimmed.startsWith("/") ? "" : "/"}${trimmed}`;
}
