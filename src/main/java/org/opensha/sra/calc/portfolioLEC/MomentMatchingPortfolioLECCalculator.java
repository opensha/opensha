package org.opensha.sra.calc.portfolioLEC;

import java.util.ArrayList;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.opensha.commons.data.function.AbstractDiscretizedFunc;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sra.asset.Asset;
import org.opensha.sra.asset.Portfolio;
import org.opensha.sra.vulnerability.Vulnerability;
import org.opensha.sra.vulnerability.models.SimpleVulnerability;

/**
 * Portfolio loss calculator as described in:
 * 
 * "Portfolio Scenario Loss and Loss Exceedance Curve Calculator using Moment Matching"
 * by Keith Porter, et al. 2010
 * 
 * and discussed by Keith Porter, Ned Field, Peter Powers, and Kevin Milner in Golden, CO
 * April 2010.
 * 
 * Revisions made to match new version of document from 10/21/2010.
 *
 * 
 * @author Peter Powers, Kevin Milner
 * @version $Id: MomentMatchingPortfolioLECCalculator.java 10931 2015-01-27 22:04:06Z kmilner $
 */
public class MomentMatchingPortfolioLECCalculator extends AbstractPortfolioLECCalculator {
	
	protected DiscretizedFunc[][] calcRuptureLECs(ScalarIMR imr,
			ERF erf, Portfolio portfolio, DiscretizedFunc function) {
		PortfolioRuptureResults[][] rupResults = calcRuptureResults(imr, erf, portfolio, function);
		
		return RupResultsToFuncArray(rupResults);
	}
	
	protected static DiscretizedFunc[][] RupResultsToFuncArray(PortfolioRuptureResults[][] rupResults) {
		DiscretizedFunc[][] funcs = new DiscretizedFunc[rupResults.length][];
		for (int sourceID=0; sourceID<rupResults.length; sourceID++) {
			funcs[sourceID] = new DiscretizedFunc[rupResults[sourceID].length];
			for (int rupID=0; rupID<rupResults[sourceID].length; rupID++) {
				funcs[sourceID][rupID] = rupResults[sourceID][rupID].getExceedanceProbs();
			}
		}
		return funcs;
	}
	
