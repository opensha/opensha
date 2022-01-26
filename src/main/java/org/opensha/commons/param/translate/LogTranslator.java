package org.opensha.commons.param.translate;


/**
 * <b>Title:</b> LogTranslator
 * <p>
 * 
 * <b>Description:</b> Translates values into the log space and back. Throws
 * translate errors when trying to take the log of negative or zero values.
 * <p>
 * 
 * Implementation of a translation framework. These concrete TranslatorAPI
 * classes can be passed into a TranslatedParameter, then the parameter will use
 * this class to translate values when getting and setting values in the
 * parameter.
 * <p>
 * 
 * This one instance is used to let users deal with the log of a value in the
 * IMRTesterApplet, but the IMR when it does it's calculation it uses the normal
 * space values.
 * <p>
 * 
 * @author Steven W. Rock
 * @version 1.0
 */

public class LogTranslator implements TranslatorAPI {

	/**
	 * Takes the log of a positive value > 0, else throws TranslateException.
	 * Translates values from normal to log space.
	 */
	public double translate(double val) {
		if (val <= 0)
			throw new IllegalArgumentException(
				"Cannot translate zero or negative values into log space.");
		return Math.log(val);
	}

	/**
	 * Takes the inverse log of a number, i.e. Math.exp(val). Translates values
	 * from log space to normal space.
	 */
	public double reverse(double val) {
		// if( val < 0 ) throw new
		// TranslateException("Cannot reverse log negative values from log space.");
		return Math.exp(val);

	}
}
