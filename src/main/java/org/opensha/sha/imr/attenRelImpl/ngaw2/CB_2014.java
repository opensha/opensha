/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.imr.attenRelImpl.ngaw2;

import static java.lang.Double.NaN;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.commons.geo.GeoTools.TO_RAD;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.NORMAL;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.UNKNOWN;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.IMT.PGA;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.IMT.SA0P01;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.IMT.SA0P25;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.opensha.sha.util.TectonicRegionType;

/**
 * Preliminary implementation of the Campbell & Bozorgnia (2013) next generation attenuation
 * relationship developed as part of NGA West II.
 * 
 * Component: RotD50
 * 
 * Implementation details:
 * 
 * Not thread safe -- create new instances as needed
 * 
 * @author Peter Powers
 */
public class CB_2014 implements NGAW2_GMM {

	public static final String NAME = "Campbell \u0026 Bozorgnia (2014)";
	public static final String SHORT_NAME = "CB2014";
	
	private final Coeffs coeffs;
	private final Coeffs coeffsPGA;
	
	private static final Set<IMT> SHORT_PERIODS = EnumSet.range(SA0P01, SA0P25);
	
	// implementation constants
	private static final double H4 = 1.0;
	private static final double C = 1.88;
	private static final double N = 1.18;
	private static final double PHI_LNAF_SQ = 0.09; // 0.3^2

	private static class Coeffs extends Coefficients {
		
		// TODO inline constance coeffs with final statics
		
		double c0, c1, c2, c3, c4, c5, c6, c7, c9, c10, c11, c14,
		c16, c17, c18, c19, c20, a2, h1, h2, h3, h5, h6, k1, k2,
		k3, phi1, phi2, tau1, tau2, rho;

		// same for all periods; replaced with constant; or unused (c8)
		double c8, c12, c13, c15, h4, c, n, phi_lnaf;
		// unused regional and other coeffs
		double Dc20_CA, Dc20_JP, Dc20_CH, phiC;
		
		// fixed PGA values used by all periods when calculating stdDev
		double tau_hi_PGA, tau_lo_PGA, phi_hi_PGA, phi_lo_PGA;

		Coeffs() {
			super("CB14.csv");
			set(PGA);
			tau_hi_PGA = get(PGA, "tau2");
			tau_lo_PGA = get(PGA, "tau1");
			phi_hi_PGA = get(PGA, "phi2");
			phi_lo_PGA = get(PGA, "phi1");
		}
	}
	
	/**
	 * Constructs a new instance of this attenuation relationship.
	 */
	public CB_2014() {
		coeffs = new Coeffs();
		coeffsPGA = new Coeffs();
	}

	private IMT imt = null;

	private double Mw = NaN;
	private double rJB = NaN;
	private double rRup = NaN;
	private double rX = NaN;
	private double dip = NaN;
	private double width = NaN;
	private double zTop = NaN;
	private double zHyp = NaN;
	private double vs30 = NaN;
	private double z2p5 = NaN;
	private FaultStyle style = UNKNOWN;

	@Override
	public ScalarGroundMotion calc() {
		return calc(imt, Mw, rJB, rRup, rX, dip, width, zTop, zHyp, vs30, 
			z2p5, style);
	}
	
	@Override public String getName() { return CB_2014.NAME; }

	@Override public void set_IMT(IMT imt) { this.imt = imt; }

	@Override public void set_Mw(double Mw) { this.Mw = Mw; }
	
	@Override public void set_rJB(double rJB) { this.rJB = rJB; }
	@Override public void set_rRup(double rRup) { this.rRup = rRup; }
	@Override public void set_rX(double rX) { this.rX = rX; }
	
	@Override public void set_dip(double dip) { this.dip = dip; }
	@Override public void set_width(double width) { this.width = width; }
	@Override public void set_zTop(double zTop) { this.zTop = zTop; }
	@Override public void set_zHyp(double zHyp) { this.zHyp = zHyp; }
	
