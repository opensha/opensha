package org.opensha.nshmp2.imr.impl;

import static org.opensha.nshmp2.util.FaultCode.*;
import static org.opensha.nshmp2.util.GaussTruncation.*;
import static org.opensha.nshmp2.util.SiteType.*;
import static org.opensha.nshmp2.util.Utils.SQRT_2;
import static org.opensha.commons.eq.cat.util.MagnitudeType.*;

import java.awt.geom.Point2D;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.commons.math3.special.Erf;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.eq.cat.util.MagnitudeType;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.nshmp2.util.FaultCode;
import org.opensha.nshmp2.util.GaussTruncation;
import org.opensha.nshmp2.util.Params;
import org.opensha.nshmp2.util.SiteType;
import org.opensha.nshmp2.util.Utils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.PropagationEffect;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;

import com.google.common.collect.Maps;

/**
 * Implementation of the hybrid attenuation relationship for the Central and
 * Eastern US by Campbell (2003). This implementation matches that used in the
 * 2008 USGS NSHMP. <br />
 * <br />
 * See: Campbell, K.W., 2003, Prediction of strong ground motion using the
 * hybrid empirical method and its use in the devel- opment of ground-motion
 * (attenuation) relations in eastern North America: Bulletin of the
 * Seismological Society of America, v. 93, p. 1012–1033.<br />
 * 
 * TODO Verify that Campbell imposes max(dtor,5); he does require rRup; why is
 * depth constrained as such in hazgrid? As with somerville, no depth is imposed
 * in hazFX - make sure 0.01 as PGA is handled corectly; may require change to
 * period = 0.0
 * 
 * @author Peter Powers
 * @version $Id$
 */
