package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;

import com.google.common.base.Preconditions;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Slip-rate segmentation constraint. Applies rupture segmentation as a fractional slip-rate constraint between faults.
 * The segmentation model can theoretically take any parameters, but the first implementation is the Shaw (2007) distance
 * model. A {@link RateCombiner} decides how quantities are combined across either side of the jump, for example,
 * one might want to base this constraint on the lower of the two slip rates (or the average).
 * <br>
 * This constraint can be applied either as an equality constraint (will try to force connections to exactly match the
 * segmentation rate) or as an inequality constraint (will try to force connections not to exceed the segmentation rate).
 * 
 * @author Kevin Milner and Ned Field
 *
 */
public class SlipRateSegmentationConstraint extends InversionConstraint {
	
	private FaultSystemRupSet rupSet;
	private SegmentationModel segModel;
	private RateCombiner combiner;
	private double weight;
	private boolean normalized;
	private boolean inequality;
	
	/*
	 *  map from jumps to rupture IDs that use that jump. Within the jump, fromID will always be < toID
	 */
	private Map<Jump, List<Integer>> jumpRupturesMap;

	public static interface SegmentationModel {
		public double calcReductionBetween(Jump jump);
	}
	
	public static class Shaw07JumpDistSegModel implements SegmentationModel {
		
		private double a;
		private double r0;

		public Shaw07JumpDistSegModel(double a, double r0) {
			this.a = a;
			this.r0 = r0;
		}

		@Override
		public double calcReductionBetween(Jump jump) {
			return Shaw07JumpDistProb.calcJumpProbability(jump.distance, a, r0);
		}
		
	}
	
	public enum RateCombiner {
		MIN("Min Rate") {
			@Override
			public double combine(double rate1, double rate2) {
				return Math.min(rate1, rate2);
			}
		},
		MAX("Max Rate") {
			@Override
			public double combine(double rate1, double rate2) {
				return Math.max(rate1, rate2);
			}
		},
		AVERAGE("Avg Rate") {
			@Override
			public double combine(double rate1, double rate2) {
				return 0.5*(rate1 + rate2);
			}
		};
		
		private String label;

		private RateCombiner(String label) {
			this.label = label;
		}
	
		@Override
		public String toString() {
			return label;
		}
		
		public abstract double combine(double rate1, double rate2);
	}
	
	/**
	 * 
	 * 
	 * @param rupSet rupture set, must have average slip, slip along rupture, and slip rate data attached
	 * @param segModel segmentation model to use, e.g., Shaw '07
	 * @param combiner method for combining rates across each side of each jump
	 * @param weight constraint weight
	 * @param inequality false for equality (exactly match seg. rate), true for inequality (don't exceed seg. rate)
	 */
	public SlipRateSegmentationConstraint(FaultSystemRupSet rupSet, SegmentationModel segModel,
			RateCombiner combiner, double weight, boolean normalized, boolean inequality) {
		this.rupSet = rupSet;
		this.segModel = segModel;
		this.combiner = combiner;
		this.weight = weight;
		this.normalized = normalized;
		this.inequality = inequality;
		
		Preconditions.checkState(rupSet.hasModule(AveSlipModule.class),
				"Rupture set does not have average slip data");
		Preconditions.checkState(rupSet.hasModule(SlipAlongRuptureModel.class),
				"Rupture set does not have slip along rupture data");
		Preconditions.checkState(rupSet.hasModule(SectSlipRates.class),
				"Rupture set does not have slip rate data");
		if (!rupSet.hasModule(ClusterRuptures.class))
			rupSet.addModule(ClusterRuptures.singleStranged(rupSet));
		
		System.out.println("Detecting jumps for segmentation constraint");
		jumpRupturesMap = new HashMap<>();
		
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		int jumpingRups = 0;
		for (int r=0; r<cRups.size(); r++) {
			ClusterRupture rup = cRups.get(r);
//			System.out.println("Rupture "+r+": "+rup);
			boolean hasJumps = false;
			for (Jump jump : rup.getJumpsIterable()) {
				if (jump.fromSection.getSectionId() > jump.toSection.getSectionId())
					jump = jump.reverse();
				List<Integer> jumpRups = jumpRupturesMap.get(jump);
				if (jumpRups == null) {
					jumpRups = new ArrayList<>();
					jumpRupturesMap.put(jump, jumpRups);
				}
				jumpRups.add(r);
				hasJumps = true;
			}
			if (hasJumps)
				jumpingRups++;
		}
		System.out.println("Found "+jumpRupturesMap.size()+" unique jumps, involving "+jumpingRups+" ruptures");
	}

	@Override
	public String getShortName() {
		if (normalized)
			return "NormSlipSeg";
		return "SlipSeg";
	}

	@Override
	public String getName() {
		if (normalized)
			return "Normalized Slip Rate Segmentation";
		return "Slip Rate Segmentation";
	}

	@Override
	public int getNumRows() {
		// one row for each unique section-to-section connection
		return jumpRupturesMap.size();
	}

	@Override
	public boolean isInequality() {
		return inequality;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		int row = startRow;
		
		List<Jump> jumps = new ArrayList<>(jumpRupturesMap.keySet());
		Collections.sort(jumps, Jump.id_comparator); // sort jumps for consistent row ordering
		
		AveSlipModule aveSlips = rupSet.requireModule(AveSlipModule.class);
		SlipAlongRuptureModel slipAlongModel = rupSet.requireModule(SlipAlongRuptureModel.class);
		SectSlipRates slipRates = rupSet.requireModule(SectSlipRates.class);
		
		long count = 0;
		
		for (Jump jump : jumps) {
			int fromID = jump.fromSection.getSectionId();
			int toID = jump.toSection.getSectionId();
			
			double rate1 = slipRates.getSlipRate(fromID);
			double rate2 = slipRates.getSlipRate(toID);
			// this is the combined slip rate across both sections (e.g., the lower of the two, avg, etc)
			double combRate = combiner.combine(rate1, rate2);
			Preconditions.checkState(Double.isFinite(combRate), "Non-finite combined slip-rate: %s", combRate);
			double segFract = segModel.calcReductionBetween(jump);
			Preconditions.checkState(Double.isFinite(segFract) && segFract >= 0d && segFract <= 1d,
					"Bad segmentation fraction: %s", segFract);
			// apply the segmentation model as a fraction of that combined rate
			double segRate = combRate * segFract;
			Preconditions.checkState(Double.isFinite(segRate), "Non-finite segmentation rate: %s", segRate);
			
			for (int rup : jumpRupturesMap.get(jump)) {
				double[] slips = slipAlongModel.calcSlipOnSectionsForRup(rupSet, aveSlips, rup);
				List<Integer> sects = rupSet.getSectionsIndicesForRup(rup);
				
				double slip1 = Double.NaN;
				double slip2 = Double.NaN;
				for (int i=0; i<slips.length; i++) {
					int sect = sects.get(i);
					if (sect == fromID)
						slip1 = slips[i];
					else if (sect == toID)
						slip2 = slips[i];
				}
				
				// combine the rupture slip on either side of the jump
				double avgSlip = combiner.combine(slip1, slip2);
				Preconditions.checkState(Double.isFinite(avgSlip),
						"Non-finite average slip across jump: %s (from %s and %s)", avgSlip, slip1, slip2);
				if (normalized)
					setA(A, row, rup, weight*avgSlip/combRate);
				else
					setA(A, row, rup, weight*avgSlip);
				count++;
			}
			
			if (normalized)
				d[row] = weight*segFract;
			else
				d[row] = weight*segRate;
			
			row++;
		}
		return count;
	}

}
