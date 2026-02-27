package org.opensha.sha.faultSurface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Function;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy;
import org.opensha.sha.faultSurface.cache.SurfaceDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;

import com.google.common.base.Preconditions;

public class NewCompoundSurface implements CacheEnabledSurface {
	
	/*
	 * These are populated by the top level init
	 */
	
	/**
	 * Original list in order passed in
	 */
	private List<? extends RuptureSurface> surfaces;
	/**
	 * Areas of each surface (in original order)
	 */
	private double[] surfaceAreas;
	/**
	 * Total area
	 */
	private double totArea;
	
	/*
	 * These are populated by the specific init methods
	 */
	
	/**
	 * Flags indicating if elements of {@link #surfaces} are on the upper edge (and should be included in Rx calcs)
	 */
	private boolean[] surfaceIsTop;
	/**
	 * Array of surfaces that constitute the upper edge
	 */
	private RuptureSurface[] topSurfaces;
	/**
	 * Array of surfaces that constitute the bottom edge; this can be equal to (or otherwise overlap with) {@link #topSurfaces}
	 */
	private RuptureSurface[] bottomSurfaces;
	/**
	 * Areas of the sections in {@link #topSurfaces}, used for area-weighting properties of the upper edge
	 */
	private double[] topSurfaceAreas;
	/**
	 * Areas of the sections in {@link #bottomSurfaces}, used for area-weighting properties of the lower edge
	 */
	private double[] bottomSurfaceAreas;
	/**
	 * Flags indicating if the traces of {@link #topSurfaces} need to be reversed
	 */
	private boolean[] topSurfacesReversed;
	/**
	 * Flags indicating if the traces of {@link #bottomSurfaces} need to be reversed
	 */
	private boolean[] bottomSurfacesReversed;
	
	// these are populated in the init methods
	private double avgDip;
	
	/*
	 * These are lazy-init
	 */
	private double length = Double.NaN;
	private double width = Double.NaN;
	private double horzWidth = Double.NaN;
	private double topDepth = Double.NaN;
	private double bottomDepth = Double.NaN;
	private FaultTrace upperEdge = null;
	
	private SurfaceDistanceCache cache = SurfaceCachingPolicy.build(this);

	public NewCompoundSurface(List<? extends RuptureSurface> surfaces, List<? extends FaultSection> sections) {
		init(surfaces, sections);
	}
	
	private NewCompoundSurface(List<? extends RuptureSurface> surfaces, double[] surfaceAreas, double totArea,
			boolean[] surfaceIsTop, RuptureSurface[] topSurfaces, RuptureSurface[] bottomSurfaces, double[] topSurfaceAreas,
			double[] bottomSurfaceAreas, boolean[] topSurfacesReversed, boolean[] bottomSurfacesReversed,
			double avgDip) {
		this.surfaces = surfaces;
		this.surfaceAreas = surfaceAreas;
		this.totArea = totArea;
		this.surfaceIsTop = surfaceIsTop;
		this.topSurfaces = topSurfaces;
		this.bottomSurfaces = bottomSurfaces;
		this.topSurfaceAreas = topSurfaceAreas;
		this.bottomSurfaceAreas = bottomSurfaceAreas;
		this.topSurfacesReversed = topSurfacesReversed;
		this.bottomSurfacesReversed = bottomSurfacesReversed;
		this.avgDip = avgDip;
	}

	private void init(List<? extends RuptureSurface> surfaces, List<? extends FaultSection> sections) {
		Preconditions.checkNotNull(surfaces, "Surfaces list is null");
		Preconditions.checkArgument(surfaces.size() > 1, "Must supply at least 2 surfaces (have %s)", surfaces.size());
		this.surfaces = surfaces;
		surfaceAreas = new double[surfaces.size()];
		totArea = 0d;
		for (int s=0; s<surfaceAreas.length; s++) {
			surfaceAreas[s] = surfaces.get(s).getArea();
			totArea += surfaceAreas[s];
		}
		
		if (sections != null) {
			Preconditions.checkArgument(sections.size() == surfaces.size(),
					"Passed in section list must be null or of equal size as the surfaces list: %s != %s",
					sections.size(), surfaces.size());
			// we have fault section data; do we have any down dip?
			if (sections.stream().anyMatch(S->S.getSubSectionIndexDownDip() > 0))
				// we have subsections down-dip
				initDownDip(sections);
			else
				initSimple();
		} else {
			initSimple();
		}
	}
	
