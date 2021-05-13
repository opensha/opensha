package scratch.UCERF3.inversion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.inversion.coulomb.CoulombRates;

/**
 * For each section, create a list of sections that are within maxJumpDist.  
 * This generates an ArrayList of ArrayLists (named sectionConnectionsList).  
 * Reciprocal duplicates are not filtered out.
 * If sections are actually subsections (meaning getParentSectionId() != -1), then each parent section can only
 * have one connection to another parent section (whichever subsections are closest).  This prevents parallel 
 * and closely space faults from having connections back and forth all the way down the section.
 * @author kevin
 *
 */
public class UCERF3SectionConnectionStrategy implements SectionConnectionStrategy {
	
	private double maxJumpDist;
	private CoulombRates coulombRates;

	public UCERF3SectionConnectionStrategy(double maxJumpDist, CoulombRates coulombRates) {
		this.maxJumpDist = maxJumpDist;
		this.coulombRates = coulombRates;
	}

	@Override
	public List<List<Integer>> computeCloseSubSectionsListList(
			List<? extends FaultSection> faultSectionData,
			Map<IDPairing, Double> subSectionDistances) {
		return computeCloseSubSectionsListList(faultSectionData, subSectionDistances,
				maxJumpDist, coulombRates);
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
			List<? extends FaultSection> faultSectionData,
			Map<IDPairing, Double> subSectionDistances,
			double maxJumpDist, CoulombRates coulombRates) {
		ArrayList<List<Integer>> sectionConnectionsListList = new ArrayList<List<Integer>>();
		for(int i=0;i<faultSectionData.size();i++)
			sectionConnectionsListList.add(new ArrayList<Integer>());

		// in case the sections here are subsections of larger sections, create a subSectionDataListList where each
		// ArrayList<FaultSectionPrefData> is a list of subsections from the parent section
		ArrayList<ArrayList<FaultSection>> subSectionDataListList = new ArrayList<>();
		int lastID=-1;
		ArrayList<FaultSection> newList = new ArrayList<>();
		for(int i=0; i<faultSectionData.size();i++) {
			FaultSection subSect = faultSectionData.get(i);
			int parentID = subSect.getParentSectionId();
			if(parentID != lastID || parentID == -1) { // -1 means there is no parent
				newList = new ArrayList<>();
				subSectionDataListList.add(newList);
				lastID = subSect.getParentSectionId();
			}
			newList.add(subSect);
		}


		// First, if larger sections have been sub-sectioned, fill in neighboring subsection connections
		// (using the other algorithm below might lead to subsections being skipped if their width is < maxJumpDist) 
		for(int i=0; i<subSectionDataListList.size(); ++i) {
			ArrayList<FaultSection> subSectList = subSectionDataListList.get(i);
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
			ArrayList<FaultSection> sect1_List = subSectionDataListList.get(i);
			for(int j=i+1; j<subSectionDataListList.size(); ++j) {
				ArrayList<FaultSection> sect2_List = subSectionDataListList.get(j);
				double minDist=Double.MAX_VALUE;
				int subSectIndex1 = -1;
				int subSectIndex2 = -1;
				// find the closest pair
				for(int k=0;k<sect1_List.size();k++) {
					for(int l=0;l<sect2_List.size();l++) {
						FaultSection data1 = sect1_List.get(k);
						FaultSection data2 = sect2_List.get(l);
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

}
