package com.rentequip.backend.services;

import com.rentequip.backend.dtos.response.CompanyDashboardResponse;
import com.rentequip.backend.enums.EquipmentStatus;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CompanyDashboardService {

    /** Equipment defaults to PEN, so the dashboard reports income in soles. */
    static final String REVENUE_CURRENCY = "PEN";

    private static final Set<ReservationStatus> ACTIVE_AS_OWNER =
            EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.IN_PROGRESS);

    private static final Set<ReservationStatus> ACTIVE_AS_RENTER =
            EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.IN_PROGRESS);

    private static final Set<ReservationStatus> REVENUE_STATUSES =
            EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.IN_PROGRESS, ReservationStatus.COMPLETED);

    private final EquipmentRepository equipmentRepository;
    private final ReservationRepository reservationRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public CompanyDashboardResponse getForCurrentCompany() {
        Long companyId = currentUser.requireCompanyId();
        return new CompanyDashboardResponse(
                companyId,
                equipmentRepository.countByOwnerIdAndStatusNot(companyId, EquipmentStatus.INACTIVE),
                reservationRepository.countByEquipmentOwnerIdAndStatusIn(companyId, ACTIVE_AS_OWNER),
                reservationRepository.countByEquipmentOwnerIdAndStatusIn(companyId, Set.of(ReservationStatus.PENDING)),
                reservationRepository.countByRenterIdAndStatusIn(companyId, ACTIVE_AS_RENTER),
                reservationRepository.sumRentalAmountForOwner(companyId, REVENUE_STATUSES, REVENUE_CURRENCY),
                REVENUE_CURRENCY);
    }
}
