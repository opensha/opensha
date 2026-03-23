package org.opensha.commons.data.xyzw;

import java.util.Arrays;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.function.UnmodifiableDiscrFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;

import com.google.common.base.Preconditions;

public class GriddedGeoDepthValueDataSet implements GeoXYZW_DataSet {

	private final GriddedRegion griddedRegion;
	private final DiscretizedFunc depthFunction;

	private float minSnapDepth, maxSnapDepth;
	private boolean snapDepths = true;

	private final int nodeCount;
	private final int depthCount;

	private double[][] values;

	public GriddedGeoDepthValueDataSet(GriddedRegion griddedRegion, double[] depths) {
		this(griddedRegion, new LightFixedXFunc(depths, new double[depths.length]));
	}

	public GriddedGeoDepthValueDataSet(GriddedRegion griddedRegion, DiscretizedFunc depthFunction) {
		this(griddedRegion, depthFunction, null);
	}

	public GriddedGeoDepthValueDataSet(GriddedRegion griddedRegion, DiscretizedFunc depthFunction, double[][] values) {
		this.griddedRegion = griddedRegion;
		this.depthFunction = depthFunction;
		if (values == null) {
			values = new double[griddedRegion.getNodeCount()][depthFunction.size()];
		} else {
			Preconditions.checkState(values.length == griddedRegion.getNodeCount(),
					"Expected %s rows for gridded region, have %s", griddedRegion.getNodeCount(), values.length);
			for (int i=0; i<values.length; i++)
				Preconditions.checkState(values[i].length == depthFunction.size(),
						"Expected %s depth values for row %s, have %s", depthFunction.size(), i, values[i].length);
		}
		this.values = values;
		if (!(depthFunction instanceof EvenlyDiscretizedFunc))
			// so that x values can't change
			depthFunction = new UnmodifiableDiscrFunc(depthFunction);
		if (depthFunction.size() == 1) {
			minSnapDepth = (float)(depthFunction.getX(0) - depthFunction.getTolerance());
			maxSnapDepth = (float)(depthFunction.getX(0) + depthFunction.getTolerance());
		} else if (depthFunction instanceof EvenlyDiscretizedFunc) {
			EvenlyDiscretizedFunc eFunc = (EvenlyDiscretizedFunc)depthFunction;
			minSnapDepth = (float)(eFunc.getMinX() - 0.5*eFunc.getDelta() - depthFunction.getTolerance());
			maxSnapDepth = (float)(eFunc.getMaxX() + 0.5*eFunc.getDelta() + depthFunction.getTolerance());
		} else {
			double delta = depthFunction.getX(1) - depthFunction.getX(0);
			minSnapDepth = (float)(depthFunction.getMinX() - 0.5*delta - depthFunction.getTolerance());
			delta = depthFunction.getX(depthFunction.size()-1) - depthFunction.getX(depthFunction.size()-2);
			maxSnapDepth = (float)(depthFunction.getMaxX() + 0.5*delta + depthFunction.getTolerance());
		}
		nodeCount = griddedRegion.getNodeCount();
		depthCount = depthFunction.size();
	}
	
	public GriddedRegion getRegion() {
		return griddedRegion;
	}
	
	public DiscretizedFunc getDepthDiscretization() {
		return depthFunction;
	}
	
	public enum DepthRediscretizationMethod {
		INTERPOLATE,
		PRESERVE_SUM,
		CLOSEST
	}

	public GriddedGeoDepthValueDataSet rediscretizeDepths(double[] depths, DepthRediscretizationMethod method) {
		return rediscretizeDepths(new LightFixedXFunc(depths, new double[depths.length]), method);
	}

