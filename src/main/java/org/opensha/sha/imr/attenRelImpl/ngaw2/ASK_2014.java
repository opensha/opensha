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
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.commons.geo.GeoTools.TO_RAD;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.NORMAL;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.UNKNOWN;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.IMT.PGA;

import java.util.Collection;

import org.opensha.commons.util.Interpolate;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Preliminary implementation of the Abrahamson & Silva (2013) next generation 
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
public class ASK_2014 implements NGAW2_GMM {

	public static final String NAME = "Abrahamson, Silva \u0026 Kamai (2014)";
	public static final String SHORT_NAME = "ASK2014";
	
	private final Coeffs coeffs;
		
	// author declared constants
	private static final double A3 = 0.275;
	private static final double A4 = -0.1;
	private static final double A5 = -0.41;
	private static final double M2 = 5.0;
	private static final double N = 1.5;
	private static final double C4 = 4.5;
	
	// implementation constants
	private static final double A = pow(610, 4);
	private static final double B = pow(1360, 4) + A;
	private static final double VS_RK = 1180.0;
	private static final double A2_HW = 0.2;
	private static final double H1 = 0.25;
	private static final double H2 = 1.5;
	private static final double H3 = -0.75;
	// private static final double RY0 = -1.0;
	private static final double PHI_AMP_SQ = 0.16;

	private class Coeffs extends Coefficients {
		
		// TODO inline constance coeffs with final statics
		
		double a1, a2, a6, a8, a10, a12, a13, a14, a15,
		a17, a43, a44, a45, a46, b, c, s1e, s2e, s3, s4, s1m, s2m,
		s5, s6, M1, Vlin;

		// same for all periods; replaced with constant
		double a3, a4, a5, c4, n;
		// unused
		double a7, a11, a16;
		// Japan model
		double a25, a28, a29, a31, a36, a37, a38, a39, a40, a41, a42;

		Coeffs() {
			super("ASK14.csv");
			set(PGA);
		}
	}
	
	/**
	 * Constructs a new instance of this attenuation relationship.
	 */
	public ASK_2014() {
		coeffs = new Coeffs();
	}

	
	private IMT imt = null;
	
	private double Mw = NaN;
	private double rJB = NaN;
	private double rRup = NaN;
	private double rX = NaN;
	private double rY0 = -1.0;
	private double dip = NaN;
	private double width = NaN;
	private double zTop = NaN;
	private double vs30 = NaN;
	private boolean vsInf = true;
	private double z1p0 = NaN;
	private FaultStyle style = UNKNOWN;
	
	@Override
	public ScalarGroundMotion calc() {
		return calc(imt, Mw, rJB, rRup, rX, rY0, dip, width, zTop, vs30,
			vsInf, z1p0, style);
	}
	
	@Override public String getName() { return ASK_2014.NAME; }

	@Override public void set_IMT(IMT imt) { this.imt = imt; }

	@Override public void set_Mw(double Mw) { this.Mw = Mw; }
	
	@Override public void set_rJB(double rJB) { this.rJB = rJB; }
	@Override public void set_rRup(double rRup) { this.rRup = rRup; }
	@Override public void set_rX(double rX) { this.rX = rX; }
	
	@Override public void set_dip(double dip) { this.dip = dip; }
	@Override public void set_width(double width) { this.width = width; }
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
	 * @param rY0 distance from end of rupture
	 * @param dip of rupture (in degrees)
	 * @param width down-dip rupture width (in km)
	 * @param zTop depth to the top of the rupture (in km)
	 * @param vs30 average shear wave velocity in top 30 m (in m/sec)
	 * @param vsInferred whether vs30 is an inferred or measured value
	 * @param z1p0 depth to V<sub>s</sub>=1.0 km/sec (in km)
	 * @param style of faulting
	 * @return the ground motion
	 */
	public final ScalarGroundMotion calc(IMT imt, double Mw, double rJB,
			double rRup, double rX, double rY0, double dip, double width,
			double zTop, double vs30, boolean vsInferred, double z1p0,
			FaultStyle style) {

		coeffs.set(imt);
		return calcValues(coeffs, Mw, rJB, rRup, rX, rY0, dip, width, zTop,
			vs30, vsInferred, z1p0, style);
	}
	

