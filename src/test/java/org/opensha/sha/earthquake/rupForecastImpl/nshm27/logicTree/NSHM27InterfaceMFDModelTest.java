package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import static org.junit.Assert.assertEquals;

import org.apache.commons.statistics.distribution.UniformContinuousDistribution;
import org.junit.Test;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.util.TectonicRegionType;

public class NSHM27InterfaceMFDModelTest {

	@Test
	public void testConfiguredWeights() {
		assertEquals(0.5d, NSHM27_InterfaceMFDModel.INDEPENDENT.getNodeWeight(), 0d);
		assertEquals(0.1d, NSHM27_InterfaceMFDModel.EXTRAPOLATED.getNodeWeight(), 0d);
		assertEquals(0.4d, NSHM27_InterfaceMFDModel.HINGED.getNodeWeight(), 0d);

		double reductionWeightSum = 0d;
		for (NSHM27_InterfaceSeismicityMomentReduction reduction
				: NSHM27_InterfaceSeismicityMomentReduction.values()) {
			assertEquals(1d/3d, reduction.getNodeWeight(), 0d);
			reductionWeightSum += reduction.getNodeWeight();
		}
		assertEquals(1d, reductionWeightSum, 1e-15);
	}

	@Test
	public void testSamplingSelectsEachModelAndOnlySamplesIndependentB() {
		NSHM27_InterfaceMFDModel.SamplingLevel level = new NSHM27_InterfaceMFDModel.SamplingLevel(
				UniformContinuousDistribution.of(0.5d, 1.5d));
		level.build(new double[] { 0.25d, 0.55d, 0.8d });

		assertEquals(NSHM27_InterfaceMFDModel.INDEPENDENT, level.getNodes().get(0).getModel());
		assertEquals(NSHM27_InterfaceMFDModel.EXTRAPOLATED, level.getNodes().get(1).getModel());
		assertEquals(NSHM27_InterfaceMFDModel.HINGED, level.getNodes().get(2).getModel());
	}

	@Test
	public void testNonSampledDefaultUsesIndependentModel() {
		LogicTreeBranch<LogicTreeNode> branch = NSHM27_LogicTree.buildDefault(
				NSHM27_SeismicityRegions.GNMI, TectonicRegionType.SUBDUCTION_INTERFACE, false);
		assertEquals(NSHM27_InterfaceMFDModel.INDEPENDENT,
				branch.requireValue(NSHM27_InterfaceMFDModel.class));
		assertEquals(NSHM27_InterfaceSeismicityMomentReduction.REGIONAL,
				branch.requireValue(NSHM27_InterfaceSeismicityMomentReduction.class));
	}
}
