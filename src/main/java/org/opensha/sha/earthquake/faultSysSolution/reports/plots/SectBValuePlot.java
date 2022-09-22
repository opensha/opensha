package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.jfree.data.Range;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectIDRange;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class SectBValuePlot extends AbstractSolutionPlot {
	
	static final EvenlyDiscretizedFunc refFunc = new EvenlyDiscretizedFunc(0.05, 110, 0.1);
	
	private static final double minB = -3;
	private static final double maxB = 3;

	@Override
	public String getName() {
		return "Section b-values";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		FaultSystemSolution compSol = meta.hasComparisonSol() ? meta.comparison.sol : null;
		if (compSol != null && !compSol.getRupSet().hasModule(AveSlipModule.class))
			compSol = null;
		if (compSol != null && !meta.comparisonHasSameSects)
			compSol = null;
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(sol.getRupSet(), meta.region);
		
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(minB, maxB);
		cpt.setNanColor(Color.GRAY);
		
		List<String> lines = new ArrayList<>();
		lines.add(getSubHeading()+" Subsection b-values");
		lines.add(topLink); lines.add("");
		
		lines.add("These plots estimate a Gutenberg-Richter b-value for each subsection and parent section nucleation"
				+ " MFD. This is a rough approximation, and is intended primarily for model comparisons.");
		lines.add("");
		
		BValEstimate[] sectBVals = estSectBValues(sol);
		BValEstimate[] compSectBVals = compSol == null ? null : estSectBValues(compSol);
		
		List<? extends IncrementalMagFreqDist> targetMFDs = null;
		if (sol.getRupSet().hasModule(InversionTargetMFDs.class))
			targetMFDs = sol.getRupSet().getModule(InversionTargetMFDs.class).getOnFaultSupraSeisNucleationMFDs();
		BValEstimate[] targetSectBVals = targetMFDs == null ? null : estSectTargetBValues(targetMFDs);
		
		double[] sectRates = sol.calcTotParticRateForAllSects();
		double[] compSectRates = compSol == null ? null : compSol.calcTotParticRateForAllSects();
		
		String prefix = "sect_b_values";
		
		System.out.println("Writing b-value CSV files");
		File sectCSV = new File(resourcesDir, prefix+".csv");
		writeSectCSV(sectCSV, sol.getRupSet().getFaultSectionDataList(), sectBVals, targetSectBVals);
		String downloadLine = "Download b-value CSV file"+(compSol == null ? "" : "s")+": ["
				+sectCSV.getName()+"]("+relPathToResources+"/"+sectCSV.getName()+")";
		if (compSectBVals != null) {
			File compSectCSV = new File(resourcesDir, prefix+"_comp.csv");
			writeSectCSV(compSectCSV, compSol.getRupSet().getFaultSectionDataList(), compSectBVals, null);
			
			downloadLine += " ["+compSectCSV.getName()+"]("+relPathToResources+"/"+compSectCSV.getName()+")";
		}
		lines.add(downloadLine);
		lines.add("");
		
		// map view
		mapMaker.plotSectScalars(toBArray(sectBVals), cpt, "Subsection b-values");
		mapMaker.plot(resourcesDir, prefix, getTruncatedTitle(meta.primary.name));
		
		if (compSol == null) {
			lines.add("![Section b-values Plot]("+relPathToResources+"/"+prefix+".png)");
		} else {
			TableBuilder table = MarkdownUtils.tableBuilder();
			
			mapMaker.plotSectScalars(toBArray(compSectBVals), cpt, "Subsection b-values");
			mapMaker.plot(resourcesDir, prefix+"_comp", getTruncatedTitle(meta.comparison.name));
			
			double[] diffs = new double[sectBVals.length];
			for (int i=0; i<diffs.length; i++)
				diffs[i] = sectBVals[i].b - compSectBVals[i].b;
			
			CPT diffCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-2d, 2d);
			diffCPT.setNanColor(Color.GRAY);
			mapMaker.plotSectScalars(diffs, diffCPT, "Subsection b-values, Primary - Comparison");
			mapMaker.plot(resourcesDir, prefix+"_diff", "Difference");
			
			table.addLine("![Section b-values Plot]("+relPathToResources+"/"+prefix+".png)",
					"![Section b-values Plot]("+relPathToResources+"/"+prefix+"_comp.png)",
					"![Section b-values Plot]("+relPathToResources+"/"+prefix+"_diff.png)");
			lines.addAll(table.build());
		}
		lines.add("");
		
		// histograms
		
		lines.addAll(getHistLines(toBArray(sectBVals), sectRates, toBArray(compSectBVals), compSectRates,
				targetSectBVals == null ? null : toBArray(targetSectBVals), resourcesDir, relPathToResources, prefix));
		
		lines.add("");
		lines.add(getSubHeading()+" Parent Section b-values");
		lines.add(topLink); lines.add("");
		
		prefix = "parent_sect_b_values";
		
		Map<Integer, BValEstimate> parentBValsMap = estParentSectBValues(sol);
		Map<Integer, String> parentNames = new HashMap<>();
		for (FaultSection sect : sol.getRupSet().getFaultSectionDataList())
			if (!parentNames.containsKey(sect.getParentSectionId()))
				parentNames.put(sect.getParentSectionId(), sect.getParentSectionName());
		BValEstimate[] parentBVals = new BValEstimate[parentBValsMap.size()];
		double[] parentRates = new double[parentBValsMap.size()];
		calcParentVals(sol, parentBVals, parentRates, parentBValsMap);
		
		BValEstimate[] parentTargetBVals = null;
		Map<Integer, BValEstimate> parentTargetBValsMap = null;
		if (targetMFDs != null) {
			parentTargetBValsMap = estParentSectTargetBValues(sol, targetMFDs);
			parentTargetBVals = new BValEstimate[parentTargetBValsMap.size()];
			calcParentVals(sol, parentTargetBVals, null, parentTargetBValsMap);
		}
		
		BValEstimate[] compParentBVals = null;
		double[] compParentRates = null;
		Map<Integer, BValEstimate> compParentBValsMap = null;
		if (compSol != null) {
			compParentBValsMap = estParentSectBValues(compSol);
			compParentBVals = new BValEstimate[compParentBValsMap.size()];
			compParentRates = new double[compParentBValsMap.size()];
			calcParentVals(sol, compParentBVals, compParentRates, compParentBValsMap);
		}
		
		System.out.println("Writing b-value CSV files");
		sectCSV = new File(resourcesDir, prefix+".csv");
		writeParentSectCSV(sectCSV, parentNames, parentBValsMap, parentTargetBValsMap);
		downloadLine = "Download b-value CSV file"+(compSol == null ? "" : "s")+": ["
				+sectCSV.getName()+"]("+relPathToResources+"/"+sectCSV.getName()+")";
		if (compSectBVals != null) {
			File compSectCSV = new File(resourcesDir, prefix+"_comp.csv");
			writeParentSectCSV(compSectCSV, parentNames, compParentBValsMap, parentTargetBValsMap);
			
			downloadLine += " ["+compSectCSV.getName()+"]("+relPathToResources+"/"+compSectCSV.getName()+")";
		}
		lines.add(downloadLine);
		lines.add("");
		
		lines.addAll(getHistLines(toBArray(parentBVals), parentRates, toBArray(compParentBVals), compParentRates,
				parentTargetBVals == null ? null : toBArray(parentTargetBVals), resourcesDir, relPathToResources, prefix));
		
		return lines;
	}
	
	private static void calcParentVals(FaultSystemSolution sol, BValEstimate[] parentBVals, double[] parentRates,
			Map<Integer, BValEstimate> parentBValsMap) {
		Preconditions.checkState(parentBVals.length == parentBValsMap.size());
		List<Integer> parentIDs = new ArrayList<>(parentBValsMap.keySet());
		Collections.sort(parentIDs);
		
		for (int i=0; i<parentIDs.size(); i++) {
			int parentID = parentIDs.get(i);
			
			parentBVals[i] = parentBValsMap.get(parentID);
			if (parentRates != null) {
				parentRates[i] = 0;
				for (int r : sol.getRupSet().getRupturesForParentSection(parentID))
					parentRates[i] += sol.getRateForRup(r);
			}
		}
	}
	
	public static double[] calcRupMoments(FaultSystemRupSet rupSet) {
		AveSlipModule aveSlips = rupSet.requireModule(AveSlipModule.class);
		
		double[] ret = new double[rupSet.getNumRuptures()];
		for (int r=0; r<ret.length; r++)
			ret[r] = FaultMomentCalc.getMoment(rupSet.getAreaForRup(r), aveSlips.getAveSlip(r));
		return ret;
	}
	
	static double calcSectMomentRate(FaultSystemRupSet rupSet, double[] rupMoRates, boolean nucleation, int sectIndex) {
		double sectArea = rupSet.getAreaForSection(sectIndex);
		double ret = 0d;
		for (int r : rupSet.getRupturesForSection(sectIndex)) {
			if (nucleation)
				ret += rupMoRates[r]*sectArea/rupSet.getAreaForRup(r);
			else
				ret += rupMoRates[r];
		}
		return ret;
	}
	
	static double[] calcSectMomentRates(FaultSystemRupSet rupSet, double[] rupMoRates, boolean nucleation) {
		double[] ret = new double[rupSet.getNumSections()];
		for (int s=0; s<ret.length; s++)
			ret[s] = calcSectMomentRate(rupSet, rupMoRates, nucleation, s);
		return ret;
	}
	
	private static BValEstimate[] estSectBValues(FaultSystemSolution sol) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		BValEstimate[] ret = new BValEstimate[rupSet.getNumSections()];
		
		ModSectMinMags modMinMags = rupSet.getModule(ModSectMinMags.class);
		
		RupMFDsModule rupMFDs = sol.getModule(RupMFDsModule.class);
		
		for (int s=0; s<ret.length; s++) {
			boolean[] binsAvail = new boolean[refFunc.size()];
			boolean[] binsUsed = new boolean[refFunc.size()];
			calcSectMags(s, sol, modMinMags, rupMFDs, binsAvail, binsUsed);
			IncrementalMagFreqDist sectMFD = sol.calcNucleationMFD_forSect(
					s, refFunc.getX(0), refFunc.getX(refFunc.size()-1), refFunc.size());
			
			ret[s] = estBValue(binsAvail, binsUsed, sectMFD);
		}
		
		return ret;
	}
	
	static void calcSectMags(int sectionIndex, FaultSystemSolution sol, ModSectMinMags modMinMags,
			RupMFDsModule rupMFDs, boolean[] binsAvail, boolean[] binsUsed) {
		Preconditions.checkState(refFunc.size() == binsAvail.length);
		Preconditions.checkState(refFunc.size() == binsUsed.length);
		FaultSystemRupSet rupSet = sol.getRupSet();
		for (int rupIndex : rupSet.getRupturesForSection(sectionIndex)) {
			DiscretizedFunc rupMFD = null;
			if (rupMFDs != null)
				rupMFD = rupMFDs.getRuptureMFD(rupIndex);
			if (rupMFD == null) {
				rupMFD = new ArbitrarilyDiscretizedFunc();
				rupMFD.set(rupSet.getMagForRup(rupIndex), sol.getRateForRup(rupIndex));
			}
			for (Point2D pt : rupMFD) {
				double mag = pt.getX();
				if (modMinMags != null && modMinMags.isBelowSectMinMag(sectionIndex, mag, refFunc))
					continue;
				double rate = pt.getY();
				int magIndex = refFunc.getClosestXIndex(mag);
				binsAvail[magIndex] = true;
				if (rate > 0)
					binsUsed[magIndex] = true;
			}
		}
	}
	
	private static BValEstimate[] estSectTargetBValues(List<? extends IncrementalMagFreqDist> sectNuclMFDs) {
		BValEstimate[] ret = new BValEstimate[sectNuclMFDs.size()];
		
		for (int s=0; s<ret.length; s++) {
			boolean[] bins = new boolean[refFunc.size()];
			IncrementalMagFreqDist sectMFD = new IncrementalMagFreqDist(
					refFunc.getMinX(), refFunc.size(), refFunc.getDelta());
			IncrementalMagFreqDist target = sectNuclMFDs.get(s);
			
			boolean any = false;
			for (Point2D pt : target) {
				if (pt.getY() > 0d) {
					int binIndex = sectMFD.getClosestXIndex(pt.getX());
					sectMFD.add(binIndex, pt.getY());
					bins[binIndex] = true;
					any = true;
				}
			}
			
			// available == used for target
			if (any)
				ret[s] = estBValue(bins, bins, sectMFD);
			else
				ret[s] = new BValEstimate(0d, 0d, 0d, bins, bins);
		}
		
		return ret;
	}
	
	private static Map<Integer, BValEstimate> estParentSectBValues(FaultSystemSolution sol) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		HashMap<Integer, BValEstimate> ret = new HashMap<>();
		
		Map<Integer, List<FaultSection>> sectsByParent = rupSet.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		
		ModSectMinMags modMinMags = rupSet.getModule(ModSectMinMags.class);
		
		RupMFDsModule rupMFDs = sol.getModule(RupMFDsModule.class);
		
		for (int p : sectsByParent.keySet()) {
			boolean[] binsAvail = new boolean[refFunc.size()];
			boolean[] binsUsed = new boolean[refFunc.size()];
			
			List<FaultSection> sects = sectsByParent.get(p);
			for (FaultSection sect : sects) {
				int s = sect.getSectionId();
				calcSectMags(s, sol, modMinMags, rupMFDs, binsAvail, binsUsed);
			}
			
			IncrementalMagFreqDist parentMFD = sol.calcNucleationMFD_forParentSect(
					p, refFunc.getMinX(), refFunc.getMaxX(), refFunc.size());
			
			ret.put(p, estBValue(binsAvail, binsUsed, parentMFD));
		}
		
		return ret;
	}
	
	private static Map<Integer, BValEstimate> estParentSectTargetBValues(FaultSystemSolution sol,
			List<? extends IncrementalMagFreqDist> sectNuclMFDs) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		HashMap<Integer, BValEstimate> ret = new HashMap<>();
		
		Map<Integer, List<FaultSection>> sectsByParent = rupSet.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		
		for (int p : sectsByParent.keySet()) {
			boolean[] bins = new boolean[refFunc.size()];
			
			IncrementalMagFreqDist parentMFD = new IncrementalMagFreqDist(
					refFunc.getMinX(), refFunc.size(), refFunc.getDelta());
			List<FaultSection> sects = sectsByParent.get(p);
			boolean any = false;
			for (FaultSection sect : sects) {
				int s = sect.getSectionId();
				IncrementalMagFreqDist target = sectNuclMFDs.get(s);
				for (Point2D pt : target) {
					if (pt.getY() > 0d) {
						int binIndex = parentMFD.getClosestXIndex(pt.getX());
						parentMFD.add(binIndex, pt.getY());
						bins[binIndex] = true;
						any = true;
					}
				}
			}
			
			// available == used for target
			if (any)
				ret.put(p, estBValue(bins, bins, parentMFD));
			else
				ret.put(p, new BValEstimate(0d, 0d, 0d, bins, bins));
		}
		
		return ret;
	}
	
	private static double[] toBArray(BValEstimate[] vals) {
		if (vals == null)
			return null;
		double[] ret = new double[vals.length];
		for (int i=0; i<ret.length; i++)
			ret[i] = vals[i].b;
		return ret;
	}
	
	static BValEstimate estBValue(boolean[] binsAvail, boolean[] binsUsed, IncrementalMagFreqDist nuclMFD) {
		Preconditions.checkState(refFunc.size() == binsAvail.length);
		Preconditions.checkState(refFunc.size() == binsUsed.length);
		
		int minMagIndex = -1;
		int maxMagIndex = 0;
		for (int i=0; i<binsAvail.length; i++) {
			if (binsAvail[i]) {
				if (minMagIndex < 0)
					minMagIndex = i;
				maxMagIndex = i;
			}
		}
		
		double minMag = refFunc.getX(minMagIndex);
		double maxMag = refFunc.getX(maxMagIndex);
		
		// do it this way because soas to ignore any rates below the first magnitude bin, which could be possible
		// if there is a modified sect min mag and those ruptures weren't forbidden in the inversion
		double supraRate = 0d;
		double moRate = 0d;
		
		for (int i=minMagIndex; i<=maxMagIndex; i++) {
			supraRate += nuclMFD.getY(i);
			moRate += nuclMFD.getMomentRate(i);
		}
		double b = estBValue(minMag, maxMag, supraRate, moRate);
		
		return new BValEstimate(b, supraRate, moRate, binsAvail, binsUsed);
	}
	
	public static class BValEstimate {
		public final double b;
		public final double supraRate;
		public final double moRate;
		public final double minMagBin;
		public final double maxMagBin;
		public final int numBins;
		public final int numBinsAvailable;
		public final int numBinsUsed;
		
		public BValEstimate(double b, double supraRate, double moRate, boolean[] binsAvail, boolean[] binsUsed) {
			this.b = b;
			this.supraRate = supraRate;
			this.moRate = moRate;
			int minIndex = -1;
			int maxIndex = 0;
			int numBinsAvailable = 0;
			int numBinsUsed = 0;
			for (int i=0; i<binsAvail.length; i++) {
				if (binsAvail[i]) {
					numBinsAvailable++;
					if (minIndex < 0)
						minIndex = i;
					maxIndex = i;
					if (binsUsed[i])
						numBinsUsed++;
				}
			}
			this.numBins = 1+maxIndex-minIndex;
			this.minMagBin = minIndex >= 0 ? refFunc.getX(minIndex) : Double.NaN;
			this.maxMagBin = maxIndex >= 0 ? refFunc.getX(maxIndex) : Double.NaN;
			this.numBinsAvailable = numBinsAvailable;
			this.numBinsUsed = numBinsUsed;
		}
		
		private static List<String> tableHeader(String... initialCols) {
			List<String> line = new ArrayList<>();
			if (initialCols != null)
				for (String col : initialCols)
					line.add(col);
			line.add("b-value");
			line.add("supra-seis event rate");
			line.add("supra-seis moment rate");
			line.add("min mag (binned)");
			line.add("max mag (binned)");
			line.add("# supra-seis mag bins");
			line.add("# supra-seis mag bins w/ available rupture");
			line.add("# supra-seis mag bins w/ nonzero rate");
			return line;
		}
		
		private List<String> tableLine(String... initialCols) {
			List<String> line = new ArrayList<>();
			if (initialCols != null)
				for (String col : initialCols)
					line.add(col);
			line.add((float)b+"");
			line.add(supraRate+"");
			line.add(moRate+"");
			line.add((float)minMagBin+"");
			line.add((float)maxMagBin+"");
			line.add(numBins+"");
			line.add(numBinsAvailable+"");
			line.add(numBinsUsed+"");
			return line;
		}
	}
	
	public static double estBValue(double minMag, double maxMag, double supraRate, double moRate) {
		Preconditions.checkState(minMag >= refFunc.getMinX()-0.5);
		Preconditions.checkState(maxMag <= refFunc.getMaxX()+0.5);
		
		int binnedMinIndex = refFunc.getClosestXIndex(minMag);
		int binnedMaxIndex = refFunc.getClosestXIndex(maxMag);
		if (binnedMinIndex == binnedMaxIndex)
			return 0d;
		if (binnedMinIndex > binnedMaxIndex)
			// binnedMinIndex can be < binnedMaxIndex if max mag is less than global section minimum magnitude
			return Double.NaN;
		Preconditions.checkState(binnedMaxIndex > binnedMinIndex);
		minMag = refFunc.getX(binnedMinIndex);
		maxMag = refFunc.getX(binnedMaxIndex);
		
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(minMag,
				1+binnedMaxIndex-binnedMinIndex, refFunc.getDelta());
		gr.setAllButBvalue(minMag, maxMag, moRate, supraRate);
		
		return gr.get_bValue();
	}
	
	private static List<String> getHistLines(double[] bValues, double[] rates, double[] compBValues, double[] compRates,
			double[] targetBValues, File outputDir, String relPath, String prefix) throws IOException {
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		if (compBValues == null)
			table.addLine("b-value Distribution", "b-value Rate-Dependence");
		else
			table.addLine("Primary b-value Distribution", "Primary b-value Rate-Dependence");
		
		File histPlot = histPlot(outputDir, prefix+"_hist", bValues, "b-value", MAIN_COLOR);
		File scatterPlot = rateScatterPlot(outputDir, prefix+"_rate_scatter", bValues, rates, MAIN_COLOR);
		
		table.addLine("![Histogram]("+relPath+"/"+histPlot.getName()+")",
				"![Scatter]("+relPath+"/"+scatterPlot.getName()+")");
		
		if (targetBValues != null)
			compHistLines(bValues, targetBValues, outputDir, relPath, prefix+"_target", table, Color.CYAN.darker(), "Target");
		
		if (compBValues != null) {
			File cHistPlot = histPlot(outputDir, prefix+"_hist_comp", compBValues, "b-value", COMP_COLOR);
			File cScatterPlot = rateScatterPlot(outputDir, prefix+"_rate_scatter_comp", compBValues, compRates, COMP_COLOR);

			table.addLine(MarkdownUtils.boldCentered("Comparison B-Value Distribution"),
					MarkdownUtils.boldCentered("Comparison b-value Rate-Dependence"));
			table.addLine("![Histogram]("+relPath+"/"+cHistPlot.getName()+")",
					"![Scatter]("+relPath+"/"+cScatterPlot.getName()+")");
			compHistLines(bValues, compBValues, outputDir, relPath, prefix+"_comp", table, COMMON_COLOR, "Comparison");
		}
		
		return table.build();
	}

	public static void compHistLines(double[] bValues, double[] compBValues, File outputDir,
			String relPath, String prefix, TableBuilder table, Color color, String compName) throws IOException {
		double[] diffs = new double[bValues.length];
		for (int i=0; i<diffs.length; i++)
			diffs[i] = bValues[i] - compBValues[i];
		
		File histDiffPlot = histPlot(outputDir, prefix+"_hist_diff", diffs, "Primary - "+compName+" b-value", color);
		File compScatterPlot = compScatterPlot(outputDir, prefix+"_scatter", bValues, compBValues, color);
		
		table.addLine(MarkdownUtils.boldCentered(compName+" b-value Difference"), MarkdownUtils.boldCentered(compName+" b-value Scatter"));
		table.addLine("![Histogram]("+relPath+"/"+histDiffPlot.getName()+")",
				"![Scatter]("+relPath+"/"+compScatterPlot.getName()+")");
	}
	private static File histPlot(File outputDir, String prefix, double[] values, String xAxisLabel,
			Color color) throws IOException {
		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(minB+0.01, maxB-0.01, 0.05);
		
		for (double value : values)
			if (Double.isFinite(value))
				hist.add(hist.getClosestXIndex(value), 1d);
		
		List<HistogramFunction> funcs = new ArrayList<>();
		funcs.add(hist);
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", xAxisLabel, "Count");
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		Range xRange = new Range(minB, maxB);
		gp.drawGraphPanel(spec, false, false, xRange, null);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, 650, true, false, false);
		return new File(outputDir, prefix+".png");
	}
	
	private static double withinBRange(double val) {
		if (val < minB)
			return minB;
		if (val > maxB)
			return maxB;
		return val;
	}
	
	private static File rateScatterPlot(File outputDir, String prefix, double[] bValues, double[] rates,
			Color color) throws IOException {
		XY_DataSet scatter = new DefaultXY_DataSet();
		
		for (int i=0; i<bValues.length; i++)
			scatter.set(rates[i], withinBRange(bValues[i]));
		
		List<XY_DataSet> funcs = new ArrayList<>();
		funcs.add(scatter);
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, color));
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", "Supra-Seismogenic Rate", "B-Value");
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		Range xRange = new Range(1e-6, 1e0);
		Range yRange = new Range(minB, maxB);
		gp.drawGraphPanel(spec, true, false, xRange, yRange);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, 650, true, false, false);
		return new File(outputDir, prefix+".png");
	}
	
	private static File compScatterPlot(File outputDir, String prefix, double[] bValues1, double[] bValues2,
			Color color) throws IOException {
		XY_DataSet scatter = new DefaultXY_DataSet();
		
		for (int i=0; i<bValues1.length; i++)
			scatter.set(withinBRange(bValues1[i]), withinBRange(bValues2[i]));
		
		List<XY_DataSet> funcs = new ArrayList<>();
		funcs.add(scatter);
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, color));
		
		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(minB, minB);
		line.set(maxB, maxB);
		funcs.add(line);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", "Primary B-Value", "Comparison B-Value");
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		Range range = new Range(minB, maxB);
		gp.drawGraphPanel(spec, false, false, range, range);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, -1, true, false, false);
		return new File(outputDir, prefix+".png");
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(AveSlipModule.class);
	}
	
	private static void writeSectCSV(File outputFile, List<? extends FaultSection> sects, BValEstimate[] bVals,
			BValEstimate[] bValTargets) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<String> header = BValEstimate.tableHeader("sect index", "sect name");
		if (bValTargets != null) {
			header.add("target MFD b-value");
			header.add("target supra-seis event rate");
			header.add("target supra-seis moment rate");
		}
		csv.addLine(header);
		for (int s=0; s<sects.size(); s++) {
			FaultSection sect = sects.get(s);
			List<String> line = bVals[s].tableLine(s+"", sect.getName());
			if (bValTargets != null) {
				line.add((float)bValTargets[s].b+"");
				line.add(bValTargets[s].supraRate+"");
				line.add(bValTargets[s].moRate+"");
			}
			csv.addLine(line);
		}
		
		csv.writeToFile(outputFile);
	}
	
	private static void writeParentSectCSV(File outputFile, Map<Integer, String> parentNames,
			Map<Integer, BValEstimate> bVals, Map<Integer, BValEstimate> bValTargets) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<Integer> parentIDs = ComparablePairing.getSortedData(parentNames);
		
		List<String> header = BValEstimate.tableHeader("parent sect ID", "parent sect name");
		if (bValTargets != null) {
			header.add("target MFD b-value");
			header.add("target supra-seis event rate");
			header.add("target supra-seis moment rate");
		}
		csv.addLine(header);
		for (int parentID : parentIDs) {
			String name = parentNames.get(parentID);
			List<String> line = bVals.get(parentID).tableLine(parentID+"", name);
			if (bValTargets != null) {
				BValEstimate target = bValTargets.get(parentID);
				line.add((float)target.b+"");
				line.add(target.supraRate+"");
				line.add(target.moRate+"");
			}
			csv.addLine(line);
		}
		
		csv.writeToFile(outputFile);
	}
	
	public static void main(String[] args) throws IOException {
		File solFile = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_11_03-reproduce-ucerf3-ref_branch-uniform-nshm23_draft-supra_b_0.8-2h/mean_solution.zip");
		File compSolFile = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular/"
				+ "FM3_1_ZENGBB_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3.zip");
		
		File outputDir = new File("/tmp/report");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		FaultSystemSolution sol = FaultSystemSolution.load(solFile);
		FaultSystemSolution compSol = FaultSystemSolution.load(compSolFile);
		
		ReportMetadata meta = new ReportMetadata(new RupSetMetadata("Primary", sol), new RupSetMetadata("Comparison", compSol));
		
		ReportPageGen gen = new ReportPageGen(meta, outputDir, List.of(new SectBValuePlot(), new ParticipationRatePlot()));
		gen.setReplot(true);
		
		gen.generatePage();
	}

}
