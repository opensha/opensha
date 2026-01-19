package scratch.UCERF3.erf;

import java.awt.HeadlessException;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;

import javax.swing.JOptionPane;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.sha.earthquake.aftershocks.MagnitudeDependentAftershockFilter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GaussianMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;

/**
 * This class represents an ERF for a given FaultSystemSolution (FSS).  Each "rupture" in the FaultSystemSolution
 * is treated as a separate source (each of which will have more than one rupture only if the 
 * AleatoryMagAreaStdDevParam has a non-zero value, or if multiple branches are represented as in subclass MeanUCERF3.
 * 
 * The fault system solution can be provided in the constructor (as an object or file name) or the file 
 * can be set in the file parameter.
 * 
 * This class make use of multiple mags for a given FSS rupture if they exist (e.g., from more than one logic tree
 * branch), but only the mean is currently used if aleatoryMagAreaStdDev !=0.
 * 
 * This filters out fault system ruptures that have zero rates, or have a magnitude below the section minimum
 * (as determined by InversionFaultSystemRupSet.isRuptureBelowSectMinMag(r)).
 * 
 * To make accessing ruptures less confusing, this class keeps track of "nth" ruptures within the ERF 
 * (see the last 7 methods here); these methods could be added to AbstractERF if more generally useful.
 * 
 * All sources are created regardless of the value of IncludeBackgroundParam
 * 
 * Subclasses can add other (non fault system) sources by simply overriding and implementing:
 * 
 *  	initOtherSources()
 *  	getOtherSource(int)
 *  
 * the first must set the numOtherSources variable (which can't change with adjustable parameters???) and must return 
 * whether the total number of ruptures has changed.  The getOtherSource(int) method must take into account any changes in 
 * the timespan duration (e.g., by making sources on the fly).
 * 
 * TODO: 
 * 
 * 1) 
 * 
 * 
 */
public class FaultSystemSolutionERF extends BaseFaultSystemSolutionERF {
	
	// this tells whether to average recurrence intervals (or rates) in computing conditional rupture RIs:
	public boolean aveRecurIntervalsInU3_BPTcalc = false;
	// this tells whether to average normalized time since last (divided by section RI) or un-normalized time since last:
	public boolean aveNormTimeSinceLastInU3_BPTcalc = true;

	
	private static final long serialVersionUID = 1L;
	
	// these are to chache timespan object for switching back and forth between time-independent (ti) and time-dependent (td) models.
	protected TimeSpan tiTimeSpanCache, tdTimeSpanCache;
	
	// Adjustable parameters, unique to this implementation
	protected AleatoryMagAreaStdDevParam aleatoryMagAreaStdDevParam;
	protected ApplyGardnerKnopoffAftershockFilterParam applyAftershockFilterParam;
	private ProbabilityModelParam probModelParam;
//	private BPT_AperiodicityParam bpt_AperiodicityParam;
	private MagDependentAperiodicityParam magDepAperiodicityParam;
	private HistoricOpenIntervalParam histOpenIntervalParam;
	private BPTAveragingTypeParam averagingTypeParam;
	
	// The primitive versions of parameters; and values here are the param defaults: (none for fileParam)
	double aleatoryMagAreaStdDev = 0.0;
	protected boolean applyAftershockFilter = false;
	protected ProbabilityModelOptions probModel = ProbabilityModelOptions.POISSON;
	private MagDependentAperiodicityOptions magDepAperiodicity = MagDependentAperiodicityOptions.MID_VALUES;
	private double histOpenInterval=0;

	// Parameter change flags:
	protected boolean aleatoryMagAreaStdDevChanged=true;
	protected boolean applyAftershockFilterChanged=true;
	protected boolean probModelChanged=true;
//	protected boolean bpt_AperiodicityChanged=true;
	protected boolean magDepAperiodicityChanged=true;
	protected boolean histOpenIntervalChanged=true;
	
	// moment-rate reduction to remove aftershocks from supra-seis ruptures
	final public static double MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS = 0.97;	// 3%

	
	// TimeSpan stuff:
	public final static int START_TIME_DEFAULT = 2014;
	protected final static int START_TIME_MIN = 1800;	// prob model calc now handles case when this is before date of last event
	protected final static int START_TIME_MAX = 2100;
	
	ProbabilityModelsCalc probModelsCalc;
	
	// preferred blend weights
	public static final double PREF_BLEND_COV_LOW_WEIGHT = 0.1;
	public static final double PREF_BLEND_COV_MID_WEIGHT = 0.4;
	public static final double PREF_BLEND_COV_HIGH_WEIGHT = 0.3;
	public static final double PREF_BLEND_POISSON_WEIGHT = 0.2;
	
