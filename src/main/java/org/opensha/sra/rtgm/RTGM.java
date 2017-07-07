package org.opensha.sra.rtgm;

import static com.google.common.base.Preconditions.checkNotNull;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.Interpolate;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

/**
 * Threadable utility class for calculating risk-targeted ground motions (RTGM).
 * Implementation is an adaptation of Matlab codes provided by N. Luco. Class
 * supplies one static utility method to {@code create()} a new RTGM 
 * instance. Invokation of {@code call()} triggers an iterative calculation of
 * risk-targeted ground motion based on a target risk and the hazard curve
 * supplied at construction, returning a reference to the {@code this} instance.
 * It can then be queried for stepwise and final results.
 * 
 * <p><i>Note:</i> Calculation parameters are currently fixed following Matlab
 * source but could be updated to be modifiable.</p>
 * 
 * <p>References: <ol> <li>Luco N, Ellingwood BR, Hamburger RO, Hooper JD,
 * Kimball JK & Kircher CA (2007), Risk-Targeted versus Current Seismic Design
 * Maps for the Conterminous United States, <i>Proceedings of the 2007
 * Structural Engineers Association of California (SEAOC) Convention</i>.</li>
 * <li>Building Seismic Safety Council (2009), NEHRP Recommended Seismic
 * Provisions for New Buildings and Other Structures (FEMA P-750): Part I,
 * Provisions, <i>Federal Emergency Management Agency</i>, Washington D.C., pp.
 * 5-8, 10-18, 67-71 & 92-93.</li> </ol></p>
 * 
 * @author Nicolas Luco (nluco@usgs.gov)
 * @author Peter Powers (pmpowers@usgs.gov)
 * @version $Id:$
 */
public class RTGM implements Callable<RTGM> {

	// Target risk in terms of annual frequency
	private static final double TARGET_RISK = -Math.log(1 - 0.01) / 50;

	// Probability on fragility curve at RTGM
	private static final double FRAGILITY_AT_RTGM = 0.10;

	// Logarithmic standard deviation of fragility curve
	private static final double BETA_DEFAULT = 0.6;
	private double beta = BETA_DEFAULT;

	// Annual frequency of exceedance for Uniform-Hazard Ground Motion (UHGM)
	// UHGM is both denominator of risk coefficient and initial guess for RTGM 
	// 2% PE 50yrs
	private static final double AFE4UHGM = -Math.log(1 - 0.02) / 50;
	
	// RTGM iteration limit
	private static final int MAX_ITERATIONS = 6;
	
	// Resampling interval (used in log space)
	private static final double UPSAMPLING_FACTOR = 1.05;
	
	// Minimum spectral acceleration used in resampling
	private static final double MIN_SA = 0.001;

	// Tolerance when comparing calculated risk to target
	private static final double TOLERANCE = 0.01;

	// hazard curve reference
	private DiscretizedFunc hazCurve;
	
	// retreivables
	private double riskCoeff = Double.NaN;
	private double rtgm = Double.NaN;
	private List<Double> rtgmIters = null;
	private List<Double> riskIters = null;
	private Frequency sa;
	private int index = -1;
	
	private RTGM() {}
	
	/**
	 * Creates a new RTGM calculation and result container for the supplied
	 * hazard curve. Users must call {@code call()} to initiate calculation of a
	 * risk-targeted ground motion that can be retreived using {@code get()}.
	 * 
	 * @param hazCurve to process
	 * @param sa specifies the period of the supplied curve; if not {@code null}
	 *        a conversion factor of 1.1 (for 0.2sec) or 1.3 (for 1sec) will be
	 *        applied to the RTGM returned by {@code get()}
	 * @param beta the fragility curve standard deviation; if {@code null} the
	 *        default value, 0.6, is used
	 * @return an RTGM calculation and result container
	 */
	public static RTGM create(DiscretizedFunc hazCurve, Frequency sa, Double beta) {
		checkNotNull(hazCurve, "Supplied curve is null");
		RTGM instance = new RTGM();
		instance.hazCurve = cleanCurve(hazCurve);
		// geoMean to maxHorizDir component conversion
		//    this could be done afterwards meaning scale the rtgm after
		//    rather than incur the overhead of creating a new function
		if (sa != null) instance.sa = sa; //hazCurve = scaleHC(hazCurve, sa.scale);
		if (beta != null) instance.beta = beta;
		return instance;
	}
	
