package scratch.UCERF3.erf.ETAS;

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
public class ETAS_PrimaryEventSampler {
	
	final static double MAX_CHAR_FACTOR = 1e10;
	boolean APPLY_ERT_FAULTS;	// this tells whether to apply elastic-rebound triggereing (ERT), where likelihood of section triggering is proportional to normalized time since last
	boolean APPLY_ERT_GRIDDED=true;	// this tells whether to apply elastic-rebound triggereing (ERT) for gridded seismicity
	
	final static boolean D=ETAS_Simulator.D;
	
	double trulyOffFaultGR_Corr = Double.NaN;
	
	String defaultSectDistForCubeCacheFilename="dev/scratch/UCERF3/data/scratch/InversionSolutions/sectDistForCubeCache";
	String defaultSectInCubeCacheFilename="dev/scratch/UCERF3/data/scratch/InversionSolutions/sectInCubeCache";
	public static final String defaultGriddedCorrFilename="dev/scratch/UCERF3/data/scratch/InversionSolutions/griddedSeisCorrectionCache";
	
	String defaultCubeInsidePolyCacheFilename="dev/scratch/UCERF3/data/scratch/InversionSolutions/cubeInsidePolyCache";
	
	// these define the cubes in space
	int numCubeDepths, numCubesPerDepth, numCubes, numParDepths, numParLocsPerDepth, numParLocs;
	double maxDepth, depthDiscr;
	GriddedRegion origGriddedRegion;
	GriddedRegion gridRegForCubes; // the center of each cube in lat/lon space
	GriddedRegion gridRegForParentLocs;
	double cubeLatLonSpacing;
	
	AbstractNthRupERF erf;
	FaultSystemSolutionERF fssERF;
	int numFltSystSources=-1, totNumSrc;
	FaultSystemRupSet rupSet;
	FaultPolyMgr faultPolyMgr;

	
	int numPtSrcSubPts;
	double pointSrcDiscr;
	
	// this is for each cube
	double[] latForCubeCenter, lonForCubeCenter, depthForCubeCenter;
	
	// this will hold the rate of each ERF source (which can be modified by external objects)
	double sourceRates[];
	double totSectNuclRateArray[];
	double totalSectRateInCubeArray[];
	ArrayList<HashMap<Integer,Float>> srcNuclRateOnSectList;
	SummedMagFreqDist[] longTermSupraSeisMFD_OnSectArray;
	List<? extends IncrementalMagFreqDist> longTermSubSeisMFD_OnSectList;
	double[] totLongTermSubSeisRateOnSectArray;
	SummedMagFreqDist longTermTotalERF_MFD;

	
	// this stores the rates of erf ruptures that go unassigned (outside the region here)
//	double rateUnassigned;
	
	double totRate;
	
	List<float[]> fractionSectInCubeList;
	List<float[]> sectDistForCubeList;
	List<int[]> sectInCubeList;
	int[] isCubeInsideFaultPolygon;	// independent of depth, so number of elements the equal to numCubesPerDepth
	int[] numCubesInsideFaultPolygonArray;
	

//	 0 the entire grid cell is truly off fault (outside fault polygons)
//	 1 the entire grid cell is subseismogenic (within fault polygons)
//	 2 a mix of the two types
	int[] origGridSeisTrulyOffVsSubSeisStatus;
	
	IntegerPDF_FunctionSampler cubeSamplerGriddedRatesOnly; 
	
	Map<Integer,ArrayList<ETAS_EqkRupture>> eventListForParLocIndexMap;  // key is the parLocIndex and value is a list of ruptures to process
//	int[] numForthcomingEventsAtParentLoc;
//	int numCachedSamplers=0;
//	int incrForReportingNumCachedSamplers=100;
//	int nextNumCachedSamplers=incrForReportingNumCachedSamplers;
	
	double[] grCorrFactorForSectArray;	// this will be 1.0 if applyGR_Corr = false
	double[] charFactorForSectArray;	// this is populated regardless of whether applyGR_Corr = true

	// ETAS distance decay params
	double etasDistDecay, etasMinDist;
	boolean includeERF_Rates=false;
	boolean applyGR_Corr;
	U3ETAS_ProbabilityModelOptions probModel;
	boolean wtSupraNuclBySubSeisRates;

	
	boolean includeSpatialDecay;
	
	ETAS_LocationWeightCalculator etas_LocWeightCalc;
	
