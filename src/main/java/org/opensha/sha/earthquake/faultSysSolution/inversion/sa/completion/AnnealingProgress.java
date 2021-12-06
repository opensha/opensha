package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

public class AnnealingProgress implements CSV_BackedModule, AverageableModule<AnnealingProgress> {
	
	private ImmutableList<String> energyTypes;
	
	private List<Long> times;
	private List<Long> iterations;
	private List<Long> perturbs;
	private List<double[]> energies;
	private List<Integer> numNonZeros;
	
	@SuppressWarnings("unused") // for serialization
	private AnnealingProgress() {}
	
	public static final List<String> defaultTypes = ImmutableList.of("Total Energy", "Equality Energy",
			"Entropy Energy", "Inequality Energy");
	
	public static AnnealingProgress forConstraintRanges(List<ConstraintRange> constraintRanges) {
		List<String> types = new ArrayList<>(defaultTypes);
		if (constraintRanges != null)
			for (ConstraintRange range : constraintRanges)
				types.add(range.name);
		return new AnnealingProgress(types);
	}
	
	public AnnealingProgress(List<String> energyTypes) {
		this.energyTypes = ImmutableList.copyOf(energyTypes);
		times = new ArrayList<>();
		iterations = new ArrayList<>();
		perturbs = new ArrayList<>();
		energies = new ArrayList<>();
		numNonZeros = new ArrayList<>();
	}
	
	public AnnealingProgress(CSVFile<String> csv) {
		this();
		initFromCSV(csv);
	}
	
	public static final String PROGRESS_FILE_NAME = "annealing_progress.csv";

	@Override
	public String getFileName() {
		return PROGRESS_FILE_NAME;
	}

	@Override
	public String getName() {
		return "Annealing Progress";
	}

	@Override
	public CSVFile<?> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		List<String> header = new ArrayList<>();
		header.add("Iteration");
		header.add("Time (ms)");
		header.add("# Perturbations");
		header.add("# Non-Zero");
		for (String energyType : energyTypes)
			header.add(energyType);
		csv.addLine(header);
		for (int i=0; i<times.size(); i++) {
			List<String> line = new ArrayList<>(header.size());
			line.add(iterations.get(i)+"");
			line.add(times.get(i)+"");
			line.add(perturbs.get(i)+"");
			line.add(numNonZeros.get(i)+"");
			for (double energy : energies.get(i))
				line.add((float)energy+"");
			csv.addLine(line);
		}
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		Builder<String> energyTypeBuilder = ImmutableList.builder();
		times = new ArrayList<>();
		iterations = new ArrayList<>();
		perturbs = new ArrayList<>();
		numNonZeros = new ArrayList<>();
		energies = new ArrayList<>();
		
		List<String> header = csv.getLine(0);
		for (int i=4; i<header.size(); i++)
			energyTypeBuilder.add(header.get(i));
		this.energyTypes = energyTypeBuilder.build();
		
