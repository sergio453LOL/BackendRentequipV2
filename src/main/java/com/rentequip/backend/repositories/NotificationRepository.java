package com.rentequip.backend.repositories;

import com.rentequip.backend.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    long countByCompanyIdAndReadAtIsNull(Long companyId);

    Optional<Notification> findByIdAndCompanyId(Long id, Long companyId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.company.id = :companyId AND n.readAt IS NULL")
    int markAllRead(@Param("companyId") Long companyId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.company.id = :companyId")
    int deleteAllByCompany(@Param("companyId") Long companyId);
}
