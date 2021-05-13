package scratch.UCERF3.elasticRebound.simulatorAnalysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.eqsim_v04.OldGeneral_EQSIM_Tools;
import org.opensha.sha.simulators.parsers.EQSIMv06FileReader;

import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoRateConstraintFetcher;

public class simulatorAnalysisUtils {
	
	
	/**
	 * This tests the event files for various things
	 */
	public static void test() {
		
		// Set the simulator Geometry file
//		File geomFileDir = new File("/Users/field/Neds_Creations/CEA_WGCEP/UCERF3/ProbModels/ElasticRebound/allcal2_1-7-11");
		File geomFileDir = new File("/Users/field/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/simulatorDataFiles");
		File geomFile = new File(geomFileDir, "ALLCAL2_1-7-11_Geometry.dat");
		
		// Set the dir for simulator event files 
//		File simEventFileDir = new File("/Users/field/Neds_Creations/CEA_WGCEP/UCERF3/ProbModels/ElasticRebound/simulatorDataFiles");
		File simEventFileDir = new File("/Users/field/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/simulatorDataFiles");
		
		File eventFile = new File(simEventFileDir, "eqs.ALLCAL2_RSQSim_sigma0.5-5_b=0.015.barall");
//		File eventFile = new File(simEventFileDir, "ALLCAL2_no-creep_dt-08_st-10_110912-471207_Events_slip-map-5.5.dat");
//		File eventFile = new File(simEventFileDir, "ALLCAL2-30k-output[3-24-11].converted");
//		File eventFile = new File(simEventFileDir, "Fred-allcal2-7june11.txt");
// OLD/SRL FILE:		File eventFile = new File(simEventFileDir, "ALLCAL2_1-7-11_no-creep_dyn-05_st-20_108764-277803_Events_slip-map-5.5.dat");

				String dirNameForSavingFiles = "tempSimTest_alt2";

				try {
					System.out.println("Loading geometry...");
					OldGeneral_EQSIM_Tools tools = new OldGeneral_EQSIM_Tools(geomFile);
					System.out.println("Loading events...");
					tools.read_EQSIMv04_EventsFile(eventFile);
//					List<EQSIM_Event> events = EQSIMv06FileReader.readEventsFile(eventFile, tools.getElementsList());
//					tools.setEvents(events);
					tools.setDirNameForSavingFiles(dirNameForSavingFiles);

					// TEST METHODS:
//					tools.testElementAreas();
//					tools.printMinAndMaxElementArea();
//					tools.checkElementSlipRates(null, true);
//					tools.checkEventMagnitudes();
//					tools.testDistanceAlong();
//					tools.writeDAS_ForVertices();
					
					// ANALYSIS METHODS
//					tools.computeTotalMagFreqDist(4.05, 8.95, 50, true, false);
//					tools.computeMagFreqDistByFaultSection(minMag, maxMag, numMag, makeOnePlotWithAll, makeSeparatePlots, savePlots)
//					tools.plotAveNormSlipAlongRupture(7.8, false);
					
//					System.out.println("isSupra: "+tools.isEventSupraSeismogenic(tools.getEventsHashMap().get(216865), Double.NaN));
					
//					tools.checkFullDDW_rupturing(true,false);
//					tools.writeEventsThatInvolveMultSections();
//					tools.plotNormRecurIntsForAllSurfaceElements(Double.NaN, true);
//					tools.plotNormRecurIntsForAllElements(Double.NaN, true);
//					tools.plotRecurIntervalsForElement(1041, Double.NaN, false, "testCarrizo","");
//					tools.writeRI_COV_forAllSurfaceEvlemets(Double.NaN, "testSimSurfElemCOVs.txt");
//					tools.plotSAF_EventsAlongStrikeVsTime(Double.NaN, 100);
//					tools.plotScalingRelationships(true);
//					tools.plotYearlyEventRates();
//					plotRI_DistsAtObsPaleoRateSites(tools, true);
	
					
					tools.testTimePredictability(Double.NaN, true, null, false);
					// This includes norm RI along rupture (ave and histograms at  points along)
					
//					ArrayList<String> infoStrings = new ArrayList<String>();
//					infoStrings.add("UCERF3.elasticRebound.simulatorAnalysis.simulatorAnalysisUtils.runAll()\n");
//					infoStrings.add(dirNameForSavingFiles+"\tusing file "+fileName+"\n");
//					infoStrings.add("Simulation Duration is "+(float)tools.getSimulationDurationYears()+" years\n");
//					
//					String info = tools.testTimePredictability(Double.NaN, false, null, false);
//					infoStrings.add(info);
//
//					try {
//						FileWriter infoFileWriter = new FileWriter(dirNameForSavingFiles+"/INFO.txt");
//						for(String string: infoStrings) 
//							infoFileWriter.write(string+"\n");
//						infoFileWriter.close();
//					} catch (IOException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
					
					System.out.println("Done");
					
				} catch (IOException e) {
					e.printStackTrace();
				}
	}

	
	/**
	 * 
	 * Note: sometimes the JfreeChart plotting crashes and it needs to be run again to get all the results
	 */
	public static void runAll() {
		
		// Set the simulator Geometry file
		File geomFileDir = new File("/Users/field/Field_Other/CEA_WGCEP/UCERF3/ProbModels/ElasticRebound/Files/allcal2_1-7-11");
//		File geomFileDir = new File("/Users/field/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/simulatorDataFiles");
		File geomFile = new File(geomFileDir, "ALLCAL2_1-7-11_Geometry.dat");
		
		// Set the dir for simulator event files 
		File simEventFileDir = new File("/Users/field/Field_Other/CEA_WGCEP/UCERF3/ProbModels/ElasticRebound/Files/simulatorDataFiles");
//		File simEventFileDir = new File("/Users/field/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/simulatorDataFiles");
		
		// set the list of event files to loop over (and corresponding short dir names for each)
		String[] eventFileArray = {
				"eqs.ALLCAL2_RSQSim_sigma0.5-5_b=0.015.barall",	// RSQSim
//				"eqs.ALLCAL2_RSQSim_sigma0.5-5_b=0.015.long.barall",	// the long file from Kevin
//				"ALLCAL2_no-creep_dt-08_st-10_110912_471207_events_slip-map-5.5_eid-fix.dat" //,
//				"ALLCAL2-30k-output[3-24-11].converted",
//				"Fred-allcal2-7june11.txt"
				};
//		String[] dirNamesPrefixArray = {"RSQSim","VirtCal","ALLCAL","ViscoSim"};
//		String[] dirNamesPrefixArray = {"RSQSim","ALLCAL","ViscoSim"};
		String[] dirNamesPrefixArray = {"RSQSim"};
//		String[] dirNamesPrefixArray = {"RSQSim_Long"};
//		String[] dirNamesPrefixArray = {"VirtCal"};
//		String[] dirNamesPrefixArray = {"ALLCAL"};
//		String[] dirNamesPrefixArray = {"ViscoSim"};

		// set the list of supra-seismogenic mag thresholds (NaN means it will be defined by ave fault DDW)
//		double[] seismoMagThreshArray = {6.5,Double.NaN};
		double[] seismoMagThreshArray = {Double.NaN};
//		double[] seismoMagThreshArray = {6.5};
				
		// loop over desired runs
		for(double magThresh:seismoMagThreshArray) {
			int dirIndex = -1;
			for(String fileName:eventFileArray) {
				
				File eventFile = new File(simEventFileDir, fileName);
				dirIndex+=1;
				String dirNameForSavingFiles = dirNamesPrefixArray[dirIndex]+"_"+ (new Double(magThresh)).toString().replaceAll("\\.", "pt");

				try {
					System.out.println("Loading geometry...");
					OldGeneral_EQSIM_Tools tools = new OldGeneral_EQSIM_Tools(geomFile);
					System.out.println("Loading events...");
					tools.read_EQSIMv04_EventsFile(eventFile);
//					List<EQSIM_Event> events = EQSIMv06FileReader.readEventsFile(eventFile, tools.getElementsList());
//					tools.setEvents(events);
					tools.setDirNameForSavingFiles(dirNameForSavingFiles);
					
					ArrayList<String> infoStrings = new ArrayList<String>();
					infoStrings.add("UCERF3.elasticRebound.simulatorAnalysis.simulatorAnalysisUtils.runAll()\n");
					infoStrings.add(dirNameForSavingFiles+"\tusing file "+fileName+"\n");
					infoStrings.add("Simulation Duration is "+(float)tools.getSimulationDurationYears()+" years\n");
					System.out.println("Simulation Duration is "+(float)tools.getSimulationDurationYears()+" years\n");
		
//					tools.testElementAreas();
//					tools.testTemp();
//					
//					// check element areas (only geometry file dependent)
//					System.out.println("Working on printMinAndMaxElementArea(*)");
//					infoStrings.add(tools.printMinAndMaxElementArea());
//
//					// check slip rates
//					System.out.println("Working on imposedVsImpliedSlipRates(*)");
//					tools.checkElementSlipRates("imposedVsImpliedSlipRates", true);
//
//					// check event mags
//					System.out.println("Working on checkEventMagnitudes(*)");
//					infoStrings.add(tools.checkEventMagnitudes(Double.NaN));
//					
//					// check full DDW ruptures
//					if(Double.isNaN(magThresh)) {
//						System.out.println("Working on checkFullDDW_rupturing(*)");
//						infoStrings.add(tools.checkFullDDW_rupturing(true,true));
//					}
//					// total MFD
//					System.out.println("Working on computeTotalMagFreqDist(*)");
//					tools.computeTotalMagFreqDist(4.05, 8.95, 50, true, true);

//					// norm RI dist for surface elements
//					System.out.println("Working on plotNormRecurIntsForAllSurfaceElements(*)");
//					tools.plotNormRecurIntsForAllSurfaceElements(magThresh, true);

//					// ave slip along rupture
//					System.out.println("Working on plotAveNormSlipAlongRupture(*)");
//					boolean success = tools.plotAveNormSlipAlongRupture(magThresh, true);
//					if(!success) {
//						infoStrings.add("plotAveNormSlipAlongRupture failed\n");
//						System.out.println("plotAveNormSlipAlongRupture failed\n");
//					}
//
//					// scaling plots
//					System.out.println("Working on plotScalingRelationships(*)");
//					tools.plotScalingRelationships(true);
//
//					// RIs at paleo sites
//					System.out.println("Working on plotRI_DistsAtObsPaleoRateSites(*)");
//					plotRI_DistsAtObsPaleoRateSites(tools, true);
					
//					// all the time & slip predictability tests (plus other things):
//					System.out.println("Working on testTimePredictability(*)");
//					String info = tools.testTimePredictability(magThresh, true, null, false);
//					infoStrings.add(info);
					
					// normalized RI at hypocenters
					System.out.println("Working on plotNormRI_AtHypocenters(*)");					
					tools.plotNormRI_AtHypocenters(magThresh, true);

					try {
						FileWriter infoFileWriter = new FileWriter(dirNameForSavingFiles+"/INFO.txt");
						for(String string: infoStrings) 
							infoFileWriter.write(string+"\n");
						infoFileWriter.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					System.out.println("Done");
					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * This plots the RI distribution at the paleo sites available as UCERF3_PaleoRateConstraintFetcher.getConstraints()
	 * @param tools
	 * @param savePlot
	 */
	public static void plotRI_DistsAtObsPaleoRateSites(OldGeneral_EQSIM_Tools tools, boolean savePlot) {
		
		try {

			ArrayList<PaleoRateConstraint> paleoConstrList = UCERF3_PaleoRateConstraintFetcher.getConstraints();
			for(PaleoRateConstraint constr:paleoConstrList) {
//				System.out.println("\n"+constr.getPaleoSiteName()+"\t"+(float)constr.getPaleoSiteLoction().getLatitude() +"\t"+
//						(float)constr.getPaleoSiteLoction().getLongitude() +"\t"+(float)constr.getMeanRate());
				double meanRI = Math.round(1.0/constr.getMeanRate());
				double low95 = Math.round(1.0/constr.getUpper95ConfOfRate());
				double up95 = Math.round(1.0/constr.getLower95ConfOfRate());
				String infoString = "PaleoRates site "+constr.getPaleoSiteName()+" (Biasi Mean RI="+meanRI+"  & 95% Conf: "+low95+" to "+up95+")\n";
				String name = constr.getPaleoSiteName().replaceAll("\\W+", "_");
				tools.plotRecurIntervalsForNearestLoc(constr.getPaleoSiteLoction(), Double.NaN, savePlot, name,infoString);
//				System.out.println(constr.getPaleoSiteName());
//				System.out.println(name);
			}

			System.out.println("Done");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
//		test();
		runAll();
		
	}

}
