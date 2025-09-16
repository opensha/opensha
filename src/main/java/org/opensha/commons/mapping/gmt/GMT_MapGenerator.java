package org.opensha.commons.mapping.gmt;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;

import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.GMT_Map.HighwayFile;
import org.opensha.commons.mapping.gmt.elements.CoastAttributes;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.mapping.gmt.elements.PSText;
import org.opensha.commons.mapping.gmt.elements.PSXYPolygon;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbol;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbolSet;
import org.opensha.commons.mapping.gmt.elements.TopographicSlopeFile;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.CPTParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ListUtils;
import org.opensha.commons.util.RunScript;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.cybershake.maps.GMT_InterpolationSettings;

import com.google.common.base.Preconditions;

/**
 * <p>Title: GMT_MapGenerator</p>
 * <p>Description: This class generates Maps using the java wrapper around GMT</p>
 * @author: Ned Field, Nitin Gupta, & Vipin Gupta
 * @created:Dec 21,2002
 * @version 1.0
 */

public class GMT_MapGenerator implements SecureMapGenerator, Serializable {

	/**
	 * Name of the class
	 */
	protected final static String C = "GMT_MapGenerator";

	// for debug purpose
	protected final static boolean D = false;

	// name of the file which contains all the GMT commands that we want to run on server
	protected final static String DEFAULT_GMT_SCRIPT_NAME = "map_GMT_Script.txt";
	protected String GMT_SCRIPT_NAME = DEFAULT_GMT_SCRIPT_NAME;
	protected final static String DEFAULT_XYZ_FILE_NAME = "map_data.txt";
	protected String XYZ_FILE_NAME = DEFAULT_XYZ_FILE_NAME;
	protected final static String DEFAULT_METADATA_FILE_NAME = "map_info.html";
	protected String METADATA_FILE_NAME = DEFAULT_METADATA_FILE_NAME;
	protected final static String DEFAULT_PS_FILE_NAME = "map.ps";
	protected String PS_FILE_NAME = DEFAULT_PS_FILE_NAME;
	public final static String DEFAULT_JPG_FILE_NAME = "map.jpg";
	protected String JPG_FILE_NAME = DEFAULT_JPG_FILE_NAME;
	public final static String DEFAULT_PNG_FILE_NAME = "map.png";
	protected String PNG_FILE_NAME = DEFAULT_PNG_FILE_NAME;
	protected final static String DEFAULT_PDF_FILE_NAME = "map.pdf";
	protected String PDF_FILE_NAME = DEFAULT_PDF_FILE_NAME;


	protected String SCALE_LABEL; // what's used to label the color scale

	/*				opensha.usc.edu paths				*/
//	public static final String OPENSHA_GMT_PATH="/usr/local/GMT4.2.1/bin/";
	public static final String OPENSHA_GMT_PATH="/usr/bin/gmt "; // needs the space after
	public static final String OPENSHA_GS_PATH="/usr/bin/gs";
	public static final String OPENSHA_PS2PDF_PATH = "/usr/bin/ps2pdf";
	public static final String OPENSHA_CONVERT_PATH="/usr/bin/convert";
	public static final String OPENSHA_GMT_DATA_PATH =
			ServerPrefUtils.SERVER_PREFS.getDataDir().getAbsolutePath()+File.separator+"gmt"+File.separator;
	public static final String OPENSHA_SERVLET_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "GMT_MapGeneratorServlet";
	public static final String OPENSHA_JAVA_PATH = "/usr/bin/java";
	public static final String OPENSHA_CLASSPATH = ServerPrefUtils.SERVER_PREFS.getTomcatDir().getAbsolutePath()
			+File.separator+"lib"+File.separator+"opensha-dev-all.jar";
//	public static final String OPENSHA_NETCDF_LIB_PATH="/usr/local/netCDF/lib/";
	public static final String OPENSHA_NETCDF_LIB_PATH=null; // libraries are in /usr/lib64/ so hopefully not needed

	// this is the path where general data (e.g., topography) are found:
	public static String GMT_DATA_PATH = OPENSHA_GMT_DATA_PATH;
	private static String SERVLET_URL = OPENSHA_SERVLET_URL;
	public static String JAVA_PATH = OPENSHA_JAVA_PATH;
	public static String JAVA_CLASSPATH = OPENSHA_CLASSPATH;
	
	// paths to needed code
	protected static String GMT_PATH = OPENSHA_GMT_PATH;
	protected static String GS_PATH = OPENSHA_GS_PATH;
	protected static String CONVERT_PATH = OPENSHA_CONVERT_PATH;
	protected static String PS2PDF_PATH = OPENSHA_PS2PDF_PATH;
	protected static String NETCDF_LIB_PATH = OPENSHA_NETCDF_LIB_PATH;
	protected static String COMMAND_PATH = "/bin/";

	protected GeoDataSet xyzDataSet;

	// for map boundary parameters
	public final static String MIN_LAT_PARAM_NAME = "Min Latitude";
	public final static String MAX_LAT_PARAM_NAME = "Max Latitude";
	public final static String MIN_LON_PARAM_NAME = "Min Longitude";
	public final static String MAX_LON_PARAM_NAME = "Max Longitude";
	public final static String GRID_SPACING_PARAM_NAME = "Grid Spacing";
	private final static String LAT_LON_PARAM_UNITS = "Degrees";
	private final static String LAT_LON_PARAM_INFO = "Corner point of mapped region";
	private final static String GRID_SPACING_PARAM_INFO = "Grid interval in the Region";
	private final static Double MIN_LAT_PARAM_DEFAULT = Double.valueOf(20);
	private final static Double MAX_LAT_PARAM_DEFAULT = Double.valueOf(60);
	private final static Double MIN_LON_PARAM_DEFAULT = Double.valueOf(-130);
	private final static Double MAX_LON_PARAM_DEFAULT = Double.valueOf(-60.0);
	private final static Double GRID_SPACING_PARAM_DEFAULT = Double.valueOf(.1);
	DoubleParameter minLatParam;
	DoubleParameter maxLatParam;
	DoubleParameter minLonParam;
	DoubleParameter maxLonParam;
	DoubleParameter gridSpacingParam;

	// for the final image width:
	public final static String IMAGE_WIDTH_NAME = "Image Width";
	private final static String IMAGE_WIDTH_UNITS = "inches";
	private final static String IMAGE_WIDTH_INFO = "Width of the final jpg image (ps file width is always 8.5 inches)";
	private final static double IMAGE_WIDTH_MIN = 1.0;
	private final static double IMAGE_WIDTH_MAX = 20.0;
	protected final static Double IMAGE_WIDTH_DEFAULT = Double.valueOf(6.5);
	DoubleParameter imageWidthParam;

//	public final static String CPT_FILE_PARAM_NAME = "Color Scheme";
//	protected final static String CPT_FILE_PARAM_DEFAULT = "MaxSpectrum.cpt";
//	private final static String CPT_FILE_PARAM_INFO = "Color scheme for the scale";
//	public final static String CPT_FILE_MAX_SPECTRUM = "MaxSpectrum.cpt";
//	public final static String CPT_FILE_STEP = "STEP.cpt";
//	public final static String CPT_FILE_SHAKEMAP = "Shakemap.cpt";
//	public final static String CPT_FILE_RELM = "relm_color_map.cpt";
//	public final static String CPT_FILE_GMT_POLAR = "GMT_polar.cpt";
//	StringParameter cptFileParam;
	
	public static final String CPT_PARAM_NAME = "Color Scheme";
	protected final static GMT_CPT_Files CPT_PARAM_DEFAULT = GMT_CPT_Files.MAX_SPECTRUM;
	private final static String CPT_PARAM_INFO = "Color scheme for the scale";
	private CPTParameter cptParam;
	

	public final static String COAST_PARAM_NAME = "Coast";
	public final static String COAST_DRAW = "Draw Boundary";
	public final static String COAST_FILL = "Draw & Fill";
	public final static String COAST_NONE = "Draw Nothing";
	protected final static String COAST_DEFAULT = COAST_FILL;
	private final static String COAST_PARAM_INFO = "Specifies how bodies of water are drawn";
	StringParameter coastParam;


	// auto versus manual color scale setting
	public final static String COLOR_SCALE_MODE_NAME = "Color Scale Limits";
	public final static String COLOR_SCALE_MODE_INFO = "Set manually or from max/min of the data";
	public final static String COLOR_SCALE_MODE_MANUALLY = "Manually";
	public final static String COLOR_SCALE_MODE_FROMDATA = "From Data";
	public final static String COLOR_SCALE_MODE_DEFAULT = COLOR_SCALE_MODE_FROMDATA;
	StringParameter colorScaleModeParam;

	// for color scale limits
	public final static String COLOR_SCALE_MIN_PARAM_NAME = "Color-Scale Min";
	private final static Double COLOR_SCALE_MIN_PARAM_DEFAULT = Double.valueOf(-2.);
	private final static String COLOR_SCALE_MIN_PARAM_INFO = "Lower limit on color scale (values below are the same color)";
	public final static String COLOR_SCALE_MAX_PARAM_NAME = "Color-Scale Max";
	private final static Double COLOR_SCALE_MAX_PARAM_DEFAULT = Double.valueOf(-0.5);
	private final static String COLOR_SCALE_MAX_PARAM_INFO = "Upper limit on color scale (values above are the same color)";
	DoubleParameter colorScaleMaxParam;
	DoubleParameter colorScaleMinParam;

	// DPI
	public final static String DPI_PARAM_NAME = "DPI";
	private final static String DPI_PARAM_INFO = "Dots per inch for PS file";
	protected final static Integer DPI_DEFAULT = Integer.valueOf(72);
	private final static Integer DPI_MIN = Integer.valueOf(0);
	private final static Integer DPI_MAX = Integer.valueOf(Integer.MAX_VALUE);
	private IntegerParameter dpiParam;

	// Apply GMT smoothing
	public final static String GMT_SMOOTHING_PARAM_NAME = "Apply GMT Smoothing?";
	private final static String GMT_SMOOTHING_PARAM_INFO = "Apply GMT Smoothing (if false, Topo Resolution is set to none)?";
	protected final static boolean GMT_SMOOTHING_DEFAULT = true;
	private BooleanParameter gmtSmoothingParam; 

	// Apply GMT smoothing
	public final static String GRD_VIEW_PARAM_NAME = "Use grdview instead of grdimage?";
	private final static String GRD_VIEW_PARAM_INFO = "Uses the grdview command instead of grdimage when smoothing and topography are disabled."
			+ " This is slower, but looks better for extremely high resolution maps.";
	protected final static boolean GRD_VIEW_DEFAULT = false;
	private BooleanParameter grdViewParam; 


	// Apply GMT smoothing
	public final static String BLACK_BACKGROUND_PARAM_NAME = "Apply Black Background?";
	private final static String BLACK_BACKGROUND_PARAM_INFO = "Otherwise background will be white";
	protected final static boolean BLACK_BACKGROUND_PARAM_DEFAULT = true;
	private BooleanParameter blackBackgroundParam; 


	// shaded relief resolution
	public final static String TOPO_RESOLUTION_PARAM_NAME = "Topo Resolution";
	private final static String TOPO_RESOLUTION_PARAM_UNITS = "arc-sec";
	private final static String TOPO_RESOLUTION_PARAM_INFO = "Resolution of the shaded relief";
	public final static String TOPO_RESOLUTION_03_CA = "03 sec California";
	public final static String TOPO_RESOLUTION_06_US = "06 sec US";
	public final static String TOPO_RESOLUTION_18_US = "18 sec US";
	public final static String TOPO_RESOLUTION_30_US = "30 sec US";
	public final static String TOPO_RESOLUTION_30_GLOBAL = "30 sec Global";
	protected final static String TOPO_RESOLUTION_PARAM_DEFAULT = TOPO_RESOLUTION_30_GLOBAL;
	public final static String TOPO_RESOLUTION_NONE = "No Topo";
	StringParameter topoResolutionParam;

