package org.opensha.commons.logicTree;

/**
 * Marks a structural logic-tree level whose choices wrap the aligned branches of another logic tree. When sampling
 * dimensions are expanded, this wrapper contributes no dimension of its own and is replaced by its nested levels.
 */
public interface NestedLogicTreeLevel {

	LogicTree<? extends LogicTreeNode> getNestedTree();
}
