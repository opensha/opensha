package org.opensha.sha.magdist;


import java.io.IOException;
import java.util.function.Consumer;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.Feature.FeatureAdapter;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;



/**
 * <p>Title:IncrementalMagFreqDist </p>
 * <p>Description:This class give the rate of earthquakes (number per year) in succesion</p>
 *
 * @author : Nitin Gupta Date:July 26,2002
 * @version 1.0
 */
@JsonAdapter(IncrementalMagFreqDist.Adapter.class)
public class IncrementalMagFreqDist extends EvenlyDiscretizedFunc
implements IncrementalMagFreqDistAPI,java.io.Serializable {

	//for Debug purposes
	private boolean D = false;

	protected String defaultInfo;
	protected String defaultName;
	
	protected Region region;

	/**
	 * todo constructors
	 * @param min
	 * @param num
	 * @param delta
	 * using the parameters we call the parent class constructors to initialise the parent class variables
	 */
	public IncrementalMagFreqDist (IncrementalMagFreqDist other) {
		super(other.minX, other.maxX, other.size());
		region = other.region;
		setName(other.getName());
		setInfo(other.getInfo());
		setTolerance(other.getTolerance());
		for (int i=0; i<other.size(); i++)
			set(i, other.getY(i));
	}

	/**
	 * todo constructors
	 * @param min
	 * @param num
	 * @param delta
	 * using the parameters we call the parent class constructors to initialise the parent class variables
	 */
	public IncrementalMagFreqDist (double min,int num,double delta)
			throws InvalidRangeException {
		super(min,num,delta);
		setTolerance(delta/1000000);
	}

	/**
	 * todo constructors
	 * @param min
	 * @param max
	 * @param num
	 * using the min, max and num we calculate the delta
	 */
	public IncrementalMagFreqDist(double min,double max,int num)
			throws InvalidRangeException {
		super(min,max,num);
		setTolerance(delta/1000000);
	}

	/**
	 * Sets the region associated with this MFD
	 * 
	 * @param region
	 */
	public void setRegion(Region region) {
		this.region = region;
	}

	/**
	 * Gets the region associated with this MFD, if available
	 * 
	 * @return region assocated with this MFD, or null if no region
	 */
	public Region getRegion() {
		return this.region;
	}

	/**
	 * This function finds IncrRate for the given magnitude
	 * @param mag
	 * @return
	 */
	public double getIncrRate(double mag) {
		int xIndex = getXIndex(mag);
		return getIncrRate(xIndex);
	}


	/**
	 * This function finds the IncrRate at the given index
	 * @param index
	 * @return
	 */
	public double getIncrRate(int index) {
		return getY(index);
	}


	/**
	 * This function finds the cumulative Rate at a specified magnitude (the rate greater than
	 * and equal to that mag)
	 * @param mag
	 * @return
	 */
	public double getCumRate(double mag) {
		return getCumRate(getXIndex(mag));
	}


	/**
	 * This function finds the cumulative Rate at a specified index  (the rate greater than
	 * and equal to that index)
	 * @param index
	 * @return
	 */

	public double getCumRate(int index) {
		double sum=0.0;
		for(int i=index;i<num;++i)
			sum+=getIncrRate(i);
		return sum;
	}



	/**
	 * This function finds the moment Rate at a specified magnitude
	 * @param mag
	 * @return
	 */

	public double getMomentRate(double mag) {
		return getIncrRate(mag) * MagUtils.magToMoment(mag);
	}


	/**
	 * This function finds the moment Rate at a specified index
	 * @param index
	 * @return
	 */

	public double getMomentRate(int index) {
		return getIncrRate(index) * MagUtils.magToMoment(getX(index));
	}



	/**
	 * This function return the sum of all the moment rates as a double variable
	 * @return
	 */

	public double getTotalMomentRate() {
		double sum=0.0;
		for(int i=0;i<num;++i)
			sum+=getMomentRate(i);
		return sum;
	}


	/**
	 * This function returns the sum of all the incremental rate as the double varibale
	 * @return
	 */

	public double getTotalIncrRate() {
		double sum=0.0;
		for(int i=0;i<num;++i)
			sum+=getIncrRate(i);
		return sum;
	}

	/**
	 * This function normalises the values of all the Incremental rate at each point, by dividing each one
	 * by the totalIncrRate, so that after normalization the sum addition of all incremental rate at each point
	 * comes to be 1.
	 */

	public void normalizeByTotalRate() {
		double totalIncrRate=getTotalIncrRate();
		for(int i=0;i<num;++i) {
			double newRate= getIncrRate(i)/totalIncrRate;
			super.set(i,newRate);
		}
	}

	/**
	 * This function normalizes to a PDF where the sum of y-values*delta is 1.0. 
	 */

	public void normalizeToPDF() {
		double totalIncrRate=getTotalIncrRate()*delta;
		for(int i=0;i<num;++i) {
			double newRate= getIncrRate(i)/totalIncrRate;
			super.set(i,newRate);
		}
	}



	/**
	 * This returns the object of the class EvenlyDiscretizedFunc which contains all the points
	 * with Cum Rate Distribution (the rate greater than and equal to each magnitude)
	 * @return
	 */

	public EvenlyDiscretizedFunc getCumRateDist() {
		EvenlyDiscretizedFunc cumRateDist = new EvenlyDiscretizedFunc(minX,num,delta);
		double sum=0.0;
		for(int i=num-1;i>=0;--i) {
			sum+=getIncrRate(i);
			cumRateDist.set(i,sum);
		}
		cumRateDist.setInfo(this.getInfo());
		cumRateDist.setName(this.getName());
		return cumRateDist;
	}

	/**
	 * This returns the object of the class EvenlyDiscretizedFunc which contains all the points
	 * with Cum Rate Distribution (the rate greater than and equal to each magnitude).
	 * It differs from getCumRateDist() in the X Values because the values are offset
	 * by delta/2 in the CumDist returned by this method.
	 * 
	 * @return
	 */

	public EvenlyDiscretizedFunc getCumRateDistWithOffset() {
		EvenlyDiscretizedFunc cumRateDist = new EvenlyDiscretizedFunc(minX-delta/2,num,delta);
		double sum=0.0;
		for(int i=num-1;i>=0;--i) {
			sum+=getIncrRate(i);
			cumRateDist.set(i,sum);
		}
		cumRateDist.setInfo(this.getInfo());
		cumRateDist.setName(this.getName());
		return cumRateDist;
	}


	/**
	 * This returns the object of the class EvenlyDiscretizedFunc which contains all the points
	 * with Moment Rate Distribution
	 * @return
	 */

	public EvenlyDiscretizedFunc getMomentRateDist() {
		EvenlyDiscretizedFunc momentRateDist = new EvenlyDiscretizedFunc(minX,num,delta);
		for(int i=num-1;i>=0;--i) {
			momentRateDist.set(i,getMomentRate(i));
		}
		momentRateDist.setInfo(this.getInfo());
		momentRateDist.setName(this.getName());
		return momentRateDist;
	}



	/**
	 * This returns the object of the class EvenlyDiscretizedFunc which contains cumulative
	 * Moment Rate (the total moment rate for all points greater than and equal to each mag)
	 * @return
	 */
	public EvenlyDiscretizedFunc getCumMomentRateDist() {
		EvenlyDiscretizedFunc momentRateDist = new EvenlyDiscretizedFunc(minX,num,delta);
		double totMoRate=0;
		for(int i=num-1;i>=0;--i) {
			totMoRate += getMomentRate(i);
			momentRateDist.set(i,totMoRate);
		}
		momentRateDist.setInfo(this.getInfo());
		momentRateDist.setName(this.getName());
		return momentRateDist;
	}

	/**
	 * Using this function each data point is scaled to ratio of specified newTotalMomentRate
	 * and oldTotalMomentRate.
	 * @param newTotMoRate
	 */

	public void scaleToTotalMomentRate(double newTotMoRate) {
		double oldTotMoRate=getTotalMomentRate();
		if(D) System.out.println("old Mo. Rate = " + oldTotMoRate);
		if(D) System.out.println("target Mo. Rate = " + newTotMoRate);
		double scaleRate=newTotMoRate/oldTotMoRate;
		for(int i=0;i<num;++i) {
			super.set(i,scaleRate*getIncrRate(i));
		}
		if(D) System.out.println("actual Mo. Rate = " + getTotalMomentRate());


	}


	/**
	 * Using this function each data point is scaled to the ratio of the CumRate at a given
	 * magnitude and the specified rate.
	 * @param mag
	 * @param rate
	 */

	public void scaleToCumRate(double mag, double rate) {
		int index = getXIndex(mag);
		scaleToCumRate(index,rate);
	}



	/**
	 * Using this function each data point is scaled to the ratio of the CumRate at a given
	 * index and the specified rate
	 * @param index
	 * @param rate
	 */

	public void scaleToCumRate(int index,double rate) {
		double temp=getCumRate(index);
		double scaleCumRate=rate/temp;
		for(int i=0;i<num;++i)
			super.set(i,scaleCumRate*getIncrRate(i));
	}



	/**
	 * Using this function each data point is scaled to the ratio of the IncrRate at a given
	 * magnitude and the specified newRate
	 * @param mag
	 * @param newRate
	 */

	public void scaleToIncrRate(double mag, double newRate) {
		int index = getXIndex(mag);
		scaleToIncrRate(index,newRate);
	}


	/**
	 * Using this function each data point is scaled to the ratio of the IncrRate at a given
	 * index and the specified newRate
	 * @param index
	 * @param newRate
	 */

	public void scaleToIncrRate(int index, double newRate) {
		double temp=getIncrRate(index);
		double scaleIncrRate=newRate/temp;
		for(int i=0;i<num;++i)
			super.set(i,scaleIncrRate*getIncrRate(i));
	}

	/**
	 * Returns the default Info String for the Distribution
	 * @return String
	 */
	public String getDefaultInfo(){
		return defaultInfo;
	}

	/**
	 * Returns the default Name for the Distribution
	 * @return String
	 */
	public String getDefaultName(){
		defaultName = "Incremental Mag Freq Dist";
		return defaultName;
	}

	/**
	 * Returns the Name of the Distribution that user has set from outside,
	 * if it is null then it returns the default Name from the distribution.
	 * Makes the call to the parent "getName()" method to get the metadata
	 * set outside the application.
	 * @return String
	 */
	public String getName(){
		if(name !=null && !(name.trim().equals("")))
			return super.getName();
		return getDefaultName();
	}


	/**
	 * Returns the info of the distribution that user has set from outside,
	 * if it is null then it returns the default info from the distribution.
	 * Makes the call to the parent "getInfo()" method to get the metadata
	 * set outside the application.
	 * @return String
	 */
	public String getInfo(){
		if(info !=null && !(info.equals("")))
			return super.getInfo();
		return getDefaultInfo();
	}


	/** Returns a copy of this and all points in this DiscretizedFunction */
	public IncrementalMagFreqDist deepClone() {

		IncrementalMagFreqDist f = new IncrementalMagFreqDist(
				minX, num, delta
				);
		f.setRegion(region);
		f.tolerance = tolerance;
		f.setInfo(this.getInfo());
		f.setName(this.getName());
		for(int i = 0; i<num; i++)
			f.set(i, points[i]);

		return f;
	}

	/**
	 * This returns the maximum magnitude with a non-zero rate
	 * @return
	 */
	public double getMinMagWithNonZeroRate() {
		for(int i=0; i<num; i++) {
			if(getY(i)>0) return getX(i);
		}
		return Double.NaN;
	}

	/**
	 * This returns the maximum magnitude with a non-zero rate
	 * @return
	 */
	public double getMaxMagWithNonZeroRate() {
		for(int i=num-1; i>=0; i--) {
			if(getY(i)>0) return getX(i);
		}
		return  Double.NaN;
	}


	/**
	 * This computes the b-value (the slope of the line of a linear-log plot, meaning
	 * after computing log10 of all y-axis values) between the the given x-axis values.
	 * If Double.NaN is passed in, then the first (or last) non-zero rate is used for
	 * min_bValMag (or max_bValMag).  Results may be biased if there are any zero-rate
	 * bins between min_bValMag and max_bValMag.
	 * @param min_bValMag
	 * @param max_bValMag
	 * @return
	 */
	public double compute_bValue(double min_bValMag, double max_bValMag) {
		int firstIndex, lastIndex;

		if(Double.isNaN(min_bValMag))
			firstIndex = getClosestXIndex(getMinMagWithNonZeroRate());
		else
			firstIndex = getClosestXIndex(min_bValMag);

		if(Double.isNaN(max_bValMag))
			lastIndex = getClosestXIndex(getMaxMagWithNonZeroRate());
		else
			lastIndex = getClosestXIndex(max_bValMag);

		SimpleRegression regression = new SimpleRegression();
		for(int i=firstIndex; i<=lastIndex; i++) {
			if(getY(i)>0.0)	// avoid taking log of zero
				regression.addData(getX(i), Math.log10(getY(i)));
		}

		//	   if(getX(lastIndex)-getX(firstIndex) <1.0)
		//		   return Double.NaN;

		return regression.getSlope();
	}

	/**
	 * This gives the b-value for a GR distribution that has the same Mmin, Mmax, total rate, and moment rate 
	 * (solving for the associated b-value).  This case handles zero-rate bins.  See the method 
	 * GutenbergRichterMagFreqDist.setAllButBvalue(*) for accuracy information.
	 * @param minMag
	 * @param maxMag
	 * @return
	 */
	public double compute_bValueAlt(double minMag, double maxMag) {
		int firstIndex = getClosestXIndex(minMag);
		int lastIndex = getClosestXIndex(maxMag);
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(this.getMinX(), this.size(), this.getDelta());
		gr.setAllButBvalue(this.getX(firstIndex), this.getX(lastIndex), this.getTotalMomentRate(), this.getTotalIncrRate());
		return gr.get_bValue();
	}

	/**
	 * This returns a GR distribution with the same Mmin, Mmax, total rate, and moment rate 
	 * (solving for the associated b-value).  Note that this is not "fit" in a least squares sense.
	 * This approach was adopted in order to handle zero-rate bins.
	 * @param minMag
	 * @param maxMag
	 * @return
	 */
	public GutenbergRichterMagFreqDist getGR_fit(double minMag, double maxMag) {
		int firstIndex = getClosestXIndex(minMag);
		int lastIndex = getClosestXIndex(maxMag);
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(this.getMinX(), this.size(), this.getDelta());
		gr.setAllButBvalue(this.getX(firstIndex), this.getX(lastIndex), this.getTotalMomentRate(), this.getTotalIncrRate());
		return gr;
	}


	/**
	 * This sets all y-axis values above the given total moment rate to zero.
	 * The final total moment rate will be something less than the value passed in.
	 * @param moRate
	 */
	public void setValuesAboveMomentRateToZero(double moRate) {
		double mag=findMagJustAboveMomentRate(moRate);
		if(Double.isNaN(mag)) return;
		zeroAboveMag(mag);
	}

	/**
	 * This finds the smallest magnitude such that all those less than and equal
	 * to this have a cumulative moment rate less than that passed in.
	 * @param moRate - in Nm/yr
	 */
	public double findMagJustAboveMomentRate(double moRate) {
		double cumMoRate=0;
		int targetIndex = -1;
		for(int i=0;i<size();i++) {
			cumMoRate += getMomentRate(i);
			if(cumMoRate>moRate) {
				targetIndex = i-1;
				break;
			}
		}
		if(targetIndex == -1)
			return Double.NaN;
		else
			return getX(targetIndex);

	}

	/**
	 * Sets the rate of all magnitudes above the supplied magnitude to 0.
	 * @param mag 
	 */
	public void zeroAboveMag(double mag) {
		for (int i = getXIndex(mag)+1; i < size(); i++) {
			set(i, 0);
		}
	}

	/**
	 * Sets the rate of all magnitudes above the supplied magnitude to 0.
	 * @param mag 
	 */
	public void zeroAtAndAboveMag(double mag) {
		for (int i = getXIndex(mag); i < size(); i++) {
			set(i, 0);
		}
	}   

	/**
	 * This computes the b-value (the slope of the line of a linear-log plot, meaning
	 * after computing log10 of all y-axis values) between the smallest and largest 
	 * mags with non-zero rates (zeros at the beginning and end of the distribution 
	 * are ignored).
	 * @return
	 */
	public double compute_bValue() {
		return compute_bValue(Double.NaN, Double.NaN);
	}

	public static class Adapter extends GenericAdapter<IncrementalMagFreqDist> {

		@Override
		protected IncrementalMagFreqDist instance(Double minX, Double maxX, Integer size) {
			Preconditions.checkNotNull(minX, "minX must be supplied before values to deserialize EvenlyDiscretizedFunc");
			Preconditions.checkNotNull(maxX, "maxX must be supplied before values to deserialize EvenlyDiscretizedFunc");
			Preconditions.checkNotNull(size, "size must be supplied before values to deserialize EvenlyDiscretizedFunc");
			return new IncrementalMagFreqDist(minX, maxX, size);
		}

		@Override
		protected Class<IncrementalMagFreqDist> getType() {
			return IncrementalMagFreqDist.class;
		}
		
	}

	public static abstract class GenericAdapter<E extends IncrementalMagFreqDist> extends DiscretizedFunc.AbstractAdapter<E> {
		
		private FeatureAdapter regionAdapter = new FeatureAdapter();

		@Override
		protected void serializeExtras(JsonWriter out, E xy) throws IOException {
			super.serializeExtras(out, xy);
			
			if (xy.region != null) {
				out.name("region");
				Region region = xy.region;
				if (region instanceof GriddedRegion)
					region = new Region(region);
				regionAdapter.write(out, region.toFeature());
			}
		}

		@Override
		protected Consumer<E> deserializeExtra(JsonReader in, String name) throws IOException {
			if (name.equals("region")) {
				if (in.peek() == JsonToken.NULL)
					return null;
				Region region = Region.fromFeature(regionAdapter.read(in));
				return new Consumer<E>() {

					@Override
					public void accept(IncrementalMagFreqDist t) {
						t.setRegion(region);;
					}
				};
			}
			return super.deserializeExtra(in, name);
		}

	}

}
