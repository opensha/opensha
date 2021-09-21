package scratch.UCERF3.simulatedAnnealing;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.ArrayUtils;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;

import com.google.common.collect.Lists;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.simulatedAnnealing.completion.AnnealingProgress;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.ProgressTrackingCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.CoolingScheduleType;
import scratch.UCERF3.simulatedAnnealing.params.GenerationFunctionType;
import scratch.UCERF3.simulatedAnnealing.params.NonnegativityConstraintType;

public interface SimulatedAnnealing {

	public void setCalculationParams(CoolingScheduleType coolingFunc,
			NonnegativityConstraintType nonnegativeityConstraintAlgorithm,
			GenerationFunctionType perturbationFunc);

	public CoolingScheduleType getCoolingFunc();

	public void setCoolingFunc(CoolingScheduleType coolingFunc);

	public NonnegativityConstraintType getNonnegativeityConstraintAlgorithm();

	public void setNonnegativeityConstraintAlgorithm(
			NonnegativityConstraintType nonnegativeityConstraintAlgorithm);

	public GenerationFunctionType getPerturbationFunc();

	public void setPerturbationFunc(GenerationFunctionType perturbationFunc);
	
	public void setVariablePerturbationBasis(double[] variablePerturbBasis);
	
	public void setRuptureSampler(IntegerPDF_FunctionSampler rupSampler);

	public double[] getBestSolution();

	public double[] getInitialSolution();
	
	/**
	 * @return the number of non-zero rates in the the best solution
	 */
	public int getNumNonZero();

	/**
	 * 
	 * @return an array of energies containing: [ total, equality, entropy, inequality ]
	 */
	public double[] getBestEnergy();
	
	public double[] calculateEnergy(double[] solution);
	
	public double[] calculateEnergy(double[] solution, double[] misfit, double[] misfit_ineq);
	
	public double[] calculateEnergy(double[] solution, double[] misfit, double[] misfit_ineq, List<ConstraintRange> constraintRanges);
	
	public double[] getBestMisfit();
	
	public double[] getBestInequalityMisfit();
	
	public DoubleMatrix2D getA_ineq();
	
	public DoubleMatrix2D getA();
	
	public double[] getD();
	
	public double[] getD_ineq();

	public void setResults(double[] Ebest, double[] xbest);
	
	public void setResults(double[] Ebest, double[] xbest, double[] misfit, double[] misfit_ineq, int numNonZero);
	
	public void setConstraintRanges(List<ConstraintRange> constraintRanges);

	/**
	 * Iterate for the given number of iterations
	 * @param numIterations
	 * @return
	 */
	public long iterate(long numIterations);

	/**
	 * Iterate until the given CompletionCriteria is satisfied
	 * @param completion
	 * @return
	 */
	public long iterate(CompletionCriteria completion);
	
	/**
	 * Sets the random number generator used - helpful for reproducing results for testing purposes
	 * @param r
	 */
	public void setRandom(Random r);

	public long[] iterate(long startIter, long startPerturbs, CompletionCriteria criteria);
	
	public default void writeRateVsRankPlot(File outputDir, String prefix, double[] minimumRuptureRates) throws IOException {
		double[] solutionRates = getBestSolution();
		double[] adjustedRates = null;
		if (minimumRuptureRates != null) {
			adjustedRates = InversionInputGenerator.adjustSolutionForWaterLevel(
					getBestSolution(), minimumRuptureRates);
		}
		writeRateVsRankPlot(outputDir, prefix, solutionRates, adjustedRates, getInitialSolution());
	}
	
	/**
	 * Write various SA related plots
	 * 
	 * @param criteria
	 * @param prefix
	 * @throws IOException
	 */
	public default void writePlots(CompletionCriteria criteria, File outputDir, String prefix, double[] minimumRuptureRates) throws IOException {
		// this plots rupture rate vs rank
		int numRuptures = minimumRuptureRates == null ? -1 : minimumRuptureRates.length;
		writeRateVsRankPlot(outputDir, prefix+"_rate_dist", minimumRuptureRates);
		if (criteria instanceof ProgressTrackingCompletionCriteria)
			SimulatedAnnealing.writeProgressPlots(((ProgressTrackingCompletionCriteria)criteria).getProgress(), outputDir, prefix, numRuptures);
	}
	
