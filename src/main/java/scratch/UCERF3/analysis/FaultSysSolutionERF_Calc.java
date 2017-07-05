package scratch.UCERF3.analysis;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.calc.FractileCurveCalculator;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.WeightedFuncList;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSetMath;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.mapping.gmt.elements.TopographicSlopeFile;
import org.opensha.commons.mapping.gmt.gui.GMT_MapGuiBean;
import org.opensha.commons.mapping.gmt.gui.ImageViewerWindow;
import org.opensha.commons.param.impl.CPTParameter;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.XMLUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;
import org.opensha.sha.earthquake.param.BPT_AperiodicityParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2_TimeDependentEpistemicList;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2_TimeIndependentEpistemicList;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.FaultSystemSolutionFetcher;
import scratch.UCERF3.analysis.CompoundFSSPlots.MapBasedPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.MapPlotData;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.erf.FSSRupsInRegionCache;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.UCERF2_Mapped.UCERF2_FM2pt1_FaultSysSolTimeDepERF;
import scratch.UCERF3.erf.mean.MeanUCERF3;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.griddedSeismicity.SmallMagScaling;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.logicTree.APrioriBranchWeightProvider;
import scratch.UCERF3.logicTree.BranchWeightProvider;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.LastEventData;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.UCERF3.utils.UCERF2_MFD_ConstraintFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2;
import scratch.UCERF3.utils.UCERF2_Section_MFDs.UCERF2_Section_MFDsCalc;
import scratch.UCERF3.utils.UCERF2_Section_MFDs.UCERF2_Section_TimeDepMFDsCalc;
import scratch.kevin.ucerf3.TestPDFCombine;

public class FaultSysSolutionERF_Calc {
	
	
	/**
	 * This returns and instance of a UCERF3_FaultSysSol_ERF where the duration 
	 * has been set as 1 year and the forecast has been updated
	 * @param faultSysSolZipFile
	 * @return
	 */
	public static FaultSystemSolutionERF getUCERF3_ERF_Instance(File faultSysSolZipFile) {
		InversionFaultSystemSolution invFss;
		try {
			invFss = FaultSystemIO.loadInvSol(faultSysSolZipFile);
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}

		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(invFss);

		erf.getParameter(AleatoryMagAreaStdDevParam.NAME).setValue(0.0);
		erf.getTimeSpan().setDuration(1d);
		erf.updateForecast();
		
		return erf;

	}
	
	
	

	
	
	
	public static void makePrelimReportPartPlots() {

		try {
			// UCERF2_FaultSysSol_ERF
//			UCERF2_FaultSysSol_ERF erf = new UCERF2_FaultSysSol_ERF();
//			erf.updateForecast();
//			GMT_CA_Maps.plotParticipationRateMap(erf, 6.7, 10d, "testUCERF3_ERF", "test", "testUCERF3_ERF");
			
//			ModMeanUCERF2 erf= new ModMeanUCERF2();
//			erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
//			erf.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
//			erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
//			erf.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
//			erf.getTimeSpan().setDuration(1d);
//			erf.updateForecast();
//			String fileName = "UCERF2";

//			File file = new File("/Users/field/Neds_Creations/CEA_WGCEP/UCERF3/PrelimModelReport/Figures/Fig16_ERF_ParticipationMaps/zipFiles/FM3_1_GLpABM_MaEllB_DsrTap_DrEllB_Char_VarAseis0.1_VarOffAseis0.5_VarMFDMod1_VarNone_sol.zip");
//			UCERF3_FaultSysSol_ERF erf = getUCERF3_ERF_Instance(file, SpatialSeisPDF.AVG_DEF_MODEL,SmallMagScaling.MO_REDUCTION);
//			String fileName = "UCERF3_CHAR_DefMod_MoBal";
//			UCERF3_FaultSysSol_ERF erf = getUCERF3_ERF_Instance(file, SpatialSeisPDF.AVG_DEF_MODEL,SmallMagScaling.SPATIAL);
//			String fileName = "UCERF3_CHAR_DefMod_Seis";
//			UCERF3_FaultSysSol_ERF erf = getUCERF3_ERF_Instance(file, SpatialSeisPDF.UCERF3,SmallMagScaling.SPATIAL);
//			String fileName = "UCERF3_CHAR_U3smSeis_Seis";
			
			File file = new File("/Users/field/Neds_Creations/CEA_WGCEP/UCERF3/PrelimModelReport/Figures/Fig16_ERF_ParticipationMaps/zipFiles/FM3_1_GLpABM_MaEllB_DsrTap_DrEllB_GR_VarAseis0.1_VarOffAseis0.5_VarMFDMod1_VarNone_sol.zip");
			FaultSystemSolutionERF erf = getUCERF3_ERF_Instance(file);
			String fileName = "UCERF3_GR_DefMod_MoBal";
//			UCERF3_FaultSysSol_ERF erf = getUCERF3_ERF_Instance(file, SpatialSeisPDF.AVG_DEF_MODEL,SmallMagScaling.SPATIAL);
//			String fileName = "UCERF3_GR_DefMod_Seis";
//			UCERF3_FaultSysSol_ERF erf = getUCERF3_ERF_Instance(file, SpatialSeisPDF.UCERF3,SmallMagScaling.SPATIAL);
//			String fileName = "UCERF3_GR_U3smSeis_Seis";


			
			GMT_CA_Maps.plotParticipationRateMap(erf, 5.0, 10d, fileName+"_Part5pt0", "test", fileName+"_Part5pt0");
			GMT_CA_Maps.plotParticipationRateMap(erf, 6.7, 10d, fileName+"_Part6pt7", "test", fileName+"_Part6pt7");
			GMT_CA_Maps.plotParticipationRateMap(erf, 7.7, 10d, fileName+"_Part7pt7", "test", fileName+"_Part7pt7");
			GMT_CA_Maps.plotM6_5_BulgeMap(erf, 6.5, 1.0, fileName+"_Bulge", "test", fileName+"_Bulge");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	
	public static void makeDraftFinalModelReportPartPlots() {

		try {
			// UCERF2_FaultSysSol_ERF
//			UCERF2_FaultSysSol_ERF erf = new UCERF2_FaultSysSol_ERF();
//			erf.updateForecast();
//			GMT_CA_Maps.plotParticipationRateMap(erf, 6.7, 10d, "testUCERF3_ERF", "test", "testUCERF3_ERF");
			
//			ModMeanUCERF2 erf= new ModMeanUCERF2();
//			erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
//			erf.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
//			erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
//			erf.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
//			erf.getTimeSpan().setDuration(1d);
//			erf.updateForecast();
//			String fileName = "UCERF2";

			File file = new File("/Users/field/Neds_Creations/CEA_WGCEP/UCERF3/draftFinalModelReport/FaultSystemSolutions/FM3_1_ZENG_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_sol.zip");
			FaultSystemSolutionERF erf = new FaultSystemSolutionERF(FaultSystemIO.loadSol(file));
			erf.updateForecast();
			String fileName = "UCERF3_Char_Ref_Zeng_Model";
			
//			File file = new File("/Users/field/Neds_Creations/CEA_WGCEP/UCERF3/draftFinalModelReport/FaultSystemSolutions/FM3_1_ZENG_EllB_DsrUni_GRConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_sol.zip");
//			UCERF3_FaultSysSol_ERF erf = new UCERF3_FaultSysSol_ERF(file);
//			erf.updateForecast();
//			String fileName = "UCERF3_GR_Ref_Zeng_Model";
			
			GMT_CA_Maps.plotParticipationRateMap(erf, 5.0, 10d, fileName+"_Part5pt0", "test", fileName+"_Part5pt0");
			GMT_CA_Maps.plotParticipationRateMap(erf, 6.7, 10d, fileName+"_Part6pt7", "test", fileName+"_Part6pt7");
			GMT_CA_Maps.plotParticipationRateMap(erf, 7.7, 10d, fileName+"_Part7pt7", "test", fileName+"_Part7pt7");
			GMT_CA_Maps.plotM6_5_BulgeMap(erf, 6.5, 1.0, fileName+"_Bulge", "test", fileName+"_Bulge");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	/**
	 * This makes a nucleation rate map for the UCERF3.3 time-independent model, where section rates are properly 
	 * mapped onto polygon grid nodes.  The mean fault-system solution for each fault model is used (not a true 
	 * branch average).  Background seismicity is treated as point sources.
	 * 
	 * @param includeAftershocks
	 */
	public static void makeAveMoRateMapForU3pt3(boolean includeAftershocks) {

		try {
			
			String fileName = "UCERF3_MEAN_BRANCH_AVG_SOL";
			String scaleLabel = "Moment Rate (Nm/yr)";
			String metadata ="includeAftershocks="+includeAftershocks+"; \n";
			String dirName = fileName+"_MoRate";
			System.out.println(dirName);


			// average solution for FM 3.1
			String f1 ="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
			File file1 = new File(f1);
			System.out.println("Instantiating ERF1...");
			FaultSystemSolutionERF erf1 = new FaultSystemSolutionERF(FaultSystemIO.loadSol(file1));
			erf1.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(!includeAftershocks);
			erf1.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
			erf1.getParameter(BackgroundRupParam.NAME).setValue(BackgroundRupType.POINT);	
			erf1.updateForecast();
			System.out.println(erf1.getAdjustableParameterList().toString());
			
//			takes a long time:
//			System.out.println("testing MoRate for erf1:");
//			double test1 = ERF_Calculator.getTotalMomentRateInRegion(erf1, GMT_CA_Maps.defaultGridRegion);
//			System.out.println("test1="+test1);
			
			// get the fault system solution and the polygon manager
			InversionFaultSystemSolution fss1 = (InversionFaultSystemSolution) erf1.getSolution();
			FaultPolyMgr fltPolyMgr1 = fss1.getRupSet().getInversionTargetMFDs().getGridSeisUtils().getPolyMgr();
			
			// compute moment rates for supra-seis rups mapped onto grid nodes inside polygons
			System.out.println("calculation section moment rates...");
			double[] sectMoRates1 = calcMomentRateForAllFaultSections(erf1);
			GriddedGeoDataSet supraSeisMoRates_xyzData1 = new GriddedGeoDataSet(GMT_CA_Maps.defaultGridRegion, true);	// true makes X latitude
			for(int s=0; s<sectMoRates1.length;s++) {
				Map<Integer, Double> nodesForSectMap = fltPolyMgr1.getNodeFractions(s);
				Set<Integer> nodeIndicesList = nodesForSectMap.keySet();
				for(int index:nodeIndicesList) {
					double oldRate = supraSeisMoRates_xyzData1.get(index);
					supraSeisMoRates_xyzData1.set(index, oldRate+nodesForSectMap.get(index)*sectMoRates1[s]);
				}
			}

			System.out.println("ERF_Calculator.getNucleationRatesInRegion...");
			erf1.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);	// don't include fault based sources here; they will be computed separately
			erf1.updateForecast();
			GriddedGeoDataSet geoDataSetForGridSeis1 = ERF_Calculator.getMomentRatesInRegion(erf1, GMT_CA_Maps.defaultGridRegion);


			// average solution for FM 3.1
			String f2 ="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_MEAN_BRANCH_AVG_SOL.zip";
			File file2 = new File(f2);
			System.out.println("Instantiating ERF2...");
			FaultSystemSolutionERF erf2 = new FaultSystemSolutionERF(FaultSystemIO.loadSol(file2));
			erf2.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(!includeAftershocks);
			erf2.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
			erf2.getParameter(BackgroundRupParam.NAME).setValue(BackgroundRupType.POINT);	
			erf2.updateForecast();
			System.out.println(erf2.getAdjustableParameterList().toString());
			
//			takes a long time:
//			System.out.println("testing MoRate for erf2:");
//			double test2 = ERF_Calculator.getTotalMomentRateInRegion(erf2, GMT_CA_Maps.defaultGridRegion);
//			System.out.println("test2="+test2);
			
			// get the fault system solution and the polygon manager
			InversionFaultSystemSolution fss2 = (InversionFaultSystemSolution) erf2.getSolution();
			FaultPolyMgr fltPolyMgr2 = fss2.getRupSet().getInversionTargetMFDs().getGridSeisUtils().getPolyMgr();
			
			// compute moment rates for supra-seis rups mapped onto grid nodes inside polygons
			System.out.println("calculation section moment rates 2...");
			double[] sectMoRates2 = calcMomentRateForAllFaultSections(erf2);
			GriddedGeoDataSet supraSeisMoRates_xyzData2 = new GriddedGeoDataSet(GMT_CA_Maps.defaultGridRegion, true);	// true makes X latitude
			for(int s=0; s<sectMoRates2.length;s++) {
				Map<Integer, Double> nodesForSectMap = fltPolyMgr2.getNodeFractions(s);
				Set<Integer> nodeIndicesList = nodesForSectMap.keySet();
				for(int index:nodeIndicesList) {
					double oldRate = supraSeisMoRates_xyzData2.get(index);
					supraSeisMoRates_xyzData2.set(index, oldRate+nodesForSectMap.get(index)*sectMoRates2[s]);
				}
			}

			System.out.println("ERF_Calculator.getNucleationRatesInRegion 2...");
			erf2.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);	// don't include fault based sources here; they will be computed separately
			erf2.updateForecast();
			GriddedGeoDataSet geoDataSetForGridSeis2 = ERF_Calculator.getMomentRatesInRegion(erf2, GMT_CA_Maps.defaultGridRegion);


			GriddedGeoDataSet sumGeoDataSet = new GriddedGeoDataSet(GMT_CA_Maps.defaultGridRegion, true);	// true makes X latitude
			
			// average over the two fault models
			double totalRate=0;
			for(int i=0;i<sumGeoDataSet.size();i++) {
				double ave = 0.5*(geoDataSetForGridSeis1.get(i)+geoDataSetForGridSeis2.get(i)+supraSeisMoRates_xyzData1.get(i)+supraSeisMoRates_xyzData2.get(i));
				totalRate += ave;
				sumGeoDataSet.set(i, ave);
			}
			
			System.out.println("Total Moment Rate In Region : "+totalRate +" Nm/yr");
			metadata += "Total Moment Rate In Region : "+totalRate +" Nm/yr";

			GMT_MapGenerator gmt_MapGenerator = GMT_CA_Maps.getDefaultGMT_MapGenerator();

			System.out.println("Making GMT Map...");
			//override default scale
			gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, 13.);
			gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, 17.);
//			gmt_MapGenerator.setParameter(GMT_MapGenerator.TOPO_RESOLUTION_PARAM_NAME, GMT_MapGenerator.TOPO_RESOLUTION_30_GLOBAL);
//			gmt_MapGenerator.setParameter(GMT_MapGenerator.GMT_SMOOTHING_PARAM_NAME, true);
			gmt_MapGenerator.setParameter(GMT_MapGenerator.DPI_PARAM_NAME, 300);
			gmt_MapGenerator.setParameter(GMT_MapGenerator.BLACK_BACKGROUND_PARAM_NAME, false);
			gmt_MapGenerator.setParameter(GMT_MapGenerator.KML_PARAM_NAME, true);


			// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
			// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
			// but it won't succeed for the isAllowed(value) call.
			CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
			cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());

			GMT_CA_Maps.makeMap(sumGeoDataSet, scaleLabel, metadata, dirName, gmt_MapGenerator);

			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	
	
	
	
	
	/**
	 * This makes the iconic figure of U3.3, Fm3.1 participation rate maps, where section rates are properly 
	 * mapped onto polygon grid nodes, and topography is included. Aftershocks are included.
	 * Results are in OpenSHA/dev/scratch/UCERF3/data/scratch/GMT/COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL_Part_*
	 * (where the "*" part is the minMagArray value(s) set below)
	 */
	public static void makeIconicFigureForU3pt3_and_FM3pt1() {

		try {
			
			double[] minMagArray = {5,6.7};	// the mags to iterate over
			double maxMag=10d;

			// average solution for FM 3.1
			String f ="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
			File file = new File(f);

			System.out.println("Instantiating ERF...");
			FaultSystemSolutionERF erf = new FaultSystemSolutionERF(FaultSystemIO.loadSol(file));
			erf.getParameter(AleatoryMagAreaStdDevParam.NAME).setValue(0.12);
			erf.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(false);
			erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);	// don't include fault based sources here
			erf.getParameter("Treat Background Seismicity As").setValue(BackgroundRupType.CROSSHAIR);	// this creates some faint cross artifacts due to tighter smoothing
			erf.updateForecast();
			String fileName = "COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL";
			
			System.out.println(erf.getAdjustableParameterList().toString());
			
			
			String scaleLabel = "Participation Rate";
			String metadata =" ";
			
			// get the fault system solution and the polygon manager
			InversionFaultSystemSolution fss = (InversionFaultSystemSolution) erf.getSolution();
			FaultPolyMgr fltPolyMgr = fss.getRupSet().getInversionTargetMFDs().getGridSeisUtils().getPolyMgr();
			
			for(double minMag: minMagArray) {
				
				// compute participation rates for supra-seis rups mapped onto grid nodes inside polygons
				System.out.println("fss.calcParticRateForAllSects...");
				double[] sectPartRates = fss.calcParticRateForAllSects(minMag, maxMag);
				GriddedGeoDataSet supraSeisPartRates_xyzData = new GriddedGeoDataSet(GMT_CA_Maps.defaultGridRegion, true);	// true makes X latitude
				for(int s=0; s<sectPartRates.length;s++) {
					Map<Integer, Double> nodesForSectMap = fltPolyMgr.getNodeFractions(s);
					Set<Integer> nodeIndicesList = nodesForSectMap.keySet();
					for(int index:nodeIndicesList) {
						double oldRate = supraSeisPartRates_xyzData.get(index);
						supraSeisPartRates_xyzData.set(index, oldRate+nodesForSectMap.get(index)*sectPartRates[s]);
					}
				}
				
				// convert minMag to string for filename
				Double tempDouble = new Double(minMag);
				String magString = tempDouble.toString();
				String dirName = fileName+"_Part_"+magString.replace(".", "pt");
				
				System.out.println(dirName);
				
				System.out.println("ERF_Calculator.getParticipationRatesInRegion...");
				GriddedGeoDataSet geoDataSetForGridSeis = ERF_Calculator.getParticipationRatesInRegion(erf, GMT_CA_Maps.defaultGridRegion, minMag, maxMag);
				GMT_MapGenerator gmt_MapGenerator = GMT_CA_Maps.getDefaultGMT_MapGenerator();
				
				GeoDataSet sumGeoDataSet = GeoDataSetMath.add(supraSeisPartRates_xyzData, geoDataSetForGridSeis);
				
				System.out.println("Making GMT Map...");
				//override default scale
				gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -6d);
				gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, -2d);
				gmt_MapGenerator.setParameter(GMT_MapGenerator.TOPO_RESOLUTION_PARAM_NAME, GMT_MapGenerator.TOPO_RESOLUTION_30_GLOBAL);
				gmt_MapGenerator.setParameter(GMT_MapGenerator.GMT_SMOOTHING_PARAM_NAME, true);
				gmt_MapGenerator.setParameter(GMT_MapGenerator.DPI_PARAM_NAME, 300);
				gmt_MapGenerator.setParameter(GMT_MapGenerator.BLACK_BACKGROUND_PARAM_NAME, false);
				gmt_MapGenerator.setParameter(GMT_MapGenerator.KML_PARAM_NAME, true);

				// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
				// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
				// but it won't succeed for the isAllowed(value) call.
				CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
				cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());

				GMT_CA_Maps.makeMap(sumGeoDataSet, "M>="+minMag+" "+scaleLabel, metadata, dirName, gmt_MapGenerator);

			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	
	/**
	 * This makes a nucleation rate map for the UCERF3.3 time-independent model, where section rates are properly 
	 * mapped onto polygon grid nodes.  The mean fault-system solution for each fault model is used (not a true 
	 * branch average).  Background seismicity is treated as point sources.
	 * 
	 * @param magThresh
	 * @param includeAftershocks
	 * @param magAreaAleatory - should be 0.0 or 0.12 (although other values are accepted)
	 */
	public static void makeNucleationRateMapForU3pt3(double magThresh, boolean includeAftershocks, double magAreaAleatory) {

		try {
			
			double maxMag=10d;
			String fileName = "MEAN_BRANCH_AVG_SOL";
			String scaleLabel = "Nucleation Rate";
			String metadata ="magThresh="+magThresh+"\nincludeAftershocks="+includeAftershocks+"\nmagAreaAleatory="+magAreaAleatory+"\n";
			// convert minMag to string for filename
			Double tempDouble = new Double(magThresh);
			String magString = tempDouble.toString();
			String dirName = fileName+"_Nucl_"+magString.replace(".", "pt");
			System.out.println(dirName);


			// average solution for FM 3.1
			String f1 ="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
			File file1 = new File(f1);
			System.out.println("Instantiating ERF1...");
			FaultSystemSolutionERF erf1 = new FaultSystemSolutionERF(FaultSystemIO.loadSol(file1));
			erf1.getParameter(AleatoryMagAreaStdDevParam.NAME).setValue(magAreaAleatory);
			erf1.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(!includeAftershocks);
			erf1.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
			erf1.getParameter(BackgroundRupParam.NAME).setValue(BackgroundRupType.POINT);	
			erf1.updateForecast();
			System.out.println(erf1.getAdjustableParameterList().toString());
			
			// get the fault system solution and the polygon manager
			InversionFaultSystemSolution fss1 = (InversionFaultSystemSolution) erf1.getSolution();
			FaultPolyMgr fltPolyMgr1 = fss1.getRupSet().getInversionTargetMFDs().getGridSeisUtils().getPolyMgr();
			
			// compute nucleation rates for supra-seis rups mapped onto grid nodes inside polygons
			System.out.println("calculation section nucleation rates...");
//			double[] sectNuclRates1 = fss.calcNucleationRateForAllSects(magThresh, maxMag);
			double[] sectNuclRates1 = calcNucleationRateAboveMagForAllSects(erf1, magThresh);	// this was tested as the same as above if magAreaAleatory = 0.0
			GriddedGeoDataSet supraSeisNuclRates_xyzData1 = new GriddedGeoDataSet(GMT_CA_Maps.defaultGridRegion, true);	// true makes X latitude
			for(int s=0; s<sectNuclRates1.length;s++) {
				Map<Integer, Double> nodesForSectMap = fltPolyMgr1.getNodeFractions(s);
				Set<Integer> nodeIndicesList = nodesForSectMap.keySet();
				for(int index:nodeIndicesList) {
					double oldRate = supraSeisNuclRates_xyzData1.get(index);
					supraSeisNuclRates_xyzData1.set(index, oldRate+nodesForSectMap.get(index)*sectNuclRates1[s]);
				}
			}

			System.out.println("ERF_Calculator.getNucleationRatesInRegion...");
			erf1.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);	// don't include fault based sources here; they will be computed separately
			erf1.updateForecast();
			GriddedGeoDataSet geoDataSetForGridSeis1 = ERF_Calculator.getNucleationRatesInRegion(erf1, GMT_CA_Maps.defaultGridRegion, magThresh, maxMag);


			// average solution for FM 3.1
			String f2 ="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_MEAN_BRANCH_AVG_SOL.zip";
			File file2 = new File(f2);
			System.out.println("Instantiating ERF1...");
			FaultSystemSolutionERF erf2 = new FaultSystemSolutionERF(FaultSystemIO.loadSol(file2));
			erf2.getParameter(AleatoryMagAreaStdDevParam.NAME).setValue(magAreaAleatory);
			erf2.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(!includeAftershocks);
			erf2.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
			erf2.getParameter(BackgroundRupParam.NAME).setValue(BackgroundRupType.POINT);	
			erf2.updateForecast();
			System.out.println(erf2.getAdjustableParameterList().toString());
			
			// get the fault system solution and the polygon manager
			InversionFaultSystemSolution fss2 = (InversionFaultSystemSolution) erf2.getSolution();
			FaultPolyMgr fltPolyMgr2 = fss2.getRupSet().getInversionTargetMFDs().getGridSeisUtils().getPolyMgr();
			
			// compute nucleation rates for supra-seis rups mapped onto grid nodes inside polygons
			System.out.println("calculation section nucleation rates 2...");
//			double[] sectNuclRates2 = fss.calcNucleationRateForAllSects(magThresh, maxMag);
			double[] sectNuclRates2 = calcNucleationRateAboveMagForAllSects(erf2, magThresh);	// this was tested as the same as above if magAreaAleatory = 0.0
			GriddedGeoDataSet supraSeisNuclRates_xyzData2 = new GriddedGeoDataSet(GMT_CA_Maps.defaultGridRegion, true);	// true makes X latitude
			for(int s=0; s<sectNuclRates2.length;s++) {
				Map<Integer, Double> nodesForSectMap = fltPolyMgr2.getNodeFractions(s);
				Set<Integer> nodeIndicesList = nodesForSectMap.keySet();
				for(int index:nodeIndicesList) {
					double oldRate = supraSeisNuclRates_xyzData2.get(index);
					supraSeisNuclRates_xyzData2.set(index, oldRate+nodesForSectMap.get(index)*sectNuclRates2[s]);
				}
			}

			System.out.println("ERF_Calculator.getNucleationRatesInRegion 2...");
			erf2.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);	// don't include fault based sources here; they will be computed separately
			erf2.updateForecast();
			GriddedGeoDataSet geoDataSetForGridSeis2 = ERF_Calculator.getNucleationRatesInRegion(erf2, GMT_CA_Maps.defaultGridRegion, magThresh, maxMag);


			GriddedGeoDataSet sumGeoDataSet = new GriddedGeoDataSet(GMT_CA_Maps.defaultGridRegion, true);	// true makes X latitude
			
			// average over the two fault models
			double totalRate=0;
			for(int i=0;i<sumGeoDataSet.size();i++) {
				double ave = 0.5*(geoDataSetForGridSeis1.get(i)+geoDataSetForGridSeis2.get(i)+supraSeisNuclRates_xyzData1.get(i)+supraSeisNuclRates_xyzData2.get(i));
				totalRate += ave;
				sumGeoDataSet.set(i, ave);
			}
			
			System.out.println("Total Rate : "+totalRate);
			metadata += "Total Rate : "+totalRate;

			GMT_MapGenerator gmt_MapGenerator = GMT_CA_Maps.getDefaultGMT_MapGenerator();

			System.out.println("Making GMT Map...");
			//override default scale
			gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -6d);
			gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, -2d);
//			gmt_MapGenerator.setParameter(GMT_MapGenerator.TOPO_RESOLUTION_PARAM_NAME, GMT_MapGenerator.TOPO_RESOLUTION_30_GLOBAL);
//			gmt_MapGenerator.setParameter(GMT_MapGenerator.GMT_SMOOTHING_PARAM_NAME, true);
			gmt_MapGenerator.setParameter(GMT_MapGenerator.DPI_PARAM_NAME, 300);
			gmt_MapGenerator.setParameter(GMT_MapGenerator.BLACK_BACKGROUND_PARAM_NAME, false);
			gmt_MapGenerator.setParameter(GMT_MapGenerator.KML_PARAM_NAME, true);

			// must set this parameter this way because the setValue(CPT) method takes a CPT object, and it must be the
			// exact same object as in the constraint (same instance); the setValue(String) method was added for convenience
			// but it won't succeed for the isAllowed(value) call.
			CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
			cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());

			GMT_CA_Maps.makeMap(sumGeoDataSet, "M>="+magThresh+" "+scaleLabel, metadata, dirName, gmt_MapGenerator);

			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	/**
	 * This computes fault section nuclation rates above the given magnitude threshold, accounting
	 * for any applied aleatory mag-area uncertainty (which is how this differs from 
	 * InversionFaultSystemSolution.calcNucleationRateForAllSects(*)), and assuming a uniform distribution
	 * of nucleations over the rupture surface.
	 * @param erf
	 * @param magThresh
	 * @return
	 */
	public static double[] calcNucleationRateAboveMagForAllSects(FaultSystemSolutionERF erf, double magThresh) {
		InversionFaultSystemRupSet rupSet = ((InversionFaultSystemSolution)erf.getSolution()).getRupSet();
		double[] sectNuclRates = new double[rupSet.getNumSections()];
		
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			double srcRate = ERF_Calculator.getTotalRateAboveMagForSource(erf.getSource(s), erf.getTimeSpan().getDuration(),  magThresh);
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			double rupArea = rupSet.getAreaForRup(fssRupIndex);
			for(int sectIndex : rupSet.getSectionsIndicesForRup(fssRupIndex)) {
				double sectArea = rupSet.getAreaForSection(sectIndex);
				sectNuclRates[sectIndex] += srcRate*sectArea/rupArea;
			}
		}
		return sectNuclRates;
	}

	
	/**
	 * Same as calcNucleationRateAboveMagForAllSects but for participation rates
	 * @param erf
	 * @param min, max, and num (MFD discretization values)
	 * @return
	 */
	public static SummedMagFreqDist[] calcParticipationMFDForAllSects(FaultSystemSolutionERF erf, double min,double max,int num) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		
		SummedMagFreqDist[] mfdArray = new SummedMagFreqDist[rupSet.getNumSections()];
		for(int i=0;i<mfdArray.length;i++) {
			mfdArray[i] = new SummedMagFreqDist(min,max,num);
		}
		double duration = erf.getTimeSpan().getDuration();
		
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			SummedMagFreqDist srcMFD = ERF_Calculator.getTotalMFD_ForSource(erf.getSource(s), duration, min, max, num, true);
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			for(int sectIndex : rupSet.getSectionsIndicesForRup(fssRupIndex)) {
				for(int i=0;i<num;i++)
					mfdArray[sectIndex].add(i,srcMFD.getY(i));
			}
		}
		return mfdArray;
	}
	
	/**
	 * Calculates participation rate above the given magnitude for all sections
	 * @param erf
	 * @param minMag
	 * @return
	 */
	public static double[] calcParticipationRateForAllSects(FaultSystemSolutionERF erf, double minMag) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		double[] rates = new double[rupSet.getNumSections()];
		double duration = erf.getTimeSpan().getDuration();

		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			for (ProbEqkRupture rup : erf.getSource(s)) {
				if (rup.getMag() >= minMag) {
					double rate = rup.getMeanAnnualRate(duration);
					for(int sectIndex : rupSet.getSectionsIndicesForRup(fssRupIndex))
						rates[sectIndex] += rate;
				}
			}
		}
		return rates;
	}
	
	/**
	 * Calculates participation rate above the given magnitude for the given parent section
	 * @param erf
	 * @param parentID
	 * @param minMag
	 * @return
	 */
	public static double calcParticipationRateForParentSect(FaultSystemSolutionERF erf, int parentID, double minMag) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		double duration = erf.getTimeSpan().getDuration();
		
		HashSet<Integer> rupIndexes = new HashSet<Integer>(rupSet.getRupturesForParentSection(parentID));
		
		double rate = 0;

		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			if (!rupIndexes.contains(fssRupIndex))
				continue;
			for (ProbEqkRupture rup : erf.getSource(s)) {
				if (rup.getMag() >= minMag)
					rate += rup.getMeanAnnualRate(duration);
			}
		}
		return rate;
	}
	
	/**
	 * Calculates participation rate above the given magnitude for the given parent sections
	 * @param erf
	 * @param parentIDs
	 * @param minMag
	 * @return
	 */
	public static double calcParticipationRateForParentSects(
			FaultSystemSolutionERF erf, double minMag, int... parentIDs) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		double duration = erf.getTimeSpan().getDuration();
		
		HashSet<Integer> rupIndexes = new HashSet<Integer>();
		for (Integer parentID : parentIDs)
			rupIndexes.addAll(rupSet.getRupturesForParentSection(parentID));
		
		double rate = 0;

		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			if (!rupIndexes.contains(fssRupIndex))
				continue;
			for (ProbEqkRupture rup : erf.getSource(s)) {
				if (rup.getMag() >= minMag)
					rate += rup.getMeanAnnualRate(duration);
			}
		}
		return rate;
	}
	
	/**
	 * Calculates participation probability above the given magnitude for the given parent sections
	 * @param erf
	 * @param parentIDs
	 * @param minMag
	 * @return
	 */
	public static double calcParticipationProbForParentSects(
			FaultSystemSolutionERF erf, double minMag, int... parentIDs) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		
		HashSet<Integer> rupIndexes = new HashSet<Integer>();
		for (Integer parentID : parentIDs)
			rupIndexes.addAll(rupSet.getRupturesForParentSection(parentID));
		
		List<Double> probs = Lists.newArrayList();

		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			if (!rupIndexes.contains(fssRupIndex))
				continue;
			for (ProbEqkRupture rup : erf.getSource(s)) {
				if (rup.getMag() >= minMag)
					probs.add(rup.getProbability());
			}
		}
		return calcSummedProbs(probs);
	}
	
	/**
	 * Calculates nucleation rate above the given magnitude for the given parent section
	 * @param erf
	 * @param parentID
	 * @param minMag
	 * @return
	 */
	public static double calcNucleationRateForParentSect(FaultSystemSolutionERF erf, int parentID, double minMag) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		double duration = erf.getTimeSpan().getDuration();
		
		HashSet<Integer> rupIndexes = new HashSet<Integer>(rupSet.getRupturesForParentSection(parentID));
		
		double rate = 0;

		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			if (!rupIndexes.contains(fssRupIndex))
				continue;
			
			double rupArea = rupSet.getAreaForRup(fssRupIndex);
			double rupAreaOnParent = 0d;
			for(FaultSectionPrefData sect : rupSet.getFaultSectionDataForRupture(fssRupIndex)) {
				if (sect.getParentSectionId() == parentID)
					rupAreaOnParent += rupSet.getAreaForSection(sect.getSectionId());
			}
			Preconditions.checkState(rupAreaOnParent > 0);
			Preconditions.checkState(rupAreaOnParent <= rupArea);
			
			double nuclFract = rupAreaOnParent/rupArea;
			
			for (ProbEqkRupture rup : erf.getSource(s)) {
				if (rup.getMag() >= minMag)
					rate += nuclFract*rup.getMeanAnnualRate(duration);
			}
		}
		return rate;
	}
	
	
	/**
	 * The gives the effective participation rates for events greater than or equal to minMag
	 * and less than maxMag for each point in the supplied GriddedRegion, where fault-based ruptures
	 * are mapped onto the associated grid nodes.
	 * 
	 * NOTE - the mapping probably isn't exactly correct (not clear the best way), but it should be fine because
	 * fault-polygon areas will be hidden behind finite faults in the intended use of this method; other uses
	 * should consider this more carefully.
	 * @param erf - it's assumed that erf.updateForecast() has already been called
	 * @param griddedRegion
	 * @param minMag
	 * @param maxMag
	 * @return GriddedGeoDataSet - X-axis is set as Latitude, and Y-axis is Longitude
	 */
	public static GriddedGeoDataSet calcParticipationProbInGriddedRegionFltMapped(FaultSystemSolutionERF erf, GriddedRegion griddedRegion,
			double minMag, double maxMag) {
		
		IncludeBackgroundOption origOption = (IncludeBackgroundOption)erf.getParameter(IncludeBackgroundParam.NAME).getValue();
		
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		FaultPolyMgr polyManager = FaultPolyMgr.create(rupSet.getFaultSectionDataList(), InversionTargetMFDs.FAULT_BUFFER);	// this works for U3, but not generalized
		double duration = erf.getTimeSpan().getDuration();
		
		// Get Gridded Seis Rates
		erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.ONLY);
		erf.updateForecast();
		// Note that this treats ruptures as point sources if that's how specified in the ERF
		GriddedGeoDataSet combinedData = ERF_Calculator.getParticipationRatesInRegion(erf, griddedRegion, minMag, maxMag);
		
		
		erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
		erf.updateForecast();
		
		for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
			int invIndex = erf.getFltSysRupIndexForSource(sourceID);
			for (ProbEqkRupture rup : erf.getSource(sourceID)) {
				double mag = rup.getMag();
				if (mag < minMag || mag > maxMag)
					continue;
				double rupRate = rup.getMeanAnnualRate(duration);
				for (int s : rupSet.getSectionsIndicesForRup(invIndex)) {
					Map<Integer, Double> nodeFracts = polyManager.getNodeFractions(s);
					for (int n : nodeFracts.keySet()) {
						double prevRate = combinedData.get(n);
						combinedData.set(n, prevRate+rupRate*nodeFracts.get(n));
					}
				}
			}
		}
		
		// Convert to prob of one or more
		for (int n=0; n<combinedData.size(); n++) {
			double nodeRate = combinedData.get(n);
			combinedData.set(n, 1.0-Math.exp(-nodeRate*duration));
		}
		
		erf.setParameter(IncludeBackgroundParam.NAME, origOption);
		erf.updateForecast();

		return combinedData;

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
	public static SummedMagFreqDist[] calcNucleationMFDForAllSects(FaultSystemSolutionERF erf, double min,double max,int num) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		
		SummedMagFreqDist[] mfdArray = new SummedMagFreqDist[rupSet.getNumSections()];
		for(int i=0;i<mfdArray.length;i++) {
			mfdArray[i] = new SummedMagFreqDist(min,max,num);
		}
		double duration = erf.getTimeSpan().getDuration();
		
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			SummedMagFreqDist srcMFD = ERF_Calculator.getTotalMFD_ForSource(erf.getSource(s), duration, min, max, num, true);
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			double rupArea = rupSet.getAreaForRup(fssRupIndex);
			for(int sectIndex : rupSet.getSectionsIndicesForRup(fssRupIndex)) {
				double sectArea = rupSet.getAreaForSection(sectIndex);
				for(int i=0;i<num;i++) {
					mfdArray[sectIndex].add(i,srcMFD.getY(i)*sectArea/rupArea);
				}
			}
		}
		return mfdArray;
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
	public static double[] tempCalcParticipationRateForAllSects(FaultSystemSolutionERF erf) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		
		double[] rateArraySmall = new double[rupSet.getNumSections()];
		double[] rateArray = new double[rupSet.getNumSections()];
		double[] maxMagArray = new double[rupSet.getNumSections()];
		double[] minMagArray = new double[rupSet.getNumSections()];
		int[] maxNumSectInRupForSect = new int[rupSet.getNumSections()];
		double duration = erf.getTimeSpan().getDuration();
		
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			double mag = erf.getSource(s).getRupture(0).getMag();
			List<Integer> setIndexList = rupSet.getSectionsIndicesForRup(fssRupIndex);
