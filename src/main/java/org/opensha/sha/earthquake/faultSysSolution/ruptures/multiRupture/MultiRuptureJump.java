package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

public class MultiRuptureJump extends Jump {
    public final ClusterRupture fromRupture;
    public final ClusterRupture toRupture;

    public MultiRuptureJump(FaultSection fromSection,
                            ClusterRupture fromRupture,
                            FaultSection toSection,
                            ClusterRupture toRupture,
                            double distance) {
        this(
                fromSection,
                fromRupture.getTreeNavigator().locateCluster(fromSection),
                fromRupture,
                toSection,
                toRupture.getTreeNavigator().locateCluster(toSection),
                toRupture,
                distance);
    }

    public MultiRuptureJump(FaultSection fromSection,
                            FaultSubsectionCluster fromCluster,
                            ClusterRupture fromRupture,
                            FaultSection toSection,
                            FaultSubsectionCluster toCluster,
                            ClusterRupture toRupture,
                            double distance) {
        super(fromSection, fromCluster, toSection, toCluster, distance);
        this.fromRupture = fromRupture;
        this.toRupture = toRupture;
    }
}
