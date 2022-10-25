package org.opensha.sha.magdist;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.exceptions.InvalidRangeException;


/**
 * <p>Title: ArbIncrementalMagFreqDist.java </p>
 *
 * <p>Description: This class allows the user to create a Arbitrary
 * IncrementalMagFreqDist.</p>
 *
 * @author Nitin Gupta , Ned Field
 * @version 1.0
 */
public class ArbIncrementalMagFreqDist
extends IncrementalMagFreqDist {

	public  static String NAME = "Arb Mag Freq Dist";


	public ArbIncrementalMagFreqDist(double min, double max, int num) throws  InvalidRangeException {
		super(min, max, num);
	}

	public ArbIncrementalMagFreqDist(double min,int num,double delta) throws InvalidRangeException {
		super(min, num, delta);
	}





	/**
	 * This sets each mag & rate value here according to the function passed in.  Each x-axis value of
	 * the function passed in is rounded to the nearest x-axis value here, and rates are summed if more
	 * than one x-axis value of the input function fall on the same x-axis value here.  X-axis values from
	 * the input function that are out of the bounds of the present MFD are ignored.  Zeros will be present
	 * at any x-axis values where there wasn't a corresponding value in the input function.
	 * If the preserveRates boolean is false, then the moment rate of each point
	 * is preserved (although the total moment rates of the two functions may differ if any
	 * endpoints were ignored).  Otherwise total rates are preserved (assuming no endpoints are
	 * ignored). Discretization of this MFD should  be same (or more densely discretized) than 
	 * that passed in or significant biases will result from the rounding (due to ≥ rules for
	 * values exactly halfway between).
	 * 
	 * @param func the new Magnitude Frequency distribution to be added
	 * @param preserveRates specifies whether to preserve rates or moment rates
	 */

	public void setResampledMagFreqDist(DiscretizedFunc func, boolean preserveRates) {

		for (int i=0;i<func.size();++i) {     // add the y values from this new distribution
			addResampledMagRate(func.getX(i), func.getY(i), preserveRates);
		}
	}

	/**
	 * This sets incremental rate according to the cumulative rates passed in.
	 * 
	 * @param cumFunc
	 */
	public void setCumRateDist(DiscretizedFunc cumFunc) {
		double halfDelta = this.delta/2;
		double mag, mag1, mag2, rate1, rate2, rate;
		double minX = cumFunc.getMinX(), maxX = cumFunc.getMaxX();
		for(int i=0; i<this.num; ++i) {
			mag = this.getX(i);
			mag1 = mag - halfDelta;
			mag2 = mag + halfDelta;
			// rate 1
			/*if(mag1 < minX) rate1 = cumFunc.getY(minX);
		  else if(mag1 > maxX) rate1 = cumFunc.getY(maxX);
		  else */rate1 = cumFunc.getInterpolatedY_inLogYDomain(mag1) ;
		  // rate 2
		  /*if(mag2 < minX) rate2 = cumFunc.getY(minX);
		  else if(mag2 > maxX) rate2 = cumFunc.getY(maxX);
		  else*/ rate2 = cumFunc.getInterpolatedY_inLogYDomain(mag2) ;
		  rate = rate1 - rate2;
		  this.set(i, rate);
		}
	}

	/**
	 * This and adds the rate & mag passed in to the MFD after rounding to the nearest x-axis
	 * value (ignoring those out of range).  If the preserveRates boolean is false, then the moment 
	 * rate of the point is preserved (assuming it's in range).  Otherwise the rate of that point 
	 * is preserved. Discretization of this MFD should  be same (or more densely discretized) than 
	 * that passed in or significant biases will result from the rounding (due to ≥ rules for
	 * values exactly halfway between).
	 * 
	 * @param mag & rate to be added
	 * @param preserveRates specifies whether to preserve rates or moment rates
	 */

	public void addResampledMagRate(double mag, double rate, boolean preserveRates) {

		int index = (int)Math.round((mag-minX)/delta);
		if(!isIndexWithinRange(index)) return;
		if(preserveRates)
			set(index,this.getY(index)+ rate);
		else {
			double newRate = rate*MagUtils.magToMoment(mag)/MagUtils.magToMoment(this.getX(index));
			set(index,this.getY(index)+ newRate);
		}
	}

	/**
	 * returns the name of the class
	 * @return
	 */
	public String getDefaultName() {
		return NAME;
	}

	/**
	 * Returns the default Info String for the Distribution
	 * @return String
	 */
	public String getDefaultInfo() {
		return "Arbitrary Incremental Magnitude Frequency Dististribution";
	}
}
