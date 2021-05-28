package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.lang.reflect.Constructor;

import org.opensha.commons.data.Named;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

public abstract class SolutionModule implements Named {
	
	private FaultSystemSolution sol;
	
	public SolutionModule(FaultSystemSolution sol) {
		this.sol = sol;
	}
	
	protected void setSolution(FaultSystemSolution sol) {
		Preconditions.checkState(this.sol == null, "Solution should only be set once");
		this.sol = sol;
	}
	
	public final FaultSystemSolution getSolution() {
		Preconditions.checkNotNull(sol, "Solution is null, module not initialized?");
		return sol;
	}
	
	/**
	 * Returns an un-initialized instance of this module, with only the solution initialized
	 * 
	 * @param <M>
	 * @param clazz
	 * @param sol
	 * @return
	 * @throws Exception
	 */
	public static <M extends SolutionModule> M instance(Class<M> clazz, FaultSystemSolution sol) throws Exception {
		Constructor<M> constructor = clazz.getConstructor();
		
		M module = constructor.newInstance();
		
		module.setSolution(sol);
		
		return module;
	}

}
