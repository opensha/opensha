package org.opensha.sha.imr.param.PropagationEffectParams;

import org.opensha.commons.data.Site;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditorOld;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.sha.earthquake.EqkRupture;

/**
 * <b>Title:</b> PropagationEffectParameter<p>
 *
 * <b>Description:</b> Propagation Effectg Paraemters
 * deal with special subclass of Parameters that are associated with
 * earthquakes, and know how to calculate their own
 * values from having a Site and EqkRupture set. <p>
 *
 * These values are generally self calculated as opposed
 * t normal Parameters where the values are specifically
 * set calling setValue().<p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public abstract class PropagationEffectParameter<E> extends
		AbstractParameter<E> implements PropagationEffectParameterAPI<E> {

    /* *******************/
    /** @todo  Variables */
    /* *******************/

    /* Class name used for debug strings. */
    protected final static String C = "PropagationEffectParameter";
    /* If true prints out debbuging statements */
    protected final static boolean D = false;

    /** The Site used for calculating the PropagationEffect */
    protected Site site = null;

    /** The EqkRupture used for calculating the PropagationEffect */
    protected EqkRupture eqkRupture = null;


    /* ***************************/
    /** @todo  Getters / Setters */
    /* ***************************/
    /* ***************************/
    /** @todo  PropagationEffectParameterAPI Interface */
    /* ***************************/



    /** Sets the independent variables (Site and EqkRupture) then calculates and returns the value */
    @Override
    public E getValue(EqkRupture eqkRupture, Site site){
        this.eqkRupture = eqkRupture;
        this.site = site;
        calcValueFromSiteAndEqkRup();
        return super.getValue();
    }

    /** Sets the site and recalculates the value. The EqkRupture must have already been set */
//    @Override
//    public E getValue(Site site){
//        this.site = site;
//        calcValueFromSiteAndEqkRup();
//        return this.value;
//    }

    /** Sets the EqkRupture and recalculates the value. The Site must have already been set */
//    @Override
//    public E getValue(EqkRupture eqkRupture){
//        this.eqkRupture = eqkRupture;
//        calcValueFromSiteAndEqkRup();
//        return this.value;
//    }

    /** Sets the independent variables (Site and EqkRupture) then calculates the value */
    @Override
    public void setValue(EqkRupture eqkRupture, Site site){
        this.eqkRupture = eqkRupture;
        this.site = site;
        calcValueFromSiteAndEqkRup();
    }

    /** The EqkRupture and Site must have already been set */
//    @Override
//    public E getValue(){ return this.value; }

    /** Sets the Site and the value is recalculated */
//    @Override
//    public void setSite(Site site){
//        this.site = site;
//        calcValueFromSiteAndEqkRup();
//    }
    /** Returns the Site associated with this Parameter */
//    @Override
//    public Site getSite(){ return site; }

    /** Sets the EqkRupture associated with this Parameter */
//    @Override
//    public void setEqkRupture(EqkRupture eqkRupture){
//        this.eqkRupture = eqkRupture;
//        calcValueFromSiteAndEqkRup();
//    }
    /** Returns the EqkRupture associated with this Parameter */
    @Override
//    public EqkRupture getEqkRupture(){ return eqkRupture; }


     /** function used to determine which GUI widget to use for editing this parameter in an Applet */
    public String getType() { return C; }



    /** Compares the values to see if they are the same, greater than or less than. */
    //public abstract int compareTo(Object obj) throws ClassCastException;

    /** Compares value to see if equal */
//    public boolean equals(Object obj) throws ClassCastException{
//        if( compareTo(obj) == 0) return true;
//        else return false;
//    }

    /**
     * Standard Java function. Creates a copy of this class instance
     * so originaly can not be modified
     */
    //public abstract Object clone();
    
//    public ParameterEditorAPI<Double> getEditor() {
//    	return null;
//    }
    
    /**
     * This is called whenever either the Site or
     * EqkRupture has been changed to
     * update the value stored in this parameter. <p>
     *
     * Subclasses implement this in their own way. This is what
     * differentiates different subclasses.
     */
    protected abstract void calcValueFromSiteAndEqkRup();


}
