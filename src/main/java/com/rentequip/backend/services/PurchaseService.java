package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.EquipmentSaleRequest;
import com.rentequip.backend.dtos.request.PaymentRequest;
import com.rentequip.backend.dtos.response.EquipmentResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.dtos.response.PurchaseQuoteResponse;
import com.rentequip.backend.dtos.response.PurchaseRequestResponse;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.Payment;
import com.rentequip.backend.entities.PurchaseRequest;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.enums.EquipmentStatus;
import com.rentequip.backend.enums.PaymentStatus;
import com.rentequip.backend.enums.PaymentType;
import com.rentequip.backend.enums.PurchaseStatus;
import com.rentequip.backend.enums.ReservationRole;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.MachineHasOtherRentalsException;
import com.rentequip.backend.exceptions.PaymentDeclinedException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.mappers.EquipmentMapper;
import com.rentequip.backend.payments.PaymentGateway;
import com.rentequip.backend.payments.PaymentGateway.GatewayResult;
import com.rentequip.backend.payments.PaymentGateway.PreparedSource;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.PurchaseRequestRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Rent with an option to buy. The owner puts a machine up for sale with a price and the share of the
 * rent that is credited towards it; a renter whose reservation is confirmed, under way or finished asks
 * to buy it; the owner accepts or rejects; the buyer pays the rest by card and the machine is sold.
 */
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    /** A renter can ask to buy once the owner has confirmed the rental, and keeps the option after returning it. */
    private static final Set<ReservationStatus> PURCHASABLE_FROM =
            Set.of(ReservationStatus.CONFIRMED, ReservationStatus.IN_PROGRESS, ReservationStatus.COMPLETED);

    /** Requests still in play: one per reservation at a time. */
    private static final Set<PurchaseStatus> OPEN = Set.of(PurchaseStatus.PENDING, PurchaseStatus.ACCEPTED);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PurchaseRequestRepository purchaseRepository;
    private final ReservationRepository reservationRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentMapper equipmentMapper;
    private final PaymentGateway paymentGateway;
    private final CurrentUser currentUser;

    /** The owner sets, changes or (with a null price) removes the sale terms of one of its machines. */
    @Transactional
    public EquipmentResponse updateSaleTerms(Long equipmentId, EquipmentSaleRequest request) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipment", equipmentId));
        if (!equipment.getOwner().getId().equals(currentUser.requireCompanyId())) {
            throw ForbiddenOperationException.notOwner("equipment", equipmentId);
        }
        if (equipment.getStatus() == EquipmentStatus.SOLD) {
            throw new InvalidOperationException("This machine has already been sold");
        }
        equipment.setSalePrice(request.salePrice());
        equipment.setRentCreditPercent(request.salePrice() == null ? null : request.rentCreditPercent());
        return equipmentMapper.toResponse(equipment);
    }

    @Transactional(readOnly = true)
    public PurchaseQuoteResponse quote(Long reservationId) {
        Reservation reservation = findReservationAsRenter(reservationId);
        Equipment equipment = reservation.getEquipment();
        Long openRequestId = purchaseRepository.findFirstByReservationIdAndStatusIn(reservationId, OPEN)
                .map(PurchaseRequest::getId).orElse(null);

        String reason = ineligibility(reservation);
        if (equipment.getSalePrice() == null) {
            return new PurchaseQuoteResponse(false, reason, null, rentPaid(reservation), 0, BigDecimal.ZERO, null,
                    equipment.getCurrency(), openRequestId);
        }
        Prices prices = pricesFor(reservation);
        return new PurchaseQuoteResponse(reason == null, reason, prices.list(), prices.rentPaid(),
                EquipmentMapper.rentCreditPercentOf(equipment), prices.credit(), prices.finalPrice(),
                equipment.getCurrency(), openRequestId);
    }

    @Transactional
    public PurchaseRequestResponse create(Long reservationId) {
        Reservation reservation = findReservationAsRenter(reservationId);
        String reason = ineligibility(reservation);
        if (reason != null) {
            throw new InvalidOperationException("This machine cannot be bought from this reservation (" + reason + ")");
        }
        if (purchaseRepository.findFirstByReservationIdAndStatusIn(reservationId, OPEN).isPresent()) {
            throw new InvalidOperationException("There is already a purchase request for this reservation");
        }

        Prices prices = pricesFor(reservation);
        PurchaseRequest request = new PurchaseRequest();
        request.setReservation(reservation);
        request.setEquipment(reservation.getEquipment());
        request.setBuyer(reservation.getRenter());
        request.setListPrice(prices.list());
        request.setRentCredit(prices.credit());
        request.setFinalPrice(prices.finalPrice());
        request.setCurrency(reservation.getEquipment().getCurrency());
        log.info("Company {} asked to buy equipment {} (reservation {})",
                reservation.getRenter().getId(), reservation.getEquipment().getId(), reservationId);
        return toResponse(purchaseRepository.save(request));
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseRequestResponse> findMine(ReservationRole role, Pageable pageable) {
        Long companyId = currentUser.requireCompanyId();
        Page<PurchaseRequest> page = role == ReservationRole.OWNER
                ? purchaseRepository.findByEquipmentOwnerId(companyId, pageable)
                : purchaseRepository.findByBuyerId(companyId, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    /** The owner agrees to sell. Other renters still holding the machine must finish first. */
    @Transactional
    public PurchaseRequestResponse accept(Long purchaseId) {
        PurchaseRequest request = findAsSeller(purchaseId);
        requireStatus(request, PurchaseStatus.PENDING);
        requireNoOtherRentals(request);
        request.setStatus(PurchaseStatus.ACCEPTED);
        request.setDecidedAt(LocalDateTime.now());
        return toResponse(request);
    }

    @Transactional
    public PurchaseRequestResponse reject(Long purchaseId) {
        PurchaseRequest request = findAsSeller(purchaseId);
        requireStatus(request, PurchaseStatus.PENDING);
        request.setStatus(PurchaseStatus.REJECTED);
        request.setDecidedAt(LocalDateTime.now());
        return toResponse(request);
    }

    /** The buyer withdraws before paying. */
    @Transactional
    public PurchaseRequestResponse cancel(Long purchaseId) {
        PurchaseRequest request = findAsBuyer(purchaseId);
        if (!OPEN.contains(request.getStatus())) {
            throw new InvalidOperationException("A " + request.getStatus() + " purchase cannot be cancelled");
        }
        request.setStatus(PurchaseStatus.CANCELLED);
        request.setDecidedAt(LocalDateTime.now());
        return toResponse(request);
    }

    /**
     * Charges the price left after the rent credit and marks the machine as sold. Any other open request
     * for the same machine is rejected, because there is nothing left to sell.
     */
    @Transactional
    public PurchaseRequestResponse pay(Long purchaseId, PaymentRequest payment) {
        PurchaseRequest request = findAsBuyer(purchaseId);
        requireStatus(request, PurchaseStatus.ACCEPTED);
        Equipment equipment = equipmentRepository.findByIdForUpdate(request.getEquipment().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Equipment", request.getEquipment().getId()));
        if (equipment.getStatus() == EquipmentStatus.SOLD) {
            throw new InvalidOperationException("This machine has already been sold");
        }
        requireNoOtherRentals(request);
        refreshCredit(request);

        String reference = null;
        if (request.getFinalPrice().signum() > 0) {
            String description = "Compra #" + request.getId() + " - " + equipment.getName();
            PreparedSource prepared = paymentGateway.prepare(payment.paymentToken(),
                    request.getBuyer().getName() + " - " + description);
            if (!prepared.isReady()) {
                throw new PaymentDeclinedException(prepared.declineReason());
            }
            GatewayResult charge = paymentGateway.charge(prepared.source(), request.getFinalPrice(),
                    request.getCurrency(), description);
            if (!charge.approved()) {
                throw new PaymentDeclinedException(charge.declineReason());
            }
            reference = charge.reference();
        }

        LocalDateTime now = LocalDateTime.now();
        request.setStatus(PurchaseStatus.PAID);
        request.setPaidAt(now);
        request.setProviderReference(reference);
        equipment.setStatus(EquipmentStatus.SOLD);
        for (PurchaseRequest other : purchaseRepository.findByEquipmentIdAndStatusIn(equipment.getId(), OPEN)) {
            if (!other.getId().equals(request.getId())) {
                other.setStatus(PurchaseStatus.REJECTED);
                other.setDecidedAt(now);
            }
        }
        log.info("Equipment {} sold to company {} (purchase {})", equipment.getId(), request.getBuyer().getId(), purchaseId);
        return toResponse(request);
    }

    /**
     * The credit was worked out when the buyer asked, but the rental may have been cancelled and refunded
     * since. Right before charging, the reservation must still allow the purchase and the credit is cut to
     * what the rent actually captured now (never raised: the agreed price only goes up if rent was refunded).
     */
    private void refreshCredit(PurchaseRequest request) {
        Reservation reservation = request.getReservation();
        if (!PURCHASABLE_FROM.contains(reservation.getStatus())) {
            throw new InvalidOperationException("The rental this purchase came from is " + reservation.getStatus()
                    + ", so the machine can no longer be bought from it");
        }
        BigDecimal current = rentPaid(reservation)
                .multiply(BigDecimal.valueOf(EquipmentMapper.rentCreditPercentOf(reservation.getEquipment())))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP)
                .min(request.getListPrice());
        BigDecimal credit = current.min(request.getRentCredit());
        if (credit.compareTo(request.getRentCredit()) != 0) {
            log.info("Purchase {}: rent credit cut from {} to {} (rent refunded since the request)",
                    request.getId(), request.getRentCredit(), credit);
            request.setRentCredit(credit);
            request.setFinalPrice(request.getListPrice().subtract(credit));
        }
    }

    /** Null when the renter may buy; otherwise a stable code the client turns into words. */
    private String ineligibility(Reservation reservation) {
        Equipment equipment = reservation.getEquipment();
        if (equipment.getStatus() == EquipmentStatus.SOLD) return "SOLD";
        if (equipment.getSalePrice() == null) return "NOT_FOR_SALE";
        if (!PURCHASABLE_FROM.contains(reservation.getStatus())) return "RESERVATION_STATUS";
        return null;
    }

    private record Prices(BigDecimal list, BigDecimal rentPaid, BigDecimal credit, BigDecimal finalPrice) {
    }

    /** The credit is a share of the rent actually captured for this reservation, never more than the price. */
    private Prices pricesFor(Reservation reservation) {
        Equipment equipment = reservation.getEquipment();
        BigDecimal list = equipment.getSalePrice();
        BigDecimal paid = rentPaid(reservation);
        BigDecimal credit = paid.multiply(BigDecimal.valueOf(EquipmentMapper.rentCreditPercentOf(equipment)))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP)
                .min(list);
        return new Prices(list, paid, credit, list.subtract(credit));
    }

    private static BigDecimal rentPaid(Reservation reservation) {
        return reservation.getPayments().stream()
                .filter(payment -> payment.getType() == PaymentType.RENTAL && payment.getStatus() == PaymentStatus.COMPLETED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireNoOtherRentals(PurchaseRequest request) {
        if (reservationRepository.existsOtherBlocking(request.getEquipment().getId(), request.getReservation().getId())) {
            throw new MachineHasOtherRentalsException(request.getEquipment().getId());
        }
    }

    private static void requireStatus(PurchaseRequest request, PurchaseStatus expected) {
        if (request.getStatus() != expected) {
            throw new InvalidOperationException("The purchase is " + request.getStatus() + ", not " + expected);
        }
    }

    private Reservation findReservationAsRenter(Long reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", reservationId));
        if (!reservation.getRenter().getId().equals(currentUser.requireCompanyId())) {
            throw new ForbiddenOperationException("Only the renting company can buy from this reservation");
        }
        return reservation;
    }

    private PurchaseRequest findOrThrow(Long purchaseId) {
        return purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Purchase request", purchaseId));
    }

    private PurchaseRequest findAsSeller(Long purchaseId) {
        PurchaseRequest request = findOrThrow(purchaseId);
        if (!request.getEquipment().getOwner().getId().equals(currentUser.requireCompanyId())) {
            throw ForbiddenOperationException.notOwner("purchase request", purchaseId);
        }
        return request;
    }

    private PurchaseRequest findAsBuyer(Long purchaseId) {
        PurchaseRequest request = findOrThrow(purchaseId);
        if (!request.getBuyer().getId().equals(currentUser.requireCompanyId())) {
            throw ForbiddenOperationException.notOwner("purchase request", purchaseId);
        }
        return request;
    }

    private PurchaseRequestResponse toResponse(PurchaseRequest request) {
        Equipment equipment = request.getEquipment();
        return new PurchaseRequestResponse(
                request.getId(),
                request.getReservation().getId(),
                equipment.getId(),
                equipment.getName(),
                equipmentMapper.primaryImageUrl(equipment),
                request.getBuyer().getId(),
                request.getBuyer().getName(),
                equipment.getOwner().getId(),
                equipment.getOwner().getName(),
                request.getListPrice(),
                request.getRentCredit(),
                request.getFinalPrice(),
                request.getCurrency(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getDecidedAt(),
                request.getPaidAt());
    }
}
