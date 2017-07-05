package scratch.UCERF3.inversion.laughTest;

import java.util.HashSet;
import java.util.List;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import scratch.UCERF3.utils.IDPairing;

import com.google.common.collect.Lists;

public abstract class AbstractLaughTest {
	
	/**
	 * This can be used on an entire candidate rupture to see if it passes (this is slow, however).
	 * @param rupture
	 * @param junctionIndexes
	 * @return
	 */
	public boolean doesRupturePass(List<FaultSectionPrefData> rupture) {
		List<FaultSectionPrefData> subRup = Lists.newArrayList();
		List<Integer> subJunctions = Lists.newArrayList();
		List<IDPairing> pairings = Lists.newArrayList();
		
		boolean pass = true;
		for (int i=0; i<rupture.size(); i++) {
			FaultSectionPrefData sect = rupture.get(i);
			subRup.add(sect);
			if (i > 0)
				pairings.add(new IDPairing(rupture.get(i-1).getSectionId(),
						sect.getSectionId()));
			boolean junction = i > 0 &&
					subRup.get(i).getParentSectionId() != subRup.get(i-1).getParentSectionId();
			if (junction)
				subJunctions.add(i);
			else if (isApplyJunctionsOnly())
				continue;
			
			pass = doesLastSectionPass(subRup, pairings, subJunctions);
			if (!pass && !isContinueOnFaulure())
				return false;
		}
		return pass;
	}
	
	/**
	 * This is called to see if the last sect (the most recently added section on the end of the
	 * rupture) passes or not. This method should be used iteratively when building ruptures as it is
	 * much faster than checking the entire rupture each time.
	 * 
	 * @param rupture list of fault sections which constitute the rupture
	 * @param pairings list of pairings (of size rupture.size()-1)
	 * @param junctionIndexes list of indexes of sections within the rupture that have a different
	 * parent section ID than the previous section.
	 * @return
	 */
	public abstract boolean doesLastSectionPass(List<FaultSectionPrefData> rupture,
			List<IDPairing> pairings, List<Integer> junctionIndexes);
	
	/**
	 * This determines if a rupture should continue to be built without actually adding it upon a failure.
	 * This is needed if a rupture doesn't pass as is, but adding another section could change that.
	 * @return
	 */
	public abstract boolean isContinueOnFaulure();
	
	/**
	 * This determines if the filter should only be applied when the most recently added section is a
	 * junction to a new parent section.
	 * @return
	 */
	public abstract boolean isApplyJunctionsOnly();

}
