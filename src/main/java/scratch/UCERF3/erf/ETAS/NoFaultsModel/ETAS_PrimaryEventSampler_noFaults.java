package scratch.UCERF3.erf.ETAS.NoFaultsModel;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.mapping.gmt.gui.GMT_MapGuiBean;
import org.opensha.commons.mapping.gmt.gui.ImageViewerWindow;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.impl.CPTParameter;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.AbstractNthRupERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultBasedMapGen;
import scratch.UCERF3.analysis.FaultSysSolutionERF_Calc;
import scratch.UCERF3.analysis.GMT_CA_Maps;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_LocationWeightCalculator;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.IntegerPDF_FunctionSampler;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.RELM_RegionUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * This class divides the supplied gridded region (and specified depth extent) into cubes, and computes
 * long-term rates of events for each cube, as well as the sources (and their fractions) that nucleate in
 * each cube.  The cube locations are defined by their centers.  For sampling aftershocks, parent ruptures
 * are assumed to be located at the intersecting corners of these cubes; thus, the number of parent locations
 * with depth is one greater than the number of cubes.
 * 
 * Important Notes:
 * 
 * See the documentation of testGriddedSeisRatesInCubes() for an explanation of why gridded seismicity rates are
 * not perfectly preserved by the cube representation (due to using cube centers, rather than areas, as a means 
 * for identifying those within a fault section polygon).
 * 
 * Because some cubes have multiple faults within, the GR correction (based on supra rates, not num aftershocks) 
 * for the total cube MFD will not necessarily equal that for any of the faults within.
 * 
 * 
 * TODO:
 * 
 * make sourceRates[] for only fault-based sources (not used otherwise?)
 * 
 * @author field
 *
 */
public class ETAS_PrimaryEventSampler_noFaults {
	
	boolean APPLY_ERT_GRIDDED=true;	// this tells whether to apply elastic-rebound triggereing (ERT) for gridded seismicity
	
	final static boolean D=ETAS_Simulator.D;
		
	// these define the cubes in space
	int numCubeDepths, numCubesPerDepth, numCubes, numParDepths, numParLocsPerDepth, numParLocs;
	double maxDepth, depthDiscr;
	GriddedRegion origGriddedRegion;
	GriddedRegion gridRegForCubes; // the center of each cube in lat/lon space
	GriddedRegion gridRegForParentLocs;
	double cubeLatLonSpacing;
	
	AbstractNthRupERF erf;
	int totNumSrc;
	
	int numPtSrcSubPts;
	double pointSrcDiscr;
	
	// this is for each cube
	double[] latForCubeCenter, lonForCubeCenter, depthForCubeCenter;
	
	// this will hold the rate of each ERF source (which can be modified by external objects)
	double sourceRates[];
	SummedMagFreqDist longTermTotalERF_MFD;

	
	double totRate;
	

//	 0 the entire grid cell is truly off fault (outside fault polygons)
//	 1 the entire grid cell is subseismogenic (within fault polygons)
//	 2 a mix of the two types
	int[] origGridSeisTrulyOffVsSubSeisStatus;
	
	IntegerPDF_FunctionSampler cubeSamplerGriddedRatesOnly; 
	
	Map<Integer,ArrayList<ETAS_EqkRupture>> eventListForParLocIndexMap;  // key is the parLocIndex and value is a list of ruptures to process

	// ETAS distance decay params
	double etasDistDecay, etasMinDist;
	boolean includeERF_Rates=false;
	U3ETAS_ProbabilityModelOptions probModel;
	
	boolean includeSpatialDecay;
	
	ETAS_LocationWeightCalculator etas_LocWeightCalc;
	
	SummedMagFreqDist[] mfdForSrcArray;
	
	
	ETAS_Utils etas_utils;
	ETAS_ParameterList etasParams;
	
	public static final double DEFAULT_MAX_DEPTH = 24;
	public static final double DEFAULT_DEPTH_DISCR = 2.0;
	public static final int DEFAULT_NUM_PT_SRC_SUB_PTS = 5;		// 5 is good for orig pt-src gridding of 0.1
	
	/**
	 * 
	 * @param griddedRegion
	 * @param erf
	 * @param sourceRates
	 * @param pointSrcDiscr
	 * @param outputFileNameWithPath
	 * @param includeERF_Rates
	 * @param etas_utils
	 * @param etasDistDecay_q
	 * @param etasMinDist_d
	 * @param applyGR_Corr
	 * @param probMode
	 */
	public ETAS_PrimaryEventSampler_noFaults(GriddedRegion griddedRegion, AbstractNthRupERF erf, double sourceRates[],
			double pointSrcDiscr, String outputFileNameWithPath, ETAS_ParameterList etasParams, ETAS_Utils etas_utils) {

		this(griddedRegion, DEFAULT_NUM_PT_SRC_SUB_PTS, erf, sourceRates, DEFAULT_MAX_DEPTH, DEFAULT_DEPTH_DISCR, 
				pointSrcDiscr, outputFileNameWithPath, etasParams, true, etas_utils);
	}

	
	/**
	 * TODO
	 * 
	 * 		resolve potential ambiguities between attributes of griddedRegion and pointSrcDiscr
	 * 
	 * 		make this take an ETAS_ParamList to simplify the constructor
	 * 
	 * @param griddedRegion
	 * @param numPtSrcSubPts - the
	 * @param erf
	 * @param sourceRates - pointer to an array of source rates (which may get updated externally)
	 * @param maxDepth
	 * @param depthDiscr
	 * @param pointSrcDiscr - the grid spacing of gridded seismicity in the ERF
	 * @param outputFileNameWithPath - TODO not used
	 * @param distDecay - ETAS distance decay parameter
	 * @param minDist - ETAS minimum distance parameter
	 * @param includeERF_Rates - tells whether to consider long-term rates in sampling aftershocks
	 * @param includeSpatialDecay - tells whether to include spatial decay in sampling aftershocks (for testing)
	 * @param etas_utils - this is for obtaining reproducible random numbers (seed set in this object)
	 * @param applyGR_Corr - whether or not to apply the GR correction
	 * @param probMode
	 */
	public ETAS_PrimaryEventSampler_noFaults(GriddedRegion griddedRegion, int numPtSrcSubPts, AbstractNthRupERF erf, double sourceRates[],
			double maxDepth, double depthDiscr, double pointSrcDiscr, String outputFileNameWithPath, ETAS_ParameterList etasParams, 
			boolean includeSpatialDecay, ETAS_Utils etas_utils) {
		
		this.etasParams = etasParams;

		this.etasDistDecay=etasParams.get_q();
		this.etasMinDist=etasParams.get_d();
		this.probModel = etasParams.getU3ETAS_ProbModel();
		
		if(probModel == U3ETAS_ProbabilityModelOptions.FULL_TD) {
			APPLY_ERT_GRIDDED = true;
		}
		else { // NO_ERT or POISSON
			APPLY_ERT_GRIDDED = false;
		}
		
		this.includeSpatialDecay=includeSpatialDecay;
			
		origGriddedRegion = griddedRegion;
		cubeLatLonSpacing = pointSrcDiscr/numPtSrcSubPts;	// TODO pointSrcDiscr from griddedRegion?
		if(D) System.out.println("Gridded Region has "+griddedRegion.getNumLocations()+" cells");
		
		this.numPtSrcSubPts = numPtSrcSubPts;
		this.erf = erf;
		
		this.maxDepth=maxDepth;	// the bottom of the deepest cube
		this.depthDiscr=depthDiscr;
		this.pointSrcDiscr = pointSrcDiscr;
		numCubeDepths = (int)Math.round(maxDepth/depthDiscr);
		
		this.etas_utils = etas_utils;
				
		Region regionForRates = new Region(griddedRegion.getBorder(),BorderType.MERCATOR_LINEAR);
		
		// need to set the region anchors so that the gridRegForRatesInSpace sub-regions fall completely inside the gridded seis regions
		// this assumes the point sources have an anchor of GriddedRegion.ANCHOR_0_0)
		if(numPtSrcSubPts % 2 == 0) {	// it's an even number
			gridRegForCubes = new GriddedRegion(regionForRates, cubeLatLonSpacing, new Location(cubeLatLonSpacing/2d,cubeLatLonSpacing/2d));
			// parent locs are mid way between cubes:
			gridRegForParentLocs = new GriddedRegion(regionForRates, cubeLatLonSpacing, GriddedRegion.ANCHOR_0_0);			
		}
		else {	// it's odd
			gridRegForCubes = new GriddedRegion(regionForRates, cubeLatLonSpacing, GriddedRegion.ANCHOR_0_0);
			// parent locs are mid way between cubes:
			gridRegForParentLocs = new GriddedRegion(regionForRates, cubeLatLonSpacing, new Location(cubeLatLonSpacing/2d,cubeLatLonSpacing/2d));			
		}
		
		numCubesPerDepth = gridRegForCubes.getNumLocations();
		numCubes = numCubesPerDepth*numCubeDepths;
		
		numParDepths = numCubeDepths+1;
		numParLocsPerDepth = gridRegForParentLocs.getNumLocations();
		numParLocs = numParLocsPerDepth*numParDepths;
		
		if(D) {
			System.out.println("numParLocsPerDepth="+numParLocsPerDepth);
			System.out.println("numCubesPerDepth="+numCubesPerDepth);
		}
		
		// this is used by the custom cache for smart evictions
		eventListForParLocIndexMap = Maps.newHashMap();
		
		totNumSrc = erf.getNumSources();
		if(totNumSrc != sourceRates.length)
			throw new RuntimeException("Problem with number of sources");
		
		if(D) System.out.println("totNumSrc="+totNumSrc+"\tnumPointsForRates="+numCubes);
				
		this.sourceRates = sourceRates;	// pointer to current source rates
		
		System.gc();	// garbage collect
		
		if(D)  ETAS_SimAnalysisTools.writeMemoryUse("Memory before making data");
		
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
		
		
		// TODO Not necessary needed, and will trigger recompute int declareRateChange?
		computeMFD_ForSrcArrays(2.05, 8.95, 70);
		
		long startTime= System.currentTimeMillis();
		if(D) System.out.println("Starting getCubeSamplerWithERF_RatesOnly()");
		getCubeSamplerWithERF_GriddedRatesOnly();
		double runtime = ((double)(System.currentTimeMillis()-startTime))/1000;
		if (D) System.out.println("getCubeSamplerWithERF_RatesOnly() took (sec): "+runtime);
				
		if(D) System.out.println("Creating ETAS_LocationWeightCalculator");
		startTime= System.currentTimeMillis();
		double maxDistKm=1000;
		double midLat = (gridRegForCubes.getMaxLat() + gridRegForCubes.getMinLat())/2.0;
		etas_LocWeightCalc = new ETAS_LocationWeightCalculator(maxDistKm, maxDepth, cubeLatLonSpacing, depthDiscr, midLat, etasDistDecay, etasMinDist, etas_utils);
		if(D) ETAS_SimAnalysisTools.writeMemoryUse("Memory after making etas_LocWeightCalc");
		runtime = ((double)(System.currentTimeMillis()-startTime))/1000;
		if(D) System.out.println("Done creating ETAS_LocationWeightCalculator; it took (sec): "+runtime);
		
	}
	
	
	
	
	
