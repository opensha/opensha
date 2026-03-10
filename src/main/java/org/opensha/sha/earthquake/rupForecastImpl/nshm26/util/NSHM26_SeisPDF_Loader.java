package org.opensha.sha.earthquake.rupForecastImpl.nshm26.util;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.CSVReader;
import org.opensha.commons.data.CSVReader.Row;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_DeclusteringAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_SeisSmoothingAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;

import com.google.common.base.Preconditions;

public class NSHM26_SeisPDF_Loader {

	private static final boolean PRINT_ALL_UNMAPPED_GRIDS = false;
	private static final boolean PRINT_FIRST_UNMAPPED_GRIDS = true;
	
	private static final DecimalFormat pDF = new DecimalFormat("0.00%");
	
	public static GriddedGeoDataSet load2D(File baseDir, NSHM26_SeismicityRegions region,
			NSHM26_DeclusteringAlgorithms decluster, NSHM26_SeisSmoothingAlgorithms smooth) throws IOException {
		if (decluster == NSHM26_DeclusteringAlgorithms.AVERAGE || smooth == NSHM26_SeisSmoothingAlgorithms.AVERAGE) {
			WeightedList<GriddedGeoDataSet> wtList = new WeightedList<>();
			NSHM26_DeclusteringAlgorithms[] declusters;
			if (decluster == NSHM26_DeclusteringAlgorithms.AVERAGE)
				declusters = NSHM26_DeclusteringAlgorithms.values();
			else
				declusters = new NSHM26_DeclusteringAlgorithms[] {decluster};
			NSHM26_SeisSmoothingAlgorithms[] smooths;
			if (smooth == NSHM26_SeisSmoothingAlgorithms.AVERAGE)
				smooths = NSHM26_SeisSmoothingAlgorithms.values();
			else
				smooths = new NSHM26_SeisSmoothingAlgorithms[] {smooth};
			for (NSHM26_DeclusteringAlgorithms d : declusters) {
				double dWeight = decluster == NSHM26_DeclusteringAlgorithms.AVERAGE ? d.getNodeWeight(null) : 1d;
				if (dWeight == 0d)
					continue;
				for (NSHM26_SeisSmoothingAlgorithms s : smooths) {
					double sWeight = smooth == NSHM26_SeisSmoothingAlgorithms.AVERAGE ? s.getNodeWeight(null) : 1d;
					if (sWeight == 0d)
						continue;
					wtList.add(load2D(region,  new File(baseDir, d.name()+"_"+s.name()+".csv")), dWeight*sWeight);
				}
			}
			return avg2D(wtList);
		}
		File file = new File(baseDir, decluster.name()+"_"+smooth.name()+".csv");
		return load2D(region, file);
	}
	
	private static GriddedGeoDataSet avg2D(WeightedList<GriddedGeoDataSet> xyzs) {
		GriddedGeoDataSet xyz = new GriddedGeoDataSet(xyzs.get(0).value.getRegion());
		xyzs.normalize();
		for (int i=0; i<xyzs.size(); i++) {
			double weight = xyzs.getWeight(i);
			GriddedGeoDataSet val = xyzs.getValue(i);
			for (int j=0; j<xyz.size(); j++)
				xyz.set(j, xyz.get(j)+val.get(j)*weight);
		}
		return xyz;
	}
	
	public static GriddedGeoDataSet load2D(NSHM26_SeismicityRegions region, File file) throws IOException {
		return load2D(region, file, false);
	}
	
	public static GriddedGeoDataSet load2D(NSHM26_SeismicityRegions region, File file, boolean writeUnmappedPlot) throws IOException {
		Region reg = region.load();
		GriddedRegion gridReg = new GriddedRegion(reg, 0.1d, GriddedRegion.ANCHOR_0_0);
		GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg, false);
		double sum = 0d;
		int numMapped = 0;
		
		List<Location> mappedLocs = new ArrayList<>();
		List<Location> unmappedLocs = new ArrayList<>();
		
		int numRows = 0;
		try (CSVReader csv = new CSVReader(new FileInputStream(file))) {
			Row row;
			while ((row = csv.read()) != null) {
				numRows++;
				double lon = row.getDouble(0);
				if (lon < 0)
					lon += 360d;
				double lat = row.getDouble(1);
				double val = row.getDouble(2);
				sum += val;
				Location loc = new Location(lat, lon);
				int gridIndex = gridReg.indexForLocation(loc);
				if (gridIndex >= 0) {
					numMapped++;
					Preconditions.checkState(xyz.get(gridIndex) == 0d);
					xyz.set(gridIndex, val);
					mappedLocs.add(loc);
				} else if (val > 0) {
					unmappedLocs.add(loc);
					if (PRINT_ALL_UNMAPPED_GRIDS || (unmappedLocs.size() == 1 && PRINT_FIRST_UNMAPPED_GRIDS))
						System.out.println("Unmapped: "+loc+" = "+val);
				}
			}
		}
		double sumMapped = xyz.getSumZ();
		System.out.println("totWeight="+(float)sum+";\tmappedWeight="+(float)sumMapped+"; mapping results:");
		System.out.println("\t"+numMapped+"/"+numRows+" ("
				+pDF.format((double)numMapped/(double)numRows)+") of locations from input CSV mapped");
		System.out.println("\t"+numMapped+"/"+gridReg.getNodeCount()+" ("
				+pDF.format((double)numMapped/(double)gridReg.getNodeCount())+") of gridded region mapped");
		Preconditions.checkState(Precision.equals(sumMapped, 1d, 0.01),
				"PDF (%s) doesn't sum to 1 when mapped to region: sum=%s, sumMapped=%s", file, (float)sum, (float)sumMapped);
		xyz.scale(1d/sumMapped);
		
		if (writeUnmappedPlot && !unmappedLocs.isEmpty()) {
			File outputDir = file.getParentFile();
			String prefix = file.getName();
			if (prefix.endsWith(".csv"))
				prefix = prefix.substring(0, prefix.indexOf(".csv"));
			prefix += "_unmapped";
			GeographicMapMaker mapMaker = new GeographicMapMaker(reg);
			mapMaker.setRegionOutlineChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK), true);
			mapMaker.setWriteGeoJSON(false);
			List<Location> combLocs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			combLocs.addAll(mappedLocs);
			PlotCurveCharacterstics mappedChar = new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 3f, Color.GREEN.darker());
			while (chars.size() < combLocs.size())
				chars.add(mappedChar);
			combLocs.addAll(unmappedLocs);
			PlotCurveCharacterstics unmappedChar = new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 3f, Color.RED.darker());
			while (chars.size() < combLocs.size())
				chars.add(unmappedChar);
			mapMaker.plotScatters(combLocs, chars, null);
			mapMaker.plot(outputDir, prefix, reg.getName()+", "+file.getName()+" Mappings");
		}
		
		return xyz;
	}

	public static void main(String[] args) throws IOException {
		NSHM26_SeismicityRegions region = NSHM26_SeismicityRegions.GNMI;
		File dir2D = new File("/home/kevin/OpenSHA/nshm26/spatial_seis_pdfs/gnmi/2026_03_09-v1_2D");
		for (File subdir : dir2D.listFiles()) {
			for (File file : subdir.listFiles()) {
				if (file.getName().endsWith(".csv")) {
					System.out.println("Reading:\t"+file.getAbsolutePath());
					load2D(region, file, true);
				}
			}
		}
	}

}
