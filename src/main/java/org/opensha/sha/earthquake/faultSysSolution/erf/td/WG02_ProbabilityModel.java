package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.EnumParameterizedModelarameter;
import org.opensha.sha.earthquake.calc.recurInterval.EqkProbDistCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

public class WG02_ProbabilityModel extends AbstractProbDistProbabilityModel implements ParameterChangeListener {
	
	// could make this adjustable if we want to support multiple, but I'm guessing we want to restrict it to the
	// model used in WG02 (which I assume was just BPT)?
	private static RenewalModels RENEWAL_MODEL = RenewalModels.BPT;
	
	// could make this a parameter if we want to support it
	private static final boolean ONLY_IF_ALL_SECTS_HAVE_DOLE = false;
	
	private EnumParameterizedModelarameter<AperiodicityModels, AperiodicityModel> aperiodicityParam;
	private AperiodicityModel aperiodicityModel;
	
	private ParameterList params;
	
	private SectGainCache sectGainCache;
	
	private final double[] sectAreas;

	public WG02_ProbabilityModel(FaultSystemSolution fltSysSol, AperiodicityModels aperiodicityModel) {
		this(fltSysSol, fltSysSol.calcTotParticRateForAllSects(), aperiodicityModel);
	}

	public WG02_ProbabilityModel(FaultSystemSolution fltSysSol, double[] longTermPartRateForSectArray,
			AperiodicityModels aperiodicityModel) {
		this(fltSysSol, longTermPartRateForSectArray, aperiodicityModel, AperiodicityModels.SINGLE_VALUED_MODELS);
	}

	public WG02_ProbabilityModel(FaultSystemSolution fltSysSol, double[] longTermPartRateForSectArray,
			AperiodicityModels aperiodicityModel, EnumSet<AperiodicityModels> supportedAperiodicityModels) {
		super(fltSysSol, longTermPartRateForSectArray);
		
		aperiodicityParam = AperiodicityModels.buildParameter(fltSysSol, supportedAperiodicityModels, aperiodicityModel);
		// make sure we're notified of any underlying parameter changes
		aperiodicityParam.setPropagateInstanceParamChangeEvents(true);
		aperiodicityParam.addParameterChangeListener(this);
		this.aperiodicityModel = aperiodicityParam.getValue();
		
		params = new ParameterList();
		params.addParameter(aperiodicityParam);
		
		sectAreas = fltSysRupSet.getAreaForAllSections();
	}

	@Override
	public double getProbability(int ruptureIndex, double ruptureRate, long forecastStartTimeMillis, double durationYears) {
		// for now just use gain, but could change that if it's more efficient or better in some way to calculate it separately
		double gain = getProbabilityGain(ruptureIndex, forecastStartTimeMillis, durationYears);
		return TimeDepUtils.rateToNonPoissonProb(ruptureRate*gain, durationYears);
	}

	@Override
	protected void sectDOLE_Changed() {
		// need to clear the section gain cache
		sectGainCache = null;
	}
	
	private static class SectGainCache {
		final double[] gains;
		final boolean[] gainReals;
		final long forecastStartTimeMillis;
		final double durationYears;
		
		private SectGainCache(double[] gains, boolean[] gainReals, long forecastStartTimeMillis,
				double durationYears) {
			super();
			this.gains = gains;
			this.gainReals = gainReals;
			this.forecastStartTimeMillis = forecastStartTimeMillis;
			this.durationYears = durationYears;
		}
		
		public boolean matches(long forecastStartTimeMillis, double durationYears) {
			return this.forecastStartTimeMillis == forecastStartTimeMillis && this.durationYears == durationYears;
		}
	}
	
