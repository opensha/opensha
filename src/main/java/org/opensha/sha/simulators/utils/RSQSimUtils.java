package org.opensha.sha.simulators.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.PlaneUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.InputJumpsOrDistClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle;
import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.TriangularElement;
import org.opensha.sha.simulators.Vertex;
import org.opensha.sha.simulators.distCalc.SimEventCumDistFuncSurface;
import org.opensha.sha.simulators.distCalc.SimRuptureDistCalcUtils.LocationElementDistanceCacheFactory;
import org.opensha.sha.simulators.distCalc.SimRuptureDistCalcUtils.Scalar;
import org.opensha.sha.simulators.iden.EventTimeIdentifier;
import org.opensha.sha.simulators.iden.LogicalAndRupIden;
import org.opensha.sha.simulators.iden.MagRangeRuptureIdentifier;
import org.opensha.sha.simulators.iden.SkipYearsLoadIden;
import org.opensha.sha.simulators.parsers.RSQSimFileReader;
import org.opensha.sha.simulators.utils.RSQSimSubSectionMapper.SubSectionMapping;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.LittleEndianDataOutputStream;

import scratch.UCERF3.U3SlipEnabledRupSet;
import scratch.UCERF3.U3SlipEnabledSolution;
import scratch.UCERF3.analysis.FaultBasedMapGen;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.BatchPlotGen;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.U3FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.U3PaleoRateConstraint;

public class RSQSimUtils {

	public static RSQSimSubSectEqkRupture buildSubSectBasedRupture(
			RSQSimSubSectionMapper mapper, RSQSimEvent event) {
		List<List<SubSectionMapping>> mappings = mapper.getFilteredSubSectionMappings(event);
		if (mapper.getMinFractForInclusion() > 0 && mappings.isEmpty())
			// fallback to any that touch if empty
			mappings = mapper.getAllSubSectionMappings(event);
		
		double mag = event.getMagnitude();

		List<Double> rakes = new ArrayList<>();
		for (List<SubSectionMapping> bundle : mappings)
			for (SubSectionMapping mapping : bundle)
				rakes.add(mapping.getSubSect().getAveRake());
		double rake = FaultUtils.getAngleAverage(rakes);
		if (rake > 180)
			rake -= 360;

		List<FaultSection> rupSects = new ArrayList<>();
		for (List<SubSectionMapping> bundle : mappings)
			for (SubSectionMapping mapping : bundle)
				rupSects.add(mapping.getSubSect());
		Preconditions.checkState(!rupSects.isEmpty(), "No mapped sections! ID=%s, M=%s, %s elems",
				event.getID(), event.getMagnitude(), event.getAllElementIDs().length);

		double gridSpacing = 1d;

		List<RuptureSurface> rupSurfs = new ArrayList<>();
		for (FaultSection sect : rupSects)
			rupSurfs.add(sect.getFaultSurface(gridSpacing, false, false));

		RuptureSurface surf;
		if (rupSurfs.size() == 1)
			surf = rupSurfs.get(0);
		else
			surf = new CompoundSurface(rupSurfs);
		
		SimulatorElement hypo = getHypocenterElem(event);

		RSQSimSubSectEqkRupture rup = new RSQSimSubSectEqkRupture(mag, rake, surf, hypo.getCenterLocation(), event,
				rupSects, mapper.getMappedSection(hypo));

		return rup;
	}
	
	public static double getElemAvgRake(RSQSimEvent event, boolean momentWeighted) {
		List<SimulatorElement> elems = event.getAllElements();
		double[] slips = momentWeighted ? event.getAllElementSlips() : null;
		
		List<Double> rakes = new ArrayList<>(elems.size());
		List<Double> weights = momentWeighted ? new ArrayList<>(elems.size()) : null;
		
		for (int i=0; i<elems.size(); i++) {
			SimulatorElement elem = elems.get(i);
			rakes.add(elem.getFocalMechanism().getRake());
			if (momentWeighted)
				weights.add(momentWeighted ? FaultMomentCalc.getMoment(elem.getArea(), slips[i]) : 1d);
		}

		double rake = momentWeighted ? FaultUtils.getScaledAngleAverage(weights, rakes) : FaultUtils.getAngleAverage(rakes);
		if (rake > 180)
			rake -= 360;
		
		return rake;
	}

