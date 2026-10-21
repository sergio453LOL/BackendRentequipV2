package com.rentequip.backend.services;

import com.rentequip.backend.dtos.response.HandoverPhotoResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.HandoverPhoto;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.enums.HandoverStage;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.repositories.HandoverPhotoRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Condition photos of the equipment at delivery and return. Only the two companies of the reservation
 * can add or see them, and only once the rental is agreed (confirmed or later).
 */
@Service
@RequiredArgsConstructor
public class HandoverPhotoService {

    private static final int MAX_PHOTOS_PER_RESERVATION = 30;
    private static final Set<ReservationStatus> DOCUMENTABLE_STATUSES =
            Set.of(ReservationStatus.CONFIRMED, ReservationStatus.IN_PROGRESS, ReservationStatus.COMPLETED);

    private final HandoverPhotoRepository photoRepository;
    private final ReservationRepository reservationRepository;
    private final ImageStorageService imageStorageService;
    private final CurrentUser currentUser;

    @Transactional
    public HandoverPhotoResponse upload(Long reservationId, MultipartFile file, HandoverStage stage,
                                        BigDecimal latitude, BigDecimal longitude, String note) {
        Reservation reservation = findVisibleReservation(reservationId);
        if (!DOCUMENTABLE_STATUSES.contains(reservation.getStatus())) {
            throw new InvalidOperationException("Photos can only be added to confirmed, in progress or completed reservations");
        }
        if (photoRepository.countByReservationId(reservationId) >= MAX_PHOTOS_PER_RESERVATION) {
            throw new InvalidOperationException("A reservation can have at most " + MAX_PHOTOS_PER_RESERVATION + " photos");
        }

        HandoverPhoto photo = new HandoverPhoto();
        photo.setReservation(reservation);
        photo.setUploadedBy(actingCompany(reservation));
        photo.setStage(stage);
        photo.setStoredName(imageStorageService.store(file));
        photo.setLatitude(latitude);
        photo.setLongitude(longitude);
        photo.setNote(note == null || note.isBlank() ? null : note.trim());
        return toResponse(photoRepository.save(photo));
    }

    @Transactional(readOnly = true)
    public List<HandoverPhotoResponse> findByReservation(Long reservationId) {
        findVisibleReservation(reservationId);
        return photoRepository.findByReservation(reservationId).stream().map(this::toResponse).toList();
    }

    private Reservation findVisibleReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", reservationId));
        actingCompany(reservation);
        return reservation;
    }

    private Company actingCompany(Reservation reservation) {
        Long companyId = currentUser.requireCompanyId();
        if (reservation.getRenter().getId().equals(companyId)) {
            return reservation.getRenter();
        }
        Company owner = reservation.getEquipment().getOwner();
        if (owner.getId().equals(companyId)) {
            return owner;
        }
        throw new ForbiddenOperationException("Only the companies of this reservation can access its photos");
    }

    private HandoverPhotoResponse toResponse(HandoverPhoto photo) {
        String imageUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/{name}").buildAndExpand(photo.getStoredName()).toUriString();
        return new HandoverPhotoResponse(
                photo.getId(),
                photo.getReservation().getId(),
                photo.getStage(),
                imageUrl,
                photo.getLatitude(),
                photo.getLongitude(),
                photo.getNote(),
                photo.getUploadedBy().getId(),
                photo.getUploadedBy().getName(),
                photo.getCreatedAt());
    }
}
