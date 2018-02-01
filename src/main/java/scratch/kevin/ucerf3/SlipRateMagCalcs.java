package scratch.kevin.ucerf3;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.Shaw_2009_ModifiedMagAreaRel;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
public class SlipRateMagCalcs {
	
	public static void main(String[] args) {
		ScalingRelationships scale = ScalingRelationships.SHAW_2009_MOD;
		MagAreaRelationship ma = new Shaw_2009_ModifiedMagAreaRel();
		
		double refMag = 7.0;
		double[] targetMags = { 7, 7.5, 7.7, 8 };
		double width = 12000;
		
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(1d, 1d, 5d, 9d, 41);
		
		double refArea = ma.getMedianArea(refMag)*1000000;
		double refSlip = scale.getAveSlip(refArea, Double.NaN, width);
		
		double refTotalSlip = 10d;
		double refQuakes = refTotalSlip / refSlip;
		System.out.println("Average slip for ref M"+(float)refMag+": "+refSlip);
		System.out.println("Ref M"+(float)refMag+" needs "+(float)refQuakes+" for "+(float)refTotalSlip+"m of slip");
		
		double refGRslipRate = refSlip * gr.getInterpolatedY(refMag);
		System.out.println("Ref GR slip rate: "+(float)refGRslipRate);
		
		for (double targetMag : targetMags) {
			double rateGain = gr.getInterpolatedY(refMag) / gr.getInterpolatedY(targetMag);
			
			System.out.println("M"+(float)refMag+" happens "+(float)rateGain+"x more often than "+targetMag);
			
			double targetArea = ma.getMedianArea(targetMag)*1000000;
			double targetSlip = scale.getAveSlip(targetArea, Double.NaN, width);
			
			double slipGain = targetSlip / refSlip;
			
			System.out.println("\tM"+targetMag+" uses "+slipGain+"x more slip");
			double targetQuakes = refTotalSlip / targetSlip;
			System.out.println("\tneeds "+(float)targetQuakes+" for "+(float)refTotalSlip+"m of slip");
			
			double numRefsToMatchArea = targetArea / refArea;
			
			double targetGRslipRate = targetSlip * gr.getInterpolatedY(targetMag);
			System.out.println("\tTarget GR slip rate: "+(float)targetGRslipRate);
			double equivRefGRslipRate = refGRslipRate/numRefsToMatchArea;
			double slipRateGain = targetGRslipRate / equivRefGRslipRate;
			System.out.println("\tEquiv slip-rate gain: "+(float)slipRateGain);
			double targetMoment = FaultMomentCalc.getMoment(targetArea, targetSlip);
			double refMoment = FaultMomentCalc.getMoment(refArea, refSlip);
			double refsForMoment = targetMoment / refMoment;
			System.out.println("\tNum M"+(float)refMag+"'s needed for moment: "+(float)refsForMoment);
		}
	}

}
