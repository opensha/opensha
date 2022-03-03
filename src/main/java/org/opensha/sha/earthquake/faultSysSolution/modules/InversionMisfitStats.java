package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;

import cern.colt.function.tdouble.DoubleComparator;

import com.google.common.base.Preconditions;

/**
 * Summary statistics for inversion misfit
 * 
 * @author kevin
 *
 */
public class InversionMisfitStats implements CSV_BackedModule, BranchAverageableModule<InversionMisfitStats> {
	
	public enum Quantity {
		MEAN("Mean") {
			@Override
			public double get(MisfitStats stats) {
				return stats.mean;
			}
		},
		MAD("Mean Absolute Deviation") {
			@Override
			public double get(MisfitStats stats) {
				return stats.absMean;
			}
		},
		MEDIAN("Median") {
			@Override
			public double get(MisfitStats stats) {
				return stats.median;
			}
		},
		MIN("Minimum") {
			@Override
			public double get(MisfitStats stats) {
				return stats.min;
			}
		},
		MAX("Maximum") {
			@Override
			public double get(MisfitStats stats) {
				return stats.max;
			}
		},
		L2_NORM("L2 Norm") {
			@Override
			public double get(MisfitStats stats) {
				return stats.l2Norm;
			}
		},
		ENERGY("Energy") {
			@Override
			public double get(MisfitStats stats) {
				return stats.energy;
			}
		},
		STD_DEV("Standard Deviation") {
			@Override
			public double get(MisfitStats stats) {
				return stats.std;
			}
		},
		RMSE("RMSE") {
			@Override
			public double get(MisfitStats stats) {
				return stats.rmse;
			}
		};
		
		private String name;

		private Quantity(String name) {
			this.name = name;
		}
		
		public abstract double get(MisfitStats stats);
		
		@Override
		public String toString() {
			return name;
		}
	}
	
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
		public final double rmse;

		private final static double epsilon = 1.0e-10;
		
		public MisfitStats(double[] misfits, boolean inequality, double weight) {
			this(misfits, new ConstraintRange(inequality ? "Inequality" : "Equality",
					inequality ? "Ineq" : "Eq", 0, misfits.length, inequality, weight, null));
		}

		private boolean doubleEquals(double u, double v) {
			return (Math.abs(u - v) <= MisfitStats.epsilon * Math.abs(u) && Math.abs(u - v) <= MisfitStats.epsilon * Math.abs(v));
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
			int included = 0;
			for (double val : misfits) {
				if (doubleEquals(val,-1d)) // deal with edge cse MFD bins where we're setting artificial rate/stddev values
					continue;
				if (range.inequality && val < 0d)
					val = 0d;
				mean += val;
				absMean += Math.abs(val);
				min = Math.min(val, min);
				max = Math.max(val, max);
				l2Norm += val*val;
				energy += (val*range.weight)*(val*range.weight);
				std.increment(val);
				included+=1;
			}
			mean /= (double)included;
			absMean /= (double)included;
			
			this.mean = mean;
			this.absMean = absMean;
			this.median = DataUtils.median(misfits);
			this.min = min;
			this.max = max;
			this.l2Norm = l2Norm;
			this.energy = energy;
			if (numRows == 1 && range.weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY)
				// we have one value and it's a std dev, use use that val
				this.std = Math.abs(misfits[0]);
			else
				this.std = std.getResult();
			double mse = l2Norm/(double)included;
			this.rmse = Math.sqrt(mse);

			/*
			if (range.shortName.equals("UncertMFDEquality")) {
				System.out.println("MFD misfit array:");
				System.out.println("number included: " + included);
				for (double val : misfits) {
					System.out.println(val);
				}
			}
			else {
				System.out.println(range.shortName);
				System.out.println("numRows = " + numRows + " included = " + included);
			}
			*/
			

		}
		
		MisfitStats(List<String> csvLine) {
			Preconditions.checkState(csvLine.size() == csvHeader.size() || csvLine.size() == csvHeader.size()-1);
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
			if (numRows == 1 && range.weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY
					&& Double.parseDouble(csvLine.get(index)) == 0d)
				// we have one value and it's a std dev, use use that val
				this.std = this.absMean;
			else
				this.std = Double.parseDouble(csvLine.get(index++));
			if (csvLine.size() == index) {
				// old
				double mse = l2Norm/(double)numRows;
				this.rmse = Math.sqrt(mse);
			} else {
				this.rmse = Double.parseDouble(csvLine.get(index++));
			}
		}
		
