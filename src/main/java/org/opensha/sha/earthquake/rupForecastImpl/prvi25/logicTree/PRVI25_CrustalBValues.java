package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
//@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME) // if rate balancing enabled
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME) // if rate balancing disabled
public enum PRVI25_CrustalBValues implements SectionSupraSeisBValues.Constant {
	
	B_0p0(0d,		0.25),
	B_0p25(0.25,	0.0),
	B_0p5(0.5,		0.5),
	B_0p75(0.75,	0.0),
	B_1p0(1d,		0.25),
	AVERAGE(0.5,	0d) {

		@Override
		public String getShortName() {
			return "bAvg="+(float)bValue;
		}

		@Override
		public String getName() {
			return super.getName()+" (Averaged)";
		}

		@Override
		public String getFilePrefix() {
			return "AvgSupraB";
		}
		
	};
	
	public final double bValue;
	public final double weight;

	private PRVI25_CrustalBValues(double bValue, double weight) {
		this.bValue = bValue;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return getName();
	}

	@Override
	public String getName() {
		return "b="+(float)bValue;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return "B"+(float)bValue;
	}

	@Override
	public double getB() {
		return bValue;
	}

}
