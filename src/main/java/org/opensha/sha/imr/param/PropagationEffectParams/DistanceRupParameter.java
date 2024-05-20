package org.opensha.sha.imr.param.PropagationEffectParams;

import java.util.ListIterator;

import org.dom4j.Element;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;


/**
 * <b>Title:</b> DistanceRupParameter<p>
 *
 * <b>Description:</b> Special subclass of PropagationEffectParameter.
 * This finds the shortest distance to the fault surface. <p>
 *
 * @see DistanceJBParameter
 * @see DistanceSeisParameter
 * @author Steven W. Rock
 * @version 1.0
 */
public class DistanceRupParameter extends AbstractDoublePropEffectParam {

	/** Class name used in debug strings */
	protected final static String C = "DistanceRupParameter";
	/** If true debug statements are printed out */
	protected final static boolean D = false;


	/** Hardcoded name */
	public final static String NAME = "DistanceRup";
	/** Hardcoded units string */
	public final static String UNITS = "km";
	/** Hardcoded info string */
	public final static String INFO = "Rupture Distance (closest distance to fault surface)";
	/** Hardcoded min allowed value */
	private final static Double MIN = Double.valueOf(0.0);
	/** Hardcoded max allowed value */
	private final static Double MAX = Double.valueOf(Double.MAX_VALUE);


	/** No-Arg constructor that calls init(). No constraint so all values are allowed.  */
	public DistanceRupParameter() {
		super(NAME);
		init();
	}
	
	/** This constructor sets the default value.  */
	public DistanceRupParameter(double defaultValue) { 
		super(NAME);
		init(); 
		this.setDefaultValue(defaultValue);
	}



	/** Constructor that sets up constraints. This is a constrained parameter. */
	public DistanceRupParameter(ParameterConstraint warningConstraint)
		throws ConstraintException {
		super(NAME);
		if( ( warningConstraint != null ) && !( warningConstraint instanceof DoubleConstraint) ){
			throw new ConstraintException(
					C + " : Constructor(): " +
					"Input constraint must be a DoubleConstraint"
			);
		}
		init( (DoubleConstraint)warningConstraint );
	}
	
    /** Constructor that sets up constraints & the default value. This is a constrained parameter. */
	public DistanceRupParameter(ParameterConstraint warningConstraint,
		double defaultValue) throws ConstraintException {
		super(NAME);
        if( ( warningConstraint != null ) && !( warningConstraint instanceof DoubleConstraint) ){
            throw new ConstraintException(
                C + " : Constructor(): " +
                "Input constraint must be a DoubleConstraint"
            );
        }
        init( (DoubleConstraint)warningConstraint );
        setDefaultValue(defaultValue);
    }


	/** Sets default fields on the Constraint,  such as info and units. */
	protected void init( DoubleConstraint warningConstraint){
		this.warningConstraint = warningConstraint;
		this.constraint = new DoubleConstraint(MIN, MAX );
		this.constraint.setNullAllowed(false);
		this.name = NAME;
		this.constraint.setName( this.name );
		this.units = UNITS;
		this.info = INFO;
		//setNonEditable();
	}

	/** Sets the warning constraint to null, then initializes the absolute constraint */
	protected void init(){ init( null ); }


	/**
	 * Note that this doesn't not throw a warning
	 */
	protected void calcValueFromSiteAndEqkRup(){
		if( ( site != null ) && ( eqkRupture != null ))
			setValueIgnoreWarning(eqkRupture.getRuptureSurface().getDistanceRup(site.getLocation()));
		else
			value = null;
	}
	
	
	/**
	 * This is a utility method that is not actually used in this class (it's for other classes).
	 * Note that this assumes the location is at the earth's surface (or at least above the rupSurface)
	 * @param loc
	 * @param rupSurf
	 * @return
	 */
	public static double getDistance(Location loc, RuptureSurface rupSurf) {
		double minDistance = Double.MAX_VALUE;
		double horzDist, vertDist, totalDist;
		
		// get locations to iterate over depending on dip
		ListIterator it;
		if(rupSurf.getAveDip() > 89)
			it = rupSurf.getEvenlyDiscritizedUpperEdge().listIterator();
		else
			it = rupSurf.getLocationsIterator();

		while( it.hasNext() ){
			Location loc2 = (Location) it.next();
			horzDist = LocationUtils.horzDistance(loc, loc2);
			vertDist = LocationUtils.vertDistance(loc, loc2);
			totalDist = horzDist * horzDist + vertDist * vertDist;
			if( totalDist < minDistance ) minDistance = totalDist;
		}				

		return Math.pow ( minDistance , 0.5 );

	}
	

	/** This is used to determine what widget editor to use in GUI Applets.  */
	public String getType() {
		String type = "DoubleParameter";
		// Modify if constrained
		ParameterConstraint constraint = this.constraint;
		if (constraint != null) type = "Constrained" + type;
		return type;
	}


	/**
	 *  Returns a copy so you can't edit or damage the origial.<P>
	 *
	 * Note: this is not a true clone. I did not clone Site or ProbEqkRupture.
	 * PE could potentially have a million points, way to expensive to clone. Should
	 * not be a problem though because once the PE and Site are set, they can not
	 * be modified by this class. The clone has null Site and PE parameters.<p>
	 *
	 * This will probably have to be changed in the future once the use of a clone is
	 * needed and we see the best way to implement this.
	 *
	 * @return    Exact copy of this object's state
	 */
	public Object clone() {

		DoubleConstraint c1 = null;
		DoubleConstraint c2 = null;

		if( constraint != null ) c1 = ( DoubleConstraint ) constraint.clone();
		if( warningConstraint != null ) c2 = ( DoubleConstraint ) warningConstraint.clone();

		Double val = null, val2 = null;
		if( value != null ) {
			val = ( Double ) this.value;
			val2 = Double.valueOf( val.doubleValue() );
		}

		DistanceRupParameter param = new DistanceRupParameter(  );
		param.value = val2;
		param.constraint =  c1;
		param.warningConstraint = c2;
		param.name = name;
		param.info = info;
		param.site = site;
		param.eqkRupture = eqkRupture;
		if( !this.editable ) param.setNonEditable();
		return param;
	}

	public boolean setIndividualParamValueFromXML(Element el) {
		// TODO Auto-generated method stub
		return false;
	}


}
