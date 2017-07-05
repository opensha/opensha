package scratch.UCERF3.erf.ETAS;

import java.awt.Color;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.zip.ZipException;

import javax.swing.JOptionPane;

import org.dom4j.DocumentException;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.region.CaliforniaRegions.RELM_TESTING_GRIDDED;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.CPTParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.FileParameter;
import org.opensha.commons.param.impl.LocationParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractNthRupERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupOrigTimeComparator;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;
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
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.GriddedSubsetSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.analysis.FaultBasedMapGen;
import scratch.UCERF3.analysis.FaultSysSolutionERF_Calc;
import scratch.UCERF3.analysis.FaultSystemSolutionCalc;
import scratch.UCERF3.analysis.GMT_CA_Maps;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools.EpicenterMapThread;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.griddedSeismicity.GridSourceFileReader;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.UCERF3.utils.U3_EqkCatalogStatewideCompleteness;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.finiteFaultMap.FiniteFaultMappingData;

public class ETAS_Simulator {
	
	public static boolean D=true; // debug flag
	private static boolean live_map = false;
	static boolean pause_for_events = false;
	// if true and in debug mode, will exit after scenario diagnostics
	static boolean exit_after_scenario_diagnostics = false;
	
	/**
	 * This version takes the pre-computed data arrays as arguments
	 * 
	 * 
	 * @param resultsDir
	 * @param erf
	 * @param griddedRegion
	 * @param scenarioRup
	 * @param histQkList
	 * @param includeSpontEvents
	 * @param includeIndirectTriggering - include secondary, tertiary, etc events
	 * @param includeEqkRates - whether or not to include the long-term rate of events in sampling aftershocks
	 * @param gridSeisDiscr - lat lon discretization of gridded seismicity (degrees)
	 * @param simulationName
	 * @param randomSeed - set for reproducibility, or set null if new seed desired
	 * @param etasParams
	 * @throws IOException
	 */
	public static void testETAS_Simulation(File resultsDir, AbstractNthRupERF erf,
			GriddedRegion griddedRegion, ETAS_EqkRupture scenarioRup, List<? extends ObsEqkRupture> histQkList, boolean includeSpontEvents,
			boolean includeIndirectTriggering, double gridSeisDiscr, String simulationName,
			Long randomSeed, ETAS_ParameterList etasParams)
					throws IOException {
		testETAS_Simulation(resultsDir, erf, griddedRegion, scenarioRup,  histQkList, includeSpontEvents,
				includeIndirectTriggering, gridSeisDiscr, simulationName,
				randomSeed, null, null, null, etasParams);
	}
	
	/**
	 * This represents an ETAS simulation.  
	 * 
	 * This assume ER probabilities are constant up until 
	 * the next fault-system event (only works if fault system events occur every few years or
	 * less).
	 * 
	 * The IDs assigned to ETAS ruptures are as follows: the list index for events in the histQkList; histQkList.size() 
	 * for any non-null scenarioRup; and the integers that follow the scenarioRup index for simulated events in order of 
	 * creation (not occurrence)
	 * 
	 * TODO:
	 * 
	 * 0) get rid of ETAS_EqkRupture.setParentID() and the parentID field within (since pointer to parent rupture exists)
	 * 1) define tests for all code elements
	 * 2) document all the code
	 * 
	 * 
	 * @param resultsDir
	 * @param erf
	 * @param griddedRegion
	 * @param scenarioRup
	 * @param histQkList
	 * @param includeSpontEvents
	 * @param includeIndirectTriggering - include secondary, tertiary, etc events
	 * @param includeEqkRates - whether or not to include the long-term rate of events in sampling aftershocks
	 * @param gridSeisDiscr - lat lon discretization of gridded seismicity (degrees)
	 * @param simulationName
	 * @param randomSeed - set for reproducibility, or set null if new seed desired
	 * @param fractionSrcInCubeList - from pre-computed data file	TODO chance name of this
	 * @param srcInCubeListt - from pre-computed data file	TODO chance name of this
	 * @param inputIsCubeInsideFaultPolygont - from pre-computed data file
	 * @param etasParams
	 * @throws IOException
	 */
	public static void testETAS_Simulation(File resultsDir, AbstractNthRupERF erf,
			GriddedRegion griddedRegion, ETAS_EqkRupture scenarioRup, List<? extends ObsEqkRupture> histQkList, boolean includeSpontEvents,
			boolean includeIndirectTriggering, double gridSeisDiscr, String simulationName,
			Long randomSeed, List<float[]> fractionSrcInCubeList, List<int[]> srcInCubeList, int[] inputIsCubeInsideFaultPolygon, 
			ETAS_ParameterList etasParams)
					throws IOException {
		
		// Overide to Poisson if needed
		if (etasParams.getU3ETAS_ProbModel() == U3ETAS_ProbabilityModelOptions.POISSON) {
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			erf.updateForecast();
		}
		
		boolean generateDiagnostics = false;	// to be able to turn off even if in debug mode
		boolean generateDiagnosticsForScenario = true;	// to be able to turn off even if in debug mode

		// set the number or fault-based sources
		int numFaultSysSources = 0;
		FaultSystemSolutionERF fssERF = null;
		if(erf instanceof FaultSystemSolutionERF) {
			fssERF = (FaultSystemSolutionERF)erf;
			numFaultSysSources = fssERF.getNumFaultSystemSources();
		}
		
		// TODO:
		// griddedRegion could come from erf.getSolution().getGridSourceProvider().getGriddedRegion(); can the two be different?
		// gridSeisDiscr could come from erf.getSolution().getGridSourceProvider().getGriddedRegion().getLatSpacing()
		// add testScenario as method argument (can be null)
		
		// set the random seed for reproducibility
		ETAS_Utils etas_utils;
		if(randomSeed != null)
			etas_utils = new ETAS_Utils(randomSeed);
		else
			etas_utils = new ETAS_Utils(System.currentTimeMillis());
		
		// this could be input value
		SeisDepthDistribution seisDepthDistribution = new SeisDepthDistribution(etas_utils);
		
		// directory for saving results
		if(!resultsDir.exists()) resultsDir.mkdir();
		
		// set file for writing simulation info & write some preliminary stuff to it
		FileWriter info_fr = new FileWriter(new File(resultsDir, "infoString.txt"));	// TODO this is closed below; why the warning?
		FileWriter simulatedEventsFileWriter = new FileWriter(new File(resultsDir, "simulatedEvents.txt"));
		ETAS_CatalogIO.writeEventHeaderToFile(simulatedEventsFileWriter);

		info_fr.write(simulationName+"\n");
		info_fr.write("\nrandomSeed="+etas_utils.getRandomSeed()+"\n");
		if(D) System.out.println("\nrandomSeed="+etas_utils.getRandomSeed());
		if(histQkList == null)
			info_fr.write("\nhistQkList.size()=null"+"\n");
		else
			info_fr.write("\nhistQkList.size()="+histQkList.size()+"\n");
		info_fr.write("includeSpontEvents="+includeSpontEvents+"\n");
		info_fr.write("includeIndirectTriggering="+includeIndirectTriggering+"\n");
		
		info_fr.write("\nERF Adjustable Paramteres:\n\n");
		for(Parameter param : erf.getAdjustableParameterList()) {
			info_fr.write("\t"+param.getName()+" = "+param.getValue()+"\n");
		}
		TimeSpan tsp = erf.getTimeSpan();
		String startTimeString = tsp.getStartTimeMonth()+"/"+tsp.getStartTimeDay()+"/"+tsp.getStartTimeYear()+"; hr="+tsp.getStartTimeHour()+"; min="+tsp.getStartTimeMinute()+"; sec="+tsp.getStartTimeSecond();
		info_fr.write("\nERF StartTime: "+startTimeString+"\n");
		info_fr.write("\nERF TimeSpan Duration: "+erf.getTimeSpan().getDuration()+" years\n");
		
		info_fr.write("\nETAS Paramteres:\n\n");
		if(D) System.out.println("\nETAS Paramteres:\n\n");
		for(Parameter param : etasParams) {
			info_fr.write("\t"+param.getName()+" = "+param.getValue()+"\n");
			if(D) System.out.println("\t"+param.getName()+" = "+param.getValue());
		}
		
		info_fr.flush();	// this writes the above out now in case of crash
		
		// Make the list of observed ruptures, plus scenario if that was included
		ArrayList<ETAS_EqkRupture> obsEqkRuptureList = new ArrayList<ETAS_EqkRupture>();
		
		if(histQkList != null) {
			// now start at 1, zero is reserved for scenarios if included
			int id=1;
			for(ObsEqkRupture qk : histQkList) {
				Location hyp = qk.getHypocenterLocation();
				if(griddedRegion.contains(hyp) && hyp.getDepth() < 24.0) {	//TODO remove hard-coded 24.0 (get from depth dist)
					ETAS_EqkRupture etasRup;
					if (qk instanceof ETAS_EqkRupture)
						// keep original, may have FSS Index set
						etasRup = (ETAS_EqkRupture)qk;
					else
						etasRup = new ETAS_EqkRupture(qk);
					etasRup.setID(id);
					obsEqkRuptureList.add(etasRup);
					id+=1;
				}
			}
			System.out.println("histQkList.size()="+histQkList.size());
			System.out.println("obsEqkRuptureList.size()="+obsEqkRuptureList.size());
		}
		
		// add scenario rup to end of obsEqkRuptureList
		int scenarioRupID = -1;
		double numPrimaryAshockForScenario = 0;
		if(scenarioRup != null) {
//			scenarioRupID = obsEqkRuptureList.size();
			// zero is reserved for scenario rupture
			scenarioRupID = 0;
			scenarioRup.setID(scenarioRupID);
			obsEqkRuptureList.add(scenarioRup);		
			if(D) {
				System.out.println("Num locs on scenario rup surface: "+scenarioRup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface().size());
			}
		}
		
		// this will store the simulated aftershocks & spontaneous events (in order of occurrence) - ObsEqkRuptureList? (they're added in order anyway)
		ObsEqkRupOrigTimeComparator oigTimeComparator = new ObsEqkRupOrigTimeComparator();	// this will keep the event in order of origin time
		PriorityQueue<ETAS_EqkRupture>  simulatedRupsQueue = new PriorityQueue<ETAS_EqkRupture>(1000, oigTimeComparator);
		
		// this is for keeping track of aftershocks on the fault system
		ArrayList<Integer> nthFaultSysRupAftershocks = new ArrayList<Integer>();
		
		// get simulation timespan info
		long simStartTimeMillis = erf.getTimeSpan().getStartTimeCalendar().getTimeInMillis();
		long simEndTimeMillis = erf.getTimeSpan().getEndTimeCalendar().getTimeInMillis();
		double simDurationYrs = erf.getTimeSpan().getDuration();
		
		if(D) System.out.println("Updating forecast in testETAS_Simulation");
		// set to yearly probabilities for simulation forecast (in case input was not a 1-year forecast)
		erf.getTimeSpan().setDuration(1.0);	// TODO make duration expected time to next supra seis event?
		erf.updateForecast();	// do this to get annual rate over the entire forecast (used to sample spontaneous events)
		if(D) System.out.println("Done updating forecast in testETAS_Simulation");
		
		//Compute origTotRate; this calculation includes any ruptures outside region, 
		// but this is dominated by gridded seis so shouldn't matter; using 
		// ERF_Calculator.getTotalRateInRegion(erf, griddedRegion, 0.0) takes way too long.
		if(D) System.out.println("Computing origTotRate");
		long st = System.currentTimeMillis();
		double origTotRate=0;
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			ProbEqkSource src = erf.getSource(sourceID);
			for (int rupID=0; rupID<src.getNumRuptures(); rupID++) {
				ProbEqkRupture rup = src.getRupture(rupID);
				double rupRate = rup.getMeanAnnualRate(erf.getTimeSpan().getDuration());
				Preconditions.checkState(Doubles.isFinite(rupRate),
						"Non fininte rup rate: %s, prob=%s, src=%s, rup=%s, mag=%s, ptSource=%s",
						rupRate, rup.getProbability(), sourceID, rupID, rup.getMag(), rup.getRuptureSurface().isPointSurface());
				origTotRate += rupRate;
			}
		}
		if (D) System.out.println("\torigTotRate="+(float)origTotRate+"; that took (sec): "+(float)(System.currentTimeMillis()-st)/1000f);
		info_fr.write("\nExpected mean annual rate over timeSpan (per year) = "+(float)origTotRate+"\n");
		info_fr.flush();
		
		
		// TODO This loop is redundant with that above; combine
		if(D) System.out.println("Computing original spontaneousRupSampler & sourceRates[s]");
		st = System.currentTimeMillis();
		double sourceRates[] = new double[erf.getNumSources()];
		double duration = erf.getTimeSpan().getDuration();
		IntegerPDF_FunctionSampler spontaneousRupSampler = new IntegerPDF_FunctionSampler(erf.getTotNumRups());
		int nthRup=0;
		if(D) System.out.println("total number of ruptures: "+erf.getTotNumRups());
		for(int s=0;s<erf.getNumSources();s++) {
			ProbEqkSource src = erf.getSource(s);
			sourceRates[s] = src.computeTotalEquivMeanAnnualRate(duration);
			if(D && sourceRates[s]==0) {
				double rate = fssERF.getSolution().getRateForRup(fssERF.getFltSysRupIndexForSource(s));
				System.out.println("ZERO RATE FAULT SOURCE: "+rate+"\t"+src.getRupture(0).getProbability()+"\t"+src.getName());
			}
			for(ProbEqkRupture rup:src) {
				if (nthRup >= spontaneousRupSampler.size())
					throw new RuntimeException("Weird...tot num="+erf.getTotNumRups()+", nth="+nthRup);
				spontaneousRupSampler.set(nthRup, rup.getMeanAnnualRate(duration));
				nthRup+=1;
			}
		}
		if(D) System.out.println("\tspontaneousRupSampler.calcSumOfY_Vals()="+(float)spontaneousRupSampler.calcSumOfY_Vals() +
				"; that took (sec): "+(float)(System.currentTimeMillis()-st)/1000f);
		
		
		if(D) System.out.println("Making ETAS_PrimaryEventSampler");
		st = System.currentTimeMillis();
		
