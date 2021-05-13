package org.opensha.nshmp2.imr.impl;

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
 * Implementation of mid-continent Toro et al. (1997) attenuation relationship
 * with 2002 updates. This is a limited implementation that matches that used in
 * the 2008 USGS NSHMP (e.g. it does not include additional epistemic uncertainty
 * branching.<br />
 * <br />
 * See: Toro, G.R., 2002, Modification of the Toro et al. (1997) attenuation
 * relations for large magnitudes and short distances: Risk Engineering, Inc.
 * report, http://www.riskeng. com/PDF/atten_toro_extended.pdf<br />
 * <br />
 * and<br />
 * <br />
 * Toro, G.R., Abrahamson, N.A., and Schneider, J.F., 1997, A model of strong
 * ground motions from earthquakes in central and eastern North America—Best
 * estimates and uncertain- ties: Seismological Research Letters, v. 68, p.
 * 41–57.<br />
 * 
 * @author Peter Powers
 * @version $Id$
 */
public class ToroEtAl_1997_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	private final static String C = "ToroEtAl_1997_AttenRel";
	private final static boolean D = false;
	public final static String SHORT_NAME = "ToroEtAl1997";
	private static final long serialVersionUID = 1234567890987654353L;
	public final static String NAME = "Toro et al. (1997)";

	// coefficients:
	// @formatter:off
	// added 0.04 and 0.4 s coeffs july 16 2008 (NRC files)
	private double[] perx = { 0.0, 0.2, 1.0, 0.1, 0.3, 0.5, 2.0, 0.04, 0.4 };
	// tb MbLg coeffs. BC/A 2-hz Siteamp = 1.58, with BC-A coef. diff. of 0.4574.
	private double[] tb1 = { 2.489, 2.165, 0.173, 2.91, 1.7323, 1.109, -0.788, 4.0, 1.2 };
	private double[] tb1h = { 2.07, 1.6, -0.12, 2.36, 1.19, 0.652, -0.97, 3.54, 0.90 };
	private double[] tb2 = { 1.20, 1.24, 2.05, 1.23, 1.51, 1.785, 2.52, 1.19, 1.70 };
	private double[] tb3 = { 0.0, 0.0, -0.34, 0.0, -0.11, -0.2795, -0.47, 0.0, -0.26 };
	private double[] tb4 = { 1.28, 0.98, 0.90, 1.12, 0.96, 0.930, 0.93, 1.46, 0.94 };
	private double[] tb5 = { 1.23, 0.74, 0.59, 1.05, 0.6881, 0.6354, 0.6, 1.84, 0.65 };
	private double[] tb6 = { 0.0018, 0.0039, 0.0019, 0.0043, 0.0034, 0.002732, 0.0012, 0.0010, .0030 };

	// tc Mw coeffs for BC rock. 3hz BC-A is 0.5423 (BC/A siteamp is then 1.72)
	// tc Mw coeffs. 3.33 hz is log-log from the 2.5 and 5 hz values.
	private double[] tc1 = { 2.619, 2.295, 0.383, 2.92, 1.8823, 1.2887, -0.558, 4.0, 1.4 };
	private double[] tc1h = { 2.20, 1.73, 0.09, 2.37, 1.34, 0.8313, -0.740, 3.68, 1.07 };
	private double[] tc2 = { 0.81, 0.84, 1.42, 0.81, 0.964, 1.14, 1.86, 0.80, 1.05 };
	private double[] tc3 = { 0.0, 0.0, -0.2, 0.0, -0.059, -0.1244, -0.31, 0.0, -0.10 };
	private double[] tc4 = { 1.27, 0.98, 0.90, 1.1, 0.951, 0.9227, 0.92, 1.46, 0.93 };
	private double[] tc5 = { 1.16, 0.66, 0.49, 1.02, 0.601, 0.5429, 0.46, 1.77, 0.56 };
	private double[] tc6 = { 0.0021, 0.0042, 0.0023, 0.004, 0.00367, 0.00306, 0.0017, 0.0013, 0.0033 };
	
	private double[] tbh = { 9.3, 7.5, 6.8, 8.5, 7.35, 7.05, 7.0, 10.5, 7.2 };
	private double[] th = { 9.3, 7.5, 6.8, 8.3, 7.26, 7.027, 6.9, 10.5, 7.1 };
	private double[] clamp = { 3.0, 6.0, 0.0, 6.0, 6.0, 6.0, 0.0, 6.0, 6.0 };

	// Sigma in nat log units. Saves a divide
	// Toro : slightly larger sigma for 1 and 2 s. Toro Lg based mag has
	// larger sigma for larger M (table 3, p 50 ,srl 1997. This isn't
	// in our rendering)
	private double[] tsigma = { 0.7506, 0.7506, 0.799, 0.7506, 0.7506, 0.7506, 0.799, 0.7506, 0.7506 };
	// @formatter:on
	
	private HashMap<Double, Integer> indexFromPerHashMap;

	private int iper;
	private double rjb, mag;
	private SiteType siteType;
	private MagnitudeType magType;
	private boolean clampMean, clampStd;
	
	// clamping in addition to one-sided 3s; unique to nshmp and hidden
	private BooleanParameter clampMeanParam;
	private BooleanParameter clampStdParam;
	private EnumParameter<MagnitudeType> magTypeParam;
	private EnumParameter<SiteType> siteTypeParam;

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
	public ToroEtAl_1997_AttenRel(ParameterChangeWarningListener listener) {
		warningListener = listener;
		initSupportedIntensityMeasureParams();
		indexFromPerHashMap = Maps.newHashMap();
		for (int i = 0; i < perx.length; i++) {
			indexFromPerHashMap.put(new Double(perx[i]), new Integer(i));
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
		magTypeParam.setValueAsDefault();
		clampMeanParam.setValueAsDefault();
		clampStdParam.setValueAsDefault();

		magParam.setValueAsDefault();
		distanceJBParam.setValueAsDefault();
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		pgaParam.setValueAsDefault();
		//stdDevTypeParam.setValueAsDefault();

		siteType = siteTypeParam.getValue();
		magType = magTypeParam.getValue();
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
//		stdDevIndependentParams.addParameter(stdDevTypeParam);

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
		for (int i = 0; i < perx.length; i++) {
			perConstraint.addDouble(new Double(perx[i]));
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
//		StringConstraint stdDevTypeConstraint = new StringConstraint();
//		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
//		stdDevTypeConstraint.addString(STD_DEV_TYPE_BASEMENT);
//		stdDevTypeConstraint.setNonEditable();
//		stdDevTypeParam = new StdDevTypeParam(stdDevTypeConstraint);
		
		magTypeParam = new EnumParameter<MagnitudeType>("Magnitude Type",
			EnumSet.of(MOMENT, LG_PHASE), MOMENT, null);
		
		clampMeanParam = new BooleanParameter("Clamp Mean", true);
		clampStdParam = new BooleanParameter("Clamp Std. Dev.", true);
		
		// add these to the list
		//otherParams.addParameter(stdDevTypeParam);
		otherParams.addParameter(magTypeParam);
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
		} else if (pName.equals(magTypeParam.getName())) {
			magType = magTypeParam.getValue();
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
		magTypeParam.removeParameterChangeListener(this);
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
		magTypeParam.addParameterChangeListener(this);
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

	private double getMean(int iper, SiteType st, double rjb, double mag) {

		double period = perx[iper];

		double t1, t2, t3, t4, t5, t6;
		double thsq, t1h;

		// set coefficients based on magnitude type
		if (magType == MagnitudeType.LG_PHASE) {
			t1 = tb1[iper];
			t2 = tb2[iper];
			t3 = tb3[iper];
			t4 = tb4[iper];
			t5 = tb5[iper];
			t6 = tb6[iper];
			thsq = tbh[iper] * tbh[iper];
			t1h = tb1h[iper];
		} else {
			t1 = tc1[iper];
			t2 = tc2[iper];
			t3 = tc3[iper];
			t4 = tc4[iper];
			t5 = tc5[iper];
			t6 = tc6[iper];
			thsq = th[iper] * th[iper];
			t1h = tc1h[iper];
		}

		// magnitude correction
		// With toro model, you change the coefficients appropriate to the
		// magnitude.
		// New, Nov 2006: the finite-fault correction, affects the
		// fictitious depth or bending point;
		// from Toro Paducah paper. Mod. Dec 2007, mblg to Mw for the
		// correction.
		// * if(mlg) then
		double mCorr;
		if (magType == MagnitudeType.LG_PHASE) {
			double mag1 = Utils.mblgToMw(FaultCode.M_CONV_J, mag);
			double cor1 = Math.exp(-1.25 + 0.227 * mag1);
			double mag2 = Utils.mblgToMw(FaultCode.M_CONV_AB, mag);
			double cor2 = Math.exp(-1.25 + 0.227 * mag2);
			mCorr = Math.sqrt(cor1 * cor2); // geo mean
		} else {
			mCorr = Math.exp(-1.25 + 0.227 * mag);
		}

		double corsq = mCorr * mCorr;
		double dist0 = rjb;
		double dist = Math.sqrt(dist0 * dist0 + thsq * corsq);

		// default to SOFT_ROCK values
		double gnd0 = (st == HARD_ROCK) ? t1h : t1;
		double gndm = gnd0 + t2 * (mag - 6.0) + t3 *
			((mag - 6.0) * (mag - 6.0));
		double gnd = gndm - t4 * Math.log(dist) - t6 * dist;

		double factor = Math.log(dist / 100.0);
		if (factor > 0) gnd = gnd - (t5 - t4) * factor;

		// clip gnd motions: 1.5g PGA, 3.0g 0.3s and 0.2s
		// TODO to ERF
		if (clampMean) gnd = Utils.ceusMeanClip(period, gnd);

		return gnd;
	}

	private double getStdDev(int iper) {
		return tsigma[iper];
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls) {
		return Utils.getExceedProbabilities(imls, getMean(), getStdDev(),
			clampStd, clamp[iper]);
	}

	// temp globals
//	private double[] dtor = null;
//	private double ntor;
//	private double[][][] wt = null; // distance weights
//	private double[][] wtdist = null;
//	private double[] nlev = null; // iml count
//	private double[][] xlev = null; // imls for each of up to 8 periods
//	private double pr[][][][][][] = null;

	// double[][][] e0_ceus = null;

	// cccccccccccccccc
//	public void getToro(int ip, int iq, int ir, int ia, int ndist, int di,
//			int nmag, double magmin, double dmag, double sigmanf, double distnf) {
//
//		try {
//			// ip = global period
//			// iq = local period
//			// ir = soft/hard rock
//
//			// c adapted to NGA code SH July 2006. 7 periods used in2002. With
//			// finite-flt corr,
//			// c added Nov 17 2006.
//			// c Period indexes ip = counter index
//			// c iq = location of the period in the perx() array.
//			// c tc coefficients correspond to Mw.
//			// c tb coeffs. correspond to MbLg (CEUS agrid often written wrt
//			// MbLg).
//			// c ir=1 use BC rock; ir=2 use hardrock model (6000 ft/s according
//			// to SRL).
//			// c Hard-rock in tb1h & tc1h, otherwise same regression model &
//			// coeffs.
//			// c Added 0.4 and 0.04 s July 16 2008 for NRC work
//			// c Coeffs for several other periods are available in the SRL
//			// report, 1997.
//			// c clamp on upper-bound ground accel is applied here. As in
//			// original hazgridX code.
//			// * parameter (sqrt2=1.414213562, pi=3.141592654,np=9)
//			// * logical mlg,et,deagg,sp !sp = short period but not pga?
//			boolean et, deagg = false, sp; // mlg
//			// *common/deagg/deagg
//			// boolean deagg; // (global)
//			// *common/depth_rup/ntor,dtor(3),wtor(3),wtor65(3)
//			// num top rup, depth tor, wt tor
//
//			// * real magmin,dmag,sigmanf,distnf,gndm,gnd,cor,corsq
//			// * real, dimension(np):: perx(np),tc1,tc2,tc3,tc4,tc5,tc6
//			// *real, dimension(np):: tc1h,th,tsigma,clamp
//			// * real, dimension(np):: tb1,tb2,tb3,tb4,tb5,tb6
//			// *real, dimension(np):: tb1h,tbh
//			// * real ptail(6)
//			// * common / atten / pr, xlev, nlev, iconv, wt,wtdist
//
//			// c e0_ceus not saving a depth of rupture dim, not sens. to this
//			// *common/e0_ceus/e0_ceus(260,31,8)
//			// dimension
//			// pr(260,38,20,8,3,3),xlev(20,8),nlev(8),iconv(8,8),wt(8,8,2),wtdist(8,8)
//
//			double sigmat = tsigma[iq]; // !already performed this: /0.4342945
//			// c set up erf matrix p as ftn of dist,mag,period,level,flt
//			// type,atten type
//			// c assume using mblg if iconv()=0.
//			double period = perx[iq];
//
//			// required internal fields;
//			double t1, t2, t3, t4, t5, t6;
//			double thsq;
//			double t1h;
//			double gnd0, gndm, gnd;
//			double xmag, xmag0, xmag1, xmag2;
//			double cor, cor1, cor2;
//
//			// mag conversion
//			// *if(iconv(ip,ia).eq.0)then
//			if (magType == MagnitudeType.LG_PHASE) {
//				t1 = tb1[iq];
//				t2 = tb2[iq];
//				t3 = tb3[iq];
//				t4 = tb4[iq];
//				t5 = tb5[iq];
//				t6 = tb6[iq];
//				// * thsq=tbh[iq]**2; t1h=tb1h[iq]
//				thsq = tbh[iq] * tbh[iq];
//				t1h = tb1h[iq];
//				// write(6,*)'Toro relation using MbLg coefficients'
//				// mlg = true;
//			} else {
//				t1 = tc1[iq];
//				t2 = tc2[iq];
//				t3 = tc3[iq];
//				t4 = tc4[iq];
//				t5 = tc5[iq];
//				t6 = tc6[iq];
//				// thsq=th[iq]**2; t1h = tc1h[iq]
//				thsq = th[iq] * th[iq];
//				t1h = tc1h[iq];
//				// write(6,*)'Toro relation using Mw coefficients'
//				// mlg = false;
//			}
//
//			// sp=perx[iq] .gt. 0.02 .and. perx[iq] .lt. 0.5
//			sp = perx[iq] > 0.02 && perx[iq] < 0.5;
//
//			if (ir == 1) {
//				gnd0 = t1;
//			} else {
//				// c hard rock. Could have other possibilities as well.
//				gnd0 = t1h;
//			}
//			// c-- loop through magnitudes
//
//			// * do 104 m=1,nmag
//			for (int m = 0; m <= nmag; m++) { // mag loop
//				xmag0 = magmin + (m - 1) * dmag;
//				xmag = xmag0;
//				// c With toro model, you change the coefficients appropriate to
//				// the magnitude.
//				// c New, Nov 2006: the finite-fault correction, affects the
//				// fictitious depth or bending point
//				// c from Toro Paducah paper. Mod. Dec 2007, mblg to Mw for the
//				// correction.
//				// * if(mlg) then
//				if (magType == MagnitudeType.LG_PHASE) {
//					xmag1 = Utils.mblgToMw(FaultCode.M_CONV_J, xmag0);
//					cor1 = Math.exp(-1.25 + 0.227 * xmag1);
//					xmag2 = Utils.mblgToMw(FaultCode.M_CONV_BA, xmag0);
//					cor2 = Math.exp(-1.25 + 0.227 * xmag2);
//					cor = Math.sqrt(cor1 * cor2); // !geo mean
//				} else {
//					cor = Math.exp(-1.25 + 0.227 * xmag);
//				}
//
//				// gndm= gnd0+t2*(xmag-6.)+ t3*((xmag-6.)**2)
//				gndm = gnd0 + t2 * (xmag - 6.) + t3 *
//					((xmag - 6.) * (xmag - 6.));
//				// c New, Nov 2006: the finite-fault correction, affects the
//				// fictitious depth or bending point
//				// c from Toro Paducah paper.
//				double corsq = cor * cor;
//				// c Formality, loop through depth of top of rupture. TORO: No
//				// sens. to this param.
//				// *do 103 kk=1,ntor
//				for (int kk = 1; kk <= ntor; kk++) { // tor loop
//
//					// not referenced
//					// double hsq = dtor[kk] * dtor[kk];
//
//					et = deagg && (kk == 1);
//					// c-- loop through distances
//					// * do 103 ii=1,ndist
//					for (int ii = 1; ii <= ndist; ii++) { // distance loop
//						double dist0 = (ii - 0.5) * di;
//						double weight = wt[ip][ia][1];
//						if (dist0 > wtdist[ip][ia]) weight = wt[ip][ia][2];
//						// c note, corsq for finite fault corr in toro relation
//						double dist = Math.sqrt(dist0 * dist0 + thsq * corsq);
//						// c-- gnd for SS,etc; mech dependence not specified in
//						// Toro ea.
//						double sigma;
//						if (dist0 < distnf) { // then
//							sigma = sigmat + sigmanf;
//						} else {
//							sigma = sigmat;
//						}
//						double sigmasq = sigma * SQRT_2;
//						gnd = gndm - t4 * Math.log(dist) - t6 * dist;
//						double factor = Math.log(dist / 100.0);
//						if (factor > 0) gnd = gnd - (t5 - t4) * factor;
//						// c---following is for clipping gnd motions: 1.5g PGA,
//						// 3.0g 0.3 and 0.2 s
//						if (period == 0) { // then
//							gnd = Math.min(0.405, gnd);
//							// * elseif(sp)then
//						} else if (sp) {
//							gnd = Math.min(gnd, 1.099);
//						}
//
//						double test0 = gnd + 3. * sigmasq / SQRT_2;
//						double test = Math.exp(test0);
//
//						double clamp2;
//						if (clamp[iq] < test && clamp[iq] > 0.) {
//							clamp2 = Math.log(clamp[iq]);
//						} else {
//							clamp2 = test0;
//						}
//						double tempgt3 = (gnd - clamp2) / sigmasq;
//						double probgt3 = (Erf.erf(tempgt3) + 1.) * .5;
//						// do 102 k=1,nlev(ip)
//						for (int k = 1; k <= nlev[ip]; k++) { //
//							double temp = (gnd - xlev[k][ip]) / sigmasq;
//							double temp1 = (Erf.erf(temp) + 1.) * .5;
//							temp1 = (temp1 - probgt3) / (1. - probgt3);
//							if (temp1 < 0) continue; // goto 103
//							double fac = weight * temp1;
//							pr[ii][m][k][ip][kk][1] = pr[ii][m][k][ip][kk][1] +
//								fac;
//							// eastern deagg??
//							// if (et)
//							// e0_ceus[ii][m][ip] = e0_ceus[ii][m][ip] -
//							// SQRT_2 * temp * fac;
//
//						} // 102 continue
//					} // 103 continue
//				} // 103 continue
//			} // 104 continue
//
//			// 103 continue
//			// 104 continue
//			// return
//			// end subroutine getToro
//		} catch (MathException me) {
//			me.printStackTrace();
//		}
//	}

	public static void main(String[] args) {
		
	}
	
}
