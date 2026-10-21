package com.rentequip.backend.entities;

import com.rentequip.backend.enums.HandoverStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Photo of the equipment's condition taken with the phone camera at delivery or return, optionally with
 * the GPS position where it was taken.
 */
@Entity
@Table(name = "handover_photos", indexes = @Index(name = "idx_handover_reservation", columnList = "reservation_id"))
@Getter
@Setter
@NoArgsConstructor
public class HandoverPhoto extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_company_id", nullable = false)
    private Company uploadedBy;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HandoverStage stage;

    /** Name of the file in the image storage; the public URL is built when the photo is read. */
    @NotBlank
    @Size(max = 64)
    @Column(name = "stored_name", nullable = false, length = 64)
    private String storedName;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Size(max = 255)
    @Column(length = 255)
    private String note;
}
