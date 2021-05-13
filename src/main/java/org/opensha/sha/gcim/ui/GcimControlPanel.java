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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.StringTokenizer;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.opensha.commons.data.Site;
import org.opensha.commons.gui.ControlPanel;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.IntegerConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeFailEvent;
import org.opensha.commons.param.event.ParameterChangeFailListener;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.gui.HazardCurveApplication;
import org.opensha.sha.gui.beans.event.IMTChangeEvent;
import org.opensha.sha.gui.beans.event.IMTChangeListener;
import org.opensha.sha.gcim.ui.infoTools.AttenuationRelationshipsInstance;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.BakerJayaram08_ImCorrRel;
import org.opensha.sha.gcim.imr.attenRelImpl.ASI_WrapperAttenRel.BA_2008_ASI_AttenRel;
import org.opensha.sha.gcim.imr.attenRelImpl.SI_WrapperAttenRel.BA_2008_SI_AttenRel;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.ASI_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.SI_Param;
import org.opensha.sha.gcim.ui.infoTools.ImCorrelationRelationshipsInstance;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodInterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;


/**
 * <p>Title: GcimControlPanel</p>
 * <p>Description: This is control panel in which user can choose whether
 * to choose to obtain generalised conditional intensity measure distributions or not. 
 * In addition, prob. can be input by the user</p>
 * <p>Copyright: Copyright (c) 2010</p>
 * <p>Company: </p>
 * @author Brendon Bradley
 * @version 1.0
 */

