package scratch.UCERF3.analysis;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.data.Named;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.elements.CoastAttributes;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.mapping.gmt.elements.PSXYPolygon;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbol;
import org.opensha.commons.mapping.gmt.elements.TopographicSlopeFile;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbol.Symbol;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbolSet;
import org.opensha.commons.mapping.gmt.gui.GMT_MapGuiBean;
import org.opensha.commons.mapping.gmt.gui.ImageViewerWindow;
import org.opensha.commons.param.impl.CPTParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.RunScript;
import org.opensha.commons.util.XMLUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipEnabledSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.inversion.InversionConfiguration;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.UCERF2_ComparisonSolutionFetcher;
import scratch.UCERF3.utils.DeformationModelFileParser;
import scratch.UCERF3.utils.DeformationModelFileParser.DeformationSection;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.GeologicSlipRate;
import scratch.UCERF3.utils.GeologicSlipRateLoader;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.UCERF3.utils.UCERF3_DataUtils;

public class FaultBasedMapGen {
	
	private static GMT_MapGenerator gmt;
	
	public static boolean LOCAL_MAPGEN = false;
	
	private static CPT slipCPT = null;
	public static CPT getSlipRateCPT() {
		if (slipCPT == null) {
			slipCPT = new CPT();
			
			slipCPT.setBelowMinColor(Color.GRAY);
			slipCPT.setNanColor(Color.GRAY);
			
//			slipCPT.add(new CPTVal(0f, Color.GRAY, 0f, Color.GRAY));
			slipCPT.add(new CPTVal(Float.MIN_VALUE, Color.BLUE, 10f, Color.MAGENTA));
			slipCPT.add(new CPTVal(10f, Color.MAGENTA, 20f, Color.RED));
			slipCPT.add(new CPTVal(20f, Color.RED, 30f, Color.ORANGE));
			slipCPT.add(new CPTVal(30f, Color.ORANGE, 40f, Color.YELLOW));
			
			slipCPT.setAboveMaxColor(Color.YELLOW);
		}
		return slipCPT;
	}
	
	/**
	 * This is a good cpt for log10(slipRate) values from UCERF3
	 */
	private static CPT log10_slipCPT = null;
	public static CPT getLog10_SlipRateCPT() {
		if (log10_slipCPT == null) {
			log10_slipCPT = new CPT();
			
			Color lightBlue = new Color(200,200,255);
			Color darkBlue = new Color(75,75,255);
			Color altOrange = new Color(255, 128, 0);
			log10_slipCPT.setNanColor(Color.GRAY);
			log10_slipCPT.setBelowMinColor(lightBlue);
			log10_slipCPT.add(new CPTVal(-3f, lightBlue, -2f, darkBlue));
			log10_slipCPT.add(new CPTVal(-2f, darkBlue, -1f, Color.GREEN));
			log10_slipCPT.add(new CPTVal(-1f, Color.GREEN, 0f, Color.YELLOW));
			log10_slipCPT.add(new CPTVal(0f, Color.YELLOW, 1f, altOrange));
			log10_slipCPT.add(new CPTVal(1f, altOrange, 1.4f, Color.RED));			// 1.4 corresponds to ~25 mm/yr
			log10_slipCPT.add(new CPTVal(1.4f, Color.RED, 1.6f, Color.MAGENTA));	// 1.6 corresponds to ~40 mm/yr
			log10_slipCPT.setAboveMaxColor(Color.MAGENTA);
		}
		return log10_slipCPT;
	}

	
	private static CPT participationCPT = null;
	public static CPT getParticipationCPT() {
		if (participationCPT == null) {
			try {
				participationCPT = GMT_CPT_Files.UCERF2_FIGURE_35.instance();
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		
		return participationCPT;
	}
	
	private static CPT linearRatioCPT = null;
	public static CPT getLinearRatioCPT() {
		if (linearRatioCPT == null) {
			try {
				linearRatioCPT = GMT_CPT_Files.UCERF3_RATIOS.instance();
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
			linearRatioCPT = linearRatioCPT.rescale(0, 2);
		}
		
		return linearRatioCPT;
	}
	
	private static CPT logRatioCPT = null;
	public static CPT getLogRatioCPT() {
		if (logRatioCPT == null) {
			try {
				logRatioCPT = GMT_CPT_Files.UCERF3_RATIOS.instance();
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
			logRatioCPT = logRatioCPT.rescale(-3, 3);
		}
		
		return logRatioCPT;
	}
	
	private static CPT normalizedPairRatesCPT = null;
	private static CPT getNormalizedPairRatesCPT() {
		if (normalizedPairRatesCPT == null) {
			try {
				normalizedPairRatesCPT = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0, 1);
//				normalizedPairRatesCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(0, 1);
				normalizedPairRatesCPT.setNanColor(Color.GRAY);
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		
		return normalizedPairRatesCPT;
	}
	
	public static void plotOrigNonReducedSlipRates(FaultSystemSolution sol, Region region, File saveDir, String prefix, boolean display)
			throws GMT_MapException, RuntimeException, IOException {
		CPT cpt = getSlipRateCPT();
		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();
		double[] values = new double[faults.size()];
		for (int i=0; i<faults.size(); i++)
			values[i] = faults.get(i).getOrigAveSlipRate();
		
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, prefix+"_orig_non_reduced_slip", display, false, "Original Non Reduced Slip Rate (mm/yr)");
	}
	
	public static void plotOrigCreepReducedSlipRates(FaultSystemSolution sol, Region region, File saveDir, String prefix, boolean display)
			throws GMT_MapException, RuntimeException, IOException {
		CPT cpt = getSlipRateCPT();
		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();
		double[] values = new double[faults.size()];
		for (int i=0; i<faults.size(); i++)
			values[i] = faults.get(i).getReducedAveSlipRate();
		
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, prefix+"_orig_creep_reduced_slip", display, false, "Orig Creep Reduced Slip Rate (mm/yr)");
	}
	
	public static void plotTargetSlipRates(FaultSystemSolution sol, Region region, File saveDir, String prefix, boolean display)
			throws GMT_MapException, RuntimeException, IOException {
		CPT cpt = getSlipRateCPT();
		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();
		double[] values = scale(sol.getRupSet().getSlipRateForAllSections(), 1e3); // to mm
		
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, prefix+"_target_slip", display, false, "Target Slip Rate (mm/yr)");
	}
	
	public static void plotSolutionSlipRates(SlipEnabledSolution sol, Region region, File saveDir, String prefix, boolean display)
			throws GMT_MapException, RuntimeException, IOException {
		CPT cpt = getSlipRateCPT();
		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();
		double[] values = scale(sol.calcSlipRateForAllSects(), 1e3); // to mm
		
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, prefix+"_solution_slip", display, false, "Solution Slip Rate (mm/yr)");
	}
	
	private static double calcFractionalDifferentce(double target, double comparison) {
		return (comparison - target) / target;
	}
	
	
	/**
	 * Warning - this uses a fault system solution, whereas an ERF will have different results due to minimum supra-seis mag
	 * cutoff, aleatory variability added to mag-area for fault ruptures, and perhaps other things.
	 * @param sol
	 * @param region
	 * @param saveDir
	 * @param prefix
	 * @param display
	 * @param logRatio
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 * @throws IOException
	 */
	public static void plotBulgeFromFirstGenAftershocksMap(InversionFaultSystemSolution sol, Region region, File saveDir, String prefix, boolean display, boolean logRatio) 
			throws GMT_MapException, RuntimeException, IOException {

		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();

		List<GutenbergRichterMagFreqDist> subSeisMFD_List = sol.getFinalSubSeismoOnFaultMFD_List();
		List<IncrementalMagFreqDist> supraSeisMFD_List = sol.getFinalSupraSeismoOnFaultMFD_List(5.05, 8.95, 40);

		double[] values = new double[faults.size()];
		
//		ETAS_Utils.getScalingFactorToImposeGR(supraSeisMFD_List.get(1), subSeisMFD_List.get(1));

		for(int i=0;i<subSeisMFD_List.size();i++) {
			values[i] = 1.0/ETAS_Utils.getScalingFactorToImposeGR_numPrimary(supraSeisMFD_List.get(i), subSeisMFD_List.get(i), false);
		}
		
		CPT cpt;
		prefix += "_bulgeFrom1stGenAft";
		String name = "BulgeFrom1stGenAftershocks";
		if (logRatio) {
			values = log10(values);
			cpt = getLogRatioCPT().rescale(-2, 2);
			prefix += "_log";
			name = "Log10("+name+")";
		} else {
			cpt = getLinearRatioCPT();
		}
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, prefix, display, false, name);

	}
	
