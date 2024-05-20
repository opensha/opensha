package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis;

import static java.lang.Math.min;
import static java.lang.Math.sin;

import java.util.Iterator;

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.oldClasses.UCERF2_Final_RelativeLocation;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.FrankelGriddedSurface;
import org.opensha.sha.faultSurface.GriddedSubsetSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * <p>Title: Point2Vert_FaultPoisSource </p>
 * <p>Description: For a given Location, IncrementalMagFreqDist (of Poissonian
 * rates), MagLengthRelationship, and duration, this creates a vertically dipping,
 * ProbEqkRupture for each magnitude (zero rates are not filtered out!).  Each finite
 * rupture is centered on the given Location.  A user-defined strike will be used if given,
 * otherwise an random stike will be computed and applied, or a cross-hair source can be
 * applied (two perpendicular faults).  One can also specify a magCutOff (magnitudes less 
 * than or equal to this will be treated as point sources). This assumes that the duration
 * units are the same as those for the rates in the IncrementalMagFreqDist.</p>
 *  * @author Edward Field
 * @date March 24, 2004
 * @version 1.0
 */

public class Point2Vert_FaultPoisSource extends ProbEqkSource implements java.io.Serializable{


	//for Debug purposes
	private static String  C = new String("Point2Vert_SS_FaultPoisSource");
	private String name = C;
	private boolean D = false;

	private IncrementalMagFreqDist magFreqDist;
	private static final double aveDip=90;
	private double fracStrikeSlip=0.0;
	private double fracNormal=0.0;
	private double fracReverse=0.0;
	private double duration;
	private MagLengthRelationship magLengthRelationship;
	private double magCutOff;
	private FrankelGriddedSurface finiteFaultSurface1;
	private FrankelGriddedSurface finiteFaultSurface2; // this is used if isCrossHair is true
	private double strike; // only used when isCrossHair is false
	private Location loc;

	private int numRuptures;
	private int ss_firstIndex;
	private int ss_lastIndex;
	private int n_firstIndex;
	private int n_lastIndex;
	private int rv_firstIndex;
	private int rv_lastIndex;

	/**
	 * If Crosshair is set to true, we have 2 perpendicular faults 
	 * (one with 0 strike and other with strike of 90 degrees)
	 */
	private boolean isCrossHair = false; 


	// to hold the non-zero mags, rates, and rupture surfaces
	//  ArrayList mags, rates, rupSurfaces;

	/**
	 * The Full Constructor
	 * @param loc - the Location of the point source
	 * @param magFreqDist - the mag-freq-dist for the point source
	 * @param magLengthRelationship - A relationship for computing length from magnitude
	 * @param strike - the strike of the finite SS fault
	 * @param duration - the forecast duration
	 * @param magCutOff - below (and equal) to this value a PointSurface will be applied
	 * @param fracStrikeSlip - 
	 * @param fracNormal - 
	 * @param fracReverse - 
	 */
	public Point2Vert_FaultPoisSource(Location loc, IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double strike, double duration, double magCutOff
			,double fracStrikeSlip, double fracNormal,double fracReverse){
		this.magCutOff = magCutOff;

		if(D) {
			System.out.println("magCutOff="+magCutOff);
			System.out.println("num pts in magFreqDist="+magFreqDist.size());
		}

		// set the mags, rates, and rupture surfaces
		setAll(loc,magFreqDist,magLengthRelationship,strike,duration, fracStrikeSlip,
				fracNormal, fracReverse);
	}


