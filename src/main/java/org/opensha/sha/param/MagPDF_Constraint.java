package org.opensha.sha.param;

import java.util.ArrayList;

import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 *  <b>Title:</b> MagPDF_Constraint<p>
 *
 *  <b>Description:</b> This extends MagFreqDistConstraint (contains a list of allowed IncrementalMagFreqDists)
 *  by checking that the total rate (or probability for the PDF) is equal to 1.0. <p>

 * @author     Ned Field
 * @created    March, 2006
 * @version    1.0
 */


public class MagPDF_Constraint extends MagFreqDistConstraint {

  /** No-Arg Constructor, constraints are null so all values allowed */
  public MagPDF_Constraint() { super(); }

  /**
   * Constructor that sets the constraints during instantiation.
   * Sets the allowed MagFreqDists in this constraint
   *
   * @param  allowedVals  Vector of strings of allowed MagFreqDists
   */
  public MagPDF_Constraint( ArrayList allowedVals ) {
    allowedMagDists = allowedVals;
  }

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
      if (nullAllowed && (obj == null))return true;
      float totRate = (float) ( (IncrementalMagFreqDist) obj).getTotalIncrRate();
      if (totRate == 1 && isAllowed( ( (IncrementalMagFreqDist) obj).getName()))
        return true;
    }catch(Exception e) {
      // return false in case of CLasscastException
    }
    return false;
  }



  /** Creates a copy of this object instance so the original cannot be altered. */
  public Object clone() {
    ArrayList cloneVector  = new ArrayList();
    int size = allowedMagDists.size();
    for(int i=0; i<size ;++i)
      cloneVector.add(allowedMagDists.get(i));
    MagPDF_Constraint c1 = new MagPDF_Constraint(cloneVector);
    c1.setName( name );
    c1.setNullAllowed( nullAllowed );
    if( !editable ) c1.setNonEditable();
    return c1;
  }

}
