import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MenuManagement } from "../components/menu-management";
import { MenuDetailDialog } from "../components/menu-detail-dialog";
import type { MenuItemResponse } from "../api/menu-api";

function renderWithClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("MenuManagement", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/api/admin/categories")) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () =>
            Promise.resolve([
              { id: "cat-1", categoryName: "Khai vị", displayOrder: 1, menuItemCount: 2 },
            ]),
        } as unknown as Response);
      }
      if (url.includes("/api/admin/menu-items")) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () =>
            Promise.resolve({
              content: [
                {
                  id: "item-1",
                  categoryId: "cat-1",
                  categoryName: "Khai vị",
                  itemName: "Gà hấp mật ong",
                  description: "Gà ta hấp mật ong",
                  currentPrice: 185000,
                  imageUrl: null,
                  isAvailable: true,
                },
              ],
              page: 0,
              size: 10,
              totalElements: 1,
              totalPages: 1,
              last: true,
            }),
        } as unknown as Response);
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      } as unknown as Response);
    });
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("renders the two tabs and the dish list", async () => {
    renderWithClient(<MenuManagement />);

    expect(screen.getByRole("tab", { name: "Danh mục" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Món ăn" })).toBeInTheDocument();

    // Tab "Danh mục" hiển thị danh sách danh mục
    await waitFor(() => {
      expect(screen.getByText("Khai vị")).toBeInTheDocument();
    });

    // Chuyển sang tab "Món ăn"
    fireEvent.mouseDown(screen.getByRole("tab", { name: "Món ăn" }), { button: 0, ctrlKey: false });

    await waitFor(() => {
      expect(screen.getByText("Gà hấp mật ong")).toBeInTheDocument();
    });
    expect(screen.getByText("185.000đ")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Thêm món/i })).toBeInTheDocument();
  });

  it("gửi tham số phân trang và sắp xếp theo thời gian tạo mặc định", async () => {
    renderWithClient(<MenuManagement />);

    fireEvent.mouseDown(screen.getByRole("tab", { name: "Món ăn" }), { button: 0, ctrlKey: false });

    await waitFor(() => {
      const call = (global.fetch as ReturnType<typeof vi.fn>).mock.calls.find(([input]) =>
        String(input).includes("/api/admin/menu-items"),
      );
      expect(call).toBeDefined();
      const url = String(call?.[0]);
      expect(url).toContain("page=0");
      expect(url).toContain("size=10");
      expect(url).toContain("sort=createdAt");
      expect(url).toContain("direction=desc");
    });
  });
});

describe("MenuDetailDialog", () => {
  const item: MenuItemResponse = {
    id: "item-1",
    categoryId: "cat-1",
    categoryName: "Khai vị",
    itemName: "Gà hấp mật ong",
    description: "Gà ta hấp mật ong đặc trưng",
    currentPrice: 185000,
    imageUrl: "https://example.com/ga.jpg",
    isAvailable: false,
  };

  it("shows the out-of-stock badge when the dish is unavailable", () => {
    renderWithClient(<MenuDetailDialog item={item} open onOpenChange={() => undefined} />);

    expect(screen.getAllByText("Hết món").length).toBeGreaterThan(0);
    expect(screen.getByText("Gà hấp mật ong")).toBeInTheDocument();
    expect(screen.getByText("Gà ta hấp mật ong đặc trưng")).toBeInTheDocument();
  });

  it("renders nothing when there is no item", () => {
    const { container } = renderWithClient(
      <MenuDetailDialog item={null} open onOpenChange={() => undefined} />,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