	public void addRuptureToProcess(ETAS_EqkRupture rup) {
		int parLocIndex = getParLocIndexForLocation(rup.getParentTriggerLoc());
		if(parLocIndex !=-1) {
			if(eventListForParLocIndexMap.keySet().contains(parLocIndex)) {
				eventListForParLocIndexMap.get(parLocIndex).add(rup);
			}
			else {
				ArrayList<ETAS_EqkRupture> list = new ArrayList<ETAS_EqkRupture>();
				list.add(rup);
				eventListForParLocIndexMap.put(parLocIndex, list);
			}
		}
	}
	
	
	
	

	
	
	
	

	
	
	
	
	

	
	/**
	 * This loops over all points on the rupture surface and creates a net (average) point sampler.
	 * @param mainshock
	 * @return
	 */
	public IntegerPDF_FunctionSampler old_getAveSamplerForRupture(EqkRupture mainshock) {
		long st = System.currentTimeMillis();
		IntegerPDF_FunctionSampler aveSampler = new IntegerPDF_FunctionSampler(numCubes);
		
		LocationList locList = mainshock.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
		for(Location loc: locList) {
			
			// set the sampler
			int parLocIndex = getParLocIndexForLocation(loc);
			IntegerPDF_FunctionSampler sampler = getCubeSampler(parLocIndex);
			
			for(int i=0;i <numCubes;i++) {
				aveSampler.add(i, sampler.getY(i));
			}
		}
		if(D) {
			double sec = ((double)(System.currentTimeMillis()-st))/1000d;
			System.out.println("getAveSamplerForRupture() took (sec): "+(float)sec);
		}
		return aveSampler;
	}
	
	
	/**
	 * 
	 * TODO Change this to just keep cubes within radius; also remove hard coded mag threshold
	 * @param hypoLoc
	 * @param mag
	 * @return
	 */
	public LocationList getParentTriggerCubeLocationsForLargePointSource(Location hypoLoc, double mag) {
		LocationList locList = new LocationList();
		
		if(mag<=4.0) {
			locList.add(hypoLoc);
		}

		else {
			double radius = ETAS_Utils.getRuptureRadiusFromMag(mag);
// System.out.println("Test Locations:");
			for(int c=0;c<numParLocs;c++) {
				Location parLoc = getParLocationForIndex(c);
				if(LocationUtils.linearDistanceFast(hypoLoc, parLoc) <= radius) {
// System.out.println(parLoc.getLongitude()+"\t"+parLoc.getLatitude()+"\t"+parLoc.getDepth());
					locList.add(parLoc);
				}
			}
		}
		return locList;
	}
	
	
	/**
	 * This loops over all points on the rupture surface and creates a net (average) point sampler.
	 * Note that this moves each point on the rupture surface horizontally to four different equally-
	 * weighted points at +/- 0.025 degrees in lat and lon.  This is to stabilize the probability of
	 * triggering the sections that extend off the ends off the fault rupture, where the relative 
	 * likelihood of triggering one versus the other can change by 70% because of discretization issues 
	 * (e.g., sliding the rupture down a tiny bit can change one or both end points to jump between haveing 
	 * 4 versus 8 adjacent cubes with the unruptured extension in it).
	 * @param mainshock
	 * @return
	 */
	public IntegerPDF_FunctionSampler getAveSamplerForRupture(ETAS_EqkRupture mainshock) {
		long st = System.currentTimeMillis();
		IntegerPDF_FunctionSampler aveSampler = new IntegerPDF_FunctionSampler(numCubes);
		
		LocationList locList;
		ArrayList<Location> locList2=null;
		
		if(mainshock.getRuptureSurface().isPointSurface()) {
			locList = getParentTriggerCubeLocationsForLargePointSource(mainshock.getHypocenterLocation(), mainshock.getMag());
			if(locList.size()==1) {	// point source
				// System.out.println("HERE: POINT SOURCE");
				return getCubeSampler(getParLocIndexForLocation(locList.get(0)));
			}
			locList2=locList;
		}
		else	{
			if(mainshock.getFSSIndex() != -1) {
//				RuptureSurface surf = etas_utils.getRuptureSurfaceWithNoCreepReduction(mainshock.getFSSIndex(), fssERF, 1.0);
				RuptureSurface surf = mainshock.getRuptureSurface();
				locList = surf.getEvenlyDiscritizedListOfLocsOnSurface();											
			}
			else {
				locList = mainshock.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
			}
			// add fuzziness
			locList2 = new ArrayList<Location>();
			for(Location loc: locList) {
				locList2.add(new Location(loc.getLatitude()+0.005,loc.getLongitude()+0.005,loc.getDepth()));
				locList2.add(new Location(loc.getLatitude()+0.005,loc.getLongitude()-0.005,loc.getDepth()));
				locList2.add(new Location(loc.getLatitude()-0.005,loc.getLongitude()+0.005,loc.getDepth()));
				locList2.add(new Location(loc.getLatitude()-0.005,loc.getLongitude()-0.005,loc.getDepth()));
			}
		}
		
		// make the number of locations at each parent location index
		HashMap<Integer,Double> parLocMap = new HashMap<Integer,Double>();
		for(Location loc:locList2) {
			int parLocIndex = getParLocIndexForLocation(loc);
			if(parLocMap.containsKey(parLocIndex)) {
				double newVal = 1 + parLocMap.get(parLocIndex);
				parLocMap.put(parLocIndex, newVal);
			}
			else {
				parLocMap.put(parLocIndex, 1.0);
			}
		}
		
		// now make the sampler
		CalcProgressBar progressBar=null;
		if(D) {
			progressBar = new CalcProgressBar("getAveSamplerForRupture(*)", "junk");
			progressBar.showProgress(true);
		}
			
		int progress =0;
		for(int parLocIndex: parLocMap.keySet()) {
			
			if(D) {
				progressBar.updateProgress(progress, parLocMap.keySet().size());
				progress += 1;
			}
			if(parLocIndex == -1)
				continue;
			IntegerPDF_FunctionSampler sampler = getCubeSampler(parLocIndex);
			for(int i=0;i <numCubes;i++) {
				aveSampler.add(i, sampler.getY(i)*parLocMap.get(parLocIndex));
			}
			
		}
		if(D) {
			progressBar.showProgress(false);
			double sec = ((double)(System.currentTimeMillis()-st))/1000d;
			System.out.println("getAveSamplerForRupture() took (sec): "+(float)sec);
		}
		return aveSampler;
	}

	
	
	
	/**
	 *  This returns the relative probability of triggering each source that exists within the cube.
	 *  null is returned if no sources exist in cube.
	 * @param cubeIndex
	 * @return Hashtable where key is src index and value is relative probability
	 */
	public Hashtable<Integer,Double> getRelativeTriggerProbOfSourcesInCube(int cubeIndex, double fracSupra) {
		Hashtable<Integer,Double> probForSrcHashtable = getNucleationRatesOfSourcesInCube(cubeIndex, fracSupra);
		
		if(probForSrcHashtable == null) {
			return null;
		}
		else {
			//Normalize rates to relative probabilities
			double sum=0;
			for(int srcIndex:probForSrcHashtable.keySet())
				sum += probForSrcHashtable.get(srcIndex);
			for(int srcIndex:probForSrcHashtable.keySet()) {
				double normVal = probForSrcHashtable.get(srcIndex)/sum;
				probForSrcHashtable.put(srcIndex, normVal);
			}
			
			if(D) {	// test that sum equals 1.0
				double testVal = 0;
				for(int srcIndex:probForSrcHashtable.keySet())
					testVal += probForSrcHashtable.get(srcIndex);
				if(testVal<0.9999 || testVal>1.0001)
					throw new RuntimeException("PROBLEM");				
			}

			return probForSrcHashtable;
		}
	}
	
	
	
	
	
	/**
	 *  This returns the nucleation rate of each source that exists within the cube.
	 *  null is returned if no sources exist in cube.
	 * @param cubeIndex
	 * @return Hashtable where key is src index and value is rate
	 */
	public Hashtable<Integer,Double> getNucleationRatesOfSourcesInCube(int cubeIndex, double fracSupra) {
		Hashtable<Integer,Double> rateForSrcHashtable = new Hashtable<Integer,Double>();
		
		// compute nucleation rate of gridded-seis source in this cube
		int gridSrcIndex = -1;
		double gridSrcRate=0;
		int griddeSeisRegionIndex = origGriddedRegion.indexForLocation(getCubeLocationForIndex(cubeIndex));
		if(griddeSeisRegionIndex != -1)	{
			gridSrcIndex = griddeSeisRegionIndex;
			gridSrcRate = cubeSamplerGriddedRatesOnly.getY(cubeIndex);	
			rateForSrcHashtable.put(gridSrcIndex, gridSrcRate);	// only gridded source in this cube
			return rateForSrcHashtable;
		}
		else {
			return null;
		}
	}
	
	
	
	
	/**
	 * For the given sampler, this gives the relative trigger probability of each source in the ERF.
	 * @param sampler
	 * @param frac - only cubes that contribute to this fraction of the total rate will be considered (much faster and looks like same result)
	 */
	public double[] getRelativeTriggerProbOfEachSource(IntegerPDF_FunctionSampler sampler, double frac,
			ETAS_EqkRupture rupture) {
		long st = System.currentTimeMillis();
		double[] trigProb = new double[erf.getNumSources()];
		
//		IntegerPDF_FunctionSampler aveSampler = getAveSamplerForRupture(mainshock);

		// normalize so values sum to 1.0
		sampler.scale(1.0/sampler.getSumOfY_vals());
		
		CalcProgressBar progressBar = null;
		if(D) {
			progressBar = new CalcProgressBar("getRelativeTriggerProbOfEachSource", "junk");
			progressBar.showProgress(true);
		}
		
		List<Integer> list = sampler.getOrderedIndicesOfHighestXFract(frac);
		double fracSupra = 1.0;
		
		// now loop over cubes
		int numDone=0;
		for(int i : list) {
			if(D) 
				progressBar.updateProgress(numDone, list.size());
			if(APPLY_ERT_GRIDDED)
				fracSupra = getERT_MinFracSupra(rupture, getCubeLocationForIndex(i));
			
			// TEST for just ERT effect with no time dep probabilities
//			fracSupra=1.0;
//			List<FaultSectionPrefData> fltDataList = rupSet.getFaultSectionDataForRupture(rupture.getFSSIndex());
//			int[] sectInCubeArray = sectInCubeList.get(i);
//			for(FaultSectionPrefData fltData : fltDataList) {
//				for(int sectID:sectInCubeArray)
//					if(sectID == fltData.getSectionId()) {
//						fracSupra=0.0;
//						break;
//					}
//			}
			
			Hashtable<Integer,Double>  relSrcProbForCube = getRelativeTriggerProbOfSourcesInCube(i,fracSupra);
			if(relSrcProbForCube != null) {
				for(int srcKey:relSrcProbForCube.keySet()) {
					trigProb[srcKey] += sampler.getY(i)*relSrcProbForCube.get(srcKey);
				}
			}
//			else {
//				// I confirmed that all of these are around the edges
//				Location loc = getCubeLocationForIndex(i);
//				System.out.println("relSrcProbForCube is null for cube index "+i+"\t"+loc.getLongitude()+"\t"+loc.getLatitude()+"\t"+loc.getDepth());
//			}
			numDone+=1;
		}
		
		if(D)
			progressBar.showProgress(false);

		double testSum=0;
		for(int s=0; s<trigProb.length; s++)
			testSum += trigProb[s];
		if(testSum<0.9999 || testSum>1.0001)
			System.out.println("testSum="+testSum);
//			throw new RuntimeException("PROBLEM");
		
		if(D) {
			st = System.currentTimeMillis()-st;
			System.out.println("getRelativeTriggerProbOfEachSource took:"+((float)st/1000f)+" sec");			
		}
		
		return trigProb;
	}
	
	
	/**
	 * This gives the MFD PDF given a primary event.  
	 * This takes relSrcProbs rather than a rupture in order to avoid repeating that calculation
	 * @param relSrcProbs
	 * @return ArrayList<SummedMagFreqDist>; index 0 has total MFD
	 */
	public SummedMagFreqDist getExpectedPrimaryMFD_PDF(double[] relSrcProbs) {
		
		long st = System.currentTimeMillis();

//		double[] relSrcProbs = getRelativeTriggerProbOfEachSource(sampler);
		SummedMagFreqDist magDist = new SummedMagFreqDist(2.05, 8.95, 70);
		IncrementalMagFreqDist srcMFD;
		
		double testTotProb = 0;
		for(int s=0; s<relSrcProbs.length;s++) {
			if(mfdForSrcArray == null)
				srcMFD = ERF_Calculator.getTotalMFD_ForSource(erf.getSource(s), 1.0, 2.05, 8.95, 70, true);
			else
				srcMFD = mfdForSrcArray[s].deepClone();
			srcMFD.normalizeByTotalRate();	// change to PDF
			srcMFD.scale(relSrcProbs[s]);
			double totMFD_Prob = srcMFD.getTotalIncrRate();
			if(!Double.isNaN(totMFD_Prob)) {// not sure why this is needed
				testTotProb += totMFD_Prob;
				magDist.addIncrementalMagFreqDist(srcMFD);
			}
		}

		if(D) {
			System.out.println("\ttestTotProb="+testTotProb);
			st = System.currentTimeMillis()-st;
			System.out.println("getExpectedPrimaryMFD_PDF took:"+((float)st/1000f)+" sec");			
		}
		
		return magDist;
	}
	
	
	
