package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;

import org.junit.Test;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.sampling.SamplingMethod;
import org.opensha.commons.logicTree.sampling.SamplingPointSetLayout;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class NSHM27LogicTreeSerializationTest {

	@Test
	public void testMultiRegimePointSetProbabilitiesRoundTripExactly() throws Exception {
		int mappedDimensions = NSHM27_LogicTree.getMultiRegimeSamplingDimensionCount(
				NSHM27_SeismicityRegions.GNMI);
		LogicTree<LogicTreeNode> tree = NSHM27_LogicTree.buildMultiRegimeTree(
				NSHM27_SeismicityRegions.GNMI, 8, 123456L, SamplingMethod.OWEN_SCRAMBLED_SOBOL,
				mappedDimensions + 4);
		LogicTree<LogicTreeNode> standard = NSHM27_LogicTree.buildMultiRegimeTree(
				NSHM27_SeismicityRegions.GNMI, 8, 123456L, SamplingMethod.OWEN_SCRAMBLED_SOBOL);
		assertEquals(SamplingPointSetLayout.EXPANDED, tree.getSamplingPointSetLayout());
		assertEquals(mappedDimensions + 4, tree.getSamplingPointSet().dimensions());
		assertEquals(mappedDimensions, tree.getMappedSamplingPointSet().dimensions());
		for (int d=mappedDimensions; d<tree.getSamplingPointSet().dimensions(); d++)
			assertTrue(!tree.getSamplingPointSet().getDimension(d).isActive());
		for (int p=0; p<tree.size(); p++) {
			assertEquals(standard.getBranch(p).buildFileName(), tree.getBranch(p).buildFileName());
			for (int d=0; d<mappedDimensions; d++)
				assertEquals(standard.getMappedSamplingPointSet().get(p, d),
						tree.getMappedSamplingPointSet().get(p, d), 0d);
		}
		Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
		String first = gson.toJson(tree, LogicTree.class);
		LogicTree<LogicTreeNode> loaded = LogicTree.read(new StringReader(first));
		String second = gson.toJson(loaded, LogicTree.class);
		assertEquals(first, second);
	}
}