	SummedMagFreqDist[] mfdForSrcArray;
	SummedMagFreqDist[] mfdForSrcSubSeisOnlyArray;
	SummedMagFreqDist[] mfdForTrulyOffOnlyArray;
	
	
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
	 * @param inputSectDistForCubeList
	 * @param inputSectInCubeList
	 * @param inputIsCubeInsideFaultPolygon
	 */
	public ETAS_PrimaryEventSampler(GriddedRegion griddedRegion, AbstractNthRupERF erf, double sourceRates[],
			double pointSrcDiscr, String outputFileNameWithPath, ETAS_ParameterList etasParams, ETAS_Utils etas_utils,
			List<float[]> inputSectDistForCubeList, List<int[]> inputSectInCubeList,  int[] inputIsCubeInsideFaultPolygon) {

		this(griddedRegion, DEFAULT_NUM_PT_SRC_SUB_PTS, erf, sourceRates, DEFAULT_MAX_DEPTH, DEFAULT_DEPTH_DISCR, 
				pointSrcDiscr, outputFileNameWithPath, etasParams, true, etas_utils, inputSectDistForCubeList, 
				inputSectInCubeList, inputIsCubeInsideFaultPolygon);
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
	 * @param inputSectDistForCubeList
	 * @param inputSectInCubeList
	 * @param inputIsCubeInsideFaultPolygon
	 */
	public ETAS_PrimaryEventSampler(GriddedRegion griddedRegion, int numPtSrcSubPts, AbstractNthRupERF erf, double sourceRates[],
			double maxDepth, double depthDiscr, double pointSrcDiscr, String outputFileNameWithPath, ETAS_ParameterList etasParams, 
			boolean includeSpatialDecay, ETAS_Utils etas_utils, List<float[]> inputSectDistForCubeList, List<int[]> inputSectInCubeList, 
			int[] inputIsCubeInsideFaultPolygon) {
		
		this.etasParams = etasParams;

		this.etasDistDecay=etasParams.get_q();
		this.etasMinDist=etasParams.get_d();
		this.applyGR_Corr = etasParams.getImposeGR();
		this.probModel = etasParams.getU3ETAS_ProbModel();
		this.wtSupraNuclBySubSeisRates=etasParams.getApplySubSeisForSupraNucl();
		
		if(probModel == U3ETAS_ProbabilityModelOptions.FULL_TD) {
			APPLY_ERT_FAULTS = true;
			APPLY_ERT_GRIDDED = true;
		}
		else { // NO_ERT or POISSON
			APPLY_ERT_FAULTS = false;
			APPLY_ERT_GRIDDED = false;
		}
		
//		// TEST for just ERT effect with no time dep probabilities
//		APPLY_ERT_FAULTS = true;
//		APPLY_ERT_GRIDDED = true;

		
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
		
		
		// fill in rupSet and faultPolyMgr is erf is a FaultSystemSolutionERF
		if(erf instanceof FaultSystemSolutionERF) {
			rupSet = ((FaultSystemSolutionERF)erf).getSolution().getRupSet();
			faultPolyMgr = FaultPolyMgr.create(rupSet.getFaultSectionDataList(), InversionTargetMFDs.FAULT_BUFFER);	// this works for U3, but not generalized
			fssERF = (FaultSystemSolutionERF) erf;
			numFltSystSources = fssERF.getNumFaultSystemSources();
		}
		else {
			rupSet = null;
			faultPolyMgr = null;	
			fssERF = null;
			numFltSystSources=0;
		}
		
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
		
		
		computeGR_CorrFactorsForSections();
		
		
		// this is used by the custom cache for smart evictions
		eventListForParLocIndexMap = Maps.newHashMap();
		
		totNumSrc = erf.getNumSources();
		if(totNumSrc != sourceRates.length)
			throw new RuntimeException("Problem with number of sources");
		
		if(D) System.out.println("totNumSrc="+totNumSrc+"\tnumFltSystSources="+numFltSystSources+
				"\tnumPointsForRates="+numCubes);
				
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
				
		origGridSeisTrulyOffVsSubSeisStatus = getOrigGridSeisTrulyOffVsSubSeisStatus();
		
		// read or make cache data if needed
		if(inputSectDistForCubeList!=null && inputSectInCubeList != null && inputIsCubeInsideFaultPolygon != null) {
				sectInCubeList = inputSectInCubeList;
				sectDistForCubeList = inputSectDistForCubeList;
				isCubeInsideFaultPolygon = inputIsCubeInsideFaultPolygon;
		}
		else {
			File sectInCubeCacheFilename = new File(defaultSectInCubeCacheFilename);
			File sectDistForCubeCacheFilename = new File(defaultSectDistForCubeCacheFilename);	
			File cubeInsidePolyCacheFilename = new File(defaultCubeInsidePolyCacheFilename);	
			if (sectInCubeCacheFilename.exists() && sectDistForCubeCacheFilename.exists() && cubeInsidePolyCacheFilename.exists()) { // read from file if it exists
				try {
					if(D) ETAS_SimAnalysisTools.writeMemoryUse("Memory before reading "+sectDistForCubeCacheFilename);
					sectDistForCubeList = MatrixIO.floatArraysListFromFile(sectDistForCubeCacheFilename);
					if(D) ETAS_SimAnalysisTools.writeMemoryUse("Memory before reading "+sectInCubeCacheFilename);
					sectInCubeList = MatrixIO.intArraysListFromFile(sectInCubeCacheFilename);
					if(D) ETAS_SimAnalysisTools.writeMemoryUse("Memory before reading "+cubeInsidePolyCacheFilename);
					isCubeInsideFaultPolygon = MatrixIO.intArrayFromFile(cubeInsidePolyCacheFilename);
					if(D) ETAS_SimAnalysisTools.writeMemoryUse("Memory after reading isCubeInsideFaultPolygon");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			else {  // make cache file if they don't exist
				if(D) ETAS_SimAnalysisTools.writeMemoryUse("Memory before running generateAndWriteCacheDataToFiles()");
				generateAndWriteCacheDataToFiles();
				if(D) ETAS_SimAnalysisTools.writeMemoryUse("Memory after running generateAndWriteCacheDataToFiles()");
				System.gc();
				// now read the data from files (because the generate method above does not set these)
				try {
					sectDistForCubeList = MatrixIO.floatArraysListFromFile(sectDistForCubeCacheFilename);
					sectInCubeList = MatrixIO.intArraysListFromFile(sectInCubeCacheFilename);
					isCubeInsideFaultPolygon = MatrixIO.intArrayFromFile(cubeInsidePolyCacheFilename);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		// make fractionSectInCubeList
		// first make lists of cubes and dists for each section
		if(D) System.out.println("Starting to make fractionSectInCubeList");
		long startTime= System.currentTimeMillis();
		ArrayList<ArrayList<Integer>> cubesForSectionList = new ArrayList<ArrayList<Integer>>();
		ArrayList<ArrayList<Float>> cubeDistsForSectionList = new ArrayList<ArrayList<Float>>();
		for(int s=0;s<rupSet.getNumSections();s++) {
			cubesForSectionList.add(new ArrayList<Integer>());
			cubeDistsForSectionList.add(new ArrayList<Float>());
		}
		for(int c=0; c<numCubes;c++) {
			int[] sectInCubeArray=sectInCubeList.get(c);
			float[] sectDistForCubeArray = sectDistForCubeList.get(c);
			for(int i=0;i<sectInCubeArray.length;i++) {
				int s = sectInCubeArray[i];
				float dist = sectDistForCubeArray[i];
				cubesForSectionList.get(s).add(c);
				cubeDistsForSectionList.get(s).add(dist);
			}
		}	
		
		// TODO already called above when making GR corr values
		makeLongTermSectMFDs();
		
		// make temporary list of fraction hash maps
		ArrayList<HashMap<Integer,Float>> hashMapForSectList = new ArrayList<HashMap<Integer,Float>>();
		for(int s=0;s<rupSet.getNumSections();s++) {
			hashMapForSectList.add(getCubesAndFractForFaultSection_BoatRamp(s, cubesForSectionList, cubeDistsForSectionList));
		}
		fractionSectInCubeList = new ArrayList<float[]>();
		for(int c=0; c<numCubes;c++) {
			int[] sectInCubeArray = sectInCubeList.get(c);
			float[] fracsForCubeArray = new float[sectInCubeArray.length];
			for(int i=0;i<sectInCubeArray.length;i++) {
				int s=sectInCubeArray[i];
				fracsForCubeArray[i]=hashMapForSectList.get(s).get(c);
			}
			fractionSectInCubeList.add(fracsForCubeArray);
		}
		double runtime = ((double)(System.currentTimeMillis()-startTime))/1000;
		if(D) System.out.println("fractionSectInCubeList took (sec): "+runtime);

		
		
		// Compute numCubesInsideFaultPolygonArray 
		if(D) System.out.println("Starting to build numCubesInsideFaultPolygonArray");
		startTime= System.currentTimeMillis();
		numCubesInsideFaultPolygonArray = new int[rupSet.getNumSections()];
		for(int s=0;s<rupSet.getNumSections();s++) {
			numCubesInsideFaultPolygonArray[s] = cubesForSectionList.get(s).size();
		}
		runtime = ((double)(System.currentTimeMillis()-startTime))/1000;
		if(D) System.out.println("numCubesInsideFaultPolygonArray took (sec): "+runtime);
		
		
		if(erf instanceof FaultSystemSolutionERF) {
			// create the arrays that will store section nucleation info
			totSectNuclRateArray = new double[rupSet.getNumSections()];
			// this is a hashmap for each section, which contains the source index (key) and nucleation rate (value)
			srcNuclRateOnSectList = new ArrayList<HashMap<Integer,Float>>();
			for(int sect=0;sect<rupSet.getNumSections();sect++) {
				srcNuclRateOnSectList.add(new HashMap<Integer,Float>());
			}
			for(int src=0; src<numFltSystSources; src++) {
				int fltSysRupIndex = fssERF.getFltSysRupIndexForSource(src);
				List<Integer> sectIndexList = rupSet.getSectionsIndicesForRup(fltSysRupIndex);
				for(int sect:sectIndexList) {
					srcNuclRateOnSectList.get(sect).put(src,0f);
				}
			}	
			
			// now compute initial values for these arrays (nucleation rates of sections given norm time since last and any GR corr)
			computeSectNucleationRates();
			System.gc();	// garbage collect
			if (D) ETAS_SimAnalysisTools.writeMemoryUse("Memory after making data");
		}
		
		if(D) ETAS_SimAnalysisTools.writeMemoryUse("Memory before computeTotSectRateInCubesArray()");
		computeTotSectRateInCubesArray();
		if(D) ETAS_SimAnalysisTools.writeMemoryUse("Memory after computeTotSectRateInCubesArray()");
		
		// TODO Not necessary needed, and will trigger recompute int declareRateChange?
		computeMFD_ForSrcArrays(2.05, 8.95, 70);
		
		startTime= System.currentTimeMillis();
		if(D) System.out.println("Starting getCubeSamplerWithERF_RatesOnly()");
		getCubeSamplerWithERF_GriddedRatesOnly();
		runtime = ((double)(System.currentTimeMillis()-startTime))/1000;
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
	
	
	/**
	 * This computes/updates the section nucleation rates (srcNuclRateOnSectList and totSectNuclRateArray), 
	 * where the time-dependent fss source rates are mapped onto different sections in proportion to the 
	 * normalized time since last event (the latter being 1.0 if unknown).  This also applies the GR
	 * correction and MaxCharFactor to each section.
	 */
	private void computeSectNucleationRates() {
		
		long st= System.currentTimeMillis();
		
		for(int s=0;s<totSectNuclRateArray.length;s++) {	// intialized totals to zero
			totSectNuclRateArray[s] = 0d;
		}
		//
		double[] sectNormTimeSince = fssERF.getNormTimeSinceLastForSections();
			
		for(int src=0; src<numFltSystSources; src++) {
			int fltSysRupIndex = fssERF.getFltSysRupIndexForSource(src);
			List<Integer> sectIndexList = rupSet.getSectionsIndicesForRup(fltSysRupIndex);
			
			// Needed if weighting by susbseis rates
			int numSubRates=0;
			double aveSubRates=0;	// this will be used where there are no subseis ruptures
			for(int sect:sectIndexList) {
				if(totLongTermSubSeisRateOnSectArray[sect]>0) {
					numSubRates+=1;
					aveSubRates+=totLongTermSubSeisRateOnSectArray[sect];
				}
			}
			if(aveSubRates==0)	// all were outside relm region; give all the same weight
				aveSubRates=1;
			else
				aveSubRates /= numSubRates;
			
			
			double[] relSectNuclRateArray = new double[sectIndexList.size()];	
			double sum=0;
			for(int s=0;s<relSectNuclRateArray.length;s++) {
				int sectIndex = sectIndexList.get(s);
				double sectWt1;
				
				if(wtSupraNuclBySubSeisRates) {
					// WEIGHT BY SUBSEIS RATE
					if(totLongTermSubSeisRateOnSectArray[sectIndex] != 0)
						sectWt1 = totLongTermSubSeisRateOnSectArray[sectIndex];
					else
						sectWt1 = aveSubRates;
					
				}
				else {
					// WEIGHT BY AREA
					sectWt1 = rupSet.getAreaForSection(sectIndex);
				}
				
				
				
				double normTS=Double.NaN;
				if(sectNormTimeSince!=null)
					normTS = sectNormTimeSince[sectIndex];
				if(Double.isNaN(normTS)) 
					relSectNuclRateArray[s] = 1.0*sectWt1;	// assume it's 1.0 if value unavailable
				else {
					if(APPLY_ERT_FAULTS)
						relSectNuclRateArray[s]=normTS*sectWt1;
					else
						relSectNuclRateArray[s]=1.0*sectWt1;	// test
				}
				sum += relSectNuclRateArray[s];	// this will be used to avoid dividing by zero later
			}
			for(int s=0;s<relSectNuclRateArray.length;s++) {
				int sectIndex = sectIndexList.get(s);
				double sectNuclRate;
				if(sum>0) {
					sectNuclRate = grCorrFactorForSectArray[sectIndex]*relSectNuclRateArray[s]*sourceRates[src]/sum;
//System.out.println("RIGHTHERE: "+grCorrFactorForSectArray[sectIndex]);
				}
				else {
					sectNuclRate = 0d;
				}
				srcNuclRateOnSectList.get(sectIndex).put(src, (float)sectNuclRate);
				totSectNuclRateArray[sectIndex] += sectNuclRate;
				
//double tempTest = (float)sectNuclRate;
//	if(tempTest == 0) {
//		System.out.println("TEST HERE: "+sectIndex+"\t"+sum+"\t"+normTimeSinceOnSectArray[s]+"\t"+grCorrFactorForSectArray[sectIndex]
//				+"\t"+sourceRates[src]+"\t"+tempTest+"\t"+sectNuclRate+"\t"+erf.getSource(src).getName());
//	}
			}
		}
		
		// TESTS TODO do this only in debug mode?
		for(int sect=0;sect<rupSet.getNumSections(); sect++) {
			double testTotRate = 0;
			for(float vals:srcNuclRateOnSectList.get(sect).values()) 
				testTotRate += vals;
			double ratio = testTotRate/totSectNuclRateArray[sect];
			if(ratio<0.9999 || ratio>1.0001) {
				throw new RuntimeException("Test failed in computeSectNucleationRates(); ratio ="+ratio+" for sect "+sect);
			}
		}
		// test that nucleation rates give back source rates
		double[] testSrcRates = new double[numFltSystSources];
		for(int sectIndex=0;sectIndex<rupSet.getNumSections();sectIndex++) {
			HashMap<Integer,Float> map = srcNuclRateOnSectList.get(sectIndex);
			for(int srcIndex:map.keySet())
				testSrcRates[srcIndex] += map.get(srcIndex)/(grCorrFactorForSectArray[sectIndex]);
		}
		for(int srcIndex=0;srcIndex<this.numFltSystSources;srcIndex++) {
			double testRatio = testSrcRates[srcIndex]/sourceRates[srcIndex];
			if(testRatio<0.9999 || testRatio>1.0001) {
				throw new RuntimeException("Source rate test failed in computeSectNucleationRates(); testRatio ="+
			testRatio+" for srcIndex "+srcIndex+"\ntestSrcRates="+testSrcRates[srcIndex]+"\nsourceRates="+sourceRates[srcIndex]);
			}
		}
		
		if(D) {
			double timeSec = (double)(System.currentTimeMillis()-st)/1000d;
			System.out.println("computeSectNucleationRates runtime(sec) = "+timeSec);			
		}
		


	}
	
	
	
	
	/**
	 * This computes the total rate of all sections that nucleate inside each cube
	 */
	public void computeTotSectRateInCubesArray() {

		long st = System.currentTimeMillis();
		totalSectRateInCubeArray = new double[numCubes];
		for(int c=0;c<numCubes;c++) {
			int[] sectInCubeArray = sectInCubeList.get(c);
			float[] fracts = fractionSectInCubeList.get(c);
			for(int s=0; s<sectInCubeArray.length;s++) {
				totalSectRateInCubeArray[c] += totSectNuclRateArray[sectInCubeArray[s]]*(double)fracts[s];
			}
		}
		
		if(D) {
			double timeSec = (double)(System.currentTimeMillis()-st)/1000d;
			System.out.println("tempSectRateInCubeArray runtime(sec) = "+timeSec);	
		}

	}
	
	
	public void setSectInCubeCaches(List<float[]> sectDistForCubeList, List<int[]> sectInCubeList) {
		this.sectDistForCubeList = sectDistForCubeList;
		this.sectInCubeList = sectInCubeList;
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
	 * This returns a map giving the cubes that are inside the fault-section's polygon (cube index is map key),
	 * plus the distance (map value) of each cube center from the fault-section surface.
	 * @param sectionIndex
	 * @return
	 */
	private HashMap<Integer,Double> getCubesAndDistancesInsideSectionPolygon(int sectionIndex) {
		HashMap<Integer,Double> cubeDistMap = new HashMap<Integer,Double>();
		Region faultPolygon = faultPolyMgr.getPoly(sectionIndex);
		StirlingGriddedSurface surface = rupSet.getFaultSectionData(sectionIndex).getStirlingGriddedSurface(0.25, false, true);
		for(int i=0; i<numCubes;i++) {
			Location cubeLoc = getCubeLocationForIndex(i);
			if(faultPolygon.contains(cubeLoc)) {
				double dist = LocationUtils.distanceToSurf(cubeLoc, surface);
				cubeDistMap.put(i, dist);
			}
		}
		return cubeDistMap;
	}
	
	/**
	 * This returns a list of sections (indices) who's surfaces cross the given cube, plus the fraction
	 * of the surface that is in the cube.  This ignores the section polygon.
	 * @param cubeIndex
	 * @return
	 */
	private HashMap<Integer,Double> getSectionsAndTheirFractsCrossingCube(int cubeIndex) {
		HashMap<Integer,Double> sectFracMap = new HashMap<Integer,Double>();
		Location cubeLoc = getCubeLocationForIndex(cubeIndex);
		for(int s=0;s<rupSet.getNumSections();s++) {
			double dist = LocationUtils.linearDistanceFast(cubeLoc, rupSet.getFaultSectionData(s).getFaultTrace().get(0));
			if(dist>32)	// section too far away
				continue;
			StirlingGriddedSurface surface = rupSet.getFaultSectionData(s).getStirlingGriddedSurface(0.25, false, true);
			double wt = 1.0/(surface.getNumCols()*surface.getNumRows());
			ListIterator<Location> iterator = surface.getLocationsIterator();
			while(iterator.hasNext()) {
				if(this.getCubeIndexForLocation(iterator.next()) == cubeIndex) {
					if(!sectFracMap.containsKey(s))
						sectFracMap.put(s, 0.0);
					double newWt = wt + sectFracMap.get(s);
					sectFracMap.put(s, newWt);
				}
			}
		}
		return sectFracMap;
	}

	
	
	/**
	 * This returns the number of cubes that are inside the polygon of the specified fault section
	 * @param sectionIndex
	 * @return
	 */
	private int getNumCubesInsideSectionPolygon(int sectionIndex) {
		int numCubesInside=0;
		Region faultPolygon = faultPolyMgr.getPoly(sectionIndex);
		for(int i=0;i<numCubesPerDepth;i++) {
			Location cubeLoc = gridRegForCubes.getLocation(i);
			if(faultPolygon.contains(cubeLoc)) {
				numCubesInside+=numCubeDepths;;
			}
		}
		return numCubesInside;
	}

	
	
	
	// 
	private double getFarthestCubeDistAtDepthForSection(StirlingGriddedSurface surface, double depthKm, ArrayList<Integer> cubeList) {
		
		// get the surface and find the row index corresponding to depthKm
		double min = Double.MAX_VALUE;
		int rowIndex=-1;
		for(int r=0;r<surface.getNumRows();r++) {
			double diff = Math.abs(surface.get(r,0).getDepth()-depthKm);
			if(diff < min) {
				min = diff;
				rowIndex = r;
			}
		}
		// get the cube depth index for depthKm 
		int cubeDepthIndex = getCubeDepthIndex(depthKm);
		
		// now find the distance of farthest cube at depthKm perpendicular to a line on the surface at depthKm
		double maxDist = -1;
		for(int cubeIndex:cubeList) {
			Location cubeLoc = getCubeLocationForIndex(cubeIndex);
			if(this.getCubeDepthIndex(cubeLoc.getDepth()) != cubeDepthIndex)
				continue;	// skip those that aren't at depthKm
			// find the mind dist to the line on the surface 
			double minDist = Double.MAX_VALUE;
			for(int c=0; c<surface.getNumCols();c++) {
				Location surfLoc= surface.getLocation(rowIndex, c);
				double dist = LocationUtils.linearDistanceFast(cubeLoc, surfLoc);
				if(dist<minDist)
					minDist = dist;
			}
			// is this the farthest yet found?
			if(maxDist<minDist)
				maxDist=minDist;
		}
		return maxDist;
	}
	
	/**
	 * This computes the half width of the fault-section polygon (perpendicular to the strike) in a
	 * somewhat weird way.
	 * 
	 * TODO Remove?
	 * 
	 * @param sectionIndex
	 * @param cubeList
	 * @return
	 */
	private double getFaultSectionPolygonHalfWidth(int sectionIndex, Set<Integer> cubeList) {
		
		// get the surface and find the row index corresponding to depthKm
		StirlingGriddedSurface surface = rupSet.getFaultSectionData(sectionIndex).getStirlingGriddedSurface(1.0, false, false);
		
//System.out.println("Surface Dip: "+surface.getAveDip());
//System.out.println("Surface:");
//for(int r=0;r<surface.getNumRows();r++) {
//	for(int c=0;c<surface.getNumCols();c++) {
//		Location loc = surface.get(r, c);
//		System.out.println(loc.getLongitude()+"\t"+loc.getLatitude()+"\t"+loc.getDepth());
//	}
//}
//Region reg = faultPolyMgr.getPoly(sectionIndex);
//System.out.println("Polygon:");
//for(Location loc:reg.getBorder()) {
//	System.out.println(loc.getLongitude()+"\t"+loc.getLatitude()+"\t"+loc.getDepth());
//}

		int rowIndexHalfWayDown = surface.getNumRows()/2;
		double depthHalfWayDown = surface.getLocation(rowIndexHalfWayDown,0).getDepth();
		// get the cube depth index for depthKm 
		int cubeDepthIndex = getCubeDepthIndex(depthHalfWayDown);
		
		// now find the distance of farthest cube at depthKm perpendicular to a line on the surface at depthKm
		double maxDist = -1;
		for(int cubeIndex:cubeList) {
			Location cubeLoc = getCubeLocationForIndex(cubeIndex);
			if(this.getCubeDepthIndex(cubeLoc.getDepth()) != cubeDepthIndex)
				continue;	// skip those that aren't at depthKm
			// find the min dist to the line on the surface 
			double minDist = Double.MAX_VALUE;
			for(int c=0; c<surface.getNumCols();c++) {
				Location surfLoc= surface.getLocation(rowIndexHalfWayDown, c);
				double dist = LocationUtils.linearDistanceFast(cubeLoc, surfLoc);
				if(dist<minDist)
					minDist = dist;
			}
			// is this the farthest yet found?
			if(maxDist<minDist)
				maxDist=minDist;
		}
// System.out.println(sectionIndex+"\t"+maxDist+"\t"+this.fssERF.getSolution().getRupSet().getFaultSectionData(sectionIndex).getName());
		return maxDist;
	}

	
	
	
	/**
	 * This shows that trace numbers are one more than sectiction numbers because the former starts at 1 for subsections
	 */
	public void tempSectTest() {
		for(int s=0;s<this.rupSet.getNumSections();s++)
			System.out.println(rupSet.getFaultSectionData(s).getName()+"\t"+rupSet.getFaultSectionData(s).getFaultTrace().getName());
	}
	
	

	/**
	 * This version ??????.
	 * 
	 * DistThreshold is hard coded at 10 km.
	 * @param sectionIndex
	 * @param cubesForSectionList
	 * @param cubeDistsForSectionList
	 * @return
	 */
	private HashMap<Integer,Float> getCubesAndFractForFaultSection_BoatRamp(int sectionIndex, ArrayList<ArrayList<Integer>> cubesForSectionList, 
			ArrayList<ArrayList<Float>> cubeDistsForSectionList) {
		
		double numCubes = (double)cubesForSectionList.get(sectionIndex).size();
		
		HashMap<Integer,Float> wtMap = new HashMap<Integer,Float>();
		
		if(applyGR_Corr || charFactorForSectArray[sectionIndex]<=1.0) {	// distribute evenly among cubes
			float wt = 1f/(float)numCubes;
			for(int i=0; i<cubesForSectionList.get(sectionIndex).size();i++) {
				int cubeIndex = cubesForSectionList.get(sectionIndex).get(i);
				wtMap.put(cubeIndex, wt);
			}
			return wtMap;
		}
		
		double charFactor = charFactorForSectArray[sectionIndex]*grCorrFactorForSectArray[sectionIndex];
		double distThresh = 10f; // where it goes from ramp to water level
		double totalRate = longTermSupraSeisMFD_OnSectArray[sectionIndex].getTotalIncrRate();
		double cubeRateBeyondDistThresh = totalRate/(charFactor*numCubes);
		double totalRateBeyondDistThresh = 0.0;
		double numCubesWithinDistThresh = 0.0;
		double sumDistWithinDistThresh = 0.0;
		double minDist=Double.MAX_VALUE;
		double maxDist=0.0;
		for(int i=0; i<cubesForSectionList.get(sectionIndex).size();i++) {
			double dist = cubeDistsForSectionList.get(sectionIndex).get(i);
			if(dist<=distThresh) {
				numCubesWithinDistThresh += 1.0;
				sumDistWithinDistThresh += dist;
			}
			else {
				totalRateBeyondDistThresh += cubeRateBeyondDistThresh;
			}
			if(minDist>dist) minDist=dist;
			if(maxDist<dist) maxDist=dist;
		}
		
		double totRateWithinDistThresh = totalRate - totalRateBeyondDistThresh;
		
		double slope = (totRateWithinDistThresh-numCubesWithinDistThresh*cubeRateBeyondDistThresh)/(sumDistWithinDistThresh-numCubesWithinDistThresh*distThresh);
		double intercept = cubeRateBeyondDistThresh-slope*distThresh;
		double minRate=Double.MAX_VALUE;
		double maxRate=0.0;
		float totWt=0;
		for(int i=0; i<cubesForSectionList.get(sectionIndex).size();i++) {
			int cubeIndex = cubesForSectionList.get(sectionIndex).get(i);
			double dist = cubeDistsForSectionList.get(sectionIndex).get(i);
			double rate;
			if(dist<=distThresh)
				rate = (slope*dist+intercept);
			else
				rate = cubeRateBeyondDistThresh;
			
			if(minRate>rate)
				minRate=rate;
			if(maxRate<rate)
				maxRate=rate;

//if(sectionIndex==1841) {
//	if(i==0)
//		System.out.println("Test for "+rupSet.getFaultSectionData(sectionIndex).getName()+"\tcharFactor="+charFactor+"\ttotalRate="+totalRate);
//	System.out.println(dist+"\t"+rate+"\t"+rate/cubeRateBeyondDistThresh);
//}
			double wt = rate/totalRate;
			wtMap.put(cubeIndex, (float)wt);
			totWt+=wt;
		}
		
double minCharFactor = minRate/cubeRateBeyondDistThresh;
double maxCharFactor = maxRate/cubeRateBeyondDistThresh;

// this will print out min and max values (e.g., to quote in the paper)
// System.out.println(sectionIndex+"\t"+charFactor+"\t"+maxCharFactor+"\t"+(maxCharFactor/charFactor)+"\t"+minCharFactor+"\t"+rupSet.getFaultSectionData(sectionIndex).getName()+"\t"+totalRate);

		if(totWt<0.9999 || totWt>1.0001) {
			System.out.println("getCubesAndFractForFaultSection_BoatRamp returned null for: "+sectionIndex+"\t"+rupSet.getFaultSectionData(sectionIndex).getName()+"\tcharFactor="+charFactor
					+"\ttotalRate="+totalRate+"\ttotWt="+totWt);
			return null;
//			throw new RuntimeException("Problem)");
		}
		
//System.out.println(sectionIndex+"\t"+rupSet.getFaultSectionData(sectionIndex).getName()+"\t"+wtMap.size()+
//"\t"+distThresh+"\t"+slope+"\t"+rupSet.getFaultSectionData(sectionIndex).getAveDip()+"\t"+lowerSeisDepth+"\t"+upperSeisDepth+"\t"+gotOne+
//"\t"+minDist+"\t"+maxDist+"\t"+minWt+"\t"+maxWt+"\t"+(maxWt/minWt));

		return wtMap;
	}


	
	
	
	
	
	/**
	 * This generates the following cached data and writes them to files:
	 * 
	 * sections in cubes saved to defaultSectInCubeCacheFilename
	 * fractions of sections in cubes saved to defaultFractSectInCubeCacheFilename
	 * whether cube center is inside one or more fault-section polygons is saved to defaultCubeInsidePolyCacheFilename
	 * 
	 */
	private void generateAndWriteCacheDataToFiles() {
		if(D) System.out.println("Starting ETAS.ETAS_PrimaryEventSampler.generateAndWriteListListDataToFile(); THIS WILL TAKE TIME AND MEMORY!");
		long st = System.currentTimeMillis();
		CalcProgressBar progressBar = null;
		try {
			progressBar = new CalcProgressBar("Sections to process in generateAndWriteCacheDataToFiles()", "junk");
		} catch (Exception e1) {} // headless
		ArrayList<ArrayList<Integer>> sectAtPointList = new ArrayList<ArrayList<Integer>>();
		ArrayList<ArrayList<Float>> sectDistToPointList = new ArrayList<ArrayList<Float>>();

		int numSect = rupSet.getNumSections();
		for(int i=0; i<numCubes;i++) {
			sectAtPointList.add(new ArrayList<Integer>());
			sectDistToPointList.add(new ArrayList<Float>());
		}
		
		if (progressBar != null) progressBar.showProgress(true);
		for(int s=0;s<numSect;s++) {
			if (progressBar != null) progressBar.updateProgress(s, numSect);
			
			HashMap<Integer,Double> cubeDistMap = getCubesAndDistancesInsideSectionPolygon(s);
			if(cubeDistMap != null) {	// null for some Mendocino sections because they are outside the RELM region
				for(int cubeIndex:cubeDistMap.keySet()) {
					sectAtPointList.get(cubeIndex).add(s);
					sectDistToPointList.get(cubeIndex).add(new Float(cubeDistMap.get(cubeIndex)));
				}			
			}
		}

		
		ETAS_SimAnalysisTools.writeMemoryUse("Memory before writing files");
		File intListListFile = new File(defaultSectInCubeCacheFilename);
		File floatListListFile = new File(defaultSectDistForCubeCacheFilename);
		try {
			MatrixIO.intListListToFile(sectAtPointList,intListListFile);
			MatrixIO.floatListListToFile(sectDistToPointList, floatListListFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
//System.exit(0);

		// Now compute which cubes are inside polygons
		if (D) System.out.println("Starting on insidePoly calculation");
		int numCubes = gridRegForCubes.getNodeCount();
		int[] insidePoly = new int[numCubes];	// default values are really 0
		
		if(erf instanceof FaultSystemSolutionERF) {	// otherwise all our outside polygons, and default values of 0 are appropriate
						
			GridSourceProvider gridSrcProvider = ((FaultSystemSolutionERF)erf).getSolution().getGridSourceProvider();
			int numBad = 0;
			for(int c=0;c<numCubes;c++) {
				if (progressBar != null) progressBar.updateProgress(c, numCubes);
				Location loc = getCubeLocationForIndex(c);
				int gridIndex = gridSrcProvider.getGriddedRegion().indexForLocation(loc);
				if(gridIndex == -1)
					numBad += 1;
				else {
					if(origGridSeisTrulyOffVsSubSeisStatus[gridIndex] == 0)
						insidePoly[c]=0;
					else if (origGridSeisTrulyOffVsSubSeisStatus[gridIndex] == 1)
						insidePoly[c]=1;

					else {	// check if loc is within any of the subsection polygons
						insidePoly[c]=0;
						for(int s=0; s< rupSet.getNumSections(); s++) {
							if(faultPolyMgr.getPoly(s).contains(loc)) {
								insidePoly[c] = 1;
								break;
							}
						}
					}				
				}
			}
			
			int numCubesInside = 0;
			for(int c=0;c<numCubes;c++) {
				if(insidePoly[c] == 1)
					numCubesInside += 1;
			}
			if(D) {
				System.out.println(numCubesInside+" are inside polygons, out of "+numCubes);
				System.out.println(numBad+" were bad");
			}
		}
		
		if (progressBar != null) progressBar.showProgress(false);
		
		File cubeInsidePolyFile = new File(defaultCubeInsidePolyCacheFilename);
		try {
			MatrixIO.intArrayToFile(insidePoly,cubeInsidePolyFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// test by reading file and comparing
		try {
			int[] insidePoly2 = MatrixIO.intArrayFromFile(cubeInsidePolyFile);
			boolean ok = true;
			for(int i=0;i<insidePoly2.length;i++)
				if(insidePoly[i] != insidePoly2[i])
					ok = false;
			if(!ok) {
				throw new RuntimeException("Problem with file");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		if(D) System.out.println("ETAS_PrimaryEventSampler.generateAndWriteListListDataToFile() took "+(System.currentTimeMillis()-st)/60000+ " min");

	}
	
	
	/**
	 * For each original gridded seismicity cell, this tells: 
	 * 
	 * 0 the entire grid cell is truly off fault (outside fault polygons)
	 * 1 the entire grid cell is subseismogenic (within fault polygons)
	 * 2 a mix of the two types
	 * 
	 * Note that results here may be inconsistent with the mapping to cubes here because contributions to the latter
	 * are based on cube center locations
	 * @return
	 */
	private int[] getOrigGridSeisTrulyOffVsSubSeisStatus() {
		GridSourceProvider gridSrcProvider = ((FaultSystemSolutionERF)erf).getSolution().getGridSourceProvider();
//		InversionFaultSystemRupSet rupSet = (InversionFaultSystemRupSet)((FaultSystemSolutionERF)erf).getSolution().getRupSet();
//		FaultPolyMgr faultPolyMgr = rupSet.getInversionTargetMFDs().getGridSeisUtils().getPolyMgr();
		int numGridLocs = gridSrcProvider.getGriddedRegion().getNodeCount();
		int[] gridSeisStatus = new int[numGridLocs];
		
		int num0=0,num1=0,num2=0;
		for(int i=0;i<numGridLocs; i++) {
			IncrementalMagFreqDist subSeisMFD = gridSrcProvider.getNodeSubSeisMFD(i);
			IncrementalMagFreqDist trulyOffMFD = gridSrcProvider.getNodeUnassociatedMFD(i);
			double frac = faultPolyMgr.getNodeFraction(i);
			if(subSeisMFD == null && trulyOffMFD != null) {
				gridSeisStatus[i] = 0;	// no cubes are inside; all are truly off
				num0 += 1;
				if(frac > 1e-10)	// should be 0.0
					throw new RuntimeException("Problem: frac > 1e-10");
			}
			else if (subSeisMFD != null && trulyOffMFD == null) {
				gridSeisStatus[i] = 1;	// all cubes are inside; all are subseimo
				num1 += 1;
				if(frac < 1.0 -1e-10)	// should be 1.0
					throw new RuntimeException("Problem: frac < 1.0 -1e-10");

			}
			else if (subSeisMFD != null && trulyOffMFD != null) {
				gridSeisStatus[i] = 2;	// some cubes are inside
				num2 += 1;
				if(frac ==0 || frac == 1) {
					System.out.println("Location:\t"+origGriddedRegion.getLocation(i));
					System.out.println("subSeisMFD:\n"+subSeisMFD.toString());
					System.out.println("trulyOffMFD:\n"+trulyOffMFD.toString());
					throw new RuntimeException("Problem: frac ==0 || frac == 1; "+frac);
				}
			}
			else {
				throw new RuntimeException("Problem");
			}
			
//if(i == 258864-numFltSystSources) {
//	System.out.println("HERE IT IS "+i+"\t"+gridSeisStatus[i]+"\t"+gridSrcProvider.getGriddedRegion().getLocation(i)+
//			"\tfrac="+frac+"\t(subSeisMFD == null)="+(subSeisMFD == null)+"\t(trulyOffMFD == null)="+(trulyOffMFD == null));
//	System.out.println("HERE IT IS faultPolyMgr.getNodeFraction(i)="+faultPolyMgr.getNodeFraction(i));
//	faultPolyMgr.getScaledNodeFractions(sectIdx)
//}
			
		}
		if(D) {
			System.out.println(num0+"\t (num0) out of\t"+numGridLocs);
			System.out.println(num1+"\t (num1) out of\t"+numGridLocs);
			System.out.println(num2+"\t (num2) out of\t"+numGridLocs);
		}
		return gridSeisStatus;
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
				RuptureSurface surf = etas_utils.getRuptureSurfaceWithNoCreepReduction(mainshock.getFSSIndex(), fssERF, 1.0);
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
	 * TODO No longer used?
	 * @param mainshock
	 */
	public void tempAveSamplerAtFaults(ETAS_EqkRupture mainshock) {
		IntegerPDF_FunctionSampler aveSampler = getAveSamplerForRupture(mainshock);
		
		double fltNuclRate[] = new double[numCubes];
		double totCubeProb[] = new double[numCubes];
		
		for(int c=0;c<numCubes;c++) {
			fltNuclRate[c] = 0d;
			if(sectInCubeList.get(c).length > 0) {
				int[] sectInCube = sectInCubeList.get(c);
				float[] fractInCube = fractionSectInCubeList.get(c);				
				for(int s=0; s<sectInCube.length;s++)
					fltNuclRate[c] += totSectNuclRateArray[sectInCube[s]]*fractInCube[s];
			}
			totCubeProb[c] = fltNuclRate[c]*aveSampler.getY(c);
		}
		
		
		// get top-prob cubes
		int[] topCubeIndices = ETAS_SimAnalysisTools.getIndicesForHighestValuesInArray(totCubeProb, 50);
		System.out.print("cubeIndex\ttotFltProb\tcubeProb\tgrdSeisRate\tfltNuclRate\tlat\tlon\tdepth\tsect data...");
		for(int cubeIndex : topCubeIndices) {
			double gridSeisRateInCube = cubeSamplerGriddedRatesOnly.getY(cubeIndex);
			Location loc = getCubeLocationForIndex(cubeIndex);
			int[] sectInCube = sectInCubeList.get(cubeIndex);
			float[] fractInCube = fractionSectInCubeList.get(cubeIndex);
			System.out.print(cubeIndex+"\t"+totCubeProb[cubeIndex]+"\t"+aveSampler.getY(cubeIndex)+"\t"+gridSeisRateInCube+"\t"+fltNuclRate[cubeIndex]+
					"\t"+loc.getLongitude()+"\t"+loc.getLatitude()+"\t"+loc.getDepth());
			
			List<FaultSectionPrefData> fltDataList = ((FaultSystemSolutionERF)erf).getSolution().getRupSet().getFaultSectionDataList();
			for(int s=0;s<sectInCube.length;s++) {
				int sectIndex = sectInCube[s];
				double sectRate = totSectNuclRateArray[sectIndex]*fractInCube[s];
				System.out.print("\t"+fltDataList.get(sectIndex).getName()+"\t"+totSectNuclRateArray[sectIndex]+"\t"+fractInCube[s]);
			}
			System.out.print("\n");
		}
		
		System.out.print(getCubeMFD(426462).toString());
		Location loc = getCubeLocationForIndex(426462);
		Location newLoc = new Location(loc.getLatitude()+0.04,loc.getLongitude()-0.04,loc.getDepth());
		System.out.print(newLoc.toString());
		System.out.print(getCubeMFD(getCubeIndexForLocation(newLoc)).toString());

		
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
			gridSrcIndex = numFltSystSources + griddeSeisRegionIndex;
			// OLD WAY:
//			gridSrcRate = sourceRates[gridSrcIndex]/(numPtSrcSubPts*numPtSrcSubPts*numCubeDepths);	// divide rate among all the cubes in grid cell

			// this assumes the following does not include supra, and that it has not been renormalized
			gridSrcRate = cubeSamplerGriddedRatesOnly.getY(cubeIndex);	
		}
		
		int[] sectInCubeArray = sectInCubeList.get(cubeIndex);
		
		if(gridSrcIndex == -1 && sectInCubeArray.length==0) {
			return null;
		}
		
		if(gridSrcIndex != -1 && (sectInCubeArray.length==0 || fracSupra==0.0)) {
			rateForSrcHashtable.put(gridSrcIndex, gridSrcRate);	// only gridded source in this cube
			return rateForSrcHashtable;
		}
		
		if(gridSrcIndex != -1) 
			rateForSrcHashtable.put(gridSrcIndex, gridSrcRate);	// add gridded source rate
		
		// now fill in nucleation rate of remaining sources
		float[] fracts = fractionSectInCubeList.get(cubeIndex);
		for(int s=0;s<sectInCubeArray.length;s++) {
			int sectIndex = sectInCubeArray[s];
			double fracSectInCube = fracts[s];
			HashMap<Integer,Float> srcRateOnSectMap = srcNuclRateOnSectList.get(sectIndex);
			for(int srcIndex:srcRateOnSectMap.keySet()) {
				double srcNuclRateInCube = srcRateOnSectMap.get(srcIndex)*fracSectInCube*fracSupra;
				if(rateForSrcHashtable.containsKey(srcIndex)) {
					double newRate = rateForSrcHashtable.get(srcIndex) + srcNuclRateInCube;
					rateForSrcHashtable.put(srcIndex, newRate);
				}
				else {
					rateForSrcHashtable.put(srcIndex, srcNuclRateInCube);
				}
			}
		}
		
		return rateForSrcHashtable;
	}
	
	
	
	/**
	 * This tests whether source rates can be recovered from the source rates in each 
	 * cube (getNucleationRatesOfSourcesInCube()).
	 */
	public void testNucleationRatesOfFaultSourcesInCubes() {
		if(D) System.out.println("testNucleationRatesOfFaultSourcesInCubes():");
		double[] testSrcRate = new double[numFltSystSources];
		System.out.println("\tloop over cubes...");
		CalcProgressBar progressBar = new CalcProgressBar("loop over cubes", "junk");
		progressBar.showProgress(true);
		for(int c=0;c<numCubes;c++) {
			progressBar.updateProgress(c, numCubes);
			Hashtable<Integer,Double> srcRatesInCube = getNucleationRatesOfSourcesInCube(c,1.0);
			if(srcRatesInCube != null) {
				for(int srcIndex:srcRatesInCube.keySet()) {
					if(srcIndex<numFltSystSources) {
						double rate = srcRatesInCube.get(srcIndex);
						if(!Double.isNaN(rate))
							testSrcRate[srcIndex] += rate;						
					}
				}				
			}
		}
		progressBar.showProgress(false);
		
		System.out.println("\tloop over sources...");
		progressBar = new CalcProgressBar("loop over sources", "junk");
		progressBar.showProgress(true);
		for(int srcIndex=0; srcIndex < numFltSystSources; srcIndex++) {
			progressBar.updateProgress(srcIndex, sourceRates.length);
			double fractDiff = Math.abs(testSrcRate[srcIndex]-sourceRates[srcIndex])/sourceRates[srcIndex];
			String name = this.fssERF.getSource(srcIndex).getName();
			if(fractDiff>0.0001 && !name.contains("Mendocino")) {
				int gridRegionIndex = srcIndex-numFltSystSources;
				if(gridRegionIndex>=0) {
					Location loc = this.origGriddedRegion.getLocation(gridRegionIndex);
					System.out.println("\tDiff="+(float)fractDiff+" for "+srcIndex+"; "+name+"\t"+loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth());			
				}
				else {
					System.out.println("\tDiff="+(float)fractDiff+" for "+srcIndex+"; "+this.fssERF.getSource(srcIndex).getName());			
				}
			}
		}
		progressBar.showProgress(false);
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
	 * @return ArrayList<SummedMagFreqDist>; index 0 has total MFD, and index 1 has supra-seis MFD,
	 * and index 2 has sub-seis MFD.
	 */
	public List<SummedMagFreqDist> getExpectedPrimaryMFD_PDF(double[] relSrcProbs) {
		
		long st = System.currentTimeMillis();

//		double[] relSrcProbs = getRelativeTriggerProbOfEachSource(sampler);
		SummedMagFreqDist magDist = new SummedMagFreqDist(2.05, 8.95, 70);
		SummedMagFreqDist supraMagDist = new SummedMagFreqDist(2.05, 8.95, 70);
		SummedMagFreqDist subSeisMagDist = new SummedMagFreqDist(2.05, 8.95, 70);
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
				if(s<numFltSystSources) {
					supraMagDist.addIncrementalMagFreqDist(srcMFD);
//					if(srcMFD.getMinMagWithNonZeroRate()<6)
//						System.out.println(srcMFD.getMinMagWithNonZeroRate()+"\t"+erf.getSource(s).getName());
				}
				else
					subSeisMagDist.addIncrementalMagFreqDist(srcMFD);
			}
		}
		ArrayList<SummedMagFreqDist> mfdList = new ArrayList<SummedMagFreqDist>();
		mfdList.add(magDist);
		mfdList.add(supraMagDist);
		mfdList.add(subSeisMagDist);

		if(D) {
			System.out.println("\ttestTotProb="+testTotProb);
			st = System.currentTimeMillis()-st;
			System.out.println("getExpectedPrimaryMFD_PDF took:"+((float)st/1000f)+" sec");			
		}
		
		return mfdList;
	}
	
	
	/**
	 * THIS DOES NOT INCLUDE ELASTIC REBOUND TRIGGERING FOR SMALL FAULTS
	 * @param mainshock
	 * @return ArrayList<SummedMagFreqDist>; index 0 has total MFD, and index 1 has supra-seis MFD
	 */
	public List<SummedMagFreqDist> getExpectedPrimaryMFD_PDF_Alt(IntegerPDF_FunctionSampler sampler, double frac) {
		// normalize so values sum to 1.0
		sampler.scale(1.0/sampler.getSumOfY_vals());
		
		List<Integer> list = sampler.getOrderedIndicesOfHighestXFract(frac);
		
		CalcProgressBar progressBar = null;
		if(D) {
			progressBar = new CalcProgressBar("getExpectedPrimaryMFD_PDF_Alt", "junk");
			progressBar.showProgress(true);
		}

		SummedMagFreqDist magDist = new SummedMagFreqDist(2.05, 8.95, 70);
		SummedMagFreqDist supraMagDist = new SummedMagFreqDist(2.05, 8.95, 70);
		SummedMagFreqDist subSeisMagDist = new SummedMagFreqDist(2.05, 8.95, 70);
		int count=0;
		for(int i:list) {
			if(D) {
				progressBar.updateProgress(count, list.size());
				count+=1;				
			}
			SummedMagFreqDist mfd = getCubeMFD(i);
			if(mfd != null) {
				double total = mfd.getTotalIncrRate();
				mfd.scale(sampler.getY(i)/total);
				magDist.addIncrementalMagFreqDist(mfd);
				SummedMagFreqDist mfdSupra = getCubeMFD_SupraSeisOnly(i);
				if(mfdSupra != null) {
					mfdSupra.scale(sampler.getY(i)/total);
					supraMagDist.addIncrementalMagFreqDist(mfdSupra);
				}
				SummedMagFreqDist mfdSub = getCubeMFD_GriddedSeisOnly(i);
				if(mfdSub != null) {
					mfdSub.scale(sampler.getY(i)/total);
					subSeisMagDist.addIncrementalMagFreqDist(mfdSub);
				}
				
			}
		}
		
		if(D)
			progressBar.showProgress(false);

		ArrayList<SummedMagFreqDist> mfdList = new ArrayList<SummedMagFreqDist>();
		mfdList.add(magDist);
		mfdList.add(supraMagDist);
		mfdList.add(subSeisMagDist);

		return mfdList;
	}
	
//	/**
//	 * This tells whether a parent rupture can trigger supra-seismogenic events at the given location.
//	 * The answer is true if the parent is itself supras-eismogenic (this case is handled using elastic
//	 * rebound) or if APPLY_ERT is false (POISSON or NO_ERT case).
//	 * It is also true if the parent mag is less than 4.0.  Otherwise the answer is false if the
//	 * distance between the parent and the trigger loc is less than sqrt(A/PI), where A is the area
//	 * defined as 10^(Mag-4.0).
//	 * @param parentRup
//	 * @param triggeredLoc
//	 * @return
//	 */
//	private boolean canSupraBeTriggered(ETAS_EqkRupture parentRup, Location triggeredLoc) {
//		if(parentRup.getFSSIndex()>=0 || !APPLY_ERT_GRIDDED)
//			return true;
//		boolean pointSurface = parentRup.getRuptureSurface() instanceof PointSurface;
//		if(!pointSurface)
//			throw new RuntimeException("non PointSurface case not yet supported");	// otherwise we need to know the point from which triggering occurrs?
//		double parMag = parentRup.getMag();
//		if(parMag<4.0)
//			return true;
//		double area = Math.pow(10d, parMag-4.0);
//		double distCutoff = Math.sqrt(area/Math.PI);
//		double dist = LocationUtils.linearDistanceFast(parentRup.getRuptureSurface().getFirstLocOnUpperEdge(), triggeredLoc);
//		if(dist<=distCutoff) {
//System.out.println("HERE canSupraBeTriggered=false for "+triggeredLoc+"\tfor M="+parentRup.getMag());
//			return false;
//		}
//		else {
//			return true;
//		}
//	}
//
	
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
	 * This plots one minus the probability that no primary aftershocks trigger each subsection, 
	 * given all expected primary aftershocks.  
	 * 
	 * This also returns a String with a list of the top numToList fault-based 
	 * sources and top sections (see next method below).
	 * 
	 * TODO move this to ETAS_SimAnalysisTools
	 */
	public String plotSubSectTriggerProbGivenAllPrimayEvents(IntegerPDF_FunctionSampler sampler, File resultsDir, int numToList, 
			String nameSuffix, double expNum, boolean isPoisson, ETAS_EqkRupture parentRup) {
		String info = "";
		if(erf instanceof FaultSystemSolutionERF) {
			FaultSystemSolutionERF tempERF = (FaultSystemSolutionERF)erf;

			// normalize so values sum to 1.0
			sampler.scale(1.0/sampler.getSumOfY_vals());

			double[] sectProbArray = new double[rupSet.getNumSections()];
			
			double totGridProb=0;

			// now loop over all cubes
			for(int i=0;i <numCubes;i++) {
				// skip if this cube cannot trigger supra-seis ruptures
				double fracSupra = getERT_MinFracSupra(parentRup, getCubeLocationForIndex(i));
				if(APPLY_ERT_GRIDDED && fracSupra==0.0) {
					continue;
				}
				
				int[] sectInCubeArray = sectInCubeList.get(i);
				
				
				// TEST for just ERT effect with no time dep probabilities
//				fracSupra=1.0;
//				List<FaultSectionPrefData> fltDataList = rupSet.getFaultSectionDataForRupture(parentRup.getFSSIndex());
//				for(FaultSectionPrefData fltData : fltDataList) {
//					for(int sectID:sectInCubeArray)
//						if(sectID == fltData.getSectionId()) {
//							fracSupra = 0.0;
//							break;
//						}
//				}
//				if(fracSupra == 0.0)
//					continue;
				
				

				float[] fractInCubeArray = fractionSectInCubeList.get(i);
				double sum = 0;
				for(int s=0;s<sectInCubeArray.length;s++) {
					int sectIndex = sectInCubeArray[s];
					sum += totSectNuclRateArray[sectIndex]*fractInCubeArray[s]*fracSupra;
				}
				double gridCubeRate=cubeSamplerGriddedRatesOnly.getY(i);
				sum += gridCubeRate;	// to make it the total nucleation rate in cube
				if(sum > 0) {	// avoid division by zero if all rates are zero
					totGridProb += sampler.getY(i)*gridCubeRate/sum;
					for(int s=0;s<sectInCubeArray.length;s++) {
						int sectIndex = sectInCubeArray[s];
						double val = totSectNuclRateArray[sectIndex]*fractInCubeArray[s]*fracSupra*sampler.getY(i)/sum;
						sectProbArray[sectIndex] += val;

// cubes for sections off ends of Mojave scenario						
//if(sectIndex==1836 || sectIndex==1846) {
//	Location loc = this.getCubeLocationForIndex(i);
//	System.out.println(i+"\t"+loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth()+"\t"+sampler.getY(i)+"\t"+totSectNuclRateArray[sectIndex]+"\t"+fractInCubeArray[s]
//			+"\t"+val+"\t"+sum+"\t"+getGridSourcRateInCube(i)+"\t"+rupSet.getFaultSectionData(sectIndex).getName());
//}
					}
				}
			}

			// normalize:
			double sum=0;
			for(double val:sectProbArray)
				sum += val;
			System.out.println("SUM TEST HERE (prob of flt rup given primary event): "+sum);
			System.out.println("SUM TEST HERE (totGridProb): "+totGridProb);
			System.out.println("SUM TEST HERE (total prob; should be 1.0): "+(totGridProb+sum));

			double min=Double.MAX_VALUE, max=0.0;
			for(int sect=0;sect<sectProbArray.length;sect++) {
				if(isPoisson)
					sectProbArray[sect] *= expNum;
				else
					sectProbArray[sect] = 1-Math.pow(1-sectProbArray[sect],expNum);
//				sectProbArray[sect] = 1-Math.pow(1-sectProbArray[sect]/sum,expectedNumSupra);
//				sectProbArray[sect] *= expectedNumSupra/sum;
				if(sectProbArray[sect]<1e-16) // to avoid log-space problems
					sectProbArray[sect]=1e-16;
			}
			
			min = -5;
			max = 0;
			CPT cpt = FaultBasedMapGen.getParticipationCPT().rescale(min, max);;
			List<FaultSectionPrefData> faults = rupSet.getFaultSectionDataList();

			//			// now log space
			double[] values = FaultBasedMapGen.log10(sectProbArray);


			String name = "SectOneOrMoreTriggerProb"+nameSuffix;
			String title = "Log10(Trigger Prob)";
			if(isPoisson) {
				name = "SectExpTriggerNum"+nameSuffix;
				title = "Log10(Exp Trigger Num)";				
			}
			// this writes all the value to a file
			try {
				FileWriter fr = new FileWriter(new File(resultsDir, name+".txt"));
				for(int s=0; s<sectProbArray.length;s++) {
					fr.write(s +"\t"+(float)sectProbArray[s]+"\t"+faults.get(s).getName()+"\n");
				}
				fr.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}


			try {
				FaultBasedMapGen.makeFaultPlot(cpt, FaultBasedMapGen.getTraces(faults), values, origGriddedRegion, resultsDir, name, true, false, title);
			} catch (Exception e) {
				e.printStackTrace();
			} 

			// list top sections
			int[] topValueIndices = ETAS_SimAnalysisTools.getIndicesForHighestValuesInArray(sectProbArray, numToList);
			info += "\nThe following sections are most likely to be triggered:\n\n";
			List<FaultSectionPrefData> fltDataList = tempERF.getSolution().getRupSet().getFaultSectionDataList();
			for(int sectIndex : topValueIndices) {
				info += "\t"+sectProbArray[sectIndex]+"\t"+sectIndex+"\t"+fltDataList.get(sectIndex).getName()+"\n";
			}
			
			return info;
		}
		else {
			throw new RuntimeException("erf must be instance of FaultSystemSolutionERF");
		}
	}
	
	
	
	public void writeGMT_PieSliceDecayData(Location parLoc, String fileNamePrefix) {
		
//		this.addParentLocToProcess(parLoc);
		
		double sliceLenghtDegrees = 2;
		
		try {
			FileWriter fileWriterGMT = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"GMT.txt"));
			FileWriter fileWriterSCECVDO = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"SCECVDO.txt"));
			CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
			
			int parLocIndex = getParLocIndexForLocation(parLoc);
			Location translatedParLoc = getParLocationForIndex(parLocIndex);
			float parLat = (float) translatedParLoc.getLatitude();
			float parLon = (float) translatedParLoc.getLongitude();
			IntegerPDF_FunctionSampler sampler = getCubeSampler(parLocIndex);
			// normalize
			sampler.scale(1.0/sampler.getSumOfY_vals());

			
//			// compute min and max
//			double minVal=Double.POSITIVE_INFINITY, maxVal=Double.NEGATIVE_INFINITY;
//			for(int i=0; i<sampler.size();i++) {
//				Location loc = this.getCubeLocationForIndex(i);
//				double latDiff = Math.abs(loc.getLatitude()-parLoc.getLatitude());
//				double lonDiff = Math.abs(loc.getLongitude()-parLoc.getLongitude());
//				double distDegrees = Math.sqrt(latDiff*latDiff+lonDiff*lonDiff);
//				if(distDegrees>sliceLenghtDegrees)
//					continue;
//				double val = Math.log10(sampler.getY(i));
//				if(val<-15)
//					continue;
//				if(minVal>val)
//					minVal=val;
//				if(maxVal<val)
//					maxVal=val;
//			}
//			
//			System.out.println("minVal="+minVal+"\tmaxVal="+maxVal);
//			System.out.println("Math.round(minVal)="+Math.round(minVal)+"\tMath.round(maxVal)="+Math.round(maxVal));
//	        cpt = cpt.rescale(Math.round(minVal+3), Math.round(maxVal));
	        
	        // hard coded:
	        cpt = cpt.rescale(-9.0, -1.0);
	        
	        cpt.writeCPTFile(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"_CPT.txt"));
			
			double halfCubeLatLon = cubeLatLonSpacing/2.0;
			double halfCubeDepth = depthDiscr/2.0;
			double startCubeLon = translatedParLoc.getLongitude() + halfCubeLatLon;
			double startCubeLat = translatedParLoc.getLatitude() + halfCubeLatLon;
			int numLatLon = (int)(sliceLenghtDegrees/cubeLatLonSpacing);	// 3 degrees in each direction

			// Data for squares on the EW trending vertical face
			double lat = startCubeLat;
			for(int i=0;i<numLatLon;i++) {
				double lon = startCubeLon + i*cubeLatLonSpacing;
				for(int d=0; d<numCubeDepths; d++) {
					double depth = getCubeDepth(d);
					Location cubeLoc = new Location(lat,lon,depth);
					double val = sampler.getY(this.getCubeIndexForLocation(cubeLoc));
					Color c = cpt.getColor((float)Math.log10(val));
					fileWriterGMT.write("> -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+val+"\n");
					String polygonString = parLat + "\t" + (float)(lon-halfCubeLatLon) + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += parLat + "\t" + (float)(lon+halfCubeLatLon) + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += parLat + "\t" + (float)(lon+halfCubeLatLon) + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					polygonString += parLat + "\t" + (float)(lon-halfCubeLatLon) + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
				}
			}
			
			// Data for squares on the NS trending vertical face
			double lon = startCubeLon;
			for(int i=0;i<numLatLon;i++) {
				lat = startCubeLat + i*cubeLatLonSpacing;
				for(int d=0; d<numCubeDepths; d++) {
					double depth = getCubeDepth(d);
					Location cubeLoc = new Location(lat,lon,depth);
					double val = sampler.getY(this.getCubeIndexForLocation(cubeLoc));
					Color c = cpt.getColor((float)Math.log10(val));
					fileWriterGMT.write("> -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+val+"\n");
					String polygonString = (float)(lat-halfCubeLatLon) + "\t" + parLon + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + parLon + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + parLon + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					polygonString += (float)(lat-halfCubeLatLon) + "\t" + parLon + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
				}
			}
			
			// Data for top surface at zero depth
			double depth = 0.0;
			for(int i=0;i<numLatLon;i++) {
				lat = startCubeLat + i*cubeLatLonSpacing;
				for(int j=0; j<numLatLon; j++) {
					lon = startCubeLon + j*cubeLatLonSpacing;
					Location cubeLoc = new Location(lat,lon,depth);
					int cubeIndex = this.getCubeIndexForLocation(cubeLoc);
					if(cubeIndex == -1) 
						continue;
					// round the back-side edge
					double distDegree = Math.sqrt((cubeLoc.getLatitude()-startCubeLat)*(cubeLoc.getLatitude()-startCubeLat) + (cubeLoc.getLongitude()-startCubeLon)*(cubeLoc.getLongitude()-startCubeLon));
					if(distDegree>sliceLenghtDegrees)
						continue;
					double val = sampler.getY(this.getCubeIndexForLocation(cubeLoc));
					Color c = cpt.getColor((float)Math.log10(val));
					fileWriterGMT.write("> -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+val+"\n");
					String polygonString = (float)(lat-halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + depth +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + depth +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + depth +"\n";
					polygonString += (float)(lat-halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + depth +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
				}
			}	
			fileWriterGMT.close();
			fileWriterSCECVDO.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		
	}
	
	
	public void writeGMT_PieSliceRatesData(Location parLoc, String fileNamePrefix) {
		
		double sliceLenghtDegrees = 2;
		
		try {
			FileWriter fileWriterGMT = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"GMT.txt"));
			FileWriter fileWriterSCECVDO = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"SCECVDO.txt"));
			CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
			
			int parLocIndex = getParLocIndexForLocation(parLoc);
			Location translatedParLoc = getParLocationForIndex(parLocIndex);
			float parLat = (float) translatedParLoc.getLatitude();
			float parLon = (float) translatedParLoc.getLongitude();
			IntegerPDF_FunctionSampler sampler = getCubeSamplerWithERF_GriddedRatesOnly();
			// normalize
			sampler.scale(1.0/sampler.getSumOfY_vals());
			
//			// compute min and max
//			double minVal=Double.POSITIVE_INFINITY, maxVal=Double.NEGATIVE_INFINITY;
//			for(int i=0; i<sampler.size();i++) {
//				Location loc = this.getCubeLocationForIndex(i);
//				double latDiff = Math.abs(loc.getLatitude()-parLoc.getLatitude());
//				double lonDiff = Math.abs(loc.getLongitude()-parLoc.getLongitude());
//				double distDegrees = Math.sqrt(latDiff*latDiff+lonDiff*lonDiff);
//				if(distDegrees>sliceLenghtDegrees)
//					continue;
//				double val = Math.log10(sampler.getY(i));
//				if(val<-15)
//					continue;
//				if(minVal>val)
//					minVal=val;
//				if(maxVal<val)
//					maxVal=val;
//			}
//			
//			System.out.println("minVal="+minVal+"\tmaxVal="+maxVal);
//			System.out.println("Math.round(minVal)="+Math.round(minVal)+"\tMath.round(maxVal)="+Math.round(maxVal));
//	        cpt = cpt.rescale(Math.round(minVal), Math.round(maxVal));
	        
	        // hard coded:
	        cpt = cpt.rescale(-7, -5);
	        cpt = cpt.rescale(-9, -1);
	        
	        cpt.writeCPTFile(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"_CPT.txt"));
			
			double halfCubeLatLon = cubeLatLonSpacing/2.0;
			double halfCubeDepth = depthDiscr/2.0;
			double startCubeLon = translatedParLoc.getLongitude() + halfCubeLatLon;
			double startCubeLat = translatedParLoc.getLatitude() + halfCubeLatLon;
			int numLatLon = (int)(sliceLenghtDegrees/cubeLatLonSpacing);	// 3 degrees in each direction

			// Data for squares on the EW trending vertical face
			double lat = startCubeLat;
			for(int i=0;i<numLatLon;i++) {
				double lon = startCubeLon + i*cubeLatLonSpacing;
				for(int d=0; d<numCubeDepths; d++) {
					double depth = getCubeDepth(d);
					Location cubeLoc = new Location(lat,lon,depth);
					double val = sampler.getY(this.getCubeIndexForLocation(cubeLoc));
					Color c = cpt.getColor((float)Math.log10(val));
					fileWriterGMT.write("> -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+val+"\n");
					String polygonString = parLat + "\t" + (float)(lon-halfCubeLatLon) + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += parLat + "\t" + (float)(lon+halfCubeLatLon) + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += parLat + "\t" + (float)(lon+halfCubeLatLon) + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					polygonString += parLat + "\t" + (float)(lon-halfCubeLatLon) + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
				}
			}
			
			// Data for squares on the NS trending vertical face
			double lon = startCubeLon;
			for(int i=0;i<numLatLon;i++) {
				lat = startCubeLat + i*cubeLatLonSpacing;
				for(int d=0; d<numCubeDepths; d++) {
					double depth = getCubeDepth(d);
					Location cubeLoc = new Location(lat,lon,depth);
					double val = sampler.getY(this.getCubeIndexForLocation(cubeLoc));
					Color c = cpt.getColor((float)Math.log10(val));
					fileWriterGMT.write("> -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+val+"\n");
					String polygonString = (float)(lat-halfCubeLatLon) + "\t" + parLon + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + parLon + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + parLon + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					polygonString += (float)(lat-halfCubeLatLon) + "\t" + parLon + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
				}
			}
			
			// Data for top surface at zero depth
			double depth = 0.0;
			for(int i=0;i<numLatLon;i++) {
				lat = startCubeLat + i*cubeLatLonSpacing;
				for(int j=0; j<numLatLon; j++) {
					lon = startCubeLon + j*cubeLatLonSpacing;
					Location cubeLoc = new Location(lat,lon,depth);
					int cubeIndex = this.getCubeIndexForLocation(cubeLoc);
					if(cubeIndex == -1) 
						continue;
					// round the back-side edge
					double distDegree = Math.sqrt((cubeLoc.getLatitude()-startCubeLat)*(cubeLoc.getLatitude()-startCubeLat) + (cubeLoc.getLongitude()-startCubeLon)*(cubeLoc.getLongitude()-startCubeLon));
					if(distDegree>sliceLenghtDegrees)
						continue;
					double val = sampler.getY(this.getCubeIndexForLocation(cubeLoc));
					Color c = cpt.getColor((float)Math.log10(val));
					fileWriterGMT.write("> -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+val+"\n");
					String polygonString = (float)(lat-halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + depth +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + depth +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + depth +"\n";
					polygonString += (float)(lat-halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + depth +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
				}
			}	
			fileWriterGMT.close();
			fileWriterSCECVDO.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	
	
	
	public void writeRatesCrossSectionData(Location startLoc, double lengthDegrees,String fileNamePrefix, double magThresh, boolean fltOnly) {
				
		try {
			FileWriter fileWriterGMT = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"GMT.txt"));
			FileWriter fileWriterSCECVDO = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"SCECVDO.txt"));
			CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
			
			// get closest cube-center
			Location startCubeLoc = getCubeLocationForIndex(getCubeIndexForLocation(startLoc));
	        
	        // hard coded:
	        cpt = cpt.rescale(-8.5, -4.5);
	        cpt.setBelowMinColor(Color.WHITE);
	        
	        cpt.writeCPTFile(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"_CPT.txt"));
			
			double halfCubeLatLon = cubeLatLonSpacing/2.0;
			double halfCubeDepth = depthDiscr/2.0;
			double startCubeLon = startCubeLoc.getLongitude();
			double startCubeLat = startCubeLoc.getLatitude();
			int numLatLon = (int)(lengthDegrees/cubeLatLonSpacing);
			
//int testCubeIndex = this.getCubeIndexForLocation(new Location(34.76,-118.48,0.0));
//int cubeRegIndex = getCubeRegAndDepIndicesForIndex(testCubeIndex)[0];
//System.out.println("HERE isCubeInsideFaultPolygon[cubeRegIndex]="+isCubeInsideFaultPolygon[cubeRegIndex]);
			
			double minVal = Double.MAX_VALUE, maxVal = -Double.MAX_VALUE;

			// Data for squares on the EW trending vertical face
			double lat = startCubeLat;
			double lon = startCubeLon;
			for(int i=0;i<numLatLon;i++) {
				for(int d=0; d<numCubeDepths; d++) {
					double depth = getCubeDepth(d);
					Location cubeLoc = new Location(lat,lon,depth);
					int cubeIndex = getCubeIndexForLocation(cubeLoc);
					SummedMagFreqDist mfd = getCubeMFD(cubeIndex);
					double val = mfd.getCumRate(magThresh);
					if(minVal>Math.log10(val))
						minVal=Math.log10(val);
					if(maxVal<Math.log10(val))
						maxVal=Math.log10(val);	
					if(fltOnly && getCubeMFD_SupraSeisOnly(cubeIndex).getTotalIncrRate()>1e-15) { // second term means its inside a polygon
						val = 1e-16; // make it white by default
						HashMap<Integer,Double> sectsAndFractMap = getSectionsAndTheirFractsCrossingCube(cubeIndex);
						if(sectsAndFractMap.size()>0) {
							val=0;
							for(int s:sectsAndFractMap.keySet()) {
								val += longTermSupraSeisMFD_OnSectArray[s].getCumRate(magThresh)*sectsAndFractMap.get(s);
							}
						}
					}
					Color c = cpt.getColor((float)Math.log10(val));
					fileWriterGMT.write("> -W125/125/125 -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+val+"\n");
					String polygonString = (float)(lat-halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += (float)(lat-halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
				}
				lat+=cubeLatLonSpacing;
				lon+=cubeLatLonSpacing;
			}
			
			// Data for the surface
			double depth = 0;
			int numOtherWay = 30;
			double newStartLat = startCubeLat;
			double newStartLon = startCubeLon+cubeLatLonSpacing;
			int numLonDone=0;
			for(int j=0; j<numOtherWay; j++) {
//				newStartLon = -j*cubeLatLonSpacing+startCubeLon;
//				if( (j & 1) != 0) {
//					if(j !=0)
//						newStartLat+=cubeLatLonSpacing;
//				}
//				else
//					newStartLon -= cubeLatLonSpacing;
				if(numLonDone!=2){
					newStartLon -= cubeLatLonSpacing;
					numLonDone+=1;
				}
				else {
					newStartLat+=cubeLatLonSpacing;
					numLonDone=0;
				}
				lat = newStartLat;
				lon = newStartLon;
				for(int i=0;i<numLatLon;i++) {
					Location cubeLoc = new Location(lat,lon,depth+halfCubeDepth);
					int cubeIndex = getCubeIndexForLocation(cubeLoc);
					SummedMagFreqDist mfd = getCubeMFD(cubeIndex);
					double val = mfd.getCumRate(magThresh);
					if(minVal>Math.log10(val))
						minVal=Math.log10(val);
					if(maxVal<Math.log10(val))
						maxVal=Math.log10(val);		
					if(fltOnly && getCubeMFD_SupraSeisOnly(cubeIndex).getTotalIncrRate()>1e-15) { // second term means its inside a polygon
						val = 1e-16; // make it white by default
						HashMap<Integer,Double> sectsAndFractMap = getSectionsAndTheirFractsCrossingCube(cubeIndex);
						if(sectsAndFractMap.size()>0) {
							val=0;
							for(int s:sectsAndFractMap.keySet()) {
								val += longTermSupraSeisMFD_OnSectArray[s].getCumRate(magThresh)*sectsAndFractMap.get(s);
							}
						}
					}
					Color c = cpt.getColor((float)Math.log10(val));
//if(cubeIndex==testCubeIndex) System.out.println("HERE index, val: "+testCubeIndex+"\t"+val+"\n"+mfd);
//if(cubeIndex==testCubeIndex) System.out.println("HERE RGB: "+"> -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterGMT.write("> -W125/125/125 -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+val+"\n");
					String polygonString = (float)(lat-halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + depth +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + depth +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + depth +"\n";
					if(j != 0)
						polygonString += (float)(lat-halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + depth +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
					lat += cubeLatLonSpacing;
					lon += cubeLatLonSpacing;
				}
			}

			
			fileWriterGMT.close();
			fileWriterSCECVDO.close();
			
			System.out.println("Value Range:\n\tminVal="+minVal+"\n\tmaxVal="+maxVal);
			
			// write out Mojave subsection polygons
			FileWriter fileWriterPolygons = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, "PolygonData.txt"));
			for(int i=1849; i>1843;i--) {
				String polygonString = "> -W\n";
				for(Location loc : faultPolyMgr.getPoly(i).getBorder()) {
					polygonString += (float)loc.getLatitude() + "\t" + (float)loc.getLongitude() + "\t" + loc.getDepth() +"\n";
				}
				fileWriterPolygons.write(polygonString);

			}
			fileWriterPolygons.close();
			
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		
		
	}
	
	
	
	public void writeBulgeCrossSectionData(Location startLoc, double lengthDegrees,String fileNamePrefix, boolean fltOnly) {
		
		try {
			FileWriter fileWriterGMT = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"GMT.txt"));
			FileWriter fileWriterSCECVDO = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"SCECVDO.txt"));
			CPT cpt = GMT_CPT_Files.UCERF3_RATIOS.instance();
			
			// get closest cube-center
			Location startCubeLoc = getCubeLocationForIndex(getCubeIndexForLocation(startLoc));
	        
	        // hard coded:
	        cpt = cpt.rescale(-2, 2);
	        cpt.setBelowMinColor(Color.WHITE);
	        
	        cpt.writeCPTFile(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"_CPT.txt"));
			
			double halfCubeLatLon = cubeLatLonSpacing/2.0;
			double halfCubeDepth = depthDiscr/2.0;
			double startCubeLon = startCubeLoc.getLongitude();
			double startCubeLat = startCubeLoc.getLatitude();
			int numLatLon = (int)(lengthDegrees/cubeLatLonSpacing);
			
			double minVal = Double.MAX_VALUE, maxVal = -Double.MAX_VALUE;

			// Data for squares on the EW trending vertical face
			double lat = startCubeLat;
			double lon = startCubeLon;
			for(int i=0;i<numLatLon;i++) {
				for(int d=0; d<numCubeDepths; d++) {
					double depth = getCubeDepth(d);
					Location cubeLoc = new Location(lat,lon,depth);
					int cubeIndex = getCubeIndexForLocation(cubeLoc);
					double val = 1.0/getAveScalingFactorToImposeGR_supraRatesInCube(cubeIndex);
					if(minVal>Math.log10(val))
						minVal=Math.log10(val);
					if(maxVal<Math.log10(val))
						maxVal=Math.log10(val);	
					if(fltOnly && getCubeMFD_SupraSeisOnly(cubeIndex).getTotalIncrRate()>1e-15) {
						val = 1e-16; // make it white by default
						HashMap<Integer,Double> sectsAndFractMap = getSectionsAndTheirFractsCrossingCube(cubeIndex);
						if(sectsAndFractMap.size()>0) {
							val=0;
							double wt=0;
							for(int s:sectsAndFractMap.keySet()) {
								val += totSectNuclRateArray[s]*charFactorForSectArray[s];
								wt += totSectNuclRateArray[s];
							}
							val /= wt; 	// so its weight averaged by section rate
							val *= 8;	// approximate effect of forcing it all on the cubes crossed by the fault
						}
					}
					Color c = cpt.getColor((float)Math.log10(val));
					fileWriterGMT.write("> -W125/125/125 -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+val+"\n");
					String polygonString = (float)(lat-halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += (float)(lat-halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
				}
				lat+=cubeLatLonSpacing;
				lon+=cubeLatLonSpacing;
			}
			
			// Data for the surface
			double depth = 0;
			int numOtherWay = 30;
			double newStartLat = startCubeLat;
			double newStartLon = startCubeLon+cubeLatLonSpacing;
			int numLonDone=0;
			for(int j=0; j<numOtherWay; j++) {
				if(numLonDone!=2){
					newStartLon -= cubeLatLonSpacing;
					numLonDone+=1;
				}
				else {
					newStartLat+=cubeLatLonSpacing;
					numLonDone=0;
				}
				lat = newStartLat;
				lon = newStartLon;
				for(int i=0;i<numLatLon;i++) {
					Location cubeLoc = new Location(lat,lon,depth+halfCubeDepth);
					int cubeIndex = getCubeIndexForLocation(cubeLoc);
					double val = 1.0/getAveScalingFactorToImposeGR_supraRatesInCube(cubeIndex);
					if(minVal>Math.log10(val))
						minVal=Math.log10(val);
					if(maxVal<Math.log10(val))
						maxVal=Math.log10(val);		
					if(fltOnly && getCubeMFD_SupraSeisOnly(cubeIndex).getTotalIncrRate()>1e-15) {
						val = 1e-16; // make it white by default
						HashMap<Integer,Double> sectsAndFractMap = getSectionsAndTheirFractsCrossingCube(cubeIndex);
						if(sectsAndFractMap.size()>0) {
							val=0;
							double wt=0;
							for(int s:sectsAndFractMap.keySet()) {
								val += totSectNuclRateArray[s]*charFactorForSectArray[s];
								wt += totSectNuclRateArray[s];
							}
							val /= wt; 	// so its weight averaged by section rate
							val *= 8;	// approximate effect of forcing it all on the cubes crossed by the fault
						}
					}
					Color c = cpt.getColor((float)Math.log10(val));
					fileWriterGMT.write("> -W125/125/125 -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+val+"\n");
					String polygonString = (float)(lat-halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + depth +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + depth +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + depth +"\n";
					if(j != 0)
						polygonString += (float)(lat-halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + depth +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
					lat += cubeLatLonSpacing;
					lon += cubeLatLonSpacing;
				}
			}

			
			fileWriterGMT.close();
			fileWriterSCECVDO.close();
			
			System.out.println("Value Range:\n\tminVal="+minVal+"\n\tmaxVal="+maxVal);
			
			// write out Mojave subsection polygons
			FileWriter fileWriterPolygons = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, "PolygonData.txt"));
			for(int i=1849; i>1843;i--) {
				String polygonString = "> -W\n";
				for(Location loc : faultPolyMgr.getPoly(i).getBorder()) {
					polygonString += (float)loc.getLatitude() + "\t" + (float)loc.getLongitude() + "\t" + loc.getDepth() +"\n";
				}
				fileWriterPolygons.write(polygonString);

			}
			fileWriterPolygons.close();
			
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		
		
	}

	
	
	/**
	 * TODO THIS IS OUT OF DATE: START FROM SCRATCH USING writeRatesCrossSectionData
	 * @param startLoc
	 * @param lengthDegrees
	 * @param fileNamePrefix
	 */
	public void OLDwriteBulgeCrossSectionData(Location startLoc, double lengthDegrees,String fileNamePrefix) {
		
		try {
			FileWriter fileWriterGMT = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"GMT.txt"));
			FileWriter fileWriterSCECVDO = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"SCECVDO.txt"));
			CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
			
			// get closest cube-center
			Location startCubeLoc = getCubeLocationForIndex(getCubeIndexForLocation(startLoc));
			
			if(mfdForSrcArray == null) {
				computeMFD_ForSrcArrays(2.05, 8.95, 70);
			}

	        
	        // hard coded:
	        cpt = cpt.rescale(-3, 3);
	        
	        cpt.writeCPTFile(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+"_CPT.txt"));
			
			double halfCubeLatLon = cubeLatLonSpacing/2.0;
			double halfCubeDepth = depthDiscr/2.0;
			double startCubeLon = startCubeLoc.getLongitude();
			double startCubeLat = startCubeLoc.getLatitude();
			int numLatLon = (int)(lengthDegrees/cubeLatLonSpacing);

			// Data for squares on the EW trending vertical face
			double lat = startCubeLat;
			double lon = startCubeLon;
			for(int i=0;i<numLatLon;i++) {
				for(int d=0; d<numCubeDepths; d++) {
					double depth = getCubeDepth(d);
					Location cubeLoc = new Location(lat,lon,depth);
					int cubeIndex = getCubeIndexForLocation(cubeLoc);
					
					SummedMagFreqDist mfdSupra = getCubeMFD_SupraSeisOnly(cubeIndex);
					SummedMagFreqDist mfdGridded = getCubeMFD_GriddedSeisOnly(cubeIndex);
					double bulge = 1.0;
					if(mfdSupra != null &&  mfdGridded != null) {
						bulge = 1.0/ETAS_Utils.getScalingFactorToImposeGR_supraRates(mfdSupra, mfdGridded, false);
						if(Double.isInfinite(bulge))
							bulge = 1e3;				
					}
					Color c = cpt.getColor((float)Math.log10(bulge));
					fileWriterGMT.write("> -G"+c.getRed()+"/"+c.getGreen()+"/"+c.getBlue()+"\n");
					fileWriterSCECVDO.write("> "+bulge+"\n");
					String polygonString = (float)(lat-halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					polygonString += (float)(lat-halfCubeLatLon) + "\t" + (float)(lon-halfCubeLatLon) + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + -(float)(depth+halfCubeDepth) +"\n";
					polygonString += (float)(lat+halfCubeLatLon) + "\t" + (float)(lon+halfCubeLatLon) + "\t" + -(float)(depth-halfCubeDepth) +"\n";
					fileWriterGMT.write(polygonString);
					fileWriterSCECVDO.write(polygonString);
				}
				lat+=cubeLatLonSpacing;
				lon+=cubeLatLonSpacing;
			}
			fileWriterGMT.close();
			fileWriterSCECVDO.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}


	
	/**
	 * This plots the implied MFD bulge for sections (log10 of one over the GR correction) using GMT
	 * @param resultsDir
	 * @param nameSuffix
	 * @param display
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 * @throws IOException
	 */
	public void plotImpliedBulgeForSubSections(String dirName, String nameSuffix, boolean display) 
			throws GMT_MapException, RuntimeException, IOException {

		File resultsDir = new File(GMT_CA_Maps.GMT_DIR, dirName);
		if(!resultsDir.exists())
			resultsDir.mkdir();
		
		List<FaultSectionPrefData> faults = fssERF.getSolution().getRupSet().getFaultSectionDataList();
		double[] values = new double[faults.size()];
		
		// Make sure long-term MFDs are created
		makeLongTermSectMFDs();
		
		FileWriter fileWriter = new FileWriter(new File(resultsDir, dirName+".csv"));
		fileWriter.write("SectID,CharFactorSupraRates,CharFactorNumPrimary,CharFactorMoRate,subRate,supraRate,minSupraMag,MoRate,SectName,SubSect\n");

		// System.out.println("GR Correction Factors:\nsectID\t1.0/GRcorr\tsectName");
		
		double meanValSupraRates=0;
		double meanValSupraRatesMoRateWted=0;
		double meanValSupraRatesSupraRateWted=0;
		double meanValNumPrimary=0;
		double meanValNumPrimaryMoRateWted=0;
		double meanValNumPrimarySupraRateWted=0;
		double totMoRate=0;
		double totSupraRate=0;
		
		double meanValSupraRatesLog=0;
		double meanValSupraRatesMoRateWtedLog=0;
		double meanValSupraRatesSupraRateWtedLog=0;
		double meanValLog=0;
		double meanValMoRateWtedLog=0;
		double meanValSupraRateWtedLog=0;
		
		ArbDiscrEmpiricalDistFunc charValSupraRatesDist = new ArbDiscrEmpiricalDistFunc();
		ArbDiscrEmpiricalDistFunc charValSupraRatesDistMoRateWted = new ArbDiscrEmpiricalDistFunc();
		ArbDiscrEmpiricalDistFunc charValSupraRatesDistSupraRateWted = new ArbDiscrEmpiricalDistFunc();
		DefaultXY_DataSet charValSupraRatesVsMomentRateData = new DefaultXY_DataSet();
		DefaultXY_DataSet charValSupraRatesVsSupraRateData = new DefaultXY_DataSet();

		int numPts=0;
		for(int sectIndex=0;sectIndex<values.length;sectIndex++) {
			double valNumPrimary, valSupraRates, valMoRate;
			if(longTermSupraSeisMFD_OnSectArray[sectIndex] != null) {
				
				// TODO only need this temporarily?
				if (Double.isNaN(longTermSupraSeisMFD_OnSectArray[sectIndex].getMaxMagWithNonZeroRate())){
					System.out.println("NaN HERE: "+fssERF.getSolution().getRupSet().getFaultSectionData(sectIndex).getName());
					throw new RuntimeException("Problem");
				}
				
				valNumPrimary = 1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false);
				valSupraRates = 1.0/ETAS_Utils.getScalingFactorToImposeGR_supraRates(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false);
				valMoRate = 1.0/ETAS_Utils.getScalingFactorToImposeGR_MoRates(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false);
			}
			else {	// no supra-seismogenic ruptures
				throw new RuntimeException("Problem");
			}
			
			values[sectIndex] = Math.log10(valSupraRates);
			
			// dont continue if the value defaulted to 1.0.
			if(longTermSubSeisMFD_OnSectList.get(sectIndex).getTotalIncrRate()<=10e-16)
				continue;
			
			numPts+=1;
			double moRate = rupSet.getFaultSectionData(sectIndex).calcMomentRate(true);
			double minSupraMag = ETAS_Utils.getMinMagSupra(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex));
			double supraRate = longTermSupraSeisMFD_OnSectArray[sectIndex].getCumRate(minSupraMag);
			double subRate = longTermSubSeisMFD_OnSectList.get(sectIndex).getCumRate(2.55);
			
			charValSupraRatesVsMomentRateData.set(moRate,valSupraRates);
			charValSupraRatesVsSupraRateData.set(supraRate,valSupraRates);
			
			meanValSupraRates += valSupraRates;
			meanValSupraRatesMoRateWted += moRate*valSupraRates;
			meanValSupraRatesSupraRateWted += supraRate*valSupraRates;
			meanValNumPrimary += valNumPrimary;
			meanValNumPrimaryMoRateWted += moRate*valNumPrimary;
			meanValNumPrimarySupraRateWted += supraRate*valNumPrimary;
			totMoRate += moRate;
			totSupraRate+=supraRate;
			
			meanValSupraRatesLog += Math.log10(valSupraRates);
			meanValSupraRatesMoRateWtedLog += moRate*Math.log10(valSupraRates);
			meanValSupraRatesSupraRateWtedLog += supraRate*Math.log10(valSupraRates);
			meanValLog += Math.log10(valNumPrimary);
			meanValMoRateWtedLog += moRate*Math.log10(valNumPrimary);
			meanValSupraRateWtedLog += supraRate*Math.log10(valNumPrimary);
			
			charValSupraRatesDist.set(valSupraRates, 1.0);
			charValSupraRatesDistMoRateWted.set(valSupraRates,moRate);
			charValSupraRatesDistSupraRateWted.set(valSupraRates,supraRate);

			//System.out.println(sectIndex+"\t"+(float)val+"\t"+fssERF.getSolution().getRupSet().getFaultSectionData(sectIndex).getName());
			fileWriter.write(sectIndex+","+(float)valSupraRates+","+(float)valNumPrimary+","+(float)valMoRate+","+subRate+","+supraRate+","+minSupraMag+","+moRate+","+fssERF.getSolution().getRupSet().getFaultSectionData(sectIndex).getName()+"\n");

		}
		fileWriter.close();
		
		meanValSupraRates/=numPts;
		meanValNumPrimary/=numPts;
		meanValSupraRatesMoRateWted/=totMoRate;
		meanValNumPrimaryMoRateWted/=totMoRate;
		meanValSupraRatesSupraRateWted/=totSupraRate;
		meanValNumPrimarySupraRateWted/=totSupraRate;

		meanValSupraRatesLog/=numPts;
		meanValLog/=numPts;
		meanValSupraRatesMoRateWtedLog/=totMoRate;
		meanValMoRateWtedLog/=totMoRate;
		meanValSupraRatesSupraRateWtedLog/=totSupraRate;
		meanValSupraRateWtedLog/=totSupraRate;
		
		meanValSupraRatesLog = Math.pow(10, meanValSupraRatesLog);
		meanValLog = Math.pow(10, meanValLog);
		meanValSupraRatesMoRateWtedLog = Math.pow(10, meanValSupraRatesMoRateWtedLog);
		meanValMoRateWtedLog = Math.pow(10, meanValMoRateWtedLog);
		meanValSupraRatesSupraRateWtedLog = Math.pow(10, meanValSupraRatesSupraRateWtedLog);
		meanValSupraRateWtedLog = Math.pow(10, meanValSupraRateWtedLog);

		System.out.println("meanValSupraRates="+meanValSupraRates+"\nmeanValSupraRatesMoRateWted="+meanValSupraRatesMoRateWted+"\nmeanValSupraRatesSupraRateWted="+meanValSupraRatesSupraRateWted);
		System.out.println("meanValNumPrimary="+meanValNumPrimary+"\nmeanValNumPrimaryMoRateWted="+meanValNumPrimaryMoRateWted+"\nmeanValNumPrimarySupraRateWted="+meanValNumPrimarySupraRateWted);
		System.out.println("meanValSupraRatesLog="+meanValSupraRatesLog+"\nmeanValSupraRatesMoRateWtedLog="+meanValSupraRatesMoRateWtedLog+"\nmeanValSupraRatesSupraRateWtedLog="+meanValSupraRatesSupraRateWtedLog);
		System.out.println("meanValNumPrimaryLog="+meanValLog+"\nmeanValNumPrimaryMoRateWtedLog="+meanValMoRateWtedLog+"\nmeanValNumPrimarySupraRateWtedLog="+meanValSupraRateWtedLog);
		
		ArbitrarilyDiscretizedFunc charValSupraRatesDistCumDist = charValSupraRatesDist.getCumDist();
		charValSupraRatesDistCumDist.scale(1.0/charValSupraRatesDist.calcSumOfY_Vals());
		charValSupraRatesDistCumDist.setName("charValSupraRatesDistCumDist");
		charValSupraRatesDistCumDist.setInfo("mean="+charValSupraRatesDist.getMean()+"; median="+charValSupraRatesDist.getMedian());
		
		ArbitrarilyDiscretizedFunc charValSupraRatesDistMoRateWtedCumDist = charValSupraRatesDistMoRateWted.getCumDist();
		charValSupraRatesDistMoRateWtedCumDist.scale(1.0/charValSupraRatesDistMoRateWted.calcSumOfY_Vals());
		charValSupraRatesDistMoRateWtedCumDist.setName("charValSupraRatesDistMoRateWtedCumDist");
		charValSupraRatesDistMoRateWtedCumDist.setInfo("mean="+charValSupraRatesDistMoRateWted.getMean()+"; median="+charValSupraRatesDistMoRateWted.getMedian());

		ArbitrarilyDiscretizedFunc charValSupraRatesDistSupraRateWtedCumDist = charValSupraRatesDistSupraRateWted.getCumDist();
		charValSupraRatesDistSupraRateWtedCumDist.scale(1.0/charValSupraRatesDistSupraRateWted.calcSumOfY_Vals());
		charValSupraRatesDistSupraRateWtedCumDist.setName("charValSupraRatesDistSupraRateWtedCumDist");
		charValSupraRatesDistSupraRateWtedCumDist.setInfo("mean="+charValSupraRatesDistSupraRateWted.getMean()+"; median="+charValSupraRatesDistSupraRateWted.getMedian());
		
		ArrayList<ArbitrarilyDiscretizedFunc> funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
		funcs.add(charValSupraRatesDistCumDist);
		funcs.add(charValSupraRatesDistMoRateWtedCumDist);
		funcs.add(charValSupraRatesDistSupraRateWtedCumDist);
		GraphWindow sectGraph = new GraphWindow(funcs, "Sect CharFactor Stats"); 
		sectGraph.setX_AxisLabel("CharFactor");
		sectGraph.setY_AxisLabel("Cumulative Fraction");
		sectGraph.setX_AxisRange(0.01,100.0);
		sectGraph.setXLog(true);
		sectGraph.setY_AxisRange(0.0,1.0);
		sectGraph.setAxisLabelFontSize(24);
		sectGraph.setTickLabelFontSize(22);
		sectGraph.setPlotLabelFontSize(26);
		File fileName1 = new File(resultsDir,"charValSupraRatesDist.pdf");
		sectGraph.saveAsPDF(fileName1.getAbsolutePath());

		
		GraphWindow sectGraph4 = new GraphWindow(charValSupraRatesVsMomentRateData, "CharFactor vs Moment Rate", new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK)); 
		sectGraph4.setY_AxisLabel("CharFactor");
		sectGraph4.setX_AxisLabel("Moment Rate (NM/yr)");
		sectGraph4.setXLog(true);
		sectGraph4.setYLog(true);
		sectGraph4.setAxisLabelFontSize(24);
		sectGraph4.setTickLabelFontSize(22);
		sectGraph4.setPlotLabelFontSize(26);
		File fileName2 = new File(resultsDir,"charValSupraRatesVsMoRate.pdf");
		sectGraph4.saveAsPDF(fileName2.getAbsolutePath());
		
		
		// make binned data statistics for charValSupraRatesVsSupraRateData
//		double startBinCenterLogX = -2.25;
		double startBinCenterLogX = -2.0-0.25/2.0;
		double binWidthLogX = 0.25;
//		double endBinCenterLogX = -6.5;
		double endBinCenterLogX = -6.00;
		DefaultXY_DataSet medianBinnedData= new DefaultXY_DataSet();
		DefaultXY_DataSet meanBinnedData= new DefaultXY_DataSet();
		DefaultXY_DataSet meanMinus2stdomData= new DefaultXY_DataSet();
		DefaultXY_DataSet meanPlus2stdomData= new DefaultXY_DataSet();

		for(double binCenterLogX=startBinCenterLogX; binCenterLogX>endBinCenterLogX; binCenterLogX-=binWidthLogX) {
			double minVal = Math.pow(10.0, binCenterLogX-binWidthLogX/2.0);
			double maxVal = Math.pow(10.0, binCenterLogX+binWidthLogX/2.0);
			ArbDiscrEmpiricalDistFunc distFunc = new ArbDiscrEmpiricalDistFunc ();

//			for(int i=0;i<charValSupraRatesVsSupraRateData.size();i++) {
//				double xVal = charValSupraRatesVsSupraRateData.getX(i);
//				if(xVal>minVal && xVal<=maxVal)
//					distFunc.set(charValSupraRatesVsSupraRateData.getY(i),1.0);
//			}
//			medianBinnedData.set(Math.pow(10.0, binCenterLogX),distFunc.getMedian());
//			meanBinnedData.set(Math.pow(10.0, binCenterLogX),distFunc.getMean());
//			double stdom = distFunc.getStdDev()/Math.sqrt(distFunc.calcSumOfY_Vals());
//			meanMinus2stdomData.set(Math.pow(10.0, binCenterLogX),distFunc.getMean()-2*stdom);
//			meanPlus2stdomData.set(Math.pow(10.0, binCenterLogX),distFunc.getMean()+2*stdom);

			// log means, shouldn't use if we want to preserve rates
			for(int i=0;i<charValSupraRatesVsSupraRateData.size();i++) {
				double xVal = charValSupraRatesVsSupraRateData.getX(i);
				if(xVal>minVal && xVal<=maxVal)
					distFunc.set(Math.log10(charValSupraRatesVsSupraRateData.getY(i)),1.0);
			}
			medianBinnedData.set(Math.pow(10.0, binCenterLogX),Math.pow(10.0,distFunc.getMedian()));
			meanBinnedData.set(Math.pow(10.0, binCenterLogX),Math.pow(10.0,distFunc.getMean()));
			double stdom = distFunc.getStdDev()/Math.sqrt(distFunc.calcSumOfY_Vals());
			meanMinus2stdomData.set(Math.pow(10.0, binCenterLogX),Math.pow(10.0,distFunc.getMean()-2*stdom));
			meanPlus2stdomData.set(Math.pow(10.0, binCenterLogX),Math.pow(10.0,distFunc.getMean()+2*stdom));
		}
		
		ArrayList<XY_DataSet> funcs2 = new ArrayList<XY_DataSet>();
		funcs2.add(charValSupraRatesVsSupraRateData);
		funcs2.add(medianBinnedData);
		funcs2.add(meanBinnedData);
		funcs2.add(meanMinus2stdomData);
		funcs2.add(meanPlus2stdomData);
		ArrayList<PlotCurveCharacterstics> plotCharList = new ArrayList<PlotCurveCharacterstics>();
		plotCharList.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.red));

		
		GraphWindow sectGraph5 = new GraphWindow(funcs2, "CharFactor vs SupraRate", plotCharList); 
		sectGraph5.setY_AxisLabel("CharFactor");
		sectGraph5.setX_AxisLabel("SupraRate (per year)");
		sectGraph5.setXLog(true);
		sectGraph5.setYLog(true);
		sectGraph5.setAxisLabelFontSize(24);
		sectGraph5.setTickLabelFontSize(22);
		sectGraph5.setPlotLabelFontSize(26);
		File fileName3 = new File(resultsDir,"charValSupraRatesVsSupraRate.pdf");
		sectGraph5.saveAsPDF(fileName3.getAbsolutePath());
		

		
//		GraphWindow sectGraph2 = new GraphWindow(charValSupraRatesDist, "charValSupraRatesDist"); 
//		sectGraph.setX_AxisLabel("CharFactor");
//		sectGraph.setY_AxisLabel("Weight");
//		GraphWindow sectGraph3 = new GraphWindow(charValSupraRatesDistMoRateWted, "charValSupraRatesDistMoRateWted"); 
//		sectGraph.setX_AxisLabel("CharFactor");
//		sectGraph.setY_AxisLabel("Weight");
	

		String name = "ImpliedCharFactorForSubSections_"+nameSuffix;
		String title = "Log10(CharFactor)";
		CPT cpt= FaultBasedMapGen.getLogRatioCPT().rescale(-2, 2);
		
		FaultBasedMapGen.makeFaultPlot(cpt, FaultBasedMapGen.getTraces(faults), values, origGriddedRegion, resultsDir, name, display, false, title);
		
	}
	
	
	
	
	
	/**
	 * This plots the implied MFD bulge for sections (log10 of one over the GR correction) using GMT
	 * @param resultsDir
	 * @param nameSuffix
	 * @param display
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 * @throws IOException
	 */
	public void plotImpliedBulgeForSubSectionsHackTestMoRate(File resultsDir, String nameSuffix, boolean display) 
			throws GMT_MapException, RuntimeException, IOException {

		if(!resultsDir.exists())
			resultsDir.mkdir();
		
		List<FaultSectionPrefData> faults = fssERF.getSolution().getRupSet().getFaultSectionDataList();
		double[] values = new double[faults.size()];
		
		// Make sure long-term MFDs are created
		makeLongTermSectMFDs();
		
		FileWriter fileWriter = new FileWriter(new File(resultsDir, "FaultSubsectionCharFactorData.csv"));
		fileWriter.write("SectID,CharFactorSupraRates,CharFactorNumPrimary,supraRate,MoRate,SectName\n");

		// System.out.println("GR Correction Factors:\nsectID\t1.0/GRcorr\tsectName");
		
		double meanValSupraRates=0;
		double meanValSupraRatesMoRateWted=0;
		double meanValSupraRatesSupraRateWted=0;
		double meanValNumPrimary=0;
		double meanValNumPrimaryMoRateWted=0;
		double meanValNumPrimarySupraRateWted=0;
		double totMoRate=0;
		double totSupraRate=0;
		
		double meanValSupraRatesLog=0;
		double meanValSupraRatesMoRateWtedLog=0;
		double meanValSupraRatesSupraRateWtedLog=0;
		double meanValLog=0;
		double meanValMoRateWtedLog=0;
		double meanValSupraRateWtedLog=0;
		
		ArbDiscrEmpiricalDistFunc charValSupraRatesDist = new ArbDiscrEmpiricalDistFunc();
		ArbDiscrEmpiricalDistFunc charValSupraRatesDistMoRateWted = new ArbDiscrEmpiricalDistFunc();
		ArbDiscrEmpiricalDistFunc charValSupraRatesDistSupraRateWted = new ArbDiscrEmpiricalDistFunc();
		DefaultXY_DataSet charValSupraRatesVsMomentRateData = new DefaultXY_DataSet();
		DefaultXY_DataSet charValSupraRatesVsSupraRateData = new DefaultXY_DataSet();

		int numPts=0;
		for(int sectIndex=0;sectIndex<values.length;sectIndex++) {
			double valNumPrimary, valSupraRates, valMoRate;
			if(longTermSupraSeisMFD_OnSectArray[sectIndex] != null) {
				
				// TODO only need this temporarily?
				if (Double.isNaN(longTermSupraSeisMFD_OnSectArray[sectIndex].getMaxMagWithNonZeroRate())){
					System.out.println("NaN HERE: "+fssERF.getSolution().getRupSet().getFaultSectionData(sectIndex).getName());
					throw new RuntimeException("Problem");
				}
				
				valNumPrimary = 1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false);
				valSupraRates = 1.0/ETAS_Utils.getScalingFactorToImposeGR_MoRates(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false);
			}
			else {	// no supra-seismogenic ruptures
				throw new RuntimeException("Problem");
			}
			
			values[sectIndex] = Math.log10(valSupraRates);
			
			// dont continue if the value defaulted to 1.0.
			if(longTermSubSeisMFD_OnSectList.get(sectIndex).getTotalIncrRate()<=10e-16)
				continue;
			
			numPts+=1;
			double moRate = rupSet.getFaultSectionData(sectIndex).calcMomentRate(true);
			double minSupraMag = ETAS_Utils.getMinMagSupra(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex));
			double supraRate = longTermSupraSeisMFD_OnSectArray[sectIndex].getCumRate(minSupraMag);
			
			charValSupraRatesVsMomentRateData.set(moRate,valSupraRates);
			charValSupraRatesVsSupraRateData.set(moRate,valSupraRates);
			
			meanValSupraRates += valSupraRates;
			meanValSupraRatesMoRateWted += moRate*valSupraRates;
			meanValSupraRatesSupraRateWted += supraRate*valSupraRates;
			meanValNumPrimary += valNumPrimary;
			meanValNumPrimaryMoRateWted += moRate*valNumPrimary;
			meanValNumPrimarySupraRateWted += supraRate*valNumPrimary;
			totMoRate += moRate;
			totSupraRate+=supraRate;
			
			meanValSupraRatesLog += Math.log10(valSupraRates);
			meanValSupraRatesMoRateWtedLog += moRate*Math.log10(valSupraRates);
			meanValSupraRatesSupraRateWtedLog += supraRate*Math.log10(valSupraRates);
			meanValLog += Math.log10(valNumPrimary);
			meanValMoRateWtedLog += moRate*Math.log10(valNumPrimary);
			meanValSupraRateWtedLog += supraRate*Math.log10(valNumPrimary);
			
			charValSupraRatesDist.set(valSupraRates, 1.0);
			charValSupraRatesDistMoRateWted.set(valSupraRates,moRate);
			charValSupraRatesDistSupraRateWted.set(valSupraRates,supraRate);

			//System.out.println(sectIndex+"\t"+(float)val+"\t"+fssERF.getSolution().getRupSet().getFaultSectionData(sectIndex).getName());
			fileWriter.write(sectIndex+","+(float)valSupraRates+","+(float)valNumPrimary+","+supraRate+","+moRate+","+fssERF.getSolution().getRupSet().getFaultSectionData(sectIndex).getName()+"\n");

		}
		fileWriter.close();
		
		meanValSupraRates/=numPts;
		meanValNumPrimary/=numPts;
		meanValSupraRatesMoRateWted/=totMoRate;
		meanValNumPrimaryMoRateWted/=totMoRate;
		meanValSupraRatesSupraRateWted/=totSupraRate;
		meanValNumPrimarySupraRateWted/=totSupraRate;

		meanValSupraRatesLog/=numPts;
		meanValLog/=numPts;
		meanValSupraRatesMoRateWtedLog/=totMoRate;
		meanValMoRateWtedLog/=totMoRate;
		meanValSupraRatesSupraRateWtedLog/=totSupraRate;
		meanValSupraRateWtedLog/=totSupraRate;
		
		meanValSupraRatesLog = Math.pow(10, meanValSupraRatesLog);
		meanValLog = Math.pow(10, meanValLog);
		meanValSupraRatesMoRateWtedLog = Math.pow(10, meanValSupraRatesMoRateWtedLog);
		meanValMoRateWtedLog = Math.pow(10, meanValMoRateWtedLog);
		meanValSupraRatesSupraRateWtedLog = Math.pow(10, meanValSupraRatesSupraRateWtedLog);
		meanValSupraRateWtedLog = Math.pow(10, meanValSupraRateWtedLog);

		System.out.println("meanValSupraRates="+meanValSupraRates+"\nmeanValSupraRatesMoRateWted="+meanValSupraRatesMoRateWted+"\nmeanValSupraRatesSupraRateWted="+meanValSupraRatesSupraRateWted);
		System.out.println("meanValNumPrimary="+meanValNumPrimary+"\nmeanValNumPrimaryMoRateWted="+meanValNumPrimaryMoRateWted+"\nmeanValNumPrimarySupraRateWted="+meanValNumPrimarySupraRateWted);
		System.out.println("meanValSupraRatesLog="+meanValSupraRatesLog+"\nmeanValSupraRatesMoRateWtedLog="+meanValSupraRatesMoRateWtedLog+"\nmeanValSupraRatesSupraRateWtedLog="+meanValSupraRatesSupraRateWtedLog);
		System.out.println("meanValNumPrimaryLog="+meanValLog+"\nmeanValNumPrimaryMoRateWtedLog="+meanValMoRateWtedLog+"\nmeanValNumPrimarySupraRateWtedLog="+meanValSupraRateWtedLog);
		
		ArbitrarilyDiscretizedFunc charValSupraRatesDistCumDist = charValSupraRatesDist.getCumDist();
		charValSupraRatesDistCumDist.scale(1.0/charValSupraRatesDist.calcSumOfY_Vals());
		charValSupraRatesDistCumDist.setName("charValSupraRatesDistCumDist");
		charValSupraRatesDistCumDist.setInfo("mean="+charValSupraRatesDist.getMean()+"; median="+charValSupraRatesDist.getMedian());
		
		ArbitrarilyDiscretizedFunc charValSupraRatesDistMoRateWtedCumDist = charValSupraRatesDistMoRateWted.getCumDist();
		charValSupraRatesDistMoRateWtedCumDist.scale(1.0/charValSupraRatesDistMoRateWted.calcSumOfY_Vals());
		charValSupraRatesDistMoRateWtedCumDist.setName("charValSupraRatesDistMoRateWtedCumDist");
		charValSupraRatesDistMoRateWtedCumDist.setInfo("mean="+charValSupraRatesDistMoRateWted.getMean()+"; median="+charValSupraRatesDistMoRateWted.getMedian());

		ArbitrarilyDiscretizedFunc charValSupraRatesDistSupraRateWtedCumDist = charValSupraRatesDistSupraRateWted.getCumDist();
		charValSupraRatesDistSupraRateWtedCumDist.scale(1.0/charValSupraRatesDistSupraRateWted.calcSumOfY_Vals());
		charValSupraRatesDistSupraRateWtedCumDist.setName("charValSupraRatesDistSupraRateWtedCumDist");
		charValSupraRatesDistSupraRateWtedCumDist.setInfo("mean="+charValSupraRatesDistSupraRateWted.getMean()+"; median="+charValSupraRatesDistSupraRateWted.getMedian());
		
		ArrayList<ArbitrarilyDiscretizedFunc> funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
		funcs.add(charValSupraRatesDistCumDist);
		funcs.add(charValSupraRatesDistMoRateWtedCumDist);
		funcs.add(charValSupraRatesDistSupraRateWtedCumDist);
		GraphWindow sectGraph = new GraphWindow(funcs, "Sect CharFactor Stats"); 
		sectGraph.setX_AxisLabel("CharFactor");
		sectGraph.setY_AxisLabel("Cumulative Fraction");
		sectGraph.setX_AxisRange(0.01,100.0);
		sectGraph.setXLog(true);
		sectGraph.setY_AxisRange(0.0,1.0);
		sectGraph.setAxisLabelFontSize(24);
		sectGraph.setTickLabelFontSize(22);
		sectGraph.setPlotLabelFontSize(26);
		File fileName1 = new File(resultsDir,"charValSupraRatesDist.pdf");
		sectGraph.saveAsPDF(fileName1.getAbsolutePath());

		
		GraphWindow sectGraph4 = new GraphWindow(charValSupraRatesVsMomentRateData, "CharFactor vs Moment Rate", new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK)); 
		sectGraph4.setY_AxisLabel("CharFactor");
		sectGraph4.setX_AxisLabel("Moment Rate (NM/yr)");
		sectGraph4.setXLog(true);
		sectGraph4.setYLog(true);
		sectGraph4.setAxisLabelFontSize(24);
		sectGraph4.setTickLabelFontSize(22);
		sectGraph4.setPlotLabelFontSize(26);
		File fileName2 = new File(resultsDir,"charValSupraRatesVsMoRate.pdf");
		sectGraph4.saveAsPDF(fileName2.getAbsolutePath());
		
		
		// make binned data statistics for charValSupraRatesVsSupraRateData
//		double startBinCenterLogX = -2.25;
		double startBinCenterLogX = 17;
		double binWidthLogX = 0.5;
//		double endBinCenterLogX = -6.5;
		double endBinCenterLogX = 13;
		DefaultXY_DataSet medianBinnedData= new DefaultXY_DataSet();
		DefaultXY_DataSet meanBinnedData= new DefaultXY_DataSet();
		DefaultXY_DataSet meanMinus2stdomData= new DefaultXY_DataSet();
		DefaultXY_DataSet meanPlus2stdomData= new DefaultXY_DataSet();

		for(double binCenterLogX=startBinCenterLogX; binCenterLogX>endBinCenterLogX; binCenterLogX-=binWidthLogX) {
			double minVal = Math.pow(10.0, binCenterLogX-binWidthLogX/2.0);
			double maxVal = Math.pow(10.0, binCenterLogX+binWidthLogX/2.0);
			ArbDiscrEmpiricalDistFunc distFunc = new ArbDiscrEmpiricalDistFunc ();

//			for(int i=0;i<charValSupraRatesVsSupraRateData.size();i++) {
//				double xVal = charValSupraRatesVsSupraRateData.getX(i);
//				if(xVal>minVal && xVal<=maxVal)
//					distFunc.set(charValSupraRatesVsSupraRateData.getY(i),1.0);
//			}
//			medianBinnedData.set(Math.pow(10.0, binCenterLogX),distFunc.getMedian());
//			meanBinnedData.set(Math.pow(10.0, binCenterLogX),distFunc.getMean());
//			double stdom = distFunc.getStdDev()/Math.sqrt(distFunc.calcSumOfY_Vals());
//			meanMinus2stdomData.set(Math.pow(10.0, binCenterLogX),distFunc.getMean()-2*stdom);
//			meanPlus2stdomData.set(Math.pow(10.0, binCenterLogX),distFunc.getMean()+2*stdom);

			// log means, shouldn't use if we want to preserve rates
			for(int i=0;i<charValSupraRatesVsSupraRateData.size();i++) {
				double xVal = charValSupraRatesVsSupraRateData.getX(i);
				if(xVal>minVal && xVal<=maxVal)
					distFunc.set(Math.log10(charValSupraRatesVsSupraRateData.getY(i)),1.0);
			}
			medianBinnedData.set(Math.pow(10.0, binCenterLogX),Math.pow(10.0,distFunc.getMedian()));
			meanBinnedData.set(Math.pow(10.0, binCenterLogX),Math.pow(10.0,distFunc.getMean()));
			double stdom = distFunc.getStdDev()/Math.sqrt(distFunc.calcSumOfY_Vals());
			meanMinus2stdomData.set(Math.pow(10.0, binCenterLogX),Math.pow(10.0,distFunc.getMean()-2*stdom));
			meanPlus2stdomData.set(Math.pow(10.0, binCenterLogX),Math.pow(10.0,distFunc.getMean()+2*stdom));
		}
		
		ArrayList<XY_DataSet> funcs2 = new ArrayList<XY_DataSet>();
		funcs2.add(charValSupraRatesVsSupraRateData);
		funcs2.add(medianBinnedData);
		funcs2.add(meanBinnedData);
		funcs2.add(meanMinus2stdomData);
		funcs2.add(meanPlus2stdomData);
		ArrayList<PlotCurveCharacterstics> plotCharList = new ArrayList<PlotCurveCharacterstics>();
		plotCharList.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.red));

		
		GraphWindow sectGraph5 = new GraphWindow(funcs2, "CharFactor vs SupraRate", plotCharList); 
		sectGraph5.setY_AxisLabel("CharFactor");
		sectGraph5.setX_AxisLabel("SupraRate (per year)");
		sectGraph5.setXLog(true);
		sectGraph5.setYLog(true);
		sectGraph5.setAxisLabelFontSize(24);
		sectGraph5.setTickLabelFontSize(22);
		sectGraph5.setPlotLabelFontSize(26);
		File fileName3 = new File(resultsDir,"charValSupraRatesVsSupraRate.pdf");
		sectGraph5.saveAsPDF(fileName3.getAbsolutePath());
		

		
//		GraphWindow sectGraph2 = new GraphWindow(charValSupraRatesDist, "charValSupraRatesDist"); 
//		sectGraph.setX_AxisLabel("CharFactor");
//		sectGraph.setY_AxisLabel("Weight");
//		GraphWindow sectGraph3 = new GraphWindow(charValSupraRatesDistMoRateWted, "charValSupraRatesDistMoRateWted"); 
//		sectGraph.setX_AxisLabel("CharFactor");
//		sectGraph.setY_AxisLabel("Weight");
	

		String name = "ImpliedCharFactorForSubSections_"+nameSuffix;
		String title = "Log10(CharFactor)";
		CPT cpt= FaultBasedMapGen.getLogRatioCPT().rescale(-2, 2);
		
		FaultBasedMapGen.makeFaultPlot(cpt, FaultBasedMapGen.getTraces(faults), values, origGriddedRegion, resultsDir, name, display, false, title);
		
	}
	


	
	
	public void plotCharFactorStats(File resultsDir)  {

		if(!resultsDir.exists())
			resultsDir.mkdir();
		
		List<FaultSectionPrefData> faults = fssERF.getSolution().getRupSet().getFaultSectionDataList();
		double[] charFactorNumPrimaryArray = new double[faults.size()];
		double[] charFactorSupraRatesArray = new double[faults.size()];
		double[] supraRatesArray = new double[faults.size()];
		
		DefaultXY_DataSet logCharFactorNumPrimaryVsLogSupraRate = new DefaultXY_DataSet();
		DefaultXY_DataSet logCharFactorSupraSeisVsLogSupraRate = new DefaultXY_DataSet();

		
		// Make sure long-term MFDs are created
		makeLongTermSectMFDs();
		
		SummedMagFreqDist totSubMFD = new SummedMagFreqDist(2.55, 8.95, 65);
		SummedMagFreqDist totSupraMFD = new SummedMagFreqDist(2.55, 8.95, 65);
		
		HashMap<String,SummedMagFreqDist> parentSectSubSeisMFD_Map = new HashMap<String,SummedMagFreqDist>();
		HashMap<String,SummedMagFreqDist> parentSectSupraSeisMFD_Map = new HashMap<String,SummedMagFreqDist>();
		
		for(int sectIndex=0;sectIndex<charFactorNumPrimaryArray.length;sectIndex++) {
			String name = this.rupSet.getFaultSectionData(sectIndex).getParentSectionName();
			if(!parentSectSubSeisMFD_Map.containsKey(name))
				parentSectSubSeisMFD_Map.put(name, new SummedMagFreqDist(2.55, 8.95, 65));
			if(!parentSectSupraSeisMFD_Map.containsKey(name))
				parentSectSupraSeisMFD_Map.put(name, new SummedMagFreqDist(2.55, 8.95, 65));
		}
		
		
//		FileWriter fileWriterGMT = new FileWriter(new File(resultsDir, "GRcorrStatsData.csv"));
//		fileWriterGMT.write("sectID,1.0/GRcor1.0/GRcorrSupraRates,tsectName\n");

		for(int sectIndex=0;sectIndex<charFactorNumPrimaryArray.length;sectIndex++) {
			if (longTermSupraSeisMFD_OnSectArray[sectIndex].getMaxY() == 0d ||  longTermSubSeisMFD_OnSectList.get(sectIndex).getMaxY() == 0d) {
				charFactorNumPrimaryArray[sectIndex] = Double.NaN;
				charFactorSupraRatesArray[sectIndex] = Double.NaN;
				supraRatesArray[sectIndex] = Double.NaN;
			}
			else {
				charFactorNumPrimaryArray[sectIndex] = Math.log10(1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false));
				charFactorSupraRatesArray[sectIndex] = Math.log10(1.0/ETAS_Utils.getScalingFactorToImposeGR_supraRates(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false));
				double minSupraMag = ETAS_Utils.getMinMagSupra(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex));
				supraRatesArray[sectIndex] = longTermSupraSeisMFD_OnSectArray[sectIndex].getCumRate(minSupraMag);
				logCharFactorNumPrimaryVsLogSupraRate.set(Math.log10(supraRatesArray[sectIndex]),charFactorNumPrimaryArray[sectIndex]);
				logCharFactorSupraSeisVsLogSupraRate.set(Math.log10(supraRatesArray[sectIndex]),charFactorSupraRatesArray[sectIndex]);
				
			}
			totSubMFD.addIncrementalMagFreqDist(longTermSubSeisMFD_OnSectList.get(sectIndex));
			totSupraMFD.addIncrementalMagFreqDist(longTermSupraSeisMFD_OnSectArray[sectIndex]);
			String name = this.rupSet.getFaultSectionData(sectIndex).getParentSectionName();
			parentSectSubSeisMFD_Map.get(name).addIncrementalMagFreqDist(longTermSubSeisMFD_OnSectList.get(sectIndex));
			parentSectSupraSeisMFD_Map.get(name).addIncrementalMagFreqDist(longTermSupraSeisMFD_OnSectArray[sectIndex]);
		}
		
//		fileWriterGMT.close();
		
		HistogramFunction histNumPrimary_SupraRateWted = new HistogramFunction(-3.,3.,61);
		HistogramFunction histSupraRates_SupraRateWted = new HistogramFunction(-3.,3.,61);
		HistogramFunction histNumPrimary = new HistogramFunction(-3.,3.,61);
		HistogramFunction histSupraRates = new HistogramFunction(-3.,3.,61);
		for(int sectIndex=0;sectIndex<charFactorNumPrimaryArray.length;sectIndex++) {
			if(!Double.isNaN(supraRatesArray[sectIndex])){
				histNumPrimary_SupraRateWted.add(charFactorNumPrimaryArray[sectIndex], supraRatesArray[sectIndex]);
				histSupraRates_SupraRateWted.add(charFactorSupraRatesArray[sectIndex], supraRatesArray[sectIndex]);
				histNumPrimary.add(charFactorNumPrimaryArray[sectIndex], 1d);
				histSupraRates.add(charFactorSupraRatesArray[sectIndex], 1d);				
			}
		}
		
		histNumPrimary_SupraRateWted.normalizeBySumOfY_Vals();
		histNumPrimary_SupraRateWted.setName("histNumPrimary_SupraRateWted");
		HistogramFunction histNumPrimary_SupraRateWtedCum = histNumPrimary_SupraRateWted.getCumulativeDistFunctionWithHalfBinOffset();
		histNumPrimary_SupraRateWtedCum.setName("histNumPrimary_SupraRateWtedCum");
//System.out.println(histNumPrimary_SupraRateWted);
		double median = histNumPrimary_SupraRateWtedCum.getFirstInterpolatedX(0.5);
		String info = "mean="+Math.pow(10d, histNumPrimary_SupraRateWted.computeMean())+"; mode="+Math.pow(10d, histNumPrimary_SupraRateWted.getMode())+"; median="+Math.pow(10d, median);
		histNumPrimary_SupraRateWted.setInfo(info);
		histNumPrimary_SupraRateWtedCum.setInfo(info);
		
		histSupraRates_SupraRateWted.normalizeBySumOfY_Vals();;
		histSupraRates_SupraRateWted.setName("histSupraRates_SupraRateWted");
		HistogramFunction histSupraRates_SupraRateWtedCum = histSupraRates_SupraRateWted.getCumulativeDistFunctionWithHalfBinOffset();
		histSupraRates_SupraRateWtedCum.setName("histSupraRates_SupraRateWtedCum");
		median = histSupraRates_SupraRateWtedCum.getFirstInterpolatedX(0.5);
		info = "Stats converted out of log space:\ngeom mean="+Math.pow(10d, histSupraRates_SupraRateWted.computeMean())+"; mode="+Math.pow(10d, histSupraRates_SupraRateWted.getMode())+"; median="+Math.pow(10d, median);
		histSupraRates_SupraRateWted.setInfo(info);
		histSupraRates_SupraRateWtedCum.setInfo(info);
		ArrayList<EvenlyDiscretizedFunc> funcs1 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs1.add(histNumPrimary_SupraRateWted);
		funcs1.add(histNumPrimary_SupraRateWtedCum);
		funcs1.add(histSupraRates_SupraRateWted);
		funcs1.add(histSupraRates_SupraRateWtedCum);
		GraphWindow graph = new GraphWindow(funcs1, "CharFactor Stats SupraRate Wted"); 
		graph.setX_AxisLabel("Log10-CharFactor");
		graph.setY_AxisLabel("Density");
		
		histNumPrimary.normalizeBySumOfY_Vals();
		histNumPrimary.setName("histNumPrimary");
		HistogramFunction histNumPrimaryCum = histNumPrimary.getCumulativeDistFunctionWithHalfBinOffset();
		histNumPrimaryCum.setName("histNumPrimaryCum");
		median = histNumPrimaryCum.getFirstInterpolatedX(0.5);
		info = "Stats converted out of log space:\ngeom mean="+Math.pow(10d, histNumPrimary.computeMean())+"; mode="+Math.pow(10d, histNumPrimary.getMode())+"; median="+Math.pow(10d, median);
		histNumPrimary.setInfo(info);
		histNumPrimaryCum.setInfo(info);
		
		histSupraRates.normalizeBySumOfY_Vals();;
		histSupraRates.setName("histSupraRates");
		HistogramFunction histSupraRatesCum = histSupraRates.getCumulativeDistFunctionWithHalfBinOffset();
		histSupraRatesCum.setName("histSupraRatesCum");
		median = histSupraRatesCum.getFirstInterpolatedX(0.5);
		info = "Stats converted out of log space:\ngeom mean="+Math.pow(10d, histSupraRates.computeMean())+"; mode="+Math.pow(10d, histSupraRates.getMode())+"; median="+Math.pow(10d, median);
		histSupraRates.setInfo(info);
		histSupraRatesCum.setInfo(info);
		ArrayList<EvenlyDiscretizedFunc> funcs2 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs2.add(histNumPrimary);
		funcs2.add(histNumPrimaryCum);
		funcs2.add(histSupraRates);
		funcs2.add(histSupraRatesCum);
		GraphWindow graph4 = new GraphWindow(funcs2, "CharFactor Stats"); 
		graph4.setX_AxisLabel("Log10-GRcorr");
		graph4.setY_AxisLabel("Density");



		GraphWindow graph2 = new GraphWindow(logCharFactorNumPrimaryVsLogSupraRate, "CharFactorNumPrimary vs SupraRate", 
				new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE)); 
		graph2.setY_AxisLabel("Log10-CharFactor");
		graph2.setX_AxisLabel("Log10(SupraRate)");

