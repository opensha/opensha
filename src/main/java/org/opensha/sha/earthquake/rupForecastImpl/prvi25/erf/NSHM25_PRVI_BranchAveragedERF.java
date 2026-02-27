package org.opensha.sha.earthquake.rupForecastImpl.prvi25.erf;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.NSHM25_Downloader;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * USGS 2025 NSHM ERF, Puerto Rico and Virgin Islands, Branch Averaged ERF
 */
public class NSHM25_PRVI_BranchAveragedERF extends BaseFaultSystemSolutionERF {
    private static final String MODEL = "2025_PRVI_NSHM_Fault-System_Solution";
    public static final String NAME = "NSHM25-PRVI Branch Avg ERF";
    private static final boolean D = false;
    private NSHM25_Downloader downloader;
    /**
     * Noarg constructor uses default storeDir for NSHM25 files
     * (Recommended Constructor)
     */
    public NSHM25_PRVI_BranchAveragedERF() {
        this(/*storeDir=*/null); // Use default storeDir
    }

    /**
     * Allow specifying where to download files
     * @param storeDir
     */
    public NSHM25_PRVI_BranchAveragedERF(File storeDir) {
        super();
        if (storeDir == null) {
            this.downloader = new NSHM25_Downloader();
        } else {
            this.downloader = new NSHM25_Downloader(storeDir);
        }
        this.setName(NAME);
    }

    /**
     * Put parameters in the ParameterList
     */
    @Override
    protected void postCreateParamListHook() {
        super.postCreateParamListHook();
        if (adjustableParams.containsParameter(FILE_PARAM_NAME)) {
            adjustableParams.removeParameter(fileParam);
        }
    }

    /**
     * Loads the latest solution available for download
     */
    private void fetchSolution() {
        downloader.updateFile(MODEL).thenAccept(solFile -> {
            try {
                if (solFile == null || !solFile.exists()) {
                    JOptionPane.showMessageDialog(null,
                            "Failed to download " + MODEL +
                                    ". Verify internet connection and restart. Server may be down.",
                            "NSHM25_PRVI_BranchAveragedERF", JOptionPane.ERROR_MESSAGE);
                } else {
                    FaultSystemSolution sol = FaultSystemSolution.load(solFile);
                    setSolution(sol);
                }
            } catch (IOException e) {
                throw ExceptionUtils.asRuntimeException(e);
            }
        }).join();
    }

    /**
     * Ensure our solution is fetched and loaded and then update the forecast.
     * Only checks for newer models if not already loaded in this session.
     */
    @Override
    public void updateForecast() {
        if (D) System.out.println("NSHM25_PRVI_BranchAveragedERF.updateForecast");
        if (getSolution() == null) {
            fetchSolution();
        }
        super.updateForecast();
    }

    public static void main(String[] args) {
        new NSHM25_PRVI_BranchAveragedERF().updateForecast();
    }
}
