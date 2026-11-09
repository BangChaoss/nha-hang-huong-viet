import { useEffect, useRef, useState, useMemo, useCallback } from "react";
import { Stage, Layer, Rect, Circle, Text, Group, Line } from "react-konva";
import type { KonvaEventObject } from "konva/lib/Node";
import {
  ZoomIn,
  ZoomOut,
  RotateCcw,
  Save,
  Sparkles,
  Users,
  Grid,
  Undo2,
  Redo2,
  RotateCw,
  Shapes,
  Maximize2,
  Maximize,
  AlertTriangle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import {
  STATUS_COLORS,
  type FloorPlanLayoutResponse,
  type FloorPlanLayoutRequest,
  type TableStatus,
  type FloorPlanShape,
} from "@/lib/table-api";

export type FloorPlanCanvasProps = {
  layouts: FloorPlanLayoutResponse[];
  onSaveLayouts?: (layouts: FloorPlanLayoutRequest[]) => Promise<void>;
  onSelectTable?: (table: FloorPlanLayoutResponse | null) => void;
  onUpdateStatus?: (tableId: string, status: TableStatus) => Promise<void>;
  selectedTableId?: string | null;
  selectedTableIds?: string[];
  readOnly?: boolean;
  isConnected?: boolean;
  canvasHeight?: number;
};

export type LocalTableLayout = {
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: number;
  shape: FloorPlanShape;
};

type GuideLine = {
  points: number[];
  orientation: "vertical" | "horizontal";
  stroke?: string;
};

const SNAP_THRESHOLD = 10;
const MAX_HISTORY = 50;

/**
 * Chuẩn hóa kích thước bàn theo số ghế tiêu chuẩn
 * - 1–2 khách: 70 × 70px (Bán kính bàn tròn: 35px)
 * - 3–4 khách: 90 × 90px (Bán kính bàn tròn: 45px)
 * - 5+ khách:  140 × 90px (Bàn chữ nhật) hoặc đường kính 120px (Bàn tròn)
 */
export function getStandardDimensions(
  capacity: number,
  shape: FloorPlanShape,
): { width: number; height: number } {
  if (shape === "CIRCLE") {
    const diameter = capacity <= 2 ? 70 : capacity <= 4 ? 90 : 120;
    return { width: diameter, height: diameter };
  }
  if (capacity <= 2) return { width: 70, height: 70 };
  if (capacity <= 4) return { width: 90, height: 90 };
  return { width: 140, height: 90 };
}

/**
 * Kiểm tra va chạm AABB (Axis-Aligned Bounding Box) giữa các bàn đang hiển thị
 */
function computeCollisions(
  layoutsMap: Record<string, LocalTableLayout>,
  visibleIds: string[],
): Set<string> {
  const colliding = new Set<string>();
  for (let i = 0; i < visibleIds.length; i++) {
    const idA = visibleIds[i]!;
    const a = layoutsMap[idA];
    if (!a) continue;
    for (let j = i + 1; j < visibleIds.length; j++) {
      const idB = visibleIds[j]!;
      const b = layoutsMap[idB];
      if (!b) continue;

      const overlap =
        a.x < b.x + b.width &&
        a.x + a.width > b.x &&
        a.y < b.y + b.height &&
        a.y + a.height > b.y;

      if (overlap) {
        colliding.add(idA);
        colliding.add(idB);
      }
    }
  }
  return colliding;
}

export function FloorPlanCanvas({
  layouts,
  onSaveLayouts,
  onSelectTable,
  onUpdateStatus,
  selectedTableId,
  selectedTableIds,
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

  // Tọa độ & hình dáng cục bộ của các bàn (tableId -> LocalTableLayout)
  const [localLayouts, setLocalLayouts] = useState<Record<string, LocalTableLayout>>({});
  const [hasChanges, setHasChanges] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  // Lịch sử hoàn tác / làm lại (Undo / Redo stack)
  const [history, setHistory] = useState<Record<string, LocalTableLayout>[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);

  // Đường gióng căn chỉnh từ tính (Magnetic Snapping Guidelines)
  const [activeGuides, setActiveGuides] = useState<GuideLine[]>([]);

  // Danh sách các bàn đang bị đè/trùng lặp vị trí (Collision warning)
  const [collidingTableIds, setCollidingTableIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    setIsMounted(true);
  }, []);

  // Bàn tạm ẩn (MAINTENANCE) bị loại bỏ khỏi 2D canvas render
  const visibleLayouts = useMemo(() => {
    return layouts.filter((item) => item.status !== "MAINTENANCE");
  }, [layouts]);

  // Lắng nghe kích thước khung chứa để canvas tự co giãn linh hoạt
  useEffect(() => {
    if (!containerRef.current) return;
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) {
        setDimensions({
          width: Math.max(entry.contentRect.width, 360),
          height: canvasHeight ?? 600,
        });
      }
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, [canvasHeight]);

  // Đồng bộ localLayouts và khởi tạo Undo/Redo stack khi `layouts` từ backend thay đổi
  useEffect(() => {
    const initialMap: Record<string, LocalTableLayout> = {};
    for (const item of layouts) {
      initialMap[item.tableId] = {
        x: item.positionX,
        y: item.positionY,
        width: item.width || 90,
        height: item.height || 90,
        rotation: item.rotation || 0,
        shape: item.shape || "RECTANGLE",
      };
    }
    setLocalLayouts(initialMap);
    setHistory([initialMap]);
    setHistoryIndex(0);
    setHasChanges(false);

    // Kiểm tra va chạm ban đầu
    const collisions = computeCollisions(
      initialMap,
      layouts.filter((l) => l.status !== "MAINTENANCE").map((l) => l.tableId),
    );
    setCollidingTableIds(collisions);
  }, [layouts]);

  // Ghi nhận trạng thái mới vào History Stack
  const pushHistory = useCallback(
    (newLayouts: Record<string, LocalTableLayout>) => {
      setHistory((prev) => {
        const nextStack = prev.slice(0, historyIndex + 1);
        nextStack.push(newLayouts);
        if (nextStack.length > MAX_HISTORY) {
          nextStack.shift();
        }
        return nextStack;
      });
      setHistoryIndex((prev) => Math.min(prev + 1, MAX_HISTORY - 1));
      setLocalLayouts(newLayouts);
      setHasChanges(true);

      const collisions = computeCollisions(
        newLayouts,
        visibleLayouts.map((l) => l.tableId),
      );
      setCollidingTableIds(collisions);
    },
    [historyIndex, visibleLayouts],
  );

  // Thao tác Undo
  const handleUndo = useCallback(() => {
    if (historyIndex > 0) {
      const prevIndex = historyIndex - 1;
      const targetState = history[prevIndex];
      if (targetState) {
        setLocalLayouts(targetState);
        setHistoryIndex(prevIndex);
        setHasChanges(true);
        const collisions = computeCollisions(
          targetState,
          visibleLayouts.map((l) => l.tableId),
        );
        setCollidingTableIds(collisions);
        toast.info("Đã hoàn tác thao tác gần nhất.");
      }
    }
  }, [history, historyIndex, visibleLayouts]);

  // Thao tác Redo
  const handleRedo = useCallback(() => {
    if (historyIndex < history.length - 1) {
      const nextIndex = historyIndex + 1;
      const targetState = history[nextIndex];
      if (targetState) {
        setLocalLayouts(targetState);
        setHistoryIndex(nextIndex);
        setHasChanges(true);
        const collisions = computeCollisions(
          targetState,
          visibleLayouts.map((l) => l.tableId),
        );
        setCollidingTableIds(collisions);
        toast.info("Đã làm lại thao tác vừa hoàn tác.");
      }
    }
  }, [history, historyIndex, visibleLayouts]);

  // Phím tắt bàn phím toàn cục: Ctrl+Z (Undo) và Ctrl+Y / Ctrl+Shift+Z (Redo)
  useEffect(() => {
    if (readOnly) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName?.toLowerCase();
      if (tag === "input" || tag === "textarea" || tag === "select") return;

      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "z") {
        if (e.shiftKey) {
          e.preventDefault();
          handleRedo();
        } else {
          e.preventDefault();
          handleUndo();
        }
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "y") {
        e.preventDefault();
        handleRedo();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [readOnly, handleUndo, handleRedo]);

  // Gom nhóm các bàn hiển thị theo khu vực để vẽ ranh giới (Zone Boundaries)
  const zoneGroups = useMemo(() => {
    const groups: Record<string, FloorPlanLayoutResponse[]> = {};
    for (const item of visibleLayouts) {
      const zoneName = item.zoneName || "Khu vực chung";
      if (!groups[zoneName]) {
        groups[zoneName] = [];
      }
      groups[zoneName].push(item);
    }
    return groups;
  }, [visibleLayouts]);

  const handleZoom = (direction: "in" | "out") => {
    setStageScale((prev) => {
      const step = 0.15;
      const next = direction === "in" ? prev + step : prev - step;
      return Math.min(Math.max(next, 0.35), 2.5);
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
    setStageScale(Math.min(Math.max(newScale, 0.35), 2.5));
  };

  // Tính năng Auto-Fit Viewport Centering: Tự động căn chỉnh bao trọn tất cả bàn trong khung nhìn
  const handleAutoFit = useCallback(() => {
    if (visibleLayouts.length === 0) {
      setStageScale(1);
      setStagePos({ x: 0, y: 0 });
      return;
    }

    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    for (const item of visibleLayouts) {
      const layout = localLayouts[item.tableId] ?? {
        x: item.positionX,
        y: item.positionY,
        width: item.width || 90,
        height: item.height || 90,
      };
      minX = Math.min(minX, layout.x);
      minY = Math.min(minY, layout.y);
      maxX = Math.max(maxX, layout.x + layout.width);
      maxY = Math.max(maxY, layout.y + layout.height);
    }

    const padding = 70;
    const contentWidth = Math.max(maxX - minX, 100);
    const contentHeight = Math.max(maxY - minY, 100);

    const availableWidth = Math.max(dimensions.width - padding * 2, 200);
    const availableHeight = Math.max(dimensions.height - padding * 2, 200);

    const scaleX = availableWidth / contentWidth;
    const scaleY = availableHeight / contentHeight;
    const targetScale = Math.min(Math.max(Math.min(scaleX, scaleY), 0.35), 1.6);

    const targetPosX = (dimensions.width - contentWidth * targetScale) / 2 - minX * targetScale;
    const targetPosY = (dimensions.height - contentHeight * targetScale) / 2 - minY * targetScale;

    setStageScale(targetScale);
    setStagePos({ x: targetPosX, y: targetPosY });
  }, [visibleLayouts, localLayouts, dimensions]);

  // Xử lý căn gióng từ tính (Magnetic Snapping) khi đang kéo bàn
  const handleDragNodeMove = (activeId: string, e: KonvaEventObject<DragEvent>) => {
    if (readOnly) return;
    const rawX = e.target.x();
    const rawY = e.target.y();
    const activeLayout = localLayouts[activeId];
    const w = activeLayout?.width ?? 90;
    const h = activeLayout?.height ?? 90;

    let snappedX = rawX;
    let snappedY = rawY;
    const newGuides: GuideLine[] = [];

    let minDiffX = SNAP_THRESHOLD;
    let minDiffY = SNAP_THRESHOLD;
    let guideLineX: number | null = null;
    let guideLineY: number | null = null;

    // So sánh với toàn bộ các bàn khác đang hiển thị
    for (const other of visibleLayouts) {
      if (other.tableId === activeId) continue;
      const o = localLayouts[other.tableId];
      if (!o) continue;

      const otherLeft = o.x;
      const otherRight = o.x + o.width;
      const otherCenterX = o.x + o.width / 2;

      const otherTop = o.y;
      const otherBottom = o.y + o.height;
      const otherCenterY = o.y + o.height / 2;

      // --- TRỤC NGANG (X-AXIS SNAPPING) ---
      // 1. Cạnh trái trùng Cạnh trái
      const dLeftLeft = Math.abs(rawX - otherLeft);
      if (dLeftLeft < minDiffX) {
        minDiffX = dLeftLeft;
        snappedX = otherLeft;
        guideLineX = otherLeft;
      }
      // 2. Cạnh phải trùng Cạnh phải
      const dRightRight = Math.abs(rawX + w - otherRight);
      if (dRightRight < minDiffX) {
        minDiffX = dRightRight;
        snappedX = otherRight - w;
        guideLineX = otherRight;
      }
      // 3. Trục tâm trùng Trục tâm
      const dCenterCenterX = Math.abs(rawX + w / 2 - otherCenterX);
      if (dCenterCenterX < minDiffX) {
        minDiffX = dCenterCenterX;
        snappedX = otherCenterX - w / 2;
        guideLineX = otherCenterX;
      }
      // 4. Cạnh trái giáp Cạnh phải
      const dLeftRight = Math.abs(rawX - otherRight);
      if (dLeftRight < minDiffX) {
        minDiffX = dLeftRight;
        snappedX = otherRight;
        guideLineX = otherRight;
      }
      // 5. Cạnh phải giáp Cạnh trái
      const dRightLeft = Math.abs(rawX + w - otherLeft);
      if (dRightLeft < minDiffX) {
        minDiffX = dRightLeft;
        snappedX = otherLeft - w;
        guideLineX = otherLeft;
      }

      // --- TRỤC DỌC (Y-AXIS SNAPPING) ---
      // 1. Cạnh trên trùng Cạnh trên
      const dTopTop = Math.abs(rawY - otherTop);
      if (dTopTop < minDiffY) {
        minDiffY = dTopTop;
        snappedY = otherTop;
        guideLineY = otherTop;
      }
      // 2. Cạnh dưới trùng Cạnh dưới
      const dBottomBottom = Math.abs(rawY + h - otherBottom);
      if (dBottomBottom < minDiffY) {
        minDiffY = dBottomBottom;
        snappedY = otherBottom - h;
        guideLineY = otherBottom;
      }
      // 3. Trục tâm trùng Trục tâm
      const dCenterCenterY = Math.abs(rawY + h / 2 - otherCenterY);
      if (dCenterCenterY < minDiffY) {
        minDiffY = dCenterCenterY;
        snappedY = otherCenterY - h / 2;
        guideLineY = otherCenterY;
      }
      // 4. Cạnh trên giáp Cạnh dưới
      const dTopBottom = Math.abs(rawY - otherBottom);
      if (dTopBottom < minDiffY) {
        minDiffY = dTopBottom;
        snappedY = otherBottom;
        guideLineY = otherBottom;
      }
      // 5. Cạnh dưới giáp Cạnh trên
      const dBottomTop = Math.abs(rawY + h - otherTop);
      if (dBottomTop < minDiffY) {
        minDiffY = dBottomTop;
        snappedY = otherTop - h;
        guideLineY = otherTop;
      }
    }

    if (guideLineX !== null) {
      newGuides.push({
        points: [guideLineX, -2000, guideLineX, 4000],
        orientation: "vertical",
        stroke: "#06b6d4",
      });
    } else {
      snappedX = Math.round(snappedX / 10) * 10;
    }

    if (guideLineY !== null) {
      newGuides.push({
        points: [-2000, guideLineY, 4000, guideLineY],
        orientation: "horizontal",
        stroke: "#06b6d4",
      });
    } else {
      snappedY = Math.round(snappedY / 10) * 10;
    }

    setActiveGuides(newGuides);
    e.target.position({ x: snappedX, y: snappedY });

    // Kiểm tra va chạm theo thời gian thực khi đang kéo
    if (activeLayout) {
      const previewLayouts = {
        ...localLayouts,
        [activeId]: {
          ...activeLayout,
          x: snappedX,
          y: snappedY,
        },
      };
      const collisions = computeCollisions(
        previewLayouts,
        visibleLayouts.map((l) => l.tableId),
      );
      setCollidingTableIds(collisions);
    }
  };

  // Hoàn tất kéo thả một bàn
  const handleDragNodeEnd = (activeId: string, e: KonvaEventObject<DragEvent>) => {
    setActiveGuides([]);
    const activeLayout = localLayouts[activeId];
    if (!activeLayout) return;

    const finalX = Math.round(e.target.x());
    const finalY = Math.round(e.target.y());

    const updatedLayouts = {
      ...localLayouts,
      [activeId]: {
        ...activeLayout,
        x: finalX,
        y: finalY,
      },
    };

    const collisions = computeCollisions(
      updatedLayouts,
      visibleLayouts.map((l) => l.tableId),
    );
    setCollidingTableIds(collisions);

    if (collisions.has(activeId)) {
      toast.warning("Vị trí bàn này đang đè lên bàn khác. Vui lòng căn chỉnh tách ra.");
    }

    pushHistory(updatedLayouts);
  };

  // Contextual Action: Xoay 90 độ quanh tâm bàn
  const handleRotate90 = (tableId: string) => {
    const current = localLayouts[tableId];
    if (!current) return;

    const nextRotation = (current.rotation + 90) % 360;
    const isSwappingDims = current.shape !== "CIRCLE";
    const nextWidth = isSwappingDims ? current.height : current.width;
    const nextHeight = isSwappingDims ? current.width : current.height;

    // Giữ nguyên tọa độ tâm của bàn khi xoay
    const centerX = current.x + current.width / 2;
    const centerY = current.y + current.height / 2;
    const nextX = Math.round(centerX - nextWidth / 2);
    const nextY = Math.round(centerY - nextHeight / 2);

    const updated = {
      ...localLayouts,
      [tableId]: {
        ...current,
        rotation: nextRotation,
        width: nextWidth,
        height: nextHeight,
        x: nextX,
        y: nextY,
      },
    };
    pushHistory(updated);
    toast.info(`Đã xoay bàn sang ${nextRotation}°`);
  };

  // Contextual Action: Chuyển đổi giữa bàn Tròn và Bàn Chữ nhật
  const handleToggleShape = (tableId: string) => {
    const current = localLayouts[tableId];
    if (!current) return;

    const nextShape: FloorPlanShape = current.shape === "CIRCLE" ? "RECTANGLE" : "CIRCLE";
    const originalItem = layouts.find((l) => l.tableId === tableId);
    const capacity = originalItem?.capacity ?? 4;
    const dims = getStandardDimensions(capacity, nextShape);

    const centerX = current.x + current.width / 2;
    const centerY = current.y + current.height / 2;
    const nextX = Math.round(centerX - dims.width / 2);
    const nextY = Math.round(centerY - dims.height / 2);

    const updated = {
      ...localLayouts,
      [tableId]: {
        ...current,
        shape: nextShape,
        width: dims.width,
        height: dims.height,
        x: nextX,
        y: nextY,
      },
    };
    pushHistory(updated);
    toast.info(`Đã chuyển sang ${nextShape === "CIRCLE" ? "Bàn tròn" : "Bàn chữ nhật"}`);
  };

  // Contextual Action: Chuẩn hóa kích thước bàn hiện tại
  const handleStandardizeCurrent = (tableId: string) => {
    const current = localLayouts[tableId];
    if (!current) return;

    const originalItem = layouts.find((l) => l.tableId === tableId);
    const capacity = originalItem?.capacity ?? 4;
    const dims = getStandardDimensions(capacity, current.shape);

    const centerX = current.x + current.width / 2;
    const centerY = current.y + current.height / 2;
    const nextX = Math.round(centerX - dims.width / 2);
    const nextY = Math.round(centerY - dims.height / 2);

    const updated = {
      ...localLayouts,
      [tableId]: {
        ...current,
        width: dims.width,
        height: dims.height,
        x: nextX,
        y: nextY,
      },
    };
    pushHistory(updated);
    toast.success(`Đã chuẩn hóa kích thước bàn: ${dims.width} × ${dims.height}px`);
  };

  // Thao tác hàng loạt: Chuẩn hóa kích thước cho toàn bộ bàn
  const handleStandardizeAll = () => {
    const updated: Record<string, LocalTableLayout> = {};
    for (const item of layouts) {
      const current = localLayouts[item.tableId] ?? {
        x: item.positionX,
        y: item.positionY,
        width: item.width || 90,
        height: item.height || 90,
        rotation: item.rotation || 0,
        shape: item.shape || "RECTANGLE",
      };
      const dims = getStandardDimensions(item.capacity, current.shape);
      const centerX = current.x + current.width / 2;
      const centerY = current.y + current.height / 2;

      updated[item.tableId] = {
        ...current,
        width: dims.width,
        height: dims.height,
        x: Math.round(centerX - dims.width / 2),
        y: Math.round(centerY - dims.height / 2),
      };
    }
    pushHistory(updated);
    toast.success(`Đã chuẩn hóa kích thước cho tất cả ${layouts.length} bàn.`);
  };

  // Lưu sơ đồ bàn xuống Backend
  const handleSave = async () => {
    if (!onSaveLayouts || !hasChanges) return;
    setIsSaving(true);
    try {
      const requests: FloorPlanLayoutRequest[] = layouts.map((item) => {
        const current = localLayouts[item.tableId] ?? {
          x: item.positionX,
          y: item.positionY,
          width: item.width || 90,
          height: item.height || 90,
          rotation: item.rotation || 0,
          shape: item.shape || "RECTANGLE",
        };
        return {
          tableId: item.tableId,
          positionX: Math.round(current.x),
          positionY: Math.round(current.y),
          width: Math.round(current.width),
          height: Math.round(current.height),
          rotation: current.rotation,
          shape: current.shape,
        };
      });
      await onSaveLayouts(requests);
      setHasChanges(false);
    } finally {
      setIsSaving(false);
    }
  };

  const selectedLayout = layouts.find((l) => l.tableId === selectedTableId) ?? null;
  const selectedLocal = selectedTableId ? localLayouts[selectedTableId] : null;

  // Tính toán tọa độ hiển thị thanh Floating Action Pill trực tiếp trên bàn được chọn
  const pillPos = useMemo(() => {
    if (!selectedLocal || readOnly || !selectedLayout) return null;
    const screenX = (selectedLocal.x + selectedLocal.width / 2) * stageScale + stagePos.x;
    const screenY = selectedLocal.y * stageScale + stagePos.y;
    return {
      left: screenX,
      top: screenY - 14,
    };
  }, [selectedLocal, stageScale, stagePos, readOnly, selectedLayout]);

  if (!isMounted) {
    return (
      <div className="flex h-[400px] w-full items-center justify-center rounded-2xl border bg-muted/20">
        <p className="text-sm text-muted-foreground">Đang khởi tạo sơ đồ 2D...</p>
      </div>
    );
  }

  // Tạo các đường lưới grid nền nét đứt
  const gridLines = [];
  const width = dimensions.width / stageScale;
  const height = dimensions.height / stageScale;
  const gridStep = 40;

  for (let x = 0; x <= width + 800; x += gridStep) {
    gridLines.push(
      <Line
        key={`vx-${x}`}
        points={[x, -800, x, height + 800]}
        stroke="#e2e8f0"
        strokeWidth={0.5}
        dash={[4, 4]}
      />,
    );
  }
  for (let y = 0; y <= height + 800; y += gridStep) {
    gridLines.push(
      <Line
        key={`hy-${y}`}
        points={[-800, y, width + 800, y]}
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
          {/* Trạng thái kết nối */}
          <div className="flex items-center gap-1.5 rounded-lg border bg-background px-3 py-1.5 text-xs font-medium">
            <span
              className={cn(
                "h-2.5 w-2.5 rounded-full transition-colors",
                isConnected ? "bg-emerald-500 animate-pulse" : "bg-primary",
              )}
            />
            <span>{isConnected ? "Live 2D" : "Sơ đồ mặt bằng"}</span>
          </div>

          {/* Phóng to / Thu nhỏ / Căn vừa màn hình */}
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
              onClick={handleAutoFit}
              title="Căn vừa màn hình (Auto-Fit)"
            >
              <Maximize className="h-3.5 w-3.5" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              onClick={handleResetZoom}
              title="Đặt lại góc nhìn 100%"
            >
              <RotateCcw className="h-3.5 w-3.5" />
            </Button>
          </div>

          {/* Hoàn tác / Làm lại (Undo / Redo) */}
          {!readOnly && (
            <div className="flex items-center gap-1 rounded-lg border bg-background p-1">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                disabled={historyIndex <= 0}
                onClick={handleUndo}
                title="Hoàn tác (Ctrl+Z)"
              >
                <Undo2 className="h-3.5 w-3.5" />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                disabled={historyIndex >= history.length - 1}
                onClick={handleRedo}
                title="Làm lại (Ctrl+Y)"
              >
                <Redo2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          )}

          {/* Nút Chuẩn hóa kích thước toàn bộ */}
          {!readOnly && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="h-8 gap-1.5 text-xs"
              onClick={handleStandardizeAll}
              title="Chuẩn hóa kích thước tất cả bàn theo số lượng ghế"
            >
              <Sparkles className="h-3.5 w-3.5 text-amber-500" />
              <span className="hidden md:inline">Chuẩn hóa kích thước</span>
            </Button>
          )}

          <div className="hidden sm:flex items-center gap-1 text-xs text-muted-foreground">
            <Grid className="h-3.5 w-3.5" />
            <span>Căn từ tính 10px · Rê nền để cuộn</span>
          </div>
        </div>

        {/* Cảnh báo va chạm & Nút Lưu */}
        {!readOnly && (
          <div className="flex items-center gap-2">
            {collidingTableIds.size > 0 && (
              <span className="text-xs font-semibold text-rose-600 flex items-center gap-1 animate-pulse">
                <AlertTriangle className="h-3.5 w-3.5" />
                Có {collidingTableIds.size} bàn bị đè lên nhau
              </span>
            )}
            {hasChanges && collidingTableIds.size === 0 && (
              <span className="text-xs font-medium text-amber-600 animate-pulse">
                Có thay đổi chưa lưu
              </span>
            )}
            <Button
              type="button"
              size="sm"
              disabled={!hasChanges || isSaving}
              onClick={() => void handleSave()}
              className="h-8 gap-1.5 shadow-xs"
            >
              <Save className="h-3.5 w-3.5" />
              {isSaving ? "Đang lưu..." : "Lưu vị trí"}
            </Button>
          </div>
        )}
      </div>

      {/* Vùng Canvas chính và bảng thông tin */}
      <div className={cn("grid gap-4", readOnly ? "grid-cols-1" : "lg:grid-cols-4")}>
        <div
          ref={containerRef}
          className={cn(
            "relative overflow-hidden rounded-2xl border bg-slate-50/50 shadow-inner select-none",
            !readOnly && "lg:col-span-3",
          )}
        >
          {/* Contextual Floating Action Pill hiển thị trực tiếp trên bàn được chọn */}
          {pillPos && !readOnly && selectedLayout && (
            <div
              className="pointer-events-auto absolute z-20 flex items-center gap-1.5 -translate-x-1/2 -translate-y-full rounded-full border border-border/80 bg-background/95 px-3 py-1.5 shadow-xl backdrop-blur-md transition-all duration-150 animate-in fade-in zoom-in-95"
              style={{
                left: Math.max(90, Math.min(pillPos.left, dimensions.width - 90)),
                top: Math.max(48, pillPos.top),
              }}
            >
              <div className="flex items-center gap-1 pr-2 border-r text-xs font-bold text-foreground">
                <span>{selectedLayout.tableNumber}</span>
                <span className="text-[10px] text-muted-foreground font-normal">
                  ({selectedLayout.capacity} chỗ)
                </span>
              </div>

              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-7 w-7 rounded-full hover:bg-accent text-foreground"
                onClick={() => handleRotate90(selectedLayout.tableId)}
                title="Xoay bàn 90°"
              >
                <RotateCw className="h-3.5 w-3.5" />
              </Button>

              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-7 w-7 rounded-full hover:bg-accent text-foreground"
                onClick={() => handleToggleShape(selectedLayout.tableId)}
                title="Đổi hình dạng (Tròn ↔ Chữ nhật)"
              >
                <Shapes className="h-3.5 w-3.5" />
              </Button>

              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-7 w-7 rounded-full hover:bg-accent text-foreground"
                onClick={() => handleStandardizeCurrent(selectedLayout.tableId)}
                title="Chuẩn hóa kích thước bàn này"
              >
                <Maximize2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          )}

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
              {/* Đường lưới nền */}
              {gridLines}

              {/* Ranh giới & Nhãn khu vực (Zone Boundaries) */}
              {Object.entries(zoneGroups).map(([zoneName, zoneTables]) => {
                if (zoneTables.length === 0) return null;

                let minX = Infinity;
                let minY = Infinity;
                let maxX = -Infinity;
                let maxY = -Infinity;

                for (const t of zoneTables) {
                  const layout = localLayouts[t.tableId] ?? {
                    x: t.positionX,
                    y: t.positionY,
                    width: t.width || 90,
                    height: t.height || 90,
                  };
                  minX = Math.min(minX, layout.x);
                  minY = Math.min(minY, layout.y);
                  maxX = Math.max(maxX, layout.x + layout.width);
                  maxY = Math.max(maxY, layout.y + layout.height);
                }

                const padding = 24;
                const headerHeight = 30;
                const boxX = minX - padding;
                const boxY = minY - padding - headerHeight;
                const boxWidth = maxX - minX + padding * 2;
                const boxHeight = maxY - minY + padding * 2 + headerHeight;
                const isVip = Boolean(zoneTables[0]?.zoneIsVip);
                const badgeWidth = Math.max(zoneName.length * 9 + 48, 120);

                return (
                  <Group key={`zone-boundary-${zoneName}`}>
                    {/* Hộp ranh giới khu vực */}
                    <Rect
                      x={boxX}
                      y={boxY}
                      width={boxWidth}
                      height={boxHeight}
                      cornerRadius={16}
                      stroke={isVip ? "#f59e0b" : "#94a3b8"}
                      strokeWidth={1.5}
                      dash={[6, 4]}
                      fill={isVip ? "rgba(254, 243, 199, 0.22)" : "rgba(241, 245, 249, 0.45)"}
                    />

                    {/* Thẻ tên khu vực */}
                    <Group x={boxX + 12} y={boxY + 6}>
                      <Rect
                        width={badgeWidth}
                        height={22}
                        cornerRadius={6}
                        fill={isVip ? "#fef3c7" : "#e2e8f0"}
                        stroke={isVip ? "#f59e0b" : "#cbd5e1"}
                        strokeWidth={1}
                      />
                      <Text
                        text={`${zoneName}${isVip ? " ★ VIP" : ""} (${zoneTables.length} bàn)`}
                        x={8}
                        y={5}
                        fontSize={11}
                        fontStyle="bold"
                        fontFamily="sans-serif"
                        fill={isVip ? "#b45309" : "#334155"}
                      />
                    </Group>
                  </Group>
                );
              })}

              {/* Đường gióng từ tính (Magnetic Snapping Guidelines) */}
              {activeGuides.map((guide, idx) => (
                <Line
                  key={`guide-${idx}`}
                  points={guide.points}
                  stroke={guide.stroke ?? "#06b6d4"}
                  strokeWidth={1.5}
                  dash={[4, 4]}
                />
              ))}

              {/* Danh sách các bàn ăn trên sơ đồ */}
              {visibleLayouts.map((item) => {
                const layout = localLayouts[item.tableId] ?? {
                  x: item.positionX,
                  y: item.positionY,
                  width: item.width || 90,
                  height: item.height || 90,
                  rotation: item.rotation || 0,
                  shape: item.shape || "RECTANGLE",
                };

                const isSingleSelected = item.tableId === selectedTableId;
                const isMultiSelected = selectedTableIds
                  ? selectedTableIds.includes(item.tableId)
                  : false;
                const isSelected = isSingleSelected || isMultiSelected;
                const isColliding = collidingTableIds.has(item.tableId);

                const statusMeta = STATUS_COLORS[item.status] ?? STATUS_COLORS.AVAILABLE;
                const isCircle = layout.shape === "CIRCLE";
                const nodeWidth = layout.width;
                const nodeHeight = layout.height;

                // Màu sắc viền & đổ bóng theo trạng thái va chạm / chọn lựa
                const strokeColor = isColliding
                  ? "#ef4444"
                  : isSelected
                    ? "#2563eb"
                    : "#475569";
                const strokeWidth = isColliding || isSelected ? 3.5 : 1.5;
                const shadowColor = isColliding
                  ? "rgba(239, 68, 68, 0.7)"
                  : isSelected
                    ? "rgba(37, 99, 235, 0.5)"
                    : "rgba(0,0,0,0.2)";
                const shadowBlur = isColliding ? 14 : isSelected ? 12 : 4;

                return (
                  <Group
                    key={item.tableId}
                    x={layout.x}
                    y={layout.y}
                    draggable={!readOnly}
                    onClick={() => onSelectTable?.(item)}
                    onTap={() => onSelectTable?.(item)}
                    onDragMove={(e) => handleDragNodeMove(item.tableId, e)}
                    onDragEnd={(e) => handleDragNodeEnd(item.tableId, e)}
                  >
                    {isCircle ? (
                      <Circle
                        radius={nodeWidth / 2}
                        x={nodeWidth / 2}
                        y={nodeHeight / 2}
                        fill={isSelected ? "#2563eb" : statusMeta.bg}
                        stroke={strokeColor}
                        strokeWidth={strokeWidth}
                        dash={isColliding ? [5, 4] : undefined}
                        shadowColor={shadowColor}
                        shadowBlur={shadowBlur}
                        shadowOffset={{ x: 2, y: 3 }}
                        shadowOpacity={0.3}
                      />
                    ) : (
                      <Rect
                        width={nodeWidth}
                        height={nodeHeight}
                        cornerRadius={10}
                        fill={isSelected ? "#2563eb" : statusMeta.bg}
                        stroke={strokeColor}
                        strokeWidth={strokeWidth}
                        dash={isColliding ? [5, 4] : undefined}
                        shadowColor={shadowColor}
                        shadowBlur={shadowBlur}
                        shadowOffset={{ x: 2, y: 3 }}
                        shadowOpacity={0.3}
                      />
                    )}

                    {/* Huy hiệu cảnh báo va chạm */}
                    {isColliding && (
                      <Group x={nodeWidth - 10} y={-4}>
                        <Circle radius={9} fill="#ef4444" stroke="#ffffff" strokeWidth={1.5} />
                        <Text
                          text="!"
                          x={-3}
                          y={-6}
                          fontSize={12}
                          fontFamily="sans-serif"
                          fontStyle="bold"
                          fill="#ffffff"
                        />
                      </Group>
                    )}

                    {/* Số hiệu bàn */}
                    <Text
                      text={item.tableNumber ?? ""}
                      x={0}
                      y={nodeHeight / 2 - 16}
                      width={nodeWidth}
                      align="center"
                      fontSize={15}
                      fontFamily="sans-serif"
                      fontStyle="bold"
                      fill="#ffffff"
                    />

                    {/* Sức chứa và biểu tượng VIP */}
                    <Text
                      text={`${item.capacity ?? 0}p${item.zoneIsVip ? " ★" : ""}`}
                      x={0}
                      y={nodeHeight / 2 + 3}
                      width={nodeWidth}
                      align="center"
                      fontSize={11}
                      fontFamily="sans-serif"
                      fill="#ffffff"
                    />
                  </Group>
                );
              })}
            </Layer>
          </Stage>
        </div>

        {/* Cột thông tin chi tiết bàn được chọn & Chú thích */}
        {!readOnly && (
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
                        {selectedLocal?.shape === "CIRCLE" ? "Bàn tròn" : "Bàn chữ nhật"}
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span>Kích thước:</span>
                      <span className="font-medium font-mono text-foreground">
                        {selectedLocal?.width} × {selectedLocal?.height}px
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span>Góc xoay:</span>
                      <span className="font-medium font-mono text-foreground">
                        {selectedLocal?.rotation ?? 0}°
                      </span>
                    </div>
                  </div>

                  {/* Các nút công cụ thao tác nhanh với bàn */}
                  <div className="pt-3 border-t space-y-2">
                    <p className="text-xs font-medium text-foreground">Công cụ điều chỉnh:</p>
                    <div className="grid grid-cols-3 gap-1.5">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="h-8 text-xs px-1 gap-1"
                        onClick={() => handleRotate90(selectedLayout.tableId)}
                        title="Xoay bàn 90°"
                      >
                        <RotateCw className="h-3 w-3" />
                        Xoay 90°
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="h-8 text-xs px-1 gap-1"
                        onClick={() => handleToggleShape(selectedLayout.tableId)}
                        title="Đổi hình tròn / chữ nhật"
                      >
                        <Shapes className="h-3 w-3" />
                        Đổi hình
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="h-8 text-xs px-1 gap-1"
                        onClick={() => handleStandardizeCurrent(selectedLayout.tableId)}
                        title="Chuẩn hóa kích thước bàn theo số ghế"
                      >
                        <Maximize2 className="h-3 w-3" />
                        Chuẩn cỡ
                      </Button>
                    </div>

                    {onUpdateStatus && (
                      <Button
                        type="button"
                        size="sm"
                        variant="destructive"
                        className="w-full h-8 text-xs mt-1"
                        onClick={() => void onUpdateStatus(selectedLayout.tableId, "MAINTENANCE")}
                      >
                        Tạm ẩn bàn khỏi sơ đồ
                      </Button>
                    )}
                  </div>
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
                {(Object.keys(STATUS_COLORS) as TableStatus[])
                  .filter((st) => st !== "MAINTENANCE")
                  .map((st) => (
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
        )}
      </div>
    </div>
  );
}
