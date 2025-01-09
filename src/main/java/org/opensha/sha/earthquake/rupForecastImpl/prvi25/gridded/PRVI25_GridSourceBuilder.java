package org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.MagScalingRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.CubedGriddedRegion;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.FiniteRuptureConverter;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRuptureProperties;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_FaultCubeAssociations;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_SingleRegionGridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_MaxMagOffFault;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalDeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_DeclusteringAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_RegionalSeismicity;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SeisSmoothingAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionDeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.PRVI25_SeismicityRegions;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import scratch.UCERF3.erf.ETAS.SeisDepthDistribution;

public class PRVI25_GridSourceBuilder {
	
	/**
	 * if true, will subtract on-fault supra-seis rates from gridded MFDs
	 */
	public static boolean RATE_BALANCE_CRUSTAL_GRIDDED = false;
	
	/**
	 * if true, depths and strikes are taken from the mapped subsection
	 * otherwise, they use Slab2 (which won't match for Muertos)
	 */
	public static boolean INTERFACE_USE_SECT_PROPERTIES = true;
	
	public static final double OVERALL_MMIN= 2.55;
	// TODO: preliminary; logic tree branch(es)?
	public static final double SLAB_MMAX = 7.95;
	
	public static void doPreGridBuildHook(FaultSystemSolution sol, LogicTreeBranch<?> faultBranch) throws IOException {
		if (faultBranch.hasValue(PRVI25_CrustalFaultModels.class)) {
			// add fault cube associations and seismicity regions
			FaultSystemRupSet rupSet = sol.getRupSet();
			
			if (!rupSet.hasModule(ModelRegion.class) && faultBranch.hasValue(PRVI25_CrustalFaultModels.class))
				rupSet.addModule(PRVI25_CrustalFaultModels.getDefaultRegion(faultBranch));
			Region modelReg = rupSet.requireModule(ModelRegion.class).getRegion();
			
			FaultCubeAssociations cubeAssociations = rupSet.getModule(FaultCubeAssociations.class);
			if (cubeAssociations == null) {
				GriddedRegion modelGridReg = new GriddedRegion(modelReg, 0.1, GriddedRegion.ANCHOR_0_0);
				cubeAssociations = new NSHM23_FaultCubeAssociations(rupSet, new CubedGriddedRegion(modelGridReg),
						NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST);
				rupSet.addModule(cubeAssociations);
			}
			Preconditions.checkNotNull(cubeAssociations, "Cube associations is null");
		}
	}
	