	protected PortfolioRuptureResults[][] calcRuptureResults(
			ScalarIMR imr,
			ERF erf,
			Portfolio portfolio,
			DiscretizedFunc function) {
		// data arrays
		int n = portfolio.size();
		// mean value
		double[] mValue = new double[n]; // v sub j bar
		double[] betaVJs = new double[n]; // betaVJ
		// median value
		double[] medValue = new double[n]; // v sub j bar
		// high value
		double[] hValue = new double[n]; // v sub j+
		// low value
		double[] lValue = new double[n]; // v sub j-
//		// mean damage for mean IML
//		double[] mDamage_mIML = new double[n]; // y sub j bar
//		// high damage for mean IML
//		double[] hDamage_mIML = new double[n]; // y sub j+
//		// low damage for mean IML
//		double[] lDamage_mIML = new double[n]; // y sub j-
//		// mean damage ...
//		double[] mShaking = new double[n]; // s sub j bar
//		double[] mDamage_hInter = new double[n]; // s sub +t
//		double[] mDamage_lInter = new double[n]; // s sub -t
//		double[] mDamage_hIntra = new double[n]; // s sub +p
//		double[] mDamage_lIntra = new double[n]; // s sub -p
		
		// Equation 15
		double w0 = 1d - (6d + 4d*portfolio.size())/8d;
		if (D) System.out.println("w0: " + w0 + " (eqn 15)");
		double wi = 1d / 8d;
		if (D) System.out.println("wi: " + wi + " (eqn 15)");
		if (D) System.out.println("");
		
		// populate the value arrays
		populateValues(portfolio, mValue, betaVJs, medValue, hValue, lValue);
		// ---
		
		// std dev tests
		
		// loop over sources
		
		PortfolioRuptureResults[][] rupResults = new PortfolioRuptureResults[erf.getNumSources()][];
		
		// used later
		NormalDistribution normDist = new NormalDistribution();
		
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			Boolean[] sourceIncludes = new Boolean[n];
			
			ProbEqkSource src = erf.getSource(sourceID);
			
			rupResults[sourceID] = new PortfolioRuptureResults[src.getNumRuptures()];
			
			for (int rupID=0; rupID<src.getNumRuptures(); rupID++) {
				
				if (D) System.out.println("");
				if (D) System.out.println("src: " + sourceID + " rup: " + rupID
						+ " prob: " + erf.getRupture(sourceID, rupID).getProbability());
				
				ArrayList<AssetRuptureResult> assetRupResults = new ArrayList<AssetRuptureResult>();
				for (int k=0; k<portfolio.size(); k++) {
					
					if (D) System.out.println("Asset " + k);
					
					Asset asset = portfolio.get(k);
					
					if (sourceIncludes[k] == null) {
						double dist = src.getMinDistance(asset.getSite());
						sourceIncludes[k] = dist < getMaxSiteSourceDistance();
					}
					if (!sourceIncludes[k]) {
						assetRupResults.add(null);
						continue;
					}
					
					Vulnerability vuln = asset.getVulnerability();
					
					// TODO: deal with setting period for SA in a better way
					String imt = vuln.getIMT();
					imr.setIntensityMeasure(imt);
					if (imt.equals(SA_Param.NAME))
						SA_Param.setPeriodInSA_Param(imr.getIntensityMeasure(), vuln.getPeriod());
					imr.setIntensityMeasure(imt);
					if (imt.equals(SA_Param.NAME))
						SA_Param.setPeriodInSA_Param(imr.getIntensityMeasure(), vuln.getPeriod());
					imr.setSite(asset.getSite());
					imr.setEqkRupture(src.getRupture(rupID));
					
					ShakingResult imrResult = calcShaking(imr);
					
					if (D) {
						AbstractDiscretizedFunc vulnFunc = vuln.getVulnerabilityFunc();
						System.out.println("Vulnerability Function:\n"+vulnFunc);
						if (vuln instanceof SimpleVulnerability)
							System.out.println("COV Function:\n"+((SimpleVulnerability)vuln).getCOVFunction());
					}
					
					double mDamage_medIML = vuln.getMeanDamageFactor(imrResult.medIML); // y sub j bar
					if (D) System.out.println("mDamage_mIML: " + mDamage_medIML + " (y sub j bar)");
					
					AbstractDiscretizedFunc covFunc = ((SimpleVulnerability)vuln).getCOVFunction();
					
					double deltaJ_medIML = covFunc.getInterpolatedY(imrResult.medIML);
					
					// Equation 23
					double medDamage_medIML = mDamage_medIML / Math.sqrt(
							1d + deltaJ_medIML*deltaJ_medIML);
					
					double hDamage_medIML = vuln.getMeanDamageAtExceedProb(imrResult.medIML, 0.086); // y sub j+
					if (D) System.out.println("hDamage_mIML: " + hDamage_medIML + " (y sub j+)");
					double lDamage_medIML = vuln.getMeanDamageAtExceedProb(imrResult.medIML, 0.914); // y sub j-
					if (D) System.out.println("lDamage_mIML: " + lDamage_medIML + " (y sub j+)");
					
					// TODO doublecheck log-space consistency for vulnerability
					// vuln not log space so what is mean?
					
					// Equation 18
					// e^(mIML+1.932*interStd)  97th %ile
					//
					// 1.932 is the inverse of the cum. std. norm. dist. at 97%
					
					double interVal = 1.932 * imrResult.interSTD;
					double imlHighInter = imrResult.medIML * Math.exp(interVal);
					if (D) System.out.println("imlHighInter: " + imlHighInter);
					double imlLowInter = imrResult.medIML * Math.exp(-1d*interVal);
					if (D) System.out.println("imlLowInter: " + imlLowInter);
					double mDamage_hInter = vuln.getMeanDamageFactor(imlHighInter); // s sub +t
					if (D) System.out.println("mDamage_hInter: " + mDamage_hInter + " (s sub +t) (eqn 18)");
					double mDamage_lInter = vuln.getMeanDamageFactor(imlLowInter);  // s sub -t
					if (D) System.out.println("mDamage_lInter: " + mDamage_lInter + " (s sub -t) (eqn 18)");
					
					double deltaJ_imlHighInter = covFunc.getInterpolatedY(imlHighInter);
					double deltaJ_imlLowInter = covFunc.getInterpolatedY(imlLowInter);
					
					double medDamage_hInter = mDamage_hInter / Math.sqrt(
							1d + deltaJ_imlHighInter*deltaJ_imlHighInter);
					double medDamage_lInter = mDamage_lInter / Math.sqrt(
							1d + deltaJ_imlLowInter*deltaJ_imlLowInter);
					
					double intraVal = 1.932 * imrResult.intraSTD;
					double imlHighIntra = imrResult.medIML * Math.exp(intraVal);
					double imlLowIntra = imrResult.medIML * Math.exp(-1d*intraVal);
					double mDamage_hIntra = vuln.getMeanDamageFactor(imlHighIntra); // s sub +p
					if (D) System.out.println("mDamage_hIntra: " + mDamage_hIntra + " (s sub +p) (eqn 18)");
					double mDamage_lIntra = vuln.getMeanDamageFactor(imlLowIntra);  // s sub -p
					if (D) System.out.println("mDamage_lIntra: " + mDamage_lIntra + " (s sub +p) (eqn 18)");
					
					double deltaJ_imlHighIntra = covFunc.getInterpolatedY(imlHighIntra);
					double deltaJ_imlLowIntra = covFunc.getInterpolatedY(imlLowIntra);
					
					double medDamage_hIntra = mDamage_hIntra / Math.sqrt(
							1d + deltaJ_imlHighIntra*deltaJ_imlHighIntra);
					double medDamage_lIntra = mDamage_lIntra / Math.sqrt(
							1d + deltaJ_imlLowIntra*deltaJ_imlLowIntra);

					
					AssetRuptureResult assetRupResult = new AssetRuptureResult(imrResult.medIML, imrResult.mLnIML, imrResult.interSTD, imrResult.intraSTD,
							mDamage_medIML, deltaJ_medIML, medDamage_medIML, hDamage_medIML, lDamage_medIML, imrResult.medIML,
							imlHighInter, imlLowInter, mDamage_hInter, mDamage_lInter, deltaJ_imlHighInter, deltaJ_imlLowInter, medDamage_hInter, medDamage_lInter,
							imlHighIntra, imlLowIntra, mDamage_hIntra, mDamage_lIntra, deltaJ_imlHighIntra, deltaJ_imlLowIntra, medDamage_hIntra, medDamage_lIntra,
							mValue[k], betaVJs[k], medValue[k], hValue[k], lValue[k]);
					assetRupResults.add(assetRupResult);
				}
				
//				int numSamples = 8 + 4*portfolio.size();
				int numSamples = 7;
				double[][] l_indv = new double[portfolio.size()][numSamples];
				double[][] lSquared_indv = new double[portfolio.size()][numSamples];
				double[] l = new double[numSamples];
				double[] lSquared = new double[numSamples];
				// init arrays to 0
				for (int i=0; i<numSamples; i++) {
					l[i] = 0;
					lSquared[i] = 0;
				}
				// now we combine everything
				for (int i=0; i<portfolio.size(); i++) {
					AssetRuptureResult assetRupResult = assetRupResults.get(i);
					
					if (assetRupResult == null)
						continue;
					
					double tempVal;
					
					if (D) System.out.println("Asset " + i + " (showing intermediate sums for L's)");
					
					// Equation 30
					tempVal = medValue[i] * assetRupResult.getMedDamage_medIML();
					l_indv[i][0] = tempVal;
					l[0] += tempVal;
					lSquared[0] += tempVal * tempVal;
					if (D) System.out.println("L[0]: " + l[0] + " (eqn 24)");
					if (D) System.out.println("L^2[0]: " + lSquared[0] + " (eqn 24)");
					
					// Equation 31
					tempVal = medValue[i] * assetRupResult.getMedDamage_hInter();
					l_indv[i][1] = tempVal;
					l[1] += tempVal;
					lSquared[1] += tempVal * tempVal;
					if (D) System.out.println("L[1]: " + l[1] + " (eqn 25)");
					if (D) System.out.println("L^2[1]: " + lSquared[1] + " (eqn 25)");
					
					// Equation 32
					tempVal = medValue[i] * assetRupResult.getMedDamage_lInter();
					l_indv[i][2] = tempVal;
					l[2] += tempVal;
					lSquared[2] += tempVal * tempVal;
					if (D) System.out.println("L[2]: " + l[2] + " (eqn 26)");
					if (D) System.out.println("L^2[2]: " + lSquared[2] + " (eqn 26)");
					
					// Equation 33
					tempVal = hValue[i] * assetRupResult.getMedDamage_medIML();
					l_indv[i][3] = tempVal;
					l[3] += tempVal;
					lSquared[3] += tempVal * tempVal;
					if (D) System.out.println("L[3]: " + l[3] + " (eqn 27)");
					if (D) System.out.println("L^2[3]: " + lSquared[3] + " (eqn 27)");
					
					// Equation 34
					tempVal = lValue[i] * assetRupResult.getMedDamage_medIML();
					l_indv[i][4] = tempVal;
					l[4] += tempVal;
					lSquared[4] += tempVal * tempVal;
					if (D) System.out.println("L[4]: " + l[4] + " (eqn 28)");
					if (D) System.out.println("L^2[4]: " + lSquared[4] + " (eqn 28)");
					
					// Equation 35
					tempVal = medValue[i] * assetRupResult.getHDamage_medIML();
					l_indv[i][5] = tempVal;
					l[5] += tempVal;
					lSquared[5] += tempVal * tempVal;
					if (D) System.out.println("L[5]: " + l[5] + " (eqn 29)");
					if (D) System.out.println("L^2[5]: " + lSquared[5] + " (eqn 29)");
					
					// Equation 36
					tempVal = medValue[i] * assetRupResult.getLDamage_medIML();
					l_indv[i][6] = tempVal;
					l[6] += tempVal;
					lSquared[6] += tempVal * tempVal;
					if (D) System.out.println("L[6]: " + l[6] + " (eqn 30)");
					if (D) System.out.println("L^2[6]: " + lSquared[6] + " (eqn 30)");
				}
				
				// all this is for Equation 43
				double sumReg = 0;
				double sumSquares = 0;
				for (int i=0; i<portfolio.size(); i++) {
					AssetRuptureResult assetRupResult = assetRupResults.get(i);
					if (assetRupResult == null)
						continue;
					double medDamage_mIML = assetRupResult.getMedDamage_medIML();
					double medDamage_hIntra = assetRupResult.getMedDamage_hIntra();
					double medDamage_lIntra = assetRupResult.getMedDamage_lIntra();
					// vBar ( yBar ( s sub +p ) + yBar ( s sub -p))
					sumReg += medValue[i] * ( medDamage_hIntra + medDamage_lIntra );
					sumSquares += Math.pow(medValue[i] * medDamage_hIntra, 2)
										+ Math.pow(medValue[i] * medDamage_lIntra, 2);
				}
				double e_LgivenS = w0 * l[0] + wi * (l[1] + l[2] + l[3] + l[4] + 2*l[5] + 2*l[6]
									+ (4*portfolio.size() - 4)*l[0] + sumReg);
				double e_LSuqaredGivenS = w0 * lSquared[0] + wi * (lSquared[1] + lSquared[2] + lSquared[3] + lSquared[4]
									+ 2*lSquared[5] + 2*lSquared[6] + (4*portfolio.size() - 4)*lSquared[0] + sumSquares);
				if (D) System.out.println("e_LgivenS: " + e_LgivenS + " (eqn 43)");
				if (D) System.out.println("e_LSuqaredGivenS: " + e_LSuqaredGivenS + " (eqn 43)");
				
				// Equation 14
				double varLgivenS = e_LSuqaredGivenS - e_LgivenS * e_LgivenS;
				if (D) System.out.println("varLgivenS: " + varLgivenS + " (eqn 14)");
				
				// Eqaution 28
				double deltaSquaredSubLgivenS = varLgivenS / (e_LgivenS * e_LgivenS);
				if (D) System.out.println("deltaSquaredSubLgivenS: " + deltaSquaredSubLgivenS + " (eqn 28)");
				
				// Equation 27
				double thetaSubLgivenS = e_LgivenS / Math.sqrt(1d + deltaSquaredSubLgivenS);
				if (D) System.out.println("thetaSubLgivenS: " + thetaSubLgivenS + " (eqn 27)");
				
				// Equation 29
				double betaSubLgivenS = Math.sqrt(Math.log(1d + deltaSquaredSubLgivenS));
				if (D) System.out.println("betaSubLgivenS: " + betaSubLgivenS + " (eqn 29)");
				
				// Equation 45
				double sumMeanValues = 0;
				for (int i=0; i<portfolio.size(); i++) {
					sumMeanValues += mValue[i];
				}
				if (D) System.out.println("sumMeanValues: " + sumMeanValues + " (eqn 45)");
				
				// Equation 44
				ArbDiscrEmpiricalDistFunc distFunc = new ArbDiscrEmpiricalDistFunc();
				for (int k=0; k<function.size(); k++) {
//					double x = Math.pow(10d, -5d + 0.1 * k);
					double x = function.getX(k);
					double inside = Math.log(x * sumMeanValues / thetaSubLgivenS) / betaSubLgivenS;
					distFunc.set(x, inside);
				}
				if (D) System.out.println("distFunc: (part of eqn 44)\n" + distFunc);
				ArbitrarilyDiscretizedFunc exceedanceProbs = new ArbitrarilyDiscretizedFunc();
				for (int k=0; k<distFunc.size(); k++) {
					double x = distFunc.getX(k);
					double y = distFunc.getY(k);
					double val = normDist.cumulativeProbability(y);
					exceedanceProbs.set(x, 1-val);
				}
				
				if (D) System.out.println("exceedanceProbs: (eqn 44)\n" + exceedanceProbs);
				
				
				PortfolioRuptureResults rupResult =
					new PortfolioRuptureResults(assetRupResults, l, lSquared, l_indv, exceedanceProbs,
							w0, wi, e_LgivenS, e_LSuqaredGivenS, varLgivenS, deltaSquaredSubLgivenS,
							thetaSubLgivenS, betaSubLgivenS);
				rupResults[sourceID][rupID] = rupResult;
			}
		}
		