	// map of weight to each ProbabilityModelsCalc instance. null value means Poisson
	Map<ProbabilityModelsCalc, Double> prefBlendProbModelsCalc;
	
	/**
	 * This creates the ERF from the given FaultSystemSolution.  FileParameter is removed 
	 * from the adjustable parameter list (to prevent changes after instantiation).
	 * @param faultSysSolution
	 */
	public FaultSystemSolutionERF(FaultSystemSolution faultSysSolution) {
		this();
		setSolution(faultSysSolution);
		// remove the fileParam from the adjustable parameter list
		adjustableParams.removeParameter(fileParam);
	}

	
	/**
	 * This creates the ERF from the given file.  FileParameter is removed from the adjustable
	 * parameter list (to prevent changes after instantiation).
	 * @param fullPathInputFile
	 */
	public FaultSystemSolutionERF(String fullPathInputFile) {
		this();
		fileParam.setValue(new File(fullPathInputFile));
		// remove the fileParam from the adjustable parameter list
		adjustableParams.removeParameter(fileParam);
	}
	
	/**
	 * This creates the ERF with a parameter for setting the input file
	 * (e.g., from a GUI).
	 */
	public FaultSystemSolutionERF() {
		super(false); // don't initialize, need initialization of class variables first
		// at this point class variables have been initialized, now call init methods
		initParams();
		initTimeSpan(); // must be done after the above because this depends on probModelParam
	}
	
	protected void initParams() {
		aleatoryMagAreaStdDevParam = new AleatoryMagAreaStdDevParam();
		applyAftershockFilterParam= new ApplyGardnerKnopoffAftershockFilterParam();  // default is false
		probModelParam = new ProbabilityModelParam();
//		bpt_AperiodicityParam = new BPT_AperiodicityParam();
		magDepAperiodicityParam = new MagDependentAperiodicityParam();
		histOpenIntervalParam = new HistoricOpenIntervalParam();
		averagingTypeParam = new BPTAveragingTypeParam();
		aleatoryMagAreaStdDevParam.addParameterChangeListener(this);
		applyAftershockFilterParam.addParameterChangeListener(this);
		probModelParam.addParameterChangeListener(this);
//		bpt_AperiodicityParam.addParameterChangeListener(this);
		magDepAperiodicityParam.addParameterChangeListener(this);
		histOpenIntervalParam.addParameterChangeListener(this);
		averagingTypeParam.addParameterChangeListener(this);
		
		// set parameters to the primitive values
		aleatoryMagAreaStdDevParam.setValue(aleatoryMagAreaStdDev);
		applyAftershockFilterParam.setValue(applyAftershockFilter);
		probModelParam.setValue(probModel);
//		bpt_AperiodicityParam.setValue(bpt_Aperiodicity);
		magDepAperiodicityParam.setValue(magDepAperiodicity);
		histOpenIntervalParam.setValue(histOpenInterval);
		// this will set the averaging method from the default value of the parameter
		updateBPTAveragingMethod();
		
		super.initParams();
	}
	
	/**
	 * Put parameters in theParameterList
	 */
	protected void postCreateParamListHook() {
		adjustableParams.addParameter(applyAftershockFilterParam);
		adjustableParams.addParameter(aleatoryMagAreaStdDevParam);
		adjustableParams.addParameter(probModelParam);
		if(!probModelParam.getValue().equals(ProbabilityModelOptions.POISSON)) {
			if(!probModelParam.getValue().equals(ProbabilityModelOptions.U3_PREF_BLEND))
				adjustableParams.addParameter(magDepAperiodicityParam);	
			adjustableParams.addParameter(histOpenIntervalParam);
		}
		if (probModelParam.getValue().equals(ProbabilityModelOptions.U3_BPT)
				|| probModelParam.getValue().equals(ProbabilityModelOptions.U3_PREF_BLEND)) {
			adjustableParams.addParameter(averagingTypeParam);
		}
	}
	
	/**
	 * This sets the date of last event on the sections associated with the given source
	 * @param srcIndex
	 * @param epoch
	 */
	public void setFltSystemSourceOccurranceTime(int srcIndex, Long epoch) {
		// set it in the fault section data objects
		int fltSysRupIndex = getFltSysRupIndexForSource(srcIndex);
		setFltSystemSourceOccurranceTimeForFSSIndex(fltSysRupIndex, epoch);
	}
	
