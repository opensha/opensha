package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

import com.google.common.base.Preconditions;

public class RuptureScalingPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Rupture Scaling";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		Scatters primary = new Scatters(rupSet);
		Scatters comp = meta.comparison == null ? null : new Scatters(meta.comparison.rupSet);
		
		boolean hasSlips = primary.slipMags != null;
		List<String> lines = new ArrayList<>();
		if (hasSlips) {
			lines.add(getSubHeading()+" Magnitude Scaling");
			lines.add(topLink); lines.add("");
		}
		
		int threads = getNumThreads();
		ExecutorService exec = null;
		List<Future<?>> futures = null;
		if (threads > 1) {
			exec = Executors.newFixedThreadPool(threads);
			futures = new ArrayList<>();
		}
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		table.addLine("Linear Plots", "Log10 Plots");
		
		table.initNewLine();
		File plot = plot(resourcesDir, "mag_area_scaling", " ", primary.magAreas, meta.primary.name,
				comp == null ? null : comp.magAreas, comp == null ? null : meta.comparison.name,
						"Area (m²)", false, "Magnitude", false, exec, futures);
		table.addColumn("![Mag/Area]("+relPathToResources+"/"+plot.getName()+")");
		plot = plot(resourcesDir, "mag_log_area_scaling", " ", primary.magAreas, meta.primary.name,
				comp == null ? null : comp.magAreas, comp == null ? null : meta.comparison.name,
						"Area (m²)", true, "Magnitude", false, exec, futures);
		table.addColumn("![Mag/Area]("+relPathToResources+"/"+plot.getName()+")");
		table.finalizeLine();
		
		if (primary.magLengths != null) {
			table.initNewLine();
			plot = plot(resourcesDir, "mag_length_scaling", " ", primary.magLengths, meta.primary.name,
					comp == null ? null : comp.magLengths, comp == null ? null : meta.comparison.name,
							"Length (m)", false, "Magnitude", false, exec, futures);
			table.addColumn("![Mag/Length]("+relPathToResources+"/"+plot.getName()+")");
			plot = plot(resourcesDir, "mag_log_length_scaling", " ", primary.magLengths, meta.primary.name,
					comp == null ? null : comp.magLengths, comp == null ? null : meta.comparison.name,
							"Length (m)", true, "Magnitude", false, exec, futures);
			table.addColumn("![Mag/Length]("+relPathToResources+"/"+plot.getName()+")");
			table.finalizeLine();
		}
		
		lines.addAll(table.build());
		lines.add("");
		
		if (hasSlips) {
			lines.add(getSubHeading()+" Slip Scaling");
			lines.add(topLink); lines.add("");
			
			table = MarkdownUtils.tableBuilder();
			
			table.addLine("Linear Plots", "Log10 Plots");
			
			table.initNewLine();
			plot = plot(resourcesDir, "slip_area_scaling", " ", primary.slipAreas, meta.primary.name,
					comp == null ? null : comp.slipAreas, comp == null ? null : meta.comparison.name,
							"Area (m²)", false, "Slip (m)", false, exec, futures);
			table.addColumn("![Slip/Area]("+relPathToResources+"/"+plot.getName()+")");
			plot = plot(resourcesDir, "slip_log_area_scaling", " ", primary.slipAreas, meta.primary.name,
					comp == null ? null : comp.slipAreas, comp == null ? null : meta.comparison.name,
							"Area (m²)", true, "Slip (m)", false, exec, futures);
			table.addColumn("![Slip/Area]("+relPathToResources+"/"+plot.getName()+")");
			table.finalizeLine();
			
			if (primary.slipLengths != null) {
				table.initNewLine();
				plot = plot(resourcesDir, "slip_length_scaling", " ", primary.slipLengths, meta.primary.name,
						comp == null ? null : comp.slipLengths, comp == null ? null : meta.comparison.name,
								"Length (m)", false, "Slip (m)", false, exec, futures);
				table.addColumn("![Slip/Length]("+relPathToResources+"/"+plot.getName()+")");
				plot = plot(resourcesDir, "slip_log_length_scaling", " ", primary.slipLengths, meta.primary.name,
						comp == null ? null : comp.slipLengths, comp == null ? null : meta.comparison.name,
								"Length (m)", true, "Slip (m)", false, exec, futures);
				table.addColumn("![Slip/Length]("+relPathToResources+"/"+plot.getName()+")");
				table.finalizeLine();
			}
			
			table.initNewLine();
			plot = plot(resourcesDir, "slip_mag_scaling", " ", primary.slipMags, meta.primary.name,
					comp == null ? null : comp.slipMags, comp == null ? null : meta.comparison.name,
							"Magnitude", false, "Slip (m)", false, exec, futures);
			table.addColumn("![Slip/Mag]("+relPathToResources+"/"+plot.getName()+")");
			plot = plot(resourcesDir, "log_slip_mag_scaling", " ", primary.slipMags, meta.primary.name,
					comp == null ? null : comp.slipMags, comp == null ? null : meta.comparison.name,
							"Magnitude", false, "Slip (m)", true, exec, futures);
			table.addColumn("![Slip/Mag]("+relPathToResources+"/"+plot.getName()+")");
			table.finalizeLine();
			
			lines.addAll(table.build());
			lines.add("");
		}
		
		if (futures != null) {
			for (Future<?> future : futures) {
				try {
					future.get();
				} catch (InterruptedException | ExecutionException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
			exec.shutdown();
		}
		
		return lines;
	}
	
	private static class Scatters {
		
		private DefaultXY_DataSet magAreas;
		private DefaultXY_DataSet magLengths;
		private DefaultXY_DataSet slipAreas;
		private DefaultXY_DataSet slipLengths;
		private DefaultXY_DataSet slipMags;

		public Scatters(FaultSystemRupSet rupSet) {
			double[] mags = rupSet.getMagForAllRups();
			double[] areas = rupSet.getAreaForAllRups();
			double[] lengths = rupSet.getLengthForAllRups();
			
			magAreas = new DefaultXY_DataSet();
			magLengths = lengths == null ? null : new DefaultXY_DataSet();
			AveSlipModule aveSlips = rupSet.getModule(AveSlipModule.class);
			slipAreas = aveSlips == null ? null : new DefaultXY_DataSet();
			slipLengths = aveSlips == null || lengths == null ? null : new DefaultXY_DataSet();
			slipMags = aveSlips == null ? null : new DefaultXY_DataSet();
			
			for (int r=0; r<rupSet.getNumRuptures(); r++) {
				magAreas.set(areas[r], mags[r]);
				if (magLengths != null)
					magLengths.set(lengths[r], mags[r]);
				if (aveSlips != null) {
					double aveSlip = aveSlips.getAveSlip(r);
					slipAreas.set(areas[r], aveSlip);
					if (slipLengths != null)
						slipLengths.set(lengths[r], aveSlip);
					slipMags.set(mags[r], aveSlip);
				}
			}
		}
	}
	
	private static File plot(File resourcesDir, String prefix, String title, XY_DataSet scatter, String name,
			XY_DataSet compScatter, String compName, String xAxisLabel, boolean xLog, String yAxisLabel, boolean yLog,
			ExecutorService exec, List<Future<?>> futures) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		PlotSymbol symbol = PlotSymbol.CROSS;
		float thickness = 3f;
		float legendThickness = 5f;
		
		funcs.add(scatter);
		chars.add(new PlotCurveCharacterstics(symbol, thickness, color(scatter.size(), MAIN_COLOR)));
		
		if (compScatter != null) {
			funcs.add(compScatter);
			chars.add(new PlotCurveCharacterstics(symbol, thickness, color(compScatter.size(), COMP_COLOR)));
		}
		
		double minX = Double.POSITIVE_INFINITY;
		double maxX = 0d;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = 0d;
		for (XY_DataSet func : funcs) {
			maxX = Math.max(maxX, func.getMaxX());
			maxY = Math.max(maxY, func.getMaxY());
			if (xLog) {
				for (Point2D pt : func)
					if (pt.getX() > 0d)
						minX = Math.min(minX, pt.getX());
			} else {
				minX = Math.min(minX, func.getMinX());
			}
			if (yLog) {
				for (Point2D pt : func)
					if (pt.getY() > 0d)
						minY = Math.min(minY, pt.getY());
			} else {
				minY = Math.min(minY, func.getMinY());
			}
		}
		Preconditions.checkState(minX < maxX, "minX=%s >= maxX=%s for plot %s", minX, maxX, prefix);
		Preconditions.checkState(minY < maxY, "minY=%s >= maxY=%s for plot %s", minY, maxY, prefix);
		
		Range xRange = cleanRange(minX, maxX, xLog);
		Range yRange = cleanRange(minY, maxY, yLog);
		
		if (compScatter != null) {
			// add non-transparent fake functions for legend
			if (name != null) {
				DefaultXY_DataSet fakeXY = new DefaultXY_DataSet();
				fakeXY.setName(name);
				fakeXY.set(xRange.getLowerBound() - 10d, yRange.getLowerBound() - 10d);
				
				funcs.add(0, fakeXY);
				chars.add(0, new PlotCurveCharacterstics(symbol, legendThickness, MAIN_COLOR));
			}
			
			if (compName != null) {
				DefaultXY_DataSet fakeXY = new DefaultXY_DataSet();
				fakeXY.setName(compName);
				fakeXY.set(xRange.getLowerBound() - 10d, yRange.getLowerBound() - 10d);
				
				funcs.add(0, fakeXY);
				chars.add(0, new PlotCurveCharacterstics(symbol, legendThickness, COMP_COLOR));
			}
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
		spec.setLegendVisible(compScatter != null);
		Runnable plotRun = new Runnable() {
			
			@Override
			public void run() {
				HeadlessGraphPanel gp = PlotUtils.initHeadless();
				
				// primary on top
				gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
				
				gp.drawGraphPanel(spec, xLog, yLog, xRange, yRange);
				
				try {
					PlotUtils.writePlots(resourcesDir, prefix, gp, 800, 700, true, false, false);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		};
		
		if (exec == null)
			plotRun.run();
		else
			futures.add(exec.submit(plotRun));
		
		return new File(resourcesDir, prefix+".png");
	}
	
	private static Color color(int num, Color baseColor) {
		int alpha;
		if (num > 500000)
			alpha = 20;
		if (num > 200000)
			alpha = 40;
		if (num > 100000)
			alpha = 60;
		else if (num > 50000)
			alpha = 80;
		else if (num > 20000)
			alpha = 120;
		else if (num > 10000)
			alpha = 160;
		else
			alpha = 255;
		return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
	}
	
	private static Range cleanRange(double min, double max, boolean log) {
		if (log)
			return new Range(Math.pow(10, Math.floor(Math.log10(min))), Math.pow(10, Math.ceil(Math.log10(max))));
		double scalar;
		if (max > 1000d)
			scalar = 500d;
		else if (max > 100d)
			scalar = 50d;
		else if (max > 20d)
			scalar = 5d;
		else if (max > 10d)
			scalar = 2d;
		else
			scalar = 1d;
		
		max = scalar*Math.ceil(max/scalar);
		min = min == 0 ? 0 : scalar*Math.floor(min/scalar);
		return new Range(min, max);
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		// TODO Auto-generated method stub
		return null;
	}

	public static void main(String[] args) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2022_08_15-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-U3_MEAN-MeanU3Scale-DsrUni-TotNuclRate-"
				+ "SubB1-ThreshAvgIterRelGR/results_FM3_1_CoulombRupSet_branch_averaged.zip"));
//		FaultSystemRupSet compRupSet = null;
		FaultSystemRupSet compRupSet = FaultSystemRupSet.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2022_08_15-nshm23_branches-NSHM23_v2-CoulombRupSet-AVERAGE-NSHM23_Avg-TotNuclRate-SubB1-ThreshAvgIterRelGR"
				+ "/results_NSHM23_v2_CoulombRupSet_branch_averaged.zip"));
		
		ReportMetadata meta;
		if (compRupSet == null)
			meta = new ReportMetadata(new RupSetMetadata("Rupture Set", rupSet));
		else
			meta = new ReportMetadata(new RupSetMetadata("Rupture Set", rupSet), new RupSetMetadata("Comparison Rup Set", compRupSet));
		
		ReportPageGen pageGen = new ReportPageGen(meta, new File("/tmp/test_plot"), List.of(new RuptureScalingPlot()));
		
		pageGen.setReplot(true);
		pageGen.generatePage();
	}

}
