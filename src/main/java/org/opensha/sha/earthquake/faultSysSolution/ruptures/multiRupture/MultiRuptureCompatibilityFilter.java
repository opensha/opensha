package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;

/**
 * Evaluates whether two ruptures connected by a {@link MultiRuptureJump} are physically
 * compatible and can be merged into a single multi-rupture. Filters are applied sequentially
 * by {@link RuptureMerger}; a {@link PlausibilityResult#FAIL_HARD_STOP} causes early exit.
 */
public interface MultiRuptureCompatibilityFilter {

    /**
     * @param jump the jump connecting two candidate ruptures
     * @param verbose if true, print diagnostic information to stdout
     * @return {@link PlausibilityResult#PASS} if compatible, a failure result otherwise
     */
    PlausibilityResult apply(MultiRuptureJump jump, boolean verbose);
}