	public double[] getNormTimeSinceLastForSections() {
		if(probModelsCalc != null)	// e.g., Poisson model
			return probModelsCalc.getNormTimeSinceLastForSections(timeSpan.getStartTimeInMillis());
		else
			return null;
	}
	
	/**
	 * This sets the date of last event on the sections associated with the given FSS rupture
	 * index. Allows for it to be set without first updating the forecast to figure out the
	 * source index.
	 * @param fltSysRupIndex
	 * @param epoch
	 */
	public void setFltSystemSourceOccurranceTimeForFSSIndex(int fltSysRupIndex, Long epoch) {
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
		List<Integer> sectIndexList = rupSet.getSectionsIndicesForRup(fltSysRupIndex);
		for(int sectIndex : sectIndexList) {
			rupSet.getFaultSectionData(sectIndex).setDateOfLastEvent(epoch);
		}
		// set it in the ProbModelCalc objects
		if(probModelsCalc != null) {
			probModelsCalc.setFltSystemRupOccurranceTime(fltSysRupIndex, epoch);
		}
		if(prefBlendProbModelsCalc != null) {
			for(ProbabilityModelsCalc calc : prefBlendProbModelsCalc.keySet()) {
				if (calc != null) // will be null for Poisson
					calc.setFltSystemRupOccurranceTime(fltSysRupIndex, epoch);
			}
		}
		// do this to make sure the probability will be updated even if nothing else changes
		probModelChanged = true;
	}
	
	/**
	 * This sets the date of last event on the given section. 
	 * @param sectIndex
	 * @param epoch
	 */
	public void setFltSectOccurranceTime(int sectIndex, Long epoch) {
		faultSysSolution.getRupSet().getFaultSectionData(sectIndex).setDateOfLastEvent(epoch);
		// set it in the ProbModelCalc objects
		if(probModelsCalc != null) {
			probModelsCalc.setFltSectRupOccurranceTime(sectIndex, epoch);
		}
		if(prefBlendProbModelsCalc != null) {
			for(ProbabilityModelsCalc calc : prefBlendProbModelsCalc.keySet()) {
				if (calc != null) // will be null for Poisson
					calc.setFltSectRupOccurranceTime(sectIndex, epoch);
			}
		}
		// do this to make sure the probability will be updated even if nothing else changes
		probModelChanged = true;
	}
	
	/**
	 * This tells whether the model is Poisson
	 * @return
	 */
	public boolean isPoisson() {
		return (probModel == ProbabilityModelOptions.POISSON);
	}
	
	@Override
	protected boolean shouldRebuildFaultSystemSources() {
		return super.shouldRebuildFaultSystemSources() || aleatoryMagAreaStdDevChanged || applyAftershockFilterChanged
				|| probModelChanged || magDepAperiodicityChanged || histOpenIntervalChanged;
	}
	
	@Override
	protected void updateHookBeforeFaultSourceBuild() {
		// update prob model calculator if needed
		if (faultSysSolutionChanged || magDepAperiodicityChanged || probModelChanged || probModelsCalc == null) {
			probModelsCalc = null;
			prefBlendProbModelsCalc = null;
			if(probModel != ProbabilityModelOptions.POISSON) {
				boolean hasTD = false;
				for (FaultSection sect : faultSysSolution.getRupSet().getFaultSectionDataList()) {
					if (sect.getDateOfLastEvent() > Long.MIN_VALUE) {
						hasTD = true;
						break;
					}
				}
				if (!hasTD) {
					String message = "WARNING: TD calculation but no sections contain date of last event data.\n"
							+ "Only historical open interval will be used in TD calculations.";
					System.out.println(message);
					try {
						JOptionPane.showMessageDialog(null, message, "WARNING: No Last Event Data", JOptionPane.ERROR_MESSAGE);
					} catch (HeadlessException e) {
						// do nothing
					}
				}
				if (probModel == ProbabilityModelOptions.U3_PREF_BLEND) {
					// now do preferred blend
					prefBlendProbModelsCalc = new HashMap<>(4);
					prefBlendProbModelsCalc.put(new ProbabilityModelsCalc(faultSysSolution, longTermRateOfFltSysRupInERF,
							MagDependentAperiodicityOptions.LOW_VALUES), PREF_BLEND_COV_LOW_WEIGHT);
					prefBlendProbModelsCalc.put(new ProbabilityModelsCalc(faultSysSolution, longTermRateOfFltSysRupInERF,
							MagDependentAperiodicityOptions.MID_VALUES), PREF_BLEND_COV_MID_WEIGHT);
					prefBlendProbModelsCalc.put(new ProbabilityModelsCalc(faultSysSolution, longTermRateOfFltSysRupInERF,
							MagDependentAperiodicityOptions.HIGH_VALUES), PREF_BLEND_COV_HIGH_WEIGHT);
					// Poisson
					prefBlendProbModelsCalc.put(null, PREF_BLEND_POISSON_WEIGHT);

					// double check that it all sums to 1
					double sum = 0;
					for (Double weight : prefBlendProbModelsCalc.values())
						sum += weight;
					Preconditions.checkState((float)sum == 1f, "Preferred Blend weights don't sum to 1!");
				} else {
					probModelsCalc = new ProbabilityModelsCalc(faultSysSolution, longTermRateOfFltSysRupInERF, magDepAperiodicity);
					if(D) {
						int numSectWith = probModelsCalc.writeSectionsWithDateOfLastEvent();
						System.out.println(numSectWith+" sections had date of last");
					}
				}
			}
		}

		super.updateHookBeforeFaultSourceBuild();
	}

