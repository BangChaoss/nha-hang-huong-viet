import { describe, expect, it, beforeEach } from "vitest";
import { getDeviceId } from "../device-id";

describe("device-id utility", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("should generate and store a new UUID device id if none exists in localStorage", () => {
    const id1 = getDeviceId();
    expect(id1).toBeTruthy();
    expect(window.localStorage.getItem("hv.device_id")).toBe(id1);

    // Calling it again should retrieve the same stored device ID
    const id2 = getDeviceId();
    expect(id2).toBe(id1);
  });

  it("should reuse existing device ID in localStorage", () => {
    window.localStorage.setItem("hv.device_id", "my-custom-test-device-uuid-12345");
    const id = getDeviceId();
    expect(id).toBe("my-custom-test-device-uuid-12345");
  });
});