	// highways to plot parameter
	public final static String SHOW_HIWYS_PARAM_NAME = "Highways in plot";
	public final static String SHOW_HIWYS_PARAM_DEFAULT = "None";
	public final static String SHOW_HIWYS_PARAM_INFO = "Select the highways you'd like to be shown";
	public final static String SHOW_HIWYS_ALL = "ca_hiwys.all.xy";
	public final static String SHOW_HIWYS_MAIN = "ca_hiwys.main.xy";
	public final static String SHOW_HIWYS_OTHER = "ca_hiwys.other.xy";
	public final static String SHOW_HIWYS_NONE = "None";
	StringParameter showHiwysParam;

	//Boolean parameter to see if user wants GMT from the GMT webservice
	public  final static String GMT_WEBSERVICE_NAME = "Use GMT WebService";
	private final static String GMT_WEBSERVICE_INFO= "Use server-mode GMT (rather than on this computer)";
	BooleanParameter gmtFromServer;

	//Boolean parameter to see if user wants linear or log plot
	public final static String LOG_PLOT_NAME = "Plot Log";
	private final static String LOG_PLOT_INFO = "Plot Log or Linear Map";
	protected final static boolean LOG_PLOT_PARAM_DEFAULT = true;
	protected BooleanParameter logPlotParam;


	//Boolean parameter to see if user wants to give a custom Label
	public final static String CUSTOM_SCALE_LABEL_PARAM_CHECK_NAME = "Custom Scale Label";
	private final static String CUSTOM_SCALE_LABEL_PARAM_CHECK_INFO = "Allows to give a custom scale label to the map";
	protected BooleanParameter customScaleLabelCheckParam;


	public final static String SCALE_LABEL_PARAM_NAME = "Scale Label";
	private final static String SCALE_LABEL_PARAM_INFO = "Map Scale Label(Don't give any brackets in label)";
	protected StringParameter scaleLabelParam;
	
	
	public final static String KML_PARAM_NAME = "Generate KML Files";
	private final static Boolean KML_PARAM_DEFAULT = false;
	private BooleanParameter kmlParam;


	protected ParameterList adjustableParams;

	//GMT files web address(if the person is using the gmt webService)
	protected String imgWebAddr=null;



