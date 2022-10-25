package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.A_Faults;

import org.opensha.sha.earthquake.calc.recurInterval.BPT_DistCalc;


/**
 * <p>Title: WG02_QkProbCalc </p>
 * <p>Description: 
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Ned Field
 * @date July, 2007
 * @version 1.0
 */


public class TimePredictableQkProbCalc {
	
	//for Debug purposes
	private static String C = new String("WG02_QkProbCalc");
	private final static boolean D = true;
	
	/*
	 * 
	 */
	public static double[] getRupProbs(double[] rupRate, double[] segSlipLast, double[] segSlipRate, double[] segArea, 
			double[] segTimeLast, int[][] rupInSeg, double alpha, double startYear, double duration) {
		
		int num_seg = segSlipRate.length;
		int num_rup = rupRate.length;

		double[] rupProb = new double[num_rup];
		for(int rup=0; rup<num_rup; rup++) {
			double deltaT = 0;
			double totalArea = 0;
			double aveTimeOfLast =0;
			for(int seg=0; seg<num_seg; seg++) {
				if(rupInSeg[seg][rup] == 1) {
					totalArea += segArea[seg];
					deltaT += segArea[seg]*segSlipLast[seg]/segSlipRate[seg];
					aveTimeOfLast += segArea[seg]*segTimeLast[seg];
				}
			}
			deltaT /= totalArea;
			aveTimeOfLast /= totalArea;
			double timeSinceLast = startYear - aveTimeOfLast;
			double prob_bpt = BPT_DistCalc.getCondProb(deltaT, alpha, timeSinceLast, duration);
			double prob_pois = 1-Math.exp(-duration/deltaT);
			double rup_prob_pois = 1-Math.exp(-duration*rupRate[rup]);
			rupProb[rup] = rup_prob_pois*prob_bpt/prob_pois;
			if (D) System.out.println("rup"+rup+":  deltaT="+(float)deltaT+"  timeSinceLast="+(float)timeSinceLast+"  rupRate="+(float)rupRate[rup]+
					"  prob_bpt="+(float)prob_bpt+"  prob_pois="+(float)prob_pois+"  rup_prob_pois="+(float)rup_prob_pois);
		}	
		
		if(D) {
			System.out.println("alpha="+alpha+"\tstartYear="+startYear+"\tduration="+duration);
			System.out.println("segSlipLast[i]\tsegSlipRate[i]\tsegArea[i]\tsegTimeLast[i]");
			for(int i=0;i<num_seg;i++)
				System.out.println((float)segSlipLast[i]+"\t"+(float)segSlipRate[i]+"\t"+(float)segArea[i]+"\t"+(float)segTimeLast[i]);
		}
		return rupProb;
	}
}