		for (int row=1; row<csv.getNumRows(); row++) {
			int col = 0;
			iterations.add(csv.getLong(row, col++));
			times.add(csv.getLong(row, col++));
			perturbs.add(csv.getLong(row, col++));
			numNonZeros.add(csv.getInt(row, col++));
			double[] energies = new double[energyTypes.size()];
			for (int i=0; i<energyTypes.size(); i++)
				energies[i] = csv.getDouble(row, col++);
			this.energies.add(energies);
		}
	}
	
	public void addProgress(long numIterations, long time, long perturbations, double[] energies, int numNonZero) {
		Preconditions.checkState(energies.length == energyTypes.size(),
				"Expected %s energies, have %s", energyTypes.size(), energies.length);
		this.times.add(time);
		this.iterations.add(numIterations);
		this.perturbs.add(perturbations);
		this.numNonZeros.add(numNonZero);
		this.energies.add(energies);

	}
	
	public int size() {
		return times.size();
	}
	
	public long getTime(int index) {
		return times.get(index);
	}
	
	public long getIterations(int index) {
		return iterations.get(index);
	}
	
	public long getNumPerturbations(int index) {
		return perturbs.get(index);
	}
	
	public int getNumNonZero(int index) {
		return numNonZeros.get(index);
	}
	
	public double[] getEnergies(int index) {
		return energies.get(index);
	}
	
	public ImmutableList<String> getEnergyTypes() {
		return energyTypes;
	}
	
	/**
	 * This creates an average annealing progress, mapping each individual inversion run into a relative time/inversion
	 * count and then mapping onto the average time/inversion count function.
	 * 
	 * @param progresses
	 * @return
	 */
	public static AnnealingProgress average(List<AnnealingProgress> progresses) {
		double avgMinTime = 0d;
		double avgMinIters = 0d;
		double avgMaxTime = 0d;
		double avgMaxIters = 0d;
		double avgSize = 0d;
		
		ImmutableList<String> types = progresses.get(0).energyTypes;
		
		double scalarEach = 1d/(double)progresses.size();
		for (AnnealingProgress progress : progresses) {
			Preconditions.checkState(types.size() == progress.energyTypes.size());
			Preconditions.checkState(types.equals(progress.energyTypes));

			avgMinTime += scalarEach*progress.times.get(0);
			avgMinIters += scalarEach*progress.iterations.get(0);
			avgMaxTime += scalarEach*progress.times.get(progress.size()-1);
			avgMaxIters += scalarEach*progress.iterations.get(progress.size()-1);
			avgSize += scalarEach*progress.size();
		}
		
		int finalSize = (int)Math.round(avgSize);
		if (finalSize < 2)
			finalSize = 2;
		List<Long> times = evenlyDiscr(avgMinTime, avgMaxTime, finalSize);
		List<Long> iters = evenlyDiscr(avgMinIters, avgMaxIters, finalSize);
		
		List<Double> avgPerturbs = new ArrayList<>();
		List<Double> avgNonZeros = new ArrayList<>();
		List<double[]> avgEnergies = new ArrayList<>();
		
		for (int i=0; i<finalSize; i++) {
			avgPerturbs.add(0d);
			avgNonZeros.add(0d);
			avgEnergies.add(new double[types.size()]);
		}
		
		for (AnnealingProgress progress : progresses) {
			// normalized functions with X from 0 to 1
			DiscretizedFunc[] relEnergyTimeFuncs = new DiscretizedFunc[progress.energyTypes.size()];
			DiscretizedFunc[] relEnergyIterFuncs = new DiscretizedFunc[progress.energyTypes.size()];
			for (int i=0; i<types.size(); i++) {
				relEnergyTimeFuncs[i] = new ArbitrarilyDiscretizedFunc();
				relEnergyIterFuncs[i] = new ArbitrarilyDiscretizedFunc();
			}
			DiscretizedFunc relPerturbs = new ArbitrarilyDiscretizedFunc();
			DiscretizedFunc relNonZeros = new ArbitrarilyDiscretizedFunc();
			long myMinTime = progress.times.get(0);
			long myMinIters = progress.iterations.get(0);
			double myMaxTime = progress.times.get(progress.size()-1);
			double myMaxIters = progress.iterations.get(progress.size()-1);
			double myTimeDur = myMaxTime-myMinTime;
			double myIters = myMaxIters-myMinIters;
			for (int i=0; i<progress.size(); i++) {
				double relTime = (progress.times.get(i)-myMinTime)/myTimeDur;
				Preconditions.checkState((float)relTime >= 0f && (float)relTime <= 1f, "Bad relTime=%s", relTime);
				double relIters = (progress.iterations.get(i)-myMinIters)/myIters;
				Preconditions.checkState((float)relIters >= 0f && (float)relIters <= 1f, "Bad relIters=%s", relIters);
				double[] energies = progress.energies.get(i);
				for (int j=0; j<energies.length; j++) {
					relEnergyTimeFuncs[j].set(relTime, energies[j]);
					relEnergyIterFuncs[j].set(relIters, energies[j]);
				}
				relPerturbs.set(relIters, progress.perturbs.get(i));
				relNonZeros.set(relIters, progress.numNonZeros.get(i));
			}
			
			// now map to our times/iterations to the global time/iteration scale
			for (int i=0; i<finalSize; i++) {
				double relX = (double)i/(double)(finalSize-1);
				double[] energies = avgEnergies.get(i);
				for (int j=0; j<energies.length; j++) {
					// average our energy at this location between the time and iteration time series
					energies[j] += scalarEach*0.5*(relEnergyIterFuncs[j].getInterpolatedY(relX) + relEnergyTimeFuncs[j].getInterpolatedY(relX));
				}
				avgPerturbs.set(i, avgPerturbs.get(i) + scalarEach*relPerturbs.getInterpolatedY(relX));
				avgNonZeros.set(i, avgNonZeros.get(i) + scalarEach*relNonZeros.getInterpolatedY(relX));
			}
		}
		// convert to longs
		List<Long> perturbs = new ArrayList<>();
		List<Integer> numNonZeros = new ArrayList<>();
		for (int i=0; i<finalSize; i++) {
			perturbs.add((long)Math.round(avgPerturbs.get(i)));
			numNonZeros.add((int)Math.round(avgNonZeros.get(i)));
		}
		AnnealingProgress ret = new AnnealingProgress();
		ret.energyTypes = types;
		ret.times = times;
		ret.iterations = iters;
		ret.perturbs = perturbs;
		ret.energies = avgEnergies;
		ret.numNonZeros = numNonZeros;
		return ret;
	}
	
	private static List<Long> evenlyDiscr(double min, double max, int size) {
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(min, max, size);
		List<Long> ret = new ArrayList<>(size);
		for (int i=0; i<size; i++)
			ret.add((long)func.getX(i));
		return ret;
	}

	@Override
	public AveragingAccumulator<AnnealingProgress> averagingAccumulator() {
		return new AveragingAccumulator<AnnealingProgress>() {
			
			List<AnnealingProgress> progresses = new ArrayList<>();

			@Override
			public void process(AnnealingProgress module, double weight) {
				progresses.add(module);
			}

			@Override
			public AnnealingProgress getAverage() {
				return average(progresses);
			}

			@Override
			public Class<AnnealingProgress> getType() {
				return AnnealingProgress.class;
			}
		};
	}

}
