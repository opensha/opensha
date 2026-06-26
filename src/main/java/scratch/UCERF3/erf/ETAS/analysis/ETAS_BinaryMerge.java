package scratch.UCERF3.erf.ETAS.analysis;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.*;


/**
 * Merges ETAS binaries into one large binary for analysis purposes.
 * <p>
 *     This will not consolidate ETAS configuration JSON files or other metadata.
 *     You need to manually write your own configuration. This is intended to merge
 *     multiple binaries for the same simulation over the same simulated catalog durations.
 *     The combined catalog count will be reported in stdout.
 * </p>
 */
public class ETAS_BinaryMerge {
    static final String[] binaries = {
            "/Users/bhatthal/Downloads/2026_05_27-FSS_Rupture_201887_M7p8_Start_2026_10_15_1_yr_kCOV_1p5_MaxPtSrcM_6/results_m5_preserve_chain.bin",
            "/Users/bhatthal/Downloads/2026_06_12-FSS_Rupture_201887_M7p8_Start_2026_10_15_1_yr_kCOV_1p5_MaxPtSrcM_6/results_m5_preserve_chain.bin"
    };
    static final String outputBinary = "/Users/bhatthal/Downloads/Merged-FSS_Rupture_201887_M7p8_Start_2026_10_15_1_yr_kCOV_1p5_MaxPtSrcM_6/results_m5_preserve_chain.bin";
    static final double MIN_MAG = 2.5;

    /**
     * This is not a CLT, you must manually edit the main function, build and execute.
     * Args will be ignored.
     */
    public static void main(String[] args) throws IOException {
        if (binaries.length == 0) return;
        List<ETAS_Catalog> merged = new ArrayList<>();
        // Iterate over all binaries and add their catalogs
        for (String binary : binaries) {
            final File binFile = new File(binary);
            List<ETAS_Catalog> catalogs = loadCatalogsBinary(binFile, MIN_MAG);
            merged.addAll(catalogs);
        }
        writeCatalogsBinary(new File(outputBinary), merged);
        System.out.println("Wrote " + merged.size() + " catalogs to " + outputBinary);
    }
}
