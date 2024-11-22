package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.uncertainty.UncertainArbDiscFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRuptureProperties;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import net.mahdilamb.colormap.Colors;

public class GriddedDiagnosticPlot extends AbstractSolutionPlot {

	@Override
	public String getName() {
		return "Gridded Seismicity Rupture Properties";
	}
	
	private enum Quantities {
		AREA("Area", "km²", true) {
			@Override
			public double get(GriddedRupture rup) {
				return rup.properties.length * calcWidth(rup);
			}
		},
		LENGTH("Length", "km") {
			@Override
			public double get(GriddedRupture rup) {
				return rup.properties.length;
			}
		},
		WIDTH("Down-Dip Width", "km") {
			@Override
			public double get(GriddedRupture rup) {
				return calcWidth(rup);
			}
		},
		UPPER_DEPTH("Upper Depth", "km") {
			@Override
			public double get(GriddedRupture rup) {
				return rup.properties.upperDepth;
			}
		},
		LOWER_DEPTH("Lower Depth", "km") {
			@Override
			public double get(GriddedRupture rup) {
				return rup.properties.lowerDepth;
			}
		};
		
		public final String label;
		public final String units;
		public final boolean plotLog;
		
		private Quantities(String label, String units) {
			this(label, units, false);
		}

		private Quantities(String label, String units, boolean plotLog) {
			this.label = label;
			this.units = units;
			this.plotLog = plotLog;
		}
		
		public abstract double get(GriddedRupture rup);
	}
	
