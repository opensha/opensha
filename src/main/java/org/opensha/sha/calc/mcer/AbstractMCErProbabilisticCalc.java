package org.opensha.sha.calc.mcer;

import java.util.Collection;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.hazardMap.HazardDataSetLoader;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.util.component.ComponentConverter;
import org.opensha.sha.util.component.ComponentTranslation;
import org.opensha.sra.rtgm.RTGM;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

public abstract class AbstractMCErProbabilisticCalc {
	
	// if positive, uniform hazard spectrum value used instead of RTGM
	protected double uhsVal = Double.NaN;
	
	protected HazardCurveCalculator curveCalc = new HazardCurveCalculator();
	static HazardCurveCalculator curveCalcForRTGM = new HazardCurveCalculator();
	
	static ComponentTranslation getComponentTranslator(ScalarIMR gmpe, Component convertToComponent) {
		// will throw exception ParameterException if no component param exists
		Component origComponent;
		try {
			origComponent = (Component) gmpe.getParameter(ComponentParam.NAME).getValue();
		} catch (ParameterException e) {
			throw new IllegalStateException("Cannot convert component as gmpe doesn't have component param", e);
		}
		if (convertToComponent != origComponent) {
			// need to translate
			Preconditions.checkState(
					ComponentConverter.isConversionSupported(origComponent, convertToComponent),
					"Cannot convert component from %s to %s", origComponent, convertToComponent);
			return ComponentConverter.getConverter(origComponent, convertToComponent);
		}
		return null;
	}
	
	/**
	 * Calculates probabilistic MCEr for the given periods
	 * @param site
	 * @param periods
	 * @return
	 */
	public abstract DiscretizedFunc calc(Site site, Collection<Double> periods);
	
	/**
	 * Calculates probabilistic MCEr for the given period
	 * @param site
	 * @param periods
	 * @return
	 */
	public double calc(Site site, double period) {
		DiscretizedFunc func = calc(site, Lists.newArrayList(period));
		int index = func.getXIndex(period);
		Preconditions.checkState(index >= 0);
		return func.getY(index);
	}
	
	private static void validateCurveForRTGM(DiscretizedFunc curve) {
		// make sure it's not empty
		Preconditions.checkState(curve.size() > 2, "curve is empty");
		// make sure it has actual values
		Preconditions.checkState(curve.getMaxY() > 0d, "curve has all zero y values");
		// make sure it is in probability space (never > 1)
		Preconditions.checkState(curve.getMaxY() <= 1d,
				"curve not in probability space. Max=%s > 1", curve.getMaxY());
		// make sure it is monotonically decreasing
		String xValStr = Iterators.toString(curve.getYValuesIterator());
		for (int j=1; j<curve.size(); j++)
			Preconditions.checkState(curve.getY(j) <= curve.getY(j-1),
				"Curve not monotonically decreasing: "+xValStr);
	}
	
	/**
	 * Calculates a RTGM value for the given hazard curve. Curve must be in probability space
	 * (will be converted to annualized rates).
	 * @param curve
	 * @return
	 */
	public static double calcRTGM(DiscretizedFunc curve) {
		validateCurveForRTGM(curve);
		// convert from annual probability to annual frequency
		curve = curveCalcForRTGM.getAnnualizedRates(curve, 1d);
		RTGM calc = RTGM.create(curve, null, null);
		try {
			calc.call();
		} catch (RuntimeException e) {
			System.err.println("RTGM Calc failed for Hazard Curve:\n"+curve);
			System.err.flush();
			throw e;
		}
		double rtgm = calc.get();
		Preconditions.checkState(rtgm > 0, "RTGM is not positive");
		return rtgm;
	}
	
	/**
	 * Use uniform hazard spectrum instead of RTGM (if uhsVal > 0)
	 * @param uhsVal
	 */
	public void setUseUHS(double uhsVal) {
		this.uhsVal = uhsVal;
	}

}
