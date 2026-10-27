package com.staffsuite.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * allocatedDays already includes any carried-forward days; remainingDays = allocatedDays - usedDays
 * and is recomputed on every save so it can never drift.
 */
@Entity
@Table(name = "leave_balances")
@Getter
@Setter
@NoArgsConstructor
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "balance_year", nullable = false)
    private int year;

    @Column(name = "allocated_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal allocatedDays = BigDecimal.ZERO;

    @Column(name = "used_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Column(name = "carried_forward_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal carriedForwardDays = BigDecimal.ZERO;

    @Column(name = "remaining_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal remainingDays = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void recalculate() {
        remainingDays = allocatedDays.subtract(usedDays);
    }
}
