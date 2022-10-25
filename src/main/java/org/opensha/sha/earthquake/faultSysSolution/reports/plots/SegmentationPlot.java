package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.RateCombiner;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SegmentationCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SegmentationCalculator.Scalars;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModelBranchNode;

import com.google.common.base.Preconditions;

public class SegmentationPlot extends AbstractSolutionPlot {
	
	private Scalars[] scalars;
	private double[] minMags;
	
	public static Scalars[] noCoulombScalars() {
		List<Scalars> newScalars = new ArrayList<>();
		for (Scalars scalar : Scalars.values())
			if (!scalar.name().contains("CFF"))
				newScalars.add(scalar);
		return newScalars.toArray(new Scalars[0]);
	}
	
	public SegmentationPlot() {
		this(noCoulombScalars(), null);
	}
	
	public SegmentationPlot(Scalars[] scalars, double[] minMags) {
		if (scalars == null)
			scalars = new Scalars[0];
		this.scalars = scalars;
		this.minMags = minMags;
	}

	@Override
	public String getName() {
		return "Fault Segmentation";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		double minMag = sol.getRupSet().getMinMag();
		double[] minMags = this.minMags;
		if (minMags == null) {
			if (minMag >= 7.5)
				minMags = new double[] { 7.5 };
			else if (minMag >= 7)
				minMags = new double[] { 7d, 7.5 };
			else if (minMag >= 6.5)
//				minMags = new double[] { 6.5, 7d, 7.5 };
				minMags = new double[] { 6.5, 7d };
			else
//				minMags = new double[] { 0d, 6.5, 7d, 7.5 };
				minMags = new double[] { 0d, 7d };
		}
		
		lines.add("The following plots show implied segmentation from the rates of this fault system solution.");
		lines.add("");
		
		List<ClusterRupture> inputRups = sol.getRupSet().requireModule(ClusterRuptures.class).getAll();
		PlausibilityConfiguration inputConfig = sol.getRupSet().requireModule(PlausibilityConfiguration.class);
		SectionDistanceAzimuthCalculator distAzCalc = inputConfig.getDistAzCalc();
		
		SegmentationCalculator inputSegCalc = new SegmentationCalculator(
				sol, inputRups, inputConfig.getConnectionStrategy(), distAzCalc, minMags);
		SegmentationCalculator compSegCalc = null;
		if (meta.comparison != null && meta.comparison.sol != null && meta.comparisonHasSameSects
				&& meta.comparison.rupSet.hasAllModules(getRequiredModules())) {
			List<ClusterRupture> compRups = meta.comparison.rupSet.requireModule(ClusterRuptures.class).getAll();
			PlausibilityConfiguration compConfig = meta.comparison.rupSet.requireModule(PlausibilityConfiguration.class);
			compSegCalc = new SegmentationCalculator(meta.comparison.sol, compRups, compConfig.getConnectionStrategy(), distAzCalc, minMags);
		}
		String compName = meta.comparison == null ? null : meta.comparison.name;
		if (inputSegCalc.areMultipleJumpsPerParent() || compSegCalc != null && compSegCalc.areMultipleJumpsPerParent()) {
			String names = null;
			if (inputSegCalc.areMultipleJumpsPerParent() &&
					// only combine jumps if this was an externally generated rupture set
					(inputConfig.getFilters() == null || inputConfig.getFilters().isEmpty())) {
				names = meta.primary.name;
				inputSegCalc = inputSegCalc.combineMultiJumps(true);
			}
			if (compSegCalc != null && compSegCalc.areMultipleJumpsPerParent()) {
				PlausibilityConfiguration compConfig = meta.comparison.rupSet.requireModule(PlausibilityConfiguration.class);
				if (compConfig.getFilters() == null || compConfig.getFilters().isEmpty()) {
					// only combine jumps if this was an externally generated rupture set
					if (names == null)
						names = compName;
					else
						names = "both "+names+" and "+compName;
					compSegCalc = compSegCalc.combineMultiJumps(true);
				}
			}
			if (names != null) {
				lines.add("NOTE: "+names+" has multiple jumping points between parent sections. We consolidate "
						+ "all jumps to occur at a single jumping point (with the highest jumping rate) and average "
						+ "quantities on either side of the jump (participation/slip rates)");
				lines.add("");
			}
		}
		
//		RateCombiner[] combiners = RateCombiner.values();
		RateCombiner[] combiners = { RateCombiner.MIN };
		
		File[] inputConnRates = inputSegCalc.plotConnectionRates(resourcesDir, "conn_rates", meta.primary.name);
		Map<RateCombiner, File[]> inputPassthroughRates = new HashMap<>();
		for (RateCombiner combiner : combiners)
			inputPassthroughRates.put(combiner, inputSegCalc.plotConnectionFracts(resourcesDir,
					"conn_passthrough_"+combiner.name(), "Connection Passthrough Rates, Relative to "+combiner, combiner));
		
		Map<RateCombiner, File[]> shawComps = new HashMap<>();
		Map<RateCombiner, File[]> shawLogComps = new HashMap<>();
		File[] shawCompComps = null, shawLogCompComps = null;
		for (RateCombiner combiner : combiners) {
			shawComps.put(combiner, inputSegCalc.plotDistDependComparison(resourcesDir, "conn_passthrough_dist_depend", false, combiner));
			shawLogComps.put(combiner, inputSegCalc.plotDistDependComparison(resourcesDir, "conn_passthrough_dist_depend_log", true, combiner));
			if (combiners.length == 1 && compSegCalc != null) {
				shawCompComps = compSegCalc.plotDistDependComparison(resourcesDir, "conn_comp_passthrough_dist_depend", false, combiners[0]);
				shawLogCompComps = compSegCalc.plotDistDependComparison(resourcesDir, "conn_comp_passthrough_dist_depend_log", true, combiners[0]);
			}
		}		
		
		Map<Scalars, File[]> inputScalarPassthroughs = new HashMap<>();
		Map<Scalars, File[]> inputScalarLogPassthroughs = new HashMap<>();
		for (Scalars scalar : scalars) {
			inputScalarPassthroughs.put(scalar, inputSegCalc.plotFractVsScalars(resourcesDir,
					"conn_passthrough_"+scalar.name(), scalar, false, combiners));
			inputScalarLogPassthroughs.put(scalar, inputSegCalc.plotFractVsScalars(resourcesDir,
					"conn_passthrough_"+scalar.name()+"_log", scalar, true, combiners));
		}
		
		File[] compConnRates = null;
		Map<RateCombiner, File[]> compPassthroughRates = null;
		Map<RateCombiner, File[]> compPassthroughRatios = null;
		Map<RateCombiner, File[]> compPassthroughDiffs = null;
		Map<Scalars, File[]> compScalarPassthroughs = null;
		Map<Scalars, File[]> compScalarLogPassthroughs = null;
		if (compSegCalc != null) {
			compConnRates = compSegCalc.plotConnectionRates(resourcesDir, "comp_conn_rates", compName);
			compPassthroughRates = new HashMap<>();
			compPassthroughDiffs = new HashMap<>();
			compPassthroughRatios = new HashMap<>();
			for (RateCombiner combiner : combiners) {
				compPassthroughRates.put(combiner, compSegCalc.plotConnectionFracts(resourcesDir,
						"comp_conn_passthrough_"+combiner.name(), "Connection Passthrough Rates, Relative to "+combiner, combiner));
				compPassthroughDiffs.put(combiner, inputSegCalc.plotConnectionDiffs(resourcesDir,
						"conn_passthrough_diff_"+combiner.name(), "Connection Passthrough Differences", combiner, compSegCalc));
				compPassthroughRatios.put(combiner, inputSegCalc.plotConnectionLogRatios(resourcesDir,
						"conn_passthrough_ratio_"+combiner.name(), "Connection Passthrough Ratios", combiner, compSegCalc));
			}
			compScalarPassthroughs = new HashMap<>();
			compScalarLogPassthroughs = new HashMap<>();
			for (Scalars scalar : scalars) {
				compScalarPassthroughs.put(scalar, compSegCalc.plotFractVsScalars(resourcesDir,
						"comp_conn_passthrough_"+scalar.name(), scalar, false, combiners));
				compScalarLogPassthroughs.put(scalar, compSegCalc.plotFractVsScalars(resourcesDir,
						"comp_conn_passthrough_"+scalar.name()+"_log", scalar, true, combiners));
			}
		}
		
		JumpProbabilityCalc segModel = getBranchSegModel(sol.getRupSet());
		String segModelName = segModel == null ? null : getBranchSegModelChoice(sol.getRupSet()).getName();
		JumpProbabilityCalc compSegModel = compSegCalc != null ? getBranchSegModel(meta.comparison.rupSet) : null;
		String compSegModelName = compSegModel == null ? null : getBranchSegModelChoice(meta.comparison.rupSet).getName();
		Map<RateCombiner, File[]> modelCompPassthroughs = null;
		Map<RateCombiner, File[]> modelCompLogPassthroughs = null;
		Map<RateCombiner, File[]> compModelCompPassthroughs = null;
		Map<RateCombiner, File[]> compModelCompLogPassthroughs = null;
		if (segModel != null) {
			modelCompPassthroughs = new HashMap<>();
			modelCompLogPassthroughs = new HashMap<>();
			if (compSegModel != null) {
				compModelCompPassthroughs = new HashMap<>();
				compModelCompLogPassthroughs = new HashMap<>();
			}
			
			for (RateCombiner combiner : combiners) {
				modelCompPassthroughs.put(combiner, inputSegCalc.plotConnectionModelDiffs(resourcesDir,
						"conn_passthrough_model_diff_"+combiner.name(), "Solution - "+segModelName, combiner, segModel));
				modelCompLogPassthroughs.put(combiner, inputSegCalc.plotConnectionModelLogRatios(resourcesDir,
						"conn_passthrough_model_ratio_"+combiner.name(), "Solution / "+segModelName, combiner, segModel));
				if (compSegModel != null) {
					compModelCompPassthroughs.put(combiner, compSegCalc.plotConnectionModelDiffs(resourcesDir,
							"comp_conn_passthrough_model_diff_"+combiner.name(), "Comparison Solution - "+compSegModelName, combiner, compSegModel));
					compModelCompLogPassthroughs.put(combiner, compSegCalc.plotConnectionModelLogRatios(resourcesDir,
							"comp_conn_passthrough_model_ratio_"+combiner.name(), "Comparison Solution / "+compSegModelName, combiner, compSegModel));
				}
			}
		}
		
		for (int m=0; m<minMags.length; m++) {
			if (minMags.length > 1) {
				if (minMags[m] > 0)
					lines.add(getSubHeading()+" M&ge;"+(float)minMags[m]+" Fault Segmentation");
				else
					lines.add(getSubHeading()+" Supra-Seismogenic Fault Segmentation");
				lines.add(topLink); lines.add("");
			}
			
			lines.add("**Connection Rates**");
			lines.add("");
			lines.add("This shows the rate at which each connection is taken.");
			lines.add("");
			
			if (compConnRates == null) {
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.addLine("![Rates]("+relPathToResources+"/"+inputConnRates[m].getName()+")");
				String relGeoPath = relPathToResources+"/"+inputConnRates[m].getName().replace(".png", ".geojson");
				table.addLine(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relGeoPath)
						+" "+"[Download GeoJSON]("+relGeoPath+")");
				lines.addAll(table.build());
				lines.add("");
			} else {
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.addLine(meta.primary.name, compName);
				table.initNewLine();
				table.addColumn("![Rates]("+relPathToResources+"/"+inputConnRates[m].getName()+")");
				table.addColumn("![Rates]("+relPathToResources+"/"+compConnRates[m].getName()+")");
				table.finalizeLine();
				table.initNewLine();
				String relGeoPath = relPathToResources+"/"+inputConnRates[m].getName().replace(".png", ".geojson");
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relGeoPath)
						+" "+"[Download GeoJSON]("+relGeoPath+")");
				relGeoPath = relPathToResources+"/"+compConnRates[m].getName().replace(".png", ".geojson");
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relGeoPath)
						+" "+"[Download GeoJSON]("+relGeoPath+")");
				table.finalizeLine();
				lines.addAll(table.build());
			}
			lines.add("");
			
			lines.add("**Connection Passthrough Rates**");
			lines.add(""); lines.add(topLink); lines.add("");
			lines.add("Passthrough rates refer to the ratio of the jumping rate to the rates on either side of the jump. "
					+ "The denominator of that ratio can be either the minimum, maximum, or average of the subsection "
					+ "rates on either side of the jump. Each choice of denomiator is plotted separately.");
			lines.add("");
			if (segModel != null) {
				String line = "Results are also plotted relative to the chosen segmentaion model, "+segModel.getName()+".";
				if (compSegModel != null)
					line += " The comparison model is shown relative to its own segmentation model, "+compSegModel.getName()+".";
				lines.add(line);
				lines.add("");
			}
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			if (compSegCalc != null)
				table.addLine(meta.primary.name, compName);
			for (RateCombiner combiner : combiners) {
				table.initNewLine();
				table.addColumn("![Rates]("+relPathToResources+"/"+inputPassthroughRates.get(combiner)[m].getName()+")");
				if (compSegCalc == null) {
					table.finalizeLine();
					table.initNewLine();
					String relGeoPath = relPathToResources+"/"+inputPassthroughRates.get(combiner)[m].getName().replace(".png", ".geojson");
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relGeoPath)
							+" "+"[Download GeoJSON]("+relGeoPath+")");
					table.finalizeLine();
				} else {
					table.addColumn("![Rates]("+relPathToResources+"/"+compPassthroughRates.get(combiner)[m].getName()+")");
					table.finalizeLine();
					table.initNewLine();
					String relGeoPath = relPathToResources+"/"+inputPassthroughRates.get(combiner)[m].getName().replace(".png", ".geojson");
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relGeoPath)
							+" "+"[Download GeoJSON]("+relGeoPath+")");
					relGeoPath = relPathToResources+"/"+compPassthroughRates.get(combiner)[m].getName().replace(".png", ".geojson");
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relGeoPath)
							+" "+"[Download GeoJSON]("+relGeoPath+")");
					table.finalizeLine();
					table.initNewLine();
					table.addColumn("![Rates]("+relPathToResources+"/"+compPassthroughRatios.get(combiner)[m].getName()+")");
					table.addColumn("![Rates]("+relPathToResources+"/"+compPassthroughDiffs.get(combiner)[m].getName()+")");
					table.finalizeLine();
				}
				if (segModel != null) {
					
					File[] diffs = modelCompPassthroughs.get(combiner);
					File[] ratios = modelCompLogPassthroughs.get(combiner);
					
					File[] compDiffs = compSegModel == null ? null : compModelCompPassthroughs.get(combiner);
					File[] compRatios = compSegModel == null ? null : compModelCompLogPassthroughs.get(combiner);

					for (boolean ratio : new boolean[] {false, true}) {
						File[] primary = ratio ? ratios : diffs;
						File[] comp = ratio ? compRatios : compDiffs;
						
						table.initNewLine();
						table.addColumn("![Comparison]("+relPathToResources+"/"+primary[m].getName()+")");
						if (compSegCalc != null) {
							if (comp == null)
								table.addColumn("_(N/A)_");
							else
								table.addColumn("![Comparison]("+relPathToResources+"/"+comp[m].getName()+")");
						}
						table.finalizeLine();
						table.initNewLine();
						String relGeoPath = relPathToResources+"/"+primary[m].getName().replace(".png", ".geojson");
						table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relGeoPath)
								+" "+"[Download GeoJSON]("+relGeoPath+")");
						if (compSegCalc != null) {
							if (comp == null) {
								table.addColumn("");
							} else {
								relGeoPath = relPathToResources+"/"+comp[m].getName().replace(".png", ".geojson");
								table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relGeoPath)
										+" "+"[Download GeoJSON]("+relGeoPath+")");
							}
						}
						table.finalizeLine();
					}
				}
			}
			lines.addAll(table.build());
			lines.add("");
			
			lines.add("**Connection Passthrough Rates vs Distance**");
			lines.add(""); lines.add(topLink); lines.add("");
			lines.add("This plots passthrough rates versus distance, either comparing with the relationship established in "
					+ "Shaw and Dieterich (2007), or distance-dependent segmentation models for this model's logic tree.");
			lines.add("");
			
			table = MarkdownUtils.tableBuilder();
			table.addLine("Linear", "Log10");
			for (RateCombiner combiner : combiners) {
				table.initNewLine();
				table.addColumn("![Rates]("+relPathToResources+"/"+shawComps.get(combiner)[m].getName()+")");
				table.addColumn("![Rates]("+relPathToResources+"/"+shawLogComps.get(combiner)[m].getName()+")");
				table.finalizeLine();
			}
			if (combiners.length == 1 && compSegCalc != null) {
				// do comparison
				table.addLine(MarkdownUtils.boldCentered("Comparison Linear"), MarkdownUtils.boldCentered("Comparison Log10"));
				table.initNewLine();
				table.addColumn("![Rates]("+relPathToResources+"/"+shawCompComps[m].getName()+")");
				table.addColumn("![Rates]("+relPathToResources+"/"+shawLogCompComps[m].getName()+")");
				table.finalizeLine();
			}
			lines.addAll(table.build());
			lines.add("");
			
			if (combiners.length == 1) {
				File csvFile = shawComps.get(combiners[0])[m];
				csvFile = new File(csvFile.getParentFile(), csvFile.getName().replaceAll(".png", ".csv"));
				Preconditions.checkState(csvFile.exists());
				
				if (compSegCalc == null) {
					lines.add("Download CSV file: ["+csvFile.getName()+"]("+relPathToResources+"/"+csvFile.getName()+")");
				} else {
					File csvCompFile = shawCompComps[m];
					csvCompFile = new File(csvCompFile.getParentFile(), csvCompFile.getName().replaceAll(".png", ".csv"));
					Preconditions.checkState(csvCompFile.exists());
					lines.add("Download CSV files: ["+meta.primary.name+"]("+relPathToResources+"/"+csvFile.getName()
						+") ["+meta.comparison.name+"]("+relPathToResources+"/"+csvCompFile.getName()+")");
				}
				lines.add("");
			}
			
			if (scalars.length > 0) {
				lines.add("**Connection Passthrough Rates vs Scalars**");
				lines.add(""); lines.add(topLink); lines.add("");
				lines.add("This plots passthrough rates versus various scalar values (for each rate combiniation type).");
				lines.add("");
				
				for (Scalars scalar : scalars) {
					table = MarkdownUtils.tableBuilder();
					table.initNewLine();
					if (compSegCalc != null)
						table.addColumn("");
					// escape absolute values
					String scalarName = scalar.toString().replace("|", "\\|");
					table.addColumn(scalarName).addColumn(scalarName+" (Log10 Rates)");
					table.finalizeLine();
					table.initNewLine();
					if (compSegCalc != null)
						table.addColumn("**"+meta.primary.name+"**");
					table.addColumn("![Rates]("+relPathToResources+"/"+inputScalarPassthroughs.get(scalar)[m].getName()+")");
					table.addColumn("![Rates]("+relPathToResources+"/"+inputScalarLogPassthroughs.get(scalar)[m].getName()+")");
					table.finalizeLine();
					if (compSegCalc != null) {
						table.initNewLine();
						table.addColumn("**"+compName+"**");
						table.addColumn("![Rates]("+relPathToResources+"/"+compScalarPassthroughs.get(scalar)[m].getName()+")");
						table.addColumn("![Rates]("+relPathToResources+"/"+compScalarLogPassthroughs.get(scalar)[m].getName()+")");
						table.finalizeLine();
					}
					lines.addAll(table.build());
					lines.add("");
				}
			}
			
