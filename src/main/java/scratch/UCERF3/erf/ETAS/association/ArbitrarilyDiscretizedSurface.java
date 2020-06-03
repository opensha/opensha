package scratch.UCERF3.erf.ETAS.association;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.DataUtils;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
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
	
	private static boolean D = false;
	
	private static final boolean GRID_CENTERED_DEFAULT = true;
	
	private LocationList locs;
	
	private LocationList lowerEdge;
	private FaultTrace upperEdge;
	
	private double minDepth, maxDepth;
	
	private DistsRecord[] locDists;
	
	private Double lengthEst;
	private Distance distForLengthEst;
	private Double widthEst;
	private double horzComponentOfWidth = 0d;
	private Double dipEst;
	private Double gridSpacingEst;
	private Boolean uniformSpacing;
	private Boolean gridCentered;
	
	// create cache using default caching policy
	private SurfaceDistanceCache cache = SurfaceCachingPolicy.build(this);
	
	public ArbitrarilyDiscretizedSurface(LocationList locs) {
		this(locs, null);
	}
	
	public ArbitrarilyDiscretizedSurface(LocationList locs, Boolean gridCentered) {
		Preconditions.checkState(!locs.isEmpty());
		this.locs = locs;
		
		minDepth = Double.POSITIVE_INFINITY;
		maxDepth = Double.NEGATIVE_INFINITY;
		
		for (Location loc : locs) {
			double depth = loc.getDepth();
			if (gridCentered == null && (float)depth == 0f) {
				if (D) System.out.println("found zero depth, so it cannot be grid centered. setting gridCentered=false");
				// can't be grid centered with zero depth
				gridCentered = false;
			}
			if (depth < minDepth)
				minDepth = depth;
			if (depth > maxDepth)
				maxDepth = depth;
		}

		this.gridCentered = gridCentered;
	}
	
	private synchronized boolean isGridCentered() {
		if (gridCentered != null)
			return gridCentered;
		double aveSpacing = getAveGridSpacing();
		// if the min depth is less than half a grid spacing, then it can't be grid centered'
		double aveDip = getAveDip();
		double vertComp = Math.sin(Math.toRadians(aveDip))*aveSpacing;
		if (D) System.out.println("vertical component of aveSpacing with assumed dip="+(float)aveDip+": "+(float)vertComp);
		double amountAboveSurface = -(minDepth - vertComp);
		if (amountAboveSurface/aveSpacing > 0.1) {
			// it's sticking up above the surface by at least 10%
			if (D) System.out.println("Assuming gridCentered=false because if false it would stick up "
					+amountAboveSurface+" km above the surface");
			gridCentered = false;
		} else {
			gridCentered = GRID_CENTERED_DEFAULT;
			if (D) System.out.println("Couldn't determine grid spacing, assuming default: "+GRID_CENTERED_DEFAULT);
		}
		return gridCentered;
	}
	
	private class DistsRecord {
		private Location loc;
		
		private Distance nearest3D;
		private Distance nearestHoriz;
		private Distance farthest3D;
		private Distance farthestHoriz;
		
		private List<Distance> distances;
		
		// this is the largest [horz - vert] distance, an attempt to find the length
		private double horzVertPenalizedDist = 0d;
		private Distance farthestHorizVertPenalized;
		
		private DistsRecord(Location loc) {
			this.loc = loc;
			distances = new ArrayList<>();
		}
		
		private void addDistance(Distance dist) {
			if (farthest3D == null || dist.dist3D > farthest3D.dist3D)
				farthest3D = dist;
			if (farthestHoriz == null || dist.horzDist > farthestHoriz.horzDist)
				farthestHoriz = dist;
			if (nearest3D == null || dist.dist3D < nearest3D.dist3D)
				nearest3D = dist;
			if (nearestHoriz == null || dist.horzDist < nearestHoriz.horzDist)
				nearestHoriz = dist;
			
			double myHorzVertPenalizedDist = dist.horzDist - dist.vertDist;
			if (farthestHorizVertPenalized == null) {
				farthestHorizVertPenalized = dist;
				horzVertPenalizedDist = myHorzVertPenalizedDist;
			} else if (myHorzVertPenalizedDist > horzVertPenalizedDist) {
				farthestHorizVertPenalized = dist;
				horzVertPenalizedDist = myHorzVertPenalizedDist;
			}
			
			distances.add(dist);
		}
	}
	
	private class Distance {
		private final Location loc1;
		private final Location loc2;
		
		private final double horzDist;
		private final double vertDist;
		private final double dist3D;
		
		public Distance(Location loc1, Location loc2) {
			this.loc1 = loc1;
			this.loc2 = loc2;
			
			horzDist = LocationUtils.horzDistanceFast(loc1, loc2);
			vertDist = Math.abs(LocationUtils.vertDistance(loc1, loc2));
			dist3D = Math.sqrt(horzDist*horzDist + vertDist*vertDist);
		}
		
		@Override
		public String toString() {
			return "Distance: "+loc1+" => "+loc2+"\n\tHoriz: "+(float)horzDist+"\n\tVert: "+(float)vertDist+"\n\t3D: "+(float)dist3D;
		}
	}
	
	/**
	 *  synchronized EXTERNALLY
	 */
	private DistsRecord[] getCalcDistances() {
		if (locDists != null)
			return locDists;
		locDists = new DistsRecord[locs.size()];
		for (int i=0; i<locs.size(); i++)
			locDists[i] = new DistsRecord(locs.get(i));
		for (int i=0; i<locs.size(); i++) {
			Location loc1 = locs.get(i);
			for (int j=i+1; j<locs.size(); j++) {
				Location loc2 = locs.get(j);
				Distance dist = new Distance(loc1, loc2);
				Preconditions.checkState(dist.dist3D > 0d, "0 distance found between points %s and %s.\n\t%s\n\t%s", i, j, loc1, loc2);
				locDists[i].addDistance(dist);
				locDists[j].addDistance(dist);
			}
		}
		return locDists;
	}

	@Override
	public synchronized double getAveDip() {
		if (dipEst != null)
			return dipEst;
		double width = getRawWidthEstimate();
		if ((float)horzComponentOfWidth == 0f) {
			dipEst = 90d;
		} else {
			dipEst = Math.toDegrees(Math.acos(horzComponentOfWidth/width));
		}
		if (D) System.out.println("Estimated dip of "+dipEst.floatValue()+" from width of "+(float)width+"  with horzComp="+(float)horzComponentOfWidth);
		return dipEst;
	}

	@Override
	public double getAveStrike() {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Estimates the length of this arbitrary surface. This is done by finding the largest horizontal distance,
	 * but penalizing for vertical distance changes. First it finds the location pair with the largerst [horz - vert]
	 * value, then returns the horizontal distance of that location pair.
	 */
	@Override
	public synchronized double getAveLength() {
		if (lengthEst != null)
			return lengthEst;
		double maxHorzDist = Double.NEGATIVE_INFINITY;
		Distance maxHorzRecord = null;
		for (DistsRecord dist : getCalcDistances()) {
			if (dist.horzVertPenalizedDist > maxHorzDist) {
				maxHorzDist = dist.horzVertPenalizedDist;
				maxHorzRecord = dist.farthestHorizVertPenalized;
			}
		}
		Preconditions.checkNotNull(maxHorzRecord);
		Preconditions.checkState(maxHorzDist > 0, "Negative max vert-penalized dist? %s\n%s", maxHorzDist, maxHorzRecord);
		Preconditions.checkState((float)maxHorzRecord.vertDist == 0f || maxHorzRecord.vertDist < getAveGridSpacing(),
				"vertical component of location pair for length estimate (%s) is larger than grid spacing (%s)",
				maxHorzRecord.vertDist, getAveGridSpacing());
		lengthEst = maxHorzRecord.horzDist;
		distForLengthEst = maxHorzRecord;
		if (isGridCentered())
			lengthEst += getAveGridSpacing();
		if (D) System.out.println("getAveLength(): Estimated ave length: "+lengthEst.floatValue()
			+" with maximim vert-penalized dist of "+(float)maxHorzDist+". Distance record:\n"+maxHorzRecord);
		return lengthEst;
	}

	/**
	 * Estimate average width. First follow the procedure of getAveLength to find the farthest horizontal pair
	 * (penalized for vertical distance), then the the horizontal component of the width as the max horizontal
	 * distance to that line (accounting for both sides of the line). Then combine that with the maximum vertical
	 * distance.
	 */
	@Override
	public synchronized double getAveWidth() {
		double width = getRawWidthEstimate();
		if (isGridCentered())
			width += getAveGridSpacing();
		return width;
	}
	
	private synchronized double getRawWidthEstimate() {
		double len = getAveLength(); // make sure it's computed
		double maxPositiveDist = 0;
		double maxNegativeDist = 0;
		Preconditions.checkNotNull(distForLengthEst, "Somehow no distForLengthEst but len calculated: %s", len);
		Location p1 = distForLengthEst.loc1;
		Location p2 = distForLengthEst.loc2;
		for (Location loc : locs) {
			double dist = LocationUtils.distanceToLineFast(p1, p2, loc);
			if (Double.isNaN(dist))
				continue;
			if (dist > maxPositiveDist)
				maxPositiveDist = dist;
			if (dist < maxNegativeDist)
				maxNegativeDist = dist;
		}
		horzComponentOfWidth = -maxNegativeDist + maxPositiveDist;
		if (D) System.out.println("getAveWidth(): estimated horizontal component to be abs("
				+(float)maxNegativeDist+") + "+(float)maxPositiveDist+" = "+(float)horzComponentOfWidth);
		double totVert = maxDepth - minDepth;
		widthEst = Math.sqrt(horzComponentOfWidth*horzComponentOfWidth + totVert*totVert);
		return widthEst;
	}

	@Override
	public double getArea() {
		double aveSpacing = getAveGridSpacing();
		if (uniformSpacing) {
			double aveArea = aveSpacing*aveSpacing;
			if (isGridCentered()) {
				return locs.size()*aveArea;
			} else {
				// don't overestimate it, remove half-grid spacing for each element on the boundary
				double estAlongStrike = getAveLength()/aveSpacing;
				double estDownDip = getAveWidth()/aveSpacing;
				double estNumBoundary = estAlongStrike*2 + estDownDip*2 - 4d;
				return (locs.size()-0.5*estNumBoundary)*aveArea;
			}
		}
		double length = getAveLength();
		double width = getAveWidth();
		return length*width;
	}

	@Override
	public double getAreaInsideRegion(Region region) {
		double area = getArea();
		double areaEach = area / size();
		
		double areaInside = 0d;
		for (Location loc : locs)
			if (region.contains(loc))
				areaInside += areaEach;
		return areaInside;
	}

	@Override
	public int getEvenlyDiscretizedNumLocs() {
		return locs.size();
	}

	@Override
	public Location getEvenlyDiscretizedLocation(int index) {
		return locs.get(index);
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
	
	private void calcUpperLower() {
		if (lowerEdge != null)
			return;
		double halfSpacing = 0.5*getAveGridSpacing();
		double maxDepthUpper = minDepth + halfSpacing;
		double minDepthLower = maxDepth - halfSpacing;
		
		lowerEdge = new LocationList();
		upperEdge = new FaultTrace(null);
		for (Location loc : locs) {
			if (loc.getDepth() <= maxDepthUpper)
				upperEdge.add(loc);
			if (loc.getDepth() >= minDepthLower)
				lowerEdge.add(loc);
		}
	}

	@Override
	public synchronized FaultTrace getEvenlyDiscritizedUpperEdge() {
		calcUpperLower();
		return upperEdge;
	}

	@Override
	public synchronized LocationList getEvenlyDiscritizedLowerEdge() {
		calcUpperLower();
		return lowerEdge;
	}

	@Override
	public synchronized double getAveGridSpacing() {
		if (gridSpacingEst != null)
			return gridSpacingEst;
		DistsRecord[] distRecs = getCalcDistances();
		double[] spacings = new double[distRecs.length];
		for (int i=0; i<distRecs.length; i++)
			spacings[i] = distRecs[i].nearest3D.dist3D;
		double aveDist = StatUtils.mean(spacings);
		double maxDist = StatUtils.max(spacings);
		if (D) System.out.println("getAveGridSpacing(): ave="+(float)aveDist+", max="+(float)maxDist);
		uniformSpacing = DataUtils.getPercentDiff(maxDist, aveDist) <= 5d;
		if (!uniformSpacing)
			System.out.println("WARNING: not evenly spaced. Average distToClosest="+(float)aveDist+", max="+(float)maxDist);
		gridSpacingEst = aveDist;
		return aveDist;
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
	public synchronized FaultTrace getUpperEdge() {
		calcUpperLower();
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
		return locs.size() == 1;
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
		
		return new ArbitrarilyDiscretizedSurface(moved, isGridCentered());
	}

	@Override
	public RuptureSurface copyShallow() {
		LocationList locs2 = new LocationList();
		locs2.addAll(locs);
		return new ArbitrarilyDiscretizedSurface(locs2, isGridCentered());
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
		
		el.addAttribute("gridCentered", isGridCentered()+"");
		locs.toXMLMetadata(el);
		
		return root;
	}
	
	public static ArbitrarilyDiscretizedSurface fromXMLMetadata(Element el) {
		LocationList locs = LocationList.fromXMLMetadata(el.element(LocationList.XML_METADATA_NAME));
		Attribute centerEl = el.attribute("gridCentered");
		Boolean gridCentered = null;
		if (centerEl != null)
			gridCentered = Boolean.parseBoolean(centerEl.getValue());
		
		return new ArbitrarilyDiscretizedSurface(locs, gridCentered);
	}

	@Override
	public void clearCache() {
		cache.clearCache();
	}
	
	private static FaultTrace buildTestTrace(Location... locs) {
		FaultTrace trace = new FaultTrace(null);
		for (Location loc : locs)
			trace.add(loc);
		return trace;
	}
	
	public static void main(String[] args) {
		List<SimpleFaultData> testSFDs = new ArrayList<>();
		testSFDs.add(new SimpleFaultData(90, 10, 0, buildTestTrace(new Location(0, 0), new Location(1d, 1d))));
		testSFDs.add(new SimpleFaultData(90, 10, 4, buildTestTrace(new Location(0, 0), new Location(1d, 1d))));
		testSFDs.add(new SimpleFaultData(45, 10, 0, buildTestTrace(new Location(0, 0), new Location(1d, 1d))));
		testSFDs.add(new SimpleFaultData(45, 10, 4, buildTestTrace(new Location(0, 0), new Location(1d, 1d))));
		
		double[] gridSpacings = { 0.5d, 1d };
		double[] randScrambles = { 0d, 0.01d, 0.5d };
		for (SimpleFaultData sfd : testSFDs) {
			System.out.println("*********************");
			System.out.println("Upper: "+sfd.getUpperSeismogenicDepth());
			System.out.println("Lower: "+sfd.getLowerSeismogenicDepth());
			System.out.println("Dip: "+sfd.getAveDip());
			System.out.println("*********************");
			
			for (double gridSpacing : gridSpacings) {
				StirlingGriddedSurface gridSurf = new StirlingGriddedSurface(sfd, gridSpacing);
				System.out.println();
				System.out.println("Grid spacing: "+gridSpacing);
				System.out.println("Width: "+gridSurf.getAveWidth());
				System.out.println();
				LocationList locs = gridSurf.getEvenlyDiscritizedListOfLocsOnSurface();
				for (double randScramble : randScrambles) {
					System.out.println("Doing test with randScramble="+randScramble);
					LocationList myLocs;
					if (randScramble > 0) {
						myLocs = new LocationList();
						for (Location loc : locs) {
							double azimuth = Math.random()*360d;
							double horiz = Math.random()*randScramble;
							double vert = Math.random()*randScramble;
							LocationVector vector = new LocationVector(azimuth, horiz, vert);
							myLocs.add(LocationUtils.location(loc, vector));
						}
						
					} else {
						myLocs = locs;
					}
					System.out.println();
					ArbitrarilyDiscretizedSurface arbSurf = new ArbitrarilyDiscretizedSurface(myLocs);
					List<Double> arbVals = new ArrayList<>();
					List<Double> actualVals = new ArrayList<>();
					List<String> names = new ArrayList<>();
					
					names.add("spacing");
					arbVals.add(arbSurf.getAveGridSpacing());
					actualVals.add(gridSurf.getAveGridSpacing());
					
					names.add("area");
					arbVals.add(arbSurf.getArea());
					actualVals.add(gridSurf.getArea());
					
					names.add("length");
					arbVals.add(arbSurf.getAveLength());
					actualVals.add(gridSurf.getAveLength());
					
					names.add("width");
					arbVals.add(arbSurf.getAveWidth());
					actualVals.add(gridSurf.getAveWidth());
					
					names.add("dip");
					arbVals.add(arbSurf.getAveDip());
					actualVals.add(gridSurf.getAveDip());
					
					System.out.println("==== RESULTS ====");
					for (int i=0; i<names.size(); i++) {
						System.out.println("Quantity: "+names.get(i));
						System.out.println("\tArb: "+arbVals.get(i).floatValue());
						System.out.println("\tAct: "+actualVals.get(i).floatValue());
						System.out.println("\tDiff: "+(float)Math.abs(arbVals.get(i) - actualVals.get(i)));
						System.out.println("\t% Diff: "+(float)DataUtils.getPercentDiff(arbVals.get(i), actualVals.get(i))+" %");
					}
					System.out.println("=================");
				}
			}
		}
	}

}
