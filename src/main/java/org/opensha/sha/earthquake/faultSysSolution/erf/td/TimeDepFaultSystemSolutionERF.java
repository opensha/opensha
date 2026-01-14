package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.EnumParameterizedModelarameter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;

import com.google.common.base.Preconditions;

public class TimeDepFaultSystemSolutionERF extends BaseFaultSystemSolutionERF {
	
	public static final String NAME = "Time-Dependent Fault System Solution ERF";
	
	public static final String PROB_MODEL_PARAM_NAME = "Probability Model";
	private static final String PROB_MODEL_PARAM_INFO = "ERF probability model. Individual options may have their own "
			+ "adjustable parameters that display below.";
	private EnumParameterizedModelarameter<FSS_ProbabilityModels, FSS_ProbabilityModel> probModelParam;
	private FSS_ProbabilityModel probModel;
	private boolean probModelChanged = true;
	
	// these are to chache timespan object for switching back and forth between time-independent (ti) and time-dependent (td) models.
	protected TimeSpan tiTimeSpanCache, tdTimeSpanCache;
	
	// TODO: mechanism for subclasses to specify default (e.g., U3 2014, newer models 2026?)
	public final static int START_TIME_DEFAULT = 2014;
	protected final static int START_TIME_MIN = 1800;
	protected final static int START_TIME_MAX = 2100;

	public TimeDepFaultSystemSolutionERF() {
		this(EnumSet.allOf(FSS_ProbabilityModels.class), FSS_ProbabilityModels.POISSON);
	}

	public TimeDepFaultSystemSolutionERF(EnumSet<FSS_ProbabilityModels> probModelChoices, FSS_ProbabilityModels probModelChoice) {
		super(false); // don't initialize, need initialization of class variables first
		
		probModelParam = new EnumParameterizedModelarameter<>(
				PROB_MODEL_PARAM_NAME, probModelChoices, probModelChoice, true,
				model -> buildProbModelInstance(model));
		probModelParam.setInfo(PROB_MODEL_PARAM_INFO);
		// this means that we want to be notified when any lower level parameter changes (e.g., TD internal parameters)
		probModelParam.setPropagateInstanceParamChangeEvents(true);
		probModelParam.addParameterChangeListener(this);
		
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

	public TimeDepFaultSystemSolutionERF(FaultSystemSolution sol, EnumSet<FSS_ProbabilityModels> probModelChoices, FSS_ProbabilityModels probModel) {
		this(probModelChoices, probModel);
		if (sol != null) {
			includeFileParam = false;
			setSolution(sol);
		}
	}
	
	private FSS_ProbabilityModel buildProbModelInstance(FSS_ProbabilityModels model) {
		if (faultSysSolution == null)
			return null;
		// todo: respect sect min mag
		double[] longTermPartRateForSectArray = faultSysSolution.calcTotParticRateForAllSects();
		return model.getProbabilityModel(faultSysSolution, longTermPartRateForSectArray);
	}

	@Override
	protected void initParams() {
		// TODO Auto-generated method stub
		super.initParams();
	}

	@Override
	protected void solutionChangedHook() {
		// need to clear all existing probability models out
		probModelParam.clearInstances();
		probModel = null;
	}

	@Override
	protected void postCreateParamListHook() {
		adjustableParams.addParameter(probModelParam);
	}
	
	/**
	 * @return currently selected probability model choice, or null if one was externally set via
	 * {@link #setCustomProbabilityModel(FSS_ProbabilityModel)}
	 */
	public FSS_ProbabilityModels getProbabilityModelChoice() {
		return probModelParam.getEnumValue();
	}
	
	/**
	 * Sets the probability model choice to one of the supported enum values. Any underlying parameters can be
	 * modifying by subsequently calling {@link #getProbabilityModel()}..
	 * 
	 * @param model
	 * @throws ConstraintException if the selected enum is not a valid choice for thie ERF
	 */
	public void setProbabilityModelChoice(FSS_ProbabilityModels model) {
		Preconditions.checkNotNull(model, "Passed in probability model cannot be null");
		probModelParam.setEnumValue(model);
	}
	
	/**
	 * @return the currently selected probability model instance
	 */
	public FSS_ProbabilityModel getProbabilityModel() {
		return probModelParam.getValue();
	}
	
	/**
	 * Sets the probability model to a custom value; the GUI will read "External Custom Value: name" until the users
	 * changes the model selector or calls {@link #setProbabilityModelChoice(FSS_ProbabilityModels)}. Any parameters
	 * will still be editable.
	 * 
	 * @param model
	 */
	public void setCustomProbabilityModel(FSS_ProbabilityModel model) {
		Preconditions.checkNotNull(model, "Passed in probability model cannot be null");
		probModelParam.setValue(model);
	}
	
	/**
	 * @return true if the currently selected probability model is Poisson
	 */
	public boolean isPoisson() {
		return probModel instanceof FSS_ProbabilityModel.Poisson;
	}
	
	@Override
	protected boolean shouldRebuildFaultSystemSources() {
		return super.shouldRebuildFaultSystemSources() || probModelChanged;
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == probModelParam) {
			probModel = probModelParam.getValue();
			System.out.println("probModelChanged: "+probModel);
			probModelChanged = true;
			
			initTimeSpan();
		} else {
			super.parameterChange(event);
		}
	}

