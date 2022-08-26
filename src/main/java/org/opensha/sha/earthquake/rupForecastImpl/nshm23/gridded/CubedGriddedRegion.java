package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;

import scratch.UCERF3.utils.RELM_RegionUtils;

/**
 * This class represents a 3D geographic volume discretized into "cubes" (default size is about 2 by 2 by 2 km).  It does so by
 * subdividing the supplied griddedRegion and depth according the values supplied in the constructor.
 * 
 * Note that a modified version of the gridded region is created and used internally to ensure that each grid cell has
 * a complete set of cubes and that no cubes are outside a cell of the griddedRegion.  This is done by creating and 
 * using a new Region (perimeter) that traces the actual exterior cell edges of the original griddedGegion (using the 
 * getRegionDefinedByExteriorCellEdges(griddedRegion) method).  Both regions have the exact same grid cells.
 * 
 * A subclass of this could replace ETAS_CubeDiscretizationParams if the following methods in the latter added
 * to this new subclass:
 * 
 *      getGridRegForParentLocs()
 *      getLocationWeightCalc(ETAS_ParameterList etasParams)
 *      
 * (the deprecated methods here are to honor the names in ETAS_CubeDiscretizationParams; delete these after references are updated)
 * 
 * This ETAS_CubedGriddedRegion could also be used to replace much of the bookkeeping in ETAS_PrimaryEventSampler.  Note that
 * there will be a different number/set of cubes from what was used in U3ETAS (the latter has cubes outside cells and cells that
 * do not have a complete set of cubes).
 * 
 * 
 * 
 * 
 * 
 * @author field
 *
 */
public class CubedGriddedRegion {
	
	final static boolean D = true;
	
	final static double DEFAULT_MAX_DEPTH = 24;	// km
	final static int DEFAULT_NUM_CUBE_DEPTHS = 12; // 2 km hights
	final static int DEFAULT_NUM_CUBES_PER_GRID_EDGE = 5;
	
	private double maxDepth;
	private int numCubeDepths;
	private int numCubesPerGridEdge; 
	private double cubeLatLonSpacing;
	private double cubeDepthDiscr;
	private int numCubesPerDepth;
	private int numCubes;
	private double maxFaultNuclDist;
	private int numCubesPerGridCell;
	
	private GriddedRegion griddedRegion;
	private GriddedRegion griddedRegionModified;
	private GriddedRegion gridRegForCubes;
	
	private double[] latForCubeCenter;
	private double[] lonForCubeCenter;
	private double[] depthForCubeCenter;

	
	/**
	 * This creates a cubeGriddedRegion with cubes that are 0.02 degrees wide and 2 km deep
	 * (maxDepth=24 km; numCubeDepths=12; and numCubesPerGridEdge=5)
	 * @param origGriddedRegion
	 */
	public CubedGriddedRegion(GriddedRegion griddedRegion) {

		this(griddedRegion, DEFAULT_MAX_DEPTH, DEFAULT_NUM_CUBE_DEPTHS, 
				DEFAULT_NUM_CUBES_PER_GRID_EDGE);
	}
	
	
	/**
	 * 
	 * @param griddedRegion
	 * @param maxDepth - maximum depth in km (i.e., the bottom of the deepest cubes)
	 * @param numCubeDepths - the number of cubes to define between the surface (0 km depth) and maxDepth; depth discretization = maxDepth/numCubeDepths
	 * @param numCubesPerGridEdge - the number of cubes along an edge of a grid cell in the supplied griddedRegion; cube width = griddedRegion.getSpacing()/numCubesPerGridEdge
	 */
	public CubedGriddedRegion(GriddedRegion griddedRegion,  double maxDepth, int numCubeDepths, int numCubesPerGridEdge) {
		
		this.maxDepth = maxDepth;
		this.numCubeDepths = numCubeDepths;
		this.numCubesPerGridEdge = numCubesPerGridEdge;
		this.griddedRegion = griddedRegion;
		
		if(!griddedRegion.isSpacingUniform())
			throw new RuntimeException("Lat and Lon discretization must be equal in griddedRegion");
			
		Region exactRegion = getRegionDefinedByExteriorCellEdges(griddedRegion);
		this.griddedRegionModified = new GriddedRegion(exactRegion, griddedRegion.getSpacing(), griddedRegion.getLocation(0)); // can't find method to get anchor from origGriddedRegion
		
		cubeLatLonSpacing = griddedRegionModified.getLatSpacing()/numCubesPerGridEdge;
		cubeDepthDiscr = maxDepth/numCubeDepths;
		
		if(numCubesPerGridEdge % 2 == 0) {	// it's an even number
			gridRegForCubes = new GriddedRegion(griddedRegionModified, cubeLatLonSpacing, new Location(cubeLatLonSpacing/2d,cubeLatLonSpacing/2d));
		} else {	// it's odd
			gridRegForCubes = new GriddedRegion(griddedRegionModified, cubeLatLonSpacing, GriddedRegion.ANCHOR_0_0);
		}

		numCubesPerDepth = gridRegForCubes.getNumLocations();
		numCubes = numCubesPerDepth*numCubeDepths;
		numCubesPerGridCell = numCubeDepths*numCubesPerGridEdge*numCubesPerGridEdge;
		
		if(D) {
			System.out.println("griddedRegionModified.getNumLocations() = "+griddedRegionModified.getNumLocations());
			System.out.println("numCubesPerDepth = "+numCubesPerDepth);
			System.out.println("numCubeDepths = "+numCubeDepths);
			System.out.println("numCubes = "+numCubes);
		}
		
		latForCubeCenter = new double[numCubes];
		lonForCubeCenter = new double[numCubes];
		depthForCubeCenter = new double[numCubes];
		for(int i=0;i<numCubes;i++) {
			int[] regAndDepIndex = getCubeRegAndDepIndicesForIndex(i);
			Location loc = gridRegForCubes.getLocation(regAndDepIndex[0]);
			latForCubeCenter[i] = loc.getLatitude();
			lonForCubeCenter[i] = loc.getLongitude();
			depthForCubeCenter[i] = getCubeDepth(regAndDepIndex[1]);
			
			// test - turn off once done once
//			Location testLoc = this.getLocationForSamplerIndex(i);
//			if(Math.abs(testLoc.getLatitude()-latForPoint[i]) > 0.00001)
//				throw new RuntimeException("Lats diff by more than 0.00001");
//			if(Math.abs(testLoc.getLongitude()-lonForPoint[i]) > 0.00001)
//				throw new RuntimeException("Lons diff by more than 0.00001");
//			if(Math.abs(testLoc.getDepth()-depthForPoint[i]) > 0.00001)
//				throw new RuntimeException("Depths diff by more than 0.00001");
			
		}
		
		// a couple testsl:
		testNumCubesInEachCell();
		testGetCubeIndicesForGridCell();
		
	}
	
	
	
