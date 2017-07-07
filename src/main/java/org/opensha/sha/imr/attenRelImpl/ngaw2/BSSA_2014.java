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
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.NORMAL;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.REVERSE;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.STRIKE_SLIP;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.UNKNOWN;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.IMT.PGA;

import java.util.Collection;

import org.opensha.sha.util.TectonicRegionType;

/**
 * Preliminary implementation of the Boore, Stewart, Seyhan, &amp; Atkinson
 * (2013) next generation attenuation relationship developed as part of NGA West
 * II.
 * 
 * Component: RotD50
 * 
 * Implementation details:
 * 
 * Not thread safe -- create new instances as needed
 * 
 * @author Peter Powers
 */
public class BSSA_2014 implements NGAW2_GMM {

	public static final String NAME = "Boore, Stewart, Seyhan \u0026 Atkinson (2014)";
	public static final String SHORT_NAME = "BSSA2014";

	// implementation constants
	private static final double A = pow(570.94, 4);
	private static final double B = pow(1360, 4) + A;
	private static final double M_REF = 4.5;
	private static final double R_REF = 1.0;
	private static final double DC3_CA_TW = 0.0;
	private static final double V_REF = 760.0;
	private static final double F1 = 0.0;
	public static final double F3 = 0.1;
	private static final double V1 = 225;
	private static final double V2 = 300;

	private final Coeffs coeffs;
	private final Coeffs coeffsPGA;

	private static class Coeffs extends Coefficients {

		double e0, e1, e2, e3, e4, e5, e6, Mh, c1, c2, c3, h, c, Vc, f4, f5,
		f6, f7, R1, R2, dPhiR, dPhiV, phi1, phi2, tau1, tau2;

		// same for all periods; replaced with constant
		double Mref, Rref, Dc3CaTw, Vref, f1, f3, v1, v2;
		// unused regional coeffs
		double Dc3CnTr, Dc3ItJp;

		Coeffs() {
			super("BSSA14.csv");
			set(PGA);
		}
	}

	/**
	 * Constructs a new instance of this attenuation relationship.
	 */
	public BSSA_2014() {
		coeffs = new Coeffs();
		coeffsPGA = new Coeffs();
	}

	// TODO limit supplied z1p0 to 0-3 km
	
	private IMT imt = null;

	private double Mw = NaN;
	private double rJB = NaN;
	private double vs30 = NaN;
	private double z1p0 = NaN;
	private FaultStyle style = UNKNOWN;

	@Override
	public ScalarGroundMotion calc() {
		return calc(imt, Mw, rJB, vs30, z1p0, style);
	}

	@Override public String getName() { return BSSA_2014.NAME; }

	@Override public void set_IMT(IMT imt) { this.imt = imt; }

	@Override public void set_Mw(double Mw) { this.Mw = Mw; }
	
	@Override public void set_rJB(double rJB) { this.rJB = rJB; }
	@Override public void set_rRup(double rRup) {} // not used
	@Override public void set_rX(double rX) {} // not used
	
	@Override public void set_dip(double dip) {} // not used
	@Override public void set_width(double width) {} // not used
	@Override public void set_zTop(double zTop) {} // not used
	@Override public void set_zHyp(double zHyp) {} // not used
	
	@Override public void set_vs30(double vs30) { this.vs30 = vs30; }
	@Override public void set_vsInf(boolean vsInf) {} // not used
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
	 * @param vs30 average shear wave velocity in top 30 m (in m/sec)
	 * @param z1p0 depth to V<sub>s</sub>=1.0 km/sec (in km)
	 * @param style of faulting
	 * @return the ground motion
	 */
	public final ScalarGroundMotion calc(IMT imt, double Mw, double rJB,
			double vs30, double z1p0, FaultStyle style) {
		
//		System.out.println("mw="+Mw+", rJB="+rJB+", vs30="+vs30+", z1p0="+z1p0+", fs="+style+", IMT="+imt);

		coeffs.set(imt);
		double pgaRock = calcPGArock(coeffsPGA, Mw, rJB, style);
		double mean = calcMean(coeffs, Mw, rJB, vs30, z1p0, style, pgaRock);
		double phi = calcPhi(coeffs, Mw, rJB, vs30);
		double tau = calcTau(coeffs, Mw);
		double stdDev = calcStdDev(phi, tau);
//		System.out.println("Mean="+mean+", phi="+phi+", tau="+tau+", stdDev="+stdDev);

		return new DefaultGroundMotion(mean, stdDev, phi, tau);
	}

	// Mean ground motion model
	private static final double calcMean(Coeffs c, double Mw, double rJB,
			double vs30, double z1p0, FaultStyle style, double pgaRock) {

		// Source/Event Term -- Equation 2
		double Fe = calcSourceTerm(c, Mw, style);
		
		// Path Term -- Equations 3, 4
		double R = sqrt(rJB * rJB + c.h * c.h);
		double Fp = calcPathTerm(c, Mw, R);

		// Site Linear Term -- Equation 6
		double lnFlin = calcLnFlin(c, vs30);
		
		// Site Nonlinear Term -- Equations 7, 8
		double f2 = calcF2(c, vs30);
		double lnFnl = F1 + f2 * log((pgaRock + F3) / F3);

		// Basin depth term -- Equations 9, 10 , 11
		double Fdz1 = calcFdz1(c, vs30, z1p0);
		
		// Total site term -- Equation 5
		double Fs = lnFlin + lnFnl + Fdz1;

		// Total model -- Equation 1
		return Fe + Fp + Fs;
	}
	
