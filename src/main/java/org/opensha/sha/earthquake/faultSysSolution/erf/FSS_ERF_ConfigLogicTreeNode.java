package org.opensha.sha.earthquake.faultSysSolution.erf;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;

import com.google.common.base.Preconditions;

/**
 * A logic-tree node that modifies the {@link FSS_ERF_Config} used for hazard calculation.
 * <p>
 * Implementations should change only the setting or related group of settings represented by the node. All nodes in
 * a branch are applied to a single builder, in branch order, and the configuration is built only after every node has
 * been applied. This allows independent levels, such as probability model, renewal model, and aperiodicity, to compose.
 * A node that replaces a related group of settings is responsible for clearing inherited settings that no longer
 * apply; for example, a Poisson probability-model node should clear the start year and time-dependent overrides.
 * Concrete node types that do not alter fault-system solutions should be annotated with
 * {@link org.opensha.commons.logicTree.AffectsNone}; this allows solution logic trees to share solution files across
 * ERF-only branches.
 */
public interface FSS_ERF_ConfigLogicTreeNode extends LogicTreeNode {

	/** Applies this branch choice to the supplied ERF configuration builder. */
	void apply(FSS_ERF_Config.Builder builder);

	/**
	 * Resolves the ERF configuration for a branch by applying every compatible node to a builder initialized from the
	 * supplied base configuration. Logic-tree choices therefore override corresponding base or command-line settings.
	 */
	static FSS_ERF_Config forBranch(FSS_ERF_Config baseConfig, LogicTreeBranch<?> branch) {
		FSS_ERF_Config.Builder builder = Preconditions.checkNotNull(baseConfig, "Base ERF config cannot be null")
				.toBuilder();
		if (branch != null)
			for (LogicTreeNode node : branch)
				if (node instanceof FSS_ERF_ConfigLogicTreeNode)
					((FSS_ERF_ConfigLogicTreeNode)node).apply(builder);
		return builder.build();
	}
}
