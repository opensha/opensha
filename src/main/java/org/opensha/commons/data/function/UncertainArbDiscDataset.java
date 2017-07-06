package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.util.Iterator;

import org.jfree.data.Range;

import com.google.common.base.Preconditions;

/**
 * Represents an uncertain discretized function which has both an upper and lower bound. Can be used in
 * conjunction with PlotLineType.SHADED_UNCERTAIN to show shaded uncertainty bounds, or plotted normally.
 * 
 * @author kevin
 *
 */
public class UncertainArbDiscDataset extends UnmodifiableDiscrFunc {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private UnmodifiableDiscrFunc lowerFunc;
	private UnmodifiableDiscrFunc upperFunc;
	
	public UncertainArbDiscDataset(DiscretizedFunc meanFunc, DiscretizedFunc lowerFunc, DiscretizedFunc upperFunc) {
		super(meanFunc);
		this.lowerFunc = new UnmodifiableDiscrFunc(lowerFunc);
		this.upperFunc = new UnmodifiableDiscrFunc(upperFunc);
		
		Preconditions.checkArgument(meanFunc.size() == lowerFunc.size(), "Lower func not same length as mean");
		Preconditions.checkArgument(meanFunc.size() == upperFunc.size(), "Upper func not same length as mean");
		
		for (int i=0; i<size(); i++) {
			double x = meanFunc.getX(i);
			double y = meanFunc.getY(i);
			
			double lowerY = lowerFunc.getY(i);
			double upperY = upperFunc.getY(i);
			
			Preconditions.checkArgument((float)x == (float)lowerFunc.getX(i), "X inconsistent in lower func");
			Preconditions.checkArgument((float)x == (float)upperFunc.getX(i), "X inconsistent in lower func");
			Preconditions.checkArgument((float)y >= (float)lowerY, "Lower func must be <= mean func: %s ! <= %s, x=%s", lowerY, y, x);
			Preconditions.checkArgument((float)y <= (float)upperY, "Upper func must be >= mean func: %s ! >= %s, x=%s", upperY, y, x);
		}
	}
	
	public DiscretizedFunc getLower() {
		return lowerFunc;
	}
	
	public DiscretizedFunc getUpper() {
		return upperFunc;
	}
	
	public Range getYRange(int index) {
		return new Range(lowerFunc.getY(index), upperFunc.getY(index));
	}
	
	public Range getYRange(double x) {
		return getYRange(getXIndex(x));
	}
	
	public double getUpperY(int index) {
		return upperFunc.getY(index);
	}
	
	public double getUpperY(double x) {
		return getUpperY(getXIndex(x));
	}
	
	public double getLowerY(int index) {
		return lowerFunc.getY(index);
	}

	public double getLowerY(double x) {
		return getLowerY(getXIndex(x));
	}
	
	public double getUpperMaxY() {
		return upperFunc.getMaxY();
	}
	
	public double getUpperMinY() {
		return upperFunc.getMinY();
	}
	
	public double getLowerMaxY() {
		return lowerFunc.getMaxY();
	}
	
	public double getLowerMinY() {
		return lowerFunc.getMinY();
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
	
}
