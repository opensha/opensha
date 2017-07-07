package org.opensha.sha.imr.attenRelImpl.test;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.hazardMap.AsciiCurveAverager;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ERFTestSubset;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.AS_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.MultiIMR_Averaged_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

public class MultiIMR_CalcTest {

	private static ERF erf;
	private static Site site;
	private static HazardCurveCalculator hc;

	/**
	 * The maximum allowed percent difference for curves calculated with 1 IMR by itself
	 * vs when wrapped with the MultiIMR averager
	 */
	private static final double max_curve_pt_diff = 0.01;
	/**
	 * The maximum allowed percent difference for curves calculated with multiple IMRs by themselves
	 * (and then averaged) vs calculated with the MultiIMR averager
	 */
	private static final double max_avg_curve_pt_diff = 0.02;
	private static final double max_val_diff = 0.01;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
//		erf = new Frankel02_AdjustableEqkRupForecast();
//		erf = new Frankel96_AdjustableEqkRupForecast();
		ERFTestSubset myERF = new ERFTestSubset(new Frankel96_AdjustableEqkRupForecast());
		myERF.updateForecast();
		myERF.includeSource(0);
		myERF.includeSource(1);
		myERF.includeSource(2);
		myERF.includeSource(281);
		myERF.includeSource(39);
		myERF.includeSource(179);
		myERF.includeSource(49);
		myERF.includeSource(22);
		myERF.includeSource(63);
		
		erf = myERF;

		Vs30_Param vs30 = new Vs30_Param(760d);
		vs30.setValueAsDefault();
		DepthTo2pt5kmPerSecParam z25 = new DepthTo2pt5kmPerSecParam(1d);
		z25.setValueAsDefault();
		DepthTo1pt0kmPerSecParam z10 = new DepthTo1pt0kmPerSecParam(1d, false);
		z10.setValueAsDefault();
		Vs30_TypeParam vs30Type = new Vs30_TypeParam();
		vs30Type.setValueAsDefault();

		site = new Site(new Location(34, -120));
		site.addParameter(vs30);
		site.addParameter(z25);
		site.addParameter(z10);
		site.addParameter(vs30Type);

