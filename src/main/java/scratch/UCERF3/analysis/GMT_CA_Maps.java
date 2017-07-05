package scratch.UCERF3.analysis;

import java.io.File;
import java.io.IOException;

import org.dom4j.DocumentException;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSetMath;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.mapping.gmt.gui.GMT_MapGuiBean;
import org.opensha.commons.mapping.gmt.gui.ImageViewerWindow;
import org.opensha.commons.param.impl.CPTParameter;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.FindEquivUCERF2_Ruptures.FindEquivUCERF2_Ruptures;


/**
 * This class makes various GMT map plots (e.g., nucleation rates, participation rates, or ratios of these).
 * This needs internet connection, and the files are place in a subdir of UCERF3/data/scratch/GMT,
 * where the subdir name is specified by the "dirName" argument of each method.
 * 
 * 
 * @author field
 *
 *
 */
public class GMT_CA_Maps {
	
	final static String defaultNucleationCPT = GMT_CPT_Files.UCERF2_FIGURE_35.getFileName();
	final static String defaultParticipationCPT = GMT_CPT_Files.UCERF2_FIGURE_35.getFileName();
//	final static String defaultRatioCPT = GMT_CPT_Files.MAX_SPECTRUM.getFileName();
//	final static String defaultRatioCPT = GMT_CPT_Files.GMT_POLAR.getFileName();
	final static String defaultRatioCPT = GMT_CPT_Files.UCERF3_RATIOS.getFileName();
	final static String defaultColorScaleLimits=GMT_MapGenerator.COLOR_SCALE_MODE_MANUALLY;
	final static double defaultColorScaleMinNucl = -6.0;
	final static double defaultColorScaleMaxNucl = -1.0;
	final static double defaultColorScaleMinPart = -5.0;
	final static double defaultColorScaleMaxPart = -1.0;
	final static double defaultColorScaleMinRatio = -2.0;
	final static double defaultColorScaleMaxRatio = 2.0;
	final static double defaultColorScaleMinPDF = -6.0;
	final static double defaultColorScaleMaxPDF = -2.0;
	final static double defaultColorScaleMinMoRate = 13.0;
	final static double defaultColorScaleMaxMoRate = 17.0;

	
	final static boolean makeMapOnServer = true;
		
	final static double defaultMinLat = 31.5;
	final static double defaultMaxLat = 43.0;
	final static double defaultMinLon = -125.4;
	final static double defaultMaxLon = -113.1;
	final static double defaultGridSpacing = 0.1;
	final static String defaultTopoResolution = GMT_MapGenerator.TOPO_RESOLUTION_NONE;
	final static String defaultShowHighways = GMT_MapGenerator.SHOW_HIWYS_NONE;
	final static String defaultCoast = GMT_MapGenerator.COAST_DRAW;
	final static double defaultImageWidth = 6.5; 
	final static boolean defaultApplyGMT_Smoothing = false;
	final static boolean defaultBlackBackground = false;
	final static CaliforniaRegions.RELM_TESTING_GRIDDED defaultGridRegion  = RELM_RegionUtils.getGriddedRegionInstance();

