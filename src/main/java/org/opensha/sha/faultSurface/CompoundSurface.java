package org.opensha.sha.faultSurface;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.dao.db.PrefFaultSectionDataDB_DAO;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.SingleLocDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy;
import org.opensha.sha.faultSurface.cache.SurfaceDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.collect.Lists;

/**
 * This class represents compound RuptureSurface to represent multi-fault ruptures. 
 * The most challenging thing here is maintaining the Aki Richards convention for the total
 * surface.  The main method here was used to make various tests to ensure that these things are
 * handled properly (these data were analyzed externally using Igor).
 * 
 * @author field
 *
 */
public class CompoundSurface implements RuptureSurface, CacheEnabledSurface {
	
	List<? extends RuptureSurface> surfaces;
	
	final static boolean D = false;
	
	// this tells whether any traces need to be reversed
	boolean[] reverseSurfTrace; //  indicates which surface traces need to be reversed in building the entire upper surface
	boolean reverseOrderOfSurfaces = false; // indicates whether the order of surfaces needs to be reversed to honor Aki and Richards
	double aveDip, totArea,aveLength=-1,aveRupTopDepth=-1,aveWidth=-1, aveGridSpacing=-1;
	FaultTrace upperEdge = null;
	
	private SurfaceDistanceCache cache = SurfaceCachingPolicy.build(this);
	
	public CompoundSurface(List<? extends RuptureSurface> surfaces) {
		this.surfaces = surfaces;
		computeInitialStuff();
	}
	
	
	/** this returns the list of surfaces provided in the constructor
	 * 
	 * @return ArrayList<EvenlyGriddedSurface>
	 */
	public List<? extends RuptureSurface> getSurfaceList() {
		return surfaces;
	}
	
	/**
	 * @param index
	 * @return true if the surface at the given index is reversed when building the entire surface
	 */
	public boolean isSubSurfaceReversed(int index) {
		return reverseSurfTrace[index];
	}
	
	
	private void computeInitialStuff() {
		
		reverseSurfTrace = new boolean[surfaces.size()];

		// determine if either of the first two sections need to be reversed
		RuptureSurface surf1 = surfaces.get(0);
		RuptureSurface surf2 = surfaces.get(1);
		double[] dist = new double[4];
		dist[0] = LocationUtils.horzDistanceFast(surf1.getFirstLocOnUpperEdge(), surf2.getFirstLocOnUpperEdge());
		dist[1] = LocationUtils.horzDistanceFast(surf1.getFirstLocOnUpperEdge(), surf2.getLastLocOnUpperEdge());
		dist[2]= LocationUtils.horzDistanceFast(surf1.getLastLocOnUpperEdge(), surf2.getFirstLocOnUpperEdge());
		dist[3] = LocationUtils.horzDistanceFast(surf1.getLastLocOnUpperEdge(), surf2.getLastLocOnUpperEdge());
		
		double min = dist[0];
		int minIndex = 0;
		for(int i=1; i<4;i++) {
			if(dist[i]<min) {
				minIndex = i;
				min = dist[i];
			}
		}

		if(D) {
			for(int i=0;i<4;i++)
				System.out.println("\t"+i+"\t"+dist[i]);
			if(D) System.out.println("minIndex="+minIndex);
		}
		if(minIndex==0) { // first_first
			reverseSurfTrace[0] = true;
			reverseSurfTrace[1] = false;
		}
		else if (minIndex==1) { // first_last
			reverseSurfTrace[0] = true;
			reverseSurfTrace[1] = true;
		}
		else if (minIndex==2) { // last_first
			reverseSurfTrace[0] = false;
			reverseSurfTrace[1] = false;
		}
		else { // minIndex==3 // last_last
			reverseSurfTrace[0] = false;
			reverseSurfTrace[1] = true;
		}

		// determine which subsequent sections need to be reversed
		for(int i=1; i< surfaces.size()-1; i++) {
			surf1 = surfaces.get(i);
			surf2 = surfaces.get(i+1);
			double d1 = LocationUtils.horzDistanceFast(surf1.getLastLocOnUpperEdge(), surf2.getFirstLocOnUpperEdge());
			double d2 = LocationUtils.horzDistanceFast(surf1.getLastLocOnUpperEdge(), surf2.getLastLocOnUpperEdge());
			if(d1<d2)
				reverseSurfTrace[i+1] = false;
			else
				reverseSurfTrace[i+1] = true;
		}
		
		// compute average dip (wt averaged by area) & total area
		aveDip = 0;
		totArea=0;
		for(int s=0; s<surfaces.size();s++) {
			RuptureSurface surf = surfaces.get(s);
			double area = surf.getArea();
			double dip;
			try {
				dip = surf.getAveDip();
			} catch (Exception e) {
				dip = Double.NaN;
			}
			totArea += area;
			if(reverseSurfTrace[s])
				aveDip += (180-dip)*area;
			else
				aveDip += dip*area;
		}
		aveDip /= totArea;  // wt averaged by area
		if(aveDip > 90.0) {
			aveDip = 180-aveDip;
			reverseOrderOfSurfaces = true;
		}
		
		if(D) {
			System.out.println("aveDip="+(float)aveDip);
			System.out.println("reverseOrderOfSurfaces="+reverseOrderOfSurfaces);
			for(int i=0;i<reverseSurfTrace.length;i++)
				System.out.println("reverseSurfTrace "+i+" = "+reverseSurfTrace[i]);
		}
	}
	