	public GMT_MapGenerator() {

		minLatParam = new DoubleParameter(MIN_LAT_PARAM_NAME,-90,90,LAT_LON_PARAM_UNITS,MIN_LAT_PARAM_DEFAULT);
		minLatParam.setInfo(LAT_LON_PARAM_INFO);
		maxLatParam = new DoubleParameter(MAX_LAT_PARAM_NAME,-90,90,LAT_LON_PARAM_UNITS,MAX_LAT_PARAM_DEFAULT);
		minLatParam.setInfo(LAT_LON_PARAM_INFO);
		minLonParam = new DoubleParameter(MIN_LON_PARAM_NAME,-360,360,LAT_LON_PARAM_UNITS,MIN_LON_PARAM_DEFAULT);
		minLatParam.setInfo(LAT_LON_PARAM_INFO);
		maxLonParam = new DoubleParameter(MAX_LON_PARAM_NAME,-360,360,LAT_LON_PARAM_UNITS,MAX_LON_PARAM_DEFAULT);
		minLatParam.setInfo(LAT_LON_PARAM_INFO);
		gridSpacingParam = new DoubleParameter(GRID_SPACING_PARAM_NAME,0.001,100,LAT_LON_PARAM_UNITS,GRID_SPACING_PARAM_DEFAULT);
		minLatParam.setInfo(GRID_SPACING_PARAM_INFO);

		imageWidthParam = new DoubleParameter(IMAGE_WIDTH_NAME,IMAGE_WIDTH_MIN,IMAGE_WIDTH_MAX,IMAGE_WIDTH_UNITS,IMAGE_WIDTH_DEFAULT);
		imageWidthParam.setInfo(IMAGE_WIDTH_INFO);
		
		ArrayList<CPT> cpts = null;
		try {
			cpts = GMT_CPT_Files.instances();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		cptParam = new CPTParameter(CPT_PARAM_NAME, cpts,
				cpts.get(ListUtils.getIndexByName(cpts, CPT_PARAM_DEFAULT.getFileName())));
		cptParam.setInfo(CPT_PARAM_INFO);

		StringConstraint coastConstraint = new StringConstraint();
		coastConstraint.addString(COAST_FILL);
		coastConstraint.addString(COAST_DRAW);
		coastConstraint.addString(COAST_NONE);
		coastParam = new StringParameter(COAST_PARAM_NAME,coastConstraint,COAST_DEFAULT );
		coastParam.setInfo(COAST_PARAM_INFO);

		StringConstraint colorScaleModeConstraint = new StringConstraint();
		colorScaleModeConstraint.addString( COLOR_SCALE_MODE_FROMDATA );
		colorScaleModeConstraint.addString( COLOR_SCALE_MODE_MANUALLY );
		colorScaleModeParam = new StringParameter( COLOR_SCALE_MODE_NAME, colorScaleModeConstraint, COLOR_SCALE_MODE_DEFAULT );
		colorScaleModeParam.setInfo( COLOR_SCALE_MODE_INFO );

		colorScaleMinParam = new DoubleParameter(COLOR_SCALE_MIN_PARAM_NAME, COLOR_SCALE_MIN_PARAM_DEFAULT);
		colorScaleMinParam.setInfo(COLOR_SCALE_MIN_PARAM_INFO);

		colorScaleMaxParam = new DoubleParameter(COLOR_SCALE_MAX_PARAM_NAME, COLOR_SCALE_MAX_PARAM_DEFAULT);
		colorScaleMaxParam.setInfo(COLOR_SCALE_MAX_PARAM_INFO);

		StringConstraint topoResolutionConstraint = new StringConstraint();
		topoResolutionConstraint.addString( TOPO_RESOLUTION_30_GLOBAL );
		topoResolutionConstraint.addString( TOPO_RESOLUTION_30_US );
		topoResolutionConstraint.addString( TOPO_RESOLUTION_18_US );
		topoResolutionConstraint.addString( TOPO_RESOLUTION_06_US );
		topoResolutionConstraint.addString( TOPO_RESOLUTION_03_CA );
		topoResolutionConstraint.addString( TOPO_RESOLUTION_NONE );
		topoResolutionParam = new StringParameter( TOPO_RESOLUTION_PARAM_NAME, topoResolutionConstraint,TOPO_RESOLUTION_PARAM_UNITS, TOPO_RESOLUTION_PARAM_DEFAULT );
		topoResolutionParam.setInfo( TOPO_RESOLUTION_PARAM_INFO );


		StringConstraint showHiwysConstraint = new StringConstraint();
		showHiwysConstraint.addString( SHOW_HIWYS_NONE );
		showHiwysConstraint.addString( SHOW_HIWYS_ALL );
		showHiwysConstraint.addString( SHOW_HIWYS_MAIN );
		showHiwysConstraint.addString( SHOW_HIWYS_OTHER );
		showHiwysParam = new StringParameter( SHOW_HIWYS_PARAM_NAME, showHiwysConstraint, SHOW_HIWYS_PARAM_DEFAULT );
		showHiwysParam.setInfo( SHOW_HIWYS_PARAM_INFO );

		gmtFromServer = new BooleanParameter(GMT_WEBSERVICE_NAME,Boolean.valueOf("true"));
		gmtFromServer.setInfo(GMT_WEBSERVICE_INFO);

		logPlotParam = new BooleanParameter(LOG_PLOT_NAME, LOG_PLOT_PARAM_DEFAULT);
		logPlotParam.setInfo(LOG_PLOT_INFO);


		customScaleLabelCheckParam = new BooleanParameter(CUSTOM_SCALE_LABEL_PARAM_CHECK_NAME,Boolean.valueOf(false));
		customScaleLabelCheckParam.setInfo(CUSTOM_SCALE_LABEL_PARAM_CHECK_INFO);

		scaleLabelParam = new StringParameter(SCALE_LABEL_PARAM_NAME,"");
		scaleLabelParam.setInfo(SCALE_LABEL_PARAM_INFO);

		// DPI param
		this.dpiParam = new IntegerParameter(DPI_PARAM_NAME, DPI_MIN, DPI_MAX, DPI_DEFAULT);
		dpiParam.setInfo(DPI_PARAM_INFO);

		// whether to apply GMT smoothing
		this.gmtSmoothingParam = new BooleanParameter(GMT_SMOOTHING_PARAM_NAME, GMT_SMOOTHING_DEFAULT);
		gmtSmoothingParam.setInfo(GMT_SMOOTHING_PARAM_INFO);

		// whether to apply GMT smoothing
		this.grdViewParam = new BooleanParameter(GRD_VIEW_PARAM_NAME, GRD_VIEW_DEFAULT);
		grdViewParam.setInfo(GRD_VIEW_PARAM_INFO);

		// whether to apply GMT smoothing
		this.blackBackgroundParam = new BooleanParameter(BLACK_BACKGROUND_PARAM_NAME, BLACK_BACKGROUND_PARAM_DEFAULT);
		blackBackgroundParam.setInfo(BLACK_BACKGROUND_PARAM_INFO);

		// whether to apply GMT smoothing
		this.kmlParam = new BooleanParameter(KML_PARAM_NAME, KML_PARAM_DEFAULT);


		// create adjustable parameter list
		adjustableParams = new ParameterList();

		adjustableParams.addParameter(minLatParam);
		adjustableParams.addParameter(maxLatParam);
		adjustableParams.addParameter(minLonParam);
		adjustableParams.addParameter(maxLonParam);
		adjustableParams.addParameter(gridSpacingParam);
		adjustableParams.addParameter(cptParam);
		adjustableParams.addParameter(colorScaleModeParam);
		adjustableParams.addParameter(colorScaleMinParam);
		adjustableParams.addParameter(colorScaleMaxParam);
		adjustableParams.addParameter(topoResolutionParam);
		adjustableParams.addParameter(showHiwysParam);
		adjustableParams.addParameter(coastParam);
		adjustableParams.addParameter(imageWidthParam);
		adjustableParams.addParameter(customScaleLabelCheckParam);
		adjustableParams.addParameter(scaleLabelParam);
		adjustableParams.addParameter(gmtSmoothingParam);
		adjustableParams.addParameter(blackBackgroundParam);
		adjustableParams.addParameter(dpiParam);
		adjustableParams.addParameter(gmtFromServer);
		adjustableParams.addParameter(logPlotParam);
		adjustableParams.addParameter(kmlParam);
		adjustableParams.addParameter(grdViewParam);


	}



	/**
	 * this function generates a GMT map from an XYZ data set using the current
	 * parameter settings, and using the version of GMT on the local computer.
	 *
	 * @param xyzDataSet
	 * @param scaleLabel - a string for the label (with no spaces!)
	 * @return - the name of the jpg file
	 */
	public String makeMapLocally(GeoDataSet xyzDataSet, String scaleLabel,
			String metadata, String dirName) throws GMT_MapException{
		
		// TODO - this method is broken; it should be rewritten to call getGMT_ScriptLines(GMT_Map map, String dir)
		//        rather than getGMT_ScriptLines(), by first getting a GMT_Map from getGMTMapSpecification(GeoDataSet)
		//        the work is getting the local paths set correctly.
		boolean needswork = true;
		if(needswork)
			throw new RuntimeException("This method needs work");

	    File file = new File(dirName);
	    file.mkdirs();

		// THESE SHOULD BE SET DYNAMICALLY
		// CURRENTLY HARD CODED FOR Ned and Nitin's Macs
		GMT_PATH="/opt/homebrew/bin/gmt ";
		GS_PATH="/opt/homebrew/bin/gs";
		PS2PDF_PATH = "/opt/homebrew/bin/ps2pdf";
		CONVERT_PATH="/opt/homebrew/bin/convert";
		GMT_DATA_PATH="/usr/scec/data/gmt/";
		
		GMT_SCRIPT_NAME = dirName+"/"+DEFAULT_GMT_SCRIPT_NAME;
		XYZ_FILE_NAME = dirName+"/"+DEFAULT_XYZ_FILE_NAME;
		METADATA_FILE_NAME = dirName+"/"+DEFAULT_METADATA_FILE_NAME;
		PS_FILE_NAME = dirName+"/"+DEFAULT_PS_FILE_NAME;
		JPG_FILE_NAME = dirName+"/"+DEFAULT_JPG_FILE_NAME;
		PNG_FILE_NAME = dirName+"/"+DEFAULT_PNG_FILE_NAME;
		PDF_FILE_NAME = dirName+"/"+DEFAULT_PDF_FILE_NAME;

		//creates the metadata file
		createMapInfoFile(metadata);
		

		// The color scale label
		SCALE_LABEL = scaleLabel;

		this.xyzDataSet = xyzDataSet;

		// take the log(z) values if necessary (and change label)
		checkForLogPlot();

		// make the local XYZ data file
		try {
			ArbDiscrGeoDataSet.writeXYZFile(xyzDataSet, XYZ_FILE_NAME);
		} catch (IOException e) {
			throw new GMT_MapException(e);
		}

		// get the GMT script lines
		ArrayList gmtLines = getGMT_ScriptLines();

		// make the script
		makeFileFromLines(gmtLines,GMT_SCRIPT_NAME);

		// now run the GMT script file
		String[] command ={"sh","-c","sh "+GMT_SCRIPT_NAME};
		RunScript.runScript(command);

		return JPG_FILE_NAME;
	}
	
	public GMT_Map getGMTMapSpecification(GeoDataSet xyzData) {
		Region region;
//		try {
//			region = new Region(minLatParam.getValue(),
//					maxLatParam.getValue(), minLonParam.getValue(), maxLonParam.getValue());
			region = new Region(
		    		new Location(minLatParam.getValue(), minLonParam.getValue()),
		    		new Location(maxLatParam.getValue(), maxLonParam.getValue()));
//		} catch (RegionConstraintException e) {
//			throw new RuntimeException(e);
//		}
		GMT_Map map = new GMT_Map(region, xyzData, gridSpacingParam.getValue(), cptParam.getValue());
		
		map.setXyzFileName(XYZ_FILE_NAME);
		map.setPSFileName(PS_FILE_NAME);
		map.setPDFFileName(PDF_FILE_NAME);
		map.setPNGFileName(PNG_FILE_NAME);
		map.setJPGFileName(JPG_FILE_NAME);
		
		CoastAttributes coast = null;
		if (coastParam.getValue().equals(COAST_DRAW)) {
			coast = new CoastAttributes(Color.GRAY, 1);
		} else if (coastParam.getValue().equals(COAST_FILL)) {
			coast = new CoastAttributes();
		}
		map.setCoast(coast);
		
		if (customScaleLabelCheckParam.getValue())
			map.setCustomLabel(scaleLabelParam.getValue());
		else
			map.setCustomLabel(null);
		
		if (colorScaleModeParam.getValue().equals(COLOR_SCALE_MODE_MANUALLY)) {
			map.setCustomScaleMin(colorScaleMinParam.getValue());
			map.setCustomScaleMax(colorScaleMaxParam.getValue());
		} else {
			map.setCustomScaleMin(null);
			map.setCustomScaleMax(null);
		}
		
		map.setDpi(dpiParam.getValue());
		
		if (showHiwysParam.getValue().equals(SHOW_HIWYS_ALL)) {
			map.setHighwayFile(HighwayFile.ALL);
		} else if (showHiwysParam.getValue().equals(SHOW_HIWYS_MAIN)) {
			map.setHighwayFile(HighwayFile.MAIN);
		} else if (showHiwysParam.getValue().equals(SHOW_HIWYS_OTHER)) {
			map.setHighwayFile(HighwayFile.OTHER);
		} else {
			map.setHighwayFile(null);
		}
		
		map.setImageWidth(imageWidthParam.getValue());
		
		map.setLogPlot(logPlotParam.getValue());
		
		map.setRescaleCPT(true);
		
		if (topoResolutionParam.getValue().equals(TOPO_RESOLUTION_03_CA)) {
			map.setTopoResolution(TopographicSlopeFile.CA_THREE);
		} else if (topoResolutionParam.getValue().equals(TOPO_RESOLUTION_06_US)) {
			map.setTopoResolution(TopographicSlopeFile.US_SIX);
		} else if (topoResolutionParam.getValue().equals(TOPO_RESOLUTION_18_US)) {
			map.setTopoResolution(TopographicSlopeFile.US_EIGHTEEN);
		} else if (topoResolutionParam.getValue().equals(TOPO_RESOLUTION_30_US)) {
			map.setTopoResolution(TopographicSlopeFile.US_THIRTY);
		} else if (topoResolutionParam.getValue().equals(TOPO_RESOLUTION_30_GLOBAL)) {
			map.setTopoResolution(TopographicSlopeFile.SRTM_30_PLUS);
		} else {
			map.setTopoResolution(null);
		}
		
		map.setUseGMTSmoothing(gmtSmoothingParam.getValue());
		
		map.setUseGRDView(grdViewParam.getValue());

		map.setBlackBackground(blackBackgroundParam.getValue());
		
		map.setGenerateKML(kmlParam.getValue());

		return map;
	}



	/**
	 * This generates GMT map for the given XYZ dataset and for the current parameter setting,
	 * using the GMT Servlet on the SCEC server (the map is made on the SCEC server).
	 *
	 * @param xyzDataSet
	 * @param scaleLabel - a string for the label (with no spaces!)
	 * @return - the name of the jpg file
	 */
	public String makeMapUsingServlet(GeoDataSet xyzDataSet,
			String scaleLabel, String metadata, String dirName)
	throws GMT_MapException,RuntimeException{
		GMT_Map map = getGMTMapSpecification(xyzDataSet);
		if (scaleLabel != null && scaleLabel.length() > 0 && !scaleLabel.equals(" "))
			map.setCustomLabel(scaleLabel);
		
		return this.makeMapUsingServlet(map, metadata, dirName);
	}
	
	/**
	 * This generates GMT map for the given XYZ dataset and for the current parameter setting,
	 * using the GMT Servlet on the SCEC server (the map is made on the SCEC server).
	 *
	 * @param xyzDataSet
	 * @param scaleLabel - a string for the label (with no spaces!)
	 * @return - the name of the jpg file
	 */
	public String makeMapUsingServlet(GMT_Map map, String metadata, String dirName)
	throws GMT_MapException,RuntimeException{

		// Set paths for the SCEC server (where the Servlet is)
		GMT_PATH = OPENSHA_GMT_PATH;
		GS_PATH = OPENSHA_GS_PATH;
		PS2PDF_PATH = OPENSHA_PS2PDF_PATH;
		CONVERT_PATH = OPENSHA_CONVERT_PATH;

		this.xyzDataSet = map.getGriddedData();

		// take the log(z) values if necessary (and change label)
		checkForLogPlot();
		
		imgWebAddr = this.openServletConnection(map, metadata, dirName);

		if (!imgWebAddr.endsWith("/"))
			imgWebAddr += "/";

		return imgWebAddr+PNG_FILE_NAME;
	}


	/**
	 * This generates GMT map for the given XYZ dataset and for the current parameter setting,
	 * using the GMT Web Service on the SCEC server (the map is made on the SCEC server).
	 *
	 * @param xyzDataSet
	 * @param scaleLabel - a string for the label (with no spaces!)
	 * @return - the name of the jpg file
	 */
	public String makeMapUsingWebServer(GeoDataSet xyzDataSet, String scaleLabel, String metadata)
	throws GMT_MapException{
		//creates the metadata file
		createMapInfoFile(metadata);
		// Set paths for the SCEC server (where the Servlet is)
		GMT_PATH = OPENSHA_GMT_PATH;
		GS_PATH = OPENSHA_GS_PATH;
		PS2PDF_PATH = OPENSHA_PS2PDF_PATH;
		CONVERT_PATH = OPENSHA_CONVERT_PATH;

		// The color scale label
		SCALE_LABEL = scaleLabel;

		this.xyzDataSet = xyzDataSet;

		// take the log(z) values if necessary (and change label)
		checkForLogPlot();

		// make the local XYZ data file
		try {
			ArbDiscrGeoDataSet.writeXYZFile(xyzDataSet, XYZ_FILE_NAME);
		} catch (IOException e) {
			throw new GMT_MapException(e);
		}

		// get the GMT script lines
		ArrayList gmtLines = getGMT_ScriptLines();

		// make the script
		makeFileFromLines(gmtLines,GMT_SCRIPT_NAME);

		//put files in String array which are to be sent to the server as the attachment
		String[] fileNames = new String[3];
		//getting the GMT script file name
		fileNames[0] = GMT_SCRIPT_NAME;
		//getting the XYZ file Name
		fileNames[1] = XYZ_FILE_NAME;

		//metadata file
		fileNames[2] = METADATA_FILE_NAME;
		//openWebServiceConnection(fileNames);
//		return imgWebAddr+JPG_FILE_NAME;
		return imgWebAddr+PNG_FILE_NAME;
	}



	/**
	 * method to get the adjustable parameters
	 */
	public ListIterator getAdjustableParamsIterator() {
		return adjustableParams.getParametersIterator();
	}


	/**
	 *
	 * @return the GMT Params List
	 */
	public ParameterList getAdjustableParamsList(){
		return adjustableParams;
	}

	/**
	 *
	 * @return the image file name
	 */
	public String getImageFileName(){
		return this.JPG_FILE_NAME;
	}

	/**
	 *
	 * @return the ArrayList containing the Metadata Info
	 */
	protected ArrayList getMapInfoLines(){
		ArrayList metadataFilesLines = new ArrayList();
		try{
			FileReader  fr = new FileReader(METADATA_FILE_NAME);
			BufferedReader br = new BufferedReader(fr);
			String fileLines = br.readLine();
			while(fileLines !=null){
				metadataFilesLines.add(fileLines);
				fileLines = br.readLine();
			}
		}catch(Exception e){
			e.printStackTrace();
		}

		return metadataFilesLines;
	}

	// make a local file from a vector of strings
	protected void makeFileFromLines(ArrayList lines, String fileName) {
		try{
			FileWriter fw = new FileWriter(fileName);
			BufferedWriter br = new BufferedWriter(fw);
			for(int i=0;i<lines.size();++i)
				br.write((String) lines.get(i)+"\n");
			br.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}


	//For the webservices Implementation
	/*private void openWebServiceConnection(String[] fileName){
    int size=fileName.length;

    FileDataSource[] fs = new FileDataSource[size+2];
    DataHandler dh[] = new DataHandler[size+2];
    System.out.println("File-0: "+fileName[0]);
    fs[0] =new FileDataSource(fileName[0]);
    dh[0] = new DataHandler(fs[0]);

    System.out.println("File-1: "+fileName[1]);
    fs[1] =new FileDataSource(fileName[1]);
    dh[1] = new DataHandler(fs[1]);


    System.out.println("File-2: "+fileName[2]);
    fs[2] =new FileDataSource(fileName[2]);
    dh[2] = new DataHandler(fs[2]);

    GMT_WebService_Impl client = new GMT_WebService_Impl();
    GMT_WebServiceAPI gmt = client.getGMT_WebServiceAPIPort();
    try{
      imgWebAddr = gmt.runGMT_Script(fileName,dh);
      System.out.println("imgWebAddr: "+imgWebAddr);
    }catch(Exception e){
      e.printStackTrace();
    }
  }*/

	/**
	 * sets the name of the metadata file with fileName( with full path)
	 * @param fileName
	 */
	public void setMetatdataFileName(String fileName){
		METADATA_FILE_NAME = fileName;
	}

	/**
	 * sets up the connection with the servlet on the server (gravity.usc.edu)
	 */
	protected String openServletConnection(GMT_Map map,
			String metadataLines, String dirName) throws RuntimeException{

		String webaddr=null;
		try{

			if(D) System.out.println("starting to make connection with servlet");
			URL gmtMapServlet = new URL(SERVLET_URL);


			URLConnection servletConnection = gmtMapServlet.openConnection();
			if(D) System.out.println("connection established");

			// inform the connection that we will send output and accept input
			servletConnection.setDoInput(true);
			servletConnection.setDoOutput(true);

			// Don't use a cached version of URL connection.
			servletConnection.setUseCaches (false);
			servletConnection.setDefaultUseCaches (false);
			// Specify the content type that we will send binary data
			servletConnection.setRequestProperty ("Content-Type","application/octet-stream");

			ObjectOutputStream outputToServlet = new
			ObjectOutputStream(servletConnection.getOutputStream());

			//sending the directory name to the servlet
			outputToServlet.writeObject(dirName);

			//sending the map specification
			outputToServlet.writeObject(map);

			//sending the contents of the Metadata file to the server.
			outputToServlet.writeObject(metadataLines);

			//sending the name of the MetadataFile to the server.
			outputToServlet.writeObject(DEFAULT_METADATA_FILE_NAME);

			outputToServlet.flush();
			outputToServlet.close();

			// Receive the "actual webaddress of all the gmt related files"
			// from the servlet after it has received all the data
			ObjectInputStream inputToServlet = new
			ObjectInputStream(servletConnection.getInputStream());

			Object messageFromServlet = inputToServlet.readObject();
			inputToServlet.close();
			if(messageFromServlet instanceof String){
				webaddr = (String) messageFromServlet;
				if (D) System.out.println("Receiving the Input from the Servlet:" +
						webaddr);
			}
			else
				throw (RuntimeException)messageFromServlet;
		}catch(RuntimeException e){
			throw new RuntimeException(e);
		}catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Server is down, please try again later. If the problem persists, make sure"
					+ " you are using the latest version of our applications.");
		}
		return webaddr;
	}





