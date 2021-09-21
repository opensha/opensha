package org.opensha.sha.earthquake.faultSysSolution.modules;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;

public abstract class SectSlipRates implements SubModule<FaultSystemRupSet> {
	
	protected FaultSystemRupSet parent;

	private SectSlipRates(FaultSystemRupSet parent) {
		this.parent = parent;
	}
	
	public static SectSlipRates precomputed(FaultSystemRupSet rupSet, double[] slipRates, double[] slipRateStdDevs) {
		return new Precomputed(rupSet, slipRates, slipRateStdDevs);
	}
	
	public static SectSlipRates fromFaultSectData(FaultSystemRupSet rupSet) {
		return new Default(rupSet);
	}
	
	/**
	 * This returns the section slip rate of the given section. It can differ from what is returned by
	 * rupSet.getFaultSectionData(index).get*AveSlipRate() if there are any reductions for creep or subseismogenic ruptures.
	 * 
	 * @param sectIndex section index
	 * @return slip rate (SI units: m/yr)
	 */
	public abstract double getSlipRate(int sectIndex);
	
	/**
	 * This returns the section slip rate of all sections. It can differ from what is returned by
	 * rupSet.getFaultSectionData(index).get*AveSlipRate() if there are any reductions for creep or subseismogenic ruptures.
	 * 
	 * @return slip rates array (SI units: m/yr)
	 */
	public double[] getSlipRates() {
		FaultSystemRupSet rupSet = getParent();
		Preconditions.checkNotNull(rupSet, "parent rupture set not set");
		double[] ret = new double[rupSet.getNumSections()];
		for (int i=0; i<ret.length; i++)
			ret[i] = getSlipRate(i);
		return ret;
	}
	
	/**
	 * This returns the standard deviation of the slip rate for the given section. It can differ from what is returned by
	 * rupSet.getFaultSectionData(index).getSlipRateStdDev() if there are any reductions for creep or subseismogenic ruptures.
	 * 
	 * @param sectIndex section index
	 * @return slip rate standard deviation (SI units: m)
	 */
	public abstract double getSlipRateStdDev(int sectIndex);
	
	/**
	 * This returns the standard deviation of the slip rate of all sections. It can differ from what is returned by
	 * rupSet.getFaultSectionData(index).getSlipRateStdDev() if there are any reductions for creep or subseismogenic ruptures.
	 * 
	 * @return slip rates array (SI units: m)
	 */
	public double[] getSlipRateStdDevs() {
		FaultSystemRupSet rupSet = getParent();
		Preconditions.checkNotNull(rupSet, "parent rupture set not set");
		double[] ret = new double[rupSet.getNumSections()];
		for (int i=0; i<ret.length; i++)
			ret[i] = getSlipRateStdDev(i);
		return ret;
	}
	
	/**
	 * Computes the total moment rate (N-m/yr) for the fault system using the slip rates and creep-reduced fault section
	 * areas
	 * 
	 * @return moment rate in N-m/yr
	 */
	public double calcTotalMomentRate() {
		FaultSystemRupSet rupSet = getParent();
		Preconditions.checkNotNull(rupSet, "parent rupture set not set");
		double totMomentRate = 0d;
		for (int s=0; s<rupSet.getNumSections(); s++)
			totMomentRate += FaultMomentCalc.getMoment(rupSet.getAreaForSection(s), getSlipRate(s));
		return totMomentRate;
	}

	@Override
	public String getName() {
		return "Section Slip Rates";
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		this.parent = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return parent;
	}
	
	private static class Default extends SectSlipRates implements SubModule<FaultSystemRupSet> {
		
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
		public double getSlipRate(int sectIndex) {
			return parent.getFaultSectionData(sectIndex).getReducedAveSlipRate()*1e-3; // mm/yr => m/yr
		}

		@Override
		public double getSlipRateStdDev(int sectIndex) {
			return parent.getFaultSectionData(sectIndex).getReducedSlipRateStdDev()*1e-3; // mm/yr => m/yr
		}
		
	}
	
	public static final String DATA_FILE_NAME = "sect_slip_rates.csv";
	
	public static class Precomputed extends SectSlipRates implements CSV_BackedModule {
		
		private double[] slipRates;
		private double[] slipRateStdDevs;

		// required for serialization
		private Precomputed() {
			super(null);
		}
		
		private Precomputed(FaultSystemRupSet rupSet, double[] slipRates, double[] slipRateStdDevs) {
			super(rupSet);
			int numSects = rupSet.getNumSections();
			Preconditions.checkState(slipRates == null || slipRates.length == numSects,
					"Num slip rates != num sections");
			Preconditions.checkState(slipRateStdDevs == null || slipRateStdDevs.length == numSects,
					"Num slip rates std devs != num sections");
			this.slipRates = slipRates;
			this.slipRateStdDevs = slipRateStdDevs;
		}

		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
			Preconditions.checkNotNull(newParent);
			if (slipRates != null)
				Preconditions.checkState(newParent.getNumSections() == slipRates.length);
			if (slipRateStdDevs != null)
				Preconditions.checkState(newParent.getNumSections() == slipRateStdDevs.length);
			return new Precomputed(newParent, slipRates, slipRateStdDevs);
		}

		@Override
		public double getSlipRate(int sectIndex) {
			return slipRates == null ? Double.NaN : slipRates[sectIndex];
		}

		@Override
		public double[] getSlipRates() {
			return slipRates;
		}

		@Override
		public double[] getSlipRateStdDevs() {
			return slipRateStdDevs;
		}

		@Override
		public double getSlipRateStdDev(int sectIndex) {
			return slipRateStdDevs == null ? Double.NaN : slipRateStdDevs[sectIndex];
		}

		@Override
		public String getFileName() {
			return DATA_FILE_NAME;
		}

		@Override
		public CSVFile<?> getCSV() {
			CSVFile<String> csv = new CSVFile<>(true);
			csv.addLine("Section Index", "Slip Rate (m/yr)", "Slip Rate Standard Deviation (m/yr)");
			int numSections = getParent().getNumSections();
			for (int s=0; s<numSections; s++)
				csv.addLine(s+"", getSlipRate(s)+"", getSlipRateStdDev(s)+"");
			return csv;
		}

		@Override
		public void initFromCSV(CSVFile<String> csv) {
			int numSects = getParent().getNumSections();
			Preconditions.checkState(csv.getNumRows() == numSects+1,
					"Expected 1 header row and %s section rows, have %s", numSects, csv.getNumRows());
			
			double[] slipRates = new double[numSects];
			double[] slipRateStdDevs = new double[numSects];
			
			for (int s=0; s<numSects; s++) {
				int row = s+1;
				Preconditions.checkState(csv.getInt(row, 0) == s, "Rows out of order or not 0-based");
				slipRates[s] = csv.getDouble(row, 1);
				slipRateStdDevs[s] = csv.getDouble(row, 2);
			}
			
			this.slipRates = slipRates;
			this.slipRateStdDevs = slipRateStdDevs;
		}

	}

}
