package org.opensha.sha.earthquake.rupForecastImpl.nshm26;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.data.xyzw.GriddedGeoDepthValueDataSet;
import org.opensha.commons.data.xyzw.GriddedGeoDepthValueDataSet.DepthRediscretizationMethod;
import org.opensha.commons.geo.CubedGriddedRegion;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.PointSource;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRuptureProperties;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_AbstractGridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_FaultCubeAssociations;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_SingleRegionGridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_SingleRegionGridSourceProvider.NSHM23_WUS_FiniteRuptureConverter;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_CrustalFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_DeclusteringAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_InterfaceFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_InterfaceMinSubSects;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_LogicTree;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_SeisRateModel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_SeisSmoothingAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.InterfaceGridAssociations;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_SeisPDF_Loader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.erf.ETAS.SeisDepthDistribution;

public class NSHM26_GridSourceBuilder {
	
	// for intraslab sources, assign these IDs as associated sections (even though there aren't sections with these IDs)
	// so that we can track them later and in nshmp-haz
	public static final int GNMI_SLAB_ASSOC_ID = 6300;
	public static final int AMSAM_SLAB_ASSOC_ID = 6800;
	
	public static final double OVERALL_MMIN = 2.55;

	private static EnumMap<NSHM26_SeismicityRegions, GriddedGeoDataSet> interfaceGridDepths;
	private static EnumMap<NSHM26_SeismicityRegions, GriddedGeoDataSet> interfaceGridStrikes;
	private static EnumMap<NSHM26_SeismicityRegions, GriddedGeoDataSet> interfaceGridDips;
	
	public static final double INTERFACE_MAX_HYOPCENTRAL_DEPTH = 60d;
	public static final double INTERFACE_MAX_GRID_FINITE_DEPTH = Double.POSITIVE_INFINITY;
	
	public static double[] SLAB_DEPTH_REDISCRETIZATION;
	
	public static final double SLAB_MIN_RATE = 1e-10; // approximately 1 in the age of the known universe
	
	public static boolean RATE_BALANCE_CRUSTAL_GRIDDED = true;
	
	// all FM v1 faults are normal
	public static final double CRUSTAL_FRACT_SS = 0.5d;
	public static final double CRUSTAL_FRACT_REV = 0d; // TODO
	public static final double CRUSTAL_FRACT_NORM = 0.5d;
	
	static {
		List<Double> depths = new ArrayList<>();
		for (int i=0; i<NSHM26_SeisPDF_Loader.DEPTH_DISCR_3D.size(); i++) {
			double x = NSHM26_SeisPDF_Loader.DEPTH_DISCR_3D.getX(i);
			if (x <= 100.01) {
				depths.add(x);
			} else if (x <= 200.01) {
				if (Math.abs(x % 20) < 0.1)
					depths.add(x);
//			} else if (x <= 00.01) {
//				if (Math.abs(x % 20) < 0.1)
//					depths.add(x);
			} else {
				if (Math.abs(x % 50) < 0.1)
					depths.add(x);
			}
		}
		SLAB_DEPTH_REDISCRETIZATION = Doubles.toArray(depths);
	}
	
	public static void doPreGridBuildHook(FaultSystemSolution sol, LogicTreeBranch<?> faultBranch) throws IOException {
		if (faultBranch.hasValue(NSHM26_CrustalFaultModels.class)) {
			// add fault cube associations and seismicity regions
			FaultSystemRupSet rupSet = sol.getRupSet();
			
			NSHM26_SeismicityRegions seisReg = faultBranch.requireValue(NSHM26_CrustalFaultModels.class).getSeisReg();
			if (!rupSet.hasModule(ModelRegion.class))
				rupSet.addModule(new ModelRegion(seisReg.load()));
			GriddedRegion modelGrid = initGridReg(seisReg);
			
			FaultCubeAssociations cubeAssociations = rupSet.getModule(FaultCubeAssociations.class);
			if (cubeAssociations == null) {
				cubeAssociations = new NSHM23_FaultCubeAssociations(rupSet, new CubedGriddedRegion(modelGrid),
						NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST);
				rupSet.addModule(cubeAssociations);
			} else {
				Preconditions.checkState(cubeAssociations.getRegion().equals(modelGrid));
			}
			Preconditions.checkNotNull(cubeAssociations, "Cube associations is null");
		} else if (faultBranch.hasValue(NSHM26_InterfaceFaultModels.class)) {
			FaultSystemRupSet rupSet = sol.getRupSet();
			
			NSHM26_SeismicityRegions seisReg = faultBranch.requireValue(NSHM26_InterfaceFaultModels.class).getSeisReg();
			if (!rupSet.hasModule(ModelRegion.class))
				rupSet.addModule(new ModelRegion(seisReg.load()));
			GriddedRegion modelGrid = initGridReg(seisReg);
			
			FaultGridAssociations assoc = rupSet.getModule(FaultGridAssociations.class);
			if (assoc == null) {
				assoc = new InterfaceGridAssociations(rupSet.getFaultSectionDataList(), modelGrid);
				rupSet.addModule(assoc);
			} else {
				Preconditions.checkState(assoc.getRegion().equals(modelGrid));
			}
		}
	}
	
