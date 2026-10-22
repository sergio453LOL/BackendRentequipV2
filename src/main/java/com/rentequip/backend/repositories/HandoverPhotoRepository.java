package com.rentequip.backend.repositories;

import com.rentequip.backend.entities.HandoverPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HandoverPhotoRepository extends JpaRepository<HandoverPhoto, Long> {

    @Query("""
            SELECT p FROM HandoverPhoto p JOIN FETCH p.uploadedBy
            WHERE p.reservation.id = :reservationId
            ORDER BY p.createdAt
            """)
    List<HandoverPhoto> findByReservation(@Param("reservationId") Long reservationId);

    long countByReservationId(Long reservationId);
}
