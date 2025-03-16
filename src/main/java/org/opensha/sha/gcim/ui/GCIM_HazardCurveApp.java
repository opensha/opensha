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
import java.util.concurrent.CompletableFuture;

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
import org.opensha.commons.util.bugReports.DefaultExceptionHandler;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.HazardCurveCalculatorAPI;
import org.opensha.sha.calc.disaggregation.chart3d.PureJavaDisaggPlotter;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.EpistemicListERF;
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
import org.opensha.sha.gcim.calc.GCIM_DisaggregationCalculator;
import org.opensha.sha.gcim.calc.GCIM_DisaggregationCalculatorAPI;
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

public class GCIM_HazardCurveApp extends HazardCurveApplication {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public static final String APP_NAME = "GCIM Enabled Hazard Curve Application";
	public static final String APP_SHORT_NAME = "GCIM_HazardCurve";

	/**
	 * Name of the class
	 */
	private final static String C = "GCIM_HazardCurveApplication";

	// objects for control panels
	protected CalculationSettingsControlPanel calcParamsControl;
	protected GcimControlPanel gcimControlPanel;

	// flag to check for the gcim functionality
	protected boolean gcimFlag = false;
	private String gcimString;
	protected boolean gcimIMiChangeFlag = false;

	private JPanel plotPanel;

	// instances of various calculators
	protected GCIM_DisaggregationCalculatorAPI disaggCalc;
	protected GcimCalculator gcimCalc;
	// Reuse the `disaggProgressClass` for our disaggCalc
	CalcProgressBar gcimProgressClass;
	// timer threads to show the progress of calculations
	Timer gcimTimer;
	// checks to see if HazardCurveCalculations are done

	//	private final static String POWERED_BY_IMAGE = TODO clean
	//			"logos/PoweredByOpenSHA_Agua.jpg";
	//	private JLabel imgLabel = new JLabel(new ImageIcon(
	//			ImageUtils.loadImage(this.POWERED_BY_IMAGE)));

	// Construct the applet
	public GCIM_HazardCurveApp(String appShortName) {
		super(appShortName);
	}

	public static void main(String[] args) throws IOException {
		new DisclaimerDialog(APP_NAME, APP_SHORT_NAME, getAppVersion());
		DefaultExceptionHandler exp = new DefaultExceptionHandler(
				APP_SHORT_NAME, getAppVersion(), null, null);
		Thread.setDefaultUncaughtExceptionHandler(exp);
		launch(exp);
	}
	
	public static GCIM_HazardCurveApp launch(DefaultExceptionHandler handler) {
		GCIM_HazardCurveApp applet = new GCIM_HazardCurveApp(APP_SHORT_NAME);
		if (handler != null) {
			handler.setApp(applet);
			handler.setParent(applet);
		}
		applet.init();
		applet.setIconImages(IconFetcher.fetchIcons(APP_SHORT_NAME));
		applet.setTitle("GCIM EHazard Curve Application "+"("+getAppVersion()+")" );
		applet.setVisible(true);
		applet.computeButton.requestFocusInWindow();
		applet.createCalcInstance();
		return applet;
	}

