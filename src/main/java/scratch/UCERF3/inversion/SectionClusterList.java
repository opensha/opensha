package scratch.UCERF3.inversion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.laughTest.LaughTestFilter;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.IDPairing;

public class SectionClusterList extends ArrayList<SectionCluster> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static final boolean D = false;
	
	private List<List<Integer>> sectionConnectionsListList;
	private LaughTestFilter filter;
	private DeformationModels defModel;
	private FaultModels faultModel;
	private List<FaultSectionPrefData> faultSectionData;
	private CoulombRates coulombRates;
	
	private Map<IDPairing, Double> subSectionDistances;
	
	public SectionClusterList(FaultModels faultModel, DeformationModels defModel, File precomputedDataDir,
			LaughTestFilter filter) {
		this(new DeformationModelFetcher(faultModel, defModel, precomputedDataDir, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE), filter);
	}
	
	public SectionClusterList(DeformationModelFetcher defModelFetcher, LaughTestFilter filter) {
		faultSectionData = defModelFetcher.getSubSectionList();
		Map<IDPairing, Double> subSectionDistances = defModelFetcher.getSubSectionDistanceMap(filter.getMaxJumpDist());
		Map<IDPairing, Double> subSectionAzimuths = defModelFetcher.getSubSectionAzimuthMap(subSectionDistances.keySet());
		init(defModelFetcher.getFaultModel(), defModelFetcher.getDeformationModel(),
				filter, faultSectionData, subSectionDistances, subSectionAzimuths);
	}
	
	public SectionClusterList(FaultModels faultModel, DeformationModels defModel, LaughTestFilter filter,
			List<FaultSectionPrefData> faultSectionData, Map<IDPairing, Double> subSectionDistances, Map<IDPairing, Double> subSectionAzimuths) {
		init(faultModel, defModel, filter, faultSectionData,
				subSectionDistances, subSectionAzimuths);
	}
	
	public SectionClusterList(FaultModels faultModel, DeformationModels defModel, LaughTestFilter filter, CoulombRates coulombRates,
			List<FaultSectionPrefData> faultSectionData, Map<IDPairing, Double> subSectionDistances, Map<IDPairing, Double> subSectionAzimuths) {
		init(faultModel, defModel, filter, coulombRates, faultSectionData,
				subSectionDistances, subSectionAzimuths);
	}

	private void init(FaultModels faultModel, DeformationModels defModel,
			LaughTestFilter filter,
			List<FaultSectionPrefData> faultSectionData,
			Map<IDPairing, Double> subSectionDistances,
			Map<IDPairing, Double> subSectionAzimuths) {
		
		CoulombRates coulombRates = null;
		if (filter.getCoulombFilter() != null) {
			try {
				coulombRates = CoulombRates.loadUCERF3CoulombRates(faultModel);
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		
		init(faultModel, defModel, filter, coulombRates, faultSectionData, subSectionDistances, subSectionAzimuths);
	}

	private void init(FaultModels faultModel, DeformationModels defModel,
			LaughTestFilter filter,
			CoulombRates coulombRates,
			List<FaultSectionPrefData> faultSectionData,
			Map<IDPairing, Double> subSectionDistances,
			Map<IDPairing, Double> subSectionAzimuths) {
		this.faultModel = faultModel;
		this.defModel = defModel;
		this.filter = filter;
		this.coulombRates = coulombRates;
		this.subSectionDistances = subSectionDistances;
		
		this.faultSectionData = faultSectionData;
		Map<Integer, Double> rakesMap = new HashMap<Integer, Double>();
		for (FaultSectionPrefData data : faultSectionData)
			rakesMap.put(data.getSectionId(), data.getAveRake());
		
		// check that indices are same as sectionIDs (this is assumed here)
		for(int i=0; i<faultSectionData.size();i++)
			Preconditions.checkState(faultSectionData.get(i).getSectionId() == i,
				"RupsInFaultSystemInversion: Error - indices of faultSectionData don't match IDs");

		// make the list of SectionCluster objects 
		// (each represents a set of nearby sections and computes the possible
		//  "ruptures", each defined as a list of sections in that rupture)
		makeClusterList(subSectionAzimuths,subSectionDistances, rakesMap);
		
		filter.buildLaughTests(
				subSectionAzimuths, subSectionDistances, rakesMap, coulombRates,
				true, sectionConnectionsListList, faultSectionData);
	}
	
	private void makeClusterList(
			Map<IDPairing, Double> subSectionAzimuths,
			Map<IDPairing, Double> subSectionDistances,
			Map<Integer, Double> rakesMap) {
		
		// make the list of nearby sections for each section (branches)
		if(D) System.out.println("Making sectionConnectionsListList");
		sectionConnectionsListList = computeCloseSubSectionsListList(subSectionDistances);
		if(D) System.out.println("Done making sectionConnectionsListList");
		
		HashSet<Integer> subSectsToIgnore = null;
		if (filter.getParentSectsToIgnore() != null) {
			HashSet<Integer> parentSectsToIgnore = filter.getParentSectsToIgnore();
			subSectsToIgnore = new HashSet<Integer>();
			for (FaultSectionPrefData sect : faultSectionData)
				if (parentSectsToIgnore.contains(sect.getParentSectionId()))
					subSectsToIgnore.add(sect.getSectionId());
			for (int i=0; i<sectionConnectionsListList.size(); i++) {
				List<Integer> list = sectionConnectionsListList.get(i);
				if (subSectsToIgnore.contains(i))
					list.clear();
				for (int j=list.size(); --j>=0;)
					if (subSectsToIgnore.contains(list.get(j)))
						list.remove(j);
			}
		}

		// make an arrayList of section indexes
		ArrayList<Integer> availableSections = new ArrayList<Integer>();
		for(int i=0; i<faultSectionData.size(); i++)
			if (subSectsToIgnore == null || !subSectsToIgnore.contains(i))
				availableSections.add(i);

		while(availableSections.size()>0) {
			if (D) System.out.println("WORKING ON CLUSTER #"+(size()+1));
			int firstSubSection = availableSections.get(0);
			SectionCluster newCluster = new SectionCluster(filter, faultSectionData,sectionConnectionsListList,
					subSectionAzimuths, rakesMap, subSectionDistances, coulombRates);
			newCluster.add(firstSubSection);
			if (D) System.out.println("\tfirst is "+faultSectionData.get(firstSubSection).getName());
			addClusterLinks(firstSubSection, newCluster, sectionConnectionsListList);
			// remove the used subsections from the available list
			for(int i=0; i<newCluster.size();i++) availableSections.remove(newCluster.get(i));
			// add this cluster to the list
			add(newCluster);
			if (D) System.out.println(newCluster.size()+"\tsubsections in cluster #"+size()+"\t"+
					availableSections.size()+"\t subsections left to allocate");
		}
	}
	
	static void addClusterLinks(int subSectIndex, SectionCluster list, List<List<Integer>> sectionConnectionsListList) {
		List<Integer> branches = sectionConnectionsListList.get(subSectIndex);
		for(int i=0; i<branches.size(); i++) {
			Integer subSect = branches.get(i);
			if(!list.contains(subSect)) {
				list.add(subSect);
				addClusterLinks(subSect, list, sectionConnectionsListList);
			}
		}
	}
	
	/**
	 * For each section, create a list of sections that are within maxJumpDist.  
	 * This generates an ArrayList of ArrayLists (named sectionConnectionsList).  
	 * Reciprocal duplicates are not filtered out.
	 * If sections are actually subsections (meaning getParentSectionId() != -1), then each parent section can only
	 * have one connection to another parent section (whichever subsections are closest).  This prevents parallel 
	 * and closely space faults from having connections back and forth all the way down the section.
	 */
	private List<List<Integer>> computeCloseSubSectionsListList(Map<IDPairing, Double> subSectionDistances) {
		return computeCloseSubSectionsListList(faultSectionData, subSectionDistances, filter.getMaxJumpDist(), coulombRates);
	}
	
	/**
	 * For each section, create a list of sections that are within maxJumpDist.  
	 * This generates an ArrayList of ArrayLists (named sectionConnectionsList).  
	 * Reciprocal duplicates are not filtered out.
	 * If sections are actually subsections (meaning getParentSectionId() != -1), then each parent section can only
	 * have one connection to another parent section (whichever subsections are closest).  This prevents parallel 
	 * and closely space faults from having connections back and forth all the way down the section.
	 */
	public static List<List<Integer>> computeCloseSubSectionsListList(
			List<FaultSectionPrefData> faultSectionData,
			Map<IDPairing, Double> subSectionDistances,
			double maxJumpDist) {
		return computeCloseSubSectionsListList(faultSectionData, subSectionDistances, maxJumpDist, null);
	}
	
	/**
	 * For each section, create a list of sections that are within maxJumpDist.  
	 * This generates an ArrayList of ArrayLists (named sectionConnectionsList).  
	 * Reciprocal duplicates are not filtered out.
	 * If sections are actually subsections (meaning getParentSectionId() != -1), then each parent section can only
	 * have one connection to another parent section (whichever subsections are closest).  This prevents parallel 
	 * and closely space faults from having connections back and forth all the way down the section.
	 */
	public static List<List<Integer>> computeCloseSubSectionsListList(
			List<FaultSectionPrefData> faultSectionData,
			Map<IDPairing, Double> subSectionDistances,
			double maxJumpDist, CoulombRates coulombRates) {

		ArrayList<List<Integer>> sectionConnectionsListList = new ArrayList<List<Integer>>();
		for(int i=0;i<faultSectionData.size();i++)
			sectionConnectionsListList.add(new ArrayList<Integer>());

		// in case the sections here are subsections of larger sections, create a subSectionDataListList where each
		// ArrayList<FaultSectionPrefData> is a list of subsections from the parent section
		ArrayList<ArrayList<FaultSectionPrefData>> subSectionDataListList = new ArrayList<ArrayList<FaultSectionPrefData>>();
		int lastID=-1;
		ArrayList<FaultSectionPrefData> newList = new ArrayList<FaultSectionPrefData>();
		for(int i=0; i<faultSectionData.size();i++) {
			FaultSectionPrefData subSect = faultSectionData.get(i);
			int parentID = subSect.getParentSectionId();
			if(parentID != lastID || parentID == -1) { // -1 means there is no parent
				newList = new ArrayList<FaultSectionPrefData>();
				subSectionDataListList.add(newList);
				lastID = subSect.getParentSectionId();
			}
			newList.add(subSect);
		}


		// First, if larger sections have been sub-sectioned, fill in neighboring subsection connections
		// (using the other algorithm below might lead to subsections being skipped if their width is < maxJumpDist) 
		for(int i=0; i<subSectionDataListList.size(); ++i) {
			ArrayList<FaultSectionPrefData> subSectList = subSectionDataListList.get(i);
			int numSubSect = subSectList.size();
			for(int j=0;j<numSubSect;j++) {
				// get index of section
				int sectIndex = subSectList.get(j).getSectionId();
				List<Integer> sectionConnections = sectionConnectionsListList.get(sectIndex);
				if(j != 0) // skip the first one since it has no previous subsection
					sectionConnections.add(subSectList.get(j-1).getSectionId());
				if(j != numSubSect-1) // the last one has no subsequent subsection
					sectionConnections.add(subSectList.get(j+1).getSectionId());
			}
		}

		// now add subsections on other sections, keeping only one connection between each section (the closest)
		for(int i=0; i<subSectionDataListList.size(); ++i) {
			ArrayList<FaultSectionPrefData> sect1_List = subSectionDataListList.get(i);
			for(int j=i+1; j<subSectionDataListList.size(); ++j) {
				ArrayList<FaultSectionPrefData> sect2_List = subSectionDataListList.get(j);
				double minDist=Double.MAX_VALUE;
				int subSectIndex1 = -1;
				int subSectIndex2 = -1;
				// find the closest pair
				for(int k=0;k<sect1_List.size();k++) {
					for(int l=0;l<sect2_List.size();l++) {
						FaultSectionPrefData data1 = sect1_List.get(k);
						FaultSectionPrefData data2 = sect2_List.get(l);
						IDPairing ind = new IDPairing(data1.getSectionId(), data2.getSectionId());
						if (subSectionDistances.containsKey(ind)) {
							double dist = subSectionDistances.get(ind);
							if(dist < minDist || (float)dist == (float)minDist) {
								if ((float)dist == (float)minDist) {
									// this 2nd check in floating point precision gets around an issue where the distance is identical
									// within floating point precision for 2 sub sections on the same section. if we don't do this check
									// than the actual "closer" section could vary depending on the machine/os used. in this case, just
									// use the one that's in coulomb. If neither are in coulomb, then keep the first occurrence (lower ID)
									
									boolean prevValCoulomb = false;
									boolean curValCoulomb = false;
									if (coulombRates != null) {
										prevValCoulomb = coulombRates.containsKey(new IDPairing(subSectIndex1, subSectIndex2));
										curValCoulomb = coulombRates.containsKey(new IDPairing(data1.getSectionId(), data2.getSectionId()));
									}
//									System.out.println("IT HAPPENED!!!!!! "+subSectIndex1+"=>"+subSectIndex2+" vs "
//													+data1.getSectionId()+"=>"+data2.getSectionId());
//									System.out.println("prevValCoulomb="+prevValCoulomb+"\tcurValCoulomb="+curValCoulomb);
									if (prevValCoulomb)
										// this means that the previous value is in coulomb, use that!
										continue;
									if (!curValCoulomb) {
										// this means that either no coulomb values were supplied, or neither choice is in coulomb. lets use
										// the first one for consistency
										continue;
									}
//									System.out.println("Sticking with the latter, "+data2.getSectionId()+" (coulomb="+curValCoulomb+")");
								}
								minDist = dist;
								subSectIndex1 = data1.getSectionId();
								subSectIndex2 = data2.getSectionId();
							}	
						}		  
					}
				}
				// add to lists for each subsection
				if (minDist<maxJumpDist) {
					sectionConnectionsListList.get(subSectIndex1).add(subSectIndex2);
					sectionConnectionsListList.get(subSectIndex2).add(subSectIndex1);  // reciprocal of the above
				}
			}
		}
		return sectionConnectionsListList;
	}

	public static boolean isD() {
		return D;
	}

	public List<List<Integer>> getSectionConnectionsListList() {
		return sectionConnectionsListList;
	}

	public LaughTestFilter getFilter() {
		return filter;
	}

	public DeformationModels getDefModel() {
		return defModel;
	}

	public FaultModels getFaultModel() {
		return faultModel;
	}

	public List<FaultSectionPrefData> getFaultSectionData() {
		return faultSectionData;
	}

	public Map<IDPairing, Double> getSubSectionDistances() {
		return subSectionDistances;
	}

}
