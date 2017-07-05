package scratch.UCERF3.inversion.laughTest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import com.google.common.base.Preconditions;

import scratch.UCERF3.utils.IDPairing;

/**
 * This restricts the maximum azimuth change of any junction in the rupture. Azimuth
 * changes are computed as the azimuth change between the midpoints of two sections
 * on the same fault. For this reason, 2 sections are required per fault to compute
 * accurate azimuths.
 * 
 * @author kevin
 *
 */
public class AzimuthChangeFilter extends AbstractLaughTest {
	
	private boolean applyGarlockPintoMtnFix;
	private HashSet<Integer> leftLateralFixParents;
	private Map<IDPairing, Double> sectionAzimuths;
	
	/** this was a bug in UCERF3.2 where the total azimuth change was only checked after junctions */
	private boolean totAzChangeAtJunctionsOnly = false;
	
	private double maxAzimuthChange;
	private double maxTotAzimuthChange;
	
	public AzimuthChangeFilter(double maxAzimuthChange, double maxTotAzimuthChange,
			boolean applyGarlockPintoMtnFix, Map<IDPairing, Double> sectionAzimuths) {
		this.maxAzimuthChange = maxAzimuthChange;
		this.maxTotAzimuthChange = maxTotAzimuthChange;
		this.applyGarlockPintoMtnFix = applyGarlockPintoMtnFix;
		this.sectionAzimuths = sectionAzimuths;
		if (applyGarlockPintoMtnFix)
			setUCERF3p3LL_List();
	}
	
	public void setUCERF3p2LL_List() {
		leftLateralFixParents = new HashSet<Integer>();
		leftLateralFixParents.add(48);
		leftLateralFixParents.add(49);
		leftLateralFixParents.add(93);
		leftLateralFixParents.add(341);
	}
	
	public void setUCERF3p3LL_List() {
		setUCERF3p2LL_List();
		leftLateralFixParents.add(47);
		leftLateralFixParents.add(169);
	}

	@Override
	public boolean doesLastSectionPass(List<FaultSectionPrefData> rupture,
			List<IDPairing> pairings,
			List<Integer> junctionIndexes) {
		// there must be at least 4 sections and at least one junction
		if (rupture.size() < 4 || junctionIndexes.isEmpty())
			return true;
		
		int lastIndexInRup = rupture.size()-1;
		
		// test the last junction if a junction happened last time, so 2 sections
		// from the new parent have been added to this rupture
		boolean testLast = junctionIndexes.contains(lastIndexInRup-1);
		// test total if we're testing the last one, or 
		boolean testTotal = testLast || !totAzChangeAtJunctionsOnly;
		// don't test total if we've only added one section of a parent, we'll check it next time
		if (junctionIndexes.get(junctionIndexes.size()-1) == lastIndexInRup)
			testTotal = false;
		
		if (!testLast && !testTotal)
			// if we're not testing anything, go ahead and pass
			return true;
		
		// this is the first section, used for total azimuth change checks
		IDPairing firstPairing = pairings.get(0);
		int firstSectParent = rupture.get(0).getParentSectionId();
		// this is the previous parent section, used for last section checks
		// we go 2 pairings back because we want the azimuth of the last two subsections on the
		// previous parent
		IDPairing prevSectPairing = pairings.get(pairings.size()-3);
		int prevSectParent = rupture.get(lastIndexInRup-2).getParentSectionId();
		// this is our newest section, used for both tests
		IDPairing newSectPairing = pairings.get(pairings.size()-1);
		int newSectParent = rupture.get(lastIndexInRup).getParentSectionId();
		
//		if (firstSectParent == newSectParent) {
//			for (FaultSectionPrefData data : rupture)
//				System.out.println(data.getSectionId()+" ("+data.getParentSectionId()+")");
//			System.out.flush();
//		}
		
		if (testLast && !testAzimuth(prevSectPairing, prevSectParent, newSectPairing, newSectParent, maxAzimuthChange))
			return false;
		
		if (totAzChangeAtJunctionsOnly && leftLateralFixParents != null
				&& leftLateralFixParents.contains(firstSectParent)) // this keeps UCERF3.2 compatability
			firstPairing = firstPairing.getReversed();
		if (testTotal && !testAzimuth(firstPairing, firstSectParent, newSectPairing, newSectParent, maxTotAzimuthChange))
			return false;
		
		return true;
	}
	
	private boolean testAzimuth(IDPairing pairing1, int parent1, IDPairing pairing2, int parent2, double threshold) {
		// we don't need this check anymore, a rupture can 
//		Preconditions.checkState(parent1 != parent2, "Makes no sense to check azimuths on the same parent");
		if (applyGarlockPintoMtnFix) {
			if (leftLateralFixParents.contains(parent1))
				pairing1 = pairing1.getReversed();
			
			if (leftLateralFixParents.contains(parent2))
				pairing2 = pairing2.getReversed();
		}
		
		double az1 = sectionAzimuths.get(pairing1);
		double az2 = sectionAzimuths.get(pairing2);
		
		return Math.abs(getAzimuthDifference(az1, az2)) <= threshold;
	}

	@Override
	public boolean isContinueOnFaulure() {
		return false;
	}

	@Override
	public boolean isApplyJunctionsOnly() {
		return false;
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

	public boolean isTotAzChangeAtJunctionsOnly() {
		return totAzChangeAtJunctionsOnly;
	}

	public void setTotAzChangeAtJunctionsOnly(boolean totAzChangeAtJunctionsOnly) {
		this.totAzChangeAtJunctionsOnly = totAzChangeAtJunctionsOnly;
	}

}
