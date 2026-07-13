package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.Interpolate;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_InterfaceCouplingDepthModels implements LogicTreeNode.FixedWeightNode {
	// beta tapers
	DEEP_TAPER("Deep Taper", "Deep", Range.closed(5d, 10d), Range.closed(40d, 60d), 0.4),
	DOUBLE_TAPER("Double Taper", "Double", Range.closed(10d, 15d), Range.closed(40d, 60d), 0.4),
	NONE("None", "None", null, null, 0.2),
	// alpha tapers
//	DEEP_TAPER("Deep Taper", "Deep", Range.closed(0d, 10d), Range.closed(40d, 60d), 1),
//	DOUBLE_TAPER("Double Taper", "Double", Range.closed(10d, 20d), Range.closed(40d, 60d), 1),
//	NONE("None", "None", null, null, 1),
	
	AVERAGE("Average", "Average", null, null, 0d);

	private String name;
	private String shortName;
	private Range<Double> upperTaper;
	private Range<Double> lowerTaper;
	private double weight;

	NSHM27_InterfaceCouplingDepthModels(String name, String shortName, Range<Double> upperTaper,
			Range<Double> lowerTaper, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.upperTaper = upperTaper;
		this.lowerTaper = lowerTaper;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getNodeWeight() {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return name();
	}
	
	public void apply(List<? extends FaultSection> subSects) {
		if (this == AVERAGE) {
			double sumWeights = 0d;
			for (NSHM27_InterfaceCouplingDepthModels model : values())
				sumWeights += model.weight;
			for (FaultSection sect : subSects) {
				RuptureSurface surf = sect.getFaultSurface(1d);
				LocationList surfLocs = surf.getEvenlyDiscritizedListOfLocsOnSurface();
				
				double coupling = 0d;
				for (NSHM27_InterfaceCouplingDepthModels model : values())
					coupling += model.getCoupling(surfLocs)*model.weight;
				sect.setCouplingCoeff(coupling/sumWeights);
			}
			return;
		}
		if (upperTaper == null && lowerTaper == null) {
			// shortcut
			for (FaultSection sect : subSects)
				sect.setCouplingCoeff(1d);
		} else {
//			double minDepth = Double.POSITIVE_INFINITY;
			for (FaultSection sect : subSects) {
				RuptureSurface surf = sect.getFaultSurface(1d);
				LocationList surfLocs = surf.getEvenlyDiscritizedListOfLocsOnSurface();
//				for (Location loc : surfLocs)
//					minDepth = Math.min(minDepth, loc.depth);
				
				sect.setCouplingCoeff(getCoupling(surfLocs));
			}
//			System.out.println("Minimum depth encountered: "+(float)minDepth);
		}
	}
	
	private double getCoupling(LocationList surfLocs) {
		if (this == AVERAGE) {
			double weightedSum = 0d;
			double sumWeight = 0;
			for (NSHM27_InterfaceCouplingDepthModels model : values()) {
				if (model != AVERAGE) {
					sumWeight += model.weight;
					weightedSum += model.weight*model.getCoupling(surfLocs);
				}
			}
			return weightedSum/sumWeight;
		}
		double coupling = 1d;
		if (upperTaper != null)
			coupling *= getTaperedCoupling(surfLocs, upperTaper, true);
		if (lowerTaper != null)
			coupling *= getTaperedCoupling(surfLocs, lowerTaper, false);
		return coupling;
	}
	
	private static double getTaperedCoupling(LocationList locs, Range<Double> taper, boolean top) {
		double topDepth = taper.lowerEndpoint();
		double bottomDepth = taper.upperEndpoint();
		double sum = 0d;
		boolean allOutside = true;
		for (Location loc : locs) {
			if (taper.contains(loc.depth)) {
				allOutside = false;
				sum += Interpolate.findY(topDepth, top ? 0d : 1d, bottomDepth, top ? 1d : 0d, loc.depth);
			} else if (top && loc.depth <= topDepth) {
				// fully above (shallower) than the start of the top taper
				allOutside = false;
			} else if (!top && loc.depth >= bottomDepth) {
				// fully below (deeper) than the end of the bottom taper
				allOutside = false;
			} else {
				sum++;
			}
		}
		if (allOutside)
			return 1d;
		return sum/(double)locs.size();
	}
	
	public static void main(String[] args) throws IOException {
		EvenlyDiscretizedFunc depths = new EvenlyDiscretizedFunc(0d, 70, 1d);
		NSHM27_InterfaceCouplingDepthModels[] models = values();
		System.out.print("Depth");
		for (NSHM27_InterfaceCouplingDepthModels model : models)
			System.out.print("\t"+model.getShortName());
		System.out.println();
		DecimalFormat cDF = new DecimalFormat("0.###");
		for (int d=0; d<depths.size(); d++) {
			double depth = depths.getX(d);
			LocationList testLocs = LocationList.of(new Location(0d, 0d, depth));
			System.out.print((int)depth);
			for (int m=0; m<models.length; m++)
				System.out.print("\t"+cDF.format(models[m].getCoupling(testLocs)));
			System.out.println();
		}
		
		NSHM27_SeismicityRegions seisReg = NSHM27_SeismicityRegions.GNMI;
//		NSHM27_SeismicityRegions seisReg = NSHM27_SeismicityRegions.AMSAM;
		LogicTreeBranch<LogicTreeNode> branch = NSHM27_LogicTree.buildDefault(seisReg, TectonicRegionType.SUBDUCTION_INTERFACE, false);
		NSHM27_InterfaceDeformationModels.Aggregated dm = branch.requireValue(NSHM27_InterfaceDeformationModels.Aggregated.class);
		CPT couplingCPT = GMT_CPT_Files.SEQUENTIAL_LAJOLLA_UNIFORM.instance().rescale(0d, 1d);
		CPT depthCPT = GMT_CPT_Files.SEQUENTIAL_NAVIA_UNIFORM.instance().reverse().rescale(0d, 60d);
		for (NSHM27_InterfaceCouplingDepthModels model : models) {
			branch.setValue(model);
			List<? extends FaultSection> subSects = dm.build(branch.requireValue(NSHM27_InterfaceFaultModels.class), branch);
			GeographicMapMaker mapMaker = new GeographicMapMaker(subSects);
			mapMaker.plotSectScalars(S->S.getCouplingCoeff(), couplingCPT, "Coupling Coefficient");
			mapMaker.plot(new File("/tmp"), seisReg.name()+"_coupling_"+model.name(), " ");
			if (model == AVERAGE) {
				mapMaker.plotSectScalars((S)->S.getFaultSurface(1d).getEvenlyDiscritizedListOfLocsOnSurface()
						.stream().mapToDouble(L->L.depth).average().getAsDouble(), depthCPT, "Subsection average depth (km)");
				mapMaker.plot(new File("/tmp"), seisReg.name()+"_coupling_depths", " ");
				double momentSum = 0d;
				for (FaultSection sect : subSects)
					momentSum += sect.calcMomentRate(true);
				System.out.println("\tAverage moment:\t"+(float)momentSum);
			}
		}
	}

}
