package scratch.UCERF3.logicTree;

import java.util.List;

public class LogicalAndTrimmer extends LogicalAndOrTrimmer {

	public LogicalAndTrimmer(List<TreeTrimmer> trimmers) {
		super(true, trimmers);
	}

	public LogicalAndTrimmer(TreeTrimmer... trimmers) {
		super(true, trimmers);
	}

}
