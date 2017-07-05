package scratch.UCERF3.utils.paleoRateConstraints;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotMultiDataLayer;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.StatUtil;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.commons.gui.plot.GraphWindow;

import scratch.UCERF3.AverageFaultSystemSolution;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.FaultSystemSolutionFetcher;
import scratch.UCERF3.SlipEnabledRupSet;
import scratch.UCERF3.SlipEnabledSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.UCERF2_ComparisonSolutionFetcher;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoFitPlotter.AveSlipFakePaleoConstraint;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

public class PaleoFitPlotter {
	
	// for thin lines/circles
	private static final PlotSymbol CONFIDENCE_BOUND_SYMBOL = PlotSymbol.DASH;
	private static final PlotSymbol DATA_SYMBOL = PlotSymbol.CIRCLE;
	private static final float CONFIDENCE_BOUND_WIDTH = 1f;
	
	// for thick lines/circles
//	private static final PlotSymbol CONFIDENCE_BOUND_SYMBOL = PlotSymbol.BOLD_DASH;
//	private static final PlotSymbol DATA_SYMBOL = PlotSymbol.FILLED_CIRCLE;
//	private static final float CONFIDENCE_BOUND_WIDTH = 2f;

	public static class AveSlipFakePaleoConstraint extends PaleoRateConstraint {
		private boolean isMultiple;
		private double origAveSlip, origAveSlipUpper, origAveSlipLower;
		
		public AveSlipFakePaleoConstraint(AveSlipConstraint aveSlip, int sectIndex, double slipRate) {
			super(null, aveSlip.getSiteLocation(), sectIndex, slipRate/aveSlip.getWeightedMean(),
					slipRate/aveSlip.getLowerUncertaintyBound(), slipRate/aveSlip.getUpperUncertaintyBound());
			isMultiple = false;
			origAveSlip = aveSlip.getWeightedMean();
			origAveSlipLower = aveSlip.getLowerUncertaintyBound();
			origAveSlipUpper = aveSlip.getUpperUncertaintyBound();
		}
		
		public AveSlipFakePaleoConstraint(
				AveSlipConstraint aveSlip, int sectIndex, double[] slipRates, double[] weights) {
			super(null, aveSlip.getSiteLocation(), sectIndex,
					FaultSystemSolutionFetcher.calcScaledAverage(slipRates, weights)/aveSlip.getWeightedMean(),
					StatUtils.min(slipRates)/aveSlip.getWeightedMean(),
					StatUtils.max(slipRates)/aveSlip.getWeightedMean());
			isMultiple = true;
			origAveSlip = aveSlip.getWeightedMean();
			origAveSlipLower = aveSlip.getLowerUncertaintyBound();
			origAveSlipUpper = aveSlip.getUpperUncertaintyBound();
		}
	}
	