	/**
	 * This returns the minimum fraction supra among all ancestors
	 * @param parentRup
	 * @param cubeLoc
	 * @return
	 */
	private double getERT_MinFracSupra(ETAS_EqkRupture parentRup, Location cubeLoc) {
		double minFrac = getERT_FracSupra(parentRup, cubeLoc);
		ETAS_EqkRupture previousParent = parentRup;
		while(previousParent.getParentRup() !=null) {
			ETAS_EqkRupture nextParent = previousParent.getParentRup();
			double newFrac = getERT_FracSupra(nextParent, cubeLoc);
			if(newFrac<minFrac) {
				minFrac=newFrac;
			}
			previousParent = nextParent;
		}
		return minFrac;
	}


	
	/**
	 * This tells the degree to which a parent rupture can trigger supra-seismogenic events at the given location.
	 * The answer is 1.0 if the parent is itself supras-eismogenic (this case is handled using elastic
	 * rebound) or if APPLY_ERT_GRIDDED is false (POISSON or NO_ERT case).
	 * It is also 1.0 if the parent mag is less than 4.0.  Otherwise the answer depends on the approximate
	 * fraction of the cube that is within the source radius computed by ETAS_Utils.getRuptureRadiusFromMag(parMag)
	 * (zero if completely inside and 1 if completely outside)
	 * TODO remove hard coded mag threshold and halfCubeWidth
	 * @param parentRup
	 * @param cubeLoc
	 * @return
	 */
	private double getERT_FracSupra(ETAS_EqkRupture parentRup, Location cubeLoc) {
		if(parentRup.getFSSIndex()>=0 || !APPLY_ERT_GRIDDED)
			return 1.0;
		double halfCubeWidth = 1.24;	// TODO remove hard coding
		double frac;
		boolean pointSurface = parentRup.getRuptureSurface() instanceof PointSurface;
		if(!pointSurface) {
			if (D) System.out.println("*****\nWarning: finite rupture not a FSS rupture, so no ERT applied; need to fix this at some point\n*******");
			return 1;
//			throw new RuntimeException("non PointSurface case not yet supported");	// otherwise we need to know the point from which triggering occurrs?
		}
		double parMag = parentRup.getMag();
		if(parMag<4.0)
			return 1.0;
		double srcRadius = ETAS_Utils.getRuptureRadiusFromMag(parMag);
		Location parLoc = ((PointSurface)parentRup.getRuptureSurface()).getLocation();
		double dist = LocationUtils.linearDistanceFast(parLoc, cubeLoc); // hypocenter location; not parent trigger location
		if(dist<=srcRadius-halfCubeWidth) {
			frac=0.0;
//			System.out.println("HERE getGriddedERT_Fraction="+frac+" for "+triggeredLoc+"\tfor M="+parentRup.getMag()+"\tdist="+dist+"\tsrcRadius="+srcRadius);
		}
		else if(dist>srcRadius+halfCubeWidth) {
			frac = 1.0;
		}
		else {
			frac = (dist-srcRadius+halfCubeWidth)/(2*halfCubeWidth);
//			System.out.println("HERE getGriddedERT_Fraction="+frac+" for "+triggeredLoc+"\tfor M="+parentRup.getMag()+"\tdist="+dist+"\tsrcRadius="+srcRadius);
		}
		return frac;
	}

	

	/**
	 * This method will populate the given rupToFillIn with attributes of a randomly chosen
	 * primary aftershock for the given main shock.  
	 * @return boolean tells whether it succeeded in setting the rupture
	 * @param rupToFillIn
	 */
	public boolean setRandomPrimaryEvent(ETAS_EqkRupture rupToFillIn) {
		
		ETAS_EqkRupture parRup = rupToFillIn.getParentRup();
		
		// get the location on the parent that does the triggering
		Location actualParentLoc=rupToFillIn.getParentTriggerLoc();
			
		// Check for problem region index
		int parRegIndex = gridRegForParentLocs.indexForLocation(actualParentLoc);
		if(parRegIndex <0) {
			if(D) {
				System.out.print("Warning: parent location outside of region; parRegIndex="+parRegIndex+"; parentLoc="+actualParentLoc.toString()+
						"; Num pts on main shock surface: "+parRup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface().size());
				if(parRup instanceof ETAS_EqkRupture) {
					System.out.println("; Problem event generation: "+((ETAS_EqkRupture)parRup).getGeneration());
				}
				else {
					System.out.println(" ");
				}
			}
			return false;
		}
		
////System.out.println("actualParentLoc: "+actualParentLoc);
////System.out.println("parDepIndex: "+getParDepthIndex(actualParentLoc.getDepth()));
////System.out.println("getParDepth(parDepIndex): "+getParDepth(parDepIndex));
////System.out.println("translatedParLoc: "+translatedParLoc);
////System.exit(0);
		
		int parLocIndex = getParLocIndexForLocation(actualParentLoc);
		Location translatedParLoc = getParLocationForIndex(parLocIndex);
		
		// get the cube index for where the event was triggered
		int aftShCubeIndex = -1;
		if(includeERF_Rates) {
			aftShCubeIndex = rupToFillIn.getCubeIndex();
			if(aftShCubeIndex == -1) {	
				IntegerPDF_FunctionSampler sampler = getCubeSampler(parLocIndex);
				// fill in the cube locations for all events with this parent location for efficiency
				for(ETAS_EqkRupture tempRup: eventListForParLocIndexMap.get(parLocIndex)) {
					tempRup.setCubeIndex(sampler.getRandomInt(etas_utils.getRandomDouble()));
				}
				eventListForParLocIndexMap.remove(parLocIndex);
				aftShCubeIndex = rupToFillIn.getCubeIndex();
				if(aftShCubeIndex == -1) {
					throw new RuntimeException("Problem Here");
				}
			}
		}
		else { // Only distance dacay
			Location relativeLoc = etas_LocWeightCalc.getRandomLoc(translatedParLoc.getDepth());
			double latSign = 1;
			double lonSign = 1;
			if(etas_utils.getRandomDouble()<0.5) {
				latSign = -1;
			}
			if(etas_utils.getRandomDouble()<0.5) {
				lonSign = -1;
			}
			Location cubeLoc = new Location(relativeLoc.getLatitude()*latSign+translatedParLoc.getLatitude(),
					relativeLoc.getLongitude()*lonSign+translatedParLoc.getLongitude(), relativeLoc.getDepth());
			aftShCubeIndex = getCubeIndexForLocation(cubeLoc);
			if(aftShCubeIndex == -1)
				return false;	// triggered location outside of the region
			int griddeSeisRegionIndex = origGriddedRegion.indexForLocation(getCubeLocationForIndex(aftShCubeIndex));
			if(griddeSeisRegionIndex == -1)	// check whether the cube has any gridded seismicity
				return false;
		}

		double fractionSupra = 0;
		int randSrcIndex = getRandomSourceIndexInCube(aftShCubeIndex, fractionSupra);
		
		int gridRegionIndex = randSrcIndex;
		ProbEqkSource src = erf.getSource(randSrcIndex);
			
		int r=0;
		if(src.getNumRuptures() > 1) {
			r = src.drawSingleRandomEqkRuptureIndex(etas_utils.getRandomDouble());
		}
		int nthRup = erf.getIndexN_ForSrcAndRupIndices(randSrcIndex,r);
		ProbEqkRupture erf_rup = src.getRupture(r);

		double relLat = latForCubeCenter[aftShCubeIndex]-translatedParLoc.getLatitude();
		double relLon = lonForCubeCenter[aftShCubeIndex]-translatedParLoc.getLongitude();
		double relDep = depthForCubeCenter[aftShCubeIndex]-translatedParLoc.getDepth();	// TODO why not used (remove or bug?)
			
		rupToFillIn.setGridNodeIndex(randSrcIndex);
						
		Location deltaLoc = etas_LocWeightCalc.getRandomDeltaLoc(Math.abs(relLat), Math.abs(relLon), 
					depthForCubeCenter[aftShCubeIndex],translatedParLoc.getDepth());
			
		double newLat, newLon, newDep;
		if(relLat<0.0)	// neg value
				newLat = latForCubeCenter[aftShCubeIndex]-deltaLoc.getLatitude();
		else 
				newLat = latForCubeCenter[aftShCubeIndex]+deltaLoc.getLatitude();
		if(relLon<0.0)	// neg value
				newLon = lonForCubeCenter[aftShCubeIndex]-deltaLoc.getLongitude();
		else 
				newLon = lonForCubeCenter[aftShCubeIndex]+deltaLoc.getLongitude();

		newDep = depthForCubeCenter[aftShCubeIndex]+deltaLoc.getDepth();

		Location randLoc = new Location(newLat,newLon,newDep);
			
		// get a location vector pointing from the translated parent location to the actual parent location nearest point here to the srcLoc
		LocationVector corrVector = LocationUtils.vector(translatedParLoc, actualParentLoc);
		Location hypLoc = LocationUtils.location(randLoc, corrVector);
			
		// check whether depth is above or below max depth
		if(hypLoc.getDepth()<=0.0) {
			Location tempLoc = new Location(hypLoc.getLatitude(),hypLoc.getLongitude(),0.0001);
			hypLoc = tempLoc;
		}
		if(hypLoc.getDepth()>maxDepth) {
			Location tempLoc = new Location(hypLoc.getLatitude(),hypLoc.getLongitude(),maxDepth-0.0001);
			hypLoc = tempLoc;
		}


			
//			// Issue: that last step could have moved the hypocenter outside the grid node of the source (by up to ~1 km);
//			// move it back just inside if the new grid node does not go that high enough magnitude
//			if(erf instanceof FaultSystemSolutionERF) {
//				int gridSrcIndex = randSrcIndex;
//				Location gridSrcLoc = fssERF.getSolution().getGridSourceProvider().getGriddedRegion().getLocation(gridSrcIndex);
//				int testIndex = fssERF.getSolution().getGridSourceProvider().getGriddedRegion().indexForLocation(hypLoc);
//				if(testIndex != gridSrcIndex) {
//					// check whether hypLoc is now out of region, and return false if so
//					if(testIndex == -1)
//						return false;
//					IncrementalMagFreqDist mfd = fssERF.getSolution().getGridSourceProvider().getNodeMFD(testIndex);
////					if(mfd==null) {
////						throw new RuntimeException("testIndex="+testIndex+"\thypLoc= "+hypLoc+"\tgridLoc= "+tempERF.getSolution().getGridSourceProvider().getGriddedRegion().getLocation(testIndex));
////					}
//					int maxMagIndex = mfd.getClosestXIndex(mfd.getMaxMagWithNonZeroRate());
//					int magIndex = mfd.getClosestXIndex(erf_rup.getMag());
//					double tempLat=hypLoc.getLatitude();
//					double tempLon= hypLoc.getLongitude();
//					double tempDepth = hypLoc.getDepth();
//					double halfGrid=pointSrcDiscr/2.0;
//					if(maxMagIndex<magIndex) {
//						if(hypLoc.getLatitude()-gridSrcLoc.getLatitude()>=halfGrid)
//							tempLat=gridSrcLoc.getLatitude()+halfGrid*0.99;	// 0.99 makes sure it's inside
//						else if (hypLoc.getLatitude()-gridSrcLoc.getLatitude()<=-halfGrid)
//							tempLat=gridSrcLoc.getLatitude()-halfGrid*0.99;	// 0.99 makes sure it's inside
//						if(hypLoc.getLongitude()-gridSrcLoc.getLongitude()>=halfGrid)
//							tempLon=gridSrcLoc.getLongitude()+halfGrid*0.99;	// 0.99 makes sure it's inside
//						else if (hypLoc.getLongitude()-gridSrcLoc.getLongitude()<=-halfGrid)
//							tempLon=gridSrcLoc.getLongitude()-halfGrid*0.99;	// 0.99 makes sure it's inside
//						hypLoc = new Location(tempLat,tempLon,tempDepth);
//						int testIndex2 = fssERF.getSolution().getGridSourceProvider().getGriddedRegion().indexForLocation(hypLoc);
//						if(testIndex2 != gridSrcIndex) {
//							throw new RuntimeException("grid problem");
//						}
//					}
//				}
//			}
			
			
			rupToFillIn.setHypocenterLocation(hypLoc);
			rupToFillIn.setPointSurface(hypLoc);
			// fill in the rest
			rupToFillIn.setAveRake(erf_rup.getAveRake());
			rupToFillIn.setMag(erf_rup.getMag());
			rupToFillIn.setNthERF_Index(nthRup);
		
		
		// distance of triggered event from parent
		double distToParent = LocationUtils.linearDistanceFast(actualParentLoc, rupToFillIn.getHypocenterLocation());
		rupToFillIn.setDistanceToParent(distToParent);
		
		return true;
	}
	