		GraphWindow graph3 = new GraphWindow(logCharFactorSupraSeisVsLogSupraRate, "CharFactorSupraRates vs SupraRate", 
				new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE)); 
		graph3.setY_AxisLabel("Log10-CharFactor");
		graph3.setX_AxisLabel("Log10(SupraRate)");
		
		
		IncrementalMagFreqDist tempSubMFD = new IncrementalMagFreqDist(2.55, 8.95, 65);
		IncrementalMagFreqDist tempSupraMFD = new IncrementalMagFreqDist(2.55, 8.95, 65);

		for(int i=0;i<totSupraMFD.size();i++) {
			if(totSupraMFD.getX(i)<6.3)	// cut each at the average seismogenic mag
				tempSubMFD.set(i,totSubMFD.getY(i));
			else
				tempSupraMFD.set(i,totSupraMFD.getY(i));
		}
		double totCharFactorNumPrimary = 1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(tempSupraMFD, tempSubMFD, false);
		double totCharFactorNumPrimary2 = 1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(totSupraMFD, totSubMFD, false);
		double totCharFactorSupraRates = 1.0/ETAS_Utils.getScalingFactorToImposeGR_supraRates(tempSupraMFD, tempSubMFD, false);
		info = "totCharFactorNumPrimary="+totCharFactorNumPrimary+"\ntotCharFactorSupraRates="+totCharFactorSupraRates+"\ntotCharFactorNumPrimary2="+totCharFactorNumPrimary2;
		double minMag=2.55;
		double maxMag=totSupraMFD.getMaxMagWithNonZeroRate();
		int numMag = (int)Math.round((maxMag-minMag)/totSupraMFD.getDelta()) + 1;
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(1.0, 1.0, minMag, maxMag, numMag);
		gr.scaleToIncrRate(minMag, totSubMFD.getY(minMag));
		gr.setName("Perfect GR");
		totSubMFD.setInfo(info);
		totSupraMFD.setInfo(info);
		
		SummedMagFreqDist totalTrulyOffFaultMFD = new SummedMagFreqDist(2.55, 8.95, 65);
		SummedMagFreqDist totalModelMFD = new SummedMagFreqDist(2.55, 8.95, 65);
		for(int src=fssERF.getNumFaultSystemSources();src<fssERF.getNumSources();src++)
			if(mfdForTrulyOffOnlyArray[src] != null)
				totalTrulyOffFaultMFD.addIncrementalMagFreqDist(mfdForTrulyOffOnlyArray[src]);
		totalModelMFD.addIncrementalMagFreqDist(totalTrulyOffFaultMFD);
		totalModelMFD.addIncrementalMagFreqDist(totSubMFD);
		totalModelMFD.addIncrementalMagFreqDist(totSupraMFD);
		totalModelMFD.setName("totalModelMFD");
		totalTrulyOffFaultMFD.setName("totalTrulyOffFaultMFD");

		ArrayList<EvenlyDiscretizedFunc> totalFuncs = new ArrayList<EvenlyDiscretizedFunc>();
		totalFuncs.add(totSubMFD);
		totalFuncs.add(totSupraMFD);
		totalFuncs.add(gr);
		totalFuncs.add(totSubMFD.getCumRateDistWithOffset());
		totalFuncs.add(totSupraMFD.getCumRateDistWithOffset());
		totalFuncs.add(gr.getCumRateDistWithOffset());
		totalFuncs.add(totalModelMFD.getCumRateDistWithOffset());
		GraphWindow totMFD_Graph = new GraphWindow(totalFuncs, "Total MFDs"); 
		totMFD_Graph.setYLog(true);
		
		
		
		ArbDiscrEmpiricalDistFunc parSectCharFactorDistSupraRateWted = new ArbDiscrEmpiricalDistFunc();
		ArbDiscrEmpiricalDistFunc parSectCharFactorDist = new ArbDiscrEmpiricalDistFunc();
		DefaultXY_DataSet parSectCharFactorVsMomentRateData = new DefaultXY_DataSet();
		DefaultXY_DataSet parSectCharFactorVsSupraRateData = new DefaultXY_DataSet();
		System.out.println("name\tcharFactorSurpaRates\tcharFactorNumPrimary\tsupraRate\tmoRate");

		for(String name : parentSectSupraSeisMFD_Map.keySet()) {
			double parGRcorrSupraRates = ETAS_Utils.getScalingFactorToImposeGR_supraRates(parentSectSupraSeisMFD_Map.get(name), parentSectSubSeisMFD_Map.get(name), false);
			double parGRcorr = ETAS_Utils.getScalingFactorToImposeGR_numPrimary(parentSectSupraSeisMFD_Map.get(name), parentSectSubSeisMFD_Map.get(name), false);
			double moRate= parentSectSupraSeisMFD_Map.get(name).getTotalMomentRate() + parentSectSubSeisMFD_Map.get(name).getTotalMomentRate();
			double minMagSupra = ETAS_Utils.getMinMagSupra(parentSectSupraSeisMFD_Map.get(name), parentSectSubSeisMFD_Map.get(name));
			double supraRate = parentSectSupraSeisMFD_Map.get(name).getCumRate(minMagSupra);
			parSectCharFactorDist.set(1/parGRcorrSupraRates,1.0);
			parSectCharFactorDistSupraRateWted.set(1/parGRcorrSupraRates,minMagSupra);
			parSectCharFactorVsMomentRateData.set(moRate,1.0/parGRcorrSupraRates);
			parSectCharFactorVsSupraRateData.set(supraRate, 1.0/parGRcorrSupraRates);
			System.out.println(name+"\t"+(1.0/parGRcorrSupraRates)+"\t"+(1.0/parGRcorr)+"\t"+supraRate+"\t"+moRate);
		}
		ArbitrarilyDiscretizedFunc parSectCharFactorDistSupraRateWtedCum = parSectCharFactorDistSupraRateWted.getCumDist();
		parSectCharFactorDistSupraRateWtedCum.scale(1.0/parSectCharFactorDistSupraRateWted.calcSumOfY_Vals());
		parSectCharFactorDistSupraRateWtedCum.setName("parSectCharFactorDistSupraRateWtedCum");
		parSectCharFactorDistSupraRateWtedCum.setInfo("mean="+parSectCharFactorDistSupraRateWted.getMean()+"; median="+parSectCharFactorDistSupraRateWted.getMedian()); //+"; mode="+parSectDistGRcorr.getMode());

		ArbitrarilyDiscretizedFunc parSectCharFactorDistCum = parSectCharFactorDist.getCumDist();
		parSectCharFactorDistCum.scale(1.0/parSectCharFactorDist.calcSumOfY_Vals());
		parSectCharFactorDistCum.setName("parSectCharFactorDistCum");
		parSectCharFactorDistCum.setInfo("mean="+parSectCharFactorDist.getMean()+"; median="+parSectCharFactorDist.getMedian()); // +"; mode="+parSectDistGRcorrSupraRates.getMode());

		ArrayList<ArbitrarilyDiscretizedFunc> parSectFuncs = new ArrayList<ArbitrarilyDiscretizedFunc>();
		parSectFuncs.add(parSectCharFactorDistSupraRateWtedCum);
		parSectFuncs.add(parSectCharFactorDistCum);
		GraphWindow parSectGraph = new GraphWindow(parSectFuncs, "Parent Sect CharFactor Stats"); 
		parSectGraph.setX_AxisLabel("CharFactor");
		parSectGraph.setY_AxisLabel("Cumulative Density");
		parSectGraph.setXLog(true);
		parSectGraph.setAxisLabelFontSize(20);
		parSectGraph.setTickLabelFontSize(18);
		parSectGraph.setPlotLabelFontSize(22);

		GraphWindow parSectGrCorrVsMoRateGraph = new GraphWindow(parSectCharFactorVsMomentRateData, "parSectCharFactorVsMomentRateData",new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK)); 
		parSectGrCorrVsMoRateGraph.setY_AxisLabel("CharFactor");
		parSectGrCorrVsMoRateGraph.setX_AxisLabel("Moment Rate (NM/yr)");
		parSectGrCorrVsMoRateGraph.setXLog(true);
		parSectGrCorrVsMoRateGraph.setYLog(true);
		parSectGrCorrVsMoRateGraph.setAxisLabelFontSize(20);
		parSectGrCorrVsMoRateGraph.setTickLabelFontSize(18);
		parSectGrCorrVsMoRateGraph.setPlotLabelFontSize(22);

		GraphWindow parSectGrCorrVsSupraRateGraph = new GraphWindow(parSectCharFactorVsSupraRateData, "parSectCharFactorVsSupraRateData",new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK)); 
		parSectGrCorrVsSupraRateGraph.setY_AxisLabel("CharFactor");
		parSectGrCorrVsSupraRateGraph.setX_AxisLabel("SupraRate");
		parSectGrCorrVsSupraRateGraph.setXLog(true);
		parSectGrCorrVsSupraRateGraph.setYLog(true);
		parSectGrCorrVsSupraRateGraph.setAxisLabelFontSize(20);
		parSectGrCorrVsSupraRateGraph.setTickLabelFontSize(18);
		parSectGrCorrVsSupraRateGraph.setPlotLabelFontSize(22);


	}

	
	/**
	 * This one is based on moment rate weights
	 * @param resultsDir
	 */
	public void OLDplotCharFactorStats(File resultsDir)  {

		if(!resultsDir.exists())
			resultsDir.mkdir();
		
		List<FaultSectionPrefData> faults = fssERF.getSolution().getRupSet().getFaultSectionDataList();
		double[] charFactorNumPrimaryArray = new double[faults.size()];
		double[] charFactorSupraRatesArray = new double[faults.size()];
		double[] momentRates = new double[faults.size()];
		
		DefaultXY_DataSet logCharFactorNumPrimaryVsLogMomentRate = new DefaultXY_DataSet();
		DefaultXY_DataSet logCharFactorSupraSeisVsLogMomentRate = new DefaultXY_DataSet();

		
		// Make sure long-term MFDs are created
		makeLongTermSectMFDs();
		
		SummedMagFreqDist totSubMFD = new SummedMagFreqDist(2.55, 8.95, 65);
		SummedMagFreqDist totSupraMFD = new SummedMagFreqDist(2.55, 8.95, 65);
		
		HashMap<String,SummedMagFreqDist> parentSectSubSeisMFD_Map = new HashMap<String,SummedMagFreqDist>();
		HashMap<String,SummedMagFreqDist> parentSectSupraSeisMFD_Map = new HashMap<String,SummedMagFreqDist>();
		
		for(int sectIndex=0;sectIndex<charFactorNumPrimaryArray.length;sectIndex++) {
			String name = this.rupSet.getFaultSectionData(sectIndex).getParentSectionName();
			if(!parentSectSubSeisMFD_Map.containsKey(name))
				parentSectSubSeisMFD_Map.put(name, new SummedMagFreqDist(2.55, 8.95, 65));
			if(!parentSectSupraSeisMFD_Map.containsKey(name))
				parentSectSupraSeisMFD_Map.put(name, new SummedMagFreqDist(2.55, 8.95, 65));
		}
		
		
//		FileWriter fileWriterGMT = new FileWriter(new File(resultsDir, "GRcorrStatsData.csv"));
//		fileWriterGMT.write("sectID,1.0/GRcor1.0/GRcorrSupraRates,tsectName\n");

		for(int sectIndex=0;sectIndex<charFactorNumPrimaryArray.length;sectIndex++) {
			charFactorNumPrimaryArray[sectIndex] = Math.log10(1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false));
			charFactorSupraRatesArray[sectIndex] = Math.log10(1.0/ETAS_Utils.getScalingFactorToImposeGR_supraRates(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false));
			momentRates[sectIndex] = faults.get(sectIndex).calcMomentRate(true);
			logCharFactorNumPrimaryVsLogMomentRate.set(Math.log10(momentRates[sectIndex]),charFactorNumPrimaryArray[sectIndex]);
			logCharFactorSupraSeisVsLogMomentRate.set(Math.log10(momentRates[sectIndex]),charFactorSupraRatesArray[sectIndex]);
			totSubMFD.addIncrementalMagFreqDist(longTermSubSeisMFD_OnSectList.get(sectIndex));
			totSupraMFD.addIncrementalMagFreqDist(longTermSupraSeisMFD_OnSectArray[sectIndex]);
			String name = this.rupSet.getFaultSectionData(sectIndex).getParentSectionName();
			parentSectSubSeisMFD_Map.get(name).addIncrementalMagFreqDist(longTermSubSeisMFD_OnSectList.get(sectIndex));
			parentSectSupraSeisMFD_Map.get(name).addIncrementalMagFreqDist(longTermSupraSeisMFD_OnSectArray[sectIndex]);
		}
		
