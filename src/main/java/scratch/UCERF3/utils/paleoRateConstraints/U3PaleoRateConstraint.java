/**
 * 
 */
package scratch.UCERF3.utils.paleoRateConstraints;

import java.util.ArrayList;

import org.opensha.commons.data.uncertainty.Uncertainty;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;


/**
 * This class is used to represent the paloe rate data from Tom Parsons or Glen Biasi
 * @author field
 *
 */
public class U3PaleoRateConstraint extends SectMappedUncertainDataConstraint implements java.io.Serializable {
	
//	private String faultSectionName; 	// fault section name
//	private int sectionIndex; 			// section index
//	private double meanRate=Double.NaN; 			// mean section rate
//	private double stdDevOfMeanRate=Double.NaN; 		// Std dev to mean
//	private double lower95ConfOfRate=Double.NaN; 		// Lower 95% confidence
//	private double upper95ConfOfRate=Double.NaN; 		// Upper 95% confidence
//	private double lower68ConfOfRate=Double.NaN; 		// Lower 68% confidence
//	private double upper68ConfOfRate=Double.NaN; 		// Upper 68% confidence
//	private Location paleoSiteLoction = null;	// site loc
//	private String paleoSiteName=null;		// site name
//	
//
//	/**
//	 * This is the constructor used for UCERF2 constraints
//	 * @param faultName
//	 */
//	public U3PaleoRateConstraint(String faultSectionName, Location paleoSiteLoction, int sectionIndex, double meanRate, 
//			double stdDevOfMeanRate, double lower95ConfOfRate, double upper95ConfOfRate) {
//		super(faultSectionName, meanRate, sectionIndex, faultSectionName, paleoSiteLoction,
//				new Uncertainty(UncertaintyType.TWO_SIGMA, lower95ConfOfRate, upper95ConfOfRate, stdDevOfMeanRate));
//		this.faultSectionName = faultSectionName;
//		this.sectionIndex = sectionIndex;
//		this.meanRate = meanRate;
//		this.stdDevOfMeanRate = stdDevOfMeanRate;
//		this.lower95ConfOfRate=lower95ConfOfRate;
//		this.upper95ConfOfRate=upper95ConfOfRate;
//		this.paleoSiteLoction = paleoSiteLoction;
//	}
//	
//
//	/**
//	 * This is the constructor used for UCERF3.2 or earlier constraints.  See code for how the following are
//	 * set: stdDevOfMeanRate, lower95ConfOfRate, upper95ConfOfRate
//	 * @param faultName
//	 */
//	public U3PaleoRateConstraint(String faultSectionName, Location paleoSiteLocation, int sectionIndex, double meanRate, 
//			double lower68ConfOfRate, double upper68ConfOfRate) {
//		super(faultSectionName, meanRate, sectionIndex, faultSectionName, paleoSiteLoction,
//				new Uncertainty(UncertaintyType.TWO_SIGMA, lower95ConfOfRate, upper95ConfOfRate, stdDevOfMeanRate));
//		this.faultSectionName = faultSectionName;
//		this.paleoSiteLoction = paleoSiteLocation;
//		this.sectionIndex = sectionIndex;
//		this.meanRate = meanRate;
//		this.lower68ConfOfRate=lower68ConfOfRate;
//		this.upper68ConfOfRate=upper68ConfOfRate;
//		stdDevOfMeanRate =  ((meanRate-lower68ConfOfRate)+(upper68ConfOfRate-meanRate))/2;
//		
//		double aveLogStd = (Math.abs(Math.log10(meanRate/lower68ConfOfRate)) + Math.abs(Math.log10(meanRate/upper68ConfOfRate)))/2;
//		
//		lower95ConfOfRate = Math.pow(10, Math.log10(meanRate) - 2*aveLogStd);
//		upper95ConfOfRate = Math.pow(10, Math.log10(meanRate) + 2*aveLogStd);
//	}
//	
//
//	/**
//	 * This is the constructor used for UCERF3.3+ constraints.  See code for how the following are
//	 * set: stdDevOfMeanRate, lower95ConfOfRate, upper95ConfOfRate
//	 * @param faultName
//	 */
//	public U3PaleoRateConstraint(String faultSectionName, Location paleoSiteLocation, int sectionIndex, double meanRate, 
//			double lower68ConfOfRate, double upper68ConfOfRate, double lower95ConfOfRate, double upper95ConfOfRate) {
//		this.faultSectionName = faultSectionName;
//		this.paleoSiteLoction = paleoSiteLocation;
//		this.sectionIndex = sectionIndex;
//		this.meanRate = meanRate;
//		this.lower68ConfOfRate=lower68ConfOfRate;
//		this.upper68ConfOfRate=upper68ConfOfRate;
//		stdDevOfMeanRate =  ((meanRate-lower68ConfOfRate)+(upper68ConfOfRate-meanRate))/2;
//		
//		this.lower95ConfOfRate = lower95ConfOfRate;
//		this.upper95ConfOfRate = upper95ConfOfRate;
//	}

	
	public U3PaleoRateConstraint(String name, int sectionIndex, String sectionName, Location dataLocation,
			double bestEstimate, Uncertainty... uncertainties) {
		super(name, sectionIndex, sectionName, dataLocation, bestEstimate, uncertainties);
	}

	/**
	 * Get the paleoSiteLoction
	 * @return
	 */
	public Location getPaleoSiteLoction() {
		return dataLocation;
	}
	
	/**
	 * Get the paleoSiteName
	 * @return
	 */
	public String getPaleoSiteName() {
		return name;
	}

	
	/**
	 * Get the associated fault section name
	 * @return
	 */
	public String getFaultSectionName() {
		return this.sectionName;
	}
	
	/**
	 * Get the associated section index
	 * @return
	 */
	public int getSectionIndex() {
		return this.sectionIndex;
	}
	
	/**
	 * Get the mean rate
	 * @return
	 */
	public double getMeanRate() {
		return this.bestEstimate;
	}
	

	public double getLower95ConfOfRate() {
		return estimateUncertaintyBounds(UncertaintyBoundType.CONF_95).lowerBound;
	}

	public double getUpper95ConfOfRate() {
		return estimateUncertaintyBounds(UncertaintyBoundType.CONF_95).upperBound;
	}
	

	public double getLower68ConfOfRate() {
		return estimateUncertaintyBounds(UncertaintyBoundType.CONF_68).lowerBound;
	}

	public double getUpper68ConfOfRate() {
		return estimateUncertaintyBounds(UncertaintyBoundType.CONF_68).upperBound;
	}


	/**
	 * Get StdDev to mean for the rate
	 * @return
	 */
	public double getStdDevOfMeanRate() {
		return getPreferredStdDev();
	}

}
