package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis;

import static java.lang.Math.min;
import static java.lang.Math.sin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.PointSource.PoissonPointSource;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.oldClasses.UCERF2_Final_RelativeLocation;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.FrankelGriddedSurface;
import org.opensha.sha.faultSurface.GriddedSubsetSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;

import com.google.common.base.Preconditions;

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

public class Point2Vert_FaultPoisSource extends PoissonPointSource implements java.io.Serializable{

	/*
	 * if mag<magCutOff and isCrosshair, should you have 2 identical ruptures (with half rates) or a single rupture?
	 * 
	 * true: original UCERF2 configuration
	 * false: faster and identical hazard (fewer ruptures) 
	 */
	public static boolean DUPLICATE_PT_SRC_FOR_CROSSHAIR = true;

	//for Debug purposes
	private static String  C = new String("Point2Vert_FaultPoisSource");
	private String name = C;
	private boolean D = false;

	private IncrementalMagFreqDist magFreqDist;
	private static final double aveDip=90;
	private MagLengthRelationship magLengthRelationship;
	private double magCutOff;
	private FrankelGriddedSurface finiteFaultSurface1;
	private FrankelGriddedSurface finiteFaultSurface2; // this is used if isCrossHair is true
	private double strike; // only used when isCrossHair is false
	private Location loc;

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
	 * @param distance corrections
	 */
	public Point2Vert_FaultPoisSource(Location loc, IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double strike, double duration, double magCutOff,
			double fracStrikeSlip, double fracNormal, double fracReverse,
			WeightedList<PointSourceDistanceCorrection> distCorrs) {
		super(loc, TECTONIC_REGION_TYPE_DEFAULT, duration, null, distCorrs); // TODO: dist corrs
		this.magCutOff = magCutOff;

		if(D) {
			System.out.println("magCutOff="+magCutOff);
			System.out.println("num pts in magFreqDist="+magFreqDist.size());
		}

		// set the mags, rates, and rupture surfaces
		setAll(loc, magFreqDist, magLengthRelationship, strike, fracStrikeSlip,
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
	 * @param distance corrections
	 */
	public Point2Vert_FaultPoisSource(Location loc, IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double duration, double magCutOff,double fracStrikeSlip,
			double fracNormal, double fracReverse, boolean isCrossHair,
			WeightedList<PointSourceDistanceCorrection> distCorrs) {
		super(loc, TECTONIC_REGION_TYPE_DEFAULT, duration, null, distCorrs); // TODO: dist corrs
		this.magCutOff = magCutOff;
		// whether to simulate it as 2 perpendicular faults
		this.isCrossHair = isCrossHair;

		// set the mags, rates, and rupture surfaces
		setAll(loc,magFreqDist,magLengthRelationship, fracStrikeSlip, fracNormal, fracReverse);

	}

	/**
	 * This computes a random strike and then builds the list of magnitudes,
	 * rates, and finite-rupture surfaces using the given MagLenthRelationship.
	 * This also sets the duration.
	 * @param loc
	 * @param magFreqDist
	 * @param magLengthRelationship
	 * @param fracStrikeSlip - 
	 * @param fracNormal - 
	 * @param fracReverse - 
	 */
	public void setAll(Location loc, IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double fracStrikeSlip, double fracNormal, double fracReverse) {

		// If isCrosssHair is true, this strike is only used for point sources. Finite sources get strike of 0 and 90
		double strike = (Math.random()-0.5)*180.0;
		if (strike < 0.0) strike +=360;
		// System.out.println(C+" random strike = "+strike);
		setAll(loc,magFreqDist,magLengthRelationship,strike,fracStrikeSlip, fracNormal, fracReverse);
	}


	/**
	 * This builds the list of magnitudes, rates, and finite-rupture surfaces using
	 * the given strike and MagLenthRelationship.  This also sets the duration.
	 *
	 * @param loc
	 * @param magFreqDist
	 * @param magLengthRelationship
	 * @param strike
	 * @param fracStrikeSlip - 
	 * @param fracNormal - 
	 * @param fracReverse - 
	 * 
	 */
	public void setAll(Location loc, IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double strike,double fracStrikeSlip,
			double fracNormal,double fracReverse) {

		if(D) System.out.println("strike="+strike);
		this.strike = strike;
		this.loc = loc;
		this.magFreqDist = magFreqDist;
		this.magLengthRelationship = magLengthRelationship;

		double sum = fracNormal+fracReverse+fracStrikeSlip;
		if(Math.abs(1-sum) > 1e-5)
			throw new RuntimeException("fractions must sum to 1.0: "+sum);
		
		Map<FocalMechanism, Double> mechWeights = new HashMap<>();
		if (fracStrikeSlip > 0d)
			mechWeights.put(FocalMech.STRIKE_SLIP.mechanism, fracStrikeSlip);
		if (fracNormal > 0d)
			mechWeights.put(FocalMech.NORMAL.mechanism, fracNormal);
		if (fracReverse > 0d)
			mechWeights.put(FocalMech.REVERSE.mechanism, fracReverse);
		
		SurfaceGenerator surfGen = new SurfaceGenerator();
		
		setData(dataForMFDs(loc, magFreqDist, mechWeights, surfGen));
	}
	
	private class SurfaceGenerator implements FocalMechRuptureSurfaceBuilder {

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface) {
			return null;
		}

		@Override
		public int getNumSurfaces(double magnitude, FocalMech mech) {
			if (isCrossHair) {
				if (DUPLICATE_PT_SRC_FOR_CROSSHAIR || magnitude > magCutOff)
					return 2;
				return 1;
			}
			return 1;
		}

		@Override
		public RuptureSurface getSurface(Location sourceLoc, double magnitude, FocalMech mech, int surfaceIndex) {
			Preconditions.checkState(surfaceIndex == 0 || (isCrossHair && surfaceIndex == 1));
			// set the rupture surface
			double depth = magnitude <= 6.5 ? 5.0 : 1.0;

			if(magnitude <= magCutOff) { // set the point surface
				PointSurface ptSurface = new PointSurface(
						Location.backwardsCompatible(loc.getLatitude(), loc.getLongitude(), depth));
				ptSurface.setAveStrike(strike);
				ptSurface.setAveDip(mech.dip());
				double width = calcWidth(magnitude, depth, mech.dip());
				ptSurface.setAveWidth(width);
				return ptSurface;
			}
			else { // set finite surface
				FrankelGriddedSurface finiteFault;
				
				checkInitSurfaces();

				// set the appropriate surface in case of CrossHair option
				if(surfaceIndex == 1) finiteFault = finiteFaultSurface2;
				else finiteFault  = finiteFaultSurface1;
				
				if(finiteFault.getLocation(0, 0).getDepth()!=depth) {
					finiteFault = finiteFault.deepCopyOverrideDepth(depth);
				}
				
				if ((float)magnitude == (float)magFreqDist.getMaxX())
					return finiteFault;
				double rupLen = magLengthRelationship.getMedianLength(magnitude);
				double startPoint = (double)finiteFault.getNumCols()/2.0 - 0.5 - rupLen/2.0;
				int cols = Math.round((float)rupLen+1);
				int startCol = Math.round((float)startPoint);
//				System.out.println("Gridded subset for M="+(float)mag+", len="+(float)rupLen+", startPoint="+(float)startPoint);
//				System.out.println("\tstartCol="+startCol+", endCol="+(startCol+cols-1)+", origCols="+finiteFault.getNumCols());
				GriddedSubsetSurface rupSurf = new GriddedSubsetSurface(1, cols,
						0, startCol, finiteFault);
				return rupSurf;
			}
		}

		@Override
		public double getSurfaceWeight(double magnitude, FocalMech mech, int surfaceIndex) {
			Preconditions.checkState(surfaceIndex == 0 || (isCrossHair && surfaceIndex == 1));
			return isCrossHair ? 0.5 : 1d;
		}

		@Override
		public boolean isSurfaceFinite(double magnitude, FocalMech mech, int surfaceIndex) {
			Preconditions.checkState(surfaceIndex == 0 || (isCrossHair && surfaceIndex == 1));
			return magnitude > magCutOff;
		}

//		@Override
//		public int getNumSurfaces(double magnitude, FocalMechanism mech) {
//			return 1;
//		}
//
//		@Override
//		public RuptureSurface getSurface(Location sourceLoc, double magnitude, FocalMechanism mech, int surfaceIndex) {
//			if (magnitude <= magCutOff)
//				return ptSurface;
//			if (magnitude == magFreqDist.getMaxX())
//				return finiteFault;
//			double rupLen = magLengthRelationship.getMedianLength(magnitude);
//			double startPoint = (double)finiteFault.getNumCols()/2.0 - 0.5 - rupLen/2.0;
//			return new GriddedSubsetSurface(1,Math.round((float)rupLen+1),
//					0,Math.round((float)startPoint),
//					finiteFault);
//		}
//
//		@Override
//		public double getSurfaceWeight(double magnitude, FocalMechanism mech, int surfaceIndex) {
//			Preconditions.checkState(surfaceIndex == 0);
//			return 1d;
//		}
//
//		@Override
//		public boolean isSurfaceFinite(double magnitude, FocalMechanism mech, int surfaceIndex) {
//			return magnitude <= magCutOff;
//		}
//
//		@Override
//		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface) {
//			return null;
//		}
		
	}
	
	private void checkInitSurfaces() {
		double depth = 1.0;

		double maxMag = magFreqDist.getX(magFreqDist.size()-1);
		// make finite source if necessary
		if(maxMag > magCutOff && finiteFaultSurface1 == null) {
			double halfLength = magLengthRelationship.getMedianLength(maxMag)/2.0;
			if(this.isCrossHair) strike = 0;
			//      loc1 = LocationUtils.getLocation(loc,new LocationVector(0.0,halfLength,strike,Double.NaN));
			// NOTE from Kevin on 10/9/2024: this was producing surfaces in the opposite trace direction, which doesn't
			// actually matter since they're all vertical, but it is confusing so I fixed it
			Location loc1 = LocationUtils.location(loc, Math.toRadians(strike+180d), halfLength); // this is before it
			Location loc2 = LocationUtils.location(loc, Math.toRadians(strike), halfLength); // this is after it
			FaultTrace fault = new FaultTrace("");
			fault.add(loc1);
			fault.add(loc2);
			finiteFaultSurface1 = new FrankelGriddedSurface(fault,aveDip,depth,depth,1.0);

			// Make second surface for cross Hair option
			if(this.isCrossHair) {
				strike = 90;
				loc1 = LocationUtils.location(loc, Math.toRadians(strike+180d), halfLength); // this is before it
				loc2 = LocationUtils.location(loc, Math.toRadians(strike), halfLength); // this is after it
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
				wc_rel, duration, 6.0,fracStrikeSlip,fracNormal,fracReverse, false, null);

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
