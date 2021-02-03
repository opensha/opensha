package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility;

import org.opensha.commons.data.ShortNamed;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public interface PlausibilityFilter extends ShortNamed {
	
	/**
	 * Apply the plausibility filter to the entire rupture
	 * @param rupture
	 * @param verbose
	 * @return
	 */
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose);
	
	/**
	 * This allows filters to declare that they are directional, i.e., they might fail for a rupture
	 * presented in one direction but pass for an inversion of that rupture. In that case, evaluation
	 * of an existing set of ruptures with this filter should wrap it in the
	 * MultiDirectionalPlausibilityFilter which will test all possible paths through a rupture and pass
	 * if any pass. This answer can depend on whether or not a rupture contains any splays.
	 * 
	 * Default implementation returns false.
	 * 
	 * @param splayed
	 * @return true if it is directional
	 */
	public default boolean isDirectional(boolean splayed) {
		return false;
	}
	
	/**
	 * This returns a TypeAdapter for JSON [de]serialization. Default implementation returns null
	 * which will use standard Gson [de]serialization.
	 * 
	 * The returned class must be static and have a public, no-arg constructor. If the returned
	 * value is of type PlausibilityFilterTypeAdapter, then the init(connStrategy, distAzCalc)
	 * method will be called before [de]serialization.
	 * 
	 * @return
	 */
	public default TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return null;
	}
	
	public static abstract class PlausibilityFilterTypeAdapter extends TypeAdapter<PlausibilityFilter> {
		
		public abstract void init(ClusterConnectionStrategy connStrategy,
				SectionDistanceAzimuthCalculator distAzCalc, Gson gson);
		
	}


}