	/**
	 * Region index is first element, and depth index is second
	 * @param index
	 * @return
	 */
	private int[] getCubeRegAndDepIndicesForIndex(int cubeIndex) {
		
		int[] indices = new int[2];
		indices[1] = (int)Math.floor((double)cubeIndex/(double)numCubesPerDepth);	// depth index
		if(indices[1] >= this.numCubeDepths )
			System.out.println("PROBLEM: "+cubeIndex+"\t"+numCubesPerDepth+"\t"+indices[1]+"\t"+numCubeDepths);
		indices[0] = cubeIndex - indices[1]*numCubesPerDepth;						// region index
		return indices;
	}
	
	public Location getCubeLocationForIndex(int cubeIndex) {
		int[] regAndDepIndex = getCubeRegAndDepIndicesForIndex(cubeIndex);
		Location regLoc = gridRegForCubes.getLocation(regAndDepIndex[0]);
		return new Location(regLoc.getLatitude(),regLoc.getLongitude(),getCubeDepth(regAndDepIndex[1]));
	}
	
	public int getRegionIndexForCubeIndex(int cubeIndex) {
		return griddedRegionModified.indexForLocation(getCubeLocationForIndex(cubeIndex));
	}
	
	
	public int getDepthIndexForCubeIndex(int cubeIndex) {
		return getCubeRegAndDepIndicesForIndex(cubeIndex)[1];
	}

	
	/**
	 * this returns -1 if loc is not within the region or depth range
	 * @param loc
	 * @return
	 */
	public int getCubeIndexForLocation(Location loc) {
		int iReg = gridRegForCubes.indexForLocation(loc);
		if(iReg == -1)
			return -1;
		int iDep = getCubeDepthIndex(loc.getDepth());
		return getCubeIndexForRegAndDepIndices(iReg,iDep);
	}

	public int getCubeIndexForRegAndDepIndices(int iReg,int iDep) {
		int index = iDep*numCubesPerDepth+iReg;
		if(index<numCubes && index>=0)
			return index;
		else
			return -1;
	}
	
	public int getCubeDepthIndex(double depth) {
		int index = (int)Math.round((depth-cubeDepthDiscr/2.0)/cubeDepthDiscr);
//		if(index < numRateDepths && index >=0)
			return index;
//		else
//			throw new RuntimeException("Index "+index+" is out of bounds for depth="+depth);
	}
	
	private double getCubeDepth(int depthIndex) {
		return (double)depthIndex*cubeDepthDiscr + cubeDepthDiscr/2;
	}
	
