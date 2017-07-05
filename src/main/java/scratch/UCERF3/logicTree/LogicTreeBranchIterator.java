package scratch.UCERF3.logicTree;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;


public class LogicTreeBranchIterator implements Iterable<LogicTreeBranch>, Iterator<LogicTreeBranch> {
	
	private List<Class<? extends LogicTreeBranchNode<?>>> classes;
	private int[] maxNums;
	private int[] curNums;
	
	private LogicTreeBranch next = null;
	
	private TreeTrimmer trimmer;
	private Iterator<LogicTreeBranch> customIt;
	
	private boolean exhausted = false;
	
	public LogicTreeBranchIterator() {
		this(null);
	}
	
	public LogicTreeBranchIterator(TreeTrimmer trimmer) {
		this.trimmer = trimmer;
		if (trimmer instanceof Iterable<?>)
			customIt = ((Iterable<LogicTreeBranch>)trimmer).iterator();
		classes = LogicTreeBranch.getLogicTreeNodeClasses();
		maxNums = new int[classes.size()];
		curNums = new int[classes.size()];
		
		for (int i=0; i<classes.size(); i++)
			maxNums[i] = classes.get(i).getEnumConstants().length-1;
		
		loadNext();
	}
	
	private void loadNext() {
		next = null;
		if (exhausted)
			return;
		if (customIt != null) {
			// use the built in iterator
			while (customIt.hasNext() && next == null) {
				LogicTreeBranch candidate = customIt.next();
				if (trimmer.isTreeValid(candidate))
					next = candidate;
			}
			if (next == null)
				exhausted = true;
		} else {
			mainLoop:
				while (next == null) {
					//					printNextState();
					LogicTreeBranch candidate = buildBranch();
					if (trimmer == null || trimmer.isTreeValid(candidate))
						next = candidate;

					// find where to increment
					for (int i=maxNums.length; --i>=0;) {
						if (curNums[i] < maxNums[i]) {
							// this is simple, just increment the current stage
							curNums[i]++;
							break;
						} else {
							// the current level of the tree is exhausted, set it to zero and keep searching
							curNums[i] = 0;
							if (i == 0) {
								// we're done
								exhausted = true;
								break mainLoop;
							}
							// reset all children of this back to the start
							for (int j=i+1; j<curNums.length; j++)
								curNums[j] = 0;
						}
					}
				}
		}
	}
	
	public void printNextState() {
		String str = null;
		for (int val : curNums) {
			if (str == null)
				str = "";
			else
				str += ",";
			str += val;
		}
		System.out.println(str);
	}
	
	private LogicTreeBranch buildBranch() {
		List<LogicTreeBranchNode<?>> vals = Lists.newArrayList();
		for (int i=0; i<curNums.length; i++) {
			vals.add(classes.get(i).getEnumConstants()[curNums[i]]);
		}
		return LogicTreeBranch.fromValues(vals);
	}
	
	@Override
	public Iterator<LogicTreeBranch> iterator() {
		return this;
	}

	@Override
	public boolean hasNext() {
		return next != null;
	}

	@Override
	public LogicTreeBranch next() {
		Preconditions.checkState(hasNext(), "next() called with no more branches!");
		LogicTreeBranch ret = next;
		loadNext();
		return ret;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("remove not supported!");
	}
	
	public static void main(String[] args) {
//		TreeTrimmer trimmer = null;
		TreeTrimmer trimmer = ListBasedTreeTrimmer.getNonZeroWeightsTrimmer();
//		TreeTrimmer trimmer = ListBasedTreeTrimmer;
		LogicTreeBranchIterator it = new LogicTreeBranchIterator(trimmer);
		
		double wtTotal = 0;
		int cnt = 0;
		for (LogicTreeBranch br : it) {
			Preconditions.checkNotNull(br);
			cnt++;
			wtTotal += br.getAprioriBranchWt();
		}
		System.out.println("TOTAL: "+cnt);
		System.out.println("WEIGHT SUM: "+wtTotal);
	}

}
