package scratch.UCERF3.logicTree;

import java.util.Arrays;
import java.util.List;

import scratch.UCERF3.enumTreeBranches.InversionModels;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class ListBasedTreeTrimmer implements TreeTrimmer {
	
	private static List<Class<? extends LogicTreeBranchNode<?>>> classes = LogicTreeBranch.getLogicTreeNodeClasses();
	private List<List<LogicTreeBranchNode<?>>> limitations;
	private boolean nonZeroWeight;
	
	public static ListBasedTreeTrimmer getNonZeroWeightsTrimmer() {
		List<List<LogicTreeBranchNode<?>>> limitations = Lists.newArrayList();
		return new ListBasedTreeTrimmer(limitations, true);
	}
		
	public static ListBasedTreeTrimmer getDefaultPlusSpecifiedTrimmer(List<List<LogicTreeBranchNode<?>>> customLimitations) {
		List<List<LogicTreeBranchNode<?>>> limitations = Lists.newArrayList();
		
		for (int i=0; i<classes.size(); i++) {
			Class<? extends LogicTreeBranchNode<?>> clazz = classes.get(i);
			List<LogicTreeBranchNode<?>> limits = null;
			if (customLimitations != null) {
				for (List<LogicTreeBranchNode<?>> customLimits : customLimitations) {
					if (customLimits != null && !customLimits.isEmpty()) {
						Class<LogicTreeBranchNode<?>> customClass = getClassForList(customLimits);
						if (customClass.equals(clazz)) {
							limits = customLimits;
							break;
						}
					}
				}
			}
			if (limits == null) {
				limits = Lists.newArrayList();
				limits.add(LogicTreeBranch.DEFAULT.getValue(i));
			}
			limitations.add(limits);
		}
		
		return new ListBasedTreeTrimmer(limitations);
	}
	
	public ListBasedTreeTrimmer(List<List<LogicTreeBranchNode<?>>> limitationsList) {
		this(limitationsList, false);
	}
	
	public ListBasedTreeTrimmer(List<List<LogicTreeBranchNode<?>>> limitationsList, boolean nonZeroWeight) {
		init(limitationsList, nonZeroWeight);
	}
	
	public ListBasedTreeTrimmer(LogicTreeBranch defaultBranch, boolean nonZeroWeight) {
		List<List<LogicTreeBranchNode<?>>> limitationsList = Lists.newArrayList();
		
		for (int i=0; i<defaultBranch.size(); i++) {
			LogicTreeBranchNode<?> val = defaultBranch.getValue(i);
			if (val != null) {
				List<LogicTreeBranchNode<?>> list = Lists.newArrayList();
				list.add(val);
				limitationsList.add(list);
			}
		}
		
		init(limitationsList, nonZeroWeight);
	}
	
	private void init(List<List<LogicTreeBranchNode<?>>> limitationsList, boolean nonZeroWeight) {
		this.limitations = Lists.newArrayList();
		this.nonZeroWeight = nonZeroWeight;
		for (int i=0; i<classes.size(); i++)
			limitations.add(null);
		if (limitationsList != null) {
			for (List<LogicTreeBranchNode<?>> limits : limitationsList) {
				if (limits == null || limits.isEmpty())
					continue;
				Class<LogicTreeBranchNode<?>> clazz = getClassForList(limits);
				int index = classes.indexOf(clazz);
				Preconditions.checkState(index >= 0, "Could not location class in class list: "+clazz);
				limitations.set(index, limits);
			}
		}
		Preconditions.checkState(limitations.size() == classes.size(), "limitations list must be same size as number of classes");
	}
	
	@SuppressWarnings("unchecked")
	private static Class<LogicTreeBranchNode<?>> getClassForList(List<LogicTreeBranchNode<?>> limits) {
		return (Class<LogicTreeBranchNode<?>>) LogicTreeBranch.getEnumEnclosingClass(limits.get(0).getClass());
	}

	@Override
	public boolean isTreeValid(LogicTreeBranch branch) {
		InversionModels im = branch.getValue(InversionModels.class);
		for (int i=0; i<branch.size(); i++) {
			LogicTreeBranchNode<?> val = branch.getValue(i);
			List<LogicTreeBranchNode<?>> myLimits = limitations.get(i);
			if (myLimits != null && !limitations.get(i).contains(val))
				return false;
			if (nonZeroWeight && val.getRelativeWeight(im) <= 0)
				return false;
		}
		return true;
	}

}