	/**
	 * The Constructor for the case where either a random strike is computed and applied, or
	 * a cross-hair source is applied (rather than assigned)
	 * @param loc - the Location of the point source
	 * @param magFreqDist - the mag-freq-dist for the point source
	 * @param magLengthRelationship - A relationship for computing length from magnitude
	 * @param duration - the forecast duration
	 * @param magCutOff - below (and equal) to this value a PointSurface will be applied
	 * @param fracStrikeSlip - 
	 * @param fracNormal - 
	 * @param fracReverse - 
	 * @param isCrossHair - tells whether to apply random strike or cross-hair source
	 */
	public Point2Vert_FaultPoisSource(Location loc, IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double duration, double magCutOff,double fracStrikeSlip,
			double fracNormal,double fracReverse, boolean isCrossHair){
		this.magCutOff = magCutOff;
		// whether to simulate it as 2 perpendicular faults
		this.isCrossHair = isCrossHair;

		// set the mags, rates, and rupture surfaces
		setAll(loc,magFreqDist,magLengthRelationship,duration, fracStrikeSlip, fracNormal, fracReverse);

	}

	/**
	 * This computes a random strike and then builds the list of magnitudes,
	 * rates, and finite-rupture surfaces using the given MagLenthRelationship.
	 * This also sets the duration.
	 * @param loc
	 * @param magFreqDist
	 * @param magLengthRelationship
	 * @param duration
	 * @param fracStrikeSlip - 
	 * @param fracNormal - 
	 * @param fracReverse - 
	 */
	public void setAll(Location loc, IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double duration,double fracStrikeSlip,
			double fracNormal,double fracReverse) {

		// If isCrosssHair is true, this strike is only used for point sources. Finite sources get strike of 0 and 90
		double strike = (Math.random()-0.5)*180.0;
		if (strike < 0.0) strike +=360;
		// System.out.println(C+" random strike = "+strike);
		setAll(loc,magFreqDist,magLengthRelationship,strike,duration,fracStrikeSlip, fracNormal, fracReverse);
	}


	/**
	 * This builds the list of magnitudes, rates, and finite-rupture surfaces using
	 * the given strike and MagLenthRelationship.  This also sets the duration.
	 *
	 * @param loc
	 * @param magFreqDist
	 * @param magLengthRelationship
	 * @param strike
	 * @param duration
	 * @param fracStrikeSlip - 
	 * @param fracNormal - 
	 * @param fracReverse - 
	 * 
	 */
	public void setAll(Location loc, IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double strike, double duration,double fracStrikeSlip,
			double fracNormal,double fracReverse) {

		if(D) System.out.println("duration="+duration);
		if(D) System.out.println("strike="+strike);
		this.duration = duration;
		this.loc = loc;
		this.magFreqDist = magFreqDist;
		this.magLengthRelationship = magLengthRelationship;
		this.fracNormal =fracNormal;
		this.fracReverse=fracReverse;
		this.fracStrikeSlip=fracStrikeSlip;

		double sum = fracNormal+fracReverse+fracStrikeSlip;
		if(Math.abs(1-sum) > 1e-5)
			throw new RuntimeException("fractions must sum to 1.0: "+sum);

		int numMags = magFreqDist.size();

		ss_firstIndex = -1;
		ss_lastIndex = -1;
		n_firstIndex = -1;
		n_lastIndex = -1;
		rv_firstIndex = -1;
		rv_lastIndex = -1;

		numRuptures = 0;
		if(fracStrikeSlip>0) {
			ss_firstIndex = 0;
			ss_lastIndex = numMags-1;
			numRuptures +=numMags;
		}
		if(fracNormal>0) {
			n_firstIndex = numRuptures;
			n_lastIndex = n_firstIndex+numMags-1;
			numRuptures +=numMags;
		}
		if(fracReverse>0) {
			rv_firstIndex = numRuptures;
			rv_lastIndex = rv_firstIndex+numMags-1;
			numRuptures +=numMags;    	
		}

		if(this.isCrossHair) numRuptures+=numRuptures;
	}
	
