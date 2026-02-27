package org.opensha.sha.earthquake.faultSysSolution;

import java.io.IOException;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.util.SubSectionBuilder;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

/**
 * Interface for a deformation model that gives slip rates on fault subsections. Those subsections are typically
 * provided by a {@link RupSetFaultModel} and ({@link RupSetSubsectioningModel}.
 * 
 * @author kevin
 *
 */
public interface RupSetDeformationModel extends LogicTreeNode {
	
	/**
	 * @param faultModel
	 * @return true if this deformation model is applicable to the given fault model, otherwise false
	 */
	public boolean isApplicableTo(RupSetFaultModel faultModel);
	
	/**
	 * This builds the deformation model according to the given {@link LogicTreeBranch}. The branch must contain a
	 * {@link RupSetFaultModel} that supplies the faults, and will likely contain a {@link RupSetSubsectioningModel}
	 * that breaks the full fault sections into smaller subsections. If a {@link RupSetSubsectioningModel} is not
	 * found, then it is assumed that the {@link RupSetFaultModel} already returns fault subections.
	 * 
	 * @param faultModel
	 * @param subSectionModel
	 * @param full logic tree branch (may be null)
	 * @return builds the given deformation model
	 */
	public default List<? extends FaultSection> build(LogicTreeBranch<? extends LogicTreeNode> branch) throws IOException {
		return build(branch.requireValue(RupSetFaultModel.class), branch.getValue(RupSetSubsectioningModel.class), branch);
	}
	
	/**
	 * This builds the deformation model to the given {@link RupSetFaultModel} that also is a
	 * {@link RupSetSubsectioningModel}. It assumes there is no additional logic tree branch needed to build the
	 * deformation model
	 * 
	 * @param faultAndSubSectModel
	 * @return builds the given deformation model
	 */
	public default <E extends RupSetFaultModel & RupSetSubsectioningModel> List<? extends FaultSection> build(
			E faultAndSubSectModel) throws IOException {
		return build(faultAndSubSectModel, faultAndSubSectModel, null);
	}

	
	/**
	 * This builds the deformation model to the given {@link RupSetFaultModel} that also is a
	 * {@link RupSetSubsectioningModel}.
	 * 
	 * @param faultAndSubSectModel
	 * @param full logic tree branch (may be null)
	 * @return builds the given deformation model
	 */
	public default <E extends RupSetFaultModel & RupSetSubsectioningModel> List<? extends FaultSection> build(
			E faultAndSubSectModel, LogicTreeBranch<? extends LogicTreeNode> branch) throws IOException {
		return build(faultAndSubSectModel, faultAndSubSectModel, branch);
	}
	
	/**
	 * This builds the deformation model to the given {@link RupSetFaultModel}, first splitting the longer fault sections
	 * in the fault model into subsections via the given {@link RupSetSubsectioningModel}.
	 * 
	 * @param faultModel
	 * @param subSectionModel subsectioning model; if null, it is assumed that the {@link RupSetFaultModel} already 
	 * provides subsections
	 * @param full logic tree branch (may be null)
	 * @return builds the given deformation model
	 */
	public default List<? extends FaultSection> build(RupSetFaultModel faultModel, RupSetSubsectioningModel subSectionModel,
			LogicTreeBranch<? extends LogicTreeNode> branch) throws IOException {
		Preconditions.checkState(isApplicableTo(faultModel),
				"Fault and deformation models are not compatible: %s, %s", faultModel.getName(), getName());
		List<? extends FaultSection> fullSects = faultModel.getFaultSections();
		List<? extends FaultSection> subSects;
		if (subSectionModel == null) {
			// assume (but validate) that the fault model already provides subsections
			SubSectionBuilder.validateSubSects(fullSects);
			subSects = fullSects;
		} else {
			subSects = subSectionModel.buildSubSects(faultModel, fullSects);
		}
		return apply(faultModel, branch, fullSects, subSects);
	}
	
	/**
	 * This applies the deformation model to the given pre-existing subsection list.
	 * <p>
	 * Note that subsection may (will likely be) updated in place, and thus the returned list may be the same subSects
	 * list that was passed.
	 * 
	 * @param faultModel
	 * @param full logic tree branch (may be null)
	 * @param subsects pre-existing subsection list
	 * @return subsection list with the deformation model applied.
	 */
	public default List<? extends FaultSection> apply(
			RupSetFaultModel faultModel, LogicTreeBranch<? extends LogicTreeNode> branch,
			List<? extends FaultSection> subSects) throws IOException {
		Preconditions.checkState(isApplicableTo(faultModel),
				"Fault and deformation models are not compatible: %s, %s", faultModel.getName(), getName());
		return apply(faultModel, branch, faultModel.getFaultSections(), subSects);
	}
	
	/**
	 * This applies the deformation model to the given pre-existing subsection list.
	 * <p>
	 * Note that subsection may be updated in place, and thus the returned list may be the same subSects list that was
	 * passed in.
	 * 
	 * @param faultModel
	 * @param full logic tree branch (may be null)
	 * @param fullSects full fault sections from the fault model
	 * @param subsects pre-existing subsection list
	 * @return subsection list with the deformation model applied.
	 */
	public List<? extends FaultSection> apply(
			RupSetFaultModel faultModel, LogicTreeBranch<? extends LogicTreeNode> branch,
			List<? extends FaultSection> fullSects, List<? extends FaultSection> subSects)
					throws IOException;

}
