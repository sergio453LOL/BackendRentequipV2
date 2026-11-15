package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.OfferCreateRequest;
import com.rentequip.backend.dtos.request.OfferReplyRequest;
import com.rentequip.backend.dtos.response.OfferResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.PriceOffer;
import com.rentequip.backend.enums.EquipmentStatus;
import com.rentequip.backend.enums.NotificationType;
import com.rentequip.backend.enums.OfferStatus;
import com.rentequip.backend.enums.ReservationRole;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.mappers.EquipmentMapper;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.PriceOfferRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Price negotiation between a renter and a machine's owner, with a notification at every turn:
 * the renter proposes, the owner accepts, rejects or counters, and the renter answers the counter.
 */
@Service
@RequiredArgsConstructor
public class OfferService {

    private static final List<OfferStatus> OPEN = List.of(OfferStatus.PENDING, OfferStatus.COUNTERED);
    private static final String OWNER_LINK = "/offers?role=OWNER";
    private static final String RENTER_LINK = "/offers?role=RENTER";

    private final PriceOfferRepository offerRepository;
    private final EquipmentRepository equipmentRepository;
    private final CompanyRepository companyRepository;
    private final EquipmentMapper equipmentMapper;
    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    @Transactional
    public OfferResponse create(OfferCreateRequest request) {
        Long renterId = currentUser.requireCompanyId();
        Equipment equipment = equipmentRepository.findById(request.equipmentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Equipment", request.equipmentId()));
        if (equipment.getOwner().getId().equals(renterId)) {
            throw new InvalidOperationException("No puedes negociar el precio de tu propia máquina");
        }
        if (equipment.getStatus() != EquipmentStatus.AVAILABLE) {
            throw new InvalidOperationException("Esta máquina no está disponible para alquiler");
        }
        if (request.dailyRate().compareTo(equipment.getDailyRate()) >= 0) {
            throw new InvalidOperationException("Tu oferta debe ser menor que la tarifa publicada");
        }
        if (offerRepository.existsByEquipmentIdAndRenterIdAndStatusIn(equipment.getId(), renterId, OPEN)) {
            throw new InvalidOperationException("Ya tienes una negociación abierta para esta máquina");
        }
        Company renter = companyRepository.findById(renterId)
                .orElseThrow(() -> ResourceNotFoundException.of("Company", renterId));

        PriceOffer offer = new PriceOffer();
        offer.setEquipment(equipment);
        offer.setRenter(renter);
        offer.setListDailyRate(equipment.getDailyRate());
        offer.setProposedDailyRate(request.dailyRate());
        offer.setCurrency(equipment.getCurrency());
        offer.setStartDate(request.startDate());
        offer.setEndDate(request.endDate());
        offer.setRenterMessage(trimToNull(request.message()));
        offerRepository.save(offer);

        notificationService.notify(equipment.getOwner().getId(), NotificationType.OFFER_RECEIVED,
                "Contraoferta por " + equipment.getName(),
                renter.getName() + " ofrece " + money(offer.getProposedDailyRate(), offer.getCurrency())
                        + " por día (publicado: " + money(offer.getListDailyRate(), offer.getCurrency()) + ").",
                OWNER_LINK);
        return toResponse(offer);
    }

    @Transactional(readOnly = true)
    public PageResponse<OfferResponse> findMine(ReservationRole role, int page, int size) {
        Long companyId = currentUser.requireCompanyId();
        PageRequest pageable = PageRequest.of(page, size);
        return PageResponse.from(role == ReservationRole.OWNER
                ? offerRepository.findAsOwner(companyId, pageable)
                : offerRepository.findAsRenter(companyId, pageable), this::toResponse);
    }

    /** The owner takes the proposal, or the renter takes the owner's counter. */
    @Transactional
    public OfferResponse accept(Long id, OfferReplyRequest reply) {
        PriceOffer offer = findOrThrow(id);
        boolean byOwner = isOwner(offer);
        if (byOwner && offer.getStatus() == OfferStatus.PENDING) {
            offer.setAgreedDailyRate(offer.getProposedDailyRate());
            offer.setOwnerMessage(trimToNull(reply == null ? null : reply.message()));
        } else if (!byOwner && isRenter(offer) && offer.getStatus() == OfferStatus.COUNTERED) {
            offer.setAgreedDailyRate(offer.getCounterDailyRate());
        } else {
            throw notYourTurn(offer);
        }
        offer.setStatus(OfferStatus.ACCEPTED);
        String price = money(offer.getAgreedDailyRate(), offer.getCurrency());
        if (byOwner) {
            notificationService.notify(offer.getRenter().getId(), NotificationType.OFFER_ACCEPTED,
                    "Aceptaron tu oferta por " + offer.getEquipment().getName(),
                    offer.getEquipment().getOwner().getName() + " aceptó " + price + " por día. Ya puedes reservar a ese precio.",
                    "/equipment/" + offer.getEquipment().getId() + "?offer=" + offer.getId());
        } else {
            notificationService.notify(offer.getEquipment().getOwner().getId(), NotificationType.OFFER_ACCEPTED,
                    "Aceptaron tu contraoferta por " + offer.getEquipment().getName(),
                    offer.getRenter().getName() + " aceptó " + price + " por día.", OWNER_LINK);
        }
        return toResponse(offer);
    }

    @Transactional
    public OfferResponse reject(Long id, OfferReplyRequest reply) {
        PriceOffer offer = findOrThrow(id);
        boolean byOwner = isOwner(offer);
        if (byOwner && offer.getStatus() == OfferStatus.PENDING) {
            offer.setOwnerMessage(trimToNull(reply == null ? null : reply.message()));
            notificationService.notify(offer.getRenter().getId(), NotificationType.OFFER_REJECTED,
                    "Rechazaron tu oferta por " + offer.getEquipment().getName(),
                    offer.getEquipment().getOwner().getName() + " mantiene su tarifa de "
                            + money(offer.getListDailyRate(), offer.getCurrency()) + " por día.", RENTER_LINK);
        } else if (!byOwner && isRenter(offer) && offer.getStatus() == OfferStatus.COUNTERED) {
            notificationService.notify(offer.getEquipment().getOwner().getId(), NotificationType.OFFER_REJECTED,
                    "Rechazaron tu contraoferta por " + offer.getEquipment().getName(),
                    offer.getRenter().getName() + " no aceptó " + money(offer.getCounterDailyRate(), offer.getCurrency())
                            + " por día.", OWNER_LINK);
        } else {
            throw notYourTurn(offer);
        }
        offer.setStatus(OfferStatus.REJECTED);
        return toResponse(offer);
    }

    /** The owner answers with another price, between the renter's proposal and the published rate. */
    @Transactional
    public OfferResponse counter(Long id, OfferReplyRequest reply) {
        PriceOffer offer = findOrThrow(id);
        if (!isOwner(offer) || offer.getStatus() != OfferStatus.PENDING) {
            throw notYourTurn(offer);
        }
        if (reply == null || reply.dailyRate() == null) {
            throw new InvalidOperationException("Indica el precio por día de tu contraoferta");
        }
        if (reply.dailyRate().compareTo(offer.getProposedDailyRate()) <= 0
                || reply.dailyRate().compareTo(offer.getListDailyRate()) >= 0) {
            throw new InvalidOperationException("La contraoferta debe estar entre la oferta recibida y tu tarifa publicada");
        }
        offer.setCounterDailyRate(reply.dailyRate());
        offer.setOwnerMessage(trimToNull(reply.message()));
        offer.setStatus(OfferStatus.COUNTERED);
        notificationService.notify(offer.getRenter().getId(), NotificationType.OFFER_COUNTERED,
                "Contraoferta por " + offer.getEquipment().getName(),
                offer.getEquipment().getOwner().getName() + " te propone " + money(reply.dailyRate(), offer.getCurrency())
                        + " por día. Acéptala o recházala.", RENTER_LINK);
        return toResponse(offer);
    }

    @Transactional
    public OfferResponse cancel(Long id) {
        PriceOffer offer = findOrThrow(id);
        if (!isRenter(offer) || !OPEN.contains(offer.getStatus())) {
            throw notYourTurn(offer);
        }
        offer.setStatus(OfferStatus.CANCELLED);
        return toResponse(offer);
    }

    /**
     * Spends an accepted offer on a reservation of the same machine by the same renter, and returns the
     * agreed daily rate the reservation must charge.
     */
    @Transactional
    public BigDecimal consumeForReservation(Long offerId, Long equipmentId, Long renterId) {
        PriceOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Offer", offerId));
        if (!offer.getEquipment().getId().equals(equipmentId) || !offer.getRenter().getId().equals(renterId)) {
            throw new ForbiddenOperationException("Esta oferta no corresponde a esta reserva");
        }
        if (offer.getStatus() != OfferStatus.ACCEPTED) {
            throw new InvalidOperationException("La oferta no está aceptada o ya se usó en otra reserva");
        }
        offer.setStatus(OfferStatus.USED);
        return offer.getAgreedDailyRate();
    }

    private PriceOffer findOrThrow(Long id) {
        PriceOffer offer = offerRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Offer", id));
        if (!isOwner(offer) && !isRenter(offer)) {
            throw new ForbiddenOperationException("No participas en esta negociación");
        }
        return offer;
    }

    private boolean isOwner(PriceOffer offer) {
        return offer.getEquipment().getOwner().getId().equals(currentUser.requireCompanyId());
    }

    private boolean isRenter(PriceOffer offer) {
        return offer.getRenter().getId().equals(currentUser.requireCompanyId());
    }

    private static InvalidOperationException notYourTurn(PriceOffer offer) {
        return new InvalidOperationException(switch (offer.getStatus()) {
            case PENDING -> "La oferta espera la respuesta del dueño";
            case COUNTERED -> "La contraoferta espera la respuesta del arrendatario";
            default -> "Esta negociación ya terminó";
        });
    }

    private OfferResponse toResponse(PriceOffer offer) {
        Equipment equipment = offer.getEquipment();
        return new OfferResponse(offer.getId(), equipment.getId(), equipment.getName(),
                equipmentMapper.toSummary(equipment).primaryImageUrl(), equipment.getOwner().getId(),
                equipment.getOwner().getName(), offer.getRenter().getId(), offer.getRenter().getName(),
                offer.getListDailyRate(), offer.getProposedDailyRate(), offer.getCounterDailyRate(),
                offer.getAgreedDailyRate(), offer.getCurrency(), offer.getStartDate(), offer.getEndDate(),
                offer.getRenterMessage(), offer.getOwnerMessage(), offer.getStatus(), offer.getCreatedAt(),
                offer.getUpdatedAt());
    }

    private static String money(BigDecimal amount, String currency) {
        String symbol = "PEN".equals(currency) ? "S/" : "USD".equals(currency) ? "US$" : currency;
        return symbol + " " + String.format(Locale.US, "%,.2f", amount);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
