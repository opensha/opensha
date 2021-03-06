package org.opensha.sha.imr.attenRelImpl.calc;

import com.google.common.base.Preconditions;

/**
 * <p>Title: Wald_MMI_Calc </p>
 * <b>Description:</b> This computes MMI (from PGA and PGV) using the relationship given by
 * Wald et al. (1999, Earthquake Spectra, vol 15, p 557-564).  The code is a modified version
 * of what Bruce Worden sent me (Ned) on 12/04/03.  This code has been validated
 * against some of the values listed in the USGS on-line data archive for the Puente Hills event:
 * http://www.trinet.org/shake/Puente_Hills_se/download/grid.xyz.zip <p>
 *
 *
 * @author Ned Field
 * @created    May, 2004
 * @version 1.0
 */

public final class Wald_MMI_Calc {

	static double sma     =  3.6598;
	static double ba      = -1.6582;
	static double sma_low =  2.1987;
	static double ba_low  =  1;

	static double smv     =  3.4709;
	static double bv      =  2.3478;
	static double smv_low =  2.0951;
	static double bv_low  =  3.3991;

	/**
	 *
	 * @param pga - peak ground acceleration (g)
	 * @param pgv - peak ground velocity (cm/sec)
	 * @return
	 */
	public static double getMMI(double pga, double pgv){
		String S = ".getMMI()";

		double ammi = getAMMI(pga); // Intensity from acceleration
		double vmmi = getVMMI(pgv); // Intensity from velocity
		double scale = getWeightVMMI(ammi);

		double mmi = ((1.0-scale) * ammi) + (scale * vmmi);
		if (mmi < 1.0) mmi = 1.0 ;
		if (mmi > 10.0) mmi = 10.0;
		//      return (double)((int) (mmi * 100)) / 100.;
		return mmi;
	}
	
	/**
	 * 
	 * @param pga (g)
	 * @return
	 */
	public static double getAMMI(double pga) {
		// Convert pga to gals as needed below
		pga *= 980.0;
		
		double ammi = (0.43429*Math.log(pga) * sma) + ba;
		if (ammi <= 5.0)
			ammi = (0.43429*Math.log(pga) * sma_low) + ba_low;
		
		if (ammi < 1) ammi = 1;
		
		return ammi;
	}
	
	public static double getPGA(double ammi) {
		Preconditions.checkState(ammi >= 1, "Only valid for AMMI>=1");
		double pga;
		if (ammi <= 5)
			pga = Math.exp((ammi - ba_low)/(0.43429*sma_low));
		else
			pga = Math.exp((ammi - ba)/(0.43429*sma));
		return pga / 980d; // convert back to G
	}
	
	/**
	 * 
	 * @param pgv (cm/sec)
	 * @return
	 */
	public static double getVMMI(double pgv) {
		
		double vmmi = (0.43429*Math.log(pgv) * smv) + bv;
		if (vmmi <= 5.0)
			vmmi = (0.43429*Math.log(pgv) * smv_low) + bv_low;
		
		if (vmmi < 1) vmmi = 1;
		
		return vmmi;
	}
	
	public static double getPGV(double vmmi) {
		Preconditions.checkState(vmmi >= 1, "Only valid for VMMI>=1");
		if (vmmi < 1)
			vmmi = 1;
		if (vmmi <= 5)
			return Math.exp((vmmi - bv_low)/(0.43429*smv_low));
		else
			return Math.exp((vmmi - bv)/(0.43429*smv));
	}
	
	public static double getWeightVMMI(double ammi) {
		// use linear ramp between MMI 5 & 7 (ammi below and vmmi above, respectively)
		double scale = (ammi - 5) / 2; // ramp
		if (scale > 1.0) scale = 1.0;
		if (scale < 0.0) scale = 0.0;
		return scale;
	}
	
	public static void main(String[] args) {
		for (double vmmi=0d; vmmi<11d; vmmi+=0.1) {
			double pgv = getPGV(vmmi);
			double calcVMMI = getVMMI(pgv);
			double diff = calcVMMI - vmmi;
			System.out.println("VMMI: "+(float)vmmi+"\tPGV: "+(float)pgv
					+"\tCalc: "+(float)calcVMMI+"\tDiff: "+(float)diff);
		}
		for (double ammi=0d; ammi<11d; ammi+=0.1) {
			double pga = getPGA(ammi);
			double calcAMMI = getAMMI(pga);
			double diff = calcAMMI - ammi;
			System.out.println("AMMI: "+(float)ammi+"\tPGA: "+(float)pga
					+"\tCalc: "+(float)calcAMMI+"\tDiff: "+(float)diff);
		}
		System.out.println(getMMI(0.1262741, 11.278339));
	}

