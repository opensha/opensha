package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainArbDiscFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
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
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;

public class SolMFDPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Solution MFDs";
	}
	
	private static final Color SUPRA_SEIS_TARGET_COLOR = Color.CYAN.darker();

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta,
			File resourcesDir, String relPathToResources, String topLink) throws IOException {
		if (sol == null && !rupSet.hasModule(InversionTargetMFDs.class))
			// need a solution or targets
			return null;
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
					MFD_Plot plot = new MFD_Plot(name, region);
					plot.addComp(constraint, SUPRA_SEIS_TARGET_COLOR, "Target");
					plots.add(plot);
				}
				// make sure to include the whole constraint in the plot
				for (Point2D pt : constraint)
					if (pt.getY() > 1e-10)
						minY = Math.min(minY, Math.pow(10, Math.floor(Math.log10(pt.getY())+0.1)));
				for (Point2D pt : constraint.getCumRateDistWithOffset())
					maxY = Math.max(maxY, Math.pow(10, Math.ceil(Math.log10(pt.getY())-0.1)));
			}
		} else {
			Preconditions.checkState(sol != null);
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
		
		for (MFD_Plot plot : plots) {
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
					UncertainBoundedIncrMagFreqDist sigmaIncrBounds =
							((UncertainIncrMagFreqDist)comp).estimateBounds(UncertaintyBoundType.ONE_SIGMA);
					sigmaIncrBounds.setName("± σ");
					
					incrFuncs.add(sigmaIncrBounds);
					incrChars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f,
							new Color(color.getRed(), color.getGreen(), color.getBlue(), 60)));
					
					EvenlyDiscretizedFunc upperCumulative = sigmaIncrBounds.getUpper().getCumRateDistWithOffset();
					EvenlyDiscretizedFunc lowerCumulative = sigmaIncrBounds.getLower().getCumRateDistWithOffset();
					Preconditions.checkState(cumulative.size() == upperCumulative.size());
					for (int i=0; i<cumulative.size(); i++) {
						upperCumulative.set(i, Math.max(cumulative.getY(i), upperCumulative.getY(i)));
						lowerCumulative.set(i, Math.max(0, Math.min(cumulative.getY(i), lowerCumulative.getY(i))));
					}
					
					UncertainArbDiscFunc cmlBounded = new UncertainArbDiscFunc(cumulative, lowerCumulative, upperCumulative);
					cmlBounded.setName("± σ");
					cmlFuncs.add(cmlBounded);
					cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f,
							new Color(color.getRed(), color.getGreen(), color.getBlue(), 60)));
				}
			}
			
			if (meta.comparison != null && meta.comparison.sol != null)
				addSolMFDs(meta.comparison.sol, "Comparison", COMP_COLOR, plot.region,
						incrFuncs, cmlFuncs, incrChars, cmlChars, defaultMFD);
			if (sol != null) {
				double myMax = addSolMFDs(sol, "Solution", MAIN_COLOR, plot.region,
						incrFuncs, cmlFuncs, incrChars, cmlChars, defaultMFD);
				maxY = Math.max(maxY, Math.pow(10, Math.ceil(Math.log10(myMax)-0.1)));
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

		List<String> lines = new ArrayList<>();
		for (int i=0; i<plots.size(); i++) {
			MFD_Plot plot = plots.get(i);
			if (plots.size() > 1) {
				if (!lines.isEmpty())
					lines.add("");
				lines.add(getSubHeading()+" "+plot.name);
				lines.add(topLink); lines.add("");
			}
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
	
	public static IncrementalMagFreqDist initDefaultMFD(double minMag, double maxMag) {
		minMag = Math.min(5d, Math.floor(minMag));
		maxMag = Math.max(9d, Math.ceil(maxMag));
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
			List<IncrementalMagFreqDist> incrFuncs, List<DiscretizedFunc> cmlFuncs,
			List<PlotCurveCharacterstics> incrChars, List<PlotCurveCharacterstics> cmlChars,
			IncrementalMagFreqDist defaultMFD) {
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
					PlotCurveCharacterstics pChar = new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, avg(color, Color.WHITE));
					incrChars.add(pChar);
					cmlChars.add(pChar);
//					chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, color.brighter()));
				}
				SummedMagFreqDist totalMFD = new SummedMagFreqDist(mfd.getMinX(), mfd.getMaxX(), mfd.size());
				totalMFD.addIncrementalMagFreqDist(mfd);
				totalMFD.addIncrementalMagFreqDist(gridMFD);
				totalMFD.setName(name+" Total");
				incrFuncs.add(totalMFD);
				cmlFuncs.add(totalMFD.getCumRateDistWithOffset());
				PlotCurveCharacterstics pChar = new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, color.darker());
				incrChars.add(pChar);
				cmlChars.add(pChar);
				name = name+" Supra-Seis";
			}
		}
		mfd.setName(name);
		incrFuncs.add(mfd);
		EvenlyDiscretizedFunc cmlFunc = mfd.getCumRateDistWithOffset();
		cmlFuncs.add(cmlFunc);
		PlotCurveCharacterstics pChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, color);
		incrChars.add(pChar);
		cmlChars.add(pChar);
		return cmlFunc.getMaxY();
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}

}
