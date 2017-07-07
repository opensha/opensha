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

package org.opensha.sha.imr.attenRelImpl.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.border.Border;

import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.gui.DisclaimerDialog;
import org.opensha.commons.gui.HelpMenuBuilder;
import org.opensha.commons.gui.plot.AxisLimitsControlPanel;
import org.opensha.commons.gui.plot.ButtonControlPanel;
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotColorAndLineTypeSelectorControlPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeFailEvent;
import org.opensha.commons.param.event.ParameterChangeFailListener;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.BrowserUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.gcim.imr.attenRelImpl.BommerEtAl_2009_AttenRel;
import org.opensha.sha.gcim.imr.attenRelImpl.CB_2010_CAV_AttenRel;
import org.opensha.sha.gcim.imr.attenRelImpl.KS_2006_AttenRel;
import org.opensha.sha.gcim.imr.attenRelImpl.ASI_WrapperAttenRel.BA_2008_ASI_AttenRel;
import org.opensha.sha.gcim.imr.attenRelImpl.DSI_WrapperAttenRel.BA_2008_DSI_AttenRel;
import org.opensha.sha.gcim.imr.attenRelImpl.SI_WrapperAttenRel.BA_2008_SI_AttenRel;
import org.opensha.sha.gui.controls.CurveDisplayAppAPI;
import org.opensha.sha.gui.controls.XY_ValuesControlPanel;
import org.opensha.sha.gui.util.IconFetcher;
import org.opensha.sha.imr.attenRelImpl.AS_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.AS_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Abrahamson_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BA_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BC_2004_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BJF_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BS_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CS_2005_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Campbell_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Field_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.GouletEtAl_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.McVerryetal_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SEA_1999_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SadighEtAl_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.ShakeMap_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SA_InterpolatedWrapperAttenRel.InterpolatedBA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.ASK_2014_Wrapper;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.BSSA_2014_Wrapper;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.CB_2014_Wrapper;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.CY_2014_Wrapper;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.Idriss_2014_Wrapper;
import org.opensha.sha.imr.mod.impl.stewartSiteSpecific.NonErgodicSiteResponseGMPE;

import com.google.common.collect.Lists;

/**
 *  <b>Title:</b> AttenuationRelationshipApplet<br>
 *  <b>Description:</b> Applet that allows testing of independent and dependent
 *  parameters in AttenuationRelationships. You can plot the standard deviation, mean, and
 *  exceedence probability by setting all independent parameters values except
 *  the one choosen for the x-axis. The x-axis is generated by the constraints
 *  of the choosen independent variable for the x-axis. This applet doesn't
 *  require a Site nor a Potential Earthquake, which are normaly necessary to
 *  calculate all the independent parameters. The purpose of the Applet is to
 *  test implemented AttenuationRelationships at it's simplest level.<br>
 *
 * @author     Steven W. Rock
 * @created    April 17, 2002
 * @see        BJF_1997_AttenRel
 * @see        AS_1997_AttenRel
 * @version    1.0
 */

