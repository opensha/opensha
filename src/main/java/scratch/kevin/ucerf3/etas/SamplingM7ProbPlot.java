package scratch.kevin.ucerf3.etas;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class SamplingM7ProbPlot extends ETAS_AbstractPlot {
	
	private static final double minMag = 7d;
	private static final int subsetSize = 5000;
	private static final int numSubsets = 10000;
	private static double[] durations =  { 7d/365.25, 1d, 10d };
	
	private List<List<Boolean>> hasMatches;
	private long[] maxOTs;

	protected SamplingM7ProbPlot(ETAS_Config config, ETAS_Launcher launcher) {
		super(config, launcher);
		
		hasMatches = new ArrayList<>();
		maxOTs = new long[durations.length];
		for (int i=0; i<maxOTs.length; i++) {
			maxOTs[i] = (long)(config.getSimulationStartTimeMillis()+ProbabilityModelsCalc.MILLISEC_PER_YEAR*durations[i]);
			hasMatches.add(new ArrayList<>());
		}
	}

	@Override
	public int getVersion() {
		return 0;
	}

	@Override
	public boolean isFilterSpontaneous() {
		return false;
	}

	@Override
	protected void doProcessCatalog(ETAS_Catalog completeCatalog, ETAS_Catalog triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		boolean[] hasIts = new boolean[maxOTs.length];
		for (ETAS_EqkRupture rup : completeCatalog) {
			if (rup.getMag() >= minMag) {
				for (int i=0; i<maxOTs.length; i++)
					if (rup.getOriginTime() <= maxOTs[i])
						hasIts[i] = true;
				break;
			}
		}
		for (int i=0; i<hasIts.length; i++)
			hasMatches.get(i).add(hasIts[i]);
	}

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
		for (int d=0; d<maxOTs.length; d++) {
			double totalProb = calcProb(0, hasMatches.get(d))*100d;
			System.out.println("Total prob: "+totalProb);
			HistogramFunction probsHist = HistogramFunction.getEncompassingHistogram(0d, 3*totalProb, totalProb/25d);
			for (int i=0; i<numSubsets; i++) {
				double prob = calcProb(subsetSize, hasMatches.get(d));
				probsHist.add(probsHist.getClosestXIndex(prob*100d), 1d);
			}
			
			double maxY = probsHist.getMaxY()*1.2;
			
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			funcs.add(probsHist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GRAY));
			
			funcs.add(vertLine(totalProb, maxY, "Mean="+(float)+totalProb+" %"));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
			
			double[] simConf = ETAS_Utils.getBinomialProportion95confidenceInterval(totalProb/100d, hasMatches.size());
			double[] subsetConf = ETAS_Utils.getBinomialProportion95confidenceInterval(totalProb/100d, subsetSize);
			
			funcs.add(vertLine(simConf[0]*100d, maxY, "95% conf"));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
			
			funcs.add(vertLine(simConf[1]*100d, maxY, null));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
			
			funcs.add(vertLine(subsetConf[0]*100d, maxY, "Subset 95% conf"));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
			
			funcs.add(vertLine(subsetConf[1]*100d, maxY, null));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
			
			String prefix = "prob_var_hist_"+getTimeShortLabel(durations[d]).replaceAll(" ", "");
			
			PlotSpec plot = new PlotSpec(funcs, chars,
					getTimeLabel(durations[d], false)+" M"+(float)minMag+" variability, subsets of N="+subsetSize+" catalogs",
					"Probability (%)", "");
			plot.setLegendVisible(true);
			
			HeadlessGraphPanel gp = buildGraphPanel();
			gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
//			widget.setX_AxisRange(new Range(0d, totalHist.getMaxX()+0.5*totalHist.getDelta()));
			gp.drawGraphPanel(plot, false, false, new Range(0d, probsHist.getMaxX()+0.5*probsHist.getDelta()), new Range(0, maxY));
			gp.getChartPanel().setSize(1000, 800);
			gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());		
		}
		return null;
	}
	
	private static XY_DataSet vertLine(double x, double maxY, String name) {
		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(x, 0d);
		line.set(x, maxY);
		line.setName(name);
		return line;
	}
	
	private static double calcProb(int numToInclude, List<Boolean> matches) {
		if (numToInclude < matches.size() && numToInclude > 0) {
			List<Boolean> subMatches = new ArrayList<>(matches);
			Collections.shuffle(subMatches);
			matches = subMatches.subList(0, numToInclude);
		}
		int numWith = 0;
		int totalNum = matches.size();
		for (boolean match : matches)
			if (match)
				numWith++;
		return (double)numWith/(double)totalNum;
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public static void main(String[] args) throws IOException {
		File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
				+ "2019_11_04-ComCatM7p1_ci38457511_122DaysAfter_ShakeMapSurfaces");
		
		File binFile = new File(simDir, "results_complete.bin");
		if (!binFile.exists())
			binFile = new File(simDir, "results_m5_preserve_chain.bin");
		
		System.out.println("Bin file: "+binFile.getAbsolutePath());
		
		ETAS_Config config = ETAS_Config.readJSON(new File(simDir, "config.json"));
		ETAS_Launcher launcher = new ETAS_Launcher(config, false);
		
		SamplingM7ProbPlot plot = new SamplingM7ProbPlot(config, launcher);
		
		plot.processCatalogsFile(binFile); 
		plot.finalize(simDir, null);
	}

}
