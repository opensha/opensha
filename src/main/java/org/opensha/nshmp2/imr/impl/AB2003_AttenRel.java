package org.opensha.nshmp2.imr.impl;

import static org.opensha.nshmp2.util.Utils.SQRT_2;
import static org.opensha.sha.util.TectonicRegionType.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.nshmp2.util.Params;
import org.opensha.nshmp2.util.Utils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.PropagationEffect;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.collect.Maps;

/**
 * Implementation of the subduction zone attenuation relationship by Atkinson
 * and Boore (2003) that matches that used in the 2008 USGS NSHMP. The
 * implementation has global and Cascadia-specific forms and can be used
 * for both slab and interface events. In the 2008 NSHMP, the 'interface' form
 * is used with the Cascadia subduction zone models and the 'slab' form is used
 * with gridded 'deep' events in northern California and the Pacific Northwest.
 * The default state of the attenuation relation is 'global' and 'interface'.
 * 
 * <p><b>See:</b> Atkinson, G.M. and Boore, D.M., 2003, Empirical Ground-Motion
 * Relations for Subduction-Zone Earthquakes and Their Application to Cascadia
 * and Other Regions: Bulletin of the Seismological Society of America, v. 93,
 * p. 1012–1033.</p>
 * 
 * @author Peter Powers
 * @version $Id$
 */
