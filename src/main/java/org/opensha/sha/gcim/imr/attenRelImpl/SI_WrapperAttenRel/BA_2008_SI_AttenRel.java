package org.opensha.sha.gcim.imr.attenRelImpl.SI_WrapperAttenRel;

import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.BakerJayaram08_ImCorrRel;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SA_InterpolatedWrapperAttenRel.InterpolatedBA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SA_InterpolatedWrapperAttenRel.InterpolatedSA_AttenRelWrapper;

/**
 * <b>Title:</b> BA_2008_SI_AttenRel<p>
 *
 * <b>Description:</b> 
 * 
 * @author     BrendonBradley
 * @created    June, 2010
 * @version    1.0
 */


public class BA_2008_SI_AttenRel
    extends SI_AttenRelWrapper {

  // no arg constructor
	public BA_2008_SI_AttenRel() {
		super();
	}
	
  // Debugging stuff
  public final static String SHORT_NAME = "Betal_08-SI-BA08";
  private static final long serialVersionUID = 1234567890987654353L; 
  public final static String NAME = "Bradley(2008)-SI-BooreAtkinson08";

  /**
   *  This initializes several ParameterList objects.
   */
  public BA_2008_SI_AttenRel(ParameterChangeWarningListener warningListener) {
	  super(warningListener,new InterpolatedBA_2008_AttenRel(warningListener));
	  
	//As BA_2008 is for shallow crustal then use the BakerJayaram NGA SA SA correlation
	  setImCorrRel(new BakerJayaram08_ImCorrRel());
  }
  
  /**
   * get the name of this IMR
   *
   * @return the name of this IMR
   */
  public String getName() {
    return NAME;
  }

  /**
   * Returns the Short Name of each AttenuationRelationship
   * @return String
   */
  public String getShortName() {
    return SHORT_NAME;
  }


}
