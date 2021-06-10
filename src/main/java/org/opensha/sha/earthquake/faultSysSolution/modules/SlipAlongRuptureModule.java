package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.gson.GsonBuilder;

import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

public abstract class SlipAlongRuptureModule implements SubModule<FaultSystemRupSet> {
	
	FaultSystemRupSet rupSet;

	protected SlipAlongRuptureModule(FaultSystemRupSet rupSet) {
		super();
		this.rupSet = rupSet;
	}

	public static SlipAlongRuptureModule forModel(FaultSystemRupSet rupSet, SlipAlongRuptureModels slipAlong) {
		return new ModelBased(rupSet, slipAlong);
	}
	
	/**
	 * This gives the slip (SI untis: m) on each section for the rth rupture
	 * @return
	 */
	public abstract double[] getSlipOnSectionsForRup(int rthRup);

	public static class ModelBased extends SlipAlongRuptureModule implements JSON_TypeAdapterBackedModule<SlipAlongRuptureModels> {
		
		protected ConcurrentMap<Integer, double[]> rupSectionSlipsCache = Maps.newConcurrentMap();

		private SlipAlongRuptureModels slipAlong;
		private AveSlipModule aveSlip;
		
		private ModelBased() {
			super(null);
		}

		protected ModelBased(FaultSystemRupSet rupSet, SlipAlongRuptureModels slipAlong) {
			super(rupSet);
			Preconditions.checkNotNull(rupSet, "Must supply rupture set");
			Preconditions.checkNotNull(slipAlong, "Must supply slip along rupture model");
			this.slipAlong = slipAlong;
		}
		
		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
			Preconditions.checkState(rupSet.getNumRuptures() == newParent.getNumRuptures());
			Preconditions.checkState(newParent.hasModule(AveSlipModule.class));
			
			return new ModelBased(newParent, slipAlong);
		}

		@Override
		public String getFileName() {
			return "slip_along_rupture_model.json";
		}

		@Override
		public Type getType() {
			return SlipAlongRuptureModels.class;
		}

		@Override
		public SlipAlongRuptureModels get() {
			return slipAlong;
		}

		@Override
		public void set(SlipAlongRuptureModels value) {
			this.slipAlong = value;
		}

		@Override
		public void registerTypeAdapters(GsonBuilder builder) {
			// do nothing
		}

		@Override
		public double[] getSlipOnSectionsForRup(int rthRup) {
			double[] slips = rupSectionSlipsCache.get(rthRup);
			if (slips == null) {
				synchronized (rupSectionSlipsCache) {
					slips = rupSectionSlipsCache.get(rthRup);
					if (slips != null)
						return slips;
					slips = calcSlipOnSectionsForRup(rthRup);
					rupSectionSlipsCache.putIfAbsent(rthRup, slips);
				}
			}
			return slips;
		}
		
		private AveSlipModule getSlipModule() {
			if (aveSlip == null) {
				synchronized (this) {
					if (aveSlip == null) {
						Preconditions.checkNotNull(rupSet, "Rupture set not initialized");
						AveSlipModule aveSlip = rupSet.getModule(AveSlipModule.class);
						Preconditions.checkNotNull(rupSet, "Rupture set doesn't have the average slip module");
						this.aveSlip = aveSlip;
					}
				}
			}
			return aveSlip;
		}

		private double[] calcSlipOnSectionsForRup(int rthRup) {
			Preconditions.checkNotNull(slipAlong);

			List<Integer> sectionIndices = rupSet.getSectionsIndicesForRup(rthRup);
			int numSects = sectionIndices.size();

			// compute rupture area
			double[] sectArea = new double[numSects];
			double[] sectMoRate = new double[numSects];
			int index=0;
			for(Integer sectID: sectionIndices) {	
				//				FaultSectionPrefData sectData = getFaultSectionData(sectID);
				//				sectArea[index] = sectData.getTraceLength()*sectData.getReducedDownDipWidth()*1e6;	// aseismicity reduces area; 1e6 for sq-km --> sq-m
				sectArea[index] = rupSet.getAreaForSection(sectID);
				sectMoRate[index] = FaultMomentCalc.getMoment(sectArea[index], rupSet.getSlipRateForSection(sectID));
				index += 1;
			}

			double aveSlip = getSlipModule().getAveSlip(rthRup); // in meters
			
			return SlipAlongRuptureModels.calcSlipOnSectionsForRup(rupSet, rthRup, slipAlong, sectArea, sectMoRate, aveSlip);
		}

	}

	@Override
	public String getName() {
		return "Slip Along Rupture";
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		if (this.rupSet != null)
			Preconditions.checkState(rupSet.getNumRuptures() == parent.getNumRuptures());
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}

}
