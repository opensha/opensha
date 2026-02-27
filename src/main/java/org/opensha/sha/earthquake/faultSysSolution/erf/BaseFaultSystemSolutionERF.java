package org.opensha.sha.earthquake.faultSysSolution.erf;

import static org.opensha.sha.earthquake.param.IncludeBackgroundOption.EXCLUDE;
import static org.opensha.sha.earthquake.param.IncludeBackgroundOption.ONLY;

import java.io.File;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import javax.swing.event.ChangeEvent;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.FileParameter;
import org.opensha.sha.calc.disaggregation.DisaggregationSourceRuptureInfo;
import org.opensha.sha.earthquake.AbstractNthRupERF;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.aftershocks.MagnitudeDependentAftershockFilter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.ProxyFaultSectionInstances;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.util.SolutionDisaggConsolidator;
import org.opensha.sha.earthquake.param.AseismicityAreaReductionParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.FaultGridSpacingParam;
import org.opensha.sha.earthquake.param.GriddedSeismicitySettingsParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.UseProxySectionsParam;
import org.opensha.sha.earthquake.param.UseRupMFDsParam;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.earthquake.util.GridCellSupersamplingSettings;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * Basic fault system solution ERF without any time dependence or model-specific features
 */
public class BaseFaultSystemSolutionERF extends AbstractNthRupERF {

	private static final long serialVersionUID = 1L;

	protected static final boolean D = false;
	
	public static final String NAME = "Fault System Solution ERF";
	private String name = NAME;
	
	// Adjustable parameters
	public static final String FILE_PARAM_NAME = "Solution Input File";
	protected FileParameter fileParam;
	protected boolean includeFileParam = true;
	protected FaultGridSpacingParam faultGridSpacingParam;
	protected IncludeBackgroundParam bgIncludeParam;
	protected BackgroundRupParam bgRupTypeParam;
	protected GriddedSeismicitySettingsParam bgSettingsParam;
	protected AseismicityAreaReductionParam aseisParam;
	protected UseRupMFDsParam useRupMFDsParam;
	protected UseProxySectionsParam useProxyRupturesParam;
	
	// default parameter values
	public static final double FAULT_GRID_SPACING_DEFAULT = 1d;
	public static final IncludeBackgroundOption INCLUDE_BG_DEFAULT = IncludeBackgroundOption.INCLUDE;
	public static final BackgroundRupType BG_RUP_TYPE_DEFAULT = BackgroundRupType.POINT;
	public static final PointSourceDistanceCorrections DIST_CORR_TYPE_DEFAULT = PointSourceDistanceCorrections.NSHM_2013;
	public static final GridCellSupersamplingSettings GRID_SUPERSAMPLE_DEFAULT = null;
	public static final GriddedSeismicitySettings GRID_SETTINGS_DEFAULT = GriddedSeismicitySettings.DEFAULT
			.forSurfaceType(BG_RUP_TYPE_DEFAULT)
			.forDistanceCorrections(DIST_CORR_TYPE_DEFAULT)
			.forSupersamplingSettings(GRID_SUPERSAMPLE_DEFAULT);
	public static final boolean ASEIS_REDUCES_AREA_DEAFULT = true;
	public static final boolean USE_RUP_MFDS_DEAFULT = true;
	public static final boolean USE_PROXY_RUPS_DEAFULT = true;
	
	// The primitive versions of parameters; and values here are the param defaults: (none for fileParam)
	protected double faultGridSpacing = FAULT_GRID_SPACING_DEFAULT;
	protected IncludeBackgroundOption bgInclude = INCLUDE_BG_DEFAULT;
	protected GriddedSeismicitySettings bgSettings = GRID_SETTINGS_DEFAULT;
	protected boolean aseisReducesArea = ASEIS_REDUCES_AREA_DEAFULT;
	protected boolean useRupMFDs = USE_RUP_MFDS_DEAFULT;
	protected boolean useProxyRuptures = USE_PROXY_RUPS_DEAFULT;
	