//			if(setIndexList.size()>2)
//				continue;
			double rate = erf.getSource(s).computeTotalEquivMeanAnnualRate(duration);
			for(int sectIndex : setIndexList) {
				rateArray[sectIndex] += rate;
				if(setIndexList.size()<=3)
					rateArraySmall[sectIndex] += rate;
				if(setIndexList.size() > maxNumSectInRupForSect[sectIndex])
					maxNumSectInRupForSect[sectIndex] = setIndexList.size();
				if(maxMagArray[sectIndex]<mag)
					maxMagArray[sectIndex]=mag;
				if(minMagArray[sectIndex]>mag)
					minMagArray[sectIndex]=mag;
			}
		}
		
		for(int s=0; s<rateArray.length;s++) {
			rateArray[s] = rateArraySmall[s]/(rateArray[s]/(maxMagArray[s]-minMagArray[s]));
//			if(maxNumSectInRupForSect[s]>4)
//				rateArray[s] = rateArraySmall[s]/rateArray[s];
//			else
//				rateArray[s] = Double.NaN;
		}

		return rateArray;
	}


	
	/**
	 * This computes fault section moment rates assuming a uniform distribution
	 * of nucleations over the rupture surface.
	 * @param erf
	 * @return
	 */
	public static double[] calcMomentRateForAllFaultSections(FaultSystemSolutionERF erf) {
		InversionFaultSystemRupSet rupSet = ((InversionFaultSystemSolution)erf.getSolution()).getRupSet();
		double[] sectMoRates = new double[rupSet.getNumSections()];
		
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			double srcMoRate = ERF_Calculator.getTotalMomentRateForSource(erf.getSource(s), erf.getTimeSpan().getDuration());
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			double rupArea = rupSet.getAreaForRup(fssRupIndex);
			for(int sectIndex : rupSet.getSectionsIndicesForRup(fssRupIndex)) {
				double sectArea = rupSet.getAreaForSection(sectIndex);
				sectMoRates[sectIndex] += srcMoRate*sectArea/rupArea;
			}
		}
		return sectMoRates;
	}

	
	/**
	 * This makes the iconic figure of U3 Time Dependence, gridded participation probability maps, where section probabilities
	 * are properly mapped onto polygon grid nodes, and topography is included. Aftershocks are included.
	 * Results are in OpenSHA/dev/scratch/UCERF3/data/scratch/GMT/time_dep_fig_1
	 */
	public static void makeIconicFigureForU3TimeDependence() throws Exception {
		File gmtDir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "GMT");
		if (!gmtDir.exists())
			gmtDir.mkdir();
		File outputDir = new File(gmtDir, "time_dep_fig_1");
		if (!outputDir.exists())
			outputDir.mkdir();
		
		File xmlInputFile = new File(outputDir, "gridded_participation_prob_plots.xml");
		if (!xmlInputFile.exists())
			// download XML file from web
			FileUtils.downloadURL("http://opensha.usc.edu/ftp/kmilner/ucerf3/2013_05_10-ucerf3p3-production-10runs/"
					+ "gridded_participation_prob_plots.xml", xmlInputFile);
		
		double minMag = 6.7;	// 5, 6.7, 7.7, 8 supported
		double duration = 30;	// only 5 or 30 supported
		
		// display figure along with saving it?
		boolean display = false;
		
		makeIconicFigureForU3TimeDependence(xmlInputFile, outputDir, minMag, duration, display);
	}
	
	/**
	 * This makes the iconic figure of U3 Time Dependence, gridded participation probability maps, where section probabilities
	 * are properly mapped onto polygon grid nodes, and topography is included. Aftershocks are included.
	 * @param xmlInputFile
	 * @param outputDir
	 * @param minMag
	 * @param duration
	 * @param display
	 * @throws Exception
	 */
	public static void makeIconicFigureForU3TimeDependence(File xmlInputFile, File outputDir,
			double minMag, double duration, boolean display) throws Exception {
		Preconditions.checkState(xmlInputFile.exists(), "XML Input file doesn't exist: "+xmlInputFile.getAbsolutePath());
		
		List<MapPlotData> mapDatas = MapBasedPlot.loadPlotData(xmlInputFile);
		String plotFileName = (int)duration+"_timedep_gridded_partic_prob_"+(float)minMag+"+";
		MapPlotData mapData = null;
		List<String> availableDatasets = Lists.newArrayList();
		for (MapPlotData data : mapDatas) {
			if (!data.getFileName().contains("timedep_gridded_partic_prob"))
				continue;
			availableDatasets.add(data.getFileName());
			if (data.getFileName().equals(plotFileName)) {
				mapData = data;
				break;
			}
		}
		if (mapData == null)
			throw new IllegalArgumentException("Selected dataset '"+plotFileName
					+"' not found in xml input file. Available files:\n"+Joiner.on("\n\t").join(availableDatasets));
		
//		CPT cpt = mapData.getCPT();
		CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
		
		double minValue = -5;
		double maxValue = 0;

		// this is required for any 0 spots
		cpt.setBelowMinColor(cpt.getMinColor());
		GeoDataSet data = mapData.getGriddedData();
		// convert any NaN holes in the middle to the min color
		for (int index=0; index<data.size(); index++)
			if (!Doubles.isFinite(data.get(index)))
				data.set(index, minValue);
		
		GMT_Map map = new GMT_Map(mapData.getRegion(), data, mapData.getSpacing(), cpt);
		map.setCustomScaleMax(null);
		map.setCustomScaleMax(null);
		map.setTopoResolution(TopographicSlopeFile.SRTM_30_PLUS);
		map.setUseGMTSmoothing(true);
		map.setDpi(300);
		map.setBlackBackground(false);
		map.setGenerateKML(true);
		map.setCustomLabel(mapData.getLabel());
		map.setRescaleCPT(true);
		map.setCustomScaleMin(minValue);
		map.setCustomScaleMax(maxValue);
		
		GMT_MapGenerator gmt = new GMT_MapGenerator();
		gmt.getAdjustableParamsList().getParameter(Boolean.class, GMT_MapGenerator.LOG_PLOT_NAME).setValue(false);
		gmt.getAdjustableParamsList().getParameter(Boolean.class, GMT_MapGenerator.GMT_SMOOTHING_PARAM_NAME).setValue(false);
		
		String url = gmt.makeMapUsingServlet(map, "metadata", null);
		System.out.println(url);
		String metadata = GMT_MapGuiBean.getClickHereHTML(gmt.getGMTFilesWebAddress());
		String baseURL = url.substring(0, url.lastIndexOf('/')+1);
		
		File pngFile = new File(outputDir, plotFileName+".png");
		FileUtils.downloadURL(baseURL+"map.png", pngFile);
		
		File pdfFile = new File(outputDir, plotFileName+".pdf");
		FileUtils.downloadURL(baseURL+"map.pdf", pdfFile);
		
		// download KMZ file
		File kmzFile = new File(outputDir, plotFileName+".kmz");
		FileUtils.downloadURL(baseURL+"map.kmz", kmzFile);
		
		// now extract projected file
		ZipFile zip = new ZipFile(kmzFile);
		ZipEntry entry = zip.getEntry("map.png");
		FileOutputStream fout = new FileOutputStream(new File(outputDir, plotFileName+"_scec_vdo.png"));
		FileUtils.copyInputStream(new BufferedInputStream(zip.getInputStream(entry)), new BufferedOutputStream(fout));
		
		if (display) {
			new ImageViewerWindow(url,metadata, true);
		}
	}


	
	
	
	public static void makeBackgroundImageForSCEC_VDO(GeoDataSet data, GriddedRegion griddedRegion, File outputDir,
			String plotFileName, boolean display, CPT cpt, double minValue, double maxValue, boolean includeTopo) throws Exception {
		
		if(!outputDir.exists())
			outputDir.mkdir();

		// this is required for any 0 spots
		cpt.setBelowMinColor(cpt.getMinColor());
		// convert any NaN holes in the middle to the min color
		for (int index=0; index<data.size(); index++)
			if (!Doubles.isFinite(data.get(index)))
				data.set(index, minValue);
		
		GMT_Map map = new GMT_Map(griddedRegion, data, griddedRegion.getSpacing(), cpt);
		map.setCustomScaleMax(null);
		map.setCustomScaleMax(null);
		if(includeTopo) {
			map.setTopoResolution(TopographicSlopeFile.SRTM_30_PLUS);
			plotFileName += "_withTopo";
		}
		map.setUseGMTSmoothing(true);
		map.setDpi(300);
		map.setBlackBackground(false);
		map.setGenerateKML(true);
		map.setCustomLabel("Label");
		map.setRescaleCPT(true);
		map.setCustomScaleMin(minValue);
		map.setCustomScaleMax(maxValue);
		
		GMT_MapGenerator gmt = new GMT_MapGenerator();
		gmt.getAdjustableParamsList().getParameter(Boolean.class, GMT_MapGenerator.LOG_PLOT_NAME).setValue(false);
		gmt.getAdjustableParamsList().getParameter(Boolean.class, GMT_MapGenerator.GMT_SMOOTHING_PARAM_NAME).setValue(false);
		
		String url = gmt.makeMapUsingServlet(map, "metadata", null);
		System.out.println(url);
		String metadata = GMT_MapGuiBean.getClickHereHTML(gmt.getGMTFilesWebAddress());
		String baseURL = url.substring(0, url.lastIndexOf('/')+1);
		
		File pngFile = new File(outputDir, plotFileName+".png");
		FileUtils.downloadURL(baseURL+"map.png", pngFile);
		
		File pdfFile = new File(outputDir, plotFileName+".pdf");
		FileUtils.downloadURL(baseURL+"map.pdf", pdfFile);
		
		// download KMZ file
		File kmzFile = new File(outputDir, plotFileName+".kmz");
		FileUtils.downloadURL(baseURL+"map.kmz", kmzFile);
		
		// download txt file
		File txtFile = new File(outputDir, plotFileName+".txt");
		FileUtils.downloadURL(baseURL+"map_data.txt", txtFile);
		
		// now extract projected file
		ZipFile zip = new ZipFile(kmzFile);
		ZipEntry entry = zip.getEntry("map.png");
		FileOutputStream fout = new FileOutputStream(new File(outputDir, plotFileName+"_scec_vdo.png"));
		FileUtils.copyInputStream(new BufferedInputStream(zip.getInputStream(entry)), new BufferedOutputStream(fout));
		
		if (display) {
			new ImageViewerWindow(url,metadata, true);
		}
	}

	
	/**
	 *
	 */
	public static void makeUCERF2_PartRateMaps() {

		try {
			
			MeanUCERF2 erf= new MeanUCERF2();
			erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
			erf.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
			erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
			erf.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
			erf.getTimeSpan().setDuration(1d);
			erf.updateForecast();
			String fileName = "UCERF2";

			
			
			GMT_CA_Maps.plotParticipationRateMap(erf, 5.0, 10d, fileName+"_Part5pt0", "test", fileName+"_Part5pt0");
			GMT_CA_Maps.plotParticipationRateMap(erf, 6.7, 10d, fileName+"_Part6pt7", "test", fileName+"_Part6pt7");
			GMT_CA_Maps.plotParticipationRateMap(erf, 7.7, 10d, fileName+"_Part7pt7", "test", fileName+"_Part7pt7");
			GMT_CA_Maps.plotParticipationRateMap(erf, 8.0, 10d, fileName+"_Part8pt0", "test", fileName+"_Part8pt0");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	
	
	/**
	 * The result here is pretty close, but not exact.  Looking back at the UCERF2 code 
	 * it looks like binning might have been off by a half delta (e.g., the excel probability file
	 * has off-fault values for M 7 and above), but I'm not sure the explains the diff.  I think the
	 * new way of computing things here is cleaner, and therefore likely better.  The differences are
	 * not noticable unless you do a close overlay, so I'm not sure this is worth pursuing further.
	 */
	public static void testUCERF2_Figure25() {
		
		UCERF2_TimeDependentEpistemicList erf_list = new UCERF2_TimeDependentEpistemicList();

		erf_list.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
		erf_list.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
		erf_list.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
		erf_list.getTimeSpan().setDuration(1d);
		erf_list.updateForecast();
		String fileName = "UCERF2";
		XY_DataSetList wtedFuncList = new XY_DataSetList();
		ArrayList<Double> relativeWts = new ArrayList<Double>();
		
		for(int e=0;e<erf_list.getNumERFs();e++) {
//		for(int e=0;e<10;e++) {
			System.out.println(e+" of "+erf_list.getNumERFs());
			SummedMagFreqDist mfdPart = ERF_Calculator.getParticipationMagFreqDistInRegion(erf_list.getERF(e), new CaliforniaRegions.SF_BOX(), 5.05, 40, 0.1, true);
			EvenlyDiscretizedFunc testMFD_Part = mfdPart.getCumRateDistWithOffset();
			for(int i=0;i<testMFD_Part.size();i++) {
				double prob_part = 1.0 - Math.exp(-testMFD_Part.getY(i)*30);
				testMFD_Part.set(i,prob_part);
			}
			wtedFuncList.add(testMFD_Part);
			relativeWts.add(erf_list.getERF_RelativeWeight(e));
		}
		FractileCurveCalculator fractileCalc = new FractileCurveCalculator(wtedFuncList,relativeWts);
		
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(fractileCalc.getMeanCurve());
		funcs.add(fractileCalc.getFractile(0.025));
		funcs.add(fractileCalc.getFractile(0.5));
		funcs.add(fractileCalc.getFractile(0.975));

		GraphWindow graph = new GraphWindow(funcs, "Test of UCERF2 Figure 25");
		graph.setX_AxisRange(5, 8.5);
		graph.setY_AxisRange(0, 1);
	}
	
	
	public static void plotMFD_InRegion(Region region, String fileName) {
		
		// First all UCERF2 branches:
		UCERF2_TimeIndependentEpistemicList erf_list = new UCERF2_TimeIndependentEpistemicList();
		erf_list.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
		erf_list.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
		erf_list.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
		erf_list.getTimeSpan().setDuration(1d);
		erf_list.updateForecast();
				
		XY_DataSetList wtedFuncList = new XY_DataSetList();
		ArrayList<Double> relativeWts = new ArrayList<Double>();
		
		for(int e=0;e<erf_list.getNumERFs();e++) {
			System.out.println(e+" of "+erf_list.getNumERFs());
			SummedMagFreqDist mfdPart = ERF_Calculator.getParticipationMagFreqDistInRegion(erf_list.getERF(e), region, 5.05, 40, 0.1, true);
			wtedFuncList.add(mfdPart.getCumRateDistWithOffset());
			relativeWts.add(erf_list.getERF_RelativeWeight(e));
		}
		FractileCurveCalculator fractileCalc = new FractileCurveCalculator(wtedFuncList,relativeWts);
		
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(fractileCalc.getMeanCurve());
//		funcs.add(fractileCalc.getFractile(0.025));
//		funcs.add(fractileCalc.getFractile(0.975));	
		funcs.add(fractileCalc.getMinimumCurve());
		funcs.add(fractileCalc.getMaximumCurve());
		funcs.add(fractileCalc.getFractile(0.5));

		// Now mean UCERF2
		MeanUCERF2 erf= new MeanUCERF2();
		erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		erf.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
		erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
		erf.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
		erf.getTimeSpan().setDuration(1d);
		erf.updateForecast();
		SummedMagFreqDist meanPart = ERF_Calculator.getParticipationMagFreqDistInRegion(erf, region, 5.05, 40, 0.1, true);
		meanPart.setName("MFD for MeanUCERF2");
		meanPart.setInfo(" ");
		funcs.add(meanPart.getCumRateDistWithOffset());	
		
		
		File file = new File("/Users/field/Downloads/FaultSystemSolutions/FM3_1_NEOK_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_mean_sol.zip");
		FaultSystemSolutionERF ucerf3_erf;
		try {
			ucerf3_erf = new FaultSystemSolutionERF(FaultSystemIO.loadSol(file));
		} catch (Exception e1) {
			throw ExceptionUtils.asRuntimeException(e1);
		}
		ucerf3_erf.updateForecast();
		SummedMagFreqDist ucerf3_Part = ERF_Calculator.getParticipationMagFreqDistInRegion(ucerf3_erf, region, 5.05, 40, 0.1, true);
		ucerf3_Part.setName("MFD for UCERF3 Char Reference Branch");
		ucerf3_Part.setInfo(" ");
		funcs.add(ucerf3_Part.getCumRateDistWithOffset());	
		
//		File file = new File("/Users/field/Downloads/FaultSystemSolutions/FM3_1_ZENG_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_sol.zip");
//		UCERF3_FaultSysSol_ERF ucerf3_erf_2 = new UCERF3_FaultSysSol_ERF(file);
//		ucerf3_erf_2.updateForecast();
//		SummedMagFreqDist ucerf3_Part_2 = ERF_Calculator.getParticipationMagFreqDistInRegion(ucerf3_erf_2, region, 5.05, 40, 0.1, true);
//		ucerf3_Part_2.setName("MFD for UCERF3 Char Reference Branch w/ Zeng");
//		ucerf3_Part_2.setInfo(" ");
//		funcs.add(ucerf3_Part_2.getCumRateDistWithOffset());	

//		file = new File("/Users/field/Downloads/FaultSystemSolutions/FM3_1_ZENG_EllB_DsrUni_GRConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_sol.zip");
//		UCERF3_FaultSysSol_ERF ucerf3_erf_3 = new UCERF3_FaultSysSol_ERF(file);
//		ucerf3_erf_3.updateForecast();
//		SummedMagFreqDist ucerf3_Part_3 = ERF_Calculator.getParticipationMagFreqDistInRegion(ucerf3_erf_3, region, 5.05, 40, 0.1, true);
//		ucerf3_Part_3.setName("MFD for UCERF3 GR Reference Branch w/ Zeng");
//		ucerf3_Part_3.setInfo(" ");
//		funcs.add(ucerf3_Part_3.getCumRateDistWithOffset());	
		
    	ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, null, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, null, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.GREEN));


		GraphWindow graph = new GraphWindow(funcs, "Cumulative MFDs in "+region.getName(),plotChars); 
		graph.setX_AxisLabel("Magnitude");
		graph.setY_AxisLabel("Rate (per year)");
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(18);
		graph.setTickLabelFontSize(16);
		graph.setX_AxisRange(5, 8.5);
		graph.setY_AxisRange(1e-4, 1);
		graph.setYLog(true);
		
		try {
			graph.saveAsPDF(fileName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	/**
	 * This comparison includes aftershocks
	 */
	public static void plot_U3pt3_U2_TotalMeanMFDs() {
		
//		double aleatoryMagAreaVar = 0.12;
		double aleatoryMagAreaVar = 0.0;
		
		Region relmRegion = RELM_RegionUtils.getGriddedRegionInstance();
		
		MeanUCERF3 erf = new MeanUCERF3();
		erf.setMeanParams(10d, true, 0d, MeanUCERF3.RAKE_BASIS_MEAN);
		erf.getParameter(AleatoryMagAreaStdDevParam.NAME).setValue(aleatoryMagAreaVar);
		erf.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(false);
		erf.getParameter("Treat Background Seismicity As").setValue(BackgroundRupType.POINT);
		
		System.out.println("Working on INCLUDE");
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
		erf.updateForecast();
		SummedMagFreqDist mfd_U3_total = ERF_Calculator.getMagFreqDistInRegionFaster(erf, relmRegion, 5.05, 40, 0.1, true);
		
		System.out.println("Working on EXCLUDE");
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
		erf.updateForecast();
		SummedMagFreqDist mfd_U3_faults = ERF_Calculator.getMagFreqDistInRegionFaster(erf, relmRegion, 5.05, 40, 0.1, true);
		
		System.out.println("Working on ONLY");
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);
		erf.updateForecast();
		SummedMagFreqDist mfd_U3_gridded = ERF_Calculator.getMagFreqDistInRegionFaster(erf, relmRegion, 5.05, 40, 0.1, true);
		
		EvenlyDiscretizedFunc mfd_U3_total_cum = mfd_U3_total.getCumRateDistWithOffset();
		mfd_U3_total_cum.setName("Cumulative MFD for Mean UCERF3 - Total");
		EvenlyDiscretizedFunc mfd_U3_faults_cum = mfd_U3_faults.getCumRateDistWithOffset();
		mfd_U3_faults_cum.setName("Cumulative MFD for Mean UCERF3 - Faults");
		EvenlyDiscretizedFunc mfd_U3_gridded_cum = mfd_U3_gridded.getCumRateDistWithOffset();
		mfd_U3_gridded_cum.setName("Cumulative MFD for Mean UCERF3 - Gridded");




//		// average solution for FM 3.1
//		String f ="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
//		File file = new File(f);
//		UCERF3_FaultSysSol_ERF erf = new UCERF3_FaultSysSol_ERF(file);
//		erf.getParameter(AleatoryMagAreaStdDevParam.NAME).setValue(aleatoryMagAreaVar);
//		erf.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(false);
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
//		erf.getParameter("Treat Background Seismicity As").setValue(BackgroundRupType.POINT);
//		erf.updateForecast();
//		SummedMagFreqDist mfd_U3_total = ERF_Calculator.getMagFreqDistInRegion(erf, relmRegion, 5.05, 40, 0.1, true);
//		
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
//		erf.updateForecast();
//		SummedMagFreqDist mfd_U3_faults = ERF_Calculator.getMagFreqDistInRegion(erf, relmRegion, 5.05, 40, 0.1, true);
//		
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);
//		erf.updateForecast();
//		SummedMagFreqDist mfd_U3_gridded = ERF_Calculator.getMagFreqDistInRegion(erf, relmRegion, 5.05, 40, 0.1, true);
//
//
//		// average solution for FM 3.2
//		String f2 ="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_MEAN_BRANCH_AVG_SOL.zip";
//		File file2 = new File(f2);
//		erf = new UCERF3_FaultSysSol_ERF(file2);
//		erf.getParameter(AleatoryMagAreaStdDevParam.NAME).setValue(aleatoryMagAreaVar);
//		erf.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(false);
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
//		erf.getParameter("Treat Background Seismicity As").setValue(BackgroundRupType.POINT);
//		erf.updateForecast();
//		
//		mfd_U3_total.addResampledMagFreqDist(ERF_Calculator.getMagFreqDistInRegion(erf, relmRegion, 5.05, 40, 0.1, true), true);
//		mfd_U3_total.scale(0.5);
//		EvenlyDiscretizedFunc mfd_U3_total_cum = mfd_U3_total.getCumRateDistWithOffset();
//		mfd_U3_total_cum.setName("Cumulative MFD for Mean UCERF3 - Total");
//		
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
//		erf.updateForecast();
//		mfd_U3_faults.addResampledMagFreqDist(ERF_Calculator.getMagFreqDistInRegion(erf, relmRegion, 5.05, 40, 0.1, true), true);
//		mfd_U3_faults.scale(0.5);
//		EvenlyDiscretizedFunc mfd_U3_faults_cum = mfd_U3_faults.getCumRateDistWithOffset();
//		mfd_U3_faults_cum.setName("Cumulative MFD for Mean UCERF3 - Faults");
//
//		
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);
//		erf.updateForecast();
//		mfd_U3_gridded.addResampledMagFreqDist(ERF_Calculator.getMagFreqDistInRegion(erf, relmRegion, 5.05, 40, 0.1, true), true);
//		mfd_U3_gridded.scale(0.5);
//		EvenlyDiscretizedFunc mfd_U3_gridded_cum = mfd_U3_gridded.getCumRateDistWithOffset();
//		mfd_U3_gridded_cum.setName("Cumulative MFD for Mean UCERF3 - Gridded");


		// Now mean UCERF2
		UCERF2_MFD_ConstraintFetcher U2_fetcher = new UCERF2_MFD_ConstraintFetcher(relmRegion);
		EvenlyDiscretizedFunc mfd_U2_total_cum = U2_fetcher.getTotalMFD().getCumRateDistWithOffset();
		EvenlyDiscretizedFunc mfd_U2_faults_cum = U2_fetcher.getFaultMFD().getCumRateDistWithOffset();
		EvenlyDiscretizedFunc mfd_U2_gridded_cum = U2_fetcher.getBackgroundSeisMFD().getCumRateDistWithOffset();
		
		
//		MeanUCERF2 erf_U2= new MeanUCERF2();
//		ModMeanUCERF2 erf_U2= new ModMeanUCERF2();
//		erf_U2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
//		erf_U2.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
//		erf_U2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
//		erf_U2.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
//		erf_U2.getTimeSpan().setDuration(1d);
//		erf_U2.updateForecast();
//		SummedMagFreqDist mfd_U2 = ERF_Calculator.getMagFreqDistInRegion(erf_U2, relmRegion, 5.05, 40, 0.1, true);
//		EvenlyDiscretizedFunc mfd_U2_cum = mfd_U2.getCumRateDistWithOffset();
//		mfd_U2_cum.setName("Cumulative MFD for Mean UCERF2");
//		mfd_U2_cum.setInfo(mfd_U2.toString());

		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(mfd_U3_total_cum);
		funcs.add(mfd_U2_total_cum);
		funcs.add(mfd_U3_faults_cum);
		funcs.add(mfd_U2_faults_cum);
		funcs.add(mfd_U3_gridded_cum);
		funcs.add(mfd_U2_gridded_cum);

		GraphWindow graph = new GraphWindow(funcs, "U2 and U3 Cumulative MFDs");
		graph.setX_AxisLabel("Magnitude");
		graph.setY_AxisLabel("Cumulative Rate (per year)");
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(18);
		graph.setTickLabelFontSize(16);
		graph.setX_AxisRange(5, 9.0);
		graph.setY_AxisRange(1e-6, 10);
		graph.setYLog(true);
		
		String tableString = new String();
		tableString="U3_total\tU3_faults\tU3_gridded\tU2_total\tU2_faults\tU2_gridded\n";
		for(double mag=5;mag<8.8;mag+=0.1) {
			tableString+=(float)mag+"\t";
			tableString+=mfd_U3_total_cum.getClosestYtoX(mag)+"\t";
			tableString+=mfd_U3_faults_cum.getClosestYtoX(mag)+"\t";
			tableString+=mfd_U3_gridded_cum.getClosestYtoX(mag)+"\t";
			tableString+=mfd_U2_total_cum.getClosestYtoX(mag)+"\t";
			tableString+=mfd_U2_faults_cum.getClosestYtoX(mag)+"\t";
			tableString+=mfd_U2_gridded_cum.getClosestYtoX(mag)+"\n";
		}
		System.out.println(tableString);		
		
		File dataFile = new File("dev/scratch/UCERF3/data/scratch/aveMFDs_ForU3_andU2.txt");
		try {
			FileWriter fw = new FileWriter(dataFile);
			fw.write(tableString);
			fw.close ();
			graph.saveAsPDF("dev/scratch/UCERF3/data/scratch/aveMFDs_ForU3_andU2.pdf");
		}
		catch (IOException e) {
			System.out.println ("IO exception = " + e );
		}

	}
	