	public static double[] getInterfaceSectMinMag(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		int numSects = rupSet.getNumSections();
		double[] ret = new double[numSects];
		int minNumSects = 1;
		if (branch.hasValue(NSHM26_InterfaceMinSubSects.class))
			minNumSects = branch.requireValue(NSHM26_InterfaceMinSubSects.class).getValue();
		for (int s=0; s<numSects; s++) {
			if (minNumSects == 1) {
				ret[s] = rupSet.getMinMagForSection(s);
			} else {
				ret[s] = Double.POSITIVE_INFINITY;;
				for (int r : rupSet.getRupturesForSection(s)) {
					if (rupSet.getSectionsIndicesForRup(r).size() >= minNumSects) {
						ret[s] = Math.min(ret[s], rupSet.getMagForRup(r));
					}
				}
			}
		}
		return ret;
	}
	
	public static GriddedRegion initGridReg(NSHM26_SeismicityRegions seisReg) {
		try {
			return new GriddedRegion(seisReg.load(), 0.1, GriddedRegion.ANCHOR_0_0);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public static GriddedGeoDataSet loadInterfaceDepths(NSHM26_SeismicityRegions seisReg) throws IOException {
		loadInterfaceDepthStrikeData(seisReg);
		return interfaceGridDepths.get(seisReg).copy();
	}
	
	public static GriddedGeoDataSet loadInterfaceStrikes(NSHM26_SeismicityRegions seisReg) throws IOException {
		loadInterfaceDepthStrikeData(seisReg);
		return interfaceGridStrikes.get(seisReg).copy();
	}
	
	public static GriddedGeoDataSet loadInterfaceDips(NSHM26_SeismicityRegions seisReg) throws IOException {
		loadInterfaceDepthStrikeData(seisReg);
		return interfaceGridDips.get(seisReg).copy();
	}
	
	private synchronized static void loadInterfaceDepthStrikeData(NSHM26_SeismicityRegions seisReg) throws IOException {
		if (interfaceGridDepths == null) {
			interfaceGridDepths = new EnumMap<>(NSHM26_SeismicityRegions.class);
			interfaceGridStrikes = new EnumMap<>(NSHM26_SeismicityRegions.class);
			interfaceGridDips = new EnumMap<>(NSHM26_SeismicityRegions.class);
		}
		
		if (!interfaceGridDepths.containsKey(seisReg)) {
			String prefix = switch (seisReg) {
			case AMSAM: {
				yield "ker_slab2_";
			}
			case GNMI: {
				yield "izu_slab2_";
			}
			default:
				throw new IllegalArgumentException("Unexpected value: " + seisReg);
			};
			String suffix = "_02.24.18.xyz";
			GriddedGeoDataSet[] xyzs = new GriddedGeoDataSet[3];
			String[] names = {
					prefix+"dep"+suffix,
					prefix+"str"+suffix,
					prefix+"dip"+suffix
			};
			
			GriddedRegion gridReg = initGridReg(seisReg);
			
			File dir = new File(NSHM26_InvConfigFactory.locateDataDirectory(), "slab2");
			
			for (int i=0; i<xyzs.length; i++) {
				GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg);
				// initialize to NaN
				xyz.scale(Double.NaN);
				File file = new File(dir, names[i]);
				Preconditions.checkState(file.exists(), "File doesn't exist: %s", file.getAbsolutePath());
				
				CSVFile<Double> csv = CSVFile.readFileNumeric(file, true, 0);
				int numSkipped = 0;
				for (int row=0; row<csv.getNumRows(); row++) {
					double lon = csv.get(row, 0);
					if (lon < 0)
						lon += 360d;
					double lat = csv.get(row, 1);
					double val = csv.get(row, 2);
					if (Double.isNaN(val)) {
						numSkipped++;
						continue;
					}
					Location loc = new Location(lat, lon);
					int index = gridReg.indexForLocation(loc);
					if (index < 0) {
						numSkipped++;
						continue;
					}
					if (LocationUtils.areSimilar(loc, xyz.getLocation(index)))
						// perfect match, always keep
						xyz.set(index, val);
					else if (!Double.isNaN(val))
						// offset grid, only fill in if not matched previously
						xyz.set(index, val);
					else
						numSkipped++;
				}
				xyzs[i] = xyz;
				
				int numFilled = 0;
				for (int j=0; j<xyz.size(); j++)
					if (!Double.isNaN(xyz.get(j)))
						numFilled++;
				
				System.out.println("\tFilled in data for "+numFilled+"/"+xyz.size()+" Slab2 grid nodes (skipped "+numSkipped+" from file)");
			}
			
			interfaceGridDips.put(seisReg, xyzs[2]);
			interfaceGridStrikes.put(seisReg, xyzs[1]);
			// the Slab2 files give elevation
			xyzs[0].scale(-1);
			interfaceGridDepths.put(seisReg, xyzs[0]);
		}
	}
	
	private static void plotInterfaceDepthStrikeData(NSHM26_SeismicityRegions seisReg, File outputDir) throws IOException {
		GeographicMapMaker mapMaker = new GeographicMapMaker(seisReg.load());
		mapMaker.setFaultSections(NSHM26_InterfaceFaultModels.regionDefault(seisReg).getFaultSections());
		
		loadInterfaceDepthStrikeData(seisReg);
		
		Color trans = new Color(255, 255, 255, 0);
		
		CPT depthCPT = GMT_CPT_Files.SEQUENTIAL_LAJOLLA_UNIFORM.instance().reverse().rescale(0d, 100d);
		depthCPT.setNanColor(trans);
		
		CPT dipCPT = GMT_CPT_Files.SEQUENTIAL_LAJOLLA_UNIFORM.instance().reverse().rescale(0d, 90d);
		dipCPT.setNanColor(trans);
		
		CPT strikeCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().reverse().rescale(0d, 360d);
		strikeCPT.setNanColor(trans);
		
		String prefix = seisReg.name()+"_interface_slab2";
		
		mapMaker.plotXYZData(loadInterfaceDepths(seisReg), depthCPT, "Interface Slab2 depth (km)");
		mapMaker.plot(outputDir, prefix+"_depths", " ");
		
		mapMaker.plotXYZData(loadInterfaceDips(seisReg), dipCPT, "Interface Slab2 dip (degrees)");
		mapMaker.plot(outputDir, prefix+"_dips", " ");
		
		mapMaker.plotXYZData(loadInterfaceStrikes(seisReg), strikeCPT, "Interface Slab2 strike (degrees)");
		mapMaker.plot(outputDir, prefix+"_strikes", " ");
	}
	
	public static GridSourceList buildInterfaceGridSourceList(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch,
			NSHM26_SeismicityRegions seisRegion) throws IOException {
		MagAreaRelationship scale = fullBranch.requireValue(PRVI25_SubductionScalingRelationships.class).getMagAreaRelationship();
		
//		final boolean D = false;
		
		System.out.println("Building interface GridSourceList for "+seisRegion);
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		double[] sectMinMags = getInterfaceSectMinMag(rupSet, fullBranch);
		double avgMinMag = StatUtils.mean(sectMinMags);
		
		NSHM26_SeisRateModel rateBranch = fullBranch.requireValue(NSHM26_SeisRateModel.class);
		NSHM26_DeclusteringAlgorithms decluster = fullBranch.requireValue(NSHM26_DeclusteringAlgorithms.class);
		NSHM26_SeisSmoothingAlgorithms smooth = fullBranch.requireValue(NSHM26_SeisSmoothingAlgorithms.class);
		
		IncrementalMagFreqDist refMFD = FaultSysTools.initEmptyMFD(OVERALL_MMIN, StatUtils.max(sectMinMags)+0.1);
		Map<Double, IncrementalMagFreqDist> mMaxMFDCache = new HashMap<>();
		Function<Double, IncrementalMagFreqDist> mfdBuilderFunc = mMax-> {
			return rateBranch.build(seisRegion, TectonicRegionType.SUBDUCTION_INTERFACE, refMFD, mMax);
		};
		
		GriddedGeoDataSet depths = loadInterfaceDepths(seisRegion);
		GriddedGeoDataSet dips = loadInterfaceDips(seisRegion);
		GriddedGeoDataSet strikes = loadInterfaceStrikes(seisRegion);
		double rake = 90d;
		
		GriddedGeoDataSet pdf = NSHM26_SeisPDF_Loader.load2D(seisRegion, TectonicRegionType.SUBDUCTION_INTERFACE, decluster, smooth);
		GriddedGeoDataSet clippedPDF = new GriddedGeoDataSet(pdf.getRegion());
		int skipped = 0;
		double skippedWeight = 0d;
		double overallMinDepth = Double.POSITIVE_INFINITY;
		for (int i=0; i<pdf.size(); i++) {
			double val = pdf.get(i);
			double depth = depths.get(i);
			if (Double.isFinite(depth))
				overallMinDepth = Math.min(overallMinDepth, depth);
			if (val > 0) {
				if (!Double.isFinite(depth) || depth > INTERFACE_MAX_HYOPCENTRAL_DEPTH
						|| !Double.isFinite(dips.get(i)) || !Double.isFinite(strikes.get(i))) {
					skippedWeight += val;
					skipped++;
					continue;
				}
				clippedPDF.set(i, val);
			}
		}
		if (skipped > 0) {
			System.err.println("WARNING: clipped out "+skipped+" PDF locations with "+(float)skippedWeight+" for "+fullBranch);
			clippedPDF.scale(1d/clippedPDF.size());
		}
		
		FaultGridAssociations assoc = rupSet.requireModule(FaultGridAssociations.class);
		Preconditions.checkState(assoc.getRegion().equals(clippedPDF.getRegion()));
		
		List<List<GriddedRupture>> ruptureLists = new ArrayList<>(clippedPDF.size());
		int rupCount = 0;
		for (int n=0; n<clippedPDF.size(); n++) {
			if (clippedPDF.get(n) == 0d) {
				ruptureLists.add(null);
				continue;
			}
//			FocalMechanism mech = new FocalMechanism(strikes.get(n), dips.get(n), 90d)
			double strike = strikes.get(n);
			double dip = dips.get(n);
			double dipRad = Math.toRadians(dip);
			double depth = depths.get(n);
			Location loc = clippedPDF.getLocation(n);
			
			Map<Integer, Double> sectFracts = assoc.getSectionFracsOnNode(n);
			
			int[] assocIDs = null;
			double[] assocFracts = null;
			double mMax;
			if (sectFracts == null || sectFracts.isEmpty()) {
				mMax = refMFD.getX(refMFD.getClosestXIndex(avgMinMag-0.1));
			} else {
				double avgSectMmin = 0d;
				double sumWeight = 0d;
				List<Integer> ids = new ArrayList<>(sectFracts.keySet());
				assocIDs = new int[ids.size()];
				assocFracts = new double[ids.size()];
				for (int i=0; i<ids.size(); i++) {
					int sectIndex = ids.get(i);
					assocIDs[i] = sectIndex;
					assocFracts[i] = sectFracts.get(sectIndex);
					avgSectMmin += sectMinMags[sectIndex]*assocFracts[i];
					sumWeight += assocFracts[i];
				}
				avgSectMmin /= sumWeight;
				mMax = refMFD.getX(refMFD.getClosestXIndex(avgSectMmin-0.1));
			}
			IncrementalMagFreqDist mfd = mMaxMFDCache.get(mMax);
			if (mfd == null) {
				mfd = mfdBuilderFunc.apply(mMax);
				mMaxMFDCache.put(mMax, mfd);
			}
			mfd = mfd.deepClone();
			mfd.scale(clippedPDF.get(n));
			
			List<GriddedRupture> ruptureList = new ArrayList<>(refMFD.getClosestXIndex(mMax)+1);
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
				
				// make a square
				double ddw = sqRtArea;
				double length = sqRtArea;
				// figure out upper/lower, making sure they don't exceed sectUpper or sectLower
				
				double vertDDW = ddw*Math.sin(dipRad);
				Preconditions.checkState(Double.isFinite(vertDDW),
						"vertDDW=%s with ddw=%s, dip=%s, dipRad=%s", vertDDW, ddw, dip, dipRad);
				double calcUpper = hypocentralDepth - 0.5*vertDDW;
				double upper, lower;
				if (calcUpper < overallMinDepth) {
					// snap upper edge
					upper = overallMinDepth;
					lower = upper + vertDDW;
				} else {
					// uncomment if we want t a depth lower limit
					double calcLower = hypocentralDepth + 0.5*vertDDW;
					if (calcLower > INTERFACE_MAX_GRID_FINITE_DEPTH) {
						// snap to lower edge
						lower = INTERFACE_MAX_GRID_FINITE_DEPTH;
						upper = INTERFACE_MAX_GRID_FINITE_DEPTH - vertDDW;
					} else {
						// no issue
						upper = calcUpper;
						lower = calcLower;
					}
				}
				
//				if (DD) {
//					System.out.println("\tM"+(float)mag+" with depth="+(float)hypocentralDepth+", range=["+(float)upper+", "+(float)lower+"]");
//				}

				Preconditions.checkState(Double.isFinite(upper));
				Preconditions.checkState(Double.isFinite(lower), "lower=%s? depthLowerLimit=%s, ddw=%s", lower, INTERFACE_MAX_GRID_FINITE_DEPTH, ddw);
				GriddedRuptureProperties props = new GriddedRuptureProperties(
						mag, rake, dip, strike, null,
						upper, lower, length, hypocentralDepth, hypocentralDAS,
						TectonicRegionType.SUBDUCTION_INTERFACE);
				ruptureList.add(new GriddedRupture(n, loc, props, rate, assocIDs, assocFracts));
				rupCount++;
			}
		}
		
		System.out.println("Built "+rupCount+" ruptures");
		
		return new GridSourceList.Precomputed(pdf.getRegion(), TectonicRegionType.SUBDUCTION_INTERFACE, ruptureLists);
	}
	
