package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jfree.data.Range;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
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
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.utils.MFD_InversionConstraint;

public class SolMFDPlot extends AbstractSolutionPlot {

	@Override
	public String getName() {
		return "Solution MFDs";
	}
	
	private static final Color SUPRA_SEIS_TARGET_COLOR = Color.CYAN.darker();

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		FaultSystemRupSet rupSet = sol.getRupSet();
		List<MFD_Plot> plots = new ArrayList<>();
		
		double minY = 1e-6;
		double maxY = 1e1;
		if (rupSet.hasModule(InversionTargetMFDs.class)) {
			InversionTargetMFDs targetMFDs = rupSet.getModule(InversionTargetMFDs.class);
			
			MFD_Plot totalPlot = new MFD_Plot("Total Target MFDs", null);
			totalPlot.addComp(targetMFDs.getTotalRegionalMFD(), Color.GREEN.darker(), "Total Target");
			totalPlot.addComp(targetMFDs.getTotalGriddedSeisMFD(), Color.GRAY, "Target Gridded");
			totalPlot.addComp(targetMFDs.getTotalOnFaultSubSeisMFD(), Color.MAGENTA.darker(), "Target Sub-Seis");
			totalPlot.addComp(targetMFDs.getTotalOnFaultSupraSeisMFD(), SUPRA_SEIS_TARGET_COLOR, "Target Supra-Seis");
			plots.add(totalPlot);
			
			List<? extends MFD_InversionConstraint> constraints = targetMFDs.getMFD_Constraints();
			for (MFD_InversionConstraint constraint : constraints) {
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
				if (constraint.getMagFreqDist().equals(targetMFDs.getTotalOnFaultSupraSeisMFD())) {
					// skip it, but set region if applicable
					totalPlot.region = region;
				} else {
					MFD_Plot plot = new MFD_Plot(name, region);
					plot.addComp(constraint.getMagFreqDist(), SUPRA_SEIS_TARGET_COLOR, "Target");
					plots.add(plot);
				}
				// make sure to include the whole constraint in the plot
				for (Point2D pt : constraint.getMagFreqDist())
					if (pt.getY() > 1e-10)
						minY = Math.min(minY, Math.pow(10, Math.floor(Math.log10(pt.getY())+0.1)));
				for (Point2D pt : constraint.getMagFreqDist().getCumRateDistWithOffset())
					maxY = Math.max(maxY, Math.pow(10, Math.ceil(Math.log10(pt.getY())-0.1)));
			}
		} else {
			// generic plot
			Region region = null;
			// see if we have a region
			if (sol.hasModule(GridSourceProvider.class))
				region = sol.getModule(GridSourceProvider.class).getGriddedRegion();
			else if (rupSet.hasModule(FaultGridAssociations.class))
				region = rupSet.getModule(FaultGridAssociations.class).getRegion();
			plots.add(new MFD_Plot("Total MFD", region));
		}

		MinMaxAveTracker magTrack = rupSetMagTrack(rupSet, meta);
		System.out.println("Rup set mags: "+magTrack);
		IncrementalMagFreqDist defaultMFD = initDefaultMFD(magTrack.getMin(), magTrack.getMax());
		Range xRange = xRange(defaultMFD);

		List<PlotSpec> incrSpecs = new ArrayList<>();
		List<PlotSpec> cmlSpecs = new ArrayList<>();
		
