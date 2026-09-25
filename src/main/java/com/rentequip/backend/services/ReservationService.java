package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.ReservationCreateRequest;
import com.rentequip.backend.dtos.request.ReservationStatusUpdateRequest;
import com.rentequip.backend.dtos.response.AvailabilityResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.dtos.response.ReservationResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.enums.EquipmentStatus;
import com.rentequip.backend.events.ReservationCreatedEvent;
import com.rentequip.backend.events.ReservationSnapshot;
import com.rentequip.backend.events.ReservationStatusChangedEvent;
import com.rentequip.backend.enums.ReservationRole;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.exceptions.EquipmentUnavailableException;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.InvalidReservationDateException;
import com.rentequip.backend.exceptions.OverbookingException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.mappers.ReservationMapper;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Booking flow. Creation and confirmation run inside a single transaction that first takes a
 * PESSIMISTIC_WRITE lock on the equipment row, so simultaneous requests for the same machine are
 * serialized by the database and the second one is rejected with 409 instead of overbooking.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int MAX_RENTAL_DAYS = 365;

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUser currentUser;
    private final ReservationRepository reservationRepository;
    private final EquipmentRepository equipmentRepository;
    private final CompanyRepository companyRepository;
    private final ReservationMapper reservationMapper;
    private final ReservationStatusPolicy statusPolicy;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse create(ReservationCreateRequest request) {
        validateDateRange(request.startDate(), request.endDate());

        Company renter = findCompanyOrThrow(currentUser.requireCompanyId());
        Equipment equipment = lockEquipment(request.equipmentId());

        validateEquipmentIsRentable(equipment);
        validateRenterIsNotOwner(equipment, renter);
        validateNoOverlap(equipment.getId(), request.startDate(), request.endDate());

        Reservation reservation = reservationRepository.save(
                reservationMapper.toEntity(request, equipment, renter));
        log.info("Reservation {} created for equipment {} by company {} ({} to {})",
                reservation.getId(), equipment.getId(), renter.getId(),
                reservation.getStartDate(), reservation.getEndDate());

        // Published inside the transaction but delivered after the commit: a rolled back booking
        // must never generate a notification for a reservation that does not exist.
        eventPublisher.publishEvent(new ReservationCreatedEvent(ReservationSnapshot.of(reservation)));
        return reservationMapper.toResponse(reservation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse updateStatus(Long reservationId, ReservationStatusUpdateRequest request) {
        Long actingCompanyId = currentUser.requireCompanyId();
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", reservationId));

        ReservationStatus previousStatus = reservation.getStatus();
        statusPolicy.validateTransition(reservation, request.status(), actingCompanyId);
        applyStatusChange(reservation, request);
        log.info("Reservation {} moved to {} by company {}", reservationId, request.status(), actingCompanyId);

        eventPublisher.publishEvent(new ReservationStatusChangedEvent(
                ReservationSnapshot.of(reservation), previousStatus, request.reason()));
        return reservationMapper.toResponse(reservation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse confirm(Long reservationId) {
        return updateStatus(reservationId,
                new ReservationStatusUpdateRequest(ReservationStatus.CONFIRMED, null));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse cancel(Long reservationId, String reason) {
        return updateStatus(reservationId,
                new ReservationStatusUpdateRequest(ReservationStatus.CANCELLED, reason));
    }

    @Transactional(readOnly = true)
    public ReservationResponse findById(Long reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", reservationId));
        validateVisibility(reservation, currentUser.requireCompanyId());
        return reservationMapper.toResponse(reservation);
    }

    /**
     * Single entry point for a company listing its own reservations. Which side of the marketplace it is
     * asking about is a domain decision, so the branch lives here and not in the controller.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> findForCompany(ReservationRole role,
                                                             ReservationStatus status, Pageable pageable) {
        Long companyId = currentUser.requireCompanyId();
        return role == ReservationRole.OWNER
                ? findByOwner(companyId, status, pageable)
                : findByRenter(companyId, status, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> findByRenter(Long renterCompanyId, ReservationStatus status,
                                                          Pageable pageable) {
        Page<Reservation> page = status == null
                ? reservationRepository.findByRenterId(renterCompanyId, pageable)
                : reservationRepository.findByRenterIdAndStatus(renterCompanyId, status, pageable);
        return PageResponse.from(page, reservationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> findByOwner(Long ownerCompanyId, ReservationStatus status,
                                                         Pageable pageable) {
        Page<Reservation> page = status == null
                ? reservationRepository.findByEquipmentOwnerId(ownerCompanyId, pageable)
                : reservationRepository.findByEquipmentOwnerIdAndStatus(ownerCompanyId, status, pageable);
        return PageResponse.from(page, reservationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> findByEquipment(Long equipmentId, Pageable pageable) {
        assertEquipmentExists(equipmentId);
        return PageResponse.from(reservationRepository.findByEquipmentId(equipmentId, pageable),
                reservationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(Long equipmentId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        assertEquipmentExists(equipmentId);
        boolean available = !reservationRepository.existsOverlapping(equipmentId, startDate, endDate);
        return new AvailabilityResponse(equipmentId, startDate, endDate, available);
    }

    private void applyStatusChange(Reservation reservation, ReservationStatusUpdateRequest request) {
        if (request.status() == ReservationStatus.CONFIRMED) {
            revalidateAvailabilityBeforeConfirming(reservation);
        }
        reservation.setStatus(request.status());
        appendReason(reservation, request.reason());
        synchronizeEquipmentStatus(reservation, request.status());
    }

    /**
     * A pending reservation only holds the calendar while it is pending; re-checking under the lock
     * keeps confirmation safe if the rules ever allow overlapping pending requests.
     */
    private void revalidateAvailabilityBeforeConfirming(Reservation reservation) {
        Equipment equipment = lockEquipment(reservation.getEquipment().getId());
        boolean conflict = !reservationRepository.findOverlappingExcluding(
                equipment.getId(), reservation.getStartDate(), reservation.getEndDate(), reservation.getId())
                .isEmpty();
        if (conflict) {
            throw OverbookingException.of(equipment.getId(), reservation.getStartDate(), reservation.getEndDate());
        }
    }

    private void synchronizeEquipmentStatus(Reservation reservation, ReservationStatus status) {
        Equipment equipment = reservation.getEquipment();
        if (status == ReservationStatus.IN_PROGRESS) {
            equipment.setStatus(EquipmentStatus.RENTED);
        } else if (statusPolicy.releasesCalendar(status) && equipment.getStatus() == EquipmentStatus.RENTED) {
            equipment.setStatus(EquipmentStatus.AVAILABLE);
        }
    }

    private void appendReason(Reservation reservation, String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        String existing = reservation.getNotes() == null ? "" : reservation.getNotes() + " | ";
        String merged = existing + reason.trim();
        reservation.setNotes(merged.length() > 500 ? merged.substring(0, 500) : merged);
    }

    private Equipment lockEquipment(Long equipmentId) {
        return equipmentRepository.findByIdForUpdate(equipmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipment", equipmentId));
    }

    private Company findCompanyOrThrow(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Company", companyId));
    }

    private void assertEquipmentExists(Long equipmentId) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw ResourceNotFoundException.of("Equipment", equipmentId);
        }
    }

    private void validateNoOverlap(Long equipmentId, LocalDate startDate, LocalDate endDate) {
        if (!reservationRepository.findOverlappingForUpdate(equipmentId, startDate, endDate).isEmpty()) {
            throw OverbookingException.of(equipmentId, startDate, endDate);
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new InvalidReservationDateException("Both start date and end date are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidReservationDateException("End date must be on or after start date");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new InvalidReservationDateException("Start date cannot be in the past");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_RENTAL_DAYS) {
            throw new InvalidReservationDateException(
                    "A rental cannot exceed " + MAX_RENTAL_DAYS + " days");
        }
    }

    private void validateEquipmentIsRentable(Equipment equipment) {
        EquipmentStatus status = equipment.getStatus();
        if (status == EquipmentStatus.MAINTENANCE || status == EquipmentStatus.INACTIVE) {
            throw EquipmentUnavailableException.of(equipment.getId(), status);
        }
    }

    private void validateRenterIsNotOwner(Equipment equipment, Company renter) {
        if (equipment.getOwner().getId().equals(renter.getId())) {
            throw new InvalidOperationException("A company cannot rent its own equipment");
        }
    }

    private void validateVisibility(Reservation reservation, Long actingCompanyId) {
        boolean isRenter = reservation.getRenter().getId().equals(actingCompanyId);
        boolean isOwner = reservation.getEquipment().getOwner().getId().equals(actingCompanyId);
        if (!isRenter && !isOwner) {
            throw ForbiddenOperationException.notOwner("reservation", reservation.getId());
        }
    }
}
