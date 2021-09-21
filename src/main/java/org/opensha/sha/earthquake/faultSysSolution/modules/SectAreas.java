package org.opensha.sha.earthquake.faultSysSolution.modules;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;

public abstract class SectAreas implements SubModule<FaultSystemRupSet> {
	
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
	
	private static class Default extends SectAreas implements SubModule<FaultSystemRupSet> {
		
		private Default(FaultSystemRupSet rupSet) {
			super(rupSet);
		}

		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
			Preconditions.checkNotNull(newParent);
			Preconditions.checkState(parent == null || newParent.getNumSections() == parent.getNumSections());
			return new Default(newParent);
		}

		@Override
		public double getSectArea(int sectIndex) {
			return parent.getFaultSectionData(sectIndex).getArea(true);
		}
		
	}
	
	public static final String DATA_FILE_NAME = "sect_areas.csv";
	
	public static class Precomputed extends SectAreas implements CSV_BackedModule {
		
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

	}

}