	private void checkInitSurfaces() {
		double depth = 1.0;

		double maxMag = magFreqDist.getX(magFreqDist.size()-1);
		// make finite source if necessary
		if(maxMag > magCutOff && finiteFaultSurface1 == null) {
			Location loc1, loc2;
			LocationVector dir;
			double halfLength = magLengthRelationship.getMedianLength(maxMag)/2.0;
			if(this.isCrossHair) strike = 0;
			//      loc1 = LocationUtils.getLocation(loc,new LocationVector(0.0,halfLength,strike,Double.NaN));
			loc1 = LocationUtils.location(loc,
					new LocationVector(strike, halfLength, 0.0));
			dir = LocationUtils.vector(loc1,loc);
			dir.setHorzDistance(dir.getHorzDistance()*2.0);
			loc2 = LocationUtils.location(loc1,dir);
			FaultTrace fault = new FaultTrace("");
			fault.add(loc1);
			fault.add(loc2);
			finiteFaultSurface1 = new FrankelGriddedSurface(fault,aveDip,depth,depth,1.0);

			// Make second surface for cross Hair option
			if(this.isCrossHair) {
				strike = 90;
				//    	  loc1 = LocationUtils.getLocation(loc,new LocationVector(0.0,halfLength,strike,Double.NaN));
				loc1 = LocationUtils.location(loc,
						new LocationVector(strike, halfLength, 0.0));
				dir = LocationUtils.vector(loc1,loc);
				dir.setHorzDistance(dir.getHorzDistance()*2.0);
				loc2 = LocationUtils.location(loc1,dir);
				fault = new FaultTrace("");
				fault.add(loc1);
				fault.add(loc2);
				finiteFaultSurface2 = new FrankelGriddedSurface(fault,aveDip,depth,depth,1.0);
			}

		}
	}

	/**
	 * It returns a list of all the locations which make up the surface for this
	 * source.
	 *
	 * @return LocationList - List of all the locations which constitute the surface
	 * of this source
	 */
	public LocationList getAllSourceLocs() {
		LocationList locList = new LocationList();
		
		checkInitSurfaces();

		if(this.finiteFaultSurface1!=null) { 
			locList = finiteFaultSurface1.getEvenlyDiscritizedListOfLocsOnSurface();
		}

		if(this.finiteFaultSurface2!=null) { 
			Iterator<Location> it = finiteFaultSurface2.getLocationsIterator();
			while(it.hasNext()) locList.add(it.next());
		}

		locList.add(loc);
		return locList;
	}


	/**
	 * This approximates the source as a point at 1.0 km depth.
	 */
	public RuptureSurface getSourceSurface() { 
		PointSurface newPtSurface = new PointSurface(loc);
		newPtSurface.setDepth(1.0);
		return newPtSurface;
	}



	/**
	 * @return the number of rutures (equals number of mags with non-zero rates)
	 */
	public int getNumRuptures() {
		return numRuptures;
	}

	private int getMagIndex(int nthRupture) {
		if(this.isCrossHair && nthRupture>=numRuptures/2)
			nthRupture -= numRuptures/2;
		if(nthRupture >= ss_firstIndex && nthRupture <= ss_lastIndex)
			return nthRupture-ss_firstIndex;
		if(nthRupture >= n_firstIndex && nthRupture <= n_lastIndex)
			return nthRupture-n_firstIndex;
		if(nthRupture >= rv_firstIndex && nthRupture <= rv_lastIndex)
			return nthRupture-rv_firstIndex;
		throw new IllegalStateException("bad rupture index: "+nthRupture+", size="+getNumRuptures());
	}
	
	public double getMag(int nthRupture) {
		return magFreqDist.getX(getMagIndex(nthRupture));
	}

	public double getAveRake(int nthRupture) {
		if(this.isCrossHair && nthRupture>=numRuptures/2)
			nthRupture -= numRuptures/2;
		if(nthRupture >= ss_firstIndex && nthRupture <= ss_lastIndex)
			return 0d;
		if(nthRupture >= n_firstIndex && nthRupture <= n_lastIndex)
			return -90;
		if(nthRupture >= rv_firstIndex && nthRupture <= rv_lastIndex)
			return 90;
		throw new IllegalStateException("bad rupture index: "+nthRupture+", size="+getNumRuptures());
	}

