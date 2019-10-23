package scratch.UCERF3.erf.ETAS;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSetMath;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.analysis.FaultBasedMapGen;
import scratch.UCERF3.erf.FSSRupsInRegionCache;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.utils.FaultSystemIO;

public class HaywiredSRL_PaperCalc {
	
	private static void calcFractWithinRegion(double[] mags, File binFile, Region region, long maxOT) {
		int[] countsWithin = new int[mags.length];
		int[] countsTotal = new int[mags.length];
		
		int catalogCount = 0;
		
		for (List<ETAS_EqkRupture> catalog : ETAS_CatalogIO.getBinaryCatalogsIterable(binFile, StatUtils.min(mags))) {
			if (catalogCount++ % 5000 == 0)
				System.out.println("Processing catalog "+catalogCount);
			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getOriginTime() > maxOT)
					break;
				double mag = rup.getMag();
				boolean inside = region.contains(rup.getHypocenterLocation());
				for (int i=0; i<mags.length; i++) {
					if (mag >= mags[i]) {
						Preconditions.checkState(countsTotal[i] < Integer.MAX_VALUE);
						countsTotal[i]++;
						if (inside)
							countsWithin[i]++;
					}
				}
			}
		}
		for (int i=0; i<mags.length; i++) {
			System.out.println("M>="+mags[i]);
			double fract = (double)countsWithin[i]/countsTotal[i];
			System.out.println(countsWithin[i]+"/"+countsTotal[i]+" = "+(float)fract+" within");
		}
	}
	
	private static final double gridRegDiscr = 0.02;
	
	public static GriddedGeoDataSet calcTINucleation(
			GriddedRegion reg, FaultSystemSolution sol, double duration, double minMag) {
		// first ruptures
		
		// cache fraction of each subsection surface associated with each node
		System.out.println("Caching sect to node mappings with "+reg.getNodeCount()+" nodes");
		List<Map<Integer, Double>> sectFractNodesMaps = new ArrayList<>();
		FaultSystemRupSet rupSet = sol.getRupSet();
		for (int s=0; s<rupSet.getNumSections(); s++) {
			Map<Integer, Double> sectFractNodesMap = new HashMap<>();
			sectFractNodesMaps.add(sectFractNodesMap);
			StirlingGriddedSurface surf = rupSet.getFaultSectionData(s).getStirlingGriddedSurface(1d);
			LocationList locs = surf.getEvenlyDiscritizedListOfLocsOnSurface();
			double fractEach = 1d/locs.size();
			for (Location loc : locs) {
				int index = reg.indexForLocation(loc);
				if (index < 0)
					continue;
				Double fract = sectFractNodesMap.get(index);
				if (fract == null)
					fract = 0d;
				fract += fractEach;
				sectFractNodesMap.put(index, fract);
			}
		}
		
		System.out.println("Calculating supra-seismogenic");
		GriddedGeoDataSet map = new GriddedGeoDataSet(reg, false);
		
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			double rate = sol.getRateForRup(r)*duration;
			for (int s : rupSet.getSectionsIndicesForRup(r)) {
				Map<Integer, Double> nodeMappings = sectFractNodesMaps.get(s);
				for (int index : nodeMappings.keySet())
					map.set(index, map.get(index)+rate*nodeMappings.get(index));
			}
		}
		
		System.out.println("Associating low res to high res grid indexes");
		Map<Integer, List<Integer>> lowToHighResIndexMap = new HashMap<>();
		GridSourceProvider gridProv = sol.getGridSourceProvider();
		GriddedRegion lowResReg = gridProv.getGriddedRegion();
		for (int i=0; i<reg.getNodeCount(); i++) {
			Location loc = reg.getLocation(i);
			int lowResIndex = lowResReg.indexForLocation(loc);
			List<Integer> indexes = lowToHighResIndexMap.get(lowResIndex);
			if (indexes == null) {
				indexes = new ArrayList<>();
				lowToHighResIndexMap.put(lowResIndex, indexes);
			}
			indexes.add(i);
		}
		
		System.out.println("Calculating gridded");
		for (int lowResIndex=0; lowResIndex<gridProv.size(); lowResIndex++) {
			List<Integer> indexes = lowToHighResIndexMap.get(lowResIndex);
			IncrementalMagFreqDist mfd = gridProv.getNodeMFD(lowResIndex);
			double rateAbove = 0d;
			for (Point2D pt : mfd)
				if (pt.getX() >= minMag)
					rateAbove += pt.getY();
			rateAbove *= duration;
			double rateEach = rateAbove/indexes.size();
			for (int i : indexes)
				map.set(i, map.get(i)+rateEach);
		}
		
		System.out.println("DONE");
		
		return map;
	}
	
	private static void writeGriddedRegCSV(GriddedGeoDataSet map, File csvFile) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Index", "Value");
		for (int i=0; i<map.size(); i++)
			csv.addLine(i+"", map.get(i)+"");
		csv.writeToFile(csvFile);
	}
	
	private static GriddedGeoDataSet loadGriddedRegCSV(GriddedRegion reg, File csvFile) throws IOException {
		GriddedGeoDataSet map = new GriddedGeoDataSet(reg, false);
		CSVFile<String> csv = CSVFile.readFile(csvFile, true);
		
		for (int row=1; row<csv.getNumRows(); row++) {
			int index = Integer.parseInt(csv.get(row, 0));
			double val = Double.parseDouble(csv.get(row, 1));
			map.set(index, val);
		}
		
		return map;
	}
	
	public static GriddedGeoDataSet calcETASNucleation(GriddedRegion reg, File binFile, long maxOT, double minMag) {
		GriddedGeoDataSet map = new GriddedGeoDataSet(reg, false);
		
		int catalogCount = 0;
		
		for (List<ETAS_EqkRupture> catalog : ETAS_CatalogIO.getBinaryCatalogsIterable(binFile, minMag)) {
			if (catalogCount++ % 5000 == 0)
				System.out.println("Processing catalog "+catalogCount);
			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getOriginTime() > maxOT)
					break;
				int index = reg.indexForLocation(rup.getHypocenterLocation());
				if (index < 0)
					continue;
				
				map.set(index, map.get(index)+1d);
			}
		}
		map.scale(1d/catalogCount);
		
		return map;
	}
	
	private static void plotRatio(GriddedRegion gridReg, GeoDataSet numerator, GeoDataSet denominator,
			CPT cpt, String label, boolean log, File outputDir, String prefix) throws GMT_MapException, IOException {
		GeoDataSet ratio = GeoDataSetMath.divide(numerator, denominator);
		for (int i=0; i<ratio.size(); i++)
			if (!Double.isFinite(ratio.get(i)) || ratio.get(i) == 0)
				ratio.set(i, Double.NaN);
		if (log)
			ratio.log10();
		
		Region plotReg = new Region(new Location(gridReg.getMinGridLat(), gridReg.getMinGridLon()),
				new Location(gridReg.getMaxGridLat(), gridReg.getMaxGridLon()));
		
		GMT_Map map = FaultBasedMapGen.buildMap(cpt, null, null, ratio, gridReg.getSpacing(), plotReg, false, label);

		FaultBasedMapGen.plotMap(outputDir, prefix, false, map);
	}

	public static void main(String[] args) throws IOException, DocumentException, GMT_MapException {
		FaultBasedMapGen.SAVE_ZIPS = true;
		FaultBasedMapGen.LOCAL_MAPGEN = true;
		
		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/haywired_srl");
		File nuclMapDir = new File(outputDir, "nucleation_rate_maps");
		Preconditions.checkState(nuclMapDir.exists() || nuclMapDir.mkdir());
		File faultParticDir = new File(outputDir, "fault_participation");
		Preconditions.checkState(faultParticDir.exists() || faultParticDir.mkdir());
		
		long otScenario = 1325419200000l;
		long otScenarioOneWeek = otScenario + ProbabilityModelsCalc.MILLISEC_PER_DAY*7;
		long otScenarioOneYear = otScenario + (long)(ProbabilityModelsCalc.MILLISEC_PER_YEAR*1);
		long otScenarioTenYear = otScenario + (long)(ProbabilityModelsCalc.MILLISEC_PER_YEAR*10);
		double durationOneWeek = 7/365.25;
		
		boolean calcFractInReg = false;
		boolean plotFaultGainTI = false;
		boolean plotGriddedGainTI = false;
		boolean plotFaultGriddedRatio = false;
		boolean plotFaultPartics = true;
		
		File faultSimDir = new File("/data/kevin/ucerf3/etas/simulations/"
				+ "2016_06_15-haywired_m7-10yr-full_td-no_ert-combined");
		File faultFullFile = new File(faultSimDir, "results_descendents.bin");
		File faultM5File = new File(faultSimDir, "results_descendents_m5.bin");
		
		File griddedSimDir = new File("/data/kevin/ucerf3/etas/simulations/"
				+ "2017_01_02-haywired_m7-10yr-gridded-only-200kcombined");
		File griddedFullFile = new File(griddedSimDir, "results_descendents_combined.bin");
		File griddedM5File = new File(griddedSimDir, "results_descendents_m5_preserve.bin");
		
		File fssFile = new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
		FaultSystemSolution refSol = null;
		
		if (calcFractInReg)
			calcFractWithinRegion(new double[] {2.5, 5},  faultFullFile, new CaliforniaRegions.SF_BOX(), otScenarioOneWeek);
		
		GriddedRegion reg = new GriddedRegion(new CaliforniaRegions.RELM_TESTING(),
				gridRegDiscr, GriddedRegion.ANCHOR_0_0);
		File tiNucleationFile = new File(nuclMapDir, "u3_ti_nucleation_one_week.csv");
		GriddedGeoDataSet tiNucleation = null;
		if (tiNucleationFile.exists()) {
			System.out.println("Loading TI nucleation file");
			tiNucleation = loadGriddedRegCSV(reg, tiNucleationFile);
		} else {
			refSol = FaultSystemIO.loadSol(fssFile);
			tiNucleation = calcTINucleation(reg, refSol, durationOneWeek, 2.5);
			writeGriddedRegCSV(tiNucleation, tiNucleationFile);
		}
		
		File etasFaultNuclFile = new File(nuclMapDir, "etas_fault_based_one_week.csv");
		GriddedGeoDataSet etasFaultNucl = null;
		if (etasFaultNuclFile.exists()) {
			System.out.println("Loading ETAS fault based nucleation");
			etasFaultNucl = loadGriddedRegCSV(reg, etasFaultNuclFile);
		} else {
			System.out.println("Calculating ETAS fault based nucleation");
			etasFaultNucl = calcETASNucleation(reg, faultFullFile, otScenarioOneWeek, 2.5);
			writeGriddedRegCSV(etasFaultNucl, etasFaultNuclFile);
		}
		
		File etasGriddedNuclFile = new File(nuclMapDir, "etas_gridded_one_week.csv");
		GriddedGeoDataSet etasGriddedNucl = null;
		if (etasGriddedNuclFile.exists()) {
			System.out.println("Loading ETAS gridded nucleation");
			etasGriddedNucl = loadGriddedRegCSV(reg, etasGriddedNuclFile);
		} else {
			System.out.println("Calculating ETAS gridded nucleation");
			etasGriddedNucl = calcETASNucleation(reg, griddedFullFile, otScenarioOneWeek, 2.5);
			writeGriddedRegCSV(etasGriddedNucl, etasGriddedNuclFile);
		}
		
		CPT logGainCPT = GMT_CPT_Files.UCERF3_ETAS_GAIN.instance().rescale(0d, 1d);
		// from 0 to 1, we want to rescale the portion from 0.5 to 1
		for (int i=logGainCPT.size(); --i>=0;)
			if (logGainCPT.get(i).start <= 0.5f)
				logGainCPT.remove(i);
		logGainCPT = logGainCPT.rescale(0d, 4d);
		logGainCPT.setBelowMinColor(logGainCPT.getMinColor());
		
		GeoDataSet faultPlusTI = GeoDataSetMath.add(etasFaultNucl, tiNucleation);
		if (plotFaultGainTI)
			plotRatio(reg, faultPlusTI, tiNucleation, logGainCPT, "Log10 Gain Fault ETAS/U3TI",
					true, nuclMapDir, "gain_fault_vs_ti");
		GeoDataSet griddedPlusTI = GeoDataSetMath.add(etasGriddedNucl, tiNucleation);
		if (plotGriddedGainTI)
			plotRatio(reg, griddedPlusTI, tiNucleation, logGainCPT, "Log10 Gain Gridded ETAS/U3TI",
					true, nuclMapDir, "gain_gridded_vs_ti");
		
		CPT ratioCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-2, 2);
		ratioCPT.setNanColor(Color.WHITE);
		if (plotFaultGriddedRatio)
			plotRatio(reg, faultPlusTI, griddedPlusTI, ratioCPT, "Log10 Fault/Gridded ETAS",
					true, nuclMapDir, "ratio_fault_gridded");
		
		if (plotFaultPartics) {
			if (refSol == null)
				refSol = FaultSystemIO.loadSol(fssFile);
			
			double[] faultMinMags = { 0, 6.7, 7.8 };
			List<? extends List<ETAS_EqkRupture>> catalogs = ETAS_CatalogIO.loadCatalogsBinary(faultM5File);
			
			CPT particCPT = CPT.loadFromFile(new File(faultParticDir, "faultPartRate.cpt"));
			CPT particGainCPT = CPT.loadFromFile(new File(faultParticDir, "faultPartGain.cpt"));
			
			ETAS_MultiSimAnalysisTools.plotSectRates(catalogs, 7d/365.25, refSol.getRupSet(), faultMinMags,
					faultParticDir, "1 Week", "week", otScenarioOneWeek, refSol, true, particCPT, particGainCPT);
			ETAS_MultiSimAnalysisTools.plotSectRates(catalogs, 1d, refSol.getRupSet(), faultMinMags,
					faultParticDir, "1 Year", "year", otScenarioOneYear, refSol, true, particCPT, particGainCPT);
			ETAS_MultiSimAnalysisTools.plotSectRates(catalogs, 10d, refSol.getRupSet(), faultMinMags,
					faultParticDir, "10 Year", "ten_year", otScenarioTenYear, refSol, true, particCPT, particGainCPT);
		}
	}

}