	/**
	 * This method allows one to set an adjustable parameter.
	 *
	 * @param paramName - the name of the Parameter to be set
	 * @param value - the desired parameter value
	 */
	public void setParameter(String paramName, Object value) {
		this.adjustableParams.getParameter(paramName).setValue(value);
	}


	/**
	 *
	 * @return the WebAddress to the files if the person used the GMT webservice,
	 * to download all the files
	 */
	public String getGMTFilesWebAddress(){
		return this.imgWebAddr;
	}


	// this computes a nice length for the km_scale
	private double getNiceKmScaleLength(double lat,double minLon,double maxLon) {

		double target = (maxLon-minLon)*111*Math.cos(Math.PI*lat/180) / 4;
		double test = 0.1;

		while(target > test) {
			test*=10;
		}
		test /= 10;
		return Math.ceil(target/test)*test;
	}

	// this computes a nice map tick intervale
	private double getNiceMapTickInterval(double minLat,double maxLat,double minLon,double maxLon) {

		double diff, niceTick=Double.NaN;

		// find the minimum range
		if( maxLat-minLat < maxLon-minLon)
			diff = maxLat-minLat;
		else
			diff = maxLon-minLon;

		// now divide this by two to ensureat least two labeled segments
		diff /= 2;

		// now find the first nice value below this one
		boolean finished = false;
		double fact = 100;
		while(!finished) {

			if((niceTick=1.0*fact) <= diff)
				finished = true;
			else if((niceTick=0.5*fact) <= diff)
				finished = true;
			else if((niceTick=0.25*fact) <= diff)
				finished = true;
			else
				fact /= 10.0;

		}
		return (double) ((float) niceTick);

	}

	public GriddedRegion getEvenlyGriddedGeographicRegion() {
		// Get the limits and discretization of the map
		double minLat = ((Double) minLatParam.getValue()).doubleValue();
		double maxLat = ((Double) maxLatParam.getValue()).doubleValue();
		double minLon = ((Double) minLonParam.getValue()).doubleValue();
		double maxLon = ((Double) maxLonParam.getValue()).doubleValue();
		double gridSpacing = ((Double) gridSpacingParam.getValue()).doubleValue();

		//return new GriddedRegion(minLat, maxLat, minLon, maxLon, gridSpacing);
		return new GriddedRegion(
	    		new Location(minLat, minLon),
	    		new Location(maxLat, maxLon),
	    		gridSpacing, new Location(0,0));
	}