	public double getDip(int nthRupture) {
		if(this.isCrossHair && nthRupture>=numRuptures/2)
			nthRupture -= numRuptures/2;
		if(nthRupture >= ss_firstIndex && nthRupture <= ss_lastIndex)
			return 90d;
		if(nthRupture >= n_firstIndex && nthRupture <= n_lastIndex)
			return 50;
		if(nthRupture >= rv_firstIndex && nthRupture <= rv_lastIndex)
			return 50;
		throw new IllegalStateException("bad rupture index: "+nthRupture+", size="+getNumRuptures());
	}

	public double getFraction(int nthRupture) {
		if(this.isCrossHair && nthRupture>=numRuptures/2)
			nthRupture -= numRuptures/2;
		if(nthRupture >= ss_firstIndex && nthRupture <= ss_lastIndex)
			return fracStrikeSlip;
		if(nthRupture >= n_firstIndex && nthRupture <= n_lastIndex)
			return fracNormal;
		if(nthRupture >= rv_firstIndex && nthRupture <= rv_lastIndex)
			return fracReverse;
		throw new IllegalStateException("bad rupture index: "+nthRupture+", size="+getNumRuptures());
	}

	/**
	 * This makes and returns the nth probEqkRupture for this source.
	 */
	public ProbEqkRupture getRupture(int nthRupture){
		boolean secondSurface = this.isCrossHair && nthRupture>=numRuptures/2;
		double fraction = getFraction(nthRupture);
		double rake = getAveRake(nthRupture);
		double dip = getDip(nthRupture);
		int magIndex = getMagIndex(nthRupture);

		// set the magnitude
		double mag = magFreqDist.getX(magIndex);
		// set the depth according to magnitude
		double depth;
		if(mag<=6.5) depth = 5.0;
		else depth = 1.0;
		ProbEqkRupture probEqkRupture = new ProbEqkRupture();
		probEqkRupture.setMag(mag);
		probEqkRupture.setAveRake(rake);
		if(this.isCrossHair) fraction/=2;
		// compute and set the probability
		double prob = 1 - Math.exp(-duration*fraction*magFreqDist.getY(magIndex));
		probEqkRupture.setProbability(prob);

		// set the rupture surface
		if(mag <= this.magCutOff) { // set the point surface
			PointSurface ptSurface = new PointSurface(
					Location.backwardsCompatible(loc.getLatitude(), loc.getLongitude(), depth));
			ptSurface.setAveStrike(strike);
			ptSurface.setAveDip(dip);
			double width = calcWidth(mag, depth, dip);
			ptSurface.setAveWidth(width);
			probEqkRupture.setRuptureSurface(ptSurface);
		}
		else { // set finite surface
			FrankelGriddedSurface finiteFault;
			
			checkInitSurfaces();

			// set the appropriate surface in case of CrossHair option
			if(secondSurface) finiteFault = this.finiteFaultSurface2;
			else finiteFault  = this.finiteFaultSurface1;
			
			if(finiteFault.getLocation(0, 0).getDepth()!=depth) {
				finiteFault = finiteFault.deepCopyOverrideDepth(depth);
			}
			
			if(magIndex == magFreqDist.size()-1) {
				probEqkRupture.setRuptureSurface(finiteFault);
			}
			else {
				double rupLen = magLengthRelationship.getMedianLength(mag);
				double startPoint = (double)finiteFault.getNumCols()/2.0 - 0.5 - rupLen/2.0;
				GriddedSubsetSurface rupSurf = new GriddedSubsetSurface(1,Math.round((float)rupLen+1),
						0,Math.round((float)startPoint),
						finiteFault);
				probEqkRupture.setRuptureSurface(rupSurf);
			}
		}

		// return the ProbEqkRupture
		return probEqkRupture;
	}


	/**
	 * This sets the duration used in computing Poisson probabilities.  This assumes
	 * the same units as in the magFreqDist rates.
	 * @param duration
	 */
	public void setDuration(double duration) {
		this.duration=duration;
	}


	/**
	 * This gets the duration used in computing Poisson probabilities
	 * @param duration
	 */
	public double getDuration() {
		return duration;
	}


