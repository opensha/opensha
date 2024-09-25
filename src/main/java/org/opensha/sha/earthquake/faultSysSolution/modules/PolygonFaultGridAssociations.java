package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations.Averager;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;

/**
 * TODO: averaging support?
 * 
 * @author kevin
 *
 */
public interface PolygonFaultGridAssociations extends FaultGridAssociations {
	
	/**
	 * Returns the polygon{@code Region} for the fault section at {@code sectIdx}.
	 * @param idx section to get polygon for
	 * @return the section's polygon
	 */
	public Region getPoly(int sectIdx);
	
	@Override
	public default AveragingAccumulator<FaultGridAssociations> averagingAccumulator() {
		return new Averager();
	}

	public static class Precomputed extends FaultGridAssociations.Precomputed implements PolygonFaultGridAssociations {
		
		private ImmutableMap<Integer, Region> polygons;

		public Precomputed() {
			super();
		}
		
		public Precomputed(FaultGridAssociations associations, Map<Integer, Region> polygons) {
			super(associations);
			this.polygons = ImmutableMap.copyOf(polygons);
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
		public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
			super.writeToArchive(output, entryPrefix);
			
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
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					FileBackedModule.initOutputStream(output, entryPrefix, ARCHIVE_SECT_POLYGONS_FILE_NAME)));
			FeatureCollection.write(collection, writer);
			writer.flush();
			output.closeEntry();
		}

		@Override
		public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
			super.initFromArchive(input, entryPrefix);
			
			BufferedInputStream polysIS = FileBackedModule.getInputStream(input, entryPrefix, ARCHIVE_SECT_POLYGONS_FILE_NAME);
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
	
	public static class Averager implements AveragingAccumulator<FaultGridAssociations> {
		
		private FaultGridAssociations.Averager gridAverager;
		private ImmutableMap<Integer, Region> polys;

		@Override
		public Class<FaultGridAssociations> getType() {
			return FaultGridAssociations.class;
		}

		@Override
		public void process(FaultGridAssociations module, double relWeight) {
			if (gridAverager == null) {
				// first time
				gridAverager = new FaultGridAssociations.Averager();
				if (module instanceof PolygonFaultGridAssociations) {
					if (module instanceof Precomputed) {
						polys = ((Precomputed)module).polygons;
					} else {
						ImmutableMap.Builder<Integer, Region> polyBuilder = ImmutableMap.builder();
						PolygonFaultGridAssociations associations = (PolygonFaultGridAssociations)module;
						for (int sectIndex : associations.sectIndices())
							polyBuilder.put(sectIndex, associations.getPoly(sectIndex));
						polys = polyBuilder.build();
					}
				}
			} else if (polys != null) {
				if (module instanceof PolygonFaultGridAssociations) {
					PolygonFaultGridAssociations associations = (PolygonFaultGridAssociations)module;
					int numPolys = 0;
					for (int sectIndex : associations.sectIndices()) {
						Region poly = associations.getPoly(sectIndex);
						if (poly != null) {
							numPolys++;
							Region prevPoly = polys.get(sectIndex);
							if (prevPoly == null || !prevPoly.equalsRegion(poly)) {
								System.err.println("WARNING: poly mismatch for sect "+sectIndex+", will average as simple "
										+ "FaultGridAssociations");
								polys = null;
								break;
							}
						}
					}
					if (polys != null && numPolys != polys.size()) {
						System.err.println("WARNING: polys.size() mismatch, will average as simple "
								+ "FaultGridAssociations: "+numPolys+" != "+polys.size());
						polys = null;
					}
				} else {
					// this one doesn't have polys
					System.err.println("WARNING: sub module doesn't have polys, will average as simple "
							+ "FaultGridAssociations");
					polys = null;
				}
			}
			gridAverager.process(module, relWeight);
		}

		@Override
		public FaultGridAssociations getAverage() {
			FaultGridAssociations associations = gridAverager.getAverage();
			if (polys != null)
				return new Precomputed(associations, polys);
			System.err.println("WARNING: don't have average polys, returning a simple FaultGridAssociations");
			return associations;
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
