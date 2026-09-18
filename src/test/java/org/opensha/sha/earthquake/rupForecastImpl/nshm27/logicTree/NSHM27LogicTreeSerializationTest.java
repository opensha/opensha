package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import static org.junit.Assert.assertEquals;

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
		LogicTree<LogicTreeNode> tree = NSHM27_LogicTree.buildMultiRegimeTree(
				NSHM27_SeismicityRegions.GNMI, 8, 123456L, SamplingMethod.OWEN_SCRAMBLED_SOBOL);
		assertEquals(SamplingPointSetLayout.EXPANDED, tree.getSamplingPointSetLayout());
		assertEquals(SamplingPointSetLayout.EXPANDED.dimensions(tree), tree.getSamplingPointSet().dimensions());
		Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
		String first = gson.toJson(tree, LogicTree.class);
		LogicTree<LogicTreeNode> loaded = LogicTree.read(new StringReader(first));
		String second = gson.toJson(loaded, LogicTree.class);
		assertEquals(first, second);
	}
}
