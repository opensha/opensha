package org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.MagScalingRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.CubedGriddedRegion;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.FiniteRuptureConverter;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_FaultCubeAssociations;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_SingleRegionGridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_DeclusteringAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_RegionalSeismicity;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SeisSmoothingAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
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
	public static final boolean RATE_BALANCE_GRIDDED = true;
	
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
	
	public static class NSHM23_WUS_FiniteRuptureConverter implements FiniteRuptureConverter {
		
		private WC1994_MagLengthRelationship WC94 = new WC1994_MagLengthRelationship();

		@Override
		public GriddedRupture buildFiniteRupture(int gridIndex, Location loc, double magnitude, double rate,
				FocalMech focalMech, int[] associatedSections, double[] associatedSectionFracts) {
			// TODO Auto-generated method stub
			
			double dipRad = Math.toRadians(focalMech.dip());
			
			double depth = (float)magnitude < 6.5f ? 5d : 1d;
			double length = WC94.getMedianLength(magnitude);
			double aspectWidth = length / 1.5;
			double ddWidth = (14.0 - depth) / Math.sin(dipRad);
			ddWidth = Math.min(aspectWidth, ddWidth);
			double lower = depth + ddWidth * Math.sin(dipRad);
			
			return new GriddedRupture(gridIndex, loc, magnitude, rate, focalMech.rake(), focalMech.dip(), Double.NaN,
					null, depth, lower, length, Double.NaN, Double.NaN,
					TectonicRegionType.ACTIVE_SHALLOW,associatedSections, associatedSectionFracts);
		}
		
	}
	
	public static GridSourceList buildCrustalGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> branch) throws IOException {
		doPreGridBuildHook(sol, branch);
		FaultSystemRupSet rupSet = sol.getRupSet();
		Preconditions.checkState(branch.hasValue(PRVI25_CrustalFaultModels.class), "Only crustal supported (so far)");
		FaultCubeAssociations cubeAssociations = rupSet.requireModule(FaultCubeAssociations.class);
		NSHM23_SingleRegionGridSourceProvider gridProv = buildCrustalGridSourceProv(sol, branch, SeismicityRegions.CRUSTAL, cubeAssociations);
		
		return GridSourceList.convert(gridProv, cubeAssociations, new NSHM23_WUS_FiniteRuptureConverter());
	}
	
	public static NSHM23_SingleRegionGridSourceProvider buildCrustalGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> branch,
			SeismicityRegions seisRegion, FaultCubeAssociations cubeAssociations)  throws IOException {
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
	
	public static GridSourceList buildCombinedSubductionGridSourceList(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch) throws IOException {
		GridSourceList muertosSlab = buildSlabGridSourceList(fullBranch, SeismicityRegions.MUE_INTRASLAB);
		GridSourceList carSlab = buildSlabGridSourceList(fullBranch, SeismicityRegions.CAR_INTRASLAB);
		GridSourceList muertosInterface = buildInterfaceGridSourceList(sol, fullBranch, SeismicityRegions.MUE_INTERFACE);
		GridSourceList carInterface = buildInterfaceGridSourceList(sol, fullBranch, SeismicityRegions.CAR_INTERFACE);
		
		// for some reason, doing them one by one in the combine method doesn't work; do it ahead of time pairwise
		Region muertosUnionRegion = Region.union(SeismicityRegions.MUE_INTRASLAB.load(), SeismicityRegions.MUE_INTERFACE.load());
		Region carUnionRegion = Region.union(SeismicityRegions.CAR_INTRASLAB.load(), SeismicityRegions.CAR_INTERFACE.load());
		Preconditions.checkNotNull(muertosUnionRegion, "Couldn't union Muertos regions");
		Preconditions.checkNotNull(carUnionRegion, "Couldn't union CAR regions");
		Region unionRegion = Region.union(muertosUnionRegion, carUnionRegion);
		Preconditions.checkNotNull(unionRegion, "Couldn't union CAR regions");
		GriddedRegion griddedUnionRegion = new GriddedRegion(unionRegion, muertosSlab.getGriddedRegion().getSpacing(), GriddedRegion.ANCHOR_0_0);

		return GridSourceList.combine(griddedUnionRegion, carInterface, carSlab, muertosInterface, muertosSlab);
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
		IncrementalMagFreqDist totalGR = seisBranch.build(seisRegion, FaultSysTools.initEmptyMFD(OVERALL_MMIN, maxMagOff), maxMagOff);
		
		GriddedGeoDataSet pdf = seisSmooth.loadXYZ(seisRegion, declusteringAlg);
		
		List<List<GriddedRupture>> ruptureLists = new ArrayList<>(pdf.size());
		
		// TODO
		System.err.println("WARNING: USING ARBITRARILY SLAB FOCAL MECH");
		double rake = -90d;
		double dip = 50d;
		double strike = Double.NaN;
		
		double upper = 50d;
		System.err.println("WARNING: USING ARBITRARILY SLAB UPPER DEPTH");
		double maxLower = 65d;
		double maxDDW = (maxLower-upper)/Math.sin(Math.toRadians(dip));
		MagScalingRelationship scale = new WC1994_MagLengthRelationship();
		
		double hypocentralDepth = Double.NaN;
		double hypocentralDAS = Double.NaN;
		
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
				
				if (rate == 0d)
					continue;
				
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
				
				ruptureList.add(new GriddedRupture(gridIndex, loc, mag, rate, rake, dip, strike, null,
						upper, lower, length, hypocentralDepth, hypocentralDAS, TectonicRegionType.SUBDUCTION_SLAB, null, null));
			}
		}
		
		return new GridSourceList(pdf.getRegion(), TectonicRegionType.SUBDUCTION_SLAB, ruptureLists);
	}
	
	public static GridSourceList buildInterfaceGridSourceList(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch) throws IOException {
		GridSourceList muertos = buildInterfaceGridSourceList(sol, fullBranch, SeismicityRegions.MUE_INTERFACE);
		GridSourceList car = buildInterfaceGridSourceList(sol, fullBranch, SeismicityRegions.CAR_INTERFACE);
		return GridSourceList.combine(car, muertos);
	}
	
	public static GridSourceList buildInterfaceGridSourceList(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch,
			SeismicityRegions seisRegion) throws IOException {
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
		
		System.out.println("Building interface GridSourceList for "+seisRegion);
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		List<FaultSection> matchingSubSects = new ArrayList<>();
		List<RuptureSurface> matchingSubSectSurfaces = new ArrayList<>();
		List<Double> matchingSubSectMMins = new ArrayList<>();
		List<Region> matchingSubSectOutlines = new ArrayList<>();
		double maxSectMinMag = 0d;
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			int sectParentID = sect.getParentSectionId();
			if (Ints.contains(parentIDs, sectParentID)) {
				matchingSubSects.add(sect);
				RuptureSurface surf = sect.getFaultSurface(2d, false, false);
				matchingSubSectSurfaces.add(surf);
				matchingSubSectOutlines.add(new Region(surf.getPerimeter(), BorderType.MERCATOR_LINEAR));
				double sectMinMag = rupSet.getMinMagForSection(sect.getSectionId());
				System.out.println(sect.getSectionId()+". "+sect.getSectionName()+": Mmin="+(float)sectMinMag);
				matchingSubSectMMins.add(sectMinMag);
				maxSectMinMag = Math.max(maxSectMinMag, sectMinMag);
//			} else {
//				System.out.println("Skipping section (not a parentID match): "+sect.getSectionId()
//					+". "+sect.getSectionName()+" (parentID="+sectParentID+")");
			}
		}
		
		PRVI25_RegionalSeismicity seisBranch = fullBranch.requireValue(PRVI25_RegionalSeismicity.class);
		PRVI25_DeclusteringAlgorithms declusteringAlg = fullBranch.requireValue(PRVI25_DeclusteringAlgorithms.class);
		PRVI25_SeisSmoothingAlgorithms seisSmooth = fullBranch.requireValue(PRVI25_SeisSmoothingAlgorithms.class);
		
		IncrementalMagFreqDist refMFD = FaultSysTools.initEmptyMFD(OVERALL_MMIN, maxSectMinMag);
		System.out.println("MFD Gridding Mmmax="+(float)refMFD.getMaxX());
		
		// total G-R up to Mmax
