package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

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

public class MarginalAveragingLTVarianceDecomposition extends AbstractLTVarianceDecomposition {
	
	private GriddedGeoDataSet varAveragingOverSampling;
	private boolean anyNegativeLTCVs;

	public MarginalAveragingLTVarianceDecomposition(List<? extends LogicTreeLevel<?>> levels, List<? extends LogicTreeBranch<?>> branches,
			List<LogicTreeLevel<?>> uniqueSamplingLevels, ExecutorService exec) {
		super(levels, branches, uniqueSamplingLevels, exec);
		// TODO make sure not downsampled
	}

	@Override
	public void initForMaps(GriddedGeoDataSet meanMap, GriddedGeoDataSet fullVariance, GriddedGeoDataSet[] allMaps,
			List<Double> allWeights) {
		// TODO Auto-generated method stub
		super.initForMaps(meanMap, fullVariance, allMaps, allWeights);
		if (uniqueSamplingLevels.isEmpty()) {
			varAveragingOverSampling = null;
		} else {
			List<Double> weightsWithoutSampling = new ArrayList<>();
			GriddedRegion region = meanMap.getRegion();
			GriddedGeoDataSet sdAveragingOverSampling = new GriddedGeoDataSet(region);
			GriddedGeoDataSet cvAveragingOverSampling = new GriddedGeoDataSet(region);
			GriddedGeoDataSet[] mapsAveragingOverSampling = doAverageAcrossLevels(
					-1, null, weightsWithoutSampling, cvAveragingOverSampling, sdAveragingOverSampling);
			Preconditions.checkNotNull(mapsAveragingOverSampling);
			
			LogicTreeHazardCompare.calcSD_CV(mapsAveragingOverSampling, weightsWithoutSampling, meanMap,
					sdAveragingOverSampling, cvAveragingOverSampling, exec);
			varAveragingOverSampling = new GriddedGeoDataSet(region);
			for (int i=0; i<region.getNodeCount(); i++)
				varAveragingOverSampling.set(i, sdAveragingOverSampling.get(i)*sdAveragingOverSampling.get(i));
		}
	}

	@Override
	public VarianceContributionResult calcMapVarianceContributionForLevel(int levelIndex, LogicTreeLevel<?> level,
			Map<LogicTreeNode, List<GriddedGeoDataSet>> choiceMaps, Map<LogicTreeNode, List<Double>> choiceMapWeights) {
		List<Double> weightsExcludingLevel = new ArrayList<>();
		GriddedRegion region = meanMap.getRegion();
		GriddedGeoDataSet cvExcluding = new GriddedGeoDataSet(region);
		GriddedGeoDataSet sdExcluding = new GriddedGeoDataSet(region);
		
		if (doAverageAcrossLevels(levelIndex, level, weightsExcludingLevel, cvExcluding, sdExcluding) == null)
			return null;
		
		GriddedGeoDataSet refVariance;
		if (uniqueSamplingLevels.isEmpty() || uniqueSamplingLevels.contains(level)) {
			// no sampling levels, or this is a sampling level
			// in which case, we're comparing to the full distribution
			refVariance = fullVariance;
		} else {
			// this is not a sampling level, but they exist; we should compare to the distribution
			// after having averaged out the sampling levels
			refVariance = varAveragingOverSampling;
		}
		
		double sumVarContrib = 0d;
		double maxVarContrib = 0d;
		double maxFractVarContrib = 0d;
		double sumCVContrib = 0d;
		double maxCVContrib = 0d;
		double maxFractCVContrib = 0d;
		int numFinite = 0;
		for (int i=0; i<sdExcluding.size(); i++) {
			double sd = sdExcluding.get(i);
			if (Double.isFinite(sd)) {
				double refVar = refVariance.get(i);
				double fullVar = fullVariance.get(i);
				double varWithout = sd*sd;
				double varContrib = refVar - varWithout;
				
				double refCV = Math.sqrt(refVar)/meanMap.get(i);
				double fullCV = Math.sqrt(fullVar)/meanMap.get(i);
				double cvWithout = cvExcluding.get(i);
				double cvContrib = refCV - cvWithout;
				
				sumVarContrib += varContrib;
				maxVarContrib = Math.max(maxVarContrib, varContrib);
				maxFractVarContrib = Math.max(maxFractVarContrib, varContrib/fullVar);
				sumCVContrib += cvContrib;
				maxCVContrib = Math.max(maxCVContrib, cvContrib);
				maxFractCVContrib = Math.max(maxFractCVContrib, cvContrib/fullCV);
				numFinite++;
			}
		}
		anyNegativeLTCVs |= sumVarContrib < 0 || sumCVContrib < 0;
		if (numFinite > 0)
			return new VarianceContributionResult(
					sumVarContrib/(double)numFinite, maxVarContrib, maxFractVarContrib,
					sumCVContrib/(double)numFinite, maxCVContrib, maxFractCVContrib);
		return null;
	}
	
