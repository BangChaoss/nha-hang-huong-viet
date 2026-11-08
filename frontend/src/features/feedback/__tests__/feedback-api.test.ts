import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FeedbackApiError, feedbackApi, hasReply, reviewerName } from "../api/feedback-api";

type FetchMock = ReturnType<typeof vi.fn>;

function mockFetch(response: { ok: boolean; status: number; body?: unknown }) {
  global.fetch = vi.fn().mockResolvedValue({
    ok: response.ok,
    status: response.status,
    json: () =>
      response.body === undefined
        ? Promise.reject(new Error("no body"))
        : Promise.resolve(response.body),
  });
}

function lastCall(): [string, RequestInit] {
  const call = (global.fetch as FetchMock).mock.calls[0];
  return [call?.[0] as string, (call?.[1] ?? {}) as RequestInit];
}

describe("feedbackApi", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("submit() POSTs trimmed comment and nulls an empty invoiceId", async () => {
    mockFetch({ ok: true, status: 201, body: { id: "f1", rating: 5 } });

    await feedbackApi.submit({ rating: 5, comment: "  Ngon  ", invoiceId: "   " });

    const [url, init] = lastCall();
    expect(url).toMatch(/\/api\/feedbacks$/);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({
      rating: 5,
      comment: "Ngon",
      invoiceId: null,
    });
  });

  it("submit() surfaces backend message and status (429 cooldown)", async () => {
    mockFetch({ ok: false, status: 429, body: { message: "Bạn thao tác quá nhanh." } });

    const error = await feedbackApi.submit({ rating: 4 }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(FeedbackApiError);
    expect((error as FeedbackApiError).status).toBe(429);
    expect((error as FeedbackApiError).message).toBe("Bạn thao tác quá nhanh.");
  });

  it("getByInvoice() returns null on 204", async () => {
    mockFetch({ ok: true, status: 204 });

    await expect(feedbackApi.getByInvoice("inv-1")).resolves.toBeNull();
    expect(lastCall()[0]).toContain("/api/feedbacks/invoice/inv-1");
  });

  it("listAdmin() only sends filters that are set", async () => {
    mockFetch({
      ok: true,
      status: 200,
      body: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true },
    });

    await feedbackApi.listAdmin({
      rating: 2,
      hasResponse: false,
      keyword: "  phở ",
      page: 1,
      size: 10,
    });

    const url = lastCall()[0];
    expect(url).toContain("/api/admin/feedbacks?");
    expect(url).toContain("rating=2");
    expect(url).toContain("hasResponse=false");
    expect(url).toContain("keyword=ph%E1%BB%9F");
    expect(url).toContain("page=1");
    expect(url).not.toContain("isPublic");
  });

  it("reply() and setVisibility() PATCH the right endpoints", async () => {
    mockFetch({ ok: true, status: 200, body: { id: "f1" } });
    await feedbackApi.reply("f1", "  Cảm ơn quý khách ");
    let [url, init] = lastCall();
    expect(url).toMatch(/\/api\/admin\/feedbacks\/f1\/reply$/);
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body as string)).toEqual({ staffResponse: "Cảm ơn quý khách" });

    mockFetch({ ok: true, status: 200, body: { id: "f1" } });
    await feedbackApi.setVisibility("f1", false);
    [url, init] = lastCall();
    expect(url).toMatch(/\/api\/admin\/feedbacks\/f1\/visibility$/);
    expect(JSON.parse(init.body as string)).toEqual({ isPublic: false });
  });

  it("maps 403 without body to a permission message", async () => {
    mockFetch({ ok: false, status: 403 });
    await expect(feedbackApi.getStats()).rejects.toThrow(
      "Bạn không có quyền thực hiện thao tác này.",
    );
  });
});

describe("feedback helpers", () => {
  it("hasReply() ignores blank responses", () => {
    expect(hasReply({ staffResponse: "   " })).toBe(false);
    expect(hasReply({ staffResponse: null })).toBe(false);
    expect(hasReply({ staffResponse: "Cảm ơn" })).toBe(true);
  });

  it("reviewerName() falls back to a generic label", () => {
    expect(reviewerName({ guestName: null })).toBe("Khách hàng");
    expect(reviewerName({ guestName: "an" })).toBe("an");
  });
});
