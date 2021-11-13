package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata.ScalarRange;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.PlausibilityFilterPlot.RupSetPlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeRakeChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.BiasiWesnouskyJumpProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CumulativeProbabilityFilter;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;

public class RupHistogramPlots extends AbstractRupSetPlot {
	
	private HistScalar[] scalars;
	
	public static final HistScalar[] RUP_SET_LIGHT_SCALARS = {
			HistScalar.LENGTH,
			HistScalar.MAG,
			HistScalar.SECT_COUNT,
			HistScalar.CLUSTER_COUNT,
			HistScalar.AREA,
			HistScalar.MAX_JUMP_DIST,
			HistScalar.CUM_JUMP_DIST,
			HistScalar.RAKE,
			HistScalar.CUM_RAKE_CHANGE
	};
	
	public static final HistScalar[] RUP_SET_SCALARS = HistScalar.values();
	
	public static final HistScalar[] SOL_SCALARS = {
			HistScalar.LENGTH,
			HistScalar.MAG,
			HistScalar.MAX_JUMP_DIST,
			HistScalar.CUM_JUMP_DIST,
			HistScalar.CUM_RAKE_CHANGE,
			HistScalar.MAX_SLIP_DIFF
	};

	public RupHistogramPlots() {
		this(HistScalar.values());
	}
	
	public RupHistogramPlots(HistScalar[] scalars) {
		this.scalars = scalars;
	}

	@Override
	public String getName() {
		return "Rupture Scalar Histograms";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		List<ClusterRupture> inputRups = rupSet.requireModule(ClusterRuptures.class).getAll();
		
		FaultSystemRupSet compRupSet = meta.comparison == null ? null : meta.comparison.rupSet;
		List<ClusterRupture> compRups = null;
		if (compRupSet != null && compRupSet.hasModule(ClusterRuptures.class))
			compRups = compRupSet.getModule(ClusterRuptures.class).getAll();

		File rupHtmlDir = new File(resourcesDir.getParentFile(), "hist_rup_pages");
		Preconditions.checkState(rupHtmlDir.exists() || rupHtmlDir.mkdir());
		List<HistScalarValues> inputScalarVals = new ArrayList<>();
		List<HistScalarValues> compScalarVals = compRups == null ? null : new ArrayList<>();
		
		HashSet<UniqueRupture> inputUniques = new HashSet<>(meta.primary.uniques);
		HashSet<UniqueRupture> compUniques = meta.comparison == null ? null : new HashSet<>(meta.comparison.uniques);
		
		SectionDistanceAzimuthCalculator distAzCalc = rupSet.getModule(SectionDistanceAzimuthCalculator.class);
		if (distAzCalc == null) {
			distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
			rupSet.addModule(distAzCalc);
		}
		
		for (HistScalar scalar : scalars) {
			lines.add(getSubHeading()+" "+scalar.getName());
			lines.add(topLink); lines.add("");
			lines.add(scalar.description);
			lines.add("");
			TableBuilder table = MarkdownUtils.tableBuilder();
			HistScalarValues inputScalars = new HistScalarValues(scalar, rupSet, sol,
					inputRups, distAzCalc);
			inputScalarVals.add(inputScalars);
			meta.primary.addScalar(inputScalars);
			HistScalarValues compScalars = null;
			if (compRupSet != null) {
				table.addLine(meta.primary.name, meta.comparison.name);
				compScalars = new HistScalarValues(scalar, compRupSet, meta.comparison.sol,
						compRups, distAzCalc);
				meta.comparison.addScalar(compScalars);
				compScalarVals.add(compScalars);
			}
			
			try {
				plotRuptureHistograms(resourcesDir, relPathToResources, "hist_"+scalar.name(), table, inputScalars,
					inputUniques, compScalars, compUniques);
			} catch (Exception err) {
				 System.out.println("Caught exception: " + err.getLocalizedMessage());
				 continue;
			}
			
			lines.addAll(table.build());
			lines.add("");
			
			if (sol == null) {
				// rup set only, do rupture examples
				double[] fractiles = scalar.getExampleRupPlotFractiles();
				if (fractiles == null)
					continue;

				lines.add(getSubHeading()+"# "+scalar.getName()+" Extremes & Examples");
				lines.add(topLink); lines.add("");
				lines.add("Example ruptures at various percentiles of "+scalar.getName());
				lines.add("");
				
				List<List<ClusterRupture>> ruptureLists = new ArrayList<>();
				List<HashSet<UniqueRupture>> uniqueLists = new ArrayList<>();
				List<String> headings = new ArrayList<>();
				List<List<Double>> scalarValsList = new ArrayList<>();
				List<String> prefixes = new ArrayList<>();
				List<FaultSystemRupSet> rupSets = new ArrayList<>();
				
				ruptureLists.add(inputRups);
				uniqueLists.add(null);
				if (compRupSet == null)
					headings.add(null);
				else
					headings.add("From the primary rupture set ("+meta.primary.name+"):");
				scalarValsList.add(inputScalars.getValues());
				
				prefixes.add("hist_rup");
				rupSets.add(rupSet);
				if (compRupSet != null) {
					if (meta.primaryOverlap.numUniqueRuptures > 0) {
						// add unique to primary
						HashSet<UniqueRupture> includeUniques = new HashSet<>();
						for (ClusterRupture rup : inputRups)
							if (!compUniques.contains(rup.unique))
								includeUniques.add(rup.unique);
						Preconditions.checkState(!includeUniques.isEmpty());
						ruptureLists.add(inputRups);
						uniqueLists.add(includeUniques);

						headings.add("Ruptures that are unique to the primary rupture set ("+meta.primary.name+"):");
						scalarValsList.add(inputScalars.getValues());

						prefixes.add("hist_rup");
						rupSets.add(rupSet);
					}
					if (meta.comparisonOverlap.numUniqueRuptures > 0) {
						// add unique to comparison
						HashSet<UniqueRupture> includeUniques = new HashSet<>();
						for (ClusterRupture rup : compRups)
							if (!inputUniques.contains(rup.unique))
								includeUniques.add(rup.unique);
						Preconditions.checkState(!includeUniques.isEmpty());
						ruptureLists.add(compRups);
						uniqueLists.add(includeUniques);

						headings.add("Ruptures that are unique to the comparison rupture set ("+meta.comparison.name+"):");
						scalarValsList.add(compScalars.getValues());

						prefixes.add("hist_comp_rup");
						rupSets.add(compRupSet);
					}
				}
				
				for (int i=0; i<headings.size(); i++) {
					List<ClusterRupture> rups = ruptureLists.get(i);
					String heading = headings.get(i);
					List<Double> vals = scalarValsList.get(i);
					HashSet<UniqueRupture> includeUniques = uniqueLists.get(i);
					FaultSystemRupSet myRupSet = rupSets.get(i);
					String prefix = prefixes.get(i);
					
					if (heading != null) {
						lines.add(heading);
						lines.add("");
					}
					
					List<Integer> filteredIndexes = new ArrayList<>();
					List<Double> filteredVals = new ArrayList<>();
					for (int r=0; r<rups.size(); r++) {
						if (includeUniques == null || includeUniques.contains(rups.get(r).unique)) {
							filteredIndexes.add(r);
							filteredVals.add(vals.get(r));
						}
					}
					List<Integer> sortedIndexes = ComparablePairing.getSortedData(filteredVals, filteredIndexes);
					
					int[] fractileIndexes = new int[fractiles.length];
					for (int j=0; j<fractiles.length; j++) {
						double f = fractiles[j];
						if (f == 1d)
							fractileIndexes[j] = filteredIndexes.size()-1;
						else
							fractileIndexes[j] = (int)(f*filteredIndexes.size());
					}
					
					table = MarkdownUtils.tableBuilder();
					table.initNewLine();
					for (int j=0; j<fractiles.length; j++) {
						int index = sortedIndexes.get(fractileIndexes[j]);
						double val = vals.get(index);
						double f = fractiles[j];
						String str;
						if (f == 0d)
							str = "Minimum";
						else if (f == 1d)
							str = "Maximum";
						else
							str = "p"+new DecimalFormat("0.#").format(f*100d);
						str += ": ";
						if (val < 0.1)
							str += (float)val;
						else
							str += new DecimalFormat("0.##").format(val);
						table.addColumn("**"+str+"**");
					}
					table.finalizeLine();
					table.initNewLine();
					for (int rawIndex : fractileIndexes) {
						int index = sortedIndexes.get(rawIndex);
						String rupPrefix = prefix+"_"+index;
						ClusterRupture rup = rups.get(index);
						if (includeUniques != null)
							Preconditions.checkState(includeUniques.contains(rup.unique),
									"IncludeUniques doesn't contain rupture at rawIndex=%s, index=%s: %s", rawIndex, index, rup);
						RupCartoonGenerator.plotRupture(resourcesDir, rupPrefix, rup,
								"Rupture "+index, false, true);
						table.addColumn("[<img src=\"" + relPathToResources + "/" + rupPrefix + ".png\" />]"+
								"("+relPathToResources+"/../"+generateRuptureInfoPage(myRupSet, rups.get(index),
										index, rupHtmlDir, rupPrefix, null, distAzCalc)+ ")");
					}
					
					lines.addAll(table.wrap(4, 0).build());
					lines.add("");
				}
			} else {
				// we have a solution and probably don't care about example ruptures, do rate scatters instead
				double[] rates = sol.getRateForAllRups();
				
				DefaultXY_DataSet valRateScatter = new DefaultXY_DataSet();
				MinMaxAveTracker track = new MinMaxAveTracker();
				for (int r=0; r<rates.length; r++) {
					double val = inputScalars.getValue(r);
					track.addValue(val);
					if (scalar.isLogX())
						val = Math.log10(val);
					valRateScatter.set(val, rates[r]);
				}
				
				EvenlyDiscretizedFunc refXFunc = scalar.getHistogram(track);
				
				List<XY_DataSet> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				funcs.add(valRateScatter);
				chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));
				
//				Range xRange = new Range(0.5*Math.floor(valRateScatter.getMinX()*2), 0.5*Math.ceil(valRateScatter.getMaxX()*2));
				Range xRange = new Range(refXFunc.getMinX()-0.5*refXFunc.getDelta(), refXFunc.getMaxX()+0.5*refXFunc.getDelta());
				Range yRange = new Range(Math.max(1e-16, valRateScatter.getMinY()), Math.max(1e-2, valRateScatter.getMaxY()));
				Range logYRange = new Range(Math.log10(yRange.getLowerBound()), Math.log10(yRange.getUpperBound()));
				
				String xAxisLabel = scalar.getxAxisLabel();
				if (scalar.isLogX())
					xAxisLabel = "Log10 "+xAxisLabel;
				
				PlotSpec spec = new PlotSpec(funcs, chars, scalar.getName()+" vs Rupture Rate", xAxisLabel, "Rupture Rate (/yr)");
				
				HeadlessGraphPanel gp = PlotUtils.initHeadless();
				
				gp.drawGraphPanel(spec, false, true, xRange, yRange);
				
				String prefix = "hist_"+scalar.name()+"_vs_rate";
				
				PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 850, true, false, false);
//				lines.add("");
//				lines.add("![Rate vs Mag]("+relPathToResources+"/"+prefix+"_rate_vs_mag.png)");
				
