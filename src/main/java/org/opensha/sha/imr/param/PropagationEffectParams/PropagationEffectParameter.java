package org.opensha.sha.imr.param.PropagationEffectParams;

import org.opensha.commons.data.Site;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditorOld;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;

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

public abstract class PropagationEffectParameter<E> extends AbstractParameter<E> {

    /* *******************/
    /** @todo  Variables */
    /* *******************/

    /* Class name used for debug strings. */
    protected final static String C = "PropagationEffectParameter";
    /* If true prints out debbuging statements */
    protected final static boolean D = false;


    /* ***************************/
    /** @todo  Getters / Setters */
    /* ***************************/
    /* ***************************/
    /** @todo  PropagationEffectParameterAPI Interface */
    /* ***************************/



    /** Sets the independent variables (Site and EqkRupture) then calculates and returns the value */
    public E getValue(EqkRupture eqkRupture, Site site){
    	setValue(eqkRupture, site);
        return super.getValue();
    }

    /** Sets the independent variables (Site and EqkRupture) then calculates and returns the value */
    public E getValue(EqkRupture eqkRupture, Site site, SurfaceDistances distances){
    	setValue(eqkRupture, site, distances);
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
    public void setValue(EqkRupture eqkRupture, Site site){
        if (eqkRupture == null || site == null) {
        	setValue(null);
        } else {
        	setValue(eqkRupture, site, eqkRupture.getRuptureSurface().getDistances(site.getLocation()));
        }
    }
    
    public void setValue(EqkRupture eqkRupture, Site site, SurfaceDistances dists) {
    	if (dists == null && eqkRupture != null && site != null)
    		dists = eqkRupture.getRuptureSurface().getDistances(site.getLocation());
    	if (dists == null) {
    		setValue(null);
    	} else {
    		setValueFromDistances(eqkRupture, site, dists);
    	}
    }

     /** function used to determine which GUI widget to use for editing this parameter in an Applet */
    public String getType() { return C; }
    
    /**
     * Set the parameter value from the given eqkRupture, site, and precomputed distances.
     */
    protected abstract void setValueFromDistances(EqkRupture eqkRupture, Site site, SurfaceDistances dists);


}
