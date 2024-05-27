package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;

public interface MultiRuptureCompatibilityFilter {
    MultiRuptureCompatibilityResult apply(MultiRuptureCompatibilityResult previousResult,
                                          ClusterRupture nucleation,
                                          ClusterRupture target,
                                          boolean verbose);
}
