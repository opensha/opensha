package scratch.UCERF3.inversion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.inversion.laughTest.BuggyCoulombFilter;
import scratch.UCERF3.inversion.laughTest.CoulombFilter;
import scratch.UCERF3.inversion.laughTest.AbstractPlausibilityFilter;
import scratch.UCERF3.inversion.laughTest.UCERF3PlausibilityConfig;
import scratch.UCERF3.inversion.laughTest.OldPlausibilityConfiguration;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * 
 * This assumes that the ith FaultSectionPrefData object in the sectionDataList list has an Id equal 
 * to i (sectionDataList.get(i).getSectionId() = i); this is not checked.
 * 
 * @author field
 *
 */
public class SectionCluster extends ArrayList<Integer> {

	public static boolean D = false;  // for debugging

	private List<? extends FaultSection> sectionDataList;
	private ArrayList<Integer> allSectionsIdList = null;
	
	// these are for tracking duplicate ruptures
	private int duplicateRups;
	private HashSet<UniqueRupture> processedRuptures;
	
	// elements here are section IDs (same as indices in sectonDataList)
	private List<List<Integer>> rupListIndices;
	private int numRupsAdded;
	private OldPlausibilityConfiguration plausibility;
	private Map<IDPairing, Double> sectionAzimuths;
	private Map<IDPairing, Double> subSectionDistances;
	
	private List<List<Integer>> sectionConnectionsListList;
	private SectionConnectionStrategy connStrategy;

	/**
	 * 
	 * @param sectionDataList - this assumes that index in this list is equal to the Id of the contained FaultSectionPrefData (i = sectionDataList.get(i).getSectionId())
	 * @param minNumSectInRup
	 * @param sectionConnectionsListList
	 * @param subSectionAzimuths
	 * @param maxAzimuthChange
	 * @param maxTotAzimuthChange
	 * @param maxRakeDiff
	 */
	public SectionCluster(OldPlausibilityConfiguration plausibility, List<? extends FaultSection> sectionDataList,
			SectionConnectionStrategy connStrategy, List<List<Integer>> sectionConnectionsListList, Map<IDPairing, Double> subSectionAzimuths,
			Map<IDPairing, Double> subSectionDistances) {
		this.sectionDataList = sectionDataList;
		this.plausibility = plausibility;
		this.connStrategy = connStrategy;
		this.sectionAzimuths = subSectionAzimuths;
		this.subSectionDistances = subSectionDistances;
		this.sectionConnectionsListList = sectionConnectionsListList;
	}


	/**
	 * This returns the number of sections in the cluster
	 * @return
	 */
	public int getNumSections() {
		return this.size();
	}


	/**
	 * This returns a list of the IDs of all  sections in the cluster
	 * @return
	 */
	public List<Integer> getAllSectionsIdList() {
		if(allSectionsIdList==null) computeAllSectionsIdList();
		return allSectionsIdList;
	}


	private void computeAllSectionsIdList() {
		allSectionsIdList = new ArrayList<Integer>();
		for(int i=0; i<size();i++) allSectionsIdList.add(sectionDataList.get(get(i)).getSectionId());
	}


	public int getNumRuptures() {
		if(rupListIndices== null)  computeRupList();
		//		return rupListIndices.size();
		return numRupsAdded;
	}


	public List<List<Integer>> getSectionIndicesForRuptures() {
		if(rupListIndices== null)
			computeRupList();
		return rupListIndices;
	}


	public List<Integer> getSectionIndicesForRupture(int rthRup) {
		if(rupListIndices== null)
			computeRupList();
		return rupListIndices.get(rthRup);
	}



//	int rupCounterProgress = 1000000;
//	int rupCounterProgressIncrement = 1000000;
	int rupCounterProgress = 100000;
	int rupCounterProgressIncrement = 100000;
	int maxRupsPerCluster = 1000000;
	
	private void addRuptures(FaultSection sect, List<AbstractPlausibilityFilter> laughTests) {
		List<? extends FaultSection> rupture = Lists.newArrayList(sect);
		List<Integer> junctionIndexes = Lists.newArrayList();
		List<IDPairing> pairings = Lists.newArrayList();
		
		HashSet<Integer> idsSet = new HashSet<Integer>();
		idsSet.add(sect.getSectionId());
		List<Integer> idsList = Lists.newArrayList(idsSet);
		
		addRuptures(rupture, pairings, junctionIndexes, laughTests, new UniqueRupture(idsList), idsSet);
	}
	
