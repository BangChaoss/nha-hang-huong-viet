import { useEffect, useRef, useState } from "react";
import { Stage, Layer, Rect, Circle, Text, Group, Line } from "react-konva";
import type { KonvaEventObject } from "konva/lib/Node";
import { ZoomIn, ZoomOut, RotateCcw, Save, Sparkles, Users, Grid } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
  STATUS_COLORS,
  type FloorPlanLayoutResponse,
  type FloorPlanLayoutRequest,
  type TableStatus,
} from "@/lib/table-api";

export type FloorPlanCanvasProps = {
  layouts: FloorPlanLayoutResponse[];
  onSaveLayouts?: (layouts: FloorPlanLayoutRequest[]) => Promise<void>;
  onSelectTable?: (table: FloorPlanLayoutResponse | null) => void;
  onUpdateStatus?: (tableId: string, status: TableStatus) => Promise<void>;
  selectedTableId?: string | null;
  readOnly?: boolean;
  isConnected?: boolean;
  canvasHeight?: number;
};

const GRID_STEP = 20;

export function FloorPlanCanvas({
  layouts,
  onSaveLayouts,
  onSelectTable,
  onUpdateStatus,
  selectedTableId,
  readOnly = false,
  isConnected = false,
  canvasHeight,
}: FloorPlanCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [dimensions, setDimensions] = useState({ width: 900, height: canvasHeight ?? 600 });
  const [isMounted, setIsMounted] = useState(false);

  // Vị trí và zoom của canvas stage
  const [stageScale, setStageScale] = useState(1);
  const [stagePos, setStagePos] = useState({ x: 0, y: 0 });

  // Tọa độ cục bộ của các bàn khi kéo thả (tableId -> { x, y })
  const [localPositions, setLocalPositions] = useState<Record<string, { x: number; y: number }>>(
    {},
  );
  const [hasChanges, setHasChanges] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    setIsMounted(true);
  }, []);

  // Lắng nghe kích thước khung chứa để canvas tự co giãn linh hoạt
  useEffect(() => {
    if (!containerRef.current) return;
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) {
        setDimensions({
          width: Math.max(entry.contentRect.width, 400),
          height: 600,
        });
      }
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  // Đồng bộ localPositions khi `layouts` từ backend thay đổi
  useEffect(() => {
    const initialPos: Record<string, { x: number; y: number }> = {};
    for (const item of layouts) {
      initialPos[item.tableId] = {
        x: item.positionX,
        y: item.positionY,
      };
    }
    setLocalPositions(initialPos);
    setHasChanges(false);
  }, [layouts]);

  const handleZoom = (direction: "in" | "out") => {
    setStageScale((prev) => {
      const step = 0.15;
      const next = direction === "in" ? prev + step : prev - step;
      return Math.min(Math.max(next, 0.4), 2.5);
    });
  };

  const handleResetZoom = () => {
    setStageScale(1);
    setStagePos({ x: 0, y: 0 });
  };

  const handleWheel = (e: KonvaEventObject<WheelEvent>) => {
    e.evt.preventDefault();
    const scaleBy = 1.05;
    const oldScale = stageScale;
    const newScale = e.evt.deltaY < 0 ? oldScale * scaleBy : oldScale / scaleBy;
    setStageScale(Math.min(Math.max(newScale, 0.4), 2.5));
  };

  const handleDragNodeEnd = (tableId: string, e: KonvaEventObject<DragEvent>) => {
    // Grid snapping 20px
    const rawX = e.target.x();
    const rawY = e.target.y();
    const snappedX = Math.round(rawX / GRID_STEP) * GRID_STEP;
    const snappedY = Math.round(rawY / GRID_STEP) * GRID_STEP;

    if (typeof e.target.to === "function") {
      e.target.to({
        x: snappedX,
        y: snappedY,
        duration: 0.1,
      });
    } else {
      e.target.position({ x: snappedX, y: snappedY });
    }

    setLocalPositions((prev) => ({
      ...prev,
      [tableId]: { x: snappedX, y: snappedY },
    }));
    setHasChanges(true);
  };

  const handleSave = async () => {
    if (!onSaveLayouts || !hasChanges) return;
    setIsSaving(true);
    try {
      const requests: FloorPlanLayoutRequest[] = layouts.map((item) => {
        const pos = localPositions[item.tableId] ?? { x: item.positionX, y: item.positionY };
        return {
          tableId: item.tableId,
          positionX: pos.x,
          positionY: pos.y,
          width: item.width,
          height: item.height,
          rotation: item.rotation,
          shape: item.shape,
        };
      });
      await onSaveLayouts(requests);
      setHasChanges(false);
    } finally {
      setIsSaving(false);
    }
  };

  const selectedLayout = layouts.find((l) => l.tableId === selectedTableId) ?? null;

  if (!isMounted) {
    return (
      <div className="flex h-[600px] w-full items-center justify-center rounded-2xl border bg-muted/20">
        <p className="text-sm text-muted-foreground">Đang khởi tạo sơ đồ 2D...</p>
      </div>
    );
  }

  // Tạo các đường lưới grid nét đứt
  const gridLines = [];
  const width = dimensions.width / stageScale;
  const height = dimensions.height / stageScale;
  const gridStep = 40;

  for (let x = 0; x <= width + 500; x += gridStep) {
    gridLines.push(
      <Line
        key={`vx-${x}`}
        points={[x, -500, x, height + 500]}
        stroke="#e2e8f0"
        strokeWidth={0.5}
        dash={[4, 4]}
      />,
    );
  }
  for (let y = 0; y <= height + 500; y += gridStep) {
    gridLines.push(
      <Line
        key={`hy-${y}`}
        points={[-500, y, width + 500, y]}
        stroke="#e2e8f0"
        strokeWidth={0.5}
        dash={[4, 4]}
      />,
    );
  }

  return (
    <div className="space-y-4">
      {/* Thanh công cụ điều khiển sơ đồ */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-card p-3 shadow-xs">
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-1.5 rounded-lg border bg-background px-3 py-1.5 text-xs font-medium">
            <span
              className={cn(
                "h-2.5 w-2.5 rounded-full transition-colors",
                isConnected ? "bg-emerald-500 animate-pulse" : "bg-rose-500",
              )}
            />
            <span>{isConnected ? "Live" : "Mất kết nối"}</span>
          </div>

          <div className="flex items-center gap-1 rounded-lg border bg-background p-1">
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              onClick={() => handleZoom("in")}
              title="Phóng to"
            >
              <ZoomIn className="h-4 w-4" />
            </Button>
            <span className="w-12 text-center text-xs font-mono text-muted-foreground">
              {Math.round(stageScale * 100)}%
            </span>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              onClick={() => handleZoom("out")}
              title="Thu nhỏ"
            >
              <ZoomOut className="h-4 w-4" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              onClick={handleResetZoom}
              title="Đặt lại góc nhìn"
            >
              <RotateCcw className="h-3.5 w-3.5" />
            </Button>
          </div>

          <div className="hidden sm:flex items-center gap-1 text-xs text-muted-foreground">
            <Grid className="h-3.5 w-3.5" />
            <span>Hít lưới 20px · Rê nền để cuộn</span>
          </div>
        </div>

        {!readOnly && (
          <div className="flex items-center gap-2">
            {hasChanges && (
              <span className="text-xs font-medium text-amber-600 animate-pulse">
                Có thay đổi vị trí chưa lưu
              </span>
            )}
            <Button
              type="button"
              size="sm"
              disabled={!hasChanges || isSaving}
              onClick={() => void handleSave()}
              className="h-8 gap-1.5"
            >
              <Save className="h-3.5 w-3.5" />
              {isSaving ? "Đang lưu..." : "Lưu vị trí"}
            </Button>
          </div>
        )}
      </div>

      {/* Vùng Canvas chính và bảng chi tiết */}
      <div className="grid gap-4 lg:grid-cols-4">
        <div
          ref={containerRef}
          className="relative overflow-hidden rounded-2xl border bg-slate-50/50 shadow-inner lg:col-span-3"
        >
          <Stage
            width={dimensions.width}
            height={dimensions.height}
            scaleX={stageScale}
            scaleY={stageScale}
            x={stagePos.x}
            y={stagePos.y}
            draggable
            onWheel={handleWheel}
            onDragEnd={(e) => {
              if (e.target === e.target.getStage()) {
                setStagePos({ x: e.target.x(), y: e.target.y() });
              }
            }}
          >
            <Layer>
              {/* Lưới tọa độ ngầm */}
              {gridLines}

              {/* Render danh sách bàn ăn */}
              {layouts.map((item) => {
                const pos = localPositions[item.tableId] ?? {
                  x: item.positionX,
                  y: item.positionY,
                };
                const isSelected = item.tableId === selectedTableId;
                const statusMeta = STATUS_COLORS[item.status] ?? STATUS_COLORS.AVAILABLE;
                const isCircle = item.shape === "CIRCLE";
                const nodeWidth = item.width;
                const nodeHeight = item.height;

                return (
                  <Group
                    key={item.tableId}
                    x={pos.x}
                    y={pos.y}
                    rotation={item.rotation}
                    draggable={!readOnly}
                    onClick={() => onSelectTable?.(item)}
                    onTap={() => onSelectTable?.(item)}
                    onDragEnd={(e) => handleDragNodeEnd(item.tableId, e)}
                  >
                    {isCircle ? (
                      <Circle
                        radius={nodeWidth / 2}
                        x={nodeWidth / 2}
                        y={nodeHeight / 2}
                        fill={statusMeta.bg}
                        stroke={isSelected ? "#09090b" : "#475569"}
                        strokeWidth={isSelected ? 3.5 : 1.5}
                        shadowColor="rgba(0,0,0,0.2)"
                        shadowBlur={isSelected ? 10 : 4}
                        shadowOffset={{ x: 2, y: 3 }}
                        shadowOpacity={0.3}
                      />
                    ) : (
                      <Rect
                        width={nodeWidth}
                        height={nodeHeight}
                        cornerRadius={10}
                        fill={statusMeta.bg}
                        stroke={isSelected ? "#09090b" : "#475569"}
                        strokeWidth={isSelected ? 3.5 : 1.5}
                        shadowColor="rgba(0,0,0,0.2)"
                        shadowBlur={isSelected ? 10 : 4}
                        shadowOffset={{ x: 2, y: 3 }}
                        shadowOpacity={0.3}
                      />
                    )}

                    {/* Nhãn số bàn */}
                    <Text
                      text={item.tableNumber ?? ""}
                      x={0}
                      y={nodeHeight / 2 - 16}
                      width={nodeWidth}
                      align="center"
                      fontSize={15}
                      fontFamily="sans-serif"
                      fontStyle="bold"
                      fill={statusMeta.text}
                    />

                    {/* Sức chứa và VIP */}
                    <Text
                      text={`${item.capacity ?? 0}p${item.zoneIsVip ? " ★" : ""}`}
                      x={0}
                      y={nodeHeight / 2 + 3}
                      width={nodeWidth}
                      align="center"
                      fontSize={11}
                      fontFamily="sans-serif"
                      fill={statusMeta.text}
                    />
                  </Group>
                );
              })}
            </Layer>
          </Stage>
        </div>

        {/* Cột thông tin chi tiết bàn được chọn & Chú thích */}
        <div className="space-y-4">
          <div className="rounded-2xl border bg-card p-4 shadow-xs">
            <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider">
              Bàn được chọn
            </h3>
            {selectedLayout ? (
              <div className="mt-3 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-2xl font-bold font-display">
                    {selectedLayout.tableNumber}
                  </span>
                  <Badge
                    variant="outline"
                    className={cn("font-semibold", STATUS_COLORS[selectedLayout.status]?.badgeCls)}
                  >
                    {STATUS_COLORS[selectedLayout.status]?.label}
                  </Badge>
                </div>

                <div className="space-y-1.5 text-xs text-muted-foreground">
                  <div className="flex items-center justify-between">
                    <span>Khu vực:</span>
                    <span className="font-medium text-foreground flex items-center gap-1">
                      {selectedLayout.zoneName ?? "Chưa phân"}
                      {selectedLayout.zoneIsVip && (
                        <Sparkles className="h-3 w-3 text-amber-500 inline" />
                      )}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span>Sức chứa:</span>
                    <span className="font-medium text-foreground flex items-center gap-1">
                      <Users className="h-3 w-3" />
                      {selectedLayout.capacity} khách
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span>Hình dạng:</span>
                    <span className="font-medium text-foreground">
                      {selectedLayout.shape === "CIRCLE" ? "Bàn tròn" : "Bàn chữ nhật"}
                    </span>
                  </div>
                </div>

                {!readOnly && onUpdateStatus && (
                  <div className="pt-3 border-t space-y-2">
                    <p className="text-xs font-medium text-foreground">Trạng thái phục vụ:</p>
                    <div className="flex items-center gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant={selectedLayout.status !== "MAINTENANCE" ? "default" : "outline"}
                        className={cn(
                          "h-8 text-xs flex-1",
                          selectedLayout.status !== "MAINTENANCE" && "bg-emerald-600 hover:bg-emerald-700 text-white"
                        )}
                        disabled={selectedLayout.status === "AVAILABLE" || selectedLayout.status === "OCCUPIED" || selectedLayout.status === "RESERVED"}
                        onClick={() => void onUpdateStatus(selectedLayout.tableId, "AVAILABLE")}
                      >
                        Đang sử dụng
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant={selectedLayout.status === "MAINTENANCE" ? "destructive" : "outline"}
                        className="h-8 text-xs flex-1"
                        disabled={selectedLayout.status === "MAINTENANCE" || selectedLayout.status === "OCCUPIED" || selectedLayout.status === "RESERVED"}
                        onClick={() => void onUpdateStatus(selectedLayout.tableId, "MAINTENANCE")}
                      >
                        Tạm ẩn bàn
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="mt-4 rounded-xl border border-dashed p-6 text-center text-xs text-muted-foreground">
                Nhấp chuột vào một bàn trên sơ đồ để xem thông tin chi tiết và thao tác.
              </div>
            )}
          </div>

          {/* Bảng chú thích trạng thái */}
          <div className="rounded-2xl border bg-card p-4 shadow-xs space-y-2.5">
            <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Chú thích trạng thái
            </h3>
            <div className="space-y-2 text-xs">
              {(Object.keys(STATUS_COLORS) as TableStatus[]).map((st) => (
                <div key={st} className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span
                      className={cn("h-3 w-3 rounded-full shadow-xs", STATUS_COLORS[st].dotCls)}
                    />
                    <span className="font-medium">{STATUS_COLORS[st].label}</span>
                  </div>
                  <span className="text-[10px] text-muted-foreground font-mono">{st}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