	public static GridSourceList buildIntraslabGridSourceList(LogicTreeBranch<?> fullBranch, NSHM26_SeismicityRegions seisRegion) throws IOException {
		NSHM26_SeisRateModel rateBranch = fullBranch.requireValue(NSHM26_SeisRateModel.class);
		NSHM26_DeclusteringAlgorithms decluster = fullBranch.requireValue(NSHM26_DeclusteringAlgorithms.class);
		NSHM26_SeisSmoothingAlgorithms smooth = fullBranch.requireValue(NSHM26_SeisSmoothingAlgorithms.class);
		
		double mMax = fullBranch.requireValue(MaxMagOffFaultBranchNode.class).getMaxMagOffFault();
		
		IncrementalMagFreqDist refMFD = FaultSysTools.initEmptyMFD(OVERALL_MMIN, mMax+0.1);
		
		IncrementalMagFreqDist totalGR = rateBranch.build(seisRegion, TectonicRegionType.SUBDUCTION_INTERFACE,
				refMFD, refMFD.getX(refMFD.getClosestXIndex(mMax-0.001)));
		double totalRate = totalGR.calcSumOfY_Vals();
		
		// this is what nshm23 did for NSHM23 Cascadia and PRVI25
		// slab GMMs don't even use these according to Peter
		double rake = 0d;
		double dip = 90d;
		double strike = Double.NaN;
		
		GriddedGeoDepthValueDataSet pdf = NSHM26_SeisPDF_Loader.load3D(
				seisRegion, TectonicRegionType.SUBDUCTION_SLAB, decluster, smooth);
		Preconditions.checkState((float)pdf.getSumValues() == 1f);
		if (SLAB_DEPTH_REDISCRETIZATION != null) {
			StringBuilder depthStr = null;
			for (double depth : SLAB_DEPTH_REDISCRETIZATION) {
				if (depthStr == null)
					depthStr = new StringBuilder();
				else
					depthStr.append(",");
				depthStr.append((float)depth);
			}
			System.out.println("rediscretizing to depths: "+depthStr);
			int origNum = pdf.size();
			pdf = pdf.rediscretizeDepths(SLAB_DEPTH_REDISCRETIZATION, DepthRediscretizationMethod.PRESERVE_SUM);
			System.out.println("Reduced from "+origNum+" to "+pdf.size()+" locations");
			Preconditions.checkState((float)pdf.getSumValues() == 1f);
		}
		if (SLAB_MIN_RATE > 0) {
			double fractFilteredOut = 0d;
			int numFilteredOut = 0;
			int size = pdf.size();
			for (int i=0; i<size; i++) {
				double fract = pdf.get(i);
				if (fract > 0d) {
					double rate = fract*totalRate;
					if (rate < SLAB_MIN_RATE) {
						pdf.set(i, 0d);
						numFilteredOut++;
						fractFilteredOut += fract;
					}
				}
			}
			if (numFilteredOut > 0) {
				System.out.println("Filtered out "+numFilteredOut+"/"+size+" locations (fract="+fractFilteredOut+") with rates < "+SLAB_MIN_RATE);
				pdf.scale(1d/pdf.getSumValues());
			}
		}
		
		GriddedRegion gridReg = pdf.getRegion();
		
		List<List<GriddedRupture>> ruptureLists = new ArrayList<>(gridReg.getNodeCount());
		
		int assocID = switch (seisRegion) {
		case AMSAM:
			yield AMSAM_SLAB_ASSOC_ID;
		case GNMI:
			yield GNMI_SLAB_ASSOC_ID;
		default:
			throw new IllegalArgumentException("Unexpected value: " + seisRegion);
		};
		final int[] assocIDarray = {assocID};
		final double[] assocFractArray = {1d};
		
		int rupCount = 0;
		for (int gridIndex=0; gridIndex<gridReg.getNodeCount(); gridIndex++) {
			double nodeFract = pdf.get(gridIndex);
			if (nodeFract == 0d) {
				ruptureLists.add(null);
				continue;
			}
			
			List<GriddedRupture> ruptureList = new ArrayList<>();
			ruptureLists.add(ruptureList);
			
			Location gridLoc = gridReg.getLocation(gridIndex);
			
			for (int d=0; d<pdf.getDepthCount(); d++) {
				double fract = pdf.get(gridIndex, d);
				if (fract > 0d) {
					double depth = pdf.getDepth(d);
					for (int i=0; i<totalGR.size(); i++) {
						double mag = totalGR.getX(i);
						double rate = totalGR.getY(i)*fract;
						
						if (rate == 0d || (float)mag < (float)OVERALL_MMIN)
							continue;
						
						double length = 0d; // true point source
						
						GriddedRuptureProperties props = new GriddedRuptureProperties(mag, rake, dip, strike, null,
								depth, depth, length, Double.NaN, Double.NaN, TectonicRegionType.SUBDUCTION_SLAB);
						ruptureList.add(new GriddedRupture(gridIndex, gridLoc, props, rate, assocIDarray, assocFractArray));
						rupCount++;
					}
				}
			}
		}
		
		System.out.println("Built "+rupCount+" ruptures");
		
		return new GridSourceList.Precomputed(pdf.getRegion(), TectonicRegionType.SUBDUCTION_SLAB, ruptureLists);
	}
	
