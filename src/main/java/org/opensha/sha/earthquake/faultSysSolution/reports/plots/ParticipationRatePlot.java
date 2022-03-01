package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.SolidFillPlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;

public class ParticipationRatePlot extends AbstractSolutionPlot implements SolidFillPlot {

	boolean fillSurfaces = false;

	@Override
	public void setFillSurfaces(boolean fillSurfaces){
		this.fillSurfaces = fillSurfaces;
	}

	@Override
	public String getName() {
		return "Participation Rates";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		FaultSystemRupSet rupSet = sol.getRupSet();
		double minMag = rupSet.getMinMag();
		double maxMag = rupSet.getMaxMag();
		
		List<Double> minMags = new ArrayList<>();
		List<String> magLabels = new ArrayList<>();
		List<String> magPrefixes = new ArrayList<>();
		
		minMags.add(0d);
		magLabels.add("Supra-Seismogenic");
		magPrefixes.add("supra_seis");
		
		if (maxMag > 6d && minMag <= 6d) {
			minMags.add(6d);
			magLabels.add("M≥6");
			magPrefixes.add("m6");
		}
		
		if (maxMag > 7d && minMag <= 7d) {
			minMags.add(7d);
			magLabels.add("M≥7");
			magPrefixes.add("m7");
		}
		
		if (maxMag > 8d) {
			minMags.add(8d);
			magLabels.add("M≥8");
			magPrefixes.add("m8");
		}
		
		if (maxMag > 9d) {
			minMags.add(9d);
			magLabels.add("M≥9");
			magPrefixes.add("m9");
		}
		
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-5, 0);
		cpt.setNanColor(Color.GRAY);
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(sol.getRupSet(), meta.region);
		mapMaker.setWriteGeoJSON(true);
		mapMaker.setFillSurfaces(fillSurfaces);
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		CPT ratioCPT = null;
		
		if (meta.comparison != null && meta.comparison.sol != null) {
			CPT belowCPT = new CPT(0.5d, 1d,
					new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
					Color.WHITE);
			CPT aboveCPT = new CPT(1d, 2d,
					Color.WHITE,
					new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
			ratioCPT = new CPT();
			ratioCPT.addAll(belowCPT);
			ratioCPT.addAll(aboveCPT);
			ratioCPT.setNanColor(Color.GRAY);
			ratioCPT.setBelowMinColor(ratioCPT.getMinColor());
			ratioCPT.setAboveMaxColor(ratioCPT.getMaxColor());
		}
		for (int m=0; m<minMags.size(); m++) {
			double myMinMag = minMags.get(m);
			
			double[] rates = sol.calcParticRateForAllSects(myMinMag, Double.POSITIVE_INFINITY);
			
			String myLabel = magLabels.get(m);
			String markdownLabel = myLabel.replaceAll("≥", "&ge;");
			String magPrefix = magPrefixes.get(m);
			
			table.initNewLine();
			table.addColumn(MarkdownUtils.boldCentered(markdownLabel));
			if (m < minMags.size()-1) {
				String rangeLabel;
				if (myMinMag == 0d)
					rangeLabel = "M&le;"+optionalDigitDF.format(minMags.get(m+1));
				else
					rangeLabel = "M&isin;["+optionalDigitDF.format(myMinMag)+", "+optionalDigitDF.format(minMags.get(m+1))+"]";
				table.addColumn(MarkdownUtils.boldCentered("Range: "+rangeLabel));
			} else {
				table.addColumn("");
			}
			table.finalizeLine();
			
			List<String> prefixes = new ArrayList<>();
			
			String mainPrefix = "sol_partic_"+magPrefix;
			double[] plotRates = log10(maskSectsOutsideMagRange(rates, sol.getRupSet(), myMinMag, Double.POSITIVE_INFINITY));
			mapMaker.plotSectScalars(plotRates, cpt, "Log10 "+myLabel+" Participation Rate (events/yr)");
			mapMaker.plot(resourcesDir, mainPrefix, " ");
			prefixes.add(mainPrefix);

			table.initNewLine();
			table.addColumn("![Map]("+relPathToResources+"/"+mainPrefix+".png)");
			
			if (m < minMags.size()-1) {
				double upperMag = minMags.get(m+1);
				double[] range = sol.calcParticRateForAllSects(myMinMag, upperMag);
				String rangePrefix = "sol_partic_"+magPrefix+"_to_"+magPrefixes.get(m+1);
				String label = "Log10 ";
				if (myMinMag == 0d)
					label += myLabel+" -> M"+optionalDigitDF.format(upperMag);
				else
					label += "M"+optionalDigitDF.format(myMinMag)+" -> "+optionalDigitDF.format(upperMag);
				label += " Participation Rate (events/yr)";
				
				double[] plotRange = log10(maskSectsOutsideMagRange(range, sol.getRupSet(), myMinMag, upperMag));
				mapMaker.plotSectScalars(plotRange, cpt, label);
				mapMaker.plot(resourcesDir, rangePrefix, " ");
				
				table.addColumn("![Map]("+relPathToResources+"/"+rangePrefix+".png)");
				prefixes.add(rangePrefix);
			} else {
				table.addColumn("");
				prefixes.add(null);
			}
			
			table.finalizeLine();
			
			table.initNewLine();
			for (String prefix : prefixes) {
				if (prefix == null)
					table.addColumn("");
				else
					table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+".geojson")
							+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+".geojson)");
			}
			table.finalizeLine();
			
