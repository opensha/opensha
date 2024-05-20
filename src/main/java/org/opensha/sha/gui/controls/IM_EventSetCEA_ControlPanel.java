package org.opensha.sha.gui.controls;


import java.awt.Component;

import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditorOld;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.impl.CPTParameter;
import org.opensha.sha.calc.IM_EventSet.v01.IM_EventSetScenarioForCEA;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.gui.beans.AttenuationRelationshipGuiBean;
import org.opensha.sha.gui.beans.EqkRupSelectorGuiBean;
import org.opensha.sha.gui.beans.EqkRuptureCreationPanel;
import org.opensha.sha.gui.beans.MapGuiBean;
import org.opensha.sha.gui.beans.SitesInGriddedRectangularRegionGuiBean;
import org.opensha.sha.imr.attenRelImpl.BA_2006_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.param.SimpleFaultParameter;


/**
 * <p>Title:IM_EventSetCEA_ControlPanel </p>
 * <p>Description: It tests IMEventSetScenario </p>
 * @author : Edward (Ned) Field and Nitin Gupta
 * @version 1.0
 */

public class IM_EventSetCEA_ControlPanel extends ConfirmDialogControlPanel {

	public static final String NAME = "Set Params for IMEvent Set Scenario";
	public static final String MESSAGE = "Are you sure to set the parameters"+
										" for a IMEvent Set scenario?";

	//for debugging
	protected final static boolean D = false;


	private EqkRupSelectorGuiBean erfGuiBean;
	private AttenuationRelationshipGuiBean imrGuiBean;
	private SitesInGriddedRectangularRegionGuiBean regionGuiBean;
	private MapGuiBean mapGuiBean;

	private SimpleFaultData simpleFaultData;
	private double aveDipDir;

	//default magnitude.
	private double magnitude = 7.15;

	/**
	 * Accepts 3 params for the EqkRupSelectorGuiBean, AttenuationRelationshipGuiBean, SitesInGriddedRectangularRegionGuiBean
	 * from the applet.
	 * @param erfGuiBean
	 * @param imrGuiBean
	 * @param regionGuiBean
	 * @param MapGuiBean
	 */
	public IM_EventSetCEA_ControlPanel(EqkRupSelectorGuiBean erfGuiBean,
			AttenuationRelationshipGuiBean imrGuiBean, SitesInGriddedRectangularRegionGuiBean regionGuiBean,
			MapGuiBean mapGuiBean, Component parent) {
		super(NAME, MESSAGE, parent);
		//getting the instance for variuos GuiBeans from the applet required to set the
		//default values for the Params for the Puente Hills Scenario.
		this.erfGuiBean = erfGuiBean;
		this.imrGuiBean = imrGuiBean;
		this.regionGuiBean = regionGuiBean;
		this.mapGuiBean = mapGuiBean;
		//mkFaultTrace();
	}

	public void doinit() {
		
	}

	/**
	 * Sets the default Parameters in the Application for the Puente Hill Scenario
	 */
	public void applyControl(){
		//making the ERF Gui Bean Adjustable Param not visible to the user, becuase
		//this control panel will set the values by itself.
		//This is done in the EqkRupSelectorGuiBean
		ParameterEditor paramEditor = erfGuiBean.getParameterEditor(erfGuiBean.RUPTURE_SELECTOR_PARAM_NAME);
		paramEditor.setValue(erfGuiBean.CREATE_RUPTURE);
		paramEditor.refreshParamEditor();
		EqkRuptureCreationPanel erfPanel= (EqkRuptureCreationPanel)erfGuiBean.getEqkRuptureSelectorPanel();

		//changing the ERF to SimpleFaultERF
		paramEditor = erfPanel.getParameterEditor(erfPanel.SRC_TYP_PARAM_NAME);
		paramEditor.setValue(erfPanel.FINITE_SRC_NAME);
		paramEditor.refreshParamEditor();


		// Set rake value to 90 degrees
		erfPanel.getParameter(erfPanel.RAKE_PARAM_NAME).setValue(Double.valueOf(90));

		IM_EventSetScenarioForCEA eventSet = new IM_EventSetScenarioForCEA(); 
		Parameter param = erfPanel.getParameter(erfPanel.FAULT_PARAM_NAME);
		eventSet.createSimpleFaultParam((SimpleFaultParameter)param);

		erfPanel.getParameter(erfPanel.MAG_PARAM_NAME).setValue(Double.valueOf(magnitude));
		erfPanel.getParameterListEditor().refreshParamEditor();


		//checking if the single AttenRel is selected
		boolean isSingleAttenRelSelected =imrGuiBean.isSingleAttenRelTypeSelected();
		//if single attenRel gui is not selected then toggle to the single attenRel gui Panel
		if(!isSingleAttenRelSelected)
			imrGuiBean.toggleBetweenSingleAndMultipleAttenRelGuiSelection();
		// Set the imt as PGA
		ParameterListEditor editor = imrGuiBean.getIntensityMeasureParamEditor();
		editor.getParameterList().getParameter(imrGuiBean.IMT_PARAM_NAME).setValue(PGA_Param.NAME);
		editor.refreshParamEditor();
		//Updating the IMR Gui Bean with the ShakeMap attenuation relationship
		imrGuiBean.setIMR_Selected(BA_2006_AttenRel.NAME);
		imrGuiBean.getSingleAttenRelParamListEditor().refreshParamEditor();

		//Updating the SitesInGriddedRectangularRegionGuiBean with the Puente Hills resion setting
		regionGuiBean.getParameterList().getParameter(regionGuiBean.MIN_LATITUDE).setValue(Double.valueOf(33));
		regionGuiBean.getParameterList().getParameter(regionGuiBean.MAX_LATITUDE).setValue(Double.valueOf(35));
		regionGuiBean.getParameterList().getParameter(regionGuiBean.MIN_LONGITUDE).setValue(Double.valueOf(-119));
		regionGuiBean.getParameterList().getParameter(regionGuiBean.MAX_LONGITUDE).setValue(Double.valueOf(-117));
		regionGuiBean.getParameterList().getParameter(regionGuiBean.GRID_SPACING).setValue(Double.valueOf(.01667));
		regionGuiBean.getParameterList().getParameter(regionGuiBean.SITE_PARAM_NAME).setValue(SitesInGriddedRectangularRegionGuiBean.USE_SITE_DATA);


		// Set some of the mapping params:
		CPTParameter cptParam =
			(CPTParameter)mapGuiBean.getParameterList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setByName(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		mapGuiBean.getParameterList().getParameter(GMT_MapGenerator.COLOR_SCALE_MODE_NAME).
		setValue(GMT_MapGenerator.COLOR_SCALE_MODE_FROMDATA);
		mapGuiBean.getParameterList().getParameter(GMT_MapGenerator.GMT_WEBSERVICE_NAME).setValue(Boolean.valueOf(true));
		mapGuiBean.getParameterList().getParameter(GMT_MapGenerator.LOG_PLOT_NAME).setValue(Boolean.valueOf(false));
		mapGuiBean.refreshParamEditor();
	}
}
