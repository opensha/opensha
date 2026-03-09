package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import java.util.Collection;
import java.util.List;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;

public interface  TargetRuptureSelector {
    List<ClusterRupture> select(Collection<ClusterRupture> ruptures);
}
