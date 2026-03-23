package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

/**
 * Spatial index for fast "all fault sections within X km" queries. Uses a quadtree built from fault
 * section bounding boxes (derived from fault traces). Queries expand the search envelope by the
 * requested distance, traverse the tree to collect candidates, then refine with exact surface
 * distance via {@link SectionDistanceAzimuthCalculator}.
 */
public class SectionProximityIndex {

    private static final int MAX_LEAF = 16;
    private static final int MAX_DEPTH = 12;
    private static final double KM_PER_DEG_LAT = 111.0;

    private final SectionDistanceAzimuthCalculator distCalc;
    private final double[][] envelopes; // [sectionId][minLat, maxLat, minLon, maxLon]
    private final int totalSections; // total number of sections (envelope array size)
    private final int[] indexedIds; // section IDs that are in the tree
    private final Node root;

    /**
     * Creates an index where all sections from the calculator are indexed.
     *
     * @param distCalc calculator providing sections and distance computation
     */
    public SectionProximityIndex(SectionDistanceAzimuthCalculator distCalc) {
        this(distCalc, distCalc.getSubSections());
    }

    /**
     * Creates an index where only the given sections are indexed into the tree. Envelopes are
     * computed for all sections known to the calculator (so any section can be used as a query),
     * but only {@code indexedSections} appear as results.
     *
     * @param distCalc calculator providing distance computation for all sections
     * @param indexedSections sections to insert into the tree
     */
    public SectionProximityIndex(
            SectionDistanceAzimuthCalculator distCalc,
            List<? extends FaultSection> indexedSections) {
        this.distCalc = distCalc;
        List<? extends FaultSection> allSects = distCalc.getSubSections();
        this.totalSections = allSects.size();
        this.envelopes = computeEnvelopes(allSects);
        this.indexedIds = new int[indexedSections.size()];
        for (int i = 0; i < indexedSections.size(); i++) {
            indexedIds[i] = indexedSections.get(i).getSectionId();
        }
        double[] rootBounds = computeRootBounds(indexedIds);
        this.root = buildTree(rootBounds, indexedIds, 0);
    }

    /**
     * Returns IDs of all indexed sections within {@code maxDistKm} of the given section. The query
     * section does not need to be indexed itself.
     */
    public List<Integer> findWithin(int sectionId, double maxDistKm) {
        List<Integer> result = new ArrayList<>();
        if (maxDistKm <= 0) {
            return result;
        }

        double[] env = envelopes[sectionId];
        double midLat = (env[0] + env[1]) / 2.0;
        double bufLat = maxDistKm / KM_PER_DEG_LAT;
        double bufLon = maxDistKm / (KM_PER_DEG_LAT * Math.cos(Math.toRadians(midLat)));
        double[] queryEnv = {env[0] - bufLat, env[1] + bufLat, env[2] - bufLon, env[3] + bufLon};

        BitSet candidates = new BitSet(totalSections);
        search(root, queryEnv, candidates);
        candidates.clear(sectionId);

        for (int id = candidates.nextSetBit(0); id >= 0; id = candidates.nextSetBit(id + 1)) {
            if (distCalc.getDistance(sectionId, id) <= maxDistKm) {
                result.add(id);
            }
        }
        return result;
    }

    /**
     * Builds adjacency lists for all sections at the given threshold. Returns {@code int[][]}
     * indexed by section ID.
     */
    /**
     * Builds adjacency lists for all indexed sections at the given threshold. Returns a list
     * parallel to the indexed sections, where each entry contains the neighbor IDs.
     */
    public int[][] buildAdjacency(double maxDistKm) {
        int[][] adj = new int[indexedIds.length][];
        for (int i = 0; i < indexedIds.length; i++) {
            List<Integer> neighbors = findWithin(indexedIds[i], maxDistKm);
            adj[i] = new int[neighbors.size()];
            for (int j = 0; j < neighbors.size(); j++) {
                adj[i][j] = neighbors.get(j);
            }
        }
        return adj;
    }

    /** Returns the section IDs that are indexed in this tree. */
    public int[] getIndexedIds() {
        return indexedIds;
    }

    // --- tree implementation ---

    private abstract static class Node {
        final double minLat, maxLat, minLon, maxLon;

        Node(double minLat, double maxLat, double minLon, double maxLon) {
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
        }
    }

    private static class LeafNode extends Node {
        final int[] sectionIds;

        LeafNode(double minLat, double maxLat, double minLon, double maxLon, int[] sectionIds) {
            super(minLat, maxLat, minLon, maxLon);
            this.sectionIds = sectionIds;
        }
    }

