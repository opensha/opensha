package org.opensha.sha.imr.attenRelImpl;


import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.param.EqkRuptureParams.AftershockParam;
import org.opensha.sha.imr.param.EqkRuptureParams.DipParam;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupWidthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusDistX_OverRupParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusJB_OverRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.HangingWallFlagParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

/**
 * <b>Title:</b> AS_2008_AttenRel<p>
 *
 * <b>Description:</b> This implements the Attenuation Relationship published by Abrahamson & Silva (2008,
 * "Summary of the Abrahamson & Silva NGA Ground-Motion Relations", Earthquake Spectra, Volume 24, Number 1, pp. 67-97) <p>
 *
 * Supported Intensity-Measure Parameters:<p>
 * <UL>
 * <LI>pgaParam - Peak Ground Acceleration
 * <LI>pgvParam - Peak Ground Velocity
 * <LI>saParam - Response Spectral Acceleration
 * </UL>
 * Other Independent Parameters:<p>
 * <UL>
 * <LI>magParam - Moment magnitude
 * <LI>fltTypeParam - Style of faulting (SS, REV, NORM)
 * <LI>rupTopDepthParam - Depth to top of rupture (km)
 * <LI>dipParam - Rupture surface dip (degrees)
 * <LI>rupWidth - Down-dip rupture width (km)
 * <LI>aftershockParam - indicates whether event is an aftershock
 * <LI>vs30Param - Average shear wave velocity of top 30 m of soil/rock (m/s)
 * <LI>vsFlagParam - indicates whether Vs30 is measured or estimated
 * <LI>depthTo1pt0kmPerSecParam - Depth to shear wave velocity Vs=1.0km/s (m)
 * <LI>distanceRupParam - Closest distance to surface projection of fault (km)
 * <LI>distRupMinusJB_OverRupParam =  - used as a proxy for hanging wall effect;
 * <li>distRupMinusDistX_OverRupParam - used to set distX
 * <LI>hangingWallFlagParam - indicates whether site is on the hanging wall
 * <LI>componentParam - Component of shaking - Only GMRotI50 is supported
 * <LI>stdDevTypeParam - The type of standard deviation
 * </UL><p>
 * 
 * <p>
 * NOTES: distRupMinusJB_OverRupParam is used rather than distancJBParameter because the latter 
 * should not be held constant when distanceRupParameter is changed (e.g., in the 
 * AttenuationRelationshipApplet).  The same is true for distRupMinusDistX_OverRupParam.
 * <p>
 * When setting parameters from a passed-in EqkRupture, aftershockParam is always set to false
 * (we could change this if aftershock info is added to an EqkRupture, but it's not clear this
 * is justified).
 * <p>
 * If depthTo1pt0kmPerSecParam is null, it is set from Vs30 using their equation (17).
 * <p>
 * Verification - This model has been tested against: 1) a verification file generated independently by Ken Campbell,
 * implemented in the JUnit test class AS_2008_test; and  2) by the test class NGA08_Site_EqkRup_Tests, which makes sure
 * parameters are set properly when Site and EqkRupture objects are passed in.
 *
 * @author     Christine Goulet & Ned Field
 * @created    2008
 * @version    2.0, Feb 2009
 */


