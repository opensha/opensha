package org.opensha.sha.param;

import java.util.ArrayList;

import org.opensha.commons.param.constraint.AbstractParameterConstraint;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 *  <b>Title:</b> MagFreqDistConstraint<p>
 *
 *  <b>Description:</b> Constraint Object containing IncrementalMagFreqDist object and
 *   list of allowed MagFreqDists to be shown in GUI <p>

 * @author     Vipin Gupta, Nitin Gupta
 * @created    Oct 16, 2002
 * @version    1.0
 */


public class MagFreqDistConstraint extends AbstractParameterConstraint {

  /** Class name for debugging. */
  protected final static String C = "MagFreqDistConstraint";
  /** If true print out debug statements. */
  protected final static boolean D = false;
  /**Vector of Distribution names to be shown */
  ArrayList allowedMagDists = new ArrayList();

  /** No-Arg Constructor, constraints are null so all values allowed */
  public MagFreqDistConstraint() { super(); }

  /**
   * Constructor that sets the constraints during instantiation.
   * Sets the allowed MagFreqDists in this constraint
   *
   * @param  allowedVals  Vector of strings of allowed MagFreqDists
   */
  public MagFreqDistConstraint( ArrayList allowedVals ) {
    allowedMagDists = allowedVals;
  }

  /** Returns the vector of allowed Mag Dists  */
  public ArrayList getAllowedMagDists() { return allowedMagDists; }


  /**
   * Checks if the passed in distribution name is allowed
   * First the value is chekced if it's null and null values
   * are allowed. Then it checks the passed in object is an String.
   *
   * @param  obj  The object to check if allowed.
   * @return      True if this is an String and one of the allowed values.
   */
  public boolean isAllowed( Object obj ) {
    try {
      if( nullAllowed && ( obj == null ) ) return true;
      else  return isAllowed(  ((IncrementalMagFreqDist)obj).getName() );
    }catch(Exception e) {
      // return false in case of CLasscastException
      return false;
    }
  }

  /**
   * Checks if the passed in distribution name is allowed
   * First the value is chekced if it's null and null values
   * are allowed. Then it checks the passed in object is an String.
   *
   * @param  distName  Distribution name
   * @return      True if this is an String and one of the allowed values.
   */
  public boolean isAllowed( String distName ) {
    if( nullAllowed && ( distName == null ) ) return true;
    // if we allow all
    if( allowedMagDists.size() == 0 ) return true;
    // if this MagDist is allowed
    else if( allowedMagDists.indexOf(distName)!=-1 )
      return true;
    else return false;
  }



  /** Returns the classname of the constraint, and the allowed Mag dist Names
   *  as a debug string */
  public String toString() {
    String TAB = "    ";
    StringBuffer b = new StringBuffer();
    b.append( C );
    if( name != null) b.append( TAB + "Name = " + name + '\n' );
    int size=this.allowedMagDists.size();
    if(size==0)
      b.append( TAB + "All Mag Dists are allowed" + '\n' );
    else
      b.append(TAB + "Allowed MagDists are: ") ;
    for(int i=0; i<size; ++i)
      b.append( (String)allowedMagDists.get(i)+ TAB );
    return b.toString();
  }


  /** Creates a copy of this object instance so the original cannot be altered. */
  public Object clone() {
    ArrayList cloneVector  = new ArrayList();
    int size = allowedMagDists.size();
    for(int i=0; i<size ;++i)
      cloneVector.add(allowedMagDists.get(i));
    MagFreqDistConstraint c1 = new MagFreqDistConstraint(cloneVector);
    c1.setName( name );
    c1.setNullAllowed( nullAllowed );
    if( !editable ) c1.setNonEditable();
    return c1;
  }

}
