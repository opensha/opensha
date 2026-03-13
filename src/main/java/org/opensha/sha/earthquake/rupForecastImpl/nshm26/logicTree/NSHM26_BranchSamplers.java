package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.text.DecimalFormat;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.TruncatedNormalDistribution;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.RandomlyGeneratedNode;

public class NSHM26_BranchSamplers {

	private static DecimalFormat twoDigits = new DecimalFormat("0.00");
	private static DecimalFormat threeDigits = new DecimalFormat("0.000");
	
	private static final ContinuousDistribution INTERFACE_B_DIST = TruncatedNormalDistribution.of(1d, 0.1, 0.7, 1.3);
//	public static class InterfaceB extends LogicTreeLevel.ContinuousDistributionSampledLevel {
//
//		public InterfaceB() {
//			this(INTERFACE_B_DIST);
//		}
//
//		public InterfaceB(ContinuousDistribution dist) {
//			super("Interface b-value", "Interface b", dist);
//		}
//
//		@Override
//		protected String getName(int index, Double value) {
//			return "Interface b="+threeDigits.format(index);
//		}
//
//		@Override
//		protected String getShortName(int index, Double value) {
//			return "b="+threeDigits.format(index);
//		}
//
//		@Override
//		protected String getFilePrefix(int index, Double value) {
//			return "IntBSample"+index;
//		}
//		
//	}
//	
//	
//	public static class InterfaceMinSumSects extends LogicTreeLevel.RandomlySampledLevel<Integer> {
//
//		public InterfaceMinSumSects() {
//			super("Interface Minimum Subsection Count", "InterfaceMinSects");
//		}
//
//		@Override
//		protected String getName(int index, Integer value) {
//			// TODO Auto-generated method stub
//			return null;
//		}
//
//		@Override
//		protected String getShortName(int index, Integer value) {
//			// TODO Auto-generated method stub
//			return null;
//		}
//
//		@Override
//		protected String getFilePrefix(int index, Integer value) {
//			// TODO Auto-generated method stub
//			return null;
//		}
//		
//	}

}
