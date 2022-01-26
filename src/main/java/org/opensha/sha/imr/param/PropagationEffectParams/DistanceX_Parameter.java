package org.opensha.sha.imr.param.PropagationEffectParams;

import org.dom4j.Element;
import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.WarningException;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

/**
 * <b>Title:</b> DistanceX_Parameter<p>
 *
 * <b>Description:</b> Special subclass of PropagationEffectParameter.
 * This finds the shortest distance to the rupture trace extended to infinity.  
 * Values >= 0 are on the hanging wall, and values < 0 are on the foot wall.
 * <p>
 *
  * @author Ned Field
 * @version 1.0
 */

public class DistanceX_Parameter extends AbstractDoublePropEffectParam {

    /** Class name used in debug strings */
    protected final static String C = "DistanceJBParameter";
    /** If true debug statements are printed out */
    protected final static boolean D = false;
    
    /** Hardcoded name */
    public final static String NAME = "DistanceX";
    /** Hardcoded units string */
    private final static String UNITS = "km";
    /** Hardcoded info string */
    private final static String INFO = "Horizontal distance to top edge of rupture, measured ppd to strike; neg valuse are on the foot wall";
    /** Hardcoded min allowed value */
    private final static Double MIN = new Double(-1*Double.MAX_VALUE);
    /** Hardcoded max allowed value */
    private final static Double MAX = new Double(Double.MAX_VALUE);


    /**
     * No-Arg constructor that just calls init() with null constraints.
     * All value are allowed.
     */
	public DistanceX_Parameter() {
		super(NAME);
		init();
	}
    
	/** This constructor sets the default value.  */
	public DistanceX_Parameter(double defaultValue) { 
		super(NAME);
		init(); 
		this.setDefaultValue(defaultValue);
	}


    /** Constructor that sets up constraints. This is a constrained parameter. */
    public DistanceX_Parameter(ParameterConstraint warningConstraint)
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
    public DistanceX_Parameter(ParameterConstraint warningConstraint, double defaultValue)
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
        this.constraint = new DoubleConstraint(MIN,MAX);
        this.constraint.setNullAllowed(false);
        this.name = NAME;
        this.constraint.setName( this.name );
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
    	if( ( site != null ) && ( eqkRupture != null ) )
    		setValueIgnoreWarning(eqkRupture.getRuptureSurface().getDistanceX(site.getLocation()));
    	else 
    		setValue(null);
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
            val2 = new Double( val.doubleValue() );
        }

        DistanceX_Parameter param = new DistanceX_Parameter(  );
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
	
	/**
	 * This performs simple tests
	 * @param args
	 */
	  public static void main(String[] args) {
		  MeanUCERF2 meanUCERF2 = new MeanUCERF2();
		  meanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
		  meanUCERF2.updateForecast();
//		  for(int s=0; s<meanUCERF2.getNumSources();s++)
//			  System.out.println(s+"   "+meanUCERF2.getSource(s).getName());
		  
		  // sierra madre is src # 271
		  ProbEqkRupture sierraMadreRup = meanUCERF2.getSource(271).getRupture(meanUCERF2.getSource(271).getNumRuptures()-1);
		  
		  Site site = new Site();
		  site.setLocation(sierraMadreRup.getRuptureSurface().getFirstLocOnUpperEdge());
		  
		  DistanceX_Parameter distX = new DistanceX_Parameter();
		  distX.setValue(sierraMadreRup, site);
		  
	  }
		   


}