	private GriddedGeoDataSet[] doAverageAcrossLevels(int levelIndex, LogicTreeLevel<?> level, List<Double> weightsExcludingLevel,
			GriddedGeoDataSet cvExcluding, GriddedGeoDataSet sdExcluding) {
		Preconditions.checkState(weightsExcludingLevel.isEmpty());
		Map<LogicTreeBranch<?>, List<Integer>> otherBranchIndexes = new HashMap<>();
		List<LogicTreeBranch<?>> uniqueOtherBranches = new ArrayList<>();
		
		if (level == null) {
			System.out.println("Averaging out any sampling levels");
		} else {
			System.out.println("Averaging out level '"+level.getName()+"' by finding all unique branches excluding that level");
		}
		
		List<Integer> clearIndexes = new ArrayList<>();
		if (levelIndex >= 0)
			clearIndexes.add(levelIndex);
		// now see if there are any random sampling branches where there are unique samples for every branch in the
		// tree (as opposed to N samples at that lavel). In that case, we need to clear them as well.
		int numLevels = levels.size();
		for (int l=0; l<numLevels; l++) {
			if (l != levelIndex) {
				LogicTreeLevel<?> testLevel = levels.get(l);
				if (uniqueSamplingLevels.contains(testLevel)) {
					System.out.println("\tWill also clear out sampling values from level '"+testLevel.getName()+"'");
					clearIndexes.add(l);
				}
			}
		}
		if (clearIndexes.isEmpty())
			return null;
		
		for (int b=0; b<branches.size(); b++) {
			// copy it so we can clear the value
			LogicTreeBranch<?> branch = branches.get(b).copy();
			for (int l : clearIndexes)
				branch.clearValue(l);
			
			List<Integer> indexes = otherBranchIndexes.get(branch);
			if (indexes == null) {
				// first time
				indexes = new ArrayList<>();
				otherBranchIndexes.put(branch, indexes);
				uniqueOtherBranches.add(branch);
			}
			indexes.add(b);
		}
		
		System.out.println("\treduced from "+branches.size()+" to "+uniqueOtherBranches.size()+" branches");
		if (branches.size() == uniqueOtherBranches.size() || uniqueOtherBranches.size() == 1)
			return null;
		
		GriddedGeoDataSet[] mapsExcludingLevel = new GriddedGeoDataSet[uniqueOtherBranches.size()];
		
		for (int b=0; b<mapsExcludingLevel.length; b++) {
			LogicTreeBranch<?> branch = uniqueOtherBranches.get(b);
			List<Integer> indexes = otherBranchIndexes.get(branch);
			double weightSum = 0d;
			GriddedGeoDataSet map = new GriddedGeoDataSet(allMaps[0].getRegion());
			for (int index : indexes) {
				double subWeight = allWeights.get(index);
				weightSum += subWeight;
				GriddedGeoDataSet subMap = allMaps[index];
				for (int i=0; i<map.size(); i++)
					map.add(i, subMap.get(i)*subWeight);
			}
			map.scale(1d/weightSum);
			mapsExcludingLevel[b] = map;
			weightsExcludingLevel.add(weightSum);
		}
		
		System.out.println("\tCalculating variance excluding");
		LogicTreeHazardCompare.calcSD_CV(mapsExcludingLevel, weightsExcludingLevel, meanMap, sdExcluding, cvExcluding, exec);
		return mapsExcludingLevel;
	}

