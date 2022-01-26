package org.opensha.refFaultParamDb.dao.exception;

/**
 * <p>Title: DBConnectException.java </p>
 * <p>Description: This exception is thrown when  connection to the database fails.
 * Connection failure can occur if user provides incorrect username/password or database
 * server is down.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class DBConnectException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public DBConnectException() {
	}

	public DBConnectException(String message) {
		super(message);
	}

	public DBConnectException(String message, Throwable cause) {
		super(message, cause);
	}

	public DBConnectException(Throwable cause) {
		super(cause);
	}
}
