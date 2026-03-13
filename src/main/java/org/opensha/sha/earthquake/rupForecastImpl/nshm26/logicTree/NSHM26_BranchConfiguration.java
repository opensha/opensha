package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.TruncatedNormalDistribution;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;

import com.google.common.base.Preconditions;

public class NSHM26_BranchConfiguration {

	// interface inversion
	public final NSHM26_InterfaceFaultModels interfaceFM;
	public final NSHM26_InterfaceDeformationModels interfaceDM;
	public final PRVI25_SubductionScalingRelationships interfaceScale;
	public static final ContinuousDistribution INTERFACE_B_DISTRIBUTION = TruncatedNormalDistribution.of(1d, 0.1, 0.7, 1.3);
	public final double interfaceB;
	public final int interfaceMinNumSubSects;
	public final NSHM26_InterfaceSubSeisAdjustment interfaceSubSeisAdjust;

	// interface gridded
	public final NSHM26_SeisRateModel interfaceGridRateModel;
	public final NSHM26_DeclusteringAlgorithms interfaceDecluster;
	public final NSHM26_SeisSmoothingAlgorithms interfaceSmoothing;

	// intraslab gridded
	public final double intraslabMmax;
	public final NSHM26_SeisRateModel intraslabRateModel;
	public final NSHM26_DeclusteringAlgorithms intraslabDecluster;
	public final NSHM26_SeisSmoothingAlgorithms intraslabSmoothing;

	// crustal inversion
	public final NSHM26_CrustalFaultModels crustalFM;
	public final NSHM26_CrustalRandomlySampledDeformationModels crustalDM;
	public final double crustalB;
	public final NSHM23_ScalingRelationships crustalScale;
	public final NSHM23_SegmentationModels crustalSeg;

	// crustal gridded
	public final double crustalMmax;
	public final NSHM26_SeisRateModel crustalRateModel;
	public final NSHM26_DeclusteringAlgorithms crustalDecluster;
	public final NSHM26_SeisSmoothingAlgorithms crustalSmoothing;

