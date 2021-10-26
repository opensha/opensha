package org.opensha.commons.data.uncertainty;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class UncertainBoundedIncrMagFreqDist extends UncertainIncrMagFreqDist implements UncertainBoundedDiscretizedFunc {

	private IncrementalMagFreqDist lowerBound;
	private IncrementalMagFreqDist upperBound;
	private UncertaintyBoundType boundType;

	public UncertainBoundedIncrMagFreqDist(IncrementalMagFreqDist mfd, IncrementalMagFreqDist lowerBound,
			IncrementalMagFreqDist upperBound, UncertaintyBoundType boundType) throws InvalidRangeException {
		this(mfd, lowerBound, upperBound, boundType, null);
	}
	
	public UncertainBoundedIncrMagFreqDist(IncrementalMagFreqDist mfd, IncrementalMagFreqDist lowerBound,
			IncrementalMagFreqDist upperBound, UncertaintyBoundType boundType, EvenlyDiscretizedFunc stdDevs) throws InvalidRangeException {
		super(mfd, stdDevs);
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		this.boundType = boundType;
		
		Preconditions.checkArgument(mfd.size() == lowerBound.size(), "Lower func not same length as mean");
		Preconditions.checkArgument(mfd.size() == upperBound.size(), "Upper func not same length as mean");
		
		for (int i=0; i<size(); i++) {
			double x = mfd.getX(i);
			double y = mfd.getY(i);
			
			double lowerY = lowerBound.getY(i);
			double upperY = upperBound.getY(i);
			
			Preconditions.checkArgument((float)x == (float)lowerBound.getX(i), "X inconsistent in lower func");
			Preconditions.checkArgument((float)x == (float)upperBound.getX(i), "X inconsistent in lower func");
			if (!Double.isNaN(y) && !Double.isNaN(lowerY) && !Double.isNaN(upperY)) {
				Preconditions.checkArgument((float)y >= (float)lowerY, "Lower func must be <= mean func: %s ! <= %s, x=%s", lowerY, y, x);
				Preconditions.checkArgument((float)y <= (float)upperY, "Upper func must be >= mean func: %s ! >= %s, x=%s", upperY, y, x);				
			}
		}
	}

	@Override
	public EvenlyDiscretizedFunc getStdDevs() {
		if (stdDevs == null) {
			Preconditions.checkState(boundType != null,
					"Standard deviations not supplied and can't estimate as bound type not specified");
			synchronized (this) {
				if (stdDevs == null) {
					EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(getMinX(), getMaxX(), size());
					for (int i=0; i<size(); i++)
						stdDevs.set(i, boundType.estimateStdDev(getY(i), getLowerY(i), getUpperY(i)));
					this.stdDevs = stdDevs;
				}
			}
		}
		return stdDevs;
	}

	@Override
	public UncertaintyBoundType getBoundType() {
		return boundType;
	}

	@Override
	public IncrementalMagFreqDist getLower() {
		return lowerBound;
	}

	@Override
	public IncrementalMagFreqDist getUpper() {
		return upperBound;
	}

}
