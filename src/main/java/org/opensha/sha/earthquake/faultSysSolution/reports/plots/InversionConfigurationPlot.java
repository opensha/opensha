package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitProgress;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.MisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.Quantity;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;

public class InversionConfigurationPlot extends AbstractSolutionPlot {

	@Override
	public String getName() {
		return "Inversion Configuration";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		InversionConfiguration config = sol.requireModule(InversionConfiguration.class);
		
		InversionConfiguration compConfig = meta.hasComparisonSol() ?
				meta.comparison.sol.getModule(InversionConfiguration.class) : null;
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		if (compConfig == null)
			table.addLine("Parameter", "Value");
		else
			table.addLine("Parameter", "Primary Value", "Comparison Value");
		
		table.initNewLine().addColumn("**Completion Criteria**");
		table.addColumn(critStr(config.getCompletionCriteria()));
		if (compConfig != null)
			table.addColumn(critStr(compConfig.getCompletionCriteria()));
		table.finalizeLine();
		
		table.initNewLine().addColumn("**Perturbation Function**");
		table.addColumn(config.getPerturbationFunc().name());
		if (compConfig != null)
			table.addColumn(compConfig.getPerturbationFunc().name());
		table.finalizeLine();
		
		table.initNewLine().addColumn("**Nonnegativity Constraint**");
		table.addColumn(config.getNonnegConstraint().name());
		if (compConfig != null)
			table.addColumn(compConfig.getNonnegConstraint().name());
		table.finalizeLine();
		
		table.initNewLine().addColumn("**Cooling Schedule**");
		table.addColumn(config.getCoolingSchedule().name());
		if (compConfig != null)
			table.addColumn(compConfig.getCoolingSchedule().name());
		table.finalizeLine();
		
		table.initNewLine().addColumn("**Initial Solution**");
		table.addColumn(solStr(config.getInitialSolution()));
		if (compConfig != null)
			table.addColumn(solStr(compConfig.getInitialSolution()));
		table.finalizeLine();
		
		table.initNewLine().addColumn("**Water Level**");
		table.addColumn(solStr(config.getWaterLevel()));
		if (compConfig != null)
			table.addColumn(solStr(compConfig.getWaterLevel()));
		table.finalizeLine();
		
		table.initNewLine().addColumn("**Threads**");
		table.addColumn(config.getThreads());
		if (compConfig != null)
			table.addColumn(compConfig.getThreads());
		table.finalizeLine();
		
		if (config.getThreads() > 1 || (compConfig != null && compConfig.getThreads() > 1)) {
			table.initNewLine().addColumn("**Sub-Completion Criteria**");
			table.addColumn(critStr(config.getSubCompletionCriteria()));
			if (compConfig != null)
				table.addColumn(critStr(compConfig.getSubCompletionCriteria()));
			table.finalizeLine();
			
			if (isAvg(config) || isAvg(compConfig)) {
				table.initNewLine().addColumn("**Averaging Layer Threads**");
				table.addColumn(avgThreadStr(config));
				if (compConfig != null)
					table.addColumn(avgThreadStr(compConfig));
				table.finalizeLine();
				
				table.initNewLine().addColumn("**Avg-Completion Criteria**");
				table.addColumn(critStr(config.getAvgCompletionCriteria()));
				if (compConfig != null)
					table.addColumn(critStr(compConfig.getAvgCompletionCriteria()));
				table.finalizeLine();
			}
			
			Quantity reweightQuantity = config.getReweightTargetQuantity();
			Quantity compReweightQuantity = compConfig == null ? null : compConfig.getReweightTargetQuantity();
			
			if (reweightQuantity != null || compReweightQuantity != null) {
				table.initNewLine().addColumn("**Re-weighting Target Quantity**");
				table.addColumn(reweightQuantity == null ? "_N/A_" : reweightQuantity);
				if (compConfig != null)
					table.addColumn(compReweightQuantity == null ? "_N/A_" : compReweightQuantity);
				table.finalizeLine();
			}
		}
		
		table.initNewLine().addColumn("**Custom Rupture Sampler**");
		table.addColumn(config.getSampler() == null ? na : "**YES**");
		if (compConfig != null)
			table.addColumn(compConfig.getSampler() == null ? na : "**YES**");
		table.finalizeLine();
		
		List<String> lines = new ArrayList<>();
		lines.addAll(table.build());;
		
		lines.add("");
		lines.add(getSubHeading()+" Inversion Constraints");
		lines.add(topLink); lines.add("");
		
		table = MarkdownUtils.tableBuilder();
		List<String> constraintNames = new ArrayList<>();
		Map<String, InversionConstraint> constraintsMap = new HashMap<>();
		Map<String, InversionConstraint> compConstraintsMap = compConfig == null ? null : new HashMap<>();
		
		for (InversionConstraint constraint : config.getConstraints()) {
			constraintNames.add(constraint.getName());
			constraintsMap.put(constraint.getName(), constraint);
		}
		
		Map<String, Double> finalWeights = getFinalWeights(sol, constraintsMap);
		Map<String, Double> compFinalWeights = null;
		if (sol.hasModule(InversionMisfitProgress.class)) {
			// see if we have variable weights
			InversionMisfitProgress progress = sol.getModule(InversionMisfitProgress.class);
			List<InversionMisfitStats> allStats = progress.getStats();
			if (!allStats.isEmpty()) {
				boolean anyDifferent = false;
				finalWeights = new HashMap<>();
				InversionMisfitStats last = allStats.get(allStats.size()-1);
				for (MisfitStats misfits : last.getStats()) {
					if (constraintNames.contains(misfits.range.name)) {
						double finalWeight = misfits.range.weight;
						anyDifferent = anyDifferent ||
								(float)finalWeight != (float)constraintsMap.get(misfits.range.name).getWeight();
						finalWeights.put(misfits.range.name, finalWeight);
					}
				}
				if (!anyDifferent)
					finalWeights = null;
			}
		}
		
		table.initNewLine().addColumn("Constraint");
		if (compConfig == null) {
			if (finalWeights == null)
				table.addColumn("Weight");
			else
				table.addColumn("Initial Weight").addColumn("Final Weight");
		} else {
			if (finalWeights == null)
				table.addColumn("Primary Weight");
			else
				table.addColumn("Primary Initial Weight").addColumn("Final");
			for (InversionConstraint constraint : compConfig.getConstraints()) {
				String name = constraint.getName();
				if (!constraintNames.contains(name))
					constraintNames.add(name);
				compConstraintsMap.put(name, constraint);
			}
			compFinalWeights = getFinalWeights(meta.comparison.sol, compConstraintsMap);
			if (compFinalWeights == null)
				table.addColumn("Comparison Weight");
			else
				table.addColumn("Comparison Initial Weight").addColumn("Final");
		}
		table.finalizeLine();
		
		for (String name : constraintNames) {
			table.initNewLine().addColumn(name);
			InversionConstraint constr = constraintsMap.get(name);
			if (constr == null) {
				table.addColumn(na);
				if (finalWeights != null)
					table.addColumn(na);
			} else {
				table.addColumn(optionalDigitDF.format(constr.getWeight()));
				if (finalWeights != null) {
					Double weight = finalWeights.get(name);
					if (weight == null)
						table.addColumn(optionalDigitDF.format(constr.getWeight()));
					else
						table.addColumn(optionalDigitDF.format(weight));
				}
			}
			if (compConfig != null) {
				InversionConstraint compConstr = compConstraintsMap.get(name);
				if (compConstr == null) {
					table.addColumn(na);
					if (compFinalWeights != null)
						table.addColumn(na);
				} else {
					table.addColumn(optionalDigitDF.format(compConstr.getWeight()));
					if (compFinalWeights != null) {
						Double weight = compFinalWeights.get(name);
						if (weight == null)
							table.addColumn(optionalDigitDF.format(compConstr.getWeight()));
						else
							table.addColumn(optionalDigitDF.format(weight));
					}
				}
			}
			table.finalizeLine();
		}
		
		lines.addAll(table.build());
		
		return lines;
	}
	
