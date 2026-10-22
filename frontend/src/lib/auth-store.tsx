import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { authApi, type AuthUser, type LoginPayload, type RegisterPayload } from "@/lib/auth-api";
import { getSession, setSession, updateSession, useSession, type AuthSession } from "@/lib/auth-session";

type AuthCtx = {
  session: AuthSession | null;
  user: AuthUser | null;
  /** false cho tới khi đã đọc phiên từ localStorage (tránh redirect sai khi SSR/hydrate). */
  ready: boolean;
  login: (payload: LoginPayload) => Promise<AuthUser>;
  register: (payload: RegisterPayload) => Promise<AuthUser>;
  logout: () => Promise<void>;
};

// Phiên bản cũ lưu phiên riêng dưới key này ({ user, token, refreshToken }).
// Nay dùng chung kho `auth-session` để trang tài khoản và header thấy cùng một phiên.
const LEGACY_STORAGE_KEY = "hv.auth";
const Ctx = createContext<AuthCtx | null>(null);

function migrateLegacySession() {
  try {
    const raw = window.localStorage.getItem(LEGACY_STORAGE_KEY);
    if (!raw) return;
    window.localStorage.removeItem(LEGACY_STORAGE_KEY);
    if (getSession()) return;
    const { user, token, refreshToken } = JSON.parse(raw) as { user?: AuthUser; token?: string; refreshToken?: string | null };
    if (user && token) setSession({ ...user, token, refreshToken: refreshToken ?? null });
  } catch { /* storage may be unavailable (private mode) */ }
}

const toUser = ({ id, username, email, role }: AuthSession): AuthUser => ({ id, username, email, role });

/** Đọc `exp` trong JWT (giây). Trả về true nếu token đã/sắp hết hạn trong 30s. */
function isExpired(token: string) {
  try {
    const payload = JSON.parse(atob((token.split(".")[1] ?? "").replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.exp === "number" && payload.exp * 1000 < Date.now() + 30_000;
  } catch {
    return true;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const { session } = useSession();
  const [ready, setReady] = useState(false);

  // Khôi phục phiên; nếu access token hết hạn thì xoay vòng bằng refresh token.
  useEffect(() => {
    migrateLegacySession();
    const stored = getSession();
    if (!stored || !isExpired(stored.token)) {
      setReady(true);
      return;
    }
    if (!stored.refreshToken) {
      setSession(null);
      setReady(true);
      return;
    }
    authApi.refresh(stored.refreshToken)
      .then((r) => updateSession({ token: r.token, refreshToken: r.refreshToken }))
      .catch(() => setSession(null))
      .finally(() => setReady(true));
  }, []);

  const login = useCallback(async (payload: LoginPayload) => {
    const next = await authApi.login(payload);
    setSession(next);
    return toUser(next);
  }, []);

  const register = useCallback(async (payload: RegisterPayload) => {
    const next = await authApi.register(payload);
    setSession(next);
    return toUser(next);
  }, []);

  const logout = useCallback(async () => {
    const token = getSession()?.token;
    setSession(null);
    // Thu hồi token phía server; phiên cục bộ đã xoá nên bỏ qua lỗi mạng.
    if (token) await authApi.logout(token).catch(() => undefined);
  }, []);

  const user = useMemo(() => (session ? toUser(session) : null), [session]);
  const value = useMemo<AuthCtx>(
    () => ({ session, user, ready, login, register, logout }),
    [session, user, ready, login, register, logout],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const c = useContext(Ctx);
  if (!c) throw new Error("useAuth must be used inside AuthProvider");
  return c;
}