	public final static File GMT_DIR = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "GMT");
	
	public static GMT_MapGenerator getDefaultGMT_MapGenerator() {
		
		GMT_MapGenerator gmt_MapGenerator = new GMT_MapGenerator();
		
		// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
		// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
		// but it won't succeed for the isAllowed(value) call.
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(defaultNucleationCPT);
		
//		// this prints out the options
//		Collection<CPT> cpts = ((ListBasedConstraint<CPT>) cptParam.getConstraint()).getAllowed();
//		for(CPT cpt:cpts)
//			System.out.println(cpt.getName());
		
		gmt_MapGenerator.setParameter(GMT_MapGenerator.MIN_LAT_PARAM_NAME, defaultMinLat);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.MIN_LON_PARAM_NAME, defaultMinLon);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.MAX_LAT_PARAM_NAME, defaultMaxLat);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.MAX_LON_PARAM_NAME, defaultMaxLon);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.GRID_SPACING_PARAM_NAME, defaultGridSpacing);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME, defaultColorScaleLimits);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, defaultColorScaleMinNucl);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, defaultColorScaleMaxNucl);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.TOPO_RESOLUTION_PARAM_NAME, defaultTopoResolution);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.SHOW_HIWYS_PARAM_NAME, defaultShowHighways);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COAST_PARAM_NAME, defaultCoast);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.IMAGE_WIDTH_NAME, defaultImageWidth);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.GMT_SMOOTHING_PARAM_NAME, defaultApplyGMT_Smoothing);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.BLACK_BACKGROUND_PARAM_NAME, defaultBlackBackground);
		
		return gmt_MapGenerator;

	}
	
	
	public static void makeMap(GeoDataSet geoDataSet, String scaleLabel, String metadata, 
			String dirName, GMT_MapGenerator gmt_MapGenerator) throws IOException {
		
		try {
			if(makeMapOnServer) {
				if (!GMT_DIR.exists())
					GMT_DIR.mkdir();
				String url = gmt_MapGenerator.makeMapUsingServlet(geoDataSet, scaleLabel, metadata, null);
				metadata += GMT_MapGuiBean.getClickHereHTML(gmt_MapGenerator.getGMTFilesWebAddress());
				File downloadDir = new File(GMT_DIR, dirName);
				if (!downloadDir.exists())
					downloadDir.mkdir();
				File zipFile = new File(downloadDir, "allFiles.zip");
				// construct zip URL
				String zipURL = url.substring(0, url.lastIndexOf('/')+1)+"allFiles.zip";
				FileUtils.downloadURL(zipURL, zipFile);
				FileUtils.unzipFile(zipFile, downloadDir);
				
				ImageViewerWindow imgView = new ImageViewerWindow(url,metadata, true);
			}
			else {
				gmt_MapGenerator.makeMapLocally(geoDataSet, scaleLabel, metadata, dirName);
			}
		} catch (GMT_MapException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	

	
	
	
	
	/**
	 * This returns and instance of the RELM gridded region used here
	 * @return
	 */
	public static GriddedRegion getDefaultGriddedRegion() {
		return defaultGridRegion;
	}
	
	
	/**
	 * This makes a map of the log10(ratio) of the rates for geoDataSet1 divided by geoDataSet2
	 * 
	 * @param geoDataSet1
	 * @param geoDataSet2
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException 
	 */
	public static void plotRatioOfRateMaps(GeoDataSet geoDataSet1, GeoDataSet geoDataSet2, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GeoDataSet ratioGeoDataSet = GeoDataSetMath.divide(geoDataSet1, geoDataSet2);
		
		System.out.println("MIN RATIO = "+ratioGeoDataSet.getMinZ());
		System.out.println("MAX RATIO = "+ratioGeoDataSet.getMaxZ());

		GMT_MapGenerator gmt_MapGenerator = getDefaultGMT_MapGenerator();
		
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, defaultColorScaleMinRatio);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, defaultColorScaleMaxRatio);
		
		// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
		// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
		// but it won't succeed for the isAllowed(value) call.
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(defaultRatioCPT);

		makeMap(ratioGeoDataSet, scaleLabel, metadata, dirName, gmt_MapGenerator);	
		
	}
	
	/**
	 * This makes a map of the log10(ratio) of the nucleation rates for erf1 divided by erf2 for the given mag range
	 * 
	 * @param erf1
	 * @param erf2
	 * @param minMag
	 * @param maxMag
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException
	 */
	public static void plotRatioOfNucleationRateMaps(ERF erf1, ERF erf2, double minMag, double maxMag, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GriddedGeoDataSet geoDataSet1 = ERF_Calculator.getNucleationRatesInRegion(erf1, defaultGridRegion, minMag, maxMag);
		GriddedGeoDataSet geoDataSet2 = ERF_Calculator.getNucleationRatesInRegion(erf2, defaultGridRegion, minMag, maxMag);
		
		plotRatioOfRateMaps(geoDataSet1, geoDataSet2, scaleLabel, metadata, dirName);
		
	}
	
	
	/**
	 * This makes a map of the log10(ratio) of the nucleation rates for faultSysSolution2 divided by faultSysSolution2 
	 * for the given mag range
	 * 
	 * @param faultSysSolution1
	 * @param faultSysSolution2
	 * @param minMag
	 * @param maxMag
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException
	 */
	public static void plotRatioOfNucleationRateMaps(FaultSystemSolution faultSysSolution1, FaultSystemSolution faultSysSolution2, double minMag, 
			double maxMag, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		FaultSystemSolutionERF erf1 = new FaultSystemSolutionERF(faultSysSolution1);
		erf1.updateForecast();
		
		FaultSystemSolutionERF erf2 = new FaultSystemSolutionERF(faultSysSolution2);
		erf2.updateForecast();
		
		plotRatioOfNucleationRateMaps(erf1, erf2, minMag, maxMag, scaleLabel, metadata, dirName);
		
	}




	/**
	 * This makes a map of the log10(ratio) of the participation rates for erf1 divided by erf2 for the given mag range
	 * 
	 * @param erf1
	 * @param erf2
	 * @param minMag
	 * @param maxMag
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException
	 */
	public static void plotRatioOfParticipationRateMaps(ERF erf1, ERF erf2, double minMag, double maxMag, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GriddedGeoDataSet geoDataSet1 = ERF_Calculator.getParticipationRatesInRegion(erf1, defaultGridRegion, minMag, maxMag);
		GriddedGeoDataSet geoDataSet2 = ERF_Calculator.getParticipationRatesInRegion(erf2, defaultGridRegion, minMag, maxMag);
		
		plotRatioOfRateMaps(geoDataSet1, geoDataSet2, scaleLabel, metadata, dirName);
		
	}
	
	/**
	 * This makes a map of the log10(ratio) of the participation rates for faultSysSolution1 divided by faultSysSolution2 
	 * for the given mag range
	 * 
	 * @param faultSysSolution1
	 * @param faultSysSolution2
	 * @param minMag
	 * @param maxMag
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException
	 */
	public static void plotRatioOfParticipationRateMaps(FaultSystemSolution faultSysSolution1, FaultSystemSolution faultSysSolution2, 
			double minMag, double maxMag, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		FaultSystemSolutionERF erf1 = new FaultSystemSolutionERF(faultSysSolution1);
		erf1.updateForecast();
		
		FaultSystemSolutionERF erf2 = new FaultSystemSolutionERF(faultSysSolution2);
		erf2.updateForecast();

		plotRatioOfParticipationRateMaps(erf1, erf2, minMag, maxMag, scaleLabel, metadata, dirName);
		
	}


	
	/**
	 * This makes a map of the log10 nucleation rates for events from the given erf 
	 * with mag>=minMag and mag<maxMag
	 * @param erf
	 * @param minMag
	 * @param maxMag
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException 
	 */
	public static void plotNucleationRateMap(ERF erf, double minMag, double maxMag, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GriddedGeoDataSet geoDataSet = ERF_Calculator.getNucleationRatesInRegion(erf, defaultGridRegion, minMag, maxMag);
		
		plotNucleationRateMap(geoDataSet, scaleLabel, metadata, dirName);
		
	}
	
	
	/**
	 * This makes a map of the log10 nucleation rates for events from the given faultSysSolution 
	 * with mag>=minMag and mag<maxMag
	 * @param erf
	 * @param minMag
	 * @param maxMag
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException 
	 */
	public static void plotNucleationRateMap(FaultSystemSolution faultSysSolution, double minMag, double maxMag, String scaleLabel,
			String metadata, String dirName) throws IOException {
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(faultSysSolution);
		erf.updateForecast();
		plotNucleationRateMap(erf, minMag, maxMag, scaleLabel, metadata, dirName);
	}
	
	
	
	/**
	 * This makes a map of the log10 nucleation rates for the given GeoDataSet
	 * 
	 * @param geoDataSet
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException 
	 */
	public static void plotNucleationRateMap(GeoDataSet geoDataSet, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GMT_MapGenerator gmt_MapGenerator = getDefaultGMT_MapGenerator();
		
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, defaultColorScaleMinNucl);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, defaultColorScaleMaxNucl);
		
		// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
		// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
		// but it won't succeed for the isAllowed(value) call.
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(defaultNucleationCPT);

		makeMap(geoDataSet, scaleLabel, metadata, dirName, gmt_MapGenerator);
	}
	
	
	
	
	/**
	 * This makes a map of the log10 participation rates for events from the given erf 
	 * with mag>=minMag and mag<maxMag
	 * @param erf
	 * @param minMag
	 * @param maxMag
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException 
	 */
	public static void plotParticipationRateMap(ERF erf, double minMag, double maxMag, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GriddedGeoDataSet geoDataSet = ERF_Calculator.getParticipationRatesInRegion(erf, defaultGridRegion, minMag, maxMag);
		
		plotParticipationRateMap(geoDataSet, scaleLabel, metadata, dirName);
		
	}
	
	
	/**
	 * This makes a map of the log10 participation rates for events from the given faultSysSolution 
	 * with mag>=minMag and mag<maxMag
	 * @param erf
	 * @param minMag
	 * @param maxMag
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException 
	 */
	public static void plotParticipationRateMap(FaultSystemSolution faultSysSolution, double minMag, double maxMag, String scaleLabel,
			String metadata, String dirName) throws IOException {
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(faultSysSolution);
		erf.updateForecast();
		plotParticipationRateMap(erf, minMag, maxMag, scaleLabel, metadata, dirName);
	}
	
	
	
	/**
	 * This makes a map of the log10 participation rates for the given GeoDataSet
	 * 
	 * @param geoDataSet
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException 
	 */
	public static void plotParticipationRateMap(GeoDataSet geoDataSet, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GMT_MapGenerator gmt_MapGenerator = getDefaultGMT_MapGenerator();
		
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, defaultColorScaleMinPart);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, defaultColorScaleMaxPart);
		
		// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
		// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
		// but it won't succeed for the isAllowed(value) call.
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(defaultParticipationCPT);

		makeMap(geoDataSet, scaleLabel, metadata, dirName, gmt_MapGenerator);
	}


	
	/**
	 * This tests whether we can reproduce Figure 35 of our report (BSSA version), where the data
	 * generated should be equal to what one get running the main method of
	 * WGCEP_UCERF2_2_Final/analysis/GenerateFilesForParticipationProbMaps.
	 * 
	 *  The values are not exactly the same because the Vivpin's UCERF2 calculations suffered from a
	 *  bin rounding problem (he created an MFD with values at 5.0, 5.1, ... (rather than at the
	 *  UCERF2 background seis mag values of 5.05, 5.15, etc), and then used the method 
	 *  SummedMagFreqDist.addResampledMagRate(), which has warnings of potential biases doing this.
	 *  
	 *  I saw differences of 30% for the M 6.7 data, but in general the two match well visually.
	 */
	public static void testMakeUCERF2_Fig35(String dirName) throws IOException {
		
		MeanUCERF2 meanUCERF2 = new MeanUCERF2();
		meanUCERF2.setParameter(UCERF2.RUP_OFFSET_PARAM_NAME, new Double(5.0));
		meanUCERF2.getParameter(UCERF2.PROB_MODEL_PARAM_NAME).setValue(MeanUCERF2.PROB_MODEL_WGCEP_PREF_BLEND);	
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_CROSSHAIR);
		meanUCERF2.getTimeSpan().setDuration(30d);
		meanUCERF2.updateForecast();
		
		GMT_MapGenerator gen = getDefaultGMT_MapGenerator();
		CPTParameter cptParam = (CPTParameter )gen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.UCERF2_FIGURE_35.getFileName());
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -5.0);
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, 0.0);

		// mag 5
		GriddedGeoDataSet geoDataSet = ERF_Calculator.getParticipationRatesInRegion(meanUCERF2, defaultGridRegion, 5.0, 10);	
		for(int i=0; i<geoDataSet.size();i++) {	// convert values to prob in 30 years
			double newVal = 1-Math.exp(-geoDataSet.get(i)*30.0);
			geoDataSet.set(i, newVal);
		}
		makeMap(geoDataSet, "Test of UCERF2 Fig 35, M=5.0", "GMT_CA_Maps.testMakeUCERF2_Fig35()", dirName+"M5_0", gen);
	
		// mag 6.7
		geoDataSet = ERF_Calculator.getParticipationRatesInRegion(meanUCERF2, defaultGridRegion, 6.7, 10);	
		for(int i=0; i<geoDataSet.size();i++) {	// convert values to prob in 30 years
			double newVal = 1-Math.exp(-geoDataSet.get(i)*30.0);
			geoDataSet.set(i, newVal);
		}
		makeMap(geoDataSet, "Test of UCERF2 Fig 35, M=6.7", "GMT_CA_Maps.testMakeUCERF2_Fig35()", dirName+"M6_7", gen);

		// mag 7.7
		geoDataSet = ERF_Calculator.getParticipationRatesInRegion(meanUCERF2, defaultGridRegion, 7.7, 10);	
		for(int i=0; i<geoDataSet.size();i++) {	// convert values to prob in 30 years
			double newVal = 1-Math.exp(-geoDataSet.get(i)*30.0);
			geoDataSet.set(i, newVal);
		}
		makeMap(geoDataSet, "Test of UCERF2 Fig 35, M=7.7", "GMT_CA_Maps.testMakeUCERF2_Fig35()", dirName+"M7_7", gen);

	}



	/**
	 * Like the test for Figure 35, this too has some differences that presumably can be attributed to the
	 * offset magnitude bins.  They seem close enough to not warrant further investigation.
	 * @param dirName
	 * @throws IOException
	 */
	public static void testMakeUCERF2_Fig19(String dirName) throws IOException {
		
		MeanUCERF2 meanUCERF2 = new MeanUCERF2();
		meanUCERF2.setParameter(UCERF2.RUP_OFFSET_PARAM_NAME, new Double(5.0));
		meanUCERF2.getParameter(UCERF2.PROB_MODEL_PARAM_NAME).setValue(MeanUCERF2.PROB_MODEL_WGCEP_PREF_BLEND);	
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_ONLY);
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_CROSSHAIR);
		meanUCERF2.getTimeSpan().setDuration(30d);
		meanUCERF2.updateForecast();
		
		GMT_MapGenerator gen = getDefaultGMT_MapGenerator();
		CPTParameter cptParam = (CPTParameter )gen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -5.0);
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, -3.0);

		GriddedGeoDataSet geoDataSet = ERF_Calculator.getNucleationRatesInRegion(meanUCERF2, defaultGridRegion, 5.0, 10);	
		makeMap(geoDataSet, "TempTest of UCERF2", "GMT_CA_Maps.tempTestUCERF2()", dirName, gen);
		
		
	}
	
	
	/**
	 * @param dirName
	 * @throws IOException
	 */
	public static void tempTestUCERF2(String dirName) throws IOException {
		
		MeanUCERF2 meanUCERF2 = new MeanUCERF2();
		meanUCERF2.setParameter(UCERF2.RUP_OFFSET_PARAM_NAME, new Double(5.0));
		meanUCERF2.getParameter(UCERF2.PROB_MODEL_PARAM_NAME).setValue(MeanUCERF2.PROB_MODEL_WGCEP_PREF_BLEND);	
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
		meanUCERF2.getTimeSpan().setDuration(30d);
		meanUCERF2.updateForecast();
		
		GMT_MapGenerator gen = getDefaultGMT_MapGenerator();
		CPTParameter cptParam = (CPTParameter )gen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -6.0);
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, -2.0);

		GriddedGeoDataSet geoDataSet = ERF_Calculator.getNucleationRatesInRegion(meanUCERF2, defaultGridRegion, 6.0, 10);	
		for(int i=0; i<geoDataSet.size();i++) {	// convert num expected in 5 years
			double newVal = geoDataSet.get(i)*5.0;
			geoDataSet.set(i, newVal);
		}
		makeMap(geoDataSet, "Test of UCERF2 Fig 19a", "GMT_CA_Maps.testMakeUCERF2_Fig19()", dirName+"_a", gen);
		
		
		// now make the 2nd part of the plot
		GriddedGeoDataSet bulgeDataSet = getM6_5_BulgeData(meanUCERF2, 6.5, 0.8);
		cptParam = (CPTParameter )gen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.GMT_POLAR.getFileName());
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -1.0);
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,  1.0);
		makeMap(bulgeDataSet, "Test of UCERF2 Fig 19b", "GMT_CA_Maps.testMakeUCERF2_Fig19()", dirName+"_b", gen);
	
	}

	
	
	/**
	 * This makes a map of the ratio of M>=mag rates to the rate projected from M 5.0 to mag using
	 * a GR distribution with the given b-value (like shown in figure 19 of the UCERF2 report). 
	 * There is no version of this that takes a FaultSystemSolution because you need smaller events to
	 * make the plot meaningful.
	 * Note - this plot is misleading for UCERF3 because subseismo events are distributed over grid nodes
	 * inside polygons but supra-seismo events are not.
	 * @param erf
	 * @param mag - magnitude for the ratio
	 * @param bValue - b-value used for extrapolation
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException 
	 */
	public static void plotM6_5_BulgeMap(ERF erf, double mag, double bValue, String scaleLabel, String metadata, String dirName) throws IOException {
		
		GriddedGeoDataSet bulgeDataSet = getM6_5_BulgeData(erf, mag, bValue);

		GMT_MapGenerator gen = getDefaultGMT_MapGenerator();
		CPTParameter cptParam = (CPTParameter )gen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.GMT_POLAR.getFileName());
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -1.0);
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,  1.0);

		makeMap(bulgeDataSet, scaleLabel, metadata, dirName, gen);

	}
	
	
	public static void plotBulgeFromFirstGenAftershocksMap(FaultSystemSolutionERF erf, String scaleLabel, String metadata, String dirName, boolean applyCorr) throws IOException {
		
		GriddedGeoDataSet bulgeDataSet = new GriddedGeoDataSet(defaultGridRegion, true);
		
		SummedMagFreqDist[] subMFD_Array = FaultSystemSolutionCalc.getSubSeismNucleationMFD_inGridNodes((InversionFaultSystemSolution)erf.getSolution(), defaultGridRegion);
		SummedMagFreqDist[] supraMFD_Array = FaultSystemSolutionCalc.getSupraSeismNucleationMFD_inGridNodes((InversionFaultSystemSolution)erf.getSolution(), defaultGridRegion);

		for(int i=0;i<bulgeDataSet.size();i++) {
			if(subMFD_Array[i] != null) {
				
				if(applyCorr) {
					double corr = ETAS_Utils.getScalingFactorToImposeGR_numPrimary(supraMFD_Array[i], subMFD_Array[i], false);
					if(corr<1.0)
						supraMFD_Array[i].scale(corr);					
				}
				
				double val = 1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(supraMFD_Array[i], subMFD_Array[i], false);
				bulgeDataSet.set(i, val);
			}
			else
				bulgeDataSet.set(i, 1.0/0.0);	// this plots as white
		}

		GMT_MapGenerator gen = getDefaultGMT_MapGenerator();
		CPTParameter cptParam = (CPTParameter )gen.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.UCERF3_RATIOS.getFileName());
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -2.0);
		gen.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME,  2.0);

		makeMap(bulgeDataSet, scaleLabel, metadata, dirName, gen);

	}


	
	/**
	 * This computes a GriddedGeoDataSet for the ratio of M>=mag rates to the rate projected from M 5.0 to mag using
	 * a GR distribution with the given b-value (like shown in figure 19 of the UCERF2 report). 
	 * 
	 * Note - this calculation is misleading for UCERF3 because sub-seismo events are distributed over grid 
	 * nodes inside polygons, but supra-seismo events are not.
	 * @param erf
	 * @param mag - magnitude for the ratio
	 * @param bValue - b-value used for extrapolation
	 */
	private static GriddedGeoDataSet getM6_5_BulgeData(ERF erf, double mag, double bValue) {
		GriddedGeoDataSet geoDataSet5_0 = ERF_Calculator.getNucleationRatesInRegion(erf, defaultGridRegion, 5.0, 10.0);
		GriddedGeoDataSet geoDataSetMag = ERF_Calculator.getNucleationRatesInRegion(erf, defaultGridRegion, mag, 10.0);
		
		GriddedGeoDataSet bulgeDataSet = new GriddedGeoDataSet(defaultGridRegion, true);
		
		double m5to6pt5 = Math.pow(10.0,-(mag-5.0)*bValue);	// GR extension
		
		for(int i=0; i<bulgeDataSet.size();i++) {	
			bulgeDataSet.set(i, geoDataSetMag.get(i)/(geoDataSet5_0.get(i)*m5to6pt5));
		}
		
		return bulgeDataSet;
	}
	
	
	/**
	 * This makes a map of the log10 spatial PDF the given GeoDataSet
	 * 
	 * @param geoDataSet
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException 
	 */
	public static void plotSpatialPDF_Map(GeoDataSet geoDataSet, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GMT_MapGenerator gmt_MapGenerator = getDefaultGMT_MapGenerator();
		
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, defaultColorScaleMinPDF);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, defaultColorScaleMaxPDF);
		
		// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
		// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
		// but it won't succeed for the isAllowed(value) call.
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(defaultNucleationCPT);

		makeMap(geoDataSet, scaleLabel, metadata, dirName, gmt_MapGenerator);
	}
	
	public static void plotSpatialMoRate_Map(GeoDataSet geoDataSet, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GMT_MapGenerator gmt_MapGenerator = getDefaultGMT_MapGenerator();
		
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, defaultColorScaleMinMoRate);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, defaultColorScaleMaxMoRate);
		
		// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
		// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
		// but it won't succeed for the isAllowed(value) call.
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(defaultNucleationCPT);

		makeMap(geoDataSet, scaleLabel, metadata, dirName, gmt_MapGenerator);
	}

	
	/**
	 * This plot a map of magnitudes (between M 3 and 9); used for plotting max magnitudes
	 * @param geoDataSet
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException
	 */
	public static void plotMagnitudeMap(GeoDataSet magGeoDataSet, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GMT_MapGenerator gmt_MapGenerator = getDefaultGMT_MapGenerator();
		
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, 4d);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, 10d);
		
		// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
		// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
		// but it won't succeed for the isAllowed(value) call.
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		
		gmt_MapGenerator.setParameter(GMT_MapGenerator.LOG_PLOT_NAME, false);

		makeMap(magGeoDataSet, scaleLabel, metadata, dirName, gmt_MapGenerator);
	}

	
	/**
	 * This makes a map of b-values
	 * @param erf
	 * @param min_bValMag - If Double.NaN, this defaults to the smallest mag with non-zero rate
	 * @param max_bValMag - If Double.NaN, this defaults to the largest mag with non-zero rate
	 * @param scaleLabel
	 * @param metadata
	 * @param dirName
	 * @throws IOException 
	 */
	public static void plot_bValueMap(ERF erf, double min_bValMag, double max_bValMag, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GriddedGeoDataSet geoDataSet = ERF_Calculator.get_bValueAtPointsInRegion(erf, defaultGridRegion, min_bValMag, max_bValMag);
		
		GMT_MapGenerator gmt_MapGenerator = getDefaultGMT_MapGenerator();
		
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -2d);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, 2d);
		
		gmt_MapGenerator.setParameter(GMT_MapGenerator.LOG_PLOT_NAME, false);
		
		// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
		// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
		// but it won't succeed for the isAllowed(value) call.
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(defaultNucleationCPT);

		makeMap(geoDataSet, scaleLabel, metadata, dirName, gmt_MapGenerator);
		
	}


	

	/**
	 * @param args
	 * @throws IOException 
	 * @throws DocumentException 
	 */
	public static void main(String[] args) throws IOException, DocumentException {
		
		testMakeUCERF2_Fig19("tempTestHere");
		
//		ERF modMeanUCERF2 = FindEquivUCERF2_Ruptures.buildERF(FaultModels.FM3_1);
//		plot_bValueMap(modMeanUCERF2, Double.NaN, Double.NaN, "UCERF2 bVals","test", "test_bValMap");
		
//		File solutionDir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions");
//		File solutionFile = new File(solutionDir, "FM3_1_GLpABM_MaEllB_DsrTap_DrEllB_GR_VarAseis0.1_VarOffAseis0.5_VarMFDMod1_VarNone_sol.zip");
////		File solutionFile = new File(solutionDir, "FM3_1_GLpABM_MaEllB_DsrTap_DrEllB_Char_VarAseis0.1_VarOffAseis0.5_VarMFDMod1_VarNone_sol.zip");
//		FaultSystemSolution fltSysSol = SimpleFaultSystemSolution.fromFile(solutionFile);
//		InversionFaultSystemSolution invFltSysSol = new InversionFaultSystemSolution(fltSysSol);
//		System.out.println(invFltSysSol.getImpliedOffFaultStatewideMFD());
//		FaultSystemSolutionPoissonERF erf = new FaultSystemSolutionPoissonERF(fltSysSol);
//		erf.updateForecast();
//		plot_bValueMap(erf, Double.NaN, Double.NaN, "Char Inversion bVals","test", "test_CharInv_bValMap");
		
		// ****** TEST FROM A FaultSystemRupSet ******

//		File solutionDir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions");
//		File solutionFile = new File(solutionDir, "FM3_1_GLpABM_MaAvU2_DsrTap_DrAvU2_Unconst_sol.zip");
//   		FaultSystemSolution fltSysSol = SimpleFaultSystemSolution.fromFile(solutionFile);
//		plotNucleationRateMap(fltSysSol, 0, 10, "TEST fltSysSol Nucl", "test meta data FOR fltSysSol", "testFltSysSolNucl");
//		plotParticipationRateMap(fltSysSol, 0, 10, "TEST fltSysSol Part", "test meta data FOR fltSysSol", "testFltSysSolPart");

		
		// ************* TEST FROM AN ERF ****************
//		System.out.println("Getting ERF");
//		ERF modMeanUCERF2 = FindEquivUCERF2_Ruptures.buildERF(FaultModels.FM3_1);
//		System.out.println("Making xyzData");
//		GriddedGeoDataSet geoDataSet = ERF_Calculator.getNucleationRatesInRegion(modMeanUCERF2, defaultGridRegion, 0, 10);

//		GMT_CA_Maps.plotNucleationRateMap(modMeanUCERF2, 0, 10, "TEST", "test meta data", "junk_GMT_Maps");
//		GMT_CA_Maps.plotNucleationRateMap(geoDataSet, "TEST", "test meta data", "test_GMT_Maps");

//		System.out.println("Plotting ratio");
//		GMT_CA_Maps.plotRatioOfRateMaps(geoDataSet, geoDataSet, "TEST", "test meta data", "testRatio_GMT_Maps");
		System.out.println("Done");
		
		
		// ********* TEST OF UCERF2 PLOTS **************
		
//		testMakeUCERF2_Fig35("ucerf2_Fig35_Test");

//		testMakeUCERF2_Fig19("ucerf2_Fig19_Test");


	}

}
