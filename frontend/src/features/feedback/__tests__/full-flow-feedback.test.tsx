import React from "react";
import { describe, expect, it, beforeEach, afterEach, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { setSession } from "@/lib/auth-session";
import { feedbackApi } from "../api/feedback-api";
import { CustomerFeedback } from "../components/customer-feedback";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
  useNavigate: () => vi.fn(),
}));

function renderWithClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("Full Flow Feedback Component", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    setSession(null);
  });

  afterEach(() => {
    setSession(null);
    vi.restoreAllMocks();
  });

  it("renders login prompt when customer is not authenticated", () => {
    renderWithClient(<CustomerFeedback />);

    expect(screen.getByText("Chia sẻ trải nghiệm của bạn")).toBeInTheDocument();
    expect(screen.getByText("Đăng nhập để đánh giá")).toBeInTheDocument();
  });

  it("shows warning and disables input when customer has no paid eligible invoices", async () => {
    setSession({
      token: "valid-jwt-token",
      refreshToken: null,
      role: "CUSTOMER",
      id: "cust-1",
      username: "cust_test",
      email: "cust@test.com",
    });

    vi.spyOn(feedbackApi, "getEligibleInvoices").mockResolvedValue([]);
    vi.spyOn(feedbackApi, "getMyReviews").mockResolvedValue([]);
    vi.spyOn(feedbackApi, "getPublic").mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 10,
      last: true,
    });

    renderWithClient(<CustomerFeedback />);

    await waitFor(() => {
      expect(screen.getByText("Chưa có hoá đơn hoàn tất thanh toán")).toBeInTheDocument();
    });

    expect(screen.getByText(/Hệ thống chỉ cho phép gửi đánh giá cho những đơn hàng/i)).toBeInTheDocument();
  });

  it("loads eligible invoice after POS checkout and submits feedback successfully", async () => {
    setSession({
      token: "valid-jwt-token",
      refreshToken: null,
      role: "CUSTOMER",
      id: "cust-1",
      username: "cust_hoangnam",
      email: "nam@test.com",
    });

    const mockInvoice = {
      id: "inv-pos-101",
      invoiceCode: "HV-20261111-INV101",
      finalAmount: 143000,
      tableNumber: "Bàn 01",
      paidAt: "2026-11-11T12:30:00",
    };

    vi.spyOn(feedbackApi, "getEligibleInvoices").mockResolvedValue([mockInvoice]);
    vi.spyOn(feedbackApi, "getMyReviews").mockResolvedValue([]);
    vi.spyOn(feedbackApi, "getPublic").mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 10,
      last: true,
    });

    const submitSpy = vi.spyOn(feedbackApi, "submit").mockResolvedValue({
      id: "fb-1",
      customerId: "cust-1",
      guestName: "cust_hoangnam",
      guestPhone: "0981112233",
      invoiceId: "inv-pos-101",
      rating: 5,
      comment: "Món Phở bò tuyệt vời!",
      staffResponse: null,
      isPublic: true,
      createdAt: "2026-11-11T12:35:00",
    });

    renderWithClient(<CustomerFeedback />);

    // Chờ hoá đơn hợp lệ hiển thị
    await waitFor(() => {
      expect(screen.getByText("HV-20261111-INV101")).toBeInTheDocument();
      expect(screen.getByText("Đã thanh toán (PAID)")).toBeInTheDocument();
      expect(screen.getByText("Bàn 01")).toBeInTheDocument();
    });

    // Chọn rating 5 sao
    const starButtons = screen.getAllByRole("radio");
    expect(starButtons.length).toBe(5);
    fireEvent.click(starButtons[4]!); // 5 sao

    // Nhập nhận xét
    const commentInput = screen.getByLabelText("Nhận xét");
    fireEvent.change(commentInput, { target: { value: "Món Phở bò tuyệt vời!" } });

    // Bấm gửi đánh giá
    const submitBtn = screen.getByRole("button", { name: /Gửi đánh giá/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(submitSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          invoiceId: "inv-pos-101",
          rating: 5,
          comment: "Món Phở bò tuyệt vời!",
        }),
        expect.anything()
      );
    });
  });
});