		// Create the ETAS_PrimaryEventSampler
		ETAS_PrimaryEventSampler etas_PrimEventSampler = new ETAS_PrimaryEventSampler(griddedRegion, erf, sourceRates,
				gridSeisDiscr,null, etasParams, etas_utils, fractionSrcInCubeList, srcInCubeList, 
				inputIsCubeInsideFaultPolygon);  // latter three may be null
		if(D) System.out.println("ETAS_PrimaryEventSampler creation took "+(float)(System.currentTimeMillis()-st)/60000f+ " min");
		info_fr.write("\nMaking ETAS_PrimaryEventSampler took "+(System.currentTimeMillis()-st)/60000+ " min");
		info_fr.flush();
		
		
		
		
		// Make list of primary aftershocks for given list of obs quakes 
		// (filling in origin time ID, parentID, and location on parent that does triggering, with the rest to be filled in later)
		if (D) System.out.println("Making primary aftershocks from input obsEqkRuptureList, size = "+obsEqkRuptureList.size());
		PriorityQueue<ETAS_EqkRupture>  eventsToProcess = new PriorityQueue<ETAS_EqkRupture>(1000, oigTimeComparator);	// not sure about the first field
//		int testParID=0;	// this will be used to test IDs
		int eventID = obsEqkRuptureList.size();	// start IDs after input events
		for(ETAS_EqkRupture parRup: obsEqkRuptureList) {
			int parID = parRup.getID();
//			if(parID != testParID) 
//				throw new RuntimeException("problem with ID");
			long rupOT = parRup.getOriginTime();
			double startDay = (double)(simStartTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;	// convert epoch to days from event origin time
			double endDay = (double)(simEndTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
			// get a list of random primary event times, in units of days since main shock
			double[] randomAftShockTimes = etas_utils.getRandomEventTimes(etasParams.get_k(), etasParams.get_p(), parRup.getMag(), ETAS_Utils.magMin_DEFAULT, etasParams.get_c(), startDay, endDay);
			if(parRup.getID() == scenarioRupID)
				numPrimaryAshockForScenario = randomAftShockTimes.length;
			LocationList locList = null;
			if(randomAftShockTimes.length>0) {
				for(int i=0; i<randomAftShockTimes.length;i++) {
					long ot = rupOT +  (long)(randomAftShockTimes[i]*(double)ProbabilityModelsCalc.MILLISEC_PER_DAY);	// convert to milliseconds
					ETAS_EqkRupture newRup = new ETAS_EqkRupture(parRup, eventID, ot);
					newRup.setParentID(parID);	// TODO don't need this if it's set from parent rup in above constructor
					newRup.setGeneration(1);	// TODO shouldn't need this either since it's 1 plus that of parent (also set in costructor)
					if(parRup.getFSSIndex()==-1)
						newRup.setParentTriggerLoc(etas_utils.getRandomLocationOnRupSurface(parRup));
					else {
						// this produces too few aftershocks on highly creeping faults:
//						Location tempLoc = etas_utils.getRandomLocationOnRupSurface(parRup);
						
						// for no creep/aseis reduction:
						if(locList==null) {	// make reusable location list
							RuptureSurface surf = etas_utils.getRuptureSurfaceWithNoCreepReduction(parRup.getFSSIndex(), fssERF, 0.05);
							locList = surf.getEvenlyDiscritizedListOfLocsOnSurface();							
						}
						Location tempLoc = locList.get(etas_utils.getRandomInt(locList.size()-1));
						
						if(tempLoc.getDepth()>etas_PrimEventSampler.maxDepth) {
							Location newLoc = new Location(tempLoc.getLatitude(),tempLoc.getLongitude(), etas_PrimEventSampler.maxDepth);
							tempLoc=newLoc;
						}

						// now add some randomness for numerical stability:
						newRup.setParentTriggerLoc(etas_PrimEventSampler.getRandomFuzzyLocation(tempLoc));
					}
					etas_PrimEventSampler.addRuptureToProcess(newRup); // for efficiency
					eventsToProcess.add(newRup);
					eventID +=1;
				}
			}
//			testParID += 1;				
		}
		if (D) System.out.println("The "+obsEqkRuptureList.size()+" input events produced "+eventsToProcess.size()+" primary aftershocks");
		info_fr.write("\nThe "+obsEqkRuptureList.size()+" input observed events produced "+eventsToProcess.size()+" primary aftershocks\n");
		info_fr.flush();

		
		// make the list of spontaneous events, filling in only event IDs and origin times for now
		if(includeSpontEvents) {
			if (D) System.out.println("Making spontaneous events and times of primary aftershocks...");
			
//			// OLD WAY
//			double fractionNonTriggered=etasParams.getFractSpont();	// one minus branching ratio TODO fix this; this is not what branching ratio is
//			double expectedNum = origTotRate*simDuration*fractionNonTriggered;
//			int numSpontEvents = etas_utils.getPoissonRandomNumber(expectedNum);
//			for(int r=0;r<numSpontEvents;r++) {
//				ETAS_EqkRupture rup = new ETAS_EqkRupture();
//				double ot = simStartTimeMillis+etas_utils.getRandomDouble()*(simEndTimeMillis-simStartTimeMillis);	// random time over time span
//				rup.setOriginTime((long)ot);
//				rup.setID(eventID);
//				rup.setGeneration(0);
//				eventsToProcess.add(rup);
//				eventID += 1;
//			}
//			String spEvStringInfo = "Spontaneous Events:\n\n\tAssumed fraction non-triggered = "+fractionNonTriggered+
//					"\n\texpectedNum="+expectedNum+"\n\tnumSampled="+numSpontEvents+"\n";
//			if(D) System.out.println(spEvStringInfo);
//			info_fr.write("\n"+spEvStringInfo);
//			info_fr.flush();
			
			
			// NEW WAY (time-dep rate of spont events)
			long histCatStartTime = simStartTimeMillis;
			long[] spontEventTimes;
			IncrementalMagFreqDist mfd = etas_PrimEventSampler.getLongTermTotalERF_MFD().deepClone();
			// Apply scale factor
			mfd.scale(etasParams.getTotalRateScaleFactor());
			if(histQkList==null)
				spontEventTimes = etas_utils.getRandomSpontanousEventTimes(mfd, histCatStartTime, simStartTimeMillis, simEndTimeMillis, 1000, 
						etasParams.get_k(), etasParams.get_p(), ETAS_Utils.magMin_DEFAULT, etasParams.get_c());
			else
				spontEventTimes = etas_utils.getRandomSpontanousEventTimes(
						mfd, U3_EqkCatalogStatewideCompleteness.load().getEvenlyDiscretizedMagYearFunc(), simStartTimeMillis, 
						simEndTimeMillis, 1000, etasParams.get_k(), etasParams.get_p(), ETAS_Utils.magMin_DEFAULT, etasParams.get_c());
			
			// This is to write out the fraction spontaneous as a funcation of time
//			EvenlyDiscretizedFunc rateFunc = etas_utils.getSpontanousEventRateFunction(mfd, U3_EqkCatalogStatewideCompleteness.load().getEvenlyDiscretizedMagYearFunc(), simStartTimeMillis, 
//					simEndTimeMillis, 1000, etasParams.get_k(), etasParams.get_p(), ETAS_Utils.magMin_DEFAULT, etasParams.get_c());
//			for(int i=0;i<rateFunc.size();i++) {
//				double year = (rateFunc.getX(i)-(double)simStartTimeMillis)/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
//				double fractRate = rateFunc.getY(i)/mfd.getTotalIncrRate();
//				System.out.println(year+"\t"+fractRate);
//			}
//			System.exit(-1);


			for(int r=0;r<spontEventTimes.length;r++) {
				ETAS_EqkRupture rup = new ETAS_EqkRupture();
				rup.setOriginTime(spontEventTimes[r]);
				rup.setID(eventID);
				rup.setGeneration(0);
				eventsToProcess.add(rup);
				eventID += 1;
			}
			double fractionNonTriggered = (double)spontEventTimes.length/(origTotRate*simDurationYrs);
			String spEvStringInfo = "Spontaneous Events:\n\n\tFraction non-triggered = "+fractionNonTriggered+
					"\t(sample num over total expected num)"+"\n\tnumSpontEventsSampled="+spontEventTimes.length+"\n";
			if(D) System.out.println(spEvStringInfo);
			info_fr.write("\n"+spEvStringInfo);
			info_fr.flush();
		}
		

		// If scenarioRup != null, generate  diagnostics if in debug mode!
		List<EvenlyDiscretizedFunc> expectedPrimaryMFDsForScenarioList=null;
		if(scenarioRup !=null) {
			long rupOT = scenarioRup.getOriginTime();
			double startDay = (double)(simStartTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;	// convert epoch to days from event origin time
			double endDay = (double)(simEndTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
			double expNum = ETAS_Utils.getExpectedNumEvents(etasParams.get_k(), etasParams.get_p(), scenarioRup.getMag(), ETAS_Utils.magMin_DEFAULT, etasParams.get_c(), startDay, endDay);
			
			info_fr.write("\nMagnitude of Scenario: "+(float)scenarioRup.getMag()+"\n");
			info_fr.write("\nExpected number of primary events for Scenario: "+expNum+"\n");
			info_fr.write("\nObserved number of primary events for Scenario: "+numPrimaryAshockForScenario+"\n");
			System.out.println("\nMagnitude of Scenario: "+(float)scenarioRup.getMag());
			System.out.println("Expected number of primary events for Scenario: "+expNum);
			System.out.println("Observed number of primary events for Scenario: "+numPrimaryAshockForScenario+"\n");

			if(D && generateDiagnosticsForScenario) {
				System.out.println("Computing Scenario Diagnostics");
				long timeMillis =System.currentTimeMillis();
				expectedPrimaryMFDsForScenarioList = etas_PrimEventSampler.generateRuptureDiagnostics(scenarioRup, expNum, "Scenario", resultsDir,info_fr);
				float timeMin = ((float)(System.currentTimeMillis()-timeMillis))/(1000f*60f);
				System.out.println("Computing Scenario Diagnostics took (min): "+timeMin);
				if (exit_after_scenario_diagnostics)
					System.exit(0);
			}
			info_fr.flush();
		}
		
		if(D) {
			System.out.println("Testing the etas_PrimEventSampler");
			etas_PrimEventSampler.testRates();
//			etas_PrimEventSampler.testMagFreqDist();	// this is time consuming
		}

		CalcProgressBar progressBar;
		try {
			progressBar = new CalcProgressBar("Primary aftershocks to process", "junk");
			progressBar.showProgress(true);
		} catch (Throwable t) {
			// headless, don't show it
			progressBar = null;
		}
		
		if (D) System.out.println("Looping over eventsToProcess (initial num = "+eventsToProcess.size()+")...\n");
		if (D) System.out.println("\tFault system ruptures triggered (date\tmag\tname\tnthRup,src,rupInSrc,fltSysRup):");
		info_fr.write("\nFault system ruptures triggered (date\tmag\tname\tnthRup,src,rupInSrc,fltSysRup):\n");
		info_fr.flush();

		st = System.currentTimeMillis();
		
		int numSimulatedEvents = 0;
		
		EpicenterMapThread mapThread;
		if (D && live_map)
			mapThread = ETAS_SimAnalysisTools.plotUpdatingEpicenterMap(
				simulationName, null, simulatedRupsQueue, griddedRegion.getBorder());
		else
			mapThread = null;
		
		if (D && pause_for_events) {	// this is demo mode for talks; so it can be set to go when the button hit
			try {
				JOptionPane.showMessageDialog(null, "Continue", "Ready To Generate Events", JOptionPane.PLAIN_MESSAGE);
			} catch (HeadlessException e) {
				// do nothing if Headless
			}
		}
		
		info_fr.flush();	// this writes the above out now in case of crash
		
		while(eventsToProcess.size()>0) {
			
			if (progressBar != null) progressBar.updateProgress(numSimulatedEvents, eventsToProcess.size()+numSimulatedEvents);
			
			ETAS_EqkRupture rup = eventsToProcess.poll();	//Retrieves and removes the head of this queue, or returns null if this queue is empty.
			
			boolean succeededInSettingRupture=true;	// used later to indicate whether ETAS Primary event sampler succeeded in sampling an event
			
			if(rup.getParentID() == -1)	{ // it's a spontaneous event TODO
//			if(rup.getParentRup() == null)	{ // it's a spontaneous event
				Location hypoLoc = null;
				ProbEqkRupture erf_rup;
				nthRup = spontaneousRupSampler.getRandomInt(etas_utils.getRandomDouble());	// sample from long-term model
				erf_rup = erf.getNthRupture(nthRup);
				LocationList surfPts = erf_rup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
				if(surfPts.size() == 1) {// point source
					Location ptLoc = surfPts.get(0);
					// FOLLOWING ASSUMES A GRID SPACING OF 0.1 FOR BACKGROUND SEIS; "0.99" is to keep it in cell
					hypoLoc = new Location(ptLoc.getLatitude()+(etas_utils.getRandomDouble()-0.5)*0.1*0.99,
							ptLoc.getLongitude()+(etas_utils.getRandomDouble()-0.5)*0.1*0.99,
							seisDepthDistribution.getRandomDepth());
					rup.setPointSurface(hypoLoc);

				}
				else {
					int hypIndex = etas_utils.getRandomInt(surfPts.size()-1);	// choose random loc assuming uniform probability among points
					hypoLoc = surfPts.get(hypIndex);
					rup.setRuptureSurface(erf_rup.getRuptureSurface());
				}
				rup.setAveRake(erf_rup.getAveRake());
				rup.setMag(erf_rup.getMag());
				rup.setNthERF_Index(nthRup);
				rup.setHypocenterLocation(hypoLoc);
				int sourceIndex = erf.getSrcIndexForNthRup(nthRup);
				if (sourceIndex < numFaultSysSources)
					rup.setFSSIndex(fssERF.getFltSysRupIndexForNthRup(nthRup));
				else
					rup.setGridNodeIndex(sourceIndex - numFaultSysSources);
			}
			// Not spontaneous, so set as a primary aftershock
			else {
				succeededInSettingRupture = etas_PrimEventSampler.setRandomPrimaryEvent(rup);
			}
			
			// break out if we failed to set the rupture
			if(!succeededInSettingRupture) // TODO shouldn't following chunk of code be in the else statement directly above?
				continue;
			nthRup = rup.getNthERF_Index();
			int srcIndex = erf.getSrcIndexForNthRup(nthRup);
			int fltSysRupIndex = -1;
			if(srcIndex<numFaultSysSources) {
				fltSysRupIndex = fssERF.getFltSysRupIndexForNthRup(nthRup);
				rup.setFSSIndex(fltSysRupIndex);
			}
				

			// add the rupture to the list
			simulatedRupsQueue.add(rup);	// this storage does not take much memory during the simulations
			numSimulatedEvents += 1;
			
			ETAS_CatalogIO.writeEventToFile(simulatedEventsFileWriter, rup);
			
			long rupOT = rup.getOriginTime();
			
			// now sample primary aftershock times for this event
			if(includeIndirectTriggering) {
				int parID = rup.getID();	// rupture is now the parent
				int gen = rup.getGeneration()+1;
				double startDay = 0;	// starting at origin time since we're within the timespan
				double endDay = (double)(simEndTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
//				double[] eventTimes = etas_utils.getDefaultRandomEventTimes(rup.getMag(), startDay, endDay);
				double[] eventTimes = etas_utils.getRandomEventTimes(etasParams.get_k(), etasParams.get_p(), rup.getMag(), ETAS_Utils.magMin_DEFAULT, etasParams.get_c(), startDay, endDay);
				LocationList locList = null;
				if(eventTimes.length>0) {
					for(int i=0; i<eventTimes.length;i++) {
						long ot = rupOT +  (long)(eventTimes[i]*(double)ProbabilityModelsCalc.MILLISEC_PER_DAY);
						ETAS_EqkRupture newRup = new ETAS_EqkRupture(rup, eventID, ot);
						newRup.setGeneration(gen);	// TODO have set in above constructor?
						newRup.setParentID(parID);	// TODO have set in above constructor?
						if(rup.getFSSIndex()==-1)
							newRup.setParentTriggerLoc(etas_utils.getRandomLocationOnRupSurface(rup));
						else {
							// this produces too few aftershocks on highly creeping faults:
//							Location tempLoc = etas_utils.getRandomLocationOnRupSurface(rup);
							
							// for no creep/aseis reduction:
							if(locList==null) {	// make reusable location list
								RuptureSurface surf = etas_utils.getRuptureSurfaceWithNoCreepReduction(rup.getFSSIndex(), fssERF, 0.05);
								locList = surf.getEvenlyDiscritizedListOfLocsOnSurface();							
							}
							Location tempLoc = locList.get(etas_utils.getRandomInt(locList.size()-1));
							
							if(tempLoc.getDepth()>etas_PrimEventSampler.maxDepth) {
								Location newLoc = new Location(tempLoc.getLatitude(),tempLoc.getLongitude(), etas_PrimEventSampler.maxDepth);
								tempLoc=newLoc;
							}

							// now add some randomness for numerical stability:
							newRup.setParentTriggerLoc(etas_PrimEventSampler.getRandomFuzzyLocation(tempLoc));
						}
						etas_PrimEventSampler.addRuptureToProcess(newRup);
						eventsToProcess.add(newRup);
						eventID +=1;
					}
				}		
			}
			
			
			// if it was a fault system rupture, need to update time span, rup rates, block, and samplers.

			if(srcIndex<numFaultSysSources) {
				
				nthFaultSysRupAftershocks.add(nthRup);

				// set the start time for the time dependent calcs
				erf.getTimeSpan().setStartTimeInMillis(rupOT);	
				
				if(D) {
					Toolkit.getDefaultToolkit().beep();
					System.out.println("GOT A FAULT SYSTEM RUPTURE!");
				}
				
				TimeSpan ts = erf.getTimeSpan();
				String rupString = "\t"+ts.getStartTimeMonth()+"/"+ts.getStartTimeDay()+"/"+ts.getStartTimeYear()+"\tmag="+
						(float)rup.getMag()+"\t"+erf.getSource(srcIndex).getName()+
						"\n\tnthRup="+nthRup+", srcIndex="+srcIndex+", RupIndexInSource="+
						erf.getRupIndexInSourceForNthRup(nthRup)+", fltSysRupIndex="+fltSysRupIndex+"\tgen="+rup.getGeneration();
				if (rup.getParentRup() != null)
					rupString += "\tparID="+rup.getParentRup().getID()+"\tparMag="+rup.getParentRup().getMag();
				else {
					rupString += "\tparID=-1 (spontaneous)";
				}
				if(D) System.out.println(rupString);
				info_fr.write(rupString+"\n");

				// set the date of last event for this rupture
				fssERF.setFltSystemSourceOccurranceTime(srcIndex, rupOT);

				// now update source rates for etas_PrimEventSampler & spontaneousRupSampler
				if(D) System.out.print("\tUpdating src rates for etas_PrimEventSampler & spontaneousRupSampler; ");
				Long st2 = System.currentTimeMillis();
				if(erf.getParameter(ProbabilityModelParam.NAME).getValue() != ProbabilityModelOptions.POISSON) {
					erf.updateForecast();
					for(int s=0;s<numFaultSysSources;s++) {
						ProbEqkSource src = erf.getSource(s);
						double oldRate = sourceRates[s];
						sourceRates[s] = src.computeTotalEquivMeanAnnualRate(duration);
						double newRate = sourceRates[s];
						// TEST THAT RATE CHANGED PROPERLY
						if(D) {
							if(s == erf.getSrcIndexForNthRup(nthRup)) {
								System.out.print("for rup that occurred, oldRate="+(float)oldRate+" & newRate = "+(float)newRate+"\n");			
							}
						}
						// update the spontaneous event sampler with new rupture rates
						for(int r=0 ; r<src.getNumRuptures(); r++) {
							ProbEqkRupture rupInSrc = src.getRupture(r);
							double rate = rupInSrc.getMeanAnnualRate(duration);
							spontaneousRupSampler.set(erf.getIndexN_ForSrcAndRupIndices(s, r), rate);
						}
					}
					// now update the ETAS sampler
					etas_PrimEventSampler.declareRateChange();

				}
				if(D) {
					System.out.println("Sampler update took "+(System.currentTimeMillis()-st2)/1000+" secs");					
					System.out.println("Running generateRuptureDiagnostics(*)");
					double startDay = 0.0;	// from the moment it occurs
					double endDay = (double)(simEndTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
					double expNum = ETAS_Utils.getExpectedNumEvents(etasParams.get_k(), etasParams.get_p(), rup.getMag(), ETAS_Utils.magMin_DEFAULT, etasParams.get_c(), startDay, endDay);
					
					String rupInfo = "FltSysRup"+fltSysRupIndex+"_trigNum"+(nthFaultSysRupAftershocks.size()-1);
					
					info_fr.write("\nExpected number of primary events for "+rupInfo+": "+expNum+"\n");
					System.out.println("\nExpected number of primary events for "+rupInfo+": "+expNum);

					if(generateDiagnostics)
						etas_PrimEventSampler.generateRuptureDiagnostics(rup, expNum, rupInfo, resultsDir, info_fr);

				}
			}
			
			info_fr.flush();	// this writes the above out now in case of crash

		}
		
		if (progressBar != null) progressBar.showProgress(false);
		if (mapThread != null)
			mapThread.kill();

		if(D) System.out.println("\nLooping over events took "+(System.currentTimeMillis()-st)/1000+" secs\n");
		info_fr.write("\nLooping over events took "+(System.currentTimeMillis()-st)/1000+" secs\n\n");
		
		ETAS_SimAnalysisTools.writeMemoryUse("Memory after loop:");
		
		
		int[] numInEachGeneration = ETAS_SimAnalysisTools.getNumAftershocksForEachGeneration(simulatedRupsQueue, 10);
		String numInfo = "Total num ruptures: "+simulatedRupsQueue.size()+"\n";
		numInfo += "Num spontaneous: "+numInEachGeneration[0]+"\n";
		numInfo += "Num 1st Gen: "+numInEachGeneration[1]+"\n";
		numInfo += "Num 2nd Gen: "+numInEachGeneration[2]+"\n";
		numInfo += "Num 3rd Gen: "+numInEachGeneration[3]+"\n";
		numInfo += "Num 4th Gen: "+numInEachGeneration[4]+"\n";
		numInfo += "Num 5th Gen: "+numInEachGeneration[5]+"\n";
		numInfo += "Num 6th Gen: "+numInEachGeneration[6]+"\n";
		numInfo += "Num 7th Gen: "+numInEachGeneration[7]+"\n";
		numInfo += "Num 8th Gen: "+numInEachGeneration[8]+"\n";
		numInfo += "Num 9th Gen: "+numInEachGeneration[9]+"\n";
		numInfo += "Num 10th Gen: "+numInEachGeneration[10]+"\n";
		
		if(D) System.out.println(numInfo);
		info_fr.write(numInfo+"\n");


		if(D && scenarioRup !=null) {	// scenario rupture included
			int inputRupID = scenarioRup.getID();	// TODO already defined above?
			ETAS_SimAnalysisTools.plotRateVsLogTimeForPrimaryAshocksOfRup(simulationName, new File(resultsDir,"logRateDecayForScenarioPrimaryAftershocks.pdf").getAbsolutePath(), simulatedRupsQueue, scenarioRup,
					etasParams.get_k(), etasParams.get_p(), etasParams.get_c());
			ETAS_SimAnalysisTools.plotRateVsLogTimeForAllAshocksOfRup(simulationName, new File(resultsDir,"logRateDecayForScenarioAllAftershocks.pdf").getAbsolutePath(), simulatedRupsQueue, scenarioRup,
					etasParams.get_k(), etasParams.get_p(), etasParams.get_c());

			ETAS_SimAnalysisTools.plotEpicenterMap(simulationName, new File(resultsDir,"hypoMap.pdf").getAbsolutePath(), obsEqkRuptureList.get(0), simulatedRupsQueue, griddedRegion.getBorder());
			ETAS_SimAnalysisTools.plotDistDecayDensityOfAshocksForRup("Scenario in "+simulationName, new File(resultsDir,"distDecayDensityForScenario.pdf").getAbsolutePath(), 
					simulatedRupsQueue, etasParams.get_q(), etasParams.get_d(), scenarioRup);
			ArrayList<IncrementalMagFreqDist> obsAshockMFDsForScenario = ETAS_SimAnalysisTools.getAftershockMFDsForRup(simulatedRupsQueue, inputRupID, simulationName);
			if(generateDiagnosticsForScenario == true)
				obsAshockMFDsForScenario.add((IncrementalMagFreqDist)expectedPrimaryMFDsForScenarioList.get(0));
			ETAS_SimAnalysisTools.plotMagFreqDistsForRup("AshocksOfScenarioMFD", resultsDir, obsAshockMFDsForScenario);
			
			
			// write stats for first rup
			
			double expPrimNumAtMainMag = Double.NaN;
			double expPrimNumAtMainMagMinusOne = Double.NaN;
			if(generateDiagnosticsForScenario && expectedPrimaryMFDsForScenarioList.get(1) != null) {
				expPrimNumAtMainMag = expectedPrimaryMFDsForScenarioList.get(1).getInterpolatedY(scenarioRup.getMag());
				expPrimNumAtMainMagMinusOne = expectedPrimaryMFDsForScenarioList.get(1).getInterpolatedY(scenarioRup.getMag()-1.0);				
			}
			EvenlyDiscretizedFunc obsPrimCumMFD = obsAshockMFDsForScenario.get(1).getCumRateDistWithOffset();
			double obsPrimNumAtMainMag = obsPrimCumMFD.getInterpolatedY(scenarioRup.getMag());
			double obsPrimNumAtMainMagMinusOne = obsPrimCumMFD.getInterpolatedY(scenarioRup.getMag()-1.0);
			EvenlyDiscretizedFunc obsAllCumMFD = obsAshockMFDsForScenario.get(1).getCumRateDistWithOffset();
			double obsAllNumAtMainMag = obsAllCumMFD.getInterpolatedY(scenarioRup.getMag());
			double obsAllNumAtMainMagMinusOne = obsAllCumMFD.getInterpolatedY(scenarioRup.getMag()-1.0);
			String testEventStats="\nAftershock Stats for Scenario event (only):\n";
			testEventStats+="\tNum Primary Aftershocks at main shock mag("+(float)scenarioRup.getMag()+"):\n\t\tExpected="+expPrimNumAtMainMag+"\n\t\tObserved="+obsPrimNumAtMainMag+"\n";
			testEventStats+="\tNum Primary Aftershocks at one minus main-shock mag("+(float)(scenarioRup.getMag()-1.0)+"):\n\t\tExpected="+expPrimNumAtMainMagMinusOne+"\n\t\tObserved="+obsPrimNumAtMainMagMinusOne+"\n";
			testEventStats+="\tTotal Observed Num Aftershocks:\n\t\tAt main-shock mag = "+obsAllNumAtMainMag+"\n\t\tAt one minus main-shock mag = "+obsAllNumAtMainMagMinusOne+"\n";
			if(D) System.out.println(testEventStats);
			info_fr.write(testEventStats);
		} else if (D) {
			ETAS_SimAnalysisTools.plotEpicenterMap(simulationName, new File(resultsDir,"hypoMap.pdf").getAbsolutePath(), null, simulatedRupsQueue, griddedRegion.getBorder());
		}
		
		if(D) {
			ETAS_SimAnalysisTools.plotRateVsLogTimeForPrimaryAshocks(simulationName, new File(resultsDir,"logRateDecayPDF_ForAllPrimaryEvents.pdf").getAbsolutePath(), simulatedRupsQueue,
					etasParams.get_k(), etasParams.get_p(), etasParams.get_c());
			ETAS_SimAnalysisTools.plotDistDecayDensityFromParentTriggerLocHist(simulationName, new File(resultsDir,"distDecayForAllPrimaryEvents.pdf").getAbsolutePath(), simulatedRupsQueue, etasParams.get_q(), etasParams.get_d());
			ETAS_SimAnalysisTools.plotMagFreqDists(simulationName, resultsDir, simulatedRupsQueue);
		}
		
		info_fr.close();
		simulatedEventsFileWriter.close();

		ETAS_SimAnalysisTools.writeMemoryUse("Memory at end of simultation");
	}
	
	
	/**
	 * This utility finds the source index for the fault system rupture that has the given first and last subsection
	 * @param erf
	 * @param firstSectID
	 * @param secondSectID
	 */
	private static void writeInfoAboutSourceWithThisFirstAndLastSection(FaultSystemSolutionERF erf, int firstSectID, int secondSectID) {
		System.out.println("Looking for source...");
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
			List<Integer> sectListForSrc = rupSet.getSectionsIndicesForRup(erf.getFltSysRupIndexForSource(s));
			boolean firstIsIt = rupSet.getFaultSectionData(sectListForSrc.get(0)).getSectionId() == firstSectID;
			boolean lastIsIt = rupSet.getFaultSectionData(sectListForSrc.get(sectListForSrc.size()-1)).getSectionId() == secondSectID;
			if(firstIsIt && lastIsIt) {
				int fssIndex=erf.getFltSysRupIndexForSource(s);
				System.out.println("SourceIndex="+s+"\tfssIndex="+fssIndex+"\t"+erf.getSource(s).getName()+"\tmag="+erf.getSolution().getRupSet().getMagForRup(fssIndex));
				break; 
			}
			firstIsIt = rupSet.getFaultSectionData(sectListForSrc.get(0)).getSectionId() == secondSectID;
			lastIsIt = rupSet.getFaultSectionData(sectListForSrc.get(sectListForSrc.size()-1)).getSectionId() == firstSectID;
			if(firstIsIt && lastIsIt) {
				int fssIndex=erf.getFltSysRupIndexForSource(s);
				System.out.println("SourceIndex="+s+"\tfssIndex="+fssIndex+"\t"+erf.getSource(s).getName()+"\tmag="+erf.getSolution().getRupSet().getMagForRup(fssIndex));
				break;
			}
		}
	}
	
	
	/**
	 * This utility finds the source index for the fault system rupture that has the given first and last subsection
	 * @param erf
	 * @param firstSectID
	 * @param secondSectID
	 */
	private static void writeTotRateRupOccurOnTheseTwoSections(FaultSystemSolutionERF erf, int firstSectID, int secondSectID) {
		System.out.println("Looking for source...");
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		double totRate=0;
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			List<Integer> sectListForSrc = rupSet.getSectionsIndicesForRup(erf.getFltSysRupIndexForSource(s));
			if(sectListForSrc.contains(firstSectID) && sectListForSrc.contains(secondSectID)) {
				totRate += erf.getSource(s).computeTotalEquivMeanAnnualRate(erf.getTimeSpan().getDuration());
			}
		}
		System.out.println("totRate="+totRate+"\n\t"+rupSet.getFaultSectionData(firstSectID).getName()+"\n\t"+rupSet.getFaultSectionData(secondSectID).getName());
	}

	
	
	private static void writeInfoAboutClosestSectionToLoc(FaultSystemSolutionERF erf, Location loc) {
		List<FaultSectionPrefData> fltDataList = erf.getSolution().getRupSet().getFaultSectionDataList();
		double minDist = Double.MAX_VALUE;
		int index=-1;
		CalcProgressBar progressBar = new CalcProgressBar("Fault data to process", "junk");
		progressBar.showProgress(true);
		int counter=0;

		for(FaultSectionPrefData fltData:fltDataList) {
			progressBar.updateProgress(counter, fltDataList.size());
			counter+=1;
			double dist = LocationUtils.distanceToSurf(loc, fltData.getStirlingGriddedSurface(1.0, false, true));
			if(minDist>dist) {
				minDist=dist;
				index = fltData.getSectionId();
			}
		}
		progressBar.showProgress(false);
		minDist = LocationUtils.distanceToSurf(loc, fltDataList.get(index).getStirlingGriddedSurface(0.01, false, true));
		System.out.println(index+"\tdist="+(float)minDist+"\tfor\t"+fltDataList.get(index).getName());
	}

	
	
	/**
	 * This utility writes info about sources that use the given index and that are between the specified minimum and maximum mag
	 * @param erf
	 * @param sectID
	 * @param minMag
	 * @param maxMag
	 */
	private static void writeInfoAboutSourcesThatUseSection(FaultSystemSolutionERF erf, int sectID, double minMag, double maxMag) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		System.out.println("srcIndex\tfssIndex\tprob\tmag\tname\t"+rupSet.getFaultSectionData(sectID).getName());
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			List<Integer> sectListForSrc = rupSet.getSectionsIndicesForRup(erf.getFltSysRupIndexForSource(s));
			if(sectListForSrc.contains(sectID)) {
				int fssIndex=erf.getFltSysRupIndexForSource(s);
				double meanMag = erf.getSolution().getRupSet().getMagForRup(fssIndex);
				if(meanMag<minMag || meanMag>maxMag)
					continue;
				double prob = erf.getSource(s).computeTotalProb();
				System.out.println(+s+"\t"+fssIndex+"\t"+prob+"\t"+meanMag+"\t"+erf.getSource(s).getName());
//				double probAboveM7 = erf.getSource(s).computeTotalProbAbove(7.0);
//				if(probAboveM7 > 0.0)
//					System.out.println(+s+"\t"+fssIndex+"\t"+probAboveM7+"\t"+meanMag+"\t"+erf.getSource(s).getName());
			}
		}
	}
	
	
	/**
	 * This utility writes a location near the center of the surface of the given section
	 * @param erf
	 * @param sectID
	 */
	private static void writeLocationAtCenterOfSectionSurf(FaultSystemSolutionERF erf, int sectID) {
		String name = erf.getSolution().getRupSet().getFaultSectionData(sectID).getName();
		StirlingGriddedSurface surf = erf.getSolution().getRupSet().getFaultSectionData(sectID).getStirlingGriddedSurface(1.0, false, true);
		Location loc = surf.getLocation(surf.getNumRows()/2, surf.getNumCols()/2);
		System.out.println("Locationat center of "+name+"\t"+loc.getLatitude()+", "+loc.getLongitude()+", "+loc.getDepth());
	}


	
	
	/**
	 * This builds a scenario rup for the given scenario.
	 * This returns null is scenario=null.
	 * TODO origin time should be passed in, as well as whether ERF elastic rebound is reset for scenario?
	 * @param scenario
	 * @param erf
	 * @return
	 */
	public static ETAS_EqkRupture buildScenarioRup(TestScenario scenario, FaultSystemSolutionERF_ETAS erf) {
		ETAS_EqkRupture scenarioRup=null;
		if(scenario != null) {
			scenarioRup = new ETAS_EqkRupture();
			Long ot = Math.round((2014.0-1970.0)*ProbabilityModelsCalc.MILLISEC_PER_YEAR); // occurs at 2014
			scenarioRup.setOriginTime(ot);	
			int fssIndex = scenario.fssIndex;
			if(fssIndex>=0) {
				int srcID = erf.getSrcIndexForFltSysRup(fssIndex);
				Preconditions.checkState(srcID >= 0, "Source not found for FSS index="+fssIndex);
				ProbEqkRupture rupFromERF = erf.getSource(srcID).getRupture(0);
				scenarioRup.setAveRake(rupFromERF.getAveRake());
				scenarioRup.setMag(rupFromERF.getMag());
				if (!Double.isNaN(scenario.mag))
					// override mag
					scenarioRup.setMag(scenario.mag);
				scenarioRup.setFSSIndex(fssIndex);
				scenarioRup.setRuptureSurface(rupFromERF.getRuptureSurface());
//	System.out.println(rupFromERF.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface().size());
//				CompoundSurface trimmedSurface = GriddedSurfaceUtils.trimEndsOfSurface(
//						(CompoundSurface)rupFromERF.getRuptureSurface(), 3, 3);
//				scenarioRup.setRuptureSurface(trimmedSurface);
//	System.out.println(trimmedSurface.getEvenlyDiscritizedListOfLocsOnSurface().size());
//	System.exit(-1);
//				scenarioRup.setRuptureSurface(rupFromERF.getRuptureSurface().getMoved(new LocationVector(295.037, 0.5, 0.0)));
//				System.out.println("test Mainshock: "+erf.getSource(srcID).getName()+"; mag="+scenarioRup.getMag());
				System.out.println("\tProbBeforeDateOfLastReset: "+erf.getSource(srcID).getRupture(0).getProbability());
				erf.setFltSystemSourceOccurranceTime(srcID, ot);
				erf.updateForecast();
				System.out.println("\tProbAfterDateOfLastReset: "+erf.getSource(srcID).getRupture(0).getProbability());
			} 
			else {
				scenarioRup.setAveRake(0.0);
				if (scenario == TestScenario.CUSTOM) {
					// prompt for loc/mag
					ParameterList params = new ParameterList();
					DoubleParameter magParam = new DoubleParameter("Magnitude", 2d, 8.89, (Double)scenario.mag);
					params.addParameter(magParam);
					LocationParameter locParam = new LocationParameter("Hpocenter Location", scenario.loc);
					params.addParameter(locParam);
					ParameterListEditor edit = new ParameterListEditor(params);
					
					// this will block until done
					JOptionPane.showMessageDialog(null, edit, "Custom Rupture", JOptionPane.PLAIN_MESSAGE);
					scenarioRup.setMag(magParam.getValue());
					scenarioRup.setPointSurface(locParam.getValue());
					System.out.println("Loaded custom scenario: M"+(float)scenarioRup.getMag()
							+", "+scenarioRup.getHypocenterLocation());
				} else {
					scenarioRup.setMag(scenario.mag);
					scenarioRup.setPointSurface(scenario.loc);
					scenarioRup.setHypocenterLocation(scenario.loc);
				}
			}	
		}
	return scenarioRup;
	}

	/**
	 * 
	 * 
	 * TODO:
	 * 
	 * @param scenario
	 * @param etasParams
	 * @param randomSeed
	 * @param simulationName - if null this will be constructed from input information
	 * @param histQkList
	 */
	public static void runTest(TestScenario scenario, ETAS_ParameterList etasParams, Long randomSeed, 
			String simulationName, ObsEqkRupList histQkList, double startTimeYear, double durationYears) {
		
		ETAS_SimAnalysisTools.writeMemoryUse("Memory at beginning of run");

		Long st = System.currentTimeMillis();

		FaultSystemSolutionERF_ETAS erf = getU3_ETAS_ERF(startTimeYear, durationYears);
//System.out.println("TotalRateBeforeGriddedSeisCorr: "+ERF_Calculator.getTotalMFD_ForERF(erf, 2.55, 8.45, 60, true).getTotalIncrRate());		

		if(etasParams.getApplyGridSeisCorr())
			ETAS_Simulator.correctGriddedSeismicityRatesInERF(erf, false);
//System.out.println("TotalRateAfterGriddedSeisCorr: "+ERF_Calculator.getTotalMFD_ForERF(erf, 2.55, 8.45, 60, true).getTotalIncrRate());		
//System.exit(-1);
		
		if(simulationName == null) {
			String imposeGR_string;
			if(etasParams.getImposeGR())
				imposeGR_string = "_GRcorrApplied";
			else {
				imposeGR_string = "_noGRcorr";
			}
			simulationName = scenario+"_"+etasParams.getU3ETAS_ProbModel()+imposeGR_string;
		}
		
//		testERF_ParamChanges(erf);
//		System.exit(-1);
		
//		// print surpise valley surface (lowest GR corr)
//		FaultSectionPrefData surpriseFaultData = erf.getSolution().getRupSet().getFaultSectionData(2460);
//		System.out.println(surpriseFaultData.getName());
//		StirlingGriddedSurface surf = surpriseFaultData.getStirlingGriddedSurface(1.0, false, true);
//		System.out.println("Mid surf loc: "+surf.getLocation(surf.getNumRows()/2, surf.getNumCols()/2));
//		System.exit(-1);
		
		
//		IncrementalMagFreqDist testMFD = ((InversionFaultSystemSolution)erf.getSolution()).getFinalTotalNucleationMFD_forSect(1846, 2.05, 8.95, 70);
//		IncrementalMagFreqDist testMFD2 = ((InversionFaultSystemSolution)erf.getSolution()).getFinalTrulyOffFaultMFD();
//		testMFD2.normalizeByTotalRate();
//		testMFD2.scaleToCumRate(2.05, testMFD.getCumRate(2.05));
//		ArrayList<EvenlyDiscretizedFunc> tempList = new ArrayList<EvenlyDiscretizedFunc>();
//		tempList.add(testMFD2.getCumRateDistWithOffset());
//		tempList.add(testMFD.getCumRateDistWithOffset());
//		GraphWindow graph = new GraphWindow(tempList, "Test MFDs"); 
//		ArrayList<EvenlyDiscretizedFunc> tempList2 = new ArrayList<EvenlyDiscretizedFunc>();
//		tempList2.add(testMFD2);
//		tempList2.add(testMFD);
//		GraphWindow graph2 = new GraphWindow(tempList2, "Test MFDs"); 

		
		CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();

		ETAS_EqkRupture scenarioRup = buildScenarioRup(scenario, erf);
		
		
		System.out.println("trace of scenario:");
		RuptureSurface surf = scenarioRup.getRuptureSurface();
		LocationList topRowLocs = surf.getEvenlyDiscritizedUpperEdge();
		LocationList bottomRowLocs = surf.getEvenlyDiscritizedLowerEdge();
		for(int i=0;i<topRowLocs.size();i++) {
			double lat = (topRowLocs.get(i).getLatitude() + bottomRowLocs.get(i).getLatitude())/2;
			double lon = (topRowLocs.get(i).getLongitude() + bottomRowLocs.get(i).getLongitude())/2;
			System.out.println(lon+"\t"+lat);
		}
		System.exit(1);;
		
		// TEST RIGHT HERE
//		erf.setFltSectOccurranceTime(1848, scenarioRup.getOriginTime());
//		erf.setFltSectOccurranceTime(1847, scenarioRup.getOriginTime());

		
//		System.out.println("aveStrike="+scenarioRup.getRuptureSurface().getAveStrike());
//		for(Location loc:scenarioRup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface()) {
//			System.out.println(loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth());
//		}
//		System.exit(-1);
		
		boolean includeSpontEvents=false;
		boolean includeIndirectTriggering=true;
		double gridSeisDiscr = 0.1;
		
		System.out.println("Starting testETAS_Simulation");
		try {
			String dirNameForSavingFiles = "U3_ETAS_"+simulationName+"/";
			File resultsDir = new File(dirNameForSavingFiles);
			testETAS_Simulation(resultsDir, erf, griddedRegion, scenarioRup, histQkList,  includeSpontEvents, 
					includeIndirectTriggering, gridSeisDiscr, simulationName, randomSeed, etasParams);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		float timeMin = (float)(System.currentTimeMillis()-st)/60000f;
		System.out.println("Total simulation took "+timeMin+" min");

	}
	
	public static ObsEqkRupList getHistCatalog(double startTimeYear, FaultSystemRupSet rupSet) {
		File file = new File("/Users/field/workspace/OpenSHA/dev/scratch/UCERF3/data/EarthquakeCatalog/ofr2013-1165_EarthquakeCat.txt");
		ObsEqkRupList histQkList=null;
		try {
			histQkList = UCERF3_CatalogParser.loadCatalog(file);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
//		HistogramFunction func = new HistogramFunction(1.0, 61, 2.0);
//		for(ObsEqkRupture qk : histQkList) {
//			func.add(qk.getHypocenterLocation().getDepth(), 1);
//		}
//		GraphWindow graph = new GraphWindow(func, "Hypocenter Depth Histogram");
		double timeInMillis = (startTimeYear-1970)*ProbabilityModelsCalc.MILLISEC_PER_YEAR;
		histQkList = histQkList.getRupsBefore((long)timeInMillis);
		
		// load in rupture surfaces. Note: this must happen before mag complete filtering, surface loading needs all ruptures
		Preconditions.checkState(rupSet.getNumRuptures() == 253706, "Hardcoded to FM3.1 but this rup set isn't FM3.1");
		try {
			FiniteFaultMappingData.loadRuptureSurfaces(
					UCERF3_DataUtils.locateResourceAsStream("EarthquakeCatalog", "finite_fault_mappings.xml"),
					histQkList, FaultModels.FM3_1, rupSet);
		} catch (DocumentException e1) {
			ExceptionUtils.throwAsRuntimeException(e1);
		}
		
		return histQkList;
	}
	
	/**
	 * This returns a list of observed ruptures that has been filtered for state wide completeness according to
	 * UCERF3.data.U3_EqkCatalogStatewideCompleteness
	 * @param startTimeYear
	 * @return
	 */
	public static ObsEqkRupList getHistCatalogFiltedForStatewideCompleteness(double startTimeYear, FaultSystemRupSet rupSet) {
		ObsEqkRupList qkList = getHistCatalog(startTimeYear, rupSet);
		
		// filter by mag complete
		U3_EqkCatalogStatewideCompleteness magComplete;
		try {
			magComplete = U3_EqkCatalogStatewideCompleteness.load();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		return magComplete.getFilteredCatalog(qkList);
	}

	
	public static void plotCatalogMagVsTime(ObsEqkRupList obsQkList, String fileName) {
		DefaultXY_DataSet yearVsMagXYdata = new DefaultXY_DataSet();
		for(ObsEqkRupture rup:obsQkList) {
			double otYear = rup.getOriginTime()/ProbabilityModelsCalc.MILLISEC_PER_YEAR+1970;
			yearVsMagXYdata.set(rup.getMag(),otYear);
		}
		
		U3_EqkCatalogStatewideCompleteness magComplete;
		try {
			magComplete = U3_EqkCatalogStatewideCompleteness.load();
		} catch (IOException e1) {
			throw ExceptionUtils.asRuntimeException(e1);
		}
		EvenlyDiscretizedFunc yrMagCompleteFunc = magComplete.getEvenlyDiscretizedMagYearFunc();
		DefaultXY_DataSet yearVsMagCompleteXYdata = new DefaultXY_DataSet();
		double deltaMagOver2 = yrMagCompleteFunc.getDelta()/2.0;
		for(int i=0;i<yrMagCompleteFunc.size();i++) {
			yearVsMagCompleteXYdata.set(yrMagCompleteFunc.getX(i)-deltaMagOver2,yrMagCompleteFunc.getY(i));
			yearVsMagCompleteXYdata.set(yrMagCompleteFunc.getX(i)+deltaMagOver2,yrMagCompleteFunc.getY(i));
		}

		
		ArrayList<XY_DataSet> funcList = new ArrayList<XY_DataSet>();
		funcList.add(yearVsMagXYdata);
		funcList.add(yearVsMagCompleteXYdata);
		ArrayList<PlotCurveCharacterstics> plotCharList = new ArrayList<PlotCurveCharacterstics>();
		plotCharList.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 1f, Color.RED));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		GraphWindow graph = new GraphWindow(funcList, "Year vs Mag", plotCharList); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Year");
		graph.setY_AxisRange(1750, 2015);
		
		if(fileName != null) {
			try {
				graph.saveAsPDF(fileName+".pdf");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	
	
	public static void plotFilteredCatalogMagFreqDist(ObsEqkRupList obsQkList,IncrementalMagFreqDist yrCompleteForMagFunc, 
			SummedMagFreqDist targetMFD, String fileName) {
		SummedMagFreqDist mfd = new SummedMagFreqDist(2.55,8.45,60);
		for(ObsEqkRupture rup:obsQkList) {
			double yrs = 2012.0 - yrCompleteForMagFunc.getClosestYtoX(rup.getMag());
			mfd.addResampledMagRate(rup.getMag(), 1.0/yrs, true);
		}
		mfd.setName("Catalog MFD");
		mfd.setInfo("Total Rate = "+mfd.getTotalIncrRate());
		
		ArrayList<XY_DataSet> funcList = new ArrayList<XY_DataSet>();
		funcList.add(mfd);
		funcList.add(mfd.getCumRateDistWithOffset());
		funcList.get(1).setName("Cumulative Catalog MFD");
		ArrayList<PlotCurveCharacterstics> plotCharList = new ArrayList<PlotCurveCharacterstics>();
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.BLUE));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));
		
		double expNumAftershockRatio=Double.NaN;
		if(targetMFD != null) {
			
			// compute ratio of expected num aftershocks
			double maxMag = targetMFD.getMaxMagWithNonZeroRate();
			double sumObs=0;
			double sumTarget=0;
			for(double mag=2.55;mag< maxMag+mfd.getDelta()/2.0; mag += mfd.getDelta()) {
				sumObs += mfd.getY(mag)*Math.pow(10, mag);
				sumTarget += targetMFD.getY(mag)*Math.pow(10, mag);
			}
			expNumAftershockRatio=sumObs/sumTarget;			
			targetMFD.setName("Target MFD");
			targetMFD.setInfo("Total Rate = "+targetMFD.getTotalIncrRate());
			funcList.add(targetMFD);
			funcList.add(targetMFD.getCumRateDistWithOffset());
			funcList.get(3).setName("Cumulative Target MFD");
			plotCharList.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLACK));			
			plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));			
		}
		mfd.setInfo(mfd.getInfo()+"\n"+"expNumAftershockRatio = "+(float)expNumAftershockRatio);

		GraphWindow graph = new GraphWindow(funcList, "MFDs; expNumAftershockRatio = "+(float)expNumAftershockRatio, plotCharList); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate (per year)");
		graph.setYLog(true);
		graph.setY_AxisRange(1e-5,1e4);
		
		if(fileName != null) {
			try {
				graph.saveAsPDF(fileName+".pdf");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}


	}

	
	
	/**
	 * This is where on can put bug-producing cases for others to look at
	 * @throws IOException
	 */
	public static void runBugReproduce() throws IOException {
	}


	
	public static FaultSystemSolutionERF_ETAS getU3_ETAS_ERF(double startTimeYear, double durationYears) {
		
		// means solution ERF
		System.out.println("Starting ERF instantiation");
		
		// temporary hack
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
		
//		String fileName="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
		String fileName="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip";
		FaultSystemSolution fss;
		try {
			fss = FaultSystemIO.loadSol(new File(fileName));
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		
////		// Or for Reference branch ERF:
////		// U3.3 compuond file, assumed to be in data/scratch/InversionSolutions
////		// download it from here: http://opensha.usc.edu/ftp/kmilner/ucerf3/2013_05_10-ucerf3p3-production-10runs/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL.zip
//		String fileName = "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL.zip";
//		File invDir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions");
//		File compoundFile = new File(invDir, fileName);
//		CompoundFaultSystemSolution fetcher;
//		InversionFaultSystemSolution fss=null;
//		try {
//			fetcher = CompoundFaultSystemSolution.fromZipFile(compoundFile);
//			LogicTreeBranch ref = LogicTreeBranch.DEFAULT;
//			fss = fetcher.getSolution(ref);
//		} catch (ZipException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}

		
		
		int numSectsWithDateLast = 0;
		for (FaultSectionPrefData sect : fss.getRupSet().getFaultSectionDataList())
			if (sect.getDateOfLastEvent() > Long.MIN_VALUE)
				numSectsWithDateLast++;
		System.out.println(numSectsWithDateLast+"/"+fss.getRupSet().getFaultSectionDataList().size()+" sects have date of last");
		return getU3_ETAS_ERF(fss, startTimeYear, durationYears);
	}
	
	
	public static FaultSystemSolutionERF_ETAS getU3_ETAS_ERF(FaultSystemSolution fss, double startTimeYear, double durationYears) {
		Long st = System.currentTimeMillis();
		FaultSystemSolutionERF_ETAS erf = new FaultSystemSolutionERF_ETAS(fss);
		
		// set parameters
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
		erf.setParameter(BackgroundRupParam.NAME, BackgroundRupType.POINT);
		erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.MID_VALUES);
		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE;
		erf.setParameter(BPTAveragingTypeParam.NAME, aveType);
		erf.setParameter(AleatoryMagAreaStdDevParam.NAME, 0.0);
		erf.getParameter(HistoricOpenIntervalParam.NAME).setValue(startTimeYear-1875d);	
		erf.getTimeSpan().setStartTimeInMillis(Math.round((startTimeYear-1970.0)*ProbabilityModelsCalc.MILLISEC_PER_YEAR)+1);
		erf.getTimeSpan().setDuration(durationYears);
		
		erf.updateForecast();
		
		// for number of gridded source ruptures
//		int numGridRups=0;
//		for(int s=erf.getNumFaultSystemSources();s<erf.getNumSources();s++)
//			numGridRups+=erf.getSource(s).getNumRuptures();
//		System.out.println("numGridRups="+numGridRups);
		
		float timeSec = (float)(System.currentTimeMillis()-st)/1000f;
		System.out.println("ERF instantiation took "+timeSec+" sec");
		
		
		
//		FaultSystemSolutionERF tempERF = (FaultSystemSolutionERF)erf;
//		InversionFaultSystemSolution invSol = (InversionFaultSystemSolution)tempERF.getSolution();
//		double minMag = 2.05;
//		double maxMag = 8.95;
//		int numMag = 70;
//		List<GutenbergRichterMagFreqDist> subSeisMFD_List = invSol.getFinalSubSeismoOnFaultMFD_List();
//		List<IncrementalMagFreqDist> supraSeisMFD_List = invSol.getFinalSupraSeismoOnFaultMFD_List(minMag, maxMag, numMag);
////		for(int s=0;s<invSol.getRupSet().getNumSections(); s++) {
////			System.out.println(s+"\t"+invSol.getRupSet().getFaultSectionData(s).getName());
////		}
//		
//		ArrayList<IncrementalMagFreqDist> mfdList = new ArrayList<IncrementalMagFreqDist>();
//		SummedMagFreqDist[] tdMFD_Array = FaultSysSolutionERF_Calc.calcNucleationMFDForAllSects(tempERF, minMag, maxMag, numMag);
//		SummedMagFreqDist[] tiMFD_Array = FaultSysSolutionERF_Calc.calcTimeIndNucleationMFDForAllSects(tempERF, minMag, maxMag, numMag);
//		int parkfieldIndex = 1923;
//		mfdList.add(supraSeisMFD_List.get(parkfieldIndex));
//		mfdList.add(tdMFD_Array[parkfieldIndex]);
//		mfdList.add(tiMFD_Array[parkfieldIndex]);
//		mfdList.add(subSeisMFD_List.get(parkfieldIndex));
//		GraphWindow mfd_Graph = new GraphWindow(mfdList, "MFDs"); 
//		mfd_Graph.setX_AxisLabel("Mag");
//		mfd_Graph.setY_AxisLabel("Rate");
//		mfd_Graph.setYLog(true);
//		mfd_Graph.setPlotLabelFontSize(22);
//		mfd_Graph.setAxisLabelFontSize(20);
//		mfd_Graph.setTickLabelFontSize(18);			
////		System.out.println(parkfieldIndex+"\t"+invSol.getRupSet().getFaultSectionData(parkfieldIndex).getName());
////		System.out.println(supraSeisMFD_List.get(parkfieldIndex).toString());
////		System.exit(-1);
		
		
		

		return erf;
	}
	
	
	public static void correctGriddedSeismicityRatesInERF(FaultSystemSolutionERF erf, boolean plotRateRatio) {
		double[] gridSeisCorrValsArray;
		try {
			gridSeisCorrValsArray = MatrixIO.doubleArrayFromFile(new File(ETAS_PrimaryEventSampler.defaultGriddedCorrFilename));
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		correctGriddedSeismicityRatesInERF(erf.getSolution(), plotRateRatio, gridSeisCorrValsArray);
	}
	
	public static void correctGriddedSeismicityRatesInERF(FaultSystemSolution sol, boolean plotRateRatio,
			double[] gridSeisCorrValsArray) {
		GridSourceFileReader gridSources = (GridSourceFileReader)sol.getGridSourceProvider();
		gridSources.scaleAllNodeMFDs(gridSeisCorrValsArray);
		
		double totalRate=0;
		double[] nodeRateArray = new double[gridSources.size()];
		for(int i=0;i<nodeRateArray.length;i++) {
			nodeRateArray[i] = gridSources.getNodeMFD(i).getCumRate(2.55);
			totalRate+=nodeRateArray[i];
		}
				
		double[] nodeRatePDF = new double[gridSources.size()];
		for(int i=0;i<nodeRateArray.length;i++) {
			nodeRatePDF[i] = nodeRateArray[i]/totalRate;
		}

		GriddedSeisUtils griddedSeisUtils = new GriddedSeisUtils(sol.getRupSet().getFaultSectionDataList(), nodeRatePDF, InversionTargetMFDs.FAULT_BUFFER);
		
		List<? extends IncrementalMagFreqDist> longTermSubSeisMFD_OnSectList = sol.getSubSeismoOnFaultMFD_List();
		
		double[] oldRateArray = new double[longTermSubSeisMFD_OnSectList.size()];
		double[] newRateArray = new double[longTermSubSeisMFD_OnSectList.size()];
		double[] ratioArray = new double[longTermSubSeisMFD_OnSectList.size()];

		for(int sectIndex=0; sectIndex<longTermSubSeisMFD_OnSectList.size(); sectIndex++) {
			
			oldRateArray[sectIndex] = longTermSubSeisMFD_OnSectList.get(sectIndex).getCumRate(2.55);
			newRateArray[sectIndex] = totalRate*griddedSeisUtils.pdfValForSection(sectIndex);
			if(oldRateArray[sectIndex] > 0.0)
				ratioArray[sectIndex] = newRateArray[sectIndex]/oldRateArray[sectIndex];
			else
				ratioArray[sectIndex] = 1.0;
			
			if (D) System.out.println(newRateArray[sectIndex]+"\t"+oldRateArray[sectIndex]+"\t"+ratioArray[sectIndex]+"\t"+sol.getRupSet().getFaultSectionData(sectIndex).getName());
			
			longTermSubSeisMFD_OnSectList.get(sectIndex).scale(ratioArray[sectIndex]);
						
		}
		
		if(plotRateRatio) {
			List<FaultSectionPrefData> faults = sol.getRupSet().getFaultSectionDataList();
			String name = "SubSeisRateChange";
			String title = "SubSeisRateChange";
			CPT cpt= FaultBasedMapGen.getLinearRatioCPT().rescale(0, 10);
			
			try {
				File file = new File("SubSeisRateChange");
				if(!file.exists())
					file.mkdir();
				FaultBasedMapGen.makeFaultPlot(cpt, FaultBasedMapGen.getTraces(faults), ratioArray, gridSources.getGriddedRegion(), file, name, true, false, title);
			} catch (GMT_MapException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (RuntimeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		
		

	}
	
	
	/**
	 * Test scenarios
	 *
	 */
	public enum TestScenario {
		// IMPORTANT: DO NOT REMOVE OR RENAME SCENARIOS from this list if we have used them in the past and want figure reproducibility.
		// these are used by the plotting code.
		MOJAVE_M7pt4("MojaveM7.4", 193830),		// SourceIndex=193816	fssIndex=193830	Inversion Src #193830; 18 SECTIONS BETWEEN San Andreas (San Bernardino N), Subsection 2 AND San Andreas (Mojave S), Subsection 0	mag=7.391156552897243
		MOJAVE_M7pt8("MojaveM7.8", 18366),
		MOJAVE_M7("MojaveM7", 193821),		// 193807	Inversion Src #193821; 9 SECTIONS BETWEEN San Andreas (San Bernardino N), Subsection 2 AND San Andreas (Mojave S), Subsection 9: writeInfoAboutSourceWithThisFirstAndLastSection(getU3_ETAS_ERF(), 1846, 1946); & the other write method here
		MOJAVE_M7_ALT("MojaveM7_Alt", 195766),		// Inversion Src #195766; 9 SECTIONS BETWEEN San Andreas (Mojave S), Subsection 4 AND San Andreas (Mojave S), Subsection 12 (away from branching faults, but has a MFD spike near 6.3 at one end)
		MOJAVE_M6pt3_FSS("MojaveM6.3_FSS", 195759),	// 		subsections 11 and 12
		MOJAVE_M6pt3_ptSrc("MojaveM6.3_PtSrc", new Location(34.42295,-117.80177,5.8), 6.3),	// on Mojave subsection 10 (id=1847), and very close to subsection 11 (id=1848)
		MOJAVE_M7pt5_ptSrc("MojaveM7.5_PtSrc", new Location(34.42295,-117.80177,5.8), 7.5),	// 
		MOJAVE_M5p5("MojaveM5.5", new Location(34.42295,-117.80177,5.8), 5.5),	// on Mojave subsection 10 (id=1847), and very close to subsection 11 (id=1848)
		MOJAVE_M5p5_SECT1850("Mojave_M5.5_Sect1850", new Location(34.354,-117.639,6.5500), 5.5), // Location at center of San Andreas (Mojave S), Subsection 13	34.35453627367332, -117.63914790390031, 6.550000190734863
		MOJAVE_M5p5_2kmAway("MojaveM5.5_2kmAway", LocationUtils.location(new Location(34.42295,-117.80177,5.8), new LocationVector((295.037-270.0), 2.0, 0.0)), 5.5),	//
		MOJAVE_M5p5_5kmAway("MojaveM5.5_5kmAway", LocationUtils.location(new Location(34.42295,-117.80177,5.8), new LocationVector((295.037-270.0), 5.0, 0.0)), 5.5),	//
		MOJAVE_M5("MojaveM5", new Location(34.42295,-117.80177,5.8), 5.0),	// on Mojave subsection 10 (id=1847), and very close to subsection 11 (id=1848)
		N_PALM_SPRINGS_1986("N Palm Springs M6.0", new Location(34.02,-116.76,10.0), 6.0),		// original provided by Kevin
		MOJAVE_OLD("Mojave M7.05", 197792),		// original provided by Kevin
		LANDERS("Landers", 246711),			// found by running: writeInfoAboutSourceWithThisFirstAndLastSection(erf, 243, 989);
		NORTHRIDGE("Northridge", 187455),	// found by running: writeInfoAboutSourceWithThisFirstAndLastSection(erf, 1409, 1413);
		LA_HABRA_6p2("La Habra 6.2", new Location(33.932,-117.917,4.8), 6.2),
		SURPRISE_VALLEY_5p0("SurpriseValley5pt0", new Location(41.83975, -120.12356, 4.6750), 5.0), // Locationat center of Surprise Valley 2011 CFM, Subsection 14	41.83975, -120.12356, 4.675
		SURPRISE_VALLEY_5p5("SurpriseValley5pt5", new Location(41.83975, -120.12356, 4.6750), 5.5),	// found with writeLocationAtCenterOfSectionSurf(erf, 2460);  2nd highest Charfactor
		NEAR_MAACAMA("Near Maacama", new Location(39.79509, -123.56665-0.04, 7.54615), 7.0),
		ON_MAACAMA("On Maacama", new Location(39.79509, -123.56665, 7.54615), 7.0),
		ON_N_MOJAVE("On N Mojave", getMojaveTestLoc(0.0), 6.0),	// on N edge of the Mojave scenario
		NEAR_N_MOJAVE_3KM("On N Mojave", getMojaveTestLoc(3.0), 5.0),	// on N edge of the Mojave scenario
		CUSTOM("Custom (will prompt)", new Location(34, -118), 5d), // will prompt when built, these are just defaults
		NAPA("Napa 6.0", 93902, null, 6d), // supra-seismogenic rup that is a 6.3 in U3, overridden to be a 6.0
		PARKFIELD("Parkfield M6", 30473), // Parkfield M6 fault based rup
		BOMBAY_BEACH_M6("Bombay Beach M6", new Location(33.3183,-115.7283,5.8), 6.0), // Bombay Beach M6 in location of 2009 M4.8
		// From Felzer appendix: 2009   3 24 11 55 43.9300 33.3172 -115.728 5.96 4.96 7.00 2.00 0.09 0.01; Andy's paper says it's M 4.8
		BOMBAY_BEACH_M4pt8("Bombay Beach M4.8", new Location(33.3172,-115.728, 5.96), 4.8), // Bombay Beach M6 in location of 2009 M4.8
		ROBINSON_CREEK_M5p5("Robinson Creek M5pt5", new Location(38.22137, -119.24255, 7.15), 5.5),  // based on writeLocationAtCenterOfSectionSurf(erf, 1717);	// Robinson Creek Subsection 0; highest Charfactor
		CENTRAL_VALLEY_M3("Central Valley M3", new Location(37.622,-119.993,5.8), 3.0), // Central Valley - farthest from faults
		CENTRAL_VALLEY_M5p0("Central Valley M5", new Location(37.622,-119.993,5.8), 5.0), // Central Valley - farthest from faults
		ZAYANTE_VERGELES_M5p5("Zayante-Vergeles M5pt5", new Location(36.71779, -121.59369, 6.49000), 5.5), // writeLocationAtCenterOfSectionSurf(erf, 2605);	// Zayante-Vergeles 2011 CFM, Subsection 7; 36.71779, -121.59369, 6.49000; lowest char factor on more than one section
		SAN_JACINTO_0_M4p8("San Jacinto (Borrego) M4pt8", new Location(33.1917, -116.17999, 7.41061), 4.8),	// San Jacinto (Borrego), Subsection 0	33.1917, -116.17999, 7.41061
		SAN_JACINTO_0_M5p5("San Jacinto (Borrego) M5pt5", new Location(33.1917, -116.17999, 7.41061), 5.5),	// San Jacinto (Borrego), Subsection 0	33.1917, -116.17999, 7.41061
		MENDOCINO_12_M5p5("Mendocino M5pt5", new Location(40.38540, -125.01342, 6.0), 5.5),	// Mendocino, Subsection 12	40.38540, -125.01342, 6.0
		SAF_PENINSULA_M5p5("SAF_PeninsulaM5pt5", new Location(37.72793, -122.54861, 7.0), 5.5),  // San Andreas (Peninsula) 2011 CFM, Subsection 12	37.72793, -122.54861, 7.0
		SAF_PENINSULA_M6p3("SAF_PeninsulaM6pt3", 122568),  // Inversion Src #122568; 2 SECTIONS BETWEEN San Andreas (Peninsula) 2011 CFM, Subsection 12 AND San Andreas (Peninsula) 2011 CFM, Subsection 11
		SAF_PENINSULA_M7("SAF_PeninsulaM7", 119367),  // Inversion Src #119367; 9 SECTIONS BETWEEN San Andreas (North Coast) 2011 CFM, Subsection 1 AND San Andreas (Peninsula) 2011 CFM, Subsection 9
		HAYWIRED_M7("HaywiredM7pt1", 101499);  // SourceIndex=101485	Inversion Src #101499; 14 SECTIONS BETWEEN Hayward (So) 2011 CFM, Subsection 2 AND Hayward (No) 2011 CFM, Subsection 7	mag=7.09
		// IMPORTANT: DO NOT REMOVE OR RENAME SCENARIOS from this list if we have used them in the past and want figure reproducibility.
		// these are used by the plotting code.
				
		private String name;
		private int fssIndex;
		private Location loc;
		private double mag;
		private TestScenario(String name, int fssIndex) {
			this(name, fssIndex, null, Double.NaN);
		}
		
		private TestScenario(String name, Location loc, double mag) {
			this(name, -1, loc, mag);
		}
		
		private TestScenario(String name, int fssIndex, Location loc, double mag) {
			this.fssIndex = fssIndex;
			this.name = name;
			this.loc = loc;
			this.mag = mag;
			Preconditions.checkState(loc != null || fssIndex >= 0);
			if (fssIndex >= 0)
				Preconditions.checkState(loc == null);
			else
				Preconditions.checkState(loc != null);
		}
		
		@Override
		public String toString() {
			return name;
		}
		
		public int getFSS_Index() {return fssIndex;}
		
		public void setFSS_Index(int fssIndex) {
			this.fssIndex = fssIndex;
		}
		
		public Location getLocation() {return loc;}
		
		public double getMagnitude() {return mag;}
		
		public void updateMag(double mag) {
			this.mag = mag;
		}
		
		public void relocatePtAwayFromFault(FaultSystemRupSet rupSet, double distAway) {
			// find closest subsection
			FaultSectionPrefData closest = null;
			double closestDist = Double.MAX_VALUE;
			for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
				// just use fault trace for efficiency
				for (Location loc : sect.getFaultTrace()) {
					double dist = LocationUtils.horzDistanceFast(loc, this.loc);
					if (dist < closestDist) {
						closestDist = dist;
						closest = sect;
						// don't break, other trace locs might be even closer
					}
				}
			}
			Preconditions.checkNotNull(closest);
			System.out.println("Cloasest section: "+closest.getName()+". Trace dist: "+closestDist);
			relocatePtAwayFromFault(closest, distAway);
		}
		
		public void relocatePtAwayFromFault(FaultSectionPrefData closestSect, double distAway) {
			double strike = closestSect.getFaultTrace().getAveStrike();
			double strikeRad = Math.toRadians(strike);
			
			// add pi/2 to move perpendicular from the fault
			double azimuth = strikeRad + Math.PI*0.5;
			
			Location newLoc = LocationUtils.location(loc, azimuth, distAway);
			
			StirlingGriddedSurface surf = closestSect.getStirlingGriddedSurface(0.1d);
			double origDist = Double.POSITIVE_INFINITY;
			double newDist = Double.POSITIVE_INFINITY;
			for (Location surfLoc : surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
				origDist = Math.min(LocationUtils.linearDistanceFast(surfLoc, loc), origDist);
				newDist = Math.min(LocationUtils.linearDistanceFast(surfLoc, newLoc), newDist);
			}
			double delta = newDist - origDist;
			System.out.println("Attempted to move away by "+distAway+" from "+closestSect.getName()
					+". Actual: "+origDist+" => "+newDist+" = "+delta);
			System.out.println("\tOrig Loc: "+loc);
			System.out.println("\tNew Loc: "+newLoc);
			System.out.println("\tStrike: "+strike);
			System.out.println("\tMoved in direction: "+LocationUtils.azimuth(loc, newLoc));
			System.out.println("\tMoved distance: "+LocationUtils.linearDistanceFast(loc, newLoc));
			loc = newLoc;
		}
	}

	
	public static Location getMojaveTestLoc(double horzDist) {
		Location loc = new Location(34.698495, -118.508948, 6.550000191);
		if(horzDist == 0.0)
			return loc;
		else {
			LocationVector vect = new LocationVector((295.037-270.0), horzDist, 0.0);
			return LocationUtils.location(loc, vect);			
		}
	}
	
	/**
	 *  A temporary method to see if switching back and forth from Poisson to Time Dependent effects
	 *  parameter values (e.g., start time and duration).
	 *  
	 * @param erf
	 */
	public static void testERF_ParamChanges(FaultSystemSolutionERF_ETAS erf) {
		
		ArrayList paramValueList = new ArrayList();
		System.out.println("\nOrig ERF Adjustable Paramteres:\n");
		for(Parameter param : erf.getAdjustableParameterList()) {
			System.out.println("\t"+param.getName()+" = "+param.getValue());
			paramValueList.add(param.getValue());
		}
		TimeSpan tsp = erf.getTimeSpan();
		String startTimeString = tsp.getStartTimeMonth()+"/"+tsp.getStartTimeDay()+"/"+tsp.getStartTimeYear()+"; hr="+tsp.getStartTimeHour()+"; min="+tsp.getStartTimeMinute()+"; sec="+tsp.getStartTimeSecond();
		double duration = erf.getTimeSpan().getDuration();
		System.out.println("\tERF StartTime: "+startTimeString);
		System.out.println("\tERF TimeSpan Duration: "+duration+" years");
		paramValueList.add(startTimeString);
		paramValueList.add(duration);
		int numParams = paramValueList.size();
		
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		System.out.println("\nPois ERF Adjustable Paramteres:\n");
		for(Parameter param : erf.getAdjustableParameterList()) {
			System.out.println("\t"+param.getName()+" = "+param.getValue());
		}
		tsp = erf.getTimeSpan();
		startTimeString = tsp.getStartTimeMonth()+"/"+tsp.getStartTimeDay()+"/"+tsp.getStartTimeYear()+"; hr="+tsp.getStartTimeHour()+"; min="+tsp.getStartTimeMinute()+"; sec="+tsp.getStartTimeSecond();
		System.out.println("\tERF StartTime: "+startTimeString);
		System.out.println("\tERF TimeSpan Duration: "+erf.getTimeSpan().getDuration()+" years");

		
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
		erf.updateForecast();
		System.out.println("\nFinal ERF Adjustable Paramteres:\n");
		int testNum = erf.getAdjustableParameterList().size()+2;
		if(numParams != testNum) {
			System.out.println("PROBLEM: num parameters changed:\t"+numParams+"\t"+testNum);
		}
		int i=0;
		for(Parameter param : erf.getAdjustableParameterList()) {
			System.out.println("\t"+param.getName()+" = "+param.getValue());
			if(param.getValue() != paramValueList.get(i))
				System.out.println("PROBLEM: "+param.getValue()+"\t"+paramValueList.get(i));
			i+=1;
		}
		tsp = erf.getTimeSpan();
		double duration2 = erf.getTimeSpan().getDuration();
		String startTimeString2 = tsp.getStartTimeMonth()+"/"+tsp.getStartTimeDay()+"/"+tsp.getStartTimeYear()+"; hr="+tsp.getStartTimeHour()+"; min="+tsp.getStartTimeMinute()+"; sec="+tsp.getStartTimeSecond();
		System.out.println("\tERF StartTime: "+startTimeString2);
		System.out.println("\tERF TimeSpan Duration: "+duration2+" years");
		if(!startTimeString2.equals(startTimeString))
			System.out.println("PROBLEM: "+startTimeString2+"\t"+startTimeString2);
		if(duration2 != duration)
			System.out.println("PROBLEM Duration: "+duration2+"\t"+duration);


	}
	
	/**
	 * 
	 * @param label - plot label
	 * @param local - whether GMT map is made locally or on server
	 * @param dirName
	 * @return
	 */
	public static void plotERF_RatesMap(FaultSystemSolutionERF_ETAS erf, String dirName) {
		
		CaliforniaRegions.RELM_TESTING_GRIDDED mapGriddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
		GriddedGeoDataSet xyzDataSet = ERF_Calculator.getNucleationRatesInRegion(erf, mapGriddedRegion, 0d, 10d);
		
		if(D) 
			System.out.println("OrigERF_RatesMap: min="+xyzDataSet.getMinZ()+"; max="+xyzDataSet.getMaxZ());
		
		String metadata = "Map from calling plotOrigERF_RatesMap() method";
		
		GMT_MapGenerator gmt_MapGenerator = GMT_CA_Maps.getDefaultGMT_MapGenerator();
		
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -3.5);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, 1.5);
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		cptParam.getValue().setBelowMinColor(Color.WHITE);

		try {
			GMT_CA_Maps.makeMap(xyzDataSet, "OrigERF_RatesMap", metadata, dirName, gmt_MapGenerator);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}


	/**
	 * This shows that El Mayor Cucapah reset Laguna Salada subsections 0-12 (leaving 13 and 14) according to UCERF3 rules.
	 * @param erf
	 */
	private static void plotElMayorAndLagunaSalada(FaultSystemSolutionERF_ETAS erf) {
		ObsEqkRupList histCat = getHistCatalogFiltedForStatewideCompleteness(2012,erf.getSolution().getRupSet());
		// this shows that the ID for El Mayor is 4552
//		for(int i=0;i<histCat.size();i++)
//			if(histCat.get(i).getMag()>7) {
//				double year = ((double)histCat.get(i).getOriginTime())/(365.25*24*3600*1000)+1970.0;
//				System.out.println(i+"\t"+histCat.get(i).getMag()+"\t"+year);
//			}
//		System.exit(-1);

		LocationList locListForElMayor = histCat.get(4552).getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
		DefaultXY_DataSet elMayorXYdata = new DefaultXY_DataSet();
		for(Location loc:locListForElMayor)
			elMayorXYdata.set(loc.getLongitude(), loc.getLatitude());
		
		ArrayList<XY_DataSet> funcList = new ArrayList<XY_DataSet>();
		funcList.add(elMayorXYdata);
		ArrayList<PlotCurveCharacterstics> plotCharList = new ArrayList<PlotCurveCharacterstics>();
		plotCharList.add(new PlotCurveCharacterstics(PlotSymbol.BOLD_CROSS, 1f, Color.RED));

		FaultSystemRupSet rupSet = ((FaultSystemSolutionERF)erf).getSolution().getRupSet();
		FaultPolyMgr faultPolyMgr = FaultPolyMgr.create(rupSet.getFaultSectionDataList(), InversionTargetMFDs.FAULT_BUFFER);	// this works for U3, but not generalized

		for(int i=1042;i<=1056;i++) {
			DefaultXY_DataSet lagunaSaladaPolygonsXYdata = new DefaultXY_DataSet();
			FaultSectionPrefData fltData = rupSet.getFaultSectionData(i);
			System.out.println(fltData.getName());
			Region polyReg = faultPolyMgr.getPoly(i);
			for(Location loc : polyReg.getBorder()) {
				lagunaSaladaPolygonsXYdata.set(loc.getLongitude(), loc.getLatitude());
			}
			funcList.add(lagunaSaladaPolygonsXYdata);
			plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
		}
		
		GraphWindow graph = new GraphWindow(funcList, "El Mayor and Laguna Salada", plotCharList); 
		graph.setX_AxisLabel("Longitude");
		graph.setY_AxisLabel("Latitude");


		
	}

	
	
	public static void tempTestGainResult() {
		
		FaultSystemSolutionERF_ETAS erf = getU3_ETAS_ERF(2014,10.0);	
		double[] td_rates = FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf, 6.7);
		
		
		// this will reset sections involved in scenario
		buildScenarioRup(TestScenario.MOJAVE_M7, erf);
		double[] td_postScen_rates = FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf, 6.7);

		
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		double[] ti_rates = FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf, 6.7);

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		for(int i=0;i<ti_rates.length;i++)
			System.out.println(td_rates[i]+"\t"+td_postScen_rates[i]+"\t"+ti_rates[i]+"\t"+rupSet.getFaultSectionData(i).getName());

	}

	


	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
