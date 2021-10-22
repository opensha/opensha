package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.utils.paleoRateConstraints.U3PaleoRateConstraint;

/**
 * Constraint to match paleoseismic event rates at subsections, also taking into account
 * the probability of a rupture being seen at a paleoseismic trench site.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class PaleoRateInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "Paleoseismic Event Rate";
	public static final String SHORT_NAME = "PaleoRate";
	
	private transient FaultSystemRupSet rupSet;
	private List<? extends SectMappedUncertainDataConstraint> paleoRateConstraints;
	private PaleoProbabilityModel paleoProbModel;

	public PaleoRateInversionConstraint(FaultSystemRupSet rupSet, double weight,
			List<? extends SectMappedUncertainDataConstraint> paleoRateConstraints, PaleoProbabilityModel paleoProbModel) {
		super(NAME, SHORT_NAME, weight, false);
		this.rupSet = rupSet;
		this.paleoRateConstraints = paleoRateConstraints;
		this.paleoProbModel = paleoProbModel;
	}

	@Override
	public int getNumRows() {
		// one for each constraint
		return paleoRateConstraints.size();
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		for (int i=0; i<paleoRateConstraints.size(); i++) {
			SectMappedUncertainDataConstraint constraint = paleoRateConstraints.get(i);
			int row = startRow + i;
			d[row] = weight * constraint.bestEstimate / constraint.getPreferredStdDev();
			List<Integer> rupsForSect = rupSet.getRupturesForSection(constraint.sectionIndex);
			for (int rupIndex=0; rupIndex<rupsForSect.size(); rupIndex++) {
				int rup = rupsForSect.get(rupIndex);
				double probPaleoVisible = paleoProbModel.getProbPaleoVisible(
						rupSet, rup, constraint.sectionIndex);	
				setA(A, row, rup, weight * probPaleoVisible / constraint.getPreferredStdDev());
				numNonZeroElements++;			
			}
		}
		return numNonZeroElements;
	}

	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
	}

}