	@Override
	public double getArea() {
		return totArea;
	}

	@Override
	public double getAveDip() {
		return aveDip;
	}

	@Override
	/**
	 * This returns getUpperEdge().getDipDirection() 
	 */
	public double getAveDipDirection() {
		return this.getUpperEdge().getDipDirection();
	}

	@Override
	/**
	 * This computes the grid spacing wt-averaged by area
	 */
	public synchronized double getAveGridSpacing() {
		if(aveGridSpacing == -1) {
			aveGridSpacing = 0;
			for(RuptureSurface surf: surfaces) {
				aveGridSpacing += surf.getAveGridSpacing()*surf.getArea();
			}
			aveGridSpacing /= getArea();
		}
		return aveGridSpacing;
	}

	@Override
	/**
	 * This sums the lengths of the given surfaces
	 */
	public synchronized double getAveLength() {
		if(aveLength == -1) {
			aveLength = 0;
			for(RuptureSurface surf: surfaces) {
				aveLength += surf.getAveLength();
			}
		}
		return aveLength;
	}

	@Override
	/**
	 * This returns the area-wt-averaged rup-top depths of the given surfaces
	 */
	public synchronized double getAveRupTopDepth() {
		if(aveRupTopDepth == -1) {
			aveRupTopDepth = 0;
			for(RuptureSurface surf: surfaces) {
				aveRupTopDepth += surf.getAveRupTopDepth()*surf.getArea();
			}
			aveRupTopDepth /= getArea();
		}
		return aveRupTopDepth;
	}

	@Override
	/**
	 * This returns getUpperEdge().getAveStrike()
	 */
	public double getAveStrike() {
		return getUpperEdge().getAveStrike();
	}

	@Override
	/**
	 * This returns the area-wt-averaged width of the given surfaces
	 */
	public synchronized double getAveWidth() {
		if(aveWidth == -1) {
			aveWidth = 0;
			for(RuptureSurface surf: surfaces) {
				aveWidth += surf.getAveWidth()*surf.getArea();
			}
			aveWidth /= getArea();
		}
		return aveWidth;
	}
	
	private static class CompoundSurfaceDistances extends SurfaceDistances {

		private final int distXIndex;
		public CompoundSurfaceDistances(double distanceRup, double distanceJB,
				double distanceSeis, int distXIndex) {
			super(distanceRup, distanceJB, distanceSeis);
			this.distXIndex = distXIndex;
		}
		
	}

