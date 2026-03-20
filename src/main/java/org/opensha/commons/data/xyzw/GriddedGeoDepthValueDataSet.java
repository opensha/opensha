package org.opensha.commons.data.xyzw;

import java.util.Arrays;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;

import com.google.common.base.Preconditions;

public class GeographicDepthValueDataSet implements GeoXYZW_DataSet {

	private GriddedRegion griddedRegion;
	private DiscretizedFunc depthFunction;

	private float minSnapDepth, maxSnapDepth;
	private boolean snapDepths = true;

	private double[][] values;

	public GeographicDepthValueDataSet(GriddedRegion griddedRegion, double[] depths) {
		this(griddedRegion, new LightFixedXFunc(depths, new double[depths.length]));
	}

	public GeographicDepthValueDataSet(GriddedRegion griddedRegion, DiscretizedFunc depthFunction) {
		this(griddedRegion, depthFunction, null);
	}

	public GeographicDepthValueDataSet(GriddedRegion griddedRegion, DiscretizedFunc depthFunction, double[][] values) {
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
	}

	public void setSnapDepths(boolean snapDepths) {
		this.snapDepths = snapDepths;
	}

	public boolean isSnapDepths() {
		return snapDepths;
	}

	private int getDepthCount() {
		return depthFunction.size();
	}

	private int getNodeIndex(double x, double y) {
		return griddedRegion.indexForLocation(new Location(y, x));
	}

	private int getNodeIndex(Location loc) {
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

	private int getDepthIndex(double depth) {
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
		return griddedRegion.getNodeCount() * getDepthCount();
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
	public GeographicDepthValueDataSet copy() {
		double[][] valCopy = new double[griddedRegion.getNodeCount()][];
		for (int i=0; i<valCopy.length; i++)
			valCopy[i] = Arrays.copyOf(values[i], values[i].length);
		GeographicDepthValueDataSet copy = new GeographicDepthValueDataSet(griddedRegion, depthFunction, valCopy);
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
}
