package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainArbDiscFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.RegionsOfInterest;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchRegionalMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchSectNuclMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchRegionalMFDs.MFDType;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public class SolMFDPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Solution MFDs";
	}

	public static final Color OBSERVED_COLOR = Color.PINK.darker();
	public static final Color SUB_SEIS_TARGET_COLOR = Color.MAGENTA.darker();
	public static final Color SUPRA_SEIS_TARGET_COLOR = Color.CYAN.darker();

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta,
			File resourcesDir, String relPathToResources, String topLink) throws IOException {
		if (sol == null && !rupSet.hasModule(InversionTargetMFDs.class))
			// need a solution or targets
			return null;
		List<MFD_Plot> plots = new ArrayList<>();

		MinMaxAveTracker magTrack = rupSetMagTrack(rupSet, meta);
		System.out.println("Rup set mags: "+magTrack);
		IncrementalMagFreqDist defaultMFD = initDefaultMFD(magTrack.getMin(), magTrack.getMax());
		Range xRange = xRange(defaultMFD);
		
		// see if we have a model region
		GridSourceProvider gridProv = sol == null ? null : sol.getGridSourceProvider();
		Region modelRegion = null;
		if (gridProv != null && gridProv.getGriddedRegion() != null)
			modelRegion = gridProv.getGriddedRegion();
		else if (rupSet.hasModule(ModelRegion.class))
			modelRegion = rupSet.getModule(ModelRegion.class).getRegion();
		
		boolean allInside = false;
		if (modelRegion != null) {
			allInside = true;
			if (gridProv != null) {
				GriddedRegion gridReg = sol.getGridSourceProvider().getGriddedRegion();
				if (gridReg == null || !modelRegion.equalsRegion(gridReg)) {
					for (int n=0; allInside && n<gridProv.getNumLocations(); n++)
						allInside = modelRegion.contains(gridProv.getLocation(n));
				}
			}
			double[] fracts = rupSet.getFractSectsInsideRegion(modelRegion, false);
			for (int s=0; allInside && s<fracts.length; s++)
				allInside = fracts[s] == 1;
		}
		
		MFD_Plot totalPlot;
		if (allInside)
			totalPlot = new MFD_Plot("Total Model Region MFDs", modelRegion, null);
		else
			totalPlot = new MFD_Plot("Total MFDs", null, null);
		plots.add(totalPlot);
		
		double minY = 1e-6;
		double maxY = 1e1;
		InversionTargetMFDs targetMFDs = rupSet.getModule(InversionTargetMFDs.class);
		List<? extends IncrementalMagFreqDist> supraSeisSectNuclMFDs = null;
		SubSeismoOnFaultMFDs subSeisSectMFDs = null;
		if (targetMFDs != null) {
			supraSeisSectNuclMFDs = targetMFDs.getOnFaultSupraSeisNucleationMFDs();
			subSeisSectMFDs = targetMFDs.getOnFaultSubSeisMFDs();
			totalPlot.addComp(targetMFDs.getTotalRegionalMFD(), Color.GREEN.darker(), "Total Target");
			totalPlot.addComp(targetMFDs.getTotalGriddedSeisMFD(), Color.GRAY, "Target Gridded");
			totalPlot.addComp(targetMFDs.getTotalOnFaultSubSeisMFD(), SUB_SEIS_TARGET_COLOR, "Target Sub-Seis");
			totalPlot.addComp(targetMFDs.getTotalOnFaultSupraSeisMFD(), SUPRA_SEIS_TARGET_COLOR, "Target Supra-Seis");
			
			List<? extends IncrementalMagFreqDist> constraints = targetMFDs.getMFD_Constraints();
			for (IncrementalMagFreqDist constraint : constraints) {
				Region region = constraint.getRegion();
				String name;
				if (region == null || region.getName() == null || region.getName().isBlank()) {
					if (constraints.size() == 1)
						name = "MFD Constraint";
					else
						name = "MFD Constraint "+plots.size();
				} else {
					name = region.getName();
				}
				if (constraint.equals(targetMFDs.getTotalOnFaultSupraSeisMFD())) {
					// skip it, but set region if applicable
					totalPlot.region = region;
				} else {
					MFD_Plot plot = new MFD_Plot(name, region, null);
					plot.addComp(constraint, SUPRA_SEIS_TARGET_COLOR, "Target Supra-Seis");
					plots.add(plot);
				}
				// make sure to include the whole constraint in the plot
				for (Point2D pt : constraint)
					if (pt.getY() > 1e-10 && xRange.contains(pt.getX()))
						minY = Math.min(minY, Math.pow(10, Math.floor(Math.log10(pt.getY())+0.1)));
				for (Point2D pt : constraint.getCumRateDistWithOffset())
					if (xRange.contains(pt.getX()))
						maxY = Math.max(maxY, Math.pow(10, Math.ceil(Math.log10(pt.getY())-0.1)));
			}
		}
		
		if (modelRegion != null && !allInside) {
			// add for the model region, which is a subset of the total model and thus not a duplicate of the above
			String name = modelRegion.getName();
			if (modelRegion.getName() == null || modelRegion.getName().isBlank())
				name = "Model Region";
			MFD_Plot plot = new MFD_Plot(name, modelRegion, null);
			addImpliedTargets(rupSet, supraSeisSectNuclMFDs, subSeisSectMFDs, modelRegion, plot);
		}
		
		if (rupSet.hasModule(RegionsOfInterest.class)) {
			RegionsOfInterest roi = rupSet.getModule(RegionsOfInterest.class);
			List<Region> regions = roi.getRegions();
			List<IncrementalMagFreqDist> regionMFDs = roi.getMFDs();
			List<TectonicRegionType> regionTRTs = roi.getTRTs();
			for (int i=0; i<regions.size(); i++) {
				Region region = regions.get(i);
				String name = region.getName();
				if (name == null || name.isBlank())
					name = "Region Of Interest "+i;
				// see if it's a duplicate
				boolean duplicate = false;
				for (MFD_Plot plot : plots) {
					if (plot.region != null && plot.region.equalsRegion(region)) {
						duplicate = true;
						break;
					}
				}
				if (duplicate) {
					System.out.println("Skipping duplicate region from ROI list: "+name);
				} else {
					TectonicRegionType trt = regionTRTs == null ? null : regionTRTs.get(i);
					MFD_Plot plot = new MFD_Plot(name, region, trt);
					if (regionMFDs != null) {
						IncrementalMagFreqDist mfd = regionMFDs.get(i);
						if (mfd != null) {
							String mfdName = mfd.getName();
							if (mfdName == null || mfdName.isBlank() || mfdName.equals(mfd.getDefaultName()))
								mfdName = "Observed";
							plot.addComp(mfd, OBSERVED_COLOR, mfdName);
						}
					}
					addImpliedTargets(rupSet, supraSeisSectNuclMFDs, subSeisSectMFDs, region, plot);
					plots.add(plot);
				}
			}
		}

		List<PlotSpec> incrSpecs = new ArrayList<>();
		List<PlotSpec> cmlSpecs = new ArrayList<>();
		List<PlotSpec[]> gridRangeIncrSpecs = null;
		List<PlotSpec[]> gridRangeCmlSpecs = null;
		if (sol != null && sol.hasModule(BranchRegionalMFDs.class) && sol.requireModule(BranchRegionalMFDs.class).hasGridded()) {
			gridRangeIncrSpecs = new ArrayList<>();
			gridRangeCmlSpecs = new ArrayList<>();
		}
		
		for (MFD_Plot plot : plots) {
			System.out.println("Plotting MFDs for "+plot.name);
			List<IncrementalMagFreqDist> incrFuncs = new ArrayList<>();
			List<DiscretizedFunc> cmlFuncs = new ArrayList<>();
			List<PlotCurveCharacterstics> incrChars = new ArrayList<>();
			List<PlotCurveCharacterstics> cmlChars = new ArrayList<>();
			
			for (int c=0; c<plot.comps.size(); c++) {
				IncrementalMagFreqDist comp = plot.comps.get(c);
				if (comp == null)
					continue;
				
				Color color = plot.compColors.get(c);
				
				comp.setName(plot.compNames.get(c));
				incrFuncs.add(comp);
				EvenlyDiscretizedFunc cumulative = comp.getCumRateDistWithOffset();
				cmlFuncs.add(cumulative);
				PlotCurveCharacterstics pChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, color);
				incrChars.add(pChar);
				cmlChars.add(pChar);
				
				if (comp instanceof UncertainIncrMagFreqDist) {
					UncertainBoundedIncrMagFreqDist bounded;
					if (comp instanceof UncertainBoundedIncrMagFreqDist)
						bounded = ((UncertainBoundedIncrMagFreqDist)comp).deepClone();
					else
						bounded = ((UncertainIncrMagFreqDist)comp).estimateBounds(UncertaintyBoundType.ONE_SIGMA);
					bounded.setName(bounded.getBoundName());
					
					incrFuncs.add(bounded);
					incrChars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f,
							new Color(color.getRed(), color.getGreen(), color.getBlue(), 60)));
					
					EvenlyDiscretizedFunc upperCumulative = bounded.getUpper().getCumRateDistWithOffset();
					EvenlyDiscretizedFunc lowerCumulative = bounded.getLower().getCumRateDistWithOffset();
					Preconditions.checkState(cumulative.size() == upperCumulative.size());
					for (int i=0; i<cumulative.size(); i++) {
						upperCumulative.set(i, Math.max(cumulative.getY(i), upperCumulative.getY(i)));
						lowerCumulative.set(i, Math.max(0, Math.min(cumulative.getY(i), lowerCumulative.getY(i))));
					}
					
					UncertainArbDiscFunc cmlBounded = new UncertainArbDiscFunc(cumulative, lowerCumulative, upperCumulative);
					cmlBounded.setName(bounded.getName());
					cmlFuncs.add(cmlBounded);
					cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f,
							new Color(color.getRed(), color.getGreen(), color.getBlue(), 60)));
				}
			}
			
			if (meta.comparison != null && meta.comparison.sol != null)
				addSolMFDs(meta.comparison.sol, "Comparison", COMP_COLOR, plot.region,
						incrFuncs, cmlFuncs, incrChars, cmlChars, defaultMFD, xRange, null, null, null, -1, plot.trt);
			if (sol != null) {
				List<IncrementalMagFreqDist> origIncrFuncs = new ArrayList<>(incrFuncs);
				List<PlotCurveCharacterstics> origIncrChars = new ArrayList<>(incrChars);
				List<DiscretizedFunc> origCmlFuncs = new ArrayList<>(cmlFuncs);
				List<PlotCurveCharacterstics> origCmlChars = new ArrayList<>(cmlChars);
				
				BranchSectNuclMFDs sectDists = null;
				BranchRegionalMFDs branchMFDsModule = sol.getModule(BranchRegionalMFDs.class);
				int regionalIndex = -1;
				if (plot.region != null && branchMFDsModule != null) {
					// find the matching region
					RegionsOfInterest roi = sol.getRupSet().getModule(RegionsOfInterest.class);
					if (roi == null || !branchMFDsModule.hasRegionalMFDs()) {
						// don't have it
						if (!(plot.region == modelRegion && allInside))
							// clear it, but only if this isn't the model region and we're not fully contained within in
							branchMFDsModule = null;
					} else {
						for (int r=0; r<roi.getRegions().size(); r++) {
							Region testReg = roi.getRegions().get(r);
							if (plot.region.equalsRegion(testReg)) {
								regionalIndex = r;
								break;
							}
						}
						if (regionalIndex < 0) {
							// no match
							if (!(plot.region == modelRegion && allInside))
								// clear it, but only if this isn't the model region and we're not fully contained within in
								branchMFDsModule = null;
						} else {
							System.out.println("Matched region with name '"+plot.name+" to ROI "+regionalIndex);
						}
					}
				}
				if (branchMFDsModule == null)
					// see if we have section MFDs
					sectDists = sol.getModule(BranchSectNuclMFDs.class);
				
				double myMax = addSolMFDs(sol, "Solution", MAIN_COLOR, plot.region,
						incrFuncs, cmlFuncs, incrChars, cmlChars, defaultMFD, xRange, sectDists, branchMFDsModule,
						MFDType.SUPRA_ONLY, regionalIndex, plot.trt);
				maxY = Math.max(maxY, Math.pow(10, Math.ceil(Math.log10(myMax)-0.1)));
				
				if (branchMFDsModule != null && branchMFDsModule.hasGridded()) {
					// add extra plots for gridded seismicity
					List<IncrementalMagFreqDist> gridOnlyIncrFuncs = new ArrayList<>(origIncrFuncs);
					List<PlotCurveCharacterstics> gridOnlyIncrChars = new ArrayList<>(origIncrChars);
					List<DiscretizedFunc> gridOnlyCmlFuncs = new ArrayList<>(origCmlFuncs);
					List<PlotCurveCharacterstics> gridOnlyCmlChars = new ArrayList<>(origCmlChars);
					
					myMax = addSolMFDs(sol, "Solution", MAIN_COLOR, plot.region,
							gridOnlyIncrFuncs, gridOnlyCmlFuncs, gridOnlyIncrChars, gridOnlyCmlChars,
							defaultMFD, xRange, sectDists, branchMFDsModule,
							MFDType.GRID_ONLY, regionalIndex, plot.trt);
					maxY = Math.max(maxY, Math.pow(10, Math.ceil(Math.log10(myMax)-0.1)));
					
					PlotSpec gridOnlyIncrSpec = new PlotSpec(gridOnlyIncrFuncs, gridOnlyIncrChars, plot.name, "Magnitude", "Incremental Rate (per yr)");
					PlotSpec gridOnlyCmlSpec = new PlotSpec(gridOnlyCmlFuncs, gridOnlyCmlChars, plot.name, "Magnitude", "Cumulative Rate (per yr)");
					gridOnlyIncrSpec.setLegendInset(true);
					gridOnlyCmlSpec.setLegendInset(true);
					
					List<IncrementalMagFreqDist> totalIncrFuncs = new ArrayList<>(origIncrFuncs);
					List<PlotCurveCharacterstics> totalIncrChars = new ArrayList<>(origIncrChars);
					List<DiscretizedFunc> totalCmlFuncs = new ArrayList<>(origCmlFuncs);
					List<PlotCurveCharacterstics> totalCmlChars = new ArrayList<>(origCmlChars);
					
					myMax = addSolMFDs(sol, "Solution", MAIN_COLOR, plot.region,
							totalIncrFuncs, totalCmlFuncs, totalIncrChars, totalCmlChars,
							defaultMFD, xRange, sectDists, branchMFDsModule,
							MFDType.SUM, regionalIndex, plot.trt);
					maxY = Math.max(maxY, Math.pow(10, Math.ceil(Math.log10(myMax)-0.1)));
					
					PlotSpec totalIncrSpec = new PlotSpec(totalIncrFuncs, totalIncrChars, plot.name, "Magnitude", "Incremental Rate (per yr)");
					PlotSpec totalCmlSpec = new PlotSpec(totalCmlFuncs, totalCmlChars, plot.name, "Magnitude", "Cumulative Rate (per yr)");
					totalIncrSpec.setLegendInset(true);
					totalCmlSpec.setLegendInset(true);
					
					gridRangeIncrSpecs.add(new PlotSpec[] { gridOnlyIncrSpec, totalIncrSpec });
					gridRangeCmlSpecs.add(new PlotSpec[] { gridOnlyCmlSpec, totalCmlSpec });
					
					if (meta.hasComparisonSol() && meta.comparison.sol.hasModule(GridSourceProvider.class)) {
						// remove the comparison gridded seis lines as necessary
						
						// remove total and grid only from supra-seis plot
						removeByName(incrFuncs, incrChars, "Comparison Gridded");
						removeByName(incrFuncs, incrChars, "Comparison Total");
						removeByName(cmlFuncs, cmlChars, "Comparison Gridded");
						removeByName(cmlFuncs, cmlChars, "Comparison Total");
						
						// remove total from grid only plot
						removeByName(gridOnlyIncrFuncs, gridOnlyIncrChars, "Comparison Total");
						removeByName(gridOnlyCmlFuncs, gridOnlyCmlChars, "Comparison Total");
						
						// remove gridded only from total plot
						removeByName(totalIncrFuncs, totalIncrChars, "Comparison Gridded");
						removeByName(totalCmlFuncs, totalCmlChars, "Comparison Gridded");
					}
				} else if (gridRangeCmlSpecs != null) {
					gridRangeIncrSpecs.add(null);
					gridRangeCmlSpecs.add(null);
				}
			}
			
			PlotSpec incrSpec = new PlotSpec(incrFuncs, incrChars, plot.name, "Magnitude", "Incremental Rate (per yr)");
			PlotSpec cmlSpec = new PlotSpec(cmlFuncs, cmlChars, plot.name, "Magnitude", "Cumulative Rate (per yr)");
			incrSpec.setLegendInset(true);
			cmlSpec.setLegendInset(true);
			
			incrSpecs.add(incrSpec);
			cmlSpecs.add(cmlSpec);
		}
		
		System.out.println("MFD Y-Range: "+minY+" "+maxY);
		Range yRange = new Range(minY, maxY);
		
		List<Region> plottedRegions = new ArrayList<>();

		List<String> lines = new ArrayList<>();
		for (int i=0; i<plots.size(); i++) {
			MFD_Plot plot = plots.get(i);
			if (plots.size() > 1) {
				if (!lines.isEmpty())
					lines.add("");
				lines.add(getSubHeading()+" "+plot.name);
				lines.add(topLink); lines.add("");
				
				if (i == 0 && plot.region == null) {
					lines.add("This section contains MFDs for all sources included in the model. Subsequent sections "
							+ "show MFDs for subsets of the model that lie in subregions.");
					lines.add("");
				}
			}
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.addLine("Incremental MFDs", "Cumulative MFDs");
			
			if (plot.region != null)
				plottedRegions.add(plot.region);
			
			String prefix = "mfd_plot_"+getFileSafe(plot.name);
			
			HeadlessGraphPanel gp = PlotUtils.initHeadless();
			gp.setTickLabelFontSize(20);
			
			double tick;
			if (xRange.getLength() > 3.5)
				tick = 0.5;
			else if (xRange.getLength() > 1.5)
				tick = 0.25;
			else
				tick = 0.1d;
			
			table.initNewLine();
			gp.drawGraphPanel(incrSpecs.get(i), false, true, xRange, yRange);
			PlotUtils.setXTick(gp, tick);
			PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 850, true, true, true);
			table.addColumn("![Incremental Plot]("+relPathToResources+"/"+prefix+".png)");
			
			String cmlPrefix = prefix+"_cumulative";
			gp.drawGraphPanel(cmlSpecs.get(i), false, true, xRange, yRange);
			PlotUtils.setXTick(gp, tick);
			PlotUtils.writePlots(resourcesDir, cmlPrefix, gp, 1000, 850, true, true, true);
			table.addColumn("![Cumulative Plot]("+relPathToResources+"/"+cmlPrefix+".png)");
			table.finalizeLine();
			
			if (gridRangeIncrSpecs != null && gridRangeIncrSpecs.get(i) != null) {
				PlotSpec[] rangeIncrSpecs = gridRangeIncrSpecs.get(i);
				if (rangeIncrSpecs != null) {
					PlotSpec[] rangeCmlSpecs = gridRangeCmlSpecs.get(i);
					
					table.addLine(MarkdownUtils.boldCentered("Distribution of Gridded Seismicity"), "");
					
					table.initNewLine();
					gp.drawGraphPanel(rangeIncrSpecs[0], false, true, xRange, yRange);
					PlotUtils.setXTick(gp, tick);
					String myPrefix = prefix+"_grid_only_dists";
					PlotUtils.writePlots(resourcesDir, myPrefix, gp, 1000, 850, true, true, true);
					table.addColumn("![Incremental Plot]("+relPathToResources+"/"+myPrefix+".png)");
					
					myPrefix += "_cumulative";
					gp.drawGraphPanel(rangeCmlSpecs[0], false, true, xRange, yRange);
					PlotUtils.setXTick(gp, tick);
					PlotUtils.writePlots(resourcesDir, myPrefix, gp, 1000, 850, true, true, true);
					table.addColumn("![Cumulative Plot]("+relPathToResources+"/"+myPrefix+".png)");
					table.finalizeLine();
					
					table.addLine(MarkdownUtils.boldCentered("Distribution of Sum (Supra-Seis + Gridded)"), "");
					
					table.initNewLine();
					gp.drawGraphPanel(rangeIncrSpecs[1], false, true, xRange, yRange);
					PlotUtils.setXTick(gp, tick);
					myPrefix = prefix+"_grid_sum_dists";
					PlotUtils.writePlots(resourcesDir, myPrefix, gp, 1000, 850, true, true, true);
					table.addColumn("![Incremental Plot]("+relPathToResources+"/"+myPrefix+".png)");
					
					myPrefix += "_cumulative";
					gp.drawGraphPanel(rangeCmlSpecs[1], false, true, xRange, yRange);
					PlotUtils.setXTick(gp, tick);
					PlotUtils.writePlots(resourcesDir, myPrefix, gp, 1000, 850, true, true, true);
					table.addColumn("![Cumulative Plot]("+relPathToResources+"/"+myPrefix+".png)");
					table.finalizeLine();
				}
			}
			
			lines.addAll(table.build());
		}
		
		if (!plottedRegions.isEmpty()) {
			// add regions plot
			
			double minLat = Double.POSITIVE_INFINITY;
			double minLon = Double.POSITIVE_INFINITY;
			double maxLat = Double.NEGATIVE_INFINITY;
			double maxLon = Double.NEGATIVE_INFINITY;
			
			for (FaultSection sect : rupSet.getFaultSectionDataList()) {
				for (Location loc : sect.getFaultTrace()) {
					minLat = Math.min(minLat, loc.getLatitude());
					maxLat = Math.max(maxLat, loc.getLatitude());
					minLon = Math.min(minLon, loc.getLongitude());
					maxLon = Math.max(maxLon, loc.getLongitude());
				}
			}
			
			for (Region reg : plottedRegions) {
				minLat = Math.min(minLat, reg.getMinLat());
				maxLat = Math.max(maxLat, reg.getMaxLat());
				minLon = Math.min(minLon, reg.getMinLon());
				maxLon = Math.max(maxLon, reg.getMaxLon());
			}
			
			double latSpan = maxLat - minLat;
			double lonSpan = maxLon - minLon;
			double span = Math.max(latSpan, lonSpan);
			double buffer;
			if (span > 10)
				buffer = 0.5;
			else if (span > 5)
				buffer = 0.2;
			else
				buffer = 0.1;
			minLat = Math.max(-90, minLat-buffer);
			maxLat = Math.min(90, maxLat+buffer);
			minLon = Math.max(-180, minLon-buffer);
			maxLon = Math.min(minLon < 0 ? 180 : 360, maxLon+buffer);
			
			Region plotReg = new Region(new Location(minLat, minLon), new Location(maxLat, maxLon));
			
			GeographicMapMaker mapMaker = new RupSetMapMaker(rupSet, plotReg);
			
			mapMaker.plotInsetRegions(plottedRegions,
					new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK), new Color(100, 100, 200), 0.1);
			
			mapMaker.setWriteGeoJSON(true);
			mapMaker.setWritePDFs(true);
			
			mapMaker.plot(resourcesDir, "mfd_regions", "MFD Regions", lonSpan > 20 ? 1200 : 800);
			
			String relPrefix = relPathToResources+"/mfd_regions";
			
			List<Feature> features = new ArrayList<>();
			for (Region reg : plottedRegions)
				features.add(reg.toFeature());
			FeatureCollection.write(new FeatureCollection(features), new File(resourcesDir, "mfd_regions_raw.geojson"));
			
			List<String> regLines = new ArrayList<>();
			
			TableBuilder linksTable = MarkdownUtils.tableBuilder().initNewLine();
			
			if (plottedRegions.size() > 1) {
				// include the plot
				regLines.add(getSubHeading()+" MFD Regions");
				regLines.add(topLink); regLines.add("");
				regLines.add("![Regions plot]("+relPrefix+".png)");
			} else {
				linksTable.addColumn("[View Map]("+relPrefix+".png)");
				regLines.add("__MFD Regions:__");
			}
			
			linksTable.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPrefix+".geojson"));
			linksTable.addColumn("[Download GeoJSON w/ Faults]("+relPrefix+".geojson)");
			linksTable.addColumn("[Download Raw GeoJSON]("+relPrefix+"_raw.geojson)");
			linksTable.finalizeLine();
			
			regLines.add("");
			regLines.addAll(linksTable.build());
			regLines.add("");
			
			lines.addAll(0, regLines);
		}
		
		return lines;
	}
	
	private void removeByName(List<? extends DiscretizedFunc> funcs, List<PlotCurveCharacterstics> chars, String name) {
		for (int i=funcs.size(); --i>=0;) {
			String funcName = funcs.get(i).getName();
			if (funcName != null && funcName.equals(name)) {
				funcs.remove(i);
				chars.remove(i);
			}
		}
	}

	private void addImpliedTargets(FaultSystemRupSet rupSet,
			List<? extends IncrementalMagFreqDist> supraSeisSectNuclMFDs, SubSeismoOnFaultMFDs subSeisSectMFDs,
			Region region, MFD_Plot plot) {
		if (subSeisSectMFDs != null && subSeisSectMFDs.size() == rupSet.getNumSections()) {
			System.out.println("Looking for subsection sub-seis MFDs in region: "+region.getName());
			SummedMagFreqDist target = sumSectMFDsInRegion(region, rupSet, subSeisSectMFDs.getAll(), plot.trt);
			if (target != null) {
				System.out.println("Found total sub-seis rate of "+target.calcSumOfY_Vals());
				plot.addComp(target, SUB_SEIS_TARGET_COLOR, "Implied Target Sub-Seis");
			}
		}
		if (supraSeisSectNuclMFDs != null && supraSeisSectNuclMFDs.size() == rupSet.getNumSections()) {
			System.out.println("Looking for subsection nucleation MFDs in region: "+region.getName());
			SummedMagFreqDist target = sumSectMFDsInRegion(region, rupSet, supraSeisSectNuclMFDs, plot.trt);
			if (target != null) {
				System.out.println("Found total supra-seis rate of "+target.calcSumOfY_Vals());
				plot.addComp(target, SUPRA_SEIS_TARGET_COLOR, "Implied Target Supra-Seis");
			}
		}
	}
	
	public static IncrementalMagFreqDist initDefaultMFD(double minMag, double maxMag) {
		double defaultMin;
		if (minMag > 7d)
			defaultMin = 6d;
		else
			defaultMin = 5d;
		double defaultMax;
		if (maxMag > 8.8)
			defaultMax = 10d;
		else if (maxMag < 7d)
			defaultMax = 8d;
		else
			defaultMax = 9d;
		
		return initDefaultMFD(defaultMin, defaultMax, minMag, maxMag);
	}
	
	public static IncrementalMagFreqDist initDefaultMFD(double defaultMinMag, double defaultMaxMag, double minMag, double maxMag) {
		minMag = Math.min(defaultMinMag, Math.floor(minMag));
		maxMag = Math.max(defaultMaxMag, Math.ceil(maxMag));
		double delta = 0.1;
		// offset
		minMag += 0.5*delta;
		maxMag -= 0.5*delta;
		int num = (int)((maxMag - minMag)/delta + 0.5)+1;
		if (num == 1)
			maxMag = minMag;
//		System.out.println(num+" "+minMag+" "+maxMag);
		return new IncrementalMagFreqDist(minMag, maxMag, num);
	}
	
	private static SummedMagFreqDist sumSectMFDsInRegion(Region region, FaultSystemRupSet rupSet,
			List<? extends IncrementalMagFreqDist> sectMFDs, TectonicRegionType targetTRT) {
		// build our own custom target by summing up section nucleation MFDs
		double[] fracts = rupSet.getFractSectsInsideRegion(region, false);
		Preconditions.checkState(fracts.length == sectMFDs.size());
		double minX = Double.NaN;
		int maxSize = 0;
		boolean[] sectTRTmatch = null;
		if (targetTRT != null) {
			RupSetTectonicRegimes rupTRTs = rupSet.getModule(RupSetTectonicRegimes.class);
			if (rupTRTs == null)
				return null;
			sectTRTmatch = new boolean[rupSet.getNumSections()];
			for (int rupIndex=0; rupIndex<rupSet.getNumRuptures(); rupIndex++)
				if (rupTRTs.get(rupIndex) == targetTRT)
					for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex))
						sectTRTmatch[sectIndex] = true;
		}
		for (int s=0; s<sectMFDs.size(); s++) {
			IncrementalMagFreqDist sectMFD = sectMFDs.get(s);
			if (sectMFD != null && (targetTRT == null || sectTRTmatch[s])) {
				if (Double.isNaN(minX))
					minX = sectMFD.getMinX();
				else
					Preconditions.checkState((float)minX == (float)sectMFD.getMinX());
				maxSize = Integer.max(maxSize, sectMFD.size());
			}
		}
		if (maxSize > 0) {
			SummedMagFreqDist target = null;
			for (int s=0; s<sectMFDs.size(); s++) {
				IncrementalMagFreqDist sectMFD = sectMFDs.get(s);
				if (fracts[s] > 0d && sectMFD != null && (targetTRT == null || sectTRTmatch[s])) {
					if (target == null)
						target = new SummedMagFreqDist(minX, maxSize, sectMFD.getDelta());
					if (fracts[s] != 1d) {
						sectMFD = sectMFD.deepClone();
						sectMFD.scale(fracts[s]);
					}
					target.addIncrementalMagFreqDist(sectMFD);
				}
			}
			if (target != null && target.calcSumOfY_Vals() > 0d) {
				return target;
			}
		}
		return null;
	}
	
	static Range xRange(IncrementalMagFreqDist mfd) {
		return new Range(mfd.getMinX()-0.5*mfd.getDelta(), mfd.getMaxX()+0.5*mfd.getDelta());
	}
	
	private static MinMaxAveTracker rupSetMagTrack(FaultSystemRupSet rupSet, ReportMetadata meta) {
		MinMaxAveTracker track = new MinMaxAveTracker();
		track.addValue(rupSet.getMinMag());
		track.addValue(rupSet.getMaxMag());
		if (meta.comparison != null && meta.comparison.sol != null) {
			track.addValue(meta.comparison.rupSet.getMinMag());
			track.addValue(meta.comparison.rupSet.getMaxMag());
		}
		return track;
	}
	
	private static class MFD_Plot {
		private String name;
		private Region region;
		private TectonicRegionType trt;
		private List<IncrementalMagFreqDist> comps;
		private List<Color> compColors;
		private List<String> compNames;
		
		public MFD_Plot(String name, Region region, TectonicRegionType trt) {
			this.name = name;
			this.region = region;
			this.trt = trt;
			this.comps = new ArrayList<>();
			this.compColors = new ArrayList<>();
			this.compNames = new ArrayList<>();
		}
		
		public void addComp(IncrementalMagFreqDist comp, Color color, String name) {
			comps.add(comp);
			compColors.add(color);
			compNames.add(name);
		}
	}
	
	private static Color avg(Color c1, Color c2) {
		int r = c1.getRed() + c2.getRed();
		int g = c1.getGreen() + c2.getGreen();
		int b = c1.getBlue() + c2.getBlue();
		return new Color((int)Math.round(r*0.5d), (int)Math.round(g*0.5d), (int)Math.round(b*0.5d));
	}
	
	private static double addSolMFDs(FaultSystemSolution sol, String name, Color color, Region region,
			List<IncrementalMagFreqDist> incrFuncs, List<DiscretizedFunc> cmlFuncs,
			List<PlotCurveCharacterstics> incrChars, List<PlotCurveCharacterstics> cmlChars,
			IncrementalMagFreqDist defaultMFD, Range rangeForMax, BranchSectNuclMFDs sectDists,
			BranchRegionalMFDs regMFDModule, MFDType regType, int regionalIndex, TectonicRegionType trt) {
		int transAlpha = 30;
		IncrementalMagFreqDist mfd = sol.calcNucleationMFD_forRegion(
				region, defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size(), false, trt);
		if (sol.hasModule(GridSourceProvider.class)) {
			GridSourceProvider prov = sol.getGridSourceProvider();
			if (trt == null || prov.getTectonicRegionTypes().contains(trt)) {
				SummedMagFreqDist gridMFD = null;
				GriddedRegion gridReg = prov.getGriddedRegion();
				boolean regionTest = region != null &&
						(gridReg == null || region != gridReg && !region.getBorder().equals(gridReg.getBorder()));
				for (int i=0; i<prov.getNumLocations(); i++) {
					IncrementalMagFreqDist nodeMFD = prov.getMFD(trt, i);
					if (nodeMFD == null)
						continue;
					if (regionTest && !region.contains(prov.getLocation(i)))
						continue;
					if (gridMFD == null) {
						gridMFD = new SummedMagFreqDist(nodeMFD.getMinX(), nodeMFD.getMaxX(), nodeMFD.size());
					} else {
						Preconditions.checkState((float)gridMFD.getMinX() == (float)nodeMFD.getMinX());
						if (gridMFD.size() < nodeMFD.size()) {
							SummedMagFreqDist tempMFD = new SummedMagFreqDist(nodeMFD.getMinX(), nodeMFD.getMaxX(), nodeMFD.size());
							tempMFD.addIncrementalMagFreqDist(gridMFD);
							gridMFD = tempMFD;
						}
					}
					gridMFD.addIncrementalMagFreqDist(nodeMFD);
				}
				if (gridMFD != null) {
					boolean includeTotal = true;
					boolean includeGridOnly = true;
					
					if (regMFDModule != null && regMFDModule.hasGridded()) {
						// we have distributions and will be plotting each, only include the relevant one in each plot
						includeTotal = regType == MFDType.SUM;
						includeGridOnly = regType == MFDType.GRID_ONLY;
					}
					
					if (includeGridOnly) {
						gridMFD.setName(name+" Gridded");
						incrFuncs.add(gridMFD);
						EvenlyDiscretizedFunc cmlFunc = gridMFD.getCumRateDistWithOffset();
						cmlFuncs.add(cmlFunc);
						Color myColor = avg(color, Color.WHITE);
						PlotCurveCharacterstics pChar = new PlotCurveCharacterstics(
								PlotLineType.DASHED, regType == MFDType.GRID_ONLY ? 5f : 3f, myColor);
						incrChars.add(pChar);
						cmlChars.add(pChar);
//						chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, color.brighter()));
						if (regMFDModule != null && regType == MFDType.GRID_ONLY && regMFDModule.hasGridded())
							addFromRegMFDs(color, incrFuncs, cmlFuncs, incrChars, cmlChars, regMFDModule, regType,
									regionalIndex, gridMFD, cmlFunc, pChar, myColor, transAlpha);
					}
					
					if (includeTotal) {
						SummedMagFreqDist totalMFD = new SummedMagFreqDist(mfd.getMinX(), mfd.getMaxX(), mfd.size());
						totalMFD.addIncrementalMagFreqDist(mfd);
						totalMFD.addIncrementalMagFreqDist(gridMFD);
						totalMFD.setName(name+" Total");
						incrFuncs.add(totalMFD);
						EvenlyDiscretizedFunc cmlFunc = totalMFD.getCumRateDistWithOffset();
						cmlFuncs.add(cmlFunc);
						Color myColor = color.darker();
						PlotCurveCharacterstics pChar = new PlotCurveCharacterstics(
								PlotLineType.DASHED, regType == MFDType.SUM ? 5f : 3f, myColor);
						incrChars.add(pChar);
						cmlChars.add(pChar);
						if (regMFDModule != null && regType == MFDType.SUM && regMFDModule.hasGridded())
							addFromRegMFDs(color, incrFuncs, cmlFuncs, incrChars, cmlChars, regMFDModule, regType,
									regionalIndex, totalMFD, cmlFunc, pChar, myColor, transAlpha);
					}
					
					name = name+" Supra-Seis";
				}
			}
		}
		mfd.setName(name);
		incrFuncs.add(mfd);
		EvenlyDiscretizedFunc cmlFunc = mfd.getCumRateDistWithOffset();
		cmlFuncs.add(cmlFunc);
		PlotCurveCharacterstics pChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, regType == null || regType == MFDType.SUPRA_ONLY ? 5f : 3f, color);
		incrChars.add(pChar);
		cmlChars.add(pChar);
		
