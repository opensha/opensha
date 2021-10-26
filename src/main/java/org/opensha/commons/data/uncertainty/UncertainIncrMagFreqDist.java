package org.opensha.commons.data.uncertainty;

import java.util.function.DoubleUnaryOperator;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class UncertainIncrMagFreqDist extends IncrementalMagFreqDist implements UncertainDiscretizedFunc {

	protected EvenlyDiscretizedFunc stdDevs;
	
	/**
	 * Use this to supply a functional form of relative standard deviations as a function of magnitude. Those
	 * relative standard deviations will be multiplied by the rate in each bin to generate final (absolute) standard
	 * deviations
	 * 
	 * @param mfd
	 * @param relStdDev
	 * @return
	 */
	public static UncertainIncrMagFreqDist relStdDev(IncrementalMagFreqDist mfd, DoubleUnaryOperator relStdDevFunc) {
		EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
		
		stdDevs.setYofX(relStdDevFunc);
		
		return new UncertainIncrMagFreqDist(mfd, stdDevs);
	}
	
	/**
	 * Sets standard deviations for each magnitude bin to the given value times the rate in that bin
	 * 
	 * @param mfd
	 * @param relStdDev
	 * @return
	 */
	public static UncertainIncrMagFreqDist constantRelStdDev(IncrementalMagFreqDist mfd, double relStdDev) {
		EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
		
		stdDevs.setYofX((M,R)->relStdDev*R);
		
		return new UncertainIncrMagFreqDist(mfd, stdDevs);
	}

	public UncertainIncrMagFreqDist(IncrementalMagFreqDist mfd, EvenlyDiscretizedFunc stdDevs) throws InvalidRangeException {
		super(mfd.getMinX(), mfd.getMaxX(), mfd.size());
		this.stdDevs = stdDevs;
		if (stdDevs != null)
			Preconditions.checkState(mfd.size() == stdDevs.size());
		for (int i=0; i<size(); i++)
			set(i, mfd.getY(i));
	}

	@Override
	public EvenlyDiscretizedFunc getStdDevs() {
		return stdDevs;
	}

	@Override
	public UncertainBoundedIncrMagFreqDist estimateBounds(UncertaintyBoundType boundType) {
		IncrementalMagFreqDist lowerBounds = new IncrementalMagFreqDist(getMinX(), getMaxX(), size());
		IncrementalMagFreqDist upperBounds = new IncrementalMagFreqDist(getMinX(), getMaxX(), size());
		
		for (int i=0; i<size(); i++) {
			BoundedUncertainty bounds = boundType.estimate(getY(i), getStdDev(i));
			lowerBounds.set(i, bounds.lowerBound);
			upperBounds.set(i, bounds.upperBound);
		}
		return new UncertainBoundedIncrMagFreqDist(this, lowerBounds, upperBounds, boundType, stdDevs);
	}

}
