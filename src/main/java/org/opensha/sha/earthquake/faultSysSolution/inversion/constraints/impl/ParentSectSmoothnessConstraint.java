package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.faultSurface.FaultSection;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * This encourages constant supra-seismogenic event rates within a parent section, by constraining each section to have
 * the same participation rate as the average of all subsections on the same parent section. If the slipRateAdjusted
 * feature is enabled, then sections within a parent section with different slip rates will be less constrained to match
 * each other.
 * 
 * @author kevin
 *
 */
public class ParentSectSmoothnessConstraint extends InversionConstraint {

	private transient FaultSystemRupSet rupSet;
	private boolean slipRateAdjusted;

	public ParentSectSmoothnessConstraint(FaultSystemRupSet rupSet, double weight, boolean slipRateAdjusted) {
		super("Parent Section Smoothness", "ParentSmooth", weight, false, ConstraintWeightingType.UNNORMALIZED);
		this.rupSet = rupSet;
		this.slipRateAdjusted = slipRateAdjusted;
	}

	@Override
	public int getNumRows() {
		// one for each sub-section
		return rupSet.getNumSections();
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		Map<Integer, List<FaultSection>> parentSectsMap = rupSet.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S->S.getParentSectionId()));
		
		SectSlipRates slipRates = slipRateAdjusted ? rupSet.getSectSlipRates() : null;
		
		long numNonZero = 0l;
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			int row = startRow+sect.getSectionId();
			
			List<FaultSection> parentSects = parentSectsMap.get(sect.getParentSectionId());
			
			Map<Integer, Double> rupAVals = new HashMap<>();
			for (int rup : rupSet.getRupturesForSection(sect.getSectionId()))
				add(rupAVals, rup, weight);
			
			List<Double> relWeights = new ArrayList<>();
			double sumRelWeights = 0d;
			for (FaultSection oSect : parentSects) {
				double relWeight = 1d;
				if (slipRateAdjusted) {
					double slip1 = slipRates.getSlipRate(sect.getSectionId());
					double slip2 = slipRates.getSlipRate(oSect.getSectionId());
					if (slip1 != slip2)
						relWeight *= Math.min(slip1, slip2)/Math.max(slip1, slip2);
				}
				relWeights.add(relWeight);
				sumRelWeights += relWeight;
			}
			for (int i=0; i<parentSects.size(); i++) {
				FaultSection oSect = parentSects.get(i);
				double myWeight = weight*relWeights.get(i)/sumRelWeights;
				for (int rup : rupSet.getRupturesForSection(oSect.getSectionId()))
					add(rupAVals, rup, -myWeight);
			}
			for (int rup : rupAVals.keySet()) {
				double val = rupAVals.get(rup);
				if (val != 0d) {
					setA(A, row, rup, val);
					numNonZero++;
				}
			}
		}
		
		return numNonZero;
	}
	
	private static void add(Map<Integer, Double> vals, Integer key, double val) {
		Double prev = vals.get(key);
		if (prev != null)
			val += prev;
		vals.put(key, val);
	}

	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
	}

}
