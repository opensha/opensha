package scratch.UCERF3.erf.ETAS;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.dom4j.DocumentException;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.ConsoleWindow;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.ButtonParameter;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.FileParameter;
import org.opensha.commons.param.impl.LongParameter;
import org.opensha.commons.param.impl.ParameterListParameter;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.LastEventData;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.kevin.ucerf3.etas.MPJ_ETAS_Simulator;

public class ETAS_SimulationGUI extends JFrame implements ParameterChangeListener {
	
	private ParameterList paramList;
	private ETAS_ParameterList etasParams;
	
	private FileParameter outputDirectoryParam;
	private FileParameter cacheDirectoryParam;
	private FileParameter fssFileParam;
	private FileParameter inputCatalogParam;
	
	private EnumParameter<ETAS_Simulator.TestScenario> scenarioParam;
	
	private BooleanParameter includeSpontEventsParam;
	private BooleanParameter includeIndirectTriggeringParam;
	
	private static final String fract_sect_in_cube_file_name = "fractSectInCubeCache.bin";
	private static final String sect_in_cube_file_name = "sectInCubeCache.bin";
	private static final String cube_inside_poly_file_name = "cubeInsidePolyCache.bin";
	private static final String cache_url =
			"http://opensha.usc.edu/ftp/kmilner/ucerf3/etas_cache/fm3_1_mean_fss/";
	private static final String fss_file_name =
			"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip";
//			"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
	
	private ButtonParameter calcButton;
	
	private LongParameter randSeedParam;
	private BooleanParameter debugParam;
	private BooleanParameter pauseParam;
	
	private ConsoleWindow console;
	
	private FaultSystemSolution sol;
	private List<float[]> fractionSectInCubeCache;
	private List<int[]> sectInCubeCache;
	private int[] cubeInsidePolyCache;
	private Map<Integer, List<LastEventData>> lastEventData;
	
	private ParameterListEditor editor;
	
	private CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
	
	
	public ETAS_SimulationGUI() {
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		console = new ConsoleWindow();
		
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
		
		etasParams = new ETAS_ParameterList();
		paramList = new ParameterList();
		
		outputDirectoryParam = new FileParameter("Output Directory (REQUIRED)");
		outputDirectoryParam.setDirectorySelect(true);
		paramList.addParameter(outputDirectoryParam);
		
		cacheDirectoryParam = new FileParameter("Cache Directory (optional)");
		cacheDirectoryParam.setDirectorySelect(true);
		cacheDirectoryParam.setInfo("Directory where input files are cached. Should contain "
				+fract_sect_in_cube_file_name+", "+sect_in_cube_file_name
				+", and "+cube_inside_poly_file_name+", or they will be downloaded"
						+ " on demand from "+cache_url+"\nDefault: ~/.opensha/etas_cache");
		File defaultCacheDir = new File(new File(System.getProperty("user.home")),
				".opensha"+File.separator+"etas_cache");
		if (!defaultCacheDir.exists())
			defaultCacheDir.mkdirs();
		if (defaultCacheDir.exists())
			cacheDirectoryParam.setValue(defaultCacheDir);
		paramList.addParameter(cacheDirectoryParam);
		
		fssFileParam = new FileParameter("Fault System Solution File (optional)");
		fssFileParam.addParameterChangeListener(this);
		paramList.addParameter(fssFileParam);
		
		ParameterListParameter etasParamsParam = new ParameterListParameter("ETAS Parameters", etasParams);
		paramList.addParameter(etasParamsParam);
		
		includeSpontEventsParam = new BooleanParameter("Include Spontaneous Events", true);
		paramList.addParameter(includeSpontEventsParam);
		
		includeIndirectTriggeringParam = new BooleanParameter("Include Indirect Triggering", true);
		paramList.addParameter(includeIndirectTriggeringParam);
		
		scenarioParam = new EnumParameter<ETAS_Simulator.TestScenario>("Scenario", EnumSet.allOf(ETAS_Simulator.TestScenario.class), null, "(none)");
		paramList.addParameter(scenarioParam);
		
		inputCatalogParam = new FileParameter("Input Historical Catalog (optional)");
		paramList.addParameter(inputCatalogParam);
		
		randSeedParam = new LongParameter("Random Seed");
		paramList.addParameter(randSeedParam);
		
		debugParam = new BooleanParameter("Show Debug Messages/Plots", true);
		paramList.addParameter(debugParam);
		
		pauseParam = new BooleanParameter("Pause Before Event Generation", false);
		paramList.addParameter(pauseParam);
		
		calcButton = new ButtonParameter("ETAS Simulation", "Start Simulation");
		calcButton.addParameterChangeListener(this);
		paramList.addParameter(calcButton);
		
		editor = new ParameterListEditor(paramList);
		
		setContentPane(editor);
		
		setSize(400, 800);
		setTitle("ETAS Simulation GUI");
		setLocationRelativeTo(null);
		setVisible(true);
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == calcButton) {
			final ETAS_SimulationGUI gui = this;
			new Thread() {
				@Override
				public void run() {
					editor.setEnabled(false);
					try {
						calculate();
					} catch (Throwable e) {
						e.printStackTrace();
						if (e instanceof OutOfMemoryError)
							JOptionPane.showMessageDialog(gui,
									"Run again allocating more memory\nex for 6 GB: java -Xmx6G <jar-file-name>",
									"Out of Memory!", JOptionPane.ERROR_MESSAGE);
						else
							JOptionPane.showMessageDialog(gui, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					}
					editor.setEnabled(true);
				}
			}.start();
		} else  if (event.getParameter() == fssFileParam) {
			sol =  null;
		}
	}
	
