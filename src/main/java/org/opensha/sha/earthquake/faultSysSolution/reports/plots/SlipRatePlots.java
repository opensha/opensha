package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.SolidFillPlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;

public class SlipRatePlots extends AbstractRupSetPlot implements SolidFillPlot {

	boolean fillSurfaces = false;

	@Override
	public void setFillSurfaces(boolean fillSurfaces){
		this.fillSurfaces = fillSurfaces;
	}

	@Override
	public String getName() {
		return "Slip Rates";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		RupSetMapMaker mapMaker = new RupSetMapMaker(rupSet, meta.region);
		mapMaker.setWriteGeoJSON(true);
		mapMaker.setFillSurfaces(fillSurfaces);
		
		List<String> lines = new ArrayList<>();
		
		String rawPrefix = "slip_rates";
		
		double[] nonReduced = nonReduced(rupSet);
		double maxSlip = StatUtils.max(nonReduced);
		double[] origReduced = origReduced(rupSet);
		double[] solRates = null;
		
		for (boolean log : new boolean[] {false,true}) {
			CPT slipCPT;
			String prefix;
			String labelPrefix = "";
			if (log) {
				if (!lines.isEmpty())
					lines.add("");
				lines.add(getSubHeading()+" Log10 Slip Rate Plots");
				
				slipCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-3, 2);
				
				slipCPT.setBelowMinColor(slipCPT.getMinColor());
				slipCPT.setAboveMaxColor(slipCPT.getMaxColor());
				slipCPT.setNanColor(Color.GRAY);
				
				prefix = rawPrefix+"_log";
				labelPrefix = "Log10 ";
			} else {
				lines.add(getSubHeading()+" Linear Slip Rate Plots");
				slipCPT = new CPT();
				
				slipCPT.setBelowMinColor(Color.GRAY);
				slipCPT.setNanColor(Color.GRAY);
				
//				slipCPT.add(new CPTVal(0f, Color.GRAY, 0f, Color.GRAY));
				slipCPT.add(new CPTVal(Float.MIN_VALUE, Color.BLUE, 10f, Color.MAGENTA));
				slipCPT.add(new CPTVal(10f, Color.MAGENTA, 20f, Color.RED));
				slipCPT.add(new CPTVal(20f, Color.RED, 30f, Color.ORANGE));
				slipCPT.add(new CPTVal(30f, Color.ORANGE, 40f, Color.YELLOW));
				
				slipCPT.setAboveMaxColor(Color.YELLOW);
				if (maxSlip < 10d)
					slipCPT = slipCPT.rescale(0d, 10d);
				else if (maxSlip < 20d)
					slipCPT = slipCPT.rescale(0d, 20d);
				else if (maxSlip > 60d)
					slipCPT = slipCPT.rescale(0d, 60d);
				prefix = rawPrefix;
			}
			lines.add(topLink); lines.add("");
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			
			mapMaker.plotSectScalars(log ? log10(nonReduced) : nonReduced, slipCPT,
					labelPrefix+"Original (non-reduced) Slip Rate (mm/yr)");
			mapMaker.plot(resourcesDir, prefix+"_orig", " ");
			table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_orig.png)");
			mapMaker.plotSectScalars(log ? log10(origReduced) : origReduced, slipCPT,
					labelPrefix+"Creep Reduced Slip Rate (mm/yr)");
			mapMaker.plot(resourcesDir, prefix+"_reduced", " ");
			table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_reduced.png)");
			table.finalizeLine();
			table.initNewLine();
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_orig.geojson")
					+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_orig.geojson)");
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_reduced.geojson")
					+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_reduced.geojson)");
			table.finalizeLine();
			
			if (rupSet.hasModule(SectSlipRates.class) || sol != null) {
				table.initNewLine();
				SectSlipRates slipRates = rupSet.getModule(SectSlipRates.class);
				double[] targets = null;
				if (slipRates != null) {
					targets = target(rupSet, slipRates);
					mapMaker.plotSectScalars(log ? log10(targets) : targets, slipCPT, labelPrefix+"Target Slip Rate (mm/yr)");
					mapMaker.plot(resourcesDir, prefix+"_target", " ");
					table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_target.png)");
				} else {
					table.addColumn("_(no target slip rates found)_");
				}
				
				if (sol != null) {
					solRates = solution(sol);
					mapMaker.plotSectScalars(log ? log10(solRates) : solRates, slipCPT, labelPrefix+"Solution Slip Rate (mm/yr)");
					mapMaker.plot(resourcesDir, prefix+"_sol", " ");
					table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_sol.png)");
				} else {
					table.addColumn("");
				}
				table.finalizeLine();
				table.initNewLine();
				if (targets == null)
					table.addColumn("");
				else
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_target.geojson")
							+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_target.geojson)");
				if (sol == null)
					table.addColumn("");
				else
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_sol.geojson")
							+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_sol.geojson)");
				table.finalizeLine();
				
				if (targets != null && solRates != null) {
					CPT diffCPT;
					CPT ratioCPT;
					
					if (log) {
						diffCPT = null;
						ratioCPT = new CPT(-1d, 1d,
								new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
								Color.WHITE,
								new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
					} else {
						diffCPT = new CPT(-10, 10,
								new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
								Color.WHITE,
								new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
						diffCPT.setNanColor(Color.GRAY);
						diffCPT.setBelowMinColor(diffCPT.getMinColor());
						diffCPT.setAboveMaxColor(diffCPT.getMaxColor());
						
						CPT belowCPT = new CPT(0.5d, 1d,
								new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
								Color.WHITE);
						CPT aboveCPT = new CPT(1d, 2d,
								Color.WHITE,
								new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
						ratioCPT = new CPT();
						ratioCPT.addAll(belowCPT);
						ratioCPT.addAll(aboveCPT);
						ratioCPT.setNanColor(Color.GRAY);
						ratioCPT.setBelowMinColor(ratioCPT.getMinColor());
						ratioCPT.setAboveMaxColor(ratioCPT.getMaxColor());
					}
					
					table.initNewLine();
					
					double[] diffs = diff(solRates, targets);
					double[] ratios = ratio(solRates, targets);
					String diffLabel, ratioLabel;
					if (log) {
						diffLabel = null;
						ratioLabel = "Log10(Solution / Target Slip Rate)";
					} else {
						diffLabel = "Solution - Target Slip Rate (mm/yr)";
						ratioLabel = "Solution / Target Slip Rate";
					}
					
					if (!log) {
						mapMaker.plotSectScalars(diffs, diffCPT, diffLabel);
						mapMaker.plot(resourcesDir, prefix+"_sol_diff", " ");
						table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_sol_diff.png)");
					}
					mapMaker.plotSectScalars(log ? log10(ratios) : ratios, ratioCPT, ratioLabel);
					mapMaker.plot(resourcesDir, prefix+"_sol_ratio", " ");
					table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_sol_ratio.png)");
					
					HeadlessGraphPanel gp = PlotUtils.initHeadless();
					if (!log) {
						table.finalizeLine().initNewLine();
						table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_sol_diff.geojson")
								+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_sol_diff.geojson)");
						table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_sol_ratio.geojson")
								+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_sol_ratio.geojson)");
						table.finalizeLine().initNewLine();
						// now scatters/histograms
						
						HistogramFunction diffHist = HistogramFunction.getEncompassingHistogram(-10d, 10d, 1d);
						for (double diff : diffs)
							diffHist.add(diffHist.getClosestXIndex(diff), 1d);
						
						List<XY_DataSet> funcs = new ArrayList<>();
						List<PlotCurveCharacterstics> chars = new ArrayList<>();
						
						funcs.add(diffHist);
						chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
						
						gp.drawGraphPanel(new PlotSpec(funcs, chars, "Slip Rate Differences", "Solution - Target Slip Rate (mm/yr)", "Count"));
						
						PlotUtils.writePlots(resourcesDir, prefix+"_sol_diff_hist", gp, 800, 650, true, true, false);
						table.addColumn("![Diff hist]("+relPathToResources+"/"+prefix+"_sol_diff_hist.png)");
					}
					
					DefaultXY_DataSet scatter = new DefaultXY_DataSet(targets, solRates);
					Range scatterRange;
					if (log)
						scatterRange = new Range(1e-3, 1e2);
					else
						scatterRange = new Range(0d, Math.max(scatter.getMaxX(), scatter.getMaxY()));
					
					List<XY_DataSet> funcs = new ArrayList<>();
					List<PlotCurveCharacterstics> chars = new ArrayList<>();
					
					funcs.add(scatter);
					chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK));
					
					DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
					oneToOne.set(scatterRange.getLowerBound(), scatterRange.getLowerBound());
					oneToOne.set(scatterRange.getUpperBound(), scatterRange.getUpperBound());
					funcs.add(oneToOne);
					chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GRAY));

					gp.drawGraphPanel(new PlotSpec(funcs, chars,
							"Slip Rates Scatter", "Target Slip Rate (mm/yr)", "Solution Slip Rate (mm/yr)"),
							log, log, scatterRange, scatterRange);
					
					PlotUtils.writePlots(resourcesDir, prefix+"_sol_scatter", gp, 800, 650, true, true, false);
					table.addColumn("![Diff hist]("+relPathToResources+"/"+prefix+"_sol_scatter.png)");
					table.finalizeLine();
					
					if (log) {
						table.initNewLine();
						table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_sol_ratio.geojson")
								+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_sol_ratio.geojson)");
						table.addColumn("");
						table.finalizeLine();
					}
				}
			}
			lines.addAll(table.build());
		}
		
		// see if we have std devs
		boolean hasStdDev = false;
		if (rupSet.hasModule(SectSlipRates.class)) {
			SectSlipRates slips = rupSet.requireModule(SectSlipRates.class);
			double[] stdDevs = slips.getSlipRateStdDevs();
			for (int s=0; s<slips.size(); s++)
				if (stdDevs[s] > 0d)
					hasStdDev = true;
			if (hasStdDev) {
				CPT relStdDevCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 1d);
				relStdDevCPT.setNanColor(Color.GRAY);
				
				CPT misfitCPT = new CPT(-3d, 3d,
						new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
						Color.WHITE,
						new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
				misfitCPT.setNanColor(Color.GRAY);
				
				double[] relStdDevs = new double[stdDevs.length];
				for (int s=0; s<stdDevs.length; s++)
					relStdDevs[s] = stdDevs[s]/slips.getSlipRate(s);
				
				TableBuilder table = MarkdownUtils.tableBuilder();
				
				table.initNewLine();
				
				String relPrefix = rawPrefix+"_rel_std_dev";
				String misfitPrefix = rawPrefix+"_std_dev_misfit";
				
				mapMaker.plotSectScalars(relStdDevs, relStdDevCPT, "Relative Standard Deviations"); 
				mapMaker.plot(resourcesDir, relPrefix, " ");
				table.addColumn("![Map]("+relPathToResources+"/"+relPrefix+".png)");
				
				if (sol != null) {
					// plot misfit z-scores
					double[] solSlips = solution(sol); // these are in mm/yr
					double[] misfits = new double[stdDevs.length];
					
					for (int s=0; s<misfits.length; s++) {
						double target = slips.getSlipRate(s);
						double solSlip = solSlips[s]*1e-3; // to m/yr
						double stdDev = stdDevs[s];
						
						misfits[s] = (solSlip - target)/stdDev;
					}
					
					mapMaker.plotSectScalars(misfits, misfitCPT, "Solution Misfit (Standard Deviations)"); 
					mapMaker.plot(resourcesDir, misfitPrefix, " ");
					table.addColumn("![Map]("+relPathToResources+"/"+misfitPrefix+".png)");
				}
				
				table.finalizeLine().initNewLine();
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+relPrefix+".geojson")
						+" "+"[Download GeoJSON]("+relPathToResources+"/"+relPrefix+"_sol_diff.geojson)");
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+misfitPrefix+".geojson")
						+" "+"[Download GeoJSON]("+relPathToResources+"/"+misfitPrefix+"_sol_ratio.geojson)");
				table.finalizeLine();
				
				if (meta.hasComparisonSol() && meta.comparison.rupSet.hasModule(SectSlipRates.class)
						&& meta.comparison.rupSet.hasModule(SlipAlongRuptureModel.class)
						&& meta.comparison.rupSet.hasModule(AveSlipModule.class)) {
					relPrefix += "_comp";
					misfitPrefix += "_comp";
					
					slips = meta.comparison.rupSet.requireModule(SectSlipRates.class);
					stdDevs = slips.getSlipRateStdDevs();
					
					relStdDevs = new double[stdDevs.length];
					for (int s=0; s<stdDevs.length; s++)
						relStdDevs[s] = stdDevs[s]/slips.getSlipRate(s);
					
					table.initNewLine();
					mapMaker.plotSectScalars(relStdDevs, relStdDevCPT, "Comparison Relative Standard Deviations"); 
					mapMaker.plot(resourcesDir, relPrefix, " ");
					table.addColumn("![Map]("+relPathToResources+"/"+relPrefix+".png)");
					
					// plot misfit z-scores
					double[] solSlips = solution(meta.comparison.sol); // these are in mm/yr
					double[] misfits = new double[stdDevs.length];
					
					for (int s=0; s<misfits.length; s++) {
						double target = slips.getSlipRate(s);
						double solSlip = solSlips[s]*1e-3;
						double stdDev = stdDevs[s];
						
						misfits[s] = (solSlip - target)/stdDev;
					}
					
					mapMaker.plotSectScalars(misfits, misfitCPT, "Comparison Solution Misfit (Standard Deviations)"); 
					mapMaker.plot(resourcesDir, misfitPrefix, " ");
					table.addColumn("![Map]("+relPathToResources+"/"+misfitPrefix+".png)");
					
					table.finalizeLine().initNewLine();
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+relPrefix+".geojson")
							+" "+"[Download GeoJSON]("+relPathToResources+"/"+relPrefix+"_sol_diff.geojson)");
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+misfitPrefix+".geojson")
							+" "+"[Download GeoJSON]("+relPathToResources+"/"+misfitPrefix+"_sol_ratio.geojson)");
					table.finalizeLine();
				}
				
				lines.add("");
				lines.add(getSubHeading()+" Slip Rate Std Dev Plots");
				lines.add(topLink); lines.add("");
				lines.addAll(table.build());
			}
