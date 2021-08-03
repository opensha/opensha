package scratch.UCERF3.simulatedAnnealing.completion;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import scratch.UCERF3.simulatedAnnealing.ConstraintRange;

public class AnnealingProgress implements CSV_BackedModule {
	
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

	@Override
	public String getFileName() {
		return "annealing_progress.csv";
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

}
