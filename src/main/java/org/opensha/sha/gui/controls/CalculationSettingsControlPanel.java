package org.opensha.sha.gui.controls;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.JFrame;

import org.opensha.commons.gui.ControlPanel;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;

/**
 * <p>Title: CalculationSettingsControlPanel</p>
 * <p>Description: This class takes the adjustable parameters from the calculators
 * like ScenarioshakeMapCalc and HazardMapCalc and show it in the control panel. </p>
 * @author : Ned Field, Nitin Gupta and Vipin  Gupta
 * @created : June 16, 2004
 * @version 1.0
 */

public class CalculationSettingsControlPanel extends ControlPanel {
	
	public static final String NAME = "Calculation Settings";

	//declaring the instance of the parameterlist and editor.
	private ParameterList paramList;
	private ParameterListEditor editor;
	private BorderLayout borderLayout1 = new BorderLayout();
	//instance of the class implementing PropagationEffectControlPanelAPI interface.
	private CalculationSettingsControlPanelAPI application;
	
	private JFrame frame;
	
	private Component parentComponent;

	/**
	 *
	 * @param api : Instance of the class using this control panel and implmenting
	 * the CalculationSettingsControlPanelAPI.
	 */
	public CalculationSettingsControlPanel(Component parentComponent,CalculationSettingsControlPanelAPI api) {
		super(NAME);
		application = api;
		this.parentComponent = parentComponent;
	}
	
	public void doinit() {
		frame = new JFrame();
		paramList = application.getCalcAdjustableParams();
		editor = new ParameterListEditor(paramList);
		try {
			// show the window at center of the parent component
			frame.setLocation(parentComponent.getX()+parentComponent.getWidth()/2,
					parentComponent.getY()+parentComponent.getHeight()/2);
			jbInit();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private void jbInit() throws Exception {
		frame.setSize(350,800);
		frame.setTitle("Calculation Settings");
		frame.getContentPane().setLayout(new GridBagLayout());
		frame.getContentPane().add(editor,new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
				,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0));
	}

	public Object getParameterValue(String paramName) {
		return paramList.getValue(paramName);
	}

	public ParameterList getAdjustableCalcParams() {
		return paramList;
	}

	@Override
	public Window getComponent() {
		return frame;
	}


}