	public static GridSourceList buildCrustalGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> branch) throws IOException {
		doPreGridBuildHook(sol, branch);
		FaultSystemRupSet rupSet = sol.getRupSet();
		Preconditions.checkState(!branch.hasValue(PRVI25_SubductionFaultModels.class), "This should only be used to build crustal models");
		FaultCubeAssociations cubeAssociations = rupSet.requireModule(FaultCubeAssociations.class);
		NSHM23_SingleRegionGridSourceProvider gridProv = buildCrustalGridSourceProv(sol, branch, PRVI25_SeismicityRegions.CRUSTAL, cubeAssociations);
		
		return gridProv.convertToGridSourceList(OVERALL_MMIN);
	}
	
	private static Double CRUSTAL_FRACT_SS;
	private static Double CRUSTAL_FRACT_REV;
	private static Double CRUSTAL_FRACT_NORM;
	
	private static void checkCalcCrustalFaultCategories() throws IOException {
		if (CRUSTAL_FRACT_SS == null) {
			synchronized (PRVI25_GridSourceBuilder.class) {
				if (CRUSTAL_FRACT_SS == null)
					calcCrustalFaultCategories();
			}
		}
	}
	
	private static void calcCrustalFaultCategories() throws IOException {
		PRVI25_CrustalFaultModels fm = PRVI25_CrustalFaultModels.PRVI_CRUSTAL_FM_V1p1;
		PRVI25_CrustalDeformationModels dm = PRVI25_CrustalDeformationModels.GEOLOGIC_DIST_AVG;
		
		List<? extends FaultSection> sects = dm.build(fm);
		
		double momentSS = 0d;
		double momentRev = 0d;
		double momentNorm = 0d;
		double momentTot = 0d;
		
		for (FaultSection sect : sects) {
			double moment = sect.calcMomentRate(false);
			double rake = sect.getAveRake();
//			System.out.println(sect.getSectionId()+". "+sect.getSectionName()+": rake="+(int)rake+", moment="+(float)rake);
			if ((int)rake == -135 || (int)rake == -45) {
				momentNorm += 0.5*moment;
				momentSS += 0.5*moment;
			} else if ((int)rake == 45 || (int)rake == 135) {
				momentRev += 0.5*moment;
				momentSS += 0.5*moment;
			} else if (rake >= -135 && rake < -45d) {
				momentNorm += moment;
			} else if (rake >= 45 && rake < 135d) {
				momentRev += moment;
			} else {
				momentSS += moment;
			}
			momentTot += moment;
		}
		CRUSTAL_FRACT_SS = momentSS/momentTot;
		CRUSTAL_FRACT_REV = momentRev/momentTot;
		CRUSTAL_FRACT_NORM = momentNorm/momentTot;
		System.out.println("Crustal fractional moments:");
		System.out.println("\tSS:\t"+(float)(momentSS/momentTot));
		System.out.println("\tRev:\t"+(float)(momentRev/momentTot));
		System.out.println("\tNorm:\t"+(float)(momentNorm/momentTot));
	}
	
	public static NSHM23_SingleRegionGridSourceProvider buildCrustalGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> branch,
			PRVI25_SeismicityRegions seisRegion, FaultCubeAssociations cubeAssociations)  throws IOException {
		GriddedRegion gridReg = cubeAssociations.getRegion();
		
		double maxMagOff = branch.requireValue(MaxMagOffFaultBranchNode.class).getMaxMagOffFault();
		
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(
				OVERALL_MMIN, Math.max(maxMagOff, sol.getRupSet().getMaxMag()));
		
		PRVI25_RegionalSeismicity seisBranch = branch.requireValue(PRVI25_RegionalSeismicity.class);
		PRVI25_DeclusteringAlgorithms declusteringAlg = branch.requireValue(PRVI25_DeclusteringAlgorithms.class);
		PRVI25_SeisSmoothingAlgorithms seisSmooth = branch.requireValue(PRVI25_SeisSmoothingAlgorithms.class);
		
		// total G-R up to Mmax
		IncrementalMagFreqDist totalGR = seisBranch.build(seisRegion, refMFD, maxMagOff);

		// figure out what's left for gridded seismicity
		IncrementalMagFreqDist totalGridded = new IncrementalMagFreqDist(
				refMFD.getMinX(), refMFD.size(), refMFD.getDelta());

		IncrementalMagFreqDist solNuclMFD = sol.calcNucleationMFD_forRegion(
				gridReg, refMFD.getMinX(), refMFD.getMaxX(), refMFD.size(), false);
		for (int i=0; i<totalGR.size(); i++) {
			double totalRate = totalGR.getY(i);
			if (totalRate > 0) {
				if (RATE_BALANCE_CRUSTAL_GRIDDED) {
					double solRate = solNuclMFD.getY(i);
					if (solRate > totalRate) {
						System.err.println("WARNING: MFD bulge at M="+(float)refMFD.getX(i)
						+"\tGR="+(float)totalRate+"\tsol="+(float)solRate);
					} else {
						totalGridded.set(i, totalRate - solRate);
					}
				} else {
					totalGridded.set(i, totalRate);
				}
			}
		}

		// focal mechanisms
//		System.err.println("WARNING: DON'T YET HAVE FOCAL MECHS!");
		double[] fractStrikeSlip = new double[gridReg.getNodeCount()];
		double[] fractReverse = new double[gridReg.getNodeCount()];
		double[] fractNormal = new double[gridReg.getNodeCount()];
		checkCalcCrustalFaultCategories();
		for (int i=0; i<fractStrikeSlip.length; i++) {
			// these moment fractions from the crustal DM
			fractStrikeSlip[i] = CRUSTAL_FRACT_SS;
			fractReverse[i] = CRUSTAL_FRACT_REV;
			fractNormal[i] = CRUSTAL_FRACT_NORM;
		}

		// spatial seismicity PDF
		double[] pdf = seisSmooth.load(seisRegion, declusteringAlg);

		// seismicity depth distribution

		// TODO still using UCERF3
		SeisDepthDistribution seisDepthDistribution = new SeisDepthDistribution();
		double delta=2;
		HistogramFunction binnedDepthDistFunc = new HistogramFunction(1d, 12,delta);
		for(int i=0;i<binnedDepthDistFunc.size();i++) {
			double prob = seisDepthDistribution.getProbBetweenDepths(binnedDepthDistFunc.getX(i)-delta/2d,binnedDepthDistFunc.getX(i)+delta/2d);
			binnedDepthDistFunc.set(i,prob);
		}
		//				EvenlyDiscretizedFunc depthNuclDistFunc = NSHM23_SeisDepthDistributions.load(region);

		// only preserve the total MFD (i.e., re-distribute carved out near fault ruptures in the supra mag range to other grid nodes)
		// if we're doing rate balancing. in that case, the passed in MFD already accounts for faults and should be preserved exactly.
		// otherwise if we're not rate balancing, just lop off the gridded rate near faults without redistributing
		boolean preserveTotalMFD = RATE_BALANCE_CRUSTAL_GRIDDED;
		return new NSHM23_SingleRegionGridSourceProvider(sol, cubeAssociations, pdf, totalGridded, preserveTotalMFD, binnedDepthDistFunc,
				fractStrikeSlip, fractNormal, fractReverse, null); // last null means all active
	}
	
	public static GridSourceList buildCombinedSubductionGridSourceList(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch) throws IOException {
		GridSourceList muertosSlab = buildSlabGridSourceList(fullBranch, PRVI25_SeismicityRegions.MUE_INTRASLAB);
		GridSourceList carSlab = buildSlabGridSourceList(fullBranch, PRVI25_SeismicityRegions.CAR_INTRASLAB);
		GridSourceList muertosInterface = buildInterfaceGridSourceList(sol, fullBranch, PRVI25_SeismicityRegions.MUE_INTERFACE);
		GridSourceList carInterface = buildInterfaceGridSourceList(sol, fullBranch, PRVI25_SeismicityRegions.CAR_INTERFACE);
		
		// for some reason, doing them one by one in the combine method doesn't work; do it ahead of time pairwise
		Region muertosUnionRegion = Region.union(PRVI25_SeismicityRegions.MUE_INTRASLAB.load(), PRVI25_SeismicityRegions.MUE_INTERFACE.load());
		Region carUnionRegion = Region.union(PRVI25_SeismicityRegions.CAR_INTRASLAB.load(), PRVI25_SeismicityRegions.CAR_INTERFACE.load());
		Preconditions.checkNotNull(muertosUnionRegion, "Couldn't union Muertos regions");
		Preconditions.checkNotNull(carUnionRegion, "Couldn't union CAR regions");
		Region unionRegion = Region.union(muertosUnionRegion, carUnionRegion);
		Preconditions.checkNotNull(unionRegion, "Couldn't union CAR regions");
		GriddedRegion griddedUnionRegion = new GriddedRegion(unionRegion, muertosSlab.getGriddedRegion().getSpacing(), GriddedRegion.ANCHOR_0_0);

		return GridSourceList.combine(griddedUnionRegion, carInterface, carSlab, muertosInterface, muertosSlab);
	}
	
	private static EnumMap<PRVI25_SeismicityRegions, GriddedGeoDataSet> gridDepths;
	private static EnumMap<PRVI25_SeismicityRegions, GriddedGeoDataSet> gridStrikes;
	private static final String DEPTHS_DIR = "/data/erf/prvi25/seismicity/depths/";
	
	private synchronized static void loadRegionDepthStrikeData(PRVI25_SeismicityRegions seisReg) throws IOException {
		if (gridDepths == null) {
			gridDepths = new EnumMap<>(PRVI25_SeismicityRegions.class);
			gridStrikes = new EnumMap<>(PRVI25_SeismicityRegions.class);
		}
		
		if (!gridDepths.containsKey(seisReg)) {
			Region reg = seisReg.load();
			GriddedRegion gridReg = new GriddedRegion(reg, 0.1, GriddedRegion.ANCHOR_0_0);
			String fileName = DEPTHS_DIR+seisReg.name()+".csv";
			InputStream depthResources = PRVI25_GridSourceBuilder.class.getResourceAsStream(fileName);
			Preconditions.checkNotNull(depthResources, "Depth CSV not found: %s", fileName);
			CSVFile<String> csv = CSVFile.readStream(depthResources, true);
			
			GriddedGeoDataSet depths = new GriddedGeoDataSet(gridReg);
			GriddedGeoDataSet strikes = null;
			
			Preconditions.checkState(csv.getNumRows() == gridReg.getNodeCount()+1);
			for (int row=1; row<csv.getNumRows(); row++) {
				int gridIndex = csv.getInt(row, 0);
				double lat = csv.getDouble(row, 1);
				double lon = csv.getDouble(row, 2);
				Location loc = new Location(lat, lon);
				Preconditions.checkState(LocationUtils.areSimilar(loc, gridReg.getLocation(gridIndex)));
				double depth = csv.getDouble(row, 3);
				FaultUtils.assertValidDepth(depth);
				depths.set(gridIndex, depth);
				String strikeStr = csv.get(row, 4);
				if (strikeStr.isBlank()) {
					Preconditions.checkState(strikes == null);
				} else {
					if (strikes == null) {
						Preconditions.checkState(gridIndex == 0);
						strikes = new GriddedGeoDataSet(gridReg);
					}
					double strike = Double.parseDouble(strikeStr);
					FaultUtils.assertValidStrike(strike);
					strikes.set(gridIndex, strike);
				}
			}
			
			gridDepths.put(seisReg, depths);
			gridStrikes.put(seisReg, strikes);
		}
	}
	
	public static GridSourceList buildSlabGridSourceList(LogicTreeBranch<?> branch) throws IOException {
		GridSourceList muertos = buildSlabGridSourceList(branch, PRVI25_SeismicityRegions.MUE_INTRASLAB);
		GridSourceList car = buildSlabGridSourceList(branch, PRVI25_SeismicityRegions.CAR_INTRASLAB);
		return GridSourceList.combine(car, muertos);
	}
	
	public static GridSourceList buildSlabGridSourceList(LogicTreeBranch<?> branch, PRVI25_SeismicityRegions seisRegion) throws IOException {
		Preconditions.checkState(seisRegion == PRVI25_SeismicityRegions.CAR_INTRASLAB
				|| seisRegion == PRVI25_SeismicityRegions.MUE_INTRASLAB);
		
		double maxMagOff = SLAB_MMAX;
		
		PRVI25_RegionalSeismicity seisBranch = branch.requireValue(PRVI25_RegionalSeismicity.class);
		PRVI25_DeclusteringAlgorithms declusteringAlg = branch.requireValue(PRVI25_DeclusteringAlgorithms.class);
		PRVI25_SeisSmoothingAlgorithms seisSmooth = branch.requireValue(PRVI25_SeisSmoothingAlgorithms.class);
		
		// total G-R up to Mmax
		IncrementalMagFreqDist totalGR = seisBranch.build(seisRegion, FaultSysTools.initEmptyMFD(OVERALL_MMIN, maxMagOff), maxMagOff);
		
		GriddedGeoDataSet pdf = seisSmooth.loadXYZ(seisRegion, declusteringAlg);
		
		List<List<GriddedRupture>> ruptureLists = new ArrayList<>(pdf.size());
		
		loadRegionDepthStrikeData(seisRegion);
		GriddedGeoDataSet depthData = gridDepths.get(seisRegion);
		Preconditions.checkNotNull(depthData);
		
		// this is what nshm23 did for Cascadia
		// slab GMMs don't even use these according to Peter
		double rake = 0d;
		double dip = 90d;
		double strike = Double.NaN;
		
		boolean truePointSources = true;
		double maxDDW = 0d;
		MagScalingRelationship scale = null;
		
//		boolean truePointSources = false;
//		double maxDDW = (15d)/Math.sin(Math.toRadians(dip));
//		MagScalingRelationship scale = new WC1994_MagLengthRelationship();
		
		double hypocentralDepth = Double.NaN;
		double hypocentralDAS = Double.NaN;
		
		for (int gridIndex=0; gridIndex<pdf.size(); gridIndex++) {
			double fract = pdf.get(gridIndex);
			if (fract == 0d) {
				ruptureLists.add(null);
				continue;
			}
			
			double upper = depthData.get(gridIndex);
			Preconditions.checkState(LocationUtils.areSimilar(pdf.getLocation(gridIndex), depthData.getLocation(gridIndex)));
			
			Location loc = pdf.getLocation(gridIndex);
			
			List<GriddedRupture> ruptureList = new ArrayList<>(totalGR.size());
			ruptureLists.add(ruptureList);
			for (int i=0; i<totalGR.size(); i++) {
				double mag = totalGR.getX(i);
				double rate = totalGR.getY(i)*fract;
				
				if (rate == 0d || (float)mag < (float)OVERALL_MMIN)
					continue;
				
				double length, lower;
				if (truePointSources) {
					length = 0d;
					lower = upper;
				} else {
					double ddw;
					if (scale instanceof MagLengthRelationship) {
						length = ((MagLengthRelationship)scale).getMedianLength(mag);
						ddw = Math.min(maxDDW, length);
					} else {
						throw new IllegalStateException();
					}
					if (dip == 90d)
						lower = upper + ddw;
					else
						lower = upper + ddw*Math.sin(Math.toRadians(dip));
				}
				
				GriddedRuptureProperties props = new GriddedRuptureProperties(mag, rake, dip, strike, null,
						upper, lower, length, hypocentralDepth, hypocentralDAS, TectonicRegionType.SUBDUCTION_SLAB);
				ruptureList.add(new GriddedRupture(gridIndex, loc, props, rate));
			}
		}
		
		return new GridSourceList.Precomputed(pdf.getRegion(), TectonicRegionType.SUBDUCTION_SLAB, ruptureLists);
	}
	
	public static GridSourceList buildInterfaceGridSourceList(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch) throws IOException {
		GridSourceList muertos = buildInterfaceGridSourceList(sol, fullBranch, PRVI25_SeismicityRegions.MUE_INTERFACE);
		GridSourceList car = buildInterfaceGridSourceList(sol, fullBranch, PRVI25_SeismicityRegions.CAR_INTERFACE);
		return GridSourceList.combine(car, muertos);
	}
	
	public static GridSourceList buildInterfaceGridSourceList(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch,
			PRVI25_SeismicityRegions seisRegion) throws IOException {
		int[] parentIDs;
		switch (seisRegion) {
		case CAR_INTERFACE:
			parentIDs = new int[] { 7500, 7501 };
			break;
		case MUE_INTERFACE:
			parentIDs = new int[] { 7550 };
			break;

		default:
			throw new IllegalStateException("Not an interface SeismicityRegion: "+seisRegion);
		}
		
		final boolean D = true;
		
		System.out.println("Building interface GridSourceList for "+seisRegion+", useSectProps="+INTERFACE_USE_SECT_PROPERTIES);
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		List<? extends FaultSection> sectsForMinMag = rupSet.getFaultSectionDataList();
		List<? extends FaultSection> sectsForGeom = sectsForMinMag;
		if (INTERFACE_USE_SECT_PROPERTIES && seisRegion == PRVI25_SeismicityRegions.CAR_INTERFACE && !fullBranch.hasValue(PRVI25_SubductionFaultModels.PRVI_SUB_FM_LARGE)) {
			// use the large fault model for rupture properties
			PRVI25_SubductionFaultModels fm = PRVI25_SubductionFaultModels.PRVI_SUB_FM_LARGE;
			PRVI25_SubductionDeformationModels dm = fullBranch.getValue(PRVI25_SubductionDeformationModels.class);
			if (dm == null)
				dm = PRVI25_SubductionDeformationModels.FULL;
			sectsForGeom = dm.build(fm);
		}
		
		PRVI25_RegionalSeismicity seisBranch = fullBranch.requireValue(PRVI25_RegionalSeismicity.class);
		PRVI25_DeclusteringAlgorithms declusteringAlg = fullBranch.requireValue(PRVI25_DeclusteringAlgorithms.class);
		PRVI25_SeisSmoothingAlgorithms seisSmooth = fullBranch.requireValue(PRVI25_SeisSmoothingAlgorithms.class);
		
		PRVI25_SubductionScalingRelationships scaleBranch = fullBranch.requireValue(PRVI25_SubductionScalingRelationships.class);
		MagAreaRelationship scale = scaleBranch.getMagAreaRelationship();
		
		loadRegionDepthStrikeData(seisRegion);
		GriddedGeoDataSet depthData = gridDepths.get(seisRegion);
		Preconditions.checkNotNull(depthData);
		GriddedGeoDataSet strikeData = gridStrikes.get(seisRegion);
		Preconditions.checkNotNull(strikeData);
		
		// total G-R up to Mmax
//		IncrementalMagFreqDist totalGR = seisBranch.build(seisRegion, refMFD, maxMagOff);
		
		GriddedGeoDataSet pdf = seisSmooth.loadXYZ(seisRegion, declusteringAlg);
		GriddedRegion region = pdf.getRegion();
		
		List<FaultSection> minMagMatchingSubSects = null;
		List<Double> minMagMatchingSubSectMMins = null;
		List<RuptureSurface> minMagMatchingSectSurfs = null;
		double maxSectMinMag = 0d;
		int[] minMagSectMappings = null;
		
		List<FaultSection> geomMatchingSubSects = null;
		List<RuptureSurface> geomMatchingSectSurfs = null;
		int[] geomSectMappings = null;

		double overallFurthestMinMagMatch = 0d;
		double overallFurthestGeomMatch = 0d;
		
		for (boolean geom : new boolean[] { false, true }) {
			List<FaultSection> matchingSubSects;
			List<Double> matchingSubSectMMins;
			List<RuptureSurface> matchingSectSurfs;
			int[] sectMappings;
			double overallFurthestMatch;
			if (geom && sectsForGeom == sectsForMinMag) {
				// don't need to recalculate
				matchingSubSects = minMagMatchingSubSects;
				matchingSubSectMMins = null;
				matchingSectSurfs = minMagMatchingSectSurfs;
				sectMappings = minMagSectMappings;
				overallFurthestMatch = overallFurthestMinMagMatch;
			} else {
				// need to calculate
				
				List<? extends FaultSection> sects = geom ? sectsForGeom : sectsForMinMag;
				matchingSubSects = new ArrayList<>();
				List<Region> matchingSubSectOutlines = new ArrayList<>();
				matchingSubSectMMins = geom ? null : new ArrayList<>();
				List<Location[]> matchingSubSectFirstEdges = new ArrayList<>();
				List<Location[]> matchingSubSectLastEdges = new ArrayList<>();
				matchingSectSurfs = new ArrayList<>();
				for (FaultSection sect : sects) {
					int sectParentID = sect.getParentSectionId();
					if (Ints.contains(parentIDs, sectParentID)) {
						matchingSubSects.add(sect);
						RuptureSurface surf = sect.getFaultSurface(2d, false, false);
						matchingSectSurfs.add(surf);
						matchingSubSectOutlines.add(new Region(surf.getPerimeter(), BorderType.MERCATOR_LINEAR));
						FaultTrace upper = surf.getUpperEdge();
						LocationList lower = surf.getEvenlyDiscritizedLowerEdge();
						Location[] firstEdge = {lower.first(), upper.first()};
						Location[] lastEdge = {lower.last(), upper.last()};
						matchingSubSectFirstEdges.add(firstEdge);
						matchingSubSectLastEdges.add(lastEdge);
						double sectMinMag = geom ? Double.NaN : rupSet.getMinMagForSection(sect.getSectionId());
						if (D) {
							System.out.println(sect.getSectionId()+". "+sect.getSectionName()+": Mmin="+(float)sectMinMag);
							System.out.println("\tStrike="+sect.getFaultTrace().getAveStrike()
									+", firstEdgeAz="+(float)LocationUtils.azimuth(firstEdge[0], firstEdge[1])
									+", lastEdgeAz="+(float)LocationUtils.azimuth(lastEdge[0], lastEdge[1]));
							System.out.println("\tDepths: "+(float)sect.getOrigAveUpperDepth()+" "+(float)sect.getAveLowerDepth());
						}
						if (!geom) {
							matchingSubSectMMins.add(sectMinMag);
							maxSectMinMag = Math.max(maxSectMinMag, sectMinMag);
						}
//					} else {
//						System.out.println("Skipping section (not a parentID match): "+sect.getSectionId()
//							+". "+sect.getSectionName()+" (parentID="+sectParentID+")");
					}
				}
				
				sectMappings = new int[pdf.size()];
				
				overallFurthestMatch = 0d;
				for (int gridIndex=0; gridIndex<pdf.size(); gridIndex++) {
					if (pdf.get(gridIndex) == 0d)
						continue;
					Location loc = pdf.getLocation(gridIndex);
					
					int matchingSectIndex = -1;
					// first check region.contains
					for (int i=0; i<matchingSubSects.size(); i++) {
						if (matchingSubSectOutlines.get(i).contains(loc)) {
							matchingSectIndex = i;
							break;
						}
					}
					
					if (matchingSectIndex < 0) {
						// see if we're between the extended edge lines of any section
						if (D) System.out.println("Location "+gridIndex+" ("+loc+") not contained, searching for nearest");
						double minEdgeDist = Double.POSITIVE_INFINITY;
						int minEdgeIndex = -1;
						for (int i=0; i<matchingSubSects.size(); i++) {
							Location[] firstEdge = matchingSubSectFirstEdges.get(i);
							Location[] lastEdge = matchingSubSectLastEdges.get(i);
							double dist1 = LocationUtils.distanceToLineFast(firstEdge[0], firstEdge[1], loc);
							boolean pos1 = dist1 >= 0d;
							double dist2 = LocationUtils.distanceToLineFast(lastEdge[0], lastEdge[1], loc);
							boolean pos2 = dist2 >= 0d;
//							if (D) System.out.println("\t\t"+matchingSubSects.get(i).getSectionName()+" edge dists:\t"+(float)dist1+"\t"+(float)dist2);
							if (pos1 != pos2) {
								if (D) System.out.println("\tEdge positivity test matched with "+matchingSubSects.get(i).getSectionName());
								// between the two lines
								Preconditions.checkState(matchingSectIndex < 0, "Edge positivity test matched multiple sections!");
								matchingSectIndex = i;
							} else {
								double dist = Math.min(Math.abs(dist1), Math.abs(dist2));
								if (dist < minEdgeDist) {
									minEdgeDist = dist;
									minEdgeIndex = i;
								}
							}
						}
						if (matchingSectIndex < 0) {
							// use closest edge distance
							matchingSectIndex = minEdgeIndex;
							if (D) {
								System.out.flush();
								System.err.println("\tEdge positivity failed on all, using closest with dist="
										+(float)minEdgeDist+": "+matchingSubSects.get(matchingSectIndex).getSectionName());
								System.err.flush();
							}
						}
						double dist = matchingSubSectOutlines.get(matchingSectIndex).distanceToLocation(loc);
						overallFurthestMatch = Math.max(overallFurthestMatch, dist);
						if (D) System.out.println("\tDistance to section: "+(float)dist+"\n");
					}
					
					FaultSection matchingSection = matchingSubSects.get(matchingSectIndex);
					sectMappings[gridIndex] = matchingSectIndex;
					
					// find the depth at this location
					Preconditions.checkState(LocationUtils.areSimilar(loc, depthData.getLocation(gridIndex)));
//					double depth = depthData.get(gridIndex);
//					sectMinDepths[matchingSectIndex] = Math.min(sectMinDepths[matchingSectIndex], Math.min(depth, matchingSection.getOrigAveUpperDepth()));
//					sectMaxDepths[matchingSectIndex] = Math.max(sectMaxDepths[matchingSectIndex], Math.max(depth, matchingSection.getAveLowerDepth()));
				}
			}
			
			if (geom) {
				geomMatchingSubSects = matchingSubSects;
				geomMatchingSectSurfs = matchingSectSurfs;
				geomSectMappings = sectMappings;
				overallFurthestGeomMatch = overallFurthestMatch;
			} else {
				minMagMatchingSubSects = matchingSubSects;
				minMagMatchingSubSectMMins = matchingSubSectMMins;
				minMagMatchingSectSurfs = matchingSectSurfs;
				minMagSectMappings = sectMappings;
				overallFurthestMinMagMatch = overallFurthestMatch;
			}
		}
		
		IncrementalMagFreqDist refMFD = FaultSysTools.initEmptyMFD(OVERALL_MMIN, maxSectMinMag);
		System.out.println("MFD Gridding Mmmax="+(float)refMFD.getMaxX());
		
		List<List<GriddedRupture>> ruptureLists = new ArrayList<>(pdf.size());
		
		double overallMinDepth = depthData.getMinZ();
		double overallMaxDepth = depthData.getMaxZ();
		double minDepthRangeLimit = 0.2*(overallMaxDepth - overallMinDepth);
		if (D) System.out.println("Overall Slab2 depth range: ["+(float)overallMinDepth+", "+(float)overallMaxDepth+"]");
			
		for (int gridIndex=0; gridIndex<pdf.size(); gridIndex++) {
			double fractRate = pdf.get(gridIndex);
			if (fractRate == 0d) {
				ruptureLists.add(new ArrayList<>());
				continue;
			}
			
			Location gridLoc = pdf.getLocation(gridIndex);
			
//			boolean DD = D && gridIndex == 66;
			boolean DD = D && gridIndex == 416;
			
			if (DD) System.out.println("DEBUG for grid "+gridIndex+" at "+gridLoc);

			double sectMmin = minMagMatchingSubSectMMins.get(minMagSectMappings[gridIndex]);
			FaultSection matchingSection = geomMatchingSubSects.get(geomSectMappings[gridIndex]);
			
			double strike;
			double dip;
			double depth;
			double depthUpperLimit;
			double depthLowerLimit;
			if (INTERFACE_USE_SECT_PROPERTIES) {
				RuptureSurface surf = geomMatchingSectSurfs.get(geomSectMappings[gridIndex]);
				
				if (surf instanceof EvenlyGriddedSurface) {
					EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surf;
					double closest = Double.POSITIVE_INFINITY;
					int closestRow = -1;
					int closestCol = -1;
					for (int row=0; row<gridSurf.getNumRows(); row++) {
						for (int col=0; col<gridSurf.getNumCols(); col++) {
							Location loc = gridSurf.get(row, col);
							double dist = LocationUtils.horzDistanceFast(loc, gridLoc);
							if (dist < closest) {
								closest = dist;
								closestRow = row;
								closestCol = col;
							}
						}
					}
					depth = gridSurf.get(closestRow, closestCol).depth;
					depthUpperLimit = gridSurf.get(0, closestCol).depth;
					depthLowerLimit = gridSurf.get(gridSurf.getNumRows()-1, closestCol).depth;
				} else {
					double closest = Double.POSITIVE_INFINITY;
					Location closestLoc = null;
					depthUpperLimit = Double.POSITIVE_INFINITY;
					depthLowerLimit = Double.NEGATIVE_INFINITY;
					for (Location loc : surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
						depthUpperLimit = Math.min(depthUpperLimit, loc.depth);
						depthLowerLimit = Math.max(depthLowerLimit, loc.depth);
						double dist = LocationUtils.horzDistanceFast(loc, gridLoc);
						if (dist < closest) {
							closest = dist;
							closestLoc = loc;
						}
					}
					depth = closestLoc.depth;
				}
				strike = surf.getAveStrike();
				dip = matchingSection.getAveDip();
				if (DD) {
					System.out.println("\tOriginal sect depth is "+(float)depthData.get(gridIndex));
					System.out.println("\tClosest sect depth is "+(float)depth);
					System.out.println("\tSect depth limits are ["+(float)depthUpperLimit+", "+(float)depthLowerLimit+"]");
				}
			} else {
				strike = strikeData.get(gridIndex);
				depth = depthData.get(gridIndex);
				// find the depth limits at this point on the surface
				// first find the edges of the seismicity region in the strike-normal direction
				Location leftEdgeLoc = null;
				Location rightEdgeLoc = null;
				for (boolean left : new boolean[] {true, false}) {
					double azRad = left ? Math.toRadians(strike-90d) : Math.toRadians(strike+90d);
					
					Location lastInsideLoc = gridLoc;
					double lastInsideLocDist = 0d;
					
					Location firstOutsideLoc = null;
					double firstOutsideLocDist = 10d;
					
					while (firstOutsideLoc == null) {
						Location testLoc = LocationUtils.location(gridLoc, azRad, firstOutsideLocDist);
						if (region.indexForLocation(testLoc) >= 0) {
							// still inside
							lastInsideLocDist = firstOutsideLocDist;
							firstOutsideLocDist += 10d;
							lastInsideLoc = testLoc;
						} else {
							firstOutsideLoc = testLoc;
						}
					}
					
					// now we have a location that's inside and outside, figure out where the boundary actually is
					// do a binary search
					for (int i=0; i<10; i++) {
						double testDist = 0.5*(lastInsideLocDist + firstOutsideLocDist);
						Location testLoc = LocationUtils.location(gridLoc, azRad, testDist);
						if (region.indexForLocation(testLoc) >= 0) {
							lastInsideLoc = testLoc;
							lastInsideLocDist = testDist;
						} else {
							firstOutsideLoc = testLoc;
							firstOutsideLocDist = testDist;
						}
					}
					
					if (left)
						leftEdgeLoc = lastInsideLoc;
					else
						rightEdgeLoc = lastInsideLoc;
				}
				int leftIndex = region.indexForLocation(leftEdgeLoc);
				int rightIndex = region.indexForLocation(rightEdgeLoc);
				double leftDepth = depthData.get(leftIndex);
				double rightDepth = depthData.get(rightIndex);
				if (leftIndex == rightIndex || leftDepth == rightDepth) {
					// just use section dip
					dip = matchingSection.getAveDip();
				} else {
					// calculate dip from the surface
					double horzDist = LocationUtils.horzDistanceFast(leftEdgeLoc, rightEdgeLoc);
					double vertDist = Math.abs(leftDepth - rightDepth);
					Preconditions.checkState(horzDist > 0d);
					Preconditions.checkState(vertDist > 0d);
					// tan(dip) = vert / horz
					// dip = arctan(vert/horz)
					dip = Math.toDegrees(Math.atan(vertDist/horzDist));
					FaultUtils.assertValidDip(dip);
				}
				// left *should* be above right
				depthUpperLimit = Math.min(leftDepth, rightDepth);
				depthLowerLimit = Math.max(leftDepth, rightDepth);
				Preconditions.checkState(depthUpperLimit <= depth, "depthUpperLimit=%s but depth=%s", depthUpperLimit, depth);
				Preconditions.checkState(depthLowerLimit >= depth, "depthLowerLimit=%s but depth=%s", depthLowerLimit, depth);
				
				double myMaxDepth = overallMaxDepth;
				double myMinDepth = overallMinDepth;
				
				// this uses section depths, but it can extend past the section horizontally to achieve those depths
//				double sectLowerDepth = matchingSection.getAveLowerDepth();
//				myMaxDepth = Math.max(sectLowerDepth, myMaxDepth);
//				if (sectLowerDepth > depthLowerLimit)
//					depthLowerLimit = sectLowerDepth;
//				double sectUpperDepth = matchingSection.getOrigAveUpperDepth();
//				myMinDepth = Math.min(sectUpperDepth, myMinDepth);
//				if (sectUpperDepth < depthUpperLimit)
//					depthUpperLimit = sectUpperDepth;
				
				double delta = depthLowerLimit - depthUpperLimit;
				if (delta < minDepthRangeLimit) {
					// too narrow, probably right at the skinny end, expand to a sensible value
					System.out.println("Delta="+(float)delta+" is too narrow, expanding to "+(float)minDepthRangeLimit+" for depth="+(float)depth);
					depthUpperLimit = depth - 0.5*minDepthRangeLimit;
					depthLowerLimit = depth + 0.5*minDepthRangeLimit;
					System.out.println("\tUpdated limits: ["+(float)depthUpperLimit+", "+(float)depthLowerLimit+"]");
					if (depthLowerLimit > myMaxDepth) {
						depthLowerLimit = overallMaxDepth;
						depthUpperLimit = depthLowerLimit - minDepthRangeLimit;
						System.out.println("\twould have extended beyond overall max depth; updated limits: ["
								+(float)depthUpperLimit+", "+(float)depthLowerLimit+"]");
					} else if (depthUpperLimit < myMinDepth) {
						depthUpperLimit = myMinDepth;
						depthLowerLimit = depthUpperLimit + minDepthRangeLimit;
						System.out.println("\twould have extended above overall min depth; updated limits: ["
								+(float)depthUpperLimit+", "+(float)depthLowerLimit+"]");
					}
				}
				Preconditions.checkState(depthLowerLimit <= myMaxDepth,
						"Bad depthLowerLimit=%s with myMaxDepth=%s", depthLowerLimit, myMaxDepth);
				Preconditions.checkState(depthUpperLimit >= myMinDepth,
						"Bad depthUpperLimit=%s with overallMinDepth=%s", depthUpperLimit, myMinDepth);
			}
			
			int[] assocIDs = { matchingSection.getSectionId() };
			double[] assocFracts = { 1d };
			
			double dipRad = Math.toRadians(dip);
//			double rake = matchingSection.getAveRake();
			double rake = 90d; // fix it to 90: don't want multiple instances of each rupture after branch averaging
//			double strike = matchingSection.getFaultTrace().getAveStrike();
			
			int sectMminIndex = refMFD.getClosestXIndex(sectMmin);
			Preconditions.checkState(sectMminIndex > 0);
			// set Mmax to one bin below the matching section Mmin
			double mMax = refMFD.getX(sectMminIndex-1);
			
			// modify to use our actual closest depth as these are average and not rectangular
			double maxDDW = (depthLowerLimit - depthUpperLimit)/Math.sin(dipRad);
			
			IncrementalMagFreqDist mfd = seisBranch.build(seisRegion, refMFD, mMax);
			mfd.scale(fractRate);
			
			List<GriddedRupture> ruptureList = new ArrayList<>(sectMminIndex);
			ruptureLists.add(ruptureList);
			for (int i=0; i<mfd.size(); i++) {
				double mag = mfd.getX(i);
				double rate = mfd.getY(i);
				
				if (rate == 0d || (float)mag < (float)OVERALL_MMIN)
					continue;
				
				double area = scale.getMedianArea(mag);
				double sqRtArea = Math.sqrt(area);
				
				double hypocentralDAS = Double.NaN;
				double hypocentralDepth = depth;
				Preconditions.checkState(Double.isFinite(depth), "closestDepth=%s?", depth);
				
				double length, ddw, upper, lower;
				if (sqRtArea <= maxDDW) {
					// make a square
					ddw = sqRtArea;
					length = sqRtArea;
					// figure out upper/lower, making sure they don't exceed sectUpper or sectLower
					
					double vertDDW = ddw*Math.sin(dipRad);
					Preconditions.checkState(Double.isFinite(vertDDW),
							"vertDDW=%s with ddw=%s, dip=%s, dipRad=%s", vertDDW, ddw, dip, dipRad);
					double calcUpper = hypocentralDepth - 0.5*vertDDW;
					if (calcUpper < depthUpperLimit) {
						// snap upper edge
						upper = depthUpperLimit;
						lower = upper + vertDDW;
					} else {
						double calcLower = hypocentralDepth + 0.5*vertDDW;
						if (calcLower > depthLowerLimit) {
							// snap to lower edge
							lower = depthLowerLimit;
							upper = depthLowerLimit - vertDDW;
						} else {
							// no issue
							upper = calcUpper;
							lower = calcLower;
						}
					}
				} else {
					// make a rectangle that matches sectDDW
					ddw = maxDDW;
					length = area/ddw;
					upper = depthUpperLimit;
					lower = depthLowerLimit;
				}
				
				if (DD) {
					System.out.println("\tM"+(float)mag+" with depth="+(float)hypocentralDepth+", range=["+(float)upper+", "+(float)lower+"]");
				}

				Preconditions.checkState(Double.isFinite(upper));
				Preconditions.checkState(Double.isFinite(lower), "lower=%s? depthLowerLimit=%s, ddw=%s", lower, depthLowerLimit, ddw);
				GriddedRuptureProperties props = new GriddedRuptureProperties(
						mag, rake, dip, strike, null,
						upper, lower, length, hypocentralDepth, hypocentralDAS,
						TectonicRegionType.SUBDUCTION_INTERFACE);
				ruptureList.add(new GriddedRupture(gridIndex, gridLoc, props, rate, assocIDs, assocFracts));
			}
		}
		
		System.out.println("Done building gridded provider for "+seisRegion+"; worst grid-to-interface distance: "+(float)overallFurthestGeomMatch+"km");
		
		return new GridSourceList.Precomputed(pdf.getRegion(), TectonicRegionType.SUBDUCTION_INTERFACE, ruptureLists);
	}

	public static void main(String[] args) throws IOException {
//		calcCrustalFaultCategories();
//		System.exit(0);
		
//		FaultSystemSolution crustalSol = FaultSystemSolution.load(new File("/data/kevin/nshm23/batch_inversions/"
//				+ "2024_11_19-prvi25_crustal_branches-dmSample5x/results_PRVI_CRUSTAL_FM_V1p1_branch_averaged.zip"));
//		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
//		levels.addAll(PRVI25_LogicTreeBranch.levelsOnFault);
//		levels.addAll(PRVI25_LogicTreeBranch.levelsCrustalOffFault);
//		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels);
//		for (LogicTreeNode node : PRVI25_LogicTreeBranch.DEFAULT_CRUSTAL_ON_FAULT)
//			branch.setValue(node);
//		PRVI25_RegionalSeismicity rateBranch = PRVI25_RegionalSeismicity.PREFFERRED;
//		PRVI25_DeclusteringAlgorithms declusteringBranch = PRVI25_DeclusteringAlgorithms.AVERAGE;
//		PRVI25_SeisSmoothingAlgorithms smoothingBranch = PRVI25_SeisSmoothingAlgorithms.AVERAGE;
//		NSHM23_MaxMagOffFault mMaxBranch = NSHM23_MaxMagOffFault.MAG_7p6;
//		branch.setValue(rateBranch);
//		branch.setValue(declusteringBranch);
//		branch.setValue(smoothingBranch);
//		branch.setValue(mMaxBranch);
//		PRVI25_SeismicityRegions seisReg = PRVI25_SeismicityRegions.CRUSTAL;
//		GridSourceList gridSources = buildCrustalGridSourceProv(crustalSol, branch);
//		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(OVERALL_MMIN, 8.01);
//		IncrementalMagFreqDist fullDataMFD = rateBranch.build(seisReg, refMFD, mMaxBranch.getMaxMagOffFault());
//		Location testLoc = new Location(18.3, -66); // not in a polygon
//		for (boolean entireRegion : new boolean[] {false,true}) {
//			IncrementalMagFreqDist gridMFD = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
//			IncrementalMagFreqDist dataMFD;
//			if (entireRegion) {
//				System.out.println("ENTIRE REGION");
//				for (int l=0; l<gridSources.getNumLocations(); l++)
//					for (GriddedRupture rup : gridSources.getRuptures(TectonicRegionType.ACTIVE_SHALLOW, l))
//						gridMFD.add(gridMFD.getClosestXIndex(rup.properties.magnitude), rup.rate);
//				dataMFD = fullDataMFD;
//			} else {
//				System.out.println("Grid-only location "+testLoc);
//				int l = gridSources.getLocationIndex(testLoc);
//				for (GriddedRupture rup : gridSources.getRuptures(TectonicRegionType.ACTIVE_SHALLOW, l)) {
//					Preconditions.checkState(rup.associatedSections == null);
//					gridMFD.add(gridMFD.getClosestXIndex(rup.properties.magnitude), rup.rate);
//				}
//				double pdfScalar = smoothingBranch.loadXYZ(seisReg, declusteringBranch).get(l);
//				dataMFD = fullDataMFD.deepClone();
//				dataMFD.scale(pdfScalar);
//			}
//			System.out.println("Mag\tInput\tOutput\tDiff\t% Diff");
//			for (int i=0; i<refMFD.size(); i++) {
//				double mag = refMFD.getX(i);
//				double data = dataMFD.getY(i);
//				double model = gridMFD.getY(i);
//				double diff = model - data;
//				double pDiff = 100d*diff/data;
//				System.out.println((float)mag+"\t"+(float)data+"\t"+(float)model+"\t\t"+(float)diff+"\t"+(float)pDiff);
//			}
//		}
		
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
		levels.addAll(PRVI25_LogicTreeBranch.levelsSubduction);
		levels.addAll(PRVI25_LogicTreeBranch.levelsSubductionGridded);
		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels);
		
		branch.setValue(PRVI25_SubductionScalingRelationships.AVERAGE);
