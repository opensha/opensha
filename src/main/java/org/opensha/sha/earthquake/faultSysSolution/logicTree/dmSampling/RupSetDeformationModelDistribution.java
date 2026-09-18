package org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

/**
 * Abstract class and level structures for a {@link RupSetDeformationModel} where slip rates are sampled from
 * {@link ContinuousDistribution}s according to a {@link SectDistributionSampler}.
 * @param <S>
 */
public abstract class RupSetDeformationModelDistribution<S extends SectDistributionSampler>
implements RupSetDeformationModel, ValuedLogicTreeNode<S> {
	
	private S sampler;
	private double weight;
	private String name;
	private String shortName;
	private String filePrefix;
	private Class<? extends S> valueClass;

	protected RupSetDeformationModelDistribution() {}

	public RupSetDeformationModelDistribution(String name, String shortName, String prefix, double weight, S sampler) {
		Preconditions.checkNotNull(sampler, "Sampler cannot be null");
		init(sampler, (Class<? extends S>)sampler.getClass(), weight, name, shortName, prefix);
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
	public double getNodeWeight() {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return filePrefix;
	}
	
	@Override
	public String toString() {
		return shortName;
	}

	@Override
	public S getValue() {
		return sampler;
	}

	@Override
	public Class<? extends S> getValueType() {
		return valueClass;
	}

	@Override
	public void init(S sampler, Class<? extends S> valueClass, double weight, String name, String shortName,
			String filePrefix) {
		this.sampler = sampler;
		this.valueClass = valueClass;
		this.weight = weight;
		this.name = name;
		this.shortName = shortName;
		this.filePrefix = filePrefix;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, filePrefix, sampler, shortName, weight);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RupSetDeformationModelDistribution other = (RupSetDeformationModelDistribution) obj;
		return Objects.equals(name, other.name) && Objects.equals(filePrefix, other.filePrefix) && Objects.equals(sampler, other.sampler)
				&& Objects.equals(shortName, other.shortName)
				&& Double.doubleToLongBits(weight) == Double.doubleToLongBits(other.weight);
	}
	
	public static abstract class Simple extends RupSetDeformationModelDistribution<SectDistributionSampler> {
		
		protected Simple() {}

		public Simple(String name, String shortName, String prefix, double weight, SectDistributionSampler sampler) {
			super(name, shortName, prefix, weight, sampler);
		}
		
		public abstract void initDistributions(LogicTreeBranch<? extends LogicTreeNode> branch,
				List<? extends FaultSection> fullSects, List<? extends FaultSection> subSects) throws IOException;
		
		public abstract ContinuousDistribution getSlipRateDistribution(FaultSection subSect);
		
		public abstract UnaryOperator<List<? extends FaultSection>> getPostProcessor();

		@Override
		public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
				LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> fullSects,
				List<? extends FaultSection> subSects) throws IOException {
			return apply(branch, fullSects, subSects, getValue());
		}
		
		public List<? extends FaultSection> apply(LogicTreeBranch<? extends LogicTreeNode> branch,
				List<? extends FaultSection> fullSects, List<? extends FaultSection> subSects,
				SectDistributionSampler sampler) throws IOException {
			initDistributions(branch, fullSects, subSects);
			sampler.init(subSects);
			for (FaultSection subSect : subSects) {
				ContinuousDistribution dist = getSlipRateDistribution(subSect);
				Preconditions.checkNotNull(dist, "No distribution found for sect %s with parentID=%s",
						subSect.getSectionName(), subSect.getParentSectionId());
				double slipRate = sampler.getValue(subSect, dist);
				subSect.setAveSlipRate(slipRate);
			}
			UnaryOperator<List<? extends FaultSection>> postProcess = getPostProcessor();
			if (postProcess != null)
				subSects = postProcess.apply(subSects);
			return subSects;
		}
		
	}
}