//		fileWriterGMT.close();
		
		HistogramFunction histNumPrimary_MoRateWted = new HistogramFunction(-3.,3.,61);
		HistogramFunction histSupraRates_MoRateWted = new HistogramFunction(-3.,3.,61);
		HistogramFunction histNumPrimary = new HistogramFunction(-3.,3.,61);
		HistogramFunction histSupraRates = new HistogramFunction(-3.,3.,61);
		for(int sectIndex=0;sectIndex<charFactorNumPrimaryArray.length;sectIndex++) {
			histNumPrimary_MoRateWted.add(charFactorNumPrimaryArray[sectIndex], momentRates[sectIndex]);
			histSupraRates_MoRateWted.add(charFactorSupraRatesArray[sectIndex], momentRates[sectIndex]);
			histNumPrimary.add(charFactorNumPrimaryArray[sectIndex], 1d);
			histSupraRates.add(charFactorSupraRatesArray[sectIndex], 1d);
		}
		
		histNumPrimary_MoRateWted.normalizeBySumOfY_Vals();
		histNumPrimary_MoRateWted.setName("histNumPrimary_MoRateWted");
		HistogramFunction histNumPrimary_MoRateWtedCum = histNumPrimary_MoRateWted.getCumulativeDistFunctionWithHalfBinOffset();
		histNumPrimary_MoRateWtedCum.setName("histNumPrimary_MoRateWtedCum");
		double median = histNumPrimary_MoRateWtedCum.getFirstInterpolatedX(0.5);
		String info = "mean="+Math.pow(10d, histNumPrimary_MoRateWted.computeMean())+"; mode="+Math.pow(10d, histNumPrimary_MoRateWted.getMode())+"; median="+Math.pow(10d, median);
		histNumPrimary_MoRateWted.setInfo(info);
		histNumPrimary_MoRateWtedCum.setInfo(info);
		
		histSupraRates_MoRateWted.normalizeBySumOfY_Vals();;
		histSupraRates_MoRateWted.setName("histSupraRates_MoRateWted");
		HistogramFunction histSupraRates_MoRateWtedCum = histSupraRates_MoRateWted.getCumulativeDistFunctionWithHalfBinOffset();
		histSupraRates_MoRateWtedCum.setName("histSupraRates_MoRateWtedCum");
		median = histSupraRates_MoRateWtedCum.getFirstInterpolatedX(0.5);
		info = "mean="+Math.pow(10d, histSupraRates_MoRateWted.computeMean())+"; mode="+Math.pow(10d, histSupraRates_MoRateWted.getMode())+"; median="+Math.pow(10d, median);
		histSupraRates_MoRateWted.setInfo(info);
		histSupraRates_MoRateWtedCum.setInfo(info);
		ArrayList<EvenlyDiscretizedFunc> funcs1 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs1.add(histNumPrimary_MoRateWted);
		funcs1.add(histNumPrimary_MoRateWtedCum);
		funcs1.add(histSupraRates_MoRateWted);
		funcs1.add(histSupraRates_MoRateWtedCum);
		GraphWindow graph = new GraphWindow(funcs1, "CharFactor Stats MoRate Wted"); 
		graph.setX_AxisLabel("Log10-CharFactor");
		graph.setY_AxisLabel("Density");
		
		histNumPrimary.normalizeBySumOfY_Vals();
		histNumPrimary.setName("histNumPrimary");
		HistogramFunction histNumPrimaryCum = histNumPrimary.getCumulativeDistFunctionWithHalfBinOffset();
		histNumPrimaryCum.setName("histNumPrimaryCum");
		median = histNumPrimaryCum.getFirstInterpolatedX(0.5);
		info = "Stats converted our of log space:\ngeom mean="+Math.pow(10d, histNumPrimary.computeMean())+"; mode="+Math.pow(10d, histNumPrimary.getMode())+"; median="+Math.pow(10d, median);
		histNumPrimary.setInfo(info);
		histNumPrimaryCum.setInfo(info);
		
		histSupraRates.normalizeBySumOfY_Vals();;
		histSupraRates.setName("histSupraRates");
		HistogramFunction histSupraRatesCum = histSupraRates.getCumulativeDistFunctionWithHalfBinOffset();
		histSupraRatesCum.setName("histSupraRatesCum");
		median = histSupraRatesCum.getFirstInterpolatedX(0.5);
		info = "Stats converted our of log space:\ngeom mean="+Math.pow(10d, histSupraRates.computeMean())+"; mode="+Math.pow(10d, histSupraRates.getMode())+"; median="+Math.pow(10d, median);
		histSupraRates.setInfo(info);
		histSupraRatesCum.setInfo(info);
		ArrayList<EvenlyDiscretizedFunc> funcs2 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs2.add(histNumPrimary);
		funcs2.add(histNumPrimaryCum);
		funcs2.add(histSupraRates);
		funcs2.add(histSupraRatesCum);
		GraphWindow graph4 = new GraphWindow(funcs2, "CharFactor Stats"); 
		graph4.setX_AxisLabel("Log10-GRcorr");
		graph4.setY_AxisLabel("Density");



		GraphWindow graph2 = new GraphWindow(logCharFactorNumPrimaryVsLogMomentRate, "CharFactor NumPrimary vs MoRate", 
				new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE)); 
		graph2.setY_AxisLabel("Log10-CharFactor");
		graph2.setX_AxisLabel("Log10(MoRate)");

		GraphWindow graph3 = new GraphWindow(logCharFactorSupraSeisVsLogMomentRate, "CharFactor SupraRates vs MoRate", 
				new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE)); 
		graph3.setY_AxisLabel("Log10-CharFactor");
		graph3.setX_AxisLabel("Log10(MoRate)");
		
		
		IncrementalMagFreqDist tempSubMFD = new IncrementalMagFreqDist(2.55, 8.95, 65);
		IncrementalMagFreqDist tempSupraMFD = new IncrementalMagFreqDist(2.55, 8.95, 65);

		for(int i=0;i<totSupraMFD.size();i++) {
			if(totSupraMFD.getX(i)<6.3)	// cut each at the average seismogenic mag
				tempSubMFD.set(i,totSubMFD.getY(i));
			else
				tempSupraMFD.set(i,totSupraMFD.getY(i));
		}
		double totCharFactorNumPrimary = 1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(tempSupraMFD, tempSubMFD, false);
		double totCharFactorNumPrimary2 = 1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(totSupraMFD, totSubMFD, false);
		double totCharFactorSupraRates = 1.0/ETAS_Utils.getScalingFactorToImposeGR_supraRates(tempSupraMFD, tempSubMFD, false);
		info = "totCharFactorNumPrimary="+totCharFactorNumPrimary+"\ntotCharFactorSupraRates="+totCharFactorSupraRates+"\ntotCharFactorNumPrimary2="+totCharFactorNumPrimary2;
		double minMag=2.55;
		double maxMag=totSupraMFD.getMaxMagWithNonZeroRate();
		int numMag = (int)Math.round((maxMag-minMag)/totSupraMFD.getDelta()) + 1;
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(1.0, 1.0, minMag, maxMag, numMag);
		gr.scaleToIncrRate(minMag, totSubMFD.getY(minMag));
		gr.setName("Perfect GR");
		totSubMFD.setInfo(info);
		totSupraMFD.setInfo(info);
		
		SummedMagFreqDist totalTrulyOffFaultMFD = new SummedMagFreqDist(2.55, 8.95, 65);
		SummedMagFreqDist totalModelMFD = new SummedMagFreqDist(2.55, 8.95, 65);
		for(int src=fssERF.getNumFaultSystemSources();src<fssERF.getNumSources();src++)
			if(mfdForTrulyOffOnlyArray[src] != null)
				totalTrulyOffFaultMFD.addIncrementalMagFreqDist(mfdForTrulyOffOnlyArray[src]);
		totalModelMFD.addIncrementalMagFreqDist(totalTrulyOffFaultMFD);
		totalModelMFD.addIncrementalMagFreqDist(totSubMFD);
		totalModelMFD.addIncrementalMagFreqDist(totSupraMFD);
		totalModelMFD.setName("totalModelMFD");
		totalTrulyOffFaultMFD.setName("totalTrulyOffFaultMFD");

		ArrayList<EvenlyDiscretizedFunc> totalFuncs = new ArrayList<EvenlyDiscretizedFunc>();
		totalFuncs.add(totSubMFD);
		totalFuncs.add(totSupraMFD);
		totalFuncs.add(gr);
		totalFuncs.add(totSubMFD.getCumRateDistWithOffset());
		totalFuncs.add(totSupraMFD.getCumRateDistWithOffset());
		totalFuncs.add(gr.getCumRateDistWithOffset());
		totalFuncs.add(totalModelMFD.getCumRateDistWithOffset());
		GraphWindow totMFD_Graph = new GraphWindow(totalFuncs, "Total MFDs"); 
		totMFD_Graph.setYLog(true);
		
		
		
		ArbDiscrEmpiricalDistFunc parSectCharFactorDistMoRateWted = new ArbDiscrEmpiricalDistFunc();
		ArbDiscrEmpiricalDistFunc parSectCharFactorDist = new ArbDiscrEmpiricalDistFunc();
		DefaultXY_DataSet parSectCharFactorVsMomentRateData = new DefaultXY_DataSet();
		DefaultXY_DataSet parSectCharFactorVsSupraRateData = new DefaultXY_DataSet();
		System.out.println("name\tcharFactorSurpaRates\tcharFactorNumPrimary");

		for(String name : parentSectSupraSeisMFD_Map.keySet()) {
			double parGRcorrSupraRates = ETAS_Utils.getScalingFactorToImposeGR_supraRates(parentSectSupraSeisMFD_Map.get(name), parentSectSubSeisMFD_Map.get(name), false);
			double parGRcorr = ETAS_Utils.getScalingFactorToImposeGR_numPrimary(parentSectSupraSeisMFD_Map.get(name), parentSectSubSeisMFD_Map.get(name), false);
			double moRate= parentSectSupraSeisMFD_Map.get(name).getTotalMomentRate() + parentSectSubSeisMFD_Map.get(name).getTotalMomentRate();
			parSectCharFactorDist.set(1/parGRcorrSupraRates,1.0);
			parSectCharFactorDistMoRateWted.set(1/parGRcorrSupraRates,moRate);
			parSectCharFactorVsMomentRateData.set(moRate,1.0/parGRcorrSupraRates);
			double minMagSupra = ETAS_Utils.getMinMagSupra(parentSectSupraSeisMFD_Map.get(name), parentSectSubSeisMFD_Map.get(name));
			parSectCharFactorVsSupraRateData.set(parentSectSupraSeisMFD_Map.get(name).getCumRate(minMagSupra), 1.0/parGRcorrSupraRates);
			System.out.println(name+"\t"+(1.0/parGRcorrSupraRates)+"\t"+(1.0/parGRcorr)+"\t"+moRate);
		}
		ArbitrarilyDiscretizedFunc parSectCharFactorDistMoRateWtedCum = parSectCharFactorDistMoRateWted.getCumDist();
		parSectCharFactorDistMoRateWtedCum.scale(1.0/parSectCharFactorDistMoRateWted.calcSumOfY_Vals());
		parSectCharFactorDistMoRateWtedCum.setName("parSectCharFactorDistMoRateWtedCum");
		parSectCharFactorDistMoRateWtedCum.setInfo("mean="+parSectCharFactorDistMoRateWted.getMean()+"; median="+parSectCharFactorDistMoRateWted.getMedian()); //+"; mode="+parSectDistGRcorr.getMode());

		ArbitrarilyDiscretizedFunc parSectCharFactorDistCum = parSectCharFactorDist.getCumDist();
		parSectCharFactorDistCum.scale(1.0/parSectCharFactorDist.calcSumOfY_Vals());
		parSectCharFactorDistCum.setName("parSectCharFactorDistCum");
		parSectCharFactorDistCum.setInfo("mean="+parSectCharFactorDist.getMean()+"; median="+parSectCharFactorDist.getMedian()); // +"; mode="+parSectDistGRcorrSupraRates.getMode());

		ArrayList<ArbitrarilyDiscretizedFunc> parSectFuncs = new ArrayList<ArbitrarilyDiscretizedFunc>();
		parSectFuncs.add(parSectCharFactorDistMoRateWtedCum);
		parSectFuncs.add(parSectCharFactorDistCum);
		GraphWindow parSectGraph = new GraphWindow(parSectFuncs, "Parent Sect CharFactor Stats"); 
		parSectGraph.setX_AxisLabel("CharFactor");
		parSectGraph.setY_AxisLabel("Cumulative Density");
		parSectGraph.setXLog(true);
		parSectGraph.setAxisLabelFontSize(20);
		parSectGraph.setTickLabelFontSize(18);
		parSectGraph.setPlotLabelFontSize(22);

		GraphWindow parSectGrCorrVsMoRateGraph = new GraphWindow(parSectCharFactorVsMomentRateData, "parSectCharFactorVsMomentRateData",new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK)); 
		parSectGrCorrVsMoRateGraph.setY_AxisLabel("Log10(CharFactor)");
		parSectGrCorrVsMoRateGraph.setX_AxisLabel("Log10(Moment Rate (NM/yr))");
		parSectGrCorrVsMoRateGraph.setXLog(true);
		parSectGrCorrVsMoRateGraph.setYLog(true);
		parSectGrCorrVsMoRateGraph.setAxisLabelFontSize(20);
		parSectGrCorrVsMoRateGraph.setTickLabelFontSize(18);
		parSectGrCorrVsMoRateGraph.setPlotLabelFontSize(22);

		GraphWindow parSectGrCorrVsSupraRateGraph = new GraphWindow(parSectCharFactorVsSupraRateData, "parSectCharFactorVsSupraRateData",new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK)); 
		parSectGrCorrVsSupraRateGraph.setY_AxisLabel("CharFactor");
		parSectGrCorrVsSupraRateGraph.setX_AxisLabel("SupraRate");