	public static void plotBulgeForM6pt7_Map(InversionFaultSystemSolution sol, Region region, File saveDir, String prefix, boolean display, boolean logRatio) 
			throws GMT_MapException, RuntimeException, IOException {

		double mag = 6.75;
		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();

		List<GutenbergRichterMagFreqDist> subSeisMFD_List = sol.getFinalSubSeismoOnFaultMFD_List();
		List<IncrementalMagFreqDist> supraSeisMFD_List = sol.getFinalSupraSeismoOnFaultMFD_List(5.05, 8.95, 40);

		double[] values = new double[faults.size()];
		
//		ETAS_Utils.getScalingFactorToImposeGR(supraSeisMFD_List.get(1), subSeisMFD_List.get(1));

		for(int i=0;i<subSeisMFD_List.size();i++) {
			
			GutenbergRichterMagFreqDist subSeisMFD = subSeisMFD_List.get(i);
			IncrementalMagFreqDist supraSeisMFD = supraSeisMFD_List.get(i);
			double minMag = subSeisMFD.getMinX();
			double maxMagWithNonZeroRate = supraSeisMFD.getMaxMagWithNonZeroRate();
			if(maxMagWithNonZeroRate >= mag) {
				int numMag = (int)Math.round((maxMagWithNonZeroRate-minMag)/0.1) + 1;
				GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(1.0, 1.0, minMag, maxMagWithNonZeroRate, numMag);
				gr.scaleToIncrRate(5.05, subSeisMFD.getY(5.05));
				SummedMagFreqDist sumDist = new SummedMagFreqDist(minMag, maxMagWithNonZeroRate,numMag);
				sumDist.addIncrementalMagFreqDist(subSeisMFD);
				sumDist.addIncrementalMagFreqDist(supraSeisMFD);
				values[i] = sumDist.getCumRate(mag)/gr.getCumRate(mag);
			}
			else {
				values[i] = 1.0/0.0;
			}

//			if(sumDist.getXIndex(mag) == -1) {
//				System.out.println(sumDist);
//			}
//			if(gr.getXIndex(mag) == -1) {
//				System.out.println(gr);
//			}

		}
		
		CPT cpt;
		prefix += "_bulgeForM6pt7";
		String name = "BulgeForM6pt7";
		if (logRatio) {
			values = log10(values);
			cpt = getLogRatioCPT().rescale(-2, 2);
			prefix += "_log";
			name = "Log10("+name+")";
		} else {
			cpt = getLinearRatioCPT();
		}
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, prefix, display, false, name);

	}


	
	public static void plotSolutionSlipMisfit(SlipEnabledSolution sol, Region region, File saveDir, String prefix, boolean display, boolean logRatio)
			throws GMT_MapException, RuntimeException, IOException {
		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();
		double[] solSlips = sol.calcSlipRateForAllSects();
		double[] targetSlips = sol.getRupSet().getSlipRateForAllSections();
		double[] values = new double[faults.size()];
//		for (int i=0; i<faults.size(); i++)
//			values[i] = calcFractionalDifferentce(targetSlips[i], solSlips[i]);
//		CPT cpt = getFractionalDifferenceCPT();
//		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, prefix+"_slip_misfit", display, false, "Solution Slip Rate Misfit (fractional diff)");
		for (int i=0; i<faults.size(); i++) {
			if (solSlips[i] == 0 && targetSlips[i] == 0)
				values[i] = 1;
			else
				values[i] = solSlips[i] / targetSlips[i];
		}
		CPT cpt;
		prefix += "_slip_misfit";
		String name = "Solution Slip / Target Slip";
		if (logRatio) {
			values = log10(values);
			cpt = getLogRatioCPT().rescale(-1, 1);
			prefix += "_log";
			name = "Log10("+name+")";
		} else {
			cpt = getLinearRatioCPT();
		}
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, prefix, display, false, name);
	}
	
	public static void plotParticipationRates(FaultSystemSolution sol, Region region, File saveDir, String prefix, boolean display,
			double minMag, double maxMag)
			throws GMT_MapException, RuntimeException, IOException {
		CPT cpt = getParticipationCPT();
		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();
		double[] values = sol.calcParticRateForAllSects(minMag, maxMag);
		
		// now log space
		values = log10(values);
		
		String name = prefix+"_partic_rates_"+(float)minMag;
		String title = "Log10(Participation Rates "+(float)+minMag;
		if (maxMag < 9) {
			name += "_"+(float)maxMag;
			title += "=>"+(float)maxMag;
		} else {
			name += "+";
			title += "+";
		}
		title += ")";
		
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, name, display, false, title);
	}
	
	
	
	public static void plotParticipationStdDevs(FaultSystemRupSet rupSet, double[][] partRates, Region region, File saveDir,
			String prefix, boolean display, double minMag, double maxMag)
			throws GMT_MapException, RuntimeException, IOException {
		CPT cpt = getParticipationCPT();
		List<FaultSectionPrefData> faults = rupSet.getFaultSectionDataList();
		
		double[] stdDevs = new double[partRates.length];
		double[] mean = new double[partRates.length];
		double[] sdom_over_means = new double[partRates.length];
		for (int i=0; i<partRates.length; i++) {
			mean[i] = StatUtils.mean(partRates[i]);
			stdDevs[i] = Math.sqrt(StatUtils.variance(partRates[i], mean[i]));
			sdom_over_means[i] = (stdDevs[i] / Math.sqrt(partRates[i].length)) / mean[i];
		}
		
		String name = prefix+"_partic_std_dev_"+(float)minMag;
		String title = "Log10(Participation Rates Std. Dev. "+(float)+minMag;
		if (maxMag < 9) {
			name += "_"+(float)maxMag;
			title += "=>"+(float)maxMag;
		} else {
			name += "+";
			title += "+";
		}
		title += ")";
		
		MatrixIO.doubleArrayToFile(stdDevs, new File(saveDir, name+".bin"));
		
		// now log space
		double[] logStdDevs = log10(stdDevs);
		
		makeFaultPlot(cpt, getTraces(faults), logStdDevs, region, saveDir, name, display, false, title);
		
		title = title.replaceAll("Dev. ", "Dev. / Mean ");
		name = name.replaceAll("_dev_", "_dev_norm_");
		double[] norm = new double[mean.length];
		for (int i=0; i<mean.length; i++)
			norm[i] = stdDevs[i] / mean[i];
		norm = log10(norm);
		cpt = cpt.rescale(-3, 2);
		
		makeFaultPlot(cpt, getTraces(faults), norm, region, saveDir, name, display, false, title);
		
		double[] logSDOMOverMeans = log10(sdom_over_means);
		
//		cpt = getLogRatioCPT();
		cpt = cpt.rescale(-4, 0);
		name = prefix+"_partic_sdom_over_mean_"+(float)minMag;
		title = "Log10(Participation Rates SDOM / Mean "+(float)+minMag;
		if (maxMag < 9) {
			name += "_"+(float)maxMag;
			title += "=>"+(float)maxMag;
		} else {
			name += "+";
			title += "+";
		}
		title += ")";
		
		makeFaultPlot(cpt, getTraces(faults), logSDOMOverMeans, region, saveDir, name, display, false, title);
	}
	
	public static void plotSolutionSlipRateStdDevs(FaultSystemRupSet rupSet, double[][] slipRates, Region region, File saveDir, String prefix, boolean display)
			throws GMT_MapException, RuntimeException, IOException {
		CPT cpt = getParticipationCPT().rescale(-4, 1);
		List<FaultSectionPrefData> faults = rupSet.getFaultSectionDataList();
		
		double[] stdDev = new double[slipRates.length];
		double[] mean = new double[slipRates.length];
		for (int i=0; i<slipRates.length; i++) {
			double[] rates = scale(slipRates[i], 1e3); // to mm
			mean[i] = StatUtils.mean(rates);
			stdDev[i] = Math.sqrt(StatUtils.variance(rates, mean[i]));
		}
		MatrixIO.doubleArrayToFile(stdDev, new File(saveDir, prefix+"_solution_slip_std_dev.bin"));
		// now log space
		double[] logStdDev = log10(stdDev);
		
		makeFaultPlot(cpt, getTraces(faults), logStdDev, region, saveDir, prefix+"_solution_slip_std_dev", display, false, "Log10(Solution Slip Rate Std Dev (mm/yr))");

		double[] norm = new double[mean.length];
		for (int i=0; i<mean.length; i++)
			norm[i] = stdDev[i] / mean[i];
		norm = log10(norm);
		cpt = cpt.rescale(-3, 2);
		
		makeFaultPlot(cpt, getTraces(faults), norm, region, saveDir, prefix+"_solution_slip_std_dev_norm", display, false, "Log10(Solution Slip Rate Std Dev / Mean)");
	}
	
	public static void plotParticipationRatios(FaultSystemSolution sol, FaultSystemSolution referenceSol, Region region,
			File saveDir, String prefix, boolean display, double minMag, double maxMag, boolean omitInfinites)
			throws GMT_MapException, RuntimeException, IOException {
		CPT cpt = getLogRatioCPT();
		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();
		double[] newVals = sol.calcParticRateForAllSects(minMag, maxMag);
		double[] refVals = referenceSol.calcParticRateForAllSects(minMag, maxMag);
		Preconditions.checkState(newVals.length == refVals.length, "solution rupture counts are incompatible!");
		
		double[] values = new double[newVals.length];
		for (int i=0; i<values.length; i++) {
			values[i] = newVals[i] / refVals[i];
			if (omitInfinites && Double.isInfinite(values[i]))
				values[i] = Double.NaN;
		}
		values = log10(values);
		
		String name = prefix+"_partic_ratio_"+(float)minMag;
		String title = "Log10(Participation Ratios "+(float)+minMag;
		if (maxMag < 9) {
			name += "_"+(float)maxMag;
			title += "=>"+(float)maxMag;
		} else {
			name += "+";
			title += "+";
		}
		title += ")";
		
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, name, display, true, title);
	}
	
	public static double plotParticipationDiffs(FaultSystemSolution sol, FaultSystemSolution referenceSol, Region region,
			File saveDir, String prefix, boolean display, double minMag, double maxMag)
			throws GMT_MapException, RuntimeException, IOException {
//		CPT cpt = getLinearRatioCPT().rescale(-3, 3);
		CPT cpt = getLinearRatioCPT().rescale(-0.005, 0.005);
		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();
		double[] newVals = sol.calcParticRateForAllSects(minMag, maxMag);
		double[] refVals = referenceSol.calcParticRateForAllSects(minMag, maxMag);
		Preconditions.checkState(newVals.length == refVals.length, "solution rupture counts are incompatible!");
		
		double[] values = new double[newVals.length];
		double total = 0;
		for (int i=0; i<values.length; i++) {
			double diff = newVals[i] - refVals[i];
			if (!Double.isNaN(diff))
				total += diff;
			values[i] = diff;
		}
		
		String name = prefix+"_ref_partic_diff_"+(float)minMag;
//		String title = "Log10(Sol Partic Rate) - Log10(Ref Partic Rate) "+(float)+minMag;
		String title = "Participation Rate Diff "+(float)+minMag;
		if (maxMag < 9) {
			name += "_"+(float)maxMag;
			title += "=>"+(float)maxMag;
		} else {
			name += "+";
			title += "+";
		}
		
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, name, display, true, title);
		return total;
	}
	
	public static void plotSectionPairRates(FaultSystemSolution sol, Region region,
			File saveDir, String prefix, boolean display) throws GMT_MapException, RuntimeException, IOException {
		CPT cpt = getNormalizedPairRatesCPT();
		List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();
		double[][] rates = sol.getSectionPairRupRates();
		
		ArrayList<LocationList> lines = new ArrayList<LocationList>();
		ArrayList<Double> vals = new ArrayList<Double>();
		
		for (int sec1=0; sec1<rates.length; sec1++) {
			double[] secRates = rates[sec1];
			
			for (int sec2=0; sec2<secRates.length; sec2++) {
				// don't draw lines between the same section (==)
				// done draw lines if sec 1 is greater than sec 2, because it
				// will have already been added (>)
				if (sec1 >= sec2)
					continue;
				
				double rate = secRates[sec2];
				if (rate <= 0)
					continue;
				
				double sec1Rate = sol.calcParticRateForSect(sec1, 0, 10);
				double sec2Rate = sol.calcParticRateForSect(sec2, 0, 10);
				double avg = 0.5 * (sec1Rate + sec2Rate);
				rate /= avg;
				
				LocationList pts = new LocationList();
				pts.add(getTraceMidpoint(faults.get(sec1)));
				pts.add(getTraceMidpoint(faults.get(sec2)));
				
				lines.add(pts);
				vals.add(rate);
			}
		}
		double[] values = new double[vals.size()];
		for (int i=0; i<values.length; i++)
			values[i] = vals.get(i);
		
		makeFaultPlot(cpt, lines, values, region, saveDir, prefix+"_sect_pairs", display, true, "Normalized Section Pair Rates");
	}
	
	public static void plotSegmentation(FaultSystemSolution sol, Region region,
			File saveDir, String prefix, boolean display, double minMag, double maxMag)
					throws GMT_MapException, RuntimeException, IOException {
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		CPT cpt = getNormalizedPairRatesCPT();
		
		List<FaultSectionPrefData> faults = rupSet.getFaultSectionDataList();
		Map<Integer, FaultSectionPrefData> faultsMap = Maps.newHashMap();
		for (FaultSectionPrefData fault : faults)
			faultsMap.put(fault.getSectionId(), fault);
		ArrayList<Integer> ends = Lists.newArrayList();
		
		Map<Integer, List<FaultSectionPrefData>> parentsMap = Maps.newHashMap();
		
		int prevParent = -2;
		List<FaultSectionPrefData> curSectsForParentList = null;
		for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++) {
			FaultSectionPrefData fault = rupSet.getFaultSectionData(sectIndex);
			int parent = fault.getParentSectionId();
			
			if (prevParent != parent) {
				// this means we're at the start of a new section
				if (sectIndex > 0)
					ends.add(sectIndex-1);
				ends.add(sectIndex);
				curSectsForParentList = Lists.newArrayList();
				parentsMap.put(parent, curSectsForParentList);
			}
			
			curSectsForParentList.add(fault);
			
			prevParent = parent;
		}
		
		List<FaultSectionPrefData> visibleFaults = Lists.newArrayList();
		List<FaultSectionPrefData> visibleNanFaults = Lists.newArrayList();
		List<Double> valsList = Lists.newArrayList();
		
		// this will color ends by the rate of all ruptures ending at this section divided by
		// the total rate of all ruptures involving this section.
		for (int sect : ends) {
			List<Integer> rups = rupSet.getRupturesForSection(sect);
			
			double totRate = 0;
			double endRate = 0;
			
			int cnt = 0;
			for (int rupID : rups) {
				double mag = rupSet.getMagForRup(rupID);
				if (mag < minMag || mag > maxMag)
					continue;
				double rate = sol.getRateForRup(rupID);
				List<Integer> sects = rupSet.getSectionsIndicesForRup(rupID);
				if (sects.get(0) == sect || sects.get(sects.size()-1) == sect)
					endRate += rate;
				totRate += rate;
				
				cnt++;
			}
			if (cnt > 0) {
				valsList.add(endRate / totRate);
				visibleFaults.add(faultsMap.get(sect));
				
				// now add the "middle" faults for this section
				List<FaultSectionPrefData> sects = parentsMap.get(
						rupSet.getFaultSectionData(sect).getParentSectionId());
				if (sect == sects.get(0).getSectionId()) {
					// only add for the first section of each parent to avoid duplication
					for (int i=1; i<sects.size()-1; i++)
						visibleNanFaults.add(sects.get(i));
				}
			}
		}
		
		faults = Lists.newArrayList();
		faults.addAll(visibleNanFaults);
		faults.addAll(visibleFaults);
		
		double[] values = new double[faults.size()];
		int index;
		for (index=0; index<visibleNanFaults.size(); index++)
			values[index] = Double.NaN;
		
		for (int i=0; i<visibleFaults.size(); i++)
			values[index+i] = valsList.get(i);
		
		String title = "Segmentation";
		String fName = prefix+"_segmentation";
		
		if (minMag > 5) {
			title += " ("+(float)minMag;
			fName += "_"+(float)minMag;
		}
		if (maxMag < 9) {
			title += "=>"+(float)maxMag;
			fName += "_"+(float)maxMag;
		} else if (minMag > 5) {
			title += "+";
			fName += "+";
		}
		
		title += ")";
		
		makeFaultPlot(cpt, getTraces(faults), values, region, saveDir, fName, display, false, title);
	}
	
	public static void plotDeformationModelSlips(Region region, File saveDir, boolean display)
			throws IOException, GMT_MapException, RuntimeException {
		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM2_1, DeformationModels.UCERF2_ALL, "fm2_1_ucerf2");
		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_1, DeformationModels.GEOLOGIC, "fm3_1_geol");
