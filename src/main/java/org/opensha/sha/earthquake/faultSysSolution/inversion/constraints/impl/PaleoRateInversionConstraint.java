package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;

public class PaleoRateInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "Paleoseismic Event Rate";
	public static final String SHORT_NAME = "PaleoRate";
	
	private FaultSystemRupSet rupSet;
	private double weight;
	private List<PaleoRateConstraint> paleoRateConstraints;
	private PaleoProbabilityModel paleoProbModel;

	public PaleoRateInversionConstraint(FaultSystemRupSet rupSet, double weight,
			List<PaleoRateConstraint> paleoRateConstraints, PaleoProbabilityModel paleoProbModel) {
		this.rupSet = rupSet;
		this.weight = weight;
		this.paleoRateConstraints = paleoRateConstraints;
		this.paleoProbModel = paleoProbModel;
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public int getNumRows() {
		// one for each constraint
		return paleoRateConstraints.size();
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		for (int i=0; i<paleoRateConstraints.size(); i++) {
			PaleoRateConstraint constraint = paleoRateConstraints.get(i);
			int row = startRow + i;
			d[row] = weight * constraint.getMeanRate() / constraint.getStdDevOfMeanRate();
			List<Integer> rupsForSect = rupSet.getRupturesForSection(constraint.getSectionIndex());
			for (int rupIndex=0; rupIndex<rupsForSect.size(); rupIndex++) {
				int rup = rupsForSect.get(rupIndex);
				double probPaleoVisible = paleoProbModel.getProbPaleoVisible(
						rupSet, rup, constraint.getSectionIndex());	
				setA(A, row, rup, weight * probPaleoVisible / constraint.getStdDevOfMeanRate());
				numNonZeroElements++;			
			}
		}
		return numNonZeroElements;
	}

}
