package org.opensha.commons.data.uncertainty;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(UncertainBoundedIncrMagFreqDist.Adapter.class)
public class UncertainBoundedIncrMagFreqDist extends UncertainIncrMagFreqDist implements UncertainBoundedDiscretizedFunc {

	private IncrementalMagFreqDist lowerBound;
	private IncrementalMagFreqDist upperBound;
	private UncertaintyBoundType boundType;
	
	private UncertainBoundedIncrMagFreqDist(double minX, double maxX, int size) {
		super(new IncrementalMagFreqDist(minX, maxX, size), null);
	}

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
			if (boundType == null)
				return null;
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
	
	@Override
	public UncertainIncrMagFreqDist deepClone() {
		if (this.stdDevs == null)
			return new UncertainBoundedIncrMagFreqDist(this, lowerBound.deepClone(), upperBound.deepClone(), boundType);
		return new UncertainBoundedIncrMagFreqDist(this, lowerBound.deepClone(), upperBound.deepClone(), boundType, stdDevs.deepClone());
	}

	@Override
	public void scale(double val) {
		super.scale(val);
		lowerBound.scale(val);
		upperBound.scale(val);
	}

	public static class Adapter extends GenericAdapter<UncertainBoundedIncrMagFreqDist> {

		@Override
		protected UncertainBoundedIncrMagFreqDist instance(Double minX, Double maxX, Integer size) {
			Preconditions.checkNotNull(minX, "minX must be supplied before values to deserialize EvenlyDiscretizedFunc");
			Preconditions.checkNotNull(maxX, "maxX must be supplied before values to deserialize EvenlyDiscretizedFunc");
			Preconditions.checkNotNull(size, "size must be supplied before values to deserialize EvenlyDiscretizedFunc");
			return new UncertainBoundedIncrMagFreqDist(minX, maxX, size);
		}

		EvenlyDiscretizedFunc.Adapter funcAdapter = new EvenlyDiscretizedFunc.Adapter();
		IncrementalMagFreqDist.Adapter mfdAdapter = new IncrementalMagFreqDist.Adapter();

		@Override
		protected void serializeExtras(JsonWriter out, UncertainBoundedIncrMagFreqDist xy) throws IOException {
			super.serializeExtras(out, xy);
			
			if (xy.stdDevs != null) {
				out.name("stdDevs");
				writeDoubleArray(out, xy.stdDevs);
			}
			
			out.name("lowerBounds");
			writeDoubleArray(out, xy.lowerBound);
			
			out.name("upperBounds");
			writeDoubleArray(out, xy.upperBound);
			
			if (xy.boundType != null)
				out.name("boundType").value(xy.boundType.name());
		}
		
		static void writeDoubleArray(JsonWriter out, DiscretizedFunc func) throws IOException {
			out.beginArray();
			for (Point2D pt : func)
				out.value(pt.getY());
			out.endArray();
		}
		
		static List<Double> readDoubleArray(JsonReader in) throws IOException {
			in.beginArray();
			List<Double> ret = new ArrayList<>();
			while (in.hasNext())
				ret.add(in.nextDouble());
			in.endArray();
			return ret;
		}
		
		private static IncrementalMagFreqDist buildIncrFunc(IncrementalMagFreqDist xVals, List<Double> yVals) {
			Preconditions.checkState(xVals.size() == yVals.size());
			IncrementalMagFreqDist ret = new IncrementalMagFreqDist(xVals.getMinX(), xVals.size(), xVals.getDelta());
			for (int i=0; i<yVals.size(); i++)
				ret.set(i, yVals.get(i));
			return ret;
		}
		
		private static EvenlyDiscretizedFunc buildFunc(EvenlyDiscretizedFunc xVals, List<Double> yVals) {
			Preconditions.checkState(xVals.size() == yVals.size());
			EvenlyDiscretizedFunc ret = new EvenlyDiscretizedFunc(xVals.getMinX(), xVals.size(), xVals.getDelta());
			for (int i=0; i<yVals.size(); i++)
				ret.set(i, yVals.get(i));
			return ret;
		}

		@Override
		protected Consumer<UncertainBoundedIncrMagFreqDist> deserializeExtra(JsonReader in, String name) throws IOException {
			if (name.equals("stdDevs")) {
				if (in.peek() == JsonToken.NULL)
					return null;
				if (in.peek() == JsonToken.BEGIN_OBJECT) {
					// deprecated
					EvenlyDiscretizedFunc stdDevs = funcAdapter.read(in);
					return new Consumer<UncertainBoundedIncrMagFreqDist>() {

						@Override
						public void accept(UncertainBoundedIncrMagFreqDist t) {
							Preconditions.checkState(t.size() == stdDevs.size());
							t.stdDevs = stdDevs;
						}
					};
				} else {
					List<Double> stdDevs = readDoubleArray(in);
					return new Consumer<UncertainBoundedIncrMagFreqDist>() {

						@Override
						public void accept(UncertainBoundedIncrMagFreqDist t) {
							t.stdDevs = buildFunc(t, stdDevs);
						}
					};
				}
			}
			if (name.equals("lowerFunc")) {
				// deprecated
				IncrementalMagFreqDist lowerFunc = mfdAdapter.read(in);
				return new Consumer<UncertainBoundedIncrMagFreqDist>() {

					@Override
					public void accept(UncertainBoundedIncrMagFreqDist t) {
						Preconditions.checkState(t.size() == lowerFunc.size());
						t.lowerBound = lowerFunc;
					}
				};
			}
			if (name.equals("upperFunc")) {
				// deprecated
				IncrementalMagFreqDist upperFunc = mfdAdapter.read(in);
				return new Consumer<UncertainBoundedIncrMagFreqDist>() {

					@Override
					public void accept(UncertainBoundedIncrMagFreqDist t) {
						Preconditions.checkState(t.size() == upperFunc.size());
						t.upperBound = upperFunc;
					}
				};
			}
			if (name.equals("lowerBounds")) {
				List<Double> lowers = readDoubleArray(in);
				return new Consumer<UncertainBoundedIncrMagFreqDist>() {

					@Override
					public void accept(UncertainBoundedIncrMagFreqDist t) {
						t.lowerBound = buildIncrFunc(t, lowers);
					}
				};
			}
			if (name.equals("upperBounds")) {
				List<Double> uppers = readDoubleArray(in);
				return new Consumer<UncertainBoundedIncrMagFreqDist>() {

					@Override
					public void accept(UncertainBoundedIncrMagFreqDist t) {
						t.upperBound = buildIncrFunc(t, uppers);
					}
				};
			}
			if (name.equals("boundType")) {
				if (in.peek() == JsonToken.NULL)
					return null;
				UncertaintyBoundType boundType = UncertaintyBoundType.valueOf(in.nextString());
				return new Consumer<UncertainBoundedIncrMagFreqDist>() {

					@Override
					public void accept(UncertainBoundedIncrMagFreqDist t) {
						t.boundType = boundType;
					}
				};
			}
			return super.deserializeExtra(in, name);
		}

		@Override
		protected Class<UncertainBoundedIncrMagFreqDist> getType() {
			return UncertainBoundedIncrMagFreqDist.class;
		}
		
	}

}
