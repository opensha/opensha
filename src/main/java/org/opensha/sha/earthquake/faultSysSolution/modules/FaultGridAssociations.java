package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;

/**
 * Module that maintains collections of the relationships between grid nodes
 * and fault sections. Use of the word 'node' in this class generally refers to
 * the lat-lon cell represented by the 'node'.
 * 
 * @author Peter, Kevin
 *
 */
public interface FaultGridAssociations extends OpenSHA_Module {

	/**
	 * Returns a map of nodes (indices of nodes intersected by faults) to the
	 * fraction of each node that intersects faults.
	 * 
	 * In other words, the fraction of each node that is covered by one or more fault polygons.
	 * @return the node extent map
	 */
	Map<Integer, Double> getNodeExtents();

	/**
	 * Returns the fraction of the node at nodeIdx that participates in fault
	 * section related seismicity (i.e. the percent of cell represented by a 
	 * node that is spanned by fault-section polygons).
	 * 
	 * In other words, the fraction of the node that is covered by one or more fault polygons.
	 * 
	 * @param nodeIdx
	 * @return the fraction of the node area at {@code nodeIdx} occupied by faults
	 */
	double getNodeFraction(int nodeIdx);

	/**
	 * Returns a map of the indices of nodes that intersect the fault-section at
	 * {@code sectIdx} where the values are the (weighted) fraction of the area of
	 * the node occupied by the fault-section.
	 * 
	 * In other words, this returns a list of nodes and the faction of each node assigned 
	 * to the fault polygon (where each fraction is reduced by extent to which each node
	 * is also covered by other fault polygons).
	 * 
	 * <p>Use this method when distributing some property of a node across the fault
	 * sections it intersects.</p>
	 * 
	 * @param idx fault-section index
	 * @return a map of fault-section participation in nodes
	 */
	Map<Integer, Double> getScaledNodeFractions(int sectIdx);

	/**
	 * Returns a map of the indices of nodes that intersect the fault-section at
	 * {@code sectIdx} where the values are the fraction of the area of the
	 * fault-section occupied by each node. The values in the map sum to 1.
	 * 
	 * In other words, the fraction of the fault polygon occupied by each node,
	 * where fractions sum to 1.0. 
	 * 
	 * <p>Use this method when distributing some property of a fault section across
	 * the nodes it intersects.</p>
	 * 
	 * @param idx section index
	 * @return a map of node participation in a fault-section
	 */
	Map<Integer, Double> getNodeFractions(int sectIdx);

	/**
	 * This provides the sections and fraction of each section that contributes to the node.
	 * @param nodeIdx
	 * @return
	 */
	Map<Integer, Double> getSectionFracsOnNode(int nodeIdx);

	/**
	 * Returns the region used by these FaultGridAssociations
	 * @return
	 */
	GriddedRegion getRegion();
	
	/**
	 * Returns the fault section for which associations are
	 * @return
	 */
	Collection<Integer> sectIndices();

	public static final String ARCHIVE_GRID_REGION_FILE_NAME = "grid_region.geojson";
	public static final String ARCHIVE_NODE_EXTENTS_FILE_NAME = "grid_node_association_fracts.csv";
	public static final String ARCHIVE_SECT_NODE_ASSOCIATIONS_FILE_NAME = "grid_node_sect_associations.csv";
	
	public static class Precomputed implements FaultGridAssociations, ArchivableModule {
		
		private GriddedRegion region;
		
		private ImmutableList<Integer> sectIndices;
		
		private ImmutableMap<Integer, Double> nodeExtents;
		
		// both are Table<SubSectionID, NodeIndex, Value>
		//
		// the percentage of each node spanned by each fault sub-section
		private ImmutableTable<Integer, Integer, Double> nodeInSectPartic;
		// same as above, scaled with percentage scaled to account for
		// multiple overlapping sub-sections
		private ImmutableTable<Integer, Integer, Double> sectInNodePartic;
		
		protected Precomputed() {
			
		}
		
		public Precomputed(FaultGridAssociations associations) {
			Preconditions.checkNotNull(associations, "Passed in associations are null");
			region = associations.getRegion();
			nodeExtents = ImmutableMap.copyOf(associations.getNodeExtents());
			ImmutableTable.Builder<Integer, Integer, Double> nodeInSectParticBuilder = ImmutableTable.builder();
			ImmutableTable.Builder<Integer, Integer, Double> sectInNodeParticBuilder = ImmutableTable.builder();
			this.sectIndices = ImmutableList.copyOf(associations.sectIndices());
			for (int sectIndex : sectIndices) {
				Map<Integer, Double> sectNodeFractions = associations.getNodeFractions(sectIndex);
				Map<Integer, Double> scaledNodeFractions = associations.getScaledNodeFractions(sectIndex);
				if (sectNodeFractions == null) {
					Preconditions.checkState(scaledNodeFractions == null,
							"Have scaled node fractions for sect %s but not unscaled?", sectIndex);
					continue;
				}
				Preconditions.checkNotNull(scaledNodeFractions,
						"Have un-scaled node fractions but not scaled for sect %s", sectIndex);
				Preconditions.checkState(sectNodeFractions.size() == scaledNodeFractions.size(),
						"Scaled & un-scaled node fractions maps of different sizes for sect %s", sectIndex);
				for (int nodeIndex : sectNodeFractions.keySet()) {
					Double nodeFraction = sectNodeFractions.get(nodeIndex);
					Double scaledFraction = scaledNodeFractions.get(nodeIndex);
					nodeInSectParticBuilder.put(sectIndex, nodeIndex, nodeFraction);
					sectInNodeParticBuilder.put(sectIndex, nodeIndex, scaledFraction);
				}
			}
			nodeInSectPartic = nodeInSectParticBuilder.build();
			sectInNodePartic = sectInNodeParticBuilder.build();
		}

