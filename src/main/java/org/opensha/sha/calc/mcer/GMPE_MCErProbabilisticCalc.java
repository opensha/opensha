package org.opensha.sha.calc.mcer;

import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.calc.hazardMap.HazardCurveSetCalculator;
import org.opensha.sha.calc.hazardMap.HazardDataSetLoader;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.util.component.ComponentTranslation;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public class GMPE_MCErProbabilisticCalc extends CurveBasedMCErProbabilisitCalc {
	
	private ERF erf;
	private ScalarIMR gmpe;
	private DiscretizedFunc xVals;
	
	private ComponentTranslation converter;
	
	/**
	 * @param erf ERF to use, forecast should already be updated and be for a single year
	 * @param gmpe GMPE to use, should already have component set as appropriate
	 * @param convertToComponent if non null, will convert the hazard curve to the given component
	 * if necessary, or throw an exception if impossible (GMPE doesn't have component param or no conversion
	 * exists).
	 * @param xVals x values for each hazard calculation
	 */
	public GMPE_MCErProbabilisticCalc(ERF erf, ScalarIMR gmpe, Component convertToComponent,
			DiscretizedFunc xVals) {
		this.erf = erf;
		this.gmpe = gmpe;
		this.xVals = xVals;
		
		if (convertToComponent != null)
			converter = getComponentTranslator(gmpe, convertToComponent);
	}
	
	public void setXVals(DiscretizedFunc xVals) {
		this.xVals = xVals;
	}

	@Override
	public Map<Double, DiscretizedFunc> calcHazardCurves(Site site, Collection<Double> periods) {
		Map<Double, DiscretizedFunc> curves = Maps.newHashMap();
		
		gmpe.setIntensityMeasure(SA_Param.NAME);
		
		Preconditions.checkState(erf.getTimeSpan().getDuration(TimeSpan.YEARS) == 1d,
				"Must be 1 year forecast");
		
		for (double period : periods) {
			SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), period);
			DiscretizedFunc myXVals;
			if (converter == null) {
				myXVals = xVals.deepClone();
			} else {
				// converter scales X Values, if we want to keep original x values, must adjust before
				myXVals = new ArbitrarilyDiscretizedFunc();
				double ratio = converter.getScalingFactor(period);
				for (Point2D pt : xVals)
					myXVals.set(pt.getX()/ratio, 0d);
			}
			DiscretizedFunc hazFunction = HazardCurveSetCalculator.getLogFunction(myXVals);
			curveCalc.getHazardCurve(hazFunction, site, gmpe, erf);
			hazFunction = HazardCurveSetCalculator.unLogFunction(myXVals, hazFunction);
			
			if (converter != null) {
				hazFunction = converter.convertCurve(hazFunction, period);
			}
			
			curves.put(period, hazFunction);
		}
		
		return curves;
	}
	
	public double calcPGA_G(Site site) {
		gmpe.setIntensityMeasure(PGA_Param.NAME);
		
		DiscretizedFunc hazFunction = HazardCurveSetCalculator.getLogFunction(xVals);
		curveCalc.getHazardCurve(hazFunction, site, gmpe, erf);
		hazFunction = HazardCurveSetCalculator.unLogFunction(xVals, hazFunction);
		
		// get 2 % in 50 year value
		return HazardDataSetLoader.getCurveVal(hazFunction, false, 0.0004);
	}

}
