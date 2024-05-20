package org.opensha.sha.imr;

import java.util.ListIterator;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.param.PropagationEffectParams.AbstractDoublePropEffectParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceSeisParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceX_Parameter;
import org.opensha.sha.imr.param.PropagationEffectParams.PropagationEffectParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.WarningDoublePropagationEffectParameter;
import org.opensha.sha.util.NSHMP_Util;


/**
 * <b>Title:</b> PropagationEffect<p>
 *
 * <b>Description:</b>
 *
 *
 * @author Ned Field
 * @version 1.0
 */
@Deprecated
public class PropagationEffect implements java.io.Serializable, ParameterChangeListener{

	private final static String C = "PropagationEffect";
	private final static boolean D = false;

	private boolean approxHorzDist = true;
	private boolean ptSrcCorr = false;
	private boolean nshmpPtSrcCorr = false;

	// Seis depth
	double seisDepth = DistanceSeisParameter.SEIS_DEPTH;

	// Approx Horz Dist Parameter
	public final static String APPROX_DIST_PARAM_NAME = "Use Approximate Distance";
	private final static String APPROX_DIST_PARAM_INFO = "Horz. dist. calculated as: 111 * ( (lat1-lat2)^2 + (cos(0.5*(lat1+lat2))*(lon1-lon2))^2 )^0.5";
	BooleanParameter approxDistParam;

	// Point source correction Parameter
	public final static String POINT_SRC_CORR_PARAM_NAME = "Point-Source Correction";
	private final static String POINT_SRC_CORR_PARAM_INFO = "Use median distance correction for point sources (Field)";
	BooleanParameter pointSrcCorrParam;
	
	// NSHMP Point source correction Parameter; if true this will override
	// the original point source correction algorithm; pointSrcCorrParam must
	// also be true for this to take; only applies to M > 6 ruptures
	public final static String NSHMP_PT_SRC_CORR_PARAM_NAME = "NSHMP Pt Src. Corr.";
	private final static String NSHMP_PT_SRC_CORR_PARAM_INFO = "Use NSHMP mean RJB distance point source correction";
	BooleanParameter nshmpPtSrcCorrParam;
	

	protected ParameterList adjustableParams;

	/** The Site used for calculating the PropagationEffect parameter values. */
	protected Site site = null;

	/** The EqkRupture used for calculating the PropagationEffect parameter values.*/
	protected EqkRupture eqkRupture = null;

	/** this distance measure for the DistanceRupParameter */
	protected double distanceRup;

	/** this distance measure for the DistanceJBParameter */
	protected double distanceJB;
	protected boolean fix_dist_JB = false;

	/** this distance measure for the DistanceSeisParameter */
	protected double distanceSeis;

	/** this distance measure for the DistanceX_Parameter */
	protected double distanceX;
	protected DistanceX_Parameter distanceX_Parameter = new DistanceX_Parameter();


	// this tells whether values are out of date w/ respect to current Site and EqkRupture
	protected boolean STALE = true;
	protected boolean DISTANCE_X_STALE = true;

	/** No Argument consructor */
	public PropagationEffect() {

		approxDistParam = new BooleanParameter(APPROX_DIST_PARAM_NAME, Boolean.valueOf(approxHorzDist));
		approxDistParam.setInfo(APPROX_DIST_PARAM_INFO);
		approxDistParam.addParameterChangeListener(this);

		pointSrcCorrParam = new BooleanParameter(POINT_SRC_CORR_PARAM_NAME, Boolean.valueOf(ptSrcCorr));
		pointSrcCorrParam.setInfo(POINT_SRC_CORR_PARAM_INFO);
		pointSrcCorrParam.addParameterChangeListener(this);

		nshmpPtSrcCorrParam = new BooleanParameter(NSHMP_PT_SRC_CORR_PARAM_NAME, Boolean.valueOf(nshmpPtSrcCorr));
		nshmpPtSrcCorrParam.setInfo(NSHMP_PT_SRC_CORR_PARAM_INFO);
		nshmpPtSrcCorrParam.addParameterChangeListener(this);

		adjustableParams = new ParameterList();
		adjustableParams.addParameter(approxDistParam);
		adjustableParams.addParameter(pointSrcCorrParam);
		adjustableParams.addParameter(nshmpPtSrcCorrParam);
		
	}

	/** Constructor that is give Site and EqkRupture objects */
	public PropagationEffect( Site site, EqkRupture eqkRupture) {
		this();
		this.site = site;
		this.eqkRupture = eqkRupture;
	}

	/** Returns the Site object */
	public Site getSite() { return site; }

