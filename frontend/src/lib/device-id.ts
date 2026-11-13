const DEVICE_ID_KEY = "hv.device_id";

/**
 * Retrieves the persistent device identifier from localStorage,
 * or generates and stores a new UUID v4 if none exists.
 */
export function getDeviceId(): string {
  if (typeof window === "undefined") {
    return "";
  }
  try {
    let deviceId = window.localStorage.getItem(DEVICE_ID_KEY);
    if (!deviceId) {
      deviceId =
        typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
          ? crypto.randomUUID()
          : `dev-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
      window.localStorage.setItem(DEVICE_ID_KEY, deviceId);
    }
    return deviceId;
  } catch {
    return "";
  }
}
