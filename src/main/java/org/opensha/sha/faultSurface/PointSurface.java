package org.opensha.sha.faultSurface;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.PointSource;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.SingleLocDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.PointSourceDistanceCorrection;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;


/**
 * <b>Title:</b> PointSurface<p>
 *
 * <b>Description:</b> This is a special case of RuptureSurface
 * that is a point surface (has only one Location). <p>
 * 
 * This class has been modified to have threadsafe distance methods that are
 * not synchronized like those of the finite fault sources.
 *
 * A PointSurface should only be used with a threadsafe EqkRupture; users
 * should ensure that new surfaces or parent ruptures are being created
 * for each calculation loop and each calculator.
 *
 * @author     Ned Field (completely rewritten)
 * @created    February 26, 2002
 * @version    1.0
 */

public class PointSurface implements RuptureSurface, java.io.Serializable{

	private static final long serialVersionUID = 1L;

	private Location pointLocation;
	
	final static double SEIS_DEPTH = GriddedSurfaceUtils.SEIS_DEPTH;   // minimum depth for Campbell model
	
	/*
	 * Finite approximation parameters.
	 * 
	 * Although this is a point surface, it may have values set that contain information on its finite extend. These are
	 * usually used by PointSourceDistanceCorrection instances, are generally initialized to NaN unless explicitly set,
	 * or if the values needed to compute them have been set.
	 * 
	 * If not explicitly set, depths are set using the passed in location's depth with upper = lower, and widths are
	 * set to 0.
	 * 
	 * Length is initialized to 0 until set
	 */

	/**
	 * The average strike of this surface, e.g., if the strike is known but we're using a simple point surface
	 * representation anyway
	 */
	private double aveStrike = Double.NaN;

	/**
	 * The average dip of this surface, used to compute the down-dip width, and often by a PointSourceDistanceCorrection
	 * to handle footwall and hanging wall terms.
	 */
	private double aveDip = Double.NaN;
	
	/**
	 * The average width of the surface. Although older ground motion models
	 * are not concerned with rupture width, some newer models require a
	 * reasonable estimate to function properly (e.g. ASK_2014). */
	private double aveWidth;
	/**
	 * The horizontal component of the average width
	 */
	private double aveHorzWidth;
	
	/**
	 * The length of the surface, often used by a PointSourceDistanceCorrection to approximate the distance to a spinning
	 * fault with this length
	 */
	private double aveLength;
	
	/**
	 * Upper depth of the surface, used to compute the down-dip width, and often by a PointSourceDistanceCorrection
	 */
	private double upperDepth;
	
	/**
	 * Lower depth of the surface, used to compute the down-dip width, and often by a PointSourceDistanceCorrection
	 */
	private double lowerDepth;

	/** The name of this point source.  */
	protected String name;

	/**
	 *  Constructor for the PointSurface object. Sets all the fields for a true point source.
	 *
	 * @param  loc    the Location object for this point source.
	 */
	public PointSurface( Location loc ) {
		this(loc, Double.NaN, loc.depth, loc.depth, 0d);
	}
	
	/**
	 * Constructor that sets the location and all approximately-finite parameters that might be used for
	 * distance-corrections: dip, depths, and length
	 * 
	 * @param loc
	 * @param dip
	 * @param upperDepth
	 * @param lowerDepth
	 * @param length
	 */
	public PointSurface(Location loc, double dip, double upperDepth, double lowerDepth, double length) {
		Preconditions.checkNotNull(loc, "Location cannot be null");
		this.pointLocation = loc;
		if (Double.isFinite(aveDip))
			// can be NaN, but validate if not
			FaultUtils.assertValidDip( aveDip );
		this.aveDip = dip;
		setDepths(upperDepth, lowerDepth); // this will compute widths
		this.aveLength = length;
	}
	
	protected PointSurface(PointSurface other) {
		this.pointLocation = other.pointLocation;
		this.aveStrike = other.aveStrike;
		this.aveDip = other.aveDip;
		this.aveWidth = other.aveWidth;
		this.aveHorzWidth = other.aveHorzWidth;
		this.aveLength = other.aveLength;
		this.upperDepth = other.upperDepth;
		this.lowerDepth = other.lowerDepth;
		this.name = other.name;
	}
	