	/**
	 * The creates a region defined by the exterior cell edges of the supplied griddedRegion
	 * @param griddedRegion
	 * @return
	 */
	public static Region getRegionDefinedByExteriorCellEdges(GriddedRegion griddedRegion) {
		
		LocationList exactRegLocList = new LocationList();
		
//		 Note that griddedRegion.getMaxGridLat() does not always return the correct value
		double gridSpacing = griddedRegion.getSpacing();
		double minLat = Double.MAX_VALUE;
		double maxLat = -Double.MAX_VALUE;
		for(int i=0;i<griddedRegion.getNumLocations();i++) {
			Location loc = griddedRegion.getLocation(i);
			if(minLat>loc.getLatitude())
				minLat=loc.getLatitude();
			if(maxLat<loc.getLatitude())
				maxLat=loc.getLatitude();
		}
//		System.out.println(griddedRegion.getMaxGridLat()+"\t"+maxLat);
//		System.out.println(griddedRegion.getMinGridLat()+"\t"+minLat);
//		System.exit(0);

		
		
		int numLats = 1 + (int)Math.round((maxLat-minLat)/gridSpacing);
		double[] minLonForLat = new double[numLats];
		double[] maxLonForLat = new double[numLats];
		for(int i=0;i<minLonForLat.length;i++) {
			minLonForLat[i] = Double.MAX_VALUE;
			maxLonForLat[i] = -Double.MAX_VALUE;
		}
		
		for(int i=0;i<griddedRegion.getNumLocations();i++) {
			Location loc = griddedRegion.getLocation(i);
			int latIndex = (int)Math.round((loc.getLatitude() - minLat)/gridSpacing);
			if(minLonForLat[latIndex]>loc.getLongitude())
				minLonForLat[latIndex]=loc.getLongitude();
			if(maxLonForLat[latIndex]<loc.getLongitude())
				maxLonForLat[latIndex]=loc.getLongitude();
		}
		
		
		// down the left side
		for(int i=minLonForLat.length-1;i>=0;i--) {
			double lat = minLat+gridSpacing*i+gridSpacing/2;
			double lon = minLonForLat[i]-gridSpacing/2;
			exactRegLocList.add(new Location(lat,lon));
			if(i>=1 && Math.abs(minLonForLat[i]-minLonForLat[i-1])>gridSpacing/100) {
				lat = minLat+gridSpacing*i-gridSpacing/2;
				exactRegLocList.add(new Location(lat,lon));
			}
			if(i==0){
				lat = minLat+gridSpacing*i-gridSpacing/2;
				exactRegLocList.add(new Location(lat,lon));				
			}
		}
		
		// across the bottom at 0.2 degree spacing (to avoid arc issues)
		double lonSpacing = 0.2;
		double lonDiff = maxLonForLat[0]-minLonForLat[0];
		if(lonDiff>lonSpacing) {
			for(double lon = minLonForLat[0]+lonSpacing; lon<maxLonForLat[0]; lon+=lonSpacing)
				exactRegLocList.add(new Location(minLat-gridSpacing/2,lon));
		}
		 

		// up the right side
		for(int i=0;i<minLonForLat.length;i++) {
			double lat = minLat+gridSpacing*i-gridSpacing/2;
			double lon = maxLonForLat[i]+gridSpacing/2;
			exactRegLocList.add(new Location(lat,lon));
			if(i<minLonForLat.length-1 && Math.abs(maxLonForLat[i]-maxLonForLat[i+1])>gridSpacing/100) {
				lat = minLat+gridSpacing*i+gridSpacing/2;
				exactRegLocList.add(new Location(lat,lon));
			}
			if(i==minLonForLat.length-1){
				lat = minLat+gridSpacing*i+gridSpacing/2;
				exactRegLocList.add(new Location(lat,lon));				
			}

		}
		
		// Along the top at 0.2 degree increments
		lonDiff = maxLonForLat[minLonForLat.length-1]-minLonForLat[minLonForLat.length-1];
		if(lonDiff>lonSpacing) {
			for(double lon = maxLonForLat[minLonForLat.length-1]-lonSpacing; lon>minLonForLat[minLonForLat.length-1]; lon-=lonSpacing)
				exactRegLocList.add(new Location(maxLat+gridSpacing/2,lon));
		}

		
//		for(Location loc:exactRegLocList) {
//			System.out.println((float)loc.getLatitude()+"\t"+(float)loc.getLongitude());
//		}
		
//		for(int i=0;i<griddedRegion.getNumLocations();i++) {
//			Location loc = griddedRegion.getLocation(i);
//			System.out.println((float)loc.getLatitude()+"\t"+(float)loc.getLongitude());
//		}

		
		return new Region(exactRegLocList, BorderType.MERCATOR_LINEAR);
	}
	
	
	public int[] getCubeIndicesForGridCell(int gridIndex) {
		
		int[] cubeIndexArray = new int[numCubesPerGridCell];
		Location loc = griddedRegionModified.getLocation(gridIndex);
		double gridSpacing = griddedRegionModified.getSpacing();
		int index=0;
		for(double lat = loc.getLatitude()-gridSpacing/2d+cubeLatLonSpacing/2d; lat<loc.getLatitude()+gridSpacing/2d; lat+=cubeLatLonSpacing) {
			for(double lon = loc.getLongitude()-gridSpacing/2d+cubeLatLonSpacing/2d; lon<loc.getLongitude()+gridSpacing/2d; lon+=cubeLatLonSpacing) {
				for(double dep=cubeDepthDiscr/2d; dep<maxDepth; dep+=cubeDepthDiscr) {
					cubeIndexArray[index] = this.getCubeIndexForLocation(new Location(lat,lon,dep));
					index+=1;
				}
			}
		}
		return cubeIndexArray;
	}
	
