package scratch.UCERF3.erf.ETAS.analysis;

import org.apache.commons.cli.*;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.math3.util.CombinatoricsUtils;

import org.opensha.commons.util.MarkdownUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimulationMetadata;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.getBinaryCatalogsIterable;
import static scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;

/**
 * Command-line tool that reads a UCERF3-ETAS results binary, an FSS ZIP, and an
 * ETAS configuration JSON, then writes a markdown analysis report for a single
 * aftershock scenario plus map plots of the matching catalogs.
 *
 * <h2>Inputs</h2>
 * <ul>
 *   <li>{@code --fss &lt;file&gt;} — Fault System Solution ZIP file (required)</li>
 *   <li>{@code --bin &lt;file&gt;} — ETAS results binary (required)</li>
 *   <li>{@code --rup &lt;id&gt;} — FSS index of the aftershock rupture to
 *       analyze (required)</li>
 *   <li>{@code --config &lt;file&gt;} — ETAS config JSON used to produce the
 *       binary (required; supplies simulation start time, duration, etc.)</li>
 *   <li>{@code --out &lt;dir&gt;} — output directory (required; created if
 *       missing). A {@code report.md} and a {@code plots/} subdirectory will be
 *       written here.</li>
 * </ul>
 *
 * <h2>Output</h2>
 * <p>The produced {@code report.md} is a self-contained markdown report
 * including:</p>
 * <ul>
 *   <li>A summary of the scenario aftershock and the simulation configuration
 *       (start time, duration, number of simulations).</li>
 *   <li>Per-catalog metadata for every simulated catalog that contains the
 *       scenario rupture, including the rupture's absolute origin time, the
 *       offset from the simulation start, and the simulation start/end times.</li>
 *   <li>Map plots of the matching catalogs.</li>
 *   <li>How often ruptures on combinations of the Hollywood and Raymond parent
 *       sections were triggered across the simulation.</li>
 *   <li>A Table of Contents linking to each top-level section.</li>
 * </ul>
 */
public class ETAS_BinaryAnalysis {

    // --- CLI / file inputs -----------------------------------------------
    private final File configFile;
    private final Path outputDir;

    // --- Parsed inputs ---------------------------------------------------
    private final BinaryParser parser;
    private final FaultSystemSolution fss;
    private final ETAS_Config config;

    // --- Derived (computed during construction) --------------------------
    private final RuptureSearcher rupSearch;

    // --- Constants -------------------------------------------------------

    /**
     * Smallest magnitude to consider when scanning binary catalogs.
     * Ruptures with M &lt; this value are ignored.
     */
    private static final double MIN_MAG = 2.5;

