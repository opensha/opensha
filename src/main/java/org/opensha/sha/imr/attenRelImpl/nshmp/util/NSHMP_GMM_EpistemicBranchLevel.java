package org.opensha.sha.imr.attenRelImpl.nshmp.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.logicTree.LogicTreeLevel.AdapterBackedLevel;
import org.opensha.commons.util.FileNameUtils;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.imr.attenRelImpl.nshmp.GroundMotionLogicTreeFilter;

import com.google.common.base.Preconditions;

import org.opensha.nshmp.shaded.gmm.NshmpGmm;
import org.opensha.nshmp.shaded.gmm.NshmpGmmInput;
import org.opensha.nshmp.shaded.gmm.NshmpGroundMotion;
import org.opensha.nshmp.shaded.gmm.NshmpGroundMotionModel;
import org.opensha.nshmp.shaded.tree.NshmpBranch;
import org.opensha.nshmp.shaded.tree.NshmpLogicTree;

public abstract class NSHMP_GMM_EpistemicBranchLevel extends AdapterBackedLevel {
	
	private List<NSHMP_GMM_Branch> nodes;
	
	public NSHMP_GMM_EpistemicBranchLevel(List<NSHMP_GMM_Branch> nodes, String name, String shortName) {
		super(name, shortName, NSHMP_GMM_Branch.class);
		this.nodes = nodes;
	}
	
	public static List<NSHMP_GMM_Branch> buildNodes(NshmpGmm gmm, String shortName, boolean expandTree) {
		return buildNodes(List.of(gmm), List.of(shortName), List.of(1d), expandTree);
	}
	
	public static List<NSHMP_GMM_Branch> buildNodes(List<NshmpGmm> gmms, List<String> gmmShortNames,
			List<Double> gmmWeights, boolean expandTree) {
		return buildNodes(gmms, gmmShortNames, gmmWeights, expandTree, null);
	}
	
	public static List<NSHMP_GMM_Branch> buildNodes(List<NshmpGmm> gmms, List<String> gmmShortNames,
			List<Double> gmmWeights, boolean expandTree, String commonFilePrefix) {
		Preconditions.checkArgument(!gmms.isEmpty());
		Preconditions.checkArgument(gmmShortNames.size() == gmms.size());
		Preconditions.checkArgument(gmmWeights.size() == gmms.size());
		
		if (commonFilePrefix == null)
			commonFilePrefix = "";
		
		String commonShortPrefix = "";
		if (gmms.size() > 1)
			commonShortPrefix = StringUtils.getCommonPrefix(gmmShortNames.toArray(new String[0]));
		
		NshmpGmmInput input = NshmpGmmInput.builder().withDefaults().build();
		
		List<NSHMP_GMM_Branch> nodes = new ArrayList<>();
		for (int i=0; i<gmms.size(); i++) {
			NshmpGmm gmm = gmms.get(i);
			double gmmWeight = gmmWeights.get(i);
			
			// see what kind of logic tree we have
			boolean doExpand = expandTree;
			NshmpLogicTree<NshmpGroundMotion> result = null;
			if (doExpand) {
				NshmpGroundMotionModel gmmInstance = gmm.instance(gmm.supportedImts().iterator().next());
				result = gmmInstance.calc(input);
				doExpand = result.size() > 1;
			}
			
			String namePrefix = gmm.toString();
			String shortNamePrefix;
			if (gmms.size() > 1) {
				// we have multiple GMMs, always prepend the name
				if (doExpand && !commonShortPrefix.isEmpty()) {
					// we have multiple epistemic values and a common prefix, just use the common prefix in the short name
					String uniqueName = gmmShortNames.get(i).substring(commonShortPrefix.length()).trim();
					shortNamePrefix = uniqueName;
				} else {
					shortNamePrefix = gmmShortNames.get(i);
				}
			} else {
				// we have a single GMM
				if (doExpand) {
					// multiple results, don't need a prefix (each one will have their own id)
					shortNamePrefix = "";
				} else {
					// one gmm and a single branch, need to have something
					shortNamePrefix = gmmShortNames.get(i);
				}
			}
			
			if (!doExpand) {
				// simple case, no filtering
				nodes.add(new NSHMP_GMM_Branch(gmm, null, namePrefix, shortNamePrefix, commonFilePrefix+getFilePrefix(shortNamePrefix), gmmWeight));
			} else {
				// expand the ground motion logic tree
				for (NshmpBranch<NshmpGroundMotion> branch : result) {
					String name = namePrefix+": "+branch.id();
					String shortName = shortNamePrefix;
					if (!shortName.isBlank())
						shortName += ": ";
					shortName += branch.id();
					nodes.add(new NSHMP_GMM_Branch(gmm, new GroundMotionLogicTreeFilter.StringMatching(branch.id()), name, shortName,
							commonFilePrefix+getFilePrefix(shortName), gmmWeight*branch.weight()));
				}
			}
		}
		
		return nodes;
	}
	
	private static String getFilePrefix(String shortName) {
		String filePrefix = shortName.replace("σ", "sig");
		return FileNameUtils.simplify(filePrefix);
	}

	@Override
	public List<? extends NSHMP_GMM_Branch> getNodes() {
		return nodes;
	}

	@Override
	public boolean isMember(LogicTreeNode node) {
		return nodes.contains(node);
	}

}
