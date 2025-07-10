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
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

/**
 * <b>Title:</b> DistanceSeisParameter<p>
 *
 * <b>Description:</b> Special subclass of PropagationEffectParameter.
 * This computes the closest distance to the seimogenic part of the fault;
 * that is, the closest distance to the part of the fault that is below the seimogenic
 * thickness (seisDepth); this depth is currently hardwired at 3 km, but we can add
 * setSeisDepth() and getSeisDepth() methods if desired (the setter will have to create
 * a new constraint with seisDepth as the lower bound, which can be done even if the
 * parameter has been set as non editable).  Note that if the earthquake rupture is a
 * line or point source where the depths are less than seisDepth, then the depths are
 * treated as seisDepth (e.g., for grid based forecast where all sources are put at
 * zero depth) <p>
 * 
 * NOTE: this was abandoned after CB 2003 and is no longer calculated by surfaces.
 * We approximate it with 
 *
 * @see DistanceRupParameter
 * @see DistanceJBParameter
 * @author Steven W. Rock
 * @version 1.0
 */
@Deprecated
public class DistanceSeisParameter extends AbstractDoublePropEffectParam {

	/** Class name used in debug strings */
	protected final static String C = "DistanceSeisParameter";
	/** If true debug statements are printed out */
	protected final static boolean D = false;


	/** Hardcoded name */
	public final static String NAME = "DistanceSeis";
	/** Hardcoded units string */
	private final static String UNITS = "km";
	/** Hardcoded info string */
	private final static String INFO = "Seismogenic Distance (closest distance to seismogenic part of fault surface)";

	/** Hardcoded max allowed value */
	private final static Double MAX = Double.valueOf(Double.MAX_VALUE);

	/** set default seismogenic depth. actually hard-wired for now. */
	public final static double SEIS_DEPTH = GriddedSurfaceUtils.SEIS_DEPTH;


	/**
	 * No-Arg constructor that just calls init() with null constraints.
	 * All value are allowed.
	 */
	public DistanceSeisParameter() {
		super(NAME);
		init();
	}

	/** This constructor sets the default value.  */
	public DistanceSeisParameter(double defaultValue) { 
		super(NAME);
		init(); 
		this.setDefaultValue(defaultValue);
	}


	/** Constructor that sets up constraints. This is a constrained parameter. */
	public DistanceSeisParameter(ParameterConstraint warningConstraint)
			throws ConstraintException
	{
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
	public DistanceSeisParameter(ParameterConstraint warningConstraint, double defaultValue)
			throws ConstraintException
	{
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



	/** Initializes the constraints, name, etc. for this parameter */
	protected void init( DoubleConstraint warningConstraint){
		this.warningConstraint = warningConstraint;
		this.constraint = new DoubleConstraint(SEIS_DEPTH, Double.MAX_VALUE );
		this.name = NAME;
		this.constraint.setName( this.name );
		this.constraint.setNullAllowed(false);
		this.units = UNITS;
		this.info = INFO;
		//setNonEditable();
	}

	/** Initializes the constraints, name, etc. for this parameter */
	protected void init(){ init( null ); }

	/**
	 * Note that this does not throw a warning
	 */
	protected void calcValueFromSiteAndEqkRup(){
		if( ( site != null ) && ( eqkRupture != null ) ) {
			setValueIgnoreWarning(estimateDistanceSeis(eqkRupture.getRuptureSurface(), site.getLocation()));
		} else {
			setValue(null);
		}

	}
	
	private static final double SEIS_DEPTH_SQ = SEIS_DEPTH*SEIS_DEPTH;

	/**
	 * We no longer calculate DistanceSeis as a standard distance metric because it is only used by a couple
	 * ancient models. Instead, this can be used to approximate it from rJB, rRup, zTop, and dip.
	 * 
	 * @param surf
	 * @param loc
	 * @return
	 */
	public static double estimateDistanceSeis(RuptureSurface surf, Location loc) {
		double zTop = surf.getAveRupTopDepth();
		SurfaceDistances dists = surf.getDistances(loc);
		double rRup = dists.getDistanceRup();
		if (zTop >= SEIS_DEPTH) {
			// already below, return rRup
			return rRup;
		}
		double rJB = dists.getDistanceJB();
		double dip = surf.getAveDip();
		if (dip > 89) {
			// vertical, just use rJB and SEIS_DEPTH
			return Math.sqrt(rJB*rJB + SEIS_DEPTH_SQ);
		}
		
		// estimate it as a correction from rJB and rRup
		
		// infer the depth (z*) of the original closest‐point
		double zStar = Math.sqrt(Math.max(0.0, rRup*rRup - rJB*rJB));

		// already below, return rRup
		if (zStar >= SEIS_DEPTH) {
			return rRup;
		}

		// compute how far you must slide *horizontally* down‐dip to go from z* down to SEIS_DEPTH
		double dipRad = Math.toRadians(dip);
		double dh = (SEIS_DEPTH - zStar) / Math.tan(dipRad);

		// 4. shift your horizontal distance and compute the new 3D distance
		double rJBeff = rJB + dh;
		return Math.sqrt(rJBeff*rJBeff + SEIS_DEPTH_SQ);
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

		DistanceSeisParameter param = new DistanceSeisParameter(  );
		param.info = info;
		param.value = val2;
		param.constraint = c1;
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
