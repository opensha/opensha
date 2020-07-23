package scratch.UCERF3.inversion;

import java.util.List;
import java.util.Map;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.faultSurface.FaultSection;

public interface SectionConnectionStrategy {
	
	/**
	 * For each section, create a list of possibly connected sections. This is the set of all
	 * possible jumps, though actual ruptures will also need to pass plausibility criteria.
	 */
	public List<List<Integer>> computeCloseSubSectionsListList(
			List<? extends FaultSection> faultSectionData,
			Map<IDPairing, Double> subSectionDistances);

}
