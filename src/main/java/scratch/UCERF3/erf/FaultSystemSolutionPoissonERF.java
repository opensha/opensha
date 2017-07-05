package scratch.UCERF3.erf;

import static org.opensha.sha.earthquake.param.IncludeBackgroundOption.EXCLUDE;
import static org.opensha.sha.earthquake.param.IncludeBackgroundOption.ONLY;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.FileParameter;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.FaultGridSpacingParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.magdist.GaussianMagFreqDist;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.utils.FaultSystemIO;

import com.google.common.collect.Lists;

/**
 * This class creates a Poisson ERF from a given FaultSystemSolution (FSS).  Each "rupture" in the FaultSystemSolution
 * is treated as a separate source (each of which will have more than one rupture only if the 
 * AleatoryMagAreaStdDevParam has a non-zero value or multiple branches are represented ; e.g., MeanUCERF3).
 * 
 * The fault system solution can be provided in the constructor (as an object or file name) or the file 
 * can be set in the file parameter.
 * 
 * This class make use of multiple mags for a given FSS rupture if they exist (e.g., from more than one logic tree
 * branch), but the mean is used if aleatoryMagAreaStdDev !=0.
 * 
 * This filters out fault system ruptures that have zero rates, or have a magnitude below the section minimum
 * (as determined by InversionFaultSystemRupSet.isRuptureBelowSectMinMag(r)).
 * 
 * To make accessing ruptures less confusing, this class keeps track of "nth" ruptures within the ERF 
 * (see the last 7 methods here); these methods could be added to AbstractERF.
 * 
 * Note that all sources are created regardless of the value of IncludeBackgroundParam
 * 
 * Subclasses can add other (non-fault system) sources by simply overriding and implementing:
 * 
 *  	initOtherSources()
 *  	getOtherSource(int)
 *  
 * the first must set the numOtherSources variable (which can't change with adjustable parameters???) and must return 
 * whether the number of ruptures has changed.  The getOtherSource(int) method must take into account any changes in 
 * the timespan duration (e.g., by making sources on the fly).
 * 
 * TODO The list of adjustable parameters is not dynamic (e.g., hiding those that aren't relevant 
 * based on other param settings); there was some memory leak with the way it was being handled previously.
 * 
 * 
 */
@Deprecated
public class FaultSystemSolutionPoissonERF extends AbstractERF {
	
	private static final long serialVersionUID = 1L;
	
	private static final boolean D = true;

	public static final String NAME = "Fault System Solution Poisson ERF";
	
	// Adjustable parameters
	public static final String FILE_PARAM_NAME = "Solution Input File";
	protected FileParameter fileParam;
	protected FaultGridSpacingParam faultGridSpacingParam;
	protected AleatoryMagAreaStdDevParam aleatoryMagAreaStdDevParam;
	protected ApplyGardnerKnopoffAftershockFilterParam applyAftershockFilterParam;
	protected IncludeBackgroundParam bgIncludeParam;
	protected BackgroundRupParam bgRupTypeParam;
	static final String QUAD_SURFACES_PARAM_NAME = "Use Quad Surfaces (otherwise gridded)";
	private static final boolean QUAD_SURFACES_PARAM_DEFAULT = false;
	private BooleanParameter quadSurfacesParam;
	
	// The primitive versions of parameters; and values here are the param defaults: (none for fileParam)
	protected double faultGridSpacing = 1.0;
	double aleatoryMagAreaStdDev = 0.0;
	protected boolean applyAftershockFilter = false;
	protected IncludeBackgroundOption bgInclude = IncludeBackgroundOption.EXCLUDE;
	protected BackgroundRupType bgRupType = BackgroundRupType.POINT;
	private boolean quadSurfaces = false;

	// Parameter change flags: (none for bgIncludeParam) 
	protected boolean fileParamChanged=false;	// set as false since most subclasses ignore this parameter
	protected boolean faultGridSpacingChanged=true;
	protected boolean aleatoryMagAreaStdDevChanged=true;
	protected boolean applyAftershockFilterChanged=true;
	protected boolean bgRupTypeChanged=true;
	protected boolean quadSurfacesChanged=true;

