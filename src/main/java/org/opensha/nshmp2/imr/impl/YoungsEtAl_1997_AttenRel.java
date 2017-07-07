package org.opensha.nshmp2.imr.impl;

import static org.opensha.nshmp2.util.Utils.SQRT_2;
import static org.opensha.sha.util.TectonicRegionType.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * Implementation of the subduction zone attenuation relationship by Youngs et
 * al. (1997). This implementation matches that used in the 2008 USGS NSHMP
 * where it is sometimes identified as the Geomatrix attenuation relationship.
 * This attenuation realtion has been modified from its original form to an NGA
 * style (S. Harmsen 7/13/2009) wherein mean ground motion varies continuously
 * with Vs30 (sigma remains the same as original). This is acheived through use
 * of a period-dependent site amplification function modified from Boore and
 * Atkinson (2008; NGA).
 * 
 * <p>This implementation supports both slab and interface type events. In the
 * 2008 NSHMP, the 'interface' form is used with the Cascadia subduction zone
 * models and the 'slab' form is used with gridded 'deep' events in northern
 * California and the Pacific Northwest. The default state of the attenuation
 * relation is 'interface'.</p>
 * 
 * <p><b>See:</b> Youngs, R.R., Chiou, S.-J., Silva, W.J., and Humphrey, J.R.,
 * 1997, Strong ground motion attenuation relationships for subduction zone
 * earthquakes: Seismological Research Letters, v. 68, p. 58-73.</p>
 * 
 * @author Peter Powers
 * @version $Id$
 */
