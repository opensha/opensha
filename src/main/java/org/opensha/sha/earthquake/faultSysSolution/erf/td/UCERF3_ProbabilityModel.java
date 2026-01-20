package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import static org.opensha.sha.earthquake.faultSysSolution.erf.td.TimeDepUtils.*;

import java.util.EnumSet;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.EnumParameterizedModelarameter;
import org.opensha.sha.earthquake.calc.recurInterval.EqkProbDistCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;

import com.google.common.base.Preconditions;

public class UCERF3_ProbabilityModel extends AbstractProbDistProbabilityModel implements ParameterChangeListener {
	
	/*
	 * Parameters; currently keeping things simpler by not storing/updating primitive versions, can revisit
	 * if that becomes a bottleneck.
	 */
	
	private EnumParameterizedModelarameter<AperiodicityModels, AperiodicityModel> aperiodicityParam;
	private EnumParameter<RenewalModels> renewalModelParam;
	private EnumParameterizedModelarameter<HistoricalOpenIntervals, HistoricalOpenInterval> histOpenIntervalParam;
	// TODO: refactor to remove BPT from the name of this and the corresponding enum
	private BPTAveragingTypeParam averagingTypeParam;
	
	private ParameterList params;
	
	/*
	 * Input data that is fixed
	 */
	private final double[] sectAreas;
	
	/*
	 * Cached data that needs to be maintained and cleared if settings change
	 */
	private double[] cachedAveRupCondRecurIntervals;
	
	public UCERF3_ProbabilityModel(FaultSystemSolution sol,
			AperiodicityModels aperiodicityModel, RenewalModels renewalModel,
			HistoricalOpenIntervals histOpenInterval, BPTAveragingTypeOptions averagingType) {
		this(sol, sol.calcTotParticRateForAllSects(), aperiodicityModel, renewalModel, histOpenInterval, averagingType);
	}
	
