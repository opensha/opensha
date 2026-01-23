package org.opensha.commons.param.editor.impl;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.opensha.commons.gui.LabeledBoxPanel;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.ParameterEditor;

/**
 * <b>Title:</b> ParameterListEditor<p>
 *
 * <b>Description:</b> The main Parameter Editor Panel that takes in a
 * ParameterList, and using the ParameterEditorFactory to build all individual
 * Parameter Editors for each editor in the Parameter List. The list is
 * presented in a Scroll Pane so all parameters are accessible, no matter the
 * size of the containing application. <p>
 *
 * What is real special about this class is that it only knows about ParameterEditor
 * and ParameterAPI! In other words it knows nothing about subclasses. Since
 * subclasses all exhibit the same API new ones can be added to the framework
 * at any time and this class doesn't change at all. This class is
 * self configurable. <p>
 *
 * Note: This class obtains all it's GUI look and behavior from it's
 * parent, the LabeledBoxPanel. THese two classes were seperated to
 * partition the GUI logic from the Parameter List and Editors logic.
 * This class acts as the Controller, the ParameterList and is the model,
 * and the parameterEditors and LabeledBoxPanel parent class comprise the
 * GUI View. This is designed after the Model View Controller (MVC)
 * design pattern, a very common design in object-oriented programming. <p>
 *
 * Note: SWR: I highly recommend all Java programmers understand the MVC framework! <p>
 *
 * @author     Steven W. Rock
 * @created    April 17, 2002
 * @version    1.0
 */

