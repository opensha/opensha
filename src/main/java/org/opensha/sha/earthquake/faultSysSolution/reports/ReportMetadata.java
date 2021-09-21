package org.opensha.sha.earthquake.faultSysSolution.reports;

import java.util.HashSet;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;

import com.google.common.base.Preconditions;

import scratch.UCERF3.griddedSeismicity.GridSourceProvider;

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
		Region region = findRegion(primary);
		if (region == null && comparison != null)
			region = findRegion(comparison);
		if (region == null)
			// just use bounding box
			region = RupSetMapMaker.buildBufferedRegion(primary.rupSet.getFaultSectionDataList());
		this.region = region;
	}
	
	private Region findRegion(RupSetMetadata meta) {
		if (ReportPageGen.getUCERF3FM(meta.rupSet) != null)
			return new CaliforniaRegions.RELM_TESTING();
		if (meta.rupSet.hasModule(FaultGridAssociations.class))
			return new Region(meta.rupSet.getModule(FaultGridAssociations.class).getRegion());
		if (meta.sol != null && meta.sol.hasModule(GridSourceProvider.class))
			return new Region(meta.sol.getModule(GridSourceProvider.class).getGriddedRegion());
		return null;
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
