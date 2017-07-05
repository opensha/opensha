package scratch.UCERF3.erf;

import static com.google.common.base.Preconditions.checkArgument;
import static org.opensha.sha.earthquake.param.IncludeBackgroundOption.EXCLUDE;
import static org.opensha.sha.earthquake.param.IncludeBackgroundOption.ONLY;

import java.awt.HeadlessException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.FileParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractNthRupERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;
import org.opensha.sha.earthquake.param.BPT_AperiodicityParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.FaultGridSpacingParam;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.magdist.GaussianMagFreqDist;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.LastEventData;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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
public class FaultSystemSolutionERF extends AbstractNthRupERF {
	
	// this tells whether to average recurrence intervals (or rates) in computing conditional rupture RIs:
	public boolean aveRecurIntervalsInU3_BPTcalc = false;
	// this tells whether to average normalized time since last (divided by section RI) or un-normalized time since last:
	public boolean aveNormTimeSinceLastInU3_BPTcalc = true;

	
	private static final long serialVersionUID = 1L;
	
	private static final boolean D = false;

	public static final String NAME = "Fault System Solution ERF";
	private String name = NAME;
	
	// these are to chache timespan object for switching back and forth between time-independent (ti) and time-dependent (td) models.
	protected TimeSpan tiTimeSpanCache, tdTimeSpanCache;
	
	// Adjustable parameters
	public static final String FILE_PARAM_NAME = "Solution Input File";
	protected FileParameter fileParam;
	protected boolean includeFileParam = true;
	protected FaultGridSpacingParam faultGridSpacingParam;
	protected AleatoryMagAreaStdDevParam aleatoryMagAreaStdDevParam;
	protected ApplyGardnerKnopoffAftershockFilterParam applyAftershockFilterParam;
	protected IncludeBackgroundParam bgIncludeParam;
	protected BackgroundRupParam bgRupTypeParam;
	public static final String QUAD_SURFACES_PARAM_NAME = "Use Quad Surfaces (otherwise gridded)";
	public static final boolean QUAD_SURFACES_PARAM_DEFAULT = false;
	private BooleanParameter quadSurfacesParam;
	private ProbabilityModelParam probModelParam;
//	private BPT_AperiodicityParam bpt_AperiodicityParam;
	private MagDependentAperiodicityParam magDepAperiodicityParam;
	private HistoricOpenIntervalParam histOpenIntervalParam;
	private BPTAveragingTypeParam averagingTypeParam;

	
	// The primitive versions of parameters; and values here are the param defaults: (none for fileParam)
	protected double faultGridSpacing = 1.0;
	double aleatoryMagAreaStdDev = 0.0;
	protected boolean applyAftershockFilter = false;
	protected IncludeBackgroundOption bgInclude = IncludeBackgroundOption.INCLUDE;
	protected BackgroundRupType bgRupType = BackgroundRupType.POINT;
	private boolean quadSurfaces = false;
	protected ProbabilityModelOptions probModel = ProbabilityModelOptions.POISSON;
//	private double bpt_Aperiodicity=0.3;
	private MagDependentAperiodicityOptions magDepAperiodicity = MagDependentAperiodicityOptions.MID_VALUES;
	private double histOpenInterval=0;

	// Parameter change flags: (none for bgIncludeParam) 
	protected boolean fileParamChanged=false;	// set as false since most subclasses ignore this parameter
	protected boolean faultGridSpacingChanged=true;
	protected boolean aleatoryMagAreaStdDevChanged=true;
	protected boolean applyAftershockFilterChanged=true;
	protected boolean bgRupTypeChanged=true;
	protected boolean quadSurfacesChanged=true;
	protected boolean probModelChanged=true;
//	protected boolean bpt_AperiodicityChanged=true;
	protected boolean magDepAperiodicityChanged=true;
	protected boolean histOpenIntervalChanged=true;
	