//	public static void makeRegionalProb

	/**
	 * This calculates a cumulative magnitude vs probability distribution for the given FSS ERF and region.
	 * Each point in the returned function represents the probability in the forecast (using the forecast duration)
	 * of a rupture at or above the given magnitude with any portion inside the region.
	 * 
	 * @param erf
	 * @param region
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 * @param calcFromMFD if true probabilities will be calculated by first computing participation MFD for the region,
	 * otherwise probabilities will be summed for each source as totProb = 1 - (1 - prob1)*(1 - prob2)*...*(1 - probN)
	 * @param cache optional but recommended - this cache will greatly speed up calculations and can be reused for
	 * different calls to this method with different durations, probability models, or regions.
	 * @return
	 */
	public static EvenlyDiscretizedFunc calcMagProbDist(FaultSystemSolutionERF erf, Region region,
			double minMag, int numMag, double deltaMag, boolean calcFromMFD, FSSRupsInRegionCache cache) {
		Preconditions.checkState(numMag > 0);
		erf.updateForecast();
		double duration = erf.getTimeSpan().getDuration();
		
		if (calcFromMFD) {
			// just use the MFD itself
			// we want the cumulative distribution, so shift minMag up by half a mag bin
			// and then get cumulative dist with offset
			SummedMagFreqDist incrMFD = ERF_Calculator.getParticipationMagFreqDistInRegion(
					erf, region, minMag+0.5*deltaMag, numMag, deltaMag, true, cache);
			EvenlyDiscretizedFunc mfd = incrMFD.getCumRateDistWithOffset();
			Preconditions.checkState(minMag == mfd.getMinX());
			EvenlyDiscretizedFunc result = calcProbsFromSummedMFD(mfd, duration);
			Preconditions.checkState(minMag == result.getMinX());
			return result;
		} else {
			// calc from each source itself
			if (cache == null)
				cache = new FSSRupsInRegionCache();
			
			EvenlyDiscretizedFunc result = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			
			// this tracks the rupture probabilities for each mag bin
			List<List<Double>> probsList = Lists.newArrayList();
			for (int m=0; m<numMag; m++)
				probsList.add(new ArrayList<Double>());
			
			for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
				ProbEqkSource source = erf.getSource(sourceID);
				if (!cache.isRupInRegion(erf, source, source.getRupture(0), sourceID, 0, region))
					// source is just for a single rupture, if the first rup isn't in the region none are
					continue;
				for (ProbEqkRupture rup : source) {
					double prob = rup.getProbability();
					double mag = rup.getMag();
					populateProbList(mag, prob, probsList, result);
				}
			}
			
			// now sum the probabilities as:
			calcSummedProbs(probsList, result);
			return result;
		}
	}
	
	private static void testProbSumMethods() throws IOException, DocumentException {
		FaultSystemSolution meanSol = FaultSystemIO.loadSol(
				new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
						"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		
		double duration = 30d;
		String durStr = (int)duration+"yr";
		
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(meanSol);
		erf.getTimeSpan().setDuration(duration);
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		Region region = new CaliforniaRegions.LA_BOX();
		double minMag = 5d;
		int numMag = 40;
		double deltaMag = 0.1;
		FSSRupsInRegionCache cache = new FSSRupsInRegionCache();
		EvenlyDiscretizedFunc mfdVals = calcMagProbDist(erf, region, minMag, numMag, deltaMag, true, cache);
		EvenlyDiscretizedFunc sumVals = calcMagProbDist(erf, region, minMag, numMag, deltaMag, true, cache);
		
		for (int i=0; i<numMag; i++) {
			double mfdY = mfdVals.getY(i);
			double sumY = sumVals.getY(i);
			double pDiff = DataUtils.getPercentDiff(sumY, mfdY);
			Preconditions.checkState(pDiff < 0.01, "Fails within 0.01%: mfdY="+mfdY+", sumY="+sumY+", pDiff="+pDiff+"%");
		}
	}
	
	public static EvenlyDiscretizedFunc calcProbsFromSummedMFD(EvenlyDiscretizedFunc cmlMFD, double duration) {
		int numMag = cmlMFD.size();
		EvenlyDiscretizedFunc result = new EvenlyDiscretizedFunc(cmlMFD.getMinX(), numMag, cmlMFD.getDelta());
		
		// convert from rates to poisson probabilities
		for (int i=0; i<numMag; i++) {
			double rate = cmlMFD.getY(i);
			double prob = 1-Math.exp(-rate*duration);
			result.set(i, prob);
		}
		return result;
	}
	
	private static void calcSummedProbs(List<List<Double>> probsList, EvenlyDiscretizedFunc result) {
		// now sum the probabilities as:
		// totProb = 1 - (1 - prob1)*(1 - prob2)*...*(1 - probN)
		for (int i=0; i<result.size(); i++) {
			List<Double> probs = probsList.get(i);
			double totProb = calcSummedProbs(probs);
			result.set(i, totProb);
//			System.out.println("\tM "+result.getX(i)+"+ Prob: "+(float)(totProb*100d)+" %");
		}
	}
	
	public static double calcSummedProbs(List<Double> probs) {
		double totOneMinus = 1;
		for (double prob : probs) {
			totOneMinus *= (1-prob);
		}
		double totProb = 1 - totOneMinus;
		
		return totProb;
	}
	
	private static void populateProbList(double mag, double prob, List<List<Double>> probsList,
			EvenlyDiscretizedFunc xVals) {
		// we want to find the smallest mag in the function where rupMag >= mag
		if (mag < xVals.getMinX())
			return;
		int magIndex = xVals.getClosestXIndex(mag);
		// closest could be above, check for that and correct
		if (mag < xVals.getX(magIndex))
			magIndex--;
		Preconditions.checkState(magIndex >= 0);
		for (int m=0; m<=magIndex && m<xVals.size(); m++)
			probsList.get(m).add(prob);
	}
	
	/**
	 * This calculates rupture probability distributions for supra-seismogenic ruptures
	 * on each fault section from the given ERF. Functions are returned in an array
	 * corresponding to the section index in the FaultSystemRupSet, with magnitude in the
	 * x field and cumulative probability in the y field.
	 * 
	 * @param erf
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 * @return
	 */
	public static EvenlyDiscretizedFunc[] calcSubSectSupraSeisMagProbDists(
			FaultSystemSolutionERF erf, double minMag, int numMag, double deltaMag) {
		return calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag, 10d);
	}
	
	public static EvenlyDiscretizedFunc[] calcSubSectSupraSeisMagProbDists(
			FaultSystemSolutionERF erf, double minMag, int numMag, double deltaMag, double overallMaxMag) {
		erf.updateForecast();
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		int numSects = rupSet.getNumSections();
		
		// create a list of all rupture probs for each section
		List<List<List<Double>>> sectProbLists = Lists.newArrayList();
		for (int i=0; i<numSects; i++) {
			List<List<Double>> probLists = Lists.newArrayList();
			for (int m=0; m<numMag; m++)
				probLists.add(new ArrayList<Double>());
			sectProbLists.add(probLists);
		}
		
		EvenlyDiscretizedFunc xVals = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
		
		for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
			int invIndex = erf.getFltSysRupIndexForSource(sourceID);
			for (ProbEqkRupture rup : erf.getSource(sourceID)) {
				double mag = rup.getMag();
				if (mag > overallMaxMag)
					continue;
				double prob = rup.getProbability();
				for (int sectIndex : rupSet.getSectionsIndicesForRup(invIndex)) {
					populateProbList(mag, prob, sectProbLists.get(sectIndex), xVals);
				}
			}
		}
		
		EvenlyDiscretizedFunc[] results = new EvenlyDiscretizedFunc[numSects];
		for (int sectIndex=0; sectIndex<numSects; sectIndex++) {
			results[sectIndex] = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			calcSummedProbs(sectProbLists.get(sectIndex), results[sectIndex]);
		}
		return results;
	}
	
	/**
	 * This calculates rupture probability distributions for supra-seismogenic ruptures
	 * on each parent fault section from the given ERF. Functions are returned in a map
	 * keyed on the parent section ID, with magnitude in the x field and cumulative
	 * probability in the y field.
	 * 
	 * @param erf
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 * @return
	 */
	public static Map<Integer, EvenlyDiscretizedFunc> calcParentSectSupraSeisMagProbDists(
			FaultSystemSolutionERF erf, double minMag, int numMag, double deltaMag) {
		return calcParentSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag, 10d);
	}
	
	private static Map<Integer, EvenlyDiscretizedFunc> calcParentSectSupraSeisMagProbDists(
			FaultSystemSolutionERF erf, double minMag, int numMag, double deltaMag, double overallMaxMag) {
		erf.updateForecast();
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		
		HashSet<Integer> parentIDs = new HashSet<Integer>();
		for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList())
			parentIDs.add(sect.getParentSectionId());
		
		// create a list of all rupture probs for each parent section
		Map<Integer, List<List<Double>>> sectProbLists = Maps.newHashMap();
		for (Integer parentID : parentIDs) {
			List<List<Double>> probLists = Lists.newArrayList();
			for (int m=0; m<numMag; m++)
				probLists.add(new ArrayList<Double>());
			sectProbLists.put(parentID, probLists);
		}
		
		EvenlyDiscretizedFunc xVals = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
		
		for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
			int invIndex = erf.getFltSysRupIndexForSource(sourceID);
			for (ProbEqkRupture rup : erf.getSource(sourceID)) {
				double mag = rup.getMag();
				if (mag > overallMaxMag)
					continue;
				double prob = rup.getProbability();
				for (int parentID : rupSet.getParentSectionsForRup(invIndex)) {
					populateProbList(mag, prob, sectProbLists.get(parentID), xVals);
				}
			}
		}
		
		Map<Integer, EvenlyDiscretizedFunc> results = Maps.newHashMap();
		for (int parentID : parentIDs) {
			EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			calcSummedProbs(sectProbLists.get(parentID), func);
			results.put(parentID, func);
		}
		return results;
	}
	
	private static final double YEARS_PER_MILLI = 1d/((double)(1000l*60l*60l*24l)*365.242);
	
	
	
	
	/**
	 * This generates a set of statewide fault probability gain maps for the given fault system
	 * solution.
	 * @param erf
	 * @param saveDir directory where plots should be saved
	 * @param prefix file prefix
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 * @throws IOException
	 */
	public static void makeFaultProbGainMaps(FaultSystemSolutionERF erf, File saveDir, String prefix
			) throws GMT_MapException, RuntimeException, IOException {
		double minMag = 6.7;
		int numMag = 4;
		double deltaMag = 0.5;
		
		double duration = erf.getTimeSpan().getDuration();
		
		FaultSystemSolution sol = erf.getSolution();
		
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.getTimeSpan().setDuration(duration);
		erf.updateForecast();
		
		EvenlyDiscretizedFunc[] poissonFuncs = calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
		EvenlyDiscretizedFunc[] poissonAllMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);
		EvenlyDiscretizedFunc[] poissonSmallMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag, 7d);
		
		// TODO historical open interval?
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		erf.getTimeSpan().setDuration(duration);
		erf.updateForecast();
		
		EvenlyDiscretizedFunc[] bptFuncs = calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
		EvenlyDiscretizedFunc[] bptAllMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);
		EvenlyDiscretizedFunc[] bptSmallMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag, 7d);
		
		// log space
		CPT probCPT = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-4, 0);
//		CPT ratioCPT = FaultBasedMapGen.getLogRatioCPT().rescale(-0.5, 0.5);
//		CPT ratioCPT = FaultBasedMapGen.getLinearRatioCPT();
		CPT ratioCPT = getScaledLinearRatioCPT(0.02);
		
		List<LocationList> faults = Lists.newArrayList();
		for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList())
			faults.add(sect.getFaultTrace());
		
		Region region = new CaliforniaRegions.RELM_COLLECTION();
		
		if (prefix == null)
			prefix = "";
		if (!prefix.isEmpty() && !prefix.endsWith("_"))
			prefix += "_";
		
		prefix += (int)duration+"yr";
		
		for (int i=0; i<numMag+2; i++) {
			
			double[] poissonVals;
			double[] bptVals;
			String myPrefix;
			String magStr;
			if (i == numMag) {
				poissonVals = extractYVals(poissonAllMags, 0);
				bptVals = extractYVals(bptAllMags, 0);
				myPrefix = prefix+"_supra_seis";
				magStr = "Supra Seis";
			} else if (i == numMag+1) {
				poissonVals = extractYVals(poissonSmallMags, 0);
				bptVals = extractYVals(bptSmallMags, 0);
				myPrefix = prefix+"_below_7";
				magStr = "All M<=7";
			} else {
				poissonVals = extractYVals(poissonFuncs, i);
				bptVals = extractYVals(bptFuncs, i);
				double mag = poissonFuncs[0].getX(i);
				myPrefix = prefix+"_"+(float)mag+"+";
				magStr = "M>="+(float)mag;
			}
			
			double[] ratioVals = new double[poissonVals.length];
			for (int j=0; j<ratioVals.length; j++)
				ratioVals[j] = bptVals[j]/poissonVals[j];
			
			// poisson probs
			FaultBasedMapGen.makeFaultPlot(probCPT, faults, FaultBasedMapGen.log10(poissonVals), region,
					saveDir, myPrefix+"_poisson", false, true,
					"Log10("+(float)duration+" yr "+magStr+" Poisson Prob)");
			// bpt probs
			FaultBasedMapGen.makeFaultPlot(probCPT, faults, FaultBasedMapGen.log10(bptVals), region,
					saveDir, myPrefix+"_bpt", false, true,
					"Log10("+(float)duration+" yr "+magStr+" BPT Prob)");
			// prob gain
			FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, ratioVals, region,
					saveDir, myPrefix+"_prob_gain", false, true,
					(float)duration+" yr "+magStr+" BPT/Poisson Prob Gain");
		}
		
		// now make normalized time since last event
		double[] normTimeSinceLast = new double[poissonAllMags.length];
//		ProbabilityModelsCalc calc = new ProbabilityModelsCalc(
//				((BPT_AperiodicityParam)erf.getParameter(BPT_AperiodicityParam.NAME)).getValue());
		ProbabilityModelsCalc calc = new ProbabilityModelsCalc(erf);
		double[] sectImpliedProbGain = new double[poissonAllMags.length];
		long curTime = System.currentTimeMillis();
		FaultSystemRupSet rupSet = sol.getRupSet();
		double[] partRates = sol.calcTotParticRateForAllSects();
		for (int i=0; i<normTimeSinceLast.length; i++) {
			FaultSectionPrefData sect = rupSet.getFaultSectionData(i);
			long dateLast = sect.getDateOfLastEvent();
			if (dateLast == Long.MIN_VALUE) {
				normTimeSinceLast[i] = Double.NaN;
				sectImpliedProbGain[i] = Double.NaN;
			} else {
				long deltaMillis = curTime - dateLast;
				double diffYears = YEARS_PER_MILLI*deltaMillis;
				double ri = 1d/partRates[i];
				normTimeSinceLast[i] = diffYears / ri;
				double bptProb = calc.computeBPT_ProbFast(ri, diffYears, duration, 0d);
				double poissonProb = ProbabilityModelsCalc.computePoissonProb(ri, duration);
				sectImpliedProbGain[i] = bptProb/poissonProb;
			}
		}
		// norm time since last
		FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, normTimeSinceLast, region,
				saveDir, prefix+"_norm_time_since_last", false, true,
				"Normalized Time Since Last Event");
		// sect implied
		FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, sectImpliedProbGain, region,
				saveDir, prefix+"_sect_implied_prob_gain", false, true,
				"Sect Implied Prob Gain");
	}
	
	
	
	
	
	/**
	 * This generates various state-wide fault probability maps for the WG02 approach.
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 * @throws IOException
	 */
	public static void makeWG02_FaultProbMaps() throws GMT_MapException, RuntimeException, IOException {
		double minMag = 6.7;
		int numMag = 4;
		double deltaMag = 0.5;
		
		String prefix = "aper0pt3";
		String dirName = "WG02_tests_Aper0pt3";
		File saveDir = new File(dirName);
		if (!saveDir.exists())
			saveDir.mkdir();
		
		
		FaultSystemSolution meanSol=null;
		try {
			meanSol = FaultSystemIO.loadSol(
					new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
							"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		} catch (DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		double duration = 30d;
		String durStr = (int)duration+"yr";
		
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(meanSol);
		erf.getTimeSpan().setDuration(duration);

		
		FaultSystemSolution sol = erf.getSolution();
		
		// Poisson values
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		
		EvenlyDiscretizedFunc[] poissonFuncs = calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
		EvenlyDiscretizedFunc[] poissonAllMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);
		
		// U3 Values for const aper=0.3
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		erf.setParameter(MagDependentAperiodicityParam.NAME, MagDependentAperiodicityOptions.ALL_PT3_VALUES);
		erf.updateForecast();
		
		EvenlyDiscretizedFunc[] bptFuncs = calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
		EvenlyDiscretizedFunc[] bptAllMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);
	
		// WG02 Values for const aper=0.3
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.WG02_BPT);
		erf.setParameter(MagDependentAperiodicityParam.NAME, MagDependentAperiodicityOptions.ALL_PT3_VALUES);
		erf.updateForecast();
		
		EvenlyDiscretizedFunc[] wg02_Funcs = calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
		EvenlyDiscretizedFunc[] wg02_AllMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);

		// log space
		CPT probCPT = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-4, 0);
//		CPT ratioCPT = FaultBasedMapGen.getLogRatioCPT().rescale(-0.5, 0.5);
//		CPT ratioCPT = FaultBasedMapGen.getLinearRatioCPT();
		CPT ratioCPT = getScaledLinearRatioCPT(0.02);
		
		List<LocationList> faults = Lists.newArrayList();
		for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList())
			faults.add(sect.getFaultTrace());
		
		Region region = new CaliforniaRegions.RELM_COLLECTION();
		
		if (prefix == null)
			prefix = "";
		if (!prefix.isEmpty() && !prefix.endsWith("_"))
			prefix += "_";
		
		prefix += (int)duration+"yr";
		
		for (int i=0; i<numMag+1; i++) {
			
			double[] poissonVals;
			double[] bptVals;
			double[] wg02_Vals;
			String myPrefix;
			String magStr;
			if (i == numMag) {
				poissonVals = extractYVals(poissonAllMags, 0);
				bptVals = extractYVals(bptAllMags, 0);
				wg02_Vals =  extractYVals(wg02_AllMags, 0);
				myPrefix = prefix+"_supra_seis";
				magStr = "Supra Seis";
			} else {
				poissonVals = extractYVals(poissonFuncs, i);
				bptVals = extractYVals(bptFuncs, i);
				wg02_Vals =  extractYVals(wg02_Funcs, 0);
				double mag = poissonFuncs[0].getX(i);
				myPrefix = prefix+"_"+(float)mag+"+";
				magStr = "M>="+(float)mag;
			}
			
			double[] wg02overPoisVavs = new double[poissonVals.length];
			double[] U3overWG02_Vavs = new double[poissonVals.length];
			for (int j=0; j<wg02overPoisVavs.length; j++) {
				wg02overPoisVavs[j] = wg02_Vals[j]/poissonVals[j];
				U3overWG02_Vavs[j] = bptVals[j]/wg02_Vals[j];
			}
			
			// poisson probs
			FaultBasedMapGen.makeFaultPlot(probCPT, faults, FaultBasedMapGen.log10(poissonVals), region,
					saveDir, myPrefix+"_poisson", false, true,
					"Log10("+(float)duration+" yr "+magStr+" Poisson Prob)");
			// bpt probs
			FaultBasedMapGen.makeFaultPlot(probCPT, faults, FaultBasedMapGen.log10(bptVals), region,
					saveDir, myPrefix+"_U3", false, true,
					"Log10("+(float)duration+" yr "+magStr+" U3 Prob)");
			// bpt probs
			FaultBasedMapGen.makeFaultPlot(probCPT, faults, FaultBasedMapGen.log10(wg02_Vals), region,
					saveDir, myPrefix+"_WG02", false, true,
					"Log10("+(float)duration+" yr "+magStr+" WG02 Prob)");
			// prob gain
			FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, wg02overPoisVavs, region,
					saveDir, myPrefix+"_WG02_prob_gain", false, true,
					(float)duration+" yr "+magStr+" WG02 Prob Gain");
			// prob gain
			FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, U3overWG02_Vavs, region,
					saveDir, myPrefix+"_U2overWG02_ratio", false, true,
					(float)duration+" yr "+magStr+" U3 over WG02 Prob Ratio");
		}
		
	}
	
	private static boolean[] toArray(boolean... vals) {
		return vals;
	}
	
	/**
	 * This generates a set of statewide fault probability gain maps for the given fault system
	 * solution, exploring different averaging options.
	 * @param erf no need to update forecast
	 * @param saveDir directory where plots should be saved
	 * @param prefix file prefix
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 * @throws IOException
	 */
	public static void makeAvgMethodProbGainMaps(FaultSystemSolutionERF erf, File saveDir, String prefix
			) throws GMT_MapException, RuntimeException, IOException {
		
		// TRUE: RI, NTS
		// FALSE: Rate, TS
		List<BPTAveragingTypeOptions> avgTypes = Lists.newArrayList();
		int refIndex = 1;
//		avgBools.add(toArray(false, false));
		avgTypes.add(BPTAveragingTypeOptions.AVE_RATE_AVE_NORM_TIME_SINCE);
		avgTypes.add(BPTAveragingTypeOptions.AVE_RI_AVE_TIME_SINCE); // reference
		avgTypes.add(BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE);
		
		
		makeAvgMethodProbGainMaps(erf, saveDir, prefix, avgTypes, refIndex);
		
		avgTypes = Lists.newArrayList();
		refIndex = 0;
		avgTypes.add(BPTAveragingTypeOptions.AVE_RATE_AVE_NORM_TIME_SINCE); // reference
		avgTypes.add(BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE);
		
		makeAvgMethodProbGainMaps(erf, saveDir, prefix, avgTypes, refIndex);
	}
	
	private static void makeAvgMethodProbGainMaps(FaultSystemSolutionERF erf, File saveDir, String prefix,
			List<BPTAveragingTypeOptions> avgTypes, int refIndex) throws GMT_MapException, RuntimeException, IOException {
		double minMag = 6.7;
		int numMag = 4;
		double deltaMag = 0.5;
		double duration = erf.getTimeSpan().getDuration();
		System.out.println("Duration: "+duration);
		
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		erf.getTimeSpan().setDuration(duration);
		erf.updateForecast();
		
		List<EvenlyDiscretizedFunc[]> regFuncsList = Lists.newArrayList();
		List<EvenlyDiscretizedFunc[]> allMagsList = Lists.newArrayList();
		List<EvenlyDiscretizedFunc[]> smallMagsList = Lists.newArrayList();
		
		for (int i=0; i<avgTypes.size(); i++) {
			BPTAveragingTypeOptions avgType = avgTypes.get(i);
			erf.setParameter(BPTAveragingTypeParam.NAME, avgType);
			// do this to make sure it gets updated
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
			erf.getTimeSpan().setDuration(duration);
			
			regFuncsList.add(calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag));
			allMagsList.add(calcSubSectSupraSeisMagProbDists(erf, 0d, numMag, deltaMag));
			smallMagsList.add(calcSubSectSupraSeisMagProbDists(erf, 0d, numMag, deltaMag, 7d));
		}
		
//		CPT ratioCPT = FaultBasedMapGen.getLinearRatioCPT().rescale(0.8d, 1.2d);
//		CPT ratioCPT = getScaledLinearRatioCPT(0.02, 0.8d, 1.2d);
		CPT ratioCPT = getScaledLinearRatioCPT(0.02);
		
		FaultSystemSolution sol = erf.getSolution();
		
		List<LocationList> faults = Lists.newArrayList();
		for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList())
			faults.add(sect.getFaultTrace());
		
		Region region = new CaliforniaRegions.RELM_COLLECTION();
		
		if (prefix == null)
			prefix = "";
		if (!prefix.isEmpty() && !prefix.endsWith("_"))
			prefix += "_";
		
		prefix += (float)duration+"yr";
		
		String refLabel = avgTypes.get(refIndex).getCompactLabel();
		
		for (int i=0; i<numMag+2; i++) {
			for (int j=0; j<avgTypes.size(); j++) {
				if (j == refIndex)
					continue;
				String testLabel = avgTypes.get(j).getCompactLabel();
				double[] testVals;
				double[] refVals;
				String myPrefix;
				String magStr;
				if (i == numMag) {
					testVals = extractYVals(allMagsList.get(j), 0);
					refVals = extractYVals(allMagsList.get(refIndex), 0);
					myPrefix = prefix+"_supra_seis";
					magStr = "Supra Seis";
				} else if (i == numMag+1) {
					testVals = extractYVals(smallMagsList.get(j), 0);
					refVals = extractYVals(smallMagsList.get(refIndex), 0);
					myPrefix = prefix+"_below_7";
					magStr = "All M<=7";
				} else {
					testVals = extractYVals(regFuncsList.get(j), 0);
					refVals = extractYVals(regFuncsList.get(refIndex), 0);
					double mag = regFuncsList.get(j)[0].getX(i);
					myPrefix = prefix+"_"+(float)mag+"+";
					magStr = "M>="+(float)mag;
				}
				
				double[] ratioVals = new double[testVals.length];
				for (int k=0; k<ratioVals.length; k++)
					ratioVals[k] = testVals[k]/refVals[k];
				
				String fName = myPrefix+"_"+testLabel.replaceAll("\\W+", "_")+"_vs_"+refLabel.replaceAll("\\W+", "_");
				
				// prob gain
				FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, ratioVals, region,
						saveDir, fName, false, true,
						(float)duration+"yr "+magStr+testLabel+"/"+refLabel);
			}
		}
	}
	
	private static double[] extractYVals(EvenlyDiscretizedFunc[] funcs, int index) {
		double[] vals = new double[funcs.length];
		for (int i=0; i<funcs.length; i++)
			vals[i] = funcs[i].getY(index);
		return vals;
	}
	
	private static void populateMagDurationXYZ(FaultSystemSolutionERF erf, EvenlyDiscrXYZ_DataSet[] xyzs,
			Region[] regions, FSSRupsInRegionCache cache) {
		double minMag = xyzs[0].getMinY();
		int numMag = xyzs[0].getNumY();
		double deltaMag = xyzs[0].getGridSpacingY();
		for (int x=0; x<xyzs[0].getNumX(); x++) {
			// x is duration
			double duration = xyzs[0].getX(x);
			erf.getTimeSpan().setDuration(duration);
			erf.updateForecast();
			
			for (int i=0; i<xyzs.length; i++) {
				EvenlyDiscretizedFunc func = calcMagProbDist(erf, regions[i], minMag, numMag, deltaMag, true, cache);
				for (int y=0; y<numMag; y++)
					xyzs[i].set(x, y, func.getY(y));
			}
		}
	}
	
	/**
	 * This makes XYZ plots of probability as a function of mag/duration for various california regions
	 * with both Poisson and BPT probabilities (and prob gains).
	 * @param erf
	 * @param saveDir
	 * @param prefix
	 * @throws IOException
	 */
	public static void makeMagDurationProbPlots(FaultSystemSolutionERF erf, File saveDir, String prefix)
			throws IOException {
		double minMag = 6.5;
		int numMag = 21;
		double deltaMag = 0.1;
		
		double minDuration = 5d;
		double deltaDuration = 5d;
		int numDuration = 20;
		
		Region[] regions = { new CaliforniaRegions.RELM_COLLECTION(), new CaliforniaRegions.RELM_SOCAL(),
				new CaliforniaRegions.RELM_NOCAL(), new CaliforniaRegions.LA_BOX(), new CaliforniaRegions.SF_BOX() };
		String[] regNames = { "Statewide", "So Cal", "No Cal", "LA", "SF" };
		
		EvenlyDiscrXYZ_DataSet[] poissonXYZs = new EvenlyDiscrXYZ_DataSet[regions.length];
		EvenlyDiscrXYZ_DataSet[] bptXYZs = new EvenlyDiscrXYZ_DataSet[regions.length];
		
		for (int r=0; r<regions.length; r++) {
			poissonXYZs[r] = new EvenlyDiscrXYZ_DataSet(
					numDuration, numMag, minDuration, minMag, deltaDuration, deltaMag);
			bptXYZs[r] = new EvenlyDiscrXYZ_DataSet(
					numDuration, numMag, minDuration, minMag, deltaDuration, deltaMag);
		}
		
		FSSRupsInRegionCache cache = new FSSRupsInRegionCache();
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		
		populateMagDurationXYZ(erf, poissonXYZs, regions, cache);
		
		// TODO historical open interval?
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		
		populateMagDurationXYZ(erf, bptXYZs, regions, cache);
		
		CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0d, 1d);
		CPT ratioCPT = (CPT)FaultBasedMapGen.getLinearRatioCPT().clone();
		ratioCPT.setNanColor(Color.WHITE);
		
		if (prefix == null)
			prefix = "";
		if (!prefix.isEmpty() && !prefix.endsWith("_"))
			prefix += "_";
		
		for (int r=0; r<regions.length; r++) {
			EvenlyDiscrXYZ_DataSet ratioXYZ = new EvenlyDiscrXYZ_DataSet(
					numDuration, numMag, minDuration, minMag, deltaDuration, deltaMag);
			for (int i=0; i<ratioXYZ.size(); i++)
				ratioXYZ.set(i, bptXYZs[r].get(i)/poissonXYZs[r].get(i));
			
			String name = regNames[r];
			String fName = prefix+name.toLowerCase().replaceAll("\\W+", "_");
			
			writeMagProbXYZ(cpt, poissonXYZs[r], name+" Poisson Prob", "Probability", saveDir, fName+"_poisson");
			writeMagProbXYZ(cpt, bptXYZs[r], name+" BPT Prob", "Probability", saveDir, fName+"_bpt");
			writeMagProbXYZ(ratioCPT, ratioXYZ, name+" BPT/Poisson Prob Gain", "Prob Gain", saveDir, fName+"_prob_gain");
		}
	}
	
	private static void writeMagProbXYZ(CPT cpt, EvenlyDiscrXYZ_DataSet xyz, String title, String zLabel, File saveDir, String prefix)
			throws IOException {
		XYZPlotSpec spec = new XYZPlotSpec(xyz, cpt, title, "Duration (years)", "Min Mag", zLabel);
		
		Range xRange = new Range(xyz.getMinX()-0.5*xyz.getGridSpacingX(), xyz.getMaxX()+0.5*xyz.getGridSpacingX());
		Range yRange = new Range(xyz.getMinY()-0.5*xyz.getGridSpacingY(), xyz.getMaxY()+0.5*xyz.getGridSpacingY());
		
		XYZGraphPanel gp = new XYZGraphPanel();
		gp.drawPlot(spec, false, false, xRange, yRange);
		
		File file = new File(saveDir, prefix);
		
		gp.getChartPanel().setSize(1000, 1000);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		
		// now write XYZ
		EvenlyDiscrXYZ_DataSet.writeXYZFile(xyz, file.getAbsolutePath()+"_xyz.txt");
	}
	
	/**
	 * This writes out a CSV file of varios probabilities/rates for each sub section.
	 * @param erf
	 * @param outputFile
	 * @throws IOException
	 */
	public static void writeSubSectionTimeDependenceCSV(FaultSystemSolutionERF erf, File outputFile)
			throws IOException {
		writeTimeDependenceCSV(erf, outputFile, false);
	}
	
	private static Map<Integer, EvenlyDiscretizedFunc> remap(EvenlyDiscretizedFunc[] funcs) {
		Map<Integer, EvenlyDiscretizedFunc> map = Maps.newHashMap();
		for (int i=0; i<funcs.length; i++)
			map.put(i, funcs[i]);
		return map;
	}
	
	private static class SubSectNameComparator implements Comparator<String> {
		private final String key = "Subsection ";
		private final int len = key.length();

		@Override
		public int compare(String o1, String o2) {
			int ss1_index = o1.indexOf(key)+len;
			int ss2_index = o2.indexOf(key)+len;
			int ret = o1.substring(0, ss1_index).compareTo(o2.substring(0, ss2_index));
			if (ret != 0)
				return ret;
			// this means same parent section
			int ss1 = Integer.parseInt(o1.substring(ss1_index));
			int ss2 = Integer.parseInt(o2.substring(ss2_index));
			Preconditions.checkState(ss1 >= 0 && ss2 >= 0);
			return new Integer(ss1).compareTo(ss2);
		}
		
	}
	
	private static void writeTimeDependenceCSV(FaultSystemSolutionERF erf, File outputFile, boolean parent)
			throws IOException {
		CSVFile<String> csv = new CSVFile<String>(true);
		double minMag = 6.7;
		int numMag = 4;
		double deltaMag = 0.5;
		
		FaultSystemSolution sol = erf.getSolution();
		double duration = erf.getTimeSpan().getDuration();
		
		double allMagMin = 5.2d;
		int numAllMag = 3+numMag;
		double allMagMax = allMagMin + deltaMag*(numAllMag-1);
		String[] magRangeStrs = { "M>=6.7", "M>=7.2", "M>=7.7", "M>=8.2", "Supra Seis", "M<=7.0" };
		
		// header
		List<String> header = Lists.newArrayList("Section Name");
		if (parent)
			header.add("Parent Section ID");
		else
			header.add("Sub Section ID");
		for (String rangeStr : magRangeStrs) {
			header.add(rangeStr);
			// these are just averaged (unweighted) since area approx the same for subsections of a parent
			header.add("Recurr Int.");
			if (parent)
				header.add("Open Int. Where Known");
			else
				header.add("Open Int.");
			header.add("Ppois");
			header.add("Pbpt");
			header.add("Prob Gain");
			header.add("Sect Impl Gain");
		}
		csv.addLine(header);
		
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.getTimeSpan().setDuration(duration);
		erf.updateForecast();
		
		Map<Integer, EvenlyDiscretizedFunc> poissonFuncs, poissonAllMags, poissonSmallMags;
		if (parent) {
			poissonFuncs = calcParentSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
			poissonAllMags = calcParentSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);
			poissonSmallMags = calcParentSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag, 7d);
		} else {
			poissonFuncs = remap(calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag));
			poissonAllMags = remap(calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag));
			poissonSmallMags = remap(calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag, 7d));
		}
		
		// TODO historical open interval?
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		erf.getTimeSpan().setDuration(duration);
		erf.updateForecast();

		Map<Integer, EvenlyDiscretizedFunc> bptFuncs, bptAllMags, bptSmallMags;
		if (parent) {
			bptFuncs = calcParentSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
			bptAllMags = calcParentSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);
			bptSmallMags = calcParentSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag, 7d);
		} else {
			bptFuncs = remap(calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag));
			bptAllMags = remap(calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag));
			bptSmallMags = remap(calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag, 7d));
		}
		
		// parent names and mapping to IDs
		HashSet<String> namesSet = new HashSet<String>();
		Map<String, Integer> nameIDMap = Maps.newHashMap();
		Map<Integer, List<Long>> parentLastEventMap = Maps.newHashMap();
		for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList()) {
			if (parent) {
				int parentID = sect.getParentSectionId();
				String parentName = sect.getParentSectionName();
				if (!namesSet.contains(parentName)) {
					namesSet.add(parentName);
					nameIDMap.put(parentName, parentID);
					parentLastEventMap.put(parentID, new ArrayList<Long>());
				}
				if (sect.getDateOfLastEvent() > Long.MIN_VALUE)
					parentLastEventMap.get(parentID).add(sect.getDateOfLastEvent());
			} else {
				int sectID = sect.getSectionId();
				String name = sect.getSectionName();
				namesSet.add(name);
				nameIDMap.put(name, sectID);
			}
		}
		List<String> sectNames = Lists.newArrayList(namesSet);
		Collections.sort(sectNames, new SubSectNameComparator());
		Collections.sort(sectNames);
		
		long curMillis = System.currentTimeMillis();
		
