package org.opensha.sha.calc.IM_EventSet;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;

import org.apache.commons.cli.*;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.DevStatus;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.calc.IM_EventSet.outputImpl.OriginalModCsvWriter;
import org.opensha.sha.calc.IM_EventSet.outputImpl.HAZ01Writer;
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.calc.sourceFilters.params.SourceFiltersParam;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel02.Frankel02_AdjustableEqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF1.WGCEP_UCERF1_EqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.USGS_Combined_2004_AttenRel;

import com.google.common.base.Preconditions;

import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;
import org.opensha.sha.util.SiteTranslator;
import scratch.UCERF3.erf.mean.MeanUCERF3;
import scratch.UCERF3.erf.mean.MeanUCERF3.Presets;



/**
 * <p>Title: IMEventSetCalculatorCLT</p>
 *
 * <p>Description: This class computes the Mean and Sigma for any Attenuation
 * supported and any IMT supported by these AttenuationRelationships.
 * Sites information is read from an input file.
 * </p>
 *
 * @author Ned Field, Nitin Gupta and Vipin Gupta
 * @version 1.0
 */
public class IMEventSetCalculatorCLT extends AbstractIMEventSetCalc
implements ParameterChangeWarningListener {

	protected LocationList locList;

    // Selected ERF
	protected ERF forecast;

	// Supported Attenuations
	protected ArrayList<ScalarIMR> chosenAttenuationsList;

	// Static IMT names
	protected ArrayList<String> supportedIMTs;

	protected String dirName = "MeanSigma"; // default output dir name
	private File outputDir;

	private ArrayList<ParameterList> userDataVals;

    private final SourceFilterManager sourceFilters;

    // All supported ERFs - call .instance() to get the BaseERF
    private static final Set<ERF_Ref> erfRefs = ERF_Ref.get(false, false, ServerPrefUtils.SERVER_PREFS);
    // Map of ERF names to their references for quick lookup
    private static final Map<String, ERF_Ref> erfNameMap = new HashMap<>();
    private static final ArrayList<String> erfShortNames = new ArrayList<>();
    private static final ArrayList<String> erfLongNames = new ArrayList<>();

    /**
	 *  ArrayList that maps picklist attenRel string names to the real fully qualified
	 *  class names
	 */
	private static final ArrayList<String> attenRelClasses = new ArrayList<>();
	private static final ArrayList<String> imNames = new ArrayList<>();

	static {
        // Initialize ERF name map
        for (ERF_Ref ref : erfRefs) {
            // Allow users to reference ERF by long or short names
            String erfName = ref.toString();
            String erfShortName = ref.getERFClass().getSimpleName();
            erfNameMap.put(erfName, ref);
            erfNameMap.put(erfShortName, ref);
            erfShortNames.add(erfShortName);
            erfLongNames.add(erfName);
        }

//		imNames.add(CY_2006_AttenRel.NAME);
//		attenRelClasses.add(CY_2006_AttenRel.class.getName());
//		imNames.add(CY_2008_AttenRel.NAME);
//		attenRelClasses.add(CY_2008_AttenRel.class.getName());
//		imNames.add(CB_2006_AttenRel.NAME);
//		attenRelClasses.add(CB_2006_AttenRel.class.getName());
//		imNames.add(CB_2008_AttenRel.NAME);
//		attenRelClasses.add(CB_2008_AttenRel.class.getName());
//		imNames.add(BA_2006_AttenRel.NAME);
//		attenRelClasses.add(BA_2006_AttenRel.class.getName());
//		imNames.add(BA_2008_AttenRel.NAME);
//		attenRelClasses.add(BA_2008_AttenRel.class.getName());
//		imNames.add(CS_2005_AttenRel.NAME);
//		attenRelClasses.add(CS_2005_AttenRel.class.getName());
//		imNames.add(BJF_1997_AttenRel.NAME);
//		attenRelClasses.add(BJF_1997_AttenRel.class.getName());
//		imNames.add(AS_1997_AttenRel.NAME);
//		attenRelClasses.add(AS_1997_AttenRel.class.getName());
//		imNames.add(AS_2008_AttenRel.NAME);
//		attenRelClasses.add(AS_2008_AttenRel.class.getName());
//		imNames.add(Campbell_1997_AttenRel.NAME);
//		attenRelClasses.add(Campbell_1997_AttenRel.class.getName());
//		imNames.add(SadighEtAl_1997_AttenRel.NAME);
//		attenRelClasses.add(SadighEtAl_1997_AttenRel.class.getName());
//		imNames.add(Field_2000_AttenRel.NAME);
//		attenRelClasses.add(Field_2000_AttenRel.class.getName());
//		imNames.add(Abrahamson_2000_AttenRel.NAME);
//		attenRelClasses.add(Abrahamson_2000_AttenRel.class.getName());
//		imNames.add(CB_2003_AttenRel.NAME);
//		attenRelClasses.add(CB_2003_AttenRel.class.getName());
//		imNames.add(BS_2003_AttenRel.NAME);
//		attenRelClasses.add(BS_2003_AttenRel.class.getName());
//		imNames.add(BC_2004_AttenRel.NAME);
//		attenRelClasses.add(BC_2004_AttenRel.class.getName());
//		imNames.add(GouletEtAl_2006_AttenRel.NAME);
//		attenRelClasses.add(GouletEtAl_2006_AttenRel.class.getName());
//		imNames.add(ShakeMap_2003_AttenRel.NAME);
//		attenRelClasses.add(ShakeMap_2003_AttenRel.class.getName());
//		imNames.add(SEA_1999_AttenRel.NAME);
//		attenRelClasses.add(SEA_1999_AttenRel.class.getName());

		for (AttenRelRef ref : AttenRelRef.get(ServerPrefUtils.SERVER_PREFS)) {
			try {
				String name = ref.getName();
				String className = ref.getAttenRelClass().getName();
				imNames.add(name);
				attenRelClasses.add(className);
			} catch (Exception e) {
				// skip that IMR
			}
		}
	}

    /**
     * Constructor parses inputs and sets params
     * @param erfName           String for short or long name of the ERF
     * @param bgSeismicity
     * @param rupOffset
     * @param attenRelNames     All IMR names as list of strings
     * @param imtNames          All IMT names as list of strings
     * @param siteFile
     * @param outDir
     */
    public IMEventSetCalculatorCLT(String erfName,
                                   String bgSeismicity,
                                   double rupOffset,
                                   double duration,
                                   ArrayList<String> attenRelNames,
                                   ArrayList<String> imtNames,
                                   String siteFile,
                                   String outDir) {
        // source filters have fixed-cutoff distance of 200km by default
        sourceFilters = SourceFiltersParam.getDefault();

        getERF(erfName, duration);
        if (bgSeismicity != null) toApplyBackGround(bgSeismicity);
        setRupOffset(rupOffset);
        for (String attenRel : attenRelNames) {
            setIMR(attenRel);
        }
        for (String imt : imtNames) {
            setIMT(imt);
        }
        if (siteFile == null || siteFile.isEmpty()) {
            // If no sites file provided, default to 1 site in LA with Vs30 760m/s
            // "34.1 -118.1 760"
            logger.log(Level.INFO, "No site file provided, defaulting to LA (34.1,-118.1) with Vs30 760m/s");
            locList = new LocationList(List.of(new Location(34.1,-118.1)));
            Vs30_Param vs30Param = new Vs30_Param();
            vs30Param.setValue(760);
            Vs30_TypeParam type = new Vs30_TypeParam();
            type.setValue(SiteData.TYPE_FLAG_MEASURED);
            ParameterList params = new ParameterList();
            params.addParameter(vs30Param);
            params.addParameter(type);
            userDataVals = new ArrayList<>(List.of(params));
        } else {
            try {
                parseSitesInputCSV(siteFile);
            } catch (Exception ex) {
                logger.log(Level.INFO, "Error parsing input file!", ex);
                System.exit(1);
            }
        }
        if (!(outDir == null || outDir.isEmpty())) {
            dirName = outDir;
            outputDir = new File(dirName);
        }
    }

    /**
     * Sites are collected directly from a CSV file
     * @param siteFile
     */
    private void parseSitesInputCSV(String siteFile) throws IOException {
        ArrayList<String> fileLines;
        SiteFileLoader loader = new SiteFileLoader(/*lonFirst=*/false,
                SiteData.TYPE_FLAG_MEASURED,
                new ArrayList<>(List.of(SiteFileLoader.allSiteDataTypes)));
        try {
            loader.loadFile(new File(siteFile));
        } catch (java.text.ParseException e) {
            logger.log(Level.SEVERE, "Failed to parse the sites input file.");
            throw new RuntimeException(e);
        }
        locList = new LocationList(loader.getLocs());

        ParameterList defaultParams = new ParameterList();
        defaultParams.addParameter(new Vs30_Param());
        defaultParams.addParameter(new Vs30_TypeParam());
        defaultParams.addParameter(new DepthTo1pt0kmPerSecParam());
        defaultParams.addParameter(new DepthTo2pt5kmPerSecParam());

        userDataVals = new ArrayList<>();
        SiteTranslator siteTrans = new SiteTranslator();
        for (ArrayList<SiteDataValue<?>> siteVals : loader.getValsList()) {
            ParameterList params = (ParameterList) defaultParams.clone();
            params.forEach(param -> siteTrans.setParameterValue(param, siteVals));
            userDataVals.add(params);
        }
    }

	/**
	 * Sets the supported IMTs as String
	 * @param line String
	 */
	private void setIMT(String line) {
		if(supportedIMTs == null)
			supportedIMTs = new ArrayList<String>();
		this.supportedIMTs.add(line.trim());
	}

	/**
	 * Creates the IMR instances and adds to the list of supported IMRs
	 * @param str String
	 */
	private void setIMR(String str) {
		if(chosenAttenuationsList == null)
			chosenAttenuationsList = new ArrayList<ScalarIMR>();
		String imrName = str.trim();
		int index = imNames.indexOf(imrName);
		createIMRClassInstance(attenRelClasses.get(index));
	}

	/**
	 * Creates a class instance from a string of the full class name including packages.
	 * This is how you dynamically make objects at runtime if you don't know which\
	 * class beforehand. For example, if you wanted to create a BJF_1997_AttenRel you can do
	 * it the normal way:<P>
	 *
	 * <code>BJF_1997_AttenRel imr = new BJF_1997_AttenRel()</code><p>
	 *
	 * If your not sure the user wants this one or AS_1997_AttenRel you can use this function
	 * instead to create the same class by:<P>
	 *
	 * <code>BJF_1997_AttenRel imr =
	 * (BJF_1997_AttenRel)ClassUtils.createNoArgConstructorClassInstance("org.opensha.sha.imt.attenRelImpl.BJF_1997_AttenRel");
	 * </code><p>
	 *
	 */
	protected void createIMRClassInstance(String AttenRelClassName) {
		try {
			Class listenerClass = ParameterChangeWarningListener.class;
			Object[] paramObjects = new Object[] {
					this};
			Class[] params = new Class[] {
					listenerClass};
			Class imrClass = Class.forName(AttenRelClassName);
			Constructor con = imrClass.getConstructor(params);
			AttenuationRelationship attenRel = (AttenuationRelationship) con.newInstance(paramObjects);
			if(attenRel.getName().equals(USGS_Combined_2004_AttenRel.NAME))
				throw new RuntimeException("Cannot use "+USGS_Combined_2004_AttenRel.NAME+" in calculation of Mean and Sigma");
			//setting the Attenuation with the default parameters
			attenRel.setParamDefaults();
			chosenAttenuationsList.add(attenRel);
		}
		catch (ClassCastException e) {
			e.printStackTrace();
		}
		catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		catch (InstantiationException e) {
			e.printStackTrace();
		}
	}

    /**
     * Creates an ERF instance from the string parsed as an erfName.
     * ERFs are created with default parameters, with a few hardcoded exceptions.
     * @param line user input to parse into an erfName
     * @param duration duration of the ERF in years
     */
	private void getERF(String line, double duration) {
		String erfName = line.trim();
		logger.log(Level.CONFIG, "Attempting to identify ERF from name: " + erfName);

        ERF_Ref erfRef = erfNameMap.get(erfName);
        if (erfRef != null) {
            logger.log(Level.CONFIG, "Creating ERF dynamically with default parameters.");
            forecast = (ERF)erfRef.instance();
        } else {
            throw new RuntimeException("Unsupported ERF");
        }
        try {
            forecast.getTimeSpan().setDuration(duration);
            logger.log(Level.FINE, "Set duration to " + duration + " for ERF: " + erfName);
        } catch (ConstraintException e) {
            // ERF has constraints that don't allow specified duration, use default duration
            if (forecast.getTimeSpan() == null)
                logger.log(Level.WARNING, "ERF " + erfName + " does not have a TimeSpan.");
            else
                logger.log(Level.WARNING, "ERF " + erfName + " does not allow duration=1.0, " +
                        "using default duration: " + forecast.getTimeSpan().getDuration());
        }
	}

	private void toApplyBackGround(String toApply){
		try {
			Parameter param = forecast.getAdjustableParameterList().getParameter(
					Frankel02_AdjustableEqkRupForecast.BACK_SEIS_NAME);
			logger.log(Level.FINE, "Setting ERF background seismicity value: " + toApply);
			if (param instanceof StringParameter) {
				param.setValue(toApply);
			} else if (param instanceof IncludeBackgroundParam) {
				IncludeBackgroundOption val = IncludeBackgroundOption.valueOf(toApply.trim().toUpperCase());
				param.setValue(val);
			}
		} catch (ParameterException e) {
			logger.log(Level.WARNING, "ERF doesn't contain param '"+Frankel02_AdjustableEqkRupForecast.
					BACK_SEIS_NAME+"', ignoring setting.");
		}
		if(!(forecast instanceof MeanUCERF2)) {
			if (forecast.getAdjustableParameterList().containsParameter(
					Frankel02_AdjustableEqkRupForecast.
					BACK_SEIS_RUP_NAME)) { 	
				Parameter param = forecast.getAdjustableParameterList().getParameter(
						Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_NAME);
				if (param instanceof StringParameter)
					param.setValue(Frankel02_AdjustableEqkRupForecast.
								BACK_SEIS_RUP_FINITE);
				else if (param instanceof BackgroundRupParam)
					param.setValue(BackgroundRupType.FINITE);
			}
		}

	}

	private void setRupOffset(double rupOffset){
		if (forecast.getAdjustableParameterList().containsParameter(Frankel02_AdjustableEqkRupForecast.
				RUP_OFFSET_PARAM_NAME)) {
			logger.log(Level.FINE, "Setting ERF rupture offset: " + rupOffset);
			forecast.getAdjustableParameterList().getParameter(
					Frankel02_AdjustableEqkRupForecast.
					RUP_OFFSET_PARAM_NAME).setValue(Double.valueOf(rupOffset));
		} else {
			logger.log(Level.WARNING, "ERF doesn't contain param '"+Frankel02_AdjustableEqkRupForecast.
					RUP_OFFSET_PARAM_NAME+"', ignoring setting.");
		}
		forecast.updateForecast();
	}
	
	/**
	 * Starting with the Mean and Sigma calculation.
	 * Creates the directory to put the mean and sigma files.
	 * @throws IOException 
	 */
	public void getMeanSigma() throws IOException {
		getMeanSigma(false);
	}
	
	/**
	 * Starting with the Mean and Sigma calculation.
	 * Creates the directory to put the mean and sigma files.
	 * @throws IOException 
	 */
	public void getMeanSigma(boolean haz01) throws IOException {

		int numIMRs = chosenAttenuationsList.size();
		File file = new File(dirName);
		file.mkdirs();
		IM_EventSetOutputWriter writer = null;
		if (haz01) {
			writer = new HAZ01Writer(this);
		} else {
			writer = new OriginalModCsvWriter(this);
		}
		writer.writeFiles(forecast, chosenAttenuationsList, supportedIMTs);
	}

	/**
	 *  Function that must be implemented by all Listeners for
	 *  ParameterChangeWarnEvents.
	 *
	 * @param e The Event which triggered this function call
	 */
	public void parameterChangeWarning(ParameterChangeWarningEvent e) {

		String S = " : parameterChangeWarning(): ";

		WarningParameter param = e.getWarningParameter();

		param.setValueIgnoreWarning(e.getNewValue());

	}

    private static void listERFs() {
        System.out.println("Available Earthquake Rupture Forecasts (ERFs):");
        System.out.println("==============================================");
        System.out.println();

        // Find the maximum length of short names for proper alignment
        int maxShortNameLength = 0;
        for (String shortName : erfShortNames) {
            if (shortName.length() > maxShortNameLength) {
                maxShortNameLength = shortName.length();
            }
        }
        // Ensure minimum width for alignment
        maxShortNameLength = Math.max(maxShortNameLength, 10);

        for (int i = 0; i < erfShortNames.size(); i++) {
            String shortName = erfShortNames.get(i);
            String longName = erfLongNames.get(i);
            // Format the output with fixed width for short name
            System.out.printf("%-" + maxShortNameLength + "s – %s%n", shortName, longName);
        }

        System.out.println();
        System.out.println("Usage: imcalc -e \"FULL_NAME\" ... or imcalc -e ShortName ...");
        System.out.println("Example: imcalc -e \"STEP Alaskan Pipeline ERF\" ...");
        System.out.println("Example: imcalc -e STEP_AlaskanPipeForecast ...");
    }

    /**
     * How to use the CLT. Use `--help` to see this.
     * @param options
     */
    private static void printUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null); // Preserve declaration order

        String header = "\nIM Event Set Calculator - Compute Mean and Sigma for Attenuation Relationships and IMTs\n\n";

        String footer = "\nExample:\n" +
                "  imcalc -e \"WGCEP (2007) UCERF2 - Single Branch\" \\\n" +
                "         -b Exclude \\\n" +
                "         -a \"Boore & Atkinson (2008)\",\"Chiou & Youngs (2008)\" \\\n" +
                "         -m \"PGA,SA20,SA 1.0\" \\\n" +
                "         -r 5 \\\n" +
                "         -s sites.csv \\\n" +
                "         -o results/\n\n" +
                "Note: Site data values are limited to Vs30, Z1.0, Z2.5 in CSV file.\n" +
                "      Use IMEventSetCalculatorGUI for more refined control of Site Parameters.\n";

        formatter.printHelp("imcalc ",
                header, options, footer, true);
    }

    private static Level getLogLevel(CommandLine cmd) {
        if (cmd.hasOption("ddd")) return Level.ALL;
        if (cmd.hasOption("dd")) return Level.FINE;
        if (cmd.hasOption("d")) return Level.CONFIG;
        if (cmd.hasOption("q")) return Level.OFF;
        return Level.WARNING; // default
    }

    /**
     * Creates all options
     * @return
     */
    private static Options createOptions() {
        Options options = new Options();

        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Show this help and exit.")
                .build());

        options.addOption(Option.builder()
                .longOpt("list-erfs")
                .desc("List available ERF short names and long names")
                .build());

        options.addOption(Option.builder()
                .longOpt("haz01")
                .desc("Use HAZ01 output file format instead of default")
                .build());

        options.addOption(Option.builder("d")
                .desc("Set logging level to CONFIG (verbose)")
                .build());

        options.addOption(Option.builder("dd")
                .desc("Set logging level to FINE (very verbose)")
                .build());

        options.addOption(Option.builder("ddd")
                .desc("Set logging level to ALL (debug)")
                .build());

        options.addOption(Option.builder("q")
                .longOpt("quiet")
                .desc("Set logging level to OFF (quiet)")
                .build());

        options.addOption(Option.builder("e")
                .longOpt("erf")
                .hasArg()
                .argName("name")
                .desc("Earthquake Rupture Forecast (ERF) - short code or full name in quotes")
                .build());

        options.addOption(Option.builder("b")
                .longOpt("background-seismicity")
                .hasArg()
                .argName("type")
                .desc("Include | Exclude | Only-Background")
                .build());

        options.addOption(Option.builder("r")
                .longOpt("rupture-offset")
                .hasArg()
                .argName("km")
                .desc("Rupture offset for floating ruptures (1-100 km; 5 km is generally best).")
                .build());

        options.addOption(Option.builder("t")
                .longOpt("duration")
                .hasArg()
                .argName("years")
                .desc("Duration in years. Defaults to 1 if not provided.")
                .build());

        OptionGroup attenRelGroup = new OptionGroup();
        attenRelGroup.addOption(Option.builder("a")
                .longOpt("atten-rels")
                .hasArg()
                .argName("IMR1,IMR2,...")
                .desc("Comma-separated in quotation attenuation relations")
                .build());
        attenRelGroup.addOption(Option.builder("f")
                .longOpt("atten-rels-file")
                .hasArg()
                .argName("file")
                .desc("Newlines-separated IMR list (mutually exclusive with --atten-rels)")
                .build());
        options.addOptionGroup(attenRelGroup);

        options.addOption(Option.builder("m")
                .longOpt("imts")
                .hasArg()
                .argName("IMT1,IMT2,...")
                .desc("Comma-separated intensity-measure types")
                .build());

        options.addOption(Option.builder("s")
                .longOpt("sites")
                .hasArg()
                .argName("csv-file")
                .desc("CSV of Lat, Lon, [Vs30/Wills] (column optional)")
                .build());

        options.addOption(Option.builder("o")
                .longOpt("output-dir")
                .hasArg()
                .argName("dir")
                .desc("Where to write results (defaults to current dir)")
                .build());

        return options;
    }

    /**
     * Process input parameters
     * @param cmd All arguments for parsing
     * @return CLT instance
     */
    private static IMEventSetCalculatorCLT processInput(CommandLine cmd) {
        Options options = createOptions();
        // Handle help option
        if (cmd.hasOption("help")) {
            printUsage(options);
            System.exit(0);
        }
        // Show ERF options
        if (cmd.hasOption("list-erfs")) {
            listERFs();
            System.exit(0);
        }

        // Check required OptionGroup for attenuation relationships
        boolean hasAttenRels = cmd.hasOption("atten-rels");
        boolean hasAttenRelsFile = cmd.hasOption("atten-rels-file");
        if (!(hasAttenRels || hasAttenRelsFile)) {
            System.err.println("Error: At least one of --atten-rels or --atten-rels-file must be specified");
            printUsage(options);
            System.exit(2);
        }
        // Check for required options without mutual exclusivity
        String[] requiredOptions = {"erf", "imts", "output-dir"};
        for (String option : requiredOptions) {
            if (!cmd.hasOption(option)) {
                System.err.println("Error: Required option --" + option + " is missing");
                printUsage(options);
                System.exit(2);
            }
        }

        String erfName = cmd.getOptionValue("erf");
        // bgSeis value is ignored if ERF doesn't support it
        String bgSeis = cmd.hasOption("background-seismicity")
                ? cmd.getOptionValue("background-seismicity") : null;
        double rupOffset = cmd.hasOption("rupture-offset")
                ? Double.parseDouble(cmd.getOptionValue("rupture-offset")) : Double.NaN;
        ArrayList<String> attenRels;
        if (hasAttenRels) {
            attenRels = parseQuotedString(cmd.getOptionValue("atten-rels"));
            System.out.println(attenRels);
        } else {
            try {
                attenRels = FileUtils.loadFile(cmd.getOptionValue("atten-rels-file"));
                // Filter comments out of attenRels input TXT file
                attenRels.removeIf(s -> s.startsWith("#"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        ArrayList<String> imts = new ArrayList<>(List.of(cmd.getOptionValue("imts").split(",")));

        String sites = cmd.hasOption("sites") ? cmd.getOptionValue("sites") : null;
        double duration = cmd.hasOption("duration")
                ? Double.parseDouble(cmd.getOptionValue("duration")) : 1.0;

        String outputDirName = cmd.getOptionValue("output-dir");

        return new IMEventSetCalculatorCLT(erfName, bgSeis, rupOffset, duration,
                attenRels, imts, sites, outputDirName);
    }

    /**
     * A string of values where each value in quotes is separated into a list
     * @param csvString     "first","second, with a comma","third"
     * @return              ["first", "second, with a comma", "third"]
     */
    private static ArrayList<String> parseQuotedString(String csvString) {
        ArrayList<String> values = new ArrayList<>();
        if (csvString == null || csvString.trim().isEmpty()) {
            return values;
        }

        // Split by comma, but be careful with quoted strings
        // This regex splits on commas that are NOT inside quotes
        String[] parts = csvString.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("\"") && part.endsWith("\"")) {
                // Remove surrounding quotes
                values.add(part.substring(1, part.length() - 1));
            } else {
                values.add(part);
            }
        }
        return values;
    }


    /**
     * Entry point to CLT.
     * Processes command line input and invokes the calculator.
     * Exits with 0 on success, 1 on failure, and 2 on invalid input.
     * @param args
     */
	public static void main(String[] args) {
        // First, parse with full options to detect mode
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args, true); // Stop at non-option
        } catch (ParseException e) {
            System.err.println("Error parsing command line: " + e.getMessage());
            printUsage(options);
            System.exit(2);
        }

        Level level = getLogLevel(cmd);
        initLogger(level);

        // Output mode
        boolean haz01 = cmd.hasOption("haz01");

        IMEventSetCalculatorCLT calc = processInput(cmd);

        // Invoke calculator
        try {
            calc.getMeanSigma(haz01);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);

	}

	public int getNumSites() {
		return locList.size();
	}

	public File getOutputDir() {
		return outputDir;
	}

    @Override
    public List<SourceFilter> getSourceFilters() {
        return sourceFilters.getEnabledFilters();
    }

    public Location getSiteLocation(int i) {
		return locList.get(i);
	}

	public ParameterList getUserSiteData(int i) {
		return userDataVals.get(i);
	}
}