//		parSectGrCorrVsSupraRateGraph.setXLog(true);
//		parSectGrCorrVsSupraRateGraph.setYLog(true);
		parSectGrCorrVsSupraRateGraph.setAxisLabelFontSize(20);
		parSectGrCorrVsSupraRateGraph.setTickLabelFontSize(18);
		parSectGrCorrVsSupraRateGraph.setPlotLabelFontSize(22);


	}
	
	/**
	 * This plots the primary triggered fault-based ruptures that have more than half of their subsections on the main shock surface,
	 * including only those that constitute up to 80% of the entire fault-triggering probability.  This is meant to show that for
	 * a Poisson model (and grCorr) about 80% of primary fault-based ruptures will be a re-rupture of the main shock surface.  Thresholds
	 * are hard coded and this has not been fully tested for non Poisson/grCorr cases.
	 * 
	 * TODO move this to ETAS_SimAnalysisTools
	 */
	public String plotPrimayEventOverlap(ETAS_EqkRupture mainshock, double[] relSrcProbs, File resultsDir, 
			int numToCheck, String nameSuffix) {
		
		Location firstLocOnMainTr = mainshock.getRuptureSurface().getFirstLocOnUpperEdge();
		Location lastLocOnMainTr = mainshock.getRuptureSurface().getLastLocOnUpperEdge();
		double offsetAimuth = LocationUtils.azimuth(firstLocOnMainTr, lastLocOnMainTr) + 90.; // direction to offset rupture traces

		String info = "";
		if(fssERF != null && mainshock.getFSSIndex() >=0) {
			
			int mainShockFSSindex = mainshock.getFSSIndex();
			List<Integer> mainShockSectIndices = fssERF.getSolution().getRupSet().getSectionsIndicesForRup(mainShockFSSindex);
			
			double totFltSrcProb=0;
			double fltSrcProbArray[] = new double[numFltSystSources];
			for(int srcIndex=0; srcIndex<numFltSystSources; srcIndex++) {
				totFltSrcProb += relSrcProbs[srcIndex];
				fltSrcProbArray[srcIndex]=relSrcProbs[srcIndex];
			}	
			
			int[] topValueIndices = ETAS_SimAnalysisTools.getIndicesForHighestValuesInArray(fltSrcProbArray, numToCheck);
			
			ArrayList<Integer> overlappingSrcIndexList = new ArrayList<Integer>();
			ArrayList<Double> overlappingSrcProbList = new ArrayList<Double>();
			
			double totRelProb=0;
			int num=0;
			for(int srcIndex : topValueIndices) {
				int fssIndex = fssERF.getFltSysRupIndexForSource(srcIndex);
				List<Integer> srcSectIndicides = fssERF.getSolution().getRupSet().getSectionsIndicesForRup(fssIndex);
				List<Integer> common = new ArrayList<Integer>(srcSectIndicides);
				common.retainAll(mainShockSectIndices);
				int numCommon = common.size();
//				double fractCommon1 = (double)numCommon/(double)mainShockSectIncides.size();	// more than half of the main shock
				double fractCommon2 = (double)numCommon/(double)srcSectIndicides.size();	// more than half aftershock is on the main shock
//				if (fractCommon1 >= 0.5 || fractCommon2 >= 0.5) {
				if (fractCommon2 >= 0.5) {
					overlappingSrcIndexList.add(srcIndex);
					double relProb = fltSrcProbArray[srcIndex]/totFltSrcProb;
					overlappingSrcProbList.add(relProb);
					totRelProb += relProb;
					System.out.println(num+"\t"+relProb+"\t"+totRelProb+"\t"+srcIndex+"\t"+erf.getSource(srcIndex).getName());
					num+=1;
				}
			}
			System.out.println("totRelProb=\t"+totRelProb);


			ArrayList<LocationList> tracesList= FaultBasedMapGen.getTraces(rupSet.getFaultSectionDataList());
			ArrayList<Double> valuesList = new ArrayList<Double>();
			for(int i=0;i<tracesList.size();i++)
				valuesList.add(1e-14);	// add very low values for each section
			
			int numToInclude = overlappingSrcIndexList.size();
//			int numToInclude = 50;
			double maxTotWt = 0.8;
			double totForNumIncluded=0;
			for(int i=0;i<numToInclude ;i++) {
				int srcIndex = overlappingSrcIndexList.get(i);
				double relProb = overlappingSrcProbList.get(i);
				totForNumIncluded+=relProb;
				int fssIndex = fssERF.getFltSysRupIndexForSource(srcIndex);
				List<Integer> srcSectIncides = fssERF.getSolution().getRupSet().getSectionsIndicesForRup(fssIndex);
				LocationVector vect = new LocationVector(offsetAimuth, (i+1)*3.0,0.0);
				for(int sectID:srcSectIncides) {
					LocationList trace = tracesList.get(sectID);
					LocationList movedTrace = new LocationList();
					for(Location loc:trace) {
						movedTrace.add(LocationUtils.location(loc, vect));
					}
					tracesList.add(movedTrace);
					valuesList.add(relProb);
				}
				if(totForNumIncluded>maxTotWt)
					break;
			}
			
			// now add main shock trace
			for(int sect:mainShockSectIndices) {
				tracesList.add(tracesList.get(sect));
				valuesList.add(1.0);	
			}
			
			// now add aximuth lines so we can see direction of offset
			LocationVector vect = new LocationVector(offsetAimuth, 200.,0.0);
			LocationList locList1 = new LocationList();
			locList1.add(firstLocOnMainTr);
			locList1.add(LocationUtils.location(firstLocOnMainTr, vect));
			tracesList.add(locList1);
			valuesList.add(2.0);	
			LocationList locList2 = new LocationList();
			locList2.add(lastLocOnMainTr);
			locList2.add(LocationUtils.location(lastLocOnMainTr, vect));
			tracesList.add(locList2);
			valuesList.add(2.0);	

			
			
			double[] valuesArray = new double[valuesList.size()];
			for(int i=0;i<valuesList.size();i++)
				valuesArray[i] = valuesList.get(i);
					
			CPT cpt = FaultBasedMapGen.getParticipationCPT().rescale(-6, -1);
			cpt.setBelowMinColor(Color.GRAY);
			cpt.setAboveMaxColor(Color.BLACK);	// main shock will be black

			// now log space
			double[] values = FaultBasedMapGen.log10(valuesArray);
			
			String name = "OverlappingSections"+nameSuffix;
			String title = "Log10(relProb); totProb="+(float)totForNumIncluded;

			
			try {
				FaultBasedMapGen.makeFaultPlot(cpt, tracesList, values, origGriddedRegion, resultsDir, name, true, false, title);
			} catch (Exception e) {
				e.printStackTrace();
			} 

		}
		else {
			throw new RuntimeException("erf must be instance of FaultSystemSolutionERF");
		}
		return info;
	}
	
	
	
	/**
	 * This plots the primary triggered fault-based ruptures that have 1 or more subsections in common with the main shock surface,
	 * including only those that constitute up to 80% of the entire fault-triggering probability (hard coded).
	 * 
	 * TODO move this to ETAS_SimAnalysisTools
	 */
	public String plotPrimayEventsThatTouchParent(ETAS_EqkRupture mainshock, double[] relSrcProbs, File resultsDir, 
			int numToCheck, String nameSuffix) {
		
		Location firstLocOnMainTr = mainshock.getRuptureSurface().getFirstLocOnUpperEdge();
		Location lastLocOnMainTr = mainshock.getRuptureSurface().getLastLocOnUpperEdge();
		double offsetAimuth = LocationUtils.azimuth(firstLocOnMainTr, lastLocOnMainTr) + 90.; // direction to offset rupture traces

		String info = "";
		if(fssERF != null && mainshock.getFSSIndex() >=0) {
			
			int mainShockFSSindex = mainshock.getFSSIndex();
			List<Integer> mainShockSectIndices = fssERF.getSolution().getRupSet().getSectionsIndicesForRup(mainShockFSSindex);
			
			double totFltSrcProb=0;
			double fltSrcProbArray[] = new double[numFltSystSources];
			for(int srcIndex=0; srcIndex<numFltSystSources; srcIndex++) {
				totFltSrcProb += relSrcProbs[srcIndex];
				fltSrcProbArray[srcIndex]=relSrcProbs[srcIndex];
			}	
			
			int[] topValueIndices = ETAS_SimAnalysisTools.getIndicesForHighestValuesInArray(fltSrcProbArray, numToCheck);
			
			ArrayList<Integer> overlappingSrcIndexList = new ArrayList<Integer>();
			ArrayList<Double> overlappingSrcProbList = new ArrayList<Double>();
			
			double maxTotProb = 0.5;
			double totCondProb=0;
			int num=0;
			for(int srcIndex : topValueIndices) {
				int fssIndex = fssERF.getFltSysRupIndexForSource(srcIndex);
				List<Integer> srcSectIndicides = fssERF.getSolution().getRupSet().getSectionsIndicesForRup(fssIndex);
				List<Integer> common = new ArrayList<Integer>(srcSectIndicides);
				common.retainAll(mainShockSectIndices);
				int numCommon = common.size();
				if (numCommon > 0) {
					num += 1;
					overlappingSrcIndexList.add(srcIndex);
					double condProb = fltSrcProbArray[srcIndex]/totFltSrcProb;
					overlappingSrcProbList.add(condProb);
					totCondProb += condProb;
					System.out.println(num+"\t"+condProb+"\t"+totCondProb+"\t"+srcIndex+"\t"+erf.getSource(srcIndex).getName());
				}
//				if(totCondProb>maxTotProb || num>200)
					if(num>200)
					break;
			}
			System.out.println("totCondProb=\t"+totCondProb+"\tnum="+overlappingSrcIndexList.size());


			ArrayList<LocationList> tracesList= FaultBasedMapGen.getTraces(rupSet.getFaultSectionDataList());
			ArrayList<Double> valuesList = new ArrayList<Double>();
			for(int i=0;i<tracesList.size();i++)
				valuesList.add(1e-14);	// add very low values for each section
			
			for(int i=0;i<overlappingSrcIndexList.size() ;i++) {
				int srcIndex = overlappingSrcIndexList.get(i);
				double relProb = overlappingSrcProbList.get(i);
				int fssIndex = fssERF.getFltSysRupIndexForSource(srcIndex);
				List<Integer> srcSectIncides = fssERF.getSolution().getRupSet().getSectionsIndicesForRup(fssIndex);
				LocationVector vect = new LocationVector(offsetAimuth, (i+1)*3.0,0.0);
				for(int sectID:srcSectIncides) {
					LocationList trace = tracesList.get(sectID);
					LocationList movedTrace = new LocationList();
					for(Location loc:trace) {
						movedTrace.add(LocationUtils.location(loc, vect));
					}
					tracesList.add(movedTrace);
					valuesList.add(relProb);
				}
			}
			
			// now add main shock trace
			for(int sect:mainShockSectIndices) {
				tracesList.add(tracesList.get(sect));
				valuesList.add(1.0);	
			}
			
			// now add aximuth lines so we can see direction of offset
			LocationVector vect = new LocationVector(offsetAimuth, 200.,0.0);
			LocationList locList1 = new LocationList();
			locList1.add(firstLocOnMainTr);
			locList1.add(LocationUtils.location(firstLocOnMainTr, vect));
			tracesList.add(locList1);
			valuesList.add(2.0);	
			LocationList locList2 = new LocationList();
			locList2.add(lastLocOnMainTr);
			locList2.add(LocationUtils.location(lastLocOnMainTr, vect));
			tracesList.add(locList2);
			valuesList.add(2.0);	

			
			
			double[] valuesArray = new double[valuesList.size()];
			for(int i=0;i<valuesList.size();i++)
				valuesArray[i] = valuesList.get(i);
					
			CPT cpt = FaultBasedMapGen.getParticipationCPT().rescale(-4.5, -0.5);
			cpt.setBelowMinColor(Color.GRAY);
			cpt.setAboveMaxColor(Color.BLACK);	// main shock will be black

			// now log space
			double[] values = FaultBasedMapGen.log10(valuesArray);
			
			String name = "PrimayRupsThatTouchParent"+nameSuffix;
			String title = "Log10(relProb); totCondProb="+(float)totCondProb;

			
			try {
				FaultBasedMapGen.makeFaultPlot(cpt, tracesList, values, origGriddedRegion, resultsDir, name, true, false, title);
			} catch (Exception e) {
				e.printStackTrace();
			} 

		}
		else {
			throw new RuntimeException("erf must be instance of FaultSystemSolutionERF");
		}
		return info;
	}



	/**
	 * This plots the fault-based ruptures that are most likely to be triggered as primary events
	 * by the given main shock; the number ot plot is specified by numToCheck, and the collective
	 * probability represented by these ruptures is given in the scale legend (the value conditioned
	 * on the fact that some supraseismogenic rupture is triggered)
	 * 
	 * TODO move this to ETAS_SimAnalysisTools
	 */
	public String plotMostLikelyTriggeredFSS_Ruptures(ETAS_EqkRupture mainshock, double[] relSrcProbs, File resultsDir, 
			int numToCheck, String nameSuffix) {
		
		double offsetAimuth;
		Location firstLocOnMainTr;
		Location lastLocOnMainTr;
		List<Integer> mainShockSectIndices = null;
		if(mainshock.getFSSIndex() >=0) {
			mainShockSectIndices = fssERF.getSolution().getRupSet().getSectionsIndicesForRup(mainshock.getFSSIndex());
			firstLocOnMainTr = mainshock.getRuptureSurface().getFirstLocOnUpperEdge();
			lastLocOnMainTr = mainshock.getRuptureSurface().getLastLocOnUpperEdge();
			offsetAimuth = LocationUtils.azimuth(firstLocOnMainTr, lastLocOnMainTr) + 90.; // direction to offset rupture traces			
		}
		// find closest subsection and use it
		else {
			Location loc = mainshock.getRuptureSurface().getFirstLocOnUpperEdge();
			double minDist = 100000;
			int sectIndex = -1;
			for(FaultSectionPrefData fltData:rupSet.getFaultSectionDataList()) {
				double dist = LocationUtils.distanceToSurfFast(loc, fltData.getStirlingGriddedSurface(1.0, false, true));
				if(dist<minDist) {
					minDist = dist;
					sectIndex = fltData.getSectionId();
				}
			}
			StirlingGriddedSurface surf = rupSet.getFaultSectionData(sectIndex).getStirlingGriddedSurface(1.0, false, true);
			firstLocOnMainTr = surf.getFirstLocOnUpperEdge();
			lastLocOnMainTr = surf.getLastLocOnUpperEdge();
			offsetAimuth = LocationUtils.azimuth(firstLocOnMainTr, lastLocOnMainTr) + 90.; // direction to offset rupture traces					
		}

		String info = "";
		if(fssERF != null) {
						
			double totFltSrcProb=0;
			double fltSrcProbArray[] = new double[numFltSystSources];
			for(int srcIndex=0; srcIndex<numFltSystSources; srcIndex++) {
				totFltSrcProb += relSrcProbs[srcIndex];
				fltSrcProbArray[srcIndex]=relSrcProbs[srcIndex];
			}	
			
			int[] topValueIndices = ETAS_SimAnalysisTools.getIndicesForHighestValuesInArray(fltSrcProbArray, numToCheck);
			
			ArrayList<Integer> overlappingSrcIndexList = new ArrayList<Integer>();
			ArrayList<Double> overlappingSrcProbList = new ArrayList<Double>();
			
			int num=0;
			for(int srcIndex : topValueIndices) {
				overlappingSrcIndexList.add(srcIndex);
				double condProb = fltSrcProbArray[srcIndex]/totFltSrcProb;
				overlappingSrcProbList.add(condProb);
				System.out.println(num+"\t"+condProb+"\t"+srcIndex+"\t"+erf.getSource(srcIndex).getName());
				num+=1;
			}


			ArrayList<LocationList> tracesList= FaultBasedMapGen.getTraces(rupSet.getFaultSectionDataList());
			ArrayList<Double> valuesList = new ArrayList<Double>();
			for(int i=0;i<tracesList.size();i++)
				valuesList.add(1e-14);	// add very low values for each section so they plot gray
			
			int numToInclude = overlappingSrcIndexList.size();
			double maxTotWt = 1.01;
			double totCondProbForThoseIncluded=0;
			for(int i=0;i<numToInclude ;i++) {
				int srcIndex = overlappingSrcIndexList.get(i);
				double condProb = overlappingSrcProbList.get(i);
				totCondProbForThoseIncluded+=condProb;
				int fssIndex = fssERF.getFltSysRupIndexForSource(srcIndex);
				List<Integer> srcSectIncides = fssERF.getSolution().getRupSet().getSectionsIndicesForRup(fssIndex);
				LocationVector vect = new LocationVector(offsetAimuth, (i+1)*3.5,0.0);
				for(int sectID:srcSectIncides) {
					LocationList trace = tracesList.get(sectID);
					LocationList movedTrace = new LocationList();
					for(Location loc:trace) {
						movedTrace.add(LocationUtils.location(loc, vect));
					}
					tracesList.add(movedTrace);
					valuesList.add(condProb);
				}
				if(totCondProbForThoseIncluded>maxTotWt)
					break;
			}
			
			// now add main shock trace
			if(mainShockSectIndices != null) {
				for(int sect:mainShockSectIndices) {
					tracesList.add(tracesList.get(sect));
					valuesList.add(2.0);	
				}				
			}
			
			// now add aximuth lines so we can see direction of offset
			LocationVector vect = new LocationVector(offsetAimuth, 200.,0.0);
			LocationList locList1 = new LocationList();
			locList1.add(firstLocOnMainTr);
			locList1.add(LocationUtils.location(firstLocOnMainTr, vect));
			tracesList.add(locList1);
			valuesList.add(2.0);	// anything greater than 1.0
			LocationList locList2 = new LocationList();
			locList2.add(lastLocOnMainTr);
			locList2.add(LocationUtils.location(lastLocOnMainTr, vect));
			tracesList.add(locList2);
			valuesList.add(2.0);	
			
			double[] valuesArray = new double[valuesList.size()];
			for(int i=0;i<valuesList.size();i++)
				valuesArray[i] = valuesList.get(i);
					
			CPT cpt = FaultBasedMapGen.getParticipationCPT().rescale(-4, 0);
			cpt.setBelowMinColor(Color.GRAY);
			cpt.setAboveMaxColor(Color.BLACK);

			// now log space
			double[] values = FaultBasedMapGen.log10(valuesArray);
			
			String name = "MostLikelyFSS_Rups"+nameSuffix;
			String title = "Log10(relProb); totProb="+(float)totCondProbForThoseIncluded;
			
			try {
				FaultBasedMapGen.makeFaultPlot(cpt, tracesList, values, origGriddedRegion, resultsDir, name, true, false, title);
			} catch (Exception e) {
				e.printStackTrace();
			} 

		}
		else {
			throw new RuntimeException("erf must be instance of FaultSystemSolutionERF");
		}
		return info;
	}
			
			
	/**
	 * This plots the relative probability that each subsection will participate
	 * given a primary aftershocks of the supplied mainshock.  This also returns
	 * a String with a list of the top numToList fault-based sources and top sections.
	 * 
	 * TODO move this to ETAS_SimAnalysisTools
	 */
	public String plotSubSectParticipationProbGivenRuptureAndReturnInfo(ETAS_EqkRupture mainshock, double[] relSrcProbs, File resultsDir, 
			int numToList, String nameSuffix, double expNum, boolean isPoisson) {
		String info = "";
		if(erf instanceof FaultSystemSolutionERF) {
			double[] sectProbArray = new double[rupSet.getNumSections()];
			for(int srcIndex=0; srcIndex<numFltSystSources; srcIndex++) {
				int fltSysIndex = fssERF.getFltSysRupIndexForSource(srcIndex);
				for(Integer sectIndex:rupSet.getSectionsIndicesForRup(fltSysIndex)) {
					sectProbArray[sectIndex] += relSrcProbs[srcIndex];
				}
			}			
			
			String name,title;
			if(isPoisson) {
				for(int i=0;i<sectProbArray.length;i++)
					sectProbArray[i] *= expNum;
				name = "SectParticipationExpNumPrimary"+nameSuffix;
				title = "Log10(SectPartExpNumPrimary)";
			}
			else {
				for(int i=0;i<sectProbArray.length;i++)
					sectProbArray[i] = 1 - Math.pow(1.0-sectProbArray[i],expNum);
				name = "SectParticipationProbOneOrMorePrimary"+nameSuffix;
				title = "Log10(SectPartProbOneOrMorePrimary)";
			}
			
			
			CPT cpt = FaultBasedMapGen.getParticipationCPT().rescale(-5, 0);;
			List<FaultSectionPrefData> faults = rupSet.getFaultSectionDataList();

//			// now log space
			double[] values = FaultBasedMapGen.log10(sectProbArray);
			
			
			// this writes all the value to a file
			try {
				FileWriter fr = new FileWriter(new File(resultsDir, name+".txt"));
				for(int s=0; s<sectProbArray.length;s++) {
					fr.write(s +"\t"+(float)sectProbArray[s]+"\t"+faults.get(s).getName()+"\n");
				}
				fr.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			
			try {
				FaultBasedMapGen.makeFaultPlot(cpt, FaultBasedMapGen.getTraces(faults), values, origGriddedRegion, resultsDir, name, true, false, title);
			} catch (Exception e) {
				e.printStackTrace();
			} 
			
			// write out probability of mainshock if it's a fault system source
			if(mainshock.getFSSIndex() >=0 && erf instanceof FaultSystemSolutionERF) {
				info += "\nProbability of sampling the rupture again:\n";
				int srcIndex = ((FaultSystemSolutionERF)erf).getSrcIndexForFltSysRup(mainshock.getFSSIndex());
				info += "\n\t"+relSrcProbs[srcIndex]+"\t"+srcIndex+"\t"+erf.getSource(srcIndex).getName()+"\n";
			}
			
			
			// below creates the string of top values; first sources, then sections			
			
			// list top fault-based ruptures
			double[] fltSrcProbs = new double[this.numFltSystSources];
			double totProb = 0;
			if(isPoisson) {
				for(int i=0;i<fltSrcProbs.length;i++) {
					fltSrcProbs[i]=relSrcProbs[i]*expNum;
					totProb += fltSrcProbs[i];
				}
				info += "\nScenario is most likely to trigger the following fault-based sources (expNum, relExpNum, srcIndex, mag, name):\n\n";
			}
			else {
				for(int i=0;i<fltSrcProbs.length;i++) {
					fltSrcProbs[i]=1.0-Math.pow(1-relSrcProbs[i],expNum);
					totProb += fltSrcProbs[i];
				}
				info += "\nScenario is most likely to trigger the following fault-based sources (prob, relProb, srcIndex, mag, name):\n\n";
			}
			int[] topValueIndices = ETAS_SimAnalysisTools.getIndicesForHighestValuesInArray(fltSrcProbs, numToList);
			for(int srcIndex : topValueIndices) {
				info += "\t"+fltSrcProbs[srcIndex]+"\t"+(fltSrcProbs[srcIndex]/totProb)+"\t"+srcIndex+"\t"+erf.getSource(srcIndex).getRupture(0).getMag()+"\t"+erf.getSource(srcIndex).getName()+"\n";
			}
			
			// list top fault section participations
			topValueIndices = ETAS_SimAnalysisTools.getIndicesForHighestValuesInArray(sectProbArray, numToList);
			info += "\nThe following sections are most likely to participate in a triggered event:\n\n";
			List<FaultSectionPrefData> fltDataList = fssERF.getSolution().getRupSet().getFaultSectionDataList();
			for(int sectIndex :topValueIndices) {
				info += "\t"+sectProbArray[sectIndex]+"\t"+sectIndex+"\t"+fltDataList.get(sectIndex).getName()+"\n";
			}

		}
		else {
			throw new RuntimeException("erf must be instance of FaultSystemSolutionERF");
		}
		return info;
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
		if(this.totalSectRateInCubeArray[aftShCubeIndex] > 0)	// only do this if the cube has sections inside
			fractionSupra = getERT_MinFracSupra(rupToFillIn.getParentRup(), getCubeLocationForIndex(aftShCubeIndex));
		
		int randSrcIndex = getRandomSourceIndexInCube(aftShCubeIndex, fractionSupra);
		
//		// following is needed for case where includeERF_Rates = false (point can be chosen that has no sources)
//		if(randSrcIndex<0) {
////			System.out.println("working on finding a non-neg source index");
//			while (randSrcIndex<0) {
//				aftShCubeIndex = sampler.getRandomInt(etas_utils.getRandomDouble());
//				randSrcIndex = getRandomSourceIndexInCube(aftShCubeIndex);
//			}
//		}
		
		if(randSrcIndex < numFltSystSources) {	// if it's a fault system source
			ProbEqkSource src = erf.getSource(randSrcIndex);
			int r=0;
			if(src.getNumRuptures() > 1) {
				r = src.drawSingleRandomEqkRuptureIndex(etas_utils.getRandomDouble());
			}
			int nthRup = erf.getIndexN_ForSrcAndRupIndices(randSrcIndex,r);
			ProbEqkRupture erf_rup = src.getRupture(r);
			
			// need to choose point on rup surface that is the hypocenter			
			
//			// Old, Old way: collect those inside the cube and choose one randomly
//			LocationList locsOnRupSurf = erf_rup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
//			LocationList locsToSampleFrom = new LocationList();
//			for(Location loc: locsOnRupSurf) {
//				if(aftShCubeIndex == getCubeIndexForLocation(loc)) {
//					locsToSampleFrom.add(loc);
//				}
//			}	
//			if(locsToSampleFrom.size() == 0) {
//				System.out.println("PROBLEM: randSrcIndex="+randSrcIndex+"\tName: = "+src.getName());
//				System.out.println("lat\tlon\tdepth");
//				System.out.println(latForCubeCenter[aftShCubeIndex]+"\t"+lonForCubeCenter[aftShCubeIndex]+"\t"+depthForCubeCenter[aftShCubeIndex]);
//				for(Location loc: locsOnRupSurf) {
//					System.out.println(loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth());
//				}	
//				throw new RuntimeException("problem");
//			}
//			// choose one randomly
//			int hypoLocIndex = etas_utils.getRandomInt(locsToSampleFrom.size()-1);
//			rupToFillIn.setHypocenterLocation(locsToSampleFrom.get(hypoLocIndex));
			
			
			
//			// Old way: choose the closest point on surface as the hypocenter
//			LocationList locsOnRupSurf = erf_rup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
//			Location hypoLoc=null;
//			Location cubeLoc= getCubeLocationForIndex(aftShCubeIndex);
//			double minDist = Double.MAX_VALUE;
//			for(Location loc:locsOnRupSurf) {
//				double dist = LocationUtils.linearDistanceFast(cubeLoc, loc);
//				if(dist<minDist) {
//					hypoLoc = loc;
//					minDist = dist;
//				}	
//			}
//			rupToFillIn.setHypocenterLocation(hypoLoc);
			
			
			// Latest way:
			// this makes it the distence from a vertex (par loc) to cube center, but we could add some randomness like below for
			// gridded seis if we want results to look better (but bad-looking results will remind us about he discretization issues
			// at large mags)
			rupToFillIn.setHypocenterLocation(getCubeLocationForIndex(aftShCubeIndex));


			rupToFillIn.setRuptureSurface(erf_rup.getRuptureSurface());
			rupToFillIn.setFSSIndex(((FaultSystemSolutionERF)erf).getFltSysRupIndexForNthRup(nthRup));
			rupToFillIn.setAveRake(erf_rup.getAveRake());
			rupToFillIn.setMag(erf_rup.getMag());
			rupToFillIn.setNthERF_Index(nthRup);

		}
		else { // it's a gridded seis source
			
			int gridRegionIndex = randSrcIndex-numFltSystSources;
			ProbEqkSource src=null;
			if(origGridSeisTrulyOffVsSubSeisStatus[gridRegionIndex] == 2) {	// it has both truly off and sub-seismo components
				int[] regAndDepIndex = getCubeRegAndDepIndicesForIndex(aftShCubeIndex);
				int isSubSeismo = isCubeInsideFaultPolygon[regAndDepIndex[0]];
				if(isSubSeismo == 1) {
					src = ((FaultSystemSolutionERF)erf).getSourceSubSeisOnly(randSrcIndex);
				}
				else {
					src = ((FaultSystemSolutionERF)erf).getSourceTrulyOffOnly(randSrcIndex);
				}
			}
			else {
				src = erf.getSource(randSrcIndex);
			}
			
			
			int r=0;
			if(src.getNumRuptures() > 1) {
				r = src.drawSingleRandomEqkRuptureIndex(etas_utils.getRandomDouble());
			}
			int nthRup = erf.getIndexN_ForSrcAndRupIndices(randSrcIndex,r);
			ProbEqkRupture erf_rup = src.getRupture(r);

			double relLat = latForCubeCenter[aftShCubeIndex]-translatedParLoc.getLatitude();
			double relLon = lonForCubeCenter[aftShCubeIndex]-translatedParLoc.getLongitude();
			double relDep = depthForCubeCenter[aftShCubeIndex]-translatedParLoc.getDepth();	// TODO why not used (remove or bug?)
			
			rupToFillIn.setGridNodeIndex(randSrcIndex - numFltSystSources);
						
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


			
			// Issue: that last step could have moved the hypocenter outside the grid node of the source (by up to ~1 km);
			// move it back just inside if the new grid node does not go that high enough magnitude
			if(erf instanceof FaultSystemSolutionERF) {
				int gridSrcIndex = randSrcIndex-numFltSystSources;
				Location gridSrcLoc = fssERF.getSolution().getGridSourceProvider().getGriddedRegion().getLocation(gridSrcIndex);
				int testIndex = fssERF.getSolution().getGridSourceProvider().getGriddedRegion().indexForLocation(hypLoc);
				if(testIndex != gridSrcIndex) {
					// check whether hypLoc is now out of region, and return false if so
					if(testIndex == -1)
						return false;
					IncrementalMagFreqDist mfd = fssERF.getSolution().getGridSourceProvider().getNodeMFD(testIndex);
//					if(mfd==null) {
//						throw new RuntimeException("testIndex="+testIndex+"\thypLoc= "+hypLoc+"\tgridLoc= "+tempERF.getSolution().getGridSourceProvider().getGriddedRegion().getLocation(testIndex));
//					}
					int maxMagIndex = mfd.getClosestXIndex(mfd.getMaxMagWithNonZeroRate());
					int magIndex = mfd.getClosestXIndex(erf_rup.getMag());
					double tempLat=hypLoc.getLatitude();
					double tempLon= hypLoc.getLongitude();
					double tempDepth = hypLoc.getDepth();
					double halfGrid=pointSrcDiscr/2.0;
					if(maxMagIndex<magIndex) {
						if(hypLoc.getLatitude()-gridSrcLoc.getLatitude()>=halfGrid)
							tempLat=gridSrcLoc.getLatitude()+halfGrid*0.99;	// 0.99 makes sure it's inside
						else if (hypLoc.getLatitude()-gridSrcLoc.getLatitude()<=-halfGrid)
							tempLat=gridSrcLoc.getLatitude()-halfGrid*0.99;	// 0.99 makes sure it's inside
						if(hypLoc.getLongitude()-gridSrcLoc.getLongitude()>=halfGrid)
							tempLon=gridSrcLoc.getLongitude()+halfGrid*0.99;	// 0.99 makes sure it's inside
						else if (hypLoc.getLongitude()-gridSrcLoc.getLongitude()<=-halfGrid)
							tempLon=gridSrcLoc.getLongitude()-halfGrid*0.99;	// 0.99 makes sure it's inside
						hypLoc = new Location(tempLat,tempLon,tempDepth);
						int testIndex2 = fssERF.getSolution().getGridSourceProvider().getGriddedRegion().indexForLocation(hypLoc);
						if(testIndex2 != gridSrcIndex) {
							throw new RuntimeException("grid problem");
						}
					}
				}
			}
			
			
			rupToFillIn.setHypocenterLocation(hypLoc);
			rupToFillIn.setPointSurface(hypLoc);
			// fill in the rest
			rupToFillIn.setAveRake(erf_rup.getAveRake());
			rupToFillIn.setMag(erf_rup.getMag());
			rupToFillIn.setNthERF_Index(nthRup);

		}
		
		
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
	 * This will force updating of all the samplers and other things
	 */
	public void declareRateChange() {
		if(D)ETAS_SimAnalysisTools.writeMemoryUse("Memory before discarding chached Samplers");
//		cubeSamplerGriddedRatesOnly = null; // don't update because this now never changes (supra rates removed)
		computeSectNucleationRates();
		computeTotSectRateInCubesArray();
		if(mfdForSrcArray != null) {	// if using this array, update only fault system sources
			for(int s=0; s<numFltSystSources;s++) {
				mfdForSrcArray[s] = ERF_Calculator.getTotalMFD_ForSource(erf.getSource(s), erf.getTimeSpan().getDuration(), 5.05, 8.95, 40, true);
			}
		}
		System.gc();
		if(D)ETAS_SimAnalysisTools.writeMemoryUse("Memory after discarding chached Samplers");
	}
	
	
	
	/**
	 * This only includes gridded seismicity rates so these don't have to be stored elsewhere.
	 * Including supra-seismogenic ruptures only increases a few cube rates, with "Surprise Valley 2011 CFM, Subsection 15"
	 * having the maximum of a 3.7% increase, and that's only if it's crazy characteristic MFD is allowed to stay.
	 */
	private synchronized IntegerPDF_FunctionSampler getCubeSamplerWithERF_GriddedRatesOnly() {
		if(cubeSamplerGriddedRatesOnly == null) {
			cubeSamplerGriddedRatesOnly = new IntegerPDF_FunctionSampler(numCubes);
//			double maxRatioTest = 0;
//			int testCubeIndex = -1;
			for(int i=0;i<numCubes;i++) {
// THIS IS NOT CORRECT
//				// compute rate of gridded-seis source in this cube
//				double gridSrcRate=0.0;
//				int griddeSeisRegionIndex = origGriddedRegion.indexForLocation(getCubeLocationForIndex(i));
//				if(griddeSeisRegionIndex != -1)	 {
//					int gridSrcIndex = numFltSystSources + griddeSeisRegionIndex;
//					gridSrcRate = sourceRates[gridSrcIndex]/(numPtSrcSubPts*numPtSrcSubPts*numCubeDepths);	// divide rate among all the cubes in grid cell
//				}
				double gridSrcRate = getGridSourcRateInCube(i,false);
				cubeSamplerGriddedRatesOnly.set(i,gridSrcRate);
				
//				// Comment out the line above and un-comment the following to include supra rates
//				if(gridSrcRate == 0.0) {
//					cubeSamplerGriddedRatesOnly.set(i,gridSrcRate);
//					continue;
//				}
//				double totRate= gridSrcRate; // start with gridded seis rate
//				int[] sections = sectInCubeList.get(i);
//				float[] fracts = fractionSectInCubeList.get(i);
//				for(int j=0; j<sections.length;j++) {
//					totRate += totSectNuclRateArray[sections[j]]*(double)fracts[j];
//				}
//				if(!Double.isNaN(totRate)) {
//					cubeSamplerGriddedRatesOnly.set(i,totRate);
//					if((totRate/gridSrcRate)>maxRatioTest) {
//						maxRatioTest=totRate/gridSrcRate;
//						testCubeIndex=i;
//					}
//				}
//				else {
////					Location loc = this.getCubeLocationForIndex(i);
////					System.out.println("NaN\t"+loc.getLongitude()+"\t"+loc.getLatitude()+"\t"+loc.getDepth());
//					throw new RuntimeException("totRate="+Double.NaN+" for cube "+i+"; "+this.getCubeLocationForIndex(i));
//				}
			}
//System.out.println("HERE maxRatioTest="+maxRatioTest+"\ttestCubeIndex="+testCubeIndex+"\tloc: "+getCubeLocationForIndex(testCubeIndex));
//for(int s:this.sectInCubeList.get(testCubeIndex))
//	System.out.println("\t"+rupSet.getFaultSectionData(s).getName());
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
			if(isCubeInsideFaultPolygon[cubeRegIndex]==1) {
				// need to construct this carefully because not all sections in cell influence all cubes in cell
				int[] sectInCubeArray = sectInCubeList.get(cubeIndex);	// TODO this assumes that supra sect rates are spread to all cubes in polygon, and not to those on the main fault surface
				// can't use fracSectInCubeArray because rates may not have been distributed evenly
				double totCubeRate=0;
				for(int i=0;i<sectInCubeArray.length;i++) {
					int sectID = sectInCubeArray[i];
					double cubeRate = longTermSubSeisMFD_OnSectList.get(sectID).getCumRate(2.55)/(double)numCubesInsideFaultPolygonArray[sectID]; // TODO remove hard coded mag
					totCubeRate += cubeRate;	
					if(debug)
						System.out.println(cubeRate+"\t"+longTermSubSeisMFD_OnSectList.get(sectID).getCumRate(2.55)+"\t"+(double)numCubesInsideFaultPolygonArray[sectID]+"\t"+sectID+"\t"+rupSet.getFaultSectionData(sectID).getName()+"\t"+cubeIndex+"\t"+getCubeLocationForIndex(cubeIndex));
				}
//				if(debug) {
//					System.out.println("getGridSourcRateInCube\tinsidePoly\t"+cubeIndex+"\t"+totCubeRate+"\t"+this.getCubeLocationForIndex(cubeIndex));
//				}
				return totCubeRate;
			}
			else {
				// first get the original rate for this grid node
				double rateTrulyOff = mfdForTrulyOffOnlyArray[numFltSystSources + griddeSeisRegionIndex].getTotalIncrRate();
				double origGridCellRate = rateTrulyOff/(1.0-faultPolyMgr.getNodeFraction(griddeSeisRegionIndex));
				double rate = origGridCellRate/(numPtSrcSubPts*numPtSrcSubPts*numCubeDepths);	// now divide rate among all the cubes in grid cell	
				return rate;
			}
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
	public int getRandomSourceIndexInCube_OLD(int cubeIndex) {
		
		// compute rate of gridded-seis source in this cube
		int griddeSeisRegionIndex = origGriddedRegion.indexForLocation(getCubeLocationForIndex(cubeIndex));
		if(griddeSeisRegionIndex == -1)	// TODO THROW EXCEPTION FOR NOW UNTIL I UNDERSTAND CONDITIONS BETTER
			throw new RuntimeException("No gridded source index for cube at: "+getCubeLocationForIndex(cubeIndex).toString());
		
		int gridSrcIndex = numFltSystSources + griddeSeisRegionIndex;
		
		int[] sectInCubeArray = sectInCubeList.get(cubeIndex);
		if(sectInCubeArray.length==0) {
			return gridSrcIndex;	// only gridded source in this cube
		}
		else {		// choose between gridded seis and a section nucleation
			IntegerPDF_FunctionSampler sampler = new IntegerPDF_FunctionSampler(sectInCubeArray.length+1);  // plus 1 for gridded source
			double gridSrcRate = sourceRates[gridSrcIndex]/(numPtSrcSubPts*numPtSrcSubPts*numCubeDepths);	// divide rate among all the cubes in grid cell
			sampler.set(0,gridSrcRate);	// first is the gridded source
			float[] fracts = fractionSectInCubeList.get(cubeIndex);
			for(int s=0; s<sectInCubeArray.length;s++) {
//				if(applyGR_Corr) {
//					sampler.set(s+1,totSectNuclRateArray[sectInCubeArray[s]]*(double)fracts[s]*grCorrFactorForSectArray[sectInCubeArray[s]]);		
//				}
//				else
					sampler.set(s+1,totSectNuclRateArray[sectInCubeArray[s]]*(double)fracts[s]);		
			}
			int randSampleIndex = sampler.getRandomInt(etas_utils.getRandomDouble());
			if(randSampleIndex == 0)	// gridded source chosen
				return gridSrcIndex;
			else {	// choose a source that nucleates on the section
				
				int sectIndex = sectInCubeArray[randSampleIndex-1];
				HashMap<Integer,Float> srcNuclRateHashMap = srcNuclRateOnSectList.get(sectIndex);
				IntegerPDF_FunctionSampler srcSampler = new IntegerPDF_FunctionSampler(srcNuclRateHashMap.size());
				int[] srcIndexArray = new int[srcNuclRateHashMap.size()];
				int index=0;
				for(int srcIndex:srcNuclRateHashMap.keySet()) {
					srcIndexArray[index] = srcIndex;
					srcSampler.set(index, srcNuclRateHashMap.get(srcIndex));
					index+=1;
				}
				return srcIndexArray[srcSampler.getRandomInt(etas_utils.getRandomDouble())];
			}
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
		
		// get gridded source index
		int gridSrcIndex = numFltSystSources + griddeSeisRegionIndex;
		
		if(totalSectRateInCubeArray[cubeIndex] < 1e-15 || fractionSupra==0.0)
			return gridSrcIndex;
		else {
//			double gridSrcRate = sourceRates[gridSrcIndex]/(numPtSrcSubPts*numPtSrcSubPts*numCubeDepths);	// divide rate among all the cubes in grid cell
			double gridSrcRate = cubeSamplerGriddedRatesOnly.getY(cubeIndex);
			double fractTest = gridSrcRate/(gridSrcRate+fractionSupra*totalSectRateInCubeArray[cubeIndex]);
			if(etas_utils.getRandomDouble() < fractTest) {
				return gridSrcIndex;
			}
			else {
				// randomly sample a section first
				int[] sectInCubeArray = sectInCubeList.get(cubeIndex);
				float[] fracts = fractionSectInCubeList.get(cubeIndex);
				IntegerPDF_FunctionSampler sectSampler = new IntegerPDF_FunctionSampler(sectInCubeArray.length);  // plus 1 for gridded source
				for(int s=0; s<sectInCubeArray.length;s++) {
					sectSampler.set(s,totSectNuclRateArray[sectInCubeArray[s]]*(double)fracts[s]);		
				}
				int randSectIndex = sectSampler.getRandomInt(etas_utils.getRandomDouble());
				int sectIndex = sectInCubeArray[randSectIndex];
				HashMap<Integer,Float> srcNuclRateHashMap = srcNuclRateOnSectList.get(sectIndex);
				IntegerPDF_FunctionSampler srcSampler = new IntegerPDF_FunctionSampler(srcNuclRateHashMap.size());
				int[] srcIndexArray = new int[srcNuclRateHashMap.size()];
				int index=0;
				for(int srcIndex:srcNuclRateHashMap.keySet()) {
					srcIndexArray[index] = srcIndex;
					srcSampler.set(index, srcNuclRateHashMap.get(srcIndex));
					index+=1;
				}
				return srcIndexArray[srcSampler.getRandomInt(etas_utils.getRandomDouble())];
			}
		}
	}

	
	/**
	 * This creates the long-term (time independent) supra- and sub-seismo MFDs for each section (if they don't already exist) held in
	 * longTermSupraSeisMFD_OnSectArray and longTermSubSeisMFD_OnSectList.
	 */
	private void makeLongTermSectMFDs() {
		
		if(longTermSupraSeisMFD_OnSectArray == null || longTermSubSeisMFD_OnSectList == null) {
			
			// here are the sub-seis MFDs
			longTermSubSeisMFD_OnSectList = fssERF.getSolution().getSubSeismoOnFaultMFD_List();
			
			totLongTermSubSeisRateOnSectArray = new double[longTermSubSeisMFD_OnSectList.size()];
			for(int s=0;s<totLongTermSubSeisRateOnSectArray.length;s++) {
				if(longTermSubSeisMFD_OnSectList.get(s) != null)
					totLongTermSubSeisRateOnSectArray[s] = longTermSubSeisMFD_OnSectList.get(s).getCumRate(2.55);
				else
					totLongTermSubSeisRateOnSectArray[s] = longTermSubSeisMFD_OnSectList.get(s).getCumRate(2.55);
			}
			
			// can't get supra-seism MFDs from fault-system-solution becuase some rups are zeroed out and there may 
			// be aleatory mag-area variability added in the ERF, so compute from ERF.
			
			// temporarily change the erf to Poisson to get long-term section MFDs (and test the parameter values are the same after)
			ProbabilityModelOptions probModel = (ProbabilityModelOptions)erf.getParameter(ProbabilityModelParam.NAME).getValue();
			ArrayList paramValueList = new ArrayList();
			for(Parameter param : erf.getAdjustableParameterList()) {
				paramValueList.add(param.getValue());
			}
			TimeSpan tsp = erf.getTimeSpan();
			double duration = tsp.getDuration();
			String startTimeString = tsp.getStartTimeMonth()+"/"+tsp.getStartTimeDay()+"/"+tsp.getStartTimeYear()+"; hr="+tsp.getStartTimeHour()+"; min="+tsp.getStartTimeMinute()+"; sec="+tsp.getStartTimeSecond();
			paramValueList.add(startTimeString);
			paramValueList.add(duration);
			int numParams = paramValueList.size();

			// now set ERF to poisson:
			erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
			erf.updateForecast();
			// get what we need
			if(wtSupraNuclBySubSeisRates) {
				longTermSupraSeisMFD_OnSectArray = calcNucleationMFDForAllSectsWtedBySubSeisRates(2.55, 8.95, 65);
			}
			else {
				longTermSupraSeisMFD_OnSectArray = FaultSysSolutionERF_Calc.calcNucleationMFDForAllSects(fssERF, 2.55, 8.95, 65);
			}
						
			longTermTotalERF_MFD = ERF_Calculator.getTotalMFD_ForERF(erf, 2.55, 8.45, 60, true);		

			
			// set it back and test param values
			erf.getParameter(ProbabilityModelParam.NAME).setValue(probModel);
			erf.updateForecast();
			
			int testNum = erf.getAdjustableParameterList().size()+2;
			if(numParams != testNum) {
				throw new RuntimeException("PROBLEM: num parameters changed:\t"+numParams+"\t"+testNum);
			}
			int i=0;
			for(Parameter param : erf.getAdjustableParameterList()) {
				if(param.getValue() != paramValueList.get(i))
					throw new RuntimeException("PROBLEM: "+param.getValue()+"\t"+paramValueList.get(i));
				i+=1;
			}
			TimeSpan tsp2 = erf.getTimeSpan();
			double duration2 = tsp2.getDuration();
			String startTimeString2 = tsp2.getStartTimeMonth()+"/"+tsp2.getStartTimeDay()+"/"+tsp2.getStartTimeYear()+"; hr="+tsp2.getStartTimeHour()+"; min="+tsp2.getStartTimeMinute()+"; sec="+tsp2.getStartTimeSecond();
			if(!startTimeString2.equals(startTimeString))
				throw new RuntimeException("PROBLEM: "+startTimeString2+"\t"+startTimeString2);
			if(duration2 != duration)
				throw new RuntimeException("PROBLEM Duration: "+duration2+"\t"+duration);
		}

	}
	
	
	
	/**
	 * This computes fault section nuclation MFD, accounting for any applied time dependence, aleatory mag-area 
	 * uncertainty, and smaller ruptures set to zero in the ERF (which is how this differs from 
	 * InversionFaultSystemSolution.calcNucleationRateForAllSects(*)), and assuming a uniform distribution
	 * of nucleations over the rupture surface.
	 * @param erf
	 * @param min, max, and num (MFD discretization values)
	 * @return
	 */
	private  SummedMagFreqDist[] calcNucleationMFDForAllSectsWtedBySubSeisRates(double min,double max,int num) {
		FaultSystemRupSet rupSet = fssERF.getSolution().getRupSet();
		
		SummedMagFreqDist[] mfdArray = new SummedMagFreqDist[rupSet.getNumSections()];
		for(int i=0;i<mfdArray.length;i++) {
			mfdArray[i] = new SummedMagFreqDist(min,max,num);
		}
		double duration = erf.getTimeSpan().getDuration();
		
		for(int s=0; s<fssERF.getNumFaultSystemSources();s++) {
			SummedMagFreqDist srcMFD = ERF_Calculator.getTotalMFD_ForSource(fssERF.getSource(s), duration, min, max, num, true);
			int fssRupIndex = fssERF.getFltSysRupIndexForSource(s);
			List<Integer> sectIndexList = rupSet.getSectionsIndicesForRup(fssRupIndex);

			int numSubRates=0;
			double aveSubRates=0;	// this will be used where there are no subseis ruptures
			for(int sectIndex:sectIndexList) {
				if(totLongTermSubSeisRateOnSectArray[sectIndex]>0) {
					numSubRates+=1;
					aveSubRates+=totLongTermSubSeisRateOnSectArray[sectIndex];
				}
			}
			if(aveSubRates==0)	// all were outside relm region; give all the same weight
				aveSubRates=1;
			else
				aveSubRates /= numSubRates;

			double sectWt;
			double totWt=0;
			for(int sectIndex : sectIndexList) {
				if(totLongTermSubSeisRateOnSectArray[sectIndex] != 0)
					sectWt = totLongTermSubSeisRateOnSectArray[sectIndex];
				else
					sectWt = aveSubRates;
				totWt += sectWt;
			}
			for(int sectIndex : sectIndexList) {
				if(totLongTermSubSeisRateOnSectArray[sectIndex] != 0)
					sectWt = totLongTermSubSeisRateOnSectArray[sectIndex];
				else
					sectWt = aveSubRates;
				for(int i=0;i<num;i++) {
					mfdArray[sectIndex].add(i,srcMFD.getY(i)*sectWt/totWt);
				}
			}
		}
		return mfdArray;
	}

	
	
	
	/**
	 * This computes a scale factor for each fault section, whereby multiplying the associate supra-seismogenic MFD
	 * by this factor will produced the same ??????? as for a perfect GR
	 * (extrapolating the sub-seismogenic MFD to the maximum, non-zero-rate  magnitude of the supra-seismogenic MFD).
	 * 
	 * The array is all 1.0 values if the erf is not a FaultSystemSolutionERF or if applyGR_Corr=false
	 * 
	 * @return
	 */
	private void computeGR_CorrFactorsForSections() {

		grCorrFactorForSectArray = new double[rupSet.getNumSections()];
		charFactorForSectArray = new double[rupSet.getNumSections()];

		// Make sure long-term MFDs are created
		makeLongTermSectMFDs();

		//System.out.println("GR Correction Factors:");

		double minCorr=Double.MAX_VALUE;
		int minCorrIndex = -1;
		//System.out.println("STARTING TEST RIGHT HERE");
		for(int sectIndex=0;sectIndex<grCorrFactorForSectArray.length;sectIndex++) {
			if(longTermSupraSeisMFD_OnSectArray[sectIndex] != null) {

				// TODO only need this temporarily?
				if (Double.isNaN(longTermSupraSeisMFD_OnSectArray[sectIndex].getMaxMagWithNonZeroRate())){
					System.out.println("NaN HERE: "+fssERF.getSolution().getRupSet().getFaultSectionData(sectIndex).getName());
//					throw new RuntimeException("Problem");
				}

//				if(Double.isInfinite(longTermSubSeisMFD_OnSectList.get(sectIndex).getMinMagWithNonZeroRate()))
//					System.out.println(rupSet.getFaultSectionData(sectIndex).getName());
					
				//					double val = ETAS_Utils.getScalingFactorToImposeGR_numPrimary(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false);
				double val = ETAS_Utils.getScalingFactorToImposeGR_supraRates(longTermSupraSeisMFD_OnSectArray[sectIndex], longTermSubSeisMFD_OnSectList.get(sectIndex), false);
				charFactorForSectArray[sectIndex]=1.0/val;
				
				if(applyGR_Corr) {
					grCorrFactorForSectArray[sectIndex]=val;
				}
				else {
					if(charFactorForSectArray[sectIndex]>MAX_CHAR_FACTOR)	// make it equal to the max value
						grCorrFactorForSectArray[sectIndex]=MAX_CHAR_FACTOR/charFactorForSectArray[sectIndex];
					else
						grCorrFactorForSectArray[sectIndex]=1.0;
				}

				if(val<minCorr) {
					minCorr=val;
					minCorrIndex=sectIndex;
				}
			}
			else {	// no supra-seismogenic ruptures
				grCorrFactorForSectArray[sectIndex]=1.0;
			}

			// System.out.println(sectIndex+"\t"+(float)grCorrFactorForSectArray[sectIndex]+"\t"+fssERF.getSolution().getRupSet().getFaultSectionData(sectIndex).getName());
		}
		if(D) System.out.println("min GR Corr ("+minCorr+") at sect index: "+minCorrIndex+"\t"+fssERF.getSolution().getRupSet().getFaultSectionData(minCorrIndex).getName());
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
	 *  See documentation for testGriddedSeisRatesInCubes() for an explanation of most of the discrepancy.
	 *  
	 *  There will be additional discrepancies for on-fault rates depending on how the GR correction
	 *  is applied.
	 */
	public void testRates() {
		
		if(D) 	System.out.println("Running testRates()");

		long startTime=System.currentTimeMillis();
		getCubeSamplerWithERF_GriddedRatesOnly();
		
		// start with gridded seis rates
		double totGriddedRate = cubeSamplerGriddedRatesOnly.calcSumOfY_Vals();
		double totRateTest1=totGriddedRate;
		
		// now compute section rates
		double[] sectSupraRatesTest = new double[this.rupSet.getNumSections()];
		for(int i=0;i<numCubes;i++) {
			int[] sections = sectInCubeList.get(i);
			float[] fracts = fractionSectInCubeList.get(i);
			for(int j=0; j<sections.length;j++) {
				sectSupraRatesTest[sections[j]] += totSectNuclRateArray[sections[j]]*(double)fracts[j];
			}
		}
		double totSectSupraRate=0;
		for(int s=0;s<sectSupraRatesTest.length;s++)
			totSectSupraRate += sectSupraRatesTest[s];
		
		// add these to total rate
		totRateTest1 += totSectSupraRate;
		
		// compute the rate of unassigned sections (outside the region)
		double rateUnassigned = 0;
		for(int s=0;s<sectSupraRatesTest.length;s++) {
			double sectRateDiff = this.totSectNuclRateArray[s]-sectSupraRatesTest[s];
			double fractDiff = Math.abs(sectRateDiff)/totSectNuclRateArray[s];
			if(fractDiff>0.001 & D) {
				System.out.println("\tfractDiff = "+(float)fractDiff+" for " + rupSet.getFaultSectionData(s).getName());
			}
			rateUnassigned += sectRateDiff;
		}
		
		totRateTest1+=rateUnassigned;
		
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
					";\t totGriddedRate="+(float)totGriddedRate+";\t totSectSupraRate="+(float)totSectSupraRate+";\t rateUnassigned="+(float)rateUnassigned);
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
//		long st = System.currentTimeMillis();
//		ETAS_SimAnalysisTools.writeMemoryUse("Memory before mfdForSrcArray");
		mfdForSrcArray = new SummedMagFreqDist[erf.getNumSources()];
		mfdForSrcSubSeisOnlyArray = new SummedMagFreqDist[erf.getNumSources()];
		mfdForTrulyOffOnlyArray = new SummedMagFreqDist[erf.getNumSources()];
		double duration = erf.getTimeSpan().getDuration();
		for(int s=0; s<erf.getNumSources();s++) {
			mfdForSrcArray[s] = ERF_Calculator.getTotalMFD_ForSource(erf.getSource(s), duration, minMag, maxMag, numMag, true);
			if(erf instanceof FaultSystemSolutionERF) {
				if(s >= numFltSystSources) {	// gridded seismicity source
					int gridRegionIndex = s-numFltSystSources;
					if(origGridSeisTrulyOffVsSubSeisStatus[gridRegionIndex] == 2) {	// it has both truly off and sub-seismo components
						mfdForSrcSubSeisOnlyArray[s] = ERF_Calculator.getTotalMFD_ForSource(fssERF.getSourceSubSeisOnly(s), duration, minMag, maxMag, numMag, true);;
						mfdForTrulyOffOnlyArray[s] = ERF_Calculator.getTotalMFD_ForSource(fssERF.getSourceTrulyOffOnly(s), duration, minMag, maxMag, numMag, true);;					}
					else if (origGridSeisTrulyOffVsSubSeisStatus[gridRegionIndex] == 1) { // it's all subseismo
						mfdForSrcSubSeisOnlyArray[s] = mfdForSrcArray[s];
						mfdForTrulyOffOnlyArray[s] = null;
					}
					else { // it's all truy off
						mfdForSrcSubSeisOnlyArray[s] = null;
						mfdForTrulyOffOnlyArray[s] = mfdForSrcArray[s];						
					}
//					// test
//					double subSeisRate=0;
//					double trulyOffRate=0;
//					if(mfdForSrcSubSeisOnlyArray[s] != null)
//						subSeisRate=mfdForSrcSubSeisOnlyArray[s].getTotalIncrRate();
//					if(mfdForTrulyOffOnlyArray[s] != null)
//						trulyOffRate=mfdForTrulyOffOnlyArray[s].getTotalIncrRate();
//					double test = (subSeisRate+trulyOffRate)/mfdForSrcArray[s].getTotalIncrRate();
//					if(Math.abs(test-1.0)>0.00001)
//						System.out.println("test="+test+"\t for source "+fssERF.getSource(s).getName());
				}
				
			}
			else {
				mfdForSrcSubSeisOnlyArray[s] = null;
				mfdForTrulyOffOnlyArray[s] = null;
			}
		}
		
		
//		ETAS_SimAnalysisTools.writeMemoryUse("Memory after mfdForSrcArray, which took (msec): "+(System.currentTimeMillis()-st));
	}
	
	
	/**
	 * This tests the difference between mfdForSrcSubSeisOnlyArray, which potentially includes contributions from 
	 * different subsections (a sum of GRs with potentially different Mmax values), and what's obtained using 
	 * longTermSubSeisMFD_OnSectList with the same total rate.  The test is for differences in rate at the M 2.55
	 * and max magnitude.  As expected, the latter show differences, but all rate differences at M 2.55 are less
	 * than 0.1%
	 */
	public void testAltSubseisOnFaultMFD_Representations() {
		System.out.println("Starting testSubseisOnFaultMFDs()");
		for(int srcID=numFltSystSources;srcID<fssERF.getNumSources();srcID++) {
			SummedMagFreqDist mfd = mfdForSrcSubSeisOnlyArray[srcID];
			if(mfd != null) {
				Map<Integer, Double> sectOnGridCellMap = faultPolyMgr.getSectionFracsOnNode(srcID-numFltSystSources);
				for(int sectID:sectOnGridCellMap.keySet()) {
					IncrementalMagFreqDist sectMFD = longTermSubSeisMFD_OnSectList.get(sectID).deepClone();
					double totRate = sectMFD.getCumRate(2.55);
					sectMFD.scale(mfd.getTotalIncrRate()/totRate);
					double test1=mfd.getIncrRate(2.55);
					double test2=sectMFD.getIncrRate(2.55);
					double fractDiff = Math.abs(test1/test2-1.0);
					if(fractDiff>1e-5)
						System.out.println("testSubseisOnFaultMFDs()  Sig M2.5 rate diff at srcID=\t"+srcID+"\t"+(float)fractDiff+"\t"+rupSet.getFaultSectionData(sectID).getName()+"\t"+origGriddedRegion.getLocation(srcID-numFltSystSources));
					test1=mfd.getCumRate(2.55);
					test2=sectMFD.getCumRate(2.55);
					fractDiff = Math.abs(test1/test2-1.0);
					if(fractDiff>1e-5)
						System.out.println("testSubseisOnFaultMFDs()  Sig M2.5 cum rate diff at srcID\t="+srcID+"\t"+(float)fractDiff+"\t"+rupSet.getFaultSectionData(sectID).getName()+"\t"+origGriddedRegion.getLocation(srcID-numFltSystSources));
					test1=mfd.getMaxMagWithNonZeroRate();
					test2=sectMFD.getMaxMagWithNonZeroRate();
					fractDiff = Math.abs(test1/test2-1.0);
					if(fractDiff>1e-5)
						System.out.println("testSubseisOnFaultMFDs()  Sig Mmax diff at srcID\t="+srcID+"\t"+(float)fractDiff+"\t"+rupSet.getFaultSectionData(sectID).getName()+"\t"+origGriddedRegion.getLocation(srcID-numFltSystSources));

				}

			}
		}
		System.out.println("Done with testSubseisOnFaultMFDs()");
	}
	
	
	/**
	 * This tests the difference between mfdForSrcSubSeisOnlyArray and what's constructed from longTermSubSeisMFD_OnSectList.
	 * It checks out.
	 */
	public void testSubseisOnFaultMFDs() {
		System.out.println("Starting testSubseisOnFaultMFDs()");
		for(int srcID=numFltSystSources;srcID<fssERF.getNumSources();srcID++) {
			SummedMagFreqDist mfd = mfdForSrcSubSeisOnlyArray[srcID];
			if(mfd != null) {
				SummedMagFreqDist testDist = new SummedMagFreqDist(mfd.getMinX(), mfd.getMaxX(), mfd.size());
				Map<Integer, Double> sectOnGridCellMap = faultPolyMgr.getSectionFracsOnNode(srcID-numFltSystSources);
				for(int sectID:sectOnGridCellMap.keySet()) {
					IncrementalMagFreqDist sectMFD = longTermSubSeisMFD_OnSectList.get(sectID).deepClone();
					sectMFD.scale(sectOnGridCellMap.get(sectID));
					testDist.addIncrementalMagFreqDist(sectMFD);
				}
				double test1=mfd.getIncrRate(2.55);
				double test2=testDist.getIncrRate(2.55);
				double fractDiff = Math.abs(test1/test2-1.0);
				if(fractDiff>1e-5)
					System.out.println("testSubseisOnFaultMFDs()  Sig M2.5 rate diff at srcID="+srcID+"\t"+origGriddedRegion.getLocation(srcID-numFltSystSources));
				test1=mfd.getCumRate(2.55);
				test2=testDist.getCumRate(2.55);
				fractDiff = Math.abs(test1/test2-1.0);
				if(fractDiff>1e-5)
					System.out.println("testSubseisOnFaultMFDs()  Sig M2.5 cum rate diff at srcID="+srcID+"\t"+origGriddedRegion.getLocation(srcID-numFltSystSources));
				test1=mfd.getMaxMagWithNonZeroRate();
				test2=testDist.getMaxMagWithNonZeroRate();
				fractDiff = Math.abs(test1/test2-1.0);
				if(fractDiff>1e-5)
					System.out.println("testSubseisOnFaultMFDs()  Sig Mmax diff at srcID="+srcID+"\t"+origGriddedRegion.getLocation(srcID-numFltSystSources));

			}
		}
		System.out.println("Done with testSubseisOnFaultMFDs()");
	}
	
	

	
	/**
	 * 
	 * TODO this can be constructed from what's returned by getCubeMFD_GriddedSeisOnly(int cubeIndex) and 
	 * getCubeMFD_SupraSeisOnly(int cubeIndex) if the max-mag tests here are no longer needed.
	 * @param cubeIndex
	 * 
	 * @return
	 */
	private SummedMagFreqDist getCubeMFD(int cubeIndex) {
		
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
//			throw new RuntimeException("must run computeMFD_ForSrcArrays(*) first");
		}
		
		Hashtable<Integer,Double> rateForSrcHashtable = getNucleationRatesOfSourcesInCube(cubeIndex, 1.0);
		
		if(rateForSrcHashtable == null)
			return null;
		
		double maxMagTest=0;
		int testSrcIndex=-1;
		SummedMagFreqDist magDist = new SummedMagFreqDist(mfdForSrcArray[0].getMinX(), mfdForSrcArray[0].getMaxX(), mfdForSrcArray[0].size());
		for(int srcIndex:rateForSrcHashtable.keySet()) {
			SummedMagFreqDist mfd=null;
			double srcNuclRate = rateForSrcHashtable.get(srcIndex);
			int gridIndex = srcIndex-numFltSystSources;
			int cubeRegIndex = getCubeRegAndDepIndicesForIndex(cubeIndex)[0];
			if(srcIndex >= numFltSystSources && origGridSeisTrulyOffVsSubSeisStatus[gridIndex] == 2) { // gridded seismicity and cell has both types
				if(isCubeInsideFaultPolygon[cubeRegIndex] == 1) {
					mfd = mfdForSrcSubSeisOnlyArray[srcIndex];
//					System.out.println("Got inside fault poly "+cubeIndex);
				}
				else {
					mfd = mfdForTrulyOffOnlyArray[srcIndex];
//					System.out.println("Got truly off "+cubeIndex);
				}
			}
			else {
				mfd = mfdForSrcArray[srcIndex];
			}
			double totRate = mfd.getTotalIncrRate();
			if(totRate>0) {
				for(int m=0;m<mfd.size();m++) {
					magDist.add(m, mfd.getY(m)*srcNuclRate/totRate);
				}
				double maxMag = mfd.getMaxMagWithNonZeroRate();
				if(maxMagTest < maxMag && srcNuclRate>0) {
					maxMagTest = maxMag;	
					testSrcIndex = srcIndex;
				}
			}
		}
		
		
		double maxMagTest2 = magDist.getMaxMagWithNonZeroRate();
		if(Double.isNaN(maxMagTest2))
			maxMagTest2=0;
		if(maxMagTest != maxMagTest2) {
			System.out.println("testSrcIndex="+testSrcIndex);
			System.out.println(mfdForSrcArray[testSrcIndex]);
			System.out.println(magDist+"\nmaxMagTest="+maxMagTest+"\nmaxMagTest2="+maxMagTest2);
			throw new RuntimeException("problem with max mag at cube index "+cubeIndex);
		}
		return magDist;
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
	private SummedMagFreqDist getCubeMFD_GriddedSeisOnly(int cubeIndex) {
		if(mfdForSrcArray == null) {
			throw new RuntimeException("must run computeMFD_ForSrcArrays(*) first");
		}
		Hashtable<Integer,Double> rateForSrcInCubeHashtable = getNucleationRatesOfSourcesInCube(cubeIndex, 1.0);
		if(rateForSrcInCubeHashtable == null)
			return null;	
		SummedMagFreqDist magDist = new SummedMagFreqDist(mfdForSrcArray[0].getMinX(), mfdForSrcArray[0].getMaxX(), mfdForSrcArray[0].size());
		for(int srcIndex:rateForSrcInCubeHashtable.keySet()) {
			if(srcIndex < numFltSystSources)
				continue;	// skip fault based sources
			SummedMagFreqDist mfd=null;
			double srcNuclRate = rateForSrcInCubeHashtable.get(srcIndex);
			int gridIndex = srcIndex-numFltSystSources;
			
			int cubeRegIndex = getCubeRegAndDepIndicesForIndex(cubeIndex)[0];
			if(origGridSeisTrulyOffVsSubSeisStatus[gridIndex] == 2) { // gridded seismicity and cell has both types
				if(isCubeInsideFaultPolygon[cubeRegIndex] == 1) {
					mfd = mfdForSrcSubSeisOnlyArray[srcIndex];
				}
				else {
					mfd = mfdForTrulyOffOnlyArray[srcIndex];
				}
			}
			else {
				mfd = mfdForSrcArray[srcIndex];
			}
			double totRate = mfd.getTotalIncrRate();
			if(totRate>0) {
				for(int m=0;m<mfd.size();m++)
					magDist.add(m, mfd.getY(m)*srcNuclRate/totRate);
			}
		}
		return magDist;
	}

	
	
	/**
	 * This gives the supraseismogeinc MFD for the cube (with zero rates if there are not such ruptures
	 * in the cube).  The GR correction is applied if appropriate.
	 * This assumes that the computeMFD_ForSrcArrays(*) has already been run (exception is thrown if not).
	 * The MFD has all zeros if there are no fault-based sources in the cube.
	 * 
	 * TODO this does not make any GR correction
	 * 
	 * @param cubeIndex
	 * @return
	 */
	private SummedMagFreqDist getCubeMFD_SupraSeisOnly(int cubeIndex) {
		if(mfdForSrcArray == null) {
			throw new RuntimeException("must run computeMFD_ForSrcArrays(*) first");
		}
		Hashtable<Integer,Double> rateForSrcHashtable = getNucleationRatesOfSourcesInCube(cubeIndex,1.0);
		if(rateForSrcHashtable == null)
			return null;
		SummedMagFreqDist magDist = new SummedMagFreqDist(mfdForSrcArray[0].getMinX(), mfdForSrcArray[0].getMaxX(), mfdForSrcArray[0].size());
		for(int srcIndex:rateForSrcHashtable.keySet()) {
			if(srcIndex < numFltSystSources) {
				IncrementalMagFreqDist mfd = mfdForSrcArray[srcIndex].deepClone();
				double totRate = mfd.getTotalIncrRate();
				mfd.scale(rateForSrcHashtable.get(srcIndex)/totRate);
				magDist.addIncrementalMagFreqDist(mfd);
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
			if(iSrc<this.numFltSystSources)	// skip fault-based sources
				continue;
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



	
	public double tempGetSampleProbForAllCubesOnFaultSection(int sectIndex, ETAS_EqkRupture mainshock) {
		double prob=0;
		IntegerPDF_FunctionSampler aveSampler = getAveSamplerForRupture(mainshock);
		ArrayList<Integer> usedCubesIndices = new ArrayList<Integer>();
		FaultSectionPrefData fltSectData = rupSet.getFaultSectionData(sectIndex);
		for(Location loc: fltSectData.getStirlingGriddedSurface(1, false, true).getEvenlyDiscritizedListOfLocsOnSurface()) {
			int cubeIndex = getCubeIndexForLocation(loc);
			if(!usedCubesIndices.contains(cubeIndex)) {
				prob += aveSampler.getY(cubeIndex);
				usedCubesIndices.add(cubeIndex);
			}
		}
		return prob;
	}
	
	
	/**
	 * This has a bunch of test using faultPolyMgr to help understand how it works in constructing various
	 * source elements.  The key thing to note is that rates in grid nodes influence the total subseismo MFD
	 * for a fault section, but this MFD is then allocated back to grid nodes by area on each, and not considering
	 * the relative rate on each grid node.
	 */
	public void testFaultPolyMgr() {
		
		computeMFD_ForSrcArrays(2.05, 8.95, 70);
		makeLongTermSectMFDs();
		
		// This shouldn't work because sub-seis fault section rates have been mapped back to grid nodes evenly rather than weighed by original grid-node rate
//		for(int i=0;i<faultPolyMgr.getRegion().getNumLocations();i++) {
//			double fracOnFaults = faultPolyMgr.getNodeFraction(i);
//			if(fracOnFaults>0) {
//				double testFrac = mfdForSrcSubSeisOnlyArray[i+numFltSystSources].getCumRate(2.55)/mfdForSrcArray[i+numFltSystSources].getCumRate(2.55);
//				double diff = Math.abs(fracOnFaults/testFrac-1.0);
//				if(diff>0.00001) {
//					Location loc = faultPolyMgr.getRegion().getLocation(i);
//					System.out.println("TEST1 diff=\t"+diff+"\t"+loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth()+"\t"+fracOnFaults+"\t"+testFrac);
//				}
//			}
//		}
		
		
		// This checks out
//		System.out.println("Starting testFaultPolyMgr()");
//		double[] testCellFraction = new double[origGriddedRegion.getNumLocations()];
//		for(int s=0;s<rupSet.getNumSections();s++) {
//			Map<Integer, Double> cellsAndFracsForSectMap = faultPolyMgr.getScaledNodeFractions(s);
//			for(int cellID:cellsAndFracsForSectMap.keySet()) {
//				testCellFraction[cellID]+=cellsAndFracsForSectMap.get(cellID);
//			}
//		}
//		for(int i=0;i<testCellFraction.length;i++) {
//			double expVal = faultPolyMgr.getNodeFraction(i);
//			double test=100;
//			if(expVal>0)
//				test = Math.abs(testCellFraction[i]/expVal-1.0);
//			else
//				test = Math.abs(testCellFraction[i]-expVal);
//			if(test > 0.000001)
//					System.out.println("Problem at cell "+i+"\ttest="+test);
//		}
//		
		
//		// test of the method I added: faultPolyMgr.getSectionFracsOnNode(i)
//		for(int i=0;i<origGriddedRegion.getNumLocations();i++) {
//			HashMap<Integer, Double> sectFracOnNodeMap = new HashMap<Integer, Double>();
//			for(int s=0;s<rupSet.getNumSections();s++) {
//				Map<Integer, Double> nodeFracMap = faultPolyMgr.getNodeFractions(s); // all the grid nodes for this section and their weights
//				if(nodeFracMap.keySet().contains(i)) {
//					sectFracOnNodeMap.put(s, nodeFracMap.get(i));
//				}
//			}
//			Map<Integer, Double> testMap = faultPolyMgr.getSectionFracsOnNode(i);
//			if(testMap.size() != sectFracOnNodeMap.size())
//				throw new RuntimeException("Problem1"+testMap.size()+"\t"+sectFracOnNodeMap.size());
//			for(int s:testMap.keySet()) {
//				double val1 = testMap.get(s);
//				double val2 = sectFracOnNodeMap.get(s);
//				double diff = Math.abs(val1/val2-1.0);
//				if(diff>0.001) {
//					System.out.println("TEST Maps diff="+diff+"\t"+val1+"\t"+val2);
//				}
//			}
//
//		}

		
		// This tests whether we can recover the subseismo section rates from the rates on grid nodes;  It works in all but a few cases,
		// which are sections right on the edge of the RELM region, plus Rose Canyon subsection 0 had a norm diff of 0.001 that I can't explain
		int num=0;
		double totVal1=0;
		double totVal2=0;
		double[] testsectSubSeisRates = new double[rupSet.getNumSections()];
		for(int s=0;s<rupSet.getNumSections();s++) {
			Map<Integer, Double> nodeFracMap = faultPolyMgr.getNodeFractions(s); // all the grid nodes for this section and their weights
			for(int regID:nodeFracMap.keySet()) {
				double subSeisRateForGridNode = mfdForSrcSubSeisOnlyArray[regID+numFltSystSources].getCumRate(2.55);	// total subseis MFD at grid node
				// get the amount of this that goes to the given section
				double sum = 0;
				
				Map<Integer, Double> sectFracOnGridNode = faultPolyMgr.getSectionFracsOnNode(regID);
				for(int s2:sectFracOnGridNode.keySet()) {
					if(s2 != s)
						sum+=longTermSubSeisMFD_OnSectList.get(s2).getCumRate(2.55)*sectFracOnGridNode.get(s2);
				}
				// old way
//				for(int s2=0;s2<rupSet.getNumSections();s2++) {
//					Map<Integer, Double> nodeFracMap2 = faultPolyMgr.getNodeFractions(s2);
//					if(nodeFracMap2.keySet().contains(regID)) {
//						if(s2 != s)
//							sum+=longTermSubSeisMFD_OnSectList.get(s2).getCumRate(2.55)*nodeFracMap2.get(regID);
//					}
//				}	
				testsectSubSeisRates[s] += (subSeisRateForGridNode - sum);
			}
			double val1 = testsectSubSeisRates[s];
			totVal1+=val1;
			double val2 = longTermSubSeisMFD_OnSectList.get(s).getCumRate(2.55);
			totVal2+=val2;
			double diff = Math.abs(val1/val2-1.0);
			if(diff>0.001) {
				System.out.println("TEST2 diff=\t"+s+"\t"+diff+"\t"+this.rupSet.getFaultSectionData(s).getName()+"\t"+val1+"\t"+val2);
				num+=1;
			}
		}
		System.out.println(num+" out of "+rupSet.getNumSections()+" had diffs >0.001");
		System.out.println("totVal1="+totVal1+"\ttotVal2="+totVal2+"\t"+(totVal1/totVal2));
		
		
		// This tests whether we can recover the subSeis rates on grid nodes from the subsection subSeismo rates; It works
		double[] subSeisRateForGridNodesTest = new double[origGriddedRegion.getNumLocations()];
		for(int s=0;s<rupSet.getNumSections();s++) {
			Map<Integer, Double> nodeFracMap = faultPolyMgr.getNodeFractions(s);
			double totSectSubSeisRate = longTermSubSeisMFD_OnSectList.get(s).getCumRate(2.55);
			for(int gridID:nodeFracMap.keySet()) {
				subSeisRateForGridNodesTest[gridID] += totSectSubSeisRate*nodeFracMap.get(gridID);
			}
		}
		for(int i=0;i<subSeisRateForGridNodesTest.length;i++) {
			if(subSeisRateForGridNodesTest[i]>0.0) {
				double expVal = mfdForSrcSubSeisOnlyArray[i+numFltSystSources].getCumRate(2.55);
				Location loc = origGriddedRegion.getLocation(i);
				double diff = Math.abs(subSeisRateForGridNodesTest[i]/expVal-1.0);
				if(diff>0.000001)
					System.out.println("TEST3\t"+i+"\t"+diff+"\t"+loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth()+"\t"+expVal+"\t"+subSeisRateForGridNodesTest[i]);
			}
		}
		
		
		// This works
//		UCERF3_GridSourceGenerator gridGen = (UCERF3_GridSourceGenerator)fssERF.getSolution().getGridSourceProvider();
//		for(int s=0;s<rupSet.getNumSections();s++) {
//			double rate1 = longTermSubSeisMFD_OnSectList.get(s).getCumRate(2.55);
//			double rate2 = gridGen.getSectSubSeisMFD(s).getCumRate(2.55);
//			System.out.println("TESTRIGHTHERE\t"+s+"\t"+(float)(rate1/rate2)+"\t"+rupSet.getFaultSectionData(s).getName());
//
//		}

		
		
		System.out.println("Done with testFaultPolyMgr()");

	}
	
	
	/**
	 * This method was written to test constructing subseismo MFDs for a fault section in
	 * different ways, in part to confirm Ned's undestanding of how subseismo MFDs for a
	 * given grid node are distributed among multiple fault-section polygons that overlap
	 * the grid node.
	 * @param sectIndex
	 */
	public void testSubSeisMFD_ForSect(int sectIndex) {
		
		// this is the target MFD
		IncrementalMagFreqDist mfd2 = ((FaultSystemSolutionERF)erf).getSolution().getSubSeismoOnFaultMFD_List().get(sectIndex);
		mfd2.setName("Subseis MFD from erf.getSolution().getSubSeismoOnFaultMFD_List().get(sectIndex) for "+sectIndex+"; "+rupSet.getFaultSectionData(sectIndex).getName());

		// Now we will make this from the source MFDs
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
		}


		// compute the fraction of the fault polygon occupied by each grid node from cube locations, where fractions sum to 1.0
		HashMap<Integer,Double> srcFractMap = new HashMap<Integer,Double>(); // this is the fraction of cubes inside the subsect polygon for each grid src
		Region faultPolygon = faultPolyMgr.getPoly(sectIndex);
//		System.out.println("Section Polygon:\n"+faultPolygon.getBorder().toString());
//		double cubeFrac = 1.0/(DEFAULT_NUM_PT_SRC_SUB_PTS*DEFAULT_NUM_PT_SRC_SUB_PTS);
		int numCubes=0;
		for(int i=0;i<gridRegForCubes.getNumLocations();i++) {
			Location cubeLoc = gridRegForCubes.getLocation(i);
			if(faultPolygon.contains(cubeLoc)) {
				int srcIndex = numFltSystSources + origGriddedRegion.indexForLocation(cubeLoc);
				if(srcFractMap.containsKey(srcIndex)) {
					double newFrac = srcFractMap.get(srcIndex)+1.0;
					srcFractMap.put(srcIndex,newFrac);
				}
				else {
					srcFractMap.put(srcIndex,1.0);
				}
				numCubes+=1;
			}
		}
		System.out.println("srcFractMap");
		double tot=0;
		for(int srcIndex:srcFractMap.keySet()) {
			double val = srcFractMap.get(srcIndex)/(double)numCubes;
			srcFractMap.put(srcIndex, val);
			Location gridLoc = origGriddedRegion.getLocation(srcIndex-numFltSystSources);
			System.out.println(srcIndex+"\t"+srcFractMap.get(srcIndex).floatValue()+"\t"+gridLoc);	
			tot += srcFractMap.get(srcIndex);
		}
		System.out.println("total="+(float)tot);
		
		
//		// This is wrong because variable node rates are accounted for in setting subseismo on-fault rates, be the total of the latter
//		// is distributed back on grid nodes ignoring the variable node rates.
//		SummedMagFreqDist mfd3 = new SummedMagFreqDist(2.05,8.95,70);
//		for(int s : srcFractMap.keySet()) {
//			double fractNodeCoveredBySections = faultPolyMgr.getNodeFraction(s-numFltSystSources); // fraction of node covered by one or more subsections
//			double scaleFactor = srcFractMap.get(s)/fractNodeCoveredBySections; // the fraction of subseimo MFD of node that goes to this section
//			IncrementalMagFreqDist gridSubSeisSrcMFD = mfdForSrcSubSeisOnlyArray[s];
//			System.out.println(s+"\tnull="+(gridSubSeisSrcMFD == null)+"\t+"+origGriddedRegion.getLocation(s-numFltSystSources)+
//					"\t"+origGridSeisTrulyOffVsSubSeisStatus[s-numFltSystSources]+"\t"+(float)fractNodeCoveredBySections+"\t"+
//					(float)scaleFactor+"\t"+(float)gridSubSeisSrcMFD.getMaxMagWithNonZeroRate());
//			IncrementalMagFreqDist scaledMFD = gridSubSeisSrcMFD.deepClone();
//			scaledMFD.scale(scaleFactor);
//			mfd3.addIncrementalMagFreqDist(scaledMFD);
//		}
//		mfd3.setName("Total Subseis MFD from gridded sources - srcFractMap");
//		mfd3.setInfo("This is known to be wrong");
//
//		
//		
		System.out.println("altMap");
		Map<Integer, Double> altMap = faultPolyMgr.getNodeFractions(sectIndex); // the fraction of the fault polygon occupied by each node, where fractions sum to 1.0
		tot=0;
		for(int gridIndex:altMap.keySet()) {
			Location gridLoc = origGriddedRegion.getLocation(gridIndex);
			System.out.println(gridIndex+numFltSystSources+"\t"+altMap.get(gridIndex).floatValue()+"\t"+gridLoc);
			tot+=altMap.get(gridIndex);
		}
		System.out.println("total="+(float)tot);

//		
//		// This is also wrong because variable node rates are accounted for in setting subseismo on-fault rates, be the total of the latter
//		// is distributed back on grid nodes ignoring the variable node rates.
//		SummedMagFreqDist mfd1 = new SummedMagFreqDist(2.05,8.95,70);
//		for(int regID : altMap.keySet()) {
//			double fractNodeCoveredBySections = faultPolyMgr.getNodeFraction(regID); // fraction of node covered by one or more subsections
//			double scaleFactor = altMap.get(regID)/fractNodeCoveredBySections; // the fraction of subseimo MFD of node that goes to this section
//			IncrementalMagFreqDist gridSubSeisSrcMFD = mfdForSrcSubSeisOnlyArray[regID+numFltSystSources];
//			System.out.println((regID+numFltSystSources)+"\t"+regID+"\tnull="+(gridSubSeisSrcMFD == null)+"\t+"+origGriddedRegion.getLocation(regID)+
//					"\t"+origGridSeisTrulyOffVsSubSeisStatus[regID]+"\t"+(float)fractNodeCoveredBySections+"\t"+
//					(float)scaleFactor+"\t"+(float)gridSubSeisSrcMFD.getMaxMagWithNonZeroRate());
//			IncrementalMagFreqDist scaledMFD = gridSubSeisSrcMFD.deepClone();
//			scaledMFD.scale(scaleFactor);
//			mfd1.addIncrementalMagFreqDist(scaledMFD);
//		}
//		mfd1.setName("Total Subseis MFD from gridded sources - altMap");
//		mfd1.setInfo("This is known to be wrong");
		
		
		
		// This is the correct way to recover the the fault section MFD from that at grid nodes
		Map<Integer, Double> nodeFracMap = faultPolyMgr.getNodeFractions(sectIndex); // the fraction of the fault polygon occupied by each node, where fractions sum to 1.0
		double targetRate=0;
		for(int regID:nodeFracMap.keySet()) {
			double subSeisRateForGridNode = mfdForSrcSubSeisOnlyArray[regID+numFltSystSources].getCumRate(2.55);	// total subseis MFD at grid node
			// get the amount of this that goes to the given section
			double sum = 0;
			Map<Integer, Double> sectFracOnGridNode = faultPolyMgr.getSectionFracsOnNode(regID); //  the sections and fraction of each section that contributes to the node
			for(int s2:sectFracOnGridNode.keySet()) {
				if(s2 != sectIndex)
					sum+=longTermSubSeisMFD_OnSectList.get(s2).getCumRate(2.55)*sectFracOnGridNode.get(s2);
			}
			targetRate += (subSeisRateForGridNode - sum);
		}
		IncrementalMagFreqDist mfd4 = longTermSubSeisMFD_OnSectList.get(sectIndex).deepClone();
		double scale = targetRate/mfd4.getCumRate(2.55);
		mfd4.scale(scale);
		mfd4.setName("Correct/New Way");
		
		
		// This is an alternative correct way to recover the the fault section MFD from that at grid nodes
		SummedMagFreqDist mfd5 = new SummedMagFreqDist(2.05,8.95,70);
		for(int regID:nodeFracMap.keySet()) {
			double subSeisRateForGridNode = mfdForSrcSubSeisOnlyArray[regID+numFltSystSources].getCumRate(2.55);	// total subseis MFD at grid node
			// get the amount of this that goes to the given section
			double sum = 0;
			Map<Integer, Double> sectFracOnGridNode = faultPolyMgr.getSectionFracsOnNode(regID);
			for(int s2:sectFracOnGridNode.keySet()) {
				if(s2 != sectIndex)
					sum+=longTermSubSeisMFD_OnSectList.get(s2).getCumRate(2.55)*sectFracOnGridNode.get(s2);
			}
			targetRate = (subSeisRateForGridNode - sum);
			IncrementalMagFreqDist mfd = mfdForSrcSubSeisOnlyArray[regID+numFltSystSources].deepClone();
			scale = targetRate/mfd.getCumRate(2.55);
			mfd.scale(scale);
			mfd5.addIncrementalMagFreqDist(mfd);
		}
		mfd5.setName("Correct/New Way Alt");


		ArrayList<IncrementalMagFreqDist> mfdList = new ArrayList<IncrementalMagFreqDist>();
//		mfdList.add(mfd1);
		mfdList.add(mfd2);
//		mfdList.add(mfd3);
		mfdList.add(mfd4);
		mfdList.add(mfd5);
		
		GraphWindow mfd_Graph = new GraphWindow(mfdList, "Subseis MFD comparison"); 
		mfd_Graph.setX_AxisLabel("Mag");
		mfd_Graph.setY_AxisLabel("Rate");
		mfd_Graph.setYLog(true);
		mfd_Graph.setPlotLabelFontSize(22);
		mfd_Graph.setAxisLabelFontSize(20);
		mfd_Graph.setTickLabelFontSize(18);			

	}

	public void tempListSubsectsThatCoverGridCell(int gridCellIndex) {
		System.out.println("Running tempListSubsectsThatCoverGridCell("+gridCellIndex+")");
		for(int s=0;s<rupSet.getNumSections();s++) {
			Map<Integer, Double> nodesForSectMap = faultPolyMgr.getScaledNodeFractions(s);
			if(nodesForSectMap.keySet().contains(gridCellIndex)) {
				System.out.println(s+"\t"+nodesForSectMap.get(gridCellIndex)+"\t"+rupSet.getFaultSectionData(s).getName());
				Region faultPolygon = faultPolyMgr.getPoly(s);
				System.out.println("Section Polygon:");
				for(Location loc:faultPolygon.getBorder())
					System.out.println(loc.getLatitude()+"\t"+loc.getLongitude());
			}
		}
	}
	
	
	
	public double getTrulyOffFaultGR_Corr(boolean debug) {
		if (!Double.isNaN(trulyOffFaultGR_Corr)) {
			return trulyOffFaultGR_Corr;
		}
		double aveMinSupra = 6.35; // from page 1150 of the UCERF3-TI BSSA report (right column)
		trulyOffFaultGR_Corr = Double.NaN;
		for(int i=0;i<origGriddedRegion.getNumLocations();i++) {
			if(origGridSeisTrulyOffVsSubSeisStatus[i] == 0) {
				SummedMagFreqDist mfd = mfdForTrulyOffOnlyArray[numFltSystSources+i];
				double maxMag=mfd.getMaxMagWithNonZeroRate();
				double minMag=mfd.getMinMagWithNonZeroRate();
				int numMag = (int)((maxMag-minMag)/mfd.getDelta()) + 1;
				GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(1.0, 1.0, minMag, maxMag, numMag);
				gr.scaleToIncrRate(minMag, mfd.getY(minMag));
				trulyOffFaultGR_Corr = gr.getCumRate(aveMinSupra)/mfd.getCumRate(aveMinSupra);
				if(debug) {
					ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
					mfd.setInfo("grCorr = "+(float)trulyOffFaultGR_Corr);
					funcs.add(mfd);
					funcs.add(gr);
					funcs.add(mfd.getCumRateDistWithOffset());
					funcs.add(gr.getCumRateDistWithOffset());
					GraphWindow graph = new GraphWindow(funcs, "From getTrulyOffFaultGR_Corr"); 
					graph.setX_AxisLabel("Mag");
					graph.setY_AxisLabel("Rate");
					graph.setYLog(true);
				}
				break;	// only need one of them
			}
		}
		
		return trulyOffFaultGR_Corr;
	}
	
	public double getFaultGR_CorrFromTotalFaultMFDs(boolean debug) {
		double aveMinSupra = 6.35; // from page 1150 of the UCERF3-TI BSSA report (right column)
		double minMag = 2.55;
		double maxMag=8.95;
		int numMag = 65;
		double grCorr = Double.NaN;
		SummedMagFreqDist totMFD_Supra = new SummedMagFreqDist(minMag, maxMag, numMag);
		totMFD_Supra.setName("totMFD_Supra");
		SummedMagFreqDist totMFD_Sub =  new SummedMagFreqDist(minMag, maxMag, numMag);
		totMFD_Sub.setName("totMFD_Sub");
		for(int s=0;s<rupSet.getNumSections();s++) {
			if(longTermSubSeisMFD_OnSectList.get(s) != null) {
				totMFD_Sub.addIncrementalMagFreqDist(longTermSubSeisMFD_OnSectList.get(s));
				if(longTermSupraSeisMFD_OnSectArray[s] != null)
					totMFD_Supra.addIncrementalMagFreqDist(longTermSupraSeisMFD_OnSectArray[s]);
			}
		}
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(1.0, 1.0, minMag, maxMag, numMag);
		gr.scaleToIncrRate(minMag, totMFD_Sub.getY(minMag));
		gr.setName("GR Dist");
		grCorr = gr.getCumRate(aveMinSupra)/totMFD_Supra.getCumRate(aveMinSupra);
		if(debug) {
			ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
			totMFD_Supra.setInfo("grCorr = "+(float)grCorr);
			funcs.add(totMFD_Supra);
			funcs.add(totMFD_Sub);
			funcs.add(gr);
			funcs.add(totMFD_Supra.getCumRateDistWithOffset());
			funcs.add(totMFD_Sub.getCumRateDistWithOffset());
			funcs.add(gr.getCumRateDistWithOffset());
			GraphWindow graph = new GraphWindow(funcs, "From getFaultGR_CorrFromTotalFaultMFDs"); 
			graph.setX_AxisLabel("Mag");
			graph.setY_AxisLabel("Rate");
			graph.setYLog(true);
		}
		
		return grCorr;
	}
	
	/**
	 * This makes a plot of the lont-term total, total on-fault, and total off fault MFDs
	 * TODO Move to utility class?
	 */
	public void plotExpectedLongTermMFDs() {
		double minMag = 2.55;
		double maxMag=8.95;
		int numMag = 65;
		SummedMagFreqDist totMFD_Supra = new SummedMagFreqDist(minMag, maxMag, numMag);
		totMFD_Supra.setName("totMFD_Supra");
		SummedMagFreqDist totMFD_Sub =  new SummedMagFreqDist(minMag, maxMag, numMag);
		totMFD_Sub.setName("totMFD_Sub");
		for(int s=0;s<rupSet.getNumSections();s++) {
			if(longTermSubSeisMFD_OnSectList.get(s) != null) {
				totMFD_Sub.addIncrementalMagFreqDist(longTermSubSeisMFD_OnSectList.get(s));
				if(longTermSupraSeisMFD_OnSectArray[s] != null)
					totMFD_Supra.addIncrementalMagFreqDist(longTermSupraSeisMFD_OnSectArray[s]);
			}
		}
		SummedMagFreqDist totalTrulyOffFaultMFD = new SummedMagFreqDist(minMag, maxMag, numMag);
		for(int src=fssERF.getNumFaultSystemSources();src<fssERF.getNumSources();src++)
			if(mfdForTrulyOffOnlyArray[src] != null)
				totalTrulyOffFaultMFD.addIncrementalMagFreqDist(mfdForTrulyOffOnlyArray[src]);
		totalTrulyOffFaultMFD.setName("totalTrulyOffFaultMFD");
		
		SummedMagFreqDist totalModelMFD = new SummedMagFreqDist(minMag, maxMag, numMag);
		totalModelMFD.addIncrementalMagFreqDist(totalTrulyOffFaultMFD);
		totalModelMFD.addIncrementalMagFreqDist(totMFD_Sub);
		totalModelMFD.addIncrementalMagFreqDist(totMFD_Supra);
		totalModelMFD.setName("totalModelMFD");
		
		SummedMagFreqDist totalOnFaultMFD = new SummedMagFreqDist(minMag, maxMag, numMag);
		totalOnFaultMFD.addIncrementalMagFreqDist(totMFD_Sub);
		totalOnFaultMFD.addIncrementalMagFreqDist(totMFD_Supra);
		

		GutenbergRichterMagFreqDist gr1 = new GutenbergRichterMagFreqDist(1.0, 1.0, minMag, maxMag+1d, numMag+10);	// add some so cum doesn't taper
		gr1.scaleToIncrRate(minMag, totalOnFaultMFD.getY(minMag));
		gr1.setName("GR Dist 1");

		GutenbergRichterMagFreqDist gr2 = new GutenbergRichterMagFreqDist(1.0, 1.0, minMag, maxMag+1d, numMag+10);
		gr2.scaleToIncrRate(minMag, totalTrulyOffFaultMFD.getY(minMag));
		gr2.setName("GR Dist 2");
		
		ArrayList<PlotCurveCharacterstics> plotCharsList = new ArrayList<PlotCurveCharacterstics>();
		plotCharsList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3, Color.BLACK));
		plotCharsList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3, Color.RED));
		plotCharsList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3, Color.BLUE));
		plotCharsList.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1, Color.RED));
		plotCharsList.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1, Color.BLUE));

		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
		funcs.add(totalModelMFD);
		funcs.add(totalOnFaultMFD);
		funcs.add(totalTrulyOffFaultMFD);
		funcs.add(gr1);
		funcs.add(gr2);
		GraphWindow graph = new GraphWindow(funcs, "Imcremental MFDs", plotCharsList); 
		graph.setX_AxisLabel("Magnitude");
		graph.setY_AxisLabel("Incremental Rate (per year)");
		graph.setYLog(true);
		graph.setAxisLabelFontSize(20);
		graph.setPlotLabelFontSize(20);
		graph.setTickLabelFontSize(18);
		graph.setAxisRange(4, 8.5, 1e-5, 1e2);
		
		ArrayList<EvenlyDiscretizedFunc> funcsCum = new ArrayList<EvenlyDiscretizedFunc>();
		funcsCum.add(totalModelMFD.getCumRateDistWithOffset());
		funcsCum.add(totalOnFaultMFD.getCumRateDistWithOffset());
		funcsCum.add(totalTrulyOffFaultMFD.getCumRateDistWithOffset());
		funcsCum.add(gr1.getCumRateDistWithOffset());
		funcsCum.add(gr2.getCumRateDistWithOffset());
		GraphWindow graphCum = new GraphWindow(funcsCum, "Cumulative MFDs", plotCharsList); 
		graphCum.setX_AxisLabel("Magnitude");
		graphCum.setY_AxisLabel("Cumulative Rate (per year)");
		graphCum.setYLog(true);
		graphCum.setAxisLabelFontSize(20);
		graphCum.setPlotLabelFontSize(20);
		graphCum.setTickLabelFontSize(18);
		graphCum.setAxisRange(4, 8.5, 1e-5, 1e2);
		
		try {
			graph.saveAsPDF("LongTermMFDs_Incremental.pdf");
			graph.saveAsTXT("LongTermMFDs_Incremental.txt");
			graphCum.saveAsPDF("LongTermMFDs_Cumulative.pdf");
			graphCum.saveAsTXT("LongTermMFDs_Cumulative.txt");
		} catch (IOException e) {
			e.printStackTrace();
		}

	}


	/**
	 * This plost sub, supra, and cumulative combined MFDs for a given subsection
	 * for both uncorrected and gr-corrected cases.
	 */
	public void plotMFDsForSubSect(int sectIndex) {
		String name = rupSet.getFaultSectionData(sectIndex).getName();
		makeLongTermSectMFDs();
		IncrementalMagFreqDist subSeisMFD = longTermSubSeisMFD_OnSectList.get(sectIndex);
		subSeisMFD.setName(subSeisMFD+" for "+name);
		IncrementalMagFreqDist supraSeisMFD = longTermSupraSeisMFD_OnSectArray[sectIndex];
		supraSeisMFD.setName(supraSeisMFD+" for "+name);
		SummedMagFreqDist combinedMFD = new SummedMagFreqDist(2.55,8.45,60);
		combinedMFD.addIncrementalMagFreqDist(subSeisMFD);
		combinedMFD.addIncrementalMagFreqDist(supraSeisMFD);
		combinedMFD.setName("Total Cum MFD for "+name);
		
		GutenbergRichterMagFreqDist perfectGR = new GutenbergRichterMagFreqDist(1.0, 1.0, 2.55, 8.35, 59);
		perfectGR.scaleToIncrRate(2.55, subSeisMFD.getY(2.55));
		perfectGR.setName("perfectGR");
		
		double grCorr = ETAS_Utils.getScalingFactorToImposeGR_supraRates(supraSeisMFD, subSeisMFD, false);
		
		IncrementalMagFreqDist supraSeisMFD_grCorr = supraSeisMFD.deepClone();
		supraSeisMFD_grCorr.scaleToCumRate(0, supraSeisMFD_grCorr.getTotalIncrRate()*grCorr);
		double testCorr = ETAS_Utils.getScalingFactorToImposeGR_supraRates(supraSeisMFD_grCorr, subSeisMFD, false);
		supraSeisMFD_grCorr.setName(supraSeisMFD_grCorr+" for "+name);
		System.out.println("grCorr="+grCorr+"\ttestCorr="+testCorr);
		supraSeisMFD_grCorr.setInfo("grCorr="+grCorr+"\ttestCorr="+testCorr);
			
		// Plot orig MFDs
		ArrayList<EvenlyDiscretizedFunc> mfdList1 = new ArrayList<EvenlyDiscretizedFunc>();
		mfdList1.add(subSeisMFD);
		mfdList1.add(supraSeisMFD);
		mfdList1.add(combinedMFD.getCumRateDistWithOffset());
		mfdList1.add(perfectGR);
		mfdList1.add(perfectGR.getCumRateDistWithOffset());
		ArrayList<PlotCurveCharacterstics> chars = new ArrayList<PlotCurveCharacterstics>();
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.LIGHT_GRAY));
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.DARK_GRAY));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.BLACK));
		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, Color.BLACK));
		GraphWindow magProbDistsGraph1 = new GraphWindow(mfdList1, name, chars); 
		magProbDistsGraph1.setX_AxisLabel("Magnitude");
		magProbDistsGraph1.setY_AxisLabel("Rate (per year)");
		magProbDistsGraph1.setY_AxisRange(1e-8, 1.0);
		magProbDistsGraph1.setX_AxisRange(2.5d, 8.5d);
		magProbDistsGraph1.setYLog(true);
		magProbDistsGraph1.setPlotLabelFontSize(18);
		magProbDistsGraph1.setAxisLabelFontSize(22);
		magProbDistsGraph1.setTickLabelFontSize(20);

		// Plot correctd  MFDs
		SummedMagFreqDist combinedMFDcorr = new SummedMagFreqDist(2.55,8.45,60);
		combinedMFDcorr.addIncrementalMagFreqDist(subSeisMFD);
		combinedMFDcorr.addIncrementalMagFreqDist(supraSeisMFD_grCorr);
		combinedMFDcorr.setName("Total Corr Cum MFD for "+name);
		ArrayList<EvenlyDiscretizedFunc> mfdList2 = new ArrayList<EvenlyDiscretizedFunc>();
		mfdList2.add(subSeisMFD);
		mfdList2.add(supraSeisMFD_grCorr);
		mfdList2.add(combinedMFDcorr.getCumRateDistWithOffset());
		mfdList2.add(perfectGR);
		mfdList2.add(perfectGR.getCumRateDistWithOffset());
		GraphWindow magProbDistsGraph2 = new GraphWindow(mfdList2, name+" (GR-corrected)",chars); 
		magProbDistsGraph2.setX_AxisLabel("Magnitude");
		magProbDistsGraph2.setY_AxisLabel("Rate (per year)");
		magProbDistsGraph2.setY_AxisRange(1e-8, 1.0);
		magProbDistsGraph2.setX_AxisRange(2.5d, 8.5d);
		magProbDistsGraph2.setYLog(true);
		magProbDistsGraph2.setPlotLabelFontSize(18);
		magProbDistsGraph2.setAxisLabelFontSize(22);
		magProbDistsGraph2.setTickLabelFontSize(20);
		
		
		
		// Expected number primary from M5 event
		ETAS_ParameterList params = new ETAS_ParameterList();
		double expNum = ETAS_Utils.getExpectedNumEvents(params.get_k(), params.get_p(), 5.5, 2.5, params.get_c(), 0, 360);
		
		EvenlyDiscretizedFunc expCumDistForM5p5 = combinedMFD.getCumRateDistWithOffset();
		double scaleFact = expNum/combinedMFD.getTotalIncrRate();
		expCumDistForM5p5.scale(scaleFact);
		expCumDistForM5p5.setName("expCumDistForM5p5");
		expCumDistForM5p5.setTolerance(0.001);
		double expNum5p5=expCumDistForM5p5.getY(5.5);
		double expNum6p3=expCumDistForM5p5.getY(6.3);
		expCumDistForM5p5.setInfo("expNum="+expNum+"\n"+"num≥5.5="+expNum5p5+"\n"+"num≥6.3="+expNum6p3);
		
		EvenlyDiscretizedFunc expCumDistForPerfectGR_M5p5 = perfectGR.getCumRateDistWithOffset();
		scaleFact = expNum/perfectGR.getTotalIncrRate();
		expCumDistForPerfectGR_M5p5.scale(scaleFact);
		expCumDistForPerfectGR_M5p5.setName("expCumDistForPerfectGR_M5p5");
		expCumDistForPerfectGR_M5p5.setTolerance(0.001);
		expNum5p5=expCumDistForPerfectGR_M5p5.getY(5.5);
		expNum6p3=expCumDistForPerfectGR_M5p5.getY(6.3);
		expCumDistForPerfectGR_M5p5.setInfo("expNum="+expNum+"\n"+"num≥5.5="+expNum5p5+"\n"+"num≥6.3="+expNum6p3);
		
		EvenlyDiscretizedFunc expCumDistForCorrM5p5 = combinedMFDcorr.getCumRateDistWithOffset();
		scaleFact = expNum/combinedMFDcorr.getTotalIncrRate();
		expCumDistForCorrM5p5.scale(scaleFact);
		expCumDistForCorrM5p5.setName("expCumDistForCorrM5p5");
		expCumDistForCorrM5p5.setTolerance(0.001);
		expNum5p5=expCumDistForCorrM5p5.getY(5.5);
		expNum6p3=expCumDistForCorrM5p5.getY(6.3);
		expCumDistForCorrM5p5.setInfo("expNum="+expNum+"\n"+"num≥5.5="+expNum5p5+"\n"+"num≥6.3="+expNum6p3);
		
		ArrayList<EvenlyDiscretizedFunc> mfdList3 = new ArrayList<EvenlyDiscretizedFunc>();
		mfdList3.add(expCumDistForM5p5);
		mfdList3.add(expCumDistForPerfectGR_M5p5);
		mfdList3.add(expCumDistForCorrM5p5);
		ArrayList<PlotCurveCharacterstics> chars2 = new ArrayList<PlotCurveCharacterstics>();
		chars2.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
		chars2.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.BLACK));
		chars2.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, Color.BLACK));

		GraphWindow magProbDistsGraph3 = new GraphWindow(mfdList3, "Expected Num Primary for M 5.5 Main Shock",chars2); 
		magProbDistsGraph3.setX_AxisLabel("Magnitude (M)");
		magProbDistsGraph3.setY_AxisLabel("Num≥M (over the next year)");
		magProbDistsGraph3.setY_AxisRange(1e-6, 100.0);
		magProbDistsGraph3.setX_AxisRange(2.5d, 8.5d);
		magProbDistsGraph3.setYLog(true);
		magProbDistsGraph3.setPlotLabelFontSize(18);
		magProbDistsGraph3.setAxisLabelFontSize(22);
		magProbDistsGraph3.setTickLabelFontSize(20);

		String fileNamePrefix =name.replace(" ", "_").replace(",", "_");
		

		try {
			magProbDistsGraph1.saveAsPDF(fileNamePrefix+"_MFDs.pdf");
			magProbDistsGraph1.saveAsTXT(fileNamePrefix+"_MFDs.txt");
			magProbDistsGraph2.saveAsPDF(fileNamePrefix+"_MFDs_GRcorr.pdf");
			magProbDistsGraph2.saveAsTXT(fileNamePrefix+"_MFDs_GRcorr.txt");
			magProbDistsGraph3.saveAsPDF(fileNamePrefix+"_ExpNumM5p5.pdf");
			magProbDistsGraph3.saveAsTXT(fileNamePrefix+"_ExpNumM5p5.txt");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public  void writePolygonsForSubSections(String fileNamePrefix, int firstSubSectID, int lastSubSectID) {
		FileWriter fileWriterPolygons;
		try {
			fileWriterPolygons = new FileWriter(new File(GMT_CA_Maps.GMT_DIR, fileNamePrefix+".txt"));
			fileWriterPolygons.write("lat\tlon\tdepth\n");
			for(int i=firstSubSectID; i<=lastSubSectID;i++) {
				String polygonString="";
				for(Location loc : faultPolyMgr.getPoly(i).getBorder()) {
					polygonString += (float)loc.getLatitude() + "\t" + (float)loc.getLongitude() + "\t" + loc.getDepth() +"\n";
				}
				fileWriterPolygons.write(polygonString);

			}
			fileWriterPolygons.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
//	longTermSubSeisMFD_OnSectList

	public  void writeTotSubSeisRateForSections(int firstSubSectID, int lastSubSectID) {
		double totRate=0;
		System.out.println("\nM≥2.5 Subseis Rates for Subsections\n");
			for(int i=firstSubSectID; i<=lastSubSectID;i++) {
				String name = rupSet.getFaultSectionData(i).getName();
				double rate = longTermSubSeisMFD_OnSectList.get(i).getCumRate(2.55);
				System.out.println("\t"+rate+"\t"+name);
				totRate+=rate;
			}
			System.out.println("\ttotRate="+totRate);
	}
	
	public SummedMagFreqDist getLongTermTotalERF_MFD() {
		return longTermTotalERF_MFD;
	}
	
	
	/**
	 * 
	 * @param numYears
	 * @param etasParams
	 * @param multFactForAllGen
	 * @param dirName - set as null if no plot wanted
	 * @return
	 */
	public IntegerPDF_FunctionSampler getExpectedAfterShockRateInCubesFromSupraRates(double numYears, ETAS_ParameterList etasParams, double multFactForAllGen, String dirName) {
		ProbabilityModelOptions probModel = (ProbabilityModelOptions)erf.getParameter(ProbabilityModelParam.NAME).getValue();
		if(probModel != ProbabilityModelOptions.POISSON)
			throw new RuntimeException("ERF must be Poisson");
		
		double duration = fssERF.getTimeSpan().getDuration();
		
		// first compute the number of aftershocks spawned from each parent location
		double[] aftRateForEachParLocArray = new double[gridRegForParentLocs.getNodeCount()];
		CalcProgressBar progressBar = new CalcProgressBar("Looping over all points", "junk");
		progressBar.showProgress(true);

		for(int srcID=0;srcID<fssERF.getNumFaultSystemSources();srcID++) {
			progressBar.updateProgress(srcID, fssERF.getNumFaultSystemSources());
			ProbEqkSource src = fssERF.getSource(srcID);
			if(src.getNumRuptures()>1)
				throw new RuntimeException("More than one rup per source not yet supported");
			ProbEqkRupture rup = src.getRupture(0);
			double rupRate = rup.getMeanAnnualRate(duration);
			int fssRupIndex=fssERF.getFltSysRupIndexForSource(srcID);
			double numAftershocks = multFactForAllGen*ETAS_Utils.getExpectedNumEvents(etasParams.get_k(), etasParams.get_p(), rup.getMag(), ETAS_Utils.magMin_DEFAULT, etasParams.get_c(), 0d, numYears*365.25);

			RuptureSurface surf = etas_utils.getRuptureSurfaceWithNoCreepReduction(fssRupIndex, fssERF, 1d);
			LocationList locList = surf.getEvenlyDiscritizedListOfLocsOnSurface();

			// this puts no aftershocks on the creeping sections
//			LocationList locList = rup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
			
			double numSurfacePoints = locList.size();
			for(Location loc:locList) {
				int parLocIndex = getParLocIndexForLocation(loc);
				if(parLocIndex>=0 && parLocIndex<aftRateForEachParLocArray.length) {
					aftRateForEachParLocArray[parLocIndex] += rupRate*numAftershocks/numSurfacePoints;
					if(parLocIndex==aftRateForEachParLocArray.length-1)
						System.out.println("Bogus Point for Loc "+loc);
				}
			}
		}
			
		// now compute the aftershock rate in each cube
		IntegerPDF_FunctionSampler aftRateInEachCubeSampler = new IntegerPDF_FunctionSampler(numCubes);
		for(int parLocIndex=0;parLocIndex<aftRateForEachParLocArray.length;parLocIndex++) {
			progressBar.updateProgress(parLocIndex, aftRateForEachParLocArray.length);
			double aftRate = aftRateForEachParLocArray[parLocIndex];
			if(aftRate>0) {
				IntegerPDF_FunctionSampler probInEachCubeSampler = getCubeSamplerWithOnlyDistDecay(parLocIndex);
				for(int cubeIndex=0;cubeIndex<aftRateInEachCubeSampler.size();cubeIndex++) {
					double cubeRate = probInEachCubeSampler.getY(cubeIndex)*aftRate;
					double prevRate = aftRateInEachCubeSampler.getY(cubeIndex);
					aftRateInEachCubeSampler.set(cubeIndex, prevRate+cubeRate);
				}
			}
		}
		
		progressBar.showProgress(false);
		
		if(dirName != null) {
			// make map
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
			double depth = 7d;
			int depthIndex = getCubeDepthIndex(depth);
			int numCubesAtDepth = xyzDataSet.size();
			progressBar = new CalcProgressBar("Looping over all points", "junk");
			progressBar.showProgress(true);
			
			for(int i=0; i<numCubesAtDepth;i++) {
				progressBar.updateProgress(i, numCubesAtDepth);
				int samplerIndex = getCubeIndexForRegAndDepIndices(i, depthIndex);
				xyzDataSet.set(i, aftRateInEachCubeSampler.getY(samplerIndex));
//				Location loc = xyzDataSet.getLocation(i);
//				System.out.println(loc.getLongitude()+"\t"+loc.getLatitude()+"\t"+xyzDataSet.get(i));
			}
			progressBar.showProgress(false);
			
			mapGen.setParameter(GMT_MapGenerator.LOG_PLOT_NAME,true);
//			mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_FROMDATA);
			mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_MANUALLY);
//			double maxZ = Math.ceil(Math.log10(xyzDataSet.getMaxZ()))+0.5;
//			mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,maxZ-5);
//			mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,maxZ);
			
			mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,-7d);
			mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,-1d);			


			String metadata = "Map from calling getExpectedAfterShockRateInCubesFromSupraRates(*) method";
			
			try {
					String url = mapGen.makeMapUsingServlet(xyzDataSet, "M≥2.5 Rates at "+depth+" km depth", metadata, dirName);
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

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return aftRateInEachCubeSampler;
	}
	
	
	/**
	 * This method makes the data used for making the correction associated with ETAS_ParameterList.getApplyGridSeisCorr(), 
	 * and it also make various comparison plots.
	 * @param numYears
	 * @param etasParams
	 * @param multFactForAllGen
	 * @param dirNameForCubeRates
	 */
	public void getExpectedAfterShockRateInGridCellsFromSupraRates(double numYears, ETAS_ParameterList etasParams, double multFactForAllGen, String dirNameForCubeRates) {

		IntegerPDF_FunctionSampler sampler = getExpectedAfterShockRateInCubesFromSupraRates(numYears, etasParams, multFactForAllGen, dirNameForCubeRates);
		
		GriddedGeoDataSet ratesFromAshocks = new GriddedGeoDataSet(origGriddedRegion, true);	// true makes X latitude
		double[] zVals = new double[origGriddedRegion.getNodeCount()];
		for(int cubeIndex=0;cubeIndex<sampler.size();cubeIndex++) {
			Location loc = getCubeLocationForIndex(cubeIndex);
			int regIndex = origGriddedRegion.indexForLocation(loc);
			if(regIndex != -1)
				zVals[regIndex] += sampler.getY(cubeIndex);
		}
		for(int i=0;i<origGriddedRegion.getNodeCount();i++)
			ratesFromAshocks.set(i, zVals[i]);
		
		GriddedGeoDataSet origCellRates = ERF_Calculator.getNucleationRatesInRegion(erf, origGriddedRegion, 0d, 10d);
		
		GriddedGeoDataSet newCellRates = new GriddedGeoDataSet(origGriddedRegion, true);	// true makes X latitude
		GriddedGeoDataSet rateRatios = new GriddedGeoDataSet(origGriddedRegion, true);	// true makes X latitude
		double[] griddedSeisCorr = new double[rateRatios.size()];
		for(int i=0;i<origGriddedRegion.getNodeCount();i++) {
			if(ratesFromAshocks.get(i) > origCellRates.get(i))
				newCellRates.set(i, ratesFromAshocks.get(i));
			else
				newCellRates.set(i, origCellRates.get(i));
			rateRatios.set(i, newCellRates.get(i)/origCellRates.get(i));
			griddedSeisCorr[i] = rateRatios.get(i);
		}

		
		GMT_MapGenerator gmt_MapGenerator = GMT_CA_Maps.getDefaultGMT_MapGenerator();
		
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -3.5);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, 1.5);
				
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		cptParam.getValue().setBelowMinColor(Color.WHITE);

		try {
			// make plots
			GMT_CA_Maps.makeMap(ratesFromAshocks, "Aftershock Nucleation Rate", "test", "ExpAfterShockRateFromSupraMap", gmt_MapGenerator);
			GMT_CA_Maps.makeMap(origCellRates, "Original Nucleation Rate", "test", "OrigRateMap", gmt_MapGenerator);
			GMT_CA_Maps.makeMap(newCellRates, "Corrected Nucleation Rate", "test", "NewRateMap", gmt_MapGenerator);
			cptParam.setValue(GMT_CPT_Files.UCERF3_RATIOS.getFileName());
			gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, 2.0-Math.floor(rateRatios.getMaxZ())); // so it'g gray at 1.0
			gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, Math.floor(rateRatios.getMaxZ()));
			gmt_MapGenerator.setParameter(GMT_MapGenerator.LOG_PLOT_NAME, false);
			System.out.println("Max Ratio = "+rateRatios.getMaxZ());
			GMT_CA_Maps.makeMap(rateRatios, "Corrected vs Original Ratio", "test", "NewVsOrigRateRatioMap", gmt_MapGenerator);
			
			// now make cache file
			MatrixIO.doubleArrayToFile(griddedSeisCorr, new File(defaultGriddedCorrFilename));

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}


	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		ETAS_ParameterList etasParams = new ETAS_ParameterList();
		etasParams.setApplyGridSeisCorr(true);
		etasParams.setApplySubSeisForSupraNucl(true);
		etasParams.setImposeGR(false);;
		etasParams.setU3ETAS_ProbModel(U3ETAS_ProbabilityModelOptions.POISSON);
		
		CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
		
		FaultSystemSolutionERF_ETAS erf = ETAS_Simulator.getU3_ETAS_ERF(2014d,1d);
		
		if(etasParams.getApplyGridSeisCorr())
			ETAS_Simulator.correctGriddedSeismicityRatesInERF(erf, false);
		
