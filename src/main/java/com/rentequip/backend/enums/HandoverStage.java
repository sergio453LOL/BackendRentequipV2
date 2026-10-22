package com.rentequip.backend.enums;

/**
 * Moment of the rental a handover photo documents: when the equipment is delivered to the renter, or when
 * it is returned to the owner. Comparing both is how damage disputes get settled.
 */
public enum HandoverStage {
    DELIVERY,
    RETURN
}
