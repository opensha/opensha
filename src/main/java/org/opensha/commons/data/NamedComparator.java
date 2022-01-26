package org.opensha.commons.data;

import java.io.Serializable;
import java.util.Comparator;

/**
 * <b>Title:</b> NamedObjectComparator<p>
 *
 * <b>Description:</b> This class can compare any two objects that implement
 * the NamedObjectAPI and sort them alphabetically. This is useful for passing
 * into a Collections.sort(Collection, Comparator) function call to sort a list
 * alphabetically by named. One example is it's use in the ParameterEditorSheet
 * to edit Parameters.<p>
 *
 * You can set the ascending boolean to true to make the comparison to be sorted
 * acending, else sort comparision is descending.<p>
 *
 *
 * @author     Steven W. Rock
 * @created    February 21, 2002
 * @version    1.0
 */

public class NamedComparator implements Comparator<Named>, Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/** Class name for debugging. */
    final static String C = "NamedObjectComparator";
    /** If true print out debug statements. */
    final static boolean D = false;

    /** If true comparision sort ascending, else comparision sort descending. */
    private boolean ascending = true;

    /** Set's the comparation to ascending if true, else descending.  */
    public void setAscending( boolean a ) { ascending = a; }

    /** Returns true if comparision is ascending, false for descending. */
    public boolean isAscending() { return ascending; }


    /**
     *  Compares two NamedObject objects by name, which both implement
     *  comparable. Throws an exception if either comparing object is not an
     *  NamedObjects. Only the names of these objects are examined for
     *  comparison. This function allows sorting of named objects
     *  alphabetically.
     *
     * @param  o1                        First object to compare
     * @param  o2                        Second object to compare
     * @return                           +1 if the first object name > second
     *      object name, 0 if the two names are equal, and -1 if the first
     *      object name is < the second object's name, alphabetically.
     * @see                              Comparable
     * @see                              Named
     */
    public int compare( Named o1, Named o2 ) {

        String S = C + ":compare(): ";
        if ( D ) {
            System.out.println( S + "Starting" );
        }
        int result = 0;

        if ( D ) {
            System.out.println( S + "O1 = " + o1.toString() );
            System.out.println( S + "O2 = " + o2.toString() );
            System.out.println( S + "Getting the names: " + o1.getClass().getName() + ", " + o2.getClass().getName() );
        }


        Named no1 = ( Named ) o1;
        Named no2 = ( Named ) o2;

        String n1 = no1.getName().toString();
        String n2 = no2.getName().toString();

        result = n1.compareTo( n2 );

        if ( ascending ) return result;
        else return -result;

    }

}