	/**
	 * This method creates the HazardCurveCalc and Disaggregation Calc(if selected) instances.
	 * Calculations are performed on the user's own machine, no internet connection
	 * is required for it.
	 */
	@Override
	protected void createCalcInstance() {
		try {
			if (calc == null) {
				calc = new HazardCurveCalculator();
				calc.setTrackProgress(true);
				if (this.calcParamsControl != null) {
					calc.setAdjustableParams(calcParamsControl.getAdjustableCalcParams());
				}
			}
			if (disaggregationFlag) {
				if (disaggCalc == null) {
					disaggCalc = new GCIM_DisaggregationCalculator();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			BugReport bug = new BugReport(e, this.getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, true);
			bugDialog.setVisible(true);
		}
	}
	
	/**
	 * Start all the timers for all calculators.
	 */
	protected void initTimers() {
		super.initTimers();
		startGcimTimer();
	}

	/**
	 * Timer for disaggregation progress bar.
	 * Needs to be overriden to measure GCIM_HazardCurveApp.disaggCalc
	 * instead of the parent HazardCurveApplication.disaggCalc
	 */
	@Override
	protected void startDisaggTimer() {
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
	}
	/**
	 * Timer for GCIM progress bar
	 */
	private void startGcimTimer() {
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
	}
	
	/**
	 * Specify whether gcim is selected or not
	 * TODO: Can users invoke this to select GCIM? May need UI changes
	 * @param isSelected
	 */
	public void setGcimSelected(boolean isSelected) {
		gcimFlag = isSelected;
	}

	/**
	 * Any time a control parameter or independent parameter is changed by the
	 * user in a GUI this function is called, and a parameter change event is
	 * passed in. This function then determines what to do with the information
	 * ie. show some paramaters, set some as invisible, basically control the
	 * parameter lists.
	 * 
	 * @param event
	 */
	@Override
	public void parameterChange(ParameterChangeEvent event) {
		super.parameterChange(event);
		// TODO: if any change is made, should we re-init the GCIM control panel?
		// gcimControlPanel.doinit();
	}

	// TODO: Create parent method with diaggCalc invocations to override.
	// TODO: Create new method for gcimCalc invocations to add inside
	//		 overriden computeHazardCurve
	// TODO: Add a todo explaining how we will combine the disaggCalc APIs
	// It's easier to override the entire computeHazardCurve for now,
	// otherwise the primary disaggCalculator will be invoked.
	/**
	 * Gets the probabilities functiion based on selected parameters this
	 * function is called when add Graph is clicked
	 */
	protected void computeHazardCurve() {
		// starting the calculation
		isHazardCalcDone = false;

		BaseERF forecast = null;

		// Check for interrupts before updating the forecast
		if (isCancelled()) return;

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
			timer.start();
		}

		// Check for interrupts after updating the forecast
		if (isCancelled()) return;

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
		if (forecast instanceof EpistemicListERF && !isDeterministicCurve) {
			// if add on top get the name of ERF List forecast
			if (addData)
				prevSelectedERF_List = forecast.getName();

//			if (!prevSelectedERF_List.equals(forecast.getName()) && !addData) {
			else if (prevSelectedERF_List == null || !prevSelectedERF_List.equals(forecast.getName())) {
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
		try {
			// calculate the hazard curve
			// eqkRupForecast =
			// (EqkRupForecastAPI)FileUtils.loadObject("erf.obj");
			try {
				if (isProbabilisticCurve) {
					hazFunction = (ArbitrarilyDiscretizedFunc) calc.getHazardCurve(hazFunction, site, imrMap,
							(ERF) forecast);
				} else if (isStochasticCurve) {
					hazFunction = (ArbitrarilyDiscretizedFunc) calc.getAverageEventSetHazardCurve(
							hazFunction, site, imrGuiBean.getSelectedIMR(), (ERF) forecast);
				} else { // deterministic
					runInEDT(new Runnable() {
						@Override
						public void run() {
							progressCheckBox.setSelected(false);
							progressCheckBox.setEnabled(false);
						}
					});
					ScalarIMR imr = imrGuiBean.getSelectedIMR();
					EqkRupture rupture = this.erfRupSelectorGuiBean.getRupture();
					hazFunction = (ArbitrarilyDiscretizedFunc) calc.getHazardCurve(hazFunction, site, imr, rupture);
					runInEDT(new Runnable() {
						@Override
						public void run() {
							progressCheckBox.setSelected(true);
							progressCheckBox.setEnabled(true);
						}
					});
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
			if (!isCancelled()) {
				JOptionPane.showMessageDialog(this, e.getMessage(),
						"Parameters Invalid", JOptionPane.INFORMATION_MESSAGE);
			}
			 e.printStackTrace();
			setButtonsEnable(true);
			return;
		}

		// add the function to the function list
		functionList.add(hazFunction);
		// set the X-axis label
		String imt = imtGuiBean.getSelectedIMT();
		String xAxisName = imt + " (" + firstIMRFromMap.getParameter(imt).getUnits() + ")";
		String yAxisName = "Probability of Exceedance";
		runInEDT(new Runnable() {
			@Override
			public void run() {
				graphWidget.setXAxisLabel(xAxisName);
				graphWidget.setYAxisLabel(yAxisName);
			}
		});

		isHazardCalcDone = true;
		disaggregationString = null;
		gcimString = null;

		// Disaggregation with stochastic event sets not yet supported
		if (disaggregationFlag && isStochasticCurve) {
			final Component parent = this;
			runInEDT(new Runnable() {
				@Override
				public void run() {
					JOptionPane.showMessageDialog(parent,
							"Disaggregation not yet supported with stochastic event-set calculations",
							"Input Error", JOptionPane.INFORMATION_MESSAGE);
					setButtonsEnable(true);
				}
			});
			return;
		}

		// checking the disAggregation flag and probability curve is being plotted
		if (disaggregationFlag && isProbabilisticCurve) {
			if (this.progressCheckBox.isSelected()) {
				disaggProgressClass = new CalcProgressBar(this,
						"Disaggregation Status",
				"Beginning disaggregation\u2026");
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
				disaggCalc.setNumSourcesToShow(numSourcesForDisag);
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
			if (disaggSuccessFlag)
				showDisaggregationResults(numSourcesForDisag, disaggrAtIML,
						imlVal, probVal);
			else if (!isCancelled())
				JOptionPane
				.showMessageDialog(
						this,
						"Disaggregation failed because there is "
						+ "no exceedance above \n "
						+ "the given IML (or that interpolated from the chosen probability).",
						"Disaggregation Message", JOptionPane.OK_OPTION);
		}
		runInEDT(new Runnable() {
			@Override
			public void run() {
				setButtonsEnable(true);
			}
		});
		// displays the disaggregation string in the pop-up window

		disaggregationString = null;
		
		// checking the gcim flag and probability curve is being plotted
		if (gcimFlag && isProbabilisticCurve) {

			gcimCalc = new GcimCalculator();
			if (this.progressCheckBox.isSelected()) {
				gcimProgressClass = new CalcProgressBar(
						"GCIM Calc Status",
				"Beginning GCIM calculations ");
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
	@Override
	protected void showDisaggregationResults(int numSourceToShow,
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
		String metadata = getMapParametersInfoAsHTML();
		// String pdfImageLink;
		if (disaggregationControlPanel.isUseGMT()) {
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
			// metadata,binData,sourceDisaggregationList);isaggCalc, metadata,
//					disaggregationControlPanel.isShowDisaggrBinDataSelected());\
			String addr = disaggregationPlotWebAddr
					+ GCIM_DisaggregationCalculator.DISAGGREGATION_PLOT_PDF_NAME;
			new DisaggregationPlotViewerWindow(null, addr, modeString, metadata, binData, sourceDisaggregationList, null);
		} else {
			new DisaggregationPlotViewerWindow(PureJavaDisaggPlotter.buildChartPanel(disaggCalc.getDisaggPlotData()),
					null, modeString, metadata, binData, sourceDisaggregationList, null);
		}
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
	 * Initialize the items to be added to the control list
	 */
	@Override
	protected void initControlList() {

		super.initControlList();
		
		/*		GCIM Control					*/
		controlComboBox.addItem(GcimControlPanel.NAME);
		gcimControlPanel = new GcimControlPanel(this, this);
		controlPanels.add(gcimControlPanel);

		// Epistemic list control wasn't present in HazardCurveApp.
		// TODO: Should we move this to the parent HazardCurveApp or remove altogether?
//		/*		Epistemic List Control		    */
//		controlComboBox.addItem(ERF_EpistemicListControlPanel.NAME);
//		controlPanels.add(epistemicControlPanel = new ERF_EpistemicListControlPanel(this, this));
	}

	@Override
	protected void selectControlPanel() {
		if (controlComboBox.getItemCount() <= 0)
			return;
		String selectedControl = controlComboBox.getSelectedItem().toString();
		
		if (selectedControl == GcimControlPanel.NAME)
			gcimControlPanel.updateWithParentDetails();
		
		showControlPanel(selectedControl);

		controlComboBox.setSelectedItem(CONTROL_PANELS);
	}

	@Override
	protected void cancelCalculation() {
		super.cancelCalculation();
		try {
			if (disaggCalc != null) {
				disaggCalc.stopCalc();
			}
			if (gcimCalc != null) {
				gcimCalc.stopCalc();
			}
		} catch (RuntimeException ee) {
			ee.printStackTrace();
			BugReport bug = new BugReport(ee, getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
			bugDialog.setVisible(true);
		}
		calcFuture.thenRun(() -> {
			// stoping the timer thread that updates the progress bar
			if (disaggTimer != null && disaggProgressClass != null) {
				disaggTimer.stop();
				disaggTimer = null;
				disaggProgressClass.dispose();
			}
			if (gcimTimer != null && gcimProgressClass != null) {
				gcimTimer.stop();
				gcimTimer = null;
				gcimProgressClass.dispose();
			}
		});
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

