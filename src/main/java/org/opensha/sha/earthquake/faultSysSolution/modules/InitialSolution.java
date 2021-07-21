package org.opensha.sha.earthquake.faultSysSolution.modules;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;

import com.google.common.base.Preconditions;

public class InitialSolution implements CSV_BackedModule {
	
	private double[] initialSolution;

	@SuppressWarnings("unused") // used in deserialization
	private InitialSolution() {}
	
	public InitialSolution(double[] initialSolution) {
		this.initialSolution = initialSolution;
	}
	
	public double[] get() {
		return initialSolution;
	}

	@Override
	public String getFileName() {
		return "initial_solution.csv";
	}

	@Override
	public String getName() {
		return "Initial (pre-inversion) Rates";
	}

	@Override
	public CSVFile<String> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Rupture Index", "Water Level Rate");
		for (int r=0; r<initialSolution.length; r++)
			csv.addLine(r+"", initialSolution[r]+"");
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		double[] vals = new double[csv.getNumRows()-1];
		for (int row=1; row<csv.getNumRows(); row++) {
			int r = row-1;
			Preconditions.checkState(r == csv.getInt(row, 0),
					"Rupture indexes must be 0-based and in order. Expected %s at row %s", r, row);
			vals[r] = csv.getDouble(row, 1);
		}
		this.initialSolution = vals;
	}

}