	/**
	 * This returns the gridded region (not the gridded region for cubes)
	 * @return
	 */
	public GriddedRegion getGriddedRegion() {
		return griddedRegion;
	}

	public double getMaxDepth() {
		return maxDepth;
	}

	
//	double maxDepth;
//	int numCubeDepths;
//	int numCubesPerGridEdge; 
//	double cubeLatLonSpacing;
//	double cubeDepthDiscr;
//	int numCubesPerDepth;
//	int numCubes;
//	double maxFaultNuclDist;
//	int numCubesPerGridCell;

	
	/**
	 * @return - the total number of cubes
	 */
	public int getNumCubes() {
		return numCubes;
	}
	
	
	/**
	 * Deprecated - use getNumCubesPerGridEdge()
	 * @return
	 */
	@Deprecated
	public int getNumPtSrcSubPts() {
		return getNumCubesPerGridEdge();
	}
	
	public int getNumCubesPerGridEdge() {
		return numCubesPerGridEdge;
	}

	/**
	 * Deprecated - use getCubeDepthDiscr()
	 * @return
	 */
	@Deprecated
	public double getDepthDiscr() {
		return getCubeDepthDiscr();
	}
	
	public double getCubeDepthDiscr() {
		return cubeDepthDiscr;
	}

	/**
	 * Deprecated - use getGriddedRegionSpacing()
	 * @return
	 */
	@Deprecated
	public double getPointSrcDiscr() {
		return getGriddedRegionSpacing();
	}
	
	public double getGriddedRegionSpacing() {
		return griddedRegion.getSpacing();
	}

	public double getCubeLatLonSpacing() {
		return cubeLatLonSpacing;
	}

	public GriddedRegion getGridRegForCubes() {
		return gridRegForCubes;
	}


	private void testGetCubeIndicesForGridCell() {
		int[] cubeUsed = new int[numCubes];
		for(int g=0;g<griddedRegionModified.getNumLocations();g++) {
			for(int i: getCubeIndicesForGridCell(g)) {
				cubeUsed[i] += 1;
			}
		}
		
		for(int i:cubeUsed)
			if(i != 1)
				throw new RuntimeException("testGetCubeIndicesForGridCell() failed");
		
		if(D)
			System.out.println("testGetCubeIndicesForGridCell() succeeded");
	}
	

	/**
	 * this makes sure there are the correct number of cells in each cell and that each cube is within a cell
	 * (problems could occur by arcs over long longitude distances between gridded-region perimeter points)
	 */
	private void testNumCubesInEachCell() {
		
		int[] numCubesInGrid = new int[griddedRegionModified.getNumLocations()];
		for(int c=0;c<numCubes;c++) {
			int gridIndex = getRegionIndexForCubeIndex(c);
			if(gridIndex==-1) {
				throw new RuntimeException("Cube is outside region; there is no cell for the cube at "+getCubeLocationForIndex(c));
			}
			numCubesInGrid[gridIndex] += 1;
		}

		int min=Integer.MAX_VALUE, max=0;
		for(int val:numCubesInGrid) {
			if(min>val) min = val;
			if(max<val) max = val;
		}
		if(min!=numCubesPerGridCell || max != numCubesPerGridCell) {
			System.out.println("maxNumCubesInGrid="+max+"\nminNumCubesInGrid="+min);
			throw new RuntimeException("Problem: non-equal number of cubes in each cell\n\n\tmaxNumCubesInGrid="+max+"\n\tminNumCubesInGrid="+min);
		}
		
		if(D)
			System.out.println("It's confrimed that each cell has "+numCubesPerGridCell+" cubes");
	}
	

	
	
	
	public static void main(String[] args) {
		
		CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();

		CubedGriddedRegion cgr = new CubedGriddedRegion(griddedRegion);

	}

}
