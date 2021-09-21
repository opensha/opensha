package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;

public interface PolygonFaultGridAssociations extends FaultGridAssociations {
	
	/**
	 * Returns the polygon{@code Region} for the fault section at {@code sectIdx}.
	 * @param idx section to get polygon for
	 * @return the section's polygon
	 */
	public Region getPoly(int sectIdx);
	
	public static class Precomputed extends FaultGridAssociations.Precomputed implements PolygonFaultGridAssociations {
		
		private ImmutableMap<Integer, Region> polygons;

		public Precomputed() {
			super();
		}

		public Precomputed(PolygonFaultGridAssociations associations) {
			super(associations);
			
			ImmutableMap.Builder<Integer, Region> polyBuilder = ImmutableMap.builder();
			
			for (int sectIndex : associations.sectIndices())
				polyBuilder.put(sectIndex, associations.getPoly(sectIndex));
			polygons = polyBuilder.build();
		}

		@Override
		public String getName() {
			return "Polygon Fault Grid Associations";
		}
		
		private static final String ARCHIVE_SECT_POLYGONS_FILE_NAME = "sect_polygons.geojson";

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			super.writeToArchive(zout, entryPrefix);
			
			// write polygons
			List<Feature> features = new ArrayList<>();
			for (int sectIndex=0; sectIndex<polygons.size(); sectIndex++) {
				Region polygon = polygons.get(sectIndex);
				if (polygon == null)
					continue;
				Feature feature = polygon.toFeature();
				feature = new Feature(sectIndex, feature.geometry, feature.properties);
				features.add(feature);
			}
			FeatureCollection collection = new FeatureCollection(features);
			FileBackedModule.initEntry(zout, entryPrefix, ARCHIVE_SECT_POLYGONS_FILE_NAME);
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zout));
			FeatureCollection.write(collection, writer);
			writer.flush();
			zout.flush();
			zout.closeEntry();
		}

		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			super.initFromArchive(zip, entryPrefix);
			
			BufferedInputStream polysIS = FileBackedModule.getInputStream(zip, entryPrefix, ARCHIVE_SECT_POLYGONS_FILE_NAME);
			InputStreamReader regionReader = new InputStreamReader(polysIS);
			FeatureCollection features = FeatureCollection.read(regionReader);
			
			ImmutableMap.Builder<Integer, Region> polyBuilder = ImmutableMap.builder();
			for (Feature feature : features.features) {
				if (feature == null)
					continue;
				Preconditions.checkState(feature.id instanceof Number, "Polygon feature must have integer ID polulated");
				int id = ((Number)feature.id).intValue();
				polyBuilder.put(id, Region.fromFeature(feature));
			}
			polygons = polyBuilder.build();
		}

		@Override
		public Region getPoly(int sectIdx) {
			return polygons.get(sectIdx);
		}
		
	}
	
//	public static void main(String[] args) throws IOException {
//		// write out UCERF3 data
//		// if you uncomment this, make sure to revert to the old location class first
//		File outputDir = new File("src/main/resources/data/erf/ucerf3/seismicityGrids/");
//		for (FaultModels fm : new FaultModels[] {FaultModels.FM3_1, FaultModels.FM3_2}) {
//			FaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(fm);
//			PolygonFaultGridAssociations polys = rupSet.requireModule(PolygonFaultGridAssociations.class);
//			ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
//			archive.addModule(polys);
//			File ouputFile = new File(outputDir, fm.name().toLowerCase()+"_fault_polygon_grid_node_associations.zip");
//			archive.write(ouputFile);
//		}
//	}

}
