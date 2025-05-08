package org.opensha.sha.earthquake.faultSysSolution;

import java.io.IOException;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Interface for a logic tree node that converts a list of {@link FaultSection}s (from a {@link RupSetFaultModel})
 * to a list of smaller fault subsections. This is often implemented directly by a {@link RupSetFaultModel} or
 * {@link RupSetDeformationModel}, but can be separated out if needed.
 */
public interface RupSetSubsectioningModel extends LogicTreeNode {
	
	/**
	 * This breaks each section in the given {@link RupSetFaultModel} into smaller subsections.
	 * Subsections will be indexed starting at zero, and original parent section names & IDs are accessible via
	 * {@link FaultSection#getParentSectionId()} and {@link FaultSection#getParentSectionName()}.
	 * 
	 * @param faultModel
	 * @return shorter subsections, indexed starting at zero
	 */
	public default List<? extends FaultSection> buildSubSects(RupSetFaultModel faultModel) throws IOException {
		return buildSubSects(faultModel, faultModel.getFaultSections());
	}
	
	/**
	 * This breaks each section in the given list into smaller subsections
	 * Subsections will be indexed starting at zero, and original parent section names & IDs are accessible via
	 * {@link FaultSection#getParentSectionId()} and {@link FaultSection#getParentSectionName()}.
	 * 
	 * @param faultModel
	 * @param fullSections
	 * @return shorter subsections, indexed starting at zero
	 */
	public List<? extends FaultSection> buildSubSects(RupSetFaultModel faultModel, List<? extends FaultSection> fullSections);
	
}
