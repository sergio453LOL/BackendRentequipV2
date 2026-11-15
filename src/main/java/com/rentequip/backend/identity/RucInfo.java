package com.rentequip.backend.identity;

/**
 * What SUNAT says about a taxpayer. A company counts as in good standing when its state is ACTIVO and its
 * fiscal address condition is HABIDO.
 */
public record RucInfo(
        String ruc,
        String name,
        String state,
        String condition,
        String address,
        String district,
        String province,
        String department,
        RucValidator.RucType type
) {

    public boolean inGoodStanding() {
        return "ACTIVO".equalsIgnoreCase(state) && "HABIDO".equalsIgnoreCase(condition);
    }
}
