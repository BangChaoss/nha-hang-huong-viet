package com.huongViet.reservation;

import com.huongViet.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_audits")
public class ReservationAudit {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reservation_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String reservationId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "changed_by", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String changedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 20)
    private ReservationStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private ReservationStatus newStatus;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "old_table_id", length = 36, columnDefinition = "CHAR(36)")
    private String oldTableId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "new_table_id", length = 36, columnDefinition = "CHAR(36)")
    private String newTableId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", insertable = false, updatable = false, columnDefinition = "CHAR(36)")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", insertable = false, updatable = false, columnDefinition = "CHAR(36)")
    private User changer;

    protected ReservationAudit() {}

    public ReservationAudit(String id,
                            String reservationId,
                            String changedBy,
                            ReservationStatus oldStatus,
                            ReservationStatus newStatus,
                            String oldTableId,
                            String newTableId,
                            String reason) {
        this.id = id;
        this.reservationId = reservationId;
        this.changedBy = changedBy;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.oldTableId = oldTableId;
        this.newTableId = newTableId;
        this.reason = reason;
        this.changedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.changedAt == null) {
            this.changedAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public ReservationStatus getOldStatus() { return oldStatus; }
    public void setOldStatus(ReservationStatus oldStatus) { this.oldStatus = oldStatus; }

    public ReservationStatus getNewStatus() { return newStatus; }
    public void setNewStatus(ReservationStatus newStatus) { this.newStatus = newStatus; }

    public String getOldTableId() { return oldTableId; }
    public void setOldTableId(String oldTableId) { this.oldTableId = oldTableId; }

    public String getNewTableId() { return newTableId; }
    public void setNewTableId(String newTableId) { this.newTableId = newTableId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }

    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }

    public User getChanger() { return changer; }
    public void setChanger(User changer) { this.changer = changer; }
}
