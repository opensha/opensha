package org.opensha.sha.earthquake;



import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.RuptureSurface;

/**
 * <p>Title:ProbEqkRupture </p>
 * <p>Description: Probabilistic Earthquake Rupture</p>
 *
 * @author rewritten by Ned Field
 * @date Dec. 14, 2011
 * @version 1.0
 */

public class ProbEqkRupture extends EqkRupture {


	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected double probability;

	/* **********************/
	/** @todo  Constructors */
	/* **********************/

	public ProbEqkRupture() {
		super();
	}

	public ProbEqkRupture(double mag,
			double aveRake,
			double probability,
			RuptureSurface ruptureSurface,
			Location hypocenterLocation)
	throws InvalidRangeException {

		super(mag, aveRake, ruptureSurface, hypocenterLocation);
		this.probability = probability;

	}



	public double getProbability() { return probability; }

	public void setProbability(double p) { probability = p; }

	/**
	 * This is a function of probability and duration
	 */
	 public double getMeanAnnualRate(double duration){
		return -Math.log(1 - probability)/duration;
	 }


	 public String getInfo() {
		 String info = new String("\tMag. = " + (float) mag + "\n" +
				 "\tAve. Rake = " + (float) aveRake + "\n" +
				 "\tProb. = " + (float) probability + "\n" +
				 "\tAve. Dip = " + (float) ruptureSurface.getAveDip() +
				 "\n" +
				 "\tHypocenter = " + hypocenterLocation + "\n");

		 info += ruptureSurface.getInfo();
		 return info;
	 }


	 /**
	  * Returns the Metadata for the rupture of a given source. Following information
	  * is represented as a single line for the rupture.
	  * <ul>
	  *   <li>Source Index
	  *   <li>Rupture Index
	  *   <li>Magnitude
	  *   <li>Probablity
	  *   <li>Ave. Rake
	  *  <p>If rupture surface is a point surface then point surface locations are
	  *  included in it.So the next 3 elements are :</p>
	  *   <li>Point Surface Latitude
	  *   <li>Point Surface Longitude
	  *   <li>Point Surface Depth
	  *   <li>Source Name
	  * </ul>
	  *
	  * Each element in the single line is seperated by a tab ("\t").
	  * @return String
	  */
	 public String getRuptureMetadata(){
		 //rupture Metadata
		 String ruptureMetadata;
		 ruptureMetadata = (float)mag + "\t";
		 ruptureMetadata += (float)probability + "\t";
		 ruptureMetadata += (float)aveRake + "\t";
		 ruptureMetadata += (float)ruptureSurface.getAveDip();
		 return ruptureMetadata;

	 }

	 /**
	  * Clones the eqk rupture and returns the new cloned object
	  * @return
	  */
	 public Object clone() {
		 ProbEqkRupture eqkRuptureClone=new ProbEqkRupture();
		 eqkRuptureClone.setAveRake(this.aveRake);
		 eqkRuptureClone.setMag(this.mag);
		 eqkRuptureClone.setRuptureSurface(this.ruptureSurface);
		 eqkRuptureClone.setHypocenterLocation(this.hypocenterLocation);
		 eqkRuptureClone.setProbability(this.probability);
		 return eqkRuptureClone;
	 }






}
