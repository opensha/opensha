package org.opensha.nshmp2.util;

import static org.opensha.nshmp2.util.FaultCode.*;
import static org.opensha.nshmp2.util.Period.*;
import static org.opensha.sha.imr.AttenRelRef.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.eq.cat.util.MagnitudeType;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.nshmp2.imr.GridIMR;
import org.opensha.nshmp2.imr.impl.ToroEtAl_1997_AttenRel;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusJB_OverRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Hazard curve table used for gridded seismicity sources. An instance of a
 * {@code CurveTable} permits curve lookup by distance and magnitude. Values
 * in the table are bin centers so when requesting the curve for a particular
 * distance and magnitude, the closest corresponding curve is returned.
 * Internally, this class uses an {@link ArrayTable} to manage curves.
 * 
 * @author Peter Powers
 * @version $Id$
 */
public class CurveTable {

	/*
	 * The internal ArrayTable is initialized using Double valued keys of
	 * distance and magnitude. These are used to build the curves but are NOT
	 * used for lookup because we often are not requesting the exact value but
	 * rather want the closest value. There would also be potential hashing
	 * problems with Double valued keys.Instead, get(double, double) requests
	 * bypass Table.get(R,C) and convert arguments to their corresponding
	 * indices and use ArrayTable.at(int, int) instead. This is ultimately
	 * faster.
	 */
	
	private final ArrayTable<Double, Double, DiscretizedFunc> curves;
	private final double dR, dM, minM;
	
	private CurveTable(List<Double> d, List<Double> m, double dR, double dM,
		double minM) {
		curves = ArrayTable.create(d, m);
		this.dR = dR;
		this.dM = dM;
		this.minM = minM;
	}
	
	private static final double DTOR = 5.0; // rup top for CEUS gridded
	private static final double DTOR_SQ = DTOR * DTOR;
	
	/**
	 * Builds a new hazard curve table. This {@code create(...)} method is most
	 * commonly used (i.e. for Western US grid sources).
	 * @param R max distance
	 * @param dR distance increment
	 * @param minM minimum magnitude
	 * @param maxM maximum magnitude
	 * @param dM magnitude interval
	 * @param model function for table
	 * @param imr to use as basis for curves; the imr should be fully
	 *        initialized for period and other parameters with the exception of
	 *        distance metrics and magnitude
	 * @param depthForMag function to calculate magnitude dependent rupture
	 *        depths
	 * @return the populated curve table
	 */
	public static CurveTable create(double R, double dR, double minM,
			double maxM, double dM, ScalarIMR imr,
			ArbitrarilyDiscretizedFunc model,
			Function<Double, Double> depthForMag) {
		CurveTable table = initTable(R, dR, minM, maxM, dM);
		table.fillWUS(imr, model, depthForMag);
		return table;
	}
	
	/**
	 * Builds a new hazard curve table. This {@code create(...)} method is used
	 * for WUS deep grid sources where rupture depth can be determined from
	 * imr.
	 * 
	 * @param R max distance
	 * @param dR distance increment
	 * @param minM minimum magnitude
	 * @param maxM maximum magnitude
	 * @param dM magnitude interval
	 * @param model function for table
	 * @param imr to use as basis for curves; the imr should be fully
	 *        initialized for period and other parameters with the exception of
	 *        distance metrics and magnitude
	 * @return the populated curve table
	 */
	public static CurveTable create(double R, double dR, double minM,
			double maxM, double dM, ScalarIMR imr,
			ArbitrarilyDiscretizedFunc model) {
		CurveTable table = initTable(R, dR, minM, maxM, dM);
		table.fillSLAB(imr, model);
		return table;
	}

	/**
	 * Builds a new hazard curve table. This {@code create(...)} method is used
	 * for CEUS grid sources where {@link ToroEtAl_1997_AttenRel} requires
	 * special treatment due to its having unique mblg parameters. All other
	 * CEUS attenuation relations simply convert mblg to Mw before calculation.
	 * 
	 * @param R max distance
	 * @param dR distance increment
	 * @param minM minimum magnitude
	 * @param maxM maximum magnitude
	 * @param dM magnitude interval
	 * @param model function for table
	 * @param imrs map of {@link ScalarIMR}s and associated weights
	 * @param magConv flag for mag conversion method
	 * @return the populated curve table
	 */
	public static CurveTable create(double R, double dR, double minM,
			double maxM, double dM, Map<ScalarIMR, Double> imrs,
			ArbitrarilyDiscretizedFunc model, FaultCode magConv) {
		CurveTable table = initTable(R, dR, minM, maxM, dM);
		table.fillCEUS(imrs, model, magConv);
		return table;
	}