	/**
	 * 
	 * @param siteLoc
	 * @param singleCorr
	 * @param trt
	 * @param magnitude
	 * @return distance-corrected view of this surface with distances pre-computed for the specified location and single
	 * distance correction
	 */
	public DistanceCorrected getForDistanceCorrection(Location siteLoc,
			PointSourceDistanceCorrection.Single singleCorr, TectonicRegionType trt, double magnitude) {
		double horzDist = LocationUtils.horzDistanceFast(pointLocation, siteLoc);
		SurfaceDistances corrDists = singleCorr.getCorrectedDistance(siteLoc, this, trt, magnitude, horzDist);
		return new DistanceCorrected(this, siteLoc, corrDists);
	}
	
	/**
	 * 
	 * @param siteLoc
	 * @param siteDistances
	 * @return distance-corrected view of this surfaces with distances to the given location already passed in, likely
	 * from a {@link PointSourceDistanceCorrection}
	 */
	public DistanceCorrected getForDistances(Location siteLoc, SurfaceDistances siteDistances) {
		return new DistanceCorrected(this, siteLoc, siteDistances);
	}
	
	/**
	 * 
	 * @param singleCorr
	 * @param trt
	 * @param magnitude
	 * @return on-the-fly distance correcting view of this point source for the given single distance correction
	 */
	public DistanceCorrecting getForDistanceCorrection(
			PointSourceDistanceCorrection.Single singleCorr, TectonicRegionType trt, double magnitude) {
		return new DistanceCorrecting(this, singleCorr, trt, magnitude);
	}
	
	/**
	 * 
	 * @param siteLoc
	 * @param distCorr
	 * @param trt
	 * @param magnitude
	 * @return weighted list of distance-corrected views of this surface with distances pre-computed for the specified
	 * location and distance correction
	 */
	public WeightedList<DistanceCorrected> getForDistanceCorrection(Location siteLoc,
			PointSourceDistanceCorrection distCorr, TectonicRegionType trt, double magnitude) {
		double horzDist = LocationUtils.horzDistanceFast(pointLocation, siteLoc);
		WeightedList<SurfaceDistances> corrDists = distCorr.getCorrectedDistances(siteLoc, this, trt, magnitude, horzDist);
		Preconditions.checkState(corrDists.isNormalized(), "Returned corrected distances aren't normalized for %s", distCorr);
		List<WeightedValue<DistanceCorrected>> surfs = new ArrayList<>(corrDists.size());
		for (int i=0; i<corrDists.size(); i++) {
			SurfaceDistances dists = corrDists.getValue(i);
			double weight = corrDists.getWeight(i);
			surfs.add(new WeightedValue<DistanceCorrected>(
					new DistanceCorrected(this, siteLoc, dists), weight));
		}
		return WeightedList.of(surfs);
	}
	
	/**
	 * 
	 * @return a version of this source that blocks access to all distance metrics, used to ensure that raw distances
	 * are never used when distance corrections are enabled
	 */
	public DistanceProtected getDistancedProtected(PointSourceDistanceCorrection corr) {
		return new DistanceProtected(this, corr);
	}

	/**
	 * Sets the average strike of this surface on the Earth. An InvalidRangeException
	 * is thrown if the ave strike is not a valid value, i.e. must be > 0, etc.
	 * Even though this is a point source, an average strike can be assigned to
	 * it to assist with particular scientific caculations.
	 */
	public void setAveStrike( double aveStrike ) throws InvalidRangeException {
		FaultUtils.assertValidStrike( aveStrike );
		this.aveStrike = aveStrike ;
	}

	
	/** Returns the average strike of this surface on the Earth.  */
	public double getAveStrike() { return aveStrike; }


	/**
	 * Sets the average dip of this surface into the Earth. An InvalidRangeException
	 * is thrown if the ave strike is not a valid value, i.e. must be > 0, etc.
	 * Even though this is a point source, an average dip can be assigned to
	 * it to assist with particular scientific caculations.
	 */
	public void setAveDip( double aveDip ) throws InvalidRangeException {
		FaultUtils.assertValidDip( aveDip );
		this.aveDip =  aveDip ;
		computeWidths();
	}

	/** Returns the average dip of this surface into the Earth.  */
	public double getAveDip() { return aveDip; }
	
	/**
	 * Sets both the upper and lower depths to equal the passed in depth.
	 * 
	 * @param depth
	 */
	public void setDepth(double depth) {
		pointLocation = new Location(pointLocation.getLatitude(), pointLocation.getLongitude(), depth);
		this.upperDepth = depth;
		this.lowerDepth = depth;
		computeWidths();
	}
	