	/**
	 * This adds + or - 0.005 degrees to both the lat and lon of the given location (sign is random,
	 * and a separate random value is applied to lat vs lon). This is used when a fault system rupture 
	 * is sampled in order to avoid numerical precision problems when it comes to samplind ajacent sections
	 * along the fault.  See getAveSamplerForRupture(*) for more info.
	 * @param loc
	 * @return
	 */
	public Location getRandomFuzzyLocation(Location loc) {
		double sign1=1, sign2=1;
		if(etas_utils.getRandomDouble() < 0.5)
			sign1=-1;
		if(etas_utils.getRandomDouble() < 0.5)
			sign2=-1;
		return new Location(loc.getLatitude()+sign1*0.005, loc.getLongitude()+sign2*0.005, loc.getDepth());
	}
	
	
	
	private IntegerPDF_FunctionSampler getCubeSampler(int locIndexForPar) {
		if(includeERF_Rates && includeSpatialDecay) {
			return getCubeSamplerWithDistDecay(locIndexForPar);
		}
		else if(includeERF_Rates && !includeSpatialDecay) {
			return getCubeSamplerWithERF_GriddedRatesOnly();
		}
		else if(!includeERF_Rates && includeSpatialDecay) {
			return getCubeSamplerWithOnlyDistDecay(locIndexForPar);
		}
		throw new IllegalStateException("include ERF rates and include spatial decay both false?");
	}
	
	
	

	/**
	 * This only includes gridded seismicity rates so these don't have to be stored elsewhere.
	 * Including supra-seismogenic ruptures only increases a few cube rates, with "Surprise Valley 2011 CFM, Subsection 15"
	 * having the maximum of a 3.7% increase, and that's only if it's crazy characteristic MFD is allowed to stay.
	 */
	private synchronized IntegerPDF_FunctionSampler getCubeSamplerWithERF_GriddedRatesOnly() {
		if(cubeSamplerGriddedRatesOnly == null) {
			cubeSamplerGriddedRatesOnly = new IntegerPDF_FunctionSampler(numCubes);
			for(int i=0;i<numCubes;i++) {
				double gridSrcRate = getGridSourcRateInCube(i,false);
				cubeSamplerGriddedRatesOnly.set(i,gridSrcRate);
			}
		}
		return cubeSamplerGriddedRatesOnly;
	}
	
	
	private IntegerPDF_FunctionSampler getCubeSamplerWithDistDecay(int parLocIndex) {
		Location parLoc = this.getParLocationForIndex(parLocIndex);
		getCubeSamplerWithERF_GriddedRatesOnly();	// this makes sure cubeSamplerRatesOnly (rates only) is updated
		IntegerPDF_FunctionSampler sampler = new IntegerPDF_FunctionSampler(numCubeDepths*numCubesPerDepth);
		for(int index=0; index<numCubes; index++) {
			double relLat = Math.abs(parLoc.getLatitude()-latForCubeCenter[index]);
			double relLon = Math.abs(parLoc.getLongitude()-lonForCubeCenter[index]);
			sampler.set(index,etas_LocWeightCalc.getProbAtPoint(relLat, relLon, depthForCubeCenter[index], parLoc.getDepth())*cubeSamplerGriddedRatesOnly.getY(index));
		}
		return sampler;
	}

	/**
	 * This sampler ignores the long-term rates
	 * @param mainshock
	 * @param etasLocWtCalc
	 * @return
	 */
	private IntegerPDF_FunctionSampler getCubeSamplerWithOnlyDistDecay(int parLocIndex) {
		Location parLoc = this.getParLocationForIndex(parLocIndex);
		IntegerPDF_FunctionSampler sampler = new IntegerPDF_FunctionSampler(numCubes);
//		try{
//			FileWriter fw1 = new FileWriter("test123.txt");
//			fw1.write("relLat\trelLon\trelDep\twt\n");
			for(int index=0; index<numCubes; index++) {
				double relLat = Math.abs(parLoc.getLatitude()-latForCubeCenter[index]);
				double relLon = Math.abs(parLoc.getLongitude()-lonForCubeCenter[index]);
				sampler.set(index,etas_LocWeightCalc.getProbAtPoint(relLat, relLon, depthForCubeCenter[index], parLoc.getDepth()));
//				if(relLat<0.25 && relLon<0.25)
//					fw1.write((float)relLat+"\t"+(float)relLon+"\t"+(float)relDep+"\t"+(float)sampler.getY(index)+"\n");
			}
//			fw1.close();
//		}catch(Exception e) {
//			e.printStackTrace();
//		}

		return sampler;
	}
	
	
	/**
	 * This returns the gridded seismicity nucleation rate inside the given cube.
	 * 0.0 is returned if there is no associated gridded seismicity cell.
	 * This treats the influence of fault-polygons correctly.
	 * @param cubeIndex
	 * @return
	 */
	public double getGridSourcRateInCube(int cubeIndex, boolean debug) {
		
		int griddeSeisRegionIndex = origGriddedRegion.indexForLocation(getCubeLocationForIndex(cubeIndex));
		if(griddeSeisRegionIndex != -1) {
			int cubeRegIndex = getCubeRegAndDepIndicesForIndex(cubeIndex)[0];
			// first get the original rate for this grid node
			double origGridCellRate = mfdForSrcArray[griddeSeisRegionIndex].getTotalIncrRate();
			double rate = origGridCellRate/(numPtSrcSubPts*numPtSrcSubPts*numCubeDepths);	// now divide rate among all the cubes in grid cell	
			return rate;
		}
		else {
			return 0.0;
		}
	}
	
	
	/**
	 * This returns -1 if ??????
	 * 
	 * getRelativeTriggerProbOfSourcesInCube(int cubeIndex) has a lot of redundant code, but this might be faster?
	 * 
	 * TODO this other method implies this will crash for certain cubes that have no gridded cell mapping
	 * @param srcIndex
	 * @return
	 */
	public int getRandomSourceIndexInCube(int cubeIndex, double fractionSupra) {
		
		// get gridded region index for the cube
		int griddeSeisRegionIndex = origGriddedRegion.indexForLocation(getCubeLocationForIndex(cubeIndex));
		if(griddeSeisRegionIndex == -1)	// TODO THROW EXCEPTION FOR NOW UNTIL I UNDERSTAND CONDITIONS BETTER
			throw new RuntimeException("No gridded source index for cube at: "+getCubeLocationForIndex(cubeIndex).toString());
		
		return griddeSeisRegionIndex;
		
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

	private int getCubeIndexForRegAndDepIndices(int iReg,int iDep) {
		int index = iDep*numCubesPerDepth+iReg;
		if(index<numCubes && index>=0)
			return index;
		else
			return -1;
	}
	
	private int getCubeDepthIndex(double depth) {
		int index = (int)Math.round((depth-depthDiscr/2.0)/depthDiscr);
//		if(index < numRateDepths && index >=0)
			return index;
//		else
//			throw new RuntimeException("Index "+index+" is out of bounds for depth="+depth);
	}
	
	private double getCubeDepth(int depthIndex) {
		return (double)depthIndex*depthDiscr + depthDiscr/2;
	}

	
	
	/**
	 * Region index is first element, and depth index is second
	 * @param index
	 * @return
	 */
	private int[] getParRegAndDepIndicesForIndex(int parLocIndex) {
		int[] indices = new int[2];
		indices[1] = (int)Math.floor((double)parLocIndex/(double)numParLocsPerDepth);	// depth index
		indices[0] = parLocIndex - indices[1]*numParLocsPerDepth;						// region index
		return indices;
	}
	
	public Location getParLocationForIndex(int parLocIndex) {
		int[] regAndDepIndex = getParRegAndDepIndicesForIndex(parLocIndex);
		Location regLoc = gridRegForParentLocs.getLocation(regAndDepIndex[0]);
		return new Location(regLoc.getLatitude(),regLoc.getLongitude(),getParDepth(regAndDepIndex[1]));
	}
	
	public int getParLocIndexForLocation(Location loc) {
		int iReg = gridRegForParentLocs.indexForLocation(loc);
		if(iReg==-1)
			return -1;
		int iDep = getParDepthIndex(loc.getDepth());
		if(iDep==-1)
			return -1;
		return getParLocIndexForRegAndDepIndices(iReg,iDep);
	}

	private int getParLocIndexForRegAndDepIndices(int iReg,int iDep) {
		return iDep*numParLocsPerDepth+iReg;
	}	
	
	private int getParDepthIndex(double depth) {
		int depthIndex = (int)Math.round(depth/depthDiscr);
		if(depthIndex<numParDepths)
			return depthIndex;
		else
			return -1;
	}
	
	private double getParDepth(int parDepthIndex) {
		return parDepthIndex*depthDiscr;
	}
	
	/**
	 *  This tests that the total rates represented here (plus unassigned rates) match that in the ERF.
	 *  
	 */
	public void testRates() {
		
		if(D) 	System.out.println("Running testRates()");

		long startTime=System.currentTimeMillis();
		getCubeSamplerWithERF_GriddedRatesOnly();
		
		// start with gridded seis rates
		double totGriddedRate = cubeSamplerGriddedRatesOnly.calcSumOfY_Vals();
		double totRateTest1=totGriddedRate;
				
		double totRateTest2=0;
		double duration = erf.getTimeSpan().getDuration();
		for(int s=0;s<erf.getNumSources();s++) {
			ProbEqkSource src = erf.getSource(s);
			int numRups = src.getNumRuptures();
			for(int r=0; r<numRups;r++) {
				totRateTest2 += src.getRupture(r).getMeanAnnualRate(duration);
			}
		}
		
		double runTime = ((double)(System.currentTimeMillis()-startTime))/1000d;
		if(D) {
			System.out.println("\tSum over cubes etc tot rate = "+(float)totRateTest1+" should equal tot rate from ERF = "+(float)totRateTest2+";\tratio="+(float)(totRateTest1/totRateTest2)+
					";\t totGriddedRate="+(float)totGriddedRate);
			System.out.println("testRates() took (sec): "+(float)runTime);
		}
	}
	
	
	/**
	 * This computes MFDs for each source, not including any GR correction
	 * 
	 * @param minMag
	 * @param maxMag
	 * @param numMag
	 */
	private void computeMFD_ForSrcArrays(double minMag, double maxMag, int numMag) {
		mfdForSrcArray = new SummedMagFreqDist[erf.getNumSources()];
		double duration = erf.getTimeSpan().getDuration();
		for(int s=0; s<erf.getNumSources();s++) {
			mfdForSrcArray[s] = ERF_Calculator.getTotalMFD_ForSource(erf.getSource(s), duration, minMag, maxMag, numMag, true);
		}
	}
	
	
	


	
	
	
	/**
	 * For subseismo on-fault ruptures, this gets the total rate correct but not the overall shape of 
	 * the MFD becuase the latter is influenced by all sections that contribute to any cube in the grid
	 * cell (some of which don't even appear in any of our cubes if the polygon does not capture the
	 * cube center, and only comes into the cell by less than half a cube width).
	 * 
	 * This assumes that the computeMFD_ForSrcArrays(*) has already been run (exception is thrown if not).
	 * TODO this seems overly complex!  Just get the gridded seis rate directly from the sampler
	 * @param cubeIndex
	 * @return
	 */
	private SummedMagFreqDist getCubeMFD(int cubeIndex) {
		if(mfdForSrcArray == null) {
			throw new RuntimeException("must run computeMFD_ForSrcArrays(*) first");
		}
		Hashtable<Integer,Double> rateForSrcInCubeHashtable = getNucleationRatesOfSourcesInCube(cubeIndex, 1.0);
		if(rateForSrcInCubeHashtable == null)
			return null;	
		SummedMagFreqDist magDist = new SummedMagFreqDist(mfdForSrcArray[0].getMinX(), mfdForSrcArray[0].getMaxX(), mfdForSrcArray[0].size());
		for(int srcIndex:rateForSrcInCubeHashtable.keySet()) {
			SummedMagFreqDist mfd=null;
			double srcNuclRate = rateForSrcInCubeHashtable.get(srcIndex);
			int gridIndex = srcIndex;
			
			int cubeRegIndex = getCubeRegAndDepIndicesForIndex(cubeIndex)[0];
			mfd = mfdForSrcArray[srcIndex];
			double totRate = mfd.getTotalIncrRate();
			if(totRate>0) {
				for(int m=0;m<mfd.size();m++)
					magDist.add(m, mfd.getY(m)*srcNuclRate/totRate);
			}
		}
		return magDist;
	}

	
	

	
	/**
	 * This compares the sum of the cube MFDs to the total ERF MFD.
	 * What about one including sources outside the region (i.e., Mendocino)?
	 * 
	 * 	See documentation for testGriddedSeisRatesInCubes() for an explanation of most of the discrepancy.
	 *  
	 *  There will be additional discrepancies for on-fault rates depending on how the GR correction
	 *  is applied.

	 * @param erf
	 */
	public void testMagFreqDist() {
		
		if(D) System.out.println("Running testMagFreqDist()");
		SummedMagFreqDist magDist = new SummedMagFreqDist(2.05, 8.95, 70);
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
		}

//		getPointSampler();	// make sure it exisits
		CalcProgressBar progressBar = new CalcProgressBar("Looping over all points", "junk");
		progressBar.showProgress(true);

		for(int i=0; i<numCubes;i++) {
			progressBar.updateProgress(i, numCubes);
			SummedMagFreqDist mfd = getCubeMFD(i);
			if(mfd != null)
				magDist.addIncrementalMagFreqDist(getCubeMFD(i));

//			int[] sources = srcInCubeList.get(i);
//			float[] fracts = fractionSrcInCubeList.get(i);
//			for(int s=0; s<sources.length;s++) {
//				SummedMagFreqDist mfd=null;
//				int gridIndex = s-numFltSystSources;
//				double wt = (double)fracts[s];
//				if(s >= numFltSystSources && origGridSeisTrulyOffVsSubSeisStatus[gridIndex] == 2) { // gridded seismicity and cell has both types
//					double fracInsideFaultPoly = faultPolyMgr.getNodeFraction(gridIndex);
//					if(isCubeInsideFaultPolygon[i] == 1) {
//						mfd = mfdForSrcSubSeisOnlyArray[s];
//						wt = 1.0/(fracInsideFaultPoly*numPtSrcSubPts*numPtSrcSubPts*numCubeDepths);
//					}
//					else {
//						mfd = mfdForTrulyOffOnlyArray[s];
//						wt = 1.0/((1.0-fracInsideFaultPoly)*numPtSrcSubPts*numPtSrcSubPts*numCubeDepths);
//					}
//				}
//				else {
//					mfd = mfdForSrcArray[sources[s]];
//				}
//				for(int m=0;m<mfd.getNum();m++)
//					magDist.add(m, mfd.getY(m)*wt);
//			}
		}
		progressBar.showProgress(false);

		magDist.setName("MFD from EqksAtPoint list");
		ArrayList<EvenlyDiscretizedFunc> magDistList = new ArrayList<EvenlyDiscretizedFunc>();
		magDistList.add(magDist);
		magDistList.add(magDist.getCumRateDistWithOffset());
		
		SummedMagFreqDist erfMFD = ERF_Calculator.getTotalMFD_ForERF(erf, 2.05, 8.95, 70, true);
		erfMFD.setName("MFD from ERF");
		magDistList.add(erfMFD);
		magDistList.add(erfMFD.getCumRateDistWithOffset());

		// Plot these MFDs
		GraphWindow magDistsGraph = new GraphWindow(magDistList, "Mag-Freq Distributions"); 
		magDistsGraph.setX_AxisLabel("Mag");
		magDistsGraph.setY_AxisLabel("Rate");
		magDistsGraph.setY_AxisRange(1e-6, magDistsGraph.getY_AxisRange().getUpperBound());
		magDistsGraph.setYLog(true);

	}
	
	
	
	
	
	
	