	/*
	 * Initialize table with distance and magnitude values that will be used to
	 * build curves.
	 */
	private static CurveTable initTable(double R, double dR, double minM,
			double maxM, double dM) {
		int numM = (int) Math.round((maxM - minM) / dM);
		int numR = (int) Math.round(R / dR);
		List<Double> rList = sequence(0d, dR, numR);
//		System.out.println(numR + " " + rList);
		List<Double> mList = sequence(minM, dM, numM);
//		System.out.println(numM + " " + mList);
		CurveTable table = new CurveTable(rList, mList, dR, dM, minM);
		return  table;
	}
	
	/*
	 * Creates a 'len' size sequential list of Doubles that are the half-widths
	 * of the supplied 'interval'.
	 */
	private static List<Double> sequence(double start, double interval,
			int len) {
		// rounding prevents precision errors and oddities
		double value = start + interval / 2;
		List<Double> list = Lists.newArrayList();
		for (int i = 0; i < len; i++) {
			list.add(Precision.round(value, 4));
			value += interval;
		}
		return list;
	}
	
	/**
	 * Returns the hazard curve associated with the supplied distance and
	 * magnitude.
	 * @param r distance
	 * @param m magnitude
	 * @return a hazard curve
	 */
	public DiscretizedFunc get(double r, double m) {
		return curves.at(key(r, dR), key(m - minM, dM));
	}
	
	public int size() {
		return curves.size();
	}
	