    private static class BranchNode extends Node {
        // [0]=SW, [1]=SE, [2]=NW, [3]=NE; null if empty
        final Node[] children = new Node[4];

        BranchNode(double minLat, double maxLat, double minLon, double maxLon) {
            super(minLat, maxLat, minLon, maxLon);
        }
    }

    private double[][] computeEnvelopes(List<? extends FaultSection> sects) {
        double[][] envs = new double[sects.size()][4];
        for (FaultSection sect : sects) {
            int id = sect.getSectionId();
            FaultTrace trace = sect.getFaultTrace();
            double minLat = Double.MAX_VALUE;
            double maxLat = -Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE;
            double maxLon = -Double.MAX_VALUE;
            for (Location loc : trace) {
                minLat = Math.min(minLat, loc.getLatitude());
                maxLat = Math.max(maxLat, loc.getLatitude());
                minLon = Math.min(minLon, loc.getLongitude());
                maxLon = Math.max(maxLon, loc.getLongitude());
            }
            envs[id] = new double[] {minLat, maxLat, minLon, maxLon};
        }
        return envs;
    }

    private double[] computeRootBounds(int[] ids) {
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        for (int id : ids) {
            double[] env = envelopes[id];
            minLat = Math.min(minLat, env[0]);
            maxLat = Math.max(maxLat, env[1]);
            minLon = Math.min(minLon, env[2]);
            maxLon = Math.max(maxLon, env[3]);
        }
        return new double[] {minLat, maxLat, minLon, maxLon};
    }

    private Node buildTree(double[] bounds, int[] ids, int depth) {
        if (ids.length <= MAX_LEAF || depth >= MAX_DEPTH) {
            return new LeafNode(bounds[0], bounds[1], bounds[2], bounds[3], ids);
        }

        double midLat = (bounds[0] + bounds[1]) / 2.0;
        double midLon = (bounds[2] + bounds[3]) / 2.0;

        List<Integer> sw = new ArrayList<>();
        List<Integer> se = new ArrayList<>();
        List<Integer> nw = new ArrayList<>();
        List<Integer> ne = new ArrayList<>();

        for (int id : ids) {
            double[] env = envelopes[id];
            boolean south = env[0] <= midLat;
            boolean north = env[1] > midLat;
            boolean west = env[2] <= midLon;
            boolean east = env[3] > midLon;

            if (south && west) sw.add(id);
            if (south && east) se.add(id);
            if (north && west) nw.add(id);
            if (north && east) ne.add(id);
        }

        // If no quadrant reduced the count, stop splitting
        if (sw.size() == ids.length
                && se.size() == ids.length
                && nw.size() == ids.length
                && ne.size() == ids.length) {
            return new LeafNode(bounds[0], bounds[1], bounds[2], bounds[3], ids);
        }

        BranchNode branch = new BranchNode(bounds[0], bounds[1], bounds[2], bounds[3]);
        branch.children[0] = buildChild(bounds[0], midLat, bounds[2], midLon, sw, depth);
        branch.children[1] = buildChild(bounds[0], midLat, midLon, bounds[3], se, depth);
        branch.children[2] = buildChild(midLat, bounds[1], bounds[2], midLon, nw, depth);
        branch.children[3] = buildChild(midLat, bounds[1], midLon, bounds[3], ne, depth);
        return branch;
    }

    private Node buildChild(
            double minLat,
            double maxLat,
            double minLon,
            double maxLon,
            List<Integer> ids,
            int depth) {
        if (ids.isEmpty()) {
            return null;
        }
        int[] arr = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            arr[i] = ids.get(i);
        }
        return buildTree(new double[] {minLat, maxLat, minLon, maxLon}, arr, depth + 1);
    }

    private void search(Node node, double[] queryEnv, BitSet candidates) {
        if (node == null) {
            return;
        }
        if (!intersects(node, queryEnv)) {
            return;
        }
        if (node instanceof LeafNode) {
            for (int id : ((LeafNode) node).sectionIds) {
                candidates.set(id);
            }
        } else {
            BranchNode branch = (BranchNode) node;
            for (Node child : branch.children) {
                search(child, queryEnv, candidates);
            }
        }
    }

    private static boolean intersects(Node node, double[] queryEnv) {
        return node.maxLat >= queryEnv[0]
                && node.minLat <= queryEnv[1]
                && node.maxLon >= queryEnv[2]
                && node.minLon <= queryEnv[3];
    }
}