	// moment-rate reduction to remove aftershocks from supra-seis ruptures
	final public static double MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS = 0.97;	// 3%

	
	// TimeSpan stuff:
	protected final static double DURATION_DEFAULT = 30;	// years
	protected final static double DURATION_MIN = 0.0001;
	public final static double DURATION_MAX = 100000;
	public final static int START_TIME_DEFAULT = 2014;
	protected final static int START_TIME_MIN = 1800;	// prob model calc now handles case when this is before date of last event
	protected final static int START_TIME_MAX = 2100;
	boolean timeSpanChangeFlag=true;	// this keeps track of time span changes
	
	// these help keep track of what's changed
	private boolean faultSysSolutionChanged = true;
	
	// leave as a FaultSystemSolution for use with Simulator/other FSS
	private FaultSystemSolution faultSysSolution;		// the FFS for the ERF
	private GridSourceProvider gridSources;				// grid sources from the FSS
	protected int numNonZeroFaultSystemSources;			// this is the number of faultSystemRups with non-zero rates (each is a source here)
	int totNumRupsFromFaultSystem;						// the sum of all nth ruptures that come from fault system sources (and not equal to faultSysSolution.getNumRuptures())
	
	protected int numOtherSources=0; 					// the non fault system sources
	protected int[] fltSysRupIndexForSource;  			// used to keep only inv rups with non-zero rates
	protected int[] srcIndexForFltSysRup;				// this stores the src index for the fault system source (-1 if there is no mapping)
	protected int[] fltSysRupIndexForNthRup;			// the fault system rupture index for the nth rup
	protected double[] longTermRateOfFltSysRupInERF;	// this holds the long-term rate of FSS rups as used by this ERF (e.g., small mags set to rate of zero); these rates include aftershocks	
	
	protected List<FaultRuptureSource> faultSourceList;
	
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
		initParams();
		initTimeSpan(); // must be done after the above because this depends on probModelParam
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

	
	
	protected void initParams() {
		fileParam = new FileParameter(FILE_PARAM_NAME);
		faultGridSpacingParam = new FaultGridSpacingParam();
		aleatoryMagAreaStdDevParam = new AleatoryMagAreaStdDevParam();
		applyAftershockFilterParam= new ApplyGardnerKnopoffAftershockFilterParam();  // default is false
		bgIncludeParam = new IncludeBackgroundParam();
		bgRupTypeParam = new BackgroundRupParam();
		quadSurfacesParam = new BooleanParameter(QUAD_SURFACES_PARAM_NAME, QUAD_SURFACES_PARAM_DEFAULT);
		probModelParam = new ProbabilityModelParam();
//		bpt_AperiodicityParam = new BPT_AperiodicityParam();
		magDepAperiodicityParam = new MagDependentAperiodicityParam();
		histOpenIntervalParam = new HistoricOpenIntervalParam();
		averagingTypeParam = new BPTAveragingTypeParam();


		// set listeners
		fileParam.addParameterChangeListener(this);
		faultGridSpacingParam.addParameterChangeListener(this);
		aleatoryMagAreaStdDevParam.addParameterChangeListener(this);
		applyAftershockFilterParam.addParameterChangeListener(this);
		bgIncludeParam.addParameterChangeListener(this);
		bgRupTypeParam.addParameterChangeListener(this);
		quadSurfacesParam.addParameterChangeListener(this);
		probModelParam.addParameterChangeListener(this);
//		bpt_AperiodicityParam.addParameterChangeListener(this);
		magDepAperiodicityParam.addParameterChangeListener(this);
		histOpenIntervalParam.addParameterChangeListener(this);
		averagingTypeParam.addParameterChangeListener(this);

		
		// set parameters to the primitive values
		// don't do anything here for fileParam 
		faultGridSpacingParam.setValue(faultGridSpacing);
		aleatoryMagAreaStdDevParam.setValue(aleatoryMagAreaStdDev);
		applyAftershockFilterParam.setValue(applyAftershockFilter);
		bgIncludeParam.setValue(bgInclude);
		bgRupTypeParam.setValue(bgRupType);
		quadSurfacesParam.setValue(quadSurfaces);
		probModelParam.setValue(probModel);
//		bpt_AperiodicityParam.setValue(bpt_Aperiodicity);
		magDepAperiodicityParam.setValue(magDepAperiodicity);
		histOpenIntervalParam.setValue(histOpenInterval);
		// this will set the averaging method from the default value of the parameter
		updateBPTAveragingMethod();

		createParamList();
	}
	
