package com.huongViet.table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "table_zones")
public class TableZone {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "zone_name", nullable = false, length = 50)
    private String zoneName;

    @Column(name = "is_vip", nullable = false)
    private Boolean isVip = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected TableZone() {}

    public TableZone(String id, String zoneName, Boolean isVip) {
        this.id = id;
        this.zoneName = zoneName;
        this.isVip = isVip != null ? isVip : false;
    }

    public String getId() {
        return id;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public Boolean getIsVip() {
        return isVip;
    }

    public void setIsVip(Boolean isVip) {
        this.isVip = isVip;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