	@Override public void set_vs30(double vs30) { this.vs30 = vs30; }
	@Override public void set_vsInf(boolean vsInf) {} // not used
	@Override public void set_z2p5(double z2p5) { this.z2p5 = z2p5; }
	@Override public void set_z1p0(double z1p0) {} // not used

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
	 * @param width down-dip rupture width (in km)
	 * @param zTop depth to the top of the rupture (in km)
	 * @param zHyp hypocentral depth (in km)
	 * @param vs30 average shear wave velocity in top 30 m (in m/sec)
	 * @param z2p5 depth to V<sub>s</sub>=2.5 km/sec
	 * @param style of faulting
	 * @return the ground motion
	 */
	public final ScalarGroundMotion calc(IMT imt, double Mw, double rJB,
			double rRup, double rX, double dip, double width, double zTop,
			double zHyp, double vs30, double z2p5, FaultStyle style) {
		
		coeffs.set(imt);

		// calc pga rock reference value using CA vs30 z2p5 value: 0.398
		double pgaRock = (vs30 < coeffs.k1) ? exp(calcMean(coeffsPGA, Mw, rJB,
			rRup, rX, dip, width, zTop, zHyp, 1100.0, 0.398, style, 0.0))
			: 0.0;

		double mean = calcMean(coeffs, Mw, rJB, rRup, rX, dip, width, zTop,
			zHyp, vs30, z2p5, style, pgaRock);
		
		// prevent SA<PGA for short periods
		if (SHORT_PERIODS.contains(imt)) {
			double pgaMean = calcMean(coeffsPGA, Mw, rJB, rRup, rX, dip,
				width, zTop, zHyp, vs30, z2p5, style, pgaRock);
			mean = max(mean, pgaMean);
		}
		
		double alpha = calcAlpha(coeffs, vs30, pgaRock);
		double phiSq = calcPhiSq(coeffs, Mw, alpha);
		double tauSq = calcTauSq(coeffs, Mw, alpha);
		double stdDev = calcStdDev(phiSq, tauSq);

		return new DefaultGroundMotion(mean, stdDev, Math.sqrt(phiSq), Math.sqrt(tauSq));
	}
	
