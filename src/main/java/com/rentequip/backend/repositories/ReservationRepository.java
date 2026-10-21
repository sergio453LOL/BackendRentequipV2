package com.rentequip.backend.repositories;

import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Statuses that hold the calendar. A reservation in any of these blocks the dates for everyone else;
     * CANCELLED, REJECTED and COMPLETED release them.
     */
    String BLOCKING_STATUSES = """
            (com.rentequip.backend.enums.ReservationStatus.PENDING,
             com.rentequip.backend.enums.ReservationStatus.CONFIRMED,
             com.rentequip.backend.enums.ReservationStatus.IN_PROGRESS)
            """;

    /**
     * Two ranges overlap when each one starts before the other ends. Dates are inclusive on both ends,
     * so a reservation ending on the same day another starts is still a conflict.
     */
    String OVERLAP_PREDICATE = """
            r.equipment.id = :equipmentId
              AND r.status IN """ + BLOCKING_STATUSES + """
              AND r.startDate <= :endDate
              AND r.endDate >= :startDate
            """;

    /**
     * Overlap check executed inside the transaction that already holds the FOR UPDATE lock on the
     * equipment row. Returning the conflicting rows (instead of a boolean) lets the service report which
     * window is taken.
     */
    @Query("SELECT r FROM Reservation r WHERE " + OVERLAP_PREDICATE)
    List<Reservation> findOverlapping(@Param("equipmentId") Long equipmentId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    @Query("SELECT count(r) > 0 FROM Reservation r WHERE " + OVERLAP_PREDICATE)
    boolean existsOverlapping(@Param("equipmentId") Long equipmentId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    /**
     * Same overlap check, but taking a PESSIMISTIC_WRITE lock on every conflicting row so that a
     * concurrent transaction cannot cancel or confirm them while this one decides. Complements the lock
     * on the equipment row, which is what stops brand new conflicting rows from being inserted.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT r FROM Reservation r WHERE " + OVERLAP_PREDICATE)
    List<Reservation> findOverlappingForUpdate(@Param("equipmentId") Long equipmentId,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    @Query("SELECT r FROM Reservation r WHERE " + OVERLAP_PREDICATE + " AND r.id <> :excludedId")
    List<Reservation> findOverlappingExcluding(@Param("equipmentId") Long equipmentId,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("excludedId") Long excludedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT r FROM Reservation r
            JOIN FETCH r.equipment e
            JOIN FETCH r.renter
            WHERE r.id = :id
            """)
    Optional<Reservation> findDetailedById(@Param("id") Long id);

    Page<Reservation> findByRenterId(Long renterId, Pageable pageable);

    Page<Reservation> findByRenterIdAndStatus(Long renterId, ReservationStatus status, Pageable pageable);

    Page<Reservation> findByEquipmentOwnerId(Long ownerId, Pageable pageable);

    Page<Reservation> findByEquipmentOwnerIdAndStatus(Long ownerId, ReservationStatus status, Pageable pageable);

    Page<Reservation> findByEquipmentId(Long equipmentId, Pageable pageable);

    long countByEquipmentOwnerIdAndStatusIn(Long ownerId, Collection<ReservationStatus> statuses);

    long countByRenterIdAndStatusIn(Long renterId, Collection<ReservationStatus> statuses);

    /**
     * Rental income an owner has secured: the rental amount (deposits excluded, they are returned) of its
     * reservations in the given statuses and currency.
     */
    @Query("""
            SELECT coalesce(sum(r.rentalAmount), 0) FROM Reservation r
            WHERE r.equipment.owner.id = :ownerId
              AND r.status IN :statuses
              AND r.currency = :currency
            """)
    BigDecimal sumRentalAmountForOwner(@Param("ownerId") Long ownerId,
                                       @Param("statuses") Collection<ReservationStatus> statuses,
                                       @Param("currency") String currency);

    /**
     * Calendar feed for the equipment detail screen: the blocked windows within the requested month range.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.equipment.id = :equipmentId
              AND r.status IN """ + BLOCKING_STATUSES + """
              AND r.startDate <= :until
              AND r.endDate >= :from
            ORDER BY r.startDate
            """)
    List<Reservation> findBlockedWindows(@Param("equipmentId") Long equipmentId,
                                         @Param("from") LocalDate from,
                                         @Param("until") LocalDate until);
}
