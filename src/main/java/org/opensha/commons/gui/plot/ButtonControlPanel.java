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

package org.opensha.commons.gui.plot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.jfree.data.Range;

import com.google.common.base.Preconditions;

/**
 * <p>Title: ButtonControlPanel</p>
 * <p>Description: This class creates a button Panel for the Applications:
 * HazardCurveApplet, HazardCurveServerModeApp and HazardSpectrum Applet</p>
 * @author : Nitin Gupta
 * @version 1.0
 */

public class ButtonControlPanel extends JPanel implements ActionListener {

//	JButton button = new JButton();
//	  button.putClientProperty("JButton.buttonType", style);
//	  button.putClientProperty("JButton.segmentPosition", position);
	  


	// message string to be dispalayed if user chooses Axis Scale
	// when a plot doesn't yet exist
	private final static String AXIS_RANGE_NOT_ALLOWED =
		new String("First Choose Add Graph. Then choose Axis Scale option");
	
	private JPanel buttonPanel;
	private JPanel checkboxPanel;
	
	private JCheckBox jCheckylog;
	private JCheckBox jCheckxlog;
	private JButton setAxisButton;
	private JButton toggleButton;
	private JButton plotPrefsButton;

	//Axis Range control panel object (creates the instance for the AxisLimitsControl)
	private AxisLimitsControlPanel axisControlPanel;

	//Curve color scheme and its line shape control panel instance
	private PlotColorAndLineTypeSelectorControlPanel plotControl;

	//boolean to check if axis range is auto or custom
	private PlotPreferences plotPrefs;
	
	private GraphWidget gw;

	public ButtonControlPanel(GraphWidget gw, PlotPreferences plotPrefs) {
		Preconditions.checkNotNull(gw, "GraphWidget cannot be null");
		Preconditions.checkNotNull(plotPrefs, "PlotPreferences cannot be null");
		this.gw = gw;
		this.plotPrefs = plotPrefs;
		initUI();
	}
	
