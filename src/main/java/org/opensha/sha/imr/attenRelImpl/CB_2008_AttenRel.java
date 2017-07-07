/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.imr.attenRelImpl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import org.opensha.commons.data.Named;
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
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.DipParam;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGD_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusJB_OverRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

/**
 * Implementation of the Campbell & Bozorgnia (2008) next generation attenuation
 * relationship. See: <i>NGA Ground Motion Model for the Geometric Mean
 * Horizontal Component of PGA, PGV, PGD and 5% Damped Linear Elastic Response
 * Spectra for Periods Ranging from 0.01 to 10 s", Earthquake Spectra, Volume
 * 24, Number 1, pp. 139-171.</i>
 * <p>
 * Supported Intensity-Measure Parameters:
 * <UL>
 * <LI>pgaParam - Peak Ground Acceleration
 * <LI>pgvParam - Peak Ground Velocity
 * <LI>pgdParam - Peak Ground Displacement
 * <LI>saParam - Response Spectral Acceleration
 * </UL>
 * Other Independent Parameters:
 * <UL>
 * <LI>magParam - moment Magnitude
 * <LI>fltTypeParam - Style of faulting
 * <LI>rupTopDepthParam - depth to top of rupture
 * <LI>dipParam - rupture surface dip
 * <LI>distanceRupParam - closest distance to surface projection of fault
 * <li>distRupMinusJB_OverRupParam - used as a proxy for hanging wall effect
 * <LI>vs30Param - average Vs over top 30 meters
 * <li>depthTo2pt5kmPerSecParam - depth to where Vs30 equals 2.5 km/sec
 * <LI>componentParam - Component of shaking
 * <LI>stdDevTypeParam - The type of standard deviation
 * </UL>
 * NOTES: distRupMinusJB_OverRupParam is used rather than distancJBParameter
 * because the latter should not be held constant when distanceRupParameter is
 * changed (e.g., in the AttenuationRelationshipApplet). This includes the
 * stipulation that the mean of 0.2-sec (or less) SA should not be less than
 * that of PGA (the latter being given if so). If depthTo2pt5kmPerSec is null
 * (unknown), it is set as 2 km if vs30 <= 2500, and zero otherwise.
 * <p>
 * Verification - This model has been tested against: 1) a verification file
 * generated independently by Ken Campbell, implemented in the JUnit test class
 * CB_2008_test; and 2) by the test class NGA08_Site_EqkRup_Tests, which makes
 * sure parameters are set properly when Site and EqkRupture objects are passed
 * in.
 * </p>
 * 
 * @author Ned Field
 * @created October 2008
 * @version $Id: CB_2008_AttenRel.java 10928 2015-01-27 21:08:50Z kmilner $
 */

