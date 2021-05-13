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

package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.gui;


import static org.opensha.sha.imr.AttenRelRef.AS_1997;
import static org.opensha.sha.imr.AttenRelRef.BA_2008;
import static org.opensha.sha.imr.AttenRelRef.BJF_1997;
import static org.opensha.sha.imr.AttenRelRef.CAMPBELL_1997;
import static org.opensha.sha.imr.AttenRelRef.CB_2008;
import static org.opensha.sha.imr.AttenRelRef.FIELD_2000;
import static org.opensha.sha.imr.AttenRelRef.SADIGH_1997;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.gui.HazardCurveApplication;
import org.opensha.sha.gui.beans.ERF_GuiBean;
import org.opensha.sha.gui.beans.EqkRupSelectorGuiBean;
import org.opensha.sha.gui.beans.IMR_GuiBean;
import org.opensha.sha.gui.beans.IMR_MultiGuiBean;
import org.opensha.sha.gui.controls.CalculationSettingsControlPanel;
import org.opensha.sha.gui.controls.DisaggregationControlPanel;
import org.opensha.sha.gui.controls.PlottingOptionControl;
import org.opensha.sha.gui.controls.SiteDataControlPanel;
import org.opensha.sha.gui.controls.SitesOfInterestControlPanel;
import org.opensha.sha.gui.controls.XY_ValuesControlPanel;
import org.opensha.sha.gui.controls.X_ValuesInCurveControlPanel;
import org.opensha.sha.gui.infoTools.ExceptionWindow;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.AS_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BJF_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Campbell_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Field_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SadighEtAl_1997_AttenRel;

