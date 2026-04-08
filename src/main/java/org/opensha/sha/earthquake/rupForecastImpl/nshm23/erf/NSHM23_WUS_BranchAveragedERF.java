package org.opensha.sha.earthquake.rupForecastImpl.nshm23.erf;

import java.io.File;
import java.io.IOException;

import javax.swing.JOptionPane;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_Downloader;

/**
 * USGS 2023 NSHM ERF, Western U.S., Branch Averaged ERF
 */
public class NSHM23_WUS_BranchAveragedERF extends BaseFaultSystemSolutionERF {

	private static final long serialVersionUID = 277613161331416141L;
	private static final String MODEL = "WUS_branch_averaged_gridded_simplified";
	public static final String NAME = "NSHM23-WUS (crustal only, excl. Cascadia) Branch Avg ERF";
	private static final boolean D = false;
	private NSHM23_Downloader downloader;
	
	/**
	 * Noarg constructor uses default storeDir for NSHM23 files
	 * (Recommended Constructor)
	 */
	public NSHM23_WUS_BranchAveragedERF() {
		this(/*storeDir=*/null); // Use default storeDir
	}
	
	/**
	 * Allow specifying where to download files
	 * @param storeDir
	 */
	public NSHM23_WUS_BranchAveragedERF(File storeDir) {
		super();
		if (storeDir == null) {
			this.downloader = new NSHM23_Downloader();
		} else {
			this.downloader = new NSHM23_Downloader(storeDir);
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
							"NSHM23_WUS_BranchAveragedERF", JOptionPane.ERROR_MESSAGE);
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
		if (D) System.out.println("NSHM23_WUS_BranchAveragedERF.updateForecast()");
		if (getSolution() == null) {
			fetchSolution();
		}
		super.updateForecast();
	}
	
	public static void main(String[] args) {
		new NSHM23_WUS_BranchAveragedERF().updateForecast();
	}
}
