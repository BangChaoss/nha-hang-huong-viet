import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { authApi, type AuthResponse, type AuthUser, type LoginPayload, type RegisterPayload } from "@/lib/auth-api";

export type AuthSession = { user: AuthUser; token: string; refreshToken: string | null };

type AuthCtx = {
  session: AuthSession | null;
  user: AuthUser | null;
  /** false cho tới khi đã đọc phiên từ localStorage (tránh redirect sai khi SSR/hydrate). */
  ready: boolean;
  login: (payload: LoginPayload) => Promise<AuthUser>;
  register: (payload: RegisterPayload) => Promise<AuthUser>;
  logout: () => Promise<void>;
};

const STORAGE_KEY = "hv.auth";
const Ctx = createContext<AuthCtx | null>(null);

function readStored(): AuthSession | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as AuthSession) : null;
  } catch {
    return null;
  }
}

function writeStored(session: AuthSession | null) {
  try {
    if (session) window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else window.localStorage.removeItem(STORAGE_KEY);
  } catch { /* storage may be unavailable (private mode) */ }
}

function toSession({ token, refreshToken, id, username, email, role }: AuthResponse): AuthSession {
  return { token, refreshToken, user: { id, username, email, role } };
}

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
  const [session, setSession] = useState<AuthSession | null>(null);
  const [ready, setReady] = useState(false);

  const save = useCallback((next: AuthSession | null) => {
    setSession(next);
    writeStored(next);
  }, []);

  // Khôi phục phiên; nếu access token hết hạn thì xoay vòng bằng refresh token.
  useEffect(() => {
    const stored = readStored();
    if (!stored || !isExpired(stored.token)) {
      setSession(stored);
      setReady(true);
      return;
    }
    if (!stored.refreshToken) {
      writeStored(null);
      setReady(true);
      return;
    }
    authApi.refresh(stored.refreshToken)
      .then((r) => save({ ...stored, token: r.token, refreshToken: r.refreshToken }))
      .catch(() => save(null))
      .finally(() => setReady(true));
  }, [save]);

  const login = useCallback(async (payload: LoginPayload) => {
    const next = toSession(await authApi.login(payload));
    save(next);
    return next.user;
  }, [save]);

  const register = useCallback(async (payload: RegisterPayload) => {
    const next = toSession(await authApi.register(payload));
    save(next);
    return next.user;
  }, [save]);

  const logout = useCallback(async () => {
    const token = session?.token;
    save(null);
    // Thu hồi token phía server; phiên cục bộ đã xoá nên bỏ qua lỗi mạng.
    if (token) await authApi.logout(token).catch(() => undefined);
  }, [session, save]);

  const value = useMemo<AuthCtx>(
    () => ({ session, user: session?.user ?? null, ready, login, register, logout }),
    [session, ready, login, register, logout],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const c = useContext(Ctx);
  if (!c) throw new Error("useAuth must be used inside AuthProvider");
  return c;
}
