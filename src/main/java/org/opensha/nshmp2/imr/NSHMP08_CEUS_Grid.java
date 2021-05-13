package org.opensha.nshmp2.imr;

import static org.opensha.nshmp2.util.FaultCode.*;
import static org.opensha.nshmp2.util.Period.GM0P00;
import static org.opensha.sha.imr.AttenRelRef.*;

import java.util.Map;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.nshmp2.util.CurveTable;
import org.opensha.nshmp2.util.FaultCode;
import org.opensha.nshmp2.util.Period;
import org.opensha.nshmp2.util.Utils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.PropagationEffect;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * Customized CEUS IMR for mblg grid sources that uses exceedance probability
 * tables to speed up calculations. 
 * 
 * Experimentation during development demonstrated that using table lookups for
 * fixed-strike sources performed worse than treating them as finite faults.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP08_CEUS_Grid extends NSHMP08_CEUS implements GridIMR {

	public final static String NAME = "NSHMP 2008 CEUS Combined (Grid)";
	public final static String SHORT_NAME = "NSHMP08_CEUS_GRID";

	private static Map<FaultCode, Map<Period, CurveTable>> tables;
	private CurveTable table; // table in use by instance
	private FaultCode code;
	private double utilMag;

	@Override
	void initImrMap() {
		imrMap = Maps.newHashMap();
		imrMap.put(TORO_1997.instance(null), 0.25);
		imrMap.put(FEA_1996.instance(null), 0.125);
		imrMap.put(AB_2006_140.instance(null), 0.125);
		imrMap.put(AB_2006_200.instance(null), 0.125);
		imrMap.put(CAMPBELL_2003.instance(null), 0.125);
		imrMap.put(TP_2005.instance(null), 0.125);
		imrMap.put(SILVA_2002.instance(null), 0.125);
	}
	
	static {
		tables = Maps.newEnumMap(FaultCode.class);
		Map<Period, CurveTable> AB_map = Maps.newEnumMap(Period.class);
		Map<Period, CurveTable> J_map = Maps.newEnumMap(Period.class);
		tables.put(M_CONV_AB, AB_map);
		tables.put(M_CONV_J, J_map);
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

	/**
	 * Triggers build of probability of exceedence table for current
	 * configuration of IMR. Sets the internal <code>FaultCode</code>. This
	 * method should be called prior to processing a grid source hazard
	 * calculation that requires conversion from mblg to Mw.
	 * 
	 * @param code to set
	 * @throws NullPointerException if <code>code</code> is <code>null</code>
	 */
	public void setTable(FaultCode code) {
		Preconditions.checkNotNull(code);
		Preconditions.checkArgument(code == M_CONV_AB || code == M_CONV_J,
			"Supplied code [%s] is not allowed", code);
		this.code = code;
		Period p = Period.valueForPeriod((Double) getParameter(
			PeriodParam.NAME).getValue());
		table = tables.get(code).get(p);
		if (table == null) table = initTable(code, p, imrMap);
	}
	

	private static final double R = 1000;
	private static final double dR = 5;
	private static final double minM = 5.0;
	private static final double maxM = 7.4;
	private static final double dM = 0.1;
	
	/*
	 * Re-check for table existence; because initTable is synchronized, a thread
	 * may be waiting for access to init the table currently being built on
	 * another thread; when the working thread releases, any waiting threads
	 * should check if the object required now exists. This shouldn't suffer
	 * from the same problems as double-checked locking in a singleton pattern
	 * because population of the table map with the locally built and refernced
	 * table happens instantanteously; there is no point were the table in
	 * question could be referenced in a partially constructed state.
	 * 
	 * An alternative might be to use a ConcurrentMap<Period, RateTable> with an
	 * unsynchronized initTable(). Rather than blocking threads waiting for a
	 * table to be built, threads would end up building and using their required
	 * table, which would only be added to the master table provided another
	 * table had not finished creating it first; see addIfAbsent(). However,
	 * the imrMap could not be static as it would be accessed concurrently.
	 */
	@SuppressWarnings("unchecked")
	private static synchronized CurveTable initTable(FaultCode code,
			Period period, Map<ScalarIMR, Double> imrMap) {
		CurveTable table = tables.get(code).get(period);
		if (table != null) { // TODO should be logged INFO
			System.out.println("Cached CEUS table [" + code + ", " + period + "]");
			return table;
		}

		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setIntensityMeasure((period == GM0P00) ? PGA_Param.NAME
				: SA_Param.NAME);
			imr.getParameter(PeriodParam.NAME).setValue(period.getValue());
		}

		System.out.println("Building CEUS table [" + code + ", " + period + "]");
		table = CurveTable.create(R, dR, minM, maxM, dM, imrMap,
			period.getFunction(), code);
		tables.get(code).put(period, table);
		return table;
	}
	
	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		// KLUDGY: because we need Mw for distance but mblg for magnitude PE
		// lookup, adjust the eqkRupture mag here to Mw and keep a local mblg
		// value for use in getPE(f)
		utilMag = eqkRupture.getMag();
		eqkRupture.setMag(Utils.mblgToMw(code, utilMag));
		super.setEqkRupture(eqkRupture);
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls)
			throws ParameterException {
		double d = eqkRupture.getRuptureSurface().getDistanceJB(
			site.getLocation());
		return table.get(d, utilMag);
	}

	@Override
	public DiscretizedFunc getExceedProbFromParent(DiscretizedFunc imls) {
		throw new UnsupportedOperationException(
			"This method not required by CEUS Grid IMR");
	}

	public static void main(String[] args) {
		NSHMP08_CEUS_Grid imr = new NSHMP08_CEUS_Grid();
		Period per = Period.GM0P00;
		FaultCode fc = FaultCode.M_CONV_AB;
		imr.setIntensityMeasure((per == Period.GM0P00) ? "PGA" : "SA");
		imr.getParameter(PeriodParam.NAME).setValue(per.getValue());
		imr.setTable(fc); // triggers table build
		
		System.out.println(tables.get(fc).get(per).get(12.0, 6.55));
		System.out.println(tables.get(fc).get(per).get(0,5.0));
	}


}
