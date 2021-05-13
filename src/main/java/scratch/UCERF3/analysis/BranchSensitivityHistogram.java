package scratch.UCERF3.analysis;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.jfreechart.tornado.TornadoDiagram;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;

import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.primitives.Doubles;

/**
 * Used to create various branch sensitivity histograms. Should be populated as each branch is
 * calculated and has convenience methods to work with LogicTreeBranchNode classes.
 * @author kevin
 *
 */
public class BranchSensitivityHistogram implements Serializable {
	
	private Table<String, String, List<Double>> valsTable;
	private Table<String, String, List<Double>> weightsTable;
	
	// keeps track of each category, in the order added
	private List<String> categoryAddedOrder = Lists.newArrayList();
	
	private String xAxisName;
	
	public BranchSensitivityHistogram(String xAxisName) {
		this.xAxisName = xAxisName;
		
		valsTable = HashBasedTable.create();
		weightsTable = HashBasedTable.create();
	}
	
	public void addValues(LogicTreeBranch branch, Double val, Double weight) {
		addValues(branch, val, weight, new String[0]);
	}
	
	/**
	 * 
	 * @param branch
	 * @param val
	 * @param weight
	 * @param extraValues
	 */
	public void addValues(LogicTreeBranch branch, Double val, Double weight, String... extraValues) {
		Preconditions.checkState(extraValues == null || extraValues.length % 2 == 0,
				"Extra values must be empty/null or supplied in category/choice pairs.");
		
		List<String> extraCategories = Lists.newArrayList();
		List<String> extraChoices = Lists.newArrayList();
		if (extraValues != null && extraValues.length > 0) {
			for (int i=0; i<extraValues.length; i++) {
				extraCategories.add(extraValues[i++]); // increment once in-between
				extraChoices.add(extraValues[i]);
			}
		}
		
		// populate each branch level
		for (int i=0; i<branch.size(); i++) {
			LogicTreeBranchNode<?> choice = branch.getValue(i);
			String categoryName = ClassUtils.getClassNameWithoutPackage(LogicTreeBranch.getEnumEnclosingClass(choice.getClass()));
			addValue(categoryName, choice.getShortName(), val, weight);
		}
		
		//  populate each extra category
		for (int i=0; i<extraCategories.size(); i++) {
			addValue(extraCategories.get(i), extraChoices.get(i), val, weight);
		}
	}
	
	public synchronized void addValue(String categoryName, String choiceName, Double val, Double weight) {
		if (!valsTable.contains(categoryName, choiceName)) {
			// new category?
			if (!valsTable.rowKeySet().contains(categoryName))
				categoryAddedOrder.add(categoryName);
			valsTable.put(categoryName, choiceName, new ArrayList<Double>());
			weightsTable.put(categoryName, choiceName, new ArrayList<Double>());
		}
		valsTable.get(categoryName, choiceName).add(val);
		weightsTable.get(categoryName, choiceName).add(weight);
	}
	
	public void addAll(BranchSensitivityHistogram o) {
		for (Cell<String, String, List<Double>> cell : valsTable.cellSet()) {
			String categoryName = cell.getRowKey();
			String choiceName = cell.getColumnKey();
			List<Double> value = cell.getValue();
			for (int i = 0; i < value.size(); i++) {
				double val = value.get(i);
				double weight = weightsTable.get(categoryName, choiceName).get(i);
				addValue(categoryName, choiceName, val, weight);
			}
		}
	}
	
	private HistogramFunction generateHist(String categoryName, String choiceName, double min, int num, double delta) {
		// values above/below included in last/first bin
		
		HistogramFunction hist = new HistogramFunction(min, num, delta);
		
		List<Double> vals = valsTable.get(categoryName, choiceName);
		List<Double> weights = weightsTable.get(categoryName, choiceName);
		
		Preconditions.checkState(vals.size() == weights.size());
		Preconditions.checkState(!vals.isEmpty());
		
//		double totWeight = 0d;
//		for (double weight : weights)
//			totWeight += weight;
//		double weightMult = 1d/totWeight;
		
		for (int i=0; i<vals.size(); i++) {
			double val = vals.get(i);
			if (!Doubles.isFinite(val))
				continue;
			double weight = weights.get(i);
			// use this to map below/above values
			int index = hist.getClosestXIndex(val);
//			Preconditions.checkState(val <= hist.getMaxX()+0.5*delta,
//					"val outside of range: "+val+" range=["+hist.getMinX()+","+hist.getMaxX()+"]");
//			Preconditions.checkState(val >= hist.getMinX()-0.5*delta,
//					"val outside of range: "+val+" range=["+hist.getMinX()+","+hist.getMaxX()+"]");
			
//			hist.add(index, weight*weightMult);
			hist.add(index, weight);
		}
		
		hist.setName(choiceName);
		
		return hist;
	}
	