	/**
	 * Sets both the upper and lower depths to equal the passed in depth.
	 * @param depth
	 */
	public void setDepths(double upperDepth, double lowerDepth) {
		Preconditions.checkState(Double.isFinite(upperDepth), "Upper depth (%s) must be finite", upperDepth);
		Preconditions.checkState(Double.isFinite(lowerDepth), "Lower depth (%s) must be finite", lowerDepth);
		Preconditions.checkState(lowerDepth >= upperDepth,
				"Lower depth (%s) must be >= upper depth (%s)", lowerDepth, upperDepth);
		if (!Precision.equals(upperDepth, pointLocation.depth, 1e-5))
			// relocate it to the top
			pointLocation = new Location(pointLocation.getLatitude(), pointLocation.getLongitude(), upperDepth);
		this.upperDepth = upperDepth;
		this.lowerDepth = lowerDepth;
		computeWidths();
	}
	
	private void computeWidths() {
		if (upperDepth == lowerDepth) {
			aveWidth = 0d;
			aveHorzWidth = 0d;
		} else if (Double.isFinite(aveDip) && !Precision.equals(aveDip, 90d, 1e-4)) {
			// dipping
			double dipRad = Math.toRadians(aveDip);
			aveWidth = (lowerDepth - upperDepth)/Math.sin(dipRad);
			aveHorzWidth = aveWidth * Math.cos(dipRad);
		} else {
			// we have depths, but no dip, assume vertical
			aveWidth = lowerDepth - upperDepth;
			aveHorzWidth = 0d;
		}
		Preconditions.checkState(aveWidth >= 0, "Bad aveWidth=%s for zTop=%s, zBot=%s, dip=%s",
				aveDip, upperDepth, lowerDepth, aveDip);
		Preconditions.checkState(aveHorzWidth >= 0, "Bad aveHorzWidth=%s for zTop=%s, zBot=%s, dip=%s, aveWidth=%s",
				aveDip, upperDepth, lowerDepth, aveDip, aveWidth);
	}

	public void setAveLength(double aveLength) {
		this.aveLength = aveLength;
	}

	/**
	 * Gets the location for this point source.
	 * 
	 * @return
	 */
	public Location getLocation() {
		return pointLocation;
	}


	/** Sets the name of this PointSource. Uesful for lookup in a list */
	public void setName(String name) { this.name = name; }
	
	/** Gets the name of this PointSource. Uesful for lookup in a list */
	public String getName() { return name; }


	/**
	 * Returns the Surface Metadata with the following info:
	 * <ul>
	 * <li>AveDip
	 * <li>Surface length
	 * <li>Surface DownDipWidth
	 * <li>GridSpacing
	 * <li>NumRows
	 * <li>NumCols
	 * <li>Number of locations on surface
	 * <p>Each of these elements are represented in Single line with tab("\t") delimitation.
	 * <br>Then follows the location of each point on the surface with the comment String
	 * defining how locations are represented.</p>
	 * <li>#Surface locations (Lat Lon Depth)
	 * <p>Then until surface locations are done each line is the point location on the surface.
	 *
	 * </ul>
	 * @return String
	 */
	public String getSurfaceMetadata() {
		String surfaceMetadata;
		surfaceMetadata = (float)aveDip + "\t";
		surfaceMetadata += (float)getAveLength() + "\t";
		surfaceMetadata += (float)getAveWidth() + "\t";
		surfaceMetadata += (float)Double.NaN + "\t";
		surfaceMetadata += "1" + "\t";
		surfaceMetadata += "1" + "\t";
		surfaceMetadata += "1" + "\n";
		surfaceMetadata += "#Surface locations (Lat Lon Depth) \n";
		surfaceMetadata += (float) pointLocation.getLatitude() + "\t";
		surfaceMetadata += (float) pointLocation.getLongitude() + "\t";
		surfaceMetadata += (float) pointLocation.getDepth();

		return surfaceMetadata;
	}

	@Override
	public double getAveDipDirection() {
		throw new RuntimeException("Method not yet implemented");
	}

	@Override
	public double getAveRupTopDepth() {
		return upperDepth;
	}
	
	@Override
	public double getAveRupBottomDepth() {
		return lowerDepth;
	}

	@Override
	public LocationList getPerimeter() {
		return getLocationList();
	}
	
	private FaultTrace getFaultTrace() {
		FaultTrace trace = new FaultTrace(null);
		// this was set to the upper depth via setDepths(...)
		trace.add(pointLocation);
		return trace;
	}

	@Override
	public FaultTrace getUpperEdge() {
		return getFaultTrace();
	}
	
	@Override
	public double getDistanceRup(Location siteLoc){
		double depth = getAveRupTopDepth();
		double djb = getDistanceJB(siteLoc);
		return Math.sqrt(depth * depth + djb * djb);
	}

	@Override
	public double getDistanceJB(Location siteLoc){
		double horzDist = LocationUtils.horzDistanceFast(pointLocation, siteLoc);
		return horzDist;
	}

