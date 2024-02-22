package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.text.DecimalFormat;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import com.google.common.base.Preconditions;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Constrain solution total rate. Misfits are in rate space (not normalized).
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class TotalRateInversionConstraint extends InversionConstraint {

	private transient FaultSystemRupSet rupSet;
	private double totalRateTarget;
	private double minMag;
	private double totalRateStdDev;

	public TotalRateInversionConstraint(double weight, double totalRateTarget) {
		this(weight, totalRateTarget, ConstraintWeightingType.UNNORMALIZED, 0d);
	}

	public TotalRateInversionConstraint(double weight, double totalRateTarget, ConstraintWeightingType weightingType,
			double totalRateStdDev) {
		this(weight, totalRateTarget, null, 0d, weightingType, totalRateStdDev, false);
	}

	public TotalRateInversionConstraint(double weight, double totalRateTarget, FaultSystemRupSet rupSet, double minMag,
			 ConstraintWeightingType weightingType, double totalRateStdDev, boolean inequality) {
		super(weightingType.applyNamePrefix(name(minMag)), weightingType.applyShortNamePrefix(shortName(minMag)),
				weight, inequality, weightingType);
		this.totalRateTarget = totalRateTarget;
		this.rupSet = rupSet;
		this.minMag = minMag;
		this.totalRateStdDev = totalRateStdDev;
	}
	
	private static String name(double minMag) {
		if (minMag > 0d)
			return "M>"+new DecimalFormat("0.#").format(minMag)+" Rate";
		return "Total Rate";
	}
	
	private static String shortName(double minMag) {
		if (minMag > 0d)
			return "M"+new DecimalFormat("0.#").format(minMag)+"Rate";
		return "TotRate";
	}

	@Override
	public int getNumRows() {
		return 1;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int numRuptures = A.columns();
		if (rupSet != null)
			Preconditions.checkState(rupSet.getNumRuptures() == numRuptures);
		if (minMag > 0d)
			Preconditions.checkNotNull(rupSet, "Minimum magnitude supplied but not rupture set");
		double scale = weightingType.getA_Scalar(totalRateTarget, totalRateStdDev);
		for (int rup=0; rup<numRuptures; rup++)  {
			if (minMag > 0d && rupSet.getMagForRup(rup) < minMag)
				continue;
			setA(A, startRow, rup, weight*scale);
			numNonZeroElements++;
		}
		d[startRow] = weight * weightingType.getD(totalRateTarget, totalRateStdDev);
		return numNonZeroElements;
	}

	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
	}

}
