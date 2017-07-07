package org.opensha.sra.gui.portfolioeal;

public interface CalculationExceptionHandler {
	
	/**
	 * Exceptions occur during a calculation; this method gets the program back to its start state
	 * 
	 * @param errorMessage The string representation of the exception's error message.
	 */
	public void calculationException( String errorMessage );

}