public class AttenuationRelationshipApplet extends JFrame
implements ParameterChangeFailListener, ParameterChangeWarningListener, ItemListener, CurveDisplayAppAPI {
	
	public static final String APP_NAME = "Attenuation Relationship Application";
	public static final String APP_SHORT_NAME = "AttenuationRelationship";
	
	private static final String GUIDE_URL = "http://www.opensha.org/guide-AttenuationRelationship";
	
	private static ApplicationVersion version;

	protected final static String C = "AttenuationRelationshipApplet";
	protected final static boolean D = false;

	// message string to be dispalayed if user chooses Axis Scale
	// without first clicking on "Add Trace"
	private final static String AXIS_RANGE_NOT_ALLOWED =
		new String("First Choose Add Graph. Then choose Axis Scale option");

	//instance of the GraphPanel (window that shows all the plots)
	protected GraphWidget graphWidget;

	//images for the OpenSHA
	private final static String FRAME_ICON_NAME = "openSHA_Aqua_sm.gif";
	protected final static String POWERED_BY_IMAGE = "logos/PoweredByOpenSHA_Agua.jpg";

	//static string for the OPENSHA website
	private final static String OPENSHA_WEBSITE="http://www.OpenSHA.org";

	private JButton attenRelInfobutton = new JButton("  Get Info  ");
	
	/**
	 *  Currently selected AttenuationRelationship and related information needed for the gui to
	 *  work
	 */
	AttenuationRelationshipGuiBean attenRel = null;

	/**
	 *  List that contains the lazy instantiation of attenRels via reflection and the
	 *  attenRel full class names
	 */
	protected AttenuationRelationshipGuiList attenRels = new AttenuationRelationshipGuiList();


	/**
	 * List of ArbitrarilyDiscretized functions and Weighted funstions
	 */
	private ArrayList<PlotElement> functionList = new ArrayList<PlotElement>();

	protected boolean inParameterChangeWarning = false;

	boolean isStandalone = false;

	// Plot panel insets

	Insets plotInsets = new Insets( 4, 10, 4, 4 );
	Insets defaultInsets = new Insets( 4, 4, 4, 4 );
	Insets emptyInsets = new Insets( 0, 0, 0, 0 );

	protected final static int W = 900;
	protected final static int H = 730;
	protected final static Font BUTTON_FONT = new java.awt.Font( "Dialog", 1, 11 );
	protected final static Font TITLE_FONT = new java.awt.Font( "Dialog", Font.BOLD, 12 );

	/**
	 *  Min number of data points where if you have less in a Discretized
	 *  Function, the points are drawn with symbols, else just a smooth line in
	 *  drawn
	 */
	protected final static int MIN_NUMBER_POINTS = 15;

	//Adding the Menu to the application
	JMenuBar menuBar = new JMenuBar();
	JMenu helpMenu;
	JMenu fileMenu = new JMenu();

	JMenuItem fileExitMenu = new JMenuItem();
	JMenuItem fileSaveMenu = new JMenuItem();
	JMenuItem filePrintMenu = new JCheckBoxMenuItem();
	JToolBar jToolBar = new JToolBar();

	JButton closeButton = new JButton();
	ImageIcon closeFileImage = new ImageIcon(FileUtils.loadImage("icons/closeFile.png"));

	JButton printButton = new JButton();
	ImageIcon printFileImage = new ImageIcon(FileUtils.loadImage("icons/printFile.jpg"));

	JButton saveButton = new JButton();
	ImageIcon saveFileImage = new ImageIcon(FileUtils.loadImage("icons/saveFile.jpg"));


	//boolean to check if the plot preferences to be used to draw the curves
	private boolean drawCurvesUsingPlotPrefs;

	/**
	 *  ArrayList that maps picklist attenRel string names to the real fully qualified
	 *  class names
	 */
	protected static ArrayList<String> attenRelClasses = new ArrayList<String>();
	protected static ArrayList<String> imNames = new ArrayList<String>();


	/**
	 *  NED - Here is where you can add the new AttenuationRelationshipS, follow my examples below
	 *  Populates the attenRels hashmap with the strings in the picklist for the
	 *  applet mapped to the class names of the attenRels. This will use the class
	 *  loader to load these
	 */
	static {
		imNames.add(org.opensha.sha.imr.attenRelImpl.ngaw2.ASK_2014.NAME);
		attenRelClasses.add(ASK_2014_Wrapper.class.getName());
		imNames.add(org.opensha.sha.imr.attenRelImpl.ngaw2.BSSA_2014.NAME);
		attenRelClasses.add(BSSA_2014_Wrapper.class.getName());
		imNames.add(org.opensha.sha.imr.attenRelImpl.ngaw2.CB_2014.NAME);
		attenRelClasses.add(CB_2014_Wrapper.class.getName());
		imNames.add(org.opensha.sha.imr.attenRelImpl.ngaw2.CY_2014.NAME);
		attenRelClasses.add(CY_2014_Wrapper.class.getName());
		imNames.add(org.opensha.sha.imr.attenRelImpl.ngaw2.Idriss_2014.NAME);
		attenRelClasses.add(Idriss_2014_Wrapper.class.getName());
		imNames.add(InterpolatedBA_2008_AttenRel.NAME);
		attenRelClasses.add(InterpolatedBA_2008_AttenRel.class.getName());
		imNames.add(CY_2008_AttenRel.NAME);
		attenRelClasses.add(CY_2008_AttenRel.class.getName());
		imNames.add(AS_2008_AttenRel.NAME);
		attenRelClasses.add(AS_2008_AttenRel.class.getName());
		imNames.add(CB_2008_AttenRel.NAME);
		attenRelClasses.add(CB_2008_AttenRel.class.getName());
		imNames.add(BA_2008_AttenRel.NAME);
		attenRelClasses.add(BA_2008_AttenRel.class.getName());
		imNames.add(CY_2006_AttenRel.NAME);
		attenRelClasses.add(CY_2006_AttenRel.class.getName());
		imNames.add(CB_2006_AttenRel.NAME);
		attenRelClasses.add(CB_2006_AttenRel.class.getName());
		imNames.add(BA_2006_AttenRel.NAME);
		attenRelClasses.add(BA_2006_AttenRel.class.getName());
		imNames.add(CS_2005_AttenRel.NAME);
		attenRelClasses.add(CS_2005_AttenRel.class.getName());
		imNames.add(BJF_1997_AttenRel.NAME);
		attenRelClasses.add(BJF_1997_AttenRel.class.getName());
		imNames.add(AS_1997_AttenRel.NAME);
		attenRelClasses.add(AS_1997_AttenRel.class.getName());
		imNames.add(Campbell_1997_AttenRel.NAME);
		attenRelClasses.add(Campbell_1997_AttenRel.class.getName());
		imNames.add(SadighEtAl_1997_AttenRel.NAME);
		attenRelClasses.add(SadighEtAl_1997_AttenRel.class.getName());
		imNames.add(Field_2000_AttenRel.NAME);
		attenRelClasses.add(Field_2000_AttenRel.class.getName());
		imNames.add(Abrahamson_2000_AttenRel.NAME);
		attenRelClasses.add(Abrahamson_2000_AttenRel.class.getName());
		imNames.add(CB_2003_AttenRel.NAME);
		attenRelClasses.add(CB_2003_AttenRel.class.getName());
		imNames.add(BS_2003_AttenRel.NAME);
		attenRelClasses.add(BS_2003_AttenRel.class.getName());
		imNames.add(BC_2004_AttenRel.NAME);
		attenRelClasses.add(BC_2004_AttenRel.class.getName());
		imNames.add(GouletEtAl_2006_AttenRel.NAME);
		attenRelClasses.add(GouletEtAl_2006_AttenRel.class.getName());
		imNames.add(ShakeMap_2003_AttenRel.NAME);
		attenRelClasses.add(ShakeMap_2003_AttenRel.class.getName());
		imNames.add(SEA_1999_AttenRel.NAME);
		attenRelClasses.add(SEA_1999_AttenRel.class.getName());
//		imNames.add(ToroEtAl_1997_AttenRel.NAME);
//		attenRelClasses.add(ToroEtAl_1997_AttenRel.class.getName());
		//    	imNames.add(GouletEtAl_2010_AttenRel.NAME);
		//    	attenRelClasses.add(GOULET_2010_CLASS_NAME);
//		imNames.add(BS_2003b_AttenRel.NAME);
//		attenRelClasses.add(BS_2003b_AttenRel.class.getName());
		imNames.add(McVerryetal_2000_AttenRel.NAME);
		attenRelClasses.add(McVerryetal_2000_AttenRel.class.getName());
		imNames.add(BA_2008_SI_AttenRel.NAME);
		attenRelClasses.add(BA_2008_SI_AttenRel.class.getName());
		imNames.add(BA_2008_ASI_AttenRel.NAME);
		attenRelClasses.add(BA_2008_ASI_AttenRel.class.getName());
		imNames.add(BA_2008_DSI_AttenRel.NAME);
		attenRelClasses.add(BA_2008_DSI_AttenRel.class.getName());
		imNames.add(CB_2010_CAV_AttenRel.NAME);
		attenRelClasses.add(CB_2010_CAV_AttenRel.class.getName());
		imNames.add(KS_2006_AttenRel.NAME);
		attenRelClasses.add(KS_2006_AttenRel.class.getName());
		imNames.add(BommerEtAl_2009_AttenRel.NAME);
		attenRelClasses.add(BommerEtAl_2009_AttenRel.class.getName());
		imNames.add(NonErgodicSiteResponseGMPE.NAME);
		attenRelClasses.add(NonErgodicSiteResponseGMPE.class.getName());

		//imNames.add( DAHLE_NAME, DAHLE_CLASS_NAME );

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {}
	}



	/**
	 *  Used to determine if shoudl switch to new AttenuationRelationship, and for display purposes
	 */
	public String currentAttenuationRelationshipName = "";


	//setting the legend string
	protected String legend=null;

	//variable to check whether to clear the existing plot or not
	boolean newGraph = false;

	private final static String AUTO_SCALE = "Auto Scale";
	private final static String CUSTOM_SCALE = "Custom Scale";
	final static Dimension COMBO_DIM = new Dimension( 170, 30 );
	final static Dimension BUTTON_DIM = new Dimension( 80, 20 );
	final static String NO_PLOT_MSG = "No Plot Data Available";
	Color darkBlue = new Color( 80, 80, 133 );
	Color lightBlue = new Color( 200, 200, 230 );
	final static GridBagLayout GBL = new GridBagLayout();
	Color background = Color.white;
	JPanel outerPanel = new JPanel();
	JPanel outerControlPanel = new JPanel();
	JPanel mainPanel = new JPanel();
	JPanel titlePanel = new JPanel();
	JPanel plotPanel = new JPanel();
	JPanel innerPlotPanel = new JPanel();
	//JLabel titleLabel = new JLabel();
	JPanel controlPanel = new JPanel();
	JButton clearButton = new JButton();
	JButton addButton = new JButton();
	JPanel parametersPanel = new JPanel();
	JPanel buttonPanel = new JPanel();
	JPanel inputPanel = new JPanel();
	JPanel sheetPanel = new JPanel();
	JSplitPane parametersSplitPane = new JSplitPane();
	JSplitPane mainSplitPane = new JSplitPane();
	JSplitPane plotSplitPane =  new JSplitPane();

	protected String lastXYAxisName = "";

	JComboBox attenRelComboBox = new JComboBox();
	JLabel attenRelLabel = new JLabel();
	protected javax.swing.JFrame frame;

	JCheckBox plotColorCheckBox = new JCheckBox();

	boolean isWhite = true;

	protected AxisLimitsControlPanel axisLimits;


	/**
	 * for Y-log, 0 values will be converted to this small value
	 */
	private double Y_MIN_VAL = 1e-8;
	protected JLabel imgLabel = new JLabel();

	protected Border border1;
	protected FlowLayout flowLayout1 = new FlowLayout();
	protected JButton xyDatasetButton = new JButton();

	//XY new Dataset control
	protected XY_ValuesControlPanel xyNewDatasetControl;
	protected JButton peelOffButton = new JButton();

	/**
	 * Returns the Application version
	 * @return ApplicationVersion
	 */
	public static ApplicationVersion getAppVersion(){
		if (version == null) {
			try {
				version = ApplicationVersion.loadBuildVersion();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return version;
	}

	/**
	 *  Gets the currentAttenuationRelationshipName attribute of the AttenuationRelationshipApplet object
	 *
	 * @return    The currentAttenuationRelationshipName value
	 */
	public String getCurrentAttenuationRelationshipName() {
		return currentAttenuationRelationshipName;
	}

	/**
	 *  Get Applet information
	 *
	 * @return    The appletInfo value
	 */
	public String getAppInfo() {
		return "Attenuation Relationship Plotter";
	}



	/**
	 *  Pops up a JFileChooser to set the filename to save the plot data to.
	 *  Contains special case for Windows systems to choose the C:\\ as the root
	 *  path, otherwise use the first root path. This function should work on
	 *  all operating systems.
	 *
	 * @return    The fileFromUser value
	 */
	private File getFileFromUser() {
		JFileChooser fc = new JFileChooser();

		// use current directory

		File[] roots = File.listRoots();
		String path = roots[0].getAbsolutePath();

		for ( int i = 0; i < roots.length; i++ ) {

			String path1 = roots[i].getAbsolutePath();
			//if(D) System.out.println("Path: " + path);

			if ( path1.startsWith( "C:" ) )
				path = path1;

		}

		fc.setCurrentDirectory( new File( path ) );

		// set default name
		fc.setSelectedFile( new File( "data.txt" ) );

		// show dialog for opening files
		int result = fc.showSaveDialog( this );

		if ( result != fc.APPROVE_OPTION )
			return null;

		return fc.getSelectedFile();
	}

	/**
	 *  Initialize the applet
	 */
	public void init() {

		// initialize the current AttenuationRelationship
		initAttenuationRelationshipGui();

		try {
			jbInit();
		}
		catch ( Exception e ) {
			e.printStackTrace();
		}
	}

	/**
	 *  THis must be called before the AttenuationRelationship is used. This is what initializes the
	 *  AttenuationRelationship
	 */
	private void initAttenuationRelationshipGui() {

		// starting
		String S = C + ": initAttenuationRelationshipGui(): ";
		if ( this.imNames.size() < 1 )
			throw new RuntimeException( S + "No AttenuationRelationships specified, unable to continue" );

		boolean first = true;
		String firstImr = "";
		Iterator it = this.imNames.iterator();
		while ( it.hasNext() )

			if ( first ) {
				first = false;
				String val = it.next().toString();
				attenRelComboBox.addItem( val );
				attenRelComboBox.setSelectedItem( val );
				firstImr = val;
			}
			else
				attenRelComboBox.addItem( it.next().toString() );


		// This one line calls alot of code, including reflection,
		// init all coefficients, attenRel, and editors
		// attenRels.setImr(firstImr, this);

	}


	/**
	 *  Component initialization
	 *
	 * @exception  Exception  Description of the Exception
	 */
	protected void jbInit() throws Exception {

		String S = C + ": jbInit(): ";


		border1 = BorderFactory.createLineBorder(new Color(80, 80, 133),2);
		this.setFont( new java.awt.Font( "Dialog", 0, 10 ) );
		this.setSize(new Dimension(900, 690) );
		this.getContentPane().setLayout( new BorderLayout());
		outerPanel.setLayout( GBL );
		mainPanel.setBorder(border1 );
		mainPanel.setLayout( GBL );
		titlePanel.setMinimumSize(new Dimension(40, 40));
		titlePanel.setPreferredSize(new Dimension(40, 40));
		titlePanel.setLayout( GBL);
		//creating the Object the GraphPaenl class
		graphWidget = new GraphWidget();

		plotPanel.setLayout(GBL);
		innerPlotPanel.setLayout(GBL);
		innerPlotPanel.setBorder( null );
		controlPanel.setLayout(GBL);
		controlPanel.setBorder(BorderFactory.createEtchedBorder(1));
		outerControlPanel.setLayout(GBL);

		attenRelInfobutton.setToolTipText("Gets the information for the selected AttenuationRelationship model");


		clearButton.setText( "Clear Plot" );

		clearButton.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(ActionEvent e){
						clearButton_actionPerformed(e);
					}
				}
		);

		addButton.setText( "Add Curve" );

		addButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				addButton_actionPerformed(e);
			}
		});

		buttonPanel.setLayout(flowLayout1 );


		parametersSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		parametersSplitPane.setBorder( null );
		parametersSplitPane.setDividerSize( 5 );

		mainSplitPane.setOrientation( JSplitPane.HORIZONTAL_SPLIT );
		mainSplitPane.setBorder( null );
		mainSplitPane.setDividerSize( 2 );

		plotSplitPane.setOrientation( JSplitPane.VERTICAL_SPLIT );
		plotSplitPane.setBorder( null );
		plotSplitPane.setDividerSize( 2 );

		plotSplitPane.setBottomComponent( buttonPanel );
		plotSplitPane.setTopComponent(mainPanel );
		plotSplitPane.setDividerLocation(500 );


		attenRelLabel.setForeground( darkBlue );
		attenRelLabel.setFont(new java.awt.Font( "Dialog", Font.BOLD, 13 ));
		attenRelLabel.setText( "Choose Model:    " );


		attenRelComboBox.setFont( new java.awt.Font( "Dialog", Font.BOLD, 16 ) );


		attenRelComboBox.addItemListener( this );


		plotColorCheckBox.setText("Black Background");

		plotColorCheckBox.addItemListener( this );

		//setting the layout for the Parameters panels
		parametersPanel.setLayout( GBL );
		controlPanel.setLayout( GBL );
		sheetPanel.setLayout( GBL );
		inputPanel.setLayout( GBL );

		//loading the OpenSHA Logo
		imgLabel.setText("");
		imgLabel.setIcon(new ImageIcon(FileUtils.loadImage(this.POWERED_BY_IMAGE)));

		xyDatasetButton.setText("Add Data Points");
		xyDatasetButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				xyDatasetButton_actionPerformed(e);
			}
		});
		peelOffButton.setText("Peel Off");
		peelOffButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				peelOffButton_actionPerformed(e);
			}
		});
		this.getContentPane().add( outerPanel, BorderLayout.CENTER);

		outerPanel.add( plotSplitPane,         new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
				,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 5, 0, 5), 0, 0) );

		titlePanel.add( this.attenRelLabel, new GridBagConstraints( 0, 0 , 1, 1, 1.0, 0.0
				, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, emptyInsets, 0, 0 ) );


		titlePanel.add( this.attenRelComboBox, new GridBagConstraints( 1, 0 , 1, 1, 1.0, 0.0
				, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, emptyInsets, 0, 0 ) );

		titlePanel.add(attenRelInfobutton, new GridBagConstraints( 2, 0 , 1, 1, 1.0, 0.0
				, GridBagConstraints.EAST, GridBagConstraints.NONE, emptyInsets, 4, 0 ) );


		mainPanel.add( mainSplitPane, new GridBagConstraints( 0, 1, 1, 1, 1.0, 1.0
				, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 2, 4, 4, 4 ), 0, 0 ) );


		controlPanel.add(parametersPanel, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
				, GridBagConstraints.CENTER, GridBagConstraints.BOTH, emptyInsets, 0, 0 ) );

		outerControlPanel.add(controlPanel, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
				, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 5, 0, 0 ), 0, 0 ) );

		parametersPanel.add( parametersSplitPane, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
				, GridBagConstraints.CENTER, GridBagConstraints.BOTH, emptyInsets, 0, 0 ) );

		plotPanel.add( titlePanel, new GridBagConstraints( 0, 0, 1, 1, 1.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets( 4, 4, 2, 4 ), 0, 0 ) );


		plotPanel.add( innerPlotPanel, new GridBagConstraints( 0, 1, 1, 1, 1.0, 1.0
				, GridBagConstraints.CENTER, GridBagConstraints.BOTH, defaultInsets, 0, 0 ) );

		attenRelInfobutton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				attenRelInfobutton_actionPerformed(e);
			}
		});


		//object for the ButtonControl Panel
		buttonPanel.add(addButton, 0);
		buttonPanel.add(clearButton, 1);
		buttonPanel.add(peelOffButton, 2);
		buttonPanel.add(xyDatasetButton, 3);
		buttonPanel.add(plotColorCheckBox, 4);
		buttonPanel.add(imgLabel, 5);

		parametersSplitPane.setBottomComponent( sheetPanel );
		parametersSplitPane.setTopComponent( inputPanel );
		parametersSplitPane.setDividerLocation(220 );

		parametersSplitPane.setOneTouchExpandable( false );

		mainSplitPane.setBottomComponent( outerControlPanel );
		mainSplitPane.setTopComponent(plotPanel );
		mainSplitPane.setDividerLocation(630 );

		//frame.setTitle( applet.getAppletInfo() + ":  [" + applet.getCurrentAttenuationRelationshipName() + ']' );
		setTitle( this.getAppInfo() + " (Version:"+getAppVersion()+")");
		setSize( W, H );
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation( ( d.width - getSize().width ) / 2, ( d.height - getSize().height ) / 2 );




		fileMenu.setText("File");
		fileExitMenu.setText("Exit");
		fileSaveMenu.setText("Save");
		filePrintMenu.setText("Print");
		//adding the Menu to the application
		helpMenu = createHelpMenu();


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


		menuBar.add(fileMenu,0);
		menuBar.add(helpMenu,1);
		fileMenu.add(fileSaveMenu);
		fileMenu.add(filePrintMenu);
		fileMenu.add(fileExitMenu);

		setJMenuBar(menuBar);
		closeButton.setIcon(closeFileImage);
		closeButton.setToolTipText("Exit Application");
		Dimension d1 = closeButton.getSize();
		jToolBar.add(closeButton);
		printButton.setIcon(printFileImage);
		printButton.setToolTipText("Print Graph");
		printButton.setSize(d1);
		jToolBar.add(printButton);
		saveButton.setIcon(saveFileImage);
		saveButton.setToolTipText("Save Graph as image");
		saveButton.setSize(d1);
		jToolBar.add(saveButton);
		jToolBar.setFloatable(false);

		this.getContentPane().add(jToolBar, BorderLayout.NORTH);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		updateChoosenAttenuationRelationship();
		createHelpMenu();
		// Big function here, sets all the AttenuationRelationship stuff and puts in sheetsPanel and
		// inputsPanel
		
		enableMenuButtons();
		
		innerPlotPanel.removeAll();
		innerPlotPanel.add(graphWidget, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
				, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ));
		innerPlotPanel.validate();
		innerPlotPanel.repaint();

		this.setVisible( true );
	}

	private JMenu createHelpMenu(){
		HelpMenuBuilder builder = new HelpMenuBuilder(APP_NAME, APP_SHORT_NAME, getAppVersion(), this);
		
		builder.setGuideURL(GUIDE_URL);
		return builder.buildMenu();
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
		graphWidget.save();
	}

	/**
	 * Creates a print job for the chart.
	 */
	public void print() {
		graphWidget.print();
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



	/**
	 *  Main method
	 *
	 * @param  args  The command line arguments
	 * @throws MalformedURLException 
	 */
	public static void main( String[] args ) throws MalformedURLException, IOException {
		new DisclaimerDialog(APP_NAME, APP_SHORT_NAME, getAppVersion());
		launch();
	}
	
	public static AttenuationRelationshipApplet launch() {
		AttenuationRelationshipApplet applet = new AttenuationRelationshipApplet();
		applet.init();
		applet.setIconImages(IconFetcher.fetchIcons(APP_SHORT_NAME));
		applet.setVisible(true);
		return applet;
	}



	/**
	 *  Used for synch applet with new AttenuationRelationship choosen. Updates lables and
	 *  initializes the AttenuationRelationship if needed.
	 */
	protected void updateChoosenAttenuationRelationship() {

		// Starting
		String S = C + ": updateChoosenAttenuationRelationship(): ";

		String choice = attenRelComboBox.getSelectedItem().toString();

		if ( choice.equals( currentAttenuationRelationshipName ) )
			return;

		if ( D )
			System.out.println( S + "Starting: New AttenuationRelationship = " + choice );

		// Clear the current traces
		//Plot needs to be cleared only if X or Y axis are changed, not otherwise
		if(newGraph)  clearPlot(true);
		int currentSelectedAttenRelIndex = this.imNames.indexOf(choice);
		try {
			attenRel = attenRels.setImr( currentSelectedAttenRelIndex, this );
		} catch (Exception e) {
			attenRelComboBox.setSelectedIndex(imNames.indexOf(currentAttenuationRelationshipName));
			throw new RuntimeException("Failed to load Attenuation Relationship: " + choice, e);
		}
		currentAttenuationRelationshipName = choice;

		sheetPanel.removeAll();
		sheetPanel.add( attenRel.getIndependentsEditor(),
				new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
						, GridBagConstraints.CENTER, GridBagConstraints.BOTH, defaultInsets, 0, 0 )
		);

		inputPanel.removeAll();
		ParameterListEditor controlsEditor = attenRel.getControlsEditor();

		if ( D )
			System.out.println( S + "Controls = " + controlsEditor.getParameterList().toString() );

		inputPanel.add( controlsEditor,
				new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
						, GridBagConstraints.CENTER, GridBagConstraints.BOTH, defaultInsets, 0, 0 )
		);

		validate();
		repaint();

		// Ending
		if ( D )  System.out.println( S + "Ending" );

	}
	
	private void enableMenuButtons() {
		boolean enableSavePrint = functionList != null && functionList.size() > 0;
		saveButton.setEnabled(enableSavePrint);
		fileSaveMenu.setEnabled(enableSavePrint);
		printButton.setEnabled(enableSavePrint);
		filePrintMenu.setEnabled(enableSavePrint);
	}

	/**
	 *  Adds a feature to the GraphPanel attribute of the AttenuationRelationshipApplet object
	 */
	private void addGraphPanel() {
		PlotSpec spec = graphWidget.getPlotSpec();
		spec.setPlotElems(functionList);
		graphWidget.drawGraph();
		
		if (isWhite)
			graphWidget.getGraphPanel().setPlotBackgroundColor(Color.white);
		else
			graphWidget.getGraphPanel().setPlotBackgroundColor(Color.black);
		
		enableMenuButtons();
	}

	/**
	 * plots the curves with defined color,line width and shape.
	 *
	 */
	public void plotGraphUsingPlotPreferences(){
		addGraphPanel();
	}


	private void clearButton(){
		clearPlot( true );
		attenRel.refreshParamEditor();
	}

	/**
	 *  Clears the plot screen of all traces
	 */
	private void clearPlot(boolean clearFunctions) {

		if ( D )
			System.out.println( "Clearing plot area" );

		int loc = this.mainSplitPane.getDividerLocation();
		int newLoc = loc;
		if (clearFunctions) {
			functionList.clear();
			//clearing the plotting preferences
			graphWidget.getPlottingFeatures().clear();
		}
		graphWidget.removeChartAndMetadata();
		graphWidget.setAutoRange();
		mainSplitPane.setDividerLocation( newLoc );
		enableMenuButtons();
	}

	/**
	 *  Shown when a Constraint error is thrown on a ParameterEditor
	 *
	 * @param  e  Description of the Parameter
	 */
	public void parameterChangeFailed( ParameterChangeFailEvent e ) {

		String S = C + " : parameterChangeWarning(): ";
		if(D) System.out.println(S + "Starting");

		if(inParameterChangeWarning) return;
		inParameterChangeWarning = true;


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
		inParameterChangeWarning = false;
		if(D) System.out.println(S + "Ending");
	}

	/**
	 *  Function that must be implemented by all Listeners for
	 *  ParameterChangeWarnEvents.
	 *
	 * @param  event  The Event which triggered this function call
	 */
	public void parameterChangeWarning( ParameterChangeWarningEvent e ){

		String S = C + " : parameterChangeWarning(): ";
		if(D) System.out.println(S + "Starting");
		if(this.inParameterChangeWarning) return;

		inParameterChangeWarning = true;

		StringBuffer b = new StringBuffer();

		WarningParameter param = e.getWarningParameter();


		try{
			Double min = (Double)param.getWarningMin();
			Double max = (Double)param.getWarningMax();

			String name = param.getName();

			b.append( "You have exceeded the recommended range\n");
			b.append( name );
			b.append( ": (" );
			b.append( min.toString() );

			b.append( " to " );
			b.append( max.toString() );
			b.append( ")\n" );
			b.append( "Click Yes to accept the new value: " );
			b.append( e.getNewValue().toString() );
		}
		catch( Exception ee){

			String name = param.getName();
			b.append( "You have exceeded the recommended range for: \n");
			b.append( name + '\n' );
			b.append( "Click Yes to accept the new value: " );
			b.append( e.getNewValue().toString() );
			b.append( name );


		}
		if(D) System.out.println(S + b.toString());

		int result = 0;

		if(D) System.out.println(S + "Showing Dialog");

		result = JOptionPane.showConfirmDialog( this, b.toString(),
				"Exceeded Recommended Values", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

		if(D) System.out.println(S + "You choose " + result);

		switch (result) {
		case JOptionPane.YES_OPTION:
			if(D) System.out.println(S + "You choose yes, changing value to " + e.getNewValue().toString() );
			param.setValueIgnoreWarning( e.getNewValue() );
			break;
		case JOptionPane.NO_OPTION:
			if(D) System.out.println(S + "You choose no, keeping value = " + e.getOldValue().toString() );
			param.setValueIgnoreWarning( e.getOldValue() );
			break;
		default:
			param.setValueIgnoreWarning( e.getOldValue() );
			if(D) System.out.println(S + "Not sure what you choose, not changing value.");
			break;
		}
		inParameterChangeWarning = false;
		if(D) System.out.println(S + "Ending");
	}

	protected void addButton_actionPerformed(ActionEvent e) {
		String S = C + " : addButton_actionPerformed";
		if(D) System.out.println(S + "Starting");
		addButton();
		if(D) System.out.println(S + "Ending");
	}

	/**
	 *  This causes the model data to be calculated and a plot trace added to
	 *  the current plot
	 *
	 * @param  e  The feature to be added to the Button_mouseClicked attribute
	 */
	private void addButton(){

		String S = C + ": addButton(): ";
		if ( D ) System.out.println( S + "Starting" );
		if ( D ) System.out.println( S + "Controls = " + this.attenRel.controlsEditor.getParameterList().toString() );


		String XLabel = (String)attenRel.getControlsEditor().getParameterList().getParameter(attenRel.X_AXIS_NAME).getValue();
		String YLabel = (String)attenRel.getControlsEditor().getParameterList().getParameter(attenRel.Y_AXIS_NAME).getValue();

		//if the user just wants to see the result for the single value.
		if(XLabel.equals(AttenuationRelationshipGuiBean.X_AXIS_SINGLE_VAL)){
			functionList.clear();
			graphWidget.getPlottingFeatures().clear();
			//making the GUI components disable if the user wants just one single value
			clearButton.setEnabled(false);
			graphWidget.getButtonControlPanel().setEnabled(false);
			plotColorCheckBox.setEnabled(false);
			double yVal = attenRel.getChosenValue();
			String info = "";
			info = "AttenuationRelationship Name: " + attenRel.getAttenRel().getName()+"\n\n";
			info += "Intensity Measure Type: "+(String)attenRel.getSelectedIMParam().getValue()+"\n\n";
			info += "Info: "+attenRel.getIndependentsEditor().getVisibleParametersCloned().toString()+"\n\n";
			info += YLabel+" = "+yVal;
			//making the panel for the JFreechart null, so that it only shows the indivdual value
			graphWidget.removeChartAndMetadata();
			graphWidget.getGraphPanel().setMetadata(info);
			return;
		}

		//if the user want to plot the curve
		else{
			//enabling all the GUI components if the user wants to see the plot
			clearButton.setEnabled(true);
			graphWidget.getButtonControlPanel().setEnabled(true);
			plotColorCheckBox.setEnabled(true);

			if( D && functionList != null ){
				ListIterator it = functionList.listIterator();
				while( it.hasNext() ){

					DiscretizedFunc func = (DiscretizedFunc)it.next();
					if ( D ) System.out.println( S + "Func info = " + func.getInfo() );

				}
			}
			DiscretizedFunc function =null;
			try{

				/**
				 * This block of the code has been put under the try and catch becuase
				 * if any of the Attenuation Relationships throws any Exception , it can be caought here
				 * and displayed back to the user. Currently it handles the CB-2003 excetion thrown
				 * if BC Boundry is selected for the Vertical component.
				 */
				function = attenRel.getChoosenFunction();
			}catch(RuntimeException e){
				e.printStackTrace();
				JOptionPane.showMessageDialog(this,e.getMessage(),"Incorrect Parameter Input",JOptionPane.ERROR_MESSAGE);
				return;
			}



			if ( D ) System.out.println( S + "New Function info = " + function.getInfo() );

			//if( D && functionList != null ){
			//ListIterator it = functionList.listIterator();
			//while( it.hasNext() ){

			//DiscretizedFuncAPI func = (DiscretizedFuncAPI)it.next();
			//if ( D ) System.out.println( S + "Func info = " + func.getInfo() );

			// }
			//}

			//data.setXLog(xLog);
			//data.setYLog(yLog);

			String xOld = graphWidget.getXAxisLabel();
			if (xOld == null)
				xOld = "";
			String xUnitsOld="";
			if(xOld.indexOf('(')!=-1)
				xUnitsOld = xOld.substring(xOld.indexOf('(')+1, xOld.indexOf(')'));
			String yOld = graphWidget.getYAxisLabel();
			if (yOld == null)
				yOld = "";

			String xNew = attenRel.getGraphXAxisLabel();
			String xUnitsNew ="";
			if(xNew.indexOf('(')!=-1)
				xUnitsNew = xNew.substring(xNew.indexOf('(')+1, xNew.indexOf(')'));
			String yNew = attenRel.getGraphIMYAxisLabel();

			newGraph = false;

			// only clear graph if units differ on X axis
			if( xUnitsNew.equals(xUnitsOld)  && !xUnitsNew.equals("") && !xUnitsOld.equals("")) {
				String tempX = xNew.substring(0, xNew.indexOf('('));
				if(xOld.indexOf(tempX)==-1) { // set the new X axis label
					xNew=xOld.substring(0, xOld.indexOf('('))+" "+xNew.substring(0, xNew.indexOf('('))+
					" ("+ xUnitsNew+")";
					graphWidget.setXAxisLabel(xNew);
				}
			}
			//if the X-Axis units are null for both old and new
			else if(xUnitsNew.equals(xUnitsOld))
				newGraph = false;
			else
				newGraph = true;

			if( !yOld.equals(yNew) ) newGraph = true;

			if( newGraph ){
				functionList.clear();
				graphWidget.getPlottingFeatures().clear();
				graphWidget.setXAxisLabel(attenRel.getGraphXAxisLabel());
				graphWidget.setYAxisLabel(attenRel.getGraphIMYAxisLabel());
			}

			newGraph = false;



			if( !functionList.contains( function ) ){
				if ( D ) System.out.println( S + "AddjAttenuationRelationshipListing new function" );
				functionList.add(function);
			}
			else {

				if(D) System.out.println(S + "Showing Dialog");
				if( !this.inParameterChangeWarning ){

					JOptionPane.showMessageDialog(
							null, "This graph already exists, will not add again.",
							"Cannot Add", JOptionPane.INFORMATION_MESSAGE
					);
				}


				if ( D ) System.out.println( S + "Function already exists in graph, not adding .." );
				return;
			}

			attenRel.refreshParamEditor();
			addGraphPanel();
			if ( D ) System.out.println( S + "Ending" );

		}

	}

	/**
	 * Sets ArbitraryDiscretizedFunc inside list containing all the functions.
	 * @param function ArbitrarilyDiscretizedFunc
	 */
	public void addCurve(ArbitrarilyDiscretizedFunc function){
		if( !functionList.contains( function )){
			functionList.add(function);
			List<PlotCurveCharacterstics> plotFeaturesList = graphWidget.getPlottingFeatures();
			plotFeaturesList.add(new PlotCurveCharacterstics(null, 1f, PlotSymbol.CROSS, 4f,
					Color.BLACK,1));
			addGraphPanel();
		}
		else
			JOptionPane.showMessageDialog(null, "This graph already exists, will not add again.",
					"Cannot Add", JOptionPane.INFORMATION_MESSAGE);
	}


	protected void clearButton_actionPerformed(ActionEvent e){

		String S = C + " : clearButtonFocusGained(): ";
		if(D) System.out.println(S + "Starting");
		clearButton();
		if(D) System.out.println(S + "Ending");

	}

	/**
	 * Actual method implementation of the "Peel-Off"
	 * This function peels off the window from the current plot and shows in a new
	 * window. The current plot just shows empty window.
	 */
	private void peelOffCurves(){
		graphWidget.getPlotSpec().setPlotElems(Lists.newArrayList(graphWidget.getPlotSpec().getPlotElems()));
		GraphWindow graphWindow = new GraphWindow(graphWidget);
		graphWidget = new GraphWidget();
		clearPlot(true);
		innerPlotPanel.removeAll();
		graphWidget.togglePlot();
		innerPlotPanel.add(graphWidget, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
				, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ));
		innerPlotPanel.validate();
		innerPlotPanel.repaint();
		graphWindow.setVisible(true);
	}


	/**
	 *
	 * @return the List for all the ArbitrarilyDiscretizedFunctions and Weighted Function list.
	 */
	public ArrayList getCurveFunctionList(){
		return functionList;
	}



	/**
	 *  Description of the Method
	 *
	 * @param  e  Description of the Parameter
	 */
	public void itemStateChanged( ItemEvent e ) {

		// Starting
		String S = C + ": itemStateChanged(): ";
		if ( D ) System.out.println( S + "Starting" );

		if ( e.getSource().equals( attenRelComboBox ) ){
			// this.customAxis =false;
			try {
				updateChoosenAttenuationRelationship();
			} catch (Exception e1) {
				e1.printStackTrace();
				JOptionPane.showMessageDialog(this, e1.getMessage());
			}
		}
		else if( e.getSource().equals( plotColorCheckBox ) ){

			if( isWhite ) {
				isWhite = false;
				graphWidget.getGraphPanel().setPlotBackgroundColor(Color.black);
			}
			else{
				isWhite = true;
				graphWidget.getGraphPanel().setPlotBackgroundColor(Color.white);
			}
		}

		// Ending
		if ( D ) System.out.println( S + "Ending" );

	}

	/**
	 *
	 * @param e ActionEvent
	 */
	void xyDatasetButton_actionPerformed(ActionEvent e) {
		if(xyNewDatasetControl == null)
			xyNewDatasetControl = new XY_ValuesControlPanel(this,this);

		xyNewDatasetControl.showControlPanel();// getComponent().setVisible(true);

	}

	void peelOffButton_actionPerformed(ActionEvent e) {
		peelOffCurves();
	}

	void attenRelInfobutton_actionPerformed(ActionEvent e) {
		try {
			URL url = attenRel.getInfoURL();
			if(url == null){
				JOptionPane.showMessageDialog(this, "No information exists for the selected Attenuation Relationship");
				return;
			}
			BrowserUtils.launch(url);
		} catch (Exception e1) {
			JOptionPane.showMessageDialog(this, "No information exists for the selected Attenuation Relationship");
			return;
		}
	}


	@Override
	public String getSelectedIMT() {
		return this.attenRel.getSelectedIMParam().getName();
	}


	@Override
	public void setCurveXValues(ArbitrarilyDiscretizedFunc func) {
		throw new RuntimeException("Not applicable for application");
	}


	@Override
	public void setCurveXValues() {
		throw new RuntimeException("Not applicable for application");
	}
}