	private FailureHandler failHandle;
	
	public static interface FailureHandler {
		public void ruptureFailed(List<FaultSection> rupture, boolean continuable);
	}
	
	public void setFailureHandler(FailureHandler failHandle) {
		this.failHandle = failHandle;
	}
	
	private class UniqueRupture {
		private final List<Integer> sectIndexes;
		private final List<Integer> sortedIndexes;
		private final int lastIndex;
		
		public UniqueRupture(List<Integer> sectIndexes) {
			this.sectIndexes = sectIndexes;
			sortedIndexes = new ArrayList<>(sectIndexes);
			Collections.sort(sortedIndexes);
			
			this.lastIndex = sectIndexes.get(sectIndexes.size()-1);
		}
		
		public UniqueRupture takeBranch(int sectIndex) {
			List<Integer> newIndexes = new ArrayList<>(sectIndexes);
			newIndexes.add(sectIndex);
			UniqueRupture newRup = new UniqueRupture(newIndexes);
			return newRup;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getEnclosingInstance().hashCode();
			result = prime * result + ((sortedIndexes == null) ? 0 : sortedIndexes.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			UniqueRupture other = (UniqueRupture) obj;
			if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
				return false;
			if (sortedIndexes == null) {
				if (other.sortedIndexes != null)
					return false;
			} else if (!sortedIndexes.equals(other.sortedIndexes))
				return false;
			return true;
		}

		private SectionCluster getEnclosingInstance() {
			return SectionCluster.this;
		}
	}
	
	/**
	 * This creates a rupture list for the given cluster by applying the laugh tests.
	 * 
	 * @param rupture
	 * @param pairings
	 * @param junctionIndexes
	 * @param tests
	 * @param idsList
	 * @param idsSet
	 */
	private void addRuptures(List<? extends FaultSection> rupture, List<IDPairing> pairings,
			List<Integer> junctionIndexes, List<AbstractPlausibilityFilter> tests,
			UniqueRupture uniqueRupture, HashSet<Integer> idsSet) {
		
		// this is for enabling debugging to figure out why a certain rupture is included
//		boolean debugMatch = idsList.size() > 1 && idsList.get(0) == 1 && idsList.get(1) == 1;
//		boolean debugMatch = idsList.get(0) == 1;
		final boolean debugMatch = false;
//		final boolean debugMatch = idsList.get(0) == 24;
//		boolean debugMatch = idsList.contains(613) && idsList.contains(1495);
		
		candidateLoop:
		for (List<FaultSection> possibleRupture : connStrategy.getNextPossibleRuptures(
				rupture, idsSet, sectionConnectionsListList.get(rupture.get(rupture.size()-1).getSectionId()),
				sectionDataList)) {
			Preconditions.checkState(possibleRupture.size() > rupture.size());
			UniqueRupture candidateUnique = uniqueRupture;
			List<FaultSection> candidateRupture = new ArrayList<>(rupture);
			List<Integer> candidateJunctionIndexes = new ArrayList<>(junctionIndexes);
			List<IDPairing> candidatePairings = Lists.newArrayList(pairings);
			
			// process each additional section that was just added one at a time
			PlausibilityResult candidateResult = null;
			for (int i=rupture.size(); i<possibleRupture.size(); i++) {
				FaultSection currentLastSect = candidateRupture.get(candidateRupture.size()-1);
				FaultSection candidateLastSect = possibleRupture.get(i);
				int candidateIndex = candidateLastSect.getSectionId();
				candidateUnique = candidateUnique.takeBranch(candidateIndex);
				
				candidateRupture.add(candidateLastSect);
				
				boolean junction = currentLastSect.getParentSectionId() !=
						candidateLastSect.getParentSectionId();
				if (junction)
					// this equals candidateRupture.size() - 1, but one less arithmetic operation
					candidateJunctionIndexes.add(rupture.size());
				
				candidatePairings.add(new IDPairing(currentLastSect.getSectionId(), candidateIndex));
				
				candidateResult = PlausibilityResult.PASS;
				
				for (AbstractPlausibilityFilter test : tests) {
					if (!junction && test.isApplyJunctionsOnly())
						continue;
					
					PlausibilityResult result = test.applyLastSection(
							candidateRupture, candidatePairings, candidateJunctionIndexes);
					candidateResult = candidateResult.logicalAnd(result);
					
					if (debugMatch && !result.isPass())
						System.out.println("Failed: "+
							ClassUtils.getClassNameWithoutPackage(test.getClass())
							+" ("+Joiner.on(",").join(uniqueRupture.sectIndexes)+","+candidateIndex+")");
					
					if (!candidateResult.canContinue())
						break;
				}
				
				// this is for debugging
				if (failHandle != null && !candidateResult.isPass())
					failHandle.ruptureFailed(candidateRupture, candidateResult.canContinue());
				
				if (!candidateResult.canContinue())
					// this means we failed a non-continuation test
					continue candidateLoop;
			}
			
			List<Integer> candidateIDList = candidateUnique.sectIndexes;
			HashSet<Integer> candidateIDSet = new HashSet<Integer>(candidateIDList);
			if (processedRuptures.contains(candidateUnique)) {
				// duplicate, don't add
				duplicateRups++;
			} else {
				// this is a new unique rupture
				if (candidateResult.isPass()) {
//					if (!(Double.isInfinite(laughTestFilter.getMaxCmlAzimuthChange()) && Math.random() > 0.001
//							&& candidateRupture.size() > 10))
					processedRuptures.add(candidateUnique);
					rupListIndices.add(candidateIDList);
					if (numRupsAdded > maxRupsPerCluster) {
						System.out.println("WARNING: Bailing on a cluster after "+maxRupsPerCluster+" ruptures!");
						return;
					}
					numRupsAdded += 1;
					// show progress
					if(numRupsAdded >= rupCounterProgress) {
						if (D) System.out.println(numRupsAdded+" ["+rupListIndices.size()+"]");
						rupCounterProgress += rupCounterProgressIncrement;
					}
				}
			}
			addRuptures(candidateRupture, candidatePairings, candidateJunctionIndexes,
					tests, candidateUnique, candidateIDSet);
		}
	}

