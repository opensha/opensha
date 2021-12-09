package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.RateCombiner;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SegmentationCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SegmentationCalculator.Scalars;

public class SegmentationPlot extends AbstractSolutionPlot {
	
	private boolean skipCoulomb = true;

	@Override
	public String getName() {
		return "Fault Segmentation";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		double minMag = sol.getRupSet().getMinMag();
		double[] minMags;
		if (minMag >= 7.5)
			minMags = new double[] { 7.5 };
		else if (minMag >= 7)
			minMags = new double[] { 7d, 7.5 };
		else if (minMag >= 6.5)
//			minMags = new double[] { 6.5, 7d, 7.5 };
			minMags = new double[] { 6.5, 7d };
		else
//			minMags = new double[] { 0d, 6.5, 7d, 7.5 };
			minMags = new double[] { 0d, 7d };
		
		lines.add("The following plots show implied segmentation from the rates of this fault system solution.");
		lines.add("");
		
		List<ClusterRupture> inputRups = sol.getRupSet().requireModule(ClusterRuptures.class).getAll();
		PlausibilityConfiguration inputConfig = sol.getRupSet().requireModule(PlausibilityConfiguration.class);
		SectionDistanceAzimuthCalculator distAzCalc = inputConfig.getDistAzCalc();
		
		SegmentationCalculator inputSegCalc = new SegmentationCalculator(
				sol, inputRups, inputConfig.getConnectionStrategy(), distAzCalc, minMags);
		SegmentationCalculator compSegCalc = null;
		if (meta.comparison != null && meta.comparison.sol != null && meta.comparison.rupSet.hasAllModules(getRequiredModules())) {
			List<ClusterRupture> compRups = meta.comparison.rupSet.requireModule(ClusterRuptures.class).getAll();
			PlausibilityConfiguration compConfig = meta.comparison.rupSet.requireModule(PlausibilityConfiguration.class);
			compSegCalc = new SegmentationCalculator(meta.comparison.sol, compRups, compConfig.getConnectionStrategy(), distAzCalc, minMags);
		}
		String compName = meta.comparison == null ? null : meta.comparison.name;
		if (inputSegCalc.areMultipleJumpsPerParent() || compSegCalc != null && compSegCalc.areMultipleJumpsPerParent()) {
			String names = null;
			if (inputSegCalc.areMultipleJumpsPerParent()) {
				names = meta.primary.name;
				inputSegCalc = inputSegCalc.combineMultiJumps(true);
			}
			if (compSegCalc != null && compSegCalc.areMultipleJumpsPerParent()) {
				if (names == null)
					names = compName;
				else
					names = "both "+names+" and "+compName;
				compSegCalc = compSegCalc.combineMultiJumps(true);
			}
			lines.add("NOTE: "+names+" has multiple jumping points between parent sections. We consolidate "
					+ "all jumps to occur at a single jumping point (with the highest jumping rate) and average "
					+ "quantities on either side of the jump (participation/slip rates)");
			lines.add("");
		}
		
//		RateCombiner[] combiners = RateCombiner.values();
		RateCombiner[] combiners = { RateCombiner.MIN };
		Scalars[] scalars = Scalars.values();
		if (skipCoulomb) {
			List<Scalars> newScalars = new ArrayList<>();
			for (Scalars scalar : scalars)
				if (!scalar.name().contains("CFF"))
					newScalars.add(scalar);
			scalars = newScalars.toArray(new Scalars[0]);
		}
		
		File[] inputConnRates = inputSegCalc.plotConnectionRates(resourcesDir, "conn_rates", meta.primary.name);
		Map<RateCombiner, File[]> inputPassthroughRates = new HashMap<>();
		for (RateCombiner combiner : combiners)
			inputPassthroughRates.put(combiner, inputSegCalc.plotConnectionFracts(resourcesDir,
					"conn_passthrough_"+combiner.name(), "Connection Passthrough Rates, Relative to "+combiner, combiner));
		
		Map<RateCombiner, File[]> shawComps = new HashMap<>();
		Map<RateCombiner, File[]> shawLogComps = new HashMap<>();
		for (RateCombiner combiner : combiners) {
			shawComps.put(combiner, inputSegCalc.plotShaw07Comparison(resourcesDir, "conn_passthrough_shaw07", false, combiner));
			shawLogComps.put(combiner, inputSegCalc.plotShaw07Comparison(resourcesDir, "conn_passthrough_shaw07_log", true, combiner));
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
		Map<Scalars, File[]> compScalarPassthroughs = null;
		Map<Scalars, File[]> compScalarLogPassthroughs = null;
		if (compSegCalc != null) {
			compConnRates = compSegCalc.plotConnectionRates(resourcesDir, "comp_conn_rates", compName);
			compPassthroughRates = new HashMap<>();
			for (RateCombiner combiner : combiners)
				compPassthroughRates.put(combiner, compSegCalc.plotConnectionFracts(resourcesDir,
						"comp_conn_passthrough_"+combiner.name(), "Connection Passthrough Rates, Relative to "+combiner, combiner));
			compScalarPassthroughs = new HashMap<>();
			compScalarLogPassthroughs = new HashMap<>();
			for (Scalars scalar : scalars) {
				compScalarPassthroughs.put(scalar, compSegCalc.plotFractVsScalars(resourcesDir,
						"comp_conn_passthrough_"+scalar.name(), scalar, false, combiners));
				compScalarLogPassthroughs.put(scalar, compSegCalc.plotFractVsScalars(resourcesDir,
						"comp_conn_passthrough_"+scalar.name()+"_log", scalar, true, combiners));
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
				lines.add("![Rates]("+relPathToResources+"/"+inputConnRates[m].getName()+")");
			} else {
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.addLine(meta.primary.name, compName);
				table.initNewLine();
				table.addColumn("![Rates]("+relPathToResources+"/"+inputConnRates[m].getName()+")");
				table.addColumn("![Rates]("+relPathToResources+"/"+compConnRates[m].getName()+")");
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
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			if (compSegCalc != null)
				table.addLine(meta.primary.name, compName);
			for (RateCombiner combiner : combiners) {
				table.initNewLine();
				table.addColumn("![Rates]("+relPathToResources+"/"+inputPassthroughRates.get(combiner)[m].getName()+")");
				if (compSegCalc != null)
					table.addColumn("![Rates]("+relPathToResources+"/"+compPassthroughRates.get(combiner)[m].getName()+")");
				table.finalizeLine();
			}
			lines.addAll(table.build());
			lines.add("");
			
			lines.add("**Connection Passthrough Rates vs Shaw 2007**");
			lines.add(""); lines.add(topLink); lines.add("");
			lines.add("This plots passthrough rates versus various the distance relationship established in Shaw (2007).");
			lines.add("");
			
			table = MarkdownUtils.tableBuilder();
			table.addLine("Linear", "Log10");
			for (RateCombiner combiner : combiners) {
				table.initNewLine();
				table.addColumn("![Rates]("+relPathToResources+"/"+shawComps.get(combiner)[m].getName()+")");
				table.addColumn("![Rates]("+relPathToResources+"/"+shawLogComps.get(combiner)[m].getName()+")");
				table.finalizeLine();
			}
			lines.addAll(table.build());
			lines.add("");
			
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

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(ClusterRuptures.class, PlausibilityConfiguration.class);
	}

}
