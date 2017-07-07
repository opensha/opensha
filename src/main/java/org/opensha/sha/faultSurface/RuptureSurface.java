
package org.opensha.sha.faultSurface;

import java.util.ArrayList;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;

/**
 * This interface defines a rupture surfaces. This does not specify how a rupture 
 * surface is to be represented (in order to maintan flexibility), but rather 
 * specifies what information a rupture surface needs to provide (see method 
 * descriptions for details).
 * @author field
 *
 */
public interface RuptureSurface extends Surface3D {
	
	
	/**
	 * Average dip (degrees) of rupture surface
	 * @return
	 */
	public double getAveDip();
	
	/**
	 * Average strike (degrees) of rupture surface
	 * @return
	 */
	public double getAveStrike();
	
    /**
     * This returns the average length of the surface in km
     * @return double
     */
    public double getAveLength();

	/**
	 * Average down-dip width (km) of rupture surface
	 * @return
	 */
	public double getAveWidth();
	
    /**
     * This returns the surface area in km-sq
     * @return double
     */
    public double getArea();
    
	/**
	 * This returns a list of locations that are evenly spread (at least 
	 * approximately) over the rupture surface, with a spacing given by
	 * what's returned by the getGridSpacing() method.  Further details 
	 * are specified by the implementing class.  Don't assume the locations 
	 * are ordered as one reads the words on a page in a book (not the case
	 * for CompoundGriddedSurface).
	 * @return
	 */
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface();
	
	/**
	 * This returns a list of locations that are evenly spread along the
	 * upper edge of the surface.  Further details are specified by the implementing 
	 * class.  These locations should be ordered along the fault following
	 * the Aki and Richards convention.
	 * @return
	 */
	public FaultTrace getEvenlyDiscritizedUpperEdge();
	
	/**
	 * This returns a list of locations that are evenly spread along the
	 * lower edge of the surface.  Further details are specified by the implementing 
	 * class.  These locations should be ordered along the fault following
	 * the Aki and Richards convention.
	 * @return
	 */
	public LocationList getEvenlyDiscritizedLowerEdge();
	
	/**
	 * This returns the average grid spacing used to define the discretization 
	 * used in what's returned by the methods here that contain "Discretized"
	 * in their names.
	 * @return
	 */
	public double getAveGridSpacing();
		
	/**
	 * This returns rupture distance (kms to closest point on the 
	 * rupture surface), assuming the location has zero depth (for numerical 
	 * expediency).
	 * @return 
	 */
	public double getDistanceRup(Location siteLoc);

	/**
	 * This returns distance JB (shortest horz distance in km to surface projection 
	 * of rupture), assuming the location has zero depth (for numerical 
	 * expediency).
	 * @return
	 */
	public double getDistanceJB(Location siteLoc);

	/**
	 * This returns "distance seis" (shortest distance in km to point on rupture 
	 * deeper than 3 km), assuming the location has zero depth (for numerical 
	 * expediency).
	 * @return
	 */
	public double getDistanceSeis(Location siteLoc);

	/**
	 * This returns distance X (the shortest distance in km to the rupture 
	 * upper edge extended to infinity), where values >= 0 are on the hanging wall
	 * and values < 0 are on the foot wall.  The location is assumed to be at zero
	 * depth (for numerical expediency).
	 * @return
	 */
	public double getDistanceX(Location siteLoc);

	/**
	 * Average depth (km) to top of rupture (always a positive number)
	 * @return
	 */
	public double getAveRupTopDepth();

	/**
	 * Average dip direction (degrees) of rupture surface
	 * @return
	 */
	public double getAveDipDirection();
	
	/**
	 * This returns the upper edge of the rupture surface (where the 
	 * locations are not necessarily equally spaced).  This may be the original
	 * Fault Trace used to define the surface, but not necessarily.
	 * @return
	 */
	public FaultTrace getUpperEdge();
	
	/**
	 * This returns the first location on the upper edge of the surface
	 * @return
	 */
	public Location getFirstLocOnUpperEdge();
	
	/**
	 * This returns the last location on the upper edge of the surface
	 * @return
	 */
	public Location getLastLocOnUpperEdge();
	
	/**
	 * The is returns the fraction of this rupture surface 
	 * that's inside the given region.
	 * @param region
	 * @return
	 */
	public double getFractionOfSurfaceInRegion(Region region);
	
	/**
	 * This is a string giving brief info about the surface (e.g., used in GUIs)
	 */
	public String getInfo();
	
	/**
	 * Calculate the minimum distance of this rupture surface to the given surface
	 * @param surface EvenlyGriddedSurface 
	 * @return distance in km
	 */
	public double getMinDistance(RuptureSurface surface);
	
	/**
	 * Returns a new RuptureSurface instance that has been moved by the given vector
	 * 
	 * @param v
	 * @return
	 */
	public RuptureSurface getMoved(LocationVector v);
	
	/**
	 * Returns a shallow copy of this RuptureSurface
	 * 
	 * @return
	 */
	public RuptureSurface copyShallow();
}
