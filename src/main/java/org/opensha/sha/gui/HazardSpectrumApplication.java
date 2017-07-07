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

package org.opensha.sha.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.WeightedFuncListforPlotting;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.gui.DisclaimerDialog;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.DoubleDiscreteParameter;
import org.opensha.commons.util.bugReports.BugReport;
import org.opensha.commons.util.bugReports.BugReportDialog;
import org.opensha.commons.util.bugReports.DefaultExceptoinHandler;
import org.opensha.sha.calc.SpectrumCalculator;
import org.opensha.sha.calc.SpectrumCalculatorAPI;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.gui.beans.IMLorProbSelectorGuiBean;
import org.opensha.sha.gui.beans.IMT_NewGuiBean;
import org.opensha.sha.gui.controls.ERF_EpistemicListControlPanel;
import org.opensha.sha.gui.controls.PlottingOptionControl;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.gui.util.IconFetcher;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.event.ScalarIMRChangeEvent;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

/**
 * @author nitingupta
 *
 */
public class HazardSpectrumApplication
extends HazardCurveApplication {

	public static final String APP_NAME = "Hazard Spectrum Application";
	public static final String APP_SHORT_NAME = "HazardSpectrumLocal";

	//Static String to tell the IMT as the SA becuase it is the only supported IMT for this Application
	protected static String SA_NAME = "SA";
	private static String SA_PERIOD = "SA Period";
	//Axis Labels
	private static final String IML = "SA (g)";
	private static final String PROB_AT_EXCEED = "Probability of Exceedance";
	private static final String X_AXIS_LABEL = "Period (sec)";

	private IMLorProbSelectorGuiBean imlProbGuiBean;

	//ArrayList that stores the SA Period values for the IMR
	private List saPeriodVector;

	//Graph Title
	protected static final String DEFAULT_TITLE = new String("Response Spectra Curves");

	//instances of various calculators
	protected SpectrumCalculatorAPI calc;
	//Prob@IML or IML@Prob
	boolean probAtIML;

	public HazardSpectrumApplication(String appShortName) {
		super(appShortName);
	}

	/**
	 *
	 * @throws RemoteException 
	 * @return the Adjustable parameters for the ScenarioShakeMap calculator
	 */
	public ParameterList getCalcAdjustableParams(){
		if (calc == null)
			createCalcInstance();
		return calc.getAdjustableParams();
	}

	/**
	 * Initialize the IMR Gui Bean
	 */
	protected void initIMR_GuiBean() {
		super.initIMR_GuiBean();
		imrGuiBean.setMultipleIMRsEnabled(false);

		//sets the Intensity measure for the IMR
		imrGuiBean.getSelectedIMR().setIntensityMeasure(SA_Param.NAME);
	}

	@Override
	protected String getGuideURL() {
		return null;
	}
	
	@Override
	protected String getTutorialURL() {
		return null;
	}

	//Initialize the applet
	public void init() {
		try {

			// initialize the control pick list
			//initControlList();
			//initialise the list to make selection whether to show ERF_GUIBean or ERF_RupSelectorGuiBean
			//initProbOrDeterList();
			startAppProgressClass = new CalcProgressBar("Starting Application", "Initializing Application .. Please Wait");

			// initialize the various GUI beans
			initIMR_GuiBean();
			initImlProb_GuiBean();
			initSiteGuiBean();
			try {
				initERF_GuiBean();
			}
			catch (RuntimeException e) {
				JOptionPane.showMessageDialog(this, "Connection to ERF's failed",
						"Internet Connection Problem",
						JOptionPane.OK_OPTION);
				e.printStackTrace();
				System.exit(0);
			}

			// initialize the GUI components
			jbInit();

			setImtPanel(imlProbGuiBean, 0.28); // swap iml panel

		} catch (Exception e) {
			e.printStackTrace();
			BugReport bug = new BugReport(e, getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, true);
			bugDialog.setVisible(true);
		}
		this.setTitle("Hazard Spectrum Application ("+getAppVersion()+")");
		startAppProgressClass.dispose();
		( (JPanel) getContentPane()).updateUI();
		graphWidget.setPlotLabel(DEFAULT_TITLE);
	}


	/**
	 * This method creates the HazardCurveCalc and Disaggregation Calc(if selected) instances.
	 * Calculations are performed on the user's own machine, no internet connection
	 * is required for it.
	 */
	protected void createCalcInstance(){
		try{
			if(calc == null) {
				calc = new SpectrumCalculator();
			}

			/*if(disaggregationFlag)
        if(disaggCalc == null)
          disaggCalc = new DisaggregationCalculator();*/
		}catch(Exception e){

			BugReport bug = new BugReport(e, getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, true);
			bugDialog.setVisible(true);
			//     e.printStackTrace();
		}
	}



	/**
	 * Gets the probabilities functiion based on selected parameters
	 * this function is called when add Graph is clicked
	 */
	protected void computeHazardCurve() {

		//starting the calculation
		isHazardCalcDone = false;
		numERFsInEpistemicList = 0;
		BaseERF forecast = null;
		EqkRupture rupture = null;
		if (!this.isProbabilisticCurve)
			rupture = this.erfRupSelectorGuiBean.getRupture();

		// get the selected forecast model
		try {
			if (this.isProbabilisticCurve) {
				// whether to show progress bar in case of update forecast
				erfGuiBean.showProgressBar(this.progressCheckBox.isSelected());
				//get the selected ERF instance
				forecast = erfGuiBean.getSelectedERF();
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, e.getMessage(), "Incorrect Values",
					JOptionPane.ERROR_MESSAGE);
			setButtonsEnable(true);
			return;
		}
		if (this.progressCheckBox.isSelected()) {
			progressClass = new CalcProgressBar("Response-Spectrum Calc Status",
			"Beginning Calculation ");
			progressClass.displayProgressBar();
			timer.start();
		}

		// get the selected IMR
		ScalarIMR imr = imrGuiBean.getSelectedIMR();

		getSA_PeriodForIMR(imr);

		// make a site object to pass to IMR
		Site site = siteGuiBean.getSite();

		//initialize the values in condProbfunc with log values as passed in hazFunction
		// intialize the hazard function
		DiscretizedFunc hazFunction =null;

		//what selection does the user have made, IML@Prob or Prob@IML
		String imlOrProb = imlProbGuiBean.getSelectedOption();
		//gets the IML or Prob value filled in by the user
		double imlProbValue = imlProbGuiBean.getIML_Prob();

		final String xAxisName, yAxisName;
		if (imlOrProb.equalsIgnoreCase(IMLorProbSelectorGuiBean.PROB_AT_IML)) {
			yAxisName = PROB_AT_EXCEED;
			probAtIML = true;
		}
		else {
			yAxisName = IML;
			probAtIML = false;
		}
		xAxisName = X_AXIS_LABEL;
		runInEDT(new Runnable() {
			
			@Override
			public void run() {
				graphWidget.setXAxisLabel(xAxisName);
				graphWidget.setYAxisLabel(yAxisName);
			}
		});

		if (forecast instanceof AbstractEpistemicListERF && isProbabilisticCurve) {
			//if add on top get the name of ERF List forecast
			if (addData)
				prevSelectedERF_List = forecast.getName();

			if (!prevSelectedERF_List.equals(forecast.getName()) && !addData) {
				JOptionPane.showMessageDialog(this,
						"Cannot add to existing without selecting same ERF Epistemic list",
						"Input Error",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			this.isEqkList = true; // set the flag to indicate thatwe are dealing with Eqk list
			handleForecastList(site, imr, forecast, imlProbValue);
			//initializing the counters for ERF List to 0, for other ERF List calculations
			currentERFInEpistemicListForHazardCurve = 0;
			numERFsInEpistemicList = 0;
			isHazardCalcDone = true;
			return;
		}

		//making the previuos selected ERF List to be null
		prevSelectedERF_List = null;

		// this is not a eqk list
		this.isEqkList = false;
		// calculate the hazard curve
		try {
			// calculate the hazard curve
			try {
				if (isProbabilisticCurve){
					if(probAtIML)
						hazFunction = (DiscretizedFunc) calc.getSpectrumCurve(
								site, imr, (ERF) forecast,
								imlProbValue,saPeriodVector);
					else{
						hazFunction = new ArbitrarilyDiscretizedFunc();

						// initialize the values in condProbfunc with log values as passed in hazFunction
						initX_Values(hazFunction);
						try {

							hazFunction = calc.getIML_SpectrumCurve(hazFunction,site,imr,
									(ERF)forecast,imlProbValue,saPeriodVector);
						}
						catch (RuntimeException e) {
							e.printStackTrace();
							JOptionPane.showMessageDialog(this, e.getMessage(),
									"Parameters Invalid",
									JOptionPane.INFORMATION_MESSAGE);
							return;
						}
					}
				}
				else {
					runInEDT(new Runnable() {
						
						@Override
						public void run() {
							progressCheckBox.setSelected(false);
							progressCheckBox.setEnabled(false);
						}
					});
					if (probAtIML)//if the user has selected prob@IML
						hazFunction = (DiscretizedFunc) calc.getDeterministicSpectrumCurve(
								site, imr,rupture,  probAtIML, imlProbValue);
					else //if the user has selected IML@prob
						hazFunction = (DiscretizedFunc) calc.getDeterministicSpectrumCurve(
								site, imr,rupture,probAtIML, imlProbValue);
					runInEDT(new Runnable() {
						
						@Override
						public void run() {
							progressCheckBox.setSelected(true);
							progressCheckBox.setEnabled(true);
						}
					});
				}
			}
			catch (Exception e) {
				e.printStackTrace();
				setButtonsEnable(true);
				BugReport bug = new BugReport(e, getParametersInfoAsString(), appShortName, getAppVersion(), this);
				BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
				bugDialog.setVisible(true);
			}
			((ArbitrarilyDiscretizedFunc)hazFunction).setInfo(getParametersInfoAsString());
		}
		catch (RuntimeException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(),
					"Parameters Invalid",
					JOptionPane.INFORMATION_MESSAGE);
			e.printStackTrace();
			setButtonsEnable(true);
			return;
		}
		isHazardCalcDone = true;

		// add the function to the function list
		functionList.add(hazFunction);
	}

	/**
	 * Initialise the IMT_Prob Selector Gui Bean
	 */
	private void initImlProb_GuiBean() {
		imlProbGuiBean = new IMLorProbSelectorGuiBean();
	}

	/**
	 * Initialize the items to be added to the control list
	 */
	protected void initControlList() {
		initCommonControlList();
	}


	/**
	 * It returns the IMT Gui bean, which allows the Cybershake control panel
	 * to set the same SA period value in the main application
	 * similar to selected for Cybershake.
	 */
	@Override
	public IMT_NewGuiBean getIMTGuiBeanInstance() {
		return null;
	}

	/**
	 * Updates the IMT_GuiBean to reflect the chnaged IM for the selected AttenuationRelationship.
	 * This method is called from the IMR_GuiBean to update the application with the Attenuation's
	 * supported IMs.
	 *
	 */
	public void updateIM() {
		return;
	}



	/**
	 *  Any time a control paramater or independent paramater is changed
	 *  by the user in a GUI this function is called, and a paramater change
	 *  event is passed in. This function then determines what to do with the
	 *  information ie. show some paramaters, set some as invisible,
	 *  basically control the paramater lists.
	 *
	 * @param  event
	 */
	public void parameterChange(ParameterChangeEvent event) {

		String S = ": parameterChange(): ";
		if (D)
			System.out.println("\n" + S + "starting: ");

		String name1 = event.getParameterName();

		if (name1.equalsIgnoreCase(this.erfGuiBean.ERF_PARAM_NAME)) {

			String plottingOption = null;
			if (plotOptionControl != null)
				plottingOption = this.plotOptionControl.getSelectedOption();
			// add the Epistemic control panel option if Epistemic ERF is selected
			if (erfGuiBean.isEpistemicList()) {
				showControlPanel(ERF_EpistemicListControlPanel.NAME);
			}
			else if (plottingOption != null &&
					plottingOption.equalsIgnoreCase(PlottingOptionControl.
							ADD_TO_EXISTING)) {
				JOptionPane.showMessageDialog(this,
						"Cannot add to existing without selecting ERF Epistemic list",
						"Input Error",
						JOptionPane.INFORMATION_MESSAGE);
				plotOptionControl.setSelectedOption(PlottingOptionControl.PLOT_ON_TOP);
				setButtonsEnable(true);
			}
		}
	}


	/**
	 * this function is called to draw the graph
	 */
	protected void calculate() {
		setButtonsEnable(false);
		// do not show warning messages in IMR gui bean. this is needed
		// so that warning messages for site parameters are not shown when Add graph is clicked
		//		imrGuiBean.showWarningMessages(false); // TODO need to add this back in
		if(plotOptionControl !=null){
			if(this.plotOptionControl.getSelectedOption().equals(PlottingOptionControl.PLOT_ON_TOP))
				addData = true;
			else
				addData = false;
		}
		try{
			createCalcInstance();
		}catch(Exception e){
			setButtonsEnable(true);
			BugReport bug = new BugReport(e, getParametersInfoAsString(), appShortName, getAppVersion(), this);
			BugReportDialog bugDialog = new BugReportDialog(this, bug, false);
			bugDialog.setVisible(true);
		}

		// check if progress bar is desired and set it up if so
		if(this.progressCheckBox.isSelected())  {

			timer = new Timer(500, new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					try{
						if(!isEqkList){

							int totRupture = calc.getTotRuptures();
							int currRupture = calc.getCurrRuptures();
							if (currRupture != -1)
								progressClass.updateProgress(currRupture, totRupture);

						}
						else{
							if((numERFsInEpistemicList+1) !=0 && !isHazardCalcDone)
								progressClass.updateProgress(currentERFInEpistemicListForHazardCurve,numERFsInEpistemicList);
						}
						if (isHazardCalcDone) {
							timer.stop();
							progressClass.dispose();
							drawGraph();
						}
					}catch(Exception e){
						//e.printStackTrace();
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

			calcThread = new Thread(this);
			calcThread.start();
		}
		else {
			this.computeHazardCurve();
			this.drawGraph();
		}
	}

	/**
	 * Gets the SA Period Values for the IMR
	 * @param imr
	 */
	private void getSA_PeriodForIMR(ScalarIMR imr) {
		for (Parameter<?> tempParam : imr.getSupportedIntensityMeasures()) {
			if (tempParam.getName().equalsIgnoreCase(SA_NAME)) {
				for (Parameter<?> independentParam: tempParam.getIndependentParameterList()) {
					if (independentParam.getName().equalsIgnoreCase(SA_PERIOD)) {
						saPeriodVector = ( (DoubleDiscreteParameter) independentParam).
						getAllowedDoubles();
						return;
					}
				}
			}
		}
	}

	/**
	 * Handle the Eqk Forecast List.
	 * @param site : Selected site
	 * @param imr : selected IMR
	 * @param eqkRupForecast : List of Eqk Rup forecasts
	 */
	protected void handleForecastList(Site site,
			ScalarIMR imr,
			BaseERF forecast,
			double imlProbValue) {

		AbstractEpistemicListERF erfList = (AbstractEpistemicListERF) forecast;

		numERFsInEpistemicList = erfList.getNumERFs(); // get the num of ERFs in the list

		if (addData) //add new data on top of the existing data
			weightedFuncList = new WeightedFuncListforPlotting();
		//if we are adding to the exsintig data then there is no need to create the new instance
		//weighted functon list.
		else if (!addData && weightedFuncList == null) {
			JOptionPane.showMessageDialog(this, "No ERF List Exists",
					"Wrong selection", JOptionPane.OK_OPTION);
			return;
		}


		XY_DataSetList hazardFuncList = new XY_DataSetList();
		for (int i = 0; i < numERFsInEpistemicList; ++i) {
			//current ERF's being used to calculated Hazard Curve
			currentERFInEpistemicListForHazardCurve = i;
			DiscretizedFunc hazFunction = null;

			try {

				if (probAtIML)
					hazFunction = (DiscretizedFunc) calc.getSpectrumCurve(
							site, imr, erfList.getERF(i),
							imlProbValue, saPeriodVector);
				else {
					hazFunction = new ArbitrarilyDiscretizedFunc();

					// initialize the values in condProbfunc with log values as passed in hazFunction
					initX_Values(hazFunction);


					hazFunction = calc.getIML_SpectrumCurve(hazFunction, site, imr,
							erfList.getERF(i),
							imlProbValue, saPeriodVector);


				}
			} catch (RuntimeException e) {
				//e.printStackTrace();
				setButtonsEnable(true);
				JOptionPane.showMessageDialog(this, e.getMessage(),
						"Parameters Invalid",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			//System.out.println("Num points:" +hazFunction.toString());

			hazardFuncList.add(hazFunction);
		}
		weightedFuncList.addList(erfList.getRelativeWeightsList(), hazardFuncList);
		//setting the information inside the weighted function list if adding on top of exisintg data
		if (addData)
			weightedFuncList.setInfo(getParametersInfoAsString());
		else //setting the information inside the weighted function list if adding the data to the existing data
			weightedFuncList.setInfo(getParametersInfoAsString() + "\n" +
					"Previous List Info:\n" +
					"--------------------\n" +
					weightedFuncList.getInfo());

		//individual curves are to be plotted
		if (!isAllCurves)
			weightedFuncList.setIndividualCurvesToPlot(false);
		else
			weightedFuncList.setIndividualCurvesToPlot(true);

		// if custom fractile needed to be plotted
		if (this.fractileOption.equalsIgnoreCase
				(ERF_EpistemicListControlPanel.CUSTOM_FRACTILE)) {
			weightedFuncList.setFractilesToPlot(true);
			weightedFuncList.addFractiles(epistemicControlPanel.
					getSelectedFractileValues());
		}
		else
			weightedFuncList.setFractilesToPlot(false);

		// calculate average
		if (this.avgSelected) {
			weightedFuncList.setMeanToPlot(true);
			weightedFuncList.addMean();
		}
		else
			weightedFuncList.setMeanToPlot(false);

		//adding the data to the functionlist if adding on top
		if (addData)
			functionList.add(weightedFuncList);
		// set the X, Y axis label
	}

	/**
	 * set x values in log space for Hazard Function to be passed to IMR as IMT is
	 * always SA
	 * It accepts 1 parameters
	 *
	 * @param originalFunc :  this is the function with X values set
	 */
	private void initX_Values(DiscretizedFunc arb){

		//iml@Prob then we have to interpolate over a range of X-Values
		if (!useCustomX_Values)
			function = imtInfo.getDefaultHazardCurve(SA_Param.NAME);


		for (int i = 0; i < function.size(); ++i)
			arb.set(function.getX(i), 1);
	}


	/**
	 *
	 * @return the String containing the values selected for different parameters
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

		return "<br>" + "IMR Param List:" + "<br>" +
		"---------------" + "<br>" +
		imrMetadata + "<br><br>" +
		"Site Param List: " + "<br>" +
		"----------------" + "<br>" +
		siteGuiBean.getParameterListEditor().getVisibleParametersCloned().
		getParameterListMetadataString() + "<br><br>" +
		"IML/Prob Param List: " + "<br>" +
		"---------------" + "<br>" +
		imlProbGuiBean.getVisibleParametersCloned().
		getParameterListMetadataString() + "<br><br>" +
		"Forecast Param List: " + "<br>" +
		"--------------------" + "<br>" +
		erfGuiBean.getERFParameterList().getParameterListMetadataString() +
		"<br><br>" +
		"TimeSpan Param List: " + "<br>" +
		"--------------------" + "<br>" +
		erfGuiBean.getSelectedERFTimespanGuiBean().
		getParameterListMetadataString() + "<br><br>" +
		getCalcParamMetadataString();
	}

	//Main method
	public static void main(String[] args) throws IOException {
		new DisclaimerDialog(APP_NAME, APP_SHORT_NAME, getAppVersion());
		DefaultExceptoinHandler exp = new DefaultExceptoinHandler(
				APP_SHORT_NAME, getAppVersion(), null, null);
		Thread.setDefaultUncaughtExceptionHandler(exp);
		launch(exp);
	}
	
	public static HazardSpectrumApplication launch(DefaultExceptoinHandler handler) {
		HazardSpectrumApplication applet = new
				HazardSpectrumApplication(APP_SHORT_NAME);
		if (handler != null) {
			handler.setApp(applet);
			handler.setParent(applet);
		}
		applet.init();
		applet.setIconImages(IconFetcher.fetchIcons(APP_SHORT_NAME));
		applet.setVisible(true);
		return applet;
	}

	@Override
	protected void initProbOrDeterList() {
		this.probDeterComboBox.addItem(PROBABILISTIC);
		this.probDeterComboBox.addItem(DETERMINISTIC);
	}

	@Override
	public void imrChange(ScalarIMRChangeEvent event) {
		super.imrChange(event);
		imrGuiBean.getSelectedIMR().setIntensityMeasure(SA_Param.NAME);
	}

	@Override
	public String getSelectedIMT() {
		return SA_Param.NAME;
	}

}
