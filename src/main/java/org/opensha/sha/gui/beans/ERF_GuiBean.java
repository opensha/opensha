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

package org.opensha.sha.gui.beans;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.editor.AbstractParameterEditorOld;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeFailEvent;
import org.opensha.commons.param.event.ParameterChangeFailListener;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.bugReports.BugReport;
import org.opensha.commons.util.bugReports.SimpleBugMessagePanel;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.param.MagFreqDistParameter;
import org.opensha.sha.param.SimpleFaultParameter;
import org.opensha.sha.param.editor.MagFreqDistParameterEditor;
import org.opensha.sha.param.editor.SimpleFaultParameterEditor;

import com.google.common.base.Preconditions;

/**
 * <p>Title: ERF_GuiBean </p>
 * <p>Description: It displays ERFs and parameters supported by them</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author : Ned Field, Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public class ERF_GuiBean extends JPanel implements ParameterChangeFailListener,
ParameterChangeListener{

	private final static String C = "ERF_GuiBean";

	//this vector saves the names of all the supported Eqk Rup Forecasts
	protected ArrayList<String> erfNamesVector = new ArrayList<String>();
	//this vector holds the references to all included ERFs
	private List<ERF_Ref> erfRefs;

	// ERF Editor stuff
	public final static String ERF_PARAM_NAME = "Eqk Rup Forecast";
	// these are to store the list of independ params for chosen ERF
	public final static String ERF_EDITOR_TITLE =  "Set Forecast";
	StringParameter erfSelectionParam;
	// boolean for telling whether to show a progress bar
	boolean showProgressBar = true;

	//instance of the selected ERF
	BaseERF eqkRupForecast = null;
	//instance of progress bar to show the progress of updation of forecast
	CalcProgressBar progress= null;


	//parameter List to hold the selected ERF parameters
	private ParameterList parameterList;
	private ParameterListEditor listEditor;

	//TimeSpanGui Bean
	private TimeSpanGuiBean timeSpanGuiBean;
	//private JScrollPane erfScrollPane;
	private JPanel erfAndTimespanPanel;

	//checks to see if this a new ERF instance has been given by application to this Gui Bean.
	private boolean isNewERF_Instance;

	private HashMap<ERF_Ref, BaseERF> erfInstanceMap = new HashMap<ERF_Ref, BaseERF>();
	
	protected static List<ERF_Ref> asList(Set<ERF_Ref> erfRefSet) {
		List<ERF_Ref> list = new ArrayList<ERF_Ref>();
		for (ERF_Ref erf : erfRefSet) {
			list.add(erf);
		}
		// sort by name, dev status
//		Collections.sort(list, new ERF_RefComparator());
		return list;
	}
	private static class ERF_RefComparator implements Comparator<ERF_Ref> {

		@Override
		public int compare(ERF_Ref o1, ERF_Ref o2) {
			int priorityComp = new Integer(o1.status().priority()).compareTo(o2.status().priority());
			if (priorityComp != 0)
				return priorityComp;
			return o1.toString().compareTo(o2.toString());
		}
		
	}
	
	public ERF_GuiBean(ERF_Ref... erfRefs) throws InvocationTargetException {
		this(Arrays.asList(erfRefs));
	}
	
	public ERF_GuiBean(Set<ERF_Ref> erfRefs) throws InvocationTargetException {
		this(asList(erfRefs));
	}

	/**
	 * Constructor : It accepts the classNames of the ERFs to be shown in the editor
	 * @param erfClassNames
	 */
	public ERF_GuiBean(List<ERF_Ref> erfRefs) throws InvocationTargetException{
		try {
			jbInit();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		// save the class names of ERFs to be shown\
		this.erfRefs = erfRefs;

		// create the instance of ERFs
		init_erf_IndParamListAndEditor();
		// forecast 1  is selected initially
		setParamsInForecast();
	}

	private BaseERF getERFInstance(ERF_Ref erfRef) {
		if (!erfInstanceMap.containsKey(erfRef)) {
			erfInstanceMap.put(erfRef, erfRef.instance());
		}
		return erfInstanceMap.get(erfRef);
	}

	/**
	 * init erf_IndParamList. List of all available forecasts at this time
	 */
	protected void init_erf_IndParamListAndEditor() throws InvocationTargetException{
		Preconditions.checkNotNull(erfRefs, "ERF list cannot be null!");
		Preconditions.checkArgument(!erfRefs.isEmpty(), "ERF list cannot be empty!");

		this.parameterList = new ParameterList();

		for (ERF_Ref erfRef : erfRefs) {
			String name = erfRef.toString();
			Preconditions.checkState(!erfNamesVector.contains(name),
					"ERF list cannot contain 2 ERFs with the same name! Duplicate: "+name);
			erfNamesVector.add(name);
		}

		// first ERF class that is to be shown as the default ERF in the ERF Pick List
		ERF_Ref erf = erfRefs.get(0);
		// make the ERF objects to get their adjustable parameters
		eqkRupForecast = getERFInstance(erf);



		// make the forecast selection parameter
		erfSelectionParam = new StringParameter(ERF_PARAM_NAME,
				erfNamesVector, (String)erfNamesVector.get(0));
		erfSelectionParam.addParameterChangeListener(this);
		parameterList.addParameter(erfSelectionParam);
	}


	/**
	 * this function is called to add the paramters based on the forecast
	 * selected by the user. Based on the selected Forecast it also creates
	 * timespan and add that to the same panel window that shows the ERF parameters.
	 */
	private void setParamsInForecast() throws InvocationTargetException{

		Parameter chooseERF_Param = parameterList.getParameter(this.ERF_PARAM_NAME);
		parameterList = new ParameterList();
		parameterList.addParameter(chooseERF_Param);
		// get the selected forecast
		getSelectedERF_Instance();
		//getting the EqkRupForecast param List and its iterator
		ParameterList paramList = eqkRupForecast.getAdjustableParameterList();
		Iterator it = paramList.getParametersIterator();

		// make the parameters visible based on selected forecast
		while(it.hasNext()){
			Parameter param = (Parameter)it.next();
			//System.out.println("Param Name: "+param.getName());
			//if(param.getName().equals(EqkRupForecast.TIME_DEPENDENT_PARAM_NAME))
			param.addParameterChangeListener(this);
			param.addParameterChangeFailListener(this);

			parameterList.addParameter(param);
		}

		//remove the parameters if they already exists in the panel.
		if(listEditor !=null){
			erfAndTimespanPanel.remove(listEditor);
			listEditor = null;
		}



		//creating the new instance of ERF parameter list editors
		listEditor = new ParameterListEditor(parameterList);

		// show the ERF gui Bean in JPanel
		erfAndTimespanPanel.add(listEditor, BorderLayout.CENTER);
		//		erfAndTimespanPanel.add(listEditor, 
		//				new GridBagConstraints(
		//						0, 0, 1, 1, 1.0, 1.0,
		//						GridBagConstraints.CENTER, 
		//						GridBagConstraints.BOTH, 
		//						new Insets(4,4,4,4),
		//						0, 0));

		// now make the editor based on the paramter list
		listEditor.setTitle(ERF_EDITOR_TITLE);

		// get the panel for increasing the font and border
		// this is hard coding for increasing the IMR font
		// the colors used here are from ParameterEditor

		ParameterEditor<?> edit = listEditor.getParameterEditor(ERF_PARAM_NAME);
		TitledBorder titledBorder1 = new TitledBorder(
				BorderFactory.createLineBorder(
						new Color( 80, 80, 140 ),3),ERF_PARAM_NAME);
		titledBorder1.setTitleColor(new Color( 80, 80, 140 ));
		Font DEFAULT_LABEL_FONT = new Font( "SansSerif", Font.BOLD, 13 );
		titledBorder1.setTitleFont(DEFAULT_LABEL_FONT);
		Border border1 = BorderFactory.createCompoundBorder(titledBorder1,BorderFactory.createEmptyBorder(0,0,3,0));
		edit.setEditorBorder(border1);
		createTimeSpanPanel();
		this.validate();
		this.repaint();
	}

	//adds the TimeSpan panel to the Gui depending on Timespan from EqkRupForecast.
	private void createTimeSpanPanel(){
		if (timeSpanGuiBean == null) {
			// create the TimeSpan Gui Bean object
			timeSpanGuiBean = new TimeSpanGuiBean(eqkRupForecast.getTimeSpan());
			timeSpanGuiBean.setOpaque(false);
			timeSpanGuiBean.setBorder(
					BorderFactory.createEmptyBorder(8, 0, 0, 0));
		} else {
			erfAndTimespanPanel.remove(timeSpanGuiBean);
		}
		//adding the Timespan Gui panel to the ERF Gui Bean
		timeSpanGuiBean.setTimeSpan(eqkRupForecast.getTimeSpan());

		erfAndTimespanPanel.add(timeSpanGuiBean, BorderLayout.PAGE_END);
		//		erfAndTimespanPanel.add(timeSpanGuiBean, TODO clean
		//				new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0,
		//						GridBagConstraints.CENTER,
		//						GridBagConstraints.BOTH,
		//						new Insets(0,0,0,0), 0, 0));
	}



	/**
	 * gets the lists of all the parameters that exists in the ERF parameter Editor
	 * then checks if the magFreqDistParameter exists inside it , if so then returns the MagEditor
	 * else return null.  The only reason this is public is because at least one control panel
	 * (for the PEER test cases) needs access.
	 * @return MagFreDistParameterEditor
	 */
	public MagFreqDistParameterEditor getMagDistEditor(){

		ListIterator lit = parameterList.getParametersIterator();
		while(lit.hasNext()){
			Parameter param=(Parameter)lit.next();
			if(param instanceof MagFreqDistParameter){
				MagFreqDistParameterEditor magDistEditor=((MagFreqDistParameterEditor)listEditor.getParameterEditor(param.getName()));
				return magDistEditor;
			}
		}
		return null;
	}


	/**
	 * gets the lists of all the parameters that exists in the ERF parameter Editor
	 * then checks if the simpleFaultParameter exists inside it , if so then returns the
	 * SimpleFaultParameterEditor else return null.  The only reason this is public is
	 * because at least one control panel (for the PEER test cases) needs access.
	 * @return SimpleFaultParameterEditor
	 */
	public SimpleFaultParameterEditor getSimpleFaultParamEditor(){

		ListIterator lit = parameterList.getParametersIterator();
		while(lit.hasNext()){
			Parameter param=(Parameter)lit.next();
			if(param instanceof SimpleFaultParameter){
				SimpleFaultParameterEditor simpleFaultEditor = ((SimpleFaultParameterEditor)listEditor.getParameterEditor(param.getName()));
				return simpleFaultEditor;
			}
		}
		return null;
	}


	/**
	 * returns the name of selected ERF
	 * @return
	 */
	public String getSelectedERF_Name() {
		return (String)parameterList.getValue(this.ERF_PARAM_NAME);
	}

	/**
	 * get the selected ERF instance
	 * It returns the forecast without updating the forecast
	 * @return
	 */
	public BaseERF getSelectedERF_Instance() throws InvocationTargetException{
		//updating the MagDist Editor
		updateMagDistParam();
		//update the fault Parameter
		updateFaultParam();
		return eqkRupForecast;
	}


	/**
	 * get the selected ERF instance.
	 * It returns the ERF after updating its forecast
	 * @return
	 */
	public BaseERF getSelectedERF() throws InvocationTargetException{
		getSelectedERF_Instance();
		if(this.showProgressBar) {
			// also show the progress bar while the forecast is being updated
			progress = new CalcProgressBar(this, null,"Updating forecast\u2026");
			//progress.displayProgressBar();
		}
		// update the forecast
		eqkRupForecast.updateForecast();
		if (showProgressBar) {
			progress.dispose();
			progress = null;
		}
		return eqkRupForecast;

	}

	/**
	 * It sees whether selected ERF is a Epistemic list.
	 * @return : true if selected ERF is a epistemic list, else false
	 */
	public boolean isEpistemicList() {
		try{
			BaseERF eqkRupForecast = getSelectedERF_Instance();
			if(eqkRupForecast instanceof AbstractEpistemicListERF)
				return true;
		}catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}


	/**checks if the magFreqDistParameter exists inside it ,
	 * if so then gets its Editor and calls the method to update the magDistParams.
	 */
	protected void updateMagDistParam() {
		MagFreqDistParameterEditor magEditor=getMagDistEditor();
		if(magEditor!=null) ((MagFreqDistParameter)magEditor.getParameter()).setMagDist();
	}

	/**checks if the Fault Parameter Editor exists inside it ,
	 * if so then gets its Editor and calls the method to update the faultParams.
	 */
	protected void updateFaultParam() {
		SimpleFaultParameterEditor faultEditor = getSimpleFaultParamEditor();
		if(faultEditor!=null)  faultEditor.getParameterEditorPanel().setEvenlyGriddedSurfaceFromParams();
	}



	/**
	 *  Shown when a Constraint error is thrown on a ParameterEditor
	 *
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
				this, b.toString(),
				"Cannot Change Value", JOptionPane.INFORMATION_MESSAGE
		);


	}
	
	private void showERFInstantiationError(Throwable t, String erfName) {
		t.printStackTrace();
		ApplicationVersion version;
		try {
			version = ApplicationVersion.loadBuildVersion();
		} catch (Exception e1) {
			version = null;
		}
		BugReport bug = new BugReport(t, "Problem occured in ERF_GuiBean" +
				" when "+erfName+" is selected.", erfName, version, this);
		String title = "Error instantiating "+erfName;
		
		String message = "An error occured when instantiating "+erfName+
					"\n\nIt will be removed from the list. If you wish, you can submit" +
					" a bug report by clicking below. To receive updates on the bug report, it" +
					" is important that you leave your e-mail address in the 'reporter' field.";
		
		new SimpleBugMessagePanel(bug, message).showAsDialog(this, title);
	}

	/**
	 *  This is the main function of this interface. Any time a control
	 *  paramater or independent paramater is changed by the user in a GUI this
	 *  function is called, and a paramater change event is passed in. This
	 *  function then determines what to do with the information ie. show some
	 *  paramaters, set some as invisible, basically control the paramater
	 *  lists.
	 *
	 * @param  event
	 */
	public void parameterChange( ParameterChangeEvent event ) {


		String name1 = event.getParameterName();

		// if ERF selected by the user  changes
		if( name1.equals(ERF_PARAM_NAME) && !isNewERF_Instance){
			String value = event.getNewValue().toString();
			int size = this.erfNamesVector.size();
			try{
				for(int i=0;i<size;++i){
					if(value.equalsIgnoreCase((String)erfNamesVector.get(i))) {
						try {
							eqkRupForecast = getERFInstance(erfRefs.get(i));
						} catch (Exception e) {
							showERFInstantiationError(e, value);
							ArrayList<ERF_Ref> removed = new ArrayList<ERF_Ref>();
							removed.add(erfRefs.get(i));
							removeERFs_FromList(removed);
							erfSelectionParam.setValue((String)event.getOldValue());
							erfSelectionParam.getEditor().refreshParamEditor();
							return;
						}
						break;
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}

		refreshGUI();
	}
	
	public void refreshGUI() {
		try {
			setParamsInForecast();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		createTimeSpanPanel();

		this.validate();
		this.repaint();
	}

	/**
	 * This allows tuning on or off the showing of a progress bar
	 * @param show - set as true to show it, or false to not show it
	 */
	public void showProgressBar(boolean show) {
		this.showProgressBar=show;
	}

	/**
	 * Adds the ERF's to the existing ERF List in the gui bean to be displayed in the gui.
	 * This function allows user to add the more ERF's names to the existing list from the application.
	 * This function allows user with the flexibility that he does not always have to specify the erfNames
	 * at time of instantiating this ERF gui bean.
	 * @param erfList
	 * @throws InvocationTargetException
	 */
	public void addERFs_ToList(ArrayList<ERF_Ref> newRefs) throws InvocationTargetException{

		int size = newRefs.size();
		for (ERF_Ref erfRef : newRefs)
			if(!erfRefs.contains(erfRef))
				newRefs.add(erfRef);
		// create the instance of ERFs
		erfNamesVector.clear();
		init_erf_IndParamListAndEditor();
		setParamsInForecast();
	}

	/**
	 * This function closes the progress bar window which shows forcast updation,
	 * if user has choosen to see the progress of updation, in the first place.
	 */
	public void closeProgressBar(){
		if(showProgressBar && progress!=null){
			progress.dispose();
			progress = null;
		}
	}

	/**
	 * Removes the ERF's from the existing ERF List in the gui bean to be displayed in the gui.
	 * This function allows user to remove ERF's names from the existing list from the application.
	 * This function allows user with the flexibility that he can always remove the erfNames
	 * later after instantiating this ERF gui bean.
	 * @param erfList
	 * @throws InvocationTargetException
	 */
	public void removeERFs_FromList(ArrayList<ERF_Ref> removed) throws InvocationTargetException{

		int size = removed.size();
		for (ERF_Ref erf : removed)
			if (erfRefs.contains(erf))
				erfRefs.remove(erf);
		// create the instance of ERFs
		erfNamesVector.clear();
		init_erf_IndParamListAndEditor();
		setParamsInForecast();
	}

	/**
	 *
	 * @return the List of ERF parameters
	 */
	public ParameterList getERFParameterList(){
		return parameterList;
	}

	/**
	 *
	 * @return the parameter list editor for ERF parameters
	 */
	public ParameterListEditor getERFParameterListEditor(){
		return listEditor;
	}


	/**
	 * Sets the EqkRupForecast in the ERF_GuiBean
	 */
	public void setERF(BaseERF eqkRupForecast){
		this.eqkRupForecast = eqkRupForecast;
		isNewERF_Instance = true;
		String erfName = eqkRupForecast.getName();
		int size = erfNamesVector.size();
		for(int i=0;i<size;++i){
			if(erfName.equalsIgnoreCase( (String) erfNamesVector.get(i))) {
				try{
					listEditor.getParameterEditor(ERF_PARAM_NAME).setValue(erfName);
					setParamsInForecast();
				}catch(Exception e){
					e.printStackTrace();
				}
				isNewERF_Instance = false;
				break;
			}
		}
	}


	/**
	 *
	 * @return the selected ERF timespan gui bean object
	 */
	public TimeSpanGuiBean getSelectedERFTimespanGuiBean(){
		return timeSpanGuiBean;
	}

	/**
	 *
	 * @param paramName
	 * @return the parameter with the ParamName
	 */
	public Parameter getParameter(String paramName){
		if(this.parameterList.containsParameter(paramName)){
			if(listEditor.getParameterEditor(paramName).isVisible()){
				return parameterList.getParameter(paramName);
			}
		}
		else{
			timeSpanGuiBean.getParameterList().getParameter(paramName);
		}
		return null;
	}


	private void jbInit() throws Exception {

		//setLayout(new GridBagLayout());
		setLayout(new BorderLayout());
		setOpaque(false);
		//erfAndTimespanPanel.setLayout(new BorderLayout());
		//		add(erfScrollPane,  new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
		//				,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 6, 4, 5),0, 0));
		erfAndTimespanPanel = new JPanel(new BorderLayout());
		erfAndTimespanPanel.setBorder(BorderFactory.createEmptyBorder(0,0,0,4));
		erfAndTimespanPanel.setOpaque(false);

		JScrollPane erfScrollPane = new JScrollPane(erfAndTimespanPanel);
		erfScrollPane.setBorder(null);
		erfScrollPane.setOpaque(false);
		erfScrollPane.getViewport().setOpaque(false);
		//erfScrollPane.setBackground(Color.orange);
		add(erfScrollPane,  BorderLayout.CENTER);

		//erfScrollPane.getViewport().add(erfAndTimespanPanel, null);
	}

}

