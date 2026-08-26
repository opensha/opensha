package org.opensha.commons.logicTree.sampling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.statistics.distribution.UniformContinuousDistribution;
import org.junit.Test;
import org.opensha.commons.data.sampling.ArrayPointSet;
import org.opensha.commons.data.sampling.InactiveSamplingDimension;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.ContinuousDistributionSampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.FileBackedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomlyGeneratedLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.FileBackedNode;
import org.opensha.commons.logicTree.LogicTreeNode.RandomlyGeneratedNode;
import org.opensha.commons.logicTree.TectonicRegionBranchTreeNode;
import org.opensha.sha.util.TectonicRegionType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class LogicTreePointSetMapperTest {

	@Test
	public void testExternalMappingAndRequiredDimension() {
		FileBackedNode low = new FileBackedNode("Low", "L", 0.25, "low");
		FileBackedNode high = new FileBackedNode("High", "H", 0.75, "high");
		FileBackedLevel categorical = new FileBackedLevel("Category", "Cat", List.of(low, high));
		ContinuousDistributionSampledLevel continuous = new ContinuousDistributionSampledLevel("Continuous", "Cont",
				UniformContinuousDistribution.of(0d, 10d), "Sample ", "S", "s");
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = List.of(categorical, continuous);
		LogicTreePointSetMapper<LogicTreeNode> mapper = new LogicTreePointSetMapper<>(levels, high);
		assertTrue(mapper.getSamplingDimensions().get(0) instanceof InactiveSamplingDimension);

		PointSet points = new ArrayPointSet(new double[][] { { 0d, 0.1 }, { 0.9, 0.8 } });
		LogicTree<LogicTreeNode> tree = mapper.map(points);
		assertEquals(high, tree.getBranch(0).getValue(0));
		assertEquals(high, tree.getBranch(1).getValue(0));
		assertEquals(1d, ((Number)((LogicTreeNode.ValuedLogicTreeNode<?>)tree.getBranch(0).getValue(1)).getValue()).doubleValue(), 0d);
		assertEquals(8d, ((Number)((LogicTreeNode.ValuedLogicTreeNode<?>)tree.getBranch(1).getValue(1)).getValue()).doubleValue(), 0d);
	}

	@Test
	public void testGeneratedSeedsUseCoordinateAndLevelSalt() {
		SeedLevel first = new SeedLevel("First", "F");
		SeedLevel second = new SeedLevel("Second", "S");
		double[] samples = { 0d, 0.5 };
		first.build(samples);
		second.build(samples);
		assertNotEquals(first.getNodes().get(0).getSeed(), second.getNodes().get(0).getSeed());
		long seed = first.getNodes().get(1).getSeed();
		first.build(samples);
		assertEquals(seed, first.getNodes().get(1).getSeed());
	}

	@Test
	public void testSamplingMetadataAndPointSetJson() throws Exception {
		FileBackedLevel level = new FileBackedLevel("Category", "Cat", List.of(
				new FileBackedNode("A", "A", 0.5, "a"), new FileBackedNode("B", "B", 0.5, "b")));
		LogicTree<LogicTreeNode> tree = LogicTree.buildSampled(List.of(level), 8, 0L, SamplingMethod.MONTE_CARLO);
		assertTrue(tree.isSampled());
		assertEquals(0L, tree.getSamplingRandomSeed());
		assertTrue(tree.hasSamplingPointSet());
		PointSet attached = tree.getSamplingPointSet();
		double original = attached.get(0, 0);
		double[] pointCopy = attached.getPoint(0);
		pointCopy[0] = original == 0d ? 0.5 : 0d;
		assertEquals(original, attached.get(0, 0), 0d);

		Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
		StringWriter writer = new StringWriter();
		gson.toJson(tree, LogicTree.class, writer);
		String json = writer.toString();
		assertTrue(json.contains("\"randomSeed\":0"));
		LogicTree<LogicTreeNode> loaded = LogicTree.read(new StringReader(json));
		assertEquals(SamplingMethod.MONTE_CARLO, loaded.getSamplingMethod());
		assertTrue(loaded.hasSamplingPointSet());
		assertEquals(attached.get(0, 0), loaded.getSamplingPointSet().get(0, 0), 0d);

		LogicTree<LogicTreeNode> sobol = LogicTree.buildSampled(List.of(level), 4, 123L, SamplingMethod.SOBOL);
		assertTrue(sobol.isSampled());
		assertEquals(0L, sobol.getSamplingRandomSeed());
		assertTrue(sobol.getSamplingPointSet().get(0, 0) > 0d);
		assertFalse(sobol.getSamplingPointSet().getDimension(0) instanceof InactiveSamplingDimension);
		StringWriter sobolWriter = new StringWriter();
		gson.toJson(sobol, LogicTree.class, sobolWriter);
		assertFalse(sobolWriter.toString().contains("\"randomSeed\""));
	}

	@Test
	public void testAllGeneratedSamplingMethodsAndExternalInput() {
		FileBackedLevel first = binaryLevel("First", "F");
		FileBackedLevel second = binaryLevel("Second", "S");
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = List.of(first, second);
		for (SamplingMethod method : SamplingMethod.values()) {
			if (method == SamplingMethod.EXTERNAL)
				continue;
			LogicTree<LogicTreeNode> tree = LogicTree.buildSampled(levels, 16, 98123L, method);
			assertEquals(method, tree.getSamplingMethod());
			assertEquals(16, tree.size());
			assertTrue(tree.hasSamplingPointSet());
		}

		PointSet external = new ArrayPointSet(new double[][] { { 0.1, 0.9 }, { 0.9, 0.1 } });
		LogicTree<LogicTreeNode> tree = LogicTree.buildSampled(levels, external);
		assertEquals(SamplingMethod.EXTERNAL, tree.getSamplingMethod());
		assertEquals(first.getNodes().get(0), tree.getBranch(0).getValue(0));
		assertEquals(second.getNodes().get(0), tree.getBranch(1).getValue(1));

		LogicTree<LogicTreeNode> transformed = LogicTree.buildSampled(levels, external,
				points -> new ArrayPointSet(new double[][] { points.getPoint(1), points.getPoint(0) }));
		assertEquals(first.getNodes().get(1), transformed.getBranch(0).getValue(0));
		assertEquals(0.9, transformed.getSamplingPointSet().get(0, 0), 0d);
	}

	@Test
	public void testSampledLogicTreeBuilderDirectly() {
		FileBackedLevel level = binaryLevel("Builder", "Builder");
		SampledLogicTreeBuilder<LogicTreeNode> builder = new SampledLogicTreeBuilder<>(List.of(level));
		LogicTree<LogicTreeNode> generated = builder.build(8, 42L, SamplingMethod.SOBOL);
		assertEquals(SamplingMethod.SOBOL, generated.getSamplingMethod());
		assertEquals(8, generated.size());
		assertTrue(generated.getSamplingPointSet().get(0, 0) > 0d);

		LogicTree<LogicTreeNode> external = builder.build(
				new ArrayPointSet(new double[][] { { 0.25 }, { 0.75 } }));
		assertEquals(SamplingMethod.EXTERNAL, external.getSamplingMethod());
		assertEquals(level.getNodes().get(0), external.getBranch(0).getValue(0));
		assertEquals(level.getNodes().get(1), external.getBranch(1).getValue(0));
	}

	@Test
	public void testLegacyOriginalSeedPropertyIgnored() throws Exception {
		FileBackedLevel level = binaryLevel("Legacy", "Legacy");
		LogicTree<LogicTreeNode> tree = LogicTree.buildSampled(List.of(level), 4, 123L,
				SamplingMethod.MONTE_CARLO);
		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(tree, LogicTree.class).replaceFirst("\"shortName\":\"Legacy\"",
				"\"shortName\":\"Legacy\",\"originalSeed\":987654321");
		LogicTree<LogicTreeNode> loaded = LogicTree.read(new StringReader(json));
		assertEquals(tree.size(), loaded.size());
		assertEquals(SamplingMethod.MONTE_CARLO, loaded.getSamplingMethod());
	}

	@Test
	public void testNestedTreePointSetProbabilitiesRoundTripExactly() throws Exception {
		FileBackedLevel innerLevel = new FileBackedLevel("Inner", "Inner", List.of(
				new FileBackedNode("A", "A", 0.3, "a"),
				new FileBackedNode("B", "B", 0.3, "b"),
				new FileBackedNode("C", "C", 0.3, "c"),
				new FileBackedNode("D", "D", 0.1, "d")));
		LogicTree<LogicTreeNode> inner = LogicTree.buildSampled(List.of(innerLevel),
				new ArrayPointSet(new double[][] { { 0.1 }, { 0.4 }, { 0.7 }, { 0.95 } }));
		TectonicRegionBranchTreeNode.Level outerLevel = new TectonicRegionBranchTreeNode.Level(
				TectonicRegionType.ACTIVE_SHALLOW, inner, "Outer", "Outer", "Branch ", "B", "b");
		List<LogicTreeBranch<LogicTreeNode>> branches = new java.util.ArrayList<>();
		for (TectonicRegionBranchTreeNode node : outerLevel.getNodes()) {
			LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(List.of(outerLevel), List.of(node));
			branch.setOrigBranchWeight(0.25);
			branches.add(branch);
		}
		LogicTree<LogicTreeNode> outer = LogicTree.fromExisting(List.of(outerLevel), branches);

		Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
		String first = gson.toJson(outer, LogicTree.class);
		LogicTree<LogicTreeNode> loaded = LogicTree.read(new StringReader(first));
		String second = gson.toJson(loaded, LogicTree.class);
		assertEquals(first, second);
	}

	private static FileBackedLevel binaryLevel(String name, String shortName) {
		return new FileBackedLevel(name, shortName, List.of(
				new FileBackedNode(name + " A", shortName + "A", 0.5, shortName.toLowerCase() + "a"),
				new FileBackedNode(name + " B", shortName + "B", 0.5, shortName.toLowerCase() + "b")));
	}

	private static final class SeedLevel extends RandomlyGeneratedLevel<SeedNode> {
		SeedLevel(String name, String shortName) {
			super(name, shortName, name + " ", shortName, shortName.toLowerCase());
		}
		@Override public Class<? extends SeedNode> getType() { return SeedNode.class; }
		@Override public SeedNode build(Long seed, double weight, String name, String shortName, String filePrefix) {
			return new SeedNode(name, shortName, filePrefix, weight, seed);
		}
	}

	public static final class SeedNode extends RandomlyGeneratedNode {
		public SeedNode() {}
		SeedNode(String name, String shortName, String prefix, double weight, long seed) {
			super(name, shortName, prefix, weight, seed);
		}
	}
}
