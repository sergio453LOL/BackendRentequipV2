package com.rentequip.backend.repositories;

import com.rentequip.backend.entities.PurchaseRequest;
import com.rentequip.backend.enums.PurchaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    @EntityGraph(attributePaths = {"equipment", "equipment.owner", "buyer", "reservation"})
    Page<PurchaseRequest> findByBuyerId(Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = {"equipment", "equipment.owner", "buyer", "reservation"})
    Page<PurchaseRequest> findByEquipmentOwnerId(Long ownerId, Pageable pageable);

    /** The request still in play for a reservation (waiting for the owner, or accepted and waiting for payment). */
    Optional<PurchaseRequest> findFirstByReservationIdAndStatusIn(Long reservationId, Collection<PurchaseStatus> statuses);

    List<PurchaseRequest> findByEquipmentIdAndStatusIn(Long equipmentId, Collection<PurchaseStatus> statuses);

}
