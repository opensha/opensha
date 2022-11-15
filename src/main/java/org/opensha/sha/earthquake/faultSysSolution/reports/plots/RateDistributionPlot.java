package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.AnnealingProgress;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.LogicTreeRateStatistics;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;

import com.google.common.base.Preconditions;

public class RateDistributionPlot extends AbstractSolutionPlot {

	@Override
	public String getName() {
		return "Rupture Rate Distribution";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		FaultSystemSolution compSol = null;
		if (meta.hasComparisonSol())
			compSol = meta.comparison.sol;
		
		List<String> lines = new ArrayList<>();
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		double[] rates = sol.getRateForAllRups();
		double[] ratesNoMin;
		if (sol.hasModule(WaterLevelRates.class))
			ratesNoMin = sol.getModule(WaterLevelRates.class).subtractFrom(rates);
		else
			ratesNoMin = rates;
		double[] initial = sol.hasModule(InitialSolution.class) ?
				sol.getModule(InitialSolution.class).get() : new double[rates.length];
		
		int numNonZero = 0;
		int numAboveWaterlevel = 0;
		for (int r=0; r<rates.length; r++) {
			if ((float)rates[r] > 0f) {
				numNonZero++;
				if ((float)ratesNoMin[r] > 0f)
					numAboveWaterlevel++;
			}
		}
		
		// will invert, rows = columns here
		table.initNewLine();
		if (compSol != null)
			table.addColumn("");
		table.addColumn("**Non-zero ruptures**");
		boolean equivRups = compSol != null && meta.primary.rupSet.isEquivalentTo(meta.comparison.rupSet);
		if (equivRups)
			table.addColumn("**Unique non-zero ruptures**").addColumn("**Rate of unique non-zero ruptures**");
		if (ratesNoMin != rates)
			table.addColumn("**Ruptures above water-level**");
		boolean hasPerturbs = sol.hasModule(AnnealingProgress.class) || (compSol != null && compSol.hasModule(AnnealingProgress.class));
		if (hasPerturbs)
			table.addColumn("**Avg. # perturbations per rupture**").addColumn("**Avg. # perturbations per non-zero rupture**");
		table.finalizeLine().initNewLine();
		if (compSol != null)
			table.addColumn("Primary");
		
		table.addColumn(countDF.format(numNonZero)
				+" ("+percentDF.format((double)numNonZero/(double)rates.length)+")");
		if (equivRups) {
			double[] crates = meta.comparison.sol.getRateForAllRups();
			int uniqueNZ = 0;
			double uniqueRate = 0d;
			for (int r=0; r<rates.length; r++) {
				if (rates[r] > 0 && crates[r] == 0) {
					uniqueNZ++;
					uniqueRate += rates[r];
				}
			}
			table.addColumn(countDF.format(uniqueNZ)
					+" ("+percentDF.format((double)uniqueNZ/(double)rates.length)+")");
			table.addColumn((float)uniqueRate
					+" ("+percentDF.format((double)uniqueRate/(double)sol.getTotalRateForAllFaultSystemRups())+")");
		}
		if (ratesNoMin != rates)
			table.addColumn(countDF.format(numAboveWaterlevel)
				+" ("+percentDF.format((double)numAboveWaterlevel/(double)rates.length)+")");
		if (hasPerturbs) {
			AnnealingProgress progress = sol.getModule(AnnealingProgress.class);
			if (progress == null) {
				table.addColumn(na).addColumn(na);
			} else {
				long perturbs = progress.getNumPerturbations(progress.size()-1);
				table.addColumn((float)(perturbs/(double)rates.length));
				table.addColumn((float)(perturbs/(double)numAboveWaterlevel));
			}
		}
		table.finalizeLine();
		
		double[] crates = null;
		double[] cratesNoMin = null;
		
		boolean inferredWL = false;
		
		if (compSol != null) {
			crates = compSol.getRateForAllRups();
			if (compSol.hasModule(WaterLevelRates.class)) {
				cratesNoMin = compSol.getModule(WaterLevelRates.class).subtractFrom(crates);
			} else if (sol.hasModule(WaterLevelRates.class) && rates.length == crates.length) {
//				System.out.println("TRYING TO APPLY ORIG WATERLEVEL");
				// see if we can apply our waterlevel
				boolean equiv = meta.primary.rupSet.isEquivalentTo(meta.comparison.rupSet);
//				System.out.println("Initial equiv: "+equiv);
				if (!equiv) {
					// try to remap it
//					System.out.println("TRYING TO REMAP!");
					FaultSystemSolution remapped = getRemapped(meta.primary.rupSet, compSol);
					if (remapped != null) {
						compSol = remapped;
						equiv = true;
						crates = compSol.getRateForAllRups();
//						System.out.println("REMAPPED!");
					}
				}
				if (equiv) {
//					System.out.println("EQUIV!");
					// see if all of these rates are at or above the original waterlevel
					inferredWL = true;
					WaterLevelRates wl = sol.requireModule(WaterLevelRates.class);
					double[] origWL = wl.get();
					for (int r=0; r<crates.length; r++) {
						if ((float)crates[r] < (float)origWL[r]) {
							inferredWL = false;
							break;
						}
					}
					if (inferredWL)
						cratesNoMin = wl.subtractFrom(crates);
					else
						cratesNoMin = crates;
				} else {
					cratesNoMin = crates;
				}
			} else {
				cratesNoMin = crates;
			}
			
			int cnumNonZero = 0;
			int cnumAboveWaterlevel = 0;
			for (int r=0; r<crates.length; r++) {
				if (crates[r] > 0) {
					cnumNonZero++;
					if (cratesNoMin[r] > 0)
						cnumAboveWaterlevel++;
				}
			}
			
			table.initNewLine().addColumn("Comparison");
			table.addColumn(countDF.format(cnumNonZero)
					+" ("+percentDF.format((double)cnumNonZero/(double)crates.length)+")");
			if (equivRups) {
				int uniqueNZ = 0;
				double uniqueRate = 0d;
				for (int r=0; r<rates.length; r++) {
					if (crates[r] > 0 && rates[r] == 0) {
						uniqueNZ++;
						uniqueRate += crates[r];
					}
				}
				table.addColumn(countDF.format(uniqueNZ)
						+" ("+percentDF.format((double)uniqueNZ/(double)crates.length)+")");
				table.addColumn((float)uniqueRate+" ("+percentDF.format((double)uniqueRate
						/(double)meta.comparison.sol.getTotalRateForAllFaultSystemRups())+")");
			}
			if (ratesNoMin != rates) {
				if (cratesNoMin != crates)
					table.addColumn(countDF.format(cnumAboveWaterlevel)
							+" ("+percentDF.format((double)cnumAboveWaterlevel/(double)crates.length)+")");
				else
					table.addColumn("");
			}
			if (hasPerturbs) {
				AnnealingProgress progress = compSol.getModule(AnnealingProgress.class);
				if (progress == null) {
					table.addColumn(na).addColumn(na);
				} else {
					long perturbs = progress.getNumPerturbations(progress.size()-1);
					table.addColumn((float)(perturbs/(double)crates.length));
					table.addColumn((float)(perturbs/(double)cnumAboveWaterlevel));
				}
			}
		}
		
		lines.addAll(table.invert().build());
		lines.add("");
		
		if (inferredWL) {
			lines.add("_NOTE: The comnparison solution didn't have an attached waterlevel, but seems to have been "
					+ "generated with the same waterlevel, so we assume that was the case. This can cause slight "
					+ "numerical artifacts affecting the red line below and ruptures above waterlevel count above._");
			lines.add("");
		}
		
		if (compSol == null)
			SimulatedAnnealing.writeRateVsRankPlot(resourcesDir, "rate_dist", ratesNoMin, rates, initial);
		else
			SimulatedAnnealing.writeRateVsRankPlot(resourcesDir, "rate_dist", ratesNoMin, rates, initial,
					crates, cratesNoMin);
		lines.add("![Rate Distribution]("+relPathToResources+"/rate_dist.png)");
		lines.add("");
		lines.add("![Cumulative Rate Distribution]("+relPathToResources+"/rate_dist_cumulative.png)");
		
		if (sol.hasModule(LogicTreeRateStatistics.class)) {
			LogicTreeRateStatistics stats = sol.requireModule(LogicTreeRateStatistics.class);

			lines.add("");
			lines.add(getSubHeading()+" Logic Tree Rate Statistics");
			lines.add(topLink); lines.add("");
			
			lines.addAll(stats.buildTable().build());
		}
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		if (compSol != null) {
			int numRups = meta.primary.numRuptures;
			if (!meta.primary.rupSet.isEquivalentTo(compSol.getRupSet())) {
				// see if they're actually the same, but in a different order
				if (numRups != compSol.getRupSet().getNumRuptures())
					return lines;
				compSol = getRemapped(sol.getRupSet(), compSol);
				if (compSol == null)
					return lines;
			}
			
			double minMag = rupSet.getMinMag();
			double maxMag = rupSet.getMaxMag();
			List<Range> magRanges = new ArrayList<>();
			magRanges.add(null);
			int minBinSize = (int)(rupSet.getNumRuptures()*0.001);
			double binFloor = 0;
			double binCeil = Math.ceil(minMag);
//			System.out.println("Min bin size: "+minBinSize);
			while (binFloor < maxMag) {
				Range range = new Range(binFloor, binCeil);
				int numInRange = numInRange(range, rupSet.getMagForAllRups());
//				System.out.println("Potential range ["+(int)range.getLowerBound()+","+(int)range.getUpperBound()+"] has "+numInRange+" rups");
				if (binFloor == 0d) {
					// see if we should skip this
					if (numInRange < minBinSize) {
						binCeil += 1d;
//						System.out.println("\tskipping (too small)");
						continue;
					}
				} else if (binCeil > maxMag) {
					// see if we should combine this with the previous one
					if (numInRange < minBinSize && magRanges.size() > 1) {
//						System.out.println("\tskipping/ending (too small)");
						Range prev = magRanges.remove(magRanges.size()-1);
						magRanges.add(new Range(prev.getLowerBound(), binCeil));
						break;
					}
				}
				
				magRanges.add(range);
				
				binFloor = binCeil;
				binCeil += 1d;
			}
			
			List<XY_DataSet> scatters = new ArrayList<>();
			for (int i=0; i<magRanges.size(); i++)
				scatters.add(new DefaultXY_DataSet());
			
			MinMaxAveTracker rateTrack = new MinMaxAveTracker();
			for (int r=0; r<numRups; r++) {
				double x = Math.max(1e-16, sol.getRateForRup(r));
				double y = Math.max(1e-16, compSol.getRateForRup(r));
				for (int m=0; m<magRanges.size(); m++) {
					Range range = magRanges.get(m);
					if (range == null || range.contains(rupSet.getMagForRup(r)))
						scatters.get(m).set(x, y);
				}
				rateTrack.addValue(x);
				rateTrack.addValue(y);
			}
			Range range = new Range(Math.max(1e-16, Math.pow(10, Math.floor(Math.log10(rateTrack.getMin())))),
					Math.pow(10, Math.ceil(Math.log10(rateTrack.getMax()))));
			
			table = MarkdownUtils.tableBuilder();
			for (int m=0; m<magRanges.size(); m++) {
				Range magRange = magRanges.get(m);
				
				String title;
				String prefix;
				if (magRange == null) {
					title = "Rupture Rate Comparison";
					prefix = "rate_comparison";
				} else if (magRange.getLowerBound() == 0d) {
					title = "M≤"+(int)magRange.getUpperBound()+" Rupture Rate Comparison";
					prefix = "rate_comparison_m_lt_"+(int)magRange.getUpperBound();
				} else if (magRange.getUpperBound() >= maxMag) {
					title = "M≥"+(int)magRange.getLowerBound()+" Rupture Rate Comparison";
					prefix = "rate_comparison_m_ge_"+(int)magRange.getLowerBound();
				} else {
					title = "M"+(int)magRange.getLowerBound()+"-"+(int)magRange.getUpperBound()+" Rupture Rate Comparison";
					prefix = "rate_comparison_m_"+(int)magRange.getLowerBound()+"_"+(int)magRange.getUpperBound();
				}
				
				List<XY_DataSet> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
				oneToOne.set(range.getLowerBound(), range.getLowerBound());
				oneToOne.set(range.getUpperBound(), range.getUpperBound());

				XY_DataSet scatter = scatters.get(m);
				funcs.add(scatter);
				chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK));
				
				funcs.add(oneToOne);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GRAY));
				
				PlotSpec spec = new PlotSpec(funcs, chars, title,
						getTruncatedTitle(meta.primary.name)+" Rate", getTruncatedTitle(meta.comparison.name)+" Rate");
				HeadlessGraphPanel gp = PlotUtils.initHeadless();
				
				gp.drawGraphPanel(spec, true, true, range, range);
				
				PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, false, true, false, false);
				
				double logMin = Math.log10(range.getLowerBound());
				double logMax = Math.log10(range.getUpperBound());
				oneToOne = new DefaultXY_DataSet();
				oneToOne.set(logMin, logMin);
				oneToOne.set(logMax, logMax);
				funcs = new ArrayList<>();
				chars = new ArrayList<>();
				funcs.add(oneToOne);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GRAY));
				HistogramFunction refFunc = HistogramFunction.getEncompassingHistogram(logMin+0.01, logMax-0.01, 0.2);
				EvenlyDiscrXYZ_DataSet xyz = new EvenlyDiscrXYZ_DataSet(refFunc.size(), refFunc.size(),
						refFunc.getMinX(), refFunc.getMinX(), refFunc.getDelta());
				
				for (Point2D pt : scatter) {
					double logX = Math.log10(pt.getX());
					double logY = Math.log10(pt.getY());
					int xInd = refFunc.getClosestXIndex(logX);
					int yInd = refFunc.getClosestXIndex(logY);
					xyz.set(xInd, yInd, xyz.get(xInd, yInd)+1);
				}
				
				// set all zero to NaN so that it will plot clear
				for (int i=0; i<xyz.size(); i++) {
					if (xyz.get(i) == 0)
						xyz.set(i, Double.NaN);
				}
				
				xyz.log10();
				double maxZ = xyz.getMaxZ();
				CPT cpt = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().reverse().rescale(0d, Math.ceil(maxZ));
				cpt.setNanColor(new Color(255, 255, 255, 0));
				cpt.setBelowMinColor(cpt.getNanColor());
				
				XYZPlotSpec xyzSpec = new XYZPlotSpec(xyz, cpt, title, "Log10 "+spec.getXAxisLabel(),
						"Log10 "+spec.getYAxisLabel(), "Log10 Count");
				xyzSpec.setCPTPosition(RectangleEdge.BOTTOM);
				xyzSpec.setXYElems(funcs);
				xyzSpec.setXYChars(chars);
				Range logRange = new Range(refFunc.getMinX()-0.5*refFunc.getDelta(), refFunc.getMaxX()+0.5*refFunc.getDelta());
				gp.drawGraphPanel(xyzSpec, false, false, logRange, logRange);
				
				gp.getChartPanel().setSize(1000, 1000);
				gp.saveAsPNG(new File(resourcesDir, prefix+"_hist2D.png").getAbsolutePath());
				
				table.initNewLine();
				table.addColumn("![rate comparison]("+relPathToResources+"/"+prefix+".png)");
				table.addColumn("![rate hist]("+relPathToResources+"/"+prefix+"_hist2D.png)");
				table.finalizeLine();
			}
			
			lines.add("");
			lines.add(getSubHeading()+" Rupture Rate Comparison");
			lines.add(topLink); lines.add("");
			
			lines.addAll(table.build());
		}
		
		if (rupSet.hasModule(ClusterRuptures.class)) {
			boolean jumpFound = false;
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			for (ClusterRupture cRup : cRups) {
				if (cRup.getTotalNumJumps() > 0) {
					jumpFound = true;
					break;
				}
			}
			if (jumpFound) {
				// add jump rate vs rank plot
				double[] jumpRates = calcJumpRates(rupSet, sol.getRateForAllRups());
				if (jumpRates == null || jumpRates.length < 2)
					return lines;
				
				double[] initialJumpRates = sol.hasModule(InitialSolution.class) ?
						calcJumpRates(rupSet, initial) : null;
				if (initialJumpRates == null)
					initialJumpRates = new double[jumpRates.length];
				
				double[] compJumpRates = null;
				if (compSol != null)
					compJumpRates = calcJumpRates(compSol.getRupSet(), compSol.getRateForAllRups());
				
				lines.add(getSubHeading()+" Connection Rate Distribution");
				lines.add(topLink); lines.add("");
				lines.add("This plot gives the rate that each unique connection (between separate fault sections) is utilized.");
				lines.add("");
				
				SimulatedAnnealing.writeRateVsRankPlot(resourcesDir, "conn_rate_dist", jumpRates, jumpRates,
						initialJumpRates, compJumpRates, compJumpRates, "Connection Rate Distribution");
				lines.add("![Connection Rate Distribution]("+relPathToResources+"/conn_rate_dist.png)");
				lines.add("");
			}
		}
		
		return lines;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}
	
	private static FaultSystemSolution getRemapped(FaultSystemRupSet refRupSet, FaultSystemSolution oSol) {
		ClusterRuptures cRups1 = refRupSet.getModule(ClusterRuptures.class);
		FaultSystemRupSet oRupSet = oSol.getRupSet();
		ClusterRuptures cRups2 = oRupSet.getModule(ClusterRuptures.class);
		Preconditions.checkState(refRupSet.getNumRuptures() == oSol.getRupSet().getNumRuptures());
		
		int numRups = refRupSet.getNumRuptures();
		
		Map<UniqueRupture, Integer> refIndexes = new HashMap<>();
		for (int r=0; r<numRups; r++) {
			UniqueRupture unique;
			if (cRups1 == null)
				unique = UniqueRupture.forIDs(refRupSet.getSectionsIndicesForRup(r));
			else
				unique = cRups1.get(r).unique;
			refIndexes.put(unique, r);
		}
		Preconditions.checkState(refIndexes.size() == numRups);
		
		double[] remappedRates = new double[numRups];
		for (int r=0; r<numRups; r++) {
			UniqueRupture unique;
			if (cRups2 == null)
				unique = UniqueRupture.forIDs(oRupSet.getSectionsIndicesForRup(r));
			else
				unique = cRups2.get(r).unique;
			if (!refIndexes.containsKey(unique))
				return null;
			int index = refIndexes.get(unique);
			Preconditions.checkState(remappedRates[index] == 0d);
			remappedRates[index] = oSol.getRateForRup(r);
		}
		
		return new FaultSystemSolution(refRupSet, remappedRates);
	}
	
	private int numInRange(Range range, double[] mags) {
		int count = 0;
		for (double mag : mags)
			if (range.contains(mag))
				count++;
		return count;
	}
	
	private static double[] calcJumpRates(FaultSystemRupSet rupSet, double[] rates) {
		Map<Jump, Double> jumpRates = new HashMap<>();
		
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		for (int rupIndex=0; rupIndex<rupSet.getNumRuptures(); rupIndex++) {
			double rate = rates[rupIndex];
			if (rate == 0d)
				continue;
			ClusterRupture cRup = cRups.get(rupIndex);
			for (Jump jump : cRup.getJumpsIterable()) {
				if (jump.fromSection.getSectionId() > jump.toSection.getSectionId())
					jump = jump.reverse();
				if (jumpRates.containsKey(jump))
					jumpRates.put(jump, jumpRates.get(jump)+rate);
				else
					jumpRates.put(jump, rate);
			}
		}
		
		if (jumpRates.isEmpty())
			return null;
		
		double[] ret = new double[jumpRates.size()];
		int index = 0;
		for (Jump jump : jumpRates.keySet())
			ret[index++] = jumpRates.get(jump);
		return ret;
	}
	
	public static void main(String[] args) throws IOException {
		File mainDir = new File("/home/kevin/markdown/inversions");
		FaultSystemSolution sol1 = FaultSystemSolution.load(
//				new File(mainDir, "2021_08_05-coulomb-u3_ref-perturb_exp_scale_1e-2_to_1e-10-avg_anneal_20m-wlAsInitial-5hr-run0/solution.zip"));
				new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
						+ "2021_10_19-reproduce-ucerf3-ref_branch-tapered-convergence-u3Iters/mean_solution.zip"));
		FaultSystemSolution sol2 = FaultSystemSolution.load(
				new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
						+ "2021_10_19-reproduce-ucerf3-ref_branch-tapered-convergence-u3Iters/"
						+ "FM3_1_ZENGBB_Shaw09Mod_DsrTap_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3_mean_sol.zip"));
//				new File(mainDir, "2021_08_05-coulomb-u3_ref-perturb_exp_scale_1e-2_to_1e-10-avg_anneal_20m-wlAsInitial-5hr-run1/solution.zip"));
		
		sol1.getRupSet().addAvailableModule(new Callable<ClusterRuptures>() {

			@Override
			public ClusterRuptures call() throws Exception {
				return ClusterRuptures.singleStranged(sol1.getRupSet());
			}
		}, ClusterRuptures.class);
	
		RateDistributionPlot plot = new RateDistributionPlot();
		ReportMetadata meta = new ReportMetadata(new RupSetMetadata("sol1", sol1), new RupSetMetadata("sol2", sol2));
		plot.plot(sol1, meta, new File("/tmp"), "resources", "");
	}

}