	static final int plot_width = 1000;
	static final int plot_height = 800;
	
	/**
	 * Rupture rate vs rank plots.
	 * 
	 * @param prefix
	 * @param ratesNoMin
	 * @param rates
	 * @param initialState
	 * @throws IOException
	 */
	public static void writeRateVsRankPlot(File outputDir, String prefix, double[] ratesNoMin, double[] rates, double[] initialState)
			throws IOException {
		// rates without waterlevel
		ratesNoMin = getSorted(ratesNoMin);
		// rates with waterlevel
		rates = getSorted(rates);
		boolean hasWL = false;
		for (int r=0; r<rates.length; r++) {
			if (ratesNoMin[r] != rates[r]) {
				hasWL = true;
				break;
			}
		}
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0d, ratesNoMin.length, 1d);
		func.setName("Inversion Rates");
		for (int i=0; i<ratesNoMin.length; i++)
			func.set(i, ratesNoMin[i]);
		ArrayList<DiscretizedFunc> funcs = new ArrayList<DiscretizedFunc>();
		funcs.add(func);
		ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList(
//				new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, PlotSymbol.CIRCLE, 5f, Color.BLACK));
				new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLUE));
		
		EvenlyDiscretizedFunc initialFunc = new EvenlyDiscretizedFunc(0d, initialState.length, 1d);
		initialFunc.setName("Initial Solution");
		double[] initialSorted = getSorted(initialState);
		for (int i=0; i<initialSorted.length; i++)
			initialFunc.set(i, initialSorted[i]);
		funcs.add(0, initialFunc);
		chars.add(0, new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GREEN));
		
		if (hasWL) {
			EvenlyDiscretizedFunc adjFunc = new EvenlyDiscretizedFunc(0d, ratesNoMin.length, 1d);
			adjFunc.setName("Final Solution");
			for (int i=0; i<rates.length; i++)
				adjFunc.set(i, rates[i]);
			funcs.add(0, adjFunc);
			chars.add(0, new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		}
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		gp.setYLog(true);
		PlotSpec spec = new PlotSpec(funcs, chars, "Rupture Rate Distribution", "Rank", "Rate (per year)");
		spec.setLegendInset(true);
		gp.drawGraphPanel(spec);
		File file = new File(outputDir, prefix);
		gp.saveAsPNG(file.getAbsolutePath()+".png", plot_width, plot_height);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf", plot_width, plot_height);
		
		// now cumulatives
		funcs = new ArrayList<>();
		chars = new ArrayList<>();
		
		if (hasWL) {
			funcs.add(getCumulative(rates, true, "Final Solution, Rate >= Rank"));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
			funcs.add(getCumulative(rates, false, "Final Solution, Rate <= Rank"));
			chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, Color.BLACK));
		}
		funcs.add(getCumulative(ratesNoMin, true, "Inversion Rates, Rate >= Rank"));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLUE));
		funcs.add(getCumulative(ratesNoMin, false, "Inversion Rates, Rate <= Rank"));
		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, Color.BLUE));
		
		spec = new PlotSpec(funcs, chars, "Cumulative Rate Distribution", "Rank", "Cumulative Rate (per year)");
		spec.setLegendInset(true);
		gp.drawGraphPanel(spec);
		gp.setAutoRange();
		file = new File(outputDir, prefix+"_cumulative");
		gp.saveAsPNG(file.getAbsolutePath()+".png", plot_width, plot_height);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf", plot_width, plot_height);
	}
	
	private static EvenlyDiscretizedFunc getCumulative(double[] rates, boolean rateAbove, String name) {
		EvenlyDiscretizedFunc cmlFunc = new EvenlyDiscretizedFunc(0d, rates.length, 1d);
		cmlFunc.setName(name);;
		
		if (rateAbove) {
			double sum = 0d;
			for (int r=rates.length; --r>=0;) {
				sum += rates[r];
				cmlFunc.set(r, sum);
			}
		} else {
			double sum = 0d;
			for (int r=0; r<rates.length; r++) {
				sum += rates[r];
				cmlFunc.set(r, sum);
			}
		}
		
		return cmlFunc;
	}
	
	/**
	 * This writes plots of SA energies as a function of time
	 * @param track
	 * @param outputDir
	 * @param prefix
	 * @throws IOException
	 */
	public static void writeProgressPlots(AnnealingProgress track, File outputDir, String prefix, int numRuptures) throws IOException {
		ArbitrarilyDiscretizedFunc perturbsVsIters = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc nonZerosVsIters = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc percentNonZerosVsIters = new ArbitrarilyDiscretizedFunc();
		
		List<String> types = track.getEnergyTypes();
		int num = types.size();
		
		List<ArbitrarilyDiscretizedFunc> timeFuncs = new ArrayList<>();
		List<ArbitrarilyDiscretizedFunc> iterFuncs = new ArrayList<>();
		boolean[] hasNonZeros = new boolean[num];
		for (int i=0; i<num; i++) {
			timeFuncs.add(new ArbitrarilyDiscretizedFunc(types.get(i)));
			iterFuncs.add(new ArbitrarilyDiscretizedFunc(types.get(i)));
		}
		
		for (int i=0; i<track.size(); i++) {
			double[] energy = track.getEnergies(i);
			long time = track.getTime(i);
			double mins = time / 1000d / 60d;
			long perturb = track.getNumPerturbations(i);
			long iter = track.getIterations(i);
			int nonZeros = track.getNumNonZero(i);
			
			for (int j=0; j<energy.length; j++) {
				timeFuncs.get(j).set(mins, energy[j]);
				iterFuncs.get(j).set((double)iter, energy[j]);
				if (energy[j] > 0)
					hasNonZeros[j] = true;
			}
			perturbsVsIters.set((double)iter, (double)perturb);
			nonZerosVsIters.set((double)iter, (double)nonZeros);
			if (numRuptures > 0)
				percentNonZerosVsIters.set((double)iter, 100d*(double)nonZeros/(double)numRuptures);
		}
		
		ArrayList<PlotCurveCharacterstics> energyChars = getEnergyBreakdownChars();
		// remove any unused
		for (int i=energyChars.size(); --i>=0;) {
			if (!hasNonZeros[i]) {
				energyChars.remove(i);
				timeFuncs.remove(i);
				iterFuncs.remove(i);
			}
		}
		ArrayList<PlotCurveCharacterstics> extraChars = getEnergyExtraChars();
		
		
		
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		for (int i=0; i<num; i++) {
			if (i < energyChars.size()) {
				chars.add(energyChars.get(i));
			} else {
				int extraIndex = i - energyChars.size();
				chars.add(extraChars.get(extraIndex % extraChars.size()));
			}
		}

		PlotCurveCharacterstics perturbChar =
			new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.CYAN.darker());
		PlotCurveCharacterstics nzChar =
				new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.ORANGE.darker());
		PlotCurveCharacterstics pnzChar =
			new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.RED.darker());
		
		String timeLabel = "Time (minutes)";
		String iterationsLabel = "Iterations";
		String energyLabel = "Energy";
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		// this chops off any huge energy values in the first 5% of the run so that the plots
		// are readable at the energy levels that are actually interesting
