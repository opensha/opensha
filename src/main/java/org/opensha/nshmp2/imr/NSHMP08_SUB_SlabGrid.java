package org.opensha.nshmp2.imr;

import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.nshmp2.util.CurveTable;
import org.opensha.nshmp2.util.Period;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;

import com.google.common.collect.Maps;

/**
 * The grid implementation of {@link NSHMP08_SUB_Slab}.
 * 
 * <p> Note that parent uses rRup for distance. Although rRup is used to build
 * this class' {@code CurveTable}, rJB is used for lookups and the corresponding
 * parameter is initialized here. Formerly, a {@code PropagationEffect} was used
 * that could be queried for any distance as needed.</p>
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP08_SUB_SlabGrid extends NSHMP08_SUB_Slab implements GridIMR {

	public final static String NAME = "NSHMP 2008 Subduction Slab Combined (Grid)";
	public final static String SHORT_NAME = "NSHMP08_SLAB_GRID";
	private static final long serialVersionUID = 1L;

	private static Map<Period, CurveTable> tables;
	private CurveTable table; // table in use by instance
	
	static {
		tables = Maps.newEnumMap(Period.class);
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void setSite(Site site) {
		// overridden as its unnecessary to push Site down to child imrs
		// only needed in getExceedProbabilities(f)
		this.site = site;
	}

	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		// overridden as its unnecessary to push Site down to child imrs
		// only needed in getExceedProbabilities(f)
		this.eqkRupture = eqkRupture;
	}

	@Override
	public void setParamDefaults() {
		distanceJBParam.setValueAsDefault();
		super.setParamDefaults();
	}
	
	@Override
	protected void initPropagationEffectParams() {
		distanceJBParam = new DistanceJBParameter(0.0);
		distanceJBParam.setNonEditable();
		propagationEffectParams.addParameter(distanceJBParam);
		super.initPropagationEffectParams();
	}

	@Override
	protected void initParameterEventListeners() {
		distanceJBParam.addParameterChangeListener(this);
		super.initParameterEventListeners();
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls)
			throws ParameterException {
		double d = eqkRupture.getRuptureSurface().getDistanceJB(
			site.getLocation());
		double m = eqkRupture.getMag();
		return table.get(d, m);
	}
	
	@Override
	public DiscretizedFunc getExceedProbFromParent(DiscretizedFunc imls) {
		return super.getExceedProbabilities(imls);
	}

	/**
	 * Triggers build of probability of exceedence table for current
	 * configuration of IMR. NOTE this should really take a mag dependent depth
	 * function as well
	 */
	public void setTable() {
		Period p = Period.valueForPeriod((Double) getParameter(
			PeriodParam.NAME).getValue());
		table = tables.get(p);
		if (table == null) table = initTable(p, 
			(Double) getParameter(RupTopDepthParam.NAME).getValue());
	}
	
	private static final double R = 200;
	private static final double dR = 1;
	private static final double minM = 5;
	private static final double maxM = 7.3;
	private static final double dM = 0.1;
	
	@SuppressWarnings("unchecked")
	private static synchronized CurveTable initTable(Period period,
			double depth) {
		CurveTable table = tables.get(period);
		if (table != null) {
			System.out.println("Cached slab table [" + period + "]");
			return table;
		}
		System.out.println("Building slab table [" + period + "]");
		ScalarIMR imr = new NSHMP08_SUB_SlabGrid();
		imr.setIntensityMeasure((period == Period.GM0P00) ? PGA_Param.NAME
			: SA_Param.NAME);
		imr.getParameter(PeriodParam.NAME).setValue(period.getValue());
		imr.getParameter(RupTopDepthParam.NAME).setValue(depth);
		table = CurveTable.create(R, dR, minM, maxM, dM, imr,
			period.getFunction());
		tables.put(period, table);
//		System.out.println(table.size());
		return table;
	}
	
	public static void main(String[] args) {
		NSHMP08_SUB_SlabGrid imr = new NSHMP08_SUB_SlabGrid();
		Period per = Period.GM0P00;
		double depth = 50;
		imr.setIntensityMeasure((per == Period.GM0P00) ? "PGA" : "SA");
		imr.getParameter(PeriodParam.NAME).setValue(per.getValue());
		imr.getParameter(RupTopDepthParam.NAME).setValue(depth);
		imr.setTable();
		
		// setting period triggers table build
		System.out.println(tables.size());
		for (Period p : tables.keySet()) {
			System.out.println(p + " " + tables.get(p));
		}
		System.out.println(tables.get(per));
		System.out.println(tables.get(per).get(10.5, 6.65));
		System.out.println(tables.get(per).get(0,5.0));
	}



}
