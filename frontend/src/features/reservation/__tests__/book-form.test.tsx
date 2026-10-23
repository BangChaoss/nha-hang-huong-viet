import { describe, expect, it } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BookForm } from "../components/book-form";
import { createReservationSchema } from "../utils/reservation-validators";

function renderWithClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("BookForm & Reservation Validators", () => {
  describe("Zod validation schema (createReservationSchema)", () => {
    it("accepts valid booking input", () => {
      const valid = {
        guestName: "Nguyễn Văn A",
        guestPhone: "0912345678",
        guestEmail: "nguyenvana@gmail.com",
        reservationDate: "2026-11-10",
        reservationTime: "18:30",
        partySize: 4,
        zoneId: "zone-1",
        specialRequests: "Có trẻ em",
      };
      const result = createReservationSchema.safeParse(valid);
      expect(result.success).toBe(true);
    });

    it("rejects phone numbers with invalid format", () => {
      const invalidPhone = {
        guestName: "Nguyễn Văn A",
        guestPhone: "12345", // Sai regex
        reservationDate: "2026-11-10",
        reservationTime: "18:30",
        partySize: 4,
        zoneId: "zone-1",
      };
      const result = createReservationSchema.safeParse(invalidPhone);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.errors.some((e) => e.path.includes("guestPhone"))).toBe(true);
      }
    });

    it("enforces party size limit <= 15 (BR-17)", () => {
      const overLimit = {
        guestName: "Nguyễn Văn A",
        guestPhone: "0912345678",
        reservationDate: "2026-11-10",
        reservationTime: "18:30",
        partySize: 16, // > 15
        zoneId: "zone-1",
      };
      const result = createReservationSchema.safeParse(overLimit);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.errors.some((e) => e.path.includes("partySize"))).toBe(true);
      }
    });
  });

  describe("BookForm Component Rendering", () => {
    it("renders form elements and steps correctly", () => {
      renderWithClient(<BookForm />);

      expect(screen.getByText("Đặt Bàn Trực Tuyến")).toBeInTheDocument();
      expect(screen.getByText(/Thời Gian & Quy Mô Khách/)).toBeInTheDocument();
      expect(screen.getByText(/Chọn Khu Vực & Khung Giờ/)).toBeInTheDocument();
      expect(screen.getByText(/Thông Tin Người Đặt/)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Gửi Yêu Cầu Đặt Bàn/i })).toBeInTheDocument();
    });

    it("displays error messages when submitting empty form", async () => {
      renderWithClient(<BookForm />);

      const submitButton = screen.getByRole("button", { name: /Gửi Yêu Cầu Đặt Bàn/i });
      fireEvent.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText(/Vui lòng nhập họ và tên/i)).toBeInTheDocument();
        expect(screen.getByText(/Vui lòng nhập số điện thoại/i)).toBeInTheDocument();
        expect(screen.getAllByText(/Vui lòng chọn ngày dùng bữa/i).length).toBeGreaterThan(0);
      });
    });

    it("displays Event Booking warning when partySize > 15", async () => {
      renderWithClient(<BookForm initialValues={{ partySize: 18 }} />);

      await waitFor(() => {
        expect(screen.getByText(/Quy định đoàn khách lớn \(BR-17\)/i)).toBeInTheDocument();
        expect(screen.getByText(/1900 8888/)).toBeInTheDocument();
      });
    });
  });
});
