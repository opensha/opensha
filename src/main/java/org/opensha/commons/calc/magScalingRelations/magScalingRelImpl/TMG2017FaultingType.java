package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

public enum TMG2017FaultingType {
    STRIKE_SLIP,
    REVERSE_FAULTING,
    NORMAL_FAULTING,
    NONE;

    public static TMG2017FaultingType fromRake(double rake) {
        if (Double.isNaN(rake)) {
            return NONE;
        } else if ((rake <= 45 && rake >= -45) || rake >= 135 || rake <= -135) {
            return STRIKE_SLIP;
        } else if (rake > 0) {
            return REVERSE_FAULTING;
        } else {
            return NORMAL_FAULTING;
        }
    }
}
