package org.opensha.sha.earthquake.faultSysSolution.modules;

import org.opensha.commons.util.modules.helpers.AbstractDoubleArrayCSV_BackedModule;

public class InitialSolution extends AbstractDoubleArrayCSV_BackedModule.Averageable<InitialSolution>
implements BranchAverageableModule<InitialSolution> {

	@SuppressWarnings("unused") // used in deserialization
	private InitialSolution() {
		super();
	}
	
	public InitialSolution(double[] initialSolution) {
		super(initialSolution);
	}

	@Override
	public String getFileName() {
		return "initial_solution.csv";
	}

	@Override
	public String getName() {
		return "Initial (pre-inversion) Rates";
	}

	@Override
	protected InitialSolution averageInstance(double[] avgValues) {
		return new InitialSolution(avgValues);
	}

	@Override
	protected String getIndexHeading() {
		return "Rupture Index";
	}

	@Override
	protected String getValueHeading() {
		return "Initial Rate";
	}

}
