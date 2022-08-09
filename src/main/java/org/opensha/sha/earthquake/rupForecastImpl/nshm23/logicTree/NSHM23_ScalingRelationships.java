package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;

/**
 * NSHM23 scaling relationships, taken from:
 * 
 * Shaw, B.E. (2022, accepted), Magnitude and Slip Scaling Relations for Fault Based Seismic Hazard.
 * 
 * Using pruned recommendations in Table A5 (appendix 3)
 * 
 * @author kevin
 *
 */
@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_ScalingRelationships implements RupSetScalingRelationship {
	
	LOGA_C4p2("LogA, C=4.2", "LogA_C4p2", 1d) {
		@Override
		public double getMag(double area, double length, double width, double origWidth, double aveRake) {
			area *= 1e-6; // m^2 -> km^2
			// eqn 1 with C=4.2
			return Math.log10(area) + 4.2;
		}
	},
	LOGA_C4p1("LogA, C=4.1", "LogA_C4p1", 1d) {
		@Override
		public double getMag(double area, double length, double width, double origWidth, double aveRake) {
			area *= 1e-6; // m^2 -> km^2
			// eqn 1 with C=4.1
			return Math.log10(area) + 4.1;
		}
	},
	WIDTH_LIMITED("Width-Limited", "WdthLmtd", 1d) {
		@Override
		public double getMag(double area, double length, double width, double origWidth, double aveRake) {
			width = USE_ORIG_WIDTHS ? origWidth : width;
			area *= 1e-6; // m^2 -> km^2
			width *= 1e-3; // m -> km
			double beta = 7.4;
			double C = 3.98;
			// eqn 4
			double upperMiddleTerm = Math.max(1, Math.sqrt(area/(width*width)));
			double lowerMiddleTerm = 0.5*(1d + Math.max(1, area/(width*width*beta)));
			return Math.log10(area) + (2d/3d)*Math.log10(upperMiddleTerm/lowerMiddleTerm) + C;
		}
	},
	LOGA_C4p2_SQRT_LEN("LogA, C=4.2, SqtLen", "LogA_C4p2_SqrtLen", 1d) {
		@Override
		public double getMag(double area, double length, double width, double origWidth, double aveRake) {
			return LOGA_C4p2.getMag(area, length, width, origWidth, aveRake);
		}

		@Override
		public double getAveSlip(double area, double length, double width, double origWidth, double aveRake) {
			width = USE_ORIG_WIDTHS ? origWidth : width;
			double C6 = 7.11e-5;
			// leave in SI units here as FaultMomentCalc.SHEAR_MODULUS is in SI units
			// eqn 13
			return C6*Math.sqrt(length*width);
		}
	},
	LOGA_C4p1_SQRT_LEN("LogA, C=4.1, SqtLen", "LogA_C4p1_SqrtLen", 1d) {
		@Override
		public double getMag(double area, double length, double width, double origWidth, double aveRake) {
			return LOGA_C4p1.getMag(area, length, width, origWidth, aveRake);
		}

		@Override
		public double getAveSlip(double area, double length, double width, double origWidth, double aveRake) {
			width = USE_ORIG_WIDTHS ? origWidth : width;
			double C6 = 5.03e-5;
			// leave in SI units here as FaultMomentCalc.SHEAR_MODULUS is in SI units
			// eqn 13
			return C6*Math.sqrt(length*width);
		}
	},
	WIDTH_LIMITED_CSD("Width-Limited Constant-Stress-Drop", "WdthLmtdCSD", 1d) {
		@Override
		public double getMag(double area, double length, double width, double origWidth, double aveRake) {
			return WIDTH_LIMITED.getMag(area, length, width, origWidth, aveRake);
		}

		@Override
		public double getAveSlip(double area, double length, double width, double origWidth, double aveRake) {
			width = USE_ORIG_WIDTHS ? origWidth : width;
			double deltaSigma = 8.01e6; // e6 here converts MPa to Pa
			// leave in SI units here as FaultMomentCalc.SHEAR_MODULUS is in SI units
			// eqn 16
			return (deltaSigma/FaultMomentCalc.SHEAR_MODULUS)*1d/(7d/(3d*length) + 1d/(2d*width));
		}
	},
	AVERAGE("NSHM23 Average", "NSHM23_Avg", 0d) {
		@Override
		public double getMag(double area, double length, double width, double origWidth, double aveRake) {
			double sum = 0d;
			double sumWeights = 0d;
			for (NSHM23_ScalingRelationships scale : values()) {
				double weight = scale.weight;
				if (weight > 0d && scale != this) {
					sum += scale.getMag(area, length, width, origWidth, aveRake)*weight;
					sumWeights += weight;
				}
			}
			return sum/sumWeights;
		}

		@Override
		public double getAveSlip(double area, double length, double width, double origWidth, double aveRake) {
			double sum = 0d;
			double sumWeights = 0d;
			for (NSHM23_ScalingRelationships scale : values()) {
				double weight = scale.weight;
				if (weight > 0d && scale != this) {
					sum += scale.getAveSlip(area, length, width, origWidth, aveRake)*weight;
					sumWeights += weight;
				}
			}
			return sum/sumWeights;
		}
	};
	
	public static boolean USE_ORIG_WIDTHS = false;
	
	private String name;
	private String shortName;
	private double weight;

	private NSHM23_ScalingRelationships(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return shortName.replaceAll("\\W+", "_");
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getAveSlip(double area, double length, double width, double origWidth, double aveRake) {
		double mag = getMag(area, length, width, origWidth, aveRake);
		double moment = MagUtils.magToMoment(mag);	// this returns: (Math.pow(10, 1.5 * magnitude + 9.05));
		return FaultMomentCalc.getSlip(area, moment);	// this returns: moment/(area*SHEAR_MODULUS);
	}

	@Override
	public abstract double getMag(double area, double length, double width, double origWidth, double aveRake);

}
