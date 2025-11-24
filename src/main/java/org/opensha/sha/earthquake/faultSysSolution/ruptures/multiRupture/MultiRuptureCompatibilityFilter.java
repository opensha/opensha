package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;

public interface MultiRuptureCompatibilityFilter {
    PlausibilityResult apply(MultiRuptureJump jump, boolean verbose);
}