	private void initSimple() {
		int numSurfaces = surfaces.size();
		
		// keep track of those we will need to reverse; don't do so just yet because we might have to flip the whole
		// surface at the end
		boolean[] reverse = new boolean[numSurfaces];
		
		// need to check the first 2 beforehand to establish the direction
		RuptureSurface surf1 = surfaces.get(0);
		RuptureSurface surf2 = surfaces.get(1);
		double[] dist = new double[4];
		// use cartesian dist sq, just need a quick and relative distance so don't bother with sqrt or true geographic transformations
		dist[0] = LocationUtils.cartesianDistanceSq(surf1.getFirstLocOnUpperEdge(), surf2.getFirstLocOnUpperEdge());
		dist[1] = LocationUtils.cartesianDistanceSq(surf1.getFirstLocOnUpperEdge(), surf2.getLastLocOnUpperEdge());
		dist[2] = LocationUtils.cartesianDistanceSq(surf1.getLastLocOnUpperEdge(), surf2.getFirstLocOnUpperEdge());
		dist[3] = LocationUtils.cartesianDistanceSq(surf1.getLastLocOnUpperEdge(), surf2.getLastLocOnUpperEdge());
		
		double min = dist[0];
		int minIndex = 0;
		for(int i=1; i<4;i++) {
			if(dist[i]<min) {
				minIndex = i;
				min = dist[i];
			}
		}
		
		if(minIndex==0) { // first_first
			reverse[0] = true;
			reverse[1] = false;
		} else if (minIndex==1) { // first_last
			reverse[0] = true;
			reverse[1] = true;
		} else if (minIndex==2) { // last_first
			reverse[0] = false;
			reverse[1] = false;
		} else { // minIndex==3 // last_last
			reverse[0] = false;
			reverse[1] = true;
		}
		
		// there was a bug in the prior implementation: it always compared against the original orientation of the prior
		// surface, not the potentially-reversed version; that's wrong, but may have never caused issues in practice.
		Location prevLast = reverse[1] ? surf2.getFirstLocOnUpperEdge() : surf2.getLastLocOnUpperEdge();
		double d1, d2;
		Location first, last;
		RuptureSurface surf;
		for (int s=2; s<numSurfaces; s++) {
			surf = surfaces.get(s);
			first = surf.getFirstLocOnUpperEdge();
			last = surf.getLastLocOnUpperEdge();
			d1 = LocationUtils.cartesianDistanceSq(prevLast, first);
			d2 = LocationUtils.cartesianDistanceSq(prevLast, last);
			reverse[s] = d2 < d1;
			prevLast = reverse[s] ? first : last;
		}
		
		double avgDip = 0d;
		double sumArea = 0d;
		for (int s=0; s<numSurfaces; s++) {
			surf = surfaces.get(s);
			double dip = surf.getAveDip();
			if (reverse[s])
				avgDip += (180d-dip)*surfaceAreas[s];
			else
				avgDip += dip*surfaceAreas[s];
			sumArea += surfaceAreas[s];
		}
		avgDip /= sumArea;
		Preconditions.checkState(avgDip > 0 && avgDip < 180d, "Bad avgDip=%s", avgDip);
		
		if (avgDip > 90d) {
			// whole surface is reversed according to aki & richards, need to flip it
			avgDip = 180d-avgDip;
			
			topSurfaces = new RuptureSurface[numSurfaces];
			topSurfaceAreas = new double[numSurfaces];
			topSurfacesReversed = new boolean[numSurfaces];
			int index = 0;
			for (int s=numSurfaces; --s>=0;) {
				topSurfaces[index] = surfaces.get(s);
				topSurfaceAreas[index] = surfaceAreas[s];
				topSurfacesReversed[index] = !reverse[s];
				index++;
			}
		} else {
			topSurfaces = surfaces.toArray(new RuptureSurface[numSurfaces]);
			topSurfaceAreas = surfaceAreas;
			topSurfacesReversed = reverse;
		}
		bottomSurfaces = topSurfaces;
		bottomSurfaceAreas = topSurfaceAreas;
		bottomSurfacesReversed = topSurfacesReversed;
		surfaceIsTop = new boolean[numSurfaces];
		for (int i=0; i<numSurfaces; i++)
			surfaceIsTop[i] = true;
		
		this.avgDip = avgDip;
		this.totArea = sumArea;
	}
	
//	private static class EdgeSupplier implements Supplier<LocationList> {
//		
//		private final RuptureSurface surf;
//		private final boolean upper;
//		private final boolean reversed;
//		private volatile LocationList cached;
//
//		public EdgeSupplier(RuptureSurface surf, boolean upper, boolean reversed) {
//			this.surf = surf;
//			this.upper = upper;
//			this.reversed = reversed;
//		}
//
//		@Override
//		public LocationList get() {
//			if (cached != null)
//				return cached;
//			LocationList edge = upper ? surf.getEvenlyDiscritizedUpperEdge() : surf.getEvenlyDiscritizedLowerEdge();
//			if (reversed) {
//				LocationList reversedEdge = new LocationList(edge.size());
//				for (int i=edge.size(); --i>=0;)
//					reversedEdge.add(edge.get(i));
//				edge = reversedEdge;
//			}
//			cached = edge;
//			return edge;
//		}
//		
//	}
	
