package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;

public class MultiRuptureCompatibilityResult {
    public final PlausibilityResult plausibilityResult;
    public final MultiRuptureJump jump;

    public final static MultiRuptureCompatibilityResult FAIL =
            new MultiRuptureCompatibilityResult(PlausibilityResult.FAIL_HARD_STOP, null);

    public final static MultiRuptureCompatibilityResult PASS =
            new MultiRuptureCompatibilityResult(PlausibilityResult.PASS, null);

    public MultiRuptureCompatibilityResult(PlausibilityResult plausibilityResult,
                                           MultiRuptureJump jump) {
        this.plausibilityResult = plausibilityResult;
        this.jump = jump;
    }

    /**
     * Jump is overwritten by the value from other (if not null) since we expect subsequent filters to become more specific.
     *
     * @param other
     * @return
     */
    public MultiRuptureCompatibilityResult and(MultiRuptureCompatibilityResult other) {
        return new MultiRuptureCompatibilityResult(
                plausibilityResult.logicalAnd(other.plausibilityResult),
                other.jump != null ? other.jump : jump
        );
    }

    public boolean canContinue() {
        return plausibilityResult.canContinue();
    }

    public boolean isPass() {
        return plausibilityResult.isPass();
    }
}
