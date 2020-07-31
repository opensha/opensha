package scratch.UCERF3.inversion;

import java.util.ArrayList;
import java.util.HashSet;
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
	
	public default List<List<FaultSection>> getNextPossibleRuptures(
			List<? extends FaultSection> curRupture, HashSet<Integer> curSectIDs,
			List<Integer> connections, List<? extends FaultSection> allSectionData) {
		List<List<FaultSection>> ret = new ArrayList<>();
		for (Integer connectionID : connections) {
			if (curSectIDs.contains(connectionID))
				// this section is already included, skip
				continue;
			FaultSection connection = allSectionData.get(connectionID);
			List<FaultSection> newRupture = new ArrayList<>(curRupture);
			newRupture.add(connection);
			ret.add(newRupture);
		}
		
		return ret;
	}

}