	// Parameter change flags:
	protected boolean fileParamChanged=false;	// set as false since most subclasses ignore this parameter
	protected boolean faultGridSpacingChanged=true;
	protected boolean bgIncludeChanged=true;
	protected boolean bgRupSettingsChanged=true;
	protected boolean quadSurfacesChanged=true;
	protected boolean aseisReducesAreaChanged=true;
	protected boolean useProxyRupturesChanged=true;
	protected boolean useRupMFDsChanged=true;
	
	// TimeSpan stuff:
	protected final static double DURATION_DEFAULT = 30;	// years
	protected final static double DURATION_MIN = 0.0001;
	public final static double DURATION_MAX = 1000000;
	protected boolean timeSpanChangeFlag=true;	// this keeps track of time span changes
	
	// solution and constants
	protected FaultSystemSolution faultSysSolution;		// the FFS for the ERF
	protected Optional<RupMFDsModule> mfdsModuleOptional; // rupture MFDs (if available); null until first load is tried
	protected Optional<ProxyFaultSectionInstances> proxySectsModuleOptional;					// proxy sects (if available); null until first load is tried
	protected boolean cacheGridSources = false;			// if true, grid sources are cached instead of built on the fly
	protected ProbEqkSource[] gridSourceCache = null;
	protected int numNonZeroFaultSystemSources;			// this is the number of faultSystemRups with non-zero rates (each is a source here)
	protected int totNumRupsFromFaultSystem;						// the sum of all nth ruptures that come from fault system sources (and not equal to faultSysSolution.getNumRuptures())

	protected int numOtherSources=0; 					// the non fault system sources
	protected int[] fltSysRupIndexForSource;  			// used to keep only inv rups with non-zero rates
	protected int[] srcIndexForFltSysRup;				// this stores the src index for the fault system source (-1 if there is no mapping)
	protected int[] fltSysRupIndexForNthRup;			// the fault system rupture index for the nth rup
	protected double[] longTermRateOfFltSysRupInERF;	// this holds the long-term rate of FSS rups as used by this ERF
	
	// these help keep track of what's changed
	protected boolean faultSysSolutionChanged = true;	
	
	protected List<ProbEqkSource> faultSourceList;
	
	public BaseFaultSystemSolutionERF() {
		this(true);
	}
	
	protected BaseFaultSystemSolutionERF(boolean doInit) {
		if (doInit) {
			initParams();
			initTimeSpan(); // must be done after the above because this depends on probModelParam
		}
	}

