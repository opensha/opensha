package org.opensha.sra.calc.portfolioLEC;

public class AssetRuptureResult {
	
	private double medIML;
	private double mLnIML;
	private double interSTD;
	private double intraSTD;
//	// mean damage for mean IML
	private double mDamage_medIML; // y sub j bar
	private double deltaJ_medIML;
	private double medDamage_medIML; // y hat sub j bar
//	// high damage for mean IML
	private double hDamage_medIML; // y sub j+
//	// low damage for mean IML
	private double lDamage_medIML; // y sub j-
//	// mean damage ...
	private double mShaking; // s sub j bar
	private double imlHighInter;
	private double imlLowInter;
	private double mDamage_hInter; // s sub +t
	private double mDamage_lInter; // s sub -t
	private double deltaJ_imlHighInter;
	private double deltaJ_imlLowInter;
	private double medDamage_hInter; // s hat sub +t
	private double medDamage_lInter; // s hat sub -t
	private double imlHighIntra;
	private double imlLowIntra;
	private double mDamage_hIntra; // s sub +p
	private double mDamage_lIntra; // s sub -p
	private double deltaJ_imlHighIntra;
	private double deltaJ_imlLowIntra;
	private double medDamage_hIntra; // s hat sub +p
	private double medDamage_lIntra; // s hat sub -p
	
	private double mValue;
	private double betaVJ;
	private double medValue;
	private double hValue;
	private double lValue;
	
	public AssetRuptureResult(double medIML, double mLnIML, double interSTD, double intraSTD,
			double mDamage_medIML, double deltaJ_medIML, double medDamage_medIML,
			double hDamage_mIML, double lDamage_mIML, double mShaking,
			double imlHighInter, double imlLowInter, double mDamage_hInter, double mDamage_lInter,
			double deltaJ_imlHighInter, double deltaJ_imlLowInter, double medDamage_hInter, double medDamage_lInter,
			double imlHighIntra, double imlLowIntra, double mDamage_hIntra, double mDamage_lIntra,
			double deltaJ_imlHighIntra, double deltaJ_imlLowIntra, double medDamage_hIntra, double medDamage_lIntra,
			double mValue, double betaVJ, double medValue, double hValue, double lValue) {
		super();
		this.medIML = medIML;
		this.mLnIML = mLnIML;
		this.interSTD = interSTD;
		this.intraSTD = intraSTD;
		this.mDamage_medIML = mDamage_medIML;
		this.medDamage_medIML = medDamage_medIML;
		this.hDamage_medIML = hDamage_mIML;
		this.lDamage_medIML = lDamage_mIML;
		this.mShaking = mShaking;
		this.imlHighInter = imlHighInter;
		this.imlLowInter = imlLowInter;
		this.mDamage_hInter = mDamage_hInter;
		this.mDamage_lInter = mDamage_lInter;
		this.medDamage_hInter = medDamage_hInter;
		this.medDamage_lInter = medDamage_lInter;
		this.imlHighIntra = imlHighIntra;
		this.imlLowIntra = imlLowIntra;
		this.mDamage_hIntra = mDamage_hIntra;
		this.mDamage_lIntra = mDamage_lIntra;
		this.medDamage_hIntra = medDamage_hIntra;
		this.medDamage_lIntra = medDamage_lIntra;
		
		this.mValue = mValue;
		this.betaVJ = betaVJ;
		this.medValue = medValue;
		this.hValue = hValue;
		this.lValue = lValue;
		
		this.deltaJ_medIML = deltaJ_medIML;
		this.deltaJ_imlHighInter = deltaJ_imlHighInter;
		this.deltaJ_imlLowInter = deltaJ_imlLowInter;
		this.deltaJ_imlHighIntra = deltaJ_imlHighIntra;
		this.deltaJ_imlLowIntra = deltaJ_imlLowIntra;
	}
	public double getMedIML() {
		return medIML;
	}
	public double getMLnIML() {
		return mLnIML;
	}
	public double getInterSTD() {
		return interSTD;
	}
	public double getIntraSTD() {
		return intraSTD;
	}
	public double getMedDamage_medIML() {
		return medDamage_medIML;
	}
	public double getHDamage_medIML() {
		return hDamage_medIML;
	}
	public double getLDamage_medIML() {
		return lDamage_medIML;
	}
	public double getMShaking() {
		return mShaking;
	}
	public double getMDamage_hInter() {
		return mDamage_hInter;
	}
	public double getMDamage_lInter() {
		return mDamage_lInter;
	}
	public double getMDamage_hIntra() {
		return mDamage_hIntra;
	}
	public double getMDamage_lIntra() {
		return mDamage_lIntra;
	}
	public double getMedDamage_hInter() {
		return medDamage_hInter;
	}
	public double getMedDamage_lInter() {
		return medDamage_lInter;
	}
	public double getMedDamage_hIntra() {
		return medDamage_hIntra;
	}
	public double getMedDamage_lIntra() {
		return medDamage_lIntra;
	}
	public double getIML_hInter() {
		return imlHighInter;
	}
	public double getIML_lInter() {
		return imlLowInter;
	}
	public double getIML_hIntra() {
		return imlHighIntra;
	}
	public double getIML_lIntra() {
		return imlLowIntra;
	}
	public double getMValue() {
		return mValue;
	}
	public double getHValue() {
		return hValue;
	}
	public double getLValue() {
		return lValue;
	}
	public double getMDamage_medIML() {
		return mDamage_medIML;
	}
	public double getDeltaJ_medIML() {
		return deltaJ_medIML;
	}
	public double getDeltaJ_imlHighInter() {
		return deltaJ_imlHighInter;
	}
	public double getDeltaJ_imlLowInter() {
		return deltaJ_imlLowInter;
	}
	public double getDeltaJ_imlHighIntra() {
		return deltaJ_imlHighIntra;
	}
	public double getDeltaJ_imlLowIntra() {
		return deltaJ_imlLowIntra;
	}
	public double getBetaVJ() {
		return betaVJ;
	}
	public double getMedValue() {
		return medValue;
	}
}