	public static RSQSimEqkRupture buildCumDistRupture(RSQSimEvent event) {
		return buildCumDistRupture(event, new LocationElementDistanceCacheFactory());
	}
	
	private static Scalar DIST_WEIGHT_SCALAR = Scalar.MOMENT;
	private static double DIST_FRACT_THRESHOLD = 0.05;
	private static double DIST_ABS_THRESHOLD = MagUtils.magToMoment(6d);
	private static double DIST_X_FRACT_THRESHOLD = 0.1;

	public static RSQSimEqkRupture buildCumDistRupture(RSQSimEvent event, LocationElementDistanceCacheFactory locCacheFactory) {
		double mag = event.getMagnitude();

		double rake = getElemAvgRake(event, true);
		
		RuptureSurface surf = new SimEventCumDistFuncSurface(event, DIST_WEIGHT_SCALAR, DIST_FRACT_THRESHOLD,
				DIST_ABS_THRESHOLD, DIST_X_FRACT_THRESHOLD, locCacheFactory);
		
		SimulatorElement hypo = getHypocenterElem(event);

		RSQSimEqkRupture rup = new RSQSimEqkRupture(mag, rake, surf, hypo.getCenterLocation(), event);

		return rup;
	}

	public static SimulatorElement getHypocenterElem(RSQSimEvent event) {
		SimulatorElement hypo = null;
		double earliestTime = Double.POSITIVE_INFINITY;
		for (EventRecord rec : event) {
			List<SimulatorElement> patches = rec.getElements();
			double[] patchTimes = rec.getElementTimeFirstSlips();
			for (int i=0; i<patches.size(); i++) {
				if (patchTimes[i] < earliestTime) {
					earliestTime = patchTimes[i];
					hypo = patches.get(i);
				}
			}
		}
		Preconditions.checkNotNull(hypo, "Couldn't detect hypocenter for event %s.",
				event.getID());
		return hypo;
	}

	public static Location getHypocenter(RSQSimEvent event) {
		return getHypocenterElem(event).getCenterLocation();
	}
	
	private static boolean warned = false;
	
	public static int getSubSectIndexOffset(List<SimulatorElement> elements, List<? extends FaultSection> subSects) {
		int minElemSectID = Integer.MAX_VALUE;
		int maxElemSectID = -1;
		HashSet<Integer> sectsFound = new HashSet<>();
		for (SimulatorElement elem : elements) {
			int id = elem.getSectionID();
			if (id < minElemSectID)
				minElemSectID = id;
			if (id > maxElemSectID)
				maxElemSectID = id;
			sectsFound.add(id);
		}
		int myNum = 1 + maxElemSectID - minElemSectID;
		if (!warned) {
			if (myNum != sectsFound.size()) {
				System.err.println("WARNING: Sub sect range not complete, has holes. "
						+sectsFound.size()+" unique, range suggests "+myNum+". Future warnings suppressed.");
				warned = true;
			}
			if (myNum < subSects.size()) {
				System.err.println("WARNING: Sub sect count different. We have "+myNum
						+" (id range: "+minElemSectID+"-"+maxElemSectID+"), expected "+subSects.size()
						+". Future warnings suppressed.");
				warned = true;
			}
		}
		if (myNum == subSects.size())
			return minElemSectID;
		if (elements.get(0).getSectionName().startsWith("nn"))
			// bruce file, 0-based
			return 0;
		Preconditions.checkState(subSects.size() >= myNum,
				"Couldn't map to subsections. Have %s sub sects, range in elems is %s to %s",
				subSects.size(), minElemSectID, maxElemSectID);
		return minElemSectID;
	}
	
	public static void populateFaultIDWithParentIDs(List<SimulatorElement> elements, List<? extends FaultSection> subSects) {
		int offset = getSubSectIndexOffset(elements, subSects);
		for (SimulatorElement elem : elements)
			elem.setFaultID(subSects.get(elem.getSectionID()-offset).getParentSectionId());
	}
	
	public static void populateSubSectionNames(List<SimulatorElement> elements, List<? extends FaultSection> subSects) {
		int offset = getSubSectIndexOffset(elements, subSects);
		for (SimulatorElement elem : elements)
			elem.setSectionName(subSects.get(elem.getSectionID()-offset).getName());
	}

