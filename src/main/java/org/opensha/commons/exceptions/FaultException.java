package org.opensha.commons.exceptions;

/**
 * <b>Title:</b> FaultException<p>
 * <b>Description: </b> Errors thrown when creating fault models.<p>
 *
 * Note: These exception subclasses add no new functionality. It's really
 * the class name that is the important information. The name indicates what
 * type of error it is and helps to pinpoint where the error could have occured
 * in the code. It it much easier to see different exception types than have one
 * catchall RuntimeException type.<p>
 *
 * @author unascribed
 * @version 1.0
 */

public class FaultException extends RuntimeException {

    /** No-arg constructor */
    public FaultException()  { super(); }
    /** Constructor that specifies an error message */
    public FaultException( String string ) { super( string ); }

}
