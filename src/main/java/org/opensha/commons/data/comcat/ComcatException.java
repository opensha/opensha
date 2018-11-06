package org.opensha.commons.data.comcat;

/**
 * Exception class for Comcat access errors.
 * Author: Michael Barall 05/29/2018.
 */
public class ComcatException extends RuntimeException {

	// Constructors.

	public ComcatException () {
		super ();
	}

	public ComcatException (String s) {
		super (s);
	}

	public ComcatException (String message, Throwable cause) {
		super (message, cause);
	}

	public ComcatException (Throwable cause) {
		super (cause);
	}

}
