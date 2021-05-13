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
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;

import com.google.common.collect.Maps;

/**
 * Implementation of the hard rock attenuation relationship for the Central and
 * Eastern US by Silva et al. (2002). This implementation matches that used in
 * the 2008 USGS NSHMP.<br />
 * <br />
 * See: Silva, W., Gregor, N., and Darragh, R., 2002, Development of hard rock
 * attenuation relations for central and eastern North America, internal report
 * from Pacific Engineering, Novem- ber 1, 2002,
 * http://www.pacificengineering.org/CEUS/
 * Development%20of%20Regional%20Hard_ABC.pdf<br />
 * 
 * @author Peter Powers
 * @version $Id$
 */
public class SilvaEtAl_2002_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	public final static String SHORT_NAME = "SilvaEtAl2002";
	private static final long serialVersionUID = 1234567890987654353L;
	public final static String NAME = "Silva et al. (2002)";

	// coefficients:
	// @formatter:off
	private double[] pd = { 0.0, 0.04, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 1.0, 2.0, 5.0 };
	private double[] c1hr = { 5.53459, 6.81012, 6.63937, 5.43782, 3.71953, 2.60689, 1.64228, 0.69539, -2.89906, -7.42051, -13.69697 };
	// c1 from c1hr using A->BC factors, 1.74 for 0.1s, 1.72 for 0.3s, 1.58 for 0.5s, and 1.20 for 2s
	// this from A Frankel advice, Mar 14 2007. For 25 hz use PGA amp.
	// For BC at 2.5 hz use interp between .3 and .5. 1.64116 whose log is 0.4953
	private double[] c1 = { 5.9533, 7.2288, 7.023, 5.9917, 4.2848, 3.14919, 2.13759, 1.15279, -2.60639, -7.23821, -13.39 };
	private double[] c2 = { -0.11691, -0.13594, -0.12193, -0.02059, 0.12490, 0.23165, 0.34751, 0.45254, 0.88116, 1.41946, 2.03488 };
	private double[] c4 = { 2.9, 3.0, 3.0, 2.9, 2.8, 2.8, 2.8, 2.8, 2.8, 2.7, 2.5 };
	private double[] c6 = { -3.42173, -3.48499, -3.45478, -3.25499, -3.04591, -2.96321, -2.87774, -2.818, -2.58296, -2.26433, -1.91969 };
	private double[] c7 = { 0.26461, .26220, 0.26008, 0.24527, 0.22877, 0.22112, 0.21215, 0.20613, 0.18098, 0.14984, 0.12052 };
	private double[] c10 = { -0.06810, -0.06115, -0.06201, -0.06853, -0.08886, -0.11352, -0.13838, -0.16423, -0.25757, -0.33999, -0.35463 };
	private double[] clamp = { 3.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 0.0, 0.0, 0.0 }; // reviewed apr 10 2008.
	// c note very high sigma for longer period SA:
	private double[] sigma = { 0.8471, 0.8870, 0.8753, 0.8546, 0.8338, 0.8428, 0.8386, 0.8484, .8785, 1.0142, 1.2253 };
	// @formatter:on

	private HashMap<Double, Integer> indexFromPerHashMap;

	private int iper;
	private double rjb, mag;
	private SiteType siteType;
	private boolean clampMean, clampStd;

	// clamping in addition to one-sided 3s; unique to nshmp and hidden
	private BooleanParameter clampMeanParam;
	private BooleanParameter clampStdParam;
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
	public SilvaEtAl_2002_AttenRel(ParameterChangeWarningListener listener) {
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
		if (pName.equals(DistanceJBParameter.NAME)) {
			rjb = ((Double) val).doubleValue();
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
		distanceJBParam.removeParameterChangeListener(this);
		siteTypeParam.removeParameterChangeListener(this);
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
		
//		if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
		double period = pd[iper];

		double c = (st == HARD_ROCK) ? c1hr[iper] : c1[iper];
		double gnd0 = c + c2[iper] * mag + c10[iper] * (mag - 6.0) *
			(mag - 6.0);
		double fac = c6[iper] + c7[iper] * mag;
		double gnd = gnd0 + fac * Math.log(rjb + Math.exp(c4[iper]));

		// TODO to ERF
		if (clampMean) gnd = Utils.ceusMeanClip(period, gnd);

		return gnd;
	}

	private double getStdDev(int iper) {
		return sigma[iper];
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

	// public void getSilva(int ip, int iq, int ir,int ia, int ndist, double di,
	// int nmag, double magmin, double dmag) {
	// // c Inputs: xmag = moment mag, dist = rjb (km), see Nov 1, 2002 doc page
	// 4
	// // c magmin=min mag for array building, nmag= number of mags for
	// aforesaid array
	// // c ip = index of spectral period in the wt() matrix. wt() is the
	// epistemic weight
	// // c previously assigned to this relation.
	// // c iq is index in pd array for current spectral period, iq shuld be in
	// 1 to 8 range.
	// // c ir is hardrock (-1) or BCrock indicator (+1).
	// // c Silva (2002) table 5, p. 13. Single corner, constant stress drop,
	// w/saturation.
	// // c parts of this subroutine are from frankel hazFXv7 code. S Harmsen
	// // c Added 2.5 and 25 hz July 1 2008 (for NRC projects). Add 20 hz, or
	// 0.05 s. july 6
	// // parameter (sqrt2=1.414213562,npmx=11)
	// // c There is a new CEUS document (7/2008) at
	// // c pacificengineering.com. looks different from the 2002 CEUS model.
	// // common/geotec/v30,dbasin
	// // common/depth_rup/ntor,dtor(3),wtor(3),wtor65(3)
	// // common / atten / pr, xlev, nlev, iconv, wt, wtdist
	// // common/deagg/deagg
	// // common/e0_ceus/e0_ceus(260,31,8)
	// // dimension pr(260,38,20,8,3,3),xlev(20,8),nlev(8),iconv(8,8),wt(8,8,2),
	// // + wtdist(8,8)
	// // c m-index of 30 allows 4.5 to 7.5 because of dM/2 shift (4.55 to 7.45
	// by 0.1)
	// // real magmin,dmag,di,fac,fac2,gnd,gnd0,period,test0,test
	// // logical sp,deagg !sp=short period data different clamping (included
	// here)
	// // real, dimension(npmx) ::
	// c1,c1hr,c2,c3,c4,c5,c6,c7,c8,c9,c10,pd,sigma,clamp
	//
	// double[] pd= { 0.0, 0.04,0.05,0.1,0.2,0.3,0.4,0.5,1.0, 2.0, 5.0 };
	// double[] clamp = { 3.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 0.0, 0.0, 0.0
	// }; // reviewed apr 10 2008.
	// double[] c1hr= { 5.53459,6.81012,6.63937,5.43782,3.71953,2.60689,
	// 1.64228,0.69539,-2.89906,-7.42051,-13.69697 };
	// // c c1 from c1hr using A->BC factors, 1.74 for 0.1s, 1.72 for 0.3s, 1.58
	// for 0.5s, and 1.20 for 2s
	// // c this from A Frankel advice, Mar 14 2007. For 25 hz use PGA amp.
	// // c For BC at 2.5 hz use interp between .3 and .5. 1.64116 whose log is
	// 0.4953
	// double[] c1= { 5.9533, 7.2288,7.023,5.9917,4.2848,3.14919,2.13759,
	// 1.15279,-2.60639,-7.23821,-13.39 };
	// double[] c2= {
	// -0.11691,-0.13594,-0.12193,-0.02059,0.12490,0.23165,0.34751, 0.45254,
	// 0.88116,1.41946,2.03488 };
	// double[] c4= { 2.9,3.0,3.0, 2.9,2.8,2.8,2.8,2.8,2.8,2.7,2.5 };
	// double[] c6= {
	// -3.42173,-3.48499,-3.45478,-3.25499,-3.04591,-2.96321,-2.87774,-2.818,-2.58296,-2.26433,-1.91969
	// };
	// double[] c7= {
	// 0.26461,.26220,0.26008,0.24527,0.22877,0.22112,0.21215,0.20613,0.18098,0.14984,0.12052
	// };
	// double[] c10= {
	// -0.06810,-0.06115,-0.06201,-0.06853,-0.08886,-0.11352,-0.13838,-0.16423,-0.25757,-0.33999,-0.35463
	// };
	// // c note very high sigma for longer period SA:
	// double[] sigma= {
	// 0.8471,0.8870,0.8753,0.8546,.8338,0.8428,.8386,0.8484,.8785,1.0142,1.2253
	// };
	//
	// // c clamping to be done in main not in subroutines.
	// double c;
	//
	// if(ir == 1) {
	// c=c1[iq];
	// } else {
	// c=c1hr[iq];
	// }
	// // write(6,*)'getSilva hardrock c1hr coef used.'
	// // endif
	// double period=pd[iq];
	//
	// boolean shortPeriod= period > 0.02 && period < 0.5;
	// // c loop on dtor
	// // write(6,*)period,ntor,ndist,nmag,sp
	//
	// double sig=sigma[iq];
	//
	// double sigmaf= 1.0/(sig*SQRT_2);
	// double sigmasq = sig*SQRT_2;
	// // c mag loop
	// // do 104 m=1,nmag
	//
	// // weight= wt(ip,ia,1)
	//
	// // convert magnitude
	// // xmag0= magmin + (m-1)*dmag
	// double mag;
	// if (magType == LG_PHASE) {
	// mag = Utils.mblgToMw(faultCode, mag);
	// }
	// // if(iconv(ip,ia).eq.0)then
	// // xmag= xmag0
	// // elseif(iconv(ip,ia).eq.1)then
	// // xmag= 1.14 +0.24*xmag0+0.0933*xmag0*xmag0
	// // else
	// // xmag = 2.715 -0.277*xmag0+0.127*xmag0*xmag0
	// // endif
	//
	//
	// double gnd0= c + c2[iq]*mag+ c10[iq]*(mag-6.0)*(mag-6.0);
	// double fac= c6[iq]+c7[iq]*mag;
	// // c loop on epicentral dist
	// // c write(6,*)'xmag, fac, gnd0',xmag,fac,gnd0
	//
	// // distance loop
	// // do 103 ii=1,ndist
	//
	// // rjb=(float(ii)-0.5)*di
	// double rjb;
	//
	// // if(rjb.gt.wtdist(ip,ia)) weight= wt(ip,ia,2)
	// // c this formula uses closest distance to surface proj.
	//
	// double gnd = gnd0+fac*Math.log(rjb+Math.exp(c4[iq]));
	//
	// // c--- modify for possible median clipping
	// // c---following is for clipping gnd motions: 1.5g PGA, 3.75g 0.3, 3.75g
	// 0.2
	//
	// // if(period.eq.0.)then
	// // gnd=min(0.405,gnd)
	// // elseif(sp)then
	// // gnd=min(gnd,1.099)
	// // endif
	// if (clampMean) {
	// if (period == 0) gnd = Math.min(0.405, gnd);
	// if (shortPeriod) gnd = Math.min(gnd, 1.099);
	// }
	//
	// // double test0=gnd + 3.*sig
	// // double test= exp(test0)
	// // if(clamp[iq].lt.test .and. clamp[iq].gt.0.) then
	// // clamp2= alog(clamp[iq])
	// // else
	// // clamp2= test0
	// // endif
	// // tempgt3= (gnd- clamp2)/sigmasq
	// // probgt3= (erf(tempgt3)+1.)*.5
	// // do 102 k=1,nlev(ip)
	// // temp= (gnd- xlev(k,ip))/sigmasq
	// // temp1= (erf(temp)+1.)*.5
	// // temp1= (temp1-probgt3)/(1.-probgt3)
	// // if(temp1.lt.0.) goto 103
	// // fac2=weight*temp1
	// // if(deagg)e0_ceus(ii,m,ip)= e0_ceus(ii,m,ip)-sqrt2*temp*fac2
	// // do 102 kk=1,ntor
	// // c no variation wrt depth of rupture in the Silva model.
	// // c Assume no branching on median motion(last subscr) for eastern US
	// // pr(ii,m,k,ip,kk,1)= pr(ii,m,k,ip,kk,1) + fac2
	// // 102 continue
	// // 103 continue
	// // 104 continue
	// //
	// // return
	// // end subroutine getSilva
	// }

}