public class GcimControlPanel extends ControlPanel
	implements ParameterChangeFailListener, ParameterChangeListener, ActionListener{

	public static final String NAME = "GCIM distributions";
	public static final boolean D = false; //debugging

	private final static String GCIM_SUPPORTED_PARAM_NAME = "Gcim Support";
	private final static String GCIM_PROB_PARAM_NAME = "Gcim Prob";
	private final static String GCIM_IML_PARAM_NAME = "Gcim IML";
	
	//Gcim Parameter
	private DoubleParameter gcimProbParam =
		new DoubleParameter(GCIM_PROB_PARAM_NAME, 0, 1, new Double(.01));

	private DoubleParameter gcimIMLParam =
		new DoubleParameter(GCIM_IML_PARAM_NAME, 0, 11, new Double(.1));

	public boolean gcimSupportedIMj = false;
	private StringParameter gcimSupportParameter;
	private StringParameter gcimParameter;
	private StringParameter gcimImisParameter;
	private boolean isGUIInitialized = false;

	private final static String GCIM_PARAM_NAME = "Get GCIM distributions";

	public final static String NO_GCIM = "No GCIM Distributions";
	public final static String GCIM_USING_PROB = "Probability";
	public final static String GCIM_USING_IML = "IML";

	public final static String IMI_LIST_NAME = "IMIs";
	public final static String IMI_LIST_DEFAULT = "(none)";
	
	//sets the Approx CDF Range for GCIM distribution calculation
	private static final String MIN_APPROXZ_PARAM_NAME = "Min Approx. Z value for CDF";
	private static final String MAX_APPROXZ_PARAM_NAME = "Max Approx. Z value for CDF";
	private static final String DELTA_APPROXZ_PARAM_NAME = "Delta Approx. Z value for CDF";
	private DoubleParameter minApproxZParam = new DoubleParameter(MIN_APPROXZ_PARAM_NAME,new Double(-3));
	private DoubleParameter maxApproxZParam = new DoubleParameter(MAX_APPROXZ_PARAM_NAME,new Double(3));
	private DoubleParameter deltaApproxZParam = new DoubleParameter(DELTA_APPROXZ_PARAM_NAME,new Double(0.2));
	
	//Set the number of GCIM realizations variable
	private static final String NUM_GCIM_REALIZATIONS_NAME =  "Num. GCIM realizations";
	private IntegerParameter numGcimRealizationsParam = new IntegerParameter(NUM_GCIM_REALIZATIONS_NAME,new Integer(0));
	
	//Main four Array lists for storing IMT, IMR, IMCorrRel details
	private ArrayList<String> imiTypes = new ArrayList<String>();
	private ArrayList<Map<TectonicRegionType, ScalarIMR>> imiMapAttenRels = 
		new ArrayList<Map<TectonicRegionType, ScalarIMR>>();
	private ArrayList<Map<TectonicRegionType, ImCorrelationRelationship>> imijMapCorrRels = 
		new ArrayList<Map<TectonicRegionType, ImCorrelationRelationship>>();
	//Correlation relations for off-diagonal terms i.e. IMi,IMk|Rup=rup,IMj=imj
	private ArrayList<Map<TectonicRegionType, ImCorrelationRelationship>> imikjMapCorrRels = 
		new ArrayList<Map<TectonicRegionType, ImCorrelationRelationship>>();

	//The Site object that the main hazard calc uses
	private Site parentSite;
	//The Site object used in gcim calculations
	private Site gcimSite;
	//The IMj for which the main hazard calcs are done
	private Parameter<Double> parentIMj;
	private static final String GCIM_SUPPORTED_NAME = "Gcim Distributions";
	private static final String GCIM_NOT_SUPPORTED_IMJ = "Not supported for this IMj";
	
	private int imiIndex;
	
	private ParameterListEditor paramListEditor;

	private boolean isGcimSelected;
	
	private GcimEditIMiControlPanel gcimEditIMiControlPanel;

	// applet which called this control panel
	GCIM_HazardCurveApp parent;
	private GridBagLayout gridBagLayout1 = new GridBagLayout();
	
	private JFrame frame;
	
	private Component parentComponent;
	
	private JButton addButton = new JButton("Add IMi");
	private JButton removeButton = new JButton("Remove IMi");
	private JButton editButton = new JButton("Edit IMi");

	public GcimControlPanel(GCIM_HazardCurveApp parent,
			Component parentComponent) {
		super(NAME);
		this.parent = parent;
		this.parentComponent = parentComponent;
		
		initWithParentDetails();
	}
	
	public void doinit() {
		
		frame = new JFrame();

		// set info strings for parameters
		
		minApproxZParam.setInfo("The approx. min Z value to construct the GCIM CDF");
		maxApproxZParam.setInfo("The approx. max Z value to construct the GCIM CDF");
		deltaApproxZParam.setInfo("The increment in approx. min Z values to construct the GCIM CDF");
		numGcimRealizationsParam.setInfo("The number of realizations from the GCIM distributions");
		
		try {

			ArrayList<String> gcimSupportList = new ArrayList<String>();
			gcimSupportList.add(GCIM_NOT_SUPPORTED_IMJ);
			gcimSupportParameter = new StringParameter(GCIM_SUPPORTED_NAME,gcimSupportList,
					(String)gcimSupportList.get(0));
			
			ArrayList<String> gcimList = new ArrayList<String>();
			gcimList.add(NO_GCIM);
			gcimList.add(GCIM_USING_PROB);
			gcimList.add(GCIM_USING_IML);

			gcimParameter = new StringParameter(GCIM_PARAM_NAME,gcimList,
					(String)gcimList.get(0));
			
			ArrayList<String> gcimImisList = new ArrayList<String>();
			gcimImisList.add(IMI_LIST_DEFAULT);
			
			gcimImisParameter = new StringParameter(IMI_LIST_NAME,gcimImisList,gcimImisList.get(0));

			gcimParameter.addParameterChangeListener(this);
			gcimImisParameter.addParameterChangeListener(this);
			gcimProbParam.addParameterChangeFailListener(this);
			gcimIMLParam.addParameterChangeFailListener(this);

			ParameterList paramList = new ParameterList();
			paramList.addParameter(gcimSupportParameter);
			paramList.addParameter(gcimParameter);
			paramList.addParameter(gcimProbParam);
			paramList.addParameter(gcimIMLParam);
			paramList.addParameter(gcimImisParameter);
			paramList.addParameter(minApproxZParam);
			paramList.addParameter(maxApproxZParam);
			paramList.addParameter(deltaApproxZParam);
			paramList.addParameter(numGcimRealizationsParam);

			paramListEditor = new ParameterListEditor(paramList);
			if (gcimSupportedIMj)
				setParamsVisible((String)gcimParameter.getValue());
			else
				setParamsVisible((String)gcimSupportParameter.getValue());
				

			jbInit();
			// show the window at center of the parent component
			frame.setLocation(parentComponent.getX()+parentComponent.getWidth()/2,0);
			parent.setGcimSelected(isGcimSelected);


		}
		catch(Exception e) {
			e.printStackTrace();
		}
		isGUIInitialized = true;
	}

	// initialize the gui components
	private void jbInit() throws Exception {

		frame.getContentPane().setLayout(new BorderLayout());
		frame.getContentPane().add(paramListEditor, BorderLayout.CENTER);
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		buttonPanel.add(addButton);
		buttonPanel.add(removeButton);
		buttonPanel.add(editButton);
		
		addButton.addActionListener(this);
		removeButton.addActionListener(this);
		editButton.addActionListener(this);
		
		addButton.setEnabled(false);
		removeButton.setEnabled(false);
		editButton.setEnabled(false);
		
		frame.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		
		frame.setTitle("GCIM Control Panel");
		paramListEditor.setTitle("Set GCIM Params");
		frame.setSize(300,200);
	}


	/**
	 *  Shown when a Constraint error is thrown on GCIM ParameterEditor
	 * @param  e  Description of the Parameter
	 */
	public void parameterChangeFailed( ParameterChangeFailEvent e ) {

		StringBuffer b = new StringBuffer();
		Parameter param = ( Parameter ) e.getSource();

		ParameterConstraint constraint = param.getConstraint();
		String oldValueStr = e.getOldValue().toString();
		String badValueStr = e.getBadValue().toString();
		String name = param.getName();


		b.append( "The value ");
		b.append( badValueStr );
		b.append( " is not permitted for '");
		b.append( name );
		b.append( "'.\n" );
		b.append( "Resetting to ");
		b.append( oldValueStr );
		b.append( ". The constraints are: \n");
		b.append( constraint.toString() );

		JOptionPane.showMessageDialog(
				frame, b.toString(),
				"Cannot Change Value", JOptionPane.INFORMATION_MESSAGE
		);

	}


	/**
	 *
	 * @param e ParameterChangeEvent
	 */
	public void parameterChange(ParameterChangeEvent e){
		String paramName = e.getParameterName();
		if(paramName.equals(GCIM_PARAM_NAME))
			setParamsVisible((String)gcimParameter.getValue());
	}

	/**
	 * Returns the number of IMi's to compute GCIM distributions for
	 * @return double
	 */
	public int getNumIMi(){
		StringConstraint stringConst = (StringConstraint) gcimImisParameter.getConstraint();
		int imiListSize = stringConst.size();
		if (imiListSize == 0) {
			throw new RuntimeException("gcimImisParameter is empty!");
		} else if (imiListSize == 1) {
			if (stringConst.getAllowedValues().get(0) == IMI_LIST_DEFAULT)
				return 0;
			else
				return 1;
		} else
			return imiListSize;
//		return imiTypes.size();
	}
	
	/**
	 * Returns the array list of the IMi types 
	 */
	public ArrayList<String> getImiTypes(){
		if (D) {
			System.out.println("Getting the IMiTypes");
			for (int i=0; i<imiTypes.size(); i++) {
				System.out.println(imiTypes.get(i));
			}
		}
		
		return imiTypes;
	}
	
	/**
	 * Returns the array list of the IMRi's corresponding to the IMi's 
	 */
	public ArrayList<? extends Map<TectonicRegionType, ScalarIMR>> getImris(){
		if (D) {
			System.out.println("Getting the IMiAttenRels");
			for (int i=0; i<getNumIMi(); i++) {
				System.out.println(imiMapAttenRels.get(i));
			}
		}
			
		return imiMapAttenRels;
	}
	
	/**
	 * Returns the array list of the ImCorrRel's corresponding to the IMi's 
	 */
	public ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> getImCorrRels(){
		if (D) {
			System.out.println("Getting the IMiCorrRels");
			for (int i=0; i<getNumIMi(); i++) {
				System.out.println(imijMapCorrRels.get(i));
			}
		}
		return imijMapCorrRels;
	}
	
	/**
	 * Returns the array list of the ImCorrRel's corresponding to the IMik's (i.e. off-diagonal terms)
	 */
	public ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> getImikCorrRels(){
		if (D) {
			System.out.println("Getting the IMikCorrRels");
			for (int i=0; i<imiTypes.size(); i++) {
				for (int j=0; j<i; j++) {
					int index = (i)*(i-1)/2+j;
					System.out.println(imikjMapCorrRels.get(index));
				}
			}
		}
		return imikjMapCorrRels;
	}
	
	/**
	 * Returns the mininum Approx. Z value used to get the GCIM CDFs
	 * @return double
	 */
	public double getMinApproxZ(){
		return ((Double)minApproxZParam.getValue()).doubleValue();
	}
	
	/**
	 * Returns the maximum Approx. Z value used to get the GCIM CDFs
	 * @return double
	 */
	public double getMaxApproxZ(){
		return ((Double)maxApproxZParam.getValue()).doubleValue();
	}
	
	/**
	 * Returns the increment in Approx. Z values used to get the GCIM CDFs
	 * @return double
	 */
	public double getDeltaApproxZ(){
		return ((Double)deltaApproxZParam.getValue()).doubleValue();
	}
	
	/**
	 * Returns the number of GCIM distribution realizations
	 * @return int
	 */
	public int getNumGcimRealizations(){
		return (int)numGcimRealizationsParam.getValue();
	}
	
	/**
	 * Makes the parameters visible based on the choice of the user for Disaggregation
	 */
	public void setParamsVisible(String paramValue){
		if(paramValue.equals(GCIM_NOT_SUPPORTED_IMJ)){
			paramListEditor.getParameterEditor(GCIM_SUPPORTED_NAME).setVisible(true);
			paramListEditor.getParameterEditor(GCIM_PARAM_NAME).setVisible(false);
			paramListEditor.getParameterEditor(GCIM_PROB_PARAM_NAME).setVisible(false);
			paramListEditor.getParameterEditor(GCIM_IML_PARAM_NAME).setVisible(false);
			isGcimSelected = false;
			
			paramListEditor.getParameterEditor(IMI_LIST_NAME).setVisible(false);
			
			paramListEditor.getParameterEditor(MIN_APPROXZ_PARAM_NAME).setVisible(false);
			paramListEditor.getParameterEditor(MAX_APPROXZ_PARAM_NAME).setVisible(false);
			paramListEditor.getParameterEditor(DELTA_APPROXZ_PARAM_NAME).setVisible(false);
			paramListEditor.getParameterEditor(NUM_GCIM_REALIZATIONS_NAME).setVisible(false);
			
			addButton.setEnabled(false);
			editButton.setEnabled(false);
			removeButton.setEnabled(false);
			
			frame.setSize(300,200);
			
		} else if(paramValue.equals(NO_GCIM)){
			paramListEditor.getParameterEditor(GCIM_PARAM_NAME).setVisible(true);
			paramListEditor.getParameterEditor(GCIM_SUPPORTED_NAME).setVisible(false);
			paramListEditor.getParameterEditor(GCIM_PROB_PARAM_NAME).setVisible(false);
			paramListEditor.getParameterEditor(GCIM_IML_PARAM_NAME).setVisible(false);
			isGcimSelected = false;
			
			paramListEditor.getParameterEditor(IMI_LIST_NAME).setVisible(false);
			
			paramListEditor.getParameterEditor(MIN_APPROXZ_PARAM_NAME).setVisible(false);
			paramListEditor.getParameterEditor(MAX_APPROXZ_PARAM_NAME).setVisible(false);
			paramListEditor.getParameterEditor(DELTA_APPROXZ_PARAM_NAME).setVisible(false);
			paramListEditor.getParameterEditor(NUM_GCIM_REALIZATIONS_NAME).setVisible(false);
			
			addButton.setEnabled(false);
			editButton.setEnabled(false);
			removeButton.setEnabled(false);
			
			frame.setSize(300,200);
		} else{
			if (paramValue.equals(GCIM_USING_PROB)) {
				paramListEditor.getParameterEditor(GCIM_PROB_PARAM_NAME).
				setVisible(true);
				paramListEditor.getParameterEditor(GCIM_IML_PARAM_NAME).
				setVisible(false);
				isGcimSelected = true;
			}
			else if (paramValue.equals(GCIM_USING_IML)) {
				paramListEditor.getParameterEditor(GCIM_PROB_PARAM_NAME).
				setVisible(false);
				paramListEditor.getParameterEditor(GCIM_IML_PARAM_NAME).
				setVisible(true);
				isGcimSelected = true;
			}

			addButton.setEnabled(true);
			
			if (getNumIMi() > 0) { //Only show most parameters if numIMi > 0
				
				paramListEditor.getParameterEditor(IMI_LIST_NAME).setVisible(true);
				
				paramListEditor.getParameterEditor(MIN_APPROXZ_PARAM_NAME).setVisible(true);
				paramListEditor.getParameterEditor(MAX_APPROXZ_PARAM_NAME).setVisible(true);
				paramListEditor.getParameterEditor(DELTA_APPROXZ_PARAM_NAME).setVisible(true);
				paramListEditor.getParameterEditor(NUM_GCIM_REALIZATIONS_NAME).setVisible(true);
				
				editButton.setEnabled(true);
				removeButton.setEnabled(true);
			}
			else {
				paramListEditor.getParameterEditor(IMI_LIST_NAME).setVisible(true);
				
				paramListEditor.getParameterEditor(MIN_APPROXZ_PARAM_NAME).setVisible(false);
				paramListEditor.getParameterEditor(MAX_APPROXZ_PARAM_NAME).setVisible(false);
				paramListEditor.getParameterEditor(DELTA_APPROXZ_PARAM_NAME).setVisible(false);
				paramListEditor.getParameterEditor(NUM_GCIM_REALIZATIONS_NAME).setVisible(false);
				
				editButton.setEnabled(false);
				removeButton.setEnabled(false);
			}
			
			Dimension curDims = frame.getSize();
			int width = 300;
			int height = 500;
			if (curDims.width > width)
				width = curDims.width;
			if (curDims.height > height)
				height = curDims.height;
			frame.setSize(width,height);
		}
		frame.repaint();
		frame.validate();
		parent.setGcimSelected(isGcimSelected);
	}


	/**
	 *
	 * @return String : Returns on what basis GCIM is being done either
	 * using Probability or IML.
	 */
	public String getGcimParamValue(){
		return (String)gcimParameter.getValue();
	}


	/**
	 * This function returns gcim prob value if GCIM to be done
	 * based on Probability else it returns IML value if GCIM to be done
	 * based on IML. If not gcim to be done , return -1.
	 */
	public double getGcimVal() {

		if(isGcimSelected){
			String paramValue = getGcimParamValue();
			if(paramValue.equals(GCIM_USING_PROB))
				return ( (Double) gcimProbParam.getValue()).doubleValue();
			else if(paramValue.equals(GCIM_USING_IML))
				return ( (Double) gcimIMLParam.getValue()).doubleValue();
		}
		return -1;
	}


	@Override
	public Window getComponent() {
		return frame;
	}
	
	/**
	 * This method is used to update the IMi Names displayed in the GUI
	 */
	public void updateIMiNames() {
		int numIMi = getNumIMi();
		
		StringConstraint stringConst = (StringConstraint) gcimImisParameter.getConstraint();
		ArrayList<String> strings = stringConst.getAllowedStrings();
		//remove all current strings
		for (int i=0; i<strings.size(); i++) {
			String oldName = strings.get(i);
			stringConst.removeString(oldName);
		}
		//replace with updated names
		if (numIMi==0) {
			stringConst.addString(IMI_LIST_DEFAULT);
			updateIMiListGuiDisplay();
		} else {
			for (int i=0; i<numIMi; i++) {
				String imtName = imiTypes.get(i);
				String newName = i+1 + ". " + imtName;
				
				//Get the imt parameter for this imtName
				Parameter<Double> imti = TRTUtils.getFirstIMR(imiMapAttenRels.get(i)).getIntensityMeasure();
				if (imtName == SA_Param.NAME) {
					String periodVal="";
					try {
						periodVal = ((SA_Param)imti).getPeriodParam().getValue().toString();
					} catch (NullPointerException e) {  
						//will happen on initialisation - leave SA period blank
					}
					newName = i+1 + ". " + "SA (" + periodVal + "s)";
				} else if (imtName == SA_InterpolatedParam.NAME) {
					String periodVal="";
					try {
						periodVal = ((SA_InterpolatedParam)imti).getPeriodInterpolatedParam().getValue().toString();
					} catch (NullPointerException e) {  
						//will happen on initialisation - leave SA period blank
					}
					newName = i+1 + ". " + "SA (" + periodVal + "s)";
				}

				strings.set(i, newName);
				stringConst.addString(newName);
				gcimImisParameter.setValue(newName);
					
				updateIMiListGuiDisplay();
			}
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		StringConstraint stringConst = (StringConstraint) gcimImisParameter.getConstraint();
		if (e.getSource().equals(addButton)) {
			if(D) System.out.println("Adding IMi");
			this.imiIndex = getNumIMi();
			if (D) System.out.println("imiIndex increased to " + this.imiIndex);
			
			String newIMIName = "IMi " + (getNumIMi()+1);
			stringConst.addString(newIMIName); 
			//now that we've added one, we need to remove the place holder if it was there.
			if (stringConst.getAllowedStrings().get(0).equals(IMI_LIST_DEFAULT))
				stringConst.removeString(IMI_LIST_DEFAULT);
			gcimImisParameter.setValue(newIMIName);
			updateIMiListGuiDisplay();
			
			this.gcimEditIMiControlPanel = new GcimEditIMiControlPanel(this, this.frame, this.imiIndex);
			gcimEditIMiControlPanel.init();
			gcimEditIMiControlPanel.setVisible(true);
			updateIMiNames();
			
		} else if (e.getSource().equals(removeButton)) {
			
			String selectedIMI = gcimImisParameter.getValue();
			this.imiIndex = stringConst.getAllowedStrings().indexOf(gcimImisParameter.getValue());
			if(D) System.out.println("Removing IMi, index " + imiIndex);
			if (getNumIMi() == 1) {
				// we need to add the place holder back in here since there won't be any
				stringConst.addString(IMI_LIST_DEFAULT);
				removeButton.setEnabled(false);
				editButton.setEnabled(false);
			}
			stringConst.removeString(selectedIMI);
			ArrayList<String> strings = stringConst.getAllowedStrings();
			gcimImisParameter.setValue(strings.get(0));
			updateIMiListGuiDisplay();
			
			removeIMiDetailsInArrayLists(imiIndex);
			
			updateIMiNames();
			
		} else if (e.getSource().equals(editButton)) {
			
			this.imiIndex = stringConst.getAllowedStrings().indexOf(gcimImisParameter.getValue());
			
			if(D) System.out.println("Editing IMi: " + (imiIndex+1));
			
			this.gcimEditIMiControlPanel = new GcimEditIMiControlPanel(this, this.frame, this.imiIndex);
			gcimEditIMiControlPanel.init(imiIndex);
			gcimEditIMiControlPanel.setVisible(true);
			
			updateIMiNames();
			updateIMiListGuiDisplay();
		}
		setParamsVisible(gcimParameter.getValue());
	}
	
	
	/** 
	 * This method puts the default IMT, IMR, IMCorrRel, IMikCorrRel in the EditGcimControlPanel into the ImiTypes,
	 * ImiMapAttenRels, and ImijMapCorrRels, and IMikMapCorrRels array lists
	 */
	public void addIMiDetailsInArrayLists() {
		int numIMi=getNumIMi();
		
		imiTypes.add(gcimEditIMiControlPanel.getSelectedIMT());
		imiMapAttenRels.add(gcimEditIMiControlPanel.getSelectedIMRMap());
		imijMapCorrRels.add(gcimEditIMiControlPanel.getSelectedIMCorrRelMap());		
		//set the off-diagonal ImikjCorrRel terms in the array list
		//Get the number of IMik|j CorrRels that SHOULD be in the HashMap
		int numIMikCorrRels = (numIMi)*(numIMi-1)/2 - (numIMi-1)*(numIMi-2)/2;
		ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> IMikCorrRels = null;
		if (numIMikCorrRels>0) {
			IMikCorrRels = gcimEditIMiControlPanel.getSelectedIMikjCorrRelMap();
		}
		for (int m=0; m<numIMikCorrRels; m++) {
//			int indexIMikCorrRel = (numIMi-1)*(numIMi-2)/2+1+m;
			imikjMapCorrRels.add(IMikCorrRels.get(m));
		}
	}
	
	/** 
	 * This method removes the ith value of the ImiTypes, ImiMapAttenRels, and ImiMapCorrRels
	 * array lists
	 */
	public void removeIMiDetailsInArrayLists(int i) {
		int numIMi=getNumIMi();
		
		imiTypes.remove(i);
		imiMapAttenRels.remove(i);
		imijMapCorrRels.remove(i);
		//Remove the off-diagonal correlation terms from imikjMapCorrRels
		//Need to move from largest to smallest to prevent deleting incorrect ones as indexes change
		//as values are deleted 
		for (int m=0; m<i; m++) {
			int indexi=(numIMi-1)-m;
			int indexj = i;
			int index_imikjlist = (indexi)*(indexi-1)/2+indexj;
			imikjMapCorrRels.remove(index_imikjlist);
		}
		for (int m=0; m<i; m++) {
			int indexi = i;
			int indexj=(i-1)-m;
			int index_imikjlist = (indexi)*(indexi-1)/2+indexj;
			imikjMapCorrRels.remove(index_imikjlist);
		}
	}
	
	/** 
	 * This method updates the ith value of the ImiTypes, ImiMapAttenRels, and ImiMapCorrRels
	 * array lists with the current details in the EditGcimControlPanel 
	 */
	public void updateIMiDetailsInArrayLists(int i) {
		int sizeIMi = getNumIMi();
		if (D) System.out.println("UpdatingIMiDetails (index "+ i + ", sizeIMi = " + sizeIMi + ")");
//		StringConstraint stringConst = (StringConstraint) gcimImisParameter.getConstraint();
		if (i > imiTypes.size()-1) {
			//The index is greater than the the size of the array -> means need to add
			if (D) System.out.println("Adding new details");
			
			addIMiDetailsInArrayLists();
		}
		else {
			if (D) System.out.println("setting new details");
			imiTypes.set(i, gcimEditIMiControlPanel.getSelectedIMT());
			imiMapAttenRels.set(i, gcimEditIMiControlPanel.getSelectedIMRMap());
			if (D) System.out.println("Set IMR (" + gcimEditIMiControlPanel.getSelectedIMRMap() + ") in imiMapAttenRels");
			imijMapCorrRels.set(i, gcimEditIMiControlPanel.getSelectedIMCorrRelMap());
			//update the off-diagonal ImikjCorrRel terms in the array list
			//Get the number of IMik|j CorrRels that SHOULD be in the HashMap
			int numIMikCorrRels = (i+1)*(i+1-1)/2 - (i)*(i-1)/2;
			ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> IMikCorrRels = null;
			if (numIMikCorrRels>0) {
				IMikCorrRels = gcimEditIMiControlPanel.getSelectedIMikjCorrRelMap();
			}
			for (int m=0; m<numIMikCorrRels; m++) {
				int indexIMikCorrRel = (i)*(i-1)/2+m;
				imikjMapCorrRels.set(indexIMikCorrRel, IMikCorrRels.get(m));
			}
			
		}

	}
	
	/**
	 * This method returns the imiType from the imiTypes array list for a given index
	 */
	public String getImiType(int index) {
		return imiTypes.get(index);
	}
	
	/**
	 * This method returns the imi Parameter by getting the IMi from the imiMapAttenRels array list
	 *  for a given index
	 */
	public Parameter<Double> getImiParam(int index) {
		if (D) {
			System.out.println("Current IMR is " + TRTUtils.getFirstIMR(imiMapAttenRels.get(index)));
			System.out.println("Current IMi is " + TRTUtils.getFirstIMR(imiMapAttenRels.get(index)).getIntensityMeasure());
		}
		return (Parameter<Double>) TRTUtils.getFirstIMR(imiMapAttenRels.get(index)).getIntensityMeasure();
	}
	
	/**
	 * This method returns the imiAttenRel from the imiMapAttenRels array list for a given index
	 */
	public Map<TectonicRegionType, ScalarIMR> getImiAttenRel(int index) {
		if (D)
			System.out.println("Getting index " + index + " of IMiAttenRelMap which has value: " +imiMapAttenRels.get(index));
		return imiMapAttenRels.get(index);
	}
	
	/**
	 * This method returns the imijCorrRel from the imijMapCorrRels array list for a given index
	 */
	public Map<TectonicRegionType, ImCorrelationRelationship> getImijCorrRel(int index) {
		return imijMapCorrRels.get(index);
	}
	
	/**
	 * This method returns the imikjCorrRel from the imikjMapCorrRels array list for a given index
	 */
	public Map<TectonicRegionType, ImCorrelationRelationship> getImikjCorrRel(int index) {
		return imikjMapCorrRels.get(index);
	}
	
	/** 
	 * This method gets the included tectonic region type, which is needed by the GcimEditIMi panel
	 */
	public ArrayList<TectonicRegionType> getIncludedTectonicRegionTypes() {
		return parent.getIncludedTectonicRegionTypes();
	}
	
	/**
	 * This gets the parent site params which are defined in the hazard curve calculator
	 */
	public void getParentSite() {
		this.parentSite = parent.getSiteGuiBeanInstance().getSite();
	}
	
	/**
	 * This method gets the site object which is defined in the Site GUI
	 */
	public Site getSite() {
		return parentSite;
	}
	
	/**
	 * This method gets the GCIM site object
	 */
	public Site getGcimSite() {
		return gcimSite;
	}
	
	/**
	 * This method initializes the GCIM site object.  This object first takes all of those parameters
	 *  from the parent site (which cannot be edited in the GCIM GUI's and then adds additional 
	 *  parameters if required by the particular IMi's for which GCIM distributions are required
	 */
	public void initGcimSite() {
		this.gcimSite = (Site)parentSite.clone();
		
	}
	
	/**
	 * This method updates the GCIM site object. 
	 */
	public void updateGcimSite() {
		//first make a copy of the old gcimSite
		Site oldGcimSite = (Site)this.gcimSite.clone();
		//Now set the new gcimSite as the parent site
		this.gcimSite = (Site)parentSite.clone();
		//Get list iterators of the site parameters
		ListIterator<String> oldGcimSiteParamIt, gcimSiteParamIt;
		oldGcimSiteParamIt = oldGcimSite.getParameterNamesIterator();
		//Now loop over the oldGcimSite parameters and if they dont exist in the new gcimSite add them
		while (oldGcimSiteParamIt.hasNext()) {
			boolean oldGcimSiteParamInNewGcimSite=false;
			//Get the oldGcimSite parameter
			String oldGcimSiteParamName = oldGcimSiteParamIt.next().toString();
			//Does the new gcimSite contain this parameter?
			gcimSiteParamIt = gcimSite.getParameterNamesIterator();
			while (gcimSiteParamIt.hasNext()&!oldGcimSiteParamInNewGcimSite) {
				String gcimSiteParamName = gcimSiteParamIt.next().toString();
				if (oldGcimSiteParamName==gcimSiteParamName) {
					oldGcimSiteParamInNewGcimSite = true;
				}
			}
			//If no then add it
			if (!oldGcimSiteParamInNewGcimSite) {
				this.gcimSite.addParameter(oldGcimSite.getParameter(oldGcimSiteParamName));
			}
		}
	}
	
	/**
	 * This updates the parameters of the GCIM site object
	 */
	public void updateGcimSite(Site gcimSite) {
		this.gcimSite = gcimSite;
	}
	
	/**
	 * This method gets the IMjName from the main hazard calcs, used to determine which other IMj are
	 * allowable
	 */
	public Parameter<Double> getParentIMj() {
		this.parentIMj = parent.getIMTGuiBeanInstance().getSelectedIM();
		return parentIMj;
	}
	
	/**
	 * This method checks the IMjName from the main hazard calcs, to see if it has changed
	 */
	public void checkParentIMj() {
		//Get the old and new IMj's
		Parameter<Double> oldParentIMj = this.parentIMj;
		Parameter<Double> newParentIMj = parent.getIMTGuiBeanInstance().getSelectedIM();
		//Now compare
		boolean oldNewIMjSame = true;
		
		if (!oldParentIMj.getName().equalsIgnoreCase(newParentIMj.getName())) {
			oldNewIMjSame = false;
		} else {
			//Names are the same now check dependent parameters
			Iterator<Parameter<?>> oldParentIMjParamsIt = oldParentIMj.getIndependentParameterList().iterator();
			//Loop over the oldParentIMj params
			while (oldParentIMjParamsIt.hasNext()) {
				boolean newOldParentIMjContainParam = false;
				String oldParentIMjParamName = oldParentIMjParamsIt.next().toString();
				//Now loop over the newParentIMj params
				Iterator<Parameter<?>> newParentIMjParamsIt = newParentIMj.getIndependentParameterList().iterator();
				while (newParentIMjParamsIt.hasNext()) {
					String newParentIMjParamName = newParentIMjParamsIt.next().toString();
					//Are the parameter names the same?
					if (oldParentIMjParamName.equalsIgnoreCase(newParentIMjParamName)) {
						//Check the value
						if (newParentIMjParamsIt.next().getValue().equals(oldParentIMjParamsIt.next().getValue())) {
							newOldParentIMjContainParam = true;
							break;
						}
					}
						
				}
				
				if (!newOldParentIMjContainParam) {
					oldNewIMjSame = false;
					break;
				}
					
			}
		}
		
		//If different then reset all arrays etc
		if (!oldNewIMjSame)
			resetGcimControlPanelArrays();
		
		this.parentIMj = newParentIMj;
	}
	
	/**
	 * This method resets the arrays with the stored GCIM information (due to a change which makes this info invalid)
	 */
	private void resetGcimControlPanelArrays() {
		imiTypes = new ArrayList<String>();
		imiMapAttenRels = new ArrayList<Map<TectonicRegionType, ScalarIMR>>();
		imijMapCorrRels = new ArrayList<Map<TectonicRegionType, ImCorrelationRelationship>>();
		imikjMapCorrRels = new ArrayList<Map<TectonicRegionType, ImCorrelationRelationship>>();
		
		ArrayList<String> gcimImisList = new ArrayList<String>();
		gcimImisList.add(IMI_LIST_DEFAULT);
		gcimImisParameter = new StringParameter(IMI_LIST_NAME,gcimImisList,gcimImisList.get(0));
		gcimImisParameter.getEditor().setParameter(gcimImisParameter); // little hack to make sure the GUI updates

	}
	
	/**
	 * This method determines if IMj is supported for GCIM distributions
	 */
	public boolean isParentIMjGcimSupported() {
		AttenuationRelationshipsInstance imrInstances = new AttenuationRelationshipsInstance();
		ArrayList<ScalarIMR> imrs = imrInstances.createIMRClassInstance(null);
		
		ImCorrelationRelationshipsInstance imCorrRelInstances = new ImCorrelationRelationshipsInstance();
		ArrayList<ImCorrelationRelationship> imCorrRels = imCorrRelInstances.createImCorrRelClassInstance(null);

//		ParameterList supportedImjParamList = new ParameterList();
		//Loop over all of the ImCorrRels
		for (ImCorrelationRelationship imCorrRel : imCorrRels) {
			//For each IMCorrRel loop over the supported IMjs
			ArrayList<Parameter<?>> imjImCorrRelParamList =imCorrRel.getSupportedIntensityMeasuresjList();
			for (int i = 0; i<imjImCorrRelParamList.size(); i++) {
				Parameter<?> imjImCorrRelParam = imjImCorrRelParamList.get(i);
				//Check if the imjParam is the imjName
				if (imjImCorrRelParam.getName()==parentIMj.getName()) {
					//Now check if any IMRs support the IMj
					//Loop over all of the ImCorrRels
					for (ScalarIMR imr : imrs) {
						ParameterList imjImrParamList = imr.getSupportedIntensityMeasures();
						for (Parameter<?> imjImrParam : imjImrParamList) {
						//for (int j = 0; j<imjImrParamList.size(); j++) {
							//ParameterAPI<?> imjImrParam = imjImrParamList.getParameter(j);
							//Check if the imjParam is the imjName
							if (imjImCorrRelParam.getName()==parentIMj.getName()) {
								//Hence this imjParam is supported by this IMR also
//								supportedImjParamList.addParameter(parentIMj);
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * This methods intiates the GCIM panel inline with the main hazard calc details
	 */
	public void initWithParentDetails() {
		//update parent site params and IMj
		getParentSite();
		initGcimSite();
		getParentIMj();
		gcimSupportedIMj = isParentIMjGcimSupported(); 
		if (gcimSupportedIMj && isGUIInitialized)
			setParamsVisible((String)gcimParameter.getValue()); 
	}
	
	/**
	 * This methods updates the GCIM panel inline with the main hazard calc details
	 */
	public void updateWithParentDetails() {
		//update parent site params and IMj
		getParentSite();
		updateGcimSite();
		checkParentIMj();
		gcimSupportedIMj = isParentIMjGcimSupported(); 
		if (gcimSupportedIMj && isGUIInitialized)
			setParamsVisible((String)gcimParameter.getValue()); 
	}
	
	/**
	 * This method ensures that the IMi list updates on the GUI when changes are made to the EditIMiGUI
	 */
	public void updateIMiListGuiDisplay() {
		gcimImisParameter.getEditor().setParameter(gcimImisParameter); // little hack to make sure the GUI updates
		String blank = "blank";
		setParamsVisible(blank);
	}
	
	
	

}