//		branch.setValue(PRVI25_RegionalSeismicity.LOW);
		branch.setValue(PRVI25_RegionalSeismicity.PREFFERRED);
//		branch.setValue(PRVI25_RegionalSeismicity.HIGH);
		branch.setValue(PRVI25_SeisSmoothingAlgorithms.AVERAGE);
		branch.setValue(PRVI25_DeclusteringAlgorithms.AVERAGE);
		
//		buildSlabGridSourceList(branch);
		
//		INTERFACE_USE_SECT_PROPERTIES = false;
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
				+ "2024_12_12-prvi25_subduction_branches/results_PRVI_SUB_FM_LARGE_branch_averaged.zip"));
		branch.setValue(PRVI25_SubductionFaultModels.PRVI_SUB_FM_LARGE);
//		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
//				+ "2024_12_12-prvi25_subduction_branches/results_PRVI_SUB_FM_SMALL_branch_averaged.zip"));
//		branch.setValue(PRVI25_SubductionFaultModels.PRVI_SUB_FM_SMALL);
		
//		GridSourceList slabModel = buildSlabGridSourceList(branch);
//		SeismicityRegions seisReg = SeismicityRegions.CAR_INTRASLAB;
//		GridSourceList slabModel = buildSlabGridSourceList(branch, seisReg);
//		double rateM5 = 0d;
//		for (int gridIndex=0; gridIndex<slabModel.getNumLocations(); gridIndex++)
//			for (GriddedRupture rup : slabModel.getRuptures(TectonicRegionType.SUBDUCTION_SLAB, gridIndex))
//				if (rup.magnitude >= 5d)
//					rateM5 += rup.rate;
//		System.out.println(seisReg+" rate M>5: "+(float)rateM5);
		