//		if (includeDists) {
//			if (sol.hasModule(BranchRegionalMFDs.class)) {
//				
//			} else if (sol.hasModule(BranchSectNuclMFDs))
//		}
		if (sectDists != null) {
			// we have distributions of MFDs
			double[] sectFracts = null;
			if (region != null)
				sectFracts = sol.getRupSet().getFractSectsInsideRegion(region, false);
			IncrementalMagFreqDist[] incrPercentiles = sectDists.calcIncrementalFractiles(sectFracts, standardFractiles);
			
			Color transColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), transAlpha);
			PlotCurveCharacterstics minMaxChar = new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, transColor);
			
			for (IncrementalMagFreqDist bounds : processIncrFractiles(incrPercentiles)) {
				incrFuncs.add(bounds);
				incrChars.add(minMaxChar);
			}
			
			EvenlyDiscretizedFunc[] cmlPercentiles = sectDists.calcCumulativeFractiles(sectFracts, standardFractiles);
			for (UncertainArbDiscFunc cmlBounds : processCmlFractiles(cmlPercentiles, cmlFunc.getMinX())) {
				cmlFuncs.add(cmlBounds);
				cmlChars.add(minMaxChar);
			}
			
			// now add the original MFD again on top, but without a name
			IncrementalMagFreqDist mfd2 = mfd.deepClone();
			mfd2.setName(null);
			incrFuncs.add(mfd2);
			incrChars.add(pChar);
			EvenlyDiscretizedFunc mfd2c = cmlFunc.deepClone();
			mfd2c.setName(null);
			cmlFuncs.add(mfd2c);
			cmlChars.add(pChar);
		}
		
		if (regMFDModule != null && regType == MFDType.SUPRA_ONLY)
			addFromRegMFDs(color, incrFuncs, cmlFuncs, incrChars, cmlChars, regMFDModule, regType, regionalIndex, mfd,
					cmlFunc, pChar, color, transAlpha);
		
		double maxY = 0d;
		for (Point2D pt : cmlFunc)
			if (rangeForMax.contains(pt.getX()))
				maxY = Math.max(maxY, pt.getY());
		// now make sure that we have the full distributions at the minimum incremental magnitude
		double minIncrMag = Double.POSITIVE_INFINITY;
		for (Point2D pt : mfd) {
			if (pt.getY() > 0) {
				minIncrMag = pt.getX();
				break;
			}
		}
		if (Double.isFinite(minIncrMag)) {
			for (DiscretizedFunc func : cmlFuncs) {
				if (func.getMinX() < minIncrMag && func.getMaxX() > minIncrMag) {
					maxY = Math.max(maxY, func.getInterpolatedY_inLogYDomain(minIncrMag));
					if (func instanceof UncertainBoundedDiscretizedFunc) {
						DiscretizedFunc upper = ((UncertainBoundedDiscretizedFunc)func).getUpper();
						maxY = Math.max(maxY, upper.getInterpolatedY_inLogYDomain(minIncrMag));
					}
				}
			}
		}
		return maxY;
	}

	public static void addFromRegMFDs(Color color, List<IncrementalMagFreqDist> incrFuncs,
			List<DiscretizedFunc> cmlFuncs, List<PlotCurveCharacterstics> incrChars,
			List<PlotCurveCharacterstics> cmlChars, BranchRegionalMFDs regMFDModule, MFDType regType, int regionalIndex,
			IncrementalMagFreqDist mfd, EvenlyDiscretizedFunc cmlFunc, PlotCurveCharacterstics pChar, Color refColor,
			int transAlpha) {
		Preconditions.checkState(regType != null);
		IncrementalMagFreqDist[] incrPercentiles;
		EvenlyDiscretizedFunc[] cmlPercentiles;
		if (regionalIndex >= 0) {
			incrPercentiles = regMFDModule.calcRegionalIncrementalFractiles(regType, regionalIndex, standardFractiles);
			cmlPercentiles = regMFDModule.calcRegionalCumulativeFractiles(regType, regionalIndex, standardFractiles);
		} else {
			incrPercentiles = regMFDModule.calcTotalIncrementalFractiles(regType, standardFractiles);
			cmlPercentiles = regMFDModule.calcTotalCumulativeFractiles(regType, standardFractiles);
		}
		
		Color transColor = new Color(refColor.getRed(), refColor.getGreen(), refColor.getBlue(), transAlpha);
		PlotCurveCharacterstics minMaxChar = new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, transColor);
		
		for (IncrementalMagFreqDist bounds : processIncrFractiles(incrPercentiles)) {
			incrFuncs.add(bounds);
			incrChars.add(minMaxChar);
		}
		
		for (UncertainArbDiscFunc cmlBounds : processCmlFractiles(cmlPercentiles,
				regType == MFDType.SUPRA_ONLY ? cmlFunc.getMinX() : Double.POSITIVE_INFINITY)) {
			cmlFuncs.add(cmlBounds);
			cmlChars.add(minMaxChar);
		}
		
		// now add the original MFD again on top, but without a name
		IncrementalMagFreqDist mfd2 = mfd.deepClone();
		mfd2.setName(null);
		incrFuncs.add(mfd2);
		incrChars.add(pChar);
		EvenlyDiscretizedFunc mfd2c = cmlFunc.deepClone();
		mfd2c.setName(null);
		cmlFuncs.add(mfd2c);
		cmlChars.add(pChar);
	}
	
	// must modify code below if you change these
	public static final double[] standardFractiles = {0d, 0.025, 0.16, 0.84, 0.975d, 1d};
	public static final String fractileLabel = "p[0,2.5,16,84,97.5,100]";
	
	public static List<UncertainIncrMagFreqDist> processIncrFractiles(IncrementalMagFreqDist[] incrPercentiles) {
		List<UncertainIncrMagFreqDist> ret = new ArrayList<>();
		int cnt = 0;
		IncrementalMagFreqDist incrMin = incrPercentiles[cnt++];
		IncrementalMagFreqDist incrP025 = incrPercentiles[cnt++];
		IncrementalMagFreqDist incrP16 = incrPercentiles[cnt++];
		IncrementalMagFreqDist incrP84 = incrPercentiles[cnt++];
		IncrementalMagFreqDist incrP975 = incrPercentiles[cnt++];
		IncrementalMagFreqDist incrMax = incrPercentiles[cnt++];
		UncertainBoundedIncrMagFreqDist bounds = new UncertainBoundedIncrMagFreqDist(
				getAvg(incrMin, incrMax), incrMin, incrMax, null);
		UncertainBoundedIncrMagFreqDist bounds95 = new UncertainBoundedIncrMagFreqDist(
				getAvg(incrP025, incrP975), incrP025, incrP975, null);
		UncertainBoundedIncrMagFreqDist bounds68 = new UncertainBoundedIncrMagFreqDist(
				getAvg(incrP16, incrP84), incrP16, incrP84, null);
		bounds.setName(fractileLabel);
		bounds95.setName(null);
		bounds68.setName(null);
		ret.add(bounds);
		ret.add(bounds95);
		ret.add(bounds68);
		return ret;
	}
	
	public static IncrementalMagFreqDist getAvg(IncrementalMagFreqDist lower, IncrementalMagFreqDist upper) {
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(lower.getMinX(), lower.size(), lower.getDelta());
		for (int i=0; i<ret.size(); i++)
			ret.set(i, 0.5*(lower.getY(i)+upper.getY(i)));
		return ret;
	}
	
	public static List<UncertainArbDiscFunc> processCmlFractiles(EvenlyDiscretizedFunc[] cmlPercentiles, double minX) {
		List<UncertainArbDiscFunc> ret = new ArrayList<>();
		int cnt = 0;
		EvenlyDiscretizedFunc cmlMin = cmlPercentiles[cnt++];
		EvenlyDiscretizedFunc cmlP025 = cmlPercentiles[cnt++];
		EvenlyDiscretizedFunc cmlP16 = cmlPercentiles[cnt++];
		EvenlyDiscretizedFunc cmlP84 = cmlPercentiles[cnt++];
		EvenlyDiscretizedFunc cmlP975 = cmlPercentiles[cnt++];
		EvenlyDiscretizedFunc cmlMax = cmlPercentiles[cnt++];
		UncertainArbDiscFunc cmlBounds = new UncertainArbDiscFunc(
				extendCumulativeToLowerBound(getAvg(cmlMin, cmlMax), minX),
				extendCumulativeToLowerBound(cmlMin, minX),
				extendCumulativeToLowerBound(cmlMax, minX));
		UncertainArbDiscFunc cml95 = new UncertainArbDiscFunc(
				extendCumulativeToLowerBound(getAvg(cmlP025, cmlP975), minX),
				extendCumulativeToLowerBound(cmlP025, minX),
				extendCumulativeToLowerBound(cmlP975, minX));
		UncertainArbDiscFunc cml68 = new UncertainArbDiscFunc(
				extendCumulativeToLowerBound(getAvg(cmlP16, cmlP84), minX),
				extendCumulativeToLowerBound(cmlP16, minX),
				extendCumulativeToLowerBound(cmlP84, minX));
		cmlBounds.setName(fractileLabel);
		cml95.setName(null);
		cml68.setName(null);
		ret.add(cmlBounds);
		ret.add(cml95);
		ret.add(cml68);
		return ret;
	}
	
	private static EvenlyDiscretizedFunc getAvg(EvenlyDiscretizedFunc lower, EvenlyDiscretizedFunc upper) {
		EvenlyDiscretizedFunc ret = new EvenlyDiscretizedFunc(lower.getMinX(), lower.size(), lower.getDelta());
		for (int i=0; i<ret.size(); i++)
			ret.set(i, 0.5*(lower.getY(i)+upper.getY(i)));
		return ret;
	}
	
	static DiscretizedFunc extendCumulativeToLowerBound(EvenlyDiscretizedFunc func, double minX) {
		if ((float)func.getMinX() <= (float)minX)
			return func;
		ArbitrarilyDiscretizedFunc ret = new ArbitrarilyDiscretizedFunc();
		ret.set(minX, func.getY(0));
		for (Point2D pt : func)
			ret.set(pt);
		return ret;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}

}