	private void calculate() throws IOException, DocumentException {
		console.setVisible(true);
		System.out.println("Calculating");
		
		ETAS_Simulator.D = debugParam.getValue();
		ETAS_Simulator.pause_for_events = pauseParam.getValue();
		
		Long ot = Math.round((2014.0-1970.0)*ProbabilityModelsCalc.MILLISEC_PER_YEAR); // occurs at 2014
		
		// output directory
		File outputDir = outputDirectoryParam.getValue();
		Preconditions.checkNotNull(outputDir, "Must Slect Output Directory");
		Preconditions.checkState(outputDir.exists() && outputDir.isDirectory(),
				"Output directory doesn't exist or isn't a directory");
		
		// cache directory
		File cacheDir = cacheDirectoryParam.getValue();
		Preconditions.checkNotNull(cacheDir, "Must Slect Cache Directory - "
				+ "for some reason this couldn't be set by default, please select a directory where we can save files");
		Preconditions.checkState(cacheDir.exists() && cacheDir.isDirectory(),
				"Cache directory doesn't exist or isn't a directory");
		
		// check cache files
		System.out.println("Checking cahces");
		try {
			if (fractionSectInCubeCache == null) {
				File fractionSectInCubeFile = new File(cacheDir, fract_sect_in_cube_file_name);
				if (!fractionSectInCubeFile.exists()) {
					System.out.println("Fraction sect in cube cache file doesn't exist, downloading");
					FileUtils.downloadURL(cache_url+fract_sect_in_cube_file_name, fractionSectInCubeFile);
				}
				System.out.println("Loading fraction sect in cube cache file");
				fractionSectInCubeCache = MatrixIO.floatArraysListFromFile(fractionSectInCubeFile);
				System.out.println("Done loading fraction sect in cube cache file");
			}
			if (sectInCubeCache == null) {
				File sectInCubeFile = new File(cacheDir, sect_in_cube_file_name);
				if (!sectInCubeFile.exists()) {
					System.out.println("Sect in cube cache file doesn't exist, downloading");
					FileUtils.downloadURL(cache_url+sect_in_cube_file_name, sectInCubeFile);
				}
				System.out.println("Loading sect in cube cache file");
				sectInCubeCache = MatrixIO.intArraysListFromFile(sectInCubeFile);
				System.out.println("Done loading sect in cube cache file");
			}
			if (cubeInsidePolyCache == null) {
				File srcAtPointListFile = new File(cacheDir, cube_inside_poly_file_name);
				if (!srcAtPointListFile.exists()) {
					System.out.println("Cube inside poly cache file doesn't exist, downloading");
					FileUtils.downloadURL(cache_url+cube_inside_poly_file_name, srcAtPointListFile);
				}
				System.out.println("Loading cube inside polygon cache file");
				cubeInsidePolyCache = MatrixIO.intArrayFromFile(srcAtPointListFile);
				System.out.println("Done loading cube inside polygon cache file");
			}
		} catch (IllegalStateException e) {
			JOptionPane.showMessageDialog(null,
					"Try deleting the contents of "+cacheDir.getAbsolutePath()+" and run again. "
							+ "Be patient as the cached files are downloaded next time you run.",
					"Bad cached input file", JOptionPane.ERROR_MESSAGE);
			throw e;
		}
		System.out.println("Done loading caches");
		
		System.out.println("Will save results to: "+outputDir.getAbsolutePath());
		Long randomSeed = randSeedParam.getValue();
		if (randomSeed == null)
			randomSeed = System.currentTimeMillis();
		System.out.println("Random seed: "+randomSeed);
		
		if (sol == null) {
			// load solution
			File fssFile = fssFileParam.getValue();
			if (fssFile == null) {
				fssFile = new File(cacheDir, fss_file_name);
				if (!fssFile.exists()) {
					System.out.println("Downloading Default Fault System Solution");
					FileUtils.downloadURL(cache_url+fss_file_name, fssFile);
				}
			}
			sol = FaultSystemIO.loadSol(fssFile);
		}
		
		if (lastEventData == null)
			lastEventData = LastEventData.load();
		LastEventData.populateSubSects(sol.getRupSet().getFaultSectionDataList(), lastEventData);
		
		FaultSystemSolutionERF_ETAS erf = MPJ_ETAS_Simulator.buildERF(sol, false, 1d);
		erf.updateForecast();
		
		ObsEqkRupList histQkList;
		if (inputCatalogParam.getValue() != null)
			histQkList = UCERF3_CatalogParser.loadCatalog(inputCatalogParam.getValue());
		else
			histQkList = new ObsEqkRupList();
		
		ETAS_Simulator.TestScenario scenario = scenarioParam.getValue();
		ETAS_EqkRupture scenarioRup = ETAS_Simulator.buildScenarioRup(scenario, erf);
		
		ETAS_Simulator.testETAS_Simulation(outputDir, erf, griddedRegion, scenarioRup, histQkList,
				includeSpontEventsParam.getValue(), includeIndirectTriggeringParam.getValue(),
				griddedRegion.getLatSpacing(), null, randomSeed, fractionSectInCubeCache, sectInCubeCache,
				cubeInsidePolyCache, etasParams);
		
		System.out.println("Done calculating");
	}
	
	public static void main(String[] args) {
		new ETAS_SimulationGUI();
	}

}