//		tempTestGainResult();
//		System.exit(0);

		
//		FaultSystemSolutionERF_ETAS erf_test = getU3_ETAS_ERF(2014,1);
//		GriddedRegion griddedRegion = new CaliforniaRegions.RELM_TESTING_GRIDDED(0.1);
//		System.out.println("Making Data");
//		GriddedGeoDataSet data = FaultSysSolutionERF_Calc.calcParticipationProbInGriddedRegionFltMapped(erf_test, griddedRegion, 6.7, 10.0);
//		for(int i=0;i<data.size();i++)
//			data.set(i, Math.log10(data.get(i)));
//		try {
//			System.out.println("Making Plot");
//			CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
//			double minValue = -7;
//			double maxValue = -2;
//			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(data, griddedRegion, new File("test2_090216"), "testPlot2", 
//					true, cpt, minValue, maxValue, true);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		System.exit(0);

		
//		// Haywired scenario:
//		writeInfoAboutSourceWithThisFirstAndLastSection(getU3_ETAS_ERF(2014,1.0),825,830);
//		System.exit(0);
		
//		FaultSystemSolutionERF_ETAS erf = getU3_ETAS_ERF(2014,10.0);	
//		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
//		erf.updateForecast();
//		
//		writeTotRateRupOccurOnTheseTwoSections(erf, 1837, 2183);
//		System.exit(-1);
		
