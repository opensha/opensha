package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;

public interface BranchDependentSampler<E extends BranchDependentSampler<?>> {
	
	public abstract E getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings);

}