//		System.out.println(erf.getSolution().getGridSourceProvider().getClass());
//		System.out.println(erf.getSolution().getClass());
//		System.exit(0);

		// this tests whether total subseismo MFD from grid source provider is the same as from the fault-sys solution
//		FaultSysSolutionERF_Calc.testTotSubSeisMFD(erf);
		
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
		
		ETAS_PrimaryEventSampler etas_PrimEventSampler = new ETAS_PrimaryEventSampler(griddedRegion, erf, sourceRates, 
				gridSeisDiscr,null, etasParams, new ETAS_Utils(),null,null,null);
		
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
		
//		etas_PrimEventSampler.plotMFDsForSubSect(560); //  Mojave S Subsect 14
//		etas_PrimEventSampler.plotMFDsForSubSect(1851); //  Mojave S Subsect 14
//		etas_PrimEventSampler.plotMFDsForSubSect(1835); //  Mojave N Subsect 3
//		etas_PrimEventSampler.plotMFDsForSubSect(1841); //  Mojave S, subsection 4
		etas_PrimEventSampler.plotMFDsForSubSect(2460); //  Surprise Valley, subsection 14
		etas_PrimEventSampler.plotMFDsForSubSect(2159); //  San Jacinto (Borrego), subsection 0
//		etas_PrimEventSampler.plotMFDsForSubSect(961); //   Imperial, subsection 0

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
		
		// this is for the Poisson cases
