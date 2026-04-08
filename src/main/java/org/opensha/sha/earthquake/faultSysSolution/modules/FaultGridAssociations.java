package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

/**
 * Module that maintains collections of the relationships between grid nodes
 * and fault sections. Use of the word 'node' in this class generally refers to
 * the lat-lon cell represented by the 'node'.
 * 
 * @author Peter, Kevin
 *
 */
public interface FaultGridAssociations extends OpenSHA_Module, BranchAverageableModule<FaultGridAssociations> {

	/**
	 * Returns a map of grid nodes (indices of nodes intersected by faults) to the
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
	 * the node occupied by the fault-section. See {@link #getScaledSectFracsOnNode(int)} to
	 * access the same information but keyed on node index.
	 * 
	 * In other words, this returns a map of nodes and the faction of each node assigned 
	 * to the fault polygon (where each fraction is reduced by extent to which each node
	 * is also covered by other fault polygons).
	 * 
	 * <p>Use this method or {@link #getScaledSectFracsOnNode(int)} when distributing some
	 * property of a node across the fault sections it intersects.</p>
	 * 
	 * @param idx fault-section index
	 * @return a map of fault-section participation in nodes
	 */
	Map<Integer, Double> getScaledNodeFractions(int sectIdx);
	
	/**
	 * For a given node, this provides a map of the sections that overlap it and their fractional
	 * weight. This is scaled to account for overlap and the sum of all values will never exceed 1.
	 * 
	 * This returns the same information as {@link #getScaledNodeFractions(int)}, except keyed on
	 * the node index rather than the section index.
	 * 
	 * @param nodeIdx
	 * @return
	 */
	Map<Integer, Double> getScaledSectFracsOnNode(int nodeIdx);

	/**
	 * Returns a map of the indices of nodes that intersect the fault-section at
	 * {@code sectIdx} where the values are the fraction of the area of the
	 * fault-section occupied by each node. See {@link #getSectionFracsOnNode(int)} to access
	 * the same information but keyed on node index.
	 * 
	 * In other words, the fraction of the fault polygon occupied by each node, not accounting
	 * for overlap with other sections. The values will typically sum to 1.0, unless the
	 * fault is not fully contained within the region.
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
	 * This returns the same information as {{@link #getNodeFractions(int)}, except keyed 
	 * on the node rather than the section. It can also be used to distribute some
	 * property of a section to a node.
	 * 
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
	
	/**
	 * The provides that fraction of the given fault section that lies inside the region, defined
	 * using its polygon (or ramp, or whatever association structure is used). This won't necessarily
	 * equal the fraction of its surface or trace that lies inside the region.
	 * 
	 * The default implementation calculates it as the sum of all {@link #getNodeFractions(int)}.
	 * 
	 * @param sectIndex
	 * @return the fraction of the given fault section that lies inside the region
	 */
	default double getSectionFractInRegion(int sectIndex) {
		// imply it from the from the sum of getNodeFractions(s);
		double sum = 0d;
		for (double val : getNodeFractions(sectIndex).values())
			sum += val;
		Preconditions.checkState((float)sum <= 1.01f, "Bad sum when calculation section fraction in region: %s", sum);
		return sum;
	}

	@Override
	public default AveragingAccumulator<FaultGridAssociations> averagingAccumulator() {
		return new Averager();
	}

	public static final String ARCHIVE_GRID_REGION_FILE_NAME = "grid_region.geojson";
	public static final String ARCHIVE_NODE_EXTENTS_FILE_NAME = "grid_node_association_fracts.csv";
	public static final String ARCHIVE_SECT_NODE_ASSOCIATIONS_FILE_NAME = "grid_node_sect_associations.csv";
	
	public static class Precomputed implements FaultGridAssociations, ArchivableModule {
		
		// for lazy init
		protected Feature regionFeature;
		protected GriddedRegion region;
		
		private ImmutableList<Integer> sectIndices;
		
		private ImmutableMap<Integer, Double> nodeExtents;
		
