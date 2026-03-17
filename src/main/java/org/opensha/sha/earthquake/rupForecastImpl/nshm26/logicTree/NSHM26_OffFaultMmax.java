package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractContinuousDistributionSampledLevel;
import org.opensha.commons.logicTree.LogicTreeNode.SimpleValuedNode;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader;
import org.opensha.sha.util.TectonicRegionType;

public class NSHM26_OffFaultMmax extends SimpleValuedNode<Double> implements MaxMagOffFaultBranchNode {
	
	private TectonicRegionType trt;

	private NSHM26_OffFaultMmax() {};

	NSHM26_OffFaultMmax(TectonicRegionType trt, double value, double weight, String name, String shortName, String filePrefix) {
		super(value, Double.class, weight, name, shortName, filePrefix);
		this.trt = trt;
	}

	@Override
	public double getMaxMagOffFault() {
		return getValue();
	}

	@Override
	public TectonicRegionType getTectonicRegime() {
		return trt;
	}
	
	public static class AbstractMmaxDistributionSamplingLevel extends AbstractContinuousDistributionSampledLevel<NSHM26_OffFaultMmax> {
		
		protected TectonicRegionType trt;

		private AbstractMmaxDistributionSamplingLevel() {
			
		}

		protected AbstractMmaxDistributionSamplingLevel(TectonicRegionType trt, ContinuousDistribution dist) {
			super(NSHM26_RegionLoader.getNameForTRT(trt)+" Off Fault Mmax",
					NSHM26_RegionLoader.getNameForTRT(trt)+"-MmaxOff", dist, "Sample ", "Sample", "Sample");
			this.trt = trt;
		}

		@Override
		protected NSHM26_OffFaultMmax build(int index, Double value, double weightEach) {
			return new NSHM26_OffFaultMmax(trt, value, weightEach,
					getNodeName(index), getNodeShortName(index), getNodeFilePrefix(index));
		}

		@Override
		public Class<? extends NSHM26_OffFaultMmax> getType() {
			return NSHM26_OffFaultMmax.class;
		}
		
	}
	
	public static class CrustalSampledLevel extends AbstractMmaxDistributionSamplingLevel {
		
		private CrustalSampledLevel() {
			trt = TectonicRegionType.ACTIVE_SHALLOW;
		}
		
		public CrustalSampledLevel(ContinuousDistribution dist) {
			super(TectonicRegionType.ACTIVE_SHALLOW, dist);
		}
		
	}
	
	public static class IntraslabSampledLevel extends AbstractMmaxDistributionSamplingLevel {
		
		private IntraslabSampledLevel() {
			trt = TectonicRegionType.SUBDUCTION_SLAB;
		}
		
		public IntraslabSampledLevel(ContinuousDistribution dist) {
			super(TectonicRegionType.SUBDUCTION_SLAB, dist);
		}
		
	}
	
//	public static class CrustalFixedLevel extends AbstractMmaxDistributionSamplingLevel {
//		
//		private CrustalSampledLevel() {
//			trt = TectonicRegionType.ACTIVE_SHALLOW;
//		}
//		
//		public CrustalSampledLevel(ContinuousDistribution dist) {
//			super(TectonicRegionType.ACTIVE_SHALLOW, dist);
//		}
//		
//	}

}
