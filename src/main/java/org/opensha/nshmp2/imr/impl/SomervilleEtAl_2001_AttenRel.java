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
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;

import com.google.common.collect.Maps;

/**
 * Implementation of the hard rock attenuation relationship for the Central and
 * Eastern US by Somerville et al. (2001). This implementation matches that used
 * in the 2008 USGS NSHMP. <br />
 * <br />
 * See: Somerville, P., Collins, N., Abrahamson, N., Graves, R., and Saikia, C.,
 * 2001, Ground motion attenuation relations for the Central and Eastern United
 * States—Final report, June 30, 2001: Report to U.S. Geological Survey for
 * award 99HQGR0098, 38 p.<br />
 * 
 * TODO check doc that distance is rjb
 * 		verify that Somerville imposes dtor of 6.0:
 * 		THIS IS NOT IMPOSED IN HAZFX
 * 		e.g. double dist = Math.sqrt(rjb * rjb + 6.0 * 6.0);
 * 
 * @author Peter Powers
 * @version $Id$
 */
public class SomervilleEtAl_2001_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	public final static String SHORT_NAME = "SomervilleEtAl2001";
	private static final long serialVersionUID = 1234567890987654353L;
	public final static String NAME = "Somerville et al. (2001)";

	// coefficients:
	// @formatter:off
	 private double[] pd = { 0.0,0.2,1.0,0.1,0.3,0.5,2.0,-1.0 };
     private double[] a1 = { 0.658,1.358,-0.0143,1.442,1.2353,0.8532,-0.9497 };
     private double[] a1h = { 0.239,0.793,-0.307,0.888,0.6930,0.3958,-1.132 };
     private double[] a2 = { 0.805,0.805,0.805,0.805,0.805,0.805,0.805 };
     private double[] a3 = { -0.679,-0.679,-0.696,-0.679,-0.67023,-0.671792,-0.728 };
     private double[] a4 = { 0.0861,0.0861, 0.0861, 0.0861,0.0861, 0.0861, 0.0861 };
     private double[] a5 = { -0.00498,-0.00498,-0.00362,-0.00498,-0.0048045,-0.00442189,-0.00221 };
     private double[] a6 = { -0.477,-0.477,-0.755,-0.477,-0.523792,-0.605213,-0.946 };
     private double[] a7 = { 0.0,0.0,-0.102,0.0,-0.030298,-0.0640237,-0.140 };
     private double[] sig0 = { 0.587,0.611,0.693,0.595, 0.6057, 0.6242,0.824 };
     private double[] clamp = { 3.0,6.0,0.0,6.0,6.0,6.0,0.0 };
 	// @formatter:on

	private HashMap<Double, Integer> indexFromPerHashMap;

	private int iper;
	private double rjb, mag;
	private SiteType siteType;
//	private MagnitudeType magType;
//	private FaultCode magConvCode;
	private boolean clampMean, clampStd;

	// clamping in addition to one-sided 3s; unique to nshmp and hidden
	private BooleanParameter clampMeanParam;
	private BooleanParameter clampStdParam;
//	private EnumParameter<MagnitudeType> magTypeParam;
	private EnumParameter<SiteType> siteTypeParam;