	/**
	 * This tests the degree to which the sum of cube rates on each grid cell equal the orig ERF gridded-
	 * seis rate each cell.  Differences result from the fact that the original ERF maps subseis on-fault
	 * rates to each grid cell by the amount of spatial overlap between the polygon and cell(exactly), whereas 
	 * our mapping from section polygons to cubes will miss any where the cube center is not in the polygon but 
	 * some amount of the cube area is. 
	 * 
	 */
	public void testGriddedSeisRatesInCubes() {
		
		if(D) System.out.println("testGriddedSeisRatesInCubes()");

		CaliforniaRegions.RELM_TESTING_GRIDDED mapGriddedRegion = RELM_RegionUtils.getGriddedRegionInstance();

		GriddedGeoDataSet xyzDataSet = new GriddedGeoDataSet(mapGriddedRegion, true);	
		// initialize values to zero
		for(int i=0; i<xyzDataSet.size();i++) xyzDataSet.set(i, 0);
		double duration = erf.getTimeSpan().getDuration();
		CalcProgressBar progressBar = new CalcProgressBar("Looping over sources", "junk");
		progressBar.showProgress(true);
		int iSrc=-1;
		int numSrc = erf.getNumSources();
		for(ProbEqkSource src : erf) {
			iSrc += 1;
			progressBar.updateProgress(iSrc, numSrc);
			for(ProbEqkRupture rup : src) {
				LocationList locList = rup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
				double ptRate = rup.getMeanAnnualRate(duration)/locList.size();
				for(Location loc:locList) {
					int locIndex = mapGriddedRegion.indexForLocation(loc);
					if(locIndex>=0) {
						double oldRate = xyzDataSet.get(locIndex);
						xyzDataSet.set(locIndex, ptRate+oldRate);					
					}
				}
			}
		}
		progressBar.showProgress(false);
		
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
		}

		GriddedGeoDataSet xyzDataSet2 = new GriddedGeoDataSet(mapGriddedRegion, true);	
		// initialize values to zero
		for(int i=0; i<xyzDataSet2.size();i++) xyzDataSet2.set(i, 0);
		progressBar = new CalcProgressBar("Looping over cubes", "junk");
		progressBar.showProgress(true);
		for(int i=0; i<numCubes;i++) {
			progressBar.updateProgress(i, numCubes);
			double rate = cubeSamplerGriddedRatesOnly.getY(i);
			int locIndex = mapGriddedRegion.indexForLocation(getCubeLocationForIndex(i));
			if(locIndex>=0) {
				double oldRate = xyzDataSet2.get(locIndex);
				xyzDataSet2.set(locIndex, rate+oldRate);							
			}
			// Old inefficient way:		
//			SummedMagFreqDist mfd = getCubeMFD_GriddedSeisOnly(i);
//			if(mfd != null) {
//				int locIndex = mapGriddedRegion.indexForLocation(this.getCubeLocationForIndex(i));
//				if(locIndex>=0) {
//					double oldRate = xyzDataSet2.get(locIndex);
//					xyzDataSet2.set(locIndex, mfd.getTotalIncrRate()+oldRate);							
//				}
//			}
		}
		progressBar.showProgress(false);
	
		double sum1=0;
		double sum2=0;
		for(int i=0; i<xyzDataSet2.size();i++) {
			sum1 +=xyzDataSet.get(i);
			sum2+=xyzDataSet2.get(i);
			double diff = xyzDataSet2.get(i)-xyzDataSet.get(i);
			double absFractDiff = Math.abs(diff/xyzDataSet.get(i));
			Location loc = xyzDataSet2.getLocation(i);
			if(absFractDiff>0.001)
				System.out.println(diff+"\t"+loc.getLongitude()+"\t"+loc.getLatitude());
		}
		
