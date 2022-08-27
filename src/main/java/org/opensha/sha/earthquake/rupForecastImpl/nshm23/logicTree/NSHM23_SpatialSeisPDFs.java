package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.PrimaryRegions;

import com.google.common.base.Preconditions;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@DoesNotAffect(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME)
@Affects(GridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME)
@Affects(GridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME)
public enum NSHM23_SpatialSeisPDFs implements LogicTreeNode {
	
	GK_ADAPTIVE("Gardner-Knopoff, Adaptive", "GK-Adaptive", 1d),
	GK_FIXED("Gardner-Knopoff, Fixed", "GK-Fixed", 1d),
	NN_ADAPTIVE("Nearest-Neighbor, Adaptive", "NN-Adaptive", 1d),
	NN_FIXED("Nearest-Neighbor, Fixed", "NN-Fixed", 1d),
	REAS_ADAPTIVE("Reasenberg, Adaptive", "Reas-Adaptive", 1d),
	REAS_FIXED("Reasenberg, Fixed", "Reas-Fixed", 1d);
	
	private String name;
	private String shortName;
	private double weight;

	private NSHM23_SpatialSeisPDFs(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}
	
	private static final String NSHM23_SS_PATH_PREFIX = "/data/erf/nshm23/seismicity/spatial_seis_pdfs/";
	
	private String getResourceName(PrimaryRegions region) {
		return NSHM23_SS_PATH_PREFIX+region.name()+"/"+name()+".csv";
	}
	
	public double[] load(PrimaryRegions region) throws IOException {
		String resource = getResourceName(region);
		
		System.out.println("Loading spatial seismicity PDF from: "+resource);
		InputStream is = NSHM23_SpatialSeisPDFs.class.getResourceAsStream(resource);
		Preconditions.checkNotNull(is, "Spatial seismicity PDF not found: %s", resource);
		CSVFile<String> csv = CSVFile.readStream(is, true);
		
		GriddedRegion gridReg = new GriddedRegion(region.load(), 0.1d, GriddedRegion.ANCHOR_0_0);
		GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg, false);
		double sum = 0d;
		int numMapped = 0;
		for (int row=0; row<csv.getNumRows(); row++) {
			double lon = csv.getDouble(row, 0);
			double lat = csv.getDouble(row, 1);
			double val = csv.getDouble(row, 2);
			sum += val;
			Location loc = new Location(lat, lon);
			int gridIndex = gridReg.indexForLocation(loc);
			if (gridIndex >= 0) {
				numMapped++;
				Preconditions.checkState(xyz.get(gridIndex) == 0d);
				xyz.set(gridIndex, val);
			}
		}
		double sumMapped = xyz.getSumZ();
		System.out.println("totWeight="+(float)sum+";\tmappedWeight="+(float)sumMapped+"; mapping results:");
		System.out.println("\t"+numMapped+"/"+csv.getNumRows()+" ("
				+pDF.format((double)numMapped/(double)csv.getNumRows())+") of locations from input CSV mapped");
		System.out.println("\t"+numMapped+"/"+gridReg.getNodeCount()+" ("
				+pDF.format((double)numMapped/(double)gridReg.getNodeCount())+") of gridded region mapped");
		xyz.scale(1d/sumMapped);
		return xyz.getValues();
	}
	
	private static final DecimalFormat pDF = new DecimalFormat("0.00%");

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
	
	public static void main(String[] args) throws IOException {
		for (PrimaryRegions region : PrimaryRegions.values()) {
			if (region == PrimaryRegions.ALASKA || region == PrimaryRegions.CONUS_HAWAII)
				continue;
			for (NSHM23_SpatialSeisPDFs pdf : values()) {
				System.out.println(region.name()+",\t"+pdf.name());
				pdf.load(region);
			}
		}
	}

}
