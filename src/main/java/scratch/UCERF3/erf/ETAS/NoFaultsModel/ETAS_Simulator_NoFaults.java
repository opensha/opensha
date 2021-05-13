package scratch.UCERF3.erf.ETAS.NoFaultsModel;

import java.awt.Color;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
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
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
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
import org.opensha.sha.earthquake.param.MaximumMagnitudeParam;
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
import com.google.common.collect.Range;
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
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CubeDiscretizationParams;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_LocationWeightCalculator;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools;
import scratch.UCERF3.erf.ETAS.ETAS_SimulationMetadata;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.FaultSystemSolutionERF_ETAS;
import scratch.UCERF3.erf.ETAS.SeisDepthDistribution;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools.EpicenterMapThread;
import scratch.UCERF3.erf.ETAS.association.FiniteFaultMappingData;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator;
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

public class ETAS_Simulator_NoFaults {
	
	public static boolean D=true; // debug flag
	private static boolean live_map = false;
	static boolean pause_for_events = false;
	// if true and in debug mode, will exit after scenario diagnostics
	static boolean exit_after_scenario_diagnostics = false;
	
	
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
	 * @param etasParams
	 * @return simulation metadata object
	 * @throws IOException
	 */
	public static ETAS_SimulationMetadata runETAS_Simulation(File resultsDir, AbstractNthRupERF erf,
			GriddedRegion griddedRegion, ETAS_EqkRupture scenarioRup, List<? extends ObsEqkRupture> histQkList, boolean includeSpontEvents,
			boolean includeIndirectTriggering, double gridSeisDiscr, String simulationName,
			Long randomSeed, ETAS_ParameterList etasParams, ETAS_CubeDiscretizationParams cubeParams)
					throws IOException {
		List<ETAS_EqkRupture> scenarioRups = null;
		if (scenarioRup != null) {
			scenarioRups = new ArrayList<>();
			scenarioRups.add(scenarioRup);
		}
		return runETAS_Simulation(resultsDir, erf, griddedRegion, scenarioRups, histQkList, includeSpontEvents,
				includeIndirectTriggering, gridSeisDiscr, simulationName, randomSeed, etasParams, cubeParams);
	}
	
