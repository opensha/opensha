package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

public class NucleationRatePlot extends AbstractSolutionPlot {

	@Override
	public String getName() {
		return "Regional Nucleation Rates";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		FaultSystemRupSet rupSet = sol.getRupSet();
		double maxMag = rupSet.getMaxMag();
		
		GridSourceProvider gridProv = sol.requireModule(GridSourceProvider.class);
		FaultGridAssociations faultAssoc = rupSet.getModule(FaultGridAssociations.class);
		boolean intersectionAssoc = false;
		if (faultAssoc == null) {
			intersectionAssoc = true;
			faultAssoc = FaultGridAssociations.getIntersectionAssociations(rupSet, gridProv.getGriddedRegion());
		}
		GriddedRegion gridReg = gridProv.getGriddedRegion();
		for (int i=0; i<gridReg.getNodeCount(); i++) {
			IncrementalMagFreqDist mfd = gridProv.getMFD(i);
			if (mfd != null)
				for (Point2D pt : mfd)
					if (pt.getY() > 0 && pt.getX() > maxMag)
						maxMag = pt.getX();
		}
		
		List<String> lines = new ArrayList<>();
		lines.add("These plots include both gridded seismicity and fault sources. The gridded seismicity"
				+ "model attached to this solution is of type _"+ClassUtils.getClassNameWithoutPackage(gridProv.getClass())
				+"_ and has a spatial resolution of "+(float)gridProv.getGriddedRegion().getSpacing()+" degrees.");
		lines.add("");
		lines.add("The following maps show the faulting type at each grid cell from the gridded seismicity model:");
		lines.add("");
		
		CPT fractCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 1d);
		fractCPT.setNanColor(Color.WHITE);
		
		GriddedGeoDataSet ssMap = new GriddedGeoDataSet(gridReg, false);
		GriddedGeoDataSet revMap = new GriddedGeoDataSet(gridReg, false);
		GriddedGeoDataSet normMap = new GriddedGeoDataSet(gridReg, false);
		for (int i=0; i<gridReg.getNodeCount(); i++) {
			ssMap.set(i, gridProv.getFracStrikeSlip(i));
			revMap.set(i, gridProv.getFracReverse(i));
			normMap.set(i, gridProv.getFracNormal(i));
		}
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(sol.getRupSet(), meta.region);
		mapMaker.setWriteGeoJSON(false);
		mapMaker.setSectOutlineChar(null);
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Fracion Strike-Slip", "Fracion Normal", "Fracion Reverse");
		table.initNewLine();
		mapMaker.plotXYZData(ssMap, fractCPT, "Fraction Strike-Slip");
		mapMaker.plot(resourcesDir, "gridded_fract_ss", " ");
		table.addColumn("![SS]("+relPathToResources+"/gridded_fract_ss.png)");
		mapMaker.plotXYZData(normMap, fractCPT, "Fraction Normal");
		mapMaker.plot(resourcesDir, "gridded_fract_norm", " ");
		table.addColumn("![Norm]("+relPathToResources+"/gridded_fract_norm.png)");
		mapMaker.plotXYZData(revMap, fractCPT, "Fraction Reverse");
		mapMaker.plot(resourcesDir, "gridded_fract_rev", " ");
		table.addColumn("![Rev]("+relPathToResources+"/gridded_fract_rev.png)");
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");

		String nuclDescription = "The following maps show the total nucleation rate in each grid cell, summed across "
				+ "both gridded seismicity and fault-based sources.";
		if (intersectionAssoc)
			nuclDescription += " No model-specific fault-to-grid-cell associations were supplied, so rates on faults are "
					+ "mapped to the grid cells that their 3D surfaces intersect.";
		else
			nuclDescription += " Rates from faults are mapped to grid cells according to the model supplied association "
					+ "fractions.";
		lines.add(nuclDescription);
		lines.add("");
		
		List<IncrementalMagFreqDist> solNuclMFDs = calcNuclMFDs(sol);
		
		List<Double> minMags = new ArrayList<>();
		List<String> magLabels = new ArrayList<>();
		List<String> magPrefixes = new ArrayList<>();
		
		minMags.add(5d);
		magLabels.add("M≥5");
		magPrefixes.add("m5");
		
		if (maxMag > 6d) {
			minMags.add(6d);
			magLabels.add("M≥6");
			magPrefixes.add("m6");
		}
		
		if (maxMag > 7d) {
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
		
		CPT nuclCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-6, -1);
		nuclCPT.setNanColor(Color.WHITE);
		
		table = MarkdownUtils.tableBuilder();
		CPT ratioCPT = null;
		
		GridSourceProvider compGridProv = null;
		FaultGridAssociations compFaultAssoc = null;
		List<IncrementalMagFreqDist> compSolNuclMFDs = null;
		if (meta.hasComparisonSol()) {
			compGridProv = meta.comparison.sol.getGridSourceProvider();
			if (compGridProv != null) {
				// make sure it's the same region
				GriddedRegion reg1 = gridProv.getGriddedRegion();
				GriddedRegion reg2 = compGridProv.getGriddedRegion();
				if (!reg1.equalsRegion(reg2))
					compGridProv = null;
			}
			if (compGridProv != null) {
				compFaultAssoc = meta.comparison.rupSet.getModule(FaultGridAssociations.class);
				if (compFaultAssoc == null)
					compFaultAssoc = FaultGridAssociations.getIntersectionAssociations(meta.comparison.rupSet, compGridProv.getGriddedRegion());
				compSolNuclMFDs = calcNuclMFDs(meta.comparison.sol);
			}
		}
		
