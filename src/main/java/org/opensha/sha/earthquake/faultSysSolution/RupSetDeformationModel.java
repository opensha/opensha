package org.opensha.sha.earthquake.faultSysSolution;

import java.io.IOException;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Interface for a deformation model that gives slip rates for a {@link RupSetFaultModel}, and usually breaks larger
 * fault sections into smaller subsections as well.
 * 
 * @author kevin
 *
 */
public interface RupSetDeformationModel extends LogicTreeNode {
	
	/**
	 * 
	 * @param faultModel
	 * @return true if this deformation model is applicable to the given fault model, otherwise false
	 */
	public boolean isApplicableTo(RupSetFaultModel faultModel);
	
	/**
	 * This applies the deformation model to the given fault model, usually also splitting the longer fault sections
	 * in the fault model into subsections.
	 * 
	 * @param faultModel
	 * @return builds the given deformation model
	 */
	public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException;

}