	@Override
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		Preconditions.checkArgument(!StringUtils.isBlank(name), "Name cannot be empty");
		this.name = name;
	}
	
	protected void initParams() {
//		System.out.println("initParams()");
		fileParam = new FileParameter(FILE_PARAM_NAME);
		faultGridSpacingParam = new FaultGridSpacingParam();
		bgIncludeParam = new IncludeBackgroundParam();
		bgRupTypeParam = new BackgroundRupParam();
		bgRupTypeParam.setValue(bgSettings.surfaceType); // needs to be here, checked immediately below for consistency
		bgSettingsParam = new GriddedSeismicitySettingsParam(bgSettings, bgRupTypeParam);
		aseisParam = new AseismicityAreaReductionParam();
		useRupMFDsParam = new UseRupMFDsParam(useRupMFDs);
		useProxyRupturesParam = new UseProxySectionsParam(useProxyRuptures);
		
		// set parameters to the primitive values
		// do so before adding listeners because the change events will trigger createParamList()
		// don't do anything here for fileParam 
		faultGridSpacingParam.setValue(faultGridSpacing);
		bgIncludeParam.setValue(bgInclude);
		bgSettingsParam.setValue(bgSettings);
		aseisParam.setValue(aseisReducesArea);
		useRupMFDsParam.setValue(useRupMFDs);
		useProxyRupturesParam.setValue(useProxyRuptures);

		// set listeners
		fileParam.addParameterChangeListener(this);
		faultGridSpacingParam.addParameterChangeListener(this);
		bgIncludeParam.addParameterChangeListener(this);
		bgRupTypeParam.addParameterChangeListener(this);
		bgSettingsParam.addParameterChangeListener(this);
		aseisParam.addParameterChangeListener(this);
		useRupMFDsParam.addParameterChangeListener(this);
		useProxyRupturesParam.addParameterChangeListener(this);

		createParamList();
	}
	
	/**
	 * Put parameters in theParameterList
	 */
	protected final void createParamList() {
		try {
			// we want to pause change events so that they're only fired once at the end (and then, only if needed
			if (adjustableParams == null) {
				adjustableParams = new ParameterList();
				adjustableParams.pauseChangeEvents();
			} else {
				adjustableParams.pauseChangeEvents();
				adjustableParams.clear();
			}
			if(includeFileParam)
				adjustableParams.addParameter(fileParam);
			adjustableParams.addParameter(bgIncludeParam);
			if (!bgIncludeParam.getValue().equals(IncludeBackgroundOption.EXCLUDE)) {
				adjustableParams.addParameter(bgRupTypeParam);
				adjustableParams.addParameter(bgSettingsParam);
			}
			adjustableParams.addParameter(faultGridSpacingParam);
			adjustableParams.addParameter(aseisParam);
			if (faultSysSolution != null) {
				if (faultSysSolution.hasAvailableModule(RupMFDsModule.class)) {
					adjustableParams.addParameter(useRupMFDsParam);
					useRupMFDs = useRupMFDsParam.getValue();
				} else {
					// parameter not showing, disable it here without setting the value in the parameter; if we change solutions
					// and the new solution has it, we will revert to the parameter value.
					useRupMFDs = false;
				}
				if (faultSysSolution.getRupSet().hasAvailableModule(ProxyFaultSectionInstances.class)) {
					adjustableParams.addParameter(useProxyRupturesParam);
					useProxyRuptures = useProxyRupturesParam.getValue();
				} else {
					// parameter not showing, disable it here without setting the value in the parameter; if we change solutions
					// and the new solution has it, we will revert to the parameter value.
					useProxyRuptures = false;
				}
			} else {
				// parameters not showing, disable them here without setting the value in the parameter; if we change solutions
				// and the new solution has them, we will revert to the parameter value.
				useRupMFDs = false;
				useProxyRuptures = false;
			}
			postCreateParamListHook();
		} finally {
			adjustableParams.resumeChangeEvents();
		}
		
	}
	
	/**
	 * Called at the end of {@link #createParamList()} but before {@link ChangeEvent}'s are resumed, for subclasses
	 * to add or modify the contents of the parameter list as needed.
	 */
	protected void postCreateParamListHook() {
		
	}
	
	/**
	 * This initiates the timeSpan.
	 */
	protected void initTimeSpan() {
		timeSpan = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
		timeSpan.setDuration(DURATION_DEFAULT);
		timeSpan.addParameterChangeListener(this);
	}
	
	/**
	 * This returns the number of fault system sources
	 * (that have non-zero rates)
	 * @return
	 */
	public int getNumFaultSystemSources(){
		return numNonZeroFaultSystemSources;
	}
	
	@Override
	public void parameterChange(ParameterChangeEvent event) {
		String paramName = event.getParameterName();
		if(paramName.equalsIgnoreCase(fileParam.getName())) {
			fileParamChanged=true;
			// read it in immediately as it may change the visible parameters
			File solFile = fileParam.getValue();
			if (solFile == null) {
				setSolution(null, false);
			} else {
				readFaultSysSolutionFromFile();
			}
			fileParamChanged = false;
		} else if(paramName.equalsIgnoreCase(faultGridSpacingParam.getName())) {
			faultGridSpacing = faultGridSpacingParam.getValue();
			faultGridSpacingChanged=true;
		} else if (paramName.equalsIgnoreCase(bgIncludeParam.getName())) {
			bgInclude = bgIncludeParam.getValue();
			createParamList();
			bgIncludeChanged = true;
			if (bgInclude != EXCLUDE && numOtherSources == 0)
				bgRupSettingsChanged = true;
		} else if (paramName.equalsIgnoreCase(bgRupTypeParam.getName())) {
			// this will propagate to the gridded seismicity settings automatically
			createParamList();
			bgRupSettingsChanged = true;
			clearCachedGridSources();
		} else if (paramName.equalsIgnoreCase(bgSettingsParam.getName())) {
			bgSettings = bgSettingsParam.getValue();
			System.out.println("FSS ERF updated grid seis settings: "+bgSettings);
			bgRupSettingsChanged = true;
			clearCachedGridSources();
		} else if (paramName.equalsIgnoreCase(aseisParam.getName())) {
			aseisReducesArea = aseisParam.getValue();
			aseisReducesAreaChanged = true;
		} else if (paramName.equalsIgnoreCase(useRupMFDsParam.getName())) {
			useRupMFDs = useRupMFDsParam.getValue();
			useRupMFDsChanged = true;
		} else if (paramName.equalsIgnoreCase(useProxyRupturesParam.getName())) {
			useProxyRuptures = useProxyRupturesParam.getValue();
			useProxyRupturesChanged = true;
		} else {
			throw new RuntimeException("parameter name not recognized");
		}
		
	}
	
	protected boolean shouldRebuildFaultSystemSources() {
		return faultSysSolutionChanged || faultGridSpacingChanged || quadSurfacesChanged
				|| timeSpanChangeFlag || aseisReducesAreaChanged || useProxyRupturesChanged
				|| useRupMFDsChanged;
	}
	
	/**
	 * Can be overridden to update data needed for building other (gridded) sources
	 */
	protected void updateHookBeforeOtherBuild() {
		// do nothing (can be overridden)
	}
	
	/**
	 * Can be overridden to update data needed for building fault system sources
	 */
	protected void updateHookBeforeFaultSourceBuild() {
		// do nothing (can be overridden)
	}
	
	@Override
	public void updateForecast() throws NullPointerException {
		
		if (D) System.out.println("Updating forecast");
		long runTime = System.currentTimeMillis();

		// read FSS solution from file if specified;
		// this sets faultSysSolutionChanged and bgRupTypeChanged (since this is obtained from the FSS) as true
		if (fileParamChanged) {
			readFaultSysSolutionFromFile();	// this will not re-read the file if the name has not changed
		}
		
		if (faultSysSolution == null) {
			if (D) System.out.println("Failed to update forecast, faultSysSolution == null.");
			throw new NullPointerException(
					"Failed to update forecast. Fault system solution is unavailable. "
					+ "Ensure the ERF is provided prior to computation.");
		}
		
		// update other sources if needed
		boolean numOtherRupsChanged=false;	// this is needed below
		if (bgIncludeChanged || bgRupSettingsChanged || timeSpanChangeFlag) {
			updateHookBeforeOtherBuild();
			numOtherRupsChanged = initOtherSources();	// these are created even if not used; this sets numOtherSources
			gridSourceCache = null;
		}
		
		// update following FSS-related arrays if needed: longTermRateOfFltSysRupInERF[], srcIndexForFltSysRup[], fltSysRupIndexForSource[], numNonZeroFaultSystemSources
		boolean numFaultRupsChanged = false;	// needed below as well
		if (faultSysSolutionChanged) {	
			makeMiscFSS_Arrays(); 
			numFaultRupsChanged = true;	// not necessarily true, but a safe assumption
		}

		// now make the list of fault-system sources if any of the following have changed
		if (shouldRebuildFaultSystemSources()) {
			updateHookBeforeFaultSourceBuild();
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
		aseisReducesAreaChanged = false;
		useRupMFDsChanged = false;
		useProxyRupturesChanged = false;
		bgIncludeChanged = false;
		bgRupSettingsChanged = false;			
		quadSurfacesChanged= false;
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
	
	protected boolean isRuptureIncluded(int fltSystRupIndex) {
		return true;
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
//			System.out.println("   aleatoryMagAreaStdDev = "+aleatoryMagAreaStdDev);
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
//			System.out.println("rate="+faultSysSolution.getRateForRup(r));
			if(faultSysSolution.getRateForRup(r) > 0.0 && isRuptureIncluded(r)) {
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
	
	protected void readFaultSysSolutionFromFile() {
		// set input file
		File file = fileParam.getValue();
		if (file == null) throw new RuntimeException("No solution file specified");

		if (D) System.out.println("Loading solution from: "+file.getAbsolutePath());
		long runTime = System.currentTimeMillis();
		try {
			setSolution(FaultSystemSolution.load(file), false);
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
		// clear out any cached values
		mfdsModuleOptional = null;
		proxySectsModuleOptional = null;
		gridSourceCache = null;
		
		// set flags
		faultSysSolutionChanged = true;
		bgIncludeChanged = true;
		bgRupSettingsChanged = true;  // because the background ruptures come from the FSS
		// have to set fileParamChanged to false in case you set the file param and then call
		// setSolution manually before doing an update forecast
		fileParamChanged = false;
		
		// rebuild the parameter list
		createParamList();
	}
	
	public FaultSystemSolution getSolution() {
		return faultSysSolution;
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
	
	protected MagnitudeDependentAftershockFilter getGridSourceAftershockFilter() {
		return null;
	}
	
//	/**
//	 * This returns a source that includes only the subseismo component
//	 * for the grid cell.  This returns null is the iSource is fault based,
//	 * or if the grid cell does not have any subseismo component.
//	 * @param iSource
//	 * @return
//	 */
//	public ProbEqkSource getSourceSubSeisOnly(int iSource) {
//		GridSourceProvider gridSources = faultSysSolution.getGridSourceProvider();
//		
//		if (bgInclude.equals(ONLY)) {
//			if (gridSources == null)
//				return null;
//			else
//				return gridSources.getSourceSubSeisOnFault(iSource, timeSpan.getDuration(),
//						getGridSourceAftershockFilter(), bgRupType);
//		} else if(bgInclude.equals(EXCLUDE)) {
//			return null;
//		} else if (iSource < numNonZeroFaultSystemSources) {
//			return null;
//		} else {
//			if (gridSources == null)
//				return null;
//			else
//				return gridSources.getSourceSubSeisOnFault(iSource - numNonZeroFaultSystemSources, timeSpan.getDuration(),
//						getGridSourceAftershockFilter(), bgRupType);
//		}
//	}
//	
//	
//	/**
//	 * This returns a source that includes only the truly off fault component
//	 * for the grid cell.  This returns null is the iSource is fault based,
//	 * or if the grid cell does not have any truly off fault component.
//	 * @param iSource
//	 * @return
//	 */
//	public ProbEqkSource getSourceTrulyOffOnly(int iSource) {
//		GridSourceProvider gridSources = faultSysSolution.getGridSourceProvider();
//		
//		if (bgInclude.equals(ONLY)) {
//			if (gridSources == null)
//				return null;
//			else
//				return gridSources.getSourceUnassociated(iSource, timeSpan.getDuration(),
//						getGridSourceAftershockFilter(), bgRupType);
//		} else if(bgInclude.equals(EXCLUDE)) {
//			return null;
//		} else if (iSource < numNonZeroFaultSystemSources) {
//			return null;
//		} else {
//			if (gridSources == null)
//				return null;
//			else
//				return gridSources.getSourceUnassociated(iSource - numNonZeroFaultSystemSources, timeSpan.getDuration(),
//						getGridSourceAftershockFilter(), bgRupType);
//		}
//	}
	
	/**
	 * This makes all the fault-system sources and put them into faultSourceList
	 */
	private void makeAllFaultSystemSources() {
		faultSourceList = new ArrayList<>(numNonZeroFaultSystemSources);
		for (int i=0; i<numNonZeroFaultSystemSources; i++) {
			faultSourceList.add(makeFaultSystemSource(i));
		}
	}
	
	/**
	 * Returns a magnitude-frequency distribution for this rupture (e.g., for alternative magnitudes or aleatory
	 * variability), or null if only the mean magnitude should be used. Rates should be annualized and not include
	 * any time-dependence.
	 * 
	 * Default implementation checks for a rupture MFDs module
	 * 
	 * @param fltSystRupIndex
	 * @return
	 */
	protected DiscretizedFunc getFaultSysRupMFD(int fltSystRupIndex) {
		if (!useRupMFDs)
			// not using rupture MFDs
			return null;
		if (mfdsModuleOptional == null) {
			synchronized (this) {
				if (mfdsModuleOptional == null) {
					mfdsModuleOptional = faultSysSolution.getOptionalModule(RupMFDsModule.class);
				}
			}
		}
		if (mfdsModuleOptional.isEmpty())
			// don't have rupture MFDs
			return null;
		RupMFDsModule rupMFDs = mfdsModuleOptional.get();
		if (rupMFDs.getParent() != faultSysSolution)
			// this will do a bounds check
			rupMFDs.setParent(faultSysSolution);
		DiscretizedFunc rupMFD = rupMFDs.getRuptureMFD(fltSystRupIndex);	// this exists for multi-branch mean solutions
		if (rupMFD == null || rupMFD.size() < 2)
			// no MFD for this rupture, or it only has 1 value
			return null;
		return rupMFD;
	}
	
	/**
	 * This returns the rate gain for the given fault system rupture index, e.g. due to time-dependence or aftershock
	 * corrections. Default implementation returns 1 (any time-dependence is handled by subclasses)
	 * 
	 * @param fltSystRupIndex
	 * @return rate gain
	 */
	protected double getFaultSysRupRateGain(int fltSystRupIndex) {
		return 1d;
	}
	
	/**
	 * @param fltSystRupIndex
	 * @return true if the fault system rupture is Poissonian, false otherwise. Default implementation returns true.
	 */
	protected boolean isFaultSysRupPoisson(int fltSystRupIndex) {
		return true;
	}
	
	protected String getFaultSysSourceName(int fltSystRupIndex) {
		// make and set the name
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
		List<Integer> rupSects = rupSet.getSectionsIndicesForRup(fltSystRupIndex);
		FaultSection firstSect = rupSet.getFaultSectionData(rupSects.get(0));
		FaultSection lastSect = rupSet.getFaultSectionData(rupSects.get(rupSects.size()-1));
		return "Inversion Src #"+fltSystRupIndex+"; "+rupSects.size()+" SECTIONS BETWEEN "+firstSect.getName()+" AND "+lastSect.getName();
	}
	
	/**
	 * Determines if the given rupture rate gain is valid. Default implementation ensures that the gain is >0; if rate
	 * gains of 0 are expected in a particular subclass (e.g., ETAS right after a rupture occurred), override this.
	 * 
	 * @param rateGain
	 * @param fltSystRupIndex
	 * @param duration
	 * @return
	 */
	protected boolean isRateGainValid(double rateGain, int fltSystRupIndex, double duration) {
		return rateGain > 0d;
	}
	
	/**
	 * Creates a fault source.
	 * @param iSource - source index in ERF
	 * @return
	 */
	protected ProbEqkSource makeFaultSystemSource(int iSource) {
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
		int fltSystRupIndex = fltSysRupIndexForSource[iSource];
		
		double meanMag = rupSet.getMagForRup(fltSystRupIndex);	// this is the average if there are more than one mags
		
		DiscretizedFunc rupMFD = getFaultSysRupMFD(fltSystRupIndex);
		
		double duration = timeSpan.getDuration();
		
		double rateGain = getFaultSysRupRateGain(fltSystRupIndex);
		boolean isPoisson = isFaultSysRupPoisson(fltSystRupIndex);
		
		Preconditions.checkState(isRateGainValid(rateGain, fltSystRupIndex, duration),
				"Bad probGain=%s for rupIndex=%s, duration=%s", rateGain, fltSystRupIndex, duration);
		
		boolean proxyRups = false;
		ProxyFaultSectionInstances proxySectsModule = null;
		if (useProxyRuptures) {
			if (proxySectsModuleOptional == null) {
				synchronized (this) {
					if (proxySectsModuleOptional == null) {
						// haven't yet checked to see if we have them, do that now
						proxySectsModuleOptional = faultSysSolution.getRupSet().getOptionalModule(ProxyFaultSectionInstances.class);
					}
				}
			}
			if (proxySectsModuleOptional.isPresent()) {
				proxySectsModule = proxySectsModuleOptional.get();
				proxyRups = proxySectsModule.rupHasProxies(fltSystRupIndex);
			}
		}
		
		double rake = rupSet.getAveRakeForRup(fltSystRupIndex);
		
		ProbEqkSource src;
		if (rupMFD == null || rupMFD.size() < 2) {
			// simple case, single rupture
			
			double annualRate = rateGain*longTermRateOfFltSysRupInERF[fltSystRupIndex];
			
			if (proxyRups) {
				List<? extends FaultSection> origSects = rupSet.getFaultSectionDataForRupture(fltSystRupIndex);
				List<List<FaultSection>> proxyRupSects = proxySectsModule.getRupProxySects(fltSystRupIndex);
				src = new ProxyRupsFaultRuptureSource(origSects, proxyRupSects,
						meanMag, annualRate, rake, duration, isPoisson, faultGridSpacing, aseisReducesArea);
			} else {
				double prob;
				if (isPoisson)
					prob = 1d-Math.exp(-annualRate*duration);
				else
					// cannot exceed 1
					prob = Math.min(1d, annualRate*duration);
				src = new FaultRuptureSource(meanMag, 
						rupSet.getSurfaceForRupture(fltSystRupIndex, faultGridSpacing, aseisReducesArea), 
						rake, prob, isPoisson);
			}
		} else {
			// we have multiple magnitudes
			DiscretizedFunc rupMFDcorrected = rupMFD;
			if (rateGain != 1d) {
				rupMFDcorrected = rupMFD.deepClone();
				rupMFDcorrected.scale(rateGain);
			}
			
			if (proxyRups) {
				List<? extends FaultSection> origSects = rupSet.getFaultSectionDataForRupture(fltSystRupIndex);
				List<List<FaultSection>> proxyRupSects = proxySectsModule.getRupProxySects(fltSystRupIndex);
				src = new ProxyRupsFaultRuptureSource(origSects, proxyRupSects,
						rupMFDcorrected, rake, duration, isPoisson, faultGridSpacing, aseisReducesArea);
			} else {
				src = new FaultRuptureSource(rupMFDcorrected, 
						rupSet.getSurfaceForRupture(fltSystRupIndex, faultGridSpacing, aseisReducesArea),
						rake, duration, isPoisson);
			}
		}
		
		// set the name
		if (src instanceof FaultRuptureSource)
			((FaultRuptureSource)src).setName(getFaultSysSourceName(fltSystRupIndex));
		else if (src instanceof ProxyRupsFaultRuptureSource)
			((ProxyRupsFaultRuptureSource)src).setName("Proxy "+getFaultSysSourceName(fltSystRupIndex));
		
		RupSetTectonicRegimes tectonics = rupSet.getModule(RupSetTectonicRegimes.class);
		if (tectonics != null) {
			// set the tectonic regime
			TectonicRegionType regime = tectonics.get(fltSystRupIndex);
			if (regime != null)
				src.setTectonicRegionType(regime);
		}
		return src;
	}
	
	/**
	 * This provides a mechanism for adding other sources in subclasses
	 * @param iSource - note that this index is relative to the other sources list (numFaultSystemSources has already been subtracted out)
	 * @return
	 */
	protected ProbEqkSource getOtherSource(int iSource) {
		Preconditions.checkNotNull(faultSysSolution, "Fault system solution is null");
		GridSourceProvider gridSources = faultSysSolution.getGridSourceProvider();
		if (gridSources == null)
			return null;
		
		if (cacheGridSources) {
			if (gridSourceCache == null) {
				// need to create it, do so in a synchronized block
				synchronized (this) {
					if (gridSourceCache == null)
						gridSourceCache = new ProbEqkSource[numOtherSources];
				}
			}
			ProbEqkSource cached = gridSourceCache[iSource];
			if (cached == null) {
				// it's not cached
				cached = gridSources.getSource(iSource, timeSpan.getDuration(),
						getGridSourceAftershockFilter(), bgSettings);
				gridSourceCache[iSource] = cached;
			}
			return cached;
		}
		return gridSources.getSource(iSource, timeSpan.getDuration(),
				getGridSourceAftershockFilter(), bgSettings);
	}
	
	public void setGriddedSeismicitySettings(GriddedSeismicitySettings settings) {
		this.bgSettingsParam.setValue(settings);
	}
	
	public GriddedSeismicitySettings getGriddedSeismicitySettings() {
		return bgSettings;
	}
	
	/**
	 * This enables caching of grid sources which is often faster when re-using an ERF, but can be more memory intensive.
	 * 
	 * The cache will be cleared if an internal grid-source related parameter is changed, but you can also call
	 * {@link #clearCachedGridSources()} if you change something externally and need to clear the cache.
	 * @param cacheGridSources
	 */
	public void setCacheGridSources(boolean cacheGridSources) {
		this.cacheGridSources = cacheGridSources;
		if (!cacheGridSources)
			gridSourceCache = null;
	}
	
	/**
	 * Clears the grid sources cache (if enabled). See {@link #setCacheGridSources(boolean)}.
	 */
	public void clearCachedGridSources() {
		gridSourceCache = null;
	}
	
	/**
	 * Any subclasses that wants to include other (gridded) sources can override
	 * this method (and the getOtherSource() method), and make sure you return true if the
	 * number of ruptures changes.
	 */
	protected boolean initOtherSources() {
		if (bgIncludeChanged && bgInclude == EXCLUDE) {
			// we don't need to erase previously generated ones, but don't bother calling
			// getGridSourceProvider() below if we're not going to use them
			return true;
		}
		if(bgRupSettingsChanged || bgIncludeChanged) {
			int prevOther = numOtherSources;
			System.out.println("faultSysSolution: " + faultSysSolution);
			GridSourceProvider gridSources = faultSysSolution.getGridSourceProvider();
			if (gridSources == null) {
				numOtherSources = 0;
				// return true only if we used to have grid sources but now don't
				return prevOther > 0;
			}
			numOtherSources = gridSources.getNumSources();
			return true;
		} else {
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
		int numSources = getNumSources();
		ArrayList<Integer> tempSrcIndexForNthRup = new ArrayList<Integer>(numSources);
		ArrayList<Integer> tempRupIndexForNthRup = new ArrayList<Integer>(numSources);
		ArrayList<Integer> tempFltSysRupIndexForNthRup = new ArrayList<Integer>(numSources);
		int n=0;
		
		for(int s=0; s<numSources; s++) {	// this includes gridded sources
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
	
	public GridSourceProvider getGridSourceProvider() {
		return faultSysSolution.getGridSourceProvider();
	}

	private SolutionDisaggConsolidator disaggSourceConsolidator;
	
	@Override
	public UnaryOperator<List<DisaggregationSourceRuptureInfo>> getDisaggSourceConsolidator() {
		if (disaggSourceConsolidator == null)
			disaggSourceConsolidator = new SolutionDisaggConsolidator(this);
		return disaggSourceConsolidator;
	}
	
}