	public UCERF3_ProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray,
			AperiodicityModels aperiodicityModel, RenewalModels renewalModel,
			HistoricalOpenIntervals histOpenInterval, BPTAveragingTypeOptions averagingType) {
		this(sol, longTermPartRateForSectArray,
				aperiodicityModel, EnumSet.allOf(AperiodicityModels.class),
				renewalModel, EnumSet.allOf(RenewalModels.class),
				histOpenInterval, EnumSet.allOf(HistoricalOpenIntervals.class),
				averagingType, EnumSet.allOf(BPTAveragingTypeOptions.class));
	}
	
	public UCERF3_ProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray,
			AperiodicityModels aperiodicityModel, EnumSet<AperiodicityModels> supportedAperiodicityModels,
			RenewalModels renewalModel, EnumSet<RenewalModels> supportedRenewalModels,
			HistoricalOpenIntervals histOpenInterval, EnumSet<HistoricalOpenIntervals> supportedHistOpenIntervals,
			BPTAveragingTypeOptions averagingType, EnumSet<BPTAveragingTypeOptions> supportedAveragingTypes) {
		super(sol, longTermPartRateForSectArray);
		params = new ParameterList();
		
		aperiodicityParam = AperiodicityModels.buildParameter(sol, supportedAperiodicityModels, aperiodicityModel);
		aperiodicityParam.addParameterChangeListener(this);
		if (supportedAperiodicityModels.size() > 1 || aperiodicityParam.getValue().getAdjustableParameters() != null)
			// display it if we have multiple options, or a single option with its own parameters
			params.addParameter(aperiodicityParam);
		
		renewalModelParam = RenewalModels.buildParameter(supportedRenewalModels, renewalModel);
		renewalModelParam.addParameterChangeListener(this);
		if (supportedRenewalModels.size() > 1)
			// display it if we have multiple options
			params.addParameter(renewalModelParam);
		
		histOpenIntervalParam = HistoricalOpenIntervals.buildParameter(sol, supportedHistOpenIntervals, histOpenInterval);
		histOpenIntervalParam.addParameterChangeListener(this);
		if (supportedHistOpenIntervals.size() > 1 || histOpenIntervalParam.getValue().getAdjustableParameters() != null)
			// display it if we have multiple options, or a single option with its own parameters
			params.addParameter(histOpenIntervalParam);
		
		averagingTypeParam = new BPTAveragingTypeParam(averagingType, supportedAveragingTypes);
		averagingTypeParam.addParameterChangeListener(this);
		if (averagingTypeParam.getConstraint().size() > 1)
			params.addParameter(averagingTypeParam);
		
		sectAreas = fltSysRupSet.getAreaForAllSections();
	}

	@Override
	public double getProbability(int ruptureIndex, double ruptureRate, long forecastStartTimeMillis, double durationYears) {
		// for now just use gain, but could change that if it's more efficient or better in some way to calculate it separately
		double gain = getProbabilityGain(ruptureIndex, forecastStartTimeMillis, durationYears);
		return rateToNonPoissonProb(ruptureRate*gain, durationYears);
	}

	@Override
	public double getProbabilityGain(int fltSysRupIndex, long presentTimeMillis,
			double durationYears) {
		final AperiodicityModel aperiodicityModel = aperiodicityParam.getValue();
		final double aperiodicity = aperiodicityModel.getRuptureAperiodicity(fltSysRupIndex);
		final RenewalModels renewalModel = getRenewalModelChoice();
		final EqkProbDistCalc normProbDistCalc = getNormProbDistCalc(renewalModel, aperiodicity);
		
		final BPTAveragingTypeOptions averagingType = averagingTypeParam.getValue();
		final boolean aveRecurIntervals = averagingType.isAveRI();
		final boolean aveNormTimeSinceLast = averagingType.isAveNTS();
		
		// historical open interval in years, or 0 if no historic open interval
		final double histOpenInterval = histOpenIntervalParam.getValue().getRuptureOpenInterval(fltSysRupIndex, presentTimeMillis);
		
		final List<Integer> rupSubSects = fltSysRupSet.getSectionsIndicesForRup(fltSysRupIndex);

		// get the average conditional recurrence interval
		// this gets the cached value and is updated automatically if averagingTypeParam changes
		double aveCondRecurInterval = getAveCondRecurIntervalForFltSysRups()[fltSysRupIndex];
		
		// get aveTimeSinceLastWhereKnownYears
		double aveTimeSinceLastWhereKnownYears;
		double aveNormTimeSinceLastEventWhereKnown=Double.NaN;
		
		// these were separate methods in the old implementations that set a bunch of global variables
		// simpler and safer to embed here, now combined as a single method
		double totRupArea = 0;
		double totRupAreaWithDateOfLast = 0;
		int numWithDateOfLast = 0;
		boolean allSectionsHadDateOfLast = true;
		boolean noSectionsHadDateOfLast = true;
		double tmpSumDOLE = 0d;
		for (int s : rupSubSects) {
			long dateOfLast = sectDOLE[s];
			double area = sectAreas[s];
			totRupArea += area;
			if (dateOfLast != Long.MIN_VALUE && dateOfLast <= presentTimeMillis) {
				noSectionsHadDateOfLast = false;
				if (aveNormTimeSinceLast) {
					tmpSumDOLE += area*((double)(presentTimeMillis-dateOfLast)*MILLISEC_TO_YEARS)*sectlongTermPartRates[s];
				} else {
					tmpSumDOLE += (double)dateOfLast*area;
				}
				totRupAreaWithDateOfLast += area;
				numWithDateOfLast+=1;
			}
			else {
				allSectionsHadDateOfLast = false;
			}
		}
		if (numWithDateOfLast > 0) {
			if (aveNormTimeSinceLast) {
				aveNormTimeSinceLastEventWhereKnown = tmpSumDOLE/totRupAreaWithDateOfLast; 
				aveTimeSinceLastWhereKnownYears = aveNormTimeSinceLastEventWhereKnown*aveCondRecurInterval;
			} else {
				long aveDOLE = Math.round(tmpSumDOLE/totRupAreaWithDateOfLast);  // epoch millis
				aveTimeSinceLastWhereKnownYears = (double)(presentTimeMillis-aveDOLE) * MILLISEC_TO_YEARS;
			}
		} else {
			aveTimeSinceLastWhereKnownYears = Double.NaN;
		}
		
		Preconditions.checkState(Double.isNaN(aveTimeSinceLastWhereKnownYears)
				|| aveTimeSinceLastWhereKnownYears >= 0, "aveTimeSinceLastWhereKnownYears=%s", aveTimeSinceLastWhereKnownYears);
		
		double expNum = durationYears/aveCondRecurInterval;
		float fractAreaWithDateOfLast = (float)(totRupAreaWithDateOfLast/totRupArea);
		
		double probGain;
		double condProb;
		double condRecurIntWhereUnknown = Double.NaN;
		
		if (allSectionsHadDateOfLast) {
			condProb = computeCondProbFast(normProbDistCalc, aveCondRecurInterval, aveTimeSinceLastWhereKnownYears, durationYears, aperiodicity);
			probGain = condProb/expNum;	
			Preconditions.checkState(probGain >= 0d, // || (probGain == 0d && (float)aveTimeSinceLastWhereKnownYears == 0f),
					"Bad probGain=%s where all sections have date of last.\n"
					+ "\taveCondRecurInterval=%s\taveTimeSinceLastWhereKnownYears=%s\n"
					+ "\thistOpenInterval=%s\tdurationYears=%s\texpNum=%s\n"+
					"\tcondProb=%s\taper=%s",
					probGain, aveCondRecurInterval, aveTimeSinceLastWhereKnownYears, histOpenInterval, durationYears, expNum, condProb, aperiodicity);
		} else if (noSectionsHadDateOfLast) {
			condProb = computeCondProbForUnknownDateOfLastFast(normProbDistCalc, aveCondRecurInterval, histOpenInterval, durationYears, aperiodicity);
			probGain = condProb/expNum;	
			Preconditions.checkState(probGain > 0d, "Bad probGain=%s where no sections have date of last.\n"
					+ "\taveCondRecurInterval=%s\thistOpenInterval=%s\n"
					+ "\tdurationYears=%s\texpNum=%s",
					probGain, aveCondRecurInterval, histOpenInterval, durationYears, expNum);
		} else {
			// case where some have date of last; loop over all possibilities for those that don't
			// this normalized CDF is much coarser than the CDF used in the regular calculations
			EvenlyDiscretizedFunc normCDF = getIntegrationNormCDF(renewalModel, aperiodicity);
			double sumCondProbGain=0;
			double totWeight=0;
			double areaWithOutDateOfLast = totRupArea-totRupAreaWithDateOfLast;
			
			condRecurIntWhereUnknown = computeAveCondRecurIntForFltSysRupsWhereDateLastUnknown(fltSysRupIndex, aveRecurIntervals, presentTimeMillis);
			
			if(aveNormTimeSinceLast) {
				for (int i=0;i<normCDF.size();i++) {
					double normTimeSinceYears = normCDF.getX(i);
					// this is the probability of the date of last event (not considering hist open interval)
					double relProbForTimeSinceLast = 1.0-normCDF.getY(i);
					if(normTimeSinceYears*condRecurIntWhereUnknown>=histOpenInterval && relProbForTimeSinceLast>1e-15) {
						double aveNormTS = (normTimeSinceYears*areaWithOutDateOfLast + aveNormTimeSinceLastEventWhereKnown*totRupAreaWithDateOfLast)/totRupArea;
						double condProbTemp = computeCondProbFast(normProbDistCalc, 1.0, aveNormTS, durationYears/aveCondRecurInterval, aperiodicity);
						sumCondProbGain += (condProbTemp/expNum)*relProbForTimeSinceLast;
						totWeight += relProbForTimeSinceLast;
					}
				}
			} else {	// average date of last event
				for (int i=0;i<normCDF.size();i++) {
					double timeSinceYears = normCDF.getX(i)*condRecurIntWhereUnknown;
					// this is the probability of the date of last event (not considering hist open interval)
					double relProbForTimeSinceLast = 1.0-normCDF.getY(i);

					if (timeSinceYears>=histOpenInterval && relProbForTimeSinceLast>1e-15) {
						// average the time since last between known and unknown sections
						double aveTimeSinceLast = (timeSinceYears*areaWithOutDateOfLast + aveTimeSinceLastWhereKnownYears*totRupAreaWithDateOfLast)/totRupArea;
						double condProbTemp = computeCondProbFast(normProbDistCalc, aveCondRecurInterval, aveTimeSinceLast, durationYears, aperiodicity);
						sumCondProbGain += (condProbTemp/expNum)*relProbForTimeSinceLast;
						totWeight += relProbForTimeSinceLast;
					}
				}	
			}
			
			if(totWeight>0) {
				probGain = sumCondProbGain/totWeight;
				condProb = probGain*expNum;
				Preconditions.checkState(probGain >= 0d, "Bad probGain=%s where some (but not all) sections have date of last.\n"
						+ "\tsumCondProbGain=%s\ttotWeight=%s\n"
						+ "\tdurationYears=%s\texpNum=%s\tfractAreaWith=%s",
						probGain, sumCondProbGain, totWeight, durationYears, expNum, (float)fractAreaWithDateOfLast);
			} else {
				// deal with case where there was no viable time since last (model implies it definitely should have
				// occurred in hist open interval); use exactly historic open interval
				if (totWeight<=0) throw new RuntimeException("Finally got here - note how this happened");
				if (aveNormTimeSinceLast) {
					double normTimeSinceYearsUnknown = histOpenInterval/condRecurIntWhereUnknown;
					double aveNormTS = (normTimeSinceYearsUnknown*areaWithOutDateOfLast + aveNormTimeSinceLastEventWhereKnown*totRupAreaWithDateOfLast)/totRupArea;
					condProb = computeCondProbFast(normProbDistCalc, 1.0, aveNormTS, durationYears/aveCondRecurInterval, aperiodicity);
					probGain = condProb/expNum;
					Preconditions.checkState(probGain > 0d, "Bad probGain=%s where some (but not all) sections have date of last.\n"
							+ "\tcondProb=%s\texpNum=%s\tnormTimeSinceYearsUnknown=%s\taveNormTS=%s\n"
						+ "\tdurationYears=%s\texpNum=%s",
							probGain, condProb, expNum, normTimeSinceYearsUnknown, aveNormTS, durationYears, expNum);
				} else {
					double aveTimeSinceLast = (histOpenInterval*areaWithOutDateOfLast + aveTimeSinceLastWhereKnownYears*totRupAreaWithDateOfLast)/totRupArea;
					condProb = computeCondProbFast(normProbDistCalc, aveCondRecurInterval, aveTimeSinceLast, durationYears, aperiodicity);
					probGain = condProb/expNum;
					Preconditions.checkState(probGain > 0d, "Bad probGain=%s where some (but not all) sections have date of last.\n"
							+ "\tcondProb=%s\texpNum=%s\taveTimeSinceLast=%s\n"
						+ "\tdurationYears=%s\texpNum=%s",
							probGain, condProb, expNum, aveTimeSinceLast, durationYears, expNum);
				}
			}
		}
		
		if(Double.isNaN(probGain))
			throw new RuntimeException("probGain=NaN for fltSysRupIndex="+fltSysRupIndex);
		
		return probGain;
	}
	
	/**
	 * This computes average conditional recurrent intervals for each fault system rup
	 * (the recurrence interval assuming the rup is the next to occur), either by averaging
	 * section recurrence intervals (typeCalc=1) or by computing one over the average section
	 * rate (typeCalc=2), both are weighted by section area.
	 * 
	 * @param typeCalc - set as 1 to average RIs, 2 to average rates, 3 for max sect RI
	 * @return
	 */
	private double[] getAveCondRecurIntervalForFltSysRups() {
		double[] aveCondRecurIntervalForFltSysRups = cachedAveRupCondRecurIntervals;
		if (aveCondRecurIntervalForFltSysRups != null)
			return aveCondRecurIntervalForFltSysRups;
		synchronized (this) {
			if (cachedAveRupCondRecurIntervals == null)
				cachedAveRupCondRecurIntervals = TimeDepUtils.computeAveCondRecurIntervalForFltSysRups(
						fltSysRupSet, aveCondRecurIntervalForFltSysRups, aveCondRecurIntervalForFltSysRups,
						averagingTypeParam.getValue().isAveRI());
		}
		return cachedAveRupCondRecurIntervals;
	}

	/**
	 * This is made fast by using a reference calculator (with a reference RI=1 year & aperiodicity), rather than
	 * redoing the calculation each time.
	 * 
	 * @param normProbDistCalc
	 * @param aveRecurIntervalYears
	 * @param aveTimeSinceLastYears
	 * @param durationYears
	 * @param aperiodicity
	 * @return
	 */
	static double computeCondProbFast(EqkProbDistCalc normProbDistCalc, double aveRecurIntervalYears,
			double aveTimeSinceLastYears, double durationYears, double aperiodicity) {
		double newTimeSinceLast = aveTimeSinceLastYears/aveRecurIntervalYears;
		if(newTimeSinceLast<0 && newTimeSinceLast > -1e-10)
			newTimeSinceLast=0;
		double prob = normProbDistCalc.getCondProb(newTimeSinceLast, durationYears/aveRecurIntervalYears);
//		if(prob<0d)
//			System.out.println("Negative Prob: "+prob+"\t"+aveRecurIntervalYears+"\t"+aveTimeSinceLastYears+"\t"+durationYears);
		return prob;
	}
	
	/**
	 * This computes average conditional recurrent interval using only sections that
	 * lack a date of last event, either by averaging section recurrence intervals 
	 * (aveRI_CalcType=true) or by computing one over the average section
	 * rate (aveRI_CalcType=false), both are weighted by section area.  Double.NaN is
	 * returned if all sections have a date of last event.
	 * 
	 * @param fltSysRupIndex
	 * @param aveRI_CalcType
	 * @param presentTimeMillis
	 * @return
	 */
	private double computeAveCondRecurIntForFltSysRupsWhereDateLastUnknown(int fltSysRupIndex, boolean aveRI_CalcType, long presentTimeMillis) {
		List<Integer> sectID_List = fltSysRupSet.getSectionsIndicesForRup(fltSysRupIndex);
		double ave=0, totArea=0;
		for(Integer sectID:sectID_List) {
			long dateOfLastMillis = sectDOLE[sectID];
			if(dateOfLastMillis == Long.MIN_VALUE || dateOfLastMillis > presentTimeMillis) {
				double area = sectAreas[sectID];
				totArea += area;
				// ave RIs or rates depending on which is set
				if(aveRI_CalcType)
					ave += area/sectlongTermPartRates[sectID];  // this one averages RIs; wt averaged by area
				else
					ave += sectlongTermPartRates[sectID]*area;  // this one averages rates; wt averaged by area					
			}
		}
		if(totArea == 0.0)
			return Double.NaN;
		else if(aveRI_CalcType)
			return ave/totArea;	// this one averages RIs
		else
			return 1.0/(ave/totArea); // this one averages rates
	}
	
	/**
	 * This is made fast by using a reference calculator (with a reference RI), rather than
	 * redoing the calculation each time.
	 * 
	 * @param normProbDistCalc
	 * @param aveRecurIntervalYears
	 * @param histOpenIntervalYears
	 * @param durationYears
	 * @param aperiodicity
	 * @return
	 */
	static double computeCondProbForUnknownDateOfLastFast(EqkProbDistCalc normProbDistCalc, double aveRecurIntervalYears,
			double histOpenIntervalYears, double durationYears, double aperiodicity) {
		return normProbDistCalc.getCondProbForUnknownTimeSinceLastEvent(
				durationYears/aveRecurIntervalYears, histOpenIntervalYears/aveRecurIntervalYears);	 
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == averagingTypeParam) {
			cachedAveRupCondRecurIntervals = null;
		}
	}

	@Override
	public ParameterList getAdjustableParameters() {
		if (params.isEmpty())
			return null;
		return params;
	}

	@Override
	public String getName() {
		return FSS_ProbabilityModels.UCERF3_METHOD.toString();
	}
	
	/*
	 * Aperiodicity convenience getters/setters
	 */
	
	/**
	 * Sets the AperiodicityModel choice via the models enum. Any model parameters can then be set on the underlying
	 * model by calling {@link #getAperiodicityModel()}.
	 * @param modelChoice
	 * @throws ConstraintException if the selected choice is not allowed
	 */
	public void setAperiodicityModelChoice(AperiodicityModels modelChoice) {
		aperiodicityParam.setEnumValue(modelChoice);
	}
	
	/**
	 * @return aperiodicity model choice, or null if it was set to an external custom value via
	 * {@link #setCustomAperiodicityModel(AperiodicityModel)}.
	 */
	public AperiodicityModels getAperiodicityModelChoice() {
		return aperiodicityParam.getEnumValue();
	}
	
	/**
	 * @return current aperiodicity model.
	 */
	public AperiodicityModel getAperiodicityModel() {
		return aperiodicityParam.getValue();
	}
	
	/**
	 * Sets a custom aperiodicity model to something not controlled by the built-in model enum.
	 * 
	 * @param model
	 */
	public void setCustomAperiodicityModel(AperiodicityModel model) {
		Preconditions.checkNotNull(model, "Passed in aperiodicity model cannot be null");
		aperiodicityParam.setValue(model);
	}
	
	/*
	 * Historical open interval convenience getters/setters
	 */
	
	/**
	 * Sets the historical open interval choice via the models enum. Any model parameters can then be set on the underlying
	 * model by calling {@link #getHistOpenInterval()}.
	 * @param modelChoice
	 * @throws ConstraintException if the selected choice is not allowed
	 */
	public void setHistOpenIntervalChoice(HistoricalOpenIntervals modelChoice) {
		histOpenIntervalParam.setEnumValue(modelChoice);
	}
	
	/**
	 * Sets a custom historical open interval model to something not controlled by the built-in model enum.
	 * 
	 * @param model
	 */
	public void setCustomHistOpenIntervalModel(HistoricalOpenInterval model) {
		Preconditions.checkNotNull(model, "Passed in historical open interval cannot be null");
		histOpenIntervalParam.setValue(model);
	}
	
	/**
	 * @return historical open interval choice, or null if it was set to an external custom value via
	 * {@link #setCustomHistOpenIntervalModel(HistoricalOpenInterval)}.
	 */
	public HistoricalOpenIntervals getHistOpenIntervalChoice() {
		return histOpenIntervalParam.getEnumValue();
	}
	
	/**
	 * @return current historical open interval model.
	 */
	public HistoricalOpenInterval getHistOpenInterval() {
		return histOpenIntervalParam.getValue();
	}
	
	/*
	 * Renewal model convenience getters/setters
	 */
	
	/**
	 * @return selected renewal model
	 */
	public RenewalModels getRenewalModelChoice() {
		return renewalModelParam.getValue();
	}
	
	/**
	 * Sets the renewal model choice to the passed in value
	 * @param modelChoice
	 */
	public void setRenewalModelChoice(RenewalModels modelChoice) {
		this.renewalModelParam.setValue(modelChoice);
	}
	
	/*
	 * Averaging type convenience getters/setters
	 */
	
	/**
	 * @return selected averaging type
	 */
	public BPTAveragingTypeOptions getAveragingTypeChoice() {
		return averagingTypeParam.getValue();
	}
	
	/**
	 * Sets the averaging type choice to the passed in value
	 * @param modelChoice
	 */
	public void setAveragingTypeChoice(BPTAveragingTypeOptions modelChoice) {
		this.averagingTypeParam.setValue(modelChoice);
	}

}
