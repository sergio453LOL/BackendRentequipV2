package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.ReviewCreateRequest;
import com.rentequip.backend.dtos.response.EquipmentRatingResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.dtos.response.ReviewResponse;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.entities.Review;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.exceptions.DuplicateResourceException;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.mappers.ReviewMapper;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.repositories.ReviewRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final CurrentUser currentUser;
    private final ReservationRepository reservationRepository;
    private final EquipmentRepository equipmentRepository;
    private final ReviewMapper reviewMapper;

    @Transactional
    public ReviewResponse create(ReviewCreateRequest request) {
        Long actingCompanyId = currentUser.requireCompanyId();
        Reservation reservation = reservationRepository.findDetailedById(request.reservationId())
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", request.reservationId()));

        validateReviewerIsRenter(reservation, actingCompanyId);
        validateReservationIsCompleted(reservation);
        validateNotAlreadyReviewed(reservation.getId());

        Review review = reviewMapper.toEntity(request, reservation);
        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @Transactional
    public void delete(Long reviewId) {
        Long actingCompanyId = currentUser.requireCompanyId();
        Review review = findOrThrow(reviewId);
        validateReviewerIsRenter(review.getReservation(), actingCompanyId);
        reviewRepository.delete(review);
    }

    @Transactional(readOnly = true)
    public ReviewResponse findById(Long reviewId) {
        return reviewMapper.toResponse(findOrThrow(reviewId));
    }

    @Transactional(readOnly = true)
    public ReviewResponse findByReservation(Long reservationId) {
        return reviewMapper.toResponse(reviewRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation " + reservationId + " has no review yet")));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> findByEquipment(Long equipmentId, Pageable pageable) {
        assertEquipmentExists(equipmentId);
        return PageResponse.from(reviewRepository.findByEquipmentId(equipmentId, pageable),
                reviewMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public EquipmentRatingResponse findAverageRating(Long equipmentId) {
        assertEquipmentExists(equipmentId);
        double average = reviewRepository.findAverageRatingByEquipmentId(equipmentId).orElse(0.0);
        long count = reviewRepository.countByEquipmentId(equipmentId);
        return new EquipmentRatingResponse(equipmentId, average, count);
    }

    private Review findOrThrow(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> ResourceNotFoundException.of("Review", reviewId));
    }

    private void assertEquipmentExists(Long equipmentId) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw ResourceNotFoundException.of("Equipment", equipmentId);
        }
    }

    private void validateReviewerIsRenter(Reservation reservation, Long actingCompanyId) {
        if (!reservation.getRenter().getId().equals(actingCompanyId)) {
            throw new ForbiddenOperationException("Only the renting company can review this reservation");
        }
    }

    private void validateReservationIsCompleted(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.COMPLETED) {
            throw new InvalidOperationException("Only completed reservations can be reviewed");
        }
    }

    private void validateNotAlreadyReviewed(Long reservationId) {
        if (reviewRepository.existsByReservationId(reservationId)) {
            throw DuplicateResourceException.of("Review", "reservation", reservationId);
        }
    }
}
