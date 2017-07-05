package scratch.UCERF3.analysis;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;

import mpi.MPI;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.jfree.data.Range;
import org.opensha.commons.calc.FractileCurveCalculator;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.AbstractXY_DataSet;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
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
import org.opensha.commons.hpc.mpj.taskDispatch.MPJTaskCalculator;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.DeadlockDetectionThread;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.threads.Task;
import org.opensha.commons.util.threads.ThreadedTaskComputer;
import org.opensha.nshmp2.util.Period;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.hazardMap.BinaryHazardCurveReader;
import org.opensha.sha.calc.hazardMap.HazardDataSetLoader;
import org.opensha.sha.calc.hazardMap.components.BinaryCurveArchiver;
import org.opensha.sha.calc.hazardMap.components.CurveMetadata;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;
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
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.RupInRegionCache;
import org.opensha.sha.faultSurface.RupNodesCache;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.AverageFaultSystemSolution;
import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.FaultSystemSolutionFetcher;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.erf.FSSRupsInRegionCache;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.griddedSeismicity.GridSourceFileReader;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.BatchPlotGen;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.UCERF2_ComparisonSolutionFetcher;
import scratch.UCERF3.inversion.laughTest.LaughTestFilter;
import scratch.UCERF3.logicTree.APrioriBranchWeightProvider;
import scratch.UCERF3.logicTree.BranchWeightProvider;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.logicTree.VariableLogicTreeBranch;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.DeformationModelFileParser;
import scratch.UCERF3.utils.DeformationModelFileParser.DeformationSection;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.GardnerKnopoffAftershockFilter;
import scratch.UCERF3.utils.IDPairing;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.UCERF2_MFD_ConstraintFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.UCERF2_Section_MFDs.UCERF2_Section_MFDsCalc;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoFitPlotter;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoFitPlotter.DataForPaleoFaultPlots;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoSiteCorrelationData;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoRateConstraintFetcher;
import scratch.kevin.ucerf3.TestPDFCombine;
import scratch.kevin.ucerf3.inversion.MiniSectRecurrenceGen;
import scratch.peter.ucerf3.calc.UC3_CalcUtils;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * This class contains plotting code which can be run in parallel to show the mean (and often min/max and fractiles)
 * across multiple logic tree branches. Each plot is a subclass of the CompoundFSSPlots abstract class. Calculations
 * can be done multithreaded on one machines via the batchPlot method, or in parallel via the
 * MPJDistributedCompoundFSSPlots class. Here is the order of operations:
 * 
 * First load in a CompoundFaultSystemSolution file. Then the following will be called for each plot:
 * 
 * // Instantiate InversionFaultSystemSolution instance for branch if not already loaded
 * if (plot.usesERFs()) {
 * 	// instantiate ERF if necessary
 * 	// make sure ERF parameters (gardner knopoff) set correctly
 * 	
 * 	// finally process the erf
 * 	plot.processERF(branch, erf);
 * } else {
 *	// process the solution
 *	plot.processSolution(branch, solution);
 * }
 * // if we're running in MPJ, load instances from other nodes and then call
 * plot.combineDistributedCalcs(otherPlots);
 * // then finalize it
 * plot.finalizePlot()
 * 
 * @author kevin
 *
 */
public abstract class CompoundFSSPlots implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final Color BROWN = new Color(130, 86, 5);
	
	static double[] time_dep_durations = {30d, 5d};
	
	/*
	 * Regional MFD plots
	 */

	/**
	 * calculates and writes MFD plots
	 * @param fetch
	 * @param weightProvider
	 * @param regions
	 * @param dir
	 * @param prefix
	 * @throws IOException
	 */
	public static void writeRegionalMFDPlots(FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, List<Region> regions,
			File dir, String prefix) throws IOException {
		
		RegionalMFDPlot plot = new RegionalMFDPlot(weightProvider, regions);

		plot.buildPlot(fetch);

		writeRegionalMFDPlots(plot.specs, plot.cumulative_specs, regions, dir, prefix);
	}

	/**
	 * Writes already computed plots to files
	 * @param specs
	 * @param cumulative_specs
	 * @param regions
	 * @param dir
	 * @param prefix
	 * @throws IOException
	 */
	public static void writeRegionalMFDPlots(List<PlotSpec> specs, List<PlotSpec> cumulative_specs,
			List<Region> regions, File dir, String prefix) throws IOException {
		
		File subDir = new File(dir, "fss_mfd_plots");
		if (!subDir.exists())
			subDir.mkdir();

		int unnamedRegionCnt = 0;

		for (int i = 0; i < regions.size(); i++) {
			for (boolean cumulative : RegionalMFDPlot.cumulatives) {
				PlotSpec spec;
				if (cumulative)
					spec = cumulative_specs.get(i);
				else
					spec = specs.get(i);
				Region region = regions.get(i);

				HeadlessGraphPanel gp = new HeadlessGraphPanel();
				CommandLineInversionRunner.setFontSizes(gp);
				gp.setYLog(true);
				if (cumulative)
					gp.setUserBounds(5d, 9d, 1e-5, 1e1);
				else
					gp.setUserBounds(5d, 9d, 3e-6, 3e0);

				gp.drawGraphPanel(spec);

				String fname = prefix;
				if (cumulative)
					fname += "_CUMULATIVE_MFD";
				else
					fname += "_MFD";
				if (region.getName() != null && !region.getName().isEmpty())
					fname += "_" + region.getName().replaceAll("\\W+", "_");
				else
					fname += "_UNNAMED_REGION_" + (++unnamedRegionCnt);

				File file = new File(subDir, fname);
				gp.getChartPanel().setSize(1000, 800);
				gp.saveAsPDF(file.getAbsolutePath() + ".pdf");
				gp.saveAsPNG(file.getAbsolutePath() + ".png");
				gp.saveAsTXT(file.getAbsolutePath() + ".txt");
				File smallDir = new File(dir, "small_MFD_plots");
				if (!smallDir.exists())
					smallDir.mkdir();
				file = new File(smallDir, fname + "_small");
				gp.getChartPanel().setSize(500, 400);
				gp.saveAsPDF(file.getAbsolutePath()+".pdf");
				gp.saveAsPNG(file.getAbsolutePath()+".png");
			}
		}
	}

	/**
	 * This creates MFD plots for a range of solutions/regions. These use InversionFaultSystemSolution instances
	 * and not the full ERF.
	 * 
	 * @author kevin
	 * 
	 */
	public static class RegionalMFDPlot extends CompoundFSSPlots {

		public static List<Region> getDefaultRegions() {
			List<Region> regions = Lists.newArrayList();
			regions.add(new CaliforniaRegions.RELM_TESTING());
			regions.add(new CaliforniaRegions.RELM_NOCAL());
			regions.add(new CaliforniaRegions.RELM_SOCAL());
			regions.add(new CaliforniaRegions.LA_BOX());
			regions.add(new CaliforniaRegions.SF_BOX());
			regions.add(new CaliforniaRegions.NORTHRIDGE_BOX());
			return regions;
		}

		private transient BranchWeightProvider weightProvider;
		private List<Region> regions;
		private List<Double> weights;

		// none (except min/mean/max which are always included)
		private double[] fractiles;

		// these are organized as (region, solution)
		private List<XY_DataSetList> solMFDs;
		private List<XY_DataSetList> solOffMFDs;
		private List<XY_DataSetList> solTotalMFDs;

		private static final double minX = 5.05d;
		private static final double maxX = 9.05d;
		private static final double delta = 0.1d;

		private List<PlotSpec> specs;
		private List<PlotSpec> cumulative_specs;
		
		private static final boolean[] cumulatives = { false, true };

		public RegionalMFDPlot(BranchWeightProvider weightProvider,
				List<Region> regions) {
			this(weightProvider, regions, new double[0]);
		}

		public RegionalMFDPlot(BranchWeightProvider weightProvider,
				List<Region> regions, double[] fractiles) {
			this.weightProvider = weightProvider;
			this.regions = regions;
			this.fractiles = fractiles;

			solMFDs = Lists.newArrayList();
			solOffMFDs = Lists.newArrayList();
			solTotalMFDs = Lists.newArrayList();

			for (int i = 0; i < regions.size(); i++) {
				solMFDs.add(new XY_DataSetList());
				solOffMFDs.add(new XY_DataSetList());
				solTotalMFDs.add(new XY_DataSetList());
			}

			weights = Lists.newArrayList();
		}

		private static boolean isStatewide(Region region) {
			// TODO dirty...
			return region.getName().startsWith("RELM_TESTING");
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			double wt = weightProvider.getWeight(branch);

			// on fault, off fault, and total MFDs for each region
			List<IncrementalMagFreqDist> onMFDs = Lists.newArrayList();
			// off is the total gridded seis MFD (truly off and subseismogenic)
			// off and total only used for statewide MFDs
			List<IncrementalMagFreqDist> offMFDs = Lists.newArrayList();
			List<IncrementalMagFreqDist> totMFDs = Lists.newArrayList();

			for (int i = 0; i < regions.size(); i++) {
				debug(solIndex, "calculating region " + i);
				Region region = regions.get(i);

				IncrementalMagFreqDist onMFD = sol.calcNucleationMFD_forRegion(
						region, minX, maxX, delta, true);
				onMFDs.add(onMFD);
				if (isStatewide(region)) {
					// we only have off fault for statewide
					IncrementalMagFreqDist offMFD = sol.getFinalTotalGriddedSeisMFD();
//					// TODO REMOVE
//					offMFD = sol.getRupSet().getInversionTargetMFDs().getTotalGriddedSeisMFD();
					IncrementalMagFreqDist trimmedOffMFD = new IncrementalMagFreqDist(
							onMFD.getMinX(), onMFD.getMaxX(), onMFD.size());
					IncrementalMagFreqDist totMFD = new IncrementalMagFreqDist(
							onMFD.getMinX(), onMFD.getMaxX(), onMFD.size());
					for (int n = 0; n < trimmedOffMFD.size(); n++) {
						double x = trimmedOffMFD.getX(n);
						if (x <= offMFD.getMaxX())
							trimmedOffMFD.set(n, offMFD.getY(x));
						totMFD.set(n, onMFD.getY(n) + trimmedOffMFD.getY(n));
					}
					offMFDs.add(trimmedOffMFD);
					totMFDs.add(totMFD);
				} else {
					offMFDs.add(null);
					totMFDs.add(null);
				}
				debug(solIndex, "DONE calculating region " + i);
			}
			debug(solIndex, "archiving");
			synchronized (this) {
				// store MFDs for this branch
				weights.add(wt);
				for (int i = 0; i < regions.size(); i++) {
					solMFDs.get(i).add(onMFDs.get(i));
					if (offMFDs.get(i) != null)
						solOffMFDs.get(i).add(offMFDs.get(i));
					if (totMFDs.get(i) != null)
						solTotalMFDs.get(i).add(totMFDs.get(i));
				}
			}
			debug(solIndex, "DONE archiving");
		}

		@Override
		protected void doFinalizePlot() {
			UCERF2_MFD_ConstraintFetcher ucerf2Fetch = null;

			System.out.println("Finalizing MFD plot for "
					+ solMFDs.get(0).size() + " branches!");

			specs = Lists.newArrayList();
			cumulative_specs = Lists.newArrayList();
			
			for (int i = 0; i < regions.size(); i++) {
				Region region = regions.get(i);
				
				for (boolean cumulative : cumulatives) {
					// we generate a cumulative and incremental plot for each region
					
					XY_DataSetList solMFDsForRegion = solMFDs.get(i);
					XY_DataSetList solOffMFDsForRegion = solOffMFDs.get(i);
					XY_DataSetList totalMFDsForRegion = solTotalMFDs.get(i);

					// get UCERF2 comparison fetcher
					if (ucerf2Fetch == null)
						ucerf2Fetch = new UCERF2_MFD_ConstraintFetcher(region);
					else
						ucerf2Fetch.setRegion(region);

					// UCERF2 comparison MFDs
					EvenlyDiscretizedFunc ucerf2TotalMFD = ucerf2Fetch.getTotalMFD();
					EvenlyDiscretizedFunc ucerf2OffMFD = ucerf2Fetch.getBackgroundSeisMFD();
					
					if (cumulative) {
						// make cumulative datasets
						XY_DataSetList cml_solMFDsForRegion = new XY_DataSetList();
						XY_DataSetList cml_solOffMFDsForRegion = new XY_DataSetList();
						XY_DataSetList cml_totalMFDsForRegion = new XY_DataSetList();
						
						for (int j=0; j<solMFDsForRegion.size(); j++)
							cml_solMFDsForRegion.add(
									((IncrementalMagFreqDist)solMFDsForRegion.get(j)).getCumRateDistWithOffset());
						
						for (int j=0; j<solOffMFDsForRegion.size(); j++)
							cml_solOffMFDsForRegion.add(
									((IncrementalMagFreqDist)solOffMFDsForRegion.get(j)).getCumRateDistWithOffset());
						
						for (int j=0; j<totalMFDsForRegion.size(); j++)
							cml_totalMFDsForRegion.add(
									((IncrementalMagFreqDist)totalMFDsForRegion.get(j)).getCumRateDistWithOffset());
						
						solMFDsForRegion = cml_solMFDsForRegion;
						solOffMFDsForRegion = cml_solOffMFDsForRegion;
						totalMFDsForRegion = cml_totalMFDsForRegion;
						
						ucerf2TotalMFD = ((IncrementalMagFreqDist)ucerf2TotalMFD).getCumRateDistWithOffset();
						ucerf2OffMFD = ((IncrementalMagFreqDist)ucerf2OffMFD).getCumRateDistWithOffset();
					}

					ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
					ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();

					funcs.add(ucerf2TotalMFD);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f,
							BROWN));
					funcs.add(ucerf2OffMFD);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f,
							Color.MAGENTA));

					if (!solOffMFDsForRegion.isEmpty()) {
						// now add target GRs
						if (cumulative) {
							funcs.add(InversionTargetMFDs
									.getTotalTargetGR_upToM9(TotalMag5Rate.RATE_9p6
											.getRateMag5()).getCumRateDistWithOffset());
							funcs.add(InversionTargetMFDs
									.getTotalTargetGR_upToM9(TotalMag5Rate.RATE_7p9
											.getRateMag5()).getCumRateDistWithOffset());
							funcs.add(InversionTargetMFDs
									.getTotalTargetGR_upToM9(TotalMag5Rate.RATE_6p5
											.getRateMag5()).getCumRateDistWithOffset());
						} else {
							funcs.add(InversionTargetMFDs
									.getTotalTargetGR_upToM9(TotalMag5Rate.RATE_9p6
											.getRateMag5()));
							funcs.add(InversionTargetMFDs
									.getTotalTargetGR_upToM9(TotalMag5Rate.RATE_7p9
											.getRateMag5()));
							funcs.add(InversionTargetMFDs
									.getTotalTargetGR_upToM9(TotalMag5Rate.RATE_6p5
											.getRateMag5()));
						}
						chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,
								1f, Color.BLACK));
						chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,
								2f, Color.BLACK));
						chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,
								1f, Color.BLACK));

						funcs.addAll(getFractiles(solOffMFDsForRegion, weights,
								"Solution Off Fault MFDs", fractiles));
						chars.addAll(getFractileChars(Color.GRAY, fractiles.length));
					}

					funcs.addAll(getFractiles(solMFDsForRegion, weights,
							"Solution On Fault MFDs", fractiles));
					chars.addAll(getFractileChars(Color.BLUE, fractiles.length));

					if (!totalMFDsForRegion.isEmpty()) {
						funcs.addAll(getFractiles(totalMFDsForRegion, weights,
								"Solution Total MFDs", fractiles));
						chars.addAll(getFractileChars(Color.RED, fractiles.length));
					}

					String title = region.getName();
					if (title == null || title.isEmpty())
						title = "Unnamed Region";

					String xAxisLabel = "Magnitude";
					String yAxisLabel;
					if (cumulative)
						yAxisLabel = "Cumulative Rate (per yr)";
					else
						yAxisLabel = "Incremental Rate (per yr)";

					PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel,
							yAxisLabel);
					if (cumulative)
						cumulative_specs.add(spec);
					else
						specs.add(spec);
				}
			}
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				RegionalMFDPlot o = (RegionalMFDPlot) otherCalc;
				weights.addAll(o.weights);
				for (int r = 0; r < regions.size(); r++) {
					solMFDs.get(r).addAll(o.solMFDs.get(r));
					solOffMFDs.get(r).addAll(o.solOffMFDs.get(r));
					solTotalMFDs.get(r).addAll(o.solTotalMFDs.get(r));
				}
			}
		}

		protected List<Region> getRegions() {
			return regions;
		}

		protected List<PlotSpec> getSpecs() {
			return specs;
		}

	}

	/*
	 * ERF Based MFD plots
	 */
	
	public static List<PlotSpec> getERFBasedRegionalMFDPlotSpecs(
			FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, List<Region> regions) {
		ERFBasedRegionalMFDPlot plot = new ERFBasedRegionalMFDPlot(
				weightProvider, regions,
				ERFBasedRegionalMFDPlot.getDefaultFractiles());

		plot.buildPlot(fetch);

		return plot.specs;
	}
	
	public static void writeERFBasedRegionalMFDPlots(
			FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, List<Region> regions,
			File dir, String prefix) throws IOException {
		List<PlotSpec> specs = getERFBasedRegionalMFDPlotSpecs(fetch,
				weightProvider, regions);

		writeERFBasedRegionalMFDPlots(specs, regions, dir, prefix);
	}

	public static void writeERFBasedRegionalMFDPlots(List<PlotSpec> specs,
			List<Region> regions, File dir, String prefix) throws IOException {
		
		File subDir = new File(dir, "erf_mfd_plots");
		if (!subDir.exists())
			subDir.mkdir();

		int unnamedRegionCnt = 0;

		for (int i = 0; i < regions.size(); i++) {
			PlotSpec spec = specs.get(i);
			Region region = regions.get(i);

			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			CommandLineInversionRunner.setFontSizes(gp);
			gp.setYLog(true);
//			gp.setUserBounds(5d, 9d, 1e-6, 1e0);
			gp.setUserBounds(5d, 9d, 1e-5, 1e1);

			gp.drawGraphPanel(spec);

			String fname = prefix + "_MFD_ERF";
			if (region.getName() != null && !region.getName().isEmpty())
				fname += "_" + region.getName().replaceAll("\\W+", "_");
			else
				fname += "_UNNAMED_REGION_" + (++unnamedRegionCnt);

			File file = new File(subDir, fname);
			gp.getChartPanel().setSize(1000, 800);
			gp.saveAsPDF(file.getAbsolutePath() + ".pdf");
			gp.saveAsPNG(file.getAbsolutePath() + ".png");
			gp.saveAsTXT(file.getAbsolutePath() + ".txt");
			File smallDir = new File(dir, "small_MFD_plots");
			if (!smallDir.exists())
				smallDir.mkdir();
			file = new File(smallDir, fname + "_small");
			gp.getChartPanel().setSize(500, 400);
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
			gp.saveAsPNG(file.getAbsolutePath()+".png");
		}
	}

	/**
	 * ERF Based regional MFD plots
	 * @author kevin
	 *
	 */
	public static class ERFBasedRegionalMFDPlot extends CompoundFSSPlots {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public static List<Region> getDefaultRegions() {
			List<Region> regions = Lists.newArrayList();
			regions.add(new CaliforniaRegions.RELM_TESTING());
			regions.add(new CaliforniaRegions.LA_BOX());
			regions.add(new CaliforniaRegions.SF_BOX());
			regions.add(new CaliforniaRegions.NORTHRIDGE_BOX());
			return regions;
		}
		
		private static final boolean infer_off_fault = false;
		private static final boolean INCLUDE_AFTERSHOCKS = true;

		private transient BranchWeightProvider weightProvider;
		private List<Region> regions;
		private List<Double> weights;
		private double[] ucerf2Weights;

		// none (except min/mean/max which are always included)
		private double[] fractiles;

		// these are organized as (region, solution)
		private List<XY_DataSetList> solMFDs;
		private List<XY_DataSetList> solOnMFDs;
		private List<XY_DataSetList> solOffMFDs;
		private List<EvenlyDiscretizedFunc[]> ucerf2MFDs;
		private List<EvenlyDiscretizedFunc[]> ucerf2OnMFDs;
		private List<EvenlyDiscretizedFunc[]> ucerf2OffMFDs;

		private transient Deque<UCERF2_TimeIndependentEpistemicList> ucerf2_erf_lists = new ArrayDeque<UCERF2_TimeIndependentEpistemicList>();
		// private transient UCERF2_TimeIndependentEpistemicList
		// ucerf2_erf_list;

		private static final double minX = 5.05d;
		private static final double maxX = 9.05d;
		private static final double delta = 0.1d;
		private static final int num = (int) ((maxX - minX) / delta + 1);

		private List<PlotSpec> specs;

		private int numUCEF2_ERFs;

		private transient Map<FaultModels, RupInRegionCache> rupInRegionsCaches = Maps
				.newHashMap();

		private static double[] getDefaultFractiles() {
//			double[] ret = { 0.5 };
			double[] ret = {};
			return ret;
		}

		public ERFBasedRegionalMFDPlot(BranchWeightProvider weightProvider) {
			this(weightProvider, getDefaultRegions());
		}

		public ERFBasedRegionalMFDPlot(BranchWeightProvider weightProvider,
				List<Region> regions) {
			this(weightProvider, regions, getDefaultFractiles());
		}

		public ERFBasedRegionalMFDPlot(BranchWeightProvider weightProvider,
				List<Region> regions, double[] fractiles) {
			this.weightProvider = weightProvider;
			this.regions = regions;
			this.fractiles = fractiles;

			UCERF2_TimeIndependentEpistemicList ucerf2_erf_list = checkOutUCERF2_ERF();
			numUCEF2_ERFs = ucerf2_erf_list.getNumERFs();
			returnUCERF2_ERF(ucerf2_erf_list);
			ucerf2_erf_list = null;

			solMFDs = Lists.newArrayList();
			solOnMFDs = Lists.newArrayList();
			solOffMFDs = Lists.newArrayList();
			ucerf2MFDs = Lists.newArrayList();
			ucerf2OnMFDs = Lists.newArrayList();
			ucerf2OffMFDs = Lists.newArrayList();
			weights = Lists.newArrayList();
			ucerf2Weights = new double[numUCEF2_ERFs];

			for (int i = 0; i < regions.size(); i++) {
				solMFDs.add(new XY_DataSetList());
				solOnMFDs.add(new XY_DataSetList());
				solOffMFDs.add(new XY_DataSetList());
				ucerf2MFDs.add(new EvenlyDiscretizedFunc[numUCEF2_ERFs]);
				ucerf2OnMFDs.add(new EvenlyDiscretizedFunc[numUCEF2_ERFs]);
				ucerf2OffMFDs.add(new EvenlyDiscretizedFunc[numUCEF2_ERFs]);
			}
		}

		/**
		 * We store one UCERF2 comparison ERF per thread. Once checked out, and ERF should be 
		 * returned via the returnUCERF2_ERF method
		 * @return
		 */
		private synchronized UCERF2_TimeIndependentEpistemicList checkOutUCERF2_ERF() {
			if (ucerf2_erf_lists.isEmpty()) {
				UCERF2_TimeIndependentEpistemicList ucerf2_erf_list = new UCERF2_TimeIndependentEpistemicList();
				ucerf2_erf_list.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME,
						UCERF2.FULL_DDW_FLOATER);
				ucerf2_erf_list.setParameter(UCERF2.BACK_SEIS_NAME,
						UCERF2.BACK_SEIS_INCLUDE);
				ucerf2_erf_list.setParameter(UCERF2.BACK_SEIS_RUP_NAME,
						UCERF2.BACK_SEIS_RUP_POINT);
				ucerf2_erf_list.getTimeSpan().setDuration(1d);
				ucerf2_erf_list.updateForecast();
				return ucerf2_erf_list;
			}
			return ucerf2_erf_lists.pop();
		}

		/**
		 * Return the ERF to the stack for a future thread to use
		 * @param erf
		 */
		private synchronized void returnUCERF2_ERF(
				UCERF2_TimeIndependentEpistemicList erf) {
			ucerf2_erf_lists.push(erf);
		}

		/**
		 * Calculate UCERF2 MFDs for the given index (up to the total number of UCERF2 branches).
		 * @param erfIndex
		 */
		private void calcUCERF2MFDs(int erfIndex) {
			UCERF2_TimeIndependentEpistemicList ucerf2_erf_list = checkOutUCERF2_ERF();
			ERF erf = ucerf2_erf_list.getERF(erfIndex);
			
			ucerf2Weights[erfIndex] = ucerf2_erf_list
					.getERF_RelativeWeight(erfIndex);
			
			// replace following as sum of on- and off-fault MFDs below
//			System.out.println("Calculating UCERF2 MFDs for branch "
//					+ erfIndex + ", "+regions.size()+" regions");
//			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
//				Region region = regions.get(regionIndex);
//				SummedMagFreqDist mfdPart = ERF_Calculator
//						.getParticipationMagFreqDistInRegion(erf, region, minX,
//								num, delta, true);
//				ucerf2MFDs.get(regionIndex)[erfIndex] = mfdPart
//						.getCumRateDistWithOffset();
//			}
			
			System.out.println("Calculating UCERF2 On Fault MFDs for branch "
					+ erfIndex + ", "+regions.size()+" regions");
			// on fault
			ucerf2_erf_list.getParameter(UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_EXCLUDE);
			erf = ucerf2_erf_list.getERF(erfIndex);
			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
				Region region = regions.get(regionIndex);
				SummedMagFreqDist mfdPart = ERF_Calculator
						.getParticipationMagFreqDistInRegion(erf, region, minX, num, delta, true);
				if(INCLUDE_AFTERSHOCKS)
					mfdPart.scale(1.0/FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS);
				ucerf2OnMFDs.get(regionIndex)[erfIndex] = mfdPart
						.getCumRateDistWithOffset();
			}
			System.out.println("Calculating UCERF2 Off Fault MFDs for branch "
					+ erfIndex + ", "+regions.size()+" regions");
			// off fault
			ucerf2_erf_list.getParameter(UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_ONLY);
			erf = ucerf2_erf_list.getERF(erfIndex);
			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
				Region region = regions.get(regionIndex);
				IncrementalMagFreqDist mfdPart = ERF_Calculator
						.getParticipationMagFreqDistInRegion(erf, region, minX, num, delta, true);
				if(INCLUDE_AFTERSHOCKS) {
					// it's a summed, turn it into an incremental to allow set operation
					IncrementalMagFreqDist scaled = new IncrementalMagFreqDist(
							mfdPart.getMinX(), mfdPart.size(), mfdPart.getDelta());
					for(int i=0;i<mfdPart.size();i++) {
						double scale = GardnerKnopoffAftershockFilter.scaleForMagnitude(mfdPart.getX(i));
						scaled.set(i, mfdPart.getY(i)/scale);	// divide to add aftershocks back in
					}
					mfdPart = scaled;
				}
				ucerf2OffMFDs.get(regionIndex)[erfIndex] = mfdPart
						.getCumRateDistWithOffset();
			}

			// sum the above on and off MFDs to get the total
			System.out.println("Calculating UCERF2 MFDs for branch "
					+ erfIndex + ", "+regions.size()+" regions");
			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
				SummedMagFreqDist summedMFD = new SummedMagFreqDist(minX-delta/2,num, delta);	// note offset minX because it's cumulative
				summedMFD.addIncrementalMagFreqDist(ucerf2OnMFDs.get(regionIndex)[erfIndex]);
				summedMFD.addIncrementalMagFreqDist(ucerf2OffMFDs.get(regionIndex)[erfIndex]);
				ucerf2MFDs.get(regionIndex)[erfIndex] = summedMFD;
			}
			
			ucerf2_erf_list.getParameter(UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_INCLUDE);
			
			returnUCERF2_ERF(ucerf2_erf_list);
		}

		/**
		 * Makes sure that all UCERF2 MFDs have been calculated (if we have less UCERF3 logic tree
		 * branches than UCERF2 this can take a little while).
		 */
		private void checkCalcAllUCERF2MFDs() {
			for (int erfIndex = 0; erfIndex < numUCEF2_ERFs; erfIndex++) {
				if (ucerf2MFDs.get(0)[erfIndex] == null)
					calcUCERF2MFDs(erfIndex);
			}
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			throw new IllegalStateException("Should not be called, ERF plot!");
		}

		@Override
		protected void processERF(LogicTreeBranch branch,
				FaultSystemSolutionERF erf, int solIndex) {
			debug(solIndex, "checking UCERF2");
			// do UCERF2 if applicable so that we don't have to do them all single threaded at the end
			if (solIndex < numUCEF2_ERFs)
				calcUCERF2MFDs(solIndex);
			debug(solIndex, " done UCERF2");

			FaultModels fm = branch.getValue(FaultModels.class);

			// this cache keeps track of which rupture is within each of the regions.
			RupInRegionCache rupsCache = rupInRegionsCaches.get(fm);
			if (rupsCache == null) {
				synchronized (this) {
					if (!rupInRegionsCaches.containsKey(fm)) {
						rupInRegionsCaches.put(fm, new RupInRegionsCache());
					}
				}
				rupsCache = rupInRegionsCaches.get(fm);
			}

			debug(solIndex, "done cache");

			List<DiscretizedFunc> mfds = Lists.newArrayList();
			List<DiscretizedFunc> offMFDs = Lists.newArrayList();
			List<DiscretizedFunc> onMFDs = Lists.newArrayList();

			// get total MFD
			for (int r = 0; r < regions.size(); r++) {
				Region region = regions.get(r);

				Stopwatch watch = Stopwatch.createStarted();
				debug(solIndex, "calculating region (COMBINED) " + r);
				// System.out.println("Calculating branch "+solIndex+" region "+r);
				SummedMagFreqDist ucerf3_Part = ERF_Calculator
						.getParticipationMagFreqDistInRegion(erf, region, minX,
								num, delta, true, rupsCache);
				watch.stop();
				debug(solIndex,
						"done region (COMBINED) " + r + " ("
								+ watch.elapsed(TimeUnit.SECONDS) + " s)");
				// System.out.println("Took "+(watch.elapsedMillis()/1000d)+" secst for branch "
				// +solIndex+" region "+r+" ("+region.getName()+")");

				mfds.add(ucerf3_Part.getCumRateDistWithOffset());
			}

			erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
			erf.updateForecast();
			
			// get on fault MFD
			for (int r = 0; r < regions.size(); r++) {
				Region region = regions.get(r);

				Stopwatch watch = Stopwatch.createStarted();
				debug(solIndex, "calculating region (ON FAULT) " + r);
				// System.out.println("Calculating branch "+solIndex+" region "+r);
				SummedMagFreqDist ucerf3_Part = ERF_Calculator
						.getParticipationMagFreqDistInRegion(erf, region, minX,
								num, delta, true, rupsCache);
				watch.stop();
				debug(solIndex,
						"done region (ON FAULT) " + r + " ("
								+ watch.elapsed(TimeUnit.SECONDS) + " s)");
				// System.out.println("Took "+(watch.elapsedMillis()/1000d)+" secst for branch "
				// +solIndex+" region "+r+" ("+region.getName()+")");

				onMFDs.add(ucerf3_Part.getCumRateDistWithOffset());
			}
			
			// get off fault - we can either infer off fault from total and on or calculate it again
			if (infer_off_fault) {
				for (int r = 0; r < regions.size(); r++) {
					DiscretizedFunc totMFD = mfds.get(r);
					DiscretizedFunc onMFD = mfds.get(r);
					DiscretizedFunc offMFD = new EvenlyDiscretizedFunc(totMFD.getMinX(), totMFD.getMaxX(), totMFD.size());
					for (int i=0; i<totMFD.size(); i++)
						offMFD.set(i, totMFD.getY(i) - onMFD.getY(i));
					offMFDs.add(offMFD);
				}
			} else {
				erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);
				erf.updateForecast();
				
				for (int r = 0; r < regions.size(); r++) {
					Region region = regions.get(r);

					Stopwatch watch = Stopwatch.createStarted();
					debug(solIndex, "calculating region (OFF FAULT) " + r);
					// System.out.println("Calculating branch "+solIndex+" region "+r);
					SummedMagFreqDist ucerf3_Part = ERF_Calculator
							.getParticipationMagFreqDistInRegion(erf, region, minX,
									num, delta, true, rupsCache);
					watch.stop();
					debug(solIndex,
							"done region (OFF FAULT) " + r + " ("
									+ watch.elapsed(TimeUnit.SECONDS) + " s)");
					// System.out.println("Took "+(watch.elapsedMillis()/1000d)+" secst for branch "
					// +solIndex+" region "+r+" ("+region.getName()+")");

					offMFDs.add(ucerf3_Part.getCumRateDistWithOffset());
				}
			}
			
			erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
			erf.updateForecast();

			debug(solIndex, " archiving");
			synchronized (this) {
				// store results
				weights.add(weightProvider.getWeight(branch));
				for (int r = 0; r < regions.size(); r++) {
					solMFDs.get(r).add(mfds.get(r));
					solOnMFDs.get(r).add(onMFDs.get(r));
					solOffMFDs.get(r).add(offMFDs.get(r));
				}
			}
			debug(solIndex, " archiving done");
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				ERFBasedRegionalMFDPlot o = (ERFBasedRegionalMFDPlot) otherCalc;
				for (int r = 0; r < regions.size(); r++) {
					solMFDs.get(r).addAll(o.solMFDs.get(r));
					solOnMFDs.get(r).addAll(o.solOnMFDs.get(r));
					solOffMFDs.get(r).addAll(o.solOffMFDs.get(r));
				}
				weights.addAll(o.weights);

				for (int e = 0; e < numUCEF2_ERFs; e++) {
					if (o.ucerf2MFDs.get(0)[e] != null) {
						for (int r = 0; r < regions.size(); r++) {
							ucerf2MFDs.get(r)[e] = o.ucerf2MFDs.get(r)[e];
							ucerf2OnMFDs.get(r)[e] = o.ucerf2OnMFDs.get(r)[e];
							ucerf2OffMFDs.get(r)[e] = o.ucerf2OffMFDs.get(r)[e];
						}
						ucerf2Weights[e] = o.ucerf2Weights[e];
					}
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			specs = Lists.newArrayList();

			// make sure we've calculated all UCERF2 MFDs
			checkCalcAllUCERF2MFDs();

//			MeanUCERF2 erf = new MeanUCERF2();
//			erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME,
//					UCERF2.PROB_MODEL_POISSON);
//			erf.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME,
//					UCERF2.FULL_DDW_FLOATER);
//			erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
//			erf.setParameter(UCERF2.BACK_SEIS_RUP_NAME,
//					UCERF2.BACK_SEIS_RUP_POINT);
//			erf.getTimeSpan().setDuration(1d);
//			erf.updateForecast();

			for (int r = 0; r < regions.size(); r++) {
				// create plot spec for each region
				Region region = regions.get(r);

				XY_DataSetList ucerf2Funcs = new XY_DataSetList();
				XY_DataSetList ucerf2OnFuncs = new XY_DataSetList();
				XY_DataSetList ucerf2OffFuncs = new XY_DataSetList();
				ArrayList<Double> ucerf2Weights = new ArrayList<Double>();
				for (int e = 0; e < ucerf2MFDs.get(r).length; e++) {
					DiscretizedFunc mfd = ucerf2MFDs.get(r)[e];
					if (mfd != null) {
						ucerf2Funcs.add(ucerf2MFDs.get(r)[e]);
						ucerf2OnFuncs.add(ucerf2OnMFDs.get(r)[e]);
						ucerf2OffFuncs.add(ucerf2OffMFDs.get(r)[e]);
						ucerf2Weights.add(this.ucerf2Weights[e]);
					}
				}

				ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
				ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
				
				funcs.addAll(getFractiles(ucerf2OnFuncs, ucerf2Weights,
						"UCERF2 Epistemic List On Fault MFDs", fractiles));
				chars.addAll(getFractileChars(Color.GREEN, fractiles.length));
				
				funcs.addAll(getFractiles(ucerf2OffFuncs, ucerf2Weights,
						"UCERF2 Epistemic List Off Fault MFDs", fractiles));
				chars.addAll(getFractileChars(Color.MAGENTA, fractiles.length));
				
				funcs.addAll(getFractiles(solOnMFDs.get(r), weights,
						"UCERF3 On Fault MFDs", fractiles));
				chars.addAll(getFractileChars(Color.ORANGE, fractiles.length));
				
				funcs.addAll(getFractiles(solOffMFDs.get(r), weights,
						"UCERF3 Off Fault MFDs", fractiles));
				chars.addAll(getFractileChars(Color.GRAY, fractiles.length));

				funcs.addAll(getFractiles(ucerf2Funcs, ucerf2Weights,
						"UCERF2 Epistemic List", fractiles));
				chars.addAll(getFractileChars(Color.RED, fractiles.length));

//				SummedMagFreqDist meanU2Part = ERF_Calculator
//						.getParticipationMagFreqDistInRegion(erf, region, minX,
//								num, delta, true);
//				meanU2Part.setName("MFD for MeanUCERF2");
//				meanU2Part.setInfo(" ");
//				funcs.add(meanU2Part.getCumRateDistWithOffset());
//				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f,
//						Color.BLUE));

				funcs.addAll(getFractiles(solMFDs.get(r), weights,
						"UCERF3 MFDs", fractiles));
				chars.addAll(getFractileChars(Color.BLUE, fractiles.length));

				String title = region.getName();
				if (title == null || title.isEmpty())
					title = "Unnamed Region";

				String xAxisLabel = "Magnitude";
				String yAxisLabel = "Cumulative Rate (per yr)";

				PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel,
						yAxisLabel);
				specs.add(spec);
			}
		}

		@Override
		protected boolean usesERFs() {
			return true;
		}

		@Override
		protected boolean isApplyAftershockFilter() {
			return !INCLUDE_AFTERSHOCKS;
		}

		protected List<PlotSpec> getSpecs() {
			return specs;
		}

	}

	public static void writeERFBasedRegionalProbDistPlots(ERFBasedRegionalMagProbPlot plot, File dir, String prefix)
			throws IOException {
		
		Map<Double, List<PlotSpec>> specs = plot.specs;
		Map<Double, List<PlotSpec>> faultSpecs = plot.faultSpecs;
		Map<Double, List<Map<LogicTreeBranch, Map<MagDependentAperiodicityOptions, Double>>>> regionM6p7Vals =
				plot.regionM6p7Vals;
		List<Region> regions = plot.regions;
		
		File subDir = new File(dir, "erf_mag_prob_plots");
		if (!subDir.exists())
			subDir.mkdir();
		File smallMPDDir = new File(dir, "small_MPD_plots");
		if (!smallMPDDir.exists())
			smallMPDDir.mkdir();
		File smallFaultsDir = new File(dir, "small_MPD_faults");
		if (!smallFaultsDir.exists())
			smallFaultsDir.mkdir();
		File regionM6p7Dir = new File(dir, "region_m6p7_branch_hists");
		if (!regionM6p7Dir.exists())
			regionM6p7Dir.mkdir();

		int unnamedRegionCnt = 0;

		for (double duration : ERFBasedRegionalMagProbPlot.durations) {
			String durStr = (int)duration+"yr";
			File durDir = new File(subDir, durStr);
			if (!durDir.exists())
				durDir.mkdir();
			List<File> regSmallPDFs = Lists.newArrayList();
			List<File> faultSmallPDFs = Lists.newArrayList();
			
			File regionM6p7DurDir = new File(regionM6p7Dir, (int)duration+"yr");
			if (!regionM6p7DurDir.exists())
				regionM6p7DurDir.mkdir();
			
			for (int i = 0; i < regions.size(); i++) {
				PlotSpec spec = specs.get(duration).get(i);
				Region region = regions.get(i);

				HeadlessGraphPanel gp = new HeadlessGraphPanel();
				CommandLineInversionRunner.setFontSizes(gp);
//				gp.setYLog(true);
//				gp.setUserBounds(5d, 9d, 1e-6, 1e0);
//				gp.setUserBounds(5d, 9d, 1e-5, 1e0);
				gp.setUserBounds(5d, 9d, 0d, 1d);

				gp.drawGraphPanel(spec);
				String regNameFileSafe;
				if (region.getName() != null && !region.getName().isEmpty())
					regNameFileSafe = region.getName().replaceAll("\\W+", "_");
				else
					regNameFileSafe = "UNNAMED_REGION_" + (++unnamedRegionCnt);

				String fname = prefix+"_"+(int)duration+"yr_MPD_ERF_"+regNameFileSafe;

				File file = new File(durDir, fname);
				gp.getChartPanel().setSize(1000, 800);
				gp.saveAsPDF(file.getAbsolutePath() + ".pdf");
				gp.saveAsPNG(file.getAbsolutePath() + ".png");
				gp.saveAsTXT(file.getAbsolutePath() + ".txt");
				file = new File(smallMPDDir, fname + "_small");
				gp.getChartPanel().setSize(500, 400);
				gp.saveAsPDF(file.getAbsolutePath()+".pdf");
				gp.saveAsPNG(file.getAbsolutePath()+".png");
				regSmallPDFs.add(new File(file.getAbsolutePath()+".pdf"));
				
				// branch sensitivity hists
				File regionHistDir = new File(regionM6p7DurDir, regNameFileSafe);
				if (!regionHistDir.exists())
					regionHistDir.mkdir();
				
				BranchSensitivityHistogram sensHist =
						ERFBasedRegionalMagProbPlot.buildHist(regionM6p7Vals.get(duration).get(i), plot.weightProvider);
				Range range = sensHist.getRange();
				double delta = 0.025;
				Map<String, PlotSpec> histSpecs = sensHist.getStackedHistPlots(true, delta);
				List<File> histPDFs = Lists.newArrayList();
				List<String> names = Lists.newArrayList();
				for (Class<? extends LogicTreeBranchNode<?>> clazz : LogicTreeBranch.getLogicTreeNodeClasses()) {
					if (clazz.equals(InversionModels.class) || clazz.equals(MomentRateFixes.class))
						continue;
					names.add(ClassUtils.getClassNameWithoutPackage(LogicTreeBranch.getEnumEnclosingClass(clazz)));
				}
				names.add("MagDepAperiodicity");
				System.out.println("Histograms for "+regNameFileSafe+", duration="+(int)duration);
				for (String name : names) {
					PlotSpec histSpec = histSpecs.get(name);
					Preconditions.checkNotNull(histSpec, "No plot found for: "+name);
					
					List<? extends PlotElement> elems = histSpec.getPlotElems();
					
					double maxY = 0;
					double min = 0d;
					int num = -1;
					for (PlotElement func : elems) {
						if (func instanceof DiscretizedFunc) {
							double myMax = ((DiscretizedFunc)func).getMaxY();
							if (myMax > maxY)
								maxY = myMax;
							if (num < 0 && func instanceof EvenlyDiscretizedFunc) {
								EvenlyDiscretizedFunc eFunc = (EvenlyDiscretizedFunc)func;
								num = eFunc.size();
								min = eFunc.getMinX();
							}
						}
					}
					double plotMaxY = maxY * 1.3;
					if (plotMaxY > 1)
						plotMaxY = 1;
					
//					XYTextAnnotation ann = new XYTextAnnotation("StdDev="+new DecimalFormat("0.00").format(stdDev), 0.05, 0.95);
//					ann.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
//					ann.setTextAnchor(TextAnchor.TOP_LEFT);
//					histSpec.setPlotAnnotations(Lists.newArrayList(ann));
					
					gp = new HeadlessGraphPanel();
					CommandLineInversionRunner.setFontSizes(gp);
//					gp.setUserBounds(min-0.5*delta, min+(num-0.5)*delta, 0, plotMaxY);
					gp.setUserBounds(min-0.5*delta - 0.5*delta, min+(num-0.5)*delta + 0.5*delta, 0, plotMaxY);
					
					gp.drawGraphPanel(histSpec);
					file = new File(regionHistDir, name);
					gp.getChartPanel().setSize(500, 400);
					gp.saveAsPDF(file.getAbsolutePath()+".pdf");
					gp.saveAsPNG(file.getAbsolutePath()+".png");
					histPDFs.add(new File(file.getAbsolutePath()+".pdf"));
				}
				
				try {
					FaultSysSolutionERF_Calc.combineBranchSensHists(histPDFs, new File(regionHistDir, "histograms_combined.pdf"));
				} catch (com.lowagie.text.DocumentException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
			File faultDir = new File(durDir, "faults");
			if (!faultDir.exists())
				faultDir.mkdir();
			for (PlotSpec spec : faultSpecs.get(duration)) {
				HeadlessGraphPanel gp = new HeadlessGraphPanel();
				CommandLineInversionRunner.setFontSizes(gp);
				gp.setYLog(false);
				gp.setUserBounds(5d, 9d, 0, 1);

				gp.drawGraphPanel(spec);

				String fname = spec.getTitle().replaceAll("\\W+", "_")+"_"+(int)duration+"yr";

				File file = new File(faultDir, fname);
				gp.getChartPanel().setSize(1000, 800);
				gp.saveAsPDF(file.getAbsolutePath() + ".pdf");
				gp.saveAsPNG(file.getAbsolutePath() + ".png");
				gp.saveAsTXT(file.getAbsolutePath() + ".txt");
				file = new File(smallFaultsDir, fname + "_small");
				gp.getChartPanel().setSize(500, 400);
				gp.saveAsPDF(file.getAbsolutePath()+".pdf");
				gp.saveAsPNG(file.getAbsolutePath()+".png");
			}
			for (String faultName : ERFBasedRegionalMagProbPlot.mainFaultsCombinedPFDNames)
				faultSmallPDFs.add(new File(
						smallFaultsDir, faultName.replaceAll("\\W+", "_")+"_"+(int)duration+"yr_small"+".pdf"));
			try {
				if (regSmallPDFs.size() > 1)
					TestPDFCombine.combine(regSmallPDFs, new File(smallMPDDir, (int)duration+"yr_combined.pdf"),
							2, 0.5, false, 0.03, 0.05);
				if (faultSmallPDFs.size() > 1)
					TestPDFCombine.combine(faultSmallPDFs, new File(smallFaultsDir, (int)duration+"yr_combined.pdf"),
							2, 0.43, false, 0.06, 0.03);
			} catch (com.lowagie.text.DocumentException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			// now write CSVs
			// tables are: duration, timeDep, CSV
			// region CSVs
			for (Boolean indepVal : plot.regionProbCSVs.row(duration).keySet()) {
				CSVFile<String> csv = plot.regionProbCSVs.get(duration, indepVal);
				String indepStr = indepVal ? "dep" : "indep";
				String fName = "region_"+durStr+"_"+indepStr+"_mag_prob_dists.csv";
				csv.writeToFile(new File(durDir, fName));
			}
			for (Boolean indepVal : plot.regionRateCSVs.row(duration).keySet()) {
				CSVFile<String> csv = plot.regionRateCSVs.get(duration, indepVal);
				String indepStr = indepVal ? "dep" : "indep";
				String fName = "region_"+durStr+"_"+indepStr+"_mag_rate_dists.csv";
				csv.writeToFile(new File(durDir, fName));
			}
			// fault CSVs
			for (Boolean indepVal : plot.faultProbCSVs.row(duration).keySet()) {
				CSVFile<String> csv = plot.faultProbCSVs.get(duration, indepVal);
				String indepStr = indepVal ? "dep" : "indep";
				String fName = "fault_"+durStr+"_"+indepStr+"_mag_prob_dists.csv";
				csv.writeToFile(new File(faultDir, fName));
			}
			for (Boolean indepVal : plot.faultRateCSVs.row(duration).keySet()) {
				CSVFile<String> csv = plot.faultRateCSVs.get(duration, indepVal);
				String indepStr = indepVal ? "dep" : "indep";
				String fName = "fault_"+durStr+"_"+indepStr+"_mag_freq_dists.csv";
				csv.writeToFile(new File(faultDir, fName));
			}
			// sub sect CSVs
			for (FaultModels fm : plot.subSectsCSVs.columnKeySet()) {
				List<CSVFile<String>> csvs = plot.subSectsCSVs.get(duration, fm);
				String csvPrefix = fm.encodeChoiceString()+"_"+durStr+"_sub_sect_probs";
				csvs.get(0).writeToFile(new File(durDir, csvPrefix+"_u3_td_mean.csv"));
				csvs.get(1).writeToFile(new File(durDir, csvPrefix+"_u3_td_min.csv"));
				csvs.get(2).writeToFile(new File(durDir, csvPrefix+"_u3_td_max.csv"));
				csvs.get(3).writeToFile(new File(durDir, csvPrefix+"_u3_poisson_mean.csv"));
				
				csvs = plot.subSectsPercentileCSVs.get(duration, fm);
				for (int i=0; i<plot.sub_sect_fractiles.length; i++) {
					CSVFile<String> csv = csvs.get(i);
					String name = csvPrefix+"_u3_td_p"+(float)(plot.sub_sect_fractiles[i]*100d)+".csv";
					csv.writeToFile(new File(durDir, name));
				}
			}
			// parent sect CSVs
			for (FaultModels fm : plot.parentSectsCSVs.columnKeySet()) {
				List<CSVFile<String>> csvs = plot.parentSectsCSVs.get(duration, fm);
				String csvPrefix = fm.encodeChoiceString()+"_"+durStr+"_parent_sect_probs";
				csvs.get(0).writeToFile(new File(durDir, csvPrefix+"_u3_td_mean.csv"));
				csvs.get(1).writeToFile(new File(durDir, csvPrefix+"_u3_td_min.csv"));
				csvs.get(2).writeToFile(new File(durDir, csvPrefix+"_u3_td_max.csv"));
				csvs.get(3).writeToFile(new File(durDir, csvPrefix+"_u3_poisson_mean.csv"));
			}
		}
		
		// write metadata
		SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
		for (File d : new File[] {subDir, smallFaultsDir, smallMPDDir}) {
			File mFile = new File(d, "metadata.txt");
			
			FileWriter fw = new FileWriter(mFile);
			
			fw.write("ERF Based Regional/Fault Probability Distributions\n");
			fw.write("Generated on: "+df.format(new Date())+"\n");
			fw.write("Generated by: "+ERFBasedRegionalMagProbPlot.class.getName()+"\n");
			fw.write("\n");
			fw.write("All data INCLUDES aftershocks. UCERF3 data uses default BPT averaging"
					+ " method and UCERF3 Preferred Blend for the time dependence. This historical"
					+ " open interval is set as "+FaultSystemSolutionERF.START_TIME_DEFAULT+" - 1875 = "
					+(FaultSystemSolutionERF.START_TIME_DEFAULT-1875)+".\n");
			
			fw.close();
		}
	}
	
	private static void recombineMagProbDistPDFs(File smallMPDDir, File smallFaultsDir, String prefix)
			throws IOException {
		List<Region> regions = ERFBasedRegionalMagProbPlot.getDefaultRegions();
		for (double duration : ERFBasedRegionalMagProbPlot.durations) {
			List<File> regSmallPDFs = Lists.newArrayList();
			List<File> faultSmallPDFs = Lists.newArrayList();
			
			for (int i = 0; i < regions.size(); i++) {
				Region region = regions.get(i);
				
				String fname = prefix+"_"+(int)duration+"yr_MPD_ERF";
				fname += "_" + region.getName().replaceAll("\\W+", "_");
				
				regSmallPDFs.add(new File(smallMPDDir, fname + "_small.pdf"));
			}
			for (String faultName : ERFBasedRegionalMagProbPlot.mainFaultsCombinedPFDNames)
				faultSmallPDFs.add(new File(
						smallFaultsDir, faultName.replaceAll("\\W+", "_")+"_"+(int)duration+"yr_small"+".pdf"));
//			Collections.reverse(regSmallPDFs);
//			Collections.reverse(faultSmallPDFs);
			try {
				TestPDFCombine.combine(regSmallPDFs, new File(smallMPDDir, (int)duration+"yr_combined.pdf"),
						2, 0.5, false, 0.03, 0.05);
				TestPDFCombine.combine(faultSmallPDFs, new File(smallFaultsDir, (int)duration+"yr_combined.pdf"),
						2, 0.43, false, 0.06, 0.03);
			} catch (com.lowagie.text.DocumentException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
	}
	
	/**
	 * Parses source name to get inversion index
	 * @param source
	 * @return
	 */
	private static int getInversionIndex(ProbEqkSource source) {
		String srcName = source.getName();
//		System.out.println(srcName);
		return Integer.parseInt(srcName.substring(srcName.indexOf("#")+1, srcName.indexOf(";")));
	}
	
	/**
	 * Cache for ruptures in regions. Uses the actual inversion index so that it works across
	 * all branches ofthe same FM.
	 * @author kevin
	 *
	 */
	public static class RupInRegionsCache implements RupInRegionCache {
		private ConcurrentMap<Region, ConcurrentMap<Integer, Boolean>> map = Maps
				.newConcurrentMap();

		@Override
		public boolean isRupInRegion(ERF erf, ProbEqkSource source, EqkRupture rup,
				int srcIndex, int rupIndex, Region region) {
			RuptureSurface surf = rup.getRuptureSurface();
			if (surf instanceof CompoundSurface) {
				int invIndex = getInversionIndex(source);
				ConcurrentMap<Integer, Boolean> regMap = map
						.get(region);
				if (regMap == null) {
					regMap = Maps.newConcurrentMap();
					map.putIfAbsent(region, regMap);
					// in case another thread put it in
					// first
					regMap = map.get(region);
				}
				Boolean inside = regMap.get(invIndex);
				if (inside == null) {
					inside = false;
					for (Location loc : surf
							.getEvenlyDiscritizedListOfLocsOnSurface())
						if (region.contains(loc)) {
							inside = true;
							break;
						}
					regMap.putIfAbsent(invIndex, inside);
				}
				return inside;
			}
			for (Location loc : surf
					.getEvenlyDiscritizedListOfLocsOnSurface())
				if (region.contains(loc))
					return true;
			return false;
		}
	}

	/**
	 * ERF Based regional MFD plots
	 * @author kevin
	 *
	 */
	public static class ERFBasedRegionalMagProbPlot extends CompoundFSSPlots {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public static List<Region> getDefaultRegions() {
			List<Region> regions = Lists.newArrayList();
			regions.add(new CaliforniaRegions.RELM_TESTING());
			regions.add(new CaliforniaRegions.RELM_NOCAL());
			regions.add(new CaliforniaRegions.RELM_SOCAL());
			regions.add(new CaliforniaRegions.SF_BOX());
			regions.add(new CaliforniaRegions.LA_BOX());
			regions.add(new CaliforniaRegions.NORTHRIDGE_BOX());
			regions.add(new CaliforniaRegions.SAN_DIEGO_BOX());
			return regions;
		}
		
		private static final boolean infer_off_fault = false;
		private static boolean INCLUDE_AFTERSHOCKS = true;
		private static boolean NUCLEATION_PROBS = false;
		
//		private static double[] durations = {5d, 30d};
		private static double[] durations = time_dep_durations;
//		private static double[] durations = {30d};

		private transient BranchWeightProvider weightProvider;
		private List<Region> regions;
		private Map<Double, List<Double>> weights;
		private Map<Double, Map<FaultModels, List<Double>>> fmWeights;
		private Map<Double, List<LogicTreeBranch>> branches;
		private double[] ucerf2DepWeights;
		private double[] ucerf2IndepWeights;

		// none (except min/mean/max which are always included)
		private double[] region_fractiles;
		private double[] sub_sect_fractiles = { 0.025, 0.16, 0.84, 0.975};
		
		private static MagDependentAperiodicityOptions[] covs = {MagDependentAperiodicityOptions.LOW_VALUES,
				MagDependentAperiodicityOptions.MID_VALUES, MagDependentAperiodicityOptions.HIGH_VALUES, null};

		// these are organized as duration: (region, cov: solution)
		private Map<Double, List<Map<MagDependentAperiodicityOptions, XY_DataSetList>>>
				solMPDs = Maps.newHashMap();
		private Map<Double, List<Map<MagDependentAperiodicityOptions, XY_DataSetList>>>
				solMFDs = Maps.newHashMap(); // same but frequency
		private Map<Double, List<Map<MagDependentAperiodicityOptions, XY_DataSetList>>>
				solOnMPDs = Maps.newHashMap();
		private Map<Double, List<Map<MagDependentAperiodicityOptions, XY_DataSetList>>>
				solOffMPDs = Maps.newHashMap();
		private Map<Double, List<EvenlyDiscretizedFunc[]>> ucerf2DepMPDs = Maps.newHashMap();
		private Map<Double, List<EvenlyDiscretizedFunc[]>> ucerf2DepMFDs = Maps.newHashMap();
		private Map<Double, List<EvenlyDiscretizedFunc[]>> ucerf2DepOnMPDs = Maps.newHashMap();
		private Map<Double, List<EvenlyDiscretizedFunc[]>> ucerf2DepOffMPDs = Maps.newHashMap();
		private Map<Double, List<EvenlyDiscretizedFunc[]>> ucerf2IndepMPDs = Maps.newHashMap();
		private Map<Double, List<EvenlyDiscretizedFunc[]>> ucerf2IndepMFDs = Maps.newHashMap();
		
		// duration: regions
//		private Map<Double, List<BranchSensitivityHistogram>> regionM6p7Hists = Maps.newHashMap();
		private Map<Double, List<Map<LogicTreeBranch, Map<MagDependentAperiodicityOptions, Double>>>>
				regionM6p7Vals = Maps.newHashMap();
		
		// now "main faults"
		// organized as duration, name, mfds
		private Map<Double, Map<MagDependentAperiodicityOptions, Map<String, XY_DataSetList>>>
				solMainFaultProbs = Maps.newHashMap();
		private Map<Double, Map<MagDependentAperiodicityOptions, Map<String, XY_DataSetList>>>
				solMainFaultRates = Maps.newHashMap();
		private Map<Double, Map<MagDependentAperiodicityOptions, Map<FaultModels, XY_DataSetList[]>>>
				solSubSectProbs = Maps.newHashMap();
		private Map<Double, Map<MagDependentAperiodicityOptions, Map<FaultModels, Map<Integer, XY_DataSetList>>>>
				solParentSectProbs = Maps.newHashMap();
//		private Map<Double, Map<String, XY_DataSetList>> ucerf2DepMainFaults = Maps.newHashMap();

		private transient Deque<UCERF2_TimeDependentEpistemicList> ucerf2_dep_erf_lists =
				new ArrayDeque<UCERF2_TimeDependentEpistemicList>();
		
		private transient Deque<UCERF2_TimeIndependentEpistemicList> ucerf2_indep_erf_lists =
				new ArrayDeque<UCERF2_TimeIndependentEpistemicList>();

		private static final double minX = 5.05d;
		private static final double maxX = 9.05d;
		private static final double delta = 0.1d;
		private static final int num = (int) ((maxX - minX) / delta + 1);

		private Map<Double, List<PlotSpec>> specs;
		private Map<Double, List<PlotSpec>> faultSpecs;

		// duration, timeDep, CSV
		private Table<Double, Boolean, CSVFile<String>> faultProbCSVs;
		private Table<Double, Boolean, CSVFile<String>> regionProbCSVs;
		private Table<Double, Boolean, CSVFile<String>> faultRateCSVs;
		private Table<Double, Boolean, CSVFile<String>> regionRateCSVs;
		
		private Table<Double, FaultModels, List<CSVFile<String>>> subSectsCSVs;
		private Table<Double, FaultModels, List<CSVFile<String>>> subSectsPercentileCSVs;
		private Table<Double, FaultModels, List<CSVFile<String>>> parentSectsCSVs;

		private int numUCEF2_DepERFs;
		private int numUCEF2_IndepERFs;

		private transient Map<FaultModels, FSSRupsInRegionCache> rupInRegionsCaches = Maps
				.newHashMap();
		
		private Map<String, List<Integer>> mainFaultsMap;
		private List<String> mainFaultsSorted;
		private static List<String> mainFaultsCombinedPFDNames;
		static {
			mainFaultsCombinedPFDNames = Lists.newArrayList(); // custom order
			mainFaultsCombinedPFDNames.add("S. San Andreas");
			mainFaultsCombinedPFDNames.add("N. San Andreas");
			mainFaultsCombinedPFDNames.add("Hayward-Rodgers Creek");
			mainFaultsCombinedPFDNames.add("Calaveras");
			mainFaultsCombinedPFDNames.add("San Jacinto");
			mainFaultsCombinedPFDNames.add("Garlock");
			mainFaultsCombinedPFDNames.add("Elsinore");
		}
		private Map<FaultModels, Map<String, HashSet<Integer>>> mainFaultsRuptures;
		
		private Map<FaultModels, Map<Integer, HashSet<Integer>>> parentSectRuptures;
		private Map<FaultModels, Map<Integer, String>> parentSectNamesMap;

		private static double[] getDefaultFractiles() {
//			double[] ret = { 0.5 };
			double[] ret = {};
			return ret;
		}

		public ERFBasedRegionalMagProbPlot(BranchWeightProvider weightProvider) {
			this(weightProvider, getDefaultRegions());
		}

		public ERFBasedRegionalMagProbPlot(BranchWeightProvider weightProvider,
				List<Region> regions) {
			this(weightProvider, regions, getDefaultFractiles());
		}

		public ERFBasedRegionalMagProbPlot(BranchWeightProvider weightProvider,
				List<Region> regions, double[] fractiles) {
			this.weightProvider = weightProvider;
			this.regions = regions;
			this.region_fractiles = fractiles;

			UCERF2_TimeDependentEpistemicList ucerf2_erf_list = checkOutUCERF2_DepERF();
			numUCEF2_DepERFs = ucerf2_erf_list.getNumERFs();
			returnUCERF2_DepERF(ucerf2_erf_list);
			ucerf2_erf_list = null;

			UCERF2_TimeIndependentEpistemicList ucerf2_indep_erf_list = checkOutUCERF2_IndepERF();
			numUCEF2_IndepERFs = ucerf2_indep_erf_list.getNumERFs();
			returnUCERF2_IndepERF(ucerf2_indep_erf_list);
			ucerf2_indep_erf_list = null;
			
			try {
				mainFaultsMap = FaultModels.parseNamedFaultsAltFile(UCERF3_DataUtils.getReader("FaultModels",
						"MainFaultsForTimeDepComparison.txt"));
				// now combine elsinore
				HashSet<Integer> elsinoreIDs = new HashSet<Integer>();
				elsinoreIDs.addAll(mainFaultsMap.remove("Elsinore FM3.1"));
				elsinoreIDs.addAll(mainFaultsMap.remove("Elsinore FM3.2"));
				mainFaultsMap.put("Elsinore", Lists.newArrayList(elsinoreIDs));
				mainFaultsSorted = Lists.newArrayList(mainFaultsMap.keySet());
				Collections.sort(mainFaultsSorted);
				mainFaultsRuptures = Maps.newHashMap();
				parentSectRuptures = Maps.newHashMap();
				parentSectNamesMap = Maps.newHashMap();
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
			
			for (double duration : durations) {
				solMPDs.put(duration, buildPopulatedList(regions.size()));
				solMFDs.put(duration, buildPopulatedList(regions.size()));
				solOnMPDs.put(duration, buildPopulatedList(regions.size()));
				solOffMPDs.put(duration, buildPopulatedList(regions.size()));
				ucerf2DepMPDs.put(duration, buildPopulatedFuncList(regions.size(), numUCEF2_DepERFs));
				ucerf2DepMFDs.put(duration, buildPopulatedFuncList(regions.size(), numUCEF2_DepERFs));
				ucerf2DepOnMPDs.put(duration, buildPopulatedFuncList(regions.size(), numUCEF2_DepERFs));
				ucerf2DepOffMPDs.put(duration, buildPopulatedFuncList(regions.size(), numUCEF2_DepERFs));
				ucerf2IndepMPDs.put(duration, buildPopulatedFuncList(regions.size(), numUCEF2_IndepERFs));
				ucerf2IndepMFDs.put(duration, buildPopulatedFuncList(regions.size(), numUCEF2_IndepERFs));
				
				solMainFaultProbs.put(duration, buildPopulatedMainFaultMap(mainFaultsMap));
				solMainFaultRates.put(duration, buildPopulatedMainFaultMap(mainFaultsMap));
				Map<MagDependentAperiodicityOptions, Map<FaultModels, XY_DataSetList[]>> sectMap =
						Maps.newHashMap();
				for (MagDependentAperiodicityOptions cov : covs) {
					Map<FaultModels, XY_DataSetList[]> fmMap = Maps.newHashMap();
					sectMap.put(cov, fmMap);
				}
				solSubSectProbs.put(duration, sectMap);
				
				Map<MagDependentAperiodicityOptions, Map<FaultModels, Map<Integer, XY_DataSetList>>>
						parentSectMap = Maps.newHashMap();
				for (MagDependentAperiodicityOptions cov : covs) {
					 Map<FaultModels, Map<Integer, XY_DataSetList>> fmMap = Maps.newHashMap();
					 parentSectMap.put(cov, fmMap);
				}
				solParentSectProbs.put(duration, parentSectMap);
//				ucerf2DepMainFaults.put(duration, buildPopulatedMainFaultMap(mainFaultsMap));
				
//				List<BranchSensitivityHistogram> hists = Lists.newArrayList();
//				for (int r=0; r<regions.size(); r++)
//					hists.add(new BranchSensitivityHistogram("Probability"));
//				regionM6p7Hists.put(duration, hists);
				List<Map<LogicTreeBranch, Map<MagDependentAperiodicityOptions, Double>>> maps = Lists.newArrayList();
				for (int r=0; r<regions.size(); r++) {
					Map<LogicTreeBranch, Map<MagDependentAperiodicityOptions, Double>> map = Maps.newHashMap();
					maps.add(map);
				}
				regionM6p7Vals.put(duration, maps);
			}
			
			weights = Maps.newHashMap();
			fmWeights = Maps.newHashMap();
			branches = Maps.newHashMap();
			for (double duration : durations) {
				weights.put(duration, new ArrayList<Double>());
				fmWeights.put(duration, new HashMap<FaultModels, List<Double>>());
				branches.put(duration, new ArrayList<LogicTreeBranch>());
			}
			ucerf2DepWeights = new double[numUCEF2_DepERFs];
			ucerf2IndepWeights = new double[numUCEF2_IndepERFs];
		}
		
		private static List<Map<MagDependentAperiodicityOptions, XY_DataSetList>> buildPopulatedList(int num) {
			List<Map<MagDependentAperiodicityOptions, XY_DataSetList>> l = Lists.newArrayList();
			for (int i=0; i<num; i++) {
				Map<MagDependentAperiodicityOptions, XY_DataSetList> map = Maps.newHashMap();
				for (MagDependentAperiodicityOptions cov : covs)
					map.put(cov, new XY_DataSetList());
				l.add(map);
			}
			return l;
		}
		
		private static Map<MagDependentAperiodicityOptions, Map<String, XY_DataSetList>> buildPopulatedMainFaultMap(
				Map<String, List<Integer>> mainFaultsMap) {
			Map<MagDependentAperiodicityOptions, Map<String, XY_DataSetList>> map = Maps.newHashMap();
			for (MagDependentAperiodicityOptions cov : covs) {
				Map<String, XY_DataSetList> faultMap = Maps.newHashMap();
				for (String fault : mainFaultsMap.keySet())
					faultMap.put(fault, new XY_DataSetList());
				map.put(cov, faultMap);
			}
			return map;
		}
		
		private static List<EvenlyDiscretizedFunc[]> buildPopulatedFuncList(int num, int numUCEF2_ERFs) {
			List<EvenlyDiscretizedFunc[]> l = Lists.newArrayList();
			for (int i=0; i<num; i++)
				l.add(new EvenlyDiscretizedFunc[numUCEF2_ERFs]);
			return l;
		}

		/**
		 * We store one UCERF2 comparison ERF per thread. Once checked out, and ERF should be 
		 * returned via the returnUCERF2_ERF method
		 * @return
		 */
		private synchronized UCERF2_TimeDependentEpistemicList checkOutUCERF2_DepERF() {
			if (ucerf2_dep_erf_lists.isEmpty()) {
				// TODO Params?
				UCERF2_TimeDependentEpistemicList ucerf2_erf_list = new UCERF2_TimeDependentEpistemicList();
				ucerf2_erf_list.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME,
						UCERF2.FULL_DDW_FLOATER);
				ucerf2_erf_list.setParameter(UCERF2.BACK_SEIS_NAME,
						UCERF2.BACK_SEIS_INCLUDE);
				ucerf2_erf_list.setParameter(UCERF2.BACK_SEIS_RUP_NAME,
						UCERF2.BACK_SEIS_RUP_POINT);
				return ucerf2_erf_list;
			}
			return ucerf2_dep_erf_lists.pop();
		}

		/**
		 * We store one UCERF2 comparison ERF per thread. Once checked out, and ERF should be 
		 * returned via the returnUCERF2_ERF method
		 * @return
		 */
		private synchronized UCERF2_TimeIndependentEpistemicList checkOutUCERF2_IndepERF() {
			if (ucerf2_indep_erf_lists.isEmpty()) {
				// TODO Params?
				UCERF2_TimeIndependentEpistemicList ucerf2_erf_list = new UCERF2_TimeIndependentEpistemicList();
				ucerf2_erf_list.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME,
						UCERF2.FULL_DDW_FLOATER);
				ucerf2_erf_list.setParameter(UCERF2.BACK_SEIS_NAME,
						UCERF2.BACK_SEIS_INCLUDE);
				ucerf2_erf_list.setParameter(UCERF2.BACK_SEIS_RUP_NAME,
						UCERF2.BACK_SEIS_RUP_POINT);
				return ucerf2_erf_list;
			}
			return ucerf2_indep_erf_lists.pop();
		}

		/**
		 * Return the ERF to the stack for a future thread to use
		 * @param erf
		 */
		private synchronized void returnUCERF2_DepERF(
				UCERF2_TimeDependentEpistemicList erf) {
			ucerf2_dep_erf_lists.push(erf);
		}

		/**
		 * Return the ERF to the stack for a future thread to use
		 * @param erf
		 */
		private synchronized void returnUCERF2_IndepERF(
				UCERF2_TimeIndependentEpistemicList erf) {
			ucerf2_indep_erf_lists.push(erf);
		}

		/**
		 * Calculate UCERF2 MFDs for the given index (up to the total number of UCERF2 branches).
		 * @param erfIndex
		 */
		private void calcUCERF2_DepMFDs(int erfIndex, double duration) {
			UCERF2_TimeDependentEpistemicList ucerf2_erf_list = checkOutUCERF2_DepERF();
			ucerf2_erf_list.getTimeSpan().setDuration(duration);
			ucerf2_erf_list.getTimeSpan().setStartTime(2007);
			Preconditions.checkState(ucerf2_erf_list.getTimeSpan().getDuration() == duration);
			ucerf2_erf_list.updateForecast();
			ERF erf = ucerf2_erf_list.getERF(erfIndex);
			
			ucerf2DepWeights[erfIndex] = ucerf2_erf_list
					.getERF_RelativeWeight(erfIndex);
			
			// replace following as sum of on- and off-fault MFDs below
//			System.out.println("Calculating UCERF2 MFDs for branch "
//					+ erfIndex + ", "+regions.size()+" regions");
//			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
//				Region region = regions.get(regionIndex);
//				SummedMagFreqDist mfdPart = ERF_Calculator
//						.getParticipationMagFreqDistInRegion(erf, region, minX,
//								num, delta, true);
//				ucerf2MFDs.get(regionIndex)[erfIndex] = mfdPart
//						.getCumRateDistWithOffset();
//			}
			
			System.out.println("Calculating UCERF2 On Fault MFDs for branch "
					+ erfIndex + ", "+regions.size()+" regions");
			// on fault
			ucerf2_erf_list.getParameter(UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_EXCLUDE);
			erf = ucerf2_erf_list.getERF(erfIndex);
			EvenlyDiscretizedFunc[] onCmlMFDs = new EvenlyDiscretizedFunc[regions.size()];
			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
				Region region = regions.get(regionIndex);
				SummedMagFreqDist mfdPart;
				if (NUCLEATION_PROBS)
					mfdPart = ERF_Calculator.getMagFreqDistInRegionFaster(erf, region, minX, num, delta, true);
				else
					mfdPart = ERF_Calculator
						.getParticipationMagFreqDistInRegion(erf, region, minX, num, delta, true);
				if(INCLUDE_AFTERSHOCKS)
					mfdPart.scale(1.0/FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS);
				onCmlMFDs[regionIndex] = mfdPart.getCumRateDistWithOffset();
				ucerf2DepOnMPDs.get(duration).get(regionIndex)[erfIndex] =
						FaultSysSolutionERF_Calc.calcProbsFromSummedMFD(onCmlMFDs[regionIndex], duration);
			}
			System.out.println("Calculating UCERF2 Off Fault MFDs for branch "
					+ erfIndex + ", "+regions.size()+" regions");
			// off fault
			ucerf2_erf_list.getParameter(UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_ONLY);
			erf = ucerf2_erf_list.getERF(erfIndex);
			EvenlyDiscretizedFunc[] offCmlMFDs = new EvenlyDiscretizedFunc[regions.size()];
			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
				Region region = regions.get(regionIndex);
				IncrementalMagFreqDist mfdPart;
				if (NUCLEATION_PROBS)
					mfdPart = ERF_Calculator.getMagFreqDistInRegionFaster(erf, region, minX, num, delta, true);
				else
					mfdPart = ERF_Calculator
						.getParticipationMagFreqDistInRegion(erf, region, minX, num, delta, true);
				if(INCLUDE_AFTERSHOCKS) {
					// it's a summed, turn it into an incremental to allow set operation
					IncrementalMagFreqDist scaled = new IncrementalMagFreqDist(
							mfdPart.getMinX(), mfdPart.size(), mfdPart.getDelta());
					for(int i=0;i<mfdPart.size();i++) {
						double scale = GardnerKnopoffAftershockFilter.scaleForMagnitude(mfdPart.getX(i));
						scaled.set(i, mfdPart.getY(i)/scale);	// divide to add aftershocks back in
					}
					mfdPart = scaled;
				}
				offCmlMFDs[regionIndex] = mfdPart.getCumRateDistWithOffset();
				ucerf2DepOffMPDs.get(duration).get(regionIndex)[erfIndex] =
						FaultSysSolutionERF_Calc.calcProbsFromSummedMFD(offCmlMFDs[regionIndex], duration);
			}

			// sum the above on and off MFDs to get the total
			System.out.println("Calculating UCERF2 MFDs for branch "
					+ erfIndex + ", "+regions.size()+" regions");
			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
				SummedMagFreqDist summedMFD = new SummedMagFreqDist(minX-delta/2,num, delta);	// note offset minX because it's cumulative
				summedMFD.addIncrementalMagFreqDist(onCmlMFDs[regionIndex]);
				summedMFD.addIncrementalMagFreqDist(offCmlMFDs[regionIndex]);
				ucerf2DepMPDs.get(duration).get(regionIndex)[erfIndex] =
						FaultSysSolutionERF_Calc.calcProbsFromSummedMFD(summedMFD, duration);
				summedMFD.scale(duration);
				ucerf2DepMFDs.get(duration).get(regionIndex)[erfIndex] = summedMFD;
			}
			
			ucerf2_erf_list.getParameter(UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_INCLUDE);
			
			returnUCERF2_DepERF(ucerf2_erf_list);
		}

		/**
		 * Calculate UCERF2 MFDs for the given index (up to the total number of UCERF2 branches).
		 * @param erfIndex
		 */
		private void calcUCERF2_IndepMFDs(int erfIndex, double duration) {
			UCERF2_TimeIndependentEpistemicList ucerf2_erf_list = checkOutUCERF2_IndepERF();
			ucerf2_erf_list.getTimeSpan().setDuration(duration);
			Preconditions.checkState(ucerf2_erf_list.getTimeSpan().getDuration() == duration);
			ucerf2_erf_list.updateForecast();
			ERF erf = ucerf2_erf_list.getERF(erfIndex);
			
			ucerf2IndepWeights[erfIndex] = ucerf2_erf_list
					.getERF_RelativeWeight(erfIndex);
			
			// replace following as sum of on- and off-fault MFDs below
//			System.out.println("Calculating UCERF2 MFDs for branch "
//					+ erfIndex + ", "+regions.size()+" regions");
//			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
//				Region region = regions.get(regionIndex);
//				SummedMagFreqDist mfdPart = ERF_Calculator
//						.getParticipationMagFreqDistInRegion(erf, region, minX,
//								num, delta, true);
//				ucerf2MFDs.get(regionIndex)[erfIndex] = mfdPart
//						.getCumRateDistWithOffset();
//			}
			
			System.out.println("Calculating UCERF2  Time Independent On Fault MFDs for branch "
					+ erfIndex + ", "+regions.size()+" regions");
			// on fault
			ucerf2_erf_list.getParameter(UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_EXCLUDE);
			erf = ucerf2_erf_list.getERF(erfIndex);
			EvenlyDiscretizedFunc[] onCmlMFDs = new EvenlyDiscretizedFunc[regions.size()];
			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
				Region region = regions.get(regionIndex);
				SummedMagFreqDist mfdPart;
				if (NUCLEATION_PROBS)
					mfdPart = ERF_Calculator.getMagFreqDistInRegionFaster(erf, region, minX, num, delta, true);
				else
					mfdPart = ERF_Calculator
						.getParticipationMagFreqDistInRegion(erf, region, minX, num, delta, true);
				if(INCLUDE_AFTERSHOCKS)
					mfdPart.scale(1.0/FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS);
				onCmlMFDs[regionIndex] = mfdPart.getCumRateDistWithOffset();
			}
			System.out.println("Calculating UCERF2 Off Fault MFDs for branch "
					+ erfIndex + ", "+regions.size()+" regions");
			// off fault
			ucerf2_erf_list.getParameter(UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_ONLY);
			erf = ucerf2_erf_list.getERF(erfIndex);
			EvenlyDiscretizedFunc[] offCmlMFDs = new EvenlyDiscretizedFunc[regions.size()];
			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
				Region region = regions.get(regionIndex);
				IncrementalMagFreqDist mfdPart;
				if (NUCLEATION_PROBS)
					mfdPart = ERF_Calculator.getMagFreqDistInRegionFaster(erf, region, minX, num, delta, true);
				else
					mfdPart = ERF_Calculator
						.getParticipationMagFreqDistInRegion(erf, region, minX, num, delta, true);
				if(INCLUDE_AFTERSHOCKS) {
					// it's a summed, turn it into an incremental to allow set operation
					IncrementalMagFreqDist scaled = new IncrementalMagFreqDist(
							mfdPart.getMinX(), mfdPart.size(), mfdPart.getDelta());
					for(int i=0;i<mfdPart.size();i++) {
						double scale = GardnerKnopoffAftershockFilter.scaleForMagnitude(mfdPart.getX(i));
						scaled.set(i, mfdPart.getY(i)/scale);	// divide to add aftershocks back in
					}
					mfdPart = scaled;
				}
				offCmlMFDs[regionIndex] = mfdPart.getCumRateDistWithOffset();
			}

			// sum the above on and off MFDs to get the total
			System.out.println("Calculating UCERF2 MFDs for branch "
					+ erfIndex + ", "+regions.size()+" regions");
			for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
				SummedMagFreqDist summedMFD = new SummedMagFreqDist(minX-delta/2,num, delta);	// note offset minX because it's cumulative
				summedMFD.addIncrementalMagFreqDist(onCmlMFDs[regionIndex]);
				summedMFD.addIncrementalMagFreqDist(offCmlMFDs[regionIndex]);
				ucerf2IndepMPDs.get(duration).get(regionIndex)[erfIndex] =
						FaultSysSolutionERF_Calc.calcProbsFromSummedMFD(summedMFD, duration);
				summedMFD.scale(duration);
				ucerf2IndepMFDs.get(duration).get(regionIndex)[erfIndex] = summedMFD;
			}
			
			ucerf2_erf_list.getParameter(UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_INCLUDE);
			
			returnUCERF2_IndepERF(ucerf2_erf_list);
		}

		/**
		 * Makes sure that all UCERF2 MFDs have been calculated (if we have less UCERF3 logic tree
		 * branches than UCERF2 this can take a little while).
		 */
		private void checkCalcAllUCERF2MFDs() {
			if (hostname.startsWith("steel")) {
				System.out.println("Skipping UCERF2 finish as local test");
				return;
			}
			for (int erfIndex = 0; erfIndex < numUCEF2_DepERFs; erfIndex++) {
				for (double duration : durations) {
					if (ucerf2DepMPDs.get(duration).get(0)[erfIndex] == null)
						calcUCERF2_DepMFDs(erfIndex, duration);
				}
			}
			for (int erfIndex = 0; erfIndex < numUCEF2_IndepERFs; erfIndex++) {
				for (double duration : durations) {
					if (ucerf2IndepMPDs.get(duration).get(0)[erfIndex] == null)
						calcUCERF2_IndepMFDs(erfIndex, duration);
				}
			}
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			throw new IllegalStateException("Should not be called, ERF plot!");
		}
		
		public static void calcFaultProbs(EvenlyDiscretizedFunc probFunc, EvenlyDiscretizedFunc rateFunc, FaultSystemSolutionERF erf,
				FaultSystemRupSet rupSet, Collection<Integer> faultRups) {
			List<List<Double>> probs = Lists.newArrayList();
			for (int i=0; i<probFunc.size(); i++)
				probs.add(new ArrayList<Double>());
			double duration = erf.getTimeSpan().getDuration();
			for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
				int rupID = erf.getFltSysRupIndexForSource(sourceID);
				if (faultRups.contains(rupID)) {
					ProbEqkSource source = erf.getSource(sourceID);
					double prob = source.computeTotalProb();
					double rate = source.computeTotalEquivMeanAnnualRate(duration)*duration;
					double mag = rupSet.getMagForRup(rupID);
					for (int i=0; i<probFunc.size(); i++) {
						if (mag >= probFunc.getX(i)) {
							probs.get(i).add(prob);
							if (rateFunc != null)
								rateFunc.add(i, rate);
						}
					}
				}
			}
			for (int i=0; i<probFunc.size(); i++) 
				probFunc.set(i, FaultSysSolutionERF_Calc.calcSummedProbs(probs.get(i)));
		}
		
//		private static List<Double> testProbs = Lists.newArrayList();
//		private static List<Double> testRates = Lists.newArrayList();
//		private static List<Double> testWeights = Lists.newArrayList();
//		
//		private static void testRateProbs() {
//			double duration = 30d;
//			
//			double baProb = FaultSystemSolutionFetcher.calcScaledAverage(
//					Doubles.toArray(testProbs), Doubles.toArray(testWeights));
//			double baRate = FaultSystemSolutionFetcher.calcScaledAverage(
//					Doubles.toArray(testRates), Doubles.toArray(testWeights));
//			// now calculate rate from ba probs by converting to rates first
//			double[] baRatesConv = new double[testProbs.size()];
//			for (int i=0; i<baRatesConv.length; i++)
//				baRatesConv[i] = -Math.log(1 - testProbs.get(i))/duration;
//			double baRateFromProbs = FaultSystemSolutionFetcher.calcScaledAverage(
//					baRatesConv, Doubles.toArray(testWeights));
//			System.out.println("BA Prob: "+baProb);
//			System.out.println("BA Rate: "+baRate);
//			System.out.println("BA Rate from Conv Probs: "+baRateFromProbs);
//			System.out.println("BA Rate from BA Prob: "+(-Math.log(1 - baProb)/duration));
//		}

		@Override
		protected void processERF(LogicTreeBranch branch,
				FaultSystemSolutionERF erf, int solIndex) {
			debug(solIndex, "checking UCERF2");
			// do UCERF2 if applicable so that we don't have to do them all single threaded at the end
			for (double duration : durations) {
				if (solIndex < numUCEF2_DepERFs)
					calcUCERF2_DepMFDs(solIndex, duration);
				if (solIndex < numUCEF2_IndepERFs)
					calcUCERF2_IndepMFDs(solIndex, duration);
			}
			debug(solIndex, " done UCERF2");

			FaultModels fm = branch.getValue(FaultModels.class);

			// this cache keeps track of which rupture is within each of the regions.
			FSSRupsInRegionCache rupsCache = rupInRegionsCaches.get(fm);
			if (rupsCache == null) {
				synchronized (this) {
					if (!rupInRegionsCaches.containsKey(fm)) {
						rupInRegionsCaches.put(fm, new FSSRupsInRegionCache());
					}
				}
				rupsCache = rupInRegionsCaches.get(fm);
			}
			
			FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
			
			Map<String, HashSet<Integer>> fmRups = mainFaultsRuptures.get(fm);
			if (fmRups == null) {
				synchronized (fm) {
					if (mainFaultsRuptures.get(fm) == null) {
						Map<String, HashSet<Integer>> newFMRups = Maps.newHashMap();
						
						for (String fault : mainFaultsSorted) {
							HashSet<Integer> rups = new HashSet<Integer>();
							for (Integer parentID : mainFaultsMap.get(fault)) {
								List<Integer> parentRups = rupSet.getRupturesForParentSection(parentID);
								if (parentRups != null)
									rups.addAll(parentRups);
							}
							newFMRups.put(fault, rups);
						}
						
						mainFaultsRuptures.put(fm, newFMRups);
					}
					fmRups = mainFaultsRuptures.get(fm);
				}
			}
			
			Map<Integer, HashSet<Integer>> fmParentRups = parentSectRuptures.get(fm);
			if (fmParentRups == null) {
				synchronized (fm) {
					if (parentSectRuptures.get(fm) == null) {
						Map<Integer, HashSet<Integer>> newParentRups = Maps.newHashMap();
						Map<Integer, String> parentNames = Maps.newHashMap();
						
						for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
							Integer parentID = sect.getParentSectionId();
							if (!parentNames.containsKey(parentID)) {
								parentNames.put(parentID, sect.getParentSectionName());
							}
						}
						
						for (int parentID : parentNames.keySet())
							newParentRups.put(parentID, new HashSet<Integer>(
									rupSet.getRupturesForParentSection(parentID)));
						
						parentSectRuptures.put(fm, newParentRups);
						parentSectNamesMap.put(fm, parentNames);
						
						for (double duration : durations) {
							for (MagDependentAperiodicityOptions cov : covs) {
								Map<FaultModels, Map<Integer, XY_DataSetList>> fmResultsMap =
										solParentSectProbs.get(duration).get(cov);
								Map<Integer, XY_DataSetList> resultsMap = Maps.newHashMap();
								for (int parentID : parentNames.keySet())
									resultsMap.put(parentID, new XY_DataSetList());
								fmResultsMap.put(fm, resultsMap);
							}
						}
					}
					fmParentRups = parentSectRuptures.get(fm);
				}
			}

			debug(solIndex, "done cache");
			
			for (double duration : durations) {
				Map<MagDependentAperiodicityOptions, List<EvenlyDiscretizedFunc>> mfds = Maps.newHashMap();
				Map<MagDependentAperiodicityOptions, List<EvenlyDiscretizedFunc>> offMFDs = Maps.newHashMap();
				Map<MagDependentAperiodicityOptions, List<EvenlyDiscretizedFunc>> onMFDs = Maps.newHashMap();
				Map<MagDependentAperiodicityOptions, Map<String, EvenlyDiscretizedFunc>> faultMPDs = Maps.newHashMap();
				Map<MagDependentAperiodicityOptions, Map<String, EvenlyDiscretizedFunc>> faultMFDs = Maps.newHashMap();
				Map<MagDependentAperiodicityOptions, EvenlyDiscretizedFunc[]> sectMPDs = Maps.newHashMap();
				Map<MagDependentAperiodicityOptions, Map<Integer, EvenlyDiscretizedFunc>> parentSectMPDs = Maps.newHashMap();
				
				for (MagDependentAperiodicityOptions cov : covs) {
					mfds.put(cov, new ArrayList<EvenlyDiscretizedFunc>());
					offMFDs.put(cov, new ArrayList<EvenlyDiscretizedFunc>());
					onMFDs.put(cov, new ArrayList<EvenlyDiscretizedFunc>());
					faultMPDs.put(cov, new HashMap<String, EvenlyDiscretizedFunc>());
					faultMFDs.put(cov, new HashMap<String, EvenlyDiscretizedFunc>());
					EvenlyDiscretizedFunc[] subSectMPDs = new EvenlyDiscretizedFunc[rupSet.getNumSections()];
					sectMPDs.put(cov, subSectMPDs);
					parentSectMPDs.put(cov, new HashMap<Integer, EvenlyDiscretizedFunc>());
					
					if (cov == null) {
						erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
					} else {
						erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
						erf.setParameter(MagDependentAperiodicityParam.NAME, cov);
					}
					double origDuration = erf.getTimeSpan().getDuration();
					erf.getTimeSpan().setDuration(duration);
					erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
					erf.updateForecast();
					
					// main faults
					debug(solIndex, "calculating mian fault probs, dur="+duration);
					for (String faultName : mainFaultsSorted) {
						HashSet<Integer> faultRups = fmRups.get(faultName);
						// shift since this is cumulative
						EvenlyDiscretizedFunc probFunc = new EvenlyDiscretizedFunc(minX-delta*0.5, num, delta);
						EvenlyDiscretizedFunc rateFunc = new EvenlyDiscretizedFunc(minX-delta*0.5, num, delta);
						calcFaultProbs(probFunc, rateFunc, erf, rupSet, faultRups);
						faultMPDs.get(cov).put(faultName, probFunc);
						faultMFDs.get(cov).put(faultName, rateFunc);
					}
					
					// sub sections
					debug(solIndex, "calculating sub section probs, dur="+duration);
					for (int s=0; s<rupSet.getNumSections(); s++) {
						HashSet<Integer> sectRups = new HashSet<Integer>(rupSet.getRupturesForSection(s));
						subSectMPDs[s] = new EvenlyDiscretizedFunc(minX-delta*0.5, num, delta);
//						debug(solIndex, "calculating sub section probs, dur="+duration+", s="+s);
						
						calcFaultProbs(subSectMPDs[s], null, erf, rupSet, sectRups);
					}
					
					// parent sections
					debug(solIndex, "calculating parent section probs, dur="+duration);
					for (int parentID : fmParentRups.keySet()) {
						HashSet<Integer> sectRups = fmParentRups.get(parentID);
						EvenlyDiscretizedFunc parentSectMPD = new EvenlyDiscretizedFunc(minX-delta*0.5, num, delta);
						
						calcFaultProbs(parentSectMPD, null, erf, rupSet, sectRups);
						parentSectMPDs.get(cov).put(parentID, parentSectMPD);
					}
					
					// get total MFD
					debug(solIndex, "calculating region probs, dur="+duration);
					for (int r = 0; r < regions.size(); r++) {
						Region region = regions.get(r);

						Stopwatch watch = Stopwatch.createStarted();
						debug(solIndex, "calculating region (COMBINED) " + r);
						// System.out.println("Calculating branch "+solIndex+" region "+r);
						SummedMagFreqDist ucerf3_Part = ERF_Calculator
								.getParticipationMagFreqDistInRegion(erf, region, minX,
										num, delta, true, rupsCache);
						watch.stop();
						debug(solIndex,
								"done region (COMBINED) " + r + " ("
										+ watch.elapsed(TimeUnit.SECONDS) + " s)");
						// System.out.println("Took "+(watch.elapsedMillis()/1000d)+" secst for branch "
						// +solIndex+" region "+r+" ("+region.getName()+")");

						mfds.get(cov).add(ucerf3_Part.getCumRateDistWithOffset());
					}

					erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
					erf.updateForecast();
					
					// get on fault MFD
					for (int r = 0; r < regions.size(); r++) {
						Region region = regions.get(r);

						Stopwatch watch = Stopwatch.createStarted();
						debug(solIndex, "calculating region (ON FAULT) " + r);
						// System.out.println("Calculating branch "+solIndex+" region "+r);
						SummedMagFreqDist ucerf3_Part = ERF_Calculator
								.getParticipationMagFreqDistInRegion(erf, region, minX,
										num, delta, true, rupsCache);
						watch.stop();
						debug(solIndex,
								"done region (ON FAULT) " + r + " ("
										+ watch.elapsed(TimeUnit.SECONDS) + " s)");
						// System.out.println("Took "+(watch.elapsedMillis()/1000d)+" secst for branch "
						// +solIndex+" region "+r+" ("+region.getName()+")");

						onMFDs.get(cov).add(ucerf3_Part.getCumRateDistWithOffset());
					}
					
					// get off fault - we can either infer off fault from total and on or calculate it again
					if (infer_off_fault) {
						for (int r = 0; r < regions.size(); r++) {
							DiscretizedFunc totMFD = mfds.get(cov).get(r);
							DiscretizedFunc onMFD = mfds.get(cov).get(r);
							EvenlyDiscretizedFunc offMFD = new EvenlyDiscretizedFunc(totMFD.getMinX(), totMFD.getMaxX(), totMFD.size());
							for (int i=0; i<totMFD.size(); i++)
								offMFD.set(i, totMFD.getY(i) - onMFD.getY(i));
							offMFDs.get(cov).add(offMFD);
						}
					} else {
						erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);
						erf.updateForecast();
						
						for (int r = 0; r < regions.size(); r++) {
							Region region = regions.get(r);

							Stopwatch watch = Stopwatch.createStarted();
							debug(solIndex, "calculating region (OFF FAULT) " + r);
							// System.out.println("Calculating branch "+solIndex+" region "+r);
							SummedMagFreqDist ucerf3_Part = ERF_Calculator
									.getParticipationMagFreqDistInRegion(erf, region, minX,
											num, delta, true, null);
							watch.stop();
							debug(solIndex,
									"done region (OFF FAULT) " + r + " ("
											+ watch.elapsed(TimeUnit.SECONDS) + " s)");
							// System.out.println("Took "+(watch.elapsedMillis()/1000d)+" secst for branch "
							// +solIndex+" region "+r+" ("+region.getName()+")");

							offMFDs.get(cov).add(ucerf3_Part.getCumRateDistWithOffset());
						}
					}
					
					erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
					erf.getTimeSpan().setDuration(origDuration);
					erf.updateForecast();
				}

				debug(solIndex, " archiving");
				synchronized (this) {
					// store results
					double branchWeight = weightProvider.getWeight(branch);
					weights.get(duration).add(branchWeight);
					List<Double> weightsForFM = fmWeights.get(duration).get(fm);
					if (weightsForFM == null) {
						weightsForFM = Lists.newArrayList();
						fmWeights.get(duration).put(fm, weightsForFM);
					}
					weightsForFM.add(branchWeight);
					branches.get(duration).add(branch);
					for (int r = 0; r < regions.size(); r++) {
						Map<MagDependentAperiodicityOptions, Double> map6p7 = Maps.newHashMap();
						for (MagDependentAperiodicityOptions cov : covs) {
							EvenlyDiscretizedFunc mfd = mfds.get(cov).get(r);
							EvenlyDiscretizedFunc probs = FaultSysSolutionERF_Calc.calcProbsFromSummedMFD(mfd, duration);
							solMPDs.get(duration).get(r).get(cov).add(probs);
							EvenlyDiscretizedFunc scaledMFD = mfd.deepClone();
							scaledMFD.scale(duration);
							
//							if (duration == 30d && r == 0 &&
//									cov == MagDependentAperiodicityOptions.MID_VALUES) {
//								synchronized (CompoundFSSPlots.class) {
//									double prob = probs.getY(16);
//									double rate = scaledMFD.getY(16);
//									testProbs.add(prob);
//									testRates.add(rate);
//									testWeights.add(branchWeight);
//								}
//							}
							solMFDs.get(duration).get(r).get(cov).add(scaledMFD);
							double prob6p7 = probs.getClosestYtoX(6.7d);
							map6p7.put(cov, prob6p7);
							solOnMPDs.get(duration).get(r).get(cov).add(
									FaultSysSolutionERF_Calc.calcProbsFromSummedMFD(onMFDs.get(cov).get(r), duration));
							solOffMPDs.get(duration).get(r).get(cov).add(
									FaultSysSolutionERF_Calc.calcProbsFromSummedMFD(offMFDs.get(cov).get(r), duration));
						}
						regionM6p7Vals.get(duration).get(r).put(branch, map6p7);
					}
					for (String faultName : mainFaultsSorted) {
						for (MagDependentAperiodicityOptions cov : covs) {
							solMainFaultProbs.get(duration).get(cov).get(faultName).add(
									faultMPDs.get(cov).get(faultName));
							solMainFaultRates.get(duration).get(cov).get(faultName).add(
									faultMFDs.get(cov).get(faultName));
						}
					}
					for (int parentID : fmParentRups.keySet()) {
						for (MagDependentAperiodicityOptions cov : covs) {
							solParentSectProbs.get(duration).get(cov).get(fm).get(parentID).add(
									parentSectMPDs.get(cov).get(parentID));
						}
					}
					for (MagDependentAperiodicityOptions cov : covs) {
						EvenlyDiscretizedFunc[] sectFuncs = sectMPDs.get(cov);
						Map<FaultModels, XY_DataSetList[]> fmMap = solSubSectProbs.get(duration).get(cov);
						synchronized (this) {
							if (!fmMap.containsKey(fm)) {
								XY_DataSetList[] lists = new XY_DataSetList[rupSet.getNumSections()];
								for (int s=0; s<lists.length; s++) {
									lists[s] = new XY_DataSetList();
									lists[s].setName(rupSet.getFaultSectionData(s).getName());
								}
								fmMap.put(fm, lists);
							}
						}
						XY_DataSetList[] lists = fmMap.get(fm);
						Preconditions.checkState(sectFuncs.length == lists.length);
						for (int s=0; s<lists.length; s++)
							lists[s].add(sectFuncs[s]);
					}
				}
				debug(solIndex, " archiving done");
			}

			
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				ERFBasedRegionalMagProbPlot o = (ERFBasedRegionalMagProbPlot) otherCalc;
				for (double duration : durations) {
					for (int r = 0; r < regions.size(); r++) {
						for (MagDependentAperiodicityOptions cov : covs) {
							solMPDs.get(duration).get(r).get(cov).addAll(
									o.solMPDs.get(duration).get(r).get(cov));
							solMFDs.get(duration).get(r).get(cov).addAll(
									o.solMFDs.get(duration).get(r).get(cov));
							solOnMPDs.get(duration).get(r).get(cov).addAll(
									o.solOnMPDs.get(duration).get(r).get(cov));
							solOffMPDs.get(duration).get(r).get(cov).addAll(
									o.solOffMPDs.get(duration).get(r).get(cov));
						}
						regionM6p7Vals.get(duration).get(r).putAll(o.regionM6p7Vals.get(duration).get(r));
//						regionM6p7Hists.get(duration).get(r).addAll(o.regionM6p7Hists.get(duration).get(r));
					}
					for (String faultName : mainFaultsSorted) {
						for (MagDependentAperiodicityOptions cov : covs) {
							solMainFaultProbs.get(duration).get(cov).get(faultName).addAll(
									o.solMainFaultProbs.get(duration).get(cov).get(faultName));
							solMainFaultRates.get(duration).get(cov).get(faultName).addAll(
									o.solMainFaultRates.get(duration).get(cov).get(faultName));
						}
					}
					for (MagDependentAperiodicityOptions cov : covs) {
						Map<FaultModels, XY_DataSetList[]> fmMap = solSubSectProbs.get(duration).get(cov);
						Map<FaultModels, XY_DataSetList[]> o_fmMap = o.solSubSectProbs.get(duration).get(cov);
						for (FaultModels fm : o_fmMap.keySet()) {
							if (!fmMap.containsKey(fm)) {
								fmMap.put(fm, o_fmMap.get(fm));
							} else {
								XY_DataSetList[] myLists = fmMap.get(fm);
								XY_DataSetList[] oLists = o_fmMap.get(fm);
								Preconditions.checkState(myLists.length == oLists.length);
								for (int s=0; s<myLists.length; s++)
									myLists[s].addAll(oLists[s]);
							}
						}
					}
					for (MagDependentAperiodicityOptions cov : covs) {
						Map<FaultModels, Map<Integer, XY_DataSetList>> fmMap =
								solParentSectProbs.get(duration).get(cov);
						Map<FaultModels, Map<Integer, XY_DataSetList>> o_fmMap =
								o.solParentSectProbs.get(duration).get(cov);
						for (FaultModels fm : o_fmMap.keySet()) {
							if (!fmMap.containsKey(fm)) {
								fmMap.put(fm, o_fmMap.get(fm));
							} else {
								Map<Integer, XY_DataSetList> myLists = fmMap.get(fm);
								Map<Integer, XY_DataSetList> oLists = o_fmMap.get(fm);
								Preconditions.checkState(myLists.size() == oLists.size());
								for (Integer parentID : myLists.keySet())
									myLists.get(parentID).addAll(oLists.get(parentID));
							}
						}
					}
					
					weights.get(duration).addAll(o.weights.get(duration));
					Map<FaultModels, List<Double>> weightsMap = fmWeights.get(duration);
					Map<FaultModels, List<Double>> oWeightsMap = o.fmWeights.get(duration);
					for (FaultModels fm : oWeightsMap.keySet()) {
						if (!weightsMap.containsKey(fm))
							weightsMap.put(fm, oWeightsMap.get(fm));
						else
							weightsMap.get(fm).addAll(oWeightsMap.get(fm));
					}
					branches.get(duration).addAll(o.branches.get(duration));
				}

				for (int e = 0; e < numUCEF2_DepERFs; e++) {
					for (double duration : durations) {
						if (o.ucerf2DepMPDs.get(duration).get(0)[e] != null) {
							for (int r = 0; r < regions.size(); r++) {
								ucerf2DepMPDs.get(duration).get(r)[e] = o.ucerf2DepMPDs.get(duration).get(r)[e];
								ucerf2DepMFDs.get(duration).get(r)[e] = o.ucerf2DepMFDs.get(duration).get(r)[e];
								ucerf2DepOnMPDs.get(duration).get(r)[e] = o.ucerf2DepOnMPDs.get(duration).get(r)[e];
								ucerf2DepOffMPDs.get(duration).get(r)[e] = o.ucerf2DepOffMPDs.get(duration).get(r)[e];
							}
							ucerf2DepWeights[e] = o.ucerf2DepWeights[e];
						}
					}
				}
				for (int e = 0; e < numUCEF2_IndepERFs; e++) {
					for (double duration : durations) {
						if (o.ucerf2IndepMPDs.get(duration).get(0)[e] != null) {
							for (int r = 0; r < regions.size(); r++) {
								ucerf2IndepMPDs.get(duration).get(r)[e] = o.ucerf2IndepMPDs.get(duration).get(r)[e];
								ucerf2IndepMFDs.get(duration).get(r)[e] = o.ucerf2IndepMFDs.get(duration).get(r)[e];
							}
							ucerf2IndepWeights[e] = o.ucerf2IndepWeights[e];
						}
					}
				}
			}
		}
		
		static BranchSensitivityHistogram buildHist(
				Map<LogicTreeBranch, Map<MagDependentAperiodicityOptions, Double>> map,
				BranchWeightProvider weightProv) {
			BranchSensitivityHistogram hist = new BranchSensitivityHistogram("Prob M>=6.7");
			for (LogicTreeBranch branch : map.keySet()) {
				double branchWeight = weightProv.getWeight(branch);
				Map<MagDependentAperiodicityOptions, Double> covMap = map.get(branch);
				for (MagDependentAperiodicityOptions cov : covMap.keySet()) {
					double prob6p7 = covMap.get(cov);
					String covName;
					if (cov == null)
						covName = "POISSON";
					else
						covName = cov.name();
					double subBranchWeight = branchWeight * FaultSystemSolutionERF.getWeightForCOV(cov);
					hist.addValues(branch, prob6p7, subBranchWeight, "MagDepAperiodicity", covName);
				}
			}
			
			return hist;
		}
		
//		private CSVFile<String> sfDebugCSV;

		@Override
		protected void doFinalizePlot() {
			ucerf2_dep_erf_lists.clear();
			ucerf2_indep_erf_lists.clear();
			System.gc();
			
			specs = Maps.newHashMap();
			faultSpecs = Maps.newHashMap();
			
			faultProbCSVs = HashBasedTable.create();
			regionProbCSVs = HashBasedTable.create();
			faultRateCSVs = HashBasedTable.create();
			regionRateCSVs = HashBasedTable.create();

			subSectsCSVs = HashBasedTable.create();
			subSectsPercentileCSVs = HashBasedTable.create();
			parentSectsCSVs = HashBasedTable.create();
			
//			sfDebugCSV = new CSVFile<String>(true);
//			sfDebugCSV.addLine("index", "branch", "weight", "prob", "equivRate");

			// make sure we've calculated all UCERF2 MFDs
			checkCalcAllUCERF2MFDs();

//			MeanUCERF2 erf = new MeanUCERF2();
//			erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME,
//					UCERF2.PROB_MODEL_POISSON);
//			erf.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME,
//					UCERF2.FULL_DDW_FLOATER);
//			erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
//			erf.setParameter(UCERF2.BACK_SEIS_RUP_NAME,
//					UCERF2.BACK_SEIS_RUP_POINT);
//			erf.getTimeSpan().setDuration(1d);
//			erf.updateForecast();
			
			Map<String, DiscretizedFunc[]> u2DepFaultVals = null;
			Map<String, DiscretizedFunc[]> u2IndepFaultVals = null;
			try {
				u2DepFaultVals = FaultSysSolutionERF_Calc.loadUCERF2MainFaultMPDs(true, true);
				// now fix for Elsinore
				u2DepFaultVals.put("Elsinore", u2DepFaultVals.get("Elsinore FM3.1"));
				u2IndepFaultVals = FaultSysSolutionERF_Calc.loadUCERF2MainFaultMPDs(true, false);
				// now fix for Elsinore
				u2IndepFaultVals.put("Elsinore", u2DepFaultVals.get("Elsinore FM3.1"));
			} catch (IOException e1) {
				ExceptionUtils.throwAsRuntimeException(e1);
			}

			for (double duration : durations) {
				List<Double> origWeights = this.weights.get(duration);
				List<LogicTreeBranch> origBranches = this.branches.get(duration);
				
				// this adds in each individual COV weight including poisson
				List<Double> weights = Lists.newArrayList();
				for (MagDependentAperiodicityOptions cov : covs) {
					double covWeight = FaultSystemSolutionERF.getWeightForCOV(cov);
					for (double weight : origWeights)
						weights.add(covWeight*weight);
				}
				Map<String, List<DiscretizedFunc>> depRegionMPDs = Maps.newHashMap();
				Map<String, List<DiscretizedFunc>> indepRegionMPDs = Maps.newHashMap();
				Map<String, DiscretizedFunc> u2DepRegionMPDs = Maps.newHashMap();
				Map<String, DiscretizedFunc> u2IndepRegionMPDs = Maps.newHashMap();
				Map<String, List<DiscretizedFunc>> depRegionMFDs = Maps.newHashMap();
				Map<String, List<DiscretizedFunc>> indepRegionMFDs = Maps.newHashMap();
				Map<String, DiscretizedFunc> u2DepRegionMFDs = Maps.newHashMap();
				Map<String, DiscretizedFunc> u2IndepRegionMFDs = Maps.newHashMap();
				List<String> regionNames = Lists.newArrayList();
				List<PlotSpec> durSpecs = Lists.newArrayList();
				specs.put(duration, durSpecs);
				for (int r = 0; r < regions.size(); r++) {
					// create plot spec for each region
					Region region = regions.get(r);
					regionNames.add(region.getName());

					XY_DataSetList ucerf2Funcs = new XY_DataSetList();
					XY_DataSetList ucerf2FreqFuncs = new XY_DataSetList();
					XY_DataSetList ucerf2OnFuncs = new XY_DataSetList();
					XY_DataSetList ucerf2OffFuncs = new XY_DataSetList();
					ArrayList<Double> ucerf2Weights = new ArrayList<Double>();
					for (int e = 0; e < ucerf2DepMPDs.get(duration).get(r).length; e++) {
						DiscretizedFunc mfd = ucerf2DepMPDs.get(duration).get(r)[e];
						if (mfd != null) {
							ucerf2Funcs.add(ucerf2DepMPDs.get(duration).get(r)[e]);
							ucerf2FreqFuncs.add(ucerf2DepMFDs.get(duration).get(r)[e]);
							ucerf2OnFuncs.add(ucerf2DepOnMPDs.get(duration).get(r)[e]);
							ucerf2OffFuncs.add(ucerf2DepOffMPDs.get(duration).get(r)[e]);
							ucerf2Weights.add(this.ucerf2DepWeights[e]);
						}
					}
					XY_DataSetList ucerf2IndepFuncs = new XY_DataSetList();
					XY_DataSetList ucerf2IndepFreqFuncs = new XY_DataSetList();
					ArrayList<Double> ucerf2IndepWeights = new ArrayList<Double>();
					for (int e = 0; e < ucerf2IndepMPDs.get(duration).get(r).length; e++) {
						DiscretizedFunc mfd = ucerf2IndepMPDs.get(duration).get(r)[e];
						if (mfd != null) {
							ucerf2IndepFuncs.add(ucerf2IndepMPDs.get(duration).get(r)[e]);
							ucerf2IndepFreqFuncs.add(ucerf2IndepMFDs.get(duration).get(r)[e]);
							ucerf2IndepWeights.add(this.ucerf2IndepWeights[e]);
						}
					}
					
					// these will include each COV branch as well
					XY_DataSetList ucerf3Funcs = new XY_DataSetList();
					XY_DataSetList ucerf3RateFuncs = new XY_DataSetList();
					XY_DataSetList ucerf3OnFuncs = new XY_DataSetList();
					XY_DataSetList ucerf3OffFuncs = new XY_DataSetList();
					XY_DataSetList ucerf3IndepFuncs = new XY_DataSetList();
					XY_DataSetList ucerf3IndepRateFuncs = new XY_DataSetList();
					
					for (MagDependentAperiodicityOptions cov : covs) {
						ucerf3Funcs.addAll(solMPDs.get(duration).get(r).get(cov));
						ucerf3RateFuncs.addAll(solMFDs.get(duration).get(r).get(cov));
						ucerf3OnFuncs.addAll(solOnMPDs.get(duration).get(r).get(cov));
						ucerf3OffFuncs.addAll(solOffMPDs.get(duration).get(r).get(cov));
					}
					ucerf3IndepFuncs.addAll(solMPDs.get(duration).get(r).get(null));
					ucerf3IndepRateFuncs.addAll(solMFDs.get(duration).get(r).get(null));
					
//					if (region.getName().toUpperCase().contains("SF") && (int)duration == 30) {
////						List<String> sfVals = Lists.newArrayList();
//						for (int i=0; i<ucerf3IndepFuncs.size(); i++) {
//							XY_DataSet func = ucerf3IndepFuncs.get(i);
//							double weight = origWeights.get(i);
////							sfVals.add("["+func.getClosestY(6.7d)+"*"+weight+"]");
//							double prob = func.getClosestY(6.7d);
//							double equivRate = -Math.log(1-prob)/duration;
//							sfDebugCSV.addLine(i+"", origBranches.get(i).buildFileName(),
//									weight+"", prob+"", equivRate+"");
//						}
////						System.out.println("SF Indep Vals: "+Joiner.on(",").join(sfVals));
//					}

					ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
					ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
					
					funcs.addAll(getFractiles(ucerf2OnFuncs, ucerf2Weights,
							"UCERF2 Time Dependent On Fault MPDs", region_fractiles));
					chars.addAll(getFractileChars(Color.GREEN, region_fractiles.length));
					
					funcs.addAll(getFractiles(ucerf2OffFuncs, ucerf2Weights,
							"UCERF2 Time Dependent Off Fault MPDs", region_fractiles));
					chars.addAll(getFractileChars(Color.MAGENTA, region_fractiles.length));
					
					// UCERF3 time DEPENDENT
					
					funcs.addAll(getFractiles(ucerf3OnFuncs, weights,
							"UCERF3 Time Dependent On Fault MPDs", region_fractiles));
					chars.addAll(getFractileChars(Color.ORANGE, region_fractiles.length));
					
					funcs.addAll(getFractiles(ucerf3OffFuncs, weights,
							"UCERF3 Time Dependent Off Fault MPDs", region_fractiles));
					chars.addAll(getFractileChars(Color.GRAY, region_fractiles.length));
					
					// UCERF2
					DiscretizedFunc u2IndepMean = getFractiles(ucerf2IndepFuncs, ucerf2IndepWeights,
							"UCERF2 Time Independent Total MPDs", region_fractiles).get(region_fractiles.length);
					DiscretizedFunc u2IndepMeanFreq = getFractiles(ucerf2IndepFreqFuncs, ucerf2IndepWeights,
							"UCERF2 Time Independent Total MFDs", region_fractiles).get(region_fractiles.length);
					u2IndepRegionMPDs.put(region.getName(), u2IndepMean);
					u2IndepRegionMFDs.put(region.getName(), u2IndepMeanFreq);
					funcs.add(u2IndepMean);
					PlotCurveCharacterstics u2IndepChar = getFractileChars(
							Color.DARK_GRAY, region_fractiles.length).get(region_fractiles.length);
					u2IndepChar.setLineWidth(2f);
					chars.add(u2IndepChar);

					List<DiscretizedFunc> u2DepFractiles = getFractiles(ucerf2Funcs, ucerf2Weights,
							"UCERF2 Time Dependent Total MPDs", region_fractiles);
					DiscretizedFunc u2DepFreq = getFractiles(ucerf2FreqFuncs, ucerf2Weights,
							"UCERF2 Time Dependent Total MFDs", region_fractiles).get(region_fractiles.length);
					u2DepRegionMPDs.put(region.getName(), u2DepFractiles.get(region_fractiles.length));
					u2DepRegionMFDs.put(region.getName(), u2DepFreq);
					funcs.addAll(u2DepFractiles);
					chars.addAll(getFractileChars(Color.RED, region_fractiles.length));
					
					// UCERF3 total time INDEPENDENT
					// only a mean line
					List<DiscretizedFunc> indepFractiles = getFractiles(ucerf3IndepFuncs, origWeights,
							"UCERF3 Time Independent Total MPDs", region_fractiles);
					DiscretizedFunc indepMean = indepFractiles.get(region_fractiles.length);
					indepRegionMPDs.put(region.getName(), indepFractiles);
					funcs.add(indepMean);
					PlotCurveCharacterstics indepChar = getFractileChars(
							Color.BLACK, region_fractiles.length).get(region_fractiles.length);
					indepChar.setLineWidth(2f);
					chars.add(indepChar);
					List<DiscretizedFunc> indepRateFractiles = getFractiles(ucerf3IndepRateFuncs, origWeights,
							"UCERF3 Time Independent Total MPDs", region_fractiles);
					DiscretizedFunc indepRateMean = indepRateFractiles.get(region_fractiles.length);
					indepRegionMFDs.put(region.getName(), indepRateFractiles);

//					SummedMagFreqDist meanU2Part = ERF_Calculator
//							.getParticipationMagFreqDistInRegion(erf, region, minX,
//									num, delta, true);
//					meanU2Part.setName("MFD for MeanUCERF2");
//					meanU2Part.setInfo(" ");
//					funcs.add(meanU2Part.getCumRateDistWithOffset());
//					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f,
//							Color.BLUE));

					List<DiscretizedFunc> depFractiles = getFractiles(ucerf3Funcs, weights,
							"UCERF3 Time Dependent Total MPDs", region_fractiles);
					DiscretizedFunc depMean = depFractiles.get(region_fractiles.length);
					depRegionMPDs.put(region.getName(), depFractiles);
					funcs.addAll(depFractiles);
					chars.addAll(getFractileChars(Color.BLUE, region_fractiles.length));
					List<DiscretizedFunc> depRateFractiles = getFractiles(ucerf3RateFuncs, weights,
							"UCERF3 Time Dependent Total MPDs", region_fractiles);
					depRegionMFDs.put(region.getName(), depRateFractiles);

					String title = region.getName();
					if (title == null || title.isEmpty())
						title = "Unnamed Region";

					String xAxisLabel = "Magnitude";
					String yAxisLabel = (int)duration+" Year Probability";

					PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel,
							yAxisLabel);
					durSpecs.add(spec);
				}
				
				// duration, timeDep, CSV
				regionProbCSVs.put(duration, true, getMFDCSV(depRegionMPDs, u2DepRegionMPDs,
						regionNames, duration, false));
				regionRateCSVs.put(duration, true, getMFDCSV(depRegionMFDs, u2DepRegionMFDs,
						regionNames, duration, true));
				regionProbCSVs.put(duration, false, getMFDCSV(indepRegionMPDs, u2IndepRegionMPDs,
						regionNames, duration, false));
				regionRateCSVs.put(duration, false, getMFDCSV(indepRegionMFDs, u2IndepRegionMFDs,
						regionNames, duration, true));
				
				List<PlotSpec> faultSpecList = Lists.newArrayList();
				faultSpecs.put(duration, faultSpecList);

				Map<String, List<DiscretizedFunc>> depFaultMPDs = Maps.newHashMap();
				Map<String, List<DiscretizedFunc>> indepFaultMPDs = Maps.newHashMap();
				Map<String, DiscretizedFunc> u2DepFaultMPDs = Maps.newHashMap();
				Map<String, DiscretizedFunc> u2IndepFaultMPDs = Maps.newHashMap();
				Map<String, List<DiscretizedFunc>> depFaultMFDs = Maps.newHashMap();
				Map<String, List<DiscretizedFunc>> indepFaultMFDs = Maps.newHashMap();
				Map<String, DiscretizedFunc> u2DepFaultMFDs = Maps.newHashMap();
				Map<String, DiscretizedFunc> u2IndepFaultMFDs = Maps.newHashMap();
				
				for (String faultName : mainFaultsSorted) {
					ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
					ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();

					XY_DataSetList depMPDs = new XY_DataSetList();
					XY_DataSetList indepMPDs = new XY_DataSetList();
					XY_DataSetList depMFDs = new XY_DataSetList();
					XY_DataSetList indepMFDs = new XY_DataSetList();
					
					for (MagDependentAperiodicityOptions cov : covs) {
						depMPDs.addAll(solMainFaultProbs.get(duration).get(cov).get(faultName));
						if (cov == null)
							indepMPDs.addAll(solMainFaultProbs.get(duration).get(cov).get(faultName));
						depMFDs.addAll(solMainFaultRates.get(duration).get(cov).get(faultName));
						if (cov == null)
							indepMFDs.addAll(solMainFaultRates.get(duration).get(cov).get(faultName));
					}
					
					if ((float)duration == 30f) {
						// we can do a UCERF2 comparison
						DiscretizedFunc[] u2DepMPDs = u2DepFaultVals.get(faultName);
						DiscretizedFunc[] u2IndepMPDs = u2IndepFaultVals.get(faultName);
						// organized as [min max mean]
						if (u2DepMPDs != null) {
							// mean indep
							funcs.add(u2IndepMPDs[2]);
							u2IndepFaultMPDs.put(faultName, u2IndepMPDs[2]);
							u2IndepFaultMFDs.put(faultName, probsToRates(u2IndepMPDs[2], duration));
							chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.DARK_GRAY));
							// min
							funcs.add(u2DepMPDs[0]);
							chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
							// max
							funcs.add(u2DepMPDs[1]);
							chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
							// mean
							funcs.add(u2DepMPDs[2]);
							u2DepFaultMPDs.put(faultName, u2DepMPDs[2]);
							u2DepFaultMFDs.put(faultName, probsToRates(u2DepMPDs[2], duration));
							chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.RED));
						}
					}
					
					// indep on bottom, only mean
					List<DiscretizedFunc> indepFractiles = getFractiles(indepMPDs, origWeights, faultName+" Time Independent", region_fractiles);
					DiscretizedFunc indepMean = indepFractiles.get(region_fractiles.length);
					indepFaultMPDs.put(faultName, indepFractiles);
					indepFaultMFDs.put(faultName, getFractiles(indepMFDs, origWeights,
							faultName+" Time Independent", region_fractiles));
					funcs.add(indepMean);
					PlotCurveCharacterstics indepChar = getFractileChars(
							Color.BLACK, region_fractiles.length).get(region_fractiles.length);
					indepChar.setLineWidth(2f);
					chars.add(indepChar);
					
					// dep on top
					List<DiscretizedFunc> depFractiles = getFractiles(depMPDs, weights, faultName+" Time Dependent", region_fractiles);
					DiscretizedFunc depMean = depFractiles.get(region_fractiles.length);
					depFaultMPDs.put(faultName, depFractiles);
					depFaultMFDs.put(faultName, getFractiles(depMFDs, weights,
							faultName+" Time Dependent", region_fractiles));
					funcs.addAll(depFractiles);
					chars.addAll(getFractileChars(Color.BLUE, region_fractiles.length));

					String title = faultName;

					String xAxisLabel = "Magnitude";
					String yAxisLabel = (int)duration+" Year Probability";

					PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel,
							yAxisLabel);
					faultSpecList.add(spec);
				}
				
				// duration, timeDep, CSV
				faultProbCSVs.put(duration, true, getMFDCSV(depFaultMPDs, u2DepFaultMPDs,
						mainFaultsSorted, duration, false));
				faultRateCSVs.put(duration, true, getMFDCSV(depFaultMFDs, u2DepFaultMFDs,
						mainFaultsSorted, duration, true));
				faultProbCSVs.put(duration, false, getMFDCSV(indepFaultMPDs, u2IndepFaultMPDs,
						mainFaultsSorted, duration, false));
				faultRateCSVs.put(duration, false, getMFDCSV(indepFaultMFDs, u2IndepFaultMFDs,
						mainFaultsSorted, duration, true));
				
				// sub sect probs
				for (FaultModels fm : fmWeights.get(duration).keySet()) {
					// handle weights
					List<Double> origWeightsForFM = fmWeights.get(duration).get(fm);
					// this adds in each individual COV weight including poisson
					List<Double> weightsForFM = Lists.newArrayList();
					for (MagDependentAperiodicityOptions cov : covs) {
						double covWeight = FaultSystemSolutionERF.getWeightForCOV(cov);
						for (double weight : origWeightsForFM)
							weightsForFM.add(covWeight*weight);
					}
					
					CSVFile<String> meanCSV = new CSVFile<String>(true);
					List<CSVFile<String>> fractileCSVs = Lists.newArrayList();
					for (int i=0; i<sub_sect_fractiles.length; i++)
						fractileCSVs.add(new CSVFile<String>(true));
					CSVFile<String> minCSV = new CSVFile<String>(true);
					CSVFile<String> maxCSV = new CSVFile<String>(true);
					CSVFile<String> poissonCSV = new CSVFile<String>(true);
					List<String> header = Lists.newArrayList();
					header.add("Sub Section Name");
					
					Map<MagDependentAperiodicityOptions, Map<FaultModels, XY_DataSetList[]>> ssProbsMaps =
							solSubSectProbs.get(duration);
					
					XY_DataSetList[] allCOVProbs = null; // this includes poisson branches
					XY_DataSetList[] poissonProbs = null;
					
					for (MagDependentAperiodicityOptions cov : covs) {
						XY_DataSetList[] ssProbs = ssProbsMaps.get(cov).get(fm);
						if (allCOVProbs == null) {
							allCOVProbs = new XY_DataSetList[ssProbs.length];
							for (int s=0; s<ssProbs.length; s++) {
								allCOVProbs[s] = new XY_DataSetList();
								allCOVProbs[s].setName(ssProbs[s].getName());
							}
							// now fill in X values
							for (Point2D pt : ssProbs[0].get(0))
								header.add((float)pt.getX()+"");
						}
						if (cov == null)
							poissonProbs = ssProbs;
						for (int s=0; s<ssProbs.length; s++)
							allCOVProbs[s].addAll(ssProbs[s]);
					}
					Preconditions.checkNotNull(allCOVProbs);
					Preconditions.checkNotNull(poissonProbs);
					
					meanCSV.addLine(header);
					for (CSVFile<String> fractileCSV : fractileCSVs)
						fractileCSV.addLine(header);
					minCSV.addLine(header);
					maxCSV.addLine(header);
					poissonCSV.addLine(header);
					
					for (int s=0; s< allCOVProbs.length; s++) {
						XY_DataSetList allList = allCOVProbs[s];
						XY_DataSetList poissonList = poissonProbs[s];
						String name = poissonList.getName();
						
						List<DiscretizedFunc> allFractiles =
								getFractiles(allList, weightsForFM, "", sub_sect_fractiles);
						List<DiscretizedFunc> poissonFractiles =
								getFractiles(poissonList, origWeightsForFM, "", new double[0]);

						int counter = 0;
						for (int i=0; i<sub_sect_fractiles.length; i++)
							fractileCSVs.get(i).addLine(getMFDRow(allFractiles.get(counter++), name));
						meanCSV.addLine(getMFDRow(allFractiles.get(counter++), name));
						minCSV.addLine(getMFDRow(allFractiles.get(counter++), name));
						maxCSV.addLine(getMFDRow(allFractiles.get(counter++), name));
						poissonCSV.addLine(getMFDRow(poissonFractiles.get(0), name));
					}
					
					List<CSVFile<String>> csvs = Lists.newArrayList();
					csvs.add(meanCSV);
					csvs.add(minCSV);
					csvs.add(maxCSV);
					csvs.add(poissonCSV);
					
					subSectsCSVs.put(duration, fm, csvs);
					
					subSectsPercentileCSVs.put(duration, fm, fractileCSVs);
				}
				
				// parent sect probs
				for (FaultModels fm : fmWeights.get(duration).keySet()) {
					// handle weights
					List<Double> origWeightsForFM = fmWeights.get(duration).get(fm);
					// this adds in each individual COV weight including poisson
					List<Double> weightsForFM = Lists.newArrayList();
					for (MagDependentAperiodicityOptions cov : covs) {
						double covWeight = FaultSystemSolutionERF.getWeightForCOV(cov);
						for (double weight : origWeightsForFM)
							weightsForFM.add(covWeight*weight);
					}
					
					CSVFile<String> meanCSV = new CSVFile<String>(true);
					CSVFile<String> minCSV = new CSVFile<String>(true);
					CSVFile<String> maxCSV = new CSVFile<String>(true);
					CSVFile<String> poissonCSV = new CSVFile<String>(true);
					List<String> header = Lists.newArrayList();
					header.add("Parent Section Name");
					
					Map<MagDependentAperiodicityOptions, Map<FaultModels, Map<Integer, XY_DataSetList>>>
							parentProbsMaps = solParentSectProbs.get(duration);
					
					Map<Integer, XY_DataSetList> allCOVProbs = null; // this includes poisson branches
					Map<Integer, XY_DataSetList> poissonProbs = null;
					
					for (MagDependentAperiodicityOptions cov : covs) {
						Map<Integer, XY_DataSetList> parentProbs = parentProbsMaps.get(cov).get(fm);
						if (allCOVProbs == null) {
							allCOVProbs = new HashMap<Integer, XY_DataSetList>();
							for (int parentID : parentProbs.keySet()) {
								XY_DataSetList parentList = new XY_DataSetList();
								parentList.setName(parentProbs.get(parentID).getName());
								allCOVProbs.put(parentID, parentList);
							}
							// now fill in X values
							for (Point2D pt : parentProbs.values().iterator().next().get(0))
								header.add((float)pt.getX()+"");
						}
						if (cov == null)
							poissonProbs = parentProbs;
						for (int parentID : parentProbs.keySet())
							allCOVProbs.get(parentID).addAll(parentProbs.get(parentID));
					}
					Preconditions.checkNotNull(allCOVProbs);
					Preconditions.checkNotNull(poissonProbs);
					
					meanCSV.addLine(header);
					minCSV.addLine(header);
					maxCSV.addLine(header);
					poissonCSV.addLine(header);
					
					Map<String, Integer> parentNamesIDMap = Maps.newHashMap();
					Map<Integer, String> parentIDsNamesMap = parentSectNamesMap.get(fm);
					for (Integer parentID : parentIDsNamesMap.keySet())
						parentNamesIDMap.put(parentIDsNamesMap.get(parentID), parentID);
					List<String> parentNamesSorted = Lists.newArrayList(parentNamesIDMap.keySet());
					Collections.sort(parentNamesSorted);
					
					for (String parentName : parentNamesSorted) {
						Integer parentID = parentNamesIDMap.get(parentName);
						
						XY_DataSetList allList = allCOVProbs.get(parentID);
						XY_DataSetList poissonList = poissonProbs.get(parentID);
						
						List<DiscretizedFunc> allFractiles = getFractiles(allList, weightsForFM, "", new double[0]);
						List<DiscretizedFunc> poissonFractiles = getFractiles(poissonList, origWeightsForFM, "", new double[0]);
						
						meanCSV.addLine(getMFDRow(allFractiles.get(0), parentName));
						minCSV.addLine(getMFDRow(allFractiles.get(1), parentName));
						maxCSV.addLine(getMFDRow(allFractiles.get(2), parentName));
						poissonCSV.addLine(getMFDRow(poissonFractiles.get(0), parentName));
					}
					
					List<CSVFile<String>> csvs = Lists.newArrayList();
					csvs.add(meanCSV);
					csvs.add(minCSV);
					csvs.add(maxCSV);
					csvs.add(poissonCSV);
					
					parentSectsCSVs.put(duration, fm, csvs);
				}
			}
			
//			sfDebugCSV.sort(1, 1, new Comparator<String>() {
//
//				@Override
//				public int compare(String o1, String o2) {
//					return o1.compareTo(o2);
//				}
//				
//			});
		}
		
		private static List<String> getMFDRow(DiscretizedFunc func, String name) {
			List<String> line = Lists.newArrayList();
			line.add(name);
			for (Point2D pt : func)
				line.add(pt.getY()+"");
			return line;
		}
		
		private static DiscretizedFunc probsToRates(DiscretizedFunc probs, double duration) {
			ArbitrarilyDiscretizedFunc rates = new ArbitrarilyDiscretizedFunc();
			rates.setName(probs.getName());
			
			for (Point2D pt : probs)
				rates.set(pt.getX(), -Math.log(1 - pt.getY()));
			
			return rates;
		}
		
		private CSVFile<String> getMFDCSV(Map<String, List<DiscretizedFunc>> mpds,
				Map<String, DiscretizedFunc> u2MPDs, List<String> names,
				double duration, boolean isRate) {
			CSVFile<String> csv = new CSVFile<String>(true);
			
			List<String> header = Lists.newArrayList("Magnitude");
			for (String fault : names)
				header.add(fault+" Mean");
			for (String fault : names)
				header.add(fault+" Min");
			for (String fault : names)
				header.add(fault+" Max");
			if (!u2MPDs.isEmpty())
				for (String fault : names)
					header.add(fault+" U2");
			
			// just used for x vals
			DiscretizedFunc xVals = mpds.values().iterator().next().get(0);
			
			csv.addLine(header);
			for (int i=0; i<xVals.size(); i++) {
				List<String> line = Lists.newArrayList();
				line.add(xVals.getX(i)+"");
				for (int j=0; j<3; j++)
					// loop over mean, min, max
					for (String fault : names)
						line.add(mpds.get(fault).get(j+region_fractiles.length).getY(i)+"");
				// now U2
				if (!u2MPDs.isEmpty()) {
					for (String fault : names) {
						double x = xVals.getX(i);
						DiscretizedFunc u2MPD = u2MPDs.get(fault);
						int ind = u2MPD.getXIndex(x);
						if (ind >= 0)
							line.add(u2MPD.getY(ind)+"");
						else if (x > u2MPD.getMinX() && x < u2MPD.getMaxX())
							line.add(u2MPD.getInterpolatedY(x)+"");
						else
							line.add("");
					}
				}
				csv.addLine(line);
			}
			
			return csv;
		}

		@Override
		protected boolean usesERFs() {
			return true;
		}

		@Override
		protected boolean isApplyAftershockFilter() {
			return !INCLUDE_AFTERSHOCKS;
		}

		@Override
		protected boolean isTimeDependent() {
			return true;
		}

		protected Map<Double, List<PlotSpec>> getSpecs() {
			return specs;
		}

	}
	
	public static void writeERFBasedSiteHazardHists(ERFBasedSiteHazardHistPlot plot, File dir) throws IOException {
		writeERFBasedSiteHazardHists(plot.plots, dir);
	}
	
	public static void writeERFBasedSiteHazardHists(
			SiteHazardResults results, File dir) throws IOException {
		File subDir = new File(dir, "site_hazard_hists");
		if (!subDir.exists())
			subDir.mkdir();
		
		Map<Site, Table<Period, Double, Map<String, PlotSpec>>> plotsMap = results.plotsMap;
		Map<Site, Table<Period, Double, CSVFile<String>>> csvsMap = results.csvsMap;
		
		File tempDir = Files.createTempDir();
		
		for (Site site : plotsMap.keySet()) {
			Table<Period, Double, Map<String, PlotSpec>> sitePlots = plotsMap.get(site);
			Table<Period, Double, CSVFile<String>> siteCSVs = csvsMap.get(site);
			File siteDir = new File(subDir, site.getName());
			if (!siteDir.exists())
				siteDir.mkdir();
			for (Period period : sitePlots.rowKeySet()) {
				for (double prob : sitePlots.columnKeySet()) {
					String prefix = (int)(prob*100d)+"in"+(int)ERFBasedSiteHazardHistPlot.duration+"_"+period.getLabel();
					Map<String, PlotSpec> specs = sitePlots.get(period, prob);
					
					List<File> histPDFs = Lists.newArrayList();
					List<String> names = Lists.newArrayList();
					for (Class<? extends LogicTreeBranchNode<?>> clazz : LogicTreeBranch.getLogicTreeNodeClasses()) {
						if (clazz.equals(InversionModels.class) || clazz.equals(MomentRateFixes.class))
							continue;
						names.add(ClassUtils.getClassNameWithoutPackage(LogicTreeBranch.getEnumEnclosingClass(clazz)));
					}
					names.add("MagDepAperiodicity");
					names.add("GMPE");
					for (String name : names) {
						PlotSpec histSpec = specs.get(name);
						if (histSpec == null) {
							System.out.println("WARNING: no spec found for "+name);
							continue;
						}
						Preconditions.checkNotNull(histSpec, "No plot found for: "+name);
						
						List<? extends PlotElement> elems = histSpec.getPlotElems();
						
						HeadlessGraphPanel gp = new HeadlessGraphPanel();
						CommandLineInversionRunner.setFontSizes(gp);
						EvenlyDiscretizedFunc f1 = (EvenlyDiscretizedFunc) elems.get(0);
						double min = f1.getMinX();
						double delta = f1.getDelta();
						int num = f1.size();
						double plotMaxY = 0d;
						for (int i=0; i<elems.size(); i++) {
							if (elems.get(i) instanceof DiscretizedFunc) {
								double max = ((XY_DataSet)elems.get(i)).getMaxY();
								if (max > plotMaxY)
									plotMaxY = max;
							}
						}
						plotMaxY *= 1.3;
						if (plotMaxY > 1d)
							plotMaxY = 1d;
						// pad by a delta
						double plotMinX = min-0.5*delta - 0.5*delta;
						double plotMaxX = min+(num-0.5)*delta + 0.5*delta;
						gp.setUserBounds(plotMinX, plotMaxX, 0, plotMaxY);
						if (plotMinX >= plotMaxX) {
							System.out.println("Data bounds: "+f1.getMinX()+" "+f1.getMaxX()
									+" "+f1.getMinY()+" "+f1.getMaxY());
							System.out.println("Plot bounds: "+plotMinX+" "+plotMaxX+" 0 "+plotMaxY);
							System.out.println("Delta="+delta);
							System.out.println(f1);
						}
						
						gp.drawGraphPanel(histSpec);
						File file = new File(tempDir, name);
						gp.getChartPanel().setSize(500, 400);
						gp.saveAsPDF(file.getAbsolutePath()+".pdf");
						gp.saveAsPNG(file.getAbsolutePath()+".png");
						histPDFs.add(new File(file.getAbsolutePath()+".pdf"));
					}
					
					try {
						FaultSysSolutionERF_Calc.combineBranchSensHists(histPDFs, new File(siteDir, prefix+".pdf"));
					} catch (com.lowagie.text.DocumentException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					
					CSVFile<String> csv = siteCSVs.get(period, prob);
					csv.writeToFile(new File(siteDir, prefix+".csv"));
				}
			}
		}
		FileUtils.deleteRecursive(tempDir);
	}
	
	private static class SiteHazardCalcJob implements Task {
		
		private ERFBasedSiteHazardHistPlot plot;
		private BinaryCurveArchiver archiver;
		private ERF erf;
		private AttenRelRef ref;
		private Site site;
		private Period period;
		private String prefix;
		private DiscretizedFunc func;
		private int solIndex;
		
		public SiteHazardCalcJob(ERFBasedSiteHazardHistPlot plot, BinaryCurveArchiver archiver,
				ERF erf, AttenRelRef ref, Site site, Period period, String prefix, DiscretizedFunc xVals, int solIndex) {
			this.plot = plot;
			this.archiver = archiver;
			this.erf = erf;
			this.ref = ref;
			this.site = site;
			this.period = period;
			this.prefix = prefix;
			this.func = xVals.deepClone();
			this.solIndex = solIndex;
		}

		@Override
		public void compute() {
			HazardCurveCalculator calc = new HazardCurveCalculator(); // init calculator
			
			ScalarIMR imr = plot.getIMRInstance(ref);
			if (period == Period.GM0P00) {
				imr.setIntensityMeasure("PGA");
			} else {
				imr.setIntensityMeasure("SA");
				imr.getParameter(PeriodParam.NAME).setValue(period.getValue());
			}
			
			Stopwatch watch = Stopwatch.createStarted();
			plot.debug(solIndex, "calculating curve: "+site.getName()+". "+prefix);
			calc.getHazardCurve(func, site, imr, erf);
			watch.stop();
			plot.debug(solIndex, "archiving curve: "+site.getName()+". "+prefix+" ("+watch.elapsed(TimeUnit.SECONDS)+" s)");
			
			// un-log it
			ArbitrarilyDiscretizedFunc unLogged = new ArbitrarilyDiscretizedFunc();
			for (int j=0; j<func.size(); j++)
				unLogged.set(Math.exp(func.getX(j)), func.getY(j));
			
			CurveMetadata meta = new CurveMetadata(site, solIndex, null	, prefix);
			try {
				archiver.archiveCurve(unLogged, meta);
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
			plot.debug(solIndex, "done archiving curve: "+site.getName()+". "+prefix);
			plot.returnIMRInstance(ref, imr);
		}
		
	}
	
	private static class SiteHazardResults {
		private Map<Site, Table<Period, Double, Map<String, PlotSpec>>> plotsMap;
		private Map<Site, Table<Period, Double, CSVFile<String>>> csvsMap;
	}
	
	public static class ERFBasedSiteHazardHistPlot extends CompoundFSSPlots {
		
		public static List<Site> getSites() {
			List<Site> sites = Lists.newArrayList();
			
//			sites.add(NEHRP_TestCity.LOS_ANGELES.getSite()); // will add params
//			sites.add(NEHRP_TestCity.SAN_FRANCISCO.getSite()); // will add params
			
//			for (NEHRP_TestCity nCity : NEHRP_TestCity.getCA())
//				sites.add(nCity.getSite()); // will add params
			
			try {
				Map<String, Location> locs = UC3_CalcUtils.readSiteFile(
						UCERF3_DataUtils.locateResource("misc", "srp_sites_no_pbr.txt"));
//				EnumSet<NEHRP_TestCity> nehrpCA = NEHRP_TestCity.getCA();
//				siteLoop:
				for (String name : locs.keySet()) {
					Location loc = locs.get(name);
					
					// only additional ones now
//					for (NEHRP_TestCity n : nehrpCA) {
//						if (loc.equals(n.location()))
//							continue siteLoop;
//					}
					
					Site s = new Site(loc, name);
					
					// CY AS
					DepthTo1pt0kmPerSecParam d10p = new DepthTo1pt0kmPerSecParam(null,
						0, 1000, true);
					d10p.setValueAsDefault();
					s.addParameter(d10p);
					// CB
					DepthTo2pt5kmPerSecParam d25p = new DepthTo2pt5kmPerSecParam(null,
						0, 1000, true);
					d25p.setValueAsDefault();
					s.addParameter(d25p);
					// all
					Vs30_Param vs30p = new Vs30_Param(760);
					vs30p.setValueAsDefault();
					s.addParameter(vs30p);
					// AS CY
					Vs30_TypeParam vs30tp = new Vs30_TypeParam();
					s.addParameter(vs30tp);
					
					sites.add(s);
				}
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
			
			return sites;
		}
		
		private static Map<AttenRelRef, Double> buildIMRMap() {
			AttenRelRef[] imrs = {AttenRelRef.CB_2008, AttenRelRef.BA_2008, AttenRelRef.CY_2008, AttenRelRef.AS_2008};
//			AttenRelRef[] imrs = {AttenRelRef.CB_2008};
			
			Map<AttenRelRef, Double> map = Maps.newHashMap();
			
			for (AttenRelRef ref : imrs) {
				map.put(ref, 1d/(double)imrs.length); // equal weight for now
			}
			
			return map;
		}
		
		public static List<Period> getPeriods() {
			List<Period> periods = Lists.newArrayList();
			
			periods.add(Period.GM0P00); // PGA
			periods.add(Period.GM4P00); // 4 sec SA
			periods.add(Period.GM1P00); // 1 sec SA
			periods.add(Period.GM0P20); // 0.2 sec SA
			
			return periods;
		}

		private static MagDependentAperiodicityOptions[] covs = {MagDependentAperiodicityOptions.LOW_VALUES,
			MagDependentAperiodicityOptions.MID_VALUES, MagDependentAperiodicityOptions.HIGH_VALUES, null};
//		private static MagDependentAperiodicityOptions[] covs = {MagDependentAperiodicityOptions.MID_VALUES, null};
		
		private BranchWeightProvider weightProv;
		
		private List<Site> sites;
		private Map<AttenRelRef, Double> imrs;
		private List<Period> periods;
		private List<DiscretizedFunc> xValsList;
		
//		private Map<Site, Table<AttenRelRef, Period,
//			Map<MagDependentAperiodicityOptions, List<DiscretizedFunc>>>> resultsTables;
		private File curveDir;
		private transient Map<Site, BinaryCurveArchiver> archivers;
		private LogicTreeBranch[] branches;
		private double[] branchWeights;
		
		protected static final String DEFAULT_CACHE_DIR_NAME = "site_hazard_curve_cache";
		
		private static final double duration = 50d;
		private static final double[] probLevels = { 0.02, 0.1 };
		
		public ERFBasedSiteHazardHistPlot(BranchWeightProvider weightProv, File curveDir, int numBranches) {
			debug(-1, "ERFBasedSiteHazardHistPlot START constructor");
			this.weightProv = weightProv;
			this.curveDir = curveDir;
			if (MPI.COMM_WORLD == null || MPI.COMM_WORLD.Rank() == 0) {
				if (!curveDir.exists())
					curveDir.mkdir();
				File metadataFile = new File(curveDir, "metadata.xml");
				if (metadataFile.exists())
					metadataFile.delete();
			}
			
			this.sites = getSites();
			this.imrs = buildIMRMap();
			this.periods = getPeriods();
			this.xValsList = Lists.newArrayList();
			for (Period period : periods)
				xValsList.add(period.getLogFunction());
			
//			resultsTables = Maps.newHashMap();
			archivers = Maps.newHashMap();
			debug(-1, "ERFBasedSiteHazardHistPlot Building Archivers");
			for (Site site : sites) {
//				HashBasedTable<AttenRelRef, Period,Map<MagDependentAperiodicityOptions, List<DiscretizedFunc>>>
//					resultsTable = HashBasedTable.create();
//				resultsTables.put(site, resultsTable);
				Map<String, DiscretizedFunc> xValsMap = Maps.newHashMap();
				for (Period period : periods) {
					for (AttenRelRef imr : imrs.keySet()) {
//						Map<MagDependentAperiodicityOptions, List<DiscretizedFunc>> covMap = Maps.newHashMap();
						for (MagDependentAperiodicityOptions cov : covs) {
//							covMap.put(cov, new ArrayList<DiscretizedFunc>());
							String prefix = buildBinFilePrefix(imr, period, cov);
							xValsMap.put(prefix, period.getFunction());
//							archivers.put(site, prefix, new BinaryCurveArchiver(outputDir, numBranches, xValsMap))
						}
//						resultsTable.put(imr, period, covMap);
					}
				}
				File siteDir = new File(curveDir, site.getName());
				
				if (MPI.COMM_WORLD == null || MPI.COMM_WORLD.Rank() == 0) {
					if (!siteDir.exists()) {
						siteDir.mkdir();
					}
				} else {
					// this is MPI and we're not rank zero
					// wait until it has been created
					while (!siteDir.exists()) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							ExceptionUtils.throwAsRuntimeException(e);
						}
					}
				}
				BinaryCurveArchiver archiver = new BinaryCurveArchiver(siteDir, numBranches, xValsMap);
				archivers.put(site, archiver);
				// initialize
				if (MPI.COMM_WORLD == null || MPI.COMM_WORLD.Rank() == 0) {
					// if this is an MPJ job, only initialize if rank 0
					
					// first delete any preexisting curve files
					debug(-1, "ERFBasedSiteHazardHistPlot Initializing an archiver for: "+site.getName());
					for (String prefix : xValsMap.keySet()) {
						File file = new File(siteDir, prefix+".bin");
						if (file.exists())
							file.delete();
					}
					archiver.initialize();
				}
			}
			branches = new LogicTreeBranch[numBranches];
			branchWeights = new double[numBranches];
			
			debug(-1, "ERFBasedSiteHazardHistPlot END constructor");
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			throw new IllegalStateException("Should never be called, ERF plot");
		}
		
		private synchronized ScalarIMR getIMRInstance(AttenRelRef ref) {
			Deque<ScalarIMR> list = imrsCache.get(ref);
			if (list == null) {
				list = new ArrayDeque<ScalarIMR>();
				imrsCache.put(ref, list);
			}
			ScalarIMR imr;
			if (list.isEmpty()) {
				imr = ref.instance(null);
				imr.setParamDefaults();
			} else {
				imr = list.pop();
			}
			return imr;
		}
		
		private synchronized void returnIMRInstance(AttenRelRef ref, ScalarIMR imr) {
			imrsCache.get(ref).push(imr);
		}
		
		private transient Map<AttenRelRef, Deque<ScalarIMR>> imrsCache = Maps.newHashMap();
		
		private static String buildBinFilePrefix(AttenRelRef imr, Period period, MagDependentAperiodicityOptions cov) {
			String str = imr.name()+"_"+period.getLabel()+"_";
			if (cov == null)
				str += "POISSON";
			else
				str += cov.name();
			return str;
		}

		@Override
		protected void processERF(LogicTreeBranch branch,
				FaultSystemSolutionERF erf, int solIndex) {
			erf.getTimeSpan().setDuration(duration);
			erf.updateForecast();
			
//			Map<MagDependentAperiodicityOptions, Map<Site, Table<AttenRelRef, Period, DiscretizedFunc>>>
//					myResultsTables = Maps.newHashMap();
			
			for (MagDependentAperiodicityOptions cov : covs) {
				if (cov == null) {
					erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
				} else {
					erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
					erf.setParameter(MagDependentAperiodicityParam.NAME, cov);
				}
				double origDuration = erf.getTimeSpan().getDuration();
				erf.getTimeSpan().setDuration(duration);
				debug(solIndex, "updating forecast for cov="+cov);
				erf.updateForecast();
				debug(solIndex, "done updating forecast for cov="+cov);
				
//				Map<Site, Table<AttenRelRef, Period, DiscretizedFunc>> covTable = Maps.newHashMap();
//				myResultsTables.put(cov, covTable);
				for (Site site : sites) {
					List<Task> tasks = Lists.newArrayList();
//					Table<AttenRelRef, Period, DiscretizedFunc> myResultsTable = HashBasedTable.create();
//					covTable.put(site, myResultsTable);
					BinaryCurveArchiver archiver = archivers.get(site);
					for (AttenRelRef ref : imrs.keySet()) {
						for (int i=0; i<periods.size(); i++) {
							Period period = periods.get(i);
							String prefix = buildBinFilePrefix(ref, period, cov);
							DiscretizedFunc xVals = xValsList.get(i);
							tasks.add(new SiteHazardCalcJob(this, archiver, erf, ref, site, period, prefix, xVals, solIndex));
						}
					}
					
					try {
						new ThreadedTaskComputer(tasks).computeThreaded();
					} catch (InterruptedException e) {
						ExceptionUtils.throwAsRuntimeException(e);
					}
				}
			}
			
			branches[solIndex] = branch;
			branchWeights[solIndex] = weightProv.getWeight(branch);
			
//			synchronized(this) {
//				double weight = weightProv.getWeight(branch);
//				for (Site site : sites) {
//					for (MagDependentAperiodicityOptions cov : covs) {
//						Table<AttenRelRef, Period, Map<MagDependentAperiodicityOptions, List<DiscretizedFunc>>>
//						resultsTable = resultsTables.get(site);
//						Table<AttenRelRef, Period, DiscretizedFunc> myResultsTable = myResultsTables.get(cov).get(site);
//						for (AttenRelRef imr : imrs.keySet())
//							for (Period period : periods)
//								resultsTable.get(imr, period).get(cov).add(myResultsTable.get(imr, period));
//					}
//				}
//				branches.add(branch);
//				branchWeights.add(weight);
//			}
		}
		
		@Override
		protected void flushResults() {
			for (BinaryCurveArchiver archiver : archivers.values())
				archiver.close();
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots other : otherCalcs) {
				ERFBasedSiteHazardHistPlot o = (ERFBasedSiteHazardHistPlot)other;
				
				Preconditions.checkState(branches.length == o.branches.length);
				
				for (int i=0; i<o.branches.length; i++) {
					if (o.branches[i] != null) {
						Preconditions.checkState(branches[i] == null);
						branches[i] = o.branches[i];
						branchWeights[i] = o.branchWeights[i];
					}
				}
				
//				for (Site site : sites) {
//					Table<AttenRelRef, Period, Map<MagDependentAperiodicityOptions, List<DiscretizedFunc>>>
//						resultsTable = resultsTables.get(site);
//					Table<AttenRelRef, Period, Map<MagDependentAperiodicityOptions, List<DiscretizedFunc>>>
//						oResultsTable = null;
//					for (Site oSite : o.resultsTables.keySet()) {
//						if (oSite.getName().equals(site.getName())) {
//							oResultsTable = o.resultsTables.get(oSite);
//							break;
//						}
//					}
//					if (oResultsTable == null) {
//						System.out.println("Didn't find a match for: "+site);
//						System.out.println("Candidates:");
//						for (Site oSite : o.resultsTables.keySet())
//							System.out.println("\t"+oSite);
//					}
//					Preconditions.checkNotNull(oResultsTable, "No match for site: "+site);
//					for (AttenRelRef imr : imrs.keySet()) {
//						for (Period period : periods) {
//							Preconditions.checkNotNull(resultsTable);
//							Preconditions.checkNotNull(resultsTable.get(imr, period));
//							Preconditions.checkNotNull(oResultsTable.get(imr, period));
//							for (MagDependentAperiodicityOptions cov : covs)
//								resultsTable.get(imr, period).get(cov).addAll(oResultsTable.get(imr, period).get(cov));
//						}
//					}
//				}
//				
//				branches.addAll(o.branches);
//				branchWeights.addAll(o.branchWeights);
			}
		}
		
		// site : <period, probLevel, branchLevel:plot>
//		private Map<Site, Table<Period, Double, Map<String, PlotSpec>>> plotsMap;
		private transient SiteHazardResults plots;

		@Override
		protected void doFinalizePlot() {
			writeMetadataFile(curveDir, sites, periods, imrs, branches, branchWeights);
			
			plots = doFinalizePlot(curveDir, sites, periods, imrs, branches, branchWeights);
		}
		
		private static void writeMetadataFileForAllBranches(
				List<LogicTreeBranch> branches, BranchWeightProvider prov, File curveDir) {
			BranchWeightProvider weightProv = new APrioriBranchWeightProvider();
			
			LogicTreeBranch[] branchArray = new LogicTreeBranch[branches.size()];
			double[] branchWeights = new double[branches.size()];
			for (int i=0; i<branches.size(); i++) {
				LogicTreeBranch branch = branches.get(i);
				branchArray[i] = branch;
				branchWeights[i] = weightProv.getWeight(branch);
			}
			
			writeMetadataFile(curveDir, getSites(), getPeriods(), buildIMRMap(), branchArray, branchWeights);
		}
		
		private static void writeMetadataFile(File curveDir, List<Site> sites, List<Period> periods, Map<AttenRelRef, Double> imrs,
				LogicTreeBranch[] branches, double[] branchWeights) {
			Document doc = XMLUtils.createDocumentWithRoot();
			Element root = doc.getRootElement();
			
			// sites
			Site.writeSitesToXML(sites, root);
			
			// periods
			Element periodsEl = root.addElement("Periods");
			for (Period period : periods) {
				Element subEl = periodsEl.addElement("Period");
				subEl.addAttribute("name", period.name());
			}
			
			// imrs
			Element imrsEl = root.addElement("IMRs");
			for (AttenRelRef imr : imrs.keySet()) {
				Element subEl = imrsEl.addElement("IMR");
				subEl.addAttribute("name", imr.name());
				subEl.addAttribute("weight", imrs.get(imr).toString());
			}
			
			// imrs
			Element branchesEl = root.addElement("Branches");
			branchesEl.addAttribute("num", branches.length+"");
			for (int i=0; i<branches.length; i++) {
				Element subEl = branchesEl.addElement("Branch");
				subEl.addAttribute("index", i+"");
				subEl.addAttribute("name", branches[i].buildFileName());
				subEl.addAttribute("weight", branchWeights[i]+"");
			}
			
			File xmlFile = new File(curveDir, "metadata.xml");
			try {
				XMLUtils.writeDocumentToFile(xmlFile, doc);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		protected static  SiteHazardResults doFinalizePlot(
				File curveDir) {
			File xmlFile = new File(curveDir, "metadata.xml");
			Document doc;
			try {
				doc = XMLUtils.loadDocument(xmlFile);
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			Element root = doc.getRootElement();
			
			List<Site> sites = Site.loadSitesFromXML(root.element(Site.XML_METADATA_LIST_NAME), new ArrayList<Parameter<?>>());
			
			List<Period> periods = Lists.newArrayList();
			for (Element periodEl : (List<Element>)root.element("Periods").elements())
				periods.add(Period.valueOf(periodEl.attributeValue("name")));
			
			Map<AttenRelRef, Double> imrs = Maps.newHashMap();
			for (Element imrEl : (List<Element>)root.element("IMRs").elements()) {
				AttenRelRef imr = AttenRelRef.valueOf(imrEl.attributeValue("name"));
				double weight = Double.parseDouble(imrEl.attributeValue("weight"));
				imrs.put(imr, weight);
			}
			
			Element branchesEl = root.element("Branches");
			int numBranches = Integer.parseInt(branchesEl.attributeValue("num"));
			LogicTreeBranch[] branches = new LogicTreeBranch[numBranches];
			double[] branchWeights = new double[numBranches];
			for (Element branchEl : (List<Element>)branchesEl.elements()) {
				LogicTreeBranch branch = LogicTreeBranch.fromFileName(branchEl.attributeValue("name"));
				double weight = Double.parseDouble(branchEl.attributeValue("weight"));
				int index = Integer.parseInt(branchEl.attributeValue("index"));
				branches[index] = branch;
				branchWeights[index] = weight;
			}
			return doFinalizePlot(curveDir, sites, periods, imrs, branches, branchWeights);
		}
		
		protected static SiteHazardResults doFinalizePlot(
				File curveDir, List<Site> sites, List<Period> periods, Map<AttenRelRef, Double> imrs,
				LogicTreeBranch[] branches, double[] branchWeights) {
			SiteHazardResults results = new SiteHazardResults();
			results.plotsMap = Maps.newHashMap();
			results.csvsMap = Maps.newHashMap();
			for (Site site : sites) {
				Table<Period, Double, Map<String, PlotSpec>> sitePlots = HashBasedTable.create();
				Table<Period, Double, CSVFile<String>> siteCSVs = HashBasedTable.create();
				results.plotsMap.put(site, sitePlots);
				results.csvsMap.put(site, siteCSVs);
				for (Period period : periods) {
					for (double prob : probLevels) {
						String periodName;
						if (period == Period.GM0P00)
							periodName = "PGA";
						else
							periodName = (float)period.getValue()+"s SA";
						String xAxisName = periodName+", "+(int)(prob*100d)+"% in "+(int)duration+"yr";
						BranchSensitivityHistogram hist = new BranchSensitivityHistogram(xAxisName);
						for (MagDependentAperiodicityOptions cov : covs) {
							double covWeight = FaultSystemSolutionERF.getWeightForCOV(cov);
							String covName;
							if (cov == null)
								covName = "POISSON";
							else
								covName = cov.name();
							for (AttenRelRef imr : imrs.keySet()) {
								double imrWeight = imrs.get(imr);
								String binFilePrefix = buildBinFilePrefix(imr, period, cov);
								File binFile = new File(new File(curveDir, site.getName()), binFilePrefix+".bin");
								Preconditions.checkState(binFile.exists(), "Bin file not found: "+binFile.getAbsolutePath());
								List<DiscretizedFunc> curves = Lists.newArrayList();
								try {
									BinaryHazardCurveReader reader = new BinaryHazardCurveReader(binFile.getAbsolutePath());
									DiscretizedFunc readCurve = reader.nextCurve();
									while (readCurve != null) {
										curves.add(readCurve);
										readCurve = reader.nextCurve();
									}
								} catch (Exception e) {
									ExceptionUtils.throwAsRuntimeException(e);
								}
//								List<DiscretizedFunc> curves = resultsTables.get(site).get(imr, period).get(cov);
								for (int i=0; i<branches.length; i++) {
									LogicTreeBranch branch = branches[i];
									double weight = branchWeights[i]*imrWeight*covWeight;
									
									DiscretizedFunc curve = curves.get(i);
									// make sure it's not NaNs
									String metadata = "site="+site.getName()+", prefix="+binFilePrefix
											+", branch="+i+", "+branch.buildFileName();
									validateCurve(curve, metadata);
//									for (Point2D pt : curve) {
//										if (Double.isNaN(pt.getY())) {
//											System.out.println("NaN found for site="+site.getName()+", prefix="+binFilePrefix
//													+", branch="+i+", pt="+pt);
//											break;
//										}
////										Preconditions.checkState(!Double.isNaN(pt.getY()),
////												"NaN found for site="+site.getName()+", prefix="+binFilePrefix
////												+", branch="+i+", pt="+pt);
//									}
									double val = HazardDataSetLoader.getCurveVal(curve, false, prob);
									
									if (Double.isNaN(val)) {
										System.err.flush();
										System.out.println("NaN probability value! site="+site.getName()+", prefix="+binFilePrefix
													+", branch="+i+", "+branch.buildFileName());
										System.out.print("\tx:");
										for (Point2D pt : curve)
											System.out.print("\t"+(float)pt.getX());
										System.out.println();
										System.out.print("\ty:");
										for (Point2D pt : curve)
											System.out.print("\t"+(float)pt.getY());
										System.out.println();
										val = curve.getMaxX();
										System.out.flush();
										System.err.println("Using max val="+val);
										System.err.flush();
									}
									
									Preconditions.checkState(Doubles.isFinite(val), "Non finite val: "+val+". "+metadata);
									
									hist.addValues(branch, val, weight, "MagDepAperiodicity", covName,
											"GMPE", imr.getShortName());
								}
							}
						}
						double delta = 0.02;
						
						// make sure we get at least 5 bins
						
						Map<String, PlotSpec> histSpecs = hist.getStackedHistPlots(true, delta);
						
						DiscretizedFunc f1 = (DiscretizedFunc) histSpecs.get(histSpecs.keySet().iterator().next()).getPlotElems().get(0);
						while (f1.size() < 5) {
							delta /= 2;
							histSpecs = hist.getStackedHistPlots(true, delta);
							
							f1 = (DiscretizedFunc) histSpecs.get(histSpecs.keySet().iterator().next()).getPlotElems().get(0);
						}
						
						siteCSVs.put(period, prob, hist.getStaticsticsCSV());
						
						sitePlots.put(period, prob, histSpecs);
						hist = null;
						System.gc();
					}
				}
			}
			return results;
		}
		
		private static void validateCurve(DiscretizedFunc curve, String metadata) {
			Preconditions.checkState(curve.size() > 1,
					"Hazard curve has too few points ("+curve.size()+")! "+metadata);
			double sumY = 0d;
			for (int i=0; i<curve.size(); i++) {
				Point2D pt = curve.get(i);
				// Y checks
				if (!Doubles.isFinite(pt.getY()))
					throw new IllegalStateException("Non finite Y value at index "+i+": "+pt+". "+metadata);
				if ((float)pt.getY() < 0f || (float)pt.getY() > 1f)
					throw new IllegalStateException("Y value not in range [0 1] at index "+i+": "+pt+". "+metadata);
				if (i > 1 && (float)pt.getY() > (float)curve.getY(i-1))
					throw new IllegalStateException("Y value not monotonically decreasing at index "+i+": "
							+pt+" > "+curve.get(i-1)+". "+metadata);
				sumY += pt.getY();
				// X checks
				if (!Doubles.isFinite(pt.getX()))
					throw new IllegalStateException("Non finite X value at index "+i+": "+pt+". "+metadata);
				if ((float)pt.getY() < 0f)
					throw new IllegalStateException("X value < 0 at index "+i+": "+pt+". "+metadata);
			}
			Preconditions.checkState(sumY > 0, "Hazard curve is all zeros! "+metadata);
		}

		@Override
		protected boolean usesERFs() {
			return true;
		}

		@Override
		protected boolean isApplyAftershockFilter() {
			return false;
		}

		@Override
		protected boolean isTimeDependent() {
			return true;
		}
		
	}
	
	public static void writeERFProbModelsFile(File dir,
			Table<Double, LogicTreeBranch, Map<MagDependentAperiodicityOptions, double[]>> probsTable) throws IOException {
		File tempDir = Files.createTempDir();
		
		if (!dir.exists())
			dir.mkdir();
		
		for (double duration : ERFProbModelCalc.durations) {
			for (MagDependentAperiodicityOptions cov : ERFProbModelCalc.covs) {
				List<String> binFileNames = Lists.newArrayList();
				
				for (LogicTreeBranch branch : probsTable.columnKeySet()) {
					double[] probs = probsTable.get(duration, branch).get(cov);
					
					File binFile = new File(tempDir, branch.buildFileName()+".bin");
					
					MatrixIO.doubleArrayToFile(probs, binFile);
					
					binFileNames.add(binFile.getName());
				}
				
				String covStr;
				if (cov == null)
					covStr = "POISSON";
				else
					covStr = cov.name();
				
				File zipFile = new File(dir, "probs_"+(int)duration+"yr_"+covStr+".zip");
				
				FileUtils.createZipFile(zipFile.getAbsolutePath(), tempDir.getAbsolutePath(), binFileNames);
			}
		}
		
		FileUtils.deleteRecursive(tempDir);
	}
	
	public static class ERFProbModelCalc extends CompoundFSSPlots {

		private static MagDependentAperiodicityOptions[] covs = {MagDependentAperiodicityOptions.LOW_VALUES,
			MagDependentAperiodicityOptions.MID_VALUES, MagDependentAperiodicityOptions.HIGH_VALUES, null};
//		private static MagDependentAperiodicityOptions[] covs = {MagDependentAperiodicityOptions.MID_VALUES, null};
		
//		private static final double[] durations = { 1d };
		private static final double[] durations = { 30d };
		
		// duration, branch: prob[rupIndex]
		private Table<Double, LogicTreeBranch, Map<MagDependentAperiodicityOptions, double[]>> probsTable;
		
		public ERFProbModelCalc() {
			probsTable = HashBasedTable.create();
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			throw new IllegalStateException("Should never be called, ERF plot");
		}

		@Override
		protected void processERF(LogicTreeBranch branch,
				FaultSystemSolutionERF erf, int solIndex) {
			debug(solIndex, "processing ERF for "+branch.buildFileName());
			double origDuration = erf.getTimeSpan().getDuration();
			erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
			
			int numRups = erf.getSolution().getRupSet().getNumRuptures();
			for (double duration : durations) {
				Map<MagDependentAperiodicityOptions, double[]> covProbs = Maps.newHashMap();
				for (MagDependentAperiodicityOptions cov : covs) {
					if (cov == null) {
						erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
					} else {
						erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
						erf.setParameter(MagDependentAperiodicityParam.NAME, cov);
					}
					erf.getTimeSpan().setDuration(duration);
					debug(solIndex, "updating forecast for cov="+cov);
					erf.updateForecast();
					debug(solIndex, "done updating forecast for cov="+cov);
					
					double[] probs = new double[numRups];
					
					for (int rupID=0; rupID<numRups; rupID++) {
						int sourceID = erf.getSrcIndexForFltSysRup(rupID);
						if (sourceID < 0)
							continue;
						
						ProbEqkSource source = erf.getSource(sourceID);
						Preconditions.checkState(source.getNumRuptures() == 1);
						probs[rupID] = source.computeTotalProb();
					}
					
					covProbs.put(cov, probs);
				}
				synchronized (this) {
					probsTable.put(duration, branch, covProbs);
				}
			}
			erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.INCLUDE);
			erf.getTimeSpan().setDuration(origDuration);
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots other : otherCalcs) {
				ERFProbModelCalc o = (ERFProbModelCalc)other;
				
				probsTable.putAll(o.probsTable);
			}
		}
		
		@Override
		protected void doFinalizePlot() {
			
		}

		@Override
		protected boolean usesERFs() {
			return true;
		}

		@Override
		protected boolean isApplyAftershockFilter() {
			return false;
		}

		@Override
		protected boolean isTimeDependent() {
			return true;
		}
		
	}

	/*
	 * Paleo fault based plots
	 */
	
	public static void writePaleoFaultPlots(FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, File dir) throws IOException {
		PaleoFaultPlot plot = new PaleoFaultPlot(weightProvider);
		plot.buildPlot(fetch);

		writePaleoFaultPlots(plot.plotsMap, dir);
	}

	public static void writePaleoFaultPlots(
			Map<FaultModels, Map<String, PlotSpec[]>> plotsMap, File dir)
			throws IOException {
		boolean multiple = plotsMap.keySet().size() > 1;

		if (!dir.exists())
			dir.mkdir();

		for (FaultModels fm : plotsMap.keySet()) {
			Map<String, PlotSpec[]> specs = plotsMap.get(fm);

			String prefix = null;
			if (multiple)
				prefix = fm.getShortName();

			CommandLineInversionRunner.writePaleoFaultPlots(specs, prefix, dir);
		}
	}

	/**
	 * Paleo fault based plot for multiple solutions. This will show min/max/mean across all
	 * logic tree branches on faults with at least one paleo/ave slip site
	 * 
	 * @author kevin
	 *
	 */
	public static class PaleoFaultPlot extends CompoundFSSPlots {

		private transient PaleoProbabilityModel paleoProbModel;
		private transient BranchWeightProvider weightProvider;

		// on demand
		private Map<FaultModels, Map<String, List<Integer>>> namedFaultsMaps = Maps
				.newHashMap();
		private Map<FaultModels, List<PaleoRateConstraint>> paleoConstraintMaps = Maps
				.newHashMap();
		private Map<FaultModels, List<AveSlipConstraint>> slipConstraintMaps = Maps
				.newHashMap();
		private Map<FaultModels, Map<Integer, List<FaultSectionPrefData>>> allParentsMaps = Maps
				.newHashMap();
		private Map<FaultModels, List<FaultSectionPrefData>> fsdsMap = Maps
				.newHashMap();

		// results
		private Map<FaultModels, List<DataForPaleoFaultPlots>> datasMap = Maps
				.newHashMap();
		private Map<FaultModels, List<List<Double>>> slipRatesMap = Maps
				.newHashMap();
		private Map<FaultModels, List<Double>> weightsMap = Maps.newHashMap();

		// plots
		private Map<FaultModels, Map<String, PlotSpec[]>> plotsMap = Maps
				.newHashMap();

		public PaleoFaultPlot(BranchWeightProvider weightProvider) {
			this.weightProvider = weightProvider;

			try {
				paleoProbModel = UCERF3_PaleoProbabilityModel.load();
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			InversionFaultSystemRupSet rupSet = sol.getRupSet();
			FaultModels fm = rupSet.getFaultModel();

			try {
				debug(solIndex, "Preparing...");
				List<PaleoRateConstraint> paleoRateConstraints = paleoConstraintMaps
						.get(fm);
				if (paleoRateConstraints == null) {
					// this means that it's the first invokation for this fault model. get constraints
					// and set up maps
					synchronized (this) {
						paleoRateConstraints = paleoConstraintMaps.get(fm);
						if (paleoRateConstraints == null) {
							System.out
									.println("I'm in the synchronized block! "
											+ fm);
							// do a bunch of FM specific stuff
							paleoRateConstraints = CommandLineInversionRunner.getPaleoConstraints(
									fm, rupSet);
							slipConstraintMaps.put(fm,
									AveSlipConstraint.load(rupSet.getFaultSectionDataList()));
							allParentsMaps.put(fm, PaleoFitPlotter.getAllParentsMap(
									rupSet.getFaultSectionDataList()));
							namedFaultsMaps.put(fm, fm.getNamedFaultsMapAlt());
							fsdsMap.put(fm, rupSet.getFaultSectionDataList());
							paleoConstraintMaps.put(fm, paleoRateConstraints);
						}
					}
				}

				// keeps track of slip rates for each ave slip constraint
				List<Double> slipsForConstraints = Lists.newArrayList();
				paleoRateConstraints = Lists.newArrayList(paleoRateConstraints);
				List<AveSlipConstraint> aveSlipConstraints = slipConstraintMaps
						.get(fm);
				for (AveSlipConstraint aveSlip : aveSlipConstraints) {
					double slip = rupSet.getSlipRateForSection(aveSlip.getSubSectionIndex());
					paleoRateConstraints.add(new PaleoFitPlotter.AveSlipFakePaleoConstraint(
									aveSlip, aveSlip.getSubSectionIndex(), slip));
					slipsForConstraints.add(slip);
				}

				Map<String, List<Integer>> namedFaultsMap = namedFaultsMaps.get(fm);

				Map<String, List<PaleoRateConstraint>> namedFaultConstraintsMap = PaleoFitPlotter
						.getNamedFaultConstraintsMap(paleoRateConstraints,
								rupSet.getFaultSectionDataList(), namedFaultsMap);

				Map<Integer, List<FaultSectionPrefData>> allParentsMap = allParentsMaps.get(fm);

				double weight = weightProvider.getWeight(branch);

				debug(solIndex, "Building...");
				//  build all data for this solution
				DataForPaleoFaultPlots data = DataForPaleoFaultPlots.build(sol,
						namedFaultsMap, namedFaultConstraintsMap,
						allParentsMap, paleoProbModel, weight);

				debug(solIndex, "Archiving results...");

				synchronized (this) {
					// store results
					List<DataForPaleoFaultPlots> datasList = datasMap.get(fm);
					if (datasList == null) {
						datasList = Lists.newArrayList();
						datasMap.put(fm, datasList);
					}
					datasList.add(data);

					List<List<Double>> slipRates = slipRatesMap.get(fm);
					if (slipRates == null) {
						slipRates = Lists.newArrayList();
						for (int i = 0; i < slipsForConstraints.size(); i++)
							slipRates.add(new ArrayList<Double>());
						slipRatesMap.put(fm, slipRates);
					}
					Preconditions.checkState(
							slipRates.size() == slipsForConstraints.size(),
							"Slip rate sizes inconsistent!");
					for (int i = 0; i < slipsForConstraints.size(); i++)
						slipRates.get(i).add(slipsForConstraints.get(i));

					List<Double> weightsList = weightsMap.get(fm);
					if (weightsList == null) {
						weightsList = Lists.newArrayList();
						weightsMap.put(fm, weightsList);
					}
					weightsList.add(weight);

					debug(solIndex,
							"Done calculating data for " + fm.getShortName()
									+ " #" + (weightsList.size()));
				}
			} catch (Exception e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}

		@Override
		protected void doFinalizePlot() {
			// build PlotSpec instances from data. Keep each FM separate
			for (FaultModels fm : datasMap.keySet()) {
				// build compound ave slips
				List<AveSlipConstraint> aveSlips = slipConstraintMaps.get(fm);

				List<List<Double>> slipVals = slipRatesMap.get(fm);

				List<PaleoRateConstraint> paleoRateConstraints = paleoConstraintMaps
						.get(fm);

				double[] weights = Doubles.toArray(weightsMap.get(fm));

				for (int i = 0; i < aveSlips.size(); i++) {
					List<Double> slipList = slipVals.get(i);
					double[] slipArray = Doubles.toArray(slipList);
					Preconditions.checkState(
							slipArray.length == weights.length,
							slipArray.length + " != " + weights.length);

					AveSlipConstraint constr = aveSlips.get(i);

					paleoRateConstraints
							.add(new PaleoFitPlotter.AveSlipFakePaleoConstraint(
									constr, constr.getSubSectionIndex(),
									slipArray, weights));
				}

				Map<String, List<Integer>> namedFaultsMap = namedFaultsMaps
						.get(fm);
				Map<String, List<PaleoRateConstraint>> namedFaultConstraintsMap = PaleoFitPlotter
						.getNamedFaultConstraintsMap(paleoRateConstraints,
								fsdsMap.get(fm), namedFaultsMap);

				List<DataForPaleoFaultPlots> datas = datasMap.get(fm);

				Map<Integer, List<FaultSectionPrefData>> allParentsMap = allParentsMaps
						.get(fm);

				Map<String, PlotSpec[]> specsMap = PaleoFitPlotter
						.getFaultSpecificPaleoPlotSpecs(namedFaultsMap,
								namedFaultConstraintsMap, datas, allParentsMap);

				plotsMap.put(fm, specsMap);
			}
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				PaleoFaultPlot o = (PaleoFaultPlot) otherCalc;
				for (FaultModels fm : o.allParentsMaps.keySet()) {
					if (!allParentsMaps.containsKey(fm)) {
						// add the fault model specific values
						namedFaultsMaps.put(fm, o.namedFaultsMaps.get(fm));
						paleoConstraintMaps.put(fm,
								o.paleoConstraintMaps.get(fm));
						slipConstraintMaps
								.put(fm, o.slipConstraintMaps.get(fm));
						allParentsMaps.put(fm, o.allParentsMaps.get(fm));
						fsdsMap.put(fm, o.fsdsMap.get(fm));
					}
					// now add data
					List<DataForPaleoFaultPlots> datasList = datasMap.get(fm);
					if (datasList == null) {
						datasList = Lists.newArrayList();
						datasMap.put(fm, datasList);
					}
					datasList.addAll(o.datasMap.get(fm));

					List<List<Double>> slipRatesList = slipRatesMap.get(fm);
					if (slipRatesList == null) {
						List<AveSlipConstraint> slipConstraints = slipConstraintMaps
								.get(fm);
						slipRatesList = Lists.newArrayList();
						for (int i = 0; i < slipConstraints.size(); i++)
							slipRatesList.add(new ArrayList<Double>());
						slipRatesMap.put(fm, slipRatesList);
					}
					for (int i = 0; i < slipRatesList.size(); i++)
						slipRatesList.get(i).addAll(
								o.slipRatesMap.get(fm).get(i));

					List<Double> weightsList = weightsMap.get(fm);
					if (weightsList == null) {
						weightsList = Lists.newArrayList();
						weightsMap.put(fm, weightsList);
					}
					weightsList.addAll(o.weightsMap.get(fm));
				}
			}
		}

		protected Map<FaultModels, Map<String, PlotSpec[]>> getPlotsMap() {
			return plotsMap;
		}

	}

	public static void writePaleoCorrelationPlots(
			FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, File dir) throws IOException {
		PaleoSiteCorrelationPlot plot = new PaleoSiteCorrelationPlot(
				weightProvider);
		plot.buildPlot(fetch);

		writePaleoCorrelationPlots(plot.plotsMap, dir);
	}

	public static void writePaleoCorrelationPlots(
			Map<String, PlotSpec> plotsMap, File dir) throws IOException {
		System.out.println("Making paleo corr plots for "
				+ plotsMap.keySet().size() + " Faults");

		if (!dir.exists())
			dir.mkdir();

		CommandLineInversionRunner.writePaleoCorrelationPlots(dir, plotsMap);
	}

	/**
	 * Paleo site correlation plot across multiple logic tree branches
	 * @author kevin
	 *
	 */
	public static class PaleoSiteCorrelationPlot extends CompoundFSSPlots {

		private transient PaleoProbabilityModel paleoProbModel;
		private transient BranchWeightProvider weightProvider;

		private Map<FaultModels, Map<String, List<PaleoSiteCorrelationData>>> corrsListsMap = Maps
				.newHashMap();

		// <fault model, data list of: <fault name, corr values>>
		private List<Map<String, double[]>> data = Lists.newArrayList();
		private List<Double> weights = Lists.newArrayList();

		private Map<String, PlotSpec> plotsMap = Maps.newHashMap();

		public PaleoSiteCorrelationPlot(BranchWeightProvider weightProvider) {
			this.weightProvider = weightProvider;

			try {
				paleoProbModel = UCERF3_PaleoProbabilityModel.load();
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			FaultModels fm = sol.getRupSet().getFaultModel();

			try {
				debug(solIndex, "Preparing...");
				Map<String, List<PaleoSiteCorrelationData>> corrs = corrsListsMap
						.get(fm);
				if (corrs == null) {
					synchronized (fm) {
						// first call for this FM, prepare maps/tables
						corrs = corrsListsMap.get(fm);
						if (corrs == null) {
							debug(solIndex, "I'm in the synchronized block! "
									+ fm);
							corrs = Maps.newHashMap();

							Map<String, Table<String, String, PaleoSiteCorrelationData>> table = PaleoSiteCorrelationData
									.loadPaleoCorrelationData(sol);

							for (String faultName : table.keySet()) {
								List<PaleoSiteCorrelationData> corrsToPlot = PaleoSiteCorrelationData
										.getCorrelataionsToPlot(table
												.get(faultName));
								corrs.put(faultName, corrsToPlot);
							}
							corrsListsMap.put(fm, corrs);
						}
					}
				}

				double weight = weightProvider.getWeight(branch);

				// mapping from fault name to correlation rates of each other
				// paleo site to plot
				Map<String, double[]> myData = Maps.newHashMap();

				debug(solIndex, "Building...");
				for (String faultName : corrs.keySet()) {
					List<PaleoSiteCorrelationData> corrsToPlot = corrs
							.get(faultName);

					double[] vals = new double[corrsToPlot.size()];
					for (int i = 0; i < vals.length; i++) {
						PaleoSiteCorrelationData corr = corrsToPlot.get(i);
						vals[i] = PaleoSiteCorrelationData.getRateCorrelated(
								paleoProbModel, sol, corr.getSite1SubSect(),
								corr.getSite2SubSect());
					}

					myData.put(faultName, vals);
				}

				debug(solIndex, "Archiving results...");

				synchronized (this) {
					data.add(myData);
					weights.add(weight);
				}
			} catch (Exception e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				PaleoSiteCorrelationPlot o = (PaleoSiteCorrelationPlot) otherCalc;

				data.addAll(o.data);
				weights.addAll(o.weights);

				for (FaultModels fm : o.corrsListsMap.keySet()) {
					if (!corrsListsMap.containsKey(fm))
						corrsListsMap.put(fm, o.corrsListsMap.get(fm));
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			// generate the plot
			Map<String, List<PaleoSiteCorrelationData>> allCorrsMap = Maps
					.newHashMap();
			for (FaultModels fm : corrsListsMap.keySet()) {
				Map<String, List<PaleoSiteCorrelationData>> corrsForFM = corrsListsMap
						.get(fm);
				for (String faultName : corrsForFM.keySet()) {
					if (!allCorrsMap.containsKey(faultName))
						allCorrsMap.put(faultName, corrsForFM.get(faultName));
				}
			}

			for (String faultName : allCorrsMap.keySet()) {
				List<double[]> solValsForFault = Lists.newArrayList();
				List<Double> weightsForFault = Lists.newArrayList();

				for (int s = 0; s < data.size(); s++) {
					double[] solData = data.get(s).get(faultName);
					if (solData != null) {
						solValsForFault.add(solData);
						weightsForFault.add(weights.get(s));
					}
				}

				List<PaleoSiteCorrelationData> corrs = allCorrsMap
						.get(faultName);

				List<double[]> solValues = Lists.newArrayList();
				double[] weights = Doubles.toArray(weightsForFault);

				for (int i = 0; i < corrs.size(); i++) {
					double[] vals = new double[solValsForFault.size()];
					for (int s = 0; s < solValsForFault.size(); s++)
						vals[s] = solValsForFault.get(s)[i];
					double min = StatUtils.min(vals);
					double max = StatUtils.max(vals);
					double mean = FaultSystemSolutionFetcher.calcScaledAverage(
							vals, weights);

					double[] ret = { min, max, mean };
					System.out.println("Vals for " + faultName + " CORR " + i
							+ ": " + min + "," + max + "," + mean + " ("
							+ vals.length + " sols)");
					solValues.add(ret);
				}

				PlotSpec spec = PaleoSiteCorrelationData
						.getCorrelationPlotSpec(faultName, corrs, solValues,
								paleoProbModel);

				plotsMap.put(faultName, spec);
			}
		}

		public Map<String, PlotSpec> getPlotsMap() {
			return plotsMap;
		}
	}

	public static void writeParentSectionMFDPlots(
			FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, File dir) throws IOException {
		ParentSectMFDsPlot plot = new ParentSectMFDsPlot(weightProvider);
		plot.buildPlot(fetch);

		writeParentSectionMFDPlots(plot, dir);
	}

	public static void writeParentSectionMFDPlots(ParentSectMFDsPlot plot,
			File dir) throws IOException {
		System.out.println("Making parent sect MFD plots for "
				+ plot.plotNuclIncrMFDs.keySet().size() + " Faults");

		if (!dir.exists())
			dir.mkdir();
		
		File particIncrSubDir = new File(dir, "participation_incremental");
		if (!particIncrSubDir.exists())
			particIncrSubDir.mkdir();
		File particCmlSubDir = new File(dir, "participation_cumulative");
		if (!particCmlSubDir.exists())
			particCmlSubDir.mkdir();
		File nuclIncrSubDir = new File(dir, "nucleation_incremental");
		if (!nuclIncrSubDir.exists())
			nuclIncrSubDir.mkdir();
		File nuclCmlSubDir = new File(dir, "nucleation_cumulative");
		if (!nuclCmlSubDir.exists())
			nuclCmlSubDir.mkdir();

		CSVFile<String> nucleationCSV = new CSVFile<String>(true);
		CSVFile<String> nucleationMinCSV = new CSVFile<String>(true);
		CSVFile<String> nucleationMaxCSV = new CSVFile<String>(true);
		CSVFile<String> participationCSV = new CSVFile<String>(true);
		CSVFile<String> participationMinCSV = new CSVFile<String>(true);
		CSVFile<String> participationMaxCSV = new CSVFile<String>(true);

		List<String> header = Lists.newArrayList();
		List<Double> xVals = Lists.newArrayList();
		for (double x = ParentSectMFDsPlot.minX; x <= ParentSectMFDsPlot.maxX; x += ParentSectMFDsPlot.delta)
			xVals.add(x);
		header.add("Fault ID");
		header.add("Fault Name");
		for (double x : xVals) {
			header.add("M" + (float) x);
			header.add("(UCERF2)");
		}
		nucleationCSV.addLine(header);
		nucleationMinCSV.addLine(header);
		nucleationMaxCSV.addLine(header);
		participationCSV.addLine(header);
		participationMinCSV.addLine(header);
		participationMaxCSV.addLine(header);

		for (Integer parentID : plot.plotNuclIncrMFDs.keySet()) {
			ArrayList<IncrementalMagFreqDist> ucerf2NuclMFDs = UCERF2_Section_MFDsCalc
					.getMeanMinAndMaxMFD(parentID, false, false);
			ArrayList<IncrementalMagFreqDist> ucerf2NuclCmlMFDs = UCERF2_Section_MFDsCalc
					.getMeanMinAndMaxMFD(parentID, false, true);
			ArrayList<IncrementalMagFreqDist> ucerf2PartMFDs = UCERF2_Section_MFDsCalc
					.getMeanMinAndMaxMFD(parentID, true, false);
			ArrayList<IncrementalMagFreqDist> ucerf2PartCmlMFDs = UCERF2_Section_MFDsCalc
					.getMeanMinAndMaxMFD(parentID, true, true);

			String name = plot.namesMap.get(parentID);

			List<IncrementalMagFreqDist> nuclMFDs = plot.plotNuclIncrMFDs
					.get(parentID);
			List<EvenlyDiscretizedFunc> nuclCmlMFDs = plot.plotNuclCmlMFDs
					.get(parentID);
			List<IncrementalMagFreqDist> partMFDs = plot.plotPartIncrMFDs
					.get(parentID);
			List<EvenlyDiscretizedFunc> partCmlMFDs = plot.plotPartCmlMFDs
					.get(parentID);

			for (int i = 0; i < nuclMFDs.size(); i++) {
				nuclMFDs.get(i).setInfo(" ");
				nuclCmlMFDs.get(i).setInfo(" ");
				partMFDs.get(i).setInfo(" ");
				partCmlMFDs.get(i).setInfo(" ");
			}

			EvenlyDiscretizedFunc nuclCmlMFD = nuclCmlMFDs.get(nuclCmlMFDs
					.size() - 3);
			nuclCmlMFD.setInfo(getCmlMFDInfo(nuclCmlMFD, false));
			EvenlyDiscretizedFunc partCmlMFD = partCmlMFDs.get(partCmlMFDs
					.size() - 3);
			partCmlMFD.setInfo(getCmlMFDInfo(partCmlMFD, false));

			EvenlyDiscretizedFunc ucerf2NuclCmlMFD = null;
			EvenlyDiscretizedFunc ucerf2NuclCmlMinMFD = null;
			EvenlyDiscretizedFunc ucerf2NuclCmlMaxMFD = null;
			EvenlyDiscretizedFunc ucerf2PartCmlMFD = null;
			EvenlyDiscretizedFunc ucerf2PartCmlMinMFD = null;
			EvenlyDiscretizedFunc ucerf2PartCmlMaxMFD = null;
			if (ucerf2NuclCmlMFDs != null) {
				ucerf2NuclCmlMFD = ucerf2NuclCmlMFDs.get(0);
				ucerf2NuclCmlMinMFD = ucerf2NuclCmlMFDs.get(1);
				ucerf2NuclCmlMaxMFD = ucerf2NuclCmlMFDs.get(2);
				ucerf2PartCmlMFD = ucerf2PartCmlMFDs.get(0);
				ucerf2PartCmlMinMFD = ucerf2PartCmlMFDs.get(1);
				ucerf2PartCmlMaxMFD = ucerf2PartCmlMFDs.get(2);
			}

			nucleationCSV.addLine(getCSVLine(xVals, parentID, name, nuclCmlMFD,
					ucerf2NuclCmlMFD));
			nucleationMinCSV.addLine(getCSVLine(xVals, parentID, name,
					nuclCmlMFDs.get(nuclCmlMFDs.size() - 2),
					ucerf2NuclCmlMinMFD));
			nucleationMaxCSV.addLine(getCSVLine(xVals, parentID, name,
					nuclCmlMFDs.get(nuclCmlMFDs.size() - 1),
					ucerf2NuclCmlMaxMFD));
			participationCSV.addLine(getCSVLine(xVals, parentID, name,
					partCmlMFD, ucerf2PartCmlMFD));
			participationMinCSV.addLine(getCSVLine(xVals, parentID, name,
					partCmlMFDs.get(partCmlMFDs.size() - 2),
					ucerf2PartCmlMinMFD));
			participationMaxCSV.addLine(getCSVLine(xVals, parentID, name,
					partCmlMFDs.get(partCmlMFDs.size() - 1),
					ucerf2PartCmlMaxMFD));

			List<IncrementalMagFreqDist> subSeismoMFDs = plot.plotSubSeismoIncrMFDs
					.get(parentID);
			List<EvenlyDiscretizedFunc> subSeismoCmlMFDs = plot.plotSubSeismoCmlMFDs
					.get(parentID);
			List<IncrementalMagFreqDist> subPlusSupraSeismoNuclMFDs = plot.plotSubPlusSupraSeismoNuclMFDs
					.get(parentID);
			List<EvenlyDiscretizedFunc> subPlusSupraSeismoNuclCmlMFDs = plot.plotSubPlusSupraSeismoNuclCmlMFDs
					.get(parentID);
			List<IncrementalMagFreqDist> subPlusSupraSeismoParticMFDs = plot.plotSubPlusSupraSeismoParticMFDs
					.get(parentID);
			List<EvenlyDiscretizedFunc> subPlusSupraSeismoParticCmlMFDs = plot.plotSubPlusSupraSeismoParticCmlMFDs
					.get(parentID);

			// nucleation
			// incremental
			writeParentSectionMFDPlot(nuclIncrSubDir, nuclMFDs, ucerf2NuclMFDs,
					subSeismoMFDs, subPlusSupraSeismoNuclMFDs, parentID, name, true, false);
			// cumulative
			writeParentSectionMFDPlot(nuclCmlSubDir, nuclCmlMFDs, ucerf2NuclCmlMFDs,
					subSeismoCmlMFDs, subPlusSupraSeismoNuclCmlMFDs, parentID, name, true, true);
			
			// participation
			writeParentSectionMFDPlot(particIncrSubDir, partMFDs, ucerf2PartMFDs,
					subSeismoMFDs, subPlusSupraSeismoParticMFDs, parentID, name, false, false);
			// cumulative
			writeParentSectionMFDPlot(particCmlSubDir, partCmlMFDs, ucerf2PartCmlMFDs,
					subSeismoCmlMFDs, subPlusSupraSeismoParticCmlMFDs, parentID, name, false, true);
			
//			writeParentSectionMFDPlot(dir, nuclMFDs, nuclCmlMFDs,
//					ucerf2NuclMFDs, ucerf2NuclCmlMFDs, subSeismoMFDs,
//					subPlusSupraSeismoMFDs, subPlusSupraSeismoCmlMFDs,
//					parentID, name, true);
//			writeParentSectionMFDPlot(dir, partMFDs, partCmlMFDs,
//					ucerf2PartMFDs, ucerf2PartCmlMFDs, null, null, null,
//					parentID, name, false);
		}

		nucleationCSV.writeToFile(new File(dir,
				"cumulative_nucleation_mfd_comparisons.csv"));
		nucleationMinCSV.writeToFile(new File(dir,
				"cumulative_nucleation_min_mfd_comparisons.csv"));
		nucleationMaxCSV.writeToFile(new File(dir,
				"cumulative_nucleation_max_mfd_comparisons.csv"));
		participationCSV.writeToFile(new File(dir,
				"cumulative_participation_mfd_comparisons.csv"));
		participationMinCSV.writeToFile(new File(dir,
				"cumulative_participation_min_mfd_comparisons.csv"));
		participationMaxCSV.writeToFile(new File(dir,
				"cumulative_participation_max_mfd_comparisons.csv"));
	}

	private static List<String> getCSVLine(List<Double> xVals, int parentID,
			String name, EvenlyDiscretizedFunc cmlMFD,
			EvenlyDiscretizedFunc ucerf2CmlMFD) {
		List<String> line = Lists.newArrayList(parentID + "", name);

		for (int i = 0; i < xVals.size(); i++) {
			line.add(cmlMFD.getY(i) + "");
			double x = xVals.get(i);
			if (ucerf2CmlMFD != null && ucerf2CmlMFD.getMinX() <= x
					&& ucerf2CmlMFD.getMaxX() >= x) {
				line.add(ucerf2CmlMFD.getClosestYtoX(x) + "");
			} else {
				line.add("");
			}
		}

		return line;
	}

	private static String getCmlMFDInfo(EvenlyDiscretizedFunc cmlMFD,
			boolean isAlreadyRI) {
		double totRate = cmlMFD.getMaxY();
		// double rate6p7 = cmlMFD.getClosestY(6.7d);
		double rate6p7 = cmlMFD.getInterpolatedY_inLogYDomain(6.7d);
		// for (int i=0; i<cmlMFD.getNum(); i++)
		// System.out.println("CML: "+i+": "+cmlMFD.getX(i)+","+cmlMFD.getY(i));
		String info;
		if (isAlreadyRI) {
			info = "\t\tTotal RI: " + (int) Math.round(totRate) + "\n";
			info += "\t\tRI M>=6.7: " + (int) Math.round(rate6p7);
		} else {
			info = "\t\tTotal Rate: " + (float) totRate + "\n";
			info += "\t\tRate M>=6.7: " + (float) rate6p7 + "\n";
			double totRI = 1d / totRate;
			double ri6p7 = 1d / rate6p7;
			info += "\t\tTotal RI: " + (int) Math.round(totRI) + "\n";
			info += "\t\tRI M>=6.7: " + (int) Math.round(ri6p7);
		}
		// System.out.println(info);

		return info;
	}

	private static void writeParentSectionMFDPlot(File dir,
			List<? extends EvenlyDiscretizedFunc> mfds,
			List<? extends EvenlyDiscretizedFunc> ucerf2MFDs,
			List<? extends EvenlyDiscretizedFunc> subSeismoMFDs,
			List<? extends EvenlyDiscretizedFunc> subPlusSupraSeismoMFDs, int id,
			String name, boolean nucleation, boolean cumulative) throws IOException {
		CommandLineInversionRunner.writeParentSectMFDPlot(dir, mfds,
				subSeismoMFDs, subPlusSupraSeismoMFDs, ucerf2MFDs, false, id, name, nucleation, cumulative);
	}

	/**
	 * Parent section MFD plots
	 * @author kevin
	 *
	 */
	public static class ParentSectMFDsPlot extends CompoundFSSPlots {

		private transient BranchWeightProvider weightProvider;

		// none (except min/mean/max which are always included)
		private double[] fractiles;

		private ConcurrentMap<FaultModels, HashSet<Integer>> parentMapsCache = Maps
				.newConcurrentMap();

		// these are organized as (region, solution)
		private Map<Integer, XY_DataSetList> nuclIncrMFDs = Maps.newHashMap();
		private Map<Integer, XY_DataSetList> nuclSubSeismoMFDs = Maps
				.newHashMap();
		private Map<Integer, XY_DataSetList> partIncrMFDs = Maps.newHashMap();

		private Map<Integer, List<Double>> weightsMap = Maps.newHashMap();
		private ConcurrentMap<Integer, String> namesMap = Maps
				.newConcurrentMap();

		private static final double minX = 5.05d;
		private static final double maxX = 9.05d;
		private static final double delta = 0.1d;
		private static final int num = (int) ((maxX - minX) / delta) + 1;

		private Map<Integer, List<IncrementalMagFreqDist>> plotNuclIncrMFDs = Maps.newHashMap();
		private Map<Integer, List<IncrementalMagFreqDist>> plotSubSeismoIncrMFDs = Maps.newHashMap();
		private Map<Integer, List<EvenlyDiscretizedFunc>> plotSubSeismoCmlMFDs = Maps.newHashMap();
		private Map<Integer, List<IncrementalMagFreqDist>> plotSubPlusSupraSeismoNuclMFDs = Maps.newHashMap();
		private Map<Integer, List<EvenlyDiscretizedFunc>> plotSubPlusSupraSeismoNuclCmlMFDs = Maps.newHashMap();
		private Map<Integer, List<IncrementalMagFreqDist>> plotSubPlusSupraSeismoParticMFDs = Maps.newHashMap();
		private Map<Integer, List<EvenlyDiscretizedFunc>> plotSubPlusSupraSeismoParticCmlMFDs = Maps.newHashMap();
		private Map<Integer, List<IncrementalMagFreqDist>> plotPartIncrMFDs = Maps.newHashMap();
		private Map<Integer, List<EvenlyDiscretizedFunc>> plotNuclCmlMFDs = Maps.newHashMap();
		private Map<Integer, List<EvenlyDiscretizedFunc>> plotPartCmlMFDs = Maps.newHashMap();

		private static double[] getDefaultFractiles() {
			// double[] ret = { 0.5 };
			double[] ret = {};
			return ret;
		}

		public ParentSectMFDsPlot(BranchWeightProvider weightProvider) {
			this(weightProvider, getDefaultFractiles());
		}

		public ParentSectMFDsPlot(BranchWeightProvider weightProvider,
				double[] fractiles) {
			this.weightProvider = weightProvider;
			this.fractiles = fractiles;
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			InversionFaultSystemRupSet rupSet = sol.getRupSet();
			FaultModels fm = rupSet.getFaultModel();

			debug(solIndex, "cache fetching");
			HashSet<Integer> parentIDs = parentMapsCache.get(fm);
			if (parentIDs == null) {
				// first call for the FM, setup caches
				parentIDs = new HashSet<Integer>();
				for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++) {
					FaultSectionPrefData sect = rupSet.getFaultSectionData(sectIndex);
					Integer parentID = sect.getParentSectionId();
					if (!parentIDs.contains(parentID)) {
						parentIDs.add(parentID);
						namesMap.putIfAbsent(parentID,
								sect.getParentSectionName());
					}
				}
				parentMapsCache.putIfAbsent(fm, parentIDs);
			}

			double weight = weightProvider.getWeight(branch);

			debug(solIndex, "calculating");
			for (Integer parentID : parentIDs) {
				// calculate nucleation/participation MFDs
				SummedMagFreqDist nuclMFD = sol
						.calcNucleationMFD_forParentSect(parentID, minX, maxX,
								num);
				SummedMagFreqDist nuclSubSeismoMFD = sol.getFinalSubSeismoOnFaultMFDForParent(parentID);
				IncrementalMagFreqDist partMFD = sol.calcParticipationMFD_forParentSect(parentID, minX,
								maxX, num);

				synchronized (this) {
					// store results
					if (!nuclIncrMFDs.containsKey(parentID)) {
						nuclIncrMFDs.put(parentID, new XY_DataSetList());
						nuclSubSeismoMFDs.put(parentID, new XY_DataSetList());
						partIncrMFDs.put(parentID, new XY_DataSetList());
						weightsMap.put(parentID, new ArrayList<Double>());
					}
					nuclIncrMFDs.get(parentID).add(nuclMFD);
					nuclSubSeismoMFDs.get(parentID).add(nuclSubSeismoMFD);
					partIncrMFDs.get(parentID).add(partMFD);
					weightsMap.get(parentID).add(weight);
				}
			}
			debug(solIndex, "done");
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				ParentSectMFDsPlot o = (ParentSectMFDsPlot) otherCalc;

				for (Integer parentID : o.nuclIncrMFDs.keySet()) {
					if (!nuclIncrMFDs.containsKey(parentID)) {
						nuclIncrMFDs.put(parentID, new XY_DataSetList());
						nuclSubSeismoMFDs.put(parentID, new XY_DataSetList());
						partIncrMFDs.put(parentID, new XY_DataSetList());
						weightsMap.put(parentID, new ArrayList<Double>());
					}
					nuclIncrMFDs.get(parentID).addAll(
							o.nuclIncrMFDs.get(parentID));
					nuclSubSeismoMFDs.get(parentID).addAll(
							o.nuclSubSeismoMFDs.get(parentID));
					partIncrMFDs.get(parentID).addAll(
							o.partIncrMFDs.get(parentID));
					weightsMap.get(parentID).addAll(o.weightsMap.get(parentID));
					if (!namesMap.containsKey(parentID))
						namesMap.put(parentID, o.namesMap.get(parentID));
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			// this reduces the MFDs for each branch into mean/min/max MFDs for plotting. It also generates
			// cumulative MFDs (with offsets).
			for (Integer parentID : nuclIncrMFDs.keySet()) {
				plotNuclIncrMFDs.put(
						parentID,
						asIncr(getFractiles(nuclIncrMFDs.get(parentID),
								weightsMap.get(parentID),
								"Incremental Nucleation MFD", fractiles)));
				plotNuclCmlMFDs.put(
						parentID,
						asEvenly(getFractiles(
								asCml(nuclIncrMFDs.get(parentID)),
								weightsMap.get(parentID),
								"Cumulative Nucleation MFD", new double[0])));
				plotPartIncrMFDs.put(
						parentID,
						asIncr(getFractiles(partIncrMFDs.get(parentID),
								weightsMap.get(parentID),
								"Incremental Participation MFD", fractiles)));
				plotPartCmlMFDs
						.put(parentID,
								asEvenly(getFractiles(
										asCml(partIncrMFDs.get(parentID)),
										weightsMap.get(parentID),
										"Cumulative Participation MFD",
										new double[0])));
				plotSubSeismoIncrMFDs.put(
						parentID,
						asIncr(getFractiles(nuclSubSeismoMFDs.get(parentID),
								weightsMap.get(parentID),
								"Incremental Sub Seismogenic Nucleation MFD",
								fractiles)));
				plotSubSeismoCmlMFDs.put(
						parentID,
						asEvenly(getFractiles(asCml(nuclSubSeismoMFDs.get(parentID)),
								weightsMap.get(parentID),
								"Cumulative Sub Seismogenic Nucleation MFD",
								fractiles)));
				XY_DataSetList subPlusSupraNuclMFDs = getSummed(
						nuclSubSeismoMFDs.get(parentID),
						nuclIncrMFDs.get(parentID));
				XY_DataSetList subPlusSupraParticMFDs = getSummed(
						nuclSubSeismoMFDs.get(parentID),
						partIncrMFDs.get(parentID));
				plotSubPlusSupraSeismoNuclMFDs.put(parentID,
								asIncr(getFractiles(
										subPlusSupraNuclMFDs,
										weightsMap.get(parentID),
										"Incremental Sub+Supra Seismogenic Nucleation MFD",
										fractiles)));
				plotSubPlusSupraSeismoNuclCmlMFDs.put(parentID,
								asEvenly(getFractiles(
										asCml(subPlusSupraNuclMFDs),
										weightsMap.get(parentID),
										"Cumulative Sub+Supra Seismogenic Nucleation MFD",
										new double[0])));
				plotSubPlusSupraSeismoParticMFDs.put(parentID,
								asIncr(getFractiles(
										subPlusSupraParticMFDs,
										weightsMap.get(parentID),
										"Incremental Sub+Supra Seismogenic Participation MFD",
										fractiles)));
				plotSubPlusSupraSeismoParticCmlMFDs.put(parentID,
								asEvenly(getFractiles(
										asCml(subPlusSupraParticMFDs),
										weightsMap.get(parentID),
										"Cumulative Sub+Supra Seismogenic Participation MFD",
										new double[0])));
			}
		}

		private static XY_DataSetList asCml(XY_DataSetList xyList) {
			XY_DataSetList cmlList = new XY_DataSetList();
			for (XY_DataSet xy : xyList)
				cmlList.add(((IncrementalMagFreqDist) xy).getCumRateDistWithOffset());
			return cmlList;
		}

		private static List<IncrementalMagFreqDist> asIncr(
				List<DiscretizedFunc> funcs) {
			List<IncrementalMagFreqDist> incrMFDs = Lists.newArrayList();
			for (DiscretizedFunc func : funcs)
				incrMFDs.add((IncrementalMagFreqDist) func);
			return incrMFDs;
		}

		private static List<EvenlyDiscretizedFunc> asEvenly(
				List<DiscretizedFunc> funcs) {
			List<EvenlyDiscretizedFunc> incrMFDs = Lists.newArrayList();
			for (DiscretizedFunc func : funcs)
				incrMFDs.add((EvenlyDiscretizedFunc) func);
			return incrMFDs;
		}

		private XY_DataSetList getSummed(XY_DataSetList list1,
				XY_DataSetList list2) {
			XY_DataSetList sumList = new XY_DataSetList();

			for (int i = 0; i < list1.size(); i++) {
				IncrementalMagFreqDist mfd1 = (IncrementalMagFreqDist) list1
						.get(i);
				IncrementalMagFreqDist mfd2 = (IncrementalMagFreqDist) list2
						.get(i);
				SummedMagFreqDist sum = new SummedMagFreqDist(
						InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.NUM_MAG,
						InversionTargetMFDs.DELTA_MAG);
				sum.addIncrementalMagFreqDist(resizeToDimensions(mfd1,
						InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.NUM_MAG,
						InversionTargetMFDs.DELTA_MAG));
				sum.addIncrementalMagFreqDist(resizeToDimensions(mfd2,
						InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.NUM_MAG,
						InversionTargetMFDs.DELTA_MAG));

				sumList.add(sum);
			}

			return sumList;
		}

		private static IncrementalMagFreqDist resizeToDimensions(
				IncrementalMagFreqDist mfd, double min, int num, double delta) {
			if (mfd.getMinX() == min && mfd.size() == num
					&& mfd.getDelta() == delta)
				return mfd;
			IncrementalMagFreqDist resized = new IncrementalMagFreqDist(min,
					num, delta);

			for (int i = 0; i < mfd.size(); i++)
				if (mfd.getY(i) > 0)
					resized.set(mfd.get(i));

			return resized;
		}
		
		public void addAsComparison(Integer parentID,
				IncrementalMagFreqDist nuclIncrMFD, EvenlyDiscretizedFunc nuclCmlMFD,
				IncrementalMagFreqDist partIncrMFD, EvenlyDiscretizedFunc partCmlMFD) {
			nuclIncrMFD.setName(nuclIncrMFD.getName()+" (COMPARISON!)");
			Preconditions.checkState(nuclIncrMFD.getMaxY() > 0);
			nuclCmlMFD.setName(nuclCmlMFD.getName()+" (COMPARISON!)");
			partIncrMFD.setName(partIncrMFD.getName()+" (COMPARISON!)");
			partCmlMFD.setName(partCmlMFD.getName()+" (COMPARISON!)");
			plotNuclIncrMFDs.get(parentID).add(0, nuclIncrMFD);
			plotNuclCmlMFDs.get(parentID).add(0, nuclCmlMFD);
			plotPartIncrMFDs.get(parentID).add(0, partIncrMFD);
			plotPartCmlMFDs.get(parentID).add(0, partCmlMFD);
		}
		
		/**
		 * This is used for generating comparisons on a single fault. The mean curve from the other
		 * plot will be added and plotted with medium thickness (same color)
		 * @param other
		 */
		public void addMeanFromExternalAsFractile(ParentSectMFDsPlot other) {
			for (Integer parentID : other.plotNuclIncrMFDs.keySet()) {
				if (!plotNuclCmlMFDs.containsKey(parentID))
					continue;
				addAsComparison(parentID,
						other.plotNuclIncrMFDs.get(parentID).get(0),
						other.plotNuclCmlMFDs.get(parentID).get(0),
						other.plotPartIncrMFDs.get(parentID).get(0),
						other.plotPartCmlMFDs.get(parentID).get(0));
			}
		}
	}

	public static void writeJumpPlots(FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, File dir, String prefix)
			throws IOException {
		RupJumpPlot plot = new RupJumpPlot(weightProvider);
		plot.buildPlot(fetch);

		writeJumpPlots(plot, dir, prefix);
	}

	public static void writeJumpPlots(RupJumpPlot plot, File dir, String prefix)
			throws IOException {
		System.out.println("Making rup jump plots for " + plot.weights.size()
				+ " sols");
		
		File subDir = new File(dir, "rup_jump_plots");
		if (!subDir.exists())
			subDir.mkdir();

		for (int i = 0; i < plot.minMags.length; i++) {
			CommandLineInversionRunner.writeJumpPlot(subDir, prefix,
					plot.plotSolFuncs.get(i), plot.plotRupSetFuncs.get(i),
					RupJumpPlot.jumpDist, plot.minMags[i], plot.paleoProbs[i]);
		}
	}

	/**
	 * This generates the rupture jump plot which keeps track of the number of 1km or greater
	 * jumps.
	 * @author kevin
	 *
	 */
	public static class RupJumpPlot extends CompoundFSSPlots {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		private double[] minMags = { 7d, 0d };
		private boolean[] paleoProbs = { false, true };

		private double[] fractiles;

		private static final double jumpDist = 1d;

		private transient BranchWeightProvider weightProvider;
		private transient PaleoProbabilityModel paleoProbModel;

		private transient ConcurrentMap<FaultModels, Map<IDPairing, Double>> distancesCache = Maps
				.newConcurrentMap();

		private List<XY_DataSetList> solFuncs = Lists.newArrayList();
		private List<XY_DataSetList> rupSetFuncs = Lists.newArrayList();
		private List<Double> weights = Lists.newArrayList();

		private List<DiscretizedFunc[]> plotSolFuncs = Lists.newArrayList();
		private List<DiscretizedFunc[]> plotRupSetFuncs = Lists.newArrayList();

		public RupJumpPlot(BranchWeightProvider weightProvider) {
			this(weightProvider, new double[0]);
		}

		public RupJumpPlot(BranchWeightProvider weightProvider,
				double[] fractiles) {
			this.weightProvider = weightProvider;
			this.fractiles = fractiles;

			try {
				paleoProbModel = UCERF3_PaleoProbabilityModel.load();
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}

			for (int i = 0; i < minMags.length; i++) {
				solFuncs.add(new XY_DataSetList());
				rupSetFuncs.add(new XY_DataSetList());
			}
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			FaultModels fm = sol.getRupSet().getFaultModel();

			Map<IDPairing, Double> distances = distancesCache.get(fm);
			debug(solIndex, "cache fetching");
			if (distances == null) {
				// calculate distances between subsections
				synchronized (this) {
					distances = distancesCache.get(fm);
					if (distances == null) {
						distances = DeformationModelFetcher.calculateDistances(
								5d, sol.getRupSet().getFaultSectionDataList());
						for (IDPairing pairing : Lists.newArrayList(distances.keySet()))
								distances.put(pairing.getReversed(), distances.get(pairing));
						distancesCache.putIfAbsent(fm, distances);
					}
				}
			}

			double weight = weightProvider.getWeight(branch);

			debug(solIndex, "calculating");
			List<EvenlyDiscretizedFunc[]> myFuncs = Lists.newArrayList();
			for (int i = 0; i < minMags.length; i++) {
				// get the single branch functions, will combine later
				EvenlyDiscretizedFunc[] funcs = CommandLineInversionRunner
						.getJumpFuncs(sol, distances, jumpDist, minMags[i],
								paleoProbModel);
				myFuncs.add(funcs);
			}
			debug(solIndex, "archiving");
			synchronized (this) {
				for (int i = 0; i < myFuncs.size(); i++) {
					EvenlyDiscretizedFunc[] funcs = myFuncs.get(i);
					solFuncs.get(i).add(funcs[0]);
					rupSetFuncs.get(i).add(funcs[1]);
				}
				weights.add(weight);
			}
			debug(solIndex, "done");
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				RupJumpPlot o = (RupJumpPlot) otherCalc;

				for (int i = 0; i < minMags.length; i++) {
					solFuncs.get(i).addAll(o.solFuncs.get(i));
					rupSetFuncs.get(i).addAll(o.rupSetFuncs.get(i));
				}
				weights.addAll(o.weights);
			}
		}

		private static DiscretizedFunc[] toArray(List<DiscretizedFunc> funcs) {
			DiscretizedFunc[] array = new DiscretizedFunc[funcs.size()];
			for (int i = 0; i < funcs.size(); i++)
				array[i] = funcs.get(i);
			return array;
		}

		@Override
		protected void doFinalizePlot() {
			// combine all of the individual branches
			for (int i = 0; i < solFuncs.size(); i++) {
				List<DiscretizedFunc> solFractiles = getFractiles(
						solFuncs.get(i), weights, "Solution Jumps", fractiles);
				List<DiscretizedFunc> rupSetFractiles = getFractiles(
						rupSetFuncs.get(i), weights, "Rup Set Jumps", fractiles);
				plotSolFuncs.add(toArray(solFractiles));
				plotRupSetFuncs.add(toArray(rupSetFractiles));
			}
		}

	}
	
	public static void writeSubSectRITables(FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, File dir, String prefix)
			throws IOException {
		SubSectRITable plot = new SubSectRITable(weightProvider);
		plot.buildPlot(fetch);

		writeSubSectRITables(plot, dir, prefix);
	}

	public static void writeSubSectRITables(SubSectRITable plot, File dir,
			String prefix) throws IOException {
		System.out.println("Making sub sect RI plot!");

		for (FaultModels fm : plot.csvTable.rowKeySet()) {
			for (double minMag : plot.csvTable.columnKeySet()) {
				CSVFile<String> csv = plot.csvTable.get(fm, minMag);
				String fileName;
				if (prefix != null && !prefix.isEmpty())
					fileName = prefix+"_";
				else
					fileName = "";
				if (plot.csvTable.rowKeySet().size() > 1)
					fileName += fm.name()+"_";
				if (minMag > 0)
					fileName += "m"+(float)minMag;
				else
					fileName += "supra_seis";
				File file = new File(dir, fileName+".csv");
				csv.writeToFile(file);
			}
			CSVFile<String> csv = plot.mfdCSVs.get(fm);
			String fileName;
			if (prefix != null && !prefix.isEmpty())
				fileName = prefix+"_";
			else
				fileName = "";
			if (plot.csvTable.rowKeySet().size() > 1)
				fileName += fm.name()+"_";
			fileName += "cumulative_mfds";
			File file = new File(dir, fileName+".csv");
			csv.writeToFile(file);
		}
	}
	
	public static class SubSectRITable extends CompoundFSSPlots {
		
		private double[] minMags = { 0d, 7d };
		
		private double[] fractiles = {0.025, 0.16, 0.84, 0.975};

		private transient BranchWeightProvider weightProvider;
		
		// FM, minMag, results by solution
		private Table<FaultModels, Double, List<double[]>> results;
		private Map<FaultModels, List<EvenlyDiscretizedFunc[]>> mfdResults;
		private Map<FaultModels, List<Double>> weights;
		private Map<FaultModels, List<FaultSectionPrefData>> fmSectsMap;
		
		private Table<FaultModels, Double, CSVFile<String>> csvTable;
		private Map<FaultModels, CSVFile<String>> mfdCSVs;
		
		// for MFDs
		private static final double minX = 6.05d;
		private static final double maxX = 9.05d;
		private static final double delta = 0.1d;
		private static final int num = (int) ((maxX - minX) / delta + 1);
		
		public SubSectRITable(BranchWeightProvider weightProvider) {
			this.weightProvider = weightProvider;
			
			results = HashBasedTable.create();
			mfdResults = Maps.newHashMap();
			weights = Maps.newHashMap();
			fmSectsMap = Maps.newHashMap();
		}

		@Override
		protected void processSolution(LogicTreeBranch branch, InversionFaultSystemSolution sol, int solIndex) {
			FaultModels fm = branch.getValue(FaultModels.class);
			
			synchronized (results) {
				if (!results.containsRow(fm)) {
					for (double minMag : minMags)
						results.put(fm, minMag, new ArrayList<double[]>());
					mfdResults.put(fm, new ArrayList<EvenlyDiscretizedFunc[]>());
					weights.put(fm, new ArrayList<Double>());
					fmSectsMap.put(fm, sol.getRupSet().getFaultSectionDataList());
				}
			}
			
			List<double[]> myRIs = Lists.newArrayList();
			
			// mag specific results
			for (double minMag : minMags) {
				double[] rates = sol.calcParticRateForAllSects(minMag, Double.POSITIVE_INFINITY);
				double[] ris = new double[rates.length];
				for (int i=0; i<rates.length; i++)
					ris[i] = 1d/rates[i];
				myRIs.add(ris);
			}
			
			// MFDs
			EvenlyDiscretizedFunc[] mfds = new EvenlyDiscretizedFunc[sol.getRupSet().getNumSections()];
			for (int s=0; s<mfds.length; s++) {
				IncrementalMagFreqDist mfd = sol.calcParticipationMFD_forSect(s, minX, maxX, num);
				mfds[s] = mfd.getCumRateDistWithOffset();
			}
			
			synchronized (results) {
				for (int i=0; i<minMags.length; i++) {
					double minMag = minMags[i];
					results.get(fm, minMag).add(myRIs.get(i));
				}
				mfdResults.get(fm).add(mfds);
				weights.get(fm).add(weightProvider.getWeight(branch));
			}
		}

		@Override
		protected void combineDistributedCalcs(Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				SubSectRITable o = (SubSectRITable) otherCalc;
				for (FaultModels fm : o.results.rowKeySet()) {
					if (!results.containsRow(fm)) {
						for (double minMag : minMags)
							results.put(fm, minMag, new ArrayList<double[]>());
						mfdResults.put(fm, new ArrayList<EvenlyDiscretizedFunc[]>());
						weights.put(fm, new ArrayList<Double>());
						fmSectsMap.put(fm, o.fmSectsMap.get(fm));
					}
					
					for (double minMag : minMags)
						results.get(fm, minMag).addAll(o.results.get(fm, minMag));
					mfdResults.get(fm).addAll(o.mfdResults.get(fm));
					weights.get(fm).addAll(o.weights.get(fm));
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			csvTable = HashBasedTable.create();
			mfdCSVs = Maps.newHashMap();
			
			for (FaultModels fm : results.rowKeySet()) {
				Map<Double, List<double[]>> fmResults = results.row(fm);
				List<Double> fmWeights = weights.get(fm);
				
				int numSects = fmResults.get(minMags[0]).get(0).length;
				
				List<FaultSectionPrefData> subSects = fmSectsMap.get(fm);
				Preconditions.checkState(numSects == subSects.size());
				
				// mag specific results
				for (double minMag : minMags) {
					List<double[]> ris = fmResults.get(minMag);
					
					Preconditions.checkState(ris.size() == fmWeights.size(),
							"Size mismatch. %s results, %s weights", fmResults.size(), fmWeights.size());
					
					CSVFile<String> csv = new CSVFile<String>(true);
					csvTable.put(fm, minMag, csv);
					List<String> header = Lists.newArrayList("Subsection Index", "Parent Section ID", "Subsection Name"
							,"Mean RI", "Min RI", "Max RI", "Std. Dev");
					for (double fractile : fractiles) {
						float p = (float)(fractile*100d);
						header.add("p"+p);
					}
					csv.addLine(header);
					
					for (int s=0; s<numSects; s++) {
						ArbDiscrEmpiricalDistFunc dist = new ArbDiscrEmpiricalDistFunc();
						for (int i=0; i<ris.size(); i++)
							dist.set(ris.get(i)[s], fmWeights.get(i)); // this actually adds if already present
						
						FaultSectionPrefData sect = subSects.get(s);
						
						List<String> line = Lists.newArrayList(sect.getSectionId()+"", sect.getParentSectionId()+"",
								sect.getSectionName());
						line.add(dist.getMean()+"");
						line.add(dist.getMinX()+"");
						line.add(dist.getMaxX()+"");
						line.add(dist.getStdDev()+"");
						for (double fractile : fractiles)
							line.add(dist.getDiscreteFractile(fractile)+"");
						csv.addLine(line);
					}
				}
				
				// MFDs
				CSVFile<String> csv = new CSVFile<String>(true);
				List<String> header = Lists.newArrayList("Subsection Index", "Parent Section ID", "Subsection Name");
				List<EvenlyDiscretizedFunc[]> solMFDs = mfdResults.get(fm);
				EvenlyDiscretizedFunc testFunc = solMFDs.get(0)[0];
				for (int i=0; i<testFunc.size(); i++)
					header.add((float)testFunc.getX(i)+"");
				csv.addLine(header);
				
				for (int s=0; s<subSects.size(); s++) {
					XY_DataSetList sectMFDs = new XY_DataSetList();
					for (int i=0; i<solMFDs.size(); i++)
						sectMFDs.add(solMFDs.get(i)[s]);
					DiscretizedFunc meanMFD = getFractiles(sectMFDs, fmWeights, "", new double[] {}).get(0);
					FaultSectionPrefData sect = subSects.get(s);
					List<String> line = Lists.newArrayList(sect.getSectionId()+"", sect.getParentSectionId()+"",
							sect.getSectionName());
					for (int i=0; i<meanMFD.size(); i++)
						line.add(meanMFD.getY(i)+"");
					csv.addLine(line);
				}
				mfdCSVs.put(fm, csv);
			}
		}
		
	}

	public static void writeMiniSectRITables(FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, File dir, String prefix)
			throws IOException {
		MiniSectRIPlot plot = new MiniSectRIPlot(weightProvider);
		plot.buildPlot(fetch);

		writeMiniSectRITables(plot, dir, prefix);
	}

	public static void writeMiniSectRITables(MiniSectRIPlot plot, File dir,
			String prefix) throws IOException {
		System.out.println("Making mini sect RI plot!");

		for (FaultModels fm : plot.solRatesMap.keySet()) {
			Map<Integer, DeformationSection> dm = plot.loadDM(fm);

			for (int i = 0; i < plot.minMags.length; i++) {
				File file = new File(dir, prefix + "_" + fm.getShortName()
						+ "_mini_sect_RIs_" + (float) plot.minMags[i] + "+.csv");
				MiniSectRecurrenceGen.writeRates(file, dm, plot.avgRatesMap
						.get(fm).get(i));
			}
		}
	}

	/**
	 * This plot creates a CSV file with recurrence intervals on each minisection.
	 * @author kevin
	 *
	 */
	public static class MiniSectRIPlot extends CompoundFSSPlots {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		private double[] minMags = { 6.7d };

		private transient BranchWeightProvider weightProvider;

		private transient ConcurrentMap<FaultModels, Map<Integer, List<List<Integer>>>> fmMappingsMap = Maps
				.newConcurrentMap();

		private Map<FaultModels, List<List<Map<Integer, List<Double>>>>> solRatesMap = Maps
				.newHashMap();
		private Map<FaultModels, List<Double>> weightsMap = Maps.newHashMap();

		private Map<FaultModels, List<Map<Integer, List<Double>>>> avgRatesMap = Maps
				.newHashMap();

		public MiniSectRIPlot(BranchWeightProvider weightProvider) {
			this.weightProvider = weightProvider;
		}

		private transient Map<FaultModels, Map<Integer, DeformationSection>> fmDMsMap = Maps
				.newHashMap();

		private synchronized Map<Integer, DeformationSection> loadDM(
				FaultModels fm) {
			Map<Integer, DeformationSection> dm = fmDMsMap.get(fm);
			if (dm == null) {
				try {
					dm = DeformationModelFileParser
							.load(DeformationModels.GEOLOGIC.getDataFileURL(fm));
				} catch (IOException e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
				fmDMsMap.put(fm, dm);
				List<List<Map<Integer, List<Double>>>> solRates = Lists
						.newArrayList();
				for (int i = 0; i < minMags.length; i++)
					solRates.add(new ArrayList<Map<Integer, List<Double>>>());
				List<Double> weights = Lists.newArrayList();
				solRatesMap.put(fm, solRates);
				weightsMap.put(fm, weights);
			}
			return dm;
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			FaultModels fm = sol.getRupSet().getFaultModel();

			Map<Integer, DeformationSection> dm = loadDM(fm);

			debug(solIndex, "cache fetching");
			// mapping from parent ID to minisection list for each subsection
			Map<Integer, List<List<Integer>>> mappings = fmMappingsMap.get(fm);
			if (mappings == null) {
				synchronized (this) {
					mappings = fmMappingsMap.get(fm);
					if (mappings == null) {
						mappings = MiniSectRecurrenceGen.buildSubSectMappings(
								dm, sol.getRupSet().getFaultSectionDataList());
						fmMappingsMap.putIfAbsent(fm, mappings);
					}
				}
			}

			double weight = weightProvider.getWeight(branch);

			debug(solIndex, "calculating");
			List<Map<Integer, List<Double>>> myRates = Lists.newArrayList();
			for (int i = 0; i < minMags.length; i++) {
				// calculate the minisection participation rates
				myRates.add(MiniSectRecurrenceGen.calcMinisectionParticRates(
						sol, mappings, minMags[i], false));
			}
			debug(solIndex, "archiving");
			synchronized (this) {
				// store results
				for (int i = 0; i < minMags.length; i++) {
					solRatesMap.get(fm).get(i).add(myRates.get(i));
				}
				weightsMap.get(fm).add(weight);
			}
			debug(solIndex, "done");
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				MiniSectRIPlot o = (MiniSectRIPlot) otherCalc;
				for (FaultModels fm : o.solRatesMap.keySet()) {
					if (!solRatesMap.containsKey(fm)) {
						List<List<Map<Integer, List<Double>>>> solRates = Lists
								.newArrayList();
						List<Double> weights = Lists.newArrayList();
						solRatesMap.put(fm, solRates);
						weightsMap.put(fm, weights);
					}
					for (int i = 0; i < minMags.length; i++) {
						solRatesMap.get(fm).get(i)
								.addAll(o.solRatesMap.get(fm).get(i));
					}
					weightsMap.get(fm).addAll(o.weightsMap.get(fm));
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			// this combines the branches into averages for each fault model and min mag
			for (int i = 0; i < minMags.length; i++) {
				for (FaultModels fm : solRatesMap.keySet()) {
					List<Map<Integer, List<Double>>> avgRatesList = avgRatesMap
							.get(fm);
					if (!avgRatesMap.containsKey(fm)) {
						avgRatesList = Lists.newArrayList();
						avgRatesMap.put(fm, avgRatesList);
					}
					List<Map<Integer, List<Double>>> solRates = solRatesMap
							.get(fm).get(i);
					double[] weights = Doubles.toArray(weightsMap.get(fm));
					Set<Integer> parents = solRates.get(0).keySet();
					Map<Integer, List<Double>> avg = Maps.newHashMap();

					for (Integer parentID : parents) {
						List<double[]> solRatesList = Lists.newArrayList();
						int numMinis = solRates.get(0).get(parentID).size();
						for (int m = 0; m < numMinis; m++)
							solRatesList.add(new double[solRates.size()]);
						for (int s = 0; s < solRates.size(); s++) {
							List<Double> rates = solRates.get(s).get(parentID);
							for (int m = 0; m < rates.size(); m++)
								solRatesList.get(m)[s] = rates.get(m);
						}

						List<Double> avgRates = Lists.newArrayList();

						for (int m = 0; m < numMinis; m++) {
							double avgRate = FaultSystemSolutionFetcher
									.calcScaledAverage(solRatesList.get(m),
											weights);
							double ri = 1d / avgRate;
							avgRates.add(ri);
						}
						avg.put(parentID, avgRates);
					}
					avgRatesList.add(avg);
				}
			}
		}

	}

	public static void writeMisfitTables(FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, File dir, String prefix)
			throws IOException {
		MisfitTable plot = new MisfitTable();
		plot.buildPlot(fetch);

		writeMisfitTables(plot, dir, prefix);
	}

	public static void writeMisfitTables(MisfitTable plot, File dir,
			String prefix) throws IOException {
		System.out.println("Making mini sect RI plot!");

		BatchPlotGen.writeMisfitsCSV(dir, prefix, plot.misfitsMap);
	}

	/**
	 * This creates a CSV file with simulated annealing misfits and energies from each logic tree branch
	 * 
	 * @author kevin
	 *
	 */
	public static class MisfitTable extends CompoundFSSPlots {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		private ConcurrentMap<VariableLogicTreeBranch, Map<String, Double>> misfitsMap = Maps
				.newConcurrentMap();

		public MisfitTable() {

		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			VariableLogicTreeBranch vbr = new VariableLogicTreeBranch(branch,
					null);

			debug(solIndex, "calc/archiving");
			// very simple, just get the misfits from the solution which have already been loaded in
			// from the inversion metadata
			misfitsMap.putIfAbsent(vbr, sol.getMisfits());
			debug(solIndex, "done");
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				MisfitTable o = (MisfitTable) otherCalc;
				misfitsMap.putAll(o.misfitsMap);
			}
		}

		@Override
		protected void doFinalizePlot() {

		}

	}

	public static void writePaleoRatesTables(FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, File dir, String prefix)
			throws IOException {
		PaleoRatesTable plot = new PaleoRatesTable(weightProvider);
		plot.buildPlot(fetch);

		writePaleoRatesTables(plot, dir, prefix);
	}

	public static void writePaleoRatesTables(PaleoRatesTable plot, File dir,
			String prefix) throws IOException {
		System.out.println("Making paleo/ave slip tables!");

		File subDir = new File(dir, "paleo_fault_based");
		if (!subDir.exists())
			subDir.mkdir();
		
		for (FaultModels fm : plot.aveSlipCSVOutputMap.keySet()) {
			File aveSlipFile = new File(subDir, fm.getShortName()
					+ "_ave_slip_rates.csv");
			plot.aveSlipCSVOutputMap.get(fm).writeToFile(aveSlipFile);
			File paleoFile = new File(subDir, fm.getShortName()
					+ "_paleo_rates.csv");
			plot.paleoCSVOutputMap.get(fm).writeToFile(paleoFile);
		}
		
		plot.carrizoCSV.writeToFile(new File(subDir, "carrizo_paleo_obs_rates.csv"));
	}

	/**
	 * This creates CSV files with paleo and average slip rates at each site, along with confidence vals and
	 * min/max amoung logic tree branches.
	 * @author kevin
	 *
	 */
	public static class PaleoRatesTable extends CompoundFSSPlots {

		private transient BranchWeightProvider weightProvider;
		private transient PaleoProbabilityModel paleoProbModel;

		private ConcurrentMap<FaultModels, List<PaleoRateConstraint>> paleoConstraintsMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, List<AveSlipConstraint>> aveSlipConstraintsMap = Maps
				.newConcurrentMap();
		private transient ConcurrentMap<FaultModels, ConcurrentMap<Integer, List<Integer>>> rupsForSectsMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, List<double[]>> reducedSlipsMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, List<double[]>> proxyAveSlipRatesMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, List<double[]>> aveSlipObsRatesMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, List<double[]>> paleoObsRatesMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, List<Double>> carrizoPaleoObsRatesMap = Maps
				.newConcurrentMap();

		private ConcurrentMap<FaultModels, List<Double>> weightsMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, List<LogicTreeBranch>> branchesMap = Maps
				.newConcurrentMap();

		private transient Map<FaultModels, CSVFile<String>> aveSlipCSVOutputMap = Maps
				.newHashMap();
		private transient Map<FaultModels, CSVFile<String>> paleoCSVOutputMap = Maps
				.newHashMap();
		private transient CSVFile<String> carrizoCSV;

		public PaleoRatesTable(BranchWeightProvider weightProvider) {
			this.weightProvider = weightProvider;

			try {
				paleoProbModel = UCERF3_PaleoProbabilityModel.load();
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			InversionFaultSystemRupSet rupSet = sol.getRupSet();
			FaultModels fm = rupSet.getFaultModel();

			debug(solIndex, "cache fetching");
			List<AveSlipConstraint> aveSlipConstraints = aveSlipConstraintsMap
					.get(fm);
			if (aveSlipConstraints == null) {
				// load in constraints
				synchronized (this) {
					aveSlipConstraints = aveSlipConstraintsMap.get(fm);
					List<PaleoRateConstraint> paleoConstraints = null;
					if (aveSlipConstraints == null) {
						try {
							aveSlipConstraints = AveSlipConstraint.load(rupSet
									.getFaultSectionDataList());
							paleoConstraints = UCERF3_PaleoRateConstraintFetcher
									.getConstraints(rupSet.getFaultSectionDataList());
						} catch (IOException e) {
							ExceptionUtils.throwAsRuntimeException(e);
						}
						paleoConstraintsMap.putIfAbsent(fm, paleoConstraints);
						ConcurrentMap<Integer, List<Integer>> rupsForSectsLists = Maps
								.newConcurrentMap();
						for (AveSlipConstraint constr : aveSlipConstraints)
							rupsForSectsLists.putIfAbsent(constr
									.getSubSectionIndex(), rupSet.getRupturesForSection(
											constr.getSubSectionIndex()));
						for (PaleoRateConstraint constr : paleoConstraints)
							rupsForSectsLists.putIfAbsent(constr.getSectionIndex(),
									rupSet.getRupturesForSection(constr.getSectionIndex()));
						rupsForSectsMap.putIfAbsent(fm, rupsForSectsLists);
						List<double[]> slipsList = Lists.newArrayList();
						reducedSlipsMap.putIfAbsent(fm, slipsList);
						List<double[]> proxyRatesList = Lists.newArrayList();
						proxyAveSlipRatesMap.putIfAbsent(fm, proxyRatesList);
						List<double[]> obsRatesList = Lists.newArrayList();
						aveSlipObsRatesMap.putIfAbsent(fm, obsRatesList);
						List<double[]> paleoObsRatesList = Lists.newArrayList();
						paleoObsRatesMap.putIfAbsent(fm, paleoObsRatesList);
						List<Double> carrizoList = Lists.newArrayList();
						carrizoPaleoObsRatesMap.putIfAbsent(fm, carrizoList);
						List<Double> weightsList = Lists.newArrayList();
						weightsMap.putIfAbsent(fm, weightsList);
						List<LogicTreeBranch> branchesList = Lists.newArrayList();
						branchesMap.putIfAbsent(fm, branchesList);

						// must be last
						aveSlipConstraintsMap.putIfAbsent(fm,
								aveSlipConstraints);
					}
				}
			}

			// get data for ave slips at each site
			double[] slips = new double[aveSlipConstraints.size()];
			double[] proxyRates = new double[aveSlipConstraints.size()];
			double[] obsRates = new double[aveSlipConstraints.size()];

			Map<Integer, List<Integer>> rupsForSectsLists = rupsForSectsMap
					.get(fm);

			debug(solIndex, "calculating ave slip");

			for (int i = 0; i < aveSlipConstraints.size(); i++) {
				AveSlipConstraint constr = aveSlipConstraints.get(i);
				int subsectionIndex = constr.getSubSectionIndex();

				slips[i] = rupSet.getSlipRateForSection(subsectionIndex);
				proxyRates[i] = slips[i] / constr.getWeightedMean();
				double obsRate = 0d;
				for (int rupID : rupsForSectsLists.get(constr
						.getSubSectionIndex())) {
					int sectIndexInRup = rupSet.getSectionsIndicesForRup(rupID)
							.indexOf(subsectionIndex);
					double slipOnSect = rupSet.getSlipOnSectionsForRup(rupID)[sectIndexInRup];
					double probVisible = AveSlipConstraint
							.getProbabilityOfObservedSlip(slipOnSect);
					obsRate += sol.getRateForRup(rupID) * probVisible;
				}
				obsRates[i] = obsRate;
			}

			List<PaleoRateConstraint> paleoConstraints = paleoConstraintsMap
					.get(fm);

			// get data for paleo constraints at each site
			debug(solIndex, "calculating paleo rates");
			double[] paleoRates = new double[paleoConstraints.size()];
			double carrizoRate = 0d;
			for (int i = 0; i < paleoConstraints.size(); i++) {
				PaleoRateConstraint constr = paleoConstraints.get(i);

				double obsRate = 0d;
				for (int rupID : rupsForSectsLists
						.get(constr.getSectionIndex())) {
					obsRate += sol.getRateForRup(rupID)
							* paleoProbModel.getProbPaleoVisible(rupSet, rupID,
									constr.getSectionIndex());
				}
				if (sol.getRupSet().getFaultSectionData(constr.getSectionIndex())
						.getParentSectionId() == 300) // carrizo
					carrizoRate = obsRate;
				paleoRates[i] = obsRate;
			}

			debug(solIndex, "archiving");
			synchronized (this) {
				// store it
				weightsMap.get(fm).add(weightProvider.getWeight(branch));
				branchesMap.get(fm).add(branch);
				reducedSlipsMap.get(fm).add(slips);
				proxyAveSlipRatesMap.get(fm).add(proxyRates);
				aveSlipObsRatesMap.get(fm).add(obsRates);
				paleoObsRatesMap.get(fm).add(paleoRates);
				carrizoPaleoObsRatesMap.get(fm).add(carrizoRate);
			}
			debug(solIndex, "done");
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				PaleoRatesTable o = (PaleoRatesTable) otherCalc;

				for (FaultModels fm : o.weightsMap.keySet()) {
					if (!weightsMap.containsKey(fm)) {
						weightsMap.put(fm, new ArrayList<Double>());
						branchesMap.put(fm, new ArrayList<LogicTreeBranch>());
						aveSlipConstraintsMap.put(fm,
								o.aveSlipConstraintsMap.get(fm));
						paleoConstraintsMap.put(fm,
								o.paleoConstraintsMap.get(fm));
						reducedSlipsMap.put(fm, new ArrayList<double[]>());
						proxyAveSlipRatesMap.put(fm, new ArrayList<double[]>());
						aveSlipObsRatesMap.put(fm, new ArrayList<double[]>());
						paleoObsRatesMap.put(fm, new ArrayList<double[]>());
						carrizoPaleoObsRatesMap.put(fm, new ArrayList<Double>());
					}

					weightsMap.get(fm).addAll(o.weightsMap.get(fm));
					branchesMap.get(fm).addAll(o.branchesMap.get(fm));
					reducedSlipsMap.get(fm).addAll(o.reducedSlipsMap.get(fm));
					proxyAveSlipRatesMap.get(fm).addAll(
							o.proxyAveSlipRatesMap.get(fm));
					aveSlipObsRatesMap.get(fm).addAll(
							o.aveSlipObsRatesMap.get(fm));
					paleoObsRatesMap.get(fm).addAll(o.paleoObsRatesMap.get(fm));
					carrizoPaleoObsRatesMap.get(fm).addAll(o.carrizoPaleoObsRatesMap.get(fm));
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			// this builds the CSV files and also calculates UCERF2 comparisons.
			
			InversionFaultSystemSolution ucerf2Sol = UCERF2_ComparisonSolutionFetcher
					.getUCERF2Solution(FaultModels.FM2_1);
			List<AveSlipConstraint> ucerf2AveSlipConstraints;
			List<PaleoRateConstraint> ucerf2PaleoConstraints;
			try {
				ucerf2AveSlipConstraints = AveSlipConstraint.load(
						ucerf2Sol.getRupSet().getFaultSectionDataList());
				ucerf2PaleoConstraints = UCERF3_PaleoRateConstraintFetcher
						.getConstraints(ucerf2Sol.getRupSet().getFaultSectionDataList());
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}

			// ave slip table
			for (FaultModels fm : weightsMap.keySet()) {
				CSVFile<String> csv = new CSVFile<String>(true);

				List<String> header = Lists.newArrayList(fm.getShortName()
						+ " Mapping", "Latitude", "Longitude",
						"Weighted Mean Slip", "UCERF2 Reduced Slip Rate",
						"UCERF2 Proxy Event Rate",
						"UCERF3 Mean Reduced Slip Rate",
						"UCERF3 Mean Proxy Event Rate",
						"UCERF3 Mean Paleo Visible Rate",
						"UCERF3 Min Paleo Visible Rate",
						"UCERF3 Max Paleo Visible Rate");

				csv.addLine(header);

				List<AveSlipConstraint> constraints = aveSlipConstraintsMap
						.get(fm);

				for (int i = 0; i < constraints.size(); i++) {
					AveSlipConstraint constr = constraints.get(i);

					// find matching UCERF2 constraint
					AveSlipConstraint ucerf2Constraint = null;
					for (AveSlipConstraint u2Constr : ucerf2AveSlipConstraints) {
						if (u2Constr.getSiteLocation().equals(
								constr.getSiteLocation())) {
							ucerf2Constraint = u2Constr;
							break;
						}
					}

					List<String> line = Lists.newArrayList();
					line.add(constr.getSubSectionName());
					line.add(constr.getSiteLocation().getLatitude() + "");
					line.add(constr.getSiteLocation().getLongitude() + "");
					line.add(constr.getWeightedMean() + "");
					if (ucerf2Constraint == null) {
						line.add("");
						line.add("");
					} else {
						double ucerf2SlipRate = ucerf2Sol.getRupSet().getSlipRateForSection(
								ucerf2Constraint.getSubSectionIndex());
						line.add(ucerf2SlipRate + "");
						double ucerf2ProxyRate = ucerf2SlipRate
								/ constr.getWeightedMean();
						line.add(ucerf2ProxyRate + "");
					}
					List<double[]> reducedSlipList = reducedSlipsMap.get(fm);
					List<double[]> proxyRatesList = proxyAveSlipRatesMap
							.get(fm);
					List<double[]> obsRatesList = aveSlipObsRatesMap.get(fm);

					int numSols = reducedSlipList.size();
					double[] slips = new double[numSols];
					double[] proxyRates = new double[numSols];
					double[] rates = new double[numSols];
					double[] weigths = Doubles.toArray(weightsMap.get(fm));

					for (int j = 0; j < numSols; j++) {
						slips[j] = reducedSlipList.get(j)[i];
						proxyRates[j] = proxyRatesList.get(j)[i];
						rates[j] = obsRatesList.get(j)[i];
					}

					line.add(FaultSystemSolutionFetcher.calcScaledAverage(
							slips, weigths) + "");
					line.add(FaultSystemSolutionFetcher.calcScaledAverage(
							proxyRates, weigths) + "");
					line.add(FaultSystemSolutionFetcher.calcScaledAverage(
							rates, weigths) + "");
					line.add(StatUtils.min(rates) + "");
					line.add(StatUtils.max(rates) + "");

					csv.addLine(line);
				}

				aveSlipCSVOutputMap.put(fm, csv);
			}

			// paleo table
			for (FaultModels fm : weightsMap.keySet()) {
				CSVFile<String> csv = new CSVFile<String>(true);

				List<String> header = Lists.newArrayList(fm.getShortName()
						+ " Mapping", "Latitude", "Longitude",
						"Paleo Observed Rate", "Paleo Observed Lower Bound",
						"Paleo Observed Upper Bound",
						"UCERF2 Paleo Visible Rate",
						"UCERF3 Mean Paleo Visible Rate",
						"UCERF3 Min Paleo Visible Rate",
						"UCERF3 Max Paleo Visible Rate");

				csv.addLine(header);

				List<PaleoRateConstraint> constraints = paleoConstraintsMap
						.get(fm);

				for (int i = 0; i < constraints.size(); i++) {
					PaleoRateConstraint constr = constraints.get(i);

					// find matching UCERF2 constraint
					PaleoRateConstraint ucerf2Constraint = null;
					for (PaleoRateConstraint u2Constr : ucerf2PaleoConstraints) {
						if (u2Constr.getPaleoSiteLoction().equals(
								constr.getPaleoSiteLoction())) {
							ucerf2Constraint = u2Constr;
							break;
						}
					}

					List<String> line = Lists.newArrayList();
					line.add(constr.getFaultSectionName());
					line.add(constr.getPaleoSiteLoction().getLatitude() + "");
					line.add(constr.getPaleoSiteLoction().getLongitude() + "");
					line.add(constr.getMeanRate() + "");
					line.add(constr.getLower95ConfOfRate() + "");
					line.add(constr.getUpper95ConfOfRate() + "");
					if (ucerf2Constraint == null) {
						line.add("");
					} else {
						line.add(PaleoFitPlotter.getPaleoRateForSect(ucerf2Sol,
								ucerf2Constraint.getSectionIndex(),
								paleoProbModel)
								+ "");
					}
					List<double[]> obsRatesList = paleoObsRatesMap.get(fm);

					int numSols = obsRatesList.size();
					double[] rates = new double[numSols];
					double[] weigths = Doubles.toArray(weightsMap.get(fm));

					for (int j = 0; j < numSols; j++)
						rates[j] = obsRatesList.get(j)[i];

					line.add(FaultSystemSolutionFetcher.calcScaledAverage(
							rates, weigths) + "");
					line.add(StatUtils.min(rates) + "");
					line.add(StatUtils.max(rates) + "");

					csv.addLine(line);
				}

				paleoCSVOutputMap.put(fm, csv);
			}
			
			// Carriso table
			carrizoCSV = new CSVFile<String>(true);
			List<String> header = Lists.newArrayList();
			for (Class<? extends LogicTreeBranchNode<?>> clazz : LogicTreeBranch.getLogicTreeNodeClasses())
				header.add(ClassUtils.getClassNameWithoutPackage(clazz));
			header.add("A Priori Branch Weight");
			header.add("Carrizo Paleo Observable Rate");
			carrizoCSV.addLine(header);
			
			double totWt = 0;
			for (List<Double> weights : weightsMap.values())
				for (double weight : weights)
					totWt += weight;
			
			for (FaultModels fm : carrizoPaleoObsRatesMap.keySet()) {
				List<LogicTreeBranch> branches = branchesMap.get(fm);
				List<Double> weights = weightsMap.get(fm);
				List<Double> rates = carrizoPaleoObsRatesMap.get(fm);
				
				for (int i=0; i<branches.size(); i++) {
					List<String> line = Lists.newArrayList();
					LogicTreeBranch branch = branches.get(i);
					for (int j=0; j<LogicTreeBranch.getLogicTreeNodeClasses().size(); j++)
						line.add(branch.getValue(j).getShortName());
					double weight = weights.get(i);
					line.add((weight / totWt)+"");
					line.add(rates.get(i)+"");
					carrizoCSV.addLine(line);
				}
			}
		}

	}

	public static void writeMeanSolutions(FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, File dir, String prefix)
			throws IOException {
		BranchAvgFSSBuilder plot = new BranchAvgFSSBuilder(weightProvider);
		plot.buildPlot(fetch);

		writeMeanSolutions(plot, dir, prefix);
	}

	public static void writeMeanSolutions(BranchAvgFSSBuilder plot, File dir,
			String prefix) throws IOException {
		System.out.println("Making mean solutions!");
		
		LaughTestFilter laughTest = LaughTestFilter.getDefault();
		
		GriddedRegion region = plot.region;

		for (FaultModels fm : plot.weightsMap.keySet()) {
			String myPrefix = prefix;
			LogicTreeBranch runningBranch = plot.runningBranches.get(fm);
			for (int i=0; i<runningBranch.size(); i++) {
				LogicTreeBranchNode<?> val = runningBranch.getValue(i);
				if (val != null && val.getRelativeWeight(runningBranch.getValue(InversionModels.class)) < 1d) {
					if (!myPrefix.isEmpty())
						myPrefix += "_";
					myPrefix += val.encodeChoiceString();
				}
			}
//			if (multiFM)
//				myPrefix += "_"+fm.getShortName();
			if (plot.solIndex >= 0) {
				if (!myPrefix.isEmpty())
					myPrefix += "_";
				myPrefix += "run"+plot.solIndex;
			}
			File outputFile = new File(dir, myPrefix+"_MEAN_BRANCH_AVG_SOL.zip");
			
			double[] rates = plot.ratesMap.get(fm);
			double[] mags = plot.magsMap.get(fm);
			
			if (rates.length == 229104 || rates.length == 249656) {
				System.err.println("WARNING: Using UCERF3.2 laugh test filter!");
				laughTest = LaughTestFilter.getUCERF3p2Filter();
				DeformationModelFetcher.IMPERIAL_DDW_HACK = true;
			}
			
			DeformationModels dm;
			if (plot.defModelsMap.get(fm).size() == 1)
				// for single dm, use that one
				dm = plot.defModelsMap.get(fm).iterator().next();
			else
				// mutiple dms, use mean
				dm = DeformationModels.MEAN_UCERF3;
			
			InversionFaultSystemRupSet reference = InversionFaultSystemRupSetFactory.forBranch(laughTest,
					InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE, LogicTreeBranch.getMEAN_UCERF3(fm, dm));
			
			String info = reference.getInfoString();
			
			info = "****** BRANCH AVERAGED SOLUTION FOR "+plot.weightsMap.get(fm).size()+" SOLUTIONS ******\n\n";
			
			info += "****** Logic Tree Branch ******";
			for (int i=0; i<runningBranch.size(); i++) {
				LogicTreeBranchNode<?> node = runningBranch.getValue(i);
				info += "\n"+ClassUtils.getClassNameWithoutPackage(LogicTreeBranch.getEnumEnclosingClass(
						LogicTreeBranch.getLogicTreeNodeClasses().get(i)))+": ";
				if (node == null)
					info += "(multiple)";
				else
					info += node.name();
			}
			info += "\n*******************************";
			
			List<List<Integer>> clusterRups = Lists.newArrayList();
			List<List<Integer>> clusterSects = Lists.newArrayList();
			for (int i=0; i<reference.getNumClusters(); i++) {
				clusterRups.add(reference.getRupturesForCluster(i));
				clusterSects.add(reference.getSectionsForCluster(i));
			}
			
			// first build the rup set
			
			// old version that was an IFSS
//			InversionFaultSystemRupSet rupSet = new InversionFaultSystemRupSet(
//					reference, reference.getLogicTreeBranch(), laughTest,
//					reference.getAveSlipForAllRups(), reference.getCloseSectionsListList(),
//					reference.getRupturesForClusters(), reference.getSectionsForClusters());
//			rupSet.setMagForallRups(mags);
			
			FaultSystemRupSet rupSet = new FaultSystemRupSet(reference.getFaultSectionDataList(),
					reference.getSlipRateForAllSections(), reference.getSlipRateStdDevForAllSections(),
					reference.getAreaForAllSections(), reference.getSectionIndicesForAllRups(), mags,
					reference.getAveRakeForAllRups(), reference.getAreaForAllRups(), reference.getLengthForAllRups(), info);
			
			GridSourceProvider gridSources = new GridSourceFileReader(region,
					plot.nodeSubSeisMFDsMap.get(fm), plot.nodeUnassociatedMFDsMap.get(fm));
//			InversionFaultSystemSolution sol = new InversionFaultSystemSolution(rupSet, rates);
			FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates, plot.subSeisMFDsMap.get(fm));
			sol.setGridSourceProvider(gridSources);
			
			FaultSystemIO.writeSol(sol, outputFile);
			
			DeformationModelFetcher.IMPERIAL_DDW_HACK = false;
		}
	}
	
	/**
	 * This builds a branch averaged fault system solution that can be used for approximate hazard calculations
	 * and some plots.
	 * @author kevin
	 *
	 */
	public static class BranchAvgFSSBuilder extends CompoundFSSPlots {
		
		private transient BranchWeightProvider weightProvider;
		
		private GriddedRegion region;
		private Map<FaultModels, Map<Integer, IncrementalMagFreqDist>> nodeSubSeisMFDsMap = Maps.newHashMap();
		private Map<FaultModels, Map<Integer, IncrementalMagFreqDist>> nodeUnassociatedMFDsMap = Maps.newHashMap();
		
		private Map<FaultModels, double[]> ratesMap = Maps.newConcurrentMap();
		private Map<FaultModels, double[]> magsMap = Maps.newConcurrentMap();
		private Map<FaultModels, List<Double>> weightsMap = Maps.newConcurrentMap();
		private Map<FaultModels, LogicTreeBranch> runningBranches = Maps.newConcurrentMap();
		
		private Map<FaultModels, List<IncrementalMagFreqDist>> subSeisMFDsMap = Maps.newConcurrentMap();
		
		private Map<FaultModels, HashSet<DeformationModels>> defModelsMap = Maps.newHashMap();
		
		// should only be used for old solutions
		private Map<FaultModels, InversionFaultSystemRupSet> rupSetCache = Maps.newHashMap();
		
		// if specified, this index will be used to grab rates and such from individual soltions
		// in an AFSS
		private int solIndex = -1;
		
		public BranchAvgFSSBuilder(BranchWeightProvider weightProvider) {
			this.weightProvider = weightProvider;
		}

		public int getSolIndex() {
			return solIndex;
		}

		public void setSolIndex(int solIndex) {
			this.solIndex = solIndex;
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			if (this.solIndex >= 0) {
				// we can build means from individual runs if specified
				
				Preconditions.checkState(sol instanceof AverageFaultSystemSolution,
						"Sol index supplied but branch isn't an average!");
				AverageFaultSystemSolution avgSol = (AverageFaultSystemSolution)sol;
				Preconditions.checkState(avgSol.getNumSolutions() > this.solIndex,
						"Sol index="+this.solIndex+" but avg sol has "+avgSol.getNumSolutions()+" sols");
				sol = avgSol.getSolution(this.solIndex);
			}
			
			InversionFaultSystemRupSet rupSet = sol.getRupSet();
			FaultModels fm = rupSet.getFaultModel();
			
			int numRups = rupSet.getNumRuptures();
			
			double weight = weightProvider.getWeight(branch);
			
			// get gridded seis data
			GridSourceProvider gridSources = sol.getGridSourceProvider();
			
			Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = Maps.newHashMap();
			Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = Maps.newHashMap();
			
			if (region == null)
				region = gridSources.getGriddedRegion();
			
			for (int i=0; i<region.getNumLocations(); i++) {
				nodeSubSeisMFDs.put(i, gridSources.getNodeSubSeisMFD(i));
				nodeUnassociatedMFDs.put(i, gridSources.getNodeUnassociatedMFD(i));
			}
			List<? extends IncrementalMagFreqDist> subSeisMFDs = sol.getFinalSubSeismoOnFaultMFD_List();
			
			synchronized (fm) {
				List<Double> weightsList = weightsMap.get(fm);
				if (weightsList == null) {
					weightsList = Lists.newArrayList();
					weightsMap.put(fm, weightsList);
					
					nodeSubSeisMFDsMap.put(fm, new HashMap<Integer, IncrementalMagFreqDist>());
					nodeUnassociatedMFDsMap.put(fm, new HashMap<Integer, IncrementalMagFreqDist>());
					ratesMap.put(fm, new double[numRups]);
					magsMap.put(fm, new double[numRups]);
					defModelsMap.put(fm, new HashSet<DeformationModels>());
					if (numRups == 229104 || numRups == 249656) {
						rupSetCache.put(fm, sol.getRupSet());
					}
					List<IncrementalMagFreqDist> runningSubSeisMFDs = Lists.newArrayList();
					IncrementalMagFreqDist tempMFD = subSeisMFDs.get(0);
					for (int i=0; i<subSeisMFDs.size(); i++)
						runningSubSeisMFDs.add(new IncrementalMagFreqDist(tempMFD.getMinX(), tempMFD.size(), tempMFD.getDelta()));
					subSeisMFDsMap.put(fm, runningSubSeisMFDs);
					runningBranches.put(fm, (LogicTreeBranch)branch.clone());
				}
				updateRunningBranch(runningBranches.get(fm), branch);
				weightsList.add(weight);
				Map<Integer, IncrementalMagFreqDist> runningNodeSubSeisMFDs = nodeSubSeisMFDsMap.get(fm);
				Map<Integer, IncrementalMagFreqDist> runningNodeUnassociatedMFDs = nodeUnassociatedMFDsMap.get(fm);
				
				for (int i=0; i<region.getNumLocations(); i++) {
					addWeighted(runningNodeSubSeisMFDs, i, nodeSubSeisMFDs.get(i), weight);
					addWeighted(runningNodeUnassociatedMFDs, i, nodeUnassociatedMFDs.get(i), weight);
				}
				
				List<IncrementalMagFreqDist> runningSubSeisMFDs = subSeisMFDsMap.get(fm);
				for (int i=0; i<subSeisMFDs.size(); i++)
					addWeighted(runningSubSeisMFDs.get(i), subSeisMFDs.get(i), weight);
				
				addWeighted(ratesMap.get(fm), sol.getRateForAllRups(), weight);
				addWeighted(magsMap.get(fm), rupSet.getMagForAllRups(), weight);
				
				synchronized (defModelsMap) {
					defModelsMap.get(fm).add(branch.getValue(DeformationModels.class));
				}
			}
		}
		
		private static void updateRunningBranch(LogicTreeBranch runningBranch, LogicTreeBranch currentBranch) {
			for (int i=0; i<currentBranch.size(); i++)
				if (runningBranch.getValue(i) != currentBranch.getValue(i))
					runningBranch.clearValue(i);
		}
		
		public static void addWeighted(Map<Integer, IncrementalMagFreqDist> mfdMap, int index,
				IncrementalMagFreqDist newMFD, double weight) {
			if (newMFD == null)
				// simple case
				return;
			IncrementalMagFreqDist runningMFD = mfdMap.get(index);
			if (runningMFD == null) {
				runningMFD = new IncrementalMagFreqDist(newMFD.getMinX(), newMFD.size(), newMFD.getDelta());
				mfdMap.put(index, runningMFD);
			} else {
				Preconditions.checkState(runningMFD.size() == newMFD.size(), "MFD sizes inconsistent");
				Preconditions.checkState((float)runningMFD.getMinX() == (float)newMFD.getMinX(), "MFD min x inconsistent");
				Preconditions.checkState((float)runningMFD.getDelta() == (float)newMFD.getDelta(), "MFD delta inconsistent");
			}
			addWeighted(runningMFD, newMFD, weight);
		}
		
		public static void addWeighted(IncrementalMagFreqDist runningMFD,
				IncrementalMagFreqDist newMFD, double weight) {
			for (int i=0; i<runningMFD.size(); i++)
				runningMFD.add(i, newMFD.getY(i)*weight);
		}
		
		private void addWeighted(double[] running, double[] vals, double weight) {
			Preconditions.checkState(running.length == vals.length);
			for (int i=0; i<running.length; i++)
				running[i] += vals[i]*weight;
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				BranchAvgFSSBuilder o = (BranchAvgFSSBuilder) otherCalc;
				if (region == null)
					region = o.region;
				for (FaultModels fm : o.weightsMap.keySet()) {
					if (!weightsMap.containsKey(fm)) {
						weightsMap.put(fm, new ArrayList<Double>());
						nodeSubSeisMFDsMap.put(fm, new HashMap<Integer, IncrementalMagFreqDist>());
						nodeUnassociatedMFDsMap.put(fm, new HashMap<Integer, IncrementalMagFreqDist>());
						List<IncrementalMagFreqDist> subSeisMFDs = new ArrayList<IncrementalMagFreqDist>();
						for (IncrementalMagFreqDist oMFD : o.subSeisMFDsMap.get(fm))
							subSeisMFDs.add(new IncrementalMagFreqDist(oMFD.getMinX(), oMFD.size(), oMFD.getDelta()));
						subSeisMFDsMap.put(fm, subSeisMFDs);
						ratesMap.put(fm, new double[o.ratesMap.get(fm).length]);
						magsMap.put(fm, new double[o.magsMap.get(fm).length]);
						defModelsMap.put(fm, new HashSet<DeformationModels>());
						runningBranches.put(fm, o.runningBranches.get(fm));
					}
					updateRunningBranch(runningBranches.get(fm), o.runningBranches.get(fm));
					Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = o.nodeSubSeisMFDsMap.get(fm);
					Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = o.nodeUnassociatedMFDsMap.get(fm);
					Map<Integer, IncrementalMagFreqDist> runningNodeSubSeisMFDs = nodeSubSeisMFDsMap.get(fm);
					Map<Integer, IncrementalMagFreqDist> runningNodeUnassociatedMFDs = nodeUnassociatedMFDsMap.get(fm);
					for (int i=0; i<region.getNumLocations(); i++) {
						// weight is one because these have already been scaled
						addWeighted(runningNodeSubSeisMFDs, i, nodeSubSeisMFDs.get(i), 1d);
						addWeighted(runningNodeUnassociatedMFDs, i, nodeUnassociatedMFDs.get(i), 1d);
					}
					for (int i=0; i<subSeisMFDsMap.get(fm).size(); i++) {
						// weight is one because these have already been scaled
						addWeighted(subSeisMFDsMap.get(fm).get(i), o.subSeisMFDsMap.get(fm).get(i), 1d);
					}
					weightsMap.get(fm).addAll(o.weightsMap.get(fm));
					
					addWeighted(ratesMap.get(fm), o.ratesMap.get(fm), 1d);
					addWeighted(magsMap.get(fm), o.magsMap.get(fm), 1d);
					defModelsMap.get(fm).addAll(o.defModelsMap.get(fm));
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			// scale everything by total weight
			
			for (FaultModels fm : weightsMap.keySet()) {
				double sum = 0d;
				for (double weight : weightsMap.get(fm))
					sum += weight;
				
				double scale = 1d/sum;
				
				for (IncrementalMagFreqDist mfd : nodeSubSeisMFDsMap.get(fm).values())
					mfd.scale(scale);
				
				for (IncrementalMagFreqDist mfd : nodeUnassociatedMFDsMap.get(fm).values())
					mfd.scale(scale);
				
				for (IncrementalMagFreqDist mfd : subSeisMFDsMap.get(fm))
					mfd.scale(scale);
				
				double[] rates = ratesMap.get(fm);
				double[] mags = magsMap.get(fm);
				
				for (int i=0; i<rates.length; i++) {
					rates[i] *= scale;
					mags[i] *= scale;
				}
			}
		}
	}

	public static List<DiscretizedFunc> getFractiles(XY_DataSetList data,
			List<Double> weights, String name, double[] fractiles) {
		List<DiscretizedFunc> funcs = Lists.newArrayList();

		FractileCurveCalculator calc = new FractileCurveCalculator(data,
				weights);
		for (double fractile : fractiles) {
			DiscretizedFunc func = (DiscretizedFunc) calc.getFractile(fractile);
			func.setName(name + " (fractile at " + fractile + ")");
			funcs.add(func);
		}
		DiscretizedFunc meanFunc = (DiscretizedFunc) calc.getMeanCurve();
		meanFunc.setName(name + " (weighted mean)");
		funcs.add(meanFunc);
		DiscretizedFunc minFunc = (DiscretizedFunc) calc.getMinimumCurve();
		minFunc.setName(name + " (minimum)");
		funcs.add(minFunc);
		DiscretizedFunc maxFunc = (DiscretizedFunc) calc.getMaximumCurve();
		maxFunc.setName(name + " (maximum)");
		funcs.add(maxFunc);

		return funcs;
	}

	public static class SlipRatePlots extends MapBasedPlot {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public static final String PLOT_DATA_FILE_NAME = "slip_misfit_plots.xml";

		private transient BranchWeightProvider weightProvider;

		private ConcurrentMap<FaultModels, List<FaultSectionPrefData>> sectDatasMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, Map<String, List<Integer>>> parentSectsMap = Maps.newConcurrentMap();
		private Map<FaultModels, List<double[]>> solSlipsMap = Maps.newHashMap();
		private Map<FaultModels, List<double[]>> targetSlipsMap = Maps.newHashMap();
		private Map<FaultModels, List<Double>> weightsMap = Maps.newHashMap();

		private List<MapPlotData> plots;

		private static int cnt;
		
		private Map<FaultModels, CSVFile<String>> subSectCSVs = Maps.newHashMap();
		private Map<FaultModels, CSVFile<String>> parentSectCSVs = Maps.newHashMap();

		public SlipRatePlots(BranchWeightProvider weightProvider) {
			this.weightProvider = weightProvider;

			cnt = 0;
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			InversionFaultSystemRupSet rupSet = sol.getRupSet();
			int myCnt = cnt++;
			debug(solIndex, "Processing solution " + myCnt);

			double weight = weightProvider.getWeight(branch);
			if (weight == 0)
				return;

			double[] solSlips = sol.calcSlipRateForAllSects();
			double[] targetSlips = rupSet.getSlipRateForAllSections();
//			double[] ratios = new double[solSlips.length];
//			double[] fractDiffs = new double[solSlips.length];
//			for (int i = 0; i < solSlips.length; i++) {
//				// if (targetSlips[i] < 1e-5)
//				// System.out.println(branch.buildFileName()+": target["+i+"]="
//				// +targetSlips[i]+", sol["+i+"]="+solSlips[i]);
//				if (targetSlips[i] < 1e-8) {
//					ratios[i] = 1;
//					fractDiffs[i] = 0;
//				} else {
//					ratios[i] = solSlips[i] / targetSlips[i];
//					fractDiffs[i] = (solSlips[i] - targetSlips[i])
//							/ targetSlips[i];
//				}
//			}

			FaultModels fm = rupSet.getFaultModel();

			if (!sectDatasMap.containsKey(fm)) {
				sectDatasMap.putIfAbsent(fm, rupSet.getFaultSectionDataList());
			}
			
			if (!parentSectsMap.containsKey(fm)) {
				Map<String, List<Integer>> parentsMap = Maps.newHashMap();
				List<Integer> sects = Lists.newArrayList();
				String prevParentName =rupSet.getFaultSectionData(0).getParentSectionName();
				for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++) {
					String parentName = rupSet.getFaultSectionData(sectIndex).getParentSectionName();
					if (!parentName.equals(prevParentName)) {
						parentsMap.put(prevParentName, sects);
						prevParentName = parentName;
						sects = Lists.newArrayList();
					}
					sects.add(sectIndex);
				}
				if (!sects.isEmpty()) {
					parentsMap.put(prevParentName, sects);
				}
				parentSectsMap.putIfAbsent(fm, parentsMap);
			}

			debug(solIndex, "Archiving solution " + myCnt);

			synchronized (this) {
				List<double[]> solSlipsList = solSlipsMap.get(fm);
				if (solSlipsList == null) {
					solSlipsList = Lists.newArrayList();
					solSlipsMap.put(fm, solSlipsList);
				}
				solSlipsList.add(solSlips);
				
				List<double[]> targetsList = targetSlipsMap.get(fm);
				if (targetsList == null) {
					targetsList = Lists.newArrayList();
					targetSlipsMap.put(fm, targetsList);
				}
				targetsList.add(targetSlips);

				List<Double> weightsList = weightsMap.get(fm);
				if (weightsList == null) {
					weightsList = Lists.newArrayList();
					weightsMap.put(fm, weightsList);
				}
				weightsList.add(weight);
			}

			debug(solIndex, "Done with solution " + myCnt);
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				SlipRatePlots o = (SlipRatePlots) otherCalc;
				for (FaultModels fm : o.weightsMap.keySet()) {
					if (!sectDatasMap.containsKey(fm)) {
						sectDatasMap.put(fm, o.sectDatasMap.get(fm));
						parentSectsMap.put(fm, o.parentSectsMap.get(fm));
						solSlipsMap.put(fm, new ArrayList<double[]>());
						targetSlipsMap.put(fm, new ArrayList<double[]>());
						weightsMap.put(fm, new ArrayList<Double>());
					}
					solSlipsMap.get(fm).addAll(o.solSlipsMap.get(fm));
					targetSlipsMap.get(fm).addAll(o.targetSlipsMap.get(fm));
					weightsMap.get(fm).addAll(o.weightsMap.get(fm));
				}
			}
		}
		
		private static double meanFromIndexes(double[] array, List<Integer> indexes) {
			MinMaxAveTracker track = new MinMaxAveTracker();
			for (int index : indexes)
				track.addValue(array[index]);
			return track.getAverage();
		}

		@Override
		protected void doFinalizePlot() {
			plots = Lists.newArrayList();

			boolean multipleFMs = sectDatasMap.keySet().size() > 1;

			CPT linearCPT = FaultBasedMapGen.getLinearRatioCPT();
			CPT logCPT = FaultBasedMapGen.getLogRatioCPT().rescale(-1, 1);
			CPT slipRateCPT = FaultBasedMapGen.getSlipRateCPT();

			Region region = new CaliforniaRegions.RELM_TESTING();

			boolean skipNans = false;

			for (FaultModels fm : sectDatasMap.keySet()) {
				List<FaultSectionPrefData> sectDatas = sectDatasMap.get(fm);
				List<LocationList> faults = FaultBasedMapGen.getTraces(sectDatas);
				List<double[]> solSlipsList = solSlipsMap.get(fm);
				List<double[]> targetsList = targetSlipsMap.get(fm);
				List<Double> weightsList = weightsMap.get(fm);

				// TODO reinstate filter?????
//				double[] ratios = getWeightedAvg(faults.size(), ratiosList,
//						weightsList);
				double[] solSlips = getWeightedAvg(faults.size(), solSlipsList,
						weightsList);
				double[] solSlipMins = getMins(faults.size(), solSlipsList);
				double[] solSlipMaxs = getMaxs(faults.size(), solSlipsList);
				double[] targets = getWeightedAvg(faults.size(), targetsList,
						weightsList);
				double[] targetMins = getMins(faults.size(), targetsList);
				double[] targetMaxs = getMaxs(faults.size(), targetsList);
				
				double[] ratios = new double[solSlips.length];
				for (int i=0; i<ratios.length; i++)
					ratios[i] = solSlips[i] / targets[i];
				
				// make CSV file
				CSVFile<String> subSectCSV = new CSVFile<String>(true);
				subSectCSV.addLine("Sub Section Index", "Parent Section ID",
						"Mean Target Slip Rate (m/yr)", "Min Target Slip Rate (m/yr)",
						"Max Target Slip Rate (m/yr)", "Mean Solution Slip Rate (m/yr)",
						"Min Solution Slip Rate (m/yr)", "Max Solution Slip Rate (m/yr)",
						"Mean Slip Rate Misfit Ratio");
				for (int sectIndex=0; sectIndex<solSlips.length; sectIndex++) {
					subSectCSV.addLine(sectIndex+"", sectDatas.get(sectIndex).getParentSectionId()+"",
							targets[sectIndex]+"", targetMins[sectIndex]+"", targetMaxs[sectIndex]+"",
							solSlips[sectIndex]+"", solSlipMins[sectIndex]+"",
							solSlipMaxs[sectIndex]+"", ratios[sectIndex]+"");
				}
				subSectCSVs.put(fm, subSectCSV);
				CSVFile<String> parentSectCSV = new CSVFile<String>(true);
				Map<Integer, String> parentNamesMap = Maps.newHashMap();
				for (FaultSectionPrefData sect : sectDatas)
					parentNamesMap.put(sect.getParentSectionId(), sect.getParentSectionName());
				List<Integer> parentIDs = Lists.newArrayList(parentNamesMap.keySet());
				Collections.sort(parentIDs);
				parentSectCSV.addLine("Parent Section ID", "Parent Section Name",
						"Mean Target Slip Rate (m/yr)", "Min Target Slip Rate (m/yr)",
						"Max Target Slip Rate (m/yr)", "Mean Solution Slip Rate (m/yr)",
						"Min Solution Slip Rate (m/yr)", "Max Solution Slip Rate (m/yr)",
						"Mean Slip Rate Misfit Ratio");
				for (Integer parentID : parentIDs) {
					String parentName = parentNamesMap.get(parentID);
					List<Integer> indexes = parentSectsMap.get(fm).get(parentName);
					double parentTarget = meanFromIndexes(targets, indexes);
					double parentTargetMin = meanFromIndexes(targetMins , indexes);
					double parentTargetMax = meanFromIndexes(targetMaxs, indexes);
					double parentSolution = meanFromIndexes(solSlips, indexes);
					double parentSolutionMin = meanFromIndexes(solSlipMins, indexes);
					double parentSolutionMax = meanFromIndexes(solSlipMaxs, indexes);
					double parentRatio = meanFromIndexes(ratios, indexes);
					parentSectCSV.addLine(parentID+"", parentName+"", parentTarget+"",
							parentTargetMin+"", parentTargetMax+"", parentSolution+"",
							parentSolutionMin+"", parentSolutionMax+"", parentRatio+"");
				}
				parentSectCSVs.put(fm, parentSectCSV);

				String label = "Mean(Solution Slip Rate / Target Slip Rate)";
				String prefix = "";
				if (multipleFMs) {
					prefix += fm.getShortName() + "_";
					label = fm.getShortName() + " " + label;
				}
				MapPlotData plot = new MapPlotData(linearCPT, faults, ratios, region,
						skipNans, label, prefix + "slip_rate_misfit");
				plot.subDirName = "slip_rate_plots";
				plots.add(plot);

				label = "Log10(" + label + ")";
				double[] log10Values = FaultBasedMapGen.log10(ratios);
				plot = new MapPlotData(logCPT, faults, log10Values, region,
						skipNans, label, prefix + "slip_rate_misfit_log");
				plot.subDirName = "slip_rate_plots";
				plots.add(plot);

//				List<double[]> fractDiffList = fractDiffMap.get(fm);
//				double[] fractDiffs = getWeightedAvg(faults.size(),
//						fractDiffList, weightsList);

				label = "Mean Solution Slip Rate";
				prefix = "";
				if (multipleFMs) {
					prefix += fm.getShortName() + "_";
					label = fm.getShortName() + " " + label;
				}

				plot = new MapPlotData(slipRateCPT, faults, FaultBasedMapGen.scale(solSlips, 1e3), region,
						skipNans, label, prefix + "sol_slip_rate");
				plot.subDirName = "slip_rate_plots";
				plots.add(plot);

				label = "Mean Target Slip Rate (mm/yr)";
				prefix = "";
				if (multipleFMs) {
					prefix += fm.getShortName() + "_";
					label = fm.getShortName() + " " + label;
				}

				plot = new MapPlotData(slipRateCPT, faults, FaultBasedMapGen.scale(targets, 1e3), region,
						skipNans, label, prefix + "target_slip_rate");
				plot.subDirName = "slip_rate_plots";
				plots.add(plot);
			}
		}
		
		@Override
		protected void writeExtraData(File dir, String prefix) {
			boolean multipleFMs = subSectCSVs.keySet().size() > 1;
			
			for (FaultModels fm : subSectCSVs.keySet()) {
				CSVFile<String> subSectCSV = subSectCSVs.get(fm);
				CSVFile<String> parentSectCSV = parentSectCSVs.get(fm);
				
				String fname = prefix;
				if (multipleFMs)
					fname += "_"+fm.getShortName();
				try {
					subSectCSV.writeToFile(new File(dir, fname+"_slip_rates_sub_sections.csv"));
					parentSectCSV.writeToFile(new File(dir, fname+"_slip_rates_parent_sections.csv"));
				} catch (IOException e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
		}

		@Override
		protected List<MapPlotData> getPlotData() {
			return plots;
		}

		@Override
		protected String getPlotDataFileName() {
			return PLOT_DATA_FILE_NAME;
		}

	}

	public static class AveSlipMapPlot extends MapBasedPlot {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public static final String PLOT_DATA_FILE_NAME = "ave_slip_plots.xml";

		private transient BranchWeightProvider weightProvider;

		private ConcurrentMap<FaultModels, List<LocationList>> faultsMap = Maps
				.newConcurrentMap();
		private Map<FaultModels, List<double[]>> aveSlipsMap = Maps
				.newHashMap();
		private Map<FaultModels, List<double[]>> avePaleoSlipsMap = Maps
				.newHashMap();
		private Map<FaultModels, List<Double>> weightsMap = Maps.newHashMap();

		private List<MapPlotData> plots;

		private static int cnt;

		public AveSlipMapPlot(BranchWeightProvider weightProvider) {
			this.weightProvider = weightProvider;

			cnt = 0;
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			InversionFaultSystemRupSet rupSet = sol.getRupSet();
			int myCnt = cnt++;
			debug(solIndex, "Processing solution " + myCnt);

			double weight = weightProvider.getWeight(branch);
			if (weight == 0)
				return;

			FaultModels fm = rupSet.getFaultModel();

			double[] aveSlips = new double[rupSet.getNumSections()];
			double[] avePaleoSlips = new double[rupSet.getNumSections()];
			for (int i = 0; i < aveSlips.length; i++) {
				aveSlips[i] = sol.calcSlipPFD_ForSect(i).getMean();
				avePaleoSlips[i] = sol.calcPaleoObsSlipPFD_ForSect(i).getMean();
			}

			if (!faultsMap.containsKey(fm)) {
				List<LocationList> faults = FaultBasedMapGen.getTraces(rupSet.getFaultSectionDataList());
				faultsMap.putIfAbsent(fm, faults);
			}

			debug(solIndex, "Archiving solution " + myCnt);

			synchronized (this) {
				List<double[]> aveSlipsList = aveSlipsMap.get(fm);
				if (aveSlipsList == null) {
					aveSlipsList = Lists.newArrayList();
					aveSlipsMap.put(fm, aveSlipsList);
				}
				aveSlipsList.add(aveSlips);

				List<double[]> avePaleoSlipsList = avePaleoSlipsMap.get(fm);
				if (avePaleoSlipsList == null) {
					avePaleoSlipsList = Lists.newArrayList();
					avePaleoSlipsMap.put(fm, avePaleoSlipsList);
				}
				avePaleoSlipsList.add(avePaleoSlips);

				List<Double> weightsList = weightsMap.get(fm);
				if (weightsList == null) {
					weightsList = Lists.newArrayList();
					weightsMap.put(fm, weightsList);
				}
				weightsList.add(weight);
			}

			debug(solIndex, "Done with solution " + myCnt);
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				AveSlipMapPlot o = (AveSlipMapPlot) otherCalc;
				for (FaultModels fm : o.aveSlipsMap.keySet()) {
					if (!faultsMap.containsKey(fm)) {
						faultsMap.put(fm, o.faultsMap.get(fm));
						aveSlipsMap.put(fm, new ArrayList<double[]>());
						avePaleoSlipsMap.put(fm, new ArrayList<double[]>());
						weightsMap.put(fm, new ArrayList<Double>());
					}
					aveSlipsMap.get(fm).addAll(o.aveSlipsMap.get(fm));
					avePaleoSlipsMap.get(fm).addAll(o.avePaleoSlipsMap.get(fm));
					weightsMap.get(fm).addAll(o.weightsMap.get(fm));
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			plots = Lists.newArrayList();

			boolean multipleFMs = faultsMap.keySet().size() > 1;

			CPT cpt;
			try {
				cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0, 8);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}

			Region region = new CaliforniaRegions.RELM_TESTING();

			boolean skipNans = false;

			for (FaultModels fm : faultsMap.keySet()) {
				List<LocationList> faults = faultsMap.get(fm);
				List<double[]> aveSlipsList = aveSlipsMap.get(fm);
				List<Double> weightsList = weightsMap.get(fm);

				double[] ratios = getWeightedAvg(faults.size(), aveSlipsList,
						weightsList);

				String label = "Average Slip (m)";
				String prefix = "";
				if (multipleFMs) {
					prefix += fm.getShortName() + "_";
					label = fm.getShortName() + " " + label;
				}

				MapPlotData plot = new MapPlotData(cpt, faults, ratios, region,
						skipNans, label, prefix + "ave_slip");
				plot.subDirName = "ave_slip_plots";
				plots.add(plot);

				List<double[]> avePaleoSlipsList = avePaleoSlipsMap.get(fm);
				double[] fractDiffs = getWeightedAvg(faults.size(),
						avePaleoSlipsList, weightsList);

				label = "Paleo Observable Average Slip (m)";
				prefix = "";
				if (multipleFMs) {
					prefix += fm.getShortName() + "_";
					label = fm.getShortName() + " " + label;
				}

				plot = new MapPlotData(cpt, faults, fractDiffs, region,
						skipNans, label, prefix + "ave_paleo_obs_slip");
				plot.subDirName = "ave_slip_plots";
				plots.add(plot);
			}
		}

		@Override
		protected List<MapPlotData> getPlotData() {
			return plots;
		}

		@Override
		protected String getPlotDataFileName() {
			return PLOT_DATA_FILE_NAME;
		}

	}

	public static class MultiFaultParticPlot extends MapBasedPlot {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public static final String PLOT_DATA_FILE_NAME = "multi_fault_rates.xml";
		public static final String SUB_DIR_NAME = "multi_fault_partics";

		private static final double minMag = 6.7;

		private transient BranchWeightProvider weightProvider;

		private ConcurrentMap<FaultModels, List<LocationList>> faultsMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, Map<Integer, int[]>> sectsByParentsMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, Map<Integer, int[]>> parentsByParentsMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<Integer, String> parentNamesMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<FaultModels, Map<Integer, List<Integer>>> rupsForParentsMap = Maps
				.newConcurrentMap();
		private Map<FaultModels, List<Map<Integer, double[]>>> ratesMap = Maps
				.newHashMap();
		private Map<FaultModels, List<Double>> weightsMap = Maps.newHashMap();

		private List<MapPlotData> plots;

		private static int cnt;

		public MultiFaultParticPlot(BranchWeightProvider weightProvider) {
			this.weightProvider = weightProvider;

			cnt = 0;
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			int myCnt = cnt++;
			debug(solIndex, "Processing solution " + myCnt);
			
			InversionFaultSystemRupSet rupSet = sol.getRupSet();

			FaultModels fm = rupSet.getFaultModel();

			debug(solIndex, "cache fetching");
			Map<Integer, int[]> sectsByParents = sectsByParentsMap.get(fm);
			if (sectsByParents == null) {
				synchronized (this) {
					if (sectsByParents == null) {
						// use hashset to avoid duplicates

						sectsByParents = Maps.newHashMap();
						HashSet<Integer> parentsSet = new HashSet<Integer>();
						for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
							parentsSet.add(sect.getParentSectionId());
							parentNamesMap.putIfAbsent(
									sect.getParentSectionId(),
									sect.getParentSectionName());
						}

						Map<Integer, List<Integer>> rupsForParents = Maps
								.newHashMap();

						Map<Integer, int[]> parentsByParents = Maps
								.newHashMap();

						for (Integer parentID : parentsSet) {
							// use hashset to avoid duplicates
							HashSet<Integer> subSectsSet = new HashSet<Integer>();
							// use hashset to avoid duplicates
							HashSet<Integer> parentsByParentsSet = new HashSet<Integer>();

							List<Integer> rups = rupSet.getRupturesForParentSection(parentID);
							rupsForParents.put(parentID, rups);

							for (Integer rupID : rups) {
								for (Integer sectIndex : rupSet.getSectionsIndicesForRup(rupID)) {
									subSectsSet.add(sectIndex);
									parentsByParentsSet.add(
											rupSet.getFaultSectionData(sectIndex).getParentSectionId());
								}
							}

							List<Integer> subSects = Lists
									.newArrayList(subSectsSet);
							// sort to ensure correct order between different
							// sols
							Collections.sort(subSects);

							sectsByParents
									.put(parentID, Ints.toArray(subSects));

							List<Integer> parentsByParentsList = Lists
									.newArrayList(parentsByParentsSet);
							// sort to ensure correct order between different
							// sols
							Collections.sort(parentsByParentsList);

							parentsByParents.put(parentID,
									Ints.toArray(parentsByParentsList));
						}

						rupsForParentsMap.put(fm, rupsForParents);
						parentsByParentsMap.put(fm, parentsByParents);

						// this MUST be the last line of this synchronized block
						sectsByParentsMap.put(fm, sectsByParents);
					}
				}
				sectsByParents = sectsByParentsMap.get(fm);
			}

			double weight = weightProvider.getWeight(branch);
			if (weight == 0)
				return;

			debug(solIndex, "calculating");

			Map<Integer, List<Integer>> rupsMap = rupsForParentsMap.get(fm);

			Map<Integer, double[]> rates = Maps.newHashMap();

			for (Integer parentID : sectsByParents.keySet()) {
				int[] sectsInvolved = sectsByParents.get(parentID);
				double[] parentRates = new double[sectsInvolved.length];
				for (Integer rupID : rupsMap.get(parentID)) {
					if (rupSet.getMagForRup(rupID) < minMag)
						continue;
					double rate = sol.getRateForRup(rupID);
					for (int sectID : rupSet.getSectionsIndicesForRup(rupID)) {
						int sectIndexInArray = Arrays.binarySearch(
								sectsInvolved, sectID);
						parentRates[sectIndexInArray] += rate;
					}
				}
				rates.put(parentID, parentRates);
			}

			if (!faultsMap.containsKey(fm)) {
				List<LocationList> faults = FaultBasedMapGen.getTraces(
						rupSet.getFaultSectionDataList());
				faultsMap.putIfAbsent(fm, faults);
			}

			debug(solIndex, "Archiving solution " + myCnt);

			synchronized (this) {
				List<Map<Integer, double[]>> ratesList = ratesMap.get(fm);
				if (ratesList == null) {
					ratesList = Lists.newArrayList();
					ratesMap.put(fm, ratesList);
				}
				ratesList.add(rates);

				List<Double> weightsList = weightsMap.get(fm);
				if (weightsList == null) {
					weightsList = Lists.newArrayList();
					weightsMap.put(fm, weightsList);
				}
				weightsList.add(weight);
			}

			debug(solIndex, "Done with solution " + myCnt);
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				MultiFaultParticPlot o = (MultiFaultParticPlot) otherCalc;
				for (FaultModels fm : o.ratesMap.keySet()) {
					if (!faultsMap.containsKey(fm)) {
						faultsMap.put(fm, o.faultsMap.get(fm));
						sectsByParentsMap.put(fm, o.sectsByParentsMap.get(fm));
						parentsByParentsMap.put(fm,
								o.parentsByParentsMap.get(fm));
						parentNamesMap.putAll(o.parentNamesMap);
						ratesMap.put(fm,
								new ArrayList<Map<Integer, double[]>>());
						weightsMap.put(fm, new ArrayList<Double>());
					}
					ratesMap.get(fm).addAll(o.ratesMap.get(fm));
					weightsMap.get(fm).addAll(o.weightsMap.get(fm));
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			plots = Lists.newArrayList();

			boolean multipleFMs = faultsMap.keySet().size() > 1;

			CPT cpt;
			try {
				cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-10, -2);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			cpt.setNanColor(Color.GRAY);

			Region region = new CaliforniaRegions.RELM_TESTING();

			boolean skipNans = false;

			List<FaultModels> fmList = Lists.newArrayList(faultsMap.keySet());

			for (int f = 0; f < fmList.size(); f++) {
				FaultModels fm = fmList.get(f);

				List<LocationList> faults = faultsMap.get(fm);
				List<Map<Integer, double[]>> ratesList = ratesMap.get(fm);
				List<Double> weightsList = weightsMap.get(fm);
				Map<Integer, int[]> sectsByParents = sectsByParentsMap.get(fm);

				Map<Integer, FaultSectionPrefData> parentSectsMap = fm
						.fetchFaultSectionsMap();

				for (Integer parentID : ratesList.get(0).keySet()) {
					if (!parentSectsMap.containsKey(parentID))
						continue; // removed
					int[] sectsInvolved = sectsByParents.get(parentID);

					List<double[]> solRates = Lists.newArrayList();
					for (Map<Integer, double[]> solRatesMap : ratesList)
						solRates.add(solRatesMap.get(parentID));
					List<Double> myWeightsList = weightsList;

					boolean comboFM = false;

					String parentName = parentNamesMap.get(parentID);

					if (f == 0 && fmList.size() > 1) {
						// see if we can combine all FMs here
						int[] parentsByParent = parentsByParentsMap.get(fm)
								.get(parentID);

						boolean match = true;

						for (int i = 1; i < fmList.size(); i++) {
							int[] otherParentsByParent = parentsByParentsMap
									.get(fmList.get(i)).get(parentID);
							if (otherParentsByParent == null
									|| !Arrays.equals(parentsByParent,
											otherParentsByParent)) {
								match = false;
								break;
							}
						}

						if (match) {
							comboFM = true;

							// System.out.println("Merging FMs for: "+parentName);

							myWeightsList = Lists.newArrayList(myWeightsList);
							for (int i = 1; i < fmList.size(); i++) {
								FaultModels ofm = fmList.get(i);
								myWeightsList.addAll(weightsMap.get(ofm));
								for (Map<Integer, double[]> solRatesMap : ratesMap
										.get(ofm))
									solRates.add(solRatesMap.remove(parentID));
							}
						}
					}

					double[] rates = getWeightedAvg(sectsInvolved.length,
							solRates, weightsList);

					// +1 because we add the highlight at the end
					double[] allRates = new double[faults.size() + 1];
					// initialize to NaN
					for (int i = 0; i < allRates.length; i++)
						allRates[i] = Double.NaN;

					for (int i = 0; i < sectsInvolved.length; i++) {
						int sectIndex = sectsInvolved[i];

						allRates[sectIndex] = rates[i];
					}

					allRates = FaultBasedMapGen.log10(allRates);
					List<LocationList> myFaults = Lists.newArrayList(faults);
					// add highlight
					myFaults.add(parentSectsMap.get(parentID).getFaultTrace());
					allRates[allRates.length - 1] = FaultBasedMapGen.FAULT_HIGHLIGHT_VALUE;

					String label = parentNamesMap.get(parentID) + " ("
							+ parentID + ")";
					String prefix = parentName.replaceAll("\\W+", "_");
					if (!comboFM) {
						label = fm.getShortName() + " " + label;
						prefix += "_" + fm.getShortName();
					}
					MapPlotData plot = new MapPlotData(cpt, myFaults, allRates,
							region, skipNans, label, prefix);
					plot.subDirName = SUB_DIR_NAME;
					plots.add(plot);
				}
			}
		}

		@Override
		protected List<MapPlotData> getPlotData() {
			return plots;
		}

		@Override
		protected String getPlotDataFileName() {
			return PLOT_DATA_FILE_NAME;
		}
	}

	public static class ParticipationMapPlot extends MapBasedPlot {

		private List<double[]> ranges;

		public static List<double[]> getDefaultRanges() {
			List<double[]> ranges = Lists.newArrayList();

			ranges.add(toArray(5d, 9d));
			ranges.add(toArray(6.7d, 9d));
			ranges.add(toArray(7.7d, 9d));
			ranges.add(toArray(8d, 9d));
			ranges.add(toArray(6.5d, 7d));
			ranges.add(toArray(7d, 7.5));
			ranges.add(toArray(7.5d, 8d));
			ranges.add(toArray(8d, 9d));

			return ranges;
		}

		private static double[] toArray(double... vals) {
			return vals;
		}

		private transient BranchWeightProvider weightProvider;

		private ConcurrentMap<FaultModels, List<LocationList>> faultsMap = Maps
				.newConcurrentMap();
		private Map<FaultModels, List<List<double[]>>> valuesMap = Maps
				.newHashMap();
		private Map<FaultModels, List<Double>> weightsMap = Maps.newHashMap();

		private List<MapPlotData> plots;

		public ParticipationMapPlot(BranchWeightProvider weightProvider) {
			this(weightProvider, getDefaultRanges());
		}

		public ParticipationMapPlot(BranchWeightProvider weightProvider,
				List<double[]> ranges) {
			this.weightProvider = weightProvider;
			this.ranges = ranges;
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			double weight = weightProvider.getWeight(branch);
			if (weight == 0)
				return;

			debug(solIndex, "calculating");

			List<double[]> myValues = Lists.newArrayList();
			for (double[] range : ranges) {
				myValues.add(sol.calcParticRateForAllSects(range[0], range[1]));
			}

			FaultModels fm = sol.getRupSet().getFaultModel();

			debug(solIndex, "trace building");
			if (!faultsMap.containsKey(fm)) {
				List<LocationList> faults = FaultBasedMapGen.getTraces(
						sol.getRupSet().getFaultSectionDataList());
				faultsMap.putIfAbsent(fm, faults);
			}

			debug(solIndex, "archiving");
			synchronized (this) {
				List<List<double[]>> valuesList = valuesMap.get(fm);
				if (valuesList == null) {
					valuesList = Lists.newArrayList();
					valuesMap.put(fm, valuesList);
				}
				valuesList.add(myValues);

				List<Double> weightsList = weightsMap.get(fm);
				if (weightsList == null) {
					weightsList = Lists.newArrayList();
					weightsMap.put(fm, weightsList);
				}
				weightsList.add(weight);
			}
			debug(solIndex, "done");
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				ParticipationMapPlot o = (ParticipationMapPlot) otherCalc;
				for (FaultModels fm : o.valuesMap.keySet()) {
					if (!faultsMap.containsKey(fm)) {
						faultsMap.put(fm, o.faultsMap.get(fm));
						valuesMap.put(fm, new ArrayList<List<double[]>>());
						weightsMap.put(fm, new ArrayList<Double>());
					}
					valuesMap.get(fm).addAll(o.valuesMap.get(fm));
					weightsMap.get(fm).addAll(o.weightsMap.get(fm));
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			plots = Lists.newArrayList();

			boolean multipleFMs = faultsMap.keySet().size() > 1;

			CPT participationCPT = FaultBasedMapGen.getParticipationCPT();
			CPT logCPT = FaultBasedMapGen.getLogRatioCPT();

			Region region = new CaliforniaRegions.RELM_TESTING();

			boolean skipNans = true;
			boolean omitInfinites = true;

			for (FaultModels fm : faultsMap.keySet()) {
				List<LocationList> faults = faultsMap.get(fm);
				List<List<double[]>> valuesList = valuesMap.get(fm);
				List<Double> weightsList = weightsMap.get(fm);

				FaultSystemSolution ucerf2 = UCERF2_ComparisonSolutionFetcher
						.getUCERF2Solution(fm);

				for (int i = 0; i < ranges.size(); i++) {
					double minMag = ranges.get(i)[0];
					double maxMag = ranges.get(i)[1];

					List<double[]> rangeValsList = Lists.newArrayList();
					for (int s = 0; s < valuesList.size(); s++)
						rangeValsList.add(valuesList.get(s).get(i));

					// double[] values = getWeightedAvg(faults.size(),
					// rangeValsList, weightsList);
					double[] values = new double[faults.size()];
					// TODO
					// double[] stdDevs = getStdDevs(faults.size(),
					// valuesList.get(i));
					double[] stdDevs = new double[values.length];
					for (int s = 0; s < values.length; s++) {
						ArbDiscrEmpiricalDistFunc func = new ArbDiscrEmpiricalDistFunc();
						for (int j = 0; j < weightsList.size(); j++)
							// val, weight
							func.set(rangeValsList.get(j)[s],
									weightsList.get(j));
						stdDevs[s] = func.getStdDev();
						values[s] = func.getMean();
					}
					double[] logValues = FaultBasedMapGen.log10(values);

					String name = "partic_rates_" + (float) minMag;
					String title = "Log10(Participation Rates "
							+ (float) +minMag;
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
					title += ")";

					if (multipleFMs) {
						name = fm.getShortName() + "_" + name;
						title = fm.getShortName() + " " + title;
					}

					MapPlotData plot = new MapPlotData(participationCPT, faults,
							logValues, region, skipNans, title, name);
					plot.subDirName = "fault_participation_plots";
					plots.add(plot);

					double[] ucerf2Vals = ucerf2.calcParticRateForAllSects(
							minMag, maxMag);

					double[] ratios = new double[ucerf2Vals.length];
					for (int j = 0; j < values.length; j++) {
						ratios[j] = values[j] / ucerf2Vals[j];
						if (omitInfinites && Double.isInfinite(ratios[j]))
							ratios[j] = Double.NaN;
					}
					ratios = FaultBasedMapGen.log10(ratios);

					name = "partic_ratio_" + (float) minMag;
					title = "Log10(Participation Ratios " + (float) +minMag;
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
					title += ")";

					if (multipleFMs) {
						name = fm.getShortName() + "_" + name;
						title = fm.getShortName() + " " + title;
					}

					plot = new MapPlotData(logCPT, faults, ratios, region,
							skipNans, title, name);
					plot.subDirName = "fault_participation_plots";
					plots.add(plot);

					double[] stdNormVals = new double[values.length];

					for (int s = 0; s < stdNormVals.length; s++) {
						if (ucerf2Vals[s] == 0)
							stdNormVals[s] = Double.NaN;
						else
							stdNormVals[s] = (values[s] - ucerf2Vals[s])
									/ stdDevs[s];
					}

					name = "partic_diffs_norm_std_dev_" + (float) minMag;
					title = "(U3mean - U2mean)/U3std " + (float) +minMag;
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
					// title += ")";

					if (multipleFMs) {
						name = fm.getShortName() + "_" + name;
						title = fm.getShortName() + " " + title;
					}

					plot = new MapPlotData(logCPT, faults, stdNormVals,
							region, skipNans, title, name);
					plot.subDirName = "fault_participation_plots";
					plots.add(plot);
				}
			}
		}

		@Override
		protected List<MapPlotData> getPlotData() {
			return plots;
		}

		@Override
		protected String getPlotDataFileName() {
			return "participation_plots.xml";
		}

	}
	
	public static class TimeDepGriddedParticipationProbPlot extends MapBasedPlot {
		private List<double[]> ranges;
		private double spacing;

		// organized as: duration: branch, magRange
		private Map<Double, List<List<GeoDataSet>>> particDepDatas;
		private Map<Double, List<List<GeoDataSet>>> particIndepDatas;
		private Map<Double, List<Double>> weights;
		
		//organized as: duration: branch, magRange
		private Map<Double, List<GriddedGeoDataSet>> meanU2IndepDatas;
		private Map<Double, List<GriddedGeoDataSet>> meanU2DepDatas;
		
		private GriddedRegion griddedRegion;

		private transient Map<FaultModels, FSSRupNodesCache> rupNodesCache = Maps.newHashMap();

		private List<MapPlotData> plots;
		
		private static final double[] durations = time_dep_durations;

		public static List<double[]> getDefaultRanges() {
			List<double[]> ranges = Lists.newArrayList();

			ranges.add(toArray(5d, 9d));
			ranges.add(toArray(6.7d, 9d));
			ranges.add(toArray(7.7d, 9d));
			ranges.add(toArray(8d, 9d));

			return ranges;
		}

		private static double[] toArray(double... vals) {
			return vals;
		}

		private transient BranchWeightProvider weightProvider;

		public TimeDepGriddedParticipationProbPlot(BranchWeightProvider weightProvider) {
			this(weightProvider, 0.1d);
		}

		public TimeDepGriddedParticipationProbPlot(BranchWeightProvider weightProvider,
				double spacing) {
			this(weightProvider, getDefaultRanges(), spacing);
		}

		public TimeDepGriddedParticipationProbPlot(BranchWeightProvider weightProvider,
				List<double[]> ranges, double spacing) {
			this.weightProvider = weightProvider;
			this.ranges = ranges;
			this.spacing = spacing;
			griddedRegion = new CaliforniaRegions.RELM_TESTING_GRIDDED(spacing);

			particDepDatas = Maps.newHashMap();
			particIndepDatas = Maps.newHashMap();
			weights = Maps.newHashMap();
			
			meanU2DepDatas = Maps.newHashMap();
			meanU2IndepDatas = Maps.newHashMap();
			
			for (double duration : durations) {
				particDepDatas.put(duration, new ArrayList<List<GeoDataSet>>());
				particIndepDatas.put(duration, new ArrayList<List<GeoDataSet>>());
				weights.put(duration, new ArrayList<Double>());
			}
		}

		@Override
		protected String getPlotDataFileName() {
			return "gridded_participation_prob_plots.xml";
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			processERF(branch, new FaultSystemSolutionERF(sol), 0);
		}

		@Override
		protected void processERF(LogicTreeBranch branch,
				FaultSystemSolutionERF erf, int solIndex) {
			
			InversionFaultSystemRupSet rupSet = ((InversionFaultSystemSolution)erf.getSolution()).getRupSet();
			FaultPolyMgr polyManager = rupSet.getInversionTargetMFDs().getGridSeisUtils().getPolyMgr();

			debug(solIndex, "cache check");
			FaultModels fm = branch.getValue(FaultModels.class);
			synchronized (this) {
				if (!rupNodesCache.containsKey(fm)) {
					FSSRupNodesCache cache = new FSSRupNodesCache();
					rupNodesCache.put(fm, cache);
				}
			}
			debug(solIndex, "cache check done");
			FSSRupNodesCache cache = rupNodesCache.get(fm);
			
			double origDur = erf.getTimeSpan().getDuration();
			
			if (solIndex == 0)
				calcUCERF2();
			
			for (double duration : durations) {
				erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.ONLY);
				erf.updateForecast();
				List<GriddedGeoDataSet> subSeisData = Lists.newArrayList();
				// can do sub seis once as it doesn't vary
				for (int i = 0; i < ranges.size(); i++) {
					double[] range = ranges.get(i);
					double minMag = range[0];
					double maxMag = range[1];
					debug(solIndex, "calc partic range " + i);
					subSeisData.add(getAsProbs(ERF_Calculator.getParticipationRatesInRegion(erf,
							griddedRegion, minMag, maxMag, cache), duration));
					debug(solIndex, "done partic range " + i);
				}
				erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
				erf.getTimeSpan().setDuration(duration);
				erf.updateForecast();
				List<GeoDataSet> particDepData = calcProbsSupraSubSeis(polyManager, erf, subSeisData);
//				EvenlyDiscretizedFunc[] depFuncs = FaultSysSolutionERF_Calc.calcSubSectSupraSeisMagProbDists(erf, 5d, 41, 0.1d);
//				for (int i = 0; i < ranges.size(); i++) {
//					double[] range = ranges.get(i);
//					double minMag = range[0];
//					double maxMag = range[1];
//					debug(solIndex, "calc partic range " + i);
//					particDepData.add(getAsProbs(ERF_Calculator.getParticipationRatesInRegion(erf,
//								griddedRegion, minMag, maxMag, cache), duration));
//					debug(solIndex, "done partic range " + i);
//				}
				erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
				erf.getTimeSpan().setDuration(duration);
				erf.updateForecast();
				List<GeoDataSet> particIndepData = calcProbsSupraSubSeis(polyManager, erf, subSeisData);
//				EvenlyDiscretizedFunc[] indepFuncs = FaultSysSolutionERF_Calc.calcSubSectSupraSeisMagProbDists(erf, 5d, 41, 0.1d);
//				for (int i = 0; i < ranges.size(); i++) {
//					double[] range = ranges.get(i);
//					double minMag = range[0];
//					double maxMag = range[1];
//					debug(solIndex, "calc partic range " + i);
//					particIndepData.add(getAsProbs(ERF_Calculator.getParticipationRatesInRegion(erf,
//								griddedRegion, minMag, maxMag, cache), duration));
//					debug(solIndex, "done partic range " + i);
//				}
				erf.getTimeSpan().setDuration(origDur);
				erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
				debug(solIndex, "archive");
				synchronized (this) {
					particDepDatas.get(duration).add(particDepData);
					particIndepDatas.get(duration).add(particIndepData);
					weights.get(duration).add(weightProvider.getWeight(branch));
				}
				debug(solIndex, "archive done");
			}
			erf.getTimeSpan().setDuration(origDur);
			erf.updateForecast();
		}
		
//		private List<GeoDataSet> combineSupraSubSeis(
//				FaultPolyMgr polys, EvenlyDiscretizedFunc[] supraProbFuncs, List<GriddedGeoDataSet> subSeisProbs) {
//			List<GeoDataSet> datas = Lists.newArrayList();
//			
//			for (int i=0; i<ranges.size(); i++) {
//				int funcIndex = supraProbFuncs[0].getXIndex(ranges.get(i)[0]);
//				GriddedGeoDataSet subSeis = subSeisProbs.get(i);
//				
//				// this lists the probability of each independent event for each node
//				List<List<Double>> nodeProbLists = Lists.newArrayList();
//				// first add in the sub seis probs
//				for (int n=0; n<subSeis.size(); n++)
//					nodeProbLists.add(Lists.newArrayList(subSeis.get(n)));
//				
//				for (int s=0; s<supraProbFuncs.length; s++) {
//					double rupProb = supraProbFuncs[s].getY(funcIndex);
//					Map<Integer, Double> nodeFracts = polys.getNodeFractions(s);
//					for (int n : nodeFracts.keySet()) {
//						nodeProbLists.get(n).add(rupProb * nodeFracts.get(n));
//					}
//				}
//				
//				GriddedGeoDataSet summed = new GriddedGeoDataSet(subSeis.getRegion(), subSeis.isLatitudeX());
//				for (int n=0; n<summed.size(); n++) {
//					List<Double> nodeProbs = nodeProbLists.get(n);
//					double prob = FaultSysSolutionERF_Calc.calcSummedProbs(nodeProbs);
//					summed.set(n, prob);
//				}
//				
//				datas.add(summed);
//			}
//			
//			return datas;
//		}
		
		private List<GeoDataSet> calcProbsSupraSubSeis(
				FaultPolyMgr polys, FaultSystemSolutionERF erf, List<GriddedGeoDataSet> subSeisProbs) {
			List<GeoDataSet> datas = Lists.newArrayList();
			FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
			
			for (int i=0; i<ranges.size(); i++) {
				double minMag = ranges.get(i)[0];
				double maxMag = ranges.get(i)[1];
				
				GriddedGeoDataSet subSeis = subSeisProbs.get(i);
				
				// this lists the probability of each independent event for each node
				List<List<Double>> nodeProbLists = Lists.newArrayList();
				// first add in the sub seis probs
				for (int n=0; n<subSeis.size(); n++)
					nodeProbLists.add(Lists.newArrayList(subSeis.get(n)));
				
				for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
					int invIndex = erf.getFltSysRupIndexForSource(sourceID);
					for (ProbEqkRupture rup : erf.getSource(sourceID)) {
						double mag = rup.getMag();
						if (mag < minMag || mag > maxMag)
							continue;
						double prob = rup.getProbability();
						for (int s : rupSet.getSectionsIndicesForRup(invIndex)) {
							Map<Integer, Double> nodeFracts = polys.getNodeFractions(s);
							for (int n : nodeFracts.keySet()) {
								nodeProbLists.get(n).add(prob * nodeFracts.get(n));
							}
						}
					}
				}
				
				GriddedGeoDataSet summed = new GriddedGeoDataSet(subSeis.getRegion(), subSeis.isLatitudeX());
				for (int n=0; n<summed.size(); n++) {
					List<Double> nodeProbs = nodeProbLists.get(n);
					double prob = FaultSysSolutionERF_Calc.calcSummedProbs(nodeProbs);
					summed.set(n, prob);
				}
				
				datas.add(summed);
			}
			
			return datas;
		}
		
		private void calcUCERF2() {
			MeanUCERF2 erf = new MeanUCERF2();
			for (double duration : durations) {
				for (boolean timeDep : new Boolean[] { true, false }) {
					if (timeDep) {
						erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, MeanUCERF2.PROB_MODEL_WGCEP_PREF_BLEND);
						erf.getTimeSpan().setDuration(duration);
					} else {
						erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
						erf.getTimeSpan().setDuration(duration);
					}
					erf.updateForecast();
					
					List<GriddedGeoDataSet> datas = Lists.newArrayList();
					
					for (int i=0; i<ranges.size(); i++) {
						double minMag = ranges.get(i)[0];
						double maxMag = ranges.get(i)[1];
						
						datas.add(getAsProbs(ERF_Calculator.getParticipationRatesInRegion(erf,
								griddedRegion, minMag, maxMag, null), duration));
					}
					
					if (timeDep)
						meanU2DepDatas.put(duration, datas);
					else
						meanU2IndepDatas.put(duration, datas);
				}
			}
		}
		
		public static GriddedGeoDataSet getAsProbs(GriddedGeoDataSet ratesData, double duration) {
			GriddedGeoDataSet probsData = new GriddedGeoDataSet(ratesData.getRegion(), ratesData.isLatitudeX());
			for (int i=0; i<ratesData.size(); i++) {
				double rate = ratesData.get(i);
				double prob = 1-Math.exp(-rate*duration);
				probsData.set(i, prob);
			}
			return probsData;
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				TimeDepGriddedParticipationProbPlot o = (TimeDepGriddedParticipationProbPlot) otherCalc;
				for (double duration : durations) {
					particDepDatas.get(duration).addAll(o.particDepDatas.get(duration));
					particIndepDatas.get(duration).addAll(o.particIndepDatas.get(duration));
					weights.get(duration).addAll(o.weights.get(duration));
				}
				if (!o.meanU2DepDatas.isEmpty()) {
					meanU2DepDatas.putAll(o.meanU2DepDatas);
					meanU2IndepDatas.putAll(o.meanU2IndepDatas);
				}
			}
		}

		@Override
		protected void doFinalizePlot() {
			debug(-1, "Finalizing plot");
			boolean debug = false;

			plots = Lists.newArrayList();

			CPT particCPT = FaultBasedMapGen.getParticipationCPT()
					.rescale(-5, 0);
//			CPT ratioCPT = (CPT) FaultBasedMapGen.getLogRatioCPT().clone();
			CPT ratioCPT;
			try {
				ratioCPT = FaultSysSolutionERF_Calc.getScaledLinearRatioCPT(0.02);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
//			ratioCPT.setNanColor(Color.WHITE);
			ratioCPT.setNanColor(ratioCPT.getColor(1f));
//			ratioCPT.setAboveMaxColor(Color.BLACK);
			
			// write metadata
			SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
			
			for (double duration : durations) {
				for (int r = 0; r < ranges.size(); r++) {
					List<List<GeoDataSet>> depDatas = particDepDatas.get(duration);
					List<List<GeoDataSet>> indepDatas = particIndepDatas.get(duration);
					
					GriddedGeoDataSet meanU2DepData = meanU2DepDatas.get(duration).get(r);
					GriddedGeoDataSet meanU2IndepData = meanU2IndepDatas.get(duration).get(r);
					
					if (depDatas.get(0).size() <= r) {
						debug(-1, "SKIPPING r="+r);
						continue;
					}
					debug(-1, "Building r="+r);
					
					double[] range = ranges.get(r);
					double minMag = range[0];
					double maxMag = range[1];

					XY_DataSetList funcs = new XY_DataSetList();

					for (int i = 0; i < depDatas.size(); i++) {
						GeoDataSet data = depDatas.get(i).get(r);
						EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0d,
								data.size(), 1d);
						for (int j = 0; j < data.size(); j++)
							func.set(j, data.get(j));

						funcs.add(func);
					}

					FractileCurveCalculator calc = new FractileCurveCalculator(
							funcs, weights.get(duration));

					GriddedGeoDataSet data = new GriddedGeoDataSet(griddedRegion,
							true);
					AbstractXY_DataSet meanDataFunc = calc.getMeanCurve();
					Preconditions.checkState(meanDataFunc.size() == data.size());
					for (int i = 0; i < data.size(); i++)
						data.set(i, meanDataFunc.getY(i));

					double[] weightsArray = Doubles.toArray(weights.get(duration));

					data = new GriddedGeoDataSet(griddedRegion, true);
					for (int i = 0; i < data.size(); i++) {
						double[] vals = new double[depDatas.size()];
						for (int j = 0; j < depDatas.size(); j++)
							vals[j] = depDatas.get(j).get(r).get(i);
						data.set(i, FaultSystemSolutionFetcher.calcScaledAverage(
								vals, weightsArray));
					}

					if (debug && r == 0) {
						for (int i = 0; i < depDatas.size() && i < 10; i++) {
							GeoDataSet subData = depDatas.get(i).get(r).copy();
							subData.log10();
							plots.add(new MapPlotData(particCPT, subData,
									spacing, griddedRegion, true,
									"Sub Participation 5+ " + i, "sub_partic_5+_"
											+ i));
						}
					}

					// take log10
					GriddedGeoDataSet logData = data.copy();
					logData.log10();

					String name = (int)duration+"_timedep_gridded_partic_prob_" + (float) minMag;
					String title = "Log10(Time Dep Participation Probs " + (float) +minMag;
					
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
					title += ")";

					MapPlotData plot = new MapPlotData(particCPT, logData, true,
							title, name);
					plot.subDirName = "gridded_time_dep_participation_prob_plots_"+(int)duration;
					
					// add metadata to this one
//					fw.write("ERF Basede Regional/Fault Probability Distributions\n");
//					fw.write("Generated on: "+df.format(new Date())+"\n");
//					fw.write("Generated by: "+ERFBasedRegionalMagProbPlot.class.getName()+"\n");
//					fw.write("\n");
//					fw.write("All data INCLUDES aftershocks. UCERF3 data uses default BPT averaging"
//							+ " method and UCERF3 Preferred Blend for the time dependence. This historical"
//							+ " open interval is set as "+FaultSystemSolutionERF.START_TIME_DEFAULT+" - 1875 = "
//							+(FaultSystemSolutionERF.START_TIME_DEFAULT-1875)+".\n");
					if (r == 0)
						plot.metadata = "ERF Based Time Dependend Gridded Participation Probability Plots\n"
							+"Generated on: "+df.format(new Date())+"\n"
							+"Generated by: "+TimeDepGriddedParticipationProbPlot.class.getName()+"\n"
							+"Forecast Duration: "+(int)duration+"\n"
							+"\n"
							+"All UCERF3 data INCLUDES aftershocks. UCERF2 data is from MeanUCERF2 and DOES"
								+ " NOT INCLUDE AFTERSHOCKS.\n"
							+"\n"
							+"UCERF3 data uses default BPT averaging method and UCERF3 Preferred Blend for "
								+"the time dependence. This historical open interval is set as "
								+FaultSystemSolutionERF.START_TIME_DEFAULT+" - 1875 = "
								+(FaultSystemSolutionERF.START_TIME_DEFAULT-1875)+".\n"
							+"\n"
							+"UCERF3 fault probabilities are spread over their respective fault polygon"
							+ " (and UCERF2 faults are not).\n";
					plots.add(plot);
					
					funcs = new XY_DataSetList();

					for (int i = 0; i < depDatas.size(); i++) {
						GeoDataSet subData = indepDatas.get(i).get(r);
						EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0d,
								subData.size(), 1d);
						for (int j = 0; j < subData.size(); j++)
							func.set(j, subData.get(j));

						funcs.add(func);
					}

					calc = new FractileCurveCalculator(
							funcs, weights.get(duration));

					GriddedGeoDataSet indepVals = new GriddedGeoDataSet(griddedRegion,
							true);
					meanDataFunc = calc.getMeanCurve();
					Preconditions.checkState(meanDataFunc.size() == indepVals.size());
					for (int i = 0; i < indepVals.size(); i++)
						indepVals.set(i, meanDataFunc.getY(i));

					// first plot UCERF2 on its own
					GriddedGeoDataSet logIndepVals = indepVals.copy();
					logIndepVals.log10();
					
					name = (int)duration+"_timeindep_gridded_partic_prob_" + (float) minMag;
					title = "Log10(Time Indep Participation Probs " + (float) +minMag;
					
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
					title += ")";

					plot = new MapPlotData(particCPT, logIndepVals,
							spacing, griddedRegion, true, title, name);
					plot.subDirName = "gridded_time_dep_participation_prob_plots_"+(int)duration;
					plots.add(plot);

					// now plot ratios
					GeoDataSet ratios = GeoDataSetMath.divide(data, indepVals);

//					ratios.log10();
					
					name = (int)duration+"_gridded_partic_u3_ratio_" + (float) minMag;
					title = "Time Dep/Indep Participation Prob Ratio " + (float) +minMag;
					
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
//					title += ")";

					plot = new MapPlotData(ratioCPT, ratios, spacing,
							griddedRegion, true, title, name);
					plot.subDirName = "gridded_time_dep_participation_prob_plots_"+(int)duration;
					plots.add(plot);
					
					// now UCERF2
					// UCERF2 Time Dep
					GriddedGeoDataSet logU2DepVals = meanU2DepData.copy();
					logU2DepVals.log10();
					
					name = (int)duration+"_u2_timedep_gridded_partic_prob_" + (float) minMag;
					title = "Log10(U2 Time Dep Participation Probs " + (float) +minMag;
					
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
					title += ")";

					plot = new MapPlotData(particCPT, logU2DepVals,
							spacing, griddedRegion, true, title, name);
					plot.subDirName = "gridded_time_dep_participation_prob_plots_"+(int)duration;
					plots.add(plot);
					// UCERF2 Time Indep
					GriddedGeoDataSet logU2IndepVals = meanU2IndepData.copy();
					logU2IndepVals.log10();
					
					name = (int)duration+"_u2_timeindep_gridded_partic_prob_" + (float) minMag;
					title = "Log10(U2 Time Indep Participation Probs " + (float) +minMag;
					
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
					title += ")";

					plot = new MapPlotData(particCPT, logU2IndepVals,
							spacing, griddedRegion, true, title, name);
					plot.subDirName = "gridded_time_dep_participation_prob_plots_"+(int)duration;
					plots.add(plot);
					
					// UCERF2 only ratios
					GeoDataSet u2Ratios = GeoDataSetMath.divide(meanU2DepData, meanU2IndepData);

//					ratios.log10();
					
					name = (int)duration+"_gridded_partic_u2_ratio_" + (float) minMag;
					title = "U2 Time Dep/Indep Participation Prob Ratio " + (float) +minMag;
					
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
//					title += ")";

					plot = new MapPlotData(ratioCPT, u2Ratios, spacing,
							griddedRegion, true, title, name);
					plot.subDirName = "gridded_time_dep_participation_prob_plots_"+(int)duration;
					plots.add(plot);
					
					// U3/U2 Time Dep Ratio
					GeoDataSet u3u2DepRatios = GeoDataSetMath.divide(data, meanU2DepData);

//					ratios.log10();
					
					name = (int)duration+"_gridded_partic_u3_u2_dep_ratio_" + (float) minMag;
					title = "Time Dep U3/U2 Participation Prob Ratio " + (float) +minMag;
					
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
//					title += ")";

					plot = new MapPlotData(ratioCPT, u3u2DepRatios, spacing,
							griddedRegion, true, title, name);
					plot.subDirName = "gridded_time_dep_participation_prob_plots_"+(int)duration;
					plots.add(plot);
					
					// U3/U2 Time Indep Ratio
					GeoDataSet u3u2IndepRatios = GeoDataSetMath.divide(indepVals, meanU2IndepData);

//					ratios.log10();
					
					name = (int)duration+"_gridded_partic_u3_u2_indep_ratio_" + (float) minMag;
					title = "Time Indep U3/U2 Participation Prob Ratio " + (float) +minMag;
					
					if (maxMag < 9) {
						name += "_" + (float) maxMag;
						title += "=>" + (float) maxMag;
					} else {
						name += "+";
						title += "+";
					}
//					title += ")";

					plot = new MapPlotData(ratioCPT, u3u2IndepRatios, spacing,
							griddedRegion, true, title, name);
					plot.subDirName = "gridded_time_dep_participation_prob_plots_"+(int)duration;
					plots.add(plot);

					// double[] stdNormVals = new double[values.length];
					//
					// for (int s=0; s<stdNormVals.length; s++) {
					// if (ucerf2Vals[s] == 0)
					// stdNormVals[s] = Double.NaN;
					// else
					// stdNormVals[s] = (values[s] - ucerf2Vals[s]) / stdDevs[s];
					// }
					//
					// name = "partic_diffs_norm_std_dev_"+(float)minMag;
					// title = "(U3mean - U2mean)/U3std "+(float)+minMag;
					// if (maxMag < 9) {
					// name += "_"+(float)maxMag;
					// title += "=>"+(float)maxMag;
					// } else {
					// name += "+";
					// title += "+";
					// }
					// // title += ")";
					//
					// if (multipleFMs) {
					// name = fm.getShortName()+"_"+name;
					// title = fm.getShortName()+" "+title;
					// }
					//
					// plots.add(new MapPlotData(logCPT, faults, stdNormVals,
					// region,
					// skipNans, title, name));
				}
			}

			debug(-1, "done finalizing");
		}

		@Override
		protected List<MapPlotData> getPlotData() {
			return plots;
		}

		@Override
		protected boolean usesERFs() {
			return true;
		}

		@Override
		protected boolean isApplyAftershockFilter() {
			return false;
		}

		@Override
		protected boolean isTimeDependent() {
			return true;
		}
	}

	public static class GriddedParticipationMapPlot extends MapBasedPlot {

		private List<double[]> ranges;
		private double spacing;

		private List<List<GeoDataSet>> particDatas;
		private List<List<GeoDataSet>> nuclDatas;
		private List<Double> weights;
		private GriddedRegion griddedRegion;

		private transient Map<FaultModels, FSSRupNodesCache> rupNodesCache = Maps.newHashMap();

		private List<MapPlotData> plots;

		public static List<double[]> getDefaultRanges() {
			List<double[]> ranges = Lists.newArrayList();

			ranges.add(toArray(5d, 9d));
			ranges.add(toArray(6.7d, 9d));
			ranges.add(toArray(7.7d, 9d));
			ranges.add(toArray(8d, 9d));

			return ranges;
		}

		private static double[] toArray(double... vals) {
			return vals;
		}

		private transient BranchWeightProvider weightProvider;

		public GriddedParticipationMapPlot(BranchWeightProvider weightProvider) {
			this(weightProvider, 0.1d);
		}

		public GriddedParticipationMapPlot(BranchWeightProvider weightProvider,
				double spacing) {
			this(weightProvider, getDefaultRanges(), spacing);
		}

		public GriddedParticipationMapPlot(BranchWeightProvider weightProvider,
				List<double[]> ranges, double spacing) {
			this.weightProvider = weightProvider;
			this.ranges = ranges;
			this.spacing = spacing;
			griddedRegion = new CaliforniaRegions.RELM_TESTING_GRIDDED(spacing);

			particDatas = Lists.newArrayList();
			nuclDatas = Lists.newArrayList();
			weights = Lists.newArrayList();
		}

		@Override
		protected String getPlotDataFileName() {
			return "gridded_participation_plots.xml";
		}

		@Override
		protected void processSolution(LogicTreeBranch branch,
				InversionFaultSystemSolution sol, int solIndex) {
			processERF(branch, new FaultSystemSolutionERF(sol), 0);
		}

		@Override
		protected void processERF(LogicTreeBranch branch,
				FaultSystemSolutionERF erf, int solIndex) {

			debug(solIndex, "cache check");
			FaultModels fm = branch.getValue(FaultModels.class);
			synchronized (this) {
				if (!rupNodesCache.containsKey(fm)) {
					FSSRupNodesCache cache = new FSSRupNodesCache();
					rupNodesCache.put(fm, cache);
				}
			}
			debug(solIndex, "cache check done");
			FSSRupNodesCache cache = rupNodesCache.get(fm);
			List<GeoDataSet> particData = Lists.newArrayList();
			List<GeoDataSet> nuclData = Lists.newArrayList();
			for (int i = 0; i < ranges.size(); i++) {
				double[] range = ranges.get(i);
				double minMag = range[0];
				double maxMag = range[1];
				debug(solIndex, "calc partic range " + i);
				particData.add(ERF_Calculator.getParticipationRatesInRegion(erf,
							griddedRegion, minMag, maxMag, cache));
				debug(solIndex, "done partic range " + i);
			}
			erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.ONLY);
			erf.updateForecast();
			for (int i = 0; i < ranges.size(); i++) {
				double[] range = ranges.get(i);
				double minMag = range[0];
				double maxMag = range[1];
				if (minMag > 5d)
					break;
				debug(solIndex, "calc nucl range " + i);
				nuclData.add(ERF_Calculator.getNucleationRatesInRegion(erf,
							griddedRegion, minMag, maxMag, cache));
				debug(solIndex, "done nucl range " + i);
			}
			erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
			erf.updateForecast();
			debug(solIndex, "archive");
			synchronized (this) {
				particDatas.add(particData);
				nuclDatas.add(nuclData);
				weights.add(weightProvider.getWeight(branch));
			}
			debug(solIndex, "archive done");
		}

		@Override
		protected void combineDistributedCalcs(
				Collection<CompoundFSSPlots> otherCalcs) {
			for (CompoundFSSPlots otherCalc : otherCalcs) {
				GriddedParticipationMapPlot o = (GriddedParticipationMapPlot) otherCalc;
				particDatas.addAll(o.particDatas);
				nuclDatas.addAll(o.nuclDatas);
				weights.addAll(o.weights);
			}
		}

		@Override
		protected void doFinalizePlot() {
			debug(-1, "Finalizing plot");
			boolean debug = false;

			plots = Lists.newArrayList();

			CPT particCPT = FaultBasedMapGen.getParticipationCPT()
					.rescale(-5, -1);
			CPT nuclCPT = FaultBasedMapGen.getParticipationCPT()
					.rescale(-6, -1);
			CPT ratioCPT = (CPT) FaultBasedMapGen.getLogRatioCPT().clone();
			ratioCPT.setNanColor(Color.WHITE);
			ratioCPT.setAboveMaxColor(Color.BLACK);

			MeanUCERF2 ucerf2 = new MeanUCERF2();
			ucerf2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME,
					UCERF2.PROB_MODEL_POISSON);
			ucerf2.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME,
					UCERF2.FULL_DDW_FLOATER);
			ucerf2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
			ucerf2.setParameter(UCERF2.BACK_SEIS_RUP_NAME,
					UCERF2.BACK_SEIS_RUP_POINT);
			ucerf2.getTimeSpan().setDuration(1d);
			ucerf2.updateForecast();

			for (int c = 0; c < ranges.size()*2; c++) {
				boolean nucleation = c >= ranges.size();
				int r = c % ranges.size();
				
				if (nucleation && r == 0) {
					debug(-1, "Setting up UCERF2 comp erf for only back seis");
					ucerf2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_ONLY);
					ucerf2.updateForecast();
				}
				
				CPT cpt;
				List<List<GeoDataSet>> datas;
				if (nucleation) {
					datas = nuclDatas;
					cpt = nuclCPT;
				} else {
					datas = particDatas;
					cpt = particCPT;
				}
				
				if (datas.get(0).size() <= r) {
					debug(-1, "SKIPPING r="+r+", nucleation="+nucleation);
					continue;
				}
				debug(-1, "Building r="+r+", nucleation="+nucleation);
				
				double[] range = ranges.get(r);
				double minMag = range[0];
				double maxMag = range[1];

				XY_DataSetList funcs = new XY_DataSetList();

				for (int i = 0; i < datas.size(); i++) {
					GeoDataSet data = datas.get(i).get(r);
					EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0d,
							data.size(), 1d);
					for (int j = 0; j < data.size(); j++)
						func.set(j, data.get(j));

					funcs.add(func);
				}

				FractileCurveCalculator calc = new FractileCurveCalculator(
						funcs, weights);

				GriddedGeoDataSet data = new GriddedGeoDataSet(griddedRegion,
						true);
				AbstractXY_DataSet meanDataFunc = calc.getMeanCurve();
				Preconditions.checkState(meanDataFunc.size() == data.size());
				for (int i = 0; i < data.size(); i++)
					data.set(i, meanDataFunc.getY(i));

				double[] weightsArray = Doubles.toArray(weights);

				data = new GriddedGeoDataSet(griddedRegion, true);
				for (int i = 0; i < data.size(); i++) {
					double[] vals = new double[datas.size()];
					for (int j = 0; j < datas.size(); j++)
						vals[j] = datas.get(j).get(r).get(i);
					data.set(i, FaultSystemSolutionFetcher.calcScaledAverage(
							vals, weightsArray));
				}

				if (debug && r == 0) {
					for (int i = 0; i < datas.size() && i < 10; i++) {
						GeoDataSet subData = datas.get(i).get(r).copy();
						subData.log10();
						plots.add(new MapPlotData(cpt, subData,
								spacing, griddedRegion, true,
								"Sub Participation 5+ " + i, "sub_partic_5+_"
										+ i));
					}
				}

				// take log10
				GriddedGeoDataSet logData = data.copy();
				logData.log10();

				String name;
				String title;
				if (nucleation) {
					name = "gridded_sub_seis_nucl_rates_" + (float) minMag;
					title = "Log10(Sub Seis Nucleation Rates " + (float) +minMag;
				} else {
					name = "gridded_partic_rates_" + (float) minMag;
					title = "Log10(Participation Rates " + (float) +minMag;
				}
				
				if (maxMag < 9) {
					name += "_" + (float) maxMag;
					title += "=>" + (float) maxMag;
				} else {
					name += "+";
					title += "+";
				}
				title += ")";

				MapPlotData plot = new MapPlotData(cpt, logData, true,
						title, name);
				plot.subDirName = "gridded_participation_plots";
				plots.add(plot);

				GriddedGeoDataSet ucerf2Vals;
				if (nucleation)
					ucerf2Vals = ERF_Calculator.getNucleationRatesInRegion(
							ucerf2, griddedRegion, range[0], range[1]);
				else
					ucerf2Vals = ERF_Calculator.getParticipationRatesInRegion(
							ucerf2, griddedRegion, range[0], range[1]);

				// first plot UCERF2 on its own
				GriddedGeoDataSet ucerf2LogVals = ucerf2Vals.copy();
				ucerf2LogVals.log10();

				if (nucleation) {
					name = "gridded_sub_seis_nucl_rates_ucerf2_" + (float) minMag;
					title = "Log10(UCERF2 Sub Seis Nucleation Rates " + (float) +minMag;
				} else {
					name = "gridded_partic_rates_ucerf2_" + (float) minMag;
					title = "Log10(UCERF2 Participation Rates " + (float) +minMag;
				}
				
				if (maxMag < 9) {
					name += "_" + (float) maxMag;
					title += "=>" + (float) maxMag;
				} else {
					name += "+";
					title += "+";
				}
				title += ")";

				plot = new MapPlotData(cpt, ucerf2LogVals,
						spacing, griddedRegion, true, title, name);
				plot.subDirName = "gridded_participation_plots";
				plots.add(plot);

				// now plot ratios
				GeoDataSet ratios = GeoDataSetMath.divide(data, ucerf2Vals);

				ratios.log10();

				if (nucleation) {
					name = "gridded_sub_seis_nucl_ratio_" + (float) minMag;
					title = "Log10(Sub Seis Nucleation Ratios " + (float) +minMag;
				} else {
					name = "gridded_partic_ratio_" + (float) minMag;
					title = "Log10(Participation Ratios " + (float) +minMag;
				}
				
				if (maxMag < 9) {
					name += "_" + (float) maxMag;
					title += "=>" + (float) maxMag;
				} else {
					name += "+";
					title += "+";
				}
				title += ")";

				plot = new MapPlotData(ratioCPT, ratios, spacing,
						griddedRegion, true, title, name);
				plot.subDirName = "gridded_participation_plots";
				plots.add(plot);

				// double[] stdNormVals = new double[values.length];
				//
				// for (int s=0; s<stdNormVals.length; s++) {
				// if (ucerf2Vals[s] == 0)
				// stdNormVals[s] = Double.NaN;
				// else
				// stdNormVals[s] = (values[s] - ucerf2Vals[s]) / stdDevs[s];
				// }
				//
				// name = "partic_diffs_norm_std_dev_"+(float)minMag;
				// title = "(U3mean - U2mean)/U3std "+(float)+minMag;
				// if (maxMag < 9) {
				// name += "_"+(float)maxMag;
				// title += "=>"+(float)maxMag;
				// } else {
				// name += "+";
				// title += "+";
				// }
				// // title += ")";
				//
				// if (multipleFMs) {
				// name = fm.getShortName()+"_"+name;
				// title = fm.getShortName()+" "+title;
				// }
				//
				// plots.add(new MapPlotData(logCPT, faults, stdNormVals,
				// region,
				// skipNans, title, name));
			}
			debug(-1, "done finalizing");
		}

		@Override
		protected List<MapPlotData> getPlotData() {
			return plots;
		}

		@Override
		protected boolean usesERFs() {
			return true;
		}

		@Override
		protected boolean isApplyAftershockFilter() {
			return true;
		}

	}
	
	/**
	 * Cache for gridded participation rate plots to speed things up.
	 * @author kevin
	 *
	 */
	public static class FSSRupNodesCache implements RupNodesCache {
		
		private ConcurrentMap<Region, ConcurrentMap<Integer, int[]>> nodesMap = Maps
				.newConcurrentMap();
		private ConcurrentMap<Region, ConcurrentMap<Integer, double[]>> fractsMap = Maps
				.newConcurrentMap();

		@Override
		public int[] getNodesForRup(ProbEqkSource source, EqkRupture rup,
				int srcIndex, int rupIndex, GriddedRegion region) {
			RuptureSurface surf = rup.getRuptureSurface();
			if (surf instanceof CompoundSurface) {
				int invIndex = getInversionIndex(source);
				ConcurrentMap<Integer, int[]> regMap = nodesMap.get(region);
				if (regMap == null) {
					regMap = Maps.newConcurrentMap();
					nodesMap.putIfAbsent(region, regMap);
					ConcurrentMap<Integer, double[]> fractMap = Maps.newConcurrentMap();
					fractsMap.putIfAbsent(region, fractMap);
					// in case another thread put it in
					// first
					regMap = nodesMap.get(region);
				}
				int[] nodes = regMap.get(invIndex);
				if (nodes == null) {
					ConcurrentMap<Integer, double[]> fractMap = fractsMap.get(region);
					List<Integer> nodesList = Lists.newArrayList();
					List<Double> fractsList = Lists.newArrayList();
					LocationList surfLocs = surf.getEvenlyDiscritizedListOfLocsOnSurface();
					double ptFract = 1d/(double)surfLocs.size();
					for(Location loc : surfLocs) {
						int index = region.indexForLocation(loc);
						if(index >= 0) {
							int indexInList = nodesList.indexOf(index);
							if (indexInList >= 0) {
								fractsList.set(indexInList, fractsList.get(indexInList)+ptFract);
							} else {
								nodesList.add(index);
								fractsList.add(ptFract);
							}
						}
					}
					nodes = Ints.toArray(nodesList);
					double[] fracts = Doubles.toArray(fractsList);
					regMap.putIfAbsent(invIndex, nodes);
					fractMap.putIfAbsent(invIndex, fracts);
				}
				return nodes;
			}
			return null;
		}

		@Override
		public double[] getFractsInNodesForRup(ProbEqkSource source,
				EqkRupture rup, int srcIndex, int rupIndex, GriddedRegion region) {
			RuptureSurface surf = rup.getRuptureSurface();
			if (surf instanceof CompoundSurface) {
				int invIndex = getInversionIndex(source);
				ConcurrentMap<Integer, double[]> fractMap = fractsMap.get(region);
				double[] fracts = fractMap.get(invIndex);
				if (fracts == null) {
					// not cached yet
					getNodesForRup(source, rup, srcIndex, rupIndex, region);
					fracts = fractMap.get(invIndex);
				}
				return fracts;
			}
			return null;
		}
		
	}

	/**
	 * Data for a map based plot which can be written to/loaded from an XML file.
	 * @author kevin
	 *
	 */
	public static class MapPlotData implements XMLSaveable, Serializable {

		private static final String XML_METADATA_NAME = "FaultBasedMap";

		private CPT cpt;
		private List<LocationList> faults;
		private double[] faultValues;
		private GeoDataSet griddedData;
		private double spacing;
		private Region region;
		private boolean skipNans;
		private String label;
		private String fileName;
		private String subDirName;
		
		private String metadata;

		public MapPlotData(CPT cpt, List<LocationList> faults,
				double[] faultValues, Region region, boolean skipNans,
				String label, String fileName) {
			this(cpt, faults, faultValues, null, 1d, region, skipNans, label,
					fileName);
		}

		public MapPlotData(CPT cpt, GriddedGeoDataSet griddedData,
				boolean skipNans, String label, String fileName) {
			this(cpt, griddedData, griddedData.getRegion().getSpacing(),
					griddedData.getRegion(), skipNans, label, fileName);
		}

		public MapPlotData(CPT cpt, GeoDataSet griddedData, double spacing,
				Region region, boolean skipNans, String label, String fileName) {
			this(cpt, null, null, griddedData, spacing, region, skipNans,
					label, fileName);
		}

		public MapPlotData(CPT cpt, List<LocationList> faults,
				double[] faultValues, GeoDataSet griddedData, double spacing,
				Region region, boolean skipNans, String label, String fileName) {
			this.cpt = cpt;
			this.faults = faults;
			this.griddedData = griddedData;
			this.spacing = spacing;
			this.faultValues = faultValues;
			this.region = region;
			this.skipNans = skipNans;
			this.label = label;
			this.fileName = fileName;
		}

		public static MapPlotData fromXMLMetadata(Element xml) {
			CPT cpt = CPT.fromXMLMetadata(xml.element(CPT.XML_METADATA_NAME));

			List<LocationList> faults;
			double[] values;
			GeoDataSet griddedData;
			double spacing = 1;

			Element geoEl = xml.element("GeoDataSet");
			if (geoEl != null) {
				// gridded
				values = null;
				faults = null;

				spacing = Double.parseDouble(geoEl.attributeValue("spacing"));

				Iterator<Element> it = geoEl.elementIterator("Node");
				List<Location> locs = Lists.newArrayList();
				List<Double> nodeVals = Lists.newArrayList();
				while (it.hasNext()) {
					Element nodeElem = it.next();
					locs.add(Location.fromXMLMetadata(nodeElem
							.element(Location.XML_METADATA_NAME)));
					double nodeVal = Double.parseDouble(nodeElem
							.attributeValue("value"));
					// if (Double.isNaN(nodeVal))
					// System.out.println("NaN!!!!");
					nodeVals.add(nodeVal);
				}
				griddedData = new ArbDiscrGeoDataSet(true);
				for (int i = 0; i < locs.size(); i++)
					griddedData.set(locs.get(i), nodeVals.get(i));
			} else {
				// fault based
				griddedData = null;

				faults = Lists.newArrayList();
				List<Double> valuesList = Lists.newArrayList();
				Iterator<Element> it = xml.elementIterator("Fault");
				while (it.hasNext()) {
					Element faultElem = it.next();
					faults.add(LocationList.fromXMLMetadata(faultElem
							.element(LocationList.XML_METADATA_NAME)));
					valuesList.add(Double.parseDouble(faultElem
							.attributeValue("value")));
				}
				values = Doubles.toArray(valuesList);
			}

			Region region = Region.fromXMLMetadata(xml
					.element(Region.XML_METADATA_NAME));

			boolean skipNans = Boolean.parseBoolean(xml
					.attributeValue("skipNans"));
			String label = xml.attributeValue("label");
			String fileName = xml.attributeValue("fileName");
			Attribute subDirName = xml.attribute("subDir");
			MapPlotData data = new MapPlotData(cpt, faults, values,
					griddedData, spacing, region, skipNans, label, fileName);
			if (subDirName != null)
				data.subDirName = subDirName.getStringValue();
			Attribute metadataAtt = xml.attribute("metadata");
			if (metadataAtt != null) {
				data.metadata = metadataAtt.getStringValue().replaceAll("<br>", "\n");
			}

			return data;
		}

		@Override
		public Element toXMLMetadata(Element root) {
			Element xml = root.addElement(XML_METADATA_NAME);

			cpt.toXMLMetadata(xml);

			if (faults != null) {
				for (int i = 0; i < faults.size(); i++) {
					Element faultElem = xml.addElement("Fault");
					faultElem.addAttribute("value", faultValues[i] + "");
					faults.get(i).toXMLMetadata(faultElem);
				}
			}
			if (griddedData != null) {
				Element geoEl = xml.addElement("GeoDataSet");
				geoEl.addAttribute("spacing", spacing + "");
				for (int i = 0; i < griddedData.size(); i++) {
					Location loc = griddedData.getLocation(i);
					double val = griddedData.get(i);
					Element nodeEl = geoEl.addElement("Node");
					nodeEl.addAttribute("value", val + "");
					loc.toXMLMetadata(nodeEl);
				}
			}

			if (region instanceof GriddedRegion) {
				GriddedRegion gridded = (GriddedRegion) region;
				if (gridded.getSpacing() <= 0.11)
					new Region(region.getBorder(), null).toXMLMetadata(xml);
				else
					new Region(new Location(gridded.getMaxGridLat(),
							gridded.getMaxGridLon()), new Location(
							gridded.getMinGridLat(), gridded.getMinGridLon()))
							.toXMLMetadata(xml);
			} else {
				region.toXMLMetadata(xml);
			}

			xml.addAttribute("skipNans", skipNans + "");
			xml.addAttribute("label", label);
			xml.addAttribute("fileName", fileName);
			if (subDirName != null)
				xml.addAttribute("subDir", subDirName);
			if (metadata != null && !metadata.isEmpty())
				xml.addAttribute("metadata", metadata.replaceAll("\n", "<br>"));

			return root;
		}

		public GeoDataSet getGriddedData() {
			return griddedData;
		}

		public String getLabel() {
			return label;
		}

		public CPT getCPT() {
			return cpt;
		}

		public Region getRegion() {
			return region;
		}

		public String getFileName() {
			return fileName;
		}

		public String getSubDirName() {
			return subDirName;
		}
		
		public List<LocationList> getFaults() {
			return faults;
		}
		
		public double[] getFaultValues() {
			return faultValues;
		}
		
		public String getMetadata() {
			return metadata;
		}
		
		public double getSpacing() {
			return spacing;
		}
	}

	/**
	 * Subclass for map based plots. Map data is written to an XML file for distributed
	 * calculations on clusters.
	 * @author kevin
	 *
	 */
	public static abstract class MapBasedPlot extends CompoundFSSPlots {

		protected abstract List<MapPlotData> getPlotData();

		protected abstract String getPlotDataFileName();

		protected double[] getWeightedAvg(int numFaults,
				List<double[]> valuesList, List<Double> weightsList) {

			double[] weights = Doubles.toArray(weightsList);

			double[] values = new double[numFaults];
			for (int i = 0; i < numFaults; i++) {
				double[] faultVals = new double[weights.length];
				for (int s = 0; s < weights.length; s++)
					faultVals[s] = valuesList.get(s)[i];
				values[i] = FaultSystemSolutionFetcher.calcScaledAverage(
						faultVals, weights);
			}

			return values;
		}

		protected double[] getMins(int numFaults,
				List<double[]> valuesList) {
			double[] values = new double[numFaults];
			int numSols = valuesList.size();
			for (int i = 0; i < numFaults; i++) {
				double[] faultVals = new double[numSols];
				for (int s = 0; s < numSols; s++)
					faultVals[s] = valuesList.get(s)[i];
				values[i] = StatUtils.min(faultVals);
			}

			return values;
		}

		protected double[] getMaxs(int numFaults,
				List<double[]> valuesList) {
			double[] values = new double[numFaults];
			int numSols = valuesList.size();
			for (int i = 0; i < numFaults; i++) {
				double[] faultVals = new double[numSols];
				for (int s = 0; s < numSols; s++)
					faultVals[s] = valuesList.get(s)[i];
				values[i] = StatUtils.max(faultVals);
			}

			return values;
		}

		protected double[] getStdDevs(int numFaults, List<double[]> valuesList) {

			double[] stdDevs = new double[numFaults];
			for (int i = 0; i < numFaults; i++) {
				double[] faultVals = new double[valuesList.size()];
				for (int s = 0; s < valuesList.size(); s++)
					faultVals[s] = valuesList.get(s)[i];
				stdDevs[i] = Math.sqrt(StatUtils.variance(faultVals));
			}

			return stdDevs;
		}

		public void writePlotData(File dir) throws IOException {
			Document doc = XMLUtils.createDocumentWithRoot();
			Element root = doc.getRootElement();

			for (MapPlotData data : getPlotData())
				data.toXMLMetadata(root);

			File dataFile = new File(dir, getPlotDataFileName());
			XMLUtils.writeDocumentToFile(dataFile, doc);
		}

		public static List<MapPlotData> loadPlotData(File file)
				throws MalformedURLException, DocumentException {
			Document doc = XMLUtils.loadDocument(file);
			Element root = doc.getRootElement();

			List<MapPlotData> plots = Lists.newArrayList();

			Iterator<Element> it = root
					.elementIterator(MapPlotData.XML_METADATA_NAME);
			while (it.hasNext())
				plots.add(MapPlotData.fromXMLMetadata(it.next()));

			return plots;
		}

		public void makeMapPlots(File dir, String prefix)
				throws GMT_MapException, RuntimeException, IOException {
			makeMapPlots(dir, prefix, this.getPlotData());
		}

		public static void makeMapPlot(File dir, String prefix, MapPlotData plot)
				throws GMT_MapException, RuntimeException, IOException {
			makeMapPlots(dir, prefix, Lists.newArrayList(plot));
		}

		public static void makeMapPlots(File dir, String prefix,
				List<MapPlotData> plots) throws GMT_MapException,
				RuntimeException, IOException {
			// if (plots.size() < 30) {
			System.out.println("*** Making " + plots.size() + " Map Plots ***");
			for (MapPlotData plot : plots) {
				doMakePlot(dir, prefix, plot);
			}
			// } else {
			// int numThreads = 10;
			// System.out.println("*** Making "+plots.size()
			// +" Map Plots ("+numThreads+" THREADS) ***");
			// ExecutorService ex = Executors.newFixedThreadPool(numThreads);
			// CompletionService<Integer> ecs = new
			// ExecutorCompletionService<Integer>(ex);
			//
			// for (MapPlotData plot : plots) {
			// ecs.submit(new MapPlotCallable(dir, prefix, plot));
			// }
			//
			// ex.shutdown();
			//
			// for (int i=0; i<plots.size(); i++) {
			// try {
			// ecs.take();
			// } catch (InterruptedException e) {
			// ExceptionUtils.throwAsRuntimeException(e);
			// }
			// }
			// }
		}

		private static void doMakePlot(File dir, String prefix, MapPlotData plot)
				throws GMT_MapException, RuntimeException, IOException {
			String plotPrefix;
			if (prefix != null && !prefix.isEmpty() && plot.subDirName == null)
				plotPrefix = prefix + "_";
			else
				plotPrefix = "";
			plotPrefix += plot.fileName;
			File writeDir = dir;
			if (plot.subDirName != null)
				writeDir = new File(dir, plot.subDirName);
			if (!writeDir.exists())
				writeDir.mkdir();
			System.out.println("Making fault plot with title: " + plot.label);
			if (plot.griddedData == null)
				FaultBasedMapGen.makeFaultPlot(plot.cpt, plot.faults,
						plot.faultValues, plot.region, writeDir, plotPrefix,
						false, plot.skipNans, plot.label);
			else
				FaultBasedMapGen.plotMap(writeDir, plotPrefix, false,
						FaultBasedMapGen.buildMap(plot.cpt, null, null,
								plot.griddedData, plot.spacing, plot.region,
								plot.skipNans, plot.label));
			String metadata = plot.getMetadata();
			if (metadata != null && !metadata.isEmpty() && plot.subDirName != null) {
				File metadataFile = new File(writeDir, "metadata.txt");
				System.out.println("Writing plot metadata to: "+metadataFile.getAbsolutePath());
				FileWriter fw = new FileWriter(metadataFile);
				fw.write(metadata+"\n");
				fw.close();
			}
			System.out.println("DONE.");
		}
		
		protected void writeExtraData(File dir, String prefix) {
			// do nothing unless overridden
		}

	}

	/**
	 * Creates PlotCurveCharactersitcs for the given number of fractiles. Will be returned with
	 * fractiles first, then mean/min/max. Matches output from getFractiles(...).
	 * 
	 * @param color
	 * @param numFractiles
	 * @return
	 */
	public static List<PlotCurveCharacterstics> getFractileChars(Color color,
			int numFractiles) {
		return getFractileChars(color, color, numFractiles);
	}
	
	/**
	 * Creates PlotCurveCharactersitcs for the given number of fractiles. Will be returned with
	 * fractiles first, then mean/min/max. Matches output from getFractiles(...). This method allows for a different
	 * fractile color.
	 * @param color
	 * @param fractileColor
	 * @param numFractiles
	 * @return
	 */
	public static List<PlotCurveCharacterstics> getFractileChars(Color color, Color fractileColor,
			int numFractiles) {
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		PlotCurveCharacterstics thinChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, 1f, color);
		PlotCurveCharacterstics medChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, 2f, fractileColor);
		PlotCurveCharacterstics thickChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, 4f, color);

		for (int i = 0; i < numFractiles; i++)
			chars.add(medChar);
		chars.add(thickChar);
		chars.add(thinChar);
		chars.add(thinChar);

		return chars;
	}

	/**
	 * Called once for each solution unless ERF based
	 * 
	 * @param branch
	 * @param sol
	 * @param solIndex
	 */
	protected abstract void processSolution(LogicTreeBranch branch,
			InversionFaultSystemSolution sol, int solIndex);

	/**
	 * This is used when doing distributed calculations. This method will be
	 * called on the root node to combine all of the other plots with this
	 * one.
	 * 
	 * @param otherCalcs
	 */
	protected abstract void combineDistributedCalcs(
			Collection<CompoundFSSPlots> otherCalcs);
	
	/**
	 * Can be overridded to flush results after calculation has completed but before distributed results
	 * are combined or plots finalized
	 */
	protected void flushResults() {
		
	}

	/**
	 * Called at the end to finalize the plot. This should handle assembly of PlotSpecs.
	 */
	protected abstract void doFinalizePlot();
	
	/**
	 * Called at the end to finalize the plot. This should handle assembly of PlotSpecs.
	 */
	protected void finalizePlot() {
		Stopwatch watch = Stopwatch.createStarted();
		doFinalizePlot();
		watch.stop();
		addToFinalizeTimeCount(watch.elapsed(TimeUnit.MILLISECONDS));
	}

	protected boolean usesERFs() {
		return false;
	}
	
	/**
	 * If true, gardner knopoff filter will be applied to ERF before passing in to plot
	 * @return
	 */
	protected boolean isApplyAftershockFilter() {
		return false;
	}
	
	/**
	 * If true, gardner knopoff filter will be applied to ERF before passing in to plot
	 * @return
	 */
	protected boolean isTimeDependent() {
		return false;
	}

	/**
	 * This will be overridden for each plot which uses ERFs
	 * @param branch
	 * @param erf
	 * @param solIndex
	 */
	protected void processERF(LogicTreeBranch branch,
			FaultSystemSolutionERF erf, int solIndex) {
		// do nothing unless overridden
		if (usesERFs())
			throw new IllegalStateException(
					"Must be overridden if usesERFs() == true");
		else
			throw new IllegalStateException(
					"Should not be called if usesERFs() == false");
	}

	private static String hostname;

	private synchronized static String getHostname() {
		if (hostname == null) {
			try {
				hostname = java.net.InetAddress.getLocalHost().getHostName();
//				System.out.println(hostname);
				if (hostname.contains("."))
					hostname = hostname.split("\\.")[0];
//				System.out.println(hostname);
			} catch (UnknownHostException e) {
			}
		}
		return hostname;
	}

	protected static final SimpleDateFormat df = new SimpleDateFormat(
			"HH:mm:ss.SSS");

	private String className;

	private synchronized String getClassName() {
		if (className == null) {
			className = ClassUtils.getClassNameWithoutPackage(this.getClass());
			while (className.contains("$"))
				className = className.substring(className.indexOf("$")+1);
		}
		return className;
	}

	protected void debug(int solIndex, String message) {
		System.out.println("[" + df.format(new Date()) + " (" + getHostname() + ") "
				+ getClassName() + "(" + solIndex + ")]: " + message);
	}
	
	private long computeTimeMillis = 0;
	
	protected synchronized void addToComputeTimeCount(long time) {
		computeTimeMillis += time;
	}
	
	protected long getComputeTimeCount() {
		return computeTimeMillis;
	}
	
	private long finalizeTimeMillis = 0;
	
	protected synchronized void addToFinalizeTimeCount(long time) {
		finalizeTimeMillis += time;
	}
	
	protected long getFinalizeTimeCount() {
		return finalizeTimeMillis;
	}

	/**
	 * This builds the plot individually (without utilizing efficiencies of
	 * working on multiple plots at once as you iterate over the solutions).
	 * 
	 * @param fetcher
	 */
	public void buildPlot(FaultSystemSolutionFetcher fetcher) {
		ArrayList<CompoundFSSPlots> plots = new ArrayList<CompoundFSSPlots>();
		plots.add(this);
		batchPlot(plots, fetcher);
	}

	/**
	 * This builds multiple plots at once, only iterating through the solutions
	 * once. This should be much faster than calling buildPlot() on each plot.
	 * 
	 * @param plots
	 * @param fetcher
	 */
	public static void batchPlot(Collection<CompoundFSSPlots> plots,
			FaultSystemSolutionFetcher fetcher) {
		int threads = Runtime.getRuntime().availableProcessors();
		threads *= 3;
		threads /= 4;
		if (threads < 1)
			threads = 1;
		batchPlot(plots, fetcher, threads);
	}

	// protected static class RupSetCacheManager {
	//
	// private Cache<String, SimpleFaultSystemRupSet> cache;
	// private ConcurrentMap<FaultModels, SimpleFaultSystemRupSet> fmCache =
	// Maps.newConcurrentMap();
	//
	// public RupSetCacheManager(int maxNum) {
	// cache = CacheBuilder.newBuilder().maximumSize(maxNum).build();
	// }
	//
	// public void cache(LogicTreeBranch branch, FaultSystemSolution sol) {
	// String cacheName = branch.getValue(FaultModels.class).getShortName()+"_"
	// +branch.getValue(DeformationModels.class).getShortName()+"_"
	// +branch.getValue(ScalingRelationships.class).getShortName();
	//
	// SimpleFaultSystemRupSet cacheMatch = cache.getIfPresent(cacheName);
	//
	// if (cacheMatch != null) {
	// sol.copyCacheFrom(cacheMatch);
	// return;
	// }
	//
	// SimpleFaultSystemRupSet fmMatch =
	// fmCache.get(branch.getValue(FaultModels.class));
	// if (fmMatch != null)
	// sol.copyCacheFrom(fmMatch);
	//
	// SimpleFaultSystemRupSet rupSet;
	// if (sol instanceof SimpleFaultSystemSolution) {
	// rupSet =
	// SimpleFaultSystemRupSet.toSimple(((SimpleFaultSystemSolution)sol).getRupSet());
	// } else {
	//
	// }
	// }
	// }

	/**
	 * Plotting task to be executed - represents all plots for a given logic tree branch.
	 * 
	 * @author kevin
	 *
	 */
	protected static class PlotSolComputeTask implements Task {

		private Collection<CompoundFSSPlots> plots;
		private FaultSystemSolutionFetcher fetcher;
		private LogicTreeBranch branch;
		private boolean mpj;
		private FaultSystemSolutionERF erf;
		private int index;
		
		private long overheadMillis;

		public PlotSolComputeTask(Collection<CompoundFSSPlots> plots,
				FaultSystemSolutionFetcher fetcher, LogicTreeBranch branch,
				int index) {
			this(plots, fetcher, branch, false, index);
		}

		public PlotSolComputeTask(Collection<CompoundFSSPlots> plots,
				FaultSystemSolutionFetcher fetcher, LogicTreeBranch branch,
				boolean mpj, int index) {
			this.plots = plots;
			this.fetcher = fetcher;
			this.branch = branch;
			this.mpj = mpj;
			this.index = index;
		}

		private void debug(String message) {
			System.out.println("["+df.format(new Date())+" ("+getHostname()+") PlotSolComputeTask]: "
					+message+" ["+getMemoryDebug()+"]");
		}

		private String getMemoryDebug() {
			System.gc();
			Runtime rt = Runtime.getRuntime();
			long totalMB = rt.totalMemory() / 1024 / 1024;
			long freeMB = rt.freeMemory() / 1024 / 1024;
			long usedMB = totalMB - freeMB;
			return "mem t/u/f: "+totalMB+"/"+usedMB+"/"+freeMB;
		}

		@Override
		public void compute() {
			try {
				Stopwatch overheadWatch = Stopwatch.createStarted();
				debug("Fetching solution for branch: "+branch);
				InversionFaultSystemSolution sol = fetcher.getSolution(branch);

				overheadWatch.stop();

				for (CompoundFSSPlots plot : plots) {
					Stopwatch computeWatch = Stopwatch.createUnstarted();
					if (plot.usesERFs()) {
						// if it's an ERF plot we need to make sure the ERF has been built and set
						// any parameters
						overheadWatch.start();
						if (erf == null) {
							debug("Building ERF");
							erf = new FaultSystemSolutionERF(sol);
						}
						// some plots want the aftershock filter and some don't
						// make sure that the ERF will be correct for this plot
						erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, plot.isApplyAftershockFilter());
						if (plot.isTimeDependent()) {
							erf.getParameter(ProbabilityModelParam.NAME).setValue(
									ProbabilityModelOptions.U3_PREF_BLEND);
							erf.setParameter(HistoricOpenIntervalParam.NAME,
									(double)(FaultSystemSolutionERF.START_TIME_DEFAULT-1875));
							erf.setParameter(BPTAveragingTypeParam.NAME,
									BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE);
						} else {
							erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
						}
						erf.updateForecast();
						overheadWatch.stop();
						debug("Processing ERF plot: "
								+ ClassUtils.getClassNameWithoutPackage(plot
										.getClass()));
						computeWatch.start();
						plot.processERF(branch, erf, index);
						computeWatch.stop();
					} else {
						// 
						debug("Processing Regular plot: "
								+ ClassUtils.getClassNameWithoutPackage(plot
										.getClass()));
						computeWatch.start();
						plot.processSolution(branch, sol, index);
						computeWatch.stop();
					}
					plot.addToComputeTimeCount(computeWatch.elapsed(TimeUnit.MILLISECONDS));
				}
				debug("DONE");
				erf = null;
				overheadMillis = overheadWatch.elapsed(TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				debug("EXCEPTION: "+e.getClass()+": "+e.getMessage());
				e.printStackTrace();
				if (mpj)
					MPJTaskCalculator.abortAndExit(1);
				else
					System.exit(1);
			}
		}

	}

	/**
	 * Calculates all of the plots for the given FaultSystemSolutionFetcher with the given
	 * number of threads.
	 * @param plots
	 * @param fetcher
	 * @param threads
	 */
	public static void batchPlot(Collection<CompoundFSSPlots> plots,
			FaultSystemSolutionFetcher fetcher, int threads) {

		List<Task> tasks = Lists.newArrayList();
		int index = 0;
		for (LogicTreeBranch branch : fetcher.getBranches()) {
			tasks.add(new PlotSolComputeTask(plots, fetcher, branch,
					index++));
		}

		System.out.println("Making " + plots.size() + " plot(s) with "
				+ tasks.size() + " branches");

		ThreadedTaskComputer comp = new ThreadedTaskComputer(tasks);
		try {
			comp.computeThreaded(threads);
		} catch (InterruptedException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		for (CompoundFSSPlots plot : plots)
			plot.flushResults();

		for (CompoundFSSPlots plot : plots)
			plot.finalizePlot();
		
		long overheadTime = 0;
		for (Task task : tasks)
			overheadTime += ((PlotSolComputeTask)task).overheadMillis;
		
		float overheadMins = (float)((double)overheadTime / (double)MILLIS_PER_MIN);
		System.out.println("TOTAL OVERHEAD TIME: "+overheadMins+"m");
		
		printComputeTimes(plots);
	}
	
	private static long MILLIS_PER_SEC = 1000;
	private static long MILLIS_PER_MIN = MILLIS_PER_SEC*60l;
	
	protected static void printComputeTimes(Collection<CompoundFSSPlots> plots) {
		long totCompTime = 0;
		long totFinalizeTime = 0;
		for (CompoundFSSPlots plot : plots) {
			totCompTime += plot.computeTimeMillis;
			totFinalizeTime += plot.finalizeTimeMillis;
		}
		
		double totCompTimeMins = (double)totCompTime / (double)MILLIS_PER_MIN;
		double totFinalizeTimeMins = (double)totFinalizeTime / (double)MILLIS_PER_MIN;
		
		System.out.println("*** CompoundFSSPlots Compute Times ***");
		System.out.println("TOTAL COMPUTE TIME: "+totCompTimeMins+" m");
		
		for (CompoundFSSPlots plot : plots) {
			float compTimeMins = (float)((double)plot.computeTimeMillis / (double)MILLIS_PER_MIN);
			float compTimePercent = (float)(100d*compTimeMins/totCompTimeMins);
			
			System.out.println("\t"+ClassUtils.getClassNameWithoutPackage(plot.getClass())
					+": "+compTimeMins+" m ("+compTimePercent+" %)");
		}
		System.out.println("***************************************");
		
		System.out.println("*** CompoundFSSPlots Finalize Times ***");
		System.out.println("TOTAL FINALIZE TIME: "+totFinalizeTimeMins+" m");
		
		for (CompoundFSSPlots plot : plots) {
			float finalizeTimeMins = (float)((double)plot.finalizeTimeMillis / (double)MILLIS_PER_MIN);
			float finalizeTimePercent = (float)(100d*finalizeTimeMins/totFinalizeTimeMins);
			
			System.out.println("\t"+ClassUtils.getClassNameWithoutPackage(plot.getClass())
					+": "+finalizeTimeMins+" m ("+finalizeTimePercent+" %)");
		}
		System.out.println("***************************************");
	}

	/**
	 * Write all plots after they have been calculated.
	 * 
	 * @param plots
	 * @param dir
	 * @param prefix
	 * @throws Exception
	 */
	public static void batchWritePlots(Collection<CompoundFSSPlots> plots,
			File dir, String prefix) throws Exception {
		batchWritePlots(plots, dir, prefix, true);
	}

	/**
	 * Write all plots after they have been calculated.
	 * 
	 * @param plots
	 * @param dir
	 * @param prefix
	 * @param makeMapPlots
	 * @throws Exception
	 */
	public static void batchWritePlots(Collection<CompoundFSSPlots> plots,
			File dir, String prefix, boolean makeMapPlots) throws Exception {
		for (CompoundFSSPlots plot : plots) {
			if (plot instanceof RegionalMFDPlot) {
				RegionalMFDPlot mfd = (RegionalMFDPlot) plot;

				CompoundFSSPlots.writeRegionalMFDPlots(mfd.specs,
						mfd.cumulative_specs, mfd.getRegions(), dir, prefix);
			} else if (plot instanceof ERFBasedRegionalMFDPlot) {
				ERFBasedRegionalMFDPlot mfd = (ERFBasedRegionalMFDPlot) plot;
				writeERFBasedRegionalMFDPlots(mfd.specs, mfd.regions, dir,
						prefix);
			} else if (plot instanceof ERFBasedRegionalMagProbPlot) {
				ERFBasedRegionalMagProbPlot mfd = (ERFBasedRegionalMagProbPlot) plot;
				writeERFBasedRegionalProbDistPlots(mfd, dir, prefix);
//				if (mfd.sfDebugCSV != null)
//					mfd.sfDebugCSV.writeToFile(new File(dir, "sf_debug_"+System.currentTimeMillis()+".csv"));
			} else if (plot instanceof ERFBasedSiteHazardHistPlot) {
				ERFBasedSiteHazardHistPlot haz = (ERFBasedSiteHazardHistPlot) plot;
				writeERFBasedSiteHazardHists(haz, dir);
			} else if (plot instanceof ERFProbModelCalc) {
				ERFProbModelCalc prob = (ERFProbModelCalc)plot;
				File probModelDir = new File(dir, "time_dep_rup_probs");
				writeERFProbModelsFile(probModelDir, prob.probsTable);
			} else if (plot instanceof PaleoFaultPlot) {
				PaleoFaultPlot paleo = (PaleoFaultPlot) plot;
				File paleoPlotsDir = new File(dir,
						CommandLineInversionRunner.PALEO_FAULT_BASED_DIR_NAME);
				if (!paleoPlotsDir.exists());
					paleoPlotsDir.mkdir();
				CompoundFSSPlots.writePaleoFaultPlots(paleo.getPlotsMap(),
						paleoPlotsDir);
			} else if (plot instanceof PaleoSiteCorrelationPlot) {
				PaleoSiteCorrelationPlot paleo = (PaleoSiteCorrelationPlot) plot;
				File paleoPlotsDir = new File(dir,
						CommandLineInversionRunner.PALEO_CORRELATION_DIR_NAME);
				if (!paleoPlotsDir.exists())
					paleoPlotsDir.mkdir();
				CompoundFSSPlots.writePaleoCorrelationPlots(
						paleo.getPlotsMap(), paleoPlotsDir);
			} else if (plot instanceof ParentSectMFDsPlot) {
				ParentSectMFDsPlot parentPlots = (ParentSectMFDsPlot) plot;
				File parentPlotsDir = new File(dir,
						CommandLineInversionRunner.PARENT_SECT_MFD_DIR_NAME);
				if (!parentPlotsDir.exists())
					parentPlotsDir.mkdir();
				CompoundFSSPlots.writeParentSectionMFDPlots(parentPlots,
						parentPlotsDir);
			} else if (plot instanceof RupJumpPlot) {
				RupJumpPlot jumpPlot = (RupJumpPlot) plot;
				CompoundFSSPlots.writeJumpPlots(jumpPlot, dir, prefix);
			} else if (plot instanceof SubSectRITable) {
				SubSectRITable subSectPlot = (SubSectRITable) plot;
				CompoundFSSPlots.writeSubSectRITables(subSectPlot, dir, prefix);
			} else if (plot instanceof MiniSectRIPlot) {
				MiniSectRIPlot miniPlot = (MiniSectRIPlot) plot;
				CompoundFSSPlots.writeMiniSectRITables(miniPlot, dir, prefix);
			} else if (plot instanceof PaleoRatesTable) {
				PaleoRatesTable aveSlip = (PaleoRatesTable) plot;
				CompoundFSSPlots.writePaleoRatesTables(aveSlip, dir, prefix);
			} else if (plot instanceof MisfitTable) {
				MisfitTable table = (MisfitTable) plot;
				BatchPlotGen.writeMisfitsCSV(dir, prefix, table.misfitsMap);
			} else if (plot instanceof BranchAvgFSSBuilder) {
				BranchAvgFSSBuilder builder = (BranchAvgFSSBuilder) plot;
				writeMeanSolutions(builder, dir, prefix);
			} else if (plot instanceof MapBasedPlot) {
				MapBasedPlot faultPlot = (MapBasedPlot) plot;
				faultPlot.writePlotData(dir);
				faultPlot.writeExtraData(dir, prefix);
				if (makeMapPlots)
					faultPlot.makeMapPlots(dir, prefix);
			}
		}
	}
	
	private static void writeU2RegProbTable(File csvFile, boolean nucleation) throws ZipException, IOException {
		File compoundFile = new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
				"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL.zip");
		
		ERFBasedRegionalMagProbPlot.NUCLEATION_PROBS = nucleation;
		
		FaultSystemSolutionFetcher fetch = CompoundFaultSystemSolution
				.fromZipFile(compoundFile);
		BranchWeightProvider weightProvider = new APrioriBranchWeightProvider();
		int threads = 3;
		
		// we only need UCERF3 to get the ball rolling, won't be using it
		fetch = FaultSystemSolutionFetcher.getRandomSample(fetch, threads,
				FaultModels.FM3_1);
		
		List<CompoundFSSPlots> plots = Lists.newArrayList();
		
		ERFBasedRegionalMagProbPlot withAftershocks = new ERFBasedRegionalMagProbPlot(weightProvider);
		ERFBasedRegionalMagProbPlot.INCLUDE_AFTERSHOCKS = true;
		plots.add(withAftershocks);
		
		batchPlot(plots, fetch, threads);
		
		plots = Lists.newArrayList();
		
		ERFBasedRegionalMagProbPlot withoutAftershocks = new ERFBasedRegionalMagProbPlot(weightProvider);
		ERFBasedRegionalMagProbPlot.INCLUDE_AFTERSHOCKS = false;
		plots.add(withoutAftershocks);
		
		batchPlot(plots, fetch, threads);
		
		CSVFile<String> csv = new CSVFile<String>(true);
		csv.addLine("Region", "U2 w/o Aftershock Mean", "U2 w/o Aftershock Min", "U2 w/o Aftershock Max",
				"U2 w/ Aftershock Mean", "U2 w/ Aftershock Min", "U2 w/ Aftershock Max");
		
		double duration = 30;
		double mag = 6.7;
		
		ERFBasedRegionalMagProbPlot[] probs = { withoutAftershocks, withAftershocks };
		
		List<Region> defaultRegions = ERFBasedRegionalMagProbPlot.getDefaultRegions();
		for (int i = 0; i < defaultRegions.size(); i++) {
			Region r = defaultRegions.get(i);
			List<String> line = Lists.newArrayList();
			line.add(r.getName());
			
			for (ERFBasedRegionalMagProbPlot prob : probs) {
				XY_DataSetList ucerf2Funcs = new XY_DataSetList();
				ArrayList<Double> ucerf2Weights = new ArrayList<Double>();
				for (int e = 0; e < prob.ucerf2DepMPDs.get(duration).get(i).length; e++) {
					DiscretizedFunc mfd = prob.ucerf2DepMPDs.get(duration).get(i)[e];
					if (mfd != null) {
						ucerf2Funcs.add(prob.ucerf2DepMPDs.get(duration).get(i)[e]);
						ucerf2Weights.add(prob.ucerf2DepWeights[e]);
					}
				}
				
				// ordered mean min max
				List<DiscretizedFunc> fractiles = getFractiles(ucerf2Funcs, ucerf2Weights, "asdf", new double[0]);
				double mean = fractiles.get(0).getY(mag);
				double min = fractiles.get(1).getY(mag);
				double max = fractiles.get(2).getY(mag);
				
				line.add(mean+"");
				line.add(min+"");
				line.add(max+"");
			}
			csv.addLine(line);
		}
		
		csv.writeToFile(csvFile);
	}
	
	private static boolean hasBothFMs(FaultSystemSolutionFetcher fetch) {
		boolean has31 = false;
		boolean has32 = false;
		for (LogicTreeBranch branch : fetch.getBranches()) {
			FaultModels fm = branch.getValue(FaultModels.class);
			if (fm == FaultModels.FM3_1)
				has31 = true;
			if (fm == FaultModels.FM3_2)
				has32 = true;
			if (has31 && has32)
				return true;
		}
		return false;
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws ZipException
	 */
	public static void main(String[] args) throws ZipException, Exception {
//		File curveDir = new File("/tmp/comp_plots/site_hazard_curve_cache");
//		ERFBasedSiteHazardHistPlot.writeMetadataFileForAllBranches(Lists.newArrayList(CompoundFaultSystemSolution
//				.fromZipFile(new File("/tmp/comp_plots/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL.zip")).getBranches()),
//				new APrioriBranchWeightProvider(), curveDir);
//		writeERFBasedSiteHazardHists(ERFBasedSiteHazardHistPlot.doFinalizePlot(
//				curveDir), new File("/tmp/comp_plots"));
//		System.exit(0);
//		recombineMagProbDistPDFs(new File("/tmp/asdf/small_MPD_plots/"),
//				new File("/tmp/asdf/small_MPD_faults/"), "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL");
//		System.exit(0);
		if (args.length >= 3) {
			File dir = new File(args[0]);
			String prefix = args[1];
			for (int i = 2; i < args.length; i++) {
				File plotFile = new File(dir, args[i]);
				MapBasedPlot.makeMapPlots(dir, prefix,
						MapBasedPlot.loadPlotData(plotFile));
			}
			System.exit(0);
		}
//		UCERF2_TimeIndependentEpistemicList ucerf2_erf_list = new UCERF2_TimeIndependentEpistemicList();
//		ucerf2_erf_list.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME,
//				UCERF2.FULL_DDW_FLOATER);
//		ucerf2_erf_list.setParameter(UCERF2.BACK_SEIS_NAME,
//				UCERF2.BACK_SEIS_INCLUDE);
//		ucerf2_erf_list.setParameter(UCERF2.BACK_SEIS_RUP_NAME,
//				UCERF2.BACK_SEIS_RUP_POINT);
//		ucerf2_erf_list.getTimeSpan().setDuration(1d);
//		ucerf2_erf_list.updateForecast();

		BranchWeightProvider weightProvider = new APrioriBranchWeightProvider();
		// File dir = new
		// File("/tmp/2012_10_12-fm3-ref-branch-weight-vars-zengfix_COMPOUND_SOL");
		// File file = new File(dir,
		// "2012_10_12-fm3-ref-branch-weight-vars-zengfix_COMPOUND_SOL.zip");
		File dir = new File("/tmp/comp_plots");
//		File file = new File(dir,
//				"2013_05_01-ucerf3p3-proposed-subset-hpcc-salmonfix_COMPOUND_SOL.zip");
//		File dir = new File("/tmp/paleo_comp_plots/Paleo1.5");
//		File file = new File(dir, "2013_05_03-ucerf3p3-production-first-five_MEAN_COMPOUND_SOL.zip");
//		File file = new File(dir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_WITH_IND_RUNS.zip");
		File file = new File(dir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL.zip");
//		File file = new File(dir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_U3_SeisPDF.zip");
//		File file = new File(dir, "2013_04_02-new-paleo-weights-tests_VarPaleo1.5_COMPOUND_SOL.zip");
		// File file = new File(dir, "zeng_convergence_compound.zip");
		// File file = new
		// File("/tmp/2012_10_10-fm3-logic-tree-sample_COMPOUND_SOL.zip");
		FaultSystemSolutionFetcher fetch = CompoundFaultSystemSolution.fromZipFile(file);
		double wts = 0;
		for (LogicTreeBranch branch : fetch.getBranches())
			wts += weightProvider.getWeight(branch);
		System.out.println("Total weight: " + wts);
		// System.exit(0);
		int sols = 8;
//		int sols = -1;
		int threads = 4;
		
		if (sols > 0) {
			// For one FM
			fetch = FaultSystemSolutionFetcher.getRandomSample(fetch, sols,
					FaultModels.FM3_1);
			
			// For both FMs
//			fetch = FaultSystemSolutionFetcher.getRandomSample(fetch, sols);
//			while (!hasBothFMs(fetch))
//				fetch = FaultSystemSolutionFetcher.getRandomSample(fetch, sols);
			
			System.out.println("Now has "+fetch.getBranches().size()+" branches");
		}
		
		// if true, only use instances of the mean fault system solution
		boolean meanDebug = false;
		if (meanDebug) {
			final Collection<LogicTreeBranch> branches = fetch.getBranches();
			final InversionFaultSystemSolution meanSol = FaultSystemIO.loadInvSol(
					new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
							"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
			
			fetch = new FaultSystemSolutionFetcher() {
				
				@Override
				public Collection<LogicTreeBranch> getBranches() {
					return branches;
				}
				
				@Override
				protected InversionFaultSystemSolution fetchSolution(LogicTreeBranch branch) {
					return meanSol;
				}
			};
		}

		new DeadlockDetectionThread(3000).start();

		// List<Region> regions = RegionalMFDPlot.getDefaultRegions();
		// List<Region> regions = RegionalMFDPlot.getDefaultRegions().subList(3,
		// 5);
		List<Region> regions = RegionalMFDPlot.getDefaultRegions()
				.subList(0, 1);
		regions = Lists.newArrayList(regions);

		// File dir = new File("/tmp");
		// String prefix = "2012_10_10-fm3-logic-tree-sample-first-247";
		String prefix = dir.getName();
		// for (PlotSpec spec : getRegionalMFDPlotSpecs(fetch, weightProvider,
		// regions)) {
		// GraphWindow gw = new GraphWindow(spec);
		// gw.setYLog(true);
		// }
		// writeRegionalMFDPlots(fetch, weightProvider, regions, dir, prefix);
		// File paleoDir = new File(dir, prefix+"-paleo-faults");
		// writePaleoFaultPlots(fetch, weightProvider, paleoDir);
		// File paleoCorrDir = new File(dir, prefix+"-paleo-corr");
		// writePaleoCorrelationPlots(fetch, weightProvider, paleoCorrDir);
		// File parentSectMFDsDir = new File(dir, prefix+"-parent-sect-mfds");
		// writeParentSectionMFDPlots(fetch, weightProvider, parentSectMFDsDir);
		// writeJumpPlots(fetch, weightProvider, dir, prefix);
		List<CompoundFSSPlots> plots = Lists.newArrayList();
//		plots.add(new RegionalMFDPlot(weightProvider, regions));
//		plots.add(new PaleoFaultPlot(weightProvider));
//		plots.add(new ERFBasedRegionalMagProbPlot(weightProvider));
//		plots.add(new ERFBasedSiteHazardHistPlot(weightProvider,
//				new File(dir, ERFBasedSiteHazardHistPlot.DEFAULT_CACHE_DIR_NAME), fetch.getBranches().size()));
//		plots.add(new ERFProbModelCalc());
//		plots.add(new BranchAvgFSSBuilder(weightProvider));
//		plots.add(new TimeDepGriddedParticipationProbPlot(weightProvider));
//		plots.add(new PaleoSiteCorrelationPlot(weightProvider));
//		plots.add(new ParentSectMFDsPlot(weightProvider));
//		plots.add(new RupJumpPlot(weightProvider));
//		plots.add(new SlipRatePlots(weightProvider));
//		plots.add(new ParticipationMapPlot(weightProvider));
//		plots.add(new GriddedParticipationMapPlot(weightProvider, 0.1d));
//		plots.add(new ERFBasedRegionalMFDPlot(weightProvider));
		plots.add(new SubSectRITable(weightProvider));
//		plots.add(new MiniSectRIPlot(weightProvider));
//		plots.add(new PaleoRatesTable(weightProvider));
//		plots.add(new AveSlipMapPlot(weightProvider));
//		plots.add(new MultiFaultParticPlot(weightProvider));
//		MeanFSSBuilder meanBuild = new MeanFSSBuilder(weightProvider);
//		meanBuild.setSolIndex(8);
//		plots.add(meanBuild);

		batchPlot(plots, fetch, threads);

		// for (CompoundFSSPlots plot : plots)
		// FileUtils.saveObjectInFile("/tmp/asdf.obj", plot);
		batchWritePlots(plots, dir, prefix, true);
		// MapBasedPlot.makeMapPlots(dir, prefix,
		// MapBasedPlot.loadPlotData(new File(dir,
		// SlipMisfitPlot.PLOT_DATA_FILE_NAME)));
		// MapBasedPlot.makeMapPlots(dir, prefix,
		// MapBasedPlot.loadPlotData(new File(dir,
		// AveSlipPlot.PLOT_DATA_FILE_NAME)));
		// MapBasedPlot.makeMapPlots(dir, prefix,
		// MapBasedPlot.loadPlotData(new File(dir,
		// MultiFaultParticPlot.PLOT_DATA_FILE_NAME)));

		System.exit(0);

	}

}