//		double energyAter5percent = iterFuncs.get(1).getY((int)((iterFuncs.get(1).size()-1d)*0.05 + 0.5));
//		double energyPlotMax = energyAter5percent*1.2;
		
		double energyAter5percent = iterFuncs.get(1).getY((int)((iterFuncs.get(1).size()-1d)*0.05 + 0.5));
		double bestEnergy = track.getEnergies(track.size()-1)[0];
		double energyPlotMax;
		if (bestEnergy < 5d)
			energyPlotMax = energyAter5percent*1.2;
		else if (bestEnergy < 20d)
			energyPlotMax = 50;
		else if (bestEnergy < 40d)
			energyPlotMax = 100;
		else if (bestEnergy < 250d)
			energyPlotMax = 500;
		else if (bestEnergy < 500d)
			energyPlotMax = 1000;
		else
			energyPlotMax = Math.max(1000, energyAter5percent*1.2);
		
		double energyPlotMin = 0;
		double timeMin = 0, itersMin = 0;
		double timeMax = timeFuncs.get(0).getMaxX(), iterMax = iterFuncs.get(0).getMaxX();
		
		// energy vs time plot
		gp.setUserBounds(timeMin, timeMax, energyPlotMin, energyPlotMax);
		PlotSpec eVtSpec = new PlotSpec(timeFuncs, chars,
				"Energy Vs Time", timeLabel, energyLabel);
		eVtSpec.setLegendInset(true);
		gp.drawGraphPanel(eVtSpec);
		gp.saveAsPNG(new File(outputDir, prefix+"_energy_vs_time.png").getAbsolutePath(),
				plot_width, plot_height);
		gp.saveAsPDF(new File(outputDir, prefix+"_energy_vs_time.pdf").getAbsolutePath(),
				plot_width, plot_height);
		
		// energy vs iters plot
		gp.setUserBounds(itersMin, iterMax, energyPlotMin, energyPlotMax);
		PlotSpec eViSpec = new PlotSpec(iterFuncs, chars,
				"Energy Vs Iterations", iterationsLabel, energyLabel);
		eViSpec.setLegendInset(true);
		gp.drawGraphPanel(eViSpec);
		gp.saveAsPNG(new File(outputDir, prefix+"_energy_vs_iters.png").getAbsolutePath(),
				plot_width, plot_height);
		gp.saveAsPDF(new File(outputDir, prefix+"_energy_vs_iters.pdf").getAbsolutePath(),
				plot_width, plot_height);
		
		// perturbations vs iters plots
		ArrayList<ArbitrarilyDiscretizedFunc> perturbWrap = new ArrayList<ArbitrarilyDiscretizedFunc>();
		perturbWrap.add(perturbsVsIters);
		ArrayList<ArbitrarilyDiscretizedFunc> nzWrap = new ArrayList<ArbitrarilyDiscretizedFunc>();
		nzWrap.add(nonZerosVsIters);
		gp.setAutoRange();
		String combTitle = "Perturbations & Non-Zero Rates Vs Iters";
		PlotSpec pSpec = new PlotSpec(Lists.newArrayList(perturbWrap), Lists.newArrayList(perturbChar),
				combTitle, iterationsLabel, "# Perturbations");
		PlotSpec nzSpec = new PlotSpec(Lists.newArrayList(nzWrap), Lists.newArrayList(nzChar),
				combTitle, iterationsLabel, "# Non-Zero Rates");
		List<PlotSpec> combSpecs;
		if (numRuptures > 0) {
			ArrayList<ArbitrarilyDiscretizedFunc> pnzWrap = new ArrayList<ArbitrarilyDiscretizedFunc>();
			pnzWrap.add(percentNonZerosVsIters);
			PlotSpec pnzSpec = new PlotSpec(Lists.newArrayList(pnzWrap), Lists.newArrayList(pnzChar),
					combTitle, iterationsLabel, "% Non-Zero Rates");
			combSpecs = List.of(pnzSpec, nzSpec, pSpec);
		} else {
			combSpecs = List.of(nzSpec, pSpec);
		}
		pSpec.setLegendInset(false);
		List<Range> combXRanges = List.of(new Range(0d, iterMax));
		gp.drawGraphPanel(combSpecs, false, false, combXRanges, null);
		gp.saveAsPNG(new File(outputDir, prefix+"_perturb_vs_iters.png").getAbsolutePath(),
				plot_width, numRuptures > 0 ? (int)(plot_height*1.4) : plot_height);
		