	@Override
	public CompoundSurfaceDistances calcDistances(Location loc) {
		double distanceJB = Double.MAX_VALUE;
		double distanceSeis = Double.MAX_VALUE;
		double distanceRup = Double.MAX_VALUE;
		double dist;
		int distXidx = -1;
		for (int i=0; i<surfaces.size(); i++) {
			RuptureSurface surf = surfaces.get(i);
			dist = surf.getDistanceJB(loc);
			if (dist<distanceJB) distanceJB=dist;
			dist = surf.getDistanceRup(loc);
			if (dist<distanceRup) {
				distanceRup=dist;
				distXidx = i;
			}
			dist = surf.getDistanceSeis(loc);
			if (dist<distanceSeis) distanceSeis=dist;
		}
		return new CompoundSurfaceDistances(distanceRup, distanceJB, distanceSeis, distXidx);
	}


	@Override
	public double calcDistanceX(Location loc) {
		// new implementation relies on knowing the index of the surface of the
		// smallest rRup; first ensure that rRup calc has been done for
		// supplied Location; in all likelihood another distance metric will
		// have already been queried with the supplied site and this call will
		// be skipped.
		CompoundSurfaceDistances distances = (CompoundSurfaceDistances)cache.getSurfaceDistances(loc);
		return surfaces.get(distances.distXIndex).getDistanceX(loc);
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
	public double getDistanceSeis(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceSeis();
	}

	@Override
	public double getDistanceX(Location siteLoc) {
		return cache.getDistanceX(siteLoc);
	}
	
	@Override
	/**
	 * This simply adds what's returned from the getEvenlyDiscritizedListOfLocsOnSurface() 
	 * method of each surface to a big master list.
	 */
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		LocationList locList = new LocationList();
		for(RuptureSurface surf:surfaces) {
			locList.addAll(surf.getEvenlyDiscritizedListOfLocsOnSurface());
		}
		return locList;
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		// TODO verify
		LocationList perimeter = new LocationList();
		LocationList upper = getEvenlyDiscritizedUpperEdge();
		LocationList lower = getEvenlyDiscritizedLowerEdge();
		LocationList right = GriddedSurfaceUtils.getEvenlyDiscretizedLine(
				upper.last(), lower.last(), getAveGridSpacing());
		LocationList left = GriddedSurfaceUtils.getEvenlyDiscretizedLine(
				lower.first(), upper.first(), getAveGridSpacing());
		// add the upper edge
		perimeter.addAll(upper);
		// make the "right" side
		perimeter.addAll(right);
		// add reversed lower
		for (int i=lower.size(); --i>=0;)
			perimeter.add(lower.get(i));
		// add "left"
		perimeter.addAll(left);
		return perimeter;
		
//		// make the lower edge
//		if(reverseOrderOfSurfaces) {
//			for(int s=0; s<surfaces.size(); s++) {
//				RuptureSurface surf = surfaces.get(s);
//				if(reverseSurfTrace[s]) { // start at the beginning
//					for(int c=surf.getNumCols()-1; c>=0 ; c--)
//						perimeter.add(surf.getLocation(surf.getNumRows()-1, c));					
//				}
//				else { // start at the end
//					for(int c=0; c< surf.getNumCols(); c++)
//						perimeter.add(surf.getLocation(surf.getNumRows()-1, c));
//				}
//			}
//		}
//		else { // no reverse order of surfaces; start at last surface
//			for(int s=surfaces.size()-1; s>=0; s--) {
//				RuptureSurface surf = surfaces.get(s);
//				if(reverseSurfTrace[s]) { // start at the beginning
//					for(int c=0; c< surf.getNumCols(); c++)
//						perimeter.add(surf.getLocation(surf.getNumRows()-1, c));
//				}
//				else { // start at the end
//					for(int c=surf.getNumCols()-1; c>=0 ; c--)
//						perimeter.add(surf.getLocation(surf.getNumRows()-1, c));					
//				}
//			}
//		}
//		return perimeter;
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
		FaultTrace evenUpperEdge = new FaultTrace(null);
			if(reverseOrderOfSurfaces) {
				for(int s=surfaces.size()-1; s>=0;s--) {
					LocationList trace;
					if (upper)
						trace = surfaces.get(s).getEvenlyDiscritizedUpperEdge();
					else
						trace = surfaces.get(s).getEvenlyDiscritizedLowerEdge();
					if(reverseSurfTrace[s]) {
						for(int i=0; i<trace.size();i++)
							evenUpperEdge.add(trace.get(i));
					}
					else {
						for(int i=trace.size()-1; i>=0;i--)
							evenUpperEdge.add(trace.get(i));
					}
				}				
			}
			else { // don't reverse order of surfaces
				for(int s=0; s<surfaces.size();s++) {
					LocationList trace;
					if (upper)
						trace = surfaces.get(s).getEvenlyDiscritizedUpperEdge();
					else
						trace = surfaces.get(s).getEvenlyDiscritizedLowerEdge();
					if(reverseSurfTrace[s]) {
						for(int i=trace.size()-1; i>=0;i--)
							evenUpperEdge.add(trace.get(i));
					}
					else {
						for(int i=0; i<trace.size();i++)
							evenUpperEdge.add(trace.get(i));
					}
				}
			}
		return evenUpperEdge;
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		return getUpperEdge().get(0);
	}
	

