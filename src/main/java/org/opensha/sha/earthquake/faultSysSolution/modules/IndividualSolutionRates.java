package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

public class IndividualSolutionRates implements CSV_BackedModule, SubModule<FaultSystemSolution> {
	
	private FaultSystemSolution sol;
	private double[] averageRates;
	private List<double[]> individualRates;
	
	@SuppressWarnings("unused") // for serialization
	private IndividualSolutionRates() {
		
	}
	
	public IndividualSolutionRates(FaultSystemSolution sol, List<double[]> individualRates) {
		this(sol, averageRates(individualRates), individualRates);
	}
	
	private IndividualSolutionRates(FaultSystemSolution sol, double[] averageRates, List<double[]> individualRates) {
		this.sol = sol;
		init(averageRates, individualRates);
	}

	@Override
	public String getFileName() {
		return "individual_rates.csv";
	}

	@Override
	public String getName() {
		return "Individual Solution Rates";
	}

	@Override
	public void setParent(FaultSystemSolution parent) throws IllegalStateException {
		if (this.sol != null && parent != null)
			Preconditions.checkState(sol.getRupSet().isEquivalentTo(parent.getRupSet()));
		if (parent != null && averageRates != null)
			checkRatesSame(parent.getRateForAllRups(), averageRates);
		this.sol = parent;
	}
	
	private void init(double[] averageRates, List<double[]> individualRates) {
		Preconditions.checkNotNull(this.sol, "Solution not yet set?");
		checkRatesSame(sol.getRateForAllRups(), averageRates);
		this.averageRates = averageRates;
		this.individualRates = individualRates;
	}
	
	private static double[] averageRates(List<double[]> individualRates) {
		double[] ret = new double[individualRates.get(0).length];
		double scalar = 1d/individualRates.size();
		for (int i=0; i<individualRates.size(); i++)
			Preconditions.checkState(individualRates.get(i).length == ret.length);
		for (int r=0; r<ret.length; r++) {
			for (double[] indv : individualRates)
				ret[r] += indv[r];
			ret[r] *= scalar;
		}
		return ret;
	}
	
	private static void checkRatesSame(double[] solRates, double[] myAvgRates) {
		Preconditions.checkState(solRates.length == myAvgRates.length);
		for (int r=0; r<solRates.length; r++)
			Preconditions.checkState((float)solRates[r] == (float)myAvgRates[r],
					"Average rate for rupture %s does not match that from solution: %s != %s",
					r, myAvgRates[r], solRates[r]);
	}

	@Override
	public FaultSystemSolution getParent() {
		return sol;
	}

	@Override
	public SubModule<FaultSystemSolution> copy(FaultSystemSolution newParent) throws IllegalStateException {
		return new IndividualSolutionRates(newParent, averageRates, individualRates);
	}

	@Override
	public CSVFile<?> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		List<String> header = new ArrayList<>();
		header.add("Rupture Index");
		for (int i=0; i<individualRates.size(); i++)
			header.add("Solution "+(i+1));
		csv.addLine(header);
		
		int numRups = sol.getRupSet().getNumRuptures();
		for (int r=0; r<numRups; r++) {
			List<String> line = new ArrayList<>(csv.getNumCols());
			line.add(r+"");
			for (double[] rates : individualRates)
				line.add(rates[r]+"");
			csv.addLine(line);
		}
		
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		Preconditions.checkNotNull(sol, "Solution not set but initFromCSV called");
		int numRups = sol.getRupSet().getNumRuptures();
		Preconditions.checkState(csv.getNumRows() == numRups+1,
				"Expected numRups+1 rows in CSV, have %s with numRups=%s", csv.getNumRows(), numRups);
		int numSols = csv.getLine(0).size()-1;
		List<double[]> individualRates = new ArrayList<>();
		for (int s=0; s<numSols; s++)
			individualRates.add(new double[numRups]);
		for (int r=0; r<numRups; r++) {
			int row = r+1;
			Preconditions.checkState(csv.getInt(row, 0) == r, "CSV out of order or not 0-based");
			for (int s=0; s<numSols; s++)
				individualRates.get(s)[r] = csv.getDouble(row, s+1);
		}
		init(averageRates(individualRates), individualRates);
	}

}