	private static double calcWidth(GriddedRupture rup) {
		double width = rup.properties.lowerDepth - rup.properties.upperDepth;
		if (rup.properties.dip != 90d)
			width /= Math.sin(Math.toRadians(rup.properties.dip));
		return width;
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		GridSourceList gridSources = sol.requireModule(GridSourceList.class);
		
		Set<TectonicRegionType> trts = gridSources.getTectonicRegionTypes();
		
		IncrementalMagFreqDist refMFD = gridSources.getRefMFD();
		
		List<String> lines = new ArrayList<>();
		
		FocalMech[] mechs = FocalMech.values();
		
		for (TectonicRegionType trt : trts) {
			String trtName = trt.toString().replace("Crust", "").replace("Subduction", "").replace("IntraSlab", "Slab").trim();
			if (trts.size() > 1) {
				lines.add(getSubHeading()+" "+trtName+" Gridded Ruptures");
				lines.add(topLink); lines.add("");
			}
			
			double[] mechRates = new double[mechs.length];
			
			List<List<GriddedRupture>> mechRupLists = new ArrayList<>(mechs.length);
			for (int i=0; i<mechs.length; i++)
				mechRupLists.add(new ArrayList<>());
			
			for (int gridIndex=0; gridIndex<gridSources.getNumLocations(); gridIndex++) {
				for (GriddedRupture rup : gridSources.getRuptures(trt, gridIndex)) {
					if (rup.rate == 0d)
						continue;
					int mechIndex = GridSourceList.getMechForRake(rup.properties.rake).ordinal();
					mechRates[mechIndex] += rup.rate;
					mechRupLists.get(mechIndex).add(rup);
				}
			}
			
			Range xRange = new Range(refMFD.getMinX()-0.5*refMFD.getDelta(), refMFD.getMaxX()+0.5*refMFD.getDelta());
			
			int numMechs = 0;
			double totRate = 0d;
			for (double mechRate : mechRates) {
				totRate += mechRate;
				if (mechRate > 0)
					numMechs++;
			}
			
			boolean firstMech = true;
			for (int mechIndex=0; mechIndex<mechs.length; mechIndex++) {
				FocalMech mech = mechs[mechIndex];
				if (mechRates[mechIndex] == 0d)
					continue;
				String titlePrefix;
				if (numMechs > 1) {
					if (trts.size() == 1) {
						if (firstMech) {
							lines.add("All ruptures are"+trtName+".");
							lines.add("");
						}
						lines.add(getSubHeading()+" "+mech+" Gridded Ruptures");
						titlePrefix = mech+" ";
					} else {
						lines.add(getSubHeading()+"# "+trtName+", "+mech+" Gridded Ruptures");
						titlePrefix = trtName+", "+mech+" ";
					}
					lines.add(topLink); lines.add("");
					
					lines.add(percentDF.format(mechRates[mechIndex]/totRate)+" of ruptures (by-rate) are "+mech+".");
					lines.add("");
				} else {
					if (trts.size() == 1) {
						if (firstMech) {
							lines.add("All ruptures are "+trtName+", "+mech+".");
							lines.add("");
						}
						titlePrefix = "";
					} else {
						if (firstMech) {
							lines.add("All ruptures are "+mech+".");
							lines.add("");
						}
						titlePrefix = trtName+" ";
					}
				}
				firstMech = false;
				
				String prefix = "grid_props_"+trt.name()+"_"+mech.name();
				
				List<GriddedRupture> mechRups = mechRupLists.get(mechIndex);
				
				Map<Quantities, RangeTracker[]> quantityTracks = new HashMap<>();
				for (Quantities q : Quantities.values()) {
					RangeTracker[] tracks = new RangeTracker[refMFD.size()];
					for (int i=0; i<tracks.length; i++)
						tracks[i] = new RangeTracker();
					quantityTracks.put(q, tracks);
					
					for (GriddedRupture rup : mechRups)
						tracks[refMFD.getClosestXIndex(rup.properties.magnitude)].add(q.get(rup), rup.rate);
				}
				
				// depths and widths
				List<DiscretizedFunc> depthFuncs = new ArrayList<>();
				List<PlotCurveCharacterstics> depthChars = new ArrayList<>();
				
				addRangeFuncs(depthFuncs, depthChars, Colors.tab_green, Quantities.UPPER_DEPTH.label,
						refMFD, quantityTracks.get(Quantities.UPPER_DEPTH));
				addRangeFuncs(depthFuncs, depthChars, Colors.tab_blue, Quantities.LOWER_DEPTH.label,
						refMFD, quantityTracks.get(Quantities.LOWER_DEPTH));
				
				double maxDepth = Math.max(1, getMax(depthFuncs));
				Range depthRange = new Range(0d, Math.max(maxDepth*1.1, maxDepth+2));
				
				PlotSpec depthSpec = new PlotSpec(depthFuncs, depthChars, titlePrefix+"Rupture Depths & Widths", "Magnitude", "Depth (km)");
				depthSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT);
				depthSpec.setYAxisInverted(true);
				
				List<DiscretizedFunc> widthFuncs = new ArrayList<>();
				List<PlotCurveCharacterstics> widthChars = new ArrayList<>();
				
				addRangeFuncs(widthFuncs, widthChars, Color.BLACK, null,
						refMFD, quantityTracks.get(Quantities.WIDTH));
				double maxWidth = Math.max(1d, getMax(widthFuncs));
				Range widthRange = new Range(0d, Math.max(maxWidth*1.1, maxWidth+2));
				
				PlotSpec widthSpec = new PlotSpec(widthFuncs, widthChars, depthSpec.getTitle(), "Magnitude",
						Quantities.WIDTH.label+" ("+Quantities.WIDTH.units+")");
				
				HeadlessGraphPanel gp = PlotUtils.initHeadless();
				
				gp.drawGraphPanel(List.of(depthSpec, widthSpec), false, false, List.of(xRange), List.of(depthRange, widthRange));
				
				PlotUtils.setSubPlotWeights(gp, 2, 1);
				
				PlotUtils.writePlots(resourcesDir, prefix+"_depth_width", gp, 700, 800, true, true, false);
				
				lines.add("![Depth-Width plot]("+relPathToResources+"/"+prefix+"_depth_width.png)");
				lines.add("");
				
				List<DiscretizedFunc> areaFuncs = new ArrayList<>();
				List<PlotCurveCharacterstics> areaChars = new ArrayList<>();
				
				addRangeFuncs(areaFuncs, areaChars, Colors.tab_red, null,
						refMFD, quantityTracks.get(Quantities.AREA));
				double minArea = Math.max(0.1d, getMin(areaFuncs));
				double maxArea = Math.max(100d, getMax(areaFuncs));
				Range areaRange = new Range(Math.pow(10, Math.floor(Math.log10(minArea))), Math.pow(10, Math.ceil(Math.log10(maxArea))));
				
				PlotSpec areaSpec = new PlotSpec(areaFuncs, areaChars, titlePrefix+"Rupture Scaling", "Magnitude",
						Quantities.AREA.label+" ("+Quantities.AREA.units+")");
				
				List<DiscretizedFunc> lengthFuncs = new ArrayList<>();
				List<PlotCurveCharacterstics> lengthChars = new ArrayList<>();
				
				addRangeFuncs(lengthFuncs, lengthChars, Colors.tab_purple, null,
						refMFD, quantityTracks.get(Quantities.LENGTH));
				double maxLength = Math.max(10d, getMax(lengthFuncs));
				Range lengthRange = new Range(0d, Math.max(maxLength*1.1, maxLength+5));
				
				PlotSpec lengthSpec = new PlotSpec(lengthFuncs, lengthChars, titlePrefix+"Rupture Scaling", "Magnitude",
						Quantities.LENGTH.label+" ("+Quantities.LENGTH.units+")");
				
				gp.drawGraphPanel(List.of(areaSpec, lengthSpec), List.of(false), List.of(true, false),
						List.of(xRange), List.of(areaRange, lengthRange));
				
				PlotUtils.writePlots(resourcesDir, prefix+"_scaling", gp, 700, 800, true, true, false);
				
				lines.add("![Scaling plot]("+relPathToResources+"/"+prefix+"_scaling.png)");
				lines.add("");
				
				// TODO: maybe scatters if they vary
				
				if (doAnyVary(quantityTracks.get(Quantities.AREA))) {
					// mag area scatter
					String magAreaPrefix = prefix+"_mag_area_scatter";
					plotScatter(resourcesDir, magAreaPrefix, mechRups, Quantities.AREA, quantityTracks.get(Quantities.AREA), refMFD);
					
					lines.add("![Mag-Area Scatter]("+relPathToResources+"/"+magAreaPrefix+".png)");
					lines.add("");
				}
				if (doAnyVary(quantityTracks.get(Quantities.LENGTH))) {
					// mag area scatter
					String magLengthPrefix = prefix+"_mag_length_scatter";
					plotScatter(resourcesDir, magLengthPrefix, mechRups, Quantities.LENGTH, quantityTracks.get(Quantities.LENGTH), refMFD);
					
					lines.add("![Mag-Length Scatter]("+relPathToResources+"/"+magLengthPrefix+".png)");
					lines.add("");
				}
			}
		}
		
		
		
//		lines.add("The gridded seismicity model attached to this solution is of type `"
//				+ClassUtils.getClassNameWithoutPackage(gridSources.getClass())+"` and has a spatial resolution of "
//				+(float)gridReg.getSpacing()+" degrees.");
//		lines.add("");
//		lines.add("The following maps show the faulting type at each grid cell from the gridded seismicity model:");
//		lines.add("");
		return lines;
	}
	
	private static double getMin(List<DiscretizedFunc> funcs) {
		double min = 0d;
		for (DiscretizedFunc func : funcs) {
			if (func instanceof UncertainBoundedDiscretizedFunc)
				min = Math.min(min, ((UncertainBoundedDiscretizedFunc)func).getLower().getMinY());
			else
				min = Math.min(min, func.getMinY());
		}
		return min;
	}
	
	private static double getMax(List<DiscretizedFunc> funcs) {
		double max = 0d;
		for (DiscretizedFunc func : funcs) {
			if (func instanceof UncertainBoundedDiscretizedFunc)
				max = Math.max(max, ((UncertainBoundedDiscretizedFunc)func).getUpper().getMaxY());
			else
				max = Math.max(max, func.getMaxY());
		}
		return max;
	}
	
	private static boolean doAnyVary(RangeTracker[] tracks) {
		for (RangeTracker track : tracks)
			if (track.numFinite > 0 && !track.allSame)
				return true;
		return false;
	}
	
	private static class RangeTracker {
		private double min = Double.POSITIVE_INFINITY;
		private double max = Double.NEGATIVE_INFINITY;
		private double sumRate = 0d;
		private double rateWeightedSum = 0d;
		private boolean first = true;
		private double firstVal;
		private boolean allSame;
		private int numNonFinite = 0;
		private int numFinite = 0;
		
		public void add(double value, double rate) {
			if (Double.isFinite(value)) {
				numFinite++;
			} else {
				numNonFinite++;
				return;
			}
			if (first) {
				firstVal = value;
				first = false;
				allSame = true;
			} else {
				allSame &= (float)firstVal == (float)value;
			}
			min = Math.min(min, value);
			max = Math.max(max, value);
			rateWeightedSum += value*rate;
			sumRate += rate;
		}
		
		public double getAverage() {
			Preconditions.checkState(numFinite > 0);
			if (allSame)
				return firstVal;
			return rateWeightedSum / sumRate;
		}
	}
	
	private static boolean addRangeFuncs(List<DiscretizedFunc> funcs, List<PlotCurveCharacterstics> chars,
			Color color, String label, IncrementalMagFreqDist refMFD, RangeTracker[] tracks) {
		// split into contiguous bundles
		List<RangeTracker> curBundle = null;
		List<Double> curBundleMags = null;
		
		List<List<RangeTracker>> allBundles = new ArrayList<>();
		List<List<Double>> allBundleMags = new ArrayList<>();
		
		for (int i=0; i<tracks.length; i++) {
			if (tracks[i].numFinite > 0) {
				if (curBundle == null) {
					curBundle = new ArrayList<>();
					curBundleMags = new ArrayList<>();
					
					allBundles.add(curBundle);
					allBundleMags.add(curBundleMags);
				}
				curBundle.add(tracks[i]);
				curBundleMags.add(refMFD.getX(i));
			} else {
				curBundle = null;
				curBundleMags = null;
			}
		}
		
		if (allBundles.isEmpty())
			return false;
		
		for (int b=0; b<allBundles.size(); b++) {
			List<RangeTracker> bundle = allBundles.get(b);
			List<Double> bundleMags = allBundleMags.get(b);
			
			boolean anyMultiple = false;
			for (RangeTracker track : bundle)
				anyMultiple |= !track.allSame;
			
			DiscretizedFunc average = new ArbitrarilyDiscretizedFunc();
			DiscretizedFunc lower = anyMultiple ? new ArbitrarilyDiscretizedFunc() : null;
			DiscretizedFunc upper = anyMultiple ? new ArbitrarilyDiscretizedFunc() : null;
			for (int i=0; i<bundle.size(); i++) {
				double mag = bundleMags.get(i);
				RangeTracker track = bundle.get(i);
				average.set(mag, track.getAverage());
				if (anyMultiple) {
					lower.set(mag, track.min);
					upper.set(mag, track.max);
				}
			}
			
			if (anyMultiple) {
				DiscretizedFunc combFunc = new UncertainArbDiscFunc(average, lower, upper);
				if (b == 0)
					combFunc.setName(label);
				funcs.add(combFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN_TRANS, 1f, color));
			}
			if (b == 0)
				average.setName(label);
			funcs.add(average);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, color));
		}
		
		return true;
	}
	
	private static void plotScatter(File resourcesDir, String prefix, List<GriddedRupture> rups,
			Quantities quantity, RangeTracker[] ranges, EvenlyDiscretizedFunc refMFD) throws IOException {
		DiscretizedFunc meanFunc = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<ranges.length; i++)
			if (ranges[i].numFinite > 0)
				meanFunc.set(refMFD.getX(i), ranges[i].getAverage());
		
		double[] mags = new double[rups.size()];
		double[] values = new double[rups.size()];
		for (int i=0; i<rups.size(); i++) {
			GriddedRupture rup = rups.get(i);
			mags[i] = rup.properties.magnitude;
			values[i] = quantity.get(rup);
		}
		LightFixedXFunc scatter = new LightFixedXFunc(mags, values);
		RuptureScalingPlot.plot(resourcesDir, prefix, " ", scatter, null, null, null, "Magnitude", false,
				quantity.label+" ("+quantity.units+")", quantity.plotLog, meanFunc, null, null);
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(GridSourceList.class);
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemSolution sol = FaultSystemSolution.load(
				new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
//						+ "2024_11_19-prvi25_crustal_branches-dmSample5x/results_PRVI_CRUSTAL_FM_V1p1_branch_averaged_gridded.zip"));
						+ "2024_02_02-nshm23_branches-WUS_FM_v3/results_WUS_FM_v3_branch_averaged_gridded_simplified.zip"));
	
		File outputDir = new File("/tmp/grid_report");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		ReportPageGen pageGen = new ReportPageGen(sol.getRupSet(), sol, "Solution", outputDir, List.of(new GriddedDiagnosticPlot()));
		pageGen.setReplot(true);
		pageGen.generatePage();
	}

}
