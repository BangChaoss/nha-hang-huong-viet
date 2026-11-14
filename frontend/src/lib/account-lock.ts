import { getSession } from "@/lib/auth-session";

/** Header backend gắn vào response khi access token thuộc về tài khoản đã bị khóa. */
const ACCOUNT_STATUS_HEADER = "X-Account-Status";
export const ACCOUNT_LOCKED_MESSAGE = "Tài khoản của bạn đã bị khóa.";

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
    if (response.headers.get(ACCOUNT_STATUS_HEADER) === "LOCKED") lockedHandler?.();
    return response;
  };
}

function uninstallFetchWatcher() {
  if (originalFetch) window.fetch = originalFetch;
  originalFetch = null;
  lockedHandler = null;
}

/** Hỏi backend xem phiên hiện tại còn hợp lệ không; kết quả LOCKED do fetch watcher xử lý. */
async function checkSession() {
  const session = getSession();
  if (!session || document.visibilityState !== "visible") return;
  try {
    await window.fetch(`${API_BASE}/api/account/profile`, {
      headers: { Authorization: `Bearer ${session.token}` },
    });
  } catch {
    /* lỗi mạng: thử lại ở lần kiểm tra sau */
  }
}

/**
 * Theo dõi việc tài khoản bị khóa khi đang đăng nhập: bắt mọi request bị chặn vì khóa,
 * đồng thời kiểm tra định kỳ (và khi người dùng quay lại tab) để báo ngay cả khi họ đang
 * không thao tác gì.
 */
export function watchAccountLock(onLocked: () => void) {
  installFetchWatcher(onLocked);
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