	public static List<? extends FaultSection> getUCERF3SubSectsForComparison(FaultModels fm, DeformationModels dm) {
		return DeformationModels.loadSubSects(fm, dm);
	}
	
	public static Map<Integer, Double> calcSubSectAreas(List<SimulatorElement> elements, List<? extends FaultSection> subSects) {
		int offset = getSubSectIndexOffset(elements, subSects);
		Map<Integer, Double> subSectAreas = new HashMap<>();
		for (SimulatorElement elem : elements) {
			Double prevArea = subSectAreas.get(elem.getSectionID());
			if (prevArea == null)
				prevArea = 0d;
			subSectAreas.put(elem.getSectionID()-offset, prevArea + elem.getArea());
		}
		
		for (FaultSection sect : subSects) {
			Integer id = sect.getSectionId();
			if (!subSectAreas.containsKey(id))
				// this subsection is skipped
				continue;
			double simSectArea = subSectAreas.get(id);
			double fsdArea = sect.getArea(false);
			if (fsdArea < simSectArea)
				subSectAreas.put(id, fsdArea);
		}
		return subSectAreas;
	}
	
	private static class RSQSimFaultSystemRupSet extends U3SlipEnabledRupSet {
		
		private final List<SimulatorElement> elements;
		private final List<RSQSimEvent> events;
		
		private final int minElemSectID;
		
		private RSQSimFaultSystemRupSet(List<? extends FaultSection> subSects,
				List<SimulatorElement> elements, List<RSQSimEvent> events) {
			this(subSects, elements, events, 0d);
		}
		