	/**
	 * This main method tests the calculations against some of the values listed
	 * in the USGS on-line data archive for the Puente Hills event:
	 * http://www.trinet.org/shake/Puente_Hills_se/download/grid.xyz.zip <p>
	 * The differences of 0.01 result from differences in how the values are rounded.
	 * @param args
	 */
	/* commented out until needed again
    public static void main(String[] args) {

      System.out.println("Comparison of values:");
      System.out.println((float) Wald_MMI_Calc.getMMI(4.922/100, 4.3774) + " 4.7");
      System.out.println((float) Wald_MMI_Calc.getMMI(5.164/100, 4.5989) + " 4.74");
      System.out.println((float) Wald_MMI_Calc.getMMI(5.454/100, 4.8645) + " 4.8");
      System.out.println((float) Wald_MMI_Calc.getMMI(6.0587/100, 5.42) + " 4.9");
      System.out.println((float) Wald_MMI_Calc.getMMI(7.3959/100, 6.6554) + " 5.15");
      System.out.println((float) Wald_MMI_Calc.getMMI(8.0925/100, 7.3029) + " 5.3");
      System.out.println((float) Wald_MMI_Calc.getMMI(9.1612/100, 8.3022) + " 5.5");
      System.out.println((float) Wald_MMI_Calc.getMMI(9.8223/100, 8.923) + " 5.61");
      System.out.println((float) Wald_MMI_Calc.getMMI(11.2098/100, 10.2373) + " 5.82");
      System.out.println((float) Wald_MMI_Calc.getMMI(12.6785/100, 11.6435) + " 6.02");
      System.out.println((float) Wald_MMI_Calc.getMMI(17.6332/100, 16.4121) + " 6.55");
      System.out.println((float) Wald_MMI_Calc.getMMI(24.3042/100, 28.9529) + " 7.42");
      System.out.println((float) Wald_MMI_Calc.getMMI(28.7264/100, 41.9774) + " 7.98");
      System.out.println((float) Wald_MMI_Calc.getMMI(32.0077/100, 44.5101) + " 8.06");
      System.out.println((float) Wald_MMI_Calc.getMMI(34.3796/100, 48.8414) + " 8.2");
      System.out.println((float) Wald_MMI_Calc.getMMI(37.7906/100, 69.7778) + " 8.74");
      System.out.println((float) Wald_MMI_Calc.getMMI(41.2803/100, 80.9729) + " 8.97");
      System.out.println((float) Wald_MMI_Calc.getMMI(42.2048/100, 85.205) + " 9.04");
      System.out.println((float) Wald_MMI_Calc.getMMI(47.0372/100, 104.926) + " 9.36");

      System.out.println("Difference in values:");
      System.out.println((float) Wald_MMI_Calc.getMMI(4.922/100, 4.3774) - 4.7);
      System.out.println((float) Wald_MMI_Calc.getMMI(5.164/100, 4.5989) - 4.74);
      System.out.println((float) Wald_MMI_Calc.getMMI(5.454/100, 4.8645) - 4.8);
      System.out.println((float) Wald_MMI_Calc.getMMI(6.0587/100, 5.42) - 4.9);
      System.out.println((float) Wald_MMI_Calc.getMMI(7.3959/100, 6.6554) - 5.15);
      System.out.println((float) Wald_MMI_Calc.getMMI(8.0925/100, 7.3029) - 5.3);
      System.out.println((float) Wald_MMI_Calc.getMMI(9.1612/100, 8.3022) - 5.5);
      System.out.println((float) Wald_MMI_Calc.getMMI(9.8223/100, 8.923) - 5.61);
      System.out.println((float) Wald_MMI_Calc.getMMI(11.2098/100, 10.2373) - 5.82);
      System.out.println((float) Wald_MMI_Calc.getMMI(12.6785/100, 11.6435) - 6.02);
      System.out.println((float) Wald_MMI_Calc.getMMI(17.6332/100, 16.4121) - 6.55);
      System.out.println((float) Wald_MMI_Calc.getMMI(24.3042/100, 28.9529) - 7.42);
      System.out.println((float) Wald_MMI_Calc.getMMI(28.7264/100, 41.9774) - 7.98);
      System.out.println((float) Wald_MMI_Calc.getMMI(32.0077/100, 44.5101) - 8.06);
      System.out.println((float) Wald_MMI_Calc.getMMI(34.3796/100, 48.8414) - 8.2);
      System.out.println((float) Wald_MMI_Calc.getMMI(37.7906/100, 69.7778) - 8.74);
      System.out.println((float) Wald_MMI_Calc.getMMI(41.2803/100, 80.9729) - 8.97);
      System.out.println((float) Wald_MMI_Calc.getMMI(42.2048/100, 85.205) - 9.04);
      System.out.println((float) Wald_MMI_Calc.getMMI(47.0372/100, 104.926) - 9.36);
    }
	 */

}