public class AB2003_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	// NOTE RupTopDepthParam is used in a funny way here (see also Youngs/
	// Geomatrix). Currently it is not adjusted when updating an earthquake
	// rupture, but must be set manually. It defaults to 20km (the value
	// imposed in the 2008 NSHMP Cascadia subduction interface model. Any other
	// sources should update this value independently.
	
	public final static String SHORT_NAME = "AB2003";
	public final static String NAME = "Atkinson and Boore (2003) Subduction";
	private static final long serialVersionUID = 1L;

	// coefficients:
	// private static final double[] pd = { 0.0, 0.2, 1.0, 0.1, 0.3, 0.4, 0.5,
	// 0.75, 2.0, 3.0 };
	// private static final double[] s1g = { 2.991, 2.5711536, 2.1442, 2.7789,
	// 2.6168785, 2.6175463, 2.536019, 2.288355, 2.1907, 2.301 };
	// private static final double[] s2g = { 0.03525, 0.13976128, 0.1345,
	// 0.09841, 0.13694176, 0.13179871, 0.1324168, 0.13728201, 0.07148, 0.02237
	// };
	// private static final double[] s3g = { 0.00759, 7.79948E-3, 0.00521,
	// 0.00974, 8.12251E-3, 8.32052E-3, 7.951397E-3, 6.429092E-3, 0.00224,
	// 0.00012 };
	// private static final double[] s4g = { -0.00206, -2.49985E-3, -0.0011,
	// -0.00287, -2.6353553E-3, -2.6501498E-3, -2.4560375E-3, -1.7370114E-3,
	// 0.0, 0.0 };
	// private static final double[] s5g = { 0.19, 0.13666, 0.10, 0.15,
	// 0.1429013, 0.14334, 0.13579784, 0.11287035, 0.10, 0.10 };
	// private static final double[] s6g = { 0.24, 0.32338, 0.30, 0.23,
	// 0.30005046, 0.27662, 0.32512766, 0.2953982, 0.25, 0.25 };
	// private static final double[] s7g = { 0.29, 0.33671, 0.55, 0.2,
	// 0.29974141, 0.29329, 0.34473022, 0.49243947, 0.4, 0.36 };
	// private static final double[] ssig = { 0.23, 0.28, 0.34, 0.27, 0.286,
	// 0.29, 0.31, 0.34, 0.34, 0.36 }; // base10 sigma

	private static final double[] pd = { 0., 0.2, 1.0, 0.1, 0.3, 0.5, 0.75, 2.0, 3. };

	private static final double[] c1 = { -0.25, 0.40, -0.98, 0.160, 0.195, -0.172, -0.67648, -2.250, -3.64 };
	private static final double[] c1w = { -0.04713, 0.51589, -1.02133, 0.43928, 0.26067, -0.16568, -0.69924, -2.39234, -3.70012 };
	private static final double[] c2 = { 0.6909, 0.69186, 0.8789, 0.66675, 0.73228, 0.7904, -.84559, 0.99640, 1.1169 };
	private static final double[] c3 = { 0.01130, 0.00572, 0.00130, 0.0108, 0.00372, 0.00166, 0.0014349, 0.00364, .00615 };
	private static final double[] c4 = { -0.00202, -0.00192, -0.00173, -0.00219, -0.00185, -0.00177, -.0017457, -0.00118, -0.00045 };
	private static final double[] c5 = { 0.19, 0.15, 0.10, .15, 0.1383, 0.125, .10941, 0.100, 0.1 };
	private static final double[] c6 = { 0.24, 0.27, 0.30, 0.23, 0.3285, 0.353, 0.322, 0.25, 0.25 };
	private static final double[] c7 = { 0.29, 0.25, 0.55, 0.20, 0.3261, 0.4214, 0.4966, 0.40, 0.36 };
	private static final double[] sigs = { 0.27, 0.28, 0.29, .28, 0.280, 0.282, 0.2869, 0.300, 0.30 };

	private static final double[] s1 = { 2.79, 2.54, 2.18, 2.5, 2.516, 2.418, 2.241635, 2.33, 2.36 };
	private static final double[] s1g = { 2.991, 2.5711536, 2.1442, 2.7789, 2.6168785, 2.536019, 2.288355, 2.1907, 2.301 };
	private static final double[] s2 = { .03525, .12386, .1345, .09841, .1373, .1444, 0.14504924, .07148, .02237 };
	private static final double[] s2g = { .03525, 0.13976128, .1345, .09841, 0.13694176, 0.1324168, 0.13728201, .07148, .02237 };
	private static final double[] s3 = { .00759, .00884, .00521, .00974, .00789, .00671, 5.9208343E-3, .00224, .00012 };
	private static final double[] s3g = { .00759, 7.79948E-3, .00521, .00974, 8.12251E-3, 7.951397E-3, 6.429092E-3, .00224, .00012 };
	private static final double[] s4 = { -0.00206, -.0028, -0.0011, -.00287, -.00252, -.00195, -1.5903986E-3, 0., 0. };
	private static final double[] s4g = { -0.00206, -2.49985E-3, -0.0011, -.00287, -2.6353553E-3, -2.4560375E-3, -1.7370114E-3, 0., 0. };
	private static final double[] s5 = { 0.19, 0.15, 0.10, 0.15, 0.14, 0.12, 0.106354214, 0.10, 0.10 };
	private static final double[] s5g = { 0.19, 0.13666, 0.10, 0.15, 0.1429013, 0.13579784, 0.11287035, 0.10, 0.10 };
	private static final double[] s6 = { 0.24, 0.23, 0.30, 0.23, 0.253, 0.277, 0.34101113, .25, 0.25 };
	private static final double[] s6g = { 0.24, 0.32338, 0.30, 0.23, 0.30005046, 0.32512766, 0.2953982, .25, 0.25 };
	private static final double[] s7 = { 0.29, 0.25, 0.55, 0.2, 0.319, 0.416, 0.53479433, 0.4, 0.36 };
	private static final double[] s7g = { 0.29, 0.33671, 0.55, 0.2, 0.29974141, 0.34473022, 0.49243947, 0.4, 0.36 };
	private static final double[] sigi = { 0.23, 0.28, 0.34, 0.27, 0.286, 0.31, 0.34, 0.34, 0.36 };

	private HashMap<Double, Integer> indexFromPerHashMap;

	private int iper;
	private double rRup, mag, vs30, depth;
	private TectonicRegionType subType;
	private boolean global;
	
	// parent TRTParam should be updated to enumParam
	private EnumParameter<TectonicRegionType> subTypeParam;
	private BooleanParameter globalParam;

	private transient ParameterChangeWarningListener warningListener = null;

	/**
	 * This initializes several ParameterList objects.
	 * @param listener
	 */
	public AB2003_AttenRel(ParameterChangeWarningListener listener) {
		warningListener = listener;
		initSupportedIntensityMeasureParams();
		indexFromPerHashMap = Maps.newHashMap();
		for (int i = 0; i < pd.length; i++) {
			indexFromPerHashMap.put(new Double(pd[i]), new Integer(i));
		}

		initEqkRuptureParams();
		initPropagationEffectParams();
		initSiteParams();
		initOtherParams();

		initIndependentParamLists(); // This must be called after the above
		initParameterEventListeners(); // add the change listeners to the
		
		setParamDefaults();
	}

	@Override
	public void setEqkRupture(EqkRupture eqkRupture)
			throws InvalidRangeException {
		magParam.setValueIgnoreWarning(new Double(eqkRupture.getMag()));
		this.eqkRupture = eqkRupture;
		setPropagationEffectParams();
	}

	@Override
	public void setSite(Site site) throws ParameterException {
		vs30Param.setValueIgnoreWarning((Double) site.getParameter(
			Vs30_Param.NAME).getValue());
		this.site = site;
		setPropagationEffectParams();
	}

	@Override
	protected void setPropagationEffectParams() {
		if ((site != null) && (eqkRupture != null)) {
			distanceRupParam.setValue(eqkRupture, site);
		}
	}

	private void setCoeffIndex() throws ParameterException {
		if (im == null) {
			throw new ParameterException("Intensity Measure Param not set");
		}
		iper = indexFromPerHashMap.get(saPeriodParam.getValue());
		intensityMeasureChanged = false;
	}

	@Override
	public double getMean() {
		if (rRup > USER_MAX_DISTANCE) {
			return VERY_SMALL_MEAN;
		}
		if (intensityMeasureChanged) {
			setCoeffIndex(); // updates intensityMeasureChanged
		}
		return getMean(iper, vs30, mag, rRup, depth, subType, global);
	}

	@Override
	public double getStdDev() {
		// updates intensityMeasureChanged
		if (intensityMeasureChanged) setCoeffIndex();
		return getStdDev(iper, subType.equals(SUBDUCTION_SLAB));
	}

	@Override
	public void setParamDefaults() {
		vs30Param.setValueAsDefault();
		subTypeParam.setValueAsDefault(); // shouldn't be necessary
		globalParam.setValueAsDefault(); // shouldn't be necessary
		
		magParam.setValueAsDefault();
		distanceRupParam.setValueAsDefault();
		rupTopDepthParam.setValueAsDefault();
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		pgaParam.setValueAsDefault();
		// stdDevTypeParam.setValueAsDefault();

		vs30 = vs30Param.getValue();
		subType = subTypeParam.getValue();
		global = globalParam.getValue();
		rRup = distanceRupParam.getValue();
		mag = magParam.getValue();
	}

	/**
	 * This creates the lists of independent parameters that the various
	 * dependent parameters (mean, standard deviation, exceedance probability,
	 * and IML at exceedance probability) depend upon. NOTE: these lists do not
	 * include anything about the intensity-measure parameters or any of thier
	 * internal independentParamaters.
	 */
	protected void initIndependentParamLists() {

		// params that the mean depends upon
		meanIndependentParams.clear();
		meanIndependentParams.addParameter(distanceRupParam);
		meanIndependentParams.addParameter(rupTopDepthParam);
		meanIndependentParams.addParameter(vs30Param);
		meanIndependentParams.addParameter(magParam);
		meanIndependentParams.addParameter(subTypeParam);
		meanIndependentParams.addParameter(globalParam);

		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
		stdDevIndependentParams.addParameter(subTypeParam);
		
		// params that the exceed. prob. depends upon
		exceedProbIndependentParams.clear();
		exceedProbIndependentParams.addParameterList(meanIndependentParams);
		exceedProbIndependentParams.addParameter(sigmaTruncTypeParam);
		exceedProbIndependentParams.addParameter(sigmaTruncLevelParam);

		// params that the IML at exceed. prob. depends upon
		imlAtExceedProbIndependentParams
			.addParameterList(exceedProbIndependentParams);
		imlAtExceedProbIndependentParams.addParameter(exceedProbParam);
	}

	@Override
	protected void initSiteParams() {
		vs30Param = new Vs30_Param(150.0, 1500.0);
		siteParams.clear();
		siteParams.addParameter(vs30Param);
	}

	@Override
	protected void initEqkRuptureParams() {
		magParam = new MagParam(7.5);
		rupTopDepthParam = new RupTopDepthParam(0, 100, 20);
		eqkRuptureParams.clear();
		eqkRuptureParams.addParameter(magParam);
		eqkRuptureParams.addParameter(rupTopDepthParam);
	}

	@Override
	protected void initPropagationEffectParams() {
		distanceRupParam = new DistanceRupParameter(0.0);
		distanceRupParam.addParameterChangeWarningListener(listener);
		distanceRupParam.setNonEditable();
		propagationEffectParams.addParameter(distanceRupParam);
	}

	@Override
	protected void initSupportedIntensityMeasureParams() {

		// Create saParam:
		DoubleDiscreteConstraint perConstraint = new DoubleDiscreteConstraint();
		for (int i = 0; i < pd.length; i++) {
			perConstraint.addDouble(new Double(pd[i]));
		}
		perConstraint.setNonEditable();
		saPeriodParam = new PeriodParam(perConstraint);
		saDampingParam = new DampingParam();
		saParam = new SA_Param(saPeriodParam, saDampingParam);
		saParam.setNonEditable();

		// Create PGA Parameter (pgaParam):
		pgaParam = new PGA_Param();
		pgaParam.setNonEditable();

		// Add the warning listeners:
		saParam.addParameterChangeWarningListener(warningListener);
		pgaParam.addParameterChangeWarningListener(warningListener);

		// Put parameters in the supportedIMParams list:
		supportedIMParams.clear();
		supportedIMParams.addParameter(saParam);
		supportedIMParams.addParameter(pgaParam);
	}

	@Override
	protected void initOtherParams() {
		super.initOtherParams();
		subTypeParam = Params.createSubType();
		globalParam = Params.createGlobalFlag();
		otherParams.addParameter(subTypeParam);
		otherParams.addParameter(globalParam);
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public void parameterChange(ParameterChangeEvent e) {
		String pName = e.getParameterName();
		Object val = e.getNewValue();
		if (pName.equals(DistanceRupParameter.NAME)) {
			rRup = (Double) val;
		} else if (pName.equals(Vs30_Param.NAME)) {
			vs30 = (Double) val;
		} else if (pName.equals(MagParam.NAME)) {
			mag = (Double) val;
		} else if (pName.equals(PeriodParam.NAME)) {
			intensityMeasureChanged = true;
		} else if (pName.equals(subTypeParam.getName())) {
			subType = (TectonicRegionType) val;
		} else if (pName.equals(globalParam.getName())) {
			global = (Boolean) val;
		} else if (pName.equals(RupTopDepthParam.NAME)) {
			depth = (Double) val;
		}

	}

	@Override
	public void resetParameterEventListeners() {
		distanceRupParam.removeParameterChangeListener(this);
		rupTopDepthParam.removeParameterChangeListener(this);
		vs30Param.removeParameterChangeListener(this);
		magParam.removeParameterChangeListener(this);
		saPeriodParam.removeParameterChangeListener(this);
		subTypeParam.removeParameterChangeListener(this);
		globalParam.removeParameterChangeListener(this);
		this.initParameterEventListeners();
	}

	@Override
	protected void initParameterEventListeners() {
		distanceRupParam.addParameterChangeListener(this);
		rupTopDepthParam.addParameterChangeListener(this);
		vs30Param.addParameterChangeListener(this);
		magParam.addParameterChangeListener(this);
		saPeriodParam.addParameterChangeListener(this);
		subTypeParam.addParameterChangeListener(this);
		globalParam.addParameterChangeListener(this);
	}

	/**
	 * @throws MalformedURLException if returned URL is not a valid URL.
	 * @return the URL to the AttenuationRelationship document on the Web.
	 */
	public URL getAttenuationRelationshipURL() throws MalformedURLException {
		return null;
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls) {
		return Utils.getExceedProbabilities(imls, getMean(), getStdDev(), 
			false, 0.0);
	}

	private static final double ALN10 = 2.30258509; // ln(10)
	private static final double gfac = 2.9912261; // log10(980)

	private double getStdDev(int iper, boolean slab) {
//		return (slab) ? sigs[iper] : sigi[iper];
		
		double sigma = (slab) ? sigs[iper] : sigi[iper];
//		System.out.println("ABsig " + sigma );
		return sigma * ALN10;
//		return 1.0 / sigma / SQRT_2 / ALN10;
	}

	public double getMean(int ip, double vs30, double mag, double rRup, double depth,
			TectonicRegionType trt, boolean global) {
		double gnd0, r1, c02, c03, c04, c05, c06, c07;

		boolean slab = trt.equals(SUBDUCTION_SLAB) ? true : false;

		double period = pd[ip];
		double freq = (period == 0.0) ? 100 : 1.0 / period;

		double r2, r3, r4;

		// @formatter:off
		if (slab) {
			r2 = c2[0]; r3 = c3[0]; r4 = c4[0];
			c02 = c2[ip]; c03 = c3[ip]; c04 = c4[ip];
			c05 = c5[ip]; c06 = c6[ip]; c07 = c7[ip];
			if (global) {
				r1 = c1w[0]; gnd0 = c1w[ip];
			} else {
				r1 = c1[0]; gnd0 = c1[ip];
			}
		} else {
			r2 = s2[0]; r3 = s3[0]; r4 = s4[0];
			if (global) {
				r1 = s1g[0]; gnd0 = s1g[ip];
				c02 = s2g[ip]; c03 = s3g[ip]; c04 = s4g[ip];
				c05 = s5g[ip]; c06 = s6g[ip]; c07 = s7g[ip];
			} else {
				r1 = s1[0]; gnd0 = s1[ip];
				c02 = s2[ip]; c03 = s3[ip]; c04 = s4[ip];
				c05 = s5[ip]; c06 = s6[ip]; c07 = s7[ip];
			}
		}
		// @formatter:on

		// "saturation effect" p. 1709 AB 2003
		mag = Math.min(mag, slab ? 8.0 : 8.5);

		double delta = 0.00724 * Math.pow(10, 0.507 * mag);
		double g;

		if (slab) {
			g = Math.pow(10, 0.301 - 0.01 * mag);
		} else {
			// c g = 2 for M=5 event.
			g = Math.pow(10, 1.2 - 0.18 * mag);
		}
		double gndm = gnd0 + c02 * mag;

		// as far as I can tell from hazSUBXnga and hazSUBXngatest, interface
		// events are fixed at depth = 20km, slab events from hazgrid are
		// variable but limited to <100km (this constraint is ignored; not
		// sure where it comes from; see depthp in hazgrid)
		// NOTE this constraint has been removed in favor of setting a default
		// depth of 20km in NSHMP08_SUB_Interface which is more appropriate as
		// the 20km value is NSHMP specific.
		// if (!slab) depth = 20;
		
		//double dsq = depth * depth;
		//double dist = Math.sqrt(dist0 * dist0 + dsq);
		double dist2 = Math.sqrt(rRup * rRup + delta * delta);
		double gnd = gndm + c03 * depth + c04 * dist2 - g * Math.log10(dist2);
		double rpga = r1 + r2 * mag + r3 * depth + r4 * dist2 - g *
			Math.log10(dist2);
		rpga = Math.pow(10, rpga);

		double sl;
		if ((rpga <= 100.0) || (freq <= 1.0)) {
			sl = 1.0;
		} else if ((rpga > 100.0) && (rpga < 500.0) && (freq > 1.0) &&
			(freq < 2.0)) {
			sl = 1.0 - (freq - 1.) * (rpga - 100) / 400.0;
		} else if ((rpga >= 500.0) && (freq > 1.0) && (freq < 2.0)) {
			sl = 1. - (freq - 1.);
		} else if ((rpga > 100.) && (rpga < 500.0) && (freq >= 2.0)) {
			sl = 1. - (rpga - 100.) / 400.;
			// c if((rpga.ge.500.).and.(freq.ge.2.)) sl= 0.
		} else {
			sl = 0.0;
		}

		if (slab) {
			if (vs30 > 780.0) { // B-rock
				gnd = gnd - gfac;
			} else if (vs30 > 660.) { // BC-rock
				gnd = gnd + (sl * c05) * 0.5 - gfac;
			} else if (vs30 > 360.) { // C-soil
				gnd = gnd + sl * c05 - gfac;
			} else if (vs30 > 190.) { // D-soil
				gnd = gnd + sl * c06 - gfac;
			} else { // DE or E-soil
				gnd = gnd + sl * c07 - gfac;
			}
		} else {
			// in NSHMP, interface site amplification is more refined, why?
			if (vs30 > 900) {
				gnd = gnd - gfac;
			} else if (vs30 > 720) { // BC boundary
				gnd = gnd + (sl * c05) * 0.5 - gfac;
			} else if (vs30 >= 380) { // site class C
				gnd = gnd + sl * c05 - gfac;
			} else if (vs30 >= 350) { // CD boundary
				gnd = gnd + 0.5 * sl * (c05 + c06) - gfac;
			} else if (vs30 >= 190) { // site class D
				gnd = gnd + sl * c06 - gfac;
			} else if (vs30 >= 170) { // DE boundary
				gnd = gnd + 0.5 * sl * (c06 + c07) - gfac;
			} else { // site class E
				gnd = gnd + sl * c07 - gfac;
			}
		}

		// if (ir == 0 || ir == 5) { // > 780
		// gnd = gnd - gfac;
		// } else if (ir == 1 || ir == 6) { // 660-780 log ave of B and C
		// gnd = gnd + (sl * c05) * 0.5 - gfac;
		// } else if (ir == 2 || ir == 7) { // 360-660
		// gnd = gnd + sl * c05 - gfac;
		// } else if (ir == 3 || ir == 8) { // 190-360
		// gnd = gnd + sl * c06 - gfac;
		// } else if (ir == 4 || ir == 9) { // 0-190
		// gnd = gnd + sl * c07 - gfac;
		// }

		gnd = gnd * ALN10;
//		System.out.println("abrpga "+r1+" "+r2+" "+mag+" "+r3+" "+depth+" "+r4+" "+dist2+" "+g);
//		System.out.println("AB03 " + vs30 + " " + gnd + " " + rRup + " " + depth + " " + rpga + 
//			" " + gndm + " " + gnd0 + " " + ip);
		return gnd;
	}

	// ccccccccccccccc
	// subroutine getABsub(ip,iq,ir,slab,ia,ndist,di,nmag,
	// & magmin,dmag,vs30)
	// c +++ Atkinson and Boore subduction zone intraslab.
	// c modified for gfortran, f95 Oct 2006.
	// c +++ Add interface source modeling July 14 2010.
	// c mod Oct 22 2008. M upper limit at 8.0 (AB03, BSSA v93 #4, p 1709)
	// c this subr. was slightly modified apr 10 2007, for NEHRP C- and D- site
	// classes
	// c See A&B BSSA v93 # 4 pp1703+.
	// c Input vars:
	// c ip = global period index, ip may be 1 to npmax (10?)
	// c iq = index of period in peri or perx array below.
	// c ir controls rock or soil, pacnw or world
	// c ir=0 PNW b rock (this was not in code)
	// c ir=1 PNW bc rock
	// c ir=2 PNW, NEHRP c soil (about 500-550 m/s)
	// c ir=3 PNW, NEHRP D soil
	// c ir=4 PNW, NEHRP E soil
	// c ir=5 Worldwide b rock
	// c ir=6 Worldwide bc rock
	// c ir = 7 Worldwide, NEHRP c soil (about 500-550 m/s)
	// c ir=8 Worldwide NEHRP D soil
	// c ir=9 Worldwide NEHRP E soil
	// c slab = logical var: .false. for interface .true. for intraplate or slab
	// eq.
	// c magmin = smallest M for filling pr() array
	// c vs30 = avg vs in top 30 m. added july 2009. SH. Site response defined
	// in
	// c broad NEHRP site classes, is not continuous with Vs30.
	// c Outputs:
	// c pr(dist, mag, gmindex,...) = probability of exceedance array.
	// c
	// c +++ Coeffs for 9 spectral pds.
	// parameter (np=9,sqrt2=1.4142136,gfac=2.9912261,aln10=2.30258509)
	// c
	// c gfac = log10(980). rc1 is region dependent; ww c1w coeff mod mar 22
	// 2007
	// parameter(rc2= 0.6909,rc3= 0.01130,rc4= -0.00202, vref=760.)
	// parameter(rs2=0.03525,rs3=0.00759,rs4=-0.00206)
	// logical deagg
	// real magmin
	// common/depth_rup/ntor,dtor(3),wtor(3),wtor65(3)
	// c wtor = weights applied to top of Benioff zone locations (km).
	// c These are applied in main, as factors to rate matrices.
	// c do not apply wtor here. dtor replaces "depth" of 2002 code. Dtor allows
	// a distribution if
	// c you are uncertain about what that depth is.
	// common/prob/p(25005),plim,dp2 !table of complementary normal probab.
	// common/e0_sub/e0_sub(260,31,8,3)
	// c last dim of e0_sub is ia model,
	// common / atten / pr, xlev, nlev, iconv, wt, wtdist
	// common/deagg/deagg
	// logical slab
	// dimension pr(260,38,20,8,3,3),xlev(20,8),nlev(8),iconv(8,8),
	// + wt(8,8,2),wtdist(8,8)
	// real, dimension(np) :: c1,c1w,c2,c3,c4,c5,c6,c7,sig,perx
	// real, dimension(np+1) :: s1,s2,s3,s4,s5,s6,s7,pcor,ssig,peri
	// real, dimension(np+1) :: s1g,s2g,s3g,s4g,s5g,s6g,s7g
	// real period
	// c array constructors oct 2006. Add 3s SA Feb 2008. Add 0.75s dec 08
	// c add c7 may 13 2009. C7 corresponds to E soil.
	// if(slab)then
	// r2=rc2; r3=rc3; r4=rc4
	// perx= (/0.,0.2,1.0,0.1,0.3,0.5,0.75,2.0,3./) !-1 shall be reserved for
	// pgv
	// c1= (/ -0.25,0.40,-0.98,0.160,0.195,-0.172,-0.67648,-2.250,-3.64/)
	// c1w=(/-0.04713,0.51589,-1.02133,0.43928,0.26067,-0.16568,-0.69924,-2.39234,
	// + -3.70012/) ! global c1 coeffs.
	// c2=
	// (/0.6909,0.69186,0.8789,0.66675,0.73228,0.7904,-.84559,0.99640,1.1169/)
	// c3=
	// (/0.01130,0.00572,0.00130,0.0108,0.00372,0.00166,0.0014349,0.00364,.00615/)
	// c4=
	// (/-0.00202,-0.00192,-0.00173,-0.00219,-0.00185,-0.00177,-.0017457,-0.00118,
	// + -0.00045/)
	// c5= (/0.19,0.15,0.10,.15,0.1383,0.125,.10941,0.100 ,0.1/)
	// c6= (/0.24,0.27,0.30,0.23,0.3285,0.353,0.322,0.25,0.25/)
	// c7= (/0.29,0.25,0.55,0.20,0.3261,0.4214,0.4966,0.40,0.36/)
	// sig= (/0.27,0.28,0.29,.28,0.280,0.282,0.2869,0.300,0.30/) !BASE 10 SIGMA
	// period = perx(iq)
	// sigmasq= sig(iq)*sqrt2*aln10
	// else !subduction
	// c For subduction events, recommendation is M7.5 up and R<300 km.
	// c coefficients for subduction, Table 1 p 1715 & T3, p 1726.
	// c Definitions: s1g global, s1 Cascadia.
	// c 10/17/2008: Cubic Splines were used for several 3.33 and 2 hz
	// Global-estimation coefs.
	// c Interested in the details? See intAB03.table.f for src code. uses
	// Numerical recipes.
	// c s1g has been recomputed for 2.0, 2.5, 3.33 and 5hz. Ditto s2g ,...
	// c Cannot Add 4 and 5 s. Not available. Nov 19 2008.
	// peri= (/0.,0.2,1.0,0.1,0.3,0.4,0.5,0.75,2.0,3.0/)
	// r2=rs2; r3=rs3; r4=rs4
	// s1g=(/2.991,2.5711536,2.1442,2.7789,2.6168785,2.6175463,2.536019,2.288355,2.1907,2.301
	// + /)
	// s1=(/2.79,2.54,2.18,2.5,2.516,2.50,2.418,2.241635,2.33,2.36/)
	// s2=(/.03525,.12386,.1345,.09841,.1373,0.1477,.1444,0.14504924,.07148,.02237/)
	// s2g=(/.03525,0.13976128,.1345,.09841,0.13694176,0.13179871,0.1324168,0.13728201,.07148,.02237/)
	// s3=(/.00759,.00884,.00521,.00974,.00789,0.00728,.00671,5.9208343E-3,.00224,.00012/)
	// s3g=(/.00759,7.79948E-3,.00521,.00974,8.12251E-3,8.32052E-3,7.951397E-3,6.429092E-3,.00224,.00012/)
	// s4=(/-0.00206,-.0028,-0.0011,-.00287,-.00252,-0.00235,-.00195,-1.5903986E-3,0.,0./)
	// s4g=(/-0.00206,-2.49985E-3,-0.0011,-.00287,-2.6353553E-3,-2.6501498E-3,-2.4560375E-3
	// ,-1.7370114E-3,0.,0./)
	// s5=(/0.19,0.15,0.10,0.15,0.14,0.13,0.12,0.106354214,0.10,0.10/)
	// s5g=(/0.19,0.13666,0.10,0.15,0.1429013,0.14334,0.13579784,0.11287035,0.10,0.10/)
	// s6= (/0.24,0.23,0.30,0.23,0.253,0.37,0.277,0.34101113,.25,0.25/)
	// s6g=
	// (/0.24,0.32338,0.30,0.23,0.30005046,0.27662,0.32512766,0.2953982,.25,0.25/)
	// c s7 corresponds to site class E
	// s7=(/0.29,0.25,0.55,0.2,0.319,0.38,0.416,0.53479433,0.4,0.36/)
	// s7g=(/0.29,0.33671,0.55,0.2,0.29974141,0.29329,0.34473022,0.49243947,0.4,0.36/)
	// ssig=(/0.23,0.28,0.34,0.27,0.286,0.29,0.31,0.34,0.34,0.36/)
	// pcor= (/-0.00298,-0.00290,-0.00536,0.,-0.00225,0.,0.,0.,0.,-0.0052/)
	// period = peri(iq)
	// sigma=ssig(iq)
	// sigmasq=sigma*sqrt2*aln10
	// endif
	// c set up erf matrix p as ftn of dist,mag,period,level,flt type,atten type
	// sigmaf= 1.0/sigmasq
	// if(period.ne.0.)then
	// freq= 1./period
	// else
	// freq= 100.
	// endif
	// amp_nl = 0.0 !nonlinear siteamp addition AF
	// c if(ip.eq.1)open(15,file='ab.tmp',status='unknown')
	// c write(6,*)'Ab data going to log file nmag, ntor=',nmag,ntor

	// public double getMean2(int ip, double mag, TectonicRegionType trt,
	// double depth, double dist0, boolean global) {
	// double gnd0, r1, c02, c03, c04, c05, c06, c07;
	//
	// boolean slab = trt.equals(SUBDUCTION_SLAB) ? true : false;
	//
	// double period = pd[ip];
	// double freq = (period == 0.0) ? 100 : 1.0 / period;
	//
	// double r2, r3, r4;
	// if (slab) {
	// r2 = rc2;
	// r3 = rc3;
	// r4 = rc4;
	// } else {
	// r2 = rs2;
	// r3 = rs3;
	// r4 = rs4;
	// }
	// int ir;
	// // call getABsub(ip,jabs,ir,slab,ia,ndist,di,nmag,magmin,dmag,vs30)
	// // c getABsub for world data set, gets new index, 17 (subd) or 18
	// // (slab).
	// // elseif(ipiaa.eq.17.or.ipiaa.eq.18.and.okabs)then
	// if (global) {
	// if (vs30 > 780.) {
	// ir = 5;
	// // write(6,*)'AB World B-rock called, seism. at ',dtor(1),' km'
	// } else if (vs30 > 660.) {
	// // write(6,*)'AB World BC rock called, seism. at ',dtor(1),' km'
	// ir = 6;
	// } else if (vs30 > 360.) {
	// ir = 7;
	// // write(6,*)'AB World C-soil called, seism. at ',dtor(1),' km'
	// } else if (vs30 > 190.) {
	// ir = 8;
	// // write(6,*)'AB World D-soil called, seism. at ',dtor(1),' km'
	// } else {
	// ir = 9;
	// // write(6,*)'AB World DE or E-soil called, seism. at
	// // ',dtor(1),' km'
	// }
	// } else {
	// // endif
	// // call getABsub(ip,jabs,ir,slab,ia,ndist,di,nmag,magmin,dmag,vs30)
	//
	// if (vs30 < 190.0) {
	// ir = 4;
	// // write(6,*)'AB PNW DE or E-soil called, seism. at ',dtor(1),'
	// // km'
	// } else if (vs30 < 360.0) {
	// ir = 3;
	// // write(6,*)'AB PNW D-soil called, seism. at ',dtor(1),' km'
	// } else if (vs30 < 660.0) {
	// ir = 2;
	// // write(6,*)'AB PNW C-soil called, seism. at ',dtor(1),' km'
	// } else if (vs30 <= 780.0) {
	// ir = 1;
	// // write(6,*)'AB PNW BC-rock called, seism. at ',dtor(1),' km'
	// } else {
	// ir = 0;
	// // write(6,*)'AB PNW benioff B rock called, seism. at
	// // ',dtor(1),' km'
	// }
	// }
	//
	// if (ir < 6 && slab) {
	// // cascadia slab
	// gnd0 = c1[ip];
	// r1 = c1[0];
	// c02 = c2[ip];
	// c03 = c3[ip];
	// c04 = c4[ip];
	// c05 = c5[ip];
	// c06 = c6[ip];
	// c07 = c7[ip];
	// } else if (slab) {
	// // global slab
	// gnd0 = c1w[ip];
	// r1 = c1w[0];
	// c02 = c2[ip];
	// c03 = c3[ip];
	// c04 = c4[ip];
	// c05 = c5[ip];
	// c06 = c6[ip];
	// c07 = c7[ip];
	// } else if (ir < 6) {
	// // cascadia interface
	// gnd0 = s1[ip];
	// r1 = s1[0];
	// c02 = s2[ip];
	// c03 = s3[ip];
	// c04 = s4[ip];
	// c05 = s5[ip];
	// c06 = s6[ip];
	// c07 = s7[ip];
	// } else {
	// // global interface
	// gnd0 = s1g[ip];
	// r1 = s1g[0];
	// c02 = s2g[ip];
	// c03 = s3g[ip];
	// c04 = s4g[ip];
	// c05 = s5g[ip];
	// c06 = s6g[ip];
	// c07 = s7g[ip];
	// }
	// // c-- loop through magnitudes
	// // do 104 m=1,nmagx
	// // xmag0= magmin +(m-1)*dmag
	// // c--- loop through atten. relations for each period
	// // c-- gnd for SS; gnd2 for thrust; gnd3 for normal
	// // c remove the possible mag conversion. Assume Mw coming in.
	// // c added Oct 22 2008. Limit magnitude to 8.0 (see AB03 bSSA p 1703).
	// // "saturation effect"
	// // "saturation effect" p. 1709
	// mag = Math.min(mag, slab ? 8.0 : 8.5);
	// // xmag=min(8.0,xmag0)
	// double delta = 0.00724 * Math.pow(10, 0.507 * mag);
	// double g;
	// if (slab) {
	// g = Math.pow(10, 0.301 - 0.01 * mag);
	// } else {
	// // c g = 2 for M=5 event.
	// g = Math.pow(10, 1.2 - 0.18 * mag);
	// }
	// double gndm = gnd0 + c02 * mag;
	//
	// // c loop through depth of slab or interface seismicity
	// // do 104 kk=1,ntor !new 7/06.
	// // depth=dtor(kk)
	//
	// // as far as I can tell from hazSUBXnga and hazSUBXngatest, interface
	// // events are fixed at depth = 20km, slab events from hazgrid are
	// // variable but limited to <100km
	//
	// double depthp = Math.min(depth, 100); // !additional constraint 3/07
	// double dsq = depth * depth;
	// // c-- loop through distances., ii is rjb distance index.
	// // do 103 ii=1,ndist
	// // dist0= (float(ii)-0.5)*di
	// // weight= wt(ip,ia,1)
	// // if(dist0.gt.wtdist(ip,ia)) weight= wt(ip,ia,2)
	// double dist = Math.sqrt(dist0 * dist0 + dsq);
	// double dist2 = Math.sqrt(dist * dist + delta * delta);
	// double gnd = gndm + c03 * depthp + c04 * dist2 - g * Math.log10(dist2);
	// // c--- calculate rock PGA for BC site amp. R1 varies with source type
	// // and region
	// // c--- interface or inslab. rpga units are cm/s/s.
	// double rpga = r1 + r2 * mag + r3 * depthp + r4 * dist2 - g *
	// Math.log10(dist2);
	// rpga = Math.pow(10, rpga);
	//
	// double sl;
	// if ((rpga <= 100.0) || (freq <= 1.0)) {
	// sl = 1.0;
	// } else if ((rpga > 100.0) && (rpga < 500.0) && (freq > 1.0) &&
	// (freq < 2.0)) {
	// sl = 1.0 - (freq - 1.) * (rpga - 100) / 400.0;
	// } else if ((rpga >= 500.0) && (freq > 1.0) && (freq < 2.0)) {
	// sl = 1. - (freq - 1.);
	// } else if ((rpga > 100.) && (rpga < 500.0) && (freq >= 2.0)) {
	// sl = 1. - (rpga - 100.) / 400.;
	// // c if((rpga.ge.500.).and.(freq.ge.2.)) sl= 0.
	// } else {
	// sl = 0.0;
	// }
	// // c-----
	// // c--- Site Amp for NEHRP classes, AB style. No siteamp if ir.eq.0 .or.
	// // ir.eq.5 (B rock)
	// if (ir == 0 || ir == 5) {
	// gnd = gnd - gfac;
	// } else if (ir == 1 || ir == 6) {
	// // c--- take log ave of B (rock) and C site
	// gnd = gnd + (sl * c05) * 0.5 - gfac;
	// // c use original formulation of siteamp in getABsub
	// } else if (ir == 2 || ir == 7) {
	// // c --- C-soil site condition, added Apr 10, 2007.
	// gnd = gnd + sl * c05 - gfac;
	// } else if (ir == 3 || ir == 8) {
	// // c === D site class coeff in c6. There was a 0.5 factor below for
	// // awhile. this
	// // c factor does not appear in the paper of Aug 2003, page 1706. I
	// // removed it apr 10
	// // c 2007.
	// gnd = gnd + sl * c06 - gfac;
	// } else if (ir == 4 || ir == 9) {
	// // c === E site class coeff in c7. added may 2009.
	// gnd = gnd + sl * c07 - gfac;
	// }
	// // c log base 10 to base e.
	// gnd = gnd * ALN10;
	// // c if(kk.eq.1.and.ii.eq.1..and.m.eq.4)
	// // c + write(6,*) period, xmag, dist, exp(gnd), rpga,
	// // sl,weight,xlev(1,ip)
	// // do 199 k=1,nlev(ip)
	// return gnd;
	// // double tmp= (gnd- xlev(k,ip))*sigmaf
	// // if(tmp.gt.3.3)then
	// // ipr=25002
	// // elseif(tmp.gt.plim)then
	// // ipr= 1+nint(dp2*(tmp-plim)) !3sigma cutoff n'(mu,sig)
	// // else
	// // goto 102 !transfer out if ln(SA) above mu+3sigma
	// // endif
	// // fac=weight*p(ipr)
	// // c print *,ii,m,ip,fac,' absub'
	// // if(deagg)e0_sub(ii,m,ip,kk)= e0_sub(ii,m,ip,kk)-sqrt2*tmp*fac
	// // 199 pr(ii,m,k,ip,kk,1)= pr(ii,m,k,ip,kk,1)+ fac !sum thru ia index
	// // 102 continue
	// // 103 continue
	// // 104 continue !mag and depth counters
	// // return
	// // end subroutine getABsub
	// }

	// if (!global && slab) {
	// // cascadia slab
	// gnd0 = c1[ip];
	// r1 = c1[0];
	// c02 = c2[ip];
	// c03 = c3[ip];
	// c04 = c4[ip];
	// c05 = c5[ip];
	// c06 = c6[ip];
	// c07 = c7[ip];
	// } else if (slab) {
	// // global slab
	// gnd0 = c1w[ip];
	// r1 = c1w[0];
	// c02 = c2[ip];
	// c03 = c3[ip];
	// c04 = c4[ip];
	// c05 = c5[ip];
	// c06 = c6[ip];
	// c07 = c7[ip];
	// } else if (!global) {
	// // cascadia interface
	// gnd0 = s1[ip];
	// r1 = s1[0];
	// c02 = s2[ip];
	// c03 = s3[ip];
	// c04 = s4[ip];
	// c05 = s5[ip];
	// c06 = s6[ip];
	// c07 = s7[ip];
	// } else {
	// // global interface
	// gnd0 = s1g[ip];
	// r1 = s1g[0];
	// c02 = s2g[ip];
	// c03 = s3g[ip];
	// c04 = s4g[ip];
	// c05 = s5g[ip];
	// c06 = s6g[ip];
	// c07 = s7g[ip];
	// }

	// if (slab) {
	// r2 = rc2;
	// r3 = rc3;
	// r4 = rc4;
	// } else {
	// r2 = rs2;
	// r3 = rs3;
	// r4 = rs4;
	// }

}