//		plotPrimayEventOverlap(rupture, relSrcProbs, subDirName, 10000, rupInfo);
//		plotPrimayEventsThatTouchParent(rupture, relSrcProbs, subDirName, 10000, rupInfo);
//		plotMostLikelyTriggeredFSS_Ruptures(rupture, relSrcProbs, subDirName, 100, rupInfo);

		long st = System.currentTimeMillis();
		List<EvenlyDiscretizedFunc> expectedPrimaryMFDsForScenarioList = ETAS_SimAnalysisTools.getExpectedPrimaryMFDs_ForRup(rupInfo, 
				new File(subDirName,rupInfo+"_ExpPrimMFD").getAbsolutePath(), 
				getExpectedPrimaryMFD_PDF(relSrcProbs), rupture, expNum, fssERF.isPoisson());
//		List<EvenlyDiscretizedFunc> expectedPrimaryMFDsForScenarioList = ETAS_SimAnalysisTools.getExpectedPrimaryMFDs_ForRup(rupInfo, 
//				new File(subDirName,rupInfo+"_ExpPrimMFD").getAbsolutePath(), 
//				getExpectedPrimaryMFD_PDF_Alt(aveCubeSamplerForRup,0.99), rupture, expNum, fssERF.isPoisson());
		
		ETAS_SimAnalysisTools.plotExpectedPrimaryMFD_ForRup(rupInfo, 
				new File(subDirName,rupInfo+"_ExpPrimMFD").getAbsolutePath(), 
				expectedPrimaryMFDsForScenarioList, rupture, expNum);
		
		if (D) System.out.println("expectedPrimaryMFDsForScenarioList took (msec) "+(System.currentTimeMillis()-st));
		
