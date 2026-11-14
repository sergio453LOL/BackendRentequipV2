package com.rentequip.backend.entities;

import com.rentequip.backend.enums.EquipmentStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "equipment", indexes = {
        @Index(name = "idx_equipment_owner", columnList = "owner_id"),
        @Index(name = "idx_equipment_status", columnList = "status"),
        @Index(name = "idx_equipment_location", columnList = "latitude, longitude")
})
@Getter
@Setter
@NoArgsConstructor
public class Equipment extends BaseEntity {

    @NotBlank
    @Size(min = 3, max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 2000)
    @Column(length = 2000)
    private String description;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String brand;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String model;

    @Min(1950)
    @Max(2100)
    @Column(name = "manufacture_year")
    private Integer manufactureYear;

    @Size(max = 100)
    @Column(name = "serial_number", unique = true, length = 100)
    private String serialNumber;

    @Size(max = 4000)
    @Column(columnDefinition = "TEXT")
    private String specifications;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @DecimalMin("0.01")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "weekly_rate", precision = 12, scale = 2)
    private BigDecimal weeklyRate;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "security_deposit", nullable = false, precision = 12, scale = 2)
    private BigDecimal securityDeposit;

    @NotBlank
    @Size(min = 3, max = 3)
    @Column(nullable = false, length = 3)
    private String currency = "PEN";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentStatus status = EquipmentStatus.AVAILABLE;

    /** The rate includes a qualified operator; renters filter and see it on the card. */
    @Column(name = "operator_included", nullable = false, columnDefinition = "boolean default false")
    private boolean operatorIncluded = false;

    @Size(max = 255)
    @Column(length = 255)
    private String address;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String city;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Digits(integer = 2, fraction = 7)
    @Column(nullable = false, precision = 9, scale = 7)
    private BigDecimal latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Digits(integer = 3, fraction = 7)
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "equipment_images", joinColumns = @JoinColumn(name = "equipment_id"))
    @Column(name = "image_url", nullable = false, length = 500)
    private List<String> imageUrls = new ArrayList<>();

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Company owner;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "equipment_category_assignments",
            joinColumns = @JoinColumn(name = "equipment_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<EquipmentCategory> categories = new HashSet<>();

    @OneToMany(mappedBy = "equipment", fetch = FetchType.LAZY)
    private List<Reservation> reservations = new ArrayList<>();

    public void addCategory(EquipmentCategory category) {
        categories.add(category);
    }

    public void removeCategory(EquipmentCategory category) {
        categories.remove(category);
    }
}