	// moment-rate reduction to remove aftershocks from supra-seis ruptures
	final public static double MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS = 0.97;	// 3%

	// this keeps track of time span changes
	boolean timeSpanChangeFlag=true;
	
	// these help keep track of what's changed
	private boolean faultSysSolutionChanged = true;
	
	// leave as a FaultSystemSolution for use with Simulator/other FSS
	protected FaultSystemSolution faultSysSolution;		// the FFS for the ERF
	protected int numNonZeroFaultSystemSources;			// this is the number of faultSystemRups with non-zero rates (each is a source here)
	int totNumRupsFromFaultSystem;						// the sum of all nth ruptures that come from fault system sources (and not equal to faultSysSolution.getNumRuptures())
	
	protected int numOtherSources=0; 					// the non fault system sources
	protected int[] fltSysRupIndexForSource;  			// used to keep only inv rups with non-zero rates
	protected int[] srcIndexForFltSysRup;				// this stores the src index for the fault system source (-1 if there is no mapping)
	protected int[] fltSysRupIndexForNthRup;			// the fault system rupture index for the nth rup
	protected ArrayList<int[]> nthRupIndicesForSource;	// this gives the nth indices for a given source
	
	// THESE COULD BE ADDED TO ABSRACT ERF:
	protected int totNumRups;
	protected int[] srcIndexForNthRup;
	protected int[] rupIndexForNthRup;
	
	
	protected List<FaultRuptureSource> faultSourceList;
	
	/**
	 * This creates the ERF from the given FaultSystemSolution.  FileParameter is removed 
	 * from the adjustable parameter list (to prevent changes after instantiation).
	 * @param faultSysSolution
	 */
	public FaultSystemSolutionPoissonERF(FaultSystemSolution faultSysSolution) {
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
	public FaultSystemSolutionPoissonERF(String fullPathInputFile) {
		this();
		fileParam.setValue(new File(fullPathInputFile));
		// remove the fileParam from the adjustable parameter list
		adjustableParams.removeParameter(fileParam);
	}

	
	/**
	 * This creates the ERF with a parameter for setting the input file
	 * (e.g., from a GUI).
	 */
	public FaultSystemSolutionPoissonERF() {
		initParams();
		timeSpan = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
		timeSpan.setDuration(30.);
	}
	
	
	protected void initParams() {
		fileParam = new FileParameter(FILE_PARAM_NAME);
		adjustableParams.addParameter(fileParam);
		
		faultGridSpacingParam = new FaultGridSpacingParam();
		adjustableParams.addParameter(faultGridSpacingParam);
		
		aleatoryMagAreaStdDevParam = new AleatoryMagAreaStdDevParam();
		adjustableParams.addParameter(aleatoryMagAreaStdDevParam);
		
		applyAftershockFilterParam= new ApplyGardnerKnopoffAftershockFilterParam();  // default is false
		adjustableParams.addParameter(applyAftershockFilterParam);

		bgIncludeParam = new IncludeBackgroundParam();
		adjustableParams.addParameter(bgIncludeParam);

		bgRupTypeParam = new BackgroundRupParam();
		adjustableParams.addParameter(bgRupTypeParam);
		
		quadSurfacesParam = new BooleanParameter(QUAD_SURFACES_PARAM_NAME, QUAD_SURFACES_PARAM_DEFAULT);
		adjustableParams.addParameter(quadSurfacesParam);

		// set listeners
		fileParam.addParameterChangeListener(this);
		faultGridSpacingParam.addParameterChangeListener(this);
		aleatoryMagAreaStdDevParam.addParameterChangeListener(this);
		applyAftershockFilterParam.addParameterChangeListener(this);
		bgIncludeParam.addParameterChangeListener(this);
		bgRupTypeParam.addParameterChangeListener(this);
		quadSurfacesParam.addParameterChangeListener(this);
		
		// set parameters to the primitive values
		// fileParam.setValue(value); don't do anything here
		faultGridSpacingParam.setValue(faultGridSpacing);
		aleatoryMagAreaStdDevParam.setValue(aleatoryMagAreaStdDev);
		applyAftershockFilterParam.setValue(applyAftershockFilter);
		bgIncludeParam.setValue(bgInclude);
		bgRupTypeParam.setValue(bgRupType);
		quadSurfacesParam.setValue(quadSurfaces);

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
	public void updateForecast() {
		
		if (D) System.out.println("Updating forecast");
		long runTime = System.currentTimeMillis();
		
		// read FSS solution from file if specified;
		// this sets faultSysSolutionChanged and bgRupTypeChanged (since this is obtained from the FSS) as true
		if(fileParamChanged) {
			readFaultSysSolutionFromFile();	// this will not re-read the file if the name has not changed
			fileParamChanged = false;
		}
		
		boolean numOtherRupsChanged = initOtherSources();	// these are created even if not used; this sets numOtherSources
		bgRupTypeChanged = false;	// since the above just updated these
		
		boolean numFaultRupsChanged = false;
		if (faultSysSolutionChanged || aleatoryMagAreaStdDevChanged || applyAftershockFilterChanged 
				|| faultGridSpacingChanged || quadSurfacesChanged) {
			makeAllFaultSystemSources();	// overrides all fault-based source objects; created even if not fault sources aren't wanted
			// set that number of ruptures changed:
			numFaultRupsChanged = true;
			// reset change flags
			faultSysSolutionChanged = false;
			aleatoryMagAreaStdDevChanged = false;
			applyAftershockFilterChanged = false;
			faultGridSpacingChanged = false;
			timeSpanChangeFlag = false;
			quadSurfacesChanged= false;
		} 
		
		// if timeSpan is still marked as changed, update fault sources (grid sources don't need to be updated here because the getSource() method handles changed durations)
		if(timeSpanChangeFlag) {	// only time-span changed
			for(FaultRuptureSource src : faultSourceList)
				src.setDuration(timeSpan.getDuration());
		}
		
		if(numFaultRupsChanged || numOtherRupsChanged)
			setAllNthRupRelatedArrays();
		
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
		} else if (paramName.equalsIgnoreCase(bgRupTypeParam.getName())) {
			bgRupType = bgRupTypeParam.getValue();
			bgRupTypeChanged = true;
		} else if (paramName.equals(QUAD_SURFACES_PARAM_NAME)) {
			quadSurfaces = quadSurfacesParam.getValue();
			quadSurfacesChanged = true;
		} else {
			throw new RuntimeException("parameter name not recognized");
		}
	}

	
	
	/**
	 * This method sets a bunch of fields, arrays, and ArrayLists.
	 */
	private void makeAllFaultSystemSources() {
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
				
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
				srcIndex += 1;
			}
		}
		