//	private EnumParameter<FaultCode> magConvCodeParam;

	// lowered to 4 from5 for CEUS mblg conversions
	private final static Double MAG_WARN_MIN = new Double(4);
	private final static Double MAG_WARN_MAX = new Double(8);
	private final static Double DISTANCE_JB_WARN_MIN = new Double(0.0);
	private final static Double DISTANCE_JB_WARN_MAX = new Double(1000.0);

	private transient ParameterChangeWarningListener warningListener = null;

	/**
	 * This initializes several ParameterList objects.
	 * @param listener
	 */
	public SomervilleEtAl_2001_AttenRel(ParameterChangeWarningListener listener) {
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
			distanceJBParam.setValue(eqkRupture, site);
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
		if (rjb > USER_MAX_DISTANCE) {
			return VERY_SMALL_MEAN;
		}
		if (intensityMeasureChanged) {
			setCoeffIndex(); // updates intensityMeasureChanged
		}
		return getMean(iper, siteType, rjb, mag);
	}

	@Override
	public double getStdDev() {
		if (intensityMeasureChanged) {
			setCoeffIndex();// updates intensityMeasureChanged
		}
		return getStdDev(iper);
	}

	@Override
	public void setParamDefaults() {
		siteTypeParam.setValueAsDefault(); // shouldn't be necessary
//		magTypeParam.setValueAsDefault();
//		magConvCodeParam.setValueAsDefault();
		clampMeanParam.setValueAsDefault();
		clampStdParam.setValueAsDefault();

		magParam.setValueAsDefault();
		distanceJBParam.setValueAsDefault();
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		pgaParam.setValueAsDefault();
		// stdDevTypeParam.setValueAsDefault();

		siteType = siteTypeParam.getValue();
//		magType = magTypeParam.getValue();
//		magConvCode = magConvCodeParam.getValue();
		clampMean = clampMeanParam.getValue();
		clampStd = clampStdParam.getValue();
		rjb = distanceJBParam.getValue();
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
		meanIndependentParams.addParameter(distanceJBParam);
		 meanIndependentParams.addParameter(siteTypeParam);
		meanIndependentParams.addParameter(magParam);

		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
//		stdDevIndependentParams.addParameter(distanceJBParam);
//		stdDevIndependentParams.addParameter(magParam);
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
		distanceJBParam = new DistanceJBParameter(0.0);
		distanceJBParam.addParameterChangeWarningListener(warningListener);
		DoubleConstraint warn = new DoubleConstraint(DISTANCE_JB_WARN_MIN,
			DISTANCE_JB_WARN_MAX);
		warn.setNonEditable();
		distanceJBParam.setWarningConstraint(warn);
		distanceJBParam.setNonEditable();
		propagationEffectParams.addParameter(distanceJBParam);
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

//		magTypeParam = new EnumParameter<MagnitudeType>("Magnitude Type",
//			EnumSet.of(MOMENT, LG_PHASE), LG_PHASE, null);
//		magConvCodeParam = new EnumParameter<FaultCode>(
//			"Mag. Conversion Method", EnumSet.of(M_CONV_AB, M_CONV_J),
//			M_CONV_J, null);
		clampMeanParam = new BooleanParameter("Clamp Mean", true);
		clampStdParam = new BooleanParameter("Clamp Std. Dev.", true);

		// add these to the list
		// otherParams.addParameter(stdDevTypeParam);
//		otherParams.addParameter(magTypeParam);
//		otherParams.addParameter(magConvCodeParam);
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
		if (pName.equals(DistanceJBParameter.NAME)) {
			rjb = ((Double) val).doubleValue();
		} else if (pName.equals(siteTypeParam.getName())) {
			siteType = siteTypeParam.getValue();
//		} else if (pName.equals(magTypeParam.getName())) {
//			magType = magTypeParam.getValue();
//			magConvCodeParam.getEditor().setEnabled(magType == LG_PHASE);
//		} else if (pName.equals(magConvCodeParam.getName())) {
//			magConvCode = magConvCodeParam.getValue();
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
		distanceJBParam.removeParameterChangeListener(this);
		siteTypeParam.removeParameterChangeListener(this);
//		magTypeParam.removeParameterChangeListener(this);
//		magConvCodeParam.removeParameterChangeListener(this);
		clampMeanParam.removeParameterChangeListener(this);
		clampStdParam.removeParameterChangeListener(this);
		magParam.removeParameterChangeListener(this);
		saPeriodParam.removeParameterChangeListener(this);
		this.initParameterEventListeners();
	}

	@Override
	protected void initParameterEventListeners() {
		distanceJBParam.addParameterChangeListener(this);
		siteTypeParam.addParameterChangeListener(this);
//		magTypeParam.addParameterChangeListener(this);
//		magConvCodeParam.addParameterChangeListener(this);
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

	private static final double dist1 = Math.sqrt(50.0 * 50.0 + 6.0 * 6.0);
	// TODO this needs to be updated with code from hazFX which is quite
	// different in terms of coefficients used.
	private double getMean(int iper, SiteType st, double rjb, double mag) {
		double period = pd[iper];
		double gnd0 = (st == HARD_ROCK) ? a1h[iper] : a1[iper];
//		if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
		double gndm = gnd0 + a2[iper] * (mag - 6.4) + a7[iper] * (8.5 - mag) *
			(8.5 - mag);

//		double dist = Math.sqrt(rjb * rjb + 6.0 * 6.0);
		// Somerville fixes depth at 6km - faults and gridded
		double dist = Math.sqrt(rjb * rjb + 36.0); 
		double gnd;
		if (rjb < 50.0) {
			gnd = gndm + a3[iper] * Math.log(dist) + a4[iper] * (mag - 6.4) *
				Math.log(dist) + a5[iper] * rjb;
		} else {
			gnd = gndm + a3[iper] * Math.log(dist1) + a4[iper] * (mag - 6.4) *
				Math.log(dist) + a5[iper] * rjb + a6[iper] *
				(Math.log(dist) - Math.log(dist1));
		}

		if (clampMean) gnd = Utils.ceusMeanClip(period, gnd);

		return gnd;
	}

	private double getStdDev(int iper) {
		return sig0[iper];
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls) {
		return Utils.getExceedProbabilities(imls, getMean(), getStdDev(),
			clampStd, clamp[iper]);
	}

//	// coefficients:
//	// @formatter:off
//	 private double[] pd = { 0.0,0.2,1.0,0.1,0.3,0.5,2.0,-1.0 };
//     private double[] a1 = { 0.658,1.358,-0.0143,1.442,1.2353,0.8532,-0.9497 };
//     private double[] a1h = { 0.239,0.793,-0.307,0.888,0.6930,0.3958,-1.132 };
//     private double[] a2 = { 0.805,0.805,0.805,0.805,0.805,0.805,0.805 };
//     private double[] a3 = { -0.679,-0.679,-0.696,-0.679,-0.67023,-0.671792,-0.728 };
//     private double[] a4 = { 0.0861,0.0861, 0.0861, 0.0861,0.0861, 0.0861, 0.0861 };
//     private double[] a5 = { -0.00498,-0.00498,-0.00362,-0.00498,-0.0048045,-0.00442189,-0.00221 };
//     private double[] a6 = { -0.477,-0.477,-0.755,-0.477,-0.523792,-0.605213,-0.946 };
//     private double[] a7 = { 0.0,0.0,-0.102,0.0,-0.030298,-0.0640237,-0.140 };
//     private double[] sig0 = { 0.587,0.611,0.693,0.595, 0.6057, 0.6242,0.824 };
//     private double[] clamp = { 3.0,6.0,0.0,6.0,6.0,6.0,0.0 };
// 	// @formatter:on

	// public void getSomer(int ip, int iq, int ir, int ia, int ndist, double
	// di, int nmag, double magmin, double dmag, double sigmanf, double distnf)
	// {
	// // c---- Somerville et al (2001) for CEUS. Coeffs for pga, 0.2 and 1.0s
	// +4 other T sa
	// // c --- adapted to nga style, include coeff values rather than read 'em
	// in
	// // c ir controls rock conditions:
	// // c ir=1 BC or firm rock
	// // c ir=2 hard rock
	// // parameter (np=7,sqrt2=1.414213562)
	// // real magmin,period
	// // logical deagg,sp
	// // common/depth_rup/ntor,dtor(3),wtor(3),wtor65(3)
	// // c wtor = weights to locations of top of rupture.
	// // c these are applied in main, to rate matrices.
	// // c Do not apply wtor here. We do apply att model epist. weight wt()
	// here, however.
	// // common / atten / pr, xlev, nlev, iconv, wt, wtdist
	// // common/deagg/deagg
	// // dimension pr(260,38,20,8,3,3),xlev(20,8),nlev(8),iconv(8,8),
	// // + wt(8,8,2),wtdist(8,8)
	// // real perx(8) !possible period set.
	// // c perx(8) corresponds to PGV which is not set up for Somerville. Could
	// check
	// // c if that relation has a PGV model. SH July 31 2006.
	// // real a1(np),a1h(np),a2(np),a3(np),a4(np),a5(np),a6(np)
	// // real a7(np),sig0(np),clamp(np)
	// // perx = (/0.,0.2,1.0,0.1,0.3,0.5,2.0,-1./)
	// // a1 = (/0.658,1.358,-0.0143,1.442,1.2353,0.8532,-0.9497/)
	// // a1h = (/0.239,0.793,-0.307,0.888,0.6930,0.3958,-1.132/)
	// // a2 = (/0.805,0.805,0.805,0.805,0.805,0.805,0.805/)
	// // a3 = (/-0.679,-.679,-.696,-.679,-.67023,-.671792,-0.728/)
	// // a4 = (/0.0861,0.0861,.0861,.0861,0.0861,.0861,.0861/)
	// // a5 =
	// (/-0.00498,-.00498,-0.00362,-.00498,-.0048045,-.00442189,-0.00221/)
	// // a6 = (/-0.477,-.477,-0.755,-.477,-.523792,-.605213,-.946/)
	// // a7 = (/0.,0.,-0.102,0.,-.030298,-.0640237,-.140/)
	// // sig0 = (/0.587,0.611,0.693,0.595,.6057,.6242,0.824/)
	// // clamp = (/3.,6.,0.,6.,6.,6.,0./)
	// // c compute SOmerville median and dispersion estimates.
	// double dist1= Math.sqrt(50.0*50.0+ 6.0*6.0);
	// // c set up erf matrix p as ftn of dist,mag,period,level,flt type,atten
	// type
	// double period=pd[iq];
	// //sp=perx[iq].gt.0.02 .and. perx[iq].lt. 0.5
	// double sig= sig0[iq];
	// // if(ir.eq.1)then
	// // gnd0=a1[iq]
	// // else
	// // gnd0=a1h[iq]
	// double gnd0 = (st == HARD_ROCK) ? a1h[iper] : a1[iper];
	//
	//
	// // c hard rock varioation in Somerville only affects a1 coef.
	// // endif
	// // c-- loop through magnitudes
	// // do 104 m=1,nmag
	// // xmag0= magmin + (m-1)*dmag
	// // double xmag0 = mag;
	// // c--- loop through atten. relations for each period
	// // c-- gnd for SS; gnd2 for thrust; gnd3 for normal
	// if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
	//
	// // if(iconv(ip,ia).eq.1)then
	// // xmag= 1.14 +0.24*xmag0+0.0933*xmag0*xmag0
	// // elseif(iconv(ip,ia).eq.2)then
	// // xmag= 2.715 -0.277*xmag0+0.127*xmag0*xmag0
	// // endif
	// double gndm= gnd0 + a2[iq]*(mag-6.4)+ a7[iq]*(8.5-mag)*(8.5-mag);
	// // weight= wt(ip,ia,1)
	// // c-- loop through distances
	// // do 103 ii=1,ndist
	// // dist0= (float(ii)-0.5)*di
	// // if(dist0.lt.distnf) then
	// // sigp= sig + sigmanf
	// // else
	// // sigp=sig
	// // endif
	// double dist0;
	// double sigmasq= sigp *SQRT_2;
	// // if(dist0.gt.wtdist(ip,ia)) weight= wt(ip,ia,2)
	// // c what about using variable h below?
	// double dist= Math.sqrt(dist0*dist0+ 6.0*6.0);
	// double gnd;
	// if (dist0 < 50.0) {
	// gnd= gndm + a3[iq]*Math.log(dist)+a4[iq]*(mag-6.4)*Math.log(dist)
	// + a5[iq]*dist0;
	// } else {
	// gnd= gndm + a3[iq]*Math.log(dist1)+a4[iq]*(mag-6.4)*Math.log(dist)
	// +a5[iq]*dist0 + a6[iq]*(Math.log(dist)-Math.log(dist1));
	// }
	//
	// if (clampMean) gnd = Utils.ceusMeanClip(period, gnd);
	//
	// // c---following is for clipping gnd motions: 1.5g PGA, 3.0g for 0.3s,
	// 3.0g 0.2s sa median
	// // if(period.eq.0.)then
	// // gnd=min(0.405,gnd)
	// // elseif(sp)then
	// // gnd=min(gnd,1.099)
	// // endif
	// // test0=gnd + 3.*sigmasq/sqrt2
	// // test= exp(test0)
	// // if(clamp[iq].lt.test .and. clamp[iq].gt.0.) then
	// // clamp2= alog(clamp[iq])
	// // else
	// // clamp2= test0
	// // endif
	// // tempgt3= (gnd- clamp2)/sigmasq
	// // probgt3= (erf(tempgt3)+1.)*0.5
	// // do 102 k=1,nlev(ip)
	// // temp= (gnd- xlev(k,ip))/sigmasq
	// // temp1= (erf(temp)+1.)*0.5
	// // temp1= (temp1-probgt3)/(1.-probgt3)
	// // if(temp1.lt.0.) goto 103 !no more calcs once p<0
	// // do kk=1,ntor
	// // c Somerville: no variation in median wrt depth to seismicity. Just
	// fill out kk index
	// // c with same scalar
	// // pr(ii,m,k,ip,kk,1)= pr(ii,m,k,ip,kk,1) + weight*temp1
	// // enddo
	// // 102 continue
	// // 103 continue
	// // 104 continue
	// // return
	// //// end subroutine getSomer
	// // return 0;
	// }
}