//		plotElMayorAndLagunaSalada(erf);

//		plotERF_RatesMap(erf, "testBeforeCorr");
//		correctGriddedSeismicityRatesInERF(erf, true);
//		plotERF_RatesMap(erf, "testAfterCorr");
		
//		double[] vals = FaultSysSolutionERF_Calc.tempCalcParticipationRateForAllSects(erf);
//		for(int s=0;s<vals.length;s++)
//			System.out.println(s+"\t"+vals[s]+"\t"+erf.getSolution().getRupSet().getFaultSectionData(s).getName());
//		System.exit(0);
		
		
//		writeLocationAtCenterOfSectionSurf(erf, 1850);	// Mojave
//		writeLocationAtCenterOfSectionSurf(erf, 1717);	// Robinson Creek Subsection 0
//		writeLocationAtCenterOfSectionSurf(erf, 2460);	// Suprise Valley Subsection 14
//		writeLocationAtCenterOfSectionSurf(erf, 2605);	// Zayante-Vergeles 2011 CFM, Subsection 7
//		writeLocationAtCenterOfSectionSurf(erf, 2159);	// San Jacinto (Borrego), Subsection 0	33.19172092060858, -116.1799928635995, 7.4106132512450795
//		writeLocationAtCenterOfSectionSurf(erf, 1259);	// Mendocino, Subsection 12	40.38540306568454, -125.01342272786124, 6.0
//		writeLocationAtCenterOfSectionSurf(erf, 1940);	// San Andreas (Peninsula) 2011 CFM, Subsection 12	37.72792670654364, -122.54861017376626, 7.0
//		writeInfoAboutSourcesThatUseSection(erf, 1940, 5d, 7.1);
//		System.exit(0);
		
