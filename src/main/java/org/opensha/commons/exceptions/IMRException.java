package org.opensha.commons.exceptions;

/**
 *  <b>Title:</b> IMRException<p>
 *
 *  <b>Description:</b> Exception thrown when constraints reject setting a
 *  Parameter value inside the IMR GUI Tester<p>
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

public final class IMRException extends RuntimeException {

    /** No-arg constructor */
    public IMRException()  { super(); }
    /** Constructor that specifies an error message */
    public IMRException( String string ) { super( string ); }
}
