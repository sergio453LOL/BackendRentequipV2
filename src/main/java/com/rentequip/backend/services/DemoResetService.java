package com.rentequip.backend.services;

import com.rentequip.backend.entities.User;
import com.rentequip.backend.enums.OfferStatus;
import com.rentequip.backend.enums.PurchaseStatus;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.repositories.PriceOfferRepository;
import com.rentequip.backend.repositories.PurchaseRequestRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.repositories.UserRepository;
import com.rentequip.backend.security.DemoAccess;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Puts the shared demo account back in order: every reservation, price offer and purchase request it left
 * open is cancelled, so the calendars of the machines it booked are free again and the owners stop seeing
 * requests from visitors. A platform admin runs it on demand (before a presentation) and it also runs
 * every night. Finished and running rentals are history and stay as they are.
 */
@Service
@RequiredArgsConstructor
public class DemoResetService {

    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);

    private static final Set<ReservationStatus> OPEN_RESERVATIONS = Set.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
    private static final Set<OfferStatus> OPEN_OFFERS = Set.of(OfferStatus.PENDING, OfferStatus.COUNTERED, OfferStatus.ACCEPTED);
    private static final Set<PurchaseStatus> OPEN_PURCHASES = Set.of(PurchaseStatus.PENDING, PurchaseStatus.ACCEPTED);

    private final DemoAccess demoAccess;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final PriceOfferRepository offerRepository;
    private final PurchaseRequestRepository purchaseRepository;

    /** What a reset cancelled. */
    public record DemoResetResult(int reservations, int offers, int purchases) {
    }

    @Transactional
    public DemoResetResult reset() {
        return doReset();
    }

    /** Every night at 04:00 Lima time, so each day starts with a tidy demo. */
    @Scheduled(cron = "0 0 4 * * *", zone = "America/Lima")
    @Transactional
    public void nightly() {
        if (!demoAccess.isEnabled()) {
            return;
        }
        try {
            doReset();
        } catch (ResourceNotFoundException missing) {
            log.warn("Demo reset skipped: {}", missing.getMessage());
        }
    }

    private DemoResetResult doReset() {
        if (!demoAccess.isEnabled()) {
            throw new ResourceNotFoundException("The demo entrance is not enabled");
        }
        User demo = userRepository.findByEmailIgnoreCase(demoAccess.email())
                .orElseThrow(() -> new ResourceNotFoundException("The demo account does not exist"));
        Long companyId = demo.getCompany().getId();

        int purchases = 0;
        for (var purchase : purchaseRepository.findByBuyerId(companyId, Pageable.unpaged())) {
            if (OPEN_PURCHASES.contains(purchase.getStatus())) {
                purchase.setStatus(PurchaseStatus.CANCELLED);
                purchases++;
            }
        }
        int offers = 0;
        for (var offer : offerRepository.findAsRenter(companyId, Pageable.unpaged())) {
            if (OPEN_OFFERS.contains(offer.getStatus())) {
                offer.setStatus(OfferStatus.CANCELLED);
                offers++;
            }
        }
        int reservations = 0;
        for (var reservation : reservationRepository.findByRenterId(companyId, Pageable.unpaged())) {
            if (OPEN_RESERVATIONS.contains(reservation.getStatus())) {
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservations++;
            }
        }
        log.info("Demo account reset: cancelled {} reservations, {} offers and {} purchase requests",
                reservations, offers, purchases);
        return new DemoResetResult(reservations, offers, purchases);
    }
}