	private SectGainCache getSectGains(long forecastStartTimeMillis, double durationYears) {
		SectGainCache cached = sectGainCache;
		if (cached != null && cached.matches(forecastStartTimeMillis, durationYears))
			return cached;
		
		EqkProbDistCalc singleCalc = null;
		double[] sectAperiodicities = null;
		AperiodicityModel aperiodicityModel = this.aperiodicityModel;
		if (aperiodicityModel instanceof AperiodicityModel.SingleValued) {
			double aperiodicity = ((AperiodicityModel.SingleValued)aperiodicityModel).getAperiodicity();
			singleCalc = getNormProbDistCalc(RENEWAL_MODEL, aperiodicity);
		} else if (aperiodicityModel instanceof AperiodicityModel.SectionDependent) {
			sectAperiodicities = ((AperiodicityModel.SectionDependent)aperiodicityModel).getSectionAperiodicity();
		} else {
			throw new IllegalStateException(getName()+" only supports single-valued or section-specific aperiodicity models");
		}
		
		// need to calculate it
		int numSections = fltSysSol.getRupSet().getNumSections();
		double[] sectionGainArray = new double[numSections];
		boolean[] sectionGainReal = new boolean[numSections];
		for(int s=0; s<numSections;s++) {
			long timeOfLastMillis = sectDOLE[s];
			if(timeOfLastMillis != Long.MIN_VALUE && timeOfLastMillis <= forecastStartTimeMillis) {
				double timeSinceLastYears = (double)(forecastStartTimeMillis-timeOfLastMillis) * TimeDepUtils.MILLISEC_TO_YEARS;
				double refTimeSinceLast = timeSinceLastYears*sectlongTermPartRates[s];
				double refDuration = durationYears*sectlongTermPartRates[s];
				EqkProbDistCalc probCalc;
				if (singleCalc != null)
					probCalc = singleCalc;
				else
					// section-specific calculator
					probCalc = getNormProbDistCalc(RENEWAL_MODEL, sectAperiodicities[s]);
				double prob_td = probCalc.getCondProb(refTimeSinceLast, refDuration);
				// can use this to debug prob_td = 0, but it occurs for valid reasons w/ low COV, so the check is disabled.
//				Preconditions.checkState(prob_td > 0d,
//						"Bad prob_td=%s for section %s. %s, sectlongTermPartRate=%s, RI=%s, timeSinceLastYears=%s,"
//						+ "duration=%s, refTimeSinceLast=%s, refDuration=%s",
//						prob_td, s, fltSysRupSet.getFaultSectionData(s).getSectionName(),
//						sectlongTermPartRates[s], 1d/sectlongTermPartRates[s],
//						timeSinceLastYears, durationYears, refTimeSinceLast, refDuration);
//				double prob_pois = 1-Math.exp(-durationYears*longTermPartRateForSectArray[s]);
				// this is their exact calculation, which is a bit different for long durations
				double prob_pois = durationYears*sectlongTermPartRates[s];
				sectionGainArray[s] = prob_td/prob_pois;
//				System.out.println("WG02 sect "+s+": gain = "+(float)prob_td+" / "+(float)prob_pois+" = "+sectionGainArray[s]);
				sectionGainReal[s]=true;
			}
			else {
				sectionGainArray[s] = 1.0;
				sectionGainReal[s]=false;
			}
		}
		
		cached = new SectGainCache(sectionGainArray, sectionGainReal, forecastStartTimeMillis, durationYears);
		sectGainCache = cached;
		return cached;
	}

	/**
	 * This returns the probability gain computed using the WG02 methodology, where the probability
	 * gain of each sections is averaged, weighted by section area (actually, this should be weighted
	 * by moment rate, but tests show no difference).
	 * 
	 * @param ruptureIndex rupture index in fault system solution
	 * @param forecastStartTimeMillis forecast start time in epoch milliseconds
	 * @param durationYears forecast duration in years
	 * @return time-dependent probability gain
	 */
	@Override
	public double getProbabilityGain(int ruptureIndex, long forecastStartTimeMillis, double durationYears) {
		return getProbabilityGain(ruptureIndex, forecastStartTimeMillis, durationYears, ONLY_IF_ALL_SECTS_HAVE_DOLE);
	}
	
	/**
	 * This returns the probability gain computed using the WG02 methodology, where the probability
	 * gain of each sections is averaged, weighted by section area (actually, this should be weighted
	 * by moment rate, but tests show no difference).
	 * 
	 * @param ruptureIndex rupture index in fault system solution
	 * @param forecastStartTimeMillis forecast start time in epoch milliseconds
	 * @param durationYears forecast duration in years
	 * @param onlyIfAllSectionsHaveDateOfLast if true, returns NaN if one or more sections lack a DOLE
	 * @return time-dependent probability gain
	 */
	public double getProbabilityGain(int ruptureIndex, long forecastStartTimeMillis, double durationYears,
			boolean onlyIfAllSectionsHaveDateOfLast) {
		SectGainCache sectGains = getSectGains(forecastStartTimeMillis, durationYears);
		
		// now compute weight-average gain for rupture
		double totalWt=0;
		double sumGains = 0;
		boolean noneAreReal = true;
		for(int sect : fltSysRupSet.getSectionsIndicesForRup(ruptureIndex)) {
//test				double wt = sectionArea[sect]*this.fltSysRupSet.getSlipRateForSection(sect);
			double wt = sectAreas[sect];
			totalWt += wt;
			sumGains += sectGains.gains[sect]*wt;
			if(sectGains.gainReals[sect] == false && onlyIfAllSectionsHaveDateOfLast) {
				return Double.NaN;
			}
			if(sectGains.gainReals[sect] == true) {
				noneAreReal=false;
			}
		}
		// can use this to debug zero gains, but it occurs for valid reasons w/ low COV, so the check is disabled.
//		Preconditions.checkState(sumGains > 0d && totalWt > 0d,
//				"sumGains=%s and totalWt=%s for rupture %s, noneAreReal=%s, forecastStartTimeMillis=%s, durationYears=%s",
//				sumGains, totalWt, ruptureIndex, noneAreReal, forecastStartTimeMillis, durationYears);
		if(noneAreReal)
			return 1d;
		else
			return sumGains/totalWt;
	}

	@Override
	public String getName() {
		return FSS_ProbabilityModels.WG02.toString();
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		aperiodicityModel = aperiodicityParam.getValue();
		// need to clear the section gain cache
		sectGainCache = null;
	}

	@Override
	public ParameterList getAdjustableParameters() {
		return params;
	}
	
	/**
	 * @return current aperiodicity model.
	 */
	public AperiodicityModel getAperiodicityModel() {
		return aperiodicityParam.getValue();
	}
	
	/**
	 * Sets the aperiodicity model to one of the enum values
	 * @param modelChoice
	 */
	public void setAperiodicityModelChoice(AperiodicityModels modelChoice) {
		Preconditions.checkNotNull(modelChoice, "Passed in aperiodicity model cannot be null");
		aperiodicityParam.setEnumValue(modelChoice);
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

}
