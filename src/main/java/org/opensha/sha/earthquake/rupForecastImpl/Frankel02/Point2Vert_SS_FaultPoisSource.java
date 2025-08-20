package org.opensha.sha.earthquake.rupForecastImpl.Frankel02;

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.PointSource.PoissonPointSource;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.FrankelGriddedSurface;
import org.opensha.sha.faultSurface.GriddedSubsetSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * <p>Title: Point2Vert_SS_FaultPoisSource </p>
 * <p>Description: For a given Location, IncrementalMagFreqDist (of Poissonian
 * rates), MagLengthRelationship, and duration, this creates a vertically dipping,
 * strike-slip ProbEqkRupture for each magnitude (that has a non-zero rate).  Each finite
 * rupture is centered on the given Location.  A user-defined strike will be used if given,
 * otherwise a single random stike will be computed and applied to all ruptures.  One can also specify a
 * magCutOff (magnitudes less than or equal to this will be treated as point sources).
 * This assumes that the duration
 * units are the same as those for the rates in the IncrementalMagFreqDist.</p>
 * This class is loosely modeled after Frankels fortran program "hazgridXv3.f".  However,
 * their use of a random strike means we will never be able to exactly reproduce their
 * results.  Also, they choose a different random strike for each magnitude (and even
 * each site location), whereas we apply one random strike to the entire source (all mags
 * in the given magFreqDist).  NOTE - I put this here rather than in the parent directory
 * because the getMinDistance(Site) method is measured only relative to the grid location
 * (not strictly correct, but it is consistent with how Frankel's 2002 code works.
 * @author Edward Field
 * @date March 24, 2004
 * @version 1.0
 */

public class Point2Vert_SS_FaultPoisSource extends PoissonPointSource implements java.io.Serializable{


	//for Debug purposes
	private static String  C = new String("Point2Vert_SS_FaultPoisSource");
	private boolean D = false;

	private IncrementalMagFreqDist magFreqDist;
	private static final double aveDip=90;
	private static final  double aveRake=0.0;
	private MagLengthRelationship magLengthRelationship;
	private double magCutOff;
	private PointSurface ptSurface;
	private FrankelGriddedSurface finiteFault;

	// to hold the non-zero mags, rates, and rupture surfaces
	//  ArrayList mags, rates, rupSurfaces;

	/**
	 * The Full Constructor
	 * @param loc - the Location of the point source
	 * @param magFreqDist - the mag-freq-dist for the point source
	 * @param magLengthRelationship - A relationship for computing length from magnitude
	 * @param strike - the strike of the finite SS fault
	 * @param duration - the forecast duration
	 * @param magCutOff - below (and eqaul) to this value a PointSurface will be applied
	 */
	public Point2Vert_SS_FaultPoisSource(Location loc, IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double strike, double duration, double magCutOff){
		super(loc, duration, null);
		this.magCutOff = magCutOff;

		if(D) {
			System.out.println("magCutOff="+magCutOff);
			System.out.println("num pts in magFreqDist="+magFreqDist.size());
		}

		// set the mags, rates, and rupture surfaces
		setAll(magFreqDist, magLengthRelationship, strike);
	}


	/**
	 * The Constructor for the case where a random strike is computed (rather than assigned)
	 * @param loc - the Location of the point source
	 * @param magFreqDist - the mag-freq-dist for the point source
	 * @param magLengthRelationship - A relationship for computing length from magnitude
	 * @param duration - the forecast duration
	 * @param magCutOff - below (and eqaul) to this value a PointSurface will be applied
	 */
	public Point2Vert_SS_FaultPoisSource(Location loc, IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double duration, double magCutOff){
		super(loc, duration, null);
		this.magCutOff = magCutOff;

		// set the mags, rates, and rupture surfaces
		setAll(magFreqDist, magLengthRelationship);

	}

	/**
	 * This computes a random strike and then builds the list of magnitudes,
	 * rates, and finite-rupture surfaces using the given MagLenthRelationship.
	 * This also sets the duration.
	 * @param magFreqDist
	 * @param magLengthRelationship
	 */
	public void setAll(IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship) {

		// get a random strike between -90 and 90
		double strike = (Math.random()-0.5)*180.0;
		if (strike < 0.0) strike +=360;
		// System.out.println(C+" random strike = "+strike);
		setAll(magFreqDist, magLengthRelationship, strike);
	}


	/**
	 * This builds the list of magnitudes, rates, and finite-rupture surfaces using
	 * the given strike and MagLenthRelationship.  This also sets the duration.
	 *
	 * @param magFreqDist
	 * @param magLengthRelationship
	 * @param strike
	 */
	public void setAll(IncrementalMagFreqDist magFreqDist,
			MagLengthRelationship magLengthRelationship,
			double strike) {
		
		Location loc = getLocation();
		double duration = getDuration();
		this.magFreqDist = magFreqDist;
		this.magLengthRelationship = magLengthRelationship;

		if(D) System.out.println("duration="+duration);
		if(D) System.out.println("strike="+strike);

		// make the point surface
		ptSurface = new PointSurface(loc);
		ptSurface.setAveDip(aveDip);
		ptSurface.setAveStrike(strike);
		double maxMag = magFreqDist.getX(magFreqDist.size()-1);
		// make finite source if necessary
		if(maxMag > magCutOff) {
			Location loc1, loc2;
			LocationVector dir;
			double halfLength = magLengthRelationship.getMedianLength(maxMag)/2.0;
			//      loc1 = LocationUtils.getLocation(loc,new LocationVector(0.0,halfLength,strike,Double.NaN));
			loc1 = LocationUtils.location(loc,
					new LocationVector(strike, halfLength, 0.0));
			dir = LocationUtils.vector(loc1,loc);
			dir.setHorzDistance(dir.getHorzDistance()*2.0);
			loc2 = LocationUtils.location(loc1,dir);
			FaultTrace fault = new FaultTrace("");
			fault.add(loc1);
			fault.add(loc2);
			finiteFault = new FrankelGriddedSurface(fault,aveDip,loc.getDepth(),loc.getDepth(),1.0);
		}
		
		SurfaceGenerator surfGen = new SurfaceGenerator();
		
		setData(dataForMFD(loc, TectonicRegionType.ACTIVE_SHALLOW, magFreqDist, new FocalMechanism(strike, aveDip, aveRake), surfGen));
	}
	
	private class SurfaceGenerator implements RuptureSurfaceBuilder {

		@Override
		public int getNumSurfaces(double magnitude, FocalMechanism mech) {
			return 1;
		}

		@Override
		public RuptureSurface getSurface(Location sourceLoc, double magnitude, TectonicRegionType trt, FocalMechanism mech, int surfaceIndex) {
			if (magnitude <= magCutOff)
				return ptSurface;
			if (magnitude == magFreqDist.getMaxX())
				return finiteFault;
			double rupLen = magLengthRelationship.getMedianLength(magnitude);
			double startPoint = (double)finiteFault.getNumCols()/2.0 - 0.5 - rupLen/2.0;
			return new GriddedSubsetSurface(1,Math.round((float)rupLen+1),
					0,Math.round((float)startPoint),
					finiteFault);
		}

		@Override
		public double getSurfaceWeight(double magnitude, FocalMechanism mech, int surfaceIndex) {
			Preconditions.checkState(surfaceIndex == 0);
			return 1d;
		}

		@Override
		public boolean isSurfaceFinite(double magnitude, FocalMechanism mech, int surfaceIndex) {
			return magnitude > magCutOff;
		}

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface) {
			return null;
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
		if(this.finiteFault!=null) return finiteFault.getEvenlyDiscritizedListOfLocsOnSurface();
		else return ptSurface.getEvenlyDiscritizedListOfLocsOnSurface();
	}

	public RuptureSurface getSourceSurface() {
		if(this.finiteFault!=null) return finiteFault;
		else return ptSurface;
	}

	/**
	 * get the name of this class
	 *
	 * @return
	 */
	public String getName() {
		return C;
	}

	// this is temporary for testing purposes
	public static void main(String[] args) {
		Location loc = new Location(34,-118,0);
		GutenbergRichterMagFreqDist dist = new GutenbergRichterMagFreqDist(5,16,0.2,1e17,0.9);
		WC1994_MagLengthRelationship wc_rel = new WC1994_MagLengthRelationship();

		//    Point2Vert_SS_FaultPoisSource src = new Point2Vert_SS_FaultPoisSource(loc, dist,
		//                                       wc_rel,45, 1.0, 6.0, 5.0);
		Point2Vert_SS_FaultPoisSource src = new Point2Vert_SS_FaultPoisSource(loc, dist,
				wc_rel, 1.0, 6.0);

		System.out.println("num rups ="+src.getNumRuptures());
		ProbEqkRupture rup;
		Location loc1, loc2;
		double length, aveLat, aveLon;
		System.out.println("Rupture mags and end locs:");
		for(int r=0; r<src.getNumRuptures();r++) {
			rup = src.getRupture(r);
			loc1 = rup.getRuptureSurface().getFirstLocOnUpperEdge();
			loc2 = rup.getRuptureSurface().getLastLocOnUpperEdge();
			length = LocationUtils.horzDistance(loc1,loc2);
			aveLat = (loc1.getLatitude()+loc2.getLatitude())/2;
			aveLon = (loc1.getLongitude()+loc2.getLongitude())/2;
			//      System.out.println("\t"+(float)rup.getMag()+"\t"+loc1+"\t"+loc2);
			System.out.println("\t"+(float)rup.getMag()+
					"\tlen1="+(float)wc_rel.getMedianLength(rup.getMag())+
					"\tlen2="+(float)length+"\taveLat="+(float)aveLat+
					"\taveLon="+(float)aveLon);
		}
	}
}