		System.out.println("sum over ERF = "+sum1);
		System.out.println("sum over cubes = "+sum2);
		
	}



	
	
	
	
	
	
	public SummedMagFreqDist getLongTermTotalERF_MFD() {
		if(longTermTotalERF_MFD==null)
			longTermTotalERF_MFD = ERF_Calculator.getTotalMFD_ForERF(erf, 2.55, 8.45, 60, true);		
		return longTermTotalERF_MFD;
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		ETAS_ParameterList etasParams = new ETAS_ParameterList();
		etasParams.setU3ETAS_ProbModel(U3ETAS_ProbabilityModelOptions.POISSON);
		
		CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
		
		UCERF3_GriddedSeisOnlyERF_ETAS erf = ETAS_Simulator_NoFaults.getU3_ETAS_ERF__GriddedSeisOnly(2014d,1d);
		
		
		// Overide to Poisson if needed
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		double gridSeisDiscr = 0.1;
		
		if(D) System.out.println("Making ETAS_PrimaryEventSampler");
		// first make array of rates for each source
		double sourceRates[] = new double[erf.getNumSources()];
		double duration = erf.getTimeSpan().getDuration();
		for(int s=0;s<erf.getNumSources();s++) {
			sourceRates[s] = erf.getSource(s).computeTotalEquivMeanAnnualRate(duration);
//			if(sourceRates[s] == 0)
//				System.out.println("HERE "+erf.getSource(s).getName());
		}
		
		ETAS_PrimaryEventSampler_noFaults etas_PrimEventSampler = new ETAS_PrimaryEventSampler_noFaults(griddedRegion, erf, sourceRates, 
				gridSeisDiscr,null, etasParams, new ETAS_Utils());
		
//		etas_PrimEventSampler.plotExpectedLongTermMFDs();
		
				
//		etas_PrimEventSampler.plotCharFactorStats(new File(GMT_CA_Maps.GMT_DIR, "GRcorrStats_012516"));

		
//		// Sections bulge plot
//		try {
////			etas_PrimEventSampler.plotImpliedBulgeForSubSectionsHackTestMoRate(new File(GMT_CA_Maps.GMT_DIR, "ImpliedCharFactorForSubSectionsMoRateTest"), "Test", true);
//			String dirName = "ImpliedCharFactorForSubSections";
//			if(etasParams.getApplyGridSeisCorr())
//				dirName += "_gridSeisCorr";
//			if(etasParams.getApplySubSeisForSupraNucl())
//				dirName += "_SubSeisForSupraNucl";
//			etas_PrimEventSampler.plotImpliedBulgeForSubSections(dirName, "Test", true);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}

		
//		ETAS_Simulator.plotERF_RatesMap(erf, "OrigRatesMap");
		
//		// this makes the file for adding aftershocks to gridded seis rates where needed, and also makes some plots
//		long startTime = System.currentTimeMillis();
////		etas_PrimEventSampler.getExpectedAfterShockRateInCubesFromSupraRates(10d, new ETAS_ParameterList(), 2.0, "ExpectedAfterShockRateInCubesFromSupraRatesAt7kmDepth");
//		etas_PrimEventSampler.getExpectedAfterShockRateInGridCellsFromSupraRates(10d, new ETAS_ParameterList(), 2.0, "ExpectedAfterShockRateInCubesFromSupraRatesAt7kmDepth");
//		long runtimeSec = (System.currentTimeMillis()-startTime)/1000;
//		System.out.println("runtimeSec="+runtimeSec);
		
		// Surprise valley subsection subseis rates
//		etas_PrimEventSampler.writeTotSubSeisRateForSections(2446, 2461);
		

//		// This generates 3D plotting data for cube charFactors and rates over sections 1844 to 1849 on Mojave S
//		Location loc = new Location(34.44-0.234+0.04,-118.34+0.573-0.06,1.);
//		etas_PrimEventSampler.writeRatesCrossSectionData(loc, 0.29,"crossSectData_Rates_mojave", 6.35, false);
//		etas_PrimEventSampler.writeBulgeCrossSectionData(loc, 0.29,"crossSectData_Bulge_mojave", false);
//		etas_PrimEventSampler.writeRatesCrossSectionData(loc, 0.29,"crossSectData_Rates_mojave_fltOnly", 6.35, true);
//		etas_PrimEventSampler.writeBulgeCrossSectionData(loc, 0.29,"crossSectData_Bulge_mojave_fltOnly", true);

//		etas_PrimEventSampler.plotMaxMagAtDepthMap(7d, "MaxMagAtDepth7km_Poiss_withCorr");
//		etas_PrimEventSampler.plotBulgeAtDepthMap(7d, "CharFactorAtDepth7km_Poiss_withCorr");
//		etas_PrimEventSampler.plotBulgeAtDepthAndAboveMagMap(7d,6.5, "CharFactorAtDepth7kmAndAboveM6pt5_Poiss_withCorr");
//		etas_PrimEventSampler.plotRateAtDepthMap(7d,2.55,"RatesAboveM2pt5_AtDepth7km_Poiss_withCorr");
//		etas_PrimEventSampler.plotRateAtDepthMap(7d,6.75,"RatesAboveM6pt7_AtDepth7km_Poiss_withCorr");
//		etas_PrimEventSampler.plotRateAtDepthMap(7d,6.35,"RatesAboveM6pt3_AtDepth7km_Poiss_withCorr");
//		etas_PrimEventSampler.plotRatesOnlySamplerAtDepthMap(7d,"SamplerAtDepth7km_MaxCharFactor10_Poisson");

		
//		// San Andreas (Mojave S), Subsection 4
//		etas_PrimEventSampler.getCubesAndFractForFaultSection_BoatRamp(1841, 5.0);
//		// for section with minimum dip: 1593 "Pitas Point (Lower West), Subsection 0"
//		etas_PrimEventSampler.getCubesAndFractForFaultSection_BoatRamp(1593, 5.0);
		// for "Bartlett Springs 2011 CFM, Subsection 10"
//		etas_PrimEventSampler.getCubesAndFractForFaultSection_BoatRamp(59, 5.0);

		
//		etas_PrimEventSampler.getTrulyOffFaultGR_Corr(true);
//		etas_PrimEventSampler.getFaultGR_CorrFromTotalFaultMFDs(true);
		


//		etas_PrimEventSampler.testRates();
//		etas_PrimEventSampler.testGriddedSeisRatesInCubes();
//		etas_PrimEventSampler.testMagFreqDist();
//		etas_PrimEventSampler.testTotalSubSeisOnFaultMFD();
//		etas_PrimEventSampler.testNucleationRatesOfFaultSourcesInCubes();

		// MISC TESTS:
//		etas_PrimEventSampler.testAltSubseisOnFaultMFD_Representations();
//		etas_PrimEventSampler.testFaultPolyMgr();
//		etas_PrimEventSampler.tempListSubsectsThatCoverGridCell(5172);
//		etas_PrimEventSampler.tempTestBulgeforCubesInSectPolygon(1850);
//		etas_PrimEventSampler.testTotalSubSeisOnFaultMFD();
//		etas_PrimEventSampler.tempTestBulgeInCube();
//		etas_PrimEventSampler.testSubSeisMFD_ForSect(2094);	// San Diego Trough south, Subsection 37
//		etas_PrimEventSampler.testSubSeisMFD_ForSect(1880);


		
//		HashMap<Integer,Float> testMap = etas_PrimEventSampler.getCubesAndFractForFaultSectionExponential(1841);	// Mojave S subsection 4
//		double maxRate=0;
//		for(int cubeID:testMap.keySet())
//			if(maxRate<testMap.get(cubeID))
//				maxRate=testMap.get(cubeID);
//		for(int cubeID:testMap.keySet()) {
//			double depth = etas_PrimEventSampler.getCubeLocationForIndex(cubeID).getDepth();
//			System.out.println(cubeID+"\t"+depth+"\t"+testMap.get(cubeID)+"\t"+(testMap.get(cubeID)/maxRate));
//		}

		
//		etas_PrimEventSampler.writeGMT_PieSliceDecayData(new Location(34., -118., 6.0), "gmtPie_SliceData_6kmDepth");
//		etas_PrimEventSampler.writeGMT_PieSliceDecayData(new Location(34., -118., 18.0), "gmtPie_SliceData_18kmDepth");
//		etas_PrimEventSampler.writeGMT_PieSliceRatesData(new Location(34., -118., 12.0), "gmtPie_SliceData");

		


		
		
		
		
		
		// THIS PLOTS THE MFDS IN CUBES MOVING AWAY FROM THE MOJAVE SECTION
		// Cube loc at center of Mojave subsection:	-117.9	34.46	7.0
//		ArrayList<SummedMagFreqDist> mfdList = new ArrayList<SummedMagFreqDist>();
//		ArrayList<EvenlyDiscretizedFunc> mfdListCum = new ArrayList<EvenlyDiscretizedFunc>();
//		for(int i=0; i<8; i++) {	// last 3 are outside polygon
//			Location loc = new Location(34.46+i*0.02, -117.90+i*0.02, 7.0);
//			int cubeIndex = etas_PrimEventSampler.getCubeIndexForLocation(loc);
//			SummedMagFreqDist mfd = etas_PrimEventSampler.getCubeMFD(cubeIndex);
//			mfd.setInfo("mfd "+i);
//			mfdListCum.add(mfd.getCumRateDistWithOffset());
//			mfd.setInfo(cubeIndex+"\n"+loc+"\n"+mfd.toString());
//			mfdList.add(mfd);
//		}
//		GraphWindow mfd_Graph = new GraphWindow(mfdList, "MFDs"); 
//		mfd_Graph.setX_AxisLabel("Mag");
//		mfd_Graph.setY_AxisLabel("Rate");
//		mfd_Graph.setYLog(true);
//		mfd_Graph.setPlotLabelFontSize(22);
//		mfd_Graph.setAxisLabelFontSize(20);
//		mfd_Graph.setTickLabelFontSize(18);			
//		GraphWindow cumMFD_Graph = new GraphWindow(mfdListCum, "Cumulative MFDs"); 
//		cumMFD_Graph.setX_AxisLabel("Mag");
//		cumMFD_Graph.setY_AxisLabel("Cumulative Rate");
//		cumMFD_Graph.setYLog(true);
//		cumMFD_Graph.setPlotLabelFontSize(22);
//		cumMFD_Graph.setAxisLabelFontSize(20);
//		cumMFD_Graph.setTickLabelFontSize(18);			
		
		
	}
	
	/**
	 * This is a potentially more memory efficient way of reading/storing the int value, where there is
	 * only one int value/object for each index; turns out its not better than using int[] with duplicates.
	 * 
	 * TODO remove because this is not used?
	 * 
	 * Reads a file created by {@link MatrixIO.intListListToFile} or {@link MatrixIO.intArraysListToFile}
	 * into an integer array list.
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public ArrayList<ArrayList<Integer>> intArraysListFromFile(File file) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");

		long len = file.length();
		Preconditions.checkState(len > 0, "file is empty!");
		Preconditions.checkState(len % 4 == 0, "file size isn't evenly divisible by 4, " +
		"thus not a sequence of double & integer values.");

		return intArraysListFromInputStream(new FileInputStream(file));
	}

	/**
	 * Reads a file created by {@link MatrixIO.intListListToFile} or {@link MatrixIO.intArraysListToFile}
	 * into an integer array list.
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public ArrayList<ArrayList<Integer>> intArraysListFromInputStream(
			InputStream is) throws IOException {
		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);

		DataInputStream in = new DataInputStream(is);

		int size = in.readInt();

		Preconditions.checkState(size > 0, "Size must be > 0!");

		ArrayList<ArrayList<Integer>> list = new ArrayList<ArrayList<Integer>>();
		
		ArrayList<Integer> idList = new ArrayList<Integer>();
		for(int i=0;i<totNumSrc;i++)
			idList.add(new Integer(i));
		
		for (int i=0; i<size; i++) {
			int listSize = in.readInt();
			ArrayList<Integer> intList = new ArrayList<Integer>();
			for(int j=0;j<listSize;j++)
				intList.add(idList.get(in.readInt()));
			list.add(intList);
		}

		in.close();

		return list;
	}
	
	
		
	public List<EvenlyDiscretizedFunc> generateRuptureDiagnostics(ETAS_EqkRupture rupture, double expNum, String rupInfo, File resultsDir, FileWriter info_fileWriter) throws IOException {
		
		if(D) System.out.println("Starting generateRuptureDiagnostics");
		
		File subDirName = new File(resultsDir,"Diagnostics_"+rupInfo);
		if(!subDirName.exists())
			subDirName.mkdir();
		
		// the following adds fuzziness to rup surf locs, even if point sournce
		IntegerPDF_FunctionSampler aveCubeSamplerForRup = getAveSamplerForRupture(rupture);
		
//		double[] fracArray = {0.99,0.999,0.9999,0.99999,1.0};
//		for(double frac:fracArray) {
//			long time=System.currentTimeMillis();
//			System.out.println("Starting getOrderedIndicesOfHighestXFract; frac="+frac);
//			List<Integer> list = aveCubeSamplerForRup.getOrderedIndicesOfHighestXFract(frac);
//			System.out.println("Done with getOrderedIndicesOfHighestXFract; that took (ms): "+(System.currentTimeMillis()-time)+"\tnum="+list.size()+"\tof "+aveCubeSamplerForRup.size());			
//		}

		double[] relSrcProbs = getRelativeTriggerProbOfEachSource(aveCubeSamplerForRup, 0.99, rupture);
		
		long st = System.currentTimeMillis();
		
		SummedMagFreqDist expMFD = getExpectedPrimaryMFD_PDF(relSrcProbs);
		expMFD.scale(expNum);	
		expMFD.setName("Expected MFD for primary aftershocks of "+rupInfo);
		EvenlyDiscretizedFunc expCumMFD=expMFD.getCumRateDistWithOffset();
		expCumMFD.setInfo("Data:\n"+expCumMFD.getMetadataString());
		
		// make GR comparison
		double minMag = expMFD.getMinMagWithNonZeroRate();
		double maxMagWithNonZeroRate = expMFD.getMaxMagWithNonZeroRate();
		int numMag = (int)Math.round((maxMagWithNonZeroRate-minMag)/expMFD.getDelta()) + 1;
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(1.0, 1.0, minMag, maxMagWithNonZeroRate, numMag);
		gr.scaleToIncrRate(3.05, expMFD.getY(3.05));
		gr.setName("Perfect GR");
		gr.setInfo("Data:\n"+gr.getMetadataString());
		EvenlyDiscretizedFunc grCum=gr.getCumRateDistWithOffset();
		grCum.setInfo("Data:\n"+grCum.getMetadataString());
	
		ArrayList<EvenlyDiscretizedFunc> incrMFD_List = new ArrayList<EvenlyDiscretizedFunc>();
		incrMFD_List.add(gr);
		incrMFD_List.add(expMFD);
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));

		GraphWindow magProbDistsGraph = new GraphWindow(incrMFD_List, "Expected Primary Aftershock MFD", plotChars); 
		magProbDistsGraph.setX_AxisLabel("Mag");
		magProbDistsGraph.setY_AxisLabel("Expected Num");
//		magProbDistsGraph.setY_AxisRange(10e-9, 10e-1);
//		magProbDistsGraph.setX_AxisRange(2., 9.);
		magProbDistsGraph.setYLog(true);
		magProbDistsGraph.setPlotLabelFontSize(22);
		magProbDistsGraph.setAxisLabelFontSize(20);
		magProbDistsGraph.setTickLabelFontSize(18);			
		
		ArrayList<EvenlyDiscretizedFunc> cumMFD_List = new ArrayList<EvenlyDiscretizedFunc>();
		cumMFD_List.add(grCum);
		cumMFD_List.add(expCumMFD);
		
		// cumulative distribution of expected num primary
		GraphWindow cumDistsGraph = new GraphWindow(cumMFD_List, "Expected Cumulative Primary Aftershock MFD", plotChars); 
		cumDistsGraph.setX_AxisLabel("Mag");
		cumDistsGraph.setY_AxisLabel("Expected Number");
		cumDistsGraph.setY_AxisRange(10e-8, 10e4);
		cumDistsGraph.setX_AxisRange(2.,9.);
		cumDistsGraph.setYLog(true);
		cumDistsGraph.setPlotLabelFontSize(22);
		cumDistsGraph.setAxisLabelFontSize(20);
		cumDistsGraph.setTickLabelFontSize(18);			
		
		List<EvenlyDiscretizedFunc> expectedPrimaryMFDsForScenarioList = new ArrayList<EvenlyDiscretizedFunc>();
		expectedPrimaryMFDsForScenarioList.add(expMFD);
		expectedPrimaryMFDsForScenarioList.add(expCumMFD);

		String fileNamePrefix = new File(subDirName,rupInfo+"_ExpPrimMFD").getAbsolutePath();
		
			try {
				magProbDistsGraph.saveAsPDF(fileNamePrefix+"_Incr.pdf");
				magProbDistsGraph.saveAsTXT(fileNamePrefix+"_Incr.txt");
				if(!Double.isNaN(expNum)) {
					cumDistsGraph.saveAsPDF(fileNamePrefix+"_Cum.pdf");
					cumDistsGraph.saveAsTXT(fileNamePrefix+"_Cum.txt");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		if (D) System.out.println("expectedPrimaryMFDsForScenarioList took (msec) "+(System.currentTimeMillis()-st));
		
		// Compute Primary Event Sampler Map
		st = System.currentTimeMillis();
		plotSamplerMap(aveCubeSamplerForRup, "Primary Sampler for "+rupInfo, "PrimarySamplerMap_"+rupInfo, subDirName);
		if (D) System.out.println("plotSamplerMap took (msec) "+(System.currentTimeMillis()-st));
		
		return expectedPrimaryMFDsForScenarioList;
	}


	
	/**
	 * This plots the spatial distribution of probabilities implied by the given cubeSampler
	 * (probs are summed inside each spatial bin of gridRegForCubes).
	 * 
	 * TODO move this to ETAS_SimAnalysisTools
	 * 
	 * @param label - plot label
	 * @param dirName - the name of the directory
	 * @param path - where to put the dir
	 * @return
	 */
	public String plotSamplerMap(IntegerPDF_FunctionSampler cubeSampler, String label, String dirName, File path) {
		
		GMT_MapGenerator mapGen = GMT_CA_Maps.getDefaultGMT_MapGenerator();
		
		CPTParameter cptParam = (CPTParameter )mapGen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		
		mapGen.setParameter(GMT_MapGenerator.MIN_LAT_PARAM_NAME,gridRegForCubes.getMinGridLat());
		mapGen.setParameter(GMT_MapGenerator.MAX_LAT_PARAM_NAME,gridRegForCubes.getMaxGridLat());
		mapGen.setParameter(GMT_MapGenerator.MIN_LON_PARAM_NAME,gridRegForCubes.getMinGridLon());
		mapGen.setParameter(GMT_MapGenerator.MAX_LON_PARAM_NAME,gridRegForCubes.getMaxGridLon());
		mapGen.setParameter(GMT_MapGenerator.GRID_SPACING_PARAM_NAME, gridRegForCubes.getLatSpacing());	// assume lat and lon spacing are same
//		mapGen.setParameter(GMT_MapGenerator.GRID_SPACING_PARAM_NAME, 0.05);	// assume lat and lon spacing are same
		mapGen.setParameter(GMT_MapGenerator.LOG_PLOT_NAME,true);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_FROMDATA);
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_MANUALLY);
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,-3.5);
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,1.5);

		//mapGen.setParameter(GMT_MapGenerator.GMT_SMOOTHING_PARAM_NAME, true);


		GriddedGeoDataSet xyzDataSet = new GriddedGeoDataSet(gridRegForCubes, true);
		
		// initialize values to zero
		for(int i=0; i<xyzDataSet.size();i++) xyzDataSet.set(i, 0);
		
		for(int i=0;i<numCubes;i++) {
			Location loc = getCubeLocationForIndex(i);
			int mapLocIndex = gridRegForCubes.indexForLocation(loc);
			if(mapLocIndex>=0) {
				double oldRate = xyzDataSet.get(mapLocIndex);
				xyzDataSet.set(mapLocIndex, cubeSampler.getY(i)+oldRate);					
			}
		}
		
		// normalize xyzDataSet (since cubeSamplers aren't necessarily normalized)
		
		// check sum
		double sum=0;
		for(int i=0; i<xyzDataSet.size();i++) sum += xyzDataSet.get(i);
		for(int i=0; i<xyzDataSet.size();i++) xyzDataSet.set(i,xyzDataSet.get(i)/sum);
		// check
