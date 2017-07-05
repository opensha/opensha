package scratch.UCERF3.utils.finiteFaultMap;

import java.util.Iterator;
import java.util.ListIterator;

import org.dom4j.Element;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy;
import org.opensha.sha.faultSurface.cache.SurfaceDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceSeisParameter;

import com.google.common.base.Preconditions;

/**
 * Arbitrarily discretized surface. Package private until tested.
 * @author kevin
 *
 */
class ArbitrarilyDiscretizedSurface implements RuptureSurface, CacheEnabledSurface, Iterable<Location>, XMLSaveable {
	
	public static String XML_METADATA_NAME = "ArbitrarilyDiscretizedSurface";
	
	private LocationList locs, lowerEdge;
	private FaultTrace upperEdge;
	
	private double dip;
	
	private double minDepth, maxDepth;
	
	// the points on the surface that are furthest away from each other horizontally
	private Location farthestPoint1, farthestPoint2;
	
	private double minDist; // used as proxy for grid spacing
	
	// create cache using default caching policy
	private SurfaceDistanceCache cache = SurfaceCachingPolicy.build(this);
	
	public ArbitrarilyDiscretizedSurface(LocationList locs, double dip) {
		Preconditions.checkState(!locs.isEmpty());
		this.locs = locs;
		this.dip = dip;
		
		minDepth = Double.POSITIVE_INFINITY;
		maxDepth = 0d;
		
		for (Location loc : locs) {
			double depth = loc.getDepth();
			if (depth < minDepth)
				minDepth = depth;
			if (depth > maxDepth)
				maxDepth = depth;
		}
		
		upperEdge = new FaultTrace(null);
		lowerEdge = new LocationList();
		
		for (Location loc : locs) {
			double depth = loc.getDepth();
			if ((float)depth == (float)minDepth)
				upperEdge.add(loc);
			if ((float)depth == (float)maxDepth)
				lowerEdge.add(loc);
		}
	}

	@Override
	public double getAveDip() {
		return dip;
	}

	@Override
	public double getAveStrike() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public synchronized double getAveLength() {
		// actually a maximum
		if (farthestPoint1 == null)
			calcFathestPoints();
		return LocationUtils.horzDistanceFast(farthestPoint1, farthestPoint2);
	}

	@Override
	public double getAveWidth() {
		// actually a maximum
		return maxDepth - minDepth;
	}

	@Override
	public double getArea() {
		// estimate area using rectangle that encapsulates all points. assumes planar surface
		return getAveLength()*getAveWidth();
	}
	
	private void calcFathestPoints() {
		// assumes external synchronization
		double maxDist = 0d;
		minDist = Double.POSITIVE_INFINITY;
		for (int i=0; i<locs.size(); i++) {
			Location loc1 = locs.get(i);
			for (int j=i+1; j<locs.size(); j++) {
				Location loc2 = locs.get(j);
				double dist = LocationUtils.horzDistanceFast(loc1, loc2);
				if (dist > maxDist) {
					maxDist = dist;
					farthestPoint1 = loc1;
					farthestPoint2 = loc2;
				}
				if (dist < minDist)
					minDist = dist;
			}
		}
	}

	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		return locs;
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		return locs.listIterator();
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		return upperEdge;
	}

	@Override
	public LocationList getEvenlyDiscritizedLowerEdge() {
		return lowerEdge;
	}

	@Override
	public synchronized double getAveGridSpacing() {
		if (farthestPoint1 == null)
			calcFathestPoints();
		return minDist;
	}

	public double getDistanceRup(Location siteLoc){
		return cache.getSurfaceDistances(siteLoc).getDistanceRup();
	}

	public double getDistanceJB(Location siteLoc){
		return cache.getSurfaceDistances(siteLoc).getDistanceJB();
	}

	public double getDistanceSeis(Location siteLoc){
		return cache.getSurfaceDistances(siteLoc).getDistanceSeis();
	}

	@Override
	public double getDistanceX(Location siteLoc) {
		return cache.getDistanceX(siteLoc);
	}
	
	@Override
	public double calcDistanceX(Location siteLoc) {
		return GriddedSurfaceUtils.getDistanceX(getEvenlyDiscritizedUpperEdge(), siteLoc);
	}

	@Override
	public double getAveRupTopDepth() {
		return minDepth;
	}

	@Override
	public double getAveDipDirection() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public FaultTrace getUpperEdge() {
		return upperEdge;
	}

	@Override
	public LocationList getPerimeter() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		return getUpperEdge().first();
	}

	@Override
	public Location getLastLocOnUpperEdge() {
		return getUpperEdge().last();
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

	@Override
	public String getInfo() {
		return "Arbitrarily Discretized Surf with "+size()+" locs";
	}

	@Override
	public boolean isPointSurface() {
		return false;
	}

	@Override
	public double getMinDistance(RuptureSurface surface) {
		return GriddedSurfaceUtils.getMinDistanceBetweenSurfaces(surface, this);
	}

	@Override
	public RuptureSurface getMoved(LocationVector v) {
		LocationList moved = new LocationList();
		
		for (Location loc : locs)
			moved.add(LocationUtils.location(loc, v));
		
		return new ArbitrarilyDiscretizedSurface(moved, dip);
	}

	@Override
	public RuptureSurface copyShallow() {
		LocationList locs2 = new LocationList();
		locs2.addAll(locs);
		return new ArbitrarilyDiscretizedSurface(locs2, dip);
	}

	@Override
	public SurfaceDistances calcDistances(Location loc) {
		Location loc1 = loc;
		Location loc2;
		double distJB = Double.MAX_VALUE;
		double distSeis = Double.MAX_VALUE;
		double distRup = Double.MAX_VALUE;
		
		double horzDist, vertDist, rupDist;

		// get locations to iterate over depending on dip
		ListIterator<Location> it = getLocationsIterator();

		while( it.hasNext() ){

			loc2 = (Location) it.next();

			// get the vertical distance
			vertDist = LocationUtils.vertDistance(loc1, loc2);

			// get the horizontal dist depending on desired accuracy
			horzDist = LocationUtils.horzDistanceFast(loc1, loc2);

			if(horzDist < distJB) distJB = horzDist;

			rupDist = horzDist * horzDist + vertDist * vertDist;
			if(rupDist < distRup) distRup = rupDist;

			if (loc2.getDepth() >= DistanceSeisParameter.SEIS_DEPTH) {
				if (rupDist < distSeis)
					distSeis = rupDist;
			}
		}

		distRup = Math.pow(distRup,0.5);
		distSeis = Math.pow(distSeis,0.5);

		return new SurfaceDistances(distRup, distJB, distSeis);
	}

	@Override
	public Iterator<Location> iterator() {
		return locs.iterator();
	}
	
	public int size() {
		return locs.size();
	}

	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		el.addAttribute("dip", getAveDip()+"");
		locs.toXMLMetadata(el);
		
		return root;
	}
	
	public static ArbitrarilyDiscretizedSurface fromXMLMetadata(Element el) {
		double dip = Double.parseDouble(el.attributeValue("dip"));
		LocationList locs = LocationList.fromXMLMetadata(el.element(LocationList.XML_METADATA_NAME));
		
		return new ArbitrarilyDiscretizedSurface(locs, dip);
	}

}
