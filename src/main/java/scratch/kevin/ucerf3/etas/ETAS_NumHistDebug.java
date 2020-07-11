package scratch.kevin.ucerf3.etas;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimulationMetadata;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public class ETAS_NumHistDebug extends ETAS_AbstractPlot {
	
	public static void main(String[] args) throws IOException {
		File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//				+ "2019_10_29-ComCatM7p1_ci38457511_ShakeMapSurfaces_kCOV1p5");
//				+ "2019_10_21-ComCatM7p1_ci38457511_ShakeMapSurfaces_kCOV1p16");
				+ "2019_11_05-ComCatM7p1_ci38457511_ShakeMapSurfaces_kCOV1p16");
		
		File binFile = new File(simDir, "results_complete.bin");
		if (!binFile.exists())
			binFile = new File(simDir, "results_m5_preserve_chain.bin");
		
		System.out.println("Bin file: "+binFile.getAbsolutePath());
		
		File mainOutputDir = new File("/home/kevin/git/misc-research/etas_bimodal_count_debug");
		File myOutputDir = new File(mainOutputDir, simDir.getName());
		Preconditions.checkState(myOutputDir.exists() || myOutputDir.mkdir());
		
		ETAS_Config config = ETAS_Config.readJSON(new File(simDir, "config.json"));
		config.setOutputDir(myOutputDir);
		ETAS_Launcher launcher = new ETAS_Launcher(config, false);
		
		ETAS_NumHistDebug plot = new ETAS_NumHistDebug(config, launcher, 30000, 10);
		
		plot.processCatalogsFile(binFile); 
		plot.finalize(myOutputDir, null);
		
		List<String> lines = plot.generateMarkdown(".", "#", "");
		
		MarkdownUtils.writeReadmeAndHTML(lines, myOutputDir);
	}
	
	private HistogramFunction totalHist;
	private double binMax;
	private Map<Integer, HistogramFunction> maxMagHists = new HashMap<>();
	private Map<Integer, HistogramFunction> numSupraHists = new HashMap<>();
	private Map<Integer, HistogramFunction> kHists = new HashMap<>();
	private Map<Boolean, HistogramFunction> garlockHists = new HashMap<>();
	
	private HashSet<Integer> garlockRups = null;
	
	private int maxRuptureCount = 0;
	
	public ETAS_NumHistDebug(ETAS_Config config, ETAS_Launcher launcher, int maxCount, int delta) {
		super(config, launcher);
		
		totalHist = new HistogramFunction((delta-1d)*0.5, (int)((double)maxCount/(double)delta), (double)delta);
		binMax = totalHist.getMaxX() + 0.5*totalHist.getDelta();
	}

	private <E extends Comparable<E>> void plot(HistogramFunction totalHist, Map<E, HistogramFunction> subHists,
			String title, String legendNameAdd, File outputDir, String prefix) throws IOException {
		List<E> keys = new ArrayList<>(subHists.keySet());
		Collections.sort(keys);
		
		CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
		cpt = cpt.rescale(0d, Double.max(1d, keys.size()-1d));
		
		HistogramFunction runningHist = new HistogramFunction(totalHist.getMinX(), totalHist.getMaxX(), totalHist.size());
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		for (int i=0; i<keys.size(); i++) {
			E key = keys.get(i);
			HistogramFunction hist = subHists.get(key);
			for (int j=0; j<hist.size(); j++)
				runningHist.add(j, hist.getY(j));
			
			EvenlyDiscretizedFunc clone = runningHist.deepClone();
			clone.setName(legendNameAdd+key);
			
			funcs.add(clone);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, cpt.getColor((float)i)));
		}
		
		funcs.add(totalHist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GRAY));
		
		PlotSpec plot = new PlotSpec(funcs, chars, title, "Event Count", "Num Simulations");
		plot.setLegendVisible(true);
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
//		widget.setX_AxisRange(new Range(0d, totalHist.getMaxX()+0.5*totalHist.getDelta()));
		gp.drawGraphPanel(plot, false, false, new Range(0d, totalHist.getMaxX()+0.5*totalHist.getDelta()), null);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
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
	protected synchronized void doProcessCatalog(ETAS_Catalog completeCatalog, ETAS_Catalog triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		if (garlockRups == null) {
			garlockRups = new HashSet<>();
			FaultSystemRupSet rupSet = fss.getRupSet();
			for (FaultSection sect : rupSet.getFaultSectionDataList()) {
				if (sect.getName().startsWith("Garlock"))
					garlockRups.addAll(rupSet.getRupturesForSection(sect.getSectionId()));
			}
		}
		ETAS_SimulationMetadata meta = completeCatalog.getSimulationMetadata();
//		System.out.println("processed "+count);
		int numRups, numSupra;
		double maxMag;
		boolean garlock;
		if (meta == null) {
			numRups = completeCatalog.size();
			numSupra = 0;
			maxMag = 0;
			garlock = false;
			for (ETAS_EqkRupture rup : completeCatalog) {
				if (rup.getFSSIndex() >= 0) {
					numSupra++;
					garlock = garlock || garlockRups.contains(rup.getFSSIndex());
				}
				maxMag = Math.max(maxMag, rup.getMag());
			}
		} else {
			numRups = meta.totalNumRuptures;
			numSupra = meta.numSupraSeis;
			maxMag = meta.maxMag;
			garlock = false;
			for (int i=0; i<completeCatalog.size() && !garlock && numSupra > 0; i++) {
				int fssIndex = completeCatalog.get(i).getFSSIndex();
				garlock = garlock || (fssIndex > 0 && garlockRups.contains(fssIndex));
			}
		}
		if (numRups > binMax)
			return;
		double maxK = Double.NEGATIVE_INFINITY;
		for (ETAS_EqkRupture rup : completeCatalog) {
			if (rup.getMag() < 5)
				continue;
			maxK = Math.max(maxK, Math.log10(rup.getETAS_k()));
		}
		int xInd = totalHist.getClosestXIndex(numRups);
		maxRuptureCount = Integer.max(maxRuptureCount, numRups);
		
		totalHist.add(xInd, 1d);
		
		int maxMagInt = (int)maxMag;
		
		HistogramFunction maxMagHist = maxMagHists.get(maxMagInt);
		if (maxMagHist == null) {
			maxMagHist = new HistogramFunction(totalHist.getMinX(), totalHist.getMaxX(), totalHist.size());
			maxMagHists.put(maxMagInt, maxMagHist);
		}
		maxMagHist.add(xInd, 1d);
		
		HistogramFunction numSupraHist = numSupraHists.get(numSupra);
		if (numSupraHist == null) {
			numSupraHist = new HistogramFunction(totalHist.getMinX(), totalHist.getMaxX(), totalHist.size());
			numSupraHists.put(numSupra, numSupraHist);
		}
		numSupraHist.add(xInd, 1d);
		
		HistogramFunction garlockHist = garlockHists.get(garlock);
		if (garlockHist == null) {
			garlockHist = new HistogramFunction(totalHist.getMinX(), totalHist.getMaxX(), totalHist.size());
			garlockHists.put(garlock, garlockHist);
		}
		garlockHist.add(xInd, 1d);
		
		if (Double.isFinite(maxK)) {
			int kInt = (int)maxK;
			
			HistogramFunction kHist = kHists.get(kInt);
			if (kHist == null) {
				kHist = new HistogramFunction(totalHist.getMinX(), totalHist.getMaxX(), totalHist.size());
				kHists.put(kInt, kHist);
			}
			kHist.add(xInd, 1d);
		}
	}

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
		System.out.println("Maximum rupture count: "+maxRuptureCount);
		
		plot(totalHist, maxMagHists, "Event Count by Max Mag", "Mmax >= ",
				outputDir, "num_events_hist_max_mag");
		plot(totalHist, numSupraHists, "Event Count by Num Supra", "Nsupra = ",
				outputDir, "num_events_hist_num_supra");
		plot(totalHist, garlockHists, "Event Count by Garlock Supraseismogenic", "Garlock ? ",
				outputDir, "num_events_hist_garlock");
		plot(totalHist, kHists, "Event Count by Max K for M>=5", "Log10(k) > ",
				outputDir, "num_events_hist_max_k");
		return null;
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add(topLevelHeading+" Total Number Histogram Disaggregation");
		lines.add(topLink); lines.add("");
		
		lines.add(topLevelHeading+"# By Maximum Magnitude");
		lines.add(topLink); lines.add("");
		
		lines.add("![plot]("+relativePathToOutputDir+"/num_events_hist_max_mag.png)");
		lines.add("");
		
		lines.add(topLevelHeading+"# By Num Supraseismogenic");
		lines.add(topLink); lines.add("");
		
		lines.add("![plot]("+relativePathToOutputDir+"/num_events_hist_num_supra.png)");
		lines.add("");
		
		lines.add(topLevelHeading+"# By Garlock Rupture");
		lines.add(topLink); lines.add("");
		
		lines.add("![plot]("+relativePathToOutputDir+"/num_events_hist_garlock.png)");
		lines.add("");
		
		lines.add(topLevelHeading+"# By Max K values for M>=5");
		lines.add(topLink); lines.add("");
		
		lines.add("![plot]("+relativePathToOutputDir+"/num_events_hist_max_k.png)");
		lines.add("");
		
		return lines;
	}

}