//		PRVI25_SeismicityRegions seisReg = PRVI25_SeismicityRegions.CAR_INTERFACE;
		PRVI25_SeismicityRegions seisReg = PRVI25_SeismicityRegions.MUE_INTERFACE;
		Region region = seisReg.load();
		GridSourceList interfaceModel = buildInterfaceGridSourceList(sol, branch, seisReg);
//		GridSourceList interfaceModel = buildInterfaceGridSourceList(sol, branch);
		AveragingAccumulator<GridSourceProvider> averager = interfaceModel.averagingAccumulator();
		for (int i=0; i<10; i++)
			averager.process(interfaceModel, 1d);
		interfaceModel = (GridSourceList) averager.getAverage();
		double rateM5 = 0d;
		for (int gridIndex=0; gridIndex<interfaceModel.getNumLocations(); gridIndex++) {
			if (region.contains(interfaceModel.getLocation(gridIndex))) {
				for (GriddedRupture rup : interfaceModel.getRuptures(TectonicRegionType.SUBDUCTION_INTERFACE, gridIndex))
					if (rup.properties.magnitude >= 5d)
						rateM5 += rup.rate;
			}
		}
		System.out.println(seisReg+" rate M>5: "+(float)rateM5);
		sol.setGridSourceProvider(interfaceModel);
		String name = "sol_"+branch.requireValue(PRVI25_SubductionFaultModels.class).getFilePrefix()+"_with_"+seisReg.name()+"_gridded";
		if (!INTERFACE_USE_SECT_PROPERTIES)
			name += "_slab_depths";
		sol.write(new File("/tmp/", name+".zip"));
		
//		buildInterfaceGridSourceList(sol, branch);
		
//		GridSourceList gridSources = buildCombinedSubductionGridSourceList(sol, branch);
//		
//		sol.addModule(gridSources);
//		String prefix = branch.requireValue(PRVI25_SubductionFaultModels.class).getFilePrefix()+"_with_gridded";
//		File outputFile = new File("/tmp", prefix+".zip");
//		sol.write(outputFile);
//		
//		System.setProperty("java.awt.headless", "true");
//		
//		ReportPageGen.main(new String[] {
//				"--input-file", outputFile.getAbsolutePath(),
//				"--name", "PRVI25 Subduction Gridded Test",
//				"--plot-level", "review",
//				"--output-dir", new File("/tmp/", prefix).getAbsolutePath(),
//				"--replot"
//		});
	}

}
