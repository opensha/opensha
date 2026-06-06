package scratch.UCERF3.erf.ETAS.analysis;

import org.apache.commons.cli.*;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;

import java.io.File;
import java.io.IOException;
import java.util.stream.StreamSupport;

import static scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.getBinaryCatalogsIterable;
import static scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;

/**
 * CLT analyzes binary results from UCERF3-ETAS simulations and generates
 * a markdown analysis report.
 */
public class ETAS_BinaryAnalysis {

    File binFile; // UCERF3-ETAS results binary
    FaultSystemSolution fss; // Fault System Solution as defined by the ZIP file
    int aftershock; // FSS ID of the aftershock scenario
    // Smallest magnitude to consider from binary catalogs
    private static final double MIN_MAG = 2.5;

    public ETAS_BinaryAnalysis(CommandLine cmd) throws IOException {
        this.binFile = new File(cmd.getOptionValue("bin"));
        // TODO: fss will be used eventually for identifying parent section IDs from partials
        this.fss = FaultSystemSolution.load(new File(cmd.getOptionValue("fss")));
        this.aftershock = Integer.parseInt(cmd.getOptionValue("idx"));
    }

    /**
     * In how many of the simulations does the aftershock scenario actually occur?
     * This method checks how many simulated catalogs above a minimum magnitude
     * see a rupture matching the aftershock scenario.
     * <p>
     * Note that this only counts the number of catalogs where at least one rupture
     * matches, but does not count multiple times if multiple ruptures match.
     * </p>
     * @param fssIndex The FSS index that defines the scenario of interest
     * @return
     */
    private int getAftershockCount(int fssIndex) {
        // TODO: We should rename the fssIndex to rupId
        // In how many of the simulations does the aftershock scenario actually occur?
        int matches = 0; // We typically run under 500k simulations in total
//        int i = 0;
        for (ETAS_Catalog catalog : getBinaryCatalogsIterable(binFile, MIN_MAG)) {
//            System.out.println("#"+ (i++) +": "+catalog.toArray().length+" ruptures");;
            if (catalogHasAftershock(catalog, fssIndex)) matches++;
        }
        return matches;
    }

    /**
     * Total number of simulated catalogs found in the ETAS binary
     * @return
     */
    private int getAllCatalogsCount() {
        return getBinaryCatalogsIterable(binFile, 0).getNumCatalogs();
    }

    /**
     * Simulated catalogs in the ETAS binary with a magnitude ≥ MIN_MAG
     * @return
     */
    private int getSignificantCatalogsCount() {
        return (int)StreamSupport.stream(getBinaryCatalogsIterable(binFile, MIN_MAG).spliterator(), false)
                .filter(catalog -> !catalog.isEmpty()).count();
    }

    private boolean catalogHasAftershock(ETAS_Catalog catalog, int fssIndex) {
        for (ETAS_EqkRupture rupture : catalog) {
//            if (rupture.getFSSIndex() == fssIndex) {
            if (rupture.getID() == fssIndex) {
                return true;
            }
        }
        return false;
    }

    public void generateReport() {
        // For now this just reports statistics to stdout
        int totalCount = getAllCatalogsCount();
        int sigCount = getSignificantCatalogsCount();
        int aftershocksCount = getAftershockCount(aftershock);
        System.out.println("== Counts ==");
        System.out.println("Total number of simulations: " + totalCount);
        System.out.println("Total number of simulations M≥"+MIN_MAG+": " + sigCount);
        System.out.println("Total number of simulations with aftershock occurrence: " + aftershocksCount);
        System.out.println("== Probabilities ==");
        System.out.printf("Total: %.2E, Sig: %.2E\n", (double)aftershocksCount/totalCount, (double)aftershocksCount/sigCount);
        // TODO: Generate markdown report
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

        options.addOption(Option.builder("f")
                .longOpt("fss")
                .desc("Fault System Solution ZIP file")
                .required(true)
                .hasArg()
                .argName("file-path")
                .build());

        options.addOption(Option.builder("b")
                .longOpt("bin")
                .desc("Binary Results file")
                .required(true)
                .hasArg()
                .argName("file-path")
                .build());

        options.addOption(Option.builder("i")
                .longOpt("idx")
                .desc("Aftershock FSS Index")
                .required(true)
                .hasArg()
                .argName("fss-index")
                .build());


        return options;
    }

    /**
     * How to use the CLT. Use `--help` to see this.
     * @param options
     */
    private static void printUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null); // Preserve declaration order

        String header = "\nETAS Binary Analysis - Generate analysis reports for UCERF3-ETAS results\n\n";

        formatter.printHelp("ETAS_BinaryAnalysis ",
                header, options, null, true);
    }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--hardcoded")) {
            args = new String[]{
                    "--fss", "/Users/bhatthal/git/ucerf3-etas-launcher/inputs/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip",
                    "--bin", "/Users/bhatthal/Downloads/2026_05_27-FSS_Rupture_201887_M7p8_Start_2026_10_15_1_yr_kCOV_1p5_MaxPtSrcM_6/results_m5_preserve_chain.bin",
                    "--idx", "201887"
            };
        }
        System.setProperty("java.awt.headless", "true");
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args, false);
        } catch (ParseException e) {
            System.err.println("Error parsing command line: " + e.getMessage());
            printUsage(options);
            System.exit(2);
        }
        try {
            ETAS_BinaryAnalysis binAnalysis = new ETAS_BinaryAnalysis(cmd);
            binAnalysis.generateReport();
            System.exit(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