	private void computeRupList() {
		//		if(D) System.out.println("Computing Rupture List in SectionCluster");
		//		System.out.println("Cluster: "+this);
		rupListIndices = new ArrayList<>();
		
		duplicateRups = 0;
		processedRuptures = new HashSet<>();
		
		int progress = 0;
		int progressIncrement = 5;
		numRupsAdded=0;
		//		System.out.print("% Done:\t");
		// loop over every section as the first in the rupture
		List<AbstractPlausibilityFilter> laughTests = plausibility.getPlausibilityFilters();
		for(int s=0;s<size();s++) {
			//		for(int s=0;s<1;s++) {	// Debugging: only compute ruptures from first section
			// show progress
			//if(s*100/size() > progress) {
			//	System.out.print(progress+"\t");
			//	progress += progressIncrement;
			//}
			ArrayList<Integer> sectList = new ArrayList<Integer>();
			int sectIndex = get(s);
			sectList.add(sectIndex);
			addRuptures(sectionDataList.get(sectIndex), laughTests);
			if (D) System.out.println(rupListIndices.size()+" ruptures after section "
					+s+" (skipped "+duplicateRups+" duplicates)");
		}
		if (D) System.out.println("\nAdded "+numRupsAdded+" rups!");
	}


	public void writeRuptureSectionNames(int index) {
		List<Integer> rupture = rupListIndices.get(index);
		System.out.println("Rutpure "+index);
		for(int i=0; i<rupture.size(); i++ ) {
			System.out.println("\t"+this.sectionDataList.get(rupture.get(i)).getName());
		}

	}

	/**
	 * This returns the change in strike direction in going from this azimuth1 to azimuth2,
	 * where these azimuths are assumed to be defined between -180 and 180 degrees.
	 * The output is between -180 and 180 degrees.
	 * @return
	 */
	public static double getAzimuthDifference(double azimuth1, double azimuth2) {
		double diff = azimuth2 - azimuth1;
		if(diff>180)
			return diff-360;
		else if (diff<-180)
			return diff+360;
		else
			return diff;
	}

}