	/**
	 * This returns the shortest horizontal dist to the point source.
	 * @param site
	 * @return minimum distance
	 */
	public  double getMinDistance(Site site) {

		/*
      // get the largest rupture surface (the last one)
      GriddedSurfaceAPI surf = (GriddedSurfaceAPI) rupSurfaces.get(rupSurfaces.size()-1);

      double tempMin, min = Double.MAX_VALUE;
      int nCols = surf.getNumCols();

      // find the minimum to the ends and the center)
      tempMin = LocationUtils.getHorzDistance(site.getLocation(),surf.getLocation(0,0));
      if(tempMin < min) min = tempMin;
      tempMin = LocationUtils.getHorzDistance(site.getLocation(),surf.getLocation(0,(int)nCols/2));
      if(tempMin < min) min = tempMin;
      tempMin = LocationUtils.getHorzDistance(site.getLocation(),surf.getLocation(0,nCols-1));
      if(tempMin < min) min = tempMin;
      return min;
		 */
		return UCERF2_Final_RelativeLocation.getHorzDistance(
				site.getLocation(),loc);
	}

	/**
	 * get the name of this class
	 *
	 * @return
	 */
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name=name;
	}


	// this is temporary for testing purposes
	public static void main(String[] args) {
		Location loc = new Location(34,-118,0);
		GutenbergRichterMagFreqDist dist = new GutenbergRichterMagFreqDist(5,16,0.2,1e17,0.9);
		WC1994_MagLengthRelationship wc_rel = new WC1994_MagLengthRelationship();
		double fracStrikeSlip = 1.0/3;
		double fracNormal = 1.0/3;
		double fracReverse = 1.0/3;
		double duration = 1;

		//    Point2Vert_SS_FaultPoisSource src = new Point2Vert_SS_FaultPoisSource(loc, dist,
		//                                       wc_rel,45, 1.0, 6.0, 5.0);
		Point2Vert_FaultPoisSource src = new Point2Vert_FaultPoisSource(loc, dist,
				wc_rel, duration, 6.0,fracStrikeSlip,fracNormal,fracReverse, false);

		System.out.println("num rups ="+src.getNumRuptures()+"\ttotProb="+src.computeTotalProb());
		ProbEqkRupture rup;
		Location loc1, loc2;
		double length, aveLat, aveLon;
		System.out.println("Rupture mags and end locs:");
		for(int r=0; r<src.getNumRuptures();r++) {
			rup = src.getRupture(r);
			System.out.println(r+"\t"+(float)rup.getMag()+"\t"+rup.getAveRake()+"\t"+rup.getProbability());

			/*
      loc1 = rup.getRuptureSurface().getLocation(0,0);
      loc2 = rup.getRuptureSurface().getLocation(0,rup.getRuptureSurface().getNumCols()-1);
      length = LocationUtils.getHorzDistance(loc1,loc2);
      aveLat = (loc1.getLatitude()+loc2.getLatitude())/2;
      aveLon = (loc1.getLongitude()+loc2.getLongitude())/2;
//      System.out.println("\t"+(float)rup.getMag()+"\t"+loc1+"\t"+loc2);
      System.out.println("\t"+(float)rup.getMag()+
                         "\tlen1="+(float)wc_rel.getMedianLength(rup.getMag())+
                         "\tlen2="+(float)length+"\taveLat="+(float)aveLat+
                         "\taveLon="+(float)aveLon);
			 */
		}
	}
	
	public boolean isCrossHair() {
		return isCrossHair;
	}
	
	
	private static final MagLengthRelationship WC94 = 
			new WC1994_MagLengthRelationship();
	
	public Location getLocation() {
		return loc;
	}

	/*
	 * Returns the minimum of the aspect ratio width (based on WC94) length
	 * and the allowable down-dip width.
	 */
	private double calcWidth(double mag, double depth, double dip) {
		double length = WC94.getMedianLength(mag);
		double aspectWidth = length / 1.5;
		double ddWidth = (14.0 - depth) / sin(dip * GeoTools.TO_RAD);
		return min(aspectWidth, ddWidth);
	}

}
