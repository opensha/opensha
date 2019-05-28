package scratch.kevin.simulators.erf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.iden.ElementMagRangeDescription;
import org.opensha.sha.simulators.iden.EventsInWindowsMatcher;
import org.opensha.sha.simulators.iden.QuietPeriodIdenMatcher;
import org.opensha.sha.simulators.iden.RuptureIdentifier;
import org.opensha.sha.simulators.parsers.EQSIMv06FileReader;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;
import org.opensha.sha.simulators.utils.SimulatorUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.utils.FaultSystemIO;

public class SimulatorFaultSystemSolution extends FaultSystemSolution {
	
	private List<? extends SimulatorEvent> events;
	private SubSectionBiulder subSectBuilder;
	
	public static SimulatorFaultSystemSolution build(List<SimulatorElement> elements, List<? extends SimulatorEvent> events,
			double durationYears) {
		SubSectionBiulder subSectBuilder = new SubSectionBiulder(elements);
		return build(subSectBuilder, events, durationYears);
	}
	
	public static SimulatorFaultSystemSolution build(SubSectionBiulder subSectBuilder, List<? extends SimulatorEvent> events,
			double durationYears)  {
		FaultSystemRupSet rupSet = buildRupSet(subSectBuilder.getElements(), events, durationYears, subSectBuilder);
		return new SimulatorFaultSystemSolution(rupSet, subSectBuilder, events, durationYears);
	}
	
	SimulatorFaultSystemSolution(FaultSystemRupSet rupSet, SubSectionBiulder subSectBuilder,
			List<? extends SimulatorEvent> events, double durationYears) {
		super(rupSet, buildRates(rupSet.getNumRuptures(), durationYears));
		this.events = events;
		this.subSectBuilder = subSectBuilder;
	}
	
	public List<double[]> getSlipAlongRupVals(boolean max) {
		List<double[]> vals = Lists.newArrayList();
		FaultSystemRupSet rupSet = getRupSet();
		Map<Integer, Integer> elemToSubMap = subSectBuilder.getElemIDToSubSectsMap();
		Preconditions.checkState(events.size() == rupSet.getNumRuptures());
		
		for (int i=0; i<events.size(); i++) {
			List<Integer> inds = rupSet.getSectionsIndicesForRup(i);
			List<List<Double>> elemMappedSlips = Lists.newArrayList();
			for (int j=0; j<inds.size(); j++)
				elemMappedSlips.add(new ArrayList<Double>());
			
			SimulatorEvent e = events.get(i);
			
			int[] elemIDs = e.getAllElementIDs();
			double[] elemSlips = e.getAllElementSlips();
			
			for (int j=0; j<e.getNumElements(); j++) {
				int mappedIndex = elemToSubMap.get(elemIDs[j]);
				double slip = elemSlips[j];
				
				int ind = inds.indexOf(mappedIndex);
				elemMappedSlips.get(ind).add(slip);
			}
			
			double[] slips = new double[inds.size()];
			for (int j=0; j<slips.length; j++) {
				List<Double> sectSlips = elemMappedSlips.get(j);
				if (sectSlips.isEmpty()) {
					slips[j] = 0;
				} else {
					if (max) {
						for (double slip : sectSlips)
							slips[j] = Math.max(slip, slips[j]);
					} else {
						for (double slip : sectSlips)
							slips[j] += slip;
						slips[j] /= (double)sectSlips.size();
					}
				}
			}
			vals.add(slips);
		}
		return vals;
	}
	
	static FaultSystemRupSet buildRupSet(List<SimulatorElement> elements, List<SimulatorEvent> events,
			double durationYears) {
		System.out.print("Building FSD...");
		SubSectionBiulder subSectBuilder = new SubSectionBiulder(elements);
		return buildRupSet(elements, events, durationYears, subSectBuilder);
	}
	
