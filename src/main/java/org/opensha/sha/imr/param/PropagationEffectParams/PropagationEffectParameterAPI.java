package org.opensha.sha.imr.param.PropagationEffectParams;

import org.opensha.commons.data.Site;
import org.opensha.sha.earthquake.EqkRupture;

/**
* <p>Title: PropagationEffectParameterAPI</p>
* <p>Description: Interface that PropagationEffect
* Parameters must implement. </p>
*
* Propagation Effect Parameters are a specific subclass
* of parameters that deal with earthquake probability
* variables. Their defining characteristics are that
* they take two independent variables, a Site and ProbEqkRupture
* and then can calculate their own value. Their use is distinct
* from regular parameters in that setValue() is typically
* not called. That is the only way to set standard
* parameters. <p>
*
* This API defines several gatValue() functions that take
* different combinations of Site and ProbEqkRupture that
* will make this parameter recalculate itself, returning the
* new value. <p>
*
* @author Steven W. Rock
* @version 1.0
*/
public interface PropagationEffectParameterAPI<E> {


    /** The EqkRupture and Site must have already been set */
    //public E getValue();

    /** Sets the independent variables (Site and eqkRupture) then calculates and returns the value */
    public E getValue(EqkRupture eqkRupture, Site site);

    /** Sets the site and recalculates the value. The ProbEqkRupture must have already been set */
    //public E getValue(Site site);

    /** Sets the EqkRupture and recalculates the value. The Site must have already been set */
    //public E getValue(EqkRupture eqkRupture);

    /** Sets the independent variables (Site and EqkRupture) then calculates the value */
    public void setValue(EqkRupture eqkRupture, Site site);


    /** Sets the Site and the value is recalculated */
    //public void setSite(Site site);
    /** Returns the Site that set this value */
    //public Site getSite();

    /** Sets the EqkRupture associated with this Parameter, and the value is recalculated */
    //public void setEqkRupture(EqkRupture eqkRupture);
    /** Returns the EqkRupture that set this value */
    //public EqkRupture getEqkRupture();

    /**
     * Standard Java function. Creates a copy of this class instance
     * so originaly can not be modified
     */
    //public Object clone();

}
