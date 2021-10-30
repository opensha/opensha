package org.opensha.commons.data.uncertainty;

import java.io.IOException;
import java.util.function.Consumer;

import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.UnmodifiableDiscrFunc;

import com.google.common.base.Preconditions;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Represents an uncertain discretized function which has both an upper and lower bound. Can be used in
 * conjunction with PlotLineType.SHADED_UNCERTAIN to show shaded uncertainty bounds, or plotted normally.
 * 
 * @author kevin
 *
 */
public class UncertainArbDiscFunc extends UnmodifiableDiscrFunc implements UncertainBoundedDiscretizedFunc {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private UnmodifiableDiscrFunc lowerFunc;
	private UnmodifiableDiscrFunc upperFunc;

	private UncertaintyBoundType boundType;
	private UnmodifiableDiscrFunc stdDevs;
	
	// used for deserialization
	private UncertainArbDiscFunc() {
		super(new ArbitrarilyDiscretizedFunc());
	}
	
	public UncertainArbDiscFunc(DiscretizedFunc meanFunc, DiscretizedFunc lowerFunc, DiscretizedFunc upperFunc) {
		this(meanFunc, lowerFunc, upperFunc, null);
	}
	
	public UncertainArbDiscFunc(DiscretizedFunc meanFunc, DiscretizedFunc lowerFunc, DiscretizedFunc upperFunc,
			UncertaintyBoundType boundType) {
		this(meanFunc, lowerFunc, upperFunc, boundType, null);
	}
	
	public UncertainArbDiscFunc(DiscretizedFunc meanFunc, DiscretizedFunc lowerFunc, DiscretizedFunc upperFunc,
			UncertaintyBoundType boundType, DiscretizedFunc stdDevs) {
		super(meanFunc);
		this.lowerFunc = new UnmodifiableDiscrFunc(lowerFunc);
		this.upperFunc = new UnmodifiableDiscrFunc(upperFunc);
		this.boundType = boundType;
		this.stdDevs = stdDevs == null ? null : new UnmodifiableDiscrFunc(stdDevs);
		
		Preconditions.checkArgument(meanFunc.size() == lowerFunc.size(), "Lower func not same length as mean");
		Preconditions.checkArgument(meanFunc.size() == upperFunc.size(), "Upper func not same length as mean");
		
		for (int i=0; i<size(); i++) {
			double x = meanFunc.getX(i);
			double y = meanFunc.getY(i);
			
			double lowerY = lowerFunc.getY(i);
			double upperY = upperFunc.getY(i);
			
			Preconditions.checkArgument((float)x == (float)lowerFunc.getX(i), "X inconsistent in lower func");
			Preconditions.checkArgument((float)x == (float)upperFunc.getX(i), "X inconsistent in lower func");
			if(!Double.isNaN(y) && !Double.isNaN(lowerY) && !Double.isNaN(upperY)) {
				Preconditions.checkArgument((float)y >= (float)lowerY, "Lower func must be <= mean func: %s ! <= %s, x=%s", lowerY, y, x);
				Preconditions.checkArgument((float)y <= (float)upperY, "Upper func must be >= mean func: %s ! >= %s, x=%s", upperY, y, x);				
			}
		}
	}
	
	@Override
	public DiscretizedFunc getLower() {
		return lowerFunc;
	}
	
	@Override
	public DiscretizedFunc getUpper() {
		return upperFunc;
	}
	
	public String toString(){
		StringBuffer b = new StringBuffer();

		b.append("Name: " + getName() + '\n');
		b.append("Num Points: " + size() + '\n');
		b.append("Info: " + getInfo() + "\n\n");
		b.append("X, Y Data:" + '\n');
		b.append(getMetadataString()+ '\n');
		return b.toString();
	}
	
	/**
	 *
	 * @return value of each point in the function in String format
	 */
	@Override
	public String getMetadataString(){
		StringBuffer b = new StringBuffer();
		
		for (int i=0; i<size(); i++) {
			double x = getX(i);
			double mean = getY(i);
			double lower = getLowerY(i);
			double upper = getUpperY(i);
			b.append((float)x+"\t"+(float)mean+"\t["+(float)lower+"\t"+(float)upper+"]\n");
		}
		return b.toString();
	}

