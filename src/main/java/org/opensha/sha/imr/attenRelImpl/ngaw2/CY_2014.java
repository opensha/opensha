/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with the Southern California
 * Earthquake Center (SCEC, http://www.scec.org) at the University of Southern
 * California and the UnitedStates Geological Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package org.opensha.sha.imr.attenRelImpl.ngaw2;

import static java.lang.Double.NaN;
import static java.lang.Math.cos;
import static java.lang.Math.cosh;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static java.lang.Math.tanh;
import static org.opensha.commons.geo.GeoTools.TO_RAD;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.NORMAL;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.REVERSE;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.UNKNOWN;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.IMT.PGA;

import java.util.Collection;

import org.opensha.sha.util.TectonicRegionType;

/**
 * Preliminary implementation of the Chiou & Youngs (2013) next generation
 * attenuation relationship developed as part of NGA West II.
 * 
 * Component: RotD50
 * 
 * Implementation details:
 * 
 * Not thread safe -- create new instances as needed
 * 
 * @author Peter Powers
 */
public class CY_2014 implements NGAW2_GMM {

	public static final String NAME = "Chiou \u0026 Youngs (2014)";
	public static final String SHORT_NAME = "CY2014";

	private static final double C2 = 1.06;
	private static final double C4 = -2.1;
	private static final double C4A = -0.5;
	private static final double dC4 = C4A - C4;
	private static final double CRB = 50.0;
	private static final double CRBsq = CRB * CRB;
	private static final double C11 = 0.0;
	private static final double PHI6 = 300.0;
	private static final double A = pow(571, 4);
	private static final double B = pow(1360, 4) + A;

	private final Coeffs coeffs;

	private class Coeffs extends Coefficients {

		// TODO inline constance coeffs with final statics

		double c1, c1a, c1b, c1c, c1d, c3, c5, c6, c7, c7b, c8b, c9, c9a, c9b,
		c11b, cn, cM, cHM, cgamma1, cgamma2, cgamma3, phi1, phi2, phi3,
		phi4, phi5, tau1, tau2, sigma1, sigma2, sigma3;

		// same for all periods; replaced with constant
		double c2, c4, c4a, c11, cRB, phi6;
		// unused regional and other coeffs
		double c8, c8a, sigma2_JP, gamma_JP_IT, gamma_WN, phi1_JP, phi5_JP,
				phi6_JP;

		Coeffs() {
			super("CY14.csv");
			set(PGA);
		}
	}

	/**
	 * Constructs a new instance of this attenuation relationship.
	 */
	public CY_2014() {
		coeffs = new Coeffs();
	}
	
	
	private IMT imt = null;

	private double Mw = NaN;
	private double rJB = NaN;
	private double rRup = NaN;
	private double rX = NaN;
	private double dip = NaN;
	private double zTop = NaN;
	private double vs30 = NaN;
	private boolean vsInf = true;
	private double z1p0 = NaN;
	private FaultStyle style = UNKNOWN;

	
	@Override
	public ScalarGroundMotion calc() {
		return calc(imt, Mw, rJB, rRup, rX, dip, zTop, vs30, vsInf, 
			z1p0, style);
	}

	@Override public String getName() { return CY_2014.NAME; }

	@Override public void set_IMT(IMT imt) { this.imt = imt; }

	@Override public void set_Mw(double Mw) { this.Mw = Mw; }
	
	@Override public void set_rJB(double rJB) { this.rJB = rJB; }
	@Override public void set_rRup(double rRup) { this.rRup = rRup; }
	@Override public void set_rX(double rX) { this.rX = rX; }
	
	@Override public void set_dip(double dip) { this.dip = dip; }
	@Override public void set_width(double width) {} // not used
	@Override public void set_zTop(double zTop) { this.zTop = zTop; }
	@Override public void set_zHyp(double zHyp) {} // not used

	@Override public void set_vs30(double vs30) { this.vs30 = vs30; }
	@Override public void set_vsInf(boolean vsInf) { this.vsInf = vsInf; }
	@Override public void set_z2p5(double z2p5) {} // not used
	@Override public void set_z1p0(double z1p0) { this.z1p0 = z1p0; }
	
	@Override public void set_fault(FaultStyle style) { this.style = style; }

	@Override
	public TectonicRegionType get_TRT() {
		return TectonicRegionType.ACTIVE_SHALLOW;
	}

	@Override
	public Collection<IMT> getSupportedIMTs() {
		return coeffs.getSupportedIMTs();
	}


	/**
	 * Returns the ground motion for the supplied arguments.
	 * @param imt intensity measure type
	 * @param Mw moment magnitude
	 * @param rJB Joyner-Boore distance to rupture (in km)
	 * @param rRup 3D distance to rupture plane (in km)
	 * @param rX distance X (in km)
	 * @param dip of rupture (in degrees)
	 * @param zTop depth to the top of the rupture (in km)
	 * @param vs30 average shear wave velocity in top 30 m (in m/sec)
	 * @param vsInferred whether vs30 is an inferred or measured value
	 * @param z1p0 depth to V<sub>s</sub>=1.0 km/sec (in km)
	 * @param style of faulting
	 * @return the ground motion
	 */
	public final ScalarGroundMotion calc(IMT imt, double Mw, double rJB,
			double rRup, double rX, double dip, double zTop, double vs30,
			boolean vsInferred, double z1p0, FaultStyle style) {

		coeffs.set(imt);
		
		// terms used by both mean and stdDev
		double saRef = calcSAref(coeffs, Mw, rJB, rRup, rX, dip, zTop, style);
		double soilNonLin = calcSoilNonLin(coeffs, vs30);
		
		double mean = calcMean(coeffs, vs30, z1p0, soilNonLin, saRef);
		
		// Aleatory uncertainty model -- Equation 3.9

		// Response Term - linear vs. non-linear
		double NL0sq = calcNLOsq(coeffs, soilNonLin, saRef);

		// Magnitude thresholds
		double mTest = min(max(Mw, 5.0), 6.5) - 5.0;

		// Inter-event Term
		double tauSq = calcTauSq(coeffs, NL0sq, mTest);

		// Intra-event term
		double phiSq = calcPhiSq(coeffs, vsInf, NL0sq, mTest);
						
		double stdDev = sqrt(tauSq + phiSq);

		return new DefaultGroundMotion(mean, stdDev, Math.sqrt(phiSq), Math.sqrt(tauSq));
	}

	// Seismic Source Scaling -- Equation 11
	private static final double calcSAref(Coeffs c, double Mw, double rJB,
			double rRup, double rX, double dip, double zTop, FaultStyle style) {
		
		// Magnitude scaling
		double r1 = c.c1 + C2 * (Mw - 6.0) + ((C2 - c.c3) / c.cn) *
			log(1.0 + exp(c.cn * (c.cM - Mw)));

		// Near-field magnitude and distance scaling
		double r2 = C4 * log(rRup + c.c5 * cosh(c.c6 * max(Mw - c.cHM, 0.0)));

		// Far-field distance scaling
		double gamma = (c.cgamma1 + c.cgamma2 / cosh(max(Mw - c.cgamma3, 0.0)));
		double r3 = dC4 * log(sqrt(rRup * rRup + CRBsq)) + rRup * gamma;

		// Scaling with other source variables
		double coshM = cosh(2 * max(Mw - 4.5, 0));
		double cosDelta = cos(dip * TO_RAD);
		// Center zTop on the zTop-M relation
		double deltaZtop = zTop - calcMwZtop(style, Mw);
		double r4 = (c.c7 + c.c7b / coshM) * deltaZtop + 
				    (C11 + c.c11b / coshM) * cosDelta * cosDelta;
		r4 += (style == REVERSE) ? (c.c1a + c.c1c / coshM) : 
			  (style == NORMAL) ? (c.c1b + c.c1d / coshM) : 0.0; 

		// Hanging-wall effect
		double r5 = 0.0;
		if (rX >= 0.0) {
			r5 = c.c9 * cos(dip * TO_RAD) *
				(c.c9a + (1.0 - c.c9a) * tanh(rX / c.c9b)) *
				(1 - sqrt(rJB * rJB + zTop * zTop) / (rRup + 1.0));
		}

		// Directivity effect (not implemented)
		// cDPP = centered DPP (direct point directivity parameter)
		//double c8 = 0.2154; // corrected from 2.154 12/3/13 per email from Sanaz
		//double c8a = 0.2695;
		//double Mc8 = Mw-c.c8b;
		//double r6 = c8 * exp(-c8a * Mc8 * Mc8) *
		//	max(0.0, 1.0 - max(0, rRup - 40.0) / 30.0) *
		//	min(max(0, Mw - 5.5) / 0.8, 1.0) * cDPP;

		return exp(r1 + r2 + r3 + r4 + r5);
	}
	
	private static final double calcSoilNonLin(Coeffs c, double vs30) {
		double exp1 = exp(c.phi3 * (min(vs30, 1130.0) - 360.0));
		double exp2 = exp(c.phi3 * (1130.0 - 360.0));
		return c.phi2 * (exp1 - exp2);
	}

	// Mean ground motion model -- Equation 12
	private static final double calcMean(Coeffs c, double vs30, double z1p0,
			double snl, double saRef) {

		// Soil effect: linear response
		double sl = c.phi1 * min(log(vs30 / 1130.0), 0.0);

		// Soil effect: nonlinear response (base passed in)
		snl *= log((saRef + c.phi4) / c.phi4);

		// Soil effect: sediment thickness
		double dZ1 = calcDeltaZ1(z1p0, vs30);
		double rkdepth = c.phi5 * (1.0 - exp(-dZ1 / PHI6));

		// total model
		return log(saRef) + sl + snl + rkdepth;
	}

	// Center zTop on the zTop-M relation -- Equations 4, 5
	private static final double calcMwZtop(FaultStyle style, double Mw) {
		double mzTop = 0.0;
		if (style == REVERSE) {
			mzTop = (Mw <= 5.849) ? 2.704 : max(2.704 - 1.226 * (Mw - 5.849), 0);
		} else {
			mzTop = (Mw <= 4.970) ? 2.673 : max(2.673 - 1.136 * (Mw - 4.970), 0);
		}
		return mzTop * mzTop;
	}
	
	// -- Equation 1
	private static final double calcDeltaZ1(double z1p0, double vs30) {
		if (Double.isNaN(z1p0)) return 0.0;
		double vsPow4 = vs30 * vs30 * vs30 * vs30;
		return z1p0 * 1000.0 - exp(-7.15 / 4 * log((vsPow4 + A) / B));
	}

	private static double calcNLOsq(Coeffs c, double snl, double saRef) {
		double NL0 = snl * saRef / (saRef + c.phi4);
		double NL0sq = (1 + NL0) * (1 + NL0);
		return NL0sq;
	}

	private static double calcTauSq(Coeffs c, double NL0sq, double mTest) {
		double tau = c.tau1 + (c.tau2 - c.tau1) / 1.5 * mTest;
		double tauSq = tau * tau * NL0sq;
		return tauSq;
	}

	private static double calcPhiSq(Coeffs c, boolean vsInf, double NL0sq, double mTest) {
		double sigmaNL0 = c.sigma1 + (c.sigma2 - c.sigma1) / 1.5 * mTest;
		double vsTerm = vsInf ? c.sigma3 : 0.7;
		sigmaNL0 *= sqrt(vsTerm + NL0sq);
		double phiSq = sigmaNL0 * sigmaNL0;
		return phiSq;
	}
	
	public static void main(String[] args) {
		CY_2014 cy = new CY_2014();

		System.out.println("PGA");
		ScalarGroundMotion sgm = cy.calc(PGA, 6.80, 0.0, 4.629, 5.963, 27.0, 2.1, 760.0, true, Double.NaN, FaultStyle.REVERSE);
		System.out.println(sgm.mean());
		System.out.println(sgm.stdDev());
		System.out.println("5Hz");
		sgm = cy.calc(IMT.SA0P2, 6.80, 0.0, 4.629, 5.963, 27.0, 2.1, 760.0, true, Double.NaN, FaultStyle.REVERSE);
		System.out.println(sgm.mean());
		System.out.println(sgm.stdDev());
		System.out.println("1Hz");
		sgm = cy.calc(IMT.SA1P0, 6.80, 0.0, 4.629, 5.963, 27.0, 2.1, 760.0, true, Double.NaN, FaultStyle.REVERSE);
		System.out.println(sgm.mean());
		System.out.println(sgm.stdDev());

//		Set<IMT> IMTs = EnumSet.complementOf(EnumSet.of(PGV, PGD, IMT.SA0P01)); 
//		for (IMT imt : IMTs) {
////			ScalarGroundMotion sgm = cy.calc(imt, 7.06, 27.08, 27.08, 27.08, 90.0, 0.0, 760.0, true, Double.NaN, FaultStyle.STRIKE_SLIP);
//			ScalarGroundMotion sgm = cy.calc(imt, 7.5, 8.5, 10, 10, 70.0, 0.0, 760.0, true, Double.NaN, FaultStyle.STRIKE_SLIP);
//			System.out.println(String.format("%s\t%.4f\t%.4f", imt, sgm.mean(), sgm.stdDev()));
//		}
		
	}

}