	public List<Double> getVals(String categoryName, String choiceName) {
		return Collections.unmodifiableList(valsTable.get(categoryName, choiceName));
	}
	
	/**
	 * calculate the weighted mean of all values across each choice in each category
	 * @return
	 */
	public double calcOverallMean() {
		double weightTot = 0d;
		double mean = 0d;
		
		for (Cell<String, String, List<Double>> cell : valsTable.cellSet()) {
			List<Double> vals = cell.getValue();
			List<Double> weights = weightsTable.get(cell.getRowKey(), cell.getColumnKey());
			
			for (int i=0; i<weights.size(); i++) {
				double val = vals.get(i);
				if (!Doubles.isFinite(val))
					continue;
				double weight = weights.get(i);
				
				mean += val*weight;
				weightTot += weight;
			}
		}
		
		return mean/weightTot;
	}
	
	/**
	 * calculate the weighted mean of all values across each choice in each category
	 * @return
	 */
	public double calcOverallStdDev() {
		double weightTot = 0d;
		double mean = calcOverallMean();
		double var = 0;
		
		for (Cell<String, String, List<Double>> cell : valsTable.cellSet()) {
			List<Double> vals = cell.getValue();
			List<Double> weights = weightsTable.get(cell.getRowKey(), cell.getColumnKey());
			
			for (int i=0; i<weights.size(); i++) {
				double val = vals.get(i);
				if (!Doubles.isFinite(val))
					continue;
				double weight = weights.get(i);
				
				var += (val-mean)*(val-mean)*weight;
				weightTot += weight;
			}
		}
		var /= weightTot;
		return Math.sqrt(var);
	}
	
	/**
	 * calculate the weighted mean of all values across each choice in the given category
	 * @param categoryName
	 * @return
	 */
	public double calcMean(String categoryName) {
		return calcMean(categoryName, new String[0]);
	}
	
	/**
	 * calculate the weighted mean of all values across the given choice in the given category,
	 * or across all choices if choiceName is null
	 * @param categoryName
	 * @param choiceNames
	 * @return
	 */
	public double calcMean(String categoryName, String... choiceNames) {
		if (choiceNames == null || choiceNames.length == 0) {
			List<String> choices = Lists.newArrayList(getChoices(categoryName));
			choiceNames = new String[choices.size()];
			for (int i=0; i<choiceNames.length; i++)
				choiceNames[i] = choices.get(i);
		}
		List<Double> vals = Lists.newArrayList();
		List<Double> weights = Lists.newArrayList();
		for (String choice : choiceNames) {
			vals.addAll(valsTable.get(categoryName, choice));
			weights.addAll(weightsTable.get(categoryName, choice));
		}
		
		double sumWeight = 0d;
		double weightedSum = 0d;
//		double nonWeightMean = 0d;
		
		MinMaxAveTracker track = new MinMaxAveTracker();
		
		for (int i=0; i<vals.size(); i++) {
			double val = vals.get(i);
			double weight = weights.get(i);
			
			if (!Doubles.isFinite(val))
				continue;
			
//			nonWeightMean += val;
			
			sumWeight += weight;
			weightedSum += val*weight;
			
			track.addValue(val);
		}
		
//		System.out.println("calcMean="+(weightedSum / sumWeight)+" nonWeight="+(nonWeightMean/(double)vals.size()));
		
		double mean = weightedSum / sumWeight;
		
		Preconditions.checkState(mean >= track.getMin() && mean <= track.getMax(),
				"Mean not within min/max: mean="+mean+", min="+track.getMin()+", max="+track.getMax());
		
		return mean;
	}
	
	/**
	 * calculate the weighted mean of all values across each choice except the given in the given category
	 * @param categoryName
	 * @return
	 */
	public double calcMeanWithout(String categoryName, String choiceName) {
		Set<String> choices = getChoices(categoryName);
		String[] namesWithout = new String[choices.size()-1];
		int cnt = 0;
		for (String oChoice : choices) {
			if (oChoice.equals(choiceName))
				continue;
			namesWithout[cnt++] = oChoice;
		}
		
		return calcMean(categoryName, namesWithout);
	}
	
	/**
	 * calculate the weighted std dev of all values across each choice in the given category
	 * @param categoryName
	 * @return
	 */
	public double calcStdDev(String categoryName) {
		return calcStdDev(categoryName, new String[0]);
	}
	
