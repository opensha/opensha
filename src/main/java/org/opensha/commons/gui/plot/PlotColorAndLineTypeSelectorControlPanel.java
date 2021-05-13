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
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import org.jfree.chart.plot.DatasetRenderingOrder;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.IntegerDiscreteConstraint;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.commons.param.impl.StringParameter;

/**
 * <p>Title: PlotColorAndLineTypeSelectorControlPanel</p>
 * <p>Description: This class allows user to select different plotting
 * styles for curves. Here user can specify color, curve style and
 * it size. the default value for lines are 1.0f and and for shapes
 * it is 4.0f.
 * Currently supported Styles are:
 * </p>
 * @author : Ned Field, Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public class PlotColorAndLineTypeSelectorControlPanel extends JFrame implements
ActionListener,ParameterChangeListener{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private JPanel jPanel1 = new JPanel();
	private JLabel jLabel1 = new JLabel();
	private JPanel colorAndLineTypeSelectorPanel;

	//X-Axis Label param name
	private StringParameter xAxisLabelParam;
	public final static String xAxisLabelParamName = "X-Axis Label";

	//Y-Axis Label param name
	private StringParameter yAxisLabelParam;
	public final static String yAxisLabelParamName = "Y-Axis Label";


	//Plot Label param name
	private StringParameter plotLabelParam;
	public final static String plotLabelParamName = "Plot Label";

	//static String definitions
	private final static String colorChooserString = "Color";
//	private final static String lineTypeString = "Choose Line Type";
	//name of the attenuationrelationship weights parameter
	public static final String LINE_WIDTH_NAME = "Line Size";
	public static final String SYMBOL_WIDTH_NAME = "Symbol Size";
	
	private static final String NONE_OPTION = "(none)";
	public static final String LINE_TYPE_NAME = "Line Type";
	private static final PlotLineType LINE_TYPE_DEFAULT = PlotLineType.SOLID;
	private ArrayList<String> lineTypeStrings;
	public static final String SYMBOL_NAME = "Symbol";
	private static final PlotSymbol SYMBOL_DEFAULT = null;
	private ArrayList<String> symbolStrings;

	//parameter for tick label font size
	private  IntegerParameter tickFontSizeParam;
	public static final String tickFontSizeParamName = "Set tick label size";
	//private ConstrainedStringParameterEditor tickFontSizeParamEditor;

	//parameter for axis label font size
	private IntegerParameter axisLabelsFontSizeParam;
	public static final String axislabelsFontSizeParamName = "Set axis label ";

	//parameter for plot label font size
	private IntegerParameter plotLabelsFontSizeParam;
	public static final String plotlabelsFontSizeParamName = "Set Plot label ";
	
	private static final String PLOT_ORDER_PARAM_NAME = "Plotting Order";
	private static final String PLOT_ORDER_FORWARD = "Most Recent On Top";
	private static final String PLOT_ORDER_BACKWARD = "Most Recent On Bottom";
	private static final String PLOT_ORDER_DEFAULT = PLOT_ORDER_FORWARD;
	private StringParameter plotOrderParam;

	//private ConstrainedStringParameterEditor axisLabelsFontSizeParamEditor;

	//parameterList and editor for axis and plot label parameters
	private ParameterList plotParamList;
	private ParameterListEditor plotParamEditor;

	//Dynamic Gui elements array to show the dataset color coding and line plot scheme
	private JLabel[] datasetSelector;
	private JButton[] colorChooserButton;
//	private JComboBox[] lineTypeSelector;
	private StringParameter[] lineTypeParam;
	private StringParameter[] symbolTypeParam;
	//AttenuationRelationship parameters and list declaration
	private DoubleParameter[] lineWidthParameter;
	private DoubleParameter[] symbolWidthParameter;

	private JButton applyButton = new JButton();
	private JButton cancelButton = new JButton();
	private BorderLayout borderLayout1 = new BorderLayout();

	//Curve characterstic array
	private List<PlotCurveCharacterstics> plottingFeatures;
	//default curve characterstics with values , when this control panel was called
	private List<PlotCurveCharacterstics> defaultPlottingFeatures;
	private JButton RevertButton = new JButton();
	//instance of application using this control panel
	private JPanel curveFeaturePanel = new JPanel();
	private JButton doneButton = new JButton();
	private GridBagLayout gridBagLayout1 = new GridBagLayout();
	
	private GraphWidget gw;

	//last updated width vals for Labels
//	private int tickLabelWidth ;
//	private int axisLabelWidth;
//	private int plotLabelWidth;

	public PlotColorAndLineTypeSelectorControlPanel(GraphWidget gw, List<PlotCurveCharacterstics> curveCharacterstics) {
		System.out.println("PlotColorAndLineTypeSelectorControlPanel Init!");
		this.gw = gw;
		
		lineTypeStrings = new ArrayList<String>();
		lineTypeStrings.add(NONE_OPTION);
		for (PlotLineType plt : PlotLineType.values())
			lineTypeStrings.add(plt.toString());
		
		symbolStrings = new ArrayList<String>();
		symbolStrings.add(NONE_OPTION);
		for (PlotSymbol sym : PlotSymbol.values())
			symbolStrings.add(sym.toString());
		
		//creating the parameters to change the size of Labels
		//creating list of supported font sizes
		ArrayList<Integer> supportedFontSizes = new ArrayList<Integer>();

		supportedFontSizes.add(8);
		supportedFontSizes.add(10);
		supportedFontSizes.add(12);
		supportedFontSizes.add(14);
		supportedFontSizes.add(16);
		supportedFontSizes.add(18);
		supportedFontSizes.add(20);
		supportedFontSizes.add(22);
		supportedFontSizes.add(24);


		//creating the font size parameters
		tickFontSizeParam = new IntegerParameter(tickFontSizeParamName,
				new IntegerDiscreteConstraint(supportedFontSizes),supportedFontSizes.get(1));
		axisLabelsFontSizeParam = new IntegerParameter(axislabelsFontSizeParamName,
				new IntegerDiscreteConstraint(supportedFontSizes),supportedFontSizes.get(2));
		plotLabelsFontSizeParam = new IntegerParameter(plotlabelsFontSizeParamName,
				new IntegerDiscreteConstraint(supportedFontSizes),supportedFontSizes.get(2));
		ArrayList<String> plotOrderStrings = new ArrayList<String>();
		plotOrderStrings.add(PLOT_ORDER_FORWARD);
		plotOrderStrings.add(PLOT_ORDER_BACKWARD);
		plotOrderParam = new StringParameter(PLOT_ORDER_PARAM_NAME, plotOrderStrings, PLOT_ORDER_DEFAULT);
		tickFontSizeParam.addParameterChangeListener(this);
		axisLabelsFontSizeParam.addParameterChangeListener(this);
		plotLabelsFontSizeParam.addParameterChangeListener(this);
		plotOrderParam.addParameterChangeListener(this);
//		tickLabelWidth = Integer.parseInt((String)tickFontSizeParam.getValue());
//		axisLabelWidth = Integer.parseInt((String)axisLabelsFontSizeParam.getValue());
//		plotLabelWidth = Integer.parseInt((String)this.plotLabelsFontSizeParam.getValue());
		//creating the axis and plot label params
		xAxisLabelParam = new StringParameter(xAxisLabelParamName,gw.getXAxisLabel());
		yAxisLabelParam = new StringParameter(yAxisLabelParamName,gw.getYAxisLabel());
		plotLabelParam = new StringParameter(plotLabelParamName,gw.getPlotLabel());

		xAxisLabelParam.addParameterChangeListener(this);
		yAxisLabelParam.addParameterChangeListener(this);
		plotLabelParam.addParameterChangeListener(this);

		//creating parameterlist and its corresponding parameter to hold the Axis and plot label parameter together.
		plotParamList = new ParameterList();
		plotParamList.addParameter(tickFontSizeParam);
		plotParamList.addParameter(axisLabelsFontSizeParam);
		plotParamList.addParameter(xAxisLabelParam);
		plotParamList.addParameter(yAxisLabelParam);
		plotParamList.addParameter(plotLabelParam);
		plotParamList.addParameter(plotLabelsFontSizeParam);
		plotParamList.addParameter(plotOrderParam);

		plotParamEditor = new ParameterListEditor(plotParamList);
		plotParamEditor.setTitle("Plot Label Prefs Setting");
		
		try {
			jbInit();
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		// show the window at center of the parent component
		this.setLocation(gw.getX()+gw.getWidth()/3,
				gw.getY()+gw.getHeight()/2);

		
		//creating editors for these font size parameters
		setPlotColorAndLineType(curveCharacterstics);
	}

	private void jbInit() throws Exception {
		this.getContentPane().setLayout(borderLayout1);
		jPanel1.setLayout(gridBagLayout1);
		jLabel1.setFont(new java.awt.Font("Arial", 0, 18));
		jLabel1.setHorizontalAlignment(SwingConstants.CENTER);
		jLabel1.setHorizontalTextPosition(SwingConstants.CENTER);
		jLabel1.setText("Plot Settings");
		applyButton.setText("Apply");
		applyButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				applyButton_actionPerformed(e);
			}
		});
		cancelButton.setText("Cancel");
		cancelButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cancelButton_actionPerformed(e);
			}
		});
		RevertButton.setText("Revert");
		RevertButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				RevertButton_actionPerformed(e);
			}
		});
		curveFeaturePanel.setLayout(new GridLayout(0, 6));
		doneButton.setText("Done");
		doneButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				doneButton_actionPerformed(e);
			}
		});
		this.getContentPane().add(jPanel1, BorderLayout.CENTER);
		colorAndLineTypeSelectorPanel = new JPanel();
		colorAndLineTypeSelectorPanel.setLayout(new BoxLayout(colorAndLineTypeSelectorPanel, BoxLayout.Y_AXIS));
		colorAndLineTypeSelectorPanel.add(curveFeaturePanel);
		colorAndLineTypeSelectorPanel.add(plotParamEditor);
		JScrollPane scroll = new JScrollPane(colorAndLineTypeSelectorPanel);
		jPanel1.add(jLabel1,  new GridBagConstraints(0, 0, 4, 1, 0.0, 0.0
				,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 6, 0, 11), 0, 0));
		jPanel1.add(scroll,  new GridBagConstraints(0, 1, 4, 1, 1.0, 1.0
				,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 6, 0, 11), 0, 0));
		jPanel1.add(cancelButton,  new GridBagConstraints(3, 2, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 22, 2, 108), 0, 0));
		jPanel1.add(RevertButton,  new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 21, 2, 0), 0, 0));
		jPanel1.add(doneButton,  new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 22, 2, 0), 0, 0));
		jPanel1.add(applyButton,  new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 99, 2, 0), 0, 0));
		jPanel1.setSize(930,500);
		
		//colorAndLineTypeSelectorPanel.setSize(500,250);
		setSize(930,500);
	}


	/**
	 * creates the control panel with plotting characterstics for each curve in list.
	 * This function shows plotting characterstics (curve style, color, and its width)
	 * for each curve in list ,so creates these gui components dynamically based on
	 * number of functions in list.
	 */
	public void setPlotColorAndLineType(List<PlotCurveCharacterstics> curveCharacterstics){
		PlotPreferences plotPrefs = gw.getPlotPrefs();
		setOrAddParameterValue(axisLabelsFontSizeParam, plotPrefs.getAxisLabelFontSize());
		setOrAddParameterValue(tickFontSizeParam, plotPrefs.getTickLabelFontSize());
		setOrAddParameterValue(plotLabelsFontSizeParam, plotPrefs.getPlotLabelFontSize());
		axisLabelsFontSizeParam.getEditor().refreshParamEditor();
		tickFontSizeParam.getEditor().refreshParamEditor();
		plotLabelsFontSizeParam.getEditor().refreshParamEditor();
		
		DatasetRenderingOrder plotOrder = gw.getPlottingOrder();
		String plotOrderStr;
		if (plotOrder == DatasetRenderingOrder.FORWARD)
			plotOrderStr = PLOT_ORDER_FORWARD;
		else
			plotOrderStr = PLOT_ORDER_BACKWARD;
		plotOrderParam.setValue(plotOrderStr);
		plotOrderParam.getEditor().refreshParamEditor();
		
		int numCurves = curveCharacterstics.size();
		plottingFeatures = curveCharacterstics;
		defaultPlottingFeatures = new ArrayList<PlotCurveCharacterstics>();

		//creating the defaultPlotting features with original color scheme.
		for(int i=0;i<numCurves;++i){
			PlotCurveCharacterstics curvePlotPref = plottingFeatures.get(i);
			defaultPlottingFeatures.add((PlotCurveCharacterstics)curvePlotPref.clone());
		}


		datasetSelector = new JLabel[numCurves];
		colorChooserButton = new  JButton[numCurves];
		lineTypeParam = new StringParameter[numCurves];
		lineWidthParameter = new DoubleParameter[numCurves];
		symbolTypeParam = new StringParameter[numCurves];
		symbolWidthParameter = new DoubleParameter[numCurves];
		DoubleConstraint sizeConstraint = new DoubleConstraint(0,1000);
		for(int i=0;i<numCurves;++i){
			PlotCurveCharacterstics curvePlotPref = (PlotCurveCharacterstics)plottingFeatures.get(i);
			//creating the dataset Labl with the color in which they are shown in plots.
			datasetSelector[i] = new JLabel(curvePlotPref.getName());
			datasetSelector[i].setForeground(curvePlotPref.getColor());
			
			colorChooserButton[i] = new JButton(colorChooserString);
			colorChooserButton[i].addActionListener(this);
			
			PlotLineType plt = curvePlotPref.getLineType();
			lineTypeParam[i] = new StringParameter(LINE_TYPE_NAME, lineTypeStrings,
					plotLineTypeToString(plt));
			lineTypeParam[i].addParameterChangeListener(this);
			
			PlotSymbol sym = curvePlotPref.getSymbol();
			symbolTypeParam[i] = new StringParameter(SYMBOL_NAME, symbolStrings,
					plotSymbolToString(sym));
			symbolTypeParam[i].addParameterChangeListener(this);

			lineWidthParameter[i] = new DoubleParameter(LINE_WIDTH_NAME, sizeConstraint,
						new Double(curvePlotPref.getLineWidth()));
			
			symbolWidthParameter[i] = new DoubleParameter(SYMBOL_WIDTH_NAME, sizeConstraint,
					new Double(curvePlotPref.getSymbolWidth()));
		}

		curveFeaturePanel.removeAll();
		//adding color chooser button,plot style and size to GUI.
		for(int i=0;i<numCurves;++i) {
			curveFeaturePanel.add(datasetSelector[i]);
			curveFeaturePanel.add(colorChooserButton[i]);
			curveFeaturePanel.add(lineTypeParam[i].getEditor().getComponent());
			curveFeaturePanel.add(lineWidthParameter[i].getEditor().getComponent());
			curveFeaturePanel.add(symbolTypeParam[i].getEditor().getComponent());
			curveFeaturePanel.add(symbolWidthParameter[i].getEditor().getComponent());
		}
		curveFeaturePanel.doLayout();
		colorAndLineTypeSelectorPanel.doLayout();
	}

	/**
	 * If parameter is changed then Parameter change event is called on this class
	 * @param event
	 */
	public void parameterChange(ParameterChangeEvent event){
		//updating the size of the labels
		String paramName = event.getParameterName();
		if (paramName.equals(LINE_TYPE_NAME) || paramName.equals(SYMBOL_NAME)) {
			Parameter<String> param = event.getParameter();
			for (int i=0; i<lineTypeParam.length; i++) {
				if (lineTypeParam[i] == param || symbolTypeParam[i] == param) {
					updateEditorEnables(i);
					break;
				}
			}
		} else {
//			if(paramName.equals(tickFontSizeParamName)){
////				tickLabelWidth = Integer.parseInt((String)tickFontSizeParam.getValue());
//				//tickFontSizeParam.setValue(""+tickLabelWidth);
//
//			}
//			else if(paramName.equals(axislabelsFontSizeParamName)){
////				axisLabelWidth = Integer.parseInt((String)axisLabelsFontSizeParam.getValue());
//				//axisLabelsFontSizeParam.setValue(""+axisLabelWidth);
//			} else if(paramName.equals(plotlabelsFontSizeParamName)) {
////				plotLabelWidth = Integer.parseInt((String)this.plotLabelsFontSizeParam.getValue());
//			}
//			else if(paramName.equals(xAxisLabelParamName))
//				xAxisLabel = (String)this.xAxisLabelParam.getValue();
//			else if(paramName.equals(yAxisLabelParamName))
//				yAxisLabel = (String)this.yAxisLabelParam.getValue();
//			else if(paramName.equals(plotLabelParamName))
//				plotLabel = (String)this.plotLabelParam.getValue();

			plotParamEditor.refreshParamEditor();
		}
	}

	/**
	 * This is a common function if any action is performed on the color chooser button
	 * and plot line type selector
	 * It checks what is the source of the action and depending on the source how will it
	 * response to it.
	 * @param e
	 */
	public void actionPerformed(ActionEvent e){
		int numCurves = plottingFeatures.size();
		//checking if the source of the action was the button
		if(e.getSource() instanceof JButton){
			Object button = e.getSource();
			//if the source of the event was color button
			for(int i=0;i<numCurves;++i){
				PlotCurveCharacterstics curvePlotPref = (PlotCurveCharacterstics)plottingFeatures.get(i);
				if(button.equals(colorChooserButton[i])){
					Color color = JColorChooser.showDialog(this,"Select Color",curvePlotPref.getColor());
					//chnage the default color only if user has selected a new color , else leave it the way it is
					if(color !=null){
						curvePlotPref.setColor(color);
						datasetSelector[i].setForeground(color);
					}
				}
			}
		}
	}
	
	private PlotLineType getPlotLineType(int index) {
		StringParameter pltParam = lineTypeParam[index];
		String str = pltParam.getValue();
		if (str.equals(NONE_OPTION))
			return null;
		else
			return PlotLineType.forString(str);
	}
	
	private String plotLineTypeToString(PlotLineType plt) {
		if (plt == null)
			return NONE_OPTION;
		return plt.toString();
	}
	
	private PlotSymbol getPlotSymbol(int index) {
		StringParameter symParam = symbolTypeParam[index];
		String str = symParam.getValue();
		if (str.equals(NONE_OPTION))
			return null;
		else
			return PlotSymbol.forString(str);
	}
	
	private String plotSymbolToString(PlotSymbol sym) {
		if (sym == null)
			return NONE_OPTION;
		return sym.toString();
	}
	
	private void updateEditorEnables(int index) {
		updateEditorEnables(index, getPlotLineType(index), getPlotSymbol(index));
	}
	
	private void updateEditorEnables(int index, PlotLineType plt, PlotSymbol sym) {
		boolean lineSelected = plt != null;
		boolean symbolEnabled = !lineSelected || (lineSelected && plt.isSymbolCompatible());
		boolean symbolWidthEnabled = symbolEnabled && sym != null;
		
		lineWidthParameter[index].getEditor().setEnabled(lineSelected);
		symbolTypeParam[index].getEditor().setEnabled(symbolEnabled);
		if (!symbolEnabled) {
			symbolTypeParam[index].removeParameterChangeListener(this);
			symbolTypeParam[index].setValue(NONE_OPTION);
			symbolTypeParam[index].addParameterChangeListener(this);
			symbolTypeParam[index].getEditor().refreshParamEditor();
		}
		symbolWidthParameter[index].getEditor().setEnabled(symbolWidthEnabled);
	}

	/**
	 * Apply changes to the Plot and keeps the control panel for user to view the results
	 * @param e
	 */
	void applyButton_actionPerformed(ActionEvent e) {
		applyChangesToPlot();
	}
	
	
	
	private int getInvalidSettingsIndex() {
		for (int i=0; i<plottingFeatures.size(); i++) {
			PlotLineType plt = getPlotLineType(i);
			PlotSymbol sym = getPlotSymbol(i);
			if (plt == null && sym == null)
				return i;
		}
		return -1;
	}
	
	private void showInvalidSettingsDialog(int index) {
		String message = "You must select a line and/or symbol.\nThey cannot both be '"+NONE_OPTION+"'."+
				"\n\nInvalid Curve: #"+(index+1);
		String title = "Invalid settigns for curve "+(index+1);
		JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
	}

	private boolean applyChangesToPlot() {
		// disable check...graph panel will just hide curve
//		int invalidIndex = getInvalidSettingsIndex();
//		if (invalidIndex >= 0) {
//			showInvalidSettingsDialog(invalidIndex);
//			return false;
//		}
		int numCurves = plottingFeatures.size();
		//getting the line width parameter
		for(int i=0;i<numCurves;++i) {
			PlotCurveCharacterstics chars = plottingFeatures.get(i);
			PlotLineType lineType = getPlotLineType(i);
			float lineWidth = lineWidthParameter[i].getValue().floatValue();
			PlotSymbol symbol = getPlotSymbol(i);
			float symbolWidth = symbolWidthParameter[i].getValue().floatValue();
			chars.set(lineType, lineWidth, symbol, symbolWidth, chars.getColor());
		}
		gw.setXAxisLabel(xAxisLabelParam.getValue());
		gw.setYAxisLabel(yAxisLabelParam.getValue());
		gw.setPlotLabel(plotLabelParam.getValue());
		String plotOrder = plotOrderParam.getValue();
		if (plotOrder.equals(PLOT_ORDER_FORWARD))
			gw.setPlottingOrder(DatasetRenderingOrder.FORWARD);
		else
			gw.setPlottingOrder(DatasetRenderingOrder.REVERSE);
		PlotPreferences plotPrefs = gw.getPlotPrefs();
		plotPrefs.setAxisLabelFontSize(axisLabelsFontSizeParam.getValue());
		plotPrefs.setTickLabelFontSize(tickFontSizeParam.getValue());
		plotPrefs.setPlotLabelFontSize(plotLabelsFontSizeParam.getValue());
		gw.drawGraph();
		return true;
	}

	/**
	 * reverts the plots to original values and close the window
	 * @param e
	 */
	void cancelButton_actionPerformed(ActionEvent e) {
		revertPlotToOriginal();
		this.dispose();
	}

	/**
	 * Restoring the original values for plotting features
	 * @param e
	 */
	void RevertButton_actionPerformed(ActionEvent e) {
		int flag =JOptionPane.showConfirmDialog(this,"Restore Original Values","Reverting changes",JOptionPane.OK_CANCEL_OPTION);
		if(flag == JOptionPane.OK_OPTION){
			revertPlotToOriginal();
		}
	}

	private void revertPlotToOriginal(){
		int numCurves = defaultPlottingFeatures.size();
		for(int i=0;i<numCurves;++i){
			PlotCurveCharacterstics defaultChars = defaultPlottingFeatures.get(i);
			PlotCurveCharacterstics chars = plottingFeatures.get(i);
			
			// color
			datasetSelector[i].setForeground(defaultChars.getColor());
			chars.setColor(defaultChars.getColor());
			
			// line type
			PlotLineType lineType = defaultChars.getLineType();
			lineTypeParam[i].setValue(plotLineTypeToString(lineType));
			lineTypeParam[i].getEditor().refreshParamEditor();
			chars.setLineType(lineType);
			
			// symbol type
			PlotSymbol symbol = defaultChars.getSymbol();
			symbolTypeParam[i].setValue(plotSymbolToString(symbol));
			symbolTypeParam[i].getEditor().refreshParamEditor();
			chars.setSymbol(symbol);
			
			// line size
			float lineWidth = defaultChars.getLineWidth();
			lineWidthParameter[i].setValue((double)lineWidth);
			lineWidthParameter[i].getEditor().refreshParamEditor();
			chars.setLineWidth(lineWidth);
			
			// symbol size
			float symbolWidth = defaultChars.getSymbolWidth();
			symbolWidthParameter[i].setValue((double)symbolWidth);
			symbolWidthParameter[i].getEditor().refreshParamEditor();
			chars.setSymbolWidth(symbolWidth);
			
			curveFeaturePanel.repaint();
			curveFeaturePanel.validate();
			gw.drawGraph();
		}
	}
	
	/**
	 * This sets the value of the given string parameter to the given value. If the value is not allowed, it is added
	 * to the string constraint's "allowed" list.
	 * @param stringParam
	 * @param value
	 */
	private void setOrAddParameterValue(IntegerParameter intParam, Integer value) {
		if (intParam.isAllowed(value)) {
			intParam.setValue(value);
		} else {
			IntegerDiscreteConstraint iconst = (IntegerDiscreteConstraint) intParam.getConstraint();
			iconst.addAllowed(value);
			intParam.setValue(value);
		}
	}

	/**
	 * Apply all changes to Plot and closes the control window
	 * @param e
	 */
	void doneButton_actionPerformed(ActionEvent e) {
		if (applyChangesToPlot())
			this.dispose();
	}

}
