package org.opensha.commons.logicTree.treeCombiner;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.opensha.commons.data.Named;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.treeCombiner.AbstractLogicTreeCombiner.LogicTreeCombinationContext;

import com.google.common.base.Stopwatch;

public interface LogicTreeCombinationProcessor extends Named {
	
	void init(LogicTreeCombinationContext treeCombination,
			ExecutorService exec, ExecutorService ioExec) throws IOException;
	
	void processBranch(LogicTreeBranch<?> combBranch, int combBranchIndex, double combBranchWeight,
			LogicTreeBranch<?> outerBranch, int outerBranchIndex,
			LogicTreeBranch<?> innerBranch, int innerBranchIndex) throws IOException;
	
	void close() throws IOException;
	
	String getTimeBreakdownString(Stopwatch overallWatch);
}