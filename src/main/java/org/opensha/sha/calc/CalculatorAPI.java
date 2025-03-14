package org.opensha.sha.calc;

/**
 * The generic CalculatorAPI enforces a standard set of methods that are
 * applicable to all types of Calculators. All other Calculator APIs will
 * implement the generic Calculator API.
 */
public interface CalculatorAPI {
	/**
	 * Stops the current calculation.
	 * Calculator can be reused for future calculations.
	 */
	public void stopCalc();
}
