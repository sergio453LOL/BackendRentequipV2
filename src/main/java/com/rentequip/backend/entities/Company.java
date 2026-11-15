package com.rentequip.backend.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company extends BaseEntity {

    @NotBlank
    @Size(min = 2, max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank
    @Pattern(regexp = "^\\d{11}$", message = "Tax ID (RUC) must contain exactly 11 digits")
    @Column(name = "tax_id", nullable = false, unique = true, length = 11)
    private String taxId;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Size(max = 20)
    @Column(length = 20)
    private String phone;

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

    /** Public URL of the company logo; null shows the initials. */
    @Size(max = 500)
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** Public "about us" text, shown to renters next to the company's machines. */
    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    /** SUNAT reported the RUC as ACTIVO and HABIDO: the profile and the catalogue show the badge. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean verified = false;

    @Column(name = "verified_at")
    private java.time.LocalDateTime verifiedAt;

    /** SUNAT's last answer, e.g. "ACTIVO / HABIDO"; null until the RUC was checked. */
    @Column(name = "tax_status", length = 60)
    private String taxStatus;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<User> users = new ArrayList<>();

    @OneToMany(mappedBy = "owner", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<Equipment> equipments = new ArrayList<>();

    @OneToMany(mappedBy = "renter", fetch = FetchType.LAZY)
    private List<Reservation> reservations = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "company_favorite_equipment",
            joinColumns = @JoinColumn(name = "company_id"),
            inverseJoinColumns = @JoinColumn(name = "equipment_id")
    )
    private Set<Equipment> favoriteEquipments = new HashSet<>();

    public void addUser(User user) {
        users.add(user);
        user.setCompany(this);
    }

    public void removeUser(User user) {
        users.remove(user);
        user.setCompany(null);
    }

    public void addEquipment(Equipment equipment) {
        equipments.add(equipment);
        equipment.setOwner(this);
    }

    public void addFavorite(Equipment equipment) {
        favoriteEquipments.add(equipment);
    }

    public void removeFavorite(Equipment equipment) {
        favoriteEquipments.remove(equipment);
    }
}
