package org.opensha.sha.earthquake.faultSysSolution.modules.impl;

import java.io.IOException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.StatefulModule;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public abstract class AveSlipModule extends RupSetModule {
	
	protected AveSlipModule(FaultSystemRupSet rupSet) {
		super(rupSet);
	}
	
	public static AveSlipModule forModel(FaultSystemRupSet rupSet, ScalingRelationships scale) {
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
	
	public static class ModelBased extends AveSlipModule implements StatefulModule {

		private ScalingRelationships scale;

		protected ModelBased(FaultSystemRupSet rupSet, ScalingRelationships scale) {
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
		public Class<? extends StatefulModule> getLoadingClass() {
			return Precomputed.class;
		}

		@Override
		public double getAveSlip(int rupIndex) {
			FaultSystemRupSet rupSet = getRupSet();
			double totOrigArea = 0d;
			for (FaultSection sect : rupSet.getFaultSectionDataForRupture(rupIndex))
				totOrigArea += sect.getTraceLength()*1e3*sect.getOrigDownDipWidth()*1e3;
			double origDDW = totOrigArea/rupSet.getLengthForRup(rupIndex);
			double aveSlip = scale.getAveSlip(rupSet.getAreaForRup(rupIndex),
					rupSet.getLengthForRup(rupIndex), origDDW);
			return aveSlip;
		}
		
	}
	
	public static class Precomputed extends AveSlipModule implements CSV_BackedModule {
		
		private double[] aveSlips;

		public Precomputed() {
			super(null);
		}

		public Precomputed(AveSlipModule module) {
			super(module.getRupSet());
			aveSlips = new double[module.getRupSet().getNumRuptures()];
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
		public String getCSV_FileName() {
			return "ave_slips.csv";
		}

		@Override
		public CSVFile<?> getCSV() {
			CSVFile<String> csv = new CSVFile<>(true);
			csv.addLine("Rupture Index", "Average Slip (m)");
			int numRups = getRupSet().getNumRuptures();
			for (int r=0; r<numRups; r++)
				csv.addLine(r+"", getAveSlip(r)+"");
			return csv;
		}

		@Override
		public void initFromCSV(CSVFile<String> csv) {
			int numRups = getRupSet().getNumRuptures();
			Preconditions.checkState(csv.getNumRows() == numRups+1,
					"Expected 1 header row and %s rupture rows, have %s", numRups, csv.getNumRows());
			
			double[] aveSlips = new double[numRups];
			for (int r=0; r<numRups; r++)
				aveSlips[r] = csv.getDouble(r+1, 1);
			this.aveSlips = aveSlips;
		}
		
	}

	@Override
	public String getName() {
		return "Rupture Average Slips";
	}

}