//		ProbabilityModelsCalc calc = new ProbabilityModelsCalc(
//				((BPT_AperiodicityParam)erf.getParameter(BPT_AperiodicityParam.NAME)).getValue());
		ProbabilityModelsCalc calc = new ProbabilityModelsCalc(erf);
		
		for (String sectName : sectNames) {
			List<String> line = Lists.newArrayList();
			
			Integer sectID = nameIDMap.get(sectName);
			
			line.add(sectName);
			line.add(sectID+"");
			
			double oi;
			List<Long> lastDates = parentLastEventMap.get(sectID);
			if (!parent) {
				lastDates = Lists.newArrayList();
				long last = sol.getRupSet().getFaultSectionData(sectID).getDateOfLastEvent();
				if (last > Long.MIN_VALUE)
					lastDates.add(last);
			}
			if (lastDates.isEmpty()) {
				oi = Double.NaN;
			} else {
				oi = 0d;
				for (long lastDate : lastDates) {
					long deltaMillis = curMillis - lastDate;
					double diffYears = YEARS_PER_MILLI*deltaMillis;
					oi += diffYears;
				}
				oi /= lastDates.size();
			}
			
			for (int i=0; i<numMag+2; i++) {
				line.add("");
				
				IncrementalMagFreqDist mfd;
				if (parent)
					mfd = sol.calcParticipationMFD_forParentSect(
						sectID, allMagMin+0.5*deltaMag, allMagMax+0.5*deltaMag, numAllMag);
				else
					mfd = sol.calcParticipationMFD_forSect(
							sectID, allMagMin+0.5*deltaMag, allMagMax+0.5*deltaMag, numAllMag);
				EvenlyDiscretizedFunc cmlMFD = mfd.getCumRateDistWithOffset();
				
				double poissonProb;
				double bptProb;
				double ri;
				double implCompareMag;
				if (i == numMag) {
					poissonProb = poissonAllMags.get(sectID).getY(0);
					bptProb = bptAllMags.get(sectID).getY(0);
					ri = 1d/cmlMFD.getY(0);
					implCompareMag = 0;
				} else if (i == numMag+1) {
					poissonProb = poissonSmallMags.get(sectID).getY(0);
					bptProb = bptSmallMags.get(sectID).getY(0);
					double smallRate = 0d;
					for (Point2D pt : mfd) {
						if (pt.getX()>7d)
							break;
						smallRate += pt.getY();
					}
					ri = 1d/smallRate;
					implCompareMag = 0;
				} else {
					poissonProb = poissonFuncs.get(sectID).getY(i);
					bptProb = bptFuncs.get(sectID).getY(i);
					implCompareMag = poissonFuncs.get(sectID).getX(i)+0.05; // make sure to get above
					ri = 1d/cmlMFD.getY(i+(numAllMag - numMag));
				}
				
				line.add(ri+"");
				line.add(oi+"");
				line.add(poissonProb+"");
				line.add(bptProb+"");
				line.add(bptProb/poissonProb+"");
				double implBPTProb = calc.computeBPT_ProbFast(ri, oi, duration, implCompareMag);
				double implPoissonProb = ProbabilityModelsCalc.computePoissonProb(ri, duration);
				line.add(implBPTProb/implPoissonProb+"");
			}
			csv.addLine(line);
		}
		
		csv.writeToFile(outputFile);
	}
	
	/**
	 * This writes out a CSV file of varios probabilities/rates aggregated over each parent section. Parent section
	 * RI's are calculated as 1 over total parent section partiticpation rate. Open intervals are averaged among all
	 * fault sections where known, or NaN if no sections of a parent have a date of last event.
	 * @param erf
	 * @param outputFile
	 * @throws IOException
	 */
	public static void writeParentSectionTimeDependenceCSV(FaultSystemSolutionERF erf, File outputFile)
			throws IOException {
		writeTimeDependenceCSV(erf, outputFile, true);
	}
	
	/**
	 * This writes out the rupture gains for the different averaging methods, and for the hard-coded erf.
	 */
	public static void writeDiffAveragingMethodsRupProbGains(double yrForOpenInterval) {

		String fileName="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(fileName);
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
		erf.getTimeSpan().setDuration(30d);
		int startYear = erf.getTimeSpan().getStartTimeYear();
		double openIntervalYrs = (double)startYear-yrForOpenInterval;
		System.out.println("ERF startYear="+startYear+": openIntervalYrs="+openIntervalYrs);
		erf.setParameter(HistoricOpenIntervalParam.NAME, openIntervalYrs);


		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.LOW_VALUES);
		erf.updateForecast();
		ProbabilityModelsCalc testCalc = new ProbabilityModelsCalc(erf);
		testCalc.writeRupProbGainsForDiffAveragingMethods(erf.getTimeSpan().getStartTimeInMillis(), 
				erf.getTimeSpan().getDuration(), "RupProbGainsForDiffAveMethods30yrs_LowApers"+"_HistOpenInt"+Math.round(openIntervalYrs)+".txt");
		
		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.MID_VALUES);
		erf.updateForecast();
		testCalc = new ProbabilityModelsCalc(erf);
		testCalc.writeRupProbGainsForDiffAveragingMethods(erf.getTimeSpan().getStartTimeInMillis(), 
				erf.getTimeSpan().getDuration(), "RupProbGainsForDiffAveMethods30yrs_MidApers"+"_HistOpenInt"+Math.round(openIntervalYrs)+".txt");
		
		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.HIGH_VALUES);
		erf.updateForecast();
		testCalc = new ProbabilityModelsCalc(erf);
		testCalc.writeRupProbGainsForDiffAveragingMethods(erf.getTimeSpan().getStartTimeInMillis(), 
				erf.getTimeSpan().getDuration(), "RupProbGainsForDiffAveMethods30yrs_HighApers"+"_HistOpenInt"+Math.round(openIntervalYrs)+".txt");
	}
	
	
	public static void testAftershockInclusionOnProbs() {
		String erfFileName="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(erfFileName);
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
		erf.getTimeSpan().setDuration(30d);
		int startYear = erf.getTimeSpan().getStartTimeYear();
		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.MID_VALUES);
		
		
		erf.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(true);
		erf.updateForecast();
		
		int numRups = erf.getTotNumRups();
		double[] probWith = new double[numRups];
		double[] probWithout = new double[numRups];
		double[] ratio = new double[numRups];

		for(int r=0; r<numRups; r++)
			probWithout[r]=erf.getNthRupture(r).getProbability();
		
		erf.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(false);
		erf.updateForecast();
		
		for(int r=0; r<numRups; r++) {
			probWith[r] = erf.getNthRupture(r).getProbability();
			ratio[r] = probWithout[r]/probWith[r];
		}
		
		double ave=0;
		int num=0;
		for(double val:ratio){
			if(!Double.isNaN(val) && !Double.isInfinite(val)) {
				ave += val;
				num +=1;
			}
		}
		
		for(int i=0; i<10;i++)
			System.out.println(ratio[i]);
		
		System.out.println("Average:\t"+ave/num);
		System.out.println("num="+num+"; ratio.length="+ratio.length);
	}
	
	
	/**
	 * This writes out the rupture gains for the different averaging methods, 
	 * for rups that utilize the given section, and for the mean fault system solution:
	 * 2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip
	 * 
	 */
	public static void writeDiffAveragingMethodsRupProbGains(int subSectIndex, double yrForOpenInterval) {
		
		 
		String erfFileName="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(erfFileName);
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
		erf.getTimeSpan().setDuration(30d);
		int startYear = erf.getTimeSpan().getStartTimeYear();
		double openIntervalYrs = (double)startYear-yrForOpenInterval;
		System.out.println("ERF startYear="+startYear+": openIntervalYrs="+openIntervalYrs);
		erf.setParameter(HistoricOpenIntervalParam.NAME, openIntervalYrs);

		
		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.LOW_VALUES);
		erf.updateForecast();
		String fileName = "sect"+subSectIndex+"_HistOpenInt"+Math.round(openIntervalYrs)+"_RupProbGainsForDiffAveMethods30yrs_LowApers.txt";
		ProbabilityModelsCalc testCalc = new ProbabilityModelsCalc(erf);
		testCalc.writeRupProbGainsForDiffAveragingMethods(erf.getTimeSpan().getStartTimeInMillis(), 
				erf.getTimeSpan().getDuration(), fileName,subSectIndex);
		
		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.MID_VALUES);
		erf.updateForecast();
		fileName = "sect"+subSectIndex+"_HistOpenInt"+Math.round(openIntervalYrs)+"_RupProbGainsForDiffAveMethods30yrs_MidApers.txt";
		testCalc = new ProbabilityModelsCalc(erf);
		testCalc.writeRupProbGainsForDiffAveragingMethods(erf.getTimeSpan().getStartTimeInMillis(), 
				erf.getTimeSpan().getDuration(), fileName,subSectIndex);
		
		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.HIGH_VALUES);
		erf.updateForecast();
		fileName = "sect"+subSectIndex+"_HistOpenInt"+Math.round(openIntervalYrs)+"_RupProbGainsForDiffAveMethods30yrs_HighApers.txt";
		testCalc = new ProbabilityModelsCalc(erf);
		testCalc.writeRupProbGainsForDiffAveragingMethods(erf.getTimeSpan().getStartTimeInMillis(), 
				erf.getTimeSpan().getDuration(), fileName,subSectIndex);
	}

	
	public static CPT getScaledLinearRatioCPT(double fractToWashOut) throws IOException {
		return getScaledLinearRatioCPT(fractToWashOut, 0d, 2d);
	}
	
	public static CPT getScaledLinearRatioCPT(double fractToWashOut, double min, double max) throws IOException {
		Preconditions.checkArgument(fractToWashOut >= 0 && fractToWashOut < 1);
		CPT ratioCPT = GMT_CPT_Files.UCERF3_HAZ_RATIO_P3.instance().rescale(min, max);
		CPT belowCPT = new CPT();
		CPT afterCPT = new CPT();
		for (CPTVal val : ratioCPT) {
			if (val.end < 1d)
				belowCPT.add(val);
			else if (val.start > 1d)
				afterCPT.add(val);
		}
		belowCPT = belowCPT.rescale(min, 1d-fractToWashOut);
		afterCPT = afterCPT.rescale(1d+fractToWashOut, max);
		CPT combCPT = (CPT) ratioCPT.clone();
		combCPT.clear();
		combCPT.addAll(belowCPT);
		if (fractToWashOut > 0) {
			Color washColor = combCPT.get(combCPT.size()-1).maxColor;
			combCPT.add(new CPTVal(belowCPT.getMaxValue(), washColor, afterCPT.getMinValue(), washColor));
		}
		combCPT.addAll(afterCPT);
		return combCPT;
	}
	
	
	/**
	 * This writes a csv file with info on sub-section time dependencies for the branch averaged solution:
	 * 2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip
	 * 
	 * @param outputDir
	 * @param yrForOpenInterval - year for historic open interval
	 * @throws IOException
	 */
	public static void writeDiffAveragingMethodsSubSectionTimeDependenceCSV(File outputDir, double yrForOpenInterval) throws IOException {
		
		if (!outputDir.exists())
			outputDir.mkdir();
		
		FaultSystemSolution meanSol;
		try {
			meanSol = FaultSystemIO.loadSol(new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
						"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}				
		
		
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(meanSol);
		double duration = 30d;
		erf.getTimeSpan().setDuration(duration);
		String durStr = (int)duration+"yr";
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
		int startYear = erf.getTimeSpan().getStartTimeYear();
		double openIntervalYrs = (double)startYear-yrForOpenInterval;
		System.out.println("ERF startYear="+startYear+": openIntervalYrs="+openIntervalYrs);
		erf.setParameter(HistoricOpenIntervalParam.NAME, openIntervalYrs);
		
		MagDependentAperiodicityOptions[] covFuncs = { MagDependentAperiodicityOptions.LOW_VALUES,
				MagDependentAperiodicityOptions.MID_VALUES, MagDependentAperiodicityOptions.HIGH_VALUES };

		for (MagDependentAperiodicityOptions cov : covFuncs) {
			erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(cov);
			
			List<BPTAveragingTypeOptions> avgTypesList = Lists.newArrayList();
			List<CSVFile<String>> csvFiles = Lists.newArrayList();
			
			for (BPTAveragingTypeOptions aveType : BPTAveragingTypeOptions.values()) {
				erf.setParameter(BPTAveragingTypeParam.NAME, aveType);
				String calcType = aveType.getFileSafeLabel();
				System.out.println("working on "+calcType);
				File csvFile = new File(outputDir, "SubSectProbData_"+durStr+"_"+cov.name()+"_COVs_"+calcType+"_HistOpenInt"+Math.round(openIntervalYrs)+".csv");
				writeSubSectionTimeDependenceCSV(erf, csvFile);
				
				// keep track of settings and parse the CSV file
				avgTypesList.add(aveType);
				csvFiles.add(CSVFile.readFile(csvFile, true));	
			}
			
			// now stitch into a master file for this COV func
			// mag ranges to do
			double[] minMags = { 0, 6.7 };
			// start col ("Recurr Int.") for that mag range
			int[] startCols = { 31, 3 };
			for (int i=0; i<minMags.length; i++) {
				double minMag = minMags[i];
				int startCol = startCols[i];
				
				// use this for common to all columns
				CSVFile<String> refCSV = csvFiles.get(0);
				
				CSVFile<String> csv = new CSVFile<String>(true);
				// add first two columns (name and ID
				csv.addColumn(refCSV.getColumn(0));
				csv.addColumn(refCSV.getColumn(1));
				
				// now add common to all values
				csv.addColumn(refCSV.getColumn(startCol)); // recurr int
				csv.addColumn(refCSV.getColumn(startCol+1)); // open int
				csv.addColumn(refCSV.getColumn(startCol+2)); // Ppois
				csv.addColumn(refCSV.getColumn(startCol+5)); // Sect Impl Gain
				
				// now for each calc setting
				for (int j=0; j<avgTypesList.size(); j++) {
					BPTAveragingTypeOptions avgTypes = avgTypesList.get(j);
					CSVFile<String> calcCSV = csvFiles.get(j);
					
					// now add blank column except for header which states settings
					List<String> headerCol = Lists.newArrayList();
					String calcType = avgTypes.getCompactLabel();
					headerCol.add(calcType);
					while (headerCol.size() < refCSV.getNumRows())
						headerCol.add("");
					csv.addColumn(headerCol);
					
					// now add unique data columns
					csv.addColumn(calcCSV.getColumn(startCol+3)); // Pbpt
					csv.addColumn(calcCSV.getColumn(startCol+4)); // Prob Gain
				}
				
				// write out combined CSV
				String magStr;
				if (minMag < 5)
					magStr = "supra_seis";
				else
					magStr = (float)minMag+"+";
				File csvFile = new File(outputDir, "SubSectProbData_"+durStr+"_"+cov.name()+"_COVs_"+magStr+"_HistOpenInt"+Math.round(openIntervalYrs)+"_combined.csv");
				csv.writeToFile(csvFile);
			}
		}
	}
	
	/**
	 * 
	 * @param zipFile
	 * @param outputDir
	 * @param parents
	 * @param magRangeIndex
	 * @param minMag
	 * @param duration
	 * @param cov
	 * @return map from parent section IDs to mean probs list (1 entry for parents, multiple for subs) 
	 * @throws ZipException
	 * @throws IOException
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 */
	public static Map<Integer, List<Double>> writeBranchAggregatedTimeDepFigs(File zipFile, File outputDir,
			boolean parents, int magRangeIndex, double minMag, double duration)
					throws ZipException, IOException, GMT_MapException, RuntimeException {
//		HashSet<MagDependentAperiodicityOptions> covsToInclude;
//		if (cov == null) {
//			// all
//			covsToInclude = null;
//		} else {
//			// just one
//			covsToInclude = new HashSet<MagDependentAperiodicityOptions>();
//			covsToInclude.add(cov);
//		}
//		
//		int magRangeIndex = 0; // 6.7+
		
		Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
				Map<LogicTreeBranch, SectProbGainResults[]>> table = loadBranchCSVVals(
				zipFile, new int[] {magRangeIndex}, parents).get(0);
		
		return writeBranchAggregatedTimeDepFigs(table, outputDir, parents, minMag, duration);
	}
	
	static Map<LogicTreeBranch, SectProbGainResults[]> calcPoissonValues(
			Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
			Map<LogicTreeBranch, SectProbGainResults[]>> table) {
		Map<LogicTreeBranch, SectProbGainResults[]> poisMap;
		
		// bpt is the same among all, so it doesn't matter which one we get
		Map<LogicTreeBranch, SectProbGainResults[]> refMap =
				table.get(table.rowKeySet().iterator().next(), table.columnKeySet().iterator().next());
		poisMap = Maps.newHashMap();
		for (LogicTreeBranch branch : refMap.keySet()) {
			SectProbGainResults[] refResults = refMap.get(branch);
			SectProbGainResults[] poisResults = new SectProbGainResults[refResults.length];
			for (int i=0; i<refResults.length; i++) {
				SectProbGainResults ref = refResults[i];
				double recurrInt = 1d/ref.pPois;
				// only put pPois as pTimeDep
				poisResults[i] = new SectProbGainResults(recurrInt, ref.openInt, ref.pPois, ref.pPois, 1d, ref.implGain);
			}
			// now add it in
			poisMap.put(branch, poisResults);
		}
		
		return poisMap;
	}
	
	public static Map<Integer, List<Double>> writeBranchAggregatedTimeDepFigs(
			Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
			Map<LogicTreeBranch, SectProbGainResults[]>> table, File outputDir,
			boolean parents, double minMag, double duration)
					throws ZipException, IOException, GMT_MapException, RuntimeException {
		
		Map<LogicTreeBranch, SectProbGainResults[]> branchVals;
		branchVals = Maps.newHashMap();
		double avgTypeWeight = 1d/(double)table.columnKeySet().size();
		double totCellWeight = 0d; // check that it all adds up to 1
		
		// if true, average along with poisson according to weights
		boolean isWeightedMultiCOV = table.rowKeySet().size() == 3
					&& table.rowKeySet().contains(MagDependentAperiodicityOptions.LOW_VALUES)
					&& table.rowKeySet().contains(MagDependentAperiodicityOptions.MID_VALUES)
					&& table.rowKeySet().contains(MagDependentAperiodicityOptions.HIGH_VALUES);
		Map<LogicTreeBranch, SectProbGainResults[]> poisMap;
		if (isWeightedMultiCOV) {
			// calculate the Poisson branch
			System.out.println("Multiple/Weighted COV!");
			// bpt is the same among all, so it doesn't matter which one we get
			poisMap = calcPoissonValues(table);
			// now add it in
			double pw = FaultSystemSolutionERF.PREF_BLEND_POISSON_WEIGHT;
			for (LogicTreeBranch branch : poisMap.keySet()) {
				SectProbGainResults[] poisResults = poisMap.get(branch);
				SectProbGainResults[] scaledResults = new SectProbGainResults[poisResults.length];
				for (int i=0; i<poisResults.length; i++) {
					SectProbGainResults pois = poisResults[i];
					scaledResults[i] = new SectProbGainResults(pw*pois.recurrInt, pw*pois.openInt,
							pw*pois.pPois, pw*pois.pTimeDep, pw*pois.pGain, pw*pois.implGain);
				}

				// now add it in
				branchVals.put(branch, scaledResults);
			}
			// add in the weighting from poisson that we just added in
			totCellWeight += pw;
		}
		HashSet<LogicTreeBranch> mojaveNaNBranches = new HashSet<LogicTreeBranch>();
		for (Cell<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
				Map<LogicTreeBranch, SectProbGainResults[]>> cell : table.cellSet()) {
			double cellWeight = avgTypeWeight;
			if (isWeightedMultiCOV) {
				cellWeight *= FaultSystemSolutionERF.getWeightForCOV(cell.getRowKey());
				if (cellWeight == 0)
					throw new IllegalStateException("Multiple COV's only supported with low/mid/high (and all 3 must be present).");
			} else {
				// equal weighting
				cellWeight *= 1d/(double)table.rowKeySet().size();
			}
			Map<LogicTreeBranch, SectProbGainResults[]> subBranchMap = cell.getValue();
			for (LogicTreeBranch branch : subBranchMap.keySet()) {
				SectProbGainResults[] vals = subBranchMap.get(branch);
				
				SectProbGainResults[] curVals = branchVals.get(branch);
				if (curVals == null) {
					curVals = new SectProbGainResults[vals.length];
					for (int i=0; i<curVals.length; i++)
						curVals[i] = new SectProbGainResults(0d, 0d, 0d, 0d, 0d, 0d);
					branchVals.put(branch, curVals);
				}
				for (int j=0; j<vals.length; j++) {
					curVals[j].recurrInt += vals[j].recurrInt*cellWeight;
					curVals[j].openInt += vals[j].openInt*cellWeight;
					curVals[j].pPois += vals[j].pPois*cellWeight;
					curVals[j].pTimeDep += vals[j].pTimeDep*cellWeight;
					curVals[j].pGain += vals[j].pGain*cellWeight;
					curVals[j].implGain += vals[j].implGain*cellWeight;
				}
//				if (branch.equals(LogicTreeBranch.DEFAULT) && parents)
//					System.out.println("REF BRANCH. Mojave S="+vals[230].pBPT+", running avg="+curVals[230].pBPT);
				if (debug_saf_nan_check && parents && Double.isNaN(vals[230].pTimeDep))
					mojaveNaNBranches.add(branch);
			}
			totCellWeight += cellWeight;
		}
		Preconditions.checkState((float)totCellWeight == 1f, "Total cell weight != 1: "+totCellWeight
				+", isWeightedMultiCOV="+isWeightedMultiCOV);
		
		if (debug_saf_nan_check && parents) {
			System.out.println("Branches:");
			for (LogicTreeBranch branch : mojaveNaNBranches)
				System.out.println("\t"+branch.buildFileName());
			System.out.println("End SAF NaN check");
			System.exit(0);
		}
		
		HashSet<FaultModels> fms = new HashSet<FaultModels>();
		for (LogicTreeBranch branch : branchVals.keySet()) {
			FaultModels fm = branch.getValue(FaultModels.class);
			if (!fms.contains(fm))
				fms.add(fm);
		}
		
		HashSet<FaultTraceComparable> tracesSet = new HashSet<FaultSysSolutionERF_Calc.FaultTraceComparable>();
		Map<FaultModels, List<LocationList>> fmTracesMap = Maps.newHashMap();
		Map<FaultModels, Map<FaultTraceComparable, Integer>> fmIndexMaps = Maps.newHashMap();
		Map<FaultTraceComparable, Double> meanYearsSinceMap = Maps.newHashMap();
		Map<FaultTraceComparable, Double> fractWithYearsSinceMap;
		if (parents)
			fractWithYearsSinceMap = Maps.newHashMap();
		else
			fractWithYearsSinceMap = null;
		long curMillis = new GregorianCalendar(FaultSystemSolutionERF.START_TIME_DEFAULT, 0, 0).getTimeInMillis();
		Table<FaultModels, String, Integer> fmSubSectIndexMap = HashBasedTable.create();
		for (FaultModels fm : fms) {
			Map<FaultTraceComparable, Integer> fmIndexMap = Maps.newHashMap();
			fmIndexMaps.put(fm, fmIndexMap);
			
			ArrayList<FaultSectionPrefData> subSects = new DeformationModelFetcher(
					fm, DeformationModels.GEOLOGIC,
					UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1d).getSubSectionList();
			LastEventData.populateSubSects(subSects, LastEventData.load());
			
			List<LocationList> fmTraces = Lists.newArrayList();
			for (int i=0; i<subSects.size(); i++) {
				fmTraces.add(subSects.get(i).getFaultTrace());
				fmSubSectIndexMap.put(fm, subSects.get(i).getName(), i);
			}
			fmTracesMap.put(fm, fmTraces);
			
			if (parents) {
				// average open intervals 
				Map<Integer, List<FaultSectionPrefData>> subSectsMap = Maps.newHashMap();
				for (FaultSectionPrefData sect : subSects) {
					List<FaultSectionPrefData> subSectsForParent = subSectsMap.get(sect.getParentSectionId());
					if (subSectsForParent == null) {
						subSectsForParent = Lists.newArrayList();
						subSectsMap.put(sect.getParentSectionId(), subSectsForParent);
					}
					subSectsForParent.add(sect);
				}
				
				List<FaultSectionPrefData> parentSects = fm.fetchFaultSections();
				Collections.sort(parentSects, new NamedComparator());
				for (int i = 0; i < parentSects.size(); i++) {
					FaultSectionPrefData sect = parentSects.get(i);
					FaultTraceComparable comp = new FaultTraceComparable(
							sect.getName(), sect.getSectionId(), sect.getFaultTrace());
					tracesSet.add(comp);
					fmIndexMap.put(comp, i);
					
					Integer parentID = sect.getSectionId();
					List<FaultSectionPrefData> sects = subSectsMap.get(parentID);
					List<Long> lastDates = Lists.newArrayList();
					for (FaultSectionPrefData subSect : sects)
						if (subSect.getDateOfLastEvent() > Long.MIN_VALUE)
							lastDates.add(subSect.getDateOfLastEvent());
					double oi;
					if (lastDates.isEmpty()) {
						oi = Double.NaN;
					} else {
						oi = 0d;
						for (long lastDate : lastDates) {
							long deltaMillis = curMillis - lastDate;
							double diffYears = YEARS_PER_MILLI*deltaMillis;
							oi += diffYears;
						}
						oi /= lastDates.size();
					}
					double fractWith = (double)lastDates.size()/(double)sects.size();
					meanYearsSinceMap.put(comp, oi);
					fractWithYearsSinceMap.put(comp, fractWith);
				}
			} else {
				for (int i = 0; i < subSects.size(); i++) {
					FaultSectionPrefData sect = subSects.get(i);
					FaultTraceComparable comp = new FaultTraceComparable(
							sect.getName(), sect.getParentSectionId(), sect.getFaultTrace());
					tracesSet.add(comp);
					fmIndexMap.put(comp, i);
					double oi;
					if (sect.getDateOfLastEvent() > Long.MIN_VALUE)
						oi = YEARS_PER_MILLI*(curMillis - sect.getDateOfLastEvent());
					else
						oi = Double.NaN;
					meanYearsSinceMap.put(comp, oi);
				}
			}
		}
		
		List<FaultTraceComparable> traceComps = Lists.newArrayList(tracesSet);
		Collections.sort(traceComps);
		List<LocationList> traces = Lists.newArrayList();
		double[] meanTimeDepVals = new double[traceComps.size()];
		Map<FaultModels, double[]> fmMeanTimeDepVals = Maps.newHashMap();
		double[] minTimeDepVals = new double[traceComps.size()];
		double[] maxTimeDepVals = new double[traceComps.size()];
		double[] gainU3Vals = new double[traceComps.size()];
		double[] gainU3U2Vals = new double[traceComps.size()];
		Map<MagDependentAperiodicityOptions, double[]> meanBPT_COVVals = null;
		Map<BPTAveragingTypeOptions, double[]> meanBPT_CalcVals = null;
		if (table.rowKeySet().size() > 1) {
			meanBPT_COVVals = Maps.newHashMap();
			for (MagDependentAperiodicityOptions theCOV : table.rowKeySet())
				meanBPT_COVVals.put(theCOV, new double[traceComps.size()]);
			if (isWeightedMultiCOV)
				meanBPT_COVVals.put(null, new double[traceComps.size()]);
		}
		if (table.columnKeySet().size() > 1) {
			meanBPT_CalcVals = Maps.newHashMap();
			for (BPTAveragingTypeOptions aveType : table.columnKeySet())
				meanBPT_CalcVals.put(aveType, new double[traceComps.size()]);
		}
		
		Map<FaultTraceComparable, Integer> traceToCombIndexMap = Maps.newHashMap();
		for (int i=0; i<traceComps.size(); i++) {
			FaultTraceComparable traceComp = traceComps.get(i);
			traces.add(traceComp.trace);
			traceToCombIndexMap.put(traceComp, i);
		}
		
		System.out.println(tracesSet.size()+" unique sects");
		
		BranchWeightProvider weightProv = new APrioriBranchWeightProvider();
		
		// aggregated CSV file
		CSVFile<String> csv = new CSVFile<String>(true);
		// TODO
		List<String> header = Lists.newArrayList("Name", "Fract With Years Since", "Average Years Since", "U3 Mean Time Dep Prob",
				"U3 Min", "U3 Max", "U3 Time Indep Prob", "U3 Time Dep/Indep");
		if (parents)
			header.addAll(Lists.newArrayList("U2 Mean Time Dep Prob", "U2 min", "U2 max", "U2 Time Indep Prob",
					"MeanU3 Time Dep/MeanU2 Time Dep"));
		csv.addLine(header);
		
		// this is what gets returned
		Map<Integer, List<Double>> meanMap = Maps.newHashMap();
		
		for (int i = 0; i < traceComps.size(); i++) {
			FaultTraceComparable trace = traceComps.get(i);
			List<Double> timeDepVals = Lists.newArrayList();
			Map<FaultModels, List<Double>> fmTimeDepValsMap = Maps.newHashMap();
			List<Double> poisVals = Lists.newArrayList();
			List<Double> gainVals = Lists.newArrayList();
			List<Double> weights = Lists.newArrayList();
			Map<FaultModels, List<Double>> fmTimeDepWeightsMap = Maps.newHashMap();
			Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions, List<Double>>
				bptOpsValsTable = null;
			if (meanBPT_COVVals != null || meanBPT_CalcVals != null) {
				bptOpsValsTable = HashBasedTable.create();
				for (Cell<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
						Map<LogicTreeBranch, SectProbGainResults[]>> cell : table.cellSet())
					bptOpsValsTable.put(cell.getRowKey(), cell.getColumnKey(), new ArrayList<Double>());
			}
			for (LogicTreeBranch branch : branchVals.keySet()) {
				FaultModels fm = branch.getValue(FaultModels.class);
				Integer index = fmIndexMaps.get(fm).get(trace);
				if (index == null)
					continue;
				List<Double> fmTimeDepVals = fmTimeDepValsMap.get(fm);
				List<Double> fmTimeDepWeights = fmTimeDepWeightsMap.get(fm);
				if (fmTimeDepVals == null) {
					fmTimeDepVals = Lists.newArrayList();
					fmTimeDepValsMap.put(fm, fmTimeDepVals);
					fmTimeDepWeights = Lists.newArrayList();
					fmTimeDepWeightsMap.put(fm, fmTimeDepWeights);
				}
				SectProbGainResults val = branchVals.get(branch)[index];
				timeDepVals.add(val.pTimeDep);
				fmTimeDepVals.add(val.pTimeDep);
				poisVals.add(val.pPois);
				gainVals.add(val.pGain);
				double weight = weightProv.getWeight(branch);
				weights.add(weight);
				fmTimeDepWeights.add(weight);
				if (bptOpsValsTable != null) {
					for (Cell<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
							Map<LogicTreeBranch, SectProbGainResults[]>> cell : table.cellSet())
						bptOpsValsTable.get(cell.getRowKey(), cell.getColumnKey()).add(
								table.get(cell.getRowKey(), cell.getColumnKey()).get(branch)[index].pTimeDep);
				}
			}
			double[] timeDepValsArray = Doubles.toArray(timeDepVals);
			for (FaultModels fm : fmTimeDepValsMap.keySet()) {
				if (parents)
					break;
				List<Double> fmTimeDepVals = fmTimeDepValsMap.get(fm);
				double[] fmTimeDepValsArray = Doubles.toArray(fmTimeDepVals);
				double[] fmWeightsArray = Doubles.toArray(fmTimeDepWeightsMap.get(fm));
				int subSectIndex = fmSubSectIndexMap.get(fm, trace.name);
				double[] fmMeanTimeDep = fmMeanTimeDepVals.get(fm);
				if (fmMeanTimeDep == null) {
					fmMeanTimeDep = new double[fmSubSectIndexMap.row(fm).size()];
					fmMeanTimeDepVals.put(fm, fmMeanTimeDep);
				}
				fmMeanTimeDep[subSectIndex] = FaultSystemSolutionFetcher.calcScaledAverage(
						fmTimeDepValsArray, fmWeightsArray);
			}
			double[] poisValsArray = Doubles.toArray(poisVals);
			double[] weightsArray = Doubles.toArray(weights);
			
			// now do min/max over all COV branches as well!
			double[] timeDepAllValsArray;
			if (table.rowKeySet().size() == 1) {
				timeDepAllValsArray = timeDepValsArray;
			} else {
				List<Double> timeDepAllVals = Lists.newArrayList();
				// loop over everything. weights not important as only used for min/max
				for (Cell<MagDependentAperiodicityOptions, BPTAveragingTypeOptions, Map<LogicTreeBranch,
						SectProbGainResults[]>> cell : table.cellSet()) {
					Map<LogicTreeBranch, SectProbGainResults[]> cellMap = cell.getValue();
					for (LogicTreeBranch branch : cellMap.keySet()) {
						FaultModels fm = branch.getValue(FaultModels.class);
						Integer index = fmIndexMaps.get(fm).get(trace);
						if (index == null)
							continue;
						timeDepAllVals.add(cellMap.get(branch)[index].pTimeDep);
					}
				}
				// add poisson values as well
				timeDepAllVals.addAll(poisVals);
				timeDepAllValsArray = Doubles.toArray(timeDepAllVals);
			}
			
//			double meanBPT = weightedAvgNonZero(bptVals, weights);
//			double minBPT = minNonZero(bptVals);
//			double maxBPT = maxNonZero(bptVals);
//			double meanPois = weightedAvgNonZero(poisVals, weights);
			double meanTimeDep = FaultSystemSolutionFetcher.calcScaledAverage(
					timeDepValsArray, weightsArray);
			double minTimeDep = StatUtils.min(timeDepAllValsArray);
			double maxTimeDep = StatUtils.max(timeDepAllValsArray);
			double meanPois = FaultSystemSolutionFetcher.calcScaledAverage(
					poisValsArray, weightsArray);
			double gainU3 = weightedAvgNonZero(gainVals, weights);
			
			double oi = meanYearsSinceMap.get(trace);
			double fractWith;
			if (fractWithYearsSinceMap == null) {
				if (Double.isNaN(oi))
					fractWith = 0d;
				else
					fractWith = 1d;
			} else {
				fractWith = fractWithYearsSinceMap.get(trace);
			}
			
			List<String> line = Lists.newArrayList(trace.name, fractWith+"", oi+"",
					meanTimeDep+"", minTimeDep+"", maxTimeDep+"", meanPois+"", gainU3+"");
			
			if (parents) {
				// 
				
				double meanU2 = Double.NaN;
				double minU2 = Double.NaN;
				double maxU2 = Double.NaN;
				double meanU2pois = Double.NaN;
				// this gets loaded each time, no need to clone
				ArrayList<IncrementalMagFreqDist> mfds =
						UCERF2_Section_TimeDepMFDsCalc.getMeanMinAndMaxMFD(trace.parentID, true, true);
				if (mfds != null) {
					// include aftershocks
					IncrementalMagFreqDist meanU2MFD = mfds.get(0);
					IncrementalMagFreqDist minU2MFD = mfds.get(1);
					IncrementalMagFreqDist maxU2MFD = mfds.get(2);
					IncrementalMagFreqDist meanU2IndepMFD = UCERF2_Section_MFDsCalc.getMeanMinAndMaxMFD(
							trace.parentID, true, true).get(0);
					meanU2MFD.scale(1.0/FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS);
					minU2MFD.scale(1.0/FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS);
					maxU2MFD.scale(1.0/FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS);
					meanU2IndepMFD.scale(1.0/FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS);
					// cumulative mfd so get the first above
					meanU2 = calcProbAboveMagFromMFD(meanU2MFD, minMag, duration);
					minU2 = calcProbAboveMagFromMFD(minU2MFD, minMag, duration);
					maxU2 = calcProbAboveMagFromMFD(maxU2MFD, minMag, duration);
					meanU2pois = calcProbAboveMagFromMFD(meanU2IndepMFD, minMag, duration);
				}
				
				double gainU3U2 = meanTimeDep / meanU2;
				gainU3U2Vals[i] = gainU3U2;
				
				line.addAll(Lists.newArrayList(meanU2+"", minU2+"", maxU2+"", meanU2pois+"", gainU3U2+""));
			}
			csv.addLine(line);
			
			meanTimeDepVals[i] = meanTimeDep;
			minTimeDepVals[i] = minTimeDep;
			maxTimeDepVals[i] = maxTimeDep;
			gainU3Vals[i] = gainU3;
			
			if (meanBPT_COVVals != null) {
				for (MagDependentAperiodicityOptions theCOV : meanBPT_COVVals.keySet()) {
					if (theCOV == null) // poisson
						continue;
					List<Double> avgVals = Lists.newArrayList();
					for (Cell<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,List<Double>> cell
							: bptOpsValsTable.cellSet()) {
						if (cell.getRowKey() != theCOV)
							continue;
						double[] bptCOV_ValsArray = Doubles.toArray(cell.getValue());
						avgVals.add(FaultSystemSolutionFetcher.calcScaledAverage(
							bptCOV_ValsArray, weightsArray));
					}
					Preconditions.checkState(!avgVals.isEmpty());
					meanBPT_COVVals.get(theCOV)[i] = StatUtils.mean(Doubles.toArray(avgVals));
				}
				// now add in Poisson
				if (isWeightedMultiCOV) {
					List<Double> branchPoisVals = Lists.newArrayList();
					for (LogicTreeBranch branch : branchVals.keySet()) {
						FaultModels fm = branch.getValue(FaultModels.class);
						Integer index = fmIndexMaps.get(fm).get(trace);
						if (index == null)
							continue;
						SectProbGainResults val = branchVals.get(branch)[index];
						branchPoisVals.add(val.pPois);
					}
					
					meanBPT_COVVals.get(null)[i] = FaultSystemSolutionFetcher.calcScaledAverage(
							Doubles.toArray(branchPoisVals), weightsArray);
				}
			}
			
			if (meanBPT_CalcVals != null) {
				for (BPTAveragingTypeOptions theAve : meanBPT_CalcVals.keySet()) {
					List<Double> avgVals = Lists.newArrayList();
					List<Double> cellWeights = Lists.newArrayList();
					for (Cell<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,List<Double>> cell
							: bptOpsValsTable.cellSet()) {
						if (cell.getColumnKey() != theAve)
							continue;
						double[] bptCOV_ValsArray = Doubles.toArray(cell.getValue());
						avgVals.add(FaultSystemSolutionFetcher.calcScaledAverage(
							bptCOV_ValsArray, weightsArray));
						if (isWeightedMultiCOV)
							cellWeights.add(FaultSystemSolutionERF.getWeightForCOV(cell.getRowKey()));
						else
							cellWeights.add(1d);
					}
					if (isWeightedMultiCOV) {
						// add in poisson now
						avgVals.add(meanPois);
						cellWeights.add(FaultSystemSolutionERF.getWeightForCOV(null));
						Preconditions.checkState(avgVals.size() == 4);
						Preconditions.checkState(cellWeights.size() == 4);
					}
					
					double mean = FaultSystemSolutionFetcher.calcScaledAverage(
							Doubles.toArray(avgVals), Doubles.toArray(cellWeights));
					Preconditions.checkState(!avgVals.isEmpty());
					meanBPT_CalcVals.get(theAve)[i] = mean;
				}
			}
			
			List<Double> parentVals = meanMap.get(trace.parentID);
			if (parentVals == null) {
				parentVals = Lists.newArrayList();
				meanMap.put(trace.parentID, parentVals);
			}
			parentVals.add(meanTimeDep);
		}
		
		File csvFile;
		if (parents)
			csvFile = new File(outputDir, "branch_aggregated_parents.csv");
		else
			csvFile = new File(outputDir, "branch_aggregated_subs.csv");
		csv.writeToFile(csvFile);
		
		CPT ratioCPT = getScaledLinearRatioCPT(0.02d);
		Region region = new CaliforniaRegions.RELM_COLLECTION();
		
		if (parents) {
			FaultBasedMapGen.makeFaultPlot(ratioCPT, traces, gainU3U2Vals, region,
					outputDir, "gain_u3_u2", false, true, "UCERF3/UCERF2 Time Dep Ratio");
			return meanMap;
		}
		
		// now do branch choice ratio maps
		File comparePlotsDir = new File(outputDir, "branch_ratios");
		if (!comparePlotsDir.exists())
			comparePlotsDir.mkdir();
		
		BranchSensitivityHistogram branchSensHist = new BranchSensitivityHistogram("Ratio");
		
		for (Class<? extends LogicTreeBranchNode<?>> clazz : LogicTreeBranch.getLogicTreeNodeClasses()) {
			if (clazz.equals(InversionModels.class) || clazz.equals(MomentRateFixes.class))
				continue;
			String className = ClassUtils.getClassNameWithoutPackage(clazz);
			LogicTreeBranchNode<?>[] choices = clazz.getEnumConstants();
			// for std dev
			List<Double> allVals = Lists.newArrayList();
			for (LogicTreeBranchNode<?> choice : choices) {
				if (choice.getRelativeWeight(InversionModels.CHAR_CONSTRAINED) <= 0)
					continue;
				double[] choiceVals = new double[meanTimeDepVals.length];
				double[] weightTots = new double[meanTimeDepVals.length];
				for (LogicTreeBranch branch : branchVals.keySet()) {
					if (branch.getValueUnchecked(clazz) != choice)
						continue;
					FaultModels fm = branch.getValue(FaultModels.class);
					double weight = weightProv.getWeight(branch);
					SectProbGainResults[] vals = branchVals.get(branch);
					for (int i = 0; i < traceComps.size(); i++) {
						FaultTraceComparable trace = traceComps.get(i);
						Integer index = fmIndexMaps.get(fm).get(trace);
						if (index == null)
							continue;
						double val = vals[index].pTimeDep;
						if (Double.isNaN(val))
							continue;
						choiceVals[i] += val*weight;
						weightTots[i] += weight;
					}
				}
				// scale for total weight
				for (int i=0; i<choiceVals.length; i++)
					choiceVals[i] /= weightTots[i];
				
				double[] ratios = new double[choiceVals.length];
				for (int i=0; i<ratios.length; i++)
					ratios[i] = choiceVals[i] / meanTimeDepVals[i];
				String prefix = className+"_"+choice.encodeChoiceString();
				String plotLabel = choice.encodeChoiceString()+"/Mean";
				
				double choiceWeight = choice.getRelativeWeight(InversionModels.CHAR_CONSTRAINED);
				
				FaultBasedMapGen.makeFaultPlot(ratioCPT, traces, ratios, region,
						comparePlotsDir, prefix, false, true, plotLabel);
				
				// now histograms
				for (double ratio : ratios)
					if (Doubles.isFinite(ratio))
						branchSensHist.addValue(className, choice.getShortName(), ratio, choiceWeight);
				allVals.addAll(getNonNanInfinites(ratios));
			}
			// write hists
			double stdDev = Math.sqrt(StatUtils.variance(Doubles.toArray(allVals)));
			System.out.println(className+" orig sigma: "+stdDev);
//			writeRatioHists(comparePlotsDir, hists, className, stdDev);
		}
		// now do it for COV values
		if (meanBPT_COVVals != null) {
			String className = "MagDepAperiodicity";
			// for std dev
			List<Double> allVals = Lists.newArrayList();
			for (MagDependentAperiodicityOptions theCOV : meanBPT_COVVals.keySet()) {
				String name;
				if (theCOV == null)
					name = "POISSON";
				else
					name = theCOV.name();
				double[] choiceVals = meanBPT_COVVals.get(theCOV);
				double[] ratios = new double[choiceVals.length];
				for (int i=0; i<ratios.length; i++)
					ratios[i] = choiceVals[i] / meanTimeDepVals[i];
				String prefix = "MagDepAperiodicity_"+name;
				String plotLabel = name+"/Mean";
				
				FaultBasedMapGen.makeFaultPlot(ratioCPT, traces, ratios, region,
						comparePlotsDir, prefix, false, true, plotLabel);
				
//				hists.add(generateRatioHistogram(ratios, theCOV.name()));
				double weight = FaultSystemSolutionERF.getWeightForCOV(theCOV);
				for (double ratio : ratios)
					if (Doubles.isFinite(ratio))
						branchSensHist.addValue(className, name, ratio, weight);
				allVals.addAll(getNonNanInfinites(ratios));
			}
			// write hists
			double stdDev = Math.sqrt(StatUtils.variance(Doubles.toArray(allVals)));
			System.out.println(className+" orig sigma: "+stdDev);
//			writeRatioHists(comparePlotsDir, hists, className, stdDev);
		}
		
		// now do it for Ave Type values
		if (meanBPT_CalcVals != null) {
			String className = "BPTAveType";
			
			double maxDiff = 0;
			List<double[]> diffVals = Lists.newArrayList();
			List<String> diffLabels = Lists.newArrayList();
			List<String> diffPrefixes = Lists.newArrayList();
			
			// for std dev
			List<Double> allVals = Lists.newArrayList();
			for (BPTAveragingTypeOptions theAve : meanBPT_CalcVals.keySet()) {
				double[] choiceVals = meanBPT_CalcVals.get(theAve);
				double[] ratios = new double[choiceVals.length];
				for (int i=0; i<ratios.length; i++)
					ratios[i] = choiceVals[i] / meanTimeDepVals[i];
				String prefix = "BPTAveType_"+theAve.getFileSafeLabel();
				String plotLabel = theAve.getCompactLabel()+"/Mean";
				
				FaultBasedMapGen.makeFaultPlot(ratioCPT, traces, ratios, region,
						comparePlotsDir, prefix, false, true, plotLabel);

				double[] diffs = new double[choiceVals.length];
				for (int i=0; i<diffs.length; i++)
					diffs[i] = choiceVals[i] - meanTimeDepVals[i];
				maxDiff = Math.max(maxDiff, Math.max(Math.abs(StatUtils.max(diffs)), Math.abs(StatUtils.min(diffs))));
				diffVals.add(diffs);
				diffLabels.add(theAve.getCompactLabel()+" - Mean");
				diffPrefixes.add("BPTAveType_diff_"+theAve.getFileSafeLabel());
				
				CSVFile<String> diffCSV = new CSVFile<String>(true);
				diffCSV.addLine("Subsection Index", "Ave Type Val", "Mean Val", "Diff", "Ratio");
				for (int i=0; i<diffs.length; i++)
					diffCSV.addLine(i+"", choiceVals[i]+"", meanTimeDepVals[i]+"", diffs[i]+"", ratios[i]+"");
				diffCSV.writeToFile(new File(comparePlotsDir, prefix+".csv"));

				for (double ratio : ratios)
					if (Doubles.isFinite(ratio))
						branchSensHist.addValue(className, theAve.getFileSafeLabel(), ratio, 1d); // equal weighting
//				hists.add(generateRatioHistogram(ratios, theAve.getFileSafeLabel()));
				allVals.addAll(getNonNanInfinites(ratios));
			}
			// write hists
			double stdDev = Math.sqrt(StatUtils.variance(Doubles.toArray(allVals)));
			System.out.println(className+" orig sigma: "+stdDev);
//			writeRatioHists(comparePlotsDir, hists, className, stdDev);
			
			// write diff plots
			
			// round up to nearest 0.05
			maxDiff = Math.ceil(maxDiff * 100d)/100d;
			CPT diffCPT = ratioCPT.rescale(-maxDiff, maxDiff);
			for (int i=0; i<diffVals.size(); i++)
				FaultBasedMapGen.makeFaultPlot(diffCPT, traces, diffVals.get(i), region,
						comparePlotsDir, diffPrefixes.get(i), false, true, diffLabels.get(i));
		}
		
//		BranchSensitivityHistogram branchSensHist = new BranchSensitivityHistogram("Ratio", 0d, 21, 0.1);
//		String covCategoryName = "MagDepAperiodicity";
//		String aveCategoryName = "BPTAveType";
//		for (Cell<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
//				Map<LogicTreeBranch, SectProbGainResults[]>> cell : table.cellSet()) {
//			String covChoiceName = cell.getRowKey().name();
//			String aveChoiceName = cell.getColumnKey().getFileSafeLabel();
//			
//			Map<LogicTreeBranch, SectProbGainResults[]> valMap = cell.getValue();
//			
//			for (LogicTreeBranch branch : valMap.keySet()) {
//				SectProbGainResults[] vals = valMap.get(branch);
//				for (int i=0; i<vals.length; i++) {
//					double ratio = vals[i].pBPT / meanBPTVals[i];
//					// TODO COV weight?
//					double weight = weightProv.getWeight(branch);
//					branchSensHist.addValues(branch, ratio, weight, covCategoryName, covChoiceName, aveCategoryName, aveChoiceName);
//				}
//			}
//		}
		
		// write the ratio hists
		Map<String, PlotSpec> histPlots = branchSensHist.getStackedHistPlots(false, 0d, 21, 0.1);
		for (String categoryName : histPlots.keySet()) {
			PlotSpec spec = histPlots.get(categoryName);
			
			double mean = branchSensHist.calcMean(categoryName);
			double stdDev = branchSensHist.calcStdDev(categoryName);
			
			System.out.println(categoryName+": mean="+mean+", sigma="+stdDev);
			
			XYTextAnnotation ann = new XYTextAnnotation("StdDev="+new DecimalFormat("0.00").format(stdDev), 0.05, 0.95);
			ann.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
			ann.setTextAnchor(TextAnchor.TOP_LEFT);
			spec.setPlotAnnotations(Lists.newArrayList(ann));
			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			CommandLineInversionRunner.setFontSizes(gp);
			
			gp.drawGraphPanel(spec, false, false, new Range(0d, 2d), new Range(0d, 1d));

			File file = new File(comparePlotsDir, categoryName+"_hists");
			gp.getChartPanel().setSize(1000, 600);
			gp.saveAsPDF(file.getAbsolutePath() + ".pdf");
			gp.saveAsPNG(file.getAbsolutePath() + ".png");
			file = new File(file.getAbsolutePath()+"_small");
			gp.getChartPanel().setSize(500, 400);
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
			gp.saveAsPNG(file.getAbsolutePath()+".png");
		}
		
		try {
			combineBranchSensHists(comparePlotsDir);
		} catch (com.lowagie.text.DocumentException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		// now do min/mean/max prob maps
		CPT logProbCPT = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-4d, 0d);
		
		FaultBasedMapGen.makeFaultPlot(logProbCPT, traces, FaultBasedMapGen.log10(meanTimeDepVals), region,
				outputDir, "mean_time_dep_prob", false, true, "UCERF3 Mean Time Dep Prob");
		FaultBasedMapGen.makeFaultKML(logProbCPT, traces, FaultBasedMapGen.log10(meanTimeDepVals), outputDir,
				"mean_time_dep_prob", false, 40, 4, "UCERF3 Mean Time Dep Prob");
		for (FaultModels fm : fmMeanTimeDepVals.keySet()) {
			if (parents)
				break;
			double[] fmVals = fmMeanTimeDepVals.get(fm);
			List<LocationList> fmTraces = fmTracesMap.get(fm);
			
			FaultBasedMapGen.makeFaultPlot(logProbCPT, fmTraces, FaultBasedMapGen.log10(fmVals), region,
					outputDir, fm.name()+"_mean_time_dep_prob", false, true, fm.name()+" UCERF3 Mean Time Dep Prob");
			FaultBasedMapGen.makeFaultKML(logProbCPT, fmTraces, FaultBasedMapGen.log10(fmVals), outputDir,
					fm.name()+"_mean_time_dep_prob", false, 40, 4, fm.name()+" UCERF3 Mean Time Dep Prob");
		}
		FaultBasedMapGen.makeFaultPlot(logProbCPT, traces, FaultBasedMapGen.log10(minTimeDepVals), region,
				outputDir, "min_time_dep_prob", false, true, "UCERF3 Min Time Dep Prob");
		FaultBasedMapGen.makeFaultPlot(logProbCPT, traces, FaultBasedMapGen.log10(maxTimeDepVals), region,
				outputDir, "max_time_dep_prob", false, true, "UCERF3 Max Time Dep Prob");
		FaultBasedMapGen.makeFaultPlot(ratioCPT, traces, gainU3Vals, region,
				outputDir, "gain_u3", false, true, "UCERF3 Mean BPT/Poisson Prob Gain");
		
		return meanMap;
	}
	
	public static void combineBranchSensHists(File dir) throws IOException, com.lowagie.text.DocumentException {
		List<File> pdfFiles = Lists.newArrayList();
		
		List<String> classNames = Lists.newArrayList();
		
		for (Class<? extends LogicTreeBranchNode<?>> clazz : LogicTreeBranch.getLogicTreeNodeClasses()) {
			if (clazz.equals(InversionModels.class) || clazz.equals(MomentRateFixes.class))
				continue;
			String name = ClassUtils.getClassNameWithoutPackage(clazz);
			classNames.add(name);
		}
		
		Collections.sort(classNames);
		
		for (String name : classNames) {
			File pdfFile = new File(dir, name+"_hists_small.pdf");
			Preconditions.checkState(pdfFile.exists(), "File doesn't exist: "+pdfFile.getAbsolutePath());
			pdfFiles.add(pdfFile);
		}
		
		// now try cov
		File covFile = new File(dir, "MagDepAperiodicity_hists_small.pdf");
		if (covFile.exists())
			pdfFiles.add(covFile);
		
		// now try bpt ave
		File bptFile = new File(dir, "BPTAveType_hists_small.pdf");
		if (bptFile.exists())
			pdfFiles.add(bptFile);
		
		File outputFile = new File(dir, "histograms_combined.pdf");
		combineBranchSensHists(pdfFiles, outputFile);
	}
	
	public static void combineBranchSensHists(List<File> pdfFiles, File outputFile)
			throws IOException, com.lowagie.text.DocumentException {
		
		int cols = 3;
		double scale = 0.4;
		boolean rotate = false;
		double xMarginBuffFract = 0.02;
		double yMarginBuffFract = 0.05;
		double xPosMult = 0.96;
		double yPosMult = 0.95;
		
		TestPDFCombine.combine(pdfFiles, outputFile, cols, scale, rotate,
				xMarginBuffFract, yMarginBuffFract, xPosMult, yPosMult);
	}
	
	private static List<Double> getNonNanInfinites(double[] vals) {
		List<Double> nonNanRatios = Lists.newArrayList();
		for (double ratio : vals) {
			if (!Double.isNaN(ratio) && !Double.isInfinite(ratio)) {
				nonNanRatios.add(ratio);
			}
		}
		return nonNanRatios;
	}
	
//	private static HistogramFunction generateRatioHistogram(double[] ratios, String choiceName) {
//		HistogramFunction ratioHist = new HistogramFunction(0d, 21, 0.1);
//		for (double ratio : getNonNanInfinites(ratios))
//			ratioHist.add(ratioHist.getClosestXIndex(ratio), 1d);
//		ratioHist.setName(choiceName);
//		return ratioHist;
//	}
	
//	private static PlotSpec generateRatioHistogram(double[] ratios, String className, String choiceName) {
//		HistogramFunction ratioHist = new HistogramFunction(0d, 21, 0.1);
//		List<Double> nonNanRatios = Lists.newArrayList();
//		for (double ratio : getNonNanInfinites(ratios))
//			ratioHist.add(ratioHist.getClosestXIndex(ratio), 1d);
//		
//		ratioHist.normalizeBySumOfY_Vals();
//		
//		List<PlotElement> funcs = Lists.newArrayList();
//		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
//		
//		funcs.add(ratioHist);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
//		XY_DataSet centerLine = new DefaultXY_DataSet();
//		centerLine.set(1d, 0d);
//		centerLine.set(1d, 1d);
//		double mean = StatUtils.mean(Doubles.toArray(nonNanRatios));
//		XY_DataSet meanLine = new DefaultXY_DataSet();
//		meanLine.set(mean, 0d);
//		meanLine.set(mean, 1d);
//		funcs.add(centerLine);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
//		funcs.add(meanLine);
//		if (mean > 1)
//			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
//		else
//			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
//		
//		double stdDev = Math.sqrt(StatUtils.variance(Doubles.toArray(nonNanRatios)));
//		PlotSpec spec = new PlotSpec(funcs, chars, className, "Ratio", "Density");
//		XYTextAnnotation ann = new XYTextAnnotation(choiceName+"\nmean="+(float)mean+"\nstdDev="+(float)stdDev, 0.05, 0.95);
//		ann.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
//		ann.setTextAnchor(TextAnchor.TOP_LEFT);
//		spec.setPlotAnnotations(Lists.newArrayList(ann));
//		
//		return spec;
//	}
	
//	private static void writeRatioHists(File comparePlotsDir, List<HistogramFunction> hists,
//			String className, double stdDev) throws IOException {
//		HeadlessGraphPanel gp = new HeadlessGraphPanel();
//		CommandLineInversionRunner.setFontSizes(gp);
//		
//		List<HistogramFunction> stackedHists = HistogramFunction.getStackedHists(hists, true);
//		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
////		List<Color> colors = GraphWindow.generateDefaultColors();
//		List<Color> colors = Lists.newArrayList(
//				Color.BLACK, Color.BLUE, Color.RED, Color.GREEN, Color.CYAN, Color.ORANGE);
//		for (int i=0; i<stackedHists.size(); i++)
//			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, colors.get(i)));
//		
//		PlotSpec spec = new PlotSpec(stackedHists, chars, className, "Ratio", "Density");
//		spec.setLegendVisible(true);
//		spec.setLegendLocation(RectangleEdge.BOTTOM);
//		
//		XYTextAnnotation ann = new XYTextAnnotation("StdDev="+new DecimalFormat("0.00").format(stdDev), 0.05, 0.95);
//		ann.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
//		ann.setTextAnchor(TextAnchor.TOP_LEFT);
//		spec.setPlotAnnotations(Lists.newArrayList(ann));
//		
//		gp.drawGraphPanel(spec, false, false, new Range(0d, 2d), new Range(0d, 1d));
//
//		File file = new File(comparePlotsDir, className+"_hists");
//		gp.getCartPanel().setSize(1000, 600);
//		gp.saveAsPDF(file.getAbsolutePath() + ".pdf");
//		gp.saveAsPNG(file.getAbsolutePath() + ".png");
//		file = new File(file.getAbsolutePath()+"_small");
//		gp.getCartPanel().setSize(500, 400);
//		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
//		gp.saveAsPNG(file.getAbsolutePath()+".png");
//	}
	
	private static void writeBranchAggregatedFaultResults(
			Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
			Map<LogicTreeBranch, SectProbGainResults[]>> table, File outputDir,
			double minMag, double duration) throws IOException {
		
		Map<LogicTreeBranch, SectProbGainResults[]> branchVals;
		branchVals = Maps.newHashMap();
		double avgTypeWeight = 1d/(double)table.columnKeySet().size();
		double totCellWeight = 0d; // check that it all adds up to 1
		
		// if true, average along with poisson according to weights
		boolean isWeightedMultiCOV = table.rowKeySet().size() == 3
					&& table.rowKeySet().contains(MagDependentAperiodicityOptions.LOW_VALUES)
					&& table.rowKeySet().contains(MagDependentAperiodicityOptions.MID_VALUES)
					&& table.rowKeySet().contains(MagDependentAperiodicityOptions.HIGH_VALUES);
		Map<LogicTreeBranch, SectProbGainResults[]> poisMap;
		if (isWeightedMultiCOV) {
			// calculate the Poisson branch
			System.out.println("Multiple/Weighted COV!");
			// bpt is the same among all, so it doesn't matter which one we get
			poisMap = calcPoissonValues(table);
			// now add it in
			double pw = FaultSystemSolutionERF.PREF_BLEND_POISSON_WEIGHT;
			for (LogicTreeBranch branch : poisMap.keySet()) {
				SectProbGainResults[] poisResults = poisMap.get(branch);
				SectProbGainResults[] scaledResults = new SectProbGainResults[poisResults.length];
				for (int i=0; i<poisResults.length; i++) {
					SectProbGainResults pois = poisResults[i];
					scaledResults[i] = new SectProbGainResults(pw*pois.recurrInt, pw*pois.openInt,
							pw*pois.pPois, pw*pois.pTimeDep, pw*pois.pGain, pw*pois.implGain);
				}

				// now add it in
				branchVals.put(branch, scaledResults);
			}
			// add in the weighting from poisson that we just added in
			totCellWeight += pw;
		}
		
		for (Cell<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
				Map<LogicTreeBranch, SectProbGainResults[]>> cell : table.cellSet()) {
			double cellWeight = avgTypeWeight;
			if (isWeightedMultiCOV) {
				cellWeight *= FaultSystemSolutionERF.getWeightForCOV(cell.getRowKey());
				if (cellWeight == 0)
					throw new IllegalStateException("Multiple COV's only supported with low/mid/high (and all 3 must be present).");
			} else {
				// equal weighting
				cellWeight *= 1d/(double)table.rowKeySet().size();
			}
			totCellWeight += cellWeight;
			Map<LogicTreeBranch, SectProbGainResults[]> subBranchMap = cell.getValue();
			for (LogicTreeBranch branch : subBranchMap.keySet()) {
				SectProbGainResults[] vals = subBranchMap.get(branch);
				
				SectProbGainResults[] curVals = branchVals.get(branch);
				if (curVals == null) {
					curVals = new SectProbGainResults[vals.length];
					for (int i=0; i<curVals.length; i++)
						curVals[i] = new SectProbGainResults(0d, 0d, 0d, 0d, 0d, 0d);
					branchVals.put(branch, curVals);
				}
				for (int j=0; j<vals.length; j++) {
					curVals[j].recurrInt += vals[j].recurrInt*cellWeight;
					curVals[j].openInt += vals[j].openInt*cellWeight;
					curVals[j].pPois += vals[j].pPois*cellWeight;
					curVals[j].pTimeDep += vals[j].pTimeDep*cellWeight;
					curVals[j].pGain += vals[j].pGain*cellWeight;
					curVals[j].implGain += vals[j].implGain*cellWeight;
				}
			}
		}
		Preconditions.checkState((float)totCellWeight == 1f, "Total cell weight != 1: "+totCellWeight
				+", isWeightedMultiCOV="+isWeightedMultiCOV);
		
		Map<String, DiscretizedFunc[]> u2DepMPDs = null;
		Map<String, DiscretizedFunc[]> u2IndepMPDs = null;
		int u2FuncIndex = -1;
		if ((float)duration == 30f) {
			u2DepMPDs = loadUCERF2MainFaultMPDs(true, true);
			u2IndepMPDs = loadUCERF2MainFaultMPDs(true, false);
			// now make sure the mag is supported
			u2FuncIndex = u2DepMPDs.values().iterator().next()[0].getXIndex(minMag);
			if (u2FuncIndex < 0) {
				// mag not supported
				u2DepMPDs = null;
				u2IndepMPDs = null;
			}
		}
		
		// aggregated CSV file
		CSVFile<String> csv = new CSVFile<String>(true);
		List<String> header = Lists.newArrayList("Name", "U3 Mean Time Dep", "U3 Min", "U3 Max",
				"U3 Time Indep", "U3 Time Dep/Time Indep");
		if (u2DepMPDs != null) {
			// we have UCERF2 values
			header.addAll(Lists.newArrayList("U2 Mean Time Dep", "U2 Min", "U2 Max",
					"U2 Mean Time Indep", "U2 Time Dep/Time Indep", "U3 Time Dep / U2 Time Dep"));
		}
		csv.addLine(header);
		
		List<String> faultNames = Lists.newArrayList(FaultModels.parseNamedFaultsAltFile(
				UCERF3_DataUtils.getReader("FaultModels", "MainFaultsForTimeDepComparison.txt")).keySet());
		Collections.sort(faultNames);
		
		BranchWeightProvider weightProv = new APrioriBranchWeightProvider();
		
		for (int i = 0; i < faultNames.size(); i++) {
			String name = faultNames.get(i);
			List<Double> bptVals = Lists.newArrayList();
			List<Double> poisVals = Lists.newArrayList();
			List<Double> gainVals = Lists.newArrayList();
			List<Double> weights = Lists.newArrayList();
			for (LogicTreeBranch branch : branchVals.keySet()) {
				FaultModels fm = branch.getValue(FaultModels.class);
				if ((name.contains("FM3.1") && fm == FaultModels.FM3_2)
						|| (name.contains("FM3.2") && fm == FaultModels.FM3_1))
					continue;
				SectProbGainResults val = branchVals.get(branch)[i];
				if (Double.isNaN(val.pTimeDep))
					continue;
				bptVals.add(val.pTimeDep);
				poisVals.add(val.pPois);
				gainVals.add(val.pGain);
				weights.add(weightProv.getWeight(branch));
			}
			double[] bptValsArray = Doubles.toArray(bptVals);
			double[] poisValsArray = Doubles.toArray(poisVals);
			double[] weightsArray = Doubles.toArray(weights);
			
			// now do min/max over all COV branches as well!
			double[] timeDepAllValsArray;
			if (table.rowKeySet().size() == 1) {
				timeDepAllValsArray = bptValsArray;
			} else {
				List<Double> timeDepAllVals = Lists.newArrayList();
				// loop over everything. weights not important as only used for min/max
				for (Cell<MagDependentAperiodicityOptions, BPTAveragingTypeOptions, Map<LogicTreeBranch,
						SectProbGainResults[]>> cell : table.cellSet()) {
					Map<LogicTreeBranch, SectProbGainResults[]> cellMap = cell.getValue();
					for (LogicTreeBranch branch : cellMap.keySet()) {
						FaultModels fm = branch.getValue(FaultModels.class);
						if ((name.contains("FM3.1") && fm == FaultModels.FM3_2)
								|| (name.contains("FM3.2") && fm == FaultModels.FM3_1))
							continue;
						timeDepAllVals.add(cellMap.get(branch)[i].pTimeDep);
					}
				}
				// add poisson values as well
				timeDepAllVals.addAll(poisVals);
				timeDepAllValsArray = Doubles.toArray(timeDepAllVals);
			}
			
//			double meanBPT = weightedAvgNonZero(bptVals, weights);
//			double minBPT = minNonZero(bptVals);
//			double maxBPT = maxNonZero(bptVals);
//			double meanPois = weightedAvgNonZero(poisVals, weights);
			double meanBPT = FaultSystemSolutionFetcher.calcScaledAverage(
					bptValsArray, weightsArray);
			double minBPT = StatUtils.min(timeDepAllValsArray);
			double maxBPT = StatUtils.max(timeDepAllValsArray);
			double meanPois = FaultSystemSolutionFetcher.calcScaledAverage(
					poisValsArray, weightsArray);
			double gainU3 = weightedAvgNonZero(gainVals, weights);
			
			List<String> line = Lists.newArrayList(name, meanBPT+"", minBPT+"", maxBPT+"", meanPois+"", gainU3+"");
			
			if (u2DepMPDs != null) {
				DiscretizedFunc[] u2Funcs = u2DepMPDs.get(name); // organized as min, max, mean
				DiscretizedFunc[] u2IndepFuncs = u2IndepMPDs.get(name); // organized as min, max, mean
				double minU2 = u2Funcs[0].getY(u2FuncIndex);
				double maxU2 = u2Funcs[1].getY(u2FuncIndex);
				double meanU2 = u2Funcs[2].getY(u2FuncIndex);
				double meanIndepU2 = u2IndepFuncs[2].getY(u2FuncIndex);
			
				// "U2 Mean Time Dep", "U2 Min", "U2 Max"
				// "U2 Mean Time Indep", "U2 Time Dep/Time Indep", "U3 Time Dep / U2 Time Dep"
				line.add(meanU2+"");
				line.add(minU2+"");
				line.add(maxU2+"");
				line.add(meanIndepU2+"");
				line.add((meanU2/meanIndepU2)+"");
				line.add((meanBPT/meanU2)+"");
			}
			
			csv.addLine(line);
		}
		
		File csvFile = new File(outputDir, "branch_aggregated_main_faults.csv");
		csv.writeToFile(csvFile);
	}
	
	private static double calcProbAboveMagFromMFD(EvenlyDiscretizedFunc cmlMFD, double minMag, double duration) {
		cmlMFD = calcProbsFromSummedMFD(cmlMFD, duration);
		Preconditions.checkState(minMag <= cmlMFD.getMaxX());
		return cmlMFD.getClosestYtoX(minMag);
//		for (Point2D pt : calcProbsFromSummedMFD(cmlMFD, duration))
//			if (pt.getX() >= minMag)
//				return pt.getY();
//		return 0;
	}
	
	private static double weightedAvgNonZero(List<Double> vals, List<Double> weights) {
		double runningTot = 0;
		double totWeight = 0d;
		
		for (int i=0; i<vals.size(); i++) {
			double val = vals.get(i);
			if (val > 0) {
				// this will fail on NaNs which is also desired
				double weight = weights.get(i);
				runningTot += val*weight;
				totWeight += weight;
			}
		}
		if (runningTot == 0)
			return Double.NaN;
		
		runningTot /= totWeight;
		
		return runningTot;
	}
	
	private static double minNonZero(List<Double> vals) {
		double min = Double.POSITIVE_INFINITY;
		for (double val : vals)
			if (val > 0 && val < min)
				min = val;
		if (Double.isInfinite(min))
			return Double.NaN;
		return min;
	}
	
	private static double maxNonZero(List<Double> vals) {
		double max = 0;
		for (double val : vals)
			if (val > 0 && val > max)
				max = val;
		if (max == 0)
			return Double.NaN;
		return max;
	}
	
	private static class FaultTraceComparable implements Comparable<FaultTraceComparable> {
		private String name;
		private int parentID;
		private FaultTrace trace;
		
		public FaultTraceComparable(String name, int parentID, FaultTrace trace) {
			this.name = name;
			this.parentID = parentID;
			this.trace = trace;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + parentID;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			FaultTraceComparable other = (FaultTraceComparable) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (parentID != other.parentID)
				return false;
			return true;
		}

		@Override
		public int compareTo(FaultTraceComparable o) {
			return name.compareTo(o.name);
		}
		
	}
	
	public static class SectProbGainResults {
		double recurrInt, openInt, pPois, pTimeDep, pGain, implGain;

		public SectProbGainResults(double recurrInt, double openInt,
				double pPois, double pBPT, double pGain, double implGain) {
			super();
			this.recurrInt = recurrInt;
			this.openInt = openInt;
			this.pPois = pPois;
			this.pTimeDep = pBPT;
			this.pGain = pGain;
			this.implGain = implGain;
		}
	}

	private static List<Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
	Map<LogicTreeBranch, SectProbGainResults[]>>> loadBranchCSVVals(
			File file, int[] magRangeIndexes, boolean parents) throws ZipException, IOException {
		return loadBranchCSVVals(new File[] {file}, magRangeIndexes, parents);
	}

	private static List<Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
	Map<LogicTreeBranch, SectProbGainResults[]>>> loadBranchCSVVals(
			File[] files, int[] magRangeIndexes, boolean parents) throws ZipException, IOException {
		// first '2' is for subsection indexes
		// the other '1' is for the blank col at the start of each mag range
		int[] colStarts = new int[magRangeIndexes.length];
		List<Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
		Map<LogicTreeBranch, SectProbGainResults[]>>> maps = Lists.newArrayList();
		for (int i=0; i<magRangeIndexes.length; i++) {
			colStarts[i] = 2 + magRangeIndexes[i]*7 + 1;
			Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
				Map<LogicTreeBranch, SectProbGainResults[]>> table = HashBasedTable.create();
			maps.add(table);
		}
		
		for (File file : files) {
			ZipFile zip = new ZipFile(file);
			
			for (ZipEntry entry : Collections.list(zip.entries())) {
				try {
					String name = entry.getName().trim();
//				System.out.println("File: "+name);
					if (parents && !name.endsWith("parents.csv"))
						continue;
					if (!parents && !name.endsWith("subs.csv"))
						continue;
					
					// remove first directory name
					int covEnd = name.lastIndexOf("/");
					String namePrefix = name.substring(0, covEnd);
					name = name.substring(covEnd+1);
					// find the cov value
					MagDependentAperiodicityOptions cov = null;
					for (MagDependentAperiodicityOptions testCOV : MagDependentAperiodicityOptions.values()) {
						if (namePrefix.contains(testCOV.name())) {
							cov = testCOV;
							break;
						}
					}
					Preconditions.checkNotNull(cov);
					BPTAveragingTypeOptions aveType = null;
					for (BPTAveragingTypeOptions testType : BPTAveragingTypeOptions.values()) {
						String dirName = MPJ_ERF_ProbGainCalcScriptWriter.getAveDirName(testType);
						if (namePrefix.contains(dirName) || file.getName().startsWith(dirName)) {
							aveType = testType;
							break;
						}
					}
					Preconditions.checkNotNull(aveType);
					LogicTreeBranch branch = LogicTreeBranch.fromFileName(name);
					Preconditions.checkNotNull(branch);
//				System.out.println("Loading "+branch.buildFileName()+", cov="+cov.name());
					CSVFile<String> csv = CSVFile.readStream(zip.getInputStream(entry), true);
					for (int i=0; i<magRangeIndexes.length; i++) {
						int colStart = colStarts[i];
						Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
							Map<LogicTreeBranch, SectProbGainResults[]>> table = maps.get(i);
						
						Preconditions.checkState(csv.get(0, colStart).startsWith("Recur"));
						SectProbGainResults[] vals = new SectProbGainResults[csv.getNumRows()-1];
						double recurrInt, openInt, pPois, pBPT, pGain, implGain;
						for (int row=1; row<csv.getNumRows(); row++) {
							recurrInt = Double.parseDouble(csv.get(row, colStart));
							openInt = Double.parseDouble(csv.get(row, colStart+1));
							pPois = Double.parseDouble(csv.get(row, colStart+2));
							pBPT = Double.parseDouble(csv.get(row, colStart+3));
							pGain = Double.parseDouble(csv.get(row, colStart+4));
							implGain = Double.parseDouble(csv.get(row, colStart+5));
							int index;
							if (parents)
								index = row-1;
							else
								index = Integer.parseInt(csv.get(row, 1));
							vals[index] = new SectProbGainResults(recurrInt, openInt, pPois, pBPT, pGain, implGain);
						}
						
						Map<LogicTreeBranch, SectProbGainResults[]> branchVals = table.get(cov, aveType);
						if (branchVals == null) {
							branchVals = Maps.newHashMap();
							table.put(cov, aveType, branchVals);
						}
						Preconditions.checkState(!branchVals.containsKey(branch));
						branchVals.put(branch, vals);
					}
				} catch (RuntimeException e) {
					System.out.println("Error on "+entry.getName());
					if (!debug_zip_file_check) {
						zip.close();
						throw e;
					}
				}
			}
			zip.close();
		}
		
		return maps;
	}
	
	private static void writeOpenIntTableComparisons() throws IOException, DocumentException,
	GMT_MapException, RuntimeException {
		// m6.7 30 year comparisons between:
		// ver 9 / ver 8
		// ver 9 with RCF / ver 9
		
		FaultSystemSolution sol = FaultSystemIO.loadSol(
				new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
						"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		
		Map<Integer, List<LastEventData>> ver9Data = LastEventData.load(UCERF3_DataUtils.locateResourceAsStream(
				"paleoRateData", "UCERF3_OpenIntervals_ver9.xls"), new int[] {0,1});
		
		Map<Integer, List<LastEventData>> ver9RCFData = LastEventData.load(UCERF3_DataUtils.locateResourceAsStream(
				"paleoRateData", "UCERF3_OpenIntervals_ver9.xls"), new int[] {0,2});
		
		Map<Integer, List<LastEventData>> ver8Data = LastEventData.load(UCERF3_DataUtils.locateResourceAsStream(
				"paleoRateData", "UCERF3_OpenIntervals_ver8.xls"), new int[] {0,1});
		
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(sol);
		
		LastEventData.populateSubSects(sol.getRupSet().getFaultSectionDataList(), ver9Data);
		
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		erf.getTimeSpan().setDuration(30d);
		
		EvenlyDiscretizedFunc[] ver9Vals = calcSubSectSupraSeisMagProbDists(erf, 6.7d, 1, 0.1d);
		
		LastEventData.populateSubSects(sol.getRupSet().getFaultSectionDataList(), ver8Data);
		
		// make it update
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		
		EvenlyDiscretizedFunc[] ver8Vals = calcSubSectSupraSeisMagProbDists(erf, 6.7d, 1, 0.1d);
		
		LastEventData.populateSubSects(sol.getRupSet().getFaultSectionDataList(), ver9RCFData);
		
		// make it update
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		
		EvenlyDiscretizedFunc[] ver9RCFVals = calcSubSectSupraSeisMagProbDists(erf, 6.7d, 1, 0.1d);
		
		double[] ver9_8_ratios = new double[ver9Vals.length];
		double[] ver9RCF_9_ratios = new double[ver9Vals.length];
		for (int i=0; i<ver9Vals.length; i++) {
			ver9_8_ratios[i] = ver9Vals[i].getY(0) / ver8Vals[i].getY(0);
			ver9RCF_9_ratios[i] = ver9RCFVals[i].getY(0) / ver9Vals[i].getY(0);
		}
		
		CPT ratioCPT = getScaledLinearRatioCPT(0.02);
		ArrayList<LocationList> traces = FaultBasedMapGen.getTraces(sol.getRupSet().getFaultSectionDataList());
		Region region = new CaliforniaRegions.RELM_TESTING();
		
		File dir = new File("/tmp");
		FaultBasedMapGen.makeFaultPlot(ratioCPT, traces, ver9_8_ratios, region,
				dir, "oi_table_m6.7_30yr_ver9_ver8_ratio", false, true,
				"M>=6.7 30yr Prob, OI Table Ver9 / Ver8");
//				"OI Ver9 / Ver8");
		
		FaultBasedMapGen.makeFaultPlot(ratioCPT, traces, ver9RCF_9_ratios, region,
				dir, "oi_table_m6.7_30yr_ver9_RCF_ver9_ratio", false, true,
				"M>=6.7 30yr Prob, OI Table Ver9_RCF / Ver9");
//				"OI Ver9_RCF / Ver9");
	}
	
	private static final boolean debug_zip_file_check = false;
	private static final boolean debug_saf_nan_check = false;

	public static List<Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
	Map<LogicTreeBranch, SectProbGainResults[]>>>
	loadBranchFaultCSVVals(File[] files, int[] magRangeIndexes) throws ZipException, IOException {
		int[] colStarts = new int[magRangeIndexes.length];
		List<Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
		Map<LogicTreeBranch, SectProbGainResults[]>>> maps = Lists.newArrayList();
		for (int i=0; i<magRangeIndexes.length; i++) {
			// first '1' is for name
						// the other '1' is for the blank col at the start of each mag range
						colStarts[i] = 1 + magRangeIndexes[i]*3 + 1;
			Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions,
				Map<LogicTreeBranch, SectProbGainResults[]>> table = HashBasedTable.create();
			maps.add(table);
		}
		
		for (File file : files) {
			ZipFile zip = new ZipFile(file);
			
			for (ZipEntry entry : Collections.list(zip.entries())) {
				String name = entry.getName().trim();
//				System.out.println("File: "+name);
				if (!name.endsWith("main_faults.csv"))
					continue;
				
				// remove first directory name
				int covEnd = name.lastIndexOf("/");
				String namePrefix = name.substring(0, covEnd);
				name = name.substring(covEnd+1);
				// find the cov value
				MagDependentAperiodicityOptions cov = null;
				for (MagDependentAperiodicityOptions testCOV : MagDependentAperiodicityOptions.values()) {
					if (namePrefix.contains(testCOV.name())) {
						cov = testCOV;
						break;
					}
				}
				Preconditions.checkNotNull(cov);
				BPTAveragingTypeOptions aveType = null;
				for (BPTAveragingTypeOptions testType : BPTAveragingTypeOptions.values()) {
					String dirName = MPJ_ERF_ProbGainCalcScriptWriter.getAveDirName(testType);
					if (namePrefix.contains(dirName) || file.getName().startsWith(dirName)) {
						aveType = testType;
						break;
					}
				}
				Preconditions.checkNotNull(aveType);
				LogicTreeBranch branch = LogicTreeBranch.fromFileName(name);
				Preconditions.checkNotNull(branch);
//				System.out.println("Loading "+branch.buildFileName()+", cov="+cov.name());
				CSVFile<String> csv = CSVFile.readStream(zip.getInputStream(entry), true);
				for (int i=0; i<magRangeIndexes.length; i++) {
					int colStart = colStarts[i];
					Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions, Map<LogicTreeBranch, SectProbGainResults[]>> table = maps.get(i);
					
//					System.out.println("Col start: "+colStart);
					
					Preconditions.checkState(csv.get(0, colStart).startsWith("U3 pBPT"));
					SectProbGainResults[] vals = new SectProbGainResults[csv.getNumRows()-1];
					double recurrInt = Double.NaN, openInt = Double.NaN, pPois, pBPT, pGain, implGain = Double.NaN;
					for (int row=1; row<csv.getNumRows(); row++) {
						pBPT = Double.parseDouble(csv.get(row, colStart));
						pPois = Double.parseDouble(csv.get(row, colStart+1));
						pGain = pBPT/pPois;
						int index = row-1; // faults, indexing not important
						vals[index] = new SectProbGainResults(recurrInt, openInt, pPois, pBPT, pGain, implGain);
//						System.out.println(csv.get(row, 0)+": pBPT="+pBPT+"\tpPois="+pPois);
					}
//					System.exit(0);
					
					Map<LogicTreeBranch, SectProbGainResults[]> branchVals = table.get(cov, aveType);
					if (branchVals == null) {
						branchVals = Maps.newHashMap();
						table.put(cov, aveType, branchVals);
					}
					Preconditions.checkState(!branchVals.containsKey(branch));
					branchVals.put(branch, vals);
				}
			}
		}
		
		return maps;
	}
	
	public static void writeTimeDepPlotsForWeb(List<BPTAveragingTypeOptions> aveTypes, boolean skipAvgMethods,
			String dirPrefix, File outputDir)
			throws IOException, DocumentException, GMT_MapException, RuntimeException {
		FaultSystemSolution meanSol = FaultSystemIO.loadSol(
				new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
						"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		writeTimeDepPlotsForWeb(aveTypes, skipAvgMethods, dirPrefix, outputDir, meanSol);
	}
	
	/**
	 * This generates the aggregated UCERF3 Time Dependence plots from data files previously generated by MPJ_ERF_ProbGainCalc.
	 * 
	 * @param aveTypes List of averaging types to be considered. Corresponding zip files should be present for each
	 * averaging type.
	 * @param skipAvgMethods If true, OtherSensitivityTests/AveragingMethods will be ommitted
	 * @param dirPrefix This is the path prefix for data directories. [prefix]-5yr, [prefix]-30yr, [prefix]-main-5yr, [prefix]-main-30yr
	 * must exist and contain the zip files.
	 * @param outputDir Output directory where plots will be written
	 * @param meanSol Branch Averaged solution for OtherSensitivityTests
	 * @throws IOException
	 * @throws DocumentException
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 */
	public static void writeTimeDepPlotsForWeb(List<BPTAveragingTypeOptions> aveTypes, boolean skipAvgMethods,
			String dirPrefix, File outputDir, FaultSystemSolution meanSol)
			throws IOException, DocumentException, GMT_MapException, RuntimeException {
		if (!outputDir.exists())
			outputDir.mkdir();
		
		double[] minMags = { 0d, 6.7d, 7.7d };
		int[] csvMagRangeIndexes = { 4, 0, 2 };
		int[] csvFaultMagRangeIndexes = { 0, 1, 3 };
		double[] durations = { 5d, 30d };
		File[] csvDirs = { new File(dirPrefix+"-5yr"), new File(dirPrefix+"-30yr")};
		File[] csvMainFaultDirs = { new File(dirPrefix+"-main-5yr"), new File(dirPrefix+"-main-30yr")};
		
		// just 6.7, 30yr
//		double[] minMags = { 6.7d };
//		int[] csvMagRangeIndexes = { 0 };
//		int[] csvFaultMagRangeIndexes = { 1 };
//		double[] durations = { 30d };
//		File[] csvDirs = { new File(dirPrefix+"-30yr")};
//		File[] csvMainFaultDirs = { new File(dirPrefix+"-main-30yr")};
		
		Preconditions.checkState(aveTypes.size() >= 1);
		String[] csvZipNames = new String[aveTypes.size()];
		for (int i=0; i<aveTypes.size(); i++)
			csvZipNames[i] = MPJ_ERF_ProbGainCalcScriptWriter.getAveDirName(aveTypes.get(i))+".zip";
		
//		File[] csvDirs = { new File("/home/kevin/OpenSHA/UCERF3/probGains/2013_12_14-ucerf3-prob-gains-open1875-5yr"),
//				new File("/home/kevin/OpenSHA/UCERF3/probGains/2013_12_14-ucerf3-prob-gains-open1875-30yr")};
//		File[] csvMainFaultDirs = { new File("/home/kevin/OpenSHA/UCERF3/probGains/2013_12_14-ucerf3-prob-gains-open1875-main-5yr"),
//				new File("/home/kevin/OpenSHA/UCERF3/probGains/2013_12_14-ucerf3-prob-gains-open1875-main-30yr")};
		int def_hist_open_ref = 1875;
//		int def_hist_open_ref = FaultSystemSolutionERF.START_TIME_DEFAULT;
		
		// write metadata file
		FileWriter fw = new FileWriter(new File(outputDir, "metadata.txt"));
		DateFormat df = new SimpleDateFormat("yyyy/MM/dd 'at' HH:mm:ss z");
		fw.write("Directory and plots generated by "+FaultSysSolutionERF_Calc.class.getName()+".writeTimeDepPlotsForWeb()\n");
		fw.write("Which calls and aggregates plots from "+FaultSysSolutionERF_Calc.class.getName()
				+".writeBranchAggregatedFigs(...)\n");
		fw.write("Date: "+df.format(new Date())+"\n");
		fw.write("Averaging types: "+Joiner.on(", ").join(aveTypes)+"\n");
		fw.write("\n");
		String zipNames;
		if (csvZipNames.length == 1)
			zipNames = "/"+csvZipNames[0];
		else
			zipNames = "/["+Joiner.on(",").join(Lists.newArrayList(csvZipNames))+"]";
		for (int i=0; i<durations.length; i++)
			fw.write((int)durations[i]+"yr data loaded from "+csvDirs[i].getName()+zipNames+"\n");
		fw.write("\n");
		fw.write("Data is generated by "+FaultSysSolutionERF_Calc.class.getName()+".writeSubSectionTimeDependenceCSV(erf, subOutputFile)"
				+" and "+FaultSysSolutionERF_Calc.class.getName()+".writeParentSectionTimeDependenceCSV(erf, subOutputFile). Results"
				+ " are calculated for each logic tree branch in parallel on a cluster with "+MPJ_ERF_ProbGainCalc.class.getName()
				+". See also "+MPJ_ERF_ProbGainCalcScriptWriter.class.getName()+" for batch submission script generation."
				+ " Each zip file contains data files for each mag-dependent apreriodicity funcion. Note that data aggregated"
				+ " for 'main faults' is calculated separately, also by MPJ_ERF_ProbGainCalc with the --main-faults argument.");
		fw.close();
		
		List<String> labels = Lists.newArrayList();
		List<File> parentSectFiles = Lists.newArrayList();
		List<File> mainFaultFiles = Lists.newArrayList();
		
		Region region = new CaliforniaRegions.RELM_COLLECTION();
		CPT tightRatioCPT = getScaledLinearRatioCPT(0.02, 0.8d, 1.2d);
		CPT wideRatioCPT = getScaledLinearRatioCPT(0.02);
		
		for (int i = 0; i < durations.length; i++) {
			double duration = durations[i];
			File[] csvZipFiles = new File[csvZipNames.length];
			File[] csvMainFualtZipFiles = new File[csvZipNames.length];
			for (int j=0; j<csvZipNames.length; j++) {
				String csvZipName = csvZipNames[j];
				csvZipFiles[j] = new File(csvDirs[i], csvZipName);
				csvMainFualtZipFiles[j] = new File(csvMainFaultDirs[i], csvZipName);
			}
			
			File avgTempDir = null;
			if (!skipAvgMethods && !debug_zip_file_check) {
				FaultSystemSolutionERF meanERF = new FaultSystemSolutionERF(meanSol);
				meanERF.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
				meanERF.setParameter(HistoricOpenIntervalParam.NAME, (double)(FaultSystemSolutionERF.START_TIME_DEFAULT-def_hist_open_ref));
				meanERF.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
				meanERF.getTimeSpan().setDuration(duration);
				
				// TRUE: RI, NTS
				// FALSE: Rate, TS
				List<BPTAveragingTypeOptions> avgTypes = Lists.newArrayList(BPTAveragingTypeOptions.values());
				avgTempDir = FileUtils.createTempDir();
//				File testDir = new File("/tmp/avg_test_"+(int)duration);
//				testDir.mkdir();
//				makeAvgMethodProbGainMaps(meanERF, testDir, "tester");
				while (avgTypes.size() >= 2) {
					int refIndex = 0;
					makeAvgMethodProbGainMaps(meanERF, avgTempDir, null, avgTypes, refIndex);
					avgTypes.remove(0);
				}
			}
			
			// average cov's
			System.out.println("Loading all parent sect results from "+csvDirs[i].getAbsolutePath()
					+" ("+Joiner.on(",").join(csvZipNames)+")");
			List<Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions, Map<LogicTreeBranch, SectProbGainResults[]>>> parentMaps =
					loadBranchCSVVals(csvZipFiles, csvMagRangeIndexes, true);
			System.out.println("Loading all sub sect results from "+csvDirs[i].getAbsolutePath()
					+" ("+Joiner.on(",").join(csvZipNames)+")");
			List<Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions, Map<LogicTreeBranch, SectProbGainResults[]>>> subSectMaps =
					loadBranchCSVVals(csvZipFiles, csvMagRangeIndexes, false);
			System.out.println("Loading all main fault results from "+csvMainFaultDirs[i].getAbsolutePath()
					+" ("+Joiner.on(",").join(csvZipNames)+")");
			List<Table<MagDependentAperiodicityOptions, BPTAveragingTypeOptions, Map<LogicTreeBranch, SectProbGainResults[]>>> mainFaultMaps =
					loadBranchFaultCSVVals(csvMainFualtZipFiles, csvFaultMagRangeIndexes);
			
			if (debug_zip_file_check)
				continue;
			
			for (int j = 0; j < minMags.length; j++) {
				FaultSystemSolutionERF meanERF = new FaultSystemSolutionERF(meanSol);
				meanERF.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
				meanERF.setParameter(HistoricOpenIntervalParam.NAME, (double)(FaultSystemSolutionERF.START_TIME_DEFAULT-def_hist_open_ref));
				meanERF.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
				if (csvZipNames.length == 1)
					// we have only one avg method, make sure mean erf uses that one. otherwise use default
					meanERF.setParameter(BPTAveragingTypeParam.NAME, aveTypes.get(0));
				meanERF.getTimeSpan().setDuration(duration);
				
				double minMag = minMags[j];
				String fileLabel, label;
				
				if (minMag == 0) {
					label = "All Events";
					fileLabel = "all";
				} else {
					label = "M>="+(float)minMag;
					fileLabel = "m"+(float)minMag;
				}
				
				label += ", "+(int)duration+"yr forecast";
				fileLabel += "_"+(int)duration+"yr";
				
				File subDir = new File(outputDir, fileLabel);
				if (!subDir.exists())
					subDir.mkdir();
				
				File branchDir = new File(subDir, "BranchAveragedResults");
				if (!branchDir.exists())
					branchDir.mkdir();
				
				File tmpResultsDir = FileUtils.createTempDir();
				System.out.println("Making "+label+" sub section plots");
				Map<Integer, List<Double>> meanVals =
						writeBranchAggregatedTimeDepFigs(subSectMaps.get(j), tmpResultsDir, false,
								minMag, duration);
				
				System.out.println("Copying "+label+" sub section plots");
				
				// copy over branch averaged appropriate files
				Files.copy(new File(tmpResultsDir, "mean_time_dep_prob.pdf"), new File(branchDir, "U3_TimeDep_Mean.pdf"));
				Files.copy(new File(tmpResultsDir, "mean_time_dep_prob.kml"), new File(branchDir, "U3_TimeDep_Mean.kml"));
				Files.copy(new File(tmpResultsDir, "FM3_1_mean_time_dep_prob.pdf"),
						new File(branchDir, "U3_FM3_1_TimeDep_Mean.pdf"));
				Files.copy(new File(tmpResultsDir, "FM3_1_mean_time_dep_prob.kml"),
						new File(branchDir, "U3_FM3_1_TimeDep_Mean.kml"));
				Files.copy(new File(tmpResultsDir, "FM3_2_mean_time_dep_prob.pdf"),
						new File(branchDir, "U3_FM3_2_TimeDep_Mean.pdf"));
				Files.copy(new File(tmpResultsDir, "FM3_2_mean_time_dep_prob.kml"),
						new File(branchDir, "U3_FM3_2_TimeDep_Mean.kml"));
				Files.copy(new File(tmpResultsDir, "min_time_dep_prob.pdf"), new File(branchDir, "U3_TimeDep_Min.pdf"));
				Files.copy(new File(tmpResultsDir, "max_time_dep_prob.pdf"), new File(branchDir, "U3_TimeDep_Max.pdf"));
				Files.copy(new File(tmpResultsDir, "gain_u3.pdf"), new File(branchDir, "U3_Gain.pdf"));
				
				// copy over branch sensitivity maps
				File branchSensDir = new File(subDir, "BranchSensitivityMaps");
				if (!branchSensDir.exists())
					branchSensDir.mkdir();
				for (File file : new File(tmpResultsDir, "branch_ratios").listFiles()) {
					String name = file.getName();
					if (!name.endsWith(".pdf") && !name.endsWith(".csv"))
						continue;
					Files.copy(file, new File(branchSensDir, name));
//					System.out.println("Copying: "+file.getAbsolutePath()+" to "+branchSensDir.getAbsolutePath());
				}
				
				// copy over sub sect vals
				Files.copy(new File(tmpResultsDir, "branch_aggregated_subs.csv"), new File(subDir, "sub_section_probabilities.csv"));
				
				FileUtils.deleteRecursive(tmpResultsDir);
				
				// now parent sections
				tmpResultsDir = FileUtils.createTempDir();
				System.out.println("Making "+label+" parent section plots");
				writeBranchAggregatedTimeDepFigs(parentMaps.get(j), tmpResultsDir, true, minMag, duration);
				System.out.println("Copying "+label+" parent section plots");
				File parentsDestCSV = new File(subDir, "parent_section_probabilities.csv");
				Files.copy(new File(tmpResultsDir, "branch_aggregated_parents.csv"), parentsDestCSV);
				Files.copy(new File(tmpResultsDir, "gain_u3_u2.pdf"), new File(branchDir, "U3_U2_TimeDep_Ratio.pdf"));
				labels.add(label);
				parentSectFiles.add(parentsDestCSV);
				
				FileUtils.deleteRecursive(tmpResultsDir);
				
				// now main faults sections
				tmpResultsDir = FileUtils.createTempDir();
				System.out.println("Making "+label+" main fault plots");
				writeBranchAggregatedFaultResults(mainFaultMaps.get(j), tmpResultsDir, minMag, duration);
				System.out.println("Copying "+label+" main fault plots");
				File mainFaultsDestCSV = new File(subDir, "main_fault_probabilities.csv");
				Files.copy(new File(tmpResultsDir, "branch_aggregated_main_faults.csv"), mainFaultsDestCSV);
				mainFaultFiles.add(mainFaultsDestCSV);
				
				FileUtils.deleteRecursive(tmpResultsDir);
				System.out.println("Done with "+label);
				
				// OtherSensitivityTests
				File sensTestDir = new File(subDir, "OtherSensitivityTests");
				if (!sensTestDir.exists())
					sensTestDir.mkdir();
				// ratio of branch-averaged mean to mean FSS
				meanERF.getTimeSpan().setDuration(duration);
				meanERF.updateForecast();
				EvenlyDiscretizedFunc[] branchAvgResults = calcSubSectSupraSeisMagProbDists(meanERF, minMag, 1, 0.5d);
				double[] ratios = new double[branchAvgResults.length];
				double[] baProbs = new double[branchAvgResults.length];
				int prevParent = -1;
				int indexInParent = -1;
				List<LocationList> faults = Lists.newArrayList();
				for (FaultSectionPrefData sect : meanSol.getRupSet().getFaultSectionDataList())
					faults.add(sect.getFaultTrace());
				for (int k=0; k<ratios.length; k++) {
					double baProb = branchAvgResults[k].getY(0);
					int myParent = meanSol.getRupSet().getFaultSectionData(k).getParentSectionId();
					if (myParent == prevParent) {
						indexInParent++;
					} else {
						prevParent = myParent;
						indexInParent = 0;
					}
					double meanProb = meanVals.get(myParent).get(indexInParent);
					ratios[k] = baProb / meanProb;
					baProbs[k] = baProb;
				}
				FaultBasedMapGen.makeFaultPlot(wideRatioCPT, faults, ratios, region,
						sensTestDir, "Branch_Averaged_vs_Mean_Ratio", false, true,
						"UCERF3 TimeDep Branch Averaged / True Mean");
				
				// mean ratios for each averaging method
				File avgMethodDir = new File(sensTestDir, "AveragingMethods");
				if (!avgMethodDir.exists())
					avgMethodDir.mkdir();
				
				// copy results
				String magStr;
				if (minMag > 0)
					magStr = (float)minMag+"";
				else
					magStr = "supra_seis";
				if (avgTempDir != null) {
					for (File file : avgTempDir.listFiles())
						if (file.getName().endsWith(".pdf") && file.getName().contains(magStr))
							Files.copy(file, new File(avgMethodDir, file.getName()));
				}
				
				int[] comps = { FaultSystemSolutionERF.START_TIME_DEFAULT, 1850, 1900 };
				
				for (int comp : comps) {
					// historic open interval (ratio of none to 1850)
					meanERF.setParameter(HistoricOpenIntervalParam.NAME, (double)(FaultSystemSolutionERF.START_TIME_DEFAULT-comp));
					meanERF.updateForecast();
					EvenlyDiscretizedFunc[] histOpenResults = calcSubSectSupraSeisMagProbDists(meanERF, minMag, 1, 0.5d);
					ratios = new double[baProbs.length];
					for (int k=0; k<baProbs.length; k++)
						ratios[k] = histOpenResults[k].getY(0) / baProbs[k];
					String compStr;
					if (comp == FaultSystemSolutionERF.START_TIME_DEFAULT)
						compStr = "None";
					else
						compStr = comp+""; 
					FaultBasedMapGen.makeFaultPlot(wideRatioCPT, faults, ratios, region,
							sensTestDir, "Hist_Open_Interval_Test_"+compStr, false, true,
							"UCERF3 Time Dep Hist Open Interval "+compStr+" / "+def_hist_open_ref);
				}
				// clear out png files
				for (File file : sensTestDir.listFiles())
					if (file.getName().endsWith(".png"))
						file.delete();
			}
			if (avgTempDir != null)
				FileUtils.deleteRecursive(avgTempDir);
		}
		
		// now aggregate parent section files into one excel file
		HSSFWorkbook wb = new HSSFWorkbook();
		for (int i=0; i<labels.size(); i++) {
			HSSFSheet sheet = wb.createSheet();
//			sheet.set
			wb.setSheetName(i, labels.get(i));
			CSVFile<String> csv = CSVFile.readFile(parentSectFiles.get(i), true);
			
			// header
			HSSFRow header = sheet.createRow(0);
			for (int col=0; col<csv.getNumCols(); col++)
				header.createCell(col).setCellValue(csv.get(0, col));
			
			for (int row=1; row<csv.getNumRows(); row++) {
				HSSFRow r = sheet.createRow(row);
				// first col is name
				r.createCell(0).setCellValue(csv.get(row, 0));
				for (int col=1; col<csv.getNumCols(); col++)
					r.createCell(col).setCellValue(Double.parseDouble(csv.get(row, col)));
			}
		}
		if (debug_zip_file_check)
			return;
		wb.setActiveSheet(0);
		
		FileOutputStream out = new FileOutputStream(new File(outputDir, "parent_section_probabilities.xls"));
		wb.write(out);
		out.close();
		
		// now aggregate main fault files into one excel file
		wb = new HSSFWorkbook();
		for (int i=0; i<labels.size(); i++) {
			HSSFSheet sheet = wb.createSheet();
//			sheet.set
			wb.setSheetName(i, labels.get(i));
			CSVFile<String> csv = CSVFile.readFile(mainFaultFiles.get(i), true);
			
			// header
			HSSFRow header = sheet.createRow(0);
			for (int col=0; col<csv.getNumCols(); col++)
				header.createCell(col).setCellValue(csv.get(0, col));
			
			for (int row=1; row<csv.getNumRows(); row++) {
				HSSFRow r = sheet.createRow(row);
				// first col is name
				r.createCell(0).setCellValue(csv.get(row, 0));
				for (int col=1; col<csv.getNumCols(); col++)
					r.createCell(col).setCellValue(Double.parseDouble(csv.get(row, col)));
			}
		}
		wb.setActiveSheet(0);
		
		out = new FileOutputStream(new File(outputDir, "main_fault_probabilities.xls"));
		wb.write(out);
		out.close();
		
		doFinalWebPlotAssembly(outputDir, aveTypes.size()>1);
	}
	
	private static void doFinalWebPlotAssembly(File dir, boolean defaultAve) throws IOException {
		writeStringToFile(new File(dir, "HEADER.html"),
				"<h1 style=\"font-family:'HelveticaNeue-Light', sans-serif; font-weight:normal;\">"
				+ "UCERF3 Time Dependent Supplementary Figures</h1>\n"
				+"\n"
				+"<p style=\"font-family:'HelveticaNeue-Light', sans-serif; font-weight:normal; width:540px;\">"
				+ "Each directory contains results for a different magnitude range and time span. For example, 'm6.7_5yr' "
				+ "refers to 5 year results for M >= 6.7 ruptures and 'all_30yr' refers to 30 year results for all "
				+ "supra-seismogenic ruptures.<br>Each calculation includes aftershocks. UCERF2 results, "
				+ "where presented, have been scaled to include aftershocks as: "
				+ "newProb = 1 - exp((1/0.97)*ln(1-oldProb))</p>");
		
		String aveTypeStr;
		if (defaultAve)
			aveTypeStr = "default BPT averaging type";
		else
			aveTypeStr = "the corresponding BPT averaging type";
		
		for (File subDir : dir.listFiles()) {
			String name = subDir.getName();
			if (!subDir.isDirectory() || name.startsWith("."))
				continue;
			String label;
			if (name.equals("all_5yr"))
				label = "All Supra-Seismogenic Ruptures, 5 Year Forecast";
			else if (name.equals("all_30yr"))
				label = "All Supra-Seismogenic Ruptures, 30 Year Forecast";
			else if (name.equals("m6.7_5yr"))
				label = "All M>=6.7 Ruptures, 5 Year Forecast";
			else if (name.equals("m6.7_30yr"))
				label = "All M>=6.7 Ruptures, 30 Year Forecast";
			else if (name.equals("m7.7_5yr"))
				label = "All M>=7.7 Ruptures, 5 Year Forecast";
			else if (name.equals("m7.7_30yr"))
				label = "All M>=7.7 Ruptures, 30 Year Forecast";
			else
				throw new IllegalStateException("Unexpected directory: "+name);
			
			writeStringToFile(new File(subDir, "HEADER.html"),
					"<h1 style=\"font-family:'HelveticaNeue-Light', sans-serif; font-weight:normal;\">"
					+ label+" Figures</h1>\n"
					+"\n"
					+"<p style=\"font-family:'HelveticaNeue-Light', sans-serif; font-weight:normal; width:540px;\">"
					+ "<b><i>BranchAveragedResults</i></b>: Results/Comparisons with UCERF2, aggregated across all logic tree branches"
					+ "<br><b><i>BranchSensitivityMaps</i></b>: Sensitivity of Time Dependent Probabilities to each logic tree branch choice"
					+ "<br><b><i>OtherSensitivityTests</i></b>: Miscelaneous sensitivity tests using the branch averaged solution, Mid COV values, and "+aveTypeStr+"</p>");
			
			File sensDir = new File(subDir, "BranchSensitivityMaps");
			try {
				TestPDFCombine.combine(sensDir, sensDir);
			} catch (com.lowagie.text.DocumentException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
			
			File avgSensDir = new File(new File(subDir, "OtherSensitivityTests"), "AveragingMethods");
			if (avgSensDir.listFiles() == null || avgSensDir.listFiles().length == 0)
				avgSensDir.delete();
		}
	}
	
	public static void writeStringToFile(File file, String string) throws IOException {
		FileWriter fw = new FileWriter(file);
		fw.write(string+"\n");
		fw.close();
	}
	
	/**
	 * Loads the UCERF2 main fault MPDs from the UCERF2 report table. They can be scaled to include aftershocks.
	 * 
	 * Names will be matched to the respective UCERF3 names, and each function array is organized as: [min max mean]
	 * @param includeAftershocks
	 * @param timeDep
	 * @return
	 * @throws IOException
	 */
	public static Map<String, DiscretizedFunc[]> loadUCERF2MainFaultMPDs(
			boolean includeAftershocks, boolean timeDep) throws IOException {
		CSVFile<String> csv;
		if (timeDep)
			csv = CSVFile.readStream(UCERF3_DataUtils.locateResourceAsStream(
					"UCERF2_Section_TimeDepMFDs", "UCERF2_Main_Fault_TimeDep_Probs.csv"), false);
		else
			csv = CSVFile.readStream(UCERF3_DataUtils.locateResourceAsStream(
					"UCERF2_Section_MFDs", "UCERF2_Main_Fault_TimeIndep_Probs.csv"), false);
		
		Set<String> u3MainFaultNames = FaultModels.parseNamedFaultsAltFile(UCERF3_DataUtils.getReader("FaultModels",
				"MainFaultsForTimeDepComparison.txt")).keySet();
		
		double aftMult = 1d/FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS;
		
		// get the x values (magnitudes) from the first column
		double[] xVals = new double[csv.getNumRows()-2];
		for (int row=2; row<csv.getNumRows(); row++)
			xVals[row-2] = Double.parseDouble(csv.get(row, 0));
		
		Map<String, DiscretizedFunc[]> map = Maps.newHashMap();
		
		for (int col=1; col<csv.getLine(0).size(); col+=3) {
			DiscretizedFunc[] funcs = new DiscretizedFunc[3]; // organized as min max mean
			
			String name = csv.get(0, col);
			
			// now map the name to the UCERF3 names
			int longestMatch = 6; // must be at least 7 long to be a match
			List<String> closestNames = null;
			for (String u3Name : u3MainFaultNames) {
				int length = StringUtils.getCommonPrefix(name, u3Name).length();
				if (length > longestMatch) {
					longestMatch = length;
					closestNames = Lists.newArrayList(u3Name);
				} else if (length == longestMatch && closestNames != null) {
					closestNames.add(u3Name);
				}
			}
			if (closestNames == null) {
				System.out.println("No match for "+name);
				continue;
			}
			System.out.println("Mapped "+name+" to "+Joiner.on(",").join(closestNames));
			
			ArbitrarilyDiscretizedFunc minFunc = new ArbitrarilyDiscretizedFunc();
			minFunc.setName("UCERF2 "+name+" (min)");
			ArbitrarilyDiscretizedFunc maxFunc = new ArbitrarilyDiscretizedFunc();
			maxFunc.setName("UCERF2 "+name+" (max)");
			ArbitrarilyDiscretizedFunc meanFunc = new ArbitrarilyDiscretizedFunc();
			meanFunc.setName("UCERF2 "+name+" (mean)");
			
			funcs[0] = minFunc;
			funcs[1] = maxFunc;
			funcs[2] = meanFunc;
			
			for (int i=0; i<funcs.length; i++) {
				int c = col+i;
				for (int j=0; j<xVals.length; j++) {
					int row = j+2;
					double prob = Double.parseDouble(csv.get(row, c));
					if (includeAftershocks)
						prob = 1 - Math.exp(aftMult*Math.log(1-prob));
					funcs[i].set(xVals[j], prob);
				}
			}
			
			for (String u3Name : closestNames)
				map.put(u3Name, funcs);
		}
		
		return map;
	}
	
	private static void debugAvgMethods() throws IOException, DocumentException {
		debugAvgMethods(FaultSystemIO.loadSol(
				new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
						"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip")));
	}
	
	private static void debugAvgMethods(FaultSystemSolution sol) {
		// choose a subsection, lets do first subsection on Mojave as that lights up in the map
		// debugging: http://opensha.usc.edu/ftp/kmilner/ucerf3/TimeDependent_preview/m6.7_30yr/
		// 		OtherSensitivityTests/AveragingMethods/30.0yr_6.7+_AveRate_AveNormTS_vs_AveRI_AveTS.pdf
		int subSectIndex = -1;
		for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList()) {
			if (sect.getName().contains("Mojave")) {
				subSectIndex = sect.getSectionId();
				break;
			}
		}
		Preconditions.checkState(subSectIndex >= 0);
		
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(sol);
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		erf.getTimeSpan().setDuration(30d);
		
		// TRUE: RI, NTS
		// FALSE: Rate, TS
		
		// first for AveRI&AveTS, the denominator
		erf.setParameter(BPTAveragingTypeParam.NAME, BPTAveragingTypeOptions.AVE_RI_AVE_TIME_SINCE);
		erf.updateForecast();
		double denomProb = calcSubSectSupraSeisMagProbDists(erf, 6.7d, 1, 0.1)[subSectIndex].getY(0);
		
		// start with a clean slate to be safe
		erf = new FaultSystemSolutionERF(sol);
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		erf.getTimeSpan().setDuration(30d);
		
		// TRUE: RI, NTS
		// FALSE: Rate, TS
		
		// first for AveRate&AveNTS, the numerator
		erf.setParameter(BPTAveragingTypeParam.NAME, BPTAveragingTypeOptions.AVE_RATE_AVE_NORM_TIME_SINCE);
		erf.updateForecast();
		double numProb = calcSubSectSupraSeisMagProbDists(erf, 6.7d, 1, 0.1)[subSectIndex].getY(0);
		
		double probGain = numProb / denomProb;
		
		System.out.println("Subsection "+subSectIndex+" results: "+numProb+"/"+denomProb+" = "+probGain);
	}
	
	
	/**
	 * This generates tests 30-yr plots for three of the averaging methods for
	 * 2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip
	 * 
	 * @param yrForOpenInterval - the year assumed for the open interval (e,g, 1875)
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 * @throws IOException
	 */
	public static void testAveragingMethodsForProbMaps(double yrForOpenInterval) throws GMT_MapException, RuntimeException, IOException {

		// these are the magnitude thresholds (all rups included below too)
		double minMag = 6.7;
		int numMag = 4;
		double deltaMag = 0.5;
		
		
		
		FaultSystemSolution meanSol=null;
		try {
			meanSol = FaultSystemIO.loadSol(
					new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
							"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		} catch (DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		double duration = 30d;
		
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(meanSol);
		erf.getTimeSpan().setDuration(duration);

		FaultSystemSolution sol = erf.getSolution();
		
		// NoOpenInterval
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		erf.setParameter(MagDependentAperiodicityParam.NAME, MagDependentAperiodicityOptions.MID_VALUES);
		
		int startYear = erf.getTimeSpan().getStartTimeYear();
		double openIntervalYrs = (double)startYear-yrForOpenInterval;
		System.out.println("ERF startYear="+startYear+": openIntervalYrs="+openIntervalYrs);
		erf.setParameter(HistoricOpenIntervalParam.NAME, openIntervalYrs);
		
		String prefix = "aveMethodsMidAper_OpenInt"+Math.round(openIntervalYrs);
		String dirName = "AveMethods_tests_MidAper_OpenInt"+Math.round(openIntervalYrs);
		File saveDir = new File(dirName);
		if (!saveDir.exists())
			saveDir.mkdir();
		
		// now do the three cases:

		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE;
		erf.setParameter(BPTAveragingTypeParam.NAME, aveType);

		erf.updateForecast();
		
		EvenlyDiscretizedFunc[] aveRI_aveNTS_Funcs = calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
		EvenlyDiscretizedFunc[] aveRI_aveNTS_AllMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);
	
		// Change calc type
		aveType = BPTAveragingTypeOptions.AVE_RI_AVE_TIME_SINCE;
		erf.setParameter(BPTAveragingTypeParam.NAME, aveType);
		erf.updateForecast();
		
		EvenlyDiscretizedFunc[] aveRI_aveTS_Funcs = calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
		EvenlyDiscretizedFunc[] aveRI_aveTS_AllMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);

		// Change calc type
		aveType = BPTAveragingTypeOptions.AVE_RATE_AVE_NORM_TIME_SINCE;
		erf.setParameter(BPTAveragingTypeParam.NAME, aveType);
		erf.updateForecast();
		
		EvenlyDiscretizedFunc[] aveRate_aveNTS_Funcs = calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
		EvenlyDiscretizedFunc[] aveRate_aveNTS_AllMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);

		// set CPT files
		CPT diffCPT = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-0.1, 0.1);
		CPT probCPT = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-4, 0);
		CPT ratioCPT = getScaledLinearRatioCPT(0.02);
		
		List<LocationList> faults = Lists.newArrayList();
		for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList())
			faults.add(sect.getFaultTrace());
		
		Region region = new CaliforniaRegions.RELM_COLLECTION();
		
		if (prefix == null)
			prefix = "";
		if (!prefix.isEmpty() && !prefix.endsWith("_"))
			prefix += "_";
		
		prefix += (int)duration+"yr";
		
		for (int i=0; i<numMag+1; i++) {
			
			double[] aveRI_aveNTS_Vals;
			double[] aveRI_aveTS_Vals;
			double[] aveRate_aveNTS_Vals;
			String myPrefix;
			String magStr;
			if (i == numMag) {
				aveRI_aveNTS_Vals = extractYVals(aveRI_aveNTS_AllMags, 0);
				aveRI_aveTS_Vals =  extractYVals(aveRI_aveTS_AllMags, 0);
				aveRate_aveNTS_Vals = extractYVals(aveRate_aveNTS_AllMags, 0);
				myPrefix = prefix+"_supra_seis";
				magStr = "Supra Seis";
			} else {
				aveRI_aveNTS_Vals = extractYVals(aveRI_aveNTS_Funcs, i);
				aveRI_aveTS_Vals =  extractYVals(aveRI_aveTS_Funcs, i);
				aveRate_aveNTS_Vals = extractYVals(aveRate_aveNTS_Funcs, i);

				double mag = aveRI_aveNTS_Funcs[0].getX(i);
				myPrefix = prefix+"_"+(float)mag+"+";
				magStr = "M>="+(float)mag;
			}
			
			double[] aveRI_aveTS_over_aveRI_aveNTS_ratio = new double[aveRI_aveNTS_Vals.length];
			double[] aveRate_aveNTS_over_aveRI_aveNTS_ratio = new double[aveRI_aveNTS_Vals.length];
			double[] aveRI_aveTS_over_aveRI_aveNTS_diff = new double[aveRI_aveNTS_Vals.length];
			double[] aveRate_aveNTS_over_aveRI_aveNTS_diff = new double[aveRI_aveNTS_Vals.length];
			
			double[] aveRI_aveTS_over_Mean_ratio = new double[aveRI_aveNTS_Vals.length];
			double[] aveRI_aveNTS_over_Mean_ratio = new double[aveRI_aveNTS_Vals.length];
			double[] aveRate_aveNTS_over_Mean_ratio = new double[aveRI_aveNTS_Vals.length];

			for (int j=0; j<aveRI_aveTS_over_aveRI_aveNTS_ratio.length; j++) {
				aveRI_aveTS_over_aveRI_aveNTS_ratio[j] = aveRI_aveTS_Vals[j]/aveRI_aveNTS_Vals[j];
				aveRate_aveNTS_over_aveRI_aveNTS_ratio[j] = aveRate_aveNTS_Vals[j]/aveRI_aveNTS_Vals[j];
				aveRI_aveTS_over_aveRI_aveNTS_diff[j] = aveRI_aveTS_Vals[j]-aveRI_aveNTS_Vals[j];
				aveRate_aveNTS_over_aveRI_aveNTS_diff[j] = aveRate_aveNTS_Vals[j]-aveRI_aveNTS_Vals[j];
				
				double mean = (aveRI_aveNTS_Vals[j]+aveRI_aveTS_Vals[j]+aveRate_aveNTS_Vals[j])/3.0;
				aveRI_aveTS_over_Mean_ratio[j]=aveRI_aveTS_Vals[j]/mean;
				aveRI_aveNTS_over_Mean_ratio[j]=aveRI_aveNTS_Vals[j]/mean;
				aveRate_aveNTS_over_Mean_ratio[j]=aveRate_aveNTS_Vals[j]/mean;
			}
			
			// 
			FaultBasedMapGen.makeFaultPlot(probCPT, faults, FaultBasedMapGen.log10(aveRI_aveNTS_Vals), region,
					saveDir, myPrefix+"_aveRI_aveNTS", false, true,
					"Log10("+(float)duration+" yr "+magStr+" aveRI_aveNTS)");
			// 
			FaultBasedMapGen.makeFaultPlot(probCPT, faults, FaultBasedMapGen.log10(aveRI_aveTS_Vals), region,
					saveDir, myPrefix+"_aveRI_aveTS", false, true,
					"Log10("+(float)duration+" yr "+magStr+" aveRI_aveTS)");
			// 
			FaultBasedMapGen.makeFaultPlot(probCPT, faults, FaultBasedMapGen.log10(aveRate_aveNTS_Vals), region,
					saveDir, myPrefix+"_aveRate_aveNTS", false, true,
					"Log10("+(float)duration+" yr "+magStr+" aveRate_aveNTS)");
			// 
			FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, aveRI_aveTS_over_aveRI_aveNTS_ratio, region,
					saveDir, myPrefix+"_aveRI_aveTS_over_aveRI_aveNTS_ratio", false, true,
					(float)duration+" yr "+magStr+" aveRI_aveTS_over_aveRI_aveNTS_ratio");
			// 
			FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, aveRate_aveNTS_over_aveRI_aveNTS_ratio, region,
					saveDir, myPrefix+"_aveRate_aveNTS_over_aveRI_aveNTS_ratio", false, true,
					(float)duration+" yr "+magStr+" aveRate_aveNTS_over_aveRI_aveNTS_ratio");
			//
			FaultBasedMapGen.makeFaultPlot(diffCPT, faults, aveRI_aveTS_over_aveRI_aveNTS_diff, region,
					saveDir, myPrefix+"_aveRI_aveTS_minus_aveRI_aveNTS_diff", false, true,
					(float)duration+" yr "+magStr+" aveRI_aveTS_minus_aveRI_aveNTS_diff");
			// 
			FaultBasedMapGen.makeFaultPlot(diffCPT, faults, aveRate_aveNTS_over_aveRI_aveNTS_diff, region,
					saveDir, myPrefix+"_aveRate_aveNTS_minus_aveRI_aveNTS_diff", false, true,
					(float)duration+" yr "+magStr+" aveRate_aveNTS_minus_aveRI_aveNTS_diff");
			
			FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, aveRI_aveTS_over_Mean_ratio, region,
					saveDir, myPrefix+"_aveRI_aveTS_over_Mean_ratio", false, true,
					(float)duration+" yr "+magStr+" aveRI_aveTS_over_Mean_ratio");
			FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, aveRI_aveNTS_over_Mean_ratio, region,
					saveDir, myPrefix+"_aveRI_aveNTS_over_Mean_ratio", false, true,
					(float)duration+" yr "+magStr+" aveRI_aveNTS_over_Mean_ratio");
			FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, aveRate_aveNTS_over_Mean_ratio, region,
					saveDir, myPrefix+"_aveRate_aveNTS_over_Mean_ratio", false, true,
					(float)duration+" yr "+magStr+" aveRate_aveNTS_over_Mean_ratio");
		}
	}

	
	/**
	 * This generates test plots for historic open interval
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 * @throws IOException
	 */
	public static void testHistOpenIntervalFaultProbMaps() throws GMT_MapException, RuntimeException, IOException {
		double minMag = 6.7;
		int numMag = 4;
		double deltaMag = 0.5;
		
		String prefix = "openIntTest_MidAper";
		String dirName = "OpenInterval_tests_MidAper";
		File saveDir = new File(dirName);
		if (!saveDir.exists())
			saveDir.mkdir();
		
		
		FaultSystemSolution meanSol=null;
		try {
			meanSol = FaultSystemIO.loadSol(
					new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
							"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		} catch (DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		double duration = 30d;
		String durStr = (int)duration+"yr";
		
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(meanSol);
		erf.getTimeSpan().setDuration(duration);

		
		FaultSystemSolution sol = erf.getSolution();
		
		// NoOpenInterval
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		erf.setParameter(MagDependentAperiodicityParam.NAME, MagDependentAperiodicityOptions.MID_VALUES);
		erf.updateForecast();
		
		EvenlyDiscretizedFunc[] noOpenIntFuncs = calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
		EvenlyDiscretizedFunc[] noOpenIntAllMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);
	
		// Open Since 1850
		erf.setParameter(HistoricOpenIntervalParam.NAME, 2014d-1850d);
		erf.updateForecast();
		
		EvenlyDiscretizedFunc[] openIntFuncs = calcSubSectSupraSeisMagProbDists(erf, minMag, numMag, deltaMag);
		EvenlyDiscretizedFunc[] openIntAllMags = calcSubSectSupraSeisMagProbDists(erf, 0d, 1, deltaMag);

		// log space
		CPT probCPT = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-4, 0);
//		CPT ratioCPT = FaultBasedMapGen.getLogRatioCPT().rescale(-0.5, 0.5);
//		CPT ratioCPT = FaultBasedMapGen.getLinearRatioCPT();
		CPT ratioCPT = getScaledLinearRatioCPT(0.02);
		
		List<LocationList> faults = Lists.newArrayList();
		for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList())
			faults.add(sect.getFaultTrace());
		
		Region region = new CaliforniaRegions.RELM_COLLECTION();
		
		if (prefix == null)
			prefix = "";
		if (!prefix.isEmpty() && !prefix.endsWith("_"))
			prefix += "_";
		
		prefix += (int)duration+"yr";
		
		for (int i=0; i<numMag+1; i++) {
			
			double[] poissonVals;
			double[] noOpenIntVals;
			double[] openIntVals;
			String myPrefix;
			String magStr;
			if (i == numMag) {
				noOpenIntVals = extractYVals(noOpenIntAllMags, 0);
				openIntVals =  extractYVals(openIntAllMags, 0);
				myPrefix = prefix+"_supra_seis";
				magStr = "Supra Seis";
			} else {
				noOpenIntVals = extractYVals(noOpenIntFuncs, i);
				openIntVals =  extractYVals(openIntFuncs, 0);
				double mag = noOpenIntFuncs[0].getX(i);
				myPrefix = prefix+"_"+(float)mag+"+";
				magStr = "M>="+(float)mag;
			}
			
			double[] openIntOverNoOpenIntRatio = new double[noOpenIntVals.length];
			for (int j=0; j<openIntOverNoOpenIntRatio.length; j++) {
				openIntOverNoOpenIntRatio[j] = openIntVals[j]/noOpenIntVals[j];
			}
			
			// no open int
			FaultBasedMapGen.makeFaultPlot(probCPT, faults, FaultBasedMapGen.log10(noOpenIntVals), region,
					saveDir, myPrefix+"_NoOpenInt", false, true,
					"Log10("+(float)duration+" yr "+magStr+" NoOpenInt)");
			// no open int
			FaultBasedMapGen.makeFaultPlot(probCPT, faults, FaultBasedMapGen.log10(openIntVals), region,
					saveDir, myPrefix+"_OpenInt", false, true,
					"Log10("+(float)duration+" yr "+magStr+" OpenInt)");
			// ratio
			FaultBasedMapGen.makeFaultPlot(ratioCPT, faults, openIntOverNoOpenIntRatio, region,
					saveDir, myPrefix+"_OpenIntOverNoOpenIntRatio", false, true,
					(float)duration+" yr "+magStr+" OpenIntOverNoOpenIntRatio");
			
			if(magStr.equals("M>=6.7")) {
				for(int s=0;s<openIntOverNoOpenIntRatio.length;s++) {
					System.out.println(s+"\t"+openIntOverNoOpenIntRatio[s]+"\t"+meanSol.getRupSet().getFaultSectionData(s).getName());
				}
			}
		}
	}

	public static void writeFullModelRegionalMagProbDists(String dirPrefix, BPTAveragingTypeOptions[] avgTypes,
			File outputDir, FaultSystemRupSet fm31RupSet, FaultSystemRupSet fm32RupSet) {
		File[] csvDirs = { new File(dirPrefix+"-5yr"), new File(dirPrefix+"-30yr")};
		
		
//		if (cache == null)
//			cache = new FSSRupsInRegionCache(erf);
//		
//		EvenlyDiscretizedFunc result = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
//		
//		// this tracks the rupture probabilities for each mag bin
//		List<List<Double>> probsList = Lists.newArrayList();
//		for (int m=0; m<numMag; m++)
//			probsList.add(new ArrayList<Double>());
//		
//		for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
//			ProbEqkSource source = erf.getSource(sourceID);
//			if (!cache.isRupInRegion(source, source.getRupture(0), sourceID, 0, region))
//				// source is just for a single rupture, if the first rup isn't in the region none are
//				continue;
//			for (ProbEqkRupture rup : source) {
//				double prob = rup.getProbability();
//				double mag = rup.getMag();
//				populateProbList(mag, prob, probsList, result);
//			}
//		}
//		
//		// now sum the probabilities as:
//		calcSummedProbs(probsList, result);
//		return result;
	}
	
	
	
	public static void testNuclVsPartMFDsInRegion(Region region) {
		
		// new CaliforniaRegions.SF_BOX()
		
		ArrayList<XY_DataSet> funcsIncr = new ArrayList<XY_DataSet>();
		ArrayList<XY_DataSet> funcsCum = new ArrayList<XY_DataSet>();
		
//		MeanUCERF3 erf = new MeanUCERF3();
//		erf.setMeanParams(10d, true, 0d, MeanUCERF3.RAKE_BASIS_MEAN);
//		System.out.println(erf.getAdjustableParameterList().toString());
//		erf.updateForecast();
		
		System.out.println("Instantiating ERF...");
		// average solution for FM 3.1
		String f ="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
		File file = new File(f);
		FaultSystemSolution meanSol;
		try {
			meanSol = FaultSystemIO.loadSol(file);
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(meanSol);
//		erf.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(false);
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
		erf.getParameter("Treat Background Seismicity As").setValue(BackgroundRupType.POINT);
		System.out.println("ADJUSTABLE PARAM SETTINGS:\n"+erf.getAdjustableParameterList().toString());
		erf.updateForecast();

		SummedMagFreqDist nuclMFD_U3 = ERF_Calculator.getMagFreqDistInRegionFaster(erf, region, 5.05, 40, 0.1, true);
		SummedMagFreqDist partMFD_U3 = ERF_Calculator.getParticipationMagFreqDistInRegion(erf, region, 5.05, 40, 0.1, true);
		nuclMFD_U3.setName("UCERF3 Nucleation MFD");
		partMFD_U3.setName("UCERF3 Participation MFD");

		// Now mean UCERF2
		MeanUCERF2 erfU2= new MeanUCERF2();
		erfU2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		erfU2.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
		erfU2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
		erfU2.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
		erfU2.updateForecast();
		SummedMagFreqDist nuclMFD_U2 = ERF_Calculator.getMagFreqDistInRegionFaster(erfU2, region, 5.05, 40, 0.1, true);
		SummedMagFreqDist partMFD_U2 = ERF_Calculator.getParticipationMagFreqDistInRegion(erfU2, region, 5.05, 40, 0.1, true);
		nuclMFD_U2.setName("UCERF2 Nucleation MFD");
		partMFD_U2.setName("UCERF2 Participation MFD");
		
		funcsIncr.add(nuclMFD_U2);
		funcsIncr.add(partMFD_U2);
		funcsIncr.add(nuclMFD_U3);
		funcsIncr.add(partMFD_U3);

		funcsCum.add(nuclMFD_U2.getCumRateDistWithOffset());
		funcsCum.add(partMFD_U2.getCumRateDistWithOffset());
		funcsCum.add(nuclMFD_U3.getCumRateDistWithOffset());
		funcsCum.add(partMFD_U3.getCumRateDistWithOffset());
		
    	ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, null, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, null, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, null, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, null, 1f, Color.BLUE));


		GraphWindow graphIncr = new GraphWindow(funcsIncr, "Incremental MFDs in "+region.getName(), plotChars); 
		graphIncr.setX_AxisLabel("Magnitude");
		graphIncr.setY_AxisLabel("Rate (per year)");
		graphIncr.setPlotLabelFontSize(18);
		graphIncr.setAxisLabelFontSize(18);
		graphIncr.setTickLabelFontSize(16);
		graphIncr.setX_AxisRange(5, 8.5);
		graphIncr.setY_AxisRange(1e-6, 10);
		graphIncr.setYLog(true);

		GraphWindow graphCum = new GraphWindow(funcsCum, "Cumulative MFDs in "+region.getName(), plotChars); 
		graphCum.setX_AxisLabel("Magnitude");
		graphCum.setY_AxisLabel("Rate (per year)");
		graphCum.setPlotLabelFontSize(18);
		graphCum.setAxisLabelFontSize(18);
		graphCum.setTickLabelFontSize(16);
		graphCum.setX_AxisRange(5, 8.5);
		graphCum.setY_AxisRange(1e-6, 10);
		graphCum.setYLog(true);

	}

	
	public static void plotCumulativeDistOfSubsectionRecurrenceIntervals(FaultSystemSolutionERF erf, boolean wtByMomentRate, File outputPDF_FileName) {
		
		ArbDiscrEmpiricalDistFunc dist = new ArbDiscrEmpiricalDistFunc();
		double[] sectPartRates = erf.getSolution().calcParticRateForAllSects(6, 10);
		double[] sectMoRates=null;
		if(wtByMomentRate)
			sectMoRates = calcMomentRateForAllFaultSections(erf);
		
		for(int i=0; i<sectPartRates.length; i++){
			if(wtByMomentRate)
				dist.set(1.0/sectPartRates[i], sectMoRates[i]);
			else
				dist.set(1.0/sectPartRates[i], 1.0);
		}
		
		ArbitrarilyDiscretizedFunc cumDist = dist.getNormalizedCumDist();
		cumDist.setName("Cumulative Distribution of Section Recurrence Intervals");
		cumDist.setInfo("Num Sections = "+erf.getSolution().getRupSet().getNumSections()+
				"\nFraction at RI=1600 years = "+cumDist.getInterpolatedY(1600)+
				"\nFraction at RI=250 years = "+cumDist.getInterpolatedY(250)+
				"\nFraction at RI=70 years = "+cumDist.getInterpolatedY(70));
		
		DefaultXY_DataSet RI1600_func = new DefaultXY_DataSet();
		RI1600_func.set(1600d,0d);
		RI1600_func.set(1600d,1d);
		
		DefaultXY_DataSet RI250_func = new DefaultXY_DataSet();
		RI250_func.set(250d,0d);
		RI250_func.set(250d,1d);

		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));
		
		ArrayList<XY_DataSet> funcList = new ArrayList<XY_DataSet>();
		funcList.add(cumDist);
		funcList.add(RI1600_func);
		funcList.add(RI250_func);

		GraphWindow graph = new GraphWindow(funcList, "", plotChars);
		graph.setX_AxisRange(10, 100000);
		graph.setY_AxisRange(0, 1);
		graph.setY_AxisLabel("Fraction of Fault Sections");
		graph.setX_AxisLabel("Recurrence Interval (years)");

		graph.setTickLabelFontSize(24);
		graph.setAxisLabelFontSize(28);
		graph.setPlotLabelFontSize(18);
		graph.setXLog(true);
		
		if (outputPDF_FileName != null) {
			// stip out an extention if present
			File dir = outputPDF_FileName.getParentFile();
			String name = outputPDF_FileName.getName();
			if (name.endsWith(".pdf"))
				name = name.substring(0, name.indexOf(".pdf"));
			try {
				graph.saveAsPDF(new File(dir, name+".pdf").getAbsolutePath());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	
	
	public static void testTotSubSeisMFD(FaultSystemSolutionERF erf) {
		GridSourceProvider gridSrcProvider = erf.getGridSourceProvider();
		SummedMagFreqDist mfd1 = new SummedMagFreqDist(2.05,8.95,70);
		for(int i=0;i<gridSrcProvider.getGriddedRegion().getNumLocations(); i++) {
			IncrementalMagFreqDist mfd = gridSrcProvider.getNodeSubSeisMFD(i);
			if(mfd != null)
				mfd1.addIncrementalMagFreqDist(mfd);
//			else {
//				Location loc = gridSrcProvider.getGriddedRegion().getLocation(i);
//				System.out.println("null MFD at node "+i+"; "+loc.toString());
//			}
		}
		mfd1.setName("Total Subseis MFD from grid source provider");
		
		SummedMagFreqDist mfd2 = new SummedMagFreqDist(2.05,8.95,70);
		for(IncrementalMagFreqDist mfd: erf.getSolution().getSubSeismoOnFaultMFD_List()) {
			mfd2.addIncrementalMagFreqDist(mfd);
		}
		mfd1.setName("Total Subseis MFD from InversionFaultSystemSolution");

		ArrayList<IncrementalMagFreqDist> mfdList = new ArrayList<IncrementalMagFreqDist>();
		mfdList.add(mfd1);
		mfdList.add(mfd2);
		
		GraphWindow mfd_Graph = new GraphWindow(mfdList, "Subseis MFD comparison"); 
		mfd_Graph.setX_AxisLabel("Mag");
		mfd_Graph.setY_AxisLabel("Rate");
		mfd_Graph.setYLog(true);
		mfd_Graph.setPlotLabelFontSize(22);
		mfd_Graph.setAxisLabelFontSize(20);
		mfd_Graph.setTickLabelFontSize(18);			

	}
	
	private static Document getFactSheetKML(CSVFile<String> timeDepMeanCSV, CSVFile<String> timeDepMinCSV,
			CSVFile<String> timeDepMaxCSV, CSVFile<String> timeIndepCSV,
			List<CSVFile<String>> timeDepPercentileCSVs, double[] timeDepPercentiles, CSVFile<String> parentMeanCSV,
			List<FaultSectionPrefData> subSects, String title) throws IOException {
		// use floats for prettier printing
		float colorMag = 6.7f;
		int colorMagColumn = 18;
		double duration = 30;
		
		float[] tableMags = { 6.7f, 7f, 7.5f, 8f };
		int[] tableColumns = { 18, 21, 26, 31 };
		
//		int numProbCols = timeDepPercentileCSVs.size()+3;
		int numProbCols = timeDepPercentileCSVs.size()+1;
		Color probColor = new Color(230, 230, 230); // very light gray
		Color gainColor = new Color(230, 200, 200); // very light red
		List<String[]> headers = Lists.newArrayList();
		List<String> firstHeader = Lists.newArrayList("", "30 Year Participation Prob (%)");
//		for (int i=0; i<timeDepPercentileCSVs.size()+2; i++)
		for (int i=0; i<timeDepPercentileCSVs.size(); i++)
			firstHeader.add(col_span_placeholder);
		firstHeader.add("Ratios");
		firstHeader.add(col_span_placeholder);
		headers.add(firstHeader.toArray(new String[0]));
//		List<String> secondHeader = Lists.newArrayList("Mag", "Mean", "Min");
		List<String> secondHeader = Lists.newArrayList("Mag", "<b>Mean</b><sup>1</sup>");
		for (double p : timeDepPercentiles)
			secondHeader.add("p<sub>"+(float)p+"</sub><sup>1</sup>");
//		secondHeader.addAll(Lists.newArrayList("Max", "Gain<sup>2</sup>", "U3/U2<sup>3</sup>"));
		secondHeader.addAll(Lists.newArrayList("Gain<sup>2</sup>", "U3/U2<sup>3</sup>"));
		headers.add(secondHeader.toArray(new String[0]));
		Preconditions.checkState(firstHeader.size() == secondHeader.size());
		
		int nh = headers.size();
		
		Preconditions.checkState(colorMag == Float.parseFloat(timeDepMeanCSV.get(0, colorMagColumn)));
		for (int i=0; i<tableMags.length; i++)
			Preconditions.checkState(tableMags[i] == Float.parseFloat(timeDepMeanCSV.get(0, tableColumns[i])));
		for (int i=0; i<tableMags.length; i++)
			Preconditions.checkState(tableMags[i] == Float.parseFloat(parentMeanCSV.get(0, tableColumns[i])));
		
		List<LocationList> faults = Lists.newArrayList();
		double[] plotValues = new double[subSects.size()];
		List<String> descriptions = Lists.newArrayList();
		
		String gte = "\u2265"; // greather than or equal to symbol
		
		// parent section time dep means
		// map from parent name to row in CSV
		Map<String, Integer> parentNameRowMap = Maps.newHashMap();
		for (int row=1; row<parentMeanCSV.getNumRows(); row++)
			parentNameRowMap.put(parentMeanCSV.get(row, 0), row);
		// now get UCERF2 parents
		Map<Integer, ArbitrarilyDiscretizedFunc> ucerf2ParentMPDs = Maps.newHashMap();
		for (FaultSectionPrefData subSect : subSects) {
			Integer parentID = subSect.getParentSectionId();
			if (ucerf2ParentMPDs.containsKey(parentID))
				continue;
			ArrayList<IncrementalMagFreqDist> u2MFDs =
					UCERF2_Section_TimeDepMFDsCalc.getMeanMinAndMaxMFD(parentID, true, true);
			if (u2MFDs == null)
				continue;
			IncrementalMagFreqDist meanU2MFD = u2MFDs.get(0);
			meanU2MFD.scale(1.0/FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS);
			// cumulative mfd so get the first above
			ArbitrarilyDiscretizedFunc u2Probs = new ArbitrarilyDiscretizedFunc();
			for (double minMag : tableMags)
				u2Probs.set(minMag, calcProbAboveMagFromMFD(meanU2MFD, minMag, duration));
			ucerf2ParentMPDs.put(parentID, u2Probs);
		}
		
		for (int i=0; i<subSects.size(); i++) {
			FaultSectionPrefData subSect = subSects.get(i);
			FaultTrace trace = subSect.getFaultTrace();
			trace.setName(subSect.getName());
			faults.add(trace);
			plotValues[i] = Double.parseDouble(timeDepMeanCSV.get(i+1, colorMagColumn));
			
			String description = "";
			String[][] tableVals = new String[tableMags.length+nh][headers.get(0).length];
			Color[][] tableColors = new Color[tableVals.length][tableVals[0].length];
			for (int h=0; h<nh; h++)
				tableVals[h] = headers.get(h);
			
			ArbitrarilyDiscretizedFunc ucerf2Probs = ucerf2ParentMPDs.get(subSect.getParentSectionId());
			
			for (int j=0; j<tableMags.length; j++) {
				double tdVal = Double.parseDouble(timeDepMeanCSV.get(i+1, tableColumns[j]));
				double minVal = Double.parseDouble(timeDepMinCSV.get(i+1, tableColumns[j]));
				double maxVal = Double.parseDouble(timeDepMaxCSV.get(i+1, tableColumns[j]));
				// TODO TI in min/max?
				double tiVal = Double.parseDouble(timeIndepCSV.get(i+1, tableColumns[j]));
				
				int row = j+nh;
				int col = 0;
				
				tableVals[row][col++] = "M"+gte+tableMags[j];
				tableVals[row][col++] = "<b>"+formattedProb(tdVal)+"</b>";
//				tableVals[row][col++] = formattedProb(minVal);
				for (CSVFile<String> pCSV : timeDepPercentileCSVs)
					tableVals[row][col++] = formattedProb(Double.parseDouble(pCSV.get(i+1, tableColumns[j])));
//				tableVals[row][col++] = formattedProb(maxVal);
				if (tdVal == 0d) {
					tableVals[row][col++] = "-";
					tableVals[row][col++] = "-";
				} else {
					tableVals[row][col++] = formattedGain(tdVal/tiVal);
					if (ucerf2Probs == null) {
						tableVals[row][col++] = formattedGain(Double.NaN);
					} else {
						double u2Prob = ucerf2Probs.getY(tableMags[j]);
						double u3ParentProb = Double.parseDouble(parentMeanCSV.get(
								parentNameRowMap.get(subSect.getParentSectionName()), tableColumns[j]));
						tableVals[row][col++] = formattedGain(u3ParentProb/u2Prob);
					}
				}
			}
			
			for (int row=0; row<tableVals.length; row++) {
				for (int col=1; col<tableVals[row].length; col++) {
					
					if (col - 1 < numProbCols)
						tableColors[row][col] = probColor;
					else
						tableColors[row][col] = gainColor;
				}
			}
//			for (int j=0; j<tableMags.length; j++) {
//				double tdVal = Double.parseDouble(timeDepMeanCSV.get(i+1, tableColumns[j]));
////				double tiVal = Double.parseDouble(timeDepCSV.get(i+1, tableColumns[j]));
//				if (!description.isEmpty())
//					description += "\n<br>";
//				description += "M"+gte+tableMags[j]+": "+df.format(tdVal);
//			}
			// TODO ripeness?
			if (!description.isEmpty())
				description += "<br>\n";
			description += generateHTMLTable(tableVals, tableColors);
			// footnotes
			description += "<br>\n";
			description += "<font size=\"-2\">";
//			description += "<br>1. Mean/Min/Max/Percentiles across all UCERF3 logic tree branches";
			description += "<br>1. Mean and percentiles across all UCERF3 logic tree branches";
			description += "<br>2. Mean time dependent probability gain (to time independent UCERF3)";
			description += "<br>3. Mean UCERF3/UCERF2 probability, averaged over parent fault section";
			description += "</font>";
			descriptions.add(description);
		}
		
		CPT logProbCPT = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-4d, 0d);
		
//		// will hide faults when region of width of 100 km is more than 3500 pixels
//		double bufferRegionKM = 100d;
//		int bufferMaxPixels = 3500;
		
		double bufferRegionKM = -1d;
		int bufferMaxPixels = 3500;
		
		Document doc = FaultBasedMapGen.getFaultKML(logProbCPT, faults, FaultBasedMapGen.log10(plotValues),
				false, 40, 4, "UCERF3 Mean Time Dep Prob", descriptions, bufferRegionKM, bufferMaxPixels);
		
		doc.getRootElement().element("Folder").element("name").setText(title);
		
		return doc;
	}

	private static void writeFactSheetKMZ(File outputFile, Document... docs) throws IOException {
		Document doc = docs[0];
		// now add legend
		Element root = doc.getRootElement();
		
		// add other docs
		for (int i=1; i<docs.length; i++) {
			Element el = docs[i].getRootElement().element("Folder");
			docs[i].getRootElement().remove(el);
			// deselect by default
			el.addElement("visibility").setText("0");
			// must do to all children as well...annoying
			List<Element> placemarkEls = Lists.newArrayList(el.elementIterator("Placemark"));
			for (Element placemarkEl : placemarkEls)
				placemarkEl.addElement("visibility").setText("0");
			// add to main doc
			root.add(el);
		}
		
		// add overlay
		Element overlayEl = root.addElement("ScreenOverlay");
		overlayEl.addElement("name").setText("Legend");
		overlayEl.addElement("Icon").addElement("href").setText("partic_legend.png");
		Element overlayXYEl = overlayEl.addElement("overlayXY");
		overlayXYEl.addAttribute("x", "0.05");
		overlayXYEl.addAttribute("y", "0.92");
		overlayXYEl.addAttribute("xunits", "fraction");
		overlayXYEl.addAttribute("yunits", "fraction");
		Element screenXYEl = overlayEl.addElement("screenXY");
		screenXYEl.addAttribute("x", "0.05");
		screenXYEl.addAttribute("y", "0.92");
		screenXYEl.addAttribute("xunits", "fraction");
		screenXYEl.addAttribute("yunits", "fraction");
		
		XMLUtils.writeDocumentToFile(outputFile, doc);
		
		// now make kmz
		String fName = outputFile.getName().replaceAll(".kml", "")+".kmz";
		File zipFile = new File(outputFile.getParentFile(), fName);
		ArrayList<String> zipFiles = Lists.newArrayList(outputFile.getName(), "partic_legend.png");
		FileUtils.createZipFile(zipFile.getAbsolutePath(),
				outputFile.getParentFile().getAbsolutePath(), zipFiles);
	}
	
	private static final DecimalFormat kmlProbDF = new DecimalFormat("0.00%");
	private static final String kmlBelowMinStr = "< 0.01%";
	private static final DecimalFormat kmlGainDF = new DecimalFormat("0.00");
//	private static final String kmlInfGainStr = "\u221E";
	private static final String kmlInfGainStr = "-";
	private static final double kmlMinProb = 0.01/100d;
	private static String formattedProb(double val) {
		String str;
		if (val == 0d)
			str = "-";
		else if (val > 0 && val < kmlMinProb)
			str = kmlBelowMinStr;
		else
			str = kmlProbDF.format(val);
		str = str.replaceAll("%", "");
		return str;
	}
	private static String formattedGain(double val) {
		String str;
		if (!Doubles.isFinite(val))
			str = kmlInfGainStr;
		else
			str = kmlGainDF.format(val);
		return str;
	}
	
	private static String col_span_placeholder = "!COLSPAN!";
	
	private static String generateHTMLTable(String[][] values, Color[][] colors) {
		StringBuilder str = new StringBuilder();
		str.append("<table cellpadding=\"5\">\n");
		for (int row=0; row<values.length; row++) {
			str.append("\t<tr>\n");
			for (int col=0; col<values[row].length; col++) {
				String colSpanStr = "";
				if (values[row][col].equals(col_span_placeholder))
					continue;
				int colSpans = 1;
				for (int col2=col+1; col2<values[row].length; col2++) {
					if (values[row][col2].equals(col_span_placeholder))
						colSpans++;
					else
						break;
				}
				if (colSpans > 1)
					colSpanStr = " colspan=\""+colSpans+"\"";
				String colorStr = "";
				if (colors[row][col] != null)
					colorStr = " bgcolor=\""+"#" + Integer.toHexString(colors[row][col].getRGB() & 0x00ffffff)+"\"";
				if (row == 0 || col == 0)
					str.append("\t\t<th"+colSpanStr+colorStr+">").append(values[row][col]).append("</th>\n");
				else
					str.append("\t\t<td"+colSpanStr+colorStr+">").append(values[row][col]).append("</td>\n");
			}
			str.append("\t</tr>\n");
		}
		str.append("</table>");
		return str.toString();
	}
	
	/**
	 * This generates the sub section probability tables for the UCERF3 fact sheet. Data files used as input are from
	 * http://opensha.usc.edu/ftp/kmilner/ucerf3/2013_05_10-ucerf3p3-production-10runs/erf_mag_prob_plots/30yr/
	 * 
	 * U3/U2 ratios are done on the parent section level.
	 * 
	 * @param fm31_td_mean_csv
	 * @param fm31_ti_mean_csv
	 * @param fm31_parent_csv
	 * @param fm31_sub_sects
	 * @param fm32_td_mean_csv
	 * @param fm32_ti_mean_csv
	 * @param fm32_parent_csv
	 * @param fm32_sub_sects
	 */
	private static void calcFactSheetSubSectTableVals(
			CSVFile<String> fm31_td_mean_csv, CSVFile<String> fm31_ti_mean_csv,
			CSVFile<String> fm31_parent_csv, List<FaultSectionPrefData> fm31_sub_sects,
			CSVFile<String> fm32_td_mean_csv, CSVFile<String> fm32_ti_mean_csv,
			CSVFile<String> fm32_parent_csv, List<FaultSectionPrefData> fm32_sub_sects) {
		List<String> names = Lists.newArrayList();
		names.add("San Andreas (Mojave S), Subsection 7");
		names.add("San Andreas (Peninsula) 2011 CFM, Subsection 8");
		names.add("Hayward (No) 2011 CFM, Subsection 4");
		names.add("Calaveras (No) 2011 CFM, Subsection 3");
		names.add("San Jacinto (San Bernardino), Subsection 4");
		names.add("Elsinore (Glen Ivy) rev, Subsection 1");
		
		float[] tableMags = { 6.7f, 7.5f, 8f };
		int[] tableColumns = { 18, 26, 31 };
		
		for (String name : names) {
			System.out.println(name);
			double ripeness = -1;
			for (int i=0; i<tableMags.length; i++) {
				float mag = tableMags[i];
				int col = tableColumns[i];
				
				double[] fm31_vals = calcFactSheetVals(name, col, mag, fm31_td_mean_csv, fm31_ti_mean_csv,
						fm31_parent_csv, fm31_sub_sects);
				double[] fm32_vals = calcFactSheetVals(name, col, mag, fm32_td_mean_csv, fm32_ti_mean_csv,
						fm32_parent_csv, fm32_sub_sects);
				
				double avgTD = 0.5*fm31_vals[0] + 0.5*fm32_vals[0];
				if (i == 0)
					ripeness = 0.5*fm31_vals[1] + 0.5*fm32_vals[1];
				double avgU3_gain = 0.5*fm31_vals[2] + 0.5*fm32_vals[2];
				System.out.println("\t"+mag+": "+formattedProb(avgTD)+" ("+formattedGain(avgU3_gain)+")");
			}
			System.out.println("\tRipeness: "+formattedGain(ripeness));
		}
	}
	
	/**
	 * Calculate subsection values for a specific fault model. Array of returned values is: U3 Time Dep Prob, U3 TD/TI gain,
	 * U3/U2 parent sect gain
	 * 
	 * @param subSectName
	 * @param col
	 * @param mag
	 * @param td_mean_csv
	 * @param ti_mean_csv
	 * @param parent_csv
	 * @param sub_sects
	 * @return
	 */
	private static double[] calcFactSheetVals(String subSectName, int col, double mag, CSVFile<String> td_mean_csv,
			CSVFile<String> ti_mean_csv, CSVFile<String> parent_csv, List<FaultSectionPrefData> sub_sects) {
		FaultSectionPrefData s = null;
		for (FaultSectionPrefData subSect : sub_sects) {
			if (subSect.getName().equals(subSectName)) {
				s = subSect;
				break;
			}
		}
		Preconditions.checkNotNull(s);
		
		int row = s.getSectionId()+1;
		
		double tdVal = Double.parseDouble(td_mean_csv.get(row, col));
		double tiVal = Double.parseDouble(ti_mean_csv.get(row, col));
		double tdGain = tdVal/tiVal;
		
		// now parent sect U3/U2
		double u3Gain = -1d;
		for (row=1; row<parent_csv.getNumRows(); row++) {
			if (parent_csv.get(row, 0).equals(s.getParentSectionName())) {
				double u3Val = Double.parseDouble(parent_csv.get(row, col));
				
				ArrayList<IncrementalMagFreqDist> u2MFDs =
						UCERF2_Section_TimeDepMFDsCalc.getMeanMinAndMaxMFD(s.getParentSectionId(), true, true);
				if (u2MFDs == null)
					continue;
				IncrementalMagFreqDist meanU2MFD = u2MFDs.get(0);
				meanU2MFD.scale(1.0/FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS);
				double u2Val = calcProbAboveMagFromMFD(meanU2MFD, mag, 30d);
				u3Gain = u3Val / u2Val;
				
				break;
			}
		}
		Preconditions.checkState(u3Gain != -1d);
		
		return new double[] { tdVal, tdGain, u3Gain };
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		
//		File invDir = new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/");
//		
//		List<FaultSectionPrefData> subSects_3_1 = FaultSystemIO.loadRupSet(new File(invDir,
//				"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"))
//				.getFaultSectionDataList();
//		List<FaultSectionPrefData> subSects_3_2 = FaultSystemIO.loadRupSet(new File(invDir,
//				"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_MEAN_BRANCH_AVG_SOL.zip"))
//				.getFaultSectionDataList();
//		File pressReleaseDir = new File("/home/kevin/OpenSHA/UCERF3/press_release/");
//		File outputFile = new File(pressReleaseDir, "ucerf3_timedep_30yr_probs.kml");
//		Document fm3_1Doc = getFactSheetKML(
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_1_30yr_sub_sect_probs_u3_td_mean.csv"), true),
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_1_30yr_sub_sect_probs_u3_td_min.csv"), true),
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_1_30yr_sub_sect_probs_u3_td_max.csv"), true),
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_1_30yr_sub_sect_probs_u3_poisson_mean.csv"), true),
//						Lists.newArrayList(CSVFile.readFile(new File(pressReleaseDir,
//								"FM3_1_30yr_sub_sect_probs_u3_td_p2.5.csv"), true),
//								CSVFile.readFile(new File(pressReleaseDir,
//										"FM3_1_30yr_sub_sect_probs_u3_td_p97.5.csv"), true)),
//										new double[] { 2.5d, 97.5d },
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_1_30yr_parent_sect_probs_u3_td_mean.csv"), true),
//				subSects_3_1, "Fault Model 3.1");
//		Document fm3_2Doc = getFactSheetKML(
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_2_30yr_sub_sect_probs_u3_td_mean.csv"), true),
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_2_30yr_sub_sect_probs_u3_td_min.csv"), true),
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_2_30yr_sub_sect_probs_u3_td_max.csv"), true),
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_2_30yr_sub_sect_probs_u3_poisson_mean.csv"), true),
//						Lists.newArrayList(CSVFile.readFile(new File(pressReleaseDir,
//								"FM3_2_30yr_sub_sect_probs_u3_td_p2.5.csv"), true),
//								CSVFile.readFile(new File(pressReleaseDir,
//										"FM3_2_30yr_sub_sect_probs_u3_td_p97.5.csv"), true)),
//										new double[] { 2.5d, 97.5d },
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_2_30yr_parent_sect_probs_u3_td_mean.csv"), true),
//				subSects_3_2, "Fault Model 3.2");
//		writeFactSheetKMZ(outputFile, fm3_1Doc, fm3_2Doc);
//		
//		
//		// now the table
//		calcFactSheetSubSectTableVals(
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_1_30yr_sub_sect_probs_u3_td_mean.csv"), true),
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_1_30yr_sub_sect_probs_u3_poisson_mean.csv"), true),
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_1_30yr_parent_sect_probs_u3_td_mean.csv"), true),
//				subSects_3_1,
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_2_30yr_sub_sect_probs_u3_td_mean.csv"), true),
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_2_30yr_sub_sect_probs_u3_poisson_mean.csv"), true),
//				CSVFile.readFile(new File(pressReleaseDir, "FM3_2_30yr_parent_sect_probs_u3_td_mean.csv"), true),
//				subSects_3_2);
//		
//		System.exit(0);
		
//		makeNucleationRateMapForU3pt3(7.0, true, 0.12);
		
//		makeAveMoRateMapForU3pt3(true);
		
//		makeIconicFigureForU3TimeDependence();
//		System.exit(0);
//		System.out.println(loadUCERF2MainFaultMPDs(true).size());
//		System.exit(0);
//		writeOpenIntTableComparisons();
//		System.exit(0);
//		testAftershockInclusionOnProbs();
		
//		try {
//			Thread.sleep(15*60*1000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		writeDiffAveragingMethodsRupProbGains();
//		writeDiffAveragingMethodsSubSectionTimeDependenceCSV(new File("SubSectProbData_HistOpenInt139"), 1875);	// 139 = 2014-1875
//		writeDiffAveragingMethodsSubSectionTimeDependenceCSV(new File("SubSectProbData_HistOpenInt0"), 2014);
//		writeDiffAveragingMethodsRupProbGains(1837);	// Mojave section
//		writeDiffAveragingMethodsRupProbGains(1486);
//		writeDiffAveragingMethodsRupProbGains(1840, 1875); // "San Andreas (Mojave S), Subsection 3"	1840
//		writeDiffAveragingMethodsRupProbGains(2568, 1875); //  subsection #2568 is "White Wolf, Subsection 0
//		writeDiffAveragingMethodsRupProbGains(1919, 1875); // San Andreas (Offshore) 2011 CFM, Subsection 23	1919

//		testAveragingMethodsForProbMaps(2014);
//		testAveragingMethodsForProbMaps(1875);
//		testHistOpenIntervalFaultProbMaps();

//		makeWG02_FaultProbMaps();
		
//		testProbSumMethods();
//		System.exit(0);
//		loadBranchFaultCSVVals(new File("/home/kevin/OpenSHA/UCERF3/probGains/"
//				+ "2013_12_03-ucerf3-prob-gains-main-30yr/aveRI_aveNTS.zip"), new int[] { 0, 1, 3 }, null);
//		System.exit(0);
//		debugAvgMethods();
//		System.exit(0);
		
//		String dirPrefix = "/home/kevin/OpenSHA/UCERF3/probGains/2013_12_14-ucerf3-prob-gains-open1875";
		String dirPrefix = "/home/kevin/OpenSHA/UCERF3/probGains/2014_02_15-ucerf3-prob-gains-open1875";
//		File outputMainDir = new File("/home/kevin/OpenSHA/UCERF3");
		FaultSystemSolution meanSol = FaultSystemIO.loadSol(
				new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
						"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
//		String dirPrefix = "/home/scec-02/kmilner/ucerf3/prob_gains/2013_12_24-ucerf3-prob-gains-open1875";
//		File outputMainDir = new File("/var/www/html/ftp/kmilner/ucerf3");
		File outputMainDir = new File("/tmp/asdf");
//		FaultSystemSolution meanSol = FaultSystemIO.loadSol(
//				new File("/home/scec-02/kmilner/ucerf3/inversion_compound_plots/"
//						+ "2013_05_10-ucerf3p3-production-10runs/2013_05_10-ucerf3p3-production-10runs_"
//						+ "COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		// just do metadata and such
//		doFinalWebPlotAssembly(new File(outputMainDir, "TimeDependent_AVE_RATE_AVE_NORM_TIME_SINCE"), false);
//		doFinalWebPlotAssembly(new File(outputMainDir, "TimeDependent_AVE_RI_AVE_NORM_TIME_SINCE"), false);
//		doFinalWebPlotAssembly(new File(outputMainDir, "TimeDependent_AVE_RI_AVE_TIME_SINCE"), false);
//		doFinalWebPlotAssembly(new File(outputMainDir, "TimeDependent_AVE_ALL"), true);
//		System.exit(0);
		
		// each individually
		// default
//		writeTimeDepPlotsForWeb(Lists.newArrayList(BPTAveragingTypeOptions.AVE_RATE_AVE_NORM_TIME_SINCE), true,
//				dirPrefix, new File(outputMainDir, "TimeDependent_AVE_RATE_AVE_NORM_TIME_SINCE"), meanSol);
		writeTimeDepPlotsForWeb(Lists.newArrayList(BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE), true,
				dirPrefix, new File(outputMainDir, "TimeDependent_AVE_RI_AVE_NORM_TIME_SINCE"), meanSol);
//		writeTimeDepPlotsForWeb(Lists.newArrayList(BPTAveragingTypeOptions.AVE_RI_AVE_TIME_SINCE), true,
//				dirPrefix, new File(outputMainDir, "TimeDependent_AVE_RI_AVE_TIME_SINCE"), meanSol);
		// do all of them including avg sensitivity plots
//		writeTimeDepPlotsForWeb(Lists.newArrayList(BPTAveragingTypeOptions.values()), false,
//				dirPrefix, new File(outputMainDir, "TimeDependent_AVE_ALL"), meanSol);
//		doFinalWebPlotAssembly(new File(outputMainDir, "TimeDependent_AVE_ALL"), true);
//		System.exit(0);
		
//		File zipsDir = new File("/home/kevin/OpenSHA/UCERF3/probGains/2013_11_21-ucerf3-prob-gains-5yr");
//		String opsStr = "aveRI_aveNTS";
//		File branchOutput = new File(z ipsDir, opsStr);
//		if (!branchOutput.exists())
//			branchOutput.mkdir();
//		File zipFile = new File(zipsDir, opsStr+".zip");
		
//		Map<MagDependentAperiodicityOptions, Map<LogicTreeBranch, double[]>> vals =
//				loadBranchCSVVals(zipFile, 0, 4, true, null);
//		writeBranchAggregatedTimeDepFigs(zipFile, branchOutput, false, 0, null);
//		writeBranchAggregatedTimeDepFigs(zipFile, branchOutput, true, 0, null);
//		System.exit(0);

//		scratch.UCERF3.utils.RELM_RegionUtils.printNumberOfGridNodes();
		
//		writeDiffAveragingMethodsRupProbGains();
//		System.exit(0);
		
//		plot_U3pt3_U2_TotalMeanMFDs();
		
//		makeIconicFigureForU3pt3_and_FM3pt1();
				
//		makeUCERF2_PartRateMaps();
		
//		testUCERF2_Figure25();
		
//		makeDraftFinalModelReportPartPlots();
		
//		plotMFD_InRegion(new CaliforniaRegions.NORTHRIDGE_BOX(), "Northridge_BoxMFDs.pdf");
//		plotMFD_InRegion(new CaliforniaRegions.SF_BOX(), "SF_BoxMFDs.pdf");
//		plotMFD_InRegion(new CaliforniaRegions.LA_BOX(), "LA_BoxMFDs.pdf");
		
//		makePrelimReportPartPlots();
		
		
//		File file = new File("/Users/field/Neds_Creations/CEA_WGCEP/UCERF3/PrelimModelReport/Figures/Fig16_ERF_ParticipationMaps/zipFiles/FM3_1_GLpABM_MaEllB_DsrTap_DrEllB_Char_VarAseis0.1_VarOffAseis0.5_VarMFDMod1_VarNone_sol.zip");
//		SimpleFaultSystemSolution tmp = null;
//		try {
//			tmp =  SimpleFaultSystemSolution.fromFile(file);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		System.out.println("numRups="+tmp.getNumRuptures());
//		System.out.println("numSect="+tmp.getNumSections());
		
		
		
//		FaultSystemSolution meanSol = FaultSystemIO.loadSol(
//				new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
//						"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		
//		double duration = 30d;
//		String durStr = (int)duration+"yr";
//		
//		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(meanSol);
//		erf.getTimeSpan().setDuration(duration);
		
		// this will generate the prob gain mains for BPT/Poisson
//		File saveDir = new File("/tmp/prob_maps");
//		if (!saveDir.exists())
//			saveDir.mkdir();
//		makeFaultProbGainMaps(meanSol, saveDir, "ucerf3_ca", false, 30d);
		
		// this will write the mag/duration/prob XYZ files for various CA regions
//		File saveDir = new File("/tmp/prob_xyzs");
//		if (!saveDir.exists())
//			saveDir.mkdir();
//		makeMagDurationProbPlots(meanSol, saveDir, "ucerf3", false);
		
//		MagDependentAperiodicityOptions[] covFuncs = { MagDependentAperiodicityOptions.LOW_VALUES,
//				MagDependentAperiodicityOptions.MID_VALUES, MagDependentAperiodicityOptions.HIGH_VALUES,
//				MagDependentAperiodicityOptions.ALL_PT3_VALUES, MagDependentAperiodicityOptions.ALL_PT4_VALUES,
//				MagDependentAperiodicityOptions.ALL_PT5_VALUES };
//		
//		File saveDir = new File("/tmp/ucerf3_time_dep_erf_plots");
//		if (!saveDir.exists())
//			saveDir.mkdir();
		
//		writeDiffAveragingMethodsSubSectionTimeDependenceCSV(saveDir);
//		System.exit(0);
		
//		for (MagDependentAperiodicityOptions cov : covFuncs) {
//			String dirName = "cov_"+cov.name();
//			File covSaveDir = new File(saveDir, dirName);
//			if (!covSaveDir.exists())
//				covSaveDir.mkdir();
//			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
//			erf.setParameter(MagDependentAperiodicityParam.NAME, cov);
//			erf.setParameter(HistoricOpenIntervalParam.NAME, (double)(FaultSystemSolutionERF.START_TIME_DEFAULT-1875));
//			erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
//			// this will write the parent section CSV file
//			writeParentSectionTimeDependenceCSV(erf, new File(covSaveDir, dirName+"_parent_probs_"+durStr+".csv"));
//			writeSubSectionTimeDependenceCSV(erf, new File(covSaveDir, dirName+"_sub_probs_"+durStr+".csv"));
//			
//			File fltGainDir = new File(covSaveDir, "fault_prob_gains");
//			if (!fltGainDir.exists())
//				fltGainDir.mkdir();
//			
//			makeFaultProbGainMaps(erf, fltGainDir, dirName);
//
//			// this will generate prob gain maps for BPT parameters
//			File bptGainDir = new File(covSaveDir, "bpt_calc_prob_gains");
//			if (!bptGainDir.exists())
//				bptGainDir.mkdir();
//			makeAvgMethodProbGainMaps(erf, bptGainDir, dirName);
//		}
		
//		double minMag = 6.5;
//		double deltaMag = 0.1;
//		int numMag = 21;
//		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(meanSol);
//		erf.getTimeSpan().setDuration(30d);
//		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
//		erf.updateForecast();
//		FSSRupsInRegionCache cache = new FSSRupsInRegionCache(erf);
//		Region region = new CaliforniaRegions.RELM_SOCAL();
//		// preload the cache so that timing comparisons are fair
//		System.out.println("Preloading cache");
//		Stopwatch watch = new Stopwatch();
//		watch.start();
//		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
//			ProbEqkSource source = erf.getSource(sourceID);
//			cache.isRupInRegion(source, source.getRupture(0), sourceID, 0, region);
//		}
//		watch.stop();
//		System.out.println("Done. Took "+(watch.elapsed(TimeUnit.MILLISECONDS)/1000f)+"s");
//		watch.reset();
//		watch.start();
//		System.out.println("Calculating indep MFD");
//		EvenlyDiscretizedFunc indepProbsMFD = calcMagProbDist(erf, region, minMag, numMag, deltaMag, true, cache);
//		watch.stop();
//		System.out.println("Took "+(watch.elapsed(TimeUnit.MILLISECONDS)/1000f)+"s");
//		watch.reset();
//		watch.start();
//		System.out.println("Calculating indep Sum");
//		EvenlyDiscretizedFunc indepProbsSum = calcMagProbDist(erf, region, minMag, numMag, deltaMag, false, cache);
//		watch.stop();
//		System.out.println("Took "+(watch.elapsed(TimeUnit.MILLISECONDS)/1000f)+"s");
//		watch.reset();
//		watch.start();
//		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
//		erf.updateForecast();
//		System.out.println("Calculating dep MFD");
//		EvenlyDiscretizedFunc depProbsMFD = calcMagProbDist(erf, region, minMag, numMag, deltaMag, true, cache);
//		watch.stop();
//		System.out.println("Took "+(watch.elapsed(TimeUnit.MILLISECONDS)/1000f)+"s");
//		watch.reset();
//		watch.start();
//		System.out.println("Calculating dep Sum");
//		EvenlyDiscretizedFunc depProbsSum = calcMagProbDist(erf, region, minMag, numMag, deltaMag, false, cache);
//		watch.stop();
//		System.out.println("Took "+(watch.elapsed(TimeUnit.MILLISECONDS)/1000f)+"s");
//		watch.reset();
//		watch.start();
//		List<DiscretizedFunc> funcs = Lists.newArrayList();
//		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
//		funcs.add(indepProbsMFD);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
//		funcs.add(indepProbsSum);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.GRAY));
//		funcs.add(depProbsMFD);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
//		funcs.add(depProbsSum);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.CYAN));
//		
//		new GraphWindow(funcs, "30 Year So Cal Probabilities", chars);
	}

}
