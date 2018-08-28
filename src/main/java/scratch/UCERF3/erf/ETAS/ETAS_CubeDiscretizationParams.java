package scratch.UCERF3.erf.ETAS;

import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;

import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;

/**
 * Container for storing all parameters related to cube discretization. Thread safe, and can be shared amung threads to reduce
 * memory and initialization overhead (especially important for the location weight calculator).
 * @author kevin
 *
 */
public class ETAS_CubeDiscretizationParams {
	
	public static boolean D = true;
	
	public static final double DEFAULT_MAX_DEPTH = 24;
	public static final double DEFAULT_DEPTH_DISCR = 2.0;
	public static final int DEFAULT_NUM_PT_SRC_SUB_PTS = 5;		// 5 is good for orig pt-src gridding of 0.1
	
	private final GriddedRegion griddedRegion;
	private final double maxDepth;
	private final int numPtSrcSubPts;
	private final double depthDiscr;
	private final double pointSrcDiscr;
	
	private final double cubeLatLonSpacing;
	private final GriddedRegion gridRegForCubes;
	private final GriddedRegion gridRegForParentLocs;
	
	private ETAS_LocationWeightCalculator locWeightCalc;
	
	public ETAS_CubeDiscretizationParams(GriddedRegion griddedRegion) {
		this(griddedRegion, DEFAULT_MAX_DEPTH, DEFAULT_NUM_PT_SRC_SUB_PTS, DEFAULT_DEPTH_DISCR);
	}
	
	public ETAS_CubeDiscretizationParams(GriddedRegion griddedRegion, double maxDepth, int numPtSrcSubPts, double depthDiscr) {
		super();
		this.griddedRegion = griddedRegion;
		this.maxDepth = maxDepth;
		this.numPtSrcSubPts = numPtSrcSubPts;
		this.depthDiscr = depthDiscr;
		this.pointSrcDiscr = griddedRegion.getSpacing();
		this.cubeLatLonSpacing = pointSrcDiscr/numPtSrcSubPts;	// TODO pointSrcDiscr from griddedRegion?
		
		Region regionForRates = new Region(griddedRegion.getBorder(), BorderType.MERCATOR_LINEAR);
		// need to set the region anchors so that the gridRegForRatesInSpace sub-regions fall completely inside the gridded seis regions
		// this assumes the point sources have an anchor of GriddedRegion.ANCHOR_0_0)
		if(numPtSrcSubPts % 2 == 0) {	// it's an even number
			gridRegForCubes = new GriddedRegion(regionForRates, cubeLatLonSpacing, new Location(cubeLatLonSpacing/2d,cubeLatLonSpacing/2d));
			// parent locs are mid way between cubes:
			gridRegForParentLocs = new GriddedRegion(regionForRates, cubeLatLonSpacing, GriddedRegion.ANCHOR_0_0);			
		} else {	// it's odd
			gridRegForCubes = new GriddedRegion(regionForRates, cubeLatLonSpacing, GriddedRegion.ANCHOR_0_0);
			// parent locs are mid way between cubes:
			gridRegForParentLocs = new GriddedRegion(regionForRates, cubeLatLonSpacing, new Location(cubeLatLonSpacing/2d,cubeLatLonSpacing/2d));			
		}
	}

	public GriddedRegion getGriddedRegion() {
		return griddedRegion;
	}

	public double getMaxDepth() {
		return maxDepth;
	}

	public int getNumPtSrcSubPts() {
		return numPtSrcSubPts;
	}

	public double getDepthDiscr() {
		return depthDiscr;
	}

	public double getPointSrcDiscr() {
		return pointSrcDiscr;
	}

	public double getCubeLatLonSpacing() {
		return cubeLatLonSpacing;
	}

	public GriddedRegion getGridRegForCubes() {
		return gridRegForCubes;
	}

	public GriddedRegion getGridRegForParentLocs() {
		return gridRegForParentLocs;
	}
	
	public synchronized ETAS_LocationWeightCalculator getLocationWeightCalc(ETAS_ParameterList etasParams) {
		if (locWeightCalc == null ) {
			if(D) System.out.println("Creating ETAS_LocationWeightCalculator");
			long startTime= System.currentTimeMillis();
			double maxDistKm=1000;
			GriddedRegion gridRegForCubes = getGridRegForCubes();
			double midLat = (gridRegForCubes.getMaxLat() + gridRegForCubes.getMinLat())/2.0;
			double etasDistDecay = etasParams.get_q();
			double etasMinDist = etasParams.get_d();
			locWeightCalc = new ETAS_LocationWeightCalculator(
					maxDistKm, getMaxDepth(), getCubeLatLonSpacing(), getDepthDiscr(), midLat, etasDistDecay, etasMinDist);
			if(D) ETAS_SimAnalysisTools.writeMemoryUse("Memory after making etas_LocWeightCalc");
			double runtime = ((double)(System.currentTimeMillis()-startTime))/1000;
			if(D) System.out.println("Done creating ETAS_LocationWeightCalculator; it took (sec): "+runtime);
		}
		return locWeightCalc;
	}

}
