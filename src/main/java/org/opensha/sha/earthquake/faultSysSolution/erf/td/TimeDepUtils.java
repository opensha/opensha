package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

public class TimeDepUtils {
	
	/**
	 * This computes the poisson probability of one or more events for the given annual rate
	 * @param annualRate
	 * @param durationYears
	 * @return
	 */
	public static double rateToPoissonProb(double annualRate, double durationYears) {
		return 1.0-Math.exp(-annualRate*durationYears);
	}
	
	/**
	 * This computes the non-poisson probability for the given annual rate, just rate*duration but ensuring
	 * that it doesn't exceed 1. This was coppied from the original FaultSystemSolution implementation
	 * 
	 * TODO: Ned, make sure this is correct
	 * @param annualRate
	 * @param durationYears
	 * @return
	 */
	public static double rateToNonPoissonProb(double annualRate, double durationYears) {
		return Math.min(1d, annualRate*durationYears);
	}
	
	/**
	 * This computes the poisson probability of one or more events for the given recurrence interval in years
	 * @param recurIntevalYears
	 * @param durationYears
	 * @return
	 */
	public static double riToPoissonProb(double recurIntevalYears, double durationYears) {
		return 1.0-Math.exp(-durationYears/recurIntevalYears);
	}
	
	/**
	 * This computes the average conditional recurrence interval for each rupture (conditioned on that
	 * rupture being the only one that occurs).  This averages recurrence intervals if aveRI=true, or
	 * averages section rates if aveRI=false, both weighted by section area. This method computes the
	 * section areas from the fltSysRupSet (creep reduced).
	 * 
	 * @param fltSysRupSet
	 * @param sectlongTermPartRates
	 * @param aveRI if true, averaging is done on recurrence intervals, else in rate sapce 
	 * @return
	 */
	public static double[] computeAveCondRecurIntervalForFltSysRups(FaultSystemRupSet fltSysRupSet,
			double[] sectlongTermPartRates, boolean aveRI) {
		double[] sectAreas = fltSysRupSet.getAreaForAllSections();
		return computeAveCondRecurIntervalForFltSysRups(fltSysRupSet, sectlongTermPartRates,  sectAreas, aveRI);
	}
	
	/**
	 * This computes the average conditional recurrence interval for each rupture (conditioned on that
	 * rupture being the only one that occurs).  This averages recurrence intervals if aveRI=true, or
	 * averages section rates if aveRI=false, both weighted by section area.
	 * 
	 * @param fltSysRupSet
	 * @param sectlongTermPartRates
	 * @param sectAreas
	 * @param aveRI if true, averaging is done on recurrence intervals, else in rate sapce 
	 * @return
	 */
	public static double[] computeAveCondRecurIntervalForFltSysRups(FaultSystemRupSet fltSysRupSet,
			double[] sectlongTermPartRates, double[] sectAreas, boolean aveRI) {
		double[] aveCondRecurIntervalForFltSysRups = new double[fltSysRupSet.getNumRuptures()];
		for (int r=0;r<aveCondRecurIntervalForFltSysRups.length; r++) {
			List<Integer> rupSections = fltSysRupSet.getSectionsIndicesForRup(r);
			double ave=0, totArea=0;
			for (int sectID : rupSections) {
				double area = sectAreas[sectID];
				totArea += area;
//				if(1.0/sectlongTermPartRates[sectID] > maxRI)
//					maxRI = 1.0/sectlongTermPartRates[sectID];
				// ave RIs or rates depending on which is set
				if (aveRI)
					ave += area/sectlongTermPartRates[sectID];  // this one averages RIs; wt averaged by area
				else
					ave += sectlongTermPartRates[sectID]*area;  // this one averages rates; wt averaged by area
			}
			if (aveRI)
				aveCondRecurIntervalForFltSysRups[r] = ave/totArea;	// this one averages RIs
			else
				aveCondRecurIntervalForFltSysRups[r] = 1d/(ave/totArea); // this one averages rates
		}
		return aveCondRecurIntervalForFltSysRups;
	}
	

	// Ned, this does not belong here. leaving it here commented out for now, but if you want it, move it somewhere in
	// dev (or if generally useful, give a better name).
//	public static double[] testJamieAveCondRecurIntervalForFltSysRups(FaultSystemSolution fltSysSolution) {
//		FaultSystemRupSet fltSysRupSet = fltSysSolution.getRupSet();
//		int numSections = fltSysRupSet.getNumSections();
//		double[] aveCondRecurIntervalForFltSysRupsAlt = new double[fltSysRupSet.getNumRuptures()];
//		IncrementalMagFreqDist[] sectMFD_Array = new IncrementalMagFreqDist[numSections];
//		for(int s=0;s<numSections;s++)
//			sectMFD_Array[s] = fltSysSolution.calcParticipationMFD_forSect(s, 5.05, 9.95, 50);
//		double[] sectAreas = fltSysRupSet.getAreaForAllSections();
//		for (int r=0;r<aveCondRecurIntervalForFltSysRupsAlt.length; r++) {
//			aveCondRecurIntervalForFltSysRupsAlt[r] = 0;
//			List<Integer> rupSections = fltSysRupSet.getSectionsIndicesForRup(r);
//			double ave=0, totArea=0;
//			for (int sectID : rupSections) {
//				double area = sectAreas[sectID];
//				totArea += area;
//				double magBin = sectMFD_Array[sectID].getClosestXtoY(fltSysRupSet.getMagForRup(r));
//				double cumRateAboveMag = sectMFD_Array[sectID].getCumRate(magBin);
//				ave += area/cumRateAboveMag;  
//			}
//			aveCondRecurIntervalForFltSysRupsAlt[r] = ave/totArea;
//		}
//		return aveCondRecurIntervalForFltSysRupsAlt;
//	}
	
}