	private void initDownDip(List<? extends FaultSection> sections) {
		// TODO
	}

	@Override
	public double getAveDip() {
		return avgDip;
	}

	@Override
	public double getAveStrike() {
		return getUpperEdge().getStrikeDirection();
	}

	@Override
	public double getAveLength() {
		if (Double.isNaN(length)) {
			double length = 0d;
			for (RuptureSurface surf : topSurfaces)
				length += surf.getAveLength();
			this.length = length;
		}
		return length;
	}
	
	public boolean hasSurfacesDownDip() {
		return topSurfaces != bottomSurfaces;
	}

	@Override
	public double getAveWidth() {
		if (Double.isNaN(width)) {
			if (hasSurfacesDownDip()) {
				// we have subsections down-dip, approximate it
				double upper = getAveRupTopDepth();
				double lower = getAveRupBottomDepth();
				width = (lower - upper)/Math.sin(Math.toRadians(avgDip));
			} else {
				// simple
				double width = 0d;
				for (int s=0; s<topSurfaces.length; s++)
					width += topSurfaces[s].getAveWidth()*topSurfaceAreas[s];
				this.width = width/totArea;
			}
		}
		return width;
	}

	@Override
	public double getAveHorizontalWidth() {
		if (Double.isNaN(horzWidth)) {
			if (hasSurfacesDownDip()) {
				// we have subsections down-dip, approximate it
				double width = getAveWidth();
				horzWidth = width * Math.cos(Math.toRadians(avgDip));
			} else {
				double horzWidth = 0;
				for (int s=0; s<topSurfaces.length; s++)
					horzWidth += topSurfaces[s].getAveHorizontalWidth()*topSurfaceAreas[s];
				this.horzWidth = horzWidth/totArea;
			}
		}
		return horzWidth;
	}

	@Override
	public double getArea() {
		return totArea;
	}

