import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { StaffPOS } from "@/components/hv/StaffPOS";
import { reservationApi } from "@/features/reservation/api/reservation-api";
import { tableApi, zoneApi, type TableResponse } from "@/lib/table-api";

vi.mock("@/features/reservation/api/reservation-api");
vi.mock("@/lib/table-api");
vi.mock("@/lib/use-table-websocket", () => ({ useTableWebSocket: vi.fn() }));
vi.mock("@/features/order/hooks/use-kitchen-tickets", () => ({ useKitchenCompleted: vi.fn() }));

describe("StaffPOS individual table display", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(zoneApi.list).mockResolvedValue([
      { id: "zone-1", zoneName: "Trong Nhà", tableCount: 2, isVip: false },
    ]);
    vi.mocked(reservationApi.listByDate).mockResolvedValue([]);
  });

  it("renders Bàn 20A and Bàn 20B as separate cards with their own statuses instead of consolidating into Bàn 20", async () => {
    const tables: TableResponse[] = [
      {
        id: "t-20a",
        tableNumber: "Bàn 20A",
        capacity: 4,
        status: "OCCUPIED",
        availableSeats: 2,
        zoneId: "zone-1",
        zoneName: "Trong Nhà",
        zoneIsVip: false,
      },
      {
        id: "t-20b",
        tableNumber: "Bàn 20B",
        capacity: 4,
        status: "AVAILABLE",
        availableSeats: null,
        zoneId: "zone-1",
        zoneName: "Trong Nhà",
        zoneIsVip: false,
      },
    ];
    vi.mocked(tableApi.list).mockResolvedValue(tables);

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <StaffPOS />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Bàn 20A")).toBeInTheDocument();
    expect(screen.getByText("Bàn 20B")).toBeInTheDocument();
    expect(screen.queryByText("Bàn 20")).not.toBeInTheDocument();
    expect(screen.getByText("Đang phục vụ · 1")).toBeInTheDocument();
    expect(screen.getByText("Trống · 1")).toBeInTheDocument();
  });
});
