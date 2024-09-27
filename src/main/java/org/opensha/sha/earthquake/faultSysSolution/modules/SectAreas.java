package org.opensha.sha.earthquake.faultSysSolution.modules;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.AverageableModule.ConstantAverageable;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;

public abstract class SectAreas implements SubModule<FaultSystemRupSet>, SplittableRuptureModule<SectAreas> {
	
	protected FaultSystemRupSet parent;

	private SectAreas(FaultSystemRupSet parent) {
		this.parent = parent;
	}
	
	public static SectAreas precomputed(FaultSystemRupSet rupSet, double[] sectAreas) {
		return new Precomputed(rupSet, sectAreas);
	}
	
	public static SectAreas fromFaultSectData(FaultSystemRupSet rupSet) {
		return new Default(rupSet);
	}
	
	/**
	 * 
	 * @param sectIndex section index
	 * @return section area (SI units: m)
	 */
	public abstract double getSectArea(int sectIndex);
	
	/**
	 * 
	 * @return section areas array (SI units: m)
	 */
	public double[] getSectAreas() {
		FaultSystemRupSet rupSet = getParent();
		Preconditions.checkNotNull(rupSet, "parent rupture set not set");
		double[] ret = new double[rupSet.getNumSections()];
		for (int i=0; i<ret.length; i++)
			ret[i] = getSectArea(i);
		return ret;
	}

	@Override
	public String getName() {
		return "Section Areas";
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		this.parent = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return parent;
	}
	
	public static class Default extends SectAreas implements SubModule<FaultSystemRupSet> {
		
		private double[] data = null;
		
		private Default(FaultSystemRupSet rupSet) {
			super(rupSet);
		}

		@Override
		public Default copy(FaultSystemRupSet newParent) throws IllegalStateException {
			Preconditions.checkNotNull(newParent);
			return new Default(newParent);
		}

		@Override
		public double getSectArea(int sectIndex) {
			if (data == null) {
				synchronized (this) {
					if (data == null) {
						double[] data = new double[parent.getNumSections()];
						for (int s=0; s<data.length; s++)
							data[s] = parent.getFaultSectionData(s).getArea(true);
						this.data = data;
					}
				}
			}
			return data[sectIndex];
		}

		@Override
		public SectAreas getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
			return copy(rupSubSet);
		}

		@Override
		public SectAreas getForSplitRuptureSet(FaultSystemRupSet splitRupSet, RuptureSetSplitMappings mappings) {
			return copy(splitRupSet);
		}
		
	}
	
	public static final String DATA_FILE_NAME = "sect_areas.csv";
	
	public static class Precomputed extends SectAreas implements CSV_BackedModule, ConstantAverageable<Precomputed> {
		
		private double[] sectAreas;

		// required for serialization
		private Precomputed() {
			super(null);
		}
		
		private Precomputed(FaultSystemRupSet rupSet, double[] sectAreas) {
			super(rupSet);
			int numSects = rupSet.getNumSections();
			Preconditions.checkState(sectAreas.length == numSects,
					"Num areas != num sections");
			this.sectAreas = sectAreas;
		}

		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
			Preconditions.checkNotNull(newParent);
			Preconditions.checkState(newParent.getNumSections() == sectAreas.length);
			return new Precomputed(newParent, sectAreas);
		}

		@Override
		public double getSectArea(int sectIndex) {
			return sectAreas[sectIndex];
		}

		@Override
		public double[] getSectAreas() {
			return sectAreas;
		}

		@Override
		public String getFileName() {
			return DATA_FILE_NAME;
		}

		@Override
		public CSVFile<?> getCSV() {
			CSVFile<String> csv = new CSVFile<>(true);
			csv.addLine("Section Index", "Section Area (m^2)");
			int numSections = getParent().getNumSections();
			for (int s=0; s<numSections; s++)
				csv.addLine(s+"", getSectArea(s)+"");
			return csv;
		}

		@Override
		public void initFromCSV(CSVFile<String> csv) {
			int numSects = getParent().getNumSections();
			Preconditions.checkState(csv.getNumRows() == numSects+1,
					"Expected 1 header row and %s section rows, have %s", numSects, csv.getNumRows());
			
			double[] sectAreas = new double[numSects];
			
			for (int s=0; s<numSects; s++) {
				int row = s+1;
				Preconditions.checkState(csv.getInt(row, 0) == s, "Rows out of order or not 0-based");
				sectAreas[s] = csv.getDouble(row, 1);
			}
			
			this.sectAreas = sectAreas;
		}

		@Override
		public Class<Precomputed> getAveragingType() {
			return Precomputed.class;
		}

		@Override
		public boolean isIdentical(Precomputed module) {
			if (sectAreas.length != module.sectAreas.length)
				return false;
			for (int s=0; s<sectAreas.length; s++) {
				if ((float)sectAreas[s] != (float)module.sectAreas[s])
					return false;
			}
			return true;
		}

		@Override
		public Precomputed getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
			double[] filtered = new double[rupSubSet.getNumSections()];
			for (int s=0; s<rupSubSet.getNumSections(); s++)
				filtered[s] = sectAreas[mappings.getOrigSectID(s)];
			return new Precomputed(rupSubSet, filtered);
		}

		@Override
		public SectAreas getForSplitRuptureSet(FaultSystemRupSet splitRupSet, RuptureSetSplitMappings mappings) {
			double[] filtered = new double[splitRupSet.getNumSections()];
			for (int s=0; s<splitRupSet.getNumSections(); s++)
				filtered[s] = sectAreas[mappings.getOrigSectID(s)];
			return new Precomputed(splitRupSet, filtered);
		}

	}

}
