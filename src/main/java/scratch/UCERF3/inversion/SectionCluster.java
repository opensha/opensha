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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.inversion.laughTest.BuggyCoulombFilter;
import scratch.UCERF3.inversion.laughTest.CoulombFilter;
import scratch.UCERF3.inversion.laughTest.AbstractLaughTest;
import scratch.UCERF3.inversion.laughTest.LaughTestFilter;

/**
 * 
 * This assumes that the ith FaultSectionPrefData object in the sectionDataList list has an Id equal 
 * to i (sectionDataList.get(i).getSectionId() = i); this is not checked.
 * 
 * @author field
 *
 */
public class SectionCluster extends ArrayList<Integer> {

	protected final static boolean D = false;  // for debugging

	List<FaultSectionPrefData> sectionDataList;
	ArrayList<Integer> allSectionsIdList = null;
	List<List<Integer>> sectionConnectionsListList;
	ArrayList<ArrayList<Integer>> rupListIndices;			// elements here are section IDs (same as indices in sectonDataList)
	int numRupsAdded;
	LaughTestFilter laughTestFilter;
	Map<IDPairing, Double> sectionAzimuths;
	Map<Integer, Double> rakesMap;
	Map<IDPairing, Double> subSectionDistances;
	CoulombRates coulombRates;
	
