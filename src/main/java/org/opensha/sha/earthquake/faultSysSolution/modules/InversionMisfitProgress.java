package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.MisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.Quantity;

import com.google.common.base.Preconditions;

public class InversionMisfitProgress implements CSV_BackedModule, AverageableModule<InversionMisfitProgress> {
	
	private List<Long> iterations;
	private List<Long> times;
	private List<InversionMisfitStats> stats;
	private Quantity targetQuantity;
	private List<Double> targetVals;
	
	@SuppressWarnings("unused") // used for deserialization
	private InversionMisfitProgress() {}
	
	public InversionMisfitProgress(CSVFile<String> csv) {
		initFromCSV(csv);
	}

	public InversionMisfitProgress(List<Long> iterations, List<Long> times, List<InversionMisfitStats> stats) {
		this(iterations, times, stats, null, null);
	}

	public InversionMisfitProgress(List<Long> iterations, List<Long> times, List<InversionMisfitStats> stats,
			Quantity targetQuantity, List<Double> targetVals) {
		Preconditions.checkState(iterations.size() == times.size());
		Preconditions.checkState(iterations.size() == stats.size());
		this.iterations = iterations;
		this.times = times;
		this.stats = stats;
		if (targetQuantity == null || targetVals == null) {
			Preconditions.checkState(targetVals == null && targetQuantity == null,
					"Should specify both target quantity and values, or neither");
		} else {
			Preconditions.checkState(targetVals.size() == iterations.size());
			this.targetQuantity = targetQuantity;
			this.targetVals = targetVals;
		}
	}
	
	public static final String MISFIT_PROGRESS_FILE_NAME = "inversion_misfit_progress.csv";

	@Override
	public String getFileName() {
		return MISFIT_PROGRESS_FILE_NAME;
	}

	@Override
	public String getName() {
		return "Inversion Misfit Progress";
	}

	public List<Long> getIterations() {
		return iterations;
	}

	public List<Long> getTimes() {
		return times;
	}

	public List<InversionMisfitStats> getStats() {
		return stats;
	}
	
	public Quantity getTargetQuantity() {
		return targetQuantity;
	}

	public List<Double> getTargetVals() {
		return targetVals;
	}

	@Override
	public CSVFile<?> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		List<String> header = new ArrayList<>();
		header.add("Iteration");
		header.add("Time (ms)");
		boolean targets = targetQuantity != null && targetVals != null;
		if (targets)
			header.add("Target "+targetQuantity);
		header.addAll(InversionMisfitStats.csvHeader);
		csv.addLine(header);
		
		for (int i=0; i<iterations.size(); i++) {
			String iters = iterations.get(i)+"";
			String time = times.get(i)+"";
			String target = targets ? targetVals.get(i)+"" : null;
			for (MisfitStats stat : stats.get(i).getStats()) {
				List<String> line = new ArrayList<>(header.size());
				line.add(iters);
				line.add(time);
				if (targets)
					line.add(target);
				line.addAll(stat.buildCSVLine());
				csv.addLine(line);
			}
		}
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		iterations = new ArrayList<>();
		times = new ArrayList<>();
		stats = new ArrayList<>();
		
		boolean targets = csv.get(0, 2).trim().startsWith("Target");
		if (targets) {
			targetQuantity = null;
			String header = csv.get(0, 2).trim();
			for (Quantity quantity : Quantity.values()) {
				if (header.equals("Target "+quantity)) {
					Preconditions.checkState(targetQuantity == null);
					targetQuantity = quantity;
				}
			}
			Preconditions.checkNotNull(targetQuantity);
			targetVals = new ArrayList<>();
		}
		
