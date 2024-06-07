package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.ParentCoulombCompatibilityFilter.Directionality;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

/**
 * Version of {@link MultiRuptureCoulombFilter} that is specific to the fraction of positive Coulomb interactions
 */
public class MultiRuptureFractCoulombPositiveFilter extends MultiRuptureCoulombFilter {
	
	public MultiRuptureFractCoulombPositiveFilter(SubSectStiffnessCalculator stiffnessCalc, float threshold) {
		this(stiffnessCalc, threshold, Directionality.EITHER);
	}
	
	public MultiRuptureFractCoulombPositiveFilter(SubSectStiffnessCalculator stiffnessCalc, float threshold,
			Directionality directionality) {
		super(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT),
				threshold, directionality);
	}
	
	public static void main(String[] args) {
		FaultSystemRupSet rupSet = null; // TODO load it in here
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		// stiffness grid spacing, increase if it's taking too long
		double stiffGridSpacing = 1d;
		// stiffness calculation constants
		double lameLambda = 3e4;
		double lameMu = 3e4;
		double coeffOfFriction = 0.5;
		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
				subSects, stiffGridSpacing, lameLambda, lameMu, coeffOfFriction, PatchAlignment.FILL_OVERLAP, 1d);
		AggregatedStiffnessCache stiffnessCache = stiffnessCalc.getAggregationCache(StiffnessType.CFF);
		
		File cacheDir = new File("/path/to/cache/dir");
		File stiffnessCacheFile = null;
		int stiffnessCacheSize = 0;
		if (cacheDir != null && cacheDir.exists()) {
			stiffnessCacheFile = new File(cacheDir, stiffnessCache.getCacheFileName());
			stiffnessCacheSize = 0;
			if (stiffnessCacheFile.exists()) {
				try {
					stiffnessCacheSize = stiffnessCache.loadCacheFile(stiffnessCacheFile);
				} catch (IOException e) {
					System.err.println("WARNING: exception loading previous cache");
					e.printStackTrace();
				}
			}
		}
		
		// what fraction of interactions should be positive? this number will take some tuning
		float threshold = 0.9f;
		MultiRuptureFractCoulombPositiveFilter filter = new MultiRuptureFractCoulombPositiveFilter(stiffnessCalc, threshold);
		
		// TODO do your combining here using that filter
		
		
		// write out the cache to make future calculations faster
		if (stiffnessCacheFile != null
				&& stiffnessCacheSize < stiffnessCache.calcCacheSize()) {
			System.out.println("Writing stiffness cache to "+stiffnessCacheFile.getAbsolutePath());
			try {
				stiffnessCache.writeCacheFile(stiffnessCacheFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("DONE writing stiffness cache");
		}
	}

}