	@Override
	public void updateForecast() {
		super.updateForecast();
		
		// clear flags specific to this implementation
		aleatoryMagAreaStdDevChanged = false;
		applyAftershockFilterChanged = false;
		probModelChanged = false;
		magDepAperiodicityChanged = false;
		histOpenIntervalChanged = false;
	}
	
	public static double getWeightForCOV(MagDependentAperiodicityOptions cov) {
		if (cov == null)
			return PREF_BLEND_POISSON_WEIGHT;
		switch (cov) {
		case LOW_VALUES:
			return PREF_BLEND_COV_LOW_WEIGHT;
		case MID_VALUES:
			return PREF_BLEND_COV_MID_WEIGHT;
		case HIGH_VALUES:
			return PREF_BLEND_COV_HIGH_WEIGHT;

		default:
			return 0d;
		}
	}
	
	@Override
	public void parameterChange(ParameterChangeEvent event) {
		String paramName = event.getParameterName();
		if (paramName.equalsIgnoreCase(aleatoryMagAreaStdDevParam.getName())) {
			aleatoryMagAreaStdDev = aleatoryMagAreaStdDevParam.getValue();
			aleatoryMagAreaStdDevChanged = true;
		} else if (paramName.equalsIgnoreCase(applyAftershockFilterParam.getName())) {
			applyAftershockFilter = applyAftershockFilterParam.getValue();
			applyAftershockFilterChanged = true;
		} else if (paramName.equals(probModelParam.getName())) {
			probModel = probModelParam.getValue();
			probModelChanged = true;
			initTimeSpan();
			createParamList();
		} else if (paramName.equals(magDepAperiodicityParam.getName())) {
			magDepAperiodicity = magDepAperiodicityParam.getValue();
			magDepAperiodicityChanged = true;
		} else if (paramName.equals(histOpenIntervalParam.getName())) {
			histOpenInterval = histOpenIntervalParam.getValue();
			histOpenIntervalChanged = true;
		} else if (paramName.equals(averagingTypeParam.getName())) {
			updateBPTAveragingMethod();
		} else {
			super.parameterChange(event);
		}
	}
	
	private void updateBPTAveragingMethod() {
		BPTAveragingTypeOptions types = averagingTypeParam.getValue();
		this.aveRecurIntervalsInU3_BPTcalc = types.isAveRI();
		this.aveNormTimeSinceLastInU3_BPTcalc = types.isAveNTS();
		histOpenIntervalChanged = true; // to ensure probabilities are updated
		if (D) System.out.println("Ave type updated: isRI: "+aveRecurIntervalsInU3_BPTcalc
				+" is NTS: "+aveNormTimeSinceLastInU3_BPTcalc);
	}

