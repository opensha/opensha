package org.opensha.sha.imr.mod.impl.stewartSiteSpecific;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.imr.mod.impl.stewartSiteSpecific.NonErgodicSiteResponseMod.Params;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

public class PeriodDependentParamSet<E extends Enum<E>> {
	
	private E[] params;
	private Map<E, Integer> paramToIndexMap;
	
	// sorted, increasing
	private List<Double> periods;
	private List<double[]> values;
	
	public PeriodDependentParamSet(E[] params) {
		Preconditions.checkArgument(params.length > 0);
		
		this.params = params;
		
		paramToIndexMap = Maps.newHashMap();
		for (int i=0; i<params.length; i++)
			paramToIndexMap.put(params[i], i);
		
		periods = Lists.newArrayList();
		values = Lists.newArrayList();
	}
	
	public synchronized void set(double period, double[] vals) {
		Preconditions.checkState(vals.length == params.length);
		
		int index = Collections.binarySearch(periods, period);
		if (index >= 0) {
			// we're replacing
			values.set(index, vals);
		} else {
			// we're adding
			index = -(index + 1);
			periods.add(index, period);
			values.add(index, vals);
		}
	}
	
	public synchronized void set(double period, E param, double value) {
		int periodIndex = Collections.binarySearch(periods, period);
		if (periodIndex < 0) {
			periodIndex = -(periodIndex + 1);
			periods.add(periodIndex, period);
			values.add(periodIndex, new double[params.length]);
		}
//		int periodIndex = periodToIndex(period);
//		Preconditions.checkState(periodIndex >= 0, "period not found: %s", period);
		
		set(periodIndex, param, value);
	}
	
	public synchronized void set(int periodIndex, E param, double value) {
		int paramIndex = paramToIndexMap.get(param);
		Preconditions.checkState(paramIndex >= 0, "param not found: %s", param);
		
		values.get(periodIndex)[paramIndex] = value;
	}
	
	public synchronized void remove(double period) {
		int index = periodToIndex(period);
		Preconditions.checkState(index >= 0, "period not found: %s", period);
		
		remove(index);
	}
	
	public synchronized void remove(int index) {
		Preconditions.checkState(index >= 0 && index < periods.size(), "bad index: %s", index);
		
		periods.remove(index);
		values.remove(index);
	}
	
	public synchronized void clear() {
		periods.clear();
		values.clear();
	}
	
	/**
	 * @return the number of periods
	 */
	public int size() {
		return periods.size();
	}
	
	public boolean isEmpty() {
		return size() == 0;
	}
	
	/**
	 * Gets a copy of the array of parameter enums.
	 * @return
	 */
	public E[] getParams() {
		return Arrays.copyOf(params, params.length);
	}
	
	/**
	 * Returns a sorted list of all current periods. This is an immutable view of the period list.
	 * @return
	 */
	public List<Double> getPeriods() {
		return Collections.unmodifiableList(periods);
	}
	
	public double getPeriod(int index) {
		return periods.get(index);
	}
	
	public int periodToIndex(double period) {
		return periods.indexOf(period);
	}
	
	/**
	 * Returns view of the values at the given period
	 * @param period
	 * @return
	 */
	public double[] getValues(double period) {
		int index = periodToIndex(period);
		Preconditions.checkState(index >= 0, "period not found: %s", period);
		return getValues(index);
	}
	
	/**
	 * Returns view of the values at the given index.
	 * 
	 * @param index
	 * @return
	 */
	public double[] getValues(int index) {
		int size = size();
		Preconditions.checkArgument(index >= 0 && index < size, "bad index: %s, size: %s", index, size);
		return Arrays.copyOf(values.get(index), params.length);
	}
	
	/**
	 * Returns view of the values for the given params at the given index.
	 * 
	 * @param index
	 * @return
	 */
	public double[] getValues(E[] params, int index) {
		int size = size();
		Preconditions.checkArgument(index >= 0 && index < size, "bad index: %s, size: %s", index, size);
		double[] ret = new double[params.length];
		for (int i=0; i<params.length; i++)
			ret[i] = get(params[i], index);
		return ret;
	}
	
	public int getParamIndex(E param) {
		return paramToIndexMap.get(param);
	}
	