		// convert list to array
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
		
		// now make the list of sources
		faultSourceList = Lists.newArrayList();
		for (int i=0; i<numNonZeroFaultSystemSources; i++) {
			faultSourceList.add(makeFaultSystemSource(i));
		}
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
	protected void setSolution(FaultSystemSolution sol) {
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
		return NAME;
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
	 * Creates a fault source.
	 * @param iSource - source index in ERF
	 * @return
	 */
	protected FaultRuptureSource makeFaultSystemSource(int iSource) {
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
		int fltSystRupIndex = fltSysRupIndexForSource[iSource];
		FaultRuptureSource src;
		
		double meanMag = rupSet.getMagForRup(fltSystRupIndex);	// this is the average if there are more than one mags
		double aftRateCorr = 1d;
		if(applyAftershockFilter) aftRateCorr = MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS; // GardnerKnopoffAftershockFilter.scaleForMagnitude(mag);
		
		if(aleatoryMagAreaStdDev == 0) {
			// TODO allow rup MFD with aleatory?
			DiscretizedFunc rupMFD = faultSysSolution.getRupMagDist(fltSystRupIndex);	// this exists for multi-branch mean solutions
			if (rupMFD == null || rupMFD.size() < 2) {
				// normal source
				boolean isPoisson = true;
				double prob = 1-Math.exp(-aftRateCorr*faultSysSolution.getRateForRup(fltSystRupIndex)*timeSpan.getDuration());
				src = new FaultRuptureSource(meanMag, 
						rupSet.getSurfaceForRupupture(fltSystRupIndex, faultGridSpacing, quadSurfaces), 
						rupSet.getAveRakeForRup(fltSystRupIndex), prob, isPoisson);
			} else {
				// we have a MFD for this rupture
				if (aftRateCorr != 1d) {
					// apply aftershock correction
					rupMFD = rupMFD.deepClone();
					rupMFD.scale(aftRateCorr);
				}
				src = new FaultRuptureSource(rupMFD, 
						rupSet.getSurfaceForRupupture(fltSystRupIndex, faultGridSpacing, quadSurfaces),
						rupSet.getAveRakeForRup(fltSystRupIndex), timeSpan.getDuration());
			}
		} else {
			// this currently only uses the mean magnitude
			double totMoRate = aftRateCorr*faultSysSolution.getRateForRup(fltSystRupIndex)*MagUtils.magToMoment(meanMag);
			GaussianMagFreqDist srcMFD = new GaussianMagFreqDist(5.05,8.65,37,meanMag,aleatoryMagAreaStdDev,totMoRate,2.0,2);
			src = new FaultRuptureSource(srcMFD, 
					rupSet.getSurfaceForRupupture(fltSystRupIndex, faultGridSpacing, quadSurfaces),
					rupSet.getAveRakeForRup(fltSystRupIndex), timeSpan.getDuration());			
		}

		List<FaultSectionPrefData> data = rupSet.getFaultSectionDataForRupture(fltSystRupIndex);
		String name = data.size()+" SECTIONS BETWEEN "+data.get(0).getName()+" AND "+data.get(data.size()-1).getName();
		src.setName("Inversion Src #"+fltSystRupIndex+"; "+name);
		return src;
	}
	
	
	/**
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
		return null;
	}
	
	/**
	 * Any subclasses that wants to include other (gridded) sources can override
	 * this method (and the getOtherSource() method), and make sure you return true if the
	 * number of ruptures changes.
	 */
	protected boolean initOtherSources() {
		numOtherSources=0;
		return false;
	}

	@Override
	public void timeSpanChange(EventObject event) {
		timeSpanChangeFlag = true;
	}
	
	
	
	/**
	 * This sets the following: totNumRups, totNumRupsFromFaultSystem, nthRupIndicesForSource,
	 * srcIndexForNthRup[], rupIndexForNthRup[], fltSysRupIndexForNthRup[]
	 * 
	 */
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
	 * This checks whether what's returned from get_nthRupIndicesForSource(s) gives
	 *  successive integer values when looped over all sources.
	 *  TODO move this to a test class
	 *  
	 */
	public void testNthRupIndicesForSource() {
		int index = 0;
		for(int s=0; s<this.getNumSources(); s++) {
			int[] test = get_nthRupIndicesForSource(s);
			for(int r=0; r< test.length;r++) {
				int nthRup = test[r];
				if(nthRup !=index)
					throw new RuntimeException("Error found");
				index += 1;
			}
		}
		System.out.println("testNthRupIndicesForSource() was successful");
	}
	
	
	/**
	 * This returns the nth rup indices for the given source
	 */
	public int[] get_nthRupIndicesForSource(int iSource) {
		return nthRupIndicesForSource.get(iSource);
	}
	
	/**
	 * This returns the total number of ruptures (the sum of all ruptures in all sources)
	 */
	public int getTotNumRups() {
		return totNumRups;
	}
	
	/**
	 * This returns the nth rupture index for the given source and rupture index
	 * (where the latter is the rupture index within the source)
	 */	
	public int getIndexN_ForSrcAndRupIndices(int s, int r) {
		return get_nthRupIndicesForSource(s)[r];
	}
	
	/**
	 * This returns the source index for the nth rupture
	 * @param nthRup
	 * @return
	 */
	public int getSrcIndexForNthRup(int nthRup) {
		return srcIndexForNthRup[nthRup];
	}

	/**
	 * This returns the rupture index (with its source) for the
	 * given nth rupture.
	 * @param nthRup
	 * @return
	 */
	public int getRupIndexInSourceForNthRup(int nthRup) {
		return rupIndexForNthRup[nthRup];
	}
	
	/**
	 * This returns the nth rupture in the ERF
	 * @param n
	 * @return
	 */
	public ProbEqkRupture getNthRupture(int n) {
		return getRupture(getSrcIndexForNthRup(n), getRupIndexInSourceForNthRup(n));
	}
	



}