	private static final boolean OLD_ADD_RUPS = false;
	
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
	@Deprecated
	public SectionCluster(List<FaultSectionPrefData> sectionDataList, int minNumSectInRup, 
			List<List<Integer>> sectionConnectionsListList, Map<IDPairing, Double> subSectionAzimuths,
			Map<Integer, Double> rakesMap, double maxAzimuthChange, double maxTotAzimuthChange, 
			double maxRakeDiff, Map<IDPairing, Double> subSectionDistances, double maxCumJumpDist) {
		this(new LaughTestFilter(5d, maxAzimuthChange, maxTotAzimuthChange,
				maxCumJumpDist, 360, 540, minNumSectInRup, false, null),
				sectionDataList, sectionConnectionsListList, subSectionAzimuths, rakesMap, subSectionDistances, null);
	}


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
	public SectionCluster(LaughTestFilter laughTestFilter, List<FaultSectionPrefData> sectionDataList,
			List<List<Integer>> sectionConnectionsListList, Map<IDPairing, Double> subSectionAzimuths,
			Map<Integer, Double> rakesMap, Map<IDPairing, Double> subSectionDistances, CoulombRates coulombRates) {
		this.sectionDataList = sectionDataList;
		this.laughTestFilter = laughTestFilter;
		this.sectionConnectionsListList = sectionConnectionsListList;
		this.sectionAzimuths = subSectionAzimuths;
		this.rakesMap = rakesMap;
		this.subSectionDistances = subSectionDistances;
		this.coulombRates = coulombRates;
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
	public ArrayList<Integer> getAllSectionsIdList() {
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


	public ArrayList<ArrayList<Integer>> getSectionIndicesForRuptures() {
		if(rupListIndices== null)
			computeRupList();
		return rupListIndices;
	}


	public ArrayList<Integer> getSectionIndicesForRupture(int rthRup) {
		if(rupListIndices== null)
			computeRupList();
		return rupListIndices.get(rthRup);
	}
	
	private double getRake(int index) {
		if (rakesMap == null)
			return sectionDataList.get(index).getAveRake();
		return rakesMap.get(index);
	}



//	int rupCounterProgress =1000000;
//	int rupCounterProgressIncrement = 1000000;
	int rupCounterProgress =100000;
	int rupCounterProgressIncrement = 100000;
	
	private void addRuptures(FaultSectionPrefData sect, List<AbstractLaughTest> laughTests) {
		List<FaultSectionPrefData> rupture = Lists.newArrayList(sect);
		List<Integer> junctionIndexes = Lists.newArrayList();
		List<IDPairing> pairings = Lists.newArrayList();
		
		HashSet<Integer> idsSet = new HashSet<Integer>();
		idsSet.add(sect.getSectionId());
		List<Integer> idsList = Lists.newArrayList(idsSet);
		
		addRuptures(rupture, pairings, junctionIndexes, laughTests, idsList, idsSet);
	}
	
	private FailureHandler failHandle;
	
	public static interface FailureHandler {
		public void ruptureFailed(List<FaultSectionPrefData> rupture, boolean continuable);
	}
	
	public void setFailureHandler(FailureHandler failHandle) {
		this.failHandle = failHandle;
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
	private void addRuptures(List<FaultSectionPrefData> rupture, List<IDPairing> pairings,
			List<Integer> junctionIndexes, List<AbstractLaughTest> tests,
			List<Integer> idsList, HashSet<Integer> idsSet) {
		FaultSectionPrefData currentLastSect = rupture.get(rupture.size()-1);
		
		List<Integer> branches = sectionConnectionsListList.get(currentLastSect.getSectionId());
		
		// this is for enabling debugging to figure out why a certain rupture is included
//		boolean debugMatch = idsList.size() > 1 && idsList.get(0) == 1 && idsList.get(1) == 1;
//		boolean debugMatch = idsList.get(0) == 1;
		final boolean debugMatch = false;
//		boolean debugMatch = idsList.contains(613) && idsList.contains(1495);
		
		for(int candidateIndex : branches) {

			// avoid looping back on self or to previous section
			if(idsSet.contains(candidateIndex))
				continue;
			
//			boolean debugMatch = idsList.get(idsList.size()-1) == 2351 && candidateIndex == 208
//					|| idsList.get(idsList.size()-1) == 208 && candidateIndex == 2351;
			
			FaultSectionPrefData candidateLastSect = sectionDataList.get(candidateIndex);
			
			List<FaultSectionPrefData> candidateRupture = Lists.newArrayList(rupture);
			candidateRupture.add(candidateLastSect);
			
			List<Integer> candidateJunctionIndexes = Lists.newArrayList(junctionIndexes);
			
			boolean junction = currentLastSect.getParentSectionId() !=
					candidateLastSect.getParentSectionId();
			if (junction)
				// this equals candidateRupture.size() - 1, but one less arithmetic operation
				candidateJunctionIndexes.add(rupture.size());
			
			List<IDPairing> candidatePairings = Lists.newArrayList(pairings);
			candidatePairings.add(new IDPairing(currentLastSect.getSectionId(), candidateIndex));
			
			boolean pass = true;
			boolean cont = true;
			
			for (AbstractLaughTest test : tests) {
				if (!junction && test.isApplyJunctionsOnly())
					continue;
				
				boolean testPass = test.doesLastSectionPass(
						candidateRupture, candidatePairings, candidateJunctionIndexes);
				if (!testPass) {
					pass = false;
					if (debugMatch)
						System.out.println("Failed: "+
								ClassUtils.getClassNameWithoutPackage(test.getClass())
								+" ("+Joiner.on(",").join(idsList)+","+candidateIndex+")");
					if (!test.isContinueOnFaulure()) {
						// be careful to only break if this isn't a fail when we can continue
						cont = false;
						break;
					}
				}
			}
			
			// this is for debugging
			if (failHandle != null && !pass)
				failHandle.ruptureFailed(candidateRupture, cont);
			
			if (!cont)
				// this means we failed a non-continuation test
				continue;
			
			ArrayList<Integer> candidateIDList = Lists.newArrayList(idsList);
			candidateIDList.add(candidateIndex);
			HashSet<Integer> candidateIDSet = new HashSet<Integer>(idsSet);
			candidateIDSet.add(candidateIndex);
			
			if (pass) {
//				if (!(Double.isInfinite(laughTestFilter.getMaxCmlAzimuthChange()) && Math.random() > 0.001
//						&& candidateRupture.size() > 10))
				rupListIndices.add(candidateIDList);
				if (numRupsAdded > 1000000) {
					System.out.println("WARNING: Bailing on a cluster after 1 million ruptures!");
					return;
				}
				numRupsAdded += 1;
				// show progress
				if(numRupsAdded >= rupCounterProgress) {
					if (D) System.out.println(numRupsAdded+" ["+rupListIndices.size()+"]");
					rupCounterProgress += rupCounterProgressIncrement;
				}
			}
			addRuptures(candidateRupture, candidatePairings, candidateJunctionIndexes,
					tests, candidateIDList, candidateIDSet);
		}
	}
	
	/**
	 * This iteratively adds sections to the list (if the new section passes azimuth 
	 * and other checks), and saves each rupture to the rupture list.
	 * 
	 * This algorithm includes ruptures with one dangling sub-section (from a new section)
	 * that projects off at a non-allowed azimuth (because we can't currently distinguish
	 * these from from those projecting in an allowed direction).  One way to remove these
	 * would be to remove all ruptures that end with a single sub-section on a new section
	 * (even if it's headed in an allowed direction).
	 * @param list
	 */
	private void addRupturesOld(ArrayList<Integer> list) {
		addRupturesOld(list, 0d, 0d, 0d, null, null, false);
	}
	
	/**
	 * Return true if there are any connections from this index that are not on
	 * the same parent fault and not already in the rupture
	 * 
	 * @param index
	 */
	private boolean isBranchPoint(int index, List<Integer> rupture) {
		List<Integer> branches = sectionConnectionsListList.get(index);
		int sectParent = sectionDataList.get(index).getParentSectionId();
		for (int branch : branches) {
			if (rupture.contains(branch))
				continue;
			int branchParent = sectionDataList.get(branch).getParentSectionId();
			if (branchParent != sectParent) {
//				System.out.println("It's a junction! Possibility: "+index+"=>"+branch);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * This iteratively adds sections to the list (if the new section passes azimuth 
	 * and other checks), and saves each rupture to the rupture list.
	 * 
	 * This algorithm includes ruptures with one dangling sub-section (from a new section)
	 * that projects off at a non-allowed azimuth (because we can't currently distinguish
	 * these from from those projecting in an allowed direction).  One way to remove these
	 * would be to remove all ruptures that end with a single sub-section on a new section
	 * (even if it's headed in an allowed direction).
	 * @param list
	 */
	private void addRupturesOld(ArrayList<Integer> list, double cmlRakeChange, double cmlAzimuthChange, double cmlJumpDist,
			ArrayList<CoulombRatesRecord> forwardRates, ArrayList<CoulombRatesRecord> backwardRates, boolean multiFault) {
		int lastIndex = list.get(list.size()-1);
		int secToLastIndex = -1;	// bogus index in case the next if fails
		if(list.size()>1)
			secToLastIndex = list.get(list.size()-2);
		
		boolean applyGarlockPintoMtnFix = true;
		
		CoulombRatesTester coulombFilter = laughTestFilter.getCoulombFilter();
		
		if (forwardRates == null && coulombFilter != null) {
			forwardRates = new ArrayList<CoulombRatesRecord>();
			backwardRates = new ArrayList<CoulombRatesRecord>();
			
			// populate the coulomb rates
			ArrayList<Integer> rup = new ArrayList<Integer>();
			for (int i=1; i<list.size(); i++) {
				rup.add(list.get(i-1));
				if (!coulombFilter.isApplyBranchesOnly() || isBranchPoint(i-1, rup)) {
					System.out.println("Adding up top!");
					System.exit(0);
					IDPairing pairing = new IDPairing(list.get(i-1), list.get(i));
					CoulombRatesRecord record = coulombRates.get(pairing);
					Preconditions.checkNotNull(record, "No mapping exists for pairing: "+pairing);
					
					forwardRates.add(record);
					
					pairing = pairing.getReversed();
					record = coulombRates.get(pairing);
					Preconditions.checkNotNull(record, "No mapping exists for pairing: "+pairing);
					backwardRates.add(0, record);
				}
			}
		}

		// loop over branches at the present section
		List<Integer> branches = sectionConnectionsListList.get(lastIndex);
		for(int newIndex : branches) {

			// avoid looping back on self or to previous section
			if(list.contains(newIndex))
				continue;
			
			// now make sure the first subsection doesn't differ from the second
			boolean newParID_NotSameAsLast = sectionDataList.get(lastIndex).getParentSectionId() != sectionDataList.get(newIndex).getParentSectionId();
			if(list.size() == 1 && newParID_NotSameAsLast) {
				continue;
			}
			boolean newIsMultiFault = multiFault || newParID_NotSameAsLast;
			
			// make sure at least two sub-sections of a section have been used
			boolean lastParID_NotSameAsSecToLast=false;
			if(list.size()>1) {
				lastParID_NotSameAsSecToLast = sectionDataList.get(lastIndex).getParentSectionId() != sectionDataList.get(secToLastIndex).getParentSectionId();
				// this checks for a single subsection in between two sections
				if(lastParID_NotSameAsSecToLast && newParID_NotSameAsLast) {
					continue;
				}
			}
			


			// check the azimuth change, first checking whether diff parent sections were crossed (need two sections before and after crossing)   
			if(list.size()>=3 && lastParID_NotSameAsSecToLast) {
				// make sure there are enough points to compute an azimuth change
				double newAzimuth = sectionAzimuths.get(new IDPairing(lastIndex, newIndex));
				if (applyGarlockPintoMtnFix && (sectionDataList.get(newIndex).getParentSectionId() == 49 || sectionDataList.get(newIndex).getParentSectionId() == 341 || sectionDataList.get(newIndex).getParentSectionId() == 48 || sectionDataList.get(newIndex).getParentSectionId() == 93))
					newAzimuth = sectionAzimuths.get(new IDPairing(newIndex, lastIndex));
				int thirdToLastIndex = list.get(list.size()-3);
				double prevAzimuth = sectionAzimuths.get(new IDPairing(thirdToLastIndex, secToLastIndex));
				if (applyGarlockPintoMtnFix && (sectionDataList.get(thirdToLastIndex).getParentSectionId() == 49 || sectionDataList.get(thirdToLastIndex).getParentSectionId() == 341 || sectionDataList.get(thirdToLastIndex).getParentSectionId() == 48 || sectionDataList.get(thirdToLastIndex).getParentSectionId() == 93))
					prevAzimuth = sectionAzimuths.get(new IDPairing(secToLastIndex, thirdToLastIndex));
				
				// check change
				double azimuthChange = Math.abs(getAzimuthDifference(prevAzimuth,newAzimuth));
				if(azimuthChange>laughTestFilter.getMaxAzimuthChange()) {
					continue;	// don't add rupture
				}

				// check total change
				double firstAzimuth = sectionAzimuths.get(new IDPairing(list.get(0), list.get(1)));
				double totAzimuthChange = Math.abs(getAzimuthDifference(firstAzimuth,newAzimuth));
				if(totAzimuthChange>laughTestFilter.getMaxTotAzimuthChange()) {
					continue;	// don't add rupture
				}

			}

			ArrayList<Integer> newList = (ArrayList<Integer>)list.clone();
			newList.add(newIndex);
			
			int newLastIndex = newList.size()-1;
			int newPrevIndex = newLastIndex-1;
			
			IDPairing newLastPairing = new IDPairing(newList.get(newPrevIndex), newList.get(newLastIndex));
			
			double newCMLJumpDist = cmlJumpDist;
			// check the cumulative jumping distance
			if(newList.size()>=2) {
				newCMLJumpDist += subSectionDistances.get(newLastPairing);
				if(newCMLJumpDist > laughTestFilter.getMaxCmlJumpDist())
					continue;
			}
			
			// Check the cumulative rake change (this adds together absolute vales of rake changes, so they don't cancel)
			// This is squirrelly-ness filter #1 of 2 
			double newCMLRakeChange = cmlRakeChange;
			if(newList.size()>=2 && !isNaNInfinite(laughTestFilter.getMaxCmlRakeChange())) {
				double rakeDiff;
				rakeDiff = Math.abs(getRake(newList.get(newPrevIndex)) - getRake(newList.get(newLastIndex)));
				if (rakeDiff > 180)
					rakeDiff = 360-rakeDiff; // Deal with branch cut (180deg = -180deg)
				newCMLRakeChange += Math.abs(rakeDiff);
				if(newCMLRakeChange > laughTestFilter.getMaxCmlRakeChange())
					continue;				
			}
			
			
			// Check the cumulative azimuth change (this adds together absolute vales of azimuth changes, so they don't cancel)
			// This is squirrelly-ness filter #2 of 2 
			double newCMLAzimuthChange = cmlAzimuthChange;
			if(newList.size()>2 && !isNaNInfinite(laughTestFilter.getMaxCmlAzimuthChange())) {
				double prevAzimuth = sectionAzimuths.get(new IDPairing(newList.get(newPrevIndex-1), newList.get(newPrevIndex)));
				double newAzimuth = sectionAzimuths.get(newLastPairing);
				newCMLAzimuthChange += Math.abs(newAzimuth - prevAzimuth);
				if(newCMLAzimuthChange > laughTestFilter.getMaxCmlAzimuthChange())
					continue;
			} 
			
			
			// Filter out rupture if the set of rakes over entire rupture has too large a spread
			// now retired
//			if (!isNaNInfinite(laughTestFilter.getMaxRakeDiff())) {
//				double[] rakes, anglediffs2;
//				rakes = new double[newList.size()];
//				for (int i=0; i<newList.size(); i++)
//					rakes[i] = getRake(newList.get(i));
//				Arrays.sort(rakes);
//				anglediffs2 = new double[newList.size()];
//				for (int i=0; i<newList.size()-1; i++) {
//					anglediffs2[i] = rakes[i+1]-rakes[i];
//				}
//				anglediffs2[anglediffs2.length-1] = rakes[0]+360-rakes[newList.size()-1];
//				double rakeDiff = 360-StatUtils.max(anglediffs2);
//				if (rakeDiff>laughTestFilter.getMaxRakeDiff()) {
//					continue;
//				}
//			}
			
//			boolean debugMatch = newList.contains(2154) && newList.contains(1942) && newList.contains(2168);
//			debugMatch = debugMatch || (newList.get(0) == 2155 && newList.get(newList.size()-1) == 1847);
			
			// if we've made it this far then we should check coulomb
			ArrayList<CoulombRatesRecord> myForwardRates = null;
			ArrayList<CoulombRatesRecord> myBackwardRates = null;
			if (coulombFilter != null) {
				if (!coulombFilter.isApplyBranchesOnly() || isBranchPoint(newIndex, newList)) {
					CoulombRatesRecord forward = coulombRates.get(newLastPairing);
					if (forward == null) {
						System.out.println(sectionDataList.get(newLastPairing.getID1()).getSectionName());
						System.out.println(sectionDataList.get(newLastPairing.getID2()).getSectionName());
					}
//					if (debugMatch && newIndex == 302) {
//						System.out.println("Adding at 302. newList: "+Joiner.on(";").join(newList));
//					}
//					System.out.println("Adding coulomb. newIndex="+newIndex+"; prev="+newList.get(newList.size()-2)+"; pairing="+newLastPairing);
					Preconditions.checkNotNull(forward, "No mapping exists for pairing: "+newLastPairing);
					IDPairing reversedPairing = newLastPairing.getReversed();
					CoulombRatesRecord backward = coulombRates.get(reversedPairing);
					Preconditions.checkNotNull(backward, "No mapping exists for pairing: "+reversedPairing);
					myForwardRates = (ArrayList<CoulombRatesRecord>)forwardRates.clone();
					myForwardRates.add(forward);
					myBackwardRates = (ArrayList<CoulombRatesRecord>)backwardRates.clone();
					myBackwardRates.add(0, backward);
				} else {
					myForwardRates = forwardRates;
					myBackwardRates = backwardRates;
				}
			}
			
//			debugMatch = debugMatch && newList.contains(2285);
			
			boolean sameParID = sectionDataList.get(lastIndex).getParentSectionId() == sectionDataList.get(newIndex).getParentSectionId();
			if(newList.size() >= laughTestFilter.getMinNumSectInRup() && sameParID)  {// it's a rupture
				// now test coulomb
				// TODO remove newList.size() <= 2 hack. this is in here to make sure that each section is
				// involved in at least one rupture
//				if (debugMatch)
//					System.out.println("We made it this far!!!!");
//				if (debugMatch) {
//					System.out.println("Rup: "+Joiner.on(", ").join(newList));
//					BuggyCoulombFilter.printDebugRates(newList, myForwardRates, myBackwardRates);
//					System.out.println("Coulomb test: "+coulombFilter.doesRupturePass(myForwardRates, myBackwardRates));
//					if (!newIsMultiFault)
//						System.out.println("Passes because single fault!");
//				}
				if (coulombFilter == null || !newIsMultiFault
						|| coulombFilter.doesRupturePass(myForwardRates, myBackwardRates)) {
					// uncomment these lines to only save a very small amount of ruptures
//					if (Math.random()<0.0005)
						rupListIndices.add(newList);
					if (numRupsAdded > 1000000) {
						System.out.println("WARNING: Bailing on a cluster after 1 million ruptures!");
						return;
					}
//					if (debugMatch)
//						System.out.println("and now we add because it passed coulomb!");
					numRupsAdded += 1;
					// show progress
					if(numRupsAdded >= rupCounterProgress) {
						if (D) System.out.println(numRupsAdded+" ["+rupListIndices.size()+"]");
						rupCounterProgress += rupCounterProgressIncrement;
					}
				} else {
//					if (debugMatch) {
//						System.out.println("Boo it failed.");
//						System.out.println("Rup: "+Joiner.on(", ").join(newList));
//						BuggyCoulombFilter.printDebugRates(newList, myForwardRates, myBackwardRates);
//					}
					continue;
				}
			}
			addRupturesOld(newList, newCMLRakeChange, newCMLAzimuthChange, newCMLJumpDist, myForwardRates, myBackwardRates, newIsMultiFault);
		}
	}
	
	private static boolean isNaNInfinite(double val) {
		if (Double.isNaN(val))
			return true;
		if (Double.isInfinite(val))
			return true;
		if (val == Double.MAX_VALUE)
			return true;
		return false;
	}

	private void computeRupList() {
		//		if(D) System.out.println("Computing Rupture List in SectionCluster");
		//		System.out.println("Cluster: "+this);
		rupListIndices = new ArrayList<ArrayList<Integer>>();
		int progress = 0;
		int progressIncrement = 5;
		numRupsAdded=0;
		//		System.out.print("% Done:\t");
		// loop over every section as the first in the rupture
		List<AbstractLaughTest> laughTests = laughTestFilter.getLaughTests();
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
			if (OLD_ADD_RUPS)
				addRupturesOld(sectList);
			else
				addRuptures(sectionDataList.get(sectIndex), laughTests);
			//			System.out.println(rupList.size()+" ruptures after section "+s);
		}
		if (D) System.out.println("\nAdded "+numRupsAdded+" rups so far!");

		if (D) System.out.print("\nFiltering out duplicates...");
		// now filter out duplicates (which would exist in reverse order)
		ArrayList<ArrayList<Integer>> newRupList = new ArrayList<ArrayList<Integer>>();
//		for(int r=0; r< rupListIndices.size();r++) {
//			ArrayList<Integer> rup = rupListIndices.get(r);
//			ArrayList<Integer> reverseRup = new ArrayList<Integer>();
//			for(int i=rup.size()-1;i>=0;i--) reverseRup.add(rup.get(i));
//			if(!newRupList.contains(reverseRup)) { // keep if we don't already have
//				newRupList.add(rup);
//			}
//		}
		// TODO we need to unit test this
		HashSet<ArrayList<Integer>> hashedSorted = new HashSet<ArrayList<Integer>>();
		ArrayList<Integer> sortedList;
		for (ArrayList<Integer> rup : rupListIndices) {
			sortedList = new ArrayList<Integer>();
			sortedList.addAll(rup);
			Collections.sort(sortedList);
			if (hashedSorted.contains(sortedList)) {
				// this means it's already in here, do nothing
				continue;
			}
			newRupList.add(rup);
			hashedSorted.add(sortedList);
		}
		hashedSorted = null;
		if (D) System.out.println("DONE.");
		rupListIndices = newRupList;
		numRupsAdded = rupListIndices.size();

		if (D) System.out.println(numRupsAdded + " potential ruptures");

		/*
		// Remove ruptures where subsection strikes have too big a spread
		// System.out.println("maxStrikeDiff = "+maxStrikeDiff); //maximum allowed difference in strikes between any two subsections in the same rupture, in degrees
		ArrayList<ArrayList<Integer>> toRemove = new ArrayList<ArrayList<Integer>>();
		for(int r=0; r< numRupsAdded;r++) {
			ArrayList<Integer> rup = rupListIndices.get(r);
			//System.out.println("rup = " + rup);
			ArrayList<Double> strikes = new ArrayList<Double>(rup.size());
			for (int i=0; i<rup.size(); i++) {
			//  System.out.println("Avg. strike = " + subSectionPrefDataList.get(rup.get(i)).getFaultTrace().getAveStrike());
				if (sectionDataList.get(rup.get(i)).getFaultTrace().getAveStrike()<0)
					System.out.println("Error:										Strike < 0 !!!");
				if (sectionDataList.get(rup.get(i)).getFaultTrace().getAveStrike()<180)
					strikes.add(sectionDataList.get(rup.get(i)).getFaultTrace().getAveStrike());	
				else
					strikes.add(sectionDataList.get(rup.get(i)).getFaultTrace().getAveStrike()-180);	

			}
		    Collections.sort(strikes);
			ArrayList<Double> anglediffs = new ArrayList<Double>(rup.size());
		    for (int i=0; i<rup.size()-1; i++) {
		    	anglediffs.add(strikes.get(i+1)-strikes.get(i));
		    }
		    anglediffs.add(strikes.get(0)+180-strikes.get(rup.size()-1));
		    double strikeDiff = 180-Collections.max(anglediffs);
		    //System.out.println("strikeDiff = " + strikeDiff);
		    if (strikeDiff>maxStrikeDiff) {
		    	toRemove.add(rup);
		    }
		}
		for(int i=0; i< toRemove.size();i++) {
			newRupList.remove(toRemove.get(i));
		}

		rupListIndices = newRupList;
		numRupsAdded = rupListIndices.size();

		System.out.println(numRupsAdded + " ruptures that pass subsection strikes test");
		 */

		// Remove ruptures where subsection rakes have too big a spread
		// System.out.println("maxRakeDiff = "+maxRakeDiff); //maximum allowed difference in strikes between any two subsections in the same rupture, in degrees

		rupListIndices = newRupList;
		numRupsAdded = rupListIndices.size();
	}


	public void writeRuptureSectionNames(int index) {
		ArrayList<Integer> rupture = rupListIndices.get(index);
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