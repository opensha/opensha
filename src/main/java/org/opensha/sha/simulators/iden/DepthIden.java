package org.opensha.sha.simulators.iden;

import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.Vertex;

import com.google.common.collect.Range;

public class DepthIden extends AbstractRuptureIdentifier {
	
	private Range<Double> upperDepthRange;
	private Range<Double> lowerDepthRange;

	public DepthIden(Range<Double> upperDepthRange, Range<Double> lowerDepthRange) {
		this.upperDepthRange = upperDepthRange;
		this.lowerDepthRange = lowerDepthRange;
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		double minDepth = Double.POSITIVE_INFINITY;
		double maxDepth = 0d;
		
		for (SimulatorElement elem : event.getAllElements()) {
			for (Vertex v : elem.getVertices()) {
				minDepth = Math.min(minDepth, v.getDepth());
				maxDepth = Math.max(maxDepth, v.getDepth());
			}
		}
		if (minDepth == -0)
			minDepth = 0;
		if (maxDepth == -0)
			maxDepth = 0;
//		System.out.println("depth range: "+minDepth+"\t"+maxDepth);
		if (upperDepthRange != null && !upperDepthRange.contains(minDepth))
			return false;
		if (lowerDepthRange != null && !lowerDepthRange.contains(maxDepth))
			return false;
//		System.out.println("\t...match!");
		return true;
	}

	@Override
	public String getName() {
		return "Depth Identifier";
	}

}
