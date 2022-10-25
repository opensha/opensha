package org.opensha.commons.exceptions;

/**
 *  <b>Title:</b> WarningException<p>
 *  <b>Description:</b> Exception thrown when WarningListener set's a
 *  parameter value, to overide calling ParameterChangeEvent<p>
 *
 * Note: These exception subclasses add no new functionality. It's really
 * the class name that is the important information. The name indicates what
 * type of error it is and helps to pinpoint where the error could have occured
 * in the code. It it much easier to see different exception types than have one
 * catchall RuntimeException type.<p>
 *
 * @author     Steven W. Rock
 * @created    February 20, 2002
 * @version    1.0
 */

public final class WarningException extends RuntimeException {

    /** No-arg constructor */
    public WarningException() { super(); }
    /** Constructor that specifies an error message */
    public WarningException( String string ) { super( string ); }
}