	/**
	 * This method generates a list of strings needed for the GMT script
	 */
	@Deprecated
	protected ArrayList getGMT_ScriptLines() throws GMT_MapException{

		ArrayList<String> rmFiles = new ArrayList<String>();

		String commandLine;

		ArrayList<String> gmtCommandLines = new ArrayList<String>();

		// Get the limits and discretization of the map
		double minLat = ((Double) minLatParam.getValue()).doubleValue();
		double maxTempLat = ((Double) maxLatParam.getValue()).doubleValue();
		double minLon = ((Double) minLonParam.getValue()).doubleValue();
		double maxTempLon = ((Double) maxLonParam.getValue()).doubleValue();
		double gridSpacing = ((Double) gridSpacingParam.getValue()).doubleValue();

		// adjust the max lat and lon to be an exact increment (needed for xyz2grd)
		double maxLat = Math.rint(((maxTempLat-minLat)/gridSpacing))*gridSpacing +minLat;
		double maxLon = Math.rint(((maxTempLon-minLon)/gridSpacing))*gridSpacing +minLon;

		String region = " -R" + minLon + "/" + maxLon + "/" + minLat + "/" + maxLat+" ";
		if(D) System.out.println(C+" region = "+region);

		// this is the prefixed used for temporary files
		String fileName = "temp_junk";

		String grdFileName  = fileName+".grd";
		rmFiles.add(grdFileName);

		// this is not used anymore...hardcoded.
		String cptFile = GMT_DATA_PATH + GMT_CPT_Files.MAX_SPECTRUM.getFileName();

		String colorScaleMode = (String) colorScaleModeParam.getValue();

		String coast = (String) coastParam.getValue();

		// Set resolution according to the topoInten file chosen (options are 3, 6, 18, or 30):
		String resolution = (String) topoResolutionParam.getValue();
		String topoIntenFile = GMT_DATA_PATH + "calTopoInten" + resolution+".grd";

		// hard-code check that lat & lon bounds are in the region where we have topography:
		// this is only temporary until we have worldwide topo data
//		if( !resolution.equals(TOPO_RESOLUTION_NONE) &&
//				( maxLat > 43 || minLat < 32 || minLon < -126 || maxLon > -115 ))
//			throw new GMT_MapException("Topography not available for the chosen region; please select \"" +
//					TOPO_RESOLUTION_NONE + "\" for the " + TOPO_RESOLUTION_PARAM_NAME + " parameter");

		// Set highways String
		String showHiwys = (String) showHiwysParam.getValue();

		// plot size parameter
		double plotWdth = 6.5;
		String projWdth = " -JM"+plotWdth+"i ";
		double plotHght = ((maxLat-minLat)/(maxLon-minLon))*plotWdth/Math.cos(Math.PI*(maxLat+minLat)/(2*180));

		double yOffset = 11 - plotHght - 0.5;
		String yOff = " -Y" + yOffset + "i ";

		// set x-axis offset to 1 inch
		String xOff = " -X1.0i ";

		gmtCommandLines.add("#!/bin/bash\n\n");
		gmtCommandLines.add("# path variables\n");
		gmtCommandLines.add("GMT_PATH='" + GMT_PATH + "'\n");
		gmtCommandLines.add("CONVERT_PATH='" + CONVERT_PATH + "'\n");
		gmtCommandLines.add("COMMAND_PATH='" + COMMAND_PATH + "'\n");
		gmtCommandLines.add("PS2PDF_PATH='" + PS2PDF_PATH + "'\n\n");

		// command line to convert xyz file to grd file
		commandLine = "${GMT_PATH}xyz2grd "+ XYZ_FILE_NAME+" -G"+ grdFileName+ " -I"+gridSpacing+ region +" -D/degree/degree/amp/=/=/=  -:";
		gmtCommandLines.add(commandLine+"\n");

		// get color scale limits
		double colorScaleMin, colorScaleMax;
		if( colorScaleMode.equals(COLOR_SCALE_MODE_MANUALLY) ) {
			colorScaleMin = ((Double) this.colorScaleMinParam.getValue()).doubleValue();
			colorScaleMax = ((Double) this.colorScaleMaxParam.getValue()).doubleValue();
			if (colorScaleMin >= colorScaleMax)
				throw new RuntimeException("Error: Color-Scale Min must be less than the Max");
		}
		else {
			colorScaleMin = xyzDataSet.getMinZ();
			colorScaleMax = xyzDataSet.getMaxZ();
			System.out.println(colorScaleMin+","+colorScaleMax);
			if (colorScaleMin == colorScaleMax)
				throw new RuntimeException("Can't make the image plot because all Z values in the XYZ dataset have the same value ");
		}

		// make the cpt file
		float inc = (float) ((colorScaleMax-colorScaleMin)/20);
		commandLine="${GMT_PATH}makecpt -C" + cptFile + " -T" + colorScaleMin +"/"+ colorScaleMax +"/" + inc + " -Z > "+fileName+".cpt";
		gmtCommandLines.add(commandLine+"\n");
		
		gmtCommandLines.add("${COMMAND_PATH}rm gmtdefaults4"+"\n");

		// set some defaults
		if(blackBackgroundParam.getValue()) {
			commandLine = "${GMT_PATH}gmtset FONT_ANNOT_PRIMARY=14p,white FONT_LABEL=18p,white PS_PAGE_COLOR=0/0/0 PS_PAGE_ORIENTATION=portrait PS_MEDIA=letter";
			commandLine+=" MAP_DEFAULT_PEN=+white FORMAT_GEO_MAP=-D MAP_FRAME_WIDTH=0.1i MAP_FRAME_PEN=1p";
		}
		else {
			commandLine = "${GMT_PATH}gmtset FONT_ANNOT_PRIMARY=14p,black FONT_LABEL=18p,black PS_PAGE_COLOR=255/255/255 PS_PAGE_ORIENTATION=portrait PS_MEDIA=letter";
			commandLine+=" MAP_DEFAULT_PEN=black FORMAT_GEO_MAP=-D MAP_FRAME_WIDTH=0.1i MAP_FRAME_PEN=1p";
		}
		gmtCommandLines.add(commandLine+"\n");

		int dpi = (Integer)this.dpiParam.getValue();
		if(!(Boolean)gmtSmoothingParam.getValue()) {	// note that this forces topo resolution to none (TOPO_RESOLUTION_NONE)
			commandLine="${GMT_PATH}grdimage "+ grdFileName + xOff + yOff + projWdth + " -C"+fileName+".cpt "+" -K -E"+dpi+ region + " > " + PS_FILE_NAME;
			gmtCommandLines.add(commandLine+"\n");
		}
		// else generate the image depending on whether topo relief is desired
		else if( resolution.equals(TOPO_RESOLUTION_NONE) ) {
			commandLine="${GMT_PATH}grdimage "+ grdFileName + xOff + yOff + projWdth + " -C"+fileName+".cpt "+" -K -E"+dpi+ region + " > " + PS_FILE_NAME;
			gmtCommandLines.add(commandLine+"\n");
		}
		else {
			// redefine the region so that maxLat, minLat, and delta fall exactly on the topoIntenFile
			gridSpacing = (Integer.valueOf(resolution)).doubleValue()/(3600.0);
			double tempNum = Math.ceil((minLat-32.0)/gridSpacing);
			minLat = tempNum*gridSpacing+32.0;
			tempNum = Math.ceil((minLon-(-126))/gridSpacing);
			minLon = tempNum*gridSpacing+(-126);
			maxLat = Math.floor(((maxLat-minLat)/gridSpacing))*gridSpacing +minLat;
			maxLon = Math.floor(((maxLon-minLon)/gridSpacing))*gridSpacing +minLon;
			region = " -R" + minLon + "/" + maxLon + "/" + minLat + "/" + maxLat + " ";

			String hiResFile = fileName+"HiResData.grd";
			rmFiles.add(hiResFile);
			commandLine="${GMT_PATH}grdsample "+grdFileName+" -G"+hiResFile+" -I" +
			resolution + "s -nl "+region;
			gmtCommandLines.add(commandLine+"\n");
			String intenFile = fileName+"Inten.grd";
			commandLine="${GMT_PATH}grdcut " + topoIntenFile + " -G"+intenFile+ " " +region;
			rmFiles.add(intenFile);
			gmtCommandLines.add(commandLine+"\n");
			commandLine="${GMT_PATH}grdimage "+hiResFile+" " + xOff + yOff + projWdth +
			" -I"+fileName+"Inten.grd -C"+fileName+".cpt "+ "-K -E"+dpi+ region + " > " + PS_FILE_NAME;
			gmtCommandLines.add(commandLine+"\n");
		}

		// add highways if desired
		if ( !showHiwys.equals(SHOW_HIWYS_NONE) ) {
			commandLine="${GMT_PATH}psxy  "+region + projWdth + " -K -O -W1p,125/125/125 -: " + GMT_DATA_PATH + showHiwys + " >> " + PS_FILE_NAME;
			gmtCommandLines.add(commandLine+"\n");
		}
		
//		if(blackBackgroundParam.getValue())
//			commandLine="${GMT_PATH}gmtset MAP_DEFAULT_PEN 255/255/255 FORMAT_GEO_MAP=-D FRAME_WIDTH 0.1i COLOR_FOREGROUND 255/255/255";
//		else
//			commandLine="${GMT_PATH}gmtset MAP_DEFAULT_PEN 0/0/0 FORMAT_GEO_MAP=-D FRAME_WIDTH 0.1i COLOR_FOREGROUND 255/255/255";
//
//		gmtCommandLines.add(commandLine+"\n");

		// add coast and fill if desired
		if(coast.equals(COAST_FILL)) {
			commandLine="${GMT_PATH}pscoast "+region + projWdth + " -K -O  -W1p,17/73/71 -P -S17/73/71 -Dh -Na >> " + PS_FILE_NAME;
			gmtCommandLines.add(commandLine+"\n");
		}
		else if(coast.equals(COAST_DRAW)) {
			commandLine="${GMT_PATH}pscoast "+region + projWdth + " -K -O  -W4 -P -Dh -Na>> " + PS_FILE_NAME;
			gmtCommandLines.add(commandLine+"\n");
		}


		// This adds intermediate commands
		addIntermediateGMT_ScriptLines(gmtCommandLines, region, projWdth);
		// set some defaults

		// add the color scale
		DecimalFormat df2 = new DecimalFormat("0.E0");
		Float tickInc = Float.valueOf(df2.format((colorScaleMax-colorScaleMin)/4.0));
		inc = tickInc.floatValue();
		//checks to see if customLabel is selected, then get the custom label
		boolean customLabelSelected = ((Boolean)customScaleLabelCheckParam.getValue()).booleanValue();
		String scaleLabel ="";
		if(customLabelSelected)
			scaleLabel = (String)scaleLabelParam.getValue();
		else
			scaleLabel = SCALE_LABEL;
		commandLine="${GMT_PATH}psscale -Ba"+inc+":"+scaleLabel+": -D3.25i/-0.5i/6i/0.3ih -C"+fileName+".cpt -O -K -N70 >> " + PS_FILE_NAME;
		gmtCommandLines.add(commandLine+"\n");

		// add the basemap
		double niceKmLength = getNiceKmScaleLength(minLat, minLon, maxLon);
		double kmScaleXoffset = plotWdth/4; 
		double niceTick = getNiceMapTickInterval(minLat, maxLat, minLon, maxLon);
		commandLine="${GMT_PATH}psbasemap -B"+niceTick+"/"+niceTick+"eWNs " + projWdth +region+
		" -Lfx"+kmScaleXoffset+"i/0.5i/"+minLat+"/"+niceKmLength+"+l -O >> " + PS_FILE_NAME;
		gmtCommandLines.add(commandLine+"\n");

		// boolean to switch between purely using convert for the ps conversion, 
		// and using gs.
		boolean use_gs_raster = true;

		int heightInPixels = (int) ((11.0 - yOffset + 2.0) * (double) dpi);
		String convertArgs = "-crop 595x"+heightInPixels+"+0+0";
		double imageWidth = ((Double)imageWidthParam.getValue()).doubleValue();
		if (imageWidth != IMAGE_WIDTH_DEFAULT.doubleValue()) {
			int wdth = (int)(imageWidth*(double)dpi);
			convertArgs += " -filter Lanczos -geometry "+wdth;
		}
		if (use_gs_raster) {
			int jpeg_quality= 90;
			gmtCommandLines.add("${COMMAND_PATH}cat "+ PS_FILE_NAME + " | "+GS_PATH+" -sDEVICE=jpeg " + 
					" -dJPEGQ="+jpeg_quality+" -sOutputFile="+JPG_FILE_NAME+" -\n");
			gmtCommandLines.add("${COMMAND_PATH}cat "+ PS_FILE_NAME + " | "+GS_PATH+" -sDEVICE=png16m " + 
					"-dTextAlphaBits=4 -sOutputFile="+PNG_FILE_NAME+" -\n");

			gmtCommandLines.add("${CONVERT_PATH} " + convertArgs + " " + JPG_FILE_NAME + " " + JPG_FILE_NAME+"\n");
			gmtCommandLines.add("${CONVERT_PATH} " + convertArgs + " " + PNG_FILE_NAME + " " + PNG_FILE_NAME+"\n");
		} else {
			convertArgs = "-density " + dpi + " " + convertArgs;

			// add a command line to convert the ps file to a jpg file - using convert
			gmtCommandLines.add("${CONVERT_PATH} " + convertArgs + " " + PS_FILE_NAME + " " + JPG_FILE_NAME+"\n");
			gmtCommandLines.add("${CONVERT_PATH} " + convertArgs + " " + PS_FILE_NAME + " " + PNG_FILE_NAME+"\n");
		}

		boolean googleearth = true;

		// Add google earth lines...this doesn't work yet, still need to figure out how to get raster extracter
		// to be called during execution
		if (googleearth) {
			System.out.println("Making Google Earth files!");
			String gEarth_psFileName = "gEarth_" + PS_FILE_NAME;
			String gEarth_proj = " -JQ180/"+plotWdth+"i ";
			String gEarth_kmz_name = "./map.kmz";
			if(!(Boolean)gmtSmoothingParam.getValue()) {	// note that this forces topo resolution to none (TOPO_RESOLUTION_NONE)
				commandLine="${GMT_PATH}grdview "+ grdFileName + xOff + yOff + gEarth_proj +
				" -C"+fileName+".cpt "+"-Ts -K"+dpi+ region + " > " + gEarth_psFileName;
				gmtCommandLines.add(commandLine+"\n");
			}
			else if( resolution.equals(TOPO_RESOLUTION_NONE) ) {
				commandLine="${GMT_PATH}grdimage "+ grdFileName + xOff + yOff + gEarth_proj +
				" -C"+fileName+".cpt "+" -K -E"+dpi+ region + " > " + gEarth_psFileName;
				gmtCommandLines.add(commandLine+"\n");
			}
			else {
				commandLine="${GMT_PATH}grdimage "+fileName+"HiResData.grd " + xOff + yOff + gEarth_proj +
				" -I"+fileName+"Inten.grd -C"+fileName+".cpt "+ "-K -E"+
				dpi+ region + " > " + gEarth_psFileName;
				gmtCommandLines.add(commandLine+"\n");
			}

			commandLine = JAVA_PATH + " -cp " + JAVA_CLASSPATH + " " + GMT_KML_Generator.class.getName() + " " + 
						gEarth_psFileName + " " + gEarth_kmz_name +
						" " + minLat + " " + maxLat + " " + minLon + " " + maxLon;
			gmtCommandLines.add(commandLine+"\n");
		}

		// add a command line to convert the ps file to a jpg file - using gs
		// this looks a bit better than that above (which sometimes shows some horz lines).


		//		commandLine = "${CONVERT_PATH} -crop 595x"+heightInPixels+"+0+0 temp1.jpg temp2.jpg";
		//		gmtCommandLines.add(commandLine+"\n");

		//resize the image if desired
		//		if (imageWidth != IMAGE_WIDTH_DEFAULT.doubleValue()) {
		//			int wdth = (int)(imageWidth*(double)dpi);
		//			commandLine = "${CONVERT_PATH} -filter Lanczos -geometry "+wdth+" temp2.jpg "+JPG_FILE_NAME;
		//			gmtCommandLines.add(commandLine+"\n");
		//		}
		//		else {
		//			commandLine = "${COMMAND_PATH}mv temp2.jpg "+JPG_FILE_NAME;
		//			gmtCommandLines.add(commandLine+"\n");
		//		}

		commandLine = "${PS2PDF_PATH}  "+PS_FILE_NAME+"  "+PDF_FILE_NAME;
		gmtCommandLines.add(commandLine+"\n");
		// clean out temp files
		if (rmFiles.size() > 0) {
			String rmCommand = "${COMMAND_PATH}rm";
			for (String rmFile : rmFiles) {
				rmCommand += " " + rmFile;
			}
			gmtCommandLines.add(rmCommand+"\n");
		}

		// This adds any final commands
		addFinalGMT_ScriptLines(gmtCommandLines);


		return gmtCommandLines;
	}
	
	public static String getGMTColorString(Color color) {
		return color.getRed() + "/" + color.getGreen() + "/" + color.getBlue();
	}
	
	private static String stripFormatLabel(String label) {
		label = label.replaceAll("'", "");
		label = label.replaceAll(";", "");
		
		return "'" + label + "'";
	}
	
	public static void clearEnv() {
		GMT_PATH = null;
		GS_PATH = null;
		CONVERT_PATH = null;
		COMMAND_PATH = null;
		PS2PDF_PATH = null;
		NETCDF_LIB_PATH = null;
	}
	
	public static ArrayList<String> getGMTPathEnvLines() {
		ArrayList<String> gmtCommandLines = new ArrayList<String>();
		gmtCommandLines.add("## path variables ##");
		String gmtPath = GMT_PATH;
		if (gmtPath == null)
			gmtPath = "gmt ";
		gmtCommandLines.add("GMT_PATH='" + gmtPath + "'");
		String gsPath = GS_PATH;
		if (gsPath == null)
			gsPath = "gs";
		gmtCommandLines.add("GS_PATH='" + gsPath + "'");
		String convertPath = CONVERT_PATH;
		if (convertPath == null)
			convertPath = "convert";
		gmtCommandLines.add("CONVERT_PATH='" + convertPath + "'");
		String cmdPath = COMMAND_PATH;
		if (cmdPath == null)
			cmdPath = "";
		gmtCommandLines.add("COMMAND_PATH='" + cmdPath + "'");
		String ps2pdfPath = PS2PDF_PATH;
		if (ps2pdfPath == null)
			ps2pdfPath = "ps2pdf";
		gmtCommandLines.add("PS2PDF_PATH='" + ps2pdfPath + "'");
		String netCDFPath = NETCDF_LIB_PATH;
		if (netCDFPath == null)
			netCDFPath = "";
		gmtCommandLines.add("NETCDF_LIB_PATH='" + netCDFPath + "'");
		gmtCommandLines.add("");
		gmtCommandLines.add("## ENV info");
		gmtCommandLines.add("echo \"SHELL: $SHELL\"");
		gmtCommandLines.add("echo \"PATH: $PATH\"");
		gmtCommandLines.add("if [[ -d $NETCDF_LIB_PATH ]];then");
		gmtCommandLines.add("\texport LD_LIBRARY_PATH=$NETCDF_LIB_PATH:${LD_LIBRARY_PATH}");
		gmtCommandLines.add("fi");
		gmtCommandLines.add("echo \"LD_LIBRARY_PATH: $LD_LIBRARY_PATH\"");
		gmtCommandLines.add("");
		
		return gmtCommandLines;
	}
	
