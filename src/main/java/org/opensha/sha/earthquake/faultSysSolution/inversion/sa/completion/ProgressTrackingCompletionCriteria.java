package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.commons.gui.plot.GraphWindow;

import com.google.common.collect.Lists;

public class ProgressTrackingCompletionCriteria implements CompletionCriteria {
	
	private AnnealingProgress progress;
	
	private CompletionCriteria criteria;
	
	private long autoPlotMillis;
	private long nextPlotMillis;
	
	private transient GraphWindow gw;
	private transient ArrayList<ArbitrarilyDiscretizedFunc> funcs;
	private transient String plotTitle;
	
	private File automaticFile;
	
	private transient List<ConstraintRange> constraintRanges;
	
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
	
	public AnnealingProgress getProgress() {
		return progress;
	}
	
	public synchronized void writeFile(File file) throws IOException {
		progress.getCSV().writeToFile(file);
	}

	@Override
	public boolean isSatisfied(StopWatch watch, long iter, double[] energy, long numPerturbsKept, int numNonZero, double[] misfits, double[] misfits_ineq, List<ConstraintRange> constraintRanges) {
		if (progress == null)
			progress = AnnealingProgress.forConstraintRanges(constraintRanges);
		if (energy[0] < Double.MAX_VALUE && (iterMod <= 0 || iter % iterMod == 0l))
			progress.addProgress(iter, watch.getTime(), numPerturbsKept, energy, numNonZero);
		if (autoPlotMillis > 0 && watch.getTime() > nextPlotMillis) {
			try {
				updatePlot();
			} catch (Throwable t) {
				// you never want a plot error to stop an inversion!
				t.printStackTrace();
			}
			nextPlotMillis = watch.getTime() + autoPlotMillis;
		}
		if (criteria.isSatisfied(watch, iter, energy, numPerturbsKept, numNonZero, misfits, misfits_ineq, constraintRanges)) {
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
				System.out.println("Done writing progress file ("+progress.size()+" entries)");
			}
			return true;
		}
		return false;
	}
	
	public void setPlotTitle(String plotTitle) {
		this.plotTitle = plotTitle;
	}
	
	private void updatePlot() {
		if (progress == null || progress.size() == 0)
			return;
		if (gw == null) {
			funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
			funcs.add(new ArbitrarilyDiscretizedFunc("Total Energy"));
			funcs.add(new ArbitrarilyDiscretizedFunc("Equality Energy"));
			funcs.add(new ArbitrarilyDiscretizedFunc("Entropy Energy"));
			funcs.add(new ArbitrarilyDiscretizedFunc("Inequality Energy"));
			if (constraintRanges != null) {
				for (ConstraintRange name : constraintRanges)
					funcs.add(new ArbitrarilyDiscretizedFunc(name.shortName+" Energy"));
			} else {
				for (int i=4; i<progress.getEnergies(0).length; i++) {
					funcs.add(new ArbitrarilyDiscretizedFunc("Unknown Energy "+(i+1)));
				}
			}
			ArrayList<PlotCurveCharacterstics> chars = SimulatedAnnealing.getEnergyBreakdownChars();
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
		if (progress.getEnergies(progress.size()-1)[0] < maxEqualityEnergy) {
			// if we're already under the max equality level, adjust the scale so that
			// everything interesting is visible
			gw.setAxisRange(0, equalityFunc.getX(equalityFunc.size()-1)*1.1, 0, maxEqualityEnergy*1.2);
		} else {
			gw.setAutoRange();
		}
	}
	
	private void updatePlotFuncs() {
		int start = funcs.get(0).size();
		for (int i=start; i<progress.size(); i++) {
			long iter = progress.getIterations(i);
			double[] energy = progress.getEnergies(i);
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
	
	public void setConstraintRanges(List<ConstraintRange> constraintRanges) {
		this.constraintRanges = constraintRanges;
	}
	
	public CompletionCriteria getCriteria() {
		return criteria;
	}
	
	public void setIterationModulus(long iterMod) {
		this.iterMod = iterMod;
	}

}
