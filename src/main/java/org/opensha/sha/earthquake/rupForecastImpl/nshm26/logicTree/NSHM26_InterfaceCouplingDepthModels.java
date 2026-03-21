package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

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
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.collect.Range;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM26_InterfaceCouplingDepthModels implements LogicTreeNode {
	DEEP("Deep Taper", "Deep", Range.closed(0d, 10d), Range.closed(40d, 60d), 1d),
	DOUBLE_TAPER("Double Taper", "Double", Range.closed(10d, 20d), Range.closed(40d, 60d), 1d),
	NONE("None", "None", null, null, 1d);

	private String name;
	private String shortName;
	private Range<Double> upperTaper;
	private Range<Double> lowerTaper;
	private double weight;

	NSHM26_InterfaceCouplingDepthModels(String name, String shortName, Range<Double> upperTaper,
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
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return name();
	}
	
	public void apply(List<? extends FaultSection> subSects) {
		if (upperTaper == null && lowerTaper == null) {
			// shortcut
			for (FaultSection sect : subSects)
				sect.setCouplingCoeff(1d);
		} else {
			for (FaultSection sect : subSects) {
				RuptureSurface surf = sect.getFaultSurface(1d);
				LocationList surfLocs = surf.getEvenlyDiscritizedListOfLocsOnSurface();
				
				sect.setCouplingCoeff(getCoupling(surfLocs));
			}
		}
	}
	
	private double getCoupling(LocationList surfLocs) {
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
		NSHM26_InterfaceCouplingDepthModels[] models = values();
		System.out.print("Depth");
		for (NSHM26_InterfaceCouplingDepthModels model : models)
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
		
		NSHM26_SeismicityRegions seisReg = NSHM26_SeismicityRegions.GNMI;
		LogicTreeBranch<LogicTreeNode> branch = NSHM26_LogicTree.buildDefault(seisReg, TectonicRegionType.SUBDUCTION_INTERFACE, false);
		NSHM26_InterfaceDeformationModels dm = branch.requireValue(NSHM26_InterfaceDeformationModels.class);
		CPT couplingCPT = GMT_CPT_Files.SEQUENTIAL_LAJOLLA_UNIFORM.instance().rescale(0d, 1d);
		for (NSHM26_InterfaceCouplingDepthModels model : models) {
			branch.setValue(model);
			List<? extends FaultSection> subSects = dm.build(branch.requireValue(NSHM26_InterfaceFaultModels.class), branch);
			GeographicMapMaker mapMaker = new GeographicMapMaker(subSects);
			mapMaker.plotSectScalars(S->S.getCouplingCoeff(), couplingCPT, "Coupling Coefficient");
			mapMaker.plot(new File("/tmp"), seisReg.name()+"_coupling_"+model.name(), " ");
		}
	}

}
