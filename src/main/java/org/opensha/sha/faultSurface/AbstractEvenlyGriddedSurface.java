package org.opensha.sha.faultSurface;

import java.io.Serializable;
import java.util.Iterator;
import java.util.ListIterator;

import org.opensha.commons.data.Container2DImpl;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy;
import org.opensha.sha.faultSurface.cache.SurfaceDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;


/**
 * <b>Title:</b> EvenlyGriddedSurface<p>
 * <b>Description:</b>
 *
 * This represents 2D container of Location objects defining a geographical surface.
 * There are no constraints on what locations are put where (this is specified by subclasses), 
 * but the presumption is that the the grid of locations map out the surface in some evenly 
 * discretized way.  It is also presumed that the zeroeth row represent the top edge (or trace). <p>
 * 
 * There are also methods for getting info about the surface (e.g., ave dip, ave strike, and various distance metrics). <p>
 *
 * @author revised by field
 * @created
 * @version    1.0
 */
public abstract class AbstractEvenlyGriddedSurface  extends Container2DImpl<Location>
implements EvenlyGriddedSurface, CacheEnabledSurface, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "EvenlyGriddedSurface";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	protected double gridSpacingAlong;
	protected double gridSpacingDown;
	protected Boolean sameGridSpacing;
	
	// create cache using default caching policy
	private SurfaceDistanceCache cache = SurfaceCachingPolicy.build(this);
	
	// no argument constructor needed by subclasses
	public AbstractEvenlyGriddedSurface() {}
	
	
	/**
	 *  Constructor for the EvenlyGriddedSurface object; this sets both the grid spacing along
	 *  and down dip to the value passed in
	 *
	 * @param  numRows  Number of grid points along width of fault
	 * @param  numCols  Number of grid points along length of fault
	 * @param  gridSpacing  Grid Spacing
	 */
	public AbstractEvenlyGriddedSurface( int numRows, int numCols,double gridSpacing ) {
		super( numRows, numCols );
		gridSpacingAlong = gridSpacing;
		gridSpacingDown = gridSpacing;
		sameGridSpacing = true;
	}
	
	/**
	 *  Constructor for the EvenlyGriddedSurface object; this sets both the grid spacing along
	 *  and down dip to the value passed in
	 *
	 * @param  numRows  Number of grid points along width of fault
	 * @param  numCols  Number of grid points along length of fault
	 * @param  gridSpacing  Grid Spacing
	 */
	public AbstractEvenlyGriddedSurface( int numRows, int numCols,double gridSpacingAlong, double gridSpacingDown) {
		super( numRows, numCols );
		this.gridSpacingAlong = gridSpacingAlong;
		this.gridSpacingDown = gridSpacingDown;
		if(gridSpacingAlong == gridSpacingDown)
			sameGridSpacing = true;
		else
			sameGridSpacing = false;
	}



	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		LocationList locList = new LocationList();
		Iterator<Location> it = listIterator();
		while(it.hasNext()) locList.add((Location)it.next());
		return locList;
	}



	/**
	 * Returns the grid spacing along strike
	 * @return
	 */
	public double getGridSpacingAlongStrike() {
		return this.gridSpacingAlong;
	}

	/**
	 * returns the grid spacing down dip
	 * @return
	 */
	public double getGridSpacingDownDip() {
		return this.gridSpacingDown;
	}
	
	/**
	 * tells whether along-strike and down-dip grid spacings are the same
	 * @return
	 */
	public Boolean isGridSpacingSame() {
		return this.sameGridSpacing;
	}
	
	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		return GriddedSurfaceUtils.getEvenlyDiscritizedPerimeter(this);
	}
	
	@Override
	/**
	 * Default is to return the evenly discretized version
	 */
	public LocationList getPerimeter() {
		return getEvenlyDiscritizedPerimeter();
	}

	/**
	 * gets the location from the 2D container
	 * @param row
	 * @param column
	 * @return
	 */
	public Location getLocation(int row, int column) {
		return get(row, column);
	}

	@Override
	public int getEvenlyDiscretizedNumLocs() {
		return (int)size();
	}

	@Override
	public Location getEvenlyDiscretizedLocation(int index) {
		int row = index / numCols;
		int col = index % numCols;
		return get(row, col);
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		return listIterator();
	}

	/**
	 * Gets a specified row as a fault trace
	 * @param row
	 * @return
	 */
	public FaultTrace getRowAsTrace(int row) {
		FaultTrace trace = new FaultTrace(null);
		for(int col=0; col<getNumCols(); col++)
			trace.add(get(row, col));
		return trace;
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
	
	public SurfaceDistances calcDistances(Location loc) {
		double[] dCalc = GriddedSurfaceUtils.getPropagationDistances(this, loc);
		return new SurfaceDistances(dCalc[0], dCalc[1], dCalc[2]);
	}
	
	/**
	 * This returns rupture distance (kms to closest point on the 
	 * rupture surface), assuming the location has zero depth (for numerical 
	 * expediency).
	 * @return 
	 */
	public double getDistanceRup(Location siteLoc){
		return cache.getSurfaceDistances(siteLoc).getDistanceRup();
	}

	/**
	 * This returns distance JB (shortest horz distance in km to surface projection 
	 * of rupture), assuming the location has zero depth (for numerical 
	 * expediency).
	 * @return
	 */
	public double getDistanceJB(Location siteLoc){
		return cache.getSurfaceDistances(siteLoc).getDistanceJB();
	}

	/**
	 * This returns "distance seis" (shortest distance in km to point on rupture 
	 * deeper than 3 km), assuming the location has zero depth (for numerical 
	 * expediency).
	 * @return
	 */
	public double getDistanceSeis(Location siteLoc){
		return cache.getSurfaceDistances(siteLoc).getDistanceSeis();
	}
	
	@Override
	public double getQuickDistance(Location siteLoc) {
		return cache.getQuickDistance(siteLoc);
	}

	@Override
	public double calcQuickDistance(Location siteLoc) {
		return GriddedSurfaceUtils.getCornerMidpointDistance(this, siteLoc);
	}

	@Override
	public double calcDistanceX(Location siteLoc) {
		return GriddedSurfaceUtils.getDistanceX(getEvenlyDiscritizedUpperEdge(), siteLoc);
	}

	/**
	 * This returns distance X (the shortest distance in km to the rupture 
	 * trace extended to infinity), where values >= 0 are on the hanging wall
	 * and values < 0 are on the foot wall.  The location is assumed to be at zero
	 * depth (for numerical expediency).
	 * @return
	 */
	public double getDistanceX(Location siteLoc){
		return cache.getDistanceX(siteLoc);
	}
	
	

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		return getRowAsTrace(0);
	}

	@Override
	public FaultTrace getEvenlyDiscritizedLowerEdge() {
		return getRowAsTrace(getNumRows()-1);
	}
	
	@Override
	/**
	 * Default is to return the evenly discretized version
	 */
	public FaultTrace getUpperEdge() {
		return getEvenlyDiscritizedUpperEdge();
	}



	@Override
	public double getFractionOfSurfaceInRegion(Region region) {
		double numInside=0;
		for(Location loc: this) {
			if(region.contains(loc))
				numInside += 1;
		}
		return numInside/size();
	}


	/**
	 * This returns the first location on row zero
	 * (which should be the same as the first loc of the FaultTrace)
	 */
	@Override
	public Location getFirstLocOnUpperEdge() {
		return get(0,0);
	}
	
	/**
	 * This returns the last location on row zero (which may not be the 
	 * same as the last loc of the FaultTrace depending on the discretization)
	 */
	@Override
	public Location getLastLocOnUpperEdge() {
		return get(0,getNumCols()-1);
	}

	@Override
	public double getAveLength() {
		return getGridSpacingAlongStrike() * (getNumCols()-1);
	}

	@Override
	public double getAveWidth() {
		return getGridSpacingDownDip() * (getNumRows()-1);
	}

	@Override
	public double getArea() {
		return getAveWidth()*getAveLength();
	}
	
	@Override
	public double getAveGridSpacing() {
		return (gridSpacingAlong+gridSpacingDown)/2;
	}
	
	@Override
	public String getInfo() {
	      return GriddedSurfaceUtils.getSurfaceInfo(this);
	}
	
	@Override
	public boolean isPointSurface() {
		return (size() == 1);
	}
	
	/**
	 * Creates a new instance with the correct sub-class type, used for copying methods
	 * @return
	 */
	protected abstract AbstractEvenlyGriddedSurface getNewInstance();


	@Override
	public AbstractEvenlyGriddedSurface getMoved(LocationVector v) {
		Preconditions.checkNotNull(v, "vector cannot be null");
		AbstractEvenlyGriddedSurface moved = copyShallow();
		for (int row=0; row<getNumRows(); row++)
			for (int col=0; col<getNumCols(); col++)
				moved.set(row, col, LocationUtils.location(get(row, col), v));
		return moved;
	}


	@Override
	public AbstractEvenlyGriddedSurface copyShallow() {
		AbstractEvenlyGriddedSurface o = getNewInstance();
		Preconditions.checkState(o.getNumCols() == getNumCols());
		Preconditions.checkState(o.getNumRows() == getNumRows());
		Preconditions.checkState(o.getGridSpacingAlongStrike() == getGridSpacingAlongStrike());
		Preconditions.checkState(o.getGridSpacingDownDip() == getGridSpacingDownDip());
		for (int row=0; row<getNumRows(); row++)
			for (int col=0; col<getNumCols(); col++)
				o.set(row, col, get(row, col));
		return o;
	}

	@Override
	public void clearCache() {
		cache.clearCache();
	}
	
}
