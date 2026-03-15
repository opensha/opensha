package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;

/**
 * A {@link ClusterRupture} that combines two separate ruptures (e.g. crustal and subduction)
 * by adding the target rupture as a splay of the nucleation rupture. Created via
 * {@link #takeSplayJump(MultiRuptureJump)}.
 */
public class MultiClusterRupture extends ClusterRupture {

    protected MultiClusterRupture(FaultSubsectionCluster[] clusters,
                               ImmutableList<Jump> internalJumps,
                               ImmutableMap<Jump, ClusterRupture> splays,
                               UniqueRupture unique,
                               UniqueRupture internalUnique) {
        super(clusters, internalJumps, splays, unique, internalUnique, false);
    }

    /**
     * Creates a new multi-cluster rupture by adding the target rupture from the jump as a splay
     * of the nucleation (from) rupture.
     *
     * @param jump the jump connecting two ruptures
     * @return a new rupture combining both ruptures via a splay connection
     */
    public static ClusterRupture takeSplayJump(MultiRuptureJump jump) {
        ImmutableMap.Builder<Jump, ClusterRupture> splayBuilder = ImmutableMap.builder();
        splayBuilder.putAll(jump.fromRupture.splays);
        splayBuilder.put(jump, jump.toRupture);
        ImmutableMap<Jump, ClusterRupture> newSplays = splayBuilder.build();

        UniqueRupture newUnique = UniqueRupture.add(jump.fromRupture.unique, jump.toRupture.unique);
        return new MultiClusterRupture(
                jump.fromRupture.clusters,
                jump.fromRupture.internalJumps,
                newSplays,
                newUnique,
                jump.fromRupture.internalUnique);
    }
}