//		writeInfoAboutSourceWithThisFirstAndLastSection(getU3_ETAS_ERF(2014,1.0),1841,1849);
		
//		writeInfoAboutSourcesThatUseSection(getU3_ETAS_ERF(2012.0,1.0), 1850, 6, 7);
//		System.exit(0);
		
//		plotCatalogMagVsTime(getHistCatalog(2012, erf.getSolution().getRupSet()).getRupsInside(new CaliforniaRegions.SF_BOX()), "U3_EqkCatalogMagVsTimePlot");
//		System.exit(0);

		TestScenario scenario = TestScenario.PARKFIELD;
//		TestScenario scenario = null;
		
//		writeInfoAboutClosestSectionToLoc(erf, scenario.getLocation());
//		System.exit(0);
		
		ETAS_ParameterList params = new ETAS_ParameterList();
		params.setImposeGR(false);	
		params.setApplyGridSeisCorr(true);
		params.setApplySubSeisForSupraNucl(true);
		params.setTotalRateScaleFactor(1.0);
		params.setU3ETAS_ProbModel(U3ETAS_ProbabilityModelOptions.NO_ERT);
//		params.setTotalRateScaleFactor(1.14);
//		params.setU3ETAS_ProbModel(U3ETAS_ProbabilityModelOptions.FULL_TD);
		
		String simulationName;
		String imposeGR_string="";
		if(params.getImposeGR())
			imposeGR_string = "_GRcorrApplied";