public class Campbell_2003_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	public final static String SHORT_NAME = "Campbell2003";
	private static final long serialVersionUID = 1234567890987654353L;
	public final static String NAME = "Campbell (2003)";

	// coefficients:
	// @formatter:off
	// some coefficients are labeled differnetly than in paper
	// localCoeff(paperCoeff):
	// c5(c7) c6(c8) c7(c9) c8(c10) c9(c5) c10(c6)
	private double[] pd = { 0.0, 0.2, 1.0, 0.1, 0.3, 0.4, 0.5, 2.0, 0.03, 0.04, 0.05 };
	private double[] c1 = { 0.4492, 0.1325, -0.3177, 0.4064, -0.1483, -0.17039, -0.1333, -1.2483, 1.68, 1.28, 0.87 };
	private double[] c1h = { 0.0305, -0.4328, -0.6104, -0.1475, -0.6906, -0.67076736, -0.5907, -1.4306, 1.186, 0.72857, 0.3736 };
	private double[] c2 = { 0.633, 0.617, 0.451, 0.613, 0.609, 0.5722637, 0.534, 0.459, 0.622, 0.618622, 0.616 };
	private double[] c3 = { -0.0427, -0.0586, -0.2090, -0.0353, -0.0786, -0.10939892, -0.1379, -0.2552, -0.0362, -0.035693, -0.0353 };
	private double[] c4 = { -1.591, -1.32, -1.158, -1.369, -1.28, -1.244142, -1.216, -1.124, -1.691, -1.5660, -1.469 };
	private double[] c5 = { 0.683, 0.399, 0.299, 0.484, 0.349, 0.32603806, 0.318, 0.310, 0.922, 0.75759, 0.630 };
	private double[] c6 = { 0.416, 0.493, 0.503, 0.467, 0.502, 0.5040741, 0.503, 0.499, 0.376, 0.40246, 0.423 };
	private double[] c7 = { 1.140, 1.25, 1.067, 1.096, 1.241, 1.1833254, 1.116, 1.015, 0.759, 0.76576, 0.771 };
	private double[] c8 = { -0.873, -0.928, -0.482, -1.284, -0.753, -0.6529481, -0.606, -0.4170, -0.922, -1.1005, -1.239 };
	private double[] c9 = { -0.00428, -0.0046, -0.00255, -0.00454, -0.00414, -3.7463151E-3, -0.00341, -0.00187, -0.00367, -0.0037319, -0.00378 };
	private double[] c10 = { 0.000483, 0.000337, 0.000141, 0.00046, 0.000263, 2.1878805E-4, 0.000194, 0.000103, 0.000501, 0.00050044, 0.0005 };
	private double[] c11 = { 1.030, 1.077, 1.110, 1.059, 1.081, 1.0901983, 1.098, 1.093, 1.03, 1.037, 1.042 };
	private double[] c12 = { -0.0860, -0.0838, -0.0793, -0.0838, -0.0838, -0.083180725, -0.0824, -0.0758, -0.086, -0.0848, -0.0838 };
	private double[] c13 = { 0.414, 0.478, 0.543, 0.460, 0.482, 0.49511834, 0.508, 0.551, 0.414, 0.43, 0.443 };
	// c clamp for 2s set to 0 as per Ken Campbell's email of Aug 18 2008.
	private double[] clamp = { 3.0, 6.0, 0.0, 6.0, 6.0, 6.0, 3.0, 0.0, 6.0, 6.0, 6.0 };
	// @formatter:on

	private HashMap<Double, Integer> indexFromPerHashMap;

	private int iper;
	private double rRup, mag;
	private SiteType siteType;
	private boolean clampMean, clampStd;

	// clamping in addition to one-sided 3s; unique to nshmp and hidden
	private BooleanParameter clampMeanParam;
	private BooleanParameter clampStdParam;
	private EnumParameter<SiteType> siteTypeParam;

	// lowered to 4 from5 for CEUS mblg conversions
	private final static Double MAG_WARN_MIN = new Double(4);
	private final static Double MAG_WARN_MAX = new Double(8);
	private final static Double DISTANCE_RUP_WARN_MIN = new Double(0.0);
	private final static Double DISTANCE_RUP_WARN_MAX = new Double(1000.0);

	private transient ParameterChangeWarningListener warningListener = null;

	/**
	 * This initializes several ParameterList objects.
	 * @param listener
	 */
	public Campbell_2003_AttenRel(ParameterChangeWarningListener listener) {
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
		siteTypeParam.setValue((SiteType) site.getParameter(
			siteTypeParam.getName()).getValue());
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
		// Check that parameter exists
		if (im == null) {
			throw new ParameterException("Intensity Measure Param not set");
		}
		iper = indexFromPerHashMap.get(saPeriodParam.getValue());
		// parameterChange = true;
		intensityMeasureChanged = false;
	}

	@Override
	public double getMean() {
		// check if distance is beyond the user specified max
		if (rRup > USER_MAX_DISTANCE) {
			return VERY_SMALL_MEAN;
		}
		if (intensityMeasureChanged) {
			setCoeffIndex(); // updates intensityMeasureChanged
		}
		return getMean(iper, siteType, rRup, mag);
	}

	@Override
	public double getStdDev() {
		if (intensityMeasureChanged) {
			setCoeffIndex();// updates intensityMeasureChanged
		}
		return getStdDev(iper, mag);
	}

	@Override
	public void setParamDefaults() {
		siteTypeParam.setValueAsDefault(); // shouldn't be necessary
		clampMeanParam.setValueAsDefault();
		clampStdParam.setValueAsDefault();

		magParam.setValueAsDefault();
		distanceRupParam.setValueAsDefault();
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		pgaParam.setValueAsDefault();
		// stdDevTypeParam.setValueAsDefault();

		siteType = siteTypeParam.getValue();
		clampMean = clampMeanParam.getValue();
		clampStd = clampStdParam.getValue();
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
		meanIndependentParams.addParameter(siteTypeParam);
		meanIndependentParams.addParameter(magParam);

		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
		// stdDevIndependentParams.addParameter(distanceRupParam);
		stdDevIndependentParams.addParameter(magParam);
		// stdDevIndependentParams.addParameter(stdDevTypeParam);

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
		siteTypeParam = Params.createSiteType();
		siteParams.clear();
		siteParams.addParameter(siteTypeParam);
	}

	@Override
	protected void initEqkRuptureParams() {
		magParam = new MagParam(MAG_WARN_MIN, MAG_WARN_MAX);
		eqkRuptureParams.clear();
		eqkRuptureParams.addParameter(magParam);
	}

	@Override
	protected void initPropagationEffectParams() {
		distanceRupParam = new DistanceRupParameter(0.0);
		DoubleConstraint warn = new DoubleConstraint(DISTANCE_RUP_WARN_MIN,
			DISTANCE_RUP_WARN_MAX);
		warn.setNonEditable();
		distanceRupParam.addParameterChangeWarningListener(listener);
		distanceRupParam.setWarningConstraint(warn);
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
		// init other params defined in parent class
		super.initOtherParams();
		// the stdDevType Parameter
		// StringConstraint stdDevTypeConstraint = new StringConstraint();
		// stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
		// stdDevTypeConstraint.addString(STD_DEV_TYPE_BASEMENT);
		// stdDevTypeConstraint.setNonEditable();
		// stdDevTypeParam = new StdDevTypeParam(stdDevTypeConstraint);

		clampMeanParam = new BooleanParameter("Clamp Mean", true);
		clampStdParam = new BooleanParameter("Clamp Std. Dev.", true);

		// add these to the list
		// otherParams.addParameter(stdDevTypeParam);
		otherParams.addParameter(clampMeanParam);
		otherParams.addParameter(clampStdParam);
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
		// parameterChange = true;
		if (pName.equals(DistanceRupParameter.NAME)) {
			rRup = ((Double) val).doubleValue();
		} else if (pName.equals(siteTypeParam.getName())) {
			siteType = siteTypeParam.getValue();
		} else if (pName.equals(clampMeanParam.getName())) {
			clampMean = clampMeanParam.getValue();
		} else if (pName.equals(clampStdParam.getName())) {
			clampStd = clampStdParam.getValue();
		} else if (pName.equals(MagParam.NAME)) {
			mag = ((Double) val).doubleValue();
		} else if (pName.equals(PeriodParam.NAME)) {
			intensityMeasureChanged = true;
		}
	}

	@Override
	public void resetParameterEventListeners() {
		distanceRupParam.removeParameterChangeListener(this);
		siteTypeParam.removeParameterChangeListener(this);
		clampMeanParam.removeParameterChangeListener(this);
		clampStdParam.removeParameterChangeListener(this);
		magParam.removeParameterChangeListener(this);
		saPeriodParam.removeParameterChangeListener(this);
		this.initParameterEventListeners();
	}

	@Override
	protected void initParameterEventListeners() {
		distanceRupParam.addParameterChangeListener(this);
		siteTypeParam.addParameterChangeListener(this);
		clampMeanParam.addParameterChangeListener(this);
		clampMeanParam.addParameterChangeListener(this);
		magParam.addParameterChangeListener(this);
		saPeriodParam.addParameterChangeListener(this);
	}

	/**
	 * @throws MalformedURLException if returned URL is not a valid URL.
	 * @return the URL to the AttenuationRelationship document on the Web.
	 */
	public URL getAttenuationRelationshipURL() throws MalformedURLException {
		return null;
	}

	private double getMean(int iper, SiteType st, double rRup, double mag) {
		double period = pd[iper];
		double gnd0 = (st == HARD_ROCK) ? c1h[iper] : c1[iper];
		// if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
		double gndm = gnd0 + c2[iper] * mag + c3[iper] * (8.5 - mag) *
			(8.5 - mag);
		double cfac = Math.pow((c5[iper] * Math.exp(c6[iper] * mag)), 2);

		// double h = max(dtor(kk), 5.0);
		// double hsq = h * h;
		// double dist = Math.sqrt(dist0 * dist0 + hsq);
		// this is taken care of... all gridded inputs for CEUS have dtor=5km

		double arg = Math.sqrt(rRup * rRup + cfac);
		double fac = 0.0;
		if (rRup > 70.0) fac = c7[iper] * (Math.log(rRup) - LOG_70);
		if (rRup > 130.0) fac = fac + c8[iper] * (Math.log(rRup) - LOG_130);
		double gnd = gndm + c4[iper] * Math.log(arg) + fac +
			(c9[iper] + c10[iper] * mag) * rRup;

		if (clampMean) gnd = Utils.ceusMeanClip(period, gnd);
		return gnd;
	}

	private double getStdDev(int iper, double mag) {
		// if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
		return (mag < 7.16) ? c11[iper] + c12[iper] * mag : c13[iper];
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls) {
		return Utils.getExceedProbabilities(imls, getMean(), getStdDev(),
			clampStd, clamp[iper]);
	}

	private static final double LOG_70 = 4.2484952;
	private static final double LOG_130 = 4.8675345;

	// double getCampCEUS(int ip, int iq, int ir, int ia, int ndist, double di,
	// int nmag,
	// double magmin, double dmag, double sigmanf, double distnf) {
	// // c----- Campbell 2001 CEUS modified for nga style, with all coeffs
	// internal defined.
	// // c-----
	// // parameter (np=11,sqrt2=1.4142136,alg70=4.2484952,alg130=4.8675345)
	// // c precompute log(70) and log(130) used below.
	// // c seismicity depth comes in via depth_rup now. Not h() as in 2002.
	// // c inputs ip,iq period
	// // c ir=1 BC or firm rock
	// // c ir=2 A or hard rock. Only difference is in constant term (check
	// this)
	// // real magmin,probgt3,tempgt3,gnd,cmagsig
	// // logical et,deagg,sp !short period; if true a CEUS gm bound applies.
	// // common/depth_rup/ntor,dtor(3),wtor(3),wtor65(3)
	// // c wtor = weights applied to top of CEUS seismicity (km).
	// // common / atten / pr, xlev, nlev, iconv, wt, wtdist
	// // common/e0_ceus/e0_ceus(260,31,8)
	// // common/deagg/deagg
	// // dimension pr(260,38,20,8,3,3),xlev(20,8),nlev(8),iconv(8,8),
	// // + wt(8,8,2),wtdist(8,8)
	// // real,dimension(np):: c1,c2,c3,c4,c5,c6,c7,c8,c9,c10,
	// // 1 clamp,c1h,c11,c12,c13,perx
	// // c
	// // perx = (/0.01,0.2,1.0,0.1,0.3,0.4,
	// // + 0.5,2.0,.03,.04,.05/)
	// // c1= (/0.4492,.1325,-.3177,.4064,-0.1483,-0.17039,
	// // + -0.1333,-1.2483,1.68,1.28,0.87/)
	// // c1h= (/0.0305,-0.4328,-.6104,-0.1475,-.6906,-0.67076736,
	// // + -.5907,-1.4306,1.186,0.72857,0.3736/)
	// // c2= (/.633,.617,.451,.613,0.609,0.5722637,
	// // + 0.534,0.459,.622,.618622,.616/)
	// // c3= (/-.0427,-.0586,-.2090,-.0353,-0.0786,-0.10939892,
	// // + -0.1379,-0.2552,-.0362,-.035693,-.0353/)
	// // c4= (/-1.591,-1.32,-1.158,-1.369,-1.28,-1.244142,
	// // + -1.216,-1.124,-1.691,-1.5660,-1.469/)
	// // c5= (/.683,.399,.299,0.484,0.349,0.32603806,
	// // + 0.318,.310,0.922,0.75759,0.630/) !paper's c7
	// // c6= (/.416,.493,.503,0.467,0.502,0.5040741,
	// // + 0.503,.499,.376,0.40246,0.423/) !paper's c8
	// // c7= (/1.140,1.25,1.067,1.096,1.241,1.1833254,
	// // + 1.116,1.015,0.759,0.76576,0.771/) !paper's c9
	// // c8= (/-.873,-.928,-.482,-1.284,-.753,-0.6529481,
	// // + -0.606,-.4170,-.922,-1.1005,-1.239/) !paper's c10
	// // c9= (/-.00428,-.0046,-.00255,-.00454,-.00414,-3.7463151E-3,
	// // + -.00341,-.00187,-.00367,-.0037319,-.00378/) !paper's c5
	// // c10= (/.000483,.000337,.000141,.00046,.000263,2.1878805E-4,
	// // + .000194,.000103,.000501,.00050044,.0005/) !paper's c6
	// // c11= (/1.030,1.077,1.110,1.059,1.081,1.0901983,
	// // + 1.098,1.093,1.03,1.037,1.042 /) !paper's c11
	// // c12= (/-.0860,-.0838,-.0793,-.0838,-0.0838,-0.083180725,
	// // + -0.0824,-.0758,-.086,-0.0848,-.0838/) !paper's c12
	// // c13= (/0.414,.478,.543,0.460,0.482,0.49511834,
	// // + 0.508,0.551,.414,0.43,.443/) !paper's c13
	// // c clamp for 2s set to 0 as per Ken Campbell's email of Aug 18 2008.
	// // clamp= (/3.0,6.0,0.,6.,6.,6.,3.0,0.,6.,6.,6./)
	// double period = pd[iq];
	// double cmagsig= 7.16;
	// // c set up erf matrix p as ftn of dist,mag,period,level,depth to
	// seismicity,/
	// // c--- Mmin=6.0 nmag=45 dmag=0.05
	// // sp = period.gt.0.02.and.period.lt.0.5
	// double gnd0 = (st == HARD_ROCK) ? c1h[iper] : c1[iper];
	//
	// // if(ir.eq.1)then
	// // gnd0=c1[iq]
	// // else
	// // gnd0=c1h[iq]
	// // endif
	// // c-- loop through magnitudes
	// // do 104 m=1,nmag
	// // xmag0= magmin+(m-1)*dmag
	// // c--- loop through atten. relations for each period
	// // c-- gnd for SS; gnd2 for thrust; gnd3 for normal
	// double xmag= xmag0
	// // c Two mblg to Mw conversion rules
	// if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
	// // if(iconv(ip,ia).eq.1)then
	// // xmag= 1.14 +0.24*xmag0+0.0933*xmag0*xmag0
	// // elseif(iconv(ip,ia).eq.2) then
	// // xmag= 2.715 -0.277*xmag0+0.127*xmag0*xmag0
	// // endif
	// double gndm = gnd0 + c2[iq]*xmag + c3[iq]*(8.5-xmag)*(8.5-xmag);
	// double csigmam;
	// if (xmag < cmagsig) {
	// csigmam= c11[iq]+ c12[iq]*xmag;
	// } else {
	// csigmam= c13[iq];
	// }
	// double cfac = Math.pow((c5[iq]*Math.exp(c6[iq]*xmag)), 2);
	// // c loop through dtor. There is a fictitious h term as well. how to use
	// dtor?
	// // do 103 kk=1,ntor !new 7/06
	// double h=max(dtor(kk),5.0);
	// // c generally h was 5 km in the 2002 maps. For Charleston charactristic,
	// h was 10 km.
	// double hsq=h*h;
	// // et= deagg .and. kk.eq.1
	// // c-- loop through distances
	// // do 103 ii=1,ndist
	// // dist0= (float(ii) - 0.5)*di
	// // weight= wt(ip,ia,1)
	// // if(dist0.gt.wtdist(ip,ia)) weight= wt(ip,ia,2)
	//
	// double rRup;
	// double h=max(dtor(kk),5.0);
	// double hsq=h*h;
	// double dist= Math.sqrt(dist0*dist0 + hsq);
	//
	// double arg= Math.sqrt(rRup*rRup + cfac);
	// // if (dist.lt.distnf)then
	// // sigmasq=(csigmam+sigmanf)*sqrt2
	// // else
	// // sigmasq=csigmam*sqrt2
	// // endif
	// double fac=0.0;
	// if(dist > 70.0) fac= c7[iq]*(Math.log(dist)- LOG_70);
	// if(dist > 130.0) fac= fac+ c8[iq]*(Math.log(dist)-LOG_130);
	// double gnd = gndm + c4[iq]*Math.log(arg) + fac
	// +(c9[iq]+c10[iq]*xmag)*dist;
	//
	// if (clampMean) gnd = Utils.ceusMeanClip(period, gnd);
	// return gnd;
	// // c write(12,*) period,dist0,xmag,exp(gnd)
	// // c--- following is for clipping
	// // c---following is for clipping gnd motions: 1.5g PGA, 3.00g 0.3, 3.0g
	// 0.2
	// // if(period.lt.0.018)then
	// // gnd=min(0.405,gnd)
	// // elseif(sp)then
	// // gnd=min(gnd,1.099)
	// // endif
	// // test0=gnd + 3.*sigmasq/sqrt2
	// // test= exp(test0)
	// // if(clamp(iq).lt.test .and. clamp(iq).gt.0.) then
	// // clamp2= alog(clamp(iq))
	// // else
	// // clamp2= test0
	// // endif
	// // tempgt3= (gnd- clamp2)/sigmasq
	// // probgt3= (erf(tempgt3)+1.)*0.5
	// // do 102 k=1,nlev(ip)
	// // temp= (gnd- xlev(k,ip))/sigmasq
	// // temp1= (erf(temp)+1.)*0.5
	// // temp1= (temp1-probgt3)/(1.-probgt3)
	// // if(temp1.lt.0.) goto 103 !safe to transfer out once prob < 0
	// // fac=weight*temp1
	// // pr(ii,m,k,ip,kk,1)= pr(ii,m,k,ip,kk,1) + fac
	// // if(et)e0_ceus(ii,m,ip)= e0_ceus(ii,m,ip)-sqrt2*temp*fac
	// // 102 continue
	// // 103 continue
	// // 104 continue
	// // return
	// // end subroutine getCampCEUS
	// }
}
