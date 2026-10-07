import { createContext, useContext, useState, type ReactNode } from "react";

export type Category = "Khai vị" | "Món chính" | "Lẩu & Nướng" | "Tráng miệng & Đồ uống";
export type Dish = {
  id: string; name: string; price: number; category: Category; desc: string; img: string; inStock: boolean; sold: number;
};
export type TableStatus = "available" | "reserved" | "occupied" | "maintenance";
export type Zone = "Trong Nhà" | "Sân Vườn" | "Phòng VIP";
export type OrderLine = { dishId: string; qty: number };
export type Table = {
  id: number; name: string; zone: Zone; seats: number; status: TableStatus;
  guest?: string | undefined; time?: string | undefined; phone?: string | undefined; partySize?: number | undefined; order: OrderLine[];
};
export type TicketStatus = "queued" | "preparing" | "ready";
export type Ticket = { id: string; table: string; createdAt: number; items: OrderLine[]; note: string; status: TicketStatus };
export type BookingStatus = "pending" | "confirmed" | "cancelled" | "completed";
export type Booking = {
  id: string; date: string; time: string; party: number; name: string; phone: string; note: string; status: BookingStatus;
  review?: { stars: number; comment: string; reply: string };
};
export type Transaction = { id: string; table: string; total: number; points: number; method: string; at: number };