		// both are Table<SubSectionID, NodeIndex, Value>
		//
		// the percentage of each node spanned by each fault sub-section
		private ImmutableTable<Integer, Integer, Double> sectNodeOrigFracts;
		// same as above, scaled with percentage scaled to account for
		// multiple overlapping sub-sections
		private ImmutableTable<Integer, Integer, Double> sectNodeScaledFracts;
		
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
			sectNodeOrigFracts = nodeInSectParticBuilder.build();
			sectNodeScaledFracts = sectInNodeParticBuilder.build();
		}

		@Override
		public String getName() {
			return "Fault Grid Associations";
		}

		@Override
		public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
			FileBackedModule.initEntry(output, entryPrefix, ARCHIVE_GRID_REGION_FILE_NAME);
			OutputStreamWriter writer = new OutputStreamWriter(output.getOutputStream());
			Feature.write(getRegionFeature(), writer);
			writer.flush();
			output.closeEntry();
			
			CSVFile<String> extentsCSV = new CSVFile<>(true);
			extentsCSV.addLine("Grid Node Index", "Fraction Associated With Fault");
			GriddedRegion region = getRegion();
			for (int nodeIndex=0; nodeIndex<region.getNodeCount(); nodeIndex++)
				extentsCSV.addLine(nodeIndex+"", getNodeFraction(nodeIndex)+"");
			CSV_BackedModule.writeToArchive(extentsCSV, output, entryPrefix, ARCHIVE_NODE_EXTENTS_FILE_NAME);
			