//		sum=0;
//		for(int i=0; i<xyzDataSet.size();i++) sum += xyzDataSet.get(i);
//		System.out.println("sumTestForMaps="+sum);
		
		// remove any zeros because they blow up the log-plot
//		System.out.println("xyzDataSet.getMinZ()="+xyzDataSet.getMinZ());
		if(xyzDataSet.getMinZ()==0) {
			double minNonZero = Double.MAX_VALUE;
			for(int i=0; i<xyzDataSet.size();i++) {
				if(xyzDataSet.get(i)>0 && xyzDataSet.get(i)<minNonZero)
					minNonZero=xyzDataSet.get(i);
			}
			for(int i=0; i<xyzDataSet.size();i++) {
				if(xyzDataSet.get(i)==0)
					xyzDataSet.set(i,minNonZero);
			}
//			System.out.println("minNonZero="+minNonZero);
//			System.out.println("xyzDataSet.getMinZ()="+xyzDataSet.getMinZ());
		}
		



//		System.out.println("Min & Max Z: "+xyzDataSet.getMinZ()+"\t"+xyzDataSet.getMaxZ());
		String metadata = "Map from calling plotSamplerMap(*) method";
		
		try {
				String url = mapGen.makeMapUsingServlet(xyzDataSet, label, metadata, dirName);
				metadata += GMT_MapGuiBean.getClickHereHTML(mapGen.getGMTFilesWebAddress());
				ImageViewerWindow imgView = new ImageViewerWindow(url,metadata, true);		
				
				File downloadDir = null;
				if(path != null)
					downloadDir = new File(path, dirName);
				else
					downloadDir = new File(dirName);
				if (!downloadDir.exists())
					downloadDir.mkdir();
				File zipFile = new File(downloadDir, "allFiles.zip");
				// construct zip URL
				String zipURL = url.substring(0, url.lastIndexOf('/')+1)+"allFiles.zip";
				FileUtils.downloadURL(zipURL, zipFile);
				FileUtils.unzipFile(zipFile, downloadDir);

//			System.out.println("GMT Plot Filename: "+name);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "For Block Prob Map: "+mapGen.getGMTFilesWebAddress()+" (deleted at midnight)";
	}
	
	
	
	
	public String plotMaxMagAtDepthMap(double depth, String dirName) {
		
		GMT_MapGenerator mapGen = GMT_CA_Maps.getDefaultGMT_MapGenerator();
		
		CPTParameter cptParam = (CPTParameter )mapGen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		
		mapGen.setParameter(GMT_MapGenerator.MIN_LAT_PARAM_NAME,gridRegForCubes.getMinGridLat());
		mapGen.setParameter(GMT_MapGenerator.MAX_LAT_PARAM_NAME,gridRegForCubes.getMaxGridLat());
		mapGen.setParameter(GMT_MapGenerator.MIN_LON_PARAM_NAME,gridRegForCubes.getMinGridLon());
		mapGen.setParameter(GMT_MapGenerator.MAX_LON_PARAM_NAME,gridRegForCubes.getMaxGridLon());
		mapGen.setParameter(GMT_MapGenerator.GRID_SPACING_PARAM_NAME, gridRegForCubes.getLatSpacing());	// assume lat and lon spacing are same
		mapGen.setParameter(GMT_MapGenerator.LOG_PLOT_NAME,false);
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_FROMDATA);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_MANUALLY);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,6.0);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,8.5);

		GriddedGeoDataSet maxMagData = new GriddedGeoDataSet(gridRegForCubes, true);
		int depthIndex = getCubeDepthIndex(depth);
		int numCubesAtDepth = maxMagData.size();
		CalcProgressBar progressBar = new CalcProgressBar("Looping over all points", "junk");
		progressBar.showProgress(true);
		
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
		}

		for(int i=0; i<numCubesAtDepth;i++) {
			progressBar.updateProgress(i, numCubesAtDepth);
			int samplerIndex = getCubeIndexForRegAndDepIndices(i, depthIndex);
			SummedMagFreqDist mfd = getCubeMFD(samplerIndex);
			if(mfd != null)
				maxMagData.set(i, mfd.getMaxMagWithNonZeroRate());
//			Location loc = maxMagData.getLocation(i);
//			System.out.println(loc.getLongitude()+"\t"+loc.getLatitude()+"\t"+maxMagData.get(i));
		}
		progressBar.showProgress(false);

		
		String metadata = "Map from calling plotMaxMagAtDepthMap(*) method";
		
		try {
				String url = mapGen.makeMapUsingServlet(maxMagData, "Max Mag at "+depth+" km depth", metadata, dirName);
				metadata += GMT_MapGuiBean.getClickHereHTML(mapGen.getGMTFilesWebAddress());
				ImageViewerWindow imgView = new ImageViewerWindow(url,metadata, true);		
				
				File downloadDir = new File(GMT_CA_Maps.GMT_DIR, dirName);
				if (!downloadDir.exists())
					downloadDir.mkdir();
				File zipFile = new File(downloadDir, "allFiles.zip");
				// construct zip URL
				String zipURL = url.substring(0, url.lastIndexOf('/')+1)+"allFiles.zip";
				FileUtils.downloadURL(zipURL, zipFile);
				FileUtils.unzipFile(zipFile, downloadDir);

//			System.out.println("GMT Plot Filename: "+name);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "For Max Mag at depth Map: "+mapGen.getGMTFilesWebAddress()+" (deleted at midnight)";
	}
	
	
	/**
	 * This plots the event rates above the specified magnitude for cubes at the given depth
	 * (not including the spatial decay of any main shock)
	 * @param depth
	 * @param dirName
	 * @return
	 */
	public String plotRateAtDepthMap(double depth, double mag, String dirName) {
		
		GMT_MapGenerator mapGen = GMT_CA_Maps.getDefaultGMT_MapGenerator();
		
		CPTParameter cptParam = (CPTParameter )mapGen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		cptParam.getValue().setBelowMinColor(Color.WHITE);
		
		mapGen.setParameter(GMT_MapGenerator.MIN_LAT_PARAM_NAME,gridRegForCubes.getMinGridLat());
		mapGen.setParameter(GMT_MapGenerator.MAX_LAT_PARAM_NAME,gridRegForCubes.getMaxGridLat());
		mapGen.setParameter(GMT_MapGenerator.MIN_LON_PARAM_NAME,gridRegForCubes.getMinGridLon());
		mapGen.setParameter(GMT_MapGenerator.MAX_LON_PARAM_NAME,gridRegForCubes.getMaxGridLon());
		mapGen.setParameter(GMT_MapGenerator.GRID_SPACING_PARAM_NAME, gridRegForCubes.getLatSpacing());	// assume lat and lon spacing are same

		GriddedGeoDataSet xyzDataSet = new GriddedGeoDataSet(gridRegForCubes, true);
		int depthIndex = getCubeDepthIndex(depth);
		int numCubesAtDepth = xyzDataSet.size();
		CalcProgressBar progressBar = new CalcProgressBar("Looping over all points", "junk");
		progressBar.showProgress(true);
		
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
		}
		
		int magIndex = mfdForSrcArray[0].getClosestXIndex(mag);


		for(int i=0; i<numCubesAtDepth;i++) {
			progressBar.updateProgress(i, numCubesAtDepth);
			int samplerIndex = getCubeIndexForRegAndDepIndices(i, depthIndex);
			SummedMagFreqDist mfd = getCubeMFD(samplerIndex);
			double rate = 0.0;
			if(mfd != null)
				rate = getCubeMFD(samplerIndex).getCumRate(magIndex);
			if(rate == 0.0)
				rate = 1e-16;
			xyzDataSet.set(i, rate);
//			Location loc = xyzDataSet.getLocation(i);
//			System.out.println(loc.getLongitude()+"\t"+loc.getLatitude()+"\t"+xyzDataSet.get(i));
		}
		progressBar.showProgress(false);
		
		mapGen.setParameter(GMT_MapGenerator.LOG_PLOT_NAME,true);
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_FROMDATA);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_MANUALLY);
//		double maxZ = Math.ceil(Math.log10(xyzDataSet.getMaxZ()))+0.5;
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,maxZ-5);
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,maxZ);
		
		if(mag<5) {
			mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,-5d);
			mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,-1d);			
		}
		else {
			mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,-9d);
			mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,-4d);
		}


		String metadata = "Map from calling plotRateAtDepthMap(*) method";
		
		try {
				String url = mapGen.makeMapUsingServlet(xyzDataSet, "M≥"+mag+" Rates at "+depth+" km depth", metadata, dirName);
				metadata += GMT_MapGuiBean.getClickHereHTML(mapGen.getGMTFilesWebAddress());
				ImageViewerWindow imgView = new ImageViewerWindow(url,metadata, true);		
				
				File downloadDir = new File(GMT_CA_Maps.GMT_DIR, dirName);
				if (!downloadDir.exists())
					downloadDir.mkdir();
				File zipFile = new File(downloadDir, "allFiles.zip");
				// construct zip URL
				String zipURL = url.substring(0, url.lastIndexOf('/')+1)+"allFiles.zip";
				FileUtils.downloadURL(zipURL, zipFile);
				FileUtils.unzipFile(zipFile, downloadDir);

//			System.out.println("GMT Plot Filename: "+name);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "For rates at depth above mag map: "+mapGen.getGMTFilesWebAddress()+" (deleted at midnight)";
	}
	
	
	
	/**
	 * This plots the event rates above the specified magnitude for cubes at the given depth
	 * (not including the spatial decay of any main shock)
	 * @param depth
	 * @param dirName
	 * @return
	 */
	public String plotRatesOnlySamplerAtDepthMap(double depth, String dirName) {
		
		GMT_MapGenerator mapGen = GMT_CA_Maps.getDefaultGMT_MapGenerator();
		
		CPTParameter cptParam = (CPTParameter )mapGen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		cptParam.getValue().setBelowMinColor(Color.WHITE);
		
		mapGen.setParameter(GMT_MapGenerator.MIN_LAT_PARAM_NAME,gridRegForCubes.getMinGridLat());
		mapGen.setParameter(GMT_MapGenerator.MAX_LAT_PARAM_NAME,gridRegForCubes.getMaxGridLat());
		mapGen.setParameter(GMT_MapGenerator.MIN_LON_PARAM_NAME,gridRegForCubes.getMinGridLon());
		mapGen.setParameter(GMT_MapGenerator.MAX_LON_PARAM_NAME,gridRegForCubes.getMaxGridLon());
		mapGen.setParameter(GMT_MapGenerator.GRID_SPACING_PARAM_NAME, gridRegForCubes.getLatSpacing());	// assume lat and lon spacing are same

		GriddedGeoDataSet xyzDataSet = new GriddedGeoDataSet(gridRegForCubes, true);
		int depthIndex = getCubeDepthIndex(depth);
		int numCubesAtDepth = xyzDataSet.size();
		CalcProgressBar progressBar = new CalcProgressBar("Looping over all points", "junk");
		progressBar.showProgress(true);
		
		getCubeSamplerWithERF_GriddedRatesOnly();
		
		for(int i=0; i<numCubesAtDepth;i++) {
			progressBar.updateProgress(i, numCubesAtDepth);
			int samplerIndex = getCubeIndexForRegAndDepIndices(i, depthIndex);
			double rate = cubeSamplerGriddedRatesOnly.getY(samplerIndex);
			if(rate <= 1e-16)
				rate = 1e-16;
			xyzDataSet.set(i, rate);
		}
		progressBar.showProgress(false);
		
		mapGen.setParameter(GMT_MapGenerator.LOG_PLOT_NAME,true);
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_FROMDATA);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_MANUALLY);
//		double maxZ = Math.ceil(Math.log10(xyzDataSet.getMaxZ()))+0.5;
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,maxZ-5);
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,maxZ);
		
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,-7d);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,-1d);			

		String metadata = "Map from calling plotSamplerAtDepthMap(*) method";
		
		try {
				String url = mapGen.makeMapUsingServlet(xyzDataSet, "Rates at depth="+depth, metadata, dirName);
				metadata += GMT_MapGuiBean.getClickHereHTML(mapGen.getGMTFilesWebAddress());
				ImageViewerWindow imgView = new ImageViewerWindow(url,metadata, true);		
				
				File downloadDir = new File(GMT_CA_Maps.GMT_DIR, dirName);
				if (!downloadDir.exists())
					downloadDir.mkdir();
				File zipFile = new File(downloadDir, "allFiles.zip");
				// construct zip URL
				String zipURL = url.substring(0, url.lastIndexOf('/')+1)+"allFiles.zip";
				FileUtils.downloadURL(zipURL, zipFile);
				FileUtils.unzipFile(zipFile, downloadDir);

//			System.out.println("GMT Plot Filename: "+name);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "For rates at depth above mag map: "+mapGen.getGMTFilesWebAddress()+" (deleted at midnight)";
	}

	

	
	
	/**
	 * 
	 * @param label - plot label
	 * @param local - whether GMT map is made locally or on server
	 * @param dirName
	 * @return
	 */
	public String plotRandomSampleRatesMap(String dirName, int numYrs) {
		
		GMT_MapGenerator mapGen = new GMT_MapGenerator();
		mapGen.setParameter(GMT_MapGenerator.GMT_SMOOTHING_PARAM_NAME, false);
		mapGen.setParameter(GMT_MapGenerator.TOPO_RESOLUTION_PARAM_NAME, GMT_MapGenerator.TOPO_RESOLUTION_NONE);
		mapGen.setParameter(GMT_MapGenerator.MIN_LAT_PARAM_NAME,31.5);		// -R-125.4/-113.0/31.5/43.0
		mapGen.setParameter(GMT_MapGenerator.MAX_LAT_PARAM_NAME,43.0);
		mapGen.setParameter(GMT_MapGenerator.MIN_LON_PARAM_NAME,-125.4);
		mapGen.setParameter(GMT_MapGenerator.MAX_LON_PARAM_NAME,-113.0);
		mapGen.setParameter(GMT_MapGenerator.LOG_PLOT_NAME,true);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_MANUALLY);
		// this is good for M≥2.5
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,-2.);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,1.);


		CaliforniaRegions.RELM_TESTING_GRIDDED mapGriddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
		GriddedGeoDataSet xyzDataSet = new GriddedGeoDataSet(mapGriddedRegion, true);
		
		// initialize values to zero
		for(int i=0; i<xyzDataSet.size();i++) xyzDataSet.set(i, 0);
		
		// do this to make sure it exists
		getCubeSamplerWithERF_GriddedRatesOnly();
		
		// get numYrs yrs worth of samples
		totRate=cubeSamplerGriddedRatesOnly.calcSumOfY_Vals();
		long numSamples = (long)numYrs*(long)totRate;
		System.out.println("num random samples for map test = "+numSamples+"\ntotRate="+totRate);
		
		CalcProgressBar progressBar = new CalcProgressBar("Looping random samples", "junk");
		progressBar.showProgress(true);
		for(long i=0;i<numSamples;i++) {
			progressBar.updateProgress(i, numSamples);
			int indexFromSampler = cubeSamplerGriddedRatesOnly.getRandomInt(etas_utils.getRandomDouble());
			int[] regAndDepIndex = getCubeRegAndDepIndicesForIndex(indexFromSampler);
			int indexForMap = mapGriddedRegion.indexForLocation(gridRegForCubes.locationForIndex(regAndDepIndex[0]));	// ignoring depth
			if(indexForMap>-0) {
				double oldNum = xyzDataSet.get(indexForMap)*numYrs;
				xyzDataSet.set(indexForMap, (1.0+oldNum)/(double)numYrs);
			}
			
		}
		progressBar.showProgress(false);
		
		if(D) 
			System.out.println("RandomSampleRatesMap: min="+xyzDataSet.getMinZ()+"; max="+xyzDataSet.getMaxZ());

		String metadata = "Map from calling RandomSampleRatesMap() method";
		
		try {
				String url = mapGen.makeMapUsingServlet(xyzDataSet, "RandomSampleRatesMap", metadata, dirName);
				metadata += GMT_MapGuiBean.getClickHereHTML(mapGen.getGMTFilesWebAddress());
				ImageViewerWindow imgView = new ImageViewerWindow(url,metadata, true);		
				
				File downloadDir = new File(GMT_CA_Maps.GMT_DIR, dirName);
				if (!downloadDir.exists())
					downloadDir.mkdir();
				File zipFile = new File(downloadDir, "allFiles.zip");
				// construct zip URL
				String zipURL = url.substring(0, url.lastIndexOf('/')+1)+"allFiles.zip";
				FileUtils.downloadURL(zipURL, zipFile);
				FileUtils.unzipFile(zipFile, downloadDir);

//			System.out.println("GMT Plot Filename: "+name);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "For RandomSampleRatesMap: "+mapGen.getGMTFilesWebAddress()+" (deleted at midnight)";
	}

}
