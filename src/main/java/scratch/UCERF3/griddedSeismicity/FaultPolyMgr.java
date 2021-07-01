package scratch.UCERF3.griddedSeismicity;

import java.awt.geom.Area;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.utils.FaultSystemIO;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

/**
 * Class maintains collections of the polygonal relationships between grid nodes
 * and fault sections. Use of the word 'node' in this class generally referes to
 * the lat-lon cell represented by the 'node'.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class FaultPolyMgr implements Iterable<Area> {

	private static boolean log = false;
	
	private SectionPolygons polys;
	
	// both are Table<SubSectionID, NodeIndex, Value>
	//
	// the percentage of each node spanned by each fault sub-section
	private Table<Integer, Integer, Double> nodeInSectPartic;
	// same as above, scaled with percentage scaled to account for
	// multiple overlapping sub-sections
	private Table<Integer, Integer, Double> sectInNodePartic;
	
	// utility collections
	private Multimap<Integer, Integer> sectToProbNodes;
	private Multimap<Integer, Integer> sectToNodes;
	private Multimap<Integer, Integer> nodeToSects;
	private Map<Integer, Area> nodeAreas;
	private Map<Integer, Double> nodeExtents;
	private Map<Integer, Double> sectExtents;

	private GriddedRegion region;
	private FaultPolyMgr() {}
	
	/**
	 * Returns the region used by this fault polygon manager
	 * @return
	 */
	public GriddedRegion getRegion() {
		return region;
	}
	
	/**
	 * Returns a map of nodes (indices of nodes intersected by faults) to the
	 * fraction of each node that intersects faults.
	 * 
	 * In other words, the fraction of each node that is covered by one or more fault polygons.
	 * @return the node extent map
	 */
	public Map<Integer, Double> getNodeExtents() {
		return ImmutableMap.copyOf(nodeExtents);
	}
	
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
	public double getNodeFraction(int nodeIdx) {
		Double fraction = nodeExtents.get(nodeIdx);
		return (fraction == null) ? 0.0 : fraction;
	}
	
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
	public Map<Integer, Double> getScaledNodeFractions(int sectIdx) {
		return sectInNodePartic.row(sectIdx);
	}
	
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
	public Map<Integer, Double> getNodeFractions(int sectIdx) {
		return nodeInSectPartic.row(sectIdx);
	}
	
	
	/**
	 * This provides the sections and fraction of each section that contributes to the node.
	 * @param nodeIdx
	 * @return
	 */
	public Map<Integer, Double> getSectionFracsOnNode(int nodeIdx) {
		return nodeInSectPartic.column(nodeIdx);
	}
	
	
	/**
	 * Returns the polygon{@code Region} for the fault section at {@code sectIdx}.
	 * @param idx section to get polygon for
	 * @return the section's polygon
	 */
	public Region getPoly(int sectIdx) {
		Area a = polys.get(sectIdx);
		if (a == null) return null;
		LocationList locs = SectionPolygons.areaToLocLists(a).get(0);
		return new Region(locs, null);
	}
	
	/**
	 * Returns the indices of all polygons
	 * @return
	 */
	public Set<Integer> indices() {
		return polys.indices();
	}
	
	@Override
	public Iterator<Area> iterator() {
		return polys.polys().iterator();
	}
	
	/**
	 * Create a fault polygon manager from a list of {@code FaultSectionPrefData}.
	 * This method assumes that  of which references the polygon of it's parent fault.
	 * @param fspd {@code FaultSectionPrefData} to initialize manager with
	 * @param buf additional buffer around fault trace to include in polygon in
	 *        km on either side of fault; may be {@code null}
	 * @param region {@code GriddedRegion} to override the default California        
	 * @return a reference to the newly minted manager
	 */
	public static FaultPolyMgr create(List<? extends FaultSection> fspd, Double buf, GriddedRegion region) {
		FaultPolyMgr mgr = new FaultPolyMgr();
		if (log) System.out.println("Building poly mgr...");
		if (log) System.out.println("   section polygons");
		mgr.region = region;
		mgr.polys = SectionPolygons.create(fspd, buf, null);
		mgr.init();
		return mgr;
	}
	
	/**
	 * Create a fault polygon manager from a list of {@code FaultSectionPrefData}.
	 * This method assumes that  of which references the polygon of it's parent fault.
	 * @param fspd {@code FaultSectionPrefData} to initialize manager with
	 * @param buf additional buffer around fault trace to include in polygon in
	 *        km on either side of fault; may be {@code null}
	 * @return a reference to the newly minted manager
	 */	
	public static FaultPolyMgr create(List<? extends FaultSection> fspd, Double buf) {
		GriddedRegion region = new CaliforniaRegions.RELM_TESTING_GRIDDED();
		return create(fspd, buf, region);
	}
	
	/**
	 * Create a fault polygon manager from a FaultModel and a desired
	 * fault-section length (for subdividing faults in model).
	 * @param fm {@code FaultModel} to initialize manager with
	 * @param buf additional buffer around fault trace to include in polygon in
	 *        km on either side of fault; may be {@code null}
	 * @param len fault-section length for subdividing
	 * @param region a {@code GriddedRegion} to override the default California
	 * @return a reference to the newly minted manager
	 */
	public static FaultPolyMgr create(FaultModels fm, Double buf, Double len, GriddedRegion region) {		
		FaultPolyMgr mgr = new FaultPolyMgr();
		if (log) System.out.println("Building poly mgr...");
		if (log) System.out.println("   getting faults from model");
		List<FaultSection> faults = fm.fetchFaultSections();
		if (log) System.out.println("   subsection polygons");
		mgr.region = region;
		mgr.polys = SectionPolygons.create(faults, buf, len);
		mgr.init();
		return mgr;
	}
	
	/**
	 * Create a fault polygon manager from a FaultModel and a desired
	 * fault-section length (for subdividing faults in model). 
	 * GriddedRegion is set to CaliforniaRegions.RELM_TESTING_GRIDDED()
	 * 
	 * @param fm {@code FaultModel} to initialize manager with
	 * @param len fault-section length for subdividing
	 * @param buf additional buffer around fault trace to include in polygon in
	 *        km on either side of fault; may be {@code null}
	 * @return a reference to the newly minted manager
	 */
	public static FaultPolyMgr create(FaultModels fm, Double buf, Double len) {		
		GriddedRegion region = new CaliforniaRegions.RELM_TESTING_GRIDDED();
		return create(fm, buf, len, region);
	}	
	
	private void init() {
		if (log) System.out.println("   section:node map");
		initSectionToProbableNodes();
		if (log) System.out.println("   node area cache");
		initNodeAreas();
		if (log) System.out.println("   section extents");
		initSectionExtents();
		if (log) System.out.println("   section participation");
		initSectInNodeParticipTable();
		if (log) System.out.println("   update node cache");
		updateNodeAreas();
		if (log) System.out.println("   node participation");
		initNodeInSectParticipTable();
		if (log) System.out.println("   node participation");
		initNodeParticipation();
		if (log) System.out.println("   update section participation");
		updateParticipationTable();
		if (log) System.out.println("   Done.");
	}
	
	/*
	 * Initializes a multimap of all nodes each section MAY intersect. This is
	 * done to prevent looping all nodes and all subsections.
	 */
	private void initSectionToProbableNodes() {
		sectToProbNodes = ArrayListMultimap.create();
		for (Integer id : polys.indices()) {
			Area poly = polys.get(id);
			List<Integer> indices = null;
			if (poly != null) {
				indices = region.indicesForBounds(poly.getBounds2D());
				sectToProbNodes.putAll(id, indices);
			}
		}
	}
	
	/*
	 * Initializes a lookup table for all PROBABLE areas intersected by fault
	 * subsections. Some will not have any intersections.
	 */
	private void initNodeAreas() {
		nodeAreas = Maps.newHashMap();
		nodeExtents = Maps.newHashMap();
		Set<Integer> nodeIdxs = Sets.newHashSet(sectToProbNodes.values());
		for (Integer nodeIdx : nodeIdxs) {
			Area nodeArea = region.areaForIndex(nodeIdx);
			double nodeExtent = SectionPolygons.getExtent(nodeArea);
			nodeAreas.put(nodeIdx, nodeArea);
			nodeExtents.put(nodeIdx, nodeExtent);
		}
	}
	
	/*
	 * Initializes a map of the total area covered by each fault section.
	 */
	private void initSectionExtents() {
		sectExtents = Maps.newHashMap();
		for (Integer id : polys.indices()) {
			Area area = polys.get(id);
			double extent = (area == null) ? 0.0 : SectionPolygons
				.getExtent(area);
			sectExtents.put(id, extent);
		}
	}
	
 	/*
	 * Initializes table of fault section participation in each node, i.e.
	 * the percent of a node's area covered by a fault section's polygon. In
	 * the process, the sect:node and node:sect maps are created. This is later
	 * revised to account for multiple overlapping sections in many nodes.
	 */
	private void initSectInNodeParticipTable() {
		sectToNodes = ArrayListMultimap.create();
		sectInNodePartic = HashBasedTable.create();
		for (Integer ssIdx : sectToProbNodes.keySet()) {
			Collection<Integer> nodeIdxs = sectToProbNodes.get(ssIdx);
			List<Integer> revisedIdxs = processFault(ssIdx, nodeIdxs);
			sectToNodes.putAll(ssIdx, revisedIdxs);
		}
		// create node:sect via inversion
		nodeToSects =  ArrayListMultimap.create();
		Multimaps.invertFrom(sectToNodes, nodeToSects);
	}
	
	private List<Integer> processFault(Integer ssIdx, Iterable<Integer> nodeIdxs) {
		List<Integer> newNodeIdxs = Lists.newArrayList();
		Area ssArea = polys.get(ssIdx); // should be singular
		for (Integer nodeIdx : nodeIdxs) {
			Area nodeArea = region.areaForIndex(nodeIdx); // should be singular
			nodeArea.intersect(ssArea);
			if (nodeArea.isEmpty()) continue; // no overlap; eliminate
			nodeArea = SectionPolygons.cleanBorder(nodeArea);
			double faultExtent = SectionPolygons.getExtent(nodeArea);
			double ratio = faultExtent / nodeExtents.get(nodeIdx);
			newNodeIdxs.add(nodeIdx);
			sectInNodePartic.put(ssIdx, nodeIdx, ratio);
		}
		return newNodeIdxs;
	}
	
	/*
	 * Initializes table of node participation in each section, i.e. the percent
	 * of a section's area present in each node it intersects. The participation
	 * values for a section sum to 1.
	 */
	private void initNodeInSectParticipTable() {
		nodeInSectPartic = HashBasedTable.create();
		for (Table.Cell<Integer, Integer, Double> cell : sectInNodePartic.cellSet()) {
			int sectIdx = cell.getRowKey();
			int nodeIdx = cell.getColumnKey();
			double sectExtent = sectExtents.get(sectIdx);
			double nodeExtent = nodeExtents.get(nodeIdx);
			double sectPartic = cell.getValue();
			// sectExtentInNode = nodeExtent * partic
			// nodePartic = sectExtentInNode / sectExtent
			double nodePartic = nodeExtent * sectPartic / sectExtent;
			nodeInSectPartic.put(sectIdx, nodeIdx, nodePartic);
		}
	}
	
	/*
	 * Once the true section:node mapping is established, revise node area
	 * caches.
	 */
	private void updateNodeAreas() {
		Set<Integer> allIdxs = Sets.newHashSet(sectToProbNodes.values());
		Set<Integer> goodIdxs = Sets.newHashSet(sectToNodes.values());
		Set<Integer> badIdxs = Sets.difference(allIdxs, goodIdxs);
		nodeAreas.keySet().removeAll(badIdxs);
		nodeExtents.keySet().removeAll(badIdxs);
	}
	
	/*
	 * Initializes map of node participation, i.e. the percent of a node's
	 * extent covered by one or more fault sections. This modifies areas in the
	 * node:area map in place and updates extents in node:extent to their
	 * participating percent; former value was actual extent
	 */
	private void initNodeParticipation() {
		for (Integer nodeIdx : nodeToSects.keySet()) {
			Area nodeArea = nodeAreas.get(nodeIdx);
			double totalExtent = nodeExtents.get(nodeIdx);
			for (Integer sectIdx : nodeToSects.get(nodeIdx)) {
				nodeArea.subtract(polys.get(sectIdx));
			}
			nodeArea = SectionPolygons.cleanBorder(nodeArea);
			nodeAreas.put(nodeIdx, nodeArea);
			double nodeExtent = SectionPolygons.getExtent(nodeArea);
			nodeExtent = 1 - (nodeExtent / totalExtent);
			nodeExtents.put(nodeIdx, nodeExtent);			
		}
	}
	
	/*
	 * Updates table of section participation. Because numerous nodes intersect
	 * multiple overlapping faults, we scale the section participation in each
	 * node to the relative fraction of the total participating area in the
	 * node. So if S1=0.6, S2=0.4, and S3=0.2 and in agreggate these three
	 * sections cover 60% of the node, then they are scaled to S1=0.3, S2=0.2, 
	 * and S3=0.1
	 */
	private void updateParticipationTable() {
		// sum of section participations in node
		for (Integer nodeIdx : sectInNodePartic.columnKeySet()) {
			Map<Integer, Double> sects = sectInNodePartic.column(nodeIdx);
			double totalPartic = sum(sects.values());
			double nodePartic = nodeExtents.get(nodeIdx);
			for (Integer sectIdx : sects.keySet()) {
				// scaled value
				double val = (sects.get(sectIdx) / totalPartic) * nodePartic;
				sects.put(sectIdx, val);
			}
		}
	}
	
	private static double sum(Iterable<Double> values) {
		double sum = 0;
		for (Double v : values) {
			sum += v;
		}
		return sum;
	}
	
	
	/**
	 * NOTE: Only for use when working with fault models.
	 * @param model fault model to use
	 * @param buf polygon buffer (km on either side of fault) to use
	 * @param len to use when subdividing faults into sections
	 * @return the fraction of each node 
	 */
	public static double[] getNodeFractions(FaultModels model, Double buf,
			Double len) {
		if (model == null) model = FaultModels.FM3_1;
		if (len == null) len = 7d;
		FaultPolyMgr mgr = create(model, buf, len);
		double[] values = new double[mgr.region.getNodeCount()];
		for (int i = 0; i < mgr.region.getNodeCount(); i++) {
			values[i] = mgr.getNodeFraction(i);
		}
		return values;
	}	
	
	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		//FaultPolyMgr mgr = new FaultPolyMgr(FaultModels.FM3_1, 7);
		
		File outputDir = new File("/tmp/fm_poly");
		outputDir.mkdir();
		for (FaultModels fm : new FaultModels[] {FaultModels.FM3_1, FaultModels.FM3_2}) {
			String fName = fm.name().toLowerCase()+"_ucerf3.zip";
			File file = new File("/home/kevin/OpenSHA/UCERF4/rup_sets", fName);
			FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(file);
			FaultPolyMgr mgr = create(rupSet.getFaultSectionDataList(), InversionTargetMFDs.FAULT_BUFFER);
			
			File outputFile = new File(outputDir, fm.name().toLowerCase()+"_sect_polygons.geojson");
			List<Feature> features = new ArrayList<>();
			for (int id : mgr.indices()) {
				Region polygon = mgr.getPoly(id);
				Feature feature = polygon.toFeature();
				feature = new Feature(id, feature.geometry, feature.properties);
				features.add(feature);
			}
			FeatureCollection.write(new FeatureCollection(features), outputFile);
		}
		
//		System.out.println(Arrays.toString(getNodeFractions(FaultModels.FM3_1, 0d, 7d)));
//		mgr.sectInNodePartic
//		mgr.nodeInSectPartic
		
//		for (Integer sectIdx : mgr.nodeInSectPartic.rowKeySet()) {
//			System.out.println(sum(mgr.nodeInSectPartic.row(sectIdx).values()));
//		}
//		System.out.println(mgr.nodeInSectPartic);
//		
//		for (Integer sectIdx : mgr.nodeInSectPartic.rowKeySet()) {
//			Map<Integer, Double> nodeVals = mgr.nodeInSectPartic.row(sectIdx);
//			System.out.println(sum(nodeVals.values()));
//		}
	}

	
}