	public static PlotSpec getSegRateComparisonSpec(
				List<PaleoRateConstraint> paleoRateConstraint,
				List<AveSlipConstraint> aveSlipConstraints,
				FaultSystemSolution sol) {
			Preconditions.checkState(paleoRateConstraint.size() > 0, "Must have at least one rate constraint");
			Preconditions.checkNotNull(sol, "Solution cannot me null!");
			
			FaultSystemRupSet rupSet = sol.getRupSet();
			
			boolean plotAveSlipBars = true;
			
			List<FaultSectionPrefData> datas = rupSet.getFaultSectionDataList();
			
			ArrayList<DiscretizedFunc> funcs = new ArrayList<DiscretizedFunc>();
			Map<Integer, DiscretizedFunc> funcParentsMap = Maps.newHashMap();
			ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
			
			Color paleoProbColor = Color.RED;
			
			ArbitrarilyDiscretizedFunc paleoRateMean = new ArbitrarilyDiscretizedFunc();
			paleoRateMean.setName("Paleo Rate Constraint: Mean");
			funcs.add(paleoRateMean);
			plotChars.add(new PlotCurveCharacterstics(DATA_SYMBOL, 5f, paleoProbColor));
			ArbitrarilyDiscretizedFunc paleoRateUpper = new ArbitrarilyDiscretizedFunc();
			paleoRateUpper.setName("Paleo Rate Constraint: Upper 95% Confidence");
			funcs.add(paleoRateUpper);
			plotChars.add(new PlotCurveCharacterstics(CONFIDENCE_BOUND_SYMBOL, 5f, paleoProbColor));
			ArbitrarilyDiscretizedFunc paleoRateLower = new ArbitrarilyDiscretizedFunc();
			paleoRateLower.setName("Paleo Rate Constraint: Lower 95% Confidence");
			funcs.add(paleoRateLower);
			plotChars.add(new PlotCurveCharacterstics(CONFIDENCE_BOUND_SYMBOL, 5f, paleoProbColor));
			
			ArbitrarilyDiscretizedFunc aveSlipRateMean = null;
			ArbitrarilyDiscretizedFunc aveSlipRateUpper = null;
			ArbitrarilyDiscretizedFunc aveSlipRateLower = null;
			
			// create new list since we might modify it
			paleoRateConstraint = Lists.newArrayList(paleoRateConstraint);
			
			Color aveSlipColor = new Color(10, 100, 55);
			
			if (aveSlipConstraints != null) {
				aveSlipRateMean = new ArbitrarilyDiscretizedFunc();
				aveSlipRateMean.setName("Ave Slip Rate Constraint: Mean");
				funcs.add(aveSlipRateMean);
				plotChars.add(new PlotCurveCharacterstics(DATA_SYMBOL, 5f, aveSlipColor));
				
				if (plotAveSlipBars) {
					aveSlipRateUpper = new ArbitrarilyDiscretizedFunc();
					aveSlipRateUpper.setName("Ave Slip Rate Constraint: Upper 95% Confidence");
					funcs.add(aveSlipRateUpper);
					plotChars.add(new PlotCurveCharacterstics(CONFIDENCE_BOUND_SYMBOL, 5f, aveSlipColor));
					
					aveSlipRateLower = new ArbitrarilyDiscretizedFunc();
					aveSlipRateLower.setName("Ave Slip Rate Constraint: Lower 95% Confidence");
					funcs.add(aveSlipRateLower);
					plotChars.add(new PlotCurveCharacterstics(CONFIDENCE_BOUND_SYMBOL, 5f, aveSlipColor));
				}
				
				for (AveSlipConstraint aveSlip : aveSlipConstraints) {
					paleoRateConstraint.add(new PaleoFitPlotter.AveSlipFakePaleoConstraint(aveSlip, aveSlip.getSubSectionIndex(),
							rupSet.getSlipRateForSection(aveSlip.getSubSectionIndex())));
				}
			}
			
			final int xGap = 5;
			
			PaleoProbabilityModel paleoProbModel = null;
			try {
				paleoProbModel = UCERF3_PaleoProbabilityModel.load();
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
			
			int x = xGap;
			
			HashMap<Integer, Integer> xIndForParentMap = new HashMap<Integer, Integer>();
			
			double runningMisfitTotal = 0d;
			
			Color origColor = Color.BLACK;
			
			for (int p=0; p<paleoRateConstraint.size(); p++) {
				PaleoRateConstraint constr = paleoRateConstraint.get(p);
				int sectID = constr.getSectionIndex();
				int parentID = -1;
				String name = null;
				for (FaultSectionPrefData data : datas) {
					if (data.getSectionId() == sectID) {
						if (data.getParentSectionId() < 0)
							throw new IllegalStateException("parent ID isn't populated for solution!");
						parentID = data.getParentSectionId();
						name = data.getParentSectionName();
						break;
					}
				}
				if (parentID < 0) {
					System.err.println("No match for rate constraint for section "+sectID);
					continue;
				}
				
				int minSect = Integer.MAX_VALUE;
				int maxSect = -1;
				for (FaultSectionPrefData data : datas) {
					if (data.getParentSectionId() == parentID) {
						int mySectID = data.getSectionId();
						if (mySectID < minSect)
							minSect = mySectID;
						if (mySectID > maxSect)
							maxSect = mySectID;
					}
				}
				
				Preconditions.checkState(maxSect >= minSect);
				int numSects = maxSect - minSect;
				
				int relConstSect = sectID - minSect;
				
				double paleoRateX;
				
				if (xIndForParentMap.containsKey(parentID)) {
					// we already have this parent section, just add the new rate constraint
					
					paleoRateX = xIndForParentMap.get(parentID) + relConstSect;
				} else {
					paleoRateX = x + relConstSect;
					
					EvenlyDiscretizedFunc paleoRtFunc = new EvenlyDiscretizedFunc((double)x, numSects, 1d);
					EvenlyDiscretizedFunc aveSlipRtFunc = new EvenlyDiscretizedFunc((double)x, numSects, 1d);
					EvenlyDiscretizedFunc origRtFunc = new EvenlyDiscretizedFunc((double)x, numSects, 1d);
					paleoRtFunc.setName("(x="+x+") Solution paleo rates for: "+name);
					aveSlipRtFunc.setName("(x="+x+") Solution ave slip prob visible rates for: "+name);
					origRtFunc.setName("(x="+x+") Solution original rates for: "+name);
					InversionFaultSystemSolution invSol = null;
					if (sol instanceof InversionFaultSystemSolution)
						invSol = (InversionFaultSystemSolution)sol;
					for (int j=0; j<numSects; j++) {
						int mySectID = minSect + j;
						paleoRtFunc.set(j, getPaleoRateForSect(sol, mySectID, paleoProbModel));
						origRtFunc.set(j, getPaleoRateForSect(sol, mySectID, null));
						if (invSol != null)
							aveSlipRtFunc.set(j, getAveSlipProbRateForSect(invSol, mySectID));
					}
					funcs.add(origRtFunc);
					plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, origColor));
					if (invSol != null) {
						funcs.add(aveSlipRtFunc);
						plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, aveSlipColor));
					}
					funcs.add(paleoRtFunc);
					plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, paleoProbColor));
					
					funcParentsMap.put(parentID, paleoRtFunc);
					
					xIndForParentMap.put(parentID, x);
					
					x += numSects;
					x += xGap;
				}
				
				if (constr instanceof PaleoFitPlotter.AveSlipFakePaleoConstraint) {
					aveSlipRateMean.set(paleoRateX, constr.getMeanRate());
					if (plotAveSlipBars) {
						aveSlipRateUpper.set(paleoRateX, constr.getUpper95ConfOfRate());
						aveSlipRateLower.set(paleoRateX, constr.getLower95ConfOfRate());
					}
				} else {
					DiscretizedFunc func = funcParentsMap.get(parentID);
					double rate = getPaleoRateForSect(sol, sectID, paleoProbModel);
	//					double misfit = Math.pow(constr.getMeanRate() - rate, 2) / Math.pow(constr.getStdDevOfMeanRate(), 2);
					double misfit = Math.pow((constr.getMeanRate() - rate) / constr.getStdDevOfMeanRate(), 2);
					String info = func.getInfo();
					if (info == null || info.isEmpty())
						info = "";
					else
						info += "\n";
					info += "\tSect "+sectID+". Mean: "+constr.getMeanRate()+"\tStd Dev: "
						+constr.getStdDevOfMeanRate()+"\tSolution: "+rate+"\tMisfit: "+misfit;
					runningMisfitTotal += misfit;
					func.setInfo(info);
					
					paleoRateMean.set(paleoRateX, constr.getMeanRate());
					paleoRateUpper.set(paleoRateX, constr.getUpper95ConfOfRate());
					paleoRateLower.set(paleoRateX, constr.getLower95ConfOfRate());
				}
			}
			
			DiscretizedFunc func = funcs.get(funcs.size()-1);
			
			String info = func.getInfo();
			info += "\n\n\tTOTAL MISFIT: "+runningMisfitTotal;
			
			func.setInfo(info);
			
			return new PlotSpec(funcs, plotChars, "Paleosiesmic Constraint Fit", "", "Event Rate Per Year");
		}

	public static void showSegRateComparison(List<PaleoRateConstraint> paleoRateConstraint,
			List<AveSlipConstraint> aveSlipConstraints,
			InversionFaultSystemSolution sol) {
		PlotSpec spec = getSegRateComparisonSpec(paleoRateConstraint, aveSlipConstraints, sol);
		
		GraphWindow w = new GraphWindow(spec.getPlotElems(), spec.getTitle(), spec.getChars(), true);
		w.setX_AxisLabel(spec.getXAxisLabel());
		w.setY_AxisLabel(spec.getYAxisLabel());
	}

	public static HeadlessGraphPanel getHeadlessSegRateComparison(List<PaleoRateConstraint> paleoRateConstraint,
			List<AveSlipConstraint> aveSlipConstraints,
			InversionFaultSystemSolution sol, boolean yLog) {
		PlotSpec spec = getSegRateComparisonSpec(paleoRateConstraint, aveSlipConstraints, sol);
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		CommandLineInversionRunner.setFontSizes(gp);
		gp.setYLog(yLog);
		
		gp.drawGraphPanel(spec);
		
		return gp;
	}
	
	public static double getPaleoRateForSect(FaultSystemSolution sol, int sectIndex,
			PaleoProbabilityModel paleoProbModel) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		double rate = 0;
		for (int rupID : rupSet.getRupturesForSection(sectIndex)) {
			double rupRate = sol.getRateForRup(rupID);
			if (paleoProbModel != null)
				rupRate *= paleoProbModel.getProbPaleoVisible(rupSet, rupID, sectIndex);
			rate += rupRate;
		}
		return rate;
	}
	
	static double getAveSlipProbRateForSect(SlipEnabledSolution sol, int sectIndex) {
		SlipEnabledRupSet rupSet = sol.getRupSet();
		double rate = 0;
		for (int rupID : rupSet.getRupturesForSection(sectIndex)) {
			int sectIndexInRup = rupSet.getSectionsIndicesForRup(rupID).indexOf(sectIndex);
			double slipOnSect = rupSet.getSlipOnSectionsForRup(rupID)[sectIndexInRup]; 
			
			double rupRate = sol.getRateForRup(rupID) * AveSlipConstraint.getProbabilityOfObservedSlip(slipOnSect);
			rate += rupRate;
		}
		return rate;
	}
	
	/**
	 * This stores data for each paleo fault for a single solution. It is used to calculate
	 * data for each logic tree branch in parallel before making a combined plot
	 * @author kevin
	 *
	 */
	public static class DataForPaleoFaultPlots implements Serializable {
		
		private Map<Integer, double[]> origSlipsMap;
		private Map<Integer, double[]> targetSlipsMap;
		private Map<Integer, double[]> solSlipsMap;
		private Map<Integer, double[]> paleoRatesMap;
		private Map<Integer, double[]> origRatesMap;
		private Map<Integer, double[]> aveSlipRatesMap;
		private Map<Integer, double[]> aveSlipsMap;
		private Map<Integer, double[]> avePaleoSlipsMap;
//		private LogicTreeBranch branch;
		
		private List<Map<Integer, double[]>> allArraysList;
		
		private double weight;
		
		private DataForPaleoFaultPlots(double weight) {
			origSlipsMap = Maps.newHashMap();
			targetSlipsMap = Maps.newHashMap();
			solSlipsMap = Maps.newHashMap();
			paleoRatesMap = Maps.newHashMap();
			origRatesMap = Maps.newHashMap();
			aveSlipRatesMap = Maps.newHashMap();
			aveSlipsMap = Maps.newHashMap();
			avePaleoSlipsMap = Maps.newHashMap();
			
			allArraysList = Lists.newArrayList();
			allArraysList.add(origSlipsMap);
			allArraysList.add(targetSlipsMap);
			allArraysList.add(solSlipsMap);
			allArraysList.add(paleoRatesMap);
			allArraysList.add(origRatesMap);
			allArraysList.add(aveSlipRatesMap);
			allArraysList.add(aveSlipsMap);
			allArraysList.add(avePaleoSlipsMap);
			
			this.weight = weight;
//			this.branch = branch;
		}
		
		public static DataForPaleoFaultPlots build(
				SlipEnabledSolution sol,
				Map<String, List<Integer>> namedFaultsMap,
				Map<String, List<PaleoRateConstraint>> namedFaultConstraintsMap,
				Map<Integer, List<FaultSectionPrefData>> allParentsMap,
				PaleoProbabilityModel paleoProbModel,
				double weight) {
//			double[] aveSlips = new double[sol.getNumSections()];
//			double[] avePaleoSlips = new double[sol.getNumSections()];
//			for (int i=0; i<aveSlips.length; i++) {
//				aveSlips[i] = sol.calcSlipPFD_ForSect(i).getMean();
//				avePaleoSlips[i] = sol.calcPaleoObsSlipPFD_ForSect(i).getMean();
//			}
			
			return build(sol, namedFaultsMap, namedFaultConstraintsMap, allParentsMap,
					paleoProbModel, weight, null, null);
		}
		
		public static DataForPaleoFaultPlots build(
				SlipEnabledSolution sol,
				Map<String, List<Integer>> namedFaultsMap,
				Map<String, List<PaleoRateConstraint>> namedFaultConstraintsMap,
				Map<Integer, List<FaultSectionPrefData>> allParentsMap,
				PaleoProbabilityModel paleoProbModel,
				double weight,
				double[] aveSlipsData,
				double[] aveSlipsPaleoObsData) {
			
			DataForPaleoFaultPlots data = new DataForPaleoFaultPlots(weight);
			
			Stopwatch watch = Stopwatch.createUnstarted();
			Stopwatch paleoWatch = Stopwatch.createUnstarted();
			Stopwatch slipsWatch = Stopwatch.createUnstarted();
			Stopwatch aveSlipsWatch = Stopwatch.createUnstarted();
			
			watch.start();
			
			for (String name : namedFaultConstraintsMap.keySet()) {
				List<Integer> parentIDs = namedFaultsMap.get(name);
				
				for (Integer parentID : parentIDs) {
					List<FaultSectionPrefData> sects = allParentsMap.get(parentID);
					int numSects = sects.size();
					
					double[] origSlips = new double[numSects];
					double[] targetSlips = new double[numSects];
					double[] solSlips = new double[numSects];
					double[] paleoRates = new double[numSects];
					double[] origRates = new double[numSects];
					double[] aveSlipRates = new double[numSects];
					double[] aveSlips = new double[numSects];
					double[] avePaleoSlips = new double[numSects];
					
					for (int s=0; s<numSects; s++) {
						FaultSectionPrefData sect = sects.get(s);
						int mySectID = sect.getSectionId();
						paleoWatch.start();
						paleoRates[s] = getPaleoRateForSect(sol, mySectID, paleoProbModel);
						origRates[s] = getPaleoRateForSect(sol, mySectID, null);
						aveSlipRates[s] = getAveSlipProbRateForSect(sol, mySectID);
						paleoWatch.stop();
						// convert slips to mm/yr
						slipsWatch.start();
						origSlips[s] = sect.getOrigAveSlipRate();
						targetSlips[s] = sol.getRupSet().getSlipRateForSection(sect.getSectionId())*1e3;
						solSlips[s] = sol.calcSlipRateForSect(sect.getSectionId())*1e3;
						slipsWatch.stop();
						
						aveSlipsWatch.start();
						if (aveSlipsData == null) {
							aveSlips[s] = sol.calcSlipPFD_ForSect(sect.getSectionId()).getMean();
							avePaleoSlips[s] = sol.calcPaleoObsSlipPFD_ForSect(sect.getSectionId()).getMean();
						} else {
							aveSlips[s] = aveSlipsData[sect.getSectionId()];
							avePaleoSlips[s] = aveSlipsPaleoObsData[sect.getSectionId()];
						}
						aveSlipsWatch.stop();
					}
					
//					if (parentID == 301) {
//						if (StatUtils.min(solSlips) < 1) {
//							System.out.println("Solution slip less than 1 on Mojave S...WTF?");
//							System.out.println("origSlips: ["+Joiner.on(",").join(Doubles.asList(origSlips))+"]");
//							System.out.println("targetSlips: ["+Joiner.on(",").join(Doubles.asList(targetSlips))+"]");
//							System.out.println("solSlips: ["+Joiner.on(",").join(Doubles.asList(solSlips))+"]");
//							System.out.println("paleoRates: ["+Joiner.on(",").join(Doubles.asList(paleoRates))+"]");
//							System.out.println("origRates: ["+Joiner.on(",").join(Doubles.asList(origRates))+"]");
//							System.out.println("aveSlipRates: ["+Joiner.on(",").join(Doubles.asList(aveSlipRates))+"]");
//							System.exit(1);
//						}
//					}
					
					data.origSlipsMap.put(parentID, origSlips);
					data.targetSlipsMap.put(parentID, targetSlips);
					data.solSlipsMap.put(parentID, solSlips);
					data.paleoRatesMap.put(parentID, paleoRates);
					data.origRatesMap.put(parentID, origRates);
					data.aveSlipRatesMap.put(parentID, aveSlipRates);
					data.aveSlipsMap.put(parentID, aveSlips);
					data.avePaleoSlipsMap.put(parentID, avePaleoSlips);
				}
			}
			watch.stop();
			
			System.out.println("Calc Times:\ttotal="+watch.elapsed(TimeUnit.SECONDS)+"\tpaleo="+paleoWatch.elapsed(TimeUnit.SECONDS)
					+"\tslip="+slipsWatch.elapsed(TimeUnit.SECONDS)+"\taveSlip="+aveSlipsWatch.elapsed(TimeUnit.SECONDS));
			return data;
		}
	}
	
	public static Map<String, PlotSpec[]> getFaultSpecificPaleoPlotSpec(
			List<PaleoRateConstraint> paleoRateConstraint,
			List<AveSlipConstraint> aveSlipConstraints,
			Map<String, List<Integer>> namedFaultsMap,
			SlipEnabledSolution sol) {
		SlipEnabledRupSet rupSet = sol.getRupSet();
		
		// create new list since we might modify it
		paleoRateConstraint = Lists.newArrayList(paleoRateConstraint);
		
		if (aveSlipConstraints != null) {
			for (AveSlipConstraint aveSlip : aveSlipConstraints) {
				paleoRateConstraint.add(new PaleoFitPlotter.AveSlipFakePaleoConstraint(aveSlip, aveSlip.getSubSectionIndex(),
						rupSet.getSlipRateForSection(aveSlip.getSubSectionIndex())));
			}
		}
		
		PaleoProbabilityModel paleoProbModel = null;
		try {
			paleoProbModel = UCERF3_PaleoProbabilityModel.load();
		} catch (IOException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		Map<String, List<PaleoRateConstraint>> namedFaultConstraintsMap =
			 getNamedFaultConstraintsMap(paleoRateConstraint, rupSet.getFaultSectionDataList(), namedFaultsMap);
		
		Map<Integer, List<FaultSectionPrefData>> allParentsMap =
			getAllParentsMap(rupSet.getFaultSectionDataList());
		
		List<DataForPaleoFaultPlots> datas = Lists.newArrayList();
		// just takes forever and no one looks at them. disable
//		if (sol instanceof AverageFaultSystemSolution && 
//				((AverageFaultSystemSolution)sol).getNumSolutions() <= 10) {
//			int cnt = 0;
//			for (InversionFaultSystemSolution s : (AverageFaultSystemSolution)sol) {
//				System.out.println("Building paleo data for solution: "+(++cnt));
//				datas.add(DataForPaleoFaultPlots.build(s, namedFaultsMap, namedFaultConstraintsMap,
//						allParentsMap, paleoProbModel, 1d));
//			}
//		} else {
			datas.add(DataForPaleoFaultPlots.build(sol, namedFaultsMap, namedFaultConstraintsMap,
					allParentsMap, paleoProbModel, 1d));
//		}
		return getFaultSpecificPaleoPlotSpecs(namedFaultsMap, namedFaultConstraintsMap, datas, allParentsMap);
	}
	
	public static Map<String, List<PaleoRateConstraint>> getNamedFaultConstraintsMap(
			List<PaleoRateConstraint> paleoRateConstraint,
			List<FaultSectionPrefData> fsd,
			Map<String, List<Integer>> namedFaultsMap) {
		Map<String, List<PaleoRateConstraint>> namedFaultConstraintsMap = Maps.newHashMap();
		
		for (PaleoRateConstraint constr : paleoRateConstraint) {
			FaultSectionPrefData sect = fsd.get(constr.getSectionIndex());
			Integer parentID = sect.getParentSectionId();
			String name = null;
			for (String faultName : namedFaultsMap.keySet()) {
				List<Integer> parentIDs = namedFaultsMap.get(faultName);
				if (parentIDs.contains(parentID)) {
					name = faultName;
					break;
				}
			}
			if (name == null) {
				System.err.println("WARNING: no named fault map for paleo constraint on parent section "
						+sect.getParentSectionName()+" (pale name="+constr.getPaleoSiteName()+")");
				continue;
			}
			
			List<PaleoRateConstraint> constraintsForFault = namedFaultConstraintsMap.get(name);
			if (constraintsForFault == null) {
				constraintsForFault = Lists.newArrayList();
				namedFaultConstraintsMap.put(name, constraintsForFault);
			}
			constraintsForFault.add(constr);
		}
		
		return namedFaultConstraintsMap;
	}
	
	public static Map<Integer, List<FaultSectionPrefData>> getAllParentsMap(
			List<FaultSectionPrefData> fsd) {
		Map<Integer, List<FaultSectionPrefData>> allParentsMap = Maps.newHashMap();
		for (FaultSectionPrefData sect : fsd) {
			List<FaultSectionPrefData> parentSects = allParentsMap.get(sect.getParentSectionId());
			if (parentSects == null) {
				parentSects = Lists.newArrayList();
				allParentsMap.put(sect.getParentSectionId(), parentSects);
			}
			parentSects.add(sect);
		}
		return allParentsMap;
	}
	
	private static List<PlotCurveCharacterstics> getCharsForFuncs(
			List<DiscretizedFunc> funcs, Color color, float mainThickness) {
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		if (funcs.size() == 1) {
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, mainThickness, color));
		} else {
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, color));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, color));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, mainThickness, color));
		}
		return chars;
	}
	
	private static boolean smooth_x_vals = false;
	
	private static List<DiscretizedFunc> getFuncsForScalar(
			List<DataForPaleoFaultPlots> datas,
			int arrayIndex, int parentID,
			double[][] xvals, String name) {
		List<DiscretizedFunc> funcs = Lists.newArrayList();
		
		if (datas.size() == 1) {
			ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
			func.setName(name);
			double[] array = datas.get(0).allArraysList.get(arrayIndex).get(parentID);
			for (int i=0; i<array.length; i++)
				for (double x : xvals[i])
					func.set(x, array[i]);
			funcs.add(func);
		} else {
			double[][] arrayVals = new double[xvals.length][datas.size()];
			
			double totWeight = 0;
			double[] weights = new double[datas.size()];
			
			for (int d=0; d<datas.size(); d++) {
				DataForPaleoFaultPlots data = datas.get(d);
				double[] array = data.allArraysList.get(arrayIndex).get(parentID);
				for (int s=0; s<xvals.length; s++)
					arrayVals[s][d] = array[s];
				weights[d] = data.weight;
				totWeight += data.weight;
			}
			
			for (int i=0; i<weights.length; i++)
				weights[i] = weights[i] / totWeight;
			Preconditions.checkState(totWeight > 0);
			
			ArbitrarilyDiscretizedFunc minFunc = new ArbitrarilyDiscretizedFunc();
			minFunc.setName(name+" (minimum)");
			ArbitrarilyDiscretizedFunc maxFunc = new ArbitrarilyDiscretizedFunc();
			maxFunc.setName(name+" (maximum)");
			ArbitrarilyDiscretizedFunc meanFunc = new ArbitrarilyDiscretizedFunc();
			meanFunc.setName(name+" (weighted mean)");
			
			// for each subsection
			for (int s=0; s<xvals.length; s++) {
				double[] myXvals = xvals[s];
				double[] array = arrayVals[s];
				
				double mean = FaultSystemSolutionFetcher.calcScaledAverage(array, weights);
				if (Double.isInfinite(mean))
					System.out.println("INFINITE! array=["+Joiner.on(",").join(Doubles.asList(array))
							+"], weights=["+Joiner.on(",").join(Doubles.asList(weights)));
				double min = StatUtils.min(array);
				double max = StatUtils.max(array);
				
				if (!smooth_x_vals) {
					// used for avoiding collisions
					boolean xIncreasing = myXvals[myXvals.length-1] > myXvals[0];
					for (int i=0; i<myXvals.length; i++) {
						double x = myXvals[i];
						if (minFunc.getXIndex(x) >= 0) {
							// collision
							boolean end = i == myXvals.length-1;
							
							if (xIncreasing) {
								if (!end)
									x += 0.0001;
								else
									x -= 0.0001;
							} else {
								if (!end)
									x -= 0.0001;
								else
									x += 0.0001;
							}
						}
						minFunc.set(x, min);
						maxFunc.set(x, max);
						meanFunc.set(x, mean);
					}
				} else {
					double x = 0.5*(myXvals[0] + myXvals[myXvals.length-1]);
					if (s == 0) {
						minFunc.set(myXvals[0], min);
						maxFunc.set(myXvals[0], max);
						meanFunc.set(myXvals[0], mean);
					}
					minFunc.set(x, min);
					maxFunc.set(x, max);
					meanFunc.set(x, mean);
					if (s == xvals.length-1) {
						minFunc.set(myXvals[myXvals.length-1], min);
						maxFunc.set(myXvals[myXvals.length-1], max);
						meanFunc.set(myXvals[myXvals.length-1], mean);
					}
				}
			}
			
//			System.out.println("Max mean for "+arrayIndex+": "+meanFunc.getMaxY());
			
			funcs.add(minFunc);
			funcs.add(maxFunc);
			funcs.add(meanFunc);
		}
		
		return funcs;
	}
	
	public static Map<String, PlotSpec[]> getFaultSpecificPaleoPlotSpecs(
			Map<String, List<Integer>> namedFaultsMap,
			Map<String, List<PaleoRateConstraint>> namedFaultConstraintsMap,
			List<DataForPaleoFaultPlots> datas,
			Map<Integer, List<FaultSectionPrefData>> allParentsMap) {
		
		Color origColor = Color.BLACK;
		Color aveSlipColor = new Color(10, 100, 55);
		Color paleoProbColor = Color.RED;
		
		Map<String, PlotSpec[]> specs = Maps.newHashMap();
		
		for (String name : namedFaultConstraintsMap.keySet()) {
			List<PaleoRateConstraint> constraints = namedFaultConstraintsMap.get(name);
			List<Integer> namedFaults = namedFaultsMap.get(name);
			
			ArrayList<DiscretizedFunc> rateFuncs = Lists.newArrayList();
			ArrayList<PlotCurveCharacterstics> rateChars = Lists.newArrayList();
			ArrayList<DiscretizedFunc> slipFuncs = Lists.newArrayList();
			ArrayList<PlotCurveCharacterstics> slipChars = Lists.newArrayList();
			ArrayList<PlotElement> aveSlipFuncs = Lists.newArrayList();
			ArrayList<PlotCurveCharacterstics> aveSlipChars = Lists.newArrayList();
			
			List<Location> allSepLocs = Lists.newArrayList();
			
			ArbitrarilyDiscretizedFunc paleoRateMean = new ArbitrarilyDiscretizedFunc();
			paleoRateMean.setName("Paleo Rate Constraint: Mean");
			ArbitrarilyDiscretizedFunc paleoRateUpper = new ArbitrarilyDiscretizedFunc();
			paleoRateUpper.setName("Paleo Rate Constraint: Upper 95% Confidence");
			ArbitrarilyDiscretizedFunc paleoRateLower = new ArbitrarilyDiscretizedFunc();
			paleoRateLower.setName("Paleo Rate Constraint: Lower 95% Confidence");
			List<ArbitrarilyDiscretizedFunc> paleoErrorBarFuncs = Lists.newArrayList();
			
			ArbitrarilyDiscretizedFunc aveSlipRateMean = new ArbitrarilyDiscretizedFunc();
			aveSlipRateMean.setName("Ave Slip Rate Constraint: Mean");
//			ArbitrarilyDiscretizedFunc aveSlipRateUpper = new ArbitrarilyDiscretizedFunc();
//			aveSlipRateUpper.setName("Ave Slip Rate Constraint: Upper 95% Confidence");
//			ArbitrarilyDiscretizedFunc aveSlipRateLower = new ArbitrarilyDiscretizedFunc();
//			aveSlipRateLower.setName("Ave Slip Rate Constraint: Lower 95% Confidence");
			
			ArbitrarilyDiscretizedFunc aveSlipDataMean = new ArbitrarilyDiscretizedFunc();
			aveSlipDataMean.setName("Ave Slip Per Event Data: Mean");
			ArbitrarilyDiscretizedFunc aveSlipDataUpper = new ArbitrarilyDiscretizedFunc();
			aveSlipDataUpper.setName("Ave Slip Per Event Data: Upper 95% Confidence");
			ArbitrarilyDiscretizedFunc aveSlipDataLower = new ArbitrarilyDiscretizedFunc();
			aveSlipDataLower.setName("Ave Slip Per Event Data: Lower 95% Confidence");
			
			// first create data lines
			
			// these are used to determine if we should make the x axis latitude or longitude
			double minLat = Double.POSITIVE_INFINITY;
			double maxLat = Double.NEGATIVE_INFINITY;
			double minLon = Double.POSITIVE_INFINITY;
			double maxLon = Double.NEGATIVE_INFINITY;
			
			double maxSlip = 0d;
			
			Map<Integer, List<FaultSectionPrefData>> sectionsForFault = Maps.newHashMap();
			
			for (Integer parentID : namedFaults) {
				List<FaultSectionPrefData> sectionsForParent = allParentsMap.get(parentID);
				if (sectionsForParent == null)
					continue;
				
				for (FaultSectionPrefData sect : sectionsForParent) {
					for (Location loc : sect.getFaultTrace()) {
						double lat = loc.getLatitude();
						double lon = loc.getLongitude();
						if (lat < minLat)
							minLat = lat;
						if (lat > maxLat)
							maxLat = lat;
						if (lon < minLon)
							minLon = lon;
						if (lon > maxLon)
							maxLon = lon;
					}
				}
				
				for (DataForPaleoFaultPlots data : datas) {
					double origSlip = StatUtils.max(data.origSlipsMap.get(parentID));
					double solSlip = StatUtils.max(data.solSlipsMap.get(parentID));
					if (origSlip > maxSlip)
						maxSlip = origSlip;
					if (solSlip > maxSlip)
						maxSlip = solSlip;
				}
				
				sectionsForFault.put(parentID, sectionsForParent);
			}
			
			double deltaLat = maxLat - minLat;
			double deltaLon = maxLon - minLon;
			boolean latitudeX = deltaLat > 0.5*deltaLon; // heavily favor latitude x
			
			PlotCurveCharacterstics sepChar =
				new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.GRAY);
			
			// find common prefix if any
			List<String> parentNames = Lists.newArrayList();
			for (Integer parentID : namedFaults) {
				List<FaultSectionPrefData> sectionsForParent = sectionsForFault.get(parentID);
				if (sectionsForParent == null)
					continue;
				String parentName = sectionsForParent.get(0).getParentSectionName();
				parentNames.add(parentName);
			}
			String[] parentNamesArray = parentNames.toArray(new String[0]);
			String commonPrefix = StringUtils.getCommonPrefix(parentNamesArray);
			
			List<XYTextAnnotation> annotations = Lists.newArrayList();
			Font font = new Font(Font.SERIF, 0, 14);
			double angle = -0.5*Math.PI;
			TextAnchor rotAnchor = TextAnchor.CENTER_RIGHT;
			TextAnchor textAnchor = TextAnchor.CENTER_RIGHT;
			
			int actualCount = 0;
			for (int i=0; i<namedFaults.size(); i++) {
				Integer parentID = namedFaults.get(i);
				List<FaultSectionPrefData> sectionsForParent = sectionsForFault.get(parentID);
				if (sectionsForParent == null)
					continue;
				String parentName = sectionsForParent.get(0).getParentSectionName();
				actualCount++;
				
				// add separators
				FaultTrace firstTrace = sectionsForParent.get(0).getFaultTrace();
				FaultTrace lastTrace = sectionsForParent.get(sectionsForParent.size()-1).getFaultTrace();
				Location startLoc = firstTrace.first();
				Location endLoc = lastTrace.last();
				allSepLocs.add(startLoc);
				allSepLocs.add(endLoc);
				
				// add text annotation
				String annotationName = parentName;
				if (!commonPrefix.isEmpty())
					annotationName = annotationName.substring(commonPrefix.length());
				annotationName = annotationName.replaceAll("San Andreas", "");
				annotationName = annotationName.replaceAll("Elsinore", "");
				annotationName = annotationName.replaceAll("\\(", "").replaceAll("\\)", "");
				annotationName = annotationName.replaceAll("2011 CFM", "");
				annotationName = annotationName.replaceAll(" rev", "");
				annotationName = annotationName.trim();
				double midPt;
				if (latitudeX)
					midPt = 0.5*(startLoc.getLatitude() + endLoc.getLatitude());
				else
					midPt = 0.5*(startLoc.getLongitude() + endLoc.getLongitude());
				// note - Y location will get reset in the plotting code
				if (!annotationName.isEmpty()) {
					XYTextAnnotation a = new XYTextAnnotation(annotationName+" ", midPt, 0d);
					a.setFont(font);
					a.setRotationAnchor(rotAnchor);
					a.setTextAnchor(textAnchor);
					a.setRotationAngle(angle);
					annotations.add(a);
				}
				
				double[][] xvals = new double[sectionsForParent.size()][];
				for (int s=0; s<xvals.length; s++) {
					FaultTrace trace = sectionsForParent.get(s).getFaultTrace();
					xvals[s] = new double[trace.size()];
					for (int t=0; t<trace.size(); t++) {
						Location loc = trace.get(t);
						if (latitudeX)
							xvals[s][t] = loc.getLatitude();
						else
							xvals[s][t] = loc.getLongitude();
					}
				}
				
				List<DiscretizedFunc> origSlipFuncs = getFuncsForScalar(datas, 0, parentID, xvals,
						"Original nonreduced slip rates for: "+parentName);
				List<DiscretizedFunc> targetSlipFuncs = getFuncsForScalar(datas, 1, parentID, xvals,
						"Target slip rates for: "+parentName);
				List<DiscretizedFunc> solSlipFuncs = getFuncsForScalar(datas, 2, parentID, xvals,
						"Solution slip rates for: "+parentName);
				List<DiscretizedFunc> paleoRtFuncs = getFuncsForScalar(datas, 3, parentID, xvals,
						"Solution paleo rates for: "+parentName);
				List<DiscretizedFunc> origRtFuncs = getFuncsForScalar(datas, 4, parentID, xvals,
						"Solution original rates for: "+parentName);
				List<DiscretizedFunc> aveSlipRtFuncs = getFuncsForScalar(datas, 5, parentID, xvals,
						"Solution ave slip prob visible rates for: "+parentName);
				List<DiscretizedFunc> myAveSlipsFuncs = getFuncsForScalar(datas, 6, parentID, xvals,
						"Solution average slip per event for: "+parentName);
				List<DiscretizedFunc> myAvePaleoSlipsFuncs = getFuncsForScalar(datas, 7, parentID, xvals,
						"Solution average paleo observable slip per event for: "+parentName);
				
				// skip if no rate on any of the sections
				boolean skip = origRtFuncs.get(origRtFuncs.size()-1).getMaxY() <= 0;
				if (!skip) {
					rateFuncs.addAll(paleoRtFuncs);
					rateChars.addAll(getCharsForFuncs(paleoRtFuncs, paleoProbColor, 2f));
					rateFuncs.addAll(origRtFuncs);
					rateChars.addAll(getCharsForFuncs(origRtFuncs, origColor, 1f));
					rateFuncs.addAll(aveSlipRtFuncs);
					rateChars.addAll(getCharsForFuncs(aveSlipRtFuncs, aveSlipColor, 2f));
					if (datas.size() == 1) {
						slipFuncs.addAll(origSlipFuncs);
						slipChars.addAll(getCharsForFuncs(origSlipFuncs, Color.CYAN, 1f));
					}
					slipFuncs.addAll(targetSlipFuncs);
					slipChars.addAll(getCharsForFuncs(targetSlipFuncs, Color.BLUE, 2f));
					slipFuncs.addAll(solSlipFuncs);
					slipChars.addAll(getCharsForFuncs(solSlipFuncs, Color.MAGENTA, 2f));
					aveSlipFuncs.addAll(myAveSlipsFuncs);
					aveSlipChars.addAll(getCharsForFuncs(myAveSlipsFuncs, Color.BLUE, 2f));
					aveSlipFuncs.addAll(myAvePaleoSlipsFuncs);
					aveSlipChars.addAll(getCharsForFuncs(myAvePaleoSlipsFuncs, Color.MAGENTA, 2f));
				}
			}
			if (actualCount == 0)
				// no matching faults in this FM
				continue;
			
			// now add paleo sites
			for (PaleoRateConstraint constr : constraints) {
				Preconditions.checkNotNull(constr, "Paleo Constraint NULL!");
				Preconditions.checkNotNull(constr.getPaleoSiteLoction(),
						"Paleo Constraint Location NULL!");
				
				// we want to map the constraint to the closest part on the fault trace as we're plotting traces
				// first find the FaultSectionPrefData for the subSect
				FaultSectionPrefData mappedSect = null;
				for (Integer parentID : namedFaults) {
					for (FaultSectionPrefData sect : allParentsMap.get(parentID)) {
						if (sect.getSectionId() == constr.getSectionIndex()) {
							mappedSect = sect;
							break;
						}
					}
				}
				Preconditions.checkNotNull(mappedSect, "Couldn't find mapped sub section: "+constr.getSectionIndex());
				FaultTrace mappedTrace = mappedSect.getFaultTrace();
				// discretize the trace for more accurate mapping
				mappedTrace = FaultUtils.resampleTrace(mappedTrace, 20);
				// now find closest
				Location origPaleoLocation = constr.getPaleoSiteLoction();
				Location paleoLocation = null;
				double paleoLocDist = Double.POSITIVE_INFINITY;
				for (Location traceLoc : mappedTrace) {
					double dist = LocationUtils.horzDistanceFast(origPaleoLocation, traceLoc);
					if (dist < paleoLocDist) {
						paleoLocDist = dist;
						paleoLocation = traceLoc;
					}
				}
				
				double paleoRateX;
				if (latitudeX)
					paleoRateX = paleoLocation.getLatitude();
				else
					paleoRateX = paleoLocation.getLongitude();
				
				if (constr instanceof PaleoFitPlotter.AveSlipFakePaleoConstraint) {
					aveSlipRateMean.set(paleoRateX, constr.getMeanRate());
//					aveSlipRateUpper.set(paleoRateX, constr.getUpper95ConfOfRate());
//					aveSlipRateLower.set(paleoRateX, constr.getLower95ConfOfRate());
					aveSlipDataMean.set(paleoRateX, ((AveSlipFakePaleoConstraint)constr).origAveSlip);
					aveSlipDataUpper.set(paleoRateX, ((AveSlipFakePaleoConstraint)constr).origAveSlipUpper);
					aveSlipDataLower.set(paleoRateX, ((AveSlipFakePaleoConstraint)constr).origAveSlipLower);
				} else {
					paleoRateMean.set(paleoRateX, constr.getMeanRate());
					paleoRateUpper.set(paleoRateX, constr.getUpper95ConfOfRate());
					paleoRateLower.set(paleoRateX, constr.getLower95ConfOfRate());
					ArbitrarilyDiscretizedFunc errorBarFunc = new ArbitrarilyDiscretizedFunc();
					errorBarFunc.setName("Paleo Rate Constraint: Error Bar");
					errorBarFunc.set(paleoRateX-0.00005, constr.getUpper95ConfOfRate());
					errorBarFunc.set(paleoRateX+0.00005, constr.getLower95ConfOfRate());
					paleoErrorBarFuncs.add(errorBarFunc);
				}
			}
			
			if (aveSlipRateMean.size() > 0) {
				rateFuncs.add(aveSlipRateMean);
				rateChars.add(new PlotCurveCharacterstics(DATA_SYMBOL, 5f, aveSlipColor));
//				rateFuncs.add(aveSlipRateUpper);
//				rateChars.add(new PlotCurveCharacterstics(CONFIDENCE_BOUND_SYMBOL, 5f, aveSlipColor));
//				rateFuncs.add(aveSlipRateLower);
//				rateChars.add(new PlotCurveCharacterstics(CONFIDENCE_BOUND_SYMBOL, 5f, aveSlipColor));
			}
			
			if (paleoRateMean.size() > 0) {
				rateFuncs.add(paleoRateMean);
				rateChars.add(new PlotCurveCharacterstics(DATA_SYMBOL, 5f, paleoProbColor));
				rateFuncs.add(paleoRateUpper);
				rateChars.add(new PlotCurveCharacterstics(CONFIDENCE_BOUND_SYMBOL, 5f, paleoProbColor));
				rateFuncs.add(paleoRateLower);
				rateChars.add(new PlotCurveCharacterstics(CONFIDENCE_BOUND_SYMBOL, 5f, paleoProbColor));
				for (ArbitrarilyDiscretizedFunc errorBarFunc : paleoErrorBarFuncs) {
					rateFuncs.add(errorBarFunc);
					rateChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,
							CONFIDENCE_BOUND_WIDTH, paleoProbColor));
				}
			}
			
			if (aveSlipDataMean.size() > 0) {
				aveSlipFuncs.add(aveSlipDataMean);
				aveSlipChars.add(new PlotCurveCharacterstics(DATA_SYMBOL, 5f, aveSlipColor));
				aveSlipFuncs.add(aveSlipDataUpper);
				aveSlipChars.add(new PlotCurveCharacterstics(CONFIDENCE_BOUND_SYMBOL, 5f, aveSlipColor));
				aveSlipFuncs.add(aveSlipDataLower);
				aveSlipChars.add(new PlotCurveCharacterstics(CONFIDENCE_BOUND_SYMBOL, 5f, aveSlipColor));
			}
			
			// no longer needed
