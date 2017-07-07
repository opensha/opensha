package org.opensha.sha.calc.mcer;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.opensha.commons.data.Site;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.util.component.ComponentTranslation;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public class GMPE_MCErDeterministicCalc extends AbstractMCErDeterministicCalc {
	
	private ERF erf;
	private ScalarIMR gmpe;
	
	private ComponentTranslation converter;
	
	private double cutoffDist = 200d;
	
	public GMPE_MCErDeterministicCalc(ERF erf, ScalarIMR gmpe, Component convertToComponent) {
		this.erf = erf;
		this.gmpe = gmpe;
		
		if (convertToComponent != null)
			converter = AbstractMCErProbabilisticCalc.getComponentTranslator(gmpe, convertToComponent);
	}

	@Override
	public Map<Double, DeterministicResult> calc(Site site, Collection<Double> periods) {
		gmpe.setSite(site);
		
		Map<Double, DeterministicResult> result = Maps.newHashMap();
		
		gmpe.setIntensityMeasure(SA_Param.NAME);
		
		for (double period : periods) {
			SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), period);
			DeterministicResult maxVal = doCalc(site);
			Preconditions.checkNotNull(maxVal);
			if (converter != null)
				maxVal.setVal(converter.getScaledValue(maxVal.getVal(), period));
			
			result.put(period, maxVal);
		}
		
		return result;
	}

	private DeterministicResult doCalc(Site site) {
		// assumes Site and IMT have been set
		DeterministicResult maxVal = null;
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			if (source.getMinDistance(site) > cutoffDist)
				continue;
			for (int rupID=0; rupID<source.getNumRuptures(); rupID++) {
				ProbEqkRupture rup = source.getRupture(rupID);
				if (rup.getProbability() == 0d)
					continue;
				gmpe.setEqkRupture(rup);
				double logMean = gmpe.getMean();
				double stdDev = gmpe.getStdDev();
				NormalDistribution norm = new NormalDistribution(logMean, stdDev);
				double val = Math.exp(norm.inverseCumulativeProbability(percentile/100d));
				if (maxVal == null || val > maxVal.getVal()) {
					maxVal = new DeterministicResult(
							sourceID, rupID, rup.getMag(), source.getName(), val);
				}
			}
		}
		return maxVal;
	}
	
	public DeterministicResult calcPGA_G(Site site) {
		gmpe.setSite(site);
		
		gmpe.setIntensityMeasure(PGA_Param.NAME);
		
		DeterministicResult maxVal = doCalc(site);
		// do not convert component
		Preconditions.checkNotNull(maxVal);
		
		return maxVal;
	}

}
