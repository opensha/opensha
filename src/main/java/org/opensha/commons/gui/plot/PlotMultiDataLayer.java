package org.opensha.commons.gui.plot;

import java.util.List;

import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;

import com.google.common.collect.Lists;

/**
 * This class is for adding multiple XY lines without cluttering the dataset list, such as for separators.
 * 
 * All lines will be plotted with the same plot curve characteristics
 * @author kevin
 *
 */
public class PlotMultiDataLayer implements PlotElement {
	
	private XY_DataSetList datas;
	private String info = "Multi XY Plot Layer";
	
	public PlotMultiDataLayer() {
		this(new XY_DataSetList());
	}
	
	public PlotMultiDataLayer(XY_DataSetList datas) {
		this.datas = datas;
	}
	
	public void add(XY_DataSet data) {
		this.datas.add(data);
	}
	
	public void addVerticalLine(double x, double y1, double y2) {
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		xy.set(x, y1);
		xy.set(x, y2);
		add(xy);
	}
	
	public void addHorizontalLine(double x1, double x2, double y) {
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		xy.set(x1, y);
		xy.set(x2, y);
		add(xy);
	}
	
	public void setInfo(String info) {
		this.info = info;
	}

	@Override
	public String getInfo() {
		return info;
	}

	@Override
	public XY_DataSetList getDatasetsToPlot() {
		return datas;
	}

	@Override
	public List<Integer> getPlotNumColorList() {
		List<Integer> ints = Lists.newArrayList();
		ints.add(datas.size());
		return ints;
	}

}