	/**
	 * Creates a new RTGM calculation and result container for the supplied
	 * hazard curve that stores an internal index. The index may be used to
	 * identify the RTGM container when working with an {@code ExecutorService}.
	 * Users must call {@code call()} to initiate calculation of a risk-targeted
	 * ground motion that can be retreived using {@code get()}.
	 * 
	 * @param hazCurve to process
	 * @param sa specifies the period of the supplied curve; if not {@code null}
	 *        a conversion factor of 1.1 (for 0.2sec) or 1.3 (for 1sec) will be
	 *        applied to the RTGM returned by {@code get()}
	 * @param beta the fragility curve standard deviation; if {@code null} the
	 *        default value, 0.6, is used
	 * @param index of
	 * @return an RTGM calculation and result container
	 */
	public static RTGM createIndexed(DiscretizedFunc hazCurve, Frequency sa,
			Double beta, int index) {
		RTGM instance = create(hazCurve, sa, beta);
		instance.index = index;
		return instance;
	}
	
	/**
	 * Triggers the internal iterative RTGM calculation.
	 * @return a reference to {@code this}
	 * @throws RuntimeException if internal RTGM calculation exceeds 6
	 *         iterations
	 */
	@Override
	public RTGM call() {
		calculate(hazCurve);
		return this;
	}
	
	/**
	 * Returns the risk-targeted ground motion for the hazard curve supplied at
	 * creation.
	 * @return the risk targeted ground motion
	 */
	public double get() {
		// In the USGS seismic design maps, hazard curves are scaled by a
		// frequency dependent factor. If a frequency was supplied at creation,
		// the corresponding scale factor is applied here to the rtgm value.
		return (sa != null) ? rtgm * sa.scale : rtgm;
	}
	
	/**
	 * Returns the optionally supplied index or -1 if none was supplied.
	 * @return the RTGM index
	 */
	public int index() {
		return index;
	}
		
	/**
	 * Returns the risk coefficient for this RTGM calculation.
	 * @return the risk coefficient
	 */
	public double riskCoeff() {
		return riskCoeff;
	}
	
	/**
	 * Returns the RTGM result history. Calls to this method prior
	 * to invoking {@code call()} will return {@code null}.
	 * @return a {@code List} of the iteratively determined RTGM values
	 */
	public List<Double> rtgmIterations() {
		return rtgmIters;
	}
	
	/**
	 * Returns the risk value result history. Calls to this method prior
	 * to invoking {@code call()} will return {@code null}.
	 * @return a {@code List} of the iteratively determined risk values
	 */
	public List<Double> riskIterations() {
		return riskIters;
	}
	
	/**
	 * Return the originally supplied hazard curve.
	 * @return the source hazard curve
	 */
	public DiscretizedFunc hazardCurve() {
		return hazCurve;
	}

