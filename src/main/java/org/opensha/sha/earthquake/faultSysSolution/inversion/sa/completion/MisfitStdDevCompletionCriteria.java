package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;

public class MisfitStdDevCompletionCriteria implements CompletionCriteria {
	
	private ConstraintWeightingType weightingType;
	private double targetStdDev;

	public MisfitStdDevCompletionCriteria(double targetStdDev) {
		this(null, targetStdDev);
	}

	public MisfitStdDevCompletionCriteria(ConstraintWeightingType weightingType, double targetStdDev) {
		this.weightingType = weightingType;
		this.targetStdDev = targetStdDev;
	}
	
	private static boolean D = true;

	@Override
	public boolean isSatisfied(StopWatch watch, long iter, double[] energy, long numPerturbsKept, int numNonZero,
			double[] misfits, double[] misfits_ineq, List<ConstraintRange> constraintRanges) {
		if (D) System.out.println("Evaluating Misfit Std. Dev. criteria, target="
			+(float)targetStdDev+", type="+weightingType);
		boolean pass = true;
		for (int c=0; c<constraintRanges.size(); c++) {
			ConstraintRange range = constraintRanges.get(c);
			if (this.weightingType != null && range.weightingType != weightingType)
				continue;
			
			// sd = sqrt((1/N)*sum((x_i-u)^2))
			// here, xi are misfits, and u=0, so
			// sd = sqrt((1/N)*sum(x_i^2))
			// total energy for a constraint is just the sum of those squared misfits:
			// E = sum((x_i)^2)
			// so if follows that:
			// sd = sqrt((1/N)*E)
			
			double e = energy[c+4];
			if (range.weight != 1d)
				// remove weighting
				e /= range.weight*range.weight;
			double stdDev = Math.sqrt(e/(range.endRow-range.startRow));
			if (D) System.out.println("\t"+range.shortName+"\tE="+(float)energy[c+4]
					+"\tunWtE="+(float)e+"\tstdDev="+(float)stdDev);
			if (stdDev > targetStdDev) {
				if (!D)
					return false;
				pass = false;
			}
		}
		return pass;
	}

	@Override
	public String toString() {
		return "MisfitStdDev="+(float)targetStdDev;
	}

}
