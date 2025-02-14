package org.opensha.sha.earthquake.rupForecastImpl.nshm23;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import javax.swing.JOptionPane;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_Downloader;

/**
 * USGS 2023 NSHM ERF, Western U.S., Branched Average ERF
 */
public class NSHM23_WUS_BranchAveragedERF extends BaseFaultSystemSolutionERF {

	private static final long serialVersionUID = 277613161331416141L;
	private static final String MODEL = "WUS_branch_averaged_gridded_simplified";
	private static final boolean D = false;
	
	private CompletableFuture<File> fetchFuture;

	/**
	 * The loadFetcher allows the download to begin in the background at
	 * the time of construction. This speeds up our wait time when the
	 * file is needed.
	 * @return		A future that will resolve with the solution file.
	 */
	private static CompletableFuture<File> loadFetcher() {
		return new NSHM23_Downloader()
				.updateFile(MODEL)
				.thenApply(treeFile -> {
			if (treeFile == null || !treeFile.exists()) {
				JOptionPane.showMessageDialog(null,
						"Failed to download " + MODEL +
						". Verify internet connection and restart. Server may be down.",
						"NSHM23_WUS_BranchAveragedERF", JOptionPane.ERROR_MESSAGE);
				return null;
			}
			return treeFile;
		});
	}

	/**
	 * Initializes FaulSystemSolutionERF with provided solution
	 * @param fetchFuture	A future for the WUS model file. Will resolve once available.
	 */
	public NSHM23_WUS_BranchAveragedERF(CompletableFuture<File> fetchFuture) {
		this.fetchFuture = fetchFuture;
	}
	
	/**
	 * Noarg constructor uses default FaultSystemSolution
	 */
	public NSHM23_WUS_BranchAveragedERF() {
		this(loadFetcher());
	}
	
	@Override
	public void updateForecast() {
		if (D) System.out.println("NSHM23_WUS_BranchAveragedERF.updateForecast()");
		// First fetch and load our solution, then update the forecast.
		fetchFuture.thenAccept(solFile -> {
			try {
				FaultSystemSolution sol = FaultSystemSolution.load(solFile);
				setSolution(sol);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}).join();
		super.updateForecast();
	}
	
	public static void main(String[] args) {
		new NSHM23_WUS_BranchAveragedERF().updateForecast();
	}
}