	private void calculate(DiscretizedFunc hazCurve) {
		
		// TODO I really wish we had a HazardCurve object that would guarantee
		// monotonically increasing x-values and flat, or monotonically
		// decreasing, y-values

		// uniform hazard ground motion
		double uhgm = hazCurve.getFirstInterpolatedX_inLogXLogYDomain(AFE4UHGM);

		rtgmIters = Lists.newArrayList();
		riskIters = Lists.newArrayList();
		Map<Double, Double> interpMap = Maps.newTreeMap();

		// For adequate discretization of fragility curves...
		DiscretizedFunc upsampHazCurve = logResample(hazCurve, MIN_SA,
			UPSAMPLING_FACTOR);
		double errorRatio = Double.NaN;

		// Iterative calculation of RTGM
		for (int i = 0; i < MAX_ITERATIONS; i++) {

			//int insertIdx = 1;
			double rtgmTmp, riskTmp;
			
			if (i == 0) {
//				rtgmIters.add(uhgm);
				rtgmTmp = uhgm;
			} else if (i == 1) {
//				rtgmIters.add(rtgmIters.get(0) * errorRatio);
				rtgmTmp = rtgmIters.get(0) * errorRatio;
			} else {
//				rtgmTmp = Interpolate.findLogLogY(
//					Doubles.toArray(riskIters), Doubles.toArray(rtgmIters),
//					TARGET_RISK);
//				System.out.println("keys: " + Arrays.toString(Doubles.toArray(interpMap.keySet())));
//				System.out.println("vals: " + Arrays.toString(Doubles.toArray(interpMap.values())));
				rtgmTmp = Interpolate.findLogLogY(
					Doubles.toArray(interpMap.keySet()),
					Doubles.toArray(interpMap.values()),
					TARGET_RISK);
//				rtgmIters.add(rtgmTmp);
				
//				System.out.println(TARGET_RISK);
//				System.out.println("YO: " + rtgmTmp);
//				System.out.println("POS: " + Collections.binarySearch(riskIters, TARGET_RISK));
//				int idx = Collections.binarySearch(riskIters, TARGET_RISK);
//				insertIdx = (idx < 0) ? -(idx + 1) : idx;
			}

			// Generate fragility curve corresponding to current guess for RTGM
//			FragilityCurve fc = new FragilityCurve(rtgmIters.get(i),
//				upsampHazCurve);
			FragilityCurve fc = new FragilityCurve(rtgmTmp, upsampHazCurve, beta);

			// Calculate risk using fragility curve generated above & upsampled
			// hazard curve
			riskTmp = riskIntegral(fc.pdf(), upsampHazCurve);
//			riskIters.add(riskIntegral(fc.pdf(), upsampHazCurve));

			// Check risk calculated above against target risk
//			errorRatio = checkRiskAgainstTarget(riskIters.get(i));
			errorRatio = checkRiskAgainstTarget(riskTmp);
//			System.out.println(riskTmp + " " + rtgmTmp);
			riskIters.add(riskTmp);
			rtgmIters.add(rtgmTmp);
			interpMap.put(riskTmp, rtgmTmp);

			// Exit if ratio of calculated and target risks is within tolerance
			if (errorRatio == 1) break;

			// If number of iterations has reached specified maximum, exit loop
			if (i == MAX_ITERATIONS) {
				throw new RuntimeException("RTGM: max # iterations reached");
			}
		}

		rtgm = (errorRatio != 1) ? Double.NaN : rtgmIters
			.get(rtgmIters.size() - 1);
		riskCoeff = rtgm / uhgm;

		for (int i = 0; i < riskIters.size(); i++) {
			riskIters.set(i, riskIters.get(i) / TARGET_RISK);
		}
	}
	
	/* Evaluates the Risk Integral */
	private static double riskIntegral(DiscretizedFunc fragPDF,
			DiscretizedFunc hazCurve) {
		// this method assumes that the fragility PDF is defined at the same
		// spectral accelerations as the hazard curve (i.e. at HazardCurve.SAs)
		RTGM_Util.multiplyFunc(fragPDF, hazCurve); // multiply fragPDF in place
		return RTGM_Util.trapz(fragPDF);
	}
	
	/* Compares calculated risk to target risk; returns 1 if within tolerance */
	private static double checkRiskAgainstTarget(double risk) {
		double er = risk / TARGET_RISK; // error ratio
		return (Math.abs(er - 1) < TOLERANCE) ? 1 : er;
	}
	