		private RSQSimFaultSystemRupSet(List<? extends FaultSection> subSects,
				List<SimulatorElement> elements, List<RSQSimEvent> events, double minFractForInclusion) {
			this.elements = elements;
			this.events = events;
			minElemSectID = getSubSectIndexOffset(elements, subSects);
			
			RSQSimSubSectionMapper mapper = new RSQSimSubSectionMapper(subSects, elements, minFractForInclusion);
			
			// for each rup
			double[] mags = new double[events.size()];
			double[] rupRakes = new double[events.size()];
			double[] rupAreas = new double[events.size()];
			double[] rupLengths = new double[events.size()];
			List<List<Integer>> sectionForRups = Lists.newArrayList();

			System.out.print("Building ruptures...");
			for (int i=0; i<events.size(); i++) {
				RSQSimEvent e = events.get(i);
				mags[i] = e.getMagnitude();
				rupAreas[i] = e.getArea();
				rupLengths[i] = 0d;
				
				List<List<SubSectionMapping>> mappings = mapper.getFilteredSubSectionMappings(e);
				
				if (minFractForInclusion > 0 && mappings.isEmpty())
					// fallback to any that touch if empty
					mappings = mapper.getAllSubSectionMappings(e);

				List<Double> rakes = Lists.newArrayList();
				List<Integer> rupSectIndexes = Lists.newArrayList();
				for (List<SubSectionMapping> bundle : mappings) {
					for (SubSectionMapping mapping : bundle) {
						FaultSection subSect = mapping.getSubSect();
						rupSectIndexes.add(subSect.getSectionId());
						rakes.add(subSect.getAveRake());
						rupLengths[i] += subSect.getTraceLength()*1000d;
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
				FaultSection sect = subSects.get(s);
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
			
			SectionDistanceAzimuthCalculator distCalc = new SectionDistanceAzimuthCalculator(subSects);
			RuptureConnectionSearch search = new RuptureConnectionSearch(this, distCalc, 200d, false);
			addModule(ClusterRuptures.instance(this, search));
			
			HashSet<Jump> jumps = new HashSet<>();
			int maxSplays = 0;
			for (ClusterRupture rup : getModule(ClusterRuptures.class)) {
				for (Jump jump : rup.getJumpsIterable()) {
					jumps.add(jump);
					jumps.add(jump.reverse());
				}
				maxSplays = Integer.max(maxSplays, rup.getTotalNumSplays());
			}
			
			ClusterConnectionStrategy connStrat = new InputJumpsOrDistClusterConnectionStrategy(
					distCalc.getSubSections(), distCalc, 15d, jumps);
			PlausibilityConfiguration config = new PlausibilityConfiguration(new ArrayList<>(), maxSplays, connStrat, distCalc);
			addModule(config);
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
	
	private static class RSQSimFaultSystemSolution extends U3SlipEnabledSolution {
		
		private RSQSimFaultSystemRupSet rupSet;
		
		public RSQSimFaultSystemSolution(RSQSimFaultSystemRupSet rupSet) {
			super(rupSet, buildRatesArray(rupSet.events));
			this.rupSet = rupSet;
		}

		@Override
		public U3SlipEnabledRupSet getRupSet() {
			return rupSet;
		}
		
	}
	
	public static U3SlipEnabledSolution buildFaultSystemSolution(List<? extends FaultSection> subSects,
			List<SimulatorElement> elements, List<RSQSimEvent> events, double minMag) {
		return buildFaultSystemSolution(subSects, elements, events, minMag, 0d);
	}

	public static U3SlipEnabledSolution buildFaultSystemSolution(List<? extends FaultSection> subSects,
			List<SimulatorElement> elements, List<RSQSimEvent> events, double minMag, double minFractForInclusion) {
		
		if (minMag > 0)
			events = new MagRangeRuptureIdentifier(minMag, 10d).getMatches(events);
		
		RSQSimFaultSystemRupSet rupSet = new RSQSimFaultSystemRupSet(subSects, elements, events, minFractForInclusion);
		return new RSQSimFaultSystemSolution(rupSet);
	}
	
	public static void writeUCERF3ComparisonPlots(U3SlipEnabledSolution sol, FaultModels fm, DeformationModels dm,
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
		ArrayList<U3PaleoRateConstraint> paleoRateConstraints =
				CommandLineInversionRunner.getPaleoConstraints(fm, sol.getRupSet());
		List<U3AveSlipConstraint> aveSlipConstraints = U3AveSlipConstraint.load(sol.getRupSet().getFaultSectionDataList());
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
	
	public static void cleanVertFocalMechs(List<SimulatorElement> elems, List<? extends FaultSection> subSects) {
		int offset = getSubSectIndexOffset(elems, subSects);
		
		for (SimulatorElement elem : elems) {
			FocalMechanism mech = elem.getFocalMechanism();
			if (mech.getDip() == 90 && (mech.getRake() == -180 || mech.getRake() == 180 || mech.getRake() == 0)) {
				FaultSection sect = subSects.get(elem.getSectionID()-offset);
				mech.setRake(sect.getAveRake());
				double strike = mech.getStrike();
				double sectStrike = sect.getFaultTrace().getAveStrike();
				double strikeDelta = Math.abs(strike - sectStrike);
				strikeDelta = Math.min(strikeDelta, Math.abs(360 + strike - sectStrike));
				strikeDelta = Math.min(strikeDelta, Math.abs(strike - (sectStrike + 360)));
				if (strikeDelta > 135)
					strike += 180;
				while (strike > 360)
					strike -= 360;
				mech.setStrike(strike);
			}
		}
	}
	
	public static void writeSTLFile(List<SimulatorElement> elements, File file) throws IOException {
		double minLat = Double.POSITIVE_INFINITY;
		double minLon = Double.POSITIVE_INFINITY;
		double maxDepth = 0;
		for (SimulatorElement e : elements) {
			Preconditions.checkState(e instanceof TriangularElement, "STL only supports triangles");
			for (Location loc : e.getVertices()) {
				minLat = Math.min(minLat, loc.getLatitude());
				minLon = Math.min(minLon, loc.getLongitude());
				maxDepth = Math.max(maxDepth, loc.getDepth());
			}
		}
		Location refLoc = new Location(minLat, minLon);
//		double aveLat = 0;
//		double aveLon = 0;
//		for (SimulatorElement e : elements) {
//			Preconditions.checkState(e instanceof TriangularElement, "STL only supports triangles");
//			Location center = e.getCenterLocation();
//			aveLat += center.getLatitude();
//			aveLon += center.getLongitude();
//		}
//		aveLat /= elements.size();
//		aveLon /= elements.size();
//		Location refLoc = new Location(aveLat, aveLon);
		
		LittleEndianDataOutputStream out = new LittleEndianDataOutputStream(
				new BufferedOutputStream(new FileOutputStream(file)));
		
		/*
		 * Format:
		 * UINT8[80]  Header
		 * UINT32  Number of triangles
		 * 
		 * foreach triangle
		 * 	REAL32[3]  Normal vector
		 * 	REAL32[3]  Vertex 1
		 * 	REAL32[3]  Vertex 2
		 * 	REAL32[3]  Vertex 3
		 * 	UINT16  Attribute byte count
		 * end
		 */
		
		// starts with 80 byte header that is ignored
		out.write(new byte[80]);
		
		// number of triangles
		out.writeInt(elements.size());
		
		for (SimulatorElement e : elements) {
			double[][] vertices = new double[3][3];
			Vertex[] eVerts = e.getVertices();
			
			for (int i=0; i<3; i++) {
				LocationVector vector = LocationUtils.vector(refLoc, eVerts[i]);
				double az = vector.getAzimuthRad();
				double horzDist = vector.getHorzDistance();
				double x = horzDist*Math.sin(az);
				double y = horzDist*Math.cos(az);
//				double z = -eVerts[i].getDepth() + maxDepth;
				double z = -eVerts[i].getDepth();
				
				vertices[i][0] = x+0.01;
				vertices[i][1] = y+0.01;
				vertices[i][2] = z+0.01;
				
//				System.out.println(refLoc+" => "+eVerts[i]);
//				System.out.println("\t"+x+"\t"+y+"\t"+z);
				
				Preconditions.checkState(x >= 0, "bad x=%s", x);
				Preconditions.checkState(y >= 0, "bad y=%s", y);
//				Preconditions.checkState(z >= 0, "bad z=%s", z);
			}
			
			double[] normal = PlaneUtils.getNormalVector(vertices);
			
			for (double val : normal)
				out.writeFloat((float)val);
			for (double[] vert : vertices)
				for (double val : vert)
					out.writeFloat((float)val);
			// " "attribute byte count" in the standard format, this should be
			// zero because most software does not understand anything else."
			out.writeShort(0);
		}
		
		out.close();
	}
	
	/**
	 * Determines the FaultStyle for the given element, using rake tolerance. If the rake of the element is within
	 * rakeTolerance of either -180, 0, or +180, it is strike-slip, 90 it is reverse, and -90 it is normal. Otherwise
	 * UNKNOWN will be returned.
	 * @param elem
	 * @param rakeTolerance
	 * @return
	 */
	public static FaultStyle getFaultStyle(SimulatorElement elem, double rakeTolerance) {
		FocalMechanism mech = elem.getFocalMechanism();
		double rake = mech.getRake();
		Preconditions.checkState(rake >= -180d && rake <= 180d, "Bad rake: %s", rake);
		Preconditions.checkState(rakeTolerance >= 0d);
		if (rake <= -180+rakeTolerance || rake >= 180-rakeTolerance)
			return FaultStyle.STRIKE_SLIP;
		if (rake >= -rakeTolerance && rake <= rakeTolerance)
			return FaultStyle.STRIKE_SLIP;
		if (rake >= 90-rakeTolerance && rake <= 90+rakeTolerance)
			return FaultStyle.REVERSE;
		if (rake >= -90-rakeTolerance && rake <= -90+rakeTolerance)
			return FaultStyle.NORMAL;
		return FaultStyle.UNKNOWN;
	}
	
	/**
	 * Calculates the FaultStyle for the given rupture. The style is first determined on each element using the element rake and the
	 * give rake tolerance. If the event involves elements with multiple fault styles, then the dominant fault style is returned if at
	 * no more than maxFractOther fraction of all elements are of a different style. Otherwise, UNKNOWN is returned.
	 * @param event
	 * @param rakeTolerance
	 * @param maxFractOther
	 * @return
	 */
	public static FaultStyle calcFaultStyle(SimulatorEvent event, double rakeTolerance, double maxFractOther) {
		Map<FaultStyle, Integer> styleCounts = new HashMap<>();
		int numElems = 0;
		for (SimulatorElement elem : event.getAllElements()) {
			FaultStyle style = getFaultStyle(elem, rakeTolerance);
			Integer prevCount = styleCounts.containsKey(style) ? styleCounts.get(style) : 0;
			styleCounts.put(style, prevCount+1);
			numElems++;
		}
	
		Preconditions.checkState(!styleCounts.isEmpty());
		if (styleCounts.size() == 1)
			return styleCounts.keySet().iterator().next();
		for (FaultStyle style : styleCounts.keySet()) {
			double fract = (double)styleCounts.get(style) / numElems;
			if (fract >= 1d-maxFractOther)
				return style;
		}
		return FaultStyle.UNKNOWN;
	}
	
	public static void main(String[] args) throws IOException, GMT_MapException, RuntimeException {
//		File dir = new File("/home/kevin/Simulators/UCERF3_35kyrs");
//		File geomFile = new File(dir, "UCERF3.1km.tri.flt");
//		File dir = new File("/home/kevin/Simulators/UCERF3_125kyrs");
//		File geomFile = new File(dir, "UCERF3.D3.1.1km.tri.2.flt");
//		File dir = new File("/home/kevin/Simulators/bruce/rundir1435");
//		File geomFile = new File(dir, "zfault_Deepen.in");
//		File dir = new File("/home/kevin/Simulators/UCERF3_JG_supraSeisGeo2");
//		File geomFile = new File(dir, "UCERF3.D3.1.1km.tri.2.flt");
//		File dir = new File("/data/kevin/simulators/catalogs/rundir2194_long");
//		File geomFile = new File(dir, "zfault_Deepen.in");
		int catID = 5892;
		File dir = new File("/data/kevin/simulators/catalogs/bruce/rundir"+catID);
		File geomFile = new File(dir, "zfault_Deepen.in");
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'N');
		System.out.println("Loaded "+elements.size()+" elements");
		double minMag = 6d;
		int skipYears = 20000;
		List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(dir, elements,
				Lists.newArrayList(new LogicalAndRupIden(new SkipYearsLoadIden(skipYears),
						new MagRangeRuptureIdentifier(minMag, 10d))));
		
		List<? extends FaultSection> subSects = NSHM23_DeformationModels.AVERAGE.build(NSHM23_FaultModels.WUS_FM_v3);
		System.out.println("read "+subSects.size()+" sub sects");
		double sectFract = 0.5;
		FaultSystemSolution sol = buildFaultSystemSolution(subSects, elements, events, minMag, sectFract);
		sol.write(new File(dir, "rsqsim_"+catID+"_m"+new DecimalFormat("0.#").format(minMag)+"_skip"+skipYears+"_sectArea"+(float)sectFract+".zip"));
//		U3SlipEnabledSolution sol = buildFaultSystemSolution(subSects, elements, events, minMag, 0.5);
//		U3FaultSystemIO.writeSol(sol, new File(dir, "rsqsim_5133_m6_skip"+skipYears+"_sectArea0.5.zip"));
		
//		File stlFile = new File("/home/kevin/markdown/rsqsim-analysis/catalogs/"+dir.getName(), "geometry.stl");
//		writeSTLFile(elements, stlFile);
//		System.exit(0);
////		for (Location loc : elements.get(0).getVertices())
////			System.out.println(loc);
//		File eventDir = dir;
//		
//		double minMag = 6d;
//		List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(eventDir, elements,
//				Lists.newArrayList(new LogicalAndRupIden(new EventTimeIdentifier(5000d, Double.POSITIVE_INFINITY, true),
//						new MagRangeRuptureIdentifier(minMag, 10d))));
//		double duration = events.get(events.size()-1).getTimeInYears() - events.get(0).getTimeInYears();
//		System.out.println("First event time: "+events.get(0).getTimeInYears()+", duration: "+duration);
//		
//		FaultModels fm = FaultModels.FM3_1;
//		DeformationModels dm = DeformationModels.GEOLOGIC;
//		SlipEnabledSolution sol = buildFaultSystemSolution(getUCERF3SubSectsForComparison(
//				fm, dm), elements, events, minMag);
//		
//		File plotDir = new File(eventDir, "ucerf3_fss_comparison_plots");
//		Preconditions.checkState(plotDir.exists() ||  plotDir.mkdir());
////		MFDCalc.writeMFDPlots(elements, events, plotDir, new CaliforniaRegions.RELM_SOCAL(),
////				new CaliforniaRegions.RELM_NOCAL(), new CaliforniaRegions.LA_BOX(), new CaliforniaRegions.NORTHRIDGE_BOX(),
////				new CaliforniaRegions.SF_BOX(), new CaliforniaRegions.RELM_TESTING());
//		writeUCERF3ComparisonPlots(sol, fm, dm, plotDir, "rsqsim_comparison");
	}

}
