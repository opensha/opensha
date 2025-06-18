package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.apache.commons.math3.util.MathArrays;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.sha.earthquake.faultSysSolution.hazard.AbstractLTVarianceDecomposition.VarianceContributionResult;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class SparseLTVarianceDecomposition extends AbstractLTVarianceDecomposition {

	public SparseLTVarianceDecomposition(LogicTree<?> tree, List<LogicTreeLevel<?>> uniqueSamplingLevels, ExecutorService exec) {
		super(tree, uniqueSamplingLevels, exec);
	}

	@Override
	public VarianceContributionResult calcMapVarianceContributionForLevel(int levelIndex, LogicTreeLevel<?> level,
			Map<LogicTreeNode, List<GriddedGeoDataSet>> choiceMaps, Map<LogicTreeNode, List<Double>> choiceMapWeights) {
		Preconditions.checkState(allMaps.length == tree.size());
		Preconditions.checkState(allMaps.length == allWeights.size());
		
		if (uniqueSamplingLevels.contains(level)) {
			// impossible to estimate
			return null;
		}
		
		List<LogicTreeNode> choices = new ArrayList<>();
		List<GriddedGeoDataSet> choiceMeanMaps = new ArrayList<>();
		List<Double> choiceWeights = new ArrayList<>();
		for (LogicTreeNode choice : choiceMapWeights.keySet()) {
			double sumWeight = 0d;
			for (double weight : choiceMapWeights.get(choice))
				sumWeight += weight;
			if (sumWeight > 0d) {
				choices.add(choice);
				choiceWeights.add(sumWeight);
				choiceMeanMaps.add(LogicTreeHazardCompare.buildMean(choiceMaps.get(choice), choiceMapWeights.get(choice)));
			}
		}
		
		double[] weightsArray = MathArrays.normalizeArray(Doubles.toArray(choiceWeights), choiceWeights.size());
		if (choices.size() < 2)
			return null;
		
		double sumVar = 0d;
		double maxVar = 0d;
		double maxFractVar = 0d;
		double sumCOV = 0d;
		double maxCOV = 0d;
		double maxFractCOV = 0d;
		int numFinite = 0;
		Variance varCalc = new Variance(false);
		for (int i=0; i<meanMap.size(); i++) {
			double mean = meanMap.get(i);
			if (mean > 0 && Double.isFinite(mean)) {
				double[] values = new double[choices.size()];
				for (int c=0; c<choices.size(); c++)
					values[c] = choiceMeanMaps.get(c).get(i);
				double var = varCalc.evaluate(values, weightsArray, mean);
				double fullVar = fullVariance.get(i);
				if (Double.isFinite(var)) {
					maxVar = Math.max(maxVar, var);
					maxFractVar = Math.max(maxFractVar, var/fullVar);
					sumVar += var;
					
					double sd = Math.sqrt(var);
					double cov = sd/mean;
					maxCOV = Math.max(maxCOV, cov);
					double fullCOV = Math.sqrt(fullVar)/mean;
					maxFractCOV = Math.max(maxFractCOV, cov/fullCOV);
					sumCOV += cov;
					numFinite++;
				}
			}
		}
		if (numFinite > 0)
			return new VarianceContributionResult(sumVar/(double)numFinite, maxVar, maxFractVar,
					sumCOV/(double)numFinite, maxCOV, maxFractCOV);
		return null;
	}

	@Override
	public String getHeading() {
		return "Logic Tree Variance Contributions";
	}

	@Override
	public List<String> buildLines(List<VarianceContributionResult> results) {
		List<String> lines = new ArrayList<>();
		lines.add("This table summarizes how each logic tree branching level contributes to the overall "
				+ "variance in the model.");
		lines.add("");
		lines.add("This logic tree appears to be randomly-downsampled, so we estimate variance at each level as the "
				+ "weighted variance of the mean maps for each for each choice at that level. This method is less "
				+ "accurate than the marginal-averaging method used for complete logic trees because we are "
				+ "estimating branch-level variance from a few mean maps rather than the full distribution.");
		if (uniqueSamplingLevels.size() > 1) {
			lines.add("");
			lines.add("This logic tree contains multiple random sampling levels and variance cannot "
					+ "be decomposed between them. They are bundled into a single line of the table.");
		}
		lines.add("");
		lines.add("Both spatially averaged and maximum contributions are reported for each level.");
		lines.add("");

		double fullVarSum = 0d;
		double fullVarMax = 0d;
		int numValid = 0;
		for (int i=0; i<fullVariance.size(); i++) {
			double var = fullVariance.get(i);
			double mean = meanMap.get(i);
			if (Double.isFinite(var) && Double.isFinite(mean)) {
				double cov = Math.sqrt(var)/mean;
				fullVarSum += var;
				fullVarMax = Math.max(fullVarMax, var);
				numValid++;
			}
		}
		double fullVar = fullVarSum/(double)numValid;
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Branch Level", "Average Variance Contribution", "Maximum Variance Contribution");
		int numLevels = tree.getLevels().size();
		Preconditions.checkState(results.size() == numLevels);
		int numWithResults = 0;
		for (int l=0; l<numLevels; l++) {
			VarianceContributionResult varResult = results.get(l);
			if (varResult == null)
				continue;
			numWithResults++;
			
			LogicTreeLevel<?> level = tree.getLevels().get(l);
			
			String levelName = level.getName();
			if (uniqueSamplingLevels.size() > 1 && uniqueSamplingLevels.get(0) == level) {
				// this is a bundle across multiple sampling levels
				levelName = uniqueSamplingLevels.size()+" sampling levels: ";
				for (int i=0; i<uniqueSamplingLevels.size(); i++) {
					if (i > 0)
						levelName += ", ";
					levelName += uniqueSamplingLevels.get(i).getShortName();
				}
			}
			
			double fractVar = varResult.meanVarianceContribution / fullVar;
			
			table.addLine(levelName,
					LogicTreeHazardCompare.pDF.format(fractVar),
					LogicTreeHazardCompare.pDF.format(varResult.maxFractionalVarianceContribution));
		}
		if (!uniqueSamplingLevels.isEmpty()) {
			// add unexplained
			double sumAvgVarContrib = 0d;
			for (VarianceContributionResult varResult : results) {
				if (varResult != null)
					sumAvgVarContrib += varResult.meanVarianceContribution;
			}
			double unexplainedFractVar = (1d-sumAvgVarContrib/fullVar);
			String levelName = "Unexplained, potentially attributable to ";
			if (uniqueSamplingLevels.size() > 1) {
				levelName += uniqueSamplingLevels.size()+" sampling levels: ";
				for (int i=0; i<uniqueSamplingLevels.size(); i++) {
					if (i > 0)
						levelName += ", ";
					levelName += uniqueSamplingLevels.get(i).getShortName();
				}
			} else {
				levelName += uniqueSamplingLevels.get(0).getName();
			}
			table.addLine(levelName,
					LogicTreeHazardCompare.pDF.format(unexplainedFractVar), "");
		}
		if (numWithResults < 2)
			return null;
		lines.addAll(table.build());
		lines.add("");
		return lines;
	}

}