	public double get(E param, double period) {
		int periodIndex = periodToIndex(period);
		Preconditions.checkState(periodIndex >= 0, "period not found: %s", period);
		return get(param, periodIndex);
	}
	
	public double get(E param, int periodIndex) {
		int paramIndex = paramToIndexMap.get(param);
		Preconditions.checkState(paramIndex >= 0, "param not found: %s", param);
		return values.get(periodIndex)[paramIndex];
	}
	
	public double[] get(E[] params, int periodIndex) {
		double[] ret = new double[params.length];
		for (int i=0; i<params.length; i++) {
			E param = params[i];
			int paramIndex = paramToIndexMap.get(param);
			Preconditions.checkState(paramIndex >= 0, "param not found: %s", param);
			ret[i] = values.get(periodIndex)[paramIndex];
		}
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	public double getInterpolated(E param, double period) {
		return doGetInterpolated(period, param)[0];
	}
	
	public double[] getInterpolated(E[] param, double period) {
		return doGetInterpolated(period, params);
	}
	
	private double[] doGetInterpolated(double period, E... params) {
		
		int periodIndex = Collections.binarySearch(periods, period);
		if (periodIndex >= 0)
			// exact match
			return get(params, periodIndex);
		// check for outside, in which case use first/last
		if (period < periods.get(0))
			return get(params, 0);
		if (period > periods.get(periods.size()-1))
			return get(params, periods.size()-1);
		
		// this means that it's not an exact match and is within min/max period
		int insertionIndex = -(periodIndex + 1);
		Preconditions.checkState(insertionIndex > 0 && insertionIndex < periods.size());
		
		double x1 = periods.get(insertionIndex-1);
		double x2 = periods.get(insertionIndex);
		
		double[] ret = new double[params.length];
		
		for (int i=0; i<params.length; i++) {
			double y1 = get(params[i], insertionIndex-1);
			double y2 = get(params[i], insertionIndex);
			
			if (x1 > 0)
				ret[i] = Interpolate.findY(Math.log(x1), y1, Math.log(x2), y2, Math.log(period));
			else
				// can happen when first point is period=0 (PGA)
				ret[i] = Interpolate.findY(x1, y1, x2, y2, period);
//			System.out.println("x1="+x1+", y1="+y1+", x2="+x2+", y2="+y2+", period="+period+", ret="+ret[i]);
		}
		
		return ret;
	}
	
	public static <E extends Enum<E>> PeriodDependentParamSet<E> loadCSV(E[] params, File csvFile) throws IOException {
		PeriodDependentParamSet<E> data = new PeriodDependentParamSet<E>(params);
		
		data.loadCSV(csvFile);
		
		return data;
	}
	
	public static <E extends Enum<E>> PeriodDependentParamSet<E> loadCSV(E[] params, InputStream csvStream) throws IOException {
		PeriodDependentParamSet<E> data = new PeriodDependentParamSet<E>(params);
		
		data.loadCSV(csvStream);
		
		return data;
	}
	
	/**
	 * For verifying params
	 * @param name
	 * @return
	 */
	private static String nameStrip(String name) {
		return name.trim().replaceAll(" ", "").replaceAll("_", "").toLowerCase();
	}
	
	private static double csvValParse(String str) {
		if (str.trim().toLowerCase().startsWith("na"))
			return Double.NaN;
		return Double.parseDouble(str);
	}
	
	public void loadCSV(File csvFile) throws IOException {
		loadCSV(CSVFile.readFile(csvFile, true));
	}
	
	public void loadCSV(InputStream csvStream) throws IOException {
		loadCSV(CSVFile.readStream(csvStream, true));
	}
	
	public void loadCSV(CSVFile<String> csv) throws IOException {
		Preconditions.checkState(csv.getNumCols() == params.length+1, "Param count mismatch");
		
		for (int i=0; i<params.length; i++) {
			String paramName = params[i].name().trim();
			String inputName = csv.get(0, i+1).trim();
			Preconditions.checkState(nameStrip(paramName).equals(nameStrip(inputName)),
					"Parameter mismatch at column %s. Expected: %s, Actual: %s", i, paramName, inputName);
		}
		
		clear();
		
		for (int row=1; row<csv.getNumRows(); row++) {
			List<String> line = csv.getLine(row);
//			System.out.println(Joiner.on(",").join(line));
			String periodStr = line.get(0);
			if (periodStr == null || periodStr.isEmpty())
				continue;
			double period = PeriodDependentParamSetEditor.getPeriodFromRender(line.get(0));
			double[] values = new double[params.length];
			for (int i=0; i<params.length; i++)
				values[i] = csvValParse(line.get(i+1));
			set(period, values);
		}
	}
	
	public void writeCSV(File csvFile) throws IOException {
		CSVFile<String> csv = new CSVFile<String>(true);
		
		List<String> header = Lists.newArrayList("Period");
		for (E param : params)
			header.add(param.name());
		csv.addLine(header);
		
		for (int i=0; i<size(); i++) {
			List<String> line = Lists.newArrayList(PeriodDependentParamSetEditor.getPeriodForRender(getPeriod(i))+"");
			for (double val : getValues(i))
				line.add(val+"");
			csv.addLine(line);
		}
		
		csv.writeToFile(csvFile);
	}
	
	static final Joiner j = Joiner.on("\t");
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(ClassUtils.getClassNameWithoutPackage(this.getClass())).append(":\n");
		sb.append("\tPeriod\t").append(j.join(params)).append("\n");
		for (int i=0; i<size(); i++)
			sb.append("\t").append(getPeriod(i)).append("\t").append(j.join(Doubles.asList(getValues(i)))).append("\n");
		return sb.toString();
	}
	
