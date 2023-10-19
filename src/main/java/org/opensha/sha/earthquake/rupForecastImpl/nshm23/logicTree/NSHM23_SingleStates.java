package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ConnectivityClusters;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ConnectivityCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.AnalysisRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_SingleStates implements LogicTreeNode {
	CA("California") {
		@Override
		public Region loadRegion() throws IOException {
			return AnalysisRegions.CONUS_U3_RELM.load();
		}
	},
	NV("Nevada"),
	OR("Oregon"),
	WA("Washington"),
	WY("Wyoming"),
	ID ("Idaho"),
	MT("Montana"),
	UT("Utah"),
	CO("Colorado"),
	NM("New Mexico"),
	AZ("Arizona"),
	TX("Texas");
	
	public static boolean INCLUDE_SECONDARY = true;
	
	private String stateName;

	private NSHM23_SingleStates(String stateName) {
		this.stateName = stateName;
	}
	
	public String getStateName() {
		return stateName;
	}
	
	@Override
	public String getShortName() {
		return "Only"+name();
	}

	@Override
	public String getName() {
		return stateName+" Only";
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return 0d;
	}

	@Override
	public String getFilePrefix() {
		return name()+"_only";
	}
	
	private List<Integer> getStateSectionIDs(List<? extends FaultSection> sects) {
		List<Integer> ids = new ArrayList<>();
		
		for (FaultSection sect : sects) {
			Preconditions.checkState(sect instanceof GeoJSONFaultSection);
			GeoJSONFaultSection geoSect = (GeoJSONFaultSection)sect;
			if (contains(geoSect))
				ids.add(sect.getSectionId());
		}
		Preconditions.checkState(!ids.isEmpty(), "None of the %s sections match state: %s", sects.size(), name());
		
		return ids;
	}
	
	public boolean contains(GeoJSONFaultSection geoSect) {
		String primary = geoSect.getProperty("PrimState", null);
		String secState = geoSect.getProperty("SecState", null);
		String myName = name();
		return myName.equals(primary) || (INCLUDE_SECONDARY && myName.equals(secState));
	}
	
	public FaultSystemRupSet getRuptureSubSet(FaultSystemRupSet rupSet) {
		ConnectivityClusters clusters = rupSet.getModule(ConnectivityClusters.class);
		if (clusters == null)
			clusters = ConnectivityClusters.build(rupSet);
		List<Integer> stateIDs = getStateSectionIDs(rupSet.getFaultSectionDataList());
		HashSet<Integer> allClusterSectIDs = new HashSet<>(stateIDs);
		for (ConnectivityCluster cluster : clusters) {
			boolean contains = false;
			for (int sectID: stateIDs) {
				if (cluster.containsSect(sectID)) {
					contains = true;
					break;
				}
			}
			if (contains)
				allClusterSectIDs.addAll(cluster.getSectIDs());
		}
		return rupSet.getForSectionSubSet(allClusterSectIDs);
	}
	
	public Region loadRegion() throws IOException {
		XY_DataSet[] outlines = PoliticalBoundariesData.loadUSState(getStateName());
		Region[] regions = new Region[outlines.length];
		for (int i=0; i<outlines.length; i++) {
			XY_DataSet outline = outlines[i];
			LocationList border = new LocationList();
			for (Point2D pt : outline)
				border.add(new Location(pt.getY(), pt.getX()));
			regions[i] = new Region(border, BorderType.MERCATOR_LINEAR);
		}
		Region largest = null;
		if (outlines.length == 1) {
			largest = regions[0];
		} else {
			double largestArea = 0;
			for (Region region : regions) {
				double area = region.getExtent();
				if (area > largestArea) {
					largestArea = area;
					largest = region;
				}
			}
		}
		return largest;
	}
	
	public static void main(String[] args) throws IOException {
		// write out subsections for one state
		
		NSHM23_FaultModels fm = NSHM23_FaultModels.NSHM23_v2;
		
		NSHM23_SingleStates state = UT;
		
		List<? extends FaultSection> allSects = fm.getFaultSections();
		
		List<GeoJSONFaultSection> stateSects = new ArrayList<>();
		for (FaultSection sect : allSects) {
			Preconditions.checkState(sect instanceof GeoJSONFaultSection);
			GeoJSONFaultSection geoSect = (GeoJSONFaultSection)sect;
			if (state.contains(geoSect))
				stateSects.add(geoSect);
		}
		
		GeoJSONFaultReader.writeFaultSections(
				new File("/tmp/"+fm.getFilePrefix()+"_"+state.getFilePrefix()+".geojson"), stateSects);
	}

}