	public final double calcLnFlin(IMT imt, double vs30) {
		coeffs.set(imt);
		return calcLnFlin(coeffs, vs30);
	}
	
	public final double calcF2(IMT imt, double vs30) {
		coeffs.set(imt);
		return calcF2(coeffs, vs30);
	}
	
	public final double calcFdz1(IMT imt, double vs30, double z1p0) {
		coeffs.set(imt);
		return calcFdz1(coeffs, vs30, z1p0);
	}

	private static final double calcLnFlin(Coeffs c, double vs30) {
		double vsLin = (vs30 <= c.Vc) ? vs30 : c.Vc;
		double lnFlin = c.c * log(vsLin / V_REF);
		return lnFlin;
	}

	private static final double calcF2(Coeffs c, double vs30) {
		double f2 = c.f4 * (exp(c.f5 * (min(vs30, 760.0) - 360.0)) - 
				exp(c.f5 * (760.0 - 360.0)));
		return f2;
	}
	
	private static final double calcFdz1(Coeffs c, double vs30, double z1p0) {
		double DZ1 = calcDeltaZ1(z1p0, vs30);
		double Fdz1 = (c.imt().isSA() && c.imt().getPeriod() >= 0.65) ?
			(DZ1 <= c.f7 / c.f6) ? c.f6 * DZ1 : c.f7
				: 0.0;
		return Fdz1;
	}
	
	// Median PGA for ref rock (Vs30=760m/s); always called with PGA coeffs
	private static final double calcPGArock(Coeffs c, double Mw, double rJB,
			FaultStyle style) {
		
		// Source/Event Term -- Equation 2
		double FePGA = calcSourceTerm(c, Mw, style);
		
		// Path Term -- Equation 3
		double R = sqrt(rJB * rJB + c.h * c.h);
		double FpPGA = calcPathTerm(c, Mw, R);

		// No Site term -- [Vs30rk==760] < [Vc(PGA)=1500] && 
		// ln(Vs30rk / V_REF) = ln(760/760) = 0

		// Total PGA model -- Equation 1
		return exp(FePGA + FpPGA);
	}

	// Source/Event Term -- Equation 2
	private static final double calcSourceTerm(Coeffs c, double Mw,
			FaultStyle style) {
		double Fe = (style == STRIKE_SLIP) ? c.e1 :
					(style == REVERSE) ? c.e3 :
					(style == NORMAL) ? c.e2 : c.e0; // else UNKNOWN
		double MwMh = Mw - c.Mh;
		Fe += (Mw <= c.Mh) ? c.e4 * MwMh + c.e5 * MwMh * MwMh : c.e6 * MwMh;
		return Fe;
	}
	
	// Path Term, base model -- Equation 3
	private static final double calcPathTerm(Coeffs c, double Mw, double R) {
		return (c.c1 + c.c2 * (Mw - M_REF)) * log(R / R_REF) +
			(c.c3 + DC3_CA_TW) * (R - R_REF);
	}
	
	// Calculate delta Z1 in km as a  function of vs30 and using the default 
	// model of ChiouYoungs_2013 -- Equations 10, 11
	private static final double calcDeltaZ1(double z1p0, double vs30) {
		if (Double.isNaN(z1p0)) return 0.0;
		double vsPow4 = vs30 * vs30 * vs30 * vs30;
		return z1p0 - exp(-7.15 / 4.0 * log((vsPow4 + A) / B)) / 1000.0;
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(Coeffs c, double Mw, double rJB,
			double vs30) {
		double tau = calcTau(c, Mw);
		
		double phiMRV = calcPhi(c, Mw, rJB, vs30);

		return calcStdDev(phiMRV, tau);
	}
	
	private static final double calcStdDev(double phiMRV, double tau) {
		// Total model -- Equation 13
		return sqrt(phiMRV * phiMRV + tau * tau);
	}

	private static double calcTau(Coeffs c, double Mw) {
		// Inter-event Term -- Equation 14
		double tau = (Mw >= 5.5) ? c.tau2 : (Mw <= 4.5) ? c.tau1 : c.tau1 +
			(c.tau2 - c.tau1) * (Mw - 4.5);
		return tau;
	}

	private static double calcPhi(Coeffs c, double Mw, double rJB, double vs30) {
		// Intra-event Term -- Equations 15, 16, 17
		double phiM = (Mw >= 5.5) ? c.phi2 : (Mw <= 4.5) ? c.phi1
			: c.phi1 + (c.phi2 - c.phi1) * (Mw - 4.5);
		
		double phiMR = phiM;
		if (rJB > c.R2) {
			phiMR += c.dPhiR;
		} else if (rJB > c.R1) {
			phiMR += c.dPhiR * (log(rJB / c.R1) / log(c.R2 / c.R1));
		}
		
		double phiMRV = phiMR;
		if (vs30 <= V1) {
			phiMRV -= c.dPhiV;
		} else if (vs30 < V2) {
			phiMRV -= c.dPhiV * (log(V2 / vs30) / log(V2 / V1));
		}
		return phiMRV;
	}
	
	// can be useful for debugging
//	public String toString() {
//		return imt.name()+"\t"+(float)Mw+"\t"+(float)rJB+"\t"+(float)vs30+"\t"+(float)z1p0+"\t"+style;
//	}
	
}
