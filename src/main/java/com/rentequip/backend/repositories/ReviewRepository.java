package com.rentequip.backend.repositories;

import com.rentequip.backend.entities.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByReservationId(Long reservationId);

    boolean existsByReservationId(Long reservationId);

    @Query("SELECT r FROM Review r WHERE r.reservation.equipment.id = :equipmentId")
    Page<Review> findByEquipmentId(@Param("equipmentId") Long equipmentId, Pageable pageable);

    @Query("SELECT avg(r.rating) FROM Review r WHERE r.reservation.equipment.id = :equipmentId")
    Optional<Double> findAverageRatingByEquipmentId(@Param("equipmentId") Long equipmentId);

    @Query("SELECT count(r) FROM Review r WHERE r.reservation.equipment.id = :equipmentId")
    long countByEquipmentId(@Param("equipmentId") Long equipmentId);

    @Query("SELECT DISTINCT r.reservation.equipment.id FROM Review r")
    Set<Long> findReviewedEquipmentIds();
}
