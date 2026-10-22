package com.rentequip.backend.repositories;

import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.enums.EquipmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    /**
     * Great-circle distance in kilometres between the search origin and the equipment location.
     * least(1.0, ...) guards acos against floating point values that drift slightly above 1.
     */
    String DISTANCE_KM = """
            (6371.0 * acos(least(1.0,
                cos(radians(:latitude)) * cos(radians(e.latitude))
                    * cos(radians(e.longitude) - radians(:longitude))
                + sin(radians(:latitude)) * sin(radians(e.latitude))
            )))
            """;

    /**
     * Loads the equipment row with SELECT ... FOR UPDATE. This is the serialization point of the booking
     * flow: concurrent reservation attempts on the same equipment queue up here, so the availability
     * check that follows can never race. A plain lock on the reservations table would not help, because
     * rows that do not exist yet cannot be locked.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT e FROM Equipment e WHERE e.id = :id")
    Optional<Equipment> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT e FROM Equipment e LEFT JOIN FETCH e.categories WHERE e.id = :id")
    Optional<Equipment> findByIdWithCategories(@Param("id") Long id);

    Page<Equipment> findByOwnerId(Long ownerId, Pageable pageable);

    long countByOwnerIdAndStatusNot(Long ownerId, EquipmentStatus status);

    boolean existsBySerialNumber(String serialNumber);

    boolean existsBySerialNumberAndIdNot(String serialNumber, Long id);

    /**
     * Proximity search. Every filter except the coordinates and the radius is optional: passing null
     * disables that predicate. Results are ordered by distance, so callers must supply an unsorted
     * Pageable to avoid appending a second ORDER BY clause.
     */
    @Query(value = """
            SELECT e FROM Equipment e
            WHERE e.status = com.rentequip.backend.enums.EquipmentStatus.AVAILABLE
              AND (:categoryId IS NULL
                   OR EXISTS (SELECT 1 FROM Equipment eq JOIN eq.categories c
                              WHERE eq.id = e.id AND c.id = :categoryId))
              AND (:city IS NULL OR lower(e.city) = lower(:city))
              AND (:namePattern IS NULL
                   OR lower(e.name) LIKE :namePattern
                   OR lower(e.brand) LIKE :namePattern
                   OR lower(e.model) LIKE :namePattern)
              AND (:maxDailyRate IS NULL OR e.dailyRate <= :maxDailyRate)
              AND (:excludedOwnerId IS NULL OR e.owner.id <> :excludedOwnerId)
              AND """ + DISTANCE_KM + """
              <= :radiusKm
              AND (:startDate IS NULL OR :endDate IS NULL
                   OR NOT EXISTS (SELECT 1 FROM Reservation r
                                  WHERE r.equipment.id = e.id
                                    AND r.status IN (com.rentequip.backend.enums.ReservationStatus.PENDING,
                                                     com.rentequip.backend.enums.ReservationStatus.CONFIRMED,
                                                     com.rentequip.backend.enums.ReservationStatus.IN_PROGRESS)
                                    AND r.startDate <= :endDate
                                    AND r.endDate >= :startDate))
            ORDER BY """ + DISTANCE_KM,
            countQuery = """
            SELECT count(e) FROM Equipment e
            WHERE e.status = com.rentequip.backend.enums.EquipmentStatus.AVAILABLE
              AND (:categoryId IS NULL
                   OR EXISTS (SELECT 1 FROM Equipment eq JOIN eq.categories c
                              WHERE eq.id = e.id AND c.id = :categoryId))
              AND (:city IS NULL OR lower(e.city) = lower(:city))
              AND (:namePattern IS NULL
                   OR lower(e.name) LIKE :namePattern
                   OR lower(e.brand) LIKE :namePattern
                   OR lower(e.model) LIKE :namePattern)
              AND (:maxDailyRate IS NULL OR e.dailyRate <= :maxDailyRate)
              AND (:excludedOwnerId IS NULL OR e.owner.id <> :excludedOwnerId)
              AND """ + DISTANCE_KM + """
              <= :radiusKm
              AND (:startDate IS NULL OR :endDate IS NULL
                   OR NOT EXISTS (SELECT 1 FROM Reservation r
                                  WHERE r.equipment.id = e.id
                                    AND r.status IN (com.rentequip.backend.enums.ReservationStatus.PENDING,
                                                     com.rentequip.backend.enums.ReservationStatus.CONFIRMED,
                                                     com.rentequip.backend.enums.ReservationStatus.IN_PROGRESS)
                                    AND r.startDate <= :endDate
                                    AND r.endDate >= :startDate))
            """)
    Page<Equipment> searchNearby(@Param("latitude") BigDecimal latitude,
                                 @Param("longitude") BigDecimal longitude,
                                 @Param("radiusKm") double radiusKm,
                                 @Param("categoryId") Long categoryId,
                                 @Param("city") String city,
                                 @Param("namePattern") String namePattern,
                                 @Param("maxDailyRate") BigDecimal maxDailyRate,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate,
                                 @Param("excludedOwnerId") Long excludedOwnerId,
                                 Pageable pageable);

    /**
     * Raw distance lookup used to enrich a single result with the kilometres shown in the listing card.
     */
    @Query("SELECT " + DISTANCE_KM + " FROM Equipment e WHERE e.id = :id")
    Optional<Double> findDistanceKm(@Param("id") Long id,
                                    @Param("latitude") BigDecimal latitude,
                                    @Param("longitude") BigDecimal longitude);
}