			// loop over ruptures
				// loop over Assets
					
					// compute mean, intra-, and inter-event
					// std dev from IMR
		
					// compute damage factor arrays
					//   - mDamage_mIML
					//   - hDamage_mIML
					//   - lDamage_mIML
					//   - mDamage_hInter
					//   - mDamage_lInter
					//   - mDamage_hIntra
					//   - mDamage_lIntra
					
					// do simulations
					// store 
		
		return rupResults;
	}
	
	private static String getArrayStr(double[] array) {
		String str = null;
		
		for (double val : array) {
			if (str == null)
				str = "";
			else
				str += ",";
			str += val;
		}
		
		return str;
	}
	
//	private static void printAssetRupVals(ArrayList<AssetRuptureResult> assetRupResults, int assetNum) {
//		String[] lines = null;
//		for (AssetRuptureResult res: assetRupResults) {
//			if (lines == null) {
//				lines = new String[19];
//				for (int i=0; i<lines.length; i++) {
//					lines[i] = "";
//				}
//			} else {
//				for (String line : lines) {
//					line += ",";
//				}
//			}
//			lines[0] += res.getMLnIML();
//			lines[1] += res.getInterSTD();
//			lines[2] += res.getIntraSTD();
//			lines[3] += res.getMIML();
//			lines[4] += "";
//			lines[5] += res.getMDamage_mIML();
//			lines[6] += "";
//			lines[7] += "";
//			lines[8] += "";
//			lines[9] += res.getHDamage_mIML();
//			lines[10] += res.getLDamage_mIML();
//			lines[11] += res.getIML_hInter();
//			lines[12] += res.getIML_lInter();
//			lines[13] += res.getIML_hIntra();
//			lines[14] += res.getIML_lIntra();
//			lines[15] += res.getMDamage_hInter();
//			lines[16] += res.getMDamage_lInter();
//			lines[17] += res.getMDamage_hIntra();
//			lines[18] += res.getMDamage_lIntra();
//		}
//		for (String line : lines) {
//			System.out.println(line);
//		}
//	}
}

