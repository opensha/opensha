package org.opensha.sha.earthquake.faultSysSolution.reports;

import java.util.HashSet;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;

import com.google.common.base.Preconditions;

public class ReportMetadata {
	
	public final RupSetMetadata primary;
	public final RupSetOverlap primaryOverlap;
	public final RupSetMetadata comparison;
	public final RupSetOverlap comparisonOverlap;
	
	public final Region region;
	
	public ReportMetadata(RupSetMetadata primary) {
		this(primary, null);
	}
	
	public ReportMetadata(RupSetMetadata primary, RupSetMetadata comparison) {
		Preconditions.checkNotNull(primary);
		this.primary = primary;
		this.comparison = comparison;
		if (comparison == null) {
			primaryOverlap = null;
			comparisonOverlap = null;
		} else {
			primaryOverlap = new RupSetOverlap(primary, comparison);
			comparisonOverlap = new RupSetOverlap(comparison, primary);
		}
		
		// look for a reagion
		Region region = detectRegion(primary);
		if (region == null && comparison != null)
			region = detectRegion(comparison);
		if (region == null)
			// just use bounding box
			region = RupSetMapMaker.buildBufferedRegion(primary.rupSet.getFaultSectionDataList());
		this.region = region;
	}
	
	public static Region detectRegion(RupSetMetadata meta) {
		return detectRegion(meta.rupSet, meta.sol);
	}
	
	public static Region detectRegion(FaultSystemRupSet rupSet) {
		return detectRegion(rupSet, null);
	}
	
	public static Region detectRegion(FaultSystemSolution sol) {
		return detectRegion(sol.getRupSet(), sol);
	}
	
	private static Region detectRegion(FaultSystemRupSet rupSet, FaultSystemSolution sol) {
		if (ReportPageGen.getUCERF3FM(rupSet) != null)
			return new CaliforniaRegions.RELM_TESTING();
		if (rupSet.hasModule(FaultGridAssociations.class))
			return new Region(rupSet.getModule(FaultGridAssociations.class).getRegion());
		if (sol != null && sol.hasModule(GridSourceProvider.class))
			return new Region(sol.getModule(GridSourceProvider.class).getGriddedRegion());
		return null;
	}
	
	public boolean hasComparison() {
		return comparison != null;
	}
	
	public boolean hasComparisonSol() {
		return comparison != null && comparison.sol != null;
	}
	
	public boolean hasPrimarySol() {
		return primary.sol != null;
	}
	
	public boolean hasAnySol() {
		return hasPrimarySol() || hasComparisonSol();
	}
	
	public static class RupSetOverlap {
		
		// ruptures
		public final int numCommonRuptures;
		public final int numUniqueRuptures;
		public final transient HashSet<Integer> commonIndexes;
		public final transient HashSet<Integer> uniqueIndexes;
		public final transient HashSet<UniqueRupture> commonUniques;
		public final transient HashSet<UniqueRupture> uniqueUniques;
		public final double commonRuptureRate;
		public final double uniqueRuptureRate;
		
		// jumps
		public final int numCommonJumps;
		public final transient HashSet<Jump> commonJumps;
		public final int numUniqueJumps;
		public final transient HashSet<Jump> uniqueJumps;
		
		public RupSetOverlap(RupSetMetadata meta, RupSetMetadata comp) {
			// ruptures
			commonIndexes = new HashSet<>();
			uniqueIndexes = new HashSet<>();
			commonUniques = new HashSet<>();
			uniqueUniques = new HashSet<>();
			double commonRuptureRate = Double.NaN;
			double uniqueRuptureRate = Double.NaN;
			if (meta.sol != null) {
				commonRuptureRate = 0d;
				uniqueRuptureRate = 0d;
			}
			
			HashSet<UniqueRupture> compUniques = new HashSet<>(comp.uniques);
			
			for (int r=0; r<meta.numRuptures; r++) {
				UniqueRupture unique = meta.uniques.get(r);
				if (compUniques.contains(unique)) {
					commonUniques.add(unique);
					commonIndexes.add(r);
					if (meta.sol != null)
						commonRuptureRate += meta.sol.getRateForRup(r);
				} else {
					uniqueUniques.add(unique);
					uniqueIndexes.add(r);
					if (meta.sol != null)
						uniqueRuptureRate += meta.sol.getRateForRup(r);
				}
			}
			
			numCommonRuptures = commonIndexes.size();
			numUniqueRuptures = uniqueIndexes.size();
			this.commonRuptureRate = commonRuptureRate;
			this.uniqueRuptureRate = uniqueRuptureRate;
			
			// jumps
			commonJumps = new HashSet<>();
			uniqueJumps = new HashSet<>();
			for (Jump jump : meta.jumps) {
				if (comp.jumps.contains(jump))
					commonJumps.add(jump);
				else
					uniqueJumps.add(jump);
			}
			numCommonJumps = commonJumps.size();
			numUniqueJumps = uniqueJumps.size();
		}
		
	}

}
