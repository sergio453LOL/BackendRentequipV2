package com.rentequip.backend.repositories;

import com.rentequip.backend.entities.PriceOffer;
import com.rentequip.backend.enums.OfferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface PriceOfferRepository extends JpaRepository<PriceOffer, Long> {

    @Query(value = "SELECT o FROM PriceOffer o JOIN FETCH o.equipment e JOIN FETCH e.owner JOIN FETCH o.renter WHERE o.renter.id = :companyId ORDER BY o.updatedAt DESC",
            countQuery = "SELECT count(o) FROM PriceOffer o WHERE o.renter.id = :companyId")
    Page<PriceOffer> findAsRenter(@Param("companyId") Long companyId, Pageable pageable);

    @Query(value = "SELECT o FROM PriceOffer o JOIN FETCH o.equipment e JOIN FETCH e.owner JOIN FETCH o.renter WHERE e.owner.id = :companyId ORDER BY o.updatedAt DESC",
            countQuery = "SELECT count(o) FROM PriceOffer o WHERE o.equipment.owner.id = :companyId")
    Page<PriceOffer> findAsOwner(@Param("companyId") Long companyId, Pageable pageable);

    /** An open negotiation on the same machine blocks a second one: one conversation at a time. */
    boolean existsByEquipmentIdAndRenterIdAndStatusIn(Long equipmentId, Long renterId, Collection<OfferStatus> statuses);

    boolean existsByEquipmentId(Long equipmentId);
}
