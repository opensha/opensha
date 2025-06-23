package org.opensha.sha.gui.beans;


import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.AbstractParameterEditorOld;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ConstrainedStringParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;


/**
 * <p>Title: Eqk Rupture Selector GuiBean</p>
 * <p>Description: This class will show ERF and its parameters. It will
 * also allow the user to select a particular rupture for scenario maps.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author : Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public class EqkRupSelectorGuiBean extends JPanel implements ParameterChangeListener
{


	/**
	 * Name of the class
	 */
	protected final static String C = "EqkRupSelectorGuiBean";
	// for debug purpose
	protected final static boolean D = false;

	//parameter to select earthquake rupture either from ERF or create your own rupture
	public final static String RUPTURE_SELECTOR_PARAM_NAME = "Select method of getting EqkRupture";
	private StringParameter ruptureSelectorParam;
	private final static String RUPTURE_SELECTOR_PARAM_INFO = "Toggles between methods to allow user "+
	"to allow defining their own rupture or select one from the existing ERF's ";
	public final static String RUPTURE_FROM_EXISTING_ERF = "Select Eqk Rupture from an ERF";
	public final static String CREATE_RUPTURE ="Custom Eqk Rupture";
	private ConstrainedStringParameterEditor ruptureSelectorParamEditor;
	private GridBagLayout gridBagLayout1 = new GridBagLayout();
	private GridBagLayout gridBagLayout2 = new GridBagLayout();
	//Gui elements
	private JScrollPane eqkRuptureParameterScrollPane = new JScrollPane();
	private JPanel rupturePanel = new JPanel();


	//defines he classes that implement we use in this GUI bean to get the
	private EqkRupSelectorGuiBeanAPI eqkRupturePanelFromERF = null;
	private EqkRupSelectorGuiBeanAPI eqkRupturePanelFromRupture = null;
	private EqkRupSelectorGuiBeanAPI eqkRupturePanel = null;

	//checks to see if user has choosen to create his own eqk rupture or getting
	//already existing one from the ERF list.
	private boolean isUserCreatedEqkRupture = false;

	//supported ERF classes
	private List<ERF_Ref> erfRefs;

	private BaseERF eqkRupForecast;

	public EqkRupSelectorGuiBean(BaseERF erf, ERF_Ref... erfRefs) throws InvocationTargetException {
		this(erf, Arrays.asList(erfRefs));
	}
	
	public EqkRupSelectorGuiBean(BaseERF erf, Set<ERF_Ref> erfRefSet) throws InvocationTargetException {
		this(erf, ERF_GuiBean.asList(erfRefSet));
	}

	public EqkRupSelectorGuiBean(BaseERF erf, List<ERF_Ref> erfRefs) throws InvocationTargetException {
		eqkRupturePanelFromERF = new EqkRuptureFromERFSelectorPanel(erf, erfRefs);
		eqkRupForecast = erf;
		this.erfRefs = erfRefs;
		eqkRupturePanel = eqkRupturePanelFromERF;
		try {
			jbInit();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		toggleRuptureSelectionMethods();
	}
	
	public EqkRupSelectorGuiBean(ERF_Ref... erfRefs) throws InvocationTargetException {
		this(Arrays.asList(erfRefs));
	}
	
	public EqkRupSelectorGuiBean(Set<ERF_Ref> erfRefSet) throws InvocationTargetException {
		this(ERF_GuiBean.asList(erfRefSet));
	}

	/**
	 * Constructor : It accepts the classNames of the ERFs to be shown in the editor
	 * @param erfClassNames
	 */
	public EqkRupSelectorGuiBean(List<ERF_Ref> erfRefs) throws InvocationTargetException {
		this.erfRefs = erfRefs;
		isUserCreatedEqkRupture = false;
		eqkRupturePanelFromERF = new EqkRuptureFromERFSelectorPanel(erfRefs);
		eqkRupturePanel = eqkRupturePanelFromERF;
		try {
			jbInit();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		toggleRuptureSelectionMethods();
	}

	public EqkRupSelectorGuiBean() throws InvocationTargetException{

		eqkRupturePanelFromRupture = new EqkRuptureCreationPanel();
		eqkRupturePanel = eqkRupturePanelFromRupture;
		isUserCreatedEqkRupture = true;
		try {
			jbInit();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		rupturePanel.add( (JPanel) eqkRupturePanel,
				new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0
						, GridBagConstraints.CENTER,
						GridBagConstraints.BOTH,
						new Insets(4, 4, 4, 4), 0, 0));

		this.validate();
		this.repaint();

	}


	private void jbInit() throws Exception {
		ArrayList ruptureSelectionMethodList = new ArrayList();
		ruptureSelectionMethodList.add(RUPTURE_FROM_EXISTING_ERF);
		ruptureSelectionMethodList.add(CREATE_RUPTURE);
		ruptureSelectorParam = new StringParameter(RUPTURE_SELECTOR_PARAM_NAME,ruptureSelectionMethodList,
				(String)ruptureSelectionMethodList.get(0));
		ruptureSelectorParam.setInfo(RUPTURE_SELECTOR_PARAM_INFO);
		ruptureSelectorParam.addParameterChangeListener(this);
		ruptureSelectorParamEditor = new ConstrainedStringParameterEditor(ruptureSelectorParam);
		this.setLayout(gridBagLayout1);
		rupturePanel.setLayout(gridBagLayout2);
		this.setMinimumSize(new Dimension(0, 0));
		rupturePanel.setMinimumSize(new Dimension(0, 0));
		this.add(eqkRuptureParameterScrollPane,  new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
				,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 3, 5, 5), 0, 0));
		eqkRuptureParameterScrollPane.getViewport().add(rupturePanel, null);
		rupturePanel.add(ruptureSelectorParamEditor,  new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
				,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0,0));
		eqkRuptureParameterScrollPane.validate();
		eqkRuptureParameterScrollPane.repaint();
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

		String S = C + ": parameterChange(): ";
		if ( D )
			System.out.println( "\n" + S + "starting: " );

		String name1 = event.getParameterName();

		// if ERF selected by the user  changes
		if( name1.equals(RUPTURE_SELECTOR_PARAM_NAME) ){
			String paramVal  = (String)event.getNewValue();
			if(paramVal.equals(RUPTURE_FROM_EXISTING_ERF))
				isUserCreatedEqkRupture = false;
			else if(paramVal.equals(CREATE_RUPTURE))
				isUserCreatedEqkRupture = true;
			//based on user selection toggles between allowing user to select rupture
			//from already existing ERF model
			try{
				toggleRuptureSelectionMethods();
			}catch(InvocationTargetException e){
				throw new RuntimeException(e.getMessage());
			}
		}

	}

	/**
	 * Sets the forecast model from the application inside
	 * this ERF_RupSelectorGuiBean to get the rupture.
	 * @param forecast EqkRupForecastAPI
	 */
	public void setEqkRupForecastModel(BaseERF forecast){
		this.eqkRupForecast = forecast;
		if(!isUserCreatedEqkRupture)
			((EqkRuptureFromERFSelectorPanel)eqkRupturePanel).setEqkRupForecast(forecast);
	}


	/**
	 * Toggles between the visible panel for selecting the rupture from existing ERF
	 * and
	 * allowing the user to create his own rupture.
	 */
	private void toggleRuptureSelectionMethods() throws InvocationTargetException {
		rupturePanel.remove( (JPanel) eqkRupturePanel);
		if (!isUserCreatedEqkRupture) {
			//if user has chosen to select eqk rupture from already existing ERF model
			if (eqkRupturePanelFromERF == null)
				if(eqkRupForecast == null)
					eqkRupturePanelFromERF = new EqkRuptureFromERFSelectorPanel(erfRefs);
				else
					eqkRupturePanelFromERF = new EqkRuptureFromERFSelectorPanel(
							eqkRupForecast, erfRefs);
			eqkRupturePanel = eqkRupturePanelFromERF;
		}
		else { //if user has chosen to create his own rupture.
			//if user has chosen to select eqk rupture from already existing ERF model
			if (eqkRupturePanelFromRupture == null)
				eqkRupturePanelFromRupture = new EqkRuptureCreationPanel();
			eqkRupturePanel = eqkRupturePanelFromRupture;
		}

		rupturePanel.add( (JPanel) eqkRupturePanel,
				new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0
						, GridBagConstraints.CENTER,
						GridBagConstraints.BOTH,
						new Insets(4, 4, 4, 4), 0, 0));

		this.validate();
		this.repaint();
	}

	/**
	 *
	 * @return the Hypocenter Location if selected else return null
	 */
	public Location getHypocenterLocation(){
		return eqkRupturePanel.getHypocenterLocation();
	}

	/**
	 *
	 * @return the EqkRupture Object
	 */
	public EqkRupture getRupture(){
		return eqkRupturePanel.getRupture();
	}

	/**
	 *  This method has been added to view the selected source in Geo3D project.
	 *  Source is only available from ERF. If user is making a custom rupture, source is not available.
	 * 
	 * @return ProbEqkSource object
	 */
	public ProbEqkSource getSource() {
		if(eqkRupturePanel instanceof EqkRuptureFromERFSelectorPanel) return ((EqkRuptureFromERFSelectorPanel)eqkRupturePanel).getSource();
		else return null;
	}


	/**
	 *
	 * @return the timespan Metadata for the selected Rupture.
	 * If no timespan exists for the rupture then it returns the Message:
	 * "No Timespan exists for the selected Rupture".
	 */
	public String getTimespanMetadataString(){
		return eqkRupturePanel.getTimespanMetadataString();
	}


	/**
	 *
	 * @return the Metadata String of parameters that constitute the making of this
	 * ERF_RupSelectorGUI  bean.
	 */
	public String getParameterListMetadataString(){
		return eqkRupturePanel.getParameterListMetadataString();
	}

	/**
	 *
	 * @param paramName
	 * @return the parameter from list of visible parameters in the rupture selector/creator GUI.
	 */
	public Parameter getParameter(String paramName){
		if(paramName.equals(this.RUPTURE_SELECTOR_PARAM_NAME))
			return ruptureSelectorParam;
		else{
			return eqkRupturePanel.getParameter(paramName);
		}
	}

	/**
	 *
	 * @param paramName
	 * @return the ParameterEditor associated with paramName
	 */
	public ParameterEditor getParameterEditor(String paramName){
		if(paramName.equals(RUPTURE_SELECTOR_PARAM_NAME))
			return this.ruptureSelectorParamEditor;
		else{
			return eqkRupturePanel.getParameterEditor(paramName);
		}
	}

	/**
	 *
	 * @return the instance to the Selected mode of Rupture calculator.
	 * If user has chosen to get the Eqk rupture from ERF model,then the
	 * returned  EqkRupSelectorGuiBeanAPI will the instance of EqkRuptureFromERFSelectorPanel.
	 * Else if the user to selected to create his own rupture then returned API will
	 * be the instance of EqkRuptureCreationPanel.
	 */
	public EqkRupSelectorGuiBeanAPI getEqkRuptureSelectorPanel(){
		return eqkRupturePanel;
	}

	/**
	 * Checks if custom rupture is selected
	 * @return boolean
	 */
	public boolean isCustomRuptureSelected(){
		return isUserCreatedEqkRupture;
	}

	/**
	 * Returns the instance of the EqkRupForecast from EqkRupFromERFSelector Panel
	 *
	 * @return ERF_API
	 */
	public ERF getSelectedEqkRupForecastModel(){
		return ((EqkRuptureFromERFSelectorPanel)eqkRupturePanel).getSelectedERF_Instance();
	}

	/**
	 *
	 * @return the visible parameters in the list
	 */
	public ParameterList getVisibleParameterList(){
		return eqkRupturePanel.getVisibleParameterList();
	}

	/**
	 *
	 * @return the parameterlist editor
	 */
	public ParameterListEditor getVisibleParameterListEditor(){
		return eqkRupturePanel.getParameterListEditor();
	}

}
