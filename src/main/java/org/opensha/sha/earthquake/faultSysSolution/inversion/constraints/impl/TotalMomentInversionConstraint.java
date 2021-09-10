package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import org.opensha.commons.eq.MagUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Constraint solution moment to equal deformation-model moment. We did not use this in UCERF3 and it is not recommended.
 * 
 * This constraint is not very useful - slip rate constraints will do the job better. If you enable this with anything
 * other than an absolutely miniscule weight, the inversion will likely get stuck in an local minimum, unable to
 * fit other constraints as briefly straying away from the target moment will incur massive penalty.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class TotalMomentInversionConstraint extends InversionConstraint {
	
	private FaultSystemRupSet rupSet;
	private double weight;
	private double totalMomentTarget;

	public TotalMomentInversionConstraint(FaultSystemRupSet rupSet, double weight,
			double totalMomentTarget) {
		this.rupSet = rupSet;
		this.weight = weight;
		this.totalMomentTarget = totalMomentTarget;
	}

	@Override
	public String getShortName() {
		return "TotMoment";
	}

	@Override
	public String getName() {
		return "Total Moment";
	}

	@Override
	public int getNumRows() {
		return 1;
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int numRuptures = rupSet.getNumRuptures();
		for (int rup=0; rup<numRuptures; rup++)  {
			setA(A, startRow, rup, weight * MagUtils.magToMoment(rupSet.getMagForRup(rup)));
			numNonZeroElements++;
		}
		d[startRow] = weight * totalMomentTarget;
		return numNonZeroElements;
	}

}
