package org.opensha.sha.earthquake.rupForecastImpl;

import java.util.ArrayList;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.MagScalingRelationship;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.griddedForecast.HypoMagFreqDistAtLoc;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * <p>Title: PointEqkSource </p>
 * <p>Description: This converts a point source to a finite-surface source as described in the constructors.
 *  If given the magScalingRel is a mag-length relationship, then the rupture length is simply that
 *  computed from mag.  If magScalingRel is a mag-area relationship, then rupture length is the 
 *  computed area divided by the down-dip width, where the latter is computed as: 
 *  (lowerSeisDepth-aveRupTopVersusMag(mag))/sin(dip).  The strike is either fixed, chosen randomly, or 
 *  multiple strikes are applied as a "spoked" source.  Note that the latter works properly for vertically dipping
 *  faults, but only half the range of strike/dip directions are represented for dipping faults (need to double the 
 *  number of strikes for these).  Also might want to center the surface projection of dipping faults for the given 
 *  location</p>
 * </UL><p>
 *
 *
 * @author Edward Field
 * @version 1.0
 */
public class PointToFiniteSource extends ProbEqkSource implements java.io.Serializable{

	//for Debug purposes
	protected static String  C = new String("PointToFiniteSource");
	protected static String NAME = "Point-to-Finite Source";
	protected boolean D = false;

	protected ArrayList<ProbEqkRupture> probEqkRuptureList;
	protected ArrayList<Double> rates;

	protected Location location;
	protected double maxLength = 0;
	int numRuptures;
	
	IncrementalMagFreqDist[] magFreqDists;
	FocalMechanism[] focalMechanisms;
	ArbitrarilyDiscretizedFunc aveRupTopVersusMag;
	MagScalingRelationship magScalingRel;
	double lowerSeisDepth;
	double duration = Double.NaN;
	double minMag = Double.NaN;  
	int numStrikes=-1;
	double firstStrike;
	boolean lineSource; // whether it's just a line source or has down-dip extent
	
	// no arg constructor (for subclasses)
	public PointToFiniteSource() {}

	
	/**
	 * This, the full-flexibility constructor, applies a spoked source where several strikes are applied with even spacing 
	 * in azimuth, where numStrikes defines the number of strikes applied (e.g., numStrikes=2 would be a cross hair) 
	 * and firstStrike defines the azimuth of the first one (e.g., firstStrike=0 with numStrikes=2 would be a 
	 * cross-hair source that is perfectly aligned NS and EW). As noted above, for non-vertically dipping faults only half 
	 * the dip directions are thus far represented (would need to double the number of spokes for such faults)
	 * 
	 * @param hypoMagFreqDistAtLoc - this specifies a location and a list of MFD/FocalMech pairs
	 * @param aveRupTopVersusMag - specifies upper depth as function of mag (make sure the x-axis range is wide enough for the MFDs)
	 * @param magScalingRel - used to compute rupture length
	 * @param lowerSeisDepth (km)
	 * @param duration (yrs)
	 * @param minMag - MFD mags below this value are ignored (zero rates)
	 * @param numStrikes - num strikes over 180 degrees (e.g., 2 for cross hairs).  Set as -1 to use that in focalMech, or if the latter is NaN, a random one is applied
	 * @param firstStrike - ignored if numStrikes = -1
	 * @param lineSource - if true, upper and lower seis depth are the same in surface applied (no down-dip extent)
	 */
	public PointToFiniteSource(HypoMagFreqDistAtLoc hypoMagFreqDistAtLoc,
			ArbitrarilyDiscretizedFunc aveRupTopVersusMag,
			MagScalingRelationship magScalingRel,double lowerSeisDepth, 
			double duration, double minMag, int numStrikes, double firstStrike, boolean lineSource){

		this.magFreqDists = hypoMagFreqDistAtLoc.getMagFreqDistList();
		this.focalMechanisms = hypoMagFreqDistAtLoc.getFocalMechanismList();
		this.aveRupTopVersusMag = aveRupTopVersusMag;
		this.magScalingRel = magScalingRel;
		this.lowerSeisDepth = lowerSeisDepth;
		this.duration = duration;
		this.minMag = minMag;
		this.numStrikes = numStrikes;
		this.firstStrike = firstStrike;
		this.lineSource = lineSource;

		this.isPoissonian = true;
		
		// Compute stuff needed for the getMinDistance(Site) method, so this can be computed before ruptures are generated
		this.location = hypoMagFreqDistAtLoc.getLocation();
		this.maxLength = computeMaxLength();
		
		numRuptures = this.computeNumRuptures();
	}
	

	
	/**
	 * This forces both a line source and applies the strike in the FocalMech, or a random one if
	 * the latter is NaN. 
	 */
	public PointToFiniteSource(HypoMagFreqDistAtLoc hypoMagFreqDistAtLoc, ArbitrarilyDiscretizedFunc aveRupTopVersusMag,
			MagScalingRelationship magScalingRel,double lowerSeisDepth, double duration, double minMag){
		// invoke other constructor with numStrikes=-1
		this(hypoMagFreqDistAtLoc,aveRupTopVersusMag,magScalingRel,lowerSeisDepth, 
				duration, minMag,-1,Double.NaN, true);
	}
	
	
	/**
	 * This applies the strike in the FocalMech, or a random one if it's NaN.  This allows both line
	 * source or full down-dip extent 
	 */
	public PointToFiniteSource(HypoMagFreqDistAtLoc hypoMagFreqDistAtLoc,
			ArbitrarilyDiscretizedFunc aveRupTopVersusMag,
			MagScalingRelationship magScalingRel,double lowerSeisDepth, 
			double duration, double minMag, boolean lineSource){
		// invoke other constructor with numStrikes=-1
		this(hypoMagFreqDistAtLoc,aveRupTopVersusMag,magScalingRel,lowerSeisDepth, 
				duration, minMag,-1, Double.NaN, lineSource);
	}
	
