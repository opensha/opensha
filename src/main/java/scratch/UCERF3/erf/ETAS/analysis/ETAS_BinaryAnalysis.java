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
import java.util.regex.Matcher;
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
 *       missing). A {@code report.md}, a rendered {@code report.html}, and a
 *       {@code plots/} subdirectory will be written here.</li>
 * </ul>
 *
 * <h2>Output</h2>
 * <p>The produced {@code report.md} is a self-contained markdown report
 * including:</p>
 * <ul>
 *   <li>A summary of the scenario aftershock and the simulation configuration
 *       (start time, duration, number of simulations).</li>
 *   <li>An occurrence timeline plot showing when the scenario rupture (and other
 *       M&ge;2.5 ruptures) occurred across all matching catalogs.</li>
 *   <li>A per-catalog metadata table with origin time, catalog size, max magnitude,
 *       M&ge;5/6/7 rupture counts, parent-section rupture count, and largest
 *       rupture for each matching catalog.</li>
 *   <li>A histogram of when the primary aftershock occurred across the matching
 *       catalogs, with cumulative occurrence counts within 1 hour, 1 day, 1 week,
 *       1 month, and 1 year (thresholds exceeding the simulation duration are
 *       omitted).</li>
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
    private final String fssPath;
    private final String binPath;

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
        this.fssPath = cmd.getOptionValue("fss");
        this.binPath = cmd.getOptionValue("bin");

        // Build the rupture searcher for the parent-section tally
        final List<String> parentSections = List.of("Hollywood", "Raymond");
        final List<Integer> parentSectionIDs = parentSections.stream()
                .map(s -> FaultSectionUtils.findParentSectionID(fss.getRupSet().getFaultSectionDataList(), s))
                .toList();
        this.rupSearch = new RuptureSearcher(parentSectionIDs);

        // Build the catalog map plotter; we only feed it the catalogs which
        // contain the primary aftershock (collected by MarkdownGenerator).
        ETAS_Launcher launcher = new ETAS_Launcher(config, false);
        ETAS_SimulatedCatalogPlot catalogMapPlot = new ETAS_SimulatedCatalogPlot(
                config, launcher, "sim_catalog_map", 0d, 25d, 50d, 75d, 100d);

        // Generate report for the scenario aftershock
        int aftershock = Integer.parseInt(cmd.getOptionValue("rup"));
        double mag = fss.getRupSet().getMagForRup(aftershock);

        // Occurrence-timeline plot for the scenario rupture across all matching catalogs.
        ETAS_RuptureOccurrenceTimeline timelinePlot =
                new ETAS_RuptureOccurrenceTimeline(config, launcher, aftershock, mag);

        // Histogram of when the primary aftershock occurred across all matching
        // catalogs, placed in the report under Catalog Matches.
        ETAS_PrimaryAftershockTimingPlot timingPlot =
                new ETAS_PrimaryAftershockTimingPlot(config, launcher, aftershock, mag);

        try (MarkdownGenerator markdownGen = new MarkdownGenerator(fssPath, binPath)) {
            System.out.println("Building ETAS Binary analysis markdown report...");

            markdownGen.generateReport(aftershock);
            System.out.println("Primary aftershock reported");

            // Build and embed the primary-aftershock timing histogram over the
            // matching catalogs, directly under the Catalog Matches section.
            parser.catalogMatches.get(aftershock).forEach(catalog -> timingPlot.processCatalog(catalog, fss));
            timingPlot.finalize(markdownGen.plotsDir.toFile(), launcher.checkOutFSS());
            markdownGen.addTimingHistogram(timingPlot);

            // Plot catalogs which contain the primary aftershock. These were
            // collected inside MarkdownGenerator::generateReport.
            parser.catalogMatches.get(aftershock).forEach(catalog -> catalogMapPlot.processCatalog(catalog, fss));
            catalogMapPlot.finalize(markdownGen.plotsDir.toFile(), launcher.checkOutFSS());

            // Embed plots from plotsDir into the markdown report
            markdownGen.addCatalogMapPlots(catalogMapPlot);

            // Build the rupture-occurrence timeline over the same matching catalogs
            // and splice its (clickable) image into the report's timeline section.
            parser.catalogMatches.get(aftershock).forEach(catalog -> timelinePlot.processCatalog(catalog, fss));
            timelinePlot.finalize(markdownGen.plotsDir.toFile(), launcher.checkOutFSS());
            markdownGen.addTimeline(timelinePlot);

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

            // Append the JSON config file contents
            markdownGen.addConfigFileSection();
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
         */
        public void countAftershocks(Set<Integer> fssIdxs) {
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
     * title and the rest of the document, then written as both {@code report.md}
     * and a rendered {@code report.html} (via {@link MarkdownUtils#writeHTML}).
     */
    private class MarkdownGenerator implements AutoCloseable {

        // --- Output paths --------------------------------------------------
        private final Path mdFile;
        public final Path plotsDir;
        /**
         * Path to {@link #plotsDir} relative to {@code report.md}/{@code report.html},
         * used for image references so the report is portable (not tied to an
         * absolute output path).
         */
        private final String plotsRelPath;

        // --- Echoed input paths --------------------------------------------
        private final String fssPath;
        private final String binPath;

        // --- In-memory buffer ----------------------------------------------
        private final List<String> lines = new ArrayList<>();

        /**
         * Creates a new markdown report and {@code plots/} directory under
         * {@code outputDir}. Existing report files (markdown and HTML) and plot
         * directories are deleted and recreated. The supplied input file paths
         * are echoed in the {@code Simulation Configuration} section of the
         * report.
         *
         * @param fssPath path of the FSS ZIP (echoed in the report)
         * @param binPath path of the ETAS results binary (echoed in the report)
         * @throws IOException if the report file or plots directory cannot
         *         be created
         */
        MarkdownGenerator(String fssPath, String binPath) throws IOException {
            this.fssPath = fssPath;
            this.binPath = binPath;
            this.mdFile = outputDir.resolve("report.md");
            PathUtils.createParentDirectories(mdFile);
            Files.deleteIfExists(mdFile);
            Files.createFile(mdFile);
            // Clear any stale HTML from a prior run; the fresh copy is written
            // in close() alongside the finalized markdown.
            Files.deleteIfExists(outputDir.resolve("report.html"));
            this.plotsDir = outputDir.resolve("plots");
            PathUtils.createParentDirectories(plotsDir);
            if (Files.exists(plotsDir)) {
                PathUtils.deleteDirectory(plotsDir);
            }
            Files.createDirectories(plotsDir);
            // Image references in the report are written relative to the report
            // file (which lives in outputDir), so they resolve regardless of the
            // absolute location of --out.
            this.plotsRelPath = outputDir.relativize(plotsDir).toString();
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
         * for the given FSS index. Writes the report title, the simulation
         * configuration (with echoed input file paths), the scenario aftershock
         * statistics expressed as verbose probabilities over both the full and
         * the M&ge;{@value #MIN_MAG} "significant" simulation pools, an
         * occurrence-timeline section (whose image is spliced in later by
         * {@link #addTimeline}), and a per-catalog metadata table.
         *
         * @param fssIndex FSS index of the aftershock rupture to summarize
         * @throws IOException if the binary cannot be re-read to gather
         *         per-catalog metadata
         */
        public void generateReport(int fssIndex) throws IOException {
            FaultSystemRupSet rupSet = fss.getRupSet();
            double mag = rupSet.getMagForRup(fssIndex);

            writeln("# ETAS Binary Analysis Report");

            // --- Simulation Configuration --------------------------------------
            writeln("## Simulation Configuration");
            writeln(String.format("- Simulation Start Time: %s",
                    getDate(config.getSimulationStartTimeMillis())));
            writeln(String.format("- Simulation Duration: %s",
                    formatDuration((long) (config.getDuration() * 365.25d * 24d * 3600d * 1000d))));
            writeln(String.format("- Number of Simulations: %d", config.getNumSimulations()));
            writeln("- ETAS Config JSON: " + configFile.getAbsolutePath());
            writeln("- Fault System Solution ZIP: " + fssPath);
            writeln("- Binary Results: " + binPath);

            // --- Primary Aftershock --------------------------------------------
            writeln("## Primary Aftershock");
            writeln("- FSS Index: " + fssIndex);
            writeln(String.format("- Magnitude: M%.4f", mag));
            writeln("- Significance threshold for simulation count: M" + MIN_MAG);

            int totalCount = parser.getAllCatalogsCount();
            int sigCount = parser.getSignificantCatalogsCount();
            parser.countAftershocks(Set.of(fssIndex));
            int aftershocksCount = parser.aftershocksCount.get(fssIndex);
            double probAll = (double) aftershocksCount / totalCount;
            double probSig = sigCount > 0 ? (double) aftershocksCount / sigCount : 0d;

            writeln("- Total number of simulations: " + totalCount);
            writeln("- Number of M≥" + MIN_MAG + " simulations: " + sigCount);
            writeln("- Number of simulations with this aftershock: " + aftershocksCount);
            writeln("- Probability over all " + totalCount + " simulations: "
                    + ETAS_AbstractPlot.getProbStr(probAll, true));
            writeln("- Probability over " + sigCount + " significant (M≥" + MIN_MAG + ") simulations: "
                    + ETAS_AbstractPlot.getProbStr(probSig, true));
            writeln("A \"significant\" simulation is one that produced at least one M≥" + MIN_MAG
                    + " rupture anywhere in the simulation domain; it is the appropriate denominator "
                    + "when comparing against observed aftershock statistics, since simulations that "
                    + "produced no detectable seismicity cannot meaningfully count toward the probability "
                    + "of a specific rupture.");
            writeln("Both probabilities are reported because the raw-binned probability (over all N "
                    + "simulations) is the more conservative (lower) number, while the conditional "
                    + "probability over significant simulations is what most seismic-hazard analyses quote.");

            // --- Occurrence Timeline (image spliced in by addTimeline) ----------
            writeln("### Occurrence Timeline");
            lines.add("<!--TIMELINE_SLOT-->");

            // --- Catalog Matches -----------------------------------------------
            parser.collectCatalogsMatching(fssIndex);
            writeln("## Catalog Matches");
            int numMatching = parser.catalogMatches.get(fssIndex).size();
//            writeln("Per-catalog metadata for each of the " + numMatching + " matching catalogs:");
            writeln("The following table summarizes all " + numMatching + " catalogs where the primary aftershock occurred.");
            lines.addAll(buildCatalogMetadataTable(fssIndex));
        }

        /**
         * Embeds the markdown produced by the given plot (already finalized
         * via {@code plot.finalize(plotsDir, fss)}) into the report under a
         * {@code ## Catalog Map Plots} section, followed by a footer line
         * stating how many catalogs were plotted.
         *
         * <p>Each bare {@code ![Map](...png)} cell emitted by the plot is
         * post-processed into a clickable {@code [![Map](...png)](...png)}
         * link so the thumbnail opens the full-resolution PNG. This wrapping
         * is applied here rather than in {@code ETAS_SimulatedCatalogPlot} so
         * that the plot's own markdown output (reused by
         * {@link SimulationMarkdownGenerator} for the HTML report) stays
         * stable.</p>
         *
         * @param plot the plot whose markdown should be embedded
         */
        public void addCatalogMapPlots(ETAS_AbstractPlot plot) {
            try {
                writeln("## Catalog Map Plots");
                List<String> plotLines = plot.generateMarkdown(plotsRelPath, "###", "");
                for (String line : plotLines) {
                    lines.add(linkifyImage(line));
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
         * Splices the (already-finalized) timeline plot's markdown image into
         * the report at the {@code <!--TIMELINE_SLOT-->} placeholder left by
         * {@link #generateReport}. The placeholder sits directly under the
         * {@code ### Occurrence Timeline} heading, so the plot's own heading
         * and top-link lines are discarded and only the prose + clickable
         * image are inserted.
         *
         * @param plot the finalized timeline plot to embed
         * @throws IOException if the plot markdown cannot be generated
         */
        public void addTimeline(ETAS_RuptureOccurrenceTimeline plot) throws IOException {
            int slot = lines.indexOf("<!--TIMELINE_SLOT-->");
            if (slot < 0)
                return;
            // generateMarkdown returns: [heading, topLink, "", prose, "", image, ""]
            List<String> md = plot.generateMarkdown(plotsRelPath, "", "");
            List<String> replacement = new ArrayList<>();
            for (int i = 2; i < md.size(); i++) {
                String l = md.get(i);
                if (l.isEmpty() && replacement.isEmpty())
                    continue; // drop leading blanks
                replacement.add(l);
            }
            lines.remove(slot);
            lines.addAll(slot, replacement);
        }

        /**
         * Embeds the (already-finalized) primary-aftershock timing plot into
         * the report as a {@code ## Primary Aftershock Timing} section: a
         * histogram of when the primary aftershock occurred across the matching
         * catalogs, followed by a cumulative occurrence-count summary over
         * fixed time thresholds. The plot's bare image is wrapped in a
         * clickable link via {@link #linkifyImage}, mirroring {@link #addCatalogMapPlots}.
         *
         * @param plot the finalized timing plot to embed
         * @throws IOException if the plot markdown cannot be generated
         */
        public void addTimingHistogram(ETAS_PrimaryAftershockTimingPlot plot) throws IOException {
            List<String> md = plot.generateMarkdown(plotsRelPath, "##", "");
            for (String line : md)
                lines.add(linkifyImage(line));
            lines.add("");
        }

        /**
         * Wraps any bare markdown image {@code ![alt](path)} in a clickable
         * link of the form {@code [![alt](path)](path)}, leaving images that
         * are already wrapped in a link untouched. Used by {@link #addCatalogMapPlots}
         * to make the catalog map thumbnails clickable without modifying
         * {@code ETAS_SimulatedCatalogPlot}.
         *
         * @param line a single line of markdown (may be a table row)
         * @return the line with any bare images wrapped in clickable links
         */
        private static String linkifyImage(String line) {
            // (?<!\[) avoids re-wrapping an image that is already inside a link.
            Pattern img = Pattern.compile("(?<!\\[)!\\[[^\\]]*\\]\\([^)]+\\)");
            Matcher m = img.matcher(line);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String image = m.group();
                // image = ![alt](path); extract the path between the last (...)
                int open = image.lastIndexOf('(');
                String path = image.substring(open + 1, image.length() - 1);
                m.appendReplacement(sb, Matcher.quoteReplacement("[" + image + "](" + path + ")"));
            }
            m.appendTail(sb);
            return sb.toString();
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
         * Builds the per-catalog metadata table for every catalog that
         * contains the scenario aftershock. One row per matching catalog,
         * sorted chronologically by the scenario rupture's origin time, with
         * columns for catalog index, time of occurrence, the offset from the
         * simulation start, catalog size, maximum magnitude, M&ge;5/6/7
         * counts, the count of ruptures on each parent-section combination,
         * and the largest rupture (FSS index).
         *
         * <p>To keep the report readable when a great many catalogs match,
         * the table is capped at the first 200 rows; a note pointing to the
         * timeline plot is appended when truncation occurs.</p>
         *
         * @param fssIndex FSS index of the scenario aftershock
         * @return the markdown table lines (plus an optional truncation note)
         */
        private List<String> buildCatalogMetadataTable(int fssIndex) {
            // Pre-compute compact labels (e.g. "H", "R", "H+R") and the candidate
            // FSS rupture-index sets for each parent-section combination. The full
            // combination name is retained alongside each label so the table can
            // carry a legend mapping labels back to parent-section names.
            List<String> comboHeaders = rupSearch.getIntersectionHeaders();
            List<List<Integer>> comboRupSets = rupSearch.getCandidateRuptureSets();
            List<String> comboLabels = new ArrayList<>();
            List<String> comboNames = new ArrayList<>();
            for (String header : comboHeaders) {
                // header e.g. "## Ruptures on Hollywood, Raymond faults"
                String body = header.substring("## Ruptures on ".length());
                if (body.endsWith(" faults"))
                    body = body.substring(0, body.length() - " faults".length());
                else if (body.endsWith(" fault"))
                    body = body.substring(0, body.length() - " fault".length());
                StringBuilder label = new StringBuilder();
                for (String name : body.split(", ")) {
                    if (!label.isEmpty())
                        label.append("+");
                    label.append(name.charAt(0));
                }
                comboLabels.add(label.toString());
                comboNames.add(body);
            }

            long simStartMs = config.getSimulationStartTimeMillis();

            // Accumulators for the brief column summary (over ALL matching
            // catalogs, not just the truncated rows shown in the table).
            long minOriginMs = Long.MAX_VALUE, maxOriginMs = Long.MIN_VALUE;
            long minDeltaMs = Long.MAX_VALUE, maxDeltaMs = Long.MIN_VALUE;
            int minSize = Integer.MAX_VALUE, maxSize = Integer.MIN_VALUE;
            long sumSize = 0;

            // One row per matching catalog, carrying the cells + sort key.
            class Row {
                final long originMs;
                final String[] cells;
                Row(long originMs, String[] cells) {
                    this.originMs = originMs;
                    this.cells = cells;
                }
            }
            List<Row> rows = new ArrayList<>();
            for (ETAS_Catalog catalog : parser.catalogMatches.get(fssIndex)) {
                ETAS_SimulationMetadata meta = catalog.getSimulationMetadata();
                long originMs = parser.rupMatches.get(meta.catalogIndex).get(fssIndex).getOriginTime();

                int size = 0;
                double maxMag = Double.NaN;
                ETAS_EqkRupture largestRup = null;
                int m5 = 0, m6 = 0, m7 = 0;
                Set<Integer> fssIdxsInCat = new HashSet<>();
                for (ETAS_EqkRupture rup : catalog) {
                    size++;
                    double m = rup.getMag();
                    if (Double.isNaN(maxMag) || m > maxMag) {
                        maxMag = m;
                        largestRup = rup;
                    }
                    if (m >= 5.0)
                        m5++;
                    if (m >= 6.0)
                        m6++;
                    if (m >= 7.0)
                        m7++;
                    int idx = rup.getFSSIndex();
                    if (idx >= 0)
                        fssIdxsInCat.add(idx);
                }

                // Parent-section ruptures: "<label>:<count>" per non-empty combo.
                StringBuilder psb = new StringBuilder();
                for (int c = 0; c < comboRupSets.size(); c++) {
                    int count = 0;
                    for (int idx : comboRupSets.get(c))
                        if (fssIdxsInCat.contains(idx))
                            count++;
                    if (count > 0) {
                        if (!psb.isEmpty())
                            psb.append(", ");
                        psb.append(comboLabels.get(c)).append(":").append(count);
                    }
                }
                String parentSectionCell = psb.isEmpty() ? "none" : psb.toString();

                String largestCell;
                if (largestRup == null)
                    largestCell = "—";
                else
                    largestCell = String.valueOf(largestRup.getFSSIndex());

                String[] cells = {
                        String.valueOf(meta.catalogIndex),
                        getDate(originMs),
                        formatDuration(originMs - simStartMs),
                        String.valueOf(size),
                        String.format("M%.2f", maxMag),
                        String.valueOf(m5),
                        String.valueOf(m6),
                        String.valueOf(m7),
                        parentSectionCell,
                        largestCell
                };
                rows.add(new Row(originMs, cells));

                // Update the column-summary accumulators.
                if (originMs < minOriginMs)
                    minOriginMs = originMs;
                if (originMs > maxOriginMs)
                    maxOriginMs = originMs;
                long delta = originMs - simStartMs;
                if (delta < minDeltaMs)
                    minDeltaMs = delta;
                if (delta > maxDeltaMs)
                    maxDeltaMs = delta;
                if (size < minSize)
                    minSize = size;
                if (size > maxSize)
                    maxSize = size;
                sumSize += size;
            }

            rows.sort(Comparator.comparingLong(r -> r.originMs));
            int total = rows.size();
            boolean truncated = false;
            if (rows.size() > 200) {
                rows = new ArrayList<>(rows.subList(0, 200));
                truncated = true;
            }

            MarkdownUtils.TableBuilder table = MarkdownUtils.tableBuilder();
            table.initNewLine();
            table.addColumn("Cat #");
            table.addColumn("Time of Occurrence");
            table.addColumn("Δt after sim start");
            table.addColumn("Cat size");
            table.addColumn("Max M");
            table.addColumn("# M≥5");
            table.addColumn("# M≥6");
            table.addColumn("# M≥7");
            table.addColumn("Parent-section ruptures");
            table.addColumn("Largest rupture (FSS)");
            table.finalizeLine();
            for (Row row : rows) {
                table.initNewLine();
                for (String cell : row.cells)
                    table.addColumn(cell);
                table.finalizeLine();
            }

            // Legend explaining the parent-section ruptures column: what the count
            // represents, how each compact label maps back to its parent section(s),
            // and that the counts overlap (they are not a disjoint partition).
            // Placed above the table so the codes are defined before the reader hits
            // the `label:count` cells.
            StringBuilder legend = new StringBuilder("Parent-section ruptures — for each matching "
                    + "catalog, the number of distinct ruptures in that catalog whose rupture surface "
                    + "intersects the listed parent section(s). Each entry is `label:count`; labels are "
                    + "compact codes (the first letter of each parent section, joined by `+`): ");
            for (int c = 0; c < comboLabels.size(); c++) {
                if (c > 0)
                    legend.append(", ");
                legend.append("`").append(comboLabels.get(c)).append("` = ").append(comboNames.get(c));
            }
            legend.append(". These counts overlap and are not a partition: a rupture whose surface "
                    + "spans multiple parent sections is counted under every combination it intersects, "
                    + "so a multi-section rupture appears in several entries and the counts should not "
                    + "be summed");
            // Append a concrete example derived from the actual labels (the first
            // two single-section labels and their pairwise combination) so it stays
            // correct if the parent-section list ever changes.
            String firstSingle = null, secondSingle = null;
            for (String lbl : comboLabels) {
                if (!lbl.contains("+")) {
                    if (firstSingle == null)
                        firstSingle = lbl;
                    else if (secondSingle == null) {
                        secondSingle = lbl;
                        break;
                    }
                }
            }
            if (firstSingle != null && secondSingle != null
                    && comboLabels.contains(firstSingle + "+" + secondSingle)) {
                legend.append(" (e.g. a rupture crossing both `").append(firstSingle).append("` and `")
                        .append(secondSingle).append("` also appears under `")
                        .append(firstSingle).append("+").append(secondSingle).append("`)");
            }
            legend.append(".");

            List<String> out = new ArrayList<>();
            out.add(legend.toString());
            out.add("");

            // Brief summary of the time-of-occurrence, Δt, and catalog-size
            // columns, computed over every matching catalog (not just the rows
            // shown). Omitted when nothing matched.
            if (total > 0) {
                double meanSize = (double) sumSize / total;
                String summary = String.format(
                        "Across all %d matching catalogs, the scenario rupture occurred between %s "
                                + "and %s (Δt after sim start: %s–%s); catalog size ranged from %d to %d "
                                + "ruptures (mean %.1f).",
                        total, getDate(minOriginMs), getDate(maxOriginMs),
                        formatDuration(minDeltaMs), formatDuration(maxDeltaMs),
                        minSize, maxSize, meanSize);
                out.add(summary);
                out.add("");
            }

            out.addAll(table.build());

            if (truncated) {
                out.add("");
                out.add("*(showing first 200 of " + total
                        + " matching catalogs; see the timeline plot for the full distribution)*");
            }
            return out;
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
            toc.addAll(MarkdownUtils.buildTOC(headers, 2, 3));
            toc.add("");

            lines.addAll(insertAt, toc);
            Files.write(mdFile, lines);

            // Also render an HTML version of the report (with GFM tables, heading
            // anchors, and image links) alongside the markdown. MarkdownUtils
            // copies markdown.css next to the HTML file; image paths are relative
            // to outputDir so they resolve from report.html as well.
            MarkdownUtils.writeHTML(lines, outputDir.resolve("report.html").toFile());
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
