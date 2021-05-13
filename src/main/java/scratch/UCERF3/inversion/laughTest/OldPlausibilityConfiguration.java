package scratch.UCERF3.inversion.laughTest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.faultSurface.FaultSection;

public interface OldPlausibilityConfiguration extends XMLSaveable {
	
	/**
	 * Builds (or re-builds) the plausibility filters
	 * 
	 * @param azimuths map of azumuths between fault section pairings
	 * @param distances map of distances (km) between fault sections pairings
	 * @param sectionConnectionsListList list of connections from each section to
	 * each potentially connected section
	 * @param subSectData sub section data list
	 * @return
	 */
	public List<AbstractPlausibilityFilter> buildPlausibilityFilters(
			Map<IDPairing, Double> azimuths,
			Map<IDPairing, Double> distances,
			List<List<Integer>> sectionConnectionsListList,
			List<? extends FaultSection> subSectData);
	
	/**
	 * @return already built plausibility filters, or null if not yet built
	 */
	public List<AbstractPlausibilityFilter> getPlausibilityFilters();

	/**
	 * @return maximum possible jump distance in km (used to build the section 
	 * connections list, prior to plausibility filter application)
	 */
	public double getMaxJumpDist();
	
	/**
	 * @return parent section IDs to ignore when building ruptures
	 */
	public default HashSet<Integer> getParentSectsToIgnore() {
		return null;
	}

}
