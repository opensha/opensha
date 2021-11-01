package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;

import com.google.common.base.Preconditions;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * This constraint enforces a G-R MFD with the specified b-value, but without specifying an a-value. It does so by
 * attempting to force ruptures in each magnitude bin to be G-R relative to the ruptures in the other bins
 * 
 * @author kevin
 *
 */
public class RelativeBValueConstraint extends InversionConstraint {
	
	private transient FaultSystemRupSet rupSet;
	private transient int[] rupMagIndexes;
	private transient int[] rupMagCounts;
	
	/**
	 * a-value inferred from a G-R computed from the rupture set's deformation model moment rate and the given b-value
	 */
	private transient double a_inferred; 
	private double b;
	private double[] magBins;
	private double magDelta;
	private double[] magBinRelStdDevs;
	
	private static final DecimalFormat bDF = new DecimalFormat("0.#");
	
	/**
	 * Relative b-value constraint with the given target b-value and weight. The constraint will be normalized such that
	 * misfits are fractions of a target G-R inferred from the deformation model moment rates.
	 * 
	 * @param rupSet
	 * @param b
	 * @param weight
	 */
	public RelativeBValueConstraint(FaultSystemRupSet rupSet, double b, double weight) {
		this(rupSet, b, weight, ConstraintWeightingType.NORMALIZED, null);
	}
	
	/**
	 * Relative b-value constraint with the given target b-value, weight, and weighting type. If the normalized weighting
	 * type is selected, the constraint will be normalized such that misfits are fractions of a target G-R inferred
	 * from the deformation model moment rates. If the uncertainty-normalized weighting type is selected, then the given
	 * standard deviations will be applied to an inferred target G-R to weight each constraint.
	 * 
	 * @param rupSet
	 * @param b
	 * @param weight
	 * @param weightingType
	 * @param relMagStdDevFunc
	 */
	public RelativeBValueConstraint(FaultSystemRupSet rupSet, double b, double weight,
			ConstraintWeightingType weightingType, DoubleUnaryOperator relMagStdDevFunc) {
		this(rupSet, b, weight, weightingType, relMagStdDevFunc, false);
	}
	
	public RelativeBValueConstraint(FaultSystemRupSet rupSet, double b, double weight,
			ConstraintWeightingType weightingType, DoubleUnaryOperator relMagStdDevFunc, boolean inequality) {
		super(weightingType.applyNamePrefix(getName(b, inequality)),
				weightingType.applyShortNamePrefix(getShortName(b, inequality)),
				weight, inequality, weightingType);
		this.b = b;
		initMagBins(rupSet);
		setRuptureSet(rupSet);
		if (relMagStdDevFunc != null) {
			magBinRelStdDevs = new double[magBins.length];
			for (int m=0; m<magBinRelStdDevs.length; m++)
				magBinRelStdDevs[m] = relMagStdDevFunc.applyAsDouble(magBins[m]);
		} else {
			Preconditions.checkState(weightingType != ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY,
					"Uncertainty-normalization selected but magnitude-dependent uncertainties not supplied");
		}
	}
	
	private void initMagBins(FaultSystemRupSet rupSet) {
		double maxMag = rupSet.getMaxMag();
		double minMag;
		if (rupSet.hasModule(ModSectMinMags.class))
			minMag = StatUtils.min(rupSet.getModule(ModSectMinMags.class).getMinMagForSections());
		else
			minMag = rupSet.getMinMag();
		Preconditions.checkState(maxMag > minMag+0.2,
				"Minimum and maximum magnitude are too close for b-value constraint: %s %s", minMag, maxMag);
		minMag = Math.floor(minMag*10)/10d + 0.05;
		maxMag = Math.ceil(maxMag*10)/10d - 0.05;
		int num = (int)((maxMag-minMag)/0.1)+2;
		Preconditions.checkState(num > 1, "Must have at least 2 MFD bins to constraint relative b-value");
		EvenlyDiscretizedFunc magFunc = new EvenlyDiscretizedFunc(minMag, maxMag, num);
		magBins = new double[num];
		for (int m=0; m<num; m++)
			magBins[m] = magFunc.getX(m);
		magDelta = magFunc.getDelta();
	}

	private static String getShortName(double b, boolean inequality) {
		if (inequality)
			return "G-R,b≥"+bDF.format(b);
		return "G-R,b="+bDF.format(b);
	}

	private static String getName(double b, boolean inequality) {
		if (inequality)
			return "G-R b-value constrant (b≥"+bDF.format(b)+")";
		return "G-R b-value constrant (b="+bDF.format(b)+")";
	}

	@Override
	public int getNumRows() {
		int rows = 0;
		for (int m1=0; m1<magBins.length; m1++)
			for (int m2=m1+1; m2<magBins.length; m2++)
				if (rupMagCounts[m1] > 0 && rupMagCounts[m2] > 0)
					rows++;
		return rows;
	}
	
	private static double incrGR(double a, double b, double m1, double m2) {
		Preconditions.checkState(m2 > m1);
		return cmlGR(a, b, m1) - cmlGR(a, b, m2);
	}
	
