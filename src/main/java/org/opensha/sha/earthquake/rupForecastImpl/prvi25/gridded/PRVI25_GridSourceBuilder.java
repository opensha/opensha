package org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.math3.util.Precision;
import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.MagScalingRelationship;
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
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRuptureProperties;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_FaultCubeAssociations;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_SingleRegionGridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.PRVI25_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalDeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalSeismicityRate;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_DeclusteringAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_LogicTree;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SeisSmoothingAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SeismicityRateEpoch;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionCaribbeanSeismicityRate;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionDeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionMuertosSeismicityRate;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionSlabMMax;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.PRVI25_SeismicityRegions;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;

import scratch.UCERF3.erf.ETAS.SeisDepthDistribution;

public class PRVI25_GridSourceBuilder {
	
	/**
	 * if true, will subtract on-fault supra-seis rates from gridded MFDs
	 */
	public static boolean RATE_BALANCE_CRUSTAL_GRIDDED = false;
	
	/**
	 * if true, will subtract interface supra-seis rates from gridded MFDs
	 * 
	 * this gets complicated because gridded mMax and sect mMin varies, so we calculate the section nucleation rate
	 * and scale that to the number of mapped grid nodes, then subtract that from the total M5 rate for each mapped
	 * grid node.
	 */
	public static boolean RATE_BALANCE_INTERFACE_GRIDDED = false;
	
	/**
	 * if true, depths and strikes are taken from the mapped subsection
	 * otherwise, they use Slab2 (which won't match for Muertos)
	 */
	public static boolean INTERFACE_USE_SECT_PROPERTIES = false;
	
	// maximum depths for interface finite ruptures; only used if INTERFACE_USE_SECT_PROPERTIES=false
	public static double INTERFACE_CAR_MAX_DEPTH = 50d;
	public static double INTERFACE_MUE_MAX_DEPTH = 50d;
	
	// for intraslab sources, assign these IDs as associated sections (even though there aren't sections with these IDs)
	// so that we can track them later and in nshmp-haz
	public static final int CAR_SLAB_ASSOC_ID = 7510;
	public static final int MUE_SLAB_ASSOC_ID = 7511;
	
	public static boolean MUERTOS_AS_CRUSTAL = false;
	
	public static final double OVERALL_MMIN= 2.55;
	
	public static double SLAB_M_CORNER = Double.NaN;
	
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
		NSHM23_SingleRegionGridSourceProvider gridProv = buildCrustalGridSourceProv(sol, branch, cubeAssociations);
		
		GridSourceList gridList = gridProv.convertToGridSourceList(OVERALL_MMIN);
		
		if (MUERTOS_AS_CRUSTAL) {
			// add muertos in
			LogicTreeBranch<LogicTreeNode> mueBranch = PRVI25_LogicTree.DEFAULT_SUBDUCTION_GRIDDED.copy();
			mueBranch.setValue(branch.requireValue(PRVI25_DeclusteringAlgorithms.class));
			mueBranch.setValue(branch.requireValue(PRVI25_SeisSmoothingAlgorithms.class));
			mueBranch.setValue(PRVI25_SubductionMuertosSeismicityRate.valueOf(branch.requireValue(PRVI25_CrustalSeismicityRate.class).name()));
			// need a scaling relationship
			MagAreaRelationship scale;
			switch (branch.requireValue(NSHM23_ScalingRelationships.class)) {
			case LOGA_C4p1:
				scale = new PRVI25_SubductionScalingRelationships.LogAPlusC(4.1);
				break;
			case LOGA_C4p2:
				scale = new PRVI25_SubductionScalingRelationships.LogAPlusC(4.2);
				break;
			case LOGA_C4p3:
				scale = new PRVI25_SubductionScalingRelationships.LogAPlusC(4.3);
				break;
			case LOGA_C4p2_SQRT_LEN:
				scale = new PRVI25_SubductionScalingRelationships.LogAPlusC(4.2);
				break;

			default:
				// TODO, could do better for width-limited?
				scale = new PRVI25_SubductionScalingRelationships.LogAPlusC(4.2);
				break;
			}
			GridSourceList mueList = buildInterfaceGridSourceList(sol, mueBranch, PRVI25_SeismicityRegions.MUE_INTERFACE, scale, null);
			// convert to crustal
			List<List<GriddedRupture>> rupLists = new ArrayList<>();
			for (int l=0; l<mueList.getNumLocations(); l++) {
				List<GriddedRupture> rups = new ArrayList<>();
				for (GriddedRupture rup : mueList.getRuptures(TectonicRegionType.SUBDUCTION_INTERFACE, l)) {
					GriddedRuptureProperties props = rup.properties;
					GriddedRuptureProperties modProps = new GriddedRuptureProperties(props.magnitude, props.rake,
							props.dip, props.strike, props.strikeRange, props.upperDepth, props.lowerDepth, props.length,
							props.hypocentralDepth, props.hypocentralDAS, TectonicRegionType.ACTIVE_SHALLOW);
					rups.add(new GriddedRupture(l, rup.location, modProps, rup.rate,
							rup.associatedSections, rup.associatedSectionFracts));
				}
				rupLists.add(rups);
			}
			mueList = new GridSourceList.Precomputed(mueList.getGriddedRegion(), TectonicRegionType.ACTIVE_SHALLOW, rupLists);
			gridList = GridSourceList.combine(gridList, mueList);
		}
		
		return gridList;
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
	
