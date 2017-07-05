/**
 * 
 */
package scratch.UCERF3.utils.paleoRateConstraints;

import java.util.ArrayList;

import org.opensha.commons.geo.Location;


/**
 * This class is used to represent the paloe rate data from Tom Parsons or Glen Biasi
 * @author field
 *
 */
public class PaleoRateConstraint  implements java.io.Serializable {
	
	private String faultSectionName; 	// fault section name
	private int sectionIndex; 			// section index
	private double meanRate=Double.NaN; 			// mean section rate
	private double stdDevOfMeanRate=Double.NaN; 		// Std dev to mean
	private double lower95ConfOfRate=Double.NaN; 		// Lower 95% confidence
	private double upper95ConfOfRate=Double.NaN; 		// Upper 95% confidence
	private double lower68ConfOfRate=Double.NaN; 		// Lower 68% confidence
	private double upper68ConfOfRate=Double.NaN; 		// Upper 68% confidence
	private Location paleoSiteLoction = null;	// site loc
	private String paleoSiteName=null;		// site name
	

	/**
	 * This is the constructor used for UCERF2 constraints
	 * @param faultName
	 */
	public PaleoRateConstraint(String faultSectionName, Location paleoSiteLoction, int sectionIndex, double meanRate, 
			double stdDevOfMeanRate, double lower95ConfOfRate, double upper95ConfOfRate) {
		this.faultSectionName = faultSectionName;
		this.sectionIndex = sectionIndex;
		this.meanRate = meanRate;
		this.stdDevOfMeanRate = stdDevOfMeanRate;
		this.lower95ConfOfRate=lower95ConfOfRate;
		this.upper95ConfOfRate=upper95ConfOfRate;
		this.paleoSiteLoction = paleoSiteLoction;
	}
	

	/**
	 * This is the constructor used for UCERF3.2 or earlier constraints.  See code for how the following are
	 * set: stdDevOfMeanRate, lower95ConfOfRate, upper95ConfOfRate
	 * @param faultName
	 */
	public PaleoRateConstraint(String faultSectionName, Location paleoSiteLocation, int sectionIndex, double meanRate, 
			double lower68ConfOfRate, double upper68ConfOfRate) {
		this.faultSectionName = faultSectionName;
		this.paleoSiteLoction = paleoSiteLocation;
		this.sectionIndex = sectionIndex;
		this.meanRate = meanRate;
		this.lower68ConfOfRate=lower68ConfOfRate;
		this.upper68ConfOfRate=upper68ConfOfRate;
		stdDevOfMeanRate =  ((meanRate-lower68ConfOfRate)+(upper68ConfOfRate-meanRate))/2;
		
		double aveLogStd = (Math.abs(Math.log10(meanRate/lower68ConfOfRate)) + Math.abs(Math.log10(meanRate/upper68ConfOfRate)))/2;
		
		lower95ConfOfRate = Math.pow(10, Math.log10(meanRate) - 2*aveLogStd);
		upper95ConfOfRate = Math.pow(10, Math.log10(meanRate) + 2*aveLogStd);
	}
	

	/**
	 * This is the constructor used for UCERF3.3+ constraints.  See code for how the following are
	 * set: stdDevOfMeanRate, lower95ConfOfRate, upper95ConfOfRate
	 * @param faultName
	 */
	public PaleoRateConstraint(String faultSectionName, Location paleoSiteLocation, int sectionIndex, double meanRate, 
			double lower68ConfOfRate, double upper68ConfOfRate, double lower95ConfOfRate, double upper95ConfOfRate) {
		this.faultSectionName = faultSectionName;
		this.paleoSiteLoction = paleoSiteLocation;
		this.sectionIndex = sectionIndex;
		this.meanRate = meanRate;
		this.lower68ConfOfRate=lower68ConfOfRate;
		this.upper68ConfOfRate=upper68ConfOfRate;
		stdDevOfMeanRate =  ((meanRate-lower68ConfOfRate)+(upper68ConfOfRate-meanRate))/2;
		
		this.lower95ConfOfRate = lower95ConfOfRate;
		this.upper95ConfOfRate = upper95ConfOfRate;
	}

	
	/**
	 * Get the paleoSiteLoction
	 * @return
	 */
	public Location getPaleoSiteLoction() {
		return paleoSiteLoction;
	}

	public void setPaleoSiteName(String paleoSiteName) {
		this.paleoSiteName = paleoSiteName;
	}
	
	/**
	 * Get the paleoSiteName
	 * @return
	 */
	public String getPaleoSiteName() {
		return paleoSiteName;
	}

	
	/**
	 * Get the associated fault section name
	 * @return
	 */
	public String getFaultSectionName() {
		return this.faultSectionName;
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
		return this.meanRate;
	}
	

	public double getLower95ConfOfRate() {
		return lower95ConfOfRate;
	}

	public double getUpper95ConfOfRate() {
		return upper95ConfOfRate;
	}
	

	public double getLower68ConfOfRate() {
		return lower68ConfOfRate;
	}

	public double getUpper68ConfOfRate() {
		return upper68ConfOfRate;
	}


	/**
	 * Get StdDev to mean for the rate
	 * @return
	 */
	public double getStdDevOfMeanRate() {
		return this.stdDevOfMeanRate;
	}
	
	/**
	   * This computes a PaleoRateConstraint object that represents a weighted average of those given
	   * Note: Lower and upper 95 not weight averaged, they are just set NaN.  Section index and name
	   * are set from the last PaleoRateConstraint in the list passed in.  Presumably this is the same
	   * as for all others, but this is not checked for.
	   * @param segRateConstraintList
	   * @return
	   */
	  public static PaleoRateConstraint getWeightMean(ArrayList<PaleoRateConstraint> segRateConstraintList) {
		  double total = 0;
		  double sigmaTotal = 0;
		  String faultSectName=null;
		  int segIndex = -1;
		  for(int i=0; i<segRateConstraintList.size(); ++i) {
			  PaleoRateConstraint segRateConstraint = segRateConstraintList.get(i);
			  faultSectName = segRateConstraint.getFaultSectionName();
			  segIndex = segRateConstraint.getSectionIndex();
			  double sigmaSq = 1.0/(segRateConstraint.getStdDevOfMeanRate()*segRateConstraint.getStdDevOfMeanRate());
			  sigmaTotal+=sigmaSq;
			  total+=sigmaSq*segRateConstraint.getMeanRate();
		  }
		  PaleoRateConstraint finalSegRateConstraint = new PaleoRateConstraint(faultSectName, null, segIndex, total/sigmaTotal, 
				  Math.sqrt(1.0/sigmaTotal), Double.NaN, Double.NaN);
		  return finalSegRateConstraint;
	  }

}
