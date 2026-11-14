import { getSession } from "@/lib/auth-session";

/** Header backend gắn vào response khi access token thuộc về tài khoản đã bị khóa. */
const ACCOUNT_STATUS_HEADER = "X-Account-Status";
export const ACCOUNT_LOCKED_MESSAGE =
  "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên để được giúp đỡ.";

/** true nếu request bị backend chặn vì tài khoản đã bị khóa (khác với token hết hạn). */
export const isAccountLockedResponse = (response: Response) =>
  response.headers.get(ACCOUNT_STATUS_HEADER) === "LOCKED";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
const POLL_INTERVAL_MS = 15_000;

let originalFetch: typeof window.fetch | null = null;
let lockedHandler: (() => void) | null = null;

/**
 * Bọc `window.fetch` một lần để mọi API module (account, voucher, table, ...) đều được kiểm tra:
 * response nào có header `X-Account-Status: LOCKED` thì gọi handler. Trả về hàm huỷ.
 */
function installFetchWatcher(onLocked: () => void) {
  lockedHandler = onLocked;
  if (originalFetch) return;
  originalFetch = window.fetch;
  const base = originalFetch;
  window.fetch = async (...args: Parameters<typeof window.fetch>) => {
    const response = await base(...args);
    if (isAccountLockedResponse(response)) lockedHandler?.();
    return response;
  };
}

function uninstallFetchWatcher() {
  if (originalFetch) window.fetch = originalFetch;
  originalFetch = null;
  lockedHandler = null;
}

/**
 * Hỏi backend xem phiên hiện tại còn hợp lệ không. Kết quả LOCKED do fetch watcher xử lý;
 * response thành công nghĩa là tài khoản đang hoạt động (vd. admin vừa mở khóa) → gọi onActive.
 */
async function checkSessionStatus(onActive: () => void) {
  const session = getSession();
  if (!session || document.visibilityState !== "visible") return;
  try {
    const res = await window.fetch(`${API_BASE}/api/account/profile`, {
      headers: { Authorization: `Bearer ${session.token}` },
    });
    if (res.ok) onActive();
  } catch {
    /* lỗi mạng: thử lại ở lần kiểm tra sau */
  }
}

/**
 * Theo dõi việc tài khoản bị khóa khi đang đăng nhập: bắt mọi request bị chặn vì khóa,
 * đồng thời kiểm tra ngay, định kỳ và khi người dùng quay lại tab — để báo cả khi họ đang
 * không thao tác gì, và nhận ra khi tài khoản được mở khóa lại.
 */
export function watchAccountLock(onLocked: () => void, onActive: () => void) {
  installFetchWatcher(onLocked);
  const checkSession = () => checkSessionStatus(onActive);
  void checkSession();
  const timer = window.setInterval(() => void checkSession(), POLL_INTERVAL_MS);
  const onVisible = () => {
    if (document.visibilityState === "visible") void checkSession();
  };
  document.addEventListener("visibilitychange", onVisible);
  window.addEventListener("focus", onVisible);
  return () => {
    window.clearInterval(timer);
    document.removeEventListener("visibilitychange", onVisible);
    window.removeEventListener("focus", onVisible);
    uninstallFetchWatcher();
  };
}