		hc = new HazardCurveCalculator();
	}
	
	private static MultiIMR_Averaged_AttenRel buildMulti(ArrayList<ScalarIMR> imrs) {
		MultiIMR_Averaged_AttenRel multi = new MultiIMR_Averaged_AttenRel(imrs);
		return multi;
	}

	protected static ArrayList<ScalarIMR> createNGAs(boolean setParamDefaults) {
		ArrayList<ScalarIMR> imrs = new ArrayList<ScalarIMR>();

		imrs.add(new CB_2008_AttenRel(null));
		imrs.add(new BA_2008_AttenRel(null));
		imrs.add(new CY_2008_AttenRel(null));
		imrs.add(new AS_2008_AttenRel(null));

		if (setParamDefaults) {
			for (ScalarIMR imr : imrs)
				imr.setParamDefaults();
		}

		return imrs;
	}

	@Test
	public void testSingleIMRs_PGA() {
		doHC_NGA_Test(PGA_Param.NAME, -1.0);
	}

	@Test
	public void testSingleIMRs_PGV() {
		doHC_NGA_Test(PGV_Param.NAME, -1.0);
	}

	@Test
	public void testSingleIMRs_SA01() {
		doHC_NGA_Test(SA_Param.NAME, 0.1);
	}

	@Test
	public void testSingleIMRs_SA10() {
		doHC_NGA_Test(SA_Param.NAME, 1.0);
	}

	@Test
	public void testSingleIMRs_SA20() {
		doHC_NGA_Test(SA_Param.NAME, 2.0);
	}

	@Test
	public void testMultiIMRs_SA10() {
		ArrayList<ScalarIMR> ngas1 = createNGAs(true);
		ArrayList<ScalarIMR> ngas2 = createNGAs(true);
		testMultiIMRAverageCurve(buildMulti(ngas1), ngas2, SA_Param.NAME, 1.0);
	}

	@Test
	public void testSingleIMRs_EPSILON_SA10() {
		doVal_NGA_Test(SA_Param.NAME, 1.0, IMR_PROP.EPSILON);
	}

	@Test
	public void testSingleIMRs_MEAN_SA10() {
		doVal_NGA_Test(SA_Param.NAME, 1.0, IMR_PROP.MEAN);
	}

	@Test
	public void testSingleIMRs_STD_DEV_SA10() {
		doVal_NGA_Test(SA_Param.NAME, 1.0, IMR_PROP.STD_DEV);
	}

	@Test
	public void testSingleIMRs_EXCEED_PROB_SA10() {
		doVal_NGA_Test(SA_Param.NAME, 1.0, IMR_PROP.EXCEED_PROB);
	}

	@Test
	public void testSingleIMRs_EPSILON_PGA() {
		doVal_NGA_Test(PGA_Param.NAME, -1.0, IMR_PROP.EPSILON);
	}

	@Test
	public void testSingleIMRs_MEAN_PGA() {
		doVal_NGA_Test(PGA_Param.NAME, -1.0, IMR_PROP.MEAN);
	}

	@Test
	public void testSingleIMRs_STD_DEV_PGA() {
		doVal_NGA_Test(PGA_Param.NAME, -1.0, IMR_PROP.STD_DEV);
	}

	@Test
	public void testSingleIMRs_EXCEED_PROB_PGA() {
		doVal_NGA_Test(PGA_Param.NAME, -1.0, IMR_PROP.EXCEED_PROB);
	}

	private void doHC_NGA_Test(String imt, double period) {
		ArrayList<ScalarIMR> imrs1 = createNGAs(true);
		ArrayList<ScalarIMR> imrs2 = createNGAs(true);
		ArrayList<ScalarIMR> imrs3 = createNGAs(true);
		ArrayList<ScalarIMR> imrs4 = createNGAs(true);
		for (int i=0; i<imrs1.size(); i++)
			testSingleIMRHazardCurve(imrs1.get(i),
					getMulti(imrs2.get(i)),
					getMulti(imrs3.get(i), imrs4.get(i)),
					imt, period);
	}

	private void doVal_NGA_Test(String imt, double period, IMR_PROP prop) {
		ArrayList<ScalarIMR> imrs1 = createNGAs(true);
		ArrayList<ScalarIMR> imrs2 = createNGAs(true);
		ArrayList<ScalarIMR> imrs3 = createNGAs(true);
		ArrayList<ScalarIMR> imrs4 = createNGAs(true);
		for (int i=0; i<imrs1.size(); i++)
			testSingleIMRIndVal(imrs1.get(i),
					getMulti(imrs2.get(i)),
					getMulti(imrs3.get(i), imrs4.get(i)),
					imt, period, prop);
	}

	private MultiIMR_Averaged_AttenRel getMulti(ScalarIMR imr) {
		ArrayList<ScalarIMR> imrs =
			new ArrayList<ScalarIMR>();
		imrs.add(imr);
		MultiIMR_Averaged_AttenRel multi = buildMulti(imrs);
		return multi;
	}

	private MultiIMR_Averaged_AttenRel getMulti(ScalarIMR imr1,
			ScalarIMR imr2) {
		ArrayList<ScalarIMR> imrs =
			new ArrayList<ScalarIMR>();
		imrs.add(imr1);
		imrs.add(imr2);
		MultiIMR_Averaged_AttenRel multi = buildMulti(imrs);
		return multi;
	}

	protected enum IMR_PROP {
		EPSILON,
		MEAN,
		STD_DEV,
		EXCEED_PROB;
	}

	private void testSingleIMRIndVal(ScalarIMR imr,
			MultiIMR_Averaged_AttenRel multi,
			MultiIMR_Averaged_AttenRel multis,
			String imt, double period, IMR_PROP prop) {

		setIMT(imr, imt, period);
		setIMT(multi, imt, period);
		setIMT(multis, imt, period);

		imr.setSite(site);
		multi.setSite(site);
		multis.setSite(site);

		MinMaxAveTracker tracker1 = new MinMaxAveTracker();
		MinMaxAveTracker tracker2 = new MinMaxAveTracker();

		String meta = "("+prop+") IMR: " + imr.getShortName() + " IMT: " + imt;
		if (period >= 0)
			meta += " PERIOD: " + period;

		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			for (int rupID=0; rupID<source.getNumRuptures(); rupID++) {
				ProbEqkRupture rup = source.getRupture(rupID);
				imr.setEqkRupture(rup);
				multi.setEqkRupture(rup);
				multis.setEqkRupture(rup);

				double mean1 = imr.getMean();
				double mean2 = multi.getMean();
				double mean3 = multis.getMean();

				double val1 = 0;
				double val2 = 0;
				double val3 = 0;
				if (prop == IMR_PROP.EPSILON) {
					val1 = imr.getEpsilon();
					val2 = multi.getEpsilon();
					val3 = multis.getEpsilon();
				} else if (prop == IMR_PROP.MEAN) {
					val1 = mean1;
					val2 = mean2;
					val3 = mean3;
				} else if (prop == IMR_PROP.STD_DEV) {
					val1 = imr.getStdDev();
					val2 = multi.getStdDev();
					val3 = multis.getStdDev();
				} else if (prop == IMR_PROP.EXCEED_PROB) {
					val1 = imr.getExceedProbability();
					val2 = multi.getExceedProbability();
					val3 = multis.getExceedProbability();
				}

				double diff1 = DataUtils.getPercentDiff(val1, val2);
				double diff2 = DataUtils.getPercentDiff(val2, val3);
				tracker1.addValue(diff1);
				tracker2.addValue(diff2);
				String vals = val1 + ", " + val2 + ", PDIFF1: " + diff1 + ", " + val3 + ", PDIFF2: " + diff2;
				assertTrue(meta+" PDiff1 greater than "+max_val_diff+"\n"+vals, diff1 < max_val_diff);
				assertTrue(meta+" PDiff2 greater than "+max_val_diff+"\n"+vals, diff2 < max_val_diff);

			}
		}
		System.out.println("********* " + meta + " *********");
		System.out.println("compare single with multi(1):\t" + tracker1);
		System.out.println("compare multi(1) with multi(2):\t" + tracker2);
		System.out.println("********************************************************");
	}

	private void testMultiIMRAverageCurve(MultiIMR_Averaged_AttenRel multi,
			ArrayList<ScalarIMR> imrs,
			String imt, double period) {

		IMT_Info imtInfo = new IMT_Info();
		ArrayList<DiscretizedFunc> singleCurves = new ArrayList<DiscretizedFunc>();
		for (ScalarIMR imr : imrs) {
			setIMT(imr, imt, period);
			DiscretizedFunc singleCurve = imtInfo.getDefaultHazardCurve(imt);
			hc.getHazardCurve(singleCurve, site, imr, erf);
			singleCurves.add(singleCurve);
		}
		DiscretizedFunc averageCurve = AsciiCurveAverager.averageCurves(singleCurves);
		averageCurve.setName("Average value curve");
		setIMT(multi, imt, period);
		DiscretizedFunc multiCurve = imtInfo.getDefaultHazardCurve(imt);
		multiCurve.setName("Curve calculated with MultiIMR_Averaged_AttenRel");
		hc.getHazardCurve(multiCurve, site, multi, erf);

		String meta = "(hazard curve) IMT: " + imt;
		if (period >= 0)
			meta += " PERIOD: " + period;

		MinMaxAveTracker tracker1 = new MinMaxAveTracker();

		int perfectMatches = 0;

		for (int j=0; j<multiCurve.size(); j++) {
			double x = multiCurve.getX(j);
			double yMulti = multiCurve.getY(j);
			double ySingleAvg = averageCurve.getY(j);

			if (ySingleAvg == yMulti && ySingleAvg != 0d)
				perfectMatches++;

			double absDiff = Math.abs(ySingleAvg - yMulti);
			double diff = DataUtils.getPercentDiff(ySingleAvg, yMulti);
			String vals = ySingleAvg + ", " + yMulti + ", PDIFF: " + diff;
			if (absDiff > 1e-10) {
				assertTrue(meta+" PDiff1 greater than "+max_avg_curve_pt_diff+"\n"+vals,
						diff < max_avg_curve_pt_diff || absDiff < 1e-10);
				
				tracker1.addValue(diff);
			}
			String singleVals = null;
			for (DiscretizedFunc singleCurve : singleCurves) {
				if (singleVals == null)
					singleVals = "";
				else
					singleVals += "\t";
				singleVals += "("+(float)singleCurve.getY(j)+")";
			}
			System.out.println("x: "+x+"\tmulti: "+(float)yMulti+"\tsingleAvg: "+(float)ySingleAvg
					+"\tpDiff: "+(float)diff+"\tind vals: "+singleVals);
		}
		//		System.out.println(multiCurve);
		//		System.out.println(averageCurve);
		System.out.println("********* " + meta + " *********");
		System.out.println("Percent differences between MultiIMR value and averaged single values " +
				"(for each hazard curve x value):\n" + tracker1);
		System.out.println(perfectMatches+"/"+multiCurve.size()+" non zero curve points match EXACTLY");
		System.out.println("********************************************************");

	}

	private void testSingleIMRHazardCurve(ScalarIMR imr,
			MultiIMR_Averaged_AttenRel multi,
			MultiIMR_Averaged_AttenRel multis,
			String imt, double period) {

		setIMT(imr, imt, period);
		setIMT(multi, imt, period);
		setIMT(multis, imt, period);

		IMT_Info imtInfo = new IMT_Info();

		DiscretizedFunc curve1 = imtInfo.getDefaultHazardCurve(imt);
		DiscretizedFunc curve2 = imtInfo.getDefaultHazardCurve(imt);
		DiscretizedFunc curve3 = imtInfo.getDefaultHazardCurve(imt);
		hc.getHazardCurve(curve1, site, imr, erf);
		hc.getHazardCurve(curve2, site, multi, erf);
		hc.getHazardCurve(curve3, site, multis, erf);

		String meta = "(hazard curve) IMR: " + imr.getShortName() + " IMT: " + imt;
		if (period >= 0)
			meta += " PERIOD: " + period;

		MinMaxAveTracker tracker1 = new MinMaxAveTracker();
		MinMaxAveTracker tracker2 = new MinMaxAveTracker();

		for (int j=0; j<curve1.size(); j++) {

			double y1 = curve1.getY(j);
			double y2 = curve2.getY(j);
			double y3 = curve3.getY(j);

			double diff1 = DataUtils.getPercentDiff(y1, y2);
			double diff2 = DataUtils.getPercentDiff(y2, y3);
			tracker1.addValue(diff1);
			tracker2.addValue(diff2);
			String vals = y1 + ", " + y2 + ", PDIFF1: " + diff1 + ", " + y3 + ", PDIFF2: " + diff2;
			assertTrue(meta+" PDiff1 greater than "+max_curve_pt_diff+"\n"+vals, diff1 < max_curve_pt_diff);
			assertTrue(meta+" PDiff2 greater than "+max_curve_pt_diff+"\n"+vals, diff2 < max_curve_pt_diff);
		}
		System.out.println("********* " + meta + " *********");
		System.out.println("compare single with multi(1):\t" + tracker1);
		System.out.println("compare multi(1) with multi(2):\t" + tracker2);
		System.out.println("********************************************************");

	}

	protected static void setIMT(ScalarIMR imr, String imt, double period) {
		imr.setIntensityMeasure(imt);
		if (period >= 0) {
			Parameter<Double> imtParam = (Parameter<Double>) imr.getIntensityMeasure();
			imtParam.getIndependentParameter(PeriodParam.NAME).setValue(period);
		}
	}

	@Test
	public void testCurveAverage_SA01() {
		testCurveAverage(SA_Param.NAME, 0.1);
	}

	@Test
	public void testCurveAverage_SA10() {
		testCurveAverage(SA_Param.NAME, 1.0);
	}

	@Test
	public void testCurveAverage_PGA() {
		testCurveAverage(PGA_Param.NAME, -1.0);
	}

	private void testCurveAverage(String imt, double period) {
		CB_2008_AttenRel cb08_master = new CB_2008_AttenRel(null);
		CB_2008_AttenRel cb08_multi = new CB_2008_AttenRel(null);
		BA_2008_AttenRel ba08_master = new BA_2008_AttenRel(null);
		BA_2008_AttenRel ba08_multi = new BA_2008_AttenRel(null);
		cb08_master.setParamDefaults();
		cb08_multi.setParamDefaults();
		ba08_master.setParamDefaults();
		ba08_multi.setParamDefaults();

		IMT_Info imtInfo = new IMT_Info();

		ArrayList<ScalarIMR> imrs = new ArrayList<ScalarIMR>();
		imrs.add(cb08_multi);
		imrs.add(ba08_multi);

		MultiIMR_Averaged_AttenRel multiIMR = buildMulti(imrs);

		setIMT(multiIMR, imt, period);
		setIMT(cb08_master, imt, period);
		setIMT(ba08_master, imt, period);

		DiscretizedFunc multiFunc = imtInfo.getDefaultHazardCurve(imt);
		DiscretizedFunc cb08Func = imtInfo.getDefaultHazardCurve(imt);
		DiscretizedFunc ba08Func = imtInfo.getDefaultHazardCurve(imt);

		hc.getHazardCurve(multiFunc, site, multiIMR, erf);
		hc.getHazardCurve(cb08Func, site, cb08_master, erf);
		hc.getHazardCurve(ba08Func, site, ba08_master, erf);

		int numVals = multiFunc.size();
		int numEqualCB = 0;
		int numEqualBA = 0;

		for (int i=0; i<numVals; i++) {
			double multiVal = multiFunc.getY(i);
			double cbVal = cb08Func.getY(i);
			double baVal = ba08Func.getY(i);

			if (multiVal != 0 && multiVal == cbVal)
				numEqualCB++;
			if (multiVal != 0 && multiVal == baVal)
				numEqualBA++;
			double avgVal = (cbVal + baVal) / 2d;
			assertEquals("average not within 0.01 of multi val!", avgVal, multiVal, 0.01);
		}
		assertTrue("averaged curve matches CB curve in " + numEqualCB + " places!", numEqualCB < 3);
		assertTrue("averaged curve matches BA curve in " + numEqualBA + " places!", numEqualBA < 3);
	}

}