	@Override
	public double getAreaInsideRegion(Region region) {
		double area = 0d;
		for (RuptureSurface surf : surfaces)
			area += surf.getAreaInsideRegion(region);
		return area;
	}

	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		// modified to use an expected size
		// not caching in order to avoid memory bloat, but individual surfaces should already be cached, making this quick'
		int count = 0;
		List<LocationList> surfLists = new ArrayList<>(surfaces.size());
		for(RuptureSurface surf:surfaces) {
			LocationList surfList = surf.getEvenlyDiscritizedListOfLocsOnSurface();
			count += surfList.size();
			surfLists.add(surfList);
		}
		LocationList locs = new LocationList(count);
		for (LocationList surfList : surfLists)
			locs.addAll(surfList);
		return locs;
	}

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		return getEvenlyDiscretizedEdge(true);
	}

	@Override
	public FaultTrace getEvenlyDiscritizedLowerEdge() {
		return getEvenlyDiscretizedEdge(false);
	}
	
	private FaultTrace getEvenlyDiscretizedEdge(boolean upper) {
		RuptureSurface[] sects = upper ? topSurfaces : bottomSurfaces;
		boolean[] reversed = upper ? topSurfacesReversed : bottomSurfacesReversed;
		FaultTrace evenUpperEdge = new FaultTrace(null);
		for (int s=0; s<sects.length; s++) {
			LocationList trace = upper ? sects[s].getEvenlyDiscritizedUpperEdge() : sects[s].getEvenlyDiscritizedLowerEdge();
			if (reversed[s]) {
				for (int i=trace.size(); --i>=0;)
					evenUpperEdge.add(trace.get(i));
			} else {
				evenUpperEdge.addAll(trace);
			}
		}
		
		return evenUpperEdge;
	}

	@Override
	public double getAveGridSpacing() {
		double avgSpacing = 0d;
		for (int s=0; s<surfaceAreas.length; s++)
			avgSpacing += surfaces.get(s).getAveGridSpacing()*surfaceAreas[s];
		avgSpacing /= totArea;
		return avgSpacing;
	}

	@Override
	public double getDistanceJB(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceJB();
	}

	@Override
	public double getDistanceRup(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceRup();
	}

	@Override
	public double getDistanceX(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceX();
	}
	
	@Override
	public double getQuickDistance(Location siteLoc) {
		return cache.getQuickDistance(siteLoc);
	}
	
	@Override
	public SurfaceDistances getDistances(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc);
	}

	@Override
	public double getAveRupTopDepth() {
		if (Double.isNaN(topDepth)) {
			double topDepth = 0d;
			double sumArea = 0d;
			for (int s=0; s<topSurfaces.length; s++) {
				topDepth += topSurfaces[s].getAveRupTopDepth()*topSurfaceAreas[s];
				sumArea += topSurfaceAreas[s];
			}
			this.topDepth = topDepth/sumArea;
		}
		return topDepth;
	}

	@Override
	public double getAveRupBottomDepth() {
		if (Double.isNaN(bottomDepth)) {
			double bottomDepth = 0d;
			double sumArea = 0d;
			for (int s=0; s<bottomSurfaces.length; s++) {
				bottomDepth += bottomSurfaces[s].getAveRupBottomDepth()*bottomSurfaceAreas[s];
				sumArea += bottomSurfaceAreas[s];
			}
			this.bottomDepth = bottomDepth/sumArea;
		}
		return bottomDepth;
	}

	@Override
	public double getAveDipDirection() {
		double dipDir = getAveStrike() + 90;
		while (dipDir > 360d)
			dipDir -= 360d;
		return dipDir;
	}

	@Override
	public FaultTrace getUpperEdge() {
		if (upperEdge == null) {
			FaultTrace upperEdge = new FaultTrace(null, topSurfaces.length);
			for (int s=0; s<topSurfaces.length; s++) {
				FaultTrace trace;
				try {
					// some surfaces don't support getUpperEdge in some circumstances,
					// so revert to evenly discretized upper if needed
					trace = topSurfaces[s].getUpperEdge();
				} catch (RuntimeException e) {
					trace = topSurfaces[s].getEvenlyDiscritizedUpperEdge();
				}
				if (topSurfacesReversed[s]) {
					for (int i=trace.size(); --i>=0;)
						upperEdge.add(trace.get(i));
				} else {
					upperEdge.addAll(trace);
				}
			}
			this.upperEdge = upperEdge;
		}
		return upperEdge;
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		return topSurfacesReversed[0] ? topSurfaces[0].getLastLocOnUpperEdge() : topSurfaces[0].getFirstLocOnUpperEdge();
	}

	@Override
	public Location getLastLocOnUpperEdge() {
		int last = topSurfaces.length-1;
		return topSurfacesReversed[last] ? topSurfaces[last].getFirstLocOnUpperEdge() : topSurfaces[last].getLastLocOnUpperEdge();
	}

	@Override
	public double getFractionOfSurfaceInRegion(Region region) {
		LocationList locList = getEvenlyDiscritizedListOfLocsOnSurface();
		double numInside = 0;
		for(Location loc: locList)
			if(region.contains(loc))
				numInside += 1;
		return numInside/(double)locList.size();
	}

	@Override
	public String getInfo() {
		// TODO could populate with top/bottom and reversed info if ever useful
		return "";
	}

	@Override
	public double getMinDistance(RuptureSurface surface) {
		double minDist = Double.POSITIVE_INFINITY;
		for (RuptureSurface mySurf : surfaces)
			minDist = Math.min(minDist, mySurf.getMinDistance(surface));
		return minDist;
	}

	@Override
	public RuptureSurface getMoved(LocationVector v) {
		Map<RuptureSurface, RuptureSurface> movedInstances = new HashMap<>(surfaces.size());
		List<RuptureSurface> movedSurfaces = new ArrayList<>(surfaces.size());
		for (RuptureSurface surf : surfaces) {
			RuptureSurface moved = surf.getMoved(v);
			movedSurfaces.add(moved);
			movedInstances.put(surf, moved);
		}
		
		// these can be in reverse order for the surface list even if we don't have any down-dip
		RuptureSurface[] movedTopSurfaces = new RuptureSurface[topSurfaces.length];
		for (int s=0; s<topSurfaces.length; s++)
			movedTopSurfaces[s] = movedInstances.get(topSurfaces[s]);
		
		RuptureSurface[] movedBottomSurfaces;
		if (hasSurfacesDownDip()) {
			movedBottomSurfaces = new RuptureSurface[bottomSurfaces.length];
			for (int s=0; s<bottomSurfaces.length; s++)
				movedBottomSurfaces[s] = movedInstances.get(bottomSurfaces[s]);
		} else {
			movedBottomSurfaces = movedTopSurfaces;
		}
		return new NewCompoundSurface(movedSurfaces, surfaceAreas, totArea, surfaceIsTop, movedTopSurfaces, movedBottomSurfaces, topSurfaceAreas,
				bottomSurfaceAreas, topSurfacesReversed, bottomSurfacesReversed, avgDip);
	}

	@Override
	public RuptureSurface copyShallow() {
		return new NewCompoundSurface(surfaces, surfaceAreas, totArea, surfaceIsTop, topSurfaces, bottomSurfaces, topSurfaceAreas,
				bottomSurfaceAreas, topSurfacesReversed, bottomSurfacesReversed, avgDip);
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		return getEvenlyDiscritizedListOfLocsOnSurface().listIterator();
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		// TODO this omits side connectors and isn't a true closed perimeter; revisit with the down-dip implementation.
		LocationList perim = new LocationList();
		perim.addAll(getEvenlyDiscritizedUpperEdge());
		for (int s=bottomSurfaces.length; --s>=0;) {
			LocationList lower = bottomSurfaces[s].getEvenlyDiscritizedLowerEdge();
			// need to go backwards on the bottom, so reverse logic is flipped here
			if (bottomSurfacesReversed[s]) {
				// go forwards
				perim.addAll(lower);
			} else {
				// go backwards
				for (int i=lower.size(); --i>=0;)
					perim.add(lower.get(i));
			}
		}
		return perim;
	}

	@Override
	public LocationList getPerimeter() {
		return getEvenlyDiscritizedPerimeter();
	}

	@Override
	public boolean isPointSurface() {
		return false;
	}

	@Override
	public SurfaceDistances calcDistances(Location loc) {
		double distanceJB = Double.MAX_VALUE;
		double distanceRup = Double.MAX_VALUE;
		double distanceRupTop = Double.MAX_VALUE;
		double dist;
		RuptureSurface surfForX = null;
		for (int i=0; i<surfaces.size(); i++) {
			RuptureSurface surf = surfaces.get(i);
			dist = surf.getDistanceJB(loc);
			if (dist<distanceJB) distanceJB=dist;
			dist = surf.getDistanceRup(loc);
			if (dist < distanceRup)
				distanceRup = dist;
			if (surfaceIsTop[i] && dist < distanceRupTop) {
				distanceRupTop = dist;
				surfForX = surf;
			}
		}
		// use the closest sub-surface (determined via rRup) for distanceX
		final RuptureSurface theSurfForX = surfForX;
		return new SurfaceDistances.PrecomputedLazyX(loc, distanceRup, distanceJB, new Function<Location, Double>() {
			
			@Override
			public Double apply(Location t) {
				return theSurfForX.getDistanceX(loc);
			}
		});
	}

	@Override
	public double calcQuickDistance(Location siteLoc) {
		double minDist = Double.POSITIVE_INFINITY;
		for (RuptureSurface surf : surfaces)
			minDist = Math.min(minDist, surf.getQuickDistance(siteLoc));
		return minDist;
	}

	@Override
	public void clearCache() {
		cache.clearCache();
	}

}
