package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.List;

import org.opensha.commons.util.modules.helpers.AbstractDoubleArrayCSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

public class SolutionSlipRates extends AbstractDoubleArrayCSV_BackedModule.Averageable<SolutionSlipRates>
implements BranchAverageableModule<SolutionSlipRates> {
	
	public static SolutionSlipRates calc(FaultSystemSolution sol, AveSlipModule aveSlips, SlipAlongRuptureModel slipAlong) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		System.out.println("Calculating slip rates for "+rupSet.getNumSections()+" sections and "
				+rupSet.getNumRuptures()+" ruptures with "+aveSlips.getName()+" and "+slipAlong.getName());
		double[] slipRates = new double[rupSet.getNumSections()];
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			List<Integer> indices = rupSet.getSectionsIndicesForRup(r);
			double[] rupSlips = slipAlong.calcSlipOnSectionsForRup(rupSet, aveSlips, r);
			double rate = sol.getRateForRup(r);
			for (int s=0; s<rupSlips.length; s++)
				slipRates[indices.get(s)] += rate*rupSlips[s];
		}
		
		return new SolutionSlipRates(slipRates);
	}
	
	private SolutionSlipRates() {} // for deserialization
	
	public SolutionSlipRates(double[] slipRates) {
		super(slipRates);
	}

	@Override
	public String getName() {
		return "Solution Slip Rates";
	}

	@Override
	public String getFileName() {
		return "sol_slip_rates.csv";
	}

	@Override
	protected SolutionSlipRates averageInstance(double[] avgValues) {
		return new SolutionSlipRates(avgValues);
	}

	@Override
	protected String getIndexHeading() {
		return "Rupture Index";
	}

	@Override
	protected String getValueHeading() {
		return "Solution Slip Rate (m/yr)";
	}

}
