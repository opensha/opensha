package scratch.UCERF3.erf.ETAS.analysis;

import org.apache.commons.cli.*;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.apache.commons.math3.util.Precision;

import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimulationMetadata;

import java.io.File;
import java.io.IOException;
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
    // Debug messages
    private static final boolean D = true;

    /**
     * Constructor executes workflow for parsing binary and generating markdown
     * @param cmd
     * @throws IOException
     */
    public ETAS_BinaryAnalysis(CommandLine cmd) throws IOException {
        this.parser = new BinaryParser(new File(cmd.getOptionValue("bin")));
        this.fss = FaultSystemSolution.load(new File(cmd.getOptionValue("fss")));

        // Generate report for the scenario aftershock
        int aftershock = Integer.parseInt(cmd.getOptionValue("rup"));

        MarkdownGenerator markdownGen = new MarkdownGenerator();
        System.out.println("# Primary Aftershock (FSS=" + aftershock + ")");
        markdownGen.generateReport(aftershock);

        // How often any rupture (not just our exact scenario) was triggered on the Hollywood fault, Raymond fault, or both together
        final List<String> parentSections = List.of("Hollywood", "Raymond");
        final List<Integer> parentSectionIDs = parentSections.stream()
                            .map(s -> FaultSectionUtils.findParentSectionID(fss.getRupSet().getFaultSectionDataList(), s))
                            .toList();
        this.rupSearch = new RuptureSearcher(parentSectionIDs);

        // TODO: We need to first filter the candidate rupture sets with what actually occurs in the binary
        // Currently it's just printing all from the FSS and reporting all with 0 occurrences
        List<String> parentSectionRupHeaders = rupSearch.getIntersectionHeaders();
        List<List<Integer>> parentSectionIntersection = rupSearch.getCandidateRuptureSets();
        for (int i = 0; i < parentSectionRupHeaders.size(); i++) {
            String header = parentSectionRupHeaders.get(i);
            System.out.println(header);
            parentSectionIntersection.get(i).forEach(markdownGen::generateReport);
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
            if (D) {
                System.out.print("Searching for all ruptures in the Fault System Solution on all combinations of the following parent section IDs: ");
                System.out.println(parentSectionIDs);
            }
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
                List<Integer> intersection = rupSet.getParentSectionsForRup(comb.get(0));
                for (int i = 1; i < comb.size(); i++) {
                    Set<Integer> currentSet = new HashSet<>(rupSet.getParentSectionsForRup(comb.get(i)));
                    intersection.retainAll(currentSet);
                }
                results.add(intersection);
            }
            return results;
        }

        /**
         * Gets a header name for each parent section combination
         * @return parent section name for first matching FaultSection, and "None" if no matches
         */
        public List<String> getIntersectionHeaders() {
            // We need to find the name for each parent section ID and build headers accordingly
            List<String> headers = new ArrayList<>();
            List<List<Integer>> combinations = getParentSectionCombinations();
            for (List<Integer> comb : combinations) {
                StringBuilder header = new StringBuilder("# Ruptures on ");
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
            return "None";
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
        private final File binFile; // UCERF3-ETAS results binary
        // Map rupture ID to list of catalogs matching scenario
        private final Map<Integer, List<ETAS_Catalog>> catalogMatches = new HashMap<>();

        BinaryParser(File binFile) {
            this.binFile = binFile;
       }

        /**
         * How many simulated catalogs match the aftershock scenario description?
         * @param fssIdx FSS index of aftershock rupture
         * @return
         */
        public int getAftershockCount(int fssIdx) {
            if (!catalogMatches.containsKey(fssIdx)) collectCatalogsMatching(fssIdx);
            return catalogMatches.get(fssIdx).size();
        }

        /**
         * Total number of simulated catalogs found in the ETAS binary
         * @return
         */
        public int getAllCatalogsCount() {
            return getBinaryCatalogsIterable(binFile, 0).getNumCatalogs();
        }

        /**
         * Simulated catalogs in the ETAS binary with a magnitude ≥ MIN_MAG
         * @return
         */
        public int getSignificantCatalogsCount() {
            return (int)StreamSupport.stream(getBinaryCatalogsIterable(binFile, MIN_MAG).spliterator(), false)
                    .filter(catalog -> !catalog.isEmpty()).count();
        }

        /**
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
        private void collectCatalogsMatching(int fssIdx) {
            catalogMatches.put(fssIdx, new ArrayList<>());
            for (ETAS_Catalog catalog : getBinaryCatalogsIterable(binFile, MIN_MAG)) {
                if (catalogHasAftershock(catalog, fssIdx)) {
                    catalogMatches.get(fssIdx).add(catalog);
                }
            }
        }

        /**
         * A simulated catalog is considered to have an aftershock if there
         * is at least one match for the FSS index.
         * @param catalog
         * @param fssIdx
         * @return
         */
        private boolean catalogHasAftershock(ETAS_Catalog catalog, int fssIdx) {
            for (ETAS_EqkRupture rupture : catalog) {
//                if (rupture.getFSSIndex() != -1)
//                    System.out.println("id=" + rupture.getID() + ", fss="+rupture.getFSSIndex());
                if (rupture.getFSSIndex() == fssIdx) {
                    return true;
                }
            }
            return false;
        }

    }

    /**
     * Nested helper class for markdown generation
     */
    private class MarkdownGenerator {
        /**
         * Generates a report for the `fssIndex` rupture using the binary parser
         * @param fssIndex
         */
        public void generateReport(int fssIndex) {
            // For now this just reports statistics to stdout
            System.out.printf("## Rupture: " + fssIndex);
            FaultSystemRupSet rupSet = fss.getRupSet();
            double mag = rupSet.getMagForRup(fssIndex);
            System.out.println(", M"+mag);
            int totalCount = parser.getAllCatalogsCount();
            int sigCount = parser.getSignificantCatalogsCount();
            int aftershocksCount = parser.getAftershockCount(fssIndex);
            System.out.println("### Counts");
            System.out.println("Total number of simulations: " + totalCount);
            System.out.println("Total number of simulations M≥"+MIN_MAG+": " + sigCount);
            System.out.println("Total number of simulations with aftershock occurrence: " + aftershocksCount);
            System.out.println("### Probabilities");
            System.out.printf("Total: %.2E, Sig: %.2E\n", (double)aftershocksCount/totalCount, (double)aftershocksCount/sigCount);
            System.out.println("### Catalog Matches");
            for (ETAS_Catalog catalog : parser.catalogMatches.get(fssIndex)) {
                System.out.println(getCatalogMeta(catalog));
            }
        }

        // TODO: Write binary details (what was the main simulation?) and what command was used to run this CLT?

        /**
         * Gets markdown representation of the metadata for an ETAS catalog
         * @param catalog
         * @return
         */
        private String getCatalogMeta(ETAS_Catalog catalog) {
            // TODO: We want the timing of the rupture from start of the simulated catalog (e.g., within a day)
            StringBuilder output = new StringBuilder("### Catalog Index: ");
            ETAS_SimulationMetadata meta = catalog.getSimulationMetadata();
            output.append(meta.catalogIndex);
            output.append("\n");
            output.append("Simulation Start Time: ");
            output.append(getDate(meta.simulationStartTime));
            output.append("\n");
            output.append("Simulation End Time: ");
            output.append(getDate(meta.simulationEndTime));
            output.append("\n");
            return output.toString();
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

        // TODO: Generate plots

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
                    "--rup", "218331"
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
