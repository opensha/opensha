package org.opensha.sha.calc.mcer;

public interface WeightProvider {
	
	/**
	 * @param calc
	 * @param period
	 * @return relative weight for the given calculator/period
	 */
	public double getProbWeight(AbstractMCErProbabilisticCalc calc, double period);
	
	/**
	 * @param calc
	 * @param period
	 * @return relative weight for the given calculator/period
	 */
	public double getDetWeight(AbstractMCErDeterministicCalc calc, double period);

}
