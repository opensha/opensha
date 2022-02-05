package org.opensha.sha.imr.logicTree;

import java.util.function.Supplier;

import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;

/**
 * LogicTreeNode sub-interface that supplies {@link AttenRelRef} and {@link ScalarIMR} instances, useful for specifying
 * ground motion models in logic trees for hazard calculation.
 * 
 * @author kevin
 *
 */
public interface ScalarIMR_LogicTreeNode extends LogicTreeNode, Supplier<ScalarIMR> {
	
	public AttenRelRef getRef();
	
	public default ScalarIMR get() {
		ScalarIMR imr = getRef().instance(null);
		imr.setParamDefaults();
		return imr;
	}

	@Override
	public default String getShortName() {
		return getRef().getShortName();
	}

	@Override
	public default String getName() {
		return getRef().getName();
	}

}
