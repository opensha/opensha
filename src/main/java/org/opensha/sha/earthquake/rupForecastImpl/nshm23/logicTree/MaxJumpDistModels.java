package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.DistDependentJumpProbabilityCalc;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum MaxJumpDistModels implements LogicTreeNode {
	ONE(		1d),
	THREE(		3d),
	FIVE(		5d),
	SEVEN(		7d),
	NINE(		9d),
	ELEVEN(		11d),
	THIRTEEN(	13d),
	FIFTEEN(	15d);
	
	public static double WEIGHT_TARGET_R0 = 3d;
	
	private double weight;
	private final double maxDist;

	private MaxJumpDistModels(double maxDist) {
		this.maxDist = maxDist;
		this.weight = -1d;
	}
	
	public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet) {
		return new HardDistCutoffJumpProbCalc(getMaxDist());
	};

	@Override
	public String getShortName() {
		return "MaxDist"+oDF.format(maxDist)+"km";
	}

	@Override
	public String getName() {
		return "MaxDist="+oDF.format(maxDist)+"km";
	}
	
	public double getMaxDist() {
		return maxDist;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		if (fullBranch != null) {
			RupturePlausibilityModels model = fullBranch.getValue(RupturePlausibilityModels.class);
			if (maxDist > 5d && model == RupturePlausibilityModels.UCERF3)
				return 0d;
		}
		if (weight < 0) {
			synchronized (this) {
				if (weight < 0)
					updateWeights(WEIGHT_TARGET_R0);
			}
		}
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return getShortName();
	}
	
	private static void updateWeights(double r0) {
		MaxJumpDistModels[] models = MaxJumpDistModels.values();
		// sort by distance, decreasing
		Arrays.sort(models, new Comparator<MaxJumpDistModels>() {

			@Override
			public int compare(MaxJumpDistModels o1, MaxJumpDistModels o2) {
				return Double.compare(o2.maxDist, o1.maxDist);
			}
		});
		double[] jumpProbs = new double[models.length];
		for (int i=0; i<jumpProbs.length; i++)
			jumpProbs[i] = Shaw07JumpDistProb.calcJumpProbability(models[i].maxDist, 1, r0);
		double[] probDeltas = new double[jumpProbs.length];
		probDeltas[0] = jumpProbs[0];
		for (int i=1; i<probDeltas.length; i++)
			probDeltas[i] = jumpProbs[i]-probDeltas[i-1];
		
		double sumDeltas = StatUtils.sum(probDeltas);
		
		for (int i=0; i<probDeltas.length; i++) {
			double weight = probDeltas[i]/sumDeltas;
			models[i].weight = weight;
		}
	}
	
	private static final DecimalFormat oDF = new DecimalFormat("0.#");
	
	public static class HardDistCutoffJumpProbCalc implements BinaryJumpProbabilityCalc, DistDependentJumpProbabilityCalc {
		
		private double maxDist;

		public HardDistCutoffJumpProbCalc(double maxDist) {
			this.maxDist = maxDist;
			
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "MaxDist="+oDF.format(maxDist)+"km";
		}

		@Override
		public boolean isJumpAllowed(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			return (float)jump.distance <= (float)maxDist;
		}

		@Override
		public double calcJumpProbability(double distance) {
			if ((float)distance < (float)maxDist)
				return 1d;
			return 0;
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			return calcJumpProbability(jump.distance);
		}
		
	}
	
	public static void main(String[] args) {
//		updateWeights(3);
		for (MaxJumpDistModels model : values())
			System.out.println(model.getName()+", weight="+(float)model.getNodeWeight(null));
	}

}