	/**
	 * calculate the weighted std dev of all values across the given choice in the given category,
	 * or across all choices if choiceName is null
	 * @param categoryName
	 * @param choiceNames
	 * @return
	 */
	public double calcStdDev(String categoryName, String... choiceNames) {
		if (choiceNames == null || choiceNames.length == 0) {
			List<String> choices = Lists.newArrayList(getChoices(categoryName));
			choiceNames = new String[choices.size()];
			for (int i=0; i<choiceNames.length; i++)
				choiceNames[i] = choices.get(i);
		}
		List<Double> vals = Lists.newArrayList();
		List<Double> weights = Lists.newArrayList();
		for (String choice : choiceNames) {
			vals.addAll(valsTable.get(categoryName, choice));
			weights.addAll(weightsTable.get(categoryName, choice));
		}
		
		double sumWeight = 0d;
		double mean = calcMean(categoryName, choiceNames);
		double var = 0;
		for(int i=0; i<vals.size(); i++) {
			double val = vals.get(i);
			double weight = weights.get(i);
			
			if (!Doubles.isFinite(val))
				continue;
			
			sumWeight += weight;
			var += (val-mean)*(val-mean)*weight;
		}
		var /= sumWeight;
		return Math.sqrt(var);
	}
	
	/**
	 * calculate the weighted std dev of all values across each choice except the given in the given category
	 * @param categoryName
	 * @return
	 */
	public double calcStdDevWithout(String categoryName, String choiceName) {
		Set<String> choices = getChoices(categoryName);
		String[] namesWithout = new String[choices.size()-1];
		int cnt = 0;
		for (String oChoice : choices) {
			if (oChoice.equals(choiceName))
				continue;
			namesWithout[cnt++] = oChoice;
		}
		
		return calcStdDev(categoryName, namesWithout);
	}
	
	public Range getRange() {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		
		for (Cell<String, String, List<Double>> cell : valsTable.cellSet()) {
			for (double val : cell.getValue()) {
				if (val < min)
					min = val;
				if (val > max)
					max = val;
			}
		}
		
		return new Range(min, max);
	}
	
	/**
	 * Calculates a nicely rounded range that will exactly cover the data range with the given discretization.
	 * Histogram num points can be calculated as num = (range.getUpperBound()-range.getLowerBound())/delta + 1;
	 * @param delta
	 * @return
	 */
	public Range calcSmartHistRange(double delta) {
		Range range = getRange();
		double min = Math.floor(range.getLowerBound()/delta) * delta;
		// it's possible that this was too conservative and that the bin above could hold the range min val
		// due to the bin width
		if (min + 0.5*delta < range.getLowerBound())
			min += delta;
		double max = min;
		while (max+0.5*delta < range.getUpperBound())
			max += delta;
		
		return new Range(min, max);
	}
	
	public Set<String> getChoices(String categoryName) {
		return valsTable.row(categoryName).keySet();
	}

	public Map<String, PlotSpec> getStackedHistPlots(boolean meanLines, double delta) {
		Range range = calcSmartHistRange(delta);
		int num = (int)Math.round((range.getUpperBound()-range.getLowerBound())/delta) + 1;
		System.out.println("Smart range: "+range+", num="+num);
		return getStackedHistPlots(meanLines, range.getLowerBound(), num, delta);
	}
	public Map<String, PlotSpec> getStackedHistPlots(boolean meanLines, double min, int num, double delta) {
		Map<String, PlotSpec> map = Maps.newHashMap();
		
		for (String categoryName : valsTable.rowKeySet()) {
			if (valsTable.row(categoryName).size() > 1)
				// only include if there are at least 2 choices at this level
				map.put(categoryName, getStackedHistPlot(categoryName, meanLines, min, num, delta));
		}
		
		return map;
	}
	
