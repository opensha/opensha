package org.opensha.sha.earthquake.faultSysSolution;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.modules.ModuleManager;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionModule;

import com.google.common.base.Preconditions;

/**
 * This class represents an Earthquake Rate Model solution for a fault system, possibly coming from an Inversion
 * or from a physics-based earthquake simulator.
 * <p>
 * It adds rate information to a FaultSystemRupSet.
 * 
 * @author Field, Milner, Page, and Powers
 *
 */
public final class FaultSystemSolution extends ModuleManager<SolutionModule> {
	// TODO: should this be final?
	// TODO: add MFD calculation methods, or throw them in a utility class instead?

	private FaultSystemRupSet rupSet;
	private double[] rates;
	// this is separate from the rupSet info string as you can have multiple solutions with one rupSet
	private String infoString;
	
	public FaultSystemSolution(FaultSystemRupSet rupSet, double[] rates) {
		super(SolutionModule.class);
		this.rupSet = rupSet;
		this.rates = rates;
		Preconditions.checkArgument(rates.length == rupSet.getNumRuptures(), "# rates and ruptures is inconsistent!");
	}
	
	/**
	 * Returns the fault system rupture set for this solution
	 * @return
	 */
	public FaultSystemRupSet getRupSet() {
		return rupSet;
	}
	
	/**
	 * These gives the long-term rate (events/yr) of the rth rupture
	 * @param rupIndex
	 * @return
	 */
	public double getRateForRup(int rupIndex) {
		return rates[rupIndex];
	}
	
	/**
	 * This gives the long-term rate (events/yr) of all ruptures
	 * @param rupIndex
	 * @return
	 */
	public double[] getRateForAllRups() {
		return rates;
	}
	
	/**
	 * This returns the total long-term rate (events/yr) of all fault-based ruptures
	 * (fault based in case off-fault ruptures are added to subclass)
	 * @return
	 */
	public double getTotalRateForAllFaultSystemRups() {
		double totRate=0;
		for(double rate:getRateForAllRups())
			totRate += rate;
		return totRate;
	}
	
	public String getInfoString() {
		return infoString;
	}

	public void setInfoString(String infoString) {
		this.infoString = infoString;
	}
	
	/*
	 * Modules
	 */
	
	/**
	 * Adds the given module to this rupture set
	 * 
	 * @param module
	 */
	@Override
	public void addModule(SolutionModule module) {
		Preconditions.checkNotNull(module.getSolution());
		Preconditions.checkState(module.getSolution() == this || module.getSolution().getRupSet() == getRupSet()
				|| getRupSet().areRupturesEquivalent(module.getSolution().getRupSet()),
				"This module was created with a different solution, and that solution's rupture set is not equivalent.");
		super.addModule(module);
	}
	
	/**
	 * Temporary method to transform this to the old version, to reduce compile errors during initial refactoring
	 * 
	 * @return
	 */
	@Deprecated
	public scratch.UCERF3.FaultSystemSolution toOldSol() {
		scratch.UCERF3.FaultSystemSolution old = new scratch.UCERF3.FaultSystemSolution(
				rupSet.toOldRupSet(), rates);
		return old;
	}
	
}