		@Override
		public String getName() {
			return "Fault Grid Associations";
		}

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			FileBackedModule.initEntry(zout, entryPrefix, ARCHIVE_GRID_REGION_FILE_NAME);
			Feature regFeature = region.toFeature();
			OutputStreamWriter writer = new OutputStreamWriter(zout);
			Feature.write(regFeature, writer);
			writer.flush();
			zout.flush();
			zout.closeEntry();
			
			CSVFile<String> extentsCSV = new CSVFile<>(true);
			extentsCSV.addLine("Grid Node Index", "Fraction Associated With Fault");
			for (int nodeIndex=0; nodeIndex<region.getNodeCount(); nodeIndex++)
				extentsCSV.addLine(nodeIndex+"", getNodeFraction(nodeIndex)+"");
			CSV_BackedModule.writeToArchive(extentsCSV, zout, entryPrefix, ARCHIVE_NODE_EXTENTS_FILE_NAME);
			
			CSVFile<String> mappingsCSV = new CSVFile<>(true);
			mappingsCSV.addLine("Section Index", "Grid Node Index", "Node Fraction", "Scaled Node Fraction");
			ArrayList<Integer> sectIDs = new ArrayList<>(nodeInSectPartic.rowKeySet());
			Collections.sort(sectIDs);
			for (int sectIndex : sectIDs) {
				Map<Integer, Double> sectNodeFractions = nodeInSectPartic.row(sectIndex);
				Map<Integer, Double> scaledNodeFractions = sectInNodePartic.row(sectIndex);
				ArrayList<Integer> nodeIndexes = new ArrayList<>(sectNodeFractions.keySet());
				Collections.sort(nodeIndexes);
				for (int nodeIndex : nodeIndexes) {
					Double sectFract = sectNodeFractions.get(nodeIndex);
					Double scaledFract = scaledNodeFractions.get(nodeIndex);
					mappingsCSV.addLine(sectIndex+"", nodeIndex+"", sectFract.toString(), scaledFract.toString());
				}
			}
			CSV_BackedModule.writeToArchive(mappingsCSV, zout, entryPrefix, ARCHIVE_SECT_NODE_ASSOCIATIONS_FILE_NAME);
		}

		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			BufferedInputStream regionIS = FileBackedModule.getInputStream(zip, entryPrefix, ARCHIVE_GRID_REGION_FILE_NAME);
			InputStreamReader regionReader = new InputStreamReader(regionIS);
			Feature regFeature = Feature.read(regionReader);
			region = GriddedRegion.fromFeature(regFeature);
			
			CSVFile<String> extentsCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, ARCHIVE_NODE_EXTENTS_FILE_NAME);
			ImmutableMap.Builder<Integer, Double> extentsBuilder = ImmutableMap.builderWithExpectedSize(extentsCSV.getNumRows()-1);
			for (int row=1; row<extentsCSV.getNumRows(); row++)
				extentsBuilder.put(extentsCSV.getInt(row, 0), extentsCSV.getDouble(row, 1));
			nodeExtents = extentsBuilder.build();
			
			CSVFile<String> mappingsCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, ARCHIVE_SECT_NODE_ASSOCIATIONS_FILE_NAME);
			ImmutableTable.Builder<Integer, Integer, Double> nodeInSectParticBuilder = ImmutableTable.builder();
			ImmutableTable.Builder<Integer, Integer, Double> sectInNodeParticBuilder = ImmutableTable.builder();
			for (int row=1; row<mappingsCSV.getNumRows(); row++) {
				int sectIndex = mappingsCSV.getInt(row, 0);
				int nodeIndex = mappingsCSV.getInt(row, 1);
				nodeInSectParticBuilder.put(sectIndex, nodeIndex, mappingsCSV.getDouble(row, 2));
				sectInNodeParticBuilder.put(sectIndex, nodeIndex, mappingsCSV.getDouble(row, 3));
			}
			nodeInSectPartic = nodeInSectParticBuilder.build();
			sectInNodePartic = sectInNodeParticBuilder.build();
		}

		@Override
		public Map<Integer, Double> getNodeExtents() {
			return ImmutableMap.copyOf(nodeExtents);
		}
		
		@Override
		public double getNodeFraction(int nodeIdx) {
			Double fraction = nodeExtents.get(nodeIdx);
			return (fraction == null) ? 0.0 : fraction;
		}
		
		@Override
		public Map<Integer, Double> getScaledNodeFractions(int sectIdx) {
			return sectInNodePartic.row(sectIdx);
		}
		
		@Override
		public Map<Integer, Double> getNodeFractions(int sectIdx) {
			return nodeInSectPartic.row(sectIdx);
		}
		
		@Override
		public Map<Integer, Double> getSectionFracsOnNode(int nodeIdx) {
			return nodeInSectPartic.column(nodeIdx);
		}

		@Override
		public GriddedRegion getRegion() {
			return region;
		}

		@Override
		public Collection<Integer> sectIndices() {
			return sectIndices;
		}
		
	}

}