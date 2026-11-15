package com.rentequip.backend.entities;

import com.rentequip.backend.enums.OfferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A counteroffer on a machine's daily rate. The renter proposes a price (and optionally the dates and
 * conditions); the owner answers. Once accepted, the renter books at {@code agreedDailyRate}.
 */
@Entity
@Table(name = "price_offers", indexes = {
        @Index(name = "idx_price_offers_renter", columnList = "renter_company_id"),
        @Index(name = "idx_price_offers_equipment", columnList = "equipment_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PriceOffer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "renter_company_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Company renter;

    @Column(name = "list_daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal listDailyRate;

    @Column(name = "proposed_daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal proposedDailyRate;

    /** The owner's answer when they counter; null otherwise. */
    @Column(name = "counter_daily_rate", precision = 12, scale = 2)
    private BigDecimal counterDailyRate;

    /** Set when either side accepts: the rate the reservation will use. */
    @Column(name = "agreed_daily_rate", precision = 12, scale = 2)
    private BigDecimal agreedDailyRate;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** The renter's conditions: with operator, delivery to site, longer term... */
    @Column(name = "renter_message", length = 500)
    private String renterMessage;

    /** The owner's reply with a counter or a rejection. */
    @Column(name = "owner_message", length = 500)
    private String ownerMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status = OfferStatus.PENDING;
}
