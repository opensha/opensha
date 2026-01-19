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
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.MarkdownUtils.TableTextAlignment;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

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
		GriddedRegion gridReg = getProvRegion(gridProv);
		FaultGridAssociations faultAssoc = rupSet.getModule(FaultGridAssociations.class);
		boolean intersectionAssoc = false;
		if (faultAssoc == null) {
			intersectionAssoc = true;
			faultAssoc = FaultGridAssociations.getIntersectionAssociations(rupSet, gridReg);
		}
		for (int i=0; i<gridReg.getNodeCount(); i++) {
			IncrementalMagFreqDist mfd = gridProv.getMFD(i);
			if (mfd != null)
				for (Point2D pt : mfd)
					if (pt.getY() > 0 && pt.getX() > maxMag)
						maxMag = pt.getX();
		}
		
		List<String> lines = new ArrayList<>();
		lines.add("The gridded seismicity model attached to this solution is of type `"
		+ClassUtils.getClassNameWithoutPackage(gridProv.getClass())+"` and has a spatial resolution of "
				+(float)gridReg.getSpacing()+" degrees.");
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
		
		GeographicMapMaker mapMaker = new RupSetMapMaker(sol.getRupSet(), meta.region);
		mapMaker.setWriteGeoJSON(false);
		mapMaker.setSectOutlineChar(null);
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Fraction Strike-Slip", "Fraction Normal", "Fraction Reverse");
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
				+ "both gridded seismicity and fault-based sources, as well as their individual contributions.";
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
		nuclCPT.setLog10(true);
		CPT ratioCPT = null;
		
		GridSourceProvider compGridProv = null;
		FaultGridAssociations compFaultAssoc = null;
		List<IncrementalMagFreqDist> compSolNuclMFDs = null;
		if (meta.hasComparisonSol()) {
			compGridProv = meta.comparison.sol.getGridSourceProvider();
			GriddedRegion compGridReg = getProvRegion(compGridProv);
			if (compGridProv != null) {
				// make sure it's the same region
				if (!gridReg.equalsRegion(compGridReg))
					compGridProv = null;
			}
			if (compGridProv != null) {
				compFaultAssoc = meta.comparison.rupSet.getModule(FaultGridAssociations.class);
				if (compFaultAssoc == null)
					compFaultAssoc = FaultGridAssociations.getIntersectionAssociations(meta.comparison.rupSet, compGridReg);
				compSolNuclMFDs = calcNuclMFDs(meta.comparison.sol);
			}
		}
		
		table = MarkdownUtils.tableBuilder(TableTextAlignment.CENTER);
		
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
			
			GriddedGeoDataSet gridXYZ = calcGriddedNucleationRates(gridProv, myMinMag);
			GriddedGeoDataSet faultXYZ = calcFaultNucleationRates(gridReg, sol, faultAssoc, solNuclMFDs, myMinMag);
			GriddedGeoDataSet xyz = sum(gridXYZ, faultXYZ);
			
			String myLabel = magLabels.get(m);
			String markdownLabel = myLabel.replaceAll("≥", "&ge;");
			String magPrefix = magPrefixes.get(m);
			
			table.initNewLine();
			table.addColumn("__"+markdownLabel+", Total__");
			table.addColumn("__"+markdownLabel+", Gridded Only__");
			table.addColumn("__"+markdownLabel+", Fault Only__");
			table.finalizeLine();
			
			String mainPrefix = "sol_nucl_"+magPrefix;
			mapMaker.plotXYZData(maskZeroesAsNan(xyz), nuclCPT, myLabel+" Nucleation Rate (events/yr)");
			mapMaker.plot(resourcesDir, mainPrefix, " ");

			table.initNewLine();
			table.addColumn("![Map]("+relPathToResources+"/"+mainPrefix+".png)");
			
			mapMaker.plotXYZData(maskZeroesAsNan(gridXYZ), nuclCPT, myLabel+" Gridded Nucleation Rate (events/yr)");
			mapMaker.plot(resourcesDir, mainPrefix+"_gridded", " ");
			table.addColumn("![Map]("+relPathToResources+"/"+mainPrefix+"_gridded.png)");
			mapMaker.plotXYZData(maskZeroesAsNan(faultXYZ), nuclCPT, myLabel+" Fault Nucleation Rate (events/yr)");
			mapMaker.plot(resourcesDir, mainPrefix+"_fault", " ");
			table.addColumn("![Map]("+relPathToResources+"/"+mainPrefix+"_fault.png)");
			
			table.finalizeLine();
			
			if (ratioCPT != null) {
				table.initNewLine().addColumn("").addColumn("__Primary / Comparison Ratios__").addColumn("").finalizeLine();
				GriddedGeoDataSet compGridXYZ = calcGriddedNucleationRates(compGridProv, myMinMag);
				GriddedGeoDataSet compFaultXYZ = calcFaultNucleationRates(gridReg, meta.comparison.sol,
						compFaultAssoc, compSolNuclMFDs, myMinMag);
				GriddedGeoDataSet compXYZ = sum(compGridXYZ, compFaultXYZ);
				
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
				mapMaker.plot(resourcesDir, compPrefix, " ");
				table.addColumn("![Map]("+relPathToResources+"/"+compPrefix+".png)");
				
				ratioXYZ = new GriddedGeoDataSet(gridReg, false);
				for (int i=0; i<xyz.size(); i++) {
					double v1 = gridXYZ.get(i);
					double v2 = compGridXYZ.get(i);
					if (v1 == 0d && v2 == 0d)
						ratioXYZ.set(i, Double.NaN);
					else if (v2 == 0d)
						ratioXYZ.set(i, Double.POSITIVE_INFINITY);
					else
						ratioXYZ.set(i, v1/v2);
				}
				
				mapMaker.plotXYZData(ratioXYZ, ratioCPT, myLabel+" Primary/Comparison Gridded Nucleation Ratio");
				mapMaker.plot(resourcesDir, compPrefix+"_gridded", " ");
				table.addColumn("![Map]("+relPathToResources+"/"+compPrefix+"_gridded.png)");
				
				ratioXYZ = new GriddedGeoDataSet(gridReg, false);
				for (int i=0; i<xyz.size(); i++) {
					double v1 = faultXYZ.get(i);
					double v2 = compFaultXYZ.get(i);
					if (v1 == 0d && v2 == 0d)
						ratioXYZ.set(i, Double.NaN);
					else if (v2 == 0d)
						ratioXYZ.set(i, Double.POSITIVE_INFINITY);
					else
						ratioXYZ.set(i, v1/v2);
				}
				
				mapMaker.plotXYZData(ratioXYZ, ratioCPT, myLabel+" Primary/Comparison Fault Nucleation Ratio");
				mapMaker.plot(resourcesDir, compPrefix+"_fault", " ");
				table.addColumn("![Map]("+relPathToResources+"/"+compPrefix+"_fault.png)");
				table.finalizeLine();
			}
		}
		
		lines.addAll(table.build());
		
		// moment rates
		GriddedGeoDataSet gridXYZ = calcGriddedNucleationMomentRates(gridProv);
		GriddedGeoDataSet faultXYZ = calcFaultNucleationMomentRates(gridReg, sol, faultAssoc, solNuclMFDs);
		
		GriddedGeoDataSet xyz = sum(gridXYZ, faultXYZ);
		double minMoRate = Double.POSITIVE_INFINITY;
		double maxMoRate = Double.NEGATIVE_INFINITY;
		for (int i=0; i<xyz.size(); i++) {
			double val = xyz.get(i);
			if (val > 0d) {
				minMoRate = Math.min(minMoRate, val);
				maxMoRate = Math.max(maxMoRate, val);
			}
		}
		if (minMoRate < maxMoRate) {
			double logMaxMo = Math.ceil(Math.log10(maxMoRate));
			double logMinMo = Math.max(logMaxMo-10d, Math.floor(Math.log10(minMoRate)));
			
			CPT moCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(logMinMo, logMaxMo);
			moCPT.setNanColor(Color.WHITE);
			moCPT.setLog10(true);
			
			GriddedGeoDataSet faultGriddedRatioXYZ = new GriddedGeoDataSet(gridReg, false);
			for (int i=0; i<xyz.size(); i++)
				faultGriddedRatioXYZ.set(i, faultXYZ.get(i)/gridXYZ.get(i));
			
			CPT moRatioCPT = GMT_CPT_Files.DIVERGING_BLUE_RED_UNIFORM.instance().rescale(-1d, 1d);
			moRatioCPT.setLog10(true);
			
			table = MarkdownUtils.tableBuilder(TableTextAlignment.CENTER);
			
			table.initNewLine();
			table.addColumn("__Total Moment Rate__");
			table.addColumn("__Fault/Gridded Moment Rate Ratio__");
			table.finalizeLine();
			
			String prefix = "sol_nucl_moment";
			
			table.initNewLine();
			mapMaker.plotXYZData(xyz, moCPT, "Total Moment Rate (N-m/yr)");
			mapMaker.plot(resourcesDir, prefix, " ");
			table.addColumn("![Map]("+relPathToResources+"/"+prefix+".png)");
			mapMaker.plotXYZData(faultGriddedRatioXYZ, moRatioCPT, "Fault/Gridded Moment Rate Ratio");
			mapMaker.plot(resourcesDir, prefix+"_fault_gridded_ratio", " ");
			table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_fault_gridded_ratio.png)");
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("__Fault Moment Rate__");
			table.addColumn("__Gridded Moment Rate__");
			table.finalizeLine();
			
			table.initNewLine();
			mapMaker.plotXYZData(maskZeroesAsNan(faultXYZ), moCPT, "Fault Moment Rate (N-m/yr)");
			mapMaker.plot(resourcesDir, prefix+"_fault", " ");
			table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_fault.png)");
			mapMaker.plotXYZData(maskZeroesAsNan(gridXYZ), moCPT, "Gridded Moment Rate (N-m/yr)");
			mapMaker.plot(resourcesDir, prefix+"_gridded", " ");
			table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_gridded.png)");
			table.finalizeLine();
			
			File gridMoRatesFile = new File(resourcesDir, "gridded_moment_rates.xyz");
			File faultMoRatesFile = new File(resourcesDir, "fault_moment_rates.xyz");
			GriddedGeoDataSet.writeXYZFile(gridXYZ, gridMoRatesFile);
			GriddedGeoDataSet.writeXYZFile(faultXYZ, faultMoRatesFile);
			lines.add("");
			lines.add("Download moment rate XYZ data: "
					+ "[Fault moment Rates]("+relPathToResources+"/"+faultMoRatesFile.getName()+") "
					+ "[Gridded moment Rates]("+relPathToResources+"/"+gridMoRatesFile.getName()+")");
			lines.add("");
			lines.add("The following table shows nucleation moment rates in each cell, as well as the ratio of fault "
					+ "to gridded moment.");
			lines.add("");
			lines.addAll(table.build());
		}
		
		return lines;
	}
	
	public static List<IncrementalMagFreqDist> calcNuclMFDs(FaultSystemSolution sol) {
		List<IncrementalMagFreqDist> ret = new ArrayList<>();
		FaultSystemRupSet rupSet = sol.getRupSet();
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(rupSet);
		for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++)
			ret.add(sol.calcNucleationMFD_forSect(sectIndex, refMFD.getMinX(), refMFD.getMaxX(), refMFD.size()));
		return ret;
	}
	
	public static GriddedGeoDataSet calcGriddedNucleationRates(GridSourceProvider gridProv, double minMag) {
		return calcGriddedNucleationRates(gridProv, getProvRegion(gridProv), minMag);
	}
	
	public static GriddedGeoDataSet calcGriddedNucleationRates(GridSourceProvider gridProv, GriddedRegion gridReg, double minMag) {
		GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg, false);
		
		boolean sameIndexing = gridProv.getGriddedRegion() == gridReg;
		for (int i=0; i<xyz.size(); i++) {
			Location loc = xyz.getLocation(i);
			int provIndex = sameIndexing ? i : gridProv.getLocationIndex(loc);
			if (provIndex >= 0) {
				IncrementalMagFreqDist mfd = gridProv.getMFD(provIndex);
				if (mfd != null && (float)(mfd.getMaxX()+0.5*mfd.getDelta()) >= (float)minMag) {
					for (int j=mfd.getClosestXIndex(minMag+0.001); j<mfd.size(); j++) {
						xyz.set(i, xyz.get(i)+mfd.getY(j));
					}
				}
			}
		}
		
		return xyz;
	}
	
	public static GriddedGeoDataSet calcFaultNucleationRates(GriddedRegion gridReg, FaultSystemSolution sol,
			FaultGridAssociations faultGridAssoc, List<IncrementalMagFreqDist> sectNuclMFDs, double minMag) {
		GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg, false);
		
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
	
	public static GriddedGeoDataSet calcGriddedNucleationMomentRates(GridSourceProvider gridProv) {
		return calcGriddedNucleationMomentRates(gridProv, getProvRegion(gridProv));
	}
	
	public static GriddedGeoDataSet calcGriddedNucleationMomentRates(GridSourceProvider gridProv, GriddedRegion gridReg) {
		GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg, false);

		boolean sameIndexing = gridProv.getGriddedRegion() == gridReg;
		for (int i=0; i<xyz.size(); i++) {
			Location loc = xyz.getLocation(i);
			int provIndex = sameIndexing ? i : gridProv.getLocationIndex(loc);
			if (provIndex >= 0) {
				IncrementalMagFreqDist mfd = gridProv.getMFD(provIndex);
				if (mfd != null)
					xyz.set(i, xyz.get(i)+mfd.getTotalMomentRate());
			}
		}
		
		return xyz;
	}
	
	public static GriddedGeoDataSet calcFaultNucleationMomentRates(GriddedRegion gridReg, FaultSystemSolution sol,
			FaultGridAssociations faultGridAssoc, List<IncrementalMagFreqDist> sectNuclMFDs) {
		GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg, false);
		
		// add in faults
		for (int sectIndex=0; sectIndex<sectNuclMFDs.size(); sectIndex++) {
			IncrementalMagFreqDist mfd = sectNuclMFDs.get(sectIndex);
			if (mfd != null) {
				double moment = mfd.getTotalMomentRate();
				Map<Integer, Double> nodeFracts = faultGridAssoc.getNodeFractions(sectIndex);
				for (int nodeIndex : nodeFracts.keySet()) {
					double nodeFract = nodeFracts.get(nodeIndex);
					xyz.set(nodeIndex, xyz.get(nodeIndex)+moment*nodeFract);
				}
			}
		}
		
		return xyz;
	}
	
	private static GriddedRegion getProvRegion(GridSourceProvider gridProv) {
		GriddedRegion gridReg = gridProv.getGriddedRegion();
		if (gridReg == null) {
			LocationList locs = new LocationList();
			for (int i=0; i<gridProv.getNumLocations(); i++)
				locs.add(gridProv.getLocation(i));
			gridReg = GriddedRegion.inferEncompassingRegion(locs);
		}
		return gridReg;
	}
	
	private static GriddedGeoDataSet sum(GriddedGeoDataSet xyz1, GriddedGeoDataSet xyz2) {
		Preconditions.checkState(xyz1.size() == xyz2.size());
		
		GriddedGeoDataSet ret = new GriddedGeoDataSet(xyz1.getRegion(), false);
		
		for (int i=0; i<ret.size(); i++)
			ret.set(i, xyz1.get(i)+xyz2.get(i));
		
		return ret;
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