	public PlotSpec getStackedHistPlot(String categoryName, boolean meanLines, double min, int num, double delta) {
		List<HistogramFunction> hists = Lists.newArrayList();
		
		List<String> choiceNames = Lists.newArrayList(getChoices(categoryName));
		Collections.sort(choiceNames);
		
		for (String choiceName : choiceNames)
			hists.add(generateHist(categoryName, choiceName, min, num, delta));
		
		List<XY_DataSet> funcs = Lists.newArrayList();
		funcs.addAll(HistogramFunction.getStackedHists(hists, true));
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
//		List<Color> colors = GraphWindow.generateDefaultColors();
		List<Color> colors = Lists.newArrayList(
				Color.BLACK, Color.BLUE, Color.RED, Color.GREEN, Color.CYAN, Color.ORANGE, Color.MAGENTA, Color.PINK, Color.YELLOW);
		Preconditions.checkState(hists.size() <= colors.size(), "Only have enough colors for "+colors.size()+" hists.");
		for (int i=0; i<funcs.size(); i++)
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, colors.get(i)));
		
		PlotSpec spec = new PlotSpec(funcs, chars, categoryName, xAxisName, "Density");
		spec.setLegendVisible(true);
		spec.setLegendLocation(RectangleEdge.BOTTOM);
		
		if (meanLines) {
			// this code here is just to get a legend that doesn't include the lines that will be added later
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			CommandLineInversionRunner.setFontSizes(gp);
			
			gp.drawGraphPanel(spec);
			spec.setCustomLegendItems(gp.getPlot().getLegendItems());
			
			List<Double> choiceMeans = Lists.newArrayList();
			List<Color> choiceColors = Lists.newArrayList();
			
			for (int j=0; j<choiceNames.size(); j++) {
				String choiceName = choiceNames.get(j);
				double choiceMean = calcMean(categoryName, choiceName);
				Color choiceColor = chars.get(j).getColor();
				
				choiceMeans.add(choiceMean);
				choiceColors.add(choiceColor);
			
			}
			// add black thicker lines as a backing to make them visible
			for (int j=0; j<choiceNames.size(); j++) {
				double choiceMean = choiceMeans.get(j);
				DefaultXY_DataSet line = new DefaultXY_DataSet();
				line.set(choiceMean, 0d);
				line.set(choiceMean, 1d);
				line.setName("(line mask)");
				funcs.add(line);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.GRAY));
			}
			for (int j=0; j<choiceNames.size(); j++) {
				double choiceMean = choiceMeans.get(j);
				Color choiceColor = choiceColors.get(j);
				String choiceName = choiceNames.get(j);
				DefaultXY_DataSet line = new DefaultXY_DataSet();
				line.set(choiceMean, 0);
				line.set(choiceMean, 1d);
				line.setName(choiceName+" (mean="+(float)choiceMean+")");
				funcs.add(line);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 4f, choiceColor));
			}
		}
		
		return spec;
	}
	
	public CSVFile<String> getStaticsticsCSV() {
		CSVFile<String> csv = new CSVFile<String>(true);
		
		csv.addLine("Category", "Choice", "Choice Mean", "Choice Std Dev",
				"Mean WITHOUT Choice", "Std Dev WITHOUT Choice");
		
		for (String categoryName : categoryAddedOrder) {
			List<String> choices = Lists.newArrayList(getChoices(categoryName));
			Collections.sort(choices);
			
			for (String choiceName : choices) {
				List<String> line = Lists.newArrayList(categoryName, choiceName);
				
				double choiceMean = calcMean(categoryName, choiceName);
				double choiceStdDev = calcStdDev(categoryName, choiceName);
				
				double withoutMean = calcMeanWithout(categoryName, choiceName);
				double withoutStdDev = calcStdDevWithout(categoryName, choiceName);
				
				line.add(choiceMean+"");
				line.add(choiceStdDev+"");
				line.add(withoutMean+"");
				line.add(withoutStdDev+"");
				csv.addLine(line);
			}
		}
		
		// add TOTAL
		// identical for each category, equally distributed
		double overallMean = calcOverallMean();
		double overallStdDev = calcOverallStdDev();
		csv.addLine("TOTAL", "N/A", overallMean+"", overallStdDev+"", Double.NaN+"", Double.NaN+"");
		
		return csv;
	}
	
	public TornadoDiagram getTornadoDiagram(String title, boolean useMeanShift) {
		double overallMean = calcOverallMean();
		TornadoDiagram t;
		if (useMeanShift)
			t = new TornadoDiagram(title, "Mean With - Mean Without, "+xAxisName, "Branch Level", 0d);
		else
			t = new TornadoDiagram(title, "Mean "+xAxisName, "Branch Level", overallMean);
		
		for (String categoryName : categoryAddedOrder) {
			Set<String> choices = getChoices(categoryName);
			if (choices.size() == 1)
				continue;
			for (String choiceName : choices) {
				double choiceMean = calcMean(categoryName, choiceName);
				double val;
				
				if (useMeanShift) {
					double withoutMean = calcMeanWithout(categoryName, choiceName);
					val = overallMean - withoutMean;
				} else {
					// just use mean
					val = choiceMean;
				}
				
				t.addTornadoValue(categoryName, choiceName, val);
			}
		}
		
		return t;
	}
	
	public List<String> getCategoriesInAddedOrder() {
		return categoryAddedOrder;
	}

}