// THIS IS ONLY CORRECT IF expectedNumSupra<0.05 or so
//		EvenlyDiscretizedFunc supraCumMFD = expectedPrimaryMFDsForScenarioList.get(3);
//		double expectedNumSupra;
//		if(supraCumMFD != null)
//			expectedNumSupra = supraCumMFD.getY(0);
//		else
//			throw new RuntimeException("Need to figure out how to handle this");

		// this is three times slower:
//		st = System.currentTimeMillis();
//		List<EvenlyDiscretizedFunc> expectedPrimaryMFDsForScenarioList2 = ETAS_SimAnalysisTools.plotExpectedPrimaryMFD_ForRup("ScenarioAlt", new File(subDirName,"scenarioExpPrimMFD_Alt").getAbsolutePath(), 
//				this.getExpectedPrimaryMFD_PDF_Alt(aveAveCubeSamplerForRup), rupture, expNum);
//		if (D) System.out.println("getExpectedPrimaryMFD_PDF_Alt took (msec) "+(System.currentTimeMillis()-st));

		// Compute Primary Event Sampler Map
		st = System.currentTimeMillis();
		plotSamplerMap(aveCubeSamplerForRup, "Primary Sampler for "+rupInfo, "PrimarySamplerMap_"+rupInfo, subDirName);
		if (D) System.out.println("plotSamplerMap took (msec) "+(System.currentTimeMillis()-st));

		// Compute subsection participation probability map
		st = System.currentTimeMillis();
		String info = plotSubSectParticipationProbGivenRuptureAndReturnInfo(rupture, relSrcProbs, subDirName, 30, rupInfo, expNum, fssERF.isPoisson());
		if (D) System.out.println("plotSubSectParticipationProbGivenRuptureAndReturnInfo took (msec) "+(System.currentTimeMillis()-st));
		
		// for subsection trigger probabilities (different than participation).
		st = System.currentTimeMillis();
//		if (D) System.out.println("expectedNumSupra="+expectedNumSupra);
		info += "\n\n"+ plotSubSectTriggerProbGivenAllPrimayEvents(aveCubeSamplerForRup, subDirName, 30, rupInfo, expNum, fssERF.isPoisson(), rupture);
		if (D) System.out.println("plotSubSectRelativeTriggerProbGivenSupraSeisRupture took (msec) "+(System.currentTimeMillis()-st));

		
		if (D) System.out.println(info);	
		info_fileWriter.write(info+"\n");
		
//info_fileWriter.close();
//System.exit(-1);
		
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
	 * this test whether the total sub-seismo MFD from grid cells equals that from the fault sections
	 */
	public void testTotalSubSeisOnFaultMFD() {
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
		}
		SummedMagFreqDist mfd1 = new SummedMagFreqDist(2.05, 8.95, 70);
		for(int src=numFltSystSources; src<erf.getNumSources();src++) {
			if(mfdForSrcSubSeisOnlyArray[src] != null)
				mfd1.addIncrementalMagFreqDist(mfdForSrcSubSeisOnlyArray[src]);
		}
		
		// Make sure long-term MFDs are created
		makeLongTermSectMFDs();
		SummedMagFreqDist mfd2 = new SummedMagFreqDist(2.05, 8.95, 70);
		for(IncrementalMagFreqDist mfd:longTermSubSeisMFD_OnSectList) {
			if(mfd != null)
				mfd2.addIncrementalMagFreqDist(mfd);
		}
		
		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
		funcs.add(mfd1);
		funcs.add(mfd2);
		GraphWindow graph = new GraphWindow(funcs, "testTotalSubSeisOnFaultMFD()");
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Incr Rate");
		graph.setYLog(true);
	}
	
	

	
	public void tempTestBulgeInCube() {
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
		}
//		int cubeIndex = getCubeIndexForLocation(new Location(34.42,-117.8,7.0));
//		int cubeIndex = getCubeIndexForLocation(new Location(38.96, -122.64,7.0));		
//		int cubeIndex = getCubeIndexForLocation(new Location(39.5,-123.04,7.0));	// 39.5	-123.04	0.81054216
		
//		int cubeIndex = getCubeIndexForLocation(new Location(36.86,-121.42,7.0));
		
//		int cubeIndex = getCubeIndexForLocation(new Location(35.9,-120.26,7.0)); //35.9	-120.26	-1.068057197
		
//		Location loc = new Location(33.96,-118.6,7.0);
		
		Location loc = new Location(35.82,-120.26, 7.0); 	// 35.82	-120.26	-0.575863121
		int cubeIndex = getCubeIndexForLocation(loc);  //33.96	-118.6	-1.101231026

		System.out.println("\ntempTestBulgeInCube()");
		Map<Integer, Double> sectFracMap = faultPolyMgr.getSectionFracsOnNode(origGriddedRegion.indexForLocation(loc));
		for(int sectID:sectFracMap.keySet()) {
			System.out.println(sectID+"\t"+sectFracMap.get(sectID)+"\t"+rupSet.getFaultSectionData(sectID).getName());
		}
		
		double aveGRcorr = getAveScalingFactorToImposeGR_supraRatesInCube(cubeIndex);
		System.out.println("aveGRcorr="+aveGRcorr+"\t(from getAveScalingFactorToImposeGR_supraRatesInCube(cubeIndex))");


		SummedMagFreqDist mfdSupra = getCubeMFD_SupraSeisOnly(cubeIndex);
		SummedMagFreqDist mfdGridded = getCubeMFD_GriddedSeisOnly(cubeIndex);
		
		ETAS_Utils.getScalingFactorToImposeGR_supraRates(mfdSupra, mfdGridded, true);
		int index=0;
		double rate1=0;
		double rate2=0;
		double rate3=0;
		for(int sectID: sectInCubeList.get(cubeIndex)) {
			System.out.println("\n"+rupSet.getFaultSectionData(sectID).getName());
			int numCubesInsideFaultPolygon = numCubesInsideFaultPolygonArray[sectID];
			System.out.println("numCubesInsideFaultPolygonArray[sectID]="+numCubesInsideFaultPolygon);
//			double frac = 1.0/(double)getCubesAndFractForFaultSectionExponential(sectID).size();
//			System.out.println(sectID+"\t"+fractionSectInCubeList.get(cubeIndex)[index]+"\t"+(float)(1.0/(double)numCubesInsideFaultPolygon)+"\t"+(float)frac);
			System.out.println(sectID+"\t"+fractionSectInCubeList.get(cubeIndex)[index]+"\t"+(float)(1.0/(double)numCubesInsideFaultPolygon));
			
			double val = ETAS_Utils.getScalingFactorToImposeGR_supraRates(longTermSupraSeisMFD_OnSectArray[sectID], longTermSubSeisMFD_OnSectList.get(sectID), true);
			
			System.out.println("\t"+grCorrFactorForSectArray[sectID]+" =? "+val);
			
			rate1 += val*longTermSupraSeisMFD_OnSectArray[sectID].getCumRate(2.55)/numCubesInsideFaultPolygonArray[sectID];
			
			rate2 += totSectNuclRateArray[sectID]/numCubesInsideFaultPolygonArray[sectID];
			
			HashMap<Integer,Float> map = srcNuclRateOnSectList.get(sectID);
			for(int s:map.keySet())
				rate3+=map.get(s)/numCubesInsideFaultPolygonArray[sectID];
			
			testSubSeisMFD_ForSect(sectID);
			index+=1;
		}
		System.out.println("Test1\t"+rate1+"\t"+mfdSupra.getTotalIncrRate());
		System.out.println("Test2\t"+rate2+"\t"+mfdSupra.getTotalIncrRate());
		System.out.println("Test3\t"+rate3+"\t"+mfdSupra.getTotalIncrRate());

	}
	
	
	public double getAveScalingFactorToImposeGR_supraRatesInCube(int cubeIndex) {
		double aveGRcorr=0;
		double totRateSupra=0;
		float[] fracArray = fractionSectInCubeList.get(cubeIndex);
		int[] sectArray = sectInCubeList.get(cubeIndex);
		if(fracArray.length==0) { // no sections in cube
			return getTrulyOffFaultGR_Corr(false);
		}
		for(int i=0;i<sectArray.length;i++) {
			int sectID=sectArray[i];
			IncrementalMagFreqDist supraMFD = longTermSupraSeisMFD_OnSectArray[sectID].deepClone();
			IncrementalMagFreqDist subMFD = longTermSubSeisMFD_OnSectList.get(sectID).deepClone();

			if(supraMFD == null ||  subMFD == null)
				continue;
			
			supraMFD.scale(grCorrFactorForSectArray[sectID]*fracArray[i]);
			subMFD.scale(1.0/numCubesInsideFaultPolygonArray[sectID]);
			
			// weight by total supra rate
			double rate = supraMFD.getTotalIncrRate();
			
			double val = ETAS_Utils.getScalingFactorToImposeGR_supraRates(supraMFD, subMFD, false);
			aveGRcorr+=rate*val;
			totRateSupra+=rate;
		}
//		throw new RuntimeException("This method is now wrong in that it does not account for nuclation rate being potentially dependent on subseis rates");
		return aveGRcorr/totRateSupra;
	}
	
	
	public void tempTestBulgeforCubesInSectPolygon(int sectID) {
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
		}
		
		makeLongTermSectMFDs();

		Region faultPolygon = faultPolyMgr.getPoly(sectID);
		System.out.println("Section Polygon for "+rupSet.getFaultSectionData(sectID).getName()+"\n"+faultPolygon.getBorder().toString());
		
		double sectCorr = ETAS_Utils.getScalingFactorToImposeGR_supraRates(longTermSupraSeisMFD_OnSectArray[sectID], longTermSubSeisMFD_OnSectList.get(sectID), false);
		System.out.println("sect grCorr = "+sectCorr);
	
		for(int i=0;i<numCubes;i++) {
			Location cubeLoc = getCubeLocationForIndex(i);
			if(faultPolygon.contains(cubeLoc)) {
				SummedMagFreqDist mfdSupra = getCubeMFD_SupraSeisOnly(i);
				SummedMagFreqDist mfdGridded = getCubeMFD_GriddedSeisOnly(i);
				double grCorr = ETAS_Utils.getScalingFactorToImposeGR_supraRates(mfdSupra, mfdGridded, false);
				int gridRegionIndex = this.origGriddedRegion.indexForLocation(cubeLoc);
				System.out.println(grCorr+"\t"+gridRegionIndex+"\t"+cubeLoc);
			}
		}
	}
	
	/**
	 * TODO Move to utility class
	 * @param depth
	 * @param dirName
	 * @return
	 */
	public String plotBulgeAtDepthMap(double depth, String dirName) {
		
		GMT_MapGenerator mapGen = GMT_CA_Maps.getDefaultGMT_MapGenerator();
		
		CPTParameter cptParam = (CPTParameter )mapGen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.UCERF3_RATIOS.getFileName());
		
		mapGen.setParameter(GMT_MapGenerator.MIN_LAT_PARAM_NAME,gridRegForCubes.getMinGridLat());
		mapGen.setParameter(GMT_MapGenerator.MAX_LAT_PARAM_NAME,gridRegForCubes.getMaxGridLat());
		mapGen.setParameter(GMT_MapGenerator.MIN_LON_PARAM_NAME,gridRegForCubes.getMinGridLon());
		mapGen.setParameter(GMT_MapGenerator.MAX_LON_PARAM_NAME,gridRegForCubes.getMaxGridLon());
		mapGen.setParameter(GMT_MapGenerator.GRID_SPACING_PARAM_NAME, gridRegForCubes.getLatSpacing());	// assume lat and lon spacing are same
		mapGen.setParameter(GMT_MapGenerator.LOG_PLOT_NAME,true);
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_FROMDATA);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_MANUALLY);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,-3d);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,3d);
		
		
		GriddedGeoDataSet bulgeData = new GriddedGeoDataSet(gridRegForCubes, true);
		int depthIndex = getCubeDepthIndex(depth);
		int numCubesAtDepth = bulgeData.size();
		CalcProgressBar progressBar = new CalcProgressBar("Looping over all points", "junk");
		progressBar.showProgress(true);
		
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
		}

		for(int i=0; i<numCubesAtDepth;i++) {
			progressBar.updateProgress(i, numCubesAtDepth);
			int cubeIndex = getCubeIndexForRegAndDepIndices(i, depthIndex);
			int[] regAndDepIndex = getCubeRegAndDepIndicesForIndex(cubeIndex);
//			double bulge = 1.0;
//			if(isCubeInsideFaultPolygon[regAndDepIndex[0]] == 1)
			double	bulge = 1.0/getAveScalingFactorToImposeGR_supraRatesInCube(cubeIndex);
			
//			SummedMagFreqDist mfdSupra = getCubeMFD_SupraSeisOnly(cubeIndex);
//			SummedMagFreqDist mfdGridded = getCubeMFD_GriddedSeisOnly(cubeIndex);
//			double bulge = 1.0;
//			int[] regAndDepIndex = getCubeRegAndDepIndicesForIndex(cubeIndex);
//			if((mfdSupra==null || mfdSupra.getMaxY()<10e-15) && isCubeInsideFaultPolygon[regAndDepIndex[0]] == 1)
//				bulge = 10e-16; // set as zero if inside polygon and no mfd supra
//			else if(mfdSupra != null &&  mfdGridded != null) {
////				bulge = 1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(mfdSupra, mfdGridded, false);
//				bulge = 1.0/ETAS_Utils.getScalingFactorToImposeGR_supraRates(mfdSupra, mfdGridded, false);
//				if(Double.isInfinite(bulge))
//					bulge = 1e3;				
//			}
			bulgeData.set(i, bulge);
		}
		progressBar.showProgress(false);

		String metadata = "Map from calling plotBulgeDepthMap(*) method";
		
		try {
				String url = mapGen.makeMapUsingServlet(bulgeData, "CharFactor at "+depth+" km depth", metadata, dirName);
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
		
		
		// make historgram
		double delta = Math.log10(1.01);
		double min = -4.0+delta/2;
		double max = 4;
		int num = (int)((max-min)/delta);
		HistogramFunction hist = new HistogramFunction(min,num,delta);
		for(int i=0;i<bulgeData.size();i++)
			if(isCubeInsideFaultPolygon[i] == 1)
				hist.add(bulgeData.get(i), 1.0);
//				hist.add(Math.log10(bulgeData.get(i)), 1.0);
		hist.normalizeBySumOfY_Vals();
		hist.setName("Histogram of log10 Values");
		HistogramFunction cumHist = hist.getCumulativeDistFunctionWithHalfBinOffset();
		cumHist.setInfo("Cumulative distribution");
		String info = "mean="+(float)hist.computeMean()+"\nmedian="+(float)cumHist.getFirstInterpolatedX(0.5)+"\nmode="+(float)hist.getMode()+"\n"+hist.toString();
		hist.setInfo(info);
		cumHist.setInfo(info);
		ArrayList<HistogramFunction> funcList = new ArrayList<HistogramFunction>();
		funcList.add(hist);
		funcList.add(cumHist);
		GraphWindow graph = new GraphWindow(funcList, "Histogram of log10 Bulge Values for Cubes with Faults"); 
		graph.setX_AxisLabel("Log10(1.0/GRcorr)");
		graph.setY_AxisLabel("Num or Fraction");
		double maxX,minX;
		if(bulgeData.getMaxZ()-bulgeData.getMinZ()< delta/10) {
			maxX=1;
			minX=-1;			
		}
		else {
			double absMax = Math.max(Math.abs(bulgeData.getMaxZ()), Math.abs(bulgeData.getMinZ()));
			maxX= absMax+delta/2.0;
			minX= -absMax-delta/2.0;

		}
		
			
		graph.setX_AxisRange(minX, maxX);
		File file = new File( new File(GMT_CA_Maps.GMT_DIR, dirName), "histogramForCubesWithFaults.pdf");

		try {
			graph.saveAsPDF(file.getAbsolutePath());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		return "For Bulge at depth Map: "+mapGen.getGMTFilesWebAddress()+" (deleted at midnight)";
	}
	
	
	
	
	
	/**
	 * TODO Move to utility class
	 * @param depth
	 * @param dirName
	 * @return
	 */
	public String plotBulgeAtDepthAndAboveMagMap(double depth, double mag, String dirName) {
		
		GMT_MapGenerator mapGen = GMT_CA_Maps.getDefaultGMT_MapGenerator();
		
		CPTParameter cptParam = (CPTParameter )mapGen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.UCERF3_RATIOS.getFileName());
		
		mapGen.setParameter(GMT_MapGenerator.MIN_LAT_PARAM_NAME,gridRegForCubes.getMinGridLat());
		mapGen.setParameter(GMT_MapGenerator.MAX_LAT_PARAM_NAME,gridRegForCubes.getMaxGridLat());
		mapGen.setParameter(GMT_MapGenerator.MIN_LON_PARAM_NAME,gridRegForCubes.getMinGridLon());
		mapGen.setParameter(GMT_MapGenerator.MAX_LON_PARAM_NAME,gridRegForCubes.getMaxGridLon());
		mapGen.setParameter(GMT_MapGenerator.GRID_SPACING_PARAM_NAME, gridRegForCubes.getLatSpacing());	// assume lat and lon spacing are same
		mapGen.setParameter(GMT_MapGenerator.LOG_PLOT_NAME,true);
//		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_FROMDATA);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME,GMT_MapGenerator.COLOR_SCALE_MODE_MANUALLY);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME,-3d);
		mapGen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,3d);
		
		
		GriddedGeoDataSet bulgeData = new GriddedGeoDataSet(gridRegForCubes, true);
		int depthIndex = getCubeDepthIndex(depth);
		int numCubesAtDepth = bulgeData.size();
		CalcProgressBar progressBar = new CalcProgressBar("Looping over all points", "junk");
		progressBar.showProgress(true);
		
		if(mfdForSrcArray == null) {
			computeMFD_ForSrcArrays(2.05, 8.95, 70);
		}

		for(int i=0; i<numCubesAtDepth;i++) {
			progressBar.updateProgress(i, numCubesAtDepth);
			int cubeIndex = getCubeIndexForRegAndDepIndices(i, depthIndex);
			if(getCubeMFD_GriddedSeisOnly(cubeIndex) == null) {
				bulgeData.set(i, 1.0);
				continue;
			}
			double rate1 = getCubeMFD_GriddedSeisOnly(cubeIndex).getCumRate(2.55)*Math.pow(10d, 2.5-mag);
			if(rate1==0.0) {
				bulgeData.set(i, 1.0);
				continue;
			}
			SummedMagFreqDist supraMFD = getCubeMFD_SupraSeisOnly(cubeIndex);
			int magIndex = supraMFD.getClosestXIndex(mag);
			double rate2 = getCubeMFD_SupraSeisOnly(cubeIndex).getCumRate(magIndex);
			bulgeData.set(i, rate2/rate1);
		}
		progressBar.showProgress(false);

		String metadata = "Map from calling plotBulgeAtDepthAndAboveMagMap(*) method";
		
		try {
				String url = mapGen.makeMapUsingServlet(bulgeData, "CharFactor at "+depth+" km depth and mag>="+mag, metadata, dirName);
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
		
		
		// make historgram
		double delta = Math.log10(1.01);
		double min = -4.0+delta/2;
		double max = 4;
		int num = (int)((max-min)/delta);
		HistogramFunction hist = new HistogramFunction(min,num,delta);
		for(int i=0;i<bulgeData.size();i++)
			if(isCubeInsideFaultPolygon[i] == 1) {
				if(bulgeData.get(i)>=hist.getMinX() &&  bulgeData.get(i)<=hist.getMaxX())
					hist.add(bulgeData.get(i), 1.0);
//				hist.add(Math.log10(bulgeData.get(i)), 1.0);
			}
		hist.normalizeBySumOfY_Vals();
		hist.setName("Histogram of log10 Values");
		HistogramFunction cumHist = hist.getCumulativeDistFunctionWithHalfBinOffset();
		cumHist.setInfo("Cumulative distribution");
		String info = "mean="+(float)hist.computeMean()+"\nmedian="+(float)cumHist.getFirstInterpolatedX(0.5)+"\nmode="+(float)hist.getMode()+"\n"+hist.toString();
		hist.setInfo(info);
		info = "mean="+(float)hist.computeMean()+"\nmedian="+(float)cumHist.getFirstInterpolatedX(0.5)+"\nmode="+(float)hist.getMode()+"\n"+cumHist.toString();
		cumHist.setInfo(info);
		ArrayList<HistogramFunction> funcList = new ArrayList<HistogramFunction>();
		funcList.add(hist);
		funcList.add(cumHist);
		GraphWindow graph = new GraphWindow(funcList, "Histogram of log10 Bulge Values for Cubes with Faults"); 
		graph.setX_AxisLabel("Log10(1.0/GRcorr)");
		graph.setY_AxisLabel("Num or Fraction");
		double maxX=3,minX=-3;
//		if(bulgeData.getMaxZ()-bulgeData.getMinZ()< delta/10) {
//			maxX=1;
//			minX=-1;			
//		}
//		else {
//			double absMax = Math.max(Math.abs(bulgeData.getMaxZ()), Math.abs(bulgeData.getMinZ()));
//			maxX= absMax+delta/2.0;
//			minX= -absMax-delta/2.0;
//
//		}
		
			
		graph.setX_AxisRange(minX, maxX);
		File file = new File( new File(GMT_CA_Maps.GMT_DIR, dirName), "histogramForCubesWithFaults.pdf");

		try {
			graph.saveAsPDF(file.getAbsolutePath());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		return "For Bulge at depth Map: "+mapGen.getGMTFilesWebAddress()+" (deleted at midnight)";
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
