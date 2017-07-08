/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.gcim.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import javax.swing.UIManager;

import org.apache.commons.lang3.SystemUtils;
import org.jfree.data.Range;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.WeightedFuncListforPlotting;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.exceptions.WarningException;
import org.opensha.commons.gui.ControlPanel;
import org.opensha.commons.gui.DisclaimerDialog;
import org.opensha.commons.gui.HelpMenuBuilder;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.ListUtils;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.bugReports.BugReport;
import org.opensha.commons.util.bugReports.BugReportDialog;
import org.opensha.commons.util.bugReports.DefaultExceptoinHandler;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.HazardCurveCalculatorAPI;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.FloatingPoissonFaultERF;
import org.opensha.sha.earthquake.rupForecastImpl.PoissonFaultERF;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel02.Frankel02_AdjustableEqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.PEER_TestCases.PEER_AreaForecast;
import org.opensha.sha.earthquake.rupForecastImpl.PEER_TestCases.PEER_MultiSourceForecast;
import org.opensha.sha.earthquake.rupForecastImpl.PEER_TestCases.PEER_NonPlanarFaultForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WG02.WG02_EqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2_TimeIndependentEpistemicList;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.step.STEP_AlaskanPipeForecast;
import org.opensha.sha.gcim.calc.DisaggregationCalculator;
import org.opensha.sha.gcim.calc.DisaggregationCalculatorAPI;
import org.opensha.sha.gcim.calc.GcimCalculator;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.gcim.ui.infoTools.AttenuationRelationshipsInstance;
import org.opensha.sha.gcim.ui.infoTools.GcimPlotViewerWindow;
import org.opensha.sha.gcim.ui.infoTools.IMT_Info;
import org.opensha.sha.gui.HazardCurveApplication;
import org.opensha.sha.gui.beans.ERF_GuiBean;
import org.opensha.sha.gui.beans.EqkRupSelectorGuiBean;
import org.opensha.sha.gui.beans.IMR_GuiBean;
import org.opensha.sha.gui.beans.IMR_MultiGuiBean;
import org.opensha.sha.gui.beans.IMT_NewGuiBean;
import org.opensha.sha.gui.beans.Site_GuiBean;
import org.opensha.sha.gui.controls.CalculationSettingsControlPanel;
import org.opensha.sha.gui.controls.DisaggregationControlPanel;
import org.opensha.sha.gui.controls.ERF_EpistemicListControlPanel;
import org.opensha.sha.gui.controls.PEER_TestCaseSelectorControlPanel;
import org.opensha.sha.gui.controls.PlottingOptionControl;
import org.opensha.sha.gui.controls.RunAll_PEER_TestCasesControlPanel;
import org.opensha.sha.gui.controls.SiteDataControlPanel;
import org.opensha.sha.gui.controls.SitesOfInterestControlPanel;
import org.opensha.sha.gui.controls.XY_ValuesControlPanel;
import org.opensha.sha.gui.controls.X_ValuesInCurveControlPanel;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.gui.infoTools.DisaggregationPlotViewerWindow;
import org.opensha.sha.gui.util.IconFetcher;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.event.ScalarIMRChangeEvent;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.collect.Lists;


/**
 * <p>
 * Title: HazardCurveServerModeApplication
 * </p>
 * <p>
 * Description: This application computes Hazard Curve for selected
 * AttenuationRelationship model , Site and Earthquake Rupture Forecast
 * (ERF)model. This computed Hazard curve is shown in a panel using JFreechart.
 * This application works with/without internet connection. If user using this
 * application has network connection then it creates the instances of ERF on
 * server and make all calls to server for any forecast updation. All the
 * computation in this application is done using the server. Once the
 * computations complete, it returns back the result. All the server client
 * relationship has been established using RMI, which allows to make simple
 * calls to the server similar to if things are existing on user's own machine.
 * If network connection is not available to user then it will create all the
 * objects on users local machine and do all computation there itself.
 * </p>
 * 
 * @author Nitin Gupta and Vipin Gupta Date : Sept 23 , 2002
 * @version 1.0
 */

public class GCIM_HazardCurveApp  extends HazardCurveApplication {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static ApplicationVersion version;
	
	public static final String APP_NAME = "GCIM Enabled Hazard Curve Application";
	public static final String APP_SHORT_NAME = "GCIM_HazardCurve";

	/**
	 * Name of the class
	 */
	private final static String C = "GCIM_HazardCurveApplication";
	// for debug purpose
	protected final static boolean D = false;

	// Strings for choosing ERFGuiBean or ERF_RupSelectorGUIBean
	public final static String PROBABILISTIC = "Probabilistic";
	public final static String DETERMINISTIC = "Deterministic";
	public final static String STOCHASTIC = "Stochastic Event Sets";

	// Strings for control pick list
	protected final static String CONTROL_PANELS = "Select";

	// objects for control panels
	protected PEER_TestCaseSelectorControlPanel peerTestsControlPanel;
	protected DisaggregationControlPanel disaggregationControlPanel;
	protected GcimControlPanel gcimControlPanel;
	protected ERF_EpistemicListControlPanel epistemicControlPanel;
	//	protected SetMinSourceSiteDistanceControlPanel distanceControlPanel;
	protected CalculationSettingsControlPanel calcParamsControl;
	protected SitesOfInterestControlPanel sitesOfInterest;
	protected SiteDataControlPanel cvmControlPanel;
	protected X_ValuesInCurveControlPanel xValuesPanel;
	private RunAll_PEER_TestCasesControlPanel runAllPeerTestsCP;
	protected PlottingOptionControl plotOptionControl;
	protected XY_ValuesControlPanel xyPlotControl;

	private ArrayList<ControlPanel> controlPanels;

	// default insets
	protected Insets defaultInsets = new Insets(4, 4, 4, 4); ///TODO remove

	/**
	 * List of ArbitrarilyDiscretized functions and Weighted funstions
	 */
	protected ArrayList<PlotElement> functionList = new ArrayList<PlotElement>();

	// holds the ArbitrarilyDiscretizedFunc
	protected ArbitrarilyDiscretizedFunc function;

	// instance to get the default IMT X values for the hazard Curve
	protected IMT_Info imtInfo = new IMT_Info();

	// variable needed for plotting Epistemic list
	protected boolean isEqkList = false; // whther we are plottin the Eqk List
	// private boolean isIndividualCurves = false; //to keep account that we are
	// first drawing the individual curve for erf in the list
	protected boolean isAllCurves = true; // whether to plot all curves
	// whether user wants to plot custom fractile
	protected String fractileOption;
	// whether avg is selected by the user
	protected boolean avgSelected = false;

	// Variables required to update progress bar if ERF List is selected
	// total number of ERF's in list
	protected int numERFsInEpistemicList = 0;
	// index number of ERF for which Hazard Curve is being calculated
	protected int currentERFInEpistemicListForHazardCurve = 0;

	// flags to check which X Values the user wants to work with: default or
	// custom
	boolean useCustomX_Values = false;

	// flag to check for the disaggregation functionality
	protected boolean disaggregationFlag = false;
	private String disaggregationString;
	
	// flag to check for the gcim functionality
	protected boolean gcimFlag = false;
	private String gcimString;
	protected boolean gcimIMiChangeFlag = false;

	// These keep track of which type of calculation is chosen (only one should be true at any time);
	protected boolean isProbabilisticCurve = true;
	protected boolean isDeterministicCurve = false;
	protected boolean isStochasticCurve = false;

	// PEER Test Cases
	private static final String DEFAULT_TITLE = new String("Hazard Curves");


	// accessible components
	private JMenuItem saveMenuItem;
	private JMenuItem printMenuItem;
	private JMenuItem closeMenuItem;

	//	private JButton saveButton; TODO clean
	//	private JButton printButton;
	//	private JButton closeButton;

	private JButton computeButton;
	private JButton cancelButton;
	private JButton clearButton;
	private JButton peelButton;
	protected JCheckBox progressCheckBox; // TODO make private
	protected JComboBox controlComboBox; // TODO make private
	protected JComboBox probDeterComboBox;

	private JPanel plotPanel;
	//private JPanel sitePanel;
	//protected JPanel imrPanel; // TODO make private
	//protected JPanel imtPanel; // TODO make private
	//protected JPanel erfPanel; // TODO make private

	private JSplitPane imrImtSplitPane;
	private JTabbedPane paramsTabbedPane;
	private GraphWidget graphWidget; // actual plot panel

	protected IMR_MultiGuiBean imrGuiBean;
	private IMT_NewGuiBean imtGuiBean;
	protected Site_GuiBean siteGuiBean;
	protected ERF_GuiBean erfGuiBean;
	protected EqkRupSelectorGuiBean erfRupSelectorGuiBean;



	// instances of various calculators
	protected HazardCurveCalculatorAPI calc;
	protected DisaggregationCalculatorAPI disaggCalc;
	protected GcimCalculator gcimCalc;
	CalcProgressBar progressClass;
	CalcProgressBar disaggProgressClass;
	CalcProgressBar gcimProgressClass;
	protected CalcProgressBar startAppProgressClass;
	// timer threads to show the progress of calculations
	Timer timer;
	Timer disaggTimer;
	Timer gcimTimer;
	// calculation thead
	Thread calcThread;
	// checks to see if HazardCurveCalculations are done
	boolean isHazardCalcDone = false;



	//	private final static String POWERED_BY_IMAGE = TODO clean
	//			"logos/PoweredByOpenSHA_Agua.jpg";
	//	private JLabel imgLabel = new JLabel(new ImageIcon(
	//			ImageUtils.loadImage(this.POWERED_BY_IMAGE)));

	// maintains which ERFList was previously selected
	protected String prevSelectedERF_List = null;

	// keeps track which was the last selected Weighted function list.
	// It only initialises this weighted function list if user wants to add data
	// to the existing ERF_List
	protected WeightedFuncListforPlotting weightedFuncList;

	/**
	 * this boolean keeps track when to plot the new data on top of other and
	 * when to add to the existing data. If it is true then add new data on top
	 * of existing data, but if it is false then add new data to the existing
	 * data(this option only works if it is ERF_List).
	 * */
	boolean addData = true;
	
	protected static String errorInInitializationMessage = "Problem occured " +
				"during initialization the ERF's. All parameters are set to default.";

	// Construct the applet
	public GCIM_HazardCurveApp(String appShortName) {
		super(appShortName);
	}

	// Initialize the applet
	public void init() {
		try {

			startAppProgressClass = new CalcProgressBar("Starting Application",
			"Initializing Application .. Please Wait");

			// initialize the various GUI beans
			initIMR_GuiBean();
			initIMT_GuiBean();
			initSiteGuiBean();

			try {
				initERF_GuiBean();
				imrGuiBean.setTectonicRegions(this.erfGuiBean.getSelectedERF().getIncludedTectonicRegionTypes());
			} catch (RuntimeException e) {
				JOptionPane.showMessageDialog(this,
						"Connection to ERF's failed",
						"Internet Connection Problem", JOptionPane.OK_OPTION);
				e.printStackTrace();
				startAppProgressClass.dispose();
				System.exit(0);
			}

			jbInit();

			computeButton.requestFocusInWindow();

		} catch (Exception e) {
			e.printStackTrace();
			BugReport bug = new BugReport(e, errorInInitializationMessage, appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, true);
			bugDialog.setVisible(true);
		}
		startAppProgressClass.dispose();

		// TODO delete not sure why this is called; maybe other platforms need it
		//((JPanel) getContentPane()).updateUI();
	}
	
	protected String getGuideURL() {
		return null;
	}
	
	protected String getTutorialURL() {
		return "http://www.opensha.org/tutorial-HazardCurveCalculator";
	}
	
	private JMenu buildHelpMenu() {
		HelpMenuBuilder builder = new HelpMenuBuilder(APP_NAME, appShortName, getAppVersion(), this);
		builder.setTutorialURL(getTutorialURL());
		builder.setGuideURL(getGuideURL());
		
		return builder.buildMenu();
	}

