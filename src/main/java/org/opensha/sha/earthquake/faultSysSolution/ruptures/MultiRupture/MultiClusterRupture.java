package org.opensha.sha.earthquake.faultSysSolution.ruptures.MultiRupture;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;

public class MultiClusterRupture extends ClusterRupture {

    protected MultiClusterRupture(FaultSubsectionCluster[] clusters,
                               ImmutableList<Jump> internalJumps,
                               ImmutableMap<Jump, ClusterRupture> splays,
                               UniqueRupture unique,
                               UniqueRupture internalUnique) {
        super(clusters, internalJumps, splays, unique, internalUnique, false);
    }

    public static ClusterRupture takeSplayJump(RuptureJump jump) {
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