	// Mean ground motion model
	private static final double calcMean(Coeffs c, double Mw, double rJB,
			double rRup, double rX, double dip, double width, double zTop,
			double zHyp, double vs30, double z2p5, FaultStyle style,
			double pgaRock) {
		
		// @formatter:off
		
		// Magnitude term -- Equation 2
		double Fmag = c.c0 + c.c1 * Mw;
		if (Mw > 6.5) {
			Fmag += c.c2 * (Mw - 4.5) + c.c3 * (Mw - 5.5) + c.c4 * (Mw - 6.5);
		} else if (Mw > 5.5) {
			Fmag += c.c2 * (Mw - 4.5) + c.c3 * (Mw - 5.5);
		} else if (Mw > 4.5) {
			Fmag += c.c2 * (Mw - 4.5);
		}

		// Distance term -- Equation 3
		double r = sqrt(rRup * rRup + c.c7 * c.c7);
		double Fr = (c.c5 + c.c6 * Mw) * log(r);
	    
		// Style-of-Faulting term -- Equations 4, 5, 6
		// c8 is always 0 so REVERSE switch has been removed
		double Fflt = 0.0;
		if (style == NORMAL && Mw > 4.5) {
			Fflt = c.c9;
			if (Mw <= 5.5) Fflt *= (Mw - 4.5);
		}
		
		// Hanging-Wall term
		double Fhw = 0.0;
		// short-circuit: f4 is 0 if rX < 0, Mw <= 5.5, zTop > 16.66
		// these switches have been removed below
		if (rX >= 0.0 && Mw > 5.5 && zTop <= 16.66) { // short-circuit
		
			// Jennifer Donahue's HW Model plus CB08 distance taper
			//  -- Equations 9, 10, 11 & 12
			double r1 = width * cos(dip * TO_RAD);
			double r2 = 62.0 * Mw - 350.0;
			double rXr1 = rX / r1;
			double rXr2r1 = (rX - r1) / (r2 - r1);
			double f1_rX = c.h1 + c.h2 * rXr1 + c.h3 * (rXr1 * rXr1);
			double f2_rX = H4 + c.h5 * (rXr2r1) + c.h6 * rXr2r1 * rXr2r1;
			
			// ... rX -- Equation 8
			double Fhw_rX = (rX >= r1) ? max(f2_rX, 0.0) : f1_rX;
			
			// ... rRup -- Equation 13
			double Fhw_rRup = (rRup == 0.0) ? 1.0 : (rRup - rJB) / rRup;
	
			// ... magnitude -- Equation 14
			double Fhw_m = 1.0 + c.a2 * (Mw - 6.5);
			if (Mw <= 6.5) Fhw_m *= (Mw - 5.5);
	
			// ... depth -- Equation 15
			double Fhw_z = 1.0 - 0.06 * zTop;
	
			// ... dip -- Equation 16
			double Fhw_d = (90.0 - dip) / 45.0;
	
			// ... total -- Equation 7
			Fhw = c.c10 * Fhw_rX * Fhw_rRup * Fhw_m * Fhw_z * Fhw_d;
		}
		
		// Shallow Site Response term - pgaRock term is computed through an
		// initial call to this method with vs30=1100; 1100 is higher than any
		// k1 value so else condition always prevails -- Equation 18
		double vsk1 = vs30 / c.k1;
		double Fsite = (vs30 <= c.k1) ? c.c11 * log(vsk1) + 
			c.k2 * (log(pgaRock + C * pow(vsk1, N)) - log(pgaRock + C)) :
				(c.c11 + c.k2 * N) * log(vsk1);
		
		// Basin Response term  -- Equation 20
		// update z2p5 with CA model if not supplied -- Equation 33
		if (Double.isNaN(z2p5)) z2p5 = exp(7.089 - 1.144 * log(vs30));
		double Fsed = 0.0;
		if (z2p5 <= 1.0) {
			Fsed = c.c14 * (z2p5 - 1.0);
		} else if (z2p5 > 3.0) {
			Fsed = c.c16 * c.k3 * exp(-0.75) * (1.0 - exp(-0.25 * (z2p5 - 3.0)));
		}

		// Hypocentral Depth term -- Equations 21, 22, 23
		double Fhyp = (zHyp <= 7.0) ? 0.0 : (zHyp <= 20.0) ? zHyp - 7.0 : 13.0;
		if (Mw <= 5.5) {
			Fhyp *= c.c17;
		} else if (Mw <= 6.5) {
			Fhyp *= (c.c17 + (c.c18 - c.c17) * (Mw - 5.5));
		} else {
			Fhyp *= c.c18;
		}

		// Fault Dip term -- Equation 24
		double Fdip = (Mw > 5.5) ? 0.0 :
					  (Mw > 4.5) ? c.c19 * (5.5 - Mw) * dip :
			        	  c.c19 * dip;
		
		// Anelastic Attenuation term -- Equation 25
		double Fatn = (rRup > 80.0) ? c.c20 * (rRup - 80.0) : 0.0;
		
		// total model -- Equation 1
		return Fmag + Fr + Fflt + Fhw + Fsite + Fsed + Fhyp + Fdip + Fatn;
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(Coeffs c, double Mw,
			double vs30, double pgaRock) {

		double alpha = calcAlpha(c, vs30, pgaRock);
		
		double tauSq = calcTauSq(c, Mw, alpha);
				
		double phiSq = calcPhiSq(c, Mw, alpha);
		
		return calcStdDev(phiSq, tauSq);
	}
	
	private static final double calcStdDev(double phiSq, double tauSq) {
		// total model -- Equation 32
		return sqrt(phiSq + tauSq);
		// @formatter:on
	}

	private static double calcAlpha(Coeffs c, double vs30, double pgaRock) {
		//  -- Equation 31
		double vsk1 = vs30 / c.k1;
		double alpha = (vs30 < c.k1) ? c.k2 * pgaRock * 
			(1 / (pgaRock + C * pow(vsk1, N)) - 1 / (pgaRock + C)) : 0.0;
		return alpha;
	}

	private static double calcPhiSq(Coeffs c, double Mw, double alpha) {
		double phi_lnY, phi_lnPGAB;
		if (Mw <= 4.5) {
			phi_lnY = c.phi1;
			phi_lnPGAB = c.phi_lo_PGA;
		} else if (Mw < 5.5) {
			phi_lnY = stdMagDep(c.phi1, c.phi2, Mw);
			phi_lnPGAB = stdMagDep(c.phi_lo_PGA, c.phi_hi_PGA, Mw);
		} else {
			phi_lnY = c.phi2;
			phi_lnPGAB = c.phi_hi_PGA;
		}
		
		// inter-event std dev -- Equation 28
		double phi_lnYB = sqrt(phi_lnY * phi_lnY - PHI_LNAF_SQ);
		phi_lnPGAB = sqrt(phi_lnPGAB * phi_lnPGAB - PHI_LNAF_SQ);
		double aPhi_lnPGAB = alpha * phi_lnPGAB;

		// phi_lnaf terms in eqn. 30 cancel when expanded leaving phi_lnY only
		double phiSq = phi_lnY * phi_lnY + aPhi_lnPGAB * aPhi_lnPGAB +
			2.0 * c.rho * phi_lnYB * aPhi_lnPGAB;
		return phiSq;
	}

	private static double calcTauSq(Coeffs c, double Mw, double alpha) {
		// Magnitude dependence -- Equations 29 & 30
		double tau_lnYB, tau_lnPGAB;
		if (Mw <= 4.5) {
			tau_lnYB = c.tau1;
			tau_lnPGAB = c.tau_lo_PGA;
		} else if (Mw < 5.5) {
			tau_lnYB = stdMagDep(c.tau1, c.tau2, Mw);
			tau_lnPGAB = stdMagDep(c.tau_lo_PGA, c.tau_hi_PGA, Mw);
		} else {
			tau_lnYB = c.tau2;
			tau_lnPGAB = c.tau_hi_PGA;
		}
		
		// intra-event std dev -- Equation 27
		double alphaTau = alpha * tau_lnPGAB;
		double tauSq = tau_lnYB * tau_lnYB + alphaTau * alphaTau + 
			2.0 * alpha * c.rho * tau_lnYB * tau_lnPGAB;
		return tauSq;
	}

	private static final double stdMagDep(double lo, double hi, double Mw) {
		return hi + (lo - hi) * (5.5 - Mw);
	}
	
	// can be useful for debugging
//	public String toString() {
//		return imt.name()+"\t"+(float)Mw+"\t"+(float)rJB+"\t"+(float)rRup+"\t"+(float)rX
//				+"\t"+(float)dip+"\t"+(float)width+"\t"+(float)zTop+"\t"+(float)zHyp
//				+"\t"+(float)vs30+"\t"+(float)z2p5+"\t"+style;
//	}
	
}