	/**
	 *  This constructor avoids the HypoMagFreqDistAtLoc object, sets a constant upper seis depth,
	 *  and applies the strike in the focalMech (or random one if this is Double.NaN).
	 * 
	 * @param magDist - MFD to apply
	 * @param loc - location (e.g., center of grid cell)
	 * @param focalMech
	 * @param upperSeisDepth (km)
	 * @param magScalingRel 
	 * @param lowerSeisDepth (km)
	 * @param duration (yrs)
	 * @param minMag - MFD mags below this value are ignored
	 * @param lineSource
	 */
	public PointToFiniteSource(IncrementalMagFreqDist magDist, Location loc, FocalMechanism focalMech,
			double upperSeisDepth, MagScalingRelationship magScalingRel,double lowerSeisDepth, 
			double duration, double minMag, boolean lineSource){
		
		HypoMagFreqDistAtLoc hypoMagFreqDistAtLoc = new HypoMagFreqDistAtLoc(magDist, loc, focalMech);
		this.magFreqDists = hypoMagFreqDistAtLoc.getMagFreqDistList();
		this.focalMechanisms = hypoMagFreqDistAtLoc.getFocalMechanismList();
		this.aveRupTopVersusMag = new ArbitrarilyDiscretizedFunc();
		aveRupTopVersusMag.set(0.0, upperSeisDepth);
		aveRupTopVersusMag.set(10.0, upperSeisDepth);
		this.magScalingRel = magScalingRel;
		this.lowerSeisDepth = lowerSeisDepth;
		this.duration = duration;
		this.minMag = minMag;
		this.numStrikes = -1;
		this.firstStrike = Double.NaN;
		this.lineSource = lineSource;

		this.isPoissonian = true;
		
		// Compute stuff needed for the getMinDistance(Site) method, so this can be computed before ruptures are generated
		this.location = hypoMagFreqDistAtLoc.getLocation();
		this.maxLength = computeMaxLength();
		
		numRuptures = computeNumRuptures();
	}
	
		
	
	
	protected double computeMaxLength() {
		double max = 0;
		for (int i=0; i<magFreqDists.length; i++) {
			double dip = focalMechanisms[i].getDip();
			double mag = magFreqDists[i].getMaxMagWithNonZeroRate();
			double length = getRupLength(mag, aveRupTopVersusMag.getClosestYtoX(mag), lowerSeisDepth, dip, magScalingRel);
			if(length>max) max = length;
		}		
		return max;
	}
	
	
	protected int computeNumRuptures() {
		int num=0;
		for(int i=0;i<magFreqDists.length;i++ ) {
			IncrementalMagFreqDist mfd = magFreqDists[i];
			double min = minMag;
			if(min<mfd.getX(0)) min = mfd.getX(0);
			for(int m=mfd.getXIndex(min);m<mfd.size();m++) {
				if(mfd.getY(m)>0) num +=1;
			}
		}
		if(numStrikes != -1) num *= numStrikes;
		return num;
	}