	private NSHM26_BranchConfiguration(Builder builder) {
		interfaceFM = builder.interfaceFM;
		interfaceDM = builder.interfaceDM;
		interfaceScale = builder.interfaceScale;
		interfaceB = builder.interfaceB;
		interfaceMinNumSubSects = builder.interfaceMinNumSubSects;
		interfaceSubSeisAdjust = builder.interfaceSubSeisAdjust;

		interfaceGridRateModel = builder.interfaceGridRateModel;
		interfaceDecluster = builder.interfaceDecluster;
		interfaceSmoothing = builder.interfaceSmoothing;

		intraslabMmax = builder.intraslabMmax;
		intraslabRateModel = builder.intraslabRateModel;
		intraslabDecluster = builder.intraslabDecluster;
		intraslabSmoothing = builder.intraslabSmoothing;

		crustalFM = builder.crustalFM;
		crustalDM = builder.crustalDM;
		crustalB = builder.crustalB;
		crustalScale = builder.crustalScale;
		crustalSeg = builder.crustalSeg;

		crustalMmax = builder.crustalMmax;
		crustalRateModel = builder.crustalRateModel;
		crustalDecluster = builder.crustalDecluster;
		crustalSmoothing = builder.crustalSmoothing;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private NSHM26_InterfaceFaultModels interfaceFM;
		private NSHM26_InterfaceDeformationModels interfaceDM;
		private PRVI25_SubductionScalingRelationships interfaceScale;
		private Double interfaceB;
		private Integer interfaceMinNumSubSects;
		private NSHM26_InterfaceSubSeisAdjustment interfaceSubSeisAdjust;

		private NSHM26_SeisRateModel interfaceGridRateModel;
		private NSHM26_DeclusteringAlgorithms interfaceDecluster;
		private NSHM26_SeisSmoothingAlgorithms interfaceSmoothing;

		private Double intraslabMmax;
		private NSHM26_SeisRateModel intraslabRateModel;
		private NSHM26_DeclusteringAlgorithms intraslabDecluster;
		private NSHM26_SeisSmoothingAlgorithms intraslabSmoothing;

		private boolean crustalInversionSet;
		private NSHM26_CrustalFaultModels crustalFM;
		private NSHM26_CrustalRandomlySampledDeformationModels crustalDM;
		private Double crustalB;
		private NSHM23_ScalingRelationships crustalScale;
		private NSHM23_SegmentationModels crustalSeg;

		private Double crustalMmax;
		private NSHM26_SeisRateModel crustalRateModel;
		private NSHM26_DeclusteringAlgorithms crustalDecluster;
		private NSHM26_SeisSmoothingAlgorithms crustalSmoothing;
		
		public Builder sample(long randSeed) {
			UniformRandomProvider rand = RandomSource.XO_RO_SHI_RO_128_PP.create(randSeed);
			interfaceFM = sampleEnumNode(NSHM26_InterfaceFaultModels.class, rand);
			interfaceDM = sampleEnumNode(NSHM26_InterfaceDeformationModels.class, rand);
			interfaceScale = sampleEnumNode(PRVI25_SubductionScalingRelationships.class, rand);
			interfaceB = INTERFACE_B_DISTRIBUTION.createSampler(rand).sample();
			return this;
		}
		
		private static <E extends Enum<E> & LogicTreeNode> E sampleEnumNode(Class<E> clazz, UniformRandomProvider rand) {
			E[] values = clazz.getEnumConstants();
			double[] weights = new double[values.length];
			for (int i=0; i<values.length; i++)
				weights[i] = values[i].getNodeWeight(null);
			IntegerPDF_FunctionSampler sampler = new IntegerPDF_FunctionSampler(weights);
			return values[sampler.getRandomInt(rand.nextDouble())];
		}

		public Builder interfaceInversion(NSHM26_InterfaceFaultModels fm,
				NSHM26_InterfaceDeformationModels dm,
				PRVI25_SubductionScalingRelationships scale,
				double b, int minNumSubSects,
				NSHM26_InterfaceSubSeisAdjustment subSeisAdjust) {
			Preconditions.checkNotNull(fm, "interfaceFM is required");
			Preconditions.checkNotNull(dm, "interfaceDM is required");
			Preconditions.checkNotNull(scale, "interfaceScale is required");
			Preconditions.checkArgument(Double.isFinite(b), "interfaceB must be finite");
			Preconditions.checkArgument(minNumSubSects > 0,
					"interfaceMinNumSubSects must be > 0");
			Preconditions.checkNotNull(subSeisAdjust, "interfaceSubSeisAdjust is required");
			this.interfaceFM = fm;
			this.interfaceDM = dm;
			this.interfaceScale = scale;
			this.interfaceB = b;
			this.interfaceMinNumSubSects = minNumSubSects;
			this.interfaceSubSeisAdjust = subSeisAdjust;
			return this;
		}

		public Builder interfaceSeismicity(NSHM26_SeisRateModel rateModel,
				NSHM26_DeclusteringAlgorithms decluster,
				NSHM26_SeisSmoothingAlgorithms smoothing) {
			Preconditions.checkNotNull(rateModel, "interface rate model is required");
			Preconditions.checkNotNull(decluster, "interface decluster is required");
			Preconditions.checkNotNull(smoothing, "interface smoothing is required");
			this.interfaceGridRateModel = rateModel;
			this.interfaceDecluster = decluster;
			this.interfaceSmoothing = smoothing;
			return this;
		}

		public Builder intraslabSeismicity(NSHM26_SeisRateModel rateModel,
				NSHM26_DeclusteringAlgorithms decluster,
				NSHM26_SeisSmoothingAlgorithms smoothing,
				double mMax) {
			Preconditions.checkNotNull(rateModel, "intraslab rate model is required");
			Preconditions.checkNotNull(decluster, "intraslab decluster is required");
			Preconditions.checkNotNull(smoothing, "intraslab smoothing is required");
			Preconditions.checkArgument(Double.isFinite(mMax), "intraslabMmax must be finite");
			this.intraslabRateModel = rateModel;
			this.intraslabDecluster = decluster;
			this.intraslabSmoothing = smoothing;
			this.intraslabMmax = mMax;
			return this;
		}

		public Builder crustalInversion(NSHM26_CrustalFaultModels fm,
				NSHM26_CrustalRandomlySampledDeformationModels dm, double b,
				NSHM23_ScalingRelationships scale, NSHM23_SegmentationModels seg) {
			Preconditions.checkNotNull(fm, "crustalFM is required");
			Preconditions.checkNotNull(dm, "crustalDM is required");
			Preconditions.checkArgument(Double.isFinite(b), "crustalB must be finite when crustal inversion is set");
			Preconditions.checkNotNull(scale, "crustalScale is required");
			Preconditions.checkNotNull(seg, "crustalSeg is required");
			this.crustalInversionSet = true;
			this.crustalFM = fm;
			this.crustalDM = dm;
			this.crustalB = b;
			this.crustalScale = scale;
			this.crustalSeg = seg;
			return this;
		}

		public Builder crustalSeismicity(NSHM26_SeisRateModel rateModel,
				NSHM26_DeclusteringAlgorithms decluster,
				NSHM26_SeisSmoothingAlgorithms smoothing,
				double mMax) {
			Preconditions.checkNotNull(rateModel, "crustal rate model is required");
			Preconditions.checkNotNull(decluster, "crustal decluster is required");
			Preconditions.checkNotNull(smoothing, "crustal smoothing is required");
			Preconditions.checkArgument(Double.isFinite(mMax), "crustalMmax must be finite");
			this.crustalRateModel = rateModel;
			this.crustalDecluster = decluster;
			this.crustalSmoothing = smoothing;
			this.crustalMmax = mMax;
			return this;
		}

		public NSHM26_BranchConfiguration build() {
			Preconditions.checkNotNull(interfaceFM, "interface inversion must be set");
			Preconditions.checkNotNull(interfaceDM, "interface inversion must be set");
			Preconditions.checkNotNull(interfaceScale, "interface inversion must be set");
			Preconditions.checkNotNull(interfaceB, "interface inversion must be set");
			Preconditions.checkNotNull(interfaceMinNumSubSects, "interface inversion must be set");
			Preconditions.checkNotNull(interfaceSubSeisAdjust, "interface inversion must be set");

			Preconditions.checkNotNull(interfaceGridRateModel, "interface seismicity must be set");
			Preconditions.checkNotNull(interfaceDecluster, "interface seismicity must be set");
			Preconditions.checkNotNull(interfaceSmoothing, "interface seismicity must be set");

			Preconditions.checkNotNull(intraslabMmax, "intraslabMmax must be set");
			Preconditions.checkNotNull(intraslabRateModel, "intraslab seismicity must be set");
			Preconditions.checkNotNull(intraslabDecluster, "intraslab seismicity must be set");
			Preconditions.checkNotNull(intraslabSmoothing, "intraslab seismicity must be set");

			Preconditions.checkNotNull(crustalMmax, "crustalMmax must be set");
			Preconditions.checkNotNull(crustalRateModel, "crustal seismicity must be set");
			Preconditions.checkNotNull(crustalDecluster, "crustal seismicity must be set");
			Preconditions.checkNotNull(crustalSmoothing, "crustal seismicity must be set");

			if (crustalInversionSet) {
				Preconditions.checkNotNull(crustalFM, "crustal inversion must be set");
				Preconditions.checkNotNull(crustalDM, "crustal inversion must be set");
				Preconditions.checkNotNull(crustalB, "crustal inversion must be set");
				Preconditions.checkNotNull(crustalScale, "crustal inversion must be set");
				Preconditions.checkNotNull(crustalSeg, "crustal inversion must be set");
			} else {
				crustalFM = null;
				crustalDM = null;
				crustalB = Double.NaN;
				crustalScale = null;
				crustalSeg = null;
			}

			return new NSHM26_BranchConfiguration(this);
		}
	}
}
