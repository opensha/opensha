package scratch.UCERF3.simulatedAnnealing.completion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.GraphWindow;

import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;

import com.google.common.collect.Lists;

public class ProgressTrackingCompletionCriteria implements CompletionCriteria {
	
	private ArrayList<Long> times;
	private ArrayList<Long> iterations;
	private ArrayList<Long> perturbs;
	private ArrayList<double[]> energies;
	
	private CompletionCriteria criteria;
	
	private long autoPlotMillis;
	private long nextPlotMillis;
	private GraphWindow gw;
	private ArrayList<ArbitrarilyDiscretizedFunc> funcs;
	private String plotTitle;
	
	private File automaticFile;
	
	private List<String> rangeNames;
	
	private long iterMod = 0;
	
	public ProgressTrackingCompletionCriteria(CompletionCriteria criteria) {
		this(criteria, null, 0);
	}
	
	public ProgressTrackingCompletionCriteria(CompletionCriteria criteria, double autoPlotMins) {
		this(criteria, null, autoPlotMins);
	}
	
	public ProgressTrackingCompletionCriteria(CompletionCriteria criteria, File automaticFile) {
		this(criteria, automaticFile, 0);
	}
	
	public ProgressTrackingCompletionCriteria(
			CompletionCriteria criteria, File automaticFile, double autoPlotMins) {
		times = new ArrayList<Long>();
		iterations = new ArrayList<Long>();
		energies = new ArrayList<double[]>();
		perturbs = new ArrayList<Long>();
		
		this.criteria = criteria;
		this.automaticFile = automaticFile;
		if (autoPlotMins > 0) {
			this.autoPlotMillis = (long)(autoPlotMins * 60d * 1000d);
			this.nextPlotMillis = autoPlotMillis;
		} else {
			this.autoPlotMillis = 0;
			this.nextPlotMillis = -1;
		}
	}
	
	public synchronized void writeFile(File file) throws IOException {
		CSVFile<String> csv = new CSVFile<String>(true);
		
		ArrayList<String> header = Lists.newArrayList("Iterations", "Time (millis)", "Energy (total)",
				"Energy (equality)", "Energy (entropy)", "Energy (inequality)");
		if (rangeNames != null)
			header.addAll(rangeNames);
		header.add("Total Perterbations Kept");
		csv.addLine(header);
		
		for (int i=0; i<times.size(); i++) {
			double[] energy = energies.get(i);
			ArrayList<String> line = Lists.newArrayList(iterations.get(i)+"", times.get(i)+"");
			for (double e : energy)
				line.add(e+"");
			line.add(perturbs.get(i)+"");
			csv.addLine(line);
		}
		
		csv.writeToFile(file);
	}

	@Override
	public boolean isSatisfied(StopWatch watch, long iter, double[] energy, long numPerturbsKept) {
		if (energy[0] < Double.MAX_VALUE && (iterMod <= 0 || iter % iterMod == 0l)) {
			times.add(watch.getTime());
			iterations.add(iter);
			energies.add(energy);
			perturbs.add(numPerturbsKept);
		}
		if (autoPlotMillis > 0 && watch.getTime() > nextPlotMillis) {
			try {
				updatePlot();
			} catch (Throwable t) {
				// you never want a plot error to stop an inversion!
				t.printStackTrace();
			}
			nextPlotMillis = watch.getTime() + autoPlotMillis;
		}
		if (criteria.isSatisfied(watch, iter, energy, numPerturbsKept)) {
			if (automaticFile != null) {
				System.out.println("Criteria satisfied with time="+(watch.getTime()/60000f)
						+" min, iter="+iter+", energy="+energy[0]+", pertubs kept="+numPerturbsKept);
				System.out.println("Writing progress to file: "+automaticFile.getAbsolutePath());
				// write out results first
				try {
					writeFile(automaticFile);
				} catch (Exception e) {
					System.err.println("Error writing results file!");
					e.printStackTrace();
				}
				System.out.println("Done writing progress file ("+times.size()+" entries)");
			}
			return true;
		}
		return false;
	}
	
	public void setPlotTitle(String plotTitle) {
		this.plotTitle = plotTitle;
	}
	
	private void updatePlot() {
		if (energies.isEmpty())
			return;
		if (gw == null) {
			funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
			funcs.add(new ArbitrarilyDiscretizedFunc("Total Energy"));
			funcs.add(new ArbitrarilyDiscretizedFunc("Equality Energy"));
			funcs.add(new ArbitrarilyDiscretizedFunc("Entropy Energy"));
			funcs.add(new ArbitrarilyDiscretizedFunc("Inequality Energy"));
			if (rangeNames != null) {
				for (String name : rangeNames)
					funcs.add(new ArbitrarilyDiscretizedFunc(name+" Energy"));
			} else {
				for (int i=4; i<energies.get(0).length; i++) {
					funcs.add(new ArbitrarilyDiscretizedFunc("Unknown Energy "+(i+1)));
				}
			}
			ArrayList<PlotCurveCharacterstics> chars = ThreadedSimulatedAnnealing.getEnergyBreakdownChars();
			updatePlotFuncs();
			String title = "Energy vs Iterations";
			if (plotTitle != null)
				title += " ("+plotTitle+")";
			gw = new GraphWindow(funcs, title, chars);
		} else {
			updatePlotFuncs();
			gw.getGraphWidget().drawGraph();
		}
		ArbitrarilyDiscretizedFunc equalityFunc = funcs.get(1);
		double maxEqualityEnergy = equalityFunc.getY(0);
		if (energies.get(energies.size()-1)[0] < maxEqualityEnergy) {
			// if we're already under the max equality level, adjust the scale so that
			// everything interesting is visible
			gw.setAxisRange(0, equalityFunc.getX(equalityFunc.size()-1)*1.1, 0, maxEqualityEnergy*1.2);
		} else {
			gw.setAutoRange();
		}
	}
	
	private void updatePlotFuncs() {
		int start = funcs.get(0).size();
		for (int i=start; i<energies.size(); i++) {
			long iter = iterations.get(i);
			double[] energy = energies.get(i);
			for (int j=0; j<energy.length; j++)
				funcs.get(j).set((double)iter, energy[j]);
		}
		for (ArbitrarilyDiscretizedFunc func : funcs)
			func.setInfo("Final Energy: "+func.getY(func.size()-1));
	}
	
	@Override
	public String toString() {
		return criteria.toString();
	}

	public ArrayList<Long> getTimes() {
		return times;
	}

	public ArrayList<Long> getIterations() {
		return iterations;
	}

	public ArrayList<Long> getPerturbs() {
		return perturbs;
	}

	public ArrayList<double[]> getEnergies() {
		return energies;
	}
	
	public void setRangeNames(List<String> rangeNames) {
		this.rangeNames = rangeNames;
	}
	
	public CompletionCriteria getCriteria() {
		return criteria;
	}
	
	public void setIterationModulus(long iterMod) {
		this.iterMod = iterMod;
	}

}
