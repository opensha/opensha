package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_ConstraintBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.data.NSHM23_WasatchSegmentationData;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels.ExcludeRupsThroughCreepingSegmentationModel;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalFaultModels;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

/**
 * NSHM23 segmentation models. Segmentation can be applied at individual jumps via an inversion constraint
 * (see {@link JumpProbabilityConstraint}), or by filtering out particular ruptures. Constraints are used for things
 * like distance-dependent segmentation and the Wasatch model, and are returned via
 * {@link #getModel(FaultSystemRupSet, LogicTreeBranch)}. Filtering is used for ruptures through the creeping
 * section (if {@link #isExcludeRupturesThroughCreepingSect()}). 
 * 
 * @author kevin
 *
 */
@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_SegmentationModels implements SegmentationModelBranchNode, RupsThroughCreepingSectBranchNode {
	/**
	 * No segmentation:
	 * 	* No distance-dependence
	 * 	* No Wasatch segmentation
	 * 	* No creeping section segmentation
	 * 	* Ruptures allowed through creeping section
	 */
	NONE("None", "None", 0.1) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			return null;
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			return false;
		}
	},
	/**
	 * Low segmentation:
	 * 	* Distance-dependent R0=4, horizontal shift of 3km
	 * 	* Wasatch segmentation P=0.75
	 * 	* Creeping section segmentation P=0.75 (applies to jumps to/from, not just ruptures that go through it)
	 * 	* Ruptures allowed through creeping section
	 */
	LOW("Low Segmentation", "Low",
			0.2, // weight
			4d, // R0
			3d,  // shift
			0.75, // creeping
			0.75) { // SAF
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			return buildModel(rupSet, shawR0, shawShift, wasatchProb, creepingProb, Double.NaN, false);
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			return false;
		}
	},
	/**
	 * Middle segmentation:
	 *	* Distance-dependent R0=3, horizontal shift of 2km
	 * 	* Wasatch segmentation P=0.5
	 * 	* Creeping section segmentation P=0.5 (applies to jumps to/from, not just ruptures that go through it)
	 * 	* Ruptures allowed through creeping section
	 */
	MID("Middle Segmentation", "Middle",
			0.3, // weight
			3d, // R0
			2d, // shift
			0.5, // creeping
			0.5) { // SAF
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			return buildModel(rupSet, shawR0, shawShift, wasatchProb, creepingProb, Double.NaN, false);
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			return false;
		}
	},
	/**
	 * High segmentation:
	 * 	* Distance-dependent R0=2, horizontal shift of 1km
	 * 	* Wasatch segmentation P=0.25
	 * 	* Creeping section segmentation P=0.25 (applies to jumps to/from, not just ruptures that go through it)
	 * 	* Ruptures prohibited through creeping section
	 */
	HIGH("High Segmentation", "High",
			0.3, // weight
			2d, // R0
			1d, // shift
			0.25, // creeping
			0.25) { // SAF
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			return buildModel(rupSet, shawR0, shawShift, wasatchProb, creepingProb, Double.NaN, false);
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			return true;
		}
	},
	/**
	 * Classic segmentation:
	 * 	* Named-fault segmentation, all other parents isolated and solved for analytically
	 * 	* Distance-dependent R0=2, horizontal shift of 1km (within named fault clusters)
	 * 	* Wasatch segmentation P=0
	 * 	* Creeping section segmentation P=0 (Creeping section treated as isolated and solved analytically)
	 * 	* Ruptures prohibited through creeping section
	 */
	CLASSIC("Classic", "Classic",
			0.1, // weight
			2d, // R0
			1d, // shift
			0d, // creeping
			0d) { // SAF
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			Preconditions.checkNotNull(rupSet, "Can only build classic segmentation model when we have a rupture set");
			return buildModel(rupSet, shawR0, shawShift, wasatchProb, creepingProb, Double.NaN, true);
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			return true;
		}
	},
	/**
	 * Same as {@link #CLASSIC} with the added requirement that all faults sections rupture fully
	 */
	CLASSIC_FULL("Classic Full Section", "FullClassic",
			0d, // weight
			2d, // R0
			1d, // shift
			0d, // creeping
			0d) { // SAF
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			return CLASSIC.getModel(rupSet, branch);
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			return CLASSIC.isExcludeRupturesThroughCreepingSect();
		}
	},
	/**
	 * Weighted average of all segmentation branches
	 */
	AVERAGE("NSHM23 Average Segmentation", "AvgSeg", 0.0d, Double.NaN, Double.NaN, 0.475, 0.475) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			List<JumpProbabilityCalc> models = new ArrayList<>();
			List<Double> weights = new ArrayList<>();
			
			double tempWeight = 0d;
			for (NSHM23_SegmentationModels segModel : values()) {
				if (segModel != this && segModel.weight > 0d) {
					models.add(segModel.getModel(rupSet, branch));
					weights.add(segModel.weight);
					tempWeight += segModel.weight;
				}
			}
			final double totWeight = tempWeight;
			return new JumpProbabilityCalc() {

				@Override
				public boolean isDirectional(boolean splayed) {
					for (JumpProbabilityCalc model : models)
						if (model.isDirectional(splayed))
							return true;
					return false;
				}

				@Override
				public String getName() {
					return "NSHM23 Average";
				}

				@Override
				public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
					double ret = 0d;
					boolean allOne = true;
					boolean allZero = true;
					for (int i=0; i<models.size(); i++) {
						JumpProbabilityCalc model = models.get(i);
						double weight = weights.get(i);
						if (model == null) {
							ret += 1*weight;
							allZero = false;
						} else {
							double modelProb = model.calcJumpProbability(fullRupture, jump, false);
							allOne = allOne && modelProb == 1d;
							allZero = allZero && modelProb == 0d;
							ret += modelProb*weight;
						}
					}
					if (allOne)
						// avoid any floating point issues by summing and dividing
						return 1d;
					if (allZero)
						return 0d;
					return ret/totWeight;
				}
				
			};
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			double weightInclude = 0d;
			double weightExclude = 0d;
			for (NSHM23_SegmentationModels segModel : values()) {
				if (segModel != this && segModel.weight > 0d) {
					if (segModel.isExcludeRupturesThroughCreepingSect())
						weightExclude += segModel.weight;
					else
						weightInclude += segModel.weight;
				}
			}
			return weightExclude > weightInclude;
		}
	};
	
	private String name;
	private String shortName;
	private double weight;

	protected double shawR0;
	protected double shawShift;
	
	protected double creepingProb;
	protected double wasatchProb;

	public static boolean LIMIT_MAX_LENGTHS = true;
	public static double SINGLE_MAX_LENGTH_LIMIT = Double.NaN;

	private NSHM23_SegmentationModels(String name, String shortName, double weight) {
		this(name, shortName, weight, Double.NaN, Double.NaN, 1d, 1d);
	}

	private NSHM23_SegmentationModels(String name, String shortName, double weight,
			double shawR0, double shawShift, double creepingProb, double wasatchProb) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
		this.shawR0 = shawR0;
		this.shawShift = shawShift;
		this.creepingProb = creepingProb;
		this.wasatchProb = wasatchProb;
	}
	
	public Shaw07JumpDistProb getShawModel() {
		if (Double.isFinite(shawR0))
			return shawShift > 0d ?
					Shaw07JumpDistProb.forHorzOffset(1d, shawR0, shawShift) : new Shaw07JumpDistProb(1d, shawR0);
		return null;
	}
	
	public abstract JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch);
	
	private static JumpProbabilityCalc buildModel(FaultSystemRupSet rupSet, double shawR0, double shawShift,
			double wasatchProb, double creepingProb, double hardCutoff, boolean namedFaultsOnly) {
		// distance dependent model, possibly with a horizontal shift
		JumpProbabilityCalc model = null;
		if (Double.isFinite(hardCutoff))
			model = new HardDistCutoffJumpProbCalc(hardCutoff);
		if (Double.isFinite(shawR0)) {
			JumpProbabilityCalc shawModel = shawShift > 0d ?
					Shaw07JumpDistProb.forHorzOffset(1d, shawR0, shawShift) : new Shaw07JumpDistProb(1d, shawR0);
			if (model == null)
				model = shawModel;
			else
				model = new JumpProbabilityCalc.Minimum(model, shawModel);
		}
		if (rupSet != null) {
			if (creepingProb < 1d) {
				int creepingParentID = NSHM23_ConstraintBuilder.findCreepingSection(rupSet);
				if (creepingParentID >= 0) {
					JumpProbabilityCalc creepModel;
					if (creepingProb == 0d)
						creepModel = new CreepingSectionBinaryJumpExcludeModel(creepingParentID);
					else
						creepModel = new CreepingSectionJumpSegModel(creepingParentID, creepingProb);
					if (model == null)
						model = creepModel;
					else
						model = new JumpProbabilityCalc.Minimum(model, creepModel);
				}
			}
			
			// Wasatch model
			JumpProbabilityCalc wasatch = NSHM23_WasatchSegmentationData.build(rupSet, wasatchProb);
			if (wasatch != null) {
				// this rupture set has Wasatch
				if (model == null)
					model = wasatch;
				else
					model = new JumpProbabilityCalc.Minimum(model, wasatch);
			}
			
			if (namedFaultsOnly) {
				NamedFaults namedFaults = rupSet.getModule(NamedFaults.class);
				if (namedFaults == null)
					// no named fault data, don't allow any jumps
					return new JumpProbabilityCalc.NoJumps();
				NamedFaultSegmentationModel namedFaultsModel = new NamedFaultSegmentationModel(namedFaults);
				if (model == null)
					model = namedFaultsModel;
				else if (model instanceof BinaryJumpProbabilityCalc)
					model = new JumpProbabilityCalc.LogicalAnd((BinaryJumpProbabilityCalc)model, namedFaultsModel);
				else
					model = new JumpProbabilityCalc.Minimum(model, namedFaultsModel);
			}
		}
		return model;
	}
	
	/**
	 * @return r0 for distance-dependent segmentation, or NaN if not applicable
	 */
	public double getShawR0() {
		return shawR0;
	}
	
	/**
	 * @return horizontal distance shift for distance-dependent segmentation, or NaN if not applicable
	 */
	public double getShawShift() {
		return shawShift;
	}
	
	public double getCreepingSectPassthrough() {
		return creepingProb;
	}
	
	public double getWasatchPassthrough() {
		return wasatchProb;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return shortName.replace("R₀=", "R0_");
	}
	
	/**
	 * @return maximum allowed rupture length, in km
	 */
	public double getMaxRuptureLength() {
		if (LIMIT_MAX_LENGTHS) {
			if (Double.isFinite(SINGLE_MAX_LENGTH_LIMIT) && SINGLE_MAX_LENGTH_LIMIT > 0d)
				return SINGLE_MAX_LENGTH_LIMIT;
			// model specific
			switch (this) {
			case NONE:
				return Double.POSITIVE_INFINITY;
			case LOW:
				return 800d;
			case MID:
				return 700d;
			case HIGH:
				return 600d;
			case CLASSIC:
				return 500d;
			case CLASSIC_FULL:
				return CLASSIC.getMaxRuptureLength();
			case AVERAGE:
				return MID.getMaxRuptureLength();

			default:
				throw new IllegalStateException();
			}
		}
		return Double.POSITIVE_INFINITY;
	}
	
	@Override
	public BinaryRuptureProbabilityCalc getExclusionModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		List<BinaryRuptureProbabilityCalc> exclusions = new ArrayList<>();
		BinaryRuptureProbabilityCalc primaryExclusion = SegmentationModelBranchNode.super.getExclusionModel(rupSet, branch);
		if (primaryExclusion != null)
			exclusions.add(primaryExclusion);
		
		if (isExcludeRupturesThroughCreepingSect()) {
			int creepingSectID = NSHM23_ConstraintBuilder.findCreepingSection(rupSet);
			if (creepingSectID >= 0)
				exclusions.add(new ExcludeRupsThroughCreepingSegmentationModel(creepingSectID));
		}
		
		if (this == CLASSIC_FULL)
			// we're on the "classic full" branch: exclude all ruptures that don't rupture a full section
			exclusions.add(new FullSectionsSegmentationModel(rupSet));
		
		if (this == CLASSIC && branch.hasValue(SectionSupraSeisBValues.class)) {
			// see if we're on the b=0 branch
			if (branch.hasValue(SupraSeisBValues.B_0p0) || branch.requireValue(SectionSupraSeisBValues.class).getB() == 0d) {
				// we're on the "classic" branch and b=0: exclude all ruptures that don't rupture a full section,
				// except on special faults
				boolean excludeNamed, excludeProxies;
				if (branch.hasValue(PRVI25_CrustalFaultModels.class)) {
					excludeNamed = false;
					excludeProxies = true;
				} else {
					excludeNamed = rupSet.hasModule(NamedFaults.class);
					excludeProxies = false;
				}
				exclusions.add(new FullSectionsSegmentationModel(rupSet, excludeNamed, excludeProxies));
			}
		}
		
		double maxRupLength = getMaxRuptureLength();
		if (maxRupLength > 0d && Double.isFinite(maxRupLength)) {
			// limit total length
			exclusions.add(new MaxLengthSegmentationModel(maxRupLength));
		}
		
		if (exclusions.isEmpty())
			return null;
		if (exclusions.size() == 1)
			return exclusions.get(0);
		
		System.out.println("Combining "+exclusions.size()+" segmentation exclusion models");
		
		return new RuptureProbabilityCalc.LogicalAnd(exclusions.toArray(new BinaryRuptureProbabilityCalc[0]));
	}

	public static class CreepingSectionJumpSegModel implements JumpProbabilityCalc {
		
		private int creepingParentID;
		private double prob;

		public CreepingSectionJumpSegModel(int creepingParentID, double prob) {
			this.creepingParentID = creepingParentID;
			this.prob = prob;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "Creeping Section Jump P≤"+(float)prob;
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			if (jump.fromCluster.parentSectionID == creepingParentID || jump.toCluster.parentSectionID == creepingParentID)
				return prob;
			return 1;
		}
		
	}
	
	public static class CreepingSectionBinaryJumpExcludeModel implements BinaryJumpProbabilityCalc {
		
		private int creepingParentID;

		public CreepingSectionBinaryJumpExcludeModel(int creepingParentID) {
			this.creepingParentID = creepingParentID;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "Exclude Creeping Section Jumps";
		}

		@Override
		public boolean isJumpAllowed(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			if (jump.fromCluster.parentSectionID == creepingParentID || jump.toCluster.parentSectionID == creepingParentID)
				return false;
			return true;
		}
		
	}
	
	public static class NamedFaultSegmentationModel implements BinaryJumpProbabilityCalc {
		
		private NamedFaults namedFaults;

		public NamedFaultSegmentationModel(NamedFaults namedFaults) {
			this.namedFaults = namedFaults;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "Named Fault Segmentation";
		}

		@Override
		public boolean isJumpAllowed(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			String faultName1 = namedFaults.getFaultName(jump.fromCluster.parentSectionID);
			String faultName2 = namedFaults.getFaultName(jump.toCluster.parentSectionID);
			if (faultName1 == null || faultName2 == null)
				// at least one of these sections is not part of a named fault
				return false;
			// both sections are part of a named fault, lets see if they're the same one
			return faultName1.equals(faultName2);
		}
		
	}
	
	public static class FullSectionsSegmentationModel implements BinaryRuptureProbabilityCalc {

		private Map<Integer, List<FaultSection>> parentSectsMap;
		private boolean excludeNamed;
		private NamedFaults namedFaults;
		private boolean excludeProxies;

		public FullSectionsSegmentationModel(FaultSystemRupSet rupSet) {
			this(rupSet, false);
		}

		public FullSectionsSegmentationModel(FaultSystemRupSet rupSet, boolean excludeNamed) {
			this(rupSet, excludeNamed, false);
		}

		public FullSectionsSegmentationModel(FaultSystemRupSet rupSet, boolean excludeNamed, boolean excludeProxies) {
			this(rupSet.getFaultSectionDataList().stream().collect(
					Collectors.groupingBy(S -> S.getParentSectionId())),
					excludeNamed, excludeProxies, rupSet.getModule(NamedFaults.class));
		}
		
		public FullSectionsSegmentationModel(Map<Integer, List<FaultSection>> parentSectsMap, boolean excludeNamed,
				boolean excludeProxies, NamedFaults namedFaults) {
			this.parentSectsMap = parentSectsMap;
			this.excludeNamed = excludeNamed;
			this.excludeProxies = excludeProxies;
			if (excludeNamed)
				Preconditions.checkNotNull(namedFaults, "excludeNamed == true but NamedFaults are null");
			this.namedFaults = namedFaults;
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
			for (FaultSubsectionCluster cluster : fullRupture.getClustersIterable()) {
				if (excludeNamed && namedFaults.getFaultName(cluster.parentSectionID) != null)
					continue;
				if (excludeProxies && cluster.startSect.isProxyFault())
					continue;
				List<FaultSection> fullCluster = parentSectsMap.get(cluster.parentSectionID);
				Preconditions.checkNotNull(fullCluster);
				Preconditions.checkState(fullCluster.size() >= cluster.subSects.size());
				if (fullCluster.size() != cluster.subSects.size())
					return false;
			}
			
			return true;
		}
		
	}
	
	public static class ExcludeRupsThroughCreepingSegmentationModel implements BinaryRuptureProbabilityCalc {
		
		private int creepingParentID;

		public ExcludeRupsThroughCreepingSegmentationModel(int creepingParentID) {
			this.creepingParentID = creepingParentID;
		}

		@Override
		public String getName() {
			return "Exclude Ruptures Through Creeping";
		}
		
		@Override
		public boolean isDirectional(boolean splayed) {
			return splayed;
		}
		
		@Override
		public boolean isRupAllowed(ClusterRupture fullRupture, boolean verbose) {
			return !NSHM23_ConstraintBuilder.isRupThroughCreeping(creepingParentID, fullRupture);
		}
		
	}
	
	public static class MaxLengthSegmentationModel implements BinaryRuptureProbabilityCalc {
		
		private double maxLen;
		private ConcurrentMap<FaultSection, Double> sectLenCache;

		public MaxLengthSegmentationModel(double maxLen) {
			this.maxLen = maxLen;
			sectLenCache = new ConcurrentHashMap<>();
		}

		@Override
		public String getName() {
			return "Rupture Len <="+(float)maxLen+"km";
		}
		
		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}
		
		@Override
		public boolean isRupAllowed(ClusterRupture fullRupture, boolean verbose) {
			if (fullRupture.getTotalNumJumps() == 0)
				return true;
			double len = 0d;
			for (FaultSubsectionCluster cluster : fullRupture.getClustersIterable()) {
				for (FaultSection sect : cluster.subSects) {
					Double sectLen = sectLenCache.get(sect);
					if (sectLen == null) {
						sectLen = sect.getFaultTrace().getTraceLength();
						sectLenCache.putIfAbsent(sect, sectLen);
					}
					len += sectLen;
				}
			}
			if ((float)len > (float)maxLen)
				return false;
			return true;
		}
		
	}

}