public class YoungsEtAl_1997_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	// NOTE RupTopDepthParam is used in a funny way here (see also Atkinson &
	// Boore sub). Currently it is not adjusted when updating an earthquake
	// rupture, but must be set manually. It defaults to 20km (the value
	// imposed in the 2008 NSHMP Cascadia subduction interface model. Any other
	// sources should update this value independently.
	
	private final static String SHORT_NAME = "YoungsEtAl1997";
	public final static String NAME = "Youngs et al. (1997)";
	private static final long serialVersionUID = 1L;

	// coefficients:
	private static final double[] pd = { 0.0, 0.2, 1.0, 0.1, 0.3, 0.5, 2.0, 0.4, 0.75, 1.5, 3.0, 4.0, 5.0 };
	private static final double[] vgeo = { 760.0, 300.0, 475.0 };
	private static final double[] gc1 = { 0.0, 0.722, -1.736, 1.1880, 0.246, -0.4, -3.3280, -0.115, -1.149, -2.634, -4.511, -5.350, -6.025 };
	private static final double[] gc1s = { 0.0, 1.549, -2.87, 2.516, 0.793, -0.438, -6.4330, 0.144, -1.704, -5.101, -6.672, -7.618, -8.352 };
	private static final double[] gc2 = { 0.0, -0.0027, -0.0064, -0.0011, -0.0036, -0.0048, -0.0080, -0.0042, -0.0057, -0.0073, -0.0089, -0.0096, -0.0101 };
	private static final double[] gc2s = { 0.0, -0.0019, -0.0066, -0.0019, -0.002, -0.0035, -0.0164, -0.002, -0.0048, -0.0114, -0.0221, -0.0235, -0.0246 };
	private static final double[] gc3 = { -2.556, -2.528, -2.234, -2.6550, -2.454, -2.36, -2.107, -2.401, -2.286, -2.16, -2.033, -1.98, -1.939 };
	private static final double[] gc3s = { -2.329, -2.464, -1.785, -2.697, -2.327, -2.140, -1.29, -2.23, -1.95, -1.47, -1.347, -1.272, -1.214 };
	private static final double[] gc4 = { 1.45, 1.45, 1.45, 1.45, 1.45, 1.45, 1.55, 1.45, 1.45, 1.50, 1.65, 1.65, 1.65 };
	private static final double[] gc5 = { -0.1, -0.1, -0.1, -0.1, -0.1, -0.1, -0.1, -0.1, -0.1, -0.1, -0.1, -0.1, -0.1 };

	private HashMap<Double, Integer> indexFromPerHashMap;

	private int iper;
	private double rRup, mag, vs30, depth;
	private TectonicRegionType subType;

	// parent TRTParam should be updated to enumParam
	private EnumParameter<TectonicRegionType> subTypeParam;

	private transient ParameterChangeWarningListener warningListener = null;

	/**
	 * This initializes several ParameterList objects.
	 * @param listener
	 */
	public YoungsEtAl_1997_AttenRel(ParameterChangeWarningListener listener) {
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
		return getMean(iper, subType, vs30, rRup, mag, depth);
	}

	@Override
	public double getStdDev() {
		if (intensityMeasureChanged) setCoeffIndex();
		return getStdDev(iper, mag);
	}

	@Override
	public void setParamDefaults() {
		vs30Param.setValueAsDefault();
		subTypeParam.setValueAsDefault(); // shouldn't be necessary
		magParam.setValueAsDefault();
		distanceRupParam.setValueAsDefault();
		rupTopDepthParam.setValueAsDefault();
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		pgaParam.setValueAsDefault();

		vs30 = vs30Param.getValue();
		subType = subTypeParam.getValue();
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

		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
		stdDevIndependentParams.addParameter(saPeriodParam);
		stdDevIndependentParams.addParameter(magParam);

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
		otherParams.addParameter(subTypeParam);
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
			rRup = ((Double) val).doubleValue();
		} else if (pName.equals(Vs30_Param.NAME)) {
			vs30 = ((Double) val).doubleValue();
		} else if (pName.equals(MagParam.NAME)) {
			mag = ((Double) val).doubleValue();
		} else if (pName.equals(PeriodParam.NAME)) {
			intensityMeasureChanged = true;
		} else if (pName.equals(RupTopDepthParam.NAME)) {
			depth = (Double) val;
		} else if (pName.equals(subTypeParam.getName())) {
			subType = (TectonicRegionType) val;
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

	private static final double GC0 = 0.2418;
	private static final double GCS0 = -0.6687;
	private static final double CI = 0.3846;
	private static final double CIS = 0.3643;
	private static final double GCH = 0.00607;
	private static final double GCHS = 0.00648;
	private static final double GMR = 1.414;
	private static final double GMS = 1.438;
	private static final double GEP = 0.554;

	private double getMean(int iper, TectonicRegionType trt, double vs30,
			double rRup, double mag, double depth) {

		boolean slab = trt.equals(SUBDUCTION_SLAB);
		double slabVal = slab ? 1 : 0;
		
		// NSHMP hazgridXnga caps slab events at M=8 after AB03 sub
		if (slab) mag = Math.min(8.0, mag);
		
		// reference PGA; determine nonlinear response using this value
		double gnd0p = GC0 + CI * slabVal;
		double gnd0, gz, g1, g2, g3, g4, ge, gm;
		int ir;
		if (vs30 > 520.0) { // rock
			gnd0 = GC0 + CI * slabVal; // no interface term ci for subduction
			gz = GCH;
			g1 = gc1[iper];
			g2 = gc2[iper];
			g3 = gc3[iper];
			g4 = 1.7818;
			ge = 0.554;
			gm = GMR;
			ir = 0;
		} else { // soil
			gnd0 = GCS0 + CIS * slabVal; // no interface term cis for subduction
			gz = GCHS;
			g1 = gc1s[iper];
			g2 = gc2s[iper];
			g3 = gc3s[iper];
			g4 = 1.097;
			ge = 0.617;
			gm = GMS;
			ir = 1;
		}

		double gndm = gnd0 + g1 + (gm * mag) + g2 * Math.pow(10.0 - mag, 3) + (gz * depth);
		double arg = Math.exp(ge * mag);
		double gnd = gndm + g3 * Math.log(rRup + g4 * arg);
		if (vs30 != vgeo[ir]) {
			// frankel mods for nonlin siteamp July 7/09
			double gndzp = gnd0p + depth * GCH + gc1[0];
			double gndmp = gndzp + GMR * mag + gc2[0] * Math.pow(10.0 - mag, 3);
			double argp = Math.exp(GEP * mag);
			double gndp = gndmp + gc3[0] * Math.log(rRup + 1.7818 * argp);
			double pganl = Math.exp(gndp);
			gnd = gnd + baSiteAmp(pganl, vs30, vgeo[ir], iper);
		}
//		System.out.println("yeaM " + vs30 + " " + gnd + " " + gndm + " " + rRup + " " + depth);
//		System.out.println("gndm " + gnd0 + " " + g1 + " " + gm + " " + mag + " " + g2 + " " + gz);
		return gnd;
	}

	private double getStdDev(int iper, double mag) {
		// same sigma for soil and rock; sigma capped at M=8 per Youngs et al.
		double sig = gc4[iper] + gc5[iper] * Math.min(8.0, mag);
//		System.out.println("yeaS " + sig);
		return sig;
//		return 1.0 / (sig * SQRT_2);
	}

	// @Override
	// public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls) {
	// return Utils.getExceedProbabilities(imls, getMean(), getStdDev(),
	// clampStd, clamp[iper]);
	// }

	// // subroutine getGeom(ip,islab,vs30,xmag,dist0,gnd,sigmaf,p_corr)
	// private double getMean(int iper, TectonicRegionType trt, double vs30,
	// double rRup, double mag) {
	// // c
	// // c Geomatrix (Youngs et al. subduction). modified to NGA style.
	// // c
	// // c July 13, 2009: modified to output gnd w/continuous variation with
	// Vs30.
	// // c Sigma, however, is not modified from original Geomatrix sigma.
	// // c
	// // c Inputs:
	// // c ip = outer loop period index. will need iq = per index in pergeo
	// // c islab=0 for subduction, islab=1 for intraplate.
	// // c xmag = moment magnitude of source
	// // c dist0 = slant distance (km)
	// // c p_corr = logical variable, .true. if petersen correction is used
	// // c when R>200 km. Effect is only programmed for 1hz 5hz and pga.
	// // c Mar 2010.
	// // c modified April 2007. Steve Harmsen. FOR rock or firm or deep soil.
	// // c added coeffs for 0.4 0.75 1.5 and 3s feb 1 2008 SH.
	// // c vs30 > 520 means rock site w/variable response.
	// // c
	// // c Vs30<=520: otherwise, soil site. rock & soil-coef for reference
	// vs30.
	// // c For all other Vs30, apply basiteamp.
	// // c This version has continuous variation.
	// // c returns median and 1/sigma/sqrt2
	// // parameter (np=13,sqrt2=1.4142136)
	// // common/ipindx/iperba(10),ipgeom(10)
	// // logical lvs,p_corr
	// // real gc0/0.2418/,gcs0/-0.6687/,ci/0.3846/,cis/0.3643/
	// // real gch/0.00607/,gchs/0.00648/,gmr/1.414/,gms/1.438/
	// double gc0 = 0.2418;
	// double gcs0 = -0.6687;
	// double ci = 0.3846;
	// double cis = 0.3643;
	// double gch = 0.00607;
	// double gchs = 0.00648;
	// double gmr = 1.414;
	// double gms = 1.438;
	// // real period,gnd0,gndz,gz,g3,g4,gndm
	// // real, dimension(np):: gc1,gc2,gc3,gc4,gc5,pergeo,gc1s,gc2s,gc3s,gp
	// // real vgeo(3)
	// // c array constructors oct 2006
	// double[] pergeo= { 0.0, 0.2, 1.0, 0.1, 0.3, 0.5, 2.0, 0.4, 0.75, 1.5,
	// 3.0, 4.0, 5.0 };
	// // C vgeo is a reference vs30 for Geomatrix, 760 m/s rock, 300 m/s soil.
	// // c Additional siteamp will be wrt these values from Frankel discussion
	// july 7.
	// // c the 475 value isn't currently used. A coeff. set that represents
	// very stiff soil (NEHRP C)
	// // c with something like 475 to 500 m/s could go with this. Currently
	// there is a discontinuity
	// // c when switching between rock and soil coeffs, currently at 520 m/s
	// Vs30.
	// double[] vgeo = { 760.0, 300.0, 475.0 };
	// double[] gc1 = { 0.0, 0.722, -1.736, 1.1880, 0.246, -0.4, -3.3280,
	// -0.115, -1.149, -2.634, -4.511, -5.350, -6.025 };
	// double[] gc2 = { 0.0, -0.0027, -0.0064, -0.0011, -0.0036, -0.0048,
	// -0.0080, -0.0042, -0.0057, -0.0073, -0.0089, -0.0096, -0.0101 };
	// double[] gc1s = { 0.0, 1.549, -2.87, 2.516, 0.793, -0.438, -6.4330,
	// 0.144, -1.704, -5.101, -6.672, -7.618, -8.352 };
	// double[] gc2s = { 0.0, -0.0019, -0.0066, -0.0019, -0.002, -0.0035,
	// -0.0164, -0.002, -0.0048, -0.0114, -0.0221, -0.0235, -0.0246 };
	// double[] gc3 = { -2.556, -2.528, -2.234, -2.6550, -2.454, -2.36, -2.107,
	// -2.401, -2.286, -2.16, -2.033, -1.98, -1.939 };
	// double[] gc3s = { -2.329, -2.464, -1.785, -2.697, -2.327, -2.140, -1.29,
	// -2.23, -1.95, -1.47, -1.347, -1.272, -1.214 };
	// double[] gc4 = { 1.45, 1.45, 1.45, 1.45, 1.45, 1.45, 1.55, 1.45, 1.45,
	// 1.50, 1.65, 1.65, 1.65 };
	// double[] gc5 = { -0.1, -0.1, -0.1, -0.1, -0.1, -0.1, -0.1, -0.1, -0.1,
	// -0.1, -0.1, -0.1, -0.1 };
	// // c revised coeffs for reduction beyond 200 km. incl 3-s SA. Dont have
	// 2-s data.
	// double[] gp = { -0.003646, -0.004724, -0.003208, 0.0, -0.004549, 0.0,
	// 0.0, 0.0, 0.0, 0.0, -0.001070, 0.0, 0.0 };
	//
	// double islab = (trt == SUBDUCTION_INTERFACE) ? 0 : 1;
	// // c Always define a reference PGA, determine nonlinear response based on
	// this value
	// double gnd0p = gc0+ci*islab;
	// // double iq=ipgeom(ip);
	// // c period=pergeo(iq)
	// double gnd0, gz, g1, g2, g3, g4, ge, gm;
	// int ir;
	// if(vs30 > 520.0) {
	// // c Use rock coeffs. No interface term ci or cis for subduction
	// gnd0=gc0 +ci*islab;
	// gz=gch;
	// g1=gc1[iper];
	// g2=gc2[iper];
	// g3=gc3[iper];
	// g4=1.7818;
	// ge=0.554;
	// gm=gmr;
	// ir=0;
	// } else {
	// // c Use soil coeffs
	// gnd0=gcs0 + cis*islab ;
	// gz=gchs;
	// g1=gc1s[iper];
	// g2=gc2s[iper];
	// g3=gc3s[iper];
	// g4=1.097;
	// ge=0.617;
	// gm=gms;
	// ir=1;
	// } //endif
	// // c gz term used for subduction, and for benioff (see hazgridXnga2)
	// double sig= gc4[iper]+gc5[iper]* Math.min(8.0, mag);
	// double sigmaf= 1./(sig*SQRT_2);
	// // c same sigma for soil and rock.
	// // c distance correction if user set atten type as -2. p_corr is then
	// true.
	// // if(p_corr.and.dist0.gt.200.)gnd0=gnd0+gp[iper]*(dist0-200.)
	// // c In 2002 we used a constant depth of 20 km but this depth depends on
	// subduction model
	// double gndm= gnd0 +g1 +gm*mag +g2*Math.pow((10.0 - mag), 3) +gz*20.0;
	// // c frankel addn, gndmp
	// double arg= Math.exp(ge*mag);
	// // c Distance could be hypocentral or distance to top-of-Benioff zone.
	// double gnd=gndm +g3*Math.log(rRup+g4*arg);
	// // if(vs30 .ne. vgeo(ir) )then
	// if (vs30 != vgeo[ir]) {
	// // c frankel mods for nonlin siteamp July 2009. Use g4p (rock g4) SH.
	// // c Gndzp for nonlin site response.
	// double gndzp= gnd0p+20.*gch +gc1[0];
	// double gndmp= gndzp+gmr*mag+ gc2[0]*Math.pow((10.0 - mag),3);
	// double argp= Math.exp(0.554*mag);
	// double gndp=gndmp+gc3[0]*Math.log(rRup+1.7818*argp);
	// double pganl= Math.exp(gndp);
	// // c write(6,*) "before basiteamp",pganl,exp(gnd). The below call
	// // c to basiteamp produces an amplitude ratio in the variable amp.
	// // c if(iq.eq.3.and.dist0.lt.30.)write(39,*)
	// dist0,exp(gnd),exp(amp),pganl
	// gnd= gnd + baSiteAmp(pganl,vs30,vgeo[ir],iper);
	// // endif !vs30 .ne. vgeo(ir), the reference Vs for soil or rock, geom.
	// // end subroutine getGeom
	//
	// }
	// return gnd;
	// }

	private static final double[] baPer = { -1.0, 0.0, 0.01, 0.02, 0.03, 0.05, 0.075, 0.1, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 7.5, 10.0 };
	private static final Map<Double, Integer> baPerIdxMap;
	static {
		baPerIdxMap = Maps.newHashMap();
		int idx = 0;
		for (double d : baPer) {
			baPerIdxMap.put(d, idx++);
		}
	}

	private static final double[] blin = { -0.600, -0.360, -0.360, -0.340, -0.330, -0.290, -0.230, -0.250, -0.280, -0.310, -0.390, -0.440, -0.500, -0.600, -0.690, -0.700, -0.720, -0.730, -0.740, -0.750, -0.750, -0.692, -0.650 };
	private static final double[] b1 = { -0.500, -0.640, -0.640, -0.630, -0.620, -0.640, -0.640, -0.600, -0.530, -0.520, -0.520, -0.520, -0.510, -0.500, -0.470, -0.440, -0.400, -0.380, -0.340, -0.310, -0.291, -0.247, -0.215 };
	private static final double[] b2 = { -0.060, -0.140, -0.140, -0.120, -0.110, -0.110, -0.110, -0.130, -0.180, -0.190, -0.160, -0.140, -0.100, -0.060, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000 };
	// 12/12/2011 pp converted v1 and v2 to scalars
	// private static final double[] v1 = { 180., 180., 180., 180., 180., 180.,
	// 180., 180., 180., 180., 180., 180., 180., 180., 180., 180., 180., 180.,
	// 180., 180., 180., 180., 180. };
	// private static final double[] v2 = { 300., 300., 300., 300., 300., 300.,
	// 300., 300., 300., 300., 300., 300., 300., 300., 300., 300., 300., 300.,
	// 300., 300., 300., 300., 300. };
	private static final double V1 = 180.0;
	private static final double V2 = 300.0;

	private static final double A1 = 0.030;
	private static final double A2 = 0.090;
	private static final double A2FAC = 0.405465108;

	private static final double VREF = 760.0;
	private static final double DX = 1.098612289; // ln(a2/a1)
	private static final double DXSQ = 1.206948961;
	private static final double DXCUBE = 1.325968960;
	private static final double PLFAC = -0.510825624; // ln(pga_low/0.1) where
														// pga_low = 0.06

	/**
	 * Utility method returns a site response value that is a continuous
	 * function of <code>vs30</code>: log(AMP at vs30)-log(AMP at vs30r). Value
	 * at <code>vs30 == vs30r</code> is unity. This function was adapted from
	 * hazSUBXngatest.f and is valid for 23 periods.
	 * @param pganl reference pga
	 * @param vs30 at a site of interest
	 * @param vs30r reference vs30, usually one value for soil and another for
	 *        rock
	 * @param per period index
	 * @return the site response correction
	 */
	public static double baSiteAmp(double pganl, double vs30, double vs30r,
			double per) {
		Preconditions.checkArgument(baPerIdxMap.containsKey(per),
			"Invalid period: " + per);
		int iq = baPerIdxMap.get(per); // index of supplied period

		double dy, dyr, site, siter = 0.0;

		double bnl, bnlr;
		// some site term precalcs that are not M or d dependent
		if (V1 < vs30 && vs30 <= V2) {
			bnl = (b1[iq] - b2[iq]) * Math.log(vs30 / V2) / Math.log(V1 / V2) +
				b2[iq];
		} else if (V2 < vs30 && vs30 <= VREF) {
			bnl = b2[iq] * Math.log(vs30 / VREF) / Math.log(V2 / VREF);
		} else if (vs30 <= V1) {
			bnl = b1[iq];
		} else {
			bnl = 0.0;
		}

		if (V1 < vs30r && vs30r <= V2) {
			// repeat site term precalcs that are not M or d dependent @ ref.
			// vs.
			bnlr = (b1[iq] - b2[iq]) * Math.log(vs30r / V2) /
				Math.log(V1 / V2) + b2[iq];
		} else if (V2 < vs30r && vs30r <= VREF) {
			bnlr = b2[iq] * Math.log(vs30r / VREF) / Math.log(V2 / VREF);
		} else if (vs30r <= V1) {
			bnlr = b1[iq];
		} else {
			bnlr = 0.0;
		}

		dy = bnl * A2FAC; // ADF added line
		dyr = bnlr * A2FAC;
		site = blin[iq] * Math.log(vs30 / VREF);
		siter = blin[iq] * Math.log(vs30r / VREF);

		// Second part, nonlinear siteamp reductions below.
		if (pganl <= A1) {
			site = site + bnl * PLFAC;
			siter = siter + bnlr * PLFAC;
		} else if (pganl <= A2) {
			// extra lines smooth a kink in siteamp, pp 9-11 of boore sept
			// report. c and d from p 10 of boore sept report. Smoothing
			// introduces extra calcs in the range a1 < pganl < a2. Otherwise
			// nonlin term same as in june-july. Many of these terms are fixed
			// and are defined in data or parameter statements. Of course, if a1
			// and a2 change from their sept 06 values the parameters will also
			// have to be redefined. (a1,a2) represents a siteamp smoothing
			// range (units g)
			double c = (3. * dy - bnl * DX) / DXSQ;
			double d = (bnl * DX - 2. * dy) / DXCUBE;
			double pgafac = Math.log(pganl / A1);
			double psq = pgafac * pgafac;
			site = site + bnl * PLFAC + (c + d * pgafac) * psq;
			c = (3. * dyr - bnlr * DX) / DXSQ;
			d = (bnlr * DX - 2. * dyr) / DXCUBE;
			siter = siter + bnlr * PLFAC + (c + d * pgafac) * psq;
		} else {
			double pgafac = Math.log(pganl / 0.1);
			site = site + bnl * pgafac;
			siter = siter + bnlr * pgafac;
		}
		return site - siter;
	}

	// COMPACTED VERSION
	// public static double baSiteAmp(double pganl, double vs30, double vs30r,
	// double per) {
	// Preconditions.checkArgument(baPerIdxMap.containsKey(per),
	// "Invalid period: " + per);
	// int iq = baPerIdxMap.get(per); // index of supplied period
	//
	// double vref = 760.0;
	// double dx = 1.098612289; // ln(a2/a1)
	// double dxsq = 1.206948961;
	// double dxcube = 1.325968960;
	// double plfac = -0.510825624; // ln(pga_low/0.1) where pga_low = 0.06
	// double dy, dyr, site;
	// double siter = 0.0;
	// double a1 = 0.030;
	// double a2 = 0.090;
	// double a2fac = 0.405465108;
	// // e2,e3,e4 are the mech-dependent set. e1 is a mech-unspecified value.
	// // Notation change from subroutine version of 12/05 and earlier.
	// // array constructors for f95 Linux
	// // from ba_02apr07_usnr.xls coef file
	// double[] blin = { -0.600, -0.360, -0.360, -0.340, -0.330, -0.290, -0.230,
	// -0.250, -0.280, -0.310, -0.390, -0.440, -0.500, -0.600, -0.690, -0.700,
	// -0.720, -0.730, -0.740, -0.750, -0.750, -0.692, -0.650 };
	// double[] b1 = { -0.500, -0.640, -0.640, -0.630, -0.620, -0.640, -0.640,
	// -0.600, -0.530, -0.520, -0.520, -0.520, -0.510, -0.500, -0.470, -0.440,
	// -0.400, -0.380, -0.340, -0.310, -0.291, -0.247, -0.215 };
	// double[] b2 = { -0.060, -0.140, -0.140, -0.120, -0.110, -0.110, -0.110,
	// -0.130, -0.180, -0.190, -0.160, -0.140, -0.100, -0.060, 0.000, 0.000,
	// 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000 };
	// double[] v1 = { 180., 180., 180., 180., 180., 180., 180., 180., 180.,
	// 180., 180., 180., 180., 180., 180., 180., 180., 180., 180., 180., 180.,
	// 180., 180. };
	// double[] v2 = { 300., 300., 300., 300., 300., 300., 300., 300., 300.,
	// 300., 300., 300., 300., 300., 300., 300., 300., 300., 300., 300., 300.,
	// 300., 300. };
	//
	// double bnl, bnlr;
	// // some site term precalcs that are not M or d dependent
	// if (v1[iq] < vs30 && vs30 <= v2[iq]) {
	// bnl = (b1[iq] - b2[iq]) * Math.log(vs30 / v2[iq]) /
	// Math.log(v1[iq] / v2[iq]) + b2[iq];
	// } else if (v2[iq] < vs30 && vs30 <= vref) {
	// bnl = b2[iq] * Math.log(vs30 / vref) / Math.log(v2[iq] / vref);
	// } else if (vs30 <= v1[iq]) {
	// bnl = b1[iq];
	// } else {
	// bnl = 0.0;
	// }
	//
	// if (v1[iq] < vs30r && vs30r <= v2[iq]) {
	// // repeat site term precalcs that are not M or d dependent @ ref.
	// // vs.
	// bnlr = (b1[iq] - b2[iq]) * Math.log(vs30r / v2[iq]) /
	// Math.log(v1[iq] / v2[iq]) + b2[iq];
	// } else if (v2[iq] < vs30r && vs30r <= vref) {
	// bnlr = b2[iq] * Math.log(vs30r / vref) / Math.log(v2[iq] / vref);
	// } else if (vs30r <= v1[iq]) {
	// bnlr = b1[iq];
	// } else {
	// bnlr = 0.0;
	// }
	//
	// dy = bnl * a2fac; // ADF added line
	// dyr = bnlr * a2fac;
	// site = blin[iq] * Math.log(vs30 / vref);
	// siter = blin[iq] * Math.log(vs30r / vref);
	//
	// // Second part, nonlinear siteamp reductions below.
	// if (pganl <= a1) {
	// site = site + bnl * plfac;
	// siter = siter + bnlr * plfac;
	// } else if (pganl <= a2) {
	// // extra lines smooth a kink in siteamp, pp 9-11 of boore sept
	// // report. c and d from p 10 of boore sept report. Smoothing
	// // introduces extra calcs in the range a1 < pganl < a2. Otherwise
	// // nonlin term same as in june-july. Many of these terms are fixed
	// // and are defined in data or parameter statements. Of course, if a1
	// // and a2 change from their sept 06 values the parameters will also
	// // have to be redefined. (a1,a2) represents a siteamp smoothing
	// // range (units g)
	// double c = (3. * dy - bnl * dx) / dxsq;
	// double d = (bnl * dx - 2. * dy) / dxcube;
	// double pgafac = Math.log(pganl / a1);
	// double psq = pgafac * pgafac;
	// site = site + bnl * plfac + (c + d * pgafac) * psq;
	// c = (3. * dyr - bnlr * dx) / dxsq;
	// d = (bnlr * dx - 2. * dyr) / dxcube;
	// siter = siter + bnlr * plfac + (c + d * pgafac) * psq;
	// } else {
	// double pgafac = Math.log(pganl / 0.1);
	// site = site + bnl * pgafac;
	// siter = siter + bnlr * pgafac;
	// }
	// return site - siter;
	// }

	// public static double baSiteAmp(double pganl, double vs30, double vs30r,
	// double per) {
	// Preconditions.checkArgument(baPerIdxMap.containsKey(per),
	// "Invalid period: " + per);
	// // need to map period in use to beSiteAmp equivalent index
	// int iq = baPerIdxMap.get(per);
	// //real function basiteamp(pganl,vs30,vs30r,ip)
	// //c inputs: pganl (g)
	// //c vs30: vs30 of site
	// //c vs30r: a reference vs30, usually one value for soil and another for
	// rock.
	// //c ip : period index (1st, 2nd, 3rd, ... in the outermost analysis). Max
	// 10.
	// //c Output: basiteamp = log(AMP at vs30)-log(AMP at vs30r)
	// //c Purpose: makes site response a continuous fcn of vs30. Its value at
	// vs30r is unity.
	// // parameter (np=23) !23 periods apr 07. include 0.01 to 10 s
	// // parameter (pi=3.14159265,sqrt2=1.414213562,vref=760.)
	// double vref = 760.0;
	// // parameter
	// (dx=1.098612289,dxsq=1.206948961,dxcube=1.325968960,plfac=-0.510825624)
	// double dx = 1.098612289;
	// double dxsq=1.206948961;
	// double dxcube=1.325968960;
	// double plfac=-0.510825624;
	//
	// //c dx = ln(a2/a1), made a param. used in a smoothed nonlin calculation
	// sept 2006.
	// //c plfac = ln(pga_low/0.1) This never changes so shouldnt be calculated.
	// // common/ipindx/iperba(10),ipgeom(10)
	// // real e1nl/-0.53804/,e2nl/-0.50350/,e3nl/-0.75472/,e4nl/-0.50970/
	// // real e5nl/0.28805/,e6nl/-0.10164/,e7nl/0.0/
	// // real c1nl/-0.66050/,c2nl/0.11970/,c3nl/-0.011510/,hnl/1.35/,b1nl/0./
	// // real b2nl/0./,pga_low/0.06/,mhnl/6.75/,mrefnl/4.5/,rrefnl/1.0/
	// // real dy,dyr,pganl, pganlm,pganlmec,site,siter/0.0/
	// // real a1/ 0.030/,a2/ 0.090/,a2fac/0.405465108/
	// double e1nl = -0.53804;
	// double e2nl = -0.50350;
	// double e3nl = -0.75472;
	// double e4nl = -0.50970;
	// double e5nl = 0.28805;
	// double e6nl = -0.10164;
	// double e7nl = 0.0;
	// double c1nl = -0.66050;
	// double c2nl = 0.11970;
	// double c3nl = -0.011510;
	// double hnl = 1.35;
	// double b1nl = 0.0;
	// double b2nl = 0.0;
	// double pga_low = 0.06;
	// double mhnl = 6.75;
	// double mrefnl =4.5;
	// double rrefnl = 1.0;
	// // double dy,dyr, pganl, pganlm,pganlmec,site;
	// double dy,dyr, pganlm,pganlmec,site;
	// double siter = 0.0;
	// double a1 = 0.030;
	// double a2 = 0.090;
	// double a2fac = 0.405465108;
	// // real, dimension(np):: per,e1,e2,e3,e4,e5,e6,e7,e8
	// // + ,mh,c1,c2,c3,c4,mref,rref,h,blin,b1,b2,v1,v2,
	// // + sig1,sig2u,sigtu,sig2m,sigtm
	// //c e2,e3,e4 are the mech-dependent set. e1 is a mech-unspecified value.
	// //c Notation change from subroutine version of 12/05 and earlier.
	// //c array constructors for f95 Linux
	// //c from ba_02apr07_usnr.xls coef file
	// // per= (/-1.000, 0.000, 0.010, 0.020, 0.030, 0.050, 0.075, 0.100,
	// // + 0.150, 0.200, 0.250, 0.300, 0.400, 0.500, 0.750, 1.000,
	// // + 1.500, 2.000, 3.000, 4.000, 5.000, 7.500,10.0/)
	// double[] e1= { 5.00121,-0.53804,-0.52883,-0.52192,-0.45285,-0.28476,
	// 0.00767, 0.20109, 0.46128, 0.57180, 0.51884, 0.43825, 0.39220,
	// 0.18957,-0.21338,
	// -0.46896,-0.86271,-1.22652,-1.82979,-2.24656,-1.28408,-1.43145,-2.15446};
	// double[] e2= { 5.04727,-0.50350,-0.49429,-0.48508,-0.41831,-0.25022,
	// 0.04912, 0.23102, 0.48661, 0.59253, 0.53496, 0.44516, 0.40602,
	// 0.19878,-0.19496,
	// -0.43443,-0.79593,-1.15514,-1.74690,-2.15906,-1.21270,-1.31632,-2.16137};
	// //c Editorial comment I used the e3(10s)=e3(7.5)+e2(10s)-e2(7.5s) for 10s
	// normal, because BA
	// //c report e3(10s) as 0.0 and this gives a large motion compared to
	// others in nhbd.
	// //c also reported this to Dave Boore in email for his advice. SHarmsen
	// Oct 3 2007. Gail suggests
	// //c that ratio for normal might equal ratio for unspecified or ratio for
	// SS. No consensus. Using
	// //c Gail's sugg. This choice of e3(10s), -2.66, is very low. Normal
	// median is probably too low.
	// double[] e3= { 4.63188,-0.75472,-0.74551,-0.73906,-0.66722,-0.48462,
	// -0.20578, 0.03058, 0.30185, 0.40860, 0.33880, 0.25356, 0.21398,
	// 0.00967,-0.49176,
	// -0.78465,-1.20902,-1.57697,-2.22584,-2.58228,-1.50904,-1.81022, -2.66 };
	// double[] e4= { 5.08210,-0.50970,-0.49966,-0.48895,-0.42229,-0.26092,
	// 0.02706, 0.22193, 0.49328, 0.61472, 0.57747, 0.51990, 0.46080,
	// 0.26337,-0.10813,
	// -0.39330,-0.88085,-1.27669,-1.91814,-2.38168,-1.41093,-1.59217,-2.14635
	// };
	// double[] e5= { 0.18322, 0.28805, 0.28897, 0.25144, 0.17976, 0.06369,
	// 0.01170, 0.04697, 0.17990, 0.52729, 0.60880, 0.64472, 0.78610, 0.76837,
	// 0.75179,
	// 0.67880, 0.70689, 0.77989, 0.77966, 1.24961, 0.14271, 0.52407, 0.40387 };
	// double[] e6= {-0.12736,-0.10164,-0.10019,-0.11006,-0.12858,-0.15752,
	// -0.17051,-0.15948,-0.14539,-0.12964,-0.13843,-0.15694,-0.07843,-0.09054,-0.14053,
	// -0.18257,-0.25950,-0.29657,-0.45384,-0.35874,-0.39006,-0.37578,-0.48492
	// };
	// double[] e7= { 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000,
	// 0.00000, 0.00000, 0.00000, 0.00102, 0.08607, 0.10601, 0.02262, 0.00000,
	// 0.10302,
	// 0.05393, 0.19082, 0.29888, 0.67466, 0.79508, 0.00000, 0.00000, 0.00000 };
	// double[] e8= { 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000,
	// 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000,
	// 0.00000,
	// 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000 };
	// double[] mh= { 8.50000, 6.75000, 6.75000, 6.75000, 6.75000, 6.75000,
	// 6.75000, 6.75000, 6.75000, 6.75000, 6.75000, 6.75000, 6.75000, 6.75000,
	// 6.75000,
	// 6.75000, 6.75000, 6.75000, 6.75000, 6.75000, 8.50000, 8.50000, 8.50000 };
	// double[] c1=
	// {-0.873700,-0.660500,-0.662200,-0.666000,-0.690100,-0.717000,
	// -0.720500,-0.708100,-0.696100,-0.583000,-0.572600,-0.554300,-0.644300,-0.691400,-0.740800,
	// -0.818300,-0.830300,-0.828500,-0.784400,-0.685400,-0.509600,-0.372400,-0.098240
	// };
	// double[] c2= { 0.100600, 0.119700, 0.120000, 0.122800, 0.128300,
	// 0.131700,
	// 0.123700, 0.111700, 0.098840, 0.042730, 0.029770, 0.019550, 0.043940,
	// 0.060800, 0.075180,
	// 0.102700, 0.097930, 0.094320, 0.072820,
	// 0.037580,-0.023910,-0.065680,-0.138000 };
	// double[] c3=
	// {-0.003340,-0.011510,-0.011510,-0.011510,-0.011510,-0.011510,
	// -0.011510,-0.011510,-0.011130,-0.009520,-0.008370,-0.007500,-0.006260,-0.005400,-0.004090,
	// -0.003340,-0.002550,-0.002170,-0.001910,-0.001910,-0.001910,-0.001910,-0.001910
	// };
	// double[] c4= { 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000,
	// 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000,
	// 0.00000,
	// 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00000 };
	// double[] mref= {4.500,4.500,4.500,4.500,4.500,4.500,
	// 4.500,4.500,4.500,4.500,4.500,4.500,4.500,4.500,4.500,
	// 4.500,4.500,4.500,4.500,4.500,4.500,4.500,4.500 };
	// double[] rref= {1.000,1.000,1.000,1.000,1.000,1.000,
	// 1.000,1.000,1.000,1.000,1.000,1.000,1.000,1.000,1.000,
	// 1.000,1.000,1.000,1.000,1.000,1.000,1.000,1.000 };
	// double[] h= {2.540,1.350,1.350,1.350,1.350,1.350,
	// 1.550,1.680,1.860,1.980,2.070,2.140,2.240,2.320,2.460,
	// 2.540,2.660,2.730,2.830,2.890,2.930,3.000,3.040 };
	// double[] blin = {-0.600,-0.360,-0.360,-0.340,-0.330,-0.290,
	// -0.230,-0.250,-0.280,-0.310,-0.390,-0.440,-0.500,-0.600,-0.690,
	// -0.700,-0.720,-0.730,-0.740,-0.750,-0.750,-0.692,-0.650 };
	// double[] b1= {-0.500,-0.640,-0.640,-0.630,-0.620,-0.640,
	// -0.640,-0.600,-0.530,-0.520,-0.520,-0.520,-0.510,-0.500,-0.470,
	// -0.440,-0.400,-0.380,-0.340,-0.310,-0.291,-0.247,-0.215 };
	// double[] b2= {-0.060,-0.140,-0.140,-0.120,-0.110,-0.110,
	// -0.110,-0.130,-0.180,-0.190,-0.160,-0.140,-0.100,-0.060, 0.000,
	// 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000 };
	// double[] v1= { 180., 180., 180., 180., 180., 180.,
	// 180., 180., 180., 180., 180., 180., 180., 180., 180.,
	// 180., 180., 180., 180., 180., 180., 180., 180. };
	// double[] v2= { 300., 300., 300., 300., 300., 300.,
	// 300., 300., 300., 300., 300., 300., 300., 300., 300.,
	// 300., 300., 300., 300., 300., 300., 300., 300. };
	// double[] sig1= {0.500,0.502,0.502,0.502,0.507,0.516,
	// 0.513,0.520,0.518,0.523,0.527,0.546,0.541,0.555,0.571,
	// 0.573,0.566,0.580,0.566,0.583,0.601,0.626,0.645 };
	// double[] sig2u= {0.286,0.265,0.267,0.267,0.276,0.286,
	// 0.322,0.313,0.288,0.283,0.267,0.272,0.267,0.265,0.311,
	// 0.318,0.382,0.398,0.410,0.394,0.414,0.465,0.355 };
	// double[] sigtu= {0.576,0.566,0.569,0.569,0.578,0.589,
	// 0.606,0.608,0.592,0.596,0.592,0.608,0.603,0.615,0.649,
	// 0.654,0.684,0.702,0.700,0.702,0.730,0.781,0.735 };
	// double[] sig2m= {0.256,0.260,0.262,0.262,0.274,0.286,
	// 0.320,0.318,0.290,0.288,0.267,0.269,0.267,0.265,0.299,
	// 0.302,0.373,0.389,0.401,0.385,0.437,0.477,0.477 };
	// double[] sigtm= {0.560,0.564,0.566,0.566,0.576,0.589,
	// 0.606,0.608,0.594,0.596,0.592,0.608,0.603,0.615,0.645,
	// 0.647,0.679,0.700,0.695,0.698,0.744,0.787,0.801 };
	// //c end of April 07 coeff. updates.
	// //c---- Input vs30.
	// //c ip index corresponds to period. (check per correspondence with main
	// perx)
	// //c--- iq is the boore-atkinson period index for period with outer index
	// ip
	// // iq = iperba(ip)
	// //c period: spectral period (s) (scalar)
	// //c period=per(iq)
	// double bnl, bnlr;
	// // some site term precalcs that are not M or d dependent
	// if (v1[iq] < vs30 && vs30 <= v2[iq]) {
	// bnl=(b1[iq]-b2[iq])*Math.log(vs30/v2[iq])/Math.log(v1[iq]/v2[iq]) +
	// b2[iq];
	// } else if (v2[iq] < vs30 && vs30 <= vref) {
	// bnl=b2[iq]*Math.log(vs30/vref)/Math.log(v2[iq]/vref);
	// } else if (vs30 <= v1[iq]) {
	// bnl=b1[iq];
	// } else {
	// bnl=0.0;
	// }
	//
	// if (v1[iq] < vs30r && vs30r <= v2[iq]) {
	// // repeat site term precalcs that are not M or d dependent @ ref. vs.
	// bnlr=(b1[iq]-b2[iq])* Math.log(vs30r/v2[iq])/Math.log(v1[iq]/v2[iq]) +
	// b2[iq];
	// } else if (v2[iq] < vs30r && vs30r <= vref) {
	// bnlr=b2[iq]*Math.log(vs30r/vref)/Math.log(v2[iq]/vref);
	// } else if (vs30r <= v1[iq]) {
	// bnlr=b1[iq];
	// } else {
	// bnlr=0.0;
	// }
	// //c----- ADF added next line
	// dy= bnl*a2fac;
	// dyr= bnlr*a2fac;
	// site = blin[iq]*Math.log(vs30/vref);
	// siter = blin[iq]*Math.log(vs30r/vref);
	//
	// //c Second part, nonlinear siteamp reductions below.
	// if (pganl <= a1) {
	// site=site+bnl*plfac;
	// siter=siter+bnlr*plfac;
	// } else if (pganl <= a2) {
	// //c extra lines smooth a kink in siteamp, pp 9-11 of boore sept report.
	// //c c and d from p 10 of boore sept report. Smoothing introduces extra
	// calcs
	// //c in the range a1 < pganl < a2. Otherwise nonlin term same as in
	// june-july.
	// //c many of these terms are fixed and are defined in data or parameter
	// statements
	// //c Of course, if a1 and a2 change from their sept 06 values the
	// parameters will
	// //c also have to be redefined. (a1,a2) represents a siteamp smoothing
	// range (units g)
	// double c=(3.*dy-bnl*dx)/dxsq;
	// double d=(bnl*dx-2.*dy)/dxcube;
	// double pgafac=Math.log(pganl/a1);
	// double psq=pgafac*pgafac;
	// site=site+bnl*plfac + (c + d*pgafac)*psq;
	// c=(3.*dyr-bnlr*dx)/dxsq;
	// d=(bnlr*dx-2.*dyr)/dxcube;
	// siter= siter +bnlr*plfac + (c + d*pgafac)*psq;
	// } else {
	// double pgafac=Math.log(pganl/0.1);
	// site=site+bnl*pgafac;
	// siter=siter+bnlr*pgafac;
	// }
	// // basiteamp = site-siter;
	// return site-siter;
	// // end function basiteamp
	// }

}