				funcs = new ArrayList<>();
				chars = new ArrayList<>();
//				EvenlyDiscretizedFunc refXFunc = new EvenlyDiscretizedFunc(xRange.getLowerBound(), xRange.getUpperBound(), 50);
				EvenlyDiscretizedFunc refYFunc = new EvenlyDiscretizedFunc(logYRange.getLowerBound(), logYRange.getUpperBound(), 50);
				EvenlyDiscrXYZ_DataSet xyz = new EvenlyDiscrXYZ_DataSet(refXFunc.size(), refYFunc.size(),
						refXFunc.getMinX(), refYFunc.getMinX(), refXFunc.getDelta(), refYFunc.getDelta());
				for (Point2D pt : valRateScatter) {
					double x = pt.getX();
					double logY = Math.log10(pt.getY());
					int xInd = refXFunc.getClosestXIndex(x);
					int yInd = refYFunc.getClosestXIndex(logY);
					xyz.set(xInd, yInd, xyz.get(xInd, yInd)+1);
				}
				
				xyz.log10();
				double maxZ = xyz.getMaxZ();
				CPT cpt = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().reverse().rescale(0d, Math.ceil(maxZ));
				cpt.setNanColor(new Color(255, 255, 255, 0));
				cpt.setBelowMinColor(cpt.getNaNColor());
				
				// set all zero to NaN so that it will plot clear
				for (int i=0; i<xyz.size(); i++) {
					if (xyz.get(i) == 0)
						xyz.set(i, Double.NaN);
				}
				