    /**
     * Executes the full analysis workflow: parses the binary, loads the FSS
     * and config, builds the markdown report, plots the matching catalogs,
     * and writes the parent-section tally to the report.
     *
     * @param cmd parsed command-line arguments (see class Javadoc for flags)
     * @throws IOException if the binary, FSS, config, or report file cannot be read/written
     */
    public ETAS_BinaryAnalysis(CommandLine cmd) throws IOException {
        System.out.println("Parsing ETAS results binary...");
        this.parser = new BinaryParser(new File(cmd.getOptionValue("bin")));
        System.out.println("Loading fault system solution...");
        this.fss = FaultSystemSolution.load(new File(cmd.getOptionValue("fss")));

        this.configFile = new File(cmd.getOptionValue("config"));
        this.config = ETAS_Config.readJSON(configFile);
        this.outputDir = Path.of(cmd.getOptionValue("out"));

        // Build the rupture searcher for the parent-section tally
        final List<String> parentSections = List.of("Hollywood", "Raymond");
        final List<Integer> parentSectionIDs = parentSections.stream()
                .map(s -> FaultSectionUtils.findParentSectionID(fss.getRupSet().getFaultSectionDataList(), s))
                .toList();
        this.rupSearch = new RuptureSearcher(parentSectionIDs);

        // Build the catalog map plotter; we only feed it the catalogs which
        // contain the primary aftershock (collected by MarkdownGenerator).
        ETAS_Launcher launcher = new ETAS_Launcher(config, false);
        ETAS_SimulatedCatalogPlot plot = new ETAS_SimulatedCatalogPlot(
                config, launcher, "sim_catalog_map", 0d, 25d, 50d, 75d, 100d);

        // Generate report for the scenario aftershock
        int aftershock = Integer.parseInt(cmd.getOptionValue("rup"));

        try (MarkdownGenerator markdownGen = new MarkdownGenerator()) {
            System.out.println("Building ETAS Binary analysis markdown report...");

            markdownGen.generateReport(aftershock);
            System.out.println("Primary aftershock reported");

            // Plot catalogs which contain the primary aftershock. These were
            // collected inside MarkdownGenerator::generateReport.
            parser.catalogMatches.get(aftershock).forEach(catalog -> plot.processCatalog(catalog, fss));
            plot.finalize(markdownGen.plotsDir.toFile(), launcher.checkOutFSS());

            // Embed plots from plotsDir into the markdown report
            markdownGen.addPlots(plot);

            // How often any rupture (not just our exact scenario) was triggered
            // on the Hollywood fault, Raymond fault, or both together.
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
                markdownGen.writeln(String.format(
                        "%s\n\nFound %d aftershocks over these parent sections across %d simulated catalogs (%.2f%%).",
                        header, aftershocksCount, totalCount, 100. * aftershocksCount / totalCount));
            }
        }
    }

    /**
     * Searches the loaded Fault System Solution for every rupture that occurs
     * on each non-empty combination of a fixed list of parent section IDs
     * (e.g. "Hollywood" alone, "Raymond" alone, "Hollywood" + "Raymond").
     *
 * <p>Each invocation of {@link #getCandidateRuptureSets()} or
     * {@link #getIntersectionHeaders()} returns one entry per combination, in a
     * stable order: combinations are emitted in order of increasing size, and
     * combinations of the same size are emitted in lexicographic order of the
     * input {@code parentSectionIDs} list.</p>
     */
    private class RuptureSearcher {
        // --- Inputs ---------------------------------------------------------
        private final List<Integer> parentSectionIDs;

        RuptureSearcher(List<Integer> parentSectionIDs) {
            this.parentSectionIDs = parentSectionIDs;
            System.out.println("Searching for all ruptures in the Fault System Solution on all combinations of the following parent section IDs: " + parentSectionIDs);
        }

        /**
         * Returns the set of FSS rupture indices for each parent-section
         * combination. The i-th sublist corresponds to the i-th combination
         * produced by {@link #getParentSectionCombinations()}, and contains
         * every FSS rupture index whose rupture surface intersects all parent
         * sections in that combination.
         *
         * @return list of FSS rupture-index lists, one per parent-section
         *         combination
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
         * Builds a markdown section heading (level 2, e.g. {@code ## Ruptures
         * on Hollywood, Raymond faults}) for each parent-section combination.
         * Section names that contain a single parent section use the singular
         * "fault"; multi-section combinations use "faults".
         *
         * @return one markdown heading per parent-section combination, in the
         *         same order as {@link #getCandidateRuptureSets()}
         */
        public List<String> getIntersectionHeaders() {
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

        /**
         * Looks up the parent-section name for a given parent-section ID by
         * scanning the FSS fault section data list.
         *
         * @param parentID parent-section ID to resolve
         * @return the parent-section name of the first matching
         *         {@link FaultSection}, or the string form of {@code parentID}
         *         if no match is found
         */
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
         * Generates every non-empty subset of {@link #parentSectionIDs}, in
         * order of increasing size. Subsets of the same size are emitted in
         * lexicographic order of their input indices.
         *
         * @return list of parent-section-ID combinations
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
     * Helper that parses the ETAS results binary and exposes per-aftershock
     * tallies and per-catalog match lists for the report.
     *
     * <p>The parser is single-pass-friendly: {@link #collectCatalogsMatching}
     * and {@link #countAftershocks} walk the binary independently, so calling
     * both with overlapping or disjoint FSS index sets is safe.</p>
     */
    private class BinaryParser {
        // --- Source file & one-time-computed counts -----------------------
        public final File binFile;
        private final int allCatalogsCount;

        // --- Per-aftershock tallies (one entry per FSS idx) ---------------
        /** Map of FSS rupture index → list of catalogs that contain a matching rupture. */
        public final Map<Integer, List<ETAS_Catalog>> catalogMatches = new HashMap<>();
        /** Map of FSS rupture index → number of catalogs that contain a matching rupture. */
        public final Map<Integer, Integer> aftershocksCount = new HashMap<>();
        /** Map of catalog index → map of FSS rupture index → the first matching rupture in that catalog. */
        public final Map<Integer, Map<Integer, ETAS_EqkRupture>> rupMatches = new HashMap<>();

        // --- Memoized cache ------------------------------------------------
        private final Map<Double, Integer> significantCatalogsCount = new HashMap<>();

        BinaryParser(File binFile) {
            this.binFile = binFile;
            this.allCatalogsCount = getBinaryCatalogsIterable(binFile, 0).getNumCatalogs();
        }

        /**
         * Walks every catalog in the binary and counts, for each FSS index in
         * {@code fssIdxs}, how many catalogs contain at least one rupture with
         * that FSS index. The result is stored in {@link #aftershocksCount}.
         *
         * @param fssIdxs set of FSS rupture indices to count
         * @throws IOException if the binary cannot be read
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
         * @return total number of simulated catalogs stored in the binary
         *         (not filtered by magnitude)
         */
        public int getAllCatalogsCount() {
            return allCatalogsCount;
        }

        /**
         * @return number of simulated catalogs in the binary with at least
         *         one rupture of M ≥ {@value #MIN_MAG}. Result is memoized.
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
         * Walks every catalog in the binary and, for any catalog containing at
         * least one rupture with the given FSS index, appends that catalog to
         * {@link #catalogMatches} and records the first matching rupture in
         * {@link #rupMatches}.
         *
         * <p>Each (catalog, fssIdx) pair contributes at most one entry to
         * {@link #catalogMatches}, even if the catalog contains multiple
         * ruptures with the same FSS index; the first such rupture is kept in
         * {@link #rupMatches}.</p>
         *
         * @param fssIdx FSS index of the aftershock rupture to scan for
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
                        break;
                    }
                }
            }
        }
    }

    /**
     * Helper that buffers markdown report content and writes it to disk on
     * {@link #close()}. The buffer is finalized with a Table of Contents
     * (built from headings at level 2 and below) inserted between the report
     * title and the rest of the document.
     */
    private class MarkdownGenerator implements AutoCloseable {

        // --- Output paths --------------------------------------------------
        private final Path mdFile;
        public final Path plotsDir;

        // --- In-memory buffer ----------------------------------------------
        private final List<String> lines = new ArrayList<>();

        /**
         * Creates a new markdown report and {@code plots/} directory under
         * {@code outputDir}. Existing report files and plot directories are
         * deleted and recreated.
         *
         * @throws IOException if the report file or plots directory cannot
         *         be created
         */
        MarkdownGenerator() throws IOException {
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
        }

        /**
         * Appends a single line (followed by a blank line) to the in-memory
         * report buffer. The buffer is not flushed to disk until {@link #close()}.
         *
         * @param line the markdown text to append (no trailing newline required)
         */
        public void writeln(String line) {
            lines.add(line);
            lines.add("");
        }

        /**
         * Generates the top-of-report summary and the catalog-matches block
         * for the given FSS index. Writes the report title, simulation
         * configuration summary, scenario aftershock statistics, and a
         * sub-section for every catalog matching the scenario.
         *
         * @param fssIndex FSS index of the aftershock rupture to summarize
         * @throws IOException if the binary cannot be re-read to gather
         *         per-catalog metadata
         */
        public void generateReport(int fssIndex) throws IOException {
            writeln("# ETAS Binary Analysis Report");
            writeln("## Simulation Configuration");
            writeln(String.format("- Simulation Start Time: %s",
                    getDate(config.getSimulationStartTimeMillis())));
            writeln(String.format("- Simulation Duration: %s",
                    formatDuration((long) (config.getDuration() * 365.25d * 24d * 3600d * 1000d))));
            writeln(String.format("- Number of Simulations: %d", config.getNumSimulations()));

            writeln("## Primary Aftershock");
            FaultSystemRupSet rupSet = fss.getRupSet();
            double mag = rupSet.getMagForRup(fssIndex);
            writeln(String.format("Primary Aftershock (FSS=%d), M%.4f", fssIndex, mag));
            int totalCount = parser.getAllCatalogsCount();
            int sigCount = parser.getSignificantCatalogsCount();
            parser.countAftershocks(Set.of(fssIndex));

            int aftershocksCount = parser.aftershocksCount.get(fssIndex);
            writeln("Total number of simulations: " + totalCount);
            writeln("Total number of simulations M≥"+MIN_MAG+": " + sigCount);
            writeln("Total number of simulations with aftershock occurrence: " + aftershocksCount);
            writeln(String.format("Total: %.2f%%, Sig: %.2f%%", 100. * aftershocksCount/totalCount, 100. * aftershocksCount/sigCount));
            parser.collectCatalogsMatching(fssIndex);
            writeln("## Catalog Matches");
            for (ETAS_Catalog catalog : parser.catalogMatches.get(fssIndex)) {
                writeln(getCatalogMeta(catalog, fssIndex));
            }
        }

        /**
         * Embeds the markdown produced by the given plot (already finalized
         * via {@code plot.finalize(plotsDir, fss)}) into the report under a
         * {@code ## Catalog Map Plots} section, followed by a footer line
         * stating how many catalogs were plotted.
         *
         * @param plot the plot whose markdown should be embedded
         */
        public void addPlots(ETAS_AbstractPlot plot) {
            try {
                writeln("## Catalog Map Plots");
                List<String> plotLines = plot.generateMarkdown(plotsDir.toString(), "###", "");
                for (String line : plotLines) {
                    lines.add(line);
                }
                lines.add("");
                lines.add("The above plots only consider the " + plot.getNumProcessed() + " catalogs which matched the scenario description.");
                lines.add("");
            } catch (IOException e) {
                System.err.println("Failed to write plots into report. Check " + plotsDir + " for plots.");
                throw new RuntimeException(e);
            }
        }

        /**
         * Emits the JSON contents of the ETAS configuration file that was used
         * to produce the binary, under a {@code ## JSON Input File} section.
         * This preserves the full input used for the analysis in the report.
         *
         * @throws IOException if the config file cannot be read
         */
        public void addConfigFileSection() throws IOException {
            writeln("## JSON Input File");
            writeln("```");
            try (BufferedReader jsonReader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = jsonReader.readLine()) != null) {
                    lines.add(line);
                }
            }
            lines.add("```");
            lines.add("");
        }

        /**
         * Builds the markdown representation of the metadata for one ETAS
         * catalog that contains the scenario aftershock, including the
         * rupture's absolute origin time, the offset (positive or negative)
         * between the rupture origin and the simulation start, and the
         * simulation start/end times.
         *
         * @param catalog the catalog to describe
         * @param fssIndex FSS index of the scenario aftershock
         * @return a multi-line markdown fragment beginning with a level-3
         *         catalog heading
         */
        private String getCatalogMeta(ETAS_Catalog catalog, int fssIndex) {
            StringBuilder output = new StringBuilder("### Catalog Index: ");
            ETAS_SimulationMetadata meta = catalog.getSimulationMetadata();
            output.append(meta.catalogIndex);
            output.append("\n");

            long originMs = parser.rupMatches
                    .get(meta.catalogIndex)
                    .get(fssIndex)
                    .getOriginTime();
            long simStartMs = config.getSimulationStartTimeMillis();
            long offsetMs = originMs - simStartMs;

            output.append("Rupture Origin Time: ").append(getDate(originMs)).append("\n\n");
            output.append("Time After Simulation Start: ").append(formatDuration(offsetMs)).append("\n\n");
            output.append("Simulation Start Time: ").append(getDate(simStartMs)).append("\n\n");
            //output.append("Simulation End Time: ").append(getDate(meta.simulationEndTime)).append("\n\n");
            long durMs = (long) (config.getDuration() * 365.25d * 24d * 3600d * 1000d);
            output.append("Simulation End Time: ").append(getDate(simStartMs + durMs)).append("\n\n");
            output.append("\n");
            return output.toString();
        }

        /**
         * Formats an epoch-millisecond timestamp in the local time zone using
         * the pattern {@code yyyy-MM-dd HH:mm:ss.SSS}.
         *
         * @param epochMillis epoch milliseconds (UTC)
         * @return a human-readable date-time string in the local time zone
         */
        private String getDate(long epochMillis) {
            Instant instant = Instant.ofEpochMilli(epochMillis);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());
            return formatter.format(instant);
        }

        /**
         * Formats a non-negative duration as {@code "Dd HHh MMm SS.SSSs"}.
         * Negative durations are formatted using the same pattern after taking
         * the absolute value, prefixed with a leading minus sign.
         *
         * @param millis duration in milliseconds (may be negative)
         * @return a human-readable duration string
         */
        private String formatDuration(long millis) {
            boolean negative = millis < 0;
            long abs = Math.abs(millis);
            Duration d = Duration.ofMillis(abs);
            long days = d.toDays();
            long hours = d.toHoursPart();
            long minutes = d.toMinutesPart();
            double seconds = d.toSecondsPart() + (d.toMillisPart() / 1000d);
            String body = String.format("%dd %02dh %02dm %06.3fs", days, hours, minutes, seconds);
            return negative ? "-" + body : body;
        }

        /**
         * Inserts a Table of Contents at the top of the report (immediately
         * after the level-1 title) and writes the final buffer to
         * {@link #mdFile}. Implements {@link AutoCloseable} so the generator
         * can be used in a try-with-resources block.
         */
        @Override
        public void close() throws IOException {
            // Find the level-1 title; TOC will be inserted just after it.
            int insertAt = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("# ") && !lines.get(i).startsWith("## ")) {
                    insertAt = i + 1;
                    // Skip any blank lines after the H1 so the TOC sits flush.
                    while (insertAt < lines.size() && lines.get(insertAt).trim().isEmpty()) {
                        insertAt++;
                    }
                    break;
                }
            }
            if (insertAt < 0) {
                insertAt = 0;
            }

            List<String> headers = lines.stream()
                    .flatMap(Pattern.compile("\\R")::splitAsStream)
                    .toList();
            List<String> toc = new ArrayList<>();
            toc.add("## Table Of Contents");
            toc.add("");
            toc.addAll(MarkdownUtils.buildTOC(headers, 2, 2));
            toc.add("");

            lines.addAll(insertAt, toc);
            Files.write(mdFile, lines);
        }
    }

    /**
     * Builds the apache-commons-cli {@link Options} for this CLT.
     *
     * @return the configured {@code Options} instance
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
     * Prints the usage banner for this CLT. Triggered by {@code --help} or by
     * a parse error.
     *
     * @param options the options to describe
     */
    private static void printUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null); // Preserve declaration order

        String header = "\nETAS Binary Analysis - Generate analysis reports for UCERF3-ETAS results\n\n";

        formatter.printHelp("ETAS_BinaryAnalysis ",
                header, options, null, true);
    }

    /**
     * Entry point for the ETAS Binary Analysis CLT.
     *
     * <p>Pass {@code --hardcoded} as the single argument to run against a
     * hard-coded fixture (useful for development).</p>
     *
     * @param args command-line arguments
     */
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
