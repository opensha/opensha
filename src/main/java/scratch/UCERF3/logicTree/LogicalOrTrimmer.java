package scratch.UCERF3.logicTree;

import java.util.List;

public class LogicalOrTrimmer extends LogicalAndOrTrimmer {

	public LogicalOrTrimmer(List<TreeTrimmer> trimmers) {
		super(false, trimmers);
	}

	public LogicalOrTrimmer(TreeTrimmer... trimmers) {
		super(false, trimmers);
	}

}