	/**
	 * This initiates the timeSpan.
	 */
	@Override
	protected void initTimeSpan() {
		if(probModel == ProbabilityModelOptions.POISSON) {
			if(tiTimeSpanCache == null) {
				tiTimeSpanCache = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
				tiTimeSpanCache.setDuration(DURATION_DEFAULT);
				tiTimeSpanCache.addParameterChangeListener(this);
			}
			timeSpan = tiTimeSpanCache;
		}
		else {
			if(tdTimeSpanCache == null) {
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
	protected boolean isRuptureIncluded(int fltSystRupIndex) {
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
		if (rupSet instanceof InversionFaultSystemRupSet)
			return !((InversionFaultSystemRupSet)rupSet).isRuptureBelowSectMinMag(fltSystRupIndex);
		ModSectMinMags minMags = rupSet.getModule(ModSectMinMags.class);
		if (minMags != null)
			return !minMags.isRupBelowSectMinMag(fltSystRupIndex);
		return true;
	}

	@Override
	protected MagnitudeDependentAftershockFilter getGridSourceAftershockFilter() {
		return applyAftershockFilter ? AbstractGridSourceProvider.GK_AFTERSHOCK_FILTER : null;
	}

	@Override
	protected DiscretizedFunc getFaultSysRupMFD(int fltSystRupIndex) {
		if (aleatoryMagAreaStdDev != 0) {
			FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
			double meanMag = rupSet.getMagForRup(fltSystRupIndex);
			double rupRate = longTermRateOfFltSysRupInERF[fltSystRupIndex];
			double totMoRate = rupRate*MagUtils.magToMoment(meanMag);
			return new GaussianMagFreqDist(5.05, 8.65, 37, meanMag, aleatoryMagAreaStdDev, totMoRate, 2.0, 2);
		}
		return super.getFaultSysRupMFD(fltSystRupIndex);
	}


	@Override
	protected double getFaultSysRupRateGain(int fltSystRupIndex) {
		double aftRateCorr = 1.0;
		if(applyAftershockFilter)
			aftRateCorr = MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS;
		
		double duration = timeSpan.getDuration();
		double probGain;
		switch (probModel) {
		case POISSON:
			probGain = 1.0;
			break;
		case U3_BPT:
			probGain = probModelsCalc.getU3_ProbGainForRup(fltSystRupIndex, histOpenInterval, false, aveRecurIntervalsInU3_BPTcalc, 
					aveNormTimeSinceLastInU3_BPTcalc, timeSpan.getStartTimeInMillis(), duration);
// TEST FOR CONSIDERING ONLY RUPS WITH DATE OF LAST EVENT ON ALL SECTIONS
//probGain = probModelsCalc.getU3_ProbGainForRup(fltSystRupIndex, histOpenInterval, true, aveRecurIntervalsInU3_BPTcalc, 
//		aveNormTimeSinceLastInU3_BPTcalc, timeSpan.getStartTimeInMillis(), duration);
//if(Double.isNaN(probGain))
//		probGain=0;
			break;
		case U3_PREF_BLEND:
			probGain = 0;
			for (ProbabilityModelsCalc calc : prefBlendProbModelsCalc.keySet()) {
				double weight = prefBlendProbModelsCalc.get(calc);
				double subProbGain;
				if (calc == null) {
					// poisson
					subProbGain = 1d;
				} else {
					subProbGain = calc.getU3_ProbGainForRup(fltSystRupIndex, histOpenInterval, false, aveRecurIntervalsInU3_BPTcalc, 
							aveNormTimeSinceLastInU3_BPTcalc, timeSpan.getStartTimeInMillis(), duration);
				}
				probGain += weight*subProbGain;
			}
			break;
		case WG02_BPT:
			probGain = probModelsCalc.getWG02_ProbGainForRup(fltSystRupIndex, false, timeSpan.getStartTimeInMillis(), duration);
			break;

		default:
			throw new IllegalStateException("Unrecognized Probability Model");
		}
		
		return aftRateCorr*probGain;
	}

	@Override
	protected boolean isFaultSysRupPoisson(int fltSystRupIndex) {
		return probModel != ProbabilityModelOptions.U3_BPT && probModel != ProbabilityModelOptions.U3_PREF_BLEND;
	}
	
	/**
	 * TODO move this elsewhere (e.g., abstract parent)?
	 * @param fileNameAndPath
	 */
	public void writeSourceNamesToFile(String fileNameAndPath) {
		try{
			FileWriter fw1 = new FileWriter(fileNameAndPath);
			fw1.write("s\tname\n");
			for(int i=0;i<this.getNumSources();i++) {
				fw1.write(i+"\t"+getSource(i).getName()+"\n");
			}
			fw1.close();
		}catch(Exception e) {
			e.printStackTrace();
		}

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
					double dateOfLast = 1970+fltData.getDateOfLastEvent()/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
					double startTimeYear = 1970+startTime/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
					System.out.println("Expunged Date of Last: "+dateOfLast+" (>"+startTimeYear+") for "+fltData.getName());
				}
				fltData.setDateOfLastEvent(Long.MIN_VALUE);
			}
		}
		probModelsCalc = null;
	}
	
	public static void main(String[] args) {
		
		long runtime = System.currentTimeMillis();

		String fileName="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(fileName);
		
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
		
		erf.updateForecast();
		
		System.out.println("run took "+runtime/(1000*60)+" minutes");

	}
}
