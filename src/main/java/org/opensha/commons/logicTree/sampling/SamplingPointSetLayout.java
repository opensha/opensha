package org.opensha.commons.logicTree.sampling;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.NestedLogicTreeLevel;

/** Describes whether point-set dimensions correspond to direct or recursively expanded logic-tree levels. */
public enum SamplingPointSetLayout {
	/** One point-set dimension for each top-level logic-tree level. */
	DIRECT,
	/** Nested wrapper levels are recursively replaced by the levels of their nested trees. */
	EXPANDED;

	public int dimensions(LogicTree<?> tree) {
		if (tree == null)
			throw new NullPointerException("Logic tree cannot be null");
		if (this == DIRECT)
			return tree.getLevels().size();
		Set<LogicTree<?>> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
		return expandedDimensions(tree, tree.size(), visiting);
	}

	private static int expandedDimensions(LogicTree<?> tree, int expectedRows, Set<LogicTree<?>> visiting) {
		if (!visiting.add(tree))
			throw new IllegalStateException("Cycle encountered while expanding nested logic-tree levels");
		int dimensions = 0;
		for (LogicTreeLevel<?> level : tree.getLevels()) {
			if (level instanceof NestedLogicTreeLevel nestedLevel) {
				LogicTree<?> nestedTree = nestedLevel.getNestedTree();
				if (nestedTree == null)
					throw new IllegalStateException("Nested level " + level.getName() + " returned a null tree");
				if (nestedTree.size() != expectedRows)
					throw new IllegalStateException("Nested tree for level " + level.getName() + " has "
							+ nestedTree.size() + " branches but the sampled tree has " + expectedRows);
				dimensions = Math.addExact(dimensions, expandedDimensions(nestedTree, expectedRows, visiting));
			} else {
				dimensions = Math.incrementExact(dimensions);
			}
		}
		visiting.remove(tree);
		return dimensions;
	}
}