	public static double getCrustalFractSS() {
		try {
			checkCalcCrustalFaultCategories();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		return CRUSTAL_FRACT_SS;
	}
	
	public static double getCrustalFractRev() {
		try {
			checkCalcCrustalFaultCategories();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		return CRUSTAL_FRACT_REV;
	}
	
	public static double getCrustalFractNorm() {
		try {
			checkCalcCrustalFaultCategories();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		return CRUSTAL_FRACT_NORM;
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
			FaultCubeAssociations cubeAssociations)  throws IOException {
		double maxMagOff = branch.requireValue(MaxMagOffFaultBranchNode.class).getMaxMagOffFault();
		
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(
				OVERALL_MMIN, Math.max(maxMagOff, sol.getRupSet().getMaxMag()));
		
		// total G-R up to Mmax
		PRVI25_CrustalSeismicityRate seisBranch = branch.requireValue(PRVI25_CrustalSeismicityRate.class);
		PRVI25_SeismicityRateEpoch epoch = branch.requireValue(PRVI25_SeismicityRateEpoch.class);
		IncrementalMagFreqDist totalGR = seisBranch.build(epoch, refMFD, maxMagOff);
		
		return buildCrustalGridSourceProv(sol, branch, cubeAssociations, totalGR);
	}
	
	public static NSHM23_SingleRegionGridSourceProvider buildCrustalGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> branch,
			FaultCubeAssociations cubeAssociations, IncrementalMagFreqDist totalGR)  throws IOException {
		PRVI25_DeclusteringAlgorithms declusteringAlg = branch.requireValue(PRVI25_DeclusteringAlgorithms.class);
		PRVI25_SeisSmoothingAlgorithms seisSmooth = branch.requireValue(PRVI25_SeisSmoothingAlgorithms.class);

		// spatial seismicity PDF
		double[] pdf = seisSmooth.load(PRVI25_SeismicityRegions.CRUSTAL, declusteringAlg);
		
		return buildCrustalGridSourceProv(sol, cubeAssociations, totalGR, pdf);
	}
	
	public static NSHM23_SingleRegionGridSourceProvider buildCrustalGridSourceProv(FaultSystemSolution sol,
			FaultCubeAssociations cubeAssociations, IncrementalMagFreqDist totalGR, double[] pdf)  throws IOException {
		GriddedRegion gridReg = cubeAssociations.getRegion();

		// figure out what's left for gridded seismicity
		IncrementalMagFreqDist totalGridded = new IncrementalMagFreqDist(
				totalGR.getMinX(), totalGR.size(), totalGR.getDelta());

		IncrementalMagFreqDist solNuclMFD = sol.calcNucleationMFD_forRegion(
				gridReg, totalGR.getMinX(), totalGR.getMaxX(), totalGR.size(), false);
		for (int i=0; i<totalGR.size(); i++) {
			double totalRate = totalGR.getY(i);
			if (totalRate > 0) {
				if (RATE_BALANCE_CRUSTAL_GRIDDED) {
					double solRate = solNuclMFD.getY(i);
					if (solRate > totalRate) {
						System.err.println("WARNING: MFD bulge at M="+(float)totalGR.getX(i)
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
		GridSourceList muertosInterface = MUERTOS_AS_CRUSTAL ? null :
					buildInterfaceGridSourceList(sol, fullBranch, PRVI25_SeismicityRegions.MUE_INTERFACE);
		GridSourceList carInterface = buildInterfaceGridSourceList(sol, fullBranch, PRVI25_SeismicityRegions.CAR_INTERFACE);
		
		// for some reason, doing them one by one in the combine method doesn't work; do it ahead of time pairwise
		Region muertosUnionRegion = MUERTOS_AS_CRUSTAL ? PRVI25_SeismicityRegions.MUE_INTRASLAB.load() :
					Region.union(PRVI25_SeismicityRegions.MUE_INTRASLAB.load(), PRVI25_SeismicityRegions.MUE_INTERFACE.load());
		Region carUnionRegion = Region.union(PRVI25_SeismicityRegions.CAR_INTRASLAB.load(), PRVI25_SeismicityRegions.CAR_INTERFACE.load());
		Preconditions.checkNotNull(muertosUnionRegion, "Couldn't union Muertos regions");
		Preconditions.checkNotNull(carUnionRegion, "Couldn't union CAR regions");
		Region unionRegion = Region.union(muertosUnionRegion, carUnionRegion);
		Preconditions.checkNotNull(unionRegion, "Couldn't union CAR regions");
		GriddedRegion griddedUnionRegion = new GriddedRegion(unionRegion, muertosSlab.getGriddedRegion().getSpacing(), GriddedRegion.ANCHOR_0_0);

		if (MUERTOS_AS_CRUSTAL)
			return GridSourceList.combine(griddedUnionRegion, carInterface, carSlab, muertosSlab);
		return GridSourceList.combine(griddedUnionRegion, carInterface, carSlab, muertosInterface, muertosSlab);
	}
	
	private static EnumMap<PRVI25_SeismicityRegions, GriddedGeoDataSet> gridDepths;
	private static EnumMap<PRVI25_SeismicityRegions, GriddedGeoDataSet> gridStrikes;
	private static EnumMap<PRVI25_SeismicityRegions, GriddedGeoDataSet> gridDips;
	private static final String DEPTHS_DIR = "/data/erf/prvi25/seismicity/depths/";
	
	public static GriddedGeoDataSet loadSubductionDepths(PRVI25_SeismicityRegions seisReg) throws IOException {
		loadRegionDepthStrikeData(seisReg);
		return gridDepths.get(seisReg).copy();
	}
	
	public static GriddedGeoDataSet loadSubductionStrikes(PRVI25_SeismicityRegions seisReg) throws IOException {
		loadRegionDepthStrikeData(seisReg);
		return gridStrikes.get(seisReg).copy();
	}
	
	public static GriddedGeoDataSet loadSubductionDips(PRVI25_SeismicityRegions seisReg) throws IOException {
		loadRegionDepthStrikeData(seisReg);
		return gridDips.get(seisReg).copy();
	}
	
	private synchronized static void loadRegionDepthStrikeData(PRVI25_SeismicityRegions seisReg) throws IOException {
		if (gridDepths == null) {
			gridDepths = new EnumMap<>(PRVI25_SeismicityRegions.class);
			gridStrikes = new EnumMap<>(PRVI25_SeismicityRegions.class);
			gridDips = new EnumMap<>(PRVI25_SeismicityRegions.class);
		}
		
		if (!gridDepths.containsKey(seisReg)) {
			PRVI25_SeismicityRegions[] applicableRegions;
			String fileName;
			GriddedRegion gridReg;
			if (seisReg == PRVI25_SeismicityRegions.CAR_INTERFACE || seisReg == PRVI25_SeismicityRegions.CAR_INTRASLAB) {
				applicableRegions = new PRVI25_SeismicityRegions[] {
						PRVI25_SeismicityRegions.CAR_INTERFACE, PRVI25_SeismicityRegions.CAR_INTRASLAB};
				fileName = DEPTHS_DIR+"CAR_slab2_extended-v2.csv";
				gridReg = new GriddedRegion(PRVI25_SeismicityRegions.CAR_INTRASLAB.load(), 0.05, GriddedRegion.ANCHOR_0_0);
			} else if (seisReg == PRVI25_SeismicityRegions.MUE_INTERFACE || seisReg == PRVI25_SeismicityRegions.MUE_INTRASLAB) {
				applicableRegions = new PRVI25_SeismicityRegions[] {
						PRVI25_SeismicityRegions.MUE_INTERFACE, PRVI25_SeismicityRegions.MUE_INTRASLAB};
				fileName = DEPTHS_DIR+"MUE_slab2_extended-v2.csv";
				gridReg = new GriddedRegion(PRVI25_SeismicityRegions.MUE_INTRASLAB.load(), 0.02, GriddedRegion.ANCHOR_0_0);
			} else {
				throw new IllegalStateException("Not applicable to region: "+seisReg);
			}
			
			System.out.println("Loading gridded Slab2 depth/strike/dip data from "+fileName);
			
			InputStream depthResources = PRVI25_GridSourceBuilder.class.getResourceAsStream(fileName);
			Preconditions.checkNotNull(depthResources, "Depth CSV not found: %s", fileName);
			CSVFile<String> csv = CSVFile.readStream(depthResources, true);
			
			GriddedGeoDataSet depths = new GriddedGeoDataSet(gridReg);
			GriddedGeoDataSet strikes = new GriddedGeoDataSet(gridReg);
			GriddedGeoDataSet dips = new GriddedGeoDataSet(gridReg);
			
			// initialize all 3 to NaN
			for (int i = 0; i < depths.size(); i++) {
				depths.set(i, Double.NaN);
				strikes.set(i, Double.NaN);
				dips.set(i, Double.NaN);
			}
			
//			Preconditions.checkState(csv.getNumRows() == gridReg.getNodeCount()+1,
//					"CSV has %s rows, grid reg has %s", csv.getNumRows(), gridReg.getNodeCount());
			int numFilled = 0;
			int numSkipped = 0;
			for (int row=1; row<csv.getNumRows(); row++) {
				double lon = csv.getDouble(row, 0);
				double lat = csv.getDouble(row, 1);
				Location loc = new Location(lat, lon);
				int gridIndex = gridReg.indexForLocation(loc);
				if (gridIndex < 0) {
//				if (gridIndex < 0 || !LocationUtils.areSimilar(loc, gridReg.getLocation(gridIndex))) {
					numSkipped++;
					continue;
				}
//				Preconditions.checkState(gridIndex >= 0, "Location not in grid: %s", loc);
				Preconditions.checkState(LocationUtils.areSimilar(loc, gridReg.getLocation(gridIndex)),
						"Location mismatch: ours[%s]=%s, theirs=%s", gridIndex, gridReg.getLocation(gridIndex), loc);
				
				double depth = csv.getDouble(row, 2);
				Preconditions.checkState(Double.isFinite(depth));
				FaultUtils.assertValidDepth(depth);
				depths.set(gridIndex, depth);
//				if ((float)loc.lat == 21.1f && (float)loc.lon == -67.8f)
//					System.out.println("Loaded depth="+(float)depth+" for "+gridIndex+". "+loc);
				
				double strike = csv.getDouble(row, 3);
				Preconditions.checkState(Double.isFinite(strike));
				FaultUtils.assertValidStrike(strike);
				strikes.set(gridIndex, strike);
				
				double dip = csv.getDouble(row, 4);
				Preconditions.checkState(Double.isFinite(dip));
				FaultUtils.assertValidDip(dip);
				dips.set(gridIndex, dip);
				
				numFilled++;
			}
			
			System.out.println("\tFilled in data for "+numFilled+"/"+depths.size()+" Slab2 grid nodes (skipped "+numSkipped+" from file)");
			
			for (PRVI25_SeismicityRegions reg : applicableRegions) {
				gridDepths.put(reg, depths);
				gridStrikes.put(reg, strikes);
				gridDips.put(reg, dips);
			}
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
		
		PRVI25_SubductionSlabMMax mMaxBranch = branch.requireValue(PRVI25_SubductionSlabMMax.class);
		PRVI25_SeismicityRateEpoch epoch = branch.requireValue(PRVI25_SeismicityRateEpoch.class);
		
		double maxMagOff = mMaxBranch.getMmax();
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(OVERALL_MMIN, maxMagOff);
		// snap Mmax to incremental bin before it
		maxMagOff = refMFD.getX(refMFD.getClosestXIndex(maxMagOff-0.01));
		Preconditions.checkState(maxMagOff <= mMaxBranch.getMmax());
		
		// total G-R up to Mmax
		IncrementalMagFreqDist totalGR;
		if (seisRegion == PRVI25_SeismicityRegions.MUE_INTRASLAB) {
			PRVI25_SubductionMuertosSeismicityRate seisBranch = branch.requireValue(PRVI25_SubductionMuertosSeismicityRate.class);
			totalGR = seisBranch.build(epoch, refMFD, maxMagOff, SLAB_M_CORNER, true);
		} else if (seisRegion == PRVI25_SeismicityRegions.CAR_INTRASLAB) {
			PRVI25_SubductionCaribbeanSeismicityRate seisBranch = branch.requireValue(PRVI25_SubductionCaribbeanSeismicityRate.class);
			totalGR = seisBranch.build(epoch, refMFD, maxMagOff, SLAB_M_CORNER, true);
		} else {
			throw new IllegalStateException("Not a slab region: "+seisRegion);
		}
		
		return buildSlabGridSourceList(branch, seisRegion, totalGR);
	}
	
	public static GridSourceList buildSlabGridSourceList(LogicTreeBranch<?> branch, PRVI25_SeismicityRegions seisRegion,
			IncrementalMagFreqDist totalGR) throws IOException {
		Preconditions.checkState(seisRegion == PRVI25_SeismicityRegions.CAR_INTRASLAB
				|| seisRegion == PRVI25_SeismicityRegions.MUE_INTRASLAB);
		
		// this will allow us to track mue and car separately
		int assocID = seisRegion == PRVI25_SeismicityRegions.CAR_INTRASLAB ? CAR_SLAB_ASSOC_ID : MUE_SLAB_ASSOC_ID;
		int[] assocIDarray = {assocID};
		double[] assocFractArray = {1d};
		
		PRVI25_DeclusteringAlgorithms declusteringAlg = branch.requireValue(PRVI25_DeclusteringAlgorithms.class);
		PRVI25_SeisSmoothingAlgorithms seisSmooth = branch.requireValue(PRVI25_SeisSmoothingAlgorithms.class);
		
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
			
			Location loc = pdf.getLocation(gridIndex);
			int depthIndex = depthData.indexOf(loc);
			Location depthLoc = depthData.getLocation(depthIndex);
			Preconditions.checkState(LocationUtils.areSimilar(loc, depthLoc));
			double upper = depthData.get(depthIndex);
			
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
				ruptureList.add(new GriddedRupture(gridIndex, loc, props, rate, assocIDarray, assocFractArray));
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
		PRVI25_SubductionScalingRelationships scaleBranch = fullBranch.requireValue(PRVI25_SubductionScalingRelationships.class);
		MagAreaRelationship scale = scaleBranch.getMagAreaRelationship();
		return buildInterfaceGridSourceList(sol, fullBranch, seisRegion, scale, null);
	}
	
	public static GridSourceList buildInterfaceGridSourceList(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch,
			PRVI25_SeismicityRegions seisRegion, MagAreaRelationship scale, Function<Double, IncrementalMagFreqDist> mfdBuilderFunc) throws IOException {
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
		
		final boolean D = false;
		
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
			if (MUERTOS_AS_CRUSTAL)
				sectsForGeom = PRVI25_InvConfigFactory.MueAsCrustal.removeMuertosFromInterface(sectsForGeom);
			Preconditions.checkState(sectsForGeom.size() == sectsForMinMag.size(),
					"Geometry (%s) and min-mag (%s) section counts differ!", sectsForGeom.size(), sectsForMinMag.size());
		}

		IncrementalMagFreqDist refMFD = FaultSysTools.initEmptyMFD(OVERALL_MMIN, 9d);
		Map<Double, IncrementalMagFreqDist> mMaxMFDCache = new HashMap<>();
		if (mfdBuilderFunc == null) {
			PRVI25_SeismicityRateEpoch epoch = fullBranch.requireValue(PRVI25_SeismicityRateEpoch.class);
			if (seisRegion == PRVI25_SeismicityRegions.MUE_INTERFACE) {
				PRVI25_SubductionMuertosSeismicityRate seisBranch = fullBranch.requireValue(PRVI25_SubductionMuertosSeismicityRate.class);
				mfdBuilderFunc = new Function<Double, IncrementalMagFreqDist>() {
					
					@Override
					public IncrementalMagFreqDist apply(Double mMax) {
						try {
							return seisBranch.build(epoch, refMFD, mMax, Double.NaN, false);
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
				};
			} else if (seisRegion == PRVI25_SeismicityRegions.CAR_INTERFACE) {
				PRVI25_SubductionCaribbeanSeismicityRate seisBranch = fullBranch.requireValue(PRVI25_SubductionCaribbeanSeismicityRate.class);
				mfdBuilderFunc = new Function<Double, IncrementalMagFreqDist>() {
					
					@Override
					public IncrementalMagFreqDist apply(Double mMax) {
						try {
							return seisBranch.build(epoch, refMFD, mMax, Double.NaN, false);
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
				};
			} else {
				throw new IllegalStateException("Not an interface region: "+seisRegion);
			}
		}
		PRVI25_DeclusteringAlgorithms declusteringAlg = fullBranch.requireValue(PRVI25_DeclusteringAlgorithms.class);
		PRVI25_SeisSmoothingAlgorithms seisSmooth = fullBranch.requireValue(PRVI25_SeisSmoothingAlgorithms.class);
		
		loadRegionDepthStrikeData(seisRegion);
		GriddedGeoDataSet depthData = gridDepths.get(seisRegion);
		Preconditions.checkNotNull(depthData);
		GriddedGeoDataSet strikeData = gridStrikes.get(seisRegion);
		Preconditions.checkNotNull(strikeData);
		GriddedGeoDataSet dipData = gridDips.get(seisRegion);
		Preconditions.checkNotNull(dipData);
		
		// total G-R up to Mmax
//		IncrementalMagFreqDist totalGR = seisBranch.build(seisRegion, refMFD, maxMagOff);
		
		GriddedGeoDataSet pdf = seisSmooth.loadXYZ(seisRegion, declusteringAlg);
		GriddedRegion region = pdf.getRegion();
		
		List<FaultSection> minMagMatchingSubSects = null;
		List<Double> minMagMatchingSubSectMMins = null;
		List<Double> minMagMatchingSubSectMMaxs = null;
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
			List<Double> matchingSubSectMMaxs;
			List<RuptureSurface> matchingSectSurfs;
			int[] sectMappings;
			double overallFurthestMatch;
			if (geom && sectsForGeom == sectsForMinMag) {
				// don't need to recalculate
				matchingSubSects = minMagMatchingSubSects;
				matchingSubSectMMins = null;
				matchingSubSectMMaxs = null;
				matchingSectSurfs = minMagMatchingSectSurfs;
				sectMappings = minMagSectMappings;
				overallFurthestMatch = overallFurthestMinMagMatch;
			} else {
				// need to calculate
				
				List<? extends FaultSection> sects = geom ? sectsForGeom : sectsForMinMag;
				matchingSubSects = new ArrayList<>();
				List<Region> matchingSubSectOutlines = new ArrayList<>();
				matchingSubSectMMins = geom ? null : new ArrayList<>();
				matchingSubSectMMaxs = geom ? null : new ArrayList<>();
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
						double sectMaxMag = geom ? Double.NaN : rupSet.getMaxMagForSection(sect.getSectionId());
						if (D) {
							System.out.println(sect.getSectionId()+". "+sect.getSectionName()+": Mmin="+(float)sectMinMag);
							System.out.println("\tStrike="+sect.getFaultTrace().getAveStrike()
									+", firstEdgeAz="+(float)LocationUtils.azimuth(firstEdge[0], firstEdge[1])
									+", lastEdgeAz="+(float)LocationUtils.azimuth(lastEdge[0], lastEdge[1]));
							System.out.println("\tDepths: "+(float)sect.getOrigAveUpperDepth()+" "+(float)sect.getAveLowerDepth());
						}
						if (!geom) {
							matchingSubSectMMins.add(sectMinMag);
							matchingSubSectMMaxs.add(sectMaxMag);
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
//					Preconditions.checkState(LocationUtils.areSimilar(loc, depthData.getLocation(gridIndex)));
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
				minMagMatchingSubSectMMaxs = matchingSubSectMMaxs;
				minMagMatchingSectSurfs = matchingSectSurfs;
				minMagSectMappings = sectMappings;
				overallFurthestMinMagMatch = overallFurthestMatch;
			}
		}
		
		System.out.println("MFD Gridding Mmmax="+(float)refMFD.getMaxX());
		
		List<List<GriddedRupture>> ruptureLists = new ArrayList<>(pdf.size());
		
		double overallMinDepth = depthData.getMinZ();
		double overallMaxDepth = depthData.getMaxZ();
		double minDepthRangeLimit = 0.2*(overallMaxDepth - overallMinDepth);
		if (D) System.out.println("Overall Slab2 depth range: ["+(float)overallMinDepth+", "+(float)overallMaxDepth+"]");
		
		double[] gridRateSubtracts = null;
		if (RATE_BALANCE_INTERFACE_GRIDDED) {
			double[] sectNuclRates = sol.calcNucleationRateForAllSects(0d, Double.POSITIVE_INFINITY);
			int[] sectMappingCounts = new int[sectsForMinMag.size()];
			for (int gridIndex=0; gridIndex<pdf.size(); gridIndex++) {
				int sectIndex = geomSectMappings[gridIndex];
				sectMappingCounts[sectIndex]++;
			}
			// split the section supra-seis nucleation rate evenly across all mapped grid nodes
			gridRateSubtracts = new double[pdf.size()];
			for (int gridIndex=0; gridIndex<pdf.size(); gridIndex++) {
				int sectIndex = geomSectMappings[gridIndex];
				gridRateSubtracts[gridIndex] = sectNuclRates[sectIndex]/(double)sectMappingCounts[sectIndex];
			}
		}
		
		// used only when INTERFACE_USE_SECT_PROPERTIES == false
		double overallDepthUpperLimit = Double.NaN;
		double overallDepthLowerLimit = Double.NaN;
		if (!INTERFACE_USE_SECT_PROPERTIES) {
			if (seisRegion == PRVI25_SeismicityRegions.CAR_INTERFACE)
				overallDepthLowerLimit = INTERFACE_CAR_MAX_DEPTH;
			else
				overallDepthLowerLimit = INTERFACE_MUE_MAX_DEPTH;
			overallDepthUpperLimit = depthData.getMinZ();
			Preconditions.checkState(overallDepthUpperLimit >= 0d);
			System.out.println("Gridded depth limits for "+region
					+": ["+(float)overallDepthUpperLimit+", "+(float)overallDepthUpperLimit+"]");
		}
		
		double totRateM5 = 0d;
		MinMaxAveTracker reductionPDiffStats = RATE_BALANCE_INTERFACE_GRIDDED ? new MinMaxAveTracker() : null;
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
				int depthIndex = depthData.indexOf(gridLoc);
				Preconditions.checkState(depthIndex >= 0, "No location in depth data for %s. %s", gridIndex, gridLoc);
				Location depthLoc = depthData.getLocation(depthIndex);
				Preconditions.checkState(LocationUtils.areSimilar(depthLoc, gridLoc),
						"Depth data location mismatch: %s. %s != %s. %s", gridIndex, gridLoc, depthIndex, depthLoc);
				strike = strikeData.get(depthIndex);
				depth = depthData.get(depthIndex);
				// depth + down, so upper <= depth <= lower
				Preconditions.checkState(depth >= overallDepthUpperLimit, "Depth=%s < limit=%s", depth, overallDepthUpperLimit);
				Preconditions.checkState(depth <= overallDepthLowerLimit, "Depth=%s > limit=%s", depth, overallDepthLowerLimit);
				
				depthUpperLimit = overallDepthUpperLimit;
				depthLowerLimit = overallDepthLowerLimit;
				dip = dipData.get(depthIndex);
				Preconditions.checkState(Double.isFinite(dip), "Bad dip for location %s: %s", gridIndex, gridLoc);
				FaultUtils.assertValidDip(dip);
				
				// all of the below was trying to use the sections for dip and extents with fallback for the edges
				// just outside; messy, and would get even messier with the new larger polygons
//				// find the depth limits at this point on the surface
//				// first find the edges of the seismicity region in the strike-normal direction
//				Location leftEdgeLoc = null;
//				Location rightEdgeLoc = null;
//				for (boolean left : new boolean[] {true, false}) {
//					double azRad = left ? Math.toRadians(strike-90d) : Math.toRadians(strike+90d);
//					
//					Location lastInsideLoc = gridLoc;
//					double lastInsideLocDist = 0d;
//					
//					Location firstOutsideLoc = null;
//					double firstOutsideLocDist = 10d;
//					
//					while (firstOutsideLoc == null) {
//						Location testLoc = LocationUtils.location(gridLoc, azRad, firstOutsideLocDist);
//						if (region.indexForLocation(testLoc) >= 0) {
//							// still inside
//							lastInsideLocDist = firstOutsideLocDist;
//							firstOutsideLocDist += 10d;
//							lastInsideLoc = testLoc;
//						} else {
//							firstOutsideLoc = testLoc;
//						}
//					}
//					
//					// now we have a location that's inside and outside, figure out where the boundary actually is
//					// do a binary search
//					for (int i=0; i<10; i++) {
//						double testDist = 0.5*(lastInsideLocDist + firstOutsideLocDist);
//						Location testLoc = LocationUtils.location(gridLoc, azRad, testDist);
//						if (region.indexForLocation(testLoc) >= 0) {
//							lastInsideLoc = testLoc;
//							lastInsideLocDist = testDist;
//						} else {
//							firstOutsideLoc = testLoc;
//							firstOutsideLocDist = testDist;
//						}
//					}
//					
//					if (left)
//						leftEdgeLoc = lastInsideLoc;
//					else
//						rightEdgeLoc = lastInsideLoc;
//				}
//				int leftIndex = region.indexForLocation(leftEdgeLoc);
//				int rightIndex = region.indexForLocation(rightEdgeLoc);
//				double leftDepth = depthData.get(leftIndex);
//				double rightDepth = depthData.get(rightIndex);
//				if (leftIndex == rightIndex || leftDepth == rightDepth) {
//					// just use section dip
//					dip = matchingSection.getAveDip();
//				} else {
//					// calculate dip from the surface
//					double horzDist = LocationUtils.horzDistanceFast(leftEdgeLoc, rightEdgeLoc);
//					double vertDist = Math.abs(leftDepth - rightDepth);
//					Preconditions.checkState(horzDist > 0d);
//					Preconditions.checkState(vertDist > 0d);
//					// tan(dip) = vert / horz
//					// dip = arctan(vert/horz)
//					dip = Math.toDegrees(Math.atan(vertDist/horzDist));
//					FaultUtils.assertValidDip(dip);
////					if ((float)gridLoc.lat == 17.9f && (float)gridLoc.lon == -66.3f) {
////						System.out.println("Debug for gridLoc="+gridLoc);
////						System.out.println("\tleftDepth="+leftDepth);
////						System.out.println("\trightDepth="+rightDepth);
////						System.out.println("\tvertDist="+vertDist);
////						System.out.println("\thorzDist="+horzDist);
////						System.out.println("\tdip="+dip);
////					}
//				}
//				// left *should* be above right
//				depthUpperLimit = Math.min(leftDepth, rightDepth);
//				depthLowerLimit = Math.max(leftDepth, rightDepth);
//				Preconditions.checkState(depthUpperLimit <= depth, "depthUpperLimit=%s but depth=%s", depthUpperLimit, depth);
//				Preconditions.checkState(depthLowerLimit >= depth, "depthLowerLimit=%s but depth=%s", depthLowerLimit, depth);
//				
//				double myMaxDepth = overallMaxDepth;
//				double myMinDepth = overallMinDepth;
//				
//				// this uses section depths, but it can extend past the section horizontally to achieve those depths
////				double sectLowerDepth = matchingSection.getAveLowerDepth();
////				myMaxDepth = Math.max(sectLowerDepth, myMaxDepth);
////				if (sectLowerDepth > depthLowerLimit)
////					depthLowerLimit = sectLowerDepth;
////				double sectUpperDepth = matchingSection.getOrigAveUpperDepth();
////				myMinDepth = Math.min(sectUpperDepth, myMinDepth);
////				if (sectUpperDepth < depthUpperLimit)
////					depthUpperLimit = sectUpperDepth;
//				
//				double delta = depthLowerLimit - depthUpperLimit;
//				if (delta < minDepthRangeLimit) {
//					// too narrow, probably right at the skinny end, expand to a sensible value
//					System.out.println("Delta="+(float)delta+" is too narrow, expanding to "+(float)minDepthRangeLimit+" for depth="+(float)depth);
//					depthUpperLimit = depth - 0.5*minDepthRangeLimit;
//					depthLowerLimit = depth + 0.5*minDepthRangeLimit;
//					System.out.println("\tUpdated limits: ["+(float)depthUpperLimit+", "+(float)depthLowerLimit+"]");
//					if (depthLowerLimit > myMaxDepth) {
//						depthLowerLimit = overallMaxDepth;
//						depthUpperLimit = depthLowerLimit - minDepthRangeLimit;
//						System.out.println("\twould have extended beyond overall max depth; updated limits: ["
//								+(float)depthUpperLimit+", "+(float)depthLowerLimit+"]");
//					} else if (depthUpperLimit < myMinDepth) {
//						depthUpperLimit = myMinDepth;
//						depthLowerLimit = depthUpperLimit + minDepthRangeLimit;
//						System.out.println("\twould have extended above overall min depth; updated limits: ["
//								+(float)depthUpperLimit+", "+(float)depthLowerLimit+"]");
//					}
//				}
//				Preconditions.checkState(depthLowerLimit <= myMaxDepth,
//						"Bad depthLowerLimit=%s with myMaxDepth=%s", depthLowerLimit, myMaxDepth);
//				Preconditions.checkState(depthUpperLimit >= myMinDepth,
//						"Bad depthUpperLimit=%s with overallMinDepth=%s", depthUpperLimit, myMinDepth);
			}
			
			int[] assocIDs = { matchingSection.getSectionId() };
			double[] assocFracts = { 1d };
			
			double dipRad = Math.toRadians(dip);
//			double rake = matchingSection.getAveRake();
			double rake = 90d; // fix it to 90: don't want multiple instances of each rupture after branch averaging
//			double strike = matchingSection.getFaultTrace().getAveStrike();
			
			double sectMmin = minMagMatchingSubSectMMins.get(minMagSectMappings[gridIndex]);
			int sectMminIndex = refMFD.getClosestXIndex(sectMmin);
			Preconditions.checkState(sectMminIndex > 0);

			double mMax;
			if (RATE_BALANCE_INTERFACE_GRIDDED) {
				// build MFD to section Mmax, then carve out and truncate in the next step
				double sectMmax = minMagMatchingSubSectMMaxs.get(minMagSectMappings[gridIndex]);
				int sectMmaxIndex = refMFD.getClosestXIndex(sectMmax);
				Preconditions.checkState(sectMmaxIndex > 0);
				mMax = refMFD.getX(sectMmaxIndex);
			} else {
				// build MFD to sect Mmin, no carve out
				// set Mmax to one bin below the matching section Mmin
				mMax = refMFD.getX(sectMminIndex-1);
			}
			
			// modify to use our actual closest depth as these are average and not rectangular
			double maxDDW = (depthLowerLimit - depthUpperLimit)/Math.sin(dipRad);
			
			IncrementalMagFreqDist mfd = mMaxMFDCache.get(mMax);
			if (mfd == null) {
				mfd = mfdBuilderFunc.apply(mMax);
				mMaxMFDCache.put(mMax, mfd);
			}
			mfd = mfd.deepClone();
			mfd.scale(fractRate);
			if (RATE_BALANCE_INTERFACE_GRIDDED) {
				double subtract = gridRateSubtracts[gridIndex];
				if (subtract > 0d) {
					int m5Index = mfd.getClosestXIndex(5.01);
					// this is up to interface Mmax
					double origRateM5 = mfd.getCumRate(m5Index);
					double targetRateM5 = origRateM5 - subtract;
					if (targetRateM5 <= 0) {
						System.err.println("WARNING: subtractRate="+(float)subtract+" > gridM5["+gridIndex+"]="
								+origRateM5+", setting grid rate to zero");
						ruptureLists.add(new ArrayList<>());
						reductionPDiffStats.addValue(100d);
						continue;
					}
					// now clear out all bins at or above sect mMin
					Preconditions.checkState(sectMminIndex > 0);
					for (int i=sectMminIndex; i<mfd.size(); i++)
						mfd.set(i, 0d);
					double afterCarveRateM5 = mfd.getCumRate(m5Index);
					double scalar = targetRateM5/afterCarveRateM5;
					reductionPDiffStats.addValue(100d*subtract/origRateM5);
					mfd.scale(scalar);
					Preconditions.checkState(Precision.equals(targetRateM5, mfd.getCumRate(m5Index), 0.00001));
				}
			}
			
			List<GriddedRupture> ruptureList = new ArrayList<>(sectMminIndex);
			ruptureLists.add(ruptureList);
			for (int i=0; i<mfd.size(); i++) {
				double mag = mfd.getX(i);
				double rate = mfd.getY(i);
				
				if (rate == 0d || (float)mag < (float)OVERALL_MMIN)
					continue;
				
				if (mag >= 5d)
					totRateM5 += rate;
				
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
		
		if (RATE_BALANCE_INTERFACE_GRIDDED) {
			IncrementalMagFreqDist mfd = mMaxMFDCache.values().iterator().next();
			double origM5 = mfd.getCumRate(mfd.getClosestXIndex(5.001));
			double origM6 = mfd.getCumRate(mfd.getClosestXIndex(6.001));
			System.out.println("Rate balance for "+seisRegion+":");
			System.out.println("\treduced origM5="+(float)origM5+" to "+(float)totRateM5
					+" ("+new DecimalFormat("0.000%").format((totRateM5-origM5)/origM5)+")");
			System.out.println("\tReduction %diff stats: "+reductionPDiffStats);
		}
		
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
		
		ModuleContainer.VERBOSE_DEFAULT = false;
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
		levels.addAll(PRVI25_LogicTree.levelsSubduction);
		levels.addAll(PRVI25_LogicTree.levelsSubductionGridded);
		
		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels);
		
		for (LogicTreeNode node : PRVI25_LogicTree.DEFAULT_SUBDUCTION_INTERFACE)
			branch.setValue(node);
		for (LogicTreeNode node : PRVI25_LogicTree.DEFAULT_SUBDUCTION_GRIDDED)
			branch.setValue(node);
		
		branch.setValue(PRVI25_SubductionScalingRelationships.AVERAGE);
//		branch.setValue(PRVI25_RegionalSeismicity.LOW);
		branch.setValue(PRVI25_SubductionMuertosSeismicityRate.PREFFERRED);
		branch.setValue(PRVI25_SubductionCaribbeanSeismicityRate.PREFFERRED);
		branch.setValue(PRVI25_SeisSmoothingAlgorithms.AVERAGE);
		branch.setValue(PRVI25_DeclusteringAlgorithms.AVERAGE);
		
//		buildSlabGridSourceList(branch);
		
//		INTERFACE_USE_SECT_PROPERTIES = false;
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
				+ "2025_08_01-prvi25_subduction_branches/results_PRVI_SUB_FM_LARGE_branch_averaged.zip"));
		branch.setValue(PRVI25_SubductionFaultModels.PRVI_SUB_FM_LARGE);
//		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
//				+ "2024_12_12-prvi25_subduction_branches/results_PRVI_SUB_FM_SMALL_branch_averaged.zip"));
//		branch.setValue(PRVI25_SubductionFaultModels.PRVI_SUB_FM_SMALL);
		
		
//		GridSourceList slabModel = buildSlabGridSourceList(branch);
//		PRVI25_SeismicityRegions seisReg = PRVI25_SeismicityRegions.CAR_INTRASLAB;
//		GridSourceList slabModel = buildSlabGridSourceList(branch, seisReg);
//		double rateM5 = 0d;
//		for (int gridIndex=0; gridIndex<slabModel.getNumLocations(); gridIndex++)
//			for (GriddedRupture rup : slabModel.getRuptures(TectonicRegionType.SUBDUCTION_SLAB, gridIndex))
//				if (rup.properties.magnitude >= 5d)
//					rateM5 += rup.rate;
//		System.out.println("rate M>5: "+(float)rateM5);
		
		INTERFACE_USE_SECT_PROPERTIES = true;
		GridSourceList mueInterface = buildInterfaceGridSourceList(sol, branch, PRVI25_SeismicityRegions.MUE_INTERFACE);
		sol.setGridSourceProvider(mueInterface);
//		sol.write(new File("/tmp/prvi_mue_interface_grid.zip"));
//		sol.write(new File("/tmp/prvi_mue_interface_grid_sect_props.zip"));
		
		// quick surface hack
		GridSourceList surfaceList = new GridSourceList.DynamicallyBuilt(mueInterface.getTectonicRegionTypes(),
				mueInterface.getGriddedRegion(), mueInterface.getRefMFD()) {
			
			@Override
			public int getNumSources() {
				return mueInterface.getNumSources();
			}
			
			@Override
			public int getLocationIndexForSource(int sourceIndex) {
				return mueInterface.getLocationIndexForSource(sourceIndex);
			}
			
			@Override
			public TectonicRegionType tectonicRegionTypeForSourceIndex(int sourceIndex) {
				return mueInterface.tectonicRegionTypeForSourceIndex(sourceIndex);
			}
			
			@Override
			public Set<Integer> getAssociatedGridIndexes(int sectionIndex) {
				return mueInterface.getAssociatedGridIndexes(sectionIndex);
			}
			
			@Override
			protected List<GriddedRupture> buildRuptures(TectonicRegionType tectonicRegionType, int gridIndex) {
				ImmutableList<GriddedRupture> origRups = mueInterface.getRuptures(tectonicRegionType, gridIndex);
				List<GriddedRupture> surfaceRups = new ArrayList<>(origRups.size());
				GriddedRuptureProperties surfaceProps = new GriddedRuptureProperties(7d, 90d, 90, 0d, null, 0d, 0d, 0d, 0d, 0d, tectonicRegionType);
				for (GriddedRupture rup : origRups)
					surfaceRups.add(new GriddedRupture(gridIndex, rup.location, surfaceProps, rup.rate));
				return surfaceRups;
			}
		};
		sol.setGridSourceProvider(surfaceList);
//		sol.write(new File("/tmp/prvi_mue_interface_grid.zip"));
		sol.write(new File("/tmp/prvi_mue_interface_grid_at_surf.zip"));
		
//		GridSourceList combSources = buildCombinedSubductionGridSourceList(sol, branch);
//		sol.setGridSourceProvider(combSources);
//		sol.write(new File("/tmp/prvi_comb_grid_source_test.zip"));
//		FaultSystemSolution.load(new File("/tmp/prvi_comb_grid_source_test.zip")).getGridSourceProvider();
		
//		RATE_BALANCE_INTERFACE_GRIDDED = false;
//		PRVI25_SeismicityRegions seisReg = PRVI25_SeismicityRegions.CAR_INTERFACE;
////		PRVI25_SeismicityRegions seisReg = PRVI25_SeismicityRegions.MUE_INTERFACE;
//		Region region = seisReg.load();
//		GridSourceList interfaceModel = buildInterfaceGridSourceList(sol, branch, seisReg);
////		GridSourceList interfaceModel = buildInterfaceGridSourceList(sol, branch);
//		AveragingAccumulator<GridSourceProvider> averager = interfaceModel.averagingAccumulator();
//		for (int i=0; i<10; i++)
//			averager.process(interfaceModel, 1d);
//		interfaceModel = (GridSourceList) averager.getAverage();
//		double rateM5 = 0d;
//		for (int gridIndex=0; gridIndex<interfaceModel.getNumLocations(); gridIndex++) {
//			if (region.contains(interfaceModel.getLocation(gridIndex))) {
//				for (GriddedRupture rup : interfaceModel.getRuptures(TectonicRegionType.SUBDUCTION_INTERFACE, gridIndex))
//					if (rup.properties.magnitude >= 5d)
//						rateM5 += rup.rate;
//			}
//		}
//		System.out.println(seisReg+" rate M>5: "+(float)rateM5);
//		sol.setGridSourceProvider(interfaceModel);
//		String name = "sol_"+branch.requireValue(PRVI25_SubductionFaultModels.class).getFilePrefix()+"_with_"+seisReg.name()+"_gridded";
//		if (!INTERFACE_USE_SECT_PROPERTIES)
//			name += "_slab_depths";
//		sol.write(new File("/tmp/", name+".zip"));
//		if (RATE_BALANCE_INTERFACE_GRIDDED) {
//			IncrementalMagFreqDist refMFD = interfaceModel.getRefMFD();
//			SummedMagFreqDist balancedMFD = new SummedMagFreqDist(refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
//			for (int l=0; l<interfaceModel.getNumLocations(); l++)
//				balancedMFD.addIncrementalMagFreqDist(interfaceModel.getMFD(l));
//			
//			// do it again without rate balancing
//			RATE_BALANCE_INTERFACE_GRIDDED = false;
//			interfaceModel = buildInterfaceGridSourceList(sol, branch, seisReg);
//			SummedMagFreqDist unbalancedMFD = new SummedMagFreqDist(refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
//			for (int l=0; l<interfaceModel.getNumLocations(); l++)
//				unbalancedMFD.addIncrementalMagFreqDist(interfaceModel.getMFD(l));
//			DecimalFormat pDF = new DecimalFormat("0.000%");
//			for (int i=0; i<balancedMFD.size(); i++) {
//				double mag = balancedMFD.getX(i);
//				if (mag < 5d)
//					continue;
//				double rateBalanced = balancedMFD.getY(i);
//				double rateUnbalanced = unbalancedMFD.getY(i);
//				if (rateBalanced == 0 && rateUnbalanced == 0)
//					break;
//				System.out.println((float)mag+"\t"+(float)rateUnbalanced+"\t"+(float)rateBalanced+"\t("
//						+pDF.format((rateBalanced - rateUnbalanced)/rateUnbalanced)+")");
//			}
//		}
//		
//		GriddedRegion gridReg = interfaceModel.getGriddedRegion();
//		GriddedGeoDataSet sectMapIndexes = new GriddedGeoDataSet(gridReg);
//		GriddedGeoDataSet gridMmaxes = new GriddedGeoDataSet(gridReg);
//		for (int l=0; l<gridReg.getNodeCount(); l++) {
//			double mMax = 0d;
//			int mapped = -1;
//			for (GriddedRupture rup : interfaceModel.getRuptures(TectonicRegionType.SUBDUCTION_INTERFACE, l)) {
//				Preconditions.checkState(rup.associatedSections.length == 1);
//				if (mapped == -1)
//					mapped = rup.associatedSections[0];
//				else
//					Preconditions.checkState(rup.associatedSections[0] == mapped);
//				mMax = Math.max(mMax, rup.properties.magnitude);
//			}
//			sectMapIndexes.set(l, mapped);
//			gridMmaxes.set(l, mMax);
//		}
//		GeographicMapMaker mapMaker = new GeographicMapMaker(sol.getRupSet().getFaultSectionDataList());
//		CPT rainbow = GMT_CPT_Files.RAINBOW_UNIFORM.instance().asDiscrete(10, true);
//		CPT indexCPT = new CPT();
//		while (indexCPT.size() < (int)sectMapIndexes.getMaxZ()) {
//			for (CPTVal val : rainbow) {
//				double index = (double)indexCPT.size();
//				indexCPT.add(new CPTVal(index+0d, val.minColor, index+1d, val.maxColor));
//			}
//		}
////		CPT indexCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(sectMapIndexes.getMinZ(), sectMapIndexes.getMaxZ());
//		CPT magCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(7d, gridMmaxes.getMaxZ()+0.01);
//		
//		mapMaker.plotXYZData(sectMapIndexes, indexCPT, "Section mapped index");
//		mapMaker.plot(new File("/tmp"), "sub_grid_sect_mappings", " ");
//		mapMaker.plotXYZData(gridMmaxes, magCPT, "Grid Mmax");
//		mapMaker.plot(new File("/tmp"), "sub_grid_mmax", " ");
		
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
