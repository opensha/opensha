package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import org.opensha.commons.param.impl.ParameterizedEnumParameter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.faultSurface.FaultSection;

public class TimeDepFaultSystemSolutionERF extends BaseFaultSystemSolutionERF {
	
	public static final String PROB_MODEL_PARAM_NAME = "Probability Model";
	public static final FSS_ProbabilityModels PROB_MODEL_DEFAULT = FSS_ProbabilityModels.POISSON; 
	private ParameterizedEnumParameter<FSS_ProbabilityModels, FSS_ProbabilityModel> probModelParam;

	public TimeDepFaultSystemSolutionERF() {
		super(false); // don't initialize, need initialization of class variables first
		// at this point class variables have been initialized, now call init methods
		initParams();
		initTimeSpan();
	}

	public TimeDepFaultSystemSolutionERF(FaultSystemSolution sol) {
		this();
		if (sol != null) {
			includeFileParam = false;
			setSolution(sol);
		}
	}

	@Override
	protected void postCreateParamListHook() {
		// TODO Auto-generated method stub
		super.postCreateParamListHook();
	}

	@Override
	protected boolean isFaultSysRupPoisson(int fltSystRupIndex) {
		return true; // TODO fix
//		return probModel != ProbabilityModelOptions.U3_BPT && probModel != ProbabilityModelOptions.U3_PREF_BLEND;
	}

}