	@Override
	public String getHeading() {
		return "Logic Tree Variance and CV Contributions";
	}

	@Override
	public List<String> buildLines(List<VarianceContributionResult> results) {
		List<String> lines = new ArrayList<>();
		lines.add("This table summarizes how each logic tree branching level contributes to the overall "
				+ "variance and coefficient of variation (CV) in the model.");
		lines.add("");
		lines.add("For each level, its contribution is estimated by collapsing the logic tree across "
				+ "that level; i.e., by averaging values across all branches that differ only in thier "
				+ "choice at that level.");
		if (uniqueSamplingLevels.size() > 1) {
			lines.add("");
			lines.add("This logic tree contains multiple random sampling levels and variance cannot "
					+ "be decomposed between them. They are bundled into a single line of the table.");
		}
		if (anyNegativeLTCVs) {
			lines.add("");
			lines.add("In some cases, the variance (and therefore CV) may slightly increase after removing "
					+ "a level. This is due to the behavior of weighted averaging and is interpreted as "
					+ "statistical noise, indicating that the level does not significantly contribute to model "
					+ "variability.");
		}
		lines.add("");
		lines.add("Both spatially averaged and maximum contributions are reported for each level.");
		lines.add("");

		double fullVarSum = 0d;
		double fullVarMax = 0d;
		double fullCVsum = 0d;
		double fullCVmax = 0d;
		int numValid = 0;
		for (int i=0; i<fullVariance.size(); i++) {
			double var = fullVariance.get(i);
			double mean = meanMap.get(i);
			if (Double.isFinite(var) && Double.isFinite(mean)) {
				double cv = Math.sqrt(var)/mean;
				fullVarSum += var;
				fullVarMax = Math.max(fullVarMax, var);
				fullCVsum += cv;
				fullCVmax = Math.max(fullCVmax, cv);
				numValid++;
			}
		}
		double fullVar = fullVarSum/(double)numValid;
		double fullCV = fullCVsum/(double)numValid;
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Branch Level", "Average Variance Contribution", "Average CV Contribution",
				"Maximum Variance Contribution", "Maximum CV Contribution");
		table.addLine("Full model", "100%", LogicTreeHazardCompare.threeDigits.format(fullCV),
				"100%", LogicTreeHazardCompare.threeDigits.format(fullCVmax));
		int numLevels = levels.size();
		Preconditions.checkState(results.size() == numLevels);
		int numWithResults = 0;
		for (int l=0; l<numLevels; l++) {
			VarianceContributionResult varResult = results.get(l);
			if (varResult == null)
				continue;
			numWithResults++;
			
			LogicTreeLevel<?> level = levels.get(l);
			
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
			double fractCV = varResult.meanCVContribution / fullCV;
			
			table.addLine(levelName,
					LogicTreeHazardCompare.pDF.format(fractVar),
					LogicTreeHazardCompare.threeDigits.format(varResult.meanCVContribution)+" ("+LogicTreeHazardCompare.pDF.format(fractCV)+")",
					LogicTreeHazardCompare.pDF.format(varResult.maxFractionalVarianceContribution),
					LogicTreeHazardCompare.threeDigits.format(varResult.maxCVContribution)+" ("+LogicTreeHazardCompare.pDF.format(varResult.maxFractionalCVContribution)+")");
		}
		if (numWithResults < 2)
			return null;
		lines.addAll(table.build());
		lines.add("");
		return lines;
	}

}
