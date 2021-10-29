package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Constrains the summed rate across a set of ruptures to equal a given total rate.
 * We used this in UCERF3 to constrain Parkfield M6's to have a 25 year recurrence interval,
 * but it could be applied elsewhere as well.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class ParkfieldInversionConstraint extends InversionConstraint {
	
	private double targetRate;
	private List<Integer> parkfieldRups;
	
	private double targetWeightStdDev;

	public ParkfieldInversionConstraint(double weight, double targetRate,
			List<Integer> parkfieldRups) {
		this(weight, targetRate, parkfieldRups, ConstraintWeightingType.UNNORMALIZED, 0d);
	}

	public ParkfieldInversionConstraint(double weight, double targetRate,
			List<Integer> parkfieldRups, ConstraintWeightingType weightingType, double targetWeightStdDev) {
		super(weightingType.applyNamePrefix("Parkfield"), weightingType.applyShortNamePrefix("Parkfield"),
				weight, false, weightingType);
		this.targetRate = targetRate;
		this.parkfieldRups = parkfieldRups;
		this.weightingType = weightingType;
		this.targetWeightStdDev = targetWeightStdDev;
	}

	@Override
	public int getNumRows() {
		return 1;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		
		double scalar = weightingType.getA_Scalar(targetRate, targetWeightStdDev);
		double target = weightingType.getD(targetRate, targetWeightStdDev);
		for (int r=0; r<parkfieldRups.size(); r++)  {
			int rup = parkfieldRups.get(r);
			setA(A, startRow, rup, weight*scalar);
			numNonZeroElements++;
		}
		d[startRow] = weight * target;
		return numNonZeroElements;
	}

}
