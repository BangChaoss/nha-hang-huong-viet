import { useEffect, useState, useSyncExternalStore } from "react";
import type { AppRole } from "@/lib/role-routing";

/** Mirrors backend `AuthResponse` (minus nothing — tokens are kept client-side). */
export type AuthSession = {
  token: string;
  refreshToken: string | null;
  id: string;
  username: string;
  email: string;
  role: AppRole;
};

const STORAGE_KEY = "hv.session";
const listeners = new Set<() => void>();
let cachedRaw: string | null = null;
let cachedSession: AuthSession | null = null;

function readRaw(): string | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function getSession(): AuthSession | null {
  const raw = readRaw();
  if (raw !== cachedRaw) {
    cachedRaw = raw;
    try {
      cachedSession = raw ? (JSON.parse(raw) as AuthSession) : null;
    } catch {
      cachedSession = null;
    }
  }
  return cachedSession;
}

export function setSession(session: AuthSession | null) {
  try {
    if (session) window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* storage may be blocked; session then lives only until reload */
  }
  listeners.forEach((l) => l());
}

export function updateSession(patch: Partial<AuthSession>) {
  const current = getSession();
  if (current) setSession({ ...current, ...patch });
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (e: StorageEvent) => {
    if (e.key === STORAGE_KEY) listener();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

/**
 * Returns the current session plus a `ready` flag. `ready` is false during SSR and the
 * hydration render, so callers must not redirect to /login until it turns true.
 */
export function useSession() {
  const session = useSyncExternalStore(subscribe, getSession, () => null);
  const [ready, setReady] = useState(false);
  useEffect(() => setReady(true), []);
  return { session, ready };
}

export const isUserManager = (role: AppRole | undefined) => role === "ADMIN" || role === "MANAGER";
