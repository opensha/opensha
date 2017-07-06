package org.opensha.commons.util;

public class ExceptionUtils {
	
	/**
	 * Throws the given exception as a runtime exception. If it already is a runtime
	 * exception then it will be simply cast and thrown, otherwise a new runtime exception
	 * will be created with this as it's cause.
	 * 
	 * @param t
	 */
	public static void throwAsRuntimeException(Throwable t) {
		throw asRuntimeException(t);
	}
	
	/**
	 * Returns the given exception as a runtime exception. If it already is a runtime
	 * exception then it will be simply cast and returned, otherwise a new runtime exception
	 * will be created with this as it's cause.
	 * 
	 * @param t
	 */
	public static RuntimeException asRuntimeException(Throwable t) {
		if (t instanceof RuntimeException)
			return (RuntimeException)t;
		return new RuntimeException(t);
	}

}
