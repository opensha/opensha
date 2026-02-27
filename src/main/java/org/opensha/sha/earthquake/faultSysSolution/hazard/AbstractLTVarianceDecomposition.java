package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import com.google.common.base.Preconditions;

public abstract class AbstractLTVarianceDecomposition {
	
	protected LogicTree<?> tree;
	protected List<LogicTreeLevel<?>> uniqueSamplingLevels;
	
	protected GriddedGeoDataSet meanMap;
	protected GriddedGeoDataSet fullVariance;
	protected GriddedGeoDataSet[] allMaps;
	protected List<Double> allWeights;
	protected ExecutorService exec;

	public AbstractLTVarianceDecomposition(LogicTree<?> tree, List<LogicTreeLevel<?>> uniqueSamplingLevels, ExecutorService exec) {
		this.tree = tree;
		this.uniqueSamplingLevels = uniqueSamplingLevels;
		this.exec = exec;
	}
	
	public void initForMaps(GriddedGeoDataSet meanMap, GriddedGeoDataSet fullVariance,
			GriddedGeoDataSet[] allMaps, List<Double> allWeights) {
		Preconditions.checkState(allMaps.length == tree.size());
		Preconditions.checkState(allMaps.length == allWeights.size());
		this.meanMap = meanMap;
		this.fullVariance = fullVariance;
		this.allMaps = allMaps;
		this.allWeights = allWeights;
	}
	
	public abstract String getHeading();
	
	public abstract List<String> buildLines(List<VarianceContributionResult> results);
	
	public abstract VarianceContributionResult calcMapVarianceContributionForLevel(int levelIndex, LogicTreeLevel<?> level,
			Map<LogicTreeNode, List<GriddedGeoDataSet>> choiceMaps, Map<LogicTreeNode, List<Double>> choiceMapWeights);
	
	public static class VarianceContributionResult {
		public final double meanVarianceContribution;
		public final double maxVarianceContribution;
		public final double maxFractionalVarianceContribution;
		public final double meanCOVContribution;
		public final double maxCOVContribution;
		public final double maxFractionalCOVContribution;
		public VarianceContributionResult(
				double meanVarianceContribution, double maxVarianceContribution, double maxFractionalVarianceContribution,
				double meanCOVContribution, double maxCOVContribution, double maxFractionalCOVContribution) {
			super();
			this.meanVarianceContribution = meanVarianceContribution;
			this.maxVarianceContribution = maxVarianceContribution;
			this.maxFractionalVarianceContribution = maxFractionalVarianceContribution;
			this.meanCOVContribution = meanCOVContribution;
			this.maxCOVContribution = maxCOVContribution;
			this.maxFractionalCOVContribution = maxFractionalCOVContribution;
		}
	}

}
