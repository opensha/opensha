package scratch.UCERF3.utils.finiteFaultMap;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.DataUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.finiteFaultMap.JeanneFileLoader.LocComparator;

/**
 * This class maps Jeanne's Finite Fault surfaces to the closest UCERF3 ruptures. It has been validated in SCEC-VDO to provide
 * correct mappings for FM3.1 and FM3.2 when filterLastEventParents=true and matchLastEventExactly=true
 * @author kevin
 *
 */
public class FiniteFaultMapper {
	
	private static final boolean D = true;
	
	private FaultSystemRupSet rupSet;
	
	// max difference in fault length allowed
	private static final double maxLengthDiff = 50d;
	// max difference in rupture center
	private static final double maxCenterDist = 30d;
	// maximum distance between any 2 points on the surfaces
	private static final double maxAnyDist = 50d;
	// maximum median distance
	private static final double maxMedianDist = 100d;
	// must have at least one point within this threshold
	private static final double minDistThresh = 5d;
	
	
	private static final int numSurfLocsToCheck = 500;
	private static final double surfSpacing = 5d;
	
	private RuptureSurface[] surfs;
	private Location[] centers;
	private double[] lengths;
	
	private boolean filterLastEventParents = true;
	
	private boolean matchLastEventExactly = true;
	
	private static final boolean use_sq_dist = true;
	
	private Map<Integer, List<FaultSectionPrefData>> parentSectsMap;
	
	// sorted with most recent first
	private List<FaultSectionPrefData> sectsWithDate;
	private List<Long> dates;
	
