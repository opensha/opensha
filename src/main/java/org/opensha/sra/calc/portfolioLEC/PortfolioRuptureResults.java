package org.opensha.sra.calc.portfolioLEC;

import java.util.ArrayList;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;

public class PortfolioRuptureResults {
	
	private ArrayList<AssetRuptureResult> assetRupResults;
	private double[] l;
	private double[] lSquared;
	private double[][] l_indv;
	private ArbitrarilyDiscretizedFunc exceedanceProbs;
	
	double w0, wi, e_LgivenS, e_LSuqaredGivenS, varLgivenS, deltaSquaredSubLgivenS,
			thetaSubLgivenS, betaSubLgivenS;
	
	public PortfolioRuptureResults(
			ArrayList<AssetRuptureResult> assetRupResults,
			double[] l, double[] lSquared, double[][] l_indv,
			ArbitrarilyDiscretizedFunc exceedanceProbs,
			double w0, double wi, double e_LgivenS, double e_LSuqaredGivenS,
			double varLgivenS, double deltaSquaredSubLgivenS,
			double thetaSubLgivenS, double betaSubLgivenS) {
		super();
		this.assetRupResults = assetRupResults;
		this.l = l;
		this.lSquared = lSquared;
		this.l_indv = l_indv;
		this.exceedanceProbs = exceedanceProbs;
		this.w0 = w0;
		this.wi = wi;
		this.e_LgivenS = e_LgivenS;
		this.e_LSuqaredGivenS = e_LSuqaredGivenS;
		this.varLgivenS = varLgivenS;
		this.deltaSquaredSubLgivenS = deltaSquaredSubLgivenS;
		this.thetaSubLgivenS = thetaSubLgivenS;
		this.betaSubLgivenS = betaSubLgivenS;
	}

	public ArrayList<AssetRuptureResult> getAssetRupResults() {
		return assetRupResults;
	}

	public double[] getL() {
		return l;
	}

	public double[] getLSquared() {
		return lSquared;
	}

	public double[][] getL_indv() {
		return l_indv;
	}

	public ArbitrarilyDiscretizedFunc getExceedanceProbs() {
		return exceedanceProbs;
	}

	public double getW0() {
		return w0;
	}

	public double getWi() {
		return wi;
	}

	public double getE_LgivenS() {
		return e_LgivenS;
	}

	public double getE_LSuqaredGivenS() {
		return e_LSuqaredGivenS;
	}

	public double getVarLgivenS() {
		return varLgivenS;
	}

	public double getDeltaSquaredSubLgivenS() {
		return deltaSquaredSubLgivenS;
	}

	public double getThetaSubLgivenS() {
		return thetaSubLgivenS;
	}

	public double getBetaSubLgivenS() {
		return betaSubLgivenS;
	}

}