//		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_1, DeformationModels.GEOLOGIC_UPPER, "fm3_1_geol_upper");
//		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_1, DeformationModels.GEOLOGIC_LOWER, "fm3_1_geol_lower");
		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_1, DeformationModels.ABM, "fm3_1_abm");
		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_1, DeformationModels.NEOKINEMA, "fm3_1_neok");
		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_1, DeformationModels.ZENGBB, "fm3_1_zengbb");
		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_2, DeformationModels.GEOLOGIC, "fm3_2_geol");
//		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_2, DeformationModels.GEOLOGIC_UPPER, "fm3_2_geol_upper");
//		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_2, DeformationModels.GEOLOGIC_LOWER, "fm3_2_geol_lower");
		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_2, DeformationModels.ABM, "fm3_2_abm");
		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_2, DeformationModels.NEOKINEMA, "fm3_2_neok");
		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_2, DeformationModels.ZENGBB, "fm3_2_zengbb");
		
		// now make geologic pts plot
		CPT cpt = getLog10_SlipRateCPT();
		List<GeologicSlipRate> geoRates = GeologicSlipRateLoader.loadExcelFile(
				new URL("http://www.wgcep.org/sites/wgcep.org/files/UCERF3_Geologic_Slip%20Rates_version%203_2012_11_01.xls"));
		ArrayList<PSXYSymbol> symbols = Lists.newArrayList();
		ArrayList<Double> vals = Lists.newArrayList();
		
		for (GeologicSlipRate geoRate : geoRates) {
			Location loc = geoRate.getLocation();
			Point2D pt = new Point2D.Double(loc.getLongitude(), loc.getLatitude());
			double rate = geoRate.getValue();
			Symbol symbol = Symbol.CIRCLE;
//			if (geoRate.isRange())
			symbols.add(new PSXYSymbol(pt, symbol, 0.1d));
			vals.add(Math.log10(rate));
		}
		
		double penWidth = 1d;
		Color penColor = Color.BLACK;
		PSXYSymbolSet symbolSet = new PSXYSymbolSet(cpt, symbols, vals, penWidth, penColor, null);
		
		GMT_Map map = buildMap(cpt, new ArrayList<LocationList>(), new double[0], null, 1, region, true, "Log10 Slip Rate (mm/yr)");
		
		map.setSymbolSet(symbolSet);
		
		plotMap(saveDir, "dm_geo_sites", display, map);
	}
	
	public static void plotDeformationModelSlip(
			Region region, File saveDir, boolean display, FaultModels fm, DeformationModels dm, String prefix)
			throws IOException, GMT_MapException, RuntimeException {
		CPT cpt = getLog10_SlipRateCPT();
		
		List<LocationList> faults = Lists.newArrayList();
		List<Double> valsList = Lists.newArrayList();
		
		if (fm == FaultModels.FM2_1) {
			DeformationModelFetcher dmFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1);
			for (FaultSectionPrefData fault : dmFetch.getSubSectionList()) {
				faults.add(fault.getFaultTrace());
				valsList.add(fault.getOrigAveSlipRate());
			}
		} else {
			Map<Integer, DeformationSection> sects = DeformationModelFileParser.load(dm.getDataFileURL(fm));
			
			for (DeformationSection sect : sects.values()) {
				for (int i=0; i<sect.getLocs1().size(); i++) {
					LocationList locs = new LocationList();
					locs.add(sect.getLocs1().get(i));
					locs.add(sect.getLocs2().get(i));
					faults.add(locs);
					valsList.add(sect.getSlips().get(i));
				}
			}
		}
		
		double[] values = Doubles.toArray(valsList);
		// convert to log10 values
		for(int i=0;i<values.length;i++)
			values[i] = Math.log10(values[i]);
		
		makeFaultPlot(cpt, faults, values, region, saveDir, prefix, display, false, "Log10 Slip Rate (mm/yr)");
	}
	
	public static void plotDeformationModelSlipRatiosToGeol(Region region, File saveDir, boolean display)
			throws IOException, GMT_MapException, RuntimeException {
//		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM2_1, DeformationModels.UCERF2_ALL, "dm_ucerf2");
//		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_1, DeformationModels.GEOLOGIC, "dm_geol");
		plotDeformationModelSlipRatio(region, saveDir, display, FaultModels.FM3_1, DeformationModels.ABM, DeformationModels.GEOLOGIC, "dm_abm_vs_geol");
		plotDeformationModelSlipRatio(region, saveDir, display, FaultModels.FM3_1, DeformationModels.NEOKINEMA, DeformationModels.GEOLOGIC, "dm_neok_vs_geol");
//		plotDeformationModelSlip(region, saveDir, display, FaultModels.FM3_1, DeformationModels.GEOBOUND, "dm_geob");
		plotDeformationModelSlipRatio(region, saveDir, display, FaultModels.FM3_1, DeformationModels.ZENG, DeformationModels.GEOLOGIC, "dm_zeng_vs_geol");
	}
	
	public static void plotDeformationModelSlipRatio(
			Region region, File saveDir, boolean display, FaultModels fm, DeformationModels dm, DeformationModels ref, String prefix)
			throws IOException, GMT_MapException, RuntimeException {
		CPT cpt = getLogRatioCPT();
		
		List<LocationList> faults = Lists.newArrayList();
		List<Double> valsList = Lists.newArrayList();
		
		if (fm == FaultModels.FM2_1) {
			DeformationModelFetcher dmFetch1 = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1);
			DeformationModelFetcher dmFetch2 = new DeformationModelFetcher(fm, ref, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1);
			ArrayList<FaultSectionPrefData> sects1 = dmFetch1.getSubSectionList();
			ArrayList<FaultSectionPrefData> sects2 = dmFetch2.getSubSectionList();
			for (int i=0; i<sects1.size(); i++) {
				FaultSectionPrefData fault1 = sects1.get(i);
				FaultSectionPrefData fault2 = sects2.get(i);
				faults.add(fault1.getFaultTrace());
				valsList.add(fault1.getOrigAveSlipRate() / fault2.getOrigAveSlipRate());
			}
		} else {
			Map<Integer, DeformationSection> sects1 = DeformationModelFileParser.load(dm.getDataFileURL(fm));
			Map<Integer, DeformationSection> sects2 = DeformationModelFileParser.load(ref.getDataFileURL(fm));
			
			for (DeformationSection sect : sects1.values()) {
				DeformationSection sect2 = sects2.get(sect.getId());
				for (int i=0; i<sect.getLocs1().size(); i++) {
					LocationList locs = new LocationList();
					locs.add(sect.getLocs1().get(i));
					locs.add(sect.getLocs2().get(i));
					faults.add(locs);
					valsList.add(sect.getSlips().get(i) / sect2.getSlips().get(i));
				}
			}
		}
		
		double[] values = Doubles.toArray(valsList);
		for (int i=0; i<values.length; i++)
			values[i] = Math.log10(values[i]);
		
		String str = dm.getShortName()+"/"+ref.getShortName();
		
		makeFaultPlot(cpt, faults, values, region, saveDir, prefix, display, false, "Log10(Slip Rate Ratio, "+str+")");
	}
	
	private static Location getTraceMidpoint(FaultSectionPrefData fault) {
		return FaultUtils.resampleTrace(fault.getFaultTrace(), 10).get(5);
	}
	
	public static double[] scale(double[] values, double scalar) {
		double[] ret = new double[values.length];
		for (int i=0; i<values.length; i++)
			ret[i] = values[i] * scalar;
		return ret;
	}
	
	public static double[] log10(double[] values) {
		double[] ret = new double[values.length];
		for (int i=0; i<values.length; i++)
			ret[i] = Math.log10(values[i]);
		return ret;
	}
	
	public static ArrayList<LocationList> getTraces(List<FaultSectionPrefData> faults) {
		ArrayList<LocationList> faultTraces = new ArrayList<LocationList>();
		for (FaultSectionPrefData fault : faults)
			faultTraces.add(fault.getFaultTrace());
		return faultTraces;
	}
	
	private static class TraceValue implements Comparable<TraceValue> {
		private LocationList trace;
		private double value;
		public TraceValue(LocationList trace, double value) {
			this.trace = trace;
			this.value = value;
		}

		@Override
		public int compareTo(TraceValue o) {
			if (Double.isNaN(value))
				return -1;
			if (Double.isNaN(o.value))
				return 1;
			return Double.compare(value, o.value);
		}
		
	}
	
	public synchronized static void makeFaultPlot(CPT cpt, List<LocationList> faults, double[] values, Region region,
			File saveDir, String prefix, boolean display, boolean skipNans, String label)
					throws GMT_MapException, RuntimeException, IOException {
		GMT_Map map = buildMap(cpt, faults, values, null, 1, region, skipNans, label);
		
		plotMap(saveDir, prefix, display, map);
	}

	public static String plotMap(File saveDir, String prefix, boolean display,
			GMT_Map map) throws GMT_MapException, IOException {
		if (gmt == null) {
			gmt = new GMT_MapGenerator();
			gmt.getAdjustableParamsList().getParameter(Boolean.class, GMT_MapGenerator.LOG_PLOT_NAME).setValue(false);
			gmt.getAdjustableParamsList().getParameter(Boolean.class, GMT_MapGenerator.GMT_SMOOTHING_PARAM_NAME).setValue(false);
		}
		
		String baseURL;
		if (LOCAL_MAPGEN) {
			GMT_MapGenerator.clearEnv();
			map.setJPGFileName(null);
			File tempDir = Files.createTempDir();
			List<String> script = gmt.getGMT_ScriptLines(map, tempDir.getAbsolutePath());
			
			File scriptFile = new File(tempDir, "script.sh");
			
			if (map.getGriddedData() != null) {
				GeoDataSet griddedData = map.getGriddedData();
				griddedData.setLatitudeX(true);
				ArbDiscrGeoDataSet.writeXYZFile(griddedData, tempDir.getAbsolutePath()+"/"+new File(map.getXyzFileName()).getName());
			}
			
			FileWriter fw = new FileWriter(scriptFile);
			BufferedWriter bw = new BufferedWriter(fw);
			for (String line : script)
				bw.write(line + "\n");
			bw.close();
			
			String[] command = {
					"sh", "-c", "/bin/bash "+scriptFile.getAbsolutePath()+" > /dev/null 2> /dev/null"};
			RunScript.runScript(command);
			
			if (saveDir != null) {
				File pngFile = new File(tempDir, map.getPNGFileName());
				Preconditions.checkState(pngFile.exists(), "No PNG file: %s", pngFile.getAbsolutePath());
				File pdfFile = new File(tempDir, map.getPDFFileName());
				Preconditions.checkState(pdfFile.exists(), "No PDF file: %s", pdfFile.getAbsolutePath());
				
				Files.move(pngFile, new File(saveDir, prefix+".png"));
				Files.move(pdfFile, new File(saveDir, prefix+".pdf"));
			}
			
			FileUtils.deleteRecursive(tempDir);
			
			baseURL = null;
		} else {
			String url = gmt.makeMapUsingServlet(map, "metadata", null);
			System.out.println(url);
			baseURL = url.substring(0, url.lastIndexOf('/')+1);
			if (saveDir != null) {
				File pngFile = new File(saveDir, prefix+".png");
				FileUtils.downloadURL(baseURL+"map.png", pngFile);
				
				File pdfFile = new File(saveDir, prefix+".pdf");
				FileUtils.downloadURL(baseURL+"map.pdf", pdfFile);
			}
//			File zipFile = new File(downloadDir, "allFiles.zip");
//			// construct zip URL
//			String zipURL = url.substring(0, url.lastIndexOf('/')+1)+"allFiles.zip";
//			FileUtils.downloadURL(zipURL, zipFile);
//			FileUtils.unzipFile(zipFile, downloadDir);
			if (display) {
				String metadata = GMT_MapGuiBean.getClickHereHTML(gmt.getGMTFilesWebAddress());
				new ImageViewerWindow(url,metadata, true);
			}
		}
		
		return baseURL;
	}
	
	public static void makeFaultKML(CPT cpt, List<LocationList> faults, double[] values,
			File saveDir, String prefix, boolean skipNans, int numColorBins, int lineWidth,
			String name) throws IOException {
		makeFaultKML(cpt, faults, values, saveDir, prefix, skipNans, numColorBins, lineWidth, name, null);
	}
	
	public static void makeFaultKML(CPT cpt, List<LocationList> faults, double[] values,
			File saveDir, String prefix, boolean skipNans, int numColorBins, int lineWidth,
			String name, List<String> descriptions) throws IOException {
		Document doc = getFaultKML(cpt, faults, values, skipNans, numColorBins, lineWidth, name, descriptions);
		
		File outputFile = new File(saveDir, prefix+".kml");
		XMLUtils.writeDocumentToFile(outputFile, doc);
	}
	
	public static Document getFaultKML(CPT cpt, List<LocationList> faults, double[] values,
			boolean skipNans, int numColorBins, int lineWidth, String name, List<String> descriptions) {
		return getFaultKML(cpt, faults, values, skipNans, numColorBins, lineWidth, name, descriptions, 0d, -1);
	}
	
	public static Document getFaultKML(CPT cpt, List<LocationList> faults, double[] values,
			boolean skipNans, int numColorBins, int lineWidth, String name, List<String> descriptions,
			double bufferWidthKM, int bufferMaxPixels) {
		
		// discretize CPT file - KML files can't have continuous line colors
		double cptMin = cpt.getMinValue();
		double cptMax = cpt.getMaxValue();
		double cptDelta = (cptMax - cptMin)/(double)numColorBins;
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(cptMin+0.5*cptDelta, numColorBins, cptDelta);
		List<Color> cptColors = Lists.newArrayList();
		for (Point2D pt : func)
			cptColors.add(cpt.getColor((float)pt.getX()));
		
		Document doc = XMLUtils.createDocumentWithRoot("Document");
		Element docEl = doc.getRootElement();
		docEl.addElement("name").addText(name);
		
		// create style elements for discretized CPT file
		for (int i=0; i<numColorBins; i++)
			 addStyleEl(docEl, cptColors.get(i), lineWidth, "CPT_"+i);
		addStyleEl(docEl, cpt.getBelowMinColor(), lineWidth, "CPT_BELOW_MIN");
		addStyleEl(docEl, cpt.getAboveMaxColor(), lineWidth, "CPT_ABOVE_MAX");
		if (!skipNans)
			addStyleEl(docEl, cpt.getNaNColor(), lineWidth, "CPT_NAN");
		
		// add lines
		Element folderEl = docEl.addElement("Folder");
		folderEl.addElement("name").addText("Faults");
		
		for (int i=0; i<faults.size(); i++) {
			LocationList fault = faults.get(i);
			double val = values[i];
			String styleID;
			if (Double.isNaN(val) && skipNans)
				continue;
			if (val < cptMin)
				styleID = "CPT_BELOW_MIN";
			else if (val > cptMax)
				styleID = "CPT_ABOVE_MAX";
			else if (Double.isNaN(val))
				styleID = "CPT_NAN";
			else
				styleID = "CPT_"+func.getClosestXIndex(val);
			
			Element placemarkEl = folderEl.addElement("Placemark");
			String faultName = "Fault "+i;
			if (fault instanceof Named) {
				String tempName = ((Named)fault).getName();
				if (name != null)
					faultName = tempName;
			}
			placemarkEl.addElement("name").addText(faultName);
			if (descriptions != null)
				placemarkEl.addElement("description").addCDATA(descriptions.get(i));
			placemarkEl.addElement("styleUrl").addText("#"+styleID);
			Element lineStrEl = placemarkEl.addElement("LineString");
			lineStrEl.addElement("tesselate").addText("1");
			Element coordsEl = lineStrEl.addElement("coordinates");
			String coordsStr = "";
			for (Location loc : fault)
				coordsStr += loc.getLongitude()+","+loc.getLatitude()+",0\n";
			coordsEl.addText(coordsStr);
			
			// add LOD keyed to buffer around center
			if (bufferWidthKM > 0d && bufferMaxPixels > 0) {
				double bufferKM = 0.5*bufferWidthKM; // half in each direction
				Location firstLoc = fault.first();
				Location lastLoc = fault.last();
				Location middleLoc = new Location(0.5*(firstLoc.getLatitude()+lastLoc.getLatitude()),
						0.5*(firstLoc.getLongitude()+lastLoc.getLongitude()));
				Location north = LocationUtils.location(middleLoc, 0d, bufferKM);
				Location east = LocationUtils.location(middleLoc, Math.PI/2d, bufferKM);
				Location south = LocationUtils.location(middleLoc, Math.PI, bufferKM);
				Location west = LocationUtils.location(middleLoc, 1.5d*Math.PI, bufferKM);
				
				Element regEl = placemarkEl.addElement("Region");
				Element boxEl = regEl.addElement("LatLonAltBox");
				boxEl.addElement("north").setText(north.getLatitude()+"");
				boxEl.addElement("south").setText(south.getLatitude()+"");
				boxEl.addElement("east").setText(east.getLongitude()+"");
				boxEl.addElement("west").setText(west.getLongitude()+"");
				Element lodEl = regEl.addElement("Lod");
				lodEl.addElement("minLodPixels").setText("-1");
				lodEl.addElement("maxLodPixels").setText(bufferMaxPixels+"");
				lodEl.addElement("minFadeExtent").setText("-1");
				// fade out last 10%
				lodEl.addElement("maxFadeExtent").setText((int)(bufferMaxPixels*0.9)+"");
			}
		}
		
		return doc;
	}
	private static void addStyleEl(Element parent, Color c, int lineWidth, String label) {
		Element styleEl = parent.addElement("Style");
		styleEl.addAttribute("id", label);
		Element lineEl = styleEl.addElement("LineStyle");
		Element colorEl = lineEl.addElement("color");
		String hex = String.format("#%02x%02x%02x%02x", c.getAlpha(), c.getBlue(), c.getGreen(), c.getRed());
		colorEl.addText(hex);
		lineEl.addElement("width").addText(lineWidth+"");
	}
	
	public static final double FAULT_HIGHLIGHT_VALUE = -123456e20;
	
	public static GMT_Map buildMap(CPT cpt, List<LocationList> faults,
			double[] values, GeoDataSet griddedData, double spacing, Region region, boolean skipNans, String label) {
		GMT_Map map = new GMT_Map(region, griddedData, spacing, cpt);
		
		map.setBlackBackground(false);
		map.setRescaleCPT(false);
		map.setCustomScaleMin((double)cpt.getMinValue());
		map.setCustomScaleMax((double)cpt.getMaxValue());
		map.setCoast(new CoastAttributes(Color.BLACK, 0.5));
		map.setCustomLabel(label);
		map.setUseGMTSmoothing(false);
		map.setTopoResolution(null);
		
//		double thickness = 8;
		double thickness = 2;
		
		if (faults != null) {
			Preconditions.checkState(faults.size() == values.length, "faults and values are different lengths!");
			
			ArrayList<TraceValue> vals = new ArrayList<FaultBasedMapGen.TraceValue>();
			for (int i=0; i<faults.size(); i++) {
				if (skipNans && Double.isNaN(values[i]))
					continue;
				LocationList fault = faults.get(i);
				vals.add(new TraceValue(fault, values[i]));
			}
			Collections.sort(vals); // so that high values are on top
//			for (int i=1; i<vals.size(); i++)
//				Preconditions.checkState(Double.isNaN(vals.get(i).value)
//						|| vals.get(i).value >= vals.get(i-1).value, vals.get(i-1).value+", "+vals.get(i).value);
			
			try {
				FileWriter fw = new FileWriter(new File("/tmp/text.xy"));
				for (TraceValue val : vals) {
					LocationList fault = val.trace;
					double value = val.value;
					fw.write("> -Z"+value+"\n");
					for (Location loc : fault)
						fw.write(loc.getLongitude()+"\t"+loc.getLatitude()+"\n");
				}
				fw.close();
				cpt.writeCPTFile(new File("/tmp/cpt.cpt"));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			for (TraceValue val : vals) {
				LocationList fault = val.trace;
				double value = val.value;
				if ((float)value == (float)FAULT_HIGHLIGHT_VALUE) {
					Color c = Color.BLACK;
					for (PSXYPolygon poly : getPolygons(fault, c, 4*thickness))
						map.addPolys(poly);
				} else {
					Color c = cpt.getColor((float)value);
					for (PSXYPolygon poly : getPolygons(fault, c, thickness))
						map.addPolys(poly);
				}
			}
		}
		return map;
	}
	
	public static ArrayList<PSXYPolygon> getPolygons(LocationList locs, Color c, double thickness) {
		ArrayList<PSXYPolygon> polys = new ArrayList<PSXYPolygon>();
		
		for (int i=1; i<locs.size(); i++) {
			Location loc1 = locs.get(i-1);
			Location loc2 = locs.get(i);
			if (thickness > 10) {
				// this will make everything appear smoother for thick lines
				loc1 = LocationUtils.location(loc1, LocationUtils.azimuthRad(loc2, loc1), 0.1*thickness);
				loc2 = LocationUtils.location(loc2, LocationUtils.azimuthRad(loc1, loc2), 0.1*thickness);
			}
			PSXYPolygon poly = new PSXYPolygon(loc1, loc2);
			poly.setPenColor(c);
			poly.setPenWidth(thickness);
			polys.add(poly);
		}
		
		return polys;
	}
	
	public static void main(String[] args) throws IOException, DocumentException, GMT_MapException, RuntimeException {
		
//		getLog10_SlipRateCPT().writeCPTFile("Log10_SlipRate.cpt");
//		System.exit(0);
		
		File invSolsDir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions");
//		File solFile = new File(invSolsDir, "FM3_1_GLpABM_MaHB08_DsrTap_DrEllB_Char_VarAseis0.2_VarOffAseis0.5_VarMFDMod1_VarNone_sol.zip");
//		File solFile = new File(invSolsDir, "FM3_1_GLpABM_MaEllB_DsrTap_DrEllB_Char_VarAseis0.2_VarOffAseis0.5_VarMFDMod1_VarNone_sol.zip");
//		File solFile = new File(invSolsDir, "FM3_1_GLpABM_MaEllB_DsrTap_DrEllB_Char_VarAseis0.1_VarOffAseis0.5_VarMFDMod1_VarNone_PREVENT_run0_sol.zip");
//		File solFile = new File(invSolsDir, "FM3_1_GLpABM_MaEllB_DsrTap_DrEllB_Char_VarAseis0.1_VarOffAseis0_VarMFDMod1.3_VarNone_sol.zip");
		File solFile = new File(invSolsDir, "FM3_1_NEOK_EllB_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_sol.zip");
//		File solFile = new File("/tmp/ucerf2_fm2_compare.zip");
//		FaultSystemSolution sol = SimpleFaultSystemSolution.fromZipFile(solFile);
		InversionFaultSystemSolution sol = null;
		
		Region region = new CaliforniaRegions.RELM_TESTING();
		
		File saveDir = new File("/tmp");
		String prefix = solFile.getName().replaceAll(".zip", "");
		boolean display = false;
		
		plotDeformationModelSlips(region, saveDir, display);
//		plotDeformationModelSlipRatiosToGeol(region, saveDir, display);
		System.exit(0);
		
//		plotOrigNonReducedSlipRates(sol, region, saveDir, prefix, display);
//		plotOrigCreepReducedSlipRates(sol, region, saveDir, prefix, display);
//		plotTargetSlipRates(sol, region, saveDir, prefix, display);
//		plotSolutionSlipRates(sol, region, saveDir, prefix, display);
		plotSegmentation(sol, region, saveDir, prefix, display, 7, 10);
		plotSegmentation(sol, region, saveDir, prefix, display, 7.5, 10);
		System.exit(0);
		plotSolutionSlipMisfit(sol, region, saveDir, prefix, display, true);
		plotSolutionSlipMisfit(sol, region, saveDir, prefix, display, false);
//		plotParticipationRates(sol, region, saveDir, prefix, display, 6.7, 10);
//		plotParticipationRates(sol, region, saveDir, prefix, display, 6, 7);
//		plotParticipationRates(sol, region, saveDir, prefix, display, 7, 8);
//		plotParticipationRates(sol, region, saveDir, prefix, display, 8, 10);
		
		FaultSystemSolution ucerf2Sol =
				UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(sol.getRupSet());
//		for (int r=0; r<ucerf2Sol.getNumRuptures(); r++) {
//			double mag = ucerf2Sol.getMagForRup(r);
//			double rate = ucerf2_rates[r];
//			if (mag>=8 && rate > 0)
//				System.out.println("Nonzero M>=8!: "+r+": Mag="+mag+", rate="+rate);
//		}
//		plotParticipationRates(sol, region, saveDir, prefix, display, 6, 7);
//		plotParticipationRates(ucerf2Sol, region, saveDir, prefix, display, 6, 7);
		plotParticipationRatios(sol, ucerf2Sol, region, saveDir, prefix, display, 6, 7, true);
//		plotParticipationRates(sol, region, saveDir, prefix, display, 7, 8);
//		plotParticipationRates(ucerf2Sol, region, saveDir, prefix, display, 7, 8);
//		plotParticipationRatios(sol, ucerf2Sol, region, saveDir, prefix, display, 7, 8, true);
//		plotParticipationRates(sol, region, saveDir, prefix, display, 8, 10);
//		plotParticipationRates(ucerf2Sol, region, saveDir, prefix, display, 8, 10);
//		plotParticipationRatios(sol, ucerf2Sol, region, saveDir, prefix, display, 8, 10, true);
		
//		plotSectionPairRates(sol, region, saveDir, prefix, display);
	}

}