	private void mkAllRuptures() {
		
		probEqkRuptureList = new ArrayList<ProbEqkRupture>();
		rates = new ArrayList<Double>();
		
//		System.out.println((float)rupLength+"\t"+(float)mag+"\t"+(float)lowerSeisDepth+"\t"+(float)dip+"\t"+magScalingRel);

		if(numStrikes == -1) { // random or applied strike
			for (int i=0; i<magFreqDists.length; i++) {
				mkAndAddRuptures(location, magFreqDists[i], focalMechanisms[i], aveRupTopVersusMag, 
						magScalingRel, lowerSeisDepth, duration, minMag, 1.0);
			}			
		}
		else {
			// set the strikes
			double deltaStrike = 180/numStrikes;
			double[] strike = new double[numStrikes];
			for(int n=0;n<numStrikes;n++)
				strike[n]=firstStrike+n*deltaStrike;

			for (int i=0; i<magFreqDists.length; i++) {
				FocalMechanism focalMech = focalMechanisms[i].copy(); // COPY THIS
				for(int s=0;s<numStrikes;s++) {
					focalMech.setStrike(strike[s]);
					double weight = 1.0/numStrikes;
					mkAndAddRuptures(location, magFreqDists[i], focalMech, aveRupTopVersusMag, 
							magScalingRel, lowerSeisDepth, duration, minMag,weight);			  
				}
			}			
		}
		
		if(numRuptures != probEqkRuptureList.size())
			throw new RuntimeException("Error in computing number of ruptures");
	}


	/**
	 * This creates the ruptures and adds them to the list for the given inputs
	 * @param magFreqDist
	 * @param focalMech
	 * @param aveRupTopVersusMag
	 * @param defaultHypoDepth
	 * @param magScalingRel
	 * @param lowerSeisDepth
	 * @param duration
	 * @param minMag
	 * @param weight
	 */
	protected void mkAndAddRuptures(Location location, IncrementalMagFreqDist magFreqDist, 
			FocalMechanism focalMech, ArbitrarilyDiscretizedFunc aveRupTopVersusMag, 
			MagScalingRelationship magScalingRel,
			double lowerSeisDepth, double duration, double minMag, double weight) {

		double dip = focalMech.getDip();
		double strike = focalMech.getStrike();
		boolean isStrikeRandom=false;
		if(Double.isNaN(strike)) {
			isStrikeRandom=true;
		}

		for (int m=0; m<magFreqDist.size(); m++){
			double mag = magFreqDist.getX(m);
			double rate = magFreqDist.getY(m);
			if(rate > 0 && mag >= minMag){
				// set depth of rupture
				//Location loc = location.copy();
				double prob = 1-Math.exp(-rate*weight*duration);
				double upperDepth;
				if(mag < aveRupTopVersusMag.getMinX() || mag > aveRupTopVersusMag.getMaxX())
					throw new RuntimeException("aveRupTopVersusMag x-axis range not wide enough");
				else
					upperDepth = aveRupTopVersusMag.getClosestYtoX(mag);
				Location loc = new Location(
						location.getLatitude(),
						location.getLongitude(),
						upperDepth);
				// set rupture length
				double rupLength = getRupLength(mag, aveRupTopVersusMag.getClosestYtoX(mag), lowerSeisDepth, dip, magScalingRel);

//				if(rupLength>maxLength) maxLength=rupLength; 

				// get random strike if needed
				if(isStrikeRandom) {
					strike = (Math.random()-0.5)*360.0;	// get a random strike between -180 and 180
					//System.out.println(strike);
				}
//				LocationVector dir = new LocationVector(0.0,rupLength/2,strike,Double.NaN);
				LocationVector dir = new LocationVector(strike, rupLength/2, 0.0);
				Location loc1 = LocationUtils.location(loc,dir);
				dir.setAzimuth(strike-180);
				Location loc2 = LocationUtils.location(loc,dir);
				FaultTrace fltTrace = new FaultTrace(null);
				fltTrace.add(loc2); // this order preserves strike and right hand rule
				fltTrace.add(loc1);

				// make the surface
				StirlingGriddedSurface surf;
				if(lineSource)
					surf = new StirlingGriddedSurface(fltTrace,dip,upperDepth,upperDepth,1.0);
				else
					surf = new StirlingGriddedSurface(fltTrace,dip,upperDepth,lowerSeisDepth,1.0);

				ProbEqkRupture rupture = new ProbEqkRupture();
				rupture.setMag(mag);
				rupture.setAveRake(focalMech.getRake());
				rupture.setRuptureSurface(surf);
				rupture.setProbability(prob);

				if (D) System.out.println("\trupLength\t"+rupLength+"\tstrike\t"+strike+"\tdip\t"+focalMech.getDip()+
						"\trake\t"+rupture.getAveRake()+"\tmag\t"+(float)mag+"\trate\t"+(float)rate+"\tprob\t"+
						(float)prob+"\tweight\t"+(float)weight);

				// add the rupture to the list and save the rate in case the duration changes
				probEqkRuptureList.add(rupture);
				rates.add(rate*weight);
			}
		}
	}
	

