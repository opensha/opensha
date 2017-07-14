package org.opensha.sha.simulators.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.iden.EventTimeIdentifier;
import org.opensha.sha.simulators.iden.LogicalAndRupIden;
import org.opensha.sha.simulators.iden.MagRangeRuptureIdentifier;
import org.opensha.sha.simulators.parsers.RSQSimFileReader;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.SlipEnabledRupSet;
import scratch.UCERF3.SlipEnabledSolution;
import scratch.UCERF3.analysis.FaultBasedMapGen;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.BatchPlotGen;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.IDPairing;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;
import scratch.kevin.simulators.erf.SimulatorFaultSystemSolution;

public class RSQSimUtils {

	public static EqkRupture buildSubSectBasedRupture(SimulatorEvent event, List<FaultSectionPrefData> subSects,
			List<SimulatorElement> elements) {
		int minElemSectID = getSubSectIndexOffset(elements, subSects);
		double mag = event.getMagnitude();

		List<Double> rakes = Lists.newArrayList();
		for (SimulatorElement elem : event.getAllElements())
			rakes.add(elem.getFocalMechanism().getRake());
		double rake = FaultUtils.getAngleAverage(rakes);
		if (rake > 180)
			rake -= 360;

		Map<IDPairing, Double> distsCache = Maps.newHashMap();

		List<List<FaultSectionPrefData>> rupSectsListBundled =
				getSectionsForRupture(event, minElemSectID, subSects, distsCache, 0d, null);

		List<FaultSectionPrefData> rupSects = Lists.newArrayList();
		for (List<FaultSectionPrefData> sects : rupSectsListBundled)
			rupSects.addAll(sects);

		double gridSpacing = 1d;

		List<RuptureSurface> rupSurfs = Lists.newArrayList();
		for (FaultSectionPrefData sect : rupSects)
			rupSurfs.add(sect.getStirlingGriddedSurface(gridSpacing, false, false));

		RuptureSurface surf;
		if (rupSurfs.size() == 1)
			surf = rupSurfs.get(0);
		else
			surf = new CompoundSurface(rupSurfs);

		EqkRupture rup = new EqkRupture(mag, rake, surf, null);

		return rup;
	}
	
	public static int getSubSectIndexOffset(List<SimulatorElement> elements, List<FaultSectionPrefData> subSects) {
		int minElemSectID = Integer.MAX_VALUE;
		int maxElemSectID = -1;
		for (SimulatorElement elem : elements) {
			int id = elem.getSectionID();
			if (id < minElemSectID)
				minElemSectID = id;
			if (id > maxElemSectID)
				maxElemSectID = id;
		}
		Preconditions.checkState(subSects.size()-1 == (maxElemSectID - minElemSectID),
				"Couldn't map to subsections. Have %s sub sects, range in elems is %s to %s",
				subSects.size(), minElemSectID, maxElemSectID);
		return minElemSectID;
	}
	
	private static List<List<FaultSectionPrefData>> getSectionsForRupture(SimulatorEvent event, int minElemSectID,
			List<FaultSectionPrefData> subSects, Map<IDPairing, Double> distsCache,
			double minFractForInclusion, Map<Integer, Double> subSectAreas) {
//		HashSet<Integer> rupSectIDs = new HashSet<Integer>();
		Map<Integer, Double> areaOnSectsMap = new HashMap<>();

		for (SimulatorElement elem : event.getAllElements()) {
			Double prevArea = areaOnSectsMap.get(elem.getSectionID());
			if (prevArea == null)
				prevArea = 0d;
			areaOnSectsMap.put(elem.getSectionID(), prevArea + elem.getArea());
		}
		
		// bundle by parent section id
		Map<Integer, List<FaultSectionPrefData>> rupSectsBundled = Maps.newHashMap();
		for (int sectID : areaOnSectsMap.keySet()) {
			if (minFractForInclusion > 0) {
				double fractOn = areaOnSectsMap.get(sectID) / subSectAreas.get(sectID);
				if (fractOn < minFractForInclusion)
					continue;
			}
			// convert to 0-based
			sectID -= minElemSectID;
			FaultSectionPrefData sect = subSects.get(sectID);
			List<FaultSectionPrefData> sects = rupSectsBundled.get(sect.getParentSectionId());
			if (sects == null) {
				sects = Lists.newArrayList();
				rupSectsBundled.put(sect.getParentSectionId(), sects);
			}
			sects.add(sect);
		}

		List<List<FaultSectionPrefData>> rupSectsListBundled = Lists.newArrayList();
		for (List<FaultSectionPrefData> sects : rupSectsBundled.values()) {
			Collections.sort(sects, sectIDCompare);
			rupSectsListBundled.add(sects);
		}
		
		if (rupSectsListBundled.size() > 1)
			rupSectsListBundled = SimulatorFaultSystemSolution.sortRupture(subSects, rupSectsListBundled, distsCache);
		
		return rupSectsListBundled;
	}
	