	@Override
	public UnmodifiableDiscrFunc getStdDevs() {
		if (stdDevs == null) {
			if (boundType == null)
				return null;
			synchronized (this) {
				if (stdDevs == null) {
					DiscretizedFunc stdDevs = new ArbitrarilyDiscretizedFunc();
					for (int i=0; i<size(); i++)
						stdDevs.set(getX(i), boundType.estimateStdDev(getY(i), getLowerY(i), getUpperY(i)));
					this.stdDevs = new UnmodifiableDiscrFunc(stdDevs);
				}
			}
		}
		return this.stdDevs;
	}

	@Override
	public UncertainBoundedDiscretizedFunc estimateBounds(UncertaintyBoundType boundType) {
		DiscretizedFunc lowerFunc = new ArbitrarilyDiscretizedFunc();
		DiscretizedFunc upperFunc = new ArbitrarilyDiscretizedFunc();
		
		for (int i=0; i<size(); i++) {
			double stdDev = getStdDev(i);
			double mean = getY(i);
			BoundedUncertainty bounds = boundType.estimate(mean, stdDev);
			double x = getX(i);
			lowerFunc.set(x, bounds.lowerBound);
			upperFunc.set(x, bounds.upperBound);
		}
		
		return new UncertainArbDiscFunc(this, lowerFunc, upperFunc, boundType, stdDevs);
	}

	@Override
	public UncertaintyBoundType getBoundType() {
		return this.boundType;
	}

	public static class Adapter extends DiscretizedFunc.AbstractAdapter<UncertainArbDiscFunc> {
		
		ArbitrarilyDiscretizedFunc.Adapter funcAdapter = new ArbitrarilyDiscretizedFunc.Adapter();

		@Override
		protected UncertainArbDiscFunc instance(Double minX, Double maxX, Integer size) {
			return new UncertainArbDiscFunc() {

				@Override
				public void set(double x, double y) {
					// allow it to be set for deserialization
					this.func.set(x, y);
				}
				
			};
		}

		@Override
		protected void serializeExtras(JsonWriter out, UncertainArbDiscFunc xy) throws IOException {
			super.serializeExtras(out, xy);
			
			out.name("lowerFunc");
			funcAdapter.write(out, xy.lowerFunc);
			
			out.name("upperFunc");
			funcAdapter.write(out, xy.upperFunc);
			
			if (xy.boundType != null)
				out.name("boundType").value(xy.boundType.name());
			
			if (xy.stdDevs != null) {
				out.name("stdDevs");
				funcAdapter.write(out, xy.stdDevs);
			}
		}

		@Override
		protected Consumer<UncertainArbDiscFunc> deserializeExtra(JsonReader in, String name) throws IOException {
			if (name.equals("stdDevs")) {
				if (in.peek() == JsonToken.NULL)
					return null;
				DiscretizedFunc stdDevs = funcAdapter.read(in);
				return new Consumer<UncertainArbDiscFunc>() {

					@Override
					public void accept(UncertainArbDiscFunc t) {
						t.stdDevs = new UnmodifiableDiscrFunc(stdDevs);
					}
				};
			}
			if (name.equals("lowerFunc")) {
				DiscretizedFunc lowerFunc = funcAdapter.read(in);
				return new Consumer<UncertainArbDiscFunc>() {

					@Override
					public void accept(UncertainArbDiscFunc t) {
						t.lowerFunc = new UnmodifiableDiscrFunc(lowerFunc);
					}
				};
			}
			if (name.equals("upperFunc")) {
				DiscretizedFunc upperFunc = funcAdapter.read(in);
				return new Consumer<UncertainArbDiscFunc>() {

					@Override
					public void accept(UncertainArbDiscFunc t) {
						t.upperFunc = new UnmodifiableDiscrFunc(upperFunc);
					}
				};
			}
			if (name.equals("boundType")) {
				if (in.peek() == JsonToken.NULL)
					return null;
				UncertaintyBoundType boundType = UncertaintyBoundType.valueOf(in.nextString());
				return new Consumer<UncertainArbDiscFunc>() {

					@Override
					public void accept(UncertainArbDiscFunc t) {
						t.boundType = boundType;
					}
				};
			}
			return super.deserializeExtra(in, name);
		}

	}
	
}