	/*
	 * Computes an index from a value and interval.
	 */
	private static final int key(double v, double delta) {
		return (int) (v / delta);
	}
	
	
	@SuppressWarnings("unchecked")
	private void fillWUS(ScalarIMR imr, ArbitrarilyDiscretizedFunc model,
			Function<Double, Double> depthForMag) {
		for (double r : curves.rowKeyList()) {
			imr.getParameter(DistanceJBParameter.NAME).setValue(r);
			for (double m : curves.columnKeyList()) {
				double v = depthForMag.apply(m);
				imr.getParameter(RupTopDepthParam.NAME).setValue(v);
				double rRup = Math.sqrt(r * r + v * v);
				imr.getParameter(DistanceRupParameter.NAME).setValue(rRup);
				double rOver = (rRup == 0.0) ? 0.0 : (rRup - r) / rRup;
				imr.getParameter(DistRupMinusJB_OverRupParameter.NAME)
						.setValue(rOver);
				imr.getParameter(MagParam.NAME).setValue(m);
				DiscretizedFunc f = ((GridIMR) imr).getExceedProbFromParent(
					model.deepClone());
				curves.put(r, m, f);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void fillSLAB(ScalarIMR imr, ArbitrarilyDiscretizedFunc model) {
		double v = (Double) imr.getParameter(RupTopDepthParam.NAME).getValue();
		double vSq  = v * v;
		for (double r : curves.rowKeyList()) {
			for (double m : curves.columnKeyList()) {
				double rRup = Math.sqrt(r * r + vSq);
				imr.getParameter(DistanceRupParameter.NAME).setValue(rRup);
				imr.getParameter(MagParam.NAME).setValue(m);
				DiscretizedFunc f = ((GridIMR) imr).getExceedProbFromParent(
					model.deepClone());
				curves.put(r, m, f);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void fillCEUS(Map<ScalarIMR, Double> imrs,
			ArbitrarilyDiscretizedFunc model, FaultCode magConv) {
		for (ScalarIMR imr : imrs.keySet()) {
			double imrWt = imrs.get(imr);
			for (double m : curves.columnKeyList()) {
				double mag = m;
				if (magConv == M_CONV_AB || magConv == M_CONV_J) {
					if (imr instanceof ToroEtAl_1997_AttenRel) {
						// update hidden Toro param to use mblg coeffs
						imr.getOtherParams().getParameter("Magnitude Type")
							.setValue(MagnitudeType.LG_PHASE);
					} else {
						// otherwise convert mag to Mw for other imrs
						mag = Utils.mblgToMw(magConv, mag);
					}
				}
				imr.getParameter(MagParam.NAME).setValue(mag);
				for (double r : curves.rowKeyList()) {
					try {
						imr.getParameter(DistanceJBParameter.NAME).setValue(r);
						// if parameter doesn't exist for imr, keep moving
					} catch (ParameterException e) {}
					double rRup = Math.sqrt(r * r + DTOR_SQ);
					try {
						imr.getParameter(DistanceRupParameter.NAME).setValue(rRup);
						// if parameter doesn't exist for imr, keep moving
					} catch (ParameterException e) {}

					DiscretizedFunc f = imr.getExceedProbabilities(
						model.deepClone());
					f.scale(imrWt);

					DiscretizedFunc fSum = curves.get(r,m);
					if (fSum == null) {
						curves.put(r, m, f);
					} else {
						Utils.addFunc(fSum, f);
					}
				}
			}
		}
	}






	// NOTE this needs to find its way into CEUS erf
	// also neeed to move mag conversion out or imrs, for ceus erf will need
	// to create lookup tables for each mag conversion method

	public static void main(String[] args) {
		// Set<Period> periods = EnumSet.of(GM0P00, GM0P20, GM1P00, GM2P00);
		Set<Period> periods = EnumSet.of(GM0P00);

		Map<ScalarIMR, Double> imrs = Maps.newHashMap();

		// XXXXX
		// ScalarIMR imr = new SomervilleEtAl_2001_AttenRel(null);
		// imr.setParamDefaults();
		// imr.getParameter("Mag. Conversion Method").setValue(FaultCode.M_CONV_AB);

//		ScalarIMR imr = FEA_1996.instance(null);
//		imr.setParamDefaults();

//		ScalarIMR imr = AB_2006_140.instance(null);
//		imr.setParamDefaults();

//		ScalarIMR imr = AB_2006_200.instance(null);
//		imr.setParamDefaults();
		

		// ScalarIMR imr = new Campbell_2003_AttenRel(null);
		// imr.setParamDefaults();

		// ScalarIMR imr = new TP2005_AttenRel(null);
		// imr.setParamDefaults();

		// ScalarIMR imr = new SilvaEtAl_2002_AttenRel(null);
		// imr.setParamDefaults();

		// ScalarIMR imr = new ToroEtAl_1997_AttenRel(null);
		// imr.setParamDefaults();

		// System.out.println(imr.getParameter("Magnitude Type").getValue());

//		imrs.put(imr, 1.0);
//		RateTable2 table = RateTable2.create(120, 5, 24, 5.05, 0.1, periods,
//			FaultCode.M_CONV_AB, imrs);

//		System.out.println(MathUtils.round((7.5 - 5.0) / 0.1, 4));
//		System.out.println(key(7.4-5.0,0.1));
		
		Map<ScalarIMR, Double> imrGrdMap = Maps.newHashMap();
		imrGrdMap.put(TORO_1997.instance(null), 0.25);
		imrGrdMap.put(FEA_1996.instance(null), 0.125);
		imrGrdMap.put(AB_2006_140.instance(null), 0.125);
		imrGrdMap.put(AB_2006_200.instance(null), 0.125);
		imrGrdMap.put(CAMPBELL_2003.instance(null), 0.125);
		imrGrdMap.put(TP_2005.instance(null), 0.125);
		imrGrdMap.put(SILVA_2002.instance(null), 0.125);

		Period p = Period.GM0P20;
		ArbitrarilyDiscretizedFunc f = p.getFunction();
		
		for (ScalarIMR imr : imrGrdMap.keySet()) {
//			imr.setParamDefaults();
			imr.setIntensityMeasure((p == GM0P00) ? PGA_Param.NAME : SA_Param.NAME);
			imr.getParameter(PeriodParam.NAME).setValue(p.getValue());
		}

		double mMin = 5.0;
		double mMax = 7.5;
		double rMax = 1000;

		Stopwatch sw = Stopwatch.createStarted();

		CurveTable table = CurveTable.create(rMax, 5d, mMin, mMax, 0.1,
			imrGrdMap, f, FaultCode.M_CONV_AB);
		sw.stop();
		
		System.out.println(table.curves.size());
		System.out.println(sw.elapsed(TimeUnit.MILLISECONDS));
		System.out.println(table.get(12.0, 6.55));
		System.out.println(table.curves.at(0,0));

//		System.out.println(key(7.4699998247045479 - 5.0, 0.1));
//		int len = 1000000;
//		List<Double> Rs = Lists.newArrayListWithCapacity(len);
//		List<Double> Ms = Lists.newArrayListWithCapacity(1000000);
//		double mDelta = mMax - mMin;
//		for (int i=0; i<len; i++) {
//			Ms.add(mMin + mDelta * Math.random());
//			Rs.add(rMax * Math.random());
//		}
//		double[] rArray = Doubles.toArray(Rs);
//		double[] mArray = Doubles.toArray(Ms);
//		System.out.println("rRange: " + Doubles.min(rArray) + " " + Doubles.max(rArray));
//		System.out.println("mRange: " + Doubles.min(mArray) + " " + Doubles.max(mArray));
//		
//		sw.reset().start();
//		for (int i=0; i<len; i++) {
//			Utils.addFunc(f, table.get(rArray[i], mArray[i]));
//		}
//		sw.stop();
//		System.out.println(sw.elapsedTime(TimeUnit.MILLISECONDS));

	}
}