//			for (Fault)
		}
		
		return lines;
	}
	
	private static double[] nonReduced(FaultSystemRupSet rupSet) {
		double[] slips = new double[rupSet.getNumSections()];
		for (int s=0; s<slips.length; s++)
			slips[s] = rupSet.getFaultSectionData(s).getOrigAveSlipRate();
		return slips;
	}
	
	private static double[] origReduced(FaultSystemRupSet rupSet) {
		double[] slips = new double[rupSet.getNumSections()];
		for (int s=0; s<slips.length; s++)
			slips[s] = rupSet.getFaultSectionData(s).getReducedAveSlipRate();
		return slips;
	}
	
	private static double[] target(FaultSystemRupSet rupSet, SectSlipRates slipRates) {
		double[] slips = new double[rupSet.getNumSections()];
		for (int s=0; s<slips.length; s++)
			slips[s] = slipRates.getSlipRate(s)*1e3;
		return slips;
	}
	
	private static double[] solution(FaultSystemSolution sol) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		AveSlipModule aveSlips = rupSet.requireModule(AveSlipModule.class);
		SlipAlongRuptureModel slipAlongs = rupSet.requireModule(SlipAlongRuptureModel.class);
		double[] slips = slipAlongs.calcSlipRateForSects(sol, aveSlips);
		slips = Arrays.copyOf(slips, slips.length);
		for (int s=0; s<slips.length; s++)
			slips[s] *= 1e3; // to mm/yr
		return slips;
	}
	
	private static double[] diff(double[] v1, double[] v2) {
		double[] diff = new double[v1.length];
		for (int i=0; i<diff.length; i++)
			diff[i] = v1[i] - v2[i];
		return diff;
	}
	
	private static double[] ratio(double[] v1, double[] v2) {
		double[] ratio = new double[v1.length];
		for (int i=0; i<ratio.length; i++)
			ratio[i] = v1[i] / v2[i];
		return ratio;
	}
	
	private static double[] log10(double[] vals) {
		double[] ret = new double[vals.length];
		for (int i=0; i<ret.length; i++)
			ret[i] = Math.log10(vals[i]);
		return ret;
	}
	
	private static double[] abs(double[] vals) {
		double[] ret = new double[vals.length];
		for (int i=0; i<ret.length; i++)
			ret[i] = Math.abs(vals[i]);
		return ret;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(SlipAlongRuptureModel.class, AveSlipModule.class);
	}

}