	/**
	 * This computes the rupture length.  If magScalingRel is a mag-length relationship, then
	 * the length from mag is returned.  If magScalingRel is a mag-area relationship, then
	 * length returned is the computed area divided by the down-dip width, where the latter
	 * is computed as: (lowerSeisDepth-aveRupTopVersusMag(mag))/sin(dip).
	 * @param mag
	 * @param aveRupTopVersusMag
	 * @param lowerSeisDepth
	 * @param dip
	 * @param magScalingRel
	 * @return
	 */
	public static double getRupLength(double mag, double upperSeisDepth, 
			double lowerSeisDepth, double dip, MagScalingRelationship magScalingRel) {
		
		double rupLength;
		if(magScalingRel instanceof MagAreaRelationship) {
			double ddw = (upperSeisDepth - lowerSeisDepth)/Math.sin(dip*Math.PI/180);
			double area = magScalingRel.getMedianScale(mag);
			if(ddw > Math.sqrt(area))
				rupLength = ddw;
			else
				rupLength = area/ddw;
		}
		else if (magScalingRel instanceof MagLengthRelationship) {
			rupLength = magScalingRel.getMedianScale(mag);
		}
		else throw new RuntimeException("bad type of MagScalingRelationship: "+magScalingRel);
		
//		System.out.println((float)rupLength+"\t"+(float)mag+"\t"+(float)lowerSeisDepth+"\t"+(float)dip+"\t"+magScalingRel);

		return rupLength;
	}



	/**
	 * It returns a list of all the locations which make up the surface for this
	 * source.
	 */
	public LocationList getAllSourceLocs() {
		LocationList locList = new LocationList();
		for(int r=0;r< getNumRuptures(); r++) {
			locList.addAll(probEqkRuptureList.get(r).getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface());
		}
		return locList;
	}

	
	/**
	 * don't know what to return here (deprecate this method?)
	 */
	public AbstractEvenlyGriddedSurface getSourceSurface() { 
		throw new RuntimeException("Method not supported");
	}



	/**
	 * @return the number of rutures (equals number of mags with non-zero rates)
	 */
	public int getNumRuptures() {
		return numRuptures;
	}


	/**
	 * This makes and returns the nth probEqkRupture for this source.
	 */
	public ProbEqkRupture getRupture(int nthRupture){
		if(probEqkRuptureList == null) mkAllRuptures();
		return probEqkRuptureList.get(nthRupture);
	}


	/**
	 * This sets the duration used in computing Poisson probabilities.  This assumes
	 * the same units as in the magFreqDist rates.  
	 * @param duration
	 */
	public void setDuration(double duration) {
		this.duration=duration;
		for(int i=0; i<probEqkRuptureList.size();i++)
			probEqkRuptureList.get(i).setProbability(1-Math.exp(-rates.get(i)*duration));
	}


	/**
	 * This gets the duration used in computing Poisson probabilities (it may be NaN
	 * if the source is not Poissonian).
	 * @param duration
	 */
	public double getDuration() {
		return duration;
	}



	/**
	 * This gets the minimum magnitude to be considered from the mag-freq dist (those
	 * below are ignored in making the source).  This will be NaN if the source is not
	 * Poissonian.
	 * @return minMag
	 */
	public double getMinMag(){
		return minMag;
	}


	/**
	 * This returns the shortest horizontal dist to the point source (minus half the length of the longest rupture).
	 * @param site
	 * @return minimum distance
	 */
	public  double getMinDistance(Site site) {
		return LocationUtils.horzDistance(site.getLocation(), location) - maxLength/2;
	}

	/**
	 * get the name of this class
	 *
	 * @return
	 */
	public String getName() {
		return NAME;
	}
}