	private static <E extends Enum<E>> void plotInterpolation(
			PeriodDependentParamSet<E> paramSet, E[] paramsToPlot, File outputDir) throws IOException {
		List<Double> periods = Lists.newArrayList();
		for (int i=1; i<32; i++) {
			double pre = i % 10;
			if (pre == 0)
				continue;
			double exp = (int)(i/10) - 2;
			double p = pre*Math.pow(10, exp);
//			System.out.println(p);
			periods.add(p);
		}
		
		ArbitrarilyDiscretizedFunc[] interpolated = new ArbitrarilyDiscretizedFunc[paramsToPlot.length];
		for (int i=0; i<interpolated.length; i++) {
			interpolated[i] = new ArbitrarilyDiscretizedFunc();
			interpolated[i].setName("Interpolated");
		}
		
		for (double period : periods) {
			double[] vals = paramSet.getInterpolated(paramsToPlot, period);
			
			for (int i=0; i<paramsToPlot.length; i++)
				interpolated[i].set(period, vals[i]);
		}
		
		List<PlotSpec> specs = Lists.newArrayList();
		
		for (int i=0; i<paramsToPlot.length; i++) {
			List<DiscretizedFunc> funcs = Lists.newArrayList();
			List<PlotCurveCharacterstics> chars = Lists.newArrayList();
			
			ArbitrarilyDiscretizedFunc input = new ArbitrarilyDiscretizedFunc();
			input.setName("Input");
			for (double period : paramSet.getPeriods())
				input.set(period, paramSet.get(paramsToPlot[i], period));
			funcs.add(input);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.TRIANGLE, 4f, Color.BLACK));
			
			funcs.add(interpolated[i]);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
			
			PlotSpec spec = new PlotSpec(funcs, chars,
					"Parameter Interpolation", "Period", paramsToPlot[i].toString());
			if (i == paramsToPlot.length-1)
				spec.setLegendVisible(true);
			specs.add(spec);
		}
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		
		List<Range> xRanges = Lists.newArrayList(new Range(periods.get(0), periods.get(periods.size()-1)));
		
		gp.drawGraphPanel(specs, true, false, xRanges, null);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, "param_interpolation.png").getAbsolutePath());
	}
	
	public static void main(String[] args) throws IOException {
		PeriodDependentParamSet<Params> periodParams = PeriodDependentParamSet.loadCSV(
				Params.values(), PeriodDependentParamSet.class.getResourceAsStream("params.csv"));
		Params[] paramsToPlot = { Params.F1, Params.F2 };
		File outputDir = new File("/tmp");
		plotInterpolation(periodParams, paramsToPlot, outputDir);
	}
}
