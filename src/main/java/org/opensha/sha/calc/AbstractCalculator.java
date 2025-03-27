package org.opensha.sha.calc;

/**
 * All calculators will extend the AbstractCalculator to ensure they
 * are cancellable and can receive a cancellation signal externally.
 */
public abstract class AbstractCalculator implements CalculatorAPI {

	private volatile boolean cancelled = false;

	@Override
	public void stopCalc() {
		cancelled = true;
	}
	
	/**
	 * Resets a cancellation request for the calculator.
	 * This is required at the start of any computation.
	 */
	protected void signalReset() {
		cancelled = false;
	}
	
	/**
	 * Returns cancellation state and then resets to false.
	 * isCancelled is only used inside the calculator, so it
	 * doesn't need to be exposed in the Calculator interface.
	 * @return
	 */
	protected boolean isCancelled() {
		boolean orig = cancelled;
		cancelled = false;
		return orig;
	}
}
