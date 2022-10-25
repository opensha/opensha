package org.opensha.refFaultParamDb.dao.exception;

/**
 * <p>Title: QueryException.java </p>
 * <p>Description: This exception is thrown when a query operation fails </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class QueryException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public QueryException() {
	}

	public QueryException(String message) {
		super(message);
	}

	public QueryException(String message, Throwable cause) {
		super(message, cause);
	}

	public QueryException(Throwable cause) {
		super(cause);
	}
}
