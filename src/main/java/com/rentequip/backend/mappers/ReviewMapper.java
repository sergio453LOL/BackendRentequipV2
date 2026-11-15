package com.rentequip.backend.mappers;

import com.rentequip.backend.dtos.request.ReviewCreateRequest;
import com.rentequip.backend.dtos.response.ReviewResponse;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.entities.Review;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public Review toEntity(ReviewCreateRequest request, Reservation reservation) {
        Review review = new Review();
        review.setReservation(reservation);
        review.setRating(request.rating());
        review.setConditionRating(request.conditionRating());
        review.setComment(request.comment());
        reservation.setReview(review);
        return review;
    }

    public ReviewResponse toResponse(Review review) {
        Reservation reservation = review.getReservation();
        return new ReviewResponse(
                review.getId(),
                reservation.getId(),
                reservation.getEquipment().getId(),
                reservation.getRenter().getId(),
                reservation.getRenter().getName(),
                review.getRating(),
                review.getConditionRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