	public static ETAS_SimulationMetadata runETAS_Simulation(File resultsDir, AbstractNthRupERF erf,
			GriddedRegion griddedRegion, List<ETAS_EqkRupture> scenarioRups, List<? extends ObsEqkRupture> histQkList, boolean includeSpontEvents,
			boolean includeIndirectTriggering, double gridSeisDiscr, String simulationName,
			Long randomSeed, ETAS_ParameterList etasParams, ETAS_CubeDiscretizationParams cubeParams)
					throws IOException {
		long simulationStartTime = System.currentTimeMillis();
		
		// etasParams.getU3ETAS_ProbModel() is ignored because no-faults assumes Poisson.
		
		boolean generateDiagnosticsForScenario = false;	// to be able to turn off even if in debug mode

		// set the random seed for reproducibility
		if (randomSeed == null)
			randomSeed = System.currentTimeMillis();
		ETAS_Utils etas_utils = new ETAS_Utils(randomSeed);
		
		// this could be input value
		SeisDepthDistribution seisDepthDistribution = new SeisDepthDistribution();
		
		// directory for saving results
		if(!resultsDir.exists()) resultsDir.mkdir();
		
		// set file for writing simulation info & write some preliminary stuff to it
		int bufferSize = 1000000;
		Writer info_fr = new BufferedWriter(new FileWriter(new File(resultsDir, "infoString.txt")), bufferSize);
		Writer simulatedEventsFileWriter = new BufferedWriter(new FileWriter(new File(resultsDir, "simulatedEvents.txt")), bufferSize);
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
		
		if (D) info_fr.flush();	// this writes the above out now in case of crash
		
		// Make the list of observed ruptures, plus scenario if that was included
		ArrayList<ETAS_EqkRupture> obsEqkRuptureList = new ArrayList<ETAS_EqkRupture>();
		
		Range<Integer> rangeHistCatalogParentIDs = null;
		if(histQkList != null) {
			// now start at 1, zero is reserved for scenarios if included
			int id;
			if (scenarioRups != null && !scenarioRups.isEmpty())
				id = scenarioRups.size();
			else
				id = 1;
			int startID = id;
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
			if (id > startID)
				rangeHistCatalogParentIDs = Range.closed(startID, id-1);
			System.out.println("histQkList.size()="+histQkList.size());
			System.out.println("obsEqkRuptureList.size()="+obsEqkRuptureList.size());
		}
		
		// add scenario rup to end of obsEqkRuptureList
		int[] scenarioRupIDs = null;
		Map<Integer, Integer> numPrimaryAshockForScenarios = null;
		Range<Integer> rangeTriggerRupIDs = null;
		if(scenarioRups != null && !scenarioRups.isEmpty()) {
//			scenarioRupID = obsEqkRuptureList.size();
			// zero is reserved for scenario rupture
			scenarioRupIDs = new int[scenarioRups.size()];
			numPrimaryAshockForScenarios = new HashMap<>();
			
			for (int i=0; i<scenarioRups.size(); i++) {
				ETAS_EqkRupture scenarioRup = scenarioRups.get(i);
				scenarioRupIDs[i] = i;
				scenarioRup.setID(i);
				obsEqkRuptureList.add(scenarioRup);		
				if(D) {
					System.out.println("Num locs on scenario rup surface: "
							+scenarioRup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface().size());
				}
			}
			rangeTriggerRupIDs = Range.closed(0, scenarioRups.size()-1);
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
//		info_fr.flush();
		
		
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
				System.out.println("ZERO RATE SOURCE: "+src.getRupture(0).getProbability()+"\t"+src.getName());
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
		if (cubeParams == null)
			cubeParams = new ETAS_CubeDiscretizationParams(griddedRegion);
		ETAS_PrimaryEventSampler_noFaults etas_PrimEventSampler = new ETAS_PrimaryEventSampler_noFaults(cubeParams, erf, sourceRates,
				null, etasParams, etas_utils);  // latter three may be null
		if(D) System.out.println("ETAS_PrimaryEventSampler creation took "+(float)(System.currentTimeMillis()-st)/60000f+ " min");
		info_fr.write("\nMaking ETAS_PrimaryEventSampler took "+(System.currentTimeMillis()-st)/60000+ " min");
//		info_fr.flush();
		
		// Make list of primary aftershocks for given list of obs quakes 
		// (filling in origin time ID, parentID, and location on parent that does triggering, with the rest to be filled in later)
		if (D) System.out.println("Making primary aftershocks from input obsEqkRuptureList, size = "+obsEqkRuptureList.size());
		PriorityQueue<ETAS_EqkRupture>  eventsToProcess = new PriorityQueue<ETAS_EqkRupture>(1000, oigTimeComparator);	// not sure about the first field
		int eventID = obsEqkRuptureList.size();	// start IDs after input events
		for(ETAS_EqkRupture parRup: obsEqkRuptureList) {
			int parID = parRup.getID();
//			if(parID != testParID) 
//				throw new RuntimeException("problem with ID");
			long rupOT = parRup.getOriginTime();
			double startDay = (double)(simStartTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;	// convert epoch to days from event origin time
			double endDay = (double)(simEndTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
			
			// set the etas parameters for this rupture (if not already set), and randomize k if it's not already set and kCOV>0
			etas_utils.setETAS_ParamsForRupture(parRup, etasParams);
			
			// get primary event times
			double[] randomAftShockTimes = etas_utils.getRandomEventTimes(parRup.getETAS_k(), parRup.getETAS_p(), parRup.getMag(), ETAS_Utils.magMin_DEFAULT, parRup.getETAS_c(), startDay, endDay);

			if (scenarioRupIDs != null && rangeTriggerRupIDs.contains(parRup.getID()))
				numPrimaryAshockForScenarios.put(parRup.getID(), randomAftShockTimes.length);
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
						Location tempLoc = etas_utils.getRandomLocationOnRupSurface(parRup);
						
//						// for no creep/aseis reduction:
//						if(locList==null) {	// make reusable location list
//							RuptureSurface surf = etas_utils.getRuptureSurfaceWithNoCreepReduction(parRup.getFSSIndex(), fssERF, 0.05);
//							locList = surf.getEvenlyDiscritizedListOfLocsOnSurface();							
//						}
//						Location tempLoc = locList.get(etas_utils.getRandomInt(locList.size()-1));
						
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
//		info_fr.flush();

		
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
			System.out.println("Starting getRandomSpontanousEventTimes");
			if (histQkList == null || histQkList.isEmpty())
				spontEventTimes = etas_utils.getRandomSpontanousEventTimes(mfd, histCatStartTime, simStartTimeMillis, simEndTimeMillis, 1000, 
						etasParams.get_k(), etasParams.get_p(), ETAS_Utils.magMin_DEFAULT, etasParams.get_c());
			else
				spontEventTimes = etas_utils.getRandomSpontanousEventTimes(
						mfd, etasParams.getStatewideCompletenessModel().getEvenlyDiscretizedMagYearFunc(), simStartTimeMillis, 
						simEndTimeMillis, 1000, etasParams.get_k(), etasParams.get_p(), ETAS_Utils.magMin_DEFAULT, etasParams.get_c());
			
			System.out.println("Done with getRandomSpontanousEventTimes");

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
//			info_fr.flush();
		}
		

		// If scenarioRup != null, generate  diagnostics if in debug mode!
		List<EvenlyDiscretizedFunc> expectedPrimaryMFDsForScenarioList=null;
		if(scenarioRups !=null && !scenarioRups.isEmpty()) {
			for (int i=0; i<scenarioRups.size(); i++) {
				ETAS_EqkRupture scenarioRup = scenarioRups.get(i);
				boolean scenWrite = (D || scenarioRups.size() < 100 || scenarioRup.getMag() >= 5d);
				if (!scenWrite)
					continue;
				long rupOT = scenarioRup.getOriginTime();
				
				double startDay = (double)(simStartTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;	// convert epoch to days from event origin time
				double endDay = (double)(simEndTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
				
				info_fr.write("\nMagnitude of Scenario: "+(float)scenarioRup.getMag()+"\n");
				if (D) System.out.println("\nMagnitude of Scenario: "+(float)scenarioRup.getMag());
				double k = scenarioRup.getETAS_k();
				double p = scenarioRup.getETAS_p();
				double c = scenarioRup.getETAS_c();
				if (k != etasParams.get_k()) {
					info_fr.write("\tCustom k-value for Scenario: "+k+"\n");
					if (D) System.out.println("\tCustom k-value for Scenario: "+k+"\n");
				}
				if (p != etasParams.get_p()) {
					info_fr.write("\tCustom p-value for Scenario: "+p+"\n");
					if (D) System.out.println("\tCustom p-value for Scenario: "+p+"\n");
				}
				if (c != etasParams.get_c()) {
					info_fr.write("\tCustom c-value for Scenario: "+c+"\n");
					if (D) System.out.println("\tCustom c-value for Scenario: "+c+"\n");
				}
				double expNum = ETAS_Utils.getExpectedNumEvents(k, p, scenarioRup.getMag(), ETAS_Utils.magMin_DEFAULT, c, startDay, endDay);
				info_fr.write("Expected number of primary events for Scenario: "+expNum+"\n");
				int numPrimaryAftershocks = numPrimaryAshockForScenarios.get(scenarioRup.getID());
				info_fr.write("Observed number of primary events for Scenario: "+numPrimaryAftershocks+"\n");
				if (D) {
					System.out.println("Expected number of primary events for Scenario: "+expNum);
					System.out.println("Observed number of primary events for Scenario: "+numPrimaryAftershocks+"\n");
				}

				if(D && generateDiagnosticsForScenario) {
					System.out.println("Computing Scenario Diagnostics");
					long timeMillis =System.currentTimeMillis();
					expectedPrimaryMFDsForScenarioList = etas_PrimEventSampler.generateRuptureDiagnostics(scenarioRup, expNum, "Scenario", resultsDir,info_fr);
					float timeMin = ((float)(System.currentTimeMillis()-timeMillis))/(1000f*60f);
					System.out.println("Computing Scenario Diagnostics took (min): "+timeMin);
					if (exit_after_scenario_diagnostics) {
						info_fr.close();
						System.exit(0);
					}
				}
//				info_fr.flush();
			}
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
		if (D) info_fr.flush();

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
		
		if (D) info_fr.flush();	// this writes the above out now in case of crash
		
		final double maxPointSourceMag = etasParams.getMaxPointSourceMag();
		
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
							seisDepthDistribution.getRandomDepth(etas_utils));
					
					if(erf_rup.getMag()<maxPointSourceMag)
						rup.setPointSurface(hypoLoc);
					else {
						double aveDip = erf_rup.getRuptureSurface().getAveDip(); // confirm this works
						rup.setRuptureSurface(etas_utils.getRandomFiniteRupSurface(erf_rup.getMag(), hypoLoc, aveDip));
					}

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
				rup.setGridNodeIndex(sourceIndex);
			}
			// Not spontaneous, so set as a primary aftershock
			else {
				succeededInSettingRupture = etas_PrimEventSampler.setRandomPrimaryEvent(rup, maxPointSourceMag);
			}
			
			// break out if we failed to set the rupture
			if(!succeededInSettingRupture) // TODO shouldn't following chunk of code be in the else statement directly above?
				continue;
			nthRup = rup.getNthERF_Index();
			int srcIndex = erf.getSrcIndexForNthRup(nthRup);				

			// add the rupture to the list
			simulatedRupsQueue.add(rup);	// this storage does not take much memory during the simulations
			numSimulatedEvents += 1;
			
			if (includeIndirectTriggering) {
				// set the etas parameters for this rupture, randomizing k if kCOV>0
				// do this before writing it to the file
				etas_utils.setETAS_ParamsForRupture(rup, etasParams);
			}
			
			ETAS_CatalogIO.writeEventToFile(simulatedEventsFileWriter, rup);
			
			long rupOT = rup.getOriginTime();
			
			// now sample primary aftershock times for this event
			if(includeIndirectTriggering) {
				int parID = rup.getID();	// rupture is now the parent
				int gen = rup.getGeneration()+1;
				double startDay = 0;	// starting at origin time since we're within the timespan
				double endDay = (double)(simEndTimeMillis-rupOT) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
//				double[] eventTimes = etas_utils.getDefaultRandomEventTimes(rup.getMag(), startDay, endDay);
				
				// get primary event times
				double[] eventTimes = etas_utils.getRandomEventTimes(rup.getETAS_k(), rup.getETAS_p(), rup.getMag(), ETAS_Utils.magMin_DEFAULT, rup.getETAS_c(), startDay, endDay);

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
							Location tempLoc = etas_utils.getRandomLocationOnRupSurface(rup);
							
//							// for no creep/aseis reduction:
//							if(locList==null) {	// make reusable location list
//								RuptureSurface surf = etas_utils.getRuptureSurfaceWithNoCreepReduction(rup.getFSSIndex(), fssERF, 0.05);
//								locList = surf.getEvenlyDiscritizedListOfLocsOnSurface();							
//							}
//							Location tempLoc = locList.get(etas_utils.getRandomInt(locList.size()-1));
							
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
			
			

			if (D) info_fr.flush();	// this writes the above out now in case of crash

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


		if(scenarioRups !=null && !scenarioRups.isEmpty() && D) {
			for (int i=0; i<scenarioRups.size(); i++) {
				ETAS_EqkRupture scenarioRup = scenarioRups.get(i);
				int inputRupID = scenarioRup.getID();	// TODO already defined above?
				ArrayList<IncrementalMagFreqDist> obsAshockMFDsForScenario = ETAS_SimAnalysisTools.getAftershockMFDsForRup(simulatedRupsQueue, inputRupID, simulationName);
				ETAS_SimAnalysisTools.plotRateVsLogTimeForPrimaryAshocksOfRup(simulationName, new File(resultsDir,"logRateDecayForScenarioPrimaryAftershocks.pdf").getAbsolutePath(), simulatedRupsQueue, scenarioRup,
						etasParams.get_k(), etasParams.get_p(), etasParams.get_c());
				ETAS_SimAnalysisTools.plotRateVsLogTimeForAllAshocksOfRup(simulationName, new File(resultsDir,"logRateDecayForScenarioAllAftershocks.pdf").getAbsolutePath(), simulatedRupsQueue, scenarioRup,
						etasParams.get_k(), etasParams.get_p(), etasParams.get_c());

				ETAS_SimAnalysisTools.plotEpicenterMap(simulationName, new File(resultsDir,"hypoMap.pdf").getAbsolutePath(), obsEqkRuptureList.get(0), simulatedRupsQueue, griddedRegion.getBorder());
				ETAS_SimAnalysisTools.plotDistDecayDensityOfAshocksForRup("Scenario in "+simulationName, new File(resultsDir,"distDecayDensityForScenario.pdf").getAbsolutePath(), 
						simulatedRupsQueue, etasParams.get_q(), etasParams.get_d(), scenarioRup);
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
			}
		} else if (D) {
			ETAS_SimAnalysisTools.plotEpicenterMap(simulationName, new File(resultsDir,"hypoMap.pdf").getAbsolutePath(), null, simulatedRupsQueue, griddedRegion.getBorder());

			ArrayList<ETAS_EqkRupture> bigGridSeisEventsList = new ArrayList<ETAS_EqkRupture>();
			for(ETAS_EqkRupture rup:simulatedRupsQueue)
				if(rup.getFSSIndex()==-1 && rup.getMag()>maxPointSourceMag)
					bigGridSeisEventsList.add(rup);
			ETAS_SimAnalysisTools.plotFiniteGridSeisRupMap(simulationName, new File(resultsDir,"hypoMapBigGridSeisEvents.pdf").getAbsolutePath(), bigGridSeisEventsList, griddedRegion.getBorder());

		}
		
		if(D) {
			ETAS_SimAnalysisTools.plotRateVsLogTimeForPrimaryAshocks(simulationName, new File(resultsDir,"logRateDecayPDF_ForAllPrimaryEvents.pdf").getAbsolutePath(), simulatedRupsQueue,
					etasParams.get_k(), etasParams.get_p(), etasParams.get_c());
			ETAS_SimAnalysisTools.plotDistDecayDensityFromParentTriggerLocHist(simulationName, new File(resultsDir,"distDecayForAllPrimaryEvents.pdf").getAbsolutePath(), simulatedRupsQueue, etasParams.get_q(), etasParams.get_d());
			ETAS_SimAnalysisTools.plotMagFreqDists(simulationName, resultsDir, simulatedRupsQueue);
		}
		
		info_fr.close();
		ETAS_SimulationMetadata meta = ETAS_SimulationMetadata.instance(randomSeed, -1, rangeHistCatalogParentIDs, rangeTriggerRupIDs,
				simulationStartTime, System.currentTimeMillis(), ETAS_Utils.magMin_DEFAULT, simulatedRupsQueue);
		ETAS_CatalogIO.writeMetadataToFile(simulatedEventsFileWriter, meta);
		simulatedEventsFileWriter.close();

		ETAS_SimAnalysisTools.writeMemoryUse("Memory at end of simultation");
		
		return meta;
	}
	
	
	public static void configAndRunSimulation() {
//		
//		UCERF3_GriddedSeisOnlyERF_ETAS erf = getU3_ETAS_ERF__GriddedSeisOnly(2012, 1);
//		System.out.println("UCERF3_GriddedSeisOnlyERF_ETAS Parameters:");
//		for(Parameter param:erf.getAdjustableParameterList())
//			System.out.println("\t"+param.getName()+" = "+param.getValue());
//		System.exit(0);
		
		ETAS_Simulator_NoFaults.D=true;
		ETAS_Simulator_NoFaults.live_map=true;
		
//		ETAS_Simulator.TestScenario scenario = ETAS_Simulator.TestScenario.MOJAVE_M7;
		ETAS_Simulator.TestScenario scenario = null;
		
		String simulationName = "Test40yrSim";
		String incrementString = "_2"; // set as "-1", or "_2" to save previous runs
		
		Long seed = null;
//		Long seed = 1449590752534l;
		
		double startTimeYear=2012;
		long startTimeMillis = ETAS_Simulator.getTimeInMillisFromYear(startTimeYear);
//		long startTimeMillis = 709732654000l;	// Landers OT


		double durationYears=40;
//		double durationYears=7.0/365.25;
		
		ETAS_ParameterList params = new ETAS_ParameterList();
		params.setImposeGR(false);	
		params.setApplyGridSeisCorr(false);
		params.setApplySubSeisForSupraNucl(false);
		params.setTotalRateScaleFactor(1.0);
		params.setU3ETAS_ProbModel(U3ETAS_ProbabilityModelOptions.POISSON);
		params.setMaxPointSourceMag(6.0);
		
		boolean includeSpontEvents=true;
		boolean includeIndirectTriggering=true;
		double gridSeisDiscr = 0.1;
		
		boolean includeHistCat = true;
		
		simulationName +="NoFaults_";
		if(scenario == null)
			simulationName += "NoScenario";
		else
			simulationName += scenario;

		// add suffix to simulation:
		simulationName += incrementString;
		

		ETAS_SimAnalysisTools.writeMemoryUse("Memory at beginning of run");

		Long st = System.currentTimeMillis();

		if(simulationName == null) {
			throw new RuntimeException("simulationName is null");
		}
		
		CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();

		// Make fault system ERF is needed for scenario
		FaultSystemSolutionERF_ETAS erf = null;
		if((scenario!=null && scenario.getFSS_Index()>=0) || includeHistCat)
			erf = ETAS_Simulator.getU3_ETAS_ERF(startTimeMillis, durationYears, params.getApplyGridSeisCorr());
		
		List<ETAS_EqkRupture> histQkList=null;
		if(includeHistCat) {
			try {
				histQkList = ETAS_Simulator.getFilteredHistCatalog(startTimeMillis, erf, params.getStatewideCompletenessModel());
			} catch (IOException | DocumentException e1) {
				e1.printStackTrace();
			}
		}
		
		// Build scenario, reseting date of last event for fault sections in erf if appropriate
		ArrayList<ETAS_EqkRupture> scenarioList=null;
		if(scenario != null ) {
			ETAS_EqkRupture scenarioRup = ETAS_Simulator.buildScenarioRup(scenario, erf, startTimeMillis);
			scenarioList = new ArrayList<ETAS_EqkRupture>();
			scenarioList.add(scenarioRup);
		}
		
		UCERF3_GriddedSeisOnlyERF_ETAS erf_noFlts = getU3_ETAS_ERF__GriddedSeisOnly(startTimeMillis, durationYears);
		
		System.out.println("Starting testETAS_Simulation");
		try {
			String dirNameForSavingFiles = "U3_ETAS_"+simulationName+"/";
			File resultsDir = new File(dirNameForSavingFiles);
			runETAS_Simulation(resultsDir, erf_noFlts, griddedRegion, scenarioList, histQkList,  includeSpontEvents, 
					includeIndirectTriggering, gridSeisDiscr, simulationName, seed, params, null);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		float timeMin = (float)(System.currentTimeMillis()-st)/60000f;
		System.out.println("Total simulation took "+timeMin+" min");

	}
	


	
	
	/**
	 * This is where on can put bug-producing cases for others to look at
	 * @throws IOException
	 */
	public static void runBugReproduce() throws IOException {
	}


	
	
	
	public static UCERF3_GriddedSeisOnlyERF_ETAS getU3_ETAS_ERF__GriddedSeisOnly(long startTimeMillis, double durationYears) {
		Long st = System.currentTimeMillis();
		
		UCERF3_GriddedSeisOnlyERF_ETAS erf = new UCERF3_GriddedSeisOnlyERF_ETAS();
		
//		// set parameters
		erf.setParameter(BackgroundRupParam.NAME, BackgroundRupType.POINT);
		erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
		erf.setParameter(MaximumMagnitudeParam.NAME, 8.3);
		erf.setParameter("Total Regional Rate",TotalMag5Rate.RATE_7p9);
		erf.setParameter("Spatial Seis PDF",SpatialSeisPDF.UCERF3);

		erf.getTimeSpan().setStartTimeInMillis(startTimeMillis);
		erf.getTimeSpan().setDuration(durationYears);
		
		erf.updateForecast();
		
		float timeSec = (float)(System.currentTimeMillis()-st)/1000f;
		System.out.println("ERF instantiation took "+timeSec+" sec");
		return erf;
	}

	
	
	
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		configAndRunSimulation();
		
		
// OLD SUTFF BELOW (pre Oct 2018) FROM BEFORE CLEANUP		
////		
////		UCERF3_GriddedSeisOnlyERF_ETAS erf = getU3_ETAS_ERF__GriddedSeisOnly(2012, 1);
////		System.out.println("UCERF3_GriddedSeisOnlyERF_ETAS Parameters:");
////		for(Parameter param:erf.getAdjustableParameterList())
////			System.out.println("\t"+param.getName()+" = "+param.getValue());
////		System.exit(0);
////		
//
//		ETAS_Simulator.TestScenario scenario = ETAS_Simulator.TestScenario.MOJAVE_M7;
////		TestScenario scenario = null;
//		
//		ETAS_ParameterList params = new ETAS_ParameterList();
//		params.setImposeGR(false);	
//		params.setTotalRateScaleFactor(1.0);
//		params.setU3ETAS_ProbModel(U3ETAS_ProbabilityModelOptions.POISSON);
//		
//		String simulationName="NoFaults_";
//		if(scenario == null)
//			simulationName += "NoScenario";
//		else
//			simulationName += scenario;
//
//		// add suffix to simulation:
//		simulationName += "_4";
//		
//		Long seed = null;
////		Long seed = 1449590752534l;
//		
//		double startTimeYear=2012;
//		double durationYears=10;
////		double durationYears=7.0/365.25;
//		
//		boolean includeHistCat = false;
//		
//		runTest(scenario, params, seed, simulationName, includeHistCat, startTimeYear, durationYears);
	}
}