public class ParameterListEditor extends LabeledBoxPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "ParameterListEditor";
	/** If true print out debug statements. */
	protected final static boolean D = false;


	/** The internal list of parameters that this editor will allow modification on. */
	protected ParameterList parameterList;


	/** List of all individual editors, one for each parameter in the parameter list */
	/** Both the parameterEditor and parameterName maintain the same ordering of the
	 *  parameters. parametersEditor store the editor for each parameter name stored
	 *  in the  parameterName variable at the same index.*/
	protected ArrayList<ParameterEditor> parameterEditors = new ArrayList<ParameterEditor>();
	protected ArrayList<String> parametersName = new ArrayList<String>();

	/** Calls super() to configure the GUI */
	public ParameterListEditor() {
		super();
		this.setLayout(new GridBagLayout());
	}

	/**
	 * Constructor for the ParameterListEditor object, calls
	 * super() to initialize the GUI. The model Parameter List
	 * is set, the search paths configured, then all Parameter
	 * Editors are initialized with the parameters, and added
	 * to the GUI in a scrolling list. <p>
	 */
	public ParameterListEditor(ParameterList paramList) {

		this();
		setParameterList(paramList);

	}

	/** Sets the parameterList. Simple javabean method */
	public void setParameterList( ParameterList paramList ) {
		parameterList = paramList;
		addParameters();
	}

	/** gets the parameterList. Simple javabean method */
	public ParameterList getParameterList() { return parameterList; }

	/**
	 *  Hides or shows one of the ParameterEditors in the ParameterList. setting
	 *  the boolean parameter to true shows the panel, setting it to false hides
	 *  the panel. Note, all editors are accesable by parameter name. <p>
	 *
	 * @param  parameterName  The parameter editor to toggle on or off.
	 * @param  visible      The boolean flag. If true editor is visible.
	 */
	public void setParameterVisible( String parameterName, boolean visible ) {

		parameterName = parameterList.getParameterName( parameterName );
		int index = getIndexOf(parameterName);
		if ( index != -1 ) {
			ParameterEditor editor = parameterEditors.get(index);
			editor.setVisible( visible );
		}

	}

	/**
	 * It enables/disables the paramaters in this editor according to whether user is allowed to
	 * fill in the values.
	 */
	public void setEnabled(boolean isEnabled) {
		for(int i=0; i<parameterEditors.size(); ++i)
			parameterEditors.get(i).getComponent().setEnabled(isEnabled);
	}


	/**
	 *
	 * @param paramName
	 * @return the index of the parameter Name in the ArrayList
	 */
	private int getIndexOf(String paramName){
		int size =  parametersName.size();
		for(int i=0;i<size;++i){
			if(((String)parametersName.get(i)).equals(paramName))
				return i;
		}
		return -1;
	}

	/**
	 * Returns ParameterList of all parameters that have their
	 * GUI editors currently visible.
	 */
	public ParameterList getVisibleParameters() {

		ParameterList visibles = new ParameterList();

		Iterator<ParameterEditor> it = parameterEditors.iterator();
		while ( it.hasNext() ) {

			ParameterEditor editor = it.next();
			if ( editor.isVisible() ) {
				Parameter param = ( Parameter ) editor.getParameter();
				visibles.addParameter( param );
			}
		}
		return visibles;
	}


	/**
	 * Returns cloned ParameterList of all parameters that have their
	 * GUI editors currently visible
	 */
	public ParameterList getVisibleParametersCloned(){
		return (ParameterList)getVisibleParameters().clone();
	}




	/**
	 *  Gets the parameterEditor attribute of the ParameterListEditor object
	 *
	 * @param  parameterName               The Parameter editor to look up.
	 * @return                             Returns the found ParameterEditor for the named parameter
	 * @exception  NoSuchElementException  Thrown if the named parameter doesn't exist.
	 */
	public ParameterEditor getParameterEditor( String parameterName ) throws NoSuchElementException {

		parameterName = parameterList.getParameterName( parameterName );
		int index = getIndexOf(parameterName);
		if ( index != -1 ) {
			ParameterEditor editor = parameterEditors.get(index);
			return editor;
		}
		else
			throw new NoSuchElementException( "No ParameterEditor exist named " + parameterName );

	}


	/**
	 * Proxy to each parameter editor. THe lsit of editors is iterated over, calling the
	 * same function. <p>
	 *
	 * Updates the paramter editor with the parameter value. Used when
	 * the parameter is set for the first time, or changed by a background
	 * process independently of the GUI. This could occur with a ParameterChangeFail
	 * event.
	 */
	public void refreshParamEditor() {
		Iterator<ParameterEditor> it = parameterEditors.iterator();
		while ( it.hasNext() ) {
			ParameterEditor editor = it.next();
			editor.refreshParamEditor();
		}
	}

	/**
	 * Searches for the named parameter editor, then replaces the parameter
	 * it is currently editing.
	 * @param parameterName : Name of the parameter that is being removed
	 * @param param : New parameter that is replacing the old parameter
	 */
	public void replaceParameterForEditor( String parameterName, Parameter param ) {

		parameterName = this.parameterList.getParameterName( parameterName );
		int index = getIndexOf(parameterName);
		if ( index != -1 ) {
			ParameterEditor editor = parameterEditors.get(index);
			editor.setParameter( param );
			parameterList.removeParameter( parameterName );
			parameterList.addParameter( param );
		}

	}

	private static GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1,
		1.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
		new Insets(4, 4, 4, 4), 0, 0);

	/**
	 * VERY IMPORTANT setup function. This is where all the parameter editors
	 * are dynamcally created based by parameter getType() function. It uses
	 * the ParameterEditorFactory to create the editors. THe search path is
	 * set for the factory, each ParameterEditor is created, and then added
	 * as a JPanel ( base class of all Editors ) to this list GUI scrolling list.
	 */
	protected void addParameters() {
		editorPanel.removeAll();
		
		if ( parameterList == null )
			return;
		
		parametersName.clear();
		parameterEditors.clear();
		
		int counter = 0;
		
		for (Parameter<?> param : parameterList) {
			ParameterEditor paramEdit = param.getEditor();
			//paramEdit.setVisible(true);
			if (paramEdit == null)
				throw new RuntimeException(
					"No parameter editor exists for type: " + param.getType() +
						" (" + param.getClass().getName() + ")");
			parametersName.add(param.getName());
			parameterEditors.add(paramEdit );
			gbc.gridy = counter;
			editorPanel.add(paramEdit.getComponent(), gbc);
			counter++;
		}
	}


}