	private DefaultGroundMotion calcValues(Coeffs c, double Mw, double rJB,
			double rRup, double rX, double rY0, double dip, double width,
			double zTop, double vs30, boolean vsInferred, double z1p0,
			FaultStyle style) {

		// @formatter:off

		//  ****** Mean ground motion and standard deviation model ****** 
		
		// Base Model (magnitude and distance dependence for strike-slip eq)
		
		// Magnitude dependent taper -- Equation 4
		double c4mag = (Mw > 5) ? C4 : 
					   (Mw > 4) ? C4 - (C4 - 1.0) * (5.0 - Mw) : 1.0;
					   
		// -- Equation 3
		double R = sqrt(rRup * rRup + c4mag * c4mag);
				
		// -- Equation 2
		double MaxMwSq = (8.5 - Mw) * (8.5 - Mw);
		double MwM1 = Mw - c.M1;

		double f1 = c.a1 + c.a17 * rRup;
		if (Mw > c.M1) {
			f1 += A5 * MwM1 + c.a8 * MaxMwSq + (c.a2 + A3 * MwM1) * log(R);
		} else if (Mw >= M2) {
			f1 += A4 * MwM1 + c.a8 * MaxMwSq + (c.a2 + A3 * MwM1) * log(R);
		} else {
			double M2M1 = M2 - c.M1;
			double MaxM2Sq = (8.5 - M2) * (8.5 - M2);
			double MwM2 = Mw - M2;
			// a7 == 0; removed c.a7 * MwM2 * MwM2 below
			f1 += A4 * M2M1 + c.a8 * MaxM2Sq + c.a6 * MwM2 + (c.a2 + A3 * M2M1) * log(R);
		}
		
		// Aftershock Model (Class1 = mainshock; Class2 = afershock)
		// not currently used as rJBc (the rJB from the centroid of the parent
		// Class1 event) is not defined; requires event type flag -- Equation 7
		// double f11 = 0.0 * c.a14;
		//if (rJBc < 5) {
		//	f11 = c.a14;
		//} else if (rJBc <= 15) {
		//	f11 = c.a14 * (1 - (rJBc - 5.0) / 10.0);
		//}

		// Hanging Wall Model
		double f4 = 0.0;
		// short-circuit: f4 is 0 if rJB >= 30, rX < 0, Mw <= 5.5, zTop > 10
		// these switches have been removed below
		if (rJB < 30 && rX >= 0.0 && Mw > 5.5 && zTop <= 10.0) {
			
			// ... dip taper -- Equation 11
			double T1 = (dip > 30.0) ? (90.0 - dip) / 45 : 1.33333333; // 60/45
			
			// ... mag taper -- Equation 12
			double dM = Mw - 6.5;
			double T2 = (Mw >= 6.5) ?
					1 + A2_HW * dM :
					1 + A2_HW * dM - (1 - A2_HW) * dM * dM;
			
			// ... rX taper -- Equation 13
			double T3 = 0.0;
			double r1 = width * cos(dip * TO_RAD);
			double r2 = 3 * r1;
			if (rX <= r1) {
				double rXr1 = rX / r1; 
				T3 = H1 + H2 * rXr1 + H3 * rXr1 * rXr1;
			} else if (rX <= r2) {
				T3 = 1-(rX-r1)/(r2-r1);
			}
			
			// ... zTop taper -- Equation 14
			double T4 = 1 - (zTop * zTop) / 100.0;
			
			// ... rX, rY0 taper -- Equation 15b
			double T5 = (rJB == 0.0) ? 1.0 : 1 - rJB / 30.0;
			
			// total -- Equation 10
			f4 = c.a13 * T1 * T2 * T3 * T4 * T5;
		}
		
		// Depth to Rupture Top Model -- Equation 16
		double f6 = c.a15;
		if (zTop < 20.0) f6 *= zTop / 20.0;

		// Style-of-Faulting Model -- Equations 5 & 6
		// Note: REVERSE doesn not need to be implemented as f7 always resolves
		// to 0 as a11==0; we skip f7 here
		double f78 = (style == NORMAL) ? 
			(Mw > 5.0) ? c.a12 : 
			(Mw >= 4.0) ? c.a12 * (Mw - 4) : 0.0
				: 0.0;

		// Soil Depth Model -- Equation 17
		double f10 = calcSoilTerm(c, vs30, z1p0);
		
		// Site Response Model
		double f5 = 0.0;
		double v1 = getV1(c.imt()); // -- Equation 9
		double vs30s = (vs30 < v1) ? vs30 : v1; // -- Equation 8

		// Site term -- Equation 7
		double saRock = 0.0; // calc Sa1180 (rock reference) if necessary 
		if (vs30 < c.Vlin) {
			// soil term (f10) for Sa1180 is zero per R. Kamai's code where
			// Z1 < 0 for Sa1180 loop
			double vs30s_rk = (VS_RK < v1) ? VS_RK : v1;
			// use this f5 form for Sa1180 Vlin is always < 1180
			double f5_rk = (c.a10 + c.b * N) * log(vs30s_rk / c.Vlin);
			saRock = exp(f1 + f78 + f5_rk + f4 + f6);
			f5 = c.a10 * log(vs30s / c.Vlin) - c.b * log(saRock + c.c) + c.b *
				log(saRock + c.c * pow(vs30s / c.Vlin, N));
		} else {
			f5 = (c.a10 + c.b * N) * log(vs30s / c.Vlin);
		}

		// total model (no aftershock f11) -- Equation 1
		double mean = f1 + f78 + f5 + f4 + f6 + f10; 
		
		
		// ****** Aleatory uncertainty model ******
		
		// the code below removes unnecessary square-sqrt pairs
		
		// Intra-event term -- Equation 24
		double phiAsq = vsInferred ? 
			getPhiA(Mw, c.s1e, c.s2e) : 
			getPhiA(Mw, c.s1m, c.s2m);
		phiAsq *= phiAsq;
		
		// Inter-event term -- Equation 25
		double tauB = getTauA(Mw, c.s3, c.s4);
		
		// Intra-event term with site amp variability removed -- Equation 27
		double phiBsq = phiAsq - PHI_AMP_SQ;
		
		// Parital deriv. of ln(soil amp) w.r.t. ln(SA1180) -- Equation 30
		// saRock subject to same vs30 < Vlin test as in mean model
		double dAmp_p1 = get_dAmp(c.b, c.c, c.Vlin, vs30, saRock) + 1.0;
		
		// phi squared, with non-linear effects -- Equation 28
		double phiSq = phiBsq * dAmp_p1 * dAmp_p1 + PHI_AMP_SQ;

		// tau squared, with non-linear effects -- Equation 29
		double tau = tauB * dAmp_p1;
		
		// total std dev
		double stdDev = sqrt(phiSq + tau * tau);
		
		return new DefaultGroundMotion(mean, stdDev, Math.sqrt(phiSq), tau);

	}
	