	private void initUI() {
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8,4,0,4));
//		setMinimumSize(new Dimension(0, 100)); TODO clean
//		setPreferredSize(new Dimension(500, 100));		
		
		plotPrefsButton = new JButton("Plot Prefs");
		plotPrefsButton.addActionListener(this);
		plotPrefsButton.putClientProperty("JButton.buttonType", "segmentedTextured");
		plotPrefsButton.putClientProperty("JButton.segmentPosition", "first");
		plotPrefsButton.putClientProperty("JComponent.sizeVariant","small");
				
		toggleButton = new JButton("Show Data");
		toggleButton.addActionListener(this);
		toggleButton.putClientProperty("JButton.buttonType", "segmentedTextured");
		toggleButton.putClientProperty("JButton.segmentPosition", "middle");
		toggleButton.putClientProperty("JComponent.sizeVariant","small");

		setAxisButton = new JButton("Set Axis");
		setAxisButton.addActionListener(this);
		setAxisButton.putClientProperty("JButton.buttonType", "segmentedTextured");
		setAxisButton.putClientProperty("JButton.segmentPosition", "last");
		setAxisButton.putClientProperty("JComponent.sizeVariant","small");

		buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
		
		buttonPanel.add(Box.createHorizontalGlue());
		buttonPanel.add(plotPrefsButton);
		buttonPanel.add(toggleButton);
		buttonPanel.add(setAxisButton);
		buttonPanel.add(Box.createHorizontalGlue());
		
		JLabel logScale  = new JLabel("Log scale: ");
		logScale.putClientProperty("JComponent.sizeVariant","small");
		
		jCheckxlog = new JCheckBox("X");
		jCheckxlog.addActionListener(this);
		jCheckxlog.putClientProperty("JComponent.sizeVariant","small");
		
		jCheckylog = new JCheckBox("Y");
		jCheckylog.addActionListener(this);
		jCheckylog.putClientProperty("JComponent.sizeVariant","small");
		
		checkboxPanel = new JPanel();
		checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.LINE_AXIS));
		
		checkboxPanel.add(Box.createHorizontalGlue());
		checkboxPanel.add(logScale);
		checkboxPanel.add(jCheckxlog);
		checkboxPanel.add(jCheckylog);
		checkboxPanel.add(Box.createHorizontalGlue());
		
		add(buttonPanel, BorderLayout.CENTER);
		add(checkboxPanel, BorderLayout.PAGE_END);
	}


	/* implementation */
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src.equals(jCheckxlog)) {
			gw.setX_Log(jCheckxlog.isSelected());
		} else if (src.equals(jCheckylog)) {
			gw.setY_Log(jCheckylog.isSelected());
		} else if (src.equals(setAxisButton)) {
			setAxisAction();
		} else if (src.equals(toggleButton)) {
			gw.togglePlot();
		} else if (src.equals(plotPrefsButton)) {
			plotPrefsAction();
		}
	}

	/**
	 * Returns the panel containing buttons so that oter items may be added.
	 * Panel has a <code>BoxLayout</code>.
	 * @return the button panel
	 */
	public JPanel getButtonRow() {
		return buttonPanel;
	}
	
	/**
	 * Returns the panel containing checkboxes so that oter items may be added.
	 * Panel has a <code>BoxLayout</code>.
	 * @return the checkbox panel
	 */
	public JPanel getCheckboxRow() {
		return checkboxPanel;
	}
	
	/**
	 * Sets the text for the toggle button.
	 * @param text to set
	 */
	public void setToggleButtonText(String text){
		toggleButton.setText(text);
	}
	
	public void updateToggleButtonText(boolean graphOn) {
		if (graphOn)
			setToggleButtonText("Show Data");
		else
			setToggleButtonText("Show Plot");
	}

	//Action method when the "Set Axis Range" button is pressed.
	private void setAxisAction() {
		Range xAxisRange = gw.getX_AxisRange();
		Range yAxisRange = gw.getY_AxisRange();
		if(xAxisRange==null || yAxisRange==null) {
			JOptionPane.showMessageDialog(this,AXIS_RANGE_NOT_ALLOWED);
			return;
		}

		double minX=xAxisRange.getLowerBound();
		double maxX=xAxisRange.getUpperBound();
		double minY=yAxisRange.getLowerBound();
		double maxY=yAxisRange.getUpperBound();
		
		Range customXRange = gw.getUserX_AxisRange();
		Range customYRange = gw.getUserY_AxisRange();
		
		if(axisControlPanel == null)
			axisControlPanel=new AxisLimitsControlPanel(gw, this);
		else
			axisControlPanel.updateParams();
		if (!axisControlPanel.isInitialized())
			axisControlPanel.init();
		axisControlPanel.getComponent().pack();
		axisControlPanel.getComponent().setVisible(true);
	}

	/**
	 * Sets the X-Log CheckBox to be selected or deselected based on the flag
	 * @param flag
	 */
	public void setXLog(boolean flag){
		jCheckxlog.setSelected(flag);
	}
	
	public boolean isXLogSelected() {
		return jCheckxlog.isSelected();
	}

	/**
	 * Sets the Y-Log CheckBox to be selected or deselected based on the flag
	 * @param flag
	 */
	public void setYLog(boolean flag){
		jCheckylog.setSelected(flag);
	}
	
	public boolean isYLogSelected() {
		return jCheckylog.isSelected();
	}

	/**
	 * Makes all the component of this button control panel to be disabled or enable
	 * based on the boolean value of the flag
	 * @param flag
	 */
	public void setEnabled(boolean flag){
		jCheckxlog.setEnabled(flag);
		jCheckylog.setEnabled(flag);
//		setAxisButton.setEnabled(flag);
//		toggleButton.setEnabled(flag);
//		plotPrefsButton.setEnabled(flag);
		for (Component c : buttonPanel.getComponents())
			c.setEnabled(flag);
	}

	/**
	 * If button to set the plot Prefernces is "clicked" by user.
	 * @param e
	 */
	private void plotPrefsAction() {
		List<PlotCurveCharacterstics> plotFeatures = gw.getPlottingFeatures();
		if(plotControl == null)
			// first display
			plotControl = new PlotColorAndLineTypeSelectorControlPanel(gw,plotFeatures);
		else
			// redisplay
			plotControl.setPlotColorAndLineType(plotFeatures);
		plotControl.setVisible(true);
	}



	/**
	 * Sets the Plot Preference, button that allows users to set the color codes
	 * and curve plotting preferences.
	 * @param flag
	 */
	public void setPlotPreferencesButtonVisible(boolean flag){
		plotPrefsButton.setVisible(false);
	}

}
