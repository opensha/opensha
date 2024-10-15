package org.opensha.commons.data.uncertainty;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.UnmodifiableDiscrFunc;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.JsonAdapter;
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
@JsonAdapter(UncertainArbDiscFunc.Adapter.class)
public class UncertainArbDiscFunc extends UnmodifiableDiscrFunc implements UncertainBoundedDiscretizedFunc {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private UnmodifiableDiscrFunc lowerFunc;
	private UnmodifiableDiscrFunc upperFunc;

	private UncertaintyBoundType boundType;
	private UnmodifiableDiscrFunc stdDevs;
	
	private String boundName;
	
	public static UncertainArbDiscFunc forStdDev(DiscretizedFunc meanFunc, double stdDev, UncertaintyBoundType boundType,
			boolean allowNegative) {
		Preconditions.checkState(stdDev >= 0d);
		DiscretizedFunc upperFunc = new ArbitrarilyDiscretizedFunc();
		DiscretizedFunc lowerFunc = new ArbitrarilyDiscretizedFunc();
		DiscretizedFunc stdDevsFunc = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<meanFunc.size(); i++) {
			double x = meanFunc.getX(i);
			double y = meanFunc.getY(i);
			BoundedUncertainty bounds = boundType.estimate(y, stdDev);
			if (!allowNegative && bounds.lowerBound < 0)
				bounds = new BoundedUncertainty(boundType, 0d, bounds.upperBound, stdDev);
			upperFunc.set(x, bounds.upperBound);
			lowerFunc.set(x, bounds.lowerBound);
			stdDevsFunc.set(x, stdDev);
//			System.out.println("x="+x+"\ty="+y+"\tsd="+stdDev+"\tbounds="+bounds);
		}
		return new UncertainArbDiscFunc(meanFunc, lowerFunc, upperFunc, boundType, stdDevsFunc);
	}
	
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
	
	@Override
	public String getBoundName() {
		if (boundName == null)
			return getDefaultBoundName();
		return boundName;
	}

	@Override
	public void setBoundName(String boundName) {
		this.boundName = boundName;
	}
	
	@Override
	public UncertainArbDiscFunc deepClone() {
		UncertainArbDiscFunc ret;
		if (this.stdDevs == null)
			ret = new UncertainArbDiscFunc(this.func, lowerFunc.deepClone(), upperFunc.deepClone(), boundType);
		else
			ret = new UncertainArbDiscFunc(this.func, lowerFunc.deepClone(), upperFunc.deepClone(), boundType, stdDevs.deepClone());
		ret.boundName = boundName;
		return ret;
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
			
			out.name("lowerBounds");
			writeDoubleArray(out, xy.lowerFunc);
			
			out.name("upperBounds");
			writeDoubleArray(out, xy.upperFunc);
			
			if (xy.boundType != null)
				out.name("boundType").value(xy.boundType.name());
			
			if (xy.boundName != null)
				out.name("boundName").value(xy.boundName);
			
			if (xy.stdDevs != null) {
				out.name("stdDevs");
				writeDoubleArray(out, xy.stdDevs);
			}
		}
		
		private static void writeDoubleArray(JsonWriter out, DiscretizedFunc func) throws IOException {
			out.beginArray();
			for (Point2D pt : func)
				out.value(pt.getY());
			out.endArray();
		}
		
		private static List<Double> readDoubleArray(JsonReader in) throws IOException {
			in.beginArray();
			List<Double> ret = new ArrayList<>();
			while (in.hasNext())
				ret.add(in.nextDouble());
			in.endArray();
			return ret;
		}
		
		private static DiscretizedFunc buildFunc(DiscretizedFunc xVals, List<Double> yVals) {
			Preconditions.checkState(xVals.size() == yVals.size());
			DiscretizedFunc ret = new ArbitrarilyDiscretizedFunc();
			for (int i=0; i<yVals.size(); i++)
				ret.set(xVals.getX(i), yVals.get(i));
			return ret;
		}

		@Override
		protected Consumer<UncertainArbDiscFunc> deserializeExtra(JsonReader in, String name) throws IOException {
			if (name.equals("stdDevs")) {
				if (in.peek() == JsonToken.NULL)
					return null;
				if (in.peek() == JsonToken.BEGIN_OBJECT) {
					// deprecated
					DiscretizedFunc stdDevs = funcAdapter.read(in);
					return new Consumer<UncertainArbDiscFunc>() {

						@Override
						public void accept(UncertainArbDiscFunc t) {
							t.stdDevs = new UnmodifiableDiscrFunc(stdDevs);
						}
					};
				} else {
					List<Double> stdDevs = readDoubleArray(in);
					return new Consumer<UncertainArbDiscFunc>() {

						@Override
						public void accept(UncertainArbDiscFunc t) {
							t.stdDevs = new UnmodifiableDiscrFunc(buildFunc(t, stdDevs));
						}
					};
				}
			}
			if (name.equals("lowerFunc")) {
				// deprecated
				DiscretizedFunc lowerFunc = funcAdapter.read(in);
				return new Consumer<UncertainArbDiscFunc>() {

					@Override
					public void accept(UncertainArbDiscFunc t) {
						t.lowerFunc = new UnmodifiableDiscrFunc(lowerFunc);
					}
				};
			}
			if (name.equals("upperFunc")) {
				// deprecated
				DiscretizedFunc upperFunc = funcAdapter.read(in);
				return new Consumer<UncertainArbDiscFunc>() {

					@Override
					public void accept(UncertainArbDiscFunc t) {
						t.upperFunc = new UnmodifiableDiscrFunc(upperFunc);
					}
				};
			}
			if (name.equals("lowerBounds")) {
				List<Double> lowers = readDoubleArray(in);
				return new Consumer<UncertainArbDiscFunc>() {

					@Override
					public void accept(UncertainArbDiscFunc t) {
						t.lowerFunc = new UnmodifiableDiscrFunc(buildFunc(t, lowers));
					}
				};
			}
			if (name.equals("upperBounds")) {
				List<Double> uppers = readDoubleArray(in);
				return new Consumer<UncertainArbDiscFunc>() {

					@Override
					public void accept(UncertainArbDiscFunc t) {
						t.upperFunc = new UnmodifiableDiscrFunc(buildFunc(t, uppers));
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
			if (name.equals("boundName")) {
				if (in.peek() == JsonToken.NULL)
					return null;
				String boundName = in.nextString();
				return new Consumer<UncertainArbDiscFunc>() {

					@Override
					public void accept(UncertainArbDiscFunc t) {
						t.boundName = boundName;
					}
				};
			}
			return super.deserializeExtra(in, name);
		}

		@Override
		protected Class<UncertainArbDiscFunc> getType() {
			return UncertainArbDiscFunc.class;
		}

	}
	
	public static void main(String[] args) throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		ArbitrarilyDiscretizedFunc meanFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc upperFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc lowerFunc = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<10; i++) {
			double x = Math.random();
			double lowY = Math.random();
			double highY = lowY + Math.random();
			double mean = 0.5*(lowY+highY);
			meanFunc.set(x, mean);
			upperFunc.set(x, highY);
			lowerFunc.set(x, lowY);
		}
		
		UncertainArbDiscFunc uncertain = new UncertainArbDiscFunc(meanFunc, lowerFunc, upperFunc);
		System.out.println("Original function:\n"+uncertain);
		
		File outFile = new File("/tmp/json_func_test.json");
		FileWriter fw = new FileWriter(outFile);
		gson.toJson(uncertain, fw);
		fw.close();
		
		FileReader fr = new FileReader(outFile);
		uncertain = gson.fromJson(fr, UncertainArbDiscFunc.class);
		System.out.println("Deserialized function:\n"+uncertain);
	}
	
}
