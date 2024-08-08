package org.opensha.sha.imr.logicTree;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 * LogicTreeNode sub-interface that supplies {@link AttenRelRef} and {@link ScalarIMR} instances, useful for specifying
 * ground motion models in logic trees for hazard calculation.
 * 
 * @author kevin
 *
 */
public interface ScalarIMRsLogicTreeNode extends LogicTreeNode {
	
	public Map<TectonicRegionType, Supplier<ScalarIMR>> getSuppliers();
	
	public Supplier<ScalarIMR> getSupplier(TectonicRegionType trt);
	
	public AttenRelSupplier getSupplier();
	
	public default ScalarIMR get() {
		ScalarIMR imr = getSupplier().get();
		return imr;
	}

	@Override
	public default String getShortName() {
		return getSupplier().getShortName();
	}

	@Override
	public default String getName() {
		return getSupplier().getName();
	}
	
	public static interface Single extends ScalarIMRsLogicTreeNode, Supplier<ScalarIMR> {
		
		public AttenRelSupplier getSupplier();
		
		public default ScalarIMR get() {
			ScalarIMR imr = getSupplier().get();
			return imr;
		}

		@Override
		default Map<TectonicRegionType, Supplier<ScalarIMR>> getSuppliers() {
			EnumMap<TectonicRegionType, Supplier<ScalarIMR>> ret = new EnumMap<>(TectonicRegionType.class);
			ret.put(TectonicRegionType.ACTIVE_SHALLOW, getSupplier());
			return ret;
		}

		@Override
		default Supplier<ScalarIMR> getSupplier(TectonicRegionType trt) {
			return getSupplier();
		}
	}

}
