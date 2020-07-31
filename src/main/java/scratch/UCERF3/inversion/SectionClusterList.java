package scratch.UCERF3.inversion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.laughTest.UCERF3PlausibilityConfig;
import scratch.UCERF3.inversion.laughTest.PlausibilityConfiguration;
import scratch.UCERF3.utils.DeformationModelFetcher;

public class SectionClusterList extends ArrayList<SectionCluster> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static final boolean D = false;
	
	private List<List<Integer>> sectionConnectionsListList;
	private PlausibilityConfiguration plausibility;
	private List<? extends FaultSection> faultSectionData;
	
	private Map<IDPairing, Double> subSectionDistances;
	
	public SectionClusterList(FaultModels faultModel, DeformationModels defModel, File precomputedDataDir,
			SectionConnectionStrategy connectionStrategy, PlausibilityConfiguration plausibility) {
		this(new DeformationModelFetcher(faultModel, defModel, precomputedDataDir,
				InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE), connectionStrategy, plausibility);
	}
	
	public SectionClusterList(DeformationModelFetcher defModelFetcher, SectionConnectionStrategy connectionStrategy,
			PlausibilityConfiguration plausibility) {
		faultSectionData = defModelFetcher.getSubSectionList();
		Map<IDPairing, Double> subSectionDistances = defModelFetcher.getSubSectionDistanceMap(plausibility.getMaxJumpDist());
		Map<IDPairing, Double> subSectionAzimuths = defModelFetcher.getSubSectionAzimuthMap(subSectionDistances.keySet());
		init(connectionStrategy, plausibility, faultSectionData, subSectionDistances, subSectionAzimuths);
	}
	
	public SectionClusterList(SectionConnectionStrategy connectionStrategy, PlausibilityConfiguration plausibility,
			List<? extends FaultSection> faultSectionData, Map<IDPairing, Double> subSectionDistances,
			Map<IDPairing, Double> subSectionAzimuths) {
		init(connectionStrategy, plausibility, faultSectionData,
				subSectionDistances, subSectionAzimuths);
	}

	private void init(SectionConnectionStrategy connectionStrategy,
			PlausibilityConfiguration plausibility,
			List<? extends FaultSection> faultSectionData,
			Map<IDPairing, Double> subSectionDistances,
			Map<IDPairing, Double> subSectionAzimuths) {
		this.plausibility = plausibility;
		this.subSectionDistances = subSectionDistances;
		
		this.faultSectionData = faultSectionData;
		
		// check that indices are same as sectionIDs (this is assumed here)
		for(int i=0; i<faultSectionData.size();i++)
			Preconditions.checkState(faultSectionData.get(i).getSectionId() == i,
				"RupsInFaultSystemInversion: Error - indices of faultSectionData don't match IDs");

		// make the list of SectionCluster objects 
		// (each represents a set of nearby sections and computes the possible
		//  "ruptures", each defined as a list of sections in that rupture)
		makeClusterList(connectionStrategy, subSectionAzimuths,subSectionDistances);
		
		plausibility.buildPlausibilityFilters(subSectionAzimuths, subSectionDistances,
				sectionConnectionsListList, faultSectionData);
	}
	
	private void makeClusterList(
			SectionConnectionStrategy connectionStrategy,
			Map<IDPairing, Double> subSectionAzimuths,
			Map<IDPairing, Double> subSectionDistances) {
		
		// make the list of nearby sections for each section (branches)
		if(D) System.out.println("Making sectionConnectionsListList");
		sectionConnectionsListList = connectionStrategy.computeCloseSubSectionsListList(faultSectionData, subSectionDistances);
		if(D) System.out.println("Done making sectionConnectionsListList");
		
		HashSet<Integer> subSectsToIgnore = null;
		if (plausibility.getParentSectsToIgnore() != null) {
			HashSet<Integer> parentSectsToIgnore = plausibility.getParentSectsToIgnore();
			subSectsToIgnore = new HashSet<Integer>();
			for (FaultSection sect : faultSectionData)
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
			SectionCluster newCluster = new SectionCluster(plausibility, faultSectionData,
					connectionStrategy, sectionConnectionsListList,
					subSectionAzimuths, subSectionDistances);
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

	public static boolean isD() {
		return D;
	}

	public List<List<Integer>> getSectionConnectionsListList() {
		return sectionConnectionsListList;
	}

	public PlausibilityConfiguration getPlausibilityConfiguration() {
		return plausibility;
	}

	public List<? extends FaultSection> getFaultSectionData() {
		return faultSectionData;
	}

	public Map<IDPairing, Double> getSubSectionDistances() {
		return subSectionDistances;
	}

}
