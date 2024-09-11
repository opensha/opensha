package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum SupraSeisBValues implements LogicTreeNode, SectionSupraSeisBValues.Constant {
	
//	B_0p0(0d,	0.04),
//	B_0p2(0.2,	0.06),
//	B_0p4(0.4,	0.1),
//	B_0p6(0.6,	0.3),
//	B_0p8(0.8,	0.3),
//	B_1p0(1d,	0.2);
	
	B_0p0(0d,		0.2),
	B_0p25(0.25,	0.2),
	B_0p5(0.5,		0.2),
	B_0p75(0.75,	0.2),
	B_1p0(1d,		0.2),
	AVERAGE(0.5,	0d) {

		@Override
		public String getShortName() {
			return "bAvg="+(float)bValue;
		}

		@Override
		public String getName() {
			return super.getName()+" (Averaged)";
		}

		@Override
		public String getFilePrefix() {
			return "AvgSupraB";
		}
		
	};
	
//	B_0p0(0d, 0.05),
//	B_0p25(0.25, 0.1),
//	B_0p5(0.5, 0.20),
//	B_0p75(0.75, 0.5),
//	B_1p0(1.0, 0.15);
	
	public final double bValue;
	public final double weight;

	private SupraSeisBValues(double bValue, double weight) {
		this.bValue = bValue;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return getName();
	}

	@Override
	public String getName() {
		return "b="+(float)bValue;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return "SupraB"+(float)bValue;
	}

	@Override
	public double getB() {
		return bValue;
	}
	
	public static void main(String[] args) {
		double totWeight = 0d;
		double avg = 0d;
		for (SupraSeisBValues b : values()) {
			avg += b.weight*b.bValue;
			totWeight += b.weight;
		}
		avg /= totWeight;
		System.out.println("weight average b: "+(float)avg);
		System.out.println("tot weight: "+(float)totWeight);
	}

}