			if (ratioCPT != null) {
				table.addLine(MarkdownUtils.boldCentered(markdownLabel+" Comparison Ratio"),
						MarkdownUtils.boldCentered(markdownLabel+" Comparison Scatter"));
				table.initNewLine();
				
				double[] compRates = meta.comparison.sol.calcParticRateForAllSects(myMinMag, Double.POSITIVE_INFINITY);
				double[] ratios = new double[compRates.length];
				for (int i=0; i<ratios.length; i++)
					ratios[i] = rates[i]/compRates[i];
				
				String compPrefix = "sol_partic_compare_"+magPrefix;
				mapMaker.plotSectScalars(ratios, ratioCPT, myLabel+" Primary/Comparison Participation Ratio");
				mapMaker.plot(resourcesDir, compPrefix, " ");

				table.addColumn("![Map]("+relPathToResources+"/"+compPrefix+".png)");
				prefixes.add(compPrefix);
				
				File scatter = compScatterPlot(resourcesDir, compPrefix+"_scatter", rates, compRates, myLabel);
				table.addColumn("![Map]("+relPathToResources+"/"+scatter.getName()+")");
				
				table.finalizeLine().initNewLine();
				
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+compPrefix+".geojson")
						+" "+"[Download GeoJSON]("+relPathToResources+"/"+compPrefix+".geojson)");
				table.addColumn("");
				
				table.finalizeLine();
			}
		}
		
		return table.build();
	}
	
	private double[] log10(double[] vals) {
		double[] ret = new double[vals.length];
		for (int i=0; i<ret.length; i++)
			ret[i] = Math.log10(vals[i]);
		return ret;
	}
	
	private double[] maskSectsOutsideMagRange(double[] values, FaultSystemRupSet rupSet, double minMag, double maxMag) {
		values = Arrays.copyOf(values, values.length);
		for (int s=0; s<values.length; s++) {
			if (values[s] == 0d) {
				double sectMin = rupSet.getMinMagForSection(s);
				double sectMax = rupSet.getMaxMagForSection(s);
				if (sectMin > maxMag || sectMax < minMag)
					values[s] = Double.NaN;
			}
		}
		return values;
	}
	
	private static double withinRange(Range range, double val) {
		if (val < range.getLowerBound())
			return range.getLowerBound();
		if (val > range.getUpperBound())
			return range.getUpperBound();
		return val;
	}
	
	private static File compScatterPlot(File outputDir, String prefix, double[] values1, double[] values2,
			String label) throws IOException {
		XY_DataSet scatter = new DefaultXY_DataSet();
		
		Range range = new Range(1e-8, 1e0);
		
		for (int i=0; i<values1.length; i++)
			scatter.set(withinRange(range, values1[i]), withinRange(range, values2[i]));
		
		List<XY_DataSet> funcs = new ArrayList<>();
		funcs.add(scatter);
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));
		
		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(range.getLowerBound(), range.getLowerBound());
		line.set(range.getUpperBound(), range.getUpperBound());
		funcs.add(line);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", "Primary "+label, "Comparison "+label);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(spec, true, true, range, range);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, -1, true, false, false);
		return new File(outputDir, prefix+".png");
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}

}
