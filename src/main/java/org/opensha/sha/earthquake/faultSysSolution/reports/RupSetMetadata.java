package org.opensha.sha.earthquake.faultSysSolution.reports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalarValues;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;

import com.google.common.base.Preconditions;

public class RupSetMetadata {
	
	public final String name;
	public final int numRuptures;
	public final double totalRate;
	public final int numSingleStrandRuptures;
	
	public final int availableConnections;
	public final int actualConnections;
	
	public final List<ScalarRange> scalarRanges;
	
	public final transient List<HistScalarValues> scalarValues;
	public final transient FaultSystemRupSet rupSet;
	public final transient FaultSystemSolution sol;
	public final transient HashSet<Jump> jumps;
	public final transient HashMap<Jump, Double> jumpRates;
	public final transient List<UniqueRupture> uniques;
	public final transient HashMap<Jump, List<Integer>> jumpRupsMap;
	
	public RupSetMetadata(String name, FaultSystemRupSet rupSet) {
		this(name, rupSet, null);
	}
	
	public RupSetMetadata(String name, FaultSystemSolution sol) {
		this(name, sol.getRupSet(), sol);
	}
	
	public RupSetMetadata(String name, FaultSystemRupSet rupSet, FaultSystemSolution sol) {
		Preconditions.checkNotNull(rupSet, "Must supply a rupture set");
		this.rupSet = rupSet;
		this.sol = sol;
		this.name = name;
		this.numRuptures = rupSet.getNumRuptures();
		if (sol == null)
			totalRate = Double.NaN;
		else
			totalRate = sol.getTotalRateForAllFaultSystemRups();
		
		jumps = new HashSet<>();
		uniques = new ArrayList<>();
		ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
		if (cRups == null)
			// assume single stranded for our purposes here
			cRups = ClusterRuptures.singleStranged(rupSet);
		jumpRates = sol == null ? null : new HashMap<>();
		jumpRupsMap = new HashMap<>();
		int numSingleStrandRuptures = 0;
		for (int r=0; r<numRuptures; r++) {
			ClusterRupture rupture = cRups.get(r);
			if (rupture.singleStrand)
				numSingleStrandRuptures++;
			uniques.add(rupture.unique);
			for (Jump jump : rupture.getJumpsIterable()) {
				if (jump.fromSection.getSectionId() > jump.toSection.getSectionId())
					jump = jump.reverse();
				jumps.add(jump);
				List<Integer> jumpRups = jumpRupsMap.get(jump);
				if (jumpRups == null) {
					jumpRups = new ArrayList<>();
					jumpRupsMap.put(jump, jumpRups);
				}
				jumpRups.add(r);
				if (sol != null) {
					double rate = sol.getRateForRup(r);
					if (jumpRates.containsKey(jump))
						jumpRates.put(jump, jumpRates.get(jump)+rate);
					else
						jumpRates.put(jump, rate);
				}
			}
		}
		actualConnections = jumps.size();
		this.numSingleStrandRuptures = numSingleStrandRuptures;
		
		if (rupSet.hasModule(PlausibilityConfiguration.class)) {
			PlausibilityConfiguration config = rupSet.getModule(PlausibilityConfiguration.class);
			HashSet<Jump> availableJumps = new HashSet<>();
			for (Jump jump : config.getConnectionStrategy().getAllPossibleJumps()) {
				if (jump.fromSection.getSectionId() > jump.toSection.getSectionId())
					jump = jump.reverse();
				availableJumps.add(jump);
			}
			availableConnections = availableJumps.size();
		} else {
			availableConnections = -1;
		}
		
		scalarRanges = new ArrayList<>();
		scalarValues = new ArrayList<>();
	}
	
	public static class ScalarRange {
		public final HistScalar scalar;
		public final double min, max;
		
		public ScalarRange(HistScalarValues values) {
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			for (double val : values.getValues()) {
				min = Math.min(val, min);
				max = Math.max(val, max);
			}
			this.min = min;
			this.max = max;
			this.scalar = values.getScalar();
		}
	}
	
	public void addScalar(HistScalarValues values) {
		this.scalarRanges.add(new ScalarRange(values));
		this.scalarValues.add(values);
	}

}
