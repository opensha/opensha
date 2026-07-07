package scratch.UCERF3.erf.ETAS.analysis;

import org.apache.commons.cli.*;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.apache.commons.io.file.PathUtils;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimulationMetadata;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.getBinaryCatalogsIterable;
import static scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;

/** NOTE
 * How often our particular rupture was triggered
 * - Information on the timing of those occurrences in the simulations (e.g., within a day, within a week, later)
 * - Maps of those individual catalogs
 * How often any rupture (not just our exact scenario) was triggered on the Hollywood fault, Raymond fault, or both together
 */

/**
 * CLT analyzes binary results from UCERF3-ETAS simulations and generates
 * a markdown analysis report.
 */
public class ETAS_BinaryAnalysis {

    private final BinaryParser parser;
    private final RuptureSearcher rupSearch;
    private final FaultSystemSolution fss; // Fault System Solution as defined by the ZIP file
    // Smallest magnitude to consider from binary catalogs
    private static final double MIN_MAG = 2.5;

    /**
     * Constructor executes workflow for parsing binary and generating markdown
     * @param cmd
     * @throws IOException
     */
    public ETAS_BinaryAnalysis(CommandLine cmd) throws IOException {
        System.out.println("Parsing ETAS results binary...");
        this.parser = new BinaryParser(new File(cmd.getOptionValue("bin")));
        System.out.println("Loading fault system solution...");
        this.fss = FaultSystemSolution.load(new File(cmd.getOptionValue("fss")));

        File configFile = new File(cmd.getOptionValue("config"));
        ETAS_Config config = ETAS_Config.readJSON(configFile);
        Path output = Path.of(cmd.getOptionValue("out"));

//        ETAS_Launcher launcher = new ETAS_Launcher(config, false);
//        ETAS_SimulatedCatalogPlot sim = new ETAS_SimulatedCatalogPlot(config, launcher, "sim_catalog_map");
//        SimulationMarkdownGenerator.generateMarkdown(
//                configFile, parser.binFile, config, output.toFile(), false, parser.getAllCatalogsCount(),
//                SimulationMarkdownGenerator.defaultNumThreads(), true, false, null, null);
//
        // TODO: Load config file for use in MarkdownGen
        // TODO: Reevaluate the nested class configuration - private variable layout

        // Generate report for the scenario aftershock
        int aftershock = Integer.parseInt(cmd.getOptionValue("rup"));

        try (MarkdownGenerator markdownGen = new MarkdownGenerator(output)) {
            System.out.println("Building ETAS Binary analysis markdown report...");

            markdownGen.generateReport(aftershock);
            System.out.println("Primary aftershock reported");

            // How often any rupture (not just our exact scenario) was triggered on the Hollywood fault, Raymond fault, or both together
            final List<String> parentSections = List.of("Hollywood", "Raymond");
            final List<Integer> parentSectionIDs = parentSections.stream()
                    .map(s -> FaultSectionUtils.findParentSectionID(fss.getRupSet().getFaultSectionDataList(), s))
                    .toList();
            this.rupSearch = new RuptureSearcher(parentSectionIDs);

            List<String> parentSectionRupHeaders = rupSearch.getIntersectionHeaders();
            List<List<Integer>> parentSectionIntersection = rupSearch.getCandidateRuptureSets();
            parser.countAftershocks(parentSectionIntersection
                    .stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toSet()));
            for (int i = 0; i < parentSectionRupHeaders.size(); i++) {
                List<Integer> rupSet = parentSectionIntersection.get(i);
                int aftershocksCount = 0;
                String header = parentSectionRupHeaders.get(i);
                    for (int rupIdx : rupSet) {
                        aftershocksCount += parser.aftershocksCount.get(rupIdx);
                    }
                    int totalCount = parser.getAllCatalogsCount();
                    markdownGen.write(String.format(
                            "%s\nFound %d aftershocks over these parent sections across %d simulated catalogs (%.2f%%).",
                            header, aftershocksCount, totalCount, 100. * aftershocksCount / totalCount));
            }
        }
    }

    /**
     * Searches for ruptures in the Fault System Solution
     */
    private class RuptureSearcher {
        private final List<Integer> parentSectionIDs;

        RuptureSearcher(List<Integer> parentSectionIDs) {
            // Use `FaultSectionUtils.findParentSectionID` to find IDs from section names
            this.parentSectionIDs = parentSectionIDs;
            System.out.println("Searching for all ruptures in the Fault System Solution on all combinations of the following parent section IDs: " + parentSectionIDs);
        }

        /**
         * Searches for ruptures on all combinations of the given parent section IDs.
         * The ith sublist is the set of ruptures corresponding to the ith combination from `getParentSectionCombinations`.
         * @return List of List fss indices for ruptures
         */
        public List<List<Integer>> getCandidateRuptureSets() {
            List<List<Integer>> results = new ArrayList<>();
            FaultSystemRupSet rupSet = fss.getRupSet();
            // Add each rupture that matches one of the valid parent section combinations
            for (List<Integer> comb : getParentSectionCombinations()) {
                // Get all ruptures that occur in all parent sections in the given combination
                Set<Integer> intersection = new HashSet<>(rupSet.getRupturesForParentSection(comb.get(0)));
                for (int i = 1; i < comb.size(); i++) {
                    Set<Integer> currentSet = new HashSet<>(rupSet.getRupturesForParentSection(comb.get(i)));
                    intersection.retainAll(currentSet);
                }
                results.add(new ArrayList<>(intersection));
            }
            return results;
        }

        /**
         * Gets a header name for each parent section combination
         * @return parent section name for first matching FaultSection, and ID if no matches
         */
        public List<String> getIntersectionHeaders() {
            // We need to find the name for each parent section ID and build headers accordingly
            List<String> headers = new ArrayList<>();
            List<List<Integer>> combinations = getParentSectionCombinations();
            for (List<Integer> comb : combinations) {
                StringBuilder header = new StringBuilder("## Ruptures on ");
                header.append(comb.stream()
                        .map(this::getParentSectionName)
                        .collect(Collectors.joining(", ")));
                header.append(" fault");
                if (comb.size() > 1) {
                    header.append("s");
                }
                headers.add(String.valueOf(header));
            }
            return headers;
        }

        private String getParentSectionName(int parentID) {
            FaultSystemRupSet rupSet = fss.getRupSet();
            for (FaultSection sect : rupSet.getFaultSectionDataList()) {
                if (sect.getParentSectionId() == parentID) {
                   return sect.getParentSectionName();
                }
            }
            return String.valueOf(parentID);
        }

        /**
         * Gets all possible combinations of parent section IDs
         * @return
         */
        private List<List<Integer>> getParentSectionCombinations() {
            List<List<Integer>> combinations = new ArrayList<>();
            for (int k = 1; k < parentSectionIDs.size()+1; k++) {

                // Generate combinations of indices choosing k
                Iterator<int[]> iterator = CombinatoricsUtils.combinationsIterator(parentSectionIDs.size(), k);

                while (iterator.hasNext()) {
                    int[] indices = iterator.next();

                    // Map indices to the original array
                    Integer[] combination = Arrays.stream(indices)
                            .mapToObj(parentSectionIDs::get)
                            .toArray(Integer[]::new);

                    combinations.add(Arrays.asList(combination));
                }
            }
            return combinations;
        }
    }


    /**
     * Nested helper class for binary parsing logic
     */
    private class BinaryParser {
        public final File binFile; // UCERF3-ETAS results binary
        // Map rupture ID to list of catalogs matching scenario
        public final Map<Integer, List<ETAS_Catalog>> catalogMatches = new HashMap<>();
        // Map MIN_MAG to catalog counts
        private final Map<Double, Integer> significantCatalogsCount = new HashMap<>();
        // Total simulated catalogs in provided binary
        private final int allCatalogsCount;
        // How many of a given aftershock (FSS idx) are found across all catalogs (≥ MIN_MAG)
        public final Map<Integer, Integer> aftershocksCount = new HashMap<>();
        // Map catalog index to map of FSS index to the first rupture that matched it
        public final Map<Integer, Map<Integer, ETAS_EqkRupture>> rupMatches = new HashMap<>();


        BinaryParser(File binFile) {
            this.binFile = binFile;
            this.allCatalogsCount = getBinaryCatalogsIterable(binFile, 0).getNumCatalogs();
       }

        /**
         * How many simulated catalogs match each aftershock scenario description?
         * @param fssIdxs Set of FSS index for each aftershock rupture
         * @return
         */
        public void countAftershocks(Set<Integer> fssIdxs) throws IOException {
            fssIdxs.forEach(id -> aftershocksCount.put(id, 0));

            for (ETAS_Catalog cat : getBinaryCatalogsIterable(binFile, MIN_MAG)) {
                for (ETAS_EqkRupture rup : cat) {
                    int id = rup.getFSSIndex();
                    if (fssIdxs.contains(id)) {
                        aftershocksCount.merge(id, 1, Integer::sum);
                    }
                }
            }
        }

        /**
         * Total number of simulated catalogs found in the ETAS binary
         * @return
         */
        public int getAllCatalogsCount() {
            return allCatalogsCount;
        }

        /**
         * Simulated catalogs in the ETAS binary with a magnitude ≥ MIN_MAG
         * @return
         */
        public int getSignificantCatalogsCount() {
            if (significantCatalogsCount.containsKey(MIN_MAG)) {
                return significantCatalogsCount.get(MIN_MAG);
            }
            int count = (int)StreamSupport
                    .stream(getBinaryCatalogsIterable(binFile, MIN_MAG).spliterator(), false)
                    .filter(catalog -> !catalog.isEmpty())
                    .count();
            significantCatalogsCount.put(MIN_MAG, count);
            return count;
        }

        /**
         * TODO: Update this javadoc: It collects matching catalogs and maps the first matching rups
         * In how many of the simulations does the aftershock scenario actually occur?
         * This method checks how many simulated catalogs above a minimum magnitude
         * see a rupture matching the aftershock scenario.
         * <p>
         * Note that this only counts the number of catalogs where at least one rupture
         * matches, but does not count multiple times if multiple ruptures match.
         * </p>
         * @param fssIdx FSS index of aftershock rupture
         * @return
         */
        public void collectCatalogsMatching(int fssIdx) {
            catalogMatches.put(fssIdx, new ArrayList<>());
            for (ETAS_Catalog cat : getBinaryCatalogsIterable(binFile, MIN_MAG)) {
                for (ETAS_EqkRupture rup : cat) {
                    if (fssIdx == rup.getFSSIndex()) {
                        catalogMatches.get(fssIdx).add(cat);
                        int catIdx = cat.getSimulationMetadata().catalogIndex;
                        if (!rupMatches.containsKey(catIdx)) {
                            rupMatches.put(catIdx, new HashMap<>());
                        }
                        rupMatches.get(catIdx).put(fssIdx, rup);
                    }
                }
            }
        }
    }

    /**
     * Nested helper class for markdown generation
     */
    private class MarkdownGenerator implements AutoCloseable {

        private final Path mdFile;
        private final Path plotsDir;
        private final BufferedWriter writer;
        final Object fileLock = new Object(); // Shared lock across all futures

        /**
         * Generates a new markdown report and plots at the given output directory.
         * This will overwrite any existing reports.
         * @param outputDir
         */
        MarkdownGenerator(Path outputDir) throws IOException {
            this.mdFile = outputDir.resolve("report.md");
            PathUtils.createParentDirectories(mdFile);
            Files.deleteIfExists(mdFile);
            Files.createFile(mdFile);
            this.plotsDir = outputDir.resolve("plots");
            PathUtils.createParentDirectories(plotsDir);
            if (Files.exists(plotsDir)) {
                PathUtils.deleteDirectory(plotsDir);
            }
            Files.createDirectories(plotsDir);

            this.writer = Files.newBufferedWriter(mdFile);
        }

        /**
         * Writes a single line to the existing report.
         * @param line
         */
        public void write(String line) throws IOException {
            synchronized (fileLock) {
                writer.write(line);
                writer.newLine();
            }
        }

        /**
         * Generates a report for the `fssIndex` rupture using the binary parser.
         * Appends to the existing markdown output.
         * @param fssIndex
         */
        public void generateReport(int fssIndex) throws IOException {
            write("# ETAS Binary Analysis Report");
            FaultSystemRupSet rupSet = fss.getRupSet();
            double mag = rupSet.getMagForRup(fssIndex);
            write(String.format("Primary Aftershock (FSS=%d), M%f", fssIndex, mag));
            int totalCount = parser.getAllCatalogsCount();
            int sigCount = parser.getSignificantCatalogsCount();
            parser.countAftershocks(Set.of(fssIndex));
            int aftershocksCount = parser.aftershocksCount.get(fssIndex);
            write("Total number of simulations: " + totalCount);
            write("Total number of simulations M≥"+MIN_MAG+": " + sigCount);
            write("Total number of simulations with aftershock occurrence: " + aftershocksCount);
            write(String.format("Total: %.2f%%, Sig: %.2f%%", 100. * aftershocksCount/totalCount, 100. * aftershocksCount/sigCount));
            // TODO: Build plot for all the catalogs
            parser.collectCatalogsMatching(fssIndex);
            write("## Catalog Matches");
            for (ETAS_Catalog catalog : parser.catalogMatches.get(fssIndex)) {
                write(getCatalogMeta(catalog, fssIndex));
            }

        }

        // TODO: Write binary details (what was the main simulation?) and what command was used to run this CLT?

        /**
         * Gets markdown representation of the metadata for an ETAS catalog
         *
         * @param catalog
         * @param fssIndex
         * @return
         */
        private String getCatalogMeta(ETAS_Catalog catalog, int fssIndex) {
            // TODO: We want the timing of the first rupture from start of the simulated catalog (e.g., within a day)
            // * Read the startTimeMillis from the config JSON for this
            StringBuilder output = new StringBuilder("### Catalog Index: ");
            ETAS_SimulationMetadata meta = catalog.getSimulationMetadata();
            output.append(meta.catalogIndex);
            output.append("\n");
            output.append("Rupture Origin Time: ");
            output.append(getDate(parser.rupMatches
                    .get(catalog.getSimulationMetadata().catalogIndex)
                    .get(fssIndex)
                    .getOriginTime()));
//            output.append("Simulation Start Time: ");
//            output.append(getDate(meta.simulationStartTime));
//            output.append("\n");
//            output.append("Simulation End Time: ");
//            output.append(getDate(meta.simulationEndTime));
            output.append("\n");
            return output.toString();

            /**
             * TODO:
             * 1. A catalog is defined as a list of ? extends ObsEqkRup
             * 2. We see in ETAS_SimulatedCatalogPlot::doFinalize, the functions to plot
             *    are defined and added to inputFuncs.
             * 3. We need to invoke ETAS_EventMapPlotUtils::buildEventPlot and figure outr what funcs etc we need
             */
        }

        /**
         * Gets human-readable datetime for a given epoch in milliseconds
         * @param epochMillis
         * @return
         */
        private String getDate(long epochMillis) {
            // 1. Convert milliseconds to an Instant object
            Instant instant = Instant.ofEpochMilli(epochMillis);

            // 2. Define a readable date-time format pattern
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault()); // Uses your local timezone

            // 3. Format into a human-readable string
            return formatter.format(instant);

        }

        @Override
        public void close() throws IOException {
            if (writer != null) {
                writer.close();
            }
        }

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

        options.addOption(Option.builder("r")
                .longOpt("rup")
                .desc("Aftershock Rupture ID")
                .required(true)
                .hasArg()
                .argName("rupture-id")
                .build());

        options.addOption(Option.builder("o")
                .longOpt("out")
                .desc("Output Directory")
                .required(true)
                .hasArg()
                .argName("dir-path")
                .build());

        options.addOption(Option.builder("c")
                .longOpt("config")
                .desc("ETAS Config JSON File")
                .required(true)
                .hasArg()
                .argName("file-path")
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
                    "--bin", "/Users/bhatthal/Downloads/Merged-FSS_Rupture_201887_M7p8_Start_2026_10_15_1_yr_kCOV_1p5_MaxPtSrcM_6/results_m5_preserve_chain.bin",
                    "--config", "/Users/bhatthal/Downloads/Merged-FSS_Rupture_201887_M7p8_Start_2026_10_15_1_yr_kCOV_1p5_MaxPtSrcM_6/config.json",
                    "--rup", "218331",
                    "--out", "/Users/bhatthal/Desktop/etas-bin-analysis"
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
            new ETAS_BinaryAnalysis(cmd);
            System.exit(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
