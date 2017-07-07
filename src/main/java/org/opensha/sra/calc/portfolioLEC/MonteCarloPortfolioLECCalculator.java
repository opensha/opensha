package org.opensha.sra.calc.portfolioLEC;

import java.util.Random;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.opensha.commons.data.function.AbstractDiscretizedFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetOutputWriter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sra.asset.Asset;
import org.opensha.sra.asset.Portfolio;
import org.opensha.sra.vulnerability.Vulnerability;
import org.opensha.sra.vulnerability.models.SimpleVulnerability;

public class MonteCarloPortfolioLECCalculator extends
AbstractPortfolioLECCalculator {
	
	private int numSimulations;
	
	public MonteCarloPortfolioLECCalculator(int numSimulations) {
		this.numSimulations = numSimulations;
	}
	
	@Override
	protected DiscretizedFunc[][] calcRuptureLECs(ScalarIMR imr, ERF erf,
			Portfolio portfolio, DiscretizedFunc function) {
		// TODO actually use the function that's passed in

		// We'll need this stuff later
		Random r1 = new Random();
		Random r2 = new Random();
		Random r3 = new Random();
		Random r4 = new Random();
		Random r5 = new Random();
		NormalDistribution normDist = new NormalDistribution();
		double sqrt2 = Math.sqrt(2);
		double oneDivN = 1d / (double)numSimulations;

		// data arrays
		double[] mValue = new double[portfolio.size()]; // v sub j bar
		double[] betaVJs = new double[portfolio.size()]; // betaVJ
		// median value
		double[] medValue = new double[portfolio.size()]; // v sub j hat
		// high value
		double[] hValue = new double[portfolio.size()]; // v sub j+
		// low value
		double[] lValue = new double[portfolio.size()]; // v sub j-

		// populate the value arrays
		//TODO: make sure that the formula is correct for betaVJ, pending hearing back from Keith
		populateValues(portfolio, mValue, betaVJs, medValue, hValue, lValue);

		DiscretizedFunc[][] funcs = new DiscretizedFunc[erf.getNumSources()][];

		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			ProbEqkSource src = erf.getSource(sourceID);
			funcs[sourceID] = new DiscretizedFunc[src.getNumRuptures()];
			for (int rupID=0; rupID<src.getNumRuptures(); rupID++) {
				ProbEqkRupture rup = src.getRupture(rupID);

				imr.setEqkRupture(rup);
				
				DiscretizedFunc lecForRup = new ArbitrarilyDiscretizedFunc();
				for (int i=0; i<function.size(); i++)
					lecForRup.set(function.getX(i), 0d);
				
				for (int n=0; n<numSimulations; n++) {
					double LsubNQ = 0; // eqn 57
					
					double u1 = r1.nextDouble();
					double u2 = r2.nextDouble();
					double u4 = r4.nextDouble();
					for (int j=0; j<portfolio.size(); j++) {
						Asset asset = portfolio.get(j);
						Vulnerability vuln = asset.getVulnerability();
						
						double dist = src.getMinDistance(asset.getSite());
						if (dist > getMaxSiteSourceDistance()) {
							continue;
						}

						// TODO: deal with setting period for SA in a better way
						String imt = vuln.getIMT();
						imr.setIntensityMeasure(imt);
						if (imt.equals(SA_Param.NAME))
							SA_Param.setPeriodInSA_Param(imr.getIntensityMeasure(), vuln.getPeriod());
						imr.setSite(asset.getSite());

						ShakingResult imrResult = calcShaking(imr);

						AbstractDiscretizedFunc covFunc = ((SimpleVulnerability)vuln).getCOVFunction();

						double deltaJ = covFunc.getInterpolatedY(imrResult.medIML);


						imr.setEqkRupture(src.getRupture(rupID));


						double u3 = r3.nextDouble();
						double u5 = r5.nextDouble();
						// eqn 54
						double vSubJNQ = medValue[j] * Math.exp(betaVJs[j]*
								normDist.inverseCumulativeProbability(u1));

						// eqn 55
						double sSubJNQ = imrResult.medIML
								* Math.exp(imrResult.interSTD*normDist.inverseCumulativeProbability(u2))
								* Math.exp(imrResult.intraSTD*normDist.inverseCumulativeProbability(u3));

						double mDamage = vuln.getMeanDamageFactor(imrResult.medIML); // y sub j bar
						// Equation 23
						double medDamage = mDamage / Math.sqrt(
								1d + deltaJ*deltaJ);

						// Equation 24
						double betaVJsubS = Math.sqrt(Math.log(1+deltaJ*deltaJ));

						// eqn 56
						double ySubJNQ = medDamage
								* Math.exp((betaVJsubS/sqrt2) * normDist.inverseCumulativeProbability(u4))
								* Math.exp((betaVJsubS/sqrt2) * normDist.inverseCumulativeProbability(u5));

						// eqn 57
						LsubNQ += vSubJNQ * ySubJNQ;
					}
					// eqn 58
//					double probLgivenQ = 1/numSimulations;
					// eqn 59
					
					for (int i=0; i<lecForRup.size(); i++) {
						double l = lecForRup.getX(i);
						double lSubNQMinusL = LsubNQ - l;
						if (lSubNQMinusL > 0)
							lecForRup.set(i, lecForRup.getY(i)+1);
					}
				}
				// eqn 60
				// multiply sums by 1/N
				for (int i=0; i<lecForRup.size(); i++) {
					lecForRup.set(i, oneDivN*lecForRup.getY(i));
				}
				funcs[sourceID][rupID] = lecForRup;
			}
		}

		return funcs;
	}

}