//			lines.add("**Connection Passthrough Rates vs Log10 Scalars**");
//			lines.add(""); lines.add(topLink); lines.add("");
//			lines.add("This plots passthrough rates versus various Log10 scalar values (for each rate combiniation type).");
//			lines.add("");
//			
//			table = MarkdownUtils.tableBuilder();
//			if (compSegCalc != null)
//				table.addLine(meta.primary.name, compName);
//			for (Scalars scalar : scalars) {
//				table.initNewLine();
//				table.addColumn("![Rates]("+relPathToResources+"/"+inputScalarLogPassthroughs.get(scalar)[m].getName()+")");
//				if (compSegCalc != null) {
//					table.addColumn("![Rates]("+relPathToResources+"/"+compScalarLogPassthroughs.get(scalar)[m].getName()+")");
//				}
//				table.finalizeLine();
//			}
//			lines.addAll(table.build());
//			lines.add("");
			
			if (combiners.length > 1) {
				lines.add("**Connection Passthrough Rates for Different Rate Combiners**");
				lines.add(""); lines.add(topLink); lines.add("");
				lines.add("This comapres "+meta.primary.name+ " passthrough rates for each rate combiniation type. "
						+ "Linear on the left, log10 on the right.");
				lines.add("");
				
				table = MarkdownUtils.tableBuilder();
				table.addLine("Linear Passthrough Rates", "Log10 Passthrough Rates");
				for (int c1=0; c1<combiners.length; c1++) {
					for (int c2=c1+1; c2<combiners.length; c2++) {
						table.initNewLine();
						String prefix = "conn_rates_"+combiners[c1].name()+"_vs_"+combiners[c2].name();
						File linearPlot = inputSegCalc.plotCombinerScatter(resourcesDir, prefix,
								false, m, combiners[c1], combiners[c2]);
						File logPlot = inputSegCalc.plotCombinerScatter(resourcesDir, prefix+"_log",
								true, m, combiners[c1], combiners[c2]);
						table.addColumn("![Scatter]("+relPathToResources+"/"+linearPlot.getName()+")");
						table.addColumn("![Scatter]("+relPathToResources+"/"+logPlot.getName()+")");
						table.finalizeLine();
					}
				}
				lines.addAll(table.build());
				lines.add("");
			}
		}
		
		if (minMags.length > 1) {
			lines.add(getSubHeading()+" Magnitude Connection Rate Comparisons");
			lines.add(topLink); lines.add("");
			lines.add("This comapres "+meta.primary.name+ " passthrough rates across magniutdes (and also for each rate "
					+ "combiniation type). Linear on the left, log10 on the right.");
			lines.add("");
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.addLine("Linear Passthrough Rates", "Log10 Passthrough Rates");
			for (int m1=0; m1<minMags.length; m1++) {
				for (int m2=m1+1; m2<minMags.length; m2++) {
					table.initNewLine();
					String prefix = "conn_rates_"+SegmentationCalculator.getMagPrefix(minMags[m1])
						+"_vs_"+SegmentationCalculator.getMagPrefix(minMags[m2]);
					File linearPlot = inputSegCalc.plotMagScatter(resourcesDir, prefix,
							false, m1, m2, combiners);
					File logPlot = inputSegCalc.plotMagScatter(resourcesDir, prefix+"_log",
							true, m1, m2, combiners);
					table.addColumn("![Scatter]("+relPathToResources+"/"+linearPlot.getName()+")");
					table.addColumn("![Scatter]("+relPathToResources+"/"+logPlot.getName()+")");
					table.finalizeLine();
				}
			}
			lines.addAll(table.build());
			lines.add("");
		}
		
		return lines;
	}
	
	private SegmentationModelBranchNode getBranchSegModelChoice(FaultSystemRupSet rupSet) {
		LogicTreeBranch<?> branch = rupSet.getModule(LogicTreeBranch.class);
		return branch.getValue(SegmentationModelBranchNode.class);
	}
	
	private JumpProbabilityCalc getBranchSegModel(FaultSystemRupSet rupSet) {
		LogicTreeBranch<?> branch = rupSet.getModule(LogicTreeBranch.class);
		if (branch != null && branch.hasValue(SegmentationModelBranchNode.class)) {
			SegmentationModelBranchNode segModelChoice = branch.getValue(SegmentationModelBranchNode.class);
			if (segModelChoice != null) {
				return segModelChoice.getModel(rupSet, branch);
			}
		}
		return null;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(ClusterRuptures.class, PlausibilityConfiguration.class);
	}

}