//		IncrementalMagFreqDist totalGR = seisBranch.build(seisRegion, refMFD, maxMagOff);
		
		GriddedGeoDataSet pdf = seisSmooth.loadXYZ(seisRegion, declusteringAlg);
		
		List<List<GriddedRupture>> ruptureLists = new ArrayList<>(pdf.size());
		
		PRVI25_SubductionScalingRelationships scaleBranch = fullBranch.requireValue(PRVI25_SubductionScalingRelationships.class);
		MagAreaRelationship scale = scaleBranch.getMagAreaRelationship();
		
		double overallFurthestMatch = 0d;
		for (int gridIndex=0; gridIndex<pdf.size(); gridIndex++) {
			double fractRate = pdf.get(gridIndex);
			if (fractRate == 0d) {
				ruptureLists.add(new ArrayList<>());
				continue;
			}
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
				// find closest
				double closestDist = Double.POSITIVE_INFINITY;
				for (int i=0; i<matchingSubSects.size(); i++) {
					RuptureSurface surf = matchingSubSectSurfaces.get(i);
					double minDist = Double.POSITIVE_INFINITY;
					for (Location perimLoc : surf.getEvenlyDiscritizedPerimeter())
						minDist = Math.min(minDist, LocationUtils.horzDistanceFast(perimLoc, loc));
					if (minDist < closestDist) {
						closestDist = minDist;
						matchingSectIndex = i;
					}
				}
				overallFurthestMatch = Math.max(overallFurthestMatch, closestDist);
			}
			
			FaultSection matchingSection = matchingSubSects.get(matchingSectIndex);
			
			// find the depth at this location
			RuptureSurface sectSurf = matchingSubSectSurfaces.get(matchingSectIndex);
			double closestDist = Double.POSITIVE_INFINITY;
			double closestDepth = Double.NaN;
			for (Location surfLoc : sectSurf.getEvenlyDiscritizedListOfLocsOnSurface()) {
				double dist = LocationUtils.horzDistanceFast(surfLoc, loc);
				if (dist < closestDist) {
					closestDist = dist;
					closestDepth = surfLoc.depth;
				}
			}
			
			int[] assocIDs = { matchingSection.getSectionId() };
			double[] assocFracts = { 1d };
			
			double dip = matchingSection.getAveDip();
			double dipRad = Math.toRadians(dip);
			double rake = matchingSection.getAveRake();
			double strike = matchingSection.getFaultTrace().getAveStrike();
			
			double sectMmin = matchingSubSectMMins.get(matchingSectIndex);
			int sectMminIndex = refMFD.getClosestXIndex(sectMmin);
			Preconditions.checkState(sectMminIndex > 0);
			// set Mmax to one bin below the matching section Mmin
			double mMax = refMFD.getX(sectMminIndex-1);
			
			// modify to use our actual closest depth as these are average and not rectangular
			double sectUpper = Math.min(closestDepth, matchingSection.getOrigAveUpperDepth());
			double sectLower = Math.max(closestDepth, matchingSection.getAveLowerDepth());
			double sectDDW = (sectLower - sectUpper)/Math.sin(dipRad);
			
			IncrementalMagFreqDist mfd = seisBranch.build(seisRegion, refMFD, mMax);
			mfd.scale(fractRate);
			
			List<GriddedRupture> ruptureList = new ArrayList<>(sectMminIndex);
			ruptureLists.add(ruptureList);
			for (int i=0; i<mfd.size(); i++) {
				double mag = mfd.getX(i);
				double rate = mfd.getY(i);
				
				if (rate == 0d)
					continue;
				
				double area = scale.getMedianArea(mag);
				double sqRtArea = Math.sqrt(area);
				
				double hypocentralDAS = Double.NaN;
				double hypocentralDepth = closestDepth;
				Preconditions.checkState(Double.isFinite(closestDepth), "closestDepth=%s?", closestDepth);
				
				double length, ddw, upper, lower;
				if (sqRtArea <= sectDDW) {
					// make a square
					ddw = sqRtArea;
					length = sqRtArea;
					// figure out upper/lower, making sure they don't exceed sectUpper or sectLower
					
					double vertDDW = ddw*Math.sin(dipRad);
					Preconditions.checkState(Double.isFinite(vertDDW),
							"vertDDW=%s with ddw=%s, dip=%s, dipRad=%s", vertDDW, ddw, dip, dipRad);
					double calcUpper = hypocentralDepth - 0.5*vertDDW;
					if (calcUpper < sectUpper) {
						// snap upper edge
						upper = sectUpper;
						lower = upper + vertDDW;
					} else {
						double calcLower = hypocentralDepth + 0.5*vertDDW;
						if (calcLower > sectLower) {
							// snap to lower edge
							lower = sectLower;
							upper = sectLower - vertDDW;
						} else {
							// no issue
							upper = calcUpper;
							lower = calcLower;
						}
					}
				} else {
					// make a rectangle that matches sectDDW
					ddw = sectDDW;
					length = area/ddw;
					upper = sectUpper;
					lower = sectLower;
				}

				Preconditions.checkState(Double.isFinite(upper));
				Preconditions.checkState(Double.isFinite(lower), "lower=%s? sectLower=%s, ddw=%s", lower, sectLower, ddw);
				
				ruptureList.add(new GriddedRupture(gridIndex, loc, mag, rate, rake, dip, strike, null,
						upper, lower, length, hypocentralDepth, hypocentralDAS,
						TectonicRegionType.SUBDUCTION_INTERFACE, assocIDs, assocFracts));
			}
		}
		
		System.out.println("Done building gridded provider for "+seisRegion+"; worst grid-to-interface distance: "+(float)overallFurthestMatch+"km");
		
		return new GridSourceList(pdf.getRegion(), TectonicRegionType.SUBDUCTION_INTERFACE, ruptureLists);
	}

	public static void main(String[] args) throws IOException {
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
		levels.addAll(PRVI25_LogicTreeBranch.levelsSubduction);
		levels.addAll(PRVI25_LogicTreeBranch.levelsSubductionGridded);
		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels);
		
		branch.setValue(PRVI25_SubductionScalingRelationships.AVERAGE);
		branch.setValue(PRVI25_RegionalSeismicity.PREFFERRED);
		branch.setValue(PRVI25_SeisSmoothingAlgorithms.AVERAGE);
		branch.setValue(PRVI25_DeclusteringAlgorithms.AVERAGE);
		