//		ArrayList<PlotCurveCharacterstics> normChars = new ArrayList<PlotCurveCharacterstics>();
//		normChars.addAll(energyChars);
//		normChars.add(perturbChar);
//		
//		// normalized plot
//		getNormalized(prefix, energyVsIters, perturbsVsIters, energies,
//				perturbs, iters, iterationsLabel, gp, normChars,
//				0, "_normalized.png", energyAter5percent);
//		
//		// zoomed normalized plots
//		int middle = (iters.size()-1)/2;
//		getNormalized(prefix, energyVsIters, perturbsVsIters, energies,
//				perturbs, iters, iterationsLabel, gp, normChars,
//				middle, "_normalized_zoomed_50.png", energyAter5percent);
//
//		int end = (int)((iters.size()-1) * 0.75d+0.5);
//		getNormalized(prefix, energyVsIters, perturbsVsIters, energies,
//				perturbs, iters, iterationsLabel, gp, normChars,
//				end, "_normalized_zoomed_75.png", energyAter5percent);
	}
	
	public static ArrayList<PlotCurveCharacterstics> getEnergyBreakdownChars() {
		ArrayList<PlotCurveCharacterstics> energyChars = new ArrayList<PlotCurveCharacterstics>();
		energyChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
		energyChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED));
		energyChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.GREEN));
		energyChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));
		return energyChars;
	}
	
	public static ArrayList<PlotCurveCharacterstics> getEnergyExtraChars() {
		ArrayList<PlotCurveCharacterstics> energyChars = new ArrayList<PlotCurveCharacterstics>();
		for (Color color : GraphPanel.defaultColor)
			energyChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, color.darker()));
		return energyChars;
	}

