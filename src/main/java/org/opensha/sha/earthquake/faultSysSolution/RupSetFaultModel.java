package org.opensha.sha.earthquake.faultSysSolution;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.RegionsOfInterest;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Interface for a fault model that can be used to build a {@link FaultSystemRupSet}.
 * 
 * @author kevin
 *
 */
public interface RupSetFaultModel extends LogicTreeNode {
	
	/**
	 * This retrieves raw fault sections for the given fault model, which may or may not have any slip rates attached.
	 * 
	 * @return list of fault sections
	 * @throws IOException
	 */
	public List<? extends FaultSection> getFaultSections() throws IOException;
	
	/**
	 * @return map from fault section IDs to fault sections
	 */
	public default Map<Integer, FaultSection> getFaultSectionIDMap() throws IOException {
		return getFaultSections().stream().collect(Collectors.toMap(FaultSection::getSectionId, Function.identity()));
	}
	
	/**
	 * Attaches any default modules related to this fault model, often {@link NamedFaults} and/or {@link RegionsOfInterest}.
	 * 
	 * @param rupSet
	 */
	public default void attachDefaultModules(FaultSystemRupSet rupSet) {
		// do nothing
	}
	
	/**
	 * This returns the default deformation model for the given fault model, which will be used to apply slip rates
	 * when building a {@link FaultSystemRupSet}. Some rupture plausibility models depend on slip rate and/or rake,
	 * and this model will be used in order to ensure a consistent rupture set across all deformation models.
	 * 
	 * @return default deformation model to be used when constructing a {@link FaultSystemRupSet}
	 */
	public RupSetDeformationModel getDefaultDeformationModel();

}
