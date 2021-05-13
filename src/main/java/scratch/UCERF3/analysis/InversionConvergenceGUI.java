package scratch.UCERF3.analysis;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.editor.impl.GriddedParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.ButtonParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.FileParameter;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.gui.plot.GraphWindow;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.logicTree.VariableLogicTreeBranch;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class InversionConvergenceGUI extends JFrame implements
ParameterChangeListener {
	
	private static final String BROWSE_PARAM_NAME = "CSV Dir/Zip File";
	private FileParameter browseParam;
	
	private static final String ANY_CHOICE = "(any)";
	private ParameterList enumParams;
	
	private static final String VARIATION_PARAM_NAME = "Variation";
	
	private static final String REFRESH_PARAM_NAME = "Plot";
	private static final String REFRESH_BUTTON_TEXT = "Reload Results";
	private ButtonParameter refreshButton;
	
	private static final String CARD_GRAPH = "Graph";
	private static final String CARD_BAR = "Bar";
	private static final String CARD_NONE = "None";
	private static final String CARD_CONVERG_PLAYGROUND = "Convergence Playground";
	
	private enum PlotType {
		ENERGY_VS_TIME("Energy vs Time", CARD_GRAPH),
		ENERGY_VS_ITERATIONS("Energy vs Iterations", CARD_GRAPH),
		FINAL_ENERGY_BREAKDOWN("Final Energy Breakdown", CARD_BAR),
		FINAL_NORMALIZED_PERTURBATION_BREAKDOWN("Final Norm. Perturb Breakdown", CARD_BAR),
		PERTURBATIONS_VS_ITERATIONS("Perturbations Vs Iterations", CARD_GRAPH),
		PERTURBATIONS_FRACTION("Perturbs/Iters Vs Time", CARD_GRAPH),
		CONVERGENCE_PERCENT_CHANGE("Convergence % Change", CARD_CONVERG_PLAYGROUND),
		CONVERGENCE_CHANGE("Convergence Change", CARD_CONVERG_PLAYGROUND),
		CONVERGENCE_ENERGY("Convergence Energy", CARD_CONVERG_PLAYGROUND);
		private String name;
		private String card;
		private PlotType(String name, String card) {
			this.name = name;
			this.card = card;
		}
		@Override
		public String toString() {
			return name;
		}
	}
	
	private static final String PLOT_TYPE_PARAM_NAME = "Plot Type";
	private EnumParameter<PlotType> plotTypeParam;
	
	private GriddedParameterListEditor griddedEditor;
	
	private JPanel contentPane;
	
	private CardLayout cl;
	private JPanel chartPanel;
	
	private GraphWidget graphWidget;
	
	private JPanel barChartPanel;
	
	// this stuff is for playing with convergence criteria
	private JPanel convergPlayPanel;
	private BooleanParameter timeBasedParam = new BooleanParameter("Time Based", true);
	private static final int LOOK_BACK_DEFAULT_TIME = 60; // 60 minutes
	private static final int LOOK_BACK_DEFAULT_ITERATIONS = 200000 * 10; // 10x num rups
	private IntegerParameter lookBackParam = new IntegerParameter("Look Back", LOOK_BACK_DEFAULT_TIME);
	private DoubleParameter energyPercentChangeThresholdParam = new DoubleParameter("Energy Change % Threshold",
			new Double(0), new Double(100), "%", new Double(1.5));
	private DoubleParameter energyChangeThresholdParam = new DoubleParameter("Energy Change Threshold",
			new Double(0), new Double(1000), new Double(2));
	private GraphWidget convergeGP;
	
	private Map<VariableLogicTreeBranch, CSVFile<String>> resultFilesMap;
	private ArrayList<String> curNames;
	private ArrayList<LogicTreeBranch> curBranches;
	private ArrayList<ArbitrarilyDiscretizedFunc> curEnergyVsTimes;
	private ArrayList<ArbitrarilyDiscretizedFunc> curEnergyVsIters;
	private ArrayList<double[]> curFinalEnergies;
	private ArrayList<String> curEnergyNames;
	private ArrayList<ArbitrarilyDiscretizedFunc> curPerturbsPerItersVsTimes;
	private ArrayList<ArbitrarilyDiscretizedFunc> curPerturbsVsIters;
	private CSVFile<String> lastCSV;
	
	private static ArrayList<String> energyComponentNames =
		Lists.newArrayList("Total", "Equality", "Entropy", "Inequality");
	
	public InversionConvergenceGUI() {
		super("Inversion Convergence GUI");
		browseParam = new FileParameter(BROWSE_PARAM_NAME);
		browseParam.addParameterChangeListener(this);
		
		enumParams = new ParameterList();
		enumParams.addParameter(buildEnumParam(FaultModels.class, FaultModels.FM3_1));
		enumParams.addParameter(buildEnumParam(DeformationModels.class, null));
		enumParams.addParameter(buildEnumParam(ScalingRelationships.class, ScalingRelationships.SHAW_2009_MOD));
		enumParams.addParameter(buildEnumParam(SlipAlongRuptureModels.class, SlipAlongRuptureModels.TAPERED));
		enumParams.addParameter(buildEnumParam(InversionModels.class, InversionModels.CHAR_CONSTRAINED));
		enumParams.addParameter(buildEnumParam(TotalMag5Rate.class, TotalMag5Rate.RATE_7p9));
		enumParams.addParameter(buildEnumParam(MaxMagOffFault.class, MaxMagOffFault.MAG_7p6));
		enumParams.addParameter(buildEnumParam(MomentRateFixes.class, MomentRateFixes.NONE));
		enumParams.addParameter(buildEnumParam(SpatialSeisPDF.class, SpatialSeisPDF.UCERF3));
		
		refreshButton = new ButtonParameter(REFRESH_PARAM_NAME, REFRESH_BUTTON_TEXT);
		refreshButton.addParameterChangeListener(this);
		
		plotTypeParam = new EnumParameter<InversionConvergenceGUI.PlotType>(
				PLOT_TYPE_PARAM_NAME, EnumSet.allOf(PlotType.class), PlotType.ENERGY_VS_TIME, null);
		plotTypeParam.addParameterChangeListener(this);
		
		contentPane = new JPanel(new BorderLayout());
		griddedEditor = new GriddedParameterListEditor(buildTopParamList(null), 1, 0);
		contentPane.add(griddedEditor, BorderLayout.NORTH);
		
		cl = new CardLayout();
		chartPanel = new JPanel(cl);
		JPanel nonePanel = new JPanel();
		nonePanel.add(new JLabel("No results found/loaded"));
		chartPanel.add(nonePanel, CARD_NONE);
		graphWidget = new GraphWidget();
		chartPanel.add(graphWidget, CARD_GRAPH);
		barChartPanel = new JPanel(new BorderLayout());
		chartPanel.add(barChartPanel, CARD_BAR);
		
		// convergence playground
		convergPlayPanel = new JPanel(new BorderLayout());
		convergeGP = new GraphWidget();
		convergPlayPanel.add(convergeGP, BorderLayout.CENTER);
		ParameterList convergeList = new ParameterList();
		convergeList.addParameter(timeBasedParam);
		convergeList.addParameter(lookBackParam);
		convergeList.addParameter(energyPercentChangeThresholdParam);
		convergeList.addParameter(energyChangeThresholdParam);
		for (Parameter<?> p : convergeList)
			p.addParameterChangeListener(this);
		GriddedParameterListEditor convergeParamEdit = new GriddedParameterListEditor(convergeList, 1, 3);
		convergPlayPanel.add(convergeParamEdit, BorderLayout.SOUTH);
		chartPanel.add(convergPlayPanel, CARD_CONVERG_PLAYGROUND);
		
		contentPane.add(chartPanel, BorderLayout.CENTER);
		
		this.setContentPane(contentPane);
		this.setSize(1400, 1000);
		this.setVisible(true);
		this.setDefaultCloseOperation(EXIT_ON_CLOSE);
	}
	
	private ParameterList buildTopParamList(ArrayList<ArrayList<String>> variations) {
		ParameterList params = new ParameterList();
		params.addParameter(browseParam);
		params.addParameterList(enumParams);
		for (int i=0; variations!=null && i<variations.size(); i++) {
			ArrayList<String> vars = variations.get(i);
			Collections.sort(vars);
			vars.add(0, ANY_CHOICE);
			StringParameter variationParam = new StringParameter(VARIATION_PARAM_NAME+" ("+i+")", vars);
			variationParam.setValue(ANY_CHOICE);
			variationParam.addParameterChangeListener(this);
			params.addParameter(variationParam);
		}
		params.addParameter(plotTypeParam);
		params.addParameter(refreshButton);
		return params;
	}
	
	private <E extends Enum<E>> EnumParameter<?> buildEnumParam(Class<E> e, E defaultValue) {
		String name = ClassUtils.getClassNameWithoutPackage(e);
		EnumParameter<E> param = new EnumParameter<E>(name, EnumSet.allOf(e),
				defaultValue, ANY_CHOICE);
		param.addParameterChangeListener(this);
		return param;
	}
	
	private static HashMap<VariableLogicTreeBranch, CSVFile<String>> loadZipFile(File zipFile, VariableLogicTreeBranch branch)
	throws IOException {
		HashMap<VariableLogicTreeBranch, CSVFile<String>> map = Maps.newHashMap();
		ZipFile zip = new ZipFile(zipFile);
		
		Enumeration<? extends ZipEntry> e = zip.entries();
		
		while (e.hasMoreElements()) {
			ZipEntry entry = e.nextElement();
			String name = entry.getName();
			if (!name.endsWith(".csv"))
				continue;
//			System.out.println("Parsing: "+name);
			VariableLogicTreeBranch candidate = VariableLogicTreeBranch.fromFileName(name);
			if (!branch.matchesNonNulls(candidate)) {
				continue;
			}
			System.out.println("Loading: "+name);
//			System.out.println("Branch: "+candidate);
			map.put(candidate, CSVFile.readStream(zip.getInputStream(entry), true));
		}
		
		return map;
	}
	
	private static HashMap<VariableLogicTreeBranch, CSVFile<String>> loadDir(File dir, VariableLogicTreeBranch branch)
	throws IOException {
		HashMap<VariableLogicTreeBranch, CSVFile<String>> map = Maps.newHashMap();
		for (File file : dir.listFiles()) {
			if (file.isDirectory())
				continue;
			String name = file.getName();
			if (!name.endsWith(".csv"))
				continue;
			VariableLogicTreeBranch candidate = VariableLogicTreeBranch.fromFileName(name);
			if (!branch.matchesNonNulls(candidate)) {
				continue;
			}
			map.put(candidate, CSVFile.readFile(file, true));
		}
		
		return map;
	}
	
	private void loadResultFiles(VariableLogicTreeBranch branch) throws IOException {
		File file = browseParam.getValue();
		
		resultFilesMap = null;
		System.gc();
		
		if (file == null) {
			resultFilesMap = null;
		} else if (file.getName().toLowerCase().endsWith(".zip")) {
			resultFilesMap = loadZipFile(file, branch);
		} else {
			resultFilesMap = loadDir(file, branch);
		}
		if (resultFilesMap != null) {
			ArrayList<ArrayList<String>> variations = Lists.newArrayList();
			for (VariableLogicTreeBranch b : resultFilesMap.keySet()) {
				if (b.getVariations() != null) {
					for (int i=0; i<b.getVariations().size(); i++) {
						if (i >= variations.size())
							variations.add(new ArrayList<String>());
						List<String> vars = variations.get(i);
						String var = b.getVariations().get(i);
						if (var != null && !vars.contains(var))
							vars.add(var);
					}
				}
			}
			
			// we must update the list
			griddedEditor.setParameterList(buildTopParamList(variations));
			griddedEditor.validate();
		}
	}
	
	private VariableLogicTreeBranch getCurrentBranch() {
		List<LogicTreeBranchNode<?>> nodes = Lists.newArrayList();
		for (Parameter<?> param : enumParams) {
			if (param.getValue() != null) {
				EnumParameter<?> enumParam = (EnumParameter<?>) param;
				LogicTreeBranchNode<?> node = (LogicTreeBranchNode<?>) enumParam.getValue();
				nodes.add(node);
			}
		}
		
		ArrayList<String> variations = new ArrayList<String>();
		for (Parameter<?> param : griddedEditor.getParameterList()) {
			if (param.getName().startsWith(VARIATION_PARAM_NAME)) {
				StringParameter variationParam = (StringParameter)param;
				String variation;
				if (!variationParam.getValue().equals(ANY_CHOICE))
					variation = variationParam.getValue();
				else
					variation = null;
				variations.add(variation);
			}
		}
		return new VariableLogicTreeBranch(LogicTreeBranch.fromValues(
				false, nodes.toArray(new LogicTreeBranchNode[0])), variations);
	}
	
	private void buildFunctions(VariableLogicTreeBranch branch) {
		curNames = new ArrayList<String>();
		curBranches = new ArrayList<LogicTreeBranch>();
		curEnergyVsTimes = new ArrayList<ArbitrarilyDiscretizedFunc>();
		curEnergyVsIters = new ArrayList<ArbitrarilyDiscretizedFunc>();
		curPerturbsPerItersVsTimes = new ArrayList<ArbitrarilyDiscretizedFunc>();
		curPerturbsVsIters = new ArrayList<ArbitrarilyDiscretizedFunc>();
		curFinalEnergies = new ArrayList<double[]>();
		curEnergyNames = null;
		
		if (resultFilesMap == null)
			return;
		
		for (VariableLogicTreeBranch candidate : resultFilesMap.keySet()) {
			if (!branch.matchesVariation(candidate))
				continue;
			ArrayList<String> diffNames = new ArrayList<String>();

			for (int i=0; i<branch.size(); i++) {
				LogicTreeBranchNode<?> node = branch.getValue(i);
				if (node == null) {
					if (candidate.getValue(i) == null)
						System.out.println("WFT? Class: "+LogicTreeBranch.getLogicTreeNodeClasses().get(i));
					diffNames.add(candidate.getValue(i).getShortName());
				}
			}
			if (branch.getVariations() != null && candidate.getVariations() != null) {
				for (int i=0; i<candidate.getVariations().size(); i++) {
					String var = candidate.getVariations().get(i);
					if (i >= branch.getVariations().size() || branch.getVariations().get(i) == null)
						diffNames.add("Var: "+var);
				}
			}
			String name = Joiner.on(", ").join(diffNames);
			CSVFile<String> csv = resultFilesMap.get(candidate);
			ArbitrarilyDiscretizedFunc energyVsTime = new ArbitrarilyDiscretizedFunc(name);
			energyVsTime.setInfo("Total energy");
			ArbitrarilyDiscretizedFunc energyVsIter = new ArbitrarilyDiscretizedFunc(name);
			energyVsIter.setInfo("Total energy");
			ArbitrarilyDiscretizedFunc perturbsPerItersVsTimes = new ArbitrarilyDiscretizedFunc();
			perturbsPerItersVsTimes.setName(name);
			ArbitrarilyDiscretizedFunc perturbsVsIters = new ArbitrarilyDiscretizedFunc();
			perturbsVsIters.setName(name);
			int numEnergies = csv.getNumCols()-3;
			if (curEnergyNames == null) {
				curEnergyNames = new ArrayList<String>();
				curEnergyNames.addAll(energyComponentNames);
				for (int i=curEnergyNames.size(); i<numEnergies; i++)
					curEnergyNames.add(csv.get(0, 2+i));
			}
			double[] finalEnergies = null;
			for (int row=1; row<csv.getNumRows(); row++) {
				double iter = Double.parseDouble(csv.get(row, 0));
				double mins = Double.parseDouble(csv.get(row, 1)) / 1000d / 60d;
				double[] energies = new double[numEnergies];
				for (int i=0; i<energies.length; i++) {
					energies[i] = Double.parseDouble(csv.get(row, i+2));
				}
				double perturbs =  Double.parseDouble(csv.get(row, csv.getNumCols()-1));
				if (row == csv.getNumRows()-1)
					finalEnergies = energies;
				energyVsTime.set(mins, energies[0]);
				energyVsIter.set(iter, energies[0]);
				perturbsPerItersVsTimes.set(mins, perturbs/iter);
				perturbsVsIters.set(iter, perturbs);
			}
			// now find insertion point
			int ind = -1;
			for (int i=curNames.size()-1; i>=0; i--) {
				int cmp = name.compareTo(curNames.get(i));
				if (cmp <= 0)
					ind = i;
			}
			if (ind < 0)
				ind = curNames.size();
			curNames.add(ind, name);
			curBranches.add(ind, candidate);
			curEnergyVsTimes.add(ind, energyVsTime);
			curEnergyVsIters.add(ind, energyVsIter);
			curPerturbsPerItersVsTimes.add(ind, perturbsPerItersVsTimes);
			curPerturbsVsIters.add(ind, perturbsVsIters);
			curFinalEnergies.add(ind, finalEnergies);
			
			lastCSV = csv;
		}
		System.out.println("Loaded: "+Joiner.on(", ").join(curNames));
	}
	
	private ArrayList<ArbitrarilyDiscretizedFunc> loadIndividualEnergies(boolean timeBased) {
		ArrayList<ArbitrarilyDiscretizedFunc> funcs = null;
		
		CSVFile<String> csv = lastCSV;
		int numEnergies = csv.getNumCols()-3;
		
		for (int row=1; row<csv.getNumRows(); row++) {
			double iter = Double.parseDouble(csv.get(row, 0));
			double mins = Double.parseDouble(csv.get(row, 1)) / 1000d / 60d;
			double[] energies = new double[numEnergies];
			for (int i=0; i<energies.length; i++) {
				energies[i] = Double.parseDouble(csv.get(row, i+2));
			}
			if (funcs == null) {
				funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
				for (int i=0; i<energies.length; i++) {
					String name = csv.get(0, 2+i);
					ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc(name);
					funcs.add(func);
				}
			}
			double x;
			if (timeBased)
				x = mins;
			else
				x = iter;
			for (int i=0; i<energies.length; i++)
				funcs.get(i).set(x, energies[i]);
			if (row == csv.getNumRows()-1) {
				for (int i=0; i<energies.length; i++)
					funcs.get(i).setInfo("Final energy: "+energies[i]);
			}
		}
		
		return funcs;
	}
	
	private void updatePlot() {
		if (curBranches == null || curBranches.isEmpty()) {
			cl.show(chartPanel, CARD_NONE);
			return;
		}
		PlotType plot = plotTypeParam.getValue();
		if (plot.card.equals(CARD_GRAPH)) {
			System.out.println("Updating a regular graph!");
			ArrayList<ArbitrarilyDiscretizedFunc> funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
			String xAxisName;
			String yAxisName;
			String title;
			ArrayList<PlotCurveCharacterstics> chars = new ArrayList<PlotCurveCharacterstics>();
			switch (plot) {
			case ENERGY_VS_ITERATIONS:
				xAxisName = "Iterations";
				yAxisName = "Energy";
				title = "Energy vs Iterations";
				if (curEnergyVsIters.size() == 1) {
					// single run case, show all component of energy
					funcs.addAll(loadIndividualEnergies(false));
					title += " (Single Run Components!)";
					chars = ThreadedSimulatedAnnealing.getEnergyBreakdownChars();
				} else {
					funcs.addAll(curEnergyVsIters);
				}
				break;
			case ENERGY_VS_TIME:
				xAxisName = "Time (minutes)";
				yAxisName = "Energy";
				title = "Energy vs Time";
				if (curEnergyVsTimes.size() == 1) {
					// single run case, show all component of energy
					funcs.addAll(loadIndividualEnergies(true));
					title += " (Single Run Components!)";
					chars = ThreadedSimulatedAnnealing.getEnergyBreakdownChars();
				} else {
					funcs.addAll(curEnergyVsTimes);
				}
				break;
			case PERTURBATIONS_VS_ITERATIONS:
				xAxisName = "Iterations";
				yAxisName = "Perturbations";
				title = "Perturbations Vs Iterations";
				funcs.addAll(curPerturbsVsIters);
				break;
			case PERTURBATIONS_FRACTION:
				xAxisName = "Time (minutes)";
				yAxisName = "Perturbations/Iterations";
				title = "Perturbations/Iterations Vs Time";
				funcs.addAll(curPerturbsPerItersVsTimes);
				break;

			default:
				throw new RuntimeException("shouldn't get here...");
			}
			PlotSpec spec = graphWidget.getPlotSpec();
			spec.setXAxisLabel(xAxisName);
			spec.setYAxisLabel(yAxisName);
			spec.setPlotElems(funcs);
			spec.setChars(chars);
			spec.setTitle(title);
			graphWidget.setX_Log(false);
			graphWidget.setY_Log(false);
			graphWidget.drawGraph();
			graphWidget.validate();
			graphWidget.repaint();
		} else if (plot.card.equals(CARD_BAR)) {
			System.out.println("Updating a bar graph!");
			// bar graph
			boolean perturb = plot == PlotType.FINAL_NORMALIZED_PERTURBATION_BREAKDOWN;
			DefaultCategoryDataset dataset = new DefaultCategoryDataset();
			ArrayList<String> series;
			String rangeLabel;
			if (perturb) {
				series = Lists.newArrayList("% Perturbs Kept");
				rangeLabel = "Perturbations Kept (%)";
			} else {
				series = new ArrayList<String>();
				series.addAll(curEnergyNames);
				rangeLabel = "Energy";
			}
			
			for (int i=0; i<curBranches.size(); i++) {
				double[] energies = curFinalEnergies.get(i);
				ArbitrarilyDiscretizedFunc perturbs = curPerturbsPerItersVsTimes.get(i);
				String category = curNames.get(i);
				
				int lastInd = perturbs.size()-1;
				if (perturb) {
					double norm = perturbs.get(lastInd).getY() * 100d;
					dataset.addValue(norm, series.get(0), category);
				} else {
					for (int j=0; j<energies.length; j++) {
						double e = energies[j];
						String s = series.get(j);
						dataset.addValue(e, s, category);
					}
//					dataset.addValue(energies[0].getY(lastInd), series.get(0), category);
//					dataset.addValue(energies[1].getY(lastInd), series.get(1), category);
//					dataset.addValue(energies[2].getY(lastInd), series.get(2), category);
//					dataset.addValue(energies[3].getY(lastInd), series.get(3), category);
				}
			}
			JFreeChart chart = ChartFactory.createBarChart(plot.toString(), "Branch", rangeLabel,
					dataset, PlotOrientation.VERTICAL, true, true, false);
			
			// set the background color for the chart...
	        chart.setBackgroundPaint(Color.white);

	        // get a reference to the plot for further customisation...
	        final CategoryPlot plt = chart.getCategoryPlot();
	        plt.setBackgroundPaint(Color.lightGray);
	        plt.setDomainGridlinePaint(Color.white);
	        plt.setRangeGridlinePaint(Color.white);

	        // set the range axis to display integers only...
	        if (!perturb) {
	        	final NumberAxis rangeAxis = (NumberAxis) plt.getRangeAxis();
		        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
	        }

	        // disable bar outlines...
	        final BarRenderer renderer = (BarRenderer) plt.getRenderer();
	        renderer.setDrawBarOutline(false);
			
			final CategoryAxis domainAxis = plt.getDomainAxis();
	        domainAxis.setCategoryLabelPositions(
	            CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 6.0)
	        );
	        
	        ChartPanel cp = new ChartPanel(chart);
	        barChartPanel.removeAll();
	        barChartPanel.add(cp, BorderLayout.CENTER);
	        barChartPanel.validate();
	        barChartPanel.repaint();
		} else {
			// convergence
			System.out.println("Updating a convergence plot!");
			updateConvergePlot(plot);
		}
		System.out.println("Displaying card: "+plot.card);
		cl.show(chartPanel, plot.card);
		chartPanel.validate();
	}
	
	private void updateConvergePlot(PlotType plot) {
		ArrayList<ArbitrarilyDiscretizedFunc> refFuncs = new ArrayList<ArbitrarilyDiscretizedFunc>();
		String xAxisName, yAxisName, title;
		double lookBack = lookBackParam.getValue().doubleValue();
		double percentThreshold = energyPercentChangeThresholdParam.getValue();
		double changeThreshold = energyChangeThresholdParam.getValue();
//		boolean energyY = plot == PlotType.CONVERGENCE_ENERGY;
		
		String yQuantity, xUnit;
		if (timeBasedParam.getValue()) {
			refFuncs.addAll(curEnergyVsTimes);
			xAxisName = "Time (minutes)";
			xUnit = "minutes";
			yAxisName = "% Improvement (last "+lookBack+" minutes)";
		} else {
			refFuncs.addAll(curEnergyVsIters);
			xAxisName = "Iterations";
			xUnit = "iterations";
			yAxisName = "% Improvement (last "+(int)(lookBack+0.5)+" iterations)";
		}
		switch (plot) {
		case CONVERGENCE_CHANGE:
			title = "Improvement (last "+(int)(lookBack+0.5)+" "+xUnit+")";
			yAxisName = "Energy Improvement";
			break;
		case CONVERGENCE_PERCENT_CHANGE:
			title = "% Improvement (last "+(int)(lookBack+0.5)+" "+xUnit+")";
			yAxisName = "Energy % Improvement";
			break;
		case CONVERGENCE_ENERGY:
			title = "Energy vs "+xAxisName;
			yAxisName = "Energy";
			break;

		default:
			throw new IllegalStateException("Not a convergence plot type: "+plot);
		}
		ArrayList<PlotCurveCharacterstics> chars = new ArrayList<PlotCurveCharacterstics>();
		ArrayList<ArbitrarilyDiscretizedFunc> funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
		
		List<Color> colors = GraphWindow.generateDefaultColors();
		int colorCnt = 0;
		
		int setMod = refFuncs.size() / 2;
		
		if (setMod > 1)
			System.out.println("Func Set modulus: "+setMod);
		
		double xSaved = 0;
		double xTot = 0;
		
		for (ArbitrarilyDiscretizedFunc func : refFuncs) {
			ArbitrarilyDiscretizedFunc unconvergedFunc = new ArbitrarilyDiscretizedFunc();
			ArbitrarilyDiscretizedFunc convergedFunc = null;
			ArbitrarilyDiscretizedFunc curFunc = unconvergedFunc;
			double transitionEnergy = 0;
			double transitionDelta = 0;
			double transitionPercent = 0;
			for (int i=0; i<func.size(); i++) {
				double x = func.getX(i);
				if ((x - lookBack - 0.001) < func.getMinX())
					continue;
				double curVal = func.getY(i);
				double prevVal = func.getInterpolatedY(x-lookBack);
				double pDiff = DataUtils.getPercentDiff(curVal, prevVal);
				double diff = prevVal - curVal;
				if (convergedFunc == null && pDiff < percentThreshold && diff < changeThreshold) {
					// this is the first time we've crossed the threshold!
					convergedFunc = new ArbitrarilyDiscretizedFunc();
					curFunc = convergedFunc;
					transitionEnergy = curVal;
					transitionDelta = diff;
					transitionPercent = pDiff;
					xSaved += func.getMaxX() - x;
					xTot += func.getMaxX();
				}
				if (setMod <= 1 || i % setMod == 0) {
					switch (plot) {
					case CONVERGENCE_CHANGE:
						curFunc.set(x, diff);
						break;
					case CONVERGENCE_PERCENT_CHANGE:
						curFunc.set(x, pDiff);
						break;
					case CONVERGENCE_ENERGY:
						curFunc.set(x, curVal);
						break;
					}
				}
			}
			if (colorCnt == colors.size())
				colorCnt = 0;
			Color color = colors.get(colorCnt++);
			unconvergedFunc.setName(func.getName()+" (unconverged)");
			funcs.add(unconvergedFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, color));
			if (convergedFunc != null) {
				convergedFunc.setName(func.getName()+" (converged)");
				double finalEnergy = func.getY(func.size()-1);
				double postConvergeImp = DataUtils.getPercentDiff(finalEnergy, transitionEnergy);
				convergedFunc.setInfo("Post converge: "+transitionEnergy+" => "+finalEnergy+" ("
						+(transitionEnergy-finalEnergy)+" = "+(float)postConvergeImp+" %)"
						+"\nImprovement at Transition Point: "+(transitionDelta+transitionEnergy)
						+" => "+transitionEnergy+" ("+transitionDelta+" = "+(float)transitionPercent+" %)");
				funcs.add(convergedFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, color));
			}
		}
		
		System.out.println(xUnit+" saved: "+xSaved+"/"+xTot+" ("+(float)(xSaved/xTot * 100d)+" %)");
		
		PlotSpec spec = convergeGP.getPlotSpec();
		spec.setXAxisLabel(xAxisName);
		spec.setYAxisLabel(yAxisName);
		spec.setPlotElems(funcs);
		spec.setChars(chars);
		spec.setTitle(title);
		convergeGP.setX_Log(false);
		convergeGP.setY_Log(false);
		convergeGP.drawGraph();
		convergeGP.validate();
		convergeGP.repaint();

	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		Parameter<?> param = event.getParameter();
		if (param == plotTypeParam) {
			updatePlot();
		} else if (param == refreshButton) {
			try {
				VariableLogicTreeBranch branch = getCurrentBranch();
				loadResultFiles(branch);
				buildFunctions(branch);
				updatePlot();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (param.getName().startsWith(VARIATION_PARAM_NAME)) {
			VariableLogicTreeBranch branch = getCurrentBranch();
			buildFunctions(branch);
			updatePlot();
		} else if (param == timeBasedParam) {
			lookBackParam.removeParameterChangeListener(this);
			if (timeBasedParam.getValue())
				lookBackParam.setValue(LOOK_BACK_DEFAULT_TIME);
			else
				lookBackParam.setValue(LOOK_BACK_DEFAULT_ITERATIONS);
			lookBackParam.getEditor().refreshParamEditor();
			lookBackParam.addParameterChangeListener(this);
			updatePlot();
		} else if (param == lookBackParam || param == energyChangeThresholdParam
				|| param == energyPercentChangeThresholdParam) {
			updatePlot();
		}
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		InversionConvergenceGUI ic = new InversionConvergenceGUI();
//		File file = new File("D:\\Documents\\temp\\csvs.zip");
		File file = new File("/tmp/2012_03_05-epicenter-test.zip");
		if (file.exists())
			ic.browseParam.setValue(file);
	}

}