	// Component initialization TODO should be private
	protected void jbInit() throws Exception {

		// ======== init menu bar ========
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		saveMenuItem = new JMenuItem("Save");
		saveMenuItem.addActionListener(this);
		fileMenu.add(saveMenuItem);
		printMenuItem = new JMenuItem("Print");
		printMenuItem.addActionListener(this);
		fileMenu.add(printMenuItem);
		closeMenuItem = new JMenuItem("Exit");
		closeMenuItem.addActionListener(this);
		fileMenu.add(closeMenuItem);
		menuBar.add(fileMenu);
		menuBar.add(buildHelpMenu());
		
		enableMenuButtons();


		// ======== init toolbar ======== TODO delayed clean
		//		JToolBar toolbar = new JToolBar();
		//		toolbar.setFloatable(false);
		//		closeButton = new JButton(new ImageIcon(
		//				ImageUtils.loadImage("icons/closeFile.png")));
		//		closeButton.setToolTipText("Exit Application");
		//		closeButton.addActionListener(this);
		//		toolbar.add(closeButton);
		//		printButton = new JButton(new ImageIcon(
		//				ImageUtils.loadImage("icons/printFile.jpg")));
		//		printButton.setToolTipText("Print Graph");
		//		printButton.addActionListener(this);
		//		toolbar.add(printButton);
		//		saveButton = new JButton(new ImageIcon(
		//				ImageUtils.loadImage("icons/saveFile.jpg")));
		//		saveButton.setToolTipText("Save Graph as image");
		//		saveButton.addActionListener(this);
		//		toolbar.add(saveButton);

		Color bg = new Color(220,220,220);

		// ======== button panel ========
		JPanel buttonPanel = new JPanel(new GridBagLayout()) {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
			public void paintComponent(Graphics g) {
				super.paintComponent(g);
				g.setColor(Color.gray);
				g.drawLine(0, 0, getWidth(), 0);
			}
		};
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 40));
		buttonPanel.setBackground(bg);
		JLabel shaLogo = new JLabel(new ImageIcon(
				FileUtils.loadImage("logos/opensha_64.png")));

		JLabel calcTypeLabel = new JLabel("Calculation type:");
		JLabel cpLabel = new JLabel("Control panel:");

		controlComboBox = new JComboBox();
		initControlList();
		controlComboBox.addActionListener(this);
		controlComboBox.setMaximumRowCount(32);
		Dimension cbSize = new Dimension(200,26);
		controlComboBox.setPreferredSize(cbSize);

		progressCheckBox = new JCheckBox("Show Progress Bar");
		progressCheckBox.setSelected(true);

		probDeterComboBox = new JComboBox();
		initProbOrDeterList();
		probDeterComboBox.addActionListener(this);
		probDeterComboBox.setPreferredSize(cbSize);

		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(this);
		cancelButton.setEnabled(false);

		computeButton = new JButton("Compute");
		computeButton.addActionListener(this);
		computeButton.setDefaultCapable(true);

		//buttonPanel.setMinimumSize(new Dimension(600, 100));
		//buttonPanel.setPreferredSize(new Dimension(600, 100));
		//buttonPanel.setLayout(flowLayout1);
		GridBagConstraints gbc = new GridBagConstraints(
				0, 0, 1, 1, 1.0, 1.0, 
				GridBagConstraints.LINE_START, 
				GridBagConstraints.NONE, 
				new Insets(0,0,0,0),0,0);

		// ---- row 1 ----
		gbc.gridheight = 2;
		gbc.weightx = 0.0;
		buttonPanel.add(shaLogo, gbc);

		gbc.gridx += 1;
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.weightx = 1.0;
		buttonPanel.add(progressCheckBox, gbc);

		gbc.gridx += 1;
		gbc.gridheight = 1;
		gbc.anchor = GridBagConstraints.LINE_END;
		gbc.weightx = 0.0;
		buttonPanel.add(calcTypeLabel, gbc);

		gbc.gridx += 1;
		gbc.anchor = GridBagConstraints.LINE_START;
		buttonPanel.add(probDeterComboBox, gbc);

		gbc.gridx += 1;
		gbc.weightx = 1.0;
		gbc.gridheight = 2;
		gbc.anchor = GridBagConstraints.LINE_END;
		buttonPanel.add(cancelButton, gbc);

		gbc.gridx += 1;
		gbc.weightx = 0.0;
		buttonPanel.add(computeButton, gbc);

		// ---- row 2 ----
		gbc.gridx = 2;
		gbc.gridy = 1;
		gbc.gridheight = 1;
		gbc.anchor = GridBagConstraints.LINE_END;
		buttonPanel.add(cpLabel, gbc);

		gbc.gridx += 1;
		gbc.anchor = GridBagConstraints.LINE_START;
		buttonPanel.add(controlComboBox, gbc);






		clearButton = new JButton("Clear Plot");
		clearButton.addActionListener(this);
		clearButton.setEnabled(false);
		clearButton.putClientProperty("JButton.buttonType", "segmentedTextured");
		clearButton.putClientProperty("JButton.segmentPosition", "first");
		clearButton.putClientProperty("JComponent.sizeVariant","small");

		peelButton = new JButton("Peel Off");
		peelButton.addActionListener(this);
		peelButton.setEnabled(false);
		peelButton.putClientProperty("JButton.buttonType", "segmentedTextured");
		peelButton.putClientProperty("JButton.segmentPosition", "last");
		peelButton.putClientProperty("JComponent.sizeVariant","small");

		//		buttonPanel.add(probDeterComboBox, 0);
		//		buttonPanel.add(controlComboBox, 1);
		//		buttonPanel.add(computeButton, 2);
		//		buttonPanel.add(cancelButton, 3);
		//		//buttonPanel.add(clearButton, 4);
		//		//buttonPanel.add(peelButton, 5);
		//		buttonPanel.add(progressCheckBox, 4);
		//		//buttonPanel.add(buttonControlPanel, 7);
		//		buttonPanel.add(imgLabel, 5);

		// ======== param panels ========
		//plotPanel = new JPanel(new GridBagLayout());
		plotPanel = new JPanel(new BorderLayout());
		plotPanel.setBorder(BorderFactory.createEmptyBorder(11, 10, 11, 4));

		// creating the GraphWidget
		buildGraphWidget();

		// IMR, IMT & Site panel
		//imrPanel = new JPanel(new GridBagLayout());

		//imtPanel = new JPanel(new GridBagLayout());

		imrImtSplitPane = new JSplitPane(
				JSplitPane.VERTICAL_SPLIT, true, 
				imtGuiBean, imrGuiBean);
		//		imrImtSplitPane.setResizeWeight(0.6);
		imrImtSplitPane.setResizeWeight(0.18);
		imrImtSplitPane.setBorder(null);
		imrImtSplitPane.setOpaque(false);
		imrImtSplitPane.setMinimumSize(new Dimension(200,100));
		imrImtSplitPane.setPreferredSize(new Dimension(280,100));

		//sitePanel = new JPanel(new GridBagLayout());
		//sitePanel.setBorder(BorderFactory.createEmptyBorder()); TODO clean
		//sitePanel.setBackground(Color.white);

		JSplitPane imrImtSiteSplitPane = new JSplitPane(
				JSplitPane.HORIZONTAL_SPLIT, true, 
				imrImtSplitPane, siteGuiBean);
		imrImtSiteSplitPane.setResizeWeight(0.7);
		imrImtSiteSplitPane.setBorder(
				BorderFactory.createEmptyBorder(2,8,8,8));
		imrImtSiteSplitPane.setOpaque(false);
		//imrImtSiteSplitPane.setDividerLocation(0.5); //TODO revisit
		//imrImtSiteSplitPane.setBorder(null);

		// ERF panel
		//erfPanel = new JPanel(new GridBagLayout());

		// tabbed
		paramsTabbedPane = new JTabbedPane();
		paramsTabbedPane.setBorder(BorderFactory.createEmptyBorder(8,0,0,4));
		paramsTabbedPane.add(imrImtSiteSplitPane, "IMR, IMT & Site");
		erfGuiBean.setBorder(BorderFactory.createEmptyBorder(2,8,8,4));
		paramsTabbedPane.add(erfGuiBean, "ERF & Time Span");

		paramsTabbedPane.setMinimumSize(new Dimension(320,100));
		paramsTabbedPane.setPreferredSize(new Dimension(480,100));


		// ======== content area ========
		JSplitPane contentSplitPane = new JSplitPane(
				JSplitPane.HORIZONTAL_SPLIT, true, 
				plotPanel, paramsTabbedPane);
		contentSplitPane.setResizeWeight(1.0);
		//contentSplitPane.setDividerLocation(0.5); //TODO revisit
		contentSplitPane.setBorder(null);
		//contentSplitPane.setDividerLocation(550);

		Container content = getContentPane();
		content.setLayout(new BorderLayout());
		//content.add(toolbar, BorderLayout.NORTH); TODO clean delay
		content.add(contentSplitPane, BorderLayout.CENTER);
		content.add(buttonPanel, BorderLayout.SOUTH);



		// erfPanel.setLayout(new GridBagLayout());
		//		erfPanel.validate();
		//		erfPanel.repaint();
		//		contentSplitPane.setDividerLocation(590);

		//		JPanel contentPanel = new JPanel(new GridBagLayout());
		//		contentPanel.add(contentSplitPane, new GridBagConstraints(
		//				0, 0, 1, 1,
		//				1.0, 1.0, 
		//				GridBagConstraints.CENTER, 
		//				GridBagConstraints.BOTH,
		//				new Insets(11, 4, 5, 6), 
		//				243, 231));

		// assemble frame

		// frame setup
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setTitle("Hazard Curve Application (" + getAppVersion() + " )");
		setSize(1000, 720);
		contentSplitPane.setDividerLocation(500);
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		int xPos = (dim.width - getWidth()) / 2;
		setLocation(xPos, 0);
		setJMenuBar(menuBar);
		getRootPane().setDefaultButton(computeButton);

		//post build param setting -- WTF doen't this work; site gets updated but not the IMR bean
		//		imrGuiBean.getParameterList().getParameter(
		//				IMR_GuiBean.IMR_PARAM_NAME).setValue(
		//						CB_2008_AttenRel.NAME); //TODO revisit
		//		/// Soooo KLUDGY ... just forcing the editor to change seems to update site as well. Wrong
		imrGuiBean.setSelectedSingleIMR(CB_2008_AttenRel.NAME);
		//		((JComboBox) imrGuiBean.getParameterList().getParameter(
		//				IMR_GuiBean.IMR_PARAM_NAME).getEditor().
		//				getValueEditor()).setSelectedIndex(9);

	}

	/* implementation */ 
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		//		if (src.equals(closeMenuItem) || src.equals(closeButton)) { TODO clean if toolbar killed
		//			close();
		//		} else if (src.equals(saveMenuItem) || src.equals(saveButton)) {
		//			save();
		//		} else if (src.equals(printMenuItem) || src.equals(printButton)) {
		//			print();
		//		} else if (src.equals(clearButton)) {
		if (src.equals(closeMenuItem)) {
			close();
		} else if (src.equals(saveMenuItem)) {
			save();
		} else if (src.equals(printMenuItem)) {
			print();
		} else if (src.equals(clearButton)) {
			clearPlot();
		} else if (src.equals(computeButton)) {
			addButton_actionPerformed();
		} else if (src.equals(controlComboBox)) {
			selectControlPanel();
		} else if (src.equals(probDeterComboBox)) {
			probDeterSelectionChange();
		} else if (src.equals(peelButton)) {
			peelOffCurves();
		} else if (src.equals(cancelButton)) {
			cancelCalculation();
		}
	}

	/* implementation KLUDGY to set focus on compute button*/