	// -- Equation 9
	private static final double getV1(IMT imt) {
		Double T = imt.getPeriod();
		if (T == null) return 1500.0;
		if (T >= 3.0) return 800.0;
		if (T > 0.5) return exp( -0.35 * log(T / 0.5) + log(1500.0));
		return 1500.0;
	}
	
	// used for interpolation in calcSoilTerm(), below
	private static final double[] VS_BINS = {150d, 250d, 400d, 700d, 1000d};
		
	// Soil depth model adapted from CY13 form -- Equation 17
	private static final double calcSoilTerm(Coeffs c, double vs30, double z1p0) {
		// short circuit; default z1 will be the same as z1ref
		if (Double.isNaN(z1p0)) return 0.0;
		// -- Equation 18
		double vsPow4 = vs30 * vs30 * vs30 * vs30;
		double z1ref = exp(-7.67 / 4.0 * log((vsPow4 + A) / B)) / 1000.0; // km

//		double z1c = (vs30 > 500.0) ? c.a46 :
//					 (vs30 > 300.0) ? c.a45 :
//					 (vs30 > 200.0) ? c.a44 : c.a43;

		// new interpolation algorithm
		double[] vsCoeff = {c.a43, c.a44, c.a45, c.a46, c.a46};
		double z1c = Interpolate.findY(VS_BINS, vsCoeff, vs30);
		
		return z1c * log((z1p0 + 0.01) / (z1ref + 0.01));
	}
	
	// -- Equation 24
	private static final double getPhiA(double Mw, double s1, double s2) {
		return Mw < 4.0 ? s1 :
			   Mw > 6.0 ? s2 : 
			   s1 + ((s2 - s1) / 2) * (Mw - 4.0);
	}
	
	// -- Equation 25
	private static final double getTauA(double Mw, double s3, double s4) {
		return Mw < 5.0 ? s3 :
			   Mw > 7.0 ? s4 :
			   s3 + ((s4 - s3) / 2) * (Mw - 5.0);
	}

	// -- Equation 30
	private static final double get_dAmp(double b, double c, double vLin,
			double vs30, double saRock) {
		if (vs30 >= vLin) return 0.0;
		return (-b * saRock) / (saRock + c) +
			    (b * saRock) / (saRock + c * pow(vs30 / vLin, N));
	}
	
	// can be useful for debugging
//	public String toString() {
//		return imt.name()+"\t"+(float)Mw+"\t"+(float)rJB+"\t"+(float)rRup+"\t"+(float)rX
//				+"\t"+(float)rY0+"\t"+(float)dip+"\t"+(float)width+"\t"+(float)zTop
//				+"\t"+(float)vs30+"\t"+vsInf+"\t"+(float)z1p0+"\t"+style;
//	}
	
}
