package scratch.UCERF3.inversion.laughTest;

import java.util.List;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.Lists;

public abstract class AbstractPlausibilityFilter implements ShortNamed {
	
	/**
	 * This can be used on an entire candidate rupture to see if it passes (this is slow, however).
	 * @param rupture
	 * @param junctionIndexes
	 * @return
	 */
	public PlausibilityResult apply(List<? extends FaultSection> rupture) {
		List<FaultSection> subRup = Lists.newArrayList();
		List<Integer> subJunctions = Lists.newArrayList();
		List<IDPairing> pairings = Lists.newArrayList();
		
		PlausibilityResult result = PlausibilityResult.PASS;
		for (int i=0; i<rupture.size(); i++) {
			FaultSection sect = rupture.get(i);
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
			
			result = applyLastSection(subRup, pairings, subJunctions);
			if (!result.canContinue())
				break;
		}
		return result;
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
	public abstract PlausibilityResult applyLastSection(List<? extends FaultSection> rupture,
			List<IDPairing> pairings, List<Integer> junctionIndexes);
	
	/**
	 * This determines if the filter should only be applied when the most recently added section is a
	 * junction to a new parent section.
	 * @return
	 */
	public abstract boolean isApplyJunctionsOnly();

}
