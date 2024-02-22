package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import org.opensha.commons.calc.WeightedSampler;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomlySampledLevel;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_ConstraintBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels.ExcludeRupsThroughCreepingSegmentationModel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels.MaxLengthSegmentationModel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModelBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class RandomSegModelSampler implements BranchDependentSampler<RandomSegModelSampler> {
	
	// globals
	private Map<UniqueJump, NSHM23_SegmentationModels> jumpsToModels;
	private boolean allowThroughCreeping;
	private double maxRupLength;
	
	// specific to conn clusters
	private transient JumpProbabilityCalc probCalc;
	private transient BinaryRuptureProbabilityCalc exclusionModel;
	
	private static CreepingTreatment CREEPING_TREATMENT_DEFAULT = CreepingTreatment.SAMPLE_WITHIN_INCLUSION_CHOICE;
	
	public enum CreepingTreatment {
		FULLY_UNCORRELATED,
		FULLY_CORRELATED,
		SAMPLE_WITHIN_INCLUSION_CHOICE
	}

	public RandomSegModelSampler(FaultSystemRupSet rupSet, WeightedSampler<NSHM23_SegmentationModels> enumSampler,
			CreepingTreatment creepingTreatment, WeightedSampler<NSHM23_SegmentationModels> creepingAllowedSampler,
			WeightedSampler<NSHM23_SegmentationModels> creepingExcludedSampler) {
		// determine all jumps
		HashSet<UniqueJump> allJumps = new HashSet<>();
		int creepingParentID = -1;
		if (creepingTreatment != CreepingTreatment.FULLY_UNCORRELATED) {
			// use a separate sampler 
			creepingParentID = NSHM23_ConstraintBuilder.findCreepingSection(rupSet);
			if (creepingTreatment == CreepingTreatment.SAMPLE_WITHIN_INCLUSION_CHOICE) {
				Preconditions.checkNotNull(creepingAllowedSampler);
				Preconditions.checkNotNull(creepingExcludedSampler);
			}
			
		}
		for (ClusterRupture rup : rupSet.requireModule(ClusterRuptures.class))
			for (Jump jump : rup.getJumpsIterable())
				allJumps.add(new UniqueJump(jump));
		// sort for reproducibility
		List<UniqueJump> allJumpsList = new ArrayList<>(allJumps);
		Collections.sort(allJumpsList);
		jumpsToModels = new HashMap<>(allJumpsList.size());
		NSHM23_SegmentationModels creepingModel = enumSampler.nextItem();
		allowThroughCreeping = creepingModel.isIncludeRupturesThroughCreepingSect();
		maxRupLength = enumSampler.nextItem().getMaxRuptureLength();
		for (UniqueJump jump : allJumpsList) {
			if (jump.parent1 == creepingParentID || jump.parent2 == creepingParentID) {
				NSHM23_SegmentationModels model;
				switch (creepingTreatment) {
				case FULLY_CORRELATED:
					model = creepingModel;
					break;
				case FULLY_UNCORRELATED:
					model = enumSampler.nextItem();
					break;
				case SAMPLE_WITHIN_INCLUSION_CHOICE:
					if (allowThroughCreeping)
						model = creepingAllowedSampler.nextItem();
					else
						model = creepingExcludedSampler.nextItem();
					break;

				default:
					throw new IllegalStateException();
				}
				jumpsToModels.put(jump, model);
			} else {
				jumpsToModels.put(jump, enumSampler.nextItem());
			}
		}
		Preconditions.checkState(jumpsToModels.size() == allJumpsList.size());
	}
	
	private RandomSegModelSampler(Map<UniqueJump, NSHM23_SegmentationModels> jumpsToModels,
			boolean allowThroughCreeping, double maxRupLength) {
		this.jumpsToModels = jumpsToModels;
		this.allowThroughCreeping = allowThroughCreeping;
		this.maxRupLength = maxRupLength;
	}
	
	public synchronized JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		if (probCalc == null)
			probCalc = new DeferringRandomJumpProbCalc(rupSet, branch, jumpsToModels);
		return probCalc;
	}
	
	public synchronized BinaryRuptureProbabilityCalc getExclusionModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		if (exclusionModel == null) {
			List<BinaryRuptureProbabilityCalc> exclusions = new ArrayList<>();
			BinaryRuptureProbabilityCalc primaryExclusion = SegmentationModelBranchNode.buildJumpExclusionModel(
					rupSet, getModel(rupSet, branch));
			if (primaryExclusion != null)
				exclusions.add(primaryExclusion);
			
			if (!allowThroughCreeping) {
				int creepingSectID = NSHM23_ConstraintBuilder.findCreepingSection(rupSet);
				if (creepingSectID >= 0)
					exclusions.add(new ExcludeRupsThroughCreepingSegmentationModel(creepingSectID));
			}
			
			if (maxRupLength > 0d && Double.isFinite(maxRupLength)) {
				// limit total length
				exclusions.add(new MaxLengthSegmentationModel(maxRupLength));
			}
			
			if (branch.hasValue(SupraSeisBValues.B_0p0) || branch.hasValue(RandomBValSampler.Node.class)) {
				// we have b=0 cases, see if we need to apply them anywhere
				BinaryRuptureProbabilityCalc upstreamExclusion = null;
				if (exclusions.size() == 1)
					upstreamExclusion = exclusions.get(0);
				else if (exclusions.size() > 1)
					upstreamExclusion = new RuptureProbabilityCalc.LogicalAnd(exclusions.toArray(new BinaryRuptureProbabilityCalc[0]));
				double[] bValues;
				if (branch.hasValue(RandomBValSampler.Node.class)) {
					// get b-values
					RandomBValSampler bSampler = rupSet.requireModule(BranchSamplingManager.class).getSampler(
							branch.requireValue(RandomBValSampler.Node.class));
					bValues = bSampler.getBValues();
				} else {
					// this initializes to all zeros
					bValues = new double[rupSet.getNumSections()];
				}
				exclusions.add(new B0_UnconnectedFullSectionsSegmentationModel(rupSet, bValues, upstreamExclusion));
			}
			
			if (exclusions.isEmpty())
				return null;
			if (exclusions.size() == 1)
				return exclusions.get(0);
			
			System.out.println("Combining "+exclusions.size()+" exclusion models");
			
			exclusionModel = new RuptureProbabilityCalc.LogicalAnd(exclusions.toArray(new BinaryRuptureProbabilityCalc[0]));
		}
		return exclusionModel;
	}
	
	public static class B0_UnconnectedFullSectionsSegmentationModel implements BinaryRuptureProbabilityCalc {
		
		private Map<Integer, List<FaultSection>> parentSectsMap;
		private HashSet<Integer> connectedSects;
		private double[] bValues;

		public B0_UnconnectedFullSectionsSegmentationModel(FaultSystemRupSet rupSet,
				double[] bValues, BinaryRuptureProbabilityCalc exclusion) {
			this.bValues = bValues;
			parentSectsMap = rupSet.getFaultSectionDataList().stream().collect(
					Collectors.groupingBy(S -> S.getParentSectionId()));
			// figure out which sections are isolated
			connectedSects = new HashSet<>();
			if (exclusion != null) {
				for (ClusterRupture rup : rupSet.requireModule(ClusterRuptures.class)) {
					if (rup.getTotalNumClusters() == 1 || !exclusion.isRupAllowed(rup, false))
						// either a single section rupture and thus no connectivity, or already excluded
						continue;
					// if we're here this is a multisection rupture that is included by every other rule
					for (FaultSubsectionCluster cluster : rup.getClustersIterable())
						connectedSects.add(cluster.parentSectionID);
				}
			}
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "Full Section Segmentation";
		}

		@Override
		public boolean isRupAllowed(ClusterRupture fullRupture, boolean verbose) {
			if (fullRupture.getTotalNumClusters() > 1)
				// multi segment rupture so connected by default
				return true;
			
			FaultSubsectionCluster cluster = fullRupture.clusters[0];
			int parentID = cluster.parentSectionID;
			if (connectedSects.contains(parentID))
				// not isolated
				return true;
			
			// check the b-values
			for (FaultSection sect : cluster.subSects)
				if (bValues[sect.getSectionId()] != 0d)
					// not b=0
					return true;

			
			// if we've made it this far, it's an isolated b=0 section
			// now check to see if it's a full segment rupture
			List<FaultSection> fullCluster = parentSectsMap.get(parentID);
			if (fullCluster.size() != cluster.subSects.size())
				// it's a partial rupture
				return false;
			
			// full segment rupture, still allowed
			return true;
		}
		
	}

	@Override
	public RandomSegModelSampler getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
		return new RandomSegModelSampler(jumpsToModels, allowThroughCreeping, maxRupLength);
	}
	
	private static class DeferringRandomJumpProbCalc implements JumpProbabilityCalc {
		
		private EnumMap<NSHM23_SegmentationModels, JumpProbabilityCalc> modelCalcs;
		private Map<UniqueJump, NSHM23_SegmentationModels> jumpsToModels;
		
		public DeferringRandomJumpProbCalc(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				Map<UniqueJump, NSHM23_SegmentationModels> jumpsToModels) {
			this.jumpsToModels = jumpsToModels;
			modelCalcs = new EnumMap<>(NSHM23_SegmentationModels.class);
			for (NSHM23_SegmentationModels model : jumpsToModels.values())
				if (!modelCalcs.containsKey(model))
					modelCalcs.put(model, model.getModel(rupSet, branch));
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			for (JumpProbabilityCalc calc : modelCalcs.values())
				if (calc.isDirectional(splayed))
					return true;
			return false;
		}

		@Override
		public String getName() {
			return "Deferring Randomly Sampled Jump Prob Calc";
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			UniqueJump unique = new UniqueJump(jump);
			NSHM23_SegmentationModels model = jumpsToModels.get(unique);
			if (verbose) System.out.println("Mapped jump "+jump+" to seg model "+model);
			Preconditions.checkNotNull(model, "Jump not found: %s", jump);
			JumpProbabilityCalc calc = modelCalcs.get(model);
			if (calc == null) {
				// happens for the NONE model, but make sure we added null and it's not just unmapped
				Preconditions.checkState(modelCalcs.containsKey(model),
						"Calc never instantiated for model %s (jump %s)?", model, jump);
				return 1d;
			}
			return calc.calcJumpProbability(fullRupture, jump, verbose);
		}
		
	}
	
	/**
	 * We want a unique hash for jumps but not one that's sensative to subsection index as we do cluster-specific inversions
	 */
	private static class UniqueJump implements Comparable<UniqueJump> {
		private final int parent1;
		private final String name1;
		private final int parent2;
		private final String name2;
		private final double distance;
		
		public UniqueJump(Jump jump) {
			if (jump.fromCluster.parentSectionID < jump.toCluster.parentSectionID) {
				parent1 = jump.fromCluster.parentSectionID;
				parent2 = jump.toCluster.parentSectionID;
				name1 = jump.fromSection.getSectionName();
				name2 = jump.toSection.getSectionName();
			} else {
				parent2 = jump.fromCluster.parentSectionID;
				parent1 = jump.toCluster.parentSectionID;
				name2 = jump.fromSection.getSectionName();
				name1 = jump.toSection.getSectionName();
			}
			Preconditions.checkState(parent1 != parent2, "Can't jump to yourself");
			distance = jump.distance;
		}

		@Override
		public int hashCode() {
			return Objects.hash(distance, name1, name2, parent1, parent2);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			UniqueJump other = (UniqueJump) obj;
			return Double.doubleToLongBits(distance) == Double.doubleToLongBits(other.distance)
					&& Objects.equals(name1, other.name1) && Objects.equals(name2, other.name2)
					&& parent1 == other.parent1 && parent2 == other.parent2;
		}

		@Override
		public int compareTo(UniqueJump o) {
			int cmp = Integer.compare(parent1, o.parent1);
			if (cmp == 0)
				cmp = Integer.compare(parent2, o.parent2);
			if (cmp == 0)
				cmp = Double.compare(distance, o.distance);
			if (cmp == 0)
				cmp = name1.compareTo(o.name1);
			if (cmp == 0)
				cmp = name2.compareTo(o.name2);
			return cmp;
		}
	}
	
	@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	public static class Node extends AbstractSamplingNode<RandomSegModelSampler> implements SegmentationModelBranchNode {
		
		@SuppressWarnings("unused") // deserialization
		private Node() {}
		
		public Node(int index, long seed, double weight) {
			super("Segmentation Model Sample "+index, "SegSample"+index, "SegSample"+index, weight, seed);
		}

		@Override
		public RandomSegModelSampler buildSampler(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch, long branchNodeSamplingSeed) {
			System.out.println("Building seg model sampler for "+getShortName()+" with seed: "+branchNodeSamplingSeed);
			Random random = new Random(branchNodeSamplingSeed);
			WeightedSampler<NSHM23_SegmentationModels> enumSampler = weightedNodeValueSampler(
					random, NSHM23_SegmentationModels.class);
			
			CreepingTreatment creepingTreatment = CREEPING_TREATMENT_DEFAULT;
			WeightedSampler<NSHM23_SegmentationModels> creepingAllowedSampler = null;
			WeightedSampler<NSHM23_SegmentationModels> creepingExcludedSampler = null;
			if (creepingTreatment == CreepingTreatment.SAMPLE_WITHIN_INCLUSION_CHOICE) {
				List<NSHM23_SegmentationModels> allowedNodes = new ArrayList<>();
				List<Double> allowedWeights = new ArrayList<>();
				List<NSHM23_SegmentationModels> excludedNodes = new ArrayList<>();
				List<Double> excludedWeights = new ArrayList<>();
				for (NSHM23_SegmentationModels node : NSHM23_SegmentationModels.values()) {
					double weight = node.getNodeWeight(null);
					if (weight > 0d) {
						if (node.isIncludeRupturesThroughCreepingSect()) {
							allowedNodes.add(node);
							allowedWeights.add(weight);
						} else {
							excludedNodes.add(node);
							excludedWeights.add(weight);
						}
					}
				}
				creepingAllowedSampler = new WeightedSampler<>(allowedNodes, allowedWeights, random);
				creepingExcludedSampler = new WeightedSampler<>(excludedNodes, excludedWeights, random);
			}
			return new RandomSegModelSampler(rupSet, enumSampler,
					creepingTreatment, creepingAllowedSampler, creepingExcludedSampler);
		}

		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			BranchSamplingManager manager = rupSet.requireModule(BranchSamplingManager.class);
			RandomSegModelSampler sampler = manager.getSampler(this);
			return sampler.getModel(rupSet, branch);
		}

		@Override
		public BinaryRuptureProbabilityCalc getExclusionModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			BranchSamplingManager manager = rupSet.requireModule(BranchSamplingManager.class);
			RandomSegModelSampler sampler = manager.getSampler(this);
			return sampler.getExclusionModel(rupSet, branch);
		}

	}
	
	public static class Level extends RandomlySampledLevel<Node> {
		
		public Level() {
			
		}
		
		public Level(int numSamples) {
			this(numSamples, new Random());
		}
		
		public Level(int numSamples, Random rand) {
			buildNodes(rand, numSamples);
		}

		@Override
		public String getShortName() {
			return "SegSamples";
		}

		@Override
		public String getName() {
			return "Segmentation Model Samples";
		}

		@Override
		public Node buildNodeInstance(int index, long seed, double weight) {
			return new Node(index, seed, weight);
		}

		@Override
		public Class<? extends Node> getType() {
			return Node.class;
		}
		
	}

}