	@Override
	public Location getLastLocOnUpperEdge() {
		return getUpperEdge().get(getUpperEdge().size()-1);
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
//		throw new RuntimeException("Need to implement this method");
		// TODO implement
		return "";
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LocationList getPerimeter() {
		return getEvenlyDiscritizedPerimeter();
	}

	@Override
	/**
	 * Should we remove adjacent points that are very close to each other
	 */
	public FaultTrace getUpperEdge() {
		if(upperEdge == null) {
			upperEdge = new FaultTrace(null);
			if(reverseOrderOfSurfaces) {
				for(int s=surfaces.size()-1; s>=0;s--) {
					FaultTrace trace;
					try {
						trace = surfaces.get(s).getUpperEdge();
					} catch (RuntimeException e) {
						// some surfaces don't support getUpperEdge in some circumstances,
						// so revert to evenly discretized upper if needed
						trace = surfaces.get(s).getEvenlyDiscritizedUpperEdge();
					}
					if(reverseSurfTrace[s]) {
						for(int i=0; i<trace.size();i++)
							upperEdge.add(trace.get(i));
					}
					else {
						for(int i=trace.size()-1; i>=0;i--)
							upperEdge.add(trace.get(i));
					}
				}				
			}
			else { // don't reverse order of surfaces
				for(int s=0; s<surfaces.size();s++) {
					FaultTrace trace;
					try {
						// some surfaces don't support getUpperEdge in some circumstances,
						// so revert to evenly discretized upper if needed
						trace = surfaces.get(s).getUpperEdge();
					} catch (RuntimeException e) {
						trace = surfaces.get(s).getEvenlyDiscritizedUpperEdge();
					}
					if(reverseSurfTrace[s]) {
						for(int i=trace.size()-1; i>=0;i--)
							upperEdge.add(trace.get(i));
					}
					else {
						for(int i=0; i<trace.size();i++)
							upperEdge.add(trace.get(i));
					}
				}
			}
		}
		return upperEdge;
	}

	@Override
	public boolean isPointSurface() {
		return false;
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

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		// this get's the DB accessor (version 3)
	    System.out.println("Accessing database...");
	    DB_AccessAPI db = DB_ConnectionPool.getDB3ReadOnlyConn();
	    PrefFaultSectionDataDB_DAO faultSectionDB_DAO = new PrefFaultSectionDataDB_DAO(db);
	    List<FaultSectionPrefData> sections = faultSectionDB_DAO.getAllFaultSectionPrefData();
//	    for(int s=0; s<sections.size();s++) {
//	    	FaultSectionPrefData data = sections.get(s);
//	    	System.out.println(s+"\t"+data.getName());
//	    }
	    System.out.println("Done accessing database.");

	    
	    SimpleFaultData sierraMadre = sections.get(367).getSimpleFaultData(true);
//	    System.out.println(sierraMadre.getFaultTrace());
//	    sierraMadre.getFaultTrace().reverse();
//	    System.out.println(sierraMadre.getFaultTrace());
	    SimpleFaultData cucamonga = sections.get(74).getSimpleFaultData(true);
//	    System.out.println(cucamonga.getFaultTrace());
	    cucamonga.getFaultTrace().reverse();
//	    System.out.println(cucamonga.getFaultTrace());
	    SimpleFaultData sanJacintoSanBer = sections.get(332).getSimpleFaultData(true);
	    sanJacintoSanBer.getFaultTrace().reverse();
	    System.out.println(sierraMadre.getFaultTrace().getName());
	    System.out.println(cucamonga.getFaultTrace().getName());
	    System.out.println(sanJacintoSanBer.getFaultTrace().getName());
	    ArrayList<EvenlyGriddedSurface> surfList = new ArrayList<EvenlyGriddedSurface>();
	    surfList.add(new StirlingGriddedSurface(sierraMadre,1.0));
	    surfList.add(new StirlingGriddedSurface(cucamonga,1.0));
	    surfList.add(new StirlingGriddedSurface(sanJacintoSanBer,1.0));
	    
	    CompoundSurface compoundSurf = new CompoundSurface(surfList);
	    
	    System.out.println("aveDipDir="+compoundSurf.getAveDipDirection());
	    System.out.println("aveStrike="+compoundSurf.getAveStrike());
	    System.out.println("aveGridSpacing="+compoundSurf.getAveGridSpacing());
	    System.out.println("aveArea="+compoundSurf.getArea()+"\t(should be "+(surfList.get(0).getArea()+
	    		surfList.get(1).getArea()+surfList.get(2).getArea())+")");
	    System.out.println("aveLength="+compoundSurf.getAveLength()+"\t("+surfList.get(0).getAveLength()+"+"+
	    		surfList.get(1).getAveLength()+"+"+surfList.get(2).getAveLength()+")");
	    System.out.println("aveWidth="+compoundSurf.getAveWidth()+"\t("+surfList.get(0).getAveWidth()+"\t"+
	    		surfList.get(1).getAveWidth()+"\t"+surfList.get(2).getAveWidth()+")");
	    System.out.println("aveRupTopDepth="+compoundSurf.getAveRupTopDepth()+"\t("+surfList.get(0).getAveRupTopDepth()+"\t"+
	    		surfList.get(1).getAveRupTopDepth()+"\t"+surfList.get(2).getAveRupTopDepth()+")");
	    
	    System.out.println("first loc: "+compoundSurf.getFirstLocOnUpperEdge());
	    System.out.println("last loc: "+compoundSurf.getLastLocOnUpperEdge());
	    
//	    System.out.println("sm_lat\tsm_lon\tsm_dep");
//	    for(Location loc:surfList.get(0).getEvenlyDiscritizedListOfLocsOnSurface())
//		    System.out.println(loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth());
//	    System.out.println("c_lat\tc_lon\tc_dep");
//	    for(Location loc:surfList.get(1).getEvenlyDiscritizedListOfLocsOnSurface())
//		    System.out.println(loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth());
//	    System.out.println("sj_lat\tsj_lon\tsj_dep");
//	    for(Location loc:surfList.get(2).getEvenlyDiscritizedListOfLocsOnSurface())
//		    System.out.println(loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth());

	    System.out.println("tr_lat\ttr_lon\ttr_dep");
	    for(Location loc:compoundSurf.getUpperEdge())
		    System.out.println(loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth());

	    System.out.println("tr2_lat\ttr2_lon\ttr2_dep");
	    for(Location loc:compoundSurf.getEvenlyDiscritizedUpperEdge())
		    System.out.println(loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth());

	    System.out.println("per_lat\tper_lon\tper_dep");
	    for(Location loc:compoundSurf.getEvenlyDiscritizedPerimeter())
		    System.out.println(loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth());
    
	    System.out.println("done");
	    
	    

	}


	@Override
	public RuptureSurface getMoved(LocationVector v) {
		List<RuptureSurface> movedSurfs = Lists.newArrayList();
		for (RuptureSurface surf : this.surfaces)
			movedSurfs.add(surf.getMoved(v));
		return new CompoundSurface(movedSurfs);
	}


	@Override
	public CompoundSurface copyShallow() {
		return new CompoundSurface(surfaces);
	}


}
