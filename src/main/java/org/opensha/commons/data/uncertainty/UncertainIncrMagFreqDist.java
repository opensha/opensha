package org.opensha.commons.data.uncertainty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(UncertainIncrMagFreqDist.Adapter.class)
public class UncertainIncrMagFreqDist extends IncrementalMagFreqDist implements UncertainDiscretizedFunc {

	protected EvenlyDiscretizedFunc stdDevs;
	
	/**
	 * Use this to supply a functional form of relative standard deviations as a function of magnitude. Those
	 * relative standard deviations will be multiplied by the rate in each bin to generate final (absolute) standard
	 * deviations
	 * 
	 * @param mfd
	 * @param relStdDev
	 * @return uncertain MFD with the standard deviations set
	 */
	public static UncertainIncrMagFreqDist relStdDev(IncrementalMagFreqDist mfd, DoubleUnaryOperator relStdDevFunc) {
		EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
		
		stdDevs.setYofX(relStdDevFunc);
		for (int i=0; i<stdDevs.size(); i++)
			stdDevs.set(i, stdDevs.getY(i)*mfd.getY(i));
		
		return new UncertainIncrMagFreqDist(mfd, stdDevs);
	}
	
	/**
	 * Sets standard deviations for each magnitude bin to the given value times the rate in that bin
	 * 
	 * @param mfd
	 * @param relStdDev
	 * @return uncertain MFD with the standard deviations set
	 */
	public static UncertainIncrMagFreqDist constantRelStdDev(IncrementalMagFreqDist mfd, double relStdDev) {
		EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
		
		stdDevs.setYofX(M->relStdDev);
		for (int i=0; i<stdDevs.size(); i++)
			stdDevs.set(i, stdDevs.getY(i)*mfd.getY(i));
		
		return new UncertainIncrMagFreqDist(mfd, stdDevs);
	}
	
	/**
	 * Creates a new {@link UncertainIncrMagFreqDist} from the given MFD with a mapping from magnitude to absolute
	 * (i.e., not relative to the rate) standard deviation.
	 * 
	 * @param mfd
	 * @param absStdDevFunc
	 * @return uncertain MFD with the standard deviations set
	 */
	public static UncertainIncrMagFreqDist absStdDev(IncrementalMagFreqDist mfd, DoubleUnaryOperator absStdDevFunc) {
		EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
		
		stdDevs.setYofX(absStdDevFunc);
		
		return new UncertainIncrMagFreqDist(mfd, stdDevs);
	}
	
	/**
	 * Creates a new {@link UncertainIncrMagFreqDist} from the given MFD with a mapping from magnitude and rate to
	 * standard deviation.
	 * 
	 * @param mfd
	 * @param absStdDevFunc
	 * @return uncertain MFD with the standard deviations set
	 */
	public static UncertainIncrMagFreqDist absStdDev(IncrementalMagFreqDist mfd, DoubleBinaryOperator absStdDevFunc) {
		EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
		
		stdDevs.setYofX(absStdDevFunc);
		
		return new UncertainIncrMagFreqDist(mfd, stdDevs);
	}

	public UncertainIncrMagFreqDist(IncrementalMagFreqDist mfd, EvenlyDiscretizedFunc stdDevs) throws InvalidRangeException {
		super(mfd.getMinX(), mfd.getMaxX(), mfd.size());
		setRegion(mfd.getRegion());
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

	public static class Adapter extends GenericAdapter<UncertainIncrMagFreqDist> {

		@Override
		protected UncertainIncrMagFreqDist instance(Double minX, Double maxX, Integer size) {
			Preconditions.checkNotNull(minX, "minX must be supplied before values to deserialize EvenlyDiscretizedFunc");
			Preconditions.checkNotNull(maxX, "maxX must be supplied before values to deserialize EvenlyDiscretizedFunc");
			Preconditions.checkNotNull(size, "size must be supplied before values to deserialize EvenlyDiscretizedFunc");
			IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(minX, maxX, size);
			return new UncertainIncrMagFreqDist(mfd, null);
		}
		
		EvenlyDiscretizedFunc.Adapter funcAdapter = new EvenlyDiscretizedFunc.Adapter();

		@Override
		protected void serializeExtras(JsonWriter out, UncertainIncrMagFreqDist xy) throws IOException {
			super.serializeExtras(out, xy);
			
			out.name("stdDevs");
			funcAdapter.write(out, xy.stdDevs);
		}

		@Override
		protected Consumer<UncertainIncrMagFreqDist> deserializeExtra(JsonReader in, String name) throws IOException {
			if (name.equals("stdDevs")) {
				EvenlyDiscretizedFunc stdDevs = funcAdapter.read(in);
				return new Consumer<UncertainIncrMagFreqDist>() {

					@Override
					public void accept(UncertainIncrMagFreqDist t) {
						Preconditions.checkState(t.size() == stdDevs.size());
						t.stdDevs = stdDevs;
					}
				};
			}
			return super.deserializeExtra(in, name);
		}

		@Override
		protected Class<UncertainIncrMagFreqDist> getType() {
			return UncertainIncrMagFreqDist.class;
		}

	}
	
	

}
