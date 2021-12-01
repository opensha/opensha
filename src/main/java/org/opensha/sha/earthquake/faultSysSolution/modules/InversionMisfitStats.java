package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;

import com.google.common.base.Preconditions;

/**
 * Summary statistics for inversion misfits
 * 
 * @author kevin
 *
 */
public class InversionMisfitStats implements CSV_BackedModule {
	
	public static class MisfitStats {
		public final ConstraintRange range;
		public final int numRows;
		public final double mean;
		public final double absMean;
		public final double median;
		public final double min;
		public final double max;
		public final double l2Norm;
		public final double energy;
		public final double std;
		
		public MisfitStats(double[] misfits, boolean inequality, double weight) {
			this(misfits, new ConstraintRange(inequality ? "Inequality" : "Equality",
					inequality ? "Ineq" : "Eq", 0, misfits.length, inequality, weight, null));
		}
		
		public MisfitStats(double[] misfits, ConstraintRange range) {
			this.range = range;
			this.numRows = misfits.length;
			Preconditions.checkState(numRows == range.endRow - range.startRow,
					"Misfits should already be trimmed to match constraint range rows");
			
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			double mean = 0d;
			double absMean = 0d;
			double l2Norm = 0d;
			double energy = 0d;
			
			StandardDeviation std = new StandardDeviation();
			for (double val : misfits) {
				if (range.inequality && val < 0d)
					val = 0d;
				mean += val;
				absMean += Math.abs(val);
				min = Math.min(val, min);
				max = Math.max(val, max);
				l2Norm += val*val;
				energy += (val*range.weight)*(val*range.weight);
				std.increment(val);
			}
			mean /= (double)numRows;
			absMean /= (double)numRows;
			
			this.mean = mean;
			this.absMean = absMean;
			this.median = DataUtils.median(misfits);
			this.min = min;
			this.max = max;
			this.l2Norm = l2Norm;
			this.energy = energy;
			this.std = std.getResult();
		}
		
		private MisfitStats(List<String> csvLine) {
			Preconditions.checkState(csvLine.size() == csvHeader.size());
			int index = 0;
			String name = csvLine.get(index++);
			String shortName = csvLine.get(index++);
			double weight = Double.parseDouble(csvLine.get(index++));
			String weightTypeName = csvLine.get(index++);
			ConstraintWeightingType weightType;
			if (weightTypeName == null || weightTypeName.isBlank() || weightTypeName.toLowerCase().trim().equals("null"))
				weightType = null;
			else
				weightType = ConstraintWeightingType.valueOf(weightTypeName);
			boolean inequality = Boolean.parseBoolean(csvLine.get(index++));
			this.numRows = Integer.parseInt(csvLine.get(index++));
			range = new ConstraintRange(name, shortName, 0, numRows, inequality, weight, weightType);
			this.mean = Double.parseDouble(csvLine.get(index++));
			this.absMean = Double.parseDouble(csvLine.get(index++));
			this.median = Double.parseDouble(csvLine.get(index++));
			this.min= Double.parseDouble(csvLine.get(index++));
			this.max = Double.parseDouble(csvLine.get(index++));
			this.l2Norm = Double.parseDouble(csvLine.get(index++));
			this.energy = Double.parseDouble(csvLine.get(index++));
			this.std = Double.parseDouble(csvLine.get(index++));
		}
		
		public List<String> buildCSVLine() {
			return List.of(
					range.name, range.shortName, range.weight+"", range.weightingType+"", range.inequality+"",
					numRows+"", mean+"", absMean+"", median+"", min+"", max+"", l2Norm+"", energy+"", std+"");
		}
	}
	
	public static final List<String> csvHeader = List.of(
			"Constraint Name", "Short Name", "Weight", "Weight Type", "Inequality", "Rows", "Mean", "Mean Absolute",
			"Median", "Minimum", "Maximum", "L2 Norm", "Energy", "Standard Deviation");
	
	private List<MisfitStats> misfitStats;
	
	public InversionMisfitStats(List<MisfitStats> misfitStats) {
		this.misfitStats = misfitStats;
	}
	
	@SuppressWarnings("unused") // used for deserialization
	private InversionMisfitStats() {}
	
	public List<MisfitStats> getStats() {
		return misfitStats;
	}
	
	/**
	 * 
	 * @param range
	 * @return statistics for the given constraint range, will return anything that matches by name
	 */
	public MisfitStats forRange(ConstraintRange range) {
		for (MisfitStats stats : misfitStats)
			if (stats.range == range || stats.range.name.equals(range.name))
				return stats;
		return null;
	}
	
	/**
	 * 
	 * @param range
	 * @return statistics for the given constraint range name
	 */
	public MisfitStats forRangeName(String name) {
		for (MisfitStats stats : misfitStats)
			if (stats.range.name.equals(name))
				return stats;
		return null;
	}
	
	public static final String MISFIT_STATS_FILE_NAME = "inversion_misfit_stats.csv";

	@Override
	public String getFileName() {
		return MISFIT_STATS_FILE_NAME;
	}

	@Override
	public String getName() {
		return "Inversion Misfit Statistics";
	}

	@Override
	public CSVFile<?> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine(csvHeader);
		for (MisfitStats stats : misfitStats)
			csv.addLine(stats.buildCSVLine());
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		misfitStats = new ArrayList<>();
		for (int row=1; row<csv.getNumRows(); row++)
			misfitStats.add(new MisfitStats(csv.getLine(row)));
	}

}