	private static final double max_height_in = 7.625;
	
	/**
	 * This method generates a list of strings needed for the GMT script
	 */
	public ArrayList<String> getGMT_ScriptLines(GMT_Map map, String dir) throws GMT_MapException {
		
		System.out.println("Generating map for dir: " + dir);
		
		if (!dir.endsWith(File.separator))
			dir += File.separator;

		ArrayList<String> rmFiles = new ArrayList<String>();

		String commandLine;

		ArrayList<String> gmtCommandLines = new ArrayList<String>();

		// Get the limits and discretization of the map
		double minLat = map.getRegion().getMinLat();
		double maxTempLat = map.getRegion().getMaxLat();
		double minLon = map.getRegion().getMinLon();
		double maxTempLon = map.getRegion().getMaxLon();
		double gridSpacing = map.getGriddedDataInc();

		// adjust the max lat and lon to be an exact increment (needed for xyz2grd)
		double maxLat = Math.rint(((maxTempLat-minLat)/gridSpacing))*gridSpacing +minLat;
		double maxLon = Math.rint(((maxTempLon-minLon)/gridSpacing))*gridSpacing +minLon;

		String region = " -R" + (float)minLon + "/" + (float)maxLon + "/" + (float)minLat + "/" + (float)maxLat+" ";
		if(D) System.out.println(C+" region = "+region);

		// this is the prefixed used for temporary files
		String tempFilePrefix = "temp_junk";

		String grdFileName = tempFilePrefix+".grd";
		rmFiles.add(grdFileName);

		String cptFile;
		if (map.getCptFile() != null) {
			cptFile = GMT_DATA_PATH + map.getCptFile();
		} else {
			cptFile = map.getCustomCptFileName();
			CPT cpt = map.getCpt();
			try {
				cpt.writeCPTFile(dir + cptFile);
			} catch (IOException e) {
				throw new GMT_MapException("Could not write custom CPT file", e);
			}
		}

		String topoIntenFile = null;
		if (map.getTopoResolution() != null)
			topoIntenFile = GMT_DATA_PATH + map.getTopoResolution().fileName();

		// hard-code check that lat & lon bounds are in the region where we have topography:
		// this is only temporary until we have worldwide topo data
		if(topoIntenFile != null && !map.getTopoResolution().region().contains(map.getRegion()))
			throw new GMT_MapException("Topography not available for the chosen region; please select \"" +
					TOPO_RESOLUTION_NONE + "\" for the " + TOPO_RESOLUTION_PARAM_NAME + " parameter");

		// plot size parameter
		double plotWdth = 6.5;
		double cosTerm = Math.cos(Math.PI*(maxLat+minLat)/(2*180));
		double plotHght = ((maxLat-minLat)/(maxLon-minLon))*plotWdth/cosTerm;
		System.out.println("calc height: "+plotHght);
		if (plotHght > max_height_in) {
			// maximum that looks good
			plotHght = max_height_in;
			plotWdth = plotHght * cosTerm * (maxLon-minLon)/(maxLat-minLat);
		}
		String projWdth = " -JM"+plotWdth+"i ";

		double yOffset = 11 - plotHght - 0.5;
		System.out.println("yOffset: "+yOffset+", height="+plotHght+", width="+plotWdth);
		String yOff = " -Y" + yOffset + "i ";

		// set x-axis offset to 1 inch, but adjust if we changed plotWidth above
		double xOffset = 1d + 0.5*(6.5-plotWdth);
		String xOff = " -X"+(float)xOffset+"i ";

		gmtCommandLines.add("#!/bin/bash");
		gmtCommandLines.add("");
		gmtCommandLines.add("cd " + dir);
		gmtCommandLines.add("");
		gmtCommandLines.addAll(getGMTPathEnvLines());
		gmtCommandLines.add("## Plot Script ##");
		gmtCommandLines.add("");
		
		// set some defaults
		gmtCommandLines.add("# Set GMT paper/font & map defaults");
		if(map.isBlackBackground())
			commandLine = "${GMT_PATH}gmtset FONT_ANNOT_PRIMARY=14p,white FONT_LABEL=18p,white PS_PAGE_COLOR=0/0/0 PS_PAGE_ORIENTATION=portrait PS_MEDIA=letter";
		else
			commandLine = "${GMT_PATH}gmtset FONT_ANNOT_PRIMARY=14p,black FONT_LABEL=18p,black PS_PAGE_COLOR=255/255/255 PS_PAGE_ORIENTATION=portrait PS_MEDIA=letter";
		gmtCommandLines.add(commandLine);
		gmtCommandLines.add("");
		if(map.isBlackBackground())
			commandLine="${GMT_PATH}gmtset MAP_DEFAULT_PEN=+white FORMAT_GEO_MAP=-D MAP_FRAME_WIDTH=0.1i COLOR_FOREGROUND=255/255/255 MAP_FRAME_PEN=1p";
		else
			commandLine="${GMT_PATH}gmtset MAP_DEFAULT_PEN=black FORMAT_GEO_MAP=-D MAP_FRAME_WIDTH=0.1i COLOR_FOREGROUND=255/255/255 MAP_FRAME_PEN=1p";
		gmtCommandLines.add(commandLine);
		gmtCommandLines.add("");
		
		if (map.getGMT_Params() != null) {
			gmtCommandLines.add("# GMT Params");
			for (String name : map.getGMT_Params().keySet()) {
				String value = map.getGMT_Params().get(name);
				gmtCommandLines.add("${GMT_PATH}gmtset "+name+" "+value);
			}
			gmtCommandLines.add("");
		}

		GeoDataSet griddedData = map.getGriddedData();
		
		if (griddedData != null) {
			try {
				ArbDiscrGeoDataSet.writeXYZFile(griddedData, dir + map.getXyzFileName());
			} catch (IOException e) {
				throw new GMT_MapException("Could not write XYZ data to a file", e);
			}
			if (map.getInterpSettings() == null) {
				// simple gridded data
				gmtCommandLines.add("# convert xyz file to grd file");
				commandLine = "${GMT_PATH}xyz2grd "+ map.getXyzFileName()+" -G"+ grdFileName+ " -I"+gridSpacing+
								region +" -D/degree/degree/amp/=/=/=  -:";
				gmtCommandLines.add(commandLine);
			} else {
				// scatter data that must be interpolated
				GMT_InterpolationSettings interpSettings = map.getInterpSettings();
				gmtCommandLines.add("# do GMT interpolation on the scatter data");
				commandLine = "${GMT_PATH}surface "+ map.getXyzFileName() +" -G"+ grdFileName+ " -I"+gridSpacing+
								region+interpSettings.getConvergenceArg()+" "+interpSettings.getSearchArg()
								+" "+interpSettings.getTensionArg()+" -: -H0";
				gmtCommandLines.add(commandLine);
				if (interpSettings.isSaveInterpSurface()) {
					gmtCommandLines.add("# write interpolated XYZ file");
					commandLine = "${GMT_PATH}grd2xyz "+ grdFileName+ " > "+GMT_InterpolationSettings.INTERP_XYZ_FILE_NAME;
					gmtCommandLines.add(commandLine);
				}
			}
		} else if (map.getCustomGRDPath() != null) {
			File grdFile = new File(map.getCustomGRDPath());
			Preconditions.checkState(grdFile.exists(), "File doesn't esist: "+grdFile.getAbsolutePath());
			grdFileName = grdFile.getAbsolutePath();
		}
		
		// get color scale limits
		double colorScaleMin, colorScaleMax;
		if (map.isCustomScale()) {
			colorScaleMin = map.getCustomScaleMin();
			colorScaleMax = map.getCustomScaleMax();
			if (colorScaleMin >= colorScaleMax)
				throw new RuntimeException("Error: Color-Scale Min must be less than the Max");
		}
		else {
			colorScaleMin = griddedData.getMinZ();
			colorScaleMax = griddedData.getMaxZ();
			System.out.println(colorScaleMin+","+colorScaleMax);
			if (colorScaleMin == colorScaleMax)
				throw new RuntimeException("Can't make the image plot because all Z values in the XYZ dataset have the same value ");
		}

		if (map.isRescaleCPT()) {
			// make the cpt file
			float inc = (float) ((colorScaleMax-colorScaleMin)/20);
			String tempCPT = tempFilePrefix+".cpt";
			rmFiles.add(tempCPT);
			gmtCommandLines.add("# Rescale the CPT file");
			commandLine="${GMT_PATH}makecpt -C" + cptFile + " -T" + colorScaleMin +"/"+ colorScaleMax +"/" + inc + " -Z > "+tempCPT;
			gmtCommandLines.add(commandLine);
			cptFile = tempCPT;
		}
		
		String psFileName = map.getPSFileName();
		
		boolean doContour = map.getGriddedData() != null && map.getContourIncrement() > 0;
		boolean contourOnly = doContour && map.isContourOnly();
		String grdFileForContour = grdFileName;
		
		int dpi = map.getDpi();
		if (griddedData == null && map.getCustomGRDPath() == null) {
			// we have to initialize it ourselves - this doesn't actually plot anything
			commandLine="echo 1000 1000 | ${GMT_PATH}psxy "+region+ xOff + yOff + projWdth+" -K > " + psFileName;
			gmtCommandLines.add(commandLine+"\n");
		} else {
			if (map.isMaskIfNotRectangular() && !map.getRegion().isRectangular()
					&& (!map.isUseGMTSmoothing() || map.getTopoResolution() == null || contourOnly)) {
				String maskName = "mask.xy";
				String maskGRD = "mask.grd";
				rmFiles.add(maskGRD);
				try {
					writeMaskFile(map.getRegion(), dir+maskName);
				} catch (IOException e) {
					throw new GMT_MapException("Couldn't write mask file!", e);
				}
				String spacing = gridSpacing + "";
				gmtCommandLines.add("# create mask");
				commandLine = "${GMT_PATH}grdmask "+maskName+region+" -I"+spacing+" -NNaN/1/1 -G"+maskGRD;
				gmtCommandLines.add(commandLine+"\n");
				
				String unmaskedGRD = "unmasked_"+grdFileName;
				rmFiles.add(unmaskedGRD);
				gmtCommandLines.add("mv "+grdFileName+" "+unmaskedGRD);
				gmtCommandLines.add("${GMT_PATH}grdmath "+unmaskedGRD+" "+maskGRD+" MUL = "+grdFileName+"\n");
			}
			
			if (!map.isUseGMTSmoothing()) {
				if (!contourOnly) {
					// TODO
					if (map.isUseGRDView())
						commandLine="${GMT_PATH}grdview "+ grdFileName + xOff + yOff + projWdth + " -C"+cptFile+" -Ts -K"+ region + " > " + psFileName;
					else
						commandLine="${GMT_PATH}grdimage "+ grdFileName + xOff + yOff + projWdth + " -C"+cptFile+" -K"+ region + " > " + psFileName;
					gmtCommandLines.add(commandLine+"\n");
				}
			}
			// generate the image depending on whether topo relief is desired
			else if (map.getTopoResolution() == null && map.getCustomIntenPath() == null) {
				if (!contourOnly) {
					gmtCommandLines.add("# Plot the gridded data");
					commandLine="${GMT_PATH}grdimage "+ grdFileName + xOff + yOff + projWdth + " -C"+cptFile+" "+" -K -E"+dpi+ region + " > " + psFileName;
					gmtCommandLines.add(commandLine+"\n");
				}
			}
			else if (!contourOnly) {
				// redefine the region so that maxLat, minLat, and delta fall exactly on the topoIntenFile
				TopographicSlopeFile topoFile = map.getTopoResolution();
				String hiResFile;
				String intenFile;
				if (topoFile != null) {
					gridSpacing = GeoTools.secondsToDeg(map.getTopoResolution().resolution());
					double tempNum = Math.ceil((minLat-topoFile.region().getMinLat())/gridSpacing);
					minLat = tempNum*gridSpacing+topoFile.region().getMinLat();
					tempNum = Math.ceil((minLon-(topoFile.region().getMinLon()))/gridSpacing);
					minLon = tempNum*gridSpacing+(topoFile.region().getMinLon());
					maxLat = Math.floor(((maxLat-minLat)/gridSpacing))*gridSpacing +minLat;
					maxLon = Math.floor(((maxLon-minLon)/gridSpacing))*gridSpacing +minLon;
					region = " -R" + (float)minLon + "/" + (float)maxLon + "/" + (float)minLat + "/" + (float)maxLat + " ";

					hiResFile = tempFilePrefix+"HiResData.grd";
					rmFiles.add(hiResFile);
					gmtCommandLines.add("# Resample the map to the topo resolution");
					commandLine="${GMT_PATH}grdsample "+grdFileName+" -G"+hiResFile+" -I" +
					topoFile.resolution() + "s -nl "+region;
					gmtCommandLines.add(commandLine);
					
					intenFile = tempFilePrefix+"Inten.grd";
					gmtCommandLines.add("# Cut the topo file to match the data region");
					commandLine="${GMT_PATH}grdcut " + topoIntenFile + " -G"+intenFile+ " " +region;
					rmFiles.add(intenFile);
					gmtCommandLines.add(commandLine);
				} else {
					Preconditions.checkNotNull(map.getCustomIntenPath());
					hiResFile = grdFileName;
					intenFile = map.getCustomIntenPath();
				}
				
				if (map.isMaskIfNotRectangular() && !map.getRegion().isRectangular()) {
					String maskName = "mask.xy";
					String maskGRD = "mask.grd";
					rmFiles.add(maskGRD);
					try {
						writeMaskFile(map.getRegion(), dir+maskName);
					} catch (IOException e) {
						throw new GMT_MapException("Couldn't write mask file!", e);
					}
					String spacing = topoFile.resolution() + "c";
					gmtCommandLines.add("# create mask");
					commandLine = "${GMT_PATH}grdmask "+maskName+region+" -I"+spacing+" -NNaN/1/1 -G"+maskGRD;
					gmtCommandLines.add(commandLine+"\n");
					
					String unmaskedGRD = "unmasked_"+hiResFile;
					rmFiles.add(unmaskedGRD);
					gmtCommandLines.add("mv "+hiResFile+" "+unmaskedGRD);
					gmtCommandLines.add("${GMT_PATH}grdmath "+unmaskedGRD+" "+maskGRD+" MUL = "+hiResFile+"\n");
				}
				grdFileForContour = hiResFile;
				
				gmtCommandLines.add("# Plot the gridded data with topographic shading");
				commandLine="${GMT_PATH}grdimage "+hiResFile+" " + xOff + yOff + projWdth +
				" -I"+intenFile+" -C"+cptFile+" "+ "-K -E"+dpi+ region + " > " + psFileName;
				gmtCommandLines.add(commandLine);
			}
		}
		
		if (doContour) {
			gmtCommandLines.add("# Plot contours");
			String contourPenIncStr = " -W1p ";
			if (map.isCPTEqualSpacing())
				contourPenIncStr += "-C"+cptFile;
			else
				contourPenIncStr += "-A"+(float)map.getContourIncrement();
//			if (contourOnly)
//				onlyAdd =
			if (contourOnly)
				commandLine="${GMT_PATH}grdcontour "+grdFileForContour+xOff+yOff+projWdth
					+" -K "+region+contourPenIncStr+ " > " + psFileName;
			else
				commandLine="${GMT_PATH}grdcontour "+grdFileForContour+projWdth
					+" -O -K "+region+contourPenIncStr+ " >> " + psFileName;
			gmtCommandLines.add(commandLine+"\n");
		}
		
		gmtCommandLines.add("");
		addSpecialElements(gmtCommandLines, map, region, projWdth, psFileName);
		
//
//		//TODO: figure this out...
//		// This adds intermediate commands
//		addIntermediateGMT_ScriptLines(gmtCommandLines);

		addColorbarCommand(gmtCommandLines, map, colorScaleMin, colorScaleMax, cptFile, psFileName, plotWdth);

		// add the basemap
		
		double niceTick = getNiceMapTickInterval(minLat, maxLat, minLon, maxLon);
		gmtCommandLines.add("# Map frame and KM scale label");
		commandLine="${GMT_PATH}psbasemap -B"+niceTick+"/"+niceTick+"eWNs " + projWdth +region;
		if (map.isDrawScaleKM()) {
			double niceKmLength = getNiceKmScaleLength(minLat, minLon, maxLon);
			double kmScaleXoffset = plotWdth/4;
			commandLine += " -Lfx"+kmScaleXoffset+"i/0.5i/"+minLat+"/"+niceKmLength+"+l";
		}
		commandLine += " -O >> " + psFileName;
		gmtCommandLines.add(commandLine);
		
		gmtCommandLines.add("");
		gmtCommandLines.add("## PostScript conversion ##");

		// boolean to switch between purely using convert for the ps conversion, 
		// and using gs.
		boolean use_gs_raster = true;
		
		String jpgFileName = map.getJPGFileName();
		String pngFileName = map.getPNGFileName();

		int heightInPixels = (int) ((11.0 - yOffset + 2.0) * (double) dpi);
		String convertArgs = "-crop 595x"+heightInPixels+"+0+0";
		double imageWidth = map.getImageWidth();
		if (imageWidth != IMAGE_WIDTH_DEFAULT.doubleValue()) {
			int wdth = (int)(imageWidth*(double)dpi);
			convertArgs += " -filter Lanczos -geometry "+wdth;
		}
		String gsArgs = " -dNumRenderingThreads=4 -dBandBufferSpace=500000000 -dBufferSpace=1000000000 ";
		if (use_gs_raster) {
			int jpeg_quality= 90;
			if (jpgFileName != null)
				gmtCommandLines.add("${COMMAND_PATH}cat "+ psFileName + " | ${GS_PATH}"+gsArgs+" -sDEVICE=jpeg " + 
					" -dJPEGQ="+jpeg_quality+" -sOutputFile="+jpgFileName+" -");
			if (pngFileName != null)
				gmtCommandLines.add("${COMMAND_PATH}cat "+ psFileName + " | ${GS_PATH}"+gsArgs+" -sDEVICE=png16m " + 
					"-dTextAlphaBits=4 -sOutputFile="+pngFileName+" -");

			if (jpgFileName != null)
				gmtCommandLines.add("${CONVERT_PATH} " + convertArgs + " " + jpgFileName + " " + jpgFileName);
			if (pngFileName != null)
				gmtCommandLines.add("${CONVERT_PATH} " + convertArgs + " " + pngFileName + " " + pngFileName);
		} else {
			convertArgs = "-density " + dpi + " " + convertArgs;

			// add a command line to convert the ps file to a jpg file - using convert
			if (jpgFileName != null)
				gmtCommandLines.add("${CONVERT_PATH} " + convertArgs + " " + psFileName + " " + jpgFileName);
			if (pngFileName != null)
				gmtCommandLines.add("${CONVERT_PATH} " + convertArgs + " " + psFileName + " " + pngFileName);
		}
		
		if (map.getPDFFileName() != null) {
			commandLine = "${PS2PDF_PATH}"+gsArgs+" "+psFileName+"  "+map.getPDFFileName();
			gmtCommandLines.add(commandLine);
		}

		boolean googleearth = map.isGenerateKML();

		// Add google earth lines...this doesn't work yet, still need to figure out how to get raste extracter
		// to be called during execution
		if (googleearth && griddedData != null) {
			gmtCommandLines.add("## Google earth files ##");
			System.out.println("Making Google Earth files!");
			String gEarth_pngFileName = "gEarth.png";
			String gEarth_proj = " -JQ180/"+plotWdth+"i ";
			String gEarth_kmz_name = "./map.kmz";
			gmtCommandLines.add("# Make PS file for google earth");
			String aArg = " -A"+gEarth_pngFileName+"=PNG";
			if (!map.isUseGMTSmoothing()) {
//				commandLine="${GMT_PATH}grdview "+ grdFileName + xOff + yOff + gEarth_proj +
//				" -C"+cptFile+" "+"-Ts"+dpi+ region + " > " + gEarth_psFileName;
				commandLine="${GMT_PATH}grdimage "+ grdFileName + xOff + yOff + gEarth_proj +
						aArg+" -C"+cptFile+" "+" -E"+dpi+ region;
				gmtCommandLines.add(commandLine);
			}
			else if (map.getTopoResolution() == null) {
				commandLine="${GMT_PATH}grdimage "+ grdFileName + xOff + yOff + gEarth_proj +
						aArg+" -C"+cptFile+" "+" -E"+dpi+ region;
				gmtCommandLines.add(commandLine);
			}
			else {
				commandLine="${GMT_PATH}grdimage "+tempFilePrefix+"HiResData.grd " + xOff + yOff + gEarth_proj +
						aArg+" -I"+tempFilePrefix+"Inten.grd -C"+cptFile+" "+ "-E"+
						dpi+ region;
				gmtCommandLines.add(commandLine);
			}

			commandLine = JAVA_PATH + " -Xmx4G -cp " + JAVA_CLASSPATH + " " + GMT_KML_Generator.class.getName() + " " + 
						gEarth_pngFileName + " " + gEarth_kmz_name +
						" " + minLat + " " + maxLat + " " + minLon + " " + maxLon;
			gmtCommandLines.add(commandLine);
		}

		// add a command line to convert the ps file to a jpg file - using gs
		// this looks a bit better than that above (which sometimes shows some horz lines).


		//		commandLine = "${CONVERT_PATH} -crop 595x"+heightInPixels+"+0+0 temp1.jpg temp2.jpg";
		//		gmtCommandLines.add(commandLine+"\n");

		//resize the image if desired
		//		if (imageWidth != IMAGE_WIDTH_DEFAULT.doubleValue()) {
		//			int wdth = (int)(imageWidth*(double)dpi);
		//			commandLine = "${CONVERT_PATH} -filter Lanczos -geometry "+wdth+" temp2.jpg "+jpgFileName;
		//			gmtCommandLines.add(commandLine+"\n");
		//		}
		//		else {
		//			commandLine = "${COMMAND_PATH}mv temp2.jpg "+jpgFileName;
		//			gmtCommandLines.add(commandLine+"\n");
		//		}
		addCleanup(gmtCommandLines, rmFiles);
		
		
		// This adds any final commands
		addFinalGMT_ScriptLines(gmtCommandLines);


		return gmtCommandLines;
	}
	
