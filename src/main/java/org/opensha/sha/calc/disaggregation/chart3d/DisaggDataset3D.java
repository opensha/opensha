package org.opensha.sha.calc.disaggregation.chart3d;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart3d.data.AbstractDataset3D;
import org.jfree.chart3d.data.category.CategoryDataset3D;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator.EpsilonCategories;
import org.opensha.sha.calc.disaggregation.DisaggregationPlotData;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

class DisaggDataset3D extends AbstractDataset3D implements CategoryDataset3D<EpsilonCategories, Double, Double>, Serializable {
	
	private List<EpsilonCategories> seriesKeys;
	private List<Double> rowKeys;
	private List<Double> columnKeys;
	private Map<Double, Integer> rowIndexMap;
	private Map<Double, Integer> columnIndexMap;
	private DisaggregationPlotData data;
	
	public DisaggDataset3D(DisaggregationPlotData data) {
		this.data = data;
		seriesKeys = List.of(EpsilonCategories.values());
		rowKeys = Doubles.asList(data.getDist_center());
		rowIndexMap = new HashMap<>(rowKeys.size());
		for (int i=0; i<rowKeys.size(); i++)
			rowIndexMap.put(rowKeys.get(i), i);
		// need to reverse magnitudes
		double[] magCenters = data.getMag_center();
		columnKeys = new ArrayList<>(magCenters.length);
		for (int i=magCenters.length; --i>=0;)
			columnKeys.add(magCenters[i]);
		columnIndexMap = new HashMap<>(columnKeys.size());
		for (int i=0; i<columnKeys.size(); i++)
			columnIndexMap.put(columnKeys.get(i), i);
	}

	@Override
	public List<EpsilonCategories> getSeriesKeys() {
		return seriesKeys;
	}

	@Override
	public List<Double> getRowKeys() {
		return rowKeys;
	}

	@Override
	public List<Double> getColumnKeys() {
		return columnKeys;
	}

	@Override
	public EpsilonCategories getSeriesKey(int seriesIndex) {
		return seriesKeys.get(seriesIndex);
	}

	@Override
	public Double getRowKey(int rowIndex) {
		return rowKeys.get(rowIndex);
	}

	@Override
	public Double getColumnKey(int columnIndex) {
		return columnKeys.get(columnIndex);
	}

	@Override
	public int getSeriesIndex(EpsilonCategories serieskey) {
		return serieskey.ordinal();
	}

	@Override
	public int getRowIndex(Double rowkey) {
		return rowIndexMap.get(rowkey);
	}

	@Override
	public int getColumnIndex(Double columnkey) {
		return columnIndexMap.get(columnkey);
	}
	
	private int columnIndexToDataIndex(int columnIndex) {
		Preconditions.checkState(columnIndex >= 0 && columnIndex < columnKeys.size());
		return (columnKeys.size()-1) - columnIndex;
	}

	@Override
	public Number getValue(EpsilonCategories seriesKey, Double rowKey, Double columnKey) {
		int i = getRowIndex(rowKey);
		int j = columnIndexToDataIndex(getColumnIndex(columnKey));
		int k = seriesKey.ordinal();
		double val = data.getPdf3D()[i][j][k];
		if (val == 0d)
			return null;
		return val;
	}

	@Override
	public int getSeriesCount() {
		return seriesKeys.size();
	}

	@Override
	public int getRowCount() {
		return rowKeys.size();
	}

	@Override
	public int getColumnCount() {
		return columnKeys.size();
	}

	@Override
	public Number getValue(int seriesIndex, int rowIndex, int columnIndex) {
		double val = data.getPdf3D()[rowIndex][columnIndexToDataIndex(columnIndex)][seriesIndex];
		if (val == 0d)
			return null;
		return val;
	}

	@Override
	public double getDoubleValue(int seriesIndex, int rowIndex, int columnIndex) {
		double val = data.getPdf3D()[rowIndex][columnIndexToDataIndex(columnIndex)][seriesIndex];
		if (val == 0d)
			return Double.NaN;
		return val;
	}

}