//		buildSlabGridSourceList(branch);
		
//		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
//				+ "2024_06_03-prvi25_subduction_branches/results_PRVI_SUB_FM_LARGE_branch_averaged.zip"));
//		branch.setValue(PRVI25_SubductionFaultModels.PRVI_SUB_FM_LARGE);
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
				+ "2024_06_03-prvi25_subduction_branches/results_PRVI_SUB_FM_SMALL_branch_averaged.zip"));
		branch.setValue(PRVI25_SubductionFaultModels.PRVI_SUB_FM_SMALL);
		
//		buildInterfaceGridSourceList(sol, branch);
		
		GridSourceList gridSources = buildCombinedSubductionGridSourceList(sol, branch);
		
		sol.addModule(gridSources);
		String prefix = branch.requireValue(PRVI25_SubductionFaultModels.class).getFilePrefix()+"_with_gridded";
		File outputFile = new File("/tmp", prefix+".zip");
		sol.write(outputFile);
		
		System.setProperty("java.awt.headless", "true");
		
		ReportPageGen.main(new String[] {
				"--input-file", outputFile.getAbsolutePath(),
				"--name", "PRVI25 Subduction Gridded Test",
				"--plot-level", "review",
				"--output-dir", new File("/tmp/", prefix).getAbsolutePath(),
				"--replot"
		});
	}

}