	/** Returns the EqkRupture object */
	public EqkRupture getEqkRupture() { return eqkRupture; }

	/** Sets the Site object */
	public void setSite(Site site) {
		this.site = site;
		STALE = true;
		DISTANCE_X_STALE = true;
	}

	/**
	 * Setting this as true will change the calculated distanceJB value to 0.0 if it's less
	 * than half the distance between diagonally neighboring points on the rupture surface
	 * (otherwise it's never exactly zero everywhere above the entire surface).  This is useful
	 * where differences between 0.0 and 0.5 km are important. The default value is false.
	 * @param fixIt
	 */
	public void fixDistanceJB(boolean fixIt) {
		fix_dist_JB = fixIt;
	}


	/** Sets the EqkRupture object */
	public void setEqkRupture(EqkRupture eqkRupture) {
		this.eqkRupture = eqkRupture;
		STALE = true;
		DISTANCE_X_STALE = true;
	}

	/** Sets both the EqkRupture and Site object */
	public void setAll(EqkRupture eqkRupture, Site site) {
		this.eqkRupture = eqkRupture;
		this.site = site;
		STALE = true;
		DISTANCE_X_STALE = true;
	}


	/**
	 * This returns the value for the parameter-name given
	 */
	public Object getParamValue(String paramName) {

		if (D) System.out.println(C+": getting Param Value for "+paramName);

		if(STALE == true)
			computeParamValues();

		//QUESTION - IS CREATING A NEW DOUBLE OBJECT WITH EACH CALL INEFFICIENT/UNNECESSARY?
		if(paramName.equals(DistanceRupParameter.NAME))
			return Double.valueOf(distanceRup);
		else if(paramName.equals(DistanceJBParameter.NAME))
			return Double.valueOf(distanceJB);
		else if(paramName.equals(DistanceSeisParameter.NAME))
			return Double.valueOf(distanceSeis);
		else if(paramName.equals(DistanceX_Parameter.NAME)) {
			if(this.DISTANCE_X_STALE == true)
				computeDistanceX();
			return Double.valueOf(distanceX);
		}
		else
			throw new RuntimeException("Parameter not supported");
	}

	/**
	 * This returns rupture distance
	 * @return
	 */
	public double getDistanceRup() {
		if(STALE == true)
			computeParamValues();
		return distanceRup;
	}

	/**
	 * This returns distance JB (shortest horz distance to surface projection of rupture)
	 * @return
	 */
	public double getDistanceJB() {
		if(STALE == true)
			computeParamValues();
		return distanceJB;
	}

	/**
	 * This returns distance seis
	 * @return
	 */
	public double getDistanceSeis() {
		if(STALE == true)
			computeParamValues();
		return distanceSeis;
	}


	/**
	 * This returns distance X (see DistanceX_Parameter)
	 * @return
	 */
	public double getDistanceX() {
		if(this.DISTANCE_X_STALE == true)
			computeDistanceX();
		return distanceX;
	}


	/**
	 * This sets the value of the passed in parameter with that computed internally.
	 * This ignores warnings exceptions.
	 */
	public void setParamValue( Parameter param ) {

		if(param instanceof AbstractDoublePropEffectParam)
			((AbstractDoublePropEffectParam)param).setValueIgnoreWarning((Double)getParamValue(param.getName()));
		else
			param.setValue(getParamValue(param.getName()));

	}


	/**
	 *
	 * @param paramName
	 * @return
	 */
	public boolean isParamSupported(String paramName) {
		if(paramName.equals(DistanceRupParameter.NAME))
			return true;
		else if(paramName.equals(DistanceJBParameter.NAME))
			return true;
		else if(paramName.equals(DistanceSeisParameter.NAME))
			return true;
		else
			return false;
	}


	/**
	 *
	 * @param param
	 * @return
	 */
	public boolean isParamSupported( Parameter param ) {
		return isParamSupported(param.getName());
	}

	private void computeDistanceX() {
		distanceX = ((Double)distanceX_Parameter.getValue(eqkRupture, site)).doubleValue();
	}