public class AS_2008_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	/** Name of IMR */
	public final static String NAME = "Abrahamson & Silva (2008)";

	private final static String AS_2008_CoeffFile = "as_2008_coeff.txt";

	// Debugging stuff
	private final static String C = "AS_2008_CG_AttenRel";
	private final static boolean D = false;
	public final static String SHORT_NAME = "AS2008";
	private static final long serialVersionUID = 1234567890987654358L;

	// URL Info String
	private final static String URL_INFO_STRING = "http://www.opensha.org/glossary-attenuationRelation-ABRAHAM_SILVA_2008";

	// style of faulting param options
	public final static String FLT_TYPE_STRIKE_SLIP = "Strike-Slip";
	public final static String FLT_TYPE_REVERSE = "Reverse";
	public final static String FLT_TYPE_NORMAL = "Normal";

	// primitive form of parameters
	private int iper;
	double mag, f_rv, f_nm, depthTop, rupWidth, dip, f_as, f_hw;
	double vs30, vsm, depthTo1pt0kmPerSec, pga_rock;
	private double rRup, distRupMinusJB_OverRup, distRupMinusDistX_OverRup;
	private Component component;
	private String stdDevType;

	private boolean rock_pga_is_not_fresh;

	// Local variables declaration
	//double[] per,VLIN,b,a1,a2,a8,a10,a12,a13,a14,a15,a16,a18,s1e,s2e,s1m,s2m,s3,s4,rho;

	double c1 = 6.75;
	double c4 = 4.5;
	double a3 = 0.265;
	double a4 = -0.231;
	double a5 = -0.398;
	double N = 1.18;
	double c = 1.88;
	double c2 = 50.0;

	private HashMap indexFromPerHashMap;

	// values for warning parameters
	private final static double MAG_WARN_MIN = 4.0;
	private final static double MAG_WARN_MAX = 8.5;
	private final static double DISTANCE_RUP_WARN_MIN = 0.0;
	private final static double DISTANCE_RUP_WARN_MAX = 200.0;
	private final static double DISTANCE_JB_WARN_MIN = 0.0;
	private final static double DISTANCE_JB_WARN_MAX = 200.0;
	private final static double DISTANCE_MINUS_WARN_MIN = 0.0;
	private final static double DISTANCE_MINUS_WARN_MAX = 50.0;
	private final static double DISTANCE_X_WARN_MIN = -300.0;
	private final static double DISTANCE_X_WARN_MAX = 300.0;
	private final static double VS30_WARN_MIN = 150.0;
	private final static double VS30_WARN_MAX = 1500.0;
	private final static double DEPTH_1pt0_WARN_MIN = 0.0;
	private final static double DEPTH_1pt0_WARN_MAX = 10000;
	private final static double DIP_WARN_MIN = 15.0;
	private final static double DIP_WARN_MAX = 90.0;
	private final static double RUP_TOP_WARN_MIN = 0.0;
	private final static double RUP_TOP_WARN_MAX = 15.0;
	private final static double RUP_WIDTH_WARN_MIN = 0.0;
	private final static double RUP_WIDTH_WARN_MAX = 100.0;

	private double[] per = {-1, 0, 0.01, 0.02, 0.03, 0.04, 0.05, 0.075, 0.1, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.75, 1, 1.5, 2, 3, 4, 5, 7.5, 10};
	private double[] VLIN = {400, 865.1, 865.1, 865.1, 907.8, 994.5, 1053.5, 1085.7, 1032.5, 877.6, 748.2, 654.3, 587.1, 503, 456.6, 410.5, 400, 400, 400, 400, 400, 400, 400, 400};
	private double[] b = {-1.955, -1.186, -1.186, -1.219, -1.273, -1.308, -1.346, -1.471, -1.624, -1.931, -2.188, -2.381, -2.518, -2.657, -2.669, -2.401, -1.955, -1.025, -0.299, 0, 0, 0, 0, 0};
	private double[] a1 = {5.7578, 0.804, 0.8111, 0.855, 0.962, 1.037, 1.133, 1.375, 1.563, 1.716, 1.687, 1.646, 1.601, 1.511, 1.397, 1.137, 0.915, 0.510, 0.192, -0.280, -0.639, -0.936, -1.527, -1.993};
	private double[] a2 = {-0.9046, -0.9679, -0.9679, -0.9774, -1.0024, -1.0289, -1.0508, -1.081, -1.0833, -1.0357, -0.97, -0.9202, -0.8974, -0.8677, -0.8475, -0.8206, -0.8088, -0.7995, -0.796, -0.796, -0.796, -0.796, -0.796, -0.796};
	private double[] a8 = {-0.12, -0.0372, -0.0372, -0.0372, -0.0372, -0.0315, -0.0271, -0.0191, -0.0166, -0.0254, -0.0396, -0.0539, -0.0656, -0.0807, -0.0924, -0.1137, -0.1289, -0.1534, -0.1708, -0.1954, -0.2128, -0.2263, -0.2509, -0.2683};
	private double[] a10 = {1.539, 0.9445, 0.9445, 0.9834, 1.0471, 1.0884, 1.1333, 1.2808, 1.4613, 1.8071, 2.0773, 2.2794, 2.4201, 2.551, 2.5395, 2.1493, 1.5705, 0.3991, -0.6072, -0.96, -0.96, -0.9208, -0.77, -0.663};
	private double[] a12 = {0.08, 0, 0, 0, 0, 0, 0, 0, 0, 0.0181, 0.0309, 0.0409, 0.0491, 0.0619, 0.0719, 0.08, 0.08, 0.08, 0.08, 0.08, 0.08, 0.08, 0.08, 0.08};
	private double[] a13 = {-0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06, -0.06};
	private double[] a14 = {0.7, 1.08, 1.08, 1.08, 1.1331, 1.1708, 1.2, 1.2, 1.2, 1.1683, 1.1274, 1.0956, 1.0697, 1.0288, 0.9971, 0.9395, 0.8985, 0.8409, 0.8, 0.4793, 0.2518, 0.0754, 0, 0};
	private double[] a15 = {-0.39, -0.35, -0.35, -0.35, -0.35, -0.35, -0.35, -0.35, -0.35, -0.35, -0.35, -0.35, -0.35, -0.35, -0.3191, -0.2629, -0.223, -0.1668, -0.127, -0.0708, -0.0309, 0, 0, 0};
	private double[] a16 = {0.63, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.8423, 0.7458, 0.5704, 0.446, 0.2707, 0.1463, -0.0291, -0.1535, -0.25, -0.25, -0.25};
	private double[] a18 = {0, -0.0067, -0.0067, -0.0067, -0.0067, -0.0067, -0.0076, -0.0093, -0.0093, -0.0093, -0.0083, -0.0069, -0.0057, -0.0039, -0.0025, 0, 0, 0, 0, 0, 0, 0, 0, 0};
	private double[] s1e = {0.59, 0.59, 0.59, 0.59, 0.605, 0.615, 0.623, 0.63, 0.63, 0.63, 0.63, 0.63, 0.63, 0.63, 0.63, 0.63, 0.63, 0.615, 0.604, 0.589, 0.578, 0.57, 0.611, 0.64};
	private double[] s2e = {0.47, 0.47, 0.47, 0.47, 0.478, 0.483, 0.488, 0.495, 0.501, 0.509, 0.514, 0.518, 0.522, 0.527, 0.532, 0.539, 0.545, 0.552, 0.558, 0.565, 0.57, 0.587, 0.618, 0.64};
	private double[] s1m = {0.576, 0.576, 0.576, 0.576, 0.591, 0.602, 0.61, 0.617, 0.617, 0.616, 0.614, 0.612, 0.611, 0.608, 0.606, 0.602, 0.594, 0.566, 0.544, 0.527, 0.515, 0.51, 0.572, 0.612};
	private double[] s2m = {0.453, 0.453, 0.453, 0.453, 0.461, 0.466, 0.471, 0.479, 0.485, 0.491, 0.495, 0.497, 0.499, 0.501, 0.504, 0.506, 0.503, 0.497, 0.491, 0.5, 0.505, 0.529, 0.579, 0.612};
	private double[] s3 = {0.42, 0.47, 0.42, 0.42, 0.462, 0.492, 0.515, 0.55, 0.55, 0.55, 0.52, 0.497, 0.479, 0.449, 0.426, 0.385, 0.35, 0.35, 0.35, 0.35, 0.35, 0.35, 0.35, 0.35};
	private double[] s4 = {0.3, 0.3, 0.3, 0.3, 0.305, 0.309, 0.312, 0.317, 0.321, 0.326, 0.329, 0.332, 0.335, 0.338, 0.341, 0.346, 0.35, 0.35, 0.35, 0.35, 0.35, 0.35, 0.35, 0.35};
	private double[] rho = {0.74, 1, 1, 1, 0.991, 0.982, 0.973, 0.952, 0.929, 0.896, 0.874, 0.856, 0.841, 0.818, 0.783, 0.68, 0.607, 0.504, 0.431, 0.328, 0.255, 0.2, 0.2, 0.2};

	/**
	 *  This initializes several ParameterList objects.
	 */
	public AS_2008_AttenRel(ParameterChangeWarningListener listener) {

		this.listener = listener;
		//readCoeffFile();
		initSupportedIntensityMeasureParams();
		indexFromPerHashMap = new HashMap();
		for (int i = 2; i < per.length; i++) {
			indexFromPerHashMap.put(Double.valueOf(per[i]), Integer.valueOf(i));
		}

		initEqkRuptureParams();
		initSiteParams();
		initPropagationEffectParams();
		initOtherParams();

		initIndependentParamLists(); // This must be called after the above
		initParameterEventListeners(); //add the change listeners to the parameters

		// do this to set the primitive types for each parameter;
		setParamDefaults();
	}
	
	@Override
	public void setEqkRupture(EqkRupture eqkRupture)
			throws InvalidRangeException {
		this.eqkRupture = eqkRupture;
		magParam.setValueIgnoreWarning(eqkRupture.getMag());
		setFaultTypeFromRake(eqkRupture.getAveRake());
		RuptureSurface surface = eqkRupture.getRuptureSurface();
		rupTopDepthParam.setValueIgnoreWarning(surface.getAveRupTopDepth());
		dipParam.setValueIgnoreWarning(surface.getAveDip());
		// this means line sources will have zero width
		rupWidthParam.setValue(surface.getAveWidth());
		aftershockParam.setValue(false);
		setPropagationEffectParams();
	}

	@Override
	public void setSite(Site site) throws ParameterException {
		this.site = site;
		vs30Param.setValueIgnoreWarning((Double) site.getParameter(Vs30_Param.NAME)
			.getValue());
		depthTo1pt0kmPerSecParam.setValueIgnoreWarning((Double) site
			.getParameter(DepthTo1pt0kmPerSecParam.NAME).getValue());
		vs30_TypeParam.setValue((String)site.getParameter(Vs30_TypeParam.NAME)
			.getValue());
		setPropagationEffectParams();
	}

	@Override
	protected void setPropagationEffectParams() {
		if (site != null && eqkRupture != null) {
			propEffectUpdate();
		}
	}

	private void propEffectUpdate() {
		
		/*
		 * This sets the two propagation-effect parameters (distanceRupParam and
		 * isOnHangingWallParam) based on the current site and eqkRupture. The
		 * hanging-wall term is rake independent (i.e., it can apply to
		 * strike-slip or normal faults as well as reverse and thrust). However,
		 * it is turned off if the dip is greater than 70 degrees. It is also
		 * turned off for point sources regardless of the dip. These
		 * specifications were determined from a series of discussions between
		 * Ned Field, Norm Abrahamson, and Ken Campbell.
		 */
		
		
		distanceRupParam.setValue(eqkRupture, site); // this sets rRup too
//		distanceRupParam.setValueIgnoreWarning(eqkRupture.getRuptureSurface().getDistanceRup(site.getLocation())); // this sets rRup too
		double dist_jb = eqkRupture.getRuptureSurface().getDistanceJB(site.getLocation());
		double distX = eqkRupture.getRuptureSurface().getDistanceX(site.getLocation());
		if(rRup>0.0) {
			distRupMinusJB_OverRupParam.setValueIgnoreWarning((rRup-dist_jb)/rRup);
			if(distX >= 0.0) {  // sign determines whether it's on the hanging wall (distX is always >= 0 in distRupMinusDistX_OverRupParam)
				distRupMinusDistX_OverRupParam.setValue((rRup-distX)/rRup);
				hangingWallFlagParam.setValue(true);
			}
			else {
				distRupMinusDistX_OverRupParam.setValue((rRup+distX)/rRup);  // switch sign of distX here
				hangingWallFlagParam.setValue(false);
			}
		}
		else {
			distRupMinusJB_OverRupParam.setValueIgnoreWarning(0d);
			distRupMinusDistX_OverRupParam.setValue(0d);
			hangingWallFlagParam.setValue(true);
		}
	}

	/**
	 * Set style of faulting from the rake angle. Values derived from Abrahamson
	 * and Silva PEER NGA report.
	 * 
	 * @param rake in degrees
	 */
	protected void setFaultTypeFromRake(double rake) {
		if (rake > 30 && rake < 150) {
			fltTypeParam.setValue(FLT_TYPE_REVERSE);
		} else if (rake > -150 && rake < -30) {
			fltTypeParam.setValue(FLT_TYPE_NORMAL);
		} else {
			fltTypeParam.setValue(FLT_TYPE_STRIKE_SLIP);
		}
	}

	/**
	 * This function returns the array index for the coeffs corresponding to the chosen IMT
	 */
	protected void setCoeffIndex() throws ParameterException {

		// Check that parameter exists
		if (im == null) {
			throw new ParameterException(C +
					": updateCoefficients(): " +
					"The Intensity Measusre Parameter has not been set yet, unable to process."
			);
		}

		if (im.getName().equalsIgnoreCase(SA_Param.NAME)) {
			iper = ( (Integer) indexFromPerHashMap.get(saPeriodParam.getValue())).intValue();
		}
		else if (im.getName().equalsIgnoreCase(PGV_Param.NAME)) {
			iper = 0;
		}
		else if (im.getName().equalsIgnoreCase(PGA_Param.NAME)) {
			iper = 1;
		}
		//parameterChange = true;
		intensityMeasureChanged = false;
	}

	/**
	 * Calculates the mean
	 * @return    The mean value
	 */
	public double getMean() {

		// check if distance is beyond the user specified max
		if (rRup > USER_MAX_DISTANCE) {
			return VERY_SMALL_MEAN;
		}

		if (intensityMeasureChanged) {
			setCoeffIndex();  // intensityMeasureChanged is set to false in this method
		}

		double rJB = rRup - distRupMinusJB_OverRup*rRup;
		double rX  = rRup - distRupMinusDistX_OverRup*rRup;

		// Returns the index of the period just below Td (Eq. 21)
		double Td=Math.pow(10,-1.25+0.3*mag );
		int iTd= searchTdIndex(Td);

		// compute rock PGA (note that value of depthTo1pt0kmPerSec has no influence)
		computeRockPGA(rJB, rX); 

		double basinDepth;
		if(Double.isNaN(depthTo1pt0kmPerSec)) {
			if(vs30<180) basinDepth = Math.exp(6.745);
			else if (vs30>500)  basinDepth = Math.exp(5.394-4.48*Math.log(vs30/500));
			else  basinDepth = Math.exp(6.745-1.35*Math.log(vs30/180));	
			// System.out.println("basin depth from vs30 = "+basinDepth);
		}
		else
			basinDepth = depthTo1pt0kmPerSec;

		// System.out.println("basin depth = "+basinDepth);

		double f10 = getf10(iper, vs30, basinDepth);
		//System.out.println("From getf10, f10 = "+f10);

		double mean = 0.0;
		if(per[iper]<Td || (Td>=10.0 && iTd==22)) {
			mean = (getMean(iper,0, vs30, rRup, rJB, f_as, rX, f_rv, f_nm, mag, dip, rupWidth,
					depthTop, pga_rock))+f10;
			//			System.out.println("From getMean, if(per<Td), mean = "+ Math.exp(mean));

		} else {
			double medSa1100WithTdMinus = Math.exp(getMean(iTd,0 , 1100.0, rRup, rJB, f_as, rX, f_rv, f_nm, mag, dip,
					rupWidth, depthTop,  pga_rock));

			double medSa1100WithTdPlus = Math.exp(getMean(iTd+1,0 , 1100.0, rRup, rJB, f_as, rX, f_rv, f_nm, mag, dip,
					rupWidth, depthTop,  pga_rock));
			//System.out.println("From getMean, pga_rock = "+pga_rock+" Tdminus = "+per[iTd]+", meanSa1100TdMinus= "+ medSa1100WithTdMinus +", Tdplus = "+per[iTd+1]+", meanSa1100TdPlus= "+ medSa1100WithTdPlus);

			double f5 = getf5(iper, vs30, pga_rock);
			//System.out.println("From getf5, f5 = "+f5);

			double medSa1100AtTd0 = Math.exp(Math.log(medSa1100WithTdPlus/medSa1100WithTdMinus)/Math.log(per[iTd+1]/per[iTd])*Math.log(Math.pow(10,-1.25+0.3*mag)/per[iTd]) + Math.log(medSa1100WithTdMinus));
			double mean1100AtTd = (medSa1100AtTd0)*Math.pow(Math.pow(10,-1.25+0.3*mag)/per[iper],2);
			double f51100 = getf5(iper, 1100.0, pga_rock);
			f5 = getf5(iper, vs30, pga_rock);
			//			System.out.println("From getf5, f51100 = "+f51100+", f5="+f5);
			//			f10 = getf10(iper, vs30, basinDepth);	// ????????? isn't this already computed?
			//			System.out.println("Inside getMean, f10 = "+f10);

			mean = (Math.log(mean1100AtTd) -f51100+f5+f10);
			//			System.out.println("Inside getMean pga_rock=" +pga_rock+", mean1100atTd= " + mean1100AtTd + ", mean = "+mean);
		}

		return mean; 
	}


	/**
	 * This computes rock PGA if it's not fresh.  
	 * @param rJB
	 * @param rX
	 */
	private void computeRockPGA(double rJB, double rX) {
		if(rock_pga_is_not_fresh) {
			pga_rock = Math.exp(getMean(1,0, 1100.0, rRup, rJB, f_as, rX, f_rv, f_nm, mag, dip,
					rupWidth, depthTop,  0.0));   
			rock_pga_is_not_fresh = false;
		}
	}

	@Override
	public double getStdDev() {

		if (intensityMeasureChanged) {
			setCoeffIndex();  // intensityMeasureChanged is set to false in this method
		}

		double rJB = rRup - distRupMinusJB_OverRup*rRup;
		double rX  = rRup - distRupMinusDistX_OverRup*rRup;

		// compute rock PGA
		computeRockPGA(rJB, rX);

		double stdDev = getStdDev(iper, stdDevType, component, vs30, pga_rock, vsm);

		return stdDev;
	}

	@Override
	public void setParamDefaults() {

		magParam.setValueAsDefault();
		fltTypeParam.setValueAsDefault();
		rupTopDepthParam.setValueAsDefault();
		dipParam.setValueAsDefault();
		rupWidthParam.setValueAsDefault();
		aftershockParam.setValueAsDefault();

		vs30Param.setValueAsDefault();
		vs30_TypeParam.setValueAsDefault();
		depthTo1pt0kmPerSecParam.setValueAsDefault();

		distanceRupParam.setValueAsDefault();
		distRupMinusJB_OverRupParam.setValueAsDefault();
		distRupMinusDistX_OverRupParam.setValueAsDefault();
		hangingWallFlagParam.setValueAsDefault();

		componentParam.setValueAsDefault();
		stdDevTypeParam.setValueAsDefault();

		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		pgaParam.setValueAsDefault();
	}

	/**
	 * This creates the lists of independent parameters that the various dependent
	 * parameters (mean, standard deviation, exceedance probability, and IML at
	 * exceedance probability) depend upon. NOTE: these lists do not include anything
	 * about the intensity-measure parameters or any of thier internal
	 * independentParamaters.
	 */
	protected void initIndependentParamLists() {

		// params that the mean depends upon
		meanIndependentParams.clear();
		meanIndependentParams.addParameter(magParam);
		meanIndependentParams.addParameter(fltTypeParam);
		meanIndependentParams.addParameter(rupTopDepthParam);
		meanIndependentParams.addParameter(dipParam);
		meanIndependentParams.addParameter(rupWidthParam);
		meanIndependentParams.addParameter(aftershockParam);
		meanIndependentParams.addParameter(vs30Param);
		meanIndependentParams.addParameter(depthTo1pt0kmPerSecParam);
		meanIndependentParams.addParameter(distanceRupParam);
		meanIndependentParams.addParameter(distRupMinusJB_OverRupParam);
		meanIndependentParams.addParameter(distRupMinusDistX_OverRupParam);
		meanIndependentParams.addParameter(hangingWallFlagParam);
		meanIndependentParams.addParameter(componentParam);

		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
		stdDevIndependentParams.addParameterList(meanIndependentParams);
		stdDevIndependentParams.addParameter(stdDevTypeParam);
		stdDevIndependentParams.addParameter(vs30_TypeParam);


		// params that the exceed. prob. depends upon
		exceedProbIndependentParams.clear();
		exceedProbIndependentParams.addParameterList(stdDevIndependentParams);
		exceedProbIndependentParams.addParameter(sigmaTruncTypeParam);
		exceedProbIndependentParams.addParameter(sigmaTruncLevelParam);

		// params that the IML at exceed. prob. depends upon
		imlAtExceedProbIndependentParams.addParameterList(
				exceedProbIndependentParams);
		imlAtExceedProbIndependentParams.addParameter(exceedProbParam);
	}

	/**
	 *  Creates the Site-Type parameter and adds it to the siteParams list.
	 *  Makes the parameters noneditable.
	 */
	protected void initSiteParams() {

		vs30Param = new Vs30_Param(VS30_WARN_MIN, VS30_WARN_MAX);
		vs30_TypeParam = new Vs30_TypeParam();
		depthTo1pt0kmPerSecParam = new DepthTo1pt0kmPerSecParam(DEPTH_1pt0_WARN_MIN, DEPTH_1pt0_WARN_MAX);
		depthTo1pt0kmPerSecParam.setValue(null);
		
		siteParams.clear();
		siteParams.addParameter(vs30Param);
		siteParams.addParameter(vs30_TypeParam);
		siteParams.addParameter(depthTo1pt0kmPerSecParam);
	}

	/**
	 *  Creates the two Potential Earthquake parameters (magParam and
	 *  fltTypeParam) and adds them to the eqkRuptureParams
	 *  list. Makes the parameters noneditable.
	 */
	protected void initEqkRuptureParams() {


		magParam = new MagParam(MAG_WARN_MIN, MAG_WARN_MAX);
		aftershockParam = new AftershockParam();
		dipParam = new DipParam(DIP_WARN_MIN,DIP_WARN_MAX);
		rupTopDepthParam = new RupTopDepthParam(RUP_TOP_WARN_MIN, RUP_TOP_WARN_MAX);
		rupWidthParam = new RupWidthParam(RUP_WIDTH_WARN_MIN, RUP_WIDTH_WARN_MAX);

		StringConstraint constraint = new StringConstraint();
		constraint.addString(FLT_TYPE_STRIKE_SLIP);
		constraint.addString(FLT_TYPE_NORMAL);
		constraint.addString(FLT_TYPE_REVERSE);
		constraint.setNonEditable();
		fltTypeParam = new FaultTypeParam(constraint,FLT_TYPE_STRIKE_SLIP);

		eqkRuptureParams.clear();
		eqkRuptureParams.addParameter(magParam);
		eqkRuptureParams.addParameter(fltTypeParam);
		eqkRuptureParams.addParameter(rupTopDepthParam);
		eqkRuptureParams.addParameter(dipParam);
		eqkRuptureParams.addParameter(rupWidthParam);
		eqkRuptureParams.addParameter(aftershockParam);

	}

	/**
	 *  Creates the Propagation Effect parameters and adds them to the
	 *  propagationEffectParams list. Makes the parameters noneditable.
	 */
	protected void initPropagationEffectParams() {

		distanceRupParam = new DistanceRupParameter(0.0);
		DoubleConstraint warn = new DoubleConstraint(DISTANCE_RUP_WARN_MIN, DISTANCE_RUP_WARN_MAX);
		warn.setNonEditable();
		distanceRupParam.setWarningConstraint(warn);
		distanceRupParam.addParameterChangeWarningListener(listener);
		distanceRupParam.setNonEditable();

		//create distRupMinusJB_OverRupParam
		distRupMinusJB_OverRupParam = new DistRupMinusJB_OverRupParameter(0.0);
		DoubleConstraint warnJB = new DoubleConstraint(DISTANCE_MINUS_WARN_MIN, DISTANCE_MINUS_WARN_MAX);
		warnJB.setNonEditable();
		distRupMinusJB_OverRupParam.setWarningConstraint(warnJB);
		distRupMinusJB_OverRupParam.addParameterChangeWarningListener(listener);
		distRupMinusJB_OverRupParam.setNonEditable();

		distRupMinusDistX_OverRupParam = new DistRupMinusDistX_OverRupParam();

		// create hanging wall parameter
		hangingWallFlagParam = new HangingWallFlagParam();

		propagationEffectParams.addParameter(distanceRupParam);
		propagationEffectParams.addParameter(distRupMinusJB_OverRupParam);
		propagationEffectParams.addParameter(distRupMinusDistX_OverRupParam);
		propagationEffectParams.addParameter(hangingWallFlagParam);
	}

	/**
	 *  Creates the two supported IM parameters (PGA and SA), as well as the
	 *  independenParameters of SA (periodParam and dampingParam) and adds
	 *  them to the supportedIMParams list. Makes the parameters noneditable.
	 */
	protected void initSupportedIntensityMeasureParams() {

		// Create saParam (& its periodParam and dampingParam):
		DoubleDiscreteConstraint periodConstraint = new DoubleDiscreteConstraint();
		for (int i = 2; i < per.length; i++) {
			periodConstraint.addDouble(Double.valueOf(per[i]));
		}
		periodConstraint.setNonEditable();
		saPeriodParam = new PeriodParam(periodConstraint);
		saDampingParam = new DampingParam();
		saParam = new SA_Param(saPeriodParam, saDampingParam);
		saParam.setNonEditable();

		//  Create PGA Parameter (pgaParam):
		pgaParam = new PGA_Param();
		pgaParam.setNonEditable();

		//  Create PGV Parameter (pgvParam):
		pgvParam = new PGV_Param();
		pgvParam.setNonEditable();

		// Add the warning listeners:
		saParam.addParameterChangeWarningListener(listener);
		pgaParam.addParameterChangeWarningListener(listener);
		pgvParam.addParameterChangeWarningListener(listener);

		// Put parameters in the supportedIMParams list:
		supportedIMParams.clear();
		supportedIMParams.addParameter(saParam);
		supportedIMParams.addParameter(pgaParam);
		supportedIMParams.addParameter(pgvParam);
	}

	/**
	 *  Creates other Parameters that the mean or stdDev depends upon,
	 *  such as the Component or StdDevType parameters.
	 */
	protected void initOtherParams() {

		// init other params defined in parent class
		super.initOtherParams();

		// the Component Parameter
		// first is default, the rest are all options (including default)
		componentParam = new ComponentParam(Component.GMRotI50, Component.GMRotI50);

		// the stdDevType Parameter
		StringConstraint stdDevTypeConstraint = new StringConstraint();
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_NONE);
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_INTER);
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_INTRA);
		stdDevTypeConstraint.setNonEditable();
		stdDevTypeParam = new StdDevTypeParam(stdDevTypeConstraint);

		// add these to the list
		otherParams.addParameter(componentParam);
		otherParams.addParameter(stdDevTypeParam);  

	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	/*
	 * Returns the index for Td, to be used in getMean for constant 
	 * displacement model
	 */
	private int searchTdIndex (double Td) {
		//double[] TestTd = new double[23];
		int iTd = 22;
		for(int i=2;i<=22;++i){
			if (Td>= per[i] && Td< per[i+1] ) {
				iTd = i;
				break;
			}
		}
		//		System.out.println("Inside searchTdIndex iTd = "+iTd +", Td = "+Td+", mag \t" +mag);
		return iTd;
	}

	/**
	 * Returns the value of f5 for vs30, to be used in getMean for constant displacement model
	 */
	public double getf5(int iper, double vs30, double pga_rock) {
		double vs30Star, v1, f5;
		//"Site response model": f5_pga1100 (Eq. 5) term and required computation for v1 and vs30Star
		//Vs30 dependent term v1 (Eq. 6)
		if(per[iper]==-1.0) {
			v1 = 862.0;
		} else if(per[iper]<=0.5 && per[iper]>-1.0) {
			v1=1500.0;
		} else if(per[iper] > 0.5 && per[iper] <=1.0) {
			v1=Math.exp(8.0-0.795*Math.log(per[iper]/0.21));
		} else if(per[iper] > 1.0 && per[iper] <2.0) {
			v1=Math.exp(6.76-0.297*Math.log(per[iper]));
		} else { 
			v1 = 700.0;
		}

		//Vs30 dependent term vs30Star (Eq. 5)
		if(vs30<v1) {
			vs30Star = vs30;
		} else {
			vs30Star = v1;
		}
		//f5_pga1100 (Eq. 4)
		if (vs30<VLIN[iper]) {
			f5 = a10[iper]*Math.log(vs30Star/VLIN[iper])-b[iper]*Math.log(pga_rock+c)+b[iper]*Math.log(pga_rock+c*Math.pow(vs30Star/VLIN[iper],N));
		} else {		
			f5 = (a10[iper]+b[iper]*N)*Math.log(vs30Star / VLIN[iper]);
		}
		return f5;
	}

	/**
	 * Returns the value of f10 to be applied to the final median computation
	 */
	public double getf10(int iper, double vs30, double depthTo1pt0kmPerSec) {

		// "Soil depth model": f10 term (eq. 16) and required z1Hat, a21, e2 and a22 computation (eqs. 17-20)
		double z1Hat, e2, a21test, a21, a22, f10;
		// Requires V1 and Vs30 star from f5
		double vs30Star, v1;
		//"Site response model": f5_pga1100 (Eq. 5) term and required computation for v1 and vs30Star
		//Vs30 dependent term v1 (Eq. 6)
		if(per[iper]==-1.0) {
			v1 = 862.0;
		} else if(per[iper]<=0.5 && per[iper]>-1.0) {
			v1=1500.0;
		} else if(per[iper] > 0.5 && per[iper] <=1.0) {
			v1=Math.exp(8.0-0.795*Math.log(per[iper]/0.21));
		} else if(per[iper] > 1.0 && per[iper] <2.0) {
			v1=Math.exp(6.76-0.297*Math.log(per[iper]));
		} else { 
			v1 = 700.0;
		}
		//		Vs30 dependent term vs30Star (Eq. 5)
		if(vs30<v1) {
			vs30Star = vs30;
		} else {
			vs30Star = v1;
		}

		// Eq. 17
		if(vs30<180.0) {
			z1Hat = Math.exp(6.745);
		} else if(vs30>=180.0 && vs30<=500.0) {
			z1Hat = Math.exp(6.745-1.35*Math.log(vs30/180.0));
		} else {
			z1Hat = Math.exp(5.394-4.48*Math.log(vs30/500.0));
		}

		// Eq. 19
		if((per[iper]<0.35 && per[iper]>-1.0) || vs30>1000.0) {
			//			if(per[iper]<0.35 || vs30>1000.0) {
			e2=0.0;
		} else if(per[iper]>=0.35 && per[iper]<2.0) {
			e2 = -0.25*Math.log(vs30/1000)*Math.log(per[iper]/0.35);
		} else if(per[iper]==-1.0) {
			e2 = -0.25*Math.log(vs30/1000)*Math.log(1.0/0.35);
		} else {// if per[iper]>2.0
			e2 = -0.25*Math.log(vs30/1000)*Math.log(2.0/0.35);
		}


		// Eq. 18
		a21test = (a10[iper] + b[iper]*N)*Math.log(vs30Star/Math.min(v1, 1000.0))+e2*Math.log((depthTo1pt0kmPerSec+c2)/(z1Hat+c2));

		if(vs30>=1000.0){
			a21=0.0;
		} else if(a21test<0.0 && vs30<1000.0) {
			a21=-(a10[iper] + b[iper]*N)*Math.log(vs30Star/Math.min(v1, 1000.0))/Math.log((depthTo1pt0kmPerSec+c2)/(z1Hat+c2));
		}	else {
			a21 = e2;
		}

		// Eq. 20
		if(per[iper]<2.0){
			a22 = 0.0;
		} else {
			a22 = 0.0625*(per[iper]-2.0);
		}

		// Eq. 16
		if(depthTo1pt0kmPerSec>=200){
			f10 = a21*Math.log((depthTo1pt0kmPerSec+c2)/(z1Hat+c2)) + a22*Math.log(depthTo1pt0kmPerSec/200.0);
		} else {
			f10 = a21*Math.log((depthTo1pt0kmPerSec+c2)/(z1Hat+c2));
		}
		//		System.out.println("Inside getf10, iper="+iper+" per[iper]="+per[iper]+" per[16]=" +per[16]+" z1hat="+z1Hat+" a21test="+a21test+" a21 "+ a21 + " a22 "+ a22 +" e2="+e2+" f10 "+f10);
		return f10;
	}
	/**
	 * Calculates the median IML, but does not include the f10 term from depthTo1pt0kmPerSec (which is why it was made private). <p>
	 * @return  
	 */

	private double getMean(int iper, int iTd, double vs30, double rRup, 
			double rJB, double f_as, double rX, double f_rv,
			double f_nm, double mag, double dip, 
			double rupWidth, double depthTop,
			double pga_rock) {

		double rR, v1, vs30Star, f1, f4, f5, f6, f8;

		boolean hw = (Boolean)hangingWallFlagParam.getValue();

		f4=0.0;

		//		if(rX>=0.0){
		//			hw = 1.0;
		//		}

		//"Base model": f1 term (Eq. 2), dependent on magnitude and distance
		rR=Math.sqrt(Math.pow(rRup,2)+Math.pow(c4,2)); // Eq. 3

		if(mag<=c1) {
			f1 = a1[iper]+a4*(mag-c1)+a8[iper]*Math.pow(8.5-mag,2)+(a2[iper]+a3*(mag-c1))*Math.log(rR);
		} else {
			f1 = a1[iper]+a5*(mag-c1)+a8[iper]*Math.pow(8.5-mag,2)+(a2[iper]+a3*(mag-c1))*Math.log(rR);
		}
		//		System.out.println("a1= "+ a1[iper] +" a4= "+a4 +" mag= "+ mag +" c1="+c1+" a8= "+a8[iper]+" a2="+a2[iper]+" a3="+a3+" Rrup="+rRup+" R="+rR );

		//"Site response model": f5_pga1100 (Eq. 5) term and required computation for v1 and vs30Star
		//Vs30 dependent term v1 (Eq. 6)
		if(per[iper]==-1.0) {
			v1 = 862.0;
		} else if(per[iper]<=0.5 && per[iper]>-1.0) {
			v1=1500.0;
		} else if(per[iper] > 0.5 && per[iper] <=1.0) {
			v1=Math.exp(8.0-0.795*Math.log(per[iper]/0.21));
		} else if(per[iper] > 1.0 && per[iper] <2.0) {
			v1=Math.exp(6.76-0.297*Math.log(per[iper]));
		} else { 
			v1 = 700.0;
		}

		//Vs30 dependent term vs30Star (Eq. 5)
		if(vs30<v1) {
			vs30Star = vs30;
		} else {
			vs30Star = v1;
		}
		//f5_pga1100 (Eq. 4)
		if (vs30<VLIN[iper]) {
			f5 = a10[iper]*Math.log(vs30Star/VLIN[iper])-b[iper]*Math.log(pga_rock+c)+b[iper]*Math.log(pga_rock+c*Math.pow(vs30Star/VLIN[iper],N));
		} else {		
			f5 = (a10[iper]+b[iper]*N)*Math.log(vs30Star / VLIN[iper]);
		}

		//System.out.println("Inside Eqn getMean, per="+per[iper]+" hw="+hw+" f5=" +f5+" v1=" +v1+" vs30Star=" +vs30Star);

		//"Hanging wall model": f4 (Eq. 7) term and required computation of T1, T2, T3, T4 and T5 (Eqs. 8-12)
		if(hw){
			double T1, T2, T3, T4, T5;

			//T1 (Eq. 8)
			if (rJB<30.0) {
				T1=1.0-rJB/30.0;
			} else {
				T1=0.0;
			}

			//T2 (Eq. 9) - rewritten 2009-01-29 to be consistent with ES paper
			double rXtest = rupWidth*Math.cos(Math.toRadians(dip));
			if (rX>=rXtest || dip==90.0) {	// if site is beyond the surface projection
				T2 = 1.0;
			} else {
				T2 = 0.5 + rX / (2.0*rXtest);
			}

			//T3 (Eq. 10)
			if (rX>=depthTop) {
				T3 = 1.0;
			} else {
				T3 = rX/depthTop;
			}

			//T4 (Eq. 11)
			if(mag<=6.0) {
				T4 = 0.0;
			} else if(mag>=7.0) {
				T4 = 1.0;
			} else {
				T4 = mag - 6.0;
			}

			//T5 (Eq. 12)
			if(dip>=70.0) {
				T5 = 1.0-(dip-70.0)/20.0;
			} else {
				T5 = 1.0;
			}   
			f4 = a14[iper]*T1*T2*T3*T4*T5;
			//			System.out.println("Inside Eqn getMean, f4=" +f4+" T1=" +T1+" T2=" +T2+" T3=" +T3+" T4=" +T4);
		} 


		// "Depth to top of rupture model": f6 term (eq. 13)
		if(depthTop<10.0) {
			f6 = a16[iper]*depthTop/10.0;
		} else {
			f6 = a16[iper];
		}
		// "Large distance model": f8 term (Eq. 14) and required T6 computation (Eq. 15)
		double T6;

		if(mag<5.5) {
			T6 = 1.0;
		} else if(mag>6.5) {
			T6 = 0.5;
		} else {
			T6 = 0.5*(6.5-mag) +0.5;
		}

		if(rRup<100) {
			f8 = 0.0;
		} else {
			f8=a18[iper]*(rRup - 100.0)*T6;
		}

		//System.out.println("Inside Eqn getMean, per="+per[iper]+" rJB="+rJB+" hw="+hw+" f1=" +f1+" f4=" +f4+" f5=" +f5+" f6=" +f6+" f8=" +f8);
		double cgMean = f1 + a12[iper]*f_rv +a13[iper]*f_nm +a15[iper]*f_as + f4 + f5 + f6 +f8; 

		return cgMean;
	}

	/**
	 * @param iper
	 * @param stdDevType
	 * @param component
	 * @param vs30
	 * @param vsm (vs flag for measured/estimated)
	 * @return
	 */
	public double getStdDev(int iper, String stdDevType, Component component, double vs30, double pga_rock, double vsm) {

		if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_NONE)) return 0.0;
		
			// Compute sigma0 (eq. 27), tau0 (eq. 28) and dterm (eq. 26) 
			//NOTE: I created variables with the PGA suffix because it's easier to read the equations below (CGoulet)
			double  v1, vs30Star, dterm, s1, s1PGA, s2, s2PGA,  sigma0, sigma0PGA, tau0, tau0PGA, sigmaB, sigmaBPGA, tauB, tauBPGA, sigma, tau;  

			//"Site response model": f5_pga1100 (Eq. 5) term and required computation for v1 and vs30Star
			//Vs30 dependent term v1 (Eq. 6)
			if(per[iper]==-1.0) {
				v1 = 862.0;
			} else if(per[iper]<=0.5 && per[iper]>-1.0) {
				v1=1500.0;
			} else if(per[iper] > 0.5 && per[iper] <=1.0) {
				v1=Math.exp(8.0-0.795*Math.log(per[iper]/0.21));
			} else if(per[iper] > 1.0 && per[iper] <2.0) {
				v1=Math.exp(6.76-0.297*Math.log(per[iper]));
			} else { 
				v1 = 700.0;
			}

			//Vs30 dependent term vs30Star (Eq. 5)
			if(vs30<v1) {
				vs30Star = vs30;
			} else {
				vs30Star = v1;
			}

			// sugmaamp=0.3 for all periods as per page 81. below equation 23
			double sigmaamp=0.3;

			// dterm (eq. 26) 
			//** The published ES version has errors in this equation. Per Norm (2008-08-15, personal communication)
			//			1) the test against VLIN should be made with vs30, but the computation with vs30Star 
			//			2) The (-b*pga_rock) is to multiply both terms of the equations
			dterm=0.0;
			if(vs30<VLIN[iper]){
				dterm=b[iper]*pga_rock*(-1.0/(pga_rock+c)+1.0/(pga_rock+c*Math.pow(vs30Star/VLIN[iper],N)));
			}

			// Define appropriate s1 and s2 values depending on how Vs30 was obtained
			// (measured or estimated), using the vsm flag defined above which is input in the GUI
			if(vsm==1.0) {
				s1PGA=s1m[1];
				s2PGA=s2m[1];
				s1=s1m[iper];
				s2=s2m[iper];				
			} else  {
				s1PGA=s1e[1];
				s2PGA=s2e[1];
				s1=s1e[iper];
				s2=s2e[iper];
			}

			// Compute sigma0 (Eq. 27)
			if(mag<5.0){
				sigma0=s1;
				sigma0PGA=s1PGA;
			} else if(mag>7.0){
				sigma0=s2;
				sigma0PGA=s2PGA;
			} else {
				sigma0=s1+0.5*(s2-s1)*(mag-5.0);
				sigma0PGA=s1PGA+0.5*(s2PGA-s1PGA)*(mag-5.0);
			}

			// Compute sigmaB  (Eq. 23)
			sigmaB=Math.sqrt(Math.pow(sigma0,2)-Math.pow(sigmaamp,2));
			sigmaBPGA=Math.sqrt(Math.pow(sigma0PGA,2)-Math.pow(sigmaamp,2));

			// Compute tau0 (Eq. 28)
			if(mag<5.0){
				tau0=s3[iper];
				tau0PGA=s3[1];
			} else if(mag>7.0){
				tau0=s4[iper];
				tau0PGA=s4[1];
			} else {
				tau0=s3[iper]+0.5*(s4[iper]-s3[iper])*(mag-5.0);
				tau0PGA=s3[1]+0.5*(s4[1]-s3[1])*(mag-5.0);
			}

			// Compute tauB (In text p. 81)
			tauB=tau0;
			tauBPGA=tau0PGA;

			// compute intra-event sigma (Eq. 24) 
			//** The published ES version has errors in this equation. Per Norm (2008-08-15, personal communication):
			//   1) use sigmaB instead of sigma0 in the first term.
			sigma = Math.sqrt(Math.pow(sigmaB,2)+Math.pow(sigmaamp,2)+Math.pow(dterm,2)*Math.pow(sigmaBPGA,2)+2.0*dterm*sigmaB*sigmaBPGA*rho[iper]);

			// get tau - inter-event term (Eq. 25)
			tau = Math.sqrt(Math.pow(tau0,2)+Math.pow(dterm,2)*Math.pow(tauBPGA,2)+2.0*dterm*tauB*tauBPGA*rho[iper]);

			//System.out.println("PGArock="+pga_rock+" vsm="+vsm+" dterm="+ dterm + " sigma="+sigma+" tau="+tau);
			//System.out.println("test PGA index, a1 at index 1="+a1[1]+" at index 2="+a1[2]);

			// compute total sigma
			double sigma_total = Math.sqrt(tau*tau + sigma*sigma);

			//			System.out.println("pga_rock="+ pga_rock +"\t t0="+tau0+"\t sB="+sigmaB+"\t sBPGA="+sigmaBPGA+"\t s="+sigma+"\t t="+tau+"\t s_tot="+sigma_total);

			// return appropriate value
			if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_TOTAL))
				return sigma_total;
			else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_INTRA))
				return sigma;
			else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_INTER))
				return tau;
			else
				return Double.NaN;   // just in case invalid stdDev given			  
	}

	@Override
	public void parameterChange(ParameterChangeEvent e) {

		String pName = e.getParameterName();
		Object val = e.getNewValue();
		//parameterChange = true;
		rock_pga_is_not_fresh = true;

		//		System.out.println(pName+"\t"+val);

		if (pName.equals(MagParam.NAME)) {
			mag = ( (Double) val).doubleValue();
		}
		else if (pName.equals(FaultTypeParam.NAME)) {
			String fltType = fltTypeParam.getValue();
			if (fltType.equals(FLT_TYPE_NORMAL)) {
				f_rv = 0 ;
				f_nm = 1;
			}
			else if (fltType.equals(FLT_TYPE_REVERSE)) {
				f_rv = 1;
				f_nm = 0;
			}
			else {
				f_rv =0 ;
				f_nm = 0;
			}
		}
		else if (pName.equals(RupTopDepthParam.NAME)) {
			depthTop = ( (Double) val).doubleValue();
		}
		else if (pName.equals(DipParam.NAME)) {
			dip = ( (Double) val).doubleValue();
		}
		else if (pName.equals(RupWidthParam.NAME)) {
			rupWidth = ( (Double) val).doubleValue();
		}
		else if (pName.equals(AftershockParam.NAME)) {
			if(((Boolean)val).booleanValue())
				f_as = 1;
			else
				f_as = 0;
		}
		else if (pName.equals(Vs30_Param.NAME)) {
			vs30 = ( (Double) val).doubleValue();
		}
		else if (pName.equals(Vs30_TypeParam.NAME)) {
			if(((String)val).equals(Vs30_TypeParam.VS30_TYPE_MEASURED)) {
				vsm = 1;
			}
			else {
				vsm = 0;
			}
		}
		else if (pName.equals(DepthTo1pt0kmPerSecParam.NAME)) {
			if(val == null)
				depthTo1pt0kmPerSec = Double.NaN;  // can't set the default here because vs30 could still change
			else
				depthTo1pt0kmPerSec = ( (Double) val).doubleValue();
		}
		else if (pName.equals(DistanceRupParameter.NAME)) {
			rRup = ( (Double) val).doubleValue();
		}
		else if (pName.equals(DistRupMinusJB_OverRupParameter.NAME)) {
			distRupMinusJB_OverRup = ( (Double) val).doubleValue();
		}
		else if(pName.equals(distRupMinusDistX_OverRupParam.getName())){
			distRupMinusDistX_OverRup = ((Double)val).doubleValue();
		}
		else if (pName.equals(HangingWallFlagParam.NAME)) {
			if(((Boolean)val)) {
				f_hw = 1.0;
			}
			else {
				f_hw = 0.0;
			}
		}
		else if (pName.equals(StdDevTypeParam.NAME)) {
			stdDevType = (String) val;
		}
		else if (pName.equals(ComponentParam.NAME)) {
			component = componentParam.getValue();
		}
		else if (pName.equals(PeriodParam.NAME)) {
			intensityMeasureChanged = true;
		}

	}

	@Override
	public void resetParameterEventListeners(){

		magParam.removeParameterChangeListener(this);
		fltTypeParam.removeParameterChangeListener(this);
		rupTopDepthParam.removeParameterChangeListener(this);
		dipParam.removeParameterChangeListener(this);
		rupWidthParam.removeParameterChangeListener(this);
		aftershockParam.removeParameterChangeListener(this);

		vs30Param.removeParameterChangeListener(this);
		vs30_TypeParam.removeParameterChangeListener(this);
		depthTo1pt0kmPerSecParam.removeParameterChangeListener(this);

		distanceRupParam.removeParameterChangeListener(this);
		distRupMinusJB_OverRupParam.removeParameterChangeListener(this);
		distRupMinusDistX_OverRupParam.removeParameterChangeListener(this);
		hangingWallFlagParam.removeParameterChangeListener(this);

		componentParam.removeParameterChangeListener(this);
		stdDevTypeParam.removeParameterChangeListener(this);
		saPeriodParam.removeParameterChangeListener(this);

		this.initParameterEventListeners();
	}

	@Override
	protected void initParameterEventListeners() {

		magParam.addParameterChangeListener(this);
		fltTypeParam.addParameterChangeListener(this);
		rupTopDepthParam.addParameterChangeListener(this);
		dipParam.addParameterChangeListener(this);
		rupWidthParam.addParameterChangeListener(this);
		aftershockParam.addParameterChangeListener(this);

		vs30Param.addParameterChangeListener(this);
		vs30_TypeParam.addParameterChangeListener(this);
		depthTo1pt0kmPerSecParam.addParameterChangeListener(this);

		distanceRupParam.addParameterChangeListener(this);
		distRupMinusJB_OverRupParam.addParameterChangeListener(this);
		distRupMinusDistX_OverRupParam.addParameterChangeListener(this);
		hangingWallFlagParam.addParameterChangeListener(this);

		componentParam.addParameterChangeListener(this);
		stdDevTypeParam.addParameterChangeListener(this);
		saPeriodParam.addParameterChangeListener(this);
	}

	@Override
	public URL getInfoURL() throws MalformedURLException{
		return new URL(URL_INFO_STRING);
	}

	/**
	 * CG: This comment was for CB 2008. Need to check with Ned what's the reason behind it:
	 * This tests rJB numerical precision with respect to the f_hngR term.  Looks OK now.
	 * @param args
	 */
	public static void main(String[] args) {

		Location loc1 = new Location(-0.1, 0.0, 0);
		Location loc2 = new Location(+0.1, 0.0, 0);
		FaultTrace faultTrace = new FaultTrace("test");
		faultTrace.add(loc1);
		faultTrace.add(loc2);	  
		StirlingGriddedSurface surface = new StirlingGriddedSurface(faultTrace, 45.0,0,10,1);
		EqkRupture rup = new EqkRupture();
		rup.setMag(7);
		rup.setAveRake(90);
		rup.setRuptureSurface(surface);

		AS_2008_AttenRel attenRel = new AS_2008_AttenRel(null);
		attenRel.setParamDefaults();
		attenRel.setIntensityMeasure("PGA");
		attenRel.setEqkRupture(rup);

		Site site = new Site();
		site.addParameter(attenRel.getParameter(Vs30_Param.NAME));
		site.addParameter(attenRel.getParameter(DepthTo1pt0kmPerSecParam.NAME));
		site.addParameter(attenRel.getParameter(Vs30_TypeParam.NAME));

		Location loc;
		for(double dist=-0.3; dist<=0.3; dist+=0.01) {
			loc = new Location(0,dist);
			site.setLocation(loc);
			attenRel.setSite(site);
			//			System.out.print((float)dist+"\t");
			attenRel.getMean();
		}

	}

}
