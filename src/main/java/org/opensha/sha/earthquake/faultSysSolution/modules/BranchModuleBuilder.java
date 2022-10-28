package org.opensha.sha.earthquake.faultSysSolution.modules;

import org.opensha.commons.util.modules.OpenSHA_Module;

public interface BranchModuleBuilder<T, M extends OpenSHA_Module> {
	
	public void process(T source, double weight);
	
	public M build();

}
