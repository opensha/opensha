package org.opensha.sha.imr.attenRelImpl;

import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;

public class GouletEtAl_2006_AttenRel extends BC_2004_AttenRel {



	public final static String NAME = "Goulet Et. Al. (2006)";
	public final static String SHORT_NAME = "GouletEtAl2006";
	private static final long serialVersionUID = 1234567890987654364L;


	public GouletEtAl_2006_AttenRel(ParameterChangeWarningListener warningListener) {
		super(warningListener);
	}

	/**
	 * Returns the Std Dev.
	 */
	public double getStdDev(){

		String stdDevType = stdDevTypeParam.getValue().toString();
		if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_NONE)) { // "None (zero)"
			return 0;
		}
		updateCoefficients();
		return getStdDevForGoulet();
	}


	/**
	 * @return    The stdDev value for Goulet (2006) Site Correction Model
	 */
	private double getStdDevForGoulet(){
		double bVal = ((Double)AF_SlopeParam.getValue()).doubleValue();
		double cVal = ((Double)this.AF_AddRefAccParam.getValue()).doubleValue();
		double stdDevAF = ((Double)this.AF_StdDevParam.getValue()).doubleValue();
		double tau = coeffs.tau;
		as_1997_attenRel.setIntensityMeasure(im);
		double asRockMean = as_1997_attenRel.getMean();
		double asRockStdDev = as_1997_attenRel.getStdDev();
		//	  double stdDev = Math.pow((bVal*Math.exp(asRockMean))/(Math.exp(asRockMean)+cVal)+1, 2)*
		//      (Math.pow(Math.exp(asRockStdDev),2)-Math.pow(tau, 2))+Math.pow(stdDevAF,2)+Math.pow(tau,2);
		double stdDev = Math.pow((bVal*Math.exp(asRockMean))/(Math.exp(asRockMean)+cVal)+1, 2)*
		(Math.pow((asRockStdDev),2))+Math.pow(stdDevAF,2);
		//		System.out.println("asRockMean="+asRockMean+"  asRockStdDev="+asRockStdDev+"  StdDev="+stdDev);
		//	  return Math.sqrt(stdDev-0.3*0.3);
		return Math.sqrt(stdDev);
	}


	/**
	 * get the name of this IMR
	 *
	 * @return the name of this IMR
	 */
	public String getName() {
		return NAME;
	}

	/**
	 * Returns the Short Name of each AttenuationRelationship
	 * @return String
	 */
	public String getShortName() {
		return SHORT_NAME;
	}

}