//	public void setVisible(boolean visible) {
//		super.setVisible(visible);
//		computeButton.requestFocusInWindow();
//	}

	/**
	 * Provided to allow subclasses to substitute the IMT panel.
	 */
	protected void setImtPanel(ParameterListEditor panel, double resizeWeight) {
		imrImtSplitPane.setTopComponent(panel);
		imrImtSplitPane.setResizeWeight(resizeWeight);
	}

	// Get Applet information
	public String getAppletInfo() {
		return "Hazard Curves Applet";
	}

	/**
	 * Returns the Application version
	 * @return ApplicationVersion
	 */
	public static ApplicationVersion getAppVersion(){
		if (version == null) {
			try {
				version = ApplicationVersion.loadBuildVersion();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return version;
	}

// NOTE Old from server mode
//	// Main method
//	public static void main(String[] args) throws IOException {
//		new DisclaimerDialog(APP_NAME, appShortName, getAppVersion());
//		HazardCurveServerModeApplication applet = new HazardCurveServerModeApplication();
//		Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptoinHandler(
//				appShortName, getAppVersion(), applet, applet));
//		applet.init();
//		applet.setIconImages(IconFetcher.fetchIcons(APP_SHORT_NAME));
//		//		applet.pack();
//		applet.setVisible(true);
//	}

// NOTE New from local
	public static void main(String[] args) throws IOException {
		new DisclaimerDialog(APP_NAME, APP_SHORT_NAME, getAppVersion());
		DefaultExceptoinHandler exp = new DefaultExceptoinHandler(
				APP_SHORT_NAME, getAppVersion(), null, null);
		Thread.setDefaultUncaughtExceptionHandler(exp);
		GCIM_HazardCurveApp applet = new GCIM_HazardCurveApp(APP_SHORT_NAME);
		exp.setApp(applet);
		exp.setParent(applet);
		applet.init();
		applet.setTitle("GCIM EHazard Curve Application "+"("+getAppVersion()+")" );
		applet.setIconImages(IconFetcher.fetchIcons(APP_SHORT_NAME));
		applet.setVisible(true);
		applet.computeButton.requestFocusInWindow();
		applet.createCalcInstance();
	}

	// static initializer for setting look & feel
	static {
		try {
			System.setProperty("apple.laf.useScreenMenuBar", "true");
		} catch (AccessControlException e1) {
			System.err.println("WARNING: could not set property 'apple.laf.useScreenMenuBar'");
		}
//		String osName = System.getProperty("os.name");
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
		}
	}

	/**
	 * Adds a feature to the GraphPanel attribute of the EqkForecastApplet
	 * object
	 */
	private void addGraphPanel() {

		// Starting
//		String S = C + ": addGraphPanel(): ";
		PlotSpec spec = graphWidget.getPlotSpec();
		spec.setPlotElems(functionList);
		graphWidget.drawGraph();
		// this.isIndividualCurves = false;
	}
	

	/**
	 * this function is called when Add Graph button is clicked
	 * 
	 * @param e
	 */
	void addButton_actionPerformed() {
		if (this.runAllPeerTestsCP != null) {
			if (this.runAllPeerTestsCP.runAllPEER_TestCases()) {
				try {
					progressCheckBox.setSelected(false);
					String peerDirName = "PEER_TESTS/";
					// creating the peer directory in which we put all the peer
					// related files
					File peerDir = new File(peerDirName);
					if (!peerDir.isDirectory()) { // if main directory does not
						// exist
						(new File(peerDirName)).mkdir();
					}

					// ArrayList testCases =
					// this.peerTestsControlPanel.getPEER_SetTwoTestCasesNames();
					ArrayList<String> testCases = this.peerTestsControlPanel
					.getPEER_SetOneTestCasesNames();

					int size = testCases.size();
					/*
					 * if(epistemicControlPanel == null) epistemicControlPanel =
					 * new ERF_EpistemicListControlPanel(this,this);
					 * epistemicControlPanel.setCustomFractileValue(05);
					 * epistemicControlPanel.setVisible(false);
					 */
					// System.out.println("size="+testCases.size());
					setAverageSelected(true);
					/*
					 * size=106 for Set 1 Case1: 0-6 Case2: 7-13 Case3: 14-20
					 * Case4: 21-27 Case5 28-34 Case6: 35-41 Case7: 42-48
					 * Case8a: 49-55 Case8b: 56-62 Case8c: 63-69 Case9a: 70-76
					 * Case9b: 77-83 Case9c: 84-90 Case10: 91-95 Case11: 96-99
					 * Case12: 100-106
					 * 
					 * DOING ALL TAKES ~24 HOURS?
					 */
					for (int i = 0; i < size; ++i) {
						// for(int i=35 ;i < 35; ++i){
						System.out.println("Working on # " + (i + 1) + " of "
								+ size);

						// first do PGA
						peerTestsControlPanel
						.setTestCaseAndSite((String) testCases.get(i));
						calculate();

						FileWriter peerFile = new FileWriter(peerDirName
								+ (String) testCases.get(i)
								+ "-PGA_OpenSHA.txt");
						DiscretizedFunc func = (DiscretizedFunc) functionList
						.get(0);
						for (int j = 0; j < func.size(); ++j)
							peerFile.write(func.get(j).getX() + "\t"
									+ func.get(j).getY() + "\n");
						peerFile.close();
						clearPlot();

						// now do SA
						/*
						 * imtGuiBean.getParameterList().getParameter(IMT_GuiBean
						 * .IMT_PARAM_NAME).setValue(SA_Param.NAME);
						 * imtGuiBean.getParameterList
						 * ().getParameter(PeriodParam.NAME).setValue(new
						 * Double(1.0)); addButton(); peerFile = new
						 * FileWriter(peerDirName
						 * +(String)testCasesTwo.get(i)+"-1secSA_OpenSHA.dat");
						 * for(int j=0; j<totalProbFuncs.get(0).getNum();++j)
						 * peerFile
						 * .write(totalProbFuncs.get(0).get(j).getX()+" "
						 * +totalProbFuncs.get(0).get(j).getY()+"\n");
						 * peerFile.close(); this.clearPlot(true);
						 */

					}
					System.exit(101);
					// peerResultsFile.close();
				} catch (Exception ee) {
					BugReport bug = new BugReport(ee, getParametersInfoAsString(), appShortName, getAppVersion(), this);
					BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
					bugDialog.setVisible(true);
				}
			}
		} else {
			cancelButton.setEnabled(true);
			calculate();
		}
	}

	/**
	 * Implementing the run method in the Runnable interface that creates a new
	 * thread to do Hazard Curve Calculation, this thread created is seperate
	 * from the timer thread, so that progress bar updation does not conflicts
	 * with Calculations.
	 */
	public void run() {
		try {
			computeHazardCurve();
			cancelButton.setEnabled(false);
			// disaggCalc = null;
			calcThread = null;
		} catch (ThreadDeath t) {
			// expected if you cancelled it
//			System.out.println("Caught ThreadDeath");
			setButtonsEnable(true);
		} catch (Throwable t) {
			t.printStackTrace();
			BugReport bug = new BugReport(t, getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
			bugDialog.setVisible(true);
			setButtonsEnable(true);
		}

	}

	/**
	 * This method creates the HazardCurveCalc and Disaggregation Calc(if selected) instances.
	 * Calculations are performed on the user's own machine, no internet connection
	 * is required for it.
	 */
	protected void createCalcInstance(){
		try{
			if(calc == null) {
				calc = new HazardCurveCalculator();
				if(this.calcParamsControl != null)
					calc.setAdjustableParams(calcParamsControl.getAdjustableCalcParams());
//System.out.println("Created new calc from LocalModeApp");
			}
			if(disaggregationFlag)
				if(disaggCalc == null)
					disaggCalc = new DisaggregationCalculator();
		}catch(Exception e){
			e.printStackTrace();
			BugReport bug = new BugReport(e, this.getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, true);
			bugDialog.setVisible(true);
		}
		
	}

	/**
	 * this function is called to draw the graph
	 */
	protected void calculate() {
		setButtonsEnable(false);
		// do not show warning messages in IMR gui bean. this is needed
		// so that warning messages for site parameters are not shown when Add
		// graph is clicked
		//		imrGuiBean.showWarningMessages(false); // TODO should we add this to the multi imr bean?
		if (plotOptionControl != null) {
			if (this.plotOptionControl.getSelectedOption().equals(
					PlottingOptionControl.PLOT_ON_TOP))
				addData = true;
			else
				addData = false;
		}
		try {
			createCalcInstance();
		} catch (Exception e) {
			setButtonsEnable(true);
			e.printStackTrace();
			BugReport bug = new BugReport(e, getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
			bugDialog.setVisible(true);
		}

		// check if progress bar is desired and set it up if so
		if (this.progressCheckBox.isSelected()) {
			calcThread = new Thread(this);
			calcThread.start();
			timer = new Timer(200, new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					try {
						if (!isEqkList) {
							int totRupture = calc.getTotRuptures();
							int currRupture = calc.getCurrRuptures();
							boolean totCurCalculated = true;
							if (currRupture == -1) {
								progressClass
								.setProgressMessage("Please wait, calculating total rutures ....");
								totCurCalculated = false;
							}
							if (!isHazardCalcDone && totCurCalculated)
								progressClass.updateProgress(currRupture,
										totRupture);
						} else {
							if ((numERFsInEpistemicList) != 0)
								progressClass
								.updateProgress(
										currentERFInEpistemicListForHazardCurve,
										numERFsInEpistemicList);
						}
						if (isHazardCalcDone) {
							timer.stop();
							progressClass.dispose();
							drawGraph();
						}
					} catch (Exception e) {
						// e.printStackTrace();
						timer.stop();
						setButtonsEnable(true);
						e.printStackTrace();
						BugReport bug = new BugReport(e, getParametersInfoAsString(), APP_NAME,
								getAppVersion(), getApplicationComponent());
						BugReportDialog bugDialog = new BugReportDialog(getApplicationComponent(), bug, false);
						bugDialog.setVisible(true);
					}
				}
			});

			// timer for disaggregation progress bar
			disaggTimer = new Timer(200, new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					try {
						int totalRupture = disaggCalc.getTotRuptures();
						int currRupture = disaggCalc.getCurrRuptures();
						boolean calcDone = disaggCalc.done();
						if (!calcDone)
							disaggProgressClass.updateProgress(currRupture,
									totalRupture);
						if (calcDone) {
							disaggTimer.stop();
							disaggProgressClass.dispose();
						}
					} catch (Exception e) {
						disaggTimer.stop();
						setButtonsEnable(true);
						e.printStackTrace();
						BugReport bug = new BugReport(e, getParametersInfoAsString(), APP_NAME,
								getAppVersion(), getApplicationComponent());
						BugReportDialog bugDialog = new BugReportDialog(getApplicationComponent(), bug, false);
						bugDialog.setVisible(true);
					}
				}
			});
			// timer for GCIM progress bar
			gcimTimer = new Timer(200, new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					try {
						int totalIMi = gcimCalc.getTotIMi(); 
						int currIMi = gcimCalc.getCurrIMi(); 
						boolean calcDone = gcimCalc.done();
						if (!calcDone)
							gcimProgressClass.updateProgress(currIMi,  
									totalIMi);
						if (calcDone) {
							gcimTimer.stop();
							gcimProgressClass.dispose();
						}
					} catch (Exception e) {
						gcimTimer.stop();
						setButtonsEnable(true);
						e.printStackTrace();
						BugReport bug = new BugReport(e, getParametersInfoAsString(), APP_NAME,
								getAppVersion(), getApplicationComponent());
						BugReportDialog bugDialog = new BugReportDialog(getApplicationComponent(), bug, false);
						bugDialog.setVisible(true);
					}
				}
			});

		} else {
			computeHazardCurve();
			drawGraph();
		}
	}

	/**
	 * 
	 * @return the application component
	 */
	protected Component getApplicationComponent() {
		return this;
	}

	/**
	 * to draw the graph
	 */
	protected void drawGraph() {
		// you can show warning messages now
		//		imrGuiBean.showWarningMessages(true); // TODO should we add this to the multi imr bean?
		addGraphPanel();
		if (!disaggregationFlag)
			setButtonsEnable(true);
	}

	/**
	 * plots the curves with defined color,line width and shape.
	 * 
	 */
	public void plotGraphUsingPlotPreferences() {
		drawGraph();
	}

	private void clearPlot() {
		graphWidget.setAutoRange();
		graphWidget.removeChartAndMetadata();
		functionList = Lists.newArrayList();
//		clearButton.setEnabled(false);
//		peelButton.setEnabled(false);
		graphWidget.getButtonControlPanel().setEnabled(false);
		enableMenuButtons();
		validate();
		repaint();
	}

	/**
	 * This function to specify whether disaggregation is selected or not
	 * 
	 * @param isSelected
	 *            : True if disaggregation is selected , else false
	 */
	public void setDisaggregationSelected(boolean isSelected) {
		disaggregationFlag = isSelected;
	}
	
	//TODO
	/**
	 * This function to specify whether gcim is selected or not
	 * 
	 * @param isSelected
	 *            : True if gcim is selected , else false
	 */
	public void setGcimSelected(boolean isSelected) {
		gcimFlag = isSelected;
	}

	/*
	 * void imgLabel_mouseClicked(MouseEvent e) { try{
	 * this.getAppletContext().showDocument(new URL(OPENSHA_WEBSITE),
	 * "new_peer_win"); }catch(java.net.MalformedURLException ee){
	 * JOptionPane.showMessageDialog(this,new
	 * String("No Internet Connection Available"),
	 * "Error Connecting to Internet",JOptionPane.OK_OPTION); return; } }
	 */

	/**
	 * Any time a control paramater or independent paramater is changed by the
	 * user in a GUI this function is called, and a paramater change event is
	 * passed in. This function then determines what to do with the information
	 * ie. show some paramaters, set some as invisible, basically control the
	 * paramater lists.
	 * 
	 * @param event
	 */
	public void parameterChange(ParameterChangeEvent event) {

		String S = C + ": parameterChange(): ";
		if (D)
			System.out.println("\n" + S + "starting: ");

		String name1 = event.getParameterName();

		// if IMR selection changed, update the site parameter list and
		// supported IMT
		if (name1.equalsIgnoreCase(IMR_GuiBean.IMR_PARAM_NAME)) {
			updateSiteParams();
		}
		if (name1.equalsIgnoreCase(ERF_GuiBean.ERF_PARAM_NAME)) {

			String plottingOption = null;
			if (plotOptionControl != null)
				plottingOption = this.plotOptionControl.getSelectedOption();
			//			controlComboBox.removeAllItems();
			//			this.initControlList();
			// add the Epistemic control panel option if Epistemic ERF is
			// selected
			if (erfGuiBean.isEpistemicList()) {
				showControlPanel(ERF_EpistemicListControlPanel.NAME);
			} else if (plottingOption != null
					&& plottingOption
					.equalsIgnoreCase(PlottingOptionControl.ADD_TO_EXISTING)) {
				JOptionPane
				.showMessageDialog(
						this,
						"Cannot add to existing without selecting ERF Epistemic list",
						"Input Error", JOptionPane.INFORMATION_MESSAGE);
				plotOptionControl
				.setSelectedOption(PlottingOptionControl.PLOT_ON_TOP);
				setButtonsEnable(true);
			}
			try {
				imrGuiBean.setTectonicRegions(erfGuiBean.getSelectedERF_Instance().getIncludedTectonicRegionTypes());
			} catch (InvocationTargetException e) {
				e.printStackTrace();
				imrGuiBean.setTectonicRegions(null);
			}
		}
		
		//if any change is made re-init the GCIM control panel
		
		
	}

	/**
	 * Function to make the buttons enable or disable in the application. It is
	 * used in application to disable the button in the buttons panel if some
	 * computation is already going on.
	 * 
	 * @param b
	 */
	protected void setButtonsEnable(boolean b) {
		computeButton.setEnabled(b);
//		clearButton.setEnabled(b);
//		peelButton.setEnabled(b);
		graphWidget.getButtonControlPanel().setEnabled(b);
		progressCheckBox.setEnabled(b);
		enableMenuButtons();
	}
	
	private void enableMenuButtons() {
		boolean enableSavePrint = functionList != null && functionList.size() > 0;
		saveMenuItem.setEnabled(enableSavePrint);
		printMenuItem.setEnabled(enableSavePrint);
	}


	/**
	 * Gets the probabilities functiion based on selected parameters this
	 * function is called when add Graph is clicked
	 */
	protected void computeHazardCurve() {

		// starting the calculation
		isHazardCalcDone = false;

		BaseERF forecast = null;

		// get the selected forecast model
		try {
			if (!isDeterministicCurve) {
				// whether to show progress bar in case of update forecast
				erfGuiBean.showProgressBar(this.progressCheckBox.isSelected());
				// get the selected ERF instance
				forecast = erfGuiBean.getSelectedERF();
			}
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, e.getMessage(),
					"Incorrect Values", JOptionPane.ERROR_MESSAGE);
			setButtonsEnable(true);
			return;
		}
		if (this.progressCheckBox.isSelected()) {
			progressClass = new CalcProgressBar("Hazard-Curve Calc Status",
			"Beginning Calculation ");
			progressClass.displayProgressBar();
			timer.start();
		}

		// get the selected IMR
		Map<TectonicRegionType, ScalarIMR> imrMap = imrGuiBean.getIMRMap();
		// this first IMR from the map...note this should ONLY be used for getting settings
		// common to all IMRS (such as units), and not for calculation (except in deterministic
		// calc with no trt's selected)
		ScalarIMR firstIMRFromMap = TRTUtils.getFirstIMR(imrMap);

		// make a site object to pass to IMR
		Site site = siteGuiBean.getSite();

		try {
			// this function will get the selected IMT parameter and set it in
			// IMT
			imtGuiBean.setIMTinIMRs(imrMap);
		} catch (Exception ex) {
			if (D)
				System.out.println(C + ":Param warning caught" + ex);
			ex.printStackTrace();
			BugReport bug = new BugReport(ex, getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
			bugDialog.setVisible(true);
		}
		if (forecast instanceof AbstractEpistemicListERF && !isDeterministicCurve) {
			// if add on top get the name of ERF List forecast
			if (addData)
				prevSelectedERF_List = forecast.getName();

			if (!prevSelectedERF_List.equals(forecast.getName()) && !addData) {
				JOptionPane
				.showMessageDialog(
						this,
						"Cannot add to existing without selecting same ERF Epistemic list",
						"Input Error", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			this.isEqkList = true; // set the flag to indicate thatwe are
			// dealing with Eqk list
			handleForecastList(site, imrMap, forecast);
			// initializing the counters for ERF List to 0, for other ERF List
			// calculations
			currentERFInEpistemicListForHazardCurve = 0;
			numERFsInEpistemicList = 0;
			isHazardCalcDone = true;
			return;
		}

		// making the previuos selected ERF List to be null
		prevSelectedERF_List = null;

		// this is not a eqk list
		this.isEqkList = false;
		// calculate the hazard curve

		// initialize the values in condProbfunc with log values as passed in
		// hazFunction
		// intialize the hazard function
		ArbitrarilyDiscretizedFunc hazFunction = new ArbitrarilyDiscretizedFunc();
		initX_Values(hazFunction);
		// System.out.println("22222222HazFunction: "+hazFunction.toString());
		try {
			// calculate the hazard curve
			// eqkRupForecast =
			// (EqkRupForecastAPI)FileUtils.loadObject("erf.obj");
			try {
				if (isProbabilisticCurve) {
					hazFunction = (ArbitrarilyDiscretizedFunc) calc
					.getHazardCurve(hazFunction, site, imrMap,
							(ERF) forecast);
				} else if (isStochasticCurve) {
					hazFunction = (ArbitrarilyDiscretizedFunc) calc.
					getAverageEventSetHazardCurve(hazFunction, site, imrGuiBean.getSelectedIMR(),
							(ERF) forecast);
				} else { // deterministic
					progressCheckBox.setSelected(false);
					progressCheckBox.setEnabled(false);
					ScalarIMR imr = imrGuiBean.getSelectedIMR();
					EqkRupture rupture = this.erfRupSelectorGuiBean.getRupture();
					hazFunction = (ArbitrarilyDiscretizedFunc) calc
					.getHazardCurve(hazFunction, site, imr, rupture);
					progressCheckBox.setSelected(true);
					progressCheckBox.setEnabled(true);
				}
			} catch (Exception e) {
				e.printStackTrace();
				setButtonsEnable(true);
				BugReport bug = new BugReport(e, getParametersInfoAsString(), appShortName, getAppVersion(), this);
				BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
				bugDialog.setVisible(true);

			}
			hazFunction = toggleHazFuncLogValues(hazFunction);
			hazFunction.setInfo(getParametersInfoAsString());
		} catch (RuntimeException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(),
					"Parameters Invalid", JOptionPane.INFORMATION_MESSAGE);
			// e.printStackTrace();
			setButtonsEnable(true);
			return;
		}

		// add the function to the function list
		functionList.add(hazFunction);
		// set the X-axis label
		String imt = imtGuiBean.getSelectedIMT();
		String xAxisName = imt + " (" + firstIMRFromMap.getParameter(imt).getUnits() + ")";
		String yAxisName = "Probability of Exceedance";
		graphWidget.setXAxisLabel(xAxisName);
		graphWidget.setYAxisLabel(yAxisName);

		isHazardCalcDone = true;
		disaggregationString = null;
		gcimString = null;

		// Disaggregation with stochastic event sets not yet supported
		if (disaggregationFlag && isStochasticCurve) {
			JOptionPane.showMessageDialog(this,
					"Disaggregation not yet supported with stochastic event-set calculations",
					"Input Error", JOptionPane.INFORMATION_MESSAGE);
			setButtonsEnable(true);
			return;
		}

		// checking the disAggregation flag and probability curve is being plotted
		if (disaggregationFlag && isProbabilisticCurve) {
			if (this.progressCheckBox.isSelected()) {
				disaggProgressClass = new CalcProgressBar(
						"Disaggregation Calc Status",
				"Beginning Disaggregation ");
				disaggProgressClass.displayProgressBar();
				disaggTimer.start();
			}
			/*
			 * try{ if(distanceControlPanel!=null)
			 * disaggCalc.setMaxSourceDistance
			 * (distanceControlPanel.getDistance()); }catch(Exception e){
			 * setButtonsEnable(true); ExceptionWindow bugWindow = new
			 * ExceptionWindow(this,e,getParametersInfoAsString());
			 * bugWindow.setVisible(true); bugWindow.pack();
			 * e.printStackTrace(); }
			 */
			int num = hazFunction.size();
			// checks if successfully disaggregated.
			boolean disaggSuccessFlag = false;
			boolean disaggrAtIML = false;
			double disaggregationVal = disaggregationControlPanel
			.getDisaggregationVal();
			String disaggregationParamVal = disaggregationControlPanel
			.getDisaggregationParamValue();
			double minMag = disaggregationControlPanel.getMinMag();
			double deltaMag = disaggregationControlPanel.getdeltaMag();
			int numMag = disaggregationControlPanel.getNumMag();
			double minDist = disaggregationControlPanel.getMinDist();
			double deltaDist = disaggregationControlPanel.getdeltaDist();
			int numDist = disaggregationControlPanel.getNumDist();
			int numSourcesForDisag = disaggregationControlPanel
			.getNumSourcesForDisagg();
			boolean showSourceDistances = disaggregationControlPanel.isShowSourceDistances();
			double maxZAxis = disaggregationControlPanel.getZAxisMax();
			double imlVal = 0, probVal = 0;
			try {
				if (disaggregationControlPanel.isCustomDistBinning()) {
					double distBins[] = disaggregationControlPanel
					.getCustomBinEdges();
					disaggCalc.setDistanceRange(distBins);
				} else {
					disaggCalc.setDistanceRange(minDist, numDist, deltaDist);
				}
				disaggCalc.setMagRange(minMag, numMag, deltaMag);
				disaggCalc.setNumSourcestoShow(numSourcesForDisag);
				disaggCalc.setShowDistances(showSourceDistances);

			} catch (Exception e) {
				setButtonsEnable(true);
				e.printStackTrace();
				BugReport bug = new BugReport(e, getParametersInfoAsString(), appShortName, getAppVersion(), this);
				BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
				bugDialog.setVisible(true);
			}
			try {

				if (disaggregationParamVal
						.equals(DisaggregationControlPanel.DISAGGREGATE_USING_PROB)) {
					disaggrAtIML = false;
					// if selected Prob is not within the range of the Exceed.
					// prob of Hazard Curve function
					if (disaggregationVal > hazFunction.getY(0)
							|| disaggregationVal < hazFunction.getY(num - 1))
						JOptionPane
						.showMessageDialog(
								this,
								new String(
										"Chosen Probability is not"
										+ " within the range of the min and max prob."
										+ " in the Hazard Curve"),
										"Disaggregation error message",
										JOptionPane.ERROR_MESSAGE);
					else {
						// gets the Disaggregation data
						imlVal = hazFunction
						.getFirstInterpolatedX_inLogXLogYDomain(disaggregationVal);
						probVal = disaggregationVal;
					}
				} else if (disaggregationParamVal
						.equals(DisaggregationControlPanel.DISAGGREGATE_USING_IML)) {
					disaggrAtIML = true;
					// if selected IML is not within the range of the IML values
					// chosen for Hazard Curve function
					if (disaggregationVal < hazFunction.getX(0)
							|| disaggregationVal > hazFunction.getX(num - 1))
						JOptionPane
						.showMessageDialog(
								this,
								new String(
										"Chosen IML is not"
										+ " within the range of the min and max IML values"
										+ " in the Hazard Curve"),
										"Disaggregation error message",
										JOptionPane.ERROR_MESSAGE);
					else {
						imlVal = disaggregationVal;
						probVal = hazFunction
						.getInterpolatedY_inLogXLogYDomain(disaggregationVal);
					}
				}
//				disaggSuccessFlag = disaggCalc.disaggregate(Math.log(imlVal),
//						site, imrMap, (EqkRupForecast) forecast,
//						this.calc.getAdjustableParams());
				disaggSuccessFlag = disaggCalc.disaggregate(Math.log(imlVal),
					site, imrMap, (AbstractERF) forecast,
					this.calc.getMaxSourceDistance(), calc.getMagDistCutoffFunc());
				
				disaggCalc.setMaxZAxisForPlot(maxZAxis);
				disaggregationString = disaggCalc.getMeanAndModeInfo();


			} catch (WarningException warningException) {
				setButtonsEnable(true);
				JOptionPane.showMessageDialog(this, warningException
						.getMessage());
			} catch (Exception e) {
				setButtonsEnable(true);
				e.printStackTrace();
				BugReport bug = new BugReport(e, getParametersInfoAsString(), appShortName, getAppVersion(), this);
				BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
				bugDialog.setVisible(true);
			}
			// }
			if (disaggSuccessFlag)
				showDisaggregationResults(numSourcesForDisag, disaggrAtIML,
						imlVal, probVal);
			else
				JOptionPane
				.showMessageDialog(
						this,
						"Disaggregation failed because there is "
						+ "no exceedance above \n "
						+ "the given IML (or that interpolated from the chosen probability).",
						"Disaggregation Message", JOptionPane.OK_OPTION);
		}
		setButtonsEnable(true);
		// displays the disaggregation string in the pop-up window

		disaggregationString = null;
		
		// checking the gcim flag and probability curve is being plotted
		if (gcimFlag && isProbabilisticCurve) {

			gcimCalc = new GcimCalculator();
			if (this.progressCheckBox.isSelected()) {
				gcimProgressClass = new CalcProgressBar(
						"GCIM Calc Status",
				"Beginning GCIM calculations ");
				gcimProgressClass.displayProgressBar();
				gcimTimer.start();
			}
			
			int num = hazFunction.size();
			// checks if successfully disaggregated.
			boolean gcimSuccessFlag = false;
			boolean gcimRealizationSuccessFlag = false;
			boolean gcimAtIML = false;
			Site gcimSite = gcimControlPanel.getGcimSite();
			double gcimVal = gcimControlPanel
			.getGcimVal();
			String gcimParamVal = gcimControlPanel
			.getGcimParamValue();
			int gcimNumIMi = gcimControlPanel.getNumIMi();
			double minApproxZVal = gcimControlPanel.getMinApproxZ();
			double maxApproxZVal = gcimControlPanel.getMaxApproxZ();
			double deltaApproxZVal = gcimControlPanel.getDeltaApproxZ();
			int numGcimRealizations = gcimControlPanel.getNumGcimRealizations();
			ArrayList<String> imiTypes = gcimControlPanel.getImiTypes(); 
			ArrayList<? extends Map<TectonicRegionType, ScalarIMR>> imiMapAttenRels = 
					gcimControlPanel.getImris();
			
			ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> imijCorrRels = 
					gcimControlPanel.getImCorrRels();
			ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> imikCorrRels = 
				gcimControlPanel.getImikCorrRels();
			
			gcimCalc.setApproxCDFvalues(minApproxZVal, maxApproxZVal, deltaApproxZVal);
			
			double imlVal = 0, probVal = 0;
			try {

				if (gcimParamVal
						.equals(GcimControlPanel.GCIM_USING_PROB)) {
					gcimAtIML = false;
					// if selected Prob is not within the range of the Exceed.
					// prob of Hazard Curve function
					if (gcimVal > hazFunction.getY(0)
							|| gcimVal < hazFunction.getY(num - 1))
						JOptionPane
						.showMessageDialog(
								this,
								new String(
										"Chosen Probability is not"
										+ " within the range of the min and max prob."
										+ " in the Hazard Curve"),
										"GCIM error message",
										JOptionPane.ERROR_MESSAGE);
					else {
						// gets the GCIM data
						imlVal = hazFunction
						.getFirstInterpolatedX_inLogXLogYDomain(gcimVal);
						probVal = gcimVal;
					}
				} else if (gcimParamVal
						.equals(GcimControlPanel.GCIM_USING_IML)) {
					gcimAtIML = true;
					// if selected IML is not within the range of the IML values
					// chosen for Hazard Curve function
					if (gcimVal < hazFunction.getX(0)
							|| gcimVal > hazFunction.getX(num - 1))
						JOptionPane
						.showMessageDialog(
								this,
								new String(
										"Chosen IML is not"
										+ " within the range of the min and max IML values"
										+ " in the Hazard Curve"),
										"GCIM error message",
										JOptionPane.ERROR_MESSAGE);
					else {
						imlVal = gcimVal;
						probVal = hazFunction
						.getInterpolatedY_inLogXLogYDomain(gcimVal);
					}
				}
				//TODO fix API problem
				gcimCalc.getRuptureContributions(Math.log(imlVal), gcimSite, imrMap,
							 	(AbstractERF) forecast, this.calc.getMaxSourceDistance(),
							 	calc.getMagDistCutoffFunc());
				
				gcimSuccessFlag = gcimCalc.getMultipleGcims(gcimNumIMi, imiMapAttenRels, imiTypes,
										imijCorrRels, this.calc.getMaxSourceDistance(),
										calc.getMagDistCutoffFunc());
				
				if (numGcimRealizations>0) {
					gcimRealizationSuccessFlag = gcimCalc.getGcimRealizations(numGcimRealizations, gcimNumIMi, imiMapAttenRels, imiTypes,
										imijCorrRels, imikCorrRels, this.calc.getMaxSourceDistance(),
										calc.getMagDistCutoffFunc());
				} else {
					gcimRealizationSuccessFlag = true;
				}
				

			} catch (WarningException warningException) {
				setButtonsEnable(true);
				JOptionPane.showMessageDialog(this, warningException
						.getMessage());
			} catch (Exception e) {
				e.printStackTrace();
				setButtonsEnable(true);
				BugReport bug = new BugReport(e, getParametersInfoAsString(), appShortName, getAppVersion(), this);
				BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
				bugDialog.setVisible(true);
			}
			// }
			if (gcimSuccessFlag&gcimRealizationSuccessFlag) {
				String imjName;
				if (firstIMRFromMap.getIntensityMeasure().getName()==SA_Param.NAME) {
					imjName= "SA (" + ((SA_Param) firstIMRFromMap.getIntensityMeasure()).getPeriodParam().getValue() + "s)";
				}
				else if (firstIMRFromMap.getIntensityMeasure().getName()==SA_InterpolatedParam.NAME) {
					imjName= "SA (" + ((SA_InterpolatedParam) firstIMRFromMap.getIntensityMeasure()).getPeriodInterpolatedParam().getValue() + "s)";
				}
				else {
					imjName = firstIMRFromMap.getIntensityMeasure().getName();
				}
				showGcimResults(imjName,gcimAtIML, imlVal, probVal);
			}
			else
				JOptionPane
				.showMessageDialog(
						this,
						"GCIM calculations failed because there is "
						+ "no exceedance above \n "
						+ "the given IML (or that interpolated from the chosen probability).",
						"GCIM Message", JOptionPane.OK_OPTION);
		}
		setButtonsEnable(true);

		gcimString = null;
	}
	
	

	/**
	 * 
	 * This function allows showing the disaggregation result in the HMTL to be
	 * shown in the dissaggregation plot window.
	 * 
	 * @param numSourceToShow
	 *            int : Number of sources to show for the disaggregation
	 * @param imlBasedDisaggr
	 *            boolean Disaggregation is done based on chosen IML
	 * @param imlVal
	 *            double iml value for the disaggregation
	 * @param probVal
	 *            double if disaggregation is done based on prob. then its value
	 */
	private void showDisaggregationResults(int numSourceToShow,
			boolean imlBasedDisaggr, double imlVal, double probVal) {
		// String sourceDisaggregationListAsHTML = null;
		String sourceDisaggregationList = null;
		if (numSourceToShow > 0) {
			sourceDisaggregationList = getSourceDisaggregationInfo();
			// sourceDisaggregationListAsHTML = sourceDisaggregationList.
			// replaceAll("\n", "<br>");
			// sourceDisaggregationListAsHTML = sourceDisaggregationListAsHTML.
			// replaceAll("\t", "&nbsp;&nbsp;&nbsp;");
		}
		String binData = null;
		boolean binDataToShow = disaggregationControlPanel
		.isShowDisaggrBinDataSelected();
		if (binDataToShow) {
			try {
				binData = disaggCalc.getBinData();
				// binDataAsHTML = binDataAsHTML.replaceAll("\n", "<br>");
				// binDataAsHTML = binDataAsHTML.replaceAll("\t",
				// "&nbsp;&nbsp;&nbsp;");
			} catch (RuntimeException ex) {
				setButtonsEnable(true);
				ex.printStackTrace();
				BugReport bug = new BugReport(ex, getParametersInfoAsString(), appShortName, getAppVersion(), this);
				BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
				bugDialog.setVisible(true);
			}
		}
		String modeString = "";
		if (imlBasedDisaggr)
			modeString = "Disaggregation Results for IML = " + imlVal
			+ " (for Prob = " + (float) probVal + ")";
		else
			modeString = "Disaggregation Results for Prob = " + probVal
			+ " (for IML = " + (float) imlVal + ")";
		modeString += "\n" + disaggregationString;

		String disaggregationPlotWebAddr = null;
		String metadata;
		// String pdfImageLink;
		try {
			disaggregationPlotWebAddr = getDisaggregationPlot();
			/*
			 * pdfImageLink = "<br>Click  " + "<a href=\"" +
			 * disaggregationPlotWebAddr +
			 * DisaggregationCalculator.DISAGGREGATION_PLOT_PDF_NAME + "\">" +
			 * "here" + "</a>" +
			 * " to view a PDF (non-pixelated) version of the image (this will be deleted at midnight)."
			 * ;
			 */

			metadata = getMapParametersInfoAsHTML();
			metadata += "<br><br>Click  " + "<a href=\""
			+ disaggregationPlotWebAddr + "\">" + "here" + "</a>"
			+ " to download files. They will be deleted at midnight";
		} catch (RuntimeException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, e.getMessage(),
					"Server Problem", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// adding the image to the Panel and returning that to the applet
		// new DisaggregationPlotViewerWindow(imgName,true,modeString,
		// metadata,binData,sourceDisaggregationList);
		new DisaggregationPlotViewerWindow(disaggregationPlotWebAddr
				+ DisaggregationCalculator.DISAGGREGATION_PLOT_PDF_NAME, true,
				modeString, metadata, binData, sourceDisaggregationList);
	}
	
	/**
	 * 
	 * This function allows showing the GCIM results
	 * @param imjName 
	 * 			  The name of the IMT for which the GCIM results are conditioned on
	 * @param imlBasedDisaggr
	 *            boolean Disaggregation is done based on chosen IML
	 * @param imlVal
	 *            double iml value for the disaggregation
	 * @param probVal
	 *            double if disaggregation is done based on prob. then its value
	 */
	private void showGcimResults(String imjName, boolean imlBasedGcim, double imlVal, double probVal) {
		
		String headerString = "";
		if (imlBasedGcim)
			headerString = "GCIM Results: \n" +
						   "Conditioning IM: " + imjName + "\n" +
						   "IML  = " + imlVal + "\n" +
						   "(Prob= " + (float) probVal + ")";
		else
			headerString = "GCIM Results: \n" +
			   "Conditioning IM: " + imjName + "\n" +
			   "Prob = " + probVal + "\n" +
			   "(IML = " + (float) imlVal + ")";


		String gcimResultsString = gcimCalc.getGcimResultsString();
		
		gcimResultsString = headerString + "\n\n" + gcimResultsString;

		new GcimPlotViewerWindow(gcimResultsString);
	}

	/**
	 * Handle the Eqk Forecast List.
	 * 
	 * @param site
	 *            : Selected site
	 * @param imr
	 *            : selected IMR
	 * @param eqkRupForecast
	 *            : List of Eqk Rup forecasts
	 */
	protected void handleForecastList(Site site,
			Map<TectonicRegionType, ScalarIMR> imrMap,
			BaseERF eqkRupForecast) {

		AbstractEpistemicListERF erfList = (AbstractEpistemicListERF) eqkRupForecast;

		numERFsInEpistemicList = erfList.getNumERFs(); // get the num of ERFs in
		// the list

		if (addData) // add new data on top of the existing data
			weightedFuncList = new WeightedFuncListforPlotting();
		// if we are adding to the exsintig data then there is no need to create
		// the new instance
		// weighted functon list.
		else if (!addData && weightedFuncList == null) {
			JOptionPane.showMessageDialog(this, "No ERF List Exists",
					"Wrong selection", JOptionPane.OK_OPTION);
			return;
		}


		XY_DataSetList hazardFuncList = new XY_DataSetList();
		for (int i = 0; i < numERFsInEpistemicList; ++i) {
			// current ERF's being used to calculated Hazard Curve
			currentERFInEpistemicListForHazardCurve = i;
			ArbitrarilyDiscretizedFunc hazFunction = new ArbitrarilyDiscretizedFunc();

			// intialize the hazard function
			initX_Values(hazFunction);
			try {
				try {
					// calculate the hazard curve
					if(isProbabilisticCurve)
						hazFunction = (ArbitrarilyDiscretizedFunc) calc
						.getHazardCurve(hazFunction, site, imrMap, erfList.getERF(i));
					else if(isStochasticCurve) // it's stochastic
						hazFunction = (ArbitrarilyDiscretizedFunc) calc
						.getAverageEventSetHazardCurve(
								hazFunction, site, imrGuiBean.getSelectedIMR(), erfList.getERF(i));
					else
						throw new RuntimeException("Can't disaggregate with deterministic calculations");
					// System.out.println("Num points:"
					// +hazFunction.toString());
				} catch (Exception e) {
					setButtonsEnable(true);
					e.printStackTrace();
					BugReport bug = new BugReport(e, getParametersInfoAsString(), appShortName, getAppVersion(), this);
					BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
					bugDialog.setVisible(true);
				}
				hazFunction = toggleHazFuncLogValues(hazFunction);
			} catch (RuntimeException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(),
						"Parameters Invalid", JOptionPane.INFORMATION_MESSAGE);
				setButtonsEnable(true);
				// e.printStackTrace();
				return;
			}
			hazardFuncList.add(hazFunction);
		}
		weightedFuncList.addList(erfList.getRelativeWeightsList(),
				hazardFuncList);
		// setting the information inside the weighted function list if adding
		// on top of exisintg data
		if (addData)
			weightedFuncList.setInfo(getParametersInfoAsString());
		else
			// setting the information inside the weighted function list if
			// adding the data to the existing data
			weightedFuncList.setInfo(getParametersInfoAsString() + "\n"
					+ "Previous List Info:\n" + "--------------------\n"
					+ weightedFuncList.getInfo());

		// individual curves are to be plotted
		if (!isAllCurves)
			weightedFuncList.setIndividualCurvesToPlot(false);
		else
			weightedFuncList.setIndividualCurvesToPlot(true);

		// if custom fractile needed to be plotted
		if (this.fractileOption
				.equalsIgnoreCase(ERF_EpistemicListControlPanel.CUSTOM_FRACTILE)) {
			weightedFuncList.setFractilesToPlot(true);
			weightedFuncList.addFractiles(epistemicControlPanel
					.getSelectedFractileValues());
		} else
			weightedFuncList.setFractilesToPlot(false);

		// calculate average
		if (this.avgSelected) {
			weightedFuncList.setMeanToPlot(true);
			weightedFuncList.addMean();
		} else
			weightedFuncList.setMeanToPlot(false);

		// adding the data to the functionlist if adding on top
		if (addData)
			functionList.add(weightedFuncList);
		// set the X, Y axis label
		String xAxisName = imtGuiBean.getSelectedIMT();
		String yAxisName = "Probability of Exceedance";
		graphWidget.setXAxisLabel(xAxisName);
		graphWidget.setYAxisLabel(yAxisName);
	}

	/**
	 * This function is to whether to plot ERF_GuiBean or ERF_RupSelectorGuiBean
	 * 
	 * @param e
	 */
	protected void probDeterSelectionChange() {

		//Set previous type
		String prevTypeCalc;
		if(isProbabilisticCurve) 
			prevTypeCalc = PROBABILISTIC;
		else if(isDeterministicCurve)
			prevTypeCalc = DETERMINISTIC;
		else
			prevTypeCalc = STOCHASTIC;

		// set new type
		String selectedControl = probDeterComboBox.getSelectedItem().toString();
		if (selectedControl.equalsIgnoreCase(PROBABILISTIC)) {
			isProbabilisticCurve = true;
			isStochasticCurve=false;
			isDeterministicCurve=false;
		} 
		else if (selectedControl.equalsIgnoreCase(STOCHASTIC)) {
			isProbabilisticCurve = false;
			isStochasticCurve=true;
			isDeterministicCurve=false;
		} 
		else if (selectedControl.equalsIgnoreCase(DETERMINISTIC)) {
			isProbabilisticCurve = false;
			isStochasticCurve=false;
			isDeterministicCurve=true;
		}

		// only allow multiple IMRs if it's probabilistic for now
		imrGuiBean.setMultipleIMRsEnabled(selectedControl.equalsIgnoreCase(PROBABILISTIC));

		// Update ERF GUI Beans

		// If it's changed FROM Deterministic
		if (prevTypeCalc.equalsIgnoreCase(DETERMINISTIC)) {
			try {
				paramsTabbedPane.remove(1);		
				paramsTabbedPane.add(erfGuiBean, "ERF & Time Span");		
			} catch (RuntimeException ee) {
				ee.printStackTrace();
				JOptionPane.showMessageDialog(this, "Connection to ERF failed",
						"Internet Connection Problem", JOptionPane.OK_OPTION);
				System.exit(0);
			}
		} 
		// If it's changed TO Deterministic
		else if (selectedControl.equalsIgnoreCase(DETERMINISTIC)) {
			try {
				initERFSelector_GuiBean();
				paramsTabbedPane.remove(1);		
				paramsTabbedPane.add(erfRupSelectorGuiBean, "ERF & Time Span");		
			} catch (RuntimeException ee) {
				ee.printStackTrace();
				JOptionPane.showMessageDialog(this, "Connection to ERF failed",
						"Internet Connection Problem", JOptionPane.OK_OPTION);
				System.exit(0);
			}
		}

		calc = null;
		createCalcInstance();
	}

	/**
	 * Initialize the IMR Gui Bean
	 */
	protected void initIMR_GuiBean() {
		AttenuationRelationshipsInstance instances = new AttenuationRelationshipsInstance();
		ArrayList<ScalarIMR> imrs = instances.createIMRClassInstance(null);
		for (ScalarIMR imr : imrs) {
			imr.setParamDefaults();
		}
		// TODO add make the multi imr bean handle warnings

		imrGuiBean = new IMR_MultiGuiBean(imrs);
		imrGuiBean.addIMRChangeListener(this);
		imrGuiBean.setMaxChooserChars(30);
		imrGuiBean.rebuildGUI();
	}

	/**
	 * Initialize the IMT Gui Bean
	 */
	private void initIMT_GuiBean() {
		// create the IMT Gui Bean object

		imtGuiBean = new IMT_NewGuiBean(imrGuiBean);
		imtGuiBean.setSelectedIMT(SA_Param.NAME);
		imtGuiBean.setMinimumSize(new Dimension(200, 90));
		imtGuiBean.setPreferredSize(new Dimension(290, 220));
		//		imtGuiBean = new IMT_GuiBean(imrGuiBean.getIMRs());
	}

	/**
	 * Initialize the site gui bean
	 */
	protected void initSiteGuiBean() {
		siteGuiBean = new Site_GuiBean();
		siteGuiBean.addSiteParams(imrGuiBean.getMultiIMRSiteParamIterator());
	}

	// NOTE Old from server mode
//	/**
//	 * Initialize the ERF Gui Bean
//	 */
//	protected void initERF_GuiBean() {
//
//		if (erfGuiBean == null) {
//			try {
//				// create the ERF Gui Bean object
//				ArrayList<String> erf_Classes = new ArrayList<String>();
//				// adding the RMI based ERF's to the application
//				erf_Classes.add(Frankel96_AdjustableEqkRupForecastClient.class.getName());
//				erf_Classes.add(WGCEP_UCERF1_EqkRupForecastClient.class.getName());
//				// erf_Classes.add(RMI_STEP_FORECAST_CLASS_NAME);
//				erf_Classes.add(STEP_AlaskanPipeForecastClient.class.getName());
//				erf_Classes.add(FloatingPoissonFaultERF_Client.class.getName());
//				erf_Classes.add(Frankel02_AdjustableEqkRupForecastClient.class.getName());
//				erf_Classes.add(PEER_AreaForecastClient.class.getName());
//				erf_Classes.add(PEER_MultiSourceForecastClient.class.getName());
//				erf_Classes.add(PEER_NonPlanarFaultForecastClient.class.getName());
//				erf_Classes.add(PoissonFaultERF_Client.class.getName());
//				erf_Classes.add(Point2MultVertSS_FaultERF_Client.class.getName());
//				erf_Classes.add(WG02_FortranWrappedERF_EpistemicListClient.class.getName());
//				erf_Classes.add(PEER_LogicTreeERF_ListClient.class.getName());
//				erf_Classes.add(Point2MultVertSS_FaultERF_ListClient.class.getName());
//
//				erfGuiBean = new ERF_GuiBean(erf_Classes);
//				erfGuiBean.getParameter(ERF_GuiBean.ERF_PARAM_NAME)
//				.addParameterChangeListener(this);
//			} catch (InvocationTargetException e) {
//				e.printStackTrace();
//				BugReport bug = new BugReport(e, errorInInitializationMessage, appShortName, getAppVersion(), this);
//				BugReportDialog bugDialog = new BugReportDialog(this, bug, true);
//				bugDialog.setVisible(true);
//			}
//		} else {
//			boolean isCustomRupture = erfRupSelectorGuiBean
//			.isCustomRuptureSelected();
//			if (!isCustomRupture) {
//				EqkRupForecastBaseAPI eqkRupForecast = erfRupSelectorGuiBean
//				.getSelectedEqkRupForecastModel();
//				erfGuiBean.setERF(eqkRupForecast);
//			}
//		}
//		//		erfPanel.removeAll();
//		//		erfPanel.add(erfGuiBean, BorderLayout.CENTER);
//		//		 erfPanel.add(erfGuiBean, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0,
//		//		 GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,
//		//		 0));
//
//		// TODO delete; not sure why needed, ui shouldn't have changed from launch
//		//erfPanel.updateUI();
//
//	}

// NOTE New from local mode
	/**
	 * Initialize the ERF Gui Bean
	 */
	protected void initERF_GuiBean() {

		if(erfGuiBean == null){
			// create the ERF Gui Bean object

			try {
				erfGuiBean = new ERF_GuiBean(ERF_Ref.get(true, ServerPrefUtils.SERVER_PREFS));
				erfGuiBean.getParameter(ERF_GuiBean.ERF_PARAM_NAME).
				addParameterChangeListener(this);
			}
			catch (InvocationTargetException e) {
				e.printStackTrace();
				
				BugReport bug = new BugReport(e, errorInInitializationMessage, appShortName, getAppVersion(), this);
				BugReportDialog bugDialog = new BugReportDialog(this, bug, true);
				bugDialog.setVisible(true);
			}
		}
		else{
			boolean isCustomRupture = erfRupSelectorGuiBean.isCustomRuptureSelected();
			if(!isCustomRupture){
				BaseERF eqkRupForecast = erfRupSelectorGuiBean.getSelectedEqkRupForecastModel();
				erfGuiBean.setERF(eqkRupForecast);
			}
		}
//		erfPanel.removeAll(); TODO clean
//		erfPanel.add(erfGuiBean, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0,
//				GridBagConstraints.CENTER,GridBagConstraints.BOTH, new Insets(0,0,0,0), 0, 0 ));
//		erfPanel.updateUI();
	}

// NOTE Old from server mode
//	/**
//	 * Initialize the ERF Rup Selector Gui Bean
//	 */
//	protected void initERFSelector_GuiBean() {
//
//		EqkRupForecastBaseAPI erf = null;
//		try {
//			erf = erfGuiBean.getSelectedERF();
//		} catch (InvocationTargetException ex) {
//			ex.printStackTrace();
//		}
//		if (erfRupSelectorGuiBean == null) {
//			// create the ERF Gui Bean object
//			ArrayList<String> erf_Classes = new ArrayList<String>();
//
//			/**
//			 * The object class names for all the supported Eqk Rup Forecasts
//			 */
//			erf_Classes.add(PoissonFaultERF_Client.class.getName());
//			erf_Classes.add(Frankel96_AdjustableEqkRupForecastClient.class.getName());
//			erf_Classes.add(WGCEP_UCERF1_EqkRupForecastClient.class.getName());
//			erf_Classes.add(STEP_EqkRupForecastClient.class.getName());
//			erf_Classes.add(STEP_AlaskanPipeForecastClient.class.getName());
//			erf_Classes.add(FloatingPoissonFaultERF_Client.class.getName());
//			erf_Classes.add(Frankel02_AdjustableEqkRupForecastClient.class.getName());
//			erf_Classes.add(PEER_AreaForecastClient.class.getName());
//			erf_Classes.add(PEER_NonPlanarFaultForecastClient.class.getName());
//			erf_Classes.add(PEER_MultiSourceForecastClient.class.getName());
//			erf_Classes.add(WG02_FortranWrappedERF_EpistemicListClient.class.getName());
//
//			try {
//				erfRupSelectorGuiBean = new EqkRupSelectorGuiBean(erf,
//						erf_Classes);
//			} catch (InvocationTargetException e) {
//				throw new RuntimeException("Connection to ERF's failed");
//			}
//		}
//		//		erfPanel.removeAll();
//		//		// erfGuiBean = null;
//		//		erfPanel.add(erfRupSelectorGuiBean, new GridBagConstraints(0, 0, 1, 1,
//		//				1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
//		//				defaultInsets, 0, 0));
//		// TODO delete; not sure why needed, ui shouldn't have changed from launch
//		//erfPanel.updateUI();
//	}

// NOTE New from local mode
	/**
	 * Initialize the ERF Rup Selector Gui Bean
	 */
	protected void initERFSelector_GuiBean() {

		BaseERF erf = null;
		try {
			erf = erfGuiBean.getSelectedERF();
		}
		catch (InvocationTargetException ex) {
			ex.printStackTrace();
		}
		if(erfRupSelectorGuiBean == null){
			// create the ERF Gui Bean object
			ArrayList<String> erf_Classes = new ArrayList<String>();

			/**
			 *  The object class names for all the supported Eqk Rup Forecasts
			 */
			erf_Classes.add(PoissonFaultERF.class.getName());
			erf_Classes.add(Frankel96_AdjustableEqkRupForecast.class.getName());
			erf_Classes.add(UCERF2.class.getName());
			erf_Classes.add(UCERF2_TimeIndependentEpistemicList.class.getName());
			erf_Classes.add(MeanUCERF2.class.getName());
			//erf_Classes.add(STEP_FORECAST_CLASS_NAME);
			erf_Classes.add(STEP_AlaskanPipeForecast.class.getName());
			erf_Classes.add(FloatingPoissonFaultERF.class.getName());
			erf_Classes.add(Frankel02_AdjustableEqkRupForecast.class.getName());
			erf_Classes.add(PEER_AreaForecast.class.getName());
			erf_Classes.add(PEER_NonPlanarFaultForecast.class.getName());
			erf_Classes.add(PEER_MultiSourceForecast.class.getName());
			erf_Classes.add(WG02_EqkRupForecast.class.getName());


			try {

				erfRupSelectorGuiBean = new EqkRupSelectorGuiBean(erf,ERF_Ref.get(false, ServerPrefUtils.SERVER_PREFS));
			}
			catch (InvocationTargetException e) {
				throw new RuntimeException("Connection to ERF's failed");
			}
		}
		else
			erfRupSelectorGuiBean.setEqkRupForecastModel(erf);
//		erfPanel.removeAll(); TODO clean
//		//erfGuiBean = null;
//		erfPanel.add(erfRupSelectorGuiBean, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0,
//				GridBagConstraints.CENTER,GridBagConstraints.BOTH, defaultInsets, 0, 0 ));
//		erfPanel.updateUI();
	}

	protected void initCommonControlList() {
		controlPanels = new ArrayList<ControlPanel>();

		controlComboBox.addItem(CONTROL_PANELS);

		/*		Calc Settings Control			*/
		controlComboBox.addItem(CalculationSettingsControlPanel.NAME);
		controlPanels.add(new CalculationSettingsControlPanel(this,this));

		/*		Sites Of Interest Control		*/
		controlComboBox.addItem(SitesOfInterestControlPanel.NAME);
		controlPanels.add(new SitesOfInterestControlPanel(this,
				this.siteGuiBean));

		/*		Site Data Control				*/
		controlComboBox.addItem(SiteDataControlPanel.NAME);
		cvmControlPanel = new SiteDataControlPanel(this, this.imrGuiBean,
				this.siteGuiBean);
		controlPanels.add(cvmControlPanel);

		/*		X Values Control				*/
		controlComboBox.addItem(X_ValuesInCurveControlPanel.NAME);
		controlPanels.add(xValuesPanel = new X_ValuesInCurveControlPanel(this, this));

		/*		Plotting Prefs Control			*/
		controlComboBox.addItem(PlottingOptionControl.NAME);
		controlPanels.add(new PlottingOptionControl(this));

		/*		External XY Data Control		*/
		controlComboBox.addItem(XY_ValuesControlPanel.NAME);
		controlPanels.add(new XY_ValuesControlPanel(this, this));
	}

	/**
	 * Initialize the items to be added to the control list
	 */
	protected void initControlList() {

		initCommonControlList();

		/*		PEER Test Case Control			*/
		controlComboBox.addItem(PEER_TestCaseSelectorControlPanel.NAME);
		controlPanels.add(new PEER_TestCaseSelectorControlPanel(this,
				this, imrGuiBean, siteGuiBean, imtGuiBean, erfGuiBean,
				erfGuiBean.getSelectedERFTimespanGuiBean(),
				this));

		/*		Disagg Control					*/
		controlComboBox.addItem(DisaggregationControlPanel.NAME);
		disaggregationControlPanel = new DisaggregationControlPanel(this, this);
		controlPanels.add(disaggregationControlPanel);
		
		/*		GCIM Control					*/
		controlComboBox.addItem(GcimControlPanel.NAME);
		gcimControlPanel = new GcimControlPanel(this, this);
		controlPanels.add(gcimControlPanel);

		/*		All Peer Tests Control			*/
		controlComboBox.addItem(RunAll_PEER_TestCasesControlPanel.NAME);
		controlPanels.add(new RunAll_PEER_TestCasesControlPanel(this));

		/*		Epistempic list Control		*/
		controlComboBox.addItem(ERF_EpistemicListControlPanel.NAME);
		controlPanels.add(epistemicControlPanel = new ERF_EpistemicListControlPanel(this, this));
	}

	private void selectControlPanel() {
		if (controlComboBox.getItemCount() <= 0)
			return;
		String selectedControl = controlComboBox.getSelectedItem().toString();
		
		if(selectedControl==GcimControlPanel.NAME)
			gcimControlPanel.updateWithParentDetails();
		
		showControlPanel(selectedControl);

		controlComboBox.setSelectedItem(CONTROL_PANELS);
	}

	/**
	 *
	 * @throws RemoteException 
	 * @return the Adjustable parameters for the ScenarioShakeMap calculator
	 */
	public ParameterList getCalcAdjustableParams(){
		return calc.getAdjustableParams();
	}


	/**
	 *
	 * @return the Metadata string for the Calculation Settings Adjustable Params
	 */
	public String getCalcParamMetadataString(){
		ParameterList params = getCalcAdjustableParams();
		if (params == null)
			return "";
		return params.getParameterListMetadataString();
	}




	/**
	 * 
	 * @return the selected IMT
	 */
	public String getSelectedIMT() {
		return imtGuiBean.getSelectedIMT();
	}

	public SiteDataControlPanel getCVMControl() {
		if (this.cvmControlPanel == null)
			cvmControlPanel = new SiteDataControlPanel(this, this.imrGuiBean,
					this.siteGuiBean);
		if (!cvmControlPanel.isInitialized())
			cvmControlPanel.init();
		return cvmControlPanel;
	}

	protected void showControlPanel(String controlName) {
		ControlPanel control = (ControlPanel)ListUtils.getObjectByName(controlPanels, controlName);
		if (control == null)
			throw new NullPointerException("Control Panel '" + controlName + "' not found!");
		showControlPanel(control);
	}

	protected void showControlPanel(ControlPanel control) {
		control.showControlPanel();
	}

	/**
	 * Initialise the item to be added to the Prob and Deter Selection
	 */
	protected void initProbOrDeterList() {
		this.probDeterComboBox.addItem(PROBABILISTIC);
		this.probDeterComboBox.addItem(DETERMINISTIC);
		this.probDeterComboBox.addItem(STOCHASTIC);
	}

	/**
	 * 
	 * @return the Range for the X-Axis
	 */
	public Range getX_AxisRange() {
		return graphWidget.getX_AxisRange();
	}

	/**
	 * 
	 * @return the Range for the Y-Axis
	 */
	public Range getY_AxisRange() {
		return graphWidget.getY_AxisRange();
	}

	/**
	 * This forces use of default X-axis values (according to the selected IMT)
	 */
	public void setCurveXValues() {
		useCustomX_Values = false;
	}

	/**
	 * Sets the hazard curve x-axis values (if user wants custom values x-axis
	 * values). Note that what's passed in is not cloned (the y-axis values will
	 * get modified).
	 * 
	 * @param func
	 */
	public void setCurveXValues(ArbitrarilyDiscretizedFunc func) {
		useCustomX_Values = true;
		function = func;
	}

	/**
	 * Sets ArbitraryDiscretizedFunc inside list containing all the functions.
	 * 
	 * @param function
	 *            ArbitrarilyDiscretizedFunc
	 */
	public void addCurve (
			ArbitrarilyDiscretizedFunc function) {
		functionList.add(function);
		enableMenuButtons();
		List<PlotCurveCharacterstics> plotFeaturesList = getPlottingFeatures();
		plotFeaturesList.add(new PlotCurveCharacterstics(null, 1f, PlotSymbol.CROSS, 4f, Color.BLACK, 1));
		addGraphPanel();
	}

	/**
	 * set x values in log space for Hazard Function to be passed to IMR if the
	 * selected IMT are SA , PGA , PGV or FaultDispl It accepts 1 parameters
	 * 
	 * @param originalFunc
	 *            : this is the function with X values set
	 */
	private void initX_Values(DiscretizedFunc arb) {

		// if not using custom values get the function according to IMT.
		if (!useCustomX_Values)
			function = imtInfo.getDefaultHazardCurve(imtGuiBean
					.getSelectedIMT());

		if (IMT_Info.isIMT_LogNormalDist(imtGuiBean.getSelectedIMT())) {
			for (int i = 0; i < function.size(); ++i)
				arb.set(Math.log(function.getX(i)), 1);

			// System.out.println("11111111111HazFunction: "+arb.toString());
		} else
			throw new RuntimeException("Unsupported IMT");
	}

	/**
	 * set x values back from the log space to the original linear values for
	 * Hazard Function after completion of the Hazard Calculations if the
	 * selected IMT are SA , PGA or PGV It accepts 1 parameters
	 * 
	 * @param hazFunction
	 *            : this is the function with X values set
	 */
	private ArbitrarilyDiscretizedFunc toggleHazFuncLogValues(
			ArbitrarilyDiscretizedFunc hazFunc) {
		int numPoints = hazFunc.size();
		DiscretizedFunc tempFunc = hazFunc.deepClone();
		hazFunc = new ArbitrarilyDiscretizedFunc();
		// take log only if it is PGA, PGV ,SA or FaultDispl

		if (IMT_Info.isIMT_LogNormalDist(imtGuiBean.getSelectedIMT())) {
			for (int i = 0; i < numPoints; ++i)
				hazFunc.set(function.getX(i), tempFunc.getY(i));
			return hazFunc;
		} else
			throw new RuntimeException("Unsupported IMT");
	}

	/**
	 * This function sets whether all curves are to drawn or only fractiles are
	 * to drawn
	 * 
	 * @param drawAllCurves
	 *            :True if all curves are to be drawn else false
	 */
	public void setPlotAllCurves(boolean drawAllCurves) {
		this.isAllCurves = drawAllCurves;
	}

	/**
	 * This function sets the percentils option chosen by the user. User can
	 * choose "No Fractiles", "5th, 50th and 95th Fractile" or "Plot Fractile"
	 * 
	 * @param fractileOption
	 *            : Option selected by the user. It can be set by various
	 *            constant String values in ERF_EpistemicListControlPanel
	 */
	public void setFractileOption(String fractileOption) {
		this.fractileOption = fractileOption;
	}

	/**
	 * This function is needed to tell the applet whether avg is selected or not
	 * This is called from ERF_EpistemicListControlPanel
	 * 
	 * @param isAvgSelected
	 *            : true if avg is selected else false
	 */
	public void setAverageSelected(boolean isAvgSelected) {
		this.avgSelected = isAvgSelected;
	}

	/**
	 * 
	 * @return the String containing the values selected for different
	 *          parameters
	 */
	public String getParametersInfoAsString() {
		return getMapParametersInfoAsHTML().replaceAll("<br>",
				SystemUtils.LINE_SEPARATOR);
	}

	/**
	 * 
	 * @return the String containing the values selected for different
	 *          parameters
	 */
	public String getMapParametersInfoAsHTML() {
		String imrMetadata;

		if (!isDeterministicCurve) { // if Probabilistic calculation then only add the
			// metadata
			imrMetadata = imrGuiBean.getIMRMetadataHTML();
		} else {
			// if deterministic calculations then add all IMR params metadata.
			imrMetadata = imrGuiBean.getSelectedIMR().getAllParamMetadata();
		}

		String calcType = probDeterComboBox.getSelectedItem().toString();

		return "<br>" + "Cacluation Type = " + calcType
		+ "<br><br>" + "IMR Param List:" + "<br>" + "---------------" + "<br>"+ imrMetadata
		+ "<br><br>"
		+ "Site Param List: "
		+ "<br>"
		+ "----------------"
		+ "<br>"
		+ siteGuiBean.getParameterListEditor()
		.getVisibleParametersCloned()
		.getParameterListMetadataString()
		+ "<br><br>"
		+ "IMT Param List: "
		+ "<br>"
		+ "---------------"
		+ "<br>"
		+ imtGuiBean.getVisibleParametersCloned()
		.getParameterListMetadataString()
		+ "<br><br>"
		+ "Forecast Param List: "
		+ "<br>"
		+ "--------------------"
		+ "<br>"
		+ erfGuiBean.getERFParameterList()
		.getParameterListMetadataString()
		+ "<br><br>"
		+ "TimeSpan Param List: "
		+ "<br>"
		+ "--------------------"
		+ "<br>"
		+ erfGuiBean.getSelectedERFTimespanGuiBean()
		.getParameterListMetadataString() 
		+ "<br><br>"
		+ "Calculation Settings: "
		+ "<br>"
		+ "--------------------"
		+ "<br>"
		+ getCalcParamMetadataString();

	}

	/**
	 * 
	 * @return the List for all the ArbitrarilyDiscretizedFunctions and
	 *          Weighted Function list.
	 */
	public List<PlotElement> getCurveFunctionList() {
		return functionList;
	}
	
	private void buildGraphWidget() {
		graphWidget = new GraphWidget();
		graphWidget.setPlotLabel(DEFAULT_TITLE);
		// we know the button cp has a box layout so add clear and peel to it
		JPanel buttonRow = graphWidget.getButtonControlPanel().getButtonRow();
		buttonRow.remove(4); // getting rid of horizontal glue
		buttonRow.add(Box.createHorizontalStrut(10));
		buttonRow.add(clearButton);
		buttonRow.add(peelButton);
		buttonRow.add(Box.createHorizontalGlue());
		plotPanel.add(graphWidget, BorderLayout.CENTER);
	}

	/**
	 * Actual method implementation of the "Peel-Off" This function peels off
	 * the window from the current plot and shows in a new window. The current
	 * plot just shows empty window.
	 */
	protected void peelOffCurves() {
		// clean up old widget
		JPanel buttonRow = graphWidget.getButtonControlPanel().getButtonRow();
		buttonRow.remove(clearButton);
		buttonRow.remove(peelButton);
		plotPanel.remove(graphWidget);
		
		GraphWindow graphWindow = new GraphWindow(graphWidget);
//		graphWindow.pack();
		
		// build new one
		buildGraphWidget();
		clearPlot();
		graphWindow.setVisible(true);
		clearButton.setEnabled(false);
		peelButton.setEnabled(false);
	}

	/**
	 * 
	 * @return the list PlotCurveCharacterstics that contain the info about
	 *          plotting the curve like plot line color , its width and line
	 *          type.
	 */
	public List<PlotCurveCharacterstics> getPlottingFeatures() {
		return graphWidget.getPlottingFeatures();
	}

	private void close() {
		int option = JOptionPane.showConfirmDialog(this,
				"Do you really want to exit the application?\n"
				+ "You will loose all unsaved data.", "Exit App",
				JOptionPane.OK_CANCEL_OPTION);
		if (option == JOptionPane.OK_OPTION)
			System.exit(0);
	}

	/* save plot in PNG format */
	private void save() {
		try {
			graphWidget.save();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(),
					"Save File Error", JOptionPane.OK_OPTION);
			return;
		}
	}

	/* print plot */
	public void print() {
		graphWidget.print();
	}

	public GraphWidget getGraphWidget() {
		return graphWidget;
	}

	/**
	 * This function stops the hazard curve calculation if started, so that user
	 * does not have to wait for the calculation to finish. Note: This function
	 * has one advantage , it starts over the calculation again, but if user has
	 * not changed any other parameter for the forecast, that won't be updated,
	 * so saves time and memory for not updating the forecast everytime, cancel
	 * is pressed.
	 * 
	 * @param e
	 */
	private void cancelCalculation() {
		// stopping the Hazard Curve calculation thread
		calcThread.stop(); // TODO remove dependency on depricated "stop" method
		calcThread = null;
		// close the progress bar for the ERF GuiBean that displays
		// "Updating Forecast".
		erfGuiBean.closeProgressBar();
		// stoping the timer thread that updates the progress bar
		if (timer != null && progressClass != null) {
			timer.stop();
			timer = null;
			progressClass.dispose();
		}
		// stopping the Hazard Curve calculations on server
		if (calc != null) {
			try {
				calc.stopCalc();
				calc = null;
			} catch (RuntimeException ee) {
				ee.printStackTrace();
				BugReport bug = new BugReport(ee, getParametersInfoAsString(), appShortName, getAppVersion(), this);
				BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
				bugDialog.setVisible(true);
			}
		}
		this.isHazardCalcDone = false;
		// making the buttons to be visible
		setButtonsEnable(true);
		cancelButton.setEnabled(false);
	}

	/**
	 * Returns the Disaggregation plot image webaddr to be shown in the plot
	 * window.
	 * 
	 * @return String
	 */
	public String getDisaggregationPlot() {
		try {
			return disaggCalc.getDisaggregationPlotUsingServlet(this
					.getParametersInfoAsString());
		} catch (Exception ex) {
			ex.printStackTrace();
			setButtonsEnable(true);
			BugReport bug = new BugReport(ex, getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
			bugDialog.setVisible(true);
		}
		return null;
	}

	/**
	 * Returns the Source Disaggregated List
	 * 
	 * @return String
	 */
	public String getSourceDisaggregationInfo() {
		try {
			return disaggCalc.getDisaggregationSourceInfo();
		} catch (Exception ex) {
			ex.printStackTrace();
			setButtonsEnable(true);
			BugReport bug = new BugReport(ex, getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
			bugDialog.setVisible(true);
		}
		return null;
	}
	
	/**
	 * Returns the Gcim Results List
	 * 
	 * @return String
	 */
	public String getGcimResults() {
		try {
			return gcimCalc.getGcimResultsString();
		} catch (Exception ex) {
			ex.printStackTrace();
			setButtonsEnable(true);
			BugReport bug = new BugReport(ex, getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
			bugDialog.setVisible(true);
		}
		return null;
	}

	/**
	 * Adding the Cybershake curve to the list of plots
	 * 
	 * @param function
	 *            DiscretizedFuncAPI
	 */
	public void addCybershakeCurveData(DiscretizedFunc function) {
		functionList.add(function);
		List<PlotCurveCharacterstics> plotFeaturesList = getPlottingFeatures();
		plotFeaturesList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f,
				PlotSymbol.FILLED_CIRCLE, 4f, Color.BLACK, 1));
		addGraphPanel();
	}

	/**
	 * Sets the application with the curve type chosen by the Cybershake
	 * application
	 * 
	 * @param isDeterministic
	 *            boolean :If deterministic calculation then make the applicaton
	 *            to plot deterministic curves.
	 */
	public void setCurveType(String calcType) {
		if (calcType.equals(PROBABILISTIC))
			probDeterComboBox.setSelectedItem(PROBABILISTIC);
		else if (calcType.equals(DETERMINISTIC))
			probDeterComboBox.setSelectedItem(DETERMINISTIC);
		else if (calcType.equals(STOCHASTIC))
			probDeterComboBox.setSelectedItem(STOCHASTIC);
		else throw new RuntimeException("Calculation Type Not Supported");
	}

	/**
	 * Returns the IML values being used by the application
	 * 
	 * @return ArrayList
	 */
	public ArrayList<Double> getIML_Values() {

		ArrayList<Double> imlList = new ArrayList<Double>();
		ArbitrarilyDiscretizedFunc func = null;
		if (function != null)
			func = function;
		else
			func = imtInfo.getDefaultHazardCurve(imtGuiBean.getSelectedIMT());

		int size = func.size();
		for (int i = 0; i < size; ++i)
			imlList.add(new Double(func.getX(i)));

		return imlList;
	}

	/**
	 * This returns the Earthquake Forecast GuiBean which allows the the
	 * cybershake control panel to set the forecast parameters from cybershake
	 * control panel, similar to what they are set when calculating cybershaks
	 * curves.
	 */
	public ERF_GuiBean getEqkRupForecastGuiBeanInstance() {
		return erfGuiBean;

	}

	/**
	 * This returns instance to the EqkRupSelectorGuiBean, this allows the
	 * cybershake control panel to set the forecast parameters and select the
	 * same source and rupture as in the cybershake control panel.
	 */
	public EqkRupSelectorGuiBean getEqkSrcRupSelectorGuiBeanInstance() {
		return erfRupSelectorGuiBean;
	}

	/**
	 * This returns the Site Guibean using which allows to set the site
	 * locations in the OpenSHA application from cybershake control panel.
	 */
	public Site_GuiBean getSiteGuiBeanInstance() {
		return siteGuiBean;
	}

	/**
	 * It returns the IMT Gui bean, which allows the Cybershake control panel to
	 * set the same SA period value in the main application similar to selected
	 * for Cybershake.
	 */
	public IMT_NewGuiBean getIMTGuiBeanInstance() {
		return imtGuiBean;
	}

	/**
	 * It returns the IMR Gui bean, which allows the Cybershake control panel to
	 * set the gaussian truncation value in the main application similar to
	 * selected for Cybershake.
	 */
	public IMR_MultiGuiBean getIMRGuiBeanInstance() {
		return imrGuiBean;
	}

	/**
	 * Updates the Site_GuiBean to reflect the chnaged SiteParams for the
	 * selected AttenuationRelationship. This method is called from the
	 * IMR_GuiBean to update the application with the Attenuation's Site Params.
	 * 
	 */
	public void updateSiteParams() {
		siteGuiBean.replaceSiteParams(imrGuiBean.getMultiIMRSiteParamIterator());
		siteGuiBean.validate();
		siteGuiBean.repaint();
	}

	@Override
	public void imrChange(ScalarIMRChangeEvent event) {
		updateSiteParams();
	}
	
	/** 
	 * This method gets the included tectonic region types, which is needed by some control panels
	 */
	public ArrayList<TectonicRegionType> getIncludedTectonicRegionTypes() {
		try {
			ArrayList<TectonicRegionType> includedTectonicRegionTypes =  erfGuiBean.getSelectedERF_Instance().getIncludedTectonicRegionTypes();
			return includedTectonicRegionTypes;
		} catch (InvocationTargetException e) {
			e.printStackTrace();
			imrGuiBean.setTectonicRegions(null);
			return null;
		}
	}

}