	public GriddedGeoDepthValueDataSet rediscretizeDepths(DiscretizedFunc newDepthFunction,
			DepthRediscretizationMethod method) {
		Preconditions.checkNotNull(newDepthFunction, "New depth function cannot be null");
		Preconditions.checkNotNull(newDepthFunction, "No depth discretization method supplied");
		int newDepthCount = newDepthFunction.size();
		double[][] newValues = new double[nodeCount][newDepthCount];
		switch (method) {
		case PRESERVE_SUM:
			double[] oldEdges = calcDepthBinEdges(depthFunction);
			double[] newEdges = calcDepthBinEdges(newDepthFunction);
			for (int nodeIndex=0; nodeIndex<nodeCount; nodeIndex++)
				for (int newDepthIndex=0; newDepthIndex<newDepthCount; newDepthIndex++)
					newValues[nodeIndex][newDepthIndex] = redistributeValue(nodeIndex, newDepthIndex, oldEdges, newEdges);
			break;
		case INTERPOLATE:
			for (int nodeIndex=0; nodeIndex<nodeCount; nodeIndex++) {
				DiscretizedFunc nodeFunc = buildNodeDepthFunc(nodeIndex);
				for (int newDepthIndex=0; newDepthIndex<newDepthCount; newDepthIndex++) {
					double depth = newDepthFunction.getX(newDepthIndex);
					newValues[nodeIndex][newDepthIndex] = interpolateValue(nodeFunc, depth);
				}
			}
			break;
		case CLOSEST:
			for (int nodeIndex=0; nodeIndex<nodeCount; nodeIndex++) {
				for (int newDepthIndex=0; newDepthIndex<newDepthCount; newDepthIndex++) {
					int oldDepthIndex = depthFunction.getClosestXIndex(newDepthFunction.getX(newDepthIndex));
					newValues[nodeIndex][newDepthIndex] = values[nodeIndex][oldDepthIndex];
				}
			}
			break;

		default:
			throw new IllegalStateException("Unsupported depth rediscretization method: " + method);
		}
		GriddedGeoDepthValueDataSet ret = new GriddedGeoDepthValueDataSet(griddedRegion, newDepthFunction, newValues);
		ret.setSnapDepths(snapDepths);
		return ret;
	}

	public void setSnapDepths(boolean snapDepths) {
		this.snapDepths = snapDepths;
	}

	public boolean isSnapDepths() {
		return snapDepths;
	}

	public int getDepthCount() {
		return depthCount;
	}

	private DiscretizedFunc buildNodeDepthFunc(int nodeIndex) {
		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
		func.setTolerance(depthFunction.getTolerance());
		for (int depthIndex=0; depthIndex<getDepthCount(); depthIndex++)
			func.set(depthFunction.getX(depthIndex), values[nodeIndex][depthIndex]);
		return func;
	}

	private static double interpolateValue(DiscretizedFunc func, double depth) {
		if (func.size() == 0)
			return Double.NaN;
		if (depth <= func.getMinX())
			return func.getY(0);
		if (depth >= func.getMaxX())
			return func.getY(func.size()-1);
		return func.getInterpolatedY(depth);
	}

	private static double[] calcDepthBinEdges(DiscretizedFunc func) {
		int size = func.size();
		double[] edges = new double[size+1];
		Preconditions.checkState(size > 0, "Depth function must have at least one point");
		if (size == 1) {
			double halfWidth = Math.max(func.getTolerance(), 0d);
			edges[0] = func.getX(0) - halfWidth;
			edges[1] = func.getX(0) + halfWidth;
			return edges;
		}
		edges[0] = func.getX(0) - 0.5*(func.getX(1) - func.getX(0));
		for (int i=1; i<size; i++)
			edges[i] = 0.5*(func.getX(i-1) + func.getX(i));
		edges[size] = func.getX(size-1) + 0.5*(func.getX(size-1) - func.getX(size-2));
		return edges;
	}

	private double redistributeValue(int nodeIndex, int newDepthIndex, double[] oldEdges, double[] newEdges) {
		double newStart = newEdges[newDepthIndex];
		double newEnd = newEdges[newDepthIndex+1];
		double sum = 0d;
		for (int oldDepthIndex=0; oldDepthIndex<getDepthCount(); oldDepthIndex++) {
			double oldStart = oldEdges[oldDepthIndex];
			double oldEnd = oldEdges[oldDepthIndex+1];
			double overlap = Math.min(oldEnd, newEnd) - Math.max(oldStart, newStart);
			if (overlap > 0d) {
				double oldWidth = oldEnd - oldStart;
				if (oldWidth > 0d)
					sum += values[nodeIndex][oldDepthIndex] * overlap / oldWidth;
			}
		}
		return sum;
	}

	public int getNodeIndex(double x, double y) {
		return griddedRegion.indexForLocation(new Location(y, x));
	}

	public int getNodeIndex(Location loc) {
		return griddedRegion.indexForLocation(loc);
	}

	private int getNodeIndexForFlatIndex(int index) {
		Preconditions.checkElementIndex(index, size());
		return index / getDepthCount();
	}

	private int getDepthIndexForFlatIndex(int index) {
		Preconditions.checkElementIndex(index, size());
		return index % getDepthCount();
	}

	@Override
	public double getMinX() {
		return griddedRegion.getMinGridLon();
	}

	@Override
	public double getMaxX() {
		return griddedRegion.getMaxGridLon();
	}

	@Override
	public double getMinY() {
		return griddedRegion.getMinGridLat();
	}