//	private static void getNormalized(File prefix,
//			ArbitrarilyDiscretizedFunc[] energyVsIters,
//			ArbitrarilyDiscretizedFunc perturbsVsIters,
//			ArrayList<double[]> energies, ArrayList<Long> perturbs,
//			ArrayList<Long> iters, String iterationsLabel,
//			HeadlessGraphPanel gp,
//			ArrayList<PlotCurveCharacterstics> normChars, int startPoint, String suffix,
//			double maxEnergy)
//			throws IOException {
//		ArbitrarilyDiscretizedFunc normPerturbs;
//		ArrayList<ArbitrarilyDiscretizedFunc> normalizedFuncs = new ArrayList<ArbitrarilyDiscretizedFunc>();
//		for (int i=0; i<energyVsIters.length; i++) {
//			ArbitrarilyDiscretizedFunc norm = new ArbitrarilyDiscretizedFunc();
//			ArbitrarilyDiscretizedFunc energyFunc = energyVsIters[i];
//			for (int j=startPoint; j<energyFunc.size(); j++) {
//				double normalized = energyFunc.getY(j) / maxEnergy;
//				norm.set(energyFunc.getX(j), normalized);
//			}
//			normalizedFuncs.add(norm);
//		}
//		double minPerturbs = (double)perturbs.get(startPoint);
//		double maxPerturbs = (double)perturbs.get(perturbs.size()-1);
//		maxPerturbs -= minPerturbs;
//		normPerturbs = new ArbitrarilyDiscretizedFunc();
//		for (int i=startPoint; i<perturbsVsIters.size(); i++) {
//			double normalized = (perturbsVsIters.getY(i) - minPerturbs) / maxPerturbs;
//			normPerturbs.set(perturbsVsIters.getX(i), normalized);
//		}
//		normalizedFuncs.add(normPerturbs);
//		String title = "Normalized";
//		if (startPoint > 0) {
//			long startIter = iters.get(startPoint);
//			long endIter = iters.get(iters.size()-1);
//			title += " (Iterations "+startIter+" => "+endIter+")";
//		}
//		double minX = normalizedFuncs.get(0).getMinX();
//		double maxX = normalizedFuncs.get(0).getMaxX()*1.05;
//		double minY = 0;
//		double maxY = 1.15;
//		gp.setUserBounds(minX, maxX, minY, maxY);
//		gp.drawGraphPanel(iterationsLabel, "Normalized", normalizedFuncs, normChars, title);
//		gp.saveAsPNG(new File(prefix.getParentFile(),
//				prefix.getName()+suffix).getAbsolutePath(),
//				plot_width, plot_height);
//	}
	
	private static double[] getSorted(double[] rates) {
		double[] newrates = Arrays.copyOf(rates, rates.length);
		Arrays.sort(newrates);
		ArrayUtils.reverse(newrates);
		return newrates;
	}

}