//		else {
//			imposeGR_string = "_noGRcorr";
//		}

		if(scenario == null)
			simulationName = "NoScenario_"+params.getU3ETAS_ProbModel()+imposeGR_string;
		else
			simulationName = scenario+"_"+params.getU3ETAS_ProbModel()+imposeGR_string;

		simulationName += "_NoSpontaneous";	// to increment runs

		Long seed = null;
//		Long seed = 1449590752534l;
//		Long seed = 1444170206879l;
//		Long seed = 1439486175712l;
		
//		double startTimeYear=2012;
//		double durationYears=10;
		double startTimeYear=2014;
		double durationYears=7.0/365.25;
		
		ObsEqkRupList histCat = null;
//		ObsEqkRupList histCat = getHistCatalog(startTimeYear);
//		ObsEqkRupList histCat = getHistCatalogFiltedForStatewideCompleteness(startTimeYear,erf.getSolution().getRupSet());

		runTest(scenario, params, seed, simulationName, histCat, startTimeYear, durationYears);
		
		
		
		
//		Location testLoc = new Location(32.57336, -118.25770, 9.05759);
//		CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
//		System.out.println(griddedRegion.indexForLocation(testLoc));
//		System.out.println(griddedRegion.contains(testLoc));
		
//		for(int i=0;i<griddedRegion.getNodeCount();i++) {
//			Location loc = griddedRegion.getLocation(i);
//			System.out.println(loc.getLongitude()+"\t"+loc.getLatitude());
//		}
		
