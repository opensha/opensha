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
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;

import com.google.common.collect.Maps;

/**
 * Implementation of the Central and Eastern US attenuation relationship by
 * Tavakoli &amp; Pezeshk (2005). This implementation matches that used in the
 * 2008 USGS NSHMP. TODO NOTE that frankel terms are used for soft rock.<br />
 * <br />
 * See: Tavakoli, B., and Pezeshk, S., 2005, Empirical-stochastic ground-motion
 * prediction for eastern North America: Bulletin of the Seismological Society
 * of America, v. 95, p. 2283–2296.<br />
 * 
 * TODO
 * 		- needs to support Rrup
 * 		- vs30 param, or kill in favor of hard/soft rock options
 * 		  as other ceus att rels
 * 		- rem: 2km gridded dtor min was removed
 * 
 * @author Peter Powers
 * @version $Id$
 */
public class TP2005_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	public final static String SHORT_NAME = "TP2005";
	private static final long serialVersionUID = 1234567890987654353L;
	public final static String NAME = "Tavakoli and Pezeshk (2005)";

	// c1 below is based on a CEUS conversion from c1h where c1h Vs30 is in
	// the NEHRP A range (Vs30 = ??). Frankel dislikes the use of wus
	// siteamp for ceus. So for all periods we use Frankel 1996 terms.
	// c1 modified at 0.1, 0.3, 0.5, and 2.0 s for Frankel ceus amp. mar 19
	// 2007.
	// c1 for 1hz 5hz and pga also use the Fr. CEUS a->bc factors developed
	// in 1996(?).
	// corrected c1(0.3s) to 0.0293 from K Campbell email Oct 13 2009.
	// c1 checked for pga, 1hz and 5hz apr 17 2007. c1(0.4s) added June 30

    // @formatter:off
	private double[] pd =    { 0.0, 0.04, 0.05, 0.08, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0 };
	private double[] clamp = { 3.0, 6.0,  6.0,  6.0,  6.0, 6.0,  6.0, 6.0, 6.0, 6.0, 0.0,  0.0, 0.0, 0.0, 0.0, 0.0 };
	private double[] c1h = { 1.14, 2.2217764, 1.82, 0.683, 0.869, 2.38, -0.548, -0.513, 0.18295555, 0.24, -0.679, -1.55, -2.30, -2.70, -2.42, -3.69 };
	private double[] c1 = { 1.559, 2.6819708, 2.24, 1.10, 1.4229, 2.87, 0.0170, 0.0293, 0.9333664, 0.6974, -0.323, -1.257, -1.94, -2.5177, -2.28, -2.28 };
	private double[] c2 = { 0.623, 0.42201685, 0.533, 0.743, 0.607, 0.501, 0.857, 0.667, 0.5887256, 0.611, 0.666, 0.764, 0.794, 0.805, 0.801, 0.817 };
	private double[] c3 = { -0.0483, -0.05892166, -0.0475, -0.0293, -0.0474, -0.0642, -0.0262, -0.0443, -0.068179324, -0.0789, -0.0830, -0.0859, -0.0884, -0.0929, -0.108, -0.118 };
	private double[] c4 = { -1.81, -1.5474958, -1.63, -1.71, -1.52, -1.73, -1.68, -1.42, -1.4782926, -1.55, -1.48, -1.49, -1.45, -1.44, -1.65, -1.46 };
	private double[] c5 = { -0.652, -0.46962538, -0.567, -0.756, -0.704, -0.976, -0.861, -0.470, -0.6740682, -0.844, -0.734, -0.941, -0.886, -0.923, -0.898, -0.845 };
	private double[] c6 = { 0.446, 0.44941282, 0.454, 0.460, 0.449, 0.414, 0.433, 0.468, 0.4376, 0.43636485, 0.435, 0.424, 0.412, 0.408, 0.437, 0.425 };
	private double[] c7 = { -2.93E-5, 9.11331E-3, 7.77E-3, -9.68E-4, -6.19E-3, 6.60E-3, 2.79E-3, 1.08E-2, 9.212394E-3, 7.89E-3, 9.53E-3, -5.84E-3, 8.30E-3, 2.06E-2, 1.67E-2, 1.13E-2 };
	private double[] c8 = { -4.05E-3, -4.7678155E-3, -4.91E-3, -4.94E-3, -4.70E-3, -4.80E-3, -3.65E-3, -5.41E-3, -4.63148E-3, -3.65E-3, -3.37E-3, -2.09E-3, -3.27E-3, -2.14E-3, -2.03E-3, -1.72E-3 };
	private double[] c9 = { 9.46E-3, -1.5620958E-3, -3.14E-3, -5.50E-3, -4.24E-3, 3.93E-3, -2.02E-3, 6.44E-3, 4.467385E-3, -2.65E-4, -1.19E-3, 3.30E-3, 2.51E-3, 2.30E-3, 3.58E-3, -3.34E-3 };
	private double[] c10 = { 1.41, 0.90152883, 0.980, 1.13, 1.04, 1.51, 1.64, 1.52, 1.543476, 1.59, 1.55, 1.52, 1.71, 1.43, 1.93, 1.69 };
	private double[] c11 = { -0.961, -0.95111495, -0.939, -0.916, -0.913, -0.865, -0.925, -0.915, -0.8872426, -0.859, -0.784, -0.757, -0.769, -0.755, -0.818, -0.737 };
	private double[] c12 = { 4.32E-4, 4.995133E-4, 5.12E-4, 4.82E-4, 4.11E-4, 3.64E-4, 1.61E-4, 4.32E-4, 3.839152E-4, 2.77E-4, 2.45E-4, 1.17E-4, 2.33E-4, 2.14E-4, 1.16E-4, 1.10E-4 };
	private double[] c13 = { 1.33E-4, 8.605951E-4, 9.30E-4, 7.33E-4, 3.58E-4, 6.84E-4, 6.43E-4, 2.87E-4, 1.2648004E-4, 1.46E-4, 5.47E-4, 7.59E-4, 1.66E-4, 3.91E-4, 3.98E-4, 3.59E-4 };
	private double[] c14 = { 1.21, 1.221863, 1.22, 1.22, 1.23, 1.24, 1.24, 1.26, 1.2742121, 1.28, 1.28, 1.28, 1.27, 1.26, 1.26, 1.25 };
	// c for c15, corrected value for the 0.5-s or 2 Hz motion, from email Pezeshk dec 7 2007
	private double[] c15 = { -0.111, -0.10814471, -0.108, -0.108, -0.108, -0.108, -0.108, -0.109, -0.10839832, -0.1073, -0.105, -0.103, -0.0999, -0.0978, -0.0952, -0.0926 };
	private double[] c16 = { 0.409, 0.43780863, 0.441, 0.449, 0.456, 0.464, 0.469, 0.479, 0.49330785, 0.505, 0.522, 0.537, 0.551, 0.562, 0.573, 0.589 };       
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
	public TP2005_AttenRel(ParameterChangeWarningListener listener) {
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
		// updates intensityMeasureChanged
		if (intensityMeasureChanged) setCoeffIndex();
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
//		stdDevIndependentParams.addParameter(distanceRupParam);
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

//	double vs30;
	
	private double getMean(int iper, SiteType st, double rRup, double mag) {

		double vs30 = (st == HARD_ROCK) ? 2000 : 760;
		
		double period = pd[iper];
		//boolean sp = period < 0.5 && period > 0.02;
		double c5sq = c5[iper] * c5[iper];

		// c R: For near-surface dtor a singularity is possible. Limit at 2 km
		// minimum.
		// NOTE I do not think this is important; singularity would require
		// a site exactly on a fault trace; this situation is not covered in
		// hazFX
		// double H1= Math.max(dtor[kk],2.0);
		// double H1sq=H1*H1;
		// above is now handled by reading rRup (and dtor for gridded)

//		if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
		//vs30 = 760;
		double f1;
		if (vs30 >= 1500.0) {
			f1 = c1h[iper] + c2[iper] * mag + c3[iper] *
				Math.pow((8.5 - mag), 2.5);
		} else if (vs30 > 900.0) {
			f1 = 0.5 * (c1h[iper] + c1[iper]) + c2[iper] * mag + c3[iper] *
				Math.pow((8.5 - mag), 2.5);
		} else {
			f1 = c1[iper] + c2[iper] * mag + c3[iper] *
				Math.pow((8.5 - mag), 2.5);
		}
		double cor = Math.exp(c6[iper] * mag + c7[iper] *
			Math.pow((8.5 - mag), 2.5));
		
//		System.out.println("cor: " + cor);
		double corsq = cor * cor;

		double f2 = c9[iper] * Math.log(rRup + 4.5);
//		System.out.println("c9: " + c9[iper] + " rRup: " + rRup);
		if (rRup > 70.0) f2 = f2 + c10[iper] * Math.log(rRup / 70.0);
		if (rRup > 130.0) f2 = f2 + c11[iper] * Math.log(rRup / 130.0);
		double R = Math.sqrt(rRup * rRup + c5sq * corsq);
		double f3 = (c4[iper] + c13[iper] * mag) * Math.log(R) +
			(c8[iper] + c12[iper] * mag) * R;
		double gnd = f1 + f2 + f3;
		if (clampMean) gnd = Utils.ceusMeanClip(period, gnd);

		return gnd;
	}

	private double getStdDev(int iper, double mag) {
//		if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
        return (mag < 7.2) ? c14[iper] + c15[iper]*mag : c16[iper];
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls) {
		return Utils.getExceedProbabilities(imls, getMean(), getStdDev(),
			clampStd, clamp[iper]);
	}

	// temp globals
	// private double[] dtor = null;
	// private double ntor;
	// private double[][][] wt = null; // distance weights
	// private double[][] wtdist = null;
	// private double[] nlev = null; // iml count
	// private double[][] xlev = null; // imls for each of up to 8 periods
	// private double pr[][][][][][] = null;
	//
	// private double[][][] e0_ceus = null;

//	public void getTP05(int ip, int iq, int ia, int ndist, double di, int nmag, double magmin, double dmag) {
////    c for what range of vs30 is this one supposed to be valid? SH nov 13 2006
////    c Coeff c1 has a hard-rock version which is used if v30>1500 m/s. Is this a
////    c good boundary? For 900 to 1500 m/s, a middle value. Then a BC value.
////    c cj(0.4s) interpolated from (ln f, a(f)) 
////    c ditto for 0.04s. Use cubic splines to interpolate (pgm intTP05.f)
////          parameter (np=16,emax=3.0,sqrt2=1.414213562)
////    	common/geotec/v30,dbasin
////    	common/depth_rup/ntor,dtor(3),wtor(3),wtor65(3)
////           common / atten / pr, xlev, nlev, iconv, wt, wtdist
////    	common/deagg/deagg
////    	common/e0_ceus/e0_ceus(260,31,8)
////    	dimension pr(260,38,20,8,3,3),xlev(20,8),nlev(8),iconv(8,8),wt(8,8,2),
////         +  wtdist(8,8) 
//		double v30;
////          double f1,f2,f3,R,Rrup,cor; //,Mw,period; //,magmin,dmag;
////          double  corsq; //,H1sq; //c5sq,
////          boolean et,deagg; //,sp; //	!for a short-period gm limit in ceus. 
////          real,dimension(np):: Pd,c1,c1h,c2,c3,c4,c5,c6,c7,c8,c9,c10,c11,c12,c13,c14,
////         + c15,c16,clamp
//          
//
//		// c1 below is based on a CEUS conversion from c1h where c1h Vs30 is in
//		// the NEHRP A range (Vs30 = ??). Frankel dislikes the use of wus
//		// siteamp for ceus. So for all periods we use Frankel 1996 terms.
//		// c1 modified at 0.1, 0.3, 0.5, and 2.0 s for Frankel ceus amp. mar 19
//		// 2007.
//		// c1 for 1hz 5hz and pga also use the Fr. CEUS a->bc factors developed
//		// in 1996(?).
//		// corrected c1(0.3s) to 0.0293 from K Campbell email Oct 13 2009.
//		// c1 checked for pga, 1hz and 5hz apr 17 2007. c1(0.4s) added June 30
//
//        // @formatter:off
//		double[] Pd =    { 0.0, 0.04, 0.05, 0.08, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0 };
//		double[] clamp = { 3.0, 6.0,  6.0,  6.0,  6.0, 6.0,  6.0, 6.0, 6.0, 6.0, 0.0,  0.0, 0.0, 0.0, 0.0, 0.0 };
//		double[] c1h = { 1.14, 2.2217764, 1.82, 0.683, 0.869, 2.38, -0.548, -0.513, 0.18295555, 0.24, -0.679, -1.55, -2.30, -2.70, -2.42, -3.69 };
//		double[] c1 = { 1.559, 2.6819708, 2.24, 1.10, 1.4229, 2.87, 0.0170, 0.0293, 0.9333664, 0.6974, -0.323, -1.257, -1.94, -2.5177, -2.28, -2.28 };
//		double[] c2 = { 0.623, 0.42201685, 0.533, 0.743, 0.607, 0.501, 0.857, 0.667, 0.5887256, 0.611, 0.666, 0.764, 0.794, 0.805, 0.801, 0.817 };
//		double[] c3 = { -0.0483, -0.05892166, -0.0475, -0.0293, -0.0474, -0.0642, -0.0262, -0.0443, -0.068179324, -0.0789, -0.0830, -0.0859, -0.0884, -0.0929, -0.108, -0.118 };
//		double[] c4 = { -1.81, -1.5474958, -1.63, -1.71, -1.52, -1.73, -1.68, -1.42, -1.4782926, -1.55, -1.48, -1.49, -1.45, -1.44, -1.65, -1.46 };
//		double[] c5 = { -0.652, -0.46962538, -0.567, -0.756, -0.704, -0.976, -0.861, -0.470, -0.6740682, -0.844, -0.734, -0.941, -0.886, -0.923, -0.898, -0.845 };
//		double[] c6 = { 0.446, 0.44941282, 0.454, 0.460, 0.449, 0.414, 0.433, 0.468, 0.4376, 0.43636485, 0.435, 0.424, 0.412, 0.408, 0.437, 0.425 };
//		double[] c7 = { -2.93E-5, 9.11331E-3, 7.77E-3, -9.68E-4, -6.19E-3, 6.60E-3, 2.79E-3, 1.08E-2, 9.212394E-3, 7.89E-3, 9.53E-3, -5.84E-3, 8.30E-3, 2.06E-2, 1.67E-2, 1.13E-2 };
//		double[] c8 = { -4.05E-3, -4.7678155E-3, -4.91E-3, -4.94E-3, -4.70E-3, -4.80E-3, -3.65E-3, -5.41E-3, -4.63148E-3, -3.65E-3, -3.37E-3, -2.09E-3, -3.27E-3, -2.14E-3, -2.03E-3, -1.72E-3 };
//		double[] c9 = { 9.46E-3, -1.5620958E-3, -3.14E-3, -5.50E-3, -4.24E-3, 3.93E-3, -2.02E-3, 6.44E-3, 4.467385E-3, -2.65E-4, -1.19E-3, 3.30E-3, 2.51E-3, 2.30E-3, 3.58E-3, -3.34E-3 };
//		double[] c10 = { 1.41, 0.90152883, 0.980, 1.13, 1.04, 1.51, 1.64, 1.52, 1.543476, 1.59, 1.55, 1.52, 1.71, 1.43, 1.93, 1.69 };
//		double[] c11 = { -0.961, -0.95111495, -0.939, -0.916, -0.913, -0.865, -0.925, -0.915, -0.8872426, -0.859, -0.784, -0.757, -0.769, -0.755, -0.818, -0.737 };
//		double[] c12 = { 4.32E-4, 4.995133E-4, 5.12E-4, 4.82E-4, 4.11E-4, 3.64E-4, 1.61E-4, 4.32E-4, 3.839152E-4, 2.77E-4, 2.45E-4, 1.17E-4, 2.33E-4, 2.14E-4, 1.16E-4, 1.10E-4 };
//		double[] c13 = { 1.33E-4, 8.605951E-4, 9.30E-4, 7.33E-4, 3.58E-4, 6.84E-4, 6.43E-4, 2.87E-4, 1.2648004E-4, 1.46E-4, 5.47E-4, 7.59E-4, 1.66E-4, 3.91E-4, 3.98E-4, 3.59E-4 };
//		double[] c14 = { 1.21, 1.221863, 1.22, 1.22, 1.23, 1.24, 1.24, 1.26, 1.2742121, 1.28, 1.28, 1.28, 1.27, 1.26, 1.26, 1.25 };
//		// c for c15, corrected value for the 0.5-s or 2 Hz motion, from email Pezeshk dec 7 2007
//		double[] c15 = { -0.111, -0.10814471, -0.108, -0.108, -0.108, -0.108, -0.108, -0.109, -0.10839832, -0.1073, -0.105, -0.103, -0.0999, -0.0978, -0.0952, -0.0926 };
//		double[] c16 = { 0.409, 0.43780863, 0.441, 0.449, 0.456, 0.464, 0.469, 0.479, 0.49330785, 0.505, 0.522, 0.537, 0.551, 0.562, 0.573, 0.589 };       
//        // @formatter:on
// 
////    cc loop on dtor
////    	write(6,*)'entering TP05, period ',Pd[iq],ndist,nmag
//    	double period = Pd[iq];
//    	boolean sp = period < 0.5 && period > 0.02;
//    	double c5sq=c5[iq]*c5[iq];
//    	
////    	do 104 kk=1,ntor
//    	
////    c R: For near-surface dtor a singularity is possible. Limit at 2 km minimum.
//    	double H1= Math.max(dtor[kk],2.0);
//    	double H1sq=H1*H1;
//    	boolean et = kk==1 && deagg;
////    c mag loop
////            do 104 m=1,nmag
////            weight= wt(ip,ia,1)
////                 double xmag0= magmin + (m-1)*dmag
//                 
//        double xmag0 = mag;
//        if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
//        double Mw = mag;
////    	if(iconv(ip,ia).eq.0)then
////            Mw= xmag0
////            elseif(iconv(ip,ia).eq.1)then
////             Mw= 1.14 +0.24*xmag0+0.0933*xmag0*xmag0
////             else
////            Mw = 2.715 -0.277*xmag0+0.127*xmag0*xmag0
////            endif
////          if (Mw.lt.7.2) then
////            sigma = c14[iq] + c15[iq]*Mw 
////          else
////            sigma = c16[iq]
////          endif
//          double sigma = (Mw < 7.2) ? c14[iq] + c15[iq]*Mw : c16[iq];
//          
//    	double sigmasq=sigma*SQRT_2;
//        double  sigmaf = 1.0/sigmasq;
////    c possible hardrock factor versus BC rock below:
//        
//        double f1;
//          if (v30 >= 1500.0) {
////    c hard rock NEHRP A
//           f1 = c1h[iq] + c2[iq]*Mw + c3[iq]*Math.pow((8.5 - Mw), 2.5);
////            if(m.eq.1)print *,'TP relation with c1h = ',c1h[iq]
//          } else if (v30 > 900.0) {
////    c add intermediate range B rock coeff, jul 1 2008, 900 to 1500 m/s.
//            f1=0.5*(c1h[iq]+c1[iq])+ c2[iq]*Mw + c3[iq]*Math.pow((8.5 - Mw), 2.5);
////            if(m.eq.1)print *,'TP relation with c1havg = ',0.5*(c1h[iq]+c1[iq])
//          } else {
//            f1 = c1[iq] + c2[iq]*Mw + c3[iq]*Math.pow((8.5 - Mw), 2.5);
////            if(m.eq.1)print *,'TP relation with c1 = ',c1[iq]
//          }
//          double cor = Math.exp(c6[iq]*Mw + c7[iq]*Math.pow((8.5 - Mw), 2.5));
//          double corsq=cor*cor;
////    c loop on epicentral dist
////          do 103 ir=1,ndist
////          rjb=(float(ir)-0.5)*di
//          double rjb;
////          if (rjb.gt.wtdist(ip,ia)) weight= wt(ip,ia,2)
//          double Rrup=Math.sqrt(rjb*rjb+H1sq);
//          
//          double f2 = c9[iq]*Math.log(Rrup + 4.5);
//          if (Rrup > 70.0) f2 = f2 + c10[iq]*Math.log(Rrup/70.0);
//          if (Rrup > 130.0) f2 = f2 + c11[iq]*Math.log(Rrup/130.0);
//          double R = Math.sqrt(Rrup*Rrup + c5sq*corsq);
//          double f3 = (c4[iq] + c13[iq]*Mw)*Math.log(R) + (c8[iq] + c12[iq]*Mw)*R;
//          double gnd = f1 + f2 + f3;
//          
////    c---following is for clipping gnd motions: 1.5g PGA, 3.75g 0.3, 3.75g 0.2 
//  		if (clampMean) gnd = Utils.ceusMeanClip(period, gnd);
//
////              if(period.eq.0.)then
////               gnd=min(0.405,gnd)
////               elseif(sp)then
////               gnd=min(gnd,1.099)
////               endif
////
////  		t0=gnd + 3*sigma
////          test= exp(t0)
//////    c      if(m.eq.10)write(6,*) Rrup,exp(gnd),sigmaf,Mw,R,test
////          if((clamp[iq].lt.test).and.(clamp[iq].gt.0.))then
////           clamp2= alog(clamp[iq])
////          else
////           clamp2= t0
////          endif
////          tempgt3= (gnd- clamp2)*sigmaf
////          probgt3= (erf(tempgt3)+1.)*0.5
////          prr= 1.0/(1.-probgt3)
////          do 102 k=1,nlev(ip)
////          temp= (gnd- xlev(k,ip))*sigmaf
////          temp1= (erf(temp)+1.)*0.5
////          temp1= (temp1-probgt3)*prr
////          if(temp1.lt.0.) goto 103
////          fac=weight*temp1
////          pr(ir,m,k,ip,kk,1)= pr(ir,m,k,ip,kk,1) + fac
////    	if(et)e0_ceus(ii,m,ip)= e0_ceus(ii,m,ip)-sqrt2*temp*fac
////      102  continue
////      103 continue	!dist loop
////      104 continue	!mag & dtor loops
////          return
//}
	// end subroutine getTP05

}
