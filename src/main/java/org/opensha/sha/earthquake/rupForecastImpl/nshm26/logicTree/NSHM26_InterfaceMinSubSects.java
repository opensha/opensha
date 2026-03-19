package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM26_InterfaceMinSubSects implements ValuedLogicTreeNode<Integer> {
	ONE("One", 1, 0.25),
	TWO("Two", 2, 0.25),
	FOUR("Four", 4, 0.25),
	NINE("Nine", 9, 0.25);
	
	private final String name;
	private final int count;
	private final double weight;

	private NSHM26_InterfaceMinSubSects(String name, int count, double weight) {
		this.name = name;
		this.count = count;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return count+"";
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getFilePrefix() {
		return name();
	}

	@Override
	public Integer getValue() {
		return count;
	}

	@Override
	public Class<? extends Integer> getValueType() {
		return Integer.class;
	}

	@Override
	public void init(Integer value, Class<? extends Integer> valueClass, double weight, String name, String shortName,
			String filePrefix) {
		throw new UnsupportedOperationException("Enum version cannot be initialized");
	}

	@Override
	public double getNodeWeight() {
		return weight;
	}

}
