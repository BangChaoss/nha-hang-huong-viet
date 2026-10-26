import { useEffect, useState, useMemo, lazy, Suspense } from "react";
import {
  LayoutGrid,
  ListFilter,
  Plus,
  Trash2,
  Edit2,
  AlertTriangle,
  RefreshCw,
  Sparkles,
  Users,
  Search,
  Layers,
  ShieldAlert,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  zoneApi,
  tableApi,
  floorPlanApi,
  type ZoneResponse,
  type ZoneRequest,
  type TableResponse,
  type TableRequest,
  type TableStatus,
  type FloorPlanLayoutResponse,
  type FloorPlanLayoutRequest,
  type TableStatusMessage,
  STATUS_COLORS,
} from "@/lib/table-api";
import { useTableWebSocket } from "@/lib/use-table-websocket";
const FloorPlanCanvas = lazy(() =>
  import("@/components/hv/FloorPlanCanvas").then((m) => ({
    default: m.FloorPlanCanvas,
  })),
);
import { cn } from "@/lib/utils";

export function TableManagement() {
  const [activeTab, setActiveTab] = useState<"floor-plan" | "list">("floor-plan");

  // Dữ liệu từ API
  const [zones, setZones] = useState<ZoneResponse[]>([]);
  const [tables, setTables] = useState<TableResponse[]>([]);
  const [layouts, setLayouts] = useState<FloorPlanLayoutResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  // Bộ lọc danh sách bàn
  const [searchQuery, setSearchQuery] = useState("");
  const [filterZoneId, setFilterZoneId] = useState<string>("ALL");
  const [selectedCanvasTableId, setSelectedCanvasTableId] = useState<string | null>(null);

  // Dialog Khu vực
  const [isZoneDialogOpen, setIsZoneDialogOpen] = useState(false);
  const [editingZone, setEditingZone] = useState<ZoneResponse | null>(null);
  const [zoneNameInput, setZoneNameInput] = useState("");
  const [zoneIsVipInput, setZoneIsVipInput] = useState(false);
  const [isSubmittingZone, setIsSubmittingZone] = useState(false);

  // Dialog Bàn ăn
  const [isTableDialogOpen, setIsTableDialogOpen] = useState(false);
  const [editingTable, setEditingTable] = useState<TableResponse | null>(null);
  const [tableNumberInput, setTableNumberInput] = useState("");
  const [tableCapacityInput, setTableCapacityInput] = useState("4");
  const [tableZoneIdInput, setTableZoneIdInput] = useState("");
  const [isSubmittingTable, setIsSubmittingTable] = useState(false);

  // Dialog Xóa bàn ăn
  const [deletingTable, setDeletingTable] = useState<TableResponse | null>(null);
  const [isDeletingTable, setIsDeletingTable] = useState(false);

  // Dialog Xóa khu vực
  const [deletingZone, setDeletingZone] = useState<ZoneResponse | null>(null);
  const [isDeletingZone, setIsDeletingZone] = useState(false);

  // Tải dữ liệu toàn bộ từ Backend
  const loadData = async () => {
    setIsLoading(true);
    try {
      const [fetchedZones, fetchedTables, fetchedLayouts] = await Promise.all([
        zoneApi.listAdmin(),
        tableApi.listAdmin(),
        floorPlanApi.getAll(),
      ]);
      setZones(fetchedZones);
      setTables(fetchedTables);
      setLayouts(fetchedLayouts);
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Không tải được danh mục bàn và khu vực.",
      );
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  // Tích hợp WebSocket Real-time
  const { isConnected } = useTableWebSocket({
    onTableStatusChange: (message: TableStatusMessage) => {
      // Cập nhật trạng thái bàn trong state bảng dữ liệu
      setTables((prev) =>
        prev.map((t) => (t.id === message.tableId ? { ...t, status: message.newStatus } : t)),
      );

      // Cập nhật trạng thái bàn trong state sơ đồ floor plan
      setLayouts((prev) =>
        prev.map((l) => (l.tableId === message.tableId ? { ...l, status: message.newStatus } : l)),
      );

      const oldLabel = STATUS_COLORS[message.oldStatus]?.label ?? message.oldStatus;
      const newLabel = STATUS_COLORS[message.newStatus]?.label ?? message.newStatus;
      toast.info(`Bàn ${message.tableNumber}: ${oldLabel} → ${newLabel}`, {
        description: message.zoneName ? `Khu vực: ${message.zoneName}` : undefined,
      });
    },
    onFloorPlanChange: (updatedLayouts: FloorPlanLayoutResponse[]) => {
      setLayouts(updatedLayouts);
      toast.success("Sơ đồ bàn vừa được cập nhật từ quản trị viên khác.");
    },
  });

  // Lưu sơ đồ bàn khi kéo thả xong
  const handleSaveLayouts = async (newLayouts: FloorPlanLayoutRequest[]) => {
    try {
      const saved = await floorPlanApi.saveAll(newLayouts);
      setLayouts(saved);
      toast.success("Đã lưu vị trí sơ đồ bàn thành công.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Lỗi khi lưu vị trí sơ đồ bàn.");
      throw error;
    }
  };

  // Cập nhật nhanh trạng thái bàn
  const handleUpdateStatus = async (tableId: string, status: TableStatus) => {
    try {
      const updated = await tableApi.updateStatus(tableId, status);
      setTables((prev) => prev.map((t) => (t.id === tableId ? updated : t)));
      setLayouts((prev) =>
        prev.map((l) => (l.tableId === tableId ? { ...l, status: updated.status } : l)),
      );
      toast.success(
        `Đã đổi bàn ${updated.tableNumber} sang ${STATUS_COLORS[status]?.label ?? status}`,
      );
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Không đổi được trạng thái bàn.");
    }
  };

  // Mở dialog thêm/sửa khu vực
  const handleOpenZoneDialog = (zone?: ZoneResponse) => {
    if (zone) {
      setEditingZone(zone);
      setZoneNameInput(zone.zoneName);
      setZoneIsVipInput(zone.isVip);
    } else {
      setEditingZone(null);
      setZoneNameInput("");
      setZoneIsVipInput(false);
    }
    setIsZoneDialogOpen(true);
  };

  const handleSubmitZone = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!zoneNameInput.trim()) {
      toast.error("Vui lòng nhập tên khu vực.");
      return;
    }
    setIsSubmittingZone(true);
    try {
      const payload: ZoneRequest = {
        zoneName: zoneNameInput.trim(),
        isVip: zoneIsVipInput,
      };
      if (editingZone) {
        const updated = await zoneApi.update(editingZone.id, payload);
        setZones((prev) => prev.map((z) => (z.id === editingZone.id ? updated : z)));
        toast.success(`Đã cập nhật khu vực "${updated.zoneName}".`);
      } else {
        const created = await zoneApi.create(payload);
        setZones((prev) => [...prev, created]);
        toast.success(`Đã thêm khu vực "${created.zoneName}".`);
      }
      setIsZoneDialogOpen(false);
      void loadData();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Lỗi khi lưu khu vực.");
    } finally {
      setIsSubmittingZone(false);
    }
  };

  const handleDeleteZone = async () => {
    if (!deletingZone) return;
    if (deletingZone.tableCount > 0) {
      toast.error(
        `Không thể xóa khu vực "${deletingZone.zoneName}" vì vẫn còn ${deletingZone.tableCount} bàn trực thuộc. Vui lòng chuyển hoặc xóa các bàn trước.`,
      );
      setDeletingZone(null);
      return;
    }
    setIsDeletingZone(true);
    try {
      await zoneApi.remove(deletingZone.id);
      setZones((prev) => prev.filter((z) => z.id !== deletingZone.id));
      toast.success(`Đã xóa khu vực "${deletingZone.zoneName}".`);
      setDeletingZone(null);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Lỗi khi xóa khu vực.");
    } finally {
      setIsDeletingZone(false);
    }
  };

  // Mở dialog thêm/sửa bàn ăn
  const handleOpenTableDialog = (tableItem?: TableResponse) => {
    if (tableItem) {
      // Ràng buộc BR-56: Ngăn chặn sửa bàn đang ăn hoặc đã đặt trước
      if (tableItem.status === "OCCUPIED" || tableItem.status === "RESERVED") {
        toast.error("Không thể chỉnh sửa bàn đang có khách hoặc đã đặt trước (BR-56).");
        return;
      }
      setEditingTable(tableItem);
      setTableNumberInput(tableItem.tableNumber);
      setTableCapacityInput(String(tableItem.capacity));
      setTableZoneIdInput(tableItem.zoneId);
    } else {
      setEditingTable(null);
      setTableNumberInput("");
      setTableCapacityInput("4");
      setTableZoneIdInput(zones[0]?.id ?? "");
    }
    setIsTableDialogOpen(true);
  };

  const handleSubmitTable = async (e: React.FormEvent) => {
    e.preventDefault();
    const capacityNum = Number(tableCapacityInput);
    if (!tableNumberInput.trim()) {
      toast.error("Vui lòng nhập số hiệu bàn.");
      return;
    }
    if (!tableZoneIdInput) {
      toast.error("Vui lòng chọn khu vực.");
      return;
    }
    if (!Number.isFinite(capacityNum) || capacityNum <= 0) {
      toast.error("Sức chứa phải lớn hơn 0.");
      return;
    }

    setIsSubmittingTable(true);
    try {
      const payload: TableRequest = {
        tableNumber: tableNumberInput.trim(),
        capacity: capacityNum,
        zoneId: tableZoneIdInput,
      };

      if (editingTable) {
        const updated = await tableApi.update(editingTable.id, payload);
        setTables((prev) => prev.map((t) => (t.id === editingTable.id ? updated : t)));
        toast.success(`Đã cập nhật bàn "${updated.tableNumber}".`);
      } else {
        const created = await tableApi.create(payload);
        setTables((prev) => [...prev, created]);
        toast.success(`Đã thêm bàn "${created.tableNumber}".`);
      }
      setIsTableDialogOpen(false);
      void loadData();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Lỗi khi lưu bàn ăn.");
    } finally {
      setIsSubmittingTable(false);
    }
  };

  const handleDeleteTable = async () => {
    if (!deletingTable) return;
    // Ràng buộc BR-56: Ngăn chặn xóa bàn đang ăn hoặc đã đặt trước
    if (deletingTable.status === "OCCUPIED" || deletingTable.status === "RESERVED") {
      toast.error("Không thể xóa bàn đang có khách hoặc đã đặt trước (BR-56).");
      setDeletingTable(null);
      return;
    }

    setIsDeletingTable(true);
    try {
      await tableApi.remove(deletingTable.id);
      setTables((prev) => prev.filter((t) => t.id !== deletingTable.id));
      setLayouts((prev) => prev.filter((l) => l.tableId !== deletingTable.id));
      toast.success(`Đã xóa bàn "${deletingTable.tableNumber}".`);
      setDeletingTable(null);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Lỗi khi xóa bàn ăn.");
    } finally {
      setIsDeletingTable(false);
    }
  };

  // Lọc danh sách bàn
  const filteredTables = useMemo(() => {
    return tables.filter((t) => {
      const matchesSearch = t.tableNumber.toLowerCase().includes(searchQuery.toLowerCase().trim());
      const matchesZone = filterZoneId === "ALL" || t.zoneId === filterZoneId;
      return matchesSearch && matchesZone;
    });
  }, [tables, searchQuery, filterZoneId]);

  return (
    <div className="space-y-6">
      {/* Tiêu đề & Chuyển đổi chế độ xem */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Quản lý Bàn & Sơ đồ mặt bằng</h2>
          <p className="text-sm text-muted-foreground">
            Quản trị khu vực, thiết lập sơ đồ 2D và giám sát trạng thái bàn thời gian thực.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void loadData()}
            disabled={isLoading}
            className="gap-1.5"
          >
            <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} />
            Làm mới
          </Button>

          <Tabs
            value={activeTab}
            onValueChange={(v) => setActiveTab(v as "floor-plan" | "list")}
            className="w-auto"
          >
            <TabsList>
              <TabsTrigger value="floor-plan" className="gap-1.5">
                <LayoutGrid className="h-4 w-4" />
                Sơ đồ 2D
              </TabsTrigger>
              <TabsTrigger value="list" className="gap-1.5">
                <ListFilter className="h-4 w-4" />
                Danh sách
              </TabsTrigger>
            </TabsList>
          </Tabs>
        </div>
      </div>

      {/* CHẾ ĐỘ SƠ ĐỒ BÀN 2D */}
      {activeTab === "floor-plan" && (
        <section className="space-y-4">
          <Suspense
            fallback={
              <div className="flex h-[600px] w-full items-center justify-center rounded-2xl border bg-muted/20">
                <p className="text-sm text-muted-foreground">Đang tải sơ đồ bàn...</p>
              </div>
            }
          >
            <FloorPlanCanvas
              layouts={layouts}
              onSaveLayouts={handleSaveLayouts}
              onUpdateStatus={handleUpdateStatus}
              onSelectTable={(tbl) => setSelectedCanvasTableId(tbl?.tableId ?? null)}
              selectedTableId={selectedCanvasTableId}
              isConnected={isConnected}
            />
          </Suspense>
        </section>
      )}

      {/* CHẾ ĐỘ DANH SÁCH BÀN & KHU VỰC */}
      {activeTab === "list" && (
        <div className="space-y-8">
          {/* 1. KHU VỰC NHÀ HÀNG (ZONES) */}
          <section className="surface-card p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h3 className="text-xl font-semibold flex items-center gap-2">
                  <Layers className="h-5 w-5 text-primary" />
                  Khu vực nhà hàng
                </h3>
                <p className="text-xs text-muted-foreground">
                  Phân chia các khu vực phục vụ (Trong nhà, Ngoài trời, Phòng VIP...).
                </p>
              </div>

              <Button
                type="button"
                size="sm"
                onClick={() => handleOpenZoneDialog()}
                className="gap-1.5"
              >
                <Plus className="h-4 w-4" />
                Thêm khu vực
              </Button>
            </div>

            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {zones.map((zone) => (
                <div
                  key={zone.id}
                  className="flex items-center justify-between rounded-xl border bg-background/50 p-4 transition hover:border-primary/40"
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-base">{zone.zoneName}</span>
                      {zone.isVip && (
                        <Badge
                          variant="outline"
                          className="bg-amber-500/10 text-amber-600 border-amber-500/20 text-xs font-medium"
                        >
                          <Sparkles className="mr-1 h-3 w-3 inline" />
                          VIP
                        </Badge>
                      )}
                    </div>
                    <p className="text-xs text-muted-foreground flex items-center gap-1">
                      <Users className="h-3.5 w-3.5" />
                      {zone.tableCount} bàn trực thuộc
                    </p>
                  </div>

                  <div className="flex items-center gap-1">
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      className="h-8 w-8"
                      onClick={() => handleOpenZoneDialog(zone)}
                      title="Sửa khu vực"
                    >
                      <Edit2 className="h-4 w-4" />
                    </Button>
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      className="h-8 w-8 text-destructive hover:text-destructive"
                      onClick={() => setDeletingZone(zone)}
                      title="Xóa khu vực"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* 2. DANH SÁCH BÀN ĂN (TABLES) */}
          <section className="surface-card p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h3 className="text-xl font-semibold flex items-center gap-2">
                  <LayoutGrid className="h-5 w-5 text-primary" />
                  Danh sách bàn ăn
                </h3>
                <p className="text-xs text-muted-foreground">
                  Số hiệu bàn, sức chứa và quản lý trạng thái trực tiếp.
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <div className="relative w-48">
                  <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder="Tìm số bàn..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-8 h-9 text-xs"
                  />
                </div>

                <Select value={filterZoneId} onValueChange={setFilterZoneId}>
                  <SelectTrigger className="w-40 h-9 text-xs">
                    <SelectValue placeholder="Tất cả khu vực" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">Tất cả khu vực</SelectItem>
                    {zones.map((z) => (
                      <SelectItem key={z.id} value={z.id}>
                        {z.zoneName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>

                <Button
                  type="button"
                  size="sm"
                  onClick={() => handleOpenTableDialog()}
                  className="gap-1.5 h-9"
                >
                  <Plus className="h-4 w-4" />
                  Thêm bàn
                </Button>
              </div>
            </div>

            {/* Bảng dữ liệu bàn ăn */}
            <div className="overflow-x-auto rounded-xl border bg-background">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-24 font-semibold">Số bàn</TableHead>
                    <TableHead className="font-semibold">Khu vực</TableHead>
                    <TableHead className="font-semibold">Sức chứa</TableHead>
                    <TableHead className="font-semibold">Trạng thái hiện tại</TableHead>
                    <TableHead className="font-semibold">Đổi trạng thái nhanh</TableHead>
                    <TableHead className="text-right font-semibold">Thao tác</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredTables.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                        Không tìm thấy bàn nào phù hợp tiêu chí.
                      </TableCell>
                    </TableRow>
                  ) : (
                    filteredTables.map((tbl) => {
                      const isLocked = tbl.status === "OCCUPIED" || tbl.status === "RESERVED";
                      const statusMeta = STATUS_COLORS[tbl.status] ?? STATUS_COLORS.AVAILABLE;

                      return (
                        <TableRow key={tbl.id}>
                          <TableCell className="font-bold font-display text-base">
                            {tbl.tableNumber}
                          </TableCell>
                          <TableCell>
                            <div className="flex items-center gap-1.5 text-sm">
                              <span>{tbl.zoneName ?? "—"}</span>
                              {tbl.zoneIsVip && (
                                <Badge
                                  variant="outline"
                                  className="bg-amber-500/10 text-amber-600 border-amber-500/20 text-[10px] px-1.5 py-0"
                                >
                                  VIP
                                </Badge>
                              )}
                            </div>
                          </TableCell>
                          <TableCell className="text-sm">
                            <span className="flex items-center gap-1">
                              <Users className="h-3.5 w-3.5 text-muted-foreground" />
                              {tbl.capacity} người
                            </span>
                          </TableCell>
                          <TableCell>
                            <div className="flex items-center gap-2.5">
                              <Switch
                                checked={tbl.status !== "MAINTENANCE"}
                                disabled={isLocked}
                                onCheckedChange={(checked) => {
                                  const newStatus: TableStatus = checked ? "AVAILABLE" : "MAINTENANCE";
                                  void handleUpdateStatus(tbl.id, newStatus);
                                }}
                              />
                              <span className="text-xs font-medium">
                                {tbl.status === "MAINTENANCE" ? (
                                  <Badge variant="outline" className="bg-slate-500/10 text-slate-600 border-slate-500/20 text-xs">
                                    Tạm ẩn
                                  </Badge>
                                ) : tbl.status === "OCCUPIED" ? (
                                  <Badge variant="outline" className="bg-blue-500/10 text-blue-600 border-blue-500/20 text-xs">
                                    Đang phục vụ
                                  </Badge>
                                ) : tbl.status === "RESERVED" ? (
                                  <Badge variant="outline" className="bg-amber-500/10 text-amber-600 border-amber-500/20 text-xs">
                                    Đã đặt trước
                                  </Badge>
                                ) : (
                                  <Badge variant="outline" className="bg-emerald-500/10 text-emerald-600 border-emerald-500/20 text-xs">
                                    Đang sử dụng
                                  </Badge>
                                )}
                              </span>
                            </div>
                          </TableCell>
                          <TableCell className="text-right">
                            <div className="flex items-center justify-end gap-1">
                              {/* Ràng buộc BR-56: Vô hiệu hóa nút Sửa và Xóa đối với bàn RESERVED hoặc OCCUPIED */}
                              <Button
                                type="button"
                                size="icon"
                                variant="ghost"
                                className="h-8 w-8 disabled:opacity-40"
                                disabled={isLocked}
                                onClick={() => handleOpenTableDialog(tbl)}
                                title={
                                  isLocked
                                    ? "Không thể sửa bàn đang có khách hoặc đã đặt trước (BR-56)"
                                    : "Sửa thông tin bàn"
                                }
                              >
                                <Edit2 className="h-4 w-4" />
                              </Button>
                              <Button
                                type="button"
                                size="icon"
                                variant="ghost"
                                className="h-8 w-8 text-destructive hover:text-destructive disabled:opacity-40"
                                disabled={isLocked}
                                onClick={() => setDeletingTable(tbl)}
                                title={
                                  isLocked
                                    ? "Không thể xóa bàn đang có khách hoặc đã đặt trước (BR-56)"
                                    : "Xóa bàn"
                                }
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })
                  )}
                </TableBody>
              </Table>
            </div>
          </section>
        </div>
      )}

      {/* DIALOG THÊM / SỬA KHU VỰC */}
      <Dialog open={isZoneDialogOpen} onOpenChange={setIsZoneDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editingZone ? "Chỉnh sửa khu vực" : "Thêm khu vực mới"}</DialogTitle>
            <DialogDescription>
              Tạo hoặc điều chỉnh tên và trạng thái VIP của khu vực phục vụ.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmitZone} className="space-y-4 py-2">
            <div className="space-y-1.5">
              <label htmlFor="zoneName" className="text-sm font-medium">
                Tên khu vực
              </label>
              <Input
                id="zoneName"
                required
                maxLength={50}
                placeholder="Ví dụ: Sân Vườn, Phòng VIP 1"
                value={zoneNameInput}
                onChange={(e) => setZoneNameInput(e.target.value)}
              />
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <div className="space-y-0.5">
                <span className="text-sm font-medium">Khu vực VIP</span>
                <p className="text-xs text-muted-foreground">
                  Đánh dấu bàn thuộc khu vực này với biểu tượng VIP đặc biệt.
                </p>
              </div>
              <Switch checked={zoneIsVipInput} onCheckedChange={setZoneIsVipInput} />
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsZoneDialogOpen(false)}
                disabled={isSubmittingZone}
              >
                Hủy
              </Button>
              <Button type="submit" disabled={isSubmittingZone}>
                {isSubmittingZone ? "Đang lưu..." : "Lưu khu vực"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* DIALOG THÊM / SỬA BÀN ĂN */}
      <Dialog open={isTableDialogOpen} onOpenChange={setIsTableDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editingTable ? "Chỉnh sửa bàn ăn" : "Thêm bàn ăn mới"}</DialogTitle>
            <DialogDescription>
              Thiết lập số hiệu bàn, sức chứa khách và khu vực trực thuộc (BR-57: Số bàn là duy
              nhất).
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmitTable} className="space-y-4 py-2">
            <div className="space-y-1.5">
              <label htmlFor="tableNumber" className="text-sm font-medium">
                Số hiệu bàn
              </label>
              <Input
                id="tableNumber"
                required
                maxLength={20}
                placeholder="Ví dụ: Bàn 01, VIP-01"
                value={tableNumberInput}
                onChange={(e) => setTableNumberInput(e.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="tableCapacity" className="text-sm font-medium">
                Sức chứa (số ghế)
              </label>
              <Input
                id="tableCapacity"
                type="number"
                required
                min={1}
                max={50}
                value={tableCapacityInput}
                onChange={(e) => setTableCapacityInput(e.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="tableZone" className="text-sm font-medium">
                Khu vực
              </label>
              <Select value={tableZoneIdInput} onValueChange={setTableZoneIdInput}>
                <SelectTrigger id="tableZone" className="w-full">
                  <SelectValue placeholder="Chọn khu vực..." />
                </SelectTrigger>
                <SelectContent>
                  {zones.map((z) => (
                    <SelectItem key={z.id} value={z.id}>
                      {z.zoneName} {z.isVip ? "(VIP)" : ""}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsTableDialogOpen(false)}
                disabled={isSubmittingTable}
              >
                Hủy
              </Button>
              <Button type="submit" disabled={isSubmittingTable}>
                {isSubmittingTable ? "Đang lưu..." : "Lưu bàn"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* DIALOG XÁC NHẬN XÓA BÀN ĂN (BR-56 CHECK) */}
      <Dialog
        open={Boolean(deletingTable)}
        onOpenChange={(open) => !open && setDeletingTable(null)}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-destructive">
              <AlertTriangle className="h-5 w-5" />
              Xác nhận xóa bàn ăn
            </DialogTitle>
            <DialogDescription>
              Bạn có chắc chắn muốn xóa bàn{" "}
              <strong className="text-foreground">{deletingTable?.tableNumber}</strong> khỏi hệ
              thống không? Hành động này sẽ đồng thời loại bỏ vị trí của bàn trên sơ đồ 2D.
            </DialogDescription>
          </DialogHeader>

          {deletingTable &&
            (deletingTable.status === "OCCUPIED" || deletingTable.status === "RESERVED") && (
              <div className="flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive">
                <ShieldAlert className="h-4 w-4 shrink-0" />
                <span>
                  Cảnh báo BR-56: Bàn đang ở trạng thái {STATUS_COLORS[deletingTable.status]?.label}
                  . Backend sẽ chặn thao tác xóa này!
                </span>
              </div>
            )}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setDeletingTable(null)}
              disabled={isDeletingTable}
            >
              Hủy
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => void handleDeleteTable()}
              disabled={isDeletingTable}
            >
              {isDeletingTable ? "Đang xóa..." : "Xóa bàn"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* DIALOG XÁC NHẬN XÓA KHU VỰC */}
      <Dialog open={Boolean(deletingZone)} onOpenChange={(open) => !open && setDeletingZone(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-destructive">
              <AlertTriangle className="h-5 w-5" />
              Xác nhận xóa khu vực
            </DialogTitle>
            <DialogDescription>
              Bạn có chắc muốn xóa khu vực{" "}
              <strong className="text-foreground">{deletingZone?.zoneName}</strong>?
            </DialogDescription>
          </DialogHeader>

          {deletingZone && deletingZone.tableCount > 0 && (
            <div className="flex items-center gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-700">
              <AlertTriangle className="h-4 w-4 shrink-0" />
              <span>
                Khu vực này hiện đang có {deletingZone.tableCount} bàn. Bạn phải chuyển hoặc xóa các
                bàn này trước khi xóa khu vực.
              </span>
            </div>
          )}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setDeletingZone(null)}
              disabled={isDeletingZone}
            >
              Hủy
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={isDeletingZone || (deletingZone ? deletingZone.tableCount > 0 : false)}
              onClick={() => void handleDeleteZone()}
            >
              {isDeletingZone ? "Đang xóa..." : "Xóa khu vực"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
