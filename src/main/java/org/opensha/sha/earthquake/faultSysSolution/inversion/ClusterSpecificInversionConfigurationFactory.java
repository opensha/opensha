package org.opensha.sha.earthquake.faultSysSolution.inversion;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ConnectivityCluster;

/**
 * Interface for a factory that can build a separate {@link InversionConfiguration} for each {@link ConnectivityCluster},
 * which will be solved individually and then combined into a single solution.
 * 
 * @author kevin
 *
 */
public interface ClusterSpecificInversionConfigurationFactory extends InversionConfigurationFactory {
	
	/**
	 * @return true if clusters should be solved for individually, otherwise will be treated as a regular
	 * {@link InversionConfigurationFactory}. Default implementation returns true.
	 */
	public default boolean isSolveClustersIndividually() {
		return true;
	}

}
