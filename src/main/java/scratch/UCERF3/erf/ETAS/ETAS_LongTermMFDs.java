package scratch.UCERF3.erf.ETAS;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultSysSolutionERF_Calc;
import scratch.UCERF3.erf.FaultSystemSolutionERF;

public class ETAS_LongTermMFDs {

	
	private SummedMagFreqDist[] longTermSupraSeisMFD_OnSectArray;
	private List<? extends IncrementalMagFreqDist> longTermSubSeisMFD_OnSectList;
	private double[] totLongTermSubSeisRateOnSectArray;
	private SummedMagFreqDist longTermTotalERF_MFD;

	public ETAS_LongTermMFDs(FaultSystemSolutionERF erf, boolean wtSupraNuclBySubSeisRates) {
		// here are the sub-seis MFDs
		longTermSubSeisMFD_OnSectList = erf.getSolution().getSubSeismoOnFaultMFD_List();

		totLongTermSubSeisRateOnSectArray = new double[longTermSubSeisMFD_OnSectList.size()];
		for(int s=0;s<totLongTermSubSeisRateOnSectArray.length;s++) {
			if(longTermSubSeisMFD_OnSectList.get(s) != null)
				totLongTermSubSeisRateOnSectArray[s] = longTermSubSeisMFD_OnSectList.get(s).getCumRate(2.55);
			else
				totLongTermSubSeisRateOnSectArray[s] = longTermSubSeisMFD_OnSectList.get(s).getCumRate(2.55);
		}

		// can't get supra-seism MFDs from fault-system-solution becuase some rups are zeroed out and there may 
		// be aleatory mag-area variability added in the ERF, so compute from ERF.

		// temporarily change the erf to Poisson to get long-term section MFDs (and test the parameter values are the same after)
		ProbabilityModelOptions probModel = (ProbabilityModelOptions)erf.getParameter(ProbabilityModelParam.NAME).getValue();
		ArrayList paramValueList = new ArrayList();
		for(Parameter param : erf.getAdjustableParameterList()) {
			paramValueList.add(param.getValue());
		}
		TimeSpan tsp = erf.getTimeSpan();
		double duration = tsp.getDuration();
		String startTimeString = tsp.getStartTimeMonth()+"/"+tsp.getStartTimeDay()+"/"+tsp.getStartTimeYear()+"; hr="+tsp.getStartTimeHour()+"; min="+tsp.getStartTimeMinute()+"; sec="+tsp.getStartTimeSecond();
		paramValueList.add(startTimeString);
		paramValueList.add(duration);
		int numParams = paramValueList.size();

		// now set ERF to poisson:
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
		erf.getTimeSpan().setDuration(1.0); // set this to avoid problems for very long simulations
		erf.updateForecast();
		// get what we need
		if(wtSupraNuclBySubSeisRates) {
			longTermSupraSeisMFD_OnSectArray = calcNucleationMFDForAllSectsWtedBySubSeisRates(erf, 2.55, 8.95, 65);
		}
		else {
			longTermSupraSeisMFD_OnSectArray = FaultSysSolutionERF_Calc.calcNucleationMFDForAllSects(erf, 2.55, 8.95, 65);
		}

		longTermTotalERF_MFD = ERF_Calculator.getTotalMFD_ForERF(erf, 2.55, 8.45, 60, true);		


		// set it back and test param values
		erf.getParameter(ProbabilityModelParam.NAME).setValue(probModel);
		erf.getTimeSpan().setDuration(duration);
		erf.updateForecast();

		int testNum = erf.getAdjustableParameterList().size()+2;
		if(numParams != testNum) {
			throw new RuntimeException("PROBLEM: num parameters changed:\t"+numParams+"\t"+testNum);
		}
		int i=0;
		for(Parameter param : erf.getAdjustableParameterList()) {
			if(param.getValue() != paramValueList.get(i))
				throw new RuntimeException("PROBLEM: "+param.getValue()+"\t"+paramValueList.get(i));
			i+=1;
		}
		TimeSpan tsp2 = erf.getTimeSpan();
		double duration2 = tsp2.getDuration();
		String startTimeString2 = tsp2.getStartTimeMonth()+"/"+tsp2.getStartTimeDay()+"/"+tsp2.getStartTimeYear()+"; hr="+tsp2.getStartTimeHour()+"; min="+tsp2.getStartTimeMinute()+"; sec="+tsp2.getStartTimeSecond();
		if(!startTimeString2.equals(startTimeString))
			throw new RuntimeException("PROBLEM: "+startTimeString2+"\t"+startTimeString2);
		if(duration2 != duration)
			throw new RuntimeException("PROBLEM Duration: "+duration2+"\t"+duration);
	}
	
