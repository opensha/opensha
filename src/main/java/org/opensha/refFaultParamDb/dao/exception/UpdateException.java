package org.opensha.refFaultParamDb.dao.exception;

/**
 * <p>Title: UpdateException.java </p>
 * <p>Description: This exception is thown when a update/delete operation
 * fails </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class UpdateException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public UpdateException() {
	}

	public UpdateException(String message) {
		super(message);
	}

	public UpdateException(String message, Throwable cause) {
		super(message, cause);
	}

	public UpdateException(Throwable cause) {
		super(cause);
	}
}