public class CB_2008_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	private final static String C = "CB_2006_AttenRel";
	private final static boolean D = false;
	public final static String SHORT_NAME = "CB2008";
	private static final long serialVersionUID = 1234567890987654358L;
	public final static String NAME = "Campbell & Bozorgnia (2008)";

	public final static double s_lnAF = 0.3;
	public final static double n = 1.18;
	public final static double c = 1.88;

	private HashMap indexFromPerHashMap;

	private int iper;
	private double vs30, rRup, distRupMinusJB_OverRup, f_rv, f_nm, mag, depthTop, depthTo2pt5kmPerSec,dip;
	private String stdDevType;
	private Component component;
	private boolean magSaturation;

	// values for warning parameters
	protected final static Double MAG_WARN_MIN = new Double(4.0);
	protected final static Double MAG_WARN_MAX = new Double(8.5);
	protected final static Double DISTANCE_RUP_WARN_MIN = new Double(0.0);
	protected final static Double DISTANCE_RUP_WARN_MAX = new Double(200.0);
	protected final static Double DISTANCE_MINUS_WARN_MIN = new Double(0.0);
	protected final static Double DISTANCE_MINUS_WARN_MAX = new Double(50.0);
	protected final static Double VS30_WARN_MIN = new Double(150.0);
	protected final static Double VS30_WARN_MAX = new Double(1500.0);
	protected final static Double DEPTH_2pt5_WARN_MIN = new Double(0);
	protected final static Double DEPTH_2pt5_WARN_MAX = new Double(10);
	protected final static Double DIP_WARN_MIN = new Double(15);
	protected final static Double DIP_WARN_MAX = new Double(90);
	protected final static Double RUP_TOP_WARN_MIN = new Double(0);
	protected final static Double RUP_TOP_WARN_MAX = new Double(15);

	// style of faulting options
	public final static String FLT_TYPE_STRIKE_SLIP = "Strike-Slip";
	public final static String FLT_TYPE_REVERSE = "Reverse";
	public final static String FLT_TYPE_NORMAL = "Normal";

	protected double[] per = {-2, -1, 0, 0.010, 0.020, 0.030, 0.050, 0.075, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 7.5, 10.0};
	protected double[] c0 = {-5.270, 0.954, -1.715, -1.715, -1.680, -1.552, -1.209, -0.657, -0.314, -0.133, -0.486, -0.890, -1.171, -1.466, -2.569, -4.844, -6.406, -8.692, -9.701, -10.556, -11.212, -11.684, -12.505, -13.087};
	protected double[] c1 = {1.600, 0.696, 0.500, 0.500, 0.500, 0.500, 0.500, 0.500, 0.500, 0.500, 0.500, 0.500, 0.500, 0.500, 0.656, 0.972, 1.196, 1.513, 1.600, 1.600, 1.600, 1.600, 1.600, 1.600};
	protected double[] c2 = {-0.070, -0.309, -0.530, -0.530, -0.530, -0.530, -0.530, -0.530, -0.530, -0.530, -0.446, -0.362, -0.294, -0.186, -0.304, -0.578, -0.772, -1.046, -0.978, -0.638, -0.316, -0.070, -0.070, -0.070};
	protected double[] c3 = {0.000, -0.019, -0.262, -0.262, -0.262, -0.262, -0.267, -0.302, -0.324, -0.339, -0.398, -0.458, -0.511, -0.592, -0.536, -0.406, -0.314, -0.185, -0.236, -0.491, -0.770, -0.986, -0.656, -0.422};
	protected double[] c4 = {-2.000, -2.016, -2.118, -2.118, -2.123, -2.145, -2.199, -2.277, -2.318, -2.309, -2.220, -2.146, -2.095, -2.066, -2.041, -2.000, -2.000, -2.000, -2.000, -2.000, -2.000, -2.000, -2.000, -2.000};
	protected double[] c5 = {0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170, 0.170};
	protected double[] c6 = {4.00, 4.00, 5.60, 5.60, 5.60, 5.60, 5.74, 7.09, 8.05, 8.79, 7.60, 6.58, 6.04, 5.30, 4.73, 4.00, 4.00, 4.00, 4.00, 4.00, 4.00, 4.00, 4.00, 4.00};
	protected double[] c7 = {0.000, 0.245, 0.280, 0.280, 0.280, 0.280, 0.280, 0.280, 0.280, 0.280, 0.280, 0.280, 0.280, 0.280, 0.280, 0.280, 0.255, 0.161, 0.094, 0.000, 0.000, 0.000, 0.000, 0.000};
	protected double[] c8 = {0.000, 0.000, -0.120, -0.120, -0.120, -0.120, -0.120, -0.120, -0.099, -0.048, -0.012, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000};
	protected double[] c9 = {0.000, 0.358, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.490, 0.371, 0.154, 0.000, 0.000, 0.000, 0.000};
	protected double[] c10 = {-0.820, 1.694, 1.058, 1.058, 1.102, 1.174, 1.272, 1.438, 1.604, 1.928, 2.194, 2.351, 2.460, 2.587, 2.544, 2.133, 1.571, 0.406, -0.456, -0.820, -0.820, -0.820, -0.820, -0.820};
	protected double[] c11 = {0.300, 0.092, 0.040, 0.040, 0.040, 0.040, 0.040, 0.040, 0.040, 0.040, 0.040, 0.040, 0.040, 0.040, 0.040, 0.077, 0.150, 0.253, 0.300, 0.300, 0.300, 0.300, 0.300, 0.300};
	protected double[] c12 = {1.000, 1.000, 0.610, 0.610, 0.610, 0.610, 0.610, 0.610, 0.610, 0.610, 0.610, 0.700, 0.750, 0.850, 0.883, 1.000, 1.000, 1.000, 1.000, 1.000, 1.000, 1.000, 1.000, 1.000};
	protected double[] k1 = {400, 400, 865, 865, 865, 908, 1054, 1086, 1032, 878, 748, 654, 587, 503, 457, 410, 400, 400, 400, 400, 400, 400, 400, 400};
	protected double[] k2 = {0.000, -1.955, -1.186, -1.186, -1.219, -1.273, -1.346, -1.471, -1.624, -1.931, -2.188, -2.381, -2.518, -2.657, -2.669, -2.401, -1.955, -1.025, -0.299, 0.000, 0.000, 0.000, 0.000, 0.000};
	protected double[] k3 = {2.744, 1.929, 1.839, 1.839, 1.840, 1.841, 1.843, 1.845, 1.847, 1.852, 1.856, 1.861, 1.865, 1.874, 1.883, 1.906, 1.929, 1.974, 2.019, 2.110, 2.200, 2.291, 2.517, 2.744};
	protected double[] s_lny = {0.667, 0.484, 0.478, 0.478, 0.480, 0.489, 0.510, 0.520, 0.531, 0.532, 0.534, 0.534, 0.544, 0.541, 0.550, 0.568, 0.568, 0.564, 0.571, 0.558, 0.576, 0.601, 0.628, 0.667};
	protected double[] t_lny = {0.485, 0.203, 0.219, 0.219, 0.219, 0.235, 0.258, 0.292, 0.286, 0.280, 0.249, 0.240, 0.215, 0.217, 0.214, 0.227, 0.255, 0.296, 0.296, 0.326, 0.297, 0.359, 0.428, 0.485};
	protected double[] s_c = {0.290, 0.190, 0.166, 0.166, 0.166, 0.165, 0.162, 0.158, 0.170, 0.180, 0.186, 0.191, 0.198, 0.206, 0.208, 0.221, 0.225, 0.222, 0.226, 0.229, 0.237, 0.237, 0.271, 0.290};
	protected double[] rho = {0.174, 0.691, 1.000, 1.000, 0.999, 0.989, 0.963, 0.922, 0.898, 0.890, 0.871, 0.852, 0.831, 0.785, 0.735, 0.628, 0.534, 0.411, 0.331, 0.289, 0.261, 0.200, 0.174, 0.174};	/**

	/**
	 * Constructs a new instance of this attenuation relationship.
	 * @param listener
	 */
	public CB_2008_AttenRel(ParameterChangeWarningListener listener) {
		this.listener = listener;

		initSupportedIntensityMeasureParams();
		indexFromPerHashMap = new HashMap();
		for (int i = 3; i < per.length; i++) {
			indexFromPerHashMap.put(new Double(per[i]), new Integer(i));
		}

		initEqkRuptureParams();
		initPropagationEffectParams();
		initSiteParams();
		initOtherParams();

		initIndependentParamLists(); // This must be called after the above
		initParameterEventListeners(); //add the change listeners to the parameters
		
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
		setPropagationEffectParams();
	}

	@Override
	public void setSite(Site site) throws ParameterException {
		this.site = site;
		vs30Param.setValueIgnoreWarning((Double) site.getParameter(Vs30_Param.NAME)
			.getValue());
		depthTo2pt5kmPerSecParam.setValueIgnoreWarning((Double) site
			.getParameter(DepthTo2pt5kmPerSecParam.NAME).getValue());
		setPropagationEffectParams();
	}

	@Override
	protected void setPropagationEffectParams() {
		if (site != null && eqkRupture != null) {
			propEffectUpdate();
		}
	}
	

	
	private void propEffectUpdate() {
//		distanceRupParam.setValueIgnoreWarning(eqkRupture.getRuptureSurface().getDistanceRup(site.getLocation())); // this sets rRup too
		distanceRupParam.setValue(eqkRupture,site); // this sets rRup too
		double dist_jb = eqkRupture.getRuptureSurface().getDistanceJB(site.getLocation());
		if(rRup == 0)
			distRupMinusJB_OverRupParam.setValueIgnoreWarning(0.0);
		else
			distRupMinusJB_OverRupParam.setValueIgnoreWarning((rRup-dist_jb)/rRup);
	}

	
	/**
	 * Set style of faulting from the rake angle. Values derived from Campbell
	 * and Bozorgnia PEER NGA report.
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
			iper = 1;
		}
		else if (im.getName().equalsIgnoreCase(PGA_Param.NAME)) {
			iper = 2;
		}
		else if (im.getName().equalsIgnoreCase(PGD_Param.NAME)) {
			iper = 0;
		}
		intensityMeasureChanged = false;

	}

	/**
	 * Calculates the mean of the exceedence probability distribution. <p>
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

		// compute rJB
		double rJB = rRup - distRupMinusJB_OverRup*rRup;
//		System.out.println(distRupMinusJB_OverRup + " " + rJB);

		// set default value of basin depth based on the final value of vs30
		// (must do this here because we get pga_rock below by passing in 1100 m/s)
		if(Double.isNaN(depthTo2pt5kmPerSec)){
			if(vs30 <= 2500)
				depthTo2pt5kmPerSec = 2;
			else
				depthTo2pt5kmPerSec = 0;
		}

		double pga_rock = Math.exp(getMean(2, 1100, rRup, rJB, f_rv, f_nm, mag, dip,
				depthTop, depthTo2pt5kmPerSec, magSaturation, 0));

		double mean = getMean(iper, vs30, rRup, rJB, f_rv, f_nm, mag, dip,
				depthTop, depthTo2pt5kmPerSec, magSaturation, pga_rock);

		// System.out.println(mean+"\t"+iper+"\t"+vs30+"\t"+rRup+"\t"+rJB+"\t"+f_rv+"\t"+f_nm+"\t"+mag+"\t"+dip+"\t"+depthTop+"\t"+depthTo2pt5kmPerSec+"\t"+magSaturation+"\t"+pga_rock);

		// make sure SA does not exceed PGA if per < 0.2 (page 11 of pre-print)
		if(iper < 3 || iper > 11 ) // not SA period between 0.02 and 0.15
			return mean;
		else {
			double pga_mean = getMean(2, vs30, rRup, rJB, f_rv, f_nm, mag, dip,
					depthTop, depthTo2pt5kmPerSec, magSaturation, pga_rock); // mean for PGA
			return Math.max(mean,pga_mean);
		}
	}



	/**
	 * @return    The stdDev value
	 */
	public double getStdDev() {
		if (intensityMeasureChanged) {
			setCoeffIndex();  // intensityMeasureChanged is set to false in this method
		}

		// compute rJB
		double rJB = rRup - distRupMinusJB_OverRup*rRup;
		

		// set default value of basin depth based on the final value of vs30
		// (must do this here because we get pga_rock below by passing in 1100 m/s)
		if(Double.isNaN(depthTo2pt5kmPerSec)){
			if(vs30 <= 2500)
				depthTo2pt5kmPerSec = 2;
			else
				depthTo2pt5kmPerSec = 0;
		}

		double pga_rock = Double.NaN;
		if(vs30 < k1[iper]) 
			pga_rock = Math.exp(getMean(2, 1100, rRup, rJB, f_rv, f_nm, mag,dip,depthTop, depthTo2pt5kmPerSec, magSaturation, 0));

		component = componentParam.getValue();

		double stdDev = getStdDev(iper, stdDevType, component, vs30, pga_rock);

		//System.out.println(stdDev+"\t"+iper+"\t"+stdDevType+"\t"+component+"\t"+vs30+"\t"+pga_rock);

		return stdDev;
	}

	/**
	 * Allows the user to set the default parameter values for the selected Attenuation
	 * Relationship.
	 */
	public void setParamDefaults() {

		vs30Param.setValueAsDefault();
		magParam.setValueAsDefault();
		fltTypeParam.setValueAsDefault();
		rupTopDepthParam.setValueAsDefault();
		distanceRupParam.setValueAsDefault();
		distRupMinusJB_OverRupParam.setValueAsDefault();
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		pgaParam.setValueAsDefault();
		pgvParam.setValueAsDefault();
		pgdParam.setValueAsDefault();
		componentParam.setValueAsDefault();
		stdDevTypeParam.setValueAsDefault();
		depthTo2pt5kmPerSecParam.setValueAsDefault();
		dipParam.setValueAsDefault();    
		vs30 = vs30Param.getValue(); 
		mag = magParam.getValue();
		stdDevType = stdDevTypeParam.getValue();

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
		meanIndependentParams.addParameter(distanceRupParam);
		meanIndependentParams.addParameter(distRupMinusJB_OverRupParam);
		meanIndependentParams.addParameter(vs30Param);
		meanIndependentParams.addParameter(depthTo2pt5kmPerSecParam);
		meanIndependentParams.addParameter(magParam);
		meanIndependentParams.addParameter(fltTypeParam);
		meanIndependentParams.addParameter(rupTopDepthParam);
		meanIndependentParams.addParameter(dipParam);
		meanIndependentParams.addParameter(componentParam);


		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
		stdDevIndependentParams.addParameterList(meanIndependentParams);
		stdDevIndependentParams.addParameter(stdDevTypeParam);

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
		depthTo2pt5kmPerSecParam = new DepthTo2pt5kmPerSecParam(DEPTH_2pt5_WARN_MIN, DEPTH_2pt5_WARN_MAX,true);

		siteParams.clear();
		siteParams.addParameter(vs30Param);
		siteParams.addParameter(depthTo2pt5kmPerSecParam);

	}

	/**
	 *  Creates the two Potential Earthquake parameters (magParam and
	 *  fltTypeParam) and adds them to the eqkRuptureParams
	 *  list. Makes the parameters noneditable.
	 */
	protected void initEqkRuptureParams() {

		magParam = new MagParam(MAG_WARN_MIN, MAG_WARN_MAX);
		dipParam = new DipParam(DIP_WARN_MIN,DIP_WARN_MAX);
		rupTopDepthParam = new RupTopDepthParam(RUP_TOP_WARN_MIN, RUP_TOP_WARN_MAX);

		StringConstraint constraint = new StringConstraint();
		constraint.addString(FLT_TYPE_STRIKE_SLIP);
		constraint.addString(FLT_TYPE_NORMAL);
		constraint.addString(FLT_TYPE_REVERSE);
		constraint.setNonEditable();
		fltTypeParam = new FaultTypeParam(constraint,FLT_TYPE_STRIKE_SLIP);

		eqkRuptureParams.clear();
		eqkRuptureParams.addParameter(magParam);
		eqkRuptureParams.addParameter(fltTypeParam);
		eqkRuptureParams.addParameter(dipParam);
		eqkRuptureParams.addParameter(rupTopDepthParam);
	}

	/**
	 *  Creates the Propagation Effect parameters and adds them to the
	 *  propagationEffectParams list. Makes the parameters noneditable.
	 */
	protected void initPropagationEffectParams() {

		distanceRupParam = new DistanceRupParameter(0.0);
		DoubleConstraint warn = new DoubleConstraint(DISTANCE_RUP_WARN_MIN,
				DISTANCE_RUP_WARN_MAX);
		warn.setNonEditable();
		distanceRupParam.setWarningConstraint(warn);
		distanceRupParam.addParameterChangeWarningListener(listener);

		distanceRupParam.setNonEditable();

		//create distRupMinusJB_OverRupParam
		distRupMinusJB_OverRupParam = new DistRupMinusJB_OverRupParameter(0.0);
		DoubleConstraint warnJB = new DoubleConstraint(DISTANCE_MINUS_WARN_MIN, DISTANCE_MINUS_WARN_MAX);
		distRupMinusJB_OverRupParam.addParameterChangeWarningListener(listener);
		warn.setNonEditable();
		distRupMinusJB_OverRupParam.setWarningConstraint(warnJB);
		distRupMinusJB_OverRupParam.setNonEditable();

		propagationEffectParams.addParameter(distanceRupParam);
		propagationEffectParams.addParameter(distRupMinusJB_OverRupParam);

	}

	/**
	 *  Creates the two supported IM parameters (PGA and SA), as well as the
	 *  independenParameters of SA (periodParam and dampingParam) and adds
	 *  them to the supportedIMParams list. Makes the parameters noneditable.
	 */
	protected void initSupportedIntensityMeasureParams() {

		// Create saParam:
		DoubleDiscreteConstraint periodConstraint = new DoubleDiscreteConstraint();
		for (int i = 3; i < per.length; i++) {
			periodConstraint.addDouble(new Double(per[i]));
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

		//  Create PGD Parameter (pgdParam):
		pgdParam = new PGD_Param();
		pgdParam.setNonEditable();

		// Add the warning listeners:
		saParam.addParameterChangeWarningListener(listener);
		pgaParam.addParameterChangeWarningListener(listener);
		pgvParam.addParameterChangeWarningListener(listener);
		pgdParam.addParameterChangeWarningListener(listener);

		// Put parameters in the supportedIMParams list:
		supportedIMParams.clear();
		supportedIMParams.addParameter(saParam);
		supportedIMParams.addParameter(pgaParam);
		supportedIMParams.addParameter(pgvParam);
		supportedIMParams.addParameter(pgdParam);
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
	    componentParam = new ComponentParam(Component.GMRotI50, Component.GMRotI50, Component.RANDOM_HORZ);

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


	/**
	 * 
	 * @param iper
	 * @param vs30
	 * @param rRup
	 * @param distJB
	 * @param f_rv
	 * @param f_nm
	 * @param mag
	 * @param depthTop
	 * @param depthTo2pt5kmPerSec
	 * @param magSaturation
	 * @param pga_rock
	 * @return
	 */
	public double getMean(int iper, double vs30, double rRup,
			double distJB,double f_rv,
			double f_nm, double mag, double dip, double depthTop,
			double depthTo2pt5kmPerSec,
			boolean magSaturation, double pga_rock) {

		// NSHM test correction
		//distJB = (distJB < 0.5) ? 0.5 : distJB;
		
		double fmag,fdis,fflt,fhng,fsite,fsed;

		//modeling depence on magnitude
		if(mag<= 5.5)
			fmag = c0[iper]+c1[iper]*mag;
		else if(mag > 5.5  && mag <=6.5)
			fmag = c0[iper]+c1[iper]*mag+c2[iper]*(mag-5.5);
		else
			fmag  = c0[iper]+c1[iper]*mag+c2[iper]*(mag-5.5)+c3[iper]*(mag - 6.5);

		//source to site distance
		fdis = (c4[iper]+c5[iper]*mag)*Math.log(Math.sqrt(rRup*rRup+c6[iper]*c6[iper]));

		//style of faulting
		double ffltz; //getting the depth top or also called Ztor in Campbell's paper
		if(depthTop <1)
			ffltz = depthTop;
		else
			ffltz = 1;

		// fault-style term
		fflt = c7[iper]*f_rv*ffltz+c8[iper]*f_nm;

		//hanging wall effects
		double fhngr;
		if(distJB == 0)
			fhngr = 1;
		else if(depthTop < 1 && distJB >0)
			fhngr = (Math.max(rRup,Math.sqrt(distJB*distJB+1)) - distJB)/
			Math.max(rRup,Math.sqrt(distJB*distJB+1));
		else
			fhngr = (rRup-distJB)/rRup;

		// if(pga_rock !=0) System.out.print((float)distJB+"\t"+(float)rRup+"\t"+fhngr+"\n");

		double fhngm;
		if(mag<=6.0)
			fhngm =0;
		else if(mag>6.0 && mag<6.5)
			fhngm = 2*(mag-6);
		else
			fhngm= 1;

		double fhngz;
		if(depthTop >=20)
			fhngz =0;
		else
			fhngz = (20-depthTop)/20;

		double fhngd;
		if(dip <= 70)
			fhngd =1;
		else
			fhngd = (90-dip)/20; 

		fhng = c9[iper]*fhngr*fhngm*fhngz*fhngd;

		//modelling dependence on linear and non-linear site conditions
		if(vs30< k1[iper])
			fsite = 	c10[iper]*Math.log(vs30/k1[iper]) +
			k2[iper]*(Math.log(pga_rock+c*Math.pow(vs30/k1[iper],n)) - Math.log(pga_rock+c));
		else if (vs30<1100)
			fsite = (c10[iper]+k2[iper]*n)*Math.log(vs30/k1[iper]);
		else 
			fsite = (c10[iper]+k2[iper]*n)*Math.log(1100/k1[iper]);

		//modelling depence on shallow sediments effects and 3-D basin effects
		if(depthTo2pt5kmPerSec<1)
			fsed = c11[iper]*(depthTo2pt5kmPerSec - 1);
		else if(depthTo2pt5kmPerSec <=3)
			fsed = 0;
		else
			fsed = c12[iper]*k3[iper]*Math.exp(-0.75)*(1-Math.exp(-0.25*(depthTo2pt5kmPerSec-3)));


		return fmag+fdis+fflt+fhng+fsite+fsed;
	}

	/**
	 * 
	 * @param iper
	 * @param stdDevType
	 * @param component
	 * @return
	 */
	public double getStdDev(int iper, String stdDevType, Component component, double vs30, double rock_pga) {

		if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_NONE)) return 0.0;

		// get tau - inter-event term
		double tau = t_lny[iper];

		// compute intra-event sigma
		double sigma;
		if(vs30 >= k1[iper])
			sigma = s_lny[iper];
		else {
			double s_lnYb = Math.sqrt(s_lny[iper]*s_lny[iper]-s_lnAF*s_lnAF);
			double s_lnAb = Math.sqrt(s_lny[2]*s_lny[2]-s_lnAF*s_lnAF); // iper=2 is for PGA
			double alpha = k2[iper]*rock_pga*((1/(rock_pga+c*Math.pow(vs30/k1[iper], n)))-1/(rock_pga+c));
			sigma = Math.sqrt(s_lnYb*s_lnYb + s_lnAF*s_lnAF + alpha*alpha*s_lnAb*s_lnAb + 2*alpha*rho[iper]*s_lnYb*s_lnAb);
		}

		// compute total sigma
		double sigma_total = Math.sqrt(tau*tau + sigma*sigma);

		// compute multiplicative factor in case component is random horizontal
		double random_ratio;
		if(component == Component.RANDOM_HORZ)
			random_ratio = Math.sqrt(1 + (s_c[iper]*s_c[iper])/(sigma_total*sigma_total));
		else
			random_ratio = 1;

		// return appropriate value
		if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_TOTAL))
			return sigma_total*random_ratio;
		else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_INTRA))
			return sigma*random_ratio;
		else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_INTER))
			return tau*random_ratio;
		else
			return Double.NaN;   // just in case invalid stdDev given			  
	}

	/**
	 * This listens for parameter changes and updates the primitive parameters accordingly
	 * @param e ParameterChangeEvent
	 */
	public void parameterChange(ParameterChangeEvent e) {

		String pName = e.getParameterName();
		Object val = e.getNewValue();
		if (pName.equals(DistanceRupParameter.NAME)) {
			rRup = ( (Double) val).doubleValue();
		}
		else if (pName.equals(DistRupMinusJB_OverRupParameter.NAME)) {
			distRupMinusJB_OverRup = ( (Double) val).doubleValue();
		}
		else if (pName.equals(Vs30_Param.NAME)) {
			vs30 = ( (Double) val).doubleValue();
		}
		else if (pName.equals(DepthTo2pt5kmPerSecParam.NAME)) {
			if(val == null)
				depthTo2pt5kmPerSec = Double.NaN;  // can't set the default here because vs30 could still change
			else
				depthTo2pt5kmPerSec = ( (Double) val).doubleValue();
		}
		else if (pName.equals(MagParam.NAME)) {
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
		else if (pName.equals(StdDevTypeParam.NAME)) {
			stdDevType = (String) val;
		}
		else if (pName.equals(DipParam.NAME)) {
			dip = ( (Double) val).doubleValue();
		}
		else if (pName.equals(ComponentParam.NAME)) {
			component = componentParam.getValue();
		}
		else if (pName.equals(PeriodParam.NAME)) {
			intensityMeasureChanged = true;
		}
	}

	/**
	 * Allows to reset the change listeners on the parameters
	 */
	public void resetParameterEventListeners(){
		distanceRupParam.removeParameterChangeListener(this);
		distRupMinusJB_OverRupParam.removeParameterChangeListener(this);
		vs30Param.removeParameterChangeListener(this);
		depthTo2pt5kmPerSecParam.removeParameterChangeListener(this);
		magParam.removeParameterChangeListener(this);
		fltTypeParam.removeParameterChangeListener(this);
		rupTopDepthParam.removeParameterChangeListener(this);
		dipParam.removeParameterChangeListener(this);
		stdDevTypeParam.removeParameterChangeListener(this);
		saPeriodParam.removeParameterChangeListener(this);

		this.initParameterEventListeners();
	}

	/**
	 * Adds the parameter change listeners. This allows to listen to when-ever the
	 * parameter is changed.
	 */
	protected void initParameterEventListeners() {

		distanceRupParam.addParameterChangeListener(this);
		distRupMinusJB_OverRupParam.addParameterChangeListener(this);
		vs30Param.addParameterChangeListener(this);
		depthTo2pt5kmPerSecParam.addParameterChangeListener(this);
		magParam.addParameterChangeListener(this);
		fltTypeParam.addParameterChangeListener(this);
		rupTopDepthParam.addParameterChangeListener(this);
		stdDevTypeParam.addParameterChangeListener(this);
		saPeriodParam.addParameterChangeListener(this);
		dipParam.addParameterChangeListener(this);
	}


	/**
	 * This provides a URL where more info on this model can be obtained
	 * @throws MalformedURLException if returned URL is not a valid URL.
	 * @return the URL to the AttenuationRelationship document on the Web.
	 */
	public URL getInfoURL() throws MalformedURLException{
		return new URL("http://www.opensha.org/glossary-attenuationRelation-CAMPBELL_BOZORG_2008");
	}


	/**
	 * This tests DistJB numerical precision with respect to the f_hngR term.  Looks OK now.
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

		CB_2008_AttenRel attenRel = new CB_2008_AttenRel(null);
		attenRel.setParamDefaults();
		attenRel.setIntensityMeasure("PGA");
		attenRel.setEqkRupture(rup);

		Site site = new Site();
		site.addParameter(attenRel.getParameter(Vs30_Param.NAME));
		site.addParameter(attenRel.getParameter(DepthTo2pt5kmPerSecParam.NAME));

		Location loc;
		for(double dist=-0.3; dist<=0.3; dist+=0.01) {
			loc = new Location(0,dist);
			site.setLocation(loc);
			attenRel.setSite(site);
			attenRel.getMean();
		}

	}

}