		long curIteration = -1l;
		long curTime = -1l;
		Double curTarget = null;
		List<MisfitStats> curStats = null;
		for (int row=1; row<csv.getNumRows(); row++) {
			int col = 0;
			long iteration = csv.getLong(row, col++);
			long time = csv.getLong(row, col++);
			Double targetVal = null;
			if (targets)
				targetVal = csv.getDouble(row, col++);
			List<String> misfitLine = csv.getLine(row);
			misfitLine = misfitLine.subList(col, misfitLine.size());
			MisfitStats stats = new MisfitStats(misfitLine);

			Preconditions.checkState(iteration >= 0l);
			Preconditions.checkState(iteration >= curIteration);
			Preconditions.checkState(time >= curTime);
			
			if (iteration != curIteration) {
				if (curStats != null) {
					// finalize the previous one
					Preconditions.checkState(curIteration >= 0l);
					Preconditions.checkState(curTime >= 0l);
					Preconditions.checkState(!curStats.isEmpty());
					iterations.add(curIteration);
					times.add(curTime);
					if (targets)
						targetVals.add(curTarget);
						
					this.stats.add(new InversionMisfitStats(curStats));
				}
				curStats = new ArrayList<>();
				curIteration = iteration;
				curTime = time;
				curTarget = targetVal;
			}
			Preconditions.checkState(time == curTime);
			// additional constraint for the same iteration
			curStats.add(stats);
		}
		// finalize the last one
		Preconditions.checkState(curIteration >= 0l);
		Preconditions.checkState(curTime >= 0l);
		Preconditions.checkState(!curStats.isEmpty());
		iterations.add(curIteration);
		times.add(curTime);
		if (targets)
			targetVals.add(curTarget);
		this.stats.add(new InversionMisfitStats(curStats));
	}

	@Override
	public AveragingAccumulator<InversionMisfitProgress> averagingAccumulator() {
		return new AveragingAccumulator<InversionMisfitProgress>() {
			
			List<InversionMisfitProgress> progresses = new ArrayList<>();
			List<Double> weights = new ArrayList<>();
			
			@Override
			public void process(InversionMisfitProgress module, double relWeight) {
				progresses.add(module);
				weights.add(relWeight);
			}
			
			@Override
			public Class<InversionMisfitProgress> getType() {
				return InversionMisfitProgress.class;
			}
			
			@Override
			public InversionMisfitProgress getAverage() {
				int minNumSteps = Integer.MAX_VALUE;
				int maxNumSteps = 0;
				for (InversionMisfitProgress p : progresses) {
					int steps = p.iterations.size();
					minNumSteps = Integer.min(minNumSteps, steps);
					maxNumSteps = Integer.max(maxNumSteps, steps);
				}
				Preconditions.checkState(minNumSteps > 0, "At least 1 InversionMisfitProgress instance has no steps");
				if (minNumSteps < maxNumSteps)
					System.err.println("WARNING: Not all InversionMisfitProgress instances have the same number of "
							+ "steps, only averaging the first "+minNumSteps+" (max is "+maxNumSteps+")");
				
				List<Long> avgIters = new ArrayList<>(minNumSteps);
				List<Long> avgTimes = new ArrayList<>(minNumSteps);
				List<InversionMisfitStats> avgStats = new ArrayList<>(minNumSteps);
				
				for (int i=0; i<minNumSteps; i++) {
					List<Long> iters = new ArrayList<>(progresses.size());
					List<Long> times = new ArrayList<>(progresses.size());
					AveragingAccumulator<InversionMisfitStats> statsAccumulator = null;
					for (int p=0; p<progresses.size(); p++) {
						InversionMisfitProgress progress = progresses.get(p);
						double weight = weights.get(p);
						iters.add(progress.iterations.get(i));
						times.add(progress.times.get(i));
						InversionMisfitStats stats = progress.stats.get(i);
						if (statsAccumulator == null)
							statsAccumulator = stats.averagingAccumulator();
						statsAccumulator.process(stats, weight);
					}
					avgIters.add(longAvg(iters, weights));
					avgTimes.add(longAvg(times, weights));
					avgStats.add(statsAccumulator.getAverage());
				}
				return new InversionMisfitProgress(avgIters, avgTimes, avgStats);
			}
		};
	}
	
	private static long longAvg(List<Long> vals, List<Double> weights) {
		long val1 = vals.get(0);
		double avgSum = 0d;
		double weightSum = 0d;
		boolean allSame = true;
		for (int i=0; i<vals.size(); i++) {
			long val = vals.get(i);
			double weight = weights.get(i);
			
			allSame = allSame && val == val1;
			avgSum += val*weight;
			weightSum += weight;
		}
		if (allSame)
			return val1;
		return (long)(avgSum/weightSum + 0.5);
	}

}
