package org.opensha.commons.param.editor.impl;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;

import org.opensha.commons.data.ValueWeight;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleValueWeightConstraint;
import org.opensha.commons.param.editor.AbstractParameterEditorOld;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.DoubleParameter;

/**
 *<b>Title:</b> DoubleValueWeightParameterEditor<p>
 *
 * <b>Description:</b>Subclass of ParameterEditor for editing DoubleValueWeightParameters.
 * The widget has two DoubleTextFields (one to enter value and other for weight)
 *  so that only numbers can be typed in. <p>
 *
 * The main functionality overidden from the parent class to achive Double
 * cusomization are the setWidgetObject() and AddWidget() functions. 
 * 
 * Note: We have to create a double parameter with constraints if we want to reflect the constarints
 *       as the tooltip text in the GUI. Because when we editor is created for that
 *       double parameter, it creates a constraint double parameter and then we can
 *       change the constraint and it will be reflected in the tool tip text.
 * <p> <p>
 *
 * @author vipingupta
 *
 */
public class DoubleValueWeightParameterEditor extends AbstractParameterEditorOld 
												implements ParameterChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final static boolean D = false;
	private final static String VALUE = "Value";
	private final static String WEIGHT = "Weight";
	protected DoubleParameter valueParameter;
	protected DoubleParameter weightParameter;
	private ParameterEditor<Double> valueParameterEditor, weightParameterEditor;
	protected final static Dimension PANEL_DIM = new Dimension( 100, 50);

	/**
	 * Default (no-argument) constructor
	 * Calls parent constructor
	 */
	public DoubleValueWeightParameterEditor() { super(); }
	
	 /**
     * Constructor that sets the parameter that it edits.
     *
     */
     public DoubleValueWeightParameterEditor(Parameter model) throws Exception {
        super(model);
        //this.setParameter(model);
    }
     


     
     /**
      *  Set's the parameter to be edited by this editor. The editor is
      *  updated with the name of the parameter as well as the widget
      *  component value. It attempts to use the Constraint name if
      *  different from the parameter and present, else uses the
      *  parameter name. This function actually just calls
      *  removeWidget() then addWidget() then setWidgetObject().
      */
     public void setParameter( Parameter model ) {
    	 this.model = model;
    	 // create params for value and weight
    	 createValueAndWeightParams();
    	 // create param editors 
    	 createParamEditors();
    	 this.setLayout(GBL);
    	 // add editors to the GUI
    	 //JPanel panel = new JPanel(new GridBagLayout());
    	  this.titledBorder1.setTitle(model.getName());
    	  this.setToolTipText(model.getInfo());
    	 //panel.add(new JLabel(model.getName()), new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0, 
    		//	 GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ) );	 
    	 widgetPanel.add(this.valueParameterEditor.getComponent(), new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0, 
    			 GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ) );
    	 widgetPanel.add(this.weightParameterEditor.getComponent(), new GridBagConstraints( 1, 0, 1, 1, 1.0, 1.0, 
    			 GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ) );
    	 widgetPanel.setMinimumSize(PANEL_DIM);
    	 widgetPanel.setPreferredSize(PANEL_DIM);
    	 //add(panel,  new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
    		//        , GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ) );

    	
     }

    /**
     * Create parameter editors
     *
     */ 
    private void createParamEditors() {
    	try {
			valueParameterEditor = valueParameter.getEditor();
			weightParameterEditor = weightParameter.getEditor();
		} catch (Exception e) {
			e.printStackTrace();
		}
    	
    }
    
    /**
     * It enables/disables the editor according to whether user is allowed to
     * fill in the values. THIS METHOD NEEDS TO BE OVERRIDDEN FOR COMPLEX ParameterEditors
     */
    public void setEnabled(boolean isEnabled) {
    	valueParameterEditor.setEnabled(isEnabled);
    	weightParameterEditor.setEnabled(isEnabled);
    }
 
     /**
      * Create parameters for value and weight
      *
      */
	private void createValueAndWeightParams() {
		// make parameter for value
		 DoubleValueWeightConstraint constraint = (DoubleValueWeightConstraint)model.getConstraint();
    	 DoubleConstraint valConstraint=null, weightConstraint = null;
    	 if(constraint!=null) {
    		 valConstraint = new DoubleConstraint(constraint.getMinVal(), constraint.getMaxVal());
    		 valConstraint.setNullAllowed(true);
    		 weightConstraint = new DoubleConstraint(constraint.getMinWt(), constraint.getMaxWt());
    		 weightConstraint.setNullAllowed(true);
    	 }
    	 valueParameter = new DoubleParameter(VALUE, valConstraint, model.getUnits());
    	 valueParameter.addParameterChangeListener(this);
    	 // make paramter for weight
    	 weightParameter = new DoubleParameter(WEIGHT, weightConstraint);
    	 weightParameter.addParameterChangeListener(this);
    	 // set initial values in value and weight
    	 ValueWeight valueWeight = (ValueWeight)this.model.getValue();
    	 if(valueWeight!=null)  {
    		 valueParameter.setValue(valueWeight.getValue());
    		 weightParameter.setValue(valueWeight.getWeight());
    	 }
	}
	
	/**
	 * This function is called whenever value or weight 
	 */
	public void parameterChange(ParameterChangeEvent event) {
		String paramName = event.getParameterName();
		ValueWeight oldValueWeight  = (ValueWeight)this.model.getValue();
		ValueWeight valueWeight;
		// set the value in the parameter
		if(oldValueWeight==null) {
			valueWeight = new ValueWeight();
		} else valueWeight = (ValueWeight)oldValueWeight.clone();
		
		// update the parameter value
		if(paramName.equalsIgnoreCase(VALUE)) {
			//set the Value in ValueWeight object
			Double value = (Double)valueParameter.getValue();
			if(value==null) value = Double.valueOf(Double.NaN);
			valueWeight.setValue(value.doubleValue());
		} else if(paramName.equalsIgnoreCase(WEIGHT)) {
			//set the weight in ValueWeight object
			Double weight = (Double)weightParameter.getValue();
			if(weight==null) weight = Double.valueOf(Double.NaN);
			valueWeight.setWeight(weight.doubleValue());
		}
		model.setValue(valueWeight);
	}
	
}
