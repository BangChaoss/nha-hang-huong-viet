import { API_BASE } from "@/lib/table-api";
import { getSession } from "@/lib/auth-session";

/** Kết quả tải ảnh lên: đường dẫn công khai do backend phục vụ. */
export type ImageUploadResponse = {
  url: string;
  /** `cloudinary` khi ảnh được đưa lên Cloudinary, `local` khi lưu trên ổ đĩa. */
  backend: "cloudinary" | "local";
};

/** Định dạng ảnh được phép tải lên — khớp với kiểm tra phía backend. */
export const ACCEPTED_IMAGE_TYPES = [
  "image/jpeg",
  "image/png",
  "image/gif",
  "image/webp",
  "image/svg+xml",
];

export const ACCEPTED_IMAGE_EXTENSIONS = ["jpg", "jpeg", "png", "gif", "webp", "svg"];

/** Dung lượng tối đa cho một ảnh (5 MB) — khớp với `app.upload.max-file-size-bytes`. */
export const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

function hasAcceptedExtension(fileName: string): boolean {
  const dot = fileName.lastIndexOf(".");
  if (dot < 0) return false;
  const extension = fileName.slice(dot + 1).toLowerCase();
  return ACCEPTED_IMAGE_EXTENSIONS.includes(extension);
}

export const uploadApi = {
  /**
   * Tải ảnh lên máy chủ và trả về đường dẫn công khai để lưu vào món ăn.
   */
  uploadImage: async (file: File): Promise<ImageUploadResponse> => {
    if (!ACCEPTED_IMAGE_TYPES.includes(file.type) || !hasAcceptedExtension(file.name)) {
      throw new Error("Chỉ chấp nhận ảnh có định dạng JPG, PNG, GIF, WEBP hoặc SVG.");
    }
    if (file.size > MAX_IMAGE_SIZE_BYTES) {
      throw new Error("Ảnh vượt quá dung lượng 5 MB cho phép.");
    }

    const form = new FormData();
    form.append("file", file);

    const token = getSession()?.token;
    const response = await fetch(`${API_BASE}/api/admin/uploads/images`, {
      method: "POST",
      body: form,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });

    if (!response.ok) {
      let message = `Tải ảnh lên thất bại (${response.status})`;
      try {
        const body = (await response.json()) as { message?: string; detail?: string } | null;
        if (body) message = body.message ?? body.detail ?? message;
      } catch {
        // Một số phản hồi lỗi không có nội dung JSON.
      }
      throw new Error(message);
    }

    const body = (await response.json()) as ImageUploadResponse;
    return body;
  },
};