/**
 * <p>Title: CEA_HazardCurveLocalModeApp</p>
 * <p>Description: This application is extension of HazardCurveApplication specific
 * to the California Earthquake Authority that contains a subset of the available
 * IMRs and ERFs.</p>
 * @author : Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public class CEA_HazardCurveLocalModeApp extends HazardCurveApplication {

	protected final static String appURL = "http://www.opensha.org/applications/hazCurvApp/HazardCurveApp.jar";

	public CEA_HazardCurveLocalModeApp(String appShortName) {
		super(appShortName);
	}

	/**
	 * Returns the Application version
	 * @return String
	 */

	public static ApplicationVersion getAppVersion(){
		return new ApplicationVersion(1, 0, 0);
	}



	/**
	 * No version check for CEA Hazard Curve Calculator
	 */
	protected void checkAppVersion(){

		return;

	}  


	/**
	 * Initialize the ERF Gui Bean
	 */
	protected void initERF_GuiBean() {

		if(erfGuiBean == null){
			try {
				erfGuiBean = new ERF_GuiBean(ERF_Ref.UCERF_2, ERF_Ref.WGCEP_UCERF_1, ERF_Ref.FRANKEL_02);
				erfGuiBean.getParameter(erfGuiBean.ERF_PARAM_NAME).
				addParameterChangeListener(this);
			}
			catch (InvocationTargetException e) {

				ExceptionWindow bugWindow = new ExceptionWindow(this, e.getStackTrace(),
						"Problem occured " +
						"during initialization the ERF's. All parameters are set to default.");
				bugWindow.setVisible(true);
				bugWindow.pack();
				//e.printStackTrace();
				//throw new RuntimeException("Connection to ERF's failed");
			}
		}
		else{
			boolean isCustomRupture = erfRupSelectorGuiBean.isCustomRuptureSelected();
			if(!isCustomRupture){
				BaseERF eqkRupForecast = erfRupSelectorGuiBean.getSelectedEqkRupForecastModel();
				erfGuiBean.setERF(eqkRupForecast);
			}
		}
		//    erfPanel.removeAll(); TODO clean
		//    erfPanel.add(erfGuiBean, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0,
		//        GridBagConstraints.CENTER,GridBagConstraints.BOTH, defaultInsets, 0, 0 ));
		//    erfPanel.updateUI();
	}


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

			try {

				erfRupSelectorGuiBean = new EqkRupSelectorGuiBean(erf,
						ERF_Ref.FRANKEL_ADJUSTABLE_96, ERF_Ref.UCERF_2, ERF_Ref.WGCEP_UCERF_1);
			}
			catch (InvocationTargetException e) {
				throw new RuntimeException("Connection to ERF's failed");
			}
		}
		else
			erfRupSelectorGuiBean.setEqkRupForecastModel(erf);
		//   erfPanel.removeAll(); TODO clean
		//   //erfGuiBean = null;
		//   erfPanel.add(erfRupSelectorGuiBean, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0,
		//                GridBagConstraints.CENTER,GridBagConstraints.BOTH, defaultInsets, 0, 0 ));
		//   erfPanel.updateUI();
	}

	/**
	 * This method creates the HazardCurveCalc and Disaggregation Calc(if selected) instances.
	 * Calculations are performed on the user's own machine, no internet connection
	 * is required for it.
	 */
	protected void createCalcInstance(){
		try{
			if(calc == null)
				calc = new HazardCurveCalculator();
			if(disaggregationFlag)
				if(disaggCalc == null)
					disaggCalc = new DisaggregationCalculator();
		}catch(Exception e){

			ExceptionWindow bugWindow = new ExceptionWindow(this,e.getStackTrace(),this.getParametersInfoAsString());
			bugWindow.setVisible(true);
			bugWindow.pack();
			//     e.printStackTrace();
		}
	}

	/**
	 * Initialize the items to be added to the control list
	 */
	protected void initControlList() {
		controlComboBox.addItem(CONTROL_PANELS);
		controlComboBox.addItem(DisaggregationControlPanel.NAME);
		controlComboBox.addItem(CalculationSettingsControlPanel.NAME);
		controlComboBox.addItem(SitesOfInterestControlPanel.NAME);
		controlComboBox.addItem(SiteDataControlPanel.NAME);
		controlComboBox.addItem(X_ValuesInCurveControlPanel.NAME);
		//this.controlComboBox.addItem(MAP_CALC_CONTROL);
		controlComboBox.addItem(PlottingOptionControl.NAME);
		controlComboBox.addItem(XY_ValuesControlPanel.NAME);
	}

	/**
	 * Initialize the IMR Gui Bean
	 */
	protected void initIMR_GuiBean() {
//		ArrayList<String> classNames = new ArrayList<String>();
//		classNames.add(BA_2008_AttenRel.class.getName());
//		classNames.add(CB_2008_AttenRel.class.getName());
//		classNames.add(BJF_1997_AttenRel.class.getName());
//		classNames.add(AS_1997_AttenRel.class.getName());
//		classNames.add(Campbell_1997_AttenRel.class.getName());
//		classNames.add(SadighEtAl_1997_AttenRel.class.getName());
//		classNames.add(Field_2000_AttenRel.class.getName());
//
//		AttenuationRelationshipsInstance inst = new AttenuationRelationshipsInstance(classNames);
//
//		imrGuiBean = new IMR_MultiGuiBean(inst.createIMRClassInstance(null));
//		imrGuiBean.addIMRChangeListener(this);

		List<? extends ScalarIMR> imrs = AttenRelRef.instanceList(null, true,
			BA_2008, CB_2008, BJF_1997, AS_1997, CAMPBELL_1997, SADIGH_1997,
			FIELD_2000);
		for (ScalarIMR imr : imrs) {
			imr.setParamDefaults();
		}

		imrGuiBean = new IMR_MultiGuiBean(imrs);
		imrGuiBean.addIMRChangeListener(this);

		// show this gui bean the JPanel
		//     imrPanel.add(this.imrGuiBean,new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0,
		//         GridBagConstraints.CENTER, GridBagConstraints.BOTH, defaultInsets, 0, 0 ));
		//     imrPanel.updateUI(); TODO clean
	}

	public static void main(String[] args) {
		CEA_HazardCurveLocalModeApp applet = new CEA_HazardCurveLocalModeApp(
				HazardCurveApplication.APP_SHORT_NAME);
		applet.checkAppVersion();
		applet.init();
		applet.setTitle("CEA Hazard Curve Calculator "+"("+getAppVersion()+")" );
		applet.setVisible(true);
	}
}