//		for(Location loc:griddedRegion.getBorder()) {
//			System.out.println(loc.getLongitude()+"\t"+loc.getLatitude());
//		}
		

		
//		BETTER MOJAVE SCENARIO:
//		writeInfoAboutSourcesThatUseSection(getU3_ETAS_ERF(), 1850, 7.0, 7.2);
		// M7:
//		writeInfoAboutSourceWithThisFirstAndLastSection(getU3_ETAS_ERF(startTimeYear, durationYears), 1846, 1946);
		// M7.4
//		writeInfoAboutSourceWithThisFirstAndLastSection(getU3_ETAS_ERF(startTimeYear, durationYears), 1837, 1946);
		// SourceIndex=18366	fssIndex=18366	Inversion Src #18366; 38 SECTIONS BETWEEN San Andreas (Carrizo) rev, Subsection 0 AND San Andreas (San Bernardino N), Subsection 2	mag=7.796:7
//		writeInfoAboutSourceWithThisFirstAndLastSection(getU3_ETAS_ERF(startTimeYear, durationYears), 1779, 1946);
//
//		System.exit(-1);
		
		
//		params.setApplyLongTermRates(false);
//		params.set_d_MinDist(2.0);

//		params.setImposeGR(true);		
//		runTest(TestScenario.MOJAVE, params, null, "Mojave_M7_grCorr", null);	// aveStrike=295.0367915096109
		
		// need to set APPLY_ERT = false in ETAS_PrimaryEventSampler
