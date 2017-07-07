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
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Map;

import javax.swing.BorderFactory;
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
import javax.swing.UIManager;
import javax.swing.border.LineBorder;

import org.apache.commons.lang3.SystemUtils;
import org.opensha.commons.data.Site;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.gui.HazardCurveApplication;
import org.opensha.sha.gui.beans.ERF_GuiBean;
import org.opensha.sha.gui.beans.EqkRupSelectorGuiBean;
import org.opensha.sha.gui.beans.IMR_GuiBean;
import org.opensha.sha.gui.beans.IMR_MultiGuiBean;
import org.opensha.sha.gui.beans.IMT_GuiBean;
import org.opensha.sha.gui.beans.IMT_NewGuiBean;
import org.opensha.sha.gui.beans.Site_GuiBean;
import org.opensha.sha.gui.beans.event.IMTChangeEvent;
import org.opensha.sha.gui.beans.event.IMTChangeListener;
import org.opensha.sha.gcim.ui.infoTools.AttenuationRelationshipsInstance;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.gui.infoTools.ExceptionWindow;
import org.opensha.sha.gcim.Utils;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.gcim.imCorrRel.event.IMCorrRelChangeEvent;
import org.opensha.sha.gcim.imCorrRel.event.IMCorrRelChangeListener;
import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.BakerJayaram08_ImCorrRel;
import org.opensha.sha.gcim.ui.infoTools.ImCorrelationRelationshipsInstance;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.event.ScalarIMRChangeEvent;
import org.opensha.sha.imr.event.ScalarIMRChangeListener;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;


/**
 * <p>
 * Title: GcimEditImiControlPanel
 * </p>
 * <p>
 * Description: This Control Panel allows a single IMi for which a GCIM
 * distribution is desired to be specified.  This includes the specification 
 * of the IMi's IMR, IMT, IMCorrRel, and site params
 * </p>
 * 
 * @author Brendon Bradley July 2010
 * @version 1.0
 */