	@Override
	public double getMaxY() {
		return griddedRegion.getMaxGridLat();
	}

	@Override
	public double getMinDepth() {
		return depthFunction.getMinX();
	}

	@Override
	public double getMaxDepth() {
		return depthFunction.getMaxX();
	}

	@Override
	public double getMinLat() {
		return griddedRegion.getMinGridLat();
	}

	@Override
	public double getMaxLat() {
		return griddedRegion.getMaxGridLat();
	}

	@Override
	public double getMinLon() {
		return griddedRegion.getMinGridLon();
	}

	@Override
	public double getMaxLon() {
		return griddedRegion.getMaxGridLon();
	}

	@Override
	public double getMinValue() {
		double min = Double.POSITIVE_INFINITY;
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				min = Math.min(min, values[i][j]);
		return min;
	}

	@Override
	public double getMaxValue() {
		double max = Double.NEGATIVE_INFINITY;
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				max = Math.max(max, values[i][j]);
		return max;
	}

	@Override
	public double getSumValues() {
		double sum = 0d;
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				sum += values[i][j];
		return sum;
	}

	public int getDepthIndex(double depth) {
		if ((float)depth < minSnapDepth || (float)depth > maxSnapDepth)
			return -1;
		if (snapDepths)
			return depthFunction.getClosestXIndex(depth);
		return depthFunction.getXIndex(depth);
	}

	@Override
	public void set(double x, double y, double depth, double value) {
		int nodeIndex = getNodeIndex(x, y);
		Preconditions.checkState(nodeIndex >= 0, "Location (%s, %s) is not in the gridded region", y, x);
		int depthIndex = getDepthIndex(depth);
		Preconditions.checkState(depthIndex >= 0, "Depth %s is not in the discretized depth function", depth);
		values[nodeIndex][depthIndex] = value;
	}

	@Override
	public void set(int index, double value) {
		values[getNodeIndexForFlatIndex(index)][getDepthIndexForFlatIndex(index)] = value;
	}

	public void set(int nodeIndex, int depthIndex, double value) {
		Preconditions.checkState(nodeIndex >= 0 && nodeIndex < nodeCount, "Bad nodeIndex=%s", nodeIndex);
		Preconditions.checkState(depthIndex >= 0 && depthIndex < depthCount, "Bad depthIndex=%s", depthIndex);
		values[nodeIndex][depthIndex] = value;
	}

	@Override
	public void set(Location loc, double value) {
		int nodeIndex = getNodeIndex(loc);
		Preconditions.checkState(nodeIndex >= 0, "Location (%s) is not in the gridded region", loc);
		int depthIndex = getDepthIndex(loc.depth);
		Preconditions.checkState(depthIndex >= 0, "Depth %s is not in the discretized depth function", loc.depth);
		values[nodeIndex][depthIndex] = value;
	}

	@Override
	public void add(double x, double y, double depth, double value) {
		int nodeIndex = getNodeIndex(x, y);
		Preconditions.checkState(nodeIndex >= 0, "Location (%s, %s) is not in the gridded region", y, x);
		int depthIndex = getDepthIndex(depth);
		Preconditions.checkState(depthIndex >= 0, "Depth %s is not in the discretized depth function", depth);
		values[nodeIndex][depthIndex] += value;
	}

	@Override
	public void add(int index, double value) {
		int nodeIndex = getNodeIndexForFlatIndex(index);
		int depthIndex = getDepthIndexForFlatIndex(index);
		values[nodeIndex][depthIndex] += value;
	}

	public void add(int nodeIndex, int depthIndex, double value) {
		Preconditions.checkState(nodeIndex >= 0 && nodeIndex < nodeCount, "Bad nodeIndex=%s", nodeIndex);
		Preconditions.checkState(depthIndex >= 0 && depthIndex < depthCount, "Bad depthIndex=%s", depthIndex);
		values[nodeIndex][depthIndex] += value;
	}

	@Override
	public void add(Location loc, double value) {
		int nodeIndex = getNodeIndex(loc);
		Preconditions.checkState(nodeIndex >= 0, "Location (%s) is not in the gridded region", loc);
		int depthIndex = getDepthIndex(loc.depth);
		Preconditions.checkState(depthIndex >= 0, "Depth %s is not in the discretized depth function", loc.depth);
		values[nodeIndex][depthIndex] += value;
	}

	@Override
	public double get(double x, double y, double depth) {
		int nodeIndex = getNodeIndex(x, y);
		Preconditions.checkState(nodeIndex >= 0, "Location (%s, %s) is not in the gridded region", y, x);
		int depthIndex = getDepthIndex(depth);
		Preconditions.checkState(depthIndex >= 0, "Depth %s is not in the discretized depth function", depth);
		return values[nodeIndex][depthIndex];
	}

