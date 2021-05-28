package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.lang.reflect.Constructor;

import org.opensha.commons.data.Named;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;

/**
 * Abstract base class for a rupture that can be attached to a FaultSystemRupSet
 * 
 * @author kevin
 *
 */
public abstract class RupSetModule implements Named {
	
	private FaultSystemRupSet rupSet;
	
	public RupSetModule(FaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
	}
	
	protected void setRupSet(FaultSystemRupSet rupSet) {
		Preconditions.checkState(this.rupSet == null, "Rupture set should only be set once");
		this.rupSet = rupSet;
	}
	
	public final FaultSystemRupSet getRupSet() {
		Preconditions.checkNotNull(rupSet, "Rupture set is null, module not initialized?");
		return rupSet;
	}
	
	/**
	 * Returns an un-initialized instance of this module, with only the rupture set initialized
	 * 
	 * @param <M>
	 * @param clazz
	 * @param rupSet
	 * @return
	 * @throws Exception
	 */
	public static <M extends RupSetModule> M instance(Class<M> clazz, FaultSystemRupSet rupSet) throws Exception {
		Constructor<M> constructor = clazz.getConstructor();
		
		M module = constructor.newInstance();
		
		module.setRupSet(rupSet);
		
		return module;
	}

}
