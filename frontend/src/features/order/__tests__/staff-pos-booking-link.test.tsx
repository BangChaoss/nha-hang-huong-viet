import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BillingDialog, StaffPOS } from "@/components/hv/StaffPOS";
import { StaffBookingDashboard } from "@/features/reservation/components/staff-booking-dashboard";
import { reservationApi } from "@/features/reservation/api/reservation-api";
import { menuApi } from "@/features/menu/api/menu-api";
import { tableApi, zoneApi, type TableResponse } from "@/lib/table-api";
import { orderApi } from "../api/order-api";
import type { OrderResponse } from "../types/order.types";
import type { ReservationResponse } from "@/features/reservation/types/reservation.types";

vi.mock("../api/order-api");
vi.mock("@/features/menu/api/menu-api");
vi.mock("@/features/reservation/api/reservation-api");
vi.mock("@/lib/table-api");
vi.mock("@/lib/use-table-websocket", () => ({ useTableWebSocket: vi.fn() }));
vi.mock("../hooks/use-kitchen-tickets", () => ({ useKitchenCompleted: vi.fn() }));
vi.mock("@/features/reservation/components/confirm-booking-dialog", () => ({
  ConfirmBookingDialog: () => null,
}));

function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function offsetTimeStr(minutesOffset: number) {
  const d = new Date(Date.now() + minutesOffset * 60_000);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}:00`;
}

describe("StaffPOS & Đặt Bàn linkage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(zoneApi.list).mockResolvedValue([
      { id: "zone-1", zoneName: "Trong Nhà", tableCount: 2, isVip: false },
    ]);
    vi.mocked(menuApi.getOverview).mockResolvedValue({ categories: [] });
    vi.mocked(reservationApi.listAdminBookings).mockResolvedValue([]);
  });

  it("hiển thị thông tin đặt bàn trên thẻ OCCUPIED, cảnh báo lịch hẹn sắp tới trên bàn Trống và thanh liên kết Đặt Bàn", async () => {
    const tables: TableResponse[] = [
      {
        id: "t1",
        tableNumber: "Bàn 01",
        capacity: 4,
        status: "OCCUPIED",
        zoneId: "zone-1",
        zoneName: "Trong Nhà",
        zoneIsVip: false,
      },
      {
        id: "t2",
        tableNumber: "Bàn 02",
        capacity: 4,
        status: "AVAILABLE",
        zoneId: "zone-1",
        zoneName: "Trong Nhà",
        zoneIsVip: false,
      },
    ];
    const arrivedRes: ReservationResponse = {
      id: "1111aaaa-2222-3333-4444-555566667777",
      guestName: "Nguyễn Văn An",
      guestPhone: "0901112233",
      partySize: 4,
      reservationDate: todayStr(),
      reservationTime: offsetTimeStr(0),
      status: "ARRIVED",
      tableId: "t1",
      tableNumber: "Bàn 01",
      depositAmount: 250000,
      specialRequests: "Sinh nhật",
      createdAt: "2026-11-25T10:00:00Z",
    };
    const upcomingRes: ReservationResponse = {
      id: "2222bbbb-2222-3333-4444-555566667777",
      guestName: "Trần Thị Bình",
      guestPhone: "0909887766",
      partySize: 2,
      reservationDate: todayStr(),
      reservationTime: offsetTimeStr(180),
      status: "CONFIRMED",
      tableId: "t2",
      tableNumber: "Bàn 02",
      depositAmount: 0,
      createdAt: "2026-11-25T10:00:00Z",
    };
    const pendingRes: ReservationResponse = {
      ...upcomingRes,
      id: "3333cccc-0000-0000-0000-000000000000",
      status: "PENDING_HOLD",
      tableId: null,
      tableNumber: null,
    };

    vi.mocked(tableApi.list).mockResolvedValue(tables);
    vi.mocked(reservationApi.listByDate).mockResolvedValue([arrivedRes, upcomingRes]);
    vi.mocked(reservationApi.listAdminBookings).mockResolvedValue([pendingRes]);
    vi.mocked(orderApi.seatedAtTable).mockResolvedValue([
      {
        id: "ord-1111",
        tableId: "t1",
        tableNumber: "Bàn 01",
        staffId: "staff-1",
        orderType: "DINE_IN",
        status: "IN_PROGRESS",
        createdAt: "2026-11-25T12:00:00Z",
        closedAt: null,
        subtotal: 0,
        cancelledAmount: 0,
        items: [],
        partySize: 4,
        seated: true,
        reservationId: arrivedRes.id,
        reservationCode: "HV-1111AAAA",
        reservationGuestName: arrivedRes.guestName,
        reservationGuestPhone: arrivedRes.guestPhone,
        reservationTime: arrivedRes.reservationTime.slice(0, 5),
        reservationSpecialRequests: "Sinh nhật",
        reservationDepositAmount: 250000,
      },
    ]);

    const onOpenBookingsTab = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <StaffPOS onOpenBookingsTab={onOpenBookingsTab} />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Chờ duyệt đặt bàn · 1")).toBeInTheDocument();
    expect(screen.getByText("Đặt trước · HV-1111AAAA")).toBeInTheDocument();
    expect(screen.getByText("Đã cọc VIP 250k")).toBeInTheDocument();
    expect(screen.getByText(new RegExp(`Lịch đặt .* · Trần Thị Bình`))).toBeInTheDocument();

    fireEvent.click(screen.getByText("Chờ duyệt đặt bàn · 1"));
    expect(onOpenBookingsTab).toHaveBeenCalledWith({ tab: "needs-action" });

    fireEvent.click(screen.getByRole("button", { name: /Bàn 01/ }));
    const dialog = await screen.findByRole("dialog", { name: /Bàn 01 · Gọi món/ });
    expect(within(dialog).getByText("HV-1111AAAA")).toBeInTheDocument();
    expect(within(dialog).getByText("Sinh nhật")).toBeInTheDocument();
  });

  it("tự động điền số điện thoại khách đặt bàn vào BillingDialog", async () => {
    const table: TableResponse = {
      id: "t1",
      tableNumber: "Bàn 01",
      capacity: 4,
      status: "OCCUPIED",
      zoneId: "zone-1",
      zoneName: "Trong Nhà",
      zoneIsVip: false,
    };
    const order: OrderResponse = {
      id: "ord-1111",
      tableId: "t1",
      tableNumber: "Bàn 01",
      staffId: null,
      orderType: "DINE_IN",
      status: "IN_PROGRESS",
      createdAt: "2026-11-25T12:00:00Z",
      closedAt: null,
      subtotal: 65000,
      cancelledAmount: 0,
      reservationId: "res-1",
      reservationCode: "HV-1111AAAA",
      reservationGuestName: "Nguyễn Văn An",
      reservationGuestPhone: "0901112233",
      items: [
        {
          id: "i1",
          menuItemId: "m1",
          itemName: "Phở bò",
          quantity: 1,
          unitPrice: 65000,
          lineTotal: 65000,
          note: null,
          cookingStatus: "SERVED",
          cancelReason: null,
          createdAt: "2026-11-25T12:00:00Z",
        },
      ],
    };
    vi.mocked(orderApi.getById).mockResolvedValue(order);

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <BillingDialog table={table} order={order} onClose={vi.fn()} />
      </QueryClientProvider>,
    );

    const phoneInput = await screen.findByLabelText("Số điện thoại khách");
    expect(phoneInput).toHaveValue("0901112233");
  });

  it("tự động gọi onOpenInPos sau khi Check-in thành công từ StaffBookingDashboard", async () => {
    const confirmedBooking: ReservationResponse = {
      id: "aaaa1111-2222-3333-4444-555566667777",
      guestName: "Lê Minh",
      guestPhone: "0912345678",
      partySize: 4,
      reservationDate: todayStr(),
      reservationTime: offsetTimeStr(0),
      status: "CONFIRMED",
      tableId: "table-5",
      tableNumber: "05",
      depositAmount: 0,
      createdAt: "2026-11-25T09:00:00Z",
    };

    vi.mocked(reservationApi.listAdminBookings).mockImplementation(async (params) => {
      if (params.scope === "pending") return [];
      return [confirmedBooking];
    });
    vi.mocked(reservationApi.updateReservationStatus).mockResolvedValue({
      ...confirmedBooking,
      status: "ARRIVED",
    });

    const onOpenInPos = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <StaffBookingDashboard onOpenInPos={onOpenInPos} />
      </QueryClientProvider>,
    );

    fireEvent.click(screen.getByText("Hôm nay").closest(".cursor-pointer")!);
    const checkInBtn = await screen.findByRole("button", { name: /Check-in/i });
    fireEvent.click(checkInBtn);

    await waitFor(() => expect(onOpenInPos).toHaveBeenCalledWith("table-5"));
  });
});
