package org.opensha.commons.exceptions;

/**
 * <b>Title:</b> EditableException<p>
 *
 * <b>Description:</b> SWR: I have no idea what this is used for since
 * I didn't create the class, and the creator left no comments<p>
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

public class EditableException extends RuntimeException {

    /** No-arg constructor */
    public EditableException()  { super(); }
    /** Constructor that specifies an error message */
    public EditableException( String string ) { super( string ); }
}