	public static void writeMaskFile(Region region, String fileName) throws IOException {
		FileWriter fw = new FileWriter(fileName);
		
		Location first = null;
		for (Location loc : region.getBorder()) {
			if (first == null)
				first = loc;
			fw.write(loc.getLongitude() + " " + loc.getLatitude() + "\n");
		}
		fw.write(first.getLongitude() + " " + first.getLatitude() + "\n");
		
		fw.close();
	}
	
	public static void addCleanup(ArrayList<String> gmtCommandLines, ArrayList<String> rmFiles) {
		// clean out temp files
		if (rmFiles.size() > 0) {
			gmtCommandLines.add("");
			gmtCommandLines.add("# Clean up");
			String rmCommand = "${COMMAND_PATH}rm";
			for (String rmFile : rmFiles) {
				rmCommand += " " + rmFile;
			}
			gmtCommandLines.add(rmCommand);
		}
	}
	
	public static void addHighwayCommand(ArrayList<String> gmtCommandLines, GMT_Map map,
			String region, String proj, String psFile) {
		// add highways if desired
		if (map.getHighwayFile() != null) {
			gmtCommandLines.add("# Add highways to plot");
			gmtCommandLines.add("${GMT_PATH}psxy  "+region + proj + " -K -O -W1p,125/125/125 -: "
						+ GMT_DATA_PATH + map.getHighwayFile().fileName() + " >> " + psFile+"\n");
		}
	}
	