	/**
	 *
	 */
	private void computeParamValues() {

		if( ( this.site != null ) && ( this.eqkRupture != null ) ){

			Location loc1 = site.getLocation();
			Location loc2;
			distanceJB = Double.MAX_VALUE;
			distanceSeis = Double.MAX_VALUE;
			distanceRup = Double.MAX_VALUE;

			double horzDist, vertDist, rupDist;

			EvenlyGriddedSurface rupSurf = (EvenlyGriddedSurface) eqkRupture.getRuptureSurface();
			int numLocs = rupSurf.getNumCols()*rupSurf.getNumRows();

			// flag to project to seisDepth if only one row and depth is below seisDepth
			boolean projectToDepth = false;
			if (rupSurf.getNumRows() == 1 && rupSurf.getLocation(0,0).getDepth() < seisDepth)
				projectToDepth = true;

			// get locations to iterate over depending on dip
			ListIterator it;
			if(rupSurf.getAveDip() > 89) {
				it = rupSurf.getColumnIterator(0);
				if (rupSurf.getLocation(0,0).getDepth() < seisDepth)
					projectToDepth = true;
			}
			else
				it = rupSurf.getLocationsIterator();

			while( it.hasNext() ){

				loc2 = (Location) it.next();

				// get the vertical distance
				vertDist = LocationUtils.vertDistance(loc1, loc2);

				// get the horizontal dist depending on desired accuracy
				if(approxHorzDist)
					horzDist = LocationUtils.horzDistanceFast(loc1, loc2);
				else
					horzDist = LocationUtils.horzDistance(loc1,loc2);

				// make point source correction if desired

				if(numLocs == 1 && ptSrcCorr) {
					
					if (nshmpPtSrcCorr) {
						double MM = eqkRupture.getMag();
						if (MM > 6) {
							// getMeanRJB is built on the assumption of 0.05 M
							// centered bins. Non-UCERF erf's often do not make
							// this assumption and are 0.1 based so we push
							// the value down to the next closest compatible M
							MM = ((int) (MM*100) % 10 != 5) ? MM - 0.05 : MM;
							horzDist = NSHMP_Util.getMeanRJB(MM, horzDist);
						}
					} else {
						
						// Wells and Coppersmith L(M) for "all" focal mechanisms
						// this correction comes from work by Ned Field and Bruce Worden
						// it assumes a vertically dipping straight fault with random
						// hypocenter and strike
						double rupLen =  Math.pow(10.0,-3.22+0.69*eqkRupture.getMag());
						double corr = 0.7071 + (1.0-0.7071)/(1 + Math.pow(rupLen/(horzDist*0.87),1.1));
						horzDist *=corr;
					}
				}

				if(horzDist < distanceJB) distanceJB = horzDist;

				rupDist = horzDist * horzDist + vertDist * vertDist;
				if(rupDist < distanceRup) distanceRup = rupDist;

				if (loc2.getDepth() >= seisDepth) {
					if (rupDist < distanceSeis)
						distanceSeis = rupDist;
				}
				// take care of shallow line or point source case
				else if(projectToDepth) {
					rupDist = horzDist * horzDist + seisDepth * seisDepth;
					if (rupDist < distanceSeis)
						distanceSeis = rupDist;
				}
			}

			distanceRup = Math.pow(distanceRup,0.5);
			distanceSeis = Math.pow(distanceSeis,0.5);

			// fix distanceJB if needed
			if(fix_dist_JB)
				if(rupSurf.getNumCols() > 1 && rupSurf.getNumRows() > 1) {
					double d1, d2,min_dist;
					loc1 = rupSurf.getLocation(0, 0);
					loc2 = rupSurf.getLocation(1, 1);
					if(approxHorzDist)
						d1 = LocationUtils.horzDistanceFast(loc1, loc2);
					else
						d1 = LocationUtils.horzDistance(loc1,loc2);
					loc1 = rupSurf.getLocation(0, 1);
					loc2 = rupSurf.getLocation(1, 0);
					if(approxHorzDist)
						d2 = LocationUtils.horzDistanceFast(loc1, loc2);
					else
						d2 = LocationUtils.horzDistance(loc1,loc2);
					min_dist = Math.min(d1, d1)/2;
					if(distanceJB<=min_dist) distanceJB = 0;
				}

			if(D) {
				System.out.println(C+": distanceRup = " + distanceRup);
				System.out.println(C+": distanceSeis = " + distanceSeis);
				System.out.println(C+": distanceJB = " + distanceJB);
			}

			STALE = false;
		}
		else
			throw new RuntimeException ("Site or EqkRupture is null");

	}

	@Override
	public void parameterChange( ParameterChangeEvent event ) {
		approxHorzDist = approxDistParam.getValue();
		ptSrcCorr = pointSrcCorrParam.getValue();
		nshmpPtSrcCorr = nshmpPtSrcCorrParam.getValue();
	}

	/**
	 *
	 * @return the adjustable ParameterList
	 */
	public ParameterList getAdjustableParameterList(){
		return this.adjustableParams;
	}

	/**
	 * get the adjustable parameters
	 *
	 * @return
	 */
	public ListIterator getAdjustableParamsIterator() {
		return adjustableParams.getParametersIterator();
	}


}