	/**
	 * This computes fault section nuclation MFD, accounting for any applied time dependence, aleatory mag-area 
	 * uncertainty, and smaller ruptures set to zero in the ERF (which is how this differs from 
	 * InversionFaultSystemSolution.calcNucleationRateForAllSects(*)), and assuming a uniform distribution
	 * of nucleations over the rupture surface.
	 * @param erf
	 * @param min, max, and num (MFD discretization values)
	 * @return
	 */
	private  SummedMagFreqDist[] calcNucleationMFDForAllSectsWtedBySubSeisRates(
			FaultSystemSolutionERF erf, double min, double max,int num) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		
		SummedMagFreqDist[] mfdArray = new SummedMagFreqDist[rupSet.getNumSections()];
		for(int i=0;i<mfdArray.length;i++) {
			mfdArray[i] = new SummedMagFreqDist(min,max,num);
		}
		double duration = erf.getTimeSpan().getDuration();
		
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			SummedMagFreqDist srcMFD = ERF_Calculator.getTotalMFD_ForSource(erf.getSource(s), duration, min, max, num, true);
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			List<Integer> sectIndexList = rupSet.getSectionsIndicesForRup(fssRupIndex);

			int numSubRates=0;
			double aveSubRates=0;	// this will be used where there are no subseis ruptures
			for(int sectIndex:sectIndexList) {
				if(totLongTermSubSeisRateOnSectArray[sectIndex]>0) {
					numSubRates+=1;
					aveSubRates+=totLongTermSubSeisRateOnSectArray[sectIndex];
				}
			}
			if(aveSubRates==0)	// all were outside relm region; give all the same weight
				aveSubRates=1;
			else
				aveSubRates /= numSubRates;

			double sectWt;
			double totWt=0;
			for(int sectIndex : sectIndexList) {
				if(totLongTermSubSeisRateOnSectArray[sectIndex] != 0)
					sectWt = totLongTermSubSeisRateOnSectArray[sectIndex];
				else
					sectWt = aveSubRates;
				totWt += sectWt;
			}
			for(int sectIndex : sectIndexList) {
				if(totLongTermSubSeisRateOnSectArray[sectIndex] != 0)
					sectWt = totLongTermSubSeisRateOnSectArray[sectIndex];
				else
					sectWt = aveSubRates;
				for(int i=0;i<num;i++) {
					mfdArray[sectIndex].add(i,srcMFD.getY(i)*sectWt/totWt);
				}
			}
		}
		return mfdArray;
	}

	public SummedMagFreqDist[] getLongTermSupraSeisMFD_OnSectArray() {
		return longTermSupraSeisMFD_OnSectArray;
	}

	public List<? extends IncrementalMagFreqDist> getLongTermSubSeisMFD_OnSectList() {
		return longTermSubSeisMFD_OnSectList;
	}

	public double[] getTotLongTermSubSeisRateOnSectArray() {
		return totLongTermSubSeisRateOnSectArray;
	}

	public SummedMagFreqDist getLongTermTotalERF_MFD() {
		return longTermTotalERF_MFD;
	}

}
