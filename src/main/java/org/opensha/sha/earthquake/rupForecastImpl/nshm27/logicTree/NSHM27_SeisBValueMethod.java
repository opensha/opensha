package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;

/** Choice of catalog b-value treatment used by seismicity rate models. */
@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_SeisBValueMethod implements LogicTreeNode.FixedWeightNode {
	CLASSIFICATION_DEPENDENT("Classification-dependent b-values", "Class-dependent", "bClass", 0.5d),
	UNION("Union catalog b-values", "Union", "bUnion", 0d),
	UNION_WIDE("Union catalog b-values (wide)", "Union (wide)", "bUnionwide", 0.5d);

	private final String name;
	private final String shortName;
	private final String filePrefix;
	private final double weight;

	private NSHM27_SeisBValueMethod(String name, String shortName, String filePrefix, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.filePrefix = filePrefix;
		this.weight = weight;
	}

	@Override public String getName() { return name; }
	@Override public String getShortName() { return shortName; }
	@Override public String getFilePrefix() { return filePrefix; }
	@Override public double getNodeWeight() { return weight; }

	public String getDatasetSuffix() { return filePrefix; }
}
