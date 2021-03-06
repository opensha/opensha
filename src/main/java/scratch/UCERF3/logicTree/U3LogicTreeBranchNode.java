package scratch.UCERF3.logicTree;

import java.io.Serializable;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;

import scratch.UCERF3.enumTreeBranches.InversionModels;

public interface U3LogicTreeBranchNode<E extends Enum<E>> extends LogicTreeNode, Serializable {
	
	/**
	 * This returns the relative weight of the logic tree branch. Now dependent on Inversion Model as weights may change.
	 * A better implementation (possibly using LogicTreeBranch or an external weight fetcher class) may be better, but this
	 * will work for now.
	 * @return
	 */
	public double getRelativeWeight(InversionModels im);
	
	/**
	 * This encodes the choice as a string that can be used in file names
	 * @return
	 */
	public String encodeChoiceString();
	
	/**
	 * This just exposes the <code>Enum.name()</code> method
	 * @return
	 */
	public String name();
	
	/**
	 * This returns the human readable branch level name, e.g. "Fault Model"
	 * @return
	 */
	public String getBranchLevelName();
	
	/**
	 * This returns the short branch level name, e.g. "FM"
	 * @return
	 */
	public String getShortBranchLevelName();

	@Override
	public default double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		InversionModels im = fullBranch == null ? InversionModels.CHAR_CONSTRAINED : fullBranch.getValue(InversionModels.class);
		return getRelativeWeight(im);
	}

	@Override
	public default String getFilePrefix() {
		return encodeChoiceString();
	}

}
