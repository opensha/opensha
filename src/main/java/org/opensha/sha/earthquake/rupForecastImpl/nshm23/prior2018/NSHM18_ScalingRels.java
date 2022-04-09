package org.opensha.sha.earthquake.rupForecastImpl.nshm23.prior2018;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM18_ScalingRels implements LogicTreeNode, RupSetScalingRelationship {
	WC94_ML("Wells & Coppersmith (1994) M-L", "WC94 W-L", 1d) {
		@Override
		public double getAveSlip(double area, double length, double origWidth, double aveRake) {
			double mag;
			synchronized (wc94) {
				mag = wc94.getMedianMag(length*1e-3); // convert to km
			}
			double moment = MagUtils.magToMoment(mag);
			return FaultMomentCalc.getSlip(area, moment);
//			return ScalingRelationships.ELLSWORTH_B.getAveSlip(area, length, origWidth, aveRake);
		}

		@Override
		public double getMag(double area, double origWidth, double aveRake) {
			double len = area/origWidth;
			synchronized (wc94) {
				return wc94.getMedianMag(len*1e-3); // convert to km
			}
//			return ScalingRelationships.ELLSWORTH_B.getMag(area, origWidth, aveRake);
		}
	};
	
	private static WC1994_MagLengthRelationship wc94 = new WC1994_MagLengthRelationship();
	
	private String name;
	private String shortName;
	private double weight;

	NSHM18_ScalingRels(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
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
		return name();
	}

	@Override
	public abstract double getAveSlip(double area, double length, double origWidth, double aveRake);

	@Override
	public abstract double getMag(double area, double origWidth, double aveRake);

}