	private static Map<String, Double> getFinalWeights(FaultSystemSolution sol, Map<String, InversionConstraint> constraintsMap) {
		// see if we have variable weights
		InversionMisfitProgress progress = sol.getModule(InversionMisfitProgress.class);
		if (progress == null)
			return null;
		List<InversionMisfitStats> allStats = progress.getStats();
		if (allStats.isEmpty())
			return null;
		boolean anyDifferent = false;
		Map<String, Double> finalWeights = new HashMap<>();
		InversionMisfitStats last = allStats.get(allStats.size()-1);
		for (MisfitStats misfits : last.getStats()) {
			if (constraintsMap.containsKey(misfits.range.name)) {
				double finalWeight = misfits.range.weight;
				anyDifferent = anyDifferent ||
						(float)finalWeight != (float)constraintsMap.get(misfits.range.name).getWeight();
				finalWeights.put(misfits.range.name, finalWeight);
			}
		}
		if (!anyDifferent)
			return null;
		return finalWeights;
	}
	
	private static boolean isAvg(InversionConfiguration config) {
		if (config == null)
			return false;
		return config.getAvgThreads() != null && config.getAvgThreads() > 1;
	}
	
	private static String avgThreadStr(InversionConfiguration config) {
		int threads = config.getThreads();
		Integer avgThreads = config.getAvgThreads();
		if (avgThreads == null)
			return na;
		double each = (double)threads/avgThreads.doubleValue();
		return avgThreads+" ("+optionalDigitDF.format(each)+" each)";
	}
	
	private static String critStr(CompletionCriteria criteria) {
		if (criteria == null)
			return na;
		else if (criteria instanceof TimeCompletionCriteria)
			return ((TimeCompletionCriteria)criteria).getTimeStr();
		else if (criteria instanceof IterationCompletionCriteria)
			return countDF.format(((IterationCompletionCriteria)criteria).getMinIterations())+" iters";
		return criteria.toString();
	}
	
	private static String solStr(double[] sol) {
		if (sol == null)
			return "_(none)_";
		return "Range: ["+(float)StatUtils.min(sol)+","+(float)StatUtils.max(sol)+"]";
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(InversionConfiguration.class);
	}

}