	/**
	 * Put parameters in theParameterList
	 */
	protected void createParamList() {
		adjustableParams = new ParameterList();
		if(includeFileParam)
			adjustableParams.addParameter(fileParam);
		adjustableParams.addParameter(applyAftershockFilterParam);
		adjustableParams.addParameter(aleatoryMagAreaStdDevParam);
		adjustableParams.addParameter(bgIncludeParam);
		if(!bgIncludeParam.getValue().equals(IncludeBackgroundOption.EXCLUDE)) {
			adjustableParams.addParameter(bgRupTypeParam);
		}
		adjustableParams.addParameter(quadSurfacesParam);
		if(quadSurfacesParam.getValue().equals(false)) {
			adjustableParams.addParameter(faultGridSpacingParam);
		}
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
	 * This returns the number of fault system sources
	 * (that have non-zero rates)
	 * @return
	 */
	public int getNumFaultSystemSources(){
		return numNonZeroFaultSystemSources;
	}
	
	/**
	 * This tells whether the model is Poisson
	 * @return
	 */
	public boolean isPoisson() {
		return (probModel == ProbabilityModelOptions.POISSON);
	}
	
	@Override
	public void updateForecast() {
		
		if (D) System.out.println("Updating forecast");
		long runTime = System.currentTimeMillis();
		
		// read FSS solution from file if specified;
		// this sets faultSysSolutionChanged and bgRupTypeChanged (since this is obtained from the FSS) as true
		if(fileParamChanged) {
			readFaultSysSolutionFromFile();	// this will not re-read the file if the name has not changed
		}
		
		// update other sources if needed
		boolean numOtherRupsChanged=false;	// this is needed below
		if(bgRupTypeChanged) {
			numOtherRupsChanged = initOtherSources();	// these are created even if not used; this sets numOtherSources
		}
		
		// update following FSS-related arrays if needed: longTermRateOfFltSysRupInERF[], srcIndexForFltSysRup[], fltSysRupIndexForSource[], numNonZeroFaultSystemSources
		boolean numFaultRupsChanged = false;	// needed below as well
		if (faultSysSolutionChanged) {	
			makeMiscFSS_Arrays(); 
			numFaultRupsChanged = true;	// not necessarily true, but a safe assumption
		}
		
		// update prob model calculator if needed
		if (faultSysSolutionChanged || magDepAperiodicityChanged || probModelChanged || probModelsCalc == null) {
			probModelsCalc = null;
			prefBlendProbModelsCalc = null;
			if(probModel != ProbabilityModelOptions.POISSON) {
				boolean hasTD = false;
				for (FaultSectionPrefData sect : faultSysSolution.getRupSet().getFaultSectionDataList()) {
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
					prefBlendProbModelsCalc = Maps.newHashMap();
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

		// now make the list of fault-system sources if any of the following have changed
		if (faultSysSolutionChanged || faultGridSpacingChanged || aleatoryMagAreaStdDevChanged || applyAftershockFilterChanged || 
				quadSurfacesChanged || probModelChanged || magDepAperiodicityChanged || timeSpanChangeFlag || histOpenIntervalChanged) {
			makeAllFaultSystemSources();	// overrides all fault-based source objects; created even if not fault sources aren't wanted
		}
		
		// update the following ERF rup-related fields: totNumRups, totNumRupsFromFaultSystem, nthRupIndicesForSource, srcIndexForNthRup[], rupIndexForNthRup[], fltSysRupIndexForNthRup[]
		if(numOtherRupsChanged || numFaultRupsChanged) {
			setAllNthRupRelatedArrays();
		}

		// reset change flags (that haven't already been done so)
		fileParamChanged = false;
		faultSysSolutionChanged = false;
		faultGridSpacingChanged = false;
		aleatoryMagAreaStdDevChanged = false;
		applyAftershockFilterChanged = false;
		bgRupTypeChanged = false;			
		quadSurfacesChanged= false;
		probModelChanged = false;
		magDepAperiodicityChanged = false;
		histOpenIntervalChanged = false;
		timeSpanChangeFlag = false;
		
		runTime = (System.currentTimeMillis()-runTime)/1000;
		if(D) {
			System.out.println("Done updating forecast (took "+runTime+" seconds)");
			System.out.println("numFaultSystemSources="+numNonZeroFaultSystemSources);
			System.out.println("totNumRupsFromFaultSystem="+totNumRupsFromFaultSystem);
			System.out.println("totNumRups="+totNumRups);
			System.out.println("numOtherSources="+this.numOtherSources);
			System.out.println("getNumSources()="+this.getNumSources());
		}
		
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
		super.parameterChange(event);	// sets parameterChangeFlag = true;
		String paramName = event.getParameterName();
		if(paramName.equalsIgnoreCase(fileParam.getName())) {
			fileParamChanged=true;
		} else if(paramName.equalsIgnoreCase(faultGridSpacingParam.getName())) {
			faultGridSpacing = faultGridSpacingParam.getValue();
			faultGridSpacingChanged=true;
		} else if (paramName.equalsIgnoreCase(aleatoryMagAreaStdDevParam.getName())) {
			aleatoryMagAreaStdDev = aleatoryMagAreaStdDevParam.getValue();
			aleatoryMagAreaStdDevChanged = true;
		} else if (paramName.equalsIgnoreCase(applyAftershockFilterParam.getName())) {
			applyAftershockFilter = applyAftershockFilterParam.getValue();
			applyAftershockFilterChanged = true;
		} else if (paramName.equalsIgnoreCase(bgIncludeParam.getName())) {
			bgInclude = bgIncludeParam.getValue();
			createParamList();
		} else if (paramName.equalsIgnoreCase(bgRupTypeParam.getName())) {
			bgRupType = bgRupTypeParam.getValue();
			bgRupTypeChanged = true;
		} else if (paramName.equals(QUAD_SURFACES_PARAM_NAME)) {
			quadSurfaces = quadSurfacesParam.getValue();
			quadSurfacesChanged = true;
			createParamList();
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
			throw new RuntimeException("parameter name not recognized");
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

	
	/**
	 * This method initializes the following arrays:
	 * 
	 *		longTermRateOfFltSysRupInERF[]
	 * 		srcIndexForFltSysRup[]
	 * 		fltSysRupIndexForSource[]
	 * 		numNonZeroFaultSystemSources
	 */
	private void makeMiscFSS_Arrays() {
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
		longTermRateOfFltSysRupInERF = new double[rupSet.getNumRuptures()];
				
		if(D) {
			System.out.println("Running makeFaultSystemSources() ...");
			System.out.println("   aleatoryMagAreaStdDev = "+aleatoryMagAreaStdDev);
			System.out.println("   faultGridSpacing = "+faultGridSpacing);
			System.out.println("   faultSysSolution.getNumRuptures() = "
					+rupSet.getNumRuptures());
		}
		
		numNonZeroFaultSystemSources =0;
		ArrayList<Integer> fltSysRupIndexForSourceList = new ArrayList<Integer>();
		srcIndexForFltSysRup = new int[rupSet.getNumRuptures()];
		for(int i=0; i<srcIndexForFltSysRup.length;i++)
			srcIndexForFltSysRup[i] = -1;				// initialize values to -1 (no mapping due to zero rate or mag too small)
		int srcIndex = 0;
		// loop over FSS ruptures
		for(int r=0; r< rupSet.getNumRuptures();r++){
			boolean rupTooSmall = false;	// filter out the too-small ruptures
			if(rupSet instanceof InversionFaultSystemRupSet)
				rupTooSmall = ((InversionFaultSystemRupSet)rupSet).isRuptureBelowSectMinMag(r);
//			System.out.println("rate="+faultSysSolution.getRateForRup(r));
			if(faultSysSolution.getRateForRup(r) > 0.0 && !rupTooSmall) {
				numNonZeroFaultSystemSources +=1;
				fltSysRupIndexForSourceList.add(r);
				srcIndexForFltSysRup[r] = srcIndex;
				longTermRateOfFltSysRupInERF[r] = faultSysSolution.getRateForRup(r);
				srcIndex += 1;
			}
		}
		
		// convert the list to array
		if(fltSysRupIndexForSourceList.size() != numNonZeroFaultSystemSources)
			throw new RuntimeException("Problem");
		fltSysRupIndexForSource = new int[numNonZeroFaultSystemSources];
		for(int i=0;i<numNonZeroFaultSystemSources;i++)
			fltSysRupIndexForSource[i] = fltSysRupIndexForSourceList.get(i);
		
		if(D) {
			System.out.println("   " + numNonZeroFaultSystemSources+" of "+
					rupSet.getNumRuptures()+ 
					" fault system sources had non-zero rates");
		}
	}
		
	/**
	 * This makes all the fault-system sources and put them into faultSourceList
	 */
	private void makeAllFaultSystemSources() {
		faultSourceList = Lists.newArrayList();
		for (int i=0; i<numNonZeroFaultSystemSources; i++) {
			faultSourceList.add(makeFaultSystemSource(i));
		}
	}
	
	
	public double[] getLongTermRateOfFltSysRupInERF() {
		return longTermRateOfFltSysRupInERF;
	}
	
	
	/**
	 * This returns the fault system rupture index for the ith source
	 * @param iSrc
	 * @return
	 */
	public int getFltSysRupIndexForSource(int iSrc) {
		return fltSysRupIndexForSource[iSrc];
	}
	
	
	private void readFaultSysSolutionFromFile() {
		// set input file
		File file = fileParam.getValue();
		if (file == null) throw new RuntimeException("No solution file specified");

		if (D) System.out.println("Loading solution from: "+file.getAbsolutePath());
		long runTime = System.currentTimeMillis();
		try {
			setSolution(FaultSystemIO.loadSol(file), false);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if(D) {
			runTime = (System.currentTimeMillis()-runTime)/1000;
			if(D) System.out.println("Loading solution took "+runTime+" seconds.");
		}
	}
	
	/**
	 * Set the current solution. Can overridden to ensure it is a particular subclass.
	 * This sets both faultSysSolutionChanged and bgRupTypeChanged as true.
	 * @param sol
	 */
	public void setSolution(FaultSystemSolution sol) {
		setSolution(sol, true);
	}
	
	private void setSolution(FaultSystemSolution sol, boolean clearFileParam) {
		this.faultSysSolution = sol;
		if (sol == null)
			this.gridSources = null;
		else
			this.gridSources = sol.getGridSourceProvider();
		if (clearFileParam) {
			// this means that the method was called manually, clear the file param so that
			// any subsequent sets to the file parameter trigger an update and override this
			// current solution.
			synchronized (fileParam) {
				fileParam.removeParameterChangeListener(this);
				fileParam.setValue(null);
				fileParam.addParameterChangeListener(this);
			}
		}
		faultSysSolutionChanged = true;
		bgRupTypeChanged = true;  // because the background ruptures come from the FSS
		// have to set fileParamChanged to false in case you set the file param and then call
		// setSolution manually before doing an update forecast
		fileParamChanged = false;
	}
	
	public FaultSystemSolution getSolution() {
		return faultSysSolution;
	}

	@Override
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		checkArgument(!StringUtils.isBlank(name), "Name cannot be empty");
		this.name = name;
	}

	@Override
	public int getNumSources() {
		if (bgInclude.equals(ONLY)) return numOtherSources;
		if (bgInclude.equals(EXCLUDE)) return numNonZeroFaultSystemSources;
		return numNonZeroFaultSystemSources + numOtherSources;
	}
	
	@Override
	public ProbEqkSource getSource(int iSource) {
		if (bgInclude.equals(ONLY)) {
			return getOtherSource(iSource);
		} else if(bgInclude.equals(EXCLUDE)) {
			return faultSourceList.get(iSource);
		} else if (iSource < numNonZeroFaultSystemSources) {
			return faultSourceList.get(iSource);
		} else {
			return getOtherSource(iSource - numNonZeroFaultSystemSources);
		}
	}
	
	/**
	 * This returns a source that includes only the subseismo component
	 * for the grid cell.  This returns null is the iSource is fault based,
	 * or if the grid cell does not have any subseismo component.
	 * @param iSource
	 * @return
	 */
	public ProbEqkSource getSourceSubSeisOnly(int iSource) {
		
		if (bgInclude.equals(ONLY)) {
			if (gridSources == null)
				return null;
			else
				return gridSources.getSourceSubseismoOnly(iSource, timeSpan.getDuration(),
						applyAftershockFilter, bgRupType);
		} else if(bgInclude.equals(EXCLUDE)) {
			return null;
		} else if (iSource < numNonZeroFaultSystemSources) {
			return null;
		} else {
			if (gridSources == null)
				return null;
			else
				return gridSources.getSourceSubseismoOnly(iSource - numNonZeroFaultSystemSources, timeSpan.getDuration(),
						applyAftershockFilter, bgRupType);
		}
	}
	
	
	/**
	 * This returns a source that includes only the truly off fault component
	 * for the grid cell.  This returns null is the iSource is fault based,
	 * or if the grid cell does not have any truly off fault component.
	 * @param iSource
	 * @return
	 */
	public ProbEqkSource getSourceTrulyOffOnly(int iSource) {
		
		if (bgInclude.equals(ONLY)) {
			if (gridSources == null)
				return null;
			else
				return gridSources.getSourceTrulyOffOnly(iSource, timeSpan.getDuration(),
						applyAftershockFilter, bgRupType);
		} else if(bgInclude.equals(EXCLUDE)) {
			return null;
		} else if (iSource < numNonZeroFaultSystemSources) {
			return null;
		} else {
			if (gridSources == null)
				return null;
			else
				return gridSources.getSourceTrulyOffOnly(iSource - numNonZeroFaultSystemSources, timeSpan.getDuration(),
						applyAftershockFilter, bgRupType);
		}
	}



	/**
	 * Creates a fault source.
	 * @param iSource - source index in ERF
	 * @return
	 */
	protected FaultRuptureSource makeFaultSystemSource(int iSource) {
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
		int fltSystRupIndex = fltSysRupIndexForSource[iSource];
		FaultRuptureSource src;
		
		double meanMag = rupSet.getMagForRup(fltSystRupIndex);	// this is the average if there are more than one mags
		
		double duration = timeSpan.getDuration();
		
		// set aftershock rate correction
		double aftRateCorr = 1.0;
		if(applyAftershockFilter) aftRateCorr = MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS; // GardnerKnopoffAftershockFilter.scaleForMagnitude(mag);
		
		// get time-dependent probability gain
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

		boolean isPoisson = true;		// this is for setting the source type
		
		double myAleatoryMagAreaStdDev = aleatoryMagAreaStdDev;
		if (myAleatoryMagAreaStdDev != 0) {
			// this tests if the rate will be zero when converted to a rate due to double precision issues
			// with tiny probabilities. if so, turn off variability as it would result in an empty source
			double rupProb = aftRateCorr*probGain*faultSysSolution.getRateForRup(fltSystRupIndex)*duration;
			double rupRate = -Math.log(1-rupProb)/duration;
			if (rupRate == 0d) {
				// turn aleatory off for this rupture
				myAleatoryMagAreaStdDev = 0d;
			}
		}
		
		if(myAleatoryMagAreaStdDev == 0) {
			// TODO allow rup MFD with aleatory?
			DiscretizedFunc rupMFD = faultSysSolution.getRupMagDist(fltSystRupIndex);	// this exists for multi-branch mean solutions
			if (rupMFD == null || rupMFD.size() < 2) {	// single mag source
				// set source type
				double prob;
				if(probModel == ProbabilityModelOptions.U3_BPT || probModel == ProbabilityModelOptions.U3_PREF_BLEND) {
					prob = aftRateCorr*probGain*faultSysSolution.getRateForRup(fltSystRupIndex)*duration;
					isPoisson = false;	// this is only the probability of the next event
				}
				else
					prob = 1-Math.exp(-aftRateCorr*probGain*faultSysSolution.getRateForRup(fltSystRupIndex)*duration);

				src = new FaultRuptureSource(meanMag, 
						rupSet.getSurfaceForRupupture(fltSystRupIndex, faultGridSpacing, quadSurfaces), 
						rupSet.getAveRakeForRup(fltSystRupIndex), prob, isPoisson);
			} else {
					// apply aftershock and/or gain corrections
				DiscretizedFunc rupMFDcorrected = rupMFD.deepClone();
				if(probModel == ProbabilityModelOptions.U3_BPT || probModel == ProbabilityModelOptions.U3_PREF_BLEND) {
					for(int i=0;i<rupMFDcorrected.size();i++) {
						double origRate = rupMFDcorrected.getY(i);
						double prob = aftRateCorr*probGain*origRate*duration;
						double equivRate = -Math.log(1-prob)/duration;
						rupMFDcorrected.set(i,equivRate);
					}
				}
				else {	// WG02 and Poisson case
					rupMFDcorrected.scale(aftRateCorr*probGain);					
				}
					
				// this set the source as Poisson for U3; does this matter? TODO
				src = new FaultRuptureSource(rupMFDcorrected, 
						rupSet.getSurfaceForRupupture(fltSystRupIndex, faultGridSpacing, quadSurfaces),
						rupSet.getAveRakeForRup(fltSystRupIndex), timeSpan.getDuration());
			}
		} else {
			// this currently only uses the mean magnitude
			double rupRate;
			if(probModel == ProbabilityModelOptions.U3_BPT || probModel == ProbabilityModelOptions.U3_PREF_BLEND) {
				double rupProb = aftRateCorr*probGain*faultSysSolution.getRateForRup(fltSystRupIndex)*duration;
				rupRate = -Math.log(1-rupProb)/duration;
			}
			else {
				rupRate = aftRateCorr*probGain*faultSysSolution.getRateForRup(fltSystRupIndex);
			}
			double totMoRate = rupRate*MagUtils.magToMoment(meanMag);
			GaussianMagFreqDist srcMFD = new GaussianMagFreqDist(5.05,8.65,37,meanMag,myAleatoryMagAreaStdDev,totMoRate,2.0,2);
			// this also sets the source as Poisson for U3; does this matter? TODO
			src = new FaultRuptureSource(srcMFD, 
					rupSet.getSurfaceForRupupture(fltSystRupIndex, faultGridSpacing, quadSurfaces),
					rupSet.getAveRakeForRup(fltSystRupIndex), timeSpan.getDuration());
			Preconditions.checkState(src.getNumRuptures() > 0,
					"Source has zero rups! Mag="+meanMag+", aleatoryMagAreaStdDev="+myAleatoryMagAreaStdDev
					+", fssRate="+faultSysSolution.getRateForRup(fltSystRupIndex)+", adjRupRate="+rupRate
					+", probGain="+probGain+", mft.getNum()="+srcMFD.size());
		}
		// make and set the name
		List<FaultSectionPrefData> data = rupSet.getFaultSectionDataForRupture(fltSystRupIndex);
		String name = data.size()+" SECTIONS BETWEEN "+data.get(0).getName()+" AND "+data.get(data.size()-1).getName();
		src.setName("Inversion Src #"+fltSystRupIndex+"; "+name);
		return src;
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
	 * This provides a mechanism for adding other sources in subclasses
	 * @param iSource - note that this index is relative to the other sources list (numFaultSystemSources has already been subtracted out)
	 * @return
	 */
	protected ProbEqkSource getOtherSource(int iSource) {
		if (gridSources == null)
			return null;
		return gridSources.getSource(iSource, timeSpan.getDuration(),
				applyAftershockFilter, bgRupType);
	}
	
	/**
	 * Any subclasses that wants to include other (gridded) sources can override
	 * this method (and the getOtherSource() method), and make sure you return true if the
	 * number of ruptures changes.
	 */
	protected boolean initOtherSources() {
		if(bgRupTypeChanged) {
			if (gridSources == null) {
				int prevOther = numOtherSources;
				numOtherSources = 0;
				// return true only if we used to have grid sources but now don't
				return prevOther > 0;
			}
			numOtherSources = gridSources.size();
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public void timeSpanChange(EventObject event) {
		timeSpanChangeFlag = true;
	}
	
	
	
	/**
	 * This sets the following: totNumRups, nthRupIndicesForSource, srcIndexForNthRup[], 
	 * rupIndexForNthRup[], fltSysRupIndexForNthRup[], and totNumRupsFromFaultSystem.  
	 * The latter two are how this differs from the parent method.
	 * 
	 */
	@Override
	protected void setAllNthRupRelatedArrays() {
		
		if(D) System.out.println("Running setAllNthRupRelatedArrays()");
		
		totNumRups=0;
		totNumRupsFromFaultSystem=0;
		nthRupIndicesForSource = new ArrayList<int[]>();

		// make temp array lists to avoid making each source twice
		ArrayList<Integer> tempSrcIndexForNthRup = new ArrayList<Integer>();
		ArrayList<Integer> tempRupIndexForNthRup = new ArrayList<Integer>();
		ArrayList<Integer> tempFltSysRupIndexForNthRup = new ArrayList<Integer>();
		int n=0;
		
		for(int s=0; s<getNumSources(); s++) {	// this includes gridded sources
			int numRups = getSource(s).getNumRuptures();
			totNumRups += numRups;
			if(s<numNonZeroFaultSystemSources) {
				totNumRupsFromFaultSystem += numRups;
			}
			int[] nthRupsForSrc = new int[numRups];
			for(int r=0; r<numRups; r++) {
				tempSrcIndexForNthRup.add(s);
				tempRupIndexForNthRup.add(r);
				if(s<numNonZeroFaultSystemSources)
					tempFltSysRupIndexForNthRup.add(fltSysRupIndexForSource[s]);
				nthRupsForSrc[r]=n;
				n++;
			}
			nthRupIndicesForSource.add(nthRupsForSrc);
		}
		// now make final int[] arrays
		srcIndexForNthRup = new int[tempSrcIndexForNthRup.size()];
		rupIndexForNthRup = new int[tempRupIndexForNthRup.size()];
		fltSysRupIndexForNthRup = new int[tempFltSysRupIndexForNthRup.size()];
		for(n=0; n<totNumRups;n++)
		{
			srcIndexForNthRup[n]=tempSrcIndexForNthRup.get(n);
			rupIndexForNthRup[n]=tempRupIndexForNthRup.get(n);
			if(n<tempFltSysRupIndexForNthRup.size())
				fltSysRupIndexForNthRup[n] = tempFltSysRupIndexForNthRup.get(n);
		}
				
		if (D) {
			System.out.println("   getNumSources() = "+getNumSources());
			System.out.println("   totNumRupsFromFaultSystem = "+totNumRupsFromFaultSystem);
			System.out.println("   totNumRups = "+totNumRups);
		}
	}
	
	/**
	 * This returns the fault system rupture index for the Nth rupture
	 * @param nthRup
	 * @return
	 */
	public int getFltSysRupIndexForNthRup(int nthRup) {
		return fltSysRupIndexForNthRup[nthRup];
	}

	/**
	 * this returns the src index for a given fault-system rupture
	 * index
	 * @param fltSysRupIndex
	 * @return
	 */
	public int getSrcIndexForFltSysRup(int fltSysRupIndex) {
		return srcIndexForFltSysRup[fltSysRupIndex];
	}
	
	public int getTotNumRupsFromFaultSystem() {
		return totNumRupsFromFaultSystem;
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
		for(FaultSectionPrefData fltData : faultSysSolution.getRupSet().getFaultSectionDataList()) {
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
	
	
	public GridSourceProvider getGridSourceProvider() {
		return gridSources;
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
