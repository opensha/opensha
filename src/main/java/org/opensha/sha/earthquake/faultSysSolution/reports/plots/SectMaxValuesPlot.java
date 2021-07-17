package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalarValues;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import com.google.common.base.Preconditions;

public class SectMaxValuesPlot extends AbstractRupSetPlot {
	
	private static final List<HistScalar> defaultMaxScalars = List.of(HistScalar.LENGTH, HistScalar.MAG,
			HistScalar.CUM_JUMP_DIST, HistScalar.CUM_RAKE_CHANGE, HistScalar.CUM_AZ_CHANGE);
	
	private List<HistScalar> scalars;
	
	public SectMaxValuesPlot() {
		this(defaultMaxScalars);
	}

	public SectMaxValuesPlot(List<HistScalar> scalars) {
		this.scalars = scalars;
	}

	@Override
	public String getName() {
		return "Subsection Maximum Values";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("These plots show the maximum value of various quantities across all ruptures for which "
				+ "each individual subsection participates. This is useful, for example, to find sections "
				+ "with low maximum magnitudes (due to low or no connectivity).");
		lines.add("");
		
		Map<HistScalar, HistScalarValues> imputVals = getInputs(meta.primary);
		Map<HistScalar, HistScalarValues> compVals = null;
		if (meta.comparison != null && meta.comparison.rupSet.hasModule(ClusterRuptures.class))
			compVals = getInputs(meta.comparison);
		for (HistScalar scalar : scalars) {
			HistScalarValues inputScalars = imputVals.get(scalar);
			HistScalarValues compScalars = compVals == null ? null : compVals.get(scalar);
			lines.add(getSubHeading()+" Subsection Maximum "+scalar.getName());
			lines.add(topLink); lines.add("");
			TableBuilder table = MarkdownUtils.tableBuilder();
			if (compScalars != null)
				table.addLine(meta.primary.name, meta.comparison.name);
			String prefix = "sect_max_"+scalar.name();
			try {			
				if (!plotScalarMaxMapView(rupSet, resourcesDir, prefix, getTruncatedTitle(meta.primary.name),
						inputScalars, compScalars, meta.region, MAIN_COLOR, false, false))
					continue;
			} catch (Exception err) {
				System.out.println("Caught exception: " + err.getLocalizedMessage());
				continue;
			}
			table.initNewLine();
			table.addColumn("![map]("+relPathToResources+"/"+prefix+".png)");
			if (compScalars != null) {
				plotScalarMaxMapView(meta.comparison.rupSet, resourcesDir, prefix+"_comp", getTruncatedTitle(meta.comparison.name),
						compScalars, inputScalars, meta.region, COMP_COLOR, false, false);
				table.addColumn("![map]("+relPathToResources+"/"+prefix+"_comp.png)");
			}
			table.finalizeLine().initNewLine();
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+".geojson")
					+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+".geojson)");
			if (compScalars != null)
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_comp.geojson")
						+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_comp.geojson)");
			table.finalizeLine().initNewLine();
			table.addColumn("![map]("+relPathToResources+"/"+prefix+"_hist.png)");
			if (compScalars != null) {
				table.addColumn("![map]("+relPathToResources+"/"+prefix+"_comp_hist.png)");
			}
			table.finalizeLine();
			if (compScalars != null) {
				table.addLine("**Difference**", "**Ratio**");
				table.initNewLine();
				plotScalarMaxMapView(rupSet, resourcesDir, prefix+"_diff", "Difference",
						inputScalars, compScalars, meta.region, MAIN_COLOR, true, false);
				table.addColumn("![map]("+relPathToResources+"/"+prefix+"_diff.png)");
				plotScalarMaxMapView(rupSet, resourcesDir, prefix+"_ratio", "Ratio",
						inputScalars, compScalars, meta.region, MAIN_COLOR, false, true);
				table.addColumn("![map]("+relPathToResources+"/"+prefix+"_ratio.png)");
				table.finalizeLine().initNewLine();
				table.addColumn("![map]("+relPathToResources+"/"+prefix+"_diff_hist.png)");
				table.addColumn("![map]("+relPathToResources+"/"+prefix+"_ratio_hist.png)");
				table.finalizeLine();
			}
			lines.addAll(table.build());
			lines.add("");
		}
		return lines;
	}
	
	private Map<HistScalar, HistScalarValues> getInputs(RupSetMetadata meta) {
		Map<HistScalar, HistScalarValues> ret = new HashMap<>();
		for (HistScalar scalar : scalars) {
			HistScalarValues values = null;
			// see if we already have it
			for (HistScalarValues oVals : meta.scalarValues) {
				if (oVals.getScalar() == scalar) {
					values = oVals;
					break;
				}
			}
			
			if (values == null)
				// calculate it
				values = new HistScalarValues(scalar, meta.rupSet, meta.sol,
						meta.rupSet.requireModule(ClusterRuptures.class).getAll(),
						meta.rupSet.requireModule(SectionDistanceAzimuthCalculator.class));
			ret.put(scalar, values);
		}
		return ret;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(SectionDistanceAzimuthCalculator.class, ClusterRuptures.class);
	}

	@Override
	public List<String> getSummary(ReportMetadata meta, File resourcesDir, String relPathToResources, String topLink) {
		if (!new File(resourcesDir, "sect_max_LENGTH.png").exists())
			return null;
		List<String> lines = new ArrayList<>();
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		if (meta.comparison != null) {
			table.addLine(meta.primary.name, meta.comparison.name);
			table.addLine("![Primary]("+relPathToResources+"/sect_max_LENGTH.png)", "![Comparison]("+relPathToResources+"/sect_max_LENGTH_comp.png)");
			table.initNewLine();
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/sect_max_LENGTH.geojson")
					+" "+"[Download GeoJSON]("+relPathToResources+"/sect_max_LENGTH.geojson)");
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/sect_max_LENGTH_comp.geojson")
					+" "+"[Download GeoJSON]("+relPathToResources+"/sect_max_LENGTH_comp.geojson)");
			table.finalizeLine();
			table.addLine("![Primary]("+relPathToResources+"/sect_max_LENGTH_diff.png)", "![Comparison]("+relPathToResources+"/sect_max_LENGTH_ratio.png)");
		} else {
			table.addLine("![Primary]("+relPathToResources+"/hist_LENGTH.png)");
			table.addLine("![Primary]("+relPathToResources+"/sect_max_LENGTH.png)");
			table.addLine(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/sect_max_LENGTH.geojson")
					+" "+"[Download GeoJSON]("+relPathToResources+"/sect_max_LENGTH.geojson)");
		}
		lines.addAll(table.build());
		return lines;
	}
	
	public static boolean plotScalarMaxMapView(FaultSystemRupSet rupSet, File outputDir, String prefix,
			String title, HistScalarValues scalarVals, HistScalarValues compScalarVals, Region reg,
			Color mainColor, boolean difference, boolean ratio) throws IOException {
		
		List<Double> values = new ArrayList<>();
		for (int s=0; s<rupSet.getNumSections(); s++)
			values.add(Double.NEGATIVE_INFINITY);
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			double value = scalarVals.getValues().get(r);
			for (int s : rupSet.getSectionsIndicesForRup(r)) {
				double prevMax = values.get(s);
				if (value > prevMax)
					values.set(s, value);
			}
		}
		MinMaxAveTracker maxTrack = new MinMaxAveTracker();
		for (double value : values)
			if (Double.isFinite(value))
				maxTrack.addValue(value);
		if (maxTrack.getNum() == 0)
			return false;
		Preconditions.checkState(compScalarVals != null || (!difference && !ratio));
		if (compScalarVals != null) {
			// for consistent ranges
			List<Double> compValues = new ArrayList<>();
			for (int s=0; s<compScalarVals.getRupSet().getNumSections(); s++)
				compValues.add(Double.NEGATIVE_INFINITY);
			for (int r=0; r<compScalarVals.getRupSet().getNumRuptures(); r++) {
				double value = compScalarVals.getValues().get(r);
				for (int s : compScalarVals.getRupSet().getSectionsIndicesForRup(r)) {
					double prevMax = compValues.get(s);
					if (value > prevMax)
						compValues.set(s, value);
				}
			}
			for (double value : compValues)
				if (Double.isFinite(value))
					maxTrack.addValue(value);
			if (difference) {
				Preconditions.checkState(compValues.size() == values.size());
				for (int i=0; i<values.size(); i++)
					values.set(i, values.get(i) - compValues.get(i));
			} else if (ratio) {
				Preconditions.checkState(compValues.size() == values.size());
				for (int i=0; i<values.size(); i++)
					values.set(i, values.get(i) / compValues.get(i));
			}
		}
		
		HistScalar histScalar = scalarVals.getScalar();
		
		HistogramFunction hist;
		CPT cpt;
		if (difference) {
			double maxAbsDiff = 0d;
			for (double value : values)
				if (Double.isFinite(value))
					maxAbsDiff = Math.max(maxAbsDiff, Math.abs(value));
			if (histScalar == HistScalar.MAG)
				maxAbsDiff = Math.min(maxAbsDiff, 2d);
			maxAbsDiff = Math.ceil(maxAbsDiff);
			if (maxAbsDiff == 0d)
				maxAbsDiff = 1;
			double delta = histScalar == HistScalar.CLUSTER_COUNT ? 1 : maxAbsDiff / 10d;
			int num = 2*(int)(maxAbsDiff/delta)+1;
			hist = new HistogramFunction(-maxAbsDiff - 0.5*delta, num, delta);
			cpt = new CPT(-maxAbsDiff, maxAbsDiff,
					new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
					Color.WHITE,
					new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
		} else if (ratio) {
			if (histScalar == HistScalar.MAG)
				hist = new HistogramFunction(0.666667d, 1.5d, 30);
			else
				hist = new HistogramFunction(0.5d, 2d, 30);
			CPT belowCPT = new CPT(hist.getMinX(), 1d,
					new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
					Color.WHITE);
			CPT aboveCPT = new CPT(1d, hist.getMaxX(),
					Color.WHITE,
					new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
			cpt = new CPT();
			cpt.addAll(belowCPT);
			cpt.addAll(aboveCPT);
		} else {
			hist = histScalar.getHistogram(maxTrack);
			cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance();
			if (hist.size() == 1)
				cpt = cpt.rescale(hist.getX(0), hist.getX(0) <= 0d ? 1d : hist.getX(0)*1.5);
			else
				cpt = cpt.rescale(hist.getMinX() - 0.5*hist.getDelta(), hist.getMaxX() + 0.5*hist.getDelta());
		}
		cpt.setBelowMinColor(cpt.getMinColor());
		cpt.setAboveMaxColor(cpt.getMaxColor());
		
		RupSetMapMaker plotter = new RupSetMapMaker(rupSet, reg);
		plotter.setWriteGeoJSON(!prefix.contains("_ratio") && !prefix.contains("_diff"));
		
		String cptTitle = "Section Max Participating "+histScalar.getxAxisLabel();
		if (difference)
			cptTitle += ", Difference";
		if (histScalar.isLogX())
			cptTitle = "Log10 "+cptTitle;
		
		if (histScalar.isLogX()) {
			for (int i=0; i<values.size(); i++)
				values.set(i, Math.log10(values.get(i)));
		}
		
		plotter.plotSectScalars(values, cpt, cptTitle);
		plotter.plot(outputDir, prefix, title);
		
		// plot histogram now
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		for (double value : values) {
			int index;
			if (histScalar.isLogX() && value <= 0d)
				index = 0;
			else
				index = hist.getClosestXIndex(value);
			hist.add(index, 1d);
		}
		
		if (histScalar.isLogX()) {
			ArbitrarilyDiscretizedFunc linearHist = new ArbitrarilyDiscretizedFunc();
			for (Point2D pt : hist)
				linearHist.set(Math.pow(10, pt.getX()), pt.getY());
			
			funcs.add(linearHist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, mainColor));
		} else {
			funcs.add(hist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, mainColor));
		}
		
		String xAxisLabel = "Section Max "+histScalar.getxAxisLabel();
		if (difference)
			xAxisLabel += ", Difference";
		String yAxisLabel = "Count";
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
		
		Range xRange;
		if (histScalar.isLogX())
			xRange = new Range(Math.pow(10, hist.getMinX() - 0.5*hist.getDelta()),
					Math.pow(10, hist.getMaxX() + 0.5*hist.getDelta()));
		else
			xRange = new Range(hist.getMinX() - 0.5*hist.getDelta(),
					hist.getMaxX() + 0.5*hist.getDelta());
		Range yRange = new Range(0, 1.05*hist.getMaxY());
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);
		
		gp.drawGraphPanel(spec, histScalar.isLogX(), false, xRange, yRange);
		gp.getChartPanel().setSize(800, 600);
		prefix += "_hist";
		File pngFile = new File(outputDir, prefix+".png");
		File pdfFile = new File(outputDir, prefix+".pdf");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		
		return true;
	}

}
