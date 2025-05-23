package org.opensha.sha.magdist.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.event.ChangeEvent;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.editor.impl.ConstrainedStringParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.GaussianMagFreqDist;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SingleMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.magdist.TaperedGR_MagFreqDist;
import org.opensha.sha.magdist.YC_1985_CharMagFreqDist;
import org.opensha.sha.param.MagFreqDistParameter;
import org.opensha.sha.param.MagPDF_Parameter;
import org.opensha.sha.param.editor.MagDistParameterEditorAPI;
import org.opensha.sha.param.editor.MagFreqDistParameterEditor;
import org.opensha.sha.param.editor.MagPDF_ParameterEditor;

import com.google.common.collect.Lists;
/**
 * <p>Title:MagFreqDistApp </p>
 *
 * <p>Description: Shows the MagFreqDist Editor and plot in a window.</p>
 *
 * <p>Copyright: Copyright (c) 2002</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class MagFreqDistAppWindow
extends JFrame implements ParameterChangeListener {

	private JSplitPane mainSplitPane = new JSplitPane();
	private JSplitPane plotSplitPane = new JSplitPane();
	private JTabbedPane plotTabPane = new JTabbedPane();
	private JPanel editorPanel = new JPanel();
	private JPanel MagSelectionEditorPanel;
	private JPanel buttonPanel = new JPanel();

	/**
	 * Defines the panel and layout for the GUI elements
	 */
	private JPanel incrRatePlotPanel = new JPanel();
	private JPanel momentRatePlotPanel = new JPanel();
	private JPanel cumRatePlotPanel = new JPanel();
	private GridBagLayout gridBagLayout1 = new GridBagLayout();
	private FlowLayout flowLayout1 = new FlowLayout();
	private BorderLayout borderLayout1 = new BorderLayout();
	private JButton addButton = new JButton();

	protected final static int W = 870;
	protected final static int H = 750;

	private final boolean D = false;

	//instance of the GraphPanel (window that shows all the plots)
	private GraphWidget incrRateGraphPanel,momentRateGraphPanel,cumRateGraphPanel;

	private JSplitPane paramSplitPane = new JSplitPane();

	//X and Y Axis  when plotting the Curves Name
	private String incrRateXAxisName = "Magnitude", incrRateYAxisName = "Incremental Rate";
	//X and Y Axis  when plotting the Curves Name
	private String cumRateXAxisName = "Magnitude" , cumRateYAxisName = "Cumulative Rate";
	//X and Y Axis  when plotting the Curves Name
	private String momentRateXAxisName = "Magnitude", momentRateYAxisName =  "Moment Rate";

	private boolean isIncrRatePlot,isMomentRatePlot,isCumRatePlot;

	private JButton peelOffButton = new JButton();
	private JMenuBar menuBar = new JMenuBar();
	private JMenu fileMenu = new JMenu();


	private JMenuItem fileExitMenu = new JMenuItem();
	private JMenuItem fileSaveMenu = new JMenuItem();
	private JMenuItem filePrintMenu = new JCheckBoxMenuItem();
	private JToolBar jToolBar = new JToolBar();

	private JButton closeButton = new JButton();
	private ImageIcon closeFileImage = new ImageIcon(FileUtils.loadImage("icons/closeFile.png"));

	private JButton printButton = new JButton();
	private ImageIcon printFileImage = new ImageIcon(FileUtils.loadImage("icons/printFile.jpg"));

	private  JButton saveButton = new JButton();
	ImageIcon saveFileImage = new ImageIcon(FileUtils.loadImage("icons/saveFile.jpg"));

	private final static String POWERED_BY_IMAGE = "logos/PoweredByOpenSHA_Agua.jpg";

	private JLabel imgLabel = new JLabel(new ImageIcon(FileUtils.loadImage(this.POWERED_BY_IMAGE)));
	private JButton clearButton = new JButton();


	//instance of the MagDist Editor
	private MagDistParameterEditorAPI magDistEditor;
	private MagFreqDistParameterEditor magFreqDistEditor;
	private MagPDF_ParameterEditor magPDF_Editor ;

	private JCheckBox jCheckSumDist = new JCheckBox();


	//list for storing all types of Mag Freq. dist. (incremental, cumulative and momentRate).
	private ArrayList<PlotElement> incrRateFunctionList = new ArrayList<PlotElement>();
	private ArrayList<PlotElement> cumRateFunctionList =  new ArrayList<PlotElement>();
	private ArrayList<PlotElement> momentRateFunctionList = new ArrayList<PlotElement>();
	//summed distribution
	private final static String textString = "(Last Plotted Dist. gets used"+
	", Summed Dist. gets used if selected)";
	private JLabel textLabel = new JLabel(textString);


	private String incrRatePlotTitle="",cumRatePlotTitle="",momentRatePlotTitle="";


	//checks to see if summed distribution has been added, then this number will be
	//one less then the number of plotted disctributions.
	private int numFunctionsWithoutSumDist;
	private static final String MAG_DIST_PARAM_SELECTOR_NAME = "Mag. Dist. Type";
	private static final String MAG_FREQ_DIST = "Mag. Freq. Dist";
	private static final String MAG_PDF_PARAM = "Mag. PDF";
	private StringParameter stParam;
	private SummedMagFreqDist summedMagFreqDist;


	public MagFreqDistAppWindow() {
		try {
			jbInit();
		}
		catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	private void jbInit() throws Exception {
		getContentPane().setLayout(borderLayout1);



		addButton.setText("Plot-Dist");
		addButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				addButton_actionPerformed(e);
			}
		});

		peelOffButton.setText("Peel-Off");
		peelOffButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				peelOffButton_actionPerformed(e);
			}
		});

		clearButton.setText("Clear-Plot");
		clearButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearButton_actionPerformed(e);
			}
		});


		fileMenu.setText("File");
		fileExitMenu.setText("Exit");
		fileSaveMenu.setText("Save");
		filePrintMenu.setText("Print");

		fileExitMenu.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				fileExitMenu_actionPerformed(e);
			}
		});

		fileSaveMenu.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				fileSaveMenu_actionPerformed(e);
			}
		});

		filePrintMenu.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filePrintMenu_actionPerformed(e);
			}
		});

		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				closeButton_actionPerformed(actionEvent);
			}
		});
		printButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				printButton_actionPerformed(actionEvent);
			}
		});
		saveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				saveButton_actionPerformed(actionEvent);
			}
		});


		menuBar.add(fileMenu);
		fileMenu.add(fileSaveMenu);
		fileMenu.add(filePrintMenu);
		fileMenu.add(fileExitMenu);

		setJMenuBar(menuBar);
		closeButton.setIcon(closeFileImage);
		closeButton.setToolTipText("Exit Application");
		Dimension d = closeButton.getSize();
		jToolBar.add(closeButton);
		printButton.setIcon(printFileImage);
		printButton.setToolTipText("Print Graph");
		printButton.setSize(d);
		jToolBar.add(printButton);
		saveButton.setIcon(saveFileImage);
		saveButton.setToolTipText("Save Graph as image");
		saveButton.setSize(d);
		jToolBar.add(saveButton);
		jToolBar.setFloatable(false);

		this.getContentPane().add(jToolBar, BorderLayout.NORTH);

		mainSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		plotSplitPane.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
		editorPanel.setLayout(gridBagLayout1);

		buttonPanel.setLayout(flowLayout1);
		plotSplitPane.add(plotTabPane, JSplitPane.LEFT);
		mainSplitPane.add(plotSplitPane, JSplitPane.TOP);
		plotSplitPane.add(paramSplitPane, JSplitPane.RIGHT);
		mainSplitPane.add(buttonPanel, JSplitPane.BOTTOM);
		paramSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		paramSplitPane.setDividerLocation(200);
		this.getContentPane().add(mainSplitPane, java.awt.BorderLayout.CENTER);
		plotSplitPane.setDividerLocation(600);
		mainSplitPane.setDividerLocation(570);
		incrRatePlotPanel.setLayout(gridBagLayout1);
		momentRatePlotPanel.setLayout(gridBagLayout1);
		cumRatePlotPanel.setLayout(gridBagLayout1);
		plotTabPane.add("Incremental-Rate", incrRatePlotPanel);
		plotTabPane.add("Cumulative-Rate", cumRatePlotPanel);
		plotTabPane.add("Moment-Rate", momentRatePlotPanel);
		plotTabPane.addChangeListener(new javax.swing.event.ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				plotTabPane_stateChanged(e);
			}
		});
		jCheckSumDist.setForeground(Color.black);
		jCheckSumDist.setText("Summed Dist");
		jCheckSumDist.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				jCheckSumDist_actionPerformed(e);
			}
		});
		jCheckSumDist.setVisible(false);
		buttonPanel.add(jCheckSumDist,0);
		buttonPanel.add(addButton, 1);
		buttonPanel.add(clearButton, 2);
		buttonPanel.add(peelOffButton, 3);
		buttonPanel.add(imgLabel, 4);
		buttonPanel.add(textLabel, 5);


		incrRateGraphPanel = new GraphWidget();
		cumRateGraphPanel = new GraphWidget();
		momentRateGraphPanel = new GraphWidget();

		this.setSize( W, H );
		Dimension dm = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation( ( dm.width - this.getSize().width ) / 2, ( dm.height - this.getSize().height ) / 2 );
		this.setTitle("Magnitude Frequency Distribution Application");
		
		incrRatePlotPanel.removeAll();
		cumRatePlotPanel.removeAll();
		momentRatePlotPanel.removeAll();

		incrRatePlotPanel.add(incrRateGraphPanel, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
				, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ));
		incrRatePlotPanel.validate();
		incrRatePlotPanel.repaint();

		cumRatePlotPanel.add(cumRateGraphPanel, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
				, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ));
		cumRatePlotPanel.validate();
		cumRatePlotPanel.repaint();

		momentRatePlotPanel.add(momentRateGraphPanel,
				new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
						, GridBagConstraints.CENTER,
						GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
		momentRatePlotPanel.validate();
		momentRatePlotPanel.repaint();
		
		this.setVisible( true );
	}

	/**
	 * Initiates the Mag Param selection and adds that to the GUI.
	 * User has the option of creating a MagFreqDist or MagPDF
	 */
	void initMagParamEditor() {
		ArrayList magParamTypes = new ArrayList();
		magParamTypes.add(this.MAG_FREQ_DIST);
		magParamTypes.add(this.MAG_PDF_PARAM);
		stParam = new StringParameter(this.
				MAG_DIST_PARAM_SELECTOR_NAME,
				magParamTypes,
				(String) magParamTypes.get(0));
		ConstrainedStringParameterEditor stParamEditor = new
		ConstrainedStringParameterEditor(stParam);
		stParam.addParameterChangeListener(this);
		MagSelectionEditorPanel = new JPanel();
		MagSelectionEditorPanel.setLayout(gridBagLayout1);
		MagSelectionEditorPanel.add(stParamEditor,
				new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
						, GridBagConstraints.NORTH,
						GridBagConstraints.BOTH,
						new Insets(1, 1, 1, 1), 0, 0));
		MagSelectionEditorPanel.validate();
		MagSelectionEditorPanel.repaint();
		this.paramSplitPane.add(MagSelectionEditorPanel,paramSplitPane.TOP);
		this.setDefaultCloseOperation(3);


	}

	/**
	 * Creates the MagDist Param.
	 * It can be Mag_PDF_Param or MagFreqDistParam.
	 * Adding the SummedDist MagDist to the MagFreqDistParam list of Distribution.
	 * It has not been added the Mag_PDFParam because then user will have to provide
	 * Relative Wts for the Dist. which has not decided yet.
	 *
	 */
	void createMagParam(){

		String magTypeSelected = (String)stParam.getValue();
		if(magTypeSelected.equals(MAG_FREQ_DIST)){

			if(magFreqDistEditor == null){
				ArrayList distNames = new ArrayList();
				distNames.add(SingleMagFreqDist.NAME);
				distNames.add(GutenbergRichterMagFreqDist.NAME);
				distNames.add(TaperedGR_MagFreqDist.NAME);
				distNames.add(GaussianMagFreqDist.NAME);
				distNames.add(YC_1985_CharMagFreqDist.NAME);
				distNames.add(SummedMagFreqDist.NAME);
				distNames.add(ArbIncrementalMagFreqDist.NAME);
				String MAG_DIST_PARAM_NAME = "Mag Dist Param";
				// make  the mag dist parameter
				MagFreqDistParameter magDist = new MagFreqDistParameter(
						MAG_DIST_PARAM_NAME, distNames);
				magFreqDistEditor = new MagFreqDistParameterEditor();
				magFreqDistEditor.setParameter(magDist);
				// make the magdist param button invisible instead display it as the panel in the window
				magFreqDistEditor.setMagFreqDistParamButtonVisible(false);
			}
			else
				editorPanel.remove(magDistEditor.getMagFreqDistParameterEditor());
			setMagDistEditor(magFreqDistEditor);
		}
		else{
			editorPanel.remove(magDistEditor.getMagFreqDistParameterEditor());
			//making the Summed Distn option not visible for Mag PDF.
			this.makeSumDistVisible(false);
			if(magPDF_Editor == null){
				String MAG_DIST_PARAM_NAME = "Mag Dist Param";
				// make  the mag dist parameter
				ArrayList distNames = new ArrayList();
				distNames.add(SingleMagFreqDist.NAME);
				distNames.add(GutenbergRichterMagFreqDist.NAME);
				distNames.add(GaussianMagFreqDist.NAME);
				distNames.add(YC_1985_CharMagFreqDist.NAME);
				distNames.add(ArbIncrementalMagFreqDist.NAME);
				MagPDF_Parameter magDist = new MagPDF_Parameter(
						MAG_DIST_PARAM_NAME, distNames);
				magPDF_Editor = new MagPDF_ParameterEditor();
				magPDF_Editor.setParameter(magDist);
				// make the magdist param button invisible instead display it as the panel in the window
				magPDF_Editor.setMagFreqDistParamButtonVisible(false);
			}
			setMagDistEditor(magPDF_Editor);
		}
		magDistEditor.refreshParamEditor();
	}

	/**
	 *
	 */
	public void setMagDistEditor(MagDistParameterEditorAPI magDistEditor) {

		this.magDistEditor = magDistEditor;
		ParameterListEditor listEditor = magDistEditor.createMagFreqDistParameterEditor();
		Parameter distParam = listEditor.getParameterEditor(MagFreqDistParameter.DISTRIBUTION_NAME).getParameter();
		distParam.addParameterChangeListener(this);
		ArrayList allowedVals = ((StringConstraint)listEditor.getParameterEditor(MagFreqDistParameter.DISTRIBUTION_NAME).
				getParameter().getConstraint()).getAllowedValues();
		//if Summed Distn. is within the allowed list of MagDistn then show it as the JCheckBox.
		//it will work the same for the Mag_PDF_Dist
		if(allowedVals.contains(SummedMagFreqDist.NAME)){
			makeSumDistVisible(true);
		}
		editorPanel.add(listEditor,
				new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0
						, GridBagConstraints.NORTH,
						GridBagConstraints.BOTH,
						new Insets(2, 2, 2, 2), 0, 0));
		//if user is not being the shown the option of both PDF and MagFreqDist then
		//user just making the SplitPane not adjustable.
		if(MagSelectionEditorPanel == null){
			paramSplitPane.setDividerLocation(0);
			paramSplitPane.setOneTouchExpandable(false);
		}
		paramSplitPane.add(editorPanel,paramSplitPane.BOTTOM);
		editorPanel.validate();
		editorPanel.repaint();
	}


	/**
	 * Makes the Summed Dist. option visible or invisible based on the passed in
	 * argument to the function. By default Summed Distribution option is not visible.
	 * Once this method is called it makes the Summed Dist. option available
	 * to user.
	 * @param toShow boolean
	 */
	public void makeSumDistVisible(boolean toShow){
		this.jCheckSumDist.setVisible(toShow);
	}


	/**
	 * This function is called when Summed distribution box is clicked
	 *
	 * @param e
	 */
	void jCheckSumDist_actionPerformed(ActionEvent e) {

		if(jCheckSumDist.isSelected()) {
			magDistEditor.setSummedDistPlotted(true);
			// if user wants a summed distribution
			double min = magDistEditor.getMin();
			double max = magDistEditor.getMax();
			int num = magDistEditor.getNum();
			// make the new object of summed distribution
			summedMagFreqDist = new  SummedMagFreqDist(min,max,num);

			// add all the existing distributions to the summed distribution
			int size = incrRateFunctionList.size();

			try {
				for(int i=0; i < size; ++i)
					summedMagFreqDist.addIncrementalMagFreqDist((IncrementalMagFreqDist)incrRateFunctionList.get(i));
			}catch(Exception ex) {
				JOptionPane.showMessageDialog(this,
						"min, max, and num must be the same to sum the distributions"
				);
				jCheckSumDist.setSelected(false);

				return;
			}

			// now we will do work so that we can put summed distribuiton to top of functionlist
			insertSummedDistribution();

		}
		// if summed distribution needs to be removed
		else {
			magDistEditor.setSummedDistPlotted(false);
			if(incrRateFunctionList.size()>0){
				// remove the summed distribution and related moment rate and cumulative rate
				incrRateFunctionList.remove(incrRateFunctionList.size() - 1);
				cumRateFunctionList.remove(cumRateFunctionList.size() - 1);
				momentRateFunctionList.remove(momentRateFunctionList.size() - 1);
				//removing the plotting features from the plot prefs. for the summed distribution
				List<PlotCurveCharacterstics> incrPlotFeaturesList = incrRateGraphPanel.getPlottingFeatures();
				List<PlotCurveCharacterstics> cumPlotFeaturesList = cumRateGraphPanel.getPlottingFeatures();
				List<PlotCurveCharacterstics> momentPlotFeaturesList = momentRateGraphPanel.getPlottingFeatures();
				incrPlotFeaturesList.remove(incrPlotFeaturesList.size() - 1);
				cumPlotFeaturesList.remove(cumPlotFeaturesList.size() - 1);
				momentPlotFeaturesList.remove(momentPlotFeaturesList.size() - 1);
			}
		}
		addGraphPanel();
	}


	/**
	 *  Adds a feature to the GraphPanel attribute of the EqkForecastApplet object
	 */
	private void addGraphPanel() {

		// Starting
		String S = ": addGraphPanel(): ";
		
		PlotSpec incrSpec = incrRateGraphPanel.getPlotSpec();
		incrSpec.setXAxisLabel(incrRateXAxisName);
		incrSpec.setYAxisLabel(incrRateYAxisName);
		incrSpec.setPlotElems(incrRateFunctionList);
		incrSpec.setTitle(incrRatePlotTitle);
		incrRateGraphPanel.drawGraph();
		
		PlotSpec cumSpec = cumRateGraphPanel.getPlotSpec();
		cumSpec.setXAxisLabel(cumRateXAxisName);
		cumSpec.setYAxisLabel(cumRateYAxisName);
		cumSpec.setPlotElems(cumRateFunctionList);
		cumSpec.setTitle(cumRatePlotTitle);
		cumRateGraphPanel.drawGraph();
		
		PlotSpec momentSpec = momentRateGraphPanel.getPlotSpec();
		momentSpec.setXAxisLabel(momentRateXAxisName);
		momentSpec.setYAxisLabel(momentRateYAxisName);
		momentSpec.setPlotElems(momentRateFunctionList);
		momentSpec.setTitle(momentRatePlotTitle);
		momentRateGraphPanel.drawGraph();
	}


	/**
	 * private function to insert the summed distribtuion to function list
	 * It first makes the clone of the original function list
	 * Then clears the original function list and then adds summed distribtuion to
	 * the top of the original function list and then adds other distributions
	 *
	 */
	private void insertSummedDistribution() {
		// add the summed distribution to the list
		incrRateFunctionList.add(summedMagFreqDist);
		cumRateFunctionList.add(summedMagFreqDist.getCumRateDist());
		momentRateFunctionList.add(summedMagFreqDist.getMomentRateDist());
		String metadata = "\n"+( (EvenlyDiscretizedFunc) incrRateFunctionList.get(incrRateFunctionList.size()-1)).getInfo()+"\n";
		for (int i = 0; i < incrRateFunctionList.size()-1; ++i)
			metadata += (i+1)+")"+( (EvenlyDiscretizedFunc) incrRateFunctionList.get(i)).getInfo()+ "\n";

		magDistEditor.setMagDistFromParams(summedMagFreqDist, metadata);

		addGraphPanel();
		//adding the plotting features to the sum distribution because this will
		//allow to create the default color scheme first then can change for the
		//sum distribution
		List<PlotCurveCharacterstics> incrPlotFeaturesList = incrRateGraphPanel.getPlottingFeatures();
		List<PlotCurveCharacterstics> cumPlotFeaturesList = cumRateGraphPanel.getPlottingFeatures();
		List<PlotCurveCharacterstics> momentPlotFeaturesList = momentRateGraphPanel.getPlottingFeatures();
		incrPlotFeaturesList.set(incrPlotFeaturesList.size() -1,new PlotCurveCharacterstics(PlotLineType.SOLID,
				1f, null, 4f, Color.BLACK));
		cumPlotFeaturesList.set(incrPlotFeaturesList.size() -1,new PlotCurveCharacterstics(PlotLineType.SOLID,
				1f, null, 4f, Color.BLACK));
		momentPlotFeaturesList.set(incrPlotFeaturesList.size() -1,new PlotCurveCharacterstics(PlotLineType.SOLID,
				1f, null, 4f, Color.BLACK));
		addGraphPanel();
	}

	/**
	 *
	 * @param e ChangeEvent
	 */
	private void plotTabPane_stateChanged(ChangeEvent e){
		JTabbedPane pane = (JTabbedPane)e.getSource();
		int index = pane.getSelectedIndex();
		if(index == 0){
			isCumRatePlot = false;
			isIncrRatePlot = true;
			isMomentRatePlot = false;
		}
		else if(index ==1){
			isCumRatePlot = true;
			isIncrRatePlot = false;
			isMomentRatePlot = false;
		}
		else if(index ==2){
			isCumRatePlot = false;
			isIncrRatePlot = false;
			isMomentRatePlot = true;
		}
	}


	/**
	 * this function is called when "Add Dist" button is clicked
	 * @param e
	 */
	void addButton_actionPerformed(ActionEvent e) {
		addButton();
	}


	/**
	 * This causes the model data to be calculated and a plot trace added to
	 * the current plot
	 *
	 * @param  e  The feature to be added to the Button_mouseClicked attribute
	 */
	private void addButton() {

		if (D)
			System.out.println("Starting");

		try {
			if(magDistEditor instanceof MagFreqDistParameterEditor)
				magDistEditor.setSummedDistPlotted(false);
			this.magDistEditor.setMagDistFromParams();

			String magDistMetadata = magDistEditor.getMagFreqDistParameterEditor().
			getVisibleParametersCloned().getParameterListMetadataString();

			IncrementalMagFreqDist function = (IncrementalMagFreqDist)this.
			magDistEditor.getParameter().getValue();
			magDistMetadata += "\n\n"+function.getInfo(); // this adds parameter values, which will include any solved for, which the next does not
			function.setInfo(magDistMetadata);
			function.setName("Incremental MFD");
			if (D)
				System.out.println(" after getting mag dist from editor");
			EvenlyDiscretizedFunc cumRateFunction;
			EvenlyDiscretizedFunc moRateFunction;

			// get the cumulative rate and moment rate distributions for this function
			cumRateFunction = (EvenlyDiscretizedFunc) function.getCumRateDist();
			cumRateFunction.setInfo(magDistMetadata);
			cumRateFunction.setName("Cumulative MFD");
			moRateFunction = (EvenlyDiscretizedFunc) function.getMomentRateDist();
			moRateFunction.setInfo(magDistMetadata);
			moRateFunction.setName("Moment Rate Distribution");
			int size = incrRateFunctionList.size();
			//if the number of functions is 1 more then numFunctionsWithoutSumDist
			//then summed has been added ealier so needs to be removed
			if (size == numFunctionsWithoutSumDist + 1) {
				incrRateFunctionList.remove(incrRateFunctionList.size() - 1);
				cumRateFunctionList.remove(cumRateFunctionList.size() - 1);
				momentRateFunctionList.remove(momentRateFunctionList.size() - 1);

				//removing the plotting features from the plot prefs. for the summed distribution
				List<PlotCurveCharacterstics> incrPlotFeaturesList = incrRateGraphPanel.getPlottingFeatures();
				List<PlotCurveCharacterstics> cumPlotFeaturesList = cumRateGraphPanel.getPlottingFeatures();
				List<PlotCurveCharacterstics> momentPlotFeaturesList = momentRateGraphPanel.getPlottingFeatures();
				incrPlotFeaturesList.remove(incrPlotFeaturesList.size() - 1);
				cumPlotFeaturesList.remove(cumPlotFeaturesList.size() - 1);
				momentPlotFeaturesList.remove(momentPlotFeaturesList.size() - 1);
			}

			// add the functions to the functionlist
			incrRateFunctionList.add( (EvenlyDiscretizedFunc) function);
			cumRateFunctionList.add(cumRateFunction);
			momentRateFunctionList.add(moRateFunction);
			numFunctionsWithoutSumDist = momentRateFunctionList.size();

			if (jCheckSumDist.isVisible() && jCheckSumDist.isSelected()) { // if summed distribution is selected, add to summed distribution
				try {
					magDistEditor.setSummedDistPlotted(true);
					double min = magDistEditor.getMin();
					double max = magDistEditor.getMax();
					int num = magDistEditor.getNum();

					if(summedMagFreqDist == null)
						summedMagFreqDist = new SummedMagFreqDist(min,max,num);
					// add this distribution to summed distribution
					summedMagFreqDist.addIncrementalMagFreqDist(function);

					// this function will insert summed distribution at top of function list
					insertSummedDistribution();

				}
				catch (Exception ex) {
					JOptionPane.showMessageDialog(this,
							"min, max, and num must be the same to sum the distributions." +
							"\n To add this distribution first deselect the Summed Dist option"
					);
					return;
				}
			}
			// draw the graph
			addGraphPanel();

			// catch the error and display messages in case of input error
		}
		catch (NumberFormatException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this,
					new String("Enter a Valid Numerical Value"),
					"Invalid Data Entered",
					JOptionPane.ERROR_MESSAGE);
		}
		catch (NullPointerException e) {
			e.printStackTrace();
			//JOptionPane.showMessageDialog(this,new String(e.getMessage()),"Data Not Entered",JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, new String(e.getMessage()),
					"Invalid Data Entered",
					JOptionPane.ERROR_MESSAGE);
		}

		if (D)
			System.out.println("Ending");

	}


	/**
	 * this function is called when "clear plot" is selected
	 *
	 * @param e
	 */
	void clearButton_actionPerformed(ActionEvent e) {
		clearPlot(true);
	}

	/**
	 *  Clears the plot screen of all traces
	 */
	private void clearPlot(boolean clearFunctions) {

		if ( D )
			System.out.println( "Clearing plot area" );

		int loc = mainSplitPane.getDividerLocation();
		int newLoc = loc;

		if( clearFunctions){
			incrRateGraphPanel.removeChartAndMetadata();
			cumRateGraphPanel.removeChartAndMetadata();
			momentRateGraphPanel.removeChartAndMetadata();
			// these should not be called as it will hide further plots from being displayed
			// alternatively you could call add() again later, but it is easier to simply not remove.
			// this is more consistant with the look and feel when launching the application anyway.
//			incrRatePlotPanel.removeAll();
//			cumRatePlotPanel.removeAll();
//			momentRatePlotPanel.removeAll();

			//panel.removeAll();
			incrRateFunctionList.clear();
			cumRateFunctionList.clear();
			momentRateFunctionList.clear();
			summedMagFreqDist = null;
		}

		mainSplitPane.setDividerLocation( newLoc );
	}



	/**
	 * File | Exit action performed.
	 *
	 * @param actionEvent ActionEvent
	 */
	private void fileSaveMenu_actionPerformed(ActionEvent actionEvent) {
		try {
			save();
		}
		catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Save File Error",
					JOptionPane.OK_OPTION);
			return;
		}
	}

	/**
	 * File | Exit action performed.
	 *
	 * @param actionEvent ActionEvent
	 */
	private void filePrintMenu_actionPerformed(ActionEvent actionEvent) {
		print();
	}

	/**
	 * Opens a file chooser and gives the user an opportunity to save the chart
	 * in PNG format.
	 *
	 * @throws IOException if there is an I/O error.
	 */
	public void save() throws IOException {
		if(isIncrRatePlot)
			incrRateGraphPanel.save();
		else if(isCumRatePlot)
			cumRateGraphPanel.save();
		else if(isMomentRatePlot)
			momentRateGraphPanel.save();
	}

	/**
	 * Creates a print job for the chart.
	 */
	public void print() {
		if(isIncrRatePlot)
			incrRateGraphPanel.print();
		else if(isCumRatePlot)
			cumRateGraphPanel.print();
		else if(isMomentRatePlot)
			momentRateGraphPanel.print();
	}


	/**
	 * Actual method implementation of the "Peel-Off"
	 * This function peels off the window from the current plot and shows in a new
	 * window. The current plot just shows empty window.
	 */
	private void peelOffCurves(){
		GraphWidget graphWidget;
		if (isCumRatePlot)
			graphWidget = cumRateGraphPanel;
		else if (isIncrRatePlot)
			graphWidget = incrRateGraphPanel;
		else
			graphWidget = momentRateGraphPanel;
		GraphWindow graphWindow = new GraphWindow(graphWidget);
		graphWidget.getPlotSpec().setPlotElems(Lists.newArrayList(graphWidget.getPlotSpec().getPlotElems()));
		graphWindow.setVisible(true);
	}


	/**
	 * Action method to "Peel-Off" the curves graph window in a seperate window.
	 * This is called when the user presses the "Peel-Off" window.
	 * @param e
	 */
	void peelOffButton_actionPerformed(ActionEvent e) {
		peelOffCurves();
	}


	/**
	 * File | Exit action performed.
	 *
	 * @param actionEvent ActionEvent
	 */
	private void fileExitMenu_actionPerformed(ActionEvent actionEvent) {
		close();
	}

	/**
	 *
	 */
	private void close() {
		int option = JOptionPane.showConfirmDialog(this,
				"Do you really want to exit the application?\n" +
				"You will loose all unsaved data.",
				"Exit App",
				JOptionPane.OK_CANCEL_OPTION);
		if (option == JOptionPane.OK_OPTION)
			System.exit(0);
	}

	public void closeButton_actionPerformed(ActionEvent actionEvent) {
		close();
	}

	public void printButton_actionPerformed(ActionEvent actionEvent) {
		print();
	}

	public void saveButton_actionPerformed(ActionEvent actionEvent) {
		try {
			save();
		}
		catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Save File Error",
					JOptionPane.OK_OPTION);
			return;
		}
	}

	public void parameterChange(ParameterChangeEvent event) {
		String paramName = event.getParameterName();
		if(paramName.equals(this.MAG_DIST_PARAM_SELECTOR_NAME)){
			createMagParam();
		}
	}
}