	/* Resamples hc with supplied interfal over min to f.max */
	private static DiscretizedFunc logResample(DiscretizedFunc f, double min, 
			double interval) {
		double[] oldXs = new double[f.size()];
		double[] oldYs = new double[f.size()];
		for (int i=0; i<f.size(); i++) {
			oldXs[i] = f.getX(i);
			oldYs[i] = f.getY(i);
		}
		double[] newXs = DataUtils.buildLogSequence(min, f.getMaxX(), interval,
			true);
		double[] newYs = Interpolate.findLogLogY(oldXs, oldYs, newXs);
		DiscretizedFunc fOut = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<newXs.length; i++) {
			fOut.set(newXs[i], newYs[i]);
		}
		return fOut;
	}
	
	/* Cleans curve of zero valued points via copy and ensures size > 2 */
	private static DiscretizedFunc cleanCurve(DiscretizedFunc f) {
		ArbitrarilyDiscretizedFunc fOut = new ArbitrarilyDiscretizedFunc();
		for (Point2D p : f) {
			if (p.getY() != 0) fOut.set(p);
		}
		Preconditions.checkArgument(
			fOut.size() > 2,
			"Curve must have more than two non-zero y-values \n" + f);
		return fOut;
	}


	/**
	 * Wrapper class for fragility curve data.
	 */
	private static class FragilityCurve {
		
		private double median;
		private double beta;
		private XY_DataSet model;
		private DiscretizedFunc pdf;
		private DiscretizedFunc cdf;
		
		FragilityCurve(double rtgm, DiscretizedFunc model, double beta) {
			median = rtgm / Math.exp(RTGM_Util.norminv(FRAGILITY_AT_RTGM) * beta);
			this.model = model;
			this.beta = beta;
		}
				
		double median() { 
			return median;
		}
		
		// NOTE not thread safe but only one instance per RTGM obj
		
		DiscretizedFunc pdf() {
			if (pdf == null) {
				pdf = new ArbitrarilyDiscretizedFunc();
				for (Point2D p : model) {
					pdf.set(p.getX(), RTGM_Util.logNormalDensity(p.getX(),
						Math.log(median), beta));
				}
			}
			return pdf;
		}

		DiscretizedFunc cdf() {
			if (cdf == null) {
				cdf = new ArbitrarilyDiscretizedFunc();
				for (Point2D p : model) {
					cdf.set(p.getX(), RTGM_Util.logNormalCumProb(p.getX(),
						Math.log(median), beta));
				}
			}
			return cdf;
		}
	}
	
	@SuppressWarnings("javadoc")
	public enum Frequency {
		SA_0P20(1.1),
		SA_1P00(1.3);
		private double scale;
		private Frequency(double scale) {
			this.scale = scale;
		}
	}

	public static void main(String[] args) {
//		double[] xs = {0.005,0.0075,0.0113,0.0169,0.0253,0.038,0.057,0.0854,0.128,0.192,0.288,0.432,0.649,0.973,1.46,2.19,3.28,4.92,7.38};
//		double[] ys = {0.585,0.5209,0.4395,0.3524,0.2688,0.1962,0.1382,0.09381,0.06055,0.03676,0.02105,0.01151,0.006008,0.002944,0.001291,0.0004791,0.0001413,3.025e-05,3.828e-06};
//		double[] xs = { 0.005,0.0075,0.0113,0.0169,0.0253,0.038,0.057,0.0854,0.128,0.192,0.288,0.432,0.649,0.973,1.46,2.19,3.28,4.92,7.38};
//		double[] ys = {0.4264,0.3804,0.3209,0.2556,0.1919,0.1368,0.09399,0.06247,0.03985,0.02437,0.01464,0.009007,0.005703,0.003489,0.001874,0.0008187,0.000277,6.776e-05,1.076e-05};

		
		double[] xs = { 0.0025,0.00375,0.00563,0.00844, 0.0127,0.019, 0.0285, 0.0427, 0.0641, 0.0961,0.144,0.216,0.324,0.487,0.73,1.09,1.64,2.46,3.69,5.54};
		double[] ys = { 0.4782,0.3901,0.3055,0.2322,0.1716,0.1241,0.08621,0.05687,0.03492,0.01985,0.01045, 0.005095, 0.002302,0.0009371,0.0003308,9.488e-05,1.952e-05,2.174e-06,8.553e-08,1.315e-10};

		DiscretizedFunc f = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<xs.length; i++) {
			f.set(xs[i], ys[i]);
		}
		
		RTGM rtgm = RTGM.create(f, Frequency.SA_1P00, 0.8);
		System.out.println(rtgm.call());
		System.out.println(rtgm.riskCoeff());
		System.out.println(rtgm.rtgmIterations());
		System.out.println(rtgm.riskIterations());
		
	}

}