//		runTest(TestScenario.MOJAVE, params, null, "Mojave_M7_noERT", null);	// aveStrike=295.0367915096109

		// need to set APPLY_ERT = false in ETAS_PrimaryEventSampler
//		params.setImposeGR(true);		
//		runTest(TestScenario.MOJAVE, params, null, "Mojave_M7_noERT_grCorr", null);	// aveStrike=295.0367915096109

		// be sure to make the erf Poisson in runTest (code commented out)
//		runTest(TestScenario.MOJAVE, params, null, "Mojave_M7_Poisson", null);	// aveStrike=295.0367915096109

//		runTest(TestScenario.MOJAVE, params, null, "Mojave_M7", null);	// aveStrike=295.0367915096109

//		runTest(TestScenario.KEVIN_MOJAVE, params, null, "Mojave_M6", null);	// aveStrike=295.0367915096109; All Hell!

//		params.setImposeGR(true);			
//		runTest(TestScenario.KEVIN_MOJAVE, params, null, "Mojave_M6_grCorr", null);	// aveStrike=295.0367915096109; All Hell!

		
		
		
		//		runTest(TestScenario.NEAR_MAACAMA, params, new Long(1407965202664l), "nearMaacama_1", null);
//		runTest(TestScenario.ON_MAACAMA, params, new Long(1407965202664l), "onMaacama_1", null);
		
//		runTest(TestScenario.ON_N_MOJAVE, params, new Long(1407965202664l), "OnN_Mojave_2", null);
//		runTest(TestScenario.NEAR_N_MOJAVE_3KM, params, new Long(1407965202664l), "NearN_Mojave_3KM_1", null);
//		runTest(TestScenario.LA_HABRA_6p2, params, null, "LaHabraTest_1", null);
//		runTest(null, params, null, "NoMainshockTest_1", null);
//		runTest(null, params, null, "HistCatalogTest_2", getHistCatalog());
//		runTest(TestScenario.NAPA, params, 1409022950070l, "Napa failure", null);
//		runTest(TestScenario.NAPA, params, 1409243011639l, "NapaEvent_noSpont_uniform_2", null);
//		runTest(TestScenario.NAPA, params, 1409709441451l, "NapaEvent_maxLoss", null);
//		runTest(TestScenario.NAPA, params, 1409709441451l, "NapaEvent_test ", null);
//		runTest(TestScenario.MOJAVE, params, new Long(14079652l), "MojaveEvent_2", null);	// aveStrike=295.0367915096109; All Hell!
//		runTest(TestScenario.MOJAVE, params, null, "MojaveEvent_New_5", null);	// aveStrike=295.0367915096109; All Hell!
//		runTest(TestScenario.MOJAVE, params, 1433367544567l, "MojaveEvent_newApproach", null);	// aveStrike=295.0367915096109; All Hell!
//		runTest(TestScenario.NORTHRIDGE, params, null, "Northridge_1", null);
//		runTest(TestScenario.LANDERS, params, null, "Landers_5", null);
//		runTest(TestScenario.NEAR_SURPRISE_VALLEY_5p0, params, null, "NearSurpriseValley5p0_1", null);	// aveStrike=295.0367915096109

//		runTest(TestScenario.KEVIN_MOJAVE, params, null, "KevinTestMojave_5_subsectResetBoth", null);	// aveStrike=295.0367915096109;
//		runTest(TestScenario.KEVIN_MOJAVE, params, null, "KevinTestMojave_4_grCorr", null);	// aveStrike=295.0367915096109;
//		runTest(TestScenario.N_PALM_SPRINGS_1986, params, null, "NorthPalmSprings1986", null);	// aveStrike=295.0367915096109;
		
//		int distAway = 12;
//		try {
//			exit_after_scenario_diagnostics = true;
//			TestScenario.KEVIN_MOJAVE.relocatePtAwayFromFault(
//					FaultSystemIO.loadRupSet(new File("dev/scratch/UCERF3/data/scratch/InversionSolutions"
//							+ "/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_"
//							+ "MEAN_BRANCH_AVG_SOL.zip")), (double)distAway);
//		} catch (Exception e) {
//			ExceptionUtils.throwAsRuntimeException(e);
//		}
//		runTest(TestScenario.KEVIN_MOJAVE, params, null, "KevinTestMojave_"+distAway+"km", null);	// aveStrike=295.0367915096109;

		
//		runTest(TestScenario.PARKFIELD, params, new Long(14079652l), "ParkfieldTest_noSpnont_1", null);	// aveStrike=295.0367915096109
//		runTest(TestScenario.BOMBAY_BEACH_M6, params, new Long(14079652l), "BombayBeachTest_noSpnont_1", null);	// aveStrike=295.0367915096109


		// ************** OLD STUFF BELOW *********************
		
		
//		try {
//			runBugReproduce();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
//		CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
//		System.out.println(griddedRegion.getNumLocations());
//		
//		GriddedRegion region = new CaliforniaRegions.RELM_TESTING_GRIDDED();
//		System.out.println(region.getNumLocations());
//		System.exit(-1);
		
		
		// test to make sure M>2.5 events included:
//		SummedMagFreqDist mfd = ERF_Calculator.getTotalMFD_ForERF(erf, 0.05, 8.95, 90, true);
//		GraphWindow graph = new GraphWindow(mfd, "Test ERF MFD"); 
		
		// make bulge plots:
//		try {
////			GMT_CA_Maps.plotBulgeFromFirstGenAftershocksMap(erf, "1stGenBulgePlotCorrected", "test bulge", "1stGenBulgePlotCorrectedDir", true);
//			GMT_CA_Maps.plotBulgeFromFirstGenAftershocksMap(erf, "1stGenBulgePlot", "test bulge", "1stGenBulgePlotCorrDir", false);
////			FaultBasedMapGen.plotBulgeFromFirstGenAftershocksMap((InversionFaultSystemSolution)erf.getSolution(), griddedRegion, null, "testBulge", true, true);
////			FaultBasedMapGen.plotBulgeForM6pt7_Map((InversionFaultSystemSolution)erf.getSolution(), griddedRegion, null, "testBulge", true, true);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		System.exit(0);
				
//		// examine bulge reduction scaling factors
//		SummedMagFreqDist[] subMFD_Array = FaultSystemSolutionCalc.getSubSeismNucleationMFD_inGridNotes((InversionFaultSystemSolution)erf.getSolution(), griddedRegion);
//		SummedMagFreqDist[] supraMFD_Array = FaultSystemSolutionCalc.getSupraSeismNucleationMFD_inGridNotes((InversionFaultSystemSolution)erf.getSolution(), griddedRegion);
//		
//		double min=Double.MAX_VALUE;
//		int minIndex=-1;
//		for(int i=0;i<subMFD_Array.length ;i++) {
//			if(subMFD_Array[i] != null) {
//				double scaleFactor = ETAS_Utils.getScalingFactorToImposeGR(supraMFD_Array[i], subMFD_Array[i]);
//				if(scaleFactor<min) {
//					min = scaleFactor;
//					minIndex=i;
//				}
//			}
//		}
//		System.out.println("maxIndex="+minIndex+"; max="+min);
//		double minFactor = ETAS_Utils.getScalingFactorToImposeGR(supraMFD_Array[5739], subMFD_Array[5739]);
//		System.out.println("Location for min scaleFactor: "+griddedRegion.getLocation(5739)+"/tminFactor="+minFactor);
//		EvenlyDiscretizedFunc testMFD = new EvenlyDiscretizedFunc(2.55, 8.95, 65);
//		EvenlyDiscretizedFunc testMFDcorr = new EvenlyDiscretizedFunc(2.55, 8.95, 65);
//		SummedMagFreqDist tempMFD = new SummedMagFreqDist(2.55, 8.95, 65);
//		SummedMagFreqDist tempMFDcorr = new SummedMagFreqDist(2.55, 8.95, 65);
//		tempMFD.addIncrementalMagFreqDist(supraMFD_Array[5739]);
//		tempMFD.addIncrementalMagFreqDist(subMFD_Array[5739]);
//		tempMFDcorr.addIncrementalMagFreqDist(supraMFD_Array[5739]);
//		tempMFDcorr.scale(minFactor);
//		tempMFDcorr.addIncrementalMagFreqDist(subMFD_Array[5739]);
//		for(int i=0;i<testMFD.getNum();i++) {
//			testMFD.set(i, tempMFD.getY(i)*Math.pow(10d, testMFD.getX(i)));
//			testMFDcorr.set(i, tempMFDcorr.getY(i)*Math.pow(10d, testMFD.getX(i)));
//		}
//		testMFD.setName("testMFD");
//		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
//		funcs.add(testMFDcorr);
//		funcs.add(testMFD);
//		GraphWindow graph = new GraphWindow(funcs, "Test GR Corr"+" "); 

	

		
		
//		double minDist=Double.MAX_VALUE;
//		int minDistIndex=-1;
//		for(FaultSectionPrefData fltData:erf.getSolution().getRupSet().getFaultSectionDataList()){
//			double dist = fltData.getStirlingGriddedSurface(1.0, false, true).getDistanceRup(ptSurf);
//			if(dist<minDist) {
//				minDist=dist;
//				minDistIndex=fltData.getSectionId();
//			}
//		}
//		System.out.println("minDist="+minDist+"; minDistIndex="+minDistIndex);
//		FaultSectionPrefData fltData = erf.getSolution().getRupSet().getFaultSectionDataList().get(minDistIndex);
//		System.out.println(fltData.getName());
//		System.out.println(fltData.getStirlingGriddedSurface(1.0, false, true));

	}
}