		if (compGridProv != null) {
			CPT belowCPT = new CPT(0.5d, 1d,
					new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
					Color.WHITE);
			CPT aboveCPT = new CPT(1d, 2d,
					Color.WHITE,
					new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
			ratioCPT = new CPT();
			ratioCPT.addAll(belowCPT);
			ratioCPT.addAll(aboveCPT);
			ratioCPT.setNanColor(Color.LIGHT_GRAY);
			ratioCPT.setBelowMinColor(ratioCPT.getMinColor());
			ratioCPT.setAboveMaxColor(ratioCPT.getMaxColor());
		}
		for (int m=0; m<minMags.size(); m++) {
			double myMinMag = minMags.get(m);
			
			GriddedGeoDataSet xyz = calcNucleationRates(sol, gridProv, faultAssoc, solNuclMFDs, myMinMag);
			GriddedGeoDataSet logXYZ = maskZeroesAsNan(xyz);
			logXYZ.log10();
			
			String myLabel = magLabels.get(m);
			String markdownLabel = myLabel.replaceAll("≥", "&ge;");
			String magPrefix = magPrefixes.get(m);
			
			table.initNewLine();
			table.addColumn(MarkdownUtils.boldCentered(markdownLabel));
			if (compGridProv != null)
				table.addColumn("Primary / Comparison Ratio");
			table.finalizeLine();
			
			String mainPrefix = "sol_nucl_"+magPrefix;
			mapMaker.plotXYZData(logXYZ, nuclCPT, "Log10 "+myLabel+" Nucleation Rate (events/yr)");
			mapMaker.plot(resourcesDir, mainPrefix, " ");

			table.initNewLine();
			table.addColumn("![Map]("+relPathToResources+"/"+mainPrefix+".png)");
			
			if (ratioCPT != null) {
				GriddedGeoDataSet compXYZ = calcNucleationRates(meta.comparison.sol,
						compGridProv, compFaultAssoc, compSolNuclMFDs, myMinMag);
				
				GriddedGeoDataSet ratioXYZ = new GriddedGeoDataSet(gridReg, false);
				for (int i=0; i<xyz.size(); i++) {
					double v1 = xyz.get(i);
					double v2 = compXYZ.get(i);
					if (v1 == 0d && v2 == 0d)
						ratioXYZ.set(i, Double.NaN);
					else if (v2 == 0d)
						ratioXYZ.set(i, Double.POSITIVE_INFINITY);
					else
						ratioXYZ.set(i, v1/v2);
				}
				
				String compPrefix = "sol_nucl_compare_"+magPrefix;
				mapMaker.plotXYZData(ratioXYZ, ratioCPT, myLabel+" Primary/Comparison Nucleation Ratio");
//				mapMaker.plotSectScalars(plotRates, cpt, "Log10 "+myLabel+" Nucleation Rate (events/yr)");
				mapMaker.plot(resourcesDir, compPrefix, " ");
				table.addColumn("![Map]("+relPathToResources+"/"+compPrefix+".png)");
			}
			
			table.finalizeLine();
		}
		
		lines.addAll(table.build());
		
		return lines;
	}
	
	private static List<IncrementalMagFreqDist> calcNuclMFDs(FaultSystemSolution sol) {
		List<IncrementalMagFreqDist> ret = new ArrayList<>();
		FaultSystemRupSet rupSet = sol.getRupSet();
		EvenlyDiscretizedFunc refMFD = SupraSeisBValInversionTargetMFDs.buildRefXValues(rupSet);
		for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++)
			ret.add(sol.calcNucleationMFD_forSect(sectIndex, refMFD.getMinX(), refMFD.getMaxX(), refMFD.size()));
		return ret;
	}
	
	private static GriddedGeoDataSet calcNucleationRates(FaultSystemSolution sol, GridSourceProvider gridProv,
			FaultGridAssociations faultGridAssoc, List<IncrementalMagFreqDist> sectNuclMFDs, double minMag) {
		GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridProv.getGriddedRegion(), false);
		
		// start with gridded
		for (int i=0; i<xyz.size(); i++) {
			IncrementalMagFreqDist mfd = gridProv.getMFD(i);
			if (mfd != null && (float)(mfd.getMaxX()+0.5*mfd.getDelta()) >= (float)minMag) {
				for (int j=mfd.getClosestXIndex(minMag+0.001); j<mfd.size(); j++) {
					xyz.set(i, xyz.get(i)+mfd.getY(j));
				}
			}
		}
		
		// add in faults
		for (int sectIndex=0; sectIndex<sectNuclMFDs.size(); sectIndex++) {
			IncrementalMagFreqDist mfd = sectNuclMFDs.get(sectIndex);
			if (mfd != null && (float)(mfd.getMaxX()+0.5*mfd.getDelta()) >= (float)minMag) {
				Map<Integer, Double> nodeFracts = faultGridAssoc.getNodeFractions(sectIndex);
				for (int nodeIndex : nodeFracts.keySet()) {
					double nodeFract = nodeFracts.get(nodeIndex);
					for (int j=mfd.getClosestXIndex(minMag+0.001); j<mfd.size(); j++) {
						xyz.set(nodeIndex, xyz.get(nodeIndex)+mfd.getY(j)*nodeFract);
					}
				}
			}
		}
		
		return xyz;
	}
	
	private static GriddedGeoDataSet maskZeroesAsNan(GriddedGeoDataSet xyz) {
		GriddedGeoDataSet ret = new GriddedGeoDataSet(xyz.getRegion(), false);
		for (int i=0; i<xyz.size(); i++) {
			double val = xyz.get(i);
			if (val == 0d)
				val = Double.NaN;
			ret.set(i, val);
		}
		return ret;
	}
	
	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(GridSourceProvider.class);
	}

}