	private static double cmlGR(double a, double b, double M) {
		return Math.pow(10, a-b*M);
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long count = 0l;
		
		double halfMagBin = magDelta*0.5;
		
//		double refVal = incrGR(a_inferred, b, magBins[0] - halfMagBin, magBins[0] + halfMagBin);
		
		int row = startRow;
		
		// weigh it such that each bin has the given weight, not each bin-to-bin comparison row
//		double weight = this.weight / (magBins.length-1);
		
		for (int m1=0; m1<magBins.length; m1++) {
			if (rupMagCounts[m1] == 0) {
				System.out.println("Skipping M1="+(float)magBins[m1]+" (no ruptures)");
				continue;
			}
			double val1 = incrGR(a_inferred, b, magBins[m1]-halfMagBin, magBins[m1]+halfMagBin);
			
			System.out.println("Building b-value constraints for ruptures with M1="+(float)magBins[m1]);
			
			// determine a weight such that misfits from each bin should we weighted equally
			// (even though rates will be smaller for larger mags)
//			double myWeight = refVal/val1;
//			System.out.println("\tWeight: rel="+(float)myWeight+",\ttotal="+(float)(myWeight*weight));
//			myWeight *= weight;
			
			for (int m2=m1+1; m2<magBins.length; m2++) {
				if (rupMagCounts[m2] == 0) {
					System.out.println("\tSkipping M2="+(float)magBins[m2]+" (no ruptures)");
					continue;
				}
				double val2 = incrGR(a_inferred, b, magBins[m2]-halfMagBin, magBins[m2]+halfMagBin);
				
				// ruptures in this row should have a final rate equal to this times the rate of
				// ruptures in the first (comparison) bin (regardless of a-value)
				double ratio = val1/val2;
				
				// values here are all scaled be relative to the lower magnitude bin's expected rate
				
				double aScalar;
				switch (weightingType) {
				case UNNORMALIZED:
					aScalar = 1d;
					break;
				case NORMALIZED:
					aScalar = 1d/val1;
					break;
				case NORMALIZED_BY_UNCERTAINTY:
					// both in val1 units (first bin)
					double uncert = magBinRelStdDevs[m1]*val1 + magBinRelStdDevs[m2]*val1;
					aScalar = 1d/uncert;
					break;

				default:
					throw new IllegalStateException();
				}
				
				System.out.println("\tTarget ratio relative to M2="+(float)magBins[m2]+": "+(float)ratio+"\taScalar="+(float)aScalar);
				
				double myWeight = weight*aScalar;
				
				for (int r=0; r<rupMagIndexes.length; r++) {
					int magIndex = rupMagIndexes[r];
					if (magIndex < 0)
						continue;
					
					if (magIndex == m2) {
						// larger mag should be positive so that things work in inequality mode
						// if the value from the larger magnitude is smaller than the target, the misfit will be negative
						// and ignored in inequality mode
						setA(A, row, r, myWeight*ratio);
						count++;
					} else if (magIndex == m1) {
						setA(A, row, r, -myWeight);
						count++;
					}
				}
				d[row] = 0d;
				
				row++;
			}
		}
		
		return count;
	}

	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		if (rupSet != this.rupSet) {
			double[] rupMags = rupSet.getMagForAllRups();
			rupMagIndexes = new int[rupMags.length];
			
			EvenlyDiscretizedFunc magFunc = new EvenlyDiscretizedFunc(magBins[0], magBins[magBins.length-1], magBins.length);
			for (int i=0; i<magBins.length; i++)
				Preconditions.checkState((float)magBins[i] == (float)magFunc.getX(i));
			double globalMin = magFunc.getMinX() - 0.5*magFunc.getDelta();
			double globalMax = magFunc.getMaxX() + 0.5*magFunc.getDelta();
			rupMagCounts = new int[magFunc.size()];
			for (int r=0; r<rupMags.length; r++) {
				if ((float)rupMags[r] < (float)globalMin  || (float)rupMags[r] > (float)globalMax) {
					rupMagIndexes[r] = -1;
				} else {
					rupMagIndexes[r] = magFunc.getClosestXIndex(rupMags[r]);
					rupMagCounts[rupMagIndexes[r]]++;
				}
			}
			
			GutenbergRichterMagFreqDist inferredGR = Inversions.inferTargetGRFromSlipRates(rupSet, b);
			EvenlyDiscretizedFunc cml = inferredGR.getCumRateDistWithOffset();
			double totRate = cml.getY(0);
			double m = cml.getX(0);
//			System.out.println("Rate of M>"+(float)m+" = "+(float)totRate);
			a_inferred = Math.log10(totRate) + b*m;
			
			this.rupSet = rupSet;
		}
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("/home/kevin/markdown/inversions/"
				+ "fm3_1_u3ref_uniform_reproduce_ucerf3.zip"));
		
		double b = 1d;
		
		RelativeBValueConstraint constr = new RelativeBValueConstraint(rupSet, b, 1d);
//		System.out.println(constr.magFunc);
		System.out.println("Func has "+constr.magBins.length+" bins");
		System.out.println("Rows: "+constr.getNumRows());
		System.out.print("Bins:\t");
		for (double mag : constr.magBins)
			System.out.print((float)mag+" ");
		System.out.println();
		if (constr.magBinRelStdDevs != null) {
			System.out.print("Uncerts:\t");
			for (double uncert : constr.magBinRelStdDevs)
				System.out.print((float)uncert+" ");
			System.out.println();
		}
		System.out.println("Inferred GR a="+(float)constr.a_inferred);
		
//		GutenbergRichterMagFreqDist inferredGR = Inversions.inferTargetGRFromSlipRates(rupSet, b);
//		EvenlyDiscretizedFunc cml = inferredGR.getCumRateDistWithOffset();
//		double totRate = cml.getY(0);
//		double m = cml.getX(0);
//		System.out.println("Rate of M>"+(float)m+" = "+(float)totRate);
//		double a = Math.log10(totRate) + b*m;
//		System.out.println("Inferred GR has a="+(float)a);
//		System.out.println("Calc GR: "+(float)cmlGR(a, b, m));
//		System.out.println("1st bin GR: "+(float)incrGR(a, b, m, m+0.1));
//		System.out.println("Inferred GR 1st bin: "+(float)inferredGR.getY(0));
	}

}
