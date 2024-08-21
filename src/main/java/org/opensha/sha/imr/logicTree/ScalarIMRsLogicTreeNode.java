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
	
	/**
	 * 
	 */
	public static interface SingleTRT extends ScalarIMRsLogicTreeNode, Supplier<ScalarIMR> {
		
		public AttenRelSupplier getSupplier();
		
		public default ScalarIMR get() {
			ScalarIMR imr = getSupplier().get();
			return imr;
		}
		
		public TectonicRegionType getTectonicRegion();

		@Override
		default Map<TectonicRegionType, Supplier<ScalarIMR>> getSuppliers() {
			EnumMap<TectonicRegionType, Supplier<ScalarIMR>> ret = new EnumMap<>(TectonicRegionType.class);
			ret.put(getTectonicRegion(), getSupplier());
			return ret;
		}

		@Override
		default Supplier<ScalarIMR> getSupplier(TectonicRegionType trt) {
			if (trt == getTectonicRegion())
				return getSupplier();
			return null;
		}
		
		@Override
		public default String getShortName() {
			return getSupplier().getShortName();
		}

		@Override
		public default String getName() {
			return getSupplier().getName();
		}
	}
	
	/**
	 * Use this for a single model to be used with any and all {@link TectonicRegionType}s
	 */
	public static interface SingleModel extends SingleTRT {
		
		public AttenRelSupplier getSupplier();
		
		public default ScalarIMR get() {
			ScalarIMR imr = getSupplier().get();
			return imr;
		}
		
		default TectonicRegionType getTectonicRegion() {
			return TectonicRegionType.ACTIVE_SHALLOW;
		}

		@Override
		default Supplier<ScalarIMR> getSupplier(TectonicRegionType trt) {
			return getSupplier();
		}
	}

}