public class GcimEditIMiControlPanel extends JFrame implements
ParameterChangeListener, ActionListener, ScalarIMRChangeListener, IMTChangeListener,
IMCorrRelChangeListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Name of the class
	 */
	private final static String NAME = "GcimEditIMiControlPanel";
	// for debug purpose
	protected final static boolean D = false;

	/**
	 * List of ArbitrarilyDiscretized functions and Weighted funstions
	 */
	protected ArrayList<Object> functionList = new ArrayList<Object>();

	// applet which called this control panel
	GcimControlPanel parent;
	private GridBagLayout gridBagLayout1 = new GridBagLayout();
	
	private JFrame frame;
	
	private Component parentComponent;
	private int imiIndex;
	private boolean guiInitialized = false;
	
	// accessible components
	private JSplitPane imrImtSplitPane;
	private JSplitPane imCorrRelSiteSplitPane;

	protected IMR_MultiGuiBean imrGuiBean;
	protected IMCorrRel_MultiGuiBean imCorrRelGuiBean;
	private IMT_GcimGuiBean imtGuiBean;
	protected GcimSite_GuiBean siteGuiBean;
	private Site parentSite;
	private Site gcimSite;
	private Parameter<Double> parentIMj;
	private ArrayList<String> ParentIMiList;
	private ArrayList<String> ParentIMiListCurrentIMiRemoved;

	// instances of various calculators
	protected CalcProgressBar startAppProgressClass;

	protected final static String versionURL = "http://www.opensha.org/applications/hazCurvApp/HazardCurveApp_Version.txt";
	protected final static String appURL = "http://www.opensha.org/applications/hazCurvApp/HazardCurveServerModeApp.jar";
	protected final static String versionUpdateInfoURL = "http://www.opensha.org/applications/hazCurvApp/versionUpdate.html";

	
	public GcimEditIMiControlPanel(GcimControlPanel parent,
			Component parentComponent, int imiIndex) {
		super(NAME);
		this.parent = parent;
		this.parentComponent = parentComponent;
		this.imiIndex = imiIndex;
		
		//Set parent site params
		getParentSite();
		getGcimSite();
		//Get the IMj name and also the current IMi list
		getParentIMjName();
		getParentIMiList();
	}
	
	/**
	 * This method initalizes the applet using information which has been previously input
	 * @param imiNum - the imiNum for which the applet is to be created for 
	 */
	public void init(int index) {
		
		// Use the imiNumber to get the previously defined info (IMT, IMR, IMCorrRel, Site params)
		Map<TectonicRegionType, ImCorrelationRelationship> imijCorrRels = parent.getImijCorrRel(index);
		Map<TectonicRegionType, ScalarIMR> imiAttenRels = parent.getImiAttenRel(index);
		String imiType = parent.getImiType(index);
		
		//First need to init the GUI
		try {
			// initialize the various GUI beans
			initIMR_GuiBean();
			initIMCorrRel_GuiBean();
			initSiteGuiBean(); 
			//Before initializing the IMT bean need to remove the IMT for the current index else it will not be allowed to be set
			ParentIMiListCurrentIMiRemoved = (ArrayList<String>) ParentIMiList.clone();
			ParentIMiListCurrentIMiRemoved.remove(index);
			initIMT_GuiBean();
			
			
			jbInit();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//Now that the GUI is init come back and feed in the correct details
		ImCorrelationRelationship firstIMCorrRelFromMap = Utils.getFirstIMCorrRel(imijCorrRels);
		ScalarIMR firstIMRFromMap = TRTUtils.getFirstIMR(imiAttenRels);
		
		imtGuiBean.setSelectedIMT(imiType);
		
		if (imiType==SA_Param.NAME) {
			double period = ((SA_Param) firstIMRFromMap.getIntensityMeasure()).getPeriodParam().getValue(); 
			imtGuiBean.setSelectedIMTPeriod(period);
		} else if (imiType==SA_InterpolatedParam.NAME) {
			double period = ((SA_InterpolatedParam) firstIMRFromMap.getIntensityMeasure()).getPeriodInterpolatedParam().getValue(); 
			imtGuiBean.setSelectedIMTPeriod(period);
		}
		
		//if the CorrRel and AttenRel are not in multi/tectonic region type mode then set single else multiple
		int numTecRegionImijCorrRel = imijCorrRels.size();
		if (numTecRegionImijCorrRel==1) {
			imCorrRelGuiBean.setSelectedSingleIMCorrRel(firstIMCorrRelFromMap.getName());
		} else {
			imCorrRelGuiBean.setMultipleIMCorrRels(true);
			for (TectonicRegionType trt : imijCorrRels.keySet()) {
				ImCorrelationRelationship imCorrRel = imijCorrRels.get(trt);
				imCorrRelGuiBean.setIMCorrRel(imCorrRel.getName(), trt);
			}			
		}
		
		//Update the hard-coded IMikCorrRels //TODO remove once gui created to handle this
		boolean setDefaultIMikCorrRelinGUIsuccess = setDefaultIMikCorrRelinGUI();
		//end of setting IMTsinIMCorrRels for off-diagonal CorrRels
		
		int numTecRegionImiAttenRel = imiAttenRels.size();
		if (numTecRegionImiAttenRel==1) {
			imrGuiBean.setSelectedSingleIMR(firstIMRFromMap.getName());
		} else {
			imrGuiBean.setMultipleIMRs(true);
			for (TectonicRegionType trt : imiAttenRels.keySet()) {
				ScalarIMR imr = imiAttenRels.get(trt);
				imrGuiBean.setIMR(imr.getName(), trt);
			}
		}
		
		//now the GUI is init, change flag, and update names etc
		guiInitialized = true;
		imtGuiBean.setIMTinIMRs(imrGuiBean.getIMRMap());
		imtGuiBean.setIMTsinIMCorrRels(imCorrRelGuiBean.getIMCorrRelMap(), parentIMj);
		//Now set the IMTsinIMCorrRels for off-diagonal CorrRels
		int imiIndex = this.imiIndex;
		int numIMikCorrRelsToSet = (imiIndex+1)*(imiIndex)/2 - (imiIndex)*(imiIndex-1)/2;
		ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> IMikCorrRelMapList = null;
		if (numIMikCorrRelsToSet>0) {
			IMikCorrRelMapList = imCorrRelGuiBean.getIMikCorrRelMap();
		}
		for (int m=0; m<numIMikCorrRelsToSet; m++) {
			Map<TectonicRegionType, ImCorrelationRelationship> IMikCorrRelMap =
				IMikCorrRelMapList.get(m);
			Parameter<Double> imi = parent.getImiParam(m);
			imtGuiBean.setIMTsinIMCorrRels(IMikCorrRelMap, imi);
		}
		//end of setting IMTsinIMCorrRels for off-diagonal CorrRels
		parent.updateIMiDetailsInArrayLists(imiIndex);
		parent.updateIMiNames();

	};

	/**
	 * This method initialises the applet assuming no prior information
	 */
	public void init() {
		try {			
			
			// initialize the various GUI beans
			initIMR_GuiBean();
			initIMCorrRel_GuiBean();
			initSiteGuiBean();
			//for new init() then there is no current IMi so nothing needs to be removed
			ParentIMiListCurrentIMiRemoved = ParentIMiList;
			initIMT_GuiBean();

			jbInit();
			
			boolean setDefaultIMRinGUIsuccess = setDefaultIMRinGUI();
			boolean setDefaultIMCorrRelinGUIsuccess = setDefaultIMCorrRelinGUI(); 
			boolean setDefaultIMikCorrRelinGUIsuccess = setDefaultIMikCorrRelinGUI();
			boolean setDefaultIMTinGUIsuccess = setDefaultIMTinIMTGUI(); 
			
			//now the GUI is init, change flag, and update names etc
			guiInitialized = true;

			imtGuiBean.setIMTinIMRs(imrGuiBean.getIMRMap());
			imtGuiBean.setIMTsinIMCorrRels(imCorrRelGuiBean.getIMCorrRelMap(), parentIMj);
			//Now set the IMTsinIMCorrRels for off-diagonal CorrRels
			int imiIndex = this.imiIndex;
			int numIMikCorrRelsToSet = (imiIndex+1)*(imiIndex)/2 - (imiIndex)*(imiIndex-1)/2;
			ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> IMikCorrRelMapList = null;
			if (numIMikCorrRelsToSet>0) {
				IMikCorrRelMapList = imCorrRelGuiBean.getIMikCorrRelMap();
			}
			for (int m=0; m<numIMikCorrRelsToSet; m++) {
				Map<TectonicRegionType, ImCorrelationRelationship> IMikCorrRelMap =
					IMikCorrRelMapList.get(m);
				Parameter<Double> imi = parent.getImiParam(m);
				imtGuiBean.setIMTsinIMCorrRels(IMikCorrRelMap, imi);
			}
			//end of setting IMTsinIMCorrRels for off-diagonal CorrRels
			parent.updateIMiDetailsInArrayLists(imiIndex);
			parent.updateIMiNames();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Component initialization 
	protected void jbInit() throws Exception {

		Color bg = new Color(220,220,220);

		GridBagConstraints gbc = new GridBagConstraints(
				0, 0, 1, 1, 1.0, 1.0, 
				GridBagConstraints.LINE_START, 
				GridBagConstraints.NONE, 
				new Insets(0,0,0,0),0,0);


		// ======== param panels ========

		// IMR, IMT panel
		imrImtSplitPane = new JSplitPane(
				JSplitPane.VERTICAL_SPLIT, true, 
				imtGuiBean, imrGuiBean);
		imrImtSplitPane.setResizeWeight(0.18);
		imrImtSplitPane.setBorder(null);
		imrImtSplitPane.setOpaque(false);
		imrImtSplitPane.setMinimumSize(new Dimension(80,100));
		imrImtSplitPane.setPreferredSize(new Dimension(80,100));
		
		// IMCorrRel & Site panel
		imCorrRelSiteSplitPane = new JSplitPane(
				JSplitPane.VERTICAL_SPLIT, true, 
				imCorrRelGuiBean, siteGuiBean);
		imCorrRelSiteSplitPane.setResizeWeight(0.18);
		imCorrRelSiteSplitPane.setBorder(null);
		imCorrRelSiteSplitPane.setOpaque(false);
		imCorrRelSiteSplitPane.setMinimumSize(new Dimension(80,100));
		imCorrRelSiteSplitPane.setPreferredSize(new Dimension(80,100));
		
		JSplitPane imrImtImCorrRelSiteSplitPane = new JSplitPane(
				JSplitPane.HORIZONTAL_SPLIT, true, 
				imrImtSplitPane, imCorrRelSiteSplitPane);
		imrImtImCorrRelSiteSplitPane.setResizeWeight(0.5);
		imrImtImCorrRelSiteSplitPane.setBorder(
				BorderFactory.createEmptyBorder(2,8,8,8));
		imrImtImCorrRelSiteSplitPane.setOpaque(false);
		
		imrImtImCorrRelSiteSplitPane.setMinimumSize(new Dimension(160,100));
		imrImtImCorrRelSiteSplitPane.setPreferredSize(new Dimension(160,100));


		// ======== content area ========
		imrImtImCorrRelSiteSplitPane.setBorder(null);

		Container content = getContentPane();
		content.setLayout(new BorderLayout());
		content.add(imrImtImCorrRelSiteSplitPane, BorderLayout.CENTER);

		// frame setup
		setTitle("Edit IMi : GCIM panel");
		setSize(500, 500);
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		int xPos = (dim.width - getWidth()) / 2;
		setLocation(xPos, 0);
		
	}
	
	/**
	 * This method gets the first IMR which supports the default IMi
	 * @return
	 */
	public boolean setDefaultIMRinGUI() {
		//find the first IMR which supports the given IMi
		AttenuationRelationshipsInstance instances = new AttenuationRelationshipsInstance();
		ArrayList<ScalarIMR> imrs = instances.createIMRClassInstance(null);
		for (ScalarIMR imr : imrs) {
			//Loop over the IMR supported IMis
			if(imr.isIntensityMeasureSupported(imtGuiBean.getSelectedIMT())) {
				imrGuiBean.setSelectedSingleIMR(imr.getName());
				return true;
			}
		}
		return false;
	}
	
	
	/**
	 * This method gets the first IMikCorrRel which supports the default IMi and IMk
	 * @return
	 */
	public boolean setDefaultIMikCorrRelinGUI() {
		int imiIndex = this.imiIndex;
		int numIMikCorrRelsToSet = (imiIndex+1)*(imiIndex)/2 - (imiIndex)*(imiIndex-1)/2;
		for (int m=0; m<numIMikCorrRelsToSet; m++) {
			boolean setDefaultIMikCorrRelinGUIsuccess = setDefaultIMikCorrRelinGUI(m, ParentIMiList.get(m));
			if (!setDefaultIMikCorrRelinGUIsuccess) //If any error then return false
				return false;
		}
		return true;
	}
	
	
	
	/**
	 * This method gets the first IMCorrRel which supports the default IMi and IMj
	 * @return
	 */
	public boolean setDefaultIMCorrRelinGUI() {
		//find the first IMCorrRel which supports the given IMi - i.e. the "on-diagonal correlation term"
		ImCorrelationRelationshipsInstance imCorrRelInstances = new ImCorrelationRelationshipsInstance();
		ArrayList<ImCorrelationRelationship> imCorrRels = imCorrRelInstances.createImCorrRelClassInstance(null);

		//Loop over all of the ImCorrRels
		for (ImCorrelationRelationship imCorrRel : imCorrRels) {
			//For each IMCorrRel loop over the supported IMjs
			ArrayList<Parameter<?>> imjParamList = imCorrRel.getSupportedIntensityMeasuresjList();
			for (int i = 0; i<imjParamList.size(); i++) {
				Parameter<?> imjParam = imjParamList.get(i);
				//Check if the imjParam is the imjName
				if (imjParam.getName()==parentIMj.getName()) {
					ArrayList<Parameter<?>> imiParamList = imCorrRel.getSupportedIntensityMeasuresiList();
					Parameter<?> imiParam = imiParamList.get(i);
					//Check if the imiParam is that set in the IMT_GuiBean
					if (imiParam.getName()==imtGuiBean.getSelectedIMT()) {
						imCorrRelGuiBean.setSelectedSingleIMCorrRel(imCorrRel.getName());
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * This method gets the first IMCorrRel which supports the default IMi and IMk
	 * @return
	 */
	public boolean setDefaultIMikCorrRelinGUI(int index, String imkName) {
		//find the first IMCorrRel which supports the given IMi - i.e. the "off-diagonal correlation term"
		ImCorrelationRelationshipsInstance imCorrRelInstances = new ImCorrelationRelationshipsInstance();
		ArrayList<ImCorrelationRelationship> imCorrRels = imCorrRelInstances.createImCorrRelClassInstance(null);

		//Loop over all of the ImCorrRels
		for (ImCorrelationRelationship imCorrRel : imCorrRels) {
			//For each IMCorrRel loop over the supported IMjs (i.e. IMks)
			ArrayList<Parameter<?>> imkParamList = imCorrRel.getSupportedIntensityMeasuresjList();
			for (int i = 0; i<imkParamList.size(); i++) {
				Parameter<?> imkParam = imkParamList.get(i);
				//Check if the imkParam is the imkName
				if (imkParam.getName()==imkName) {
					ArrayList<Parameter<?>> imiParamList = imCorrRel.getSupportedIntensityMeasuresiList();
					Parameter<?> imiParam = imiParamList.get(i);
					//Check if the imiParam is that set in the IMT_GuiBean
					if (imiParam.getName()==imtGuiBean.getSelectedIMT()) {
						imCorrRelGuiBean.setSelectedSingleIMikCorrRel(index, imCorrRel);
						return true;
					}
				}
			}
		}
		return false;
	}

	public void actionPerformed(ActionEvent e) {
	}
	


	/**
	 * Provided to allow subclasses to substitute the IMT panel.
	 */
	protected void setImtPanel(ParameterListEditor panel, double resizeWeight) {
		imrImtSplitPane.setTopComponent(panel);
		imrImtSplitPane.setResizeWeight(resizeWeight);
	}

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

		String S = NAME + ": parameterChange(): ";
		if (D)
			System.out.println("\n" + S + "starting: ");

		String e = event.getParameterName();

		// if IMR selection changed, update the site parameter list and
		// supported IMT
		if (e.equalsIgnoreCase(IMR_GuiBean.IMR_PARAM_NAME)) {
			updateSiteParams();
		}
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

		imrGuiBean = new IMR_MultiGuiBean(imrs);
		imrGuiBean.addIMRChangeListener(this);
		imrGuiBean.setMaxChooserChars(30);
		imrGuiBean.rebuildGUI();
		
		imrGuiBean.setTectonicRegions(parent.getIncludedTectonicRegionTypes());
	}

	/**
	 * Initialize the IMT Gui Bean
	 */
	private void initIMT_GuiBean() {
		// create the IMT Gui Bean object

//		imtGuiBean = new IMT_GcimGuiBean(imrGuiBean, imCorrRelGuiBean, parentIMj);
		imtGuiBean = new IMT_GcimGuiBean(imrGuiBean, imCorrRelGuiBean, parentIMj, ParentIMiListCurrentIMiRemoved); 
		imtGuiBean.addIMTChangeListener(this);
//		imtGuiBean.setSelectedIMT(imtGuiBean.getIMtParam(0).getName());
		imtGuiBean.setMinimumSize(new Dimension(200, 90));
		imtGuiBean.setPreferredSize(new Dimension(290, 220));
	}
	
	/**
	 * Sets the default IMT to use in the IMT Gui Bean
	 */
	private boolean setDefaultIMTinIMTGUI() {
		try {
			//imtGuiBean.setSelectedIMT(imtGuiBean.getIMtParam(0).getName());
			imtGuiBean.setSelectedIMT(imtGuiBean.getIMTParams().iterator().next().getName());
		}
		catch(Exception ex) {
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * Initialize the IMCorrRel Gui Bean
	 */
	protected void initIMCorrRel_GuiBean() {
		ImCorrelationRelationshipsInstance instances = new ImCorrelationRelationshipsInstance();
		ArrayList<ImCorrelationRelationship> imCorrRels = instances.createImCorrRelClassInstance(null);

		imCorrRelGuiBean = new IMCorrRel_MultiGuiBean(imCorrRels, parentIMj);
		imCorrRelGuiBean.addIMCorrRelChangeListener(this); 
		imCorrRelGuiBean.setMaxChooserChars(30);
		imCorrRelGuiBean.rebuildGUI();
		
		imCorrRelGuiBean.setTectonicRegions(parent.getIncludedTectonicRegionTypes());
	}

	/**
	 * Initialize the site gui bean
	 */
	protected void initSiteGuiBean() {
		siteGuiBean = new GcimSite_GuiBean(this, parentSite, gcimSite);
		siteGuiBean.addSiteParams(imrGuiBean.getMultiIMRSiteParamIterator());
	}

	/**
	 * 
	 * @return the selected IMiType
	 */
	public String getSelectedIMT() {
		if (D) 
			System.out.println("getting the current IMT: " + imtGuiBean.getSelectedIMT());
		return imtGuiBean.getSelectedIMT();
	}
	/**
	 * 
	 * @return the selected IMi
	 */
	public Parameter<Double> getSelectedIM() {
		if (D) 
			System.out.println("getting the current IM: " + imtGuiBean.getSelectedIM());
		return imtGuiBean.getSelectedIM();
	}
	
	/**
	 * This returns the selected IMR map
	 */
	public Map<TectonicRegionType, ScalarIMR> getSelectedIMRMap() {
		if (D) 
			System.out.println("getting the current IMRmap: " + imrGuiBean.getIMRMap());
		return imrGuiBean.getIMRMap();
	}
	
	/**
	 * This returns the selected IMCorrRel map
	 */
	public Map<TectonicRegionType, ImCorrelationRelationship> getSelectedIMCorrRelMap() {
		if (D) 
			System.out.println("getting the current IMCorrRelmap: " + imCorrRelGuiBean.getIMCorrRelMap());
		return  imCorrRelGuiBean.getIMCorrRelMap();
	}
	
	/**
	 * This returns the selected IMikCorrRel map
	 */
	public ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> getSelectedIMikjCorrRelMap() {
		return imCorrRelGuiBean.getIMikCorrRelMap();
	}

	/**
	 * It returns the IMT Gui bean, which allows the Cybershake control panel to
	 * set the same SA period value in the main application similar to selected
	 * for Cybershake.
	 */
	public IMT_GcimGuiBean getIMTGuiBeanInstance() {
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
		if (guiInitialized) {
			imtGuiBean.setIMTinIMRs(imrGuiBean.getIMRMap());
			parent.updateIMiDetailsInArrayLists(imiIndex);
		}
	}
	
	@Override
	public void imCorrRelChange(IMCorrRelChangeEvent event) {
		if (guiInitialized) {
			imtGuiBean.setIMTsinIMCorrRels(imCorrRelGuiBean.getIMCorrRelMap(), parentIMj);
			parent.updateIMiDetailsInArrayLists(imiIndex);
		}
	}

	@Override
	public void imtChange(IMTChangeEvent e) {
		
		if (guiInitialized) {
			imtGuiBean.setIMTinIMRs(imrGuiBean.getIMRMap());
			imtGuiBean.setIMTsinIMCorrRels(imCorrRelGuiBean.getIMCorrRelMap(), parentIMj);
			
			//Update the hard-coded IMikCorrRels //TODO remove once gui created to handle this
			boolean setDefaultIMikCorrRelinGUIsuccess = setDefaultIMikCorrRelinGUI();
			//Now set the IMTsinIMCorrRels for off-diagonal CorrRels
			int imiIndex = this.imiIndex;
			int numIMikCorrRelsToSet = (imiIndex+1)*(imiIndex)/2 - (imiIndex)*(imiIndex-1)/2;
			ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> IMikCorrRelMapList = null;
			if (numIMikCorrRelsToSet>0) {
				IMikCorrRelMapList = imCorrRelGuiBean.getIMikCorrRelMap();
			}
			for (int m=0; m<numIMikCorrRelsToSet; m++) {
				Map<TectonicRegionType, ImCorrelationRelationship> IMikCorrRelMap =
					IMikCorrRelMapList.get(m);
				Parameter<Double> imi = parent.getImiParam(m);
				imtGuiBean.setIMTsinIMCorrRels(IMikCorrRelMap, imi);
			}
			//end of setting IMTsinIMCorrRels for off-diagonal CorrRels
			
			
			
			parent.updateIMiDetailsInArrayLists(imiIndex);
		}
		parent.updateIMiNames();
		parent.updateIMiListGuiDisplay();
		String blank = "blank";
		parent.setParamsVisible(blank);
	}
	
	/**
	 * This gets the parent site object which are defined in the hazard curve calculator
	 */
	public void getParentSite() {
		this.parentSite = parent.getSite();
	}
	
	/**
	 * This gets the current imiIndex
	 */
	public int getImiIndex() {
		return this.imiIndex;
	}
	
	/**
	 * This gets the GCIM site object
	 */
	public void getGcimSite() {
		this.gcimSite = parent.getGcimSite();
	}
	
	/**
	 * This updates the parameters of the GCIM site object
	 */
	public void updateGcimSite(Site gcimSite) {
		this.gcimSite = gcimSite;
		parent.updateGcimSite(gcimSite);
	}
	
	/**
	 * This method gets the IMjName from the main hazard calcs, used to determine which other IMj are
	 * allowable
	 */
	public Parameter<Double> getParentIMjName() {
		this.parentIMj = parent.getParentIMj();
		return parentIMj;
	}
	
	/**
	 * This method gets the current IMiNames list from the GcimControl Panel 
	 */
	public ArrayList<String> getParentIMiList() {
		this.ParentIMiList = parent.getImiTypes();
		return ParentIMiList;
	}
	
	/** 
	 * THis methods returns the IM parameter which is currently selected in the IMT GUI
	 */
	public Parameter<Double> getIMTSelectedInIMTGUI() {
		 return imtGuiBean.getSelectedIM();
	}
	

	
}