	@Override
	public double getDistanceSeis(Location siteLoc){
		double depth = Math.max(SEIS_DEPTH, getAveRupTopDepth());
		double djb = getDistanceJB(siteLoc);
		return Math.sqrt(depth * depth + djb * djb);
	}
	
	@Override
	public double getQuickDistance(Location siteLoc) {
		return LocationUtils.horzDistanceFast(pointLocation, siteLoc);
	}

	/**
	 * This returns distance X (the shortest distance in km to the rupture 
	 * trace extended to infinity), where values >= 0 are on the hanging wall
	 * and values < 0 are on the foot wall.  The given site location is assumed to be at zero
	 * depth (for numerical expediency).  This always returns zero since this is a point surface.
	 * @return
	 */
	@Override
	public double getDistanceX(Location siteLoc){
		return 0.0;
	}
	
	@Override
	public String getInfo() {
        return new String("\tPoint-Surface Location (lat, lon, depth (km):" +
                "\n\n" +
                "\t\t" + (float) pointLocation.getLatitude() + ", " +
                (float) pointLocation.getLongitude() +
                ", " + (float) pointLocation.getDepth());
	}

	@Override
	public boolean isPointSurface() {
		return true;
	}

	@Override
	public double getArea() {
		return 0;
	}

	@Override
	public double getAreaInsideRegion(Region region) {
		if (region.contains(getLocation()))
			return getArea();
		return 0d;
	}

	@Override
	public double getAveGridSpacing() {
		return 0;
	}

	@Override
	public double getAveLength() {
		return aveLength;
	}
	
	@Override
	public double getAveWidth() {
		return aveWidth;
	}
	
	@Override
	public double getAveHorizontalWidth() {
		return aveHorzWidth;
	}
	