	public static GridSourceList buildCrustalGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch,
			NSHM26_SeismicityRegions seisRegion) throws IOException {
		NSHM26_SeisRateModel rateBranch = fullBranch.requireValue(NSHM26_SeisRateModel.class);
		NSHM26_DeclusteringAlgorithms decluster = fullBranch.requireValue(NSHM26_DeclusteringAlgorithms.class);
		NSHM26_SeisSmoothingAlgorithms smooth = fullBranch.requireValue(NSHM26_SeisSmoothingAlgorithms.class);
		
		double mMax = fullBranch.requireValue(MaxMagOffFaultBranchNode.class).getMaxMagOffFault();
		
		IncrementalMagFreqDist refMFD = FaultSysTools.initEmptyMFD(OVERALL_MMIN, mMax+0.1);
		
		IncrementalMagFreqDist totalGR = rateBranch.build(seisRegion, TectonicRegionType.ACTIVE_SHALLOW,
				refMFD, refMFD.getX(refMFD.getClosestXIndex(mMax-0.001)));
		
		GriddedGeoDataSet pdf = NSHM26_SeisPDF_Loader.load2D(seisRegion, TectonicRegionType.ACTIVE_SHALLOW, decluster, smooth);
		
		return buildCrustalGridSourceProv(seisRegion, sol, totalGR, pdf);
	}
	
	public static GridSourceList buildCrustalGridSourceProv(NSHM26_SeismicityRegions seisReg, FaultSystemSolution sol,
			IncrementalMagFreqDist totalGR, GriddedGeoDataSet pdf)  throws IOException {
		
		GriddedRegion gridReg = pdf.getRegion();

		// focal mechanisms
//		System.err.println("WARNING: DON'T YET HAVE FOCAL MECHS!");
		double[] fractStrikeSlip = new double[gridReg.getNodeCount()];
		double[] fractReverse = new double[gridReg.getNodeCount()];
		double[] fractNormal = new double[gridReg.getNodeCount()];
//		checkCalcCrustalFaultCategories();
		double[] pdfVals = new double[pdf.size()];
		for (int i=0; i<fractStrikeSlip.length; i++) {
			// these moment fractions from the crustal DM
			fractStrikeSlip[i] = CRUSTAL_FRACT_SS;
			fractReverse[i] = CRUSTAL_FRACT_REV;
			fractNormal[i] = CRUSTAL_FRACT_NORM;
			pdfVals[i] = pdf.get(i);
		}
		
		FaultGridAssociations assoc;
		MFDGridSourceProvider mfdGridProv;
		if (sol == null) {
			mfdGridProv = new NoFaultMFDGridProv(gridReg, TectonicRegionType.ACTIVE_SHALLOW, totalGR, pdfVals, fractStrikeSlip, fractReverse, fractNormal);
			assoc = null;
		} else {
			FaultCubeAssociations cubeAssociations = sol.getRupSet().requireModule(FaultCubeAssociations.class);
			Preconditions.checkState(gridReg.equals(cubeAssociations.getRegion()));
			assoc = cubeAssociations;
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
			mfdGridProv = new NSHM23_SingleRegionGridSourceProvider(sol, cubeAssociations, pdfVals, totalGridded, preserveTotalMFD, binnedDepthDistFunc,
					fractStrikeSlip, fractNormal, fractReverse, null); // last null means all active
		}
		
		return GridSourceList.convert(mfdGridProv, assoc, new NSHM23_WUS_FiniteRuptureConverter());
	}
	
	private static class NoFaultMFDGridProv extends NSHM23_AbstractGridSourceProvider.Abstract {
		
		private GriddedRegion gridReg;
		private TectonicRegionType trt;
		private IncrementalMagFreqDist totalGR;
		private double[] pdf;
		private double[] fractStrikeSlip;
		private double[] fractReverse;
		private double[] fractNormal;

		public NoFaultMFDGridProv(GriddedRegion gridReg, TectonicRegionType trt, IncrementalMagFreqDist totalGR,
				double[] pdf, double[] fractStrikeSlip, double[] fractReverse, double[] fractNormal) {
			this.gridReg = gridReg;
			this.trt = trt;
			this.totalGR = totalGR;
			this.pdf = pdf;
			this.fractStrikeSlip = fractStrikeSlip;
			this.fractReverse = fractReverse;
			this.fractNormal = fractNormal;
		}

		@Override
		public TectonicRegionType getTectonicRegionType(int gridIndex) {
			return trt;
		}

		@Override
		public MFDGridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse, TectonicRegionType[] trts) {
			throw new IllegalStateException();
		}

		@Override
		public IncrementalMagFreqDist getMFD_Unassociated(int gridIndex) {
			IncrementalMagFreqDist mfd = this.totalGR.deepClone();
			mfd.scale(pdf[gridIndex]);
			return mfd;
		}

		@Override
		public IncrementalMagFreqDist getMFD_SubSeisOnFault(int gridIndex) {
			return null;
		}

		@Override
		public GriddedRegion getGriddedRegion() {
			return gridReg;
		}

		@Override
		public double getFracStrikeSlip(int gridIndex) {
			return fractStrikeSlip[gridIndex];
		}

		@Override
		public double getFracReverse(int gridIndex) {
			return fractReverse[gridIndex];
		}

		@Override
		public double getFracNormal(int gridIndex) {
			return fractNormal[gridIndex];
		}

		@Override
		public void scaleAll(double[] valuesArray) {
			throw new IllegalStateException();
		}

		@Override
		public String getName() {
			return "No-Faults MFD";
		}

		@Override
		protected PointSource buildSource(int gridIndex, IncrementalMagFreqDist mfd, double duration,
				GriddedSeismicitySettings gridSourceSettings) {
			throw new IllegalStateException();
		}
		
	}
	
	public static GridSourceList buildCombinedGridSourceProv(GridSourceList interfaceProv,
			GridSourceList intraslabProv, GridSourceList crustalProv) {
		Preconditions.checkState(interfaceProv.getGriddedRegion().equals(intraslabProv.getGriddedRegion()));
		Preconditions.checkState(interfaceProv.getGriddedRegion().equals(crustalProv.getGriddedRegion()));
		
		return GridSourceList.combine(interfaceProv, intraslabProv, crustalProv);
	}
	
	public static void main(String[] args) throws IOException {
//		plotInterfaceDepthStrikeData(NSHM26_SeismicityRegions.AMSAM, new File("/tmp"));
//		plotInterfaceDepthStrikeData(NSHM26_SeismicityRegions.GNMI, new File("/tmp"));
		
		File invDir = new File("/home/kevin/markdown/inversions/");
		
//		FaultSystemSolution interfaceSol = FaultSystemSolution.load(new File(invDir,
//				"2026_03_09-nshm26-gnmi-MARIANA_PREF_COUPLING_LogA_C4p0_B1.0/solution.zip"));
//		NSHM26_SeismicityRegions seisReg = NSHM26_SeismicityRegions.GNMI;
//		FaultSystemSolution crustalSol = FaultSystemSolution.load(new File(invDir,
//				"2026_03_18-nshm26-gnmi-AVERAGE_LogA_C4p2_Middle_Average_AVERAGE_AVERAGE/solution.zip"));
		
		FaultSystemSolution interfaceSol = FaultSystemSolution.load(new File(invDir,
				"2026_03_09-nshm26-amsam-TONGA_PREF_COUPLING_LogA_C4p0_B1.0/solution.zip"));
		NSHM26_SeismicityRegions seisReg = NSHM26_SeismicityRegions.AMSAM;
		FaultSystemSolution crustalSol = null;
		
		LogicTreeBranch<LogicTreeNode> branch = NSHM26_LogicTree.buildDefault(seisReg, TectonicRegionType.SUBDUCTION_INTERFACE, false);
		doPreGridBuildHook(interfaceSol, branch);
		NSHM26_InterfaceFaultModels fm = branch.requireValue(NSHM26_InterfaceFaultModels.class);
		GridSourceList interfaceProv = buildInterfaceGridSourceList(interfaceSol, branch, seisReg);
		
		branch = NSHM26_LogicTree.buildDefault(seisReg, TectonicRegionType.SUBDUCTION_SLAB, false);
		GridSourceList intraslabProv = buildIntraslabGridSourceList(branch, seisReg);
		
		branch = NSHM26_LogicTree.buildDefault(seisReg, TectonicRegionType.ACTIVE_SHALLOW, false);
		if (crustalSol != null)
			doPreGridBuildHook(crustalSol, branch);
		GridSourceList crustalProv = buildCrustalGridSourceProv(crustalSol, branch, seisReg);
		
		GridSourceList combProv = buildCombinedGridSourceProv(interfaceProv, intraslabProv, crustalProv);
		interfaceSol.setGridSourceProvider(combProv);
		
		fm.attachDefaultModules(interfaceSol.getRupSet());
		interfaceSol.write(new File("/tmp/"+seisReg.name()+"_test_gridded_sol.zip"));
	}
}