	public FiniteFaultMapper(FaultSystemRupSet rupSet, boolean filterLastEventParents, boolean matchLastEventExactly) {
		this.rupSet = rupSet;
		this.filterLastEventParents = filterLastEventParents;
		this.matchLastEventExactly = matchLastEventExactly;
		surfs = new RuptureSurface[rupSet.getNumRuptures()];
		centers = new Location[rupSet.getNumRuptures()];
		lengths = new double[rupSet.getNumRuptures()];
		
		for (int i=0; i<rupSet.getNumRuptures(); i++) {
			lengths[i] = rupSet.getLengthForRup(i)/1000d; // convert to km
			surfs[i] = rupSet.getSurfaceForRupupture(i, surfSpacing, false);
			centers[i] = calcCenter(surfs[i]);
		}
		
		parentSectsMap = Maps.newHashMap();
		for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
			List<FaultSectionPrefData> sects = parentSectsMap.get(sect.getParentSectionId());
			if (sects == null) {
				sects = Lists.newArrayList();
				parentSectsMap.put(sect.getParentSectionId(), sects);
			}
			sects.add(sect);
		}
	}
	
	private static Location calcCenter(RuptureSurface surf) {
		double lat = 0;
		double lon = 0;
		double depth = 0;
		int num = 0;
		for (Location loc : surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
			lat += loc.getLatitude();
			lon += loc.getLongitude();
			depth += loc.getDepth();
			num++;
		}
		lat /= num;
		lon /= num;
		depth /= num;
		
		return new Location(lat, lon, depth);
	}
	
	private void initSectsWithDate() {
		sectsWithDate = Lists.newArrayList();
		dates = Lists.newArrayList();
		
		for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
			long date = sect.getDateOfLastEvent();
			if (date == Long.MIN_VALUE)
				continue;
			sectsWithDate.add(sect);
			dates.add(date);
		}
		
		// now sort by epoch time decreasing
		List<ComparablePairing<Long, FaultSectionPrefData>> comps = ComparablePairing.build(dates, sectsWithDate);
		Collections.sort(comps);
		Collections.reverse(comps);
		
		sectsWithDate = Lists.newArrayList();
		dates = Lists.newArrayList();
		
		for (ComparablePairing<Long, FaultSectionPrefData> comp : comps) {
			sectsWithDate.add(comp.getData());
			dates.add(comp.getComparable());
		}
	}
	
	private HashSet<Integer> getViableParentIDs(long eventDate) {
		if (sectsWithDate == null)
			initSectsWithDate();
		
		// pad event date by a year since last event dates are rounded
		eventDate -= ProbabilityModelsCalc.MILLISEC_PER_YEAR;
		
		HashSet<Integer> parentIDs = new HashSet<Integer>();
		
		for (int i=0; i<sectsWithDate.size(); i++) {
			long sectDate = dates.get(i);
			if (sectDate < eventDate)
				break;
			
			parentIDs.add(sectsWithDate.get(i).getParentSectionId());
		}
		
		return parentIDs;
	}
	
	public int getMappedRup(ObsEqkRupture rup) {
		Stopwatch watch = null;
		if (D) watch = Stopwatch.createStarted();
		RuptureSurface surf = rup.getRuptureSurface();
		Preconditions.checkNotNull(surf);
		double length = surf.getAveLength();
		Location center = calcCenter(surf);
		
		if (D) System.out.println("**********************************");
		
		if (D) System.out.println("Loading cadidate for "+JeanneFileLoader.getRupStr(rup)+". Len="+length+". Center: "+center);
		
		HashSet<Integer> parentIDs = null;
		if (filterLastEventParents) {
			parentIDs = getViableParentIDs(rup.getOriginTime());
			if (parentIDs.isEmpty()) {
				System.out.println("No viable parents for M"+rup.getMag()+" at "+rup.getOriginTimeCal().getTime());
				return -1;
			}
		}
		
		List<Integer> candidates = Lists.newArrayList();
		int numFilteredParents = 0;
		rupLoop:
		for (int i=0; i<rupSet.getNumRuptures(); i++) {
			// check length within possible range
			double lengthDiff = Math.abs(lengths[i] - length);
			if (lengthDiff > maxLengthDiff)
				continue;
			// check center distance
			double hDist = LocationUtils.horzDistance(center, centers[i]);
			if (hDist > maxCenterDist)
				continue;
			if (parentIDs != null) {
				for (int parentID : rupSet.getParentSectionsForRup(i)) {
					if (!parentIDs.contains(parentID)) {
						numFilteredParents++;
						continue rupLoop;
					}
				}
			}
			candidates.add(i);
		}
		
		if (D) System.out.println("Found "+candidates.size()+" candidates ("+numFilteredParents+" filtered due to parents)");
		
		List<Location> surfLocs = Lists.newArrayList();
		if (surf instanceof EvenlyGriddedSurface) {
			EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surf;
			for (int col=0; col<gridSurf.getNumCols(); col++)
				for (int row=0; row<gridSurf.getNumRows(); row++)
					surfLocs.add(gridSurf.get(row, col));
		} else {
			Location loc1 = surf.getFirstLocOnUpperEdge();
			Location loc2 = surf.getFirstLocOnUpperEdge();
			double latDelta = Math.abs(loc1.getLatitude() - loc2.getLatitude());
			double lonDelta = Math.abs(loc1.getLongitude() - loc2.getLongitude());
			LocComparator comp = new LocComparator(latDelta > lonDelta);
			surfLocs.addAll(surf.getEvenlyDiscritizedListOfLocsOnSurface());
			Collections.sort(surfLocs, comp);
		}
		List<Location> surfLocsToCheck;
		if (numSurfLocsToCheck >= surfLocs.size()) {
			surfLocsToCheck = surfLocs;
		} else {
			surfLocsToCheck = Lists.newArrayList();
			int mod = (int)((double)surfLocs.size()/(double)numSurfLocsToCheck);
			for (int i=0; i<surfLocs.size(); i++)
				if (i % mod == 0)
					surfLocsToCheck.add(surfLocs.get(i));
		}
		
		if (D) System.out.println("Checking distance of "+surfLocsToCheck.size()+"/"+surfLocs.size()+" surf pts");
		
		List<Double> means = Lists.newArrayList();
		List<Double> squaredMeans = Lists.newArrayList();
		List<Double> medians = Lists.newArrayList();
		List<double[]> allDists = Lists.newArrayList();
		List<Integer> sortIndexes = Lists.newArrayList(); // will be used to map back to candidate index
		
		candidateLoop:
		for (int i=0; i<candidates.size(); i++) {
			int rupIndex = candidates.get(i);
			RuptureSurface candidate = surfs[rupIndex];
			double[] surfToCandidateDistances = new double[surfLocsToCheck.size()];
			for (int j=0; j<surfLocsToCheck.size(); j++) {
				surfToCandidateDistances[j] = candidate.getDistanceRup(surfLocsToCheck.get(j));
				if (surfToCandidateDistances[j] > maxAnyDist) {
					means.add(Double.POSITIVE_INFINITY);
					squaredMeans.add(Double.POSITIVE_INFINITY);
					medians.add(Double.POSITIVE_INFINITY);
					allDists.add(null);
					sortIndexes.add(i);
					continue candidateLoop;
				}
			}
			
			// now candidate to surf distances
			List<Location> candidateDistsToCheck;
			List<Location> candidateUpperEdge = candidate.getEvenlyDiscritizedUpperEdge();
			if (candidateUpperEdge.size() < numSurfLocsToCheck) {
				candidateDistsToCheck = candidateUpperEdge;
			} else {
				candidateDistsToCheck = Lists.newArrayList();
				int mod = (int)((double)candidateUpperEdge.size()/(double)numSurfLocsToCheck);
				for (int j=0; j<candidateUpperEdge.size(); j++)
					if (j % mod == 0)
						surfLocsToCheck.add(candidateUpperEdge.get(j));
			}
			double[] candidateToSurfDistances = new double[candidateDistsToCheck.size()];
			for (int j=0; j<candidateDistsToCheck.size(); j++) {
				candidateToSurfDistances[j] = surf.getDistanceRup(candidateDistsToCheck.get(j));
				if (candidateToSurfDistances[j] > maxAnyDist) {
					means.add(Double.POSITIVE_INFINITY);
					squaredMeans.add(Double.POSITIVE_INFINITY);
					medians.add(Double.POSITIVE_INFINITY);
					allDists.add(null);
					sortIndexes.add(i);
					continue candidateLoop;
				}
			}
			double[] combDists = new double[surfToCandidateDistances.length+candidateToSurfDistances.length];
			System.arraycopy(surfToCandidateDistances, 0, combDists, 0, surfToCandidateDistances.length);
			System.arraycopy(candidateToSurfDistances, 0, combDists, surfToCandidateDistances.length, candidateToSurfDistances.length);
			
			double median = DataUtils.median(combDists);
			
			if (StatUtils.min(combDists) > minDistThresh || median > maxMedianDist) {
				means.add(Double.POSITIVE_INFINITY);
				squaredMeans.add(Double.POSITIVE_INFINITY);
				medians.add(Double.POSITIVE_INFINITY);
				allDists.add(null);
				sortIndexes.add(i);
				continue candidateLoop;
			}
			
			means.add(0.5*StatUtils.mean(surfToCandidateDistances) + 0.5*StatUtils.mean(candidateToSurfDistances));
			squaredMeans.add(0.5*sq_mean(surfToCandidateDistances) + 0.5*sq_mean(candidateToSurfDistances));
			medians.add(median);
			allDists.add(combDists);
			sortIndexes.add(i);
		}
		
		List<ComparablePairing<Double, Integer>> pairings;
		if (use_sq_dist)
			pairings = ComparablePairing.build(squaredMeans, sortIndexes);
		else
			pairings = ComparablePairing.build(means, sortIndexes);
		Collections.sort(pairings);
		
		for (int i=0; i<5 && i<pairings.size() && D; i++) {
			ComparablePairing<Double, Integer> pairing = pairings.get(i);
			if (Double.isInfinite(pairing.getComparable()))
				 break;
			int index = pairing.getData();
			double mean = means.get(index);
			double sqMean = squaredMeans.get(index);
			double median = medians.get(index);
			double[] dists = allDists.get(index);
			double min = StatUtils.min(dists);
			double max = StatUtils.max(dists);
			int rupIndex = candidates.get(index);
			
			
			List<String> parents = Lists.newArrayList();
			
			for (FaultSectionPrefData sect : rupSet.getFaultSectionDataForRupture(rupIndex)) {
				String parentName = sect.getParentSectionName();
				if (parents.isEmpty() || !parents.get(parents.size()-1).equals(parentName))
					parents.add(parentName);
			}
			
			String parStr = Joiner.on("; ").join(parents);
			
			if (D) System.out.println("Match "+i+". Mean="+(float)mean+". Sq Mean="+(float)sqMean+". Median="+(float)median
					+". Range=["+(float)min+" "+(float)max+"]. Mag="+rupSet.getMagForRup(rupIndex)
					+". Len="+lengths[rupIndex]+". Center: "+centers[rupIndex]+". Parents: "+parStr);
		}
		
		if (D) {
			watch.stop();
			System.out.println("Took "+watch.elapsed(TimeUnit.SECONDS)+" seconds to find match");
		}
		
		if (pairings.isEmpty())
			return -1;
		
		ComparablePairing<Double, Integer> pairing = pairings.get(0);
		
		if (pairing.getComparable().isInfinite())
			return -1;
		
		int rupIndex = candidates.get(pairing.getData());
		
		if (matchLastEventExactly) {
			if (D) System.out.println("Adjusting to match date of last event data exactly");
			List<FaultSectionPrefData> subSectsWithData = Lists.newArrayList();
			
			long closestDateToEvent = -1;
			long closestDelta = Long.MAX_VALUE;
			
			List<FaultSectionPrefData> sectsForRup = rupSet.getFaultSectionDataForRupture(rupIndex);
			
			for (FaultSectionPrefData sect : sectsForRup) {
				if (sect.getDateOfLastEvent() != Long.MIN_VALUE) {
					long delta = rup.getOriginTime() - sect.getDateOfLastEvent();
					if (delta < 0l)
						delta = -delta;
					if (delta < closestDelta) {
						closestDateToEvent = sect.getDateOfLastEvent();
						closestDelta = delta;
					}
				}
			}
			
			for (FaultSectionPrefData sect : sectsForRup)
				if (sect.getDateOfLastEvent() >= closestDateToEvent)
					subSectsWithData.add(sect);
			
			if (D) System.out.println("Removing "+(sectsForRup.size() - subSectsWithData.size())
					+" sects without data (or with earlier date). Date: "+new Date(closestDateToEvent));
			
			HashSet<Integer> sectsToInclude = new HashSet<Integer>();
			for (FaultSectionPrefData sect : subSectsWithData)
				sectsToInclude.add(sect.getSectionId());
			
			// now look for others that were excluded, but only if the rupture isn't completely masked
			if (closestDelta < 2l*ProbabilityModelsCalc.MILLISEC_PER_YEAR) {
				List<String> newNames = Lists.newArrayList();
				for (FaultSectionPrefData sect : subSectsWithData) {
					// now look at others on same parent
					for (FaultSectionPrefData oSect : parentSectsMap.get(sect.getParentSectionId())) {
						if (sectsToInclude.contains(oSect.getSectionId()))
							continue;
						if (oSect.getDateOfLastEvent() == closestDateToEvent) {
							sectsToInclude.add(oSect.getSectionId());
							newNames.add(oSect.getName());
						}
					}
				}
				
				if (D) System.out.println("Adding "+(sectsToInclude.size() - subSectsWithData.size())
						+" sects that match date but were excluded: "+j.join(newNames));
			} else {
				System.out.println("Not adding any more as rupture is masked in last event data");
			}
			if (sectsToInclude.size() < 2) {
				if (D) System.out.println("Too few subsections, skipping");
				rupIndex = -1;
			} else {
				rupIndex = getRuptureForSects(sectsToInclude);
			}
		}
		
		if (rupIndex >= 0) {
			// check for smaller aftershocks being mapped to full main shock surfaces
			
			// no abs, this is just for detecting aftershocks that get mapped to larger'
			double fssMag = rupSet.getMagForRup(rupIndex);
			double magDelta = fssMag - rup.getMag();
			if (fssMag > 7d && magDelta > 0.5) {
				if (D) System.out.println("Throwing out match as this is likely a smaller aftershock. Mag Delta: "
						+fssMag+" - "+rup.getMag()+" = "+magDelta);
				rupIndex = -1;
			}
		}
		
		if (D) System.out.println("**********************************");
		
		return rupIndex;
	}
	
	private Map<String, Integer> sectHashes = null;
	
	private int getRuptureForSects(HashSet<Integer> sects) {
		if (sectHashes == null) {
			sectHashes = Maps.newHashMap();
			for (int i=0; i<rupSet.getNumRuptures(); i++)
				sectHashes.put(getSectsKey(rupSet.getSectionsIndicesForRup(i)), i);
		}
		
		String key = getSectsKey(sects);
		Integer rupIndex = sectHashes.get(key);
		
		if (rupIndex == null) {
			if (D) System.out.println("Rupture doesn't exist, trying subsets");
			HashSet<Integer> parents = new HashSet<Integer>();
			for (int sect : sects)
				parents.add(rupSet.getFaultSectionData(sect).getParentSectionId());
			
			HashSet<Integer> candidateRups = new HashSet<Integer>();
			for (int parentID : parents)
				candidateRups.addAll(rupSet.getRupturesForParentSection(parentID));
			
			int mostSects = 0;
			int matchIndex = -1;
			candidateLoop:
			for (int rup : candidateRups) {
				List<Integer> oSects = rupSet.getSectionsIndicesForRup(rup);
				if (oSects.size() > sects.size() || oSects.size() <= mostSects)
					continue;
				for (int sectIndex : oSects)
					if (!sects.contains(sectIndex))
						continue candidateLoop;
				mostSects = oSects.size();
				matchIndex = rup;
			}
			Preconditions.checkState(matchIndex > 0, "No subset match either!");
			List<String> excludedNames = Lists.newArrayList();
			HashSet<Integer> newSects = new HashSet<Integer>(rupSet.getSectionsIndicesForRup(matchIndex));
			for (int sect : sects)
				if (!newSects.contains(sect))
					excludedNames.add(rupSet.getFaultSectionData(sect).getName());
			if (D) System.out.println("Found match by removing: "+j.join(excludedNames));
			rupIndex = matchIndex;
		}
		Preconditions.checkNotNull(rupIndex, "No match for rupture: "+key);
		return rupIndex;
	}
	
	private static final Joiner j = Joiner.on(",");
	
	private static String getSectsKey(Collection<Integer> sects) {
		List<Integer> sorted = Lists.newArrayList(sects);
		Collections.sort(sorted);
		return j.join(sorted);
	}
	
	private static double sq_mean(double[] array) {
		double mean = 0;
		for (double val : array)
			mean += val*val;
		mean /= array.length;
		return mean;
	}

	public static void main(String[] args) throws IOException, DocumentException {
		File finiteFile = new File("/home/kevin/OpenSHA/UCERF3/historical_finite_fault_mapping/UCERF3_finite.dat");
		ObsEqkRupList inputRups = UCERF3_CatalogParser.loadCatalog(
				new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/EarthquakeCatalog/ofr2013-1165_EarthquakeCat.txt"));
		for (ObsEqkRupture rup : inputRups) {
			if (rup.getHypocenterLocation().getDepth() > 24 && rup.getMag() >= 4)
				System.out.println(rup.getHypocenterLocation()+", mag="+rup.getMag());
		}
		
		List<ObsEqkRupture> finiteRups = JeanneFileLoader.loadFiniteRups(finiteFile, inputRups);
//		finiteRups = finiteRups.subList(0, 1);
		System.out.println("Loaded "+finiteRups.size()+" finite rups");
		
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(new File("/home/kevin/workspace/OpenSHA/dev/scratch/"
				+ "UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_"
				+ "FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		
		FiniteFaultMapper mapper = new FiniteFaultMapper(rupSet, true, true);
		
		for (ObsEqkRupture rup : finiteRups)
			mapper.getMappedRup(rup);
	}

}
