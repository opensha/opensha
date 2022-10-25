package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.uncertainty.Uncertainty;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_PaleoUncertainties implements LogicTreeNode {
	EVEN_FIT("Even-Fit Paleo Data", "EvenFit", 1d, 1d),
	OVER_FIT("Over-Fit Paleo Data (5x)", "OverFit", 1d/5d, 1d),
	UNDER_FIT("Under-Fit Paleo Data (10x)", "UnderFit", 10d, 1d);

	private String name;
	private String shortName;
	private double scalar;
	private double weight;

	private NSHM23_PaleoUncertainties(String name, String shortName, double scalar, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.scalar = scalar;
		this.weight = weight;
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
		return getShortName()+"Paleo";
	}
	
	public List<SectMappedUncertainDataConstraint> getScaled(List<? extends SectMappedUncertainDataConstraint> constraints) {
		return getScaled(scalar, constraints);
	}
	
	public static List<SectMappedUncertainDataConstraint> getScaled(double uncertScalar,
			List<? extends SectMappedUncertainDataConstraint> constraints) {
		List<SectMappedUncertainDataConstraint> ret = new ArrayList<>();
		for (SectMappedUncertainDataConstraint constraint : constraints)
			ret.add(getScaled(uncertScalar, constraint));
		return ret;
	}
	
	public SectMappedUncertainDataConstraint getScaled(SectMappedUncertainDataConstraint constraint) {
		return getScaled(scalar, constraint);
	}
	
	public static SectMappedUncertainDataConstraint getScaled(double uncertScalar, SectMappedUncertainDataConstraint constraint) {
		if (uncertScalar == 1d)
			return constraint;
		Uncertainty[] modUncerts = new Uncertainty[constraint.uncertainties.length];
		for (int i=0; i<modUncerts.length; i++)
			modUncerts[i] = constraint.uncertainties[i].scaled(constraint.bestEstimate, uncertScalar);
		SectMappedUncertainDataConstraint ret = new SectMappedUncertainDataConstraint(
				constraint.name, constraint.sectionIndex, constraint.sectionName,
				constraint.dataLocation, constraint.bestEstimate, modUncerts);
		return ret;
	}
	
	public double getUncertaintyScalar() {
		return scalar;
	}

}