	private static Comparator<FaultSectionPrefData> sectIDCompare = new Comparator<FaultSectionPrefData>() {

		@Override
		public int compare(FaultSectionPrefData o1, FaultSectionPrefData o2) {
			return new Integer(o1.getSectionId()).compareTo(o2.getSectionId());
		}
	};
	
	private static File getCacheDir() {
		File scratchDir = UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR;
		if (scratchDir.exists()) {
			// eclipse project
			File dir = new File(scratchDir, "SubSections");
			if (!dir.exists())
				Preconditions.checkState(dir.mkdir());
			return dir;
		} else {
			// use home dir
			String path = System.getProperty("user.home");
			File homeDir = new File(path);
			Preconditions.checkState(homeDir.exists(), "user.home dir doesn't exist: "+path);
			File openSHADir = new File(homeDir, ".opensha");
			if (!openSHADir.exists())
				Preconditions.checkState(openSHADir.mkdir(),
						"Couldn't create OpenSHA store location: "+openSHADir.getAbsolutePath());
			File uc3Dir = new File(openSHADir, "ucerf3_sub_sects");
			if (!uc3Dir.exists())
				Preconditions.checkState(uc3Dir.mkdir(),
						"Couldn't create UCERF3 ERF store location: "+uc3Dir.getAbsolutePath());
			return uc3Dir;
		}
	}

