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
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.GeographicMapMaker;
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
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

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
		GeographicMapMaker mapMaker = new RupSetMapMaker(rupSet, meta.region);
		mapMaker.setWriteGeoJSON(true);
		mapMaker.setFillSurfaces(fillSurfaces);
		
		List<String> lines = new ArrayList<>();
		
		String rawPrefix = "slip_rates";
		
		double[] nonReduced = nonReduced(rupSet);
		double maxSlip = StatUtils.max(nonReduced);
		double[] origReduced = origReduced(rupSet);
		double[] solRates = null;
		
		SectSlipRates slipRates = rupSet.getModule(SectSlipRates.class);
		double[] targets = null;
		if (slipRates != null)
			targets = target(rupSet, slipRates);
		
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
				slipCPT = linearSlipCPT(maxSlip);
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
				if (slipRates != null) {
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
		if (slipRates != null) {
			double[] stdDevs = slipRates.getSlipRateStdDevs();
			for (int s=0; s<slipRates.size(); s++)
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
					relStdDevs[s] = stdDevs[s]/slipRates.getSlipRate(s);
				
				TableBuilder table = MarkdownUtils.tableBuilder();
				
				table.initNewLine();
				
				String relPrefix = rawPrefix+"_rel_std_dev";
				String misfitPrefix = rawPrefix+"_std_dev_misfit";
				
				mapMaker.plotSectScalars(relStdDevs, relStdDevCPT, "Relative Standard Deviations (COV)"); 
				mapMaker.plot(resourcesDir, relPrefix, " ");
				table.addColumn("![Map]("+relPathToResources+"/"+relPrefix+".png)");
				
				if (sol != null) {
					// plot misfit z-scores
					double[] solSlips = solution(sol); // these are in mm/yr
					double[] misfits = new double[stdDevs.length];
					
					for (int s=0; s<misfits.length; s++) {
						double target = slipRates.getSlipRate(s);
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
						+" "+"[Download GeoJSON]("+relPathToResources+"/"+relPrefix+".geojson)");
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+misfitPrefix+".geojson")
						+" "+"[Download GeoJSON]("+relPathToResources+"/"+misfitPrefix+".geojson)");
				table.finalizeLine();
				
				if (meta.hasComparisonSol() && meta.comparisonHasSameSects
						&& meta.comparison.rupSet.hasModule(SectSlipRates.class)
						&& meta.comparison.rupSet.hasModule(SlipAlongRuptureModel.class)
						&& meta.comparison.rupSet.hasModule(AveSlipModule.class)) {
					relPrefix += "_comp";
					misfitPrefix += "_comp";
					
					SectSlipRates compSlips = meta.comparison.rupSet.requireModule(SectSlipRates.class);
					stdDevs = compSlips.getSlipRateStdDevs();
					
					relStdDevs = new double[stdDevs.length];
					for (int s=0; s<stdDevs.length; s++)
						relStdDevs[s] = stdDevs[s]/compSlips.getSlipRate(s);
					
					table.initNewLine();
					mapMaker.plotSectScalars(relStdDevs, relStdDevCPT, "Comparison Relative Standard Deviations"); 
					mapMaker.plot(resourcesDir, relPrefix, " ");
					table.addColumn("![Map]("+relPathToResources+"/"+relPrefix+".png)");
					
					// plot misfit z-scores
					double[] solSlips = solution(meta.comparison.sol); // these are in mm/yr
					double[] misfits = new double[stdDevs.length];
					
					for (int s=0; s<misfits.length; s++) {
						double target = compSlips.getSlipRate(s);
						double solSlip = solSlips[s]*1e-3;
						double stdDev = stdDevs[s];
						
						misfits[s] = (solSlip - target)/stdDev;
					}
					
					mapMaker.plotSectScalars(misfits, misfitCPT, "Comparison Solution Misfit (Standard Deviations)"); 
					mapMaker.plot(resourcesDir, misfitPrefix, " ");
					table.addColumn("![Map]("+relPathToResources+"/"+misfitPrefix+".png)");
					
					table.finalizeLine().initNewLine();
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+relPrefix+".geojson")
							+" "+"[Download GeoJSON]("+relPathToResources+"/"+relPrefix+".geojson)");
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+misfitPrefix+".geojson")
							+" "+"[Download GeoJSON]("+relPathToResources+"/"+misfitPrefix+".geojson)");
					table.finalizeLine();
				}
				
				lines.add("");
				lines.add(getSubHeading()+" Slip Rate Std Dev Plots");
				lines.add(topLink); lines.add("");
				lines.addAll(table.build());
			}
		}
		
		boolean hasAseis = false;
		boolean hasCoupling = false;
		boolean hasSubSeis = false;
		boolean hasCreep = false;
		SectSlipRates slips = rupSet.getModule(SectSlipRates.class);
		for (int s=0; s<rupSet.getNumSections(); s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			hasAseis = hasAseis || (float)sect.getAseismicSlipFactor() > 0f;
			hasCoupling = hasCoupling || (float)sect.getCouplingCoeff() < 1f;
			if (slips != null)
				hasSubSeis = hasSubSeis || (float)sect.getReducedAveSlipRate() > (float)slips.getSlipRate(s);
			hasCreep = hasCreep || (sect instanceof GeoJSONFaultSection &&
					((GeoJSONFaultSection)sect).getProperty(GeoJSONFaultSection.CREEP_RATE, Double.NaN) >= 0d);
		}
		
		if (hasAseis || hasCoupling || hasSubSeis) {
			lines.add("");
			lines.add(getSubHeading()+" Slip Rate & Area Reductions");
			lines.add(topLink); lines.add("");
			
//			TableBuilder table = MarkdownUtils.tableBuilder();
			
			List<String> plotHeadings = new ArrayList<>();
			List<String> plotPrefixes = new ArrayList<>();
			List<String> compPlotPrefixes = meta.comparisonHasSameSects ? new ArrayList<>() : null;
			
			if (hasCreep) {
				double[] creepRates = creep(rupSet);
				double max = 10d;
				for (double creepRate : creepRates)
					if (Double.isFinite(creepRate))
						max = Math.max(max, creepRate);
				
				CPT cpt = linearSlipCPT(max);
				
				String prefix = rawPrefix+"_creep";
				
				plotHeadings.add("Creep Rates");
				
				mapMaker.plotSectScalars(creepRates, cpt, "Creep Rate (mm/yr)");
				mapMaker.plot(resourcesDir, prefix, " ");
				plotPrefixes.add(prefix);
				
				if (meta.comparisonHasSameSects) {
					mapMaker.plotSectScalars(creep(meta.comparison.rupSet), cpt, "Creep Rate (mm/yr)");
					mapMaker.plot(resourcesDir, prefix+"_comp", " ");
					compPlotPrefixes.add(prefix+"_comp");
				}
			}
			
			if (hasAseis) {
				CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 1d);
				
				String prefix = rawPrefix+"_aseis";
				
				plotHeadings.add("Aseismic Slip Factors");
				
				mapMaker.plotSectScalars(aseis(rupSet), cpt, "Aseismic Slip Factor");
				mapMaker.plot(resourcesDir, prefix, " ");
				plotPrefixes.add(prefix);
				
				if (meta.comparisonHasSameSects) {
					mapMaker.plotSectScalars(aseis(meta.comparison.rupSet), cpt, "Aseismic Slip Factor");
					mapMaker.plot(resourcesDir, prefix+"_comp", " ");
					compPlotPrefixes.add(prefix+"_comp");
				}
			}
			
			if (hasCoupling) {
				CPT cpt = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().rescale(0d, 1d);
				
				String prefix = rawPrefix+"_coupling";
				
				plotHeadings.add("Coupling Coefficients");
				
				mapMaker.setReverseSort(true);
				mapMaker.plotSectScalars(coupling(rupSet), cpt, "Coupling Coefficient");
				mapMaker.plot(resourcesDir, prefix, " ");
				plotPrefixes.add(prefix);
				
				if (meta.comparisonHasSameSects) {
					mapMaker.plotSectScalars(coupling(meta.comparison.rupSet), cpt, "Coupling Coefficient");
					mapMaker.plot(resourcesDir, prefix+"_comp", " ");
					compPlotPrefixes.add(prefix+"_comp");
				}
				mapMaker.setReverseSort(false);
			}
			
			if (hasSubSeis) {
				CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 1d);
				
				String prefix = rawPrefix+"_sub_seis_red";
				
				plotHeadings.add("Subseismogenic Reduction Factors");
				
				mapMaker.plotSectScalars(subSeisReduction(rupSet, rupSet.getModule(SectSlipRates.class)),
						cpt, "Subseismogenic Reduction Factor");
				mapMaker.plot(resourcesDir, prefix, " ");
				plotPrefixes.add(prefix);
				
				if (meta.comparisonHasSameSects) {
					mapMaker.plotSectScalars(subSeisReduction(meta.comparison.rupSet,
							meta.comparison.rupSet.getModule(SectSlipRates.class)), cpt, "Subseismogenic Reduction Factor");
					mapMaker.plot(resourcesDir, prefix+"_comp", " ");
					compPlotPrefixes.add(prefix+"_comp");
				}
			}
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			
			if (meta.comparisonHasSameSects) {
				// one line for each type
				for (int i=0; i<plotHeadings.size(); i++) {
					table.initNewLine();
					
					String heading = plotHeadings.get(i);
					table.addColumn(MarkdownUtils.boldCentered("Primary "+heading));
					table.addColumn(MarkdownUtils.boldCentered("Comparison "+heading));
					
					table.finalizeLine().initNewLine();
					
					String prefix = plotPrefixes.get(i);
					String compPrefix = compPlotPrefixes.get(i);
					table.addColumn("![Map]("+relPathToResources+"/"+prefix+".png)");
					table.addColumn("![Map]("+relPathToResources+"/"+compPrefix+".png)");
					
					table.finalizeLine().initNewLine();
					
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+".geojson")
							+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+".geojson)");
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+compPrefix+".geojson")
							+" "+"[Download GeoJSON]("+relPathToResources+"/"+compPrefix+".geojson)");
					
					table.finalizeLine();
				}
			} else {
				// bundle them
				table.initNewLine();
				for (String heading : plotHeadings)
					table.addColumn(MarkdownUtils.boldCentered(heading));
				table.finalizeLine().initNewLine();
				for (String prefix : plotPrefixes)
					table.addColumn("![Map]("+relPathToResources+"/"+prefix+".png)");
				table.finalizeLine().initNewLine();
				for (String prefix : plotPrefixes)
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+".geojson")
							+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+".geojson)");
				table.finalizeLine();
				table = table.wrap(2, 0);
			}
			
			lines.addAll(table.build());
		}
		
		// write CSV file
		List<String> header = new ArrayList<>();
		
		header.add("Section Index");
		header.add("Section Name");
		header.add("Original (non-reduced) Slip Rate (mm/yr)");
		header.add("Creep-Reduced Slip Rate (mm/yr)");
		if (targets != null) {
			header.add("Target Slip Rate (mm/yr)");
			if (hasStdDev)
				header.add("Target Slip Rate Std. Dev. (mm/yr)");
		}
		if (sol != null) {
			header.add("Solution Slip Rate (mm/yr)");
			if (targets != null) {
				header.add("Solution - Target, Slip Rate Misfit (mm/yr)");
				header.add("Solution / Target, Slip Rate Ratio");
				if (hasStdDev)
					header.add("Solution Slip Rate Rate z-score (std. devs.)");
			}
		}
		
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine(header);
		
		for (int s=0; s<rupSet.getNumSections(); s++) {
			List<String> line = new ArrayList<>();
			
			line.add(s+"");
			line.add(rupSet.getFaultSectionData(s).getSectionName());
			line.add((float)nonReduced[s]+"");
			line.add((float)origReduced[s]+"");
			if (targets != null) {
				line.add((float)targets[s]+"");
				if (hasStdDev)
					line.add((float)(slipRates.getSlipRateStdDev(s)*1e3)+"");
			}
			if (sol != null) {
				line.add((float)solRates[s]+"");
				if (targets != null) {
					line.add((float)(solRates[s] - targets[s])+"");
					line.add((float)(solRates[s] / targets[s])+"");
					if (hasStdDev) {
						double sd = slipRates.getSlipRateStdDev(s)*1e3;
						double z = (solRates[s] - targets[s])/sd;
						line.add((float)z+"");
					}
				}
			}
			
			csv.addLine(line);
		}
		
		csv.writeToFile(new File(resourcesDir, rawPrefix+".csv"));
		
		List<String> csvLines = new ArrayList<>();
		csvLines.add("Download CSV file with slip rate data: ["+rawPrefix+".csv]("+relPathToResources+"/"+rawPrefix+".csv)");
		csvLines.add("");
		lines.addAll(0, csvLines);
		
		return lines;
	}

	public static CPT linearSlipCPT(double maxSlip) {
		CPT slipCPT;
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
		return slipCPT;
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
	
	private static double[] aseis(FaultSystemRupSet rupSet) {
		double[] aseis = new double[rupSet.getNumSections()];
		for (int s=0; s<aseis.length; s++)
			aseis[s] = rupSet.getFaultSectionData(s).getAseismicSlipFactor();
		return aseis;
	}
	
	private static double[] creep(FaultSystemRupSet rupSet) {
		double[] creep = new double[rupSet.getNumSections()];
		for (int s=0; s<creep.length; s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			if (sect instanceof GeoJSONFaultSection)
				creep[s] = ((GeoJSONFaultSection)sect).getProperty(GeoJSONFaultSection.CREEP_RATE, Double.NaN);
			else
				creep[s] = Double.NaN;
		}
		return creep;
	}
	
	private static double[] coupling(FaultSystemRupSet rupSet) {
		double[] coupling = new double[rupSet.getNumSections()];
		for (int s=0; s<coupling.length; s++)
			coupling[s] = rupSet.getFaultSectionData(s).getCouplingCoeff();
		return coupling;
	}
	
	private static double[] subSeisReduction(FaultSystemRupSet rupSet, SectSlipRates slipRates) {
		double[] subSeisRed = new double[rupSet.getNumSections()];
		if (slipRates == null)
			// no reductions
			return subSeisRed;
		for (int s=0; s<subSeisRed.length; s++) {
			// 'non' here means not further reduced by sub-seismogenic ruptures, both have coupling coef. applied
			double nonReduced = rupSet.getFaultSectionData(s).getReducedAveSlipRate();
			if (nonReduced > 0d) {
				double subSeisReduced = 1e3*slipRates.getSlipRate(s);
				subSeisRed[s] = (nonReduced - subSeisReduced)/nonReduced;
			}
		}
		return subSeisRed;
	}
	
	static double[] solution(FaultSystemSolution sol) {
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
