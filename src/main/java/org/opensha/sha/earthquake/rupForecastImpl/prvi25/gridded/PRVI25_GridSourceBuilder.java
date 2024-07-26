package org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.MagScalingRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.CubedGriddedRegion;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_FaultCubeAssociations;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_SingleRegionGridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_DeclusteringAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_RegionalSeismicity;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SeisSmoothingAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.SeismicityRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.SeisDepthDistribution;

public class PRVI25_GridSourceBuilder {
	
	/**
	 * if true, will subtract on-fault supra-seis rates from gridded MFDs
	 */
	public static final boolean RATE_BALANCE_GRIDDED = true;
	
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
	
	public static NSHM23_SingleRegionGridSourceProvider buildCrustalGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> branch) throws IOException {
		doPreGridBuildHook(sol, branch);
		FaultSystemRupSet rupSet = sol.getRupSet();
		Preconditions.checkState(branch.hasValue(PRVI25_CrustalFaultModels.class), "Only crustal supported (so far)");
		FaultCubeAssociations cubeAssociations = rupSet.requireModule(FaultCubeAssociations.class);
		return buildCrustalGridSourceProv(sol, branch, SeismicityRegions.CRUSTAL, cubeAssociations);
	}
	
	public static NSHM23_SingleRegionGridSourceProvider buildCrustalGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> branch,
			SeismicityRegions seisRegion, FaultCubeAssociations cubeAssociations)  throws IOException {
		GriddedRegion gridReg = cubeAssociations.getRegion();
		
		double maxMagOff = branch.requireValue(MaxMagOffFaultBranchNode.class).getMaxMagOffFault();
		
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(
				Math.max(maxMagOff, sol.getRupSet().getMaxMag()));
		
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
				if (RATE_BALANCE_GRIDDED) {
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
		System.err.println("WARNING: DON'T YET HAVE FOCAL MECHS!");
		double[] fractStrikeSlip = new double[gridReg.getNodeCount()];
		double[] fractReverse = new double[gridReg.getNodeCount()];
		double[] fractNormal = new double[gridReg.getNodeCount()];
		for (int i=0; i<fractStrikeSlip.length; i++) {
			// TODO: these moment fractions from the crustal DM
			fractStrikeSlip[i] = 0.63;
			fractReverse[i] = 0.11;
			fractNormal[i] = 0.26;
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

		return new NSHM23_SingleRegionGridSourceProvider(sol, cubeAssociations, pdf, totalGridded, binnedDepthDistFunc,
				fractStrikeSlip, fractNormal, fractReverse);
	}
	
	public static GridSourceList buildSlabGridSourceList(LogicTreeBranch<?> branch) throws IOException {
		GridSourceList muertos = buildSlabGridSourceList(branch, SeismicityRegions.MUE_INTRASLAB);
		GridSourceList car = buildSlabGridSourceList(branch, SeismicityRegions.CAR_INTRASLAB);
		return GridSourceList.combine(car, muertos);
	}
	
	public static GridSourceList buildSlabGridSourceList(LogicTreeBranch<?> branch, SeismicityRegions seisRegion) throws IOException {
		Preconditions.checkState(seisRegion == SeismicityRegions.CAR_INTRASLAB
				|| seisRegion == SeismicityRegions.MUE_INTRASLAB);
		
		double maxMagOff = SLAB_MMAX;
		
		PRVI25_RegionalSeismicity seisBranch = branch.requireValue(PRVI25_RegionalSeismicity.class);
		PRVI25_DeclusteringAlgorithms declusteringAlg = branch.requireValue(PRVI25_DeclusteringAlgorithms.class);
		PRVI25_SeisSmoothingAlgorithms seisSmooth = branch.requireValue(PRVI25_SeisSmoothingAlgorithms.class);
		
		// total G-R up to Mmax
		IncrementalMagFreqDist totalGR = seisBranch.build(seisRegion, FaultSysTools.initEmptyMFD(maxMagOff), maxMagOff);
		
		GriddedGeoDataSet pdf = seisSmooth.loadXYZ(seisRegion, declusteringAlg);
		
		List<List<GriddedRupture>> ruptureLists = new ArrayList<>(pdf.size());
		
		// TODO
		System.err.println("WARNING: USING ARBITRARILY SLAB FOCAL MECH");
		double rake = -90d;
		double dip = 50d;
		
		double upper = 50d;
		System.err.println("WARNING: USING ARBITRARILY SLAB UPPER DEPTH");
		double maxLower = 65d;
		double maxDDW = (maxLower-upper)/Math.sin(Math.toRadians(dip));
		MagScalingRelationship scale = new WC1994_MagLengthRelationship();
		
		for (int gridIndex=0; gridIndex<pdf.size(); gridIndex++) {
			double fract = pdf.get(gridIndex);
			if (fract == 0d) {
				ruptureLists.add(null);
				continue;
			}
			
			Location loc = pdf.getLocation(gridIndex);
			
			List<GriddedRupture> ruptureList = new ArrayList<>(totalGR.size());
			ruptureLists.add(ruptureList);
			for (int i=0; i<totalGR.size(); i++) {
				double mag = totalGR.getX(i);
				double rate = totalGR.getY(i)*fract;
				
				double length, ddw;
				if (scale instanceof MagLengthRelationship) {
					length = ((MagLengthRelationship)scale).getMedianLength(mag);
					ddw = Math.min(maxDDW, length);
				} else {
					throw new IllegalStateException();
				}
				double lower;
				if (dip == 90d)
					lower = upper + ddw;
				else
					lower = upper + ddw*Math.sin(Math.toRadians(dip));
				
				ruptureList.add(new GriddedRupture(gridIndex, loc, mag, rate, rake, dip, Double.NaN, null,
						upper, lower, length, TectonicRegionType.SUBDUCTION_SLAB, null, null));
			}
		}
		
		return new GridSourceList(pdf.getRegion(), TectonicRegionType.SUBDUCTION_SLAB, ruptureLists);
	}
	
	public static GridSourceList buildInterfaceGridSourceList(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch) throws IOException {
		GridSourceList muertos = buildInterfaceGridSourceList(sol, fullBranch, SeismicityRegions.MUE_INTERFACE);
		GridSourceList car = buildInterfaceGridSourceList(sol, fullBranch, SeismicityRegions.CAR_INTERFACE);
		return GridSourceList.combine(car, muertos);
	}
	
//	public static GridSourceList buildInterfaceGridSourceList(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch,
//			SeismicityRegions seisRegion) throws IOException {
//		Preconditions.checkState(seisRegion == SeismicityRegions.CAR_INTERFACE
//				|| seisRegion == SeismicityRegions.MUE_INTERFACE);
//		
//		int[] parentID;
//		switch (seisRegion) {
//		case CAR_INTERFACE:
//			
//			break;
//
//		default:
//			break;
//		}
//	}

	public static void main(String[] args) throws IOException {
		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(PRVI25_LogicTreeBranch.levelsSubductionGridded);
		branch.setValue(PRVI25_RegionalSeismicity.PREFFERRED);
		branch.setValue(PRVI25_SeisSmoothingAlgorithms.AVERAGE);
		branch.setValue(PRVI25_DeclusteringAlgorithms.AVERAGE);
		
		buildSlabGridSourceList(branch);
	}

}