			CSVFile<String> mappingsCSV = new CSVFile<>(true);
			mappingsCSV.addLine("Section Index", "Grid Node Index", "Node Fraction", "Scaled Node Fraction");
			ArrayList<Integer> sectIDs = new ArrayList<>(sectNodeOrigFracts.rowKeySet());
			Collections.sort(sectIDs);
			for (int sectIndex : sectIDs) {
				Map<Integer, Double> sectNodeFractions = sectNodeOrigFracts.row(sectIndex);
				Map<Integer, Double> scaledNodeFractions = sectNodeScaledFracts.row(sectIndex);
				ArrayList<Integer> nodeIndexes = new ArrayList<>(sectNodeFractions.keySet());
				Collections.sort(nodeIndexes);
				for (int nodeIndex : nodeIndexes) {
					Double sectFract = sectNodeFractions.get(nodeIndex);
					Double scaledFract = scaledNodeFractions.get(nodeIndex);
					mappingsCSV.addLine(sectIndex+"", nodeIndex+"", sectFract.toString(), scaledFract.toString());
				}
			}
			CSV_BackedModule.writeToArchive(mappingsCSV, output, entryPrefix, ARCHIVE_SECT_NODE_ASSOCIATIONS_FILE_NAME);
		}

		@Override
		public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
			BufferedInputStream regionIS = FileBackedModule.getInputStream(input, entryPrefix, ARCHIVE_GRID_REGION_FILE_NAME);
			InputStreamReader regionReader = new InputStreamReader(regionIS);
			regionFeature = Feature.read(regionReader);
			
			CSVFile<String> extentsCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, ARCHIVE_NODE_EXTENTS_FILE_NAME);
			ImmutableMap.Builder<Integer, Double> extentsBuilder = ImmutableMap.builderWithExpectedSize(extentsCSV.getNumRows()-1);
			for (int row=1; row<extentsCSV.getNumRows(); row++)
				extentsBuilder.put(extentsCSV.getInt(row, 0), extentsCSV.getDouble(row, 1));
			nodeExtents = extentsBuilder.build();
			
			CSVFile<String> mappingsCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, ARCHIVE_SECT_NODE_ASSOCIATIONS_FILE_NAME);
			ImmutableTable.Builder<Integer, Integer, Double> nodeInSectParticBuilder = ImmutableTable.builder();
			ImmutableTable.Builder<Integer, Integer, Double> sectInNodeParticBuilder = ImmutableTable.builder();
			for (int row=1; row<mappingsCSV.getNumRows(); row++) {
				int sectIndex = mappingsCSV.getInt(row, 0);
				int nodeIndex = mappingsCSV.getInt(row, 1);
				nodeInSectParticBuilder.put(sectIndex, nodeIndex, mappingsCSV.getDouble(row, 2));
				sectInNodeParticBuilder.put(sectIndex, nodeIndex, mappingsCSV.getDouble(row, 3));
			}
			sectNodeOrigFracts = nodeInSectParticBuilder.build();
			sectNodeScaledFracts = sectInNodeParticBuilder.build();
			
			sectIndices = ImmutableList.copyOf(sectNodeOrigFracts.rowKeySet());
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
			return sectNodeScaledFracts.row(sectIdx);
		}
		
		@Override
		public Map<Integer, Double> getScaledSectFracsOnNode(int sectIdx) {
			return sectNodeScaledFracts.column(sectIdx);
		}
		
		@Override
		public Map<Integer, Double> getNodeFractions(int sectIdx) {
			return sectNodeOrigFracts.row(sectIdx);
		}
		
		@Override
		public Map<Integer, Double> getSectionFracsOnNode(int nodeIdx) {
			return sectNodeOrigFracts.column(nodeIdx);
		}

		@Override
		public GriddedRegion getRegion() {
			if (region == null) {
				synchronized (this) {
					if (region == null) {
						Preconditions.checkNotNull(regionFeature,
								"Region is null but we don't have a Feature to load it from");
						region = GriddedRegion.fromFeature(regionFeature);
					}
				}
			}
			return region;
		}
		
		protected Feature getRegionFeature() {
			if (regionFeature == null)
				regionFeature = getRegion().toFeature();
			return regionFeature;
		}

		@Override
		public Collection<Integer> sectIndices() {
			return sectIndices;
		}
		
	}
	
	public static class Averager implements AveragingAccumulator<FaultGridAssociations> {
		
		private FaultGridAssociations ref;
		private boolean identical;
		private GriddedRegion gridReg;
		
		private double sumWeight = 0d;
		
		private HashMap<Integer, Double> nodeExtents;
		private Table<Integer, Integer, Double> sectNodeOrigFracts;
		private Table<Integer, Integer, Double> sectNodeScaledFracts;
		private HashSet<Integer> sectIndices;
		
		private boolean ID_D = false;

		@Override
		public Class<FaultGridAssociations> getType() {
			return FaultGridAssociations.class;
		}

		@Override
		public void process(FaultGridAssociations module, double relWeight) {
			if (ref == null) {
				ref = module;
				identical = true;
				gridReg = ref.getRegion();
				
				nodeExtents = new HashMap<>();
				sectNodeOrigFracts = HashBasedTable.create();
				sectNodeScaledFracts = HashBasedTable.create();
				sectIndices = new HashSet<>();
			} else {
				Preconditions.checkState(module.getRegion().equalsRegion(ref.getRegion()));
			}
			
			if (ID_D) System.out.println("identical="+identical+" as we begin processing module with relWeight="
					+relWeight+" (prevSum="+sumWeight+", ref == module ? "+(ref == module)+")");
			
			for (int sectIndex : module.sectIndices()) {
				sectIndices.add(sectIndex);
				for (boolean scaled : new boolean[] {false,true}) {
					String name;
					Map<Integer, Double> nodeFracts;
					Map<Integer, Double> refNodeFracts;
					Table<Integer, Integer, Double> dest;
					if (scaled) {
						name = "scaledNodeFracts";
						nodeFracts = module.getScaledNodeFractions(sectIndex);
						refNodeFracts = ref.getScaledNodeFractions(sectIndex);
						dest = sectNodeScaledFracts;
					} else {
						name = "nodeFracts";
						nodeFracts = module.getNodeFractions(sectIndex);
						refNodeFracts = ref.getNodeFractions(sectIndex);
						dest = sectNodeOrigFracts;
					}
					if (identical) {
						identical = refNodeFracts != null && nodeFracts.size() == refNodeFracts.size();
						if (ID_D && !identical) System.out.println("Identical fail for "+name+".size(): "
								+nodeFracts.size() +" != "+refNodeFracts.size());
					}
					for (int nodeIndex : nodeFracts.keySet()) {
						double nodeFract = nodeFracts.get(nodeIndex);
						Double prevFract = dest.get(sectIndex, nodeIndex);
						if (prevFract == null)
							prevFract = 0d;
						dest.put(sectIndex, nodeIndex, prevFract + nodeFract*relWeight);
						if (identical) {
							Double refNodeFract = refNodeFracts.get(nodeIndex);
							identical = refNodeFract != null && refNodeFract == nodeFract;
							if (ID_D && !identical) System.out.println("Identical fail for "+name+"["+nodeIndex+"]="
									+nodeFract+" != "+refNodeFract);
						}
					}
				}
			}
			for (int nodeIndex=0; nodeIndex<gridReg.getNodeCount(); nodeIndex++) {
				Double prevExtent = nodeExtents.get(nodeIndex);
				if (prevExtent == null)
					prevExtent = 0d;
				double extent = module.getNodeFraction(nodeIndex);
				if (identical) {
					identical = ref.getNodeFraction(nodeIndex) == extent;
					if (ID_D && !identical) System.out.println("Identical fail for extent["+nodeIndex+"]="
							+extent+" != "+ref.getNodeFraction(nodeIndex));
				}
				nodeExtents.put(nodeIndex, prevExtent + extent*relWeight);
			}
			if (ID_D) System.out.println("identical="+identical+" after processing module with relWeight="+relWeight);
			sumWeight += relWeight;
		}
		
		public boolean areAllIdentical() {
			return identical;
		}
		
		public void disableIdenticalCheck() {
			identical = false;
		}

		@Override
		public Precomputed getAverage() {
			if (identical)
				return ref instanceof Precomputed ? (Precomputed)ref : new Precomputed(ref);
			ImmutableMap.Builder<Integer, Double> nodeExtentsBuilder = ImmutableMap.builder();
			ImmutableTable.Builder<Integer, Integer, Double> sectNodeOrigFractsBuilder = ImmutableTable.builder();
			ImmutableTable.Builder<Integer, Integer, Double> sectNodeScaledFractsBuilder = ImmutableTable.builder();
			
			for (int nodeIndex : nodeExtents.keySet())
				nodeExtentsBuilder.put(nodeIndex, nodeExtents.get(nodeIndex)/sumWeight);
			for (Cell<Integer, Integer, Double> cell : sectNodeOrigFracts.cellSet())
				sectNodeOrigFractsBuilder.put(cell.getRowKey(), cell.getColumnKey(), cell.getValue()/sumWeight);
			for (Cell<Integer, Integer, Double> cell : sectNodeScaledFracts.cellSet())
				sectNodeScaledFractsBuilder.put(cell.getRowKey(), cell.getColumnKey(), cell.getValue()/sumWeight);
			
			Precomputed ret = new Precomputed();
			ret.region = ref.getRegion();
			ret.nodeExtents = nodeExtentsBuilder.build();
			ret.sectNodeOrigFracts = sectNodeOrigFractsBuilder.build();
			ret.sectNodeScaledFracts = sectNodeScaledFractsBuilder.build();
			ret.sectIndices = ImmutableList.copyOf(sectIndices);
			return ret;
		}
		
	}
	
	/**
	 * Simple {@link FaultGridAssociations} implementation where faults are associated by the fraction of the rupture
	 * surface area that lies within a grid cell, not including any polygons or distance taper.
	 * 
	 * Node to section associations here assume that any section that touches a node fully controls it. In the case of
	 * multiple sections touching a node, they are weighted by area.
	 * @param rupSet
	 * @param region
	 * @return
	 */
	public static Precomputed getIntersectionAssociations(FaultSystemRupSet rupSet, GriddedRegion region) {
		return getIntersectionAssociations(rupSet.getFaultSectionDataList(), region);
	}
	
	/**
	 * Simple {@link FaultGridAssociations} implementation where faults are associated by the fraction of the rupture
	 * surface area that lies within a grid cell, not including any polygons or distance taper.
	 * 
	 * Node to section associations here assume that any section that touches a node fully controls it. In the case of
	 * multiple sections touching a node, they are weighted by area.
	 * @param rupSet
	 * @param region
	 * @return
	 */
	public static Precomputed getIntersectionAssociations(List<? extends FaultSection> sects, GriddedRegion region) {
		Precomputed ret = new Precomputed();
		
		// set region
		ret.region = region;
		
		// don't have fractional node mappings, only from section to node
		// TODO revisit
		ret.nodeExtents = ImmutableMap.of();
		
		
		ImmutableTable.Builder<Integer, Integer, Double> nodeInSectParticBuilder = ImmutableTable.builder();
		ImmutableList.Builder<Integer> sectIndicesBuilder = ImmutableList.builder();
		
		// section index, node index, area of that section in that node
		Table<Integer, Integer, Double> sectAreasInNode = HashBasedTable.create();
		
		for (int s=0; s<sects.size(); s++) {
			FaultSection sect = sects.get(s);
			Preconditions.checkState(sect.getSectionId() == s, "Section IDs must be 0-based and in order");
			RuptureSurface surf = sect.getFaultSurface(0.25, false, true);
			Map<Integer, Integer> nodeAssocCounts = new HashMap<>();
			for (Location loc : surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
				int nodeIndex = region.indexForLocation(loc);
				if (nodeIndex >= 0) {
					Integer prevCount = nodeAssocCounts.get(nodeIndex);
					if (prevCount == null)
						prevCount = 0;
					nodeAssocCounts.put(nodeIndex, prevCount+1);
				}
			}
			if (!nodeAssocCounts.isEmpty()) {
				int sectIndex = sect.getSectionId();
				sectIndicesBuilder.add(sectIndex);
				double fractScalar = 1d/(double)surf.getEvenlyDiscretizedNumLocs();
				double areaScalar = sect.getArea(true) * fractScalar;
				for (int nodeIndex : nodeAssocCounts.keySet()) {
					double count = nodeAssocCounts.get(nodeIndex).doubleValue();
					double fractAssoc = count*fractScalar;
					nodeInSectParticBuilder.put(sectIndex, nodeIndex, fractAssoc);
					double areaAssoc = count*areaScalar;
					sectAreasInNode.put(sectIndex, nodeIndex, areaAssoc);
				}
			}
		}
		
		// now deal with overlapping faults
		ImmutableTable.Builder<Integer, Integer, Double> sectInNodeParticBuilder = ImmutableTable.builder();
		ImmutableMap.Builder<Integer, Double> nodeExtentsBuilder = ImmutableMap.builder();
		for (int nodeIndex : sectAreasInNode.columnKeySet()) {
			Map<Integer, Double> nodeSectAreas = sectAreasInNode.column(nodeIndex);
			if (nodeSectAreas.size() == 1) {
				sectInNodeParticBuilder.put(nodeSectAreas.keySet().iterator().next(), nodeIndex, 1d);
			} else {
				double sum = 0d;
				for (Double area : nodeSectAreas.values())
					sum += area;
				for (int sectIndex : nodeSectAreas.keySet()) {
					double fract = nodeSectAreas.get(sectIndex)/sum;
					sectInNodeParticBuilder.put(sectIndex, nodeIndex, fract);
				}
			}
			// fully associated
			nodeExtentsBuilder.put(nodeIndex, 1d);
		}
		
		ret.nodeExtents = nodeExtentsBuilder.build();
		ret.sectIndices = sectIndicesBuilder.build();
		ret.sectNodeOrigFracts = nodeInSectParticBuilder.build();
		ret.sectNodeScaledFracts = sectInNodeParticBuilder.build();
		
		return ret;
	}

}