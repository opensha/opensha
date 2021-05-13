package scratch.UCERF3.analysis;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.editor.impl.GriddedParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.ButtonParameter;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.FileParameter;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.AverageFaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.FindEquivUCERF2_Ruptures.FindEquivUCERF2_FM2pt1_Ruptures;
import scratch.UCERF3.utils.FindEquivUCERF2_Ruptures.FindEquivUCERF2_FM3_Ruptures;
import scratch.UCERF3.utils.FindEquivUCERF2_Ruptures.FindEquivUCERF2_Ruptures;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class RupRateConvergenceGUI extends JFrame implements ParameterChangeListener {
	
	private static final int DEFAULT_PLOT_WIDTH = 100;
	
	private AverageFaultSystemSolution sol;
	
	private static final String BROWSE_PARAM_NAME = "Browse";
	private FileParameter browseParam = new FileParameter(BROWSE_PARAM_NAME);
	
	private static final String UCERF2_PARAM_NAME = "Only UCERF2 Equiv Rups";
	private BooleanParameter ucerf2Param = new BooleanParameter(UCERF2_PARAM_NAME, false);
	
	private static final String SDOM_N_PARAM_NAME = "N (for SDOM)";
	private static final Integer SDOM_N_PARAM_MIN = 1;
	private static final Integer SDOM_N_PARAM_MAX = 10000;
	private IntegerParameter sdomNParam = new IntegerParameter(
			SDOM_N_PARAM_NAME, SDOM_N_PARAM_MIN, SDOM_N_PARAM_MAX);
	
	private static final String LOG_PARAM_NAME = "Log Y Scale";
	private static final Boolean LOG_PARAM_DEFAULT = false;
	private BooleanParameter logParam = new BooleanParameter(LOG_PARAM_NAME, LOG_PARAM_DEFAULT);
	
	private static final String PARENT_SECT_FILTER_PARAM_NAME = "Parent Section Filter";
	private static final String PARENT_SECT_FILTER_PARAM_DEFAULT = "(none)";
	private StringParameter parentSectParam;
	
	private static final String SORT_PARAM_NAME = "Sort By";
	private enum SortTypes {
		INDEX("Rupture Index") {
			@Override
			public Comparator<Integer> getComparator(AverageFaultSystemSolution sol, int n) {
				return new Comparator<Integer>() {

					@Override
					public int compare(Integer o1, Integer o2) {
						return o1.compareTo(o2);
					}
				};
			}
		},
		MAG_INCREASING("Mag (Increasing)") {
			@Override
			public Comparator<Integer> getComparator(final AverageFaultSystemSolution sol, int n) {
				return new Comparator<Integer>() {

					@Override
					public int compare(Integer o1, Integer o2) {
						double m1 = sol.getRupSet().getMagForRup(o1);
						double m2 = sol.getRupSet().getMagForRup(o2);
						return Double.compare(m1, m2);
					}
					
				};
			}
		},
		MAG_DECREASING("Mag (Decreasing)") {
			@Override
			public Comparator<Integer> getComparator(final AverageFaultSystemSolution sol, int n) {
				return new Comparator<Integer>() {

					@Override
					public int compare(Integer o1, Integer o2) {
						double m1 = sol.getRupSet().getMagForRup(o1);
						double m2 = sol.getRupSet().getMagForRup(o2);
						return -Double.compare(m1, m2);
					}
					
				};
			}
		},
		STD_DEV_OVER_MEAN("Std Dev / Mean") {
			@Override
			public Comparator<Integer> getComparator(final AverageFaultSystemSolution sol, int n) {
				return new Comparator<Integer>() {

					@Override
					public int compare(Integer o1, Integer o2) {
						double d1 = sol.getRateStdDev(o1) / sol.getRateForRup(o1);
						double d2 = sol.getRateStdDev(o2) / sol.getRateForRup(o2);
						return Double.compare(d1, d2);
					}
					
				};
			}
		},
		STD_DEV_OF_MEAN_OVER_MEAN("SDOM / Mean") {
			@Override
			public Comparator<Integer> getComparator(final AverageFaultSystemSolution sol, final int n) {
				return new Comparator<Integer>() {

					@Override
					public int compare(Integer o1, Integer o2) {
						double stdDev1 = sol.getRateStdDev(o1);
						double stdDev2 = sol.getRateStdDev(o2);
						double mean1 = sol.getRateForRup(o1);
						double mean2 = sol.getRateForRup(o2);
						double sdom1 = stdDev1 / Math.sqrt(n);
						double sdom2 = stdDev2 / Math.sqrt(n);
						double d1 = sdom1 / mean1;
						double d2 = sdom2 / mean2;
						return Double.compare(d1, d2);
					}
					
				};
			}
		};
		
		private String name;
		private SortTypes(String name) {
			this.name = name;
		}
		
		@Override
		public String toString() {
			return name;
		}
		
		public abstract Comparator<Integer> getComparator(AverageFaultSystemSolution sol, int n);
	}
	private EnumParameter<SortTypes> sortParam = new EnumParameter<RupRateConvergenceGUI.SortTypes>(
			SORT_PARAM_NAME, EnumSet.allOf(SortTypes.class), SortTypes.INDEX, null);
	
	private static final String ZOOM_IN_PARAM_NAME = "Zoom In";
	private ButtonParameter zoomInParam = new ButtonParameter(ZOOM_IN_PARAM_NAME, "+");
	
	private static final String ZOOM_OUT_PARAM_NAME = "Zoom Out";
	private ButtonParameter zoomOutParam = new ButtonParameter(ZOOM_OUT_PARAM_NAME, "-");
	
	private static final String START_PARAM_NAME = "Start";
	private ButtonParameter startParam = new ButtonParameter(START_PARAM_NAME, "|<");
	
	private static final String PREV_PAGE_PARAM_NAME = "Prev Page";
	private ButtonParameter prevPageParam = new ButtonParameter(PREV_PAGE_PARAM_NAME, "<<<");
	
	private static final String PREV_HALF_PAGE_PARAM_NAME = "Prev 1/2 Page";
	private ButtonParameter prevHalfPageParam = new ButtonParameter(PREV_HALF_PAGE_PARAM_NAME, "<<");
	
	private static final String PREV_RUP_PARAM_NAME = "Prev Rup";
	private ButtonParameter prevRupParam = new ButtonParameter(PREV_RUP_PARAM_NAME, "<");
	
	private static final String END_PARAM_NAME = "End";
	private ButtonParameter endParam = new ButtonParameter(END_PARAM_NAME, ">|");
	
	private static final String NEXT_PAGE_PARAM_NAME = "Next Page";
	private ButtonParameter nextPageParam = new ButtonParameter(NEXT_PAGE_PARAM_NAME, ">>>");
	
	private static final String NEXT_HALF_PAGE_PARAM_NAME = "Next 1/2 Page";
	private ButtonParameter nextHalfPageParam = new ButtonParameter(NEXT_HALF_PAGE_PARAM_NAME, ">>");
	
	private static final String NEXT_RUP_PARAM_NAME = "Next Rup";
	private ButtonParameter nextRupParam = new ButtonParameter(NEXT_RUP_PARAM_NAME, ">");
	
	private ArrayList<String> parentNames;
	private ArrayList<Integer> parentIDs;
	
	private HeadlessGraphPanel gp;
	private ArrayList<DiscretizedFunc> funcs;
	private ArrayList<PlotCurveCharacterstics> chars;
	private double[] stdDevs;
	private FindEquivUCERF2_Ruptures ucerf2Rups;
	private EvenlyDiscretizedFunc meanFunc;
	private EvenlyDiscretizedFunc minFunc;
	private EvenlyDiscretizedFunc maxFunc;
	private EvenlyDiscretizedFunc meanPlusStdDevFunc;
	private EvenlyDiscretizedFunc meanMinusStdDevFunc;
	private EvenlyDiscretizedFunc meanPlusStdDevOfMeanFunc;
	private EvenlyDiscretizedFunc meanMinusStdDevOfMeanFunc;
	private ArbitrarilyDiscretizedFunc ucerf2RatesFunc;
	
	private List<Integer> curRups;
	
	private ParameterList controlParamList;
	
	private JTextArea labels;
	
	public RupRateConvergenceGUI() {
//		super(new BorderLayout());
		
		ParameterList paramList = new ParameterList();
		paramList.addParameter(browseParam);
		paramList.addParameter(ucerf2Param);
		sdomNParam.setValue(SDOM_N_PARAM_MIN);
		paramList.addParameter(sdomNParam);
		paramList.addParameter(logParam);
		ArrayList<String> parentSects = Lists.newArrayList(PARENT_SECT_FILTER_PARAM_DEFAULT);
		parentSectParam = new StringParameter(PARENT_SECT_FILTER_PARAM_NAME, parentSects, PARENT_SECT_FILTER_PARAM_DEFAULT);
		paramList.addParameter(parentSectParam);
		paramList.addParameter(sortParam);
		
		controlParamList = new ParameterList();
		controlParamList.addParameter(zoomOutParam);
		controlParamList.addParameter(zoomInParam);
		controlParamList.addParameter(startParam);
		controlParamList.addParameter(prevPageParam);
		controlParamList.addParameter(prevHalfPageParam);
		controlParamList.addParameter(prevRupParam);
		controlParamList.addParameter(nextRupParam);
		controlParamList.addParameter(nextHalfPageParam);
		controlParamList.addParameter(nextPageParam);
		controlParamList.addParameter(endParam);
		
		for (Parameter<?> param : paramList)
			param.addParameterChangeListener(this);
		
		for (Parameter<?> param : controlParamList)
			param.addParameterChangeListener(this);
		
		GriddedParameterListEditor paramEdit = new GriddedParameterListEditor(paramList, 1, 0);
		GriddedParameterListEditor controlParamEdit = new GriddedParameterListEditor(controlParamList, 1, 0);
		
		enableButtons();
		
		JPanel mainPanel = new JPanel(new BorderLayout());
		this.setContentPane(mainPanel);
		
		gp = new HeadlessGraphPanel();
		
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.add(paramEdit);
		topPanel.add(controlParamEdit);
		
		mainPanel.add(topPanel, BorderLayout.NORTH);
		mainPanel.add(gp, BorderLayout.CENTER);
		
		labels = new JTextArea("");
		labels.setEditable(false);
		
		mainPanel.add(labels, BorderLayout.SOUTH);
		
		setTitle("Rupture Rate Convergence GUI");
		setSize(1400, 800);
	}
	
	private void setSol(AverageFaultSystemSolution sol) {
		this.sol = sol;
		ucerf2Rups = null;
		if (sol == null) {
			meanFunc = null;
			minFunc = null;
			maxFunc = null;
			meanPlusStdDevFunc = null;
			meanMinusStdDevFunc = null;
			meanPlusStdDevOfMeanFunc = null;
			meanMinusStdDevOfMeanFunc = null;
			stdDevs = null;
		} else {
			int numRups = sol.getRupSet().getNumRuptures();
			stdDevs = new double[numRups];
			meanFunc = new EvenlyDiscretizedFunc(0, numRups, 1d);
			meanFunc.setName("Mean Rate");
			minFunc = new EvenlyDiscretizedFunc(0, numRups, 1d);
			minFunc.setName("Min Rate");
			maxFunc = new EvenlyDiscretizedFunc(0, numRups, 1d);
			maxFunc.setName("Max Rate");
			meanPlusStdDevFunc = new EvenlyDiscretizedFunc(0, numRups, 1d);
			meanPlusStdDevFunc.setName("Mean + Std Dev");
			meanMinusStdDevFunc = new EvenlyDiscretizedFunc(0, numRups, 1d);
			meanMinusStdDevFunc.setName("Mean - Std Dev");
			meanPlusStdDevOfMeanFunc = new EvenlyDiscretizedFunc(0, numRups, 1d);
			meanPlusStdDevOfMeanFunc.setName("Mean + Std Dev Of Mean");
			meanMinusStdDevOfMeanFunc = new EvenlyDiscretizedFunc(0, numRups, 1d);
			meanMinusStdDevOfMeanFunc.setName("Mean - Std Dev Of Mean");
			ucerf2RatesFunc = new ArbitrarilyDiscretizedFunc();
			ucerf2RatesFunc.setName("UCERF2 Rates");
			
			sdomNParam.setValue(sol.getNumSolutions());
			sdomNParam.getEditor().refreshParamEditor();
			
			ArrayList<double[]> ucerf2_magsAndRates = UCERF3InversionConfiguration.getUCERF2MagsAndrates(sol.getRupSet());
			
			for (int r=0; r<numRups; r++) {
				double mean = sol.getRateForRup(r);
				double stdDev = sol.getRateStdDev(r);
				stdDevs[r] = stdDev;
				double min = sol.getRateMin(r);
				double max = sol.getRateMax(r);
				meanFunc.set(r, mean);
				minFunc.set(r, min);
				maxFunc.set(r, max);
				meanPlusStdDevFunc.set(r, mean + stdDev);
				meanMinusStdDevFunc.set(r, mean - stdDev);
				double[] ucerf2_vals = ucerf2_magsAndRates.get(r);
				if (ucerf2_vals != null)
					ucerf2RatesFunc.set((double)r, ucerf2_vals[1]);
			}
			updateSDOM();
		}
		
		// update parent sections
		parentNames = Lists.newArrayList(PARENT_SECT_FILTER_PARAM_DEFAULT);
		parentIDs = Lists.newArrayList(-1);
		if (sol != null) {
			int prevParentID = -2;
			for (FaultSection sect : sol.getRupSet().getFaultSectionDataList()) {
				int parentID = sect.getParentSectionId();
				
				if (parentID != prevParentID) {
					parentNames.add(sect.getParentSectionName());
					parentIDs.add(parentID);
					prevParentID = parentID;
				}
			}
		}
		parentSectParam.removeParameterChangeListener(this);
		parentSectParam.setValue(PARENT_SECT_FILTER_PARAM_DEFAULT);
		((StringConstraint)parentSectParam.getConstraint()).setStrings(parentNames);
		parentSectParam.getEditor().refreshParamEditor();
		parentSectParam.addParameterChangeListener(this);
		
		enableButtons();
	}
	
	private void updateSDOM() {
		if (sol == null)
			return;
		int n = sdomNParam.getValue();
		for (int i=0; i<sol.getRupSet().getNumRuptures(); i++) {
			double stdDev = stdDevs[i];
			double mean = meanFunc.getY(i);
			double sdom = stdDev / Math.sqrt(n);
			meanPlusStdDevOfMeanFunc.set(i, mean + sdom);
			meanMinusStdDevOfMeanFunc.set(i, mean - sdom);
		}
	}
	
	private void enableButtons() {
		boolean enable = sol != null && funcs != null;
		
		for (Parameter<?> param : controlParamList) {
			param.getEditor().setEnabled(enable);
			param.getEditor().refreshParamEditor();
		}
	}
	
	private void rebuildPlot() {
		funcs = Lists.newArrayList();
		chars = Lists.newArrayList();
		
		if (sol != null) {
			float normalWidth = 4f;
			float meanWidth = 10f;
			
			funcs.add(meanFunc);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.DASH, meanWidth, Color.BLACK));
			funcs.add(minFunc);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.DASH, normalWidth, Color.RED));
			funcs.add(maxFunc);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.DASH, normalWidth, Color.RED));
			funcs.add(meanPlusStdDevFunc);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.DASH, normalWidth, Color.GREEN));
			funcs.add(meanMinusStdDevFunc);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.DASH, normalWidth, Color.GREEN));
			funcs.add(meanPlusStdDevOfMeanFunc);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.DASH, normalWidth, Color.BLUE));
			funcs.add(meanMinusStdDevOfMeanFunc);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.DASH, normalWidth, Color.BLUE));
			funcs.add(ucerf2RatesFunc);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.X, normalWidth*0.5f, Color.GRAY));
			
			if (sol != null && ucerf2Param.getValue()) {
				System.out.println("Using UCERF2 Rups!");
				if (ucerf2Rups == null) {
					if (sol.getRupSet().getFaultModel() == FaultModels.FM2_1)
						ucerf2Rups = new FindEquivUCERF2_FM2pt1_Ruptures(
								sol.getRupSet(), UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR);
					else
						ucerf2Rups = new FindEquivUCERF2_FM3_Ruptures(
								sol.getRupSet(), UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, sol.getRupSet().getFaultModel());
				}
				
				curRups = Lists.newArrayList();
				HashSet<Integer> rupsSet = new HashSet<Integer>(); // for speed of contains() ops
				for (int r=0; r<ucerf2Rups.getNumUCERF2_Ruptures(); r++) {
					int ind = ucerf2Rups.getEquivFaultSystemRupIndexForUCERF2_Rupture(r);
					if (ind >= 0 && !rupsSet.contains(ind)) {
						curRups.add(ind);
						rupsSet.add(ind);
					}
				}
			} else {
				curRups = Lists.newArrayList();
				for (int i=0; i<sol.getRupSet().getNumRuptures(); i++)
					curRups.add(i);
			}
			
			if (!parentSectParam.getValue().equals(PARENT_SECT_FILTER_PARAM_DEFAULT)) {
				String parentName = parentSectParam.getValue();
				int parentID = parentIDs.get(parentNames.indexOf(parentName));
				
				List<Integer> subSet = Lists.newArrayList();
				
				for (int r : curRups) {
					List<Integer> parents = sol.getRupSet().getParentSectionsForRup(r);
					if (parents.contains(parentID))
						subSet.add(r);
				}
				
				curRups = subSet;
			}
			
			if (curRups.isEmpty()) {
				funcs = Lists.newArrayList();
				chars = Lists.newArrayList();
			} else {
				// now sort
				Collections.sort(curRups, sortParam.getValue().getComparator(sol, sdomNParam.getValue()));
				
				funcs = getForSubset();
				
				int minX = 0;
				int maxX = DEFAULT_PLOT_WIDTH-1;
				if (maxX >= curRups.size())
					maxX = curRups.size()-1;
				double[] yBounds = getYBounds(minX, maxX);
				gp.setUserBounds(minX, maxX, yBounds[0], yBounds[1]);
				updateSectionsLabel(minX, maxX);
			}
		}
		
		if (funcs.size() == 0)
			updateSectionsLabel(-1, -1);
		
		drawGraph();
		
		enableButtons();
	}
	
	private ArrayList<DiscretizedFunc> getForSubset() {
		ArrayList<DiscretizedFunc> newFuncs = Lists.newArrayList();
		for (DiscretizedFunc func : funcs) {
			if (func instanceof EvenlyDiscretizedFunc) {
				EvenlyDiscretizedFunc newFunc = new EvenlyDiscretizedFunc(0, curRups.size(), 1d);
				newFunc.setName(func.getName());
				
				for (int i=0; i<curRups.size(); i++) {
					newFunc.set(i, func.getY(curRups.get(i)));
				}
				
				newFuncs.add(newFunc);
			} else {
				ArbitrarilyDiscretizedFunc newFunc = new ArbitrarilyDiscretizedFunc();
				newFunc.setName(func.getName());
				for (int i=0; i<curRups.size(); i++) {
					int ind = func.getXIndex(curRups.get(i));
					if (ind >= 0)
						newFunc.set((double)i, func.getY(ind));
				}
				newFuncs.add(newFunc);
			}
		}
		return newFuncs;
	}
	
	private void drawGraph() {
		gp.setYLog(logParam.getValue());
		gp.drawGraphPanel("Rupture Index", "Rate", funcs, chars, "Rup Rate Convergence");
	}
	
	private double[] getYBounds(int minX, int maxX) {
		double minY = Double.POSITIVE_INFINITY;
		double minNonZero = Double.POSITIVE_INFINITY;
		double maxY = 0;
		
		for (DiscretizedFunc func : funcs) {
			if (func instanceof EvenlyDiscretizedFunc) {
				for (int x=minX; x<=maxX; x++) {
					double y = func.getY(x);
					if (y < minY)
						minY = y;
					if (y > maxY)
						maxY = y;
					if (y > 0 && y < minNonZero)
						minNonZero = y;
				}
			} else {
				for (int x=minX; x<=maxX; x++) {
					int ind = func.getXIndex((double)x);
					if (ind >= 0) {
						double y = func.getY(ind);
						if (y < minY)
							minY = y;
						if (y > maxY)
							maxY = y;
						if (y > 0 && y < minNonZero)
							minNonZero = y;
					}
				}
			}
		}
		
		if (logParam.getValue()) {
			minY = minNonZero;
		}
		
		if (Double.isInfinite(minY) || minY > maxY)
			minY = maxY;
		
		if (logParam.getValue()) {
			if (minY <= 0)
				minY = 1e-10;
			if (maxY < minY)
				maxY = minY;
		}
		
		double[] ret = {minY, maxY};
		return ret;
	}
	
	private void updatePlotRange(int min, int max) {
		double[] yBounds = getYBounds(min, max);
		double minY = yBounds[0]*0.9d;
		double maxY = yBounds[1]*1.1d;
		gp.setUserBounds(min, max, minY, maxY);
		gp.getXAxis().setRange(min, max);
		gp.getYAxis().setRange(minY, maxY);
		updateSectionsLabel(min, max);
//		drawGraph();
//		gp.validate();
//		gp.repaint();
	}
	
	private void updateSectionsLabel(int minX, int maxX) {
		String startLabel;
		String endLabel;
		
		if (sol == null || minX < 0) {
			startLabel = "";
			endLabel = "";
		} else {
			int startRup = curRups.get(minX);
			int endRup = curRups.get(maxX);
			
			startLabel = getLabelForRup(startRup);
			endLabel = getLabelForRup(endRup);
		}
		
		String label = "FIRST RUP:\t"+startLabel+"\nLAST RUP:\t"+endLabel;
		labels.setText(label);
	}
	
	private String getLabelForRup(int rupIndex) {
		List<Integer> inds = sol.getRupSet().getSectionsIndicesForRup(rupIndex);
		
		List<String> parentNames = Lists.newArrayList();
		int lastParentID = -2;
		
		for (int ind : inds) {
			FaultSection sect = sol.getRupSet().getFaultSectionData(ind);
			int parent = sect.getParentSectionId();
			if (parent != lastParentID) {
				parentNames.add(sect.getParentSectionName());
				lastParentID = parent;
			}
		}
		
		return "Mag: "+(float)sol.getRupSet().getMagForRup(rupIndex)+"\tParent Sects: "+Joiner.on("; ").join(parentNames);
	}
	
	private int[] getCurrentBounds() {
		int min;
		int max;
		try {
			min = (int)gp.getX_AxisRange().getLowerBound();
			max = (int)gp.getX_AxisRange().getUpperBound();
		} catch (Exception e) {
			min = 0;
			max = 0;
		}
		
		int[] ret = {min, max};
		return ret;
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		Parameter<?> param = event.getParameter();
		
		int[] range = getCurrentBounds();
		int min = range[0];
		if (min < 0)
			min = 0;
		int max = range[1];
		int num = max - min;
		
		if (param == browseParam) {
			AverageFaultSystemSolution sol = null;
			try {
				sol = FaultSystemIO.loadAvgInvSol(browseParam.getValue());
			} catch (Exception e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(this, "Error loading average solution:\n"+e.getMessage(),
						"Error Loading Average Solution", JOptionPane.ERROR_MESSAGE);
			}
			setSol(sol);
			rebuildPlot();
		} else if (param == ucerf2Param) {
			rebuildPlot();
		} else if (param == sdomNParam) {
			int[] bounds = getCurrentBounds();
			updateSDOM();
			rebuildPlot();
			updatePlotRange(bounds[0], bounds[1]);
		} else if (param == logParam) {
			int[] bounds = getCurrentBounds();
			rebuildPlot();
			updatePlotRange(bounds[0], bounds[1]);
		} else if (param == parentSectParam) {
			rebuildPlot();
		} else if (param == sortParam) {
			rebuildPlot();
		} else {
			int totSize = funcs.get(0).size();
			
			// move/zoom buttons
			if (param == startParam) {
				min = 0;
				max = num;
			} else if (param == prevPageParam) {
				min -= num;
				max -= num;
			} else if (param == prevHalfPageParam) {
				min -= (num / 2);
				max -= (num / 2);
			} else if (param == prevRupParam) {
				min--;
				max--;
			} else if (param == endParam) {
				max = totSize-1;
				min = max - num;
			} else if (param == nextPageParam) {
				min += num;
				max += num;
			} else if (param == nextHalfPageParam) {
				min += (num / 2);
				max += (num / 2);
			} else if (param == nextRupParam) {
				min++;
				max++;
			} else if (param == zoomInParam) {
				int size = (int)((double)num * 2d / 3d);
				max = min + size;
			} else if (param == zoomOutParam) {
				int size = (int)((double)num * 1.5d);
				max = min + size;
			}
			
			if (min < 0) {
				int below = 0 - min;
				min += below;
				max += below;
			} else if (max >= totSize) {
				int above = max - totSize;
				max -= above;
				min -= above;
			}
			updatePlotRange(min, max);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		RupRateConvergenceGUI gui = new RupRateConvergenceGUI();
		
		gui.setVisible(true);
		gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

}
