package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.faultSurface.FaultSection;

public class TimeDepFaultSystemSolutionERF extends BaseFaultSystemSolutionERF {

	private TimeDepFaultSystemSolutionERF() {
		super(false); // don't initialize, need initialization of class variables first
		// at this point class variables have been initialized, now call init methods
		initParams();
		initTimeSpan();
	}

	private TimeDepFaultSystemSolutionERF(FaultSystemSolution sol) {
		this();
		if (sol != null) {
			includeFileParam = false;
			setSolution(sol);
		}
	}

	@Override
	protected boolean isFaultSysRupPoisson(int fltSystRupIndex) {
		return true; // TODO fix
//		return probModel != ProbabilityModelOptions.U3_BPT && probModel != ProbabilityModelOptions.U3_PREF_BLEND;
	}
	
	/**
	 * This is to prevent simulators from evolving into a time where historical date of
	 * last event data exists on some faults
	 */
	public void eraseDatesOfLastEventAfterStartTime() {
		if(faultSysSolution == null) {
			readFaultSysSolutionFromFile();
		}
		long startTime = getTimeSpan().getStartTimeInMillis();
		for(FaultSection fltData : faultSysSolution.getRupSet().getFaultSectionDataList()) {
			if(fltData.getDateOfLastEvent() > startTime) {
				if(D) {
					double dateOfLast = 1970+fltData.getDateOfLastEvent()/TimeDepUtils.MILLISEC_PER_YEAR;
					double startTimeYear = 1970+startTime/TimeDepUtils.MILLISEC_PER_YEAR;
					System.out.println("Expunged Date of Last: "+dateOfLast+" (>"+startTimeYear+") for "+fltData.getName());
				}
				fltData.setDateOfLastEvent(Long.MIN_VALUE);
			}
		}
		// TODO: clear out prob model instances? or signal that they've changed (maybe via a setSolution method?))
//		probModelsCalc = null;
	}

}
