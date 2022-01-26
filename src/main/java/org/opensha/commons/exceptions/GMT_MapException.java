package org.opensha.commons.exceptions;

/**
 *  <b>Title:</b> GMT_MapException<p>
 *  <b>Description:</b> Exception thrown when Error occurs while using GMT to generate the
 * PSHA and Shakemaps,such as trying to set invalid depths, such as a negative number<p>
 *
 * Note: These exception subclasses add no new functionality. It's really
 * the class name that is the important information. The name indicates what
 * type of error it is and helps to pinpoint where the error could have occured
 * in the code. It it much easier to see different exception types than have one
 * catchall Exception type.<p>
 *
 * @author     Steven W. Rock
 * @created    February 20, 2002
 * @version    1.0
 */
public class GMT_MapException extends Exception {

    /** No-arg constructor */
    public GMT_MapException()  { super(); }
    /** Constructor that specifies an error message */
    public GMT_MapException( String string ) { super( string ); }
    public GMT_MapException(Exception e) {super(e); }
    public GMT_MapException(String message, Exception e) {super(message, e); }
}