	private LocationList getLocationList() {
		LocationList list = new LocationList();
		list.add(pointLocation); // always at upper depth
		return list;
	}

	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		return getLocationList();
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		return getLocationList();
	}

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		return getFaultTrace();
	}

	@Override
	public LocationList getEvenlyDiscritizedLowerEdge() {
		if (lowerDepth > upperDepth) {
			LocationList list = new LocationList();
			list.add(new Location(pointLocation.lat, pointLocation.lon, lowerDepth)); // always at upper depth
			return list;
		}
		return getLocationList();
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		return pointLocation;
	}

	@Override
	public Location getLastLocOnUpperEdge() {
		return pointLocation;
	}

	@Override
	public double getFractionOfSurfaceInRegion(Region region) {
		if(region.contains(pointLocation))
			return 1.0;
		else
			return 0.0;
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		return getLocationList().listIterator();
	}

	
	/**
	 * This returns the minimum distance as the minimum among all location
	 * pairs between the two surfaces
	 * @param surface RuptureSurface 
	 * @return distance in km
	 */
	@Override
	public double getMinDistance(RuptureSurface surface) {
		return GriddedSurfaceUtils.getMinDistanceBetweenSurfaces(surface, this);
	}

	@Override
	public PointSurface getMoved(LocationVector v) {
		PointSurface moved = new PointSurface(this);
		moved.pointLocation = LocationUtils.location(moved.getLocation(), v);
		return moved;
	}
	
	/**
	 * Gets a copy of this surface for a new location and with all properties (including depths) retained
	 * 
	 * @param lat
	 * @param lon
	 * @return
	 */
	public PointSurface getMoved(double lat, double lon) {
		PointSurface moved = new PointSurface(this);
		moved.pointLocation = new Location(lat, lon, pointLocation.depth);
		return moved;
	}

	@Override
	public PointSurface copyShallow() {
		return new PointSurface(this);
	}
	
	/**
	 * Point surface wrapper with pre-computed {@link SurfaceDistances} for a specific site, usually from a
	 * {@link PointSourceDistanceCorrection}
	 */
	public static class DistanceCorrected extends PointSurface {
		
		private Location siteLoc;
		private SurfaceDistances surfDists;

		public DistanceCorrected(PointSurface surf, Location siteLoc, SurfaceDistances surfDists) {
			super(surf);
			Preconditions.checkNotNull(siteLoc);
			Preconditions.checkNotNull(surfDists);
			this.siteLoc = siteLoc;
			this.surfDists = surfDists;
		}

		@Override
		public double getDistanceRup(Location siteLoc) {
			assertSameLocation(siteLoc);
			return surfDists.getDistanceRup();
		}

		@Override
		public double getDistanceJB(Location siteLoc) {
			assertSameLocation(siteLoc);
			return surfDists.getDistanceJB();
		}

		@Override
		public double getDistanceSeis(Location siteLoc) {
			assertSameLocation(siteLoc);
			return surfDists.getDistanceSeis();
		}

		@Override
		public double getDistanceX(Location siteLoc) {
			assertSameLocation(siteLoc);
			return surfDists.getDistanceX();
		}
		
		private void assertSameLocation(Location siteLoc) {
			if (this.siteLoc == siteLoc || this.siteLoc.equals(siteLoc) || LocationUtils.areSimilar(siteLoc, this.siteLoc))
				return;
			throw new IllegalStateException("This distance-corrected point-source has been precomputed for one location ("
				+this.siteLoc+"), cannot return distances for the passed in location ("+siteLoc+")");
		}

	}
	
	/**
	 * Point surface wrapper with that calculates corrected distances on the fly from a
	 * {@link PointSourceDistanceCorrection.Single} correction.
	 */
	public static class DistanceCorrecting extends PointSurface implements CacheEnabledSurface {
		
		private SingleLocDistanceCache cache;
		private PointSourceDistanceCorrection.Single corr;
		private TectonicRegionType trt;
		private double magnitude;

		public DistanceCorrecting(PointSurface surf, PointSourceDistanceCorrection.Single corr,
				TectonicRegionType trt, double magnitude) {
			super(surf);
			this.trt = trt;
			this.magnitude = magnitude;
			Preconditions.checkNotNull(corr);
			this.corr = corr;
			this.cache = new SingleLocDistanceCache(this);
		}

		@Override
		public double getDistanceRup(Location siteLoc) {
			return cache.getSurfaceDistances(siteLoc).getDistanceRup();
		}

		@Override
		public double getDistanceJB(Location siteLoc) {
			return cache.getSurfaceDistances(siteLoc).getDistanceJB();
		}

		@Override
		public double getDistanceSeis(Location siteLoc) {
			return cache.getSurfaceDistances(siteLoc).getDistanceSeis();
		}

		@Override
		public double getDistanceX(Location siteLoc) {
			return cache.getSurfaceDistances(siteLoc).getDistanceX();
		}

		@Override
		public SurfaceDistances calcDistances(Location loc) {
			return corr.getCorrectedDistance(loc, this, trt, magnitude, super.getQuickDistance(loc));
		}

		@Override
		public double calcQuickDistance(Location loc) {
			return super.getQuickDistance(loc);
		}

		@Override
		public void clearCache() {
			cache.clearCache();
		}

	}
	
	/**
	 * Point surface wrapper that blocks access to all distance metrics; used by a {@link PointSource} to prevent other
	 * classes from accidentally accessing non-corrected distance metrics. Instead, an {@link IllegalStateException} will
	 * be thrown.
	 */
	public static class DistanceProtected extends PointSurface {
		
		private static final String MESSAGE = "This PointSurface is part of a distance-corrected PointSource and cannot be "
				+ "used directly; instead, access the distance-corrected ruptures via PointSource.getForSite(Site) method. "
				+ "If you really need to access the distance metrics, uncorrected alternatives are provided, e.g., "
				+ "getUncorrectedDistanceRup(Location)";
		
		private PointSurface surf;
		private PointSourceDistanceCorrection corr;

		public DistanceProtected(PointSurface surf, PointSourceDistanceCorrection corr) {
			super(surf);
			this.surf = surf;
			this.corr = corr;
		}

		@Override
		public double getDistanceRup(Location siteLoc) {
			throw new IllegalStateException(MESSAGE);
		}

		@Override
		public double getDistanceJB(Location siteLoc) {
			throw new IllegalStateException(MESSAGE);
		}

		@Override
		public double getDistanceSeis(Location siteLoc) {
			throw new IllegalStateException(MESSAGE);
		}

		@Override
		public double getDistanceX(Location siteLoc) {
			throw new IllegalStateException(MESSAGE);
		}

		public double getUncorrectedDistanceRup(Location siteLoc) {
			return super.getDistanceRup(siteLoc);
		}

		public double getUncorrectedDistanceJB(Location siteLoc) {
			return super.getDistanceJB(siteLoc);
		}

		public double getUncorrectedDistanceSeis(Location siteLoc) {
			return super.getDistanceSeis(siteLoc);
		}

		public double getUncorrectedDistanceX(Location siteLoc) {
			return super.getDistanceX(siteLoc);
		}
		
		public PointSurface getUncorrectedSurface() {
			return surf;
		}
		
		public PointSourceDistanceCorrection getDistanceCorrection() {
			return corr;
		}

	}

}
