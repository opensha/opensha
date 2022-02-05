package org.opensha.sha.imr.logicTree;

import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.imr.ScalarIMR;

/**
 * {@link LogicTreeNode} implementation that sets parameters on a {@link ScalarIMR} for hazard calculation.
 * 
 * @author kevin
 *
 */
public interface ScalarIMR_ParamsLogicTreeNode extends LogicTreeNode {

	/**
	 * @param imr
	 * @return true if this node is applicable to the given imr, false otherwise
	 */
	public boolean isApplicableTo(ScalarIMR imr);
	
	/**
	 * Sets parameters in the supplied {@link ScalarIMR} for this logic tree node
	 * 
	 * @param imr
	 * @throws IllegalStateException if {@link #isApplicableTo(ScalarIMR)} is false
	 */
	public void setParams(ScalarIMR imr);
}