	public static List<FaultSectionPrefData> getUCERF3SubSectsForComparison(FaultModels fm, DeformationModels dm) {
		File cacheDir = getCacheDir();
		File xmlFile = new File(cacheDir, fm.encodeChoiceString()+"_"+dm.encodeChoiceString()+"_sub_sects.xml");
		if (xmlFile.exists()) {
			try {
				return FaultModels.loadStoredFaultSections(xmlFile);
			} catch (Exception e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		System.out.println("No sub section cache exists for "+fm.getShortName()+", "+dm.getShortName());
		List<FaultSectionPrefData> sects = new DeformationModelFetcher(
				fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1).getSubSectionList();
		// write to XML
		Document doc = XMLUtils.createDocumentWithRoot();
		FaultSystemIO.fsDataToXML(doc.getRootElement(), FaultModels.XML_ELEMENT_NAME, fm, null, sects);
		try {
			XMLUtils.writeDocumentToFile(xmlFile, doc);
		} catch (IOException e) {
			System.err.println("WARNING: Couldn't write cache file: "+xmlFile.getAbsolutePath());
			e.printStackTrace();
		}
		return sects;
	}
	
	private static Map<Integer, Double> calcSubSectAreas(List<SimulatorElement> elements) {
		Map<Integer, Double> subSectAreas = new HashMap<>();
		for (SimulatorElement elem : elements) {
			Double prevArea = subSectAreas.get(elem.getSectionID());
			if (prevArea == null)
				prevArea = 0d;
			subSectAreas.put(elem.getSectionID(), prevArea + elem.getArea());
		}
		return subSectAreas;
	}
	
	private static class RSQSimFaultSystemRupSet extends SlipEnabledRupSet {
		
		private final List<SimulatorElement> elements;
		private final List<RSQSimEvent> events;
		
		private final int minElemSectID;
		
		private RSQSimFaultSystemRupSet(List<FaultSectionPrefData> subSects,
				List<SimulatorElement> elements, List<RSQSimEvent> events) {
			this(subSects, elements, events, 0d);
		}
		
		private RSQSimFaultSystemRupSet(List<FaultSectionPrefData> subSects,
				List<SimulatorElement> elements, List<RSQSimEvent> events, double minFractForInclusion) {
			this.elements = elements;
			this.events = events;
			minElemSectID = getSubSectIndexOffset(elements, subSects);
			
			// for each rup
			double[] mags = new double[events.size()];
			double[] rupRakes = new double[events.size()];
			double[] rupAreas = new double[events.size()];
			double[] rupLengths = new double[events.size()];
			List<List<Integer>> sectionForRups = Lists.newArrayList();

			Map<IDPairing, Double> distsCache = Maps.newHashMap();
			
			Map<Integer, Double> subSectAreas = null;
			if (minFractForInclusion > 0)
				subSectAreas = calcSubSectAreas(elements);

			System.out.print("Building ruptures...");
			for (int i=0; i<events.size(); i++) {
				SimulatorEvent e = events.get(i);
				mags[i] = e.getMagnitude();
				rupAreas[i] = e.getArea();
				rupLengths[i] = e.getLength();
				
				List<List<FaultSectionPrefData>> subSectsForFaults = getSectionsForRupture(
						e, minElemSectID, subSects, distsCache, minFractForInclusion, subSectAreas);
				if (subSectsForFaults.isEmpty())
					// fallback to any that touch if empty
					subSectsForFaults = getSectionsForRupture(
							e, minElemSectID, subSects, distsCache, 0d, subSectAreas);

				List<Double> rakes = Lists.newArrayList();
				List<Integer> rupSectIndexes = Lists.newArrayList();
				for (List<FaultSectionPrefData> faultList : subSectsForFaults) {
					for (FaultSectionPrefData subSect : faultList) {
						rupSectIndexes.add(subSect.getSectionId());
						rakes.add(subSect.getAveRake());
					}
				}
				sectionForRups.add(rupSectIndexes);

				double avgRake = FaultUtils.getAngleAverage(rakes);
				if (avgRake > 180)
					avgRake -= 360;
				rupRakes[i] = avgRake;
			}
			System.out.println("DONE.");

			// for each section
			double[] sectSlipRates = new double[subSects.size()];
			double[] sectSlipRateStdDevs = null;
			double[] sectAreas = new double[subSects.size()];

			for (int s=0; s<subSects.size(); s++) {
				FaultSectionPrefData sect = subSects.get(s);
				sectSlipRates[s] = sect.getReducedAveSlipRate()/1e3; // in meters
				sectAreas[s] = sect.getReducedDownDipWidth()*sect.getTraceLength()*1e6; // in meters
			}

			String info = "Fault Simulators Solution\n"
					+ "# Elements: "+elements.size()+"\n"
					+ "# Sub Sections: "+subSects.size()+"\n"
					+ "# Events/Rups: "+events.size();
//					+ "Duration: "+durationYears+"\n"
//					+ "Indv. Rup Rate: "+(1d/durationYears);
			
			init(subSects, sectSlipRates, sectSlipRateStdDevs, sectAreas,
					sectionForRups, mags, rupRakes, rupAreas, rupLengths, info);
		}
		
		private int getSectIndex(int elemSectionID) {
			return elemSectionID - minElemSectID;
		}

		@Override
		public double getAveSlipForRup(int rupIndex) {
			double[] slipOnSects = getSlipOnSectionsForRup(rupIndex);
			// area average
			double avg = 0d;
			List<Integer> sectIndexes = getSectionsIndicesForRup(rupIndex);
			for (int i=0; i<slipOnSects.length; i++)
				avg += slipOnSects[i]/getAreaForSection(sectIndexes.get(i));
			return avg;
		}

		@Override
		public double[] getAveSlipForAllRups() {
			double[] slips = new double[getNumRuptures()];
			for (int r=0; r<slips.length; r++)
				slips[r] = getAveSlipForRup(r);
			return slips;
		}

		@Override
		protected double[] calcSlipOnSectionsForRup(int rthRup) {
			List<Integer> sectIndexes = getSectionsIndicesForRup(rthRup);
			double[] slips = new double[sectIndexes.size()];
			double[] slipsNotNormalized = new double[sectIndexes.size()];
			
			SimulatorEvent event = events.get(rthRup);
			Map<Integer, EventRecord> sectIndexToRecordMap = Maps.newHashMap();
			for (EventRecord rec : event) {
				Integer sectIndex = getSectIndex(rec.getSectionID());
				Preconditions.checkState(!sectIndexToRecordMap.containsKey(sectIndex),
						"Multiple EventRecord's with the same section ID");
				sectIndexToRecordMap.put(sectIndex, rec);
			}
			
			for (int i=0; i<sectIndexes.size(); i++) {
				int sectIndex = sectIndexes.get(i);
				EventRecord record = sectIndexToRecordMap.get(sectIndex);
				Preconditions.checkNotNull(record);
				
				double[] elemSlips = record.getElementSlips();
				List<SimulatorElement> elems = record.getElements();
				Preconditions.checkState(elemSlips.length == elems.size());
				
				double sumSlipTimesArea = 0;
				
				for (int e=0; e<elemSlips.length; e++) {
					sumSlipTimesArea += elemSlips[e] * elems.get(e).getArea();
				}
				
				double sectArea = getAreaForSection(sectIndex); // m^2
				
				// now scale to actual subsection area
				slips[i] = sumSlipTimesArea/sectArea;
				
				slipsNotNormalized[i] = sumSlipTimesArea/record.getArea();
			}
			
//			if (event.getMagnitude() > 7.9) {
//				EvenlyDiscretizedFunc subSectMapped = new EvenlyDiscretizedFunc(0, slips.length, 1d);
//				EvenlyDiscretizedFunc actualMapped = new EvenlyDiscretizedFunc(0, slips.length, 1d);
//				
//				for (int i=0; i<slips.length; i++) {
//					subSectMapped.set(i, slips[i]);
//					actualMapped.set(i, slipsNotNormalized[i]);
//				}
//				
//				new GraphWindow(Lists.newArrayList(subSectMapped, actualMapped), "Rup "+event.getID());
//			}
			
//			return slips;
			return slipsNotNormalized;
		}
		
	}
	
	private static double[] buildRatesArray(List<? extends SimulatorEvent> events) {
		double[] rates = new double[events.size()];
		double durationYears = SimulatorUtils.getSimulationDurationYears(events);
		double rateEach = 1d/(durationYears);
		for (int i=0; i<rates.length; i++)
			rates[i] = rateEach;
		return rates;
	}
	
	private static class RSQSimFaultSystemSolution extends SlipEnabledSolution {
		
		private RSQSimFaultSystemRupSet rupSet;
		
		public RSQSimFaultSystemSolution(RSQSimFaultSystemRupSet rupSet) {
			super(rupSet, buildRatesArray(rupSet.events));
			this.rupSet = rupSet;
		}

		@Override
		public SlipEnabledRupSet getRupSet() {
			return rupSet;
		}
		
	}
	
	public static SlipEnabledSolution buildFaultSystemSolution(List<FaultSectionPrefData> subSects,
			List<SimulatorElement> elements, List<RSQSimEvent> events, double minMag) {
		return buildFaultSystemSolution(subSects, elements, events, minMag, 0d);
	}

	public static SlipEnabledSolution buildFaultSystemSolution(List<FaultSectionPrefData> subSects,
			List<SimulatorElement> elements, List<RSQSimEvent> events, double minMag, double minFractForInclusion) {
		
		if (minMag > 0)
			events = new MagRangeRuptureIdentifier(minMag, 10d).getMatches(events);
		
		RSQSimFaultSystemRupSet rupSet = new RSQSimFaultSystemRupSet(subSects, elements, events, minFractForInclusion);
		return new RSQSimFaultSystemSolution(rupSet);
	}
	
	public static void writeUCERF3ComparisonPlots(SlipEnabledSolution sol, FaultModels fm, DeformationModels dm,
			File dir, String prefix) throws GMT_MapException, RuntimeException, IOException {
		// regular plots
//		CommandLineInversionRunner.writeMFDPlots(sol, dir, prefix);
		
//		if (!hasJumpPlots) {
//			try {
//				DeformationModels dm = sol.getRupSet().getFaultModel().getFilterBasis();
//				if (dm == null)
//					dm = sol.getRupSet().getDeformationModel();
//				Map<IDPairing, Double> distsMap = new DeformationModelFetcher(
//						sol.getRupSet().getFaultModel(), dm,
//						UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1).getSubSectionDistanceMap(
//								LaughTestFilter.getDefault().getMaxJumpDist());
//				CommandLineInversionRunner.writeJumpPlots(sol, distsMap, dir, prefix);
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//		}
		
		System.out.println("Loading paleo/slip constraints");
		ArrayList<PaleoRateConstraint> paleoRateConstraints =
				CommandLineInversionRunner.getPaleoConstraints(fm, sol.getRupSet());
		List<AveSlipConstraint> aveSlipConstraints = AveSlipConstraint.load(sol.getRupSet().getFaultSectionDataList());
//		CommandLineInversionRunner.writePaleoPlots(paleoRateConstraints, aveSlipConstraints, sol, dir, prefix);
		System.out.println("Writing SAF Seg plots");
		CommandLineInversionRunner.writeSAFSegPlots(sol, fm, dir, prefix);
//		CommandLineInversionRunner.writePaleoCorrelationPlots(sol,
//						new File(dir, CommandLineInversionRunner.PALEO_CORRELATION_DIR_NAME), UCERF3_PaleoProbabilityModel.load());
		System.out.println("Writing parent sect MFD plots");
		CommandLineInversionRunner.writeParentSectionMFDPlots(sol,
						new File(dir, CommandLineInversionRunner.PARENT_SECT_MFD_DIR_NAME));
		Map<String, List<Integer>> namedFaultsMap = fm.getNamedFaultsMapAlt();
		System.out.println("Writing paleo fault based plots");
		CommandLineInversionRunner.writePaleoFaultPlots(paleoRateConstraints, aveSlipConstraints, namedFaultsMap, sol,
						new File(dir, CommandLineInversionRunner.PALEO_FAULT_BASED_DIR_NAME));
//		System.out.println("Writing rup pairing smoothness plots");
//		CommandLineInversionRunner.writeRupPairingSmoothnessPlot(sol, prefix, dir);

		// map plots
		Region region = new CaliforniaRegions.RELM_TESTING();
		System.out.println("Plotting slip rates");
		FaultBasedMapGen.plotOrigNonReducedSlipRates(sol, region, dir, prefix, false);
		FaultBasedMapGen.plotOrigCreepReducedSlipRates(sol, region, dir, prefix, false);
		FaultBasedMapGen.plotTargetSlipRates(sol, region, dir, prefix, false);
		FaultBasedMapGen.plotSolutionSlipRates(sol, region, dir, prefix, false);
		FaultBasedMapGen.plotSolutionSlipMisfit(sol, region, dir, prefix, false, true);
		FaultBasedMapGen.plotSolutionSlipMisfit(sol, region, dir, prefix, false, false);
//		FaultSystemSolution ucerf2 = getUCERF2Comparision(sol.getRupSet().getFaultModel(), dir);
		System.out.println("Plotting participation rates");
		for (double[] range : BatchPlotGen.partic_mag_ranges) {
			FaultBasedMapGen.plotParticipationRates(sol, region, dir, prefix, false, range[0], range[1]);
//			FaultBasedMapGen.plotParticipationRatios(sol, ucerf2, region, dir, prefix, false, range[0], range[1], true);
		}
		System.out.println("Plotting sect pair");
		FaultBasedMapGen.plotSectionPairRates(sol, region, dir, prefix, false);
		System.out.println("Plotting segmentation");
		FaultBasedMapGen.plotSegmentation(sol, region, dir, prefix, false, 0, 10);
		FaultBasedMapGen.plotSegmentation(sol, region, dir, prefix, false, 7, 10);
		FaultBasedMapGen.plotSegmentation(sol, region, dir, prefix, false, 7.5, 10);
		System.out.println("DONE");
	}
	
	public static void main(String[] args) throws IOException, GMT_MapException, RuntimeException {
//		File dir = new File("/home/kevin/Simulators/UCERF3_35kyrs");
//		File geomFile = new File(dir, "UCERF3.1km.tri.flt");
//		File dir = new File("/home/kevin/Simulators/UCERF3_125kyrs");
//		File geomFile = new File(dir, "UCERF3.D3.1.1km.tri.2.flt");
//		File dir = new File("/home/kevin/Simulators/bruce/rundir1435");
//		File geomFile = new File(dir, "zfault_Deepen.in");
		File dir = new File("/home/kevin/Simulators/UCERF3_JG_supraSeisGeo2");
		File geomFile = new File(dir, "UCERF3.D3.1.1km.tri.2.flt");
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
		System.out.println("Loaded "+elements.size()+" elements");
//		for (Location loc : elements.get(0).getVertices())
//			System.out.println(loc);
		File eventDir = dir;
		
		double minMag = 6d;
		List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(eventDir, elements,
				Lists.newArrayList(new LogicalAndRupIden(new EventTimeIdentifier(5000d, Double.POSITIVE_INFINITY, true),
						new MagRangeRuptureIdentifier(minMag, 10d))));
		double duration = events.get(events.size()-1).getTimeInYears() - events.get(0).getTimeInYears();
		System.out.println("First event time: "+events.get(0).getTimeInYears()+", duration: "+duration);
		
		FaultModels fm = FaultModels.FM3_1;
		DeformationModels dm = DeformationModels.GEOLOGIC;
		SlipEnabledSolution sol = buildFaultSystemSolution(getUCERF3SubSectsForComparison(
				fm, dm), elements, events, minMag);
		
		File plotDir = new File(eventDir, "ucerf3_fss_comparison_plots");
		Preconditions.checkState(plotDir.exists() ||  plotDir.mkdir());
//		MFDCalc.writeMFDPlots(elements, events, plotDir, new CaliforniaRegions.RELM_SOCAL(),
//				new CaliforniaRegions.RELM_NOCAL(), new CaliforniaRegions.LA_BOX(), new CaliforniaRegions.NORTHRIDGE_BOX(),
//				new CaliforniaRegions.SF_BOX(), new CaliforniaRegions.RELM_TESTING());
		writeUCERF3ComparisonPlots(sol, fm, dm, plotDir, "rsqsim_comparison");
	}

}
