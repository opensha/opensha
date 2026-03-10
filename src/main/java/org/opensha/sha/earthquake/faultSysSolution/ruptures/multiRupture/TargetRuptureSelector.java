package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import java.util.Collection;
import java.util.List;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;

/**
 * Strategy for selecting which nearby target ruptures to actually attempt merging with.
 * Used by {@link RuptureMerger} to reduce the combinatorial space of candidate pairs.
 */
public interface TargetRuptureSelector {

    /**
     * Selects a subset of candidate ruptures for merging.
     *
     * @param ruptures all nearby target ruptures found by spatial lookup
     * @return the selected subset to attempt merging with
     */
    List<ClusterRupture> select(Collection<ClusterRupture> ruptures);
}
