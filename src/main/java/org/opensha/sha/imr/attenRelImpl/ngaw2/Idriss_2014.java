package org.opensha.sha.imr.attenRelImpl.ngaw2;

import static java.lang.Double.NaN;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.REVERSE;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.UNKNOWN;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.IMT.PGA;

import java.util.Collection;

import org.opensha.sha.util.TectonicRegionType;

/**
 * Preliminary implementation of the Idriss (2013) next generation attenuation
 * relationship developed as part of NGA West II.
 * 
 * Idriss (2013) recommends a cap of Vs=1200m/s (implemented) and a distance limit of 150km (not implemented)
 * 
 * Component: RotD50
 * 
 * Implementation details:
 * 
 * Not thread safe -- create new instances as needed
 * 
 * @author Peter Powers
 */
public class Idriss_2014 implements NGAW2_GMM {

	public static final String NAME = "Idriss (2014)";
	public static final String SHORT_NAME = "Idriss2014";
	
	private final Coeffs coeffs;
	
	private static class Coeffs extends Coefficients {
		double a1_lo, a2_lo, a1_hi, a2_hi, a3, b1_lo, b2_lo, b1_hi, b2_hi, xi,
		gamma, phi;
		Coeffs(String cName) {
			super(cName);
		}
	}
	
	/**
	 * Constructs a new instance of this attenuation relationship.
	 */
	public Idriss_2014() {
		coeffs = new Coeffs("Idriss14.csv");
		coeffs.set(PGA);
	}
	
	
	private IMT imt = null;
	private double Mw = NaN;
	private double rRup = NaN;
	private double vs30 = NaN;
	private FaultStyle style = UNKNOWN;

	@Override
	public ScalarGroundMotion calc() {
		return calc(imt, Mw, rRup, vs30, style);
	}

	@Override public String getName() { return NAME; }

	@Override public void set_IMT(IMT imt) { this.imt = imt; }

	@Override public void set_Mw(double Mw) { this.Mw = Mw; }
	
	@Override public void set_rJB(double rJB) {} // not used
	@Override public void set_rRup(double rRup) { this.rRup = rRup; }
	@Override public void set_rX(double rX) {} // not used
	
	@Override public void set_dip(double dip) {} // not used
	@Override public void set_width(double width) {} // not used
	@Override public void set_zTop(double zTop) {} // not used
	@Override public void set_zHyp(double zHyp) {} // not used
	
	@Override public void set_vs30(double vs30) { this.vs30 = vs30; }
	@Override public void set_vsInf(boolean vsInf) {} // not used
	@Override public void set_z2p5(double z2p5) {} // not used
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
	 * @param rRup 3D distance to rupture plane (in km)
	 * @param vs30 average shear wave velocity in top 30 m (in m/sec)
	 * @param style of faulting; only {@code REVERSE} is used
	 * @return the ground motion
	 */
	public final ScalarGroundMotion calc(IMT imt, double Mw,
			double rRup, double vs30, FaultStyle style) {

		coeffs.set(imt);
		
		double mean = calcMean(coeffs, Mw, rRup, style, vs30);
		double stdDev = calcStdDev(coeffs, Mw);

		return new DefaultGroundMotion(mean, stdDev);
	}
	
	// Mean ground motion model - cap of Vs = 1200 m/s
	private static final double calcMean(Coeffs c, double Mw, double rRup,
			FaultStyle style, double vs30) {
		
		double a1 = c.a1_lo, a2 = c.a2_lo;
		double b1 = c.b1_lo, b2 = c.b2_lo;
		if (Mw > 6.75) {
			a1 = c.a1_hi; a2 = c.a2_hi;
			b1 = c.b1_hi; b2 = c.b2_hi;
		}

		return a1 + a2 * Mw + c.a3 * (8.5 - Mw) * (8.5 - Mw) -
			(b1 + b2 * Mw) * log(rRup + 10.0) +
			c.xi * log(min(vs30, 1200.0)) + 
			c.gamma * rRup + (style == REVERSE ? c.phi : 0.0);
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(Coeffs c, double Mw) {
		double s1 = 0.035;
		Double T = c.imt().getPeriod();
		s1 *= (T == null || T <= 0.05) ? log(0.05) : (T < 3.0) ? log(T)
			: log(3d);
		double s2 = 0.06;
		s2 *= (Mw <= 5.0) ? 5.0 : (Mw < 7.5) ? Mw : 7.5;
		return 1.18 + s1 - s2;
	}
	
}
