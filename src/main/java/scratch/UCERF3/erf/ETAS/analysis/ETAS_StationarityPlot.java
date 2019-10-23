package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator.PlotResult;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class ETAS_StationarityPlot extends ETAS_AbstractPlot {
	
	public static final double MIN_SIM_DURATION = 99;
	
	private static final double YEARS_PER_BIN = 10;
	
	private static final int MAX_TABLE_ROWS = 20;
	
	private EvenlyDiscretizedFunc xVals;
	private List<EvenlyDiscretizedFunc[]> simFuncs;
	
	private double[] mags = { 2.5d, 5d, 6d, 7d };
	private double plotMinMag;
	
	private HistogramFunction totalCountHist;

	private String prefix;
	
	protected ETAS_StationarityPlot(ETAS_Config config, ETAS_Launcher launcher, String prefix) {
		super(config, launcher);
		this.prefix = prefix;
		
		Preconditions.checkState(!config.hasTriggers(), "Stationarity plot not applicable to aftershock catalogs");
		
		double totDuration = config.getDuration();
		
		int numX = (int)(totDuration/YEARS_PER_BIN);
		
		xVals = new EvenlyDiscretizedFunc(0.5*YEARS_PER_BIN, numX, YEARS_PER_BIN);
		simFuncs = new ArrayList<>();
		
		totalCountHist = new HistogramFunction(ETAS_MFD_Plot.mfdMinMag, ETAS_MFD_Plot.mfdNumMag, ETAS_MFD_Plot.mfdDelta);
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public boolean isFilterSpontaneous() {
		return false;
	}

	@Override
	protected void doProcessCatalog(ETAS_Catalog completeCatalog, ETAS_Catalog triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		long simStartTime = getConfig().getSimulationStartTimeMillis();
		
		EvenlyDiscretizedFunc[] magFuncs = new EvenlyDiscretizedFunc[mags.length];
		for (int m=0; m<mags.length; m++)
			magFuncs[m] = new EvenlyDiscretizedFunc(xVals.getMinX(), xVals.getMaxX(), xVals.size());
		
		double maxX = xVals.getMaxX() + 0.5*xVals.getDelta();
		
		for (ETAS_EqkRupture rup : completeCatalog) {
			totalCountHist.add(totalCountHist.getClosestXIndex(rup.getMag()), 1d);
			
			double relativeTime = (double)(rup.getOriginTime() - simStartTime) / ProbabilityModelsCalc.MILLISEC_PER_YEAR;
			if (relativeTime > maxX)
				continue;
			int timeX = xVals.getClosestXIndex(relativeTime);
			
			for (int m=0; m<mags.length; m++)
				if (rup.getMag() >= mags[m])
					magFuncs[m].add(timeX, 1d);
		}
		
		simFuncs.add(magFuncs);
	}

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
		int numToTrim = ETAS_MFD_Plot.calcNumToTrim(totalCountHist);
		plotMinMag = totalCountHist.getX(numToTrim)-totalCountHist.getDelta();
		
		List<EvenlyDiscretizedFunc> magFuncs = new ArrayList<>();
		List<Double> means = new ArrayList<>();
		
		for (int m=0; m<mags.length; m++) {
			if (mags[m] < plotMinMag)
				continue;
			EvenlyDiscretizedFunc meanFunc = new EvenlyDiscretizedFunc(xVals.getMinX(), xVals.getMaxX(), xVals.size());
			for (EvenlyDiscretizedFunc[] simFunc : simFuncs)
				for (int i=0; i<xVals.size(); i++)
					meanFunc.add(i, simFunc[m].getY(i));
			meanFunc.scale(1d/(double)simFuncs.size());
			// now annualize
			meanFunc.scale(1d/YEARS_PER_BIN);
			meanFunc.setName("M≥"+(float)mags[m]);
			magFuncs.add(meanFunc);
			double mean = meanFunc.calcSumOfY_Vals()/(double)meanFunc.size();
			means.add(mean);
			System.out.println("Mean "+meanFunc.getName()+": "+mean);
		}
		
		CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0d, Math.max(1, magFuncs.size()-1));
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		double halfDelta = xVals.getDelta()*0.5;
		double maxX = xVals.getMaxX()+halfDelta;
		double minNonZeroY = Double.POSITIVE_INFINITY;
		double maxY = 0;
		for (int i=0; i<magFuncs.size(); i++) {
			EvenlyDiscretizedFunc meanFunc = magFuncs.get(i);
			XY_DataSet xy = new DefaultXY_DataSet();
			for (int x=0; x<meanFunc.size(); x++) {
				double y = meanFunc.getY(x);
				if (y > 0) {
					maxY = Math.max(y, maxY);
					minNonZeroY = Math.min(y, minNonZeroY);
					double middle = meanFunc.getX(x);
					xy.set(middle-halfDelta, y);
					xy.set(middle+halfDelta, y);
				}
			}
			if (xy.size() == 0)
				continue;
			funcs.add(xy);
			Color c = cpt.getColor((float)i).darker();
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, c));
			
			double mean = means.get(i);
			xy.setName(meanFunc.getName()+", mean="+(float)mean);
			XY_DataSet meanXY = new DefaultXY_DataSet();
			meanXY.set(0d, mean);
			meanXY.set(maxX, mean);
			funcs.add(meanXY);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, c));
		}
		
		String title = "ETAS Simulation Stationarity";
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Simulated Years", "Annual Rate");
		spec.setLegendVisible(true);
		
		Range xRange = new Range(0d, maxX);
		System.out.println("Y range: "+minNonZeroY+" "+maxY);
		Range yRange = getYRange(minNonZeroY, maxY);
		
		HeadlessGraphPanel gp = buildGraphPanel();
//		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);

		gp.drawGraphPanel(spec, false, true, xRange, yRange);
		gp.getChartPanel().setSize(1000, 700);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
		return null;
	}
	
	private static Range getYRange(double minY, double maxY) {
		// pad them by a factor of 2
		minY /= 2;
		maxY *= 2;
		
		// now encompassing log10 range
		minY = Math.pow(10, Math.floor(Math.log10(minY)));
		maxY = Math.pow(10, Math.ceil(Math.log10(maxY)));
		
		return new Range(minY, maxY);
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add(topLevelHeading+" Simulation Stationarity");
		lines.add(topLink); lines.add("");
		
		lines.add("![Stationarity Plot]("+relativePathToOutputDir+"/"+prefix+".png)");
		lines.add("");
		
		return lines;
	}

}