//			String[] parentNameArray = new String[parentNames.size()];
//			for (int i=0; i<parentNames.size(); i++)
//				parentNameArray[i] = parentNames.get(i);
//			String faultName = StringUtils.getCommonPrefix(parentNameArray);
//			if (parentNameArray.length > 2 && (
//					parentNameArray[0].startsWith("San Andreas")
//					|| parentNameArray[1].startsWith("San Andreas")))
//				faultName = "San Andreas";
//			faultName = faultName.replaceAll("\\(", "").replaceAll("\\)", "").trim();
//			if (faultName.length() < 2) {
//				System.out.println("WARNING: couldn't come up with a common name for: "
//						+Joiner.on(", ").join(parentNames));
//				faultName = "Fault which includes "+parentNameArray[0];
//			}
			System.out.println(name+"\tDeltaLat: "+deltaLat+"\tDeltaLon: "+deltaLon
					+"\tLatitudeX ? "+latitudeX);
			
			String paleoTitle = "Paleo Rates/Constraints for "+name;
			String slipTitle = "Slip Rates for "+name;
			String aveSlipTitle = "Average Slip In Events for "+name;
			String xAxisLabel;
			if (latitudeX)
				xAxisLabel = "Latitude (degrees)";
			else
				xAxisLabel = "Longitude (degrees)";
			String paleoYAxisLabel = "Event Rate Per Year";
			String slipYAxisLabel = "Slip Rate (mm/yr)";
			String aveSlipYAxisLabel = "Ave Slip In Events (m)";
			
			ArrayList<PlotElement> paleoOnlyFuncs = Lists.newArrayList();
			ArrayList<PlotCurveCharacterstics> paleoOnlyChars = Lists.newArrayList();
			paleoOnlyFuncs.addAll(rateFuncs);
			paleoOnlyChars.addAll(rateChars);
			
			ArrayList<PlotElement> slipOnlyFuncs = Lists.newArrayList();
			ArrayList<PlotCurveCharacterstics> slipOnlyChars = Lists.newArrayList();
			slipOnlyFuncs.addAll(slipFuncs);
			slipOnlyChars.addAll(slipChars);
			
			PlotMultiDataLayer paleoSeps = new PlotMultiDataLayer();
			paleoSeps.setInfo("(separators)");
			PlotMultiDataLayer slipSeps = new PlotMultiDataLayer();
			slipSeps.setInfo("(separators)");
			PlotMultiDataLayer aveSeps = new PlotMultiDataLayer();
			aveSeps.setInfo("(separators)");
			
			for (Location sepLoc : allSepLocs) {
				double x;
				if (latitudeX)
					x = sepLoc.getLatitude();
				else
					x = sepLoc.getLongitude();
				paleoSeps.addVerticalLine(x, 1e-10, 1e1);
				slipSeps.addVerticalLine(x, 1e-10, 5e3);
				aveSeps.addVerticalLine(x, 0, 50);
			}
			
			paleoOnlyFuncs.add(paleoSeps);
			slipOnlyChars.add(sepChar);
			slipOnlyFuncs.add(slipSeps);
			paleoOnlyChars.add(sepChar);
			aveSlipFuncs.add(aveSeps);
			aveSlipChars.add(sepChar);
			
			PlotSpec paleoOnlySpec = new PlotSpec(
					paleoOnlyFuncs, paleoOnlyChars, paleoTitle, xAxisLabel, paleoYAxisLabel);
			paleoOnlySpec.setPlotAnnotations(annotations);
			PlotSpec slipOnlySpec = new PlotSpec(
					slipOnlyFuncs, slipOnlyChars, slipTitle, xAxisLabel, slipYAxisLabel);
			slipOnlySpec.setPlotAnnotations(annotations);
			PlotSpec aveSlipSpec = new PlotSpec(
					aveSlipFuncs, aveSlipChars, aveSlipTitle, xAxisLabel, aveSlipYAxisLabel);
			aveSlipSpec.setPlotAnnotations(annotations);
			PlotSpec[] specArray = { paleoOnlySpec, slipOnlySpec, aveSlipSpec };
			specs.put(name, specArray);
		}
		
		return specs;
	}
	
	public static void writeTables(File dir,
			InversionFaultSystemSolution sol,
			List<AveSlipConstraint> aveSlipConstraints,
			List<PaleoRateConstraint> paleoRateConstraints,
			FaultSystemSolution ucerf2Sol,
			List<AveSlipConstraint> ucerf2AveSlipConstraints,
			List<PaleoRateConstraint> ucerf2PaleoRateConstraints,
			PaleoProbabilityModel paleoProbModel) throws IOException {
		CSVFile<String> aveSlipTable = buildAveSlipTable(sol, aveSlipConstraints,
				ucerf2Sol, ucerf2AveSlipConstraints);
		CSVFile<String> paleoTable = buildPaleoRateTable(sol, paleoRateConstraints,
				ucerf2Sol, ucerf2PaleoRateConstraints, paleoProbModel);
		
		File aveSlipFile = new File(dir, "ave_slip_rates.csv");
		File paleoFile = new File(dir, "paleo_rates.csv");
		
		aveSlipTable.writeToFile(aveSlipFile);
		paleoTable.writeToFile(paleoFile);
	}
	
	public static CSVFile<String> buildAveSlipTable(
			InversionFaultSystemSolution sol, List<AveSlipConstraint> constraints,
			FaultSystemSolution ucerf2Sol, List<AveSlipConstraint> ucerf2AveSlipConstraints) {
		InversionFaultSystemRupSet rupSet = sol.getRupSet();
		
		CSVFile<String> csv = new CSVFile<String>(true);

		List<String> header = Lists.newArrayList(rupSet.getFaultModel().getShortName()
				+ " Mapping", "Latitude", "Longitude",
				"Weighted Mean Slip", "UCERF2 Reduced Slip Rate",
				"UCERF2 Proxy Event Rate",
				"UCERF3 Reduced Slip Rate",
				"UCERF3 Proxy Event Rate",
				"UCERF3 Paleo Visible Rate");

		csv.addLine(header);

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
			
			int subsectionIndex = constr.getSubSectionIndex();

			double slip = rupSet.getSlipRateForSection(subsectionIndex);
			double proxyRate = slip / constr.getWeightedMean();
			double obsRate = 0d;
			for (int rupID : rupSet.getRupturesForSection(constr
					.getSubSectionIndex())) {
				int sectIndexInRup = rupSet.getSectionsIndicesForRup(rupID)
						.indexOf(subsectionIndex);
				double slipOnSect = rupSet.getSlipOnSectionsForRup(rupID)[sectIndexInRup];
				double probVisible = AveSlipConstraint
						.getProbabilityOfObservedSlip(slipOnSect);
				obsRate += sol.getRateForRup(rupID) * probVisible;
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

			line.add(slip + "");
			line.add(proxyRate + "");
			line.add(obsRate + "");

			csv.addLine(line);
		}
		
		return csv;
	}
	
	public static CSVFile<String> buildPaleoRateTable(
			InversionFaultSystemSolution sol, List<PaleoRateConstraint> constraints,
			FaultSystemSolution ucerf2Sol, List<PaleoRateConstraint> ucerf2PaleoConstraints,
			PaleoProbabilityModel paleoProbModel) {
		InversionFaultSystemRupSet rupSet = sol.getRupSet();
		CSVFile<String> csv = new CSVFile<String>(true);

		List<String> header = Lists.newArrayList(rupSet.getFaultModel().getShortName()
				+ " Mapping", "Latitude", "Longitude",
				"Paleo Observed Rate", "Paleo Observed Lower Bound",
				"Paleo Observed Upper Bound",
				"UCERF2 Proxy Event Rate",
				"UCERF3 Paleo Visible Rate");

		csv.addLine(header);

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
			double obsRate = 0d;
			for (int rupID : rupSet.getRupturesForSection(constr.getSectionIndex())) {
				obsRate += sol.getRateForRup(rupID)
						* paleoProbModel.getProbPaleoVisible(rupSet, rupID,
								constr.getSectionIndex());
			}

			line.add(obsRate + "");

			csv.addLine(line);
		}
		
		return csv;
	}
	
	public static void main(String[] args) throws IOException, DocumentException {
		File invDir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions");
		File solFile = new File(invDir,
				"FM3_1_ZENG_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3" +
				"_VarPaleo10_VarMFDSmooth1000_VarSectNuclMFDWt0.01_sol.zip");
//				"FM3_1_ZENG_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3" +
//				"_VarPaleo0.1_VarAveSlip0.1_VarMFDSmooth1000_VarSectNuclMFDWt0.1_VarNone_sol.zip");
//				"FM2_1_UC2ALL_EllB_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU2" +
//				"_VarPaleo10_VarMFDSmooth1000_VarSectNuclMFDWt0.01_sol.zip");
//				"FM2_1_UC2ALL_EllB_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU2" +
//				"_VarPaleo0.1_VarAveSlip0.1_VarMFDSmooth1000_VarSectNuclMFDWt0.1_VarNone_sol.zip");
		InversionFaultSystemSolution sol = FaultSystemIO.loadInvSol(solFile);
//		FaultSystemSolution sol = UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(FaultModels.FM2_1);
		List<PaleoRateConstraint> paleoRateConstraint =
			UCERF3_PaleoRateConstraintFetcher.getConstraints(sol.getRupSet().getFaultSectionDataList());
		List<AveSlipConstraint> aveSlipConstraints = AveSlipConstraint.load(sol.getRupSet().getFaultSectionDataList());
		
//		Map<String, PlotSpec> specs =
//				getFaultSpecificPaleoPlotSpec(paleoRateConstraint, aveSlipConstraints, sol);
		
//		File plotDir = new File("/tmp/paleo_fault_plots_low_fm2_tapered");
//		File plotDir = new File("/tmp/paleo_fault_plots_ucerf2");
//		File plotDir = new File("/tmp/paleo_fault_plots_lowpaleo");
		File plotDir = new File("/tmp/paleo_fault_plots");
		if (!plotDir.exists())
			plotDir.mkdir();
		
		Map<String, List<Integer>> namedFaultsMap = sol.getRupSet().getFaultModel().getNamedFaultsMapAlt();
		CommandLineInversionRunner.writePaleoFaultPlots(paleoRateConstraint, aveSlipConstraints, namedFaultsMap, sol, plotDir);
	}

}