	public static void addCoastCommand(ArrayList<String> gmtCommandLines, GMT_Map map,
			String region, String proj, String psFile) {

		// add coast and fill if desired
		CoastAttributes coastAt = map.getCoast();
		if(coastAt != null) {
			
			String fillColor = "";
			if (coastAt.getFillColor() != null) {
				fillColor = "-S" + getGMTColorString(coastAt.getFillColor());
			}
			String lineColor = "";
			if (coastAt.getLineColor() != null) {
				lineColor = "-W" + coastAt.getLineSize() + "p," + getGMTColorString(coastAt.getLineColor());
			}
			
			gmtCommandLines.add("# Draw coastline");
			gmtCommandLines.add("${GMT_PATH}pscoast "+region + proj + " -K -O " + lineColor + 
						" -P " + fillColor + " -Dh -Na/"+coastAt.getLineSize()+"p,"
						+getGMTColorString(coastAt.getLineColor())+" >> " + psFile+"\n");
		}
	}
	
	public static void addPolyCommands(ArrayList<String> gmtCommandLines, GMT_Map map,
			String region, String proj, String psFile) throws GMT_MapException {
		ArrayList<PSXYPolygon> polys = map.getPolys();
		if (polys != null && polys.size() > 0) {
			System.out.println("Map has " + polys.size() + " polygons!");
			gmtCommandLines.add("");
			gmtCommandLines.add("# Lines/Polygons");
			String polyFile = "polys.xy";
			gmtCommandLines.add("${COMMAND_PATH}cat  << END > " + polyFile);
			for (int i=0; i<polys.size(); i++) {
				PSXYPolygon poly = polys.get(i);
				if (!poly.isValid())
					throw new GMT_MapException("Polygons must have at least 2 points");
				String sep = "> " + poly.getPenString();
				if (poly.size() > 2) {
					sep += " " + poly.getFillString();
				}
				gmtCommandLines.add(sep);
				for (Point2D point : poly.getPoints()) {
					gmtCommandLines.add(point.getX() + "\t" + point.getY());
				}
			}
			gmtCommandLines.add("END");
			gmtCommandLines.add("${GMT_PATH}psxy " + polyFile + " " + region + proj
								+" -K -O >> " + psFile);
//			rmFiles.add(polyFile);
		}
	}
	
	public static void addSymbolCommands(ArrayList<String> gmtCommandLines, GMT_Map map,
			String region, String proj, String psFile) {
		ArrayList<PSXYSymbol> symbols = map.getSymbols();
		if (symbols != null && symbols.size() > 0) {
			System.out.println("Map has " + symbols.size() + " symbols!");
			gmtCommandLines.add("");
			gmtCommandLines.add("# Symbols");
//			String symbolFile = "symbols.xy";
//			gmtCommandLines.add("${COMMAND_PATH}cat  << END > " + symbolFile);
//			for (int i=0; i<symbols.size(); i++) {
//				PSXYSymbol symbol = symbols.get(i);
//				Point2D point = symbol.getPoint();
//				String line = point.getX() + "\t" + point.getY() + "\t0";
//				line += "\t" + symbol.getSymbolString() + "\t" + symbol.getFillString();
//				if (symbol.getPenColor() != null)
//					line += "\t" + symbol.getPenString();
//				gmtCommandLines.add(line);
//			}
//			gmtCommandLines.add("END");
//			gmtCommandLines.add("${GMT_PATH}psxy " + symbolFile + " " + region + projWdth + " -K -O >> " + PS_FILE_NAME);
			
			for (int i=0; i<symbols.size(); i++) {
				PSXYSymbol symbol = symbols.get(i);
				Point2D point = symbol.getPoint();
				String line = "echo " + point.getX() + " " + point.getY() + " | ${GMT_PATH}psxy "
							+ symbol.getSymbolString() + " " + symbol.getFillString();
				if (symbol.getPenColor() != null)
					line += " " + symbol.getPenString();
				line += " " + region + proj + " -K -O >> " + psFile;
				gmtCommandLines.add(line);
			}
		}
	}
	
	public static void addSymbolSetCommands(ArrayList<String> gmtCommandLines, GMT_Map map,
			String region, String proj, String psFile) {
		if (map.getSymbolSet() != null) {
			PSXYSymbolSet symSet = map.getSymbolSet();
			System.out.println("Map has a symbol set!");
			gmtCommandLines.add("");
			gmtCommandLines.add("# Symbol set");
			String symbolCPTFile = "symbol_set.cpt";
			gmtCommandLines.add("${COMMAND_PATH}cat  << END > " + symbolCPTFile);
			gmtCommandLines.add(symSet.getCpt().toString());
			gmtCommandLines.add("END");
			String symbolFile = "symbol_set.xy";
			gmtCommandLines.add("${COMMAND_PATH}cat  << END > " + symbolFile);
			List<PSXYSymbol> symbols = symSet.getSymbols();
			List<Double> vals = symSet.getVals();
			for (int i=0; i<symbols.size(); i++) {
				PSXYSymbol symbol = symbols.get(i);
				double val = vals.get(i);
				Point2D point = symbol.getPoint();
				String line = point.getX() + "\t" + point.getY() + "\t" + val + "\t"
						+ symbol.getSymbol().val() + symbol.getWidth() + "i";
				gmtCommandLines.add(line);
			}
			gmtCommandLines.add("END");
			String penStr = "";
			if (symSet.getPenColor() != null)
				penStr = " " + symSet.getPenString();
			gmtCommandLines.add("${GMT_PATH}psxy " + symbolFile + " -C" + symbolCPTFile + penStr +
					" -S " + region + proj + " -K -O >> " + psFile);
		}
	}
	
	public static void addTextCommands(ArrayList<String> gmtCommandLines, GMT_Map map,
			String region, String proj, String psFile) {
		ArrayList<PSText> text = map.getText();
		if (text != null && text.size() > 0) {
			System.out.println("Map has " + text.size() + " text items!");
			gmtCommandLines.add("");
			gmtCommandLines.add("# Text");
			for (int i=0; i<text.size(); i++) {
				PSText item = text.get(i);
				Point2D point = item.getPoint();
				String line = "echo "+point.getX()+" "+point.getY()+" "+item.getText()
						+" | ${GMT_PATH}pstext "+item.getFontArg();
				line += " " + region + proj + " -K -O >> " + psFile;
				gmtCommandLines.add(line);
			}
		}
	}
	
	public static void addSpecialElements(ArrayList<String> gmtCommandLines, GMT_Map map,
			String region, String proj, String psFile) throws GMT_MapException {
		addHighwayCommand(gmtCommandLines, map, region, proj, psFile);
		addCoastCommand(gmtCommandLines, map, region, proj, psFile);
		addPolyCommands(gmtCommandLines, map, region, proj, psFile);
		addSymbolCommands(gmtCommandLines, map, region, proj, psFile);
		addSymbolSetCommands(gmtCommandLines, map, region, proj, psFile);
		addTextCommands(gmtCommandLines, map, region, proj, psFile);
	}
	
	public static void addColorbarCommand(ArrayList<String> gmtCommandLines, GMT_Map map,
			double colorScaleMin, double colorScaleMax, String cptFile, String psFile, double plotWidth) {
		if (!map.isHideColorbar())
			addColorbarCommand(gmtCommandLines, map.getCustomLabel(), map.isLogPlot(), colorScaleMin, colorScaleMax,
					cptFile, psFile, map.isCPTEqualSpacing(), map.getCPTCustomInterval(),
					map.getLabelSize(), map.getLabelTickSize(), plotWidth);
	}
	
	public static void addColorbarCommand(ArrayList<String> gmtCommandLines, String scaleLabel, boolean isLog,
			double colorScaleMin, double colorScaleMax, String cptFile, String psFile, boolean cptEqualSpacing,
			Double customTickInterval, Integer fontSize, Integer tickFontSize, double plotWidth) {
		// add the color scale
		DecimalFormat df2 = new DecimalFormat("0.E0");
		Float tickInc;
		if (customTickInterval == null)
			tickInc = Float.valueOf(df2.format((colorScaleMax-colorScaleMin)/4.0));
		else
			tickInc = customTickInterval.floatValue();
		//checks to see if customLabel is selected, then get the custom label
		if (scaleLabel == null)
			scaleLabel = " ";
		else if (isLog)
			scaleLabel = "Log10("+scaleLabel+")";
		scaleLabel = stripFormatLabel(scaleLabel);
		gmtCommandLines.add("# Colorbar/label");
		if (fontSize != null && fontSize > 0) {
			String commandLine = "${GMT_PATH}set FONT_LABEL="+fontSize+"p,Helvetica,black";
			gmtCommandLines.add(commandLine+"\n");
		}
		if (tickFontSize != null && tickFontSize > 0) {
			String commandLine = "${GMT_PATH}set FONT_ANNOT_PRIMARY="+tickFontSize+"p,Helvetica,black";
			gmtCommandLines.add(commandLine+"\n");
		}
		
		float w = (float)Math.max(1d, plotWidth - 0.5);
		float x = (float)(plotWidth/2);
		if (cptEqualSpacing) {
			String commandLine="${GMT_PATH}psscale -L -B+l"+scaleLabel+" -D"+x+"i/-0.5i/"+w+"i/0.3ih -C"+cptFile+" -O -K -N70 >> " + psFile;
			gmtCommandLines.add(commandLine+"\n");
		} else {
			String commandLine="${GMT_PATH}psscale -Ba"+tickInc+":"+scaleLabel+": -D"+x+"i/-0.5i/"+w+"i/0.3ih -C"+cptFile+" -O -K -N70 >> " + psFile;
			gmtCommandLines.add(commandLine+"\n");
		}
	}


	/**
	 * This method allows subclasses to add intemediate lines the the GMT script.  For
	 * example, for Scenario ShakeMaps one might want to plot the Earthuake Rupture Surface.
	 * These lines have to be added at an intermediate step because the last layer in GMT
	 * has to have the "-O" but not "-K" option.
	 */
	protected void addIntermediateGMT_ScriptLines(ArrayList gmtLines, String region, String projWdth) {

	}


	/**
	 * Function to adds any final commands desired by a subclass.
	 * @param gmtCommandLines : ArrayList to store the command line
	 */
	protected void addFinalGMT_ScriptLines(ArrayList gmtCommandLines){

	}


	/**
	 * If log-plot has been chosen, this replaces the z-values in the xyzDataSet
	 * with the log (base 10) values.  Zero values are converted to 10e-16.
	 * This also wraps the SCALE_LABEL in "log(*)".
	 * @param xyzVals
	 */
	private void checkForLogPlot(){
		//checks to see if the user wants Log Plot, if so then convert the zValues to the Log Space
		boolean logPlotCheck = ((Boolean)logPlotParam.getValue()).booleanValue();
		if(logPlotCheck && xyzDataSet != null){
			xyzDataSet.log10();
			SCALE_LABEL = "\"log@-10@-\050"+SCALE_LABEL+"\051\"";
		}
	}

	/**
	 * This simply saves the supplied string to an ascii file that is placed in the
	 * same directory where the image, gmt script, etc. are placed.  The name of the file is in
	 * the METADATA_FILE_NAME String.  This is simply a method for saving arbitrary
	 * metatdata associated with a map.
	 */
	public void createMapInfoFile(String mapInfo){
		ArrayList mapInfoLines = new ArrayList();
		StringTokenizer st = new StringTokenizer(mapInfo,"\n");
		while(st.hasMoreTokens())
			mapInfoLines.add(st.nextToken());
		makeFileFromLines(mapInfoLines,METADATA_FILE_NAME);
	}

}

