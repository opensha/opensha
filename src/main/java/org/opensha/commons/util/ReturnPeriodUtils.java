package org.opensha.commons.util;

import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

import com.google.common.base.Preconditions;


/**
 * Class for conversions between return periods and durations assuming Poisson probabilities
 * 
 * @author kevin
 *
 */
public class ReturnPeriodUtils {
	
	/**
	 * This returns the exceedance probability for a given duration, that is equivalent to the given reference
	 * probability and reference duration. For example, if you have a 1 year hazard curve and want to compute the
	 * appropriate probability for your 1 year curve that is equivalent to the 2% in 50 year hazard level, that would
	 * be computed as: <code>double prob = calcExceedanceProb(0.02, 50, 1)</code>.
	 * <p>
	 * This uses equivalence p1Star/t1 = p2Star/t2 where pStar = -Ln(1-p)
	 * 
	 * @param referenceProb reference probability
	 * @param referenceDuration reference duration
	 * @param calcDuration duration for which we should compute the corresponding probability level
	 * @return
	 */
	public static double calcExceedanceProb(double referenceProb, double referenceDuration, double calcDuration) {
		Preconditions.checkArgument(referenceProb >= 0 && referenceProb <= 1);
		Preconditions.checkArgument(referenceDuration >= 0 && Double.isFinite(referenceDuration));
		Preconditions.checkArgument(calcDuration >= 0 && Double.isFinite(calcDuration));
		double p1star = calcProbStar(referenceProb);
//		System.out.println("p1star="+(float)p1star);
		double p2star = p1star*calcDuration/referenceDuration;
//		System.out.println("p2star="+(float)p2star);
		return calcProbFromPorbStar(p2star);
	}
	
	private static double calcProbStar(double prob) {
		// no input validation, assumed done externally
		return -Math.log(1d - prob);
	}
	
	private static double calcProbFromPorbStar(double probStar) {
		return 1d - Math.exp(-probStar);
	}
	
	/**
	 * This computes the duration in which you would expect an event to occur with the given exceedance probability,
	 * referenced to another reference probability and duration. For example, if you have an event with 2% probability
	 * in 50 years, and want to know the duration with a 50% chance of that event happening, you would calculate it as:
	 * <code>double duration = calcDurationWithExceedanceProb(0.5, 0.02, 50)</code>
	 * 
	 * @param exceedProb
	 * @param referenceProb
	 * @param referenceDuration
	 * @return
	 */
	public static double calcDurationWithExceedanceProb(double exceedProb, double referenceProb, double referenceDuration) {
		double targetProbStar = calcProbStar(exceedProb);
//		targetProbStar = 1;
		double referenceProbStar = calcProbStar(referenceProb);
		
		return referenceDuration * targetProbStar / referenceProbStar;
	}
	
	/**
	 */
	private static double calcDurationWithExceedanceProb(double exceedProb, double referenceReturnPeriod) {
		double targetProbStar = calcProbStar(exceedProb);
		
		return referenceReturnPeriod * targetProbStar;
	}
	
	/**
	 * This computes the return period that is associated with the given probability of exceedence in the
	 * given return period
	 * 
	 * @param exceedProb
	 * @param referenceReturnPeriod
	 * @return duration (return period) associated with the given exceedance probability in the given duration
	 */
	public static double calcReturnPeriod(double exceedProb, double duration) {
		return duration / calcProbStar(exceedProb);
	}
	
	/**
	 * This calculates the exceedance probability for the given duration that corresponds to the given return period
	 * @param returnPeriod
	 * @param calcDuration
	 * @return
	 */
	public static double calcExceedanceProbForReturnPeriod(double returnPeriod, double calcDuration) {
		// rp = duration / pStar(prob)
		// pStar(prob) = duration/rp
		double probStar = calcDuration/returnPeriod;
		return calcProbFromPorbStar(probStar);
	}
	
	public static void main(String[] args) {
//		System.out.println(calcReturnPeriod(0.5, 30));
//		System.out.println(calcExceedanceProb(0.5, 30, 1d));
////		System.out.println(calcExceedanceProb(0.02, 50, 1d));
		
//		System.out.println(calcDurationWithExceedanceProb(0.1, 1));
//		System.out.println(calcReturnPeriod(0.1, 1));
//		System.exit(0);
//		double r1 = 0.02;
//		double t1 = 50;
//		
//		double[] rps = { 1d, 50d, 500d, 1547.0297, 1733, 2474, 2500 };
//		
//		for (double t2 : rps) {
//			double r2 = calcExceedanceProb(r1, t1, t2);
//			double r2Star = calcProbFromPorbStar(r1)*t2/t1;
//			
//			System.out.println("T2="+(float)t2+"\tR2*="+(float)r2Star+"\tR2="+(float)r2);
//		}
		
//		System.out.println("Return periods for probability levels");
//		for (double p : new double[] {0.2, 0.1, 0.05, 0.02, 0.01}) {
//			System.out.println("\t"+(float)(p*100d)+"% in 50:\t"+(float)calcReturnPeriod(p, 50d));
//			System.out.println("\t\tVerificiation: "
//					+(float)calcExceedanceProb(0.5, calcDurationWithExceedanceProb(0.5, calcReturnPeriod(p, 50d)), 50d));
//			System.out.println("\t\tProb for 1yr curves: "+(float)calcExceedanceProb(p, 50d, 1d));
//		}
//		System.out.println();
//		System.out.println("duration with p=1.0 for 2% in 50");
//		System.out.println(calcDurationWithExceedanceProb(1.0, 0.02, 50));
//		System.out.println("duration with p=0.9 for 2% in 50");
//		System.out.println(calcDurationWithExceedanceProb(0.9, 0.02, 50));
//		System.out.println("duration with p=0.5 for 2% in 50");
//		System.out.println(calcDurationWithExceedanceProb(0.5, 0.02, 50));
//		System.out.println("duration with p=0.5 for 2474.9yr RP");
//		System.out.println(calcDurationWithExceedanceProb(0.5, calcReturnPeriod(0.02, 50d)));
//		System.out.println("duration with p=0.5 for 2500yr RP");
//		System.out.println(calcDurationWithExceedanceProb(0.5, 2500));
//		System.out.println();
//		System.out.println("duration with p=0.5 for 10% in 50");
//		System.out.println(calcDurationWithExceedanceProb(0.5, 0.10, 50));
//		System.out.println("duration with p=0.5 for 474.6yr RP");
//		System.out.println(calcDurationWithExceedanceProb(0.5, calcReturnPeriod(0.1, 50d)));
//		System.out.println("duration with p=0.5 for 500yr RP");
//		System.out.println(calcDurationWithExceedanceProb(0.5, 500));
		
		double[] rps = { 2475, 975, 475 };
		double[] durs = { 1d, 50d };
		for (double rp : rps)
			for (double dur : durs)
				System.out.println("Prob for "+(float)rp+", "+(float)dur+" year: "+calcExceedanceProbForReturnPeriod(rp, dur));
		
		for (ReturnPeriods rp : ReturnPeriods.values()) {
			System.out.println(rp.label);
			System.out.println("\tReturn period: "+rp.returnPeriod);
			System.out.println("\tOne year prob: "+rp.oneYearProb);
		}
	}

}
