package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public abstract class AveSlipModule implements SubModule<FaultSystemRupSet>, BranchAverageableModule<AveSlipModule> {
	
	FaultSystemRupSet rupSet;

	protected AveSlipModule(FaultSystemRupSet rupSet) {
		super();
		this.rupSet = rupSet;
	}

	public static AveSlipModule forModel(FaultSystemRupSet rupSet, RupSetScalingRelationship scale) {
		return new ModelBased(rupSet, scale);
	}

	public static AveSlipModule precomputed(FaultSystemRupSet rupSet, double[] aveSlips) {
		return new Precomputed(rupSet, aveSlips);
	}

	/**
	 * Returns average slip for the given rupture in SI units (m)
	 * @param rupIndex
	 * @return rupture average slip
	 */
	public abstract double getAveSlip(int rupIndex);

	public static class ModelBased extends AveSlipModule implements ArchivableModule {

		private RupSetScalingRelationship scale;
		private transient double[] cache;

		protected ModelBased(FaultSystemRupSet rupSet, RupSetScalingRelationship scale) {
			super(rupSet);
			this.scale = scale;
		}

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			new Precomputed(this).writeToArchive(zout, entryPrefix);
		}

		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			throw new IllegalStateException("Only pre-computed average slip modules can be loaded");
		}

		@Override
		public Class<? extends ArchivableModule> getLoadingClass() {
			return Precomputed.class;
		}

		@Override
		public double getAveSlip(int rupIndex) {
			if (cache == null) {
				synchronized (this) {
					if (cache == null) {

						Preconditions.checkNotNull(rupSet, "Parent rupture set not set");
						cache = new double[rupSet.getNumRuptures()];
					}
				}
			}
			if (cache[rupIndex] != 0d)
				return cache[rupIndex];
			Preconditions.checkNotNull(rupSet, "Parent rupture set not set");
			double totArea = rupSet.getAreaForRup(rupIndex);
			double length = rupSet.getLengthForRup(rupIndex);
			double totOrigArea = 0d;
			for (FaultSection sect : rupSet.getFaultSectionDataForRupture(rupIndex))
				totOrigArea += sect.getArea(false);
			double origDDW = totOrigArea/rupSet.getLengthForRup(rupIndex);
			double aveRake = rupSet.getAveRakeForRup(rupIndex);
			double aveSlip = scale.getAveSlip(totArea, length, origDDW, aveRake);
			cache[rupIndex] = aveSlip;
			return aveSlip;
		}
		
		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
			Preconditions.checkState(rupSet.getNumRuptures() == newParent.getNumRuptures());
			
			return new ModelBased(newParent, scale);
		}

		@Override
		public String getName() {
			return scale.getShortName()+" Average Slips";
		}

		@Override
		public AveragingAccumulator<AveSlipModule> averagingAccumulator() {
			return new AveragingAccumulator<AveSlipModule>() {

				private RupSetScalingRelationship constScale = ModelBased.this.scale;
				private double sumWeight = 0d;
				private AveragingAccumulator<AveSlipModule> baAccumulator;
				
				@Override
				public void process(AveSlipModule module, double relWeight) {
					if (constScale != null) {
						// everything has been model based and with the same model so far
						RupSetScalingRelationship oScale =
								module instanceof ModelBased ? ((ModelBased)module).scale : null;
						if (oScale != constScale) {
							// we have encountered multiple modules, need to switch to branch averaged
							baAccumulator = new Precomputed(module).averagingAccumulator();
							if (sumWeight != 0d)
								// add in all prior weight already processed for the original scaling relationship
								baAccumulator.process(new ModelBased(rupSet, constScale), sumWeight);
							constScale = null;
						} // else we can do nothing
					} else {
						// we have mixed models or some precomputed versions, need average
						baAccumulator.process(module, relWeight);
					}
					sumWeight += relWeight;
				}
				
				@Override
				public Class<AveSlipModule> getType() {
					return AveSlipModule.class;
				}
				
				@Override
				public AveSlipModule getAverage() {
					if (constScale != null)
						// they're all constant
						return ModelBased.this;
					// they're precomputed
					return baAccumulator.getAverage();
				}
			};
		}

	}

	public static class Precomputed extends AveSlipModule implements CSV_BackedModule {

		private double[] aveSlips;

		private Precomputed() {
			super(null);
		}

		public Precomputed(AveSlipModule module) {
			super(module.rupSet);
			aveSlips = new double[module.rupSet.getNumRuptures()];
			for (int r=0; r<aveSlips.length; r++)
				aveSlips[r] = module.getAveSlip(r);
		}

		public Precomputed(FaultSystemRupSet rupSet, double[] aveSlips) {
			super(rupSet);
			this.aveSlips = aveSlips;
		}

		@Override
		public double getAveSlip(int rupIndex) {
			return aveSlips[rupIndex];
		}

		@Override
		public String getFileName() {
			return "average_slips.csv";
		}

		@Override
		public CSVFile<?> getCSV() {
			CSVFile<String> csv = new CSVFile<>(true);
			csv.addLine("Rupture Index", "Average Slip (m)");
			int numRups = aveSlips.length;
			for (int r=0; r<numRups; r++)
				csv.addLine(r+"", getAveSlip(r)+"");
			return csv;
		}

		@Override
		public void initFromCSV(CSVFile<String> csv) {
			int numRups = rupSet.getNumRuptures();
			Preconditions.checkState(csv.getNumRows() == numRups+1,
					"Expected 1 header row and %s rupture rows, have %s", numRups, csv.getNumRows());

			double[] aveSlips = new double[numRups];
			for (int r=0; r<numRups; r++) {
				Preconditions.checkState(csv.getInt(r+1, 0) == r, "Rows out of order or not 0-based");
				aveSlips[r] = csv.getDouble(r+1, 1);
			}
			this.aveSlips = aveSlips;
		}
		
		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
			Preconditions.checkState(rupSet.getNumRuptures() == newParent.getNumRuptures());
			
			return new Precomputed(newParent, aveSlips);
		}

		@Override
		public AveragingAccumulator<AveSlipModule> averagingAccumulator() {
			return new AveragingAccumulator<AveSlipModule>() {
				
				private boolean allSame;
				private AveSlipModule ref;
				private double[] refValues;
				private double[] values;
				
				private double sumWeight = 0d;
				
				@Override
				public void process(AveSlipModule module, double relWeight) {
					if (refValues == null) {
						allSame = true;
						ref = module;
						values = new double[module.rupSet.getNumRuptures()];
						refValues = new double[values.length];
						for (int r=0; r<values.length; r++) {
							refValues[r] = module.getAveSlip(r);
							values[r] = refValues[r]*relWeight;
						}
					} else {
						Preconditions.checkState(module.rupSet.getNumRuptures() == values.length);
						for (int r=0; r<values.length; r++) {
							double val = module.getAveSlip(r);
							values[r] += val*relWeight;
							allSame = allSame && val == refValues[r];
						}
						if (!allSame)
							ref = null;
					}
					sumWeight += relWeight;
				}
				
				@Override
				public AveSlipModule getAverage() {
					if (allSame)
						return ref;
					AverageableModule.scaleToTotalWeight(values, sumWeight);
					return new Precomputed(null, values);
				}

				@Override
				public Class<AveSlipModule> getType() {
					return AveSlipModule.class;
				}
			};
		}

		@Override
		public String getName() {
			return "Precomputed Average Slips";
		}

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
