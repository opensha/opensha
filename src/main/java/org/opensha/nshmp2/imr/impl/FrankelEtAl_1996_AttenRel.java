package org.opensha.nshmp2.imr.impl;

import static org.opensha.nshmp2.util.FaultCode.*;
import static org.opensha.nshmp2.util.GaussTruncation.*;
import static org.opensha.nshmp2.util.SiteType.*;
import static org.opensha.nshmp2.util.Utils.SQRT_2;
import static org.opensha.commons.eq.cat.util.MagnitudeType.*;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.special.Erf;
import org.apache.commons.math3.util.MathUtils;
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

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;

/**
 * Implementation of the attenuation relationship for the Central and Eastern US
 * by Frankel et al. (1996). This implementation matches that used in the 2008
 * USGS NSHMP.<br />
 * <br />
 * See: Frankel, A., Mueller, C., Barnhard, T., Perkins, D., Leyendecker, E.,
 * Dickman, N., Hanson, S., and Hopper, M., 1996, National Seismic Hazard
 * Maps—Documentation June 1996: U.S. Geological Survey Open-File Report 96–532,
 * 110 p.<br />
 * 
 * @author Peter Powers
 * @version $Id$
 */
public class FrankelEtAl_1996_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	public final static String SHORT_NAME = "FrankelEtAl1996";
	private static final long serialVersionUID = 1234567890987654353L;
	public final static String NAME = "Frankel et al. (1996)";

	private List<GM_Table> gmTablesSoft;
	private List<GM_Table> gmTablesHard;

	// coefficients:
	// @formatter:off
	double[] pd = { 0.0, 0.2, 1.0, 0.1, 0.3, 0.5, 2.0 };
	double[] bsigma = { 0.326, 0.326, 0.347, 0.326, 0.326, 0.326, 0.347 };
	double[] clamp = { 3.0, 6.0, 0.0, 6.0, 6.0, 6.0, 0.0 };
	String[] srTableNames = { "pgak01l.tbl", "t0p2k01l.tbl", "t1p0k01l.tbl", "t0p1k01l.tbl", "t0p3k01l.tbl", "t0p5k01l.tbl", "t2p0k01l.tbl" };
	String[] hrTableNames = { "pgak006.tbl", "t0p2k006.tbl", "t1p0k006.tbl", "t0p1k006.tbl", "t0p3k006.tbl", "t0p5k006.tbl", "t2p0k006.tbl" };
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
	public FrankelEtAl_1996_AttenRel(ParameterChangeWarningListener listener) {
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

		// init ground motion lookup tables
		gmTablesSoft = Lists.newArrayList();
		gmTablesHard = Lists.newArrayList();
		try {
			for (int i = 0; i < pd.length; i++) {
				URL fSoft = Utils.getResource("/imr/" + srTableNames[i]);
				gmTablesSoft.add(new GM_Table(fSoft));
				URL fHard = Utils.getResource("/imr/" + hrTableNames[i]);
				gmTablesHard.add(new GM_Table(fHard));
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		
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
		return getStdDev(iper);
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
		// stdDevIndependentParams.addParameter(magParam);
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
		distanceRupParam.addParameterChangeWarningListener(warningListener);
		DoubleConstraint warn = new DoubleConstraint(DISTANCE_RUP_WARN_MIN,
			DISTANCE_RUP_WARN_MAX);
		warn.setNonEditable();
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
		GM_Table table = (st == HARD_ROCK) ? gmTablesHard.get(iper)
			: gmTablesSoft.get(iper);
		// File f = Utils.getResource(tableName);
		double gnd = table.get(mag, rRup);
		if (clampMean) gnd = Utils.ceusMeanClip(pd[iper], gnd);
		return gnd;
	}

	private double getStdDev(int iper) {
		return bsigma[iper] * 2.302585093;
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls) {
		return Utils.getExceedProbabilities(imls, getMean(), getStdDev(),
			clampStd, clamp[iper]);
	}

	static class GM_Table {

		static final Range<Double> mRange = Range.closed(4.4, 8.2);
		static final Range<Double> dRange = Range.closed(1.0, 3.0);
		static final int M_IDX = 19;
		static final int D_IDX = 20;

		List<List<Double>> gmTable;

		GM_Table(URL data) throws IOException {
			gmTable = Resources.readLines(data, Charsets.US_ASCII,
				new TableParser());
		}

		/*
		 * Returns the interpolated value from the table that is constrainted to
		 * the table bounds; i.e. arguments are clamped to table min max.
		 */
		double get(double mag, double dist) {
			// int iMag = (int) (10 * mag);
			dist = Math.log10(dist);

			// clamp values to min max
			if (!mRange.contains(mag))
				mag = (mag < mRange.lowerEndpoint()) ? mRange.lowerEndpoint()
					: mRange.upperEndpoint();
			if (!dRange.contains(dist))
				dist = (dist < dRange.lowerEndpoint()) ? dRange.lowerEndpoint()
					: dRange.upperEndpoint();

			int mIdx, mIdx1, dIdx1, dIdx;
			double mFrac, dFrac;
			double gm1, gm2, gm3, gm4;

			mIdx = idxForMag(mag);
			dIdx = idxForDist(dist);
			mIdx1 = Math.min(mIdx + 1, M_IDX);
			dIdx1 = Math.min(dIdx + 1, D_IDX);

			mFrac = (mag - magForIdx(mIdx)) / 0.2;
			dFrac = (dist - distForIdx(dIdx)) / 0.1;

			gm1 = gmTable.get(dIdx).get(mIdx);
			gm2 = gmTable.get(dIdx).get(mIdx1);
			gm3 = gmTable.get(dIdx1).get(mIdx);
			gm4 = gmTable.get(dIdx1).get(mIdx1);

			double gmDlo = gm1 + mFrac * (gm2 - gm1);
			double gmDhi = gm3 + mFrac * (gm4 - gm3);
			double gm = gmDlo + dFrac * (gmDhi - gmDlo);

			return gm * Utils.LOG_BASE_10_TO_E;

		}

		private static double magForIdx(int idx) {
			return idx * 0.2 + mRange.lowerEndpoint();
		}

		private static int idxForMag(double mag) {
			// upscaling by *10 eliminates double rounding errors
			return (int) (mag * 10 - mRange.lowerEndpoint() * 10) / 2;
		}

		private static double distForIdx(int idx) {
			return idx * 0.1 + dRange.lowerEndpoint();
		}

		private static int idxForDist(double dist) {
			// upscaling by *10 eliminates double rounding errors
			return (int) (dist * 10 - dRange.lowerEndpoint() * 10);
		}
	}

	//@formatter:off
	static class TableParser implements LineProcessor<List<List<Double>>> {
		boolean firstLine = true;
		List<List<Double>> data;
		TableParser() { data = Lists.newArrayList(); }
		@Override
		public List<List<Double>> getResult() { return data; }
		@Override
		public boolean processLine(String line) throws IOException {
			if (firstLine) {
				firstLine = false;
				return true;
			}
			String[] values = StringUtils.split(line);
			values = ArrayUtils.remove(values, 0);
			data.add(Utils.stringsToDoubles(values));
			//System.out.println(Utils.stringsToDoubles(values));
			return true;
		}
	}
	//@formatter:on

	// void getFEA(int ip, int iq, int ir, int ia, int ndist, double di, int
	// nmag,
	// double magmin, double dmag, double sigmanf, double distnf) {
	// // c example call:
	// // c call getFEA(ip,iq,1,ia,ndist,di,nmag,magmin,dmag,sigmanf,distnf)
	// // c adapted to nga style. This routine is used for background &
	// // charleston.
	// // c
	// // c getFEA used for several HR and firm rock models with look-up
	// // tables.
	// // c input variables:
	// // c ip,iq period indexes. ip is the current index in iatten() array
	// // c iq = is the index in perx() the standard set of SA periods in 2003
	// // PSHA.
	// // c ir = flag to control which interpolation table:
	// // c The table data are input here. File has to reside in subdir called
	// // GR.
	// // c Table data are not saved (new aug 06) use 'em and overwrite 'em.
	// // c ir =1=>Fea BC,
	// // c 2=> AB Bc,
	// // c 3=> Fea A(HR)
	// // c 4=> AB A (HR) tables. Tables currently in a subdirectory.
	// // c Note: depth to rupture is controlled by dtor rather than set to
	// // "bdepth"
	// // c
	// // parameter (np=7,npp=9,sqrt2=1.414213562)
	// // logical deagg,et,sp/.false./ !short-period?
	// // real magmin,perx(9)
	// // common/depth_rup/ntor,dtor(3),wtor(3),wtor65(3)
	// // common / atten / pr, xlev, nlev, iconv, wt, wtdist
	// // common/deagg/deagg
	// // c e0_ceus not saving a depth of rupture dim, although could be sens.
	// // to this.
	// // c FOR CEUS runs 2008, only one depth of rupture is considered.
	// // common/e0_ceus/e0_ceus(260,31,8)
	// // dimension pr(260,38,20,8,3,3),xlev(20,8),nlev(8),iconv(8,8),
	// // + wt(8,8,2),wtdist(8,8)
	// // real bdepth/5./,bsigma(9),clamp(9),xlogfac(7)/7*0./
	// // c bdepth is no longer used. use dtor instead.
	// // c Same sigma for AB94 and FEA. 1s and 2s larger than the rest. As in
	// // Toro ea.
	// // dimension tabdist(21),gma(22,30)
	// // character*30 nametab(np),nameab(np),namehr(np+2),subd*3/'GR/'/
	// // character*30 hardab(np+2)
	// // c Subroutine assumes these files are in working directory:
	// double[] pd = { 0.0, 0.2, 1.0, 0.1, 0.3, 0.5, 2.0, 0.04, 0.4 };
	// // c added 0.04 and 0.4 s for NRC work july 15 2008. hard rock tables
	// // k006
	// double[] bsigma = { 0.326, 0.326, 0.347, 0.326, 0.326, 0.326, 0.347,
	// 0.326, 0.326 };
	// double[] clamp = { 3.0, 6.0, 0.0, 6.0, 6.0, 6.0, 0.0, 6.0, 6.0 };
	// String[] srTables = { "pgak01l.tbl", "t0p2k01l.tbl", "t1p0k01l.tbl",
	// "t0p1k01l.tbl", "t0p3k01l.tbl", "t0p5k01l.tbl", "t2p0k01l.tbl" };
	// String[] hrTables = { "pgak006.tbl ", "t0p2k006.tbl", "t1p0k006.tbl",
	// "t0p1k006.tbl", "t0p3k006.tbl", "t0p5k006.tbl", "t2p0k006.tbl",
	// "tp04k006.tbl", "t0p4k006.tbl" };
	// // c tp04k006 was renamed from t0p04k006.tbl to have fixed length
	// // nameab= (/'Abbc_pga.tbl','Abbc0p20.tbl','Abbc1p00.tbl',
	// // 1 'Abbc0p10.tbl','Abbc0p30.tbl','Abbc0p50.tbl','Abbc2p00.tbl'/)
	// // c added 0.04 and 0.4s to hardab, July 15 2008. SH.
	// // hardab= (/'ABHR_PGA.TBL','ABHR0P20.TBL','ABHR1P00.TBL',
	// // 1'ABHR0P10.TBL','ABHR0P30.TBL','ABHR0P50.TBL','ABHR2P00.TBL',
	// // 2'Abhr0p04.tbl','Abhr0p40.tbl'/)
	// double period = pd[iq];
	// // c write(6,*)ip,nlev(ip),' ip nlev() in getFEA before table fill'
	// double freq;
	// if (period > 0.0) {
	// freq = 1.0 / pd[iq];
	// // sp=freq.gt.2.0
	// } else if (period == 0.0) {
	// freq = 99.0;
	// // sp=.false.
	// } else {
	// // c In nga, negative period implies PGV. However, PGV not available
	// // in tables
	// freq = 1.0; // Flow should not have arrived at this spot.
	// }
	// // c write(6,*) "enter file name of table"
	// // c read(1,900) nametab
	// // 900 format(a)
	// // c write(6,*) "enter depth, sigma, log factor"
	// // c read(1,*) bdepth,bsigma,xlogfac, clamp
	// // if(ir.eq.1)then
	// String tableName = (st == HARD_ROCK) ? hrTables[iq] : srTables[iq];
	// File f = Utils.getResource(tableName);
	// // if (st == HARD_ROCK) {
	// // open(unit=15,file=subd//nametab[iq],status='old',err=234)
	// // elseif(ir.eq.2)then
	// // open(unit=15,file=subd//nameab[iq],status='old',err=236)
	// // elseif(ir.eq.3)then
	// // open(unit=15,file=subd//namehr[iq],status='old',err=237)
	// // elseif(ir.eq.4)then
	// // open(unit=15,file=subd//hardab[iq],status='old',err=238)
	// // else
	// // stop'invalid ir in getFEA'
	// // endif
	// // read(15,900) adum
	//
	// // do 80 idist=1,21
	// // 80 read(15,*) tabdist(idist),(gma(imag,idist),imag=1,20)
	// // close(15)
	// // c---- following for new Boore look-up table
	// // c set up erf matrix p as ftn of dist,mag,period,level,flt type,atten
	// // type
	// // c convert to natural log units
	// double sigma = bsigma[iq] * 2.302585093;
	// // c-- loop through magnitudes
	// // do 104 m=1,nmag
	// // xmag0= magmin + (m-1)*dmag
	// // c--- loop through atten. relations for each period
	// // c-- gnd for SS; gnd2 for thrust; gnd3 for normal
	// double mag;
	// if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
	//
	// // if(iconv(ip,ia).eq.1) THEN
	// // xmag= 1.14 +0.24*xmag0+0.0933*xmag0*xmag0
	// // ELSEif(iconv(ip,ia).eq.2) then
	// // xmag= 2.715 -0.277*xmag0+0.127*xmag0*xmag0
	// // endif
	//
	// // / mag should be passed to table
	// // / should throw exception for M<4.4
	// // int imag= (int) ((mag-4.4)/0.2 + 1.0); //
	// // double xm1= (imag-1.0)*0.2 + 4.4;
	// // double fracm= (mag-xm1)/0.2;
	// // c loop over depth of rupture. dtor replaces bdepth in this
	// // subroutine.
	// // do 103 kk=1,ntor
	// // double hsq=dtor(kk)*dtor(kk);
	// // et = kk.eq.1 .and. deagg
	// // c-- loop through distances. ii index corresponds to rjb.
	// // do 103 ii=1,ndist
	// // dist0= (ii-.5)*di
	// // weight= wt(ip,ia,1)
	// // if(dist0.gt.wtdist(ip,ia)) weight= wt(ip,ia,2)
	// // dist= sqrt(dist0*dist0+hsq)
	// double dist = rRup;
	//
	// // if(dist0.lt.distnf) then
	// // sigmap= sigma+ sigmanf
	// // else
	// // sigmap = sigma
	// // endif
	// // double sigmasq= sigmap*SQRT_2;
	// double sigmasq = sigma * SQRT_2;
	// if (dist < 10.0) dist = 10.0;
	// double rdist = Math.log10(dist);
	//
	// // index needs +1 removed
	// int idist = (rdist - tabdist[1]) / 0.1 + 1;
	// double xd1 = (idist - 1) * 0.1 + tabdist[1];
	// double fracd = (rdist - xd1) / 0.1;
	// int idist1 = idist + 1;
	// // c write(19,*) ip,xmag,imag,dist,idist
	//
	// if (idist > 21) idist = 21;
	// if (idist1 > 21) idist1 = 21;
	//
	// double gnd = gmMap.get(iq).get(mag, dist);
	// // double gm1= gma[imag][idist];
	// // double gm2= gma[imag+1][idist];
	// // double gm3= gma[imag][idist1];
	// // double gm4= gma[imag+1][idist1];
	//
	// // double arl= gm1 + fracm*(gm2-gm1);
	// // double aru= gm3 + fracm*(gm4-gm3);
	// // double gnd= arl +fracd*(aru-arl);
	// // double gnd= gnd+ xlogfac[iq];
	// // double gnd= gnd/0.4342945;
	// // c if(dist0.gt.950.) then
	// // c taper= (1001.-dist)/50.
	// // c if(taper.le.0.01) taper=.01
	// // c taper= log(taper)
	// // c gnd=gnd+taper
	// // c endif
	// // c--- following is for clipping 1.5 g pga, 3 g 5hz 3 hz
	// if (clampMean) gnd = Utils.ceusMeanClip(period, gnd);
	//
	// // if(period.eq.0.)then
	// // gnd=min(0.405,gnd)
	// // elseif(sp)then
	// // gnd=min(gnd,1.099)
	// // endif
	// // c clamping issues mean that erf() must be called inside subr. THis
	// // may be
	// // c a problem for PCs with windows OS and using off-the-shelf gfortran
	// // to compile.
	// // test= exp(gnd + 3.*sigmasq/sqrt2)
	// // if (clamp[iq].lt.test.and.clamp[iq].gt.0.)then
	// // clamp2= alog(clamp[iq])
	// // else
	// // clamp2= gnd+ 3.*sigmasq/sqrt2
	// // endif
	// // tempgt3= (gnd- clamp2)/sigmasq
	// // probgt3= (erf(tempgt3)+1.)*0.5
	// // do 102 k=1,nlev(ip)
	// // temp= (gnd- xlev(k,ip))/sigmasq
	// // temp1= (erf(temp)+1.)*0.5
	// // temp1= (temp1-probgt3)/(1.-probgt3)
	// // c if
	// //
	// (ii.eq.1.and.m.eq.1.and.k.eq.1)write(6,*)ip,temp,temp1,gnd,probgt3,'getFEA'
	// // if(temp1.lt.0.) goto 103
	// // fac=weight*temp1
	// // pr(ii,m,k,ip,kk,1)= pr(ii,m,k,ip,kk,1) + fac
	// // if(et)e0_ceus(ii,m,ip)= e0_ceus(ii,m,ip)-sqrt2*temp*fac
	// // 102 continue
	// // 103 continue !dist loop
	// // 104 continue !mag loop
	// // return
	// // 234 write(6,*)'getFEA table ',subd//nametab(iq),' not found'
	// // stop 'program hazgridXnga5 cannot continue without it.'
	// // 236 write(6,*)'getFEA table ',subd//nameab(iq),' not found'
	// // stop 'program hazgridXnga5 cannot continue without it.'
	// // 237 write(6,*)'getFEA table ',subd//namehr(iq),' not found'
	// // write(6,*)'Period index iq is ',iq
	// // stop 'program hazgridXnga5 cannot continue without it.'
	// // 238 write(6,*)'getFEA table ',subd//hardab(iq),' not found'
	// // stop 'program hazgridXnga5 cannot continue without it.'
	// // end subroutine getFEA
	// }

	// public static void main(String[] args) {
	// try {
	// GM_Table gmt = new GM_Table(Utils.getResource("/imr/pgak01l.tbl"));
	// //System.out.println(Math.log10(Math.exp(gmt.get(8.3, 1001))));
	// System.out.println(gmt.get(5.1, Math.pow(10,1.55)) /
	// Utils.LOG_BASE_10_TO_E);
	// System.out.println(gmt.get(7.1, Math.pow(10,1.55)) /
	// Utils.LOG_BASE_10_TO_E);
	// System.out.println(gmt.get(5.1, Math.pow(10,2.55)) /
	// Utils.LOG_BASE_10_TO_E);
	// System.out.println(gmt.get(7.1, Math.pow(10,2.55)) /
	// Utils.LOG_BASE_10_TO_E);
	// } catch (IOException ioe) {
	// ioe.printStackTrace();
	// }
	//
	// // FrankelEtAl_1996_AttenRel fea = new FrankelEtAl_1996_AttenRel(null);
	// // System.out.println(fea.gmTablesHard.size());
	// // double gm = fea.gmTablesHard.get(0).get(5.4, 100);
	// // System.out.println(gm);
	// }
}