		List<String> lines = new ArrayList<>();
		for (MFD_Plot plot : plots) {
			if (plots.size() > 1) {
				if (!lines.isEmpty())
					lines.add("");
				lines.add(getSubHeading()+" "+plot.name);
				lines.add(topLink); lines.add("");
			}
			List<IncrementalMagFreqDist> incrFuncs = new ArrayList<>();
			List<EvenlyDiscretizedFunc> cmlFuncs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			for (int c=0; c<plot.comps.size(); c++) {
				IncrementalMagFreqDist comp = plot.comps.get(c);
				if (comp == null)
					continue;
				comp.setName(plot.compNames.get(c));
				incrFuncs.add(comp);
				cmlFuncs.add(comp.getCumRateDistWithOffset());
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, plot.compColors.get(c)));
			}
			
			if (meta.comparison != null && meta.comparison.sol != null)
				addSolMFDs(meta.comparison.sol, "Comparison", COMP_COLOR, plot.region,
						incrFuncs, cmlFuncs, chars, defaultMFD);
			double myMax = addSolMFDs(sol, "Solution", MAIN_COLOR, plot.region, incrFuncs, cmlFuncs, chars, defaultMFD);
			maxY = Math.max(maxY, Math.pow(10, Math.ceil(Math.log10(myMax)-0.1)));
			
			PlotSpec incrSpec = new PlotSpec(incrFuncs, chars, plot.name, "Magnitude", "Incremental Rate (per yr)");
			PlotSpec cmlSpec = new PlotSpec(cmlFuncs, chars, plot.name, "Magnitude", "Cumulative Rate (per yr)");
			incrSpec.setLegendInset(true);
			cmlSpec.setLegendInset(true);
			
			incrSpecs.add(incrSpec);
			cmlSpecs.add(cmlSpec);
		}
		
		System.out.println("MFD Y-Range: "+minY+" "+maxY);
		Range yRange = new Range(minY, maxY);
		
		for (int i=0; i<plots.size(); i++) {
			MFD_Plot plot = plots.get(i);
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.addLine("Incremental MFDs", "Cumulative MFDs");
			
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
			
			prefix += "_cumulative";
			gp.drawGraphPanel(cmlSpecs.get(i), false, true, xRange, yRange);
			PlotUtils.setXTick(gp, tick);
			PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 850, true, true, true);
			table.addColumn("![Cumulative Plot]("+relPathToResources+"/"+prefix+".png)");
			table.finalizeLine();
			
			lines.addAll(table.build());
		}
		return lines;
	}
	
	static IncrementalMagFreqDist initDefaultMFD(double minMag, double maxMag) {
		minMag = Math.min(5d, Math.floor(minMag));
		maxMag = Math.max(9d, Math.ceil(maxMag));
		double delta = 0.1;
		// offset
		minMag += 0.5*delta;
		maxMag -= 0.5*delta;
		int num = (int)((maxMag - minMag)/delta + 0.5)+1;
		return new IncrementalMagFreqDist(minMag, maxMag, num);
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
		private List<IncrementalMagFreqDist> comps;
		private List<Color> compColors;
		private List<String> compNames;
		
		public MFD_Plot(String name, Region region) {
			this.name = name;
			this.region = region;
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
			List<IncrementalMagFreqDist> incrFuncs, List<EvenlyDiscretizedFunc> cmlFuncs,
			List<PlotCurveCharacterstics> chars, IncrementalMagFreqDist defaultMFD) {
		IncrementalMagFreqDist mfd = sol.calcNucleationMFD_forRegion(
				region, defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size(), false);
		if (sol.hasModule(GridSourceProvider.class)) {
			GridSourceProvider prov = sol.getGridSourceProvider();
			SummedMagFreqDist gridMFD = null;
			GriddedRegion gridReg = prov.getGriddedRegion();
			boolean regionTest = region != null && region != gridReg && !region.getBorder().equals(gridReg.getBorder());
			for (int i=0; i<gridReg.getNodeCount(); i++) {
				IncrementalMagFreqDist nodeMFD = prov.getNodeMFD(i);
				if (nodeMFD == null)
					continue;
				if (regionTest && !region.contains(gridReg.getLocation(i)))
					continue;
				if (gridMFD == null)
					gridMFD = new SummedMagFreqDist(nodeMFD.getMinX(), nodeMFD.getMaxX(), nodeMFD.size());
				gridMFD.addIncrementalMagFreqDist(nodeMFD);
			}
			if (gridMFD != null) {
				if (!name.toLowerCase().contains("comparison")) {
					gridMFD.setName(name+" Gridded");
					incrFuncs.add(gridMFD);
					cmlFuncs.add(gridMFD.getCumRateDistWithOffset());
					chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, avg(color, Color.WHITE)));
//					chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, color.brighter()));
				}
				SummedMagFreqDist totalMFD = new SummedMagFreqDist(mfd.getMinX(), mfd.getMaxX(), mfd.size());
				totalMFD.addIncrementalMagFreqDist(mfd);
				totalMFD.addIncrementalMagFreqDist(gridMFD);
				totalMFD.setName(name+" Total");
				incrFuncs.add(totalMFD);
				cmlFuncs.add(totalMFD.getCumRateDistWithOffset());
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, color.darker()));
				name = name+" Supra-Seis";
			}
		}
		mfd.setName(name);
		incrFuncs.add(mfd);
		EvenlyDiscretizedFunc cmlFunc = mfd.getCumRateDistWithOffset();
		cmlFuncs.add(cmlFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, color));
		return cmlFunc.getMaxY();
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}

}
