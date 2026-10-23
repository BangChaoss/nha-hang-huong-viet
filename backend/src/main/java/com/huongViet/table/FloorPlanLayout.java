package com.huongViet.table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "floor_plan_layouts")
public class FloorPlanLayout {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "table_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String tableId;

    @Column(name = "position_x", nullable = false)
    private Double positionX = 0.0;

    @Column(name = "position_y", nullable = false)
    private Double positionY = 0.0;

    @Column(nullable = false)
    private Double width = 80.0;

    @Column(nullable = false)
    private Double height = 80.0;

    @Column(nullable = false)
    private Double rotation = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FloorPlanShape shape = FloorPlanShape.RECTANGLE;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected FloorPlanLayout() {}

    public FloorPlanLayout(String id, String tableId, Double positionX, Double positionY,
                           Double width, Double height, Double rotation, FloorPlanShape shape) {
        this.id = id;
        this.tableId = tableId;
        this.positionX = positionX != null ? positionX : 0.0;
        this.positionY = positionY != null ? positionY : 0.0;
        this.width = width != null ? width : 80.0;
        this.height = height != null ? height : 80.0;
        this.rotation = rotation != null ? rotation : 0.0;
        this.shape = shape != null ? shape : FloorPlanShape.RECTANGLE;
    }

    public String getId() {
        return id;
    }

    public String getTableId() {
        return tableId;
    }

    public Double getPositionX() {
        return positionX;
    }

    public void setPositionX(Double positionX) {
        this.positionX = positionX;
    }

    public Double getPositionY() {
        return positionY;
    }

    public void setPositionY(Double positionY) {
        this.positionY = positionY;
    }

    public Double getWidth() {
        return width;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Double getRotation() {
        return rotation;
    }

    public void setRotation(Double rotation) {
        this.rotation = rotation;
    }

    public FloorPlanShape getShape() {
        return shape;
    }

    public void setShape(FloorPlanShape shape) {
        this.shape = shape;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