		private MisfitStats(ConstraintRange range, int numRows, double mean, double absMean, double median, double min,
				double max, double l2Norm, double energy, double std, double rmse) {
			super();
			this.range = range;
			this.numRows = numRows;
			this.mean = mean;
			this.absMean = absMean;
			this.median = median;
			this.min = min;
			this.max = max;
			this.l2Norm = l2Norm;
			this.energy = energy;
			this.std = std;
			this.rmse = rmse;
		}
		
		public double get(Quantity quantity) {
			return quantity.get(this);
		}

		public List<String> buildCSVLine() {
			return List.of(
					range.name, range.shortName, range.weight+"", range.weightingType+"", range.inequality+"",
					numRows+"", mean+"", absMean+"", median+"", min+"", max+"", l2Norm+"", energy+"", std+"", rmse+"");
		}
	}
	
	public static final List<String> csvHeader = List.of(
			"Constraint Name", "Short Name", "Weight", "Weight Type", "Inequality", "Rows", "Mean", "Mean Absolute",
			"Median", "Minimum", "Maximum", "L2 Norm", "Energy", "Standard Deviation", "Root Mean Squared Error");
	
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

	@Override
	public AveragingAccumulator<InversionMisfitStats> averagingAccumulator() {
		return new AveragingAccumulator<InversionMisfitStats>() {
			
			private List<List<MisfitStats>> stats = new ArrayList<>();
			private List<Double> weights = new ArrayList<>();
			
			@Override
			public void process(InversionMisfitStats module, double relWeight) {
				stats.add(module.misfitStats);
				weights.add(relWeight);
			}
			
			@Override
			public InversionMisfitStats getAverage() {
				HashMap<String, List<MisfitStats>> nameMatchesMap = new HashMap<>();
				HashMap<String, List<Double>> nameWeightsMap = new HashMap<>();
				
				List<ConstraintRange> ranges = new ArrayList<>();
				
				for (int i=0; i<stats.size(); i++) {
					List<MisfitStats> myStats = stats.get(i);
					double weight = weights.get(i);
					for (int j=0; j<myStats.size(); j++) {
						MisfitStats stat = myStats.get(j);
						String name = stat.range.name;
						Preconditions.checkState(name != null && !name.isBlank());
						if (!nameMatchesMap.containsKey(name)) {
							// first time we've encountered this constraint
							nameMatchesMap.put(name, new ArrayList<>());
							nameWeightsMap.put(name, new ArrayList<>());
							ranges.add(stat.range);
						}
						
						nameMatchesMap.get(name).add(stat);
						nameWeightsMap.get(name).add(weight);
					}
				}
				
				
				List<MisfitStats> avgStats = new ArrayList<>();
				
				for (ConstraintRange range : ranges) {
					int numRows = 0; // could vary by constraint, we'll keep the max
					double mean = 0d;
					double absMean = 0d;
					double median = 0d;
					double min = 0d;
					double max = 0d;
					double l2Norm = 0d;
					double energy = 0d;
					double std = 0d;
					double rmse = 0d;
					
					List<MisfitStats> matchingStats = nameMatchesMap.get(range.name);
					List<Double> matchingWeights = nameWeightsMap.get(range.name);
					
					double myTotWeight = 0d;
					for (Double weight : matchingWeights)
						myTotWeight += weight;
					
					for (int i=0; i<matchingStats.size(); i++) {
						double weight = matchingWeights.get(i)/myTotWeight;
						MisfitStats myStats = matchingStats.get(i);
						Preconditions.checkState(range.name.equals(myStats.range.name));
						numRows = Integer.max(numRows, myStats.numRows);
						mean += weight*myStats.mean;
						absMean += weight*myStats.absMean;
						median += weight*myStats.median;
						min += weight*myStats.min;
						max += weight*myStats.max;
						l2Norm += weight*myStats.l2Norm;
						energy += weight*myStats.energy;
						std += weight*myStats.std;
						rmse += weight*myStats.rmse;
					}
					
					avgStats.add(new MisfitStats(range, numRows, mean, absMean, median, min,
							max, l2Norm, energy, std, rmse));
				}
				return new InversionMisfitStats(avgStats);
			}

			@Override
			public Class<InversionMisfitStats> getType() {
				return InversionMisfitStats.class;
			}
		};
	}

}
