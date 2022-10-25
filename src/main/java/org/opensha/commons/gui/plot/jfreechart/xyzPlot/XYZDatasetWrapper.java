package org.opensha.commons.gui.plot.jfreechart.xyzPlot;

import org.jfree.data.xy.AbstractXYZDataset;
import org.opensha.commons.data.xyz.XYZ_DataSet;

public class XYZDatasetWrapper extends AbstractXYZDataset {
	
	private XYZ_DataSet xyz;
	private String zLabel;
	
	public XYZDatasetWrapper(XYZPlotSpec spec) {
		this(spec.getXYZ_Data(), spec.getZAxisLabel());
	}
	
	public XYZDatasetWrapper(XYZ_DataSet xyz, String zLabel) {
		this.xyz = xyz;
		this.zLabel = zLabel;
	}

	@Override
	public Number getZ(int series, int item) {
		return xyz.get(item);
	}

	@Override
	public int getItemCount(int series) {
		return xyz.size();
	}

	@Override
	public Number getX(int series, int item) {
		return xyz.getPoint(item).getX();
	}

	@Override
	public Number getY(int series, int item) {
		return xyz.getPoint(item).getY();
	}

	@Override
	public int getSeriesCount() {
		return 1;
	}

	@Override
	public Comparable getSeriesKey(int arg0) {
		return zLabel;
	}
	
}