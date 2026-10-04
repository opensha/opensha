package org.opensha.sha.earthquake.faultSysSolution.erf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import org.opensha.commons.logicTree.AffectsNone;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.RenewalModels;
import org.opensha.sha.earthquake.faultSysSolution.hazard.HazardCurveMetadata;

public class FSS_ERF_ConfigLogicTreeNodeTest {

	@Test
	public void testNodesComposeOnBaseConfig() {
		LogicTreeBranch<LogicTreeNode> branch = branch(ProbabilityNode.TIME_DEPENDENT, RenewalNode.WEIBULL);
		FSS_ERF_Config config = FSS_ERF_ConfigLogicTreeNode.forBranch(
				FSS_ERF_Config.timeIndependent(50d), branch);
		assertEquals(FSS_ProbabilityModels.NSHM27, config.probabilityModel());
		assertEquals(Integer.valueOf(2026), config.startYear());
		assertEquals(RenewalModels.WEIBULL, config.renewalModel());
		assertEquals(50d, config.durationYears(), 0d);
	}

	@Test
	public void testMixedTreeMetadata() {
		LogicTreeBranch<LogicTreeNode> tiBranch = branch(ProbabilityNode.POISSON, RenewalNode.DEFAULT);
		LogicTreeBranch<LogicTreeNode> tdBranch = branch(ProbabilityNode.TIME_DEPENDENT, RenewalNode.DEFAULT);
		LogicTree<LogicTreeNode> tree = LogicTree.fromExisting(tiBranch.getLevels(), List.of(tiBranch, tdBranch));
		HazardCurveMetadata metadata = metadataForTree(FSS_ERF_Config.timeIndependent(50d), tree);
		assertTrue(metadata.hasTimeIndependentCurves());
		assertTrue(metadata.hasTimeDependentCurves());
		assertEquals(50d, metadata.getDurationYears(), 0d);
		assertEquals(2026, metadata.getTimeSpan().getStartTimeYear());
	}

	@Test
	public void testTreeMetadataRejectsDifferentTDStartTimes() {
		LogicTreeLevel<StartYearNode> level = LogicTreeLevel.forEnum(
				StartYearNode.class, "Forecast Start Year", "StartYear");
		LogicTreeBranch<LogicTreeNode> first = new LogicTreeBranch<>(List.of(level), List.of(StartYearNode.YEAR_2026));
		LogicTreeBranch<LogicTreeNode> second = new LogicTreeBranch<>(List.of(level), List.of(StartYearNode.YEAR_2030));
		LogicTree<LogicTreeNode> tree = LogicTree.fromExisting(List.of(level), List.of(first, second));
		try {
			metadataForTree(FSS_ERF_Config.timeIndependent(50d), tree);
			fail("Expected different TD start times to be rejected");
		} catch (IllegalArgumentException expected) {}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static LogicTreeBranch<LogicTreeNode> branch(ProbabilityNode probability, RenewalNode renewal) {
		List levels = List.of(
				LogicTreeLevel.forEnum(ProbabilityNode.class, "Probability Model", "ProbModel"),
				LogicTreeLevel.forEnum(RenewalNode.class, "Renewal Model", "Renewal"));
		return new LogicTreeBranch<>(levels, List.of(probability, renewal));
	}

	private static HazardCurveMetadata metadataForTree(FSS_ERF_Config baseConfig, LogicTree<?> tree) {
		HazardCurveMetadata metadata = null;
		for (LogicTreeBranch<?> branch : tree) {
			HazardCurveMetadata branchMetadata = new HazardCurveMetadata(
					FSS_ERF_ConfigLogicTreeNode.forBranch(baseConfig, branch).buildTimeSpan());
			metadata = metadata == null ? branchMetadata : metadata.merge(branchMetadata);
		}
		return metadata;
	}

	@AffectsNone
	private enum ProbabilityNode implements FSS_ERF_ConfigLogicTreeNode {
		POISSON {
			@Override public void apply(FSS_ERF_Config.Builder builder) {
				builder.probabilityModel(FSS_ProbabilityModels.POISSON).startYear(null);
			}
		},
		TIME_DEPENDENT {
			@Override public void apply(FSS_ERF_Config.Builder builder) {
				builder.probabilityModel(FSS_ProbabilityModels.NSHM27).startYear(2026);
			}
		};

		@Override public String getName() { return name(); }
		@Override public String getShortName() { return name(); }
		@Override public String getFilePrefix() { return name(); }
		@Override public double getNodeWeight(LogicTreeBranch<?> fullBranch) { return 1d; }
	}

	@AffectsNone
	private enum RenewalNode implements FSS_ERF_ConfigLogicTreeNode {
		DEFAULT(null), WEIBULL(RenewalModels.WEIBULL);

		private final RenewalModels model;
		private RenewalNode(RenewalModels model) { this.model = model; }
		@Override public void apply(FSS_ERF_Config.Builder builder) { builder.renewalModel(model); }
		@Override public String getName() { return name(); }
		@Override public String getShortName() { return name(); }
		@Override public String getFilePrefix() { return name(); }
		@Override public double getNodeWeight(LogicTreeBranch<?> fullBranch) { return 1d; }
	}

	@AffectsNone
	private enum StartYearNode implements FSS_ERF_ConfigLogicTreeNode {
		YEAR_2026(2026), YEAR_2030(2030);

		private final int year;
		private StartYearNode(int year) { this.year = year; }
		@Override public void apply(FSS_ERF_Config.Builder builder) {
			builder.probabilityModel(FSS_ProbabilityModels.NSHM27).startYear(year);
		}
		@Override public String getName() { return name(); }
		@Override public String getShortName() { return name(); }
		@Override public String getFilePrefix() { return name(); }
		@Override public double getNodeWeight(LogicTreeBranch<?> fullBranch) { return 1d; }
	}
}
