package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

public class IndividualSolutionRates implements SubModule<FaultSystemSolution>, CSV_BackedModule {
	
	private FaultSystemSolution sol;
	private List<double[]> ratesList;
	
	@SuppressWarnings("unused") // for serialization
	private IndividualSolutionRates() {
		
	}

	public IndividualSolutionRates(FaultSystemSolution sol, List<double[]> ratesList) {
		init(sol, ratesList);
	}
	
	private void init(FaultSystemSolution sol, List<double[]> ratesList) {
		validate(sol, ratesList);
		this.sol = sol;
		this.ratesList = ratesList;
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
		if (this.sol != null)
			Preconditions.checkState(this.sol.getRupSet().isEquivalentTo(parent.getRupSet()));
		validate(parent, ratesList);
	}
	
	private static void validate(FaultSystemSolution sol, List<double[]> ratesList) {
		int numRups = sol.getRupSet().getNumRuptures();
		double origSum = sol.getTotalRateForAllFaultSystemRups();
		double indvSum = 0d;
		for (double[] rates : ratesList) {
			Preconditions.checkState(rates.length == numRups);
			indvSum += StatUtils.sum(rates);
		}
		indvSum /= ratesList.size();
		Preconditions.checkState((float)origSum == (float)indvSum,
				"Sum of averaged rates doesn't match that from supplied solution: %s != %s",
				(float)origSum, (float)indvSum);
	}

	@Override
	public FaultSystemSolution getParent() {
		return sol;
	}

	@Override
	public SubModule<FaultSystemSolution> copy(FaultSystemSolution newParent) throws IllegalStateException {
		if (this.sol != null)
			Preconditions.checkState(this.sol.getRupSet().isEquivalentTo(newParent.getRupSet()));
		return new IndividualSolutionRates(newParent, ratesList);
	}

	@Override
	public CSVFile<?> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		List<String> header = new ArrayList<>();
		header.add("Rupture Index");
		for (int i=0; i<ratesList.size(); i++)
			header.add("Solution "+(i+1));
		csv.addLine(header);
		
		int numRups = sol.getRupSet().getNumRuptures();
		for (int r=0; r<numRups; r++) {
			List<String> line = new ArrayList<>(csv.getNumCols());
			line.add(r+"");
			for (double[] rates : ratesList)
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
		init(sol, individualRates);
	}

}