	static FaultSystemRupSet buildRupSet(List<SimulatorElement> elements, List<? extends SimulatorEvent> events,
			double durationYears, SubSectionBiulder subSectBuilder) {
		List<FaultSectionPrefData> fsd = subSectBuilder.getSubSectsList();
		Map<Integer, Integer> elemIDsMap = subSectBuilder.getElemIDToSubSectsMap();
		System.out.println("DONE.");
		
		// for each rup
		double[] mags = new double[events.size()];
		double[] rupRakes = new double[events.size()];
		double[] rupAreas = new double[events.size()];
		double[] rupLengths = new double[events.size()];
		List<List<Integer>> sectionForRups = Lists.newArrayList();
		
		Comparator<FaultSectionPrefData> fsdIndexSorter = new Comparator<FaultSectionPrefData>() {

			@Override
			public int compare(FaultSectionPrefData o1, FaultSectionPrefData o2) {
				return new Integer(o1.getSectionId()).compareTo(new Integer(o2.getSectionId()));
			}
		};
		
		// cache sub section distances, used for rupture section ordering later
//		System.out.print("Caching Distances...");
		Map<IDPairing, Double> distsCache = Maps.newHashMap();
//		for (int i=0; i<fsd.size(); i++) {
//			for (int j=i+1; j<fsd.size(); j++) {
//				if (i == j)
//					continue;
//				double minDist = Double.POSITIVE_INFINITY;
//				for (Location loc1 : fsd.get(i).getFaultTrace()) {
//					for (Location loc2 : fsd.get(j).getFaultTrace()) {
//						double dist = LocationUtils.horzDistance(loc1, loc2);
//						if (dist < minDist)
//							minDist = dist;
//					}
//				}
//				IDPairing pair = new IDPairing(i, j);
//				distsCache.put(pair, minDist);
//				distsCache.put(pair.getReversed(), minDist);
//			}
//		}
//		System.out.println("DONE.");
		
//		Table<String, String, Double> distsCache = HashBasedTable.create();
		
		System.out.print("Building ruptures...");
		for (int i=0; i<events.size(); i++) {
			SimulatorEvent e = events.get(i);
			mags[i] = e.getMagnitude();
			rupAreas[i] = e.getArea();
			rupLengths[i] = e.getLength();
			
			// build rupture sections list
			List<List<FaultSectionPrefData>> subSectsForFaults = Lists.newArrayList();
			for (EventRecord rec : e) {
				List<FaultSectionPrefData> subSectsForFault = Lists.newArrayList();
				HashSet<FaultSectionPrefData> subSectsForFaultSet = new HashSet<FaultSectionPrefData>();
				for (int elemID : rec.getElementIDs()) {
					Preconditions.checkState(elemIDsMap.get(elemID) != null,
							"No mapping for "+elemID+"...map size: "+elemIDsMap.size());
					subSectsForFaultSet.add(fsd.get(elemIDsMap.get(elemID)));
				}
				subSectsForFault.addAll(subSectsForFaultSet);
				Collections.sort(subSectsForFault, fsdIndexSorter);
				
				subSectsForFaults.add(subSectsForFault);
			}
			
			// now make sure that they're in order by minimizing jump distances
			if (subSectsForFaults.size() > 1) {
				subSectsForFaults = sortRupture(fsd, subSectsForFaults, distsCache);
			}
			
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
		double[] sectSlipRates = new double[fsd.size()];
		double[] sectSlipRateStdDevs = null;
		double[] sectAreas = new double[fsd.size()];
		
		for (int s=0; s<fsd.size(); s++) {
			FaultSectionPrefData sect = fsd.get(s);
			sectSlipRates[s] = sect.getReducedAveSlipRate();
			sectAreas[s] = sect.getReducedDownDipWidth()*sect.getTraceLength()*1e6; // in meters
		}
		
		String info = "Fault Simulators Solution\n"
				+ "# Elements: "+elements.size()+"\n"
				+ "# Sub Sections: "+fsd.size()+"\n"
				+ "# Events/Rups: "+events.size()+"\n"
				+ "Duration: "+durationYears+"\n"
				+ "Indv. Rup Rate: "+(1d/durationYears);
		
		return new FaultSystemRupSet(fsd, sectSlipRates, sectSlipRateStdDevs, sectAreas,
				sectionForRups,mags, rupRakes, rupAreas, rupLengths, info);
	}
	
	private static double[] buildRates(int num, double durationYears) {
		double rupRate = 1/durationYears;
		double[] rates = new double[num];
		for (int i=0; i<num; i++)
			rates[i] = rupRate;
		return rates;
	}
	
	private static double calcDistance(List<FaultSectionPrefData> fsd, IDPairing pairing, Map<IDPairing, Double> distsCache) {
		Double cachedDist = distsCache.get(pairing);
		if (cachedDist != null)
			return cachedDist;
		double minDist = Double.POSITIVE_INFINITY;
		for (Location loc1 : fsd.get(pairing.getID1()).getFaultTrace()) {
			for (Location loc2 : fsd.get(pairing.getID2()).getFaultTrace()) {
				double dist = LocationUtils.horzDistance(loc1, loc2);
				if (dist < minDist)
					minDist = dist;
			}
		}
		distsCache.put(pairing, minDist);
		distsCache.put(pairing.getReversed(), minDist);
		return minDist;
	}
	
	public static List<List<FaultSectionPrefData>> sortRupture(
			List<FaultSectionPrefData> fsd,
			List<List<FaultSectionPrefData>> subSectsForFaults,
			Map<IDPairing, Double> distsCache) {
		return sortRupture(fsd, subSectsForFaults, distsCache, null);
	}
	
	public static List<List<FaultSectionPrefData>> sortRupture(
			List<FaultSectionPrefData> fsd,
			List<List<FaultSectionPrefData>> subSectsForFaults,
			Map<IDPairing, Double> distsCache, Collection<FaultSectionPrefData> reversedSections) {
		// select the most isolated endpoint as the starting point
		double isolatedMaxDist = 0;
		int isolatedFaultIndex = -1;
		boolean isolatedFaultStart = false;
		
		// make sure that each list is actually a different fault
		HashSet<Integer> parents = new HashSet<Integer>();
		for (int i=0; i<subSectsForFaults.size(); i++) {
			List<FaultSectionPrefData> subSects = subSectsForFaults.get(i);
			int parent = subSects.get(0).getParentSectionId();
			for (int j=1; j<subSects.size(); j++)
				Preconditions.checkState(parent == subSects.get(j).getParentSectionId());
			Preconditions.checkState(!parents.contains(parent));
			parents.add(parent);
		}
		
		for (int j=0; j<subSectsForFaults.size(); j++) {
			List<FaultSectionPrefData> subSects1 = subSectsForFaults.get(j);
			int startID1 = subSects1.get(0).getSectionId();
			int endID1 = subSects1.get(subSects1.size()-1).getSectionId();
			
			double myStartMaxDist = Double.POSITIVE_INFINITY;
			double myEndMaxDist = Double.POSITIVE_INFINITY;
			for (int k=0; k<subSectsForFaults.size(); k++) {
				if (j == k)
					continue;
				List<FaultSectionPrefData> subSects2 = subSectsForFaults.get(k);
				int startID2 = subSects2.get(0).getSectionId();
				int endID2 = subSects2.get(subSects2.size()-1).getSectionId();
				
				double startStartDist = calcDistance(fsd, new IDPairing(startID1, startID2), distsCache);
				if (startStartDist > isolatedMaxDist) {
					isolatedMaxDist = startStartDist;
					isolatedFaultIndex = j;
					isolatedFaultStart = true;
				}
				double startEndDist = calcDistance(fsd, new IDPairing(startID1, endID2), distsCache);
				if (startEndDist > isolatedMaxDist) {
					isolatedMaxDist = startEndDist;
					isolatedFaultIndex = j;
					isolatedFaultStart = true;
				}
				double endStartDist = calcDistance(fsd, new IDPairing(endID1, startID2), distsCache);
				if (endStartDist > isolatedMaxDist) {
					isolatedMaxDist = endStartDist;
					isolatedFaultIndex = j;
					isolatedFaultStart = false;
				}
				double endEndDist = calcDistance(fsd, new IDPairing(endID1, endID2), distsCache);
				if (endEndDist > isolatedMaxDist) {
					isolatedMaxDist = endEndDist;
					isolatedFaultIndex = j;
					isolatedFaultStart = false;
				}
				
//				if (startStartDist > myStartMaxDist)
//					myStartMaxDist = startStartDist;
//				if (startEndDist > myStartMaxDist)
//					myStartMaxDist = startEndDist;
//				if (endStartDist > myEndMaxDist)
//					myEndMaxDist = endStartDist;
//				if (endEndDist > myEndMaxDist)
//					myEndMaxDist = endEndDist;
//				System.out.println(startID1+"\t"+endID1+"\t"+startID2+"\t"+endID2);
//				System.out.println(startStartDist+"\t"+startEndDist+"\t"+endStartDist+"\t"+endEndDist);
			}
//			double myMaxDist;
//			boolean myStart;
//			if (myStartMaxDist < myEndMaxDist) {
//				myMaxDist = myEndMaxDist;
//				myStart = false;
//			} else {
//				myMaxDist = myStartMaxDist;
//				myStart = true;
//			}
//			if (myMaxDist > isolatedMaxDist) {
//				// this is the most isolated
//				isolatedMaxDist = myMaxDist;
//				isolatedFaultIndex = j;
//				isolatedFaultStart = myStart;
//			}
//			System.out.println(j+". "+myMinDist+" (overall: "+isolatedMinDist+")");
		}
		
		if (isolatedFaultIndex < 0) {
			isolatedFaultIndex = 0;
			isolatedFaultStart = true;
		}
		
		List<List<FaultSectionPrefData>> sortedFaults = Lists.newArrayList();
		// add the first fault
		List<FaultSectionPrefData> curFault = subSectsForFaults.remove(isolatedFaultIndex);
		if (!isolatedFaultStart) {
			// this means we're starting on the end of this one, reverse it
			Collections.reverse(curFault);
			if (reversedSections != null)
				reversedSections.addAll(curFault);
		}
		sortedFaults.add(curFault);
		int curEndID = curFault.get(0).getSectionId();
		
		while (!subSectsForFaults.isEmpty()) {
			// now find the shortest jump for the current end
			double minDist = Double.POSITIVE_INFINITY;
			int closestFaultIndex = -1;
			boolean closestAtStart = false;
			
			for (int i=0; i<subSectsForFaults.size(); i++) {
				List<FaultSectionPrefData> faultSects = subSectsForFaults.get(i);
				double startDist = calcDistance(fsd, new IDPairing(curEndID,
						faultSects.get(0).getSectionId()), distsCache);
				double endDist = calcDistance(fsd, new IDPairing(curEndID,
						faultSects.get(faultSects.size()-1).getSectionId()), distsCache);
				
				if (startDist < minDist) {
					minDist = startDist;
					closestFaultIndex = i;
					closestAtStart = true;
				}
				if (endDist < minDist) {
					minDist = endDist;
					closestFaultIndex = i;
					closestAtStart = false;
				}
			}
			
			curFault = subSectsForFaults.remove(closestFaultIndex);
			if (!closestAtStart) {
				Collections.reverse(curFault);
				if (reversedSections != null)
					reversedSections.addAll(curFault);
			}
			sortedFaults.add(curFault);
			curEndID = curFault.get(0).getSectionId();
		}
		
		return sortedFaults;
	}
	
	public static void main(String[] args) throws IOException {
		File dir = new File("/home/kevin/Simulators");
		File geomFile = new File(dir, "ALLCAL2_1-7-11_Geometry.dat");
		System.out.println("Loading geometry...");
		General_EQSIM_Tools tools = new General_EQSIM_Tools(geomFile);
//		File eventFile = new File(dir, "eqs.ALLCAL2_RSQSim_sigma0.5-5_b=0.015.barall");
		File eventFile = new File(dir, "eqs.ALLCAL2_RSQSim_sigma0.5-5_b=0.015.long.barall");
		System.out.println("Loading events...");
		List<? extends SimulatorEvent> events = EQSIMv06FileReader.readEventsFile(eventFile, tools.getElementsList());
		
		double durationYears = SimulatorUtils.getSimulationDurationYears(events);
		
		Region region = null;
//		Region region = new CaliforniaRegions.RELM_SOCAL();
		
//		RuptureIdentifier rupIden = null;
		ElementMagRangeDescription cholameIden = new ElementMagRangeDescription("SAF Cholame 7+",
				ElementMagRangeDescription.SAF_CHOLAME_ELEMENT_ID, 7d, 10d);
		ElementMagRangeDescription carrizoIden = new ElementMagRangeDescription("SAF Carrizo 7+",
				ElementMagRangeDescription.SAF_CARRIZO_ELEMENT_ID, 7d, 10d);
		ElementMagRangeDescription mojaveIden = new ElementMagRangeDescription("SAF Mojave 7+",
				ElementMagRangeDescription.SAF_MOJAVE_ELEMENT_ID, 7d, 10d);
		ElementMagRangeDescription coachellaIden = new ElementMagRangeDescription("SAF Coachella 7+",
				ElementMagRangeDescription.SAF_COACHELLA_ELEMENT_ID, 7d, 10d);
		
		ElementMagRangeDescription mojaveCoachellCorupture = new ElementMagRangeDescription("SAF Mojave/Coachella Corupture 7+",
				6d, 10d, ElementMagRangeDescription.SAF_MOJAVE_ELEMENT_ID, ElementMagRangeDescription.SAF_COACHELLA_ELEMENT_ID);
		
		double quietYears = 156;
		double forecastYears = 30;
		
//		RuptureIdentifier rupIden = new QuietPeriodIdenMatcher(mojaveIden, 5, quietYears,
//				cholameIden, carrizoIden, mojaveIden, coachellaIden);
		RuptureIdentifier rupIden = null;
		
		if (rupIden != null) {
			EventsInWindowsMatcher matcher = new EventsInWindowsMatcher(events, rupIden, quietYears, quietYears+forecastYears, false);
			events = matcher.getEventsInWindows();
			durationYears = matcher.getTotalWindowDurationYears();
			System.out.println("New duration: "+durationYears+" ("+events.size()
					+" events in "+matcher.getMatchIDs().size()+" matches)");
		}
		
		if (region != null) {
			Map<Integer, Boolean> elementsInRegionsCache = Maps.newHashMap();
			
			// just uese centers since they're small enough elements
			for (SimulatorElement elem : tools.getElementsList())
				elementsInRegionsCache.put(elem.getID(), region.contains(elem.getCenterLocation()));
			
			List<SimulatorEvent> eventsInRegion = Lists.newArrayList();
			for (SimulatorEvent e : events) {
				for (int elemID : e.getAllElementIDs()) {
					if (elementsInRegionsCache.get(elemID)) {
						eventsInRegion.add(e);
						break;
					}
				}
			}
			
			System.out.println(eventsInRegion.size()+"/"+events.size()+" events in region: "+region.getName());
			events = eventsInRegion;
		}
		
		SimulatorFaultSystemSolution fss = build(tools.getElementsList(), events, durationYears);
		System.out.println(fss.getInfoString());
		
//		FaultSystemIO.writeSol(fss, new File("/tmp/simulators_long_sol_mojave_trigger_quiet_156_wind_30_yr.zip"));
//		FaultSystemIO.writeSol(fss, new File("/tmp/simulators_long_sol.zip"));
		
		Map<String, Integer> rupCounts = Maps.newHashMap();
		FaultSystemRupSet rupSet = fss.getRupSet();
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			String key = uniqueRupHash(rupSet.getSectionsIndicesForRup(r));
			Integer count = rupCounts.get(key);
			if (count == null)
				count = 0;
			count += 1;
			rupCounts.put(key, count);
		}
		MinMaxAveTracker track = new MinMaxAveTracker();
		HistogramFunction hist = new HistogramFunction(1d, 10, 1d);
		int tot = 0;
		for (String key : rupCounts.keySet()) {
			int count = rupCounts.get(key);
			tot += count;
			track.addValue(count);
			hist.add(hist.getClosestXIndex(count), 1d);
		}
		System.out.println(rupCounts.size()+"/"+tot+" unique rupture");
		System.out.println(track);
		System.out.println(hist);
	}
	
	private static final Joiner j = Joiner.on("_");
	private static String uniqueRupHash(List<Integer> sects) {
		sects = Lists.newArrayList(sects);
		Collections.sort(sects);
		return j.join(sects);
	}

}
