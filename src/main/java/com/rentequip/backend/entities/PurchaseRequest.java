package com.rentequip.backend.entities;

import com.rentequip.backend.enums.PurchaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A renter asks to keep the machine it rented. The prices are a snapshot taken when the request is made
 * (list price, the rent credited and what is left to pay), so a later change of the sale price does not
 * move an offer the owner already saw.
 */
@Entity
@Table(name = "purchase_requests", indexes = {
        @Index(name = "idx_purchase_equipment", columnList = "equipment_id"),
        @Index(name = "idx_purchase_buyer", columnList = "buyer_id"),
        @Index(name = "idx_purchase_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class PurchaseRequest extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    /** The renting company that wants to buy. The seller is always the equipment's owner. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Company buyer;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "list_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal listPrice;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "rent_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentCredit;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "final_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @NotNull
    @Size(min = 3, max = 3)
    @Column(nullable = false, length = 3)
    private String currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status = PurchaseStatus.PENDING;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Size(max = 120)
    @Column(name = "provider_reference", length = 120)
    private String providerReference;
}