				XYZPlotSpec xyzSpec = new XYZPlotSpec(xyz, cpt, spec.getTitle(), spec.getXAxisLabel(),
						"Log10 "+spec.getYAxisLabel(), "Log10 Count");
				xyzSpec.setCPTPosition(RectangleEdge.BOTTOM);
				xyzSpec.setXYElems(funcs);
				xyzSpec.setXYChars(chars);
				gp.drawGraphPanel(xyzSpec, false, false, xRange, logYRange);
				
				gp.getChartPanel().setSize(1000, 850);
				gp.saveAsPNG(new File(resourcesDir, prefix+"_hist2D.png").getAbsolutePath());
				
				table = MarkdownUtils.tableBuilder();
				table.initNewLine();
				table.addColumn("![rate comparison]("+relPathToResources+"/"+prefix+".png)");
				table.addColumn("![rate hist]("+relPathToResources+"/"+prefix+"_hist2D.png)");
				table.finalizeLine();
				lines.add(getSubHeading()+"# "+scalar.getName()+" vs Rupture Rate");
				lines.add(topLink); lines.add("");
				lines.add("");
				lines.addAll(table.build());
			}
			
		}
		return lines;
	}

	@Override
	public List<String> getSummary(ReportMetadata meta, File resourcesDir, String relPathToResources, String topLink) {
		if (meta.primary.scalarRanges.isEmpty())
			return null;
		List<String> lines = new ArrayList<>();
		
		lines.add(getSubHeading()+" Scalar Values Table");
		lines.add(topLink); lines.add("");
		
		boolean comp = meta.comparison != null && !meta.comparison.scalarRanges.isEmpty();
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		if (comp)
			table.addLine("*Name*", "*Primary Range*", "*Comparison ("+meta.comparison.name+") Range*");
		else
			table.addLine("*Name*", "*Range*");
		Map<HistScalar, ScalarRange> primaryScalars = new HashMap<>();
		for (ScalarRange range : meta.primary.scalarRanges)
			primaryScalars.put(range.scalar, range);
		Map<HistScalar, ScalarRange> compScalars = new HashMap<>();
		if (comp)
			for (ScalarRange range : meta.comparison.scalarRanges)
				compScalars.put(range.scalar, range);
		
		for (HistScalar scalar : HistScalar.values()) {
			ScalarRange primaryScalar = primaryScalars.get(scalar);
			ScalarRange compScalar = compScalars.get(scalar);
			if (primaryScalar == null && compScalar == null)
				continue;
			table.initNewLine();
			table.addColumn("**"+scalar.getName()+"**");
			if (primaryScalar == null)
				table.addColumn("");
			else
				table.addColumn("["+(float)primaryScalar.min+", "+(float)primaryScalar.max+"]");
			if (comp) {
				if (compScalar == null)
					table.addColumn("");
				else
					table.addColumn("["+(float)compScalar.min+", "+(float)compScalar.max+"]");
			}
			table.finalizeLine();
		}
		lines.addAll(table.build());
		
		lines.add("");
		lines.add(getSubHeading()+" Length Figures");
		lines.add(topLink); lines.add("");
		
		table = MarkdownUtils.tableBuilder();
		if (comp) {
			table.addLine(meta.primary.name, meta.comparison.name);
			table.addLine("![Primary]("+relPathToResources+"/hist_LENGTH.png)", "![Comparison]("+relPathToResources+"/hist_LENGTH_comp.png)");
			if (new File(resourcesDir, "hist_LENGTH_rates.png").exists()
					|| new File(resourcesDir, "hist_LENGTH_comp_rates.png").exists()) {
				table.initNewLine();
				if (new File(resourcesDir, "hist_LENGTH_rates.png").exists())
					table.addColumn("![Primary]("+relPathToResources+"/hist_LENGTH_rates.png)");
				else
					table.addColumn("*N/A*");
				if (new File(resourcesDir, "hist_LENGTH_comp_rates.png").exists())
					table.addColumn("![Comparison]("+relPathToResources+"/hist_LENGTH_comp_rates.png)");
				else
					table.addColumn("*N/A*");
				table.finalizeLine();
			}
			if (new File(resourcesDir, "hist_LENGTH_rates_log.png").exists()
					|| new File(resourcesDir, "hist_LENGTH_comp_rates_log.png").exists()) {
				table.initNewLine();
				if (new File(resourcesDir, "hist_LENGTH_rates_log.png").exists())
					table.addColumn("![Primary]("+relPathToResources+"/hist_LENGTH_rates_log.png)");
				else
					table.addColumn("*N/A*");
				if (new File(resourcesDir, "hist_LENGTH_comp_rates_log.png").exists())
					table.addColumn("![Comparison]("+relPathToResources+"/hist_LENGTH_comp_rates_log.png)");
				else
					table.addColumn("*N/A*");
				table.finalizeLine();
			}
		} else {
			table.addLine("![Primary]("+relPathToResources+"/hist_LENGTH.png)");
			if (new File(resourcesDir, "hist_LENGTH_rates.png").exists())
				table.addLine("![Primary]("+relPathToResources+"/hist_LENGTH_rates.png)");
			if (new File(resourcesDir, "hist_LENGTH_rates_log.png").exists())
				table.addLine("![Primary]("+relPathToResources+"/hist_LENGTH_rates_log.png)");
		}
		lines.addAll(table.build());
		return lines;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(ClusterRuptures.class);
	}
	
	public static void plotRuptureHistograms(File outputDir, String relPathToOutput, String prefix, TableBuilder table,
			HistScalarValues inputScalars, HashSet<UniqueRupture> inputUniques, HistScalarValues compScalars,
			HashSet<UniqueRupture> compUniques) throws IOException {
		File main = plotRuptureHistogram(outputDir, prefix, inputScalars, compScalars, compUniques,
				MAIN_COLOR, false, false);
		// normal as-discretized hists
		table.initNewLine();
		table.addColumn("![hist]("+relPathToOutput+"/"+main.getName()+")");
		if (compScalars != null) {
			File comp = plotRuptureHistogram(outputDir, prefix+"_comp", compScalars,
					inputScalars, inputUniques, COMP_COLOR, false, false);
			table.addColumn("![hist]("+relPathToOutput+"/"+comp.getName()+")");
		}
		table.finalizeLine();
		boolean hasCompSol = compScalars != null && compScalars.getSol() != null;
		if (inputScalars.getSol() != null || hasCompSol) {
			// rate-weighted hists
			for (boolean logY : new boolean[] {false, true}) {
				table.initNewLine();
				
				String logAdd = logY ? "_log" : "";
				if (inputScalars.getSol() != null) {
					main = plotRuptureHistogram(outputDir, prefix+"_rates"+logAdd, inputScalars,
							compScalars, compUniques, MAIN_COLOR, logY, true);
					table.addColumn("![hist]("+relPathToOutput+"/"+main.getName()+")");
				} else {
					table.addColumn("*N/A*");
				}
				if (hasCompSol) {
					File comp = plotRuptureHistogram(outputDir, prefix+"_comp_rates"+logAdd, compScalars,
							inputScalars, inputUniques, COMP_COLOR, logY, true);
					table.addColumn("![hist]("+relPathToOutput+"/"+comp.getName()+")");
				} else {
					table.addColumn("*N/A*");
				}
				
				table.finalizeLine();
			}
		}
		
		// now cumulatives
		table.initNewLine();
		main = plotRuptureCumulatives(outputDir, prefix+"_cumulative", inputScalars, compScalars, compUniques,
				MAIN_COLOR, false);
		table.addColumn("![hist]("+relPathToOutput+"/"+main.getName()+")");
		if (compScalars != null) {
			File comp = plotRuptureCumulatives(outputDir, prefix+"_cumulative_comp", compScalars,
					inputScalars, inputUniques, COMP_COLOR, false);
			table.addColumn("![hist]("+relPathToOutput+"/"+comp.getName()+")");
		}
		table.finalizeLine();
	}
	
	public static File plotRuptureHistogram(File outputDir, String prefix,
			HistScalarValues scalarVals, HistScalarValues compScalarVals, HashSet<UniqueRupture> compUniques, Color color,
			boolean logY, boolean rateWeighted) throws IOException {
		List<Integer> includeIndexes = new ArrayList<>();
		for (int r=0; r<scalarVals.getRupSet().getNumRuptures(); r++)
			includeIndexes.add(r);
		return plotRuptureHistogram(outputDir, prefix, scalarVals, includeIndexes,
				compScalarVals, compUniques, color, logY, rateWeighted);
	}
	
	public static File plotRuptureHistogram(File outputDir, String prefix,
			HistScalarValues scalarVals, Collection<Integer> includeIndexes, HistScalarValues compScalarVals,
			HashSet<UniqueRupture> compUniques, Color color, boolean logY, boolean rateWeighted)
					throws IOException {
		List<Integer> indexesList = includeIndexes instanceof List<?> ?
				(List<Integer>)includeIndexes : new ArrayList<>(includeIndexes);
		MinMaxAveTracker track = new MinMaxAveTracker();
		for (int r : indexesList)
			track.addValue(scalarVals.getValue(r)); 
		if (compScalarVals != null) {
			// used only for bounds
			for (double scalar : compScalarVals.getValues())
				track.addValue(scalar);
		}
		HistScalar histScalar = scalarVals.getScalar();
		HistogramFunction hist = histScalar.getHistogram(track);
		boolean logX = histScalar.isLogX();
		HistogramFunction commonHist = null;
		if (compUniques != null)
			commonHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
		HistogramFunction compHist = null;
		if (compScalarVals != null && (!rateWeighted || compScalarVals.getSol() != null)) {
			compHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
			for (int i=0; i<compScalarVals.getValues().size(); i++) {
				double scalar = compScalarVals.getValue(i);
				double y = rateWeighted ? compScalarVals.getSol().getRateForRup(i) : 1d;
				int index;
				if (logX)
					index = scalar <= 0 ? 0 : compHist.getClosestXIndex(Math.log10(scalar));
				else
					index = compHist.getClosestXIndex(scalar);
				compHist.add(index, y);
			}
		}
		
		for (int i=0; i<indexesList.size(); i++) {
			int rupIndex = indexesList.get(i);
			double scalar = scalarVals.getValue(i);
			double y = rateWeighted ? scalarVals.getSol().getRateForRup(rupIndex) : 1;
			int index;
			if (logX)
				index = scalar <= 0 ? 0 : hist.getClosestXIndex(Math.log10(scalar));
			else
				index = hist.getClosestXIndex(scalar);
			hist.add(index, y);
			if (compUniques != null && compUniques.contains(scalarVals.getRups().get(rupIndex).unique))
				commonHist.add(index, y);
		}
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		if (logX) {
			ArbitrarilyDiscretizedFunc linearHist = new ArbitrarilyDiscretizedFunc();
			for (Point2D pt : hist)
				linearHist.set(Math.pow(10, pt.getX()), pt.getY());
			
			linearHist.setName("Unique");
			funcs.add(linearHist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));
			
			if (commonHist != null) {
				linearHist = new ArbitrarilyDiscretizedFunc();
				for (Point2D pt : commonHist)
					linearHist.set(Math.pow(10, pt.getX()), pt.getY());
				linearHist.setName("Common To Both");
				funcs.add(linearHist);
				chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, COMMON_COLOR));
			}
		} else {
			hist.setName("Unique");
			funcs.add(hist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));
			
			if (commonHist != null) {
				commonHist.setName("Common To Both");
				funcs.add(commonHist);
				chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, COMMON_COLOR));
			}
		}
		
		String title = histScalar.getName()+" Histogram";
		String xAxisLabel = histScalar.getxAxisLabel();
		String yAxisLabel = rateWeighted ? "Annual Rate" : "Count";
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
		spec.setLegendVisible(compUniques != null);
		
		Range xRange;
		if (logX)
			xRange = new Range(Math.pow(10, hist.getMinX() - 0.5*hist.getDelta()),
					Math.pow(10, hist.getMaxX() + 0.5*hist.getDelta()));
		else
			xRange = new Range(hist.getMinX() - 0.5*hist.getDelta(),
					hist.getMaxX() + 0.5*hist.getDelta());
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);
		
		Range yRange;
		if (logY) {
			double minY = Double.POSITIVE_INFINITY;
			double maxY = 0d;
			for (DiscretizedFunc func : funcs) {
				for (Point2D pt : func) {
					double y = pt.getY();
					if (y > 0) {
						minY = Math.min(minY, y);
						maxY = Math.max(maxY, y);
					}
				}
			}
			if (compHist != null) {
				for (Point2D pt : compHist) {
					double y = pt.getY();
					if (y > 0) {
						minY = Math.min(minY, y);
						maxY = Math.max(maxY, y);
					}
				}
			}
			yRange = new Range(Math.pow(10, Math.floor(Math.log10(minY))),
					Math.pow(10, Math.ceil(Math.log10(maxY))));
		} else {
			double maxY = hist.getMaxY();
			if (compHist != null)
				maxY = Math.max(maxY, compHist.getMaxY());
			yRange = new Range(0, 1.05*maxY);
		}
		
		gp.drawGraphPanel(spec, logX, logY, xRange, yRange);
		gp.getChartPanel().setSize(800, 600);
		File pngFile = new File(outputDir, prefix+".png");
		File pdfFile = new File(outputDir, prefix+".pdf");
		File txtFile = new File(outputDir, prefix+".txt");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		gp.saveAsTXT(txtFile.getAbsolutePath());
		return pngFile;
	}
	
	private static HistogramFunction getCumulativeFractionalHist(HistogramFunction hist) {
		HistogramFunction ret = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
		double sum = 0d;
		for (int i=0; i<hist.size(); i++) {
			sum += hist.getY(i);
			ret.set(i, sum);
		}
		ret.scale(1d/sum);
		return ret;
	}
	
	public static File plotRuptureCumulatives(File outputDir, String prefix,
			HistScalarValues scalarVals, HistScalarValues compScalarVals,
			HashSet<UniqueRupture> compUniques, Color color, boolean logY)
					throws IOException {
		List<Integer> includeIndexes = new ArrayList<>();
		for (int r=0; r<scalarVals.getRupSet().getNumRuptures(); r++)
			includeIndexes.add(r);
		return plotRuptureCumulatives(outputDir, prefix, scalarVals, includeIndexes, compScalarVals,
				compUniques, color, logY);
	}
	
	public static File plotRuptureCumulatives(File outputDir, String prefix,
			HistScalarValues scalarVals, Collection<Integer> includeIndexes, HistScalarValues compScalarVals,
			HashSet<UniqueRupture> compUniques, Color color, boolean logY)
					throws IOException {
		List<Integer> indexesList = includeIndexes instanceof List<?> ?
				(List<Integer>)includeIndexes : new ArrayList<>(includeIndexes);
		MinMaxAveTracker track = new MinMaxAveTracker();
		for (int r : indexesList)
			track.addValue(scalarVals.getValue(r)); 
		if (compScalarVals != null) {
			// used only for bounds
			for (double scalar : compScalarVals.getValues())
				track.addValue(scalar);
		}
		HistScalar histScalar = scalarVals.getScalar();
		HistogramFunction hist = histScalar.getHistogram(track);
		// now super-sample it for the cumulative plot
		double origDelta = hist.getDelta();
		double origMin = hist.getMinX();
		double newDelta = origDelta/50d;
		double newMin = origMin - 0.5*origDelta + 0.5*newDelta;
		int newNum = hist.size() == 1 ? 1 : hist.size()*50;
		hist = new HistogramFunction(newMin, newNum, newDelta);
		boolean logX = histScalar.isLogX();
		HistogramFunction commonHist = null;
		if (compUniques != null)
			commonHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
		HistogramFunction rateHist = null;
		HistogramFunction commonRateHist = null;
		if (scalarVals.getSol() != null) {
			rateHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
			commonRateHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
		}
		
		for (int i=0; i<indexesList.size(); i++) {
			int rupIndex = indexesList.get(i);
			double scalar = scalarVals.getValue(i);
			int index;
			if (logX)
				index = scalar <= 0 ? 0 : hist.getClosestXIndex(Math.log10(scalar));
			else
				index = hist.getClosestXIndex(scalar);
			hist.add(index, 1d);
			if (rateHist != null)
				rateHist.add(index, scalarVals.getSol().getRateForRup(rupIndex));
			if (compUniques != null && compUniques.contains(scalarVals.getRups().get(rupIndex).unique)) {
				commonHist.add(index, 1d);
				if (commonRateHist != null)
					commonRateHist.add(index, scalarVals.getSol().getRateForRup(rupIndex));
			}
		}
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		hist = getCumulativeFractionalHist(hist);
		if (commonHist != null)
			commonHist = getCumulativeFractionalHist(commonHist);
		if (rateHist != null)
			rateHist = getCumulativeFractionalHist(rateHist);
		if (commonRateHist != null)
			commonRateHist = getCumulativeFractionalHist(commonRateHist);
		
		boolean[] rateWeighteds;
		if (rateHist == null)
			rateWeighteds = new boolean[] { false };
		else
			rateWeighteds = new boolean[] { false, true };
		
		for (boolean rateWeighted : rateWeighteds) {
			DiscretizedFunc myHist;
			DiscretizedFunc myCommonHist = null;
			PlotLineType plt;
			if (rateWeighted) {
				myHist = rateHist;
				myHist.setName("Rate-Weighted");
				if (commonRateHist != null) {
					myCommonHist = commonRateHist;
					myCommonHist.setName("Common");
				}
				plt = PlotLineType.DASHED;
			} else {
				myHist = hist;
				myHist.setName("As Discretized");
				if (commonHist != null) {
					myCommonHist = commonHist;
					myCommonHist.setName("Common");
				}
				plt = PlotLineType.SOLID;
			}
			if (logX) {
				ArbitrarilyDiscretizedFunc linearHist = new ArbitrarilyDiscretizedFunc();
				for (Point2D pt : myHist)
					linearHist.set(Math.pow(10, pt.getX()), pt.getY());
				
				funcs.add(linearHist);
				chars.add(new PlotCurveCharacterstics(plt, 3f, color));
				
				if (myCommonHist != null) {
					linearHist = new ArbitrarilyDiscretizedFunc();
					for (Point2D pt : myCommonHist)
						linearHist.set(Math.pow(10, pt.getX()), pt.getY());
					funcs.add(linearHist);
					chars.add(new PlotCurveCharacterstics(plt, 3f, COMMON_COLOR));
				}
			} else {
				funcs.add(myHist);
				chars.add(new PlotCurveCharacterstics(plt, 3f, color));
				
				if (myCommonHist != null) {
					funcs.add(myCommonHist);
					chars.add(new PlotCurveCharacterstics(plt, 3f, COMMON_COLOR));
				}
			}
		}
		
		String title = histScalar.getName()+" Cumulative Distribution";
		String xAxisLabel = histScalar.getxAxisLabel();
		String yAxisLabel = "Cumulative Fraction";
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
		spec.setLegendVisible(true);
		
		Range xRange;
		if (logX)
			xRange = new Range(Math.pow(10, hist.getMinX() - 0.5*hist.getDelta()),
					Math.pow(10, hist.getMaxX() + 0.5*hist.getDelta()));
		else
			xRange = new Range(hist.getMinX() - 0.5*hist.getDelta(),
					hist.getMaxX() + 0.5*hist.getDelta());
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);
		
		Range yRange;
		if (logY)
			yRange = new Range(1e-8, 1);
		else
			yRange = new Range(0, 1d);
		
		gp.drawGraphPanel(spec, logX, logY, xRange, yRange);
		gp.getChartPanel().setSize(800, 600);
		File pngFile = new File(outputDir, prefix+".png");
		File pdfFile = new File(outputDir, prefix+".pdf");
		File txtFile = new File(outputDir, prefix+".txt");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		gp.saveAsTXT(txtFile.getAbsolutePath());
		
		return pngFile;
	}
	
	public static class HistScalarValues {
		private final HistScalar scalar;
		private final FaultSystemRupSet rupSet;
		private final FaultSystemSolution sol;
		private final List<ClusterRupture> rups;
		private final List<Double> values;
		
		public HistScalarValues(HistScalar scalar, FaultSystemRupSet rupSet, FaultSystemSolution sol,
				List<ClusterRupture> rups, SectionDistanceAzimuthCalculator distAzCalc) {
			super();
			this.scalar = scalar;
			this.rupSet = rupSet;
			this.sol = sol;
			this.rups = rups;
			
			values = new ArrayList<>();
			System.out.println("Calculating "+scalar.getName()+" for "+rups.size()+" ruptures");
			for (int r=0; r<rups.size(); r++)
				values.add(scalar.getValue(r, rupSet, rups.get(r), distAzCalc));
		}

		/**
		 * @return the rupSet
		 */
		public FaultSystemRupSet getRupSet() {
			return rupSet;
		}

		/**
		 * @return the values
		 */
		public List<Double> getValues() {
			return values;
		}

		/**
		 * @return the values
		 */
		public double getValue(int rupIndex) {
			return values.get(rupIndex);
		}

		/**
		 * @return the scalar
		 */
		public HistScalar getScalar() {
			return scalar;
		}

		/**
		 * @return the sol
		 */
		public FaultSystemSolution getSol() {
			return sol;
		}

		/**
		 * @return the rups
		 */
		public List<ClusterRupture> getRups() {
			return rups;
		}
	}
	
	private static double[] example_fractiles_default =  { 0d, 0.5, 0.9, 0.95, 0.975, 0.99, 0.999, 1d };
	
	public enum HistScalar {
		LENGTH("Rupture Length", "Length (km)",
				"Total length (km) of the rupture, not including jumps or gaps.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				HistogramFunction hist;
				if (scalarTrack.getMax() <= 250d)
					hist = HistogramFunction.getEncompassingHistogram(
							0d, Math.max(100d, scalarTrack.getMax()), 10d);
				else if (scalarTrack.getMax() <= 500d)
					hist = HistogramFunction.getEncompassingHistogram(
							0d, scalarTrack.getMax(), 20d);
				else
					hist = HistogramFunction.getEncompassingHistogram(
							0d, scalarTrack.getMax(), 50d);
				return hist;
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				return rupSet.getLengthForRup(index)*1e-3;
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		MAG("Rupture Magnitude", "Magnitude", "Magnitude of the rupture.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				double minMag;
//				if (scalarTrack.getMin() < 5d)
//					// only go below 6 if we have really weirdly low magnitudes (below 5)
					minMag = 0.5*Math.floor(scalarTrack.getMin()*2);
//				else
//					minMag = 6d;
				double maxMag;
				if (scalarTrack.getMax() > 9d)
					maxMag = 0.5*Math.ceil(scalarTrack.getMax()*2);
				else if (scalarTrack.getMax() > 7.5)
					maxMag = 8.5d;
				else 
					maxMag = 8d;
				return HistogramFunction.getEncompassingHistogram(minMag, maxMag-0.1, 0.1d);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				return rupSet.getMagForRup(index);
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		SECT_COUNT("Subsection Count", "# Subsections", "Total number of subsections involved in a rupture.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				return new HistogramFunction(1, (int)Math.max(10, scalarTrack.getMax()), 1d);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				return rupSet.getSectionsIndicesForRup(index).size();
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		CLUSTER_COUNT("Cluster Count", "# Clusters",
				"Total number of clusters (of contiguous subsections on the same parent fault section) "
				+ "in a rupture.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				return new HistogramFunction(1, (int)Math.max(2, scalarTrack.getMax()), 1d);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				return rup.getTotalNumClusters();
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		AREA("Area", "Area (km^2)", "Total area of the rupture (km^2).") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				HistogramFunction hist = HistogramFunction.getEncompassingHistogram(
							0d, scalarTrack.getMax(), 100d);
				return hist;
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				return rupSet.getAreaForRup(index)*1e-6;
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		MAX_JUMP_DIST("Maximum Jump Dist", "Maximum Jump Distance (km)",
				"The maximum jump distance in the rupture.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				double delta;
				if (scalarTrack.getMax() > 20d)
					delta = 2d;
				else
					delta = 1d;
				return HistogramFunction.getEncompassingHistogram(0d, scalarTrack.getMax(), delta);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				double max = 0d;
				for (Jump jump : rup.getJumpsIterable())
					max = Math.max(max, jump.distance);
				return max;
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		CUM_JUMP_DIST("Cumulative Jump Dist", "Cumulative Jump Distance (km)",
				"The total cumulative jump distance summed over all jumps in the rupture.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				double delta;
				if (scalarTrack.getMax() > 20d)
					delta = 2d;
				else
					delta = 1d;
				return HistogramFunction.getEncompassingHistogram(0d, Math.max(1d, scalarTrack.getMax()), delta);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				double sum = 0d;
				for (Jump jump : rup.getJumpsIterable())
					sum += jump.distance;
				return sum;
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		IDEAL_LEN_RATIO("Ideal Length Ratio", "Ideal Length Ratio",
				"The ratio between the total length of this rupture and the 'idealized length,' which we "
				+ "define as the straight line distance between the furthest two subsections.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				return new HistogramFunction(0.25, 10, 0.5);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				double idealLen = calcIdealMinLength(rupSet.getFaultSectionDataForRupture(index), distAzCalc);
				double len = LENGTH.getValue(index, rupSet, rup, distAzCalc);
				return len/idealLen;
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		IDEAL_LEN_DIFF("Ideal Length Difference", "Ideal Length Difference",
				"The difference between the total length of this rupture and the 'idealized length,' which we "
				+ "define as the straight line distance between the furthest two subsections.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				HistogramFunction hist;
				if (scalarTrack.getMax() <= 100d)
					hist = HistogramFunction.getEncompassingHistogram(
							scalarTrack.getMin(), 100d, 10d);
				else
					hist = HistogramFunction.getEncompassingHistogram(
							scalarTrack.getMin(), scalarTrack.getMax(), 50d);
				return hist;
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				double idealLen = calcIdealMinLength(rupSet.getFaultSectionDataForRupture(index), distAzCalc);
				double len = LENGTH.getValue(index, rupSet, rup, distAzCalc);
				return len - idealLen;
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		RAKE("Rake", "Rake (degrees)",
				"The area-averaged rake for this rupture.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				return new HistogramFunction(-175, 36, 10d);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				return rupSet.getAveRakeForRup(index);
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				// don't want rake rupture plots
				return null;
			}
		},
		CUM_RAKE_CHANGE("Cumulative Rake Change", "Cumulative Rake Change (degrees)",
				"Cumulative rake change for this rupture.") {
			CumulativeRakeChangeFilter filter = null;
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				return HistogramFunction.getEncompassingHistogram(0d, scalarTrack.getMax(), 60d);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				if (filter == null) {
					synchronized (this) {
						if (filter == null)
							filter = new CumulativeRakeChangeFilter(180f);
					}
				}
				return filter.getValue(rup);
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		MECH_CHANGE("Mechanism Change", "# Mechanism Changes",
				"The number of times a rupture changed mechanisms, e.g., "
				+ "from right-lateral SS to left-lateral or SS to reverse.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				int num = Integer.max(2, 1 + (int)scalarTrack.getMax());
				return new HistogramFunction(0d, num, 1d);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				if (rup.getTotalNumClusters() == 1)
					return 0;
				int count = 0;
				for (Jump jump : rup.getJumpsIterable())
					if (RakeType.getType(jump.fromSection.getAveRake())
							!= RakeType.getType(jump.toSection.getAveRake()))
						count++;
				return count;
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		CUM_AZ_CHANGE("Cumulative Azimuth Change", "Cumulative Azimuth Change (degrees)",
				"Cumulative azimuth change for this rupture.") {
			CumulativeAzimuthChangeFilter filter = null;
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				return HistogramFunction.getEncompassingHistogram(0d, scalarTrack.getMax(), 20d);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				if (filter == null) {
					synchronized (this) {
						if (filter == null)
							filter = new CumulativeAzimuthChangeFilter(
									new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc), 560f);
					}
				}
				return filter.getValue(rup);
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		},
		BW_PROB("Biasi & Wesnousky (2016,2017) Prob", "BS '16-'17 Prob",
				"Biasi & Wesnousky (2016,2017) conditional probability of passing through each jump.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
//				return HistogramFunction.getEncompassingHistogram(0d, 1d, 0.02);
				return HistogramFunction.getEncompassingHistogram(-5, 0, 0.1);
			}
			
			public boolean isLogX() {
				return true;
			}
			
			private CumulativeProbabilityFilter filter = null;

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				if (filter == null) {
					synchronized (this) {
						if (filter == null)
							filter = new CumulativeProbabilityFilter(1e-10f,
									BiasiWesnouskyJumpProb.getPrefferedBWCalcs(distAzCalc));
					}
				}
				return filter.getValue(rup).doubleValue();
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return new double[] { 1d, 0.5, 0.1, 0.05, 0.025, 0.01, 0.001, 0 };
			}
		},
		MAX_SLIP_DIFF("Max Slip Rate Difference", "Section Max - Min Slip Rate in Rupture (mm/yr)",
				"The difference between the slip rate of the sections with the highest and lowest "
				+ "slip rate in the rupture.") {
			@Override
			public HistogramFunction getHistogram(MinMaxAveTracker scalarTrack) {
				double max = Math.max(5d, scalarTrack.getMax());
				if (max > 50)
					return HistogramFunction.getEncompassingHistogram(0d, max, 5d);
				else if (max > 20)
					return HistogramFunction.getEncompassingHistogram(0d, max, 2d);
				return HistogramFunction.getEncompassingHistogram(0d, max, 1d);
			}

			@Override
			public double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
					SectionDistanceAzimuthCalculator distAzCalc) {
				SectSlipRates slipRates = rupSet.getModule(SectSlipRates.class);
				double max = 0d;
				double min = Double.POSITIVE_INFINITY;
				for (int s : rupSet.getSectionsIndicesForRup(index)) {
					double slipRate = slipRates.getSlipRate(s)*1e3; // m/yr -> mm/yr
					max = Math.max(max, slipRate);
					min = Math.min(min, slipRate);
				}
				return max-min;
			}

			@Override
			public double[] getExampleRupPlotFractiles() {
				return example_fractiles_default;
			}
		};
		
		private String name;
		private String xAxisLabel;
		private String description;

		private HistScalar(String name, String xAxisLabel, String description) {
			this.name = name;
			this.xAxisLabel = xAxisLabel;
			this.description = description;
		}
		
		public boolean isLogX() {
			return false;
		}
		
		public abstract HistogramFunction getHistogram(MinMaxAveTracker scalarTrack);
		
		public abstract double getValue(int index, FaultSystemRupSet rupSet, ClusterRupture rup,
				SectionDistanceAzimuthCalculator distAzCalc);
		
		public abstract double[] getExampleRupPlotFractiles();

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @return the xAxisLabel
		 */
		public String getxAxisLabel() {
			return xAxisLabel;
		}
	}
	
	private static double calcIdealMinLength(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distAzCalc) {
		FaultSection farS1 = subSects.get(0);
		if (subSects.size() == 1)
			return LocationUtils.horzDistance(farS1.getFaultTrace().first(), farS1.getFaultTrace().last());
		FaultSection farS2 = null;
		double maxDist = 0d;
		for (int i=1; i<subSects.size(); i++) {
			FaultSection s2 = subSects.get(i);
			double dist = distAzCalc.getDistance(farS1, s2);
			if (dist >= maxDist) {
				maxDist = dist;
				farS2 = s2;
			}
		}
		if (farS1 == farS2)
			return farS1.getTraceLength();
		maxDist = 0d;
		for (Location l1 : farS1.getFaultTrace())
			for (Location l2 : farS2.getFaultTrace())
				maxDist = Math.max(maxDist, LocationUtils.horzDistanceFast(l1, l2));

		return maxDist;
	}
	
	public enum RakeType {
		RIGHT_LATERAL("Right-Lateral SS", "rl", Color.RED.darker()) {
			@Override
			public boolean isMatch(double rake) {
				return (float)rake >= -180f && (float)rake <= -170f
						|| (float)rake <= 180f && (float)rake >= 170f;
			}
		},
		LEFT_LATERAL("Left-Lateral SS", "ll", Color.GREEN.darker()) {
			@Override
			public boolean isMatch(double rake) {
				return (float)rake >= -10f && (float)rake <= 10f;
			}
		},
		REVERSE("Reverse", "rev", Color.BLUE.darker()) {
			@Override
			public boolean isMatch(double rake) {
				return (float)rake >= 80f && (float)rake <= 100f;
			}
		},
		NORMAL("Normal", "norm", Color.YELLOW.darker()) {
			@Override
			public boolean isMatch(double rake) {
				return (float)rake >= -100f && (float)rake <= -80f;
			}
		},
		OBLIQUE("Oblique", "oblique", Color.MAGENTA.darker()) {
			@Override
			public boolean isMatch(double rake) {
				for (RakeType type : values())
					if (type != this && type.isMatch(rake))
						return false;
				return true;
			}
		};
		
		public final String name;
		public final String prefix;
		public final Color color;

		private RakeType(String name, String prefix, Color color) {
			this.name = name;
			this.prefix = prefix;
			this.color = color;
		}
		
		public abstract boolean isMatch(double rake);
		
		public static RakeType getType(double rake) {
			for (RakeType type : values())
				if (type != OBLIQUE && type.isMatch(rake))
					return type;
			return OBLIQUE;
		}
	}
	
	static String generateRuptureInfoPage(FaultSystemRupSet rupSet, ClusterRupture rupture, int rupIndex,
			File outputDir, String fileNamePrefix, RupSetPlausibilityResult plausibiltyResult,
			SectionDistanceAzimuthCalculator distAzCalc) throws IOException {
		DecimalFormat format = new DecimalFormat("###,###.#");

		List<String> lines = new ArrayList<>();
		lines.add("## Rupture " + rupIndex);
		lines.add("");
		lines.add("![Rupture " + rupIndex + "](../resources/" + fileNamePrefix + ".png)");

		HashMap<Integer, Jump> jumps = new HashMap<>();
		for (Jump jump : rupture.getJumpsIterable()) {
			jumps.put(jump.fromSection.getSectionId(), jump);
		}
		
		lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder().initNewLine();
		HistScalar[] scalars = HistScalar.values();
		for (HistScalar scalar : scalars)
			table.addColumn("**"+scalar.getxAxisLabel()+"**");
		table.finalizeLine().initNewLine();
		for (HistScalar scalar : scalars) {
			double val = scalar.getValue(rupIndex, rupSet, rupture, distAzCalc);
			if (Math.abs(val) < 1)
				table.addColumn((float)val+"");
			else
				table.addColumn(format.format(val));
		}
		table.finalizeLine();
		lines.addAll(table.wrap(5, 0).build());

		lines.add("");
		lines.add("Text representation:");
		lines.add("");
		lines.add("```");
		lines.add(rupture.toString());
		lines.add("```");
		lines.add("");

		List<FaultSection> sections = rupSet.getFaultSectionDataForRupture(rupIndex);

		Location startLocation = sections.get(0).getFaultTrace().get(0);
		Location lastLocation = sections.get(sections.size() - 1).getFaultTrace().get(sections.get(sections.size() - 1).getFaultTrace().size() - 1);
		String location = null;
		if (startLocation.getLatitude() < lastLocation.getLatitude()) {
			location = " South ";
		} else {
			location = " North ";
		}
		if (startLocation.getLongitude() < lastLocation.getLongitude()) {
			location += " West ";
		} else {
			location += " East ";
		}
		lines.add("");
		lines.add("Fault section list. First section listed is " + location + " relative to the last section.");
		lines.add("");

		int lastParent = Integer.MIN_VALUE;
		List<String> sectionIds = new ArrayList<>();
		for (FaultSection section : sections) {
			if (lastParent != section.getParentSectionId()) {
				if (lastParent != Integer.MIN_VALUE) {
					lines.add("    * " + String.join(", ", sectionIds));
					sectionIds.clear();
				}
				lines.add("* " + section.getParentSectionName());
				lastParent = section.getParentSectionId();
			}
			if (jumps.containsKey(section.getSectionId())) {
				Jump jump = jumps.get(section.getSectionId());
				sectionIds.add("" + section.getSectionId() + " (jump to " + jump.toSection.getSectionId() + ", " + format.format(jump.distance) + "km)");
			} else {
				sectionIds.add("" + section.getSectionId());
			}
		}
		lines.add("    * " + String.join(", ", sectionIds));
		
		if (plausibiltyResult != null) {
			lines.add("");
			lines.add("### Plausibility Filters Results");
			lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.addLine("Name", "Result", "Scalar Value");
			for (int f=0; f<plausibiltyResult.filters.size(); f++) {
				table.initNewLine();
				table.addColumn(plausibiltyResult.filters.get(f).getName());
				PlausibilityResult result = plausibiltyResult.filterResults.get(f).get(rupIndex);
				if (result == null)
					table.addColumn("Erred");
				else
					table.addColumn(result.toString());
				if (plausibiltyResult.scalarVals.get(f) == null) {
					table.addColumn("*N/A*");
				} else {
					Double scalar = plausibiltyResult.scalarVals.get(f).get(rupIndex);
					if (scalar == null) {
						table.addColumn("*N/A*");
					} else {
						String str = scalar.floatValue()+"";
						PlausibilityFilter filter = plausibiltyResult.filters.get(f);
						String units = ((ScalarValuePlausibiltyFilter<?>)filter).getScalarUnits();
						if (units != null)
							str += " ("+units+")";
						table.addColumn(str);
					}
				}
			}
			table.finalizeLine();
			lines.addAll(table.build());
		}
		
		MarkdownUtils.writeHTML(lines, new File(outputDir, fileNamePrefix + ".html"));
		return outputDir.getName()+"/"+fileNamePrefix + ".html";
	}

}