//      **********       MATLAB SOURCE      ***************
//
//function [ RTGM, RiskCoefficient ] = RTGM_Calculator( HazardCurve )
//
//%
//% Author:  Nicolas Luco (nluco@usgs.gov)
//% Last Revised:  23 February 2012
//% References:
//% [1] Luco N, Ellingwood BR, Hamburger RO, Hooper JD, Kimball JK & Kircher CA (2007),
//%     "Risk-Targeted versus Current Seismic Design Maps for the Conterminous United States,"
//%     Proceedings of the 2007 Structural Engineers Association of California (SEAOC) Convention.
//% [2] Building Seismic Safety Council (2009), "NEHRP Recommended Seismic Provisions 
//%     for New Buildings and Other Structures (FEMA P-750): Part I, Provisions," 
//%     Federal Emergency Management Agency, Washington D.C., pp. 5-8, 10-18, 67-71 & 92-93.
//%
//% INPUT:
//% ======
//% HazardCurve.SAs  = Spectral Response Accelerations  ( column vector )
//% HazardCurve.AFEs = Annual Frequencies of Exceedance ( column vector )
//%
//% OUTPUT:
//% =======
//% RTGM            = Risk-Targeted Ground Motion         ( scalar )
//% RiskCoefficient = RTGM / Uniform-Hazard Ground Motion ( scalar )
//%
//
//
//% Display time stamp
//disp( strvcat( ' ' , datestr(now) ) )
//
//
//% MODEL PARAMETERS
//% ================
//
//disp( strvcat( ' ' , 'Model Parameters', '----------------' ) )
//
//% Target risk in terms of annual frequency
//TARGET_RISK = - log( 1 - 0.01 )/ 50
//
//% Probability on fragility curve at RTGM
//FRAGILITY_AT_RTGM = 0.10
//
//% Logarithmic standard deviation of fragility curve
//BETA = 0.6
//
//% Annual frequency of exceedance for Uniform-Hazard Ground Motion (UHGM)
//% UHGM is both denominator of risk coefficient and initial guess for RTGM 
//AFE4UHGM = - log( 1 - 0.02 )/ 50
//%AFE4UHGM = 2 * TARGET_RISK
//
//% See also "UPSAMPLING_FACTOR" and "TOLERANCE" in subfunctions below
//
//
//% CALCULATIONS
//% ============
//
//disp( strvcat( ' ' , 'Calculated RTGM', '---------------' ) )
//
//% Uniform-Hazard Ground Motion
//UHGM = LogLog_Interp1( HazardCurve.AFEs, HazardCurve.SAs, AFE4UHGM );
//
//% For adequate discretization of fragility curves ...
//[ UpsampledHC ] = Upsample_Hazard_Curve( HazardCurve );
//
//% Iterative calculation of RTGM
//% -----------------------------
//MAX_N_ITERATIONS = 6;
//for i = 1:MAX_N_ITERATIONS
//   
//    if i == 1
//        RTGMi(i) = UHGM;
//    elseif i ==2 
//        RTGMi(i) = RTGMi(1) * Error_Ratio;
//    else
//        RTGMi(i) = LogLog_Interp1( RiskValues, RTGMi, TARGET_RISK );
//    end
//    
//    % Generate fragility curve corresponding to current guess for RTGM
//    FragilityCurves(i) = Generate_Fragility_Curve( RTGMi(i), FRAGILITY_AT_RTGM, BETA, UpsampledHC.SAs );
//
//    % Calculate risk using fragility curve generated above & upsampled hazard curve
//    RiskValues(i) = Risk_Integral( FragilityCurves(i).PDF, UpsampledHC );
//        
//    % Check risk calculated above against target risk
//    Error_Ratio = Check_Risk_against_Target( RiskValues(i), TARGET_RISK );
//
//    % If ratio of calculated and target risks is 1 (within tolerance), exit loop
//    if Error_Ratio == 1
//        break
//    end
//    
//    % If number of iterations has reached specified maximum, exit loop
//    if i == MAX_N_ITERATIONS
//        disp( 'MAX # ITERATIONS REACHED' )
//    end
//    
//end
//
//% Specify output
//if Error_Ratio ~= 1
//    RTGM = NaN
//else
//    RTGM = RTGMi(end)
//end
//RiskCoefficient = RTGM / UHGM
//
//
//% ADDITIONAL OUTPUT
//% =================
//
//disp( strvcat( ' ' , 'Iteration Summary', '-----------------' ) )
//
//% Display RTGM iterations
//RTGMi
//RISKiDivideByTARGET = RiskValues / TARGET_RISK
//
//% Plot RTGM iterations
//%Plot_RTGM_Calculation_Iterations( UpsampledHC, AFE4UHGM, UHGM, RTGMi, ...
//%                                  FragilityCurves, FRAGILITY_AT_RTGM, TARGET_RISK );
//
//                              
//
//% SUBFUNCTION for upsampling the hazard curve
//% ===========================================
//
//function [ upsampledHC ] = Upsample_Hazard_Curve( originalHC )
//
//UPSAMPLING_FACTOR = 1.05;
//SMALLEST_SA = 0.001;
//LARGEST_SA = max(originalHC.SAs);
//
//upsampledHC.SAs = exp( log(SMALLEST_SA) : log(UPSAMPLING_FACTOR) : log(LARGEST_SA) )';
//if upsampledHC.SAs(end) ~= LARGEST_SA
//    upsampledHC.SAs(end+1) = LARGEST_SA;
//end
//
//upsampledHC.AFEs = LogLog_Interp1( originalHC.SAs, originalHC.AFEs, upsampledHC.SAs );
//
//upsampledHC.Iextrap = ( upsampledHC.SAs < min(originalHC.SAs) ...
//                        | upsampledHC.SAs > max(originalHC.SAs) );
//
//                    
//% SUBFUNCTION for log-log interpolation
//% =====================================
//
//function [ YI ] = LogLog_Interp1( X, Y, XI )
//
//YI = exp( interp1( log(X), log(Y), log(XI), 'linear', 'extrap' ) );
//
//
//% SUBFUNCTION for generating a fragility curve
//% ============================================
//
//function [ FragilityCurve ] = Generate_Fragility_Curve( RTGM, FRAGILITY_AT_RTGM, BETA, SAs )
//
//FragilityCurve.Median = RTGM / exp( norminv( FRAGILITY_AT_RTGM ) * BETA );
//FragilityCurve.PDF = lognpdf( SAs, log(FragilityCurve.Median), BETA );
//FragilityCurve.CDF = logncdf( SAs, log(FragilityCurve.Median), BETA );
//FragilityCurve.SAs = SAs;
//FragilityCurve.Beta = BETA;
//
//
//% SUBFUNCTION for evaluationg the Risk Integral
//% =============================================
//
//function [ Risk ] = Risk_Integral( FragilityCurvePDF, HazardCurve )
//
//% This function assumes that FrigilityCurvePDF is defined at the same
//% spectral accelerations as the hazard curve (i.e. at HazardCurve.SAs)
//Integrand = FragilityCurvePDF .* HazardCurve.AFEs;
//Risk = trapz( HazardCurve.SAs, Integrand );
//
//
//% SUBFUNCTION for checking calculated risk against target risk
//% ============================================================
//
//function [ Error_Ratio ] = Check_Risk_against_Target( Risk, TARGET_RISK )
//
//TOLERANCE = 0.01;
//
//Error_Ratio = Risk / TARGET_RISK;
//% If error is within tolerance, round error to integer value of 1
//if abs( Error_Ratio - 1 ) <= TOLERANCE
//    Error_Ratio = round(1); NOTE this doesn't make sense
//end