	@Override
	public double get(Location loc) {
		return get(loc.getLongitude(), loc.getLatitude(), loc.getDepth());
	}

	@Override
	public double get(int index) {
		return values[getNodeIndexForFlatIndex(index)][getDepthIndexForFlatIndex(index)];
	}

	public double get(int nodeIndex, int depthIndex) {
		Preconditions.checkState(nodeIndex >= 0 && nodeIndex < nodeCount, "Bad nodeIndex=%s", nodeIndex);
		Preconditions.checkState(depthIndex >= 0 && depthIndex < depthCount, "Bad depthIndex=%s", depthIndex);
		return values[nodeIndex][depthIndex];
	}

	@Override
	public double getX(int index) {
		return griddedRegion.locationForIndex(getNodeIndexForFlatIndex(index)).getLongitude();
	}

	@Override
	public double getY(int index) {
		return griddedRegion.locationForIndex(getNodeIndexForFlatIndex(index)).getLatitude();
	}

	@Override
	public double getDepth(int index) {
		return depthFunction.getX(getDepthIndexForFlatIndex(index));
	}

	@Override
	public int indexOf(double x, double y, double depth) {
		int nodeIndex = getNodeIndex(x, y);
		if (nodeIndex < 0)
			return -1;
		int depthIndex = getDepthIndex(depth);
		if (depthIndex < 0)
			return -1;
		return nodeIndex * getDepthCount() + depthIndex;
	}

	@Override
	public int indexOf(Location loc) {
		if (loc == null)
			return -1;
		return indexOf(loc.getLongitude(), loc.getLatitude(), loc.getDepth());
	}

	@Override
	public boolean contains(double x, double y, double depth) {
		return contains(new Location(y, x, depth));
	}

	@Override
	public boolean contains(Location loc) {
		return loc != null && getNodeIndex(loc) >= 0 && getDepthIndex(loc.getDepth()) >= 0;
	}

	@Override
	public Location getLocation(int index) {
		int nodeIndex = getNodeIndexForFlatIndex(index);
		Location gridLoc = griddedRegion.locationForIndex(nodeIndex);
		return new Location(gridLoc.getLatitude(), gridLoc.getLongitude(), getDepth(index));
	}

	@Override
	public int size() {
		return nodeCount * depthCount;
	}

	@Override
	public void setAll(XYZW_DataSet dataset) {
		for (int i=0; i<dataset.size(); i++)
			set(dataset.getX(i), dataset.getY(i), dataset.getDepth(i), dataset.get(i));
	}

	@Override
	public LocationList getLocationList() {
		LocationList locs = new LocationList(size());
		for (int i=0; i<size(); i++)
			locs.add(getLocation(i));
		return locs;
	}

	@Override
	public GriddedGeoDepthValueDataSet copy() {
		double[][] valCopy = new double[nodeCount][];
		for (int i=0; i<valCopy.length; i++)
			valCopy[i] = Arrays.copyOf(values[i], values[i].length);
		GriddedGeoDepthValueDataSet copy = new GriddedGeoDepthValueDataSet(griddedRegion, depthFunction, valCopy);
		copy.setSnapDepths(snapDepths);
		return copy;
	}

	@Override
	public void abs() {
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				values[i][j] = Math.abs(values[i][j]);
	}

	@Override
	public void log() {
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				values[i][j] = Math.log(values[i][j]);
	}

	@Override
	public void log10() {
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				values[i][j] = Math.log10(values[i][j]);
	}

	@Override
	public void exp() {
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				values[i][j] = Math.exp(values[i][j]);
	}

	@Override
	public void exp(double base) {
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				values[i][j] = Math.pow(base, values[i][j]);
	}

	@Override
	public void pow(double pow) {
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				values[i][j] = Math.pow(values[i][j], pow);
	}

	@Override
	public void scale(double scalar) {
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				values[i][j] *= scalar;
	}

	@Override
	public void add(double value) {
		for (int i=0; i<values.length; i++)
			for (int j=0; j<values[i].length; j++)
				values[i][j] += value;
	}
	
	public GriddedGeoDataSet sum2D() {
		GriddedGeoDataSet sum = new GriddedGeoDataSet(griddedRegion);
		for (int l=0; l<sum.size(); l++) {
			double depthSum = 0d;
			for (int d=0; d<depthCount; d++)
				depthSum += get(l, d);
			sum.set(l, depthSum);
		}
		return sum;
	}
}
