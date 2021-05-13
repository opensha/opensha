package org.opensha.sra.calc.portfolioLEC;

import static com.google.common.base.Preconditions.*;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sra.asset.Asset;
import org.opensha.sra.asset.MonetaryHighLowValue;
import org.opensha.sra.asset.MonetaryValue;
import org.opensha.sra.asset.Portfolio;
import org.opensha.sra.asset.Value;

public abstract class AbstractPortfolioLECCalculator {

	public static final boolean D = false;
	
	protected abstract DiscretizedFunc[][] calcRuptureLECs(ScalarIMR imr,
			ERF erf, Portfolio portfolio, DiscretizedFunc function);
	
	protected void calcProbabilityOfExceedanceCurve(
			DiscretizedFunc[][] funcs, ERF erf, DiscretizedFunc function) {
		
		if (D) System.out.println("Creating final curve");
		for (int k=0; k<function.size(); k++) {
			double x = function.getX(k);
			if (D) System.out.println("iml: " + x);
			
			double product = 1;
			for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
				ProbEqkSource src = erf.getSource(sourceID);
				for (int rupID=0; rupID<src.getNumRuptures(); rupID++) {
					ProbEqkRupture rup = src.getRupture(rupID);
					
					DiscretizedFunc normCumDist = funcs[sourceID][rupID];
					
					if (normCumDist == null)
						// skipping this rupture
						continue;
					
					double rupProb = rup.getProbability();
					
					if (D) System.out.println("src: " + sourceID + " rup: " + rupID + " prob: " + rupProb);
					
					double normCumDistVal = normCumDist.getY(k);
					if (D) System.out.println("normCumDifunctionst[iml]: " + normCumDistVal);
					if (Double.isNaN(normCumDistVal)) {
						if (D) System.out.println("it's NaN, skipping");
						continue;
					}
					
					// part of equation 47
					product *= 1 - rupProb * normCumDist.getY(k);
				}
			}
			// 1 - product (part of eqn 47)
			function.set(x, 1-product);
		}
	}

	public void calcProbabilityOfExceedanceCurve(ScalarIMR imr,
			ERF erf, Portfolio portfolio, DiscretizedFunc function) {
		DiscretizedFunc[][] funcs = calcRuptureLECs(imr, erf, portfolio, function);
		
		calcProbabilityOfExceedanceCurve(funcs, erf, function);
	}

	public void calcFrequencyOfExceedanceCurve(ScalarIMR imr,
			ERF erf, Portfolio portfolio, DiscretizedFunc function) {
		// TODO implement
		throw new UnsupportedOperationException("not yet implemented");
	}
	
	protected final double getMaxSiteSourceDistance() {
		return 200d;
	}

	protected static double valueCoefficientOfVariation = 0.15;
	protected static double interEventFactor = 0.25;

	public AbstractPortfolioLECCalculator() {
		super();
	}
	
	protected void populateValues(Portfolio portfolio, double[] mValue,
			double[] betaVJs, double[] medValue, double[] hValue, double[] lValue) {
		checkNotNull(portfolio, "portfolio can't be null");
		checkState(portfolio.size() > 0, "portfolio can't be empty");
		checkNotNull(mValue, "mValue array can't be null");
		checkState(mValue.length == portfolio.size(), "mValue array is of incorrect size");
		checkNotNull(betaVJs, "betaVJs array can't be null");
		checkState(betaVJs.length == portfolio.size(), "betaVJs array is of incorrect size");
		checkNotNull(medValue, "medValue array can't be null");
		checkState(medValue.length == portfolio.size(), "medValue array is of incorrect size");
		checkNotNull(mValue, "mValue array can't be null");
		checkState(hValue.length == portfolio.size(), "hValue array is of incorrect size");
		checkNotNull(hValue, "hValue array can't be null");
		checkState(lValue.length == portfolio.size(), "lValue array is of incorrect size");
		
		for (int i=0; i<portfolio.size(); i++) {
			Asset asset = portfolio.get(i);
			Value value = asset.getValue();
			if (D) System.out.println("Asset " + i);
			
			if (value instanceof MonetaryValue) {
				MonetaryValue mvalue = (MonetaryValue)asset.getValue();
				
				double meanValue = mvalue.getValue();
				double betaVJ;
				double medianValue;
				if (D) System.out.println("meanValue: " + meanValue + " (v sub jbar)");
				double highValue, lowValue;
				if (mvalue instanceof MonetaryHighLowValue) {
					if (D) System.out.println("Asset already has high/low vals");
					MonetaryHighLowValue hlmValue = (MonetaryHighLowValue) mvalue;
					highValue = hlmValue.getHighValue();
					lowValue = hlmValue.getLowValue();
					
					betaVJ = Math.log(highValue/lowValue) / (2*1.932);
					medianValue = meanValue / Math.sqrt(Math.exp(betaVJ*betaVJ));
					
					if (D) {
						System.out.println("highValue: " + highValue + " (v sub j+) (from asset)");
						System.out.println("lowValue: " + lowValue + " (v sub j-) (from asset)");
					}
				} else {
					if (D) System.out.println("calculating high/low vals");
					// if high/low value isn't given, we need to calculate it from mean and COV
					// Equation 21
					betaVJ = Math.sqrt(Math.log(1d+valueCoefficientOfVariation*valueCoefficientOfVariation));
					medianValue = meanValue / Math.sqrt(Math.exp(betaVJ*betaVJ));
					// Equation 22
					highValue = medianValue * Math.exp(betaVJ*1.932);
					lowValue = medianValue * Math.exp(-betaVJ*1.932);
					if (D) {
						System.out.println("highValue: " + highValue + " (v sub j+) (eqn 16)");
						System.out.println("lowValue: " + lowValue + " (v sub j-) (eqn 16)");
					}
				}
				
				mValue[i] = meanValue;
				betaVJs[i] = betaVJ;
				medValue[i] = medianValue;
				hValue[i] = highValue;
				lValue[i] = lowValue;
			} else {
				throw new RuntimeException("Value must be of type MonetaryValue");
			}
		}
	}
	
	protected ShakingResult calcShaking(ScalarIMR imr) {
		double intraSTD, interSTD;
		
		double mLnIML = imr.getMean();
		if (D) System.out.println("mLnIML: " + mLnIML);
		Parameter<String> stdParam = imr.getParameter(StdDevTypeParam.NAME);
		stdParam.setValue(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
		double std = imr.getStdDev();
		if (D) System.out.println("ln STD: " + std);
		
		if (stdParam.isAllowed(StdDevTypeParam.STD_DEV_TYPE_INTRA)) {
			stdParam.setValue(StdDevTypeParam.STD_DEV_TYPE_INTRA);
			intraSTD = imr.getStdDev();
			stdParam.setValue(StdDevTypeParam.STD_DEV_TYPE_INTER);
			interSTD = imr.getStdDev();
			if (D) System.out.println("interStd: " + interSTD + " (from IMR)");
			if (D) System.out.println("intraStd: " + intraSTD + " (from IMR)");
		} else {
			if (D) System.out.println("IMR doesn't support inter/intra std, we hae to calculate");
			if (D) System.out.println("interEventFactor: " + interEventFactor);
			interSTD = interEventFactor*std; // Equation 10
			intraSTD = Math.sqrt(std*std-interSTD*interSTD); // Equation 11
			if (D) System.out.println("interStd: " + interSTD + " (eqn 10)");
			if (D) System.out.println("intraStd: " + intraSTD + " (eqn 11)");
		}
		
		// TODO K. Porter explain 11th and 89th
		// e^(mIML + 0.5 * std * std)
//		double mIML = Math.exp(mLnIML + 0.5 * std * std); // Equation 20, mean shaking real domain
		double medIML = Math.exp(mLnIML);
		if (D) System.out.println("medIML: " + medIML + " (s sub j hat) (eqn 20)");
		
		return new ShakingResult(mLnIML, medIML, interSTD, intraSTD);
	}
	
	protected class ShakingResult {
		
		protected final double mLnIML, medIML, interSTD, intraSTD;
		private ShakingResult(double mLnIML, double medIML, double interSTD, double intraSTD) {
			this.mLnIML = mLnIML;
			this.medIML = medIML;
			this.interSTD = interSTD;
			this.intraSTD = intraSTD;
		}
	}
	
	protected double calcMedDamage(double meanDamage, double deltaJ) {
		// Equation 23
		return meanDamage / Math.sqrt(
				1d + deltaJ*deltaJ);
	}

}