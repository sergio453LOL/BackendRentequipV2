package com.rentequip.backend.dtos.response;

import java.math.BigDecimal;

/**
 * Quick metrics for the home screen of the authenticated company, seen from both sides of the marketplace.
 *
 * @param publishedEquipment          equipment the company offers that is not deactivated
 * @param activeReservationsAsOwner   bookings of its equipment that are confirmed or in progress
 * @param pendingRequestsAsOwner      bookings of its equipment waiting for its approval
 * @param activeReservationsAsRenter  bookings the company made that are pending, confirmed or in progress
 * @param estimatedRevenue            rental income from confirmed, in progress and completed bookings. It is
 *                                    estimated from the reservations because there is no payment gateway yet
 */
public record CompanyDashboardResponse(
        Long companyId,
        long publishedEquipment,
        long activeReservationsAsOwner,
        long pendingRequestsAsOwner,
        long activeReservationsAsRenter,
        BigDecimal estimatedRevenue,
        String currency
) {
}
