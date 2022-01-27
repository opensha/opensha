package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.List;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.*;
import org.opensha.sha.faultSurface.FaultSection;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum RupturePlausibilityModels implements LogicTreeNode {
	UCERF3("UCERF3", "U3", 1d) { // for now
		@Override
		public RupSetConfig getConfig(List<? extends FaultSection> subSects, RupSetScalingRelationship scale) {
			return new U3RupSetConfig(subSects, scale);
		}
	},
	UCERF3_REDUCED("UCERF3 Reduced", "U3Red", 1d) { // for now
		@Override
		public RupSetConfig getConfig(List<? extends FaultSection> subSects, RupSetScalingRelationship scale) {
			U3RupSetConfig config = new U3RupSetConfig(subSects, scale);
			config.setAdaptiveSectFract(0.1f);
			return config;
		}
	},
	COULOMB("Coulomb", "Coulomb", 1d) {
		@Override
		public RupSetConfig getConfig(List<? extends FaultSection> subSects, RupSetScalingRelationship scale) {
			return new CoulombRupSetConfig(subSects, null, scale);
		}
	};
	
	private String name;
	private String shortName;
	private double weight;

	private RupturePlausibilityModels(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}
	
	public abstract RupSetConfig getConfig(List<? extends FaultSection> subSects, RupSetScalingRelationship scale);

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
		return shortName+"RupSet";
	}

}