	@Override
	protected void updateHookBeforeFaultSourceBuild() {
		// update prob model calculator if needed
		if (probModel == null) {
			probModel = probModelParam.getValue();
			System.out.println("ProbModel: "+probModel);
		}
		probModelChanged = false;
		
		if (D) {
			int numWithDOLE = probModel.getNumSectsWithDOLE();
			System.out.println(numWithDOLE+" sections had date of last");
		}

		super.updateHookBeforeFaultSourceBuild();
	}

	@Override
	public void updateForecast() {
		super.updateForecast();
		
		if (!isPoisson()) {
			int numWithDOLE = probModel.getNumSectsWithDOLE();
		}
	}
	
	/**
	 * This initiates the timeSpan.
	 */
	@Override
	protected void initTimeSpan() {
		if (isPoisson()) {
			if( tiTimeSpanCache == null) {
				tiTimeSpanCache = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
				tiTimeSpanCache.setDuration(DURATION_DEFAULT);
				tiTimeSpanCache.addParameterChangeListener(this);
			}
			timeSpan = tiTimeSpanCache;
		}
		else {
			if (tdTimeSpanCache == null) {
				tdTimeSpanCache = new TimeSpan(TimeSpan.YEARS, TimeSpan.YEARS);
				tdTimeSpanCache.setDuractionConstraint(DURATION_MIN, DURATION_MAX);
				tdTimeSpanCache.setDuration(DURATION_DEFAULT);
				tdTimeSpanCache.setStartTimeConstraint(TimeSpan.START_YEAR, START_TIME_MIN, START_TIME_MAX);
				tdTimeSpanCache.setStartTime(START_TIME_DEFAULT);	
				tdTimeSpanCache.addParameterChangeListener(this);			
			}
			timeSpan = tdTimeSpanCache;
		}
	}
	
	@Override
	protected double getFaultSysRupRateGain(int fltSystRupIndex) {
		double duration = timeSpan.getDuration();
		long startTime = isPoisson() ? Long.MIN_VALUE : timeSpan.getStartTimeInMillis();
		
		return probModel.getProbabilityGain(fltSystRupIndex, startTime, duration);
	}

	@Override
	protected boolean isFaultSysRupPoisson(int fltSystRupIndex) {
		// TODO: the previous logic was as follows, which would return true for WG02; was that intended?
		// if we want to call anything "Poisson" that's not the FSS_ProbabilityModels.POISSON choice, we need to add
		// another method to FSS_ProbabilityModel to specify if the model should be treated as Poisson.
		// return probModel != ProbabilityModelOptions.U3_BPT && probModel != ProbabilityModelOptions.U3_PREF_BLEND;
		
		return isPoisson();
	}

}
