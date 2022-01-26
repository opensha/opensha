package org.opensha.refFaultParamDb.dao.exception;

/**
 * <p>Title: InsertException.java </p>
 * <p>Description: This exception is thrown when an insert method fails to insert
 * the data.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class InsertException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public InsertException() {
	}

	public InsertException(String message) {
		super(message);
	}

	public InsertException(String message, Throwable cause) {
		super(message, cause);
	}

	public InsertException(Throwable cause) {
		super(cause);
	}
}