const U = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=800&q=80`;

const initialDishes: Dish[] = [
  { id: "d1", name: "Gỏi Cuốn Tôm Thịt Ba Chỉ", price: 60000, category: "Khai vị", desc: "Bánh tráng cuốn tôm, ba chỉ, bún và rau thơm, chấm tương đậu.", img: U("photo-1559314809-0d155014e29e"), inStock: true, sold: 64 },
  { id: "d2", name: "Chả Giò Rế Cua Biển", price: 70000, category: "Khai vị", desc: "Chả giò vỏ rế giòn rụm, nhân cua biển và nấm mèo.", img: U("photo-1544025162-d76694265947"), inStock: true, sold: 41 },
  { id: "d3", name: "Phở Bò Tái Lăn Hà Nội", price: 85000, category: "Món chính", desc: "Nước dùng xương ninh 12 giờ, thịt bò tái lăn gừng tỏi.", img: U("photo-1582878826629-29b7ad1cdc43"), inStock: true, sold: 128 },
  { id: "d4", name: "Bún Chả Hương Quê", price: 75000, category: "Món chính", desc: "Chả nướng than hoa, nước mắm chua ngọt, rau sống.", img: U("photo-1555126634-323283e090fa"), inStock: true, sold: 97 },
  { id: "d5", name: "Cá Kho Tộ Niêu Đất", price: 120000, category: "Món chính", desc: "Cá basa kho tiêu nước màu dừa trong niêu đất nung.", img: U("photo-1547592180-85f173990554"), inStock: false, sold: 52 },
  { id: "d6", name: "Lẩu Riêu Cua Bắp Bò", price: 380000, category: "Lẩu & Nướng", desc: "Riêu cua đồng, bắp bò, sườn sụn, đậu phụ chiên và rau đồng.", img: U("photo-1569718212165-3a8278d5f624"), inStock: true, sold: 38 },
  { id: "d7", name: "Bò Nướng Lá Lốt", price: 140000, category: "Lẩu & Nướng", desc: "Bò cuốn lá lốt nướng than, mỡ hành, đậu phộng rang.", img: U("photo-1529692236671-f1f6cf9683ba"), inStock: true, sold: 45 },
  { id: "d8", name: "Chè Hạt Sen Long Nhãn", price: 35000, category: "Tráng miệng & Đồ uống", desc: "Hạt sen Huế bùi, long nhãn Hưng Yên, nước đường phèn.", img: U("photo-1563805042-7684c019e1cb"), inStock: true, sold: 73 },
  { id: "d9", name: "Trà Sen Vàng Cố Đô", price: 40000, category: "Tráng miệng & Đồ uống", desc: "Trà Thái Nguyên ướp sen Tây Hồ, phục vụ ấm.", img: U("photo-1556679343-c7306c1976bc"), inStock: true, sold: 88 },
];

const zoneOf = (i: number): Zone => (i <= 5 ? "Trong Nhà" : i <= 9 ? "Sân Vườn" : "Phòng VIP");
const initialTables: Table[] = Array.from({ length: 12 }, (_, k) => {
  const i = k + 1;
  const base: Table = { id: i, name: `Bàn ${String(i).padStart(2, "0")}`, zone: zoneOf(i), seats: i >= 10 ? 10 : i % 2 ? 4 : 2, status: "available", order: [] };
  if ([2, 7, 11].includes(i)) return { ...base, status: "reserved", guest: "Nguyễn Văn A", time: "19:00", phone: "0903 123 456", partySize: 4 };
  if (i === 3) return { ...base, status: "occupied", order: [{ dishId: "d3", qty: 2 }, { dishId: "d1", qty: 1 }] };
  if (i === 6) return { ...base, status: "occupied", order: [{ dishId: "d6", qty: 1 }, { dishId: "d9", qty: 3 }] };
  if (i === 10) return { ...base, status: "occupied", order: [{ dishId: "d4", qty: 4 }, { dishId: "d7", qty: 2 }, { dishId: "d8", qty: 4 }] };
  if (i === 9) return { ...base, status: "maintenance" };
  return base;
});

const now = Date.now();
const initialTickets: Ticket[] = [
  { id: "t1", table: "Bàn 03", createdAt: now - 18 * 60000, items: [{ dishId: "d3", qty: 2 }], note: "", status: "preparing" },
  { id: "t2", table: "Bàn 06", createdAt: now - 12 * 60000, items: [{ dishId: "d6", qty: 1 }], note: "LƯU Ý: KHÔNG ĂN ĐƯỢC ĐẬU PHỘNG", status: "queued" },
  { id: "t3", table: "Bàn 10", createdAt: now - 7 * 60000, items: [{ dishId: "d4", qty: 4 }, { dishId: "d7", qty: 2 }], note: "1 khách ĂN CHAY — tách riêng rau", status: "queued" },
  { id: "t4", table: "Bàn 03", createdAt: now - 22 * 60000, items: [{ dishId: "d1", qty: 1 }], note: "", status: "ready" },
];

const initialBookings: Booking[] = [
  { id: "b1", date: "2026-10-06", time: "19:00", party: 4, name: "Nguyễn Văn A", phone: "0903 123 456", note: "Gần cửa sổ", status: "confirmed" },
  { id: "b2", date: "2026-09-28", time: "12:00", party: 2, name: "Nguyễn Văn A", phone: "0903 123 456", note: "", status: "completed" },
  { id: "b3", date: "2026-09-14", time: "18:30", party: 6, name: "Nguyễn Văn A", phone: "0903 123 456", note: "Sinh nhật", status: "completed",
    review: { stars: 5, comment: "Phở rất thơm, không gian ấm cúng.", reply: "Cảm ơn anh A, hẹn gặp lại anh tại Hương Việt!" } },
  { id: "b4", date: "2026-09-02", time: "20:00", party: 3, name: "Nguyễn Văn A", phone: "0903 123 456", note: "", status: "cancelled" },
];

const initialTx: Transaction[] = [
  { id: "x1", table: "Bàn 04", total: 1240000, points: 62000, method: "Thẻ", at: now - 5 * 3600000 },
  { id: "x2", table: "Bàn 08", total: 865000, points: 43250, method: "Tiền mặt", at: now - 4 * 3600000 },
  { id: "x3", table: "Bàn 12", total: 3420000, points: 171000, method: "Chuyển khoản QR", at: now - 3 * 3600000 },
  { id: "x4", table: "Bàn 01", total: 512000, points: 25600, method: "Tiền mặt", at: now - 2 * 3600000 },
];

export const formatVND = (n: number) => new Intl.NumberFormat("vi-VN").format(Math.round(n)) + "đ";

type Store = {
  dishes: Dish[]; tables: Table[]; tickets: Ticket[]; bookings: Booking[]; transactions: Transaction[]; loyaltyBalance: number;
  dish: (id: string) => Dish | undefined;
  checkIn: (tableId: number) => void;
  sendToKitchen: (tableId: number, lines: OrderLine[], note: string) => void;
  completePayment: (tableId: number, total: number, cashback: number, redeemed: number, method: string) => void;
  advanceTicket: (id: string) => void;
  toggleStock: (id: string) => void;
  addBooking: (b: Omit<Booking, "id" | "status">) => void;
  reviewBooking: (id: string, stars: number, comment: string) => void;
};

const Ctx = createContext<Store | null>(null);

export function RestaurantProvider({ children }: { children: ReactNode }) {
  const [dishes, setDishes] = useState(initialDishes);
  const [tables, setTables] = useState(initialTables);
  const [tickets, setTickets] = useState(initialTickets);
  const [bookings, setBookings] = useState(initialBookings);
  const [transactions, setTx] = useState(initialTx);
  const [loyaltyBalance, setLoyalty] = useState(48000);

  const store: Store = {
    dishes, tables, tickets, bookings, transactions, loyaltyBalance,
    dish: (id) => dishes.find((d) => d.id === id),
    checkIn: (tableId) => setTables((ts) => ts.map((t) => (t.id === tableId ? { ...t, status: "occupied" } : t))),
    sendToKitchen: (tableId, lines, note) => {
      const table = tables.find((t) => t.id === tableId)!;
      setTables((ts) => ts.map((t) => {
        if (t.id !== tableId) return t;
        const order = [...t.order];
        lines.forEach((l) => {
          const ex = order.find((o) => o.dishId === l.dishId);
          if (ex) ex.qty += l.qty; else order.push({ ...l });
        });
        return { ...t, order: order.map((o) => ({ ...o })) };
      }));
      setTickets((tk) => [...tk, { id: crypto.randomUUID(), table: table.name, createdAt: Date.now(), items: lines, note, status: "queued" }]);
    },
    completePayment: (tableId, total, cashback, redeemed, method) => {
      const table = tables.find((t) => t.id === tableId)!;
      setDishes((ds) => ds.map((d) => ({ ...d, sold: d.sold + (table.order.find((o) => o.dishId === d.id)?.qty ?? 0) })));
      setTx((x) => [...x, { id: crypto.randomUUID(), table: table.name, total, points: cashback, method, at: Date.now() }]);
      setLoyalty((l) => l - redeemed + cashback);
      setTables((ts) => ts.map((t) => (t.id === tableId ? { ...t, status: "available", order: [], guest: undefined, time: undefined } : t)));
    },
    advanceTicket: (id) => setTickets((tk) => tk.map((t) => (t.id === id ? { ...t, status: t.status === "queued" ? "preparing" : "ready" } : t))),
    toggleStock: (id) => setDishes((ds) => ds.map((d) => (d.id === id ? { ...d, inStock: !d.inStock } : d))),
    addBooking: (b) => setBookings((bs) => [{ ...b, id: crypto.randomUUID(), status: "pending" }, ...bs]),
    reviewBooking: (id, stars, comment) => setBookings((bs) => bs.map((b) => (b.id === id ? { ...b, review: { stars, comment, reply: "Cảm ơn quý khách đã góp ý. Hương Việt rất mong được đón tiếp lần sau!" } } : b))),
  };
  return <Ctx.Provider value={store}>{children}</Ctx.Provider>;
}

export function useRestaurant() {
  const c = useContext(Ctx);
  if (!c) throw new Error("useRestaurant outside provider");
  return c;
